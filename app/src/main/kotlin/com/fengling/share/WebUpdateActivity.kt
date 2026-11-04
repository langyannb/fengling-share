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
    // layoutParams 必须显式设: Compose 的 AndroidView 不替子 View 设 LayoutParams,
    // 拿到 0 高的容器时 WebView 会把页面 CSS 的百分比高度链全解析成 0 → 整页空白,
    // 只有 position:fixed 的元素还能画出来。这是内置浏览器白屏的总根因, 三个 WebView 都要设。
    val webView = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
    // 契约 B: 预测性返回(跟手) —— 有网页历史先跟手回退网页, 没有历史整页退回上一页
    val backProgress = rememberPredictiveBackProgress(enabled = true) {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    // 配置 WebView (一次性, 与主浏览器同配置)
    remember {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // 实验: via / Chrome 默认 loadWithOverviewMode=false, 只有 useWideViewPort=true。
        // 开着 overview 模式时 WebView 会按「页面宽度」整体缩放, 页面宽度计算异常就直接白屏。
        settings.loadWithOverviewMode = false
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

            override fun onPageStarted(view: WebView?, u: String?, f: android.graphics.Bitmap?) {
                // 加载一开始就装错误收集器, 抓 mount 阶段抛出的 JS 异常 (onPageFinished 太晚)
                view?.evaluateJavascript(
                    """
                    (function(){
                      if (window.__flErrHook) return;
                      window.__flErrHook = true; window.__flErrs = [];
                      window.addEventListener('error', function(e){
                        window.__flErrs.push((e&&e.message||'err')+' @'+(e&&e.filename||'').split('/').pop()+':'+(e&&e.lineno||0));
                      }, true);
                    })();
                    """.trimIndent(), null
                )
            }

            override fun onPageFinished(view: WebView?, u: String?) {
                super.onPageFinished(view, u)
                runCatching {
                    view?.evaluateJavascript(
                        """
                        (function(){
                          if (window.__flAct) return 'skip';
                          window.__flAct = true;
                          window.__flErrs = [];
                          window.addEventListener('error', function(e){
                            window.__flErrs.push((e && e.message || 'err') + '@' + (e && e.filename || ''));
                          });
                          var probe = function(tag){
                            try {
                              var de=document.documentElement, b=document.body;
                              var app=document.getElementById('app');
                              var ld=document.getElementById('index_loading');
                              var cs=b?getComputedStyle(b):null;
                              var rb=b?b.getBoundingClientRect():null;
                              var ra=app?app.getBoundingClientRect():null;
                              var rde=de?de.getBoundingClientRect():null;
                              return JSON.stringify({
                                tag: tag,
                                href: (location&&location.href)||'',
                                vh: window.innerHeight, vw: window.innerWidth,
                                deH: de?de.offsetHeight:-1,
                                deRectH: rde?Math.round(rde.height):-1,
                                deScrollH: de?de.scrollHeight:-1,
                                deCS: de?getComputedStyle(de).height:'',
                                bodyH: b?b.offsetHeight:-1,
                                bodyCS: cs?cs.height:'',
                                bodyDisp: cs?cs.display:'',
                                bodyTop: rb?Math.round(rb.top):-1,
                                bodyW: rb?Math.round(rb.width):-1,
                                bodyRectH: rb?Math.round(rb.height):-1,
                                bodyScrollH: b?b.scrollHeight:-1,
                                compat: document.compatMode,
                                appH: app?app.offsetHeight:-1,
                                appTop: ra?Math.round(ra.top):-1,
                                appKids: app?app.children.length:-1,
                                appTxt: app?(app.innerText||'').length:-1,
                                textLen: b?(b.innerText||'').length:-1,
                                loading: ld?('yes h='+ld.offsetHeight+' disp='+getComputedStyle(ld).display):'no',
                                bodyScrollH2: b?b.scrollHeight:-1,
                                appSample: app?((app.innerText||'').replace(/\s+/g,' ').slice(0,120)):'',
                                appChild0: (app&&app.firstElementChild)?(app.firstElementChild.tagName+' disp='+getComputedStyle(app.firstElementChild).display+' h='+Math.round(app.firstElementChild.getBoundingClientRect().height)+' w='+Math.round(app.firstElementChild.getBoundingClientRect().width)):'none',
                                hitCenter: (function(){ try { var e=document.elementFromPoint(Math.round(window.innerWidth/2), Math.round(window.innerHeight/2)); return e?(e.tagName+'#'+(e.id||'')+'.'+(e.className||'').toString().slice(0,40)):'null'; } catch(e){ return 'ERR'; } })(),
                                bodyBg: b?getComputedStyle(b).backgroundColor:'',
                                scripts: document.scripts.length,
                                errs: (window.__flErrs||[]).slice(0,3)
                              });
                            } catch(e) { return 'ERR '+e.message; }
                          };
                          // 废掉「探测到塌陷就改 html/body 为 100vh」那套自愈 ——
                          // 塌陷的根因是 WebView 没拿到有尺寸的容器(见 WebUpdateScreen 里 layoutParams 的注释),
                          // 在外层修掉之后再改页面样式只会把正常页面弄坏。这里只留诊断日志。
                          setTimeout(function(){ console.log('[FLdom/act] '+probe('t1500')); }, 1500);
                          setTimeout(function(){ console.log('[FLdom/act] '+probe('t5000')); }, 5000);
                          return probe('t0');
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
