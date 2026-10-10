package com.qmusic.ui.download

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qmusic.R
import com.qmusic.data.local.DownloadManager
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.LocalQMusicColors

/** 我的下载列表页（应用私有目录，无需存储权限） */
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    val colors = LocalQMusicColors.current
    val context = LocalContext.current
    var songs by remember { mutableStateOf(DownloadManager.getDownloadedSongs(context)) }

    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 40.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.textPrimary,
                )
            }
            Text(stringResource(R.string.my_downloads), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_downloads), fontSize = 13.sp, color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            ) {
                itemsIndexed(songs) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySongs(songs, index) },
                        trailing = {
                            IconButton(onClick = {
                                DownloadManager.remove(context, song.id)
                                songs = DownloadManager.getDownloadedSongs(context)
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
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