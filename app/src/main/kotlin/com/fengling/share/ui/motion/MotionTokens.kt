package com.fengling.share.ui.motion

/**
 * 全 App 动效的统一参数。
 *
 * 页面用短促的 fade/位移，列表用轻量交错上浮。所有页面只从这里取值，
 * 避免每个页面各调一套。新增场景时先加 token，再改调用点。
 */
object MotionTokens {

    /** 页面进入：320ms，淡入 + 轻微位移 */
    const val PageEnterMs = 320

    /** 列表首屏逐个上浮 */
    const val ListEnterMs = 300

    /** 列表项之间的交错延迟 */
    const val ListStepMs = 20
}
