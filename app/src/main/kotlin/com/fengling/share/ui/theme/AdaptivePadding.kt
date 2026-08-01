package com.fengling.share.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自适应边距 (参考 legado-with-MD3 AdaptivePadding)
 * MD3 标准: 内容区水平边距 16dp
 */

/** 内容区水平边距 (MD3: 16dp) */
@Composable
fun Modifier.adaptiveHorizontalPadding(): Modifier {
    return this.padding(horizontal = 16.dp)
}

@Composable
fun Modifier.adaptiveHorizontalPadding(
    vertical: Dp,
): Modifier {
    return this.padding(horizontal = 16.dp, vertical = vertical)
}

/** 内容区 PaddingValues (水平 16dp) */
@Composable
fun adaptiveContentPadding(
    top: Dp,
    bottom: Dp,
): PaddingValues {
    return PaddingValues(
        top = top + 8.dp,
        bottom = bottom,
        start = 16.dp,
        end = 16.dp
    )
}

/** 列表内容 PaddingValues (水平 16dp, 无额外顶部) */
@Composable
fun adaptiveListPadding(
    top: Dp = 4.dp,
    bottom: Dp = 20.dp,
): PaddingValues {
    return PaddingValues(
        top = top,
        bottom = bottom,
        start = 16.dp,
        end = 16.dp
    )
}
