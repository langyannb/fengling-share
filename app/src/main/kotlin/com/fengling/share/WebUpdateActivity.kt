package com.fengling.share

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
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
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress

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
    // 契约 B: 预测性返回(跟手) —— 有网页历史先跟手回退网页, 没有历史整页退回上一页
    val backProgress = rememberPredictiveBackProgress(enabled = true) {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    // 配置 WebView (一次性, 与主浏览器同配置)
    remember {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.databaseEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        // UA 伪装 (UC网盘等站点检测 WebView UA 会拦截)
        settings.userAgentString =
            android.webkit.WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace("Version/4.0", "")
                .trim() + " Mobile"
        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)
    }

    Scaffold(
        modifier = Modifier.predictiveBackTransform(backProgress),
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
