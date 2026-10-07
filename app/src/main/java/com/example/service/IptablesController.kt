package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object IptablesController {

    data class ExecutionResult(
        val isSuccess: Boolean,
        val output: String,
        val exitCode: Int
    )

    /**
     * Checks if device has Root binary (su) accessible.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            return@withContext line != null && line.isNotEmpty()
        } catch (_: Exception) {
            return@withContext false
        }
    }

    /**
     * Executes shell commands with root privileges (su) if granted.
     */
    suspend fun executeRootCommand(command: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdOut = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stdErr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()

            val output = (stdOut + if (stdErr.isNotBlank()) "\n[Err] $stdErr" else "").trim()
            ExecutionResult(
                isSuccess = exitCode == 0,
                output = if (output.isEmpty()) "Commande exécutée avec succès" else output,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ExecutionResult(
                isSuccess = false,
                output = "Échec : ${e.message ?: "Accès Root refusé ou indisponible"}",
                exitCode = -1
            )
        }
    }

    /**
     * Generates iptables command to block forwarding of an IP address.
     */
    fun generateBlockIpCommand(ip: String): String {
        return "iptables -I FORWARD -s $ip -j DROP; iptables -I FORWARD -d $ip -j DROP"
    }

    /**
     * Generates iptables command to unblock an IP address.
     */
    fun generateUnblockIpCommand(ip: String): String {
        return "iptables -D FORWARD -s $ip -j DROP; iptables -D FORWARD -d $ip -j DROP"
    }

    /**
     * Generates iptables / tc command for real-time bandwidth throttling on Android.
     */
    fun generateBandwidthLimitCommand(ip: String, limitKbps: Int): String {
        val bytesPerSec = limitKbps * 1024
        return "iptables -I FORWARD -s $ip -m limit --limit ${limitKbps}/s --limit-burst ${limitKbps * 2} -j ACCEPT; " +
               "iptables -A FORWARD -s $ip -j DROP"
    }

    /**
     * Generates ADB shell one-liner for remote desktop terminal.
     */
    fun generateAdbCommand(rawCommand: String): String {
        return "adb shell su -c \"$rawCommand\""
    }
}
