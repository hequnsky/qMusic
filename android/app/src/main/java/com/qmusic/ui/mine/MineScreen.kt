package com.qmusic.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.qmusic.data.local.entity.Favorite
import com.qmusic.data.local.entity.History
import com.qmusic.data.model.Album
import com.qmusic.data.model.Artist
import com.qmusic.data.model.Song
import com.qmusic.ui.components.PrivacyDialog
import com.qmusic.ui.components.SectionTitle
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors
import com.qmusic.ui.theme.TextSecondary

@Composable
fun MineScreen(
    repository: MusicRepository,
    darkMode: Boolean = false,
    onThemeChanged: (Boolean) -> Unit = {},
    onPlaySong: (Song) -> Unit,
) {
    val viewModel: MineViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MineViewModel(repository) }
        },
    )
    val colors = LocalQMusicColors.current
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    var showPrivacy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
        item { MineHeader() }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionCard(icon = Icons.Filled.Favorite, label = stringResource(R.string.liked_songs))
                ActionCard(icon = Icons.Filled.History, label = stringResource(R.string.recent_plays))
                ActionCard(icon = Icons.Filled.Folder, label = stringResource(R.string.local_music))
            }
        }

        // 深色模式开关
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dark_mode),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.dark_mode_desc),
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
                Switch(checked = darkMode, onCheckedChange = onThemeChanged)
            }
        }

        // 隐私政策入口
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
                    .clickable { showPrivacy = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.privacy_policy),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        item { SectionTitle(title = stringResource(R.string.liked_songs), more = "${favorites.size} ${stringResource(R.string.songs)}") }
        if (favorites.isEmpty()) {
            item { EmptyText(stringResource(R.string.empty_favorite)) }
        } else {
            items(favorites, key = { it.songId }) { favorite ->
                SongRow(song = favorite.toSong(), onClick = { onPlaySong(favorite.toSong()) })
            }
        }

        item { SectionTitle(title = stringResource(R.string.recent_plays), more = "${history.size} ${stringResource(R.string.songs)}") }
        if (history.isEmpty()) {
            item { EmptyText(stringResource(R.string.empty_history)) }
        } else {
            items(history, key = { it.songId }) { item ->
                SongRow(song = item.toSong(), onClick = { onPlaySong(item.toSong()) })
            }
        }
    }

    // 隐私政策查看弹窗（仅确认关闭）
    if (showPrivacy) {
        PrivacyDialog(
            onAgree = { showPrivacy = false },
            requireChoice = false,
        )
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
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = GradientStart, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
    }
}

@Composable
private fun EmptyText(text: String) {
    val colors = LocalQMusicColors.current
    Text(
        text,
        fontSize = 13.sp,
        color = colors.textSecondary,
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