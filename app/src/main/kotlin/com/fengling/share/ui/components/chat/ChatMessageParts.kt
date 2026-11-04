package com.fengling.share.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 群聊 (SocialScreen) 与私聊 (PmChatScreen) 共用的消息行辅助组件。
 *
 * 只收「辅助件」: 操作菜单行、圆形头像、居中提示、轻量按钮。
 * 两边的两个主渲染函数 (MessageRow / PmMessageRow) 字段差异太大, 不在这里合并。
 *
 * 一处保持一致的做法: 两边原本的差异 (字号 / 内边距 / 占位字 / 图片缩放模式 / 对齐)
 * 全部提升成带默认值的参数, 由调用点显式传自己的值 —— 谁的视觉都不许变。
 */

/** 「消息操作」菜单里的一行 (整行可点, 左对齐; danger = 撤回这类删除性操作) */
@Composable
fun ChatActionRow(
    text: String,
    danger: Boolean = false,
    /** 群聊 15sp, 私聊 14sp */
    fontSize: TextUnit = 15.sp,
    /** 群聊 9dp, 私聊 10dp */
    verticalPadding: Dp = 9.dp,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        fontSize = fontSize,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
    )
}

/**
 * 圆形头像: 有图就画图, 没图显示名字首字 (再没有就用 placeholder)。
 *
 * size 固定 34dp; 首字用 primary 色 Semibold。
 * contentScale / contentDescription 两边原本不同: 群聊 Crop + 无描述, 私聊 Fit + "头像"。
 */
@Composable
fun ChatAvatar(
    url: String,
    name: String,
    size: Dp = 34.dp,
    /** 名字为空时的兜底字: 群聊「群」, 私聊「铃」 */
    placeholder: String = "群",
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            // 外层 Box 已经裁成圆形, 这里再裁一次只是保证图片自身也圆角, 不影响观感
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = contentScale,
            )
        } else {
            Text(
                text = name.take(1).ifBlank { placeholder },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** 空态 / 加载态居中提示 (群聊不额外留边, 私聊左右各留 32dp) */
@Composable
fun ChatCenterHint(
    text: String,
    horizontalPadding: Dp = 0.dp,
    /** 群聊原本居中排版, 私聊不带 textAlign (多行时靠左) */
    textAlign: TextAlign? = TextAlign.Center,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = textAlign,
        )
    }
}

/** 轻量操作按钮 (不依赖 AccountScreen 的私有按钮组件) */
@Composable
fun SmallActionButton(text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier,
        cornerRadius = 12.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
