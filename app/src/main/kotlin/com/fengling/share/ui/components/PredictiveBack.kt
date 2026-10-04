package com.fengling.share.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException

/**
 * 预测性返回 (predictive back) 进度 (v1.1.5)
 *
 * 用 androidx.activity.compose.PredictiveBackHandler 取代「抢返回但没动画」的旧式 BackHandler:
 *  - 手势进行中: 进度 0→1 跟手 (回调里持续喂 progress)
 *  - 松手 < 50%: 系统判定取消 → 这里走 catch 分支, spring 回弹到 0
 *  - 松手 ≥ 50%: 流程正常结束 → 先 snap 到 1 再执行 onBack()
 *
 * 兼容性: Android 13 及未开「预测性返回手势动画」的设备上, 系统不会回传手势进度,
 * 回调会立刻正常结束 —— 直接执行 onBack(), 功能与原来完全一致, 不会崩。
 *
 * 用法:
 *   val backProgress = rememberPredictiveBackProgress(enabled = true) { onBack() }
 *   Scaffold(modifier = Modifier.predictiveBackTransform(backProgress), ...) { ... }
 */
@Composable
fun rememberPredictiveBackProgress(
    enabled: Boolean = true,
    onBack: () -> Unit,
): State<Float> {
    val progress = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
        } catch (e: CancellationException) {
            // 手势取消 (松手未过半): 跟手回弹
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            throw e
        }
        // 手势提交: 补到 1 再真正返回
        progress.snapTo(1f)
        onBack()
        // 页面没被销毁 (例如 WebView 内部历史回退) → 复位, 不留偏移
        progress.snapTo(0f)
    }
    return remember(progress) { derivedStateOf { progress.value } }
}

/**
 * 跟手变换: 向右滑出 + 轻微缩小淡出 (缩放原点取中心)。
 * 直接读 State, 在绘制阶段生效 —— 全程不触发重组, 只脏图层。
 */
fun Modifier.predictiveBackTransform(
    progress: State<Float>,
    slideFraction: Float = 0.30f,
    scaleDown: Float = 0.08f,
    fadeOut: Float = 0.25f,
): Modifier = this.graphicsLayer {
    val p = progress.value.coerceIn(0f, 1f)
    if (p > 0f) {
        // 原点取中心: 内嵌模式 (slideFraction = 0) 只有缩放, 右边缘原点会显得偏心
        transformOrigin = TransformOrigin(0.5f, 0.5f)
        translationX = p * size.width * slideFraction
        scaleX = 1f - scaleDown * p
        scaleY = 1f - scaleDown * p
        alpha = 1f - fadeOut * p
    }
}
