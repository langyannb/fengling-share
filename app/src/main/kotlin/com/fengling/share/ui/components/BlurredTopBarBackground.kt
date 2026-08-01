package com.fengling.share.ui.components

import android.graphics.RenderEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * BlurredTopBarBackground - OShin 同款顶栏模糊渐变
 * 捕获 backdrop 背景, 顶部 72dp 模糊 + 渐隐遮罩 (切页时可见模糊效果)
 */
@Composable
fun BlurredTopBarBackground(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val background = MiuixTheme.colorScheme.background

    Box(
        Modifier
            .height(72.dp)
            .fillMaxWidth()
            .statusBarsPadding()
            .then(modifier)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(4f.dp.toPx())
                    effect(
                        RenderEffect.createRuntimeShaderEffect(
                            obtainRuntimeShader(
                                "TopBarAlphaMask",
                                """
uniform shader content;
uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.2, coord.y);
    float tintAlpha = smoothstep(size.y, size.y * 0.2, coord.y);
    return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
}
""".trimIndent()
                            ).apply {
                                setFloatUniform("size", size.width, size.height)
                                setColorUniform("tint", background.value.toLong())
                                setFloatUniform("tintIntensity", 0.8f)
                            },
                            "content"
                        )
                    )
                }
            ),
    ) {}
}
