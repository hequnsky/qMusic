package com.qmusic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.PlaylistDetail
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SongCover
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.components.formatPlayCount
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors

/**
 * 歌单详情页：展示歌单封面、歌曲列表，支持整单播放与下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    repository: MusicRepository,
    onBack: () -> Unit,
    onPlayAll: (List<Song>, Int) -> Unit,
) {
    val viewModel: PlaylistDetailViewModel = viewModel(
        key = "playlist_$playlistId",
        factory = viewModelFactory {
            initializer { PlaylistDetailViewModel(playlistId, repository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val colors = LocalQMusicColors.current

    Box(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        when {
            state.detail == null && state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientStart)
                }
            }

            state.detail == null && state.error != null -> {
                val error = state.error ?: ""
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.retry),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                            .clickable { viewModel.refresh() }
                            .padding(horizontal = 24.dp, vertical = 9.dp),
                    )
                }
            }

            state.detail != null -> {
                val detail = state.detail!!
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item { PlaylistHeader(detail = detail) }
                        item {
                            PlayAllButton(
                                onClick = { onPlayAll(detail.tracks, 0) },
                                enabled = detail.tracks.isNotEmpty(),
                            )
                        }
                        item {
                            Text(
                                "${stringResource(R.string.song_list)} · 共 ${detail.tracks.size} ${stringResource(R.string.songs)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        itemsIndexed(detail.tracks, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song,
                                onClick = { onPlayAll(detail.tracks, index) },
                            )
                        }
                    }
                }
            }
        }

        // 返回按钮固定在顶部
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 28.dp, start = 4.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "返回", tint = Color.White)
        }
    }
}

@Composable
private fun PlaylistHeader(detail: PlaylistDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF372A55), Color(0xFF6A4A8A))),
            )
            .padding(start = 16.dp, end = 16.dp, top = 84.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SongCover(url = detail.coverUrl, size = 168, corner = 18)
        Spacer(Modifier.height(16.dp))
        Text(
            detail.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "▶ ${formatPlayCount(detail.playCount)} ${stringResource(R.string.times_played)} · 共 ${detail.trackCount} ${stringResource(R.string.songs)}",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PlayAllButton(onClick: () -> Unit, enabled: Boolean) {
    val backgroundModifier = if (enabled) {
        Modifier.background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
    } else {
        Modifier.background(Color(0xFFE3E3E8))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(backgroundModifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = if (enabled) Color.White else Color(0xFF9A9AA2),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.play_all),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color(0xFF9A9AA2),
        )
    }
}