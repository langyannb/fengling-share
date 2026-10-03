package com.fengling.share.ui.main

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fengling.share.data.ApiClient
import com.fengling.share.data.NoticeInfo
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.data.VersionInfo
import com.fengling.share.ui.book.detail.DetailScreen
import com.fengling.share.ui.browser.WebViewScreen
import com.fengling.share.ui.components.AppScaffold
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.fengling.share.ui.components.ExternalJumpDialog
import com.fengling.share.ui.components.ExternalJumpTarget
import com.fengling.share.ui.components.resolveExternalJump
import com.fengling.share.ui.components.navigation.LiquidBottomBar
import com.fengling.share.ui.components.rememberGlassBackdrop2
import com.fengling.share.ui.main.explore.ExploreScreen
import com.fengling.share.ui.main.home.HomeScreen
import com.fengling.share.ui.main.my.AccountScreen
import com.fengling.share.ui.main.my.ContributorsScreen
import com.fengling.share.ui.main.my.MessagesScreen
import com.fengling.share.ui.main.my.MyScreen
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

/** 导航路由 */
object Routes {
    const val MAIN = "main"
    const val DETAIL = "detail/{appId}"
    const val WEBVIEW = "webview?url={url}&title={title}&password={password}"
    const val UPDATE = "update?version={version}&url={url}&log={log}&mode={mode}&size={size}&date={date}"
    const val CONTRIBUTORS = "contributors"
    const val ACCOUNT = "account"
    // 社交群组聊天 / 消息中心 (从「我的」页入口进入, 不占用底部 tab)
    const val SOCIAL = "social"
    const val MESSAGES = "messages"

    fun detail(appId: Int) = "detail/$appId"
    fun webview(url: String, title: String, password: String = "") =
        "webview?url=${android.net.Uri.encode(url)}&title=${android.net.Uri.encode(title)}&password=${android.net.Uri.encode(password)}"
    fun update(info: com.fengling.share.data.VersionInfo) =
        "update?version=${android.net.Uri.encode(info.version)}&url=${android.net.Uri.encode(info.url)}" +
            "&log=${android.net.Uri.encode(info.updateLog)}&mode=${android.net.Uri.encode(info.updateMode)}" +
            "&size=${info.sizeMb}&date=${android.net.Uri.encode(info.releaseDate)}"
}

/**
 * MainScreen - 主界面 (OShin 风格重构)
 * - HorizontalPager: tab 左右滑动切换 (平滑滑动动画)
 * - 液态玻璃: 页面级 backdrop 捕获 + 玻璃底栏 (OShin 同款 kyant/backdrop)
 * - Navigation 返回栈: main → detail → webview
 * - WebViewScreen 每次进入创建全新实例 (不复用, 避免历史栈残留)
 */

