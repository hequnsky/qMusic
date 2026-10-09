package com.qmusic.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A2A33),
    onPrimaryContainer = Color.White,
    secondary = Accent2,
    onSecondary = Color.White,
    background = Color(0xFF121216),
    onBackground = Color(0xFFECECEF),
    surface = Color(0xFF1E1E24),
    onSurface = Color(0xFFECECEF),
    surfaceVariant = Color(0xFF2A2A31),
    onSurfaceVariant = Color(0xFF9A9AA3),
    outline = Color(0xFF2A2A31),
    error = Color(0xFFFF6B6B),
)

/** 应用自定义配色（供非 Material 组件使用，可随深浅色切换） */
data class QMusicColors(
    val background: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val bottomBar: Color,
    val tabInactive: Color,
)

val LightQMusicColors = QMusicColors(
    background = Color(0xFFF7F7F9),
    card = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF15161A),
    textSecondary = Color(0xFF8A8A93),
    textTertiary = Color(0xFFB9B9C0),
    divider = Color(0xFFF0F0F2),
    bottomBar = Color.White,
    tabInactive = Color(0xFF9A9AA2),
)

val DarkQMusicColors = QMusicColors(
    background = Color(0xFF121216),
    card = Color(0xFF1E1E24),
    textPrimary = Color(0xFFECECEF),
    textSecondary = Color(0xFF9A9AA3),
    textTertiary = Color(0xFF6C6C75),
    divider = Color(0xFF2A2A31),
    bottomBar = Color(0xFF1A1A20),
    tabInactive = Color(0xFF8A8A93),
)

val LocalQMusicColors = staticCompositionLocalOf { LightQMusicColors }

/** 主题偏好持久化（SharedPreferences） */
object ThemePref {
    private const val NAME = "qmusic_theme"
    private const val KEY_DARK_MODE = "dark_mode"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, dark)
            .apply()
    }
}

@Composable
fun QMusicTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkQMusicColors else LightQMusicColors
    val colorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalQMusicColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.compose.material3.Typography(),
            content = content,
        )
    }
}