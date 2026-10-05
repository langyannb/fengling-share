package com.fengling.share.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.fengling.share.data.AppState
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore
import com.fengling.share.service.MessageService

/**
 * ServiceRestartReceiver - 被划掉 / 被杀之后把常驻消息服务拉回来 (v1.1.1, 核心需求 ①)
 *
 * 触发链路: MessageService 排下 AlarmManager 闹钟 (requestCode 1001,
 * action = [MessageService.ACTION_RESTART_SERVICE]) -> 这里收到 -> 重新 `start` 服务。
 *
 * 为什么需要它: `android:stopWithTask="false"` 已经能保证「划掉任务时服务不被停」,
 * 但 ColorOS / MIUI 这类 ROM 的「深度清理」会直接把进程杀掉, 那时只能靠系统闹钟
 * 在新进程里把它叫回来 (闹钟是跨进程 / 跨重启存活的)。
 *
 * 三重闸门, 缺一不可 —— 用户主动关掉的开关**绝不能**被这个广播重新打开:
 * 1. `UserStore.isLoggedIn()`: 没登录挂了也白挂;
 * 2. `Settings.msgServiceOn`: 用户在「我的 → 消息通知」里开着的总开关;
 * 3. `!AppState.serviceStoppedByUser`: 不是用户自己关的 (关开关 / 退出登录会置位)。
 *
 * 重试策略 (契约 C14): 前台服务的启动是异步的, receiver 里看不到成败, 所以每次拉起后
 * 30 秒再回来「看一眼」—— 服务起来了就归零计数, 没起来就再拉一次, 最多 3 次;
 * 超过 3 次就不再密集重试, 交给 15 分钟一次的 [com.fengling.share.service.MessageJobService] 兜底。
 * 计数写进 SharedPreferences (不是内存): 进程被杀后计数不会丢, 否则「最多 3 次」形同虚设。
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MessageService.ACTION_RESTART_SERVICE) return
        val app = context.applicationContext
        runCatching {
            // receiver 里 Settings / UserStore 还没初始化过, 必须先 init (只在 Activity.onCreate 里 init 过)
            Settings.init(app)
            UserStore.init(app)

            if (!UserStore.isLoggedIn() || !Settings.msgServiceOn || AppState.serviceStoppedByUser) {
                Log.i(
                    TAG,
                    "收到重启请求但不需要重启: 登录=" + UserStore.isLoggedIn() +
                        " 开关=" + Settings.msgServiceOn +
                        " 用户已关=" + AppState.serviceStoppedByUser,
                )
                setAttempts(app, 0)
                return
            }
            if (MessageService.isRunning) {
                // 已经回来了 (上一轮刚起来), 计数归零, 不再打扰
                setAttempts(app, 0)
                return
            }

            val n = attempts(app) + 1
            setAttempts(app, n)
            Log.i(TAG, "第 " + n + " 次拉起常驻消息服务")
            MessageService.start(app)

            if (n <= MAX_ATTEMPTS) {
                scheduleCheck(app, RETRY_CHECK_MS)
            } else {
                Log.w(TAG, "已连续尝试 " + n + " 次, 停止密集重试, 交给 15 分钟的周期看护任务")
            }
        }.onFailure { Log.w(TAG, "处理服务重启请求出错", it) }
    }

    /** 重启尝试计数: 存 SP 而不是内存, 否则进程被杀后计数清零, 「最多 3 次」就失效了 */
    private fun attempts(context: Context): Int =
        prefs(context).getInt(KEY_ATTEMPTS, 0)

    private fun setAttempts(context: Context, n: Int) {
        prefs(context).edit().putInt(KEY_ATTEMPTS, n).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "FLS_MSG"
        private const val PREFS_NAME = "svc_restart"
        private const val KEY_ATTEMPTS = "attempts"
        private const val REQUEST_CODE = 1001

        /** 拉起后多久回来确认一次 (毫秒) */
        private const val RETRY_CHECK_MS = 30_000L

        /** 最多连续尝试几次, 超过就只留 15 分钟周期任务兜底 */
        private const val MAX_ATTEMPTS = 3

        /** 复查闹钟的 PendingIntent (requestCode 1001, 与服务侧排定时是同一个) */
        private fun pending(context: Context): PendingIntent {
            val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
                action = MessageService.ACTION_RESTART_SERVICE
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * 排一次「复查」闹钟 (v1.1.2: 从 onReceive 里提出来 —— 闹钟守护心跳
         * [com.fengling.share.receiver.KeepAliveReceiver] 把服务拉起来之后, 也要排同一条复查)。
         * setExactAndAllowWhileIdle 没权限时失败, 退化成不精确的 set() (不申请 SCHEDULE_EXACT_ALARM)。
         */
        fun scheduleCheck(context: Context, delayMs: Long) {
            runCatching {
                val am = context.getSystemService(AlarmManager::class.java)
                if (am == null) {
                    Log.w(TAG, "拿不到 AlarmManager, 不再安排复查, 交给周期任务")
                    return@runCatching
                }
                val at = System.currentTimeMillis() + delayMs
                val pi = pending(context)
                val exact = runCatching {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    true
                }.getOrDefault(false)
                if (!exact) am.set(AlarmManager.RTC_WAKEUP, at, pi)
                Log.i(TAG, "已排定 " + (delayMs / 1000) + " 秒后复查 (exact=" + exact + ")")
            }.onFailure { Log.w(TAG, "排复查闹钟失败", it) }
        }
    }
}
