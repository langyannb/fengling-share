package com.fengling.share.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 风铃分享库 API 客户端
 * 后端: PHP + Nginx (地址已加密, 见 ServerConfig)
 * 响应格式: {"code":0,"msg":"ok","data":...}
 */
object ApiClient {

    private val BASE_URL = ServerConfig.BASE_URL
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 通用请求: 校验 code, 返回整个响应 JSONObject (含 data)
     * @param token 非空时附加 `Authorization: Bearer <token>` (现有调用不传, 行为不变)
     */
    private fun request(
        action: String,
        params: Map<String, Any?> = emptyMap(),
        token: String? = null,
    ): JSONObject {
        val url = StringBuilder(BASE_URL).append("?action=").append(action)
        val body: okhttp3.RequestBody?

        if (params.isEmpty()) {
            body = null
        } else {
            val json = JSONObject()
            params.forEach { (k, v) ->
                when (v) {
                    null -> json.put(k, JSONObject.NULL)
                    is Int -> json.put(k, v)
                    is Long -> json.put(k, v)
                    is Double -> json.put(k, v)
                    is Boolean -> json.put(k, v)
                    // 数组参数 (如 social_send 的 at 用户 id 列表) 必须原样提交成 JSON 数组,
                    // 走 else 分支会变成字符串 "[6, 7]" 导致后端解析不到
                    is JSONArray -> json.put(k, v)
                    else -> json.put(k, v.toString())
                }
            }
            body = json.toString().toRequestBody(JSON)
        }

        val builder = Request.Builder().url(url.toString())
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        if (body != null) {
            builder.post(body)
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string() ?: "{}"
            val obj = JSONObject(text)
            if (obj.optInt("code", -1) != 0) {
                throw ApiException(obj.optString("msg", "请求失败"))
            }
            return obj
        }
    }

    // ===================== SSE 实时消息流 (契约 A 章节) =====================

    /**
     * 专用流式 client: readTimeout(0) = 永不读超时 (SSE 长连接靠服务端 25 秒收尾 + 10 秒心跳)。
     * ⚠️ 用 newBuilder() 派生, 绝不动上面那个 15 秒读超时的 client。
     */
    private val streamClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * SSE 实时消息流 (契约 A1/A2/A3): 阻塞读到服务端 `event: bye` 收尾或连接断开为止。
     *
     * - URL 里 action 必须放查询串 (api.php 用 $_GET['action'] 路由), token 也走查询串
     *   (current_user() 支持 param('token'); 不用 Authorization 头, 免得 nginx 丢头)。
     * - `pmId` / `groupId` 是「已收到的最大消息 id」游标: `0` = 服务端从「现在」开始 (不重放历史),
     *   `> 0` = 从该 id **之后**开始推, 可以把断线期间的消息补齐 (v1.1.2 起由
     *   [com.fengling.share.data.StreamCursor] 持久化, 见契约 A1)。
     * - 逐行解析 `event:` / `data:`; `:` 开头是心跳注释行, 忽略; `event: bye` 视为服务端
     *   正常收尾 -> 返回 Result.success, 让外层 (MessageStream) 立刻重连。
     * - onEvent 在 IO 线程回调, UI 侧自己 withContext。
     * - `onActivity` 每读到**任何一行**(含 10 秒一次的 `: hb` 心跳注释行) 都回调一次,
     *   给 [com.fengling.share.service.MessageService] 判断「连接是不是还活着」(v1.1.2)。
     *
     * @return 成功 = 服务端正常收尾 / 连接自然结束; 失败 = 建连失败 (含 401)
     */
    suspend fun streamMessages(
        pmId: Int = 0,
        groupId: Int = 0,
        onActivity: (() -> Unit)? = null,
        onEvent: (StreamEvent) -> Unit,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = StringBuilder(BASE_URL)
                    .append("?action=stream&token=")
                    .append(URLEncoder.encode(UserStore.token, "UTF-8"))
                    // 游标 (v1.1.2): 0 = 从现在开始 (首次安装 / 刚退出登录, 不重放历史);
                    // > 0 = 从该 id 之后开始推 —— 断线期间服务端攒的积压会补推过来, 不再丢那 2 秒窗口
                    .append("&pm_id=").append(pmId)
                    .append("&group_id=").append(groupId)
                    .toString()
                val call = streamClient.newCall(Request.Builder().url(url).get().build())
                // 协程被取消 (App 退后台 -> MessageStream.stop()) 时 cancel 掉 Call,
                // 否则 readUtf8Line 会一直阻塞到服务端 25 秒收尾才放手, 留下悬挂连接
                val completion = currentCoroutineContext()[Job]?.invokeOnCompletion {
                    runCatching { call.cancel() }
                }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw ApiException("实时消息连接失败 (HTTP ${response.code})")
                        }
                        val body = response.body
                            ?: throw ApiException("实时消息连接没有返回内容")
                        // readTimeout(0) 下 readUtf8Line 会阻塞到有数据为止, 不需要任何额外超时逻辑
                        val source = body.source()
                        Log.i("FLS_SSE", "SSE 已连接: HTTP " + response.code)
                        var eventName = ""
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            // 任何一行 (心跳/事件/空行) 都证明这条连接是活的
                            onActivity?.invoke()
                            when {
                                // 心跳行 `: hb` (纯注释) 直接忽略
                                line.startsWith(":") -> Unit
                                // 空行 = 一条 SSE 事件结束, 清掉事件名
                                line.isEmpty() -> eventName = ""
                                // "event:" 是 6 个字符, 必须按前缀剥离 (substring(5) 会留下 ": pm")
                                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    val payload = line.removePrefix("data:").trim()
                                    when (eventName) {
                                        "pm" -> {
                                            val ev = parsePmStreamEvent(payload)
                                            if (ev == null) {
                                                Log.w("FLS_SSE", "SSE 私聊事件解析失败 (已丢弃)")
                                            } else {
                                                onEvent(ev)
                                            }
                                        }
                                        "group" -> {
                                            val ev = parseGroupStreamEvent(payload)
                                            if (ev == null) {
                                                Log.w("FLS_SSE", "SSE 群事件解析失败 (已丢弃)")
                                            } else {
                                                onEvent(ev)
                                            }
                                        }
                                        // 服务端正常收尾 (25 秒到点) -> 成功返回让外层重连
                                        "bye" -> {
                                            return@use
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    completion?.dispose()
                }
                Unit
            }
        }

    /** 解析 `event: pm` 的 data 行 (契约 A3) */
    private fun parsePmStreamEvent(payload: String): StreamEvent.Pm? = runCatching {
        val j = JSONObject(payload)
        StreamEvent.Pm(
            msgId = j.optInt("id", 0),
            convId = j.optInt("conv_id", 0),
            fromUser = j.optInt("from_user", 0),
            nickname = j.optString("nickname", ""),
            content = j.optString("content", ""),
            image = j.optString("image", ""),
            createdAt = j.optString("created_at", ""),
        )
    }.getOrNull()

    /** 解析 `event: group` 的 data 行 (契约 A3) */
    private fun parseGroupStreamEvent(payload: String): StreamEvent.Group? = runCatching {
        val j = JSONObject(payload)
        StreamEvent.Group(
            msgId = j.optInt("id", 0),
            groupId = j.optInt("group_id", 0),
            groupName = j.optString("group_name", ""),
            userId = j.optInt("user_id", 0),
            nickname = j.optString("nickname", ""),
            content = j.optString("content", ""),
            image = j.optString("image", ""),
            atMe = j.optInt("at_me", 0),
            atAll = j.optInt("at_all", 0),
            // 服务端在 group 事件里带上「我是否对该群开了免打扰」(契约 A6), 老服务端没有这个键 -> false
            muted = j.optInt("muted", 0) == 1,
            createdAt = j.optString("created_at", ""),
        )
    }.getOrNull()

