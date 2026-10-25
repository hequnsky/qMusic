package com.qmusic.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.SodaPlaylist
import com.qmusic.ui.components.CoverWithPlaceholder
import com.qmusic.ui.components.topBarTopPadding
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors

/** 汽水歌单广场：3 列网格 + 触底自动加载，能把服务端攒下来的几百个歌单翻完 */
private const val PAGE_SIZE = 30

@Composable
fun SodaPlaylistSquareScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    BackHandler { onBack() }
    val colors = LocalQMusicColors.current
    val gridState = rememberLazyGridState()

    var playlists by remember { mutableStateOf<List<SodaPlaylist>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    suspend fun loadNext() {
        if (loading || endReached) return
        loading = true
        val page = runCatching {
            repository.getSodaPlaylistPage(limit = PAGE_SIZE, offset = playlists.size)
        }.getOrNull()
        loading = false
        if (page == null) {
            failed = playlists.isEmpty()
            return
        }
        failed = false
        total = page.total
        if (page.list.isEmpty()) {
            endReached = true
            return
        }
        val known = playlists.mapTo(HashSet()) { it.id }
        playlists = playlists + page.list.filter { known.add(it.id) }
        // 池子可能因服务端增量而变长，这里只按当前 known total 判尾
        if (playlists.size >= page.total) endReached = true
    }

    LaunchedEffect(Unit) { loadNext() }

    // 触底预加载：离末尾还有 6 个就开始拉下一页
    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    LaunchedEffect(lastVisible, playlists.size, endReached) {
        if (!endReached && playlists.isNotEmpty() && lastVisible >= playlists.size - 6) {
            loadNext()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topBarTopPadding(), start = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("汽水歌单", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                    Spacer(Modifier.size(7.dp))
                    Text(
                        "汽水",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFE2C55), Color(0xFFFF6B8A))))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                Text(
                    if (total > 0) "共 $total 个 · 越往后越冷门" else "正在整理歌单…",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }

        when {
            playlists.isEmpty() && loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientStart, modifier = Modifier.size(22.dp))
                }
            }
            playlists.isEmpty() && failed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("歌单没取到，返回重进试试", fontSize = 12.sp, color = colors.textSecondary)
                }
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        SodaSquareCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.longId) })
                    }
                    if (!endReached) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        color = GradientStart,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Text("上滑加载更多", fontSize = 11.sp, color = colors.textTertiary)
                                }
                            }
                        }
                    } else if (playlists.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("已经到底啦 · 共 ${playlists.size} 个", fontSize = 11.sp, color = colors.textTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 网格卡：方形封面 + 曲目数角标 + 两行标题 */
@Composable
private fun SodaSquareCard(playlist: SodaPlaylist, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            CoverWithPlaceholder(
                url = playlist.coverUrl,
                modifier = Modifier.fillMaxSize(),
                circle = false,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
                    ),
            )
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    "${playlist.trackCount} 首",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.94f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            playlist.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
