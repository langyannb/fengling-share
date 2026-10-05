package com.fengling.share.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.fengling.share.data.VideoCache

/**
 * 视频播放的 UI 状态 (驱动「转圈 / 中文错误提示 / 重试」)
 */
sealed interface VideoPlaybackState {
    /** 首帧还没出来, 正在缓冲 (用户至少能看到转圈, 而不是纯黑屏) */
    data object Loading : VideoPlaybackState

    /** 已经在放 / 可以放 */
    data object Playing : VideoPlaybackState

    /** 彻底失败, 带中文原因 + 错误码 */
    data class Failed(val message: String, val code: String) : VideoPlaybackState
}

/** 把 Media3 的错误码翻译成用户看得懂的中文 (以前只有一句 Toast「视频播放失败」) */
@OptIn(UnstableApi::class)
internal fun playbackErrorMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> "网络不太好, 视频没加载出来"

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    -> "视频服务暂时不可用"

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "视频文件不存在或已被清理"

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    -> "视频文件损坏, 无法播放"

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> "这台设备不支持这个视频的编码格式"

    else -> "视频播放失败"
}

/** 顺着 ContextWrapper 链找到宿主的 lifecycle (退到后台自动暂停, 免得声音还在放) */
private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is LifecycleOwner) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 建一个走 [VideoCache] 缓存池的 ExoPlayer, 并在离开组合时释放。
 *
 * 依赖注意: media3 的 SimpleCache / CacheDataSource 属于 @UnstableApi, 这里用
 * androidx.annotation.OptIn 显式标注, 免得 CI 的 lint 报 UnsafeOptInUsageError。
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberCachedExoPlayer(url: String): ExoPlayer {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(VideoCache.dataSourceFactory(context.applicationContext)),
            )
            .build()
    }
    LaunchedEffect(player, url) {
        if (url.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(VideoCache.cacheKey(url)))
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.playWhenReady = true
            player.prepare()
        }
    }
    DisposableEffect(player) {
        val owner = context.findLifecycleOwner()
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnStart = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (resumeOnStart) player.play()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            player.release()
        }
    }
    return player
}

/**
 * 视频画面 (ExoPlayer + PlayerView) —— 取代原来的 Android View { Video View }。
 *
 * - 走 [VideoCache] 的 300MB LRU 缓存, 同一条视频第二次打开秒开、不重复走流量。
 * - 出错自动重试 1 次 (moov 在文件尾部的老视频首次解析容易抖一下), 再失败才报中文提示。
 * - [retryToken] 变化 = 用户点了「重新加载」, 由调用方持有这个计数。
 */
@OptIn(UnstableApi::class)
@Composable
fun CachedVideoSurface(
    url: String,
    modifier: Modifier = Modifier,
    showController: Boolean = true,
    retryToken: Int = 0,
    onState: (VideoPlaybackState) -> Unit = {},
) {
    val player = rememberCachedExoPlayer(url)
    var autoRetried by remember(url) { mutableStateOf(false) }

    DisposableEffect(player, url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onState(VideoPlaybackState.Loading)
                    Player.STATE_READY -> onState(VideoPlaybackState.Playing)
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (autoRetried) {
                    onState(VideoPlaybackState.Failed(playbackErrorMessage(error), error.errorCodeName))
                } else {
                    // 自动重试一次, 用户看不到失败
                    autoRetried = true
                    onState(VideoPlaybackState.Loading)
                    runCatching {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(retryToken) {
        if (retryToken > 0) {
            autoRetried = false
            onState(VideoPlaybackState.Loading)
            runCatching {
                player.prepare()
                player.play()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                // 自己画转圈, 不用 PlayerView 自带的缓冲图, 免得两层转圈叠一起
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                useController = showController
                controllerAutoShow = true
                controllerShowTimeoutMs = 2500
            }
        },
        update = { view ->
            view.player = player
            view.useController = showController
        },
    )
}

/**
 * 播放状态覆盖层: 加载中转圈 / 失败中文提示 + 重试
 */
@Composable
fun VideoPlaybackOverlay(
    state: VideoPlaybackState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        VideoPlaybackState.Playing -> Unit
        VideoPlaybackState.Loading -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(text = "视频加载中…", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }

        is VideoPlaybackState.Failed -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text(
                    text = state.message,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "错误码 " + state.code,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
                TextButton(onClick = onRetry) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text(text = "重新加载", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