    /** 轮播图 */
    suspend fun getBanners(): List<Banner> = withContext(Dispatchers.IO) {
        val obj = request("banners")
        val arr = obj.optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { Banner.fromJson(arr.getJSONObject(it)) }
    }

    /** 分类列表 */
    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        val obj = request("categories")
        val arr = obj.optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { Category.fromJson(arr.getJSONObject(it)) }
    }

    /** 软件列表 */
    suspend fun getApps(categoryId: Int = 0, keyword: String = ""): List<AppItem> =
        withContext(Dispatchers.IO) {
            val params = mutableMapOf<String, Any?>()
            if (categoryId > 0) params["category_id"] = categoryId
            if (keyword.isNotBlank()) params["keyword"] = keyword
            val obj = request("apps", params)
            val arr = obj.optJSONArray("data") ?: return@withContext emptyList()
            (0 until arr.length()).map { AppItem.fromJson(arr.getJSONObject(it)) }
        }

    /** 软件详情 */
    suspend fun getAppDetail(id: Int): AppItem = withContext(Dispatchers.IO) {
        val obj = request("app_detail", mapOf("id" to id))
        val data = obj.optJSONObject("data") ?: JSONObject()
        AppItem.fromJson(data)
    }

    /** 点击下载: 返回网盘 URL + 提取码 */
    suspend fun clickLink(linkId: Int): Pair<String, String> = withContext(Dispatchers.IO) {
        val obj = request("link_click", mapOf("id" to linkId))
        val data = obj.optJSONObject("data") ?: JSONObject()
        data.optString("url") to data.optString("password")
    }

    /** 版本检测: 返回最新版本信息 */
    suspend fun checkVersion(): VersionInfo = withContext(Dispatchers.IO) {
        val obj = request("version")
        val data = obj.optJSONObject("data") ?: JSONObject()
        VersionInfo(
            version = data.optString("version", ""),
            url = data.optString("url", ""),
            updateLog = data.optString("update_log", ""),
            updateMode = data.optString("update_mode", "internal"),
            forceUpdate = data.optInt("force_update", 0) == 1,
            sizeMb = data.optDouble("size_mb", 0.0).toFloat(),
            releaseDate = data.optString("release_date", ""),
        )
    }

    /** 关于页配置 (官方频道/链接) */
    suspend fun getAboutConfig(): AboutConfig = withContext(Dispatchers.IO) {
        val obj = request("about_config_get")
        val d = obj.optJSONObject("data") ?: JSONObject()
        AboutConfig(
            qqGroup = d.optString("qq_group", ""),
            qqKey = d.optString("qq_key", ""),
            qqUrl = d.optString("qq_url", ""),
            qqChannel = d.optString("qq_channel", ""),
            website = d.optString("website", ""),
            github = d.optString("github", ""),
            feedback = d.optString("feedback", ""),
            donate = d.optString("donate", ""),
            bannerText = d.optString("banner_text", "风铃分享库 · 官方频道"),
            bannerSub = d.optString("banner_sub", "最新软件 · 更新通知 · 交流反馈"),
        )
    }

    /** 公告: 返回内容 + 显示模式 (daily=每日一次, every=每次打开) + 是否启用 */
    suspend fun getNotice(): NoticeInfo = withContext(Dispatchers.IO) {
        val obj = request("notice_get")
        val d = obj.optJSONObject("data") ?: JSONObject()
        NoticeInfo(
            content = d.optString("content", ""),
            mode = d.optString("mode", "daily"),
            enabled = d.optInt("enabled", 0) == 1,
        )
    }

    /**
     * 反馈和谐: 用户报告软件哪里被和谐了 (App 端匿名提交, 管理员在管理端「设置→和谐」查看)
     * @return null = 提交成功; 非 null = 错误消息 (后端业务拒绝如频率限制 / 网络失败, 可直接 Toast)
     */
    suspend fun submitHarmReport(appId: Int, appName: String, content: String, contact: String = ""): String? =
        withContext(Dispatchers.IO) {
            try {
                request("harm_report", mapOf(
                    "app_id" to appId,
                    "app_name" to appName,
                    "content" to content,
                    "contact" to contact,
                ))
                null
            } catch (e: ApiException) {
                e.message ?: "提交失败，请稍后重试"
            } catch (_: Exception) {
                "网络连接失败，请检查网络后重试"
            }
        }

    /** 投稿名单: 只返回头像/昵称/说明 + 投稿应用名 (后端不暴露 QQ 号) */
    suspend fun getContributors(): List<Contributor> = withContext(Dispatchers.IO) {
        val obj = request("contributors")
        val arr = obj.optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            val names = j.optJSONArray("app_names")
            Contributor(
                avatar = j.optString("avatar", ""),
                name = j.optString("name", ""),
                bio = j.optString("bio", ""),
                appNames = names?.let { n -> (0 until n.length()).map { n.optString(it) } } ?: emptyList(),
            )
        }
    }
    // ===================== 账号系统 (接口契约 v1.0.10) =====================

    /**
     * Result 包装: 业务失败用服务端 msg, 网络失败用统一文案 (绝不泄露服务器地址)
     * token 失效 (code=401 / msg=登录已失效) 时顺手清掉本地登录态
     */
    private inline fun <T> apiCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: ApiException) {
        if (e.message?.contains("登录已失效") == true) UserStore.clear()
        Result.failure(Exception(e.message?.takeIf { it.isNotBlank() } ?: "请求失败"))
    } catch (e: Exception) {
        Result.failure(Exception(e.userFriendlyMessage()))
    }

    /** 解析 {"token":"...","user":{...}} */
    private fun parseTokenUser(obj: JSONObject): Pair<String, User> {
        val d = obj.optJSONObject("data") ?: JSONObject()
        val token = d.optString("token", "")
        val user = d.optJSONObject("user")?.let { User.fromJson(it) } ?: User()
        return token to user
    }

    /** 解析 data 里的单个 user 对象 */
    private fun parseUserData(obj: JSONObject): User =
        obj.optJSONObject("data")?.let { User.fromJson(it) } ?: User()

    /**
     * 发送邮箱验证码 (purpose: register=注册 / reset=找回密码)
     * 契约 v1 起必须带图形验证码: captchaToken / captchaCode 由 getCaptcha() 取得
     */
    suspend fun sendCode(
        email: String,
        purpose: String = "register",
        captchaToken: String = "",
        captchaCode: String = "",
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            apiCall {
                request(
                    "send_code",
                    mapOf(
                        "email" to email,
                        "purpose" to purpose,
                        "captcha_token" to captchaToken,
                        "captcha_code" to captchaCode,
                    ),
                )
                Unit
            }
        }

    /** 注册: 成功返回 (token, user) */
    suspend fun register(
        username: String,
        password: String,
        nickname: String,
        email: String,
        code: String,
    ): Result<Pair<String, User>> = withContext(Dispatchers.IO) {
        apiCall {
            parseTokenUser(
                request(
                    "register",
                    mapOf(
                        "username" to username,
                        "password" to password,
                        "nickname" to nickname,
                        "email" to email,
                        "code" to code,
                    ),
                ),
            )
        }
    }

    /** 登录 (account 可填用户名或邮箱) */
    suspend fun loginAccount(account: String, password: String): Result<Pair<String, User>> =
        withContext(Dispatchers.IO) {
            apiCall {
                parseTokenUser(request("login", mapOf("username" to account, "password" to password)))
            }
        }

    /** 当前登录用户资料 */
    suspend fun getMe(): Result<User> = withContext(Dispatchers.IO) {
        apiCall { parseUserData(request("user_me", token = UserStore.token)) }
    }

    /** 更新资料 (只提交非 null 字段: 昵称≤20 / 简介≤100 / 头像 URL) */
    suspend fun updateProfile(
        nickname: String? = null,
        bio: String? = null,
        avatar: String? = null,
    ): Result<User> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>()
            if (nickname != null) params["nickname"] = nickname
            if (bio != null) params["bio"] = bio
            if (avatar != null) params["avatar"] = avatar
            parseUserData(request("user_update", params, UserStore.token))
        }
    }

    /** 修改密码 (成功后后端不返回数据, 需重新登录) */
    suspend fun changePassword(old: String, new: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            apiCall {
                request(
                    "user_password",
                    mapOf("old_password" to old, "new_password" to new),
                    UserStore.token,
                )
                Unit
            }
        }

    /**
     * 上传头像 (multipart, 字段名固定 file, action=user_avatar)
     * 后端会同时把 users.avatar 写成新 URL, 成功返回该 URL
     */
    suspend fun uploadAvatar(bytes: ByteArray, filename: String, mime: String): Result<String> =
        withContext(Dispatchers.IO) {
            apiCall {
                val safeMime = if (mime.contains("/")) mime else "image/*"
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, bytes.toRequestBody(safeMime.toMediaType()))
                    .build()
                // 注意: 局部变量不能叫 request, 会遮蔽上面的 private fun request
                val req = Request.Builder()
                    .url(StringBuilder(BASE_URL).append("?action=user_avatar").toString())
                    .header("Authorization", "Bearer ${UserStore.token}")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { response ->
                    val text = response.body?.string() ?: "{}"
                    val obj = JSONObject(text)
                    if (obj.optInt("code", -1) != 0) {
                        throw ApiException(obj.optString("msg", "请求失败"))
                    }
                    obj.optJSONObject("data")?.optString("url", "") ?: ""
                }
            }
        }

    /**
     * 上传群聊图片 (multipart, 字段名固定 file, action=social_image_upload)
     *
     * 服务端已压缩到最长边 1600 / 质量 82; 返回的 width/height 是**原图**尺寸,
     * 客户端按它算气泡里的显示比例。失败时抛 ApiException(服务端 msg)。
     */
    suspend fun uploadChatImage(bytes: ByteArray, filename: String, mime: String): Result<ChatImage> =
        withContext(Dispatchers.IO) {
            apiCall {
                val safeMime = if (mime.contains("/")) mime else "image/*"
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, bytes.toRequestBody(safeMime.toMediaType()))
                    .build()
                val req = Request.Builder()
                    .url(StringBuilder(BASE_URL).append("?action=social_image_upload").toString())
                    .header("Authorization", "Bearer ${UserStore.token}")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { response ->
                    val text = response.body?.string() ?: "{}"
                    val obj = JSONObject(text)
                    if (obj.optInt("code", -1) != 0) {
                        throw ApiException(obj.optString("msg", "请求失败"))
                    }
                    val d = obj.optJSONObject("data") ?: JSONObject()
                    ChatImage(
                        url = jsonStr(d, "url"),
                        width = jsonInt(d, "width"),
                        height = jsonInt(d, "height"),
                    )
                }
            }
        }

    /**
     * 下载聊天图片的原图字节 — 「保存到相册」用。
     * 复用同一个 OkHttpClient (带 10s/15s 超时), 不额外引依赖。
     */
    suspend fun downloadChatImage(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        apiCall {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException("图片下载失败 (HTTP " + response.code + ")")
                }
                response.body?.bytes()?.takeIf { it.isNotEmpty() } ?: throw ApiException("图片内容为空")
            }
        }
    }

    /** 退出登录 (通知后端作废 token, 本地登录态由调用方 UserStore.clear()) */
    suspend fun logoutAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            request("logout", mapOf("token" to UserStore.token), UserStore.token)
            Unit
        }
    }

    // ===================== 社交 / 消息中心 / 邮箱验证 (接口契约 v1 第一~四节) =====================

    /** 图形验证码 (公开): 返回 token + base64 data URI 图片, 可直接交给 Coil 渲染 */
    suspend fun getCaptcha(): Result<Captcha> = withContext(Dispatchers.IO) {
        apiCall {
            val d = request("captcha").optJSONObject("data") ?: JSONObject()
            Captcha(token = d.optString("token", ""), image = d.optString("image", ""))
        }
    }

    /** 社交群组列表 (公开, 只含 is_active=1 的群) */
    suspend fun socialGroups(): Result<List<SocialGroup>> = withContext(Dispatchers.IO) {
        apiCall {
            // 必须带 token: social_groups 只有登录后才返回 unread / first_unread_id,
            // 之前漏传 token, 服务端当访客处理 → 未读数恒为 0, 群列表没有红标
            val arr = request("social_groups", emptyMap<String, Any?>(), UserStore.token)
                .optJSONObject("data")?.optJSONArray("list")
            if (arr == null) {
                emptyList()
            } else {
                (0 until arr.length()).map { SocialGroup.fromJson(arr.getJSONObject(it)) }
            }
        }
    }

    /** 消息中心一页数据: 列表 + 总数 + 未读 + 分类未读数 (type -> count) */
    data class NotifyPage(
        val list: List<NotifyItem>,
        val total: Int = 0,
        val unread: Int = 0,
        val unreadByType: Map<String, Int> = emptyMap(),
    )

    /** 开启/关闭某个群的消息免打扰 (只对自己生效, @我 与群公告仍然提醒) */
    suspend fun socialMuteSet(groupId: Int, muted: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        apiCall {
            val body = request(
                "social_mute_set",
                mapOf<String, Any?>("group_id" to groupId, "muted" to if (muted) 1 else 0),
                UserStore.token,
            )
            body.optJSONObject("data")?.optInt("muted", 0)?.let { it == 1 } ?: muted
        }
    }

    /**
     * 管理员: 开启 / 关闭某个群的「全体禁言」(action=social_group_allmute_set, 契约 A3)。
     *
     * 开启后除管理员外任何人都不能发言, 服务端会返回 403 + 中文文案
     * (客户端在群聊页另有一层「输入框禁用 + 横幅」的前置拦截)。
     */
    suspend fun socialGroupAllMuteSet(groupId: Int, muted: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            apiCall {
                val d = request(
                    "social_group_allmute_set",
                    mapOf<String, Any?>("group_id" to groupId, "muted" to if (muted) 1 else 0),
                    UserStore.token,
                ).optJSONObject("data")
                // 服务端返回 data.all_muted (0/1); 拿不到就按本地意图返回
                d?.optInt("all_muted", if (muted) 1 else 0)?.let { it == 1 } ?: muted
            }
        }

    /** 群成员候选 (可 @ 的人): 该群发过言的活跃用户 + 管理员, 已排除自己 */
    suspend fun socialGroupMembers(groupId: Int): Result<List<SocialGroupMember>> = withContext(Dispatchers.IO) {
        apiCall {
            val arr = request("social_group_members", mapOf<String, Any?>("group_id" to groupId), UserStore.token)
                .optJSONObject("data")?.optJSONArray("list")
            if (arr == null) {
                emptyList()
            } else {
                (0 until arr.length()).map { SocialGroupMember.fromJson(arr.getJSONObject(it)) }
            }
        }
    }

    /**
     * 管理员: 禁言某个用户 (group_id = 0 表示全站禁言)
     * @param minutes 禁言分钟数, 0 表示永久 (服务端上限 300 天)
     * @return 禁言剩余时长文案, 例如「剩余 1 小时」/「永久」
     */
    suspend fun adminUserMute(userId: Int, minutes: Int, groupId: Int = 0, reason: String = ""): Result<String> =
        withContext(Dispatchers.IO) {
            apiCall {
                val d = request(
                    "admin_user_mute",
                    mapOf<String, Any?>(
                        "user_id" to userId,
                        "group_id" to groupId,
                        "minutes" to minutes,
                        "reason" to reason,
                    ),
                    UserStore.token,
                ).optJSONObject("data")
                jsonStr(d ?: org.json.JSONObject(), "left_text")
            }
        }

    /** 管理员: 解除禁言, 返回实际解除的记录数 (0 = 该用户本来就没被禁言) */
    suspend fun adminUserUnmute(userId: Int, groupId: Int = 0): Result<Int> =
        withContext(Dispatchers.IO) {
            apiCall {
                request(
                    "admin_user_unmute",
                    mapOf<String, Any?>("user_id" to userId, "group_id" to groupId),
                    UserStore.token,
                ).optJSONObject("data")?.optInt("removed", 0) ?: 0
            }
        }

    /** 群消息一页数据: 消息列表 + 该群未读条数 + 第一条未读消息 id */
    data class SocialMessagesPage(
        val list: List<SocialMessage> = emptyList(),
        val unread: Int = 0,
        val firstUnreadId: Int = 0,
        val myId: Int = 0,
        /** 是否还存在更早的消息 (false = 已经翻到群聊最开始, 上滑不用再拉) */
        val hasMoreBefore: Boolean = false,
    )

    /**
     * 群消息列表 + 未读信息 (需登录)
     * @param afterId >0 时只取比它更新的消息 (3 秒轮询用); 返回已按时间正序
     * @param aroundId >0 时以该消息为中心取一屏 (定位用)
     * @param beforeId >0 时只取比它更早的一页 (往上翻历史消息用, 返回仍是时间正序);
     *   服务端优先级: aroundId > beforeId > afterId > 默认(最新)
     */
    suspend fun socialMessagesPage(
        groupId: Int,
        afterId: Int = 0,
        limit: Int = 30,
        aroundId: Int = 0,
        beforeId: Int = 0,
    ): Result<SocialMessagesPage> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("group_id" to groupId, "limit" to limit)
            if (aroundId > 0) params["around_id"] = aroundId
            if (beforeId > 0) params["before_id"] = beforeId
            if (afterId > 0) params["after_id"] = afterId
            val d = request("social_messages", params, UserStore.token).optJSONObject("data")
            val arr = d?.optJSONArray("list")
            SocialMessagesPage(
                list = if (arr == null) emptyList() else
                    (0 until arr.length()).map { SocialMessage.fromJson(arr.getJSONObject(it)) },
                unread = d?.optInt("unread", 0) ?: 0,
                firstUnreadId = d?.optInt("first_unread_id", 0) ?: 0,
                myId = d?.optInt("my_id", 0) ?: 0,
                hasMoreBefore = jsonBool(d ?: JSONObject(), "has_more_before"),
            )
        }
    }

    /**
     * 群消息列表 (需登录)
     * @param afterId >0 时只取比它更新的消息 (3 秒轮询用); 返回已按时间正序
     * @param beforeId >0 时只取比它更早的消息 (上滑加载更早的消息用)
     */
    suspend fun socialMessages(
        groupId: Int,
        afterId: Int = 0,
        limit: Int = 30,
        aroundId: Int = 0,
        beforeId: Int = 0,
    ): Result<List<SocialMessage>> =
        socialMessagesPage(groupId, afterId, limit, aroundId, beforeId).map { it.list }

    /** 发送群消息, 成功返回新消息 id (同一用户同一群 2 秒 1 条) */
    suspend fun socialSend(
        groupId: Int,
        content: String,
        at: List<Int> = emptyList(),
        /** 管理员专用: @所有人 (全体提醒) */
        atAll: Boolean = false,
        /** 引用回复: 被引用的消息 id (0 = 不引用) */
        quoteId: Int = 0,
        /** 图片消息: 上传接口拿到的直链 (必须以 https://fenglin.cn-nb1.rains3.com/chat/ 开头) */
        image: String = "",
        /** 原图宽 (用于客户端排版, 0 = 未知) */
        imageW: Int = 0,
        /** 原图高 (0 = 未知) */
        imageH: Int = 0,
    ): Result<Int> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("group_id" to groupId, "content" to content)
            if (at.isNotEmpty()) {
                params["at"] = JSONArray().apply { at.forEach { put(it) } }
            }
            if (atAll) params["at_all"] = 1
            if (quoteId > 0) params["quote_id"] = quoteId
            // 纯图片消息: content 传空串 + 带上 image 三件套; 纯文字时不带这三个键(保持老行为)
            if (image.isNotBlank()) {
                params["image"] = image
                params["image_w"] = imageW
                params["image_h"] = imageH
            }
            request("social_send", params, UserStore.token)
                .optJSONObject("data")?.optInt("id", 0) ?: 0
        }
    }

    /** 撤回消息 (管理员可撤任何人, 普通用户只能撤自己 5 分钟内的) */
    suspend fun socialRecall(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            request("social_recall", mapOf("id" to id), UserStore.token)
            Unit
        }
    }

    /**
     * 把某个群标记为已读 (进群定位到第一条未读之后调一次)
     * @param lastId 不传 (0) = 标记到最新; 传更小的 last_id 服务端也不会让已读位置回退
     */
    suspend fun socialRead(groupId: Int, lastId: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("group_id" to groupId)
            // last_id 是可选的: 不传才表示「标记到最新」, 所以 0 时不带这个键
            if (lastId > 0) params["last_id"] = lastId
            request("social_read", params, UserStore.token)
            Unit
        }
    }

    /**
     * 设置群公告 (仅管理员)。最长 500 字, 传空串即清空公告。
     */
    suspend fun socialSetNotice(groupId: Int, notice: String): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            request(
                "social_group_notice_set",
                mapOf("group_id" to groupId, "notice" to notice),
                UserStore.token,
            )
            Unit
        }
    }

    // ===================== 私聊 / 用户主页 (接口契约 2026-10-04 A 节) =====================

    /**
     * 用户主页 (可匿名, 带 token 时才能拿到准确的 is_me / can_chat / conv_id)
     * @param userId 目标用户 id
     */
    suspend fun userProfile(userId: Int, groupId: Int = 0): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            apiCall {
                // group_id: 传群 id = 看这个用户「在这个群里」的禁言状态; 不传/0 = 全站 (契约 F2)
                val params = mutableMapOf<String, Any?>("user_id" to userId)
                if (groupId > 0) params["group_id"] = groupId
                val d = request("user_profile", params, UserStore.token)
                    .optJSONObject("data") ?: JSONObject()
                UserProfile.fromJson(d)
            }
        }

    /** 私聊会话列表 (需登录): 每条含对方信息 + 最新一条消息 + 未读数, 以及全部会话未读总数 */
    suspend fun pmConversations(): Result<PmConversationPage> = withContext(Dispatchers.IO) {
        apiCall {
            val d = request("pm_conversations", emptyMap<String, Any?>(), UserStore.token)
                .optJSONObject("data")
            val arr = d?.optJSONArray("list")
            PmConversationPage(
                list = if (arr == null) emptyList() else
                    (0 until arr.length()).map { PmConversation.fromJson(arr.getJSONObject(it)) },
                totalUnread = d?.optInt("total_unread", 0) ?: 0,
            )
        }
    }

    /**
     * 私聊消息列表 (需登录), 返回已按时间正序
     * @param convId / userId 二选一: 已有会话传 convId; 首次私聊 (还没会话) 传 userId
     * @param beforeId >0 只取比它更早的一页 (上翻历史)
     * @param afterId  >0 只取比它更新的消息 (轮询增量)
     * @param aroundId >0 以该消息为中心取一屏 (定位)
     * 服务端优先级: aroundId > beforeId > afterId > 默认(最新)
     */
    suspend fun pmMessages(
        convId: Int = 0,
        userId: Int = 0,
        beforeId: Int = 0,
        afterId: Int = 0,
        aroundId: Int = 0,
        limit: Int = 30,
    ): Result<PmMessagesPage> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("limit" to limit)
            if (convId > 0) params["conv_id"] = convId
            if (userId > 0) params["user_id"] = userId
            if (aroundId > 0) params["around_id"] = aroundId
            if (beforeId > 0) params["before_id"] = beforeId
            if (afterId > 0) params["after_id"] = afterId
            val d = request("pm_messages", params, UserStore.token).optJSONObject("data")
            val arr = d?.optJSONArray("list")
            PmMessagesPage(
                convId = d?.optInt("conv_id", 0) ?: 0,
                other = d?.optJSONObject("other")?.let { PmPeer.fromJson(it) },
                list = if (arr == null) emptyList() else
                    (0 until arr.length()).map { PmMessage.fromJson(arr.getJSONObject(it)) },
                hasMoreBefore = jsonBool(d ?: JSONObject(), "has_more_before"),
                unread = d?.optInt("unread", 0) ?: 0,
                firstUnreadId = d?.optInt("first_unread_id", 0) ?: 0,
                myId = d?.optInt("my_id", 0) ?: 0,
            )
        }
    }

    /**
     * 发私聊消息 (需登录)
     * @param toUser / convId 二选一 (toUser 用于还没会话时)
     * 失败时 ApiException.message 就是服务端中文提示, 可直接 Toast:
     * 「不能给自己发私聊」/「你已被禁言 (…), 原因: …」/「消息不能超过 500 个字」/「发送太快了, 请稍后再试」
     */
    suspend fun pmSend(
        toUser: Int = 0,
        convId: Int = 0,
        content: String = "",
        /** 图片直链: 必须先走 social_image_upload (uploadChatImage), 前缀 https://fenglin.cn-nb1.rains3.com/chat/ */
        image: String = "",
        imageW: Int = 0,
        imageH: Int = 0,
    ): Result<PmSendResult> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("content" to content)
            if (toUser > 0) params["to_user"] = toUser
            if (convId > 0) params["conv_id"] = convId
            // 纯图片消息: content 传空串 + 带 image 三件套; 纯文字时不带这三个键
            if (image.isNotBlank()) {
                params["image"] = image
                params["image_w"] = imageW
                params["image_h"] = imageH
            }
            val d = request("pm_send", params, UserStore.token).optJSONObject("data") ?: JSONObject()
            PmSendResult.fromJson(d)
        }
    }

    /**
     * 把某个私聊会话标记为已读 (进会话时调一次)
     * @param lastId 不传 (0) = 标记到最新
     */
    suspend fun pmRead(convId: Int, lastId: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("conv_id" to convId)
            if (lastId > 0) params["last_id"] = lastId
            request("pm_read", params, UserStore.token)
            Unit
        }
    }

    /** 撤回私聊消息 (普通用户只能撤自己 5 分钟内的, 管理员不受限) */
    suspend fun pmRecall(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            request("pm_recall", mapOf("id" to id), UserStore.token)
            Unit
        }
    }

    // ===================== 抽奖 (需登录) =====================

    /**
     * 抽奖活动信息: 开关 / 标题 / 说明 / 我的剩余次数 / 奖品 / 我的中奖记录
     *
     * 必须带 UserStore.token: 服务端要靠它算 my_quota / my_drawn / records,
     * 漏 token 会被当访客处理 (和之前 socialGroups 漏 token 是同一类 bug)
     */
    suspend fun lotteryInfo(): Result<LotteryInfo> = withContext(Dispatchers.IO) {
        apiCall {
            val d = request("lottery_info", emptyMap<String, Any?>(), UserStore.token)
                .optJSONObject("data") ?: JSONObject()
            LotteryInfo.fromJson(d)
        }
    }

    /**
     * 抽一次奖 (无参数)
     * 失败时 ApiException.message 就是服务端中文提示, 可直接 Toast:
     * 「抽奖活动已关闭」/「你的抽奖次数已用完」/「奖品已抽完, 请稍后再来」
     */
    suspend fun lotteryDraw(): Result<LotteryResult> = withContext(Dispatchers.IO) {
        apiCall {
            val d = request("lottery_draw", emptyMap<String, Any?>(), UserStore.token)
                .optJSONObject("data") ?: JSONObject()
            LotteryResult.fromJson(d)
        }
    }

    /** 我的中奖记录 (分页, 每页最多 50 条) */
    suspend fun lotteryRecords(page: Int = 1, pageSize: Int = 20): Result<LotteryRecordPage> =
        withContext(Dispatchers.IO) {
            apiCall {
                val d = request(
                    "lottery_records",
                    mapOf("page" to page, "page_size" to pageSize),
                    UserStore.token,
                ).optJSONObject("data")
                val arr = d?.optJSONArray("list")
                LotteryRecordPage(
                    list = if (arr == null) emptyList() else
                        (0 until arr.length()).map { LotteryRecord.fromJson(arr.getJSONObject(it)) },
                    total = d?.optInt("total", 0) ?: 0,
                    page = d?.optInt("page", page) ?: page,
                    pageSize = d?.optInt("page_size", pageSize) ?: pageSize,
                )
            }
        }

    /**
     * 消息通知列表 (需登录)
     * @return Triple(list, total, unread)
     */
    suspend fun notifications(
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<NotifyPage> = withContext(Dispatchers.IO) {
        apiCall {
            val d = request(
                "notifications",
                mapOf("page" to page, "page_size" to pageSize),
                UserStore.token,
            ).optJSONObject("data")
            val arr = d?.optJSONArray("list")
            val list = if (arr == null) {
                emptyList()
            } else {
                (0 until arr.length()).map { NotifyItem.fromJson(arr.getJSONObject(it)) }
            }
            val byType = mutableMapOf<String, Int>()
            d?.optJSONObject("unread_by_type")?.let { obj ->
                obj.keys().forEach { k -> byType[k] = obj.optInt(k, 0) }
            }
            NotifyPage(
                list = list,
                total = d?.optInt("total", list.size) ?: list.size,
                unread = d?.optInt("unread", 0) ?: 0,
                unreadByType = byType,
            )
        }
    }

    /**
     * 标记通知已读 (all=true 时全部已读, 否则按 id 单条)
     * @return 服务端返回的新未读数
     */
    suspend fun notificationRead(id: Int = 0, all: Boolean = false): Result<Int> =
        withContext(Dispatchers.IO) {
            apiCall {
                val params: Map<String, Any?> =
                    if (all) mapOf("all" to 1) else mapOf("id" to id)
                request("notification_read", params, UserStore.token)
                    .optJSONObject("data")?.optInt("unread", 0) ?: 0
            }
        }

    /** 删除单条通知 */
    suspend fun notificationDelete(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        apiCall {
            request("notification_delete", mapOf("id" to id), UserStore.token)
            Unit
        }
    }

    /**
     * 给当前账号邮箱发验证码 (需登录, 需图形验证码)
     * @param email 传空串表示用当前账号邮箱
     * @return 实际发送到的邮箱
     */
    suspend fun emailVerifySend(
        email: String,
        captchaToken: String,
        captchaCode: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>(
                "captcha_token" to captchaToken,
                "captcha_code" to captchaCode,
            )
            if (email.isNotBlank()) params["email"] = email
            request("email_verify_send", params, UserStore.token)
                .optJSONObject("data")?.optString("email", "") ?: ""
        }
    }

    /** 提交邮箱验证码, 成功后 email_verified=1 并返回最新 user */
    suspend fun emailVerify(email: String, code: String): Result<User> =
        withContext(Dispatchers.IO) {
            apiCall {
                val u = request(
                    "email_verify",
                    mapOf("email" to email, "code" to code),
                    UserStore.token,
                ).optJSONObject("data")?.optJSONObject("user")
                if (u == null) User() else User.fromJson(u)
            }
        }

}

