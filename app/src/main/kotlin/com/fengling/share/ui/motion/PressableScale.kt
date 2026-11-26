package com.fengling.share.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按压缩放：1 → 0.98。
 *
 * 调用方把同一个 [interactionSource] 传给 `clickable` / `combinedClickable`，
 * 否则这里收不到按压状态。只改 graphicsLayer，不触发重新布局。
 */
@Composable
fun Modifier.pressableScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = MotionTokens.SpringSnappy,
        label = "pressableScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
