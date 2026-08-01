package com.fengling.share.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * GlassCard - 毛玻璃风格卡片 (参考 legado-with-MD3)
 * Miuix Card + MD3 surfaceContainer 色 + 圆角 + 可选边框
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.Sink,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedShape: Shape = RoundedCornerShape(cornerRadius)
    val decoratedModifier = if (border != null) {
        modifier.border(border, resolvedShape)
    } else {
        modifier
    }
    val colors = MiuixCardDefaults.defaultColors(
        color = containerColor,
        contentColor = contentColor,
    )

    if (onClick != null) {
        MiuixCard(
            modifier = decoratedModifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            pressFeedbackType = pressFeedbackType,
            showIndication = true,
            onClick = onClick,
            colors = colors,
        ) {
            Column(content = content)
        }
    } else {
        MiuixCard(
            modifier = decoratedModifier.clip(resolvedShape),
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = colors,
        ) {
            Column(content = content)
        }
    }
}
