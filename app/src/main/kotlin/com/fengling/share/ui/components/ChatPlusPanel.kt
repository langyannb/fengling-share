package com.fengling.share.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 聊天输入栏「+」面板里的一项功能 (v1.1.12 契约第 6 条)。
 * 只往里放 App 真的有的功能 —— 点了没反应的按钮一律不放。
 */
data class ChatPanelItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * 「+」面板: 从输入框下方 spring 弹出, 圆角上沿 + 液体玻璃底, 高度约屏高 45%。
 *
 * 布局照 QQ 截图:
 *  顶部一行图标 (横向可滑, 末尾固定「关闭」) + 下面 4 列网格 (每页最多 8 个) + 分页圆点。
 * 超过一页时才会出现圆点与左右滑动翻页; 只有一页就不画圆点 (避免多余的 UI)。
 */
@Composable
fun ChatPlusPanel(
    visible: Boolean,
    items: List<ChatPanelItem>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.45f,
) {
    val panelHeight = LocalConfiguration.current.screenHeightDp.dp * heightFraction
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetY = { it },
        ) + fadeIn(animationSpec = tween(140)),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = Spring.StiffnessMedium,
            ),
            targetOffsetY = { it },
        ) + fadeOut(animationSpec = tween(110)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
                .glassSurface(
                    // 面板在捕获层内部, 不能自引用 backdrop → 退化为半透明玻璃底
                    backdrop = null,
                    shape = RoundedCornerShape(
                        topStart = GlassRadius.panel,
                        topEnd = GlassRadius.panel,
                    ),
                    fill = MiuixTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
        ) {
            Spacer(Modifier.height(10.dp))
            // ===== 顶部一行图标 (与截图排布一致: 图标在上、小字在下, 最右是关闭) =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    TopIconButton(item)
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f))
                        .border(1.dp, glassStroke(), CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭面板",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
            )
            Spacer(Modifier.height(6.dp))
            // ===== 4 列网格 + 分页 (每页最多 8 个 = 2 行) =====
            val perPage = 8
            val pages = if (items.isEmpty()) 1 else (items.size + perPage - 1) / perPage
            val pagerState = rememberPagerState(pageCount = { pages })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                ) {
                    for (row in 0 until 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (col in 0 until 4) {
                                val index = page * perPage + row * 4 + col
                                val item = items.getOrNull(index)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (item != null) GridCell(item)
                                }
                            }
                        }
                    }
                }
            }
            if (pages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onBackground.copy(alpha = 0.18f)
                                    },
                                ),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** 顶部一行里的小图标 + 文字 */
@Composable
private fun TopIconButton(item: ChatPanelItem) {
    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { item.onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f))
                .border(1.dp, glassStroke(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/** 网格里的一个大方块 (图标 + 名字) */
@Composable
private fun GridCell(item: ChatPanelItem) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { item.onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f))
                .border(1.dp, glassStroke(), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/** 表情面板里能点的表情 (够用就行, 不做分类/最近使用) */
val CHAT_EMOJIS: List<String> = listOf(
    "😀", "😄", "😁", "😂", "🤣", "😊", "😍", "😘",
    "😜", "🤪", "😎", "🥰", "🤗", "🤔", "🙄", "😏",
    "😴", "😪", "😭", "😢", "😅", "😳", "😱", "🤯",
    "😡", "😤", "😋", "🤤", "🥳", "😬", "🙃", "😇",
    "👍", "👎", "👌", "🙏", "💪", "👏", "🤝", "✌️",
    "❤️", "💔", "🌹", "🎉", "🎁", "🔥", "⭐", "✨",
    "💯", "🍺", "🍉", "🍎", "🍰", "☕", "🍜", "🍚",
    "🐶", "🐱", "🐼", "🦄", "🌸", "🌈", "☀️", "🌙",
    "⚡", "🍀", "🎂", "🚀", "✈️", "🏆", "🎵", "🎮",
    "📱", "💻", "📷", "🎓", "🛒", "🏃", "🚴", "💩",
)

/**
 * 表情面板: 和「+」面板同一套弹出动画与玻璃底, 每行 4 个, 点一下把表情插进输入框。
 */
@Composable
fun ChatEmojiPanel(
    visible: Boolean,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 0.30f,
) {
    val panelHeight = LocalConfiguration.current.screenHeightDp.dp * heightFraction
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetY = { it },
        ) + fadeIn(animationSpec = tween(140)),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = Spring.StiffnessMedium,
            ),
            targetOffsetY = { it },
        ) + fadeOut(animationSpec = tween(110)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
                .glassSurface(
                    backdrop = null,
                    shape = RoundedCornerShape(
                        topStart = GlassRadius.panel,
                        topEnd = GlassRadius.panel,
                    ),
                    fill = MiuixTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "表情",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭表情面板",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            val perPage = 16
            val pages = if (CHAT_EMOJIS.isEmpty()) 1 else (CHAT_EMOJIS.size + perPage - 1) / perPage
            val pagerState = rememberPagerState(pageCount = { pages })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                ) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (col in 0 until 4) {
                                val index = page * perPage + row * 4 + col
                                val emoji = CHAT_EMOJIS.getOrNull(index)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (emoji != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onPick(emoji) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(text = emoji, fontSize = 24.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onBackground.copy(alpha = 0.18f)
                                    },
                                ),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
