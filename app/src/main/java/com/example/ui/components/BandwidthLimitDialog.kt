package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectedDevice
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BandwidthLimitDialog(
    device: ConnectedDevice,
    onDismiss: () -> Unit,
    onApplyLimit: (limitKbps: Int?) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(device.bandwidthLimitKbps) }
    var customLimitText by remember { mutableStateOf(device.bandwidthLimitKbps?.toString() ?: "") }
    var isCustom by remember {
        mutableStateOf(
            device.bandwidthLimitKbps != null &&
                    device.bandwidthLimitKbps !in listOf(128, 256, 512, 1024, 2048, 5120)
        )
    }

    val presets = listOf(
        Pair(128, "128 Ko/s\n(Messagerie)"),
        Pair(256, "256 Ko/s\n(Léger)"),
        Pair(512, "512 Ko/s\n(Standard)"),
        Pair(1024, "1 Mo/s\n(Vidéo 720p)"),
        Pair(2048, "2 Mo/s\n(Rapide)"),
        Pair(5120, "5 Mo/s\n(Haut débit)")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = CyberAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Limiter la bande passante",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "${device.displayName} (${device.ipAddress})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "Sélectionnez un plafond de débit pour cet appareil :",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Presets Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in presets.chunked(3)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for ((kbps, label) in row) {
                                val isSelected = !isCustom && selectedPreset == kbps
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
                                        .clickable {
                                            selectedPreset = kbps
                                            isCustom = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) CyberAmber else TextPrimary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Value Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCustom) CyberAmber.copy(alpha = 0.15f) else CyberSurfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isCustom) CyberAmber else CyberBorder.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { isCustom = true }
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Limite Personnalisée (Ko/s)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCustom) CyberAmber else TextSecondary
                        )
                        if (isCustom) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = customLimitText,
                                onValueChange = { customLimitText = it.filter { char -> char.isDigit() } },
                                placeholder = { Text("Ex: 750", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_limit_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberAmber,
                                    unfocusedBorderColor = CyberBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Unlimited button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (!isCustom && selectedPreset == null) CyberCyan.copy(alpha = 0.2f) else CyberSurfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (!isCustom && selectedPreset == null) CyberCyan else CyberBorder.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            selectedPreset = null
                            isCustom = false
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = if (!isCustom && selectedPreset == null) CyberCyan else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Débit Illimité (Pas de restriction)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (!isCustom && selectedPreset == null) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (!isCustom && selectedPreset == null) CyberCyan else TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalLimit = if (isCustom) {
                        customLimitText.toIntOrNull()?.takeIf { it > 0 }
                    } else {
                        selectedPreset
                    }
                    onApplyLimit(finalLimit)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberAmber, contentColor = CyberSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("apply_limit_button")
            ) {
                Text("Appliquer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = TextSecondary)
            }
        }
    )
}
