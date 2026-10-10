package com.qmusic.ui.category

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.compose.AsyncImage
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.components.formatPlayCount
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors

/** 分类内容加载模式 */
sealed class CategoryLoadMode {
    /** 新歌/榜单歌曲列表（top/song） */
    data class TopSongs(val type: Int) : CategoryLoadMode()

    /** 榜单歌单详情（热歌榜） */
    data class PlaylistDetail(val id: Long) : CategoryLoadMode()

    /** 分类歌单列表（纯音乐/摇滚/民谣） */
    data class TopPlaylists(val cat: String) : CategoryLoadMode()
}

/** 分类页配置 */
data class CategorySpec(
    val title: String,
    val mode: CategoryLoadMode,
)

/** 由分类名称生成加载配置（发现页 Chips 调用） */
fun categorySpecOf(name: String): CategorySpec = when (name) {
    "热歌榜" -> CategorySpec(name, CategoryLoadMode.PlaylistDetail(3778678))
    "每日推荐", "新歌速递" -> CategorySpec(name, CategoryLoadMode.TopSongs(0))
    "华语" -> CategorySpec(name, CategoryLoadMode.TopSongs(7))
    "欧美" -> CategorySpec(name, CategoryLoadMode.TopSongs(96))
    else -> CategorySpec(name, CategoryLoadMode.TopPlaylists(name)) // 纯音乐/摇滚/民谣
}

/** 分类音乐页：展示歌曲列表或歌单列表 */
@Composable
fun CategoryMusicScreen(
    spec: CategorySpec,
    repository: MusicRepository,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val colors = LocalQMusicColors.current
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(spec) {
        failed = false
        songs = null
        playlists = null
        try {
            when (val mode = spec.mode) {
                is CategoryLoadMode.TopSongs -> songs = repository.getTopSongs(mode.type)
                is CategoryLoadMode.PlaylistDetail -> {
                    val detail = repository.getPlaylistDetail(mode.id)
                    if (detail == null) failed = true else songs = detail.tracks
                }
                is CategoryLoadMode.TopPlaylists -> playlists = repository.getTopPlaylistsByCat(mode.cat)
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.textPrimary,
                )
            }
            Text(
                spec.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
            )
        }

        when {
            songs == null && playlists == null && !failed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientStart)
                }
            }
            failed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.load_failed), fontSize = 13.sp, color = colors.textSecondary)
                }
            }
            playlists != null -> {
                // 分类歌单列表
                val list = playlists.orEmpty()
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.empty_playlist), fontSize = 13.sp, color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(list, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onOpenPlaylist(playlist.id) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!playlist.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = playlist.coverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    Box(
                                        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Brush.linearGradient(listOf(Color(0xFFB9B9C4), Color(0xFF8A8A93)))),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("♪", fontSize = 20.sp, color = Color.White)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "▶ ${formatPlayCount(playlist.playCount)} ${stringResource(R.string.times_played)}",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                    )
                                }
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // 歌曲列表
                val list = songs.orEmpty()
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.empty_playlist), fontSize = 13.sp, color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(list, key = { index, song -> "$index-${song.id}" }) { index, song ->
                            SongRow(song = song, onClick = { onPlaySongs(list, index) })
                        }
                    }
                }
            }
        }
    }
}