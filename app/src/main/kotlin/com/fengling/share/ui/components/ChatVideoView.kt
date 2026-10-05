package com.fengling.share.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
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
 * 视频消息气泡 (群聊 / 私聊共用)
 *
 * - 画面用 **AndroidView { VideoView }** (不加 ExoPlayer/Media3 依赖), 停在首帧当封面:
 *   静音 + seekTo(1) —— 不 seek 的话很多视频第一帧是黑的。
 * - 点一下 → 交给 onOpenFullscreen() 打开全屏播放 (全屏 Dialog + VideoView + MediaController)。
 * - 长按 → onLongPress() 走原有的撤回/删除菜单 (与图片消息同一套)。
 * - 真机未实测: 只能说明「代码路径已接好」, 手势/首帧表现以装机后为准。
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
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(width = w, height = h)
            .clip(shape)
            .background(Color.Black)
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { if (!failed) onOpenFullscreen() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!failed) {
            key(url) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx: Context ->
                        VideoView(ctx).apply {
                            setBackgroundColor(AndroidColor.BLACK)
                            // 气泡里只当「会动的封面」: 静音 + 不自动播放
                            setOnPreparedListener { mp ->
                                runCatching {
                                    mp.setVolume(0f, 0f)
                                    mp.isLooping = false
                                }
                                runCatching { seekTo(1) }
                                ready = true
                            }
                            setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            runCatching { setVideoURI(Uri.parse(url)) }
                        }
                    },
                )
            }
        }

        if (!ready && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        if (failed) {
            Text(
                text = "视频加载失败",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
        } else {
            // 中间的播放按钮: 纯视觉提示, 点击由外层 Box 的 detectTapGestures 接
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
) {
    val (w, h) = videoBubbleSize(videoW, videoH)
    val pct = progress.coerceIn(0, 100)
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
                text = if (cancellable) "点击取消" else "发送中",
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
 * 全屏播放 (群聊 / 私聊共用): 全屏黑底 Dialog + VideoView + MediaController。
 * 用 usePlatformDefaultWidth=false 让 Dialog 真正铺满屏幕。
 */
@Composable
fun VideoFullscreenDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    if (url.isBlank()) return
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            key(url) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx: Context ->
                        VideoView(ctx).apply {
                            setBackgroundColor(AndroidColor.BLACK)
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnPreparedListener { mp ->
                                runCatching { mp.isLooping = false }
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                Toast.makeText(ctx, "视频播放失败", Toast.LENGTH_SHORT).show()
                                true
                            }
                            runCatching { setVideoURI(Uri.parse(url)) }
                        }
                    },
                )
            }
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
