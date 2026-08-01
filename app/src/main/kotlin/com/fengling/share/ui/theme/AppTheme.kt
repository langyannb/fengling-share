package com.fengling.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.theme.colorScheme.fenglingLightColorScheme
import com.fengling.share.ui.theme.colorScheme.fenglingDarkColorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 风铃分享库主题 - Miuix (小米设计语言)
 * 外层 MiuixTheme 提供 Miuix 组件风格 (TopAppBar/Card/SearchBar/Switch...)
 * 内层 MaterialTheme 提供 MD3 色板 (MaterialTheme.colorScheme 引用)
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

    MiuixTheme {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
