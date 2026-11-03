package com.fengling.share.ui.theme

import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.theme.colorScheme.PaletteStyle
import com.fengling.share.ui.theme.colorScheme.SpecVersionOption

/**
 * 主题设置的一次性快照。
 *
 * 为什么要有它: 换肤相关配置现在有 8 项 (明暗 / 主题色 / 色板风格 / 色彩规范 /
 * 模糊 / 悬浮底栏 / 玻璃 / 预测性返回)。全部从 SharedPreferences 读, 但 Compose
 * 不会自动观察 SharedPreferences —— 必须把它们收进一个 `mutableStateOf`, 设置页
 * 改完调用 [fromSettings] 生成新快照赋回去, 才会触发整棵树重组。
 *
 * 单一来源仍是 [Settings] (落盘), 这个类只负责「读出来给 Compose 看」。
 */
data class ThemeState(
    val mode: ThemeMode,
    val color: ThemeColor,
    val paletteStyle: PaletteStyle,
    val specVersion: SpecVersionOption,
    val enableBlur: Boolean,
    val floatingBottomBar: Boolean,
    val glassBottomBar: Boolean,
    val predictiveBack: Boolean,
) {
    /** 当前种子色: 自定义色优先, 否则用预置色的 seed */
    val seedColor: Long
        get() = if (color == ThemeColor.CUSTOM) Settings.customColor else color.seed

    companion object {
        /** 从 SharedPreferences 读一份最新快照 (设置页每次改动后调用) */
        fun fromSettings(): ThemeState = ThemeState(
            mode = Settings.getThemeMode(),
            color = Settings.getThemeColor(),
            paletteStyle = PaletteStyle.fromName(Settings.paletteStyle),
            specVersion = SpecVersionOption.fromName(Settings.colorSpec),
            enableBlur = Settings.enableBlur,
            floatingBottomBar = Settings.floatingBottomBar,
            glassBottomBar = Settings.glassBottomBar,
            predictiveBack = Settings.predictiveBack,
        )
    }
}
