package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TrafficSample
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.max

@Composable
fun LiveTrafficWaveform(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberSurfaceElevated, RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("live_traffic_waveform_card")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DÉBIT RÉSEAU EN TEMPS RÉEL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = TextSecondary
                )
                Text(
                    text = "Historique 30 secondes",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextMuted
                )
            }

            // Legend indicators
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendPill(color = CyberCyan, label = "Download (Rx)")
                LegendPill(color = CyberAmber, label = "Upload (Tx)")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Canvas Graph
        val maxSpeed = remember(samples) {
            val maxRx = samples.maxOfOrNull { it.rxSpeedKbps } ?: 100f
            val maxTx = samples.maxOfOrNull { it.txSpeedKbps } ?: 100f
            max(max(maxRx, maxTx), 200f) * 1.15f // 15% headroom
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val width = size.width
                val height = size.height

                // Grid lines (horizontal)
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = height * (i.toFloat() / gridLines)
                    drawLine(
                        color = CyberSurfaceVariant.copy(alpha = 0.6f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (samples.size < 2) return@Canvas

                val stepX = width / (samples.size - 1).coerceAtLeast(1)

                // Paths for Rx (Download)
                val rxPath = Path()
                val rxFillPath = Path()

                // Paths for Tx (Upload)
                val txPath = Path()
                val txFillPath = Path()

                samples.forEachIndexed { index, sample ->
                    val x = index * stepX
                    val yRx = (height - (sample.rxSpeedKbps / maxSpeed) * height).coerceIn(0f, height)
                    val yTx = (height - (sample.txSpeedKbps / maxSpeed) * height).coerceIn(0f, height)

                    if (index == 0) {
                        rxPath.moveTo(x, yRx)
                        rxFillPath.moveTo(x, height)
                        rxFillPath.lineTo(x, yRx)

                        txPath.moveTo(x, yTx)
                        txFillPath.moveTo(x, height)
                        txFillPath.lineTo(x, yTx)
                    } else {
                        // Smooth cubic bezier or straight lines
                        val prevX = (index - 1) * stepX
                        val prevRxY = (height - (samples[index - 1].rxSpeedKbps / maxSpeed) * height).coerceIn(0f, height)
                        val prevTxY = (height - (samples[index - 1].txSpeedKbps / maxSpeed) * height).coerceIn(0f, height)

                        val cx = (prevX + x) / 2f
                        rxPath.cubicTo(cx, prevRxY, cx, yRx, x, yRx)
                        rxFillPath.cubicTo(cx, prevRxY, cx, yRx, x, yRx)

                        txPath.cubicTo(cx, prevTxY, cx, yTx, x, yTx)
                        txFillPath.cubicTo(cx, prevTxY, cx, yTx, x, yTx)
                    }

                    if (index == samples.size - 1) {
                        rxFillPath.lineTo(x, height)
                        rxFillPath.close()

                        txFillPath.lineTo(x, height)
                        txFillPath.close()
                    }
                }

                // Draw Fills
                drawPath(
                    path = rxFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(CyberCyan.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                drawPath(
                    path = txFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(CyberAmber.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                // Draw Lines
                drawPath(
                    path = rxPath,
                    color = CyberCyan,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                drawPath(
                    path = txPath,
                    color = CyberAmber,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Top scale indicator
            val topScaleText = if (maxSpeed >= 1024) String.format(Locale.US, "%.1f Mo/s", maxSpeed / 1024f)
            else String.format(Locale.US, "%.0f Ko/s", maxSpeed)

            Text(
                text = topScaleText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextMuted,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextSecondary
        )
    }
}
