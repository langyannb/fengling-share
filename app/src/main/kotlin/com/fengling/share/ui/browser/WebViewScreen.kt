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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import com.fengling.share.ui.components.AppTopBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * WebViewScreen - 内置浏览器 (Navigation Compose 返回栈)
 * - WebView 实例复用: sharedWebView 由上层持有, 退出再进不重新加载
 * - 预测性返回: 内部历史回退由 BackHandler 承接, 无历史时 Navigation 返回
 * - 右上角菜单: 复制链接 / 浏览器打开 / 刷新
 *
 * @param sharedWebView 复用的 WebView 实例 (Activity 生命周期内保持)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    title: String,
    password: String = "",
    sharedWebView: WebView,
    onBack: () -> Unit,
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
            AppTopBar(
                title = pageTitle,
                onBack = {
                    if (webView.canGoBack()) webView.goBack() else onBack()
                },
                actions = {
                    // 右上角菜单
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { menuExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "更多",
                                tint = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
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
            // 加载进度条 (顶部细条, 平滑动画)
            val animatedProgress by animateFloatAsState(
                targetValue = progress / 100f,
                animationSpec = tween(250),
                label = "webProgress",
            )
            if (animatedProgress < 1f) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            // 提取码提示条 (网盘链接有密码时显示, 点击复制)
            if (password.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("pwd", password))
                            Toast.makeText(context, "提取码已复制: $password", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "提取码: $password",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "点击复制",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
        }
    }
}
