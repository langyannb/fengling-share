package com.fengling.share.ui.main

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.fengling.share.data.ApiClient
import com.fengling.share.data.MessageStream
import com.fengling.share.data.PendingNav
import com.fengling.share.service.MessageService
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.data.NoticeInfo
import com.fengling.share.data.StreamEvent
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore
import com.fengling.share.ui.theme.isAppInDarkTheme
import com.fengling.share.utils.NotifySound
import com.fengling.share.data.VersionInfo
import com.fengling.share.ui.book.detail.DetailScreen
import com.fengling.share.ui.browser.WebViewScreen
import com.fengling.share.ui.download.DownloadScreen
import com.fengling.share.ui.components.AppScaffold
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.fengling.share.ui.components.ExternalJumpDialog
import com.fengling.share.ui.components.ExternalJumpTarget
import com.fengling.share.ui.components.resolveExternalJump
import com.fengling.share.ui.components.navigation.MainBottomBar
import com.fengling.share.ui.components.rememberGlassBackdrop
import com.fengling.share.ui.components.rememberBlurBackdrop
import com.fengling.share.ui.theme.LocalEnableBlur
import com.fengling.share.ui.theme.LocalFloatingBottomBar
import com.fengling.share.ui.theme.LocalGlassBottomBar
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import com.fengling.share.ui.theme.LocalPredictiveBack
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import com.fengling.share.ui.main.explore.ExploreScreen
import com.fengling.share.ui.main.home.HomeScreen
import com.fengling.share.ui.main.my.AccountScreen
import com.fengling.share.ui.main.my.ContributorsScreen
import com.fengling.share.ui.main.my.MessagesScreen
import com.fengling.share.ui.main.my.NotifySettingsScreen
import com.fengling.share.ui.main.my.ThemeSettingsScreen
import com.fengling.share.ui.pm.PmChatScreen
import com.fengling.share.ui.user.UserProfileScreen
import com.fengling.share.ui.main.my.MyScreen
import com.fengling.share.ui.social.GroupInfoScreen
import com.fengling.share.ui.social.GroupMemberListScreen
import com.fengling.share.ui.social.SocialScreen
import com.fengling.share.ui.update.UpdateScreen
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 底部导航 tab */
private data class NavTab(val label: String, val icon: ImageVector)

/** 「群组」在底部 tab 里的下标 (未登录时会被拦到登录页) */
private const val SOCIAL_TAB_PAGE = 2

/**
 * 目的地注册。
 *
 * 转场策略 (契约 B 的替代版):
 *   - 全局默认走 [NavTransitions.MiuixDefault]: 进入页从右侧整宽滑入, 被覆盖页 1/4 宽视差 + 轻微变暗。
 *     这套映射同时被「程序化 push/pop」和「手势跟手」复用 —— 同一个 float 驱动深度,
 *     所以抬手收尾不会有第二套曲线, 也就不存在旧实现里「两层预测返回 / 抬手卡一下」。
 *   - 用户在主题设置里关掉「预测性返回」时, 全局换成 [NavTransitions.None]:
 *     identity 变换 + dismissDirection = None —— 返回照常可用, 但没有任何跟手位移。
 *     这是真正让开关生效的地方 (旧实现里 Settings.predictiveBack 只写进了 CompositionLocal,
 *     全项目没有一处读它, 所以关了也照旧有动画)。
 */
