package com.fengling.share.ui.main

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * MainScreen - 主界面 (参考 legado-with-MD3 MainScreen)
 * MD3 Scaffold + 底部导航 + 页面切换 + 覆盖层(详情/浏览器)
 * 预测性返回: 覆盖层打开时返回手势关闭覆盖层
 */
@Composable
fun MainScreen() {
    var currentTab by remember { mutableIntStateOf(0) }
    var currentAppId by remember { mutableStateOf<Int?>(null) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var webTitle by remember { mutableStateOf("") }

    val tabs = remember {
        listOf(
            NavTab("home", "首页", Icons.Filled.Home),
            NavTab("explore", "分类", Icons.Filled.Category),
            NavTab("my", "我的", Icons.Filled.Person),
        )
    }

    // 预测性返回: 有覆盖层时返回关闭覆盖层
    PredictiveBackHandler(enabled = currentAppId != null || webUrl != null) {
        if (webUrl != null) {
            webUrl = null
        } else if (currentAppId != null) {
            currentAppId = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppScaffold(
            bottomBar = {
                // 覆盖层打开时隐藏底栏
                if (currentAppId == null && webUrl == null) {
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
                }
            },
        ) { innerPadding ->
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(200),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> HomeScreen(onAppClick = { currentAppId = it })
                    1 -> ExploreScreen(onAppClick = { currentAppId = it })
                    else -> MyScreen()
                }
            }
        }

        // 详情覆盖层
        currentAppId?.let { appId ->
            Crossfade(
                targetState = appId,
                animationSpec = tween(200),
                modifier = Modifier.fillMaxSize(),
            ) { id ->
                DetailScreen(
                    appId = id,
                    onBack = { currentAppId = null },
                    onOpenWeb = { url, title ->
                        currentAppId = null
                        webUrl = url
                        webTitle = title
                    },
                )
            }
        }

        // 内置浏览器覆盖层
        webUrl?.let { url ->
            Crossfade(
                targetState = url,
                animationSpec = tween(200),
                modifier = Modifier.fillMaxSize(),
            ) { u ->
                WebViewScreen(
                    url = u,
                    title = webTitle,
                    onBack = { webUrl = null },
                )
            }
        }
    }
}
