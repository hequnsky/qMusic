package com.qmusic.ui.mine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import com.qmusic.data.model.Album
import com.qmusic.data.model.Artist
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.LocalQMusicColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 「我喜欢的」独立列表页。
 *
 * 说明：「我的」页原来把收藏/历史直接内嵌在同一个 LazyColumn 里，
 * 页面很长且首页信息密度高。现在「我的」页只保留入口卡片，
 * 具体列表放到独立页面，点进来才加载、点列表项即播放。
 */
@Composable
fun FavoriteSongsScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    val songsFlow = remember(repository) {
        repository.observeFavorites().map { list -> list.map { it.toSong() } }
    }
    SongCollectionPage(
        title = stringResource(R.string.liked_songs),
        emptyText = stringResource(R.string.empty_favorite),
        songsFlow = songsFlow,
        onBack = onBack,
        onPlaySongs = onPlaySongs,
    )
}

/** 「最近播放」独立列表页 */
@Composable
fun HistorySongsScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    val songsFlow = remember(repository) {
        repository.observeHistory().map { list -> list.map { it.toSong() } }
    }
    SongCollectionPage(
        title = stringResource(R.string.recent_plays),
        emptyText = stringResource(R.string.empty_history),
        songsFlow = songsFlow,
        onBack = onBack,
        onPlaySongs = onPlaySongs,
    )
}

@Composable
private fun SongCollectionPage(
    title: String,
    emptyText: String,
    songsFlow: Flow<List<Song>>,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    // 系统返回键：先关闭列表页
    BackHandler { onBack() }
    val colors = LocalQMusicColors.current
    val songs by songsFlow.collectAsState(initial = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 40.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.textPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${songs.size} ${stringResource(R.string.songs)}", fontSize = 11.sp, color = colors.textSecondary)
            }
            if (songs.isNotEmpty()) {
                IconButton(onClick = { onPlaySongs(songs, 0) }) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.play_all),
                        tint = colors.textPrimary,
                    )
                }
            }
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, fontSize = 13.sp, color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { index, song ->
                    SongRow(song = song, onClick = { onPlaySongs(songs, index) })
                }
            }
        }
    }
}

/** Favorite -> Song（保留 source/token，多音源歌曲才能正确解析播放地址） */
internal fun Favorite.toSong(): Song = Song(
    id = songId,
    name = name,
    artists = listOf(Artist(name = artist)),
    album = Album(name = album, picUrl = picUrl),
    dt = durationMs,
    source = source,
    token = token,
)

/** History -> Song */
internal fun History.toSong(): Song = Song(
    id = songId,
    name = name,
    artists = listOf(Artist(name = artist)),
    album = Album(name = album, picUrl = picUrl),
    dt = durationMs,
    source = source,
    token = token,
)
