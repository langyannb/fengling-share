package com.fengling.share

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
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
                .replace(" Version/4.0", "")
                .replace("Version/4.0 ", "")
                .trim()
                // 默认 UA 本就含 "Mobile Safari/537.36", 再拼一次会得到 "Mobile Mobile",
                // 站点用 UA 正则校验时会判成非标准浏览器 → 下发残缺页面
                .let { if (it.contains("Mobile")) it else "$it Mobile" }
        // 诊断: console / 资源失败全部转发到 logcat (tag: FLWebView), 用于定位 Link3 白屏
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                Log.i("FLWebView", "console: ${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                return true
            }
        }
        // DOM 自检: 与主内置浏览器同款探针, 只在 logcat (tag: FLWebView) 里看
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                v: WebView?, request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                Log.w("FLWebView", "ERR ${if (request?.isForMainFrame == true) "主" else "子"}: ${request?.url} code=${error?.errorCode} desc=${error?.description}")
            }

            override fun onReceivedHttpError(
                v: WebView?, request: android.webkit.WebResourceRequest?,
                r: android.webkit.WebResourceResponse?,
            ) {
                Log.w("FLWebView", "HTTP ${r?.statusCode} ${if (request?.isForMainFrame == true) "主" else "子"}: ${request?.url}")
            }

            override fun onPageFinished(view: WebView?, u: String?) {
                super.onPageFinished(view, u)
                runCatching {
                    view?.evaluateJavascript(
                        """
                        (function(){
                          try {
                            var b=document.body, app=document.getElementById('app');
                            var ld=document.getElementById('index_loading');
                            return JSON.stringify({
                              href: (location&&location.href)||'',
                              vh: window.innerHeight,
                              bodyH: b?b.offsetHeight:-1,
                              appKids: app?app.children.length:-1,
                              textLen: b?b.innerText.length:-1,
                              loading: ld?'yes':'no',
                              uaWv: navigator.userAgent.indexOf(' wv')>=0?'yes':'no',
                              scripts: document.scripts.length
                            });
                          } catch(e) { return 'ERR '+e.message; }
                        })();
                        """
                    ) { r -> Log.i("FLWebView", "[FLdom/act] $r") }
                }
            }
        }
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
