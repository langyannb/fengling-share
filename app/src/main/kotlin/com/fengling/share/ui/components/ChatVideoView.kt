package com.fengling.share.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.fengling.share.data.VIDEO_COVER_PARAM
import com.fengling.share.data.VideoCoverLoader
import com.fengling.share.data.VideoThumbCache
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Wave 2 视频消息的气泡尺寸。
 *
 * 契约: 宽高比按 video_w/video_h, **最高 240dp**。
 * r >= 1 (横屏) → 宽 240, 高 240/r;  r < 1 (竖屏) → 高 240, 宽 240*r。
 * 比例限制在 0.5~3 之间, 免得超宽/超长的视频把聊天气泡撑变形。
 */
fun videoBubbleSize(width: Int, height: Int): Pair<Dp, Dp> {
    val r = if (width > 0 && height > 0) {
        (width.toFloat() / height.toFloat()).coerceIn(0.5f, 3f)
    } else {
        16f / 9f
    }
    return if (r >= 1f) 240.dp to (240f / r).dp else (240f * r).dp to 240.dp
}

/** 视频时长角标: 右下角黑底白字 "0:12" */
@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 10.sp)
    }
}

/**
 * 视频气泡里的封面 (v1.1.15)
 *
 * 取封面顺序:
 *   ① 自己刚发出去时存下的本地首帧 (VideoThumbCache) —— 立刻有画面, 也不用为了封面把整段视频再拉一遍;
 *   ② coil-video 从视频里抽帧 —— 第 0 帧经常是黑帧 / 淡入, 所以先取第 1.5 秒,
 *      解码失败在 onError 里换成第 0 帧重来一次;
 *   ③ 两次都不行就什么都不画, 由 VideoBubble 那层深色渐变 + 播放图标兜底 (中性占位, 不是纯黑)。
 *
 * 抽帧跑在 Coil 自己的后台调度器上 (吃 Coil 的内存 + 磁盘缓存), 不阻塞主线程, 也不挡列表滚动。
 */
@Composable
private fun VideoCover(url: String, modifier: Modifier = Modifier) {
    if (url.isBlank()) return
    // 读一下 version: 自己发的视频把首帧写进缓存之后, 已经组合好的气泡会自动重绘
    val cacheVersion = VideoThumbCache.version
    val local = remember(url, cacheVersion) { VideoThumbCache.get(url) }
    if (local != null) {
        Image(
            bitmap = local.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    val loader = remember(context) { VideoCoverLoader.get(context) }
    // 1500ms 起步; 解码失败回退第 0 帧; 再失败就停在下面的深色占位
    var frameMillis by remember(url) { mutableStateOf(1500L) }
    val request = remember(url, frameMillis, context) {
        ImageRequest.Builder(context)
            .data(url)
            // 明确告诉抽帧解码器「这是视频」: 对象存储不回 video 类型的 Content-Type 时也能出封面
            .setParameter(VIDEO_COVER_PARAM, true)
            .videoFrameMillis(frameMillis)
            .build()
    }
    AsyncImage(
        model = request,
        imageLoader = loader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        onError = {
            if (frameMillis != 0L) frameMillis = 0L
        },
    )
}

/**
 * 视频消息气泡 (群聊 / 私聊共用)
 *
 * v1.1.15: 封面改成真抽帧 (coil-video) —— 之前那版只有静态深色渐变, 用户看到的就是「封面永远黑屏」。
 * 1.1.14 起上传前已统一压成 720p/H.264/faststart, 抽帧走 Coil 的磁盘缓存, 同一条视频只拉一次;
 * 抽不到画面时退化成深色渐变 + 播放按钮的中性占位 (绝不是纯黑)。点开仍由全屏 ExoPlayer 播。
 *
 * - 点一下 -> onOpenFullscreen() 打开全屏播放
 * - 长按 -> onLongPress() 走原有的撤回/删除菜单 (与图片消息同一套)
 */
@Composable
fun VideoBubble(
    url: String,
    videoW: Int,
    videoH: Int,
    durationSec: Int,
    mine: Boolean,
    onOpenFullscreen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
) {
    val (w, h) = videoBubbleSize(videoW, videoH)
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(width = w, height = h)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF33333A), Color(0xFF101014)),
                ),
            )
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onOpenFullscreen() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // v1.1.15: 真封面。画不出来就不画, 留在下面这层深色渐变 + 播放图标兜底 ——
        // 结果只有两种: 看得见画面, 或者看得见一个像播放器的中性占位, 不会是一块纯黑。
        VideoCover(url = url, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }

        DurationBadge(
            text = com.fengling.share.data.VideoProbe.formatDuration(durationSec),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        )
    }
}

