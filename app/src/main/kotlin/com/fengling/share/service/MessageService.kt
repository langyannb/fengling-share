package com.fengling.share.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fengling.share.MainActivity
import com.fengling.share.R
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppState
import com.fengling.share.data.MessageStream
import com.fengling.share.data.Settings
import com.fengling.share.data.StreamEvent
import com.fengling.share.data.UserStore
import com.fengling.share.ui.main.my.MessageBadge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 后台常驻消息服务 (v1.0.35) —— SSE 长连接的**唯一持有者**
 *
 * 为什么要有它: 之前那条 SSE 连接由 MainScreen 按前后台启停, 退到桌面就断,
 * 于是「后台秒收消息」做不到。现在改成前台服务持有连接, 退到任何地方连接都不断,
 * 新消息到达时由服务弹系统通知 (QQ/微信同款 0 元方案: 一条常驻连接 + 一条常驻通知)。
 *
 * 职责边界:
 * - 连接: 复用 [ApiClient.streamMessages] (**绝不自己写第二套 SSE 解析**), 断了等 2 秒重连, 永不退出;
 * - 分发: 所有事件**先无条件转发给 [MessageStream] 事件总线**(各页面的订阅逻辑照旧), 再决定要不要弹通知;
 * - 通知: 只在 App 不在前台 ([AppState.foreground] == false) 时弹, 前台不弹 (前台由各页面自己响提示音);
 *   没有 POST_NOTIFICATIONS 权限时静默失败, 服务照跑不误。
 */
class MessageService : Service() {

    /** 服务自己的协程作用域: 和任何 Activity/页面生命周期都无关 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        // 服务可能被系统在「全新进程」里重启 (START_STICKY), 这里两个 init 都是幂等的, 补一次最稳
        runCatching { Settings.init(applicationContext) }
        runCatching { UserStore.init(applicationContext) }
        createChannels()
        // Android 14+ 的 dataSync 前台服务必须带类型; 少数机型 (后台启动受限) 会抛异常, 兜住别崩
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID_KEEPALIVE,
                    buildKeepAliveNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIF_ID_KEEPALIVE, buildKeepAliveNotification())
            }
        }.isSuccess
        if (!started) {
            // 起不了前台服务就别硬撑: 直接收摊, 免得被系统 ANR/杀进程
            running = false
            stopSelf()
            return
        }
        running = true
        job = scope.launch { runStream() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被系统重启 / 重复 startService 时, 保证连接协程还活着
        if (job?.isActive != true) job = scope.launch { runStream() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { scope.cancel() }
        job = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ==================== SSE 主循环 ====================

    /**
     * 连接主循环: 连 -> 断开 -> 等 2 秒 -> 再连, 永不退出。
     * 未登录时只空转等待 (3 秒一轮), 不浪费连接; 登录后下一次循环自然就连上了。
     */
    private suspend fun runStream() {
        var firstConnect = true
        while (true) {
            if (!UserStore.isLoggedIn()) {
                firstConnect = true
                delay(LOGIN_POLL_MS)
                continue
            }
            val beganAt = System.currentTimeMillis()
            val result = runCatching {
                ApiClient.streamMessages { event -> onEvent(event) }
            }.getOrElse { Result.failure<Unit>(it) }
            // 「连上了」= 服务端正常收尾 (isSuccess) 或这条连接活了够久 (中途断的)
            val connected = result.isSuccess ||
                System.currentTimeMillis() - beganAt >= MessageStream.CONNECTED_MIN_MS
            // 第一次连接不算「重连」, 不广播 —— 免得一进 App 所有页面就各刷一次
            if (!firstConnect && connected) MessageStream.emit(StreamEvent.Reconnected)
            firstConnect = false
            delay(MessageStream.RECONNECT_DELAY_MS)
        }
    }

    /**
     * 收到一个实时事件。
     *
     * ⚠️ 顺序不能反: **先转发事件总线**(前台页面靠它即时刷新 + 响提示音),
     * 再判断要不要弹系统通知。任何异常都不许影响连接 (全 runCatching)。
     */
    private fun onEvent(event: StreamEvent) {
        MessageStream.emit(event)
        // App 在前台: 不弹通知 (前台由 MainScreen 的订阅逻辑响提示音), 前后台不重复打扰
        if (AppState.foreground) return
        runCatching {
            when (event) {
                is StreamEvent.Pm -> notifyPm(event)
                is StreamEvent.Group -> notifyGroup(event)
                StreamEvent.Reconnected -> Unit
            }
        }
    }

    // ==================== 通知 ====================

