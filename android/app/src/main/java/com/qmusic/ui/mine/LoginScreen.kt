package com.qmusic.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qmusic.R
import com.qmusic.data.MusicRepository
import com.qmusic.ui.components.topBarTopPadding
import com.qmusic.ui.theme.Accent
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.LocalQMusicColors
import kotlinx.coroutines.launch

/**
 * 网易云账号登录页（手机号 + 密码）。
 *
 * 登录方式说明：原先为二维码扫码登录（需跳转网易云 App 扫码），
 * 现改用网易云 weapi 的 `/login/cellphone` 网页云接口——手机号 + 密码
 * 直接换 cookie，不依赖第二台设备，失败时还能拿到网易云返回的错误原因。
 *
 * 注意：本页由「我的」页以**全屏 Dialog** 形式包裹打开（见 MineScreen），
 * 独立 window 保证覆盖底部导航栏，不会点到下层页面的按钮。
 */
@Composable
fun LoginScreen(
    repository: MusicRepository,
    onBack: () -> Unit,
    onLoginSuccess: (cookie: String, userId: Long) -> Unit,
) {
    val colors = LocalQMusicColors.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun submit() {
        if (loading) return
        if (phone.length != 11) {
            phoneError = true
            return
        }
        if (password.isEmpty()) {
            passwordError = true
            return
        }
        phoneError = false
        passwordError = false
        error = ""
        keyboardController?.hide()
        loading = true
        scope.launch {
            try {
                val cookie = repository.loginCellphone(phone, password)
                // 登录成功后顺手拉一次账号信息拿 userId（失败不阻塞登录）
                val uid = try {
                    repository.getLoginAccount(cookie)?.userId ?: 0L
                } catch (_: Exception) {
                    0L
                }
                onLoginSuccess(cookie, uid)
            } catch (e: Exception) {
                loading = false
                error = e.message?.takeIf { it.isNotBlank() } ?: "登录失败，请检查网络"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // 全屏 Dialog 仍可能覆盖到导航栏区域：内容整体抬到虚拟返回键之上
            .navigationBarsPadding()
            .imePadding(),
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

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text("📱", fontSize = 34.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.login_desc),
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it.filter { c -> c.isDigit() }.take(11)
                    phoneError = false
                },
                singleLine = true,
                isError = phoneError,
                supportingText = if (phoneError) {
                    { Text(stringResource(R.string.login_phone_invalid), fontSize = 11.sp) }
                } else {
                    null
                },
                label = { Text(stringResource(R.string.login_phone)) },
                placeholder = { Text(stringResource(R.string.login_phone_hint)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = false
                },
                singleLine = true,
                isError = passwordError,
                supportingText = if (passwordError) {
                    { Text(stringResource(R.string.login_password_empty), fontSize = 11.sp) }
                } else {
                    null
                },
                label = { Text(stringResource(R.string.login_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, fontSize = 12.sp, color = Accent, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { submit() },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = GradientStart),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = GradientEnd,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(stringResource(R.string.login_submit), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.login_scan_hint),
                fontSize = 10.5.sp,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
