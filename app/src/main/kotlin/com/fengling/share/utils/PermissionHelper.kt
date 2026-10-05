package com.fengling.share.utils

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore

/**
 * PermissionHelper - 权限状态判定 + 系统设置跳转 (v1.0.36)
 *
 * 抽出来的原因: 「启动自动权限引导」(ui/components/PermissionGuide.kt) 和
 * 「消息通知设置页」(ui/main/my/NotifySettingsScreen.kt) 用的是同一套判定与跳转,
 * 复制两份迟早会走偏, 所以统一放这里, 两处都调它。
 *
 * 三类权限:
 * 1. 通知 (POST_NOTIFICATIONS): 有系统一键允许框, 可直接申请;
 * 2. 忽略电池优化: 有系统一键允许框 (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
 * 3. 厂商自启动: **没有公开 API**, 只能跳到厂商页面让用户自己点一下。
 */
object PermissionHelper {

    /** 启动引导节流间隔: 12 小时 */
    const val GUIDE_INTERVAL_MS = 12 * 60 * 60 * 1000L

    // ==================== 状态判定 ====================

    /**
     * 通知是否可用 = 通知总开关开着 且 POST_NOTIFICATIONS 已授予。
     * Android 13+ 两个条件都可能单独不满足 (总开关被关 / 权限被撤销), 所以都要看。
     */
    fun notificationsEnabled(context: Context): Boolean {
        val channelOn = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
        if (!channelOn) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = runCatching {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            }.getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
            return granted
        }
        return true
    }

    /** 是否已在电池优化白名单里 */
    fun ignoringBattery(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(PowerManager::class.java) ?: return@runCatching false
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    /** 通知 + 电池优化 都满足 */
    fun allSatisfied(context: Context): Boolean =
        notificationsEnabled(context) && ignoringBattery(context)

    /**
     * 启动时该不该走权限引导 (状态驱动, 不是一次性 flag):
     * - 必须已登录 (没登录没有消息可漏) 且 msg_service_on 为 true;
     * - 通知或电池优化任一未满足;
     * - 距上次引导超过 12 小时, 或从未引导过 (perm_guide_ts = 0)。
     */
    fun shouldGuide(context: Context): Boolean = runCatching {
        if (!UserStore.isLoggedIn()) return false
        if (!Settings.msgServiceOn) return false
        if (allSatisfied(context)) return false
        val last = Settings.permGuideTs
        if (last > 0L && System.currentTimeMillis() - last < GUIDE_INTERVAL_MS) return false
        true
    }.getOrDefault(false)

    /** 引导走完一轮 (允许或稍后再说) 记一次时间戳, 用于 12 小时节流 */
    fun markGuided(context: Context) {
        runCatching { Settings.permGuideTs = System.currentTimeMillis() }
    }

    // ==================== 系统设置跳转 ====================

    /**
     * 厂商「自启动」页: 依次尝试, 跳不动 (没这个 Activity / 被系统拦) 就试下一个,
     * 全都不行最后落到应用详情页 —— 用户总能在那里找到「自启动 / 后台运行」开关。
     */
    fun openAutoStartSettings(context: Context) {
        for ((pkg, cls) in AUTOSTART_PAGES) {
            val intent = Intent().apply {
                component = ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 魅族那页要显式告诉它看哪个包
                if (pkg == "com.meizu.safe") putExtra("packageName", context.packageName)
            }
            // 先问一句「这个页面在不在」(v1.1.2): 该机型没这个页面就直接下一个候选,
            // 少一次注定失败的 startActivity。包可见性已在 Manifest <queries> 里声明。
            val resolvable = runCatching {
                context.packageManager.resolveActivity(intent, 0) != null
            }.getOrDefault(false)
            if (resolvable && runCatching { context.startActivity(intent) }.isSuccess) {
                Log.i(TAG, "自启动页跳转成功: " + pkg + "/" + cls)
                return
            }
            // resolveActivity == null 也可能只是厂商没在清单里导出: 再硬跳一次, 失败就下一个
            if (!resolvable && runCatching { context.startActivity(intent) }.isSuccess) {
                Log.i(TAG, "自启动页硬跳成功 (resolve 为 null): " + pkg + "/" + cls)
                return
            }
        }
        Log.w(TAG, "没有可用的自启动页, 退化到应用详情页: " + context.packageName)
        openAppDetailSettings(context)
    }

    /**
     * 忽略电池优化: 优先带包名的**系统一键允许框** (用户点一下「允许」即可),
     * 不行退到列表页, 再不行退到应用详情。
     */
    fun openIgnoreBatterySettings(context: Context) {
        val direct = runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess
        if (direct) return

        val list = runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess
        if (list) return

        openAppDetailSettings(context)
    }

    /** 通知设置页 (申请被拒过 / 已授予时用这个看详情) */
    fun openNotificationSettings(context: Context) {
        val ok = runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess
        if (!ok) openAppDetailSettings(context)
    }

    /** 最后的兜底: 应用详情页 (所有系统都有) */
    fun openAppDetailSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    /** 日志 tag (跳转自启动页的可观测性: 真机排查到底跳没跳成功) */
    private const val TAG = "FLS_PERM"

    /** 各家「自启动 / 后台运行」入口, 顺序 = 尝试顺序 */
    private val AUTOSTART_PAGES: List<Pair<String, String>> = listOf(
        // 小米 / 红米 / POCO
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // 华为
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
        // 荣耀
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity",
        // OPPO / 一加 / realme —— ColorOS 12+ 把「自启动管理」从安全中心挪进了「电池」应用 (v1.1.2 实测)：
        // ColorOS 14 真机上只有下面第一条真实存在 (dumpsys package com.oplus.battery 的证据:
        // action com.oplus.battery.permission.startup.StartupAppListActivity ->
        // com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity)，
        // 而 com.coloros.safecenter 在这台机器上**根本没装** (现名 com.oplus.safecenter，且里面没有自启动页)，
        // 所以旧表里的 coloros 候选必然全跳失败 —— 这就是「允许自启动」点了没反应的根因。
        "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
        "com.oplus.battery" to "com.oplus.startupapp.view.OptimizationAutoStartActivity",
        // 旧机型 (ColorOS 7~11) 的安全中心入口, 保底留着
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // vivo / iQOO
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // 魅族
        "com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC",
        // 三星
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity",
    )
}
