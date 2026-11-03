package com.fengling.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.theme.colorScheme.PaletteStyle
import com.fengling.share.ui.theme.colorScheme.SpecVersionOption
import com.fengling.share.ui.theme.colorScheme.dynamicColorScheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 模糊是否可用 (开关打开 + 设备支持 RenderEffect)。
 *
 * 用 staticCompositionLocalOf: 只在主题设置页切换开关时整体重组一次, 平时读它不产生依赖。
 */
val LocalEnableBlur = staticCompositionLocalOf { true }

/** 底部导航栏是否用悬浮胶囊样式。 */
val LocalFloatingBottomBar = staticCompositionLocalOf { true }

/** 悬浮底栏是否带玻璃质感 (仅在 [LocalFloatingBottomBar] 开启时有意义)。 */
val LocalGlassBottomBar = staticCompositionLocalOf { true }

/** 预测性返回动画是否开启。 */
val LocalPredictiveBack = staticCompositionLocalOf { true }

/**
 * 当前是否深色主题。
 *
 * 与 [isSystemInDarkTheme] 的区别: 这里已经算上了用户在设置里选的「强制浅色 / 强制深色」。
 * 任何需要判断明暗的地方都应该用它, 而不是直接调 [isSystemInDarkTheme] —— 否则在用户
 * 手动选浅色、但系统是深色时, 会拿到相反的结论 (玻璃色、图标色全反)。
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * 风铃分享库主题 - Miuix (小米设计语言) + MD3 动态色板
 *
 * MiuixTheme 传入动态 Colors (Miuix 组件才能换肤)
 * MaterialTheme 提供 MD3 色板 (MaterialTheme.colorScheme 引用)
 *
 * 色板由 MD3 tonal 算法从种子色推导 (见 DynamicColorScheme.kt), 不再是「种子色直接当 primary」。
 *
 * @param seedColor 种子色 (切换主题色时由调用方传入触发重组)
 * @param paletteStyle 色板风格
 * @param specVersion 色彩规范版本
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    seedColor: Long = Settings.currentSeedColor(),
    paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
    specVersion: SpecVersionOption = SpecVersionOption.V2021,
    enableBlur: Boolean = Settings.enableBlur,
    floatingBottomBar: Boolean = Settings.floatingBottomBar,
    glassBottomBar: Boolean = Settings.glassBottomBar,
    predictiveBack: Boolean = Settings.predictiveBack,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 种子色 / 风格 / 规范 / 明暗 任一变化时重建色板 (tonal 计算有成本, 必须 remember)
    val colorScheme = remember(darkTheme, seedColor, paletteStyle, specVersion) {
        dynamicColorScheme(
            seed = seedColor,
            darkTheme = darkTheme,
            style = paletteStyle,
            spec = specVersion.spec,
        )
    }

    // Miuix Colors (映射自 MD3 色板, 让 Miuix 组件同步换肤; Colors 参数是 Color)
    val miuixColors: Colors = remember(darkTheme, colorScheme) {
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

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalEnableBlur provides enableBlur,
        LocalFloatingBottomBar provides floatingBottomBar,
        LocalGlassBottomBar provides glassBottomBar,
        LocalPredictiveBack provides predictiveBack,
    ) {
        MiuixTheme(
            colors = miuixColors,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                content = content
            )
        }
    }
}

/** 当前是否深色 (已算上用户的手动选择)。@see LocalDarkTheme */
@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(): Boolean = LocalDarkTheme.current