    /** 两个渠道都幂等创建 (已存在时是空操作) */
    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_KEEPALIVE,
                    "后台消息接收",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "保持与服务器的实时消息连接 (状态栏常驻)"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MESSAGE,
                    "新消息提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "私聊消息 / 群里 @我 @所有人"
                    setShowBadge(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 120, 200)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        null,
                    )
                },
            )
        }
    }

    /** 常驻通知 (id 固定 1000): 标题「风铃分享库」正文「正在接收消息 · 未读 N」 */
    private fun buildKeepAliveNotification(): Notification {
        val unread = runCatching { MessageBadge.unread }.getOrDefault(0)
        val text = if (unread > 0) "正在接收消息 · 未读 $unread" else "正在接收消息"
        val pi = PendingIntent.getActivity(
            this,
            NOTIF_ID_KEEPALIVE,
            // 点常驻通知: 只回 App, 不带跳转参数
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("风铃分享库")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    /** 刷新常驻通知 (未读数变了时调用; 拿不到通知权限就静默跳过) */
    private fun refreshKeepAlive() {
        if (!canNotify()) return
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NOTIF_ID_KEEPALIVE, buildKeepAliveNotification())
        }
    }

    /** 私聊: 每个会话一条, id = 2000 + convId, 同一会话新消息覆盖旧的那条 (但每条都响) */
    private fun notifyPm(event: StreamEvent.Pm) {
        val title = event.nickname.ifBlank { "用户 ${event.fromUser}" }
        val body = summaryOf(event.content, event.image)
        post(
            id = NOTIF_ID_PM_BASE + event.convId,
            title = title,
            body = body,
            pmConvId = event.convId,
            groupId = 0,
        )
        refreshKeepAlive()
    }

    /** 群消息: 只有 @我 / @所有人 才弹, id = 3000 + groupId */
    private fun notifyGroup(event: StreamEvent.Group) {
        val atMe = event.atMe == 1
        val atAll = event.atAll == 1
        if (!atMe && !atAll) return
        val prefix = if (atMe) "@你 " else "@所有人 "
        val title = event.groupName.ifBlank { "群 ${event.groupId}" }
        val who = event.nickname.ifBlank { "有人" }
        val body = prefix + who + "：" + summaryOf(event.content, event.image)
        post(
            id = NOTIF_ID_GROUP_BASE + event.groupId,
            title = title,
            body = body,
            pmConvId = 0,
            groupId = event.groupId,
        )
        refreshKeepAlive()
    }

    /** 正文摘要: 文字取前 60 字 (截断了补省略号), 没有文字就是「[图片]」 */
    private fun summaryOf(content: String, image: String): String {
        val text = content.trim()
        if (text.isEmpty()) return if (image.isNotBlank()) "[图片]" else "新消息"
        return if (text.length > SUMMARY_MAX) text.take(SUMMARY_MAX) + "…" else text
    }

    /** 真正发通知: 权限没了 / 被系统限流都只是静默失败, 绝不让服务挂掉 */
    private fun post(id: Int, title: String, body: String, pmConvId: Int, groupId: Int) {
        if (!canNotify()) return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            // 点通知直达对应会话: MainActivity 读到 extra 后交给 PendingNav -> MainScreen 导航
            if (pmConvId > 0) putExtra(EXTRA_OPEN_PM_CONV, pmConvId)
            if (groupId > 0) putExtra(EXTRA_OPEN_GROUP, groupId)
        }
        val pi = PendingIntent.getActivity(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 200, 120, 200))
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    /** Android 13+ 没给 POST_NOTIFICATIONS 时 notify 会静默失败, 先问一句免得白忙 */
    private fun canNotify(): Boolean =
        runCatching { NotificationManagerCompat.from(this).areNotificationsEnabled() }
            .getOrDefault(false)

    companion object {
        /** 常驻通知 id (固定) */
        const val NOTIF_ID_KEEPALIVE = 1000

        /** 私聊通知 id 基址: 实际 id = 2000 + convId */
        const val NOTIF_ID_PM_BASE = 2000

        /** 群通知 id 基址: 实际 id = 3000 + groupId */
        const val NOTIF_ID_GROUP_BASE = 3000

        /** 常驻 (保活) 渠道 id */
        const val CHANNEL_KEEPALIVE = "msg_keepalive"

        /** 消息提醒渠道 id */
        const val CHANNEL_MESSAGE = "msg_message"

        /** 点通知直达私聊会话的 extra 名 */
        const val EXTRA_OPEN_PM_CONV = "open_pm_conv"

        /** 点通知直达群聊的 extra 名 */
        const val EXTRA_OPEN_GROUP = "open_group"

        /** 未登录时的空转间隔 */
        private const val LOGIN_POLL_MS = 3000L

        /** 通知正文最多取多少字 */
        private const val SUMMARY_MAX = 60

        /** 服务当前是否在跑 (设置页显示「运行中 / 未运行」用) */
        @Volatile
        var running: Boolean = false
            private set

        /** 静态可读的「服务在跑吗」 */
        val isRunning: Boolean get() = running

        /**
         * 启动服务 (幂等, 已在跑就什么都不做)。
         * 未登录不启动 —— 没 token 连上去也会被服务端踢掉, 白挂一个常驻通知。
         */
        fun start(context: Context) {
            if (!UserStore.isLoggedIn()) return
            val intent = Intent(context, MessageService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** 停止服务 (没在跑也是安全空操作) */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MessageService::class.java)) }
        }
    }
}
