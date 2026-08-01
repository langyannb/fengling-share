package com.fengling.share.ui.main

import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.book.detail.DetailScreen
import com.fengling.share.ui.browser.WebViewScreen
import com.fengling.share.ui.components.AppScaffold
import com.fengling.share.ui.components.navigation.AppNavigationBar
import com.fengling.share.ui.components.navigation.AppNavigationBarItem
import com.fengling.share.ui.main.explore.ExploreScreen
import com.fengling.share.ui.main.home.HomeScreen
import com.fengling.share.ui.main.my.MyScreen

/** 底部导航 tab */
private data class NavTab(val route: String, val label: String, val icon: ImageVector)

/** 导航路由 */
object Routes {
    const val MAIN = "main"
    const val DETAIL = "detail/{appId}"
    const val WEBVIEW = "webview?url={url}&title={title}&password={password}"

    fun detail(appId: Int) = "detail/$appId"
    fun webview(url: String, title: String, password: String = "") =
        "webview?url=${android.net.Uri.encode(url)}&title=${android.net.Uri.encode(title)}&password=${android.net.Uri.encode(password)}"
}

/**
 * MainScreen - 主界面 (Navigation Compose 返回栈)
 * 结构: main(底部导航) → detail(详情) → webview(内置浏览器)
 * - 转场动画: 水平滑动 + 淡入淡出
 * - 预测性返回: Navigation 2.9 在 Android 13+ 自动启用系统手势动画
 * - WebView 实例复用: 浏览器页不重建, 返回栈 pop 保留状态
 */
@Composable
fun MainScreen(
    onThemeChanged: (ThemeMode) -> Unit = {},
) {
    val navController = rememberNavController()
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    // 共享 WebView 实例 (Activity 生命周期内复用, 退出浏览器再进不重新加载)
    val context = LocalContext.current
    val sharedWebView = remember { WebView(context.applicationContext) }

    val tabs = remember {
        listOf(
            NavTab("home", "首页", Icons.Outlined.Home),
            NavTab("explore", "分类", Icons.Outlined.Category),
            NavTab("settings", "设置", Icons.Outlined.Settings),
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
        // 主界面 (底部导航 + tab 内容)
        composable(Routes.MAIN) {
            AppScaffold(
                bottomBar = {
                    AppNavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            AppNavigationBarItem(
                                selected = currentTab == index,
                                onClick = { currentTab = index },
                                icon = tab.icon,
                                label = tab.label,
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // tab 切换滑动动画 (方向跟随 tab 顺序)
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(280)) { it / 3 * direction } + fadeIn(tween(280)))
                                .togetherWith(
                                    slideOutHorizontally(tween(280)) { -it / 4 * direction } + fadeOut(tween(280))
                                )
                        },
                        label = "tabSwitch",
                    ) { tab ->
                        when (tab) {
                            0 -> HomeScreen(onAppClick = { navController.navigate(Routes.detail(it)) })
                            1 -> ExploreScreen(onAppClick = { navController.navigate(Routes.detail(it)) })
                            else -> MyScreen(onThemeChanged = onThemeChanged)
                        }
                    }
                }
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
    }
}
