package com.fengling.share.ui.theme.colorScheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/**
 * 色板风格 (对应 material-color-utilities 的 9 种 Variant)。
 *
 * material-color-utilities 只提供 8 个 Scheme 类和 Variant 枚举, 没有 Compose 侧的
 * PaletteStyle, 所以这里自己定义一份, 并逐项映射到具体的 Scheme 实现。
 */
enum class PaletteStyle(val label: String) {
    TONAL_SPOT("柔和 (默认)"),
    VIBRANT("鲜艳"),
    EXPRESSIVE("活泼"),
    FIDELITY("忠实原色"),
    CONTENT("内容优先"),
    NEUTRAL("中性"),
    MONOCHROME("单色"),
    RAINBOW("彩虹"),
    FRUIT_SALAD("水果沙拉"),
    ;

    companion object {
        fun fromName(name: String?): PaletteStyle =
            entries.firstOrNull { it.name == name } ?: TONAL_SPOT
    }
}

/** 色彩规范版本 (MD3 2021 / 2025)。 */
enum class SpecVersionOption(val label: String, val spec: ColorSpec.SpecVersion) {
    V2021("Material 3 (2021)", ColorSpec.SpecVersion.SPEC_2021),
    V2025("Material 3 Expressive (2025)", ColorSpec.SpecVersion.SPEC_2025),
    ;

    companion object {
        fun fromName(name: String?): SpecVersionOption =
            entries.firstOrNull { it.name == name } ?: V2021
    }
}

/**
 * 用 MD3 tonal 色板算法从种子色生成 Compose [ColorScheme]。
 *
 * 与旧的 fenglingLightColorScheme 的区别: 旧版直接把种子色当 primary、容器色靠 alpha 派生,
 * 成对颜色的对比度没有保证 (浅色下 primary 上的白字常常 < 4.5:1); 这里走完整的 HCT tonal
 * 计算, primary/onPrimary 等成对颜色的对比度由算法保证。
 *
 * @param seed 种子色 (ARGB, 0xAARRGGBB 形式存放在 Long 里)
 * @param darkTheme 是否深色
 * @param style 色板风格
 * @param spec 色彩规范版本
 */
fun dynamicColorScheme(
    seed: Long,
    darkTheme: Boolean,
    style: PaletteStyle = PaletteStyle.TONAL_SPOT,
    spec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
): ColorScheme {
    val scheme = tonalScheme(seed, darkTheme, style, spec)
    return if (darkTheme) scheme.toDarkColorScheme() else scheme.toLightColorScheme()
}

/** 直接暴露 [DynamicScheme], 供主题预览卡等需要逐个取色值的地方使用。 */
fun tonalScheme(
    seed: Long,
    darkTheme: Boolean,
    style: PaletteStyle = PaletteStyle.TONAL_SPOT,
    spec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
): DynamicScheme {
    val hct = Hct.fromInt(seed.toInt())
    // contrastLevel = 0.0 是 MD3 标准对比度; 调高会牺牲观感换可读性, 这里不暴露给用户
    return when (style) {
        PaletteStyle.TONAL_SPOT -> SchemeTonalSpot(hct, darkTheme, 0.0, spec)
        PaletteStyle.VIBRANT -> SchemeVibrant(hct, darkTheme, 0.0, spec)
        PaletteStyle.EXPRESSIVE -> SchemeExpressive(hct, darkTheme, 0.0, spec)
        PaletteStyle.FIDELITY -> SchemeFidelity(hct, darkTheme, 0.0, spec)
        PaletteStyle.CONTENT -> SchemeContent(hct, darkTheme, 0.0, spec)
        PaletteStyle.NEUTRAL -> SchemeNeutral(hct, darkTheme, 0.0, spec)
        PaletteStyle.MONOCHROME -> SchemeMonochrome(hct, darkTheme, 0.0, spec)
        PaletteStyle.RAINBOW -> SchemeRainbow(hct, darkTheme, 0.0, spec)
        PaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, darkTheme, 0.0, spec)
    }
}

private fun DynamicScheme.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(surfaceTint),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
)

private fun DynamicScheme.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(surfaceTint),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
)
