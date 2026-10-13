package com.qmusic.ui.video

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.qmusic.QMusicApp
import com.qmusic.data.model.VideoData
import java.util.Locale

/** 类抖音竖屏视频信息流页（覆盖层，全屏） */
@Composable
fun VideoFeedScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as QMusicApp
    val viewModel: VideoFeedViewModel = viewModel(
        factory = viewModelFactory {
            initializer { VideoFeedViewModel(app.repository) }
        },
    )
    val state by viewModel.uiState.collectAsState()

    // 全屏黑色背景 + 状态栏沉浸
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading && state.videos.isEmpty() -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.error != null && state.videos.isEmpty() -> {
                val err = state.error ?: "加载失败"
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(err, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "点击重试",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(Color(0x66FFFFFF), CircleShape)
                            .clickable { viewModel.refresh() }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
            else -> {
                VideoPager(
                    videos = state.videos,
                    onLoadMore = { viewModel.loadMore() },
                )
            }
        }

        // 顶部返回栏
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
            Text(
                "视频",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 全屏垂直翻页视频列表 */
@Composable
private fun VideoPager(
    videos: List<VideoData>,
    onLoadMore: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { videos.size })

    // 当前页变化时：上一页暂停，当前页播放
    LaunchedEffect(pagerState.currentPage, videos.size) {
        if (videos.isEmpty()) return@LaunchedEffect
        val current = pagerState.currentPage
        // 接近底部时加载更多
        if (current >= videos.size - 2) {
            onLoadMore()
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        val video = videos.getOrNull(page) ?: return@VerticalPager
        VideoPage(
            video = video,
            active = page == pagerState.currentPage,
        )
    }
}

/** 单个视频页：全屏视频 + 右侧操作栏 + 底部作者信息 */
@Composable
private fun VideoPage(
    video: VideoData,
    active: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer = remember(video.videoId) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var likedCount by remember { mutableLongStateOf(video.playTime) }
    var showPauseHint by remember { mutableStateOf(false) }

    // 加载视频地址并播放
    LaunchedEffect(video.videoId, active) {
        if (!active) {
            exoPlayer.pause()
            return@LaunchedEffect
        }
        val app = context.applicationContext as QMusicApp
        val url = try {
            app.repository.getVideoUrl(video.videoId)
        } catch (e: Exception) {
            null
        }
        if (url.isNullOrBlank()) return@LaunchedEffect
        val mediaItem = MediaItem.Builder()
            .setMediaId(video.videoId)
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(video.title)
                    .setArtist(video.creator?.nickname)
                    .setArtworkUri(video.coverUrl.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                    .build(),
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    // 生命周期：页面不可见时暂停（避免后台继续播放声音）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // 播放状态监听
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) showPauseHint = false
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isPlaying = exoPlayer.isPlaying
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 视频画面：竖屏视频用 fit 居中，横屏视频裁剪铺满
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }.also { view ->
                    view.player = exoPlayer
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        // 封面占位：仅在视频尚未开始播放时显示（首帧渲染后隐藏，避免遮挡画面）
        if (!isPlaying) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 点击视频区域：暂停/继续
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (isPlaying) {
                        exoPlayer.pause()
                        showPauseHint = true
                    } else {
                        exoPlayer.play()
                        showPauseHint = false
                    }
                },
        )

        // 暂停时中间播放图标
        if (showPauseHint && !isPlaying) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp),
            )
        }

        // 右侧操作栏（点赞/评论/分享/收藏）
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ActionItem(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = formatCount(if (isFavorite) likedCount + 1 else likedCount),
                tint = if (isFavorite) Color(0xFFFF3B5C) else Color.White,
            ) { isFavorite = !isFavorite }
            ActionItem(
                icon = Icons.Filled.Comment,
                label = formatCount(video.commentCount),
                tint = Color.White,
            ) {}
            ActionItem(
                icon = Icons.Filled.Star,
                label = "收藏",
                tint = Color.White,
            ) {}
            ActionItem(
                icon = Icons.Filled.Share,
                label = formatCount(video.shareCount),
                tint = Color.White,
            ) {}

            // 作者头像
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0x66FFFFFF), CircleShape),
            ) {
                AsyncImage(
                    model = video.creator?.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                )
            }
        }

        // 底部作者信息
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 76.dp, bottom = 28.dp),
        ) {
            Text(
                "@${video.creator?.nickname ?: "网易云音乐"}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                video.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            video.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 右侧竖排操作按钮：图标 + 数字 */
@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 数字格式化：1.2w / 3456 */
private fun formatCount(count: Long): String = when {
    count >= 10000 -> String.format(Locale.CHINA, "%.1fw", count / 10000.0)
    count >= 1000 -> String.format(Locale.CHINA, "%.1fk", count / 1000.0)
    else -> count.toString()
}