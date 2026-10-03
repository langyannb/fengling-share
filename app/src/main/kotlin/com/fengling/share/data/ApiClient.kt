package com.fengling.share.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
            val arr = request("social_groups").optJSONObject("data")?.optJSONArray("list")
            if (arr == null) {
                emptyList()
            } else {
                (0 until arr.length()).map { SocialGroup.fromJson(arr.getJSONObject(it)) }
            }
        }
    }

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
     * 群消息列表 (需登录)
     * @param afterId >0 时只取比它更新的消息 (3 秒轮询用); 返回已按时间正序
     */
    suspend fun socialMessages(
        groupId: Int,
        afterId: Int = 0,
        limit: Int = 30,
    ): Result<List<SocialMessage>> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("group_id" to groupId, "limit" to limit)
            if (afterId > 0) params["after_id"] = afterId
            val arr = request("social_messages", params, UserStore.token)
                .optJSONObject("data")?.optJSONArray("list")
            if (arr == null) {
                emptyList()
            } else {
                (0 until arr.length()).map { SocialMessage.fromJson(arr.getJSONObject(it)) }
            }
        }
    }

    /** 发送群消息, 成功返回新消息 id (同一用户同一群 2 秒 1 条) */
    suspend fun socialSend(
        groupId: Int,
        content: String,
        at: List<Int> = emptyList(),
        /** 管理员专用: @所有人 (全体提醒) */
        atAll: Boolean = false,
    ): Result<Int> = withContext(Dispatchers.IO) {
        apiCall {
            val params = mutableMapOf<String, Any?>("group_id" to groupId, "content" to content)
            if (at.isNotEmpty()) {
                params["at"] = JSONArray().apply { at.forEach { put(it) } }
            }
            if (atAll) params["at_all"] = 1
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

    /**
     * 消息通知列表 (需登录)
     * @return Triple(list, total, unread)
     */
    suspend fun notifications(
        page: Int = 1,
        pageSize: Int = 20,
    ): Result<Triple<List<NotifyItem>, Int, Int>> = withContext(Dispatchers.IO) {
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
            Triple(list, d?.optInt("total", list.size) ?: list.size, d?.optInt("unread", 0) ?: 0)
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
) {
    companion object {
        fun fromJson(j: JSONObject): SocialGroupMember = SocialGroupMember(
            id = j.optInt("id", 0),
            nickname = jsonStr(j, "nickname"),
            username = jsonStr(j, "username"),
            avatar = jsonStr(j, "avatar"),
            role = jsonStr(j, "role"),
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
    val content: String = "",
    val at: List<Int> = emptyList(),
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
                content = jsonStr(j, "content"),
                at = if (atArr == null) {
                    emptyList()
                } else {
                    (0 until atArr.length()).map { atArr.optInt(it, 0) }
                },
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
