package com.fengling.share.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 实时消息事件 (SSE 契约 A 章节)
 *
 * 只有 [Reconnected] 是客户端内部事件, 其余都是服务端推过来的原始消息。
 */
sealed interface StreamEvent {

    /** 私聊消息 (只会是「别人发给我的」) */
    data class Pm(
        val msgId: Int,
        val convId: Int,
        val fromUser: Int,
        val nickname: String,
        val content: String,
        val image: String,
        val createdAt: String,
    ) : StreamEvent

    /** 群消息 (只会是「我以外的人发的、未撤回的」) */
    data class Group(
        val msgId: Int,
        val groupId: Int,
        val groupName: String,
        val userId: Int,
        val nickname: String,
        val content: String,
        val image: String,
        /** 1 = 这条消息 @ 了我 */
        val atMe: Int,
        /** 1 = 这条消息 @ 了所有人 */
        val atAll: Int,
        /** 收件人 (我) 是否对该群开了「消息免打扰」; true = 这条不要弹通知 */
        val muted: Boolean = false,
        /** 消息类型: "system" = 系统消息(加入了群聊等), 一律不弹通知 (契约 A3) */
        val msgType: String = "",
        val createdAt: String,
    ) : StreamEvent

    /**
     * 断线重连成功 (客户端内部事件)
     *
     * 服务端单条连接最长 25 秒就会 `event: bye` 收尾, 客户端立刻重连。
     * 这个「断开 -> 重连」的窗口里到达的消息不会被推, 所以重连成功后广播一次,
     * 让各页面补一次全量刷新, 把断线期间漏掉的消息补回来。
     */
    object Reconnected : StreamEvent
}

/**
 * 实时消息事件总线 (SSE 长连接)
 *
 * 设计要点 (v1.0.35 起):
 * - **连接的持有者搬到了 [com.fengling.share.service.MessageService]**(常驻前台服务),
 *   这里只剩下「事件总线」这一个职责: 服务 [emit] 事件, 各页面订阅 [events]。
 *   这样退到后台连接也不断, 才能像 QQ/微信一样秒收。
 * - 全局单例: 整个 App 只维持**一条**连接, 各页面只订阅不自己重连。
 * - 对外只暴露只读的 [events], 页面拿不到也改不了内部的 MutableSharedFlow。
 * - 缓冲区满了丢最旧的 (DROP_OLDEST): 保证 UI 一定拿到「最新的」消息, 不会被老消息堵住。
 */
object MessageStream {

    /** 断线重连间隔 (毫秒): 与服务端 `retry: 2000` 一致 (MessageService 主循环用) */
    const val RECONNECT_DELAY_MS = 2000L

    /**
     * 一次连接至少活过这么久才算「真的连上了」。
     * 服务端认得 token 时连接会一直挂着 (最长 25 秒), 用它把「刚建连就失败」区分出来。
     */
    const val CONNECTED_MIN_MS = 3000L

    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 页面订阅这个 (只读) */
    val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    /**
     * 投递一个事件 (只有 MessageService 调)。
     *
     * 用 tryEmit (非挂起): 服务在 IO 线程回调里调它, 不能阻塞读流;
     * 缓冲区满会丢最旧的, 不会卡住 SSE 解析。
     */
    fun emit(event: StreamEvent) {
        _events.tryEmit(event)
    }
}
