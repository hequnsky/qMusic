package com.qmusic.ui.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.qmusic.data.MusicRepository
import com.qmusic.player.PlayerController
import com.qmusic.player.PlayerUiState
import com.qmusic.ui.components.SongCover
import com.qmusic.ui.components.formatDuration
import com.qmusic.ui.theme.Accent
import com.qmusic.ui.theme.Dark
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    playerController: PlayerController,
    repository: MusicRepository,
    onClose: () -> Unit,
) {
    val viewModel: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlayerViewModel(playerController, repository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    if (showLyrics) {
        LyricView(state = state, onBack = { showLyrics = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Dark)) {
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
                    Text("正在播放", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "汽水音乐 · 每日推荐",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = { showLyrics = true }) {
                    Icon(Icons.Filled.Notes, contentDescription = "歌词", tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Color.White)
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
                    Text("💬 评论", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }

            // 进度与控制
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 18.dp, bottom = 8.dp),
            ) {
                val duration = state.durationMs.coerceAtLeast(1L)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(state.positionMs), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Slider(
                        value = state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = GradientStart,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                    )
                    Text(formatDuration(duration), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Repeat, contentDescription = "播放模式", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewModel.prev() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(Color.White)
                            .clickable { viewModel.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = Dark,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.next() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "播放列表", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    }
                }
            }

            // 底部操作
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("评论", "下载", "分享", "更多").forEach { label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("•", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.height(2.dp))
                        Text(label, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
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
    val current = state.currentLyricIndex

    LaunchedEffect(current) {
        if (current >= 0) {
            listState.animateScrollToItem(current.coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF22152E))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "返回", tint = Color.White)
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
                    Text("暂无歌词", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    itemsIndexed(state.lyrics) { index, line ->
                        Text(
                            line.content,
                            fontSize = if (index == current) 17.sp else 15.sp,
                            fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == current) Color.White else Color.White.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}