@Composable
private fun AppNavDisplay(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    content: @Composable (Route) -> Unit,
) {
    val predictiveBack = LocalPredictiveBack.current
    val transition = if (predictiveBack) NavTransitions.MiuixDefault else NavTransitions.None
    // 屏幕圆角跟随设备 (卡片式进场时四角裁切才对得上机身)
    val cornerRadius = rememberNavSystemCornerRadius()
    // ⚠️ enableCornerClip 必须关掉, 否则内置浏览器返回时白闪。
    // 圆角裁剪走 miuix-squircle: 节点尺寸 > 2048px 时是 contentLayer.record{ drawContent() }
    // + drawLayer 的重放路径 (小尺寸则走 CompositingStrategy.Offscreen 离屏层), 而
    // NavCornerClipMode.Leading 的裁剪区间是 -1 < d < 0 —— 正好覆盖整个返回/进场动画。
    // AndroidView 承载的 WebView 是独立硬件 surface, 塞进离屏层重放后内容直接丢失,
    // 用户看到的就是「网页闪一下白然后不见了」。圆角只是修饰, 不能拿它换白屏。
    val effects = remember(cornerRadius) {
        NavDisplayEffects(
            enableCornerClip = false,
            cornerClipRadius = cornerRadius,
            dimAmount = 0.5f,
        )
    }
    // NavDisplay 内部 remember(content) 缓存了入口注册块, 所以开关变化时必须整体重建,
    // 否则 swipeDismiss / transition 改了也不生效。
    key(predictiveBack) {
        NavDisplay(
            backStack = navigator.backStack,
            modifier = modifier,
            onBack = { navigator.pop() },
            transition = transition,
            effects = effects,
        ) {
            entry<Page>(
                contentKey = { it },
                // 侧滑返回只在栈深 >1 时由库自己门控; 关掉预测返回时整个手势禁用
                swipeDismiss = if (predictiveBack) NavSwipeDirection.LeftToRight
                else NavSwipeDirection.None,
            ) { page ->
                content(page.route)
            }
        }
    }
}

