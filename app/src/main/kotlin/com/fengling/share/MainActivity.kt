package com.fengling.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fengling.share.ui.DetailScreen
import com.fengling.share.ui.HomeScreen
import com.fengling.share.ui.ProvideNavigationEventDispatcher
import com.fengling.share.ui.WebViewScreen
import com.fengling.share.ui.theme.FenglingTheme
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FenglingTheme {
                ProvideNavigationEventDispatcher {
                    AppRoot()
                }
            }
        }
    }
}

/** 底部导航 tab */
private data class NavTab(val route: String, val label: String, val icon: ImageVector)

/**
 * 应用根: 首页常驻 + 底部导航 + 覆盖层 (详情/内置浏览器)
 * - 底部导航: 首页 / 分类 / 我的 (iOS 悬浮圆角底栏)
 * - 详情页与 WebView 是覆盖层, 交叉淡化进入/退出
 */
@Composable
fun AppRoot() {
    var currentTab by remember { mutableIntStateOf(0) }
    var currentAppId by remember { mutableStateOf<Int?>(null) }
    // 内置浏览器: url + 标题
    var webUrl by remember { mutableStateOf<String?>(null) }
    var webTitle by remember { mutableStateOf("") }

    val tabs = remember {
        listOf(
            NavTab("home", "首页", MiuixIcons.Home),
            NavTab("category", "分类", MiuixIcons.GridView),
            NavTab("mine", "我的", MiuixIcons.Settings),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                // 覆盖层打开时隐藏底栏
                if (currentAppId == null && webUrl == null) {
                    FloatingNavigationBar(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        tabs.forEachIndexed { index, tab ->
                            FloatingNavigationBarItem(
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
            // 层1: tab 内容 (常驻)
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(200),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> HomeScreen(onAppClick = { currentAppId = it })
                    1 -> HomeScreen(onAppClick = { currentAppId = it })
                    else -> MineScreen(onAppClick = { currentAppId = it })
                }
            }
        }

        // 层2: 详情覆盖层
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

        // 层3: 内置浏览器覆盖层
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

/** 我的页 (占位) */
@Composable
private fun MineScreen(onAppClick: (Int) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("我的页面")
    }
}
