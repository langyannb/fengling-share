// 液体玻璃分段控件 —— 与底部悬浮导航栏 (ui/components/navigation/FloatingBottomBar.kt)
// 走**同一条**渲染管线: miuix-blur 的 Backdrop + drawBackdrop (vibrancy / blur / lens),
// 镜面高光用同一份 GlassSpecularHighlight (双峰 BloomStroke, 跟着重力方向转)。

package com.fengling.share.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.ui.components.liquid.GlassSpecularHighlight
import com.fengling.share.ui.components.liquid.InnerShadow
import com.fengling.share.ui.components.liquid.innerShadow
import com.fengling.share.ui.components.liquid.lens
import com.fengling.share.ui.components.liquid.rememberCombinedBackdrop
import com.fengling.share.ui.components.liquid.rememberGravityRotatedHighlight
import com.fengling.share.ui.components.liquid.vibrancy
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.fengling.share.ui.theme.isAppInDarkTheme

/**
 * 液体玻璃分段控件 (v1.1.19 · 与悬浮底栏同源)
 *
 * 和底部悬浮胶囊 (`ui/components/navigation/FloatingBottomBar.kt`) 用**同一套**做法:
 *  - 底板: 页面捕获层毛玻璃 (vibrancy + blur(4dp) + lens(24dp)) + 棱上镜面高光 + 外阴影
 *  - 滑块: 采样「页面层 + 文字幽灵层」的合并 backdrop, 用 lens + 色散把底下的文字放大折射出来
 *          —— 液体玻璃真正的「放大镜」观感, 而不是一块半透明色块
 *  - 手感: 过冲 spring (dampingRatio 0.55 / stiffness 400) 平移; 位移途中按速度横向拉伸、纵向压扁;
 *          按下时高光 / 内阴影 / 折射强度一起抬起来再回落
 *  - 高光: 和底栏同一份 [GlassSpecularHighlight], 跟着设备倾斜滑过去 (容器 -45°, 滑块 +90°)
 *
 * 性能红线: 只在**固定层**用 backdrop (分段控件不随列表滚动), 绝不在 LazyColumn item 上逐个模糊。
 * 退化: backdrop == null (拿不到捕获层 / 用户关了模糊) 时退化成半透明纯色底 + 描边, 不崩、观感一致。
 *
 * ⚠️ backdrop 必须是「不包含本控件自身」的捕获层。把包含自己的层传进来会让 hwui 的 RenderNode 树
 *    无限递归, 真机直接 SIGSEGV (stack overflow) —— 真机踩过一次, 别再传页面级整层。
 */
