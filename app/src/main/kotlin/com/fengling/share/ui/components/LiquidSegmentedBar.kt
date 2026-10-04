package com.fengling.share.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液体玻璃分段控件 (v1.1.5): 「群组 / 私聊」等同款
 *
 * 观感
 *  - 底板: backdrop 毛玻璃 + vibrancy + 细高光描边 (Highlight) + 内阴影 (InnerShadow)
 *  - 滑块: 过冲 spring (dampingRatio 0.55 / stiffness 400) 平移, 位移途中按「速度」横向拉伸、
 *          纵向轻微压扁, 停下自然回弹 —— 这就是液体感
 *  - 文字: animateColorAsState 补间 + 选中加粗/微放大
 *
 * 性能: 只在**固定层**用 backdrop 模糊 (分段控件不随列表滚动), 不在 LazyColumn item 上模糊。
 * 退化: backdrop == null (拿不到捕获层) 时退化成半透明白底 + 描边, 不崩、观感一致。
 */
@Composable
fun LiquidSegmentedBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 页面级捕获层 (拿不到就传 null, 自动退化) */
    backdrop: Backdrop? = null,
    /** 每个分段上的未读红点数量 (0 不显示), 长度不足按 0 处理 */
    unread: List<Int> = emptyList(),
    height: Dp = SegmentBarHeight,
) {
    if (tabs.isEmpty()) return

    val dark = isSystemInDarkTheme()
    val scheme = MiuixTheme.colorScheme
    val trackShape = RoundedCornerShape(percent = 50)
    val thumbShape = RoundedCornerShape(percent = 50)
    val trackFill = glassFill(dark)
    val trackStroke = glassStroke(dark)
    val thumbFill = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.82f)
    val selText = scheme.primary
    val normText = scheme.onBackgroundVariant

    // 过冲 spring: 0.55 阻尼 → 到位置后轻微过冲再回弹
    val thumb = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected, tabs.size) {
        thumb.animateTo(
            targetValue = selected.toFloat(),
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
        val density = LocalDensity.current
        val pad = 3.dp
        val padPx = with(density) { pad.toPx() }
        val usablePx = (constraints.maxWidth.toFloat() - padPx * 2f).coerceAtLeast(1f)
        val stepPx = usablePx / tabs.size

        val track = if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { trackShape },
                highlight = { Highlight.Default.copy(alpha = 0.60f) },
                shadow = { null },
                innerShadow = { InnerShadow(radius = 12f.dp, alpha = 0.35f) },
                effects = {
                    vibrancy()
                    blur(16f.dp.toPx())
                },
                onDrawSurface = { drawRect(trackFill) },
            )
        } else {
            Modifier
                .background(trackFill, trackShape)
                .border(1.dp, trackStroke, trackShape)
        }

        val thumbGlass = if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { thumbShape },
                highlight = { Highlight.Default.copy(alpha = 0.90f) },
                shadow = { Shadow(radius = 10f.dp, alpha = 0.22f) },
                innerShadow = { InnerShadow(radius = 6f.dp, alpha = 0.30f) },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                },
                onDrawSurface = { drawRect(thumbFill) },
            )
        } else {
            Modifier
                .background(thumbFill, thumbShape)
                .border(1.dp, trackStroke, thumbShape)
        }

        Box(Modifier.fillMaxSize().then(track)) {
            // 选中滑块: 先画, 文字盖在上面
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = pad)
                    .width(with(density) { stepPx.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = pad)
                    .graphicsLayer {
                        translationX = thumb.value * stepPx
                        // 速度驱动的横向拉伸 + 纵向压扁 (液体)
                        val stretch = (abs(thumb.velocity) / 60f).coerceIn(0f, 0.22f)
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.35f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .then(thumbGlass),
            )

            Row(Modifier.fillMaxSize().padding(pad)) {
                tabs.forEachIndexed { index, label ->
                    val active = index == selected
                    val count = unread.getOrElse(index) { 0 }
                    val p by animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "segProgress",
                    )
                    val color by animateColorAsState(
                        targetValue = if (active) selText else normText,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "segColor",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(thumbShape)
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight((500f + 200f * p).roundToInt()),
                                color = color,
                                modifier = Modifier.graphicsLayer {
                                    val s = 1f + 0.045f * p
                                    scaleX = s
                                    scaleY = s
                                },
                            )
                            if (count > 0) {
                                Spacer(Modifier.width(4.dp))
                                SegmentUnreadBadge(count = count)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分段控件上的未读小红点 (>99 显示 99+) */
@Composable
private fun SegmentUnreadBadge(count: Int) {
    val label = if (count > 99) "99+" else count.toString()
    Box(
        modifier = Modifier
            .heightIn(min = 16.dp)
            .widthIn(min = 16.dp)
            .clip(CircleShape)
            .background(Color(0xFFE5484D))
            .padding(horizontal = if (label.length > 1) 4.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
