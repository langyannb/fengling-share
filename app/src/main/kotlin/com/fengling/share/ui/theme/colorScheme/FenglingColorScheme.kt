package com.fengling.share.ui.theme.colorScheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor

/**
 * 动态主题色板 - 基于种子色生成
 * 使用 material3 的 tone-based 调色: 种子色 → primary 色系
 * (Android 12+ 系统动态取色由 dynamicColorScheme 提供, 这里提供 App 内自定义主题色)
 */

/** 从种子色生成浅色/深色色板 (简化: 种子色作为 primary, 派生容器色) */
fun fenglingLightColorScheme(seed: Long = Settings.currentSeedColor()): androidx.compose.material3.ColorScheme {
    val primary = Color(seed)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.22f),
        onPrimaryContainer = primary,
        secondary = primary.copy(alpha = 0.8f),
        onSecondary = Color.White,
        secondaryContainer = primary.copy(alpha = 0.18f),
        onSecondaryContainer = primary,
        tertiary = primary.copy(alpha = 0.7f),
        background = Color(0xFFF7F8FA),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFFDFDFF),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF3EDF7),
        surfaceContainerHigh = Color(0xFFECE6F0),
        surfaceContainerHighest = Color(0xFFE6E0E9),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF4EFF4),
        inversePrimary = primary.copy(alpha = 0.8f),
        surfaceTint = primary,
        scrim = Color(0xFF000000),
    )
}

fun fenglingDarkColorScheme(seed: Long = Settings.currentSeedColor()): androidx.compose.material3.ColorScheme {
    val primary = Color(seed)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.30f),
        onPrimaryContainer = primary.copy(alpha = 0.9f),
        secondary = primary.copy(alpha = 0.85f),
        onSecondary = Color.White,
        secondaryContainer = primary.copy(alpha = 0.26f),
        onSecondaryContainer = primary.copy(alpha = 0.9f),
        tertiary = primary.copy(alpha = 0.75f),
        background = Color(0xFF131316),
        onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF17171B),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F24),
        surfaceContainerHigh = Color(0xFF2B292F),
        surfaceContainerHighest = Color(0xFF36343B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE6E1E5),
        inverseOnSurface = Color(0xFF313033),
        inversePrimary = primary.copy(alpha = 0.9f),
        surfaceTint = primary,
        scrim = Color(0xFF000000),
    )
}

/** 预置色板的辅助函数 */
val ThemeColor.colorValue: Color
    get() = Color(seed)
