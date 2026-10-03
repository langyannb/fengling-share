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
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
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

/** 消息里的链接: 点一下用内置浏览器打开 */
private val URL_REGEX = Regex("https?://[^\\s@，。；、）)\"]+")

/** 聊天页顶栏「三条横杠」菜单 / 公告弹框的共享状态 (顶栏在 SocialScreen, 聊天内容在 ChatView) */
private class ChatMenuState {
    var showNoticeViewer by mutableStateOf(false)
    var showNoticeEditor by mutableStateOf(false)
    var draft by mutableStateOf("")

    /** 当前群我是否开了消息免打扰 */
    var muted by mutableStateOf(false)
}

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
/**
 * 未登录时的群组占位页: 图标 + 文案 + 「去登录 / 注册」按钮。
 *
 * 底部 tab 的「群组」在未登录时会被拦到登录页, 这里兜住
 * 「从通知点进某个群」这类直达路径, 避免出现一片空白。
 */
@Composable
private fun LoginRequiredView(onBack: (() -> Unit)?, onNeedLogin: (() -> Unit)?) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { if (onBack != null) AppTopBar(title = "群组", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Forum,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "登录后加入群聊",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "登录后可以参与群聊、@ 提醒他人, 并接收群消息通知",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            if (onNeedLogin != null) {
                SmallActionButton(text = "去登录 / 注册", onClick = onNeedLogin)
            }
        }
    }
}

