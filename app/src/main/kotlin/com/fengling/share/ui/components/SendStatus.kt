package com.fengling.share.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * v1.1.12 乐观发送的状态
 *
 * Sending = 本地临时消息正在等服务端返回 (转圈)
 * Failed  = 发送失败 (含服务端 2 秒 1 条的限流错误); 点一下重发, 不弹 toast 打断
 * Done    = 已经有服务端真实消息了, 本地占位该被移除
 */
enum class SendState { Sending, Failed, Done }

/** 失败提示用的红 (和 AccountScreen 的 DangerRed / UserTagChips 同一色值) */
private val SendFailRed = Color(0xFFE5484D)

/**
 * 本地临时消息下面的状态条 (群聊 / 私聊共用)。
 * Done 什么都不画 —— 那时候消息已经被服务端真实消息替换掉了。
 */
@Composable
fun SendStatusIndicator(
    state: SendState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SendState.Done -> Unit

        SendState.Sending -> Row(
            modifier = modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "发送中",
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }

        SendState.Failed -> Row(
            modifier = modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onRetry() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 红色感叹号: 一眼看出这条没发出去, 点它就是重发
            Row(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(SendFailRed),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "!",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "发送失败，点击重发",
                fontSize = 10.sp,
                color = SendFailRed,
            )
        }
    }
}
