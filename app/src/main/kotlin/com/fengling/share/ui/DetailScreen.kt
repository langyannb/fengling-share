package com.fengling.share.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.PanLink
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TopAppBar

/** 详情页: 覆盖层, 显示软件信息 + 网盘下载入口 */
@Composable
fun DetailScreen(
    appId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF5F6FA))) {
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
                    Text("← 返回", fontSize = 14.sp, color = Color(0xFF4C6FFF))
                }
            },
        )

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4C6FFF), strokeWidth = 2.dp)
                }
            }
            error.isNotEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = Color(0xFFF5455C), fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "返回",
                            color = Color(0xFF4C6FFF),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x144C6FFF))
                                .clickable(onClick = onBack)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            app != null -> {
                val item = app!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    // 头部: 图标 + 名称 + 信息
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFE8ECFF)),
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
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4C6FFF),
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = item.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A2E),
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
                                color = Color(0xFF8A8FA8),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${formatCount(item.downloadCount)} 次下载",
                                fontSize = 13.sp,
                                color = Color(0xFF8A8FA8),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // 描述
                    if (item.description.isNotEmpty()) {
                        Text(
                            text = "软件介绍",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1A2E),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.description,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF444444),
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    // 网盘下载区
                    if (item.panLinks.isNotEmpty()) {
                        Text(
                            text = "选择下载方式",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A1A2E),
                        )
                        Spacer(Modifier.height(10.dp))
                        item.panLinks.forEach { link ->
                            PanLinkButton(
                                link = link,
                                enabled = !clicking,
                                onClick = {
                                    scope.launch {
                                        clicking = true
                                        try {
                                            val (url, password) = ApiClient.clickLink(link.id)
                                            if (url.isNotEmpty()) {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                )
                                            }
                                        } catch (_: Exception) { }
                                        clicking = false
                                    }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    } else {
                        Text(
                            text = "该软件暂无下载链接",
                            fontSize = 14.sp,
                            color = Color(0xFF8A8FA8),
                        )
                    }
                }
            }
        }
    }
}

/** 网盘下载按钮 */
@Composable
private fun PanLinkButton(link: PanLink, enabled: Boolean, onClick: () -> Unit) {
    val (bg, label) = when (link.panType) {
        "uc" -> Color(0xFF2E7CF6) to "UC网盘"
        "quark" -> Color(0xFFFF7A45) to "夸克网盘"
        "baidu" -> Color(0xFF4E8CF7) to "百度网盘"
        "ali" -> Color(0xFF5C7CFA) to "阿里云盘"
        else -> Color(0xFF7B8CA8) to "网盘下载"
    }
    val displayLabel = if (link.label.isNotEmpty()) link.label else label

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = displayLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            if (link.password.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "提取码: ${link.password}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Text(
            text = "下载 ↗",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
