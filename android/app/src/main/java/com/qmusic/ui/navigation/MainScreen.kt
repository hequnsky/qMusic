package com.qmusic.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.qmusic.QMusicApp
import com.qmusic.ui.components.MiniPlayerBar
import com.qmusic.ui.components.QMusicBottomBar
import com.qmusic.ui.discover.DiscoverScreen
import com.qmusic.ui.mine.MineScreen
import com.qmusic.ui.player.PlayerScreen
import com.qmusic.ui.playlist.PlaylistDetailScreen
import com.qmusic.ui.search.SearchScreen

/** 主界面：底部导航 + 页面切换 + 迷你播放条 + 全屏播放页 */
@Composable
fun MainScreen(
    darkMode: Boolean = false,
    onThemeChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as QMusicApp
    val playerController = app.playerController

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var playlistDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    val playerState by playerController.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (playerState.song != null) {
                    MiniPlayerBar(
                        state = playerState,
                        onClick = { playerOpen = true },
                        onToggle = { playerController.toggle() },
                    )
                }
                QMusicBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onPlayTab = {
                        if (playerState.song != null) playerOpen = true
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DiscoverScreen(
                    repository = app.repository,
                    onPlayAll = { songs, index ->
                        playerController.play(songs, index)
                        playerOpen = true
                    },
                    onOpenSearch = { selectedTab = 2 },
                    onOpenMine = { selectedTab = 1 },
                    onOpenPlaylist = { playlistDetailId = it },
                )
                1 -> MineScreen(
                    repository = app.repository,
                    darkMode = darkMode,
                    onThemeChanged = onThemeChanged,
                    onPlaySong = { song ->
                        playerController.play(listOf(song), 0)
                        playerOpen = true
                    },
                )
                2 -> SearchScreen(
                    repository = app.repository,
                    onPlaySong = { songs, index ->
                        playerController.play(songs, index)
                        playerOpen = true
                    },
                )
            }
        }
    }

    playlistDetailId?.let { id ->
        PlaylistDetailScreen(
            playlistId = id,
            repository = app.repository,
            onBack = { playlistDetailId = null },
            onPlayAll = { songs, index ->
                playerController.play(songs, index)
                playerOpen = true
            },
        )
    }

    if (playerOpen) {
        PlayerScreen(
            playerController = playerController,
            repository = app.repository,
            onClose = { playerOpen = false },
        )
    }
}