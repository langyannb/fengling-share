package com.fengling.share.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.MimeTypeMap
import android.app.DownloadManager
import android.os.Environment
import android.widget.Toast
import com.fengling.share.ui.components.AppTopBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * WebViewScreen - 内置浏览器 (Navigation Compose 返回栈)
 * - WebView 全新实例: 每次进入创建, 离开销毁 (不残留历史栈/状态)
 * - 预测性返回: 内部历史回退由 BackHandler 承接, 无历史时 Navigation 返回
 * - 右上角菜单: 复制链接 / 浏览器打开 / 刷新
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    title: String,
    password: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var pageTitle by remember { mutableStateOf(title) }
    var progress by remember { mutableIntStateOf(100) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 返回栈状态跟踪 (解决 BackHandler enabled 陈旧问题)
    // 每次进入都是全新 WebView 实例, 初始恒为 false
    var canGoBack by remember { mutableStateOf(false) }

    // 每次进入创建全新 WebView 实例, 离开销毁:
    // 复用实例会残留上一链接的内部历史栈 → 返回键 goBack 回到旧链接
    val webView = remember { WebView(context) }

    // 配置 WebView (同步执行, 确保先于 loadUrl; 不用 LaunchedEffect 避免时序竞争导致
    // WebViewClient 未设置 → 页面加载绕过 shouldOverrideUrlLoading 拦截)
    // 文件上传回调 (onShowFileChooser)
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data
            val uris = if (data?.clipData != null) {
                (0 until data.clipData!!.itemCount)
                    .map { data.clipData!!.getItemAt(it).uri }
                    .toTypedArray()
            } else {
                data?.data?.let { arrayOf(it) } ?: emptyArray()
            }
            callback?.onReceiveValue(uris)
        } else {
            callback?.onReceiveValue(null)
        }
    }

    remember(webView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // 正常浏览器配置: 遵循页面 viewport meta
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.databaseEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        // 支持多窗口: window.open 弹新窗 → onCreateWindow 处理
        // (http(s) 当前 WebView 打开; 自定义 scheme 如 uclink:// 交系统, 不拦截)
        settings.setSupportMultipleWindows(true)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        // 禁用深色反色: ColorOS/系统深色模式会强制 WebView 反色页面 (algorithmic darkening),
        // 深色背景页面被反色后内容不可见 (link3.cc 空白实测); 正常浏览器不强制反色
        settings.setAlgorithmicDarkeningAllowed(false)
        // UA 伪装: 去掉 WebView 标识, 伪装成手机 Chrome (UC网盘等站点检测 WebView UA 会拦截)
        settings.userAgentString =
            WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace("Version/4.0", "")
                .replace("Version/4.0 Mobile", "")
                .trim() + " Mobile"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = handleProtocolUrl(view, request.url.toString())

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleProtocolUrl(view, url)

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

            // window.open 新窗口: 自定义 scheme (uclink:// 等) 交给系统; http(s) 用新 WebView 承接 URL 后当前 WebView 打开
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                // 临时 WebView 承接目标 URL (onCreateWindow 无法直接拿到 url, 需经 shouldOverrideUrlLoading)
                val temp = WebView(view.context)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url?.toString() ?: return false
                        if (handleProtocolUrl(view, u)) return true
                        view.loadUrl(u)
                        return true
                    }
                }
                transport.webView = temp
                resultMsg.sendToTarget()
                return true
            }

            // 文件上传 (input[type=file]) → 系统文件选择器
            override fun onShowFileChooser(
                webView: WebView,
                newCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                filePathCallback?.let { it.onReceiveValue(null) }
                filePathCallback = newCallback
                fileChooserLauncher.launch(fileChooserParams.createIntent())
                return true
            }

            // 网页权限请求 (摄像头/麦克风等) → 直接授予 (内置浏览器不拦截)
            override fun onPermissionRequest(request: PermissionRequest) {
                runCatching { request.grant(request.resources) }
            }

            // 定位权限 → 直接授予
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback,
            ) {
                callback.invoke(origin, true, false)
            }
        }

        // 下载处理: 交给系统下载管理器 (不拦截, 完整浏览器行为)
        webView.setDownloadListener(
            DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                try {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype ?: "application/octet-stream")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            downloadFileName(url, contentDisposition, mimetype),
                        )
                    }
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    dm?.enqueue(request)
                    Toast.makeText(context, "开始下载", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    // DownloadManager 失败时回退: 系统浏览器打开
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) { }
                }
            }
        )
    }

    // 每次进入都强制重新加载 (新 WebView 实例, 天然无历史残留)
    // ⚠️ 等 WebView attach 到窗口且有实际尺寸后才 loadUrl:
    // SPA 页面 (link3.cc 等) 在 WebView 未布局时加载会 height:100% 链塌陷 → 内容空白
    var webViewReady by remember { mutableStateOf(false) }
    LaunchedEffect(webView, url, webViewReady) {
        if (!webViewReady) return@LaunchedEffect
        webView.stopLoading()
        webView.clearHistory()
        canGoBack = false
        webView.loadUrl(url)
    }

    // 页面离开时: 停加载 + 销毁 WebView 实例 (每次进入都是全新实例, 不残留历史/状态)
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
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
                modifier = Modifier
                    .fillMaxSize()
                    // WebView 有实际尺寸 (attach 完成) 后才触发加载, 避免 SPA 布局塌陷
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) webViewReady = true
                    },
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
 * 处理协议跳转链接: http(s) 放行给 WebView 正常加载;
 * 非 http(s) scheme (intent:// / uclink:// 等) 正常跳转交给系统处理
 * (Intent.parseUri 或 ACTION_VIEW, 系统会路由到注册了对应 scheme 的应用, 如 UC 浏览器),
 * 不做任何拦截/重写。无法处理的 scheme 吞掉, 防止 WebView 显示乱码。
 */
private fun handleProtocolUrl(view: WebView, url: String): Boolean {
    val scheme = try { Uri.parse(url).scheme?.lowercase() ?: "" } catch (_: Exception) { "" }
    if (scheme == "http" || scheme == "https") return false

    // intent:// 用 Intent.parseUri 解析后正常启动 (系统路由到对应 App)
    if (url.startsWith("intent://")) {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            view.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: Exception) { }
    }

    // 其他 scheme (uclink:// 等): ACTION_VIEW 正常跳转
    try {
        view.context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) { }
    return true
}

/** 从下载 URL / Content-Disposition / MIME 推断文件名 (DownloadManager 落盘用) */
private fun downloadFileName(url: String, contentDisposition: String?, mimeType: String?): String {
    // Content-Disposition 里的 filename 优先 (支持 RFC 5987 filename*)
    contentDisposition?.let { cd ->
        val m = Regex("filename\\*?=(?:UTF-8''|\")?([^;\"]+)", RegexOption.IGNORE_CASE).find(cd)
        m?.groupValues?.get(1)?.let {
            val decoded = try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
            if (decoded.isNotBlank() && !decoded.contains("/") && !decoded.contains("\\")) {
                return decoded
            }
        }
    }
    // 再从 URL 路径尾段取文件名
    val path = try { Uri.parse(url).lastPathSegment } catch (_: Exception) { null }
    if (!path.isNullOrBlank() && !path.contains("/")) return path
    // 兜底: 按时间戳 + MIME 扩展名
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
    return "download_${System.currentTimeMillis()}.$ext"
}
