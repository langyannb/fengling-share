package com.fengling.share.ui.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * WebViewScreen - 内置浏览器 (Navigation Compose 返回栈)
 * - WebView 实例复用: sharedWebView 由上层持有, 退出再进不重新加载
 * - 预测性返回: 内部历史回退由 BackHandler 承接, 无历史时 Navigation 返回
 * - 右上角菜单: 复制链接 / 浏览器打开 / 刷新
 *
 * @param sharedWebView 复用的 WebView 实例 (Activity 生命周期内保持)
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    title: String,
    sharedWebView: WebView,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pageTitle by remember { mutableStateOf(title) }
    var progress by remember { mutableIntStateOf(100) }
    var menuExpanded by remember { mutableStateOf(false) }

    val webView = sharedWebView

    // 配置 WebView (只配置一次)
    LaunchedEffect(webView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = false
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank()) pageTitle = title
            }
        }
    }

    // URL 变化才加载 (新页面加载; 返回时复用 WebView 内部历史不重载)
    LaunchedEffect(webView, url) {
        if (webView.url == null || webView.url != url) {
            webView.loadUrl(url)
        }
    }

    // 页面离开时只停止加载, 不销毁 WebView (复用)
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
        }
    }

    // 返回键: 优先回退 WebView 内部历史, 无历史时由 Navigation 返回栈关闭
    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView.canGoBack()) webView.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 右上角菜单
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制链接") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("url", webView.url ?: "")
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("浏览器打开") },
                                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(webView.url ?: url))
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    webView.reload()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            // 加载进度条 (平滑动画, 避免 onProgressChanged 跳变僵硬)
            val animatedProgress by animateFloatAsState(
                targetValue = progress / 100f,
                animationSpec = tween(250),
                label = "webProgress",
            )
            if (animatedProgress < 1f) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
