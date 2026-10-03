package com.fengling.share.ui.social

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.SocialGroup
import com.fengling.share.data.SocialGroupMember
import com.fengling.share.data.SocialMessage
import com.fengling.share.data.User
import com.fengling.share.data.UserStore
import com.fengling.share.ui.components.AppTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 轮询间隔 (毫秒): 聊天页每 3 秒拉一次新消息 */
private const val POLL_INTERVAL_MS = 3000L

/** 群消息内容里的 @昵称 (中文/字母/数字/下划线, 不含空白与 @) */
private val MENTION_REGEX = Regex("@[^\\s@]{1,20}")

/**
 * SocialScreen - 社交页 (群组列表 → 群聊)
 *
 * 结构:
 * - 首页: PullToRefresh + 群组卡片列表 (群名/简介/公告摘要/消息数), 空状态可重试
 * - 聊天页: 消息气泡 (自己靠右主色 / 别人靠左灰色) + @高亮 + 3 秒轮询 + 长按撤回
 *
 * 轮询只在页面处于前台 (ON_RESUME) 时进行, 页面退到后台自动停止。
 */
@Composable
fun SocialScreen(
    /** 为 null 时作为底部 tab 常驻页使用 (顶部不显示返回按钮) */
    onBack: (() -> Unit)? = null,
    /** 指定群 id: 进入后自动打开该群 (从「群组」tab 点进来时用) */
    initialGroupId: Int? = null,
    /** 非空时点群组交给外部导航 (tab 模式全屏打开聊天页); 为空则页内切换 */
    onOpenGroup: ((SocialGroup) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<SocialGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var currentGroup by remember { mutableStateOf<SocialGroup?>(null) }

    fun loadGroups(isRefresh: Boolean) {
        scope.launch {
            if (isRefresh) refreshing = true else loading = true
            ApiClient.socialGroups()
                .onSuccess {
                    groups = it
                    error = ""
                }
                .onFailure { e -> error = e.message ?: "加载失败" }
            if (isRefresh) refreshing = false else loading = false
        }
    }

    LaunchedEffect(Unit) { loadGroups(false) }

    // 从「群组」tab 指定群进入: 列表加载完成后自动打开该群
    LaunchedEffect(groups) {
        val pid = initialGroupId ?: return@LaunchedEffect
        if (currentGroup == null) {
            groups.firstOrNull { it.id == pid }?.let { currentGroup = it }
        }
    }

    // 返回键: 全屏路由模式(从「群组」tab 点进某个群)直接回上一页, 一次到位;
    // 内嵌模式(群组列表 + 聊天同屏)则先回到群组列表。用户反馈原来要点两次才回去。
    BackHandler(enabled = currentGroup != null || onBack != null) {
        if (onBack != null) onBack() else currentGroup = null
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            val g = currentGroup
            when {
                // 聊天页: 全屏路由模式直接回上一页, 内嵌模式先回群组列表
                g != null -> AppTopBar(
                    title = g.name,
                    onBack = { if (onBack != null) onBack() else currentGroup = null },
                )
                onBack != null -> AppTopBar(title = "群组", onBack = onBack)
                // tab 常驻页: 无返回按钮
                else -> AppTopBar(title = "群组")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val group = currentGroup
            if (group == null) {
                GroupList(
                    groups = groups,
                    loading = loading,
                    refreshing = refreshing,
                    error = error,
                    onRefresh = { loadGroups(true) },
                    onRetry = { loadGroups(false) },
                    onOpen = { g ->
                        if (onOpenGroup != null) onOpenGroup(g) else currentGroup = g
                    },
                )
            } else {
                ChatView(
                    group = group,
                    me = UserStore.current,
                    onToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                )
            }
        }
    }
}

// ===================== 群组列表 =====================

@Composable
private fun GroupList(
    groups: List<SocialGroup>,
    loading: Boolean,
    refreshing: Boolean,
    error: String,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (SocialGroup) -> Unit,
) {
    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            loading && groups.isEmpty() -> CenterHint("加载中…")
            groups.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (error.isNotBlank()) error else "暂无可用群组",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    SmallActionButton(text = "重新加载", onClick = onRetry)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    Text(
                        text = "点击群组进入聊天 · 群公告与消息实时同步",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                items(groups, key = { it.id }) { g ->
                    GroupCard(group = g, onClick = { onOpen(g) })
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: SocialGroup, onClick: () -> Unit) {
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 群图标: 有图用图, 没有用群名首字占位
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (group.icon.isNotBlank()) {
                    AsyncImage(
                        model = group.icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = group.name.take(1).ifBlank { "群" },
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
                        text = group.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = group.memberCount.toString() + " 人 · " + group.messageCount + " 条",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = group.description.ifBlank { "暂无群简介" },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.notice.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "公告： " + group.notice.replace('\n', ' ').take(40),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ===================== 聊天页 =====================

@Composable
private fun ChatView(
    group: SocialGroup,
    me: User?,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val foreground = rememberIsForeground()

    var messages by remember { mutableStateOf<List<SocialMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var recallTarget by remember { mutableStateOf<SocialMessage?>(null) }
    var showMentionPicker by remember { mutableStateOf(false) }
    var mentionMembers by remember { mutableStateOf<List<SocialGroupMember>>(emptyList()) }
    var mentionLoading by remember { mutableStateOf(false) }
    /** 通过选择器 @ 到的人: 直接记 userId (昵称可能重名) */
    var pickedAt by remember { mutableStateOf<List<Int>>(emptyList()) }

    val isAdmin = me != null && me.role == "admin"

    fun loadLatest(isRefresh: Boolean) {
        scope.launch {
            if (isRefresh) refreshing = true else loading = true
            ApiClient.socialMessages(group.id, limit = 30)
                .onSuccess { list ->
                    messages = list.distinctBy { it.id }.sortedBy { it.id }
                }
                .onFailure { e -> onToast(e.message ?: "消息加载失败") }
            if (isRefresh) refreshing = false else loading = false
        }
    }

    /** 合并新消息: 按 id 去重 + 正序 (轮询片段可能重复或乱序) */
    fun mergeNew(incoming: List<SocialMessage>) {
        if (incoming.isEmpty()) return
        val base = messages
        val merged = (base + incoming).distinctBy { it.id }.sortedBy { it.id }
        messages = merged
    }

    LaunchedEffect(group.id) { loadLatest(false) }

    // 3 秒轮询: 仅在页面处于前台时进行 (退到后台立刻停)
    LaunchedEffect(group.id, foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            delay(POLL_INTERVAL_MS)
            val after = messages.maxOfOrNull { it.id } ?: 0
            ApiClient.socialMessages(group.id, afterId = after)
                .onSuccess { new -> mergeNew(new) }
        }
    }

    // 有新消息时滚到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    /** 昵称 → userId 映射 (只用当前已加载的消息构建, 对应 @ 解析的简单实现) */
    fun resolveMentionIds(text: String): List<Int> {
        val nameToId = messages
            .filter { it.nickname.isNotBlank() }
            .associate { it.nickname to it.userId }
        return MENTION_REGEX.findAll(text)
            .mapNotNull { m -> nameToId[m.value.removePrefix("@")] }
            .filter { it > 0 }
            .toList()
            .distinct()
    }

    /** 打开 @ 选择器 (首次打开时拉取成员候选) */
    fun openMentionPicker() {
        showMentionPicker = true
        if (mentionLoading) return
        mentionLoading = true
        scope.launch {
            ApiClient.socialGroupMembers(group.id)
                .onSuccess { mentionMembers = it }
                .onFailure { e -> onToast(e.message ?: "成员加载失败") }
            mentionLoading = false
        }
    }

    fun doSend() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        sending = true
        scope.launch {
            ApiClient.socialSend(group.id, text, (resolveMentionIds(text) + pickedAt).distinct())
                .onSuccess {
                    input = ""
                    pickedAt = emptyList()
                    val after = messages.maxOfOrNull { it.id } ?: 0
                    ApiClient.socialMessages(group.id, afterId = after)
                        .onSuccess { new -> mergeNew(new) }
                }
                .onFailure { e -> onToast(e.message ?: "发送失败") }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { loadLatest(true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    loading && messages.isEmpty() -> CenterHint("加载中…")
                    messages.isEmpty() -> CenterHint("还没有人说话, 来打个招呼吧")
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val mine = me != null && msg.userId == me.id
                            MessageRow(
                                msg = msg,
                                mine = mine,
                                canRecall = !msg.isRecalled && (mine || isAdmin),
                                onLongPress = { recallTarget = msg },
                            )
                        }
                    }
                }
            }
        }

        // ===== 底部输入区 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 500) input = it },
                    placeholder = {
                        Text(
                            text = "说点什么… 用 @昵称 提醒对方",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    ),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            SmallActionButton(
                text = "@",
                onClick = { openMentionPicker() },
            )
            Spacer(Modifier.width(6.dp))
            SmallActionButton(
                text = if (sending) "发送中" else "发送",
                onClick = { doSend() },
            )
        }
    }

    // ===== @ 成员选择 =====
    if (showMentionPicker) {
        AlertDialog(
            onDismissRequest = { showMentionPicker = false },
            title = {
                Text(text = "选择要 @ 的人", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                when {
                    mentionLoading -> Text(
                        text = "加载中…",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    mentionMembers.isEmpty() -> Text(
                        text = "暂无可 @ 的成员。\n群里有人发过言后, 他就会出现在这里。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(mentionMembers, key = { it.id }) { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        input = (input + "@" + m.nickname + " ").take(500)
                                        pickedAt = (pickedAt + m.id).distinct()
                                        showMentionPicker = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = m.nickname,
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                                if (m.role == "admin") {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "管理员",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                M3TextButton(onClick = { showMentionPicker = false }) {
                    Text(text = "关闭", color = MiuixTheme.colorScheme.onBackground)
                }
            },
        )
    }

    // ===== 长按撤回确认 =====
    val target = recallTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { recallTarget = null },
            title = {
                Text(text = "撤回消息", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Text(
                    text = "确定撤回这条消息吗? 撤回后群成员将看到「该消息已撤回」。",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            },
            confirmButton = {
                M3TextButton(onClick = {
                    recallTarget = null
                    scope.launch {
                        ApiClient.socialRecall(target.id)
                            .onSuccess {
                                onToast("已撤回")
                                messages = messages.map {
                                    if (it.id == target.id) {
                                        it.copy(content = "", isRecalled = true)
                                    } else {
                                        it
                                    }
                                }
                            }
                            .onFailure { e -> onToast(e.message ?: "撤回失败") }
                    }
                }) {
                    Text(text = "撤回", color = Color(0xFFE5484D))
                }
            },
            dismissButton = {
                M3TextButton(onClick = { recallTarget = null }) {
                    Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }
}

/** 单条消息: 自己靠右 (主色气泡), 别人靠左 (灰色气泡 + 头像 + 昵称 + 时间) */
@Composable
private fun MessageRow(
    msg: SocialMessage,
    mine: Boolean,
    canRecall: Boolean,
    onLongPress: () -> Unit,
) {
    // 撤回的消息: 居中灰字提示, 不显示气泡
    if (msg.isRecalled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "该消息已撤回",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            MessageAvatar(url = msg.avatar, name = msg.nickname)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 250.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!mine) {
                    Text(
                        text = msg.nickname.ifBlank { "群成员" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    if (msg.role == "admin") {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "管理员",
                                fontSize = 9.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = msg.timeText.ifBlank { msg.createdAt },
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (mine) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .pointerInput(msg.id, canRecall) {
                        detectTapGestures(
                            onLongPress = { if (canRecall) onLongPress() },
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = highlightMentions(
                        content = msg.content,
                        mentionColor = if (mine) {
                            Color(0xFFFFE082)
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                    ),
                    fontSize = 14.sp,
                    color = if (mine) {
                        MiuixTheme.colorScheme.onPrimary
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                )
            }
        }
        if (mine) {
            Spacer(Modifier.width(8.dp))
            MessageAvatar(url = msg.avatar, name = msg.nickname)
        }
    }
}

/** 把内容里的 @昵称 高亮 (AnnotatedString) */
private fun highlightMentions(content: String, mentionColor: Color): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        MENTION_REGEX.findAll(content).forEach { m ->
            if (m.range.first > last) append(content.substring(last, m.range.first))
            withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Medium)) {
                append(m.value)
            }
            last = m.range.last + 1
        }
        if (last < content.length) append(content.substring(last))
    }

@Composable
private fun MessageAvatar(url: String, name: String) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.take(1).ifBlank { "群" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

// ===================== 小工具 =====================

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
            textAlign = TextAlign.Center,
        )
    }
}

/** 轻量操作按钮 (不依赖 AccountScreen 的私有按钮组件) */
@Composable
private fun SmallActionButton(text: String, onClick: () -> Unit) {
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

/**
 * 页面是否处于前台 (ON_RESUME ~ ON_PAUSE)
 *
 * 不用 LifecycleResumeEffect (需要额外的 lifecycle-runtime-compose 依赖),
 * 这里从 LocalContext 拿 ComponentActivity 直接注册 LifecycleEventObserver,
 * Lifecycle / LifecycleEventObserver / LifecycleOwner 都来自项目已有的 lifecycle-common。
 */
@Composable
private fun rememberIsForeground(): Boolean {
    val context = LocalContext.current
    val owner = context as? LifecycleOwner
    var foreground by remember { mutableStateOf(true) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE -> foreground = false
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
        }
    }
    return foreground
}
