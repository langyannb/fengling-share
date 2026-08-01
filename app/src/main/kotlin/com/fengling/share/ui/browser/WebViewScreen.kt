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
    // 返回栈状态跟踪 (解决 BackHandler enabled 陈旧问题)
    var canGoBack by remember { mutableStateOf(sharedWebView.canGoBack()) }

    val webView = sharedWebView

    // 配置 WebView (只配置一次)
    LaunchedEffect(webView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.databaseEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        // UA 伪装: 去掉 WebView 标识, 伪装成手机 Chrome (UC网盘等站点检测 WebView UA 会拦截)
        settings.userAgentString =
            android.webkit.WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace("Version/4.0", "")
                .replace("Version/4.0 Mobile", "")
                .trim() + " Mobile"
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                // 拦截 UC 网盘唤端跳转: 提取真实分享地址跳系统浏览器(UC)打开, 不再落到 m.uc.cn 官网
                val u = request.url.toString()
                if (isUcCallUrl(view, u)) return true
                return handleProtocolUrl(view, u)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (isUcCallUrl(view, url)) return true
                return handleProtocolUrl(view, url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // 页面跳转后更新返回栈状态
                canGoBack = view?.canGoBack() ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // 加载完成后更新 (goBack 后 canGoBack 可能变 false)
                canGoBack = view?.canGoBack() ?: false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
                // 进度变化时同步更新 (goBack 前后)
                canGoBack = view.canGoBack()
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

    // 返回键: 始终启用, 内部判断 —— 有 WebView 历史先回退, 无历史才关闭
    BackHandler {
        if (canGoBack) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = pageTitle,
                onBack = {
                    if (canGoBack) webView.goBack() else onBack()
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

/**
 * 处理协议跳转链接 (UC 网盘等站点使用 intent:// / uclink:// / ucfp: 等私有协议)
 * 规则: 非 http(s) scheme 全部拦截 —— 先提取 url 参数在 WebView 内加载,
 * 提取失败则 Intent.parseUri 打开, 再失败外部浏览器打开, 绝不留给 WebView 显示乱码
 */
private fun handleProtocolUrl(view: WebView, url: String): Boolean {
    // UC 剪贴板/唤端协议 (UCFP:xxx:https://m.uc.cn), 外部打开只会拉起UC浏览器, 直接吞掉
    if (url.lowercase().startsWith("ucfp:")) return true
    // 提取 scheme (intent:// 的 scheme 在 #Intent;scheme=xxx;end 里)
    var scheme = try { Uri.parse(url).scheme?.lowercase() ?: "" } catch (_: Exception) { "" }
    if (url.startsWith("intent://")) {
        scheme = Regex("scheme=([^;]+)").find(url)?.groupValues?.get(1)?.lowercase() ?: "intent"
    }
    if (scheme == "http" || scheme == "https") return false

    // 1) 提取 url 参数 (uclink:// 和 intent:// 的 action 部分都带 url= 参数)
    val target = extractUcUrlParam(url)
    if (!target.isNullOrBlank()) {
        // UC网盘App路由链接(www.uc.cn+clouddrive_params)在WebView里只会显示
        // UC浏览器官网首页, 提取pwd_id重写为网页版分享详情页
        view.loadUrl(rewriteUcShareUrl(target))
        return true
    }

    // 2) intent:// 用 Intent.parseUri 打开 (系统会路由到对应 App)
    if (url.startsWith("intent://")) {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            view.context.startActivity(intent)
            return true
        } catch (_: Exception) { }
    }

    // 3) 外部浏览器打开
    try {
        view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) { }
    return true
}

/**
 * UC 网盘唤端跳转处理: ucbrowser/newUlcall 是 UC 系页面"唤起UC浏览器/UC App"的接口,
 * 直接放行会跳到系统 UC 浏览器并打开 m.uc.cn 官网。这里拦截后从唤端参数里提取
 * 真实分享地址(drive.uc.cn/s/{pwd_id}), 用系统浏览器(UC)打开, 让用户能在 UC 环境查看分享。
 * 提取失败则吞掉(避免落到 m.uc.cn 官网)。
 */
private fun isUcCallUrl(view: WebView, url: String): Boolean {
    val lower = url.lowercase()
    val isCall = lower.contains("/ucbrowser/newulcall") ||
        lower.contains("/ucbrowser/ulcall") ||
        lower.contains("/new_ul_call") ||
        lower.startsWith("ucbrowser:")
    if (!isCall) return false
    // 从唤端参数提取真实分享地址, 跳系统浏览器(UC)打开
    val shareUrl = extractShareFromCall(url)
    if (shareUrl != null) {
        try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl)))
        } catch (_: Exception) { }
    }
    return true
}

