package com.fengling.share.data

import android.content.Context
import coil.ImageLoader
import coil.decode.Decoder
import coil.decode.VideoFrameDecoder
import coil.fetch.SourceResult
import coil.request.Options

/**
 * 视频封面专用 ImageLoader 的「这是视频」标记参数 (v1.1.15)。
 * 只在工程内部用, 名字带 coil# 前缀避免和 Coil 自带参数撞车。
 */
const val VIDEO_COVER_PARAM = "coil#fls_video_cover"

/**
 * 视频封面专用的 Coil ImageLoader (v1.1.15)
 *
 * 抽帧要用 coil-video 的 VideoFrameDecoder, 而本工程没有 Application 子类、没有全局
 * ImageLoaderFactory 可以挂 (为了一个封面去改 Application 会牵动整条图片链路, 不动)。
 * 所以单开一个只给视频气泡用的 loader —— 它照旧吃 Coil 默认的内存 + 磁盘缓存,
 * 同一条视频只会被下载/解码一次, 解码全程在 Coil 自己的后台调度器上, 不碰主线程。
 */
object VideoCoverLoader {

    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        loader?.let { return it }
        return synchronized(this) {
            loader?.let { return@synchronized it }
            val built = ImageLoader.Builder(context.applicationContext)
                .components { add(VideoCoverFrameFactory()) }
                .build()
            loader = built
            built
        }
    }
}

/**
 * 抽帧解码器工厂。
 *
 * 直接用 coil-video 的 VideoFrameDecoder.Factory 有个坑: 它只认 fetcher 报上来的 MIME
 * (`mimeType.startsWith("video/")`), 而对象存储没回 Content-Type 时 Coil 拿到的是 null,
 * 结果就是「静默不出封面」, 又变回黑屏。
 *
 * 所以这里认两个条件之一:
 *   ① 请求带了 [VIDEO_COVER_PARAM] 标记 (视频气泡发的请求一定带 —— 地址是不是视频我们自己最清楚);
 *   ② fetcher 报的 MIME 本来就是 video 开头的。
 * 两条都不满足才返回 null 让给别的解码器, 不会影响这个 loader 之外的任何图片。
 */
private class VideoCoverFrameFactory : Decoder.Factory {

    override fun create(
        result: SourceResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        val marked = options.parameters.value<Boolean>(VIDEO_COVER_PARAM) == true
        val mimeIsVideo = result.mimeType?.startsWith("video/") == true
        return if (marked || mimeIsVideo) VideoFrameDecoder(result.source, options) else null
    }

    override fun equals(other: Any?) = other is VideoCoverFrameFactory

    override fun hashCode() = javaClass.hashCode()
}
