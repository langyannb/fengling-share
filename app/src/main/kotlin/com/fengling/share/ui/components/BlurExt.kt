package com.fengling.share.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 创建一个「从下层内容取景」的 backdrop, 供顶栏 / 悬浮底栏做真实模糊。
 *
 * 返回 null 的两种情况 (调用方据此降级成半透明纯色, 不要硬画模糊):
 * 1. 用户在主题设置里关掉了模糊;
 * 2. 设备不支持 RenderEffect (Android 12 以下)。
 *
 * 性能红线: 这个 backdrop 会持续把内容录进 GraphicsLayer, 只允许用在**固定层**
 * (顶栏 / 底栏 / 分段控件), 绝不能挂在列表卡片上 —— 否则滑动时每帧重录, 掉帧。
 *
 * 用法:
 * ```
 * val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
 * Box(Modifier.layerBackdrop(backdrop!!)) { 页面内容 }   // 录制
 * BlurredBar(backdrop) { TopAppBar(...) }               // 取景
 * ```
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        // 先铺一层不透明 surface, 防止下层是纯透明时模糊出来一片黑
        drawRect(surface)
        drawContent()
    }
}

/**
 * 顶栏的模糊容器: 把 [backdrop] 录到的内容模糊后垫在 [content] 下面。
 *
 * @param backdrop 由 [rememberBlurBackdrop] 创建; 为 null 时只画 [content], 不做模糊
 * @param blurActive 是否真的启用模糊 (预览等场景可以传 false 只看静态效果)
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    shape: Shape = RectangleShape,
    blurRadius: Float = 25f,
    tintAlpha: Float = 0.87f,
    content: @Composable () -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier
                .fillMaxWidth()
                .textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = blurRadius,
                    colors = BlurColors(
                        blendColors = listOf(BlendColorEntry(color = surface.copy(alpha = tintAlpha))),
                    ),
                )
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        content()
    }
}
