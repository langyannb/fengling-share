package com.fengling.share.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 图形验证码对话框 (契约 v1: send_code / email_verify_send 都必须带 captcha_token + captcha_code)
 *
 * 用法: 调用方在 onConfirm 里自己去请求, 请求失败时把失败文案通过 errorMessage 回传,
 * 并递增 refreshKey 让图片自动换一张 (图形验证码是一次性的)。
 *
 * @param confirming 调用方请求中 (确定按钮置灰显示进度)
 * @param errorMessage 调用方请求失败文案 (显示在图片下方)
 * @param refreshKey 变化时重新拉取图形验证码
 */
@Composable
fun CaptchaDialog(
    title: String = "安全验证",
    subtitle: String = "请输入图片中的字符以继续",
    errorMessage: String = "",
    confirming: Boolean = false,
    refreshKey: Int = 0,
    onConfirm: (token: String, code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }

    fun loadCaptcha() {
        scope.launch {
            loading = true
            ApiClient.getCaptcha()
                .onSuccess {
                    token = it.token
                    image = it.image
                    loadError = ""
                }
                .onFailure { e -> loadError = e.message ?: "验证码加载失败" }
            loading = false
        }
    }

    // 首次展示 / 外部要求换一张时重新拉取
    LaunchedEffect(refreshKey) {
        code = ""
        loadCaptcha()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(10.dp))

                // 服务端返回的是 data:image/png;base64,... 形式; Coil 2.x 不识别 data: URI,
                // 直接交给 AsyncImage 只会渲染出一块灰底(用户反馈「验证码看不清, 是个灰色的」),
                // 所以这里自己把 base64 解码成 Bitmap 再显示。
                val captchaBitmap = remember(image) {
                    if (image.isBlank()) {
                        null
                    } else {
                        runCatching {
                            val b64 = image.substringAfter("base64,", image)
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory
                                .decodeByteArray(bytes, 0, bytes.size)
                                ?.asImageBitmap()
                        }.getOrNull()
                    }
                }

                // 图形验证码图片: 点一下换一张
                Box(
                    modifier = Modifier
                        .size(width = 260.dp, height = 94.dp)  // 服务端出图 280x100, 放大到接近 1:1 才看得清
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .clickable { loadCaptcha() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (captchaBitmap != null) {
                        Image(
                            bitmap = captchaBitmap,
                            contentDescription = "图形验证码",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    } else if (image.isNotBlank() && !loading) {
                        Text(
                            text = "图片解析失败, 点我重试",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    } else if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = "点击重试",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (loading) "正在刷新…" else "看不清? 点击图片换一张",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 8) code = it },
                    label = {
                        Text(text = "图形验证码", fontSize = 13.sp)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val tip = errorMessage.ifBlank { loadError }
                if (tip.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = tip,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFE5484D),
                    )
                }
            }
        },
        confirmButton = {
            M3TextButton(
                onClick = { onConfirm(token, code.trim()) },
                enabled = !confirming && token.isNotBlank() && code.isNotBlank(),
            ) {
                Text(
                    text = if (confirming) "提交中…" else "确定",
                    color = if (confirming || token.isBlank() || code.isBlank()) {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            M3TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        },
    )
}
