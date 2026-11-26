package com.fengling.share.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.fengling.share.ui.theme.LocalAnimListEnter
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 列表项逐个淡入上浮 (v1.1.23)。
 *
 * 列表第一次出现时, 卡片一张接一张浮上来, 比"啪"地一整屏同时出现要有质感得多。
 * 只给前 [maxOrdered] 项排延迟 —— 滚到后面才出现的卡片如果还带延迟, 看起来像卡了一下;
 * 而且列表滚动时每个新组合的项都会重新播一次, 延迟排在靠后的项上就是纯浪费。
 *
 * 动画跑完把 graphicsLayer 摘掉, 不给滚动帧留额外开销。
 *
 * @param index 该项在列表里的下标
 * @param stepMs 相邻两项的延迟
 * @param maxOrdered 只有下标小于它的项才排延迟
 */
@Composable
fun Modifier.enterUp(
    index: Int,
    stepMs: Int = 26,
    maxOrdered: Int = 12,
    durationMs: Int = 300,
    offsetDp: Int = 20,
): Modifier {
    // 设置里关掉「列表进场动画」时直接给出终态, 连 Animatable 都不建
    if (!LocalAnimListEnter.current) return this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        if (index in 0 until maxOrdered) {
            kotlinx.coroutines.delay(index.toLong() * stepMs)
        }
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    val p = progress.value
    return if (p >= 1f) {
        this
    } else {
        this.graphicsLayer {
            alpha = p
            translationY = (1f - p) * offsetDp.dp.toPx()
        }
    }
}