/** 版本信息 */
data class VersionInfo(
    val version: String,
    val url: String,
    val updateLog: String,
    val updateMode: String = "internal", // internal=内置浏览器, external=外置浏览器
    val forceUpdate: Boolean = false,    // true=强制更新
    val sizeMb: Float = 0f,              // APK 大小 (MB)
    val releaseDate: String = "",        // 发布日期
)

/** 关于页配置 (后端可配置) */
data class AboutConfig(
    val qqGroup: String = "",
    val qqKey: String = "",
    val qqUrl: String = "",
    val qqChannel: String = "",
    val website: String = "",
    val github: String = "",
    val feedback: String = "",
    val donate: String = "",
    val bannerText: String = "风铃分享库 · 官方频道",
    val bannerSub: String = "最新软件 · 更新通知 · 交流反馈",
)

/** 公告信息 */
data class NoticeInfo(
    val content: String = "",
    val mode: String = "daily", // daily=每日显示一次, every=每次打开显示
    val enabled: Boolean = false,
)

/** 投稿人 (关于页名单) */
data class Contributor(
    val avatar: String = "",
    val name: String = "",   // 昵称 (App 端显示)
    val bio: String = "",    // 投稿说明 (App 端显示)
    val appNames: List<String> = emptyList(),
)

/** 语义化版本比较: 返回 true 表示 [latest] 比 [current] 新 */
fun isNewerVersion(latest: String, current: String): Boolean {
    if (latest.isBlank() || current.isBlank()) return false
    if (latest == current) return false
    val parse: (String) -> List<Int> = { s ->
        s.trim().split(".").mapNotNull { it.toIntOrNull() }
    }
    val l = parse(latest)
    val c = parse(current)
    val maxLen = maxOf(l.size, c.size)
    for (i in 0 until maxLen) {
        val lv = l.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (lv != cv) return lv > cv
    }
    return false
}

