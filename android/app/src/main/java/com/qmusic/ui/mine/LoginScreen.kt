package com.qmusic.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.ui.theme.LocalQMusicColors
import kotlinx.coroutines.delay
import com.qmusic.ui.components.topBarTopPadding

/**
 * 网易云二维码登录页。
 * 流程：获取 unikey → 生成二维码 → 轮询扫码状态（801 等待扫码 / 802 待确认 / 803 登录成功 / 800 过期刷新）。
 */
@Composable
fun LoginScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onLoginSuccess: (cookie: String, userId: Long) -> Unit,
) {
    val colors = LocalQMusicColors.current
    var qrImage by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            var key = repository.getQrKey()
            val (img, _) = repository.createQr(key)
            qrImage = img
            loading = false
            while (true) {
                val (code, cookie) = repository.checkQr(key)
                when (code) {
                    801 -> status = "请使用网易云音乐 App 扫码登录"
                    802 -> status = "已扫码，请在手机上确认"
                    803 -> {
                        val uid = try {
                            repository.getLoginAccount(cookie)?.userId ?: 0L
                        } catch (_: Exception) {
                            0L
                        }
                        onLoginSuccess(cookie, uid)
                        return@LaunchedEffect
                    }
                    800 -> {
                        key = try {
                            repository.getQrKey()
                        } catch (_: Exception) {
                            return@LaunchedEffect
                        }
                        val (img2, _) = repository.createQr(key)
                        qrImage = img2
                        status = "二维码已过期，已刷新"
                    }
                    else -> status = "等待登录..."
                }
                delay(2000)
            }
        } catch (_: Exception) {
            failed = true
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = topBarTopPadding(), end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.textPrimary,
                )
            }
            Text(stringResource(R.string.login), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text("🔐", fontSize = 34.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.login_desc),
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                if (failed) {
                    Text(
                        stringResource(R.string.login_failed),
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                    )
                } else if (loading || qrImage == null) {
                    Text(stringResource(R.string.login_generating), fontSize = 13.sp, color = colors.textSecondary)
                } else {
                    AsyncImage(
                        model = qrImage,
                        contentDescription = "登录二维码",
                        modifier = Modifier
                            .size(224.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(10.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(status, fontSize = 12.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.login_scan_hint),
                        fontSize = 10.5.sp,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}