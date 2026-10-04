package com.fengling.share.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * 消息提示音 (前台播放系统默认通知铃声)
 *
 * 直接取系统默认通知铃声播放: 用户在系统设置里换铃声会自动跟着变,
 * 不需要任何权限, 也不需要发系统通知 (POST_NOTIFICATIONS)。
 * 内部限流, 短时间内连收多条消息时不会把声音叠在一起。
 */
object NotifySound {
    /** 两次响铃之间至少间隔的毫秒数 */
    private const val MIN_GAP_MS = 1500L

    private var lastPlayedAt = 0L

    fun play(context: Context, minGapMs: Long = MIN_GAP_MS) {
        val now = System.currentTimeMillis()
        if (now - lastPlayedAt < minGapMs) return
        lastPlayedAt = now
        val app = context.applicationContext
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return@runCatching
            val ringtone = RingtoneManager.getRingtone(app, uri) ?: return@runCatching
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        }
    }
}
