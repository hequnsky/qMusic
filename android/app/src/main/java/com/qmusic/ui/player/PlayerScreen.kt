package com.qmusic.ui.player

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Song
import com.qmusic.data.local.DownloadManager
import com.qmusic.player.PlayerController
import com.qmusic.player.PlayerUiState
import com.qmusic.ui.components.SongCover
import com.qmusic.ui.components.formatDuration
import com.qmusic.ui.theme.Accent
import com.qmusic.ui.theme.Dark
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    playerController: PlayerController,
    repository: MusicRepository,
    onClose: () -> Unit,
    onOpenComment: (Song) -> Unit = {},
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
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 系统返回键：优先关闭播放页内子页面，再关闭整个播放页（而不是直接退出到桌面）
    BackHandler {
        when {
            showLyrics -> showLyrics = false
            showQueue -> showQueue = false
            showMore -> showMore = false
            else -> onClose()
        }
    }

    if (showLyrics) {
        LyricView(state = state, onBack = { showLyrics = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Dark).navigationBarsPadding()) {
        // 背景渐变
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF3A1D4F), Dark, Dark)),
            ),
        )

        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起", tint = Color.White)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.currently_playing), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "汽水音乐 · 每日推荐",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.timer_off)) },
                            onClick = {
                                showMore = false
                                showTimerDialog = true
                            },
                        )
                    }
                }
            }

            // 圆盘
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.isFavorite) "❤️ 已喜欢" else "🤍 喜欢",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.isFavorite) Accent else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { viewModel.toggleFavorite() },
                    )
                    Spacer(Modifier.width(28.dp))
                    Text(
                        "💬 评论",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { state.song?.let(onOpenComment) },
                    )
                }
            }

            // 进度与控制
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 10.dp, bottom = 4.dp),
            ) {
                val duration = state.durationMs.coerceAtLeast(1L)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(state.positionMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Slider(
                        value = state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.weight(1f).height(26.dp).padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = GradientStart,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                    )
                    Text(formatDuration(duration), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
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

            // 底部操作：评论（真实）/ 下载（暂未实现）/ 分享（系统分享）/ 更多（弹出菜单）
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
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
                                    repository.getSongUrl(song.id)
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
                BottomActionItem(
                    label = "分享",
                    onClick = {
                        val song = state.song ?: return@BottomActionItem
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
                BottomActionItem(label = "更多", onClick = { showMore = true })
            }
        }

        // 错误提示
        state.error?.let { err ->
            Text(
                err,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
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

        // 定时关闭对话框
        if (showTimerDialog) {
            AlertDialog(
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
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("♪", fontSize = 96.sp, color = Color.White)
                    }
                }
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF0D0D0F)),
            )
        }
    }
}

@Composable
private fun LyricView(state: PlayerUiState, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val current = state.currentLyricIndex
    var userScrolling by remember { mutableStateOf(false) }

    // 用户手动滚动时暂停自动跟随；停止滚动 2 秒后自动恢复
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    userScrolling = true
                } else {
                    delay(2000)
                    userScrolling = false
                }
            }
    }

    // 当前行变化时自动滚动居中（用户拖动期间不打扰）
    LaunchedEffect(listState) {
        snapshotFlow { Triple(current, userScrolling, state.lyrics.size) }
            .collectLatest { (cur, scrolling, lyricCount) ->
                if (cur >= 0 && !scrolling && lyricCount > 0) {
                    listState.animateScrollToItem(cur.coerceAtLeast(0))
                }
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF22152E))) {
        // 上下各留半屏 contentPadding，使当前行位于视口正中
        val halfScreen = maxHeight / 2
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 8.dp, end = 8.dp),
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
            if (state.lyrics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_lyrics), color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(bottom = 60.dp),
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
        }
        // 用户拖动期间显示“回到当前行”按钮
        if (userScrolling && state.lyrics.isNotEmpty()) {
            Text(
                stringResource(R.string.back_to_current),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        userScrolling = false
                        if (current >= 0) {
                            coroutineScope.launch { listState.animateScrollToItem(current) }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun BottomActionItem(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text("•", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.7f))
    }
}