package com.fengling.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.theme.colorScheme.fenglingLightColorScheme
import com.fengling.share.ui.theme.colorScheme.fenglingDarkColorScheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 风铃分享库主题 - Miuix (小米设计语言) + MD3 动态色板
 * MiuixTheme 传入动态 Colors (Miuix 组件才能换肤)
 * MaterialTheme 提供 MD3 色板 (MaterialTheme.colorScheme 引用)
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

    // Miuix Colors (映射自 MD3 色板, 让 Miuix 组件同步换肤; Colors 参数是 Color)
    val miuixColors: Colors = remember(darkTheme, seedColor) {
        val primary = colorScheme.primary
        val onPrimary = colorScheme.onPrimary
        val bg = colorScheme.background
        val onBg = colorScheme.onBackground
        val onBgVariant = colorScheme.onSurfaceVariant
        val surface = colorScheme.surface
        val surfaceVariant = colorScheme.surfaceVariant
        if (darkTheme) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                background = bg,
                onBackground = onBg,
                onBackgroundVariant = onBgVariant,
                surface = surface,
                onSurface = onBg,
                surfaceVariant = surfaceVariant,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                background = bg,
                onBackground = onBg,
                onBackgroundVariant = onBgVariant,
                surface = surface,
                onSurface = onBg,
                surfaceVariant = surfaceVariant,
            )
        }
    }

    MiuixTheme(
        colors = miuixColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