class ApiException(message: String) : Exception(message)

/**
 * 用户可见错误文案 — 绝不展示原始异常消息
 * OkHttp 连接失败消息形如 "Failed to connect to /<host>:<port>", 直接显示会泄露服务器 IP
 * (用户明确要求: 没联网打开分享库也不能暴露服务器 IP; 地址本身也已加密见 ServerConfig)
 */
fun Throwable.userFriendlyMessage(): String = when (this) {
    is ApiException -> message?.takeIf { it.isNotBlank() } ?: "请求失败"
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.SocketTimeoutException,
    is java.io.IOException -> "网络连接失败，请检查网络后重试"
    else -> "加载失败，请稍后重试"
}

// ===================== 社交 / 消息中心 数据模型 (接口契约 v1) =====================

/**
 * org.json 的 JSONObject.NULL 用 optString 会得到字符串 "null", 统一挡掉
 * (与 UserStore 内部同名私有函数互不影响)
 */
private fun jsonStr(j: JSONObject, key: String, def: String = ""): String =
    if (j.isNull(key)) def else j.optString(key, def)

/** 兼容后端返回 int(0/1) / bool / 字符串三种写法 */
private fun jsonBool(j: JSONObject, key: String): Boolean {
    if (j.isNull(key)) return false
    return when (val v = j.opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() == 1
        else -> v?.toString() == "1" || v?.toString() == "true"
    }
}

