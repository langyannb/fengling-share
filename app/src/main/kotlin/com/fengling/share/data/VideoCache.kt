package com.fengling.share.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 视频播放缓存 (阶段2): Media3 SimpleCache + LRU 上限 300MB。
 *
 * 为什么要缓存:
 * 1. 手机拍的 mp4 的 moov 在文件尾部 (不是 faststart), 播放器为了解析轨道必须先把整段
 *    文件扫一遍; 有了缓存, 同一条视频第二次打开就是本地读, 不再重复走流量。
 * 2. 缓存 key 就是视频直链 (服务端直链稳定), 命中率高。
 *
 * 注意: SimpleCache 要求「同一个缓存目录全局只有一个实例」, 所以这里做单例, 不要别处再 new。
 */
@OptIn(UnstableApi::class)
object VideoCache {

    /** 播放缓存上限: 300MB (超出按 LRU 淘汰最久未用的片段) */
    private const val MAX_BYTES = 300L * 1024L * 1024L

    private const val DIR_NAME = "video_play_cache"

    @Volatile
    private var instance: SimpleCache? = null

    /** 取全局唯一的播放缓存 (首次调用会建索引数据库, 必须在可写的 cacheDir 下) */
    @OptIn(UnstableApi::class)
    fun get(context: Context): SimpleCache {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val app = context.applicationContext
            val dir = File(app.cacheDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val cache = SimpleCache(
                dir,
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(app),
            )
            instance = cache
            return cache
        }
    }

    /** ExoPlayer 用的数据源: 先查缓存, 未命中再走网络并边播边写缓存 */
    @OptIn(UnstableApi::class)
    fun dataSourceFactory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        return CacheDataSource.Factory()
            .setCache(get(app))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** 缓存 key = 视频直链 (不做额外变换, 便于命中) */
    fun cacheKey(url: String): String = url
}
