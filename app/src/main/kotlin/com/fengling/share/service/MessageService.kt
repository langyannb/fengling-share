package com.fengling.share.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fengling.share.MainActivity
import com.fengling.share.R
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppState
import com.fengling.share.data.MessageStream
import com.fengling.share.data.Settings
import com.fengling.share.data.StreamCursor
import com.fengling.share.data.StreamEvent
import com.fengling.share.data.UserStore
import com.fengling.share.receiver.KeepAliveReceiver
import com.fengling.share.receiver.ServiceRestartReceiver
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

    /** 同群消息合并用: 上一次弹通知的群 / 累计条数 / 时间戳 (v1.1.1) */
    private var lastGroupId: Int = 0
    private var lastGroupCount: Int = 0
    private var lastGroupAt: Long = 0L

    /** 上一次 SSE 连接是不是「没连上」——没连上就说明中间有断线窗口, 下次连之前先 HTTP 补拉 (v1.1.2) */
    private var lastConnectFailed: Boolean = false

    override fun onCreate() {
        super.onCreate()
        // 服务可能被系统在「全新进程」里重启 (START_STICKY), 这里两个 init 都是幂等的, 补一次最稳
        runCatching { Settings.init(applicationContext) }
        runCatching { UserStore.init(applicationContext) }
        // SSE 游标 (已通知过的最大 pm/group id): 断线重连靠它补齐漏掉的事件 (v1.1.2)
        runCatching { StreamCursor.init(applicationContext) }
        instance = this
        // 闹钟守护心跳 (v1.1.2): 先排上 —— 就算下面 startForeground 失败、服务当场收摊,
        // 这条心跳也会继续把「服务该不该活着」和「有没有漏消息」接管过去
        runCatching { KeepAliveReceiver.schedule(applicationContext) }
        createChannels()
        // 长期兜底: 15 分钟一次的周期看护任务 (被划掉 / 被 ROM 杀 / 重启手机后都能把服务叫回来)
        scheduleJob()
        Log.i(TAG, "服务创建: 已登录=" + UserStore.isLoggedIn())
        // start() 里 FGS 被系统拒绝过 -> 这次走「普通后台服务」降级路径 (见 start() 的说明)
        val degrade = degradeToPlainService
        degradeToPlainService = false
        // Android 14+ 的 dataSync 前台服务必须带类型; 少数机型 (后台启动受限) 会抛异常, 兜住别崩
        val started = if (degrade) {
            // 是被 startService 拉起来的, 系统没要求 startForeground; 硬调反而会抛/ANR。
            // 只发一条普通通知, 至少让用户知道「后台还能收到消息」。
            runCatching { notifyDegraded() }
            true
        } else {
            runCatching {
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
        }
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

    /**
     * 用户在「最近任务」里把 App 划掉 (v1.1.1, 核心需求 ①)
     *
     * manifest 里给服务加了 `android:stopWithTask="false"`, 所以划掉任务时**只会走这里,
     * 服务本身继续活着、SSE 长连接不断** —— 这是「被划掉也能收到消息」的主要机制。
     * 少数 ROM (ColorOS 的"深度清理"、MIUI 的"结束进程"等) 会顺手把服务一起停掉,
     * 那时 running 已是 false, 就排一次重启兜底。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "任务被移除 (App 被划掉), 服务仍在跑=" + running)
        if (running) return
        scheduleRestart(RESTART_DELAY_MS)
    }

    override fun onDestroy() {
        Log.i(TAG, "服务销毁 stoppedByUser=" + AppState.serviceStoppedByUser)
        running = false
        runCatching { scope.cancel() }
        job = null
        if (instance === this) instance = null
        super.onDestroy()
        // 不是用户主动关的 (被划掉 / 被 ROM 杀 / 被系统回收) -> 3 秒后自己回来。
        // 用户自己关的开关、或已退出登录, 绝不能拉回来 (否则就是关不掉的流氓服务)。
        if (Settings.msgServiceOn && UserStore.isLoggedIn() && !AppState.serviceStoppedByUser) {
            scheduleRestart(RESTART_DELAY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 掐断当前连接并立刻重连 (守护心跳专用): 先起新协程再取消旧的, 避免出现「一条连接都没有」的窗口 */
    private fun forceReconnectNow(reason: String) {
        Log.i(TAG, "强制重连 SSE: " + reason)
        val old = job
        lastConnectFailed = true
        job = scope.launch { runStream() }
        runCatching { old?.cancel() }
    }

    // ==================== 保活: 被划掉 / 被杀后自己回来 (v1.1.1) ====================

    /**
     * 排一次「把服务拉回来」的系统闹钟。
     *
     * 为什么必须用 AlarmManager 而不是 Handler.postDelayed: 任务被划掉后进程随时会被杀,
     * 进程内的定时器跟着一起消失, 只有系统级闹钟能跨进程 / 跨重启存活。
     * setExactAndAllowWhileIdle 在 Android 12+ 没有 SCHEDULE_EXACT_ALARM 权限时会抛
     * SecurityException (契约允许不加该权限), runCatching 兜住后退化成不精确的 set():
     * 晚几分钟醒过来, 也好过永远收不到消息。
     */
    private fun scheduleRestart(delayMs: Long) {
        runCatching {
            val am = getSystemService(AlarmManager::class.java)
            if (am == null) {
                Log.w(TAG, "拿不到 AlarmManager, 只能靠周期任务兜底")
            } else {
                val pi = restartPendingIntent()
                val at = System.currentTimeMillis() + delayMs
                val exact = runCatching {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    true
                }.getOrDefault(false)
                if (!exact) am.set(AlarmManager.RTC_WAKEUP, at, pi)
                Log.i(TAG, "已排定服务重启: " + delayMs + "ms 后 exact=" + exact)
            }
        }.onFailure { Log.w(TAG, "排定服务重启失败", it) }
        // 不管闹钟有没有排上, 都再确认一次周期任务在册 (长期兜底)
        scheduleJob()
    }

    /** 服务重启闹钟的 PendingIntent (requestCode 固定 1001, 反复排定只覆盖同一条) */
    private fun restartPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, ServiceRestartReceiver::class.java).apply {
            action = ACTION_RESTART_SERVICE
        }
        return PendingIntent.getBroadcast(
            this,
            RESTART_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 注册 15 分钟一次的 JobScheduler 周期任务 (v1.1.1)。
     *
     * 为什么要有它: 划掉任务后进程被杀时 onDestroy 根本不会执行 (闹钟也就排不上),
     * 而 setPersisted(true) 的周期任务写进了系统, 重启手机后依然有效 —— 是最后一道兜底。
     * **故意不 cancel**: 服务起来后也留着, 它会自己判断「服务在跑就什么都不做」。
     */
    private fun scheduleJob() {
        val js = runCatching { getSystemService(JobScheduler::class.java) }.getOrNull() ?: return
        runCatching {
            val component = ComponentName(this, MessageJobService::class.java)
            val info = JobInfo.Builder(JOB_ID_WATCHDOG, component)
                .setPeriodic(JOB_PERIOD_MS)
                .setPersisted(true)
                .build()
            val result = js.schedule(info)
            Log.i(TAG, "周期看护任务已注册: result=" + result)
        }.onFailure { Log.w(TAG, "注册周期看护任务失败", it) }
    }

    /** 降级运行 (没起成前台服务) 时的普通通知: 告诉用户后台仍能收消息, 但没有常驻保活 */
    private fun notifyDegraded() {
        if (!canNotify()) return
        runCatching {
            val n = NotificationCompat.Builder(this, CHANNEL_KEEPALIVE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("风铃分享库")
                .setContentText("后台接收消息已降级运行 (系统未允许前台服务, 保活变弱)")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            NotificationManagerCompat.from(this).notify(NOTIF_ID_DEGRADED, n)
        }
    }

    // ==================== SSE 主循环 ====================

    /**
     * 连接主循环: 连 -> 断开 -> 等 2 秒 -> 再连, 永不退出。
     * 未登录时只空转等待 (3 秒一轮), 不浪费连接; 登录后下一次循环自然就连上了。
     */
    private suspend fun runStream() {
        var firstConnect = true
        while (true) {
            if (!UserStore.isLoggedIn()) {
                Log.i(TAG, "未登录, 3 秒后再看")
                firstConnect = true
                delay(LOGIN_POLL_MS)
                continue
            }
            // 游标账号校对 (v1.1.2): 换了账号 / 退出重登必须归零, 否则会拿上一个账号的消息 id 当基线,
            // 把新账号的历史消息当「积压」补弹出来。每轮连接前都核对一次, 成本只是一次 SP 读。
            runCatching { StreamCursor.init(applicationContext) }
            // 兜底补拉 (v1.1.2): 服务刚起来 / 上一次压根没连上时, 先做一次轻量 HTTP 补拉, 把断线期间
            // 漏掉的通知当场补弹。正常的 25 秒收尾重连**不做** (那 2 秒窗口由 SSE 游标在服务端补齐)。
            // 基线落盘 (v1.1.2): 首次安装 / 刚升级 / 刚换账号时, 游标是 0, 此刻若进程被划掉,
            // 之后到达的消息既没有 SSE 推送、补拉也会因为「游标为 0」而跳过 -> 通知永久丢失。
            // 所以连接前先把服务端当前最大 id 记成基线 (幂等, 之后每轮都是纯本地读)。
            if (StreamCursor.lastPmId <= 0 || StreamCursor.lastGroupId <= 0) {
                runCatching { MessageCatchUp.seedIfUnset(this, "连接前建立基线") }
                    .onFailure { Log.w(TAG, "建立游标基线失败", it) }
            }
            if (firstConnect || lastConnectFailed) {
                runCatching {
                    MessageCatchUp.pull(
                        this,
                        if (firstConnect) "服务起来时兜底" else "上次没连上, 重连前兜底",
                    )
                }.onFailure { Log.w(TAG, "补拉异常", it) }
            }
            val beganAt = System.currentTimeMillis()
            lastStreamActivityAt = SystemClock.elapsedRealtime()
            Log.i(
                TAG,
                "发起 SSE 连接 (游标 pm=" + StreamCursor.lastPmId + " grp=" + StreamCursor.lastGroupId + ")",
            )
            val result = runCatching {
                ApiClient.streamMessages(
                    pmId = StreamCursor.lastPmId,
                    groupId = StreamCursor.lastGroupId,
                    onActivity = { lastStreamActivityAt = SystemClock.elapsedRealtime() },
                ) { event -> onEvent(event) }
            }.getOrElse { Result.failure<Unit>(it) }
            // 「连上了」= 服务端正常收尾 (isSuccess) 或这条连接活了够久 (中途断的)
            Log.i(
                TAG,
                "SSE 结束: success=" + result.isSuccess +
                    " 时长=" + (System.currentTimeMillis() - beganAt) + "ms" +
                    " err=" + result.exceptionOrNull(),
            )
            val connected = result.isSuccess ||
                System.currentTimeMillis() - beganAt >= MessageStream.CONNECTED_MIN_MS
            // 没连上 = 中间有一段谁也没覆盖的窗口, 下次连之前先补拉
            lastConnectFailed = !connected
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
        // 游标先走 (v1.1.2): 收到就算「已处理」, 下次重连从它之后开始推, 不会把历史消息重弹一遍。
        // 免打扰 / 自己发的消息也照样推进游标 —— 否则它们会永远卡在「比游标大」的状态里被反复补拉。
        when (event) {
            is StreamEvent.Pm -> StreamCursor.markPm(event.msgId)
            is StreamEvent.Group -> StreamCursor.markGroup(event.msgId)
            StreamEvent.Reconnected -> Unit
        }
        MessageStream.emit(event)
        Log.i(TAG, "收到事件 " + event::class.simpleName + " foreground=" + AppState.foreground)
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

    /** 两个渠道都幂等创建 (已存在时是空操作); 实现搬到了 [MessageNotifier] (v1.1.2: 补拉也要弹通知) */
    private fun createChannels() {
        MessageNotifier.ensureChannels(this)
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
        MessageNotifier.notifyPm(
            context = this,
            convId = event.convId,
            nickname = event.nickname,
            fromUser = event.fromUser,
            content = event.content,
            image = event.image,
        )
        refreshKeepAlive()
    }

    /**
     * 群消息通知 (v1.1.1 起: **群里任何人的消息都提醒**, 不再只提醒 @)
     *
     * 跳过条件 (契约 B7):
     * - `event.userId == 自己` —— 自己发的不提醒;
     * - `event.muted` —— 我把这个群设了「消息免打扰」, 一条都不弹;
     * - `event.msgType == "system"` —— 系统消息(「xxx加入了群聊」)只在群聊里看, 不弹通知 (契约 B5);
     * - `Settings.notifyGroupAll == false` —— 用户在设置页关掉了「群消息提醒」,
     *   退回老行为: 只有 @我 / @所有人 才弹。
     *
     * 通知 id 固定 `3000 + groupId`: 同一群 3 秒内连发多条时, 后一条会**覆盖**前一条,
     * 正文变成「N 条新消息」(QQ/微信的折叠效果), 但每一条都仍然响一次提示音。
     */
    private fun notifyGroup(event: StreamEvent.Group) {
        val myId = runCatching { UserStore.current?.id ?: 0 }.getOrDefault(0)
        if (myId > 0 && event.userId == myId) return
        if (event.muted) return
        // 系统消息(「xxx加入了群聊」)不弹通知 (契约 B5)
        if (event.msgType == "system") return
        val atMe = event.atMe == 1
        val atAll = event.atAll == 1
        if (!atMe && !atAll && !Settings.notifyGroupAll) return
        val prefix = when {
            atMe -> "@你 "
            atAll -> "@所有人 "
            else -> ""
        }
        val title = event.groupName.ifBlank { "群 ${event.groupId}" }
        val who = event.nickname.ifBlank { "有人" }
        val one = prefix + who + "：" + MessageNotifier.summaryOf(event.content, event.image)
        // 同一群 3 秒内的连续消息合并计数 (成员变量, 服务实例自己维护)
        val now = System.currentTimeMillis()
        val count = if (lastGroupId == event.groupId && now - lastGroupAt <= GROUP_MERGE_MS) {
            lastGroupCount + 1
        } else {
            1
        }
        lastGroupId = event.groupId
        lastGroupCount = count
        lastGroupAt = now
        val body = if (count > 1) prefix + count + " 条新消息" else one
        MessageNotifier.post(
            context = this,
            id = NOTIF_ID_GROUP_BASE + event.groupId,
            title = title,
            body = body,
            pmConvId = 0,
            groupId = event.groupId,
        )
        refreshKeepAlive()
    }

    /** Android 13+ 没给 POST_NOTIFICATIONS 时 notify 会静默失败, 先问一句免得白忙 */
    private fun canNotify(): Boolean = MessageNotifier.canNotify(this)

    companion object {

        /** 日志 TAG (只打状态变化与事件, 不打消息内容) */
        private const val TAG = "FLS_MSG"
        /** 通知 id / 渠道 / extra 的唯一真源在 [MessageNotifier] (v1.1.2: 补拉也要弹同一张通知) */
        const val NOTIF_ID_KEEPALIVE = MessageNotifier.NOTIF_ID_KEEPALIVE

        /** 降级运行时的普通通知 id (FGS 起不来时用, v1.1.1) */
        const val NOTIF_ID_DEGRADED = MessageNotifier.NOTIF_ID_DEGRADED

        /** 私聊通知 id 基址: 实际 id = 2000 + convId */
        const val NOTIF_ID_PM_BASE = MessageNotifier.NOTIF_ID_PM_BASE

        /** 群通知 id 基址: 实际 id = 3000 + groupId */
        const val NOTIF_ID_GROUP_BASE = MessageNotifier.NOTIF_ID_GROUP_BASE

        /** 常驻 (保活) 渠道 id */
        const val CHANNEL_KEEPALIVE = MessageNotifier.CHANNEL_KEEPALIVE

        /** 消息提醒渠道 id */
        const val CHANNEL_MESSAGE = MessageNotifier.CHANNEL_MESSAGE

        /** 点通知直达私聊会话的 extra 名 */
        const val EXTRA_OPEN_PM_CONV = MessageNotifier.EXTRA_OPEN_PM_CONV

        /** 点通知直达群聊的 extra 名 */
        const val EXTRA_OPEN_GROUP = MessageNotifier.EXTRA_OPEN_GROUP

        /** 未登录时的空转间隔 */
        private const val LOGIN_POLL_MS = 3000L

        /** 同一群多久之内的多条消息合并成「N 条新消息」(毫秒) */
        private const val GROUP_MERGE_MS = 3000L

        /** 服务重启闹钟的 requestCode / action (契约 C14: requestCode 1001) */
        private const val RESTART_REQUEST_CODE = 1001
        const val ACTION_RESTART_SERVICE = "com.fengling.share.RESTART_SERVICE"

        /** 被划掉 / 被杀后多久把服务拉回来 */
        private const val RESTART_DELAY_MS = 3000L

        /** JobScheduler 周期看护任务: id + 周期 (15 分钟 = 系统下限) */
        private const val JOB_ID_WATCHDOG = 1002
        private const val JOB_PERIOD_MS = 15 * 60 * 1000L

        /**
         * [start] 里前台服务被系统拒绝时置 true, [onCreate] 读到后走「普通后台服务」降级路径。
         * 用静态标志而不是 Intent extra: Service.onCreate 拿不到那次的 Intent。
         */
        @Volatile
        private var degradeToPlainService = false

        /** 服务当前是否在跑 (设置页显示「运行中 / 未运行」用) */
        @Volatile
        var running: Boolean = false
            private set

        /** 静态可读的「服务在跑吗」 */
        val isRunning: Boolean get() = running

        /** 当前活着的服务实例 (守护心跳要调它强制重连); onDestroy 里清掉 */
        @Volatile
        private var instance: MessageService? = null

        /** 最近一次收到任何 SSE 数据 (含 10 秒一次的心跳行) 的时刻, elapsedRealtime */
        @Volatile
        private var lastStreamActivityAt: Long = 0L

        /** SSE 是不是卡死了 / 被冻结了: 超过 timeoutMs 一点数据都没有 (v1.1.2 守护心跳用) */
        fun isStreamStale(timeoutMs: Long): Boolean {
            if (!running) return false
            val last = lastStreamActivityAt
            if (last <= 0L) return false
            return SystemClock.elapsedRealtime() - last > timeoutMs
        }

        /**
         * 强制掐断当前 SSE 并立刻重连 (守护心跳判断连接卡死时调)。
         * 必须重新 launch: job.cancel() 之后那条协程就退出了, 不补一条连接就永远断了。
         */
        fun requestReconnect(reason: String) {
            val s = instance
            if (s == null || !running) {
                Log.i(TAG, "重连请求被忽略 (服务不在跑): " + reason)
                return
            }
            s.forceReconnectNow(reason)
        }

        /**
         * 启动服务 (幂等, 已在跑就什么都不做)。
         * 未登录不启动 —— 没 token 连上去也会被服务端踢掉, 白挂一个常驻通知。
         */
        fun start(context: Context) {
            if (!UserStore.isLoggedIn()) return
            // 这一次是「要它跑」, 允许后续被划掉 / 被杀后自动重启
            AppState.serviceStoppedByUser = false
            val intent = Intent(context, MessageService::class.java)
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }.isSuccess
            if (!ok) {
                // Android 12+ 后台不允许启动前台服务 (ForegroundServiceStartNotAllowedException)。
                // 退化成普通后台服务: 通知栏没有常驻通知、保活能力弱, 但只要进程活着 SSE 就不断,
                // 总比直接放弃强 (契约 C 允许的兜底, 会在报告里单独说明)。
                degradeToPlainService = true
                runCatching { context.startService(intent) }
                    .onFailure { Log.w(TAG, "降级启动普通服务也失败", it) }
            }
        }

        /**
         * 停止服务 (没在跑也是安全空操作)。
         *
         * ⚠️ 必须先置 [AppState.serviceStoppedByUser] = true 再 stopService:
         * stopService 会触发 onDestroy, 那里看到 stoppedByUser 才会放弃自动重启。
         */
        fun stop(context: Context) {
            AppState.serviceStoppedByUser = true
            runCatching { context.stopService(Intent(context, MessageService::class.java)) }
        }
    }
}
