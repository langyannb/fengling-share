package com.fengling.share.data

/**
 * 当前版本号 - 独立配置文件
 *
 * 更新检测的"当前版本"以此文件为准 (不是 build.gradle),
 * 防止改包者通过修改 versionName 绕过更新检测。
 * 发布新版本时: 改这里 + 后端发布 + 重新打包。
 */
object AppVersion {
    /** 当前版本号 (语义化 x.y.z) */
    const val CURRENT = "1.0.24"

    /** 版本代号 (build.gradle versionCode 对应值) */
    const val CODE = 124
}
