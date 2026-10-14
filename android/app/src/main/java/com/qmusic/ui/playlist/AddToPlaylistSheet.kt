package com.qmusic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SongCover
import com.qmusic.ui.theme.LocalQMusicColors
import kotlinx.coroutines.launch

/**
 * 「添加到歌单」底部弹窗。
 * 列出全部本地歌单供勾选，支持直接新建歌单并把当前歌曲加进去。
 */
@Composable
fun AddToPlaylistSheet(
    repository: MusicRepository,
    song: Song,
    onDismiss: () -> Unit,
    onManagePlaylists: () -> Unit,
) {
    val colors = LocalQMusicColors.current
    val scope = rememberCoroutineScope()
    val vm: PlaylistViewModel = viewModel(
        factory = viewModelFactory { initializer { PlaylistViewModel(repository) } },
    )
    val playlists by vm.playlists.collectAsState()
    val songPlaylistIds by vm.songPlaylistIds.collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(false) }

    // 打开时刷新一次「当前歌曲已在哪些歌单」
    LaunchedEffect(song.id) { vm.refreshSongPlaylists(song.id) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(colors.background)
            // 面板自身消费掉点击，避免按到面板空白处时穿透到底下的遮罩把弹窗关掉
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(bottom = 20.dp),
    ) {
        Column {
            // 标题栏
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "添加到歌单",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        song.name,
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onManagePlaylists) {
                    Text("管理歌单", fontSize = 12.5.sp, color = colors.textSecondary)
                }
            }

            // 新建歌单入口
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showCreate = true }
                    .padding(vertical = 11.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(colors.card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("新建歌单", fontSize = 14.sp, color = colors.textPrimary)
            }

            if (playlists.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "还没有歌单，点击上方新建一个",
                        fontSize = 12.5.sp,
                        color = colors.textTertiary,
                    )
                }
            } else {
                // 高度自适应：歌单少时面板自然收窄，不再固定留出一大块空白
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(playlists, key = { it.playlist.id }) { item ->
                        val checked = songPlaylistIds.contains(item.playlist.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        if (checked) {
                                            vm.removeFromPlaylist(item.playlist.id, song)
                                        } else {
                                            vm.addToPlaylist(item.playlist.id, song)
                                        }
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongCover(url = item.playlist.coverUrl, size = 40, corner = 8)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.playlist.name,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${item.songCount} 首",
                                    fontSize = 11.sp,
                                    color = colors.textTertiary,
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    scope.launch {
                                        if (checked) {
                                            vm.removeFromPlaylist(item.playlist.id, song)
                                        } else {
                                            vm.addToPlaylist(item.playlist.id, song)
                                        }
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.textPrimary,
                                    uncheckedColor = colors.textTertiary,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    // 新建歌单对话框
    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                scope.launch {
                    vm.createPlaylistWithSong(name, song)
                    showCreate = false
                }
            },
        )
    }
}

/** 新建歌单对话框（同时被歌单管理页复用） */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val valid = name.isNotBlank()
    // 打开即聚焦并弹出软键盘（延迟一帧等 Dialog window 拿到焦点，失败则静默降级）
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单", fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    placeholder = { Text("给歌单起个名字", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    try {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    } catch (_: Exception) {
                        // 拿不到焦点也不影响：输入框外框常显，点击即可输入
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onConfirm(name) },
                enabled = valid,
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
