package com.fengling.share.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ApkDownloader - APK 下载安装 (OShin 更新页同款)
 * 下载到 app 外部缓存目录, 完成后 FileProvider 拉起安装
 */
object ApkDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow(-1) // -1=空闲, 0-100=下载中, 200=完成
    val progress: StateFlow<Int> = _progress

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var downloadedFile: File? = null
        private set

    fun reset() {
        _progress.value = -1
        _status.value = ""
        _error.value = null
        downloadedFile = null
    }

    /** 下载 APK 到缓存目录 */
    suspend fun download(context: Context, url: String) = withContext(Dispatchers.IO) {
        try {
            _error.value = null
            _status.value = "正在下载..."
            _progress.value = 0

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _error.value = "下载失败: HTTP ${response.code}"
                _status.value = ""
                _progress.value = -1
                return@withContext
            }

            val total = response.body?.contentLength() ?: -1L
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val file = File(dir, "fengling_update.apk")
            FileOutputStream(file).use { output ->
                val body = response.body ?: return@withContext
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            _progress.value = ((downloaded * 100) / total).toInt()
                        }
                    }
                }
            }
            downloadedFile = file
            _progress.value = 200
            _status.value = "下载完成"
        } catch (e: Exception) {
            // 不显示 e.message: 下载 URL 含服务器 IP, 失败提示不能泄露
            _error.value = if (e is java.io.IOException) "下载失败，请检查网络后重试" else "下载失败，请稍后重试"
            _status.value = ""
            _progress.value = -1
        }
    }

    /** 拉起安装 (FileProvider), 无权限时跳设置引导开启 */
    fun install(context: Context, file: File): Boolean {
        // Android 8+ 需要"安装未知应用"权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _error.value = "请先允许安装未知应用"
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            _error.value = "安装失败: ${e.message}"
            false
        }
    }
}
