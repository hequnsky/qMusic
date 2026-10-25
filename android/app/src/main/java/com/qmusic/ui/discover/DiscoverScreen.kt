package com.qmusic.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.qmusic.data.model.SodaPlaylist
import com.qmusic.data.model.SodaRadio
import com.qmusic.data.model.Song
import com.qmusic.data.model.Toplist
import com.qmusic.ui.category.CategorySpec
import com.qmusic.ui.category.categorySpecOf
import com.qmusic.ui.components.CoverWithPlaceholder
import com.qmusic.ui.components.PlaylistCard
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.components.formatPlayCount
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors
import com.qmusic.ui.toplist.musicToplists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 汽水品牌红（抖音系），只用于「汽水」角标，不参与主题换色 */
private val SodaRed = Color(0xFFFE2C55)
private val SodaRedSoft = Color(0xFFFF6B8A)

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
    val scope = rememberCoroutineScope()

    var toplists by remember { mutableStateOf<List<Toplist>?>(null) }
    // 汽水各区块独立降级：任一接口失败只影响自己那块，不会让整页空掉
    var sodaCharts by remember { mutableStateOf<List<Toplist>>(emptyList()) }
    var sodaHot by remember { mutableStateOf<List<Song>>(emptyList()) }
    var sodaPlaylists by remember { mutableStateOf<List<SodaPlaylist>>(emptyList()) }
    var sodaRadios by remember { mutableStateOf<List<SodaRadio>>(emptyList()) }
    // 歌单池在服务端，客户端只管翻 offset，实现「换一批」
    var playlistOffset by remember { mutableIntStateOf(0) }
    var radioBusyId by remember { mutableStateOf<String?>(null) }

    // null = 还没加载完；emptyList = 加载完但没数据（含失败）。
    // 地址被启动自愈切换后要重拉一次，否则这块会一直停在上一次的结果上。
    val urlVersion by ServerConfig.baseUrlVersion.collectAsState()
    LaunchedEffect(urlVersion) {
        toplists = null
        sodaCharts = emptyList()
        sodaHot = emptyList()
        sodaPlaylists = emptyList()
        sodaRadios = emptyList()
        playlistOffset = 0
        // 用独立 launch 而不是 async+await 串行收口：
        // 任一接口抛异常只影响自己那块，不会把后面还没 await 的区块一起毙掉。
        // 地址被启动自愈切换后要重拉一次，否则这块会一直停在上一次的结果上。
        coroutineScope {
            launch {
                sodaCharts = runCatching { repository.getSodaToplists().map { it.toToplist() } }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
            }
            launch {
                sodaHot = runCatching { repository.getSodaHotSongs(12) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
            }
            launch {
                sodaPlaylists = runCatching { repository.getSodaPlaylists(limit = 16, offset = 0) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
            }
            launch {
                sodaRadios = runCatching { repository.getSodaRadios() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
            }
            launch {
                toplists = runCatching { repository.getToplists() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
            }
        }
    }

    val swapPlaylists: () -> Unit = {
        scope.launch {
            val next = playlistOffset + 16
            var list = repository.getSodaPlaylists(limit = 16, offset = next)
            var nextOffset = next
            if (list.isEmpty()) {
                // 翻到池子末尾就回绕到开头，保证「换一批」永远有反馈
                list = repository.getSodaPlaylists(limit = 16, offset = 0)
                nextOffset = 0
            }
            if (list.isNotEmpty()) {
                sodaPlaylists = list
                playlistOffset = nextOffset
            }
        }
    }

    val playRadio: (SodaRadio) -> Unit = { radio ->
        val id = radio.id.toLongOrNull() ?: 0L
        if (radioBusyId == null && id > 0) {
            radioBusyId = radio.id
            scope.launch {
                val songs = repository.getSodaRadioSongs(id, 30)
                radioBusyId = null
                if (songs.isNotEmpty()) onPlayAll(songs, 0)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                // 顶部一层品牌色光晕，让首屏不那么「一片死黑」
                Brush.verticalGradient(
                    0f to GradientStart.copy(alpha = 0.16f),
                    0.28f to GradientStart.copy(alpha = 0.04f),
                    0.5f to Color.Transparent,
                ),
            ),
        contentPadding = PaddingValues(bottom = 20.dp),
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

        // ---------- 汽水精选歌单（双行横滑） ----------
        if (sodaPlaylists.isNotEmpty()) {
            item {
                SodaSectionHeader(
                    title = "汽水精选歌单",
                    subtitle = "${sodaPlaylists.size} 个 · 抖音热歌精选",
                    action = "换一批",
                    onAction = swapPlaylists,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        sodaPlaylists.chunked(2),
                        key = { index, pair -> "sp-$index-${pair.firstOrNull()?.id}" },
                    ) { _, pair ->
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            pair.forEach { playlist ->
                                SodaPlaylistCard(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.longId) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 榜单 ----------
        item { SectionHeader(title = stringResource(R.string.toplist), onMore = onOpenToplists) }
        // 汽水榜单排在网易云榜单前面：它是「抖音热歌」的原始出处，用户最想先看到
        val shownToplists = (sodaCharts + toplists.orEmpty().musicToplists()).take(12)
        if (toplists == null && sodaCharts.isEmpty()) {
            item { LoadingBlock() }
        } else if (shownToplists.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("榜单暂时取不到，稍后下拉重试", fontSize = 12.sp, color = colors.textSecondary)
                }
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(shownToplists, key = { index, item -> "tl-$index-${item.id}" }) { index, toplist ->
                        ToplistCard(toplist = toplist, rank = index + 1, onClick = { onOpenPlaylist(toplist.id) })
                    }
                }
            }
        }

        // ---------- 汽水电台（点即播） ----------
        if (sodaRadios.isNotEmpty()) {
            item {
                SodaSectionHeader(
                    title = "汽水电台",
                    subtitle = "点一下，直接开始放",
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(sodaRadios, key = { it.id }) { radio ->
                        RadioCard(
                            radio = radio,
                            busy = radioBusyId == radio.id,
                            onClick = { playRadio(radio) },
                        )
                    }
                }
            }
        }

        // ---------- 汽水热歌 ----------
        if (sodaHot.isNotEmpty()) {
            item {
                SodaSectionHeader(
                    title = "汽水音乐 · 抖音热歌",
                    subtitle = "热歌榜 Top ${sodaHot.size}",
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(sodaHot, key = { index, song -> "sh-$index-${song.id}" }) { index, song ->
                        SodaSongCard(song = song, rank = index + 1, onClick = { onPlayAll(sodaHot, index) })
                    }
                }
            }
        }

        item { SectionHeader(title = stringResource(R.string.hot_playlist), onMore = onOpenMorePlaylists) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.playlists, key = { index, item -> "pl-$index-${item.id}" }) { _, playlist ->
                    PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                }
            }
        }

        item { SectionHeader(title = stringResource(R.string.for_you)) }

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
                itemsIndexed(state.songs, key = { index, song -> "fy-$index-${song.id}" }) { index, song ->
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

// ---------------------------------------------------------------- 顶栏

@Composable
private fun TopBar(onOpenSearch: () -> Unit, onOpenMine: () -> Unit, onOpenVideo: () -> Unit) {
    val colors = LocalQMusicColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.app_name),
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(10.dp))
        // 搜索做成胶囊条，比光秃秃一个放大镜更有「能点」的感觉
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = "搜索", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "搜歌曲 / 歌手",
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenVideo, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.PlayCircle, contentDescription = "视频", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onOpenMine, modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center,
            ) {
                Text("我", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- 每日推荐大卡

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
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(196.dp)
            .shadow(18.dp, RoundedCornerShape(24.dp), spotColor = GradientStart.copy(alpha = 0.55f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B1B24), GradientStart.copy(alpha = 0.55f), GradientEnd.copy(alpha = 0.85f)),
                ),
            ),
    ) {
        // 两个光斑，制造一点空间感
        Box(
            Modifier
                .size(190.dp)
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-72).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Box(
            Modifier
                .size(120.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.18f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(106.dp)
                    .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.6f)),
            ) {
                CoverWithPlaceholder(
                    url = playlist?.coverUrl,
                    modifier = Modifier.size(106.dp).clip(RoundedCornerShape(18.dp)),
                    circle = false,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.daily_recommend),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "为你精选",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    playlist?.name ?: if (loading) "正在加载…" else "今日推荐",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp,
                )
                if (playlist != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "▶ ${formatPlayCount(playlist.playCount)} ${stringResource(R.string.times_played)}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            }
        }
        // 播放按钮 + 换一批：放在右下角，不再横在卡片中间挡图
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "换一批",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .shadow(10.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.45f))
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = GradientStart,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            "每日推荐 · 根据你的口味更新",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 22.dp),
        )
    }
}

