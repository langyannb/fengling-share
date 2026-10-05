package com.fengling.share.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as kyantRememberLayerBackdrop

/**
 * 液态玻璃宿主: 创建 backdrop 并捕获背景内容
 * 用法:
 *   val (backdrop, captureModifier) = rememberGlassBackdrop()
 *   Box(Modifier.glassCapture(captureModifier)) { 背景内容 }
 *   GlassCard(backdrop = backdrop) { 玻璃卡片 }
 */

/** 创建液态玻璃 backdrop (页面级, 调用一次) */
@Composable
fun rememberGlassBackdrop(): Pair<Backdrop, Modifier> {
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = rememberLayerBackdrop(graphicsLayer) { }
    val captureModifier = Modifier.layerBackdrop(backdrop)
    return backdrop to captureModifier
}

/** 创建 kyant/backdrop 液态玻璃 (OShin 同款: lens/vibrancy/highlight 全效果) */
@Composable
fun rememberGlassBackdrop2(): Pair<com.kyant.backdrop.Backdrop, Modifier> {
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = kyantRememberLayerBackdrop(graphicsLayer)
    val captureModifier = Modifier.kyantLayerBackdrop(backdrop)
    // 采样用的那份套上「跟手期间不折射」, 捕获用的仍是原始 backdrop (v1.1.10)
    return remember(backdrop) { backdrop.mutedDuringReveal() } to captureModifier
}

/**
 * GlassCard - 液态玻璃卡片 (Miuix blur)
 * 纹理模糊 + Bloom 高光描边 (液态玻璃效果)
 * 需在 [rememberGlassBackdrop] 的捕获层之上使用
 */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 14.dp,
    blurRadius: Float = 30f,
    highlight: Highlight = Highlight.Companion.GlassStrokeMiddleLight,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)

    val glassModifier = modifier
        .textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadiusX = blurRadius,
            blurRadiusY = blurRadius,
            colors = BlurDefaults.blurColors(),
            highlight = highlight,
        )

    if (onClick != null) {
        Box(
            modifier = glassModifier.clickable(onClick = onClick),
        ) {
            Column(content = content)
        }
    } else {
        Box(modifier = glassModifier) {
            Column(content = content)
        }
    }
}
