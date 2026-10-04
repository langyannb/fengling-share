package com.fengling.share.ui.main.my

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
 * 从抽奖通知里抠出卡密。
 * 服务端把卡密写进通知正文, 形如「你的卡密: GY-XXXX-XXXX」, 这里取「卡密/卡号」后面那段;
 * 抠不到就退回整段正文 (宁可复制多, 别复制空)。
 */
private fun lotteryCodeOf(item: NotifyItem): String {
    val text = (item.content + "\n" + item.title).trim()
    val m = Regex("(?:卡密|卡号)\\s*[:：]\\s*(\\S+)").find(text)
    return m?.groupValues?.get(1)?.trim().orEmpty()
}

/**
 * 顶部「新消息 N 条」汇总条
 *
 * 分类未读来自服务端 unread_by_type: system(系统通知) / admin(管理员) / social(群聊相关, 含 @我)
 */
@Composable
private fun UnreadSummary(unread: Int, unreadByType: Map<String, Int>) {
    val system = unreadByType["system"] ?: 0
    val admin = unreadByType["admin"] ?: 0
    val social = unreadByType["social"] ?: 0
    val total = if (unread > 0) unread else system + admin + social
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (total > 0) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (total > 0) "新消息 " + total + " 条" else "暂时没有新消息",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (total > 0) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onBackgroundVariant
            },
        )
        Spacer(Modifier.width(10.dp))
        if (system > 0) UnreadChip("系统 " + system)
        if (admin > 0) UnreadChip("管理员 " + admin)
        if (social > 0) UnreadChip("@我/群聊 " + social)
    }
}

