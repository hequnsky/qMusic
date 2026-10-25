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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.ServerConfig
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.Song
import com.qmusic.data.model.Toplist
import com.qmusic.ui.category.CategorySpec
import com.qmusic.ui.category.categorySpecOf
import com.qmusic.ui.components.CoverWithPlaceholder
import com.qmusic.ui.components.PlaylistCard
import com.qmusic.ui.components.SectionTitle
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors
import com.qmusic.ui.toplist.musicToplists
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun DiscoverScreen(
    repository: MusicRepository,
    onPlayAll: (List<Song>, Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMine: () -> Unit,
    onOpenVideo: () -> Unit = {},
    onOpenPlaylist: (Long) -> Unit,
    onOpenMorePlaylists: () -> Unit,
    onOpenCategory: (CategorySpec) -> Unit = {},
    onOpenToplists: () -> Unit = {},
) {
    val viewModel: DiscoverViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DiscoverViewModel(repository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val colors = LocalQMusicColors.current
    var toplists by remember { mutableStateOf<List<Toplist>?>(null) }
    // 汽水音乐：官方榜单 + 热歌榜推荐（两者独立降级，任一失败不影响首页其它区块）
    var sodaCharts by remember { mutableStateOf<List<Toplist>>(emptyList()) }
    var sodaHot by remember { mutableStateOf<List<Song>>(emptyList()) }
    // null = 还没加载完；emptyList = 加载完但没数据（含失败）。
    // 地址被启动自愈切换后要重拉一次，否则这块会一直停在上一次的结果上。
    val urlVersion by ServerConfig.baseUrlVersion.collectAsState()
    LaunchedEffect(urlVersion) {
        toplists = null
        sodaCharts = emptyList()
        sodaHot = emptyList()
        // 三个来源并发拉，谁先好谁先上，避免一个慢接口拖住整页
        coroutineScope {
            val sodaDeferred = async { repository.getSodaToplists().map { it.toToplist() } }
            val hotDeferred = async { repository.getSodaHotSongs(12) }
            val ncmDeferred = async {
                try {
                    repository.getToplists()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            sodaCharts = sodaDeferred.await()
            sodaHot = hotDeferred.await()
            toplists = ncmDeferred.await()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item { TopBar(onOpenSearch = onOpenSearch, onOpenMine = onOpenMine, onOpenVideo = onOpenVideo) }

        item {
            HeroCard(
                playlist = state.hero,
                loading = state.loading,
                onPlay = { state.songs.firstOrNull()?.let { onPlayAll(state.songs, 0) } },
                onRefresh = { viewModel.refresh() },
            )
        }

        item { ChipsRow(onCategoryClick = { name -> onOpenCategory(categorySpecOf(name)) }) }

        item { SectionTitle(title = stringResource(R.string.toplist), onMore = onOpenToplists) }
        // 汽水榜单排在网易云榜单前面：它是「抖音热歌」的原始出处，用户最想先看到
        val shownToplists = (sodaCharts + toplists.orEmpty().musicToplists()).take(10)
        if (toplists == null && sodaCharts.isEmpty()) {
            // 真的还在加载
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientStart, modifier = Modifier.size(22.dp))
                }
            }
        } else if (shownToplists.isEmpty()) {
            // 加载完成但没有数据：不要继续转圈假装还在加载，否则用户永远等下去
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "榜单暂时取不到，稍后下拉重试",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                }
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(shownToplists, key = { index, item -> "$index-${item.id}" }) { index, toplist ->
                        ToplistCard(toplist = toplist, onClick = { onOpenPlaylist(toplist.id) })
                    }
                }
            }
        }

        // 汽水热歌推荐：整块没数据就整体隐藏，不留只有标题的空区块
        if (sodaHot.isNotEmpty()) {
            item { SectionTitle(title = "汽水音乐 · 抖音热歌") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(sodaHot, key = { index, song -> "soda-$index-${song.id}" }) { index, song ->
                        SodaSongCard(
                            song = song,
                            rank = index + 1,
                            onClick = { onPlayAll(sodaHot, index) },
                        )
                    }
                }
            }
        }

        item { SectionTitle(title = stringResource(R.string.hot_playlist), onMore = onOpenMorePlaylists) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.playlists, key = { index, item -> "$index-${item.id}" }) { index, playlist ->
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
                itemsIndexed(state.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = {
                            val i = state.songs.indexOfFirst { it.id == song.id }
                            if (i >= 0) onPlayAll(state.songs, i)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onOpenSearch: () -> Unit, onOpenMine: () -> Unit, onOpenVideo: () -> Unit) {
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
        IconButton(onClick = onOpenVideo) {
            Icon(Icons.Filled.PlayCircle, contentDescription = "视频", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        }
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
            CoverWithPlaceholder(
                url = playlist?.coverUrl,
                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(18.dp)),
                circle = false,
            )
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
private fun ChipsRow(onCategoryClick: (String) -> Unit) {
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
                    .clickable { onCategoryClick(chip) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** 排行榜卡片 */
@Composable
private fun ToplistCard(toplist: Toplist, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Box {
            CoverWithPlaceholder(
                url = toplist.coverImgUrl,
                modifier = Modifier
                    .size(width = 150.dp, height = 150.dp)
                    .clip(RoundedCornerShape(14.dp)),
                circle = false,
            )
            // 汽水榜单单独打标，避免和 60 多个网易云榜单混在一起分不出来
            if (toplist.source == Song.SOURCE_SODA) {
                Text(
                    "汽水",
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFE2C55))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Text(
                toplist.updateFrequency,
                fontSize = 9.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            toplist.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 汽水热歌卡片（首页推荐区）。
 * 名次直接角标在图左上，前三名用汽水品牌红强调。
 */
@Composable
private fun SodaSongCard(song: Song, rank: Int, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier.width(116.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Box {
            CoverWithPlaceholder(
                url = song.coverUrl,
                modifier = Modifier.size(116.dp).clip(RoundedCornerShape(14.dp)),
                circle = false,
            )
            Text(
                "$rank",
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (rank <= 3) Color(0xFFFE2C55) else Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            song.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artistName.ifBlank { "未知歌手" },
            fontSize = 10.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}