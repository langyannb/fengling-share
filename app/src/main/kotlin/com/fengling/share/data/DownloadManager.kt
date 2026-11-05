package com.fengling.share.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** 下载任务状态 */
@Serializable
enum class DownloadState {
    /** 排队中 (等并发槽位) */
    PENDING,

    /** 正在下 */
    RUNNING,

    /** 用户暂停 / 进程被杀后恢复成这个状态 */
    PAUSED,

    /** 下完了, 文件可用 */
    DONE,

    /** 失败, 可以重试 */
    FAILED,
}

/**
 * 一个下载任务。
 *
 * [downloadedBytes] / [totalBytes] 一起算进度; 服务器没给 Content-Length 时 [totalBytes] 为 0,
 * 界面就只显示已下载大小 (不显示百分比)。
 */
@Serializable
data class DownloadTask(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    /** 来源页面标题 (下载管理里显示「来自 XX 页面」) */
    val pageTitle: String = "",
    /** 来源页面地址 (下载时带上 Referer, 网盘防盗链要它) */
    val referer: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val state: DownloadState = DownloadState.PENDING,
    val error: String = "",
    /** 落地文件的绝对路径 (DONE 才有意义) */
    val filePath: String = "",
    val createdAt: Long = 0L,
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** 是不是安装包 (决定主按钮是「安装」还是「打开」) */
    val isApk: Boolean
        get() = mimeType.equals("application/vnd.android.package-archive", ignoreCase = true) ||
            fileName.endsWith(".apk", ignoreCase = true)

    val isActive: Boolean get() = state == DownloadState.RUNNING || state == DownloadState.PENDING
}

/**
 * 下载中心 (进程内单例)。
 *
 * 为什么不用系统的 DownloadManager:
 *  - 系统那个把任务丢给别的进程, App 拿不到实时进度, 也没法在列表里展示/暂停/重试;
 *  - 内置浏览器打开的网盘链接通常要带 WebView 的 Cookie + Referer, 系统 DownloadManager
 *    只能带 Cookie, 带不上 Referer, 蓝奏云这类站点会直接 403。
 * 所以自己用 OkHttp 下: 能带全请求头、能实时报进度、能断点续传、能暂停/重试。
 *
 * 任务列表持久化到 SharedPreferences (JSON), 进程被杀后重进 App 还能看到记录;
 * 当时正在下的任务会回落成 [DownloadState.PAUSED], 用户点一下继续 (Range 续传)。
 */
object Downloads {

