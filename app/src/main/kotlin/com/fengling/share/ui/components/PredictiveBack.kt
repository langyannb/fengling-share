package com.fengling.share.ui.components

import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

private const val TAG = "FLS_BACK"

/**
 * 可预测式返回 (predictive back) 进度 (v1.1.7)
 *
 * 用 androidx.activity.compose.PredictiveBackHandler 拿系统手势进度:
 *  - 手势进行中: 进度 0->1 跟手 (回调里持续喂 progress), 同时写进 [BackReveal.progress],
 *    让 NavHost 把「上一级画面」铺在下面 —— ColorOS 16 的「从哪来回哪去」靠这一步。
 *  - 松手 < 50%: 系统判定取消 -> catch 分支 spring 回弹到 0, 上一级画面同步退回。
 *  - 松手 >= 50%: 补到 1 再 onBack(), 由导航转场收尾。
 *
 * 兼容性: Android 13 及未开「可预测式返回」的设备上系统不回传进度, 回调立刻正常结束 ——
 * 直接 onBack(), 功能与旧式 BackHandler 完全一致, 不会崩。
 *
 * 用法不变:
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
        var started = false
        try {
            events.collect { event ->
                if (!started) {
                    started = true
                    Log.d(TAG, "可预测式返回: 开始跟手")
                }
                val p = event.progress.coerceIn(0f, 1f)
                progress.snapTo(p)
                BackReveal.progress = p
            }
        } catch (e: CancellationException) {
            // 手势取消 (松手未过半): 跟手回弹, 上一级画面同步退回
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            BackReveal.progress = 0f
            throw e
        }
        // 手势提交: 补到 1 再真正返回
        progress.snapTo(1f)
        BackReveal.progress = 1f
        Log.d(TAG, "可预测式返回: 提交")
        onBack()
        // 页面没被销毁 (例如 WebView 内部历史回退) -> 复位, 不留偏移
        progress.snapTo(0f)
        BackReveal.progress = 0f
    }
    return remember(progress) { derivedStateOf { progress.value } }
}

/**
 * 跟手变换: 页面 1:1 跟着手指向右走 + 轻微缩小 + 圆角 (与系统可预测式返回同款观感)。
 * 直接读 State, 在绘制阶段生效 —— 全程不触发重组, 只脏图层。
 *
 * 内嵌模式 (页面下面没有横向的上一级) 传 slideFraction = 0f 即可退化成「原地缩小淡出」。
 */
fun Modifier.predictiveBackTransform(
    progress: State<Float>,
    slideFraction: Float = 1f,
    scaleDown: Float = 0.05f,
    fadeOut: Float = 0.10f,
    corner: Dp = 26.dp,
): Modifier = this.graphicsLayer {
    val p = progress.value.coerceIn(0f, 1f)
    if (p <= 0f) {
        translationX = 0f
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        clip = false
    } else {
        transformOrigin = TransformOrigin(0.5f, 0.5f)
        translationX = p * size.width * slideFraction
        scaleX = 1f - scaleDown * p
        scaleY = 1f - scaleDown * p
        alpha = 1f - fadeOut * p
        // 跟手途中圆角才明显: 完全返回时不留圆角, 免得和系统转场打架
        clip = true
        shape = RoundedCornerShape(corner * p)
    }
}
