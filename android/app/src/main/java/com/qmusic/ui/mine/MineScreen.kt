package com.qmusic.ui.mine

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.data.local.LoginStore
import com.qmusic.data.local.ServerConfig
import com.qmusic.data.model.Song
import com.qmusic.ui.components.PrivacyDialog
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors
import com.qmusic.ui.theme.LocalThemeController
import com.qmusic.ui.theme.ThemeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MineScreen(
    repository: MusicRepository,
    onPlaySong: (Song) -> Unit,
    onOpenLocalMusic: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenMyPlaylists: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    val viewModel: MineViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MineViewModel(
                    repository,
                    this[ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY]
                        ?: error("Application required"),
                )
            }
        },
    )
    val colors = LocalQMusicColors.current
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    var showPrivacy by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
        item { MineHeader(loginState = loginState, onLoginClick = { showLogin = true }) }

        // 功能卡片：2x2 网格（我喜欢的 / 最近播放 / 本地音乐 / 我的下载）
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(
                        icon = Icons.Filled.Favorite,
                        label = stringResource(R.string.liked_songs),
                        sub = "${favorites.size} ${stringResource(R.string.songs)}",
                        onClick = onOpenFavorites,
                    )
                    ActionCard(
                        icon = Icons.Filled.History,
                        label = stringResource(R.string.recent_plays),
                        sub = "${history.size} ${stringResource(R.string.songs)}",
                        onClick = onOpenHistory,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(
                        icon = Icons.Filled.Folder,
                        label = stringResource(R.string.local_music),
                        onClick = onOpenLocalMusic,
                    )
                    ActionCard(
                        icon = Icons.Filled.Download,
                        label = stringResource(R.string.my_downloads),
                        onClick = onOpenDownloads,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(
                        icon = Icons.Filled.LibraryMusic,
                        label = "我的歌单",
                        onClick = onOpenMyPlaylists,
                    )
                    ActionCard(
                        icon = Icons.Filled.FileUpload,
                        label = "导入歌单",
                        sub = if (loginState.loggedIn) "文字 / 链接" else "需登录",
                        onClick = {
                            if (loginState.loggedIn) showImportDialog = true
                            else showLogin = true
                        },
                    )
                }
            }
        }

        // 主题切换（多套深色风格，可任选）
        item {
            val controller = LocalThemeController.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
                    .clickable { showThemePicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(controller.current.brand.gradientStart, controller.current.brand.gradientEnd),
                            ),
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "主题",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        controller.current.label + " · 点击切换",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
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

        // 服务器地址（真机切换电脑局域网 IP 时使用）
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card)
                    .clickable { showServerDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.server_address),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        ServerConfig.getBaseUrl(context),
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 退出登录（已登录时显示）
        if (loginState.loggedIn) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.card)
                        .clickable {
                            viewModel.logout()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.logout),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GradientStart,
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
        }

        // 「我喜欢的」「最近播放」不再内嵌在本页（首页信息密度太高、页面太长），
        // 改为上面的入口卡片 → 独立列表页（FavoriteSongsScreen / HistorySongsScreen）。
    }

    // 隐私政策查看弹窗（仅确认关闭）
    if (showPrivacy) {
        PrivacyDialog(
            onAgree = { showPrivacy = false },
            requireChoice = false,
        )
    }

    // 服务器地址设置
    if (showServerDialog) {
        var url by remember { mutableStateOf(ServerConfig.getBaseUrl(context)) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = Color(0xFF1B1B1F),
            title = { Text(stringResource(R.string.server_address), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.server_address_hint),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    // 一键切换线上环境（Cloudflare 隧道 → 本机网关）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌐", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                ServerConfig.useOnline(context)
                                showServerDialog = false
                                Toast.makeText(context, R.string.server_switched_online, Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                stringResource(R.string.server_use_online),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GradientStart
                            )
                        }
                        if (ServerConfig.isOnline(context)) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.server_online_active),
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ServerConfig.setBaseUrl(context, url)
                    showServerDialog = false
                }) {
                    Text(stringResource(R.string.save), color = GradientStart)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.6f))
                }
            },
        )
    }

    // 主题选择
    if (showThemePicker) {
        ThemePickerDialog(onDismiss = { showThemePicker = false })
    }

    // 网易云二维码登录
    if (showLogin) {
        LoginScreen(
            repository = repository,
            onBack = { showLogin = false },
            onLoginSuccess = { cookie, uid ->
                viewModel.applyLogin(cookie, uid)
                showLogin = false
            },
        )
    }

    // 导入歌单
    if (showImportDialog) {
        ImportPlaylistDialog(
            repository = repository,
            onDismiss = { showImportDialog = false },
        )
    }
    }
}

