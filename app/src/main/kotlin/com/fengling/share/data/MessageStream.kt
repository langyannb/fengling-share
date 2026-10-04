package com.fengling.share.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

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
 * 实时消息流 (SSE 长连接)
 *
 * 设计要点:
 * - 全局单例: 整个 App 只维持**一条**连接, 由 MainScreen 按前后台启停, 各页面只订阅不重连。
 * - 对外只暴露只读的 [events], 页面拿不到也改不了内部的 MutableSharedFlow。
 * - 自动重连: 每次断开 (含服务端 25 秒正常收尾) 都等 [RECONNECT_DELAY_MS] 再连, 失败也继续,
 *   异常一律内部吞掉, 绝不抛给 UI —— 轮询兜底还在, 流挂了顶多回到「慢一点」。
 */
object MessageStream {

    /** 断线重连间隔 (毫秒): 与服务端 `retry: 2000` 一致 */
    private const val RECONNECT_DELAY_MS = 2000L

    /**
     * 一次连接至少活过这么久才算「真的连上了」。
     * 服务端认得 token 时连接会一直挂着 (最长 25 秒), 用它把「刚建连就失败」区分出来。
     */
    private const val CONNECTED_MIN_MS = 3000L

    private val _events = MutableSharedFlow<StreamEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 页面订阅这个 (只读) */
    val events: SharedFlow<StreamEvent> = _events.asSharedFlow()

    /** 连接协程跑在自己的 scope 上, 和任何页面的生命周期都无关 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var job: Job? = null

    /** 启动流 (幂等); 由 MainScreen 在「前台 + 已登录」时调用 */
    fun start(context: Context) {
        // context 只做「调用方确实有 Android 环境」的标记, 内部不持有它 (绝不持有 Activity);
        // 未登录时直接不连, 登录后 MainScreen 会再调一次 (key 带了 hasToken)
        if (!UserStore.isLoggedIn()) return
        if (job?.isActive == true) return
        job = scope.launch { runForever() }
    }

    /** 停止流: 取消连接协程 (内部会顺手 cancel 掉 OkHttp Call, 不留悬挂连接) */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** 流是否在跑 */
    val running: Boolean get() = job?.isActive == true

    /**
     * 连接主循环: 连 -> 断开 -> 等 2 秒 -> 再连, 永不退出。
     * 未登录时只空转等待, 不浪费连接 (登录后下一次循环自然就连上了)。
     */
    private suspend fun runForever() {
        var firstConnect = true
        while (true) {
            if (!UserStore.isLoggedIn()) {
                firstConnect = true
                delay(RECONNECT_DELAY_MS)
                continue
            }
            val beganAt = System.currentTimeMillis()
            val result = runCatching {
                ApiClient.streamMessages { event -> _events.tryEmit(event) }
            }.getOrElse { Result.failure<Unit>(it) }
            // 「连上了」= 服务端正常收尾 (isSuccess) 或这条连接活了够久 (中途断的)
            val connected = result.isSuccess ||
                System.currentTimeMillis() - beganAt >= CONNECTED_MIN_MS
            // 第一次连接不算「重连」, 不广播 —— 免得一进 App 所有页面就各刷一次
            if (!firstConnect && connected) _events.tryEmit(StreamEvent.Reconnected)
            firstConnect = false
            delay(RECONNECT_DELAY_MS)
        }
    }
}
