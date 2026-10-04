package com.fengling.share.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore
import com.fengling.share.service.MessageService

/**
 * BootReceiver - 开机 / 应用更新后自动恢复后台常驻消息服务 (v1.0.36)
 *
 * 手机重启、系统更新、应用被覆盖安装之后, 常驻服务不会自己回来, 用户不打开 App 就漏消息。
 * 这里监听开机广播, 满足「已登录 + msg_service_on 打开」就把 [MessageService] 再拉起来。
 *
 * 注意: receiver 里 `Settings` / `UserStore` 还没初始化 (只在 Activity.onCreate 里 init 过),
 * 所以必须先用 applicationContext 初始化, 否则会抛 UninitializedPropertyAccessException。
 * 整段 runCatching 包住: 开机广播崩了会被系统记 Crash, 宁可静默失败。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in BOOT_ACTIONS) return

        runCatching {
            val app = context.applicationContext
            Settings.init(app)
            UserStore.init(app)
            if (UserStore.isLoggedIn() && Settings.msgServiceOn) {
                MessageService.start(app)
            }
        }
    }

    companion object {
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
