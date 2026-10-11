package com.qmusic

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.qmusic.data.local.PrivacyPref
import com.qmusic.ui.components.PrivacyDialog
import com.qmusic.ui.navigation.MainScreen
import com.qmusic.ui.theme.LocalThemeController
import com.qmusic.ui.theme.QMusicTheme
import com.qmusic.ui.theme.ThemeController
import com.qmusic.ui.theme.ThemePref
import com.qmusic.ui.theme.ThemeStyle
import com.qmusic.ui.theme.currentBrand

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 初始化全局品牌色（与持久化主题一致）
        currentBrand = ThemeStyle.fromId(ThemePref.getThemeId(this)).brand
        setContent {
            val context = LocalContext.current
            var themeId by remember { mutableStateOf(ThemePref.getThemeId(context)) }
            val themeStyle = ThemeStyle.fromId(themeId)
            val themeController = ThemeController(
                current = themeStyle,
                setTheme = { style ->
                    themeId = style.id
                    currentBrand = style.brand
                    ThemePref.setThemeId(context, style.id)
                },
            )
            var privacyAccepted by remember { mutableStateOf(PrivacyPref.isAccepted(context)) }

            CompositionLocalProvider(LocalThemeController provides themeController) {
                QMusicTheme(themeStyle = themeStyle) {
                    MainScreen()

                    // 首次启动隐私政策弹窗（必须明确同意）
                    if (!privacyAccepted) {
                        PrivacyDialog(
                            onAgree = {
                                PrivacyPref.setAccepted(context, true)
                                privacyAccepted = true
                            },
                            onDisagree = {
                                (context as? Activity)?.finish()
                            },
                        )
                    }
                }
            }
        }
    }
}
