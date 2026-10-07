package com.example.service

import android.util.Log
import com.example.model.DnsLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class DnsFilterServer(
    private val scope: CoroutineScope
) {
    private val tag = "DnsFilterServer"
    private var udpSocket: DatagramSocket? = null
    private var serverJob: Job? = null
    var boundPort: Int = 53
        private set

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        val blockAds = MutableStateFlow(true)
        val blockSocial = MutableStateFlow(false)
        val blockAdult = MutableStateFlow(false)
        val customBlacklist = ConcurrentHashMap.newKeySet<String>()

        private val _dnsLogs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
        val dnsLogs = _dnsLogs.asStateFlow()

        val totalQueriesCount = AtomicInteger(0)
        val totalBlockedCount = AtomicInteger(0)

        // Common Ad & Tracker domains
        private val adDomains = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "adservice.google", "adcolony.com", "applvn.com", "applovin.com",
            "unity3d.com/ads", "taboola.com", "outbrain.com", "adnxs.com",
            "criteo.com", "amazon-adsystem.com", "popads.net", "admob.com",
            "flurry.com", "scorecardresearch.com", "appsflyer.com"
        )

        // Social Media domains
        private val socialDomains = listOf(
            "tiktok.com", "tiktokv.com", "musical.ly", "byteoversea.com", "ibytedtos.com",
            "instagram.com", "cdninstagram.com", "facebook.com", "fbcdn.net",
            "snapchat.com", "twitter.com", "x.com", "twimg.com", "pinterest.com"
        )

        // Adult content domains
        private val adultDomains = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
            "youporn.com", "chaturbate.com", "livejasmin.com", "stripchat.com", "onlyfans.com"
        )

        fun addDomainToBlacklist(domain: String) {
            customBlacklist.add(domain.lowercase().trim())
        }

        fun removeDomainFromBlacklist(domain: String) {
            customBlacklist.remove(domain.lowercase().trim())
        }

        fun clearLogs() {
            _dnsLogs.value = emptyList()
        }
    }

    private val upstreamDns = InetAddress.getByName("8.8.8.8")
    private val memoryLogList = CopyOnWriteArrayList<DnsLogEntry>()

    fun start() {
        if (serverJob != null && serverJob?.isActive == true) return

        serverJob = scope.launch(Dispatchers.IO) {
            // Attempt to bind port 53. If not allowed, fallback to port 5354
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(53, InetAddress.getByName("0.0.0.0"))
                boundPort = 53
                Log.i(tag, "DNS Filter Server successfully bound to standard port 53")
            } catch (e: Exception) {
                Log.w(tag, "Port 53 restricted or in use (${e.message}), falling back to port 5354")
                try {
                    socket = DatagramSocket(5354, InetAddress.getByName("0.0.0.0"))
                    boundPort = 5354
                    // If root is available, redirect port 53 UDP to 5354 transparently!
                    if (IptablesController.isRootAvailable()) {
                        IptablesController.executeRootCommand("iptables -t nat -I PREROUTING -p udp --dport 53 -j REDIRECT --to-ports 5354")
                        Log.i(tag, "Root iptables NAT redirect applied: UDP 53 -> 5354")
                    }
                } catch (e2: Exception) {
                    Log.e(tag, "Failed to bind DNS socket on port 5354: ${e2.message}")
                    return@launch
                }
            }

            udpSocket = socket
            _isRunning.value = true
            val buffer = ByteArray(1500)

            while (isActive) {
                try {
                    val requestPacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(requestPacket)

                    val packetData = requestPacket.data.copyOf(requestPacket.length)
                    val clientAddress = requestPacket.address
                    val clientPort = requestPacket.port
                    val clientIp = clientAddress.hostAddress ?: ""

                    scope.launch(Dispatchers.IO) {
                        handleDnsQuery(socket, packetData, clientAddress, clientPort, clientIp)
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                }
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null

        serverJob?.cancel()
        serverJob = null

        // Clean up iptables redirect if root was used
        if (boundPort == 5354) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (IptablesController.isRootAvailable()) {
                        IptablesController.executeRootCommand("iptables -t nat -D PREROUTING -p udp --dport 53 -j REDIRECT --to-ports 5354")
                    }
                } catch (_: Exception) {}
            }
        }
        Log.i(tag, "DNS Filter Server stopped")
    }

    private suspend fun handleDnsQuery(
        socket: DatagramSocket,
        packetData: ByteArray,
        clientAddress: InetAddress,
        clientPort: Int,
        clientIp: String
    ) = withContext(Dispatchers.IO) {
        if (packetData.size < 12) return@withContext

        totalQueriesCount.incrementAndGet()
        val domain = extractDomainName(packetData)

        // 1. Check if device is blocked by IP (Total Block)
        val isDeviceBlocked = HotspotProxyService.isIpBlocked(clientIp)

        // 2. Check Domain against categories and blacklists
        var shouldBlock = isDeviceBlocked
        var blockReason = if (isDeviceBlocked) "Appareil Bloqué" else ""

        if (!shouldBlock && domain.isNotBlank()) {
            val lowerDomain = domain.lowercase()

            // Custom blacklist
            if (customBlacklist.any { lowerDomain.contains(it) }) {
                shouldBlock = true
                blockReason = "Liste Noire"
            }
            // AdBlock
            else if (blockAds.value && adDomains.any { lowerDomain.contains(it) }) {
                shouldBlock = true
                blockReason = "Publicité"
            }
            // Social Media
            else if (blockSocial.value && socialDomains.any { lowerDomain.contains(it) }) {
                shouldBlock = true
                blockReason = "Réseau Social"
            }
            // Adult content
            else if (blockAdult.value && adultDomains.any { lowerDomain.contains(it) }) {
                shouldBlock = true
                blockReason = "Contrôle Parental"
            }
        }

        // Record to Live Log
        val logEntry = DnsLogEntry(
            clientIp = clientIp,
            domain = if (domain.isBlank()) "Inconnu" else domain,
            isBlocked = shouldBlock,
            reason = blockReason
        )
        recordDnsLog(logEntry)

        if (shouldBlock) {
            totalBlockedCount.incrementAndGet()
            // Forge Sinkhole DNS Response (pointing to 0.0.0.0)
            val sinkholeResponse = forgeSinkholeResponse(packetData)
            try {
                val responsePacket = DatagramPacket(sinkholeResponse, sinkholeResponse.size, clientAddress, clientPort)
                socket.send(responsePacket)
            } catch (_: Exception) {}
        } else {
            // Forward to upstream DNS (8.8.8.8) and relay real response back
            try {
                DatagramSocket().use { upstreamSocket ->
                    upstreamSocket.soTimeout = 2500
                    val upstreamReq = DatagramPacket(packetData, packetData.size, upstreamDns, 53)
                    upstreamSocket.send(upstreamReq)

                    val respBuffer = ByteArray(1500)
                    val upstreamResp = DatagramPacket(respBuffer, respBuffer.size)
                    upstreamSocket.receive(upstreamResp)

                    val clientResp = DatagramPacket(
                        upstreamResp.data,
                        upstreamResp.length,
                        clientAddress,
                        clientPort
                    )
                    socket.send(clientResp)
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {}
        }
    }

    private fun recordDnsLog(entry: DnsLogEntry) {
        memoryLogList.add(0, entry)
        if (memoryLogList.size > 100) {
            memoryLogList.removeAt(memoryLogList.size - 1)
        }
        _dnsLogs.value = memoryLogList.toList()
    }

    /**
     * Parses RFC 1035 QNAME from the question section.
     */
    private fun extractDomainName(data: ByteArray): String {
        var offset = 12 // DNS Header is 12 bytes
        val domainParts = mutableListOf<String>()

        try {
            while (offset < data.size) {
                val len = data[offset].toInt() and 0xFF
                if (len == 0) break // End of QNAME
                if ((len and 0xC0) == 0xC0) {
                    // Compression pointer, break to avoid complex parsing
                    break
                }
                offset++
                if (offset + len <= data.size) {
                    val part = String(data, offset, len, Charsets.US_ASCII)
                    domainParts.add(part)
                    offset += len
                } else {
                    break
                }
            }
        } catch (_: Exception) {
            return ""
        }

        return domainParts.joinToString(".")
    }

    /**
     * Forges a DNS response pointing to 0.0.0.0 (Sinkhole).
     */
    private fun forgeSinkholeResponse(query: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()

        // Transaction ID (same as query)
        baos.write(query[0].toInt())
        baos.write(query[1].toInt())

        // Flags: Standard response, No error, Recursion Available (0x8180)
        baos.write(0x81)
        baos.write(0x80)

        // QDCOUNT: 1 question
        baos.write(0x00)
        baos.write(0x01)

        // ANCOUNT: 1 answer
        baos.write(0x00)
        baos.write(0x01)

        // NSCOUNT & ARCOUNT: 0
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)

        // Copy question section from query
        var offset = 12
        while (offset < query.size && query[offset] != 0.toByte()) {
            offset++
        }
        offset += 5 // Skip terminating 0x00, QTYPE (2 bytes), and QCLASS (2 bytes)
        val questionLen = offset - 12
        if (offset <= query.size) {
            baos.write(query, 12, questionLen)
        }

        // Answer Section:
        // Name pointer (0xC00C pointing to QNAME at byte 12)
        baos.write(0xC0)
        baos.write(0x0C)

        // Type: A (0x0001)
        baos.write(0x00)
        baos.write(0x01)

        // Class: IN (0x0001)
        baos.write(0x00)
        baos.write(0x01)

        // TTL: 60 seconds (0x0000003C)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x3C)

        // RDLENGTH: 4 bytes (IPv4)
        baos.write(0x00)
        baos.write(0x04)

        // RDATA: 0.0.0.0
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)

        return baos.toByteArray()
    }
}
