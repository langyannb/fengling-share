package com.fengling.share.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ===== 风铃分享库 MD3 色板 (参考 legado-with-MD3 精致配色, 蓝色系) =====

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E5FF),
    onPrimaryContainer = Color(0xFF00105A),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF171A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F2),
    onTertiaryContainer = Color(0xFF2D1228),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF303037),
    inverseOnSurface = Color(0xFFF3F0F7),
    inversePrimary = Color(0xFFB9C4FF),
    surfaceDim = Color(0xFFDCD9E0),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2F9),
    surfaceContainer = Color(0xFFEFECF4),
    surfaceContainerHigh = Color(0xFFE9E6EE),
    surfaceContainerHighest = Color(0xFFE3E1E8),
    primaryFixed = Color(0xFFE0E5FF),
    onPrimaryFixed = Color(0xFF00105A),
    primaryFixedDim = Color(0xFFB9C4FF),
    onPrimaryFixedVariant = Color(0xFF2339D3),
    secondaryFixed = Color(0xFFE0E1F9),
    onSecondaryFixed = Color(0xFF171A2C),
    secondaryFixedDim = Color(0xFFC4C5DD),
    onSecondaryFixedVariant = Color(0xFF43465A),
    tertiaryFixed = Color(0xFFFFD7F2),
    onTertiaryFixed = Color(0xFF2D1228),
    tertiaryFixedDim = Color(0xFFE7BAD9),
    onTertiaryFixedVariant = Color(0xFF5D3C55),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9C4FF),
    onPrimary = Color(0xFF00208F),
    primaryContainer = Color(0xFF2339D3),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF43465A),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE7BAD9),
    onTertiary = Color(0xFF45263E),
    tertiaryContainer = Color(0xFF5D3C55),
    onTertiaryContainer = Color(0xFFFFD7F2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE3E1E8),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE3E1E8),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE3E1E8),
    inverseOnSurface = Color(0xFF303037),
    inversePrimary = Color(0xFF3D5AFE),
    surfaceDim = Color(0xFF131318),
    surfaceBright = Color(0xFF39393F),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF29292F),
    surfaceContainerHighest = Color(0xFF34343A),
    primaryFixed = Color(0xFFE0E5FF),
    onPrimaryFixed = Color(0xFF00105A),
    primaryFixedDim = Color(0xFFB9C4FF),
    onPrimaryFixedVariant = Color(0xFF2339D3),
    secondaryFixed = Color(0xFFE0E1F9),
    onSecondaryFixed = Color(0xFF171A2C),
    secondaryFixedDim = Color(0xFFC4C5DD),
    onSecondaryFixedVariant = Color(0xFF43465A),
    tertiaryFixed = Color(0xFFFFD7F2),
    onTertiaryFixed = Color(0xFF2D1228),
    tertiaryFixedDim = Color(0xFFE7BAD9),
    onTertiaryFixedVariant = Color(0xFF5D3C55),
)

/**
 * 风铃分享库主题
 * 外层 MiuixTheme 提供小米设计语言组件风格 (miuix-ui),
 * 内层 MaterialTheme 提供 MD3 组件风格 (material3), 与 legado-with-MD3 同风格。
 */
@Composable
fun FenglingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MiuixTheme {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
