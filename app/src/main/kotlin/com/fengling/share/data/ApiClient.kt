package com.fengling.share.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 风铃分享库 API 客户端
 * 后端: PHP + Nginx, 地址 http://REDACTED_SERVER_HOST:9845/api.php
 * 响应格式: {"code":0,"msg":"ok","data":...}
 */
object ApiClient {

    private const val BASE_URL = "http://REDACTED_SERVER_HOST:9845/api.php"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 通用请求: 校验 code, 返回整个响应 JSONObject (含 data) */
    private fun request(action: String, params: Map<String, Any?> = emptyMap()): JSONObject {
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
                    else -> json.put(k, v.toString())
                }
            }
            body = json.toString().toRequestBody(JSON)
        }

        val builder = Request.Builder().url(url.toString())
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
