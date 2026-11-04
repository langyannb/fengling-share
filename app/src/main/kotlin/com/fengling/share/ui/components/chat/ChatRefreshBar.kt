package com.fengling.share.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.fengling.share.ui.components.glassSurface

/**
 * 消息刷新时的浮层提示 (v1.1.20)
 *
 * 以前 `refreshing` 这个状态在两个聊天页里都是「置位、复位、没人看」—— 用户下拉或者
 * SSE 断线重连之后重新拉消息, 屏幕上一点变化都没有, 只能干等, 还以为卡住了。
 *
 * 这里给一个贴顶的小胶囊:
 *  · 从上方滑入 + 淡入, 收起时反向, 不打断正在看的位置;
 *  · 里面是自绘的旋转弧线 (Canvas, 不用 CircularProgressIndicator —— 那个是 material3 的
 *    4dp 粗圆环, 塞进 14dp 会糊成一坨);
 *  · 底走 [glassSurface], 和输入栏同一条模糊管线。
 *
 * 放在消息区 Box 里靠上即可, 不占布局高度 (只是 overlay), 不会把消息列表顶下去。
 */
@Composable
fun ChatRefreshBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    text: String = "正在刷新…",
    backdrop: Backdrop? = null,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it },
    ) {
        Row(
            modifier = Modifier
                .glassSurface(
                    backdrop = backdrop,
                    shape = CircleShape,
                    fill = MiuixTheme.colorScheme.surface.copy(
                        alpha = if (backdrop != null) 0.72f else 0.94f,
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpinnerDot()
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/** 自绘小转圈: 一段 240° 的弧无限旋转, 比 material3 的圆环细, 塞进小胶囊里不糊 */
@Composable
private fun SpinnerDot() {
    val transition = rememberInfiniteTransition(label = "chat_refresh_spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chat_refresh_angle",
    )
    val color = MiuixTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(13.dp)) {
        val stroke = 1.8.dp.toPx()
        val d = size.minDimension - stroke
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f),
                size = Size(d, d),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
