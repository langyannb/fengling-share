package com.fengling.share.ui.pm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.PmMessage
import com.fengling.share.data.PmPeer
import com.fengling.share.data.UserStore
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 私聊轮询间隔 (毫秒): 和群聊一样 3 秒 */
private const val PM_POLL_INTERVAL_MS = 3000L

/**
 * 私聊会话页 (接口契约 A 节 pm_messages / pm_send / pm_read / pm_recall)。
 *
 * 视觉与交互和群聊保持一致: 发文字、发图片、长按消息菜单(复制/撤回)、点图片放大、
 * 进会话自动已读、按 first_unread_id 定位未读、上翻用 before_id 加载更多、
 * 底部输入框 + 右下角「回到最新消息」。
 *
 * 两种进法:
 * - 从会话列表 / 用户主页「发消息」进来: 带 initialUserId (可能还没有会话, convId = 0)
 * - 从消息中心通知 (link = pm:<conv_id>:<msg_id>) 进来: 带 initialConvId + initialMessageId
 */
@Composable
fun PmChatScreen(
    initialConvId: Int = 0,
    initialUserId: Int = 0,
    /** 从通知点进来时要定位的那条消息 id (0 = 不定位) */
    initialMessageId: Int = 0,
    onBack: () -> Unit,
    onOpenWeb: ((url: String, title: String) -> Unit)? = null,
    onOpenUser: ((userId: Int) -> Unit)? = null,
    onToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val foreground = rememberPmChatForeground()

    var convId by remember { mutableStateOf(initialConvId) }
    var peerUserId by remember { mutableStateOf(initialUserId) }
    var peer by remember { mutableStateOf<PmPeer?>(null) }
    var myId by remember { mutableStateOf(0) }

    var messages by remember { mutableStateOf<List<PmMessage>>(emptyList()) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf("") }
    var hasMoreBefore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    var atBottom by remember { mutableStateOf(true) }
    var newWhileAway by remember { mutableStateOf(0) }
    var autoScrollPending by remember { mutableStateOf(false) }
    var settleWithoutAnimation by remember { mutableStateOf(false) }
    /** 负值 = 没有待补偿的前插滚动 */
    var pendingScrollTo by remember { mutableStateOf(-1) }

    /**
     * 首次要定位的消息 id:
     * - 从通知点进来: 用通知带的 msg_id (优先)
     * - 否则会话里还有未读: 用 first_unread_id, 让第一条未读落在屏幕上方
     */
    var locateId by remember { mutableStateOf(initialMessageId) }
    var highlightId by remember { mutableStateOf(0) }
    var unreadAnchorId by remember { mutableStateOf(0) }

    var previewImage by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<PmMessage?>(null) }
    var recallTarget by remember { mutableStateOf<PmMessage?>(null) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    /** 已读上报去重: 记住最近一次上报的 last_id, 避免重复请求 */
    var lastReadReported by remember { mutableStateOf(0) }

    fun isStuckToBottom(): Boolean {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
        if (lastVisible < messages.size - 1) return false
        return lastVisible >= messages.lastIndex - 1
    }

    LaunchedEffect(listState) {
        snapshotFlow { isStuckToBottom() }.collect { bottom ->
            atBottom = bottom
            if (bottom) newWhileAway = 0
        }
    }

    /** 回到最新消息: 滚到底部 + 清掉上翻期间累积的新消息角标 */
    fun jumpToLatest() {
        val last = messages.lastIndex
        if (last >= 0) scope.launch { runCatching { listState.animateScrollToItem(last) } }
        newWhileAway = 0
    }

    fun loadLatest(isRefresh: Boolean) {
        if (convId <= 0 && peerUserId <= 0) {
            loading = false
            return
        }
        scope.launch {
            if (isRefresh) refreshing = true else loading = true
            pendingScrollTo = -1
            if (isRefresh) hasMoreBefore = true
            settleWithoutAnimation = true
            val around = locateId
            ApiClient.pmMessages(
                convId = convId,
                userId = if (convId <= 0) peerUserId else 0,
                limit = 30,
                aroundId = around,
            )
                .onSuccess { page ->
                    if (page.convId > 0) convId = page.convId
                    if (page.other != null) peer = page.other
                    if (page.other != null) peerUserId = page.other.id
                    myId = page.myId
                    var finalList = page.list.distinctBy { it.id }.sortedBy { it.id }
                    var finalHasMore = page.hasMoreBefore
                    // 会话列表上的未读数可能是旧的; 首次加载若还有未读, 以第一条未读为锚点重取一屏
                    val anchor = if (!isRefresh && around <= 0 && initialMessageId <= 0 &&
                        page.unread > 0 && page.firstUnreadId > 0
                    ) page.firstUnreadId else 0
                    if (anchor > 0) {
                        locateId = anchor
                        unreadAnchorId = anchor
                        ApiClient.pmMessages(convId = convId, limit = 30, aroundId = anchor)
                            .onSuccess { p2 ->
                                finalList = p2.list.distinctBy { it.id }.sortedBy { it.id }
                                finalHasMore = p2.hasMoreBefore
                            }
                            .onFailure { e -> onToast(e.userFriendlyMessage()) }
                    }
                    hasMoreBefore = finalHasMore
                    autoScrollPending = anchor <= 0 && locateId <= 0
                    messages = finalList
                    loadError = ""
                }
                .onFailure { e -> loadError = e.userFriendlyMessage() }
            if (isRefresh) refreshing = false else loading = false
        }
    }

    /**
     * 合并新消息: 按 id 去重 + 正序。
     * forceScroll = true 用于自己刚发出去的消息; 其余按「改写前」的贴底状态决定是否跟到底。
     */
    fun mergeNew(incoming: List<PmMessage>, forceScroll: Boolean = false) {
        if (incoming.isEmpty()) return
        val base = messages
        val merged = (base + incoming).distinctBy { it.id }.sortedBy { it.id }
        val added = merged.size - base.size
        if (added <= 0) return
        val stuck = forceScroll || isStuckToBottom()
        messages = merged
        if (stuck) {
            autoScrollPending = true
            newWhileAway = 0
        } else {
            newWhileAway += added
        }
    }

    /** 上翻加载更早一页 (before_id = 当前列表最小 id) */
    fun loadOlder(manual: Boolean = false) {
        if (loadingMore || loading || messages.isEmpty() || convId <= 0) return
        if (!hasMoreBefore) {
            if (manual) onToast("已经是最早的消息了")
            return
        }
        if (!manual && (locateId > 0 || pendingScrollTo >= 0)) return
        val minId = messages.minOfOrNull { it.id } ?: return
        if (minId <= 0) return
        val firstIndex = listState.firstVisibleItemIndex
        val headerBefore = if (hasMoreBefore) 1 else 0
        loadingMore = true
        scope.launch {
            ApiClient.pmMessages(convId = convId, limit = 30, beforeId = minId)
                .onSuccess { page ->
                    hasMoreBefore = page.hasMoreBefore
                    val older = page.list.filter { it.id < minId }
                    if (older.isNotEmpty()) {
                        val merged = (older + messages).distinctBy { it.id }.sortedBy { it.id }
                        val added = merged.size - messages.size
                        messages = merged
                        if (added > 0) {
                            val headerAfter = if (hasMoreBefore) 1 else 0
                            pendingScrollTo = (firstIndex + added + headerAfter - headerBefore)
                                .coerceAtLeast(0)
                        }
                    }
                }
                .onFailure { e -> onToast(e.userFriendlyMessage()) }
            loadingMore = false
        }
    }

    // 前插完成后补偿滚动 (等新列表组合完索引才有效)
    LaunchedEffect(messages.size, pendingScrollTo) {
        val target = pendingScrollTo
        if (target < 0) return@LaunchedEffect
        pendingScrollTo = -1
        runCatching { listState.scrollToItem(target) }
    }

    // 上滑到顶自动加载更早消息
    LaunchedEffect(locateId) {
        snapshotFlow {
            listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset == 0
        }.collect { atTop -> if (atTop) loadOlder() }
    }

    // 首屏只加载一次: convId 在加载成功后会被服务端回填, 用它当 key 会导致重复请求
    LaunchedEffect(Unit) { loadLatest(false) }

    // 3 秒轮询: 仅前台 + 会话已建立时
    LaunchedEffect(convId, foreground) {
        if (!foreground || convId <= 0) return@LaunchedEffect
        while (true) {
            delay(PM_POLL_INTERVAL_MS)
            val after = messages.maxOfOrNull { it.id } ?: 0
            ApiClient.pmMessages(convId = convId, afterId = after)
                .onSuccess { page -> mergeNew(page.list) }
        }
    }

    // 有新消息时滚到底部 (只有本来就贴底时才会触发)
    LaunchedEffect(messages.size, autoScrollPending) {
        if (!autoScrollPending) return@LaunchedEffect
        if (locateId > 0 || pendingScrollTo >= 0 || loadingMore) {
            autoScrollPending = false
            return@LaunchedEffect
        }
        autoScrollPending = false
        if (messages.isEmpty()) return@LaunchedEffect
        val target = messages.lastIndex
        runCatching {
            if (settleWithoutAnimation) {
                settleWithoutAnimation = false
                listState.scrollToItem(target)
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    // 定位到未读 / 通知带来的那条消息并高亮一下
    LaunchedEffect(messages.size, locateId) {
        val target = locateId
        if (target <= 0 || messages.isEmpty()) return@LaunchedEffect
        val idx = messages.indexOfFirst { it.id == target }
        if (idx < 0) return@LaunchedEffect
        highlightId = target
        runCatching { listState.scrollToItem(idx) }
        delay(2800)
        if (highlightId == target) highlightId = 0
    }

    // 进会话自动上报已读: 只在首屏加载完、且前台时上报, 同一 last_id 只报一次
    LaunchedEffect(convId, messages.lastOrNull()?.id, loading, foreground) {
        if (!foreground || convId <= 0 || loading) return@LaunchedEffect
        val last = messages.lastOrNull()?.id ?: return@LaunchedEffect
        if (last <= 0 || last == lastReadReported) return@LaunchedEffect
        lastReadReported = last
        ApiClient.pmRead(convId, last)
    }

    fun doSend() {
        if (sending) return
        val text = input.text.trim()
        if (text.isEmpty()) {
            onToast("消息内容不能为空")
            return
        }
        if (text.length > 500) {
            onToast("消息不能超过 500 个字")
            return
        }
        sending = true
        scope.launch {
            ApiClient.pmSend(
                toUser = if (convId <= 0) peerUserId else 0,
                convId = convId,
                content = text,
            )
                .onSuccess { res ->
                    if (res.convId > 0) convId = res.convId
                    input = TextFieldValue("")
                    keyboard?.hide()
                    val after = messages.maxOfOrNull { it.id } ?: 0
                    ApiClient.pmMessages(convId = convId, afterId = after)
                        .onSuccess { page -> mergeNew(page.list, forceScroll = true) }
                }
                .onFailure { e -> onToast(e.userFriendlyMessage()) }
            sending = false
        }
    }

    // 相册选图 → 读字节 → 上传 → 作为图片消息发出 (content 传空串)
    val pickChatImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    uploading = false
                    onToast("读取图片失败, 请换一张")
                } else {
                    val mime = context.contentResolver.getType(uri) ?: "image/*"
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                    ApiClient.uploadChatImage(bytes, "chat.$ext", mime)
                        .onSuccess { img ->
                            ApiClient.pmSend(
                                toUser = if (convId <= 0) peerUserId else 0,
                                convId = convId,
                                content = "",
                                image = img.url,
                                imageW = img.width,
                                imageH = img.height,
                            )
                                .onSuccess { res ->
                                    if (res.convId > 0) convId = res.convId
                                    val after = messages.maxOfOrNull { it.id } ?: 0
                                    ApiClient.pmMessages(convId = convId, afterId = after)
                                        .onSuccess { page -> mergeNew(page.list, forceScroll = true) }
                                }
                                .onFailure { e -> onToast(e.userFriendlyMessage()) }
                        }
                        .onFailure { e -> onToast(e.userFriendlyMessage()) }
                    uploading = false
                }
            }
        }
    }

    /** 复制一条消息的文字到剪贴板 */
    fun copyMessage(text: String) {
        if (text.isBlank()) {
            onToast("这条消息没有可复制的文字")
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm == null) {
            onToast("复制失败")
            return
        }
        cm.setPrimaryClip(ClipData.newPlainText("私聊消息", text))
        onToast("已复制")
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = peer?.displayName?.takeIf { it.isNotBlank() } ?: "私聊",
                onBack = onBack,
                // 对方的管理员标签 (防骗警示, 契约 F1)
                titleTags = peer?.tags ?: emptyList(),
                actions = {
                    val target = peer?.id ?: peerUserId
                    if (onOpenUser != null && target > 0) {
                        Text(
                            text = "主页",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { onOpenUser(target) },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(Modifier.weight(1f)) {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { loadLatest(true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        loading && messages.isEmpty() -> PmCenterHint("加载中…")
                        messages.isEmpty() -> PmCenterHint(
                            if (loadError.isNotBlank()) loadError
                            else "还没有聊过, 发条消息打个招呼吧",
                        )

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            if (hasMoreBefore) {
                                item(key = "load_older") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { loadOlder(manual = true) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (loadingMore) "加载更早的消息…"
                                            else "上滑或点这里加载更早的消息",
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        )
                                    }
                                }
                            }
                            items(messages, key = { it.id }) { msg ->
                                val mine = if (myId > 0) msg.userId == myId else msg.mine
                                PmMessageRow(
                                    msg = msg,
                                    mine = mine,
                                    peerName = peer?.displayName.orEmpty(),
                                    peerAvatar = peer?.avatar.orEmpty(),
                                    myName = UserStore.current?.displayName.orEmpty(),
                                    myAvatar = UserStore.current?.avatarUrl.orEmpty(),
                                    onLongPress = { actionTarget = msg },
                                    onAvatarTap = {
                                        val uid = if (mine) myId else msg.userId
                                        if (onOpenUser != null && uid > 0) onOpenUser(uid)
                                    },
                                    onImageTap = { url -> previewImage = url },
                                    highlight = highlightId == msg.id || unreadAnchorId == msg.id,
                                )
                            }
                        }
                    }
                }

                // 右下角「回到最新消息」
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && messages.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .clickable { jumpToLatest() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                                contentDescription = "回到最新消息",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (newWhileAway > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935))
                                    .clickable { jumpToLatest() }
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (newWhileAway > 99) "99+" else newWhileAway.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // ===== 底部输入区: 左边「+」(发图片), 中间输入框, 右边发送 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .clickable { plusMenuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "发送图片",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = plusMenuOpen,
                        onDismissRequest = { plusMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (uploading) "图片 (上传中…)" else "图片") },
                            enabled = !uploading && !sending,
                            onClick = {
                                plusMenuOpen = false
                                pickChatImage.launch("image/*")
                            },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { nv -> if (nv.text.length <= 500) input = nv },
                        placeholder = {
                            Text(
                                text = "说点什么…",
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
                Card(
                    onClick = { if (!uploading && !sending) doSend() },
                    cornerRadius = 12.dp,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                uploading -> "上传中"
                                sending -> "发送中"
                                else -> "发送"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    // ===== 长按消息: 复制 / 撤回 =====
    val acting = actionTarget
    if (acting != null) {
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = {
                Text(
                    text = "消息操作",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    PmActionRow(text = "放大查看图片", danger = false, onClick = {
                        if (acting.image.isNotBlank()) previewImage = acting.image else onToast("这条消息没有图片")
                        actionTarget = null
                    })
                    PmActionRow(text = "复制", danger = false, onClick = {
                        copyMessage(acting.content)
                        actionTarget = null
                    })
                    if (!acting.isRecalled && acting.mine) {
                        PmActionRow(text = "撤回", danger = true, onClick = {
                            actionTarget = null
                            recallTarget = acting
                        })
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                M3TextButton(onClick = { actionTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    // ===== 撤回确认 =====
    val recalling = recallTarget
    if (recalling != null) {
        AlertDialog(
            onDismissRequest = { recallTarget = null },
            title = {
                Text(
                    text = "撤回消息",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                Text(
                    text = "撤回后对方就看不到这条消息了 (只能撤回 5 分钟内的消息)",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            },
            confirmButton = {
                M3TextButton(onClick = {
                    val target = recalling
                    recallTarget = null
                    scope.launch {
                        ApiClient.pmRecall(target.id)
                            .onSuccess {
                                messages = messages.map {
                                    if (it.id == target.id) {
                                        it.copy(content = "", image = "", isRecalled = true)
                                    } else {
                                        it
                                    }
                                }
                                onToast("已撤回")
                            }
                            .onFailure { e -> onToast(e.userFriendlyMessage()) }
                    }
                }) {
                    Text("撤回", color = Color(0xFFE5484D))
                }
            },
            dismissButton = {
                M3TextButton(onClick = { recallTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    // ===== 点图片: 全屏查看 (点任意处 / 右上角关闭) =====
    if (previewImage.isNotBlank()) {
        Dialog(
            onDismissRequest = { previewImage = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .pointerInput(Unit) {
                        detectTapGestures { previewImage = "" }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = previewImage,
                    contentDescription = "查看大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
                Text(
                    text = "关闭",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .clickable { previewImage = "" },
                )
            }
        }
    }
}

/** 一条私聊消息的气泡行 (复刻群聊 MessageRow 的视觉, 去掉 @ / 引用 / 群管理员相关逻辑) */
@Composable
private fun PmMessageRow(
    msg: PmMessage,
    mine: Boolean,
    peerName: String,
    peerAvatar: String,
    myName: String,
    myAvatar: String,
    onLongPress: () -> Unit,
    onAvatarTap: () -> Unit,
    onImageTap: (String) -> Unit,
    highlight: Boolean = false,
) {
    if (msg.isRecalled) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "该消息已撤回",
                fontSize = 12.sp,
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
            Box(
                modifier = Modifier.pointerInput(msg.userId) {
                    detectTapGestures(onTap = { onAvatarTap() })
                },
            ) {
                PmAvatar(name = peerName, url = peerAvatar)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        highlight -> MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
                        mine -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .pointerInput(msg.id) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (msg.image.isNotBlank()) {
                val ratio = if (msg.imageW > 0 && msg.imageH > 0) {
                    msg.imageW.toFloat() / msg.imageH.toFloat()
                } else {
                    4f / 3f
                }
                AsyncImage(
                    model = msg.image,
                    contentDescription = "图片消息",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(if (ratio >= 1f) 200.dp else (200f * ratio).dp)
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(10.dp))
                        .pointerInput(msg.id) {
                            detectTapGestures(
                                onTap = { onImageTap(msg.image) },
                                onLongPress = { onLongPress() },
                            )
                        },
                )
                if (msg.content.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
            if (msg.content.isNotBlank()) {
                Text(
                    text = msg.content,
                    fontSize = 14.sp,
                    color = if (mine) {
                        MiuixTheme.colorScheme.onPrimary
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = pmMessageTime(msg.createdAt),
                fontSize = 10.sp,
                color = if (mine) {
                    MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                },
            )
        }

        if (mine) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { onAvatarTap() })
                },
            ) {
                PmAvatar(name = myName, url = myAvatar)
            }
        }
    }
}

/** 消息里的时间只显示 HH:mm */
private fun pmMessageTime(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw) ?: return ""
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        ""
    }
}

/** 私聊头像 (圆形, 没图用首字, 再没有用「铃」) */
@Composable
private fun PmAvatar(name: String, url: String) {
    val initial = name.take(1).ifBlank { "铃" }
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
                contentDescription = "头像",
                modifier = Modifier.size(34.dp).clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** 长按菜单里的一行 */
@Composable
private fun PmActionRow(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onBackground,
        )
    }
}

/** 空态 / 加载态提示 */
@Composable
private fun PmCenterHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/** 页面是否在前台 (后台不轮询) */
@Composable
private fun rememberPmChatForeground(): Boolean {
    val context = LocalContext.current
    val owner = context as? LifecycleOwner
    var foreground by remember { mutableStateOf(true) }
    if (owner != null) {
        DisposableEffect(owner) {
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
