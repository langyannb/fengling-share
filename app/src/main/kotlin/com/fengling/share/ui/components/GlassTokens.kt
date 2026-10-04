package com.fengling.share.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

/**
 * 全局观感 token (v1.1.5 UI 精修)
 *
 * 目的: 圆角 / 间距 / 玻璃配色统一, 各页面不再各写一套魔法数字。
 * 性能红线: 这里只提供「半透明 + 描边」的静态玻璃卡片修饰符 (零模糊开销),
 *          真正的 backdrop 模糊只允许用在固定层 (顶栏 / 分段控件 / 输入栏)。
 */

/** 圆角 token */
object GlassRadius {
    /** 小标签 / chip */
    val chip: Dp = 8.dp
    /** 头像 / 图标底 */
    val icon: Dp = 14.dp
    /** 列表卡片 */
    val card: Dp = 18.dp
    /** 大面板 / 弹层 */
    val panel: Dp = 22.dp
}

/** 间距 token */
object GlassSpacing {
    /** 列表左右留白 */
    val page: Dp = 14.dp
    /** 卡片之间竖向间距 */
    val cardGap: Dp = 8.dp
    /** 卡片内部留白 */
    val cardInner: Dp = 14.dp
    /** 分区之间 */
    val section: Dp = 12.dp
}

/** AppTopBar 固有高度 (statusBarsPadding + 6 + 38 + 6), 不含状态栏高度 */
val AppTopBarContentHeight: Dp = 50.dp

/** 「群组 / 私聊」等分段控件高度 */
val SegmentBarHeight: Dp = 44.dp

/** 玻璃描边 (细高光) */
@Composable
fun glassStroke(dark: Boolean = isSystemInDarkTheme()): Color =
    if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.62f)

/** 玻璃底色 (固定层用) */
@Composable
fun glassFill(dark: Boolean = isSystemInDarkTheme()): Color =
    if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.46f)

/** 列表卡片玻璃底 (半透明, 不模糊) */
@Composable
fun cardGlassFill(dark: Boolean = isSystemInDarkTheme()): Color =
    if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.66f)

/** 列表卡片描边 */
@Composable
fun cardStroke(dark: Boolean = isSystemInDarkTheme()): Color =
    if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.55f)

/** 半透明玻璃卡片修饰符 (列表 item 专用: 只有底色 + 描边, 不逐项模糊) */
@Composable
fun Modifier.glassCard(
    radius: Dp = GlassRadius.card,
    fill: Color = cardGlassFill(),
    stroke: Color = cardStroke(),
    strokeWidth: Dp = 1.dp,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(fill)
        .border(strokeWidth, stroke, shape)
}

/**
 * 固定层玻璃修饰符 (顶栏 / 输入栏 / 分段控件这类不随列表滚动的层专用)
 *
 * backdrop 为 null (页面级捕获层拿不到, 例如走 NavHost 全屏路由的群聊页) 时,
 * 自动退化为「半透明底 + 细描边」, 不崩、观感一致。
 * 性能红线: 绝不把这个修饰符用在 LazyColumn 的 item 上。
 */
@Composable
fun Modifier.glassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    fill: Color = glassFill(),
    blurRadius: Dp = 20.dp,
    highlightAlpha: Float = 0.55f,
    innerShadowRadius: Dp = 10.dp,
): Modifier {
    val stroke = glassStroke()
    return if (backdrop != null) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { Highlight.Default.copy(alpha = highlightAlpha) },
            shadow = { null },
            innerShadow = { InnerShadow(radius = innerShadowRadius, alpha = 0.28f) },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
            },
            onDrawSurface = { drawRect(fill) },
        )
    } else {
        this
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
    }
}

/**
 * [AppGradientBackground] 的 Modifier 版本 (契约 C)
 *
 * 有些页面 Scaffold 的 content 根就是一个 Column —— 往里面插一个 fillMaxSize 的
 * 兄弟 Box 会把真实内容挤出屏幕, 所以这里做成 Modifier, 直接挂在那个 Column/Box 上。
 * 同样 drawBehind + 两个大半径径向渐变, 静态绘制, 尺寸不变就不重绘, 列表滚动不触发。
 */
@Composable
fun Modifier.appGradientBackground(glow: Color = MiuixTheme.colorScheme.primary): Modifier {
    val dark = isSystemInDarkTheme()
    val base = MiuixTheme.colorScheme.background
    val a1 = if (dark) 0.20f else 0.11f
    val a2 = if (dark) 0.14f else 0.07f
    val second = Color(0xFF4C6FFF)
    return this
        .background(base)
        .drawBehind {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = a1), Color.Transparent),
                    center = Offset(size.width * 0.10f, -size.height * 0.02f),
                    radius = size.width * 0.95f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(second.copy(alpha = a2), Color.Transparent),
                    center = Offset(size.width * 1.02f, size.height * 0.30f),
                    radius = size.width * 0.85f,
                ),
            )
        }
}

/**
 * 页面级柔和渐变背景 (静态, 不逐帧重绘)
 *
 * 作用: ①统一各页面底色观感; ②给顶栏 / 分段控件这些玻璃层提供可被模糊的层次,
 * 否则毛玻璃在一片纯色上模糊出来还是纯色, 看不出「玻璃」。
 * 用 drawBehind + 两个大半径径向渐变实现, 尺寸不变就不重绘, 列表滚动也不会触发。
 */
@Composable
fun AppGradientBackground(
    modifier: Modifier = Modifier,
    glow: Color = MiuixTheme.colorScheme.primary,
) {
    val dark = isSystemInDarkTheme()
    val base = MiuixTheme.colorScheme.background
    val a1 = if (dark) 0.20f else 0.11f
    val a2 = if (dark) 0.14f else 0.07f
    val second = Color(0xFF4C6FFF)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = a1), Color.Transparent),
                        center = Offset(size.width * 0.10f, -size.height * 0.02f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(second.copy(alpha = a2), Color.Transparent),
                        center = Offset(size.width * 1.02f, size.height * 0.30f),
                        radius = size.width * 0.85f,
                    ),
                )
            },
    )
}
