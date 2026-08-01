package com.fengling.share

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.fengling.share.ui.components.AppTopBar

/**
 * WebUpdateActivity - 内置更新浏览器
 * 独立 Activity (不依赖主界面导航), 用于强制/内置更新打开下载页
 */
class WebUpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: run { finish(); return }
        enableEdgeToEdge()
        setContent {
            WebUpdateScreen(url = url, onBack = { finish() })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebUpdateScreen(url: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val webView = remember { WebView(context) }
    BackHandler { onBack() }

    // 配置 WebView (一次性)
    remember {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "更新下载", onBack = onBack)
        },
    ) { innerPadding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