/** 兼容后端返回 int / 字符串数字 / null 三种写法 */
private fun jsonInt(j: JSONObject, key: String, def: Int = 0): Int {
    if (j.isNull(key)) return def
    return when (val v = j.opt(key)) {
        is Number -> v.toInt()
        else -> v?.toString()?.trim()?.toIntOrNull() ?: def
    }
}

/**
 * 标签数组解析 (契约 F1): 服务端一律返回 `tags: []`,
 * 同时兜住「单个逗号分隔字符串」与 null 两种写法, 免得老数据把页面搞崩。
 */
private fun jsonStrList(j: JSONObject, key: String): List<String> {
    val arr = j.optJSONArray(key)
    if (arr != null) {
        return (0 until arr.length())
            .map { arr.optString(it, "") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val single = jsonStr(j, key)
    return if (single.isBlank()) emptyList()
    else single.split(',').map { it.trim() }.filter { it.isNotBlank() }
}

/** 群聊图片: 上传接口返回 (width/height 是原图尺寸, 用于气泡按比例排版) */
data class ChatImage(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/** 图形验证码: image 是 data:image/png;base64,... 可直接给 Coil */
data class Captcha(
    val token: String = "",
    val image: String = "",
)

/** 社交群组 (社交页列表卡片) */
data class SocialGroup(
    val id: Int = 0,
    val name: String = "",
    val icon: String = "",
    val description: String = "",
    val notice: String = "",
    val memberCount: Int = 0,
    val messageCount: Int = 0,
    /** 我是否对这个世界开了消息免打扰 */
    val muted: Boolean = false,
    /** 群主 / 管理员是否开启了「全体禁言」(true = 只有管理员能发言, 契约 A2) */
    val allMuted: Boolean = false,
    /** 该群未读条数 (只算别人发的、未撤回的消息; 0 = 全部已读) */
    val unread: Int = 0,
    /** 第一条未读消息 id (无未读为 0); 进群时拿它当 around_id 定位 */
    val firstUnreadId: Int = 0,
    /** 最新一条消息 (null = 群里还没人发过言) */
    val lastMessage: LastMessage? = null,
    /** 最新消息时间 "yyyy-MM-dd HH:mm:ss" */
    val lastTime: String = "",
    /** 未读里「@我」的条数 (0 = 没有) */
    val atMe: Int = 0,
    /** 第一条「@我」的消息 id (点「有人@你」进群用 around_id 定位) */
    val atMeFirst: Int = 0,
    /** 未读里「@所有人」的条数 */
    val atAll: Int = 0,
    /** 第一条「@所有人」的消息 id */
    val atAllFirst: Int = 0,
) {
    companion object {
        fun fromJson(j: JSONObject): SocialGroup = SocialGroup(
            id = j.optInt("id", 0),
            name = jsonStr(j, "name"),
            icon = jsonStr(j, "icon"),
            description = jsonStr(j, "description"),
            notice = jsonStr(j, "notice"),
            memberCount = j.optInt("member_count", 0),
            messageCount = j.optInt("message_count", 0),
            muted = j.optInt("muted", 0) == 1,
            allMuted = j.optInt("all_muted", 0) == 1,
            unread = j.optInt("unread", 0),
            firstUnreadId = j.optInt("first_unread_id", 0),
            lastMessage = j.optJSONObject("last_message")?.let { LastMessage.fromJson(it) },
            lastTime = jsonStr(j, "last_time"),
            atMe = jsonInt(j, "at_me"),
            atMeFirst = jsonInt(j, "at_me_first"),
            atAll = jsonInt(j, "at_all"),
            atAllFirst = jsonInt(j, "at_all_first"),
        )
    }
}

/** 群列表里的「最新一条消息」摘要 (social_groups.last_message) */
data class LastMessage(
    val id: Int = 0,
    val userId: Int = 0,
    /** 发消息的人 (副标题里显示「昵称: 内容」) */
    val nickname: String = "",
    val content: String = "",
    /** 纯图片消息: content 为空, image 非空 → 前端显示 [图片] */
    val image: String = "",
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): LastMessage = LastMessage(
            id = j.optInt("id", 0),
            userId = j.optInt("user_id", 0),
            nickname = jsonStr(j, "nickname"),
            content = jsonStr(j, "content"),
            image = jsonStr(j, "image"),
            createdAt = jsonStr(j, "created_at"),
        )
    }
}

/** 群消息 (isRecalled 时 content 为空串, 前端显示「该消息已撤回」) */
/** 群成员候选 (用于 @ 选择) */
data class SocialGroupMember(
    val id: Int = 0,
    val nickname: String = "",
    val username: String = "",
    val avatar: String = "",
    val role: String = "",
    /** 是否被管理员禁言 (禁言只影响发言, 仍然能看消息) */
    val muted: Boolean = false,
    /** 禁言剩余时长文案, 例如「剩余 1 小时」/「永久」 */
    val muteLeft: String = "",
    val muteReason: String = "",
    /** 管理员打在这个成员身上的标签 (契约 F1, 防骗警示) */
    val tags: List<String> = emptyList(),
) {
    companion object {
        fun fromJson(j: JSONObject): SocialGroupMember = SocialGroupMember(
            id = j.optInt("id", 0),
            nickname = jsonStr(j, "nickname"),
            username = jsonStr(j, "username"),
            avatar = jsonStr(j, "avatar"),
            role = jsonStr(j, "role"),
            muted = j.optInt("muted", 0) == 1,
            muteLeft = jsonStr(j, "mute_left"),
            muteReason = jsonStr(j, "mute_reason"),
            tags = jsonStrList(j, "tags"),
        )
    }
}

data class SocialMessage(
    val id: Int = 0,
    val groupId: Int = 0,
    val userId: Int = 0,
    val nickname: String = "",
    val avatar: String = "",
    val role: String = "user",
    /** 发送者的管理员标签 (契约 F1), 显示在群聊气泡昵称旁 */
    val tags: List<String> = emptyList(),
    val content: String = "",
    /** 图片消息: 对象存储直链 (空串 = 没图; 撤回后也是空串) */
    val image: String = "",
    /** 原图宽 (0 = 没有图或未知) */
    val imageW: Int = 0,
    /** 原图高 */
    val imageH: Int = 0,
    val at: List<Int> = emptyList(),
    /** 引用的原消息 (0 = 不是引用) */
    val quoteId: Int = 0,
    val quoteNickname: String = "",
    val quoteContent: String = "",
    val isRecalled: Boolean = false,
    val createdAt: String = "",
    val timeText: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): SocialMessage {
            val atArr = j.optJSONArray("at")
            return SocialMessage(
                id = j.optInt("id", 0),
                groupId = j.optInt("group_id", 0),
                userId = j.optInt("user_id", 0),
                nickname = jsonStr(j, "nickname"),
                avatar = jsonStr(j, "avatar"),
                role = jsonStr(j, "role").ifBlank { "user" },
                tags = jsonStrList(j, "tags"),
                content = jsonStr(j, "content"),
                image = jsonStr(j, "image"),
                imageW = jsonInt(j, "image_w"),
                imageH = jsonInt(j, "image_h"),
                at = if (atArr == null) {
                    emptyList()
                } else {
                    (0 until atArr.length()).map { atArr.optInt(it, 0) }
                },
                quoteId = j.optInt("quote_id", 0),
                quoteNickname = jsonStr(j, "quote_nickname"),
                quoteContent = jsonStr(j, "quote_content"),
                isRecalled = jsonBool(j, "is_recalled"),
                createdAt = jsonStr(j, "created_at"),
                timeText = jsonStr(j, "time_text"),
            )
        }
    }
}

