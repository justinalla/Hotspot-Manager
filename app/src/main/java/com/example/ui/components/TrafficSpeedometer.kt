package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TrafficSpeedometer(
    downloadSpeedKbps: Float,
    uploadSpeedKbps: Float,
    peakDownloadKbps: Float,
    peakUploadKbps: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberSurfaceElevated, RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("traffic_speedometer_row"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Download Dial
        SpeedDial(
            title = "TÉLÉCHARGEMENT",
            speedKbps = downloadSpeedKbps,
            peakKbps = peakDownloadKbps,
            color = CyberCyan,
            icon = Icons.Default.ArrowDownward,
            maxScaleKbps = 10240f // 10 MB/s scale
        )

        // Divider
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 90.dp)
                .background(CyberSurfaceVariant)
        )

        // Upload Dial
        SpeedDial(
            title = "TÉLÉVERSEMENT",
            speedKbps = uploadSpeedKbps,
            peakKbps = peakUploadKbps,
            color = CyberAmber,
            icon = Icons.Default.ArrowUpward,
            maxScaleKbps = 5120f // 5 MB/s scale
        )
    }
}

@Composable
private fun SpeedDial(
    title: String,
    speedKbps: Float,
    peakKbps: Float,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    maxScaleKbps: Float
) {
    // 0f to 1f normalized ratio
    val normalized = (speedKbps / maxScaleKbps).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(durationMillis = 600),
        label = "speed_progress"
    )

    val formattedSpeed = formatSpeed(speedKbps)
    val formattedPeak = formatSpeed(peakKbps)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(110.dp)
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val strokeWidth = 8.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                val startAngle = 135f
                val sweepAngle = 270f

                // Track Background Arc
                drawArc(
                    color = Color(0xFF1E293B),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active Gradient Arc
                if (animatedProgress > 0.01f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(color.copy(alpha = 0.4f), color),
                            center = center
                        ),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Needle tip indicator
                val currentAngleRad = Math.toRadians((startAngle + sweepAngle * animatedProgress).toDouble())
                val indicatorX = (center.x + radius * cos(currentAngleRad)).toFloat()
                val indicatorY = (center.y + radius * sin(currentAngleRad)).toFloat()

                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(indicatorX, indicatorY)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formattedSpeed.first,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = TextPrimary
                )
                Text(
                    text = formattedSpeed.second,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Pic: ${formattedPeak.first} ${formattedPeak.second}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextMuted
        )
    }
}

fun formatSpeed(speedKbps: Float): Pair<String, String> {
    return if (speedKbps >= 1024f) {
        val mbps = speedKbps / 1024f
        Pair(String.format(Locale.US, "%.1f", mbps), "Mo/s")
    } else {
        Pair(String.format(Locale.US, "%.0f", speedKbps), "Ko/s")
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f Go", bytes / 1_073_741_824f)
        bytes >= 1_048_576L -> String.format(Locale.US, "%.1f Mo", bytes / 1_048_576f)
        bytes >= 1024L -> String.format(Locale.US, "%.0f Ko", bytes / 1024f)
        else -> "$bytes octets"
    }
}
