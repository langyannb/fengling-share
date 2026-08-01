package com.fengling.share.ui.theme

import androidx.compose.material3.ColorScheme

/**
 * 色板抽象基类 (参考 legado-with-MD3 BaseColorScheme)
 * 每个具体色板实现提供 lightScheme 和 darkScheme
 */
abstract class BaseColorScheme {
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme

    fun getColorScheme(darkTheme: Boolean): ColorScheme {
        return if (darkTheme) darkScheme else lightScheme
    }
}
