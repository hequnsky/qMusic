package com.qmusic.ui.playlist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qmusic.R
import com.qmusic.data.local.LocalMusicScanner
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SongCover
import com.qmusic.ui.components.formatDuration
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors

/** 本地音乐列表页：MediaStore 扫描 + 运行时权限申请 */
@Composable
fun LocalMusicScreen(
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
) {
    val colors = LocalQMusicColors.current
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var denied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        denied = !isGranted
        if (isGranted) songs = null
    }

    LaunchedEffect(granted) {
        if (granted && songs == null) {
            songs = LocalMusicScanner.scan(context)
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
                stringResource(R.string.local_music),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
            )
        }

        when {
            // 1. 未授权：引导授权
            !granted -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(52.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (denied) stringResource(R.string.permission_denied) else stringResource(R.string.permission_required),
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.grant_permission),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(GradientStart, Color(0xFFFF4B5C))))
                                .clickable { permissionLauncher.launch(audioPermission()) }
                                .padding(horizontal = 24.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            // 2. 扫描中
            songs == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GradientStart)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.scanning_local),
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            // 3. 空列表
            songs.orEmpty().isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_local_music), fontSize = 13.sp, color = colors.textSecondary)
                }
            }
            // 4. 歌曲列表
            else -> {
                val localSongs = songs.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(localSongs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val index = localSongs.indexOfFirst { it.id == song.id }
                                    if (index >= 0) onPlaySongs(localSongs, index)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongCover(url = song.coverUrl, size = 46, corner = 10)
                            Spacer(Modifier.padding(horizontal = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    song.artistName,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(formatDuration(song.durationMs), fontSize = 11.sp, color = colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
}

/** 返回当前系统版本的音频读取权限 */
private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED