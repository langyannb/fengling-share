package com.fengling.share.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.ui.components.GlassSpacing
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 群组 / 私聊列表顶部的一条统计 (v1.1.23)。
 *
 * 原来这里是一行灰字「点击群组进入聊天 · 群公告与消息实时同步 · 未读 12 条」——
 * 又长又没重点, 未读数和说明文字混在一起。改成分两段:
 * 左边是说清「这是多少个群」, 右边一颗胶囊只在真有未读时出现。
 */
@Composable
fun ListStatStrip(
    count: Int,
    unit: String,
    unread: Int,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = GlassSpacing.page + 2.dp,
                end = GlassSpacing.page + 2.dp,
                top = 12.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$count 个$unit",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
        if (unread > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFE5484D).copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (unread > 99) "99+ 条未读" else "$unread 条未读",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE5484D),
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "全部已读",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
            )
        }
    }
}
