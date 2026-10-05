package com.fengling.share.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
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
import com.fengling.share.ui.components.ExternalJumpDialog
import com.fengling.share.ui.components.ExternalJumpTarget
import com.fengling.share.ui.components.resolveExternalJump
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

    // ===== 外部应用跳转确认 (2026-10-02: 用户要求跳转前先问) =====
    // 非 http(s) 链接 (uclink:// weixin:// intent:// mailto: 等) 不再直接 startActivity,
    // 先记下来弹确认框, 用户点「打开」才真正跳转。http(s) 返回 false 放行给 WebView。
    var pendingExternal by remember { mutableStateOf<ExternalJumpTarget?>(null) }
    val interceptExternal: (String) -> Boolean = { u ->
        val target = resolveExternalJump(context, u)
        if (target == null) {
            false
        } else {
            pendingExternal = target
            true
        }
    }
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

    var challengeCount by remember { mutableIntStateOf(0) }

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
        // ===== Cookie / 渲染能力补齐 (2026-10-03, 用户要求频道类页面必须完整显示) =====
        // pd.qq.com 的 TADs 反爬会先下发 PoW 挑战页, 靠 document.cookie 下发 EO-Bot-Js-Token
        // (domain=.qq.com, 跨站) 再重载; 若第三方 cookie 被禁 → 挑战过不了 → 后续 trpc 接口全空,
        // 症状正是「频道外壳(名称/成员数/标签)显示了, 内容列表一片空白」。
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.allowContentAccess = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        Log.i(
            "FLWebView",
            "WebView 内核: " + (
                WebView.getCurrentWebViewPackage()?.let { it.packageName + " " + it.versionName }
                    ?: "unknown"
                )
        )
        // UA 伪装: 去掉 WebView 标识, 伪装成手机 Chrome (UC网盘等站点检测 WebView UA 会拦截)
        settings.userAgentString =
            WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace("Version/4.0", "")
                .replace("Version/4.0 Mobile", "")
                .trim() + " Mobile"

        // ===== 布局高度塌陷修复 (2026-08-10, link3.cc 实测) =====
        // SPA 页面用 html/body height:100% 百分比高度链, WebView 在视口未就绪时
        // 解析该链得到 0 → 内容在 DOM 里但 offsetHeight=0 不可见, 只剩 fixed 元素。
        // vh 单位实时跟随视口, 不依赖百分比链; 多时延重试覆盖 SPA 晚挂载。
        // 仅在检测到塌陷 (body 高度 < 视口一半) 时注入, 正常页面零影响。
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = interceptExternal(request.url.toString())

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                interceptExternal(url)

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // 页面跳转后更新返回栈状态
                canGoBack = view?.canGoBack() ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // 加载完成后更新 (goBack 后 canGoBack 可能变 false)
                canGoBack = view?.canGoBack() ?: false
                // 反爬 (TADs) 挑战页检测: 该页只有一行 PoW 脚本, 会写 cookie 后自行重载;
                // 记录次数便于诊断「内容空白」, 供 logcat -s FLWebView 排查
                if (url?.startsWith("http") == true) {
                    runCatching {
                        view?.evaluateJavascript(
                            "(function(){try{return typeof window.solveChallenge==='function'}catch(e){return false}})()"
                        ) { r ->
                            if (r == "true") {
                                challengeCount++
                                Log.w("FLWebView", "反爬挑战页 第 $challengeCount 次, 等待 PoW 后自动重载: $url")
                                // 腾讯 TADs 挑战页偶发「solveChallenge 尚未加载」而停在壳上 (白屏) →
                                // 3.5s 后若 cookie 里仍没有 EO-Bot-Js-Token 就重载重试, 最多 3 次
                                if (challengeCount <= 3 && view != null) {
                                    view.postDelayed({
                                        runCatching {
                                            view.evaluateJavascript(
                                                "(function(){try{return document.cookie.indexOf('EO-Bot-Js-Token')>=0?'yes':'no'}catch(e){return 'no'}})()"
                                            ) { c ->
                                                if (c != "\"yes\"") {
                                                    Log.w("FLWebView", "反爬 token 未生成, 重载重试 (第 $challengeCount 次)")
                                                    runCatching { view.reload() }
                                                } else {
                                                    Log.i("FLWebView", "反爬 token 已写入 cookie")
                                                }
                                            }
                                        }
                                    }, 3500)
                                }
                            } else if (challengeCount != 0) {
                                Log.i("FLWebView", "已通过反爬挑战 (共 $challengeCount 次)")
                                challengeCount = 0
                            }
                        }
                    }
                }
                // 修复 SPA 页面在 WebView 中「内容空白只剩 fixed 元素」的两大元凶:
                // 1) html/body height:100% 百分比高度链在视口未就绪时解析为 0 → 内容 offsetHeight=0 不可见
                // 2) 首屏 loading 遮罩 (#index_loading) 应用挂载后未移除 → 一直盖在白屏上
                // vh 单位实时跟随视口, 不依赖百分比链; 多时延重试覆盖 SPA 晚挂载。
                if (url?.startsWith("http") == true) {
                    runCatching {
                        view?.evaluateJavascript(
                            """
                            (function(){
                              if (window.__flFixInstalled) return;
                              window.__flFixInstalled = true;
                              var log = function(m){ try { console.log('[FLfix] ' + m); } catch(e){} };
                              var fix = function(tag){
                                try {
                                  var vh = window.innerHeight || document.documentElement.clientHeight;
                                  var de = document.documentElement, b = document.body;
                                  var app = document.getElementById('app');
                                  var bh = b ? b.offsetHeight : -1;
                                  var ah = app ? app.offsetHeight : -1;
                                  log(tag + ' vh=' + vh + ' bodyH=' + bh + ' appH=' + ah + ' appKids=' + (app ? app.children.length : -1));
                                  if (!vh || vh <= 0) return;
                                  if (bh < vh * 0.5 || (app && ah < vh * 0.5)) {
                                    de.style.minHeight = '100vh';
                                    if (b) { b.style.minHeight = '100vh'; b.style.height = 'auto'; }
                                    if (app) { app.style.minHeight = '100vh'; app.style.height = 'auto'; }
                                    log(tag + ' 已修复高度塌陷');
                                  }
                                  var ld = document.getElementById('index_loading');
                                  if (ld && app && app.children.length > 0) {
                                    ld.parentNode.removeChild(ld);
                                    log(tag + ' 已移除首屏 loading 遮罩');
                                  }
                                  window.dispatchEvent(new Event('resize'));
                                  window.dispatchEvent(new Event('scroll'));
                                } catch(e) { log(tag + ' ERR ' + e.message); }
                              };
                              fix('t0');
                              setTimeout(function(){fix('t500')},500);
                              setTimeout(function(){fix('t1500')},1500);
                              setTimeout(function(){fix('t3000')},3000);
                              setTimeout(function(){fix('t6000')},6000);
                            })();
                            """.trimIndent()
                        ) { }
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
                // 进度变化时同步更新 (goBack 前后)
                canGoBack = view.canGoBack()
            }

            // 网页 console 转发到 logcat (诊断内置浏览器空白/报错, tag: FLWebView)
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i("FLWebView", "console: " + message.message() + " @" + message.sourceId() + ":" + message.lineNumber())
                return true
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
                        if (interceptExternal(u)) return true
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

    // v1.1.15: WebView 自己的底色也跟 App 主题背景一致 ——
    // 网页换页 / 重渲染的空档露出来的是 App 底色, 不再是刺眼的白闪。
    // MiuixTheme 只能在组合里读, 所以先把颜色取出来, 再交给非组合的 SideEffect
    val webBackgroundArgb = MiuixTheme.colorScheme.background.toArgb()
    SideEffect { webView.setBackgroundColor(webBackgroundArgb) }

    // 页面离开时: 停加载 + 销毁 WebView 实例 (每次进入都是全新实例, 不残留历史/状态)
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // v1.1.15: 这一页**不能**再注册自己的预测返回 (PredictiveBackHandler)。
    // OnBackPressedDispatcher 是后注册者优先 —— 本页一注册, 就把 navigation-compose 的 NavHost
    // 自己的预测返回回调顶掉了; NavHost 收不到手势进度, 上一级目的地从头到尾没被组合,
    // 于是页面跟手右移让出来的左边那条缝没有任何东西可画, 正是用户看到的「返回过程背景一片空白」。
    //
    // 现在改成:
    //   有网页历史 -> 非预测式 BackHandler 直接回退上一页 (页面不位移, 也就没有缝可露);
    //   没有历史   -> 本页完全不注册, 返回交给 NavHost 框架自己 seek (上一级真实页面跟入)。
    // 代价只是网页内部回退不再有跟手动画; 空白不可接受, 动画可以让。
    BackHandler(enabled = canGoBack) {
        webView.goBack()
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
                .padding(innerPadding)
                // v1.1.15: 内容层铺一层 App 底色 —— 网页还没画出来 / 换页的空档不露窗口底色
                .background(MiuixTheme.colorScheme.background),
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

            // 外部应用跳转确认框 (点击非 http(s) 链接时弹出)
            pendingExternal?.let { target ->
                ExternalJumpDialog(
                    target = target,
                    onConfirm = {
                        val opened = runCatching { context.startActivity(target.intent) }.isSuccess
                        if (!opened) {
                            Toast.makeText(
                                context,
                                "没有找到可以打开该链接的应用",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        pendingExternal = null
                    },
                    onDismiss = { pendingExternal = null },
                )
            }
        }
    }
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