fun SocialScreen(
    /** 为 null 时作为底部 tab 常驻页使用 (顶部不显示返回按钮) */
    onBack: (() -> Unit)? = null,
    /** 指定群 id: 进入后自动打开该群 (从「群组」tab 点进来时用) */
    initialGroupId: Int? = null,
    /** 非空时点群组交给外部导航 (tab 模式全屏打开聊天页); 为空则页内切换 */
    onOpenGroup: ((SocialGroup) -> Unit)? = null,
    /** 从通知点进来时定位的消息 id (0 = 不定位) */
    initialMessageId: Int = 0,
    /** 从「群公告更新」通知点进来时直接弹出公告 */
    openNotice: Boolean = false,
    /** 消息里的链接: 交给内置浏览器打开 */
    onOpenWeb: ((url: String, title: String) -> Unit)? = null,
    /** 未登录时点「去登录」的回调 (跳账号页登录/注册) */
    onNeedLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 未登录: 群组内容一律不给看, 直接引导去登录 (用户 2026-10-04 要求)
    if (!UserStore.isLoggedIn()) {
        LoginRequiredView(onBack = onBack, onNeedLogin = onNeedLogin)
        return
    }

    var groups by remember { mutableStateOf<List<SocialGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var currentGroup by remember { mutableStateOf<SocialGroup?>(null) }
    // 顶栏「三条横杠」菜单与公告弹框的状态 (聊天页顶栏在这里, 内容在 ChatView)
    val chatMenu = remember { ChatMenuState() }
    // 群公告通知点进来: 直接把公告弹框打开
    LaunchedEffect(Unit) { if (openNotice) chatMenu.showNoticeViewer = true }
    var refreshTick by remember { mutableStateOf(0) }

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
                    actions = {
                        // 右上角「三条横杠」菜单 (和 QQ 群一样的入口): 看公告 / 发公告 / 刷新
                        var menuOpen by remember { mutableStateOf(false) }
                        val admin = UserStore.current?.role == "admin"
                        // 进群时同步该群的免打扰状态
                        LaunchedEffect(g.id) { chatMenu.muted = g.muted }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { menuOpen = true }
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "更多",
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (g.notice.isBlank()) "群公告 (暂无)" else "查看群公告") },
                                onClick = {
                                    menuOpen = false
                                    chatMenu.showNoticeViewer = true
                                },
                            )
                            if (admin) {
                                DropdownMenuItem(
                                    text = { Text(if (g.notice.isBlank()) "发布群公告" else "编辑群公告") },
                                    onClick = {
                                        menuOpen = false
                                        chatMenu.draft = g.notice
                                        chatMenu.showNoticeEditor = true
                                    },
                                )
                            }
                            // 消息免打扰: 和 QQ/微信一样, 开了之后普通消息不再提醒,
                            // 但 @我 和群公告这些「重要的」还是会提醒
                            DropdownMenuItem(
                                text = {
                                    Text(if (chatMenu.muted) "消息免打扰: 已开启" else "消息免打扰: 已关闭")
                                },
                                onClick = {
                                    menuOpen = false
                                    val next = !chatMenu.muted
                                    chatMenu.muted = next
                                    // 立刻在列表上也反映出来
                                    currentGroup = currentGroup?.copy(muted = next)
                                    scope.launch {
                                        ApiClient.socialMuteSet(g.id, next)
                                            .onSuccess { on ->
                                                chatMenu.muted = on
                                                currentGroup = currentGroup?.copy(muted = on)
                                                groups = groups.map { row ->
                                                    if (row.id == g.id) row.copy(muted = on) else row
                                                }
                                            }
                                            .onFailure { e -> Toast.makeText(context, e.message ?: "设置失败", Toast.LENGTH_SHORT).show() }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("刷新消息") },
                                onClick = {
                                    menuOpen = false
                                    refreshTick++
                                },
                            )
                        }
                    },
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
                    menu = chatMenu,
                    refreshTick = refreshTick,
                    locateMessageId = initialMessageId,
                    onOpenWeb = onOpenWeb,
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
                    if (group.muted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = "消息免打扰",
                            tint = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
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
    menu: ChatMenuState,
    refreshTick: Int,
    /** 从通知点进来时要定位的消息 id (0 = 不定位) */
    locateMessageId: Int = 0,
    onOpenWeb: ((url: String, title: String) -> Unit)?,
    onToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val foreground = rememberIsForeground()

    var messages by remember { mutableStateOf<List<SocialMessage>>(emptyList()) }
    // 用 TextFieldValue 而不是 String: @ 插入后要把光标放到插入文本之后
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var recallTarget by remember { mutableStateOf<SocialMessage?>(null) }
    // 长按消息弹出的操作菜单 (引用 / 撤回)
    var actionTarget by remember { mutableStateOf<SocialMessage?>(null) }
    // 正在引用回复的那条消息 (null = 普通发送)
    var quoteTarget by remember { mutableStateOf<SocialMessage?>(null) }
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 群公告默认折叠两行, 点一下展开全文
    var noticeExpanded by remember { mutableStateOf(false) }
    // 群公告本地副本: 管理员在客户端改完立刻生效, 不必等下次拉群资料
    var noticeText by remember(group.id) { mutableStateOf(group.notice) }
    var noticeSaving by remember { mutableStateOf(false) }
    /** 是否通过选择器选了「所有人」(@所有人) */
    var atAllPicked by remember { mutableStateOf(false) }
    var showMentionPicker by remember { mutableStateOf(false) }
    var mentionMembers by remember { mutableStateOf<List<SocialGroupMember>>(emptyList()) }
    var mentionLoading by remember { mutableStateOf(false) }
    /** @ 选择器里的搜索关键字 */
    var mentionQuery by remember { mutableStateOf("") }
    /** 通过选择器 @ 到的人: 直接记 userId (昵称可能重名) */
    var pickedAt by remember { mutableStateOf<List<Int>>(emptyList()) }
    /** 待定位的消息 id: 首次加载会以它为中心取一屏, 定位完清零 (避免每次刷新都跳) */
    var locateId by remember { mutableStateOf(locateMessageId) }
    /** 正在高亮闪烁的消息 id (定位到的消息会给个底色) */
    var highlightId by remember { mutableStateOf(0) }
    // 点击头像: 管理员打开成员操作面板 (可以 @他 / 禁言 / 解除禁言)
    var memberTarget by remember { mutableStateOf<SocialMessage?>(null) }
    var memberInfo by remember { mutableStateOf<SocialGroupMember?>(null) }
    var memberLoading by remember { mutableStateOf(false) }
    /** 选择的禁言时长 (分钟), 0 表示永久 */
    var muteMinutes by remember { mutableStateOf(60) }
    var muteReason by remember { mutableStateOf("") }
    var muteSaving by remember { mutableStateOf(false) }

    val isAdmin = me != null && me.role == "admin"

    fun loadLatest(isRefresh: Boolean) {
        scope.launch {
            if (isRefresh) refreshing = true else loading = true
            val around = locateId
            ApiClient.socialMessages(group.id, limit = 30, aroundId = around)
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

    // 顶栏菜单里的「刷新消息」
    LaunchedEffect(refreshTick) { if (refreshTick > 0) loadLatest(false) }

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
        // 从通知点进来定位消息时不要抢滚动
        if (locateId > 0) return@LaunchedEffect
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    // 从通知点进来: 滚到那条消息并高亮一下, 然后恢复正常
    LaunchedEffect(messages.size, locateId) {
        val target = locateId
        if (target <= 0 || messages.isEmpty()) return@LaunchedEffect
        val idx = messages.indexOfFirst { it.id == target }
        if (idx < 0) return@LaunchedEffect
        highlightId = target
        runCatching { listState.animateScrollToItem(idx) }
        locateId = 0
        kotlinx.coroutines.delay(2800)
        highlightId = 0
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

    /**
     * 把 @昵称 插到输入框当前光标处, 并把光标移到插入文本之后。
     * 用户自己敲下的 '@' 会被这次插入替换掉, 避免出现 "@@昵称"。
     */
    fun insertMention(m: SocialGroupMember) {
        val cur = input
        val text = cur.text
        var start = cur.selection.start.coerceIn(0, text.length)
        val end = cur.selection.end.coerceIn(0, text.length)
        if (start > 0 && text[start - 1] == '@') start -= 1
        val insert = "@" + m.nickname + " "
        val newText = (text.substring(0, start) + insert + text.substring(end)).take(500)
        val cursor = (start + insert.length).coerceAtMost(newText.length)
        input = TextFieldValue(text = newText, selection = TextRange(cursor))
        pickedAt = (pickedAt + m.id).distinct()
    }

    /** 管理员: 插入 @所有人 (全体提醒) */
    fun insertMentionAll() {
        val cur = input
        val text = cur.text
        var start = cur.selection.start.coerceIn(0, text.length)
        val end = cur.selection.end.coerceIn(0, text.length)
        if (start > 0 && text[start - 1] == '@') start -= 1
        val insert = "@所有人 "
        val newText = (text.substring(0, start) + insert + text.substring(end)).take(500)
        val cursor = (start + insert.length).coerceAtMost(newText.length)
        input = TextFieldValue(text = newText, selection = TextRange(cursor))
        atAllPicked = true
    }

    /** 让输入框拿到焦点并弹出键盘 (长按头像 @ / 引用回复之后直接就能打字) */
    fun focusInput() {
        inputFocus.requestFocus()
        keyboard?.show()
    }

    /** 引用回复: 记住被引用的消息, 自动 @ 对方, 并把焦点交给输入框 */
    fun quoteMessage(m: SocialMessage) {
        quoteTarget = m
        insertMention(
            SocialGroupMember(
                id = m.userId,
                nickname = m.nickname,
                username = "",
                avatar = m.avatar,
                role = m.role,
            ),
        )
        focusInput()
    }

    /** 打开 @ 选择器 (首次打开时拉取成员候选) */
    fun openMentionPicker() {
        mentionQuery = ""
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

    /**
     * 点击对方头像: 管理员打开「成员操作」面板可以禁言, 普通成员直接 @ 他。
     * 禁言和 QQ 群一致, 只对当前群生效 (后台「用户管理」里则是全站禁言);
     * 被禁言的人仍然能看消息, 只是不能在本群发言。
     */
    fun openMemberPanel(msg: SocialMessage) {
        if (!isAdmin) {
            insertMention(
                SocialGroupMember(
                    id = msg.userId,
                    nickname = msg.nickname,
                    username = "",
                    avatar = msg.avatar,
                    role = msg.role,
                ),
            )
            focusInput()
            onToast("已 @ " + msg.nickname.ifBlank { "群成员" })
            return
        }
        memberTarget = msg
        memberInfo = null
        muteMinutes = 60
        muteReason = ""
        memberLoading = true
        scope.launch {
            ApiClient.socialGroupMembers(group.id)
                .onSuccess { list ->
                    mentionMembers = list
                    memberInfo = list.firstOrNull { it.id == msg.userId }
                }
                .onFailure { e -> onToast(e.message ?: "读取成员信息失败") }
            memberLoading = false
        }
    }

    /** 管理员禁言 / 解除禁言: minutes 传 null 表示解除禁言 */
    fun doMute(target: SocialMessage, minutes: Int?) {
        if (muteSaving) return
        muteSaving = true
        scope.launch {
            if (minutes == null) {
                ApiClient.adminUserUnmute(target.userId, group.id)
                    .onSuccess { n ->
                        onToast(if (n > 0) "已解除禁言" else "该用户当前未被禁言")
                        memberInfo = memberInfo?.copy(muted = false, muteLeft = "", muteReason = "")
                    }
                    .onFailure { e -> onToast(e.message ?: "操作失败") }
            } else {
                val why = muteReason.trim()
                ApiClient.adminUserMute(target.userId, minutes, group.id, why)
                    .onSuccess { left ->
                        onToast("已禁言 " + target.nickname.ifBlank { "该用户" } + " (" + left + ")")
                        memberInfo = memberInfo?.copy(muted = true, muteLeft = left, muteReason = why)
                    }
                    .onFailure { e -> onToast(e.message ?: "操作失败") }
            }
            muteSaving = false
        }
    }

    fun doSend() {
        val text = input.text.trim()
        if (text.isEmpty() || sending) return
        sending = true
        scope.launch {
            // 管理员 + (选过「所有人」或内容里写了 @所有人) -> 全体提醒
            val wantAll = isAdmin && (atAllPicked || text.contains("@所有人"))
            val quoteId = quoteTarget?.id ?: 0
            ApiClient.socialSend(
                group.id,
                text,
                (resolveMentionIds(text) + pickedAt).distinct(),
                atAll = wantAll,
                quoteId = quoteId,
            )
                .onSuccess {
                    input = TextFieldValue("")
                    pickedAt = emptyList()
                    atAllPicked = false
                    quoteTarget = null
                    val after = messages.maxOfOrNull { it.id } ?: 0
                    ApiClient.socialMessages(group.id, afterId = after)
                        .onSuccess { new -> mergeNew(new) }
                }
                .onFailure { e -> onToast(e.message ?: "发送失败") }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ===== 群公告 (管理员设置, 成员进群就能看到, 点击展开全文) =====
        if (noticeText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .clickable { noticeExpanded = !noticeExpanded }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "群公告",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isAdmin) {
                        Text(
                            text = "编辑",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                menu.draft = noticeText
                                menu.showNoticeEditor = true
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = if (noticeExpanded) "收起" else "展开",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                // 公告里的链接也能点开 (走内置浏览器); 点空白处仍然是展开/收起
                LinkText(
                    content = noticeText,
                    color = MiuixTheme.colorScheme.onBackground,
                    linkColor = MiuixTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    maxLines = if (noticeExpanded) Int.MAX_VALUE else 2,
                    onTap = { url ->
                        if (url != null) {
                            if (onOpenWeb != null) onOpenWeb(url, group.name) else onToast("没有可用的内置浏览器")
                        } else {
                            noticeExpanded = !noticeExpanded
                        }
                    },
                )
            }
        }

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
                                onLongPress = { actionTarget = msg },
                                highlight = highlightId == msg.id,
                                // 消息里的链接: 用内置浏览器打开
                                onOpenLink = { url ->
                                    if (onOpenWeb != null) {
                                        onOpenWeb(url, group.name)
                                    } else {
                                        onToast("没有可用的内置浏览器")
                                    }
                                },
                                onAvatarTap = { openMemberPanel(msg) },
                                // 长按对方头像 = @ 他
                                onAvatarLongPress = {
                                    insertMention(
                                        SocialGroupMember(
                                            id = msg.userId,
                                            nickname = msg.nickname,
                                            username = "",
                                            avatar = msg.avatar,
                                            role = msg.role,
                                        ),
                                    )
                                    focusInput()
                                    onToast("已 @ " + msg.nickname.ifBlank { "群成员" })
                                },
                            )
                        }
                    }
                }
            }
        }

        // ===== 引用预览 (长按消息 -> 引用) =====
        val quoting = quoteTarget
        if (quoting != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(30.dp)
                        .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "回复 " + quoting.nickname.ifBlank { "群成员" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = quoting.content.replace('\n', ' '),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "取消",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.clickable { quoteTarget = null },
                )
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
            // 管理员: 没有公告时也能从这里写一条
            if (isAdmin && noticeText.isBlank()) {
                SmallActionButton(
                    text = "公告",
                    onClick = {
                        menu.draft = ""
                        menu.showNoticeEditor = true
                    },
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { nv ->
                        if (nv.text.length <= 500) {
                            // 刚敲下一个 '@' 就自动弹成员选择器 (选择器里还能搜索)
                            val c = nv.selection.start
                            val typedAt = nv.text.length > input.text.length && c > 0 &&
                                c <= nv.text.length && nv.text[c - 1] == '@'
                            input = nv
                            if (typedAt) openMentionPicker()
                        }
                    },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocus),
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

    // ===== 群公告编辑 (仅管理员) =====
    // ===== 查看群公告 (顶栏「三条横杠」菜单进入) =====
    if (menu.showNoticeViewer) {
        AlertDialog(
            onDismissRequest = { menu.showNoticeViewer = false },
            title = {
                Text(
                    text = group.name + " · 群公告",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                LinkText(
                    content = noticeText.ifBlank { "群主和管理员还没有发布公告" },
                    color = if (noticeText.isBlank()) {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                    linkColor = MiuixTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    onTap = { url ->
                        if (url != null && onOpenWeb != null) {
                            menu.showNoticeViewer = false
                            onOpenWeb(url, group.name)
                        } else if (url != null) {
                            onToast("没有可用的内置浏览器")
                        }
                    },
                )
            },
            confirmButton = {
                M3TextButton(onClick = { menu.showNoticeViewer = false }) {
                    Text(text = "知道了", color = MiuixTheme.colorScheme.primary)
                }
            },
        )
    }

    if (menu.showNoticeEditor) {
        AlertDialog(
            onDismissRequest = { menu.showNoticeEditor = false },
            title = {
                Text(text = "群公告", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Column {
                    Text(
                        text = "发布后会显示在群聊顶部, 所有成员进群都能看到。留空则清空公告。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = menu.draft,
                        onValueChange = { if (it.length <= 500) menu.draft = it },
                        placeholder = { Text(text = "写点群规、活动或者通知…", fontSize = 13.sp) },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onBackground,
                        ),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = menu.draft.length.toString() + " / 500",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            },
            confirmButton = {
                M3TextButton(
                    onClick = {
                        if (noticeSaving) return@M3TextButton
                        noticeSaving = true
                        scope.launch {
                            ApiClient.socialSetNotice(group.id, menu.draft.trim())
                                .onSuccess {
                                    noticeText = menu.draft.trim()
                                    menu.showNoticeEditor = false
                                    onToast("公告已更新")
                                }
                                .onFailure { e -> onToast(e.message ?: "公告保存失败") }
                            noticeSaving = false
                        }
                    },
                ) {
                    Text(text = if (noticeSaving) "保存中…" else "保存", color = MiuixTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                M3TextButton(onClick = { menu.showNoticeEditor = false }) {
                    Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }

    // ===== @ 成员选择 =====
    if (showMentionPicker) {
        AlertDialog(
            onDismissRequest = { showMentionPicker = false },
            title = {
                Text(text = "选择要 @ 的人", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = mentionQuery,
                        onValueChange = { if (it.length <= 30) mentionQuery = it },
                        placeholder = {
                            Text(
                                text = "搜索昵称或用户名",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onBackground,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    val q = mentionQuery.trim().lowercase()
                    val filtered = if (q.isEmpty()) {
                        mentionMembers
                    } else {
                        mentionMembers.filter {
                            it.nickname.lowercase().contains(q) || it.username.lowercase().contains(q)
                        }
                    }
                    // 管理员可以 @所有人 (全体提醒)
                    val showAll = isAdmin && (q.isEmpty() || "所有人".contains(q) || "all".startsWith(q))
                    when {
                        mentionLoading -> Text(
                            text = "加载中…",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        else -> Column {
                            if (showAll) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            insertMentionAll()
                                            showMentionPicker = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "所有人",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "@所有人 (全体提醒)",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                }
                            }
                            if (mentionMembers.isEmpty()) {
                                Text(
                                    text = "暂无可 @ 的成员。\n群里有人发过言后, 他就会出现在这里。",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            } else if (filtered.isEmpty()) {
                                Text(
                                    text = "没有找到匹配「" + mentionQuery.trim() + "」的成员",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            } else LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(filtered, key = { it.id }) { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            insertMention(m)
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
                                    if (m.username.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "@" + m.username,
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        )
                                    }
                                    if (m.role == "admin") {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "管理员",
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                    // 已被管理员禁言的成员: 标出来, 顺便显示还剩多久
                                    if (m.muted) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "已禁言" + m.muteLeft.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                            fontSize = 11.sp,
                                            color = Color(0xFFE5484D),
                                        )
                                    }
                                }
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

    // ===== 长按消息: 引用 / 撤回 =====
    val acting = actionTarget
    if (acting != null) {
        val canRecall = !acting.isRecalled && (me != null && acting.userId == me.id || isAdmin)
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = {
                Text(text = "消息操作", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Text(
                    text = acting.nickname.ifBlank { "群成员" } + ": " +
                        acting.content.replace('\n', ' ').take(60),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                M3TextButton(onClick = {
                    actionTarget = null
                    quoteMessage(acting)
                }) {
                    Text(text = "引用", color = MiuixTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                Row {
                    if (canRecall) {
                        M3TextButton(onClick = {
                            actionTarget = null
                            recallTarget = acting
                        }) {
                            Text(text = "撤回", color = Color(0xFFE5484D))
                        }
                    }
                    M3TextButton(onClick = { actionTarget = null }) {
                        Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                }
            },
        )
    }

    // ===== 点击头像: 成员操作 (管理员可设置禁言时长) =====
    val memberActing = memberTarget
    if (memberActing != null) {
        val info = memberInfo
        val muted = info?.muted == true
        AlertDialog(
            onDismissRequest = { memberTarget = null },
            title = {
                Text(
                    text = "成员操作",
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    Text(
                        text = memberActing.nickname.ifBlank { "群成员" } +
                            if (memberActing.role == "admin") " · 管理员" else "",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            memberLoading -> "读取禁言状态中…"
                            muted -> "当前状态: 已禁言" +
                                (info?.muteLeft?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                                (info?.muteReason?.takeIf { it.isNotBlank() }?.let { ", 原因: $it" } ?: "")
                            else -> "当前状态: 正常"
                        },
                        fontSize = 12.sp,
                        color = if (muted) Color(0xFFE5484D) else MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    if (memberActing.role == "admin") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "管理员不能被禁言",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "禁言时长 (只在本群生效)",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        MUTE_OPTIONS.chunked(3).forEach { rowItems ->
                            Row {
                                rowItems.forEach { item ->
                                    val picked = muteMinutes == item.second
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (picked) {
                                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                } else {
                                                    MiuixTheme.colorScheme.surfaceContainerHigh
                                                },
                                            )
                                            .clickable { muteMinutes = item.second }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                    ) {
                                        Text(
                                            text = item.first,
                                            fontSize = 12.sp,
                                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (picked) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onBackgroundVariant
                                            },
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = muteReason,
                            onValueChange = { if (it.length <= 60) muteReason = it },
                            label = { Text("禁言原因 (可选, 会告知对方)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                if (memberActing.role != "admin") {
                    M3TextButton(
                        enabled = !muteSaving,
                        onClick = { doMute(memberActing, muteMinutes) },
                    ) {
                        Text(
                            text = if (muteSaving) "处理中…" else if (muted) "重新禁言" else "禁言",
                            color = Color(0xFFE5484D),
                        )
                    }
                }
            },
            dismissButton = {
                Row {
                    if (muted && memberActing.role != "admin") {
                        M3TextButton(
                            enabled = !muteSaving,
                            onClick = { doMute(memberActing, null) },
                        ) {
                            Text(text = "解除禁言", color = MiuixTheme.colorScheme.primary)
                        }
                    }
                    M3TextButton(onClick = {
                        val t = memberActing
                        memberTarget = null
                        insertMention(
                            SocialGroupMember(
                                id = t.userId,
                                nickname = t.nickname,
                                username = "",
                                avatar = t.avatar,
                                role = t.role,
                            ),
                        )
                        focusInput()
                    }) {
                        Text(text = "@他", color = MiuixTheme.colorScheme.primary)
                    }
                    M3TextButton(onClick = { memberTarget = null }) {
                        Text(text = "关闭", color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
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
    onAvatarLongPress: () -> Unit = {},
    onAvatarTap: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    /** 从通知定位过来的那条消息: 给个底色方便一眼看到 */
    highlight: Boolean = false,
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
            .background(if (highlight) Color(0x33FFB300) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            // 长按头像 @ 他; 点击头像 (管理员) 打开成员操作面板
            Box(
                modifier = Modifier.pointerInput(msg.userId) {
                    detectTapGestures(
                        onLongPress = { onAvatarLongPress() },
                        onTap = { onAvatarTap() },
                    )
                },
            ) {
                MessageAvatar(url = msg.avatar, name = msg.nickname)
            }
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
                    if (msg.at.contains(0)) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFF53F3F).copy(alpha = 0.14f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "@所有人",
                                fontSize = 9.sp,
                                color = Color(0xFFF53F3F),
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
            // 引用回复: 被引用的原消息 (昵称 + 内容)
            if (msg.quoteNickname.isNotBlank() || msg.quoteContent.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 250.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (mine) {
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MiuixTheme.colorScheme.surfaceContainerHigh
                            },
                        )
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = msg.quoteNickname.ifBlank { "群成员" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = msg.quoteContent.replace('\n', ' '),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // 链接要能点(走内置浏览器), 又不能抢掉长按撤回 —— 所以手势自己做在文本上:
                // onTextLayout 拿到排版结果, 把点击坐标换成字符偏移, 再查 URL 注解
                var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val shown = highlightMentions(
                    content = msg.content,
                    mentionColor = if (mine) {
                        Color(0xFFFFE082)
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                    linkColor = if (mine) {
                        Color(0xFFFFF3C4)
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                )
                Text(
                    text = shown,
                    fontSize = 14.sp,
                    color = if (mine) {
                        MiuixTheme.colorScheme.onPrimary
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                    onTextLayout = { textLayout = it },
                    modifier = Modifier.pointerInput(msg.id, canRecall) {
                        detectTapGestures(
                            onLongPress = { onLongPress() },
                            onTap = { pos ->
                                val lr = textLayout
                                if (lr != null) {
                                    val off = lr.getOffsetForPosition(pos).coerceIn(0, shown.length)
                                    shown.getStringAnnotations("URL", off, off)
                                        .firstOrNull()
                                        ?.let { ann -> onOpenLink(ann.item) }
                                }
                            },
                        )
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

/** 高亮 @昵称 与链接: @ 用 mentionColor, 链接用 linkColor 并打上 URL 注解 (点击可打开) */
private fun highlightMentions(
    content: String,
    mentionColor: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    var last = 0
    URL_REGEX.findAll(content).forEach { m ->
        if (m.range.first > last) {
            appendPlainWithMentions(content.substring(last, m.range.first), mentionColor)
        }
        pushStringAnnotation("URL", m.value)
        withStyle(
            SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append(m.value)
        }
        pop()
        last = m.range.last + 1
    }
    if (last < content.length) appendPlainWithMentions(content.substring(last), mentionColor)
}

/** 纯文本片段: 只把 @昵称 高亮上去 */
private fun AnnotatedString.Builder.appendPlainWithMentions(text: String, mentionColor: Color) {
    var last = 0
    MENTION_REGEX.findAll(text).forEach { m ->
        if (m.range.first > last) append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Medium)) {
            append(m.value)
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
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
/** 禁言时长选项: 文案 → 分钟数 (0 = 永久) */
private val MUTE_OPTIONS = listOf(
    "10 分钟" to 10,
    "1 小时" to 60,
    "1 天" to 1440,
    "7 天" to 10080,
    "30 天" to 43200,
    "永久" to 0,
)

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

/**
 * 可点击链接文本 (群公告 / 查看公告共用)
 *
 * Coil 之外这里不能用 ClickableText (会和长按手势打架), 所以自己处理手势:
 * onTextLayout 拿到排版结果, 把点击坐标换成字符偏移, 再查 highlightMentions 打好的 URL 注解。
 * onTap 收到 null 表示点在普通文字上, 调用方可以拿来做「展开/收起」。
 */
@Composable
private fun LinkText(
    content: String,
    color: Color,
    linkColor: Color,
    fontSize: TextUnit,
    onTap: ((String?) -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    mentionColor: Color = linkColor,
    modifier: Modifier = Modifier,
) {
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val shown = remember(content, mentionColor, linkColor) {
        highlightMentions(content = content, mentionColor = mentionColor, linkColor = linkColor)
    }
    Text(
        text = shown,
        fontSize = fontSize,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textLayout = it },
        modifier = modifier.pointerInput(shown.text, onTap) {
            if (onTap != null) {
                detectTapGestures(onTap = { pos ->
                    val lr = textLayout
                    val url = if (lr == null) {
                        null
                    } else {
                        val off = lr.getOffsetForPosition(pos).coerceIn(0, shown.length)
                        shown.getStringAnnotations("URL", off, off).firstOrNull()?.item
                    }
                    onTap(url)
                })
            }
        },
    )
}
