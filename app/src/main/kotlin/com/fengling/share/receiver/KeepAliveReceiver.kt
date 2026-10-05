package com.fengling.share.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.fengling.share.data.AppState
import com.fengling.share.data.Settings
import com.fengling.share.data.StreamCursor
import com.fengling.share.data.UserStore
import com.fengling.share.service.MessageCatchUp
import com.fengling.share.service.MessageService
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * KeepAliveReceiver - 「闹钟守护」心跳 (v1.1.2, 缺口 1 的关键新增)
 *
 * 上一轮真机结论: ColorOS 里把 App「锁定」后划掉 -> 3 秒内服务重建, 通知正常;
 * **未锁定**时 ROM 直接拒绝重启服务 (`OplusAppStartupManager: prevent restart service, scenePriority=-1`),
 * 只有 15 分钟的 JobScheduler 兜底, 等于收不到消息。
 *
 * 所以这里用一条自续期的一次性精确闹钟 (约 120 秒) 把「服务该不该活着」这件事定期重新摆正:
 * - 服务进程被杀 -> 走既有 [ServiceRestartReceiver] 的拉起逻辑 (+30 秒复查);
 * - 服务活着但 SSE 卡死 / 被冻结 -> `MessageService.requestReconnect` 掐断重连 (重连带游标, 服务端会补推积压);
 * - **无论哪种情况**都做一次轻量 HTTP 补拉 [MessageCatchUp.pull], 漏掉的通知当场补弹 ——
 *   就算 ROM 死活不让服务起来, 用户也还能收到通知 (只是延迟到下一次心跳)。
 *
 * 克制原则 (和需求一致):
 * - 只在「已登录 + 消息服务开关打开 + 用户没主动关掉服务 + App 不在前台」时干活;
 * - `setExactAndAllowWhileIdle` 失败就 `runCatching` 退化成 `setAndAllowWhileIdle`, **不申请** SCHEDULE_EXACT_ALARM;
 * - doze 下系统会把间隔放宽到约 9 分钟, 这是可接受的 (不做「精确到秒」的挣扎)。
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEEPALIVE_TICK) return
        val app = context.applicationContext
        // goAsync: 网络补拉要几秒, 不能卡在 onReceive 里
        val pending = goAsync()
        thread(name = "fls-keepalive-tick") {
            try {
                tick(app)
            } catch (t: Throwable) {
                Log.w(TAG, "守护心跳异常", t)
            } finally {
                // 不管这次成没成, 先把下一次排上 —— 这条链子断一次就再也醒不过来了
                schedule(app)
                runCatching { pending.finish() }
            }
        }
    }

    /** 一次心跳的全部动作 (跑在独立线程里) */
    private fun tick(app: Context) {
        Settings.init(app)
        UserStore.init(app)
        StreamCursor.init(app)

        // 三重闸门: 和 MessageJobService / ServiceRestartReceiver 一致
        val shouldRun = runCatching {
            UserStore.isLoggedIn() && Settings.msgServiceOn && !AppState.serviceStoppedByUser
        }.getOrDefault(false)
        if (!shouldRun) {
            Log.i(TAG, "守护心跳: 不该跑 (未登录 / 开关关 / 用户已停), 只续期")
            return
        }
        if (AppState.foreground) {
            Log.i(TAG, "守护心跳: App 在前台 (SSE 连着), 只续期")
            return
        }

        val running = MessageService.isRunning
        if (!running) {
            Log.i(TAG, "守护心跳: 服务不在跑 -> 拉起常驻服务")
            MessageService.start(app)
            // 拉起后 30 秒复查一次 (MessageService 自己的 onDestroy 也会排, 这里双保险)
            ServiceRestartReceiver.scheduleCheck(app, RETRY_CHECK_MS)
        } else if (MessageService.isStreamStale(STALE_MS)) {
            Log.i(TAG, "守护心跳: 服务在跑但 SSE 卡死 -> 强制重连")
            MessageService.requestReconnect("守护心跳: 连接超过 ${STALE_MS / 1000}s 没有任何数据")
        } else {
            Log.i(TAG, "守护心跳: 服务健康 (SSE 有数据)")
        }

        // 无论哪种情况都补一次 —— 服务的死活和「通知有没有漏」是两件事
        val n = runCatching {
            runBlocking { withTimeoutOrNull(CATCHUP_TIMEOUT_MS) { MessageCatchUp.pull(app, "守护心跳") } }
        }.getOrNull()
        Log.i(TAG, "守护心跳: 补拉结果=" + (n ?: -99) + " (正数=补弹条数, 0=无遗漏, 负数=拉取失败)")
    }

    companion object {

        private const val TAG = "FLS_MSG"

        /** 心跳广播: 显式组件广播, receiver 不导出, 只有系统 (alarm) 能送到 */
        const val ACTION_KEEPALIVE_TICK = "com.fengling.share.KEEPALIVE_TICK"

        private const val REQUEST_CODE = 1004

        /** 心跳间隔: 约 120 秒 (doze 下系统会放宽到约 9 分钟, 认了) */
        const val TICK_MS = 120_000L

        /** 超过这个时间没有任何 SSE 数据 (含 10 秒一次的心跳行) 就算连接卡死 */
        const val STALE_MS = 180_000L

        /** 补拉最多等这么久, 免得把广播的 pending result 拖超时 */
        private const val CATCHUP_TIMEOUT_MS = 20_000L

        private const val RETRY_CHECK_MS = 30_000L

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, KeepAliveReceiver::class.java).apply {
                action = ACTION_KEEPALIVE_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * 排下一次心跳。幂等 (同一个 PendingIntent, FLAG_UPDATE_CURRENT), 每次都会把时间往后推。
         * 调用点: MessageService.onCreate (服务起来就先排上) + 每次心跳结束时自续期。
         */
        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val at = SystemClock.elapsedRealtime() + TICK_MS
            val ok = runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pendingIntent(context))
            }.isSuccess
            if (!ok) {
                // 没给 SCHEDULE_EXACT_ALARM 硬权限 / 系统不给精确闹钟: 退化成不精确的, 一样能醒
                runCatching {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pendingIntent(context))
                }.onFailure { Log.w(TAG, "守护心跳: 排闹钟失败", it) }
            }
            Log.i(TAG, "守护心跳: 下一次已排 (${TICK_MS / 1000}s 后, exact=" + ok + ")")
        }
    }
}
