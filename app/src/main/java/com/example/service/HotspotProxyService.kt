package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class HotspotProxyService : Service() {

    private val tag = "HotspotProxyService"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var proxyServer: HotspotProxyServer? = null
    private var dnsFilterServer: DnsFilterServer? = null

    companion object {
        const val ACTION_START = "com.example.service.START_PROXY"
        const val ACTION_STOP = "com.example.service.STOP_PROXY"
        const val NOTIFICATION_CHANNEL_ID = "hotspot_proxy_channel"
        const val NOTIFICATION_ID = 3002
        const val PROXY_PORT = 8282

        private val _isProxyRunning = MutableStateFlow(false)
        val isProxyRunning = _isProxyRunning.asStateFlow()

        val blockedIps = ConcurrentHashMap.newKeySet<String>()
        val rateLimitsKbps = ConcurrentHashMap<String, Int>() // IP -> KB/s
        val quotasBytes = ConcurrentHashMap<String, Long>() // IP -> Max Bytes
        val ipBytesUsedMap = ConcurrentHashMap<String, Long>() // IP -> Cumulative Bytes

        var onQuotaExceededListener: ((ip: String) -> Unit)? = null

        fun isIpBlocked(ip: String): Boolean = blockedIps.contains(ip)

        fun isQuotaExceeded(ip: String): Boolean {
            val quota = quotasBytes[ip] ?: return false
            val used = ipBytesUsedMap[ip] ?: 0L
            return used >= quota
        }

        fun getBandwidthLimit(ip: String): Int? = rateLimitsKbps[ip]

        fun addBytesUsed(ip: String, bytes: Long) {
            val updated = ipBytesUsedMap.compute(ip) { _, current -> (current ?: 0L) + bytes }
            val quota = quotasBytes[ip]
            if (quota != null && updated != null && updated >= quota) {
                onQuotaExceededListener?.invoke(ip)
            }
        }

        fun blockIp(ip: String) {
            blockedIps.add(ip)
        }

        fun unblockIp(ip: String) {
            blockedIps.remove(ip)
        }

        fun setBandwidthLimit(ip: String, limitKbps: Int?) {
            if (limitKbps == null || limitKbps <= 0) {
                rateLimitsKbps.remove(ip)
            } else {
                rateLimitsKbps[ip] = limitKbps
            }
        }

        fun setQuota(ip: String, quotaBytes: Long?) {
            if (quotaBytes == null || quotaBytes <= 0) {
                quotasBytes.remove(ip)
            } else {
                quotasBytes[ip] = quotaBytes
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, HotspotProxyService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HotspotProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProxy()
            else -> startProxy()
        }
        return START_STICKY
    }

    private fun startProxy() {
        if (_isProxyRunning.value) return

        try {
            val notification = buildForegroundNotification()
            startForeground(NOTIFICATION_ID, notification)

            proxyServer = HotspotProxyServer(PROXY_PORT, serviceScope)
            proxyServer?.start()

            dnsFilterServer = DnsFilterServer(serviceScope)
            dnsFilterServer?.start()

            _isProxyRunning.value = true
            Log.i(tag, "Hotspot Proxy & DNS Gateway started on port $PROXY_PORT / DNS ${dnsFilterServer?.boundPort}")
        } catch (e: Exception) {
            Log.e(tag, "Error starting Proxy service: ${e.message}")
            stopSelf()
        }
    }

    private fun stopProxy() {
        _isProxyRunning.value = false
        proxyServer?.stop()
        proxyServer = null
        dnsFilterServer?.stop()
        dnsFilterServer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(tag, "Hotspot Proxy Gateway stopped")
    }

    override fun onDestroy() {
        stopProxy()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Passerelle Proxy Hotspot (Zero-Root)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Contrôle, blocage et limitation de bande passante par proxy local"
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
            .setContentTitle("Passerelle Proxy Hotspot Active (Port $PROXY_PORT)")
            .setContentText("Filtrage des connexions et bridage de débit opérationnels")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
