package com.fengling.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fengling.share.ui.DetailScreen
import com.fengling.share.ui.HomeScreen
import com.fengling.share.ui.theme.FenglingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FenglingTheme {
                AppRoot()
            }
        }
    }
}

/**
 * 应用根: 首页常驻 + 详情覆盖层
 * - 首页是唯一常驻层, 状态(分类/滚动位置/已加载数据)在返回时保留
 * - 详情是覆盖层, 交叉淡化进入/退出 (无位移动画, 用户偏好)
 */
@Composable
fun AppRoot() {
    // 当前打开的软件 id; null = 首页
    var currentAppId by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize()) {
        // 层1: 首页 (常驻)
        HomeScreen(
            onAppClick = { id -> currentAppId = id },
            modifier = Modifier.fillMaxSize(),
        )

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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
