package com.fengling.share.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * 登录用户 (字段与后端 user 对象一致, 见接口契约)
 * emailVerified: 后端返回 int(1/0), 也兼容 bool/字符串
 */
data class User(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val avatar: String = "",
    val bio: String = "",
    val role: String = "user",
) {
    /** 展示名: 昵称优先, 没有昵称用用户名 */
    val displayName: String get() = nickname.ifBlank { username }

    /** 首字占位 (昵称/用户名都为空时给个默认字) */
    val initial: String get() = displayName.take(1).ifBlank { "铃" }

    /**
     * 头像地址: 服务端没给头像时, 用 QQ 邮箱里的 QQ 号取腾讯公开的 qlogo 头像兜底。
     * (注册 / 邮箱验证时服务端也会把同一个地址写进 users.avatar, 这里只是双保险,
     *  顺便兼容「以前就用 QQ 邮箱注册、当时还没有这个功能」的老账号)
     */
    val avatarUrl: String get() = avatar.ifBlank { qqAvatarOf(email) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("username", username)
        put("nickname", nickname)
        put("email", email)
        put("email_verified", if (emailVerified) 1 else 0)
        put("avatar", avatar)
        put("bio", bio)
        put("role", role)
    }

    companion object {
        fun fromJson(json: JSONObject): User = User(
            id = json.optInt("id", 0),
            username = optStr(json, "username"),
            nickname = optStr(json, "nickname"),
            email = optStr(json, "email"),
            emailVerified = optBool(json, "email_verified"),
            avatar = optStr(json, "avatar"),
            bio = optStr(json, "bio"),
            role = optStr(json, "role").ifBlank { "user" },
        )

        /** org.json 的 JSONObject.NULL 用 optString 会得到字符串 "null", 这里统一挡掉 */
        private fun optStr(json: JSONObject, key: String, def: String = ""): String =
            if (json.isNull(key)) def else json.optString(key, def)

        private fun optBool(json: JSONObject, key: String): Boolean {
            if (json.isNull(key)) return false
            return when (val v = json.opt(key)) {
                is Boolean -> v
                is Number -> v.toInt() == 1
                else -> v?.toString() == "1" || v?.toString() == "true"
            }
        }
    }
}

/**
 * 登录态存储 (SharedPreferences + Compose 可观察状态)
 *
 * 用法: Application/Activity onCreate 里调一次 [init], 之后可直接读写;
 * [current] / [hasToken] 是 mutableStateOf, 登录、改资料、退出时同步更新,
 * 所以相关页面 (关于页账号卡片 / 账号页) 会自动刷新, 不需要手动通知。
 */
/** QQ 邮箱 -> QQ 号 (非 QQ 邮箱返回 null) */
private val QQ_EMAIL_RE = Regex("^(\\d{5,12})@(qq\\.com|vip\\.qq\\.com)$", RegexOption.IGNORE_CASE)

/** 用 QQ 号拼腾讯公开的 qlogo 头像地址 (服务端 qq_avatar_from_email 用的是同一个) */
internal fun qqAvatarOf(email: String): String {
    val qq = QQ_EMAIL_RE.find(email.trim())?.groupValues?.get(1) ?: return ""
    return "https://q1.qlogo.cn/g?b=qq&nk=$qq&s=640"
}

object UserStore {

    private const val PREFS_NAME = "fengling_user"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"

    /** 未 init 时为 null, 所有读写都做空保护, 不会因为漏调 init 崩溃 */
    private var prefs: SharedPreferences? = null
    private var cachedToken: String = ""

    /** 当前登录用户 (Compose 可观察) */
    var current: User? by mutableStateOf(null)
        private set

    /** 是否持有 token (Compose 可观察) */
    var hasToken: Boolean by mutableStateOf(false)
        private set

    /** 在 Application/Activity 里调一次 (幂等), 从本地恢复上次登录态 */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        cachedToken = p.getString(KEY_TOKEN, "") ?: ""
        current = parseUser(p.getString(KEY_USER, "") ?: "")
        hasToken = cachedToken.isNotBlank()
    }

    /** 当前 token (未登录返回空串) */
    val token: String get() = cachedToken

    /** 当前用户 (等价于 [current], 便于非 Compose 代码调用) */
    val user: User? get() = current

    fun isLoggedIn(): Boolean = cachedToken.isNotBlank()

    /** 登录/注册成功后保存 token + user */
    fun save(token: String, user: User?) {
        cachedToken = token
        current = user
        hasToken = token.isNotBlank()
        prefs?.edit()
            ?.putString(KEY_TOKEN, token)
            ?.putString(KEY_USER, user?.toJson()?.toString() ?: "")
            ?.apply()
    }

    /** 仅更新用户资料 (改昵称/简介/头像后调用) */
    fun updateUser(user: User) {
        current = user
        prefs?.edit()?.putString(KEY_USER, user.toJson().toString())?.apply()
    }

    /** 退出登录: 清空本地 token + 用户 */
    fun clear() {
        cachedToken = ""
        current = null
        hasToken = false
        prefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_USER)?.apply()
    }

    private fun parseUser(raw: String): User? = try {
        if (raw.isBlank()) null else User.fromJson(JSONObject(raw))
    } catch (_: Exception) {
        null
    }
}
