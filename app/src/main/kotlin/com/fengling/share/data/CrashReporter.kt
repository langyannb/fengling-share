package com.fengling.share.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.fengling.share.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 崩溃日志上报:
 * - 未捕获异常 (主线程 + 子线程) 落盘到 filesDir/crash/, 然后走系统默认崩溃流程
 * - 下次启动 uploadPending() 把待上报文件 POST 到服务器, 成功即删除
 * 服务器: api.php?action=crash_report (管理端「崩溃」tab 查看)
 */
object CrashReporter {

    private val API_URL = ServerConfig.CRASH_URL
    private const val MAX_FILES = 20
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var inited = false

    /** 注册全局未捕获异常处理器 (MainActivity.onCreate 第一行调用) */
    fun init(context: Context) {
        if (inited) return
        inited = true
        val app = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(app, thread, throwable)
            } catch (_: Throwable) {
                // 落盘失败不影响崩溃流程
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    /** 崩溃时写入本地文件 (含设备/系统/App 信息 + 堆栈) */
    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, "crash").apply { mkdirs() }
        // 保留最近 MAX_FILES 份, 防止反复崩溃撑爆存储
        val files = dir.listFiles { f -> f.name.endsWith(".txt") }?.sortedBy { it.lastModified() } ?: emptyList()
        if (files.size >= MAX_FILES) {
            files.take(files.size - MAX_FILES + 1).forEach { it.delete() }
        }
        val sb = StringBuilder()
        sb.append("时间: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append('\n')
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(')').append('\n')
        sb.append("App: v").append(BuildConfig.VERSION_NAME).append(" (code ").append(BuildConfig.VERSION_CODE).append(')').append('\n')
        sb.append("线程: ").append(thread.name).append('\n')
        sb.append('\n')
        sb.append(Log.getStackTraceString(throwable))
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        File(dir, "crash_$stamp.txt").writeText(sb.toString())
    }

    /** 下次启动上传所有待上报崩溃日志, 成功即删除本地文件 (网络失败保留, 下次再传) */
    suspend fun uploadPending(context: Context) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "crash")
        val files = dir.listFiles { f -> f.name.endsWith(".txt") } ?: return@withContext
        for (f in files) {
            try {
                val text = f.readText()
                if (text.isBlank()) {
                    f.delete()
                    continue
                }
                val lines = text.split("\n")
                val device = lines.firstOrNull { it.startsWith("设备:") }?.substringAfter("设备:")?.trim() ?: ""
                val androidVersion = lines.firstOrNull { it.startsWith("Android:") }?.substringAfter("Android:")?.trim() ?: ""
                val appVersion = lines.firstOrNull { it.startsWith("App:") }?.substringAfter("App:")?.trim() ?: ""
                val body = JSONObject().apply {
                    put("device", device)
                    put("android_version", androidVersion)
                    put("app_version", appVersion)
                    put("stack", text)
                }.toString().toRequestBody(JSON)
                val req = Request.Builder().url(API_URL).post(body).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) f.delete()
                }
            } catch (_: Throwable) {
                // 网络/解析失败: 保留文件下次再传
            }
        }
    }
}
