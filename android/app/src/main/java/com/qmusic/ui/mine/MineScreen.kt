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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.qmusic.data.local.ServerConfig
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
import kotlinx.coroutines.launch

@Composable
fun MineScreen(
    repository: MusicRepository,
    darkMode: Boolean = false,
    onThemeChanged: (Boolean) -> Unit = {},
    onPlaySong: (Song) -> Unit,
    onOpenLocalMusic: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 我的页 LazyColumn item 索引：0 header / 1 ActionCard / 2 深色模式 / 3 隐私政策 / 4 我喜欢的标题 / 5.. 收藏 / ... 最近播放标题
    val recentTitleIndex = 5 + (if (favorites.isEmpty()) 1 else favorites.size)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
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
                        onClick = { scope.launch { listState.animateScrollToItem(4) } },
                    )
                    ActionCard(
                        icon = Icons.Filled.History,
                        label = stringResource(R.string.recent_plays),
                        onClick = { scope.launch { listState.animateScrollToItem(recentTitleIndex) } },
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