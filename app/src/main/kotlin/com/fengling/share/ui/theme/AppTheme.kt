package com.fengling.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

    // Miuix Colors: 53 个槽位全部映射自 MD3 色板。
    // 之前只映射 8 个槽位 —— 其余 45 个会退回 miuix 的「浅色」默认值
    // (onSurfaceContainerVariant=0xFF737373 / onSurfaceVariantSummary=0x80FFFFFF /
    //  outline=0xFF404040 / dividerLine=0xFF393939 …), 深色背景上对比度严重不足,
    // 这就是「深色模式下关于 / 群组 / 首页有些字看不清」的根因。
    // 现在每个「文字 / 背景 / 描边」槽都对齐到 MD3 的语义槽, 对比度由 MD3 tonal 算法保证。
    val miuixColors: Colors = remember(darkTheme, colorScheme) {
        val c = colorScheme
        val disabledFill = c.onSurface.copy(alpha = 0.12f)
        val disabledText = c.onSurface.copy(alpha = 0.38f)
        val dimming = Color.Black.copy(alpha = if (darkTheme) 0.6f else 0.3f)
        val secondaryFill = c.secondaryContainer
        val onSecondaryFill = c.onSecondaryContainer
        val tertiaryFill = c.tertiaryContainer
        val onTertiaryFill = c.onTertiaryContainer
        val tertiaryVariantFill = c.surfaceContainerHigh
        val secondaryVariantFill = c.surfaceContainerHigh
        val onSecondaryVariantFill = c.onSurfaceVariant
        val keyPoint = c.primary.copy(alpha = 0.3f)
        val sliderBg = c.onSurface.copy(alpha = 0.08f)
        if (darkTheme) {
            darkColorScheme(
                primary = c.primary,
                onPrimary = c.onPrimary,
                primaryVariant = c.primary,
                onPrimaryVariant = c.onPrimary.copy(alpha = 0.7f),
                error = c.error,
                onError = c.onError,
                errorContainer = c.errorContainer,
                onErrorContainer = c.onErrorContainer,
                disabledPrimary = disabledFill,
                disabledOnPrimary = disabledText,
                disabledPrimaryButton = disabledFill,
                disabledOnPrimaryButton = disabledText,
                disabledPrimarySlider = disabledFill,
                primaryContainer = c.primaryContainer,
                onPrimaryContainer = c.onPrimaryContainer,
                secondary = c.secondary,
                onSecondary = c.onSecondary,
                secondaryVariant = secondaryVariantFill,
                onSecondaryVariant = onSecondaryVariantFill,
                disabledSecondary = disabledFill,
                disabledOnSecondary = disabledText,
                disabledSecondaryVariant = disabledFill,
                disabledOnSecondaryVariant = disabledText,
                secondaryContainer = secondaryFill,
                onSecondaryContainer = onSecondaryFill,
                secondaryContainerVariant = secondaryVariantFill,
                onSecondaryContainerVariant = c.onSurfaceVariant,
                tertiaryContainer = tertiaryFill,
                onTertiaryContainer = onTertiaryFill,
                tertiaryContainerVariant = tertiaryVariantFill,
                background = c.background,
                onBackground = c.onBackground,
                onBackgroundVariant = c.onSurfaceVariant,
                surface = c.surface,
                onSurface = c.onSurface,
                surfaceVariant = c.surfaceVariant,
                onSurfaceSecondary = c.onSurfaceVariant,
                onSurfaceVariantSummary = c.onSurfaceVariant,
                onSurfaceVariantActions = c.onSurfaceVariant,
                disabledOnSurface = disabledText,
                surfaceContainer = c.surfaceContainer,
                onSurfaceContainer = c.onSurface,
                onSurfaceContainerVariant = c.onSurfaceVariant,
                surfaceContainerHigh = c.surfaceContainerHigh,
                onSurfaceContainerHigh = c.onSurface,
                surfaceContainerHighest = c.surfaceContainerHighest,
                onSurfaceContainerHighest = c.onSurface,
                outline = c.outline,
                dividerLine = c.outlineVariant,
                windowDimming = dimming,
                sliderKeyPoint = keyPoint,
                sliderKeyPointForeground = c.primary,
                sliderBackground = sliderBg,
            )
        } else {
            lightColorScheme(
                primary = c.primary,
                onPrimary = c.onPrimary,
                primaryVariant = c.primary,
                onPrimaryVariant = c.onPrimary.copy(alpha = 0.7f),
                error = c.error,
                onError = c.onError,
                errorContainer = c.errorContainer,
                onErrorContainer = c.onErrorContainer,
                disabledPrimary = disabledFill,
                disabledOnPrimary = disabledText,
                disabledPrimaryButton = disabledFill,
                disabledOnPrimaryButton = disabledText,
                disabledPrimarySlider = disabledFill,
                primaryContainer = c.primaryContainer,
                onPrimaryContainer = c.onPrimaryContainer,
                secondary = c.secondary,
                onSecondary = c.onSecondary,
                secondaryVariant = secondaryVariantFill,
                onSecondaryVariant = onSecondaryVariantFill,
                disabledSecondary = disabledFill,
                disabledOnSecondary = disabledText,
                disabledSecondaryVariant = disabledFill,
                disabledOnSecondaryVariant = disabledText,
                secondaryContainer = secondaryFill,
                onSecondaryContainer = onSecondaryFill,
                secondaryContainerVariant = secondaryVariantFill,
                onSecondaryContainerVariant = c.onSurfaceVariant,
                tertiaryContainer = tertiaryFill,
                onTertiaryContainer = onTertiaryFill,
                tertiaryContainerVariant = tertiaryVariantFill,
                background = c.background,
                onBackground = c.onBackground,
                onBackgroundVariant = c.onSurfaceVariant,
                surface = c.surface,
                onSurface = c.onSurface,
                surfaceVariant = c.surfaceVariant,
                onSurfaceSecondary = c.onSurfaceVariant,
                onSurfaceVariantSummary = c.onSurfaceVariant,
                onSurfaceVariantActions = c.onSurfaceVariant,
                disabledOnSurface = disabledText,
                surfaceContainer = c.surfaceContainer,
                onSurfaceContainer = c.onSurface,
                onSurfaceContainerVariant = c.onSurfaceVariant,
                surfaceContainerHigh = c.surfaceContainerHigh,
                onSurfaceContainerHigh = c.onSurface,
                surfaceContainerHighest = c.surfaceContainerHighest,
                onSurfaceContainerHighest = c.onSurface,
                outline = c.outline,
                dividerLine = c.outlineVariant,
                windowDimming = dimming,
                sliderKeyPoint = keyPoint,
                sliderKeyPointForeground = c.primary,
                sliderBackground = sliderBg,
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
