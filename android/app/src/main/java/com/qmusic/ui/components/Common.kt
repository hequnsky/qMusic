package com.qmusic.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qmusic.R
import com.qmusic.data.model.Playlist
import com.qmusic.data.model.Song
import com.qmusic.player.PlayerUiState
import com.qmusic.ui.theme.Dark
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors

/** 时长格式化：45000 -> 00:45 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

/** 播放量格式化：123456 -> 12万 */
fun formatPlayCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 1e8)
    count >= 10_000 -> "%.0f万".format(count / 1e4)
    else -> count.toString()
}

/** 未接线功能统一提示「暂未实现」 */
fun showNotImplemented(context: Context) {
    Toast.makeText(context, R.string.not_implemented, Toast.LENGTH_SHORT).show()
}

/** 封面图（无图时显示渐变占位） */
@Composable
fun SongCover(url: String?, size: Int, corner: Int = 10) {
    val shape = RoundedCornerShape(corner.dp)
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.size(size.dp).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFFB9B9C4), Color(0xFF8A8A93)))),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", fontSize = (size * 0.42).sp, color = Color.White)
        }
    }
}

/** 歌曲行 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalQMusicColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(url = song.coverUrl, size = 46, corner = 10)
        Spacer(Modifier.width(12.dp))
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
                "${song.artistName} · ${song.albumName}",
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Text(formatDuration(song.durationMs), fontSize = 11.sp, color = colors.textTertiary)
        }
    }
}

/** 区块标题 */
@Composable
fun SectionTitle(title: String, more: String = stringResource(R.string.more), onMore: () -> Unit = {}) {
    val colors = LocalQMusicColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            more,
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.clickable(onClick = onMore),
        )
    }
}

/** 横向歌单卡片 */
@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier.width(128.dp).clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.size(128.dp)) {
            if (!playlist.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(128.dp).clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.size(128.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFB9B9C4), Color(0xFF8A8A93)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♪", fontSize = 40.sp, color = Color.White)
                }
            }
            Text(
                "▶ ${formatPlayCount(playlist.playCount)}",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp, end = 9.dp).align(Alignment.TopEnd),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
        )
    }
}

/** 迷你播放条 */
@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Dark.copy(alpha = 0.94f))
            .clickable(onClick = onClick),
    ) {
        // 底部进度条
        val progress = if (state.durationMs > 0) {
            (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier.fillMaxWidth(progress).height(2.dp)
                    .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(url = state.song?.coverUrl, size = 42, corner = 10)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.song?.name ?: "",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.song?.artistName ?: "",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** 底部导航 */
@Composable
fun QMusicBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onPlayTab: () -> Unit,
) {
    val colors = LocalQMusicColors.current
    Surface(color = colors.bottomBar, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(bottom = 8.dp),
        ) {
            BottomTabItem(
                label = stringResource(R.string.discover),
                icon = Icons.Filled.Home,
                selected = selectedTab == 0,
            ) {
                onTabSelected(0)
            }
            BottomTabItem(
                label = stringResource(R.string.player),
                icon = Icons.Filled.MusicNote,
                selected = false,
            ) {
                onPlayTab()
            }
            BottomTabItem(
                label = stringResource(R.string.mine),
                icon = Icons.Filled.Person,
                selected = selectedTab == 1,
            ) {
                onTabSelected(1)
            }
        }
    }
}

@Composable
private fun RowScope.BottomTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().height(56.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) colors.textPrimary else colors.tabInactive,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.textPrimary else colors.tabInactive,
        )
    }
}