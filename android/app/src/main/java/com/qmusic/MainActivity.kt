package com.qmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.qmusic.ui.navigation.MainScreen
import com.qmusic.ui.theme.QMusicTheme
import com.qmusic.ui.theme.ThemePref

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var darkTheme by remember { mutableStateOf(ThemePref.isDark(context)) }
            QMusicTheme(darkTheme = darkTheme) {
                MainScreen(
                    darkMode = darkTheme,
                    onThemeChanged = { dark ->
                        darkTheme = dark
                        ThemePref.setDark(context, dark)
                    },
                )
            }
        }
    }
}