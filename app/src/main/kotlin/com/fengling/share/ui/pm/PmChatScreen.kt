package com.fengling.share.ui.pm

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
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
import com.fengling.share.data.MessageStream
import com.fengling.share.data.PmMessage
import com.fengling.share.data.StreamEvent
import com.fengling.share.data.PmPeer
import com.fengling.share.data.UserStore
import com.fengling.share.data.VideoProbe
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppGradientBackground
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.SendState
import com.fengling.share.ui.components.SendStatusIndicator
import com.fengling.share.ui.components.ZoomableImage
import com.fengling.share.ui.components.CleanedVideoPlaceholder
import com.fengling.share.ui.components.VideoBubble
import com.fengling.share.ui.components.VideoFullscreenDialog
import com.fengling.share.ui.components.VideoSendingBubble
import com.fengling.share.ui.components.glassStroke
import com.fengling.share.ui.components.ChatEmojiPanel
import com.fengling.share.ui.components.ChatPanelItem
import com.fengling.share.ui.components.ChatPlusPanel
import com.fengling.share.ui.components.bitmapToJpeg
import com.fengling.share.ui.components.linkify
import com.fengling.share.ui.components.normalizeUrl
import com.fengling.share.ui.lottery.LotteryScreen
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 私聊轮询间隔 (毫秒): 和群聊一样 3 秒 */
private const val PM_POLL_INTERVAL_MS = 2000L

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
    // v1.1.12 乐观发送: 本地临时消息 (发送中转圈 / 失败可点重发), 服务端返回后用真实消息替换
    var outgoing by remember { mutableStateOf<List<OutgoingPmMsg>>(emptyList()) }
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
    // Wave 2 (1.1.13) 视频消息: 全屏播放的视频地址 (空串 = 不显示)
    var fullscreenVideo by remember { mutableStateOf("") }
    // 服务端 video_config 开关 / 上限 (默认按「开」乐观处理, 拉配置失败也不让入口凭空消失)
    var videoEnabled by remember { mutableStateOf(true) }
    var videoMaxMb by remember { mutableStateOf(0) }
    var actionTarget by remember { mutableStateOf<PmMessage?>(null) }
    var recallTarget by remember { mutableStateOf<PmMessage?>(null) }
    // v1.1.12 契约第 6 条: 「+」面板 / 表情面板 (互斥, 都在输入栏下面弹出)
    var panelOpen by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    /**
     * v1.1.14: 私聊同款 —— 面板开着时返回键先收起面板, 面板收完了才退页。
     * 这一条注册得比下面那条「全屏大图 / 全屏播放」早 → 优先级更低,
     * 于是两者的先后天然是: 全屏预览 > 面板 > 退页。
     */
    BackHandler(enabled = panelOpen || emojiOpen) {
        if (emojiOpen) {
            emojiOpen = false
        } else {
            panelOpen = false
        }
    }
    // 抽奖界面 (「+」面板进入; 和群聊那边同一个 LotteryScreen)
    var showLottery by remember { mutableStateOf(false) }
    // 开面板时收键盘用 (原变量在第 4 条改造里删过, 这里补回来)
    val keyboard = LocalSoftwareKeyboardController.current
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

    // SSE 实时流: 只对「本会话」的消息立即 loadLatest(), 旁人的消息不打扰本页。
    // convId > 0 时按会话 id 匹配; 会话还没建立 (convId == 0) 时退回按对方 user id 匹配。
    // 断开重连 -> 也补一次 (断线窗口里可能漏了消息)。上面的 3 秒兜底轮询保留不动。
    // ⚠️ 必须放在局部函数 loadLatest() 之后 (Kotlin 局部函数先声明后使用)。
    LaunchedEffect(Unit) {
        MessageStream.events.collect { event ->
            when (event) {
                is StreamEvent.Pm -> {
                    val mine = if (event.convId > 0) event.convId == convId
                    else event.fromUser == peerUserId
                    if (mine) loadLatest(false)
                }
                StreamEvent.Reconnected -> loadLatest(false)
                else -> Unit
            }
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

    /**
     * Wave 2 (1.1.13) 视频消息: 两段式发送 —— 先上传拿直链 (带百分比进度 + 可取消), 再当消息发出去。
     * 与群聊同一套逻辑: 上传用专用长超时 client (100MB 要 50~70 秒), 失败/取消只标失败态可重发,
     * 重发时已经拿到直链 (uploadedUrl 非空) 就不再重传一遍。
     */
    suspend fun performSendVideo(localId: String) {
        val item = outgoing.firstOrNull { it.localId == localId } ?: return
        val uri = item.videoUri ?: return
        var url = item.uploadedUrl
        if (url.isBlank()) {
            val res = ApiClient.uploadChatVideo(
                context = context,
                uri = uri,
                videoW = item.videoW,
                videoH = item.videoH,
                videoDuration = item.videoDuration,
                videoSize = item.videoSize,
                filename = item.fileName,
                mime = item.mime,
                isCancelled = {
                    outgoing.firstOrNull { it.localId == localId }?.cancelRequested == true
                },
                // 回调在 OkHttp 写线程上: 转回主线程再改 Compose 状态
                onProgress = { pct ->
                    scope.launch {
                        outgoing = outgoing.map {
                            if (it.localId == localId) it.copy(progress = pct) else it
                        }
                    }
                },
            )
            val cancelled = outgoing.firstOrNull { it.localId == localId }?.cancelRequested == true
            val uploaded = res.getOrNull()
            if (uploaded == null || uploaded.url.isBlank()) {
                outgoing = outgoing.map {
                    if (it.localId == localId) {
                        it.copy(state = SendState.Failed, cancelRequested = false, progress = 0)
                    } else {
                        it
                    }
                }
                if (cancelled) onToast("已取消发送")
                return
            }
            url = uploaded.url
            outgoing = outgoing.map {
                if (it.localId == localId) it.copy(uploadedUrl = url, progress = 100) else it
            }
        }
        ApiClient.pmSend(
            toUser = if (convId <= 0) peerUserId else 0,
            convId = convId,
            content = item.text,
            video = url,
            videoW = item.videoW,
            videoH = item.videoH,
            videoDuration = item.videoDuration,
            videoSize = item.videoSize,
        )
            .onSuccess { res ->
                if (res.convId > 0) convId = res.convId
                outgoing = outgoing.filterNot { it.localId == localId }
                val after = messages.maxOfOrNull { it.id } ?: 0
                ApiClient.pmMessages(convId = convId, afterId = after)
                    .onSuccess { page -> mergeNew(page.list, forceScroll = true) }
            }
            .onFailure {
                outgoing = outgoing.map {
                    if (it.localId == localId) {
                        it.copy(state = SendState.Failed, cancelRequested = false)
                    } else {
                        it
                    }
                }
            }
    }

    /** 真正把一条本地临时消息发给服务端: 成功 = 移除本地占位 + 立刻拉真实消息, 失败 = 标成失败态 */
    suspend fun performSend(localId: String) {
        val item = outgoing.firstOrNull { it.localId == localId } ?: return
        // 视频走两段式 (先上传再发送); 文字 / 图片保持原来那条链路, 一个字节都不动
        if (item.videoUri != null) {
            performSendVideo(localId)
            return
        }
        ApiClient.pmSend(
            toUser = if (convId <= 0) peerUserId else 0,
            convId = convId,
            content = item.text,
        )
            .onSuccess { res ->
                if (res.convId > 0) convId = res.convId
                outgoing = outgoing.filterNot { it.localId == localId }
                val after = messages.maxOfOrNull { it.id } ?: 0
                ApiClient.pmMessages(convId = convId, afterId = after)
                    .onSuccess { page -> mergeNew(page.list, forceScroll = true) }
            }
            .onFailure {
                // 失败不打断 (不弹 toast): 只把这一条标成失败, 用户点红色感叹号重发
                outgoing = outgoing.map {
                    if (it.localId == localId) it.copy(state = SendState.Failed) else it
                }
            }
    }

    /** 点失败消息上的红色感叹号: 重发同一条 */
    fun retrySend(localId: String) {
        if (outgoing.none { it.localId == localId }) return
        outgoing = outgoing.map {
            if (it.localId == localId) it.copy(state = SendState.Sending) else it
        }
        scope.launch { performSend(localId) }
    }

    /**
     * v1.1.12 乐观发送: 发送按钮不再被「发送中」门控 (只看内容非空)。
     * 点下去立刻清空输入框 + 把本地临时消息挂到列表尾部 (转圈「发送中」), 服务端返回后换成真实消息。
     * 失败 (含服务端 2 秒 1 条的限流错误) 只把这一条标成失败态, 不弹 toast 打断, 点红色感叹号重发。
     * 契约第 4 条: 焦点与键盘都不动 —— 原来是 keyboard?.hide(), 现在发完照样能接着打字。
     */
    fun doSend() {
        val text = input.text.trim()
        if (text.isEmpty()) return
        if (text.length > 500) {
            onToast("消息不能超过 500 个字")
            return
        }
        val seq = PM_LOCAL_SEQ.incrementAndGet()
        val localId = "local-$seq"
        // 立刻: 清输入框 (重新抓住焦点, 键盘不收) + 本地临时消息追加到列表尾部
        input = TextFieldValue("")
        inputFocus.requestFocus()
        outgoing = outgoing + OutgoingPmMsg(
            localId = localId,
            text = text,
            state = SendState.Sending,
            message = PmMessage(
                // 本地临时消息用负 id: 永远不会和服务端 id 撞上 (拉新消息也是按 afterId > 0 拉)
                id = -seq,
                convId = convId,
                userId = myId,
                toUser = if (convId <= 0) peerUserId else 0,
                content = text,
                mine = true,
                createdAt = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.getDefault(),
                ).format(java.util.Date()),
            ),
        )
        scope.launch { performSend(localId) }
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

    /**
     * 契约第 6 条「拍摄」: 系统相机拍一张 → 缩略图压成 JPEG → 和相册同一条上传 + 发送链路。
     */
    val cameraShot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            uploading = true
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { bitmapToJpeg(bmp) }.getOrNull() }
                if (bytes == null || bytes.isEmpty()) {
                    uploading = false
                    onToast("读取照片失败, 请重拍一张")
                } else {
                    ApiClient.uploadChatImage(bytes, "chat.jpg", "image/jpeg")
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

    // ===================== Wave 2 (1.1.13) 视频消息: 选 / 拍 / 发送前校验 =====================

    /** 选到或拍完视频后的唯一入口: 发送前校验 (中文 Toast) + 立刻挂本地乐观气泡 */
    fun startVideoSend(uri: Uri) {
        if (!videoEnabled) {
            onToast("视频消息功能暂未开启")
            return
        }
        if (outgoing.any { it.videoUri != null && it.state == SendState.Sending }) {
            onToast("上一个视频还在上传, 稍等一下")
            return
        }
        scope.launch {
            val info = withContext(Dispatchers.IO) { VideoProbe.probe(context, uri) }
            val thumb = withContext(Dispatchers.IO) { VideoProbe.firstFrame(context, uri) }
            val limitMb = if (videoMaxMb > 0) videoMaxMb else 100
            // 1) 字节数 (服务端真实 max_mb, 不写死 100); 拿不到大小就交给服务端兜底
            if (info.sizeBytes > 0L && info.sizeBytes > limitMb.toLong() * 1024L * 1024L) {
                onToast("视频不能超过 " + limitMb + "MB")
                return@launch
            }
            // 2) 时长上限 5 分钟
            if (info.durationSec > VideoProbe.MAX_DURATION_SEC) {
                onToast("视频不能超过 5 分钟")
                return@launch
            }
            val seq = PM_LOCAL_SEQ.incrementAndGet()
            val localId = "local-$seq"
            outgoing = outgoing + OutgoingPmMsg(
                localId = localId,
                text = "",
                state = SendState.Sending,
                message = PmMessage(
                    // 本地占位: 负 id + msgType=video, 真正的 video 直链要等上传完才有
                    id = -seq,
                    convId = convId,
                    userId = myId,
                    toUser = if (convId <= 0) peerUserId else 0,
                    content = "",
                    msgType = "video",
                    video = "",
                    videoW = info.width,
                    videoH = info.height,
                    videoDuration = info.durationSec,
                    videoSize = info.sizeBytes,
                    mine = true,
                    createdAt = java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date()),
                ),
                videoUri = uri,
                videoW = info.width,
                videoH = info.height,
                videoDuration = info.durationSec,
                videoSize = info.sizeBytes,
                thumbnail = thumb,
                fileName = info.displayName.ifBlank { "chat.mp4" },
                mime = context.contentResolver.getType(uri) ?: "video/mp4",
            )
            panelOpen = false
            // 上传要走 50~70 秒: 丢到自己的协程里
            scope.launch { performSendVideo(localId) }
        }
    }

    /** 「相册选视频」: 优先系统照片选择器 (PickVisualMedia + VideoOnly), 不可用回退 GetContent(video 通配 mime) */
    val pickChatVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) startVideoSend(uri) }

    val pickChatVideoLegacy = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) startVideoSend(uri) }

    /** ACTION_VIDEO_CAPTURE 要求自己给输出 uri, 这里用 FileProvider 指到 cacheDir/video/ */
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }

    /** 「拍视频」回调: ROM 没相机 / 用户按返回 时 resultCode != RESULT_OK, 安静退出不崩 */
    val recordVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = cameraVideoUri
        cameraVideoUri = null
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            startVideoSend(uri)
        }
    }

    /** 点本地视频气泡上的「取消」: 只打个标记, 上传线程下次写块时自己中止 */
    fun cancelVideoSend(localId: String) {
        outgoing = outgoing.map {
            if (it.localId == localId) it.copy(cancelRequested = true) else it
        }
        onToast("正在取消…")
    }

    /** 「拍视频」入口: 建临时文件 → FileProvider uri → 交给系统录像机 */
    fun launchVideoCapture() {
        if (!videoEnabled) {
            onToast("视频消息功能暂未开启")
            return
        }
        val target = runCatching {
            val dir = java.io.File(context.cacheDir, "video").apply { mkdirs() }
            val file = java.io.File(dir, "rec_" + System.currentTimeMillis() + ".mp4")
            // authority 与 AndroidManifest 里的 ${applicationId}.fileprovider 一致
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull()
        if (target == null) {
            onToast("没有可用的相机")
            return
        }
        cameraVideoUri = target
        val intent = android.content.Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, target)
            addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("android.intent.extra.durationLimit", VideoProbe.MAX_DURATION_SEC)
        }
        val ok = runCatching { recordVideo.launch(intent) }.isSuccess
        if (!ok) {
            cameraVideoUri = null
            onToast("没有可用的相机")
        }
    }

    /** 「相册选视频」入口 */
    fun pickVideoFromGallery() {
        if (!videoEnabled) {
            onToast("视频消息功能暂未开启")
            return
        }
        val started = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                pickChatVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            } else {
                pickChatVideoLegacy.launch("video/*")
            }
        }.isSuccess
        if (!started) {
            val fallback = runCatching { pickChatVideoLegacy.launch("video/*") }.isSuccess
            if (!fallback) onToast("没有可用的相册")
        }
    }

    // 服务端视频开关 (无需鉴权): enabled=0 时面板里「视频 / 拍视频」两个入口直接不出现
    LaunchedEffect(Unit) {
        ApiClient.videoConfig().onSuccess {
            videoEnabled = it.enabled
            videoMaxMb = it.maxMb
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

    /**
     * 下载私聊里的图片并保存到相册 (MediaStore, Android 10+ 无需存储权限)。
     * 与群聊的保存逻辑完全一致: 相册里归到「风铃分享库」相簿。
     */
    fun saveImageToGallery(url: String) {
        if (url.isBlank()) {
            onToast("这条消息没有图片")
            return
        }
        scope.launch {
            val bytes = ApiClient.downloadChatImage(url).getOrElse { e ->
                onToast(e.message?.takeIf { it.isNotBlank() } ?: "图片下载失败")
                return@launch
            }
            val mime = when {
                url.endsWith(".png", true) -> "image/png"
                url.endsWith(".webp", true) -> "image/webp"
                else -> "image/jpeg"
            }
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            // IO 线程 insert + 写字节; 出错只把文案带回来, Toast 一定在主线程弹
            val errMsg = withContext(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "fl_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext,
                        )
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/风铃分享库",
                        )
                    }
                    val cr = context.contentResolver
                    val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("相册写入失败")
                    cr.openOutputStream(uri)?.use { out -> out.write(bytes) } ?: error("相册写入失败")
                }.exceptionOrNull()?.let { it.message?.takeIf { m -> m.isNotBlank() } ?: "未知错误" }
            }
            if (errMsg == null) onToast("已保存到相册") else onToast("保存失败: " + errMsg)
        }
    }

    // v1.1.10: 跟手动画交回框架 (NavHost 的 SeekableTransitionState 会按手势进度 seek pop 转场)。
    // 页内有全屏大图预览时: 返回键先把大图关掉, 再退页 (和系统返回语义一致)
    // Wave 2: 全屏播放 / 全屏大图都先吃掉返回键, 再退页 (和系统返回语义一致)
    BackHandler(enabled = previewImage.isNotBlank() || fullscreenVideo.isNotBlank()) {
        if (fullscreenVideo.isNotBlank()) fullscreenVideo = "" else previewImage = ""
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
                // 契约 C: 页面级柔和渐变底 (静态绘制, 零模糊开销), 给气泡/图片垫层次
                AppGradientBackground()
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
                        itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                            val mine = if (myId > 0) msg.userId == myId else msg.mine
                            // 契约 C: 连续同作者消息收紧间距; 进场只淡入 (placementSpec = null,
                            // 否则「加载更早的消息」往顶部插数据时整列被推着走)
                            val prev = messages.getOrNull(index - 1)
                            val compact = prev != null && !prev.isRecalled && !msg.isRecalled &&
                                prev.userId == msg.userId
                            PmMessageRow(
                                modifier = pmMessageItemEnter(),
                                compact = compact,
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
                                // Wave 2: 点视频气泡 = 全屏播放
                                onVideoTap = { url -> fullscreenVideo = url },
                                // 消息里的链接: 用内置浏览器打开 (无协议的在打开前补 https://)
                                onOpenLink = { url ->
                                    if (onOpenWeb != null) {
                                        onOpenWeb(
                                            normalizeUrl(url),
                                            peer?.displayName.orEmpty().ifBlank { "私聊" },
                                        )
                                    } else {
                                        onToast("没有可用的内置浏览器")
                                    }
                                },
                                highlight = highlightId == msg.id || unreadAnchorId == msg.id,
                            )
                        }

                            // v1.1.12 乐观发送: 本地临时消息挂在列表最尾部 —— 转圈 = 发送中, 红色感叹号 = 失败可重发
                            items(outgoing, key = { it.localId }) { item ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Wave 2: 视频本地占位自己画 (缩略图 + 圆形进度 + 可取消),
                                    // 这一刻还没有服务端直链, 走不了 PmMessageRow 的 VideoBubble 分支
                                    if (item.videoUri != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            VideoSendingBubble(
                                                thumbnail = item.thumbnail,
                                                progress = item.progress,
                                                videoW = item.videoW,
                                                videoH = item.videoH,
                                                durationSec = item.videoDuration,
                                                cancellable = item.state == SendState.Sending,
                                                // 100% 之后还有「服务端落盘」一段, 文案切成「服务器处理中…」
                                                serverProcessing = item.progress >= 100 && item.state == SendState.Sending,
                                                onCancel = { cancelVideoSend(item.localId) },
                                                onLongPress = {},
                                            )
                                        }
                                    } else {
                                    PmMessageRow(
                                        msg = item.message,
                                        // 本地临时消息一定是自己发的
                                        mine = true,
                                        peerName = peer?.displayName.orEmpty().ifBlank { "对方" },
                                        peerAvatar = peer?.avatar.orEmpty(),
                                        myName = UserStore.current?.displayName.orEmpty(),
                                        myAvatar = UserStore.current?.avatarUrl.orEmpty(),
                                        onLongPress = {},
                                        onAvatarTap = {},
                                        onImageTap = { url -> previewImage = url },
                                        onVideoTap = { url -> fullscreenVideo = url },
                                        highlight = false,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 14.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        SendStatusIndicator(
                                            state = item.state,
                                            onRetry = { retrySend(item.localId) },
                                        )
                                    }
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
                                .border(1.dp, glassStroke(), CircleShape)
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
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 契约第 6 条: QQ 那种「+」圆钮, 点开在下面弹出面板 (相册/拍摄/抽奖/表情/关闭)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            emojiOpen = false
                            panelOpen = !panelOpen
                            // 开面板的同时收起键盘
                            if (panelOpen) keyboard?.hide()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (panelOpen) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (panelOpen) "收起更多功能" else "更多功能",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
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
                        // 契约 C: 药丸输入框
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocus)
                            // 用户自己点回输入框: 面板让位给键盘
                            .onFocusChanged { st ->
                                if (st.isFocused) {
                                    panelOpen = false
                                    emojiOpen = false
                                }
                            },
                    )
                }
                Spacer(Modifier.width(8.dp))
                // 契约第 6 条: 有文字 = 发送胶囊 (只判断内容非空); 没文字 = 表情圆钮, 发送隐藏
                if (input.text.isNotEmpty()) {
                    Card(
                        onClick = { doSend() },
                        cornerRadius = 12.dp,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "发送",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                panelOpen = false
                                emojiOpen = !emojiOpen
                                if (emojiOpen) keyboard?.hide()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEmotions,
                            contentDescription = "表情",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // ===== 「+」面板 (v1.1.12 契约第 6 条): 只放 App 真有的功能 =====
            val panelItems = buildList {
                // 相册: 原来的「图片」入口, 行为不变
                add(
                    ChatPanelItem("album", "相册", Icons.Filled.PhotoLibrary) {
                        panelOpen = false
                        pickChatImage.launch("image/*")
                    },
                )
                // 拍摄: 系统相机拍一张, 走和相册同一条上传链路
                add(
                    ChatPanelItem("camera", "拍摄", Icons.Filled.PhotoCamera) {
                        panelOpen = false
                        if (uploading) {
                            onToast("上一张还在上传, 稍等一下")
                        } else {
                            runCatching { cameraShot.launch(null) }
                                .onFailure { onToast("没有可用的相机") }
                        }
                    },
                )
                // Wave 2 视频: 相册选视频 / 拍视频。原来的「相册 / 拍摄」是图片链路,
                // 契约要求「现有功能入口要保留可点」→ 视频另起两个入口, 不动图片那两个
                if (videoEnabled) {
                    add(
                        ChatPanelItem("video", "视频", Icons.Filled.VideoLibrary) {
                            panelOpen = false
                            pickVideoFromGallery()
                        },
                    )
                    add(
                        ChatPanelItem("videocam", "拍视频", Icons.Filled.Videocam) {
                            panelOpen = false
                            if (outgoing.any { it.videoUri != null && it.state == SendState.Sending }) {
                                onToast("上一个视频还在上传, 稍等一下")
                            } else {
                                launchVideoCapture()
                            }
                        },
                    )
                }
                add(
                    ChatPanelItem("lottery", "抽奖", Icons.Filled.CardGiftcard) {
                        panelOpen = false
                        showLottery = true
                    },
                )
                // 私聊没有 @提醒 (没有成员列表, 硬放就是死按钮), 也没群管理那三项
                add(
                    ChatPanelItem("emoji", "表情", Icons.Filled.EmojiEmotions) {
                        panelOpen = false
                        emojiOpen = true
                    },
                )
                add(ChatPanelItem("close", "关闭", Icons.Filled.Close) { panelOpen = false })
            }
            ChatPlusPanel(
                visible = panelOpen,
                items = panelItems,
                onClose = { panelOpen = false },
            )
            ChatEmojiPanel(
                visible = emojiOpen,
                onPick = { e ->
                    if (input.text.length + e.length <= 500) {
                        input = TextFieldValue(
                            text = input.text + e,
                            selection = TextRange(input.text.length + e.length),
                        )
                    }
                },
                onClose = { emojiOpen = false },
            )
        }
    }

    // ===== 抽奖界面 (「+」面板进入) =====
    if (showLottery) {
        Dialog(
            onDismissRequest = { showLottery = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize()) {
                LotteryScreen(
                    onBack = { showLottery = false },
                    onToast = onToast,
                )
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
                    // 菜单顶部先展示这条消息的摘要, 免得点错
                    Text(
                        text = if (acting.image.isNotBlank()) "[图片]"
                        else if (VideoProbe.isVideoMessage(acting.msgType, acting.video)) "[视频]"
                        else acting.content.replace('\n', ' ').take(60),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Wave 2: 视频消息才有 —— 全屏播放 (撤回等操作对视频一样可用, 不受影响)
                    if (VideoProbe.isVideoMessage(acting.msgType, acting.video) &&
                        !VideoProbe.isVideoCleaned(acting.video, acting.content)
                    ) {
                        PmActionRow(text = "全屏播放", danger = false, onClick = {
                            val url = acting.video
                            actionTarget = null
                            fullscreenVideo = url
                        })
                    }
                    // 图片消息才有: 全屏看大图 / 存进系统相册
                    if (acting.image.isNotBlank()) {
                        PmActionRow(text = "放大查看", danger = false, onClick = {
                            actionTarget = null
                            previewImage = acting.image
                        })
                        PmActionRow(text = "保存到相册", danger = false, onClick = {
                            val img = acting.image
                            actionTarget = null
                            saveImageToGallery(img)
                        })
                    }
                    // 纯图片消息没有文字, 不显示「复制」
                    if (acting.content.isNotBlank()) {
                        PmActionRow(text = "复制", danger = false, onClick = {
                            actionTarget = null
                            copyMessage(acting.content)
                        })
                    }
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

    // ===== Wave 2: 全屏播放视频 (阶段2 起 ExoPlayer + 300MB LRU 缓存, 左上角关闭) =====
    if (fullscreenVideo.isNotBlank()) {
        VideoFullscreenDialog(url = fullscreenVideo, onDismiss = { fullscreenVideo = "" })
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
                // v1.1.12: 双指缩放 1x~5x / 双击放大还原 / 放大后单指拖动平移(边界回弹)
                // (背景那层 pointerInput 仍然负责点空白关闭; 放大后点图片不会误关)
                ZoomableImage(
                    url = previewImage,
                    onTapWhenNormal = { previewImage = "" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
                // 右上角: 保存到相册 / 关闭 (点图片本身仍是关闭)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "保存",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            val img = previewImage
                            saveImageToGallery(img)
                        },
                    )
                    Text(
                        text = "关闭",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { previewImage = "" },
                    )
                }
            }
        }
    }
}

