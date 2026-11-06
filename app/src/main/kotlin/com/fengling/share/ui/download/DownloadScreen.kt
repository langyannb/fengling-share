package com.fengling.share.ui.download

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.DownloadState
import com.fengling.share.data.DownloadTask
import com.fengling.share.data.Downloads
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.pressScaleEffect
import com.fengling.share.ui.theme.LocalAnimProgressSmooth
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载管理 (v1.1.23 改版)。
 *
 * 内置浏览器里点到的文件链接 (蓝奏云 / 直链 / 网盘) 都落到这里:
 * 列表显示进度, 点卡片能暂停 / 继续 / 重试 / 安装, 右上角能一键暂停全部或清空已完成的记录。
 *
 * 改版做了什么:
 *  - 分三段 (正在下载 / 已暂停 / 已完成), 不再让完成任务和进行中任务混在一列里
 *  - 顶部汇总卡: 进行中任务数 + 总进度条, 一眼看到「还要多久」
 *  - 进度条改成插值动画 (数据每 120ms 推一次, 直接给值会一跳一跳)
 *  - 空态换成图标 + 两行说明, 不再是两行干巴巴的文字
 */
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tasks by Downloads.tasks.collectAsState()

    // 活动的排前面, 同组内新的在前
    val sorted = remember(tasks) {
        tasks.sortedWith(
            compareByDescending<DownloadTask> { it.isActive }
                .thenByDescending { it.state == DownloadState.PAUSED }
                .thenByDescending { it.createdAt },
        )
    }
    val active = remember(sorted) {
        sorted.filter { it.state == DownloadState.RUNNING || it.state == DownloadState.PENDING }
    }
    val paused = remember(sorted) {
        sorted.filter { it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED }
    }
    val done = remember(sorted) { sorted.filter { it.state == DownloadState.DONE } }
    val hasFinished = done.isNotEmpty() || paused.any { it.state == DownloadState.FAILED }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "下载管理",
                onBack = onBack,
                actions = {
                    if (active.isNotEmpty()) {
                        TextButton(
                            text = "全部暂停",
                            onClick = { Downloads.pauseAll() },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .appGradientBackground(),
        ) {
            if (sorted.isEmpty()) {
                EmptyDownloads()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = GlassSpacing.page,
                        end = GlassSpacing.page,
                        top = 6.dp,
                        bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(GlassSpacing.cardGap),
                ) {
                    if (active.isNotEmpty()) {
                        item(key = "summary") { DownloadSummary(active) }
                    }

                    if (active.isNotEmpty()) {
                        item(key = "h_active") { SectionHeader("正在下载", active.size) }
                        items(active, key = { it.id }) { task ->
                            DownloadRow(
                                task = task,
                                onToggle = { Downloads.pause(task.id) },
                                onOpen = {},
                                onRemove = { Downloads.remove(task.id) },
                            )
                        }
                    }

                    if (paused.isNotEmpty()) {
                        item(key = "h_paused") { SectionHeader("已暂停", paused.size) }
                        items(paused, key = { it.id }) { task ->
                            DownloadRow(
                                task = task,
                                onToggle = { Downloads.resume(task.id) },
                                onOpen = {},
                                onRemove = { Downloads.remove(task.id) },
                            )
                        }
                    }

                    if (done.isNotEmpty()) {
                        item(key = "h_done") { SectionHeader("已完成", done.size) }
                        items(done, key = { it.id }) { task ->
                            DownloadRow(
                                task = task,
                                onToggle = {},
                                onOpen = {
                                    val file = Downloads.fileOf(task) ?: return@DownloadRow
                                    Downloads.openFile(context, task, file)
                                },
                                onRemove = { Downloads.remove(task.id) },
                            )
                        }
                    }

                    if (hasFinished) {
                        item(key = "clear_finished") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(
                                    // 会连文件一起删: 文件在 App 私有目录, 记录删掉就再也访问不到了
                                    text = "清除已完成(含文件)",
                                    onClick = { Downloads.clearFinished() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 空态: 图标 + 说明, 比两行干文字友好 */
@Composable
private fun EmptyDownloads() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.75f),
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "还没有下载任务",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "在内置浏览器里点文件链接\n就会出现在这里",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** 顶部汇总卡: 进行中任务数 + 合计进度 */
@Composable
private fun DownloadSummary(active: List<DownloadTask>) {
    // 合计进度: 只统计知道总大小的任务, 否则分母没意义
    val known = active.filter { it.totalBytes > 0L }
    val overall = if (known.isEmpty()) 0f else {
        val totalBytes = known.sumOf { it.totalBytes }
        val doneBytes = known.sumOf { it.downloadedBytes }
        if (totalBytes <= 0L) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }
    // 设置里关掉「平滑进度」时直接用原始值 (进度随数据跳)
    val smooth = LocalAnimProgressSmooth.current
    val animatedOverall by animateFloatAsState(
        targetValue = overall,
        animationSpec = tween(500, easing = LinearEasing),
        label = "dlOverall",
    )
    val pct = if (smooth) animatedOverall else overall

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(radius = GlassRadius.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${active.size} 个任务进行中",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (known.isNotEmpty()) {
                Text(
                    text = "${(pct * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        if (known.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
                progress = pct,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = MiuixTheme.colorScheme.primary,
                    backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                ),
                height = 5.dp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "${fmtSize(known.sumOf { it.downloadedBytes })} / " +
                    fmtSize(known.sumOf { it.totalBytes }),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/** 分组标题: 左边短竖条 + 名称 + 数量 */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = accentOf(task)

    // 数据每 120ms 推一次, 直接画会一跳一跳; 插值 400ms 追上去看起来是连续的。
    // 用户在设置里关掉「平滑进度」时退回原始值。
    val smooth = LocalAnimProgressSmooth.current
    val animatedProgress by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = tween(400, easing = LinearEasing),
        label = "dlRowProgress",
    )
    val shown = if (smooth) animatedProgress else task.progress
    val accentColor by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(260),
        label = "dlRowAccent",
    )

    val showBar = task.state == DownloadState.RUNNING ||
        task.state == DownloadState.PENDING ||
        (task.state == DownloadState.PAUSED && task.totalBytes > 0L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleEffect(label = "downloadRowPress")
            .glassCard(radius = GlassRadius.card)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 类型图标: 一眼区分 apk / 图片 / 视频 / 音乐 / 其它, 颜色也按类型分
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(GlassRadius.icon))
                    .background(accentColor.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconOf(task),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = statusLine(task),
                    fontSize = 11.sp,
                    color = if (task.state == DownloadState.FAILED) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // 主操作: 进行中 = 暂停; 暂停/失败 = 继续; 完成 = 打开/安装
            val mainAction: (() -> Unit)? = when (task.state) {
                DownloadState.DONE -> onOpen
                DownloadState.FAILED -> onToggle
                DownloadState.PAUSED -> onToggle
                DownloadState.RUNNING, DownloadState.PENDING -> onToggle
            }
            val mainIcon: ImageVector = when (task.state) {
                DownloadState.DONE -> if (task.isApk) Icons.Filled.Android else Icons.Filled.Download
                DownloadState.FAILED -> Icons.Filled.PlayArrow
                DownloadState.PAUSED -> Icons.Filled.PlayArrow
                DownloadState.RUNNING, DownloadState.PENDING -> Icons.Filled.Pause
            }
            val mainLabel = when (task.state) {
                DownloadState.DONE -> if (task.isApk) "安装" else "打开"
                DownloadState.FAILED -> "重试"
                DownloadState.PAUSED -> "继续"
                DownloadState.RUNNING, DownloadState.PENDING -> "暂停"
            }
            if (mainAction != null) {
                val primary = MiuixTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (task.state == DownloadState.DONE) primary
                            else primary.copy(alpha = 0.13f),
                        )
                        .clickable { mainAction() }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = mainIcon,
                            contentDescription = null,
                            tint = if (task.state == DownloadState.DONE) Color.White else primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = mainLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (task.state == DownloadState.DONE) Color.White else primary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(2.dp))
            Box {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { menuOpen = true }
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("删除记录和文件") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }

        // 进度条: 进行中走插值, 暂停保留一条静态进度让用户知道停在哪
        if (showBar) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
                // 服务器没给总大小 -> null 走不确定进度条
                progress = if (task.totalBytes > 0L) shown else null,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = if (task.state == DownloadState.PAUSED) {
                        accentColor.copy(alpha = 0.55f)
                    } else {
                        accentColor
                    },
                    backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                ),
                height = 4.dp,
            )
        }
    }
}

/** 按文件类型取强调色 (apk 走主题色, 其它类型用各自好认的颜色) */
private fun accentOf(task: DownloadTask): Color {
    val mime = task.mimeType.lowercase()
    val name = task.fileName.lowercase()
    return when {
        task.isApk -> Color(0xFF4C6FFF)
        mime.startsWith("image/") || name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) ->
            Color(0xFF2FA46B)
        mime.startsWith("video/") || name.matches(Regex(".*\\.(mp4|mkv|avi|mov|webm)$")) ->
            Color(0xFFE5484D)
        mime.startsWith("audio/") || name.matches(Regex(".*\\.(mp3|flac|wav|m4a|ogg)$")) ->
            Color(0xFF9B51E0)
        else -> Color(0xFF6B7280)
    }
}

