package com.qmusic.ui.player

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import com.qmusic.R
import com.qmusic.ui.components.CoverWithPlaceholder
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Song
import com.qmusic.data.local.DownloadManager
import com.qmusic.player.PlayerController
import com.qmusic.player.PlayerUiState
import com.qmusic.ui.components.SongCover
import com.qmusic.ui.components.formatDuration
import com.qmusic.ui.playlist.AddToPlaylistSheet
import com.qmusic.ui.theme.Accent
import com.qmusic.ui.theme.Dark
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import com.qmusic.ui.components.topBarTopPadding

@Composable
fun PlayerScreen(
    playerController: PlayerController,
    repository: MusicRepository,
    onClose: () -> Unit,
    onOpenComment: (Song) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onManagePlaylists: () -> Unit = {},
) {
    val viewModel: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlayerViewModel(playerController, repository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 系统返回键：优先关闭播放页内子页面，再关闭整个播放页（而不是直接退出到桌面）
    BackHandler {
        when {
            showLyrics -> showLyrics = false
            showQueue -> showQueue = false
            showMore -> showMore = false
            showAddToPlaylist -> showAddToPlaylist = false
            else -> onClose()
        }
    }

    if (showLyrics) {
        LyricView(state = state, onBack = { showLyrics = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Dark)
            .navigationBarsPadding()
            // 关键：播放页是覆盖在主页之上的浮层。必须在此消费掉所有触摸事件，
            // 否则点击标题/歌手这类「无 handler」区域时，Compose 命中测试会穿透到
            // 下层主页的歌曲行，触发 onPlay 重新播放当前歌曲（表现为「点标题会重复播放」）。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // 背景渐变
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF3A1D4F), Dark, Dark)),
            ),
        )

        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = topBarTopPadding(), start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起", tint = Color.White)
                }
                // 只保留「正在播放」。原先下面还有一行写死的「汽水音乐 · 每日推荐」，
                // 但现在音源不止汽水（网易云/QQ/酷狗/酷我等多源聚合），写死的来源文案已经不成立，去掉。
                Text(
                    stringResource(R.string.currently_playing),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showLyrics = true }) {
                    Icon(Icons.Filled.Notes, contentDescription = "歌词", tint = Color.White)
                }
                // 更多菜单：分享 / 定时关闭
                Box {
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMore,
                        onDismissRequest = { showMore = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            leadingIcon = { MenuIcon(Icons.Filled.Share) },
                            onClick = {
                                showMore = false
                                val song = state.song ?: return@DropdownMenuItem
                                val shareText = context.getString(R.string.share_text, song.name, song.artistName)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(
                                    Intent.createChooser(sendIntent, context.getString(R.string.share)),
                                )
                            },
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        // 歌单管理 / 已下载：从底部按钮行收进「更多」菜单，避免底部一行过挤
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.my_playlists)) },
                            leadingIcon = { MenuIcon(Icons.Filled.QueueMusic) },
                            onClick = {
                                showMore = false
                                onManagePlaylists()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.my_downloads)) },
                            leadingIcon = { MenuIcon(Icons.Filled.Download) },
                            onClick = {
                                showMore = false
                                onOpenDownloads()
                            },
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.timer_off)) },
                            leadingIcon = { MenuIcon(Icons.Filled.Timer) },
                            onClick = {
                                showMore = false
                                showTimerDialog = true
                            },
                        )
                    }
                }
            }

            // 圆盘（仅作视觉展示，点击无任何反应，避免误触切换播放状态）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                RotatingDisc(coverUrl = state.song?.coverUrl, isPlaying = state.isPlaying)
            }

            // 歌曲信息
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    state.song?.name ?: "",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    state.song?.artistName ?: "",
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 快捷操作行：喜欢 / 评论 / 下载 / 歌单 / 更多
            // 「喜欢」原先单独占歌曲信息下方一整行，现并入本行并排第一位，省下一行高度。
            // 本行整体位于进度条之上，进度条与播放控制按钮顺势下移、更靠近屏幕底部（单手拇指区）。
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BottomActionItem(
                    label = if (state.isFavorite) "已喜欢" else "喜欢",
                    icon = if (state.isFavorite) "❤️" else "🤍",
                    labelColor = if (state.isFavorite) Accent else Color.White.copy(alpha = 0.7f),
                    onClick = { viewModel.toggleFavorite() },
                )
                BottomActionItem(label = "评论", onClick = { state.song?.let(onOpenComment) })
                BottomActionItem(label = "下载", onClick = {
                    val song = state.song ?: return@BottomActionItem
                    when {
                        DownloadManager.isDownloading(song.id) -> {
                            Toast.makeText(context, R.string.downloading, Toast.LENGTH_SHORT).show()
                        }
                        DownloadManager.isDownloaded(context, song.id) -> {
                            Toast.makeText(context, R.string.downloaded, Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val url = try {
                                    repository.getSongUrl(song)
                                } catch (e: Exception) {
                                    null
                                }
                                if (url.isNullOrBlank()) {
                                    Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
                                } else {
                                    val ok = DownloadManager.download(context, song, url)
                                    Toast.makeText(
                                        context,
                                        if (ok) R.string.download_finished else R.string.download_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    }
                })
                BottomActionItem(label = "歌单", onClick = {
                    if (state.song != null) showAddToPlaylist = true
                })
                BottomActionItem(label = "更多", onClick = { showMore = true })
            }

            // 进度与控制（位于快捷操作行之下）
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 14.dp, bottom = 22.dp),
            ) {
                val duration = state.durationMs.coerceAtLeast(1L)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDuration(state.positionMs),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    // 自绘细进度条：Material3 Slider 的轨道默认太粗，这里用 3dp 细线 + 小圆点
                    ThinProgressBar(
                        positionMs = state.positionMs,
                        durationMs = duration,
                        onSeek = { viewModel.seekTo(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text(formatDuration(duration), fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                }

                // 进度条与下方播放控制按钮保持足够呼吸感（避免贴在一起、误触）
                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> stringResource(R.string.play_mode_single)
                                Player.REPEAT_MODE_ALL -> stringResource(R.string.play_mode_list)
                                else -> stringResource(R.string.play_mode_order)
                            },
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                                Color.White.copy(alpha = 0.5f)
                            } else {
                                GradientStart
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.prev() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.prev), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(Color.White)
                            .clickable { viewModel.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) stringResource(R.string.pause) else stringResource(R.string.player),
                            tint = Dark,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.next() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = stringResource(R.string.playlist), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    }
                }
            }

        }

        // 错误提示（如「该歌曲暂不支持播放」）
        // 布局调整后底部已被「操作行 + 进度条 + 播放按钮」占满，
        // 提示改到顶部标题栏下方，避免压住进度条。
        state.error?.let { err ->
            Text(
                err,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 108.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // 播放列表弹窗
        if (showQueue) {
            QueuePanel(
                queue = state.queue,
                currentSongId = state.song?.id,
                onPlayAt = { index ->
                    viewModel.playAt(index)
                    showQueue = false
                },
                onClose = { showQueue = false },
            )
        }

        // 「添加到歌单」底部弹窗
        // 外层补一个半透明遮罩 + 底部居中容器：
        // 之前面板直接挂在播放页根 Box 下，而根 Box 默认 TopStart 对齐，
        // 结果这个「底部弹窗」跑到了屏幕顶部、还盖住了状态栏（用户反馈的「半屏显示不好」）。
        state.song?.let { currentSong ->
            if (showAddToPlaylist) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showAddToPlaylist = false },
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AddToPlaylistSheet(
                        repository = repository,
                        song = currentSong,
                        onDismiss = { showAddToPlaylist = false },
                        onManagePlaylists = {
                            showAddToPlaylist = false
                            onManagePlaylists()
                        },
                    )
                }
            }
        }

        // 定时关闭对话框
        if (showTimerDialog) {            AlertDialog(
                onDismissRequest = { showTimerDialog = false },
                title = { Text(stringResource(R.string.timer_off)) },
                text = {
                    Column {
                        listOf(15L, 30L, 60L).forEach { minutes ->
                            Text(
                                stringResource(R.string.timer_minutes, minutes),
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        timerJob?.cancel()
                                        timerJob = scope.launch {
                                            delay(minutes * 60_000L)
                                            playerController.stop()
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.timer_set, minutes),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        showTimerDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.timer_cancel),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    timerJob?.cancel()
                                    timerJob = null
                                    showTimerDialog = false
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTimerDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                },
                containerColor = Color(0xFF1B1B1F),
                titleContentColor = Color.White,
                textContentColor = Color.White,
            )
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<Song>,
    currentSongId: Long?,
    onPlayAt: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1B1B1F))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(top = 8.dp, bottom = 28.dp),
        ) {
            // 拖拽指示条
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${stringResource(R.string.playlist)}（${queue.size}）",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
            }
            if (queue.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty_queue), color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(queue, key = { index, song -> "$index-${song.id}" }) { index, song ->
                        val isCurrent = song.id == currentSongId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayAt(index) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 12.sp,
                                color = if (isCurrent) GradientStart else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.width(28.dp),
                            )
                            SongCover(url = song.coverUrl, size = 40, corner = 8)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    song.artistName,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    Icons.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.currently_playing),
                                    tint = GradientStart,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Text(
                                    formatDuration(song.durationMs),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RotatingDisc(coverUrl: String?, isPlaying: Boolean) {
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotation = (rotation + 0.3f) % 360f
                delay(16)
            }
        }
    }
    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(280.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
        Box(
            modifier = Modifier.size(264.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(264.dp).clip(CircleShape).rotate(rotation),
            ) {
                // 始终先画默认封面（主题渐变 + 音符），仅在真实封面加载成功后覆盖。
                // URL 缺失 / 加载失败 / 加载中都不会出现「空白圆」。
                CoverWithPlaceholder(
                    url = coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    circle = true,
                )
            }
        }
    }
}

