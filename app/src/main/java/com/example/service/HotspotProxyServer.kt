package com.example.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HotspotProxyServer(
    private val port: Int = 8282,
    private val scope: CoroutineScope
) {
    private val tag = "HotspotProxyServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    val activeConnectionsCount = AtomicInteger(0)

    val clientSockets = ConcurrentHashMap.newKeySet<Socket>()

    fun start() {
        if (serverJob != null && serverJob?.isActive == true) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port, 100, InetAddress.getByName("0.0.0.0")).apply {
                    reuseAddress = true
                }
                Log.i(tag, "Local Proxy Gateway listening on 0.0.0.0:$port")

                while (isActive) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (!isActive) break
                        Log.w(tag, "Accept interrupted: ${e.message}")
                        break
                    }

                    clientSockets.add(clientSocket)
                    scope.launch(Dispatchers.IO) {
                        try {
                            activeConnectionsCount.incrementAndGet()
                            handleClientConnection(clientSocket)
                        } catch (e: Exception) {
                            // Connection finished or closed
                        } finally {
                            activeConnectionsCount.decrementAndGet()
                            clientSockets.remove(clientSocket)
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Proxy server error: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        // Close all active client connections
        clientSockets.forEach { s ->
            try { s.close() } catch (_: Exception) {}
        }
        clientSockets.clear()

        serverJob?.cancel()
        serverJob = null
        activeConnectionsCount.set(0)
        Log.i(tag, "Proxy Gateway server stopped")
    }

    private suspend fun handleClientConnection(clientSocket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = clientSocket.inetAddress?.hostAddress ?: ""
        clientSocket.soTimeout = 30000 // 30s timeout

        // 1. Check Blacklist / Blocked IP
        if (HotspotProxyService.isIpBlocked(clientIp)) {
            sendBlockedResponse(clientSocket)
            return@withContext
        }

        // 2. Check Data Quota
        if (HotspotProxyService.isQuotaExceeded(clientIp)) {
            sendQuotaExceededResponse(clientSocket)
            return@withContext
        }

        val clientIn = BufferedInputStream(clientSocket.getInputStream())
        val clientOut = BufferedOutputStream(clientSocket.getOutputStream())

        // Read the initial request line (e.g., "CONNECT example.com:443 HTTP/1.1" or "GET http://...")
        val requestLine = readLine(clientIn) ?: return@withContext
        val tokens = requestLine.trim().split(" ")
        if (tokens.size < 2) return@withContext

        val method = tokens[0].uppercase()
        val target = tokens[1]

        if (method == "CONNECT") {
            handleHttpsConnectTunnel(clientSocket, clientIn, clientOut, clientIp, target)
        } else {
            handleHttpPlainRequest(clientSocket, clientIn, clientOut, clientIp, requestLine, tokens)
        }
    }

    /**
     * Handles HTTPS CONNECT tunneling (method CONNECT host:443 HTTP/1.1)
     */
    private suspend fun handleHttpsConnectTunnel(
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        clientIp: String,
        target: String
    ) = withContext(Dispatchers.IO) {
        // Read remaining headers until empty line
        while (true) {
            val line = readLine(clientIn) ?: break
            if (line.trim().isEmpty()) break
        }

        val parts = target.split(":")
        val remoteHost = parts[0]
        val remotePort = parts.getOrNull(1)?.toIntOrNull() ?: 443

        var remoteSocket: Socket? = null
        try {
            remoteSocket = Socket()
            remoteSocket.connect(InetSocketAddress(remoteHost, remotePort), 10000)
            remoteSocket.soTimeout = 30000

            // Send 200 Connection Established to client
            val establishedMsg = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: HotspotManager-ZeroRoot/1.0\r\n\r\n"
            clientOut.write(establishedMsg.toByteArray(Charsets.US_ASCII))
            clientOut.flush()

            val remoteIn = BufferedInputStream(remoteSocket.getInputStream())
            val remoteOut = BufferedOutputStream(remoteSocket.getOutputStream())

            // Bidirectional tunnel relay with per-IP rate-limiting and byte accounting
            val job1 = scope.launch(Dispatchers.IO) {
                relayStreamWithControl(clientIn, remoteOut, clientIp, clientSocket, remoteSocket)
            }
            val job2 = scope.launch(Dispatchers.IO) {
                relayStreamWithControl(remoteIn, clientOut, clientIp, clientSocket, remoteSocket)
            }

            job1.join()
            job2.join()
        } catch (e: Exception) {
            // Tunnel ended
        } finally {
            try { remoteSocket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Handles standard HTTP request forwarding (GET / POST / HEAD etc.)
     */
    private suspend fun handleHttpPlainRequest(
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        clientIp: String,
        requestLine: String,
        tokens: List<String>
    ) = withContext(Dispatchers.IO) {
        val headers = mutableListOf<String>()
        var host = ""
        var port = 80

        // Parse remaining headers to find Host
        while (true) {
            val line = readLine(clientIn) ?: break
            if (line.trim().isEmpty()) break
            headers.add(line)
            if (line.startsWith("Host:", ignoreCase = true)) {
                val hostVal = line.substring(5).trim()
                if (hostVal.contains(":")) {
                    host = hostVal.substringBefore(":")
                    port = hostVal.substringAfter(":").toIntOrNull() ?: 80
                } else {
                    host = hostVal
                }
            }
        }

        if (host.isEmpty()) {
            val uri = tokens[1]
            if (uri.startsWith("http://", ignoreCase = true)) {
                val noProto = uri.substring(7)
                host = noProto.substringBefore("/").substringBefore(":")
                val portStr = noProto.substringBefore("/").substringAfter(":", "")
                if (portStr.isNotEmpty()) port = portStr.toIntOrNull() ?: 80
            }
        }

        if (host.isEmpty()) return@withContext

        var remoteSocket: Socket? = null
        try {
            remoteSocket = Socket()
            remoteSocket.connect(InetSocketAddress(host, port), 10000)
            remoteSocket.soTimeout = 30000

            val remoteOut = BufferedOutputStream(remoteSocket.getOutputStream())
            val remoteIn = BufferedInputStream(remoteSocket.getInputStream())

            // Forward sanitized request line and headers
            remoteOut.write("$requestLine\r\n".toByteArray(Charsets.US_ASCII))
            for (header in headers) {
                remoteOut.write("$header\r\n".toByteArray(Charsets.US_ASCII))
            }
            remoteOut.write("\r\n".toByteArray(Charsets.US_ASCII))
            remoteOut.flush()

            // Bidirectional relay with rate limiting & quota tracking
            val job1 = scope.launch(Dispatchers.IO) {
                relayStreamWithControl(clientIn, remoteOut, clientIp, clientSocket, remoteSocket)
            }
            val job2 = scope.launch(Dispatchers.IO) {
                relayStreamWithControl(remoteIn, clientOut, clientIp, clientSocket, remoteSocket)
            }

            job1.join()
            job2.join()
        } catch (_: Exception) {
        } finally {
            try { remoteSocket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Relays data chunks with real-time per-IP bandwidth throttling (Token Bucket)
     * and byte counting. Drops immediately if IP becomes blocked or exceeds quota.
     */
    private suspend fun relayStreamWithControl(
        input: InputStream,
        output: OutputStream,
        clientIp: String,
        clientSocket: Socket,
        remoteSocket: Socket
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(8192)

        try {
            while (true) {
                // Check if blocked in real time
                if (HotspotProxyService.isIpBlocked(clientIp)) {
                    sendBlockedResponse(clientSocket)
                    break
                }
                // Check if quota exceeded in real time
                if (HotspotProxyService.isQuotaExceeded(clientIp)) {
                    sendQuotaExceededResponse(clientSocket)
                    break
                }

                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break

                val startTime = System.currentTimeMillis()

                output.write(buffer, 0, bytesRead)
                output.flush()

                // Track bytes consumed by client IP
                HotspotProxyService.addBytesUsed(clientIp, bytesRead.toLong())

                // Bandwidth Throttling (Rate Limiting in KB/s)
                val limitKbps = HotspotProxyService.getBandwidthLimit(clientIp)
                if (limitKbps != null && limitKbps > 0) {
                    val maxBytesPerSec = limitKbps * 1024L
                    val expectedDurationMs = (bytesRead * 1000L) / maxBytesPerSec
                    val elapsedMs = System.currentTimeMillis() - startTime
                    if (expectedDurationMs > elapsedMs) {
                        delay(expectedDurationMs - elapsedMs)
                    }
                }
            }
        } catch (_: SocketException) {
        } catch (_: Exception) {}
    }

    private fun readLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) baos.write(c)
        }
        if (baos.size() == 0 && c == -1) return null
        return baos.toString(Charsets.US_ASCII.name())
    }

    private fun sendBlockedResponse(socket: Socket) {
        try {
            val html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>403 - Accès Bloqué</title></head>
                <body style="background:#0F172A;color:#FFFFFF;font-family:sans-serif;text-align:center;padding:50px;">
                    <div style="background:#1E293B;padding:30px;border-radius:16px;display:inline-block;border:1px solid #EF4444;max-width:400px;">
                        <h1 style="color:#EF4444;margin:0 0 10px 0;">⛔ Accès Bloqué</h1>
                        <p style="color:#94A3B8;font-size:14px;">L'administrateur du point d'accès Wi-Fi a bloqué cet appareil.</p>
                        <p style="color:#64748B;font-size:12px;">Hotspot Manager - Passerelle Zero-Root</p>
                    </div>
                </body></html>
            """.trimIndent()

            val response = "HTTP/1.1 403 Forbidden\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${html.toByteArray().size}\r\n" +
                    "Connection: close\r\n\r\n$html"

            socket.getOutputStream().write(response.toByteArray())
            socket.getOutputStream().flush()
            socket.close()
        } catch (_: Exception) {}
    }

    private fun sendQuotaExceededResponse(socket: Socket) {
        try {
            val html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>Quota Dépassé</title></head>
                <body style="background:#0F172A;color:#FFFFFF;font-family:sans-serif;text-align:center;padding:50px;">
                    <div style="background:#1E293B;padding:30px;border-radius:16px;display:inline-block;border:1px solid #F59E0B;max-width:400px;">
                        <h1 style="color:#F59E0B;margin:0 0 10px 0;">⚠️ Quota Épuisé</h1>
                        <p style="color:#94A3B8;font-size:14px;">Votre volume de données alloué sur ce point d'accès a été atteint.</p>
                        <p style="color:#64748B;font-size:12px;">Hotspot Manager - Passerelle Zero-Root</p>
                    </div>
                </body></html>
            """.trimIndent()

            val response = "HTTP/1.1 403 Forbidden\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${html.toByteArray().size}\r\n" +
                    "Connection: close\r\n\r\n$html"

            socket.getOutputStream().write(response.toByteArray())
            socket.getOutputStream().flush()
            socket.close()
        } catch (_: Exception) {}
    }
}
