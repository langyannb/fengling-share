package com.fengling.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.theme.colorScheme.fenglingLightColorScheme
import com.fengling.share.ui.theme.colorScheme.fenglingDarkColorScheme

/**
 * 风铃分享库主题入口 (参考 legado-with-MD3 AppTheme)
 *
 * 结构:
 * - 色板: fenglingLightColorScheme / fenglingDarkColorScheme 完整 MD3 色板
 * - Typography: MD3 完整字体体系
 * - Shapes: MD3 标准圆角
 * - 主题模式: 跟随系统 / 浅色 / 深色
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = remember(darkTheme) {
        if (darkTheme) fenglingDarkColorScheme() else fenglingLightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(),
        content = content
    )
}