@Composable
private fun LyricView(state: PlayerUiState, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val current = state.currentLyricIndex
    // 区分「程序自动滚动」与「用户手动拖拽」：
    // isScrollInProgress 对两者都会置位。这里不去维护一个「正在程序滚动」的布尔标志
    // （协程被取消时标志来不及复位，会把自己的滚动误判成用户操作、白冻 2 秒跟随），
    // 改成记录「上次程序滚动发生在什么时候」，只有距它足够久才认定是用户拖拽。
    // 用 AtomicLong 而非 MutableState：避免被 snapshotFlow 订阅、反过来打断 2 秒恢复计时。
    val lastAutoScrollAt = remember { AtomicLong(0L) }
    var userScrolling by remember { mutableStateOf(false) }
    var lastSongId by remember { mutableStateOf(state.song?.id) }

    // 用户手动滚动时暂停自动跟随；停止滚动 2 秒后自动恢复
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    if (System.currentTimeMillis() - lastAutoScrollAt.get() > 300) {
                        userScrolling = true
                    }
                } else if (userScrolling) {
                    delay(2000)
                    userScrolling = false
                }
            }
    }

    // 当前行变化时把该行滚动到视口正中。
    //
    // 历史 Bug「歌词不会动 / 整块歌词沉在下半屏 / 停在上一首的位置」由三点叠加造成：
    //   1) 触发条件失效（最关键）。state 是 collectAsState() 出来的**值**，
    //      `val current = state.currentLyricIndex` 只是当前这一帧的快照。
    //      旧实现把它放进 LaunchedEffect(listState) { snapshotFlow { current to ... } }：
    //      LaunchedEffect 的 key 是 listState（恒定），协程只在首次组合时启动一次，
    //      snapshotFlow 捕获的 current 永远是**打开歌词页那一瞬间的旧值** →
    //      之后唱到哪一句都不会再发射，列表就此定格。
    //      （只有 userScrolling 是 MutableState，写它才能让 snapshotFlow 重新求值。）
    //      改法：用 LaunchedEffect 的 key 承载 current / 歌 / 歌词条数，值一变就重启协程，
    //      天然等价于 collectLatest。
    //   2) 坐标口径混用（本次「歌词都在下方」的根因，详见下方【坐标口径】注释）。
    //      LazyListItemInfo.offset 是内容坐标，视口顶端在那里是 viewportStartOffset（负值），
    //      旧实现却拿 viewportSize/2 当中心基准，算出的 delta 恒为负 = 向列表头部回滚；
    //      而打开歌词页时列表正好在顶部（canScrollBackward=false），scrollBy 消耗 0 → 纹丝不动。
    //      改法：中心基准改用 viewportStartOffset + viewportSize/2，量出差值用 scrollBy 相对纠正。
    //   3) 切歌后 listState 不重置：新歌词沿用上一首的滚动位置，停在半空且没有高亮。
    //      旧实现把 song.id / lyrics.size 当 key：若新旧两首行数相同、或电台内切曲目时
    //      song.id 是电台 id 不变，两个键都不触发 → effect 不重跑 → 新歌词停在上首滚动位置沉到下方。
    //      改法：改用 `state.song` + `state.lyrics` 的**对象引用**作 key（切歌/重载歌词必换新对象，必触发）。
    LaunchedEffect(current, userScrolling, state.song, state.lyrics) {
        if (state.lyrics.isEmpty()) return@LaunchedEffect
        // 切歌时解除任何残留的「手动滚动冻结」，避免新歌首行被旧状态卡住
        val songId = state.song?.id
        if (songId != lastSongId) {
            lastSongId = songId
            userScrolling = false
        }
        // 用户正在手动拖动时不打扰，交给「回到当前行」按钮处理
        if (userScrolling) return@LaunchedEffect
        // 还没唱到第一句（current == -1，常见于前奏）时把第一句摆到正中，
        // 否则列表停在「内容起点」这个未滚动状态、首句会偏下半个行高。
        val target = current.coerceAtLeast(0)

        // 【坐标口径】这是本段最容易踩错、也是「歌词沉在下方」的真正根因：
        // LazyListItemInfo.offset 是**内容坐标**，contentPadding 已被平移掉 ——
        // 实测 contentPadding = 半屏时 viewportStartOffset = -1309（负值！），
        // 视口顶端在内容坐标里是 -1309，而不是 0。
        //   · 行的视觉 y       = offset + size/2 - viewportStartOffset
        //   · 视口中心的内容坐标 = viewportStartOffset + viewportSize/2
        // 旧实现直接拿 viewportSize/2 当中心基准去减 offset（两种坐标混用），
        // 算出的是一个**恒为负**的 delta = 向列表头部回滚；
        // 而打开歌词页时列表正处于顶部（canScrollBackward=false），scrollBy 消耗 0，
        // 于是歌词永远停在「未滚动」位置：唱到第 3 行时它已落到屏幕约 66% 处，
        // 越唱越往下，用户看到的就是「歌词都跑到下面去了」。
        fun centerRef(): Int = listState.layoutInfo.viewportStartOffset +
            listState.layoutInfo.viewportSize.height / 2

        fun centerOf(index: Int): Int? = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.let { it.offset + it.size / 2 }

        // 目标行还没被组合进视口时先粗定位到「内容坐标 0」：
        // 上下 padding 各占半屏，内容坐标 0 恰好就是视口正中，粗定位即居中。
        // 切歌后 listState 可能停在上首的滚动位置，多试几次确保目标行真正进入视口。
        repeat(3) { attempt ->
            if (centerOf(target) != null) return@repeat
            lastAutoScrollAt.set(System.currentTimeMillis())
            listState.scrollToItem(target, 0)
            withFrameNanos { }
            if (attempt == 2) return@LaunchedEffect
        }
        if (centerOf(target) == null) return@LaunchedEffect

        // 相对纠正：目标行中心离视口中心差多少就滚多少。
        // 最多 6 轮（当前行加粗放大导致行高变化也能自动修回来）。
        repeat(6) {
            val c = centerOf(target) ?: return@repeat
            val delta = c - centerRef()
            if (kotlin.math.abs(delta) <= 2) return@repeat
            lastAutoScrollAt.set(System.currentTimeMillis())
            listState.scrollBy(delta.toFloat())
            // 等一帧让 LazyColumn 重新测量，下一轮才能读到纠正后的真实位置
            withFrameNanos { }
        }
    }

    // navigationBarsPadding：整块歌词连同「半屏留白」的基准高度一起抬到系统导航栏之上，
    // 否则滚到末尾时最后一行会被导航栏压住（背景仍由 background 铺满全屏）。
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF22152E)).navigationBarsPadding()) {
        // 上下各留半屏 contentPadding，使当前行位于视口正中。
        //
        // 关键：歌词列表必须**铺满整屏**、顶栏改成浮层叠在它上面。
        // 之前顶栏是 Column 里的普通一行，列表视口被顶栏压掉约 340px，
        // 而上下留白仍按「整屏高度的一半」算 → 第 0 项的中心落到屏幕 66% 处，
        // 整块歌词看着往下沉、上半屏空一大片（用户反馈的「歌词有问题」）。
        val halfScreen = maxHeight / 2

        if (state.lyrics.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_lyrics), color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = halfScreen),
            ) {
                itemsIndexed(state.lyrics) { index, line ->
                    val isCurrent = index == current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 9.dp),
                    ) {
                        Text(
                            line.content,
                            fontSize = if (isCurrent) 17.sp else 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center,
                        )
                        if (line.translation.isNotBlank()) {
                            Text(
                                line.translation,
                                fontSize = if (isCurrent) 12.sp else 11.sp,
                                color = if (isCurrent) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.22f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        // 顶栏（浮层）：底部用「同色 → 透明」的渐变收口，
        // 免得歌词行滚上来时贴着标题被硬切。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF22152E),
                        0.62f to Color(0xFF22152E),
                        1f to Color.Transparent,
                    ),
                )
                .padding(top = topBarTopPadding(), start = 8.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Text(
                state.song?.name ?: "",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
        // 用户拖动期间显示“回到当前行”按钮
        if (userScrolling && state.lyrics.isNotEmpty()) {
            Text(
                stringResource(R.string.back_to_current),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        // 放开跟随开关，snapshotFlow 会立即把当前行重新居中
                        userScrolling = false
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * 细进度条（问题 3：Material3 Slider 轨道太粗）。
 *
 * 设计：视觉轨道只有 3dp，滑块 8dp，但保留 24dp 的可点击热区，
 * 既保证观感纤细又不牺牲易点性；支持点击跳转与拖动擦洗。
 */
@Composable
private fun ThinProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var widthPx by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }

    fun seekToX(x: Float) {
        if (widthPx <= 0) return
        val ratio = (x / widthPx).coerceIn(0f, 1f)
        onSeek((ratio * durationMs).toLong())
    }

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMs) {
                detectTapGestures { offset -> seekToX(offset.x) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        seekToX(offset.x)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ -> seekToX(change.position.x) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val trackH = 3.dp.toPx()
            val startX = if (dragging) 4.dp.toPx() else 4.dp.toPx()
            val endX = size.width - startX
            val usable = (endX - startX).coerceAtLeast(0f)
            // 底轨
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = androidx.compose.ui.geometry.Offset(startX, cy),
                end = androidx.compose.ui.geometry.Offset(endX, cy),
                strokeWidth = trackH,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            // 已播放进度
            drawLine(
                brush = Brush.horizontalGradient(listOf(GradientStart, GradientEnd)),
                start = androidx.compose.ui.geometry.Offset(startX, cy),
                end = androidx.compose.ui.geometry.Offset(startX + usable * fraction, cy),
                strokeWidth = trackH,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            // 滑块：仅拖动时放大，平时只显示小圆点
            val thumbR = if (dragging) 5.5.dp.toPx() else 3.5.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = thumbR,
                center = androidx.compose.ui.geometry.Offset(startX + usable * fraction, cy),
            )
        }
    }
}

/** 「更多」菜单项图标：统一 18dp 尺寸，颜色跟随菜单内容色 */
@Composable
private fun MenuIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
}

/**
 * 底部快捷操作项（图标 + 文字上下排列）。
 *
 * @param icon       上方小图标；默认是中性圆点，可传 emoji（如喜欢项的 🤍/❤️）
 * @param labelColor 文字颜色；喜欢项在「已喜欢」时用主题强调色
 */
@Composable
private fun BottomActionItem(
    label: String,
    onClick: () -> Unit,
    icon: String = "•",
    labelColor: Color = Color.White.copy(alpha = 0.7f),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(icon, fontSize = 16.sp, color = labelColor)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.5.sp, color = labelColor)
    }
}