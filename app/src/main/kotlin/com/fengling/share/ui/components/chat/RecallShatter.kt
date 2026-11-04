package com.fengling.share.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** 撤回破碎动画时长 (ms)。太短看不出碎, 太长那条消息会一直卡在半空。 */
const val RECALL_SHATTER_MS = 520

/**
 * 撤回时的「破碎」动画 (v1.1.20)
 *
 * 做法: 把气泡当前这一帧录进 GraphicsLayer, 再按 4x4 网格切块, 每块按自己的方向
 * 往外飞 + 轻微旋转, 整块同时淡出 —— 内容是真的碎开, 不是整块淡掉。
 *
 * 为什么不逐字碎: 消息可能是图片或视频封面, 统一按像素网格切才能保证「无论气泡里
 * 装的是什么, 碎法都一样自然」, 也省掉逐字布局的开销。
 *
 * 性能: 只在 [playing] 的这 [RECALL_SHATTER_MS] 毫秒里录制并画 16 块; playing = false
 * 时直接 drawContent, 零额外开销。
 *
 * 红线: 别拿它包 AndroidView (WebView / ExoPlayer 这类独立硬件 surface) —— 录进
 * GraphicsLayer 重放会把 surface 内容丢掉 (v1.1.19 内置浏览器白闪就是这么来的)。
 * 这里只包纯 Compose 画出来的气泡, 安全。
 */
@Composable
fun RecallShatter(
    playing: Boolean,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!playing) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
        return
    }

    val layer = rememberGraphicsLayer()
    val progress = remember { Animatable(0f) }

    // 碎块方向只摇一次 —— 每帧重摇会让碎块原地抖, 而不是飞出去
    val fragments = remember {
        val rnd = Random(System.nanoTime())
        List(COLS * ROWS) {
            Fragment(
                // 横向飞得比纵向远: 撕开的感觉主要在左右
                dx = (rnd.nextFloat() * 2f - 1f) * (0.6f + rnd.nextFloat() * 0.8f),
                dy = (rnd.nextFloat() * 2f - 1f) * (0.35f + rnd.nextFloat() * 0.6f),
                spin = (rnd.nextFloat() * 2f - 1f) * 24f,
                // 每块晚一点点起步, 看着是依次崩开而不是整体平移
                delay = rnd.nextFloat() * 0.18f,
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(RECALL_SHATTER_MS, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }

    val p = progress.value

    Box(
        modifier = modifier
            // 整体淡出放在图层上: 一次就够, 不用给 16 块各开一次离屏层
            .graphicsLayer { alpha = 1f - p }
            .drawWithContent {
                // 只有这一帧的内容会被录制 + 切块重放, 原图不再直接画
                layer.record { this@drawWithContent.drawContent() }

                val cellW = size.width / COLS
                val cellH = size.height / ROWS
                val flyX = size.width * 1.05f
                val flyY = size.height * 0.85f

                for (row in 0 until ROWS) {
                    for (col in 0 until COLS) {
                        val f = fragments[row * COLS + col]
                        val local = ((p - f.delay) / (1f - f.delay)).coerceIn(0f, 1f)
                        val left = col * cellW
                        val top = row * cellH

                        translate(
                            left = f.dx * local * flyX,
                            // 加一点重力: 后半段额外往下掉, 像被撕下来掉出去
                            top = f.dy * local * flyY + 70.dp.toPx() * local * local,
                        ) {
                            rotate(
                                degrees = f.spin * local,
                                pivot = Offset(left + cellW / 2f, top + cellH / 2f),
                            ) {
                                // clipRect 只让这一块露出来 —— 整层只画一次, 不做 16 次录制
                                clipRect(
                                    left = left,
                                    top = top,
                                    right = left + cellW,
                                    bottom = top + cellH,
                                ) {
                                    drawLayer(layer)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val COLS = 4
private const val ROWS = 4

private class Fragment(
    val dx: Float,
    val dy: Float,
    val spin: Float,
    val delay: Float,
)
