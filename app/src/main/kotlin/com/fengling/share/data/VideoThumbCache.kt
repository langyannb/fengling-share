package com.fengling.share.data

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 视频封面缓存 (v1.1.15)
 *
 * 用户反馈「视频能发能看, 封面永远黑屏」: 服务端根本不返回封面图, 气泡以前画的是静态深色渐变。
 * 现在气泡改成用 coil-video 抽帧; 但**自己刚发出去的视频**不该为了封面再把整段视频从服务器拉一遍 ——
 * 发送时本来就抽好了一张本地首帧 (VideoProbe.firstFrame), 这里按远端直链存下来, 自己的气泡立刻有画面。
 *
 * 按字节数计量的 LRU (存之前先把最长边缩到 480px, 单张约 0.3MB), 上限 12MB。
 * version 是放进 Compose 快照系统的计数: 已经组合好的气泡在封面写进来之后会自动重绘。
 */
object VideoThumbCache {

    /** 缓存上限 (KB) */
    private const val MAX_KB = 12 * 1024

    /** 存之前把最长边缩到这个像素: 气泡最大 240dp, 480px 足够清楚 */
    private const val MAX_SIDE_PX = 480

    private val lru = object : android.util.LruCache<String, Bitmap>(MAX_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    /** 每写一次 +1; Composable 里读一下就能收到「缓存变了」的通知 */
    var version by mutableIntStateOf(0)
        private set

    fun put(url: String, bitmap: Bitmap?) {
        if (url.isBlank() || bitmap == null || bitmap.isRecycled) return
        val scaled = runCatching { scale(bitmap) }.getOrNull() ?: return
        lru.put(url, scaled)
        version++
    }

    fun get(url: String): Bitmap? {
        if (url.isBlank()) return null
        val b = lru.get(url) ?: return null
        return if (b.isRecycled) null else b
    }

    private fun scale(src: Bitmap): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= MAX_SIDE_PX || maxSide <= 0) return src
        val ratio = MAX_SIDE_PX.toFloat() / maxSide.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
