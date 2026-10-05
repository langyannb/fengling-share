package com.fengling.share.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Wave 2 视频消息: 发送前探测工具 (选片 / 拍摄后立刻调一次)
 *
 * 服务端 social_video_upload 的 video_w/video_h/video_duration/video_size 是
 * **客户端回传**的值 (服务端 video_meta_params() 只做范围兜底), 所以这几个字段必须由
 * 这里探测出来再传上去, 否则消息里的宽高/时长全是 0, 气泡排版和时长角标就废了。
 */
data class VideoProbeInfo(
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    /** 秒 */
    val durationSec: Int = 0,
    val displayName: String = "",
)

object VideoProbe {

    /** 单个视频时长上限 (秒) —— 5 分钟, 超过直接拒绝 (契约: 客户端校验) */
    const val MAX_DURATION_SEC = 5 * 60

    /**
     * 从 content:// 拿文件名 (用于 multipart 的 file 名 + 后缀).
     * ContentResolver 的 OpenableColumns.DISPLAY_NAME, 拿不到就给个兜底名。
     */
    fun displayName(context: Context, uri: Uri, fallback: String = "chat.mp4"): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: fallback
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val n = c.getString(idx)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        return fallback
    }

    /**
     * 文件字节数 (OpenableColumns.SIZE)。
     * 拿不到时退回 0 —— 调用方会跳过「超出上限」判断, 交给服务端兜底 (它会回中文错误)。
     */
    fun sizeBytes(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { p -> java.io.File(p).length() } ?: 0L
        }
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) return c.getLong(idx)
            }
        }
        return 0L
    }

    /**
     * 探测宽/高/时长 (MediaMetadataRetriever)。
     * 个别 ROM / 编码拿不到 METADATA_KEY_VIDEO_WIDTH, 拿不到就返回 0 (调用方兜 16:9)。
     */
    fun probe(context: Context, uri: Uri): VideoProbeInfo = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            // 竖屏拍出来的视频, 元数据里的宽高是「传感器方向」的, 要按 rotation 换回来,
            // 否则 9:16 的竖屏视频会被当成 16:9 横屏排版
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val swap = rot == 90 || rot == 270
            VideoProbeInfo(
                sizeBytes = sizeBytes(context, uri),
                width = if (swap) rawH else rawW,
                height = if (swap) rawW else rawH,
                durationSec = durationSec(retriever),
                displayName = displayName(context, uri),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrElse {
        VideoProbeInfo(
            sizeBytes = sizeBytes(context, uri),
            displayName = displayName(context, uri),
        )
    }

    private fun durationSec(retriever: MediaMetadataRetriever): Int {
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        return if (ms <= 0L) 0 else ((ms + 999L) / 1000L).toInt()
    }

    /**
     * 取首帧做本地缩略图 (乐观发送时气球里立刻有画面)。
     * timeUs 用 1_000_000us = 第 1 秒 —— 很多视频第 0 帧是全黑的。
     */
    fun firstFrame(context: Context, uri: Uri, timeUs: Long = 1_000_000L): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    /** 时长角标: 12 -> "0:12", 95 -> "1:35" */
    fun formatDuration(sec: Int): String {
        if (sec <= 0) return ""
        val m = sec / 60
        val s = sec % 60
        return "%d:%02d".format(m, s)
    }

    /**
     * 这条消息是不是视频消息。
     * 服务端: 视频消息 msg_type="video"; 老图片消息 msg_type 是空串 (不是 "image"), 所以不能按
     * msg_type=="image" 判断, 这里只认 video —— 图片分支的旧逻辑一个字节都不动。
     */
    fun isVideoMessage(msgType: String, video: String): Boolean =
        msgType.equals("video", ignoreCase = true) || video.isNotBlank()

    /**
     * 视频已被服务端自动清理 (只删了对象存储里的文件, 消息还在):
     * video 为空串 或 content 里带了「[视频已清理]」。
     */
    fun isVideoCleaned(video: String, content: String): Boolean =
        video.isBlank() || content.contains("[视频已清理]")

    /** 上传成功后服务端返回的宽高可能是 0 (客户端没传), 用探测器结果兜底 */
    fun pickRatio(w: Int, h: Int): Float =
        if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
}
