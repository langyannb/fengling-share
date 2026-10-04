package com.fengling.share.ui.pm

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.MessageStream
import com.fengling.share.data.PmConversation
import com.fengling.share.data.StreamEvent
import com.fengling.share.data.UserStore
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.TagChips
import com.fengling.share.ui.social.LoginRequiredView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 会话列表静默刷新间隔 (毫秒): 和群列表一样 8 秒 */
private const val PM_LIST_POLL_MS = 8000L

/**
 * 私聊会话列表 (接口契约 A 节 pm_conversations)。
 *
 * 挂在社交页顶部的「私聊」分段 Tab 里, 和「群组」是两条独立的分类。
 * 未登录时沿用群组那套 LoginRequiredView 引导登录。
 */
@Composable
fun PmScreen(
    /** 点会话进入聊天页 (userId = 对方 id, convId = 已有会话 id) */
    onOpenChat: (userId: Int, convId: Int) -> Unit,
    /** 点头像看对方主页 */
    onOpenUser: ((userId: Int) -> Unit)? = null,
    onNeedLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!UserStore.isLoggedIn()) {
        LoginRequiredView(onBack = null, onNeedLogin = onNeedLogin)
        return
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val foreground = rememberPmForeground()

    var conversations by remember { mutableStateOf<List<PmConversation>>(emptyList()) }
    var totalUnread by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    /** 拉会话列表; silent = true 时不显示加载态 (后台轮询用) */
    fun load(isRefresh: Boolean = false, silent: Boolean = false) {
        scope.launch {
            if (!silent) {
                if (isRefresh) refreshing = true else loading = true
            }
            ApiClient.pmConversations()
                .onSuccess { page ->
                    conversations = page.list
                    totalUnread = page.totalUnread
                    error = ""
                }
                .onFailure { e ->
                    // 静默轮询失败不动界面, 免得把已有列表清成错误页
                    if (!silent) error = e.userFriendlyMessage()
                }
            if (!silent) {
                if (isRefresh) refreshing = false else loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load(false) }

    // 前台静默轮询: 和群列表一样 8 秒一次, 后台不轮询
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            delay(PM_LIST_POLL_MS)
            load(isRefresh = true, silent = true)
        }
    }

    // SSE 实时流: 收到私聊消息 / 断线重连 -> 立刻静默重拉会话列表。
    // 静默 = 不显示加载态、失败不动界面; 上面的 8 秒兜底轮询保留不动。
    // ⚠️ 必须放在局部函数 load() 之后 (Kotlin 局部函数先声明后使用)。
    LaunchedEffect(Unit) {
        MessageStream.events.collect { event ->
            when (event) {
                is StreamEvent.Pm -> load(isRefresh = true, silent = true)
                StreamEvent.Reconnected -> load(isRefresh = true, silent = true)
                else -> Unit
            }
        }
    }

    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = { load(true) },
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            loading && conversations.isEmpty() -> CenterHint("加载中…")
            conversations.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (error.isNotBlank()) error else "还没有私聊会话\n在群里点头像就能发起私聊",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Card(onClick = { load(false) }, cornerRadius = 12.dp) {
                        Text(
                            text = "重新加载",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    val hint = "点击会话进入私聊 · 和群聊消息分开显示" +
                        (if (totalUnread > 0) " · 未读 " + totalUnread + " 条" else "")
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                items(conversations, key = { it.convId }) { conv ->
                    PmConversationCard(
                        conversation = conv,
                        onClick = { onOpenChat(conv.userId, conv.convId) },
                        onAvatarClick = {
                            if (onOpenUser != null && conv.userId > 0) {
                                onOpenUser(conv.userId)
                            } else {
                                Toast.makeText(context, "该用户暂时打不开主页", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 会话列表一行: 头像 + 昵称 + 最新消息 + 右侧时间/未读 */
@Composable
private fun PmConversationCard(
    conversation: PmConversation,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像: 点一下看主页, 不让点击穿透到整行的「进会话」
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .pointerInput(conversation.userId) {
                        detectTapGestures { onAvatarClick() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (conversation.avatar.isNotBlank()) {
                    AsyncImage(
                        model = conversation.avatar,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        text = conversation.displayName.take(1).ifBlank { "友" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 管理员标签 (防骗警示, 契约 F1)
                    if (conversation.tags.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        TagChips(tags = conversation.tags, max = 2, small = true)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pmPreviewText(conversation),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                val timeText = pmTimeText(conversation.lastTime)
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                if (conversation.unread > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5484D))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (conversation.unread > 99) "99+" else conversation.unread.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** 会话副标题: 纯图片显示 [图片], 撤回显示「消息已撤回」 */
private fun pmPreviewText(conversation: PmConversation): String {
    val last = conversation.last ?: return "暂无消息"
    if (last.isRecalled) return "消息已撤回"
    val content = last.content.trim()
    if (content.isNotBlank()) return content
    if (last.image.isNotBlank()) return "[图片]"
    return "暂无消息"
}

/** 会话时间: 今天 HH:mm, 昨天「昨天」, 更早 MM-dd */
private fun pmTimeText(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw) ?: return ""
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dayFmt.format(Date())
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
        val day = dayFmt.format(date)
        when {
            day == today -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            day == dayFmt.format(yesterday) -> "昨天"
            else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        ""
    }
}

/** 会话列表空态/加载态提示 */
@Composable
private fun CenterHint(text: String) {
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

/** 页面是否在前台 (后台不轮询, 省电也省流量) */
@Composable
private fun rememberPmForeground(): Boolean {
    val context = LocalContext.current
    val owner = context as? LifecycleOwner
    var foreground by remember { mutableStateOf(true) }
    if (owner != null) {
        androidx.compose.runtime.DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> foreground = true
                    Lifecycle.Event.ON_PAUSE -> foreground = false
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }
    return foreground
}
