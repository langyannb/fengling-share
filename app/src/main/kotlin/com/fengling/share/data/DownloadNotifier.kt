package com.fengling.share.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fengling.share.MainActivity

/**
 * 下载进度通知。
 *
 * 下载中心本身没有前台服务 (任务活在 App 进程里), 所以用一条「汇总通知」告诉用户
 * 后台还在下: 几个在跑、总进度多少、点开进下载管理。
 * 全部下完/没有活动任务时自动撤掉, 不留垃圾通知。
 */
internal object DownloadNotifier {

    private const val CHANNEL = "download_progress"
    private const val NOTIFICATION_ID = 0x0D0D

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "下载进度",
                    NotificationManager.IMPORTANCE_LOW, // 静默, 不打扰
                ).apply {
                    description = "内置浏览器下载文件的进度"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
    }

    /** 按当前任务列表刷新 (没有活动任务就撤掉通知) */
    fun sync(context: Context, tasks: List<DownloadTask>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val active = tasks.filter { it.isActive }
        if (active.isEmpty()) {
            runCatching { nm.cancel(NOTIFICATION_ID) }
            return
        }
        ensureChannel(context)

        val running = active.count { it.state == DownloadState.RUNNING }
        val known = active.filter { it.totalBytes > 0L }
        // 只统计「知道总大小」的那些: 有一个不知道总大小就没法给整体百分比
        val overall = if (known.isNotEmpty() && known.size == active.size) {
            known.sumOf { it.downloadedBytes }.toFloat() / known.sumOf { it.totalBytes }.toFloat()
        } else {
            -1f
        }
        val title = if (active.size == 1) active.first().fileName else "${active.size} 个文件正在下载"
        val summary = buildString {
            append(if (running > 0) "$running 个进行中" else "等待中")
            if (overall >= 0f) append(" · ${(overall * 100).toInt()}%")
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(summary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (overall >= 0f) {
            builder.setProgress(100, (overall * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        runCatching { nm.notify(NOTIFICATION_ID, builder.build() as Notification) }
    }
}
