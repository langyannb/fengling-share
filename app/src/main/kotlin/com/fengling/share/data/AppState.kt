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
}