/**
 * 从唤端 URL (newUlcall?ucLink=...&url=...) 提取真实分享地址:
 * 1) ucLink 参数 (uclink://...&url={landingPage} 格式) → 提取 url → 重写为分享页
 * 2) 唤端 URL 直接带 url 参数
 */
private fun extractShareFromCall(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        // 1) ucLink 参数
        val ucLink = uri.getQueryParameter("ucLink") ?: uri.getQueryParameter("uc_link") ?: ""
        if (ucLink.isNotEmpty()) {
            val target = extractUcUrlParam(ucLink)
            if (!target.isNullOrBlank()) return rewriteUcShareUrl(target)
        }
        // 2) 直接 url 参数
        val direct = uri.getQueryParameter("url") ?: ""
        if (direct.startsWith("http")) return rewriteUcShareUrl(direct)
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * 从 uclink:// / intent:// 链接提取 url 参数 (即 landingPage, 双重URL编码)
 * 例: uclink://www.uc.cn/xxx?action=open_url&url=https%3A%2F%2Fwww.uc.cn%2F...%23Intent;scheme=uclink;end
 * → https://www.uc.cn/?entry=buwang_view_on_app&uc_flutter_route=...&clouddrive_params={...}
 */
private fun extractUcUrlParam(url: String): String? {
    return try {
        val actionPart = url
            .substringBefore("#Intent;")
            .removePrefix("intent://")
            .removePrefix("uclink://")
        if (actionPart.isEmpty()) return null
        val target = Uri.parse("https://$actionPart").getQueryParameter("url")
        if (!target.isNullOrBlank() && target.startsWith("http")) target else null
    } catch (_: Exception) {
        null
    }
}

/**
 * UC网盘 App 路由链接重写为网页版分享页
 *
 * UC网盘分享出去的链接是 uclink:// intent, 其中的 url 参数指向
 * https://www.uc.cn/?entry=buwang_view_on_app&uc_flutter_route=/clouddrive/main&clouddrive_params={...}
 * —— 这是给 UC App 内部使用的路由, 浏览器/WebView 打开只会落到 www.uc.cn 官网首页。
 * 这里从 clouddrive_params(URL编码的JSON)提取 additionProps.pwd_id,
 * 重写为网页版分享详情页 https://drive.uc.cn/s/{pwd_id}
 */
private fun rewriteUcShareUrl(target: String): String {
    return try {
        val uri = Uri.parse(target)
        val host = uri.host?.lowercase() ?: ""
        val cdp = uri.getQueryParameter("clouddrive_params") ?: ""
        val flutterRoute = uri.getQueryParameter("uc_flutter_route") ?: ""
        // 仅处理 UC App 路由场景 (www.uc.cn + clouddrive_params / flutter 路由)
        if (host.contains("uc.cn") && (cdp.isNotEmpty() || flutterRoute.isNotEmpty())) {
            // getQueryParameter 已解码一层, 再解码一次得到 JSON
            val jsonStr = java.net.URLDecoder.decode(cdp, "UTF-8")
            val obj = org.json.JSONObject(jsonStr)
            val pwdId = obj.optJSONObject("additionProps")?.optString("pwd_id") ?: ""
            if (pwdId.isNotEmpty()) "https://drive.uc.cn/s/$pwdId" else target
        } else if (host.contains("uc.cn")) {
            // 兜底: URL 直接带 pwd_id 参数 (无 clouddrive_params JSON)
            val pwdId = uri.getQueryParameter("pwd_id") ?: ""
            if (pwdId.isNotEmpty()) "https://drive.uc.cn/s/$pwdId" else target
        } else {
            target
        }
    } catch (_: Exception) {
        target
    }
}
