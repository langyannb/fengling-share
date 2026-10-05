package com.fengling.share.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fengling.share.MainActivity
import com.fengling.share.R

/**
 * MessageNotifier - 消息通知的唯一出口 (v1.1.2)
 *
 * 从 [MessageService] 里搬出来, 因为现在有**两个**地方要弹消息通知:
 * 1) 常驻服务收到 SSE 事件时 (前台实时);
 * 2) 闹钟守护任务 [com.fengling.share.receiver.KeepAliveReceiver] 的「兜底补拉」发现漏掉的消息时。
 *
 * 两处必须用**完全一样**的渠道、id 规则和正文格式, 否则同一条消息会被弹成两条通知。
 * 所以这里做成 object 单例 + 静态常量, MessageService 的常量直接引用本类的常量。
 */
object MessageNotifier {

    private const val TAG = "FLS_MSG"

    /** 常驻通知 id (固定, 与「降级通知」区分) */
    const val NOTIF_ID_KEEPALIVE = 1000

    /** 降级运行时的普通通知 id (FGS 起不来时用, v1.1.1) */
    const val NOTIF_ID_DEGRADED = 1003

    /** 私聊通知 id 基数: 每个会话一条, id = 2000 + convId */
    const val NOTIF_ID_PM_BASE = 2000

    /** 群通知 id 基数: 每个群一条, id = 3000 + groupId */
    const val NOTIF_ID_GROUP_BASE = 3000

    /** 常驻通知渠道 (无声无震动) */
    const val CHANNEL_KEEPALIVE = "msg_keepalive"

    /** 新消息提醒渠道 (默认铃声 + 震动) */
    const val CHANNEL_MESSAGE = "msg_message"

    /** 点通知直达会话的 extra key */
    const val EXTRA_OPEN_PM_CONV = "open_pm_conv"

    /** 点通知直达群的 extra key */
    const val EXTRA_OPEN_GROUP = "open_group"

    /** 正文摘要最大长度 */
    private const val SUMMARY_MAX = 60

    /** 两个渠道都幂等创建 (已存在时是空操作) */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
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

    /** Android 13+ 没给 POST_NOTIFICATIONS 时 notify 会静默失败, 先问一句免得白忙 */
    fun canNotify(context: Context): Boolean =
        runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
            .getOrDefault(false)

    /**
     * 正文摘要: 文字取前 60 字 (截断了补省略号), 没有文字就是「[图片]」
     * (v1.1.2 起图片消息正文固定这个格式); Wave 2 起视频消息是「[视频]」。
     */
    fun summaryOf(content: String, image: String, video: String = ""): String {
        val text = content.trim()
        if (text.isEmpty()) {
            return when {
                video.isNotBlank() -> "[视频]"
                image.isNotBlank() -> "[图片]"
                else -> "新消息"
            }
        }
        return if (text.length > SUMMARY_MAX) text.take(SUMMARY_MAX) + "…" else text
    }

    /**
     * 私聊通知: 每个会话一条, id = 2000 + convId, 同一会话新消息覆盖旧的那条 (但每条都响)
     *
     * @param entityId 会话 id (convId), 只用于拼通知 id
     * @param fromUser 对方用户 id, 昵称为空时兜底显示「用户 N」
     */
    fun notifyPm(
        context: Context,
        convId: Int,
        nickname: String,
        fromUser: Int,
        content: String,
        image: String,
        video: String = "",
    ) {
        post(
            context = context,
            id = NOTIF_ID_PM_BASE + convId,
            title = nickname.ifBlank { "用户 $fromUser" },
            body = summaryOf(content, image, video),
            pmConvId = convId,
            groupId = 0,
        )
    }

    /** 群通知: 每个群一条, id = 3000 + groupId, 正文「[@你 ][昵称：]内容」 */
    fun notifyGroup(
        context: Context,
        groupId: Int,
        groupName: String,
        nickname: String,
        content: String,
        image: String,
        video: String = "",
        atMe: Boolean,
        atAll: Boolean,
    ) {
        val prefix = when {
            atMe -> "@你 "
            atAll -> "@所有人 "
            else -> ""
        }
        val who = nickname.ifBlank { "有人" }
        post(
            context = context,
            id = NOTIF_ID_GROUP_BASE + groupId,
            title = groupName.ifBlank { "群 $groupId" },
            body = prefix + who + "：" + summaryOf(content, image, video),
            pmConvId = 0,
            groupId = groupId,
        )
    }

    /** 真正发通知: 权限没了 / 被系统限流都只是静默失败, 绝不让服务挂掉 */
    fun post(
        context: Context,
        id: Int,
        title: String,
        body: String,
        pmConvId: Int = 0,
        groupId: Int = 0,
    ) {
        val allowed = canNotify(context)
        Log.i(TAG, "弹通知 id=" + id + " canNotify=" + allowed + " title=" + title)
        if (!allowed) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            // 点通知直达对应会话: MainActivity 读到 extra 后交给 PendingNav -> MainScreen 导航
            if (pmConvId > 0) putExtra(EXTRA_OPEN_PM_CONV, pmConvId)
            if (groupId > 0) putExtra(EXTRA_OPEN_GROUP, groupId)
        }
        val pi = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGE)
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
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
            .onFailure { Log.w(TAG, "notify 失败 id=" + id, it) }
    }
}
