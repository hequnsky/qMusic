package com.qmusic.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 当前生效的品牌色（文件级快照状态，换主题时整体换色；全 app 仍直接写 Accent / GradientStart 等）。
// 用可变快照状态而非 CompositionLocal：读取 .value 不是 @Composable 操作，
// 因此 Accent 等可以写成普通顶层 getter；同时读 .value 仍会被组合订阅，换主题时自动重组。
var currentBrand by mutableStateOf(ThemeStyle.CLASSIC.brand)

data class BrandColors(
    val accent: Color,
    val accent2: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
)

/**
 * 主题风格：默认都是深色，仅换强调色 / 渐变，保持与现默认深色一致。
 * 另含一个浅色选项，作为「浅色」主题。
 */
enum class ThemeStyle(
    val id: String,
    val label: String,
    val dark: Boolean,
    val brand: BrandColors,
) {
    CLASSIC("classic", "经典红", true, BrandColors(Color(0xFFFF4B4B), Color(0xFFFF8A3D), Color(0xFFFF4B5C), Color(0xFFFF8A3D))),
    PURPLE("purple", "星河紫", true, BrandColors(Color(0xFF9B5DE5), Color(0xFF7B5CFF), Color(0xFF8A5CFF), Color(0xFFB05CFF))),
    BLUE("blue", "深海蓝", true, BrandColors(Color(0xFF3DA5FF), Color(0xFF2E7BFF), Color(0xFF3DA5FF), Color(0xFF2E7BFF))),
    GREEN("green", "极光绿", true, BrandColors(Color(0xFF2BD9A0), Color(0xFF16C2C2), Color(0xFF2BD9A0), Color(0xFF16C2C2))),
    PINK("pink", "蜜桃粉", true, BrandColors(Color(0xFFFF6FA5), Color(0xFFFF9F6B), Color(0xFFFF6FA5), Color(0xFFFF9F6B))),
    LIGHT("light", "浅色", false, BrandColors(Color(0xFFFF4B4B), Color(0xFFFF8A3D), Color(0xFFFF4B5C), Color(0xFFFF8A3D))),
    ;

    companion object {
        fun fromId(id: String?): ThemeStyle =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

// 兼容旧写法：全 app 仍直接写 Accent / Accent2 / GradientStart / GradientEnd，
// 这里读取当前生效品牌色的「动态取值」，换主题时自动整体换色。
val Accent: Color get() = currentBrand.accent
val Accent2: Color get() = currentBrand.accent2
val GradientStart: Color get() = currentBrand.gradientStart
val GradientEnd: Color get() = currentBrand.gradientEnd

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

/** 主题偏好持久化（SharedPreferences），存储主题 id（默认经典红） */
object ThemePref {
    private const val NAME = "qmusic_theme"
    private const val KEY_THEME = "theme_id"
    private const val KEY_DARK_OLD = "dark_mode"

    fun getThemeId(context: Context): String {
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        var id = sp.getString(KEY_THEME, null)
        if (id.isNullOrEmpty()) {
            // 兼容旧版 dark_mode 布尔：深色 -> 经典红，浅色 -> 浅色主题
            id = if (sp.getBoolean(KEY_DARK_OLD, true)) ThemeStyle.CLASSIC.id
            else ThemeStyle.LIGHT.id
        }
        return id
    }

    fun setThemeId(context: Context, id: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, id).apply()
    }
}

/** 主题控制器：持有当前主题并提供切换入口，由 MainActivity 在根部注入 */
data class ThemeController(
    val current: ThemeStyle,
    val setTheme: (ThemeStyle) -> Unit,
)

val LocalThemeController = staticCompositionLocalOf {
    ThemeController(ThemeStyle.CLASSIC) {}
}

@Composable
fun QMusicTheme(
    themeStyle: ThemeStyle = ThemeStyle.CLASSIC,
    content: @Composable () -> Unit,
) {
    val colors = if (themeStyle.dark) DarkQMusicColors else LightQMusicColors
    val brand = themeStyle.brand
    val colorScheme = if (themeStyle.dark) {
        darkColorScheme(
            primary = brand.accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF5A2A33),
            onPrimaryContainer = Color.White,
            secondary = brand.accent2,
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
    } else {
        lightColorScheme(
            primary = brand.accent,
            onPrimary = Color.White,
            primaryContainer = brand.gradientStart,
            onPrimaryContainer = Color.White,
            secondary = brand.accent2,
            onSecondary = Color.White,
            background = Color(0xFFF7F7F9),
            onBackground = Color(0xFF15161A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF15161A),
            surfaceVariant = Color(0xFFF0F0F2),
            onSurfaceVariant = Color(0xFF8A8A93),
            outline = Color(0xFFF0F0F2),
            error = Color(0xFFB3261E),
        )
    }
    CompositionLocalProvider(LocalQMusicColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.compose.material3.Typography(),
            content = content,
        )
    }
}
