package com.qmusic.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.Song
import com.qmusic.ui.components.PlaylistCard
import com.qmusic.ui.components.SectionTitle
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors

@Composable
fun DiscoverScreen(
    repository: MusicRepository,
    onPlayAll: (List<Song>, Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMine: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val viewModel: DiscoverViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DiscoverViewModel(repository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val colors = LocalQMusicColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item { TopBar(onOpenSearch = onOpenSearch, onOpenMine = onOpenMine) }

        item {
            HeroCard(
                playlist = state.hero,
                loading = state.loading,
                onPlay = { state.songs.firstOrNull()?.let { onPlayAll(state.songs, 0) } },
                onRefresh = { viewModel.refresh() },
            )
        }

        item { ChipsRow() }

        item { SectionTitle(title = stringResource(R.string.hot_playlist)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                }
            }
        }

        item { SectionTitle(title = stringResource(R.string.for_you)) }

        val error = state.error

        when {
            state.loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GradientStart)
                    }
                }
            }
            error != null -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error, color = colors.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.retry),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                                .clickable { viewModel.refresh() }
                                .padding(horizontal = 22.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            else -> {
                items(state.songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = {
                            val index = state.songs.indexOfFirst { it.id == song.id }
                            if (index >= 0) onPlayAll(state.songs, index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onOpenSearch: () -> Unit, onOpenMine: () -> Unit) {
    val colors = LocalQMusicColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center,
            ) {
                Text("♪", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.app_name), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Filled.Search, contentDescription = "搜索", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onOpenMine) {
            Icon(Icons.Filled.Person, contentDescription = "我的", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HeroCard(
    playlist: Playlist?,
    loading: Boolean,
    onPlay: () -> Unit,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B2D42), Color(0xFF4A3A78), Color(0xFFB14A8A)))),
    ) {
        // 右上光斑
        Box(
            Modifier.size(180.dp).align(Alignment.TopEnd).offset(x = 40.dp, y = (-60).dp)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (playlist != null && !playlist.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(18.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (loading) "…" else "♪", color = Color.White, fontSize = 40.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.daily_recommend),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD9C9),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    playlist?.name ?: "加载中…",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playlist != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "▶ ${com.qmusic.ui.components.formatPlayCount(playlist.playCount)} ${stringResource(R.string.times_played)}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = GradientStart,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            "🔄 ${stringResource(R.string.change)}",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onRefresh)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChipsRow() {
    val colors = LocalQMusicColors.current
    val chips = listOf("每日推荐", "热歌榜", "新歌速递", "华语", "欧美", "纯音乐", "摇滚", "民谣")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        chips.forEachIndexed { index, chip ->
            val selected = index == 0
            Text(
                chip,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) Color.White else colors.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Color(0xFF15161A) else colors.card)
                    .clickable {}
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}