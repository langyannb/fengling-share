package com.fengling.share.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * AppText - 主题驱动文本 (参考 legado-with-MD3 AppText)
 * 默认 MaterialTheme onSurface 色 + bodyMedium
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            color = color,
            fontWeight = fontWeight,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** 标题 (titleSmall, 列表项主标题) */
@Composable
fun AppTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    maxLines: Int = 1,
) {
    AppText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 次级文本 (onSurfaceVariant) */
@Composable
fun AppSubtitle(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    AppText(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
