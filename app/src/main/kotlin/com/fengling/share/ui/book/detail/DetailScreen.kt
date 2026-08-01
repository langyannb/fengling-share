package com.fengling.share.ui.book.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.PackItem
import com.fengling.share.data.PanLink
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.EmptyMessage
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
    var app by remember { mutableStateOf<AppItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var clicking by remember { mutableStateOf(false) }

    // 底部下载栏用普通卡片, 不用 blur backdrop
    LaunchedEffect(appId) {
        try {
            app = ApiClient.getAppDetail(appId)
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = app?.name ?: "软件详情",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        when {
            loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("加载中...", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
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
                                    Text(
                                        text = item.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = buildString {
                                            if (item.categoryName.isNotEmpty()) append(item.categoryName)
                                            if (item.version.isNotEmpty()) {
                                                if (isNotEmpty()) append(" · ")
                                                append("v${item.version}")
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