@Composable
fun LiquidSegmentedBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 捕获层 (miuix-blur; 拿不到就传 null, 自动退化) */
    backdrop: Backdrop? = null,
    /** 每个分段上的未读红点数量 (0 不显示), 长度不足按 0 处理 */
    unread: List<Int> = emptyList(),
    height: Dp = SegmentBarHeight,
) {
    if (tabs.isEmpty()) return

    val dark = isAppInDarkTheme()
    val scheme = MiuixTheme.colorScheme
    val trackShape = RoundedCornerShape(percent = 50)
    val thumbShape = RoundedCornerShape(percent = 50)
    // 和底栏一个底色: 40% surfaceContainer + 真模糊。底栏关掉模糊时是纯色, 这里同理。
    val containerColor = if (backdrop != null) {
        scheme.surfaceContainer.copy(alpha = 0.4f)
    } else {
        scheme.surfaceContainer
    }
    val trackStroke = glassStroke(dark)
    // 选中滑块: 玻璃上的一层淡淡高光, 不遮住折射出来的内容
    val thumbFill = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
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

    // 按下反馈: 高光 / 内阴影 / 折射强度一起抬起来 (按下期间手指下的玻璃"被压亮")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "segPress",
    )

    // 镜面高光: 和底栏同一份参数, 只是朝向不同 (不转开两道光会叠成一块白斑)
    val trackHighlight = if (backdrop != null) {
        rememberGravityRotatedHighlight(GlassSpecularHighlight, extraDegrees = -45f)
    } else {
        GlassSpecularHighlight
    }
    val thumbHighlight = if (backdrop != null) {
        rememberGravityRotatedHighlight(GlassSpecularHighlight, extraDegrees = 90f)
    } else {
        GlassSpecularHighlight
    }

    // 「文字幽灵层」: 和底部导航栏同一个套路 —— 把标签文字录进一层 backdrop,
    // 选中滑块采样时用 lens + 色散放大折射出来 (液体玻璃的放大镜观感)。
    val labelsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
        val density = LocalDensity.current
        val pad = 3.dp
        val padPx = with(density) { pad.toPx() }
        val usablePx = (constraints.maxWidth.toFloat() - padPx * 2f).coerceAtLeast(1f)
        val stepPx = usablePx / tabs.size

        val track = if (backdrop != null) {
            Modifier
                // 和底栏一样的外投影: 玻璃是浮在内容上的一块, 没有影子就贴成一片
                .dropShadow(
                    shape = trackShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = if (dark) 0.20f else 0.10f,
                    ),
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { trackShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())
                    },
                    highlight = { trackHighlight.copy(alpha = 0.75f) },
                    onDrawSurface = { drawRect(containerColor) },
                )
        } else {
            Modifier
                .background(containerColor, trackShape)
                .border(1.dp, trackStroke, trackShape)
        }

        val thumbGlass = if (backdrop != null) {
            Modifier
                .dropShadow(
                    shape = thumbShape,
                    shadow = Shadow(
                        radius = 12.dp,
                        color = Color.Black,
                        alpha = 0.18f + 0.12f * press,
                    ),
                )
                .drawBackdrop(
                    // 文字幽灵层排第二 → 滑块里能看到被折射放大的标签文字
                    backdrop = rememberCombinedBackdrop(backdrop, labelsBackdrop),
                    shape = { thumbShape },
                    effects = {
                        vibrancy()
                        // 按下去折射更强 (玻璃被按出来的感觉)
                        val k = 0.7f + 0.5f * press
                        lens(
                            refractionHeight = 10.dp.toPx() * k,
                            refractionAmount = 14.dp.toPx() * k,
                            depthEffect = true,
                            chromaticAberration = 0.5f,
                        )
                    },
                    highlight = { thumbHighlight.copy(alpha = 0.35f + 0.55f * press) },
                    layerBlock = {
                        // 采样内容跟着速度拉伸: 位移越快, 折射拖影越明显
                        val stretch = (abs(thumb.velocity) / 90f).coerceIn(0f, 0.18f)
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.30f
                    },
                    onDrawSurface = { drawRect(thumbFill) },
                )
                .innerShadow(shape = thumbShape) {
                    InnerShadow(
                        radius = 8.dp * press,
                        color = Color.Black.copy(alpha = 0.15f),
                        alpha = press,
                    )
                }
        } else {
            Modifier
                .background(thumbFill, thumbShape)
                .border(1.dp, trackStroke, thumbShape)
        }

        Box(Modifier.fillMaxSize().then(track)) {
            // 1) 幽灵文字层: 只录进 labelsBackdrop 给滑块折射用; 不可见、不可点、不占无障碍节点
            if (backdrop != null) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .alpha(0f)
                        .clearAndSetSemantics {}
                        .layerBackdrop(labelsBackdrop),
                ) {
                    tabs.forEach { label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dark) Color.White else Color.Black,
                            )
                        }
                    }
                }
            }

            // 2) 选中滑块: 先画, 文字盖在上面
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
                        scaleX = 1f + stretch + 0.03f * press
                        scaleY = 1f - stretch * 0.35f - 0.02f * press
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .then(thumbGlass),
            )

            // 3) 真实文字层
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
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onSelect(index) },
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
