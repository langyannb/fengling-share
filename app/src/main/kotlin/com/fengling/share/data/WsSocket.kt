package com.fengling.share.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** 服务端 ack 的解析结果。 */
data class WsAck(
    val clientMsgId: String,
    val id: Long,
    val convId: Int,
    val groupId: Int,
    val createdAt: String,
)

/**
 * 当前 WebSocket 的发送侧。
 *
 * 收消息仍由 [ApiClient.streamMessagesWs] + [MessageService] 负责；这里只保存
 * 已连接的 socket，供页面发送文本并等待 ack。
 *
 * 新 Go 服务的 hello 带 `features:["send_ack",...]`；旧 goapi 的 hello 没有，
 * 所以 [supportsSendAck] 为 false 时页面继续走原来的 HTTP 发送，灰度切换不会炸。
 */
object WsSocket {

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    var supportsSendAck: Boolean = false
        private set

    private val pending = ConcurrentHashMap<String, CompletableDeferred<WsAck>>()

    fun attach(ws: WebSocket) {
        socket = ws
    }

    fun detach(ws: WebSocket) {
        if (socket === ws) {
            socket = null
            supportsSendAck = false
            pending.values.forEach { it.cancel() }
            pending.clear()
        }
    }

    fun setFeatures(features: List<String>) {
        supportsSendAck = features.contains("send_ack")
    }

    val isConnected: Boolean get() = socket != null

    fun onAck(data: JSONObject) {
        val id = data.optString("client_msg_id")
        if (id.isEmpty()) return
        pending.remove(id)?.complete(
            WsAck(
                clientMsgId = id,
                id = data.optLong("id", 0L),
                convId = data.optInt("conv_id", 0),
                groupId = data.optInt("group_id", 0),
                createdAt = data.optString("created_at", ""),
            )
        )
    }

    /**
     * 发送一条文本/媒体消息并等待服务端 ack。
     *
     * @return 成功时带服务端 id；连接不在、发送失败、15 秒无 ack 都返回 failure。
     */
    suspend fun sendText(
        clientMsgId: String,
        content: String,
        groupId: Int = 0,
        toUser: Int = 0,
        convId: Int = 0,
        at: List<Int> = emptyList(),
        atAll: Boolean = false,
        quoteId: Int = 0,
        image: String = "",
        imageW: Int = 0,
        imageH: Int = 0,
        video: String = "",
        videoW: Int = 0,
        videoH: Int = 0,
        videoDuration: Int = 0,
        videoSize: Long = 0L,
        msgType: String = "",
    ): Result<WsAck> {
        val ws = socket ?: return Result.failure(IllegalStateException("WebSocket 未连接"))
        val data = JSONObject()
            .put("client_msg_id", clientMsgId)
            .put("content", content)
        when {
            groupId > 0 -> data.put("group_id", groupId)
            convId > 0 -> data.put("conv_id", convId)
            else -> data.put("to_user", toUser)
        }
        if (at.isNotEmpty()) {
            data.put("at", JSONArray().apply { at.forEach { put(it) } })
        }
        if (atAll) data.put("at_all", 1)
        if (quoteId > 0) data.put("quote_id", quoteId)
        if (image.isNotBlank()) {
            data.put("image", image)
            data.put("image_w", imageW)
            data.put("image_h", imageH)
        }
        if (video.isNotBlank()) {
            data.put("video", video)
            data.put("video_w", videoW)
            data.put("video_h", videoH)
            data.put("video_duration", videoDuration)
            data.put("video_size", videoSize)
            data.put("msg_type", if (msgType.isBlank()) "video" else msgType)
        }
        val frame = JSONObject().put("type", "send").put("data", data).toString()

        val deferred = CompletableDeferred<WsAck>()
        pending[clientMsgId] = deferred
        if (!ws.send(frame)) {
            pending.remove(clientMsgId)
            return Result.failure(IllegalStateException("WebSocket 发送失败"))
        }
        val ack = withTimeoutOrNull(15_000L) { deferred.await() }
        pending.remove(clientMsgId)
        return if (ack == null) {
            Result.failure(IllegalStateException("等待发送确认超时"))
        } else {
            Result.success(ack)
        }
    }

    /** 已读回执：fire-and-forget，失败不回滚 UI。 */
    fun sendRead(convId: Int = 0, groupId: Int = 0, lastReadId: Long) {
        val ws = socket ?: return
        val data = JSONObject().put("last_read_id", lastReadId)
        if (groupId > 0) data.put("group_id", groupId) else data.put("conv_id", convId)
        runCatching { ws.send(JSONObject().put("type", "read").put("data", data).toString()) }
    }

    /** 输入中：fire-and-forget。 */
    fun sendTyping(convId: Int = 0, groupId: Int = 0) {
        val ws = socket ?: return
        val data = JSONObject()
        if (groupId > 0) data.put("group_id", groupId) else data.put("conv_id", convId)
        runCatching { ws.send(JSONObject().put("type", "typing").put("data", data).toString()) }
    }
}
