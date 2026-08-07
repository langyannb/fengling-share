package com.fengling.share.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.fengling.share.R

@SuppressLint("ViewConstructor")
class BgEffectView(context: Context?, mode: Int) : LinearLayout(context) {
    private var mBgEffectView: View? = null
    private var mBgEffectPainter: BgEffectPainter? = null
    private val startTime = System.nanoTime().toFloat()
    private val mHandler = Handler(Looper.getMainLooper())
    private var colorMode = 1
    var runnableBgEffect: Runnable = object : Runnable {
        override fun run() {
            // 渲染循环加固: View detach/尺寸异常时不再 postDelayed, 停止循环 (降级静态背景), 避免闪退
            try {
                val painter = mBgEffectPainter ?: return
                val view = mBgEffectView ?: return
                painter.setAnimTime((((System.nanoTime().toFloat()) - startTime) / 1.0E9f) % 62.831852f)
                painter.setResolution(floatArrayOf(view.width.toFloat(), view.height.toFloat()))
                painter.updateMaterials()
                view.setRenderEffect(painter.renderEffect)
            } catch (_: Throwable) {
                return
            }
            mHandler.postDelayed(runnableBgEffect, 16L)
        }
    }
    init {
        colorMode = mode
        BgEffect(context)
    }
    fun BgEffect(context: Context?) {
        mBgEffectView = LayoutInflater.from(context).inflate(R.layout.layout_effect_bg, this, true)
        mBgEffectView!!.post(Runnable {
            if (context != null) {
                try {
                    val appContext = context.applicationContext
                    mBgEffectPainter = BgEffectPainter(appContext)
                    mBgEffectPainter!!.showRuntimeShader(appContext, mBgEffectView!!, colorMode)
                    mHandler.post(runnableBgEffect)
                } catch (_: Throwable) {
                    // GPU 不支持 AGSL/RuntimeShader 或 View 未布局 (荣耀等设备实锤 NPE):
                    // 降级为普通背景, 不再启动渲染循环, 避免闪退
                    mBgEffectPainter = null
                }
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 尺寸变化时刷新 (切页回来 View 尺寸恢复, 避免白屏)
        mBgEffectPainter?.let { painter ->
            if (w > 0 && h > 0) {
                painter.setResolution(floatArrayOf(w.toFloat(), h.toFloat()))
            }
        }
    }
    fun updateMode(mode: Int) {
        if (mode != colorMode) {
            colorMode = mode
            // 降级模式 (GPU 不支持 AGSL) 时 painter 为 null, 直接忽略
            mBgEffectPainter?.updateMode(mode)
        }
    }
}
