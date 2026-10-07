package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.model.ConnectedDevice
import com.example.service.IptablesController
import com.example.ui.components.formatBytes
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

@Composable
fun FirewallScreen(
    viewModel: HotspotViewModel,
    onSelectDeviceForLimit: (ConnectedDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hotspotState by viewModel.hotspotState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val rootLog by viewModel.rootExecutionLog.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showInstructions by remember { mutableStateOf(true) }

    val proxyHost = hotspotState.gatewayIp
    val proxyPort = hotspotState.proxyPort
    val proxyConfigString = "$proxyHost:$proxyPort"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card: Serveur Proxy Passerelle Zero-Root
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proxy_gateway_hero_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (hotspotState.isProxyActive) CyberEmerald.copy(alpha = 0.8f) else CyberBorder.copy(alpha = 0.5f)
                    )
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (hotspotState.isProxyActive) CyberEmerald.copy(alpha = 0.18f) else CyberSurfaceVariant,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    tint = if (hotspotState.isProxyActive) CyberEmerald else TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SERVEUR PROXY LOCAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = if (hotspotState.isProxyActive) CyberEmerald else TextMuted
                                )
                                Text(
                                    text = "Passerelle Zero-Root (Port $proxyPort)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                        }

                        Switch(
                            checked = hotspotState.isProxyActive,
                            onCheckedChange = { viewModel.toggleProxyService(context) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberEmerald,
                                checkedTrackColor = CyberEmerald.copy(alpha = 0.35f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = CyberSurfaceVariant
                            ),
                            modifier = Modifier.testTag("proxy_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Proxy IP and Port configuration badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Paramètres de connexion Proxy :",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Hôte : $proxyHost  |  Port : $proxyPort",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = CyberCyan
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Configuration Proxy", proxyConfigString)
                                clipboard.setPrimaryClip(clip)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberCyan.copy(alpha = 0.15f),
                                contentColor = CyberCyan
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copier", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Instructions Card for Client Device Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CyberBorder.copy(alpha = 0.4f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Comment activer le contrôle sur un appareil ?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = if (showInstructions) "Masquer" else "Afficher",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = CyberAmber,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.clickable { showInstructions = !showInstructions }
                        )
                    }

                    if (showInstructions) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sur le smartphone, PC ou tablette connecté à votre point d'accès Wi-Fi :",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        StepItem(number = "1", text = "Ouvrez les paramètres Wi-Fi et touchez le réseau Wi-Fi de votre Infinix.")
                        StepItem(number = "2", text = "Appuyez sur 'Modifier le réseau' > 'Options avancées' > 'Proxy'.")
                        StepItem(number = "3", text = "Sélectionnez 'Manuel' et entrez :")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 12.dp)
                                .background(CyberSurface, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Nom d'hôte du proxy : $proxyHost\nPort du proxy : $proxyPort",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = CyberEmerald
                            )
                        }

                        StepItem(number = "4", text = "Enregistrez : tout son trafic passe dès lors par le serveur proxy local. Vous pouvez alors le bloquer instantanément ou brider sa vitesse !")
                    }
                }
            }
        }

        // Section Tabs: Bloqués par Proxy | Débits Limités | Terminal Root (Optionnel)
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CyberSurfaceElevated,
                contentColor = CyberCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyberCyan
                    )
                },
                modifier = Modifier
                    .background(CyberSurfaceElevated, RoundedCornerShape(12.dp))
                    .testTag("firewall_tab_row")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Bloqués (${devices.count { it.isBlocked }})",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Limités (${devices.count { it.bandwidthLimitKbps != null && !it.isBlocked }})",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Mode Root (IPTables)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                )
            }
        }

        // Tab Content
        when (selectedTab) {
            0 -> {
                // Blocked Devices List
                val blockedDevices = devices.filter { it.isBlocked }
                if (blockedDevices.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.CheckCircle,
                            iconColor = CyberEmerald,
                            title = "Aucun appareil bloqué",
                            description = "Tous les appareils connectés ont actuellement accès au point d'accès."
                        )
                    }
                } else {
                    items(blockedDevices, key = { it.id }) { device ->
                        BlockedDeviceRow(
                            device = device,
                            onUnblock = { viewModel.toggleBlockDevice(device) }
                        )
                    }
                }
            }
            1 -> {
                // Throttled Devices List
                val throttledDevices = devices.filter { it.bandwidthLimitKbps != null && !it.isBlocked }
                if (throttledDevices.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Speed,
                            iconColor = CyberAmber,
                            title = "Aucun appareil bridé",
                            description = "Définissez un débit maximal (ex. 256 Ko/s, 512 Ko/s) pour un appareil depuis l'onglet Appareils."
                        )
                    }
                } else {
                    items(throttledDevices, key = { it.id }) { device ->
                        ThrottledDeviceRow(
                            device = device,
                            onChangeLimit = { onSelectDeviceForLimit(device) },
                            onRemoveLimit = { viewModel.setBandwidthLimit(device, null) }
                        )
                    }
                }
            }
            2 -> {
                // Root & ADB IPTables Console
                item {
                    RootTerminalCard(
                        hotspotState = hotspotState,
                        rootLog = rootLog,
                        onExecuteRootCommand = { viewModel.executeRootCommand(it) }
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
private fun StepItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(CyberAmber.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CyberAmber
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = TextPrimary
        )
    }
}

@Composable
private fun BlockedDeviceRow(
    device: ConnectedDevice,
    onUnblock: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCrimson.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CyberCrimson.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = CyberCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Bloqué par Proxy • ${device.ipAddress}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = CyberCrimson
                    )
                }
            }

            Button(
                onClick = onUnblock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberEmerald.copy(alpha = 0.15f),
                    contentColor = CyberEmerald
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Débloquer", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun ThrottledDeviceRow(
    device: ConnectedDevice,
    onChangeLimit: () -> Unit,
    onRemoveLimit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CyberAmber.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = CyberAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Bridé à : ${device.bandwidthLimitKbps} Ko/s • IP : ${device.ipAddress}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = CyberAmber
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onChangeLimit,
                    modifier = Modifier
                        .size(34.dp)
                        .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifier",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onRemoveLimit,
                    modifier = Modifier
                        .size(34.dp)
                        .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = CyberCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RootTerminalCard(
    hotspotState: com.example.model.HotspotState,
    rootLog: String,
    onExecuteRootCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val sampleIp = "192.168.43.50"
    val sampleIptablesDrop = IptablesController.generateBlockIpCommand(sampleIp)
    val sampleAdb = IptablesController.generateAdbCommand(sampleIptablesDrop)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CyberSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = CyberEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "MODE ROOT KERNEL (IPTABLES)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Alternative avec accès Root : permet de couper ou limiter le trafic directement au niveau du noyau Linux (FORWARD chain) sans configurer de proxy sur l'appareil client.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Exemple règle blocage :",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = sampleIptablesDrop,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = CyberCyan
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", sampleAdb))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copier ADB", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = { onExecuteRootCommand("iptables -L FORWARD -n -v") },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = CyberSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lister FORWARD", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (rootLog.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "SORTIE TERMINAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = rootLog,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF00FF66)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CyberSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
