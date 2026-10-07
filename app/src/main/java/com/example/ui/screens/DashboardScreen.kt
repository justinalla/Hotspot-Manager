package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectedDevice
import com.example.ui.components.DeviceItemCard
import com.example.ui.components.LiveTrafficWaveform
import com.example.ui.components.TrafficSpeedometer
import com.example.ui.components.formatBytes
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
import com.example.viewmodel.HotspotViewModel

@Composable
fun DashboardScreen(
    viewModel: HotspotViewModel,
    onNavigateToDevices: () -> Unit,
    onSelectDeviceForLimit: (ConnectedDevice) -> Unit,
    onSelectDeviceForQuota: (ConnectedDevice) -> Unit,
    onSelectDeviceForDetails: (ConnectedDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hotspotState by viewModel.hotspotState.collectAsState()
    val trafficHistory by viewModel.trafficHistory.collectAsState()
    val devices by viewModel.devices.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Infinix Hot 9 & Hotspot System Card
        item {
            Spacer(modifier = Modifier.height(4.dp))
            InfinixHotspotHeroCard(
                hotspotState = hotspotState,
                onOpenTetheringSettings = {
                    try {
                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        } catch (_: Exception) {}
                    }
                }
            )
        }

        // Dual Speedometer (Download & Upload with animated dial)
        item {
            TrafficSpeedometer(
                downloadSpeedKbps = hotspotState.currentRxSpeedKbps,
                uploadSpeedKbps = hotspotState.currentTxSpeedKbps,
                peakDownloadKbps = hotspotState.peakRxSpeedKbps,
                peakUploadKbps = hotspotState.peakTxSpeedKbps
            )
        }

        // Live Dynamic Telemetry Waveform Chart
        item {
            LiveTrafficWaveform(samples = trafficHistory)
        }

        // Quick Metrics 2x2 Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickMetricCard(
                    title = "Connectés",
                    value = "${hotspotState.connectedClientsCount}",
                    subtitle = "Appareils actifs",
                    color = CyberCyan,
                    icon = Icons.Default.Devices,
                    modifier = Modifier.weight(1f)
                )
                QuickMetricCard(
                    title = "Limités",
                    value = "${hotspotState.throttledClientsCount}",
                    subtitle = "Bande restreinte",
                    color = CyberAmber,
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickMetricCard(
                    title = "Bloqués",
                    value = "${hotspotState.blockedClientsCount}",
                    subtitle = "Accès coupé",
                    color = CyberCrimson,
                    icon = Icons.Default.Block,
                    modifier = Modifier.weight(1f)
                )
                QuickMetricCard(
                    title = "Session",
                    value = formatBytes(hotspotState.totalRxBytes + hotspotState.totalTxBytes),
                    subtitle = "Volume cumulé",
                    color = CyberEmerald,
                    icon = Icons.Default.SwapVert,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Active Devices Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "APPAREILS ACTIFS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Gestion du trafic et restrictions",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hotspotState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = CyberCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.scanNetwork() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(CyberSurfaceElevated, CircleShape)
                                .testTag("refresh_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scanner",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToDevices,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("view_all_devices_button")
                    ) {
                        Text("Voir tout (${devices.size})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Top 3 connected devices
        val topDevices = devices.take(3)
        if (topDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (hotspotState.isHotspotActive) "En attente d'appareils..." else "Point d'accès inactif",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hotspotState.isHotspotActive)
                                "Le point d'accès est actif (${hotspotState.gatewayIp}). Connectez un téléphone, PC ou tablette à votre Wi-Fi pour le voir et le gérer ici."
                            else
                                "Veuillez activer le point d'accès Wi-Fi dans les paramètres de votre Infinix Hot 9.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.scanNetwork() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanner le réseau")
                        }
                    }
                }
            }
        } else {
            items(topDevices, key = { it.id }) { device ->
                DeviceItemCard(
                    device = device,
                    onToggleBlock = { viewModel.toggleBlockDevice(device) },
                    onOpenLimitDialog = { onSelectDeviceForLimit(device) },
                    onOpenQuotaDialog = { onSelectDeviceForQuota(device) },
                    onOpenDetailDialog = { onSelectDeviceForDetails(device) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfinixHotspotHeroCard(
    hotspotState: com.example.model.HotspotState,
    onOpenTetheringSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("infinix_hero_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(CyberBorder.copy(alpha = 0.5f))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Device & Hotspot Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (hotspotState.isHotspotActive) CyberEmerald else CyberAmber,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "POINT D'ACCÈS WI-FI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (hotspotState.isHotspotActive) CyberEmerald else CyberAmber
                    )
                }

                // Infinix Hot 9 Tag
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CyberSurfaceVariant
                ) {
                    Text(
                        text = "Infinix Hot 9 (Android 10)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subnet Gateway & Interface Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Passerelle Hotspot",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Text(
                        text = "${hotspotState.gatewayIp} (${hotspotState.interfaceName})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextPrimary
                    )
                }

                // Battery and Thermal stats
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Battery
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (hotspotState.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = if (hotspotState.batteryLevel < 20) CyberCrimson else CyberCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${hotspotState.batteryLevel}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = TextPrimary
                        )
                    }

                    // Thermal
                    val isThermalWarning = hotspotState.batteryTempCelsius >= 42f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                if (isThermalWarning) CyberCrimson.copy(alpha = 0.2f) else CyberSurfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeviceThermostat,
                            contentDescription = null,
                            tint = if (isThermalWarning) CyberCrimson else CyberEmerald,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${hotspotState.batteryTempCelsius}°C",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isThermalWarning) CyberCrimson else TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onOpenTetheringSettings() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Paramètres Point d'accès XOS",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ouvrir",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.border(1.dp, CyberBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = CyberSurfaceElevated
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextMuted
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSecondary
            )
        }
    }
}
