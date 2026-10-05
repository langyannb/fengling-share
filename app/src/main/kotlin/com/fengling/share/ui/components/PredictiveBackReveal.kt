package com.fengling.share.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
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
 * 可预测式返回的「上一级画面」快照 (v1.1.7)
 *
 * 为什么要自己画: 系统的可预测式返回 (predictive back) 只会把手势进度 0..1 喂给 App,
 * **上一级界面长什么样得 App 自己呈现**。而 NavHost 在手势进行中只组合当前目的地,
 * 上一级根本没被组合 —— 于是侧滑只能露出一片背景色, 做不到 ColorOS 16 那种
 * 「从哪来回哪去 / 侧滑一半就能看见上一级内容」。
 *
 * 做法 (按路由存快照, 多级返回也不会串图):
 *  1. 每次 push 之前调用 [captureBeforeNavigate] 把当前窗口截成位图, 按「目标路由」暂存;
 *  2. 目的地真的切过去后, 这张图转正进 shots (最多 4 张, 超出丢最旧), 并设为当前路由的 behind;
 *  3. 手势进行中 [progress] > 0 时, 挂在 NavHost 上的 [backRevealBackdrop] 把 behind
 *     铺在当前页面下方: 0.90 -> 1.0 放大 + 轻微视差 + 由暗转亮;
 *  4. 松手回弹 / 提交后由系统转场接管, 观感连贯。
 */
object BackReveal {
    private const val TAG = "FLS_BACK"

    /** 同时最多保留几层快照 (每张约 1.7MB, 4 张足够覆盖多级返回) */
    private const val MAX_SHOTS = 4

    /** 快照按窗口尺寸缩到 40% 再存, 铺回时放大, 视觉够用又省内存 */
    private const val SNAPSHOT_SCALE = 0.4f

    /** 手势进度 0..1 (由 rememberPredictiveBackProgress 写入) */
    var progress by mutableFloatStateOf(0f)
        internal set

    /** 当前页面「下面」那一屏的画面 (手势进行中显示) */
    var behind by mutableStateOf<ImageBitmap?>(null)
        private set

    private val shots = LinkedHashMap<String, ImageBitmap>()
    private var pending: ImageBitmap? = null
    private var pendingRoute: String? = null

    /** 导航前同步截屏 (在点击所在的线程上跑, 十几毫秒; 手势进行中不截, 免得截到自己的跟手状态) */
    fun captureBeforeNavigate(context: Context, targetRoute: String) {
        if (progress > 0f) return
        val shot = captureWindow(context) ?: return
        pending = shot
        pendingRoute = targetRoute
        Log.d(TAG, "已记录上一级画面 -> " + targetRoute)
    }

    /** 目的地变化回调: pending 与路由配对成功就转正, 并把 behind 换成当前路由自己的上一级画面 */
    fun onDestinationChanged(route: String?) {
        val shot = pending
        val shotRoute = pendingRoute
        pending = null
        pendingRoute = null
        if (shot != null && route != null && route == shotRoute) {
            shots[route] = shot
            while (shots.size > MAX_SHOTS) {
                val oldest = shots.keys.firstOrNull() ?: break
                shots.remove(oldest)
            }
        }
        behind = if (route != null) shots[route] else null
    }

    private fun captureWindow(context: Context): ImageBitmap? = try {
        val activity = context.findActivity()
        val view = activity?.window?.decorView
        val w = view?.width ?: 0
        val h = view?.height ?: 0
        if (view == null || w <= 0 || h <= 0) {
            null
        } else {
            val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(full))
            val sw = (w * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            val sh = (h * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(full, sw, sh, true)
            if (small !== full) full.recycle()
            small.asImageBitmap()
        }
    } catch (t: Throwable) {
        Log.w(TAG, "截屏失败: " + t.message)
        null
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
