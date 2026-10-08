package com.qmusic.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import com.qmusic.data.model.Album
import com.qmusic.data.model.Artist
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SectionTitle
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.Background
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.TextPrimary
import com.qmusic.ui.theme.TextSecondary

@Composable
fun MineScreen(
    repository: MusicRepository,
    onPlaySong: (Song) -> Unit,
) {
    val viewModel: MineViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MineViewModel(repository) }
        },
    )
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { MineHeader() }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionCard(icon = Icons.Filled.Favorite, label = "我喜欢的")
                ActionCard(icon = Icons.Filled.History, label = "最近播放")
                ActionCard(icon = Icons.Filled.Folder, label = "本地音乐")
            }
        }

        item { SectionTitle(title = "我喜欢的", more = "${favorites.size} 首") }
        if (favorites.isEmpty()) {
            item { EmptyText("还没有喜欢的歌曲，快去播放页点 ❤️ 吧～") }
        } else {
            items(favorites, key = { it.songId }) { favorite ->
                SongRow(song = favorite.toSong(), onClick = { onPlaySong(favorite.toSong()) })
            }
        }

        item { SectionTitle(title = "最近播放", more = "${history.size} 首") }
        if (history.isEmpty()) {
            item { EmptyText("暂无播放记录") }
        } else {
            items(history, key = { it.songId }) { item ->
                SongRow(song = item.toSong(), onClick = { onPlaySong(item.toSong()) })
            }
        }
    }
}

@Composable
private fun MineHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF8A5C), Color(0xFFFF4B5C), Color(0xFFC1356D)),
                ),
            )
            .padding(top = 56.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFFD93D), Color(0xFFFF6B6B)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("🎧", fontSize = 28.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("音乐爱好者小Q", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("🎵 让音乐治愈每一天", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 18.dp).padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            listOf("12" to "关注", "58" to "粉丝", "326" to "获赞").forEach { (num, label) ->
                Column {
                    Text(num, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = GradientStart, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private fun Favorite.toSong(): Song = Song(
    id = songId,
    name = name,
    arList = listOf(Artist(name = artist)),
    alAlbum = Album(name = album, picUrl = picUrl),
    dt = durationMs,
)

private fun History.toSong(): Song = Song(
    id = songId,
    name = name,
    arList = listOf(Artist(name = artist)),
    alAlbum = Album(name = album, picUrl = picUrl),
    dt = durationMs,
)