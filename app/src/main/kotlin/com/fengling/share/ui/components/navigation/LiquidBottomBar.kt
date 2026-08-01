package com.fengling.share.ui.components.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * LiquidBottomBar - OShin 风格液态玻璃底栏
 * - drawBackdrop 液态玻璃 (vibrancy 活力 + blur 模糊)
 * - 按压: lens 放大镜 + 高光 + 内外阴影 + 玻璃放大 (液态动画)
 * - 选中项高亮 (主题色)
 */
@Composable
fun LiquidBottomBar(
    tabs: List<Pair<String, ImageVector>>,
    currentPage: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color(0xFF1A1A1A) else Color.White
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f) else Color(0xFF121212).copy(alpha = 0.4f)

    // 按压动画状态
    val pressProgress = remember { Animatable(0f) }
    var pressIndex by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxHeight = 72.dp.roundToPx()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabs.size }

        // 玻璃底栏主体
        Row(
            Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    effects = {
                        vibrancy()
                        blur(12f.dp.toPx())
                    },
                    layerBlock = {
                        val p = pressProgress.value
                        scaleX = 1f + 0.015f * p
                        scaleY = 1f + 0.015f * p
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                if (isPressed && pressIndex != index) {
                    pressIndex = index
                    scope.launch { pressProgress.animateTo(1f, spring(0.7f, 300f)) }
                } else if (!isPressed && pressIndex == index) {
                    pressIndex = -1
                    scope.launch { pressProgress.animateTo(0f, spring(0.7f, 300f)) }
                }

                val selected = currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) accentColor else contentColor.copy(alpha = 0.55f),
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = if (selected) accentColor else contentColor.copy(alpha = 0.55f),
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // 按压指示胶囊 (液态 lens + 高光 + 阴影)
        if (pressIndex in tabs.indices) {
            val p = pressProgress.value
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        translationX = pressIndex * tabWidth
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(24.dp) },
                        highlight = { Highlight.Default.copy(alpha = p) },
                        shadow = { Shadow(alpha = p * 0.5f) },
                        innerShadow = { InnerShadow(radius = 6.dp * p, alpha = p) },
                        effects = {
                            lens(
                                10f.dp.toPx() * p,
                                14f.dp.toPx() * p,
                                chromaticAberration = true,
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                if (isLightTheme) Color.Black.copy(0.06f) else Color.White.copy(0.06f),
                                alpha = 1f - p,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * p))
                        },
                    )
                    .height(56.dp)
                    .fillMaxWidth(1f / tabs.size),
            )
        }
    }
}