// ---------------------------------------------------------------- 分类胶囊

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
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else colors.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (selected) {
                            Brush.linearGradient(listOf(GradientStart, GradientEnd))
                        } else {
                            Brush.linearGradient(listOf(colors.card, colors.card))
                        },
                    )
                    .clickable { onCategoryClick(chip) }
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 区块标题

@Composable
private fun SectionHeader(title: String, onMore: (() -> Unit)? = null) {
    val colors = LocalQMusicColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd))),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        Spacer(Modifier.weight(1f))
        if (onMore != null) {
            Text(
                stringResource(R.string.more),
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onMore)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** 带「汽水」品牌标签的区块标题 */
@Composable
private fun SodaSectionHeader(
    title: String,
    subtitle: String = "",
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalQMusicColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
            Spacer(Modifier.width(7.dp))
            Text(
                "汽水",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(SodaRed, SodaRedSoft)))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            if (action != null && onAction != null) {
                Text(
                    action,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 11.sp, color = colors.textTertiary)
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GradientStart, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- 汽水歌单卡

/** 横向 150dp 歌单卡：封面 + 底部渐隐标题 + 曲目数 */
@Composable
private fun SodaPlaylistCard(playlist: SodaPlaylist, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.5f)),
        ) {
            CoverWithPlaceholder(
                url = playlist.coverUrl,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                circle = false,
            )
            // 底部压暗，让叠在图上的曲目数看得清
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f)))),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    "${playlist.trackCount} 首",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                "汽水",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 7.dp, top = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(SodaRed, SodaRedSoft)))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            playlist.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            playlist.description.ifBlank { "${playlist.collectCount} 人收藏" },
            fontSize = 10.sp,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- 榜单卡

