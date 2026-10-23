package com.vidplay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VidPlayColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF0B0B10),
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color(0xFF00363D),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFF2F0FA),
    surface = Color(0xFF15151E),
    onSurface = Color(0xFFF2F0FA),
    surfaceVariant = Color(0xFF23232F),
    onSurfaceVariant = Color(0xFFC9C5D6),
    outline = Color(0xFF8F8AA3),
    error = Color(0xFFFFB4AB),
)

@Composable
fun VidPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VidPlayColors,
        typography = Typography(),
        content = content,
    )
}
