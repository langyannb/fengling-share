package com.fengling.share.ui.social

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.MessageStream
import com.fengling.share.data.SocialGroup
import com.fengling.share.data.StreamEvent
import com.fengling.share.data.SocialGroupMember
import com.fengling.share.data.SocialMessage
import com.fengling.share.data.User
import com.fengling.share.data.UserStore
import com.fengling.share.data.isSystemMessage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.fengling.share.ui.components.AppGradientBackground
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.glassStroke
import com.fengling.share.ui.components.glassSurface
import com.fengling.share.ui.components.LiquidSegmentedBar
import com.fengling.share.ui.components.SegmentBarHeight

import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.listBehindTransform
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import com.fengling.share.ui.components.MuteOptionPicker
import com.fengling.share.ui.components.TagChips
import com.fengling.share.ui.lottery.LotteryScreen
import com.fengling.share.ui.pm.PmScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 群组列表的**进程内缓存** (用户 m00464 反馈「群组里面返回的话会卡一下」)。
 *
 * 点会话/群都走 NavHost 路由, 导航过去时 SocialScreen 被 dispose; 返回时重建,
 * groups 是空的 + loading = true → 先闪一屏「加载中…」再等一次网络往返, 看着就是「卡一下」。
 * 用进程内缓存垫一层: 返回立刻出上次的数据, 再后台静默刷新, 视觉上无跳变。
 * 只在内存里, 不落盘、不跨进程, 退出 App 即失效, 不会显示过期很久的脏数据。
 */
private object SocialListCache {
    var groups: List<SocialGroup>? = null
}

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
 * 未登录时的群组占位页: 图标 + 文案 + 「去登录 / 注册」按钮。
 *
 * 底部 tab 的「群组」在未登录时会被拦到登录页, 这里兜住
 * 「从通知点进某个群」这类直达路径, 避免出现一片空白。
 */
