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
import androidx.compose.material.icons.filled.Download
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
import android.view.ViewGroup
import android.widget.Toast
import com.fengling.share.data.Downloads
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
    /** 下载任务已收进下载中心: 跳过去让用户看到进度 */
    onOpenDownloads: () -> Unit = {},
) {
    val context = LocalContext.current
    var pageTitle by remember { mutableStateOf(title) }
    var progress by remember { mutableIntStateOf(100) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 返回栈状态跟踪 (解决 BackHandler enabled 陈旧问题)
    // 每次进入都是全新 WebView 实例, 初始恒为 false
    var canGoBack by remember { mutableStateOf(false) }
    // 主资源加载失败时的提示文案 (null = 正常)
    var loadError by remember { mutableStateOf<String?>(null) }

    // 每次进入创建全新 WebView 实例, 离开销毁:
    // 复用实例会残留上一链接的内部历史栈 → 返回键 goBack 回到旧链接
    //
    // ⚠️ 必须显式给 layoutParams (这是「DOM 有内容却整页空白」的总根因):
    // Compose 的 AndroidView 只负责测量/放置, 不会替子 View 设 LayoutParams。
    // WebView 内部的 AwLayoutSizer 在拿不到「有尺寸的容器」时, 会给渲染树一个高度为 0 的
    // 初始包含块 —— 于是 CSS 里所有百分比高度链 (html{height:100%} body{height:100%}
    // #app{height:100%}) 全部解析成 0px, position:fixed 的元素因为有视口做参照反而还能画出来。
    // 这正好对上实测现象:
    //   · link3.cc:     整页空白, 只剩底部那个固定定位的紫色按钮
    //   · aceyun.cn:    页面能显示, 但 van-popup / van-overlay 是 fixed + 100% 高 → 弹窗看不见
    //   · pd.qq.com:    频道头/标签正常, 内容列表整片空白
    // 而且 window.innerHeight 一直是正确值(686), 只有 html/body 的
    // getComputedStyle/getBoundingClientRect 高度是 0 —— 很容易误判成「站点自己的 CSS 有毛病」,
    // 之前的 100vh 注入自愈脚本就是被这个假象带偏的 (logcat 里反复生效但页面仍然空白)。
    // 参考: stackoverflow.com/questions/77867390 (同症状, 同一个解法)
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

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
        // via / Chrome 默认是 loadWithOverviewMode=false + useWideViewPort=true。
        // 开着 overview 模式时 WebView 会「按整页宽度」缩放视口, 一旦页面宽度算成 0
        // (SPA 首屏 #app 还没撑开时就是), 整页会被缩成一条线 → 表现为白屏。
        settings.loadWithOverviewMode = false
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
        // ===== 补齐真实浏览器行为 (v1.1.18, 用户对比 via / edge 后反馈) =====
        // 缓存走默认策略: 之前没设, WebView 默认值虽然也是 LOAD_DEFAULT, 但显式写上避免
        // 某些 ROM 定制成 LOAD_NO_CACHE(每次全量回源 → 慢且容易半截渲染)。
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // 定位: onGeolocationPermissionsShowPrompt 虽然放行了, 但不开这个开关不会去问它,
        // 地图类页面会直接拿不到坐标 → 内容区域空着。(setGeolocationEnabled 已废弃但仍是唯一开关;
        // 项目未开 allWarningsAsErrors, 只是编译告警)
        settings.setGeolocationEnabled(true)
        // 让页面能用「保存密码 / 表单自动填充」的语义, 部分登录页靠它判断是否真实浏览器
        settings.saveFormData = true
        Log.i(
            "FLWebView",
            "WebView 内核: " + (
                WebView.getCurrentWebViewPackage()?.let { it.packageName + " " + it.versionName }
                    ?: "unknown"
                )
        )
        // UA 伪装: 去掉 WebView 标识, 伪装成手机 Chrome (UC网盘等站点检测 WebView UA 会拦截)
        // v1.1.18 修正: 原来无条件 `+ " Mobile"`, 而默认 UA 里本来就带 "Mobile Safari/537.36",
        // 拼出来是 "...Safari/537.36 Mobile Mobile" —— 部分站点按 UA 做正则校验时直接判为
        // 非浏览器 → 返回残缺页面/降级 DOM，这也是「有些内容不显示」的一种来源。
        // 现在只做减法(去 wv / 去 Version/4.0), 并保证 Mobile 恰好一次。
        settings.userAgentString = WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")
            .replace(" Version/4.0", "")
            .replace("Version/4.0 ", "")
            .trim()
            .let { if (it.contains("Mobile")) it else "$it Mobile" }

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
                loadError = null
            }

            // v1.1.18: 主资源加载失败时给个明确提示。
            // 之前 onReceivedError 完全没实现 —— 断网 / 域名被墙 / 证书错误时页面就是纯白,
            // 用户无法区分「网站在加载」和「根本打不开」, 观感上正是「打开不全 / 什么都不显示」。
            // 只处理主框架请求(isForMainFrame), 子资源失败不打扰用户。
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                Log.w(
                    "FLWebView",
                    "加载失败 ${if (request?.isForMainFrame == true) "主资源" else "子资源"}: " +
                        "${request?.url} code=${error?.errorCode} desc=${error?.description}",
                )
                if (request?.isForMainFrame == true) {
                    loadError = "打不开这个页面（${error?.errorCode ?: -1}）"
                    progress = 100
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?,
            ) {
                Log.w(
                    "FLWebView",
                    "HTTP ${errorResponse?.statusCode} " +
                        "${if (request?.isForMainFrame == true) "主资源" else "子资源"}: ${request?.url}",
                )
                if (request?.isForMainFrame == true) {
                    loadError = "服务器返回 ${errorResponse?.statusCode ?: 0}"
                    progress = 100
                }
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
                // ===== 只留一条诊断日志，不再注入任何布局修复 =====
                // 废掉了「探测到高度塌陷就改 html/body 为 100vh」那套自愈脚本。
                // 真机上它每次都「生效」了（logcat 里 fixed=yes 一路打出来），页面却依然空白 ——
                // 因为塌陷不是站点 CSS 的问题，是 WebView 自己没拿到有尺寸的容器
                // （见文件上方 layoutParams 的注释）。根因在外层修掉之后，这里再改页面样式
                // 只会把正常站点也弄坏，所以整段删掉，只在 logcat 留一行尺寸，方便下次对照。
                if (url?.startsWith("http") == true) {
                    runCatching {
                        view?.evaluateJavascript(
                            """
                            (function(){
                              if (window.__flProbe) return;
                              window.__flProbe = true;
                              try {
                                var de=document.documentElement, b=document.body;
                                console.log('[FLdom] vh='+window.innerHeight+' vw='+window.innerWidth+
                                  ' deH='+Math.round(de.getBoundingClientRect().height)+
                                  ' bodyH='+Math.round(b.getBoundingClientRect().height)+
                                  ' textLen='+(b.innerText||'').length+
                                  ' dpr='+window.devicePixelRatio);
                              } catch(e) {}
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

        // 下载处理: 收进 App 自己的下载中心 (Downloads), 不再甩给系统 DownloadManager。
        // 为什么换: 系统那个把任务丢给别的进程 —— App 拿不到实时进度、没法在列表里暂停/续传,
        // 而且带不上 Referer, 蓝奏云这类网盘会直接 403。自己下就能带全请求头 + 断点续传。
        webView.setDownloadListener(
            DownloadListener { url, _userAgent, contentDisposition, mimetype, _contentLength ->
                val name = downloadFileName(url, contentDisposition, mimetype)
                Downloads.enqueue(
                    url = url,
                    fileName = name,
                    mimeType = mimetype ?: "application/octet-stream",
                    pageTitle = pageTitle,
                    // 网盘防盗链要 Referer, 用当前页面地址
                    referer = webView.url ?: url,
                )
                Toast.makeText(context, "已加入下载管理", Toast.LENGTH_SHORT).show()
                onOpenDownloads()
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
                            DropdownMenuItem(
                                text = { Text("下载管理") },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenDownloads()
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
            // v1.1.18: 之前这里没有任何「网页自己滚不动」的兜底。
            // 页面内容比视口长、但页面自身没开滚动(或滚动容器被注入脚本改塌)时,
            // 用户看到的就是「下面一段内容不显示」。所以外层再包一层可滚动的 Column?
            // —— 不行: 包滚动容器会让页面内部的 position:fixed / 惯性滚动全失效。
            // 正确做法是保持 WebView 全尺寸、由页面自己滚; 这里只保证不额外裁剪。
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

            // 加载失败提示 (v1.1.18): 白屏时给出可操作信息, 而不是让用户盯着一片空白
            loadError?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    TopLevelErrorRetry(
                        message = msg,
                        onRetry = {
                            loadError = null
                            progress = 0
                            webView.reload()
                        },
                    )
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

/** 从下载 URL / Content-Disposition / MIME 推断文件名 (下载中心落盘用) */
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

/** 加载失败时的居中提示 + 重试按钮（内置浏览器白屏兜底，v1.1.18） */
@Composable
private fun TopLevelErrorRetry(message: String, onRetry: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = message,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Box(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.primary)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(text = "重试", fontSize = 15.sp, color = MiuixTheme.colorScheme.onPrimary)
        }
    }
}
