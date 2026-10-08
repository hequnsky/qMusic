package com.qmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = GradientStart,
    onPrimaryContainer = Color.White,
    secondary = Accent2,
    onSecondary = Color.White,
    background = Background,
    onBackground = TextPrimary,
    surface = Card,
    onSurface = TextPrimary,
    surfaceVariant = DividerLine,
    onSurfaceVariant = TextSecondary,
    outline = DividerLine,
    error = Color(0xFFB3261E),
)

@Composable
fun QMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}