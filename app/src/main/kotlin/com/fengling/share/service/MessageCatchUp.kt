package com.fengling.share.service

import android.content.Context
import android.util.Log
import com.fengling.share.data.ApiClient
import com.fengling.share.data.Settings
import com.fengling.share.data.StreamCursor
import com.fengling.share.data.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MessageCatchUp - 「兜底补拉」: 拿服务端最新消息 id 和本地游标比, 把漏掉的通知补弹出来 (v1.1.2)
 *
 * 为什么需要它:
 * 划掉后台之后, ColorOS 未「锁定」时会直接拒绝服务重启 (`OplusAppStartupManager: prevent restart service`),
 * 只有 15 分钟的 JobScheduler 兜底 —— 等于收不到消息。所以闹钟守护任务
 * [com.fengling.share.receiver.KeepAliveReceiver] 每约 120 秒醒一次, 除了把服务拉回来 / 重连 SSE,
 * **无论哪种情况都做一次轻量 HTTP 补拉**: 拉会话列表 + 群列表, 和 [StreamCursor] 里记录的
 * 「已通知过的最大 id」比大小, 发现更大的、不是我发的、没开免打扰的, 就补一条通知。
 *
 * 去重规则 (不重复弹同一条):
 * - 只处理 `id > 游标` 的消息, 每个会话 / 群只看**最新那一条** (更早的积压就让它过去, 不刷屏);
 * - 自己发的 (`SocialGroup.lastMessage.userId == 我` / `PmMessage.mine`) 不弹;
 * - 群消息遵守「消息免打扰」(muted) 与设置页的「群消息提醒」开关;
 * - 通知 id 与正文格式完全复用 [MessageNotifier] (私聊 2000+convId, 群 3000+groupId),
 *   所以即使 SSE 同时把同一条推了, 也只是覆盖同一张通知, 不会出现两条。
 *
 * 游标推进与 SSE 收到事件时用的是**同一套**游标, 因此补拉不会让 SSE 重复推送历史消息。
 */
object MessageCatchUp {

    private const val TAG = "FLS_MSG"

    /**
     * 执行一次补拉。
     *
     * @param reason 日志用 (谁触发的: 守护心跳 / SSE 连接前 ...)
     * @return 补弹的通知条数; -1 = 会话列表拉取失败, -2 = 群列表拉取失败 (网络问题, 不是崩溃)
     */
    suspend fun pull(context: Context, reason: String): Int = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (!runCatching { UserStore.isLoggedIn() }.getOrDefault(false)) return@withContext 0

        val myId = runCatching { UserStore.current?.id ?: 0 }.getOrDefault(0)
        val basePm = StreamCursor.lastPmId
        val baseGrp = StreamCursor.lastGroupId
        // 游标全 0 = 首次安装 / 刚退出登录: 语义是「从现在开始」, 绝不重放历史消息
        if (basePm <= 0 && baseGrp <= 0) {
            Log.i(TAG, "补拉($reason): 游标为 0 (首次/刚登录), 跳过, 不重放历史")
            return@withContext 0
        }

        var maxPm = basePm
        var maxGrp = baseGrp
        var pushed = 0
        var fail = 0

        // ---------------- 私聊 ----------------
        val pmPage = ApiClient.pmConversations().getOrNull()
        if (pmPage == null) {
            fail = -1
            Log.w(TAG, "补拉($reason): 私聊会话列表拉取失败")
        } else {
            MessageNotifier.ensureChannels(context)
            for (conv in pmPage.list) {
                val last = conv.last ?: continue
                if (last.id > maxPm) maxPm = last.id
                if (last.id <= basePm) continue
                if (last.mine) continue
                if (myId > 0 && last.userId == myId) continue
                val title = conv.displayName.ifBlank { "用户 ${conv.userId}" }
                Log.i(TAG, "补拉($reason): 私聊 conv=" + conv.convId + " msg=" + last.id)
                MessageNotifier.notifyPm(
                    context = context,
                    convId = conv.convId,
                    nickname = title,
                    fromUser = conv.userId,
                    content = last.content,
                    image = last.image,
                )
                pushed++
            }
        }

        // ---------------- 群 ----------------
        val groups = ApiClient.socialGroups().getOrNull()
        if (groups == null) {
            if (fail == 0) fail = -2
            Log.w(TAG, "补拉($reason): 群列表拉取失败")
        } else {
            MessageNotifier.ensureChannels(context)
            for (g in groups) {
                val last = g.lastMessage ?: continue
                if (last.id > maxGrp) maxGrp = last.id
                if (last.id <= baseGrp) continue
                if (myId > 0 && last.userId == myId) continue
                if (g.muted) continue
                // at_me_first / at_all_first 是「未读里第一条 @」的消息 id: 等于最新消息 id 才说明的最新这条就是 @
                val atMe = g.atMeFirst > 0 && g.atMeFirst >= last.id
                val atAll = g.atAllFirst > 0 && g.atAllFirst >= last.id
                if (!atMe && !atAll && !Settings.notifyGroupAll) continue
                Log.i(TAG, "补拉($reason): 群 " + g.id + " msg=" + last.id + " atMe=" + atMe)
                MessageNotifier.notifyGroup(
                    context = context,
                    groupId = g.id,
                    groupName = g.name,
                    nickname = last.nickname,
                    content = last.content,
                    image = last.image,
                    atMe = atMe,
                    atAll = atAll,
                )
                pushed++
            }
        }

        // 游标只增不减: 无论有没有弹通知 (免打扰 / 自己发的 / 关掉了群提醒), 都算「已处理过」
        StreamCursor.markPm(maxPm)
        StreamCursor.markGroup(maxGrp)

        Log.i(
            TAG,
            "补拉($reason): 完成 补弹=" + pushed + " 耗时=" + (System.currentTimeMillis() - started) +
                "ms 游标 pm=" + basePm + "->" + maxPm + " grp=" + baseGrp + "->" + maxGrp,
        )
        if (pushed > 0) return@withContext pushed
        return@withContext fail
    }
}
