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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.qmusic.QMusicApp
import com.qmusic.data.model.Song
import com.qmusic.ui.category.CategoryMusicScreen
import com.qmusic.ui.category.CategorySpec
import com.qmusic.ui.comment.CommentScreen
import com.qmusic.ui.components.MiniPlayerBar
import com.qmusic.ui.components.QMusicBottomBar
import com.qmusic.ui.discover.DiscoverScreen
import com.qmusic.ui.mine.MineScreen
import com.qmusic.ui.player.PlayerScreen
import com.qmusic.ui.playlist.HotPlaylistScreen
import com.qmusic.ui.playlist.LocalMusicScreen
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
    var hotPlaylistOpen by rememberSaveable { mutableStateOf(false) }
    var localMusicOpen by rememberSaveable { mutableStateOf(false) }
    var category by remember { mutableStateOf<CategorySpec?>(null) }
    var commentSong by remember { mutableStateOf<Song?>(null) }
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
                    onOpenMorePlaylists = { hotPlaylistOpen = true },
                    onOpenCategory = { category = it },
                )
                1 -> MineScreen(
                    repository = app.repository,
                    darkMode = darkMode,
                    onThemeChanged = onThemeChanged,
                    onPlaySong = { song ->
                        playerController.play(listOf(song), 0)
                        playerOpen = true
                    },
                    onOpenLocalMusic = { localMusicOpen = true },
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

    // 全部热门歌单（覆盖在歌单详情之上，点击歌单后进入详情）
    if (hotPlaylistOpen) {
        HotPlaylistScreen(
            repository = app.repository,
            onBack = { hotPlaylistOpen = false },
            onOpenPlaylist = { id ->
                hotPlaylistOpen = false
                playlistDetailId = id
            },
        )
    }

    // 本地音乐列表（覆盖层）
    if (localMusicOpen) {
        LocalMusicScreen(
            onBack = { localMusicOpen = false },
            onPlaySongs = { songs, index ->
                playerController.play(songs, index)
                localMusicOpen = false
                playerOpen = true
            },
        )
    }

    // 分类音乐页（每日推荐/热歌榜/新歌速递/华语/欧美/纯音乐/摇滚/民谣）
    category?.let { spec ->
        CategoryMusicScreen(
            spec = spec,
            repository = app.repository,
            onBack = { category = null },
            onPlaySongs = { songs, index ->
                playerController.play(songs, index)
                category = null
                playerOpen = true
            },
            onOpenPlaylist = { id ->
                category = null
                playlistDetailId = id
            },
        )
    }

    // 歌曲评论页
    commentSong?.let { song ->
        CommentScreen(
            songId = song.id,
            songName = song.name,
            repository = app.repository,
            onBack = { commentSong = null },
        )
    }

    if (playerOpen) {
        PlayerScreen(
            playerController = playerController,
            repository = app.repository,
            onClose = { playerOpen = false },
            onOpenComment = { song -> commentSong = song },
        )
    }
}