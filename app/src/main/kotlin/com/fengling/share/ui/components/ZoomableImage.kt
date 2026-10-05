package com.fengling.share.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * v1.1.12 大图查看器 (群聊 / 私聊共用)
 *
 * 手势:
 *  - 双指捏合 1x ~ 5x
 *  - 双击: 1x -> 2.5x (以点击点为放大中心) / 放大状态 -> 1x
 *  - 放大后单指拖动平移, 拖出边界有阻尼 (橡皮筋), 松手 spring 回弹到边界
 *  - 1x 时单指拖动不做任何事 (这样上层如果以后放左右切图的 pager, 不会被吃掉手势)
 *
 * 用 Animatable 而不是 raw state: 回弹和双击缩放都要动画, 自己算插值不划算。
 */
@Composable
fun ZoomableImage(
    url: String,
    modifier: Modifier = Modifier,
    maxScale: Float = 5f,
    doubleTapScale: Float = 2.5f,
    /** 1x 状态下点一下图片 (放大后不触发, 避免看细节时误关) */
    onTapWhenNormal: (() -> Unit)? = null,
    contentDescription: String = "查看大图",
) {
    val scope = rememberCoroutineScope()
    val scale = remember(url) { Animatable(1f) }
    val offset = remember(url) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var boxSize by remember(url) { mutableStateOf(Size.Zero) }

    // 放大后能平移的距离: 图片按 Fit 铺在容器里, 用容器尺寸当上限 (简单且不会拖飞)
    fun maxOffset(forScale: Float): Offset {
        val mx = (boxSize.width * (forScale - 1f) / 2f).coerceAtLeast(0f)
        val my = (boxSize.height * (forScale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(mx, my)
    }

    // 越界之后按 35% 跟随 (橡皮筋), 松手再 spring 回来
    fun rubber(value: Float, max: Float): Float = when {
        max <= 0f -> 0f
        value > max -> max + (value - max) * 0.35f
        value < -max -> -max + (value + max) * 0.35f
        else -> value
    }

    fun clamp(value: Offset, forScale: Float): Offset {
        val m = maxOffset(forScale)
        return Offset(
            value.x.coerceIn(-m.x, m.x),
            value.y.coerceIn(-m.y, m.y),
        )
    }

    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { boxSize = it.toSize() }
            // 双击: 先注册, 单指拖动的手势里不消费纯点击, 两者不冲突
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        scope.launch {
                            if (scale.value > 1.01f) {
                                scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                                offset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow))
                            } else {
                                val target = doubleTapScale.coerceAtMost(maxScale)
                                val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                val focus = pos - center
                                val want = clamp(-focus * (target - 1f), target)
                                scale.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                                offset.animateTo(want, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    // 放大后点图片不关闭 (用户是在看细节)
                    onTap = { if (scale.value <= 1.01f) onTapWhenNormal?.invoke() },
                )
            }
            // 捏合 + 放大后拖动
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoomed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (zoomChange != 1f) {
                            val old = scale.value
                            val next = (old * zoomChange).coerceIn(1f, maxScale)
                            // 以双指中心为基准缩放, 手感才对得上手指
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val focus = centroid - center
                            scale.snapTo(next)
                            val moved = offset.value + panChange - focus * (next / old - 1f)
                            val m = maxOffset(next)
                            offset.snapTo(Offset(rubber(moved.x, m.x), rubber(moved.y, m.y)))
                            zoomed = true
                        } else if (panChange != Offset.Zero && scale.value > 1f) {
                            val moved = offset.value + panChange
                            val m = maxOffset(scale.value)
                            offset.snapTo(Offset(rubber(moved.x, m.x), rubber(moved.y, m.y)))
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                    // 松手: 越界的 spring 回来; 缩到 1x 以下时也就位
                    if (zoomed || scale.value > 1f) {
                        val target = clamp(offset.value, scale.value)
                        if (target != offset.value) {
                            offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offset.value.x
                translationY = offset.value.y
            },
    )
}
