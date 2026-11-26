package com.fengling.share.ui.components.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.glassSurface
import com.fengling.share.ui.theme.LocalAnimSheetSpring
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 群公告编辑器 (v1.1.23, 替掉原来的 material3 AlertDialog)
 *
 * 为什么换: 编辑公告是个「要坐下来写点东西」的界面, AlertDialog 那点宽度里塞一个
 * 6 行的输入框, 键盘一弹就被顶到屏幕外, 而且圆角阴影全是 M3 默认值, 跟 App 的
 * 液体玻璃不是一套。换成底部弹出的玻璃面板:
 *  · 整块面板用 [glassSurface], 和长按菜单 / 输入栏同一条管线;
 *  · 输入区自己做 (BasicTextField + 圆角内胆), 深浅主题下颜色都能跟着主题走;
 *  · 加了 imePadding, 键盘弹出时面板整体抬起来, 不再被挡住;
 *  · 字数接近上限时计数变红, 保存按钮在保存中置灰, 不会连点两次。
 *
 * @param backdrop 页面内局部捕获层; 传 null 退化成半透明纯色面板 (不崩, 只是没模糊)
 */
@Composable
fun NoticeEditorSheet(
    visible: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    maxLength: Int = 500,
) {
    // 设置里关掉「弹层弹性」时只保留 120ms 淡入淡出
    val springOn = LocalAnimSheetSpring.current

    // 收起动画播完才摘节点, 否则最后 180ms 会「啪」地消失
    var mounted by remember { mutableStateOf(visible) }
    val progress = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(visible, springOn) {
        if (visible) {
            mounted = true
            if (springOn) {
                progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 560f))
            } else {
                progress.animateTo(1f, tween(120))
            }
        } else if (mounted) {
            progress.animateTo(0f, tween(if (springOn) 180 else 90, easing = FastOutSlowInEasing))
            mounted = false
        }
    }

    // 保存中不让物理返回键把面板关掉, 否则请求还在飞、界面已经没了
    BackHandler(enabled = mounted && !saving) { onDismiss() }

    if (!mounted) return

    val scrimInteraction = remember { MutableInteractionSource() }
    val over = value.length >= maxLength - 40

    Box(modifier = modifier.fillMaxSize()) {
        // ===== 遮罩 =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value * 0.32f }
                .background(Color.Black)
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = { if (!saving) onDismiss() },
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .imePadding()
                .glassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(
                        topStart = GlassRadius.panel,
                        topEnd = GlassRadius.panel,
                    ),
                    // 面板盖住的是输入栏那一带, 捕获层里那块是空的 —— 太透会透出输入框文字
                    fill = MiuixTheme.colorScheme.surface.copy(
                        alpha = if (backdrop != null) 0.92f else 0.97f,
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            // 顶部小横条
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .background(
                        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.28f),
                        RoundedCornerShape(2.dp),
                    ),
            )

            // ===== 标题行 =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                        .clickable(enabled = !saving) { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== 输入区 =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f))
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 13.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "写点群规、活动或者通知…",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // ===== 提示 + 字数 =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (value.isBlank()) "留空保存 = 清空公告" else "保存后所有成员进群都能看到",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${value.length}/$maxLength",
                    fontSize = 11.sp,
                    fontWeight = if (over) FontWeight.Medium else FontWeight.Normal,
                    color = if (over) Color(0xFFE5484D)
                    else MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }

            Spacer(Modifier.height(14.dp))

            // ===== 按钮行 =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                        .clickable(enabled = !saving) { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (saving) MiuixTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else MiuixTheme.colorScheme.primary,
                        )
                        .clickable(enabled = !saving, onClick = onSave),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (saving) "保存中…" else "保存",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}
