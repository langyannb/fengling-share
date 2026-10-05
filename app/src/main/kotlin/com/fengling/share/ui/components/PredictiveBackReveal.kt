package com.fengling.share.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 可预测式返回的「上一级画面」快照 (v1.1.9)
 *
 * 为什么要自己画: 系统的可预测式返回只把手势进度 0..1 喂给 App, **上一级界面长什么样
 * 得 App 自己呈现**。NavHost 在手势进行中只组合当前目的地, 上一级根本没被组合 ——
 * 不画就是一片背景色, 做不到 ColorOS 16 那种「从哪来回哪去 / 侧滑一半就看见上一级」。
 *
 * v1.1.7 用 View.draw(Canvas) 软件绘制抓屏 —— 画不出 Compose 的硬件层与毛玻璃
 * (RenderEffect), 抓出来是空白, 表现就是「返回过程中一片空白」。v1.1.9 改用
 * PixelCopy 直接取窗口的真实帧 (含毛玻璃), 抓屏是异步的 (1~2 帧 ≈ 30ms),
 * 所以导航动作挪到截图完成之后。
 */
object BackReveal {
    private const val TAG = "FLS_BACK"

    /** 同时最多保留几层快照 (0.6 倍约 5MB 一张, 实测返回栈一般就到 2 层) */
    private const val MAX_SHOTS = 2

    /** 快照按窗口尺寸缩到 60% 再存: 铺回时放到全屏只放大 1.67 倍, 肉眼看不出糊 */
    private const val SNAPSHOT_SCALE = 0.6f

    /** 手势进度 0..1 (由 rememberPredictiveBackProgress 写入) */
    var progress by mutableFloatStateOf(0f)
        internal set

    /** 当前页面「下面」那一屏的画面 (手势进行中显示) */
    var behind by mutableStateOf<ImageBitmap?>(null)
        private set

    private val shots = LinkedHashMap<String, ImageBitmap>()
    private var pending: ImageBitmap? = null
    private var capturing = false

    /**
     * 导航前把当前这一屏截下来, 截好后再执行 [onReady] (里面才真正 navigate)。
     * 手势进行中 / 正在截图 / 窗口还没尺寸 都直接放行, 不阻塞导航。
     */
    fun captureBeforeNavigate(context: Context, targetRoute: String, onReady: () -> Unit) {
        val activity = context.findActivity()
        val view = activity?.window?.decorView
        val w = view?.width ?: 0
        val h = view?.height ?: 0
        if (progress > 0f || capturing || activity == null || view == null || w <= 0 || h <= 0) {
            onReady()
            return
        }
        val full = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            Log.w(TAG, "分配快照失败: " + t.message)
            onReady()
            return
        }
        capturing = true
        val done = { result: Boolean ->
            try {
                if (result) {
                    // 缩到 60%: 内存从 13MB 降到 5MB, 铺回时放到全屏仍然清楚
                    val sw = (w * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
                    val sh = (h * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(full, sw, sh, true)
                    if (small !== full) full.recycle()
                    pending = small.asImageBitmap()
                    Log.d(TAG, "已记录上一级画面 -> " + targetRoute)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "缩放缓照失败: " + t.message)
            } finally {
                capturing = false
                onReady()
            }
        }
        try {
            PixelCopy.request(
                activity.window,
                full,
                { res ->
                    if (res == PixelCopy.SUCCESS) {
                        done(true)
                    } else {
                        Log.w(TAG, "PixelCopy 失败: " + res)
                        done(false)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "PixelCopy 异常: " + t.message)
            done(false)
        }
    }

    /**
     * 目的地变化: 刚截下的那一帧就是「上一级画面」, 直接记到新路由名下。
     *
     * v1.1.10 修: 不再拿路由字符串配对 —— 带参数的导航目的地 (例如
     * user_profile/{userId}/{groupId}) 在 destination.route 里是**模板**,
     * 而 captureBeforeNavigate 拿到的是填好参数的 user_profile/17/1, 两者永远配不上,
     * behind 一直是 null, 表现就是用户看到的「返回过程中一片空白」。
     */
    fun onDestinationChanged(route: String?) {
        val shot = pending
        pending = null
        if (shot != null && route != null) {
            shots.remove(route)
            shots[route] = shot
            while (shots.size > MAX_SHOTS) {
                val oldest = shots.keys.firstOrNull() ?: break
                shots.remove(oldest)
            }
        }
        behind = if (route != null) shots[route] else null
        // 新页面接管: 手势进度归零。被弹掉的旧页面用的是自己那份 Animatable,
        // 不受这里影响 (它在转场里依然是滑出去的状态, 不会跳回来)。
        progress = 0f
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 挂在 NavHost 上: 手势进行中, 先在页面内容下方把「上一级画面」画出来 */
fun Modifier.backRevealBackdrop(): Modifier = drawBehind { drawRevealBackdrop() }

internal fun DrawScope.drawRevealBackdrop() {
    val p = BackReveal.progress
    if (p <= 0f) return
    val shot = BackReveal.behind ?: return
    val q = p.coerceIn(0f, 1f)

    // 上一级: 从 0.90 放大回 1.0, 并带一点点左向视差 (跟手越深越"到位")
    val scale = 0.90f + 0.10f * q
    val dstW = size.width * scale
    val dstH = size.height * scale
    val left = (size.width - dstW) / 2f - size.width * 0.05f * (1f - q)
    val top = (size.height - dstH) / 2f
    drawImage(
        image = shot,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(shot.width, shot.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
    )

    // 越接近全屏越亮 (和系统一致: 上一级是"正在被拉回来"的那一屏)
    val scrim = 0.28f * (1f - q)
    if (scrim > 0.001f) {
        drawRect(color = Color.Black, topLeft = Offset.Zero, size = size, alpha = scrim)
    }
}
