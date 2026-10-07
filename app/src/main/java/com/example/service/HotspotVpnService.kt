package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class HotspotVpnService : VpnService() {

    private val tag = "HotspotVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ACTION_START = "com.example.service.START_VPN"
        const val ACTION_STOP = "com.example.service.STOP_VPN"
        const val NOTIFICATION_CHANNEL_ID = "hotspot_vpn_channel"
        const val NOTIFICATION_ID = 2001

        private val _isVpnRunning = MutableStateFlow(false)
        val isVpnRunning = _isVpnRunning.asStateFlow()

        val blockedIps = ConcurrentHashMap.newKeySet<String>()
        val bandwidthLimitsKbps = ConcurrentHashMap<String, Int>() // IP -> KB/s
        val ipByteCounts = ConcurrentHashMap<String, Long>()

        fun blockIp(ip: String) {
            blockedIps.add(ip)
        }

        fun unblockIp(ip: String) {
            blockedIps.remove(ip)
        }

        fun setRateLimit(ip: String, limitKbps: Int?) {
            if (limitKbps == null || limitKbps <= 0) {
                bandwidthLimitsKbps.remove(ip)
            } else {
                bandwidthLimitsKbps[ip] = limitKbps
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (_isVpnRunning.value) return

        try {
            val notification = buildForegroundNotification()
            startForeground(NOTIFICATION_ID, notification)

            val builder = Builder()
                .setSession("Hotspot Traffic Limiter")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(tag, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            _isVpnRunning.value = true
            startPacketLoop()
            Log.i(tag, "Hotspot Firewall & Bandwidth Limiter VPN active")
        } catch (e: Exception) {
            Log.e(tag, "Error starting VPN: ${e.message}")
            stopSelf()
        }
    }

    /**
     * Packet processing loop implementing IP blocking and token bucket rate limiting.
     */
    private fun startPacketLoop() {
        vpnJob?.cancel()
        vpnJob = serviceScope.launch {
            val fd = vpnInterface?.fileDescriptor ?: return@launch
            val inputStream = FileInputStream(fd)
            val outputStream = FileOutputStream(fd)
            val buffer = ByteBuffer.allocate(32768)

            // Token bucket maps: IP -> Pair(availableTokensInBytes, lastRefillTimestamp)
            val tokenBuckets = HashMap<String, Pair<Double, Long>>()

            while (isActive && _isVpnRunning.value) {
                try {
                    buffer.clear()
                    val length = inputStream.read(buffer.array())
                    if (length > 0) {
                        buffer.limit(length)

                        // Inspect IPv4 Header (first byte version & IHL)
                        val versionAndIhl = buffer.get(0).toInt()
                        val version = (versionAndIhl shr 4) and 0x0F

                        if (version == 4 && length >= 20) {
                            val srcBytes = ByteArray(4)
                            val dstBytes = ByteArray(4)
                            System.arraycopy(buffer.array(), 12, srcBytes, 0, 4)
                            System.arraycopy(buffer.array(), 16, dstBytes, 0, 4)

                            val srcIp = InetAddress.getByAddress(srcBytes).hostAddress ?: ""
                            val dstIp = InetAddress.getByAddress(dstBytes).hostAddress ?: ""

                            // 1. Check if IP is blocked in Firewall Blacklist
                            if (blockedIps.contains(srcIp) || blockedIps.contains(dstIp)) {
                                // Drop packet silently (Firewall Block)
                                continue
                            }

                            // 2. Check Bandwidth Limiting for Source or Destination
                            val targetIp = if (bandwidthLimitsKbps.containsKey(srcIp)) srcIp
                            else if (bandwidthLimitsKbps.containsKey(dstIp)) dstIp
                            else null

                            if (targetIp != null) {
                                val limitKbps = bandwidthLimitsKbps[targetIp] ?: 0
                                if (limitKbps > 0) {
                                    val now = System.currentTimeMillis()
                                    val bytesPerSec = limitKbps * 1024.0
                                    val currentBucket = tokenBuckets[targetIp]
                                    var tokens = currentBucket?.first ?: bytesPerSec
                                    val lastRefill = currentBucket?.second ?: now
                                    val elapsedSec = (now - lastRefill) / 1000.0

                                    tokens = (tokens + elapsedSec * bytesPerSec).coerceAtMost(bytesPerSec * 2)

                                    if (tokens >= length) {
                                        tokens -= length
                                        tokenBuckets[targetIp] = Pair(tokens, now)
                                        // Forward packet
                                        outputStream.write(buffer.array(), 0, length)
                                    } else {
                                        // Rate exceeded, drop packet or throttle
                                        tokenBuckets[targetIp] = Pair(tokens, now)
                                        continue
                                    }
                                } else {
                                    outputStream.write(buffer.array(), 0, length)
                                }
                            } else {
                                outputStream.write(buffer.array(), 0, length)
                            }

                            // Update traffic counters
                            val currentTotal = ipByteCounts[srcIp] ?: 0L
                            ipByteCounts[srcIp] = currentTotal + length
                        } else {
                            outputStream.write(buffer.array(), 0, length)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) Log.w(tag, "Packet loop notice: ${e.message}")
                    break
                }
            }
        }
    }

    private fun stopVpn() {
        _isVpnRunning.value = false
        vpnJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(tag, "Hotspot VPN service stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Hotspot Traffic Limiter & Firewall",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Protection et limitation de bande passante du point d'accès"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Surveillance Hotspot Active")
            .setContentText("Filtrage du trafic et contrôle de bande passante en cours")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
