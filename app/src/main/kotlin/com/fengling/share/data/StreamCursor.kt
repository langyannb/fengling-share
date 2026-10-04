package com.fengling.share.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * StreamCursor - SSE 游标 / 「已通知过的最大 id」持久化 (v1.1.2)
 *
 * 为什么需要它 (两个用途):
 *
 * 1) **断线补漏**: 服务端 `sse_run(array $me, int $pmCur, int $grpCur)` 的语义是 ——
 *    `pmCur <= 0` 表示「从现在开始推」; `pmCur > 0` 表示「从该 id 之后开始推」。
 *    客户端以前把 `pm_id=0&group_id=0` 写死在 URL 里, 每次约 25 秒重连都会丢掉重连窗口内的
 *    那几条事件 (上一轮真机实测丢了 2 条)。改成用这里的游标去连, 断线期间的消息就能补齐。
 *
 * 2) **兜底补拉**: 闹钟守护任务醒来时, 要拿服务端最新消息 id 和「我最后弹过通知的 id」比大小,
 *    才知道划掉后台 / 被 ROM 冻结期间有没有漏弹的通知。
 *
 * 语义统一为: 「**id 小于等于游标的消息, 要么已经收到, 要么已经弹过通知, 不会再弹第二次**」。
 * 所以游标只增不减; 换账号 / 退出登录会归零 —— 0 表示「从现在开始」, 首次安装绝不重放历史消息。
 */
object StreamCursor {

    private const val TAG = "FLS_MSG"
    private const val PREFS_NAME = "fls_stream_cursor"
    private const val KEY_PM = "sse_last_pm_id"
    private const val KEY_GROUP = "sse_last_group_id"
    /** 游标属于哪个账号 (换账号必须归零, 否则会把别人账号的消息 id 当基线) */
    private const val KEY_OWNER = "sse_owner_uid"

    @Volatile private var prefs: SharedPreferences? = null

    /**
     * 初始化 / 换账号检查。幂等, Service、Receiver、Activity 都可以随时调。
     * 每次调用都会核对「游标所属账号」和当前登录账号, 不一致就整体归零。
     */
    fun init(context: Context) {
        val p = prefs ?: runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return
        prefs = p
        val me = runCatching { UserStore.current?.id ?: 0 }.getOrDefault(0)
        val saved = p.getInt(KEY_OWNER, -1)
        if (saved != me) {
            runCatching { p.edit().putInt(KEY_OWNER, me).putInt(KEY_PM, 0).putInt(KEY_GROUP, 0).commit() }
            Log.i(TAG, "SSE 游标: 账号变更 -> 归零 (当前 uid=$me, 原 uid=$saved)")
        }
    }

    /** 私聊游标 (已通知过的最大私聊消息 id), 0 = 从现在开始 */
    val lastPmId: Int get() = runCatching { prefs?.getInt(KEY_PM, 0) ?: 0 }.getOrDefault(0)

    /** 群游标 (已通知过的最大群消息 id), 0 = 从现在开始 */
    val lastGroupId: Int get() = runCatching { prefs?.getInt(KEY_GROUP, 0) ?: 0 }.getOrDefault(0)

    /** 只增不减; 用 commit() 是为了「服务刚记下游标就被 ROM 杀掉」时游标不会丢 */
    fun markPm(id: Int) {
        val p = prefs ?: return
        runCatching {
            if (id <= 0 || id <= p.getInt(KEY_PM, 0)) return@runCatching
            p.edit().putInt(KEY_PM, id).commit()
        }
    }

    /** 只增不减, 同 [markPm] */
    fun markGroup(id: Int) {
        val p = prefs ?: return
        runCatching {
            if (id <= 0 || id <= p.getInt(KEY_GROUP, 0)) return@runCatching
            p.edit().putInt(KEY_GROUP, id).commit()
        }
    }

    /** 归零 (退出登录 / 换账号时用): 下次连接 = 从现在开始, 不重放历史 */
    fun reset() {
        val p = prefs ?: return
        runCatching { p.edit().putInt(KEY_PM, 0).putInt(KEY_GROUP, 0).commit() }
        Log.i(TAG, "SSE 游标: 已归零")
    }
}
