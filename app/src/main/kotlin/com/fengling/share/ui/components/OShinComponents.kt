package com.fengling.share.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 列表项在卡片组中的位置 (OShin CouiListItemPosition) */
enum class CouiPosition { Top, Middle, Bottom, Single }

/**
 * OShinSettingRow - COUI 风格设置行 (OShin FunArrow 等价)
 * 图标 + 标题 + 副标题 + 右侧文本 + 箭头, 按压缩放动画
 */
@Composable
fun OShinSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    rightText: String? = null,
    leftIcon: ImageVector? = null,
    iconColor: Color = MiuixTheme.colorScheme.onBackground,
    position: CouiPosition = CouiPosition.Single,
    // 彩色品牌图标插槽 (优先于 leftIcon; 用于 QQ/GitHub/官网等真实品牌彩色图标)
    leading: (@Composable () -> Unit)? = null,
    // 右侧自定义插槽 (优先于 rightText 和箭头; 用于 Switch 等控件)
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "rowScale",
    )

    val extraTop = if (position == CouiPosition.Top || position == CouiPosition.Single) 2.dp else 0.dp
    val extraBottom = if (position == CouiPosition.Bottom || position == CouiPosition.Single) 2.dp else 0.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp + extraTop + extraBottom)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = extraTop,
                bottom = extraBottom,
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .scale(scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.size(16.dp))
        } else if (leftIcon != null) {
            Icon(
                imageVector = leftIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
            Box(Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            if (rightText != null) {
                Text(
                    text = rightText,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** OShin 分隔线 */
@Composable
fun OShinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .heightIn(max = 1.dp)
            .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
    )
}

/**
 * OShinCard - COUI 风格卡片容器 (分组圆角)
 * 内容按 position 自动圆角
 */
@Composable
fun OShinCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 4.dp),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(contentPadding),
    ) {
        content()
    }
}

/** 卡片标题 (OShin SmallTitle 等价) */
@Composable
fun OShinCardTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}
