package com.fengling.share.ui.book.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.PackItem
import com.fengling.share.data.PanLink
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.LoadingBox
import com.fengling.share.ui.components.ScreenshotSaveDialog
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import com.fengling.share.ui.main.home.formatCount
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * DetailScreen - 软件详情 (Miuix + 液态玻璃)
 * 头部信息 + 介绍 + 网盘下载列表 + 底部液态玻璃下载栏
 */
@Composable
fun DetailScreen(
    appId: Int,
    onBack: () -> Unit,
    onOpenWeb: (String, String, String) -> Unit,
    onOpenSubApp: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var app by remember { mutableStateOf<AppItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var clicking by remember { mutableStateOf(false) }
    var showHarmDialog by remember { mutableStateOf(false) }

    // 底部下载栏用普通卡片, 不用 blur backdrop
    LaunchedEffect(appId) {
        try {
            app = ApiClient.getAppDetail(appId)
        } catch (e: Exception) {
            error = e.userFriendlyMessage()
        }
        loading = false
    }

    // 契约 B: 预测性返回(跟手) —— 跟手右移+缩小淡出, 松手过半分提交返回, 否则回弹;
    // 未开「预测性返回手势动画」的系统上系统不回传进度, 回调立刻正常结束 -> 直接 onBack(), 功能不变。
    Scaffold(
        modifier = Modifier,
        topBar = {
            AppTopBar(
                title = app?.name ?: "软件详情",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        when {
            loading -> {
                LoadingBox(Modifier.fillMaxSize().padding(innerPadding))
            }
            error.isNotEmpty() -> {
                EmptyMessage(text = error)
            }
            app != null -> {
                val item = app!!
                Box(Modifier.fillMaxSize()) {
                    // 内容层 (被液态玻璃捕获)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))

                        // 头部信息卡
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (item.icon.isNotEmpty()) {
                                        AsyncImage(
                                            model = item.icon,
                                            contentDescription = item.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Text(
                                            text = item.name.take(1),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MiuixTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        // 新版本徽标 (渐变)
                                        if (item.isNew) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "新",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                                            listOf(
                                                                androidx.compose.ui.graphics.Color(0xFFFF8F1F),
                                                                androidx.compose.ui.graphics.Color(0xFFFF4D6D),
                                                            )
                                                        )
                                                    )
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = buildString {
                                            if (item.categoryName.isNotEmpty()) append(item.categoryName)
                                            if (item.version.isNotEmpty()) {
                                                if (isNotEmpty()) append(" · ")
                                                append("v${item.version}")
                                            }
                                            // 发布日期 (完整年月日)
                                            if (item.releaseDate.isNotEmpty()) {
                                                if (isNotEmpty()) append(" · ")
                                                append("更新于 ")
                                                append(item.releaseDate)
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${formatCount(item.downloadCount)} 次下载",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 描述
                        if (item.description.isNotEmpty()) {
                            SmallTitle(text = "软件介绍")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 14.dp,
                            ) {
                                Text(
                                    text = item.description,
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // 应用截图 (应用市场风格, 横向滑动 + 点击放大)
                        if (item.screenshots.isNotEmpty()) {
                            SmallTitle(text = "应用截图")
                            ScreenshotStrip(
                                urls = item.screenshots,
                                appName = item.name,
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        // 整合包: 包含的软件
                        if (item.packItems.isNotEmpty()) {
                            SmallTitle(text = "包含软件")
                            item.packItems.forEach { sub ->
                                PackItemCard(
                                    item = sub,
                                    onClick = { onOpenSubApp(sub.id) },
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // 所属整合包
                        if (item.parentPack != null) {
                            SmallTitle(text = "所属整合包")
                            PackItemCard(
                                item = item.parentPack,
                                onClick = { onOpenSubApp(item.parentPack!!.id) },
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        // 网盘下载
                        if (item.panLinks.isNotEmpty()) {
                            SmallTitle(text = "选择下载方式")
                            item.panLinks.forEach { link ->
                                PanLinkCard(
                                    link = link,
                                    enabled = !clicking,
                                    onClick = {
                                        scope.launch {
                                            clicking = true
                                            try {
                                                val (url, password) = ApiClient.clickLink(link.id)
                                                if (url.isNotEmpty()) {
                                                    onOpenWeb(url, displayLinkName(link), password)
                                                }
                                            } catch (_: Exception) { }
                                            clicking = false
                                        }
                                    },
                                )
                            }
                        } else {
                            Spacer(Modifier.height(14.dp))
                            EmptyMessage(text = "暂无下载链接")
                        }

                        // 反馈和谐入口 (用户报告软件被和谐, 请求更新)
                        Spacer(Modifier.height(6.dp))
                        HarmReportCard(onClick = { showHarmDialog = true })

                        Spacer(Modifier.height(120.dp))
                    }

                    // 底部下载栏 (半透明玻璃质感卡片, 避免 blur backdrop 黑边问题)
                    if (item.panLinks.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            cornerRadius = 20.dp,
                            colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                contentColor = MiuixTheme.colorScheme.onBackground,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${item.panLinks.size} 个下载源",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                }
                                Card(
                                    onClick = {
                                        scope.launch {
                                            clicking = true
                                            try {
                                                val link = item.panLinks.first()
                                                val (url, password) = ApiClient.clickLink(link.id)
                                                if (url.isNotEmpty()) {
                                                    onOpenWeb(url, displayLinkName(link), password)
                                                }
                                            } catch (_: Exception) { }
                                            clicking = false
                                        }
                                    },
                                    cornerRadius = 12.dp,
                                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                                        color = MiuixTheme.colorScheme.primary,
                                        contentColor = MiuixTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Box(Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) {
                                        Text(
                                            text = if (clicking) "打开中..." else "立即下载",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 反馈和谐弹窗 (用户填写哪里被和谐了, 提交后端)
    if (showHarmDialog && app != null) {
        HarmReportDialog(
            appName = app!!.name,
            onDismiss = { showHarmDialog = false },
            onSubmit = { content, contact ->
                scope.launch {
                    val err = ApiClient.submitHarmReport(app!!.id, app!!.name, content, contact)
                    showHarmDialog = false
                    Toast.makeText(
                        context,
                        if (err == null) "反馈已提交，感谢支持" else err,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }
}

/** 反馈和谐入口卡片 (详情页下载区下方) */
@Composable
private fun HarmReportCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "软件有问题？反馈和谐",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "链接失效 / 内容被屏蔽 / 需要更新，告诉我们",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Text(
                text = "反馈",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

/** 反馈和谐填写弹窗 (miuix 风格: 圆角卡片 + 主色胶囊按钮) */
@Composable
private fun HarmReportDialog(
    appName: String,
    onDismiss: () -> Unit,
    onSubmit: (content: String, contact: String) -> Unit,
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MiuixTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "反馈和谐",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "软件：$appName",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("哪里被和谐了？例如：下载链接失效、提取码错误、内容被屏蔽…", fontSize = 13.sp) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                placeholder = { Text("联系方式（选填，方便处理结果通知你）", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 取消 (次级圆角按钮)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !submitting, onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                }
                // 提交 (主色胶囊)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (submitting) MiuixTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MiuixTheme.colorScheme.primary
                        )
                        .clickable(enabled = !submitting, onClick = {
                            if (content.trim().isEmpty()) {
                                Toast.makeText(context, "请填写哪里被和谐了", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            submitting = true
                            onSubmit(content.trim(), contact.trim())
                        })
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (submitting) "提交中..." else "提交",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 网盘链接卡片 */
@Composable
private fun PanLinkCard(
    link: PanLink,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = displayLinkName(link),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (link.password.isNotEmpty()) "提取码: ${link.password}" else "点击进入下载",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Text(
                text = "下载",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** 整合包子项卡片 */
@Composable
private fun PackItemCard(item: PackItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.icon.isNotEmpty()) {
                    AsyncImage(
                        model = item.icon,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = item.name.take(1),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (item.version.isNotEmpty()) append("v${item.version}")
                        if (item.downloadCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${item.downloadCount} 次下载")
                        }
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Text(
                text = "查看",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** 网盘链接显示名 */
private fun displayLinkName(link: PanLink): String {
    return when {
        link.label.isNotEmpty() -> link.label
        link.panType == "baidu" -> "百度网盘"
        link.panType == "quark" -> "夸克网盘"
        link.panType == "aliyun" -> "阿里云盘"
        link.panType == "lanzou" -> "蓝奏云"
        link.panType == "xunlei" -> "迅雷网盘"
        else -> "网盘链接"
    }
}

/**
 * 应用截图横滑条 (应用市场风格)
 * 每张 160dp 宽圆角卡, 点击全屏预览
 */
@Composable
private fun ScreenshotStrip(urls: List<String>, appName: String) {
    var previewIndex by remember { mutableStateOf(-1) } // -1 = 不预览
    var saveUrl by remember { mutableStateOf<String?>(null) } // 长按保存的图片
    LazyRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        items(urls.size) { i ->
            val url = urls[i]
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    // 点击全屏预览, 长按保存弹窗
                    .combinedClickable(
                        onClick = { previewIndex = i },
                        onLongClick = { saveUrl = url },
                    ),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "$appName 截图 ${i + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
    // 全屏预览
    if (previewIndex >= 0 && previewIndex < urls.size) {
        ScreenshotPreview(
            urls = urls,
            initialIndex = previewIndex,
            appName = appName,
            onDismiss = { previewIndex = -1 },
        )
    }
    // 长按保存弹窗
    saveUrl?.let { u ->
        ScreenshotSaveDialog(
            url = u,
            appName = appName,
            onDismiss = { saveUrl = null },
        )
    }
}

/**
 * 截图全屏预览 (横向滑动切换 + 双指缩放/拖动 + 页码 + 右上角关闭)
 * 背景跟随主题深色玻璃, 支持双指捏合放大 1x~4x, 每页缩放独立
 */

/** 单页缩放状态 (不可变, 整体替换触发重组) */
private data class ZoomState(val scale: Float = 1f, val offset: Offset = Offset.Zero)

@Composable
private fun ScreenshotPreview(
    urls: List<String>,
    initialIndex: Int,
    appName: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val pagerState = rememberPagerState(pageCount = { urls.size }, initialPage = initialIndex)
        var saveUrl by remember { mutableStateOf<String?>(null) }
        // 每页独立缩放状态 (放大某张时相邻页不受影响)
        val zoomStates = remember(urls.size) {
            List(urls.size) { mutableStateOf(ZoomState()) }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            // 深色玻璃底 (跟随主题, 替代纯黑)
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.75f)
                        else Color(0xFF1A1A1E).copy(alpha = 0.88f)
                    ),
            )
            // 图片横向滑动 (双指缩放/拖动, 单击关闭, 双击放大, 长按保存)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val density = LocalDensity.current
                val config = LocalConfiguration.current
                val screenW = with(density) { config.screenWidthDp.dp.toPx() }
                val screenH = with(density) { config.screenHeightDp.dp.toPx() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds() // 放大后图片不溢出到相邻页
                        .padding(12.dp)
                        .pointerInput(Unit) {
                            // 只有双指(缩放)或已放大(拖动)时才消费手势;
                            // 1x 状态下手势放行给 Pager 做横向翻页
                            // ⚠️ 每次手势事件都重新读 zoomStates[page].value,
                            //    不能捕获组合时的快照 (pointerInput 闭包不会重组)
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val multiTouch = event.changes.size > 1
                                    val cur = zoomStates[page].value
                                    if (multiTouch || cur.scale > 1f) {
                                        val newScale = (cur.scale * zoomChange).coerceIn(1f, 4f)
                                        // 平移边界: 放大后拖动不超出图片范围 (scale>=1, 恒非负)
                                        val maxX = screenW * (newScale - 1f) / 2f
                                        val maxY = screenH * (newScale - 1f) / 2f
                                        zoomStates[page].value = ZoomState(
                                            scale = newScale,
                                            offset = Offset(
                                                (cur.offset.x + panChange.x).coerceIn(-maxX, maxX),
                                                (cur.offset.y + panChange.y).coerceIn(-maxY, maxY),
                                            ),
                                        )
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onDismiss() },
                                onDoubleTap = {
                                    // 读取最新状态: 放大过则还原, 否则放大
                                    val cur = zoomStates[page].value
                                    zoomStates[page].value = if (cur.scale > 1.1f) {
                                        ZoomState()
                                    } else {
                                        ZoomState(scale = 2.5f)
                                    }
                                },
                                onLongPress = { saveUrl = urls[page] },
                            )
                        },
                ) {
                    val zoom = zoomStates[page].value
                    AsyncImage(
                        model = urls[page],
                        contentDescription = "$appName 截图 ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = zoom.offset.x
                                translationY = zoom.offset.y
                            },
                        // Fit 完整显示全图 (默认即可看全图), 双击/双指放大看细节
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            // 关闭按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 页码
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        // 长按保存弹窗 (叠加在当前 Dialog 之上)
        saveUrl?.let { u ->
            ScreenshotSaveDialog(
                url = u,
                appName = appName,
                onDismiss = { saveUrl = null },
            )
        }
    }
}
