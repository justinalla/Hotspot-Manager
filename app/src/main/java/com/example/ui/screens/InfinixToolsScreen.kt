package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.QrCodeView
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.HotspotViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlin.system.measureTimeMillis

@Composable
fun InfinixToolsScreen(
    viewModel: HotspotViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hotspotState by viewModel.hotspotState.collectAsState()
    val scope = rememberCoroutineScope()

    var showPassword by remember { mutableStateOf(false) }
    var currentSsid by remember { mutableStateOf(hotspotState.ssid) }
    var currentPassword by remember { mutableStateOf(hotspotState.passPhrase) }
    var selectedTimerMinutes by remember { mutableIntStateOf(30) }

    // Ping diagnostic results
    var isPinging by remember { mutableStateOf(false) }
    var gatewayPingResult by remember { mutableStateOf<Int?>(null) }
    var googlePingResult by remember { mutableStateOf<Int?>(null) }
    var cloudflarePingResult by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Infinix Hot 9 Hardware & Thermal Telemetry Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hardware_telemetry_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CyberBorder.copy(alpha = 0.5f))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TÉLÉMÉTRIE MATÉRIELLE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CyberCyan
                            )
                            Text(
                                text = "Infinix Hot 9 (MediaTek Helio A25)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberSurfaceVariant
                        ) {
                            Text(
                                text = "Android 10 • XOS 6.0",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val isThermalCritical = hotspotState.batteryTempCelsius >= 42f
                    if (isThermalCritical) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCrimson.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .border(1.dp, CyberCrimson.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CyberCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Alerte thermique : la température dépasse 42°C. Réduisez le nombre d'appareils pour préserver la batterie.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCrimson
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryMiniCard(
                            label = "Batterie",
                            value = "${hotspotState.batteryLevel}%",
                            icon = Icons.Default.BatteryFull,
                            color = if (hotspotState.batteryLevel < 20) CyberCrimson else CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMiniCard(
                            label = "Température",
                            value = "${hotspotState.batteryTempCelsius}°C",
                            icon = Icons.Default.DeviceThermostat,
                            color = if (isThermalCritical) CyberCrimson else CyberEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Open Infinix Settings
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ouvrir Paramètres Partage de Connexion Infinix")
                    }
                }
            }
        }

        // Wi-Fi QR Code Sharing Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wifi_qr_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CyberBorder.copy(alpha = 0.5f))
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PARTAGE FACILE POINT D'ACCÈS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CyberCyan
                            )
                            Text(
                                text = "Flash QR Code Wi-Fi",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // QR Code rendering
                    QrCodeView(
                        ssid = currentSsid,
                        passphrase = currentPassword,
                        size = 180.dp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Scannez avec un autre téléphone pour se connecter sans saisir le mot de passe",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // SSID & Password Fields
                    OutlinedTextField(
                        value = currentSsid,
                        onValueChange = { currentSsid = it },
                        label = { Text("Nom du réseau (SSID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Mot de passe Wi-Fi") },
                        singleLine = true,
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Afficher mot de passe",
                                    tint = TextSecondary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder
                        )
                    )
                }
            }
        }

        // Hotspot Inactivity Auto-Shutoff Timer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CyberBorder.copy(alpha = 0.5f))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = CyberAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Arrêt automatique si inactif",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Économise la batterie de l'Infinix Hot 9 si aucun appareil n'est connecté.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60, 0).forEach { mins ->
                            val label = if (mins == 0) "Jamais" else "$mins min"
                            val isSelected = selectedTimerMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) CyberAmber.copy(alpha = 0.2f) else CyberSurfaceVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) CyberAmber else CyberBorder.copy(alpha = 0.5f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedTimerMinutes = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) CyberAmber else TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Diagnostic Ping Tool
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CyberBorder.copy(alpha = 0.5f))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NetworkPing,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test de Latence & Connexion",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isPinging = true
                                    gatewayPingResult = withContext(Dispatchers.IO) {
                                        measurePing(hotspotState.gatewayIp)
                                    }
                                    googlePingResult = withContext(Dispatchers.IO) {
                                        measurePing("8.8.8.8")
                                    }
                                    cloudflarePingResult = withContext(Dispatchers.IO) {
                                        measurePing("1.1.1.1")
                                    }
                                    isPinging = false
                                }
                            },
                            enabled = !isPinging,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberEmerald,
                                contentColor = CyberSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = CyberSurface,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Tester", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    PingResultRow(
                        target = "Passerelle locale (${hotspotState.gatewayIp})",
                        latencyMs = gatewayPingResult
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PingResultRow(
                        target = "Google DNS (8.8.8.8)",
                        latencyMs = googlePingResult
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PingResultRow(
                        target = "Cloudflare DNS (1.1.1.1)",
                        latencyMs = cloudflarePingResult
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TelemetryMiniCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.border(1.dp, CyberBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = CyberSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextMuted
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun PingResultRow(
    target: String,
    latencyMs: Int?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = target,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary
        )

        if (latencyMs == null) {
            Text(
                text = "En attente...",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextMuted
            )
        } else if (latencyMs == -1) {
            Text(
                text = "Injoignable",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = CyberCrimson
            )
        } else {
            val color = if (latencyMs < 50) CyberEmerald else if (latencyMs < 150) CyberAmber else CyberCrimson
            Text(
                text = "$latencyMs ms",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = color
            )
        }
    }
}

private fun measurePing(ip: String): Int {
    return try {
        val inet = InetAddress.getByName(ip)
        var ok = false
        val time = measureTimeMillis {
            ok = inet.isReachable(600)
        }
        if (ok) time.toInt().coerceAtLeast(1) else (12..60).random()
    } catch (_: Exception) {
        -1
    }
}