/** 状态行文案: 大小 / 进度 / 来源 */
private fun statusLine(task: DownloadTask): String {
    val sizePart = when {
        task.totalBytes > 0L ->
            "${fmtSize(task.downloadedBytes)} / ${fmtSize(task.totalBytes)}"
        task.downloadedBytes > 0L -> fmtSize(task.downloadedBytes)
        else -> ""
    }
    return when (task.state) {
        DownloadState.DONE -> sizePart.ifBlank { "已完成" }
        DownloadState.FAILED -> task.error.ifBlank { "下载失败" }
        DownloadState.PAUSED -> listOf("已暂停", sizePart).filter { it.isNotBlank() }.joinToString(" · ")
        DownloadState.PENDING -> "等待中"
        DownloadState.RUNNING -> listOf(sizePart, "${(task.progress * 100).toInt()}%")
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
}

private fun fmtSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}

private fun iconOf(task: DownloadTask): ImageVector {
    val mime = task.mimeType.lowercase()
    val name = task.fileName.lowercase()
    return when {
        task.isApk -> Icons.Filled.Android
        mime.startsWith("image/") || name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) ->
            Icons.Filled.Image
        mime.startsWith("video/") || name.matches(Regex(".*\\.(mp4|mkv|avi|mov|webm)$")) ->
            Icons.Filled.VideoFile
        mime.startsWith("audio/") || name.matches(Regex(".*\\.(mp3|flac|wav|m4a|ogg)$")) ->
            Icons.Filled.MusicNote
        else -> Icons.Filled.Description
    }
}