@Composable
private fun ThemePickerDialog(onDismiss: () -> Unit) {
    val controller = LocalThemeController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1F),
        title = {
            Text("选择主题", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeStyle.entries.forEach { style ->
                    val selected = style == controller.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Color.White.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.04f),
                            )
                            .clickable {
                                controller.setTheme(style)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 主题迷你预览图（手机缩略效果）
                        ThemePreview(style = style, modifier = Modifier.size(width = 52.dp, height = 88.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            style.label,
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(style.brand.accent),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color.White.copy(alpha = 0.7f))
            }
        },
    )
}

/**
 * 主题迷你预览图：模拟手机界面缩略效果。
 * 仅依赖 [ThemeStyle.brand]，换主题时预览随之更新。
 */
@Composable
private fun ThemePreview(style: ThemeStyle, modifier: Modifier = Modifier) {
    val bg = if (style.dark) Color(0xFF121216) else Color(0xFFF7F7F9)
    val card = if (style.dark) Color(0xFF1E1E24) else Color(0xFFFFFFFF)
    val text = if (style.dark) Color(0xFFECECEF) else Color(0xFF15161A)
    val sub = if (style.dark) Color(0xFF9A9AA3) else Color(0xFF8A8A93)
    val brand = style.brand

    Canvas(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        val w = size.width
        val h = size.height
        // 手机外壳圆角背景
        drawRoundRect(
            color = Color(0xFF000000),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        )
        // 屏幕背景
        drawRoundRect(
            color = bg,
            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(w - 4.dp.toPx(), h - 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
        // 顶栏
        drawRoundRect(
            color = card,
            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(w - 4.dp.toPx(), 16.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
        // 顶栏返回箭头
        drawLine(
            color = text,
            start = Offset(7.dp.toPx(), 8.dp.toPx()),
            end = Offset(10.dp.toPx(), 11.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
        )
        drawLine(
            color = text,
            start = Offset(10.dp.toPx(), 11.dp.toPx()),
            end = Offset(7.dp.toPx(), 14.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
        )
        // 顶栏标题（主题色）
        drawLine(
            color = brand.accent,
            start = Offset(13.dp.toPx(), 7.dp.toPx()),
            end = Offset(26.dp.toPx(), 7.dp.toPx()),
            strokeWidth = 2.2.dp.toPx(),
        )
        drawLine(
            color = brand.accent,
            start = Offset(13.dp.toPx(), 10.dp.toPx()),
            end = Offset(22.dp.toPx(), 10.dp.toPx()),
            strokeWidth = 2.2.dp.toPx(),
        )
        // 主内容区：渐变封面（圆）
        val coverCenter = Offset(w / 2f, (16.dp.toPx() + (h - 16.dp.toPx() - 22.dp.toPx()) * 0.40f))
        val coverRadius = (w * 0.30f).coerceAtMost((h - 16.dp.toPx() - 22.dp.toPx()) * 0.38f)
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(brand.gradientStart, brand.gradientEnd),
                start = Offset(coverCenter.x - coverRadius, coverCenter.y - coverRadius),
                end = Offset(coverCenter.x + coverRadius, coverCenter.y + coverRadius),
            ),
            radius = coverRadius,
            center = coverCenter,
        )
        // 封面上的音符
        val noteColor = Color.White.copy(alpha = 0.9f)
        val nr = coverRadius * 0.22f
        drawOval(
            color = noteColor,
            topLeft = Offset(coverCenter.x - nr * 0.4f, coverCenter.y - nr * 0.35f),
            size = androidx.compose.ui.geometry.Size(nr * 0.8f, nr * 0.55f),
        )
        drawLine(
            color = noteColor,
            start = Offset(coverCenter.x - nr * 0.4f, coverCenter.y + nr * 0.2f),
            end = Offset(coverCenter.x - nr * 0.4f, coverCenter.y - nr * 0.6f),
            strokeWidth = 1.4.dp.toPx(),
        )
        drawCircle(
            color = noteColor,
            radius = nr * 0.22f,
            center = Offset(coverCenter.x - nr * 0.4f + nr * 0.55f, coverCenter.y + nr * 0.2f),
        )
        // 封面下方文字条
        drawRoundRect(
            color = card,
            topLeft = Offset(4.dp.toPx(), coverCenter.y + coverRadius + 5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(w - 8.dp.toPx(), 9.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        // 播放按钮（强调色圆）
        val btnCenter = Offset(w / 2f, h - 13.dp.toPx())
        drawCircle(color = brand.accent, radius = 5.5.dp.toPx(), center = btnCenter)
        // 播放三角
        val path = Path().apply {
            moveTo(btnCenter.x - 1.8.dp.toPx(), btnCenter.y - 2.6.dp.toPx())
            lineTo(btnCenter.x - 1.8.dp.toPx(), btnCenter.y + 2.6.dp.toPx())
            lineTo(btnCenter.x + 2.6.dp.toPx(), btnCenter.y)
            close()
        }
        drawPath(path, color = Color.White)
        // 底部导航点
        drawCircle(color = brand.accent, radius = 1.3.dp.toPx(), center = Offset(w / 2f - 8.dp.toPx(), h - 4.dp.toPx()))
        drawCircle(color = sub, radius = 1.3.dp.toPx(), center = Offset(w / 2f, h - 4.dp.toPx()))
        drawCircle(color = sub, radius = 1.3.dp.toPx(), center = Offset(w / 2f + 8.dp.toPx(), h - 4.dp.toPx()))
    }
}

@Composable
private fun MineHeader(
    loginState: LoginUiState,
    onLoginClick: () -> Unit = {},
) {
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
            if (loginState.loggedIn && !loginState.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = loginState.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFFFD93D), Color(0xFFFF6B6B)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🎧", fontSize = 28.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (loginState.loggedIn) loginState.nickname else "音乐爱好者小Q",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (loginState.loggedIn) "🎵 已登录网易云账号" else "🎵 点击登录，同步关注/粉丝/获赞",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            if (!loginState.loggedIn) {
                Text(
                    stringResource(R.string.login),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .clickable(onClick = onLoginClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 18.dp).padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            listOf(
                formatCount(loginState.follows) to "关注",
                formatCount(loginState.followeds) to "粉丝",
                formatCount(loginState.eventCount) to "获赞",
            ).forEach { (num, label) ->
                Column(modifier = Modifier.clickable(enabled = !loginState.loggedIn, onClick = onLoginClick)) {
                    Text(num, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

private fun formatCount(count: Long): String {
    if (count >= 10_000) {
        val wan = count / 10_000.0
        return if (wan >= 10) "${wan.toLong()}万" else String.format("%.1f万", wan)
    }
    return count.toString()
}

@Composable
private fun RowScope.ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String? = null,
    onClick: () -> Unit = {},
) {
    val colors = LocalQMusicColors.current
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = GradientStart, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 10.sp, color = colors.textSecondary)
        }
    }
}

// ==================== 导入歌单 ====================

/**
 * 网易云歌单导入对话框：支持「文字导入」与「链接导入」两种方式。
 * 仅登录后可见（未登录时入口直接引导去登录）。
 */
@Composable
private fun ImportPlaylistDialog(
    repository: MusicRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalQMusicColors.current
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf("") }
    var taskId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        containerColor = Color(0xFF1B1B1F),
        title = {
            Text("导入歌单", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column {
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = GradientStart,
                ) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("文字导入", fontSize = 13.sp, color = if (tab == 0) GradientStart else Color.White.copy(alpha = 0.5f)) },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("链接导入", fontSize = 13.sp, color = if (tab == 1) GradientStart else Color.White.copy(alpha = 0.5f)) },
                    )
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    singleLine = true,
                    label = { Text("歌单名（可选）", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                if (tab == 0) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        minLines = 4,
                        label = { Text("每行一首：歌名 歌手", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        minLines = 4,
                        label = { Text("每行一个歌单分享链接", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (importing) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (taskId.isBlank()) "提交中…" else "已提交，正在查询任务状态…",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                if (resultMsg.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(resultMsg, fontSize = 12.sp, color = if (resultMsg.startsWith("✅")) Color(0xFF7CFC98) else Color(0xFFFF6B6B))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !importing,
                onClick = {
                    val cookie = LoginStore.getCookie(context)
                    if (cookie.isBlank()) {
                        resultMsg = "❌ 未登录，请先登录网易云账号"
                        return@TextButton
                    }
                    val content = if (tab == 0) text.trim() else link.trim()
                    if (content.isBlank()) {
                        resultMsg = "❌ 请输入内容"
                        return@TextButton
                    }
                    scope.launch {
                        importing = true
                        resultMsg = ""
                        taskId = ""
                        try {
                            val id = if (tab == 0) {
                                repository.importPlaylistByText(content, playlistName.ifBlank { null }, cookie)
                            } else {
                                val links = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                repository.importPlaylistByLink(links, playlistName.ifBlank { null }, cookie)
                            }
                            if (id.isNullOrBlank()) {
                                resultMsg = "❌ 导入失败：未拿到任务 id（可能是接口变更或网络问题）"
                                importing = false
                                return@launch
                            }
                            taskId = id
                            // 轮询任务状态（最多 30 次 × 2s = 60s）
                            for (i in 1..30) {
                                delay(2000)
                                val (status, message, _) = repository.importPlaylistStatus(id, cookie)
                                if (status == 0) {
                                    resultMsg = "✅ 导入成功！歌单已生成（可在网易云 App 查看）"
                                    importing = false
                                    return@launch
                                } else if (status != 1) {
                                    resultMsg = "❌ 导入失败：$message"
                                    importing = false
                                    return@launch
                                }
                            }
                            resultMsg = "⏳ 已提交，请稍后在网易云 App 查看结果"
                            importing = false
                        } catch (e: Exception) {
                            resultMsg = "❌ 导入异常：${e.message}"
                            importing = false
                        }
                    }
                },
            ) {
                Text("开始导入", color = GradientStart)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("关闭", color = Color.White.copy(alpha = 0.6f))
            }
        },
    )
}