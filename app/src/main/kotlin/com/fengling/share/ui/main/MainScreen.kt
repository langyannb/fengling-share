package com.fengling.share.ui.main

import android.webkit.WebView
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.fengling.share.ui.main.my.MyScreen
import com.fengling.share.ui.update.UpdateScreen
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 底部导航 tab */
private data class NavTab(val label: String, val icon: ImageVector)

/** 导航路由 */
object Routes {
    const val MAIN = "main"
    const val DETAIL = "detail/{appId}"
    const val WEBVIEW = "webview?url={url}&title={title}&password={password}"
    const val UPDATE = "update?version={version}&url={url}&log={log}&mode={mode}&size={size}&date={date}"

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
                                onOpenUrl = { url, title ->
                                    navController.navigate(Routes.webview(url, title))
                                },
                            )
                            1 -> ExploreScreen(onAppClick = { navController.navigate(Routes.detail(it)) })
                            else -> MyScreen(
                                onThemeChanged = onThemeChanged,
                                onOpenWeb = { url, title ->
                                    navController.navigate(Routes.webview(url, title))
                                },
                                onOpenUpdate = { info ->
                                    navController.navigate(Routes.update(info))
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
                    navController.navigate(Routes.webview(url, title, password))
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
