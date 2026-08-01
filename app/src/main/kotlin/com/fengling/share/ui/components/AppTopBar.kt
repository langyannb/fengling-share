package com.fengling.share.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AppTopBar - 统一顶栏 (Miuix)
 * 背景延伸到状态栏后面 (surface 色覆盖状态栏区域, 不显空白)
 * 紧凑标题 (16sp), 可选返回按钮 / 右侧操作
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 外层背景 Box: 覆盖状态栏区域 (Miuix TopAppBar 自带状态栏 insets padding,
    // Box 背景随之延伸到状态栏后面, 状态栏与标题栏同色)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        MiuixTopAppBar(
            title = title,
            titleColor = MiuixTheme.colorScheme.onBackground,
            navigationIcon = if (onBack != null) {
                val back = onBack
                {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(CircleShape)
                            .clickable(onClick = back)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            } else {
                {}
            },
            actions = actions,
        )
    }
}

/** 顶栏右侧文本按钮 (Miuix 风格) */
@Composable
fun RowScope.AppTopBarTextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}