/** 消息通知 (type: system=系统 / admin=管理员 / social=社交) */
data class NotifyItem(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val type: String = "system",
    val link: String = "",
    val isRead: Boolean = false,
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): NotifyItem = NotifyItem(
            id = j.optInt("id", 0),
            title = jsonStr(j, "title"),
            content = jsonStr(j, "content"),
            type = jsonStr(j, "type").ifBlank { "system" },
            link = jsonStr(j, "link"),
            isRead = jsonBool(j, "is_read"),
            createdAt = jsonStr(j, "created_at"),
        )
    }
}

/** 抽奖奖项 (服务端只下发「启用 且 还有库存」的奖项) */
data class LotteryPrize(
    val id: Int = 0,
    val name: String = "",
    /** 卡密类型: 天卡 / 周卡 / 月卡 (也可以是后台自定义的任意文本) */
    val cardType: String = "",
    /** 该奖项剩余可抽数量 */
    val left: Int = 0,
) {
    companion object {
        fun fromJson(j: JSONObject): LotteryPrize = LotteryPrize(
            id = j.optInt("id", 0),
            name = jsonStr(j, "name"),
            cardType = jsonStr(j, "card_type"),
            left = j.optInt("left", 0),
        )
    }
}

/** 我的中奖记录 (含卡密, 客户端可直接复制) */
data class LotteryRecord(
    val id: Int = 0,
    val prizeId: Int = 0,
    val prizeName: String = "",
    val cardType: String = "",
    val code: String = "",
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): LotteryRecord = LotteryRecord(
            id = j.optInt("id", 0),
            prizeId = j.optInt("prize_id", 0),
            prizeName = jsonStr(j, "prize_name"),
            cardType = jsonStr(j, "card_type"),
            code = jsonStr(j, "code"),
            createdAt = jsonStr(j, "created_at"),
        )
    }
}

