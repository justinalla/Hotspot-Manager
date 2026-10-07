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
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DeviceQuotaDialog(
    device: ConnectedDevice,
    onDismiss: () -> Unit,
    onApplyQuota: (quotaBytes: Long?) -> Unit
) {
    val presets = listOf(
        Pair(100_000_000L, "100 Mo"),
        Pair(250_000_000L, "250 Mo"),
        Pair(500_000_000L, "500 Mo"),
        Pair(1_000_000_000L, "1 Go"),
        Pair(2_000_000_000L, "2 Go"),
        Pair(5_000_000_000L, "5 Go")
    )

    var selectedQuota by remember { mutableStateOf(device.quotaBytes) }
    var customMbText by remember {
        mutableStateOf(
            device.quotaBytes?.let { (it / 1_000_000).toString() } ?: ""
        )
    }
    var isCustom by remember {
        mutableStateOf(
            device.quotaBytes != null &&
                    presets.none { it.first == device.quotaBytes }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DataUsage,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Définir un Quota de Données",
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
                    text = "L'appareil sera automatiquement bloqué dès que son volume de données dépassera ce quota :",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Presets
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in presets.chunked(3)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for ((bytes, label) in row) {
                                val isSelected = !isCustom && selectedQuota == bytes
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) CyberCyan.copy(alpha = 0.2f) else CyberSurfaceVariant,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) CyberCyan else CyberBorder.copy(alpha = 0.5f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedQuota = bytes
                                            isCustom = false
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) CyberCyan else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom quota input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCustom) CyberCyan.copy(alpha = 0.15f) else CyberSurfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isCustom) CyberCyan else CyberBorder.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { isCustom = true }
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quota Personnalisé (en Mo)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCustom) CyberCyan else TextSecondary
                        )
                        if (isCustom) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = customMbText,
                                onValueChange = { customMbText = it.filter { char -> char.isDigit() } },
                                placeholder = { Text("Ex: 700", color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_quota_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = CyberBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Remove quota button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (!isCustom && selectedQuota == null) CyberCyan.copy(alpha = 0.2f) else CyberSurfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (!isCustom && selectedQuota == null) CyberCyan else CyberBorder.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            selectedQuota = null
                            isCustom = false
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = null,
                            tint = if (!isCustom && selectedQuota == null) CyberCyan else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Aucun Quota (Illimité)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (!isCustom && selectedQuota == null) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (!isCustom && selectedQuota == null) CyberCyan else TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalQuota = if (isCustom) {
                        customMbText.toLongOrNull()?.let { it * 1_000_000L }
                    } else {
                        selectedQuota
                    }
                    onApplyQuota(finalQuota)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("apply_quota_button")
            ) {
                Text("Enregistrer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = TextSecondary)
            }
        }
    )
}
