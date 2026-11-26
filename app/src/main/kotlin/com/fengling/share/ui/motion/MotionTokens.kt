package com.fengling.share.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * 全 App 动效的统一参数。
 *
 * 参数参考 FlClash 的 Apple 交互质感：页面用短促的 fade/位移，列表用轻量 spring，
 * sheet 用比页面更软的 spring。所有页面只从这里取值，避免每个页面各调一套。
 */
object MotionTokens {

    /** 页面进入：320ms，淡入 + 0.98→1 + 右移 24dp→0 */
    const val PageEnterMs = 320

    /** 页面退出：220ms，淡出 + 轻微左移 */
    const val PageExitMs = 220

    /** 列表首屏逐个上浮 */
    const val ListEnterMs = 300

    /** 列表项之间的交错延迟 */
    const val ListStepMs = 20

    /** 主题色板过渡 */
    const val ThemeMs = 300

    /** 按压、消息气泡：快而稳 */
    val SpringSnappy = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 420f,
    )

    /** Sheet / Dialog：更软，接近 FlClash 的 modal */
    val SpringSheet = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 560f,
    )

    /** 底栏图标选中时的轻微回弹 */
    val SpringBar = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 500f,
    )
}