/** 抽奖总览 (lottery_info) */
data class LotteryInfo(
    /** 后台开关: false = 抽奖活动已关闭, 客户端要禁用抽奖按钮 */
    val enabled: Boolean = false,
    val title: String = "",
    /** 抽奖说明, 可含换行 (客户端用 SelectionContainer 包住方便复制) */
    val content: String = "",
    /** 默认每人可抽次数 (仅展示用) */
    val perUserLimit: Int = 0,
    /** 我还能抽几次 (>=0, 已扣掉已抽的) */
    val myQuota: Int = 0,
    /** 我已经抽了几次 */
    val myDrawn: Int = 0,
    /** 每日次数上限 (0 = 不限次数, 客户端不显示每日次数) */
    val dailyLimit: Int = 0,
    /** 我今天已经抽了几次 */
    val myTodayDrawn: Int = 0,
    /** 我今天还能抽几次 (dailyLimit = 0 时为不限) */
    val myTodayLeft: Int = 0,
    val prizes: List<LotteryPrize> = emptyList(),
    /** 我最近的中奖记录 */
    val records: List<LotteryRecord> = emptyList(),
) {
    companion object {
        fun fromJson(j: JSONObject): LotteryInfo {
            val prizeArr = j.optJSONArray("prizes")
            val recordArr = j.optJSONArray("records")
            return LotteryInfo(
                enabled = jsonBool(j, "enabled"),
                title = jsonStr(j, "title"),
                content = jsonStr(j, "content"),
                perUserLimit = jsonInt(j, "per_user_limit"),
                myQuota = jsonInt(j, "my_quota"),
                myDrawn = jsonInt(j, "my_drawn"),
                dailyLimit = jsonInt(j, "daily_limit"),
                myTodayDrawn = jsonInt(j, "my_today_drawn"),
                myTodayLeft = jsonInt(j, "my_today_left"),
                prizes = if (prizeArr == null) emptyList() else
                    (0 until prizeArr.length()).map { LotteryPrize.fromJson(prizeArr.getJSONObject(it)) },
                records = if (recordArr == null) emptyList() else
                    (0 until recordArr.length()).map { LotteryRecord.fromJson(recordArr.getJSONObject(it)) },
            )
        }
    }
}

/** 一次抽奖的结果 (lottery_draw) */
data class LotteryResult(
    val prizeId: Int = 0,
    val prizeName: String = "",
    val cardType: String = "",
    /** 抽中的卡密 (要大字等宽显示 + 可复制) */
    val code: String = "",
    /** 抽完后我还剩几次 */
    val left: Int = 0,
) {
    companion object {
        fun fromJson(j: JSONObject): LotteryResult = LotteryResult(
            prizeId = j.optInt("prize_id", 0),
            prizeName = jsonStr(j, "prize_name"),
            cardType = jsonStr(j, "card_type"),
            code = jsonStr(j, "code"),
            left = jsonInt(j, "left"),
        )
    }
}

