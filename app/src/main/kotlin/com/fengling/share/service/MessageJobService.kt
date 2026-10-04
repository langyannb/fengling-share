package com.fengling.share.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.fengling.share.data.AppState
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore

/**
 * MessageJobService - 常驻消息服务的「周期看护」(v1.1.1, 核心需求 ① 的最后一道兜底)
 *
 * 由 [MessageService] 在启动时注册 (15 分钟一次, `setPersisted(true)` —— 重启手机后依然有效),
 * 每次触发只做一件事: 该跑而没在跑, 就把它拉起来。
 *
 * 为什么非要有它不可: App 被从最近任务里划掉、或被 ROM 深度清理时, 进程是**直接被杀**的,
 * `onDestroy` 根本不会执行 —— 也就没机会排 AlarmManager 闹钟。只有写进系统的持久化周期任务
 * 能在几百毫秒的随机窗口里把新进程叫起来。
 *
 * 用户主动关掉开关 / 退出登录后一律不拉起 (三重闸门与 [com.fengling.share.receiver.ServiceRestartReceiver] 一致)。
 * 必须在 manifest 里声明 `android:permission="android.permission.BIND_JOB_SERVICE"`。
 */
class MessageJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        runCatching {
            // JobService 可能在「没有 Activity 的全新进程」里跑, Settings / UserStore 必须先 init
            Settings.init(applicationContext)
            UserStore.init(applicationContext)
            val shouldRun = UserStore.isLoggedIn() &&
                Settings.msgServiceOn &&
                !AppState.serviceStoppedByUser
            val running = MessageService.isRunning
            Log.i(TAG, "周期看护任务触发: 该跑=" + shouldRun + " 服务在跑=" + running)
            if (shouldRun && !running) MessageService.start(applicationContext)
        }.onFailure { Log.w(TAG, "周期看护任务出错", it) }
        // 同步就做完了, 不需要系统再回调 onStopJob
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        /** 与 MessageService 用同一个日志 tag, 核对时看一条日志就够 */
        private const val TAG = "FLS_MSG"
    }
}
