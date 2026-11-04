package com.fengling.share.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.pressScaleEffect
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载管理。
 *
 * 内置浏览器里点到的文件链接 (蓝奏云 / 直链 / 网盘) 都落到这里:
 * 列表显示进度, 点卡片能暂停 / 继续 / 重试 / 安装, 右上角能一键暂停全部或清空已完成的记录。
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
    val hasActive = sorted.any { it.isActive }
    val hasFinished = sorted.any { it.state == DownloadState.DONE || it.state == DownloadState.FAILED }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "下载管理",
                onBack = onBack,
                actions = {
                    if (hasActive) {
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
                EmptyMessage(text = "还没有下载任务\n在内置浏览器里点文件链接就会出现在这里")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = GlassSpacing.page,
                        end = GlassSpacing.page,
                        top = 8.dp,
                        bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sorted, key = { it.id }) { task ->
                        DownloadRow(
                            task = task,
                            onToggle = {
                                if (task.isActive) {
                                    Downloads.pause(task.id)
                                } else {
                                    Downloads.resume(task.id)
                                }
                            },
                            onOpen = {
                                val file = Downloads.fileOf(task) ?: return@DownloadRow
                                Downloads.openFile(context, task, file)
                            },
                            onRemove = { Downloads.remove(task.id) },
                        )
                    }
                    if (hasFinished) {
                        item(key = "clear_finished") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(
                                    text = "清除已完成",
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

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackground,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleEffect(label = "downloadRowPress")
            .glassCard(radius = GlassRadius.card),
        cornerRadius = GlassRadius.card,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 类型图标: 一眼区分 apk / 图片 / 视频 / 音乐 / 其它
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(GlassRadius.icon))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconOf(task),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
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
                        fontSize = 12.sp,
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
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { mainAction() }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = mainLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
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

            // 进度条只在没下完时显示, 下完留白更干净
            if (task.state == DownloadState.RUNNING || task.state == DownloadState.PENDING) {
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                    // 服务器没给总大小 -> null 走不确定进度条
                    progress = if (task.totalBytes > 0L) task.progress else null,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                    ),
                    height = 4.dp,
                )
            } else if (task.state == DownloadState.PAUSED && task.totalBytes > 0L) {
                // 暂停时保留一条静态进度, 让用户知道停在哪
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                    progress = task.progress,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.5f),
                        backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                    ),
                    height = 4.dp,
                )
            }
        }
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