@Composable
private fun UnreadChip(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

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
    /** 群聊类通知: 跳进对应群聊, messageId>0 时定位到那条消息, showNotice=true 时直接弹群公告 */
    onOpenGroup: ((groupId: Int, messageId: Int, showNotice: Boolean) -> Unit)? = null,
    /** 私聊通知: 跳进对应私聊会话, messageId>0 时定位到那条消息 */
    onOpenPm: ((convId: Int, messageId: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // 复制卡密用得到系统剪贴板
    val context = LocalContext.current

    var items by remember { mutableStateOf<List<NotifyItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<NotifyItem?>(null) }
    // 抽奖通知详情 (看/复制完整卡密)
    var lotteryTarget by remember { mutableStateOf<NotifyItem?>(null) }
    /** 分类未读数: type -> count (system / admin / social) */
    var unreadByType by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val hasMore = items.size < total

    fun load(targetPage: Int, append: Boolean) {
        scope.launch {
            if (append) loadingMore = true else loading = true
            ApiClient.notifications(page = targetPage, pageSize = PAGE_SIZE)
                .onSuccess { res ->
                    items = if (append) (items + res.list).distinctBy { it.id } else res.list
                    total = res.total
                    page = targetPage
                    MessageBadge.update(res.unread)
                    if (!append) unreadByType = res.unreadByType
                    error = ""
                }
                .onFailure { e -> error = e.message ?: "加载失败" }
            if (append) loadingMore = false else loading = false
        }
    }

    LaunchedEffect(Unit) { load(1, false) }

    /**
     * 点通知后的去向 (链接约定, 服务端写入 link 字段):
     * - msg:<群id>:<消息id>  群消息/@我  -> 进群聊并定位到那条消息
     * - notice:<群id>        群公告更新 -> 进群聊并弹出公告
     * - group:<群id>         旧格式兼容 -> 只进群聊
     * - pm:<会话id>:<消息id>  私聊消息   -> 进私聊会话并定位到那条消息
     * - http(s) 开头         普通链接   -> 内置浏览器
     */
    fun openLinkOf(item: NotifyItem) {
        val link = item.link
        when {
            link.startsWith("msg:") -> {
                val p = link.removePrefix("msg:").split(":")
                val gid = p.getOrNull(0)?.toIntOrNull() ?: 0
                val mid = p.getOrNull(1)?.toIntOrNull() ?: 0
                if (gid > 0 && onOpenGroup != null) onOpenGroup(gid, mid, false)
                else if (link.startsWith("http")) onOpenWeb(link, item.title)
                else onOpenWeb(link, item.title)
            }
            link.startsWith("notice:") -> {
                val gid = link.removePrefix("notice:").toIntOrNull() ?: 0
                if (gid > 0 && onOpenGroup != null) onOpenGroup(gid, 0, true)
            }
            link.startsWith("group:") -> {
                val gid = link.removePrefix("group:").toIntOrNull() ?: 0
                if (gid > 0 && onOpenGroup != null) onOpenGroup(gid, 0, false)
            }
            link.startsWith("pm:") -> {
                // 私聊通知: pm:<会话id>:<消息id>
                val p = link.removePrefix("pm:").split(":")
                val cid = p.getOrNull(0)?.toIntOrNull() ?: 0
                val mid = p.getOrNull(1)?.toIntOrNull() ?: 0
                if (cid > 0 && onOpenPm != null) onOpenPm(cid, mid)
                else onOpenWeb(link, item.title)
            }
            else -> onOpenWeb(link, item.title)
        }
    }

    fun markRead(item: NotifyItem, thenOpen: Boolean) {
        if (item.isRead) {
            if (thenOpen && item.link.isNotBlank()) openLinkOf(item)
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
            if (thenOpen && item.link.isNotBlank()) openLinkOf(item)
        }
    }

    /** 复制卡密到剪贴板 (和群消息「复制」同一套写法) */
    fun copyLotteryCode(item: NotifyItem) {
        val code = lotteryCodeOf(item).ifBlank { item.content }
        if (code.isBlank()) {
            Toast.makeText(context, "这条通知里没有卡密", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("lottery_code", code))
        Toast.makeText(context, "卡密已复制", Toast.LENGTH_SHORT).show()
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
                    // 顶部汇总: 「新消息 N 条」+ 分类未读 (和 QQ / 微信的消息提醒一样)
                    item(key = "summary") {
                        UnreadSummary(
                            unread = MessageBadge.unread,
                            unreadByType = unreadByType,
                        )
                    }
                    items(items, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            onClick = {
                                // 抽奖通知: 点开看完整卡密 (顺手标已读, 不跳链接)
                                if (item.type == "lottery") {
                                    markRead(item, thenOpen = false)
                                    lotteryTarget = item
                                } else {
                                    markRead(item, thenOpen = true)
                                }
                            },
                            onLongClick = { deleteTarget = item },
                            // 抽奖通知多一个「复制卡密」按钮
                            onCopyCode = if (item.type == "lottery") {
                                { copyLotteryCode(item) }
                            } else {
                                null
                            },
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

            // 抽奖通知详情: 完整卡密可长按选中, 也能一键复制
            val lt = lotteryTarget
            if (lt != null) {
                AlertDialog(
                    onDismissRequest = { lotteryTarget = null },
                    title = {
                        Text(
                            text = lt.title.ifBlank { "抽奖通知" },
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                    },
                    text = {
                        Column {
                            SelectionContainer {
                                Text(
                                    text = lt.content,
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                            val code = lotteryCodeOf(lt)
                            if (code.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = code,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = lt.createdAt,
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    },
                    confirmButton = {
                        M3TextButton(onClick = { copyLotteryCode(lt) }) {
                            Text(text = "复制卡密", color = MiuixTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        M3TextButton(onClick = { lotteryTarget = null }) {
                            Text(
                                text = "关闭",
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
    /** 非空时表示这是一条带卡密的通知: 正文全文显示 + 多一个「复制卡密」按钮 */
    onCopyCode: (() -> Unit)? = null,
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
                // 抽奖通知里的卡密要能看全, 不截断
                val code = if (onCopyCode != null) lotteryCodeOf(item) else ""
                Text(
                    text = item.content,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = if (onCopyCode != null) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onCopyCode != null && code.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = code,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "复制卡密",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable(onClick = onCopyCode)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
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

/** 通知类型标签: system=系统 / admin=管理员 / social=社交 / pm=私聊 / lottery=抽奖 */
@Composable
private fun TypeTag(type: String) {
    val (label, color) = when (type) {
        "admin" -> "管理员" to Color(0xFFE5484D)
        "social" -> "社交" to Color(0xFF2F9E5F)
        "pm" -> "私聊" to Color(0xFF3B82F6)
        "lottery" -> "抽奖" to Color(0xFFE08A00)
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
