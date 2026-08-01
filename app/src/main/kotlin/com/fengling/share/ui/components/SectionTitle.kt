package com.fengling.share.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * SectionTitle - 区块标题 (参考 legado-with-MD3 SectionTitle)
 * 列表分组标题, 使用 titleSmall + onSurfaceVariant
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall,
    )
}
