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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * v1.1.12 大图查看器 (群聊 / 私聊共用)
 *
 * 手势:
 *  - 双指捏合 1x ~ 5x
 *  - 双击: 1x -> 2.5x (以点击点为放大中心) / 放大状态 -> 1x
 *  - 放大后单指拖动平移, 拖出边界有阻尼 (橡皮筋), 松手 spring 回弹到边界
 *  - 1x 时单指拖动不做任何事 (上层若以后放左右切图的 pager, 不会被吃掉手势)
 *
 * 实现注意: 指针作用域 (awaitEachGesture / awaitPointerEventScope) 是「受限挂起作用域」,
 * 里面只能调指针输入类挂起函数 —— 不能直接调 Animatable.animateTo/snapTo。
 * 所以手势里只做纯计算 + 赋值, 动画统一交给普通协程 (rememberCoroutineScope) 去跑。
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
    var scale by remember(url) { mutableStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var boxSize by remember(url) { mutableStateOf(Size.Zero) }
    // 动画作业: 新手势一来就取消旧动画, 免得和手指抢值
    var animJob by remember(url) { mutableStateOf<Job?>(null) }

    // 放大后能平移的距离: 用容器尺寸当上限 (简单且不会拖飞)
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

    // 双击 / 松手回弹的动画: 跑在普通协程里 (受限作用域里调不了 animateTo)
    fun springTo(targetScale: Float, targetOffset: Offset) {
        animJob?.cancel()
        val fromScale = scale
        val fromOffset = offset
        animJob = scope.launch {
            launch {
                val a = Animatable(fromScale)
                a.animateTo(
                    targetValue = targetScale,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) {
                    scale = a.value
                }
            }
            launch {
                val a = Animatable(fromOffset, Offset.VectorConverter)
                a.animateTo(
                    targetValue = targetOffset,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) {
                    offset = a.value
                }
            }
        }
    }

    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { boxSize = it.toSize() }
            // 双击: 单指拖动的手势里不消费纯点击, 两者不冲突
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 1.01f) {
                            springTo(1f, Offset.Zero)
                        } else {
                            val target = doubleTapScale.coerceAtMost(maxScale)
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val focus = pos - center
                            val want = clamp(-focus * (target - 1f), target)
                            springTo(target, want)
                        }
                    },
                    // 放大后点图片不关闭 (用户是在看细节)
                    onTap = { if (scale <= 1.01f) onTapWhenNormal?.invoke() },
                )
            }
            // 捏合 + 放大后拖动
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    animJob?.cancel()
                    var zoomed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (zoomChange != 1f) {
                            val old = scale
                            val next = (old * zoomChange).coerceIn(1f, maxScale)
                            // 以双指中心为基准缩放, 手感才对得上手指
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val focus = centroid - center
                            scale = next
                            val moved = offset + panChange - focus * (next / old - 1f)
                            val m = maxOffset(next)
                            offset = Offset(rubber(moved.x, m.x), rubber(moved.y, m.y))
                            zoomed = true
                        } else if (panChange != Offset.Zero && scale > 1f) {
                            val moved = offset + panChange
                            val m = maxOffset(scale)
                            offset = Offset(rubber(moved.x, m.x), rubber(moved.y, m.y))
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                    // 松手: 越界的 spring 回来; 已经缩回 1x 就归位
                    if (zoomed || scale > 1f) {
                        val target = clamp(offset, scale)
                        if (target != offset) springTo(scale, target)
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
