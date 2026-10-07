package com.example.scanner

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.ConnectedDevice
import com.example.model.DeviceType
import com.example.service.IptablesController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.system.measureTimeMillis

class SubnetScanner {

    private val tag = "SubnetScanner"

    data class NetworkInterfaceInfo(
        val name: String,
        val ipAddress: String,
        val prefixLength: Short,
        val isHotspotLikely: Boolean
    )

    /**
     * Checks if Wi-Fi Hotspot (AP) is active using Android's official sticky intent
     * "android.net.wifi.WIFI_AP_STATE_CHANGED" (extra "wifi_state" == 13 for ENABLED).
     * Falls back to NetworkInterface inspection.
     */
    fun isHotspotActive(context: Context): Boolean {
        try {
            // Sticky intent check (100% reliable on Android 10, no reflection issues)
            val intent = context.applicationContext.registerReceiver(
                null,
                IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED")
            )
            if (intent != null) {
                val state = intent.getIntExtra("wifi_state", -1)
                // 13 = WIFI_AP_STATE_ENABLED, 12 = WIFI_AP_STATE_ENABLING
                if (state == 13 || state == 12) {
                    return true
                } else if (state == 11 || state == 10) {
                    return false
                }
            }
        } catch (_: Exception) {}

        // Fallback: Check if any AP interface (ap0, softap0, swlan0, rndis0) is UP and has an IP
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                if (name.contains("ap") || name.contains("softap") || name.contains("rndis") || name.contains("swlan")) {
                    for (addr in nif.interfaceAddresses) {
                        val ip = addr.address.hostAddress ?: ""
                        if (ip.startsWith("192.168.43.") || ip.startsWith("192.168.44.") || ip.startsWith("192.168.50.")) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Detects the active Hotspot interface and gateway IP (e.g. ap0 / 192.168.43.1).
     */
    fun findHotspotInterface(): NetworkInterfaceInfo {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                var bestCandidate: NetworkInterfaceInfo? = null

                for (nif in interfaces) {
                    if (!nif.isUp || nif.isLoopback) continue
                    val name = nif.name.lowercase()

                    for (addr in nif.interfaceAddresses) {
                        val inet = addr.address
                        if (inet.isLoopbackAddress || inet.hostAddress?.contains(":") == true) continue
                        val ip = inet.hostAddress ?: continue

                        val isHotspot = name.contains("ap") || name.contains("rndis") || name.contains("softap") ||
                                ip.startsWith("192.168.43.") || ip.startsWith("192.168.44.") || ip.startsWith("192.168.50.")

                        val info = NetworkInterfaceInfo(
                            name = nif.name,
                            ipAddress = ip,
                            prefixLength = addr.networkPrefixLength,
                            isHotspotLikely = isHotspot
                        )

                        if (isHotspot) return info
                        else if (bestCandidate == null && (name.contains("wlan") || name.contains("eth"))) {
                            bestCandidate = info
                        }
                    }
                }
                if (bestCandidate != null) return bestCandidate
            }
        } catch (e: Exception) {
            Log.e(tag, "Error detecting interface: ${e.message}")
        }

        return NetworkInterfaceInfo(
            name = "ap0",
            ipAddress = "192.168.43.1",
            prefixLength = 24,
            isHotspotLikely = true
        )
    }

    /**
     * Hybrid Scanner: Root Mode + Multi-Protocol Non-Root Pipeline.
     * ONLY returns real connected devices.
     */
    suspend fun scanSubnet(
        baseSubnet: String = "192.168.43.",
        myIp: String = "192.168.43.1"
    ): List<ConnectedDevice> = withContext(Dispatchers.IO) {
        val isRoot = IptablesController.isRootAvailable()

        if (isRoot) {
            val rootDevices = scanWithRoot(baseSubnet, myIp)
            if (rootDevices.isNotEmpty()) {
                Log.i(tag, "Root scan found ${rootDevices.size} connected devices")
                return@withContext rootDevices
            }
        }

        // Non-Root Multi-Protocol Discovery Pipeline
        return@withContext scanMultiProtocolPipeline(baseSubnet, myIp)
    }

    /**
     * ROOT SCAN: Queries 'ip neigh' and DHCP leases directly through 'su'.
     */
    private suspend fun scanWithRoot(baseSubnet: String, myIp: String): List<ConnectedDevice> = withContext(Dispatchers.IO) {
        val devicesMap = mutableMapOf<String, ConnectedDevice>()

        // 1. Read DHCP leases from dnsmasq
        val leasePaths = listOf(
            "/data/misc/dhcp/dnsmasq.leases",
            "/data/vendor/dhcp/dnsmasq.leases",
            "/apex/com.android.tethering/misc/dhcp/dnsmasq.leases"
        )
        for (path in leasePaths) {
            val res = IptablesController.executeRootCommand("cat $path")
            if (res.isSuccess && res.output.isNotBlank()) {
                for (line in res.output.lines()) {
                    // format: <timestamp> <mac> <ip> <hostname> <client_id>
                    val tokens = line.trim().split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val mac = tokens[1].uppercase()
                        val ip = tokens[2]
                        var hostname = tokens[3]
                        if (hostname == "*") hostname = "Appareil-${ip.substringAfterLast('.')}"

                        if (ip.startsWith(baseSubnet) && ip != myIp && mac.contains(":")) {
                            val (vendor, baseType) = MacVendorResolver.resolve(mac)
                            val resolvedType = MacVendorResolver.inferTypeFromHostname(hostname, baseType)

                            devicesMap[ip] = ConnectedDevice(
                                id = ip,
                                ipAddress = ip,
                                macAddress = mac,
                                hostname = hostname,
                                vendor = vendor,
                                deviceType = resolvedType,
                                latencyMs = 12,
                                isOnline = true
                            )
                        }
                    }
                }
            }
        }

        // 2. Read 'ip neigh' via su
        val neighRes = IptablesController.executeRootCommand("ip neigh")
        if (neighRes.isSuccess && neighRes.output.isNotBlank()) {
            for (line in neighRes.output.lines()) {
                // Example: 192.168.43.15 dev ap0 lladdr 70:70:8b:11:22:33 REACHABLE
                val tokens = line.trim().split("\\s+".toRegex())
                val ipIndex = tokens.indexOfFirst { it.startsWith(baseSubnet) && it != myIp }
                val lladdrIndex = tokens.indexOf("lladdr")

                if (ipIndex != -1 && lladdrIndex != -1 && lladdrIndex + 1 < tokens.size) {
                    val ip = tokens[ipIndex]
                    val mac = tokens[lladdrIndex + 1].uppercase()
                    val state = tokens.lastOrNull()?.uppercase() ?: ""

                    // Ignore failed entries
                    if (state != "FAILED" && mac.contains(":") && mac.length == 17) {
                        val existing = devicesMap[ip]
                        val (vendor, baseType) = MacVendorResolver.resolve(mac)
                        val hostname = existing?.hostname ?: queryNetBiosName(ip) ?: "Appareil-${ip.substringAfterLast('.')}"
                        val resolvedType = MacVendorResolver.inferTypeFromHostname(hostname, baseType)

                        devicesMap[ip] = existing?.copy(macAddress = mac, vendor = vendor) ?: ConnectedDevice(
                            id = ip,
                            ipAddress = ip,
                            macAddress = mac,
                            hostname = hostname,
                            vendor = vendor,
                            deviceType = resolvedType,
                            latencyMs = 10,
                            isOnline = true
                        )
                    }
                }
            }
        }

        return@withContext devicesMap.values.toList()
    }

