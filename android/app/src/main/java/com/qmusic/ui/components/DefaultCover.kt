package com.qmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart

/**
 * 默认封面图（无图 / 加载失败时的正式占位）。
 *
 * 主题化视觉：斜向品牌渐变 + 半透明音轨圈 + 白色音符图标。
 * 不再是一块灰底「♪」，而是有层次、与当前主题一致的音乐封面。
 *
 * @param circle 是否裁成圆形（播放页唱片用 true；列表方形封面用 false）
 */
@Composable
fun DefaultCover(
    modifier: Modifier = Modifier,
    circle: Boolean = false,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
    Box(
        modifier = modifier.clip(shape).background(
            Brush.linearGradient(
                colors = listOf(GradientStart, GradientEnd),
                start = Offset.Zero,
                end = Offset(2000f, 2000f),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        // 半透明音轨圈（仅圆形唱片形态下更明显）
        Box(
            modifier = Modifier
                .fillMaxSize(0.62f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxSize(0.34f),
        )
    }
}

/**
 * 带封面 URL 的封面容器：始终先绘制 [placeholder]（默认图），
 * 仅当 [url] 非空且网络图加载成功后才用真实封面覆盖。
 * 这样 URL 缺失 / 加载失败 / 加载中都不会露出空白。
 *
 * @param circle 是否裁成圆形（播放页唱片用 true）
 */
@Composable
fun CoverWithPlaceholder(
    url: String?,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
    Box(modifier = modifier.clip(shape)) {
        // 底层：默认封面，永远存在，避免空白圆
        DefaultCover(modifier = Modifier.fillMaxSize(), circle = circle)
        // 上层：真实封面（仅加载成功时显示内容；外层已裁剪，不会溢出圆角）
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}