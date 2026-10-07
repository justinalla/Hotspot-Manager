package com.example.model

data class TrafficSample(
    val timestamp: Long,
    val rxSpeedKbps: Float,
    val txSpeedKbps: Float
)

data class HotspotState(
    val interfaceName: String = "ap0",
    val gatewayIp: String = "192.168.43.1",
    val subnetMask: String = "255.255.255.0",
    val isHotspotActive: Boolean = true,
    val connectedClientsCount: Int = 0,
    val throttledClientsCount: Int = 0,
    val blockedClientsCount: Int = 0,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val currentRxSpeedKbps: Float = 0f,
    val currentTxSpeedKbps: Float = 0f,
    val peakRxSpeedKbps: Float = 0f,
    val peakTxSpeedKbps: Float = 0f,
    val batteryLevel: Int = 85,
    val batteryTempCelsius: Float = 32.5f,
    val isCharging: Boolean = false,
    val isVpnActive: Boolean = false,
    val isRootGranted: Boolean = false,
    val isScanning: Boolean = false,
    val ssid: String = "Infinix-Hot-9-AP",
    val securityType: String = "WPA2-PSK",
    val passPhrase: String = "Infinix2026!",
    val isSimulationMode: Boolean = false,
    val isProxyActive: Boolean = false,
    val proxyPort: Int = 8282,
    val deviceModel: String = "Infinix Hot 9",
    val androidVersion: String = "Android 10 (API 29)"
)
