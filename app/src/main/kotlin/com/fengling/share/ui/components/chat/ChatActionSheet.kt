package com.fengling.share.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.glassSurface
import com.fengling.share.ui.theme.LocalAnimSheetSpring

/**
 * 「消息操作」菜单里的一项 (v1.1.20)
 *
 * 和原来的 [ChatActionRow] 的区别: 这个带图标、带 key, 由 [ChatActionSheet] 统一排版 ——
 * 群聊和私聊两个页面都改成描述这一份数据, 视觉与动画从此不会再各写一套。
 */
class ChatSheetAction(
    val key: String,
    val label: String,
    val icon: ImageVector,
    /** 删除性操作 (撤回 / 删除图片) 用红色, 和 Telegram 一致 */
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 底部弹出的「消息操作」菜单 (v1.1.20, 替掉原来的 material3 AlertDialog)
 *
 * 为什么换: AlertDialog 是系统弹窗 —— 圆角、阴影、进出场全是 M3 默认值, 和这个 App 的
 * 液体玻璃完全不是一套东西; 而且它是独立窗口, 采样不到页面级的 backdrop, 底下永远是
 * 一块死板的半透明遮罩。改成页面内浮层后:
 *  · 面板底走 [glassSurface], 和底栏 / 输入栏同一条 miuix-blur 管线, 能真的糊到后面的消息;
 *  · 进场用弹簧 (带轻微过冲), 退场用 180ms tween —— 弹出有「弹」的感觉, 收起又利落;
 *  · 每一行按 index 错开 40ms 淡入 + 右移, 眼睛会顺着往下读完, 不糊成一坨;
 *  · 遮罩自己做, 透明度跟着进度走, 不会在开头/结尾闪一下。
 *
 * 用法 (群聊 / 私聊一致):
 * ```
 * val acting = actionTarget
 * ChatActionSheet(
 *     visible = acting != null,
 *     summary = acting?.let { 摘要 } ?: "",
 *     actions = if (acting == null) emptyList() else listOf(ChatSheetAction(...)),
 *     onDismiss = { actionTarget = null },
 *     backdrop = msgBackdrop,
 * )
 * ```
 * 注意: [actions] 在 visible 变 false 之后仍然要能读到 —— 因为收起动画还要画 180ms。
 * 所以调用点用 `acting` 的最新非空值来构造, 或者干脆像上面那样用 `acting` 判空。
 *
 * @param backdrop 页面内局部捕获层; 传 null 时退化成半透明纯色面板 (不崩, 也没有模糊)
 */
@Composable
fun ChatActionSheet(
    visible: Boolean,
    summary: String,
    actions: List<ChatSheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    /** 标题下方那行小字 (通常是「谁: 说了什么」), 空串则不画 */
    title: String = "消息操作",
) {
    // 设置里关掉「弹层弹性」时: 只保留 120ms 的淡入淡出, 不再有弹簧过冲
    val springOn = LocalAnimSheetSpring.current

    // mounted: 收起动画播完才把节点摘掉, 否则最后 180ms 会「啪」地消失
    var mounted by remember { mutableStateOf(visible) }
    val progress = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(visible, springOn) {
        if (visible) {
            mounted = true
            if (springOn) {
                // 进场: 阻尼 0.78 + 刚度 620 —— 冲过头一点点再回弹, 是「弹出来」不是「滑出来」
                progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 620f))
            } else {
                progress.animateTo(1f, tween(durationMillis = 120))
            }
        } else if (mounted) {
            progress.animateTo(
                0f,
                tween(durationMillis = if (springOn) 180 else 90, easing = FastOutSlowInEasing),
            )
            mounted = false
        }
    }

    if (!mounted) return

    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = modifier.fillMaxSize()) {
        // ===== 遮罩: 只负责压暗 + 点空白关闭, 不接收其它手势 =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value * 0.30f }
                .background(Color.Black)
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    // 从屏幕外整体推上来; 用自身高度算位移, 面板多高都不用手工填数
                    translationY = (1f - progress.value) * size.height
                }
                .glassSurface(
                    // 面板在页面内, 可以安全采样消息区的局部捕获层 (不是页面级层, 不会自引用)
                    backdrop = backdrop,
                    shape = RoundedCornerShape(
                        topStart = GlassRadius.panel,
                        topEnd = GlassRadius.panel,
                    ),
                    // 菜单要读得清: 捕获层只框了消息区, 面板下沿会盖到输入栏 ——
                    // 那块区域在层里是空的, 底子太透就会把输入框文字透上来 (真机验收发现的)。
                    fill = MiuixTheme.colorScheme.surface.copy(
                        alpha = if (backdrop != null) 0.92f else 0.97f,
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            // 顶部小横条: 告诉手指「这是一张可以弹走的菜单」
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .background(
                        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.28f),
                        RoundedCornerShape(2.dp),
                    ),
            )

            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 2.dp),
            )

            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }

            actions.forEachIndexed { index, action ->
                // 错开入场: 每行比上一行晚 6% 进度, 眼睛能一行一行读下去
                val rowProgress = ((progress.value - index * 0.06f) / 0.55f).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = rowProgress
                            translationX = (1f - rowProgress) * 18.dp.toPx()
                        }
                        .clickable { action.onClick() }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = if (action.danger) Color(0xFFE5484D)
                        else MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = action.label,
                        fontSize = 15.sp,
                        color = if (action.danger) Color(0xFFE5484D)
                        else MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp),
                    )
                }
            }

            // 底部「取消」: 和上面几行视觉隔开, 顺手把遮罩那 30% 的黑也抵掉一点手感
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = progress.value }
                    .clickable { onDismiss() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * 长按菜单顶部那句摘要: 「昵称: 内容」, 图片 / 视频消息用占位词。
 * 群聊私聊原本各写了一份一模一样的逻辑, 提到这里共用。
 */
fun chatMenuSummary(nickname: String, content: String, isImage: Boolean, isVideo: Boolean): String {
    val body = when {
        isVideo -> "[视频]"
        isImage -> "[图片]"
        else -> content.ifBlank { "[消息]" }
    }
    val head = nickname.ifBlank { "群成员" }
    return head + ": " + body.replace('\n', ' ').take(60)
}
