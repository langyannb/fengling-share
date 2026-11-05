package com.fengling.share.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液态玻璃宿主: 创建 backdrop 并捕获背景内容
 * 用法:
 *   val (backdrop, captureModifier) = rememberGlassBackdrop()
 *   Box(Modifier.glassCapture(captureModifier)) { 背景内容 }
 *   GlassCard(backdrop = backdrop) { 玻璃卡片 }
 */

/**
 * 创建液态玻璃 backdrop (miuix-blur, 页面级, 调用一次)
 *
 * v1.1.19: 全项目只剩这一套玻璃管线 (miuix-blur 的 `Backdrop`/`LayerBackdrop`)。
 * 以前并存的 kyant/backdrop 那份 (rememberGlassBackdrop2) 已删 —— 两套库的 Backdrop
 * 类型不兼容, 同一个组件只认其中一种, 混着用就是「看着都是玻璃却不是一套 UI」。
 *
 * @param enabled false 时不挂捕获层 (用户关掉模糊/设备不支持), backdrop 仍然返回, 但采到的是空内容
 * @param surfaceColor 捕获时先铺的不透明底色 —— 不铺的话纯透明区域模糊出来是黑的
 */
@Composable
fun rememberGlassBackdrop(
    enabled: Boolean = true,
    surfaceColor: Color = MiuixTheme.colorScheme.surface,
): Pair<LayerBackdrop, Modifier> {
    val graphicsLayer = rememberGraphicsLayer()
    val state = rememberUpdatedState(surfaceColor)
    val backdrop = rememberLayerBackdrop(graphicsLayer) {
        drawRect(state.value)
        drawContent()
    }
    val captureModifier = if (enabled) Modifier.layerBackdrop(backdrop) else Modifier
    return backdrop to captureModifier
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