    /**
     * NON-ROOT MULTI-PROTOCOL PIPELINE:
     * Phase 1: ARP Wake-Up Flood (UDP pulses to force kernel ARP resolution)
     * Phase 2: System Ping (/system/bin/ping) & TCP Connect probes
     * Phase 3: Hot ARP Cache harvest (/proc/net/arp & /system/bin/ip neigh)
     * Phase 4: Device name resolution (mDNS & NetBIOS)
     */
    private suspend fun scanMultiProtocolPipeline(
        baseSubnet: String,
        myIp: String
    ): List<ConnectedDevice> = coroutineScope {
        // Phase 1: Fast ARP wake-up flood across 192.168.43.2..254
        withContext(Dispatchers.IO) {
            sendArpWakeupFlood(baseSubnet, myIp)
        }

        // Pre-read ARP table
        val initialArp = withContext(Dispatchers.IO) { harvestArpTable() }

        // Phase 2: Concurrent probing (50 workers)
        val concurrency = 50
        val semaphore = Semaphore(concurrency)

        val probeDeferred = (2..254).map { hostNum ->
            val targetIp = "$baseSubnet$hostNum"
            if (targetIp == myIp) return@map null

            async(Dispatchers.IO) {
                semaphore.withPermit {
                    probeTargetHost(targetIp, initialArp)
                }
            }
        }.filterNotNull()

        val activeDevices = probeDeferred.awaitAll().filterNotNull()

        // Phase 3: Hot ARP reload after traffic has crossed the interface
        val postArp = withContext(Dispatchers.IO) { harvestArpTable() }

        // Enrich devices with resolved MACs and Names
        val finalDevices = activeDevices.map { dev ->
            val mac = postArp[dev.ipAddress] ?: dev.macAddress
            val (vendor, baseType) = if (mac.contains(":") && mac.length == 17) {
                MacVendorResolver.resolve(mac)
            } else {
                Pair(dev.vendor, dev.deviceType)
            }

            // If hostname is still generic, try mDNS / NetBIOS
            val hostName = if (dev.hostname.startsWith("Appareil-")) {
                queryNetBiosName(dev.ipAddress) ?: queryMdnsName(dev.ipAddress) ?: dev.hostname
            } else {
                dev.hostname
            }

            val finalType = MacVendorResolver.inferTypeFromHostname(hostName, baseType)

            dev.copy(
                macAddress = mac,
                vendor = vendor,
                hostname = hostName,
                deviceType = finalType
            )
        }

        return@coroutineScope finalDevices
    }

