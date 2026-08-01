package com.fengling.share.data

import android.content.Context
import android.content.SharedPreferences

/** 主题模式 */
enum class ThemeMode(val value: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
}

/**
 * 应用设置存储 (SharedPreferences)
 * 设置项: 预测返回开关 / 主题模式 / 已缓存最新版本
 */
object Settings {

    private const val PREFS_NAME = "fengling_settings"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ===== 预测返回 =====
    var predictiveBackEnabled: Boolean
        get() = prefs.getBoolean("predictive_back", true)
        set(value) = prefs.edit().putBoolean("predictive_back", value).apply()

    // ===== 主题模式 =====
    var themeMode: String
        get() = prefs.getString("theme_mode", ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    fun getThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.value == themeMode } ?: ThemeMode.SYSTEM

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
}
