package com.qmusic.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import com.qmusic.data.local.entity.LocalPlaylist
import com.qmusic.data.model.Song
import com.qmusic.ui.category.CategoryMusicScreen
import com.qmusic.ui.category.CategorySpec
import com.qmusic.ui.comment.CommentScreen
import com.qmusic.ui.components.MiniPlayerBar
import com.qmusic.ui.components.QMusicBottomBar
import com.qmusic.ui.discover.DiscoverScreen
import com.qmusic.ui.download.DownloadScreen
import com.qmusic.ui.mine.FavoriteSongsScreen
import com.qmusic.ui.mine.HistorySongsScreen
import com.qmusic.ui.mine.MineScreen
import com.qmusic.ui.player.PlayerScreen
import com.qmusic.ui.playlist.HotPlaylistScreen
import com.qmusic.ui.playlist.LocalMusicScreen
import com.qmusic.ui.playlist.MyPlaylistsScreen
import com.qmusic.ui.playlist.PlaylistDetailScreen
import com.qmusic.ui.playlist.PlaylistSongsScreen
import com.qmusic.ui.search.SearchScreen
import com.qmusic.ui.toplist.ToplistScreen

/** 主界面：底部导航 + 页面切换 + 迷你播放条 + 全屏播放页 */
@Composable
fun MainScreen(
) {
    val context = LocalContext.current
    val app = context.applicationContext as QMusicApp
    val playerController = app.playerController

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var playlistDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var hotPlaylistOpen by rememberSaveable { mutableStateOf(false) }
    var toplistOpen by rememberSaveable { mutableStateOf(false) }
    var localMusicOpen by rememberSaveable { mutableStateOf(false) }
    var downloadOpen by rememberSaveable { mutableStateOf(false) }
    var myPlaylistsOpen by rememberSaveable { mutableStateOf(false) }
    var favoriteSongsOpen by rememberSaveable { mutableStateOf(false) }
    var historySongsOpen by rememberSaveable { mutableStateOf(false) }
    // 打开的本地歌单（null = 未打开），直接存歌单对象，详情页才能显示真实歌单名
    var openedLocalPlaylist by remember { mutableStateOf<LocalPlaylist?>(null) }
    var category by remember { mutableStateOf<CategorySpec?>(null) }
    var commentSong by remember { mutableStateOf<Song?>(null) }
    val playerState by playerController.uiState.collectAsState()

    // 系统返回键：非发现页时先回到发现页。
    // 覆盖层（播放页/评论/歌单/搜索建议等）各自的 BackHandler 优先级更高，会先被处理；
    // 这里是兜底，保证任何页面按返回最终都回到发现主页（主页再按返回才退出 App）。
    //
    // 问题 6：原实现 `BackHandler(enabled = selectedTab != 0)` 在主页（selectedTab==0）时
    // 是「禁用」状态，系统回退会走 Activity 默认的退出动画，Compose 树先做一次
    // 无意义的重组再退出 —— 观感就是「按返回闪一下」。
    // 修复：主页也接管返回键，直接 finish()，跳过退出动画与多余重组。
    val activity = context as? Activity
    BackHandler {
        when {
            selectedTab != 0 -> selectedTab = 0
            playerState.song != null -> Unit // 主页有 MiniPlayerBar 时不退出，避免误触
            else -> {
                activity?.finish()
                // 去掉退出转场动画，消除那一下闪烁
                @Suppress("DEPRECATION")
                activity?.overridePendingTransition(0, 0)
            }
        }
    }

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
                    onOpenToplists = { toplistOpen = true },
                )
                1 -> MineScreen(
                    repository = app.repository,
                    onPlaySong = { song ->
                        playerController.play(listOf(song), 0)
                        playerOpen = true
                    },
                    onOpenLocalMusic = { localMusicOpen = true },
                    onOpenDownloads = { downloadOpen = true },
                    onOpenMyPlaylists = { myPlaylistsOpen = true },
                    onOpenFavorites = { favoriteSongsOpen = true },
                    onOpenHistory = { historySongsOpen = true },
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

    // 全部排行榜（点击榜单后进入歌单详情）
    if (toplistOpen) {
        ToplistScreen(
            repository = app.repository,
            onBack = { toplistOpen = false },
            onOpenPlaylist = { id ->
                toplistOpen = false
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

    // 我喜欢的（独立列表页，点列表项即播放）
    if (favoriteSongsOpen) {
        FavoriteSongsScreen(
            repository = app.repository,
            onBack = { favoriteSongsOpen = false },
            onPlaySongs = { songs, index ->
                playerController.play(songs, index)
                favoriteSongsOpen = false
                playerOpen = true
            },
        )
    }

    // 最近播放（独立列表页）
    if (historySongsOpen) {
        HistorySongsScreen(
            repository = app.repository,
            onBack = { historySongsOpen = false },
            onPlaySongs = { songs, index ->
                playerController.play(songs, index)
                historySongsOpen = false
                playerOpen = true
            },
        )
    }

    if (playerOpen) {
        PlayerScreen(
            playerController = playerController,
            repository = app.repository,
            onClose = { playerOpen = false },
            onOpenComment = { song -> commentSong = song },
            // 从播放页进入「我的歌单 / 已下载」时不再关掉播放页，
            // 而是让目标页直接覆盖在播放页之上 —— 返回时自然退回播放页，
            // 避免「播放页瞬间消失 → 目标页出现」造成的闪烁（跳转更顺）。
            onOpenDownloads = { downloadOpen = true },
            onManagePlaylists = { myPlaylistsOpen = true },
        )
    }

    // 我的下载（覆盖层，可叠加在播放页之上）
    if (downloadOpen) {
        DownloadScreen(
            onBack = { downloadOpen = false },
            onPlaySongs = { songs, index ->
                playerController.play(songs, index)
                downloadOpen = false
                playerOpen = true
            },
        )
    }

    // 我的歌单（本地歌单管理：创建/重命名/删除）
    if (myPlaylistsOpen) {
        MyPlaylistsScreen(
            repository = app.repository,
            onBack = { myPlaylistsOpen = false },
            onOpenPlaylist = { playlist ->
                // 详情页叠加在歌单列表之上，返回时回到歌单列表（而不是直接跳回播放页）
                openedLocalPlaylist = playlist
            },
        )
    }

    // 本地歌单详情（叠加在「我的歌单」之上）
    openedLocalPlaylist?.let { playlist ->
        PlaylistSongsScreen(
            repository = app.repository,
            playlistId = playlist.id,
            playlistName = playlist.name,
            onBack = { openedLocalPlaylist = null },
            onPlaySongs = { songs, index ->
                playerController.play(songs, index)
                openedLocalPlaylist = null
                // 播放后收起歌单相关页面，露出播放页
                myPlaylistsOpen = false
                playerOpen = true
            },
        )
    }

    // 歌曲评论页（必须覆盖在播放页之上，否则点击评论没有反应）
    commentSong?.let { song ->
        CommentScreen(
            songId = song.id,
            songName = song.name,
            repository = app.repository,
            onBack = { commentSong = null },
        )
    }
}