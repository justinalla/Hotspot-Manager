package com.example.model

import java.util.UUID

data class DnsLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val domain: String,
    val isBlocked: Boolean,
    val reason: String = ""
)
