package com.fengling.share.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 警示红 —— 管理员「用户标签」(防骗) 与禁言状态统一用这个色,
 * 和 AccountScreen 的 DangerRed / UserProfileScreen 的 RoleTag 是同一个色值 (0xFFE5484D)。
 */
val WarnRed = Color(0xFFE5484D)

/**
 * 管理员给用户打的标签 (防骗警示): 红色系小圆角 chip。
 *
 * 契约 F1: 标签出现在 user_public / user_brief / user_profile / social_messages /
 * social_group_members / pm_messages.other / pm_conversations[].user 里, 都是字符串数组。
 *
 * @param max  最多显示几个, 多出来的折成一个 "+N"
 * @param small true = 群聊气泡昵称旁用的超小号 (9sp / 4dp 圆角)
 */
@Composable
fun TagChips(
    tags: List<String>,
    modifier: Modifier = Modifier,
    max: Int = Int.MAX_VALUE,
    small: Boolean = false,
) {
    val list = tags.filter { it.isNotBlank() }
    if (list.isEmpty()) return
    val shown = if (list.size > max) list.take(max) else list
    val more = list.size - shown.size
    Row(modifier = modifier) {
        shown.forEach { tag ->
            TagChip(text = tag, small = small)
            Spacer(Modifier.width(if (small) 3.dp else 6.dp))
        }
        if (more > 0) TagChip(text = "+" + more, small = small)
    }
}

/** 单个标签 chip (红底红字半透明) */
@Composable
private fun TagChip(text: String, small: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(if (small) 4.dp else 8.dp))
            .background(WarnRed.copy(alpha = 0.14f))
            .padding(
                horizontal = if (small) 4.dp else 8.dp,
                vertical = if (small) 1.dp else 3.dp,
            ),
    ) {
        Text(
            text = text,
            fontSize = if (small) 9.sp else 11.sp,
            fontWeight = if (small) FontWeight.Normal else FontWeight.Medium,
            color = WarnRed,
            maxLines = 1,
        )
    }
}

/**
 * 禁言时长选项: 文案 → 分钟数 (0 = 永久)。
 * 群聊「成员操作」面板与用户主页禁言区共用同一份, 保证两处档位一致
 * (契约 F3: minutes 上限 432000, 0 = 永久)。
 */
val MUTE_OPTIONS = listOf(
    "10 分钟" to 10,
    "1 小时" to 60,
    "1 天" to 1440,
    "7 天" to 10080,
    "30 天" to 43200,
    "永久" to 0,
)

/**
 * 禁言时长选择器 (每行 3 个 chip), selected 传当前选中的分钟数。
 */
@Composable
fun MuteOptionPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        MUTE_OPTIONS.chunked(3).forEach { rowItems ->
            Row {
                rowItems.forEach { item ->
                    val picked = selected == item.second
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (picked) {
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .clickable { onSelect(item.second) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = item.first,
                            fontSize = 12.sp,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (picked) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