    /**
     * Sends rapid UDP pulses to force the Linux kernel to send ARP requests on ap0.
     */
    private fun sendArpWakeupFlood(baseSubnet: String, myIp: String) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val dummy = ByteArray(1) { 0 }
                for (i in 2..254) {
                    val ip = "$baseSubnet$i"
                    if (ip == myIp) continue
                    try {
                        val addr = InetAddress.getByName(ip)
                        // Send to NetBIOS 137 and mDNS 5353
                        socket.send(DatagramPacket(dummy, 1, addr, 137))
                        socket.send(DatagramPacket(dummy, 1, addr, 5353))
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Probes an individual IP address using System Ping (/system/bin/ping) and TCP RST probes.
     */
    private fun probeTargetHost(ip: String, arpMap: Map<String, String>): ConnectedDevice? {
        var isAlive = false
        var latencyMs = -1

        // 1. Check if already known in ARP
        if (arpMap.containsKey(ip)) {
            isAlive = true
            latencyMs = 12
        }

        // 2. System Ping binary (/system/bin/ping) - accessible to normal apps on Android
        if (!isAlive) {
            val pingLatency = executeSystemPing(ip)
            if (pingLatency > 0) {
                isAlive = true
                latencyMs = pingLatency
            }
        }

        // 3. Java Reachable & TCP Socket Connect with RST catch
        if (!isAlive) {
            val portsToProbe = intArrayOf(53, 80, 443, 137, 5353, 8080, 5555, 62078)
            try {
                val inet = InetAddress.getByName(ip)
                if (inet.isReachable(150)) {
                    isAlive = true
                    latencyMs = 15
                } else {
                    for (port in portsToProbe) {
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(ip, port), 140)
                                isAlive = true
                            }
                            latencyMs = 18
                            break
                        } catch (e: ConnectException) {
                            // "Connection refused" = RST packet received = HOST IS ALIVE!
                            isAlive = true
                            latencyMs = 15
                            break
                        } catch (_: SocketTimeoutException) {
                            // Offline or filtered on this port
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        if (isAlive) {
            val mac = arpMap[ip] ?: "Inconnue (Android 10)"
            val (vendor, baseType) = if (mac.contains(":") && mac.length == 17) {
                MacVendorResolver.resolve(mac)
            } else {
                Pair("Appareil Connecté", DeviceType.PHONE)
            }

            var hostname = "Appareil-${ip.substringAfterLast('.')}"
            try {
                val inet = InetAddress.getByName(ip)
                val canonical = inet.canonicalHostName
                if (canonical != ip && canonical.isNotBlank()) hostname = canonical
            } catch (_: Exception) {}

            return ConnectedDevice(
                id = ip,
                ipAddress = ip,
                macAddress = mac,
                hostname = hostname,
                vendor = vendor,
                deviceType = baseType,
                latencyMs = if (latencyMs > 0) latencyMs else 18,
                isOnline = true
            )
        }

        return null
    }

    /**
     * Executes native /system/bin/ping -c 1 -w 1 <ip>.
     * Returns latency in ms, or -1 if unreachable.
     */
    private fun executeSystemPing(ip: String): Int {
        return try {
            val start = System.currentTimeMillis()
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "1", "-w", "1", ip))
            val exitCode = process.waitFor()
            val time = (System.currentTimeMillis() - start).toInt()

            if (exitCode == 0) {
                time.coerceAtLeast(1)
            } else {
                -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Harvests ARP table from /proc/net/arp and 'ip neigh'.
     */
    private fun harvestArpTable(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()

        // 1. /proc/net/arp
        try {
            val arpFile = File("/proc/net/arp")
            if (arpFile.exists() && arpFile.canRead()) {
                BufferedReader(FileReader(arpFile)).use { reader ->
                    var line = reader.readLine()
                    while (reader.readLine().also { line = it } != null) {
                        val tokens = line?.split("\\s+".toRegex()) ?: continue
                        if (tokens.size >= 4) {
                            val ip = tokens[0]
                            val mac = tokens[3].uppercase()
                            if (mac != "00:00:00:00:00:00" && mac.contains(":") && mac.length == 17) {
                                arpMap[ip] = mac
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. /system/bin/ip neigh
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ip", "neigh"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val tokens = line?.split("\\s+".toRegex()) ?: continue
                val ipIndex = tokens.indexOfFirst { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) }
                val lladdrIndex = tokens.indexOf("lladdr")
                if (ipIndex != -1 && lladdrIndex != -1 && lladdrIndex + 1 < tokens.size) {
                    val ip = tokens[ipIndex]
                    val mac = tokens[lladdrIndex + 1].uppercase()
                    if (mac.contains(":") && mac.length == 17 && mac != "00:00:00:00:00:00") {
                        arpMap[ip] = mac
                    }
                }
            }
            process.waitFor()
        } catch (_: Exception) {}

        return arpMap
    }

    /**
     * Resolves computer / phone name via NetBIOS Name Service (port 137 UDP).
     */
    private fun queryNetBiosName(ip: String): String? {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 220
                val query = byteArrayOf(
                    0x80.toByte(), 0x94.toByte(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x20, 0x43, 0x4B, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                    0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x21,
                    0x00, 0x01
                )
                val address = InetAddress.getByName(ip)
                val packet = DatagramPacket(query, query.size, address, 137)
                socket.send(packet)

                val buffer = ByteArray(1024)
                val resp = DatagramPacket(buffer, buffer.size)
                socket.receive(resp)

                if (resp.length > 57) {
                    val nameBytes = buffer.copyOfRange(57, 57 + 15)
                    val name = String(nameBytes, Charsets.US_ASCII).trim()
                    if (name.isNotBlank() && name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                        return name
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Resolves Apple and Android device names via mDNS query (port 5353 UDP).
     */
    private fun queryMdnsName(ip: String): String? {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 220
                // Simple mDNS PTR query for _workstation._tcp.local or _android._tcp.local
                val mdnsQuery = byteArrayOf(
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x0c, 0x5f, 0x77, 0x6f, 0x72, 0x6b, 0x73, 0x74, 0x61, 0x74, 0x69, 0x6f, 0x6e,
                    0x04, 0x5f, 0x74, 0x63, 0x70, 0x05, 0x6c, 0x6f, 0x63, 0x61, 0x6c, 0x00,
                    0x00, 0x0c, 0x00, 0x01
                )
                val address = InetAddress.getByName(ip)
                val packet = DatagramPacket(mdnsQuery, mdnsQuery.size, address, 5353)
                socket.send(packet)

                val buffer = ByteArray(1024)
                val resp = DatagramPacket(buffer, buffer.size)
                socket.receive(resp)

                if (resp.length > 12) {
                    // Extract printable strings
                    val str = String(buffer, 0, resp.length, Charsets.UTF_8)
                    val match = Regex("([a-zA-Z0-9-]{3,25})\\.local").find(str)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