/** 一条私聊消息的气泡行 (复刻群聊 MessageRow 的视觉, 去掉 @ / 引用 / 群管理员相关逻辑) */
@Composable
private fun PmMessageRow(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    msg: PmMessage,
    mine: Boolean,
    peerName: String,
    peerAvatar: String,
    myName: String,
    myAvatar: String,
    onLongPress: () -> Unit,
    onAvatarTap: () -> Unit,
    onImageTap: (String) -> Unit,
    /** Wave 2: 点气泡里的视频 = 上层打开全屏播放 */
    onVideoTap: (String) -> Unit = {},
    /** 点气泡里的链接: 走内置浏览器 (无协议链接调用方会补 https://) */
    onOpenLink: (String) -> Unit = {},
    highlight: Boolean = false,
) {
    // Wave 2 视频消息判定: video 非空 或 msg_type == "video" 都算 (老图片消息 msg_type 是空串, 不受影响)。
    // 被服务端清理后 video 置空、content 追加「[视频已清理]」→ 灰底占位不可播。
    val isVideo = VideoProbe.isVideoMessage(msg.msgType, msg.video)
    val videoCleaned = isVideo && VideoProbe.isVideoCleaned(msg.video, msg.content)
    // 已清理时正文里那句「[视频已清理]」不再重复渲染一遍 (占位块已经说明过了)
    val bodyText = if (videoCleaned) msg.content.replace("[视频已清理]", "").trim() else msg.content

    if (msg.isRecalled) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 6.dp),
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

    // 契约 C: 大圆角 + 靠自己那一侧的「尾角」; 别人的气泡补一道细玻璃描边做层次
    // (与群聊 MessageRow 同一套观感, 私聊页面不再是一根灰条)
    val bubbleShape = if (mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    val bubbleStroke = glassStroke()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = GlassSpacing.page,
                vertical = if (compact) 2.dp else 5.dp,
            ),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            if (compact) {
                // 连续消息: 用等宽占位顶住头像位, 气泡左右仍对齐, 但不重复画头像
                Spacer(Modifier.width(42.dp))
            } else {
                Box(
                    modifier = Modifier.pointerInput(msg.userId) {
                        detectTapGestures(onTap = { onAvatarTap() })
                    },
                ) {
                    PmAvatar(name = peerName, url = peerAvatar)
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(bubbleShape)
                .background(
                    when {
                        highlight -> MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
                        mine -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .then(if (mine) Modifier else Modifier.border(1.dp, bubbleStroke, bubbleShape))
                .pointerInput(msg.id) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ===== Wave 2: 视频消息 (放在图片分支之前, 图片分支一个字节都不动) =====
            if (isVideo) {
                if (videoCleaned) {
                    CleanedVideoPlaceholder(onLongPress = onLongPress)
                } else {
                    VideoBubble(
                        url = msg.video,
                        videoW = msg.videoW,
                        videoH = msg.videoH,
                        durationSec = msg.videoDuration,
                        mine = mine,
                        onOpenFullscreen = { onVideoTap(msg.video) },
                        onLongPress = onLongPress,
                    )
                }
                if (bodyText.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
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
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(msg.id) {
                            detectTapGestures(
                                onTap = { onImageTap(msg.image) },
                                onLongPress = { onLongPress() },
                            )
                        },
                )
                if (msg.content.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
            if (bodyText.isNotBlank()) {
                // v1.1.12: 气泡里的链接要能点开 (内置浏览器), 又不能抢掉长按菜单 —— 手势自己做:
                // onTextLayout 拿到排版结果, 把点击坐标换成字符偏移, 再查 linkify 打好的 URL 注解
                var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                // MiuixTheme.colorScheme 是 @Composable 属性, 不能放进 remember 的 lambda, 先取出来
                val linkColor = if (mine) Color(0xFFFFF3C4) else MiuixTheme.colorScheme.primary
                val shown = remember(msg.id, bodyText, linkColor) {
                    linkify(content = bodyText, linkColor = linkColor)
                }
                Text(
                    text = shown,
                    fontSize = 14.sp,
                    color = if (mine) {
                        MiuixTheme.colorScheme.onPrimary
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                    onTextLayout = { textLayout = it },
                    modifier = Modifier.pointerInput(msg.id) {
                        detectTapGestures(onTap = { pos ->
                            val lr = textLayout ?: return@detectTapGestures
                            val off = lr.getOffsetForPosition(pos).coerceIn(0, shown.length)
                            shown.getStringAnnotations("URL", off, off).firstOrNull()?.let { ann ->
                                onOpenLink(ann.item)
                            }
                        })
                    },
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = pmMessageTime(msg.createdAt),
                fontSize = 10.sp,
                color = if (mine) {
                    MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.88f)
                },
            )
        }

        if (mine) {
            if (compact) {
                Spacer(Modifier.width(42.dp))
            } else {
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
}

/**
 * 消息 item 进场动画 (契约 C)
 *
 * 只保留淡入: placementSpec = null —— 否则「加载更早的消息」往列表顶部插数据时,
 * 整列消息会被动画推着走, 和顶部锚定逻辑打架 (与群聊 messageItemEnter 同一决定)。
 */
private fun LazyItemScope.pmMessageItemEnter(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(durationMillis = 200),
    placementSpec = null,
    fadeOutSpec = null,
)

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

/**
 * v1.1.12 乐观发送的本地临时消息 (私聊版)。
 * text 留着是为了失败重发时原样再发一次。
 */
private data class OutgoingPmMsg(
    val localId: String,
    val text: String,
    val message: PmMessage,
    val state: SendState,
    // ===== Wave 2 (1.1.13) 视频消息: 纯文字 / 纯图片消息全走默认值, 既有行为完全不变 =====
    /** 本地选中的视频 (content:// 或 file://); null = 这条不是视频消息 */
    val videoUri: Uri? = null,
    /** 上传进度 0~100 (圆形进度环) */
    val progress: Int = 0,
    /** 用户点了「取消」: 上传线程下一次写块时自行中止 */
    val cancelRequested: Boolean = false,
    /** 已经拿到的服务端直链: 非空时重发跳过上传 */
    val uploadedUrl: String = "",
    val videoW: Int = 0,
    val videoH: Int = 0,
    val videoDuration: Int = 0,
    val videoSize: Long = 0L,
    /** 本地首帧缩略图 (视频气泡占位用) */
    val thumbnail: Bitmap? = null,
    val fileName: String = "chat.mp4",
    val mime: String = "video/mp4",
)

/** 本地临时消息的序号 (负 id 用), 进程内自增就够 */
private val PM_LOCAL_SEQ = java.util.concurrent.atomic.AtomicInteger(0)
