package com.qmusic.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.entity.LocalPlaylist
import com.qmusic.data.model.Song
import com.qmusic.data.toSong
import com.qmusic.ui.components.SongCover
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.Accent
import com.qmusic.ui.theme.LocalQMusicColors
import kotlinx.coroutines.launch
import com.qmusic.ui.components.topBarTopPadding

/** 我的歌单：创建、重命名、删除、进入查看曲目 */
@Composable
fun MyPlaylistsScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onOpenPlaylist: (LocalPlaylist) -> Unit,
) {
    BackHandler { onBack() }
    val colors = LocalQMusicColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val vm: PlaylistViewModel = viewModel(
        factory = viewModelFactory { initializer { PlaylistViewModel(repository) } },
    )
    val playlists by vm.playlists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = topBarTopPadding(), end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                )
            }
            Text(
                "我的歌单",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建歌单", tint = colors.textPrimary)
            }
        }

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "还没有创建歌单",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "点击右上角 + 创建",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(playlists, key = { it.playlist.id }) { item ->
                    val p = item.playlist
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(p) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SongCover(url = p.coverUrl, size = 52, corner = 10)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${item.songCount} 首",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                            )
                        }
                        // 更多：重命名 / 删除
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "更多",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    onClick = {
                                        menuOpen = false
                                        renameTarget = p.id to p.name
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = Accent) },
                                    onClick = {
                                        menuOpen = false
                                        deleteTarget = p.id to p.name
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                scope.launch { repository.createPlaylist(name) }
                showCreate = false
            },
        )
    }

    // 重命名对话框
    renameTarget?.let { (id, current) ->
        var text by remember(id) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名歌单", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 30) text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        scope.launch { repository.renamePlaylist(id, text) }
                        renameTarget = null
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    // 删除确认
    deleteTarget?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除歌单", fontSize = 16.sp) },
            text = { Text("确定删除「$name」吗？歌单内的歌曲会一并移除。", fontSize = 13.5.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deletePlaylist(id) }
                    deleteTarget = null
                }) { Text("删除", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

/** 歌单详情：查看 / 播放 / 移出歌单 */
@Composable
fun PlaylistSongsScreen(
    repository: MusicRepository,
    playlistId: Long,
    playlistName: String,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    BackHandler { onBack() }
    val colors = LocalQMusicColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val songs by remember(playlistId) {
        repository.observePlaylistSongs(playlistId)
    }.collectAsState(initial = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = topBarTopPadding(), end = 16.dp, bottom = 8.dp),
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
                Text(
                    playlistName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${songs.size} 首", fontSize = 11.sp, color = colors.textSecondary)
            }
            if (songs.isNotEmpty()) {
                IconButton(onClick = { onPlaySongs(songs.map { it.toSong() }, 0) }) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "播放全部",
                        tint = colors.textPrimary,
                    )
                }
            }
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("歌单还是空的", fontSize = 13.sp, color = colors.textSecondary)
            }
        } else {
            val playable = songs.map { it.toSong() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(playable, key = { i, s -> "$i-${s.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySongs(playable, index) },
                        trailing = {
                            IconButton(onClick = {
                                scope.launch {
                                    repository.removeSongFromPlaylist(playlistId, song.id)
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "移出歌单",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