/**
 * 视频已被服务端自动清理 (或 video 字段为空) 的灰底占位 —— 不可播放。
 * 契约: msg_type=="video" 但 video 为空 / content 含「[视频已清理]」时用它。
 */
@Composable
fun CleanedVideoPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    onLongPress: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(width = 180.dp, height = 110.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "视频已清理",
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 乐观发送中的视频占位: 本地首帧缩略图 + 圆形百分比进度 + 可取消。
 * 缩略图由 VideoProbe.firstFrame() 在选片后立刻取好 (Bitmap), 这里只负责画。
 */
@Composable
fun VideoSendingBubble(
    thumbnail: Bitmap?,
    progress: Int,
    videoW: Int,
    videoH: Int,
    durationSec: Int,
    cancellable: Boolean,
    onCancel: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    /**
     * 字节已经 100% 发完, 还在等服务端落存储 + 写库 (v1.1.14)。
     * 用户反馈「发到 100% 后还要等很久才真正发出去」, 之前一直停在 100% 像卡死;
     * 现在这一段明确写成「服务器处理中…」, 让这段等待有解释。
     */
    serverProcessing: Boolean = false,
    /**
     * 阶段3 (1.1.14) 上传前压缩的进度 0~100; -1 = 这条没在压缩。
     * 大于等于 0 时进度环和文案都跟着压缩走 (「压缩中 x%」), 压完自动接回上传进度。
     */
    compressPct: Int = -1,
) {
    val (w, h) = videoBubbleSize(videoW, videoH)
    val compressing = compressPct >= 0
    val pct = (if (compressing) compressPct else progress).coerceIn(0, 100)
    Box(
        modifier = modifier
            .size(width = w, height = h)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black)
            .pointerInput(cancellable) {
                detectTapGestures(
                    onTap = { if (cancellable) onCancel() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 遮一层黑, 让进度环在任何画面上都看得清
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.size(46.dp),
                    strokeWidth = 3.dp,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f),
                )
                Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                // 阶段顺序: 压缩中 x% → 上传中 x% → 服务器处理中… (100% 之后还有落盘 + 写库)
                text = when {
                    compressing -> "压缩中 " + pct + "%"
                    serverProcessing -> "服务器处理中…"
                    cancellable && pct > 0 -> "上传中 " + pct + "%"
                    cancellable -> "点击取消"
                    else -> "发送中"
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
            )
        }
        DurationBadge(
            text = com.fengling.share.data.VideoProbe.formatDuration(durationSec),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        )
    }
}

/**
 * 全屏播放 (群聊 / 私聊共用): 全屏黑底 Dialog + ExoPlayer (走 300MB LRU 播放缓存)。
 *
 * 阶段2 换掉原来的 Video View + Media Controller:
 * - 加载中有转圈 + 「视频加载中…」, 不再是一屏纯黑;
 * - 失败自动重试 1 次, 再失败给中文原因 + 错误码 + 「重新加载」;
 * - 退到后台自动暂停 (findLifecycleOwner), 退出对话框释放播放器。
 * 用 usePlatformDefaultWidth=false 让 Dialog 真正铺满屏幕。
 */
@Composable
fun VideoFullscreenDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    if (url.isBlank()) return
    var state by remember(url) { mutableStateOf<VideoPlaybackState>(VideoPlaybackState.Loading) }
    var retryToken by remember(url) { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            CachedVideoSurface(
                url = url,
                modifier = Modifier.fillMaxSize(),
                showController = true,
                retryToken = retryToken,
                onState = { state = it },
            )

            VideoPlaybackOverlay(
                state = state,
                onRetry = { retryToken++ },
                modifier = Modifier.fillMaxSize(),
            )

            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(28.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            )
        }
    }
}
