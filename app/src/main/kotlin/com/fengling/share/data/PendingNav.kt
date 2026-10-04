package com.fengling.share.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一次的跳转请求 (点通知打开对应会话)
 *
 * @param pmConvId 私聊会话 id (0 = 不跳私聊)
 * @param groupId  群 id (0 = 不跳群聊)
 */
data class NavTarget(
    val pmConvId: Int = 0,
    val groupId: Int = 0,
) {
    /** 是不是一个有效请求 (两个 id 都为 0 就当没这回事) */
    val valid: Boolean get() = pmConvId > 0 || groupId > 0
}

/**
 * 通知点击的跳转请求 (进程内共享)
 *
 * 链路: 系统通知 `PendingIntent` 带 extra `open_pm_conv` / `open_group` 拉起 [MainActivity]
 * -> `onCreate` / `onNewIntent` 解析后写进这里 -> `MainScreen` 里收集并真正导航 -> 导航完置 null。
 *
 * ⚠️ 为什么要走这个中转: 通知把 Activity 拉起来时 Compose 的 NavHost 可能还没建好
 * (冷启动 onCreate 阶段), 直接在那里 navigate 会丢; 统一交给 MainScreen 收集最稳。
 */
object PendingNav {

    private val _target = MutableStateFlow<NavTarget?>(null)

    /** MainScreen 订阅这个 */
    val target: StateFlow<NavTarget?> = _target.asStateFlow()

    /** Activity 解析到通知 extra 时调用 */
    fun request(pmConvId: Int, groupId: Int) {
        val t = NavTarget(pmConvId = pmConvId, groupId = groupId)
        if (t.valid) _target.value = t
    }

    /** MainScreen 跳完 (或判定跳不动) 时调用 */
    fun consume() {
        _target.value = null
    }
}
