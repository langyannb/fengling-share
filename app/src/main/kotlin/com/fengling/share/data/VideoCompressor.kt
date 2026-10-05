package com.fengling.share.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===================== Wave 3 (1.1.14) 阶段3: 上传前压缩 =====================

/**
 * 触发压缩的阈值 (契约规格): 长边 / 时长 / 体积 任意一条超线就先压再传。
 * 只压「明显偏大」的, 正常手机随手拍的短片一个字节都不动。
 */
internal const val COMPRESS_MAX_LONG_EDGE_PX = 1280
internal const val COMPRESS_TRIGGER_DURATION_SEC = 20
internal const val COMPRESS_TRIGGER_SIZE_BYTES = 8L * 1024L * 1024L

/**
 * 压缩目标: 720p 上限 (按**短边**算, 竖屏横屏都不吃亏) + H.264 约 2.5Mbps + AAC 96kbps + 尽量 faststart。
 * 只缩不放: 源视频短边本来就不到 720 就不加缩放 (Media3 的竖屏缩放会把小视频放大, 那就白压了)。
 */
private const val COMPRESS_MAX_SHORT_SIDE_PX = 720
private const val COMPRESS_VIDEO_BITRATE_BPS = 2_500_000
private const val COMPRESS_AUDIO_BITRATE_BPS = 96_000

private const val COMPRESS_POLL_INTERVAL_MS = 200L
private const val COMPRESS_OUTPUT_PREFIX = "upload_compressed_"
private const val COMPRESS_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L

/**
 * 这条视频要不要先压再传:
 * 长边超过 1280px (1080p / 4K)、时长超过 20 秒、或体积超过 8MB 都算「大」。
 * 宽高拿不到 (0) 时按「不超线」处理, 交给时长 / 体积判断, 不误压。
 */
internal fun shouldCompressVideo(sizeBytes: Long, width: Int, height: Int, durationSec: Int): Boolean {
    val longEdge = maxOf(width, height)
    return longEdge > COMPRESS_MAX_LONG_EDGE_PX ||
        durationSec > COMPRESS_TRIGGER_DURATION_SEC ||
        sizeBytes > COMPRESS_TRIGGER_SIZE_BYTES
}

/**
 * 上传前压缩: 720p 上限 + H.264 + 约 2.5Mbps + AAC 96kbps, 输出到缓存目录的临时 mp4。
 *
 * 返回压缩后的文件; **返回 null 表示压缩失败** (不支持的解码器 / 编码器被占用 / 文件损坏),
 * 调用方必须回退成原文件直传, 不能因为压不动就不发了。
 *
 * 注意: Transformer 只能在带 Looper 的线程上创建和驱动, 所以整段跑在主线程上,
 * 进度用 delay 轮询 (每 200ms 一次), 挂起而不阻塞, 不卡 UI。
 */
@OptIn(UnstableApi::class)
internal suspend fun compressVideoForUpload(
    context: Context,
    uri: Uri,
    /** 源视频的短边像素 (拿不到传 0, 那就只压码率不缩分辨率) */
    sourceShortSide: Int,
    /** 用户中途点了取消: 转码立刻停下, 返回 null (调用方按「没压出东西」处理并走取消分支) */
    isCancelled: () -> Boolean = { false },
    onProgress: (Int) -> Unit,
): File? = withContext(Dispatchers.Main) {
    val app = context.applicationContext
    val outFile = File(app.cacheDir, COMPRESS_OUTPUT_PREFIX + System.currentTimeMillis() + ".mp4")
    cleanupStaleCompressOutputs(app)

    val done = CompletableDeferred<Boolean>()
    val holder = ProgressHolder()
    val transformer: Transformer = try {
        Transformer.Builder(app)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(app)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(COMPRESS_VIDEO_BITRATE_BPS)
                            .build(),
                    )
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder()
                            .setBitrate(COMPRESS_AUDIO_BITRATE_BPS)
                            .build(),
                    )
                    .build(),
            )
            .setMuxerFactory(FastStartMp4MuxerFactory)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(true)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        done.complete(false)
                    }
                },
            )
            .build()
    } catch (t: Throwable) {
        return@withContext null
    }

    try {
        val audioProcessors: List<androidx.media3.common.audio.AudioProcessor> = emptyList()
        val videoEffects: List<androidx.media3.common.Effect> =
            if (sourceShortSide > COMPRESS_MAX_SHORT_SIDE_PX) {
                listOf(Presentation.createForShortSide(COMPRESS_MAX_SHORT_SIDE_PX))
            } else {
                emptyList()
            }
        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
        transformer.start(editedItem, outFile.absolutePath)
    } catch (t: Throwable) {
        transformer.cancel()
        outFile.delete()
        return@withContext null
    }

    val poll = launch {
        while (isActive && !done.isCompleted) {
            if (isCancelled()) {
                transformer.cancel()
                done.complete(false)
                return@launch
            }
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgress(holder.progress.coerceIn(0, 100))
            }
            delay(COMPRESS_POLL_INTERVAL_MS)
        }
    }
    val ok = done.await()
    poll.cancel()

    if (!ok || !outFile.exists() || outFile.length() <= 0L) {
        outFile.delete()
        return@withContext null
    }
    onProgress(100)
    outFile
}

/** 顺手清掉上次残留的压缩临时文件 (超过 24 小时的), 免得缓存目录越堆越大 */
private fun cleanupStaleCompressOutputs(context: Context) {
    runCatching {
        val cutoff = System.currentTimeMillis() - COMPRESS_CACHE_MAX_AGE_MS
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(COMPRESS_OUTPUT_PREFIX) && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}

/**
 * 让输出尽量 faststart 的封装器: 委托 Media3 内置的 mp4 封装器, 明确打开「可流式输出」。
 *
 * 静态证据 (media3 1.11.1 源码, 尚未真机扫字节序):
 * - 打开开关后 Mp4Writer 在 ftyp 之后会预留 DEFAULT_MOOV_BOX_SIZE_BYTES = 400000 字节的 moov 位
 *   (Mp4Writer.java:51 常量, :323-327 预留); 采样数据写在预留区之后。
 * - 收尾时 maybeWriteMoovAtStart() (Mp4Writer.java:385-400) 只要 moov 装得下就把 moov 写在预留位
 *   (也就是在 mdat 采样数据**之前**), 剩下的空隙填 free box;
 *   装不下才退回文件尾, 并把预留位改写成 free box (:401-411)。
 * 所以 moov 不大的输出 (我们压到 720p / 2.5Mbps 的产物) 会拿到 moov 在前的文件。
 * 这是「尽力而为」不是保证: 长视频 moov 超过 400KB 时仍会落在文件尾。
 */
@OptIn(UnstableApi::class)
private object FastStartMp4MuxerFactory : Muxer.Factory {
    private val delegate: InAppMp4Muxer.Factory = InAppMp4Muxer.Factory()
        .setAttemptStreamableOutputEnabled(true)

    override fun create(path: String): Muxer = delegate.create(path)

    override fun getSupportedSampleMimeTypes(trackType: Int) =
        delegate.getSupportedSampleMimeTypes(trackType)
}
