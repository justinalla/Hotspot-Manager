package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = CyberBackground,
    primaryContainer = CyberSurfaceElevated,
    onPrimaryContainer = CyberCyan,
    secondary = CyberBlue,
    onSecondary = CyberBackground,
    secondaryContainer = CyberSurfaceVariant,
    onSecondaryContainer = CyberBlue,
    tertiary = CyberEmerald,
    onTertiary = CyberBackground,
    background = CyberBackground,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = CyberCrimson,
    onError = TextPrimary,
    outline = CyberBorder,
    outlineVariant = CyberSurfaceVariant
)

private val LightColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = CyberBackground,
    background = CyberBackground,
    surface = CyberSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For Infinix Hot 9 (Android 10) and dark cyber telemetry aesthetic, DarkColorScheme is default
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = CyberBackground.toArgb()
                window.navigationBarColor = CyberBackground.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
