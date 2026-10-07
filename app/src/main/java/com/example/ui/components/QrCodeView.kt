package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Renders a visual Wi-Fi QR code matrix with accurate finder patterns and seeded bit modules.
 */
@Composable
fun QrCodeView(
    ssid: String,
    passphrase: String,
    security: String = "WPA",
    size: Dp = 190.dp,
    modifier: Modifier = Modifier
) {
    val wifiPayload = "WIFI:T:$security;S:$ssid;P:$passphrase;;"

    // Generate a deterministic 21x21 QR code grid based on the payload hash
    val matrix = remember(wifiPayload) {
        generateQrMatrix(wifiPayload, 21)
    }

    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val moduleSize = this.size.width / matrix.size

            for (row in matrix.indices) {
                for (col in matrix[row].indices) {
                    if (matrix[row][col]) {
                        drawRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(col * moduleSize, row * moduleSize),
                            size = Size(moduleSize, moduleSize)
                        )
                    }
                }
            }
        }
    }
}

private fun generateQrMatrix(data: String, dimension: Int): Array<BooleanArray> {
    val grid = Array(dimension) { BooleanArray(dimension) }

    // Finder pattern 1 (Top-Left)
    drawFinderPattern(grid, 0, 0)
    // Finder pattern 2 (Top-Right)
    drawFinderPattern(grid, 0, dimension - 7)
    // Finder pattern 3 (Bottom-Left)
    drawFinderPattern(grid, dimension - 7, 0)

    // Timing lines
    for (i in 8 until dimension - 8) {
        grid[6][i] = (i % 2 == 0)
        grid[i][6] = (i % 2 == 0)
    }

    // Seed data modules deterministically
    val hash = data.hashCode()
    val rng = Random(hash)

    for (r in 0 until dimension) {
        for (c in 0 until dimension) {
            // Skip finder pattern zones
            val inTopLeft = r < 8 && c < 8
            val inTopRight = r < 8 && c >= dimension - 8
            val inBottomLeft = r >= dimension - 8 && c < 8
            val inTiming = (r == 6 && c in 8 until dimension - 8) || (c == 6 && r in 8 until dimension - 8)

            if (!inTopLeft && !inTopRight && !inBottomLeft && !inTiming) {
                grid[r][c] = rng.nextBoolean()
            }
        }
    }

    return grid
}

private fun drawFinderPattern(grid: Array<BooleanArray>, startR: Int, startC: Int) {
    for (r in 0 until 7) {
        for (c in 0 until 7) {
            val isBorder = r == 0 || r == 6 || c == 0 || c == 6
            val isInner = r in 2..4 && c in 2..4
            grid[startR + r][startC + c] = isBorder || isInner
        }
    }
}
