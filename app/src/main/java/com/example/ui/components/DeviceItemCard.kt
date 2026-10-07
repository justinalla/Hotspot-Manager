package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectedDevice
import com.example.model.DeviceType
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun DeviceItemCard(
    device: ConnectedDevice,
    onToggleBlock: () -> Unit,
    onOpenLimitDialog: () -> Unit,
    onOpenQuotaDialog: () -> Unit,
    onOpenDetailDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            device.isBlocked -> CyberCrimson.copy(alpha = 0.7f)
            device.bandwidthLimitKbps != null -> CyberAmber.copy(alpha = 0.7f)
            else -> CyberBorder.copy(alpha = 0.4f)
        },
        label = "border_color"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .testTag("device_card_${device.ipAddress}"),
        shape = RoundedCornerShape(18.dp),
        color = CyberSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Icon, Names, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Icon with status glow
                DeviceIconBadge(device = device)

                Spacer(modifier = Modifier.width(12.dp))

                // Name, Vendor & IP
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${device.vendor} • ${device.ipAddress}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                DeviceStatusBadge(device = device)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-row: MAC, Latency, Realtime Speed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MAC: ",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextMuted
                    )
                    Text(
                        text = device.macAddress,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Latency
                    if (device.latencyMs > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (device.latencyMs < 50) CyberEmerald else CyberAmber,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${device.latencyMs} ms",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSecondary
                            )
                        }
                    }

                    // Live Speed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (device.isBlocked) CyberCrimson else CyberCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (device.isBlocked) "0 Ko/s" else formatSpeed(device.currentSpeedKbps).let { "${it.first} ${it.second}" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = if (device.isBlocked) CyberCrimson else TextPrimary
                        )
                    }
                }
            }

            // Quota and Data Usage Progress bar (if quota defined)
            if (device.quotaBytes != null && device.quotaBytes > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val ratio = (device.bytesUsed.toFloat() / device.quotaBytes.toFloat()).coerceIn(0f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val remaining = maxOf(0L, device.quotaBytes - device.bytesUsed)
                        Text(
                            text = if (device.isQuotaExceeded) "Quota dépassé ! Bloqué" else "Quota restant : ${formatBytes(remaining)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            color = if (device.isQuotaExceeded) CyberCrimson else CyberCyan
                        )
                        Text(
                            text = "${formatBytes(device.bytesUsed)} / ${formatBytes(device.quotaBytes)} (${(ratio * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp),
                        color = if (device.isQuotaExceeded) CyberCrimson else CyberCyan,
                        trackColor = CyberSurfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Consommation totale session:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextMuted
                    )
                    Text(
                        text = formatBytes(device.bytesUsed),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Block / Unblock Button
                OutlinedButton(
                    onClick = onToggleBlock,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp)
                        .testTag("block_btn_${device.ipAddress}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (device.isBlocked) CyberEmerald else CyberCrimson,
                        containerColor = if (device.isBlocked) CyberEmerald.copy(alpha = 0.12f)
                        else CyberCrimson.copy(alpha = 0.12f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (device.isBlocked) CyberEmerald.copy(alpha = 0.6f)
                            else CyberCrimson.copy(alpha = 0.6f)
                        )
                    )
                ) {
                    Icon(
                        imageVector = if (device.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (device.isBlocked) "Débloquer" else "Bloquer",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Limit Bandwidth Button
                OutlinedButton(
                    onClick = onOpenLimitDialog,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(38.dp)
                        .testTag("limit_btn_${device.ipAddress}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (device.bandwidthLimitKbps != null) CyberAmber else TextSecondary,
                        containerColor = if (device.bandwidthLimitKbps != null) CyberAmber.copy(alpha = 0.12f)
                        else CyberSurfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (device.bandwidthLimitKbps != null) CyberAmber.copy(alpha = 0.6f)
                            else CyberBorder.copy(alpha = 0.4f)
                        )
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (device.bandwidthLimitKbps != null) "${device.bandwidthLimitKbps} Ko/s" else "Limiter",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }

                // Quota Button
                IconButton(
                    onClick = onOpenQuotaDialog,
                    modifier = Modifier
                        .size(38.dp)
                        .background(CyberSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .testTag("quota_btn_${device.ipAddress}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DataUsage,
                        contentDescription = "Définir Quota",
                        tint = if (device.quotaBytes != null) CyberCyan else TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Details & Root / ADB Command inspector
                IconButton(
                    onClick = onOpenDetailDialog,
                    modifier = Modifier
                        .size(38.dp)
                        .background(CyberSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .testTag("details_btn_${device.ipAddress}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Détails & Root/ADB",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceIconBadge(device: ConnectedDevice) {
    val icon = when (device.deviceType) {
        DeviceType.PHONE -> Icons.Default.PhoneAndroid
        DeviceType.TABLET -> Icons.Default.Tablet
        DeviceType.LAPTOP -> Icons.Default.Computer
        DeviceType.SMART_TV -> Icons.Default.Tv
        DeviceType.CONSOLE -> Icons.Default.VideogameAsset
        DeviceType.IOT -> Icons.Default.Router
        DeviceType.UNKNOWN -> Icons.Default.DevicesOther
    }

    val iconColor = when {
        device.isBlocked -> CyberCrimson
        device.bandwidthLimitKbps != null -> CyberAmber
        else -> CyberCyan
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .background(iconColor.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun DeviceStatusBadge(device: ConnectedDevice) {
    when {
        device.isBlocked -> {
            Box(
                modifier = Modifier
                    .background(CyberCrimson.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .border(1.dp, CyberCrimson.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "BLOQUÉ PAR PROXY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CyberCrimson
                )
            }
        }
        device.bandwidthLimitKbps != null -> {
            Box(
                modifier = Modifier
                    .background(CyberAmber.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .border(1.dp, CyberAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "LIMITÉ (${device.bandwidthLimitKbps} Ko/s)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CyberAmber
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .background(CyberEmerald.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .border(1.dp, CyberEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ACTIF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CyberEmerald
                )
            }
        }
    }
}
