package com.fengling.share.data

import android.content.Context
import android.content.SharedPreferences

/** 主题模式 */
enum class ThemeMode(val value: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
}

/** 预置主题色板 */
enum class ThemeColor(val value: String, val label: String, val seed: Long) {
    BLUE("blue", "经典蓝", 0xFF4C8DFF),
    GREEN("green", "清新绿", 0xFF00B96B),
    ORANGE("orange", "活力橙", 0xFFFF8F1F),
    PINK("pink", "少女粉", 0xFFFF4D6D),
    PURPLE("purple", "高雅紫", 0xFF7C4DFF),
    TEAL("teal", "海洋青", 0xFF00B8D4),
    GOLD("gold", "奢华金", 0xFFFFB300),
    CUSTOM("custom", "自定义", 0xFF4C8DFF),
}

/**
 * 应用设置存储 (SharedPreferences)
 * 设置项: 主题模式 / 已缓存最新版本
 */
object Settings {

    private const val PREFS_NAME = "fengling_settings"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ===== 主题模式 =====
    var themeMode: String
        get() = prefs.getString("theme_mode", ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    fun getThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.value == themeMode } ?: ThemeMode.SYSTEM

    // ===== 主题色 =====
    var themeColor: String
        get() = prefs.getString("theme_color", ThemeColor.BLUE.value) ?: ThemeColor.BLUE.value
        set(value) = prefs.edit().putString("theme_color", value).apply()

    fun getThemeColor(): ThemeColor =
        ThemeColor.entries.firstOrNull { it.value == themeColor } ?: ThemeColor.BLUE

    /** 自定义主题色 (ARGB) */
    var customColor: Long
        get() = prefs.getLong("custom_color", ThemeColor.BLUE.seed)
        set(value) = prefs.edit().putLong("custom_color", value).apply()

    /** 当前生效的种子色 (预置或自定义) */
    fun currentSeedColor(): Long {
        val c = getThemeColor()
        return if (c == ThemeColor.CUSTOM) customColor else c.seed
    }

    // ===== 版本检测 =====
    var latestVersion: String
        get() = prefs.getString("latest_version", "") ?: ""
        set(value) = prefs.edit().putString("latest_version", value).apply()

    var latestVersionUrl: String
        get() = prefs.getString("latest_version_url", "") ?: ""
        set(value) = prefs.edit().putString("latest_version_url", value).apply()

    var updateCheckedAt: Long
        get() = prefs.getLong("update_checked_at", 0L)
        set(value) = prefs.edit().putLong("update_checked_at", value).apply()

    // ===== 公告 =====
    /** 用户勾选「今日不再提示」的日期 (yyyy-MM-dd), 当天不再弹公告 */
    var noticeHiddenDate: String
        get() = prefs.getString("notice_hidden_date", "") ?: ""
        set(value) = prefs.edit().putString("notice_hidden_date", value).apply()

    /** 勾选「今日不再提示」时的公告内容 — 公告改了即使当天也重新弹 */
    var noticeHiddenContent: String
        get() = prefs.getString("notice_hidden_content", "") ?: ""
        set(value) = prefs.edit().putString("notice_hidden_content", value).apply()

    /** 上次弹出公告的日期 (daily 模式: 每日只弹一次) */
    var noticeShownDate: String
        get() = prefs.getString("notice_shown_date", "") ?: ""
        set(value) = prefs.edit().putString("notice_shown_date", value).apply()

    /** 上次弹出公告的内容 — daily 模式公告改了也重新弹 */
    var noticeShownContent: String
        get() = prefs.getString("notice_shown_content", "") ?: ""
        set(value) = prefs.edit().putString("notice_shown_content", value).apply()

    // ===== 后台常驻消息服务 (v1.0.35) =====
    /**
     * 是否开启「后台接收消息」(常驻前台服务, key = msg_service_on)。
     *
     * 默认 **true**: 登录后就应该像 QQ/微信一样在后台秒收消息;
     * 用户想省电/不想看到状态栏常驻通知时, 可在「我的 → 消息通知」里关掉。
     */
    var msgServiceOn: Boolean
        get() = prefs.getBoolean("msg_service_on", true)
        set(value) = prefs.edit().putBoolean("msg_service_on", value).apply()

    // ===== 启动权限引导节流 (v1.0.36) =====
    /**
     * 上次走完「启动权限引导」的时间戳 (毫秒, key = perm_guide_ts)。
     *
     * 0 = 从未引导过。引导结束 (点了「一键允许」或「稍后再说」) 都会写一次,
     * 用于 12 小时节流: 期间不再骚扰用户; 设置页三个入口一直保留。
     */
    var permGuideTs: Long
        get() = prefs.getLong("perm_guide_ts", 0L)
        set(value) = prefs.edit().putLong("perm_guide_ts", value).apply()
}
