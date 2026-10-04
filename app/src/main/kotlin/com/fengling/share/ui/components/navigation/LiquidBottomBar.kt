package com.fengling.share.ui.components.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow


import com.fengling.share.utils.DampedDragAnimation
import com.fengling.share.utils.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import kotlin.math.abs
import kotlin.math.sign

/**
 * 为了让图标和文字也能缩放，需要定义这个 CompositionLocal
 * 这是原版示例 `LiquidBottomTabs` 的一部分
 *
 * --- MIGRATION: 变为 () -> Float ---
 */
val LocalLiquidBottomTabScale = compositionLocalOf { { 1f } }

@Composable
fun LiquidBottomBar(
    tabs: List<Pair<String, ImageVector>>,
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f)
    val iconColorFilter = ColorFilter.tint(contentColor)
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier
            .padding(bottom = 16.dp) // 保留原有 padding
            .padding(horizontal = 40.dp) // 保留原有 padding
            .layout { measurable, constraints -> // --- MIGRATION: 使用 layout 约束高度
                val placeable = measurable.measure(constraints.copy(maxHeight = 64f.dp.roundToPx()))
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabs.size
        }

        val animationScope = rememberCoroutineScope()

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth.toFloat()).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        // --- MIGRATION: 添加 isLtr 支持 ---
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = pagerState.currentPage.toFloat(),
                valueRange = 0f..(tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        // --- MIGRATION: 使用 fastRoundToInt ---
                        val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabs.size - 1)
                        if (pagerState.currentPage != targetIndex) {
                            onTabSelected(targetIndex)
                        }
                        didDrag = false
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    // --- MIGRATION: 添加 isLtr 支持 ---
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(
                            0f,
                            (tabs.size - 1).toFloat()
                        )
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        LaunchedEffect(dampedDragAnimation, pagerState) {
            snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
                .collectLatest { position ->
                    if (dampedDragAnimation.targetValue != position) {
                        dampedDragAnimation.animateToValue(position)
                    }
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    // --- MIGRATION: 添加 isLtr 支持 ---
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        val content: @Composable RowScope.() -> Unit = {
            tabs.forEachIndexed { index, screen ->
                val scale = lerp(1f, 1.05f, dampedDragAnimation.pressProgress)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { onTabSelected(index) }
                        .fillMaxHeight()
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- MIGRATION: 调用 lambda 获取 scale ---
                    val contentScale = LocalLiquidBottomTabScale.current()
                    Image(
                        imageVector = screen.second,
                        contentDescription = screen.first,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = contentScale
                                scaleY = contentScale
                            },
                        colorFilter = iconColorFilter
                    )
                    BasicText(
                        screen.first,
                        style = TextStyle(color = contentColor, fontSize = 12.sp),
                        modifier = Modifier.graphicsLayer {
                            scaleX = contentScale
                            scaleY = contentScale
                        }
                    )
                }
            }
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    // 对齐 AndroidLiquidGlass: 静止态也保留一圈**柔和棱边高光** (不是以前那种刺眼白边)
                    // + 轻微内阴影, 让玻璃有厚度; 按下时高光/内阴影一起变强。
                    // 外投影仍然关掉 (用户之前明确不要底部投影)。
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = 0.20f + 0.55f * progress)
                    },
                    shadow = { null },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 14f.dp, alpha = 0.10f + 0.26f * progress)
                    },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        // 常驻折射: 玻璃边缘始终在掰弯背后的内容 (液体玻璃核心观感)
                        lens(28f.dp.toPx(), 30f.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / this.size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            // --- MIGRATION: provides 变为一个 lambda ---
            LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
            content = {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(28.dp) },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = 0.12f + 0.88f * progress)
                            },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(8f.dp.toPx())
                                // 静止态保留 35% 折射 → 选中胶囊里始终有被放大的标签 (放大镜常亮)
                                val strength = 0.35f + 0.65f * progress
                                lens(26f.dp.toPx() * strength, 26f.dp.toPx() * strength)
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(56f.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4f.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        )

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    // --- MIGRATION: 添加 isLtr 支持 ---
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { RoundedCornerShape(28.dp) },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = 0.30f + 0.70f * progress)
                    },
                    // 胶囊保留一点很淡的投影做层次 (alpha 低, 不会变成脏边)
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(radius = 16f.dp, alpha = 0.08f + 0.20f * progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 10f.dp, alpha = 0.12f + 0.28f * progress)
                    },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // 静止态 45% 折射 + 色散 → 选中胶囊像一颗液态玻璃珠
                        val strength = 0.45f + 0.55f * progress
                        lens(
                            12f.dp.toPx() * strength,
                            18f.dp.toPx() * strength,
                            chromaticAberration = true
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        // --- MIGRATION: scaleY 逻辑更新 ---
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabs.size)
        )
    }
}
