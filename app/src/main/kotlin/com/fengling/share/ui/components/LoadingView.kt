package com.fengling.share.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * LoadingView - 现代化加载动画 (三圆点弹跳, 参考懒人工具 loading-224 风格)
 * 三个圆点依次上下弹跳 + 可选加载文字, 替代单调的"加载中..."文字
 */
@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    text: String? = null,
    dotColor: Color = MiuixTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "loadingDots")
    // 三个圆点相位差 0.16s 依次弹跳
    val scales = listOf(0f, 0.16f, 0.32f).map { delay ->
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, delayMillis = (delay * 1000).toInt()),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot$delay",
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scales.forEach { scale ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(scale.value)
                        .background(dotColor, CircleShape),
                )
            }
        }
        if (text != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/**
 * LoadingBox - 全屏加载占位 (居中 LoadingView), 替代 "加载中..." Text
 */
@Composable
fun LoadingBox(
    modifier: Modifier = Modifier,
    text: String? = "加载中",
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LoadingView(text = text)
    }
}
