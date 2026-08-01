package com.fengling.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.theme.colorScheme.fenglingLightColorScheme
import com.fengling.share.ui.theme.colorScheme.fenglingDarkColorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 风铃分享库主题 - Miuix (小米设计语言) + MD3 动态色板
 * 外层 MiuixTheme 提供 Miuix 组件风格 (TopAppBar/Card/SearchBar/Switch...)
 * 内层 MaterialTheme 提供 MD3 色板 (MaterialTheme.colorScheme 引用)
 * 主题色: 预置 7 色板 + 自定义 (ColorPalette 选色)
 *
 * @param seedColor 种子色 (切换主题色时由调用方传入触发重组)
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    seedColor: Long = Settings.currentSeedColor(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 种子色变化时重建色板
    val colorScheme = remember(darkTheme, seedColor) {
        if (darkTheme) fenglingDarkColorScheme(seedColor) else fenglingLightColorScheme(seedColor)
    }

    MiuixTheme {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