@Composable
fun MainScreen(
    onThemeChanged: () -> Unit = {},
) {
    val navigator = rememberAppNavigator()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 所有内部导航统一走这里。
    // 返回栈由 miuix-nav 的 NavDisplay 驱动: 手势进度直接喂给同一个深度 float,
    // 上一级是**活的**页面 (不用截图), 所以既没有空白也没有「两层预测返回」。
    fun go(route: Route) {
        navigator.push(route)
    }

    fun back() {
        navigator.pop()
    }

    // 玻璃 backdrop 现在全项目只有一套 (miuix-blur), 见 ui/components/GlassCard.kt 的说明。
    // v1.1.20 起这里不再建「页面级捕获层」给 SocialScreen: 那个层每帧都会把整屏录一遍
    // GraphicsLayer, 却只被分段控件采样一次; 而分段控件真正想模糊的是它下面的群列表,
    // 那份内容 SocialScreen 自己用一个局部层就够 (而且页面级层套住分段控件自身会自引用,
    // hwui 的 RenderNode 树无限递归 → 真机 SIGSEGV, 已经踩过一次)。
    val enableBlur = LocalEnableBlur.current
    val floatingBar = LocalFloatingBottomBar.current
    val glassBar = LocalGlassBottomBar.current
    // 关掉模糊时返回 null: BlurredBar 收到 null 就不挂 textureBlur, 走不透明样式 (参考项目同款判断)
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    // 悬浮胶囊的透镜采样源: 只有「悬浮底栏 + 玻璃质感」都开才需要捕获层
    val glassCaptureNeeded = floatingBar && glassBar && enableBlur
    val (barBackdrop, barCaptureModifier) = rememberGlassBackdrop(enabled = glassCaptureNeeded)

    val tabs = remember {
        listOf(
            NavTab("首页", Icons.Filled.Home),
            NavTab("分类", Icons.Filled.Category),
            NavTab("群组", Icons.Filled.Forum),
            NavTab("关于", Icons.Filled.Info),
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // 底栏数据 (miuix 的 NavigationItem), tab 定义在 tabs 里, 这里只做类型转换
    val navItems = remember(tabs) { tabs.map { NavigationItem(label = it.label, icon = it.icon) } }

    // 契约 A(强化): 「群组 / 私聊」分段的选中值提升到这里 (MainScreen 常驻组合) 持有。
    // 原来放在 SocialScreen 内部 —— 导航返回能靠 rememberSaveable 保住, 但切底部 Tab 时
    // pager 页面会被回收重建, 页面内状态照样丢。放这里就不随任何页面销毁:
    // 1 私聊进会话返回仍停「私聊」 2 群聊返回仍停「群组」 3 切底部 Tab 来回切仍保持 4 冷启动默认「群组」
    var socialTab by rememberSaveable { mutableIntStateOf(0) }

    // 私聊新消息提示音 (不管停在哪个 tab 都能听到; 见文件末尾 PmNotifyWatcher)
    PmNotifyWatcher()

    // ---------- 点通知直达会话 (v1.0.35) ----------
    // 链路: 通知 PendingIntent 带 open_pm_conv / open_group -> MainActivity 解析写 PendingNav
    //      -> 这里收集后真正导航 -> 立刻 consume 清空 (免得重复跳)。
    // 复用消息中心那条老路径 (MessagesScreen 里 pm:<cid>:<mid> 的处理方式): 先切底部 tab, 再压栈到会话页。
    LaunchedEffect(Unit) {
        PendingNav.target.collect { target ->
            val t = target ?: return@collect
            PendingNav.consume()
            // 下载管理不需要登录, 先处理它
            if (t.openDownloads) {
                go(Route.Downloads)
                return@collect
            }
            if (!UserStore.isLoggedIn()) return@collect
            when {
                t.groupId > 0 -> {
                    // 群聊在「群组」tab 里
                    pagerState.scrollToPage(SOCIAL_TAB_PAGE)
                    go(Route.SocialGroup(t.groupId))
                }
                t.pmConvId > 0 -> {
                    // 私聊会话列表也在「群组」tab 里, 先切过去再压栈, 返回时落点才自然
                    pagerState.scrollToPage(SOCIAL_TAB_PAGE)
                    go(Route.PmChat(convId = t.pmConvId))
                }
            }
        }
    }

    // 未登录时左右滑动也不许停在「群组」页 (3 = 0 首页 / 1 分类 / 2 群组 / 3 关于)
    var lastAllowedPage by remember { mutableStateOf(0) }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            if (pagerState.currentPage == SOCIAL_TAB_PAGE && !UserStore.isLoggedIn()) {
                go(Route.Account)
                pagerState.animateScrollToPage(lastAllowedPage)
            } else {
                lastAllowedPage = pagerState.currentPage
            }
        }
    }

    // ===== 外部应用跳转确认 (2026-10-02: 用户要求跳转前先问一句) =====
    // 以前 mailto/tel/mqqwpa/uclink 等协议是直接 startActivity 抢跳, 现在一律先弹确认框
    var pendingExternal by remember { mutableStateOf<ExternalJumpTarget?>(null) }

    /**
     * 统一链接分流: http(s) → 内置浏览器;
     * 其它 scheme → 记下来弹确认框, 用户点「打开」才真跳, 点「取消」什么都不做
     */
    fun openLink(url: String, title: String, password: String = "") {
        val target = resolveExternalJump(context, url)
        if (target != null) {
            pendingExternal = target
            return
        }
        // 腾讯频道 / QQ群 一律保持在内置浏览器打开 (2026-10-03 用户明确要求)。
        // 之前「交 QQ 客户端打开」的方案已撤销; 页面完整渲染改由 WebView 侧保障
        // (CookieManager 放开第三方 cookie + 反爬挑战页等待 + 渲染能力补齐), 见 WebViewScreen.kt。
        go(Route.WebView(url, title, password))
    }

    // ===== 公告 (首页弹窗: 每日一次 / 每次打开 + 今日不再提示) =====
    // 语义: 「今日不再提示」只对当时那条公告当天生效; 公告内容一变 (hidden/shown content != 当前), 当天也重新弹
    var noticeInfo by remember { mutableStateOf<NoticeInfo?>(null) }
    var showNotice by remember { mutableStateOf(false) }
    var noMoreToday by remember { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    LaunchedEffect(Unit) {
        try {
            val info = ApiClient.getNotice()
            if (info.enabled && info.content.isNotBlank()) {
                val hiddenDate = Settings.noticeHiddenDate
                val hiddenContent = Settings.noticeHiddenContent
                val shownDate = Settings.noticeShownDate
                val shownContent = Settings.noticeShownContent
                val content = info.content
                val canShow = when (info.mode) {
                    // 每日一次: 今天没弹过, 或 (今天弹过但公告改了 → 重新弹)
                    "daily" -> shownDate != today || shownContent != content
                    // 每次打开: 没勾今日不再, 或 (勾过但公告改了 → 重新弹)
                    else -> hiddenDate != today || hiddenContent != content
                }
                if (canShow) {
                    noticeInfo = info
                    showNotice = true
                    // 记录本次弹窗 (daily 模式: 今天已弹过 + 弹的是哪条内容)
                    if (info.mode == "daily") {
                        Settings.noticeShownDate = today
                        Settings.noticeShownContent = content
                    }
                }
            }
        } catch (_: Exception) { }
    }
    if (showNotice) {
        NoticeDialog(
            info = noticeInfo,
            noMoreToday = noMoreToday,
            onNoMoreTodayChange = { noMoreToday = it },
            onLinkClick = { url ->
                // 公告里的链接统一分流: http(s) 内置浏览器, 自定义协议 (mqqwpa:// 等) 交系统应用
                showNotice = false
                openLink(url, "公告详情")
            },
            onDismiss = {
                if (noMoreToday) {
                    // 今日不再提示: 记录日期 + 当前公告内容 (公告改了当天也会重新弹)
                    Settings.noticeHiddenDate = today
                    Settings.noticeHiddenContent = noticeInfo?.content ?: ""
                }
                showNotice = false
            },
        )
    }

    // 外部应用跳转确认框 (公告/关于页/详情页里点了自定义协议链接时弹出)
    pendingExternal?.let { target ->
        ExternalJumpDialog(
            target = target,
            onConfirm = {
                val opened = runCatching { context.startActivity(target.intent) }.isSuccess
                if (!opened) {
                    // 没有应用能接管 → http(s) 回退内置浏览器, 不让用户卡住 (2026-10-03)
                    if (target.url.startsWith("http")) {
                        go(Route.WebView(target.url, target.appLabel))
                    } else {
                        Toast.makeText(
                            context,
                            "没有找到可以打开该链接的应用",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                pendingExternal = null
            },
            onDismiss = { pendingExternal = null },
        )
    }

    // 保底: 返回栈外层铺一层不透明的 App 渐变底, 转场期间露出的永远是 App 底色,
    // 不会再露出窗口底色 (用户曾报的「返回过程一片空白」)。静态绘制, 不写状态。
    Box(modifier = Modifier.fillMaxSize().appGradientBackground()) {
    AppNavDisplay(navigator = navigator, modifier = Modifier.fillMaxSize()) { route ->
        when (route) {
        is Route.Main -> {
            // 非首个 tab 按返回 → 收回「首页」。
            // Main 在栈底, 即使被上层页面盖住也仍然在组合中, 所以必须显式判「是不是栈顶」:
            // 一旦压上详情页, 这里 enabled = false, 返回整个交回 NavDisplay 的预测返回。
            // (BackHandler 的优先级是后组合的赢, 上层页面的 handler 也会盖过这里, 双保险)
            val onMainRoute = navigator.top == Route.Main
            BackHandler(enabled = onMainRoute && pagerState.currentPage != 0) {
                scope.launch { pagerState.animateScrollToPage(0) }
            }
            // 底栏摆放照抄参考项目: 用 Scaffold 的 bottomBar 槽位, 不再把底栏叠在内容 Box 上。
            // containerColor 透明 —— 底下那层 appGradientBackground 要能透出来。
            // contentWindowInsets 归零 —— 内容区本来就是 edge-to-edge 全屏, 各页面自己处理状态栏,
            // 交给 Scaffold 补 systemBars 只会多出一份顶部内边距。
            // 键盘弹出时把底栏收起来。
            // 不加这个的话, 系统 adjustResize 会先把窗口压矮, 底栏仍占着 ~80dp 底部空间,
            // 输入栏被挤到键盘上方紧贴底栏的位置 —— 用户看到的「输入框会沉下去一点」就是它。
            // 聊天页自己会随窗口收缩, 底栏在这里纯属多余, 收掉才是 Telegram 那种观感。
            val density = LocalDensity.current
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    // 键盘弹起时整条底栏收掉 (AnimatedVisibility 让收放跟着键盘一起滑, 不会硬跳)
                    AnimatedVisibility(
                        visible = !imeVisible,
                        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                        // 底栏: 照抄参考项目的 BottomBarMiuix ——
                        // 关悬浮 → BlurredBar + NavigationBar (原生);
                        // 开悬浮 → FloatingBottomBar (液体玻璃胶囊, 可拖动切 tab)
                        MainBottomBar(
                            items = navItems,
                            // 用 targetPage 而不是 currentPage:
                            // currentPage 是「离视口中心最近的页」, 翻页动画途中会逐页跳变 →
                            // 底栏高亮跟着闪, 配合 FloatingBottomBar 的联动就会互相抢动画。
                            // targetPage 只在滑动/点击确定目标后才变, 点哪亮哪, 动画途中不抖。
                            selectedIndex = { pagerState.targetPage },
                            onSelect = { index ->
                                if (index == SOCIAL_TAB_PAGE && !UserStore.isLoggedIn()) {
                                    // 未登录点「群组」: 直接去登录页, 不切到这个 tab (用户 2026-10-04 要求)
                                    go(Route.Account)
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            },
                            blurBackdrop = blurBackdrop,
                            backdrop = barBackdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                        }
                    }
                },
            ) { _ ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background),
            ) {
                // 内容区: 全屏 (无底部 padding) —— 列表必须能滚动到悬浮胶囊下方,
                // 捕获层才能拿到真实内容做模糊; 底部留白由各页面 LazyColumn
                // contentPadding bottom 自行处理 (OShin 同款布局)。
                // 注意: 这里故意不消费 Scaffold 的 innerPadding, 否则会和页面自带的
                // 100/110.dp 底部留白叠成双份。
                // 这里不挂任何 predictiveBackTransform: 主页面在返回栈栈底,
                // 页面本身没有「上一层」可揭示, 自绘跟手只会和框架的预测返回叠成两层。
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(barCaptureModifier),
                ) {
                    // HorizontalPager: tab 左右滑动切换
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 2,
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onAppClick = { go(Route.Detail(it)) },
                                onOpenUrl = { url, title ->
                                    // 内置浏览器打开
                                    go(Route.WebView(url, title))
                                },
                            )
                            1 -> ExploreScreen(onAppClick = { go(Route.Detail(it)) })
                            2 -> SocialScreen(
                                // 「群组」tab: 常驻列表页, 无返回栏; 点群组进全屏聊天
                                onBack = null,
                                // 契约 A: 分段选中值由本层持有并回传 (受控), 页面重建也不丢
                                initialTab = socialTab,
                                onTabChange = { socialTab = it },
                                onOpenGroup = { g -> go(Route.SocialGroup(g.id)) },
                                // 群聊消息里的链接: 内置浏览器打开
                                onOpenWeb = { url, title -> openLink(url, title) },
                                onNeedLogin = { go(Route.Account) },
                                // 点群聊里任何人的头像: 打开用户主页 (带上群 id, 主页里可直接禁言)
                                onOpenUser = { uid, gid -> go(Route.UserProfile(uid, gid)) },

                                // 私聊会话列表点一条: 进私聊会话页
                                onOpenPm = { uid, cid -> go(Route.PmChat(convId = cid, userId = uid)) },
                                // 群列表点「有人@你 / 有人@所有人」: 进群并定位到那条消息
                                onOpenGroupAt = { gid, mid -> go(Route.SocialGroup(gid, mid)) },
                            )
                            else -> MyScreen(
                                onOpenThemeSettings = {
                                    go(Route.ThemeSettings)
                                },
                                onOpenWeb = { url, title ->
                                    // 自定义协议先弹确认框, http(s) 内置浏览器
                                    openLink(url, title)
                                },
                                onOpenUpdate = { info ->
                                    go(Route.Update(info.version, info.url, info.updateLog, info.updateMode, info.sizeMb, info.releaseDate))
                                },
                                onOpenContributors = {
                                    go(Route.Contributors)
                                },
                                onOpenAccount = {
                                    go(Route.Account)
                                },
                                onOpenMessages = {
                                    go(Route.Messages)
                                },
                                onOpenNotifySettings = {
                                    go(Route.NotifySettings)
                                },
                                onOpenDownloads = {
                                    go(Route.Downloads)
                                },
                            )
                        }
                    }
                }
            }
            }
        }

        // 详情页
        is Route.Detail -> {
            DetailScreen(
                appId = route.appId,
                onBack = { back() },
                onOpenWeb = { url, title, password ->
                    // 自定义协议先弹确认框, http(s) 内置浏览器
                    openLink(url, title, password)
                },
                onOpenSubApp = { subId ->
                    go(Route.Detail(subId))
                },
            )
        }

        // 内置浏览器
        is Route.WebView -> {
            WebViewScreen(
                url = route.url,
                title = route.title,
                password = route.password,
                onBack = { back() },
                onOpenDownloads = { go(Route.Downloads) },
            )
        }

        // 下载管理
        is Route.Downloads -> {
            DownloadScreen(onBack = { back() })
        }

        // 投稿名单页
        is Route.Contributors -> {
            ContributorsScreen(
                onBack = { back() },
            )
        }

        // 账号页 (登录 / 注册 / 个人资料)
        is Route.Account -> {
            AccountScreen(
                onBack = { back() },
            )
        }

        // 群组聊天页 (从「群组」tab 点进来, 直接进入指定群)
        is Route.SocialGroup -> {
            SocialScreen(
                onBack = {
                    // 返回时直接回到主界面(群组 tab 的列表页), 不停在中间层
                    navigator.popToRoot()
                },
                initialGroupId = route.groupId,
                onNeedLogin = { go(Route.Account) },
                initialMessageId = route.messageId,
                openNotice = route.notice,
                // 群聊消息里的链接: 内置浏览器打开
                onOpenWeb = { url, title -> openLink(url, title) },
                // 群聊里点头像: 带上当前群 id → 主页里就能直接禁言 (只对本群生效)
                onOpenUser = { uid, gid -> go(Route.UserProfile(uid, gid)) },
                onOpenPm = { uid, cid -> go(Route.PmChat(convId = cid, userId = uid)) },
                onOpenGroupAt = { gid, mid -> go(Route.SocialGroup(gid, mid)) },
                // 群聊右上角「☰」: 进群详情页 (群公告 / 群相册 / 群成员 / 退出群聊)
                onOpenGroupInfo = { gid -> go(Route.GroupInfo(gid)) },
            )
        }

        // 群详情页: 群公告 / 群相册 / 群成员 / 消息免打扰 / 全员禁言 / 退出群聊
        is Route.GroupInfo -> {
            GroupInfoScreen(
                groupId = route.groupId,
                onBack = { back() },
                onOpenMembers = { id -> go(Route.GroupMembers(id)) },
                onOpenUser = { uid, id -> go(Route.UserProfile(uid, id)) },
                // 群公告里的链接: 走内置浏览器
                onOpenWeb = { url, title -> openLink(url, title) },
                // 退出群聊成功: 直接回群列表 (群详情页 + 群聊页一起弹掉)
                onLeft = { navigator.popToRoot() },
            )
        }

        // 群成员列表页: 搜索昵称 / 用户名 + 分页 + 点成员进主页
        is Route.GroupMembers -> {
            GroupMemberListScreen(
                groupId = route.groupId,
                onBack = { back() },
                groupName = route.name,
                onOpenUser = { uid, gid -> go(Route.UserProfile(uid, gid)) },
            )
        }

        // 消息中心 (通知列表; link 走统一链接分流, http(s) 内置浏览器)
        is Route.Messages -> {
            MessagesScreen(
                onBack = { back() },
                onOpenWeb = { url, title ->
                    openLink(url, title)
                },
                // @我 / 群消息通知: 跳进对应群聊并定位到那条消息; 群公告通知: 进群并弹出公告
                onOpenGroup = { groupId, messageId, showNotice ->
                    go(Route.SocialGroup(groupId, messageId, showNotice))
                },
                // 私聊通知 (link = pm:<conv_id>:<msg_id>): 进私聊会话并定位到那条消息
                onOpenPm = { convId, messageId ->
                    go(Route.PmChat(convId = convId, messageId = messageId))
                },
            )
        }

        // 消息通知设置 (我的 -> 消息通知): 后台接收开关 + 权限引导
        is Route.NotifySettings -> {
            NotifySettingsScreen(
                onBack = { back() },
            )
        }

        // 主题设置 (我的 -> 主题): 明暗 / 主题色 / 色板风格 / 模糊 / 悬浮底栏 / 预测性返回
        is Route.ThemeSettings -> {
            ThemeSettingsScreen(
                onBack = { back() },
                onChanged = onThemeChanged,
            )
        }

        // 用户主页 (群聊 / 私聊里点任何人的头像进入, 含自己; 自己看时没有「发消息」按钮)
        is Route.UserProfile -> {
            UserProfileScreen(
                userId = route.userId,
                groupId = route.groupId,
                onBack = { back() },
                onOpenPm = { uid, cid -> go(Route.PmChat(convId = cid, userId = uid)) },
                // 用户简介里的链接: 走内置浏览器
                onOpenWeb = { url, title -> openLink(url, title) },
            )
        }

        // 私聊会话页 (会话列表 / 用户主页「发消息」/ 私聊通知进入)
        is Route.PmChat -> {
            PmChatScreen(
                initialConvId = route.convId,
                initialUserId = route.userId,
                initialMessageId = route.messageId,
                onBack = { back() },
                onOpenWeb = { url, title -> openLink(url, title) },
                // 私聊里点头像: 没有群上下文 → groupId = 0 (全站视角)
                onOpenUser = { uid -> go(Route.UserProfile(uid)) },
            )
        }

        // 软件更新页 (OShin 同款: 下载并安装)
        is Route.Update -> {
            UpdateScreen(
                info = VersionInfo(
                    version = route.version,
                    url = route.url,
                    updateLog = route.log,
                    updateMode = route.mode,
                    sizeMb = route.sizeMb,
                    releaseDate = route.date,
                ),
                onBack = { back() },
            )
        }
        }
    }
    }
}

