package com.fengling.share.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fengling.share.ui.theme.colorScheme.fenglingLightColorScheme
import com.fengling.share.ui.theme.colorScheme.fenglingDarkColorScheme

/**
 * 风铃分享库主题入口 (参考 legado-with-MD3 AppTheme)
 *
 * 结构:
 * - 色板: BaseColorScheme 抽象基类 → FenglingColorScheme 具体实现
 * - Typography: MD3 完整字体体系 (Typography.kt)
 * - Shapes: MD3 标准圆角 (4/8/12/16/28dp)
 * - 深色模式: 跟随系统
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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
