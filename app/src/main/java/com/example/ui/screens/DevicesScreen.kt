package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectedDevice
import com.example.ui.components.DeviceItemCard
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
import com.example.viewmodel.DeviceFilter
import com.example.viewmodel.HotspotViewModel

@Composable
fun DevicesScreen(
    viewModel: HotspotViewModel,
    onSelectDeviceForLimit: (ConnectedDevice) -> Unit,
    onSelectDeviceForQuota: (ConnectedDevice) -> Unit,
    onSelectDeviceForDetails: (ConnectedDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredDevices by viewModel.filteredDevices.collectAsState()
    val allDevices by viewModel.devices.collectAsState()
    val hotspotState by viewModel.hotspotState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search & Refresh Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Rechercher IP, MAC, Fabricant...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Recherche",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Effacer",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CyberSurfaceElevated,
                    unfocusedContainerColor = CyberSurfaceElevated,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = CyberBorder.copy(alpha = 0.5f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("device_search_bar")
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Scan Button
            Button(
                onClick = { viewModel.scanNetwork() },
                enabled = !hotspotState.isScanning,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF0F172A)
                ),
                modifier = Modifier
                    .height(56.dp)
                    .testTag("scan_network_button")
            ) {
                if (hotspotState.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF0F172A),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.WifiFind,
                        contentDescription = "Scanner",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterPill(
                    label = "Tous (${allDevices.size})",
                    isSelected = selectedFilter == DeviceFilter.ALL,
                    color = CyberCyan,
                    onClick = { viewModel.setSelectedFilter(DeviceFilter.ALL) }
                )
            }
            item {
                FilterPill(
                    label = "Actifs (${allDevices.count { it.isOnline && !it.isBlocked }})",
                    isSelected = selectedFilter == DeviceFilter.ACTIVE,
                    color = CyberEmerald,
                    onClick = { viewModel.setSelectedFilter(DeviceFilter.ACTIVE) }
                )
            }
            item {
                FilterPill(
                    label = "Limités (${allDevices.count { it.bandwidthLimitKbps != null && !it.isBlocked }})",
                    isSelected = selectedFilter == DeviceFilter.THROTTLED,
                    color = CyberAmber,
                    onClick = { viewModel.setSelectedFilter(DeviceFilter.THROTTLED) }
                )
            }
            item {
                FilterPill(
                    label = "Bloqués (${allDevices.count { it.isBlocked }})",
                    isSelected = selectedFilter == DeviceFilter.BLOCKED,
                    color = CyberCrimson,
                    onClick = { viewModel.setSelectedFilter(DeviceFilter.BLOCKED) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Devices List
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CyberSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Aucun appareil trouvé"
                            else if (hotspotState.isHotspotActive) "En attente d'appareils connectés..."
                            else "Point d'accès inactif",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Modifiez vos critères de recherche."
                            else if (hotspotState.isHotspotActive) "Point d'accès actif (${hotspotState.gatewayIp}). Connectez un téléphone ou PC à votre point d'accès. La détection s'effectue automatiquement en continu."
                            else "Activez le point d'accès Wi-Fi dans les paramètres de votre Infinix pour commencer à surveiller les appareils.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                            Text("Scanner manuellement")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredDevices, key = { it.id }) { device ->
                    DeviceItemCard(
                        device = device,
                        onToggleBlock = { viewModel.toggleBlockDevice(device) },
                        onOpenLimitDialog = { onSelectDeviceForLimit(device) },
                        onOpenQuotaDialog = { onSelectDeviceForQuota(device) },
                        onOpenDetailDialog = { onSelectDeviceForDetails(device) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) color.copy(alpha = 0.2f) else CyberSurfaceElevated,
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (isSelected) color else CyberBorder.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            ),
            color = if (isSelected) color else TextSecondary
        )
    }
}