@Composable
fun MainScreen(
    onThemeChanged: (ThemeMode) -> Unit = {},
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 液态玻璃 backdrop (kyant/backdrop, 内容捕获 + 底栏模糊) — OShin 同款方案
    val (backdrop, captureModifier) = rememberGlassBackdrop2()

    val tabs = remember {
        listOf(
            NavTab("首页", Icons.Filled.Home),
            NavTab("分类", Icons.Filled.Category),
            NavTab("关于", Icons.Filled.Info),
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

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
        navController.navigate(Routes.webview(url, title, password))
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
                        navController.navigate(Routes.webview(target.url, target.appLabel))
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

    // 进入 = 滑动切换动画 (用户定案: 点击应用右滑进入, 旧页左滑出)
    // 返回 = 系统预测返回动画 (跟手缩放回上一级, 不要滑出):
    // manifest enableOnBackInvokedCallback=true 时系统手势跟手,
    // 提交后 pop 转场用缩放+淡出延续预测返回观感 (官方 predictive-back)
    val slideSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val popSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { slideInHorizontally(slideSpec) { it } },
        exitTransition = { slideOutHorizontally(slideSpec) { -it } },
        // 返回: 预测返回风格 — 当前页缩小淡出, 上一级放大淡入
        popEnterTransition = {
            scaleIn(initialScale = 0.95f, animationSpec = popSpec) + fadeIn(animationSpec = popSpec)
        },
        popExitTransition = {
            scaleOut(targetScale = 0.9f, animationSpec = popSpec) + fadeOut(animationSpec = popSpec)
        },
    ) {
        composable(Routes.MAIN) {
            // OShin 式玻璃底栏: 内容捕获 + 底栏模糊覆盖 (kyant/backdrop, 不用 Scaffold bottomBar 槽位)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background),
            ) {
                // 内容区: 全屏 (无底部 padding) —— 列表必须能滚动到悬浮胶囊下方,
                // 捕获层才能拿到真实内容做模糊; 底部留白由各页面 LazyColumn
                // contentPadding bottom 自行处理 (OShin 同款布局)
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(captureModifier),
                ) {
                    // HorizontalPager: tab 左右滑动切换
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 2,
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onAppClick = { navController.navigate(Routes.detail(it)) },
                                onOpenUrl = { url, title ->
                                    // 内置浏览器打开
                                    navController.navigate(Routes.webview(url, title))
                                },
                            )
                            1 -> ExploreScreen(onAppClick = { navController.navigate(Routes.detail(it)) })
                            else -> MyScreen(
                                onThemeChanged = onThemeChanged,
                                onOpenWeb = { url, title ->
                                    // 自定义协议先弹确认框, http(s) 内置浏览器
                                    openLink(url, title)
                                },
                                onOpenUpdate = { info ->
                                    navController.navigate(Routes.update(info))
                                },
                                onOpenContributors = {
                                    navController.navigate(Routes.CONTRIBUTORS)
                                },
                                onOpenAccount = {
                                    navController.navigate(Routes.ACCOUNT)
                                },
                                onOpenSocial = {
                                    navController.navigate(Routes.SOCIAL)
                                },
                                onOpenMessages = {
                                    navController.navigate(Routes.MESSAGES)
                                },
                            )
                        }
                    }
                }

                // OShin 完整版液态玻璃底栏 (拖动切 tab + lens + 高光 + 图标缩放)
                LiquidBottomBar(
                    tabs = tabs.map { it.label to it.icon },
                    pagerState = pagerState,
                    onTabSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // 详情页
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("appId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getInt("appId") ?: 0
            DetailScreen(
                appId = appId,
                onBack = { navController.popBackStack() },
                onOpenWeb = { url, title, password ->
                    // 自定义协议先弹确认框, http(s) 内置浏览器
                    openLink(url, title, password)
                },
                onOpenSubApp = { subId ->
                    navController.navigate(Routes.detail(subId))
                },
            )
        }

        // 内置浏览器
        composable(
            route = Routes.WEBVIEW,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("password") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val password = backStackEntry.arguments?.getString("password") ?: ""
            WebViewScreen(
                url = url,
                title = title,
                password = password,
                onBack = { navController.popBackStack() },
            )
        }

        // 投稿名单页
        composable(Routes.CONTRIBUTORS) {
            ContributorsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 账号页 (登录 / 注册 / 个人资料)
        composable(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 社交页 (群组列表 + 群聊)
        composable(Routes.SOCIAL) {
            SocialScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 消息中心 (通知列表; link 走统一链接分流, http(s) 内置浏览器)
        composable(Routes.MESSAGES) {
            MessagesScreen(
                onBack = { navController.popBackStack() },
                onOpenWeb = { url, title ->
                    openLink(url, title)
                },
            )
        }

        // 软件更新页 (OShin 同款: 下载并安装)
        composable(
            route = Routes.UPDATE,
            arguments = listOf(
                navArgument("version") { type = NavType.StringType; defaultValue = "" },
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("log") { type = NavType.StringType; defaultValue = "" },
                navArgument("mode") { type = NavType.StringType; defaultValue = "internal" },
                navArgument("size") { type = NavType.FloatType; defaultValue = 0f },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            UpdateScreen(
                info = VersionInfo(
                    version = args?.getString("version") ?: "",
                    url = args?.getString("url") ?: "",
                    updateLog = args?.getString("log") ?: "",
                    updateMode = args?.getString("mode") ?: "internal",
                    sizeMb = args?.getFloat("size") ?: 0f,
                    releaseDate = args?.getString("date") ?: "",
                ),
                onBack = { navController.popBackStack() },
            )
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
    val isDark = isSystemInDarkTheme()
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
                val webView = remember { WebView(context) }
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
