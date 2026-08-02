package com.fengling.share.ui.main

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
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
import com.fengling.share.ui.components.navigation.LiquidBottomBar
import com.fengling.share.ui.components.rememberGlassBackdrop2
import com.fengling.share.ui.main.explore.ExploreScreen
import com.fengling.share.ui.main.home.HomeScreen
import com.fengling.share.ui.main.my.ContributorsScreen
import com.fengling.share.ui.main.my.MyScreen
import com.fengling.share.ui.update.UpdateScreen
import kotlinx.coroutines.launch
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
 * - 液态玻璃: 页面级 backdrop 捕获 + 玻璃底栏
 * - Navigation 返回栈: main → detail → webview
 * - WebView 实例复用
 */
@Composable
fun MainScreen(
    onThemeChanged: (ThemeMode) -> Unit = {},
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // 共享 WebView 实例 (Activity 生命周期内复用)
    val context = LocalContext.current
    val sharedWebView = remember { WebView(context.applicationContext) }

    // 液态玻璃 backdrop (kyant/backdrop, 内容捕获 + 底栏模糊)
    val (backdrop, captureModifier) = rememberGlassBackdrop2()

    val tabs = remember {
        listOf(
            NavTab("首页", Icons.Outlined.Home),
            NavTab("分类", Icons.Outlined.Category),
            NavTab("关于", Icons.Outlined.Info),
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

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
                // 公告里的链接 → 内置浏览器打开
                showNotice = false
                navController.navigate(Routes.webview(url, "公告详情"))
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

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            if (Settings.predictiveBackEnabled) {
                slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))
            } else {
                EnterTransition.None
            }
        },
        exitTransition = {
            if (Settings.predictiveBackEnabled) fadeOut(tween(300)) else ExitTransition.None
        },
        popEnterTransition = {
            if (Settings.predictiveBackEnabled) fadeIn(tween(300)) else EnterTransition.None
        },
        popExitTransition = {
            if (Settings.predictiveBackEnabled) {
                slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(300))
            } else {
                ExitTransition.None
            }
        },
    ) {
        composable(Routes.MAIN) {
            // OShin 式玻璃底栏: 内容捕获 + 底栏模糊覆盖 (不用 Scaffold bottomBar 槽位)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background),
            ) {
                // 内容区 (捕获背景供玻璃模糊)
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(captureModifier)
                        .padding(bottom = 84.dp), // 给玻璃底栏留空间
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
                                onOpenUrl = { url, _ ->
                                    // 外置浏览器打开 (不用内置 WebView)
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                                },
                            )
                            1 -> ExploreScreen(onAppClick = { navController.navigate(Routes.detail(it)) })
                            else -> MyScreen(
                                onThemeChanged = onThemeChanged,
                                onOpenWeb = { url, _ ->
                                    // 外置浏览器打开
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                                },
                                onOpenUpdate = { info ->
                                    navController.navigate(Routes.update(info))
                                },
                                onOpenContributors = {
                                    navController.navigate(Routes.CONTRIBUTORS)
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
                onOpenWeb = { url, _, _ ->
                    // 外置浏览器打开 (不用内置 WebView)
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
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
                sharedWebView = sharedWebView,
                onBack = { navController.popBackStack() },
            )
        }

        // 投稿名单页
        composable(Routes.CONTRIBUTORS) {
            ContributorsScreen(
                onBack = { navController.popBackStack() },
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
 * 公告弹窗 (首页弹出, 富文本 HTML 渲染 + 美化)
 * - 渐变顶部 + 关闭按钮 + 圆角大卡片
 * - WebView 渲染公告 HTML (支持加粗/变色/图片/列表)
 * - 点击链接 → onLinkClick (App 内置浏览器打开)
 * - 「今日不再提示」复选框: 勾选后关闭时记录当天, 当天不再弹
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

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                // 顶部渐变横幅
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MiuixTheme.colorScheme.primary,
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.7f),
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Campaign,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "公告",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        // 关闭按钮
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                // WebView 渲染公告 HTML
                val webView = remember { WebView(context.applicationContext) }
                LaunchedEffect(content) {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val u = url ?: return false
                            if (u.startsWith("http://") || u.startsWith("https://")) {
                                onLinkClick(u)
                                return true
                            }
                            return false
                        }
                    }
                    // 深色模式: 页面底色随主题, 文字默认色适配
                    val bg = if (isDark) "#1C1B1F" else "#FFFFFF"
                    val text = if (isDark) "#E6E1E5" else "#1A1A2E"
                    val css = "<style>body{background:$bg;color:$text;font-size:15px;line-height:1.7;padding:0;margin:0;word-break:break-word;} a{color:#4C6FFF;} img{max-width:100%;border-radius:10px;} h1,h2,h3{color:${if (isDark) "#FFFFFF" else "#1A1A2E"};}</style>"
                    val fullHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">$css</head><body>$content</body></html>"
                    webView.loadDataWithBaseURL(null, fullHtml, "text/html", "utf-8", null)
                }
                AndroidView(
                    factory = { webView },
                    update = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 420.dp)
                        .padding(horizontal = 4.dp),
                )
                // 底部操作区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    // 今日不再提示复选框
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onNoMoreTodayChange(!noMoreToday) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = noMoreToday,
                            onCheckedChange = { onNoMoreTodayChange(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MiuixTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "今日不再提示",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // 知道了按钮 (主色胶囊)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.primary)
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "知道了",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
