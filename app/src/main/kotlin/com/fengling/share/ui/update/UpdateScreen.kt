package com.fengling.share.ui.update

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApkDownloader
import com.fengling.share.data.AppVersion
import com.fengling.share.data.VersionInfo
import com.fengling.share.ui.components.AppTopBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * UpdateScreen - OShin 同款更新页
 * 当前版本 + 新版本卡片 + 大小/日期 + 下载并安装 (进度) + 展开更新说明
 */
@Composable
fun UpdateScreen(
    info: VersionInfo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by ApkDownloader.progress.collectAsState()
    val status by ApkDownloader.status.collectAsState()
    val error by ApkDownloader.error.collectAsState()
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ApkDownloader.reset() }
    LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        AppTopBar(title = "软件更新", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // 当前版本卡片
            Text(
                text = "当前版本",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.5f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "风铃分享库",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "v${AppVersion.CURRENT}(${AppVersion.CODE})",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 新版本卡片
            Text(
                text = "最新版本",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MiuixTheme.colorScheme.primary,
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "风铃分享库",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "v${info.version}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 大小 | 日期 (OShin 同款)
                val sizeText = if (info.sizeMb > 0f) String.format("%.2f MB", info.sizeMb) else "未知大小"
                val dateText = if (info.releaseDate.isNotEmpty()) "发布于 ${info.releaseDate}" else ""
                Text(
                    text = if (dateText.isNotEmpty()) "$sizeText | $dateText" else sizeText,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )

                Spacer(Modifier.height(14.dp))

                // 下载并安装按钮 (OShin 同款)
                when {
                    progress == 200 -> {
                        // 下载完成, 拉起安装
                        LaunchedEffect(Unit) {
                            ApkDownloader.downloadedFile?.let { file ->
                                ApkDownloader.install(context, file)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.primary)
                                .clickable {
                                    ApkDownloader.downloadedFile?.let { file ->
                                        ApkDownloader.install(context, file)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "安装",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    progress in 0..99 -> {
                        Column(Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MiuixTheme.colorScheme.primary,
                                trackColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (status.isNotEmpty()) "$status $progress%" else "正在下载... $progress%",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MiuixTheme.colorScheme.primary,
                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.75f),
                                        )
                                    )
                                )
                                .clickable {
                                    if (info.url.isNotEmpty()) {
                                        scope.launch {
                                            ApkDownloader.download(context, info.url)
                                        }
                                    } else {
                                        Toast.makeText(context, "下载地址未配置", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "下载并安装",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            // 更新说明 (展开/收起, OShin 同款)
            if (info.updateLog.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "更新说明",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.9f)),
                ) {
                    AnimatedVisibility(
                        visible = showHistory,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Text(
                            text = info.updateLog,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHistory = !showHistory }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (showHistory) "收起更新说明 ↑" else "查看更多更新说明 >",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = if (showHistory) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
