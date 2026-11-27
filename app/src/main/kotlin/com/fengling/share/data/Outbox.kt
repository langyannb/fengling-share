package com.fengling.share.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 未确认发送的文本消息。
 *
 * 只在 WS 发送失败/超时后写入；MessageService 连上带 send_ack 的新服务后按顺序重发。
 * 服务端用 client_msg_id 做幂等，所以重发不会产生重复消息。
 */
data class PendingSend(
    val clientMsgId: String,
    val groupId: Int = 0,
    val toUser: Int = 0,
    val convId: Int = 0,
    val content: String,
    val at: List<Int> = emptyList(),
    val atAll: Boolean = false,
    val quoteId: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("client_msg_id", clientMsgId)
        .put("group_id", groupId)
        .put("to_user", toUser)
        .put("conv_id", convId)
        .put("content", content)
        .put("at", JSONArray().apply { at.forEach { put(it) } })
        .put("at_all", if (atAll) 1 else 0)
        .put("quote_id", quoteId)

    companion object {
        fun fromJson(j: JSONObject): PendingSend = PendingSend(
            clientMsgId = j.optString("client_msg_id"),
            groupId = j.optInt("group_id", 0),
            toUser = j.optInt("to_user", 0),
            convId = j.optInt("conv_id", 0),
            content = j.optString("content", ""),
            at = buildList {
                val arr = j.optJSONArray("at")
                if (arr != null) for (i in 0 until arr.length()) add(arr.optInt(i))
            },
            atAll = j.optInt("at_all", 0) == 1,
            quoteId = j.optInt("quote_id", 0),
        )
    }
}

object Outbox {

    private const val PREFS = "fengling_outbox"
    private const val KEY = "pending"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun add(send: PendingSend) {
        if (!::prefs.isInitialized || send.clientMsgId.isBlank()) return
        val all = all().filterNot { it.clientMsgId == send.clientMsgId }.toMutableList()
        all.add(send)
        save(all)
    }

    @Synchronized
    fun remove(clientMsgId: String) {
        if (!::prefs.isInitialized) return
        save(all().filterNot { it.clientMsgId == clientMsgId })
    }

    @Synchronized
    fun all(): List<PendingSend> {
        if (!::prefs.isInitialized) return emptyList()
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { PendingSend.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<PendingSend>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** 返回成功发出的条数；遇到第一个失败就停，等下一条连接再试。 */
    suspend fun flush(): Int {
        if (!WsSocket.supportsSendAck) return 0
        var sent = 0
        for (send in all()) {
            val result = WsSocket.sendText(
                clientMsgId = send.clientMsgId,
                content = send.content,
                groupId = send.groupId,
                toUser = send.toUser,
                convId = send.convId,
                at = send.at,
                atAll = send.atAll,
                quoteId = send.quoteId,
            )
            if (result.isSuccess) {
                remove(send.clientMsgId)
                sent++
            } else {
                break
            }
        }
        return sent
    }
}
