package com.fengling.share.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 面板展开 / 收起动画时长 (ms), 固定 tween。
 * 原来收/开用的是 spring (先冲一下再停), 加上收起最后一帧高度突变 → 用户反馈「卡一下再收起」。
 */
internal const val CHAT_PANEL_ANIM_MS = 220
/** 「+」面板里一行功能格的高度 (图标 52dp + 文字 + 上下留白) */
private val PANEL_ROW_HEIGHT = 84.dp

/**
 * 面板「从下往上滑出 / 向下收起」的布局变换 (v1.1.14)。
 *
 * 三条要点 (都是冲用户那句「点加号收起会卡一下再收起, 很僵硬」去的):
 *  1. 内容永远按**完整高度**测量 (约束每帧不变 → 内部布局被缓存), 动画只改「容器高度」,
 *     所以里面的 4 列网格 / 表情不会被逐帧重新测量。
 *  2. 容器高度与内容位移由**同一个进度**驱动, 消息列表跟面板同帧收缩 / 展开,
 *     不会出现「面板已经滑下去了, 列表还等半秒才跳一下」。
 *  3. 进度只在 layout 阶段读取 → 整段动画重组次数为 0, 只重新布局。
 *
 * 内容顶边贴着容器顶边 (= 刚体平移): 容器长高 = 面板整体上滑, 容器长矮 = 整体下滑。
 */
private fun Modifier.chatPanelSlide(progress: State<Float>, fullHeightPx: Int): Modifier =
    this
        .clipToBounds()
        .layout { measurable, constraints ->
            val avail = constraints.maxHeight
            val full = if (avail == Constraints.Infinity) fullHeightPx else minOf(fullHeightPx, avail)
            val placeable = measurable.measure(
                constraints.copy(minHeight = full, maxHeight = full),
            )
            val shown = (full * progress.value.coerceIn(0f, 1f)).roundToInt()
            layout(placeable.width, shown) {
                placeable.place(0, 0)
            }
        }

/**
 * 面板的展开进度 (0 = 完全收起, 1 = 完全展开), 供 [chatPanelSlide] 在 layout 阶段读取。
 * 真正的「收起动画播完再移除」由调用方的 mounted 状态负责。
 */
@Composable
private fun rememberChatPanelProgress(visible: Boolean): State<Float> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        anim.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(CHAT_PANEL_ANIM_MS, easing = FastOutSlowInEasing),
        )
    }
    // 只在 layout 阶段读它 → 动画期间不触发重组 (只重新布局)
    return remember(anim) { derivedStateOf { anim.value } }
}

/**
 * 收起动画播完之后才把整块面板从组合里摘掉 (高度此时已经是 0, 摘掉不会造成任何高度突变)。
 * 用 [panelMounted] 这层薄封装是为了让两个面板的写法保持完全一致。
 */
@Composable
private fun panelMounted(visible: Boolean): Boolean {
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
        } else {
            // 多等 2 帧: 保证进度已经归零, 摘掉节点时高度一定是 0
            kotlinx.coroutines.delay(CHAT_PANEL_ANIM_MS.toLong() + 32L)
            mounted = false
        }
    }
    return mounted
}

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
 * 布局: 4 列网格 (每页最多 8 个 = 2 行) + 分页圆点, 面板高按实际行数贴合内容。
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
    // 高度按内容的实际行数算, 不再固定占屏高 45% —— 只有 7 个功能时固定高度会把
    // 两行网格拉到各占一半面板高, 行间留出一大片空白 (真机 q3.png 那样)。
    val perRow = 4
    val maxRows = 2
    // 每页 8 个 (2 行 x 4 列); 实际画几行由该页的功能数决定, 一页放得下就不留空行
    val pages = if (items.isEmpty()) 1 else (items.size + perRow * maxRows - 1) / (perRow * maxRows)
    fun rowsOf(page: Int): Int {
        val count = (items.size - page * perRow * maxRows).coerceIn(0, perRow * maxRows)
        return if (count == 0) 1 else (count + perRow - 1) / perRow
    }
    // 多页时高度按满页算, 保证翻页时面板不跳高; 只有一页就直接贴合内容
    val heightRows = if (pages > 1) maxRows else rowsOf(0)
    val panelHeight = (
        8.dp + PANEL_ROW_HEIGHT * heightRows + (if (pages > 1) 18.dp else 0.dp) + 8.dp
        ).coerceAtMost(LocalConfiguration.current.screenHeightDp.dp * heightFraction)
    // 先起「摘除时机」(收起动画播完才整块移除), 再起进度动画 —— 两个 effect 的执行顺序
    // 就是这里的书写顺序, 这样展开的首帧进度仍停在 0, 面板不会先闪一下再滑上来。
    val panelInTree = panelMounted(visible)
    val progress = rememberChatPanelProgress(visible)
    if (!panelInTree) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .chatPanelSlide(progress, with(LocalDensity.current) { panelHeight.roundToPx() })
            .height(panelHeight)
            .glassSurface(
                // 面板排在输入栏下面, 背后没有内容可糊 —— 硬采样只会取到捕获层边界外的
                // 透明像素, 模糊后算出脏灰色。这里只做玻璃质感 (高光 + 内阴影)。
                backdrop = null,
                shape = RoundedCornerShape(
                    topStart = GlassRadius.panel,
                    topEnd = GlassRadius.panel,
                ),
                fill = MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
    ) {
        Spacer(Modifier.height(8.dp))
        // ===== 4 列网格 + 分页 (每页最多 8 个 = 2 行) =====
        val perPage = perRow * maxRows
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
                val pageRows = rowsOf(page)
                for (row in 0 until pageRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PANEL_ROW_HEIGHT),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (col in 0 until perRow) {
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
    // 先起「摘除时机」(收起动画播完才整块移除), 再起进度动画 —— 两个 effect 的执行顺序
    // 就是这里的书写顺序, 这样展开的首帧进度仍停在 0, 面板不会先闪一下再滑上来。
    val panelInTree = panelMounted(visible)
    val progress = rememberChatPanelProgress(visible)
    if (!panelInTree) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .chatPanelSlide(progress, with(LocalDensity.current) { panelHeight.roundToPx() })
            .height(panelHeight)
            .glassSurface(
                // 面板排在输入栏下面, 背后没有内容可糊 —— 硬采样只会取到捕获层边界外的
                // 透明像素, 模糊后算出脏灰色。这里只做玻璃质感 (高光 + 内阴影)。
                backdrop = null,
                shape = RoundedCornerShape(
                    topStart = GlassRadius.panel,
                    topEnd = GlassRadius.panel,
                ),
                fill = MiuixTheme.colorScheme.surface.copy(alpha = 0.96f),
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
