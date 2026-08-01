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

    /** 版本检测: 返回最新版本信息 (version/url/update_log/update_mode/force_update) */
    suspend fun checkVersion(): VersionInfo = withContext(Dispatchers.IO) {
        val obj = request("version")
        val data = obj.optJSONObject("data") ?: JSONObject()
        VersionInfo(
            version = data.optString("version", ""),
            url = data.optString("url", ""),
            updateLog = data.optString("update_log", ""),
            updateMode = data.optString("update_mode", "internal"),
            forceUpdate = data.optInt("force_update", 0) == 1,
        )
    }
}

/** 版本信息 */
data class VersionInfo(
    val version: String,
    val url: String,
    val updateLog: String,
    val updateMode: String = "internal", // internal=内置浏览器, external=外置浏览器
    val forceUpdate: Boolean = false,    // true=强制更新
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