    private const val PREFS = "fengling_downloads"
    private const val KEY_TASKS = "tasks"
    private const val MAX_CONCURRENT = 2

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // 大文件 + 慢网盘: 读超时给足, 不然下到一半被判超时
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Long, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())

    /** 全部任务, 新的在前 */
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private lateinit var appContext: Context
    private var initialized = false

    /** 在 Application/Activity 启动时调一次 (幂等) */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        val saved = runCatching {
            val raw = prefs().getString(KEY_TASKS, null) ?: return@runCatching emptyList()
            json.decodeFromString<List<DownloadTask>>(raw)
        }.getOrDefault(emptyList())
        // 上次没下完的: 一律回落成暂停 (不能自动续, 用户可能已经不想下了 / 也可能没网)
        _tasks.value = saved.map {
            if (it.state == DownloadState.RUNNING || it.state == DownloadState.PENDING) {
                it.copy(state = DownloadState.PAUSED)
            } else {
                it
            }
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist() {
        runCatching {
            prefs().edit()
                .putString(KEY_TASKS, json.encodeToString(_tasks.value))
                .apply()
        }
    }

    /** 列表变化统一走这里: 改内存 + 落盘 + 刷通知 */
    private fun update(block: (List<DownloadTask>) -> List<DownloadTask>) {
        _tasks.value = block(_tasks.value)
        persist()
        DownloadNotifier.sync(appContext, _tasks.value)
    }

    private fun patch(id: Long, transform: (DownloadTask) -> DownloadTask) {
        update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /** 下载目录 (外部私有目录, 不需要存储权限, 也能被 FileProvider 分享出去) */
    private fun downloadDir(): File {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun targetFile(task: DownloadTask): File {
        val safe = task.fileName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "download_${task.id}" }
        return File(downloadDir(), safe)
    }

    /**
     * 加一个任务并立刻开始 (受并发上限约束, 超了就排队)。
     *
     * @return 任务 id
     */
    fun enqueue(
        url: String,
        fileName: String,
        mimeType: String,
        pageTitle: String = "",
        referer: String = "",
    ): Long {
        val id = System.currentTimeMillis() * 1000 + (0..999).random()
        val task = DownloadTask(
            id = id,
            url = url,
            fileName = fileName,
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            pageTitle = pageTitle,
            referer = referer,
            state = DownloadState.PENDING,
            createdAt = System.currentTimeMillis(),
        )
        update { listOf(task) + it }
        pump()
        return id
    }

    /** 有空位就把排队的任务拉起来 */
    private fun pump() {
        val active = _tasks.value.count { it.state == DownloadState.RUNNING }
        if (active >= MAX_CONCURRENT) return
        val next = _tasks.value
            .filter { it.state == DownloadState.PENDING }
            .take(MAX_CONCURRENT - active)
        next.forEach { start(it.id) }
    }

    private fun start(id: Long) {
        if (jobs[id]?.isActive == true) return
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        patch(id) { it.copy(state = DownloadState.RUNNING, error = "") }
        jobs[id] = scope.launch {
            runCatching { download(task) }
                .onFailure { e ->
                    // 不把异常原文抛给用户: URL 里可能带服务器地址 / 签名
                    val msg = when {
                        e is java.io.IOException -> "网络中断, 可重试"
                        else -> "下载失败, 可重试"
                    }
                    patch(id) { it.copy(state = DownloadState.FAILED, error = msg) }
                }
            jobs.remove(id)
            pump()
        }
    }

    private suspend fun download(task: DownloadTask) {
        val file = targetFile(task)
        val existing = if (file.exists()) file.length() else 0L
        // 续传: 已经有半截文件就带 Range 从断点接
        val builder = Request.Builder()
            .url(task.url)
            .header("User-Agent", USER_AGENT)
        if (task.referer.isNotBlank()) builder.header("Referer", task.referer)
        val cookie = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(task.url)
        }.getOrNull()
        if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
        if (existing > 0L) builder.header("Range", "bytes=$existing-")

        val response = client.newCall(builder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                // 416 = 断点位置已经超过文件尾 (多半是文件变了), 从头再来一次
                if (resp.code == 416 && existing > 0L) {
                    file.delete()
                    patch(task.id) { it.copy(downloadedBytes = 0L, totalBytes = 0L) }
                    download(task.copy(downloadedBytes = 0L, totalBytes = 0L))
                    return
                }
                patch(task.id) {
                    it.copy(state = DownloadState.FAILED, error = "下载失败 (HTTP ${resp.code})")
                }
                return
            }
            val body = resp.body ?: run {
                patch(task.id) { it.copy(state = DownloadState.FAILED, error = "服务器没有返回内容") }
                return
            }
            val append = resp.code == 206 && existing > 0L
            val startAt = if (append) existing else 0L
            val total = body.contentLength().let { if (it > 0L) it + startAt else 0L }
            patch(task.id) {
                it.copy(
                    totalBytes = total,
                    downloadedBytes = startAt,
                    state = DownloadState.RUNNING,
                    filePath = file.absolutePath,
                )
            }

            RandomAccessFile(file, "rw").use { raf ->
                if (!append) raf.setLength(0L)
                raf.seek(startAt)
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = startAt
                    var lastPush = 0L
                    while (true) {
                        // 用户点了暂停 / 任务被删: 立刻收手, 文件留着下次续传
                        val now = _tasks.value.firstOrNull { it.id == task.id }
                        if (now == null || now.state == DownloadState.PAUSED) {
                            patch(task.id) { it.copy(downloadedBytes = done) }
                            return
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        done += read
                        // 每 120ms 推一次进度: 每 64KB 都重组一次列表没必要, 还费电
                        if (System.currentTimeMillis() - lastPush > 120L) {
                            lastPush = System.currentTimeMillis()
                            patch(task.id) { it.copy(downloadedBytes = done) }
                        }
                    }
                    patch(task.id) { it.copy(downloadedBytes = done) }
                }
            }
        }

        val finished = _tasks.value.firstOrNull { it.id == task.id } ?: return
        if (finished.state == DownloadState.PAUSED) return
        patch(task.id) {
            it.copy(
                state = DownloadState.DONE,
                downloadedBytes = if (it.totalBytes > 0L) it.totalBytes else it.downloadedBytes,
                filePath = file.absolutePath,
                error = "",
            )
        }
    }

    /** 暂停 (文件保留, 继续时按 Range 续传) */
    fun pause(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state != DownloadState.RUNNING && task.state != DownloadState.PENDING) return
        patch(id) { it.copy(state = DownloadState.PAUSED) }
        jobs.remove(id)?.cancel()
        pump()
    }

    /** 继续 / 重试 */
    fun resume(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state == DownloadState.DONE) return
        if (task.state == DownloadState.FAILED) {
            // 失败重试从头来: 半截文件不一定完整
            targetFile(task).delete()
            patch(id) { it.copy(downloadedBytes = 0L, totalBytes = 0L, state = DownloadState.PENDING, error = "") }
        } else {
            patch(id) { it.copy(state = DownloadState.PENDING, error = "") }
        }
        pump()
    }

    /** 全部暂停 */
    fun pauseAll() {
        _tasks.value.filter { it.isActive }.forEach { pause(it.id) }
    }

    /** 删记录 (顺带删文件) */
    fun remove(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        jobs.remove(id)?.cancel()
        runCatching { targetFile(task).delete() }
        update { list -> list.filterNot { it.id == id } }
        pump()
    }

    /**
     * 清掉已完成/失败的记录, **连文件一起删**。
     *
     * 为什么不只删记录: 文件落在 App 私有外部目录 (getExternalFilesDir),
     * 记录一没就再也没有入口能访问它 —— 用户的手机上只会多出一堆看不见的垃圾。
     * 想留着文件的场景是「下完就去打开/安装」, 那属于当下动作, 不是事后翻列表。
     */
    fun clearFinished() {
        val gone = _tasks.value.filter {
            it.state == DownloadState.DONE || it.state == DownloadState.FAILED
        }
        gone.forEach { runCatching { targetFile(it).delete() } }
        update { list -> list.filter { it.isActive || it.state == DownloadState.PAUSED } }
    }

    /** 已完成任务对应的文件 (不存在就返回 null) */
    fun fileOf(task: DownloadTask): File? {
        val path = task.filePath.ifBlank { targetFile(task).absolutePath }
        val f = File(path)
        return if (f.exists()) f else null
    }

    /** 安装 APK; 没「安装未知应用」权限时跳设置页 */
    fun installApk(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        }.getOrDefault(false)
    }

    /** 非 APK: 交给系统「打开方式」 */
    fun openFile(context: Context, task: DownloadTask, file: File): Boolean {
        if (task.isApk) return installApk(context, file)
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, task.mimeType.ifBlank { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        }.getOrDefault(false)
    }

    /** 和内置浏览器一致的 UA, 免得某些站点按 UA 下发不同的（不可下的）页面 */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
}
