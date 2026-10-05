package com.fengling.share.data

/**
 * 进程级 App 前后台状态
 *
 * 由 [com.fengling.share.MainActivity] 的 `onResume()` / `onPause()` 维护。
 * 后台常驻服务 ([com.fengling.share.service.MessageService]) 靠它区分「要不要弹系统通知」:
 * - 前台 = true: 消息只走 [MessageStream] 事件总线 + 提示音 (页面自己订阅), 不弹通知栏;
 * - 后台 = false: 才弹系统通知, 做到「退到桌面也能秒收」。
 *
 * 只有一个 volatile 布尔量: 服务在 IO 线程读, 主线程写, 不需要锁。
 */
object AppState {

    /** App 是否在前台 (onResume ~ onPause) */
    @Volatile
    var foreground: Boolean = false

    /**
     * 「后台接收消息」是不是用户主动关掉的 (v1.1.1)
     *
     * 常驻服务被划掉 / 被 ROM 杀掉后要自动重启, 但**用户自己在设置页关掉开关、或退出登录**
     * 之后绝对不能再把它拉回来 —— 那会变成「关不掉的流氓服务」。
     * [com.fengling.share.service.MessageService.stop] 置 true, `start` 置 false,
     * 重启链路 ([com.fengling.share.receiver.ServiceRestartReceiver] /
     * [com.fengling.share.service.MessageJobService]) 每次都要先看它。
     */
    @Volatile
    var serviceStoppedByUser: Boolean = false
}