/** 排行榜卡片：封面即入口，名字压在图底部，带名次角标 */
@Composable
private fun ToplistCard(toplist: Toplist, rank: Int, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.45f)),
        ) {
            CoverWithPlaceholder(
                url = toplist.coverImgUrl,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                circle = false,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
            )
            if (toplist.source == Song.SOURCE_SODA) {
                Text(
                    "汽水",
                    fontSize = 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 7.dp, top = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(SodaRed, SodaRedSoft)))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            Text(
                toplist.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 9.dp, end = 9.dp, bottom = 8.dp),
            )
            Text(
                "TOP $rank",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 7.dp, top = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            toplist.updateFrequency.ifBlank { "每周更新" },
            fontSize = 10.sp,
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- 电台卡

@Composable
private fun RadioCard(radio: SodaRadio, busy: Boolean, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(10.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.45f)),
        ) {
            CoverWithPlaceholder(
                url = radio.coverUrl,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                circle = true,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SodaRed, SodaRedSoft))),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(12.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            radio.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- 热歌卡

/**
 * 汽水热歌卡片（首页推荐区）。
 * 名次直接角标在图左上，前三名用汽水品牌红强调。
 */
@Composable
private fun SodaSongCard(song: Song, rank: Int, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.45f)),
        ) {
            CoverWithPlaceholder(
                url = song.coverUrl,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                circle = false,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
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
                    .background(
                        if (rank <= 3) {
                            Brush.horizontalGradient(listOf(SodaRed, SodaRedSoft))
                        } else {
                            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.5f)))
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = SodaRed,
                    modifier = Modifier.size(14.dp),
                )
            }
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
