package com.fengling.share.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
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
    /** 管理员打在这个账号身上的标签 (契约 F1, 防骗警示), 一般只在「我」的资料里带回来 */
    val tags: List<String> = emptyList(),
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
        put("tags", JSONArray(tags))
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
            tags = optStrList(json, "tags"),
        )

        /** org.json 的 JSONObject.NULL 用 optString 会得到字符串 "null", 这里统一挡掉 */
        private fun optStr(json: JSONObject, key: String, def: String = ""): String =
            if (json.isNull(key)) def else json.optString(key, def)

        /** 标签数组: 正常是 JSONArray, 老数据可能是逗号分隔字符串, 两种都认 */
        private fun optStrList(json: JSONObject, key: String): List<String> {
            if (json.isNull(key)) return emptyList()
            json.optJSONArray(key)?.let { arr ->
                return (0 until arr.length())
                    .map { arr.optString(it, "").trim() }
                    .filter { it.isNotBlank() }
            }
            return optStr(json, key)
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

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

/**
 * 账号保险箱里的一条账号 (契约 F5): 用来免密切换账号。
 * 只存 token 和展示要用的昵称/头像, **不存密码**。
 */
data class VaultAccount(
    val username: String = "",
    val token: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val userId: Int = 0,
) {
    val displayName: String get() = nickname.ifBlank { username }

    /** 用保险箱里的信息拼一个够列表/顶部展示的 User */
    fun toUser(): User = User(
        id = userId,
        username = username,
        nickname = nickname,
        avatar = avatar,
    )
}

object UserStore {

    private const val PREFS_NAME = "fengling_user"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"

    /** 最近登录过的用户名 (换行分隔), 用于「切换账号」 */
    private const val KEY_RECENT = "recent_accounts"
    private const val RECENT_MAX = 5

    /**
     * 账号保险箱: JSON 对象, username -> {token, nickname, avatar, userId}
     * 每次登录成功时写入; 退出登录/切换账号都**不清**, 所以点一下最近账号就能免密切回去。
     */
    private const val KEY_VAULT = "account_vault"

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

    /** 登录/注册成功后保存 token + user (同时写进账号保险箱, 供下次免密切换) */
    fun save(token: String, user: User?) {
        cachedToken = token
        current = user
        hasToken = token.isNotBlank()
        if (user != null && user.username.isNotBlank()) {
            addRecentAccount(user.username)
            putVaultAccount(user.username, token, user)
        }
        prefs?.edit()
            ?.putString(KEY_TOKEN, token)
            ?.putString(KEY_USER, user?.toJson()?.toString() ?: "")
            ?.apply()
    }

    /**
     * 最近登录过的用户名 (最新在前, 最多 5 个, 去重)
     * 只存用户名, 不存 token/密码, 切换账号时用来快速回填登录框
     */
    fun recentAccounts(): List<String> =
        (prefs?.getString(KEY_RECENT, "") ?: "")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    /** 记录一个登录过的用户名 (登录成功后自动调用) */
    fun addRecentAccount(username: String) {
        val name = username.trim()
        if (name.isBlank()) return
        val list = (listOf(name) + recentAccounts().filter { it != name }).take(RECENT_MAX)
        prefs?.edit()?.putString(KEY_RECENT, list.joinToString("\n"))?.apply()
    }

    /** 仅更新用户资料 (改昵称/简介/头像后调用) */
    fun updateUser(user: User) {
        current = user
        prefs?.edit()?.putString(KEY_USER, user.toJson().toString())?.apply()
        // 顺手把保险箱里的昵称/头像刷新一下, 免得切换账号列表显示旧名字
        if (user.username.isNotBlank()) putVaultAccount(user.username, token = null, user = user)
    }

    // ---------------- 账号保险箱 (切换账号免密, 契约 F5) ----------------

    private fun vaultJson(): JSONObject {
        val raw = try {
            prefs?.getString(KEY_VAULT, "") ?: ""
        } catch (_: Exception) {
            ""
        }
        return try {
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            // 老版本 / 损坏数据: 当作空保险箱, 不能崩
            JSONObject()
        }
    }

    /** 某条最近账号在保险箱里的信息 (没有 = 还得输密码) */
    fun vaultAccount(username: String): VaultAccount? {
        val name = username.trim()
        if (name.isBlank()) return null
        val o = vaultJson().optJSONObject(name) ?: return null
        return VaultAccount(
            username = name,
            token = o.optString("token", ""),
            nickname = o.optString("nickname", ""),
            avatar = o.optString("avatar", ""),
            userId = o.optInt("userId", 0),
        )
    }

    /**
     * 保险箱里的全部账号 (用于「切换账号」列表显示头像 + 昵称)。
     * 顺序跟 [recentAccounts] 对齐 (最近登录的在前), 保险箱里多出来的排最后。
     */
    fun vaultAccounts(): List<VaultAccount> {
        val j = vaultJson()
        val names = ArrayList<String>()
        recentAccounts().forEach { if (!j.isNull(it) && !names.contains(it)) names.add(it) }
        j.keys().forEach { k -> if (!names.contains(k)) names.add(k) }
        return names.mapNotNull { vaultAccount(it) }
    }

    /** 写入/更新保险箱条目 (token 传 null = 只更新昵称头像, 不动 token) */
    private fun putVaultAccount(username: String, token: String?, user: User) {
        val name = username.trim()
        if (name.isBlank()) return
        val j = vaultJson()
        val old = j.optJSONObject(name)
        val keepToken = token ?: old?.optString("token", "") ?: ""
        if (keepToken.isBlank()) return
        val o = JSONObject().apply {
            put("token", keepToken)
            put("nickname", user.nickname)
            put("avatar", user.avatar)
            put("userId", user.id)
        }
        j.put(name, o)
        prefs?.edit()?.putString(KEY_VAULT, j.toString())?.apply()
    }

    /** 忘掉一个账号 (token 已失效时调用): 保险箱 + 最近列表一起清 */
    fun forgetAccount(username: String) {
        val name = username.trim()
        if (name.isBlank()) return
        val j = vaultJson()
        j.remove(name)
        val list = recentAccounts().filter { it != name }
        prefs?.edit()
            ?.putString(KEY_VAULT, j.toString())
            ?.putString(KEY_RECENT, list.joinToString("\n"))
            ?.apply()
    }

    /**
     * 免密切换到某个最近账号 —— 从保险箱取出 token 直接把登录态切过去。
     *
     * ⚠️ 这里**只切换本地状态, 不校验 token**; 调用方切换后必须再调
     * `ApiClient.getMe()` 校验一次: 成功 = 切换完成; 失败(登录过期) =
     * 调 [forgetAccount] 清掉这条 + 回填用户名让用户重新输密码。
     *
     * ⚠️ **绝对不要在切换账号时调 `ApiClient.logout()`** —— 服务端 logout 会执行
     * `DELETE FROM sessions WHERE token = ?`, 会把那条 session token 删掉,
     * 下次就再也免密不起来了。只有用户主动点「退出登录」才调 logout。
     *
     * @return true = 保险箱里有这个账号, 登录态已切过去 (待校验);
     *         false = 没有 token (老版本记录 / 已过期), 请回填用户名让用户输密码
     */
    fun switchTo(username: String): Boolean {
        val name = username.trim()
        val acc = vaultAccount(name) ?: return false
        if (acc.token.isBlank()) return false
        cachedToken = acc.token
        current = acc.toUser()
        hasToken = true
        prefs?.edit()
            ?.putString(KEY_TOKEN, acc.token)
            ?.putString(KEY_USER, current?.toJson()?.toString() ?: "")
            ?.apply()
        return true
    }

    /**
     * 退出登录: 清空本地 token + 用户。
     * 账号保险箱**保持不动**, 下次还能点最近账号免密切回来 (契约 F5)。
     */
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