/** 中奖记录一页 (lottery_records) */
data class LotteryRecordPage(
    val list: List<LotteryRecord> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

// ===================== 私聊 / 用户主页 数据模型 (接口契约 2026-10-04 A 节) =====================

/**
 * 用户主页 (user_profile)
 * - canChat = false 或 isMe = true 时不显示「发消息」按钮
 * - convId = 0 表示还没聊过 (第一次发消息用 user_id, 服务端会自动建会话)
 */
data class UserProfile(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val bio: String = "",
    /** admin = 管理员, 其它 = 普通用户 */
    val role: String = "user",
    val createdAt: String = "",
    /** 发过的消息数 */
    val messageCount: Int = 0,
    /** 和我共同所在的群数 */
    val sameGroups: Int = 0,
    /** 是不是我自己 (1 = 自己) */
    val isMe: Boolean = false,
    /** 还能不能和我私聊 (0 = 不能, 例如被封禁) */
    val canChat: Boolean = true,
    /** 已有私聊会话 id (0 = 还没聊过) */
    val convId: Int = 0,
    /** 管理员打的标签 (契约 F1, 防骗警示) */
    val tags: List<String> = emptyList(),
    /** 本页是按哪个群看的 (回显; 0 = 全站视角) */
    val groupId: Int = 0,
    /** 该群已被禁言 (group_id 传 0 时即全站禁言) */
    val muted: Boolean = false,
    /** 禁言剩余时长文案, 例如「剩余 1 小时」/「永久」 */
    val muteLeft: String = "",
    /** 禁言原因 */
    val muteReason: String = "",
    /** 全站禁言状态 (不管 group_id 传了什么都会返回) */
    val globalMuted: Boolean = false,
    val globalMuteLeft: String = "",
    val globalMuteReason: String = "",
    /** 我是不是管理员 */
    val isAdminMe: Boolean = false,
    /** 我能不能禁言他 (管理员=1 / 自己=0 / 目标是管理员=0) */
    val canMute: Boolean = false,
) {
    /** 展示名: 昵称优先, 没有昵称用用户名 */
    val displayName: String get() = nickname.ifBlank { username }

    /** 头像首字占位 */
    val initial: String get() = displayName.take(1).ifBlank { "铃" }

    val isAdmin: Boolean get() = role == "admin"

    /** 是否显示「发消息」按钮 */
    val canStartChat: Boolean get() = !isMe && canChat

    companion object {
        fun fromJson(j: JSONObject): UserProfile = UserProfile(
            id = j.optInt("id", 0),
            username = jsonStr(j, "username"),
            nickname = jsonStr(j, "nickname"),
            avatar = jsonStr(j, "avatar"),
            bio = jsonStr(j, "bio"),
            role = jsonStr(j, "role").ifBlank { "user" },
            createdAt = jsonStr(j, "created_at"),
            messageCount = jsonInt(j, "message_count"),
            sameGroups = jsonInt(j, "same_groups"),
            isMe = jsonBool(j, "is_me"),
            canChat = jsonBool(j, "can_chat"),
            convId = jsonInt(j, "conv_id"),
            tags = jsonStrList(j, "tags"),
            groupId = jsonInt(j, "group_id"),
            muted = jsonBool(j, "muted"),
            muteLeft = jsonStr(j, "mute_left"),
            muteReason = jsonStr(j, "mute_reason"),
            globalMuted = jsonBool(j, "global_muted"),
            globalMuteLeft = jsonStr(j, "global_mute_left"),
            globalMuteReason = jsonStr(j, "global_mute_reason"),
            isAdminMe = jsonBool(j, "is_admin_me"),
            canMute = jsonBool(j, "can_mute"),
        )
    }
}

/** 私聊会话里的对方 (pm_conversations.list[].user / pm_messages.other) */
data class PmPeer(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val bio: String = "",
    val role: String = "user",
    val createdAt: String = "",
    /** 管理员标签 (契约 F1), 私聊页顶部显示 */
    val tags: List<String> = emptyList(),
) {
    val displayName: String get() = nickname.ifBlank { username }

    val initial: String get() = displayName.take(1).ifBlank { "铃" }

    companion object {
        fun fromJson(j: JSONObject): PmPeer = PmPeer(
            id = j.optInt("id", 0),
            username = jsonStr(j, "username"),
            nickname = jsonStr(j, "nickname"),
            avatar = jsonStr(j, "avatar"),
            bio = jsonStr(j, "bio"),
            role = jsonStr(j, "role").ifBlank { "user" },
            createdAt = jsonStr(j, "created_at"),
            tags = jsonStrList(j, "tags"),
        )
    }
}

/** 一条私聊消息 (mine = true 时是自己发的, 显示在右边) */
data class PmMessage(
    val id: Int = 0,
    val convId: Int = 0,
    val userId: Int = 0,
    val toUser: Int = 0,
    val content: String = "",
    /** 图片直链 (空串 = 没图; 撤回后也是空串) */
    val image: String = "",
    val imageW: Int = 0,
    val imageH: Int = 0,
    val isRecalled: Boolean = false,
    /** 服务端直接告诉我们这条是不是自己发的 */
    val mine: Boolean = false,
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): PmMessage = PmMessage(
            id = j.optInt("id", 0),
            convId = jsonInt(j, "conv_id"),
            userId = jsonInt(j, "user_id"),
            toUser = jsonInt(j, "to_user"),
            content = jsonStr(j, "content"),
            image = jsonStr(j, "image"),
            imageW = jsonInt(j, "image_w"),
            imageH = jsonInt(j, "image_h"),
            isRecalled = jsonBool(j, "is_recalled"),
            mine = jsonBool(j, "mine"),
            createdAt = jsonStr(j, "created_at"),
        )
    }
}

/** 私聊会话 (会话列表一行) */
data class PmConversation(
    val convId: Int = 0,
    val userId: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    /** 对方的管理员标签 (契约 F1), 会话列表昵称旁显示 */
    val tags: List<String> = emptyList(),
    /** 最新一条消息 (null = 会话里还没有消息) */
    val last: PmMessage? = null,
    val lastTime: String = "",
    val unread: Int = 0,
    /** 第一条未读消息 id (无未读为 0); 进会话时拿它定位 */
    val firstUnreadId: Int = 0,
) {
    val displayName: String get() = nickname.ifBlank { username }

    val initial: String get() = displayName.take(1).ifBlank { "铃" }

    companion object {
        fun fromJson(j: JSONObject): PmConversation {
            val u = j.optJSONObject("user")
            return PmConversation(
                convId = jsonInt(j, "conv_id"),
                userId = u?.optInt("id", 0) ?: 0,
                username = if (u == null) "" else jsonStr(u, "username"),
                nickname = if (u == null) "" else jsonStr(u, "nickname"),
                avatar = if (u == null) "" else jsonStr(u, "avatar"),
                tags = if (u == null) emptyList() else jsonStrList(u, "tags"),
                last = j.optJSONObject("last")?.let { PmMessage.fromJson(it) },
                lastTime = jsonStr(j, "last_time"),
                unread = jsonInt(j, "unread"),
                firstUnreadId = jsonInt(j, "first_unread_id"),
            )
        }
    }
}

/** 私聊会话列表一页 (totalUnread = 所有会话未读之和) */
data class PmConversationPage(
    val list: List<PmConversation> = emptyList(),
    val totalUnread: Int = 0,
)

/** 私聊消息一页 */
data class PmMessagesPage(
    /** 0 = 还没建立会话 */
    val convId: Int = 0,
    /** 对方信息 (首次私聊传 user_id 时也会返回) */
    val other: PmPeer? = null,
    val list: List<PmMessage> = emptyList(),
    val hasMoreBefore: Boolean = false,
    val unread: Int = 0,
    val firstUnreadId: Int = 0,
    val myId: Int = 0,
)

/** 发送私聊消息的结果 */
data class PmSendResult(
    val id: Int = 0,
    val convId: Int = 0,
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject): PmSendResult = PmSendResult(
            id = j.optInt("id", 0),
            convId = jsonInt(j, "conv_id"),
            createdAt = jsonStr(j, "created_at"),
        )
    }
}
