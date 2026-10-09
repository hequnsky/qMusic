package com.qmusic.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.qmusic.R

/**
 * 隐私政策对话框。
 * - 首次启动：requireChoice = true，必须「同意」或「不同意」。
 * - 设置页查看：requireChoice = false，仅“我知道了”确认关闭。
 */
@Composable
fun PrivacyDialog(
    onAgree: () -> Unit,
    onDisagree: (() -> Unit)? = null,
    requireChoice: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { onAgree() },
        title = { Text(stringResource(R.string.privacy_title)) },
        text = {
            Text(
                stringResource(R.string.privacy_content),
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(stringResource(R.string.privacy_agree))
            }
        },
        dismissButton = {
            if (requireChoice && onDisagree != null) {
                TextButton(onClick = onDisagree) {
                    Text(stringResource(R.string.privacy_disagree))
                }
            }
        },
    )
}