/**
 * 公告弹窗 (首页弹出, 富文本 HTML 渲染)
 * 2026-10-02 重新设计: 去掉高饱和渐变横幅 (塑料感来源) → 浅色图标 + 通透留白;
 * WebView 底色改透明、融进圆角容器, 消除「白块拼贴」; 进出场缩放淡入。
 */
@Composable
private fun NoticeDialog(
    info: NoticeInfo?,
    noMoreToday: Boolean,
    onNoMoreTodayChange: (Boolean) -> Unit,
    onLinkClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val content = info?.content ?: ""
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    val scheme = MiuixTheme.colorScheme

    // 不做任何入场动画: Dialog 遮罩一出现, 卡片就是最终状态。
    // 历史坑: ①欠阻尼 spring 缩放 + alpha 淡入 → 回弹 + 遮罩空窗;
    //         ②animateContentSize 跟随 WebView 加载时的高度多帧变化,
    //           默认 spring(StiffnessMediumLow) 会让卡片缓慢弹性伸缩 —— 用户反馈的「很抖很抖慢慢的」。

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Miuix 官方 Card (自带正确的圆角与配色, 不再手搓 clip+background)
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                // ── 头部: 左对齐标题 + 淡色关闭按钮 (去掉彩色圆底图标, 不再有模板感) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "公告",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = scheme.onBackgroundVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.surfaceVariant),
                )

                // ── 内容: WebView 直接铺在卡片上 (透明底, 不再套一层灰色容器) ──
                // layoutParams 必须显式设: Compose 的 AndroidView 不替子 View 设 LayoutParams,
                // 拿到 0 高的容器时 WebView 会把 CSS 百分比高度全解析成 0 (与内置浏览器同一个坑)。
                val webView = remember {
                    WebView(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                }
                LaunchedEffect(content) {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val u = url ?: return false
                            // 链接统一交上层分流 (http(s) 内置浏览器; 自定义协议弹确认框)
                            onLinkClick(u)
                            return true
                        }
                    }
                    val textColor = if (isDark) "#E6E1E5" else "#1A1A2E"
                    val css = "<style>" +
                        "html,body{background:transparent;color:$textColor;font-size:14.5px;line-height:1.75;word-break:break-word;}" +
                        "body{padding:0;margin:0;}" +
                        "p{margin:0 0 8px 0;} p:last-child{margin-bottom:0;}" +
                        "a{color:#4C6FFF;text-decoration:none;font-weight:500;}" +
                        "img{max-width:100%;border-radius:10px;display:block;margin:8px 0;}" +
                        "h1,h2,h3{font-size:16px;font-weight:600;color:$textColor;margin:10px 0 6px;}" +
                        "ul,ol{padding-left:20px;margin:6px 0;}" +
                        "blockquote{margin:8px 0;padding:8px 12px;border-left:3px solid #4C6FFF;background:rgba(127,127,127,0.08);border-radius:8px;}" +
                        "</style>"
                    val fullHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">$css</head><body>$content</body></html>"
                    webView.loadDataWithBaseURL(null, fullHtml, "text/html", "utf-8", null)
                }
                AndroidView(
                    factory = { webView },
                    update = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 80.dp, max = 300.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.surfaceVariant),
                )

                // ── 底部: 方形勾选 (原生质感) + 主按钮 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onNoMoreTodayChange(!noMoreToday) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (noMoreToday) scheme.primary else Color.Transparent)
                                .then(
                                    if (noMoreToday) Modifier
                                    else Modifier.border(
                                        BorderStroke(1.5.dp, scheme.onBackgroundVariant.copy(alpha = 0.45f)),
                                        RoundedCornerShape(5.dp),
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (noMoreToday) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = scheme.onPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "今日不再提示",
                            fontSize = 13.sp,
                            color = scheme.onBackgroundVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Miuix 官方 Button (自带按压反馈与涟漪, 不手搓 Box + clickable)
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        cornerRadius = 23.dp,
                    ) {
                        Text(
                            text = "我知道了",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 私聊新消息提示音 (仅 App 前台生效)
 *
 * 每 8 秒静默拉一次私聊会话列表, 发现「别人发来的」新消息就响一声系统通知音。
 * 放在 MainScreen 里做, 这样用户停在哪个页面都能听到, 不用每个页面各写一份。
 *
 * 注意: 只在 App 处于前台时轮询 (ON_RESUME ~ ON_PAUSE)。想做到「退到后台也响」
 * 必须有常驻前台服务, 属于另一个量级的改动, 暂未做。
 */
@Composable
private fun PmNotifyWatcher() {
    val context = LocalContext.current
    val foreground = rememberAppForeground()
    // 已经见过的最新私聊消息 id; -1 = 还没建立基线 (首次只记录, 不响, 免得一进 App 就响)
    var seenMaxId by remember { mutableStateOf(-1) }

    // ---------- 兜底轮询 (SSE 挂了 / 断线窗口里也能响) ----------
    // 每 8 秒静默拉一次私聊会话列表, 发现「别人发来的」新消息就响一声。
    // ⚠️ 接上 SSE 后这条轮询**保留不动** (契约: 现有轮询一律保留作兜底)。
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            if (UserStore.isLoggedIn()) {
                ApiClient.pmConversations().getOrNull()?.let { page ->
                    // 只看「别人发给我的」最后一条: 自己发的不算, 撤回后字段也会是空
                    val newestIncoming = page.list
                        .mapNotNull { it.last }
                        .filter { !it.mine }
                        .maxOfOrNull { it.id } ?: 0
                    when {
                        seenMaxId < 0 -> seenMaxId = newestIncoming
                        newestIncoming > seenMaxId -> {
                            seenMaxId = newestIncoming
                            NotifySound.play(context)
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(4000L)
        }
    }

    // ---------- 后台常驻服务: 按「已登录 + 开关」启停 (v1.0.35) ----------
    // 那条唯一的 SSE 连接从 MainScreen 搬到了 MessageService (前台服务): 退到后台/桌面也不断,
    // 才能像 QQ/微信一样秒收。这里只负责把「该不该跑」这个状态摆正, 不再按 foreground 断连。
    // - 已登录 + 开关打开 -> 拉起服务 (幂等, 已在跑就什么都不做)
    // - 未登录 -> 停掉服务 (没 token 挂着也是白发一条常驻通知)
    // 用户在设置页关开关时那边会自己 stopService, 这里不会把它又拉起来。
    // key 带上 foreground: 从设置页返回/回到前台时重新对一次状态。
    LaunchedEffect(foreground, UserStore.hasToken) {
        when {
            UserStore.isLoggedIn() && Settings.msgServiceOn -> MessageService.start(context)
            !UserStore.isLoggedIn() -> MessageService.stop(context)
        }
    }

    // 订阅流事件:
    // - 私聊 -> 响
    // - 群消息 -> 也响 (v1.1.1: 群里任何人发消息都有提示音, 和 QQ/微信一致);
    //   自己发的消息不响; 该群被我设了「消息免打扰」(event.muted) 时不响
    // - Reconnected -> 这里什么都不用做 (各页面自己补一次全量刷新)
    LaunchedEffect(Unit) {
        MessageStream.events.collect { event ->
            when (event) {
                is StreamEvent.Pm -> {
                    // 同步兜底轮询的基线, 免得 8 秒后同一条消息又响一次
                    if (event.msgId > seenMaxId) seenMaxId = event.msgId
                    NotifySound.play(context)
                }
                is StreamEvent.Group -> {
                    val mine = event.userId != 0 && event.userId == UserStore.current?.id
                    if (!mine && !event.muted) NotifySound.play(context)
                }
                StreamEvent.Reconnected -> Unit
            }
        }
    }
}

/**
 * 本页面是否处于前台 (ON_RESUME ~ ON_PAUSE)
 *
 * 从 LocalContext 拿 ComponentActivity 注册 LifecycleEventObserver,
 * 不引额外依赖 (Lifecycle/ LifecycleEventObserver 来自项目已有的 lifecycle-common)。
 */
@Composable
private fun rememberAppForeground(): Boolean {
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
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return foreground
}