@Composable
internal fun LoginRequiredView(onBack: (() -> Unit)?, onNeedLogin: (() -> Unit)?) {
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
    /** 从通知点进来时定位的消息 id (0 = 不定位) */
    initialMessageId: Int = 0,
    /** 从「群公告更新」通知点进来时直接弹出公告 */
    openNotice: Boolean = false,
    /** 消息里的链接: 交给内置浏览器打开 */
    onOpenWeb: ((url: String, title: String) -> Unit)? = null,
    /** 未登录时点「去登录」的回调 (跳账号页登录/注册) */
    onNeedLogin: (() -> Unit)? = null,
    /** 点头像 / 点昵称: 打开某个用户的主页 (含自己; 主页里自己看不显示「发消息」) */
    onOpenUser: ((userId: Int, groupId: Int) -> Unit)? = null,
    /** 「私聊」tab 里点会话: 打开私聊聊天页 (convId = 0 表示还没会话说, 用 userId 首次私聊) */
    onOpenPm: ((userId: Int, convId: Int) -> Unit)? = null,
    /** 点「有人@你 / 有人@所有人」提示: 进群并定位到那条消息 */
    onOpenGroupAt: ((groupId: Int, messageId: Int) -> Unit)? = null,
    /** 点右上角「☰」: 打开群详情页 (群头像/成员/公告/相册/设置; 契约 B6) */
    onOpenGroupInfo: ((groupId: Int) -> Unit)? = null,
    /** 页面级捕获层 (MainScreen 的 backdrop): 液体玻璃分段控件用它做真实 backdrop 模糊 */
    glassBackdrop: Backdrop? = null,
    /** 「群组 / 私聊」分段选中值 (0 群组 / 1 私聊), 由 MainScreen 常驻持有后传入 (契约 A) */
    initialTab: Int = 0,
    /** 分段切换回调: 状态提升到 MainScreen, 切底部 Tab / 导航返回都不会丢 (契约 A) */
    onTabChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 未登录: 群组内容一律不给看, 直接引导去登录 (用户 2026-10-04 要求)
    if (!UserStore.isLoggedIn()) {
        LoginRequiredView(onBack = onBack, onNeedLogin = onNeedLogin)
        return
    }

    // 返回重建时直接吃缓存 (非空 → 不显示加载态), 首启动才走「加载中…」
    var groups by remember { mutableStateOf(SocialListCache.groups ?: emptyList<SocialGroup>()) }
    var loading by remember { mutableStateOf(SocialListCache.groups == null) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var currentGroup by remember { mutableStateOf<SocialGroup?>(null) }
    // 当前群的定位消息 id (点「有人@你」进来时用它, 进群后自动定位并高亮)
    var locateMsgId by remember { mutableStateOf(0) }
    // 顶栏「三条横杠」菜单与公告弹框的状态 (聊天页顶栏在这里, 内容在 ChatView)
    val chatMenu = remember { ChatMenuState() }
    // 群公告通知点进来: 直接把公告弹框打开
    LaunchedEffect(Unit) { if (openNotice) chatMenu.showNoticeViewer = true }
    var refreshTick by remember { mutableStateOf(0) }
    // 顶部「群组 / 私聊」分段: 只在底部 tab 常驻模式显示
    // (从「消息」点通知进来的全屏页 onBack != null, 直接进群聊/私聊, 不需要分段)
    val showTabs = onBack == null && onOpenGroup != null
    // 契约 A(强化): 分段选中值不再由本页自己持有 —— 页面重建 (导航返回 / 切底部 Tab 回收) 时,
    // 页面内 remember / rememberSaveable 都可能丢, 用户反馈的「私聊进会话返回掉回群组」就是这样。
    // 现在改成受控: 值由 MainScreen 常驻层传入, 切换时回调上报, 页面怎么重建都不受影响。
    val tab = initialTab
    val selectTab: (Int) -> Unit = { v -> if (v != tab) onTabChange?.invoke(v) }
    // 私聊未读总数 (契约 B2): 只用来画顶部「私聊」分段上的红点
    var pmUnread by remember { mutableStateOf(0) }
    // 任何一处改动 (加载成功 / 已读清零 / 加入群聊) 都同步进缓存, 免得返回后看到旧红点
    LaunchedEffect(groups) { SocialListCache.groups = groups }

    /**
     * 拉群列表。
     * - silent = true 时不显示任何加载态、失败也不打扰用户, 用于后台自动刷新未读数
     */
    fun loadGroups(isRefresh: Boolean, silent: Boolean = false) {
        scope.launch {
            if (!silent) {
                if (isRefresh) refreshing = true else loading = true
            }
            ApiClient.socialGroups()
                .onSuccess { list ->
                    groups = list
                    // v1.1.1: 当前正在看的那个群也要跟着刷新 (公告 / 免打扰 / 全员禁言 / 人数),
                    // 否则别人刚开的「全员禁言」要退出群再进来才看得到
                    val cur = currentGroup
                    if (cur != null) {
                        val fresh = list.firstOrNull { it.id == cur.id }
                        if (fresh != null && fresh != cur) {
                            currentGroup = fresh
                            if (fresh.muted != cur.muted) chatMenu.muted = fresh.muted
                        }
                    }
                    error = ""
                }
                .onFailure { e -> if (!silent) error = e.message ?: "加载失败" }
            if (!silent) {
                if (isRefresh) refreshing = false else loading = false
            }
        }
    }

    /**
     * 私聊未读总数 (契约 B2): 给顶部「私聊」分段上的红点用。
     * 失败一律静默 (红点不显示就行, 不能打扰用户)。
     */
    fun loadPmUnread() {
        scope.launch {
            ApiClient.pmConversations().onSuccess { pmUnread = it.totalUnread }
        }
    }

    LaunchedEffect(Unit) {
        loadGroups(false)
        loadPmUnread()
    }

    /**
     * 进群标记已读后调: 立刻把本地未读数清零, 返回群列表时角标就消失了。
     *
     * 契约 B1: 除了 unread / firstUnreadId, 必须把 atMe / atMeFirst / atAll / atAllFirst 一起清零,
     * 否则返回列表后红点没了, 却还挂着「有人@你 / 有人@所有人」的提示。
     */
    fun clearUnread(groupId: Int) {
        groups = groups.map {
            if (it.id == groupId) {
                it.copy(
                    unread = 0,
                    firstUnreadId = 0,
                    atMe = 0,
                    atMeFirst = 0,
                    atAll = 0,
                    atAllFirst = 0,
                )
            } else {
                it
            }
        }
        val cur = currentGroup
        if (cur != null && cur.id == groupId) {
            currentGroup = cur.copy(
                unread = 0,
                firstUnreadId = 0,
                atMe = 0,
                atMeFirst = 0,
                atAll = 0,
                atAllFirst = 0,
            )
        }
    }

    /**
     * 离开群聊回列表前 (契约 B1): 再补一次「标记到最新」的已读上报 + 立刻清掉本地红标。
     *
     * 根因回顾: 老实现只在进群那一刻上报一次已读, 之后别人再发的消息又让服务端 unread > 0,
     * 返回列表时轮询把红标又画出来 (用户报的「必须再进再退才消失」就是这个)。
     */
    fun leaveCurrentGroup() {
        val cur = currentGroup ?: return
        clearUnread(cur.id)
        currentGroup = null
        scope.launch {
            // last_id = 0 = 标记到最新; 失败不提示 (下次进群还会再报一次)
            ApiClient.socialRead(cur.id)
        }
    }

    // 回到群组列表 (从聊天页返回 / 切回本 tab) 时重新拉一次群列表:
    // 未读角标要立刻反映服务端最新状态 (进群标记已读后角标必须消失)
    val listForeground = rememberIsForeground()
    // 回到群列表 / App 回到前台: 立刻静默刷新一次未读数
    LaunchedEffect(listForeground, currentGroup) {
        if (listForeground && currentGroup == null && !loading) {
            loadGroups(false, silent = true)
            loadPmUnread()
        }
    }

    // 群列表自动刷新: 停在列表页时每 8 秒静默拉一次 (未读红标自己就更新了, 不用手动下拉)
    LaunchedEffect(listForeground, currentGroup) {
        if (!listForeground || currentGroup != null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(8000L)
            loadGroups(true, silent = true)
            // 私聊未读总数并入同一个 8 秒静默轮询 (契约 B2)
            loadPmUnread()
        }
    }

    // SSE 实时流: 收到群消息 -> 立刻静默刷新群列表 (未读红标不用等 8 秒);
    // 如果用户正在看这个群 -> 再顺手刷一次当前会话消息 (改 refreshTick 触发 ChatView 的 loadLatest)。
    // 断线重连 -> 群列表和当前会话都补刷一次。上面的 8 秒兜底轮询保留不动。
    LaunchedEffect(Unit) {
        MessageStream.events.collect { event ->
            when (event) {
                is StreamEvent.Group -> {
                    loadGroups(true, silent = true)
                    val cur = currentGroup
                    if (cur != null && cur.id == event.groupId) {
                        // 契约 B1: 我正看着这个群, 这条消息就算已读 —— 不能只 refreshTick++,
                        // 那样服务端 unread 还是 > 0, 返回列表红标又冒出来
                        clearUnread(cur.id)
                        scope.launch {
                            ApiClient.socialRead(cur.id, event.msgId)
                            clearUnread(cur.id)
                        }
                        refreshTick++
                    }
                }
                StreamEvent.Reconnected -> {
                    loadGroups(true, silent = true)
                    if (currentGroup != null) refreshTick++
                }
                else -> Unit
            }
        }
    }

    // 从「群组」tab 指定群进入: 列表加载完成后自动打开该群
    LaunchedEffect(groups) {
        val pid = initialGroupId ?: return@LaunchedEffect
        if (currentGroup == null) {
            groups.firstOrNull { it.id == pid }?.let { currentGroup = it }
        }
    }

    // 契约 B: 预测返回手势 —— 手势进度 0→1 跟手位移 + 缩小淡出, 松手 <50% 回弹, ≥50% 完成。
    // 全屏路由模式(从「群组」tab 点进某个群)直接回上一页, 一次到位;
    // 内嵌模式(群组列表 + 聊天同屏)则先回到群组列表。用户反馈原来要点两次才回去。
    // 用户反馈 (m00464)「预测返回还很奇怪」的根因:
    // 路由模式 (onBack != null) 下 navigation-compose 2.8+ 的 NavHost 自带预测返回动画,
    // 这里又注册了一个后注册的自定义回调把它顶掉 → 手势先按本地的 progress 跟手缩放,
    // 松手完成时本体 snapTo(1f) 再瞬间弹回 0f, 之后才走 NavHost 的转场 → 「一卡二弹」。
    // 现在只在**内嵌模式**(群组列表 + 群聊同屏、不走 NavHost)注册, 路由模式整个交回框架, 一次到位。
    val backProgress = rememberPredictiveBackProgress(
        enabled = onBack == null && currentGroup != null,
    ) {
        // 契约 B1: 离开群聊回列表之前, 补一次已读上报 + 清掉本地红标
        if (currentGroup != null) leaveCurrentGroup()
        if (onBack != null) onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            val g = currentGroup
            when {
                // 聊天页: 全屏路由模式直接回上一页, 内嵌模式先回群组列表
                g != null -> AppTopBar(
                    // 契约 B4: 标题显示「群名(成员数)」, 人数取 social_groups 的 member_count
                    title = if (g.memberCount > 0) g.name + "(" + g.memberCount + ")" else g.name,
                    // 契约 B1: 离开群聊之前补一次已读上报 + 清本地红标
                    onBack = {
                        leaveCurrentGroup()
                        if (onBack != null) onBack()
                    },
                    actions = {
                        // 契约 B6: 右上角「☰」进群详情页。
                        // 原来的下拉菜单 (查看/发布群公告、全员禁言、消息免打扰、刷新消息)
                        // 能力全部搬进群详情页, 这里只保留入口
                        LaunchedEffect(g.id) { chatMenu.muted = g.muted }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    if (onOpenGroupInfo != null) {
                                        onOpenGroupInfo(g.id)
                                    } else {
                                        Toast.makeText(context, "群详情页暂不可用", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "群详情",
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.size(22.dp),
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
            // 页面级柔和渐变底 (静态绘制, 零模糊开销):
            // 顶栏 / 分段控件 / 输入栏这些玻璃层靠它才有可模糊的层次, 否则模糊纯色仍是纯色
            AppGradientBackground()
            // 局部捕获层: 只捕获「列表内容」这一层, **不包含上面的分段控件自身**。
            // 不能复用 MainScreen 的页面捕获层 —— 那一层包含本控件, 自引用会让 hwui 的
            // RenderNode 树无限递归 (真机 SIGSEGV stack overflow, 已经踩过一次)。
            // 列表内容从玻璃底下穿过 = 真模糊 + 真折射; 分段控件固定在顶部, 不随列表滚动。
            val listBackdrop = rememberLayerBackdrop()
            val tabTopSpace = if (showTabs && currentGroup == null) SegmentBarHeight + 16.dp else 0.dp
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                val group = currentGroup
                // 群聊层与列表层**同时**组合: 跟手右滑时露出来的是真实的群列表, 不是一片背景色
                // (用 when 二选一就做不到 ColorOS 16 的「从哪来回哪去」)。
                Box(modifier = Modifier.fillMaxSize()) {
                // 列表层: 被露出的那一层, 跟手时做 0.90 -> 1.0 视差放大 (只在盖着群聊时才挂)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (group != null) Modifier.listBehindTransform(backProgress) else Modifier)
                        // v1.1.10: 背景捕获层只框住「列表」这一层 —— 群聊层滑走时它采样到的
                        // 就是下面那份真实列表; 以前框住两层, 采到上一帧的群聊画面 = 重影/透明感
                        .layerBackdrop(listBackdrop),
                ) {
                    when {
                        // 私聊 tab: 会话列表 (点会话交给外部导航打开聊天页)
                        showTabs && tab == 1 -> PmScreen(
                            onOpenChat = { userId, convId -> onOpenPm?.invoke(userId, convId) },
                            // 私聊列表进来没有群上下文 → groupId = 0 (全站视角)
                            onOpenUser = { uid -> onOpenUser?.invoke(uid, 0) },
                            topPadding = tabTopSpace,
                            onNeedLogin = onNeedLogin,
                        )
                        else -> GroupList(
                            groups = groups,
                            loading = loading,
                            refreshing = refreshing,
                            error = error,
                            // 分段控件浮在列表上方, 列表顶部让出等高的空档
                            topPadding = tabTopSpace,
                            onRefresh = { loadGroups(true) },
                            onRetry = { loadGroups(false) },
                            onOpen = { g ->
                                locateMsgId = 0
                                if (onOpenGroup != null) onOpenGroup(g) else currentGroup = g
                            },
                            // 点「有人@你」: 进群并定位到那条消息
                            onOpenAt = { g, mid ->
                                if (onOpenGroupAt != null) {
                                    onOpenGroupAt(g.id, mid)
                                } else {
                                    locateMsgId = mid
                                    if (onOpenGroup != null) onOpenGroup(g) else currentGroup = g
                                }
                            },
                        )
                    }
                }
                // 群聊层: 盖在列表之上, 由 predictiveBackTransform 跟手右移 —— 滑到一半就能
                // 看见下面真实的群列表 (ColorOS 16 的跟手返回), 松手回弹/提交都自然衔接
                if (group != null) {
                    ChatView(
                        group = group,
                        me = UserStore.current,
                        menu = chatMenu,
                        refreshTick = refreshTick,
                        locateMessageId = if (locateMsgId > 0) locateMsgId else initialMessageId,
                        onOpenWeb = onOpenWeb,
                        onToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                        // 进群定位完成 → 标记已读 → 清掉群列表上的未读角标
                        onMarkRead = { clearUnread(group.id) },
                        // 群聊进来带上群 id: 主页上的禁言只对本群生效 (契约 F2)
                        onOpenUser = { uid, _ -> onOpenUser?.invoke(uid, group.id) },
                        // 契约 B3: 加入群聊成功 → 本地马上把 isMember 置 true, 并刷新群资料
                        // (标题里的成员数 +1) 与群列表; 底部输入区随即从「加入群聊」变回输入框
                        glassBackdrop = glassBackdrop,
                        onJoined = {
                            currentGroup = currentGroup?.copy(isMember = true)
                            groups = groups.map { row ->
                                if (row.id == group.id) row.copy(isMember = true) else row
                            }
                            loadGroups(true, silent = true)
                        },
            // 不透明底: 列表层现在常驻在下面, 群聊层若还是透的就会「隔着聊天看见列表」
            // (v1.1.8 的毛病)。铺一层与页面一致的渐变底, 跟手时移动的是一张实心页面。
            modifier = Modifier
                .fillMaxSize()
                .appGradientBackground()
                .predictiveBackTransform(progress = backProgress),
        )
                }
                }
                }
                // 顶部「群组 / 私聊」分段: 浮动在列表上方 (固定层, 不随列表滚动 → 可安全用 backdrop)
                if (showTabs && currentGroup == null) {
                    LiquidSegmentedBar(
                        tabs = listOf("群组", "私聊"),
                        selected = tab,
                        onSelect = { selectTab(it) },
                        // 局部捕获层只含列表内容、不含本控件自身 → 安全 (页面级整层会自引用递归崩溃)
                        backdrop = listBackdrop,
                        unread = listOf(
                            // 契约 B2: 群组未读 = 所有群未读之和
                            groups.sumOf { it.unread },
                            // 私聊未读 = 会话未读总数
                            pmUnread,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 群列表右侧的时间文案: 今天显示 HH:mm, 昨天显示「昨天」, 更早显示 MM-dd。
 * 解析失败 (格式不对) 就返回空串, 不显示时间也不崩。
 */
private fun groupTimeText(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw)
            ?: return ""
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val day = dayFmt.format(date)
        val today = dayFmt.format(Date())
        val yesterday = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        when {
            day == today -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            day == dayFmt.format(yesterday.time) -> "昨天"
            else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        ""
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
    /** 点「有人@你 / 有人@所有人」: 进群并定位到第一条相关消息 */
    onOpenAt: (SocialGroup, Int) -> Unit = { _, _ -> },
    /** 顶部留给浮动分段控件的高度 (液体玻璃要能从下面透出内容才有模糊可看) */
    topPadding: Dp = 0.dp,
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
                contentPadding = PaddingValues(top = topPadding, bottom = 100.dp),
            ) {
                item {
                    // 顺手汇总一下未读总数 (免打扰的群也算在里面)
                    val totalUnread = groups.sumOf { it.unread }
                    val listHint = "点击群组进入聊天 · 群公告与消息实时同步" +
                        (if (totalUnread > 0) " · 未读 " + totalUnread + " 条" else "")
                    Text(
                        text = listHint,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                items(groups, key = { it.id }) { g ->
                    GroupCard(
                        group = g,
                        onClick = { onOpen(g) },
                        onOpenAt = { mid -> onOpenAt(g, mid) },
                    )
                }
            }
        }
    }
}

/**
 * 群列表卡片 (照 QQ 群列表做):
 * - 第一行: 群名 (+ 免打扰小图标)
 * - 第二行: 「有人@你」(红) / 「有人@所有人」(橙) 提示 + 最新一条消息摘要
 * - 第三行: 群公告 (有公告才显示)
 * - 右侧: 最新消息时间 (今天 HH:mm / 昨天 / MM-dd) + 未读角标
 */
@Composable
private fun GroupCard(
    group: SocialGroup,
    onClick: () -> Unit,
    onOpenAt: (Int) -> Unit = {},
) {
    val last = group.lastMessage
    // 副标题: 别人发的显示「昵称: 内容」; 纯图片显示 [图片]; 撤回/空显示「暂无消息」
    val subtitle = when {
        last == null -> "暂无消息"
        last.content.isNotBlank() ->
            last.nickname.ifBlank { "群友" } + ": " + last.content.replace('\n', ' ')
        last.image.isNotBlank() -> last.nickname.ifBlank { "群友" } + ": [图片]"
        else -> "暂无消息"
    }
    // 契约 C: 群列表卡片玻璃化 + 按下缩到 0.97 松手弹回 (去掉涟漪, 靠缩放给触感反馈)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "groupCardPress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GlassSpacing.page, vertical = GlassSpacing.cardGap)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .glassCard(radius = GlassRadius.card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GlassSpacing.cardInner),
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
                    // 全员禁言小标 (v1.1.1)
                    if (group.allMuted) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "全员禁言",
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // @提示: 有人@我用红色, 只有@所有人时用橙色 (两种颜色必须区分开)
                    // 点提示条 → 直接进群并定位到第一条相关消息
                    if (group.atMe > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE5484D).copy(alpha = 0.12f))
                                .clickable { onOpenAt(group.atMeFirst) }
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "有人@你",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE5484D),
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                    } else if (group.atAll > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE08A00).copy(alpha = 0.12f))
                                .clickable { onOpenAt(group.atAllFirst) }
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "有人@所有人",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE08A00),
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            Spacer(Modifier.width(10.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                val timeText = groupTimeText(group.lastTime)
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                // 未读角标: 免打扰的群用灰点 (不打扰), 正常的用红底数字, >99 显示 99+
                if (group.unread > 0) {
                    Spacer(Modifier.height(4.dp))
                    UnreadBadge(count = group.unread, muted = group.muted)
                }
            }
        }
    }
}

/** 群列表右侧的未读角标 (count <= 0 不显示; 免打扰只给一个灰点) */
@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    if (count <= 0) return
    if (muted) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.45f)),
        )
        return
    }
    val label = if (count > 99) "99+" else count.toString()
    Box(
        modifier = Modifier
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .clip(CircleShape)
            .background(Color(0xFFE5484D))
            .padding(horizontal = if (label.length > 1) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
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
    onOpenWeb: ((url: String, title: String) -> Unit)? = null,
    onToast: (String) -> Unit,
    onMarkRead: () -> Unit = {},
    /** 点头像: 打开用户主页 (为 null 时退回旧的成员操作面板) */
    onOpenUser: ((Int, Int) -> Unit)? = null,
    /** 点「加入群聊」成功后回调: 上层刷新群资料(成员数/isMember)与群列表 (契约 B3) */
    onJoined: () -> Unit = {},
    /** 页面级捕获层: 输入栏 / 顶栏做毛玻璃用, null 时自动退化为半透明底 (契约 C) */
    glassBackdrop: Backdrop? = null,
    /** 群聊层自己的 modifier: 群组页拿它做可预测式返回的跟手变换 (v1.1.8) */
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // 复制消息用得到系统剪贴板
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val foreground = rememberIsForeground()
    // 契约 B3: 「加入群聊」请求进行中 (防重复点)
    var joining by remember(group.id) { mutableStateOf(false) }

    var messages by remember { mutableStateOf<List<SocialMessage>>(emptyList()) }
    // 用 TextFieldValue 而不是 String: @ 插入后要把光标放到插入文本之后
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    // 图片上传中: 上传期间禁用发送/选图, 防止重复上传
    var uploading by remember { mutableStateOf(false) }
    // 输入框左侧「+」菜单是否展开 (图片 / @某人 / 群公告)
    var plusMenuOpen by remember { mutableStateOf(false) }
    // 全屏查看的图片地址 (空串 = 不显示)
    var previewImage by remember { mutableStateOf("") }
    // 上滑加载更早的消息: 服务端还有没有更早的一页 + 是否正在拉更早的一页
    var hasMoreBefore by remember(group.id) { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    // 前插更早消息后要补偿的滚动目标 index (-1 = 不需要补偿)
    var pendingScrollTo by remember { mutableStateOf(-1) }
    /**
     * 用户当前是否贴在消息列表底部。
     * 只有「本来就贴底」时才自动跟到最新消息; 用户上翻翻历史时收到新消息绝不能把ta拽回底部。
     * 由下面 snapshotFlow 持续维护, 同时决定右下角「回到最新消息」按钮显不显示。
     */
    var atBottom by remember { mutableStateOf(true) }
    /** 用户上翻期间新增的消息条数: 显示在「回到最新消息」按钮的角标上, 回到底部后清零 */
    var newWhileAway by remember { mutableStateOf(0) }
    /**
     * 本次消息列表变化后要不要自动滚到底。
     * 必须在「改写 messages 的那一刻」按当时的贴底状态算好: 新消息一进列表
     * canScrollForward 立刻就是 true, 等到组合后再判断就永远不敢跟到底了。
     */
    var autoScrollPending by remember { mutableStateOf(false) }
    /** 首屏/刷新落地用无动画的 scrollToItem (从顶部动画滚到最新一条看起来像「抖一下」) */
    var settleWithoutAnimation by remember { mutableStateOf(true) }

    /**
     * 当前是否「贴在底部」(带「最后一条已进入可视区」的一格容差, 与 atBottom 判据一致)。
     * 判据带上容差是为了防抖动: 贴底时刚来一条新消息会让 canScrollForward 立刻变 true,
     * 只看它的话按钮会闪一下、自动跟随也会失效。
     * 注意: 必须在改写 messages 之前调用, 这样才代表「改写前」的位置。
     */
    fun isStuckToBottom(): Boolean {
        if (!listState.canScrollForward) return true
        if (messages.isEmpty()) return true
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
        return lastVisible >= messages.lastIndex - 1
    }

    // 持续维护 atBottom (用户滚到底部时顺手把「上翻期间新增」角标清零)
    LaunchedEffect(group.id, listState) {
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
    // 抽奖界面 (输入区「+」菜单进入)
    var showLottery by remember { mutableStateOf(false) }
    // 点空白处取消文本选中用的 key: 本仓库 Compose 版本的 SelectionContainer 只公开
    // (modifier, content) 这一个重载, 既没有选中态回调也没有清除选中的 API,
    // 所以「取消选中」靠改这个 key 重建被选中的那段文本 (用户 2026-10-04 反馈的问题)
    var selectionReset by remember { mutableStateOf(0) }
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
    /**
     * 首次要定位的消息 id:
     * - 从通知 / 引用点进来: 用通知带过来的 messageId (保持原行为, 不被未读覆盖)
     * - 否则群里还有未读: 用 first_unread_id, 让「第一条未读」落在屏幕上方, 往下滑就能读完
     */
    val initialLocateId = when {
        locateMessageId > 0 -> locateMessageId
        group.unread > 0 && group.firstUnreadId > 0 -> group.firstUnreadId
        else -> 0
    }
    /** 待定位的消息 id: 首次加载会以它为中心取一屏, 定位完清零 (避免每次刷新都跳) */
    var locateId by remember(group.id) { mutableStateOf(initialLocateId) }
    /** 正在高亮闪烁的消息 id (定位到的消息会给个底色) */
    var highlightId by remember { mutableStateOf(0) }
    /** 第一条未读那条消息: 底色一直留到离开这个群 (只有按未读定位时才用) */
    var unreadAnchorId by remember(group.id) {
        mutableStateOf(if (locateMessageId <= 0 && initialLocateId > 0) initialLocateId else 0)
    }
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
            // 整屏重载: 更早消息的分页状态作废 (下拉刷新时重置为「可能还有更早」)
            pendingScrollTo = -1
            if (isRefresh) hasMoreBefore = true
            // 首屏落地用无动画滚动, 避免从顶部动画滚到底部 (看起来像「抖一下」)
            settleWithoutAnimation = true
            val around = locateId
            ApiClient.socialMessagesPage(group.id, limit = 30, aroundId = around)
                .onSuccess { page ->
                    // 先把「最终要用哪一屏」算完, 中途不改写 messages:
                    // 以前先赋值第一屏、再赋值第二屏, 界面会闪两下、位置跳一次
                    // (用户反馈的进群「抖一下, 好像是刷新」)
                    var finalList = page.list.distinctBy { it.id }.sortedBy { it.id }
                    var finalHasMore = page.hasMoreBefore
                    // 群列表里的未读数可能是旧的 (群对象是进群前拿到的), 所以这里再信一次服务端:
                    // 首次加载若还有未读、且不是从通知点进来的, 就以「第一条未读」为锚点重取一屏。
                    // 群对象上已经有未读锚点时 (initialLocateId) around > 0, 这里不会再发第二次请求。
                    val anchor = if (!isRefresh && around <= 0 && locateMessageId <= 0 &&
                        page.unread > 0 && page.firstUnreadId > 0
                    ) page.firstUnreadId else 0
                    if (anchor > 0) {
                        // 定位状态在最终赋值之前定好, 避免赋值后二次组合再跳一下
                        locateId = anchor
                        unreadAnchorId = anchor
                        ApiClient.socialMessagesPage(group.id, limit = 30, aroundId = anchor)
                            .onSuccess { p2 ->
                                finalList = p2.list.distinctBy { it.id }.sortedBy { it.id }
                                finalHasMore = p2.hasMoreBefore
                            }
                            .onFailure { e -> onToast(e.message ?: "消息加载失败") }
                    }
                    hasMoreBefore = finalHasMore
                    // 有锚点/定位时由下面「定位」那个 effect 负责滚动, 这里不插手「跟消息」
                    autoScrollPending = anchor <= 0 && locateId <= 0
                    messages = finalList
                }
                .onFailure { e -> onToast(e.message ?: "消息加载失败") }
            if (isRefresh) refreshing = false else loading = false
        }
    }

    /**
     * 合并新消息: 按 id 去重 + 正序 (轮询片段可能重复或乱序)。
     * forceScroll = true 用于「自己刚发出去的消息」: 不管当时在哪都跟到最新。
     * 其余情况按「改写 messages 之前」的贴底状态决定要不要自动跟到底 ——
     * 用户上翻看历史时绝不滚动, 只把新增条数记到 newWhileAway 上, 由右下角按钮一键回去。
     */
    fun mergeNew(incoming: List<SocialMessage>, forceScroll: Boolean = false) {
        if (incoming.isEmpty()) return
        val base = messages
        val merged = (base + incoming).distinctBy { it.id }.sortedBy { it.id }
        val added = merged.size - base.size
        if (added <= 0) return
        // 关键: 贴底状态要在改写 messages 之前读 —— 列表一变长 canScrollForward 立刻就是 true
        val stuck = forceScroll || isStuckToBottom()
        messages = merged
        if (stuck) {
            autoScrollPending = true
            newWhileAway = 0
        } else {
            newWhileAway += added
        }
    }

    /**
     * 上滑加载更早的一页 (before_id = 当前列表最小 id)。
     * manual = 用户点了顶部那一行; 自动加载只在真的到了顶部时才发请求。
     */
    fun loadOlder(manual: Boolean = false) {
        if (loadingMore || loading || messages.isEmpty()) return
        if (!hasMoreBefore) {
            if (manual) onToast("已经是最早的消息了")
            return
        }
        // 从通知点进来正在定位 / 正在补偿滚动时不要抢
        if (!manual && (locateId > 0 || pendingScrollTo >= 0)) return
        val minId = messages.minOfOrNull { it.id } ?: return
        if (minId <= 0) return
        // 前插前记下「用户正看着第几行」以及「顶部是否已经有加载行」
        val firstIndex = listState.firstVisibleItemIndex
        val headerBefore = if (hasMoreBefore) 1 else 0
        // 同步置上 loadingMore (不等协程调度): 快速 fling 时触顶条件可能连续成立,
        // 靠它保证同一时刻只有一个分页请求在跑
        loadingMore = true
        scope.launch {
            ApiClient.socialMessagesPage(group.id, limit = 30, beforeId = minId)
                .onSuccess { page ->
                    hasMoreBefore = page.hasMoreBefore
                    // 保险: 只前插真的更早的消息 (万一服务端把 before_id 语义放宽也不会乱序)
                    val older = page.list.filter { it.id < minId }
                    if (older.isNotEmpty()) {
                        val merged = (older + messages).distinctBy { it.id }.sortedBy { it.id }
                        val added = merged.size - messages.size
                        messages = merged
                        if (added > 0) {
                            // 保持滚动位置: 前面插进 added 条后, 原来第 firstIndex 行挪到了
                            // firstIndex + added; 顶部「加载更早」行占 1 个 index, 并且只在
                            // 还有更早消息时才存在, 所以按「新的」hasMoreBefore 补差额
                            val headerAfter = if (hasMoreBefore) 1 else 0
                            pendingScrollTo = (firstIndex + added + headerAfter - headerBefore)
                                .coerceAtLeast(0)
                        }
                    }
                }
                .onFailure { e -> onToast(e.message ?: "更早的消息加载失败") }
            loadingMore = false
        }
    }

    // 前插完成后补偿滚动: 放在 LaunchedEffect 里, 等新列表组合完索引才有效
    LaunchedEffect(messages.size, pendingScrollTo) {
        val target = pendingScrollTo
        if (target < 0) return@LaunchedEffect
        pendingScrollTo = -1
        runCatching { listState.scrollToItem(target) }
    }

    // 上滑到顶自动加载更早的消息: 只在「已经在顶部」这个条件由 false 变 true 时才触发一次,
    // 短列表 / 补偿失败时不会反复请求 (顶部那一行也可以点, 作为手动兜底)
    LaunchedEffect(group.id, locateId) {
        snapshotFlow {
            listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset == 0
        }.collect { atTop ->
            if (atTop) loadOlder()
        }
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

    // 有新消息时滚到底部 —— 只有「本来就贴在底部」时 mergeNew 才会把 autoScrollPending 置上,
    // 用户正在上翻看历史时这里不会跑 (这正是「滑快了被强行拉回最新消息」的修复)
    LaunchedEffect(messages.size, autoScrollPending) {
        if (!autoScrollPending) return@LaunchedEffect
        // 定位消息 / 前插补偿滚动 / 正在拉更早一页时都不抢滚动, 并放弃这一次跟随
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

    // 从通知点进来 / 未读定位: 滚到那条消息并高亮一下, 然后恢复正常
    // (未读定位的那条另有 unreadAnchorId 常驻底色, 见 MessageRow 的 highlight)
    LaunchedEffect(messages.size, locateId) {
        val target = locateId
        if (target <= 0 || messages.isEmpty()) return@LaunchedEffect
        val idx = messages.indexOfFirst { it.id == target }
        if (idx < 0) return@LaunchedEffect
        highlightId = target
        // 无动画定位: 进群时从顶部动画滚到未读那条, 看起来就是「抖一下」
        runCatching { listState.scrollToItem(idx) }
        locateId = 0
        // 定位自己决定位置, 不让「跟最新消息」的逻辑再补一次滚动
        autoScrollPending = false
        scope.launch {
            kotlinx.coroutines.delay(2800)
            highlightId = 0
        }
    }

    // 契约 B1: 只要「别人发来的消息最大 id」变大, 就防抖 1.2 秒上报一次已读。
    //
    // 老实现只在进群那一刻标一次已读: 用户在群里待着的时候别人又发消息 → 服务端 unread 又 > 0,
    // 返回群列表时红标又冒出来 (得再进再退才消失) —— 这就是用户报的 bug。
    // 现在已读位置跟着「来自别人的最新消息」走, 且只在本群可见 + App 前台时才上报。
    val otherMaxId = messages.maxOfOrNull { if (me != null && it.userId == me.id) 0 else it.id } ?: 0
    var readReported by remember(group.id) { mutableStateOf(0) }
    LaunchedEffect(group.id, otherMaxId, loading, foreground) {
        if (loading || !foreground || otherMaxId <= 0) return@LaunchedEffect
        if (otherMaxId <= readReported) return@LaunchedEffect
        // 防抖: 群里连着来消息时不用每条都上报一次
        delay(1200L)
        if (otherMaxId <= readReported) return@LaunchedEffect
        readReported = otherMaxId
        ApiClient.socialRead(group.id, otherMaxId).onSuccess { onMarkRead() }
    }

    /**
     * 契约 B3: 加入群聊 (先加入才能发消息)。
     *
     * 服务端加入成功时会往群里写一条系统消息「xxx加入了群聊」,
     * 所以成功后立刻重新拉一屏消息, 让用户马上看到这条提示。
     */
    fun joinGroup() {
        if (joining) return
        joining = true
        scope.launch {
            ApiClient.socialGroupJoin(group.id)
                .onSuccess {
                    onJoined()
                    onToast("已加入 " + group.name)
                    loadLatest(false)
                }
                // 失败: Toast 服务端返回的中文原文
                .onFailure { e -> onToast(e.message ?: "加入失败") }
            joining = false
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
        // 全员禁言 (v1.1.1): 普通成员在本地就拦住; 服务端还会再拦一次并返回中文错误
        if (group.allMuted && !isAdmin) {
            onToast("全员禁言中, 仅群管理员可发言")
            return
        }
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
                        .onSuccess { new -> mergeNew(new, forceScroll = true) }
                }
                .onFailure { e -> onToast(e.message ?: "发送失败") }
            sending = false
        }
    }

    /**
     * 保存图片到系统相册 (targetSdk 34 / minSdk 33 → MediaStore + RELATIVE_PATH, 不需要任何存储权限)。
     * 下载走 OkHttp (ApiClient.downloadChatImage), 写盘在 IO 线程, Toast 回主线程再弹。
     */
    fun saveImageToGallery(url: String) {
        if (url.isBlank()) return
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
            // 在 IO 线程 insert + 写字节; 出错只把文案带回来, Toast 一定在主线程弹
            val errMsg = withContext(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "fl_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext,
                        )
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        // 相册里的「风铃分享库」相簿
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

    /**
     * 相册选图 → 读字节 → 上传对象存储 → 立刻作为图片消息发出。
     * 纯图片消息 content 传空串; 上传/发送失败都不动用户已经打好的文字。
     */
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
                    // 相册 URI 常常没有扩展名, 按 MIME 给规范文件名 (后端只收 jpg / png / webp)
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                    ApiClient.uploadChatImage(bytes, "chat.$ext", mime)
                        .onSuccess { img ->
                            ApiClient.socialSend(
                                group.id,
                                "",
                                at = emptyList(),
                                quoteId = quoteTarget?.id ?: 0,
                                image = img.url,
                                imageW = img.width,
                                imageH = img.height,
                            )
                                .onSuccess {
                                    quoteTarget = null
                                    val after = messages.maxOfOrNull { it.id } ?: 0
                                    ApiClient.socialMessages(group.id, afterId = after)
                                        .onSuccess { new -> mergeNew(new) }
                                }
                                .onFailure { e -> onToast(e.message ?: "图片发送失败") }
                        }
                        .onFailure { e -> onToast(e.message ?: "图片上传失败") }
                    uploading = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 长按选中公告/消息文字后, 点页面空白处取消选中 (重建公告文本 = 清掉选中态)。
            // 子控件 (按钮/输入框/图片/气泡文字) 会先消费点击, 所以这里只吃掉「真正空白处」的点击,
            // 不会影响滚动、展开收起、长按菜单等既有交互 (拖拽会让 tap 检测自动取消)。
            .pointerInput(Unit) {
                detectTapGestures { selectionReset++ }
            },
    ) {
        // ===== 群公告 (管理员设置, 成员进群就能看到, 点击展开全文) =====
        if (noticeText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                    // 点公告条本身也顺手清一次选中 (和点空白处一致), 再展开/收起
                    .clickable {
                        selectionReset++
                        noticeExpanded = !noticeExpanded
                    }
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
                // SelectionContainer: 公告文字可以长按选中复制 (用户 2026-10-04 要求)
                // key(selectionReset): 重建这段文本即可清掉长按选中状态, 见上面状态声明处的说明
                key(selectionReset) {
                    SelectionContainer {
                        LinkText(
                            content = noticeText,
                            color = MiuixTheme.colorScheme.onBackground,
                            linkColor = MiuixTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            maxLines = if (noticeExpanded) Int.MAX_VALUE else 2,
                            onTap = { url ->
                                if (url != null) {
                                    if (onOpenWeb != null) {
                                        onOpenWeb(url, group.name)
                                    } else {
                                        onToast("没有可用的内置浏览器")
                                    }
                                } else {
                                    noticeExpanded = !noticeExpanded
                                }
                            },
                        )
                    }
                }
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
                        // 顶部: 还有更早的消息时占一行 (自动加载时显示提示, 也可以手动点一下)
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
                            // 契约 B5: 系统消息 (「xxx加入了群聊」这种) 单独走居中灰字样式:
                            // 没有头像、没有气泡、不可长按 (不能引用/撤回/复制)
                            if (isSystemMessage(msg.msgType, msg.content)) {
                                SystemMessageRow(
                                    msg = msg,
                                    groupId = group.id,
                                    onOpenUser = onOpenUser,
                                    modifier = messageItemEnter(),
                                )
                            } else {
                            val mine = me != null && msg.userId == me.id
                            MessageRow(
                                msg = msg,
                                mine = mine,
                                canRecall = !msg.isRecalled && (mine || isAdmin),
                                onLongPress = { actionTarget = msg },
                                highlight = highlightId == msg.id || unreadAnchorId == msg.id,
                                modifier = messageItemEnter(),
                                // 消息里的链接: 用内置浏览器打开
                                onOpenLink = { url ->
                                    if (onOpenWeb != null) {
                                        onOpenWeb(url, group.name)
                                    } else {
                                        onToast("没有可用的内置浏览器")
                                    }
                                },
                                // 点头像/昵称: 打开用户主页 (含自己, 主页里自己看不显示「发消息」);
                                // 没接主页时退回旧的成员操作面板
                                onAvatarTap = {
                                    if (onOpenUser != null) {
                                        onOpenUser(msg.userId, group.id)
                                    } else {
                                        openMemberPanel(msg)
                                    }
                                },
                                // 点图片: 全屏查看大图
                                onImageTap = { url -> previewImage = url },
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

            // ===== 右下角「回到最新消息」(问题3): 用户上翻看历史时出现, 一点回最新 =====
            // 贴在这个消息列表区域的右下角 (输入框之上, 不挡输入框); 在底部时淡出隐藏
            // 这里外层是 Column, 直接写 AnimatedVisibility 会被解析成 ColumnScope 的扩展重载而报错,
            // 因此显式写成顶层函数 (它内部依然能用到 BoxScope 的 Modifier.align)
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
                    // 上翻期间新增的消息条数 (点按钮回到底部后清零)
                    if (newWhileAway > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 1.dp, y = (-1).dp)
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

        // ===== 全员禁言横幅 (v1.1.1): 管理员开了之后, 普通成员只能看不能发 =====
        val canSpeak = (!group.allMuted || isAdmin) && group.isMember
        if (!canSpeak && group.isMember) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.error.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "全员禁言中, 仅群管理员可发言",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                )
            }
        }

        // ===== 底部输入区 (QQ 那种布局: 左边「+」, 中间输入框占满, 右边发送) =====
        // 契约 B3: 还没加入这个群 → 输入区整条换成「加入群聊」按钮 (先加入才能发消息)
        if (!group.isMember) {
            JoinGroupBar(joining = joining, onJoin = { joinGroup() })
        }
        // 已加入: 正常输入区
        if (group.isMember) {
        Row(
            modifier = Modifier
                // 契约 C: 输入栏玻璃化 —— 固定层, 允许 backdrop 模糊, 拿不到就退化为半透明底
                .fillMaxWidth()
                .glassSurface(
                    // 同上: 输入栏在捕获层内部, 不能自引用 backdrop
                    backdrop = null,
                    shape = RoundedCornerShape(topStart = GlassRadius.panel, topEnd = GlassRadius.panel),
                    fill = MiuixTheme.colorScheme.surface.copy(alpha = 0.90f),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 「+」: 图片 / @某人 / (管理员) 群公告 都收进这个菜单。
            // 原来「公告」「图片」「@」三个按钮并排摆着, 把输入框挤得又窄又不齐 (用户反馈布局有问题)
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f))
                        .border(1.dp, glassStroke(), CircleShape)
                        .clickable { if (canSpeak) plusMenuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "图片 / @某人 / 群公告",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(
                    expanded = plusMenuOpen,
                    onDismissRequest = { plusMenuOpen = false },
                ) {
                    // 图片: 系统相册选图 → 上传 → 发送 (上传/发送期间禁用, 避免重复上传)
                    DropdownMenuItem(
                        text = { Text(if (uploading) "图片 (上传中…)" else "图片") },
                        enabled = !uploading && !sending,
                        onClick = {
                            plusMenuOpen = false
                            pickChatImage.launch("image/*")
                        },
                    )
                    // @某人: 只是入口从原来的「@」按钮挪进了这里, 功能不变
                    DropdownMenuItem(
                        text = { Text("@某人") },
                        onClick = {
                            plusMenuOpen = false
                            openMentionPicker()
                        },
                    )
                    // 抽奖: 打开抽奖界面 (全屏 Dialog 承载 LotteryScreen)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CardGiftcard,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("抽奖")
                            }
                        },
                        onClick = {
                            plusMenuOpen = false
                            showLottery = true
                        },
                    )
                    // 管理员: 顺手把群公告的入口也收进来
                    if (isAdmin) {
                        DropdownMenuItem(
                            text = { Text(if (noticeText.isBlank()) "发布群公告" else "编辑群公告") },
                            onClick = {
                                plusMenuOpen = false
                                menu.draft = noticeText
                                menu.showNoticeEditor = true
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = input,
                    enabled = canSpeak,
                    // 契约 C: 药丸输入框
                    shape = RoundedCornerShape(22.dp),
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
                            text = if (canSpeak) "说点什么… 用 @昵称 提醒对方" else "全员禁言中, 仅群管理员可发言",
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
            // 发送: 发送中/上传中都给文案反馈, 上传期间不允许重复发送
            Card(
                onClick = { if (!uploading && !sending) doSend() },
                modifier = Modifier,
                cornerRadius = 18.dp,
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
                // 弹框是独立窗口, 用不了页面上的根容器, 所以这里单独挂一份「点空白取消选中」
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            // 弹框里点空白处同样清掉长按选中 (弹框是独立窗口, 要单独挂一份)
                            detectTapGestures { selectionReset++ }
                        },
                ) {
                    key(selectionReset) {
                        SelectionContainer {
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
                        }
                    }
                }
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
                                    if (m.isAdmin) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "管理员",
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                    } else if (m.role == "owner" || m.role == "admin") {
                                        // role 是「群角色」(契约 A3): 群主 / 群管理员
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (m.role == "owner") "群主" else "群管理员",
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

    // ===== 长按消息: 引用 / 复制 / 放大 / 保存到相册 / 撤回 =====
    // 竖排菜单 (原来横着塞在 dismissButton 里, 图片消息加了「放大」「保存」之后放不下)
    val acting = actionTarget
    if (acting != null) {
        val canRecall = !acting.isRecalled && (me != null && acting.userId == me.id || isAdmin)
        val isImage = acting.image.isNotBlank()
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = {
                Text(text = "消息操作", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Column {
                    Text(
                        text = acting.nickname.ifBlank { "群成员" } + ": " +
                            (acting.content.ifBlank { "[图片]" }).replace('\n', ' ').take(60),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    MessageActionRow(text = "引用") {
                        actionTarget = null
                        quoteMessage(acting)
                    }
                    // 图片消息才有: 全屏看大图 / 存进系统相册
                    if (isImage) {
                        MessageActionRow(text = "放大查看") {
                            actionTarget = null
                            previewImage = acting.image
                        }
                        MessageActionRow(text = "保存到相册") {
                            actionTarget = null
                            saveImageToGallery(acting.image)
                        }
                    }
                    // 复制: 把这句原文放进系统剪贴板。纯图片消息没有文字, 不显示「复制」
                    if (acting.content.isNotBlank()) {
                        MessageActionRow(text = "复制") {
                            actionTarget = null
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            if (cm != null) {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("群消息", acting.content))
                                onToast("已复制")
                            } else {
                                onToast("复制失败")
                            }
                        }
                    }
                    // 管理员: 从消息菜单也能进「成员操作」(禁言) 面板 ——
                    // 点头像现在是进个人主页 (主页里也能禁言), 这里保证老入口不丢
                    if (isAdmin) {
                        MessageActionRow(text = "成员操作") {
                            val t = acting
                            actionTarget = null
                            openMemberPanel(t)
                        }
                    }
                    // 撤回权限: 自己的消息 or 管理员, 已撤回的不再给入口
                    if (canRecall) {
                        MessageActionRow(text = if (isImage) "删除图片" else "撤回", danger = true) {
                            actionTarget = null
                            recallTarget = acting
                        }
                    }
                }
            },
            confirmButton = {
                M3TextButton(onClick = { actionTarget = null }) {
                    Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
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
                        // 时长档位与「个人主页 → 禁言」共用同一份 MUTE_OPTIONS (components/UserTagChips.kt)
                        MuteOptionPicker(
                            selected = muteMinutes,
                            onSelect = { muteMinutes = it },
                        )
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

    // ===== 全屏查看图片 (点气泡里的图打开, 点任意处 / 右上角关闭) =====
    if (previewImage.isNotBlank()) {
        Dialog(
            onDismissRequest = { previewImage = "" },
            // usePlatformDefaultWidth = false: 让 Dialog 窗口铺满屏幕, 不然只能占中间一小块
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { previewImage = "" },
            ) {
                AsyncImage(
                    model = previewImage,
                    contentDescription = "查看大图",
                    contentScale = ContentScale.Fit,
                    // fillMaxSize + Fit: 无论横竖图都完整显示在屏幕内, 不会被裁掉
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .align(Alignment.Center),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 保存: 把当前这张原图写进系统相册 (自己点自己的图也能存)
                    Text(
                        text = "保存",
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { saveImageToGallery(previewImage) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "关闭",
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable { previewImage = "" }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    // ===== 抽奖 (输入区「+」菜单进入): 全屏 Dialog 承载 LotteryScreen =====
    if (showLottery) {
        Dialog(
            onDismissRequest = { showLottery = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background),
            ) {
                LotteryScreen(onBack = { showLottery = false }, onToast = onToast)
            }
        }
    }
}

/** 「消息操作」菜单里的一行 (整行可点, 左对齐; danger = 撤回这类删除性操作) */
@Composable
private fun MessageActionRow(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = if (danger) Color(0xFFE5484D) else MiuixTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    )
}

/**
 * 消息 item 进场动画 (契约 C)
 *
 * 只保留淡入: 不启用 placement 动画 —— 否则「加载更早的消息」往列表顶部插数据时,
 * 整列消息会被动画推着走, 和顶部锚定逻辑打架。
 */
private fun LazyItemScope.messageItemEnter(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(durationMillis = 200),
    placementSpec = null,
    fadeOutSpec = null,
)

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
    /** 点气泡里的图片: 上层打开全屏查看 */
    onImageTap: (String) -> Unit = {},
    /** 从通知定位过来的那条消息: 给个底色方便一眼看到 */
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 撤回的消息: 居中灰字提示, 不显示气泡
    if (msg.isRecalled) {
        Box(
            modifier = modifier
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
        modifier = modifier
            .fillMaxWidth()
            // 契约 C: 未读/定位高亮改柔和 (原来是生硬的琥珀色块)
            .background(
                if (highlight) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent,
            )
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
                        fontWeight = FontWeight.SemiBold,
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
                    // 管理员给这个用户打的标签 (防骗警示): 最多 2 个, 多了折成 +N
                    if (msg.tags.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        TagChips(tags = msg.tags, max = 2, small = true)
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
                    // 契约 C: 时间戳更轻, 不抢消息内容
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.72f),
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
            // 契约 C: 大圆角 + 靠自己那一侧的「尾角」; 别人的气泡补一道细玻璃描边做层次
            val bubbleShape = if (mine) {
                RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
            } else {
                RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
            }
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(
                        if (mine) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .then(
                        if (mine) Modifier else Modifier.border(1.dp, glassStroke(), bubbleShape),
                    )
                    // 长按气泡的任意位置都弹「消息操作」: 图片 / 引用块 / 留白都算,
                    // 原来只有文字那一小块能长按, 图片消息长按没反应 (用户反馈)
                    .pointerInput(msg.id) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Column {
                    // 图片消息: 按 image_w/image_h 比例排版 (最长边 200dp, 竖图不会撑满屏幕),
                    // 只传 http 地址给 Coil (Coil 2.x 不支持 base64 data URI)
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
                                // 点一下 = 全屏看大图, 长按 = 消息操作 (引用 / 保存到相册 / 撤回)
                                .pointerInput(msg.id) {
                                    detectTapGestures(
                                        onTap = { onImageTap(msg.image) },
                                        onLongPress = { onLongPress() },
                                    )
                                },
                        )
                        if (msg.content.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
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
                    if (msg.content.isNotBlank()) {
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
            }
        }
        if (mine) {
            Spacer(Modifier.width(8.dp))
            // 自己的头像也能点开主页 (主页里自己看不显示「发消息」按钮)
            Box(
                modifier = Modifier.pointerInput(msg.userId) {
                    detectTapGestures(onTap = { onAvatarTap() })
                },
            ) {
                MessageAvatar(url = msg.avatar, name = msg.nickname)
            }
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


/**
 * 契约 B3: 未加入这个群时的底部整条按钮 —— 「先加入才能发消息」。
 *
 * 加入成功后服务端会多记一位成员, 群里也会出现一条系统消息「xxx加入了群聊」。
 */
@Composable
private fun JoinGroupBar(joining: Boolean, onJoin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "加入群聊后才能在这里发言 (加入后你会成为群成员之一)",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(
            onClick = { if (!joining) onJoin() },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 12.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (joining) "加入中…" else "加入群聊",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}


/**
 * 契约 B5: 群聊里的系统消息 (「xxx加入了群聊」/「xxx退出了群聊」)。
 *
 * 样式对齐 QQ: 整行居中、灰字、小字, 没有头像也没有气泡, 因此也不进消息长按菜单
 * (引用/撤回/复制/保存都不该出现在系统消息上)。
 *
 * 唯一的交互是「点人名」: 名字用主色调并且可点, 点了进这个人的主页 (带上群 id,
 * 主页里能看到他在这个群里的禁言状态), 名字后面跟身份标签 chips (和聊天页其它地方一致)。
 * 消息体里如果没有带 tags (服务端系统消息一般不带), 就按 user_id 拉一次 user_profile 补上。
 */
@Composable
private fun SystemMessageRow(
    msg: SocialMessage,
    groupId: Int,
    onOpenUser: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 标签: 先用消息自带的, 没有就拉一次主页接口补 (remember(msg.id) 保证只拉一次)
    var tags by remember(msg.id) { mutableStateOf(msg.tags) }
    LaunchedEffect(msg.id, groupId) {
        if (tags.isEmpty() && msg.userId > 0) {
            ApiClient.userProfile(msg.userId, groupId).onSuccess { p -> tags = p.tags }
        }
    }
    val nickname = msg.nickname.ifBlank { "" }
    val text = msg.content.ifBlank { nickname + "加入了群聊" }
    val nameStart = if (nickname.isNotEmpty()) text.indexOf(nickname) else -1
    val hasName = msg.userId > 0 && nameStart >= 0
    val gray = MiuixTheme.colorScheme.onBackgroundVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasName) {
                Text(
                    text = text.substring(0, nameStart),
                    fontSize = 11.sp,
                    color = gray,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = nickname,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (onOpenUser != null) {
                                onOpenUser(msg.userId, groupId)
                            } else {
                                Toast.makeText(context, nickname, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 2.dp),
                )
                // 身份标签 (管理员/防骗警示之类), 没有就不占位置
                TagChips(
                    tags = tags,
                    max = 2,
                    small = true,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Text(
                    text = text.substring(nameStart + nickname.length),
                    fontSize = 11.sp,
                    color = gray,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(text = text, fontSize = 11.sp, color = gray, textAlign = TextAlign.Center)
            }
        }
    }
}
