package com.fengling.share.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.PanLink
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 详情页: 覆盖层, Miuix iOS 风格 */
@Composable
fun DetailScreen(
    appId: Int,
    onBack: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var app by remember { mutableStateOf<AppItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var clicking by remember { mutableStateOf(false) }

    LaunchedEffect(appId) {
        loading = true
        error = ""
        try {
            app = ApiClient.getAppDetail(appId)
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "软件详情",
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("‹ 返回", fontSize = 18.sp, color = MiuixTheme.colorScheme.primary)
                    }
                },
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MiuixTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onBack) {
                            Text("返回", color = MiuixTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            app != null -> {
                val item = app!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    // 头部信息
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
                                    .size(68.dp)
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
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = item.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onBackground,
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
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${formatCount(item.downloadCount)} 次下载",
                                    fontSize = 13.sp,
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
                                lineHeight = 22.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
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
                                                // 内置浏览器打开
                                                onOpenWeb(url, displayLinkName(link))
                                            }
                                        } catch (_: Exception) { }
                                        clicking = false
                                    }
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        SmallTitle(text = "下载")
                        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            Text(
                                text = "该软件暂无下载链接",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 网盘下载卡片 */
@Composable
private fun PanLinkCard(link: PanLink, enabled: Boolean, onClick: () -> Unit) {
    val label = when (link.panType) {
        "uc" -> "UC网盘"
        "quark" -> "夸克网盘"
        "baidu" -> "百度网盘"
        "ali" -> "阿里云盘"
        else -> "网盘下载"
    }
    val displayLabel = if (link.label.isNotEmpty()) link.label else label

    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
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
                    text = displayLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                if (link.password.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "提取码: ${link.password}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            Text(
                text = if (enabled) "下载 ›" else "跳转中...",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 网盘链接显示名 */
private fun displayLinkName(link: PanLink): String {
    val label = when (link.panType) {
        "uc" -> "UC网盘"
        "quark" -> "夸克网盘"
        "baidu" -> "百度网盘"
        "ali" -> "阿里云盘"
        else -> "网盘下载"
    }
    return if (link.label.isNotEmpty()) link.label else label
}
