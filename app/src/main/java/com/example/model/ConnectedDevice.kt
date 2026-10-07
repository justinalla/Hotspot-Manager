package com.example.model

data class ConnectedDevice(
    val id: String, // IP or MAC address
    val ipAddress: String,
    val macAddress: String,
    val hostname: String,
    val vendor: String,
    val deviceType: DeviceType,
    val isBlocked: Boolean = false,
    val bandwidthLimitKbps: Int? = null, // null means unlimited, otherwise cap in KB/s
    val quotaBytes: Long? = null, // null means no quota, otherwise max allowed bytes
    val bytesUsed: Long = 0L,
    val currentSpeedKbps: Float = 0f,
    val latencyMs: Int = -1,
    val isOnline: Boolean = true,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val customNickname: String? = null
) {
    val displayName: String
        get() = customNickname?.takeIf { it.isNotBlank() }
            ?: hostname.takeIf { it.isNotBlank() && it != "Inconnu" && it != ipAddress }
            ?: "$vendor (${ipAddress.substringAfterLast('.')})"

    val isQuotaExceeded: Boolean
        get() = quotaBytes != null && bytesUsed >= quotaBytes
}
