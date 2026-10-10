package com.qmusic.ui.comment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Comment
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 歌曲评论列表页（/comment/music） */
@Composable
fun CommentScreen(
    songId: Long,
    songName: String,
    repository: MusicRepository,
    onBack: () -> Unit,
) {
    // 系统返回键：先关闭评论页，返回播放页/上一页
    BackHandler { onBack() }
    val colors = LocalQMusicColors.current
    var comments by remember { mutableStateOf<List<Comment>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(songId) {
        comments = try {
            repository.getMusicComments(songId)
        } catch (_: Exception) {
            failed = true
            emptyList()
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
            Column {
                Text(
                    stringResource(R.string.comments),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                )
                Text(
                    songName,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            comments == null && !failed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientStart)
                }
            }
            comments.orEmpty().isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty_comments), fontSize = 13.sp, color = colors.textSecondary)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(comments.orEmpty(), key = { index, comment -> "$index-${comment.content.hashCode()}" }) { _, comment ->
                        Row(Modifier.fillMaxWidth()) {
                            if (!comment.user.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = comment.user.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                )
                            } else {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape)
                                        .background(colors.card),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("🎵", fontSize = 16.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        comment.user.nickname,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatCommentTime(comment.time),
                                        fontSize = 10.sp,
                                        color = colors.textTertiary,
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    comment.content,
                                    fontSize = 13.5.sp,
                                    color = colors.textPrimary,
                                    lineHeight = 19.sp,
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "👍 ${comment.likedCount} ${stringResource(R.string.comment_likes)}",
                                    fontSize = 10.5.sp,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCommentTime(time: Long): String {
    if (time <= 0) return ""
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time * 1000))
}