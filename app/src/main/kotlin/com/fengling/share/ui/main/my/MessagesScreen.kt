package com.fengling.share.ui.main.my

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import com.fengling.share.data.NotifyItem
import com.fengling.share.ui.components.AppTopBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 每页条数 (与契约 notifications.page_size 默认一致) */
private const val PAGE_SIZE = 20

/**
 * 未读数的全局可观察单例
 *
 * 「我的」页的消息入口红点与消息中心内部状态共用同一份未读数, 避免两处显示不一致。
 */
object MessageBadge {
    var unread by mutableStateOf(0)
        private set

    fun update(value: Int) {
        unread = if (value < 0) 0 else value
    }
}

/**
 * MessagesScreen - 消息中心
 *
 * - 通知列表: 未读圆点 + 类型标签 + 标题 + 内容摘要 + 时间
 * - 点击: 先标记已读 (同步红点), 有 link 时用内置浏览器打开
 * - 顶部「全部已读」; 长按单条删除; 空状态; 滑到底部自动加载更多
 */
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<NotifyItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<NotifyItem?>(null) }

    val hasMore = items.size < total

    fun load(targetPage: Int, append: Boolean) {
        scope.launch {
            if (append) loadingMore = true else loading = true
            ApiClient.notifications(page = targetPage, pageSize = PAGE_SIZE)
                .onSuccess { (list, t, unread) ->
                    items = if (append) (items + list).distinctBy { it.id } else list
                    total = t
                    page = targetPage
                    MessageBadge.update(unread)
                    error = ""
                }
                .onFailure { e -> error = e.message ?: "加载失败" }
            if (append) loadingMore = false else loading = false
        }
    }

    LaunchedEffect(Unit) { load(1, false) }

    fun markRead(item: NotifyItem, thenOpen: Boolean) {
        if (item.isRead) {
            if (thenOpen && item.link.isNotBlank()) onOpenWeb(item.link, item.title)
            return
        }
        scope.launch {
            ApiClient.notificationRead(id = item.id)
                .onSuccess { unread ->
                    MessageBadge.update(unread)
                    items = items.map {
                        if (it.id == item.id) it.copy(isRead = true) else it
                    }
                }
                .onFailure { /* 标记已读失败不打断阅读, 下次进入会重试 */ }
            if (thenOpen && item.link.isNotBlank()) onOpenWeb(item.link, item.title)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "消息中心",
                onBack = onBack,
                actions = {
                    M3TextButton(
                        onClick = {
                            scope.launch {
                                ApiClient.notificationRead(all = true)
                                    .onSuccess { unread ->
                                        MessageBadge.update(unread)
                                        items = items.map { it.copy(isRead = true) }
                                    }
                            }
                        },
                        enabled = items.any { !it.isRead },
                    ) {
                        Text(
                            text = "全部已读",
                            fontSize = 13.sp,
                            color = if (items.any { !it.isRead }) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                loading && items.isEmpty() -> CenterText("加载中…")
                items.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (error.isNotBlank()) error else "暂时没有新消息",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        RetryButton { load(1, false) }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            onClick = { markRead(item, thenOpen = true) },
                            onLongClick = { deleteTarget = item },
                        )
                        // 滑到底部自动加载下一页
                        if (hasMore && item.id == items.last().id) {
                            LaunchedEffect(item.id) { load(page + 1, true) }
                        }
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "加载中…",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    } else if (!hasMore && items.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "没有更多了",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                }
            }

            val target = deleteTarget
            if (target != null) {
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = {
                        Text(
                            text = "删除通知",
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                    },
                    text = {
                        Text(
                            text = "确定删除「" + target.title + "」吗?",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    },
                    confirmButton = {
                        M3TextButton(onClick = {
                            val id = target.id
                            deleteTarget = null
                            scope.launch {
                                ApiClient.notificationDelete(id)
                                    .onSuccess {
                                        items = items.filterNot { it.id == id }
                                        if (total > 0) total -= 1
                                    }
                            }
                        }) {
                            Text(text = "删除", color = Color(0xFFE5484D))
                        }
                    },
                    dismissButton = {
                        M3TextButton(onClick = { deleteTarget = null }) {
                            Text(
                                text = "取消",
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotifyItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            // 短按已读/打开链接, 长按删除
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 未读圆点 (已读时留同宽占位, 避免文字跳动)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isRead) Color.Transparent else MiuixTheme.colorScheme.error,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeTag(item.type)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.title.ifBlank { "通知" },
                        fontSize = 15.sp,
                        fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.content,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.createdAt,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            if (item.link.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "查看",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 通知类型标签: system=系统 / admin=管理员 / social=社交 */
@Composable
private fun TypeTag(type: String) {
    val (label, color) = when (type) {
        "admin" -> "管理员" to Color(0xFFE5484D)
        "social" -> "社交" to Color(0xFF2F9E5F)
        else -> "系统" to MiuixTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun CenterText(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier,
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "重新加载",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
