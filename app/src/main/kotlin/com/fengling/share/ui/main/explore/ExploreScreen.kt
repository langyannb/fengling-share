package com.fengling.share.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.Category
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.EmptyMessage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * ExploreScreen - 分类页 (Miuix 风格)
 * 分类卡片 → 分类内软件列表
 */
@Composable
fun ExploreScreen(
    onAppClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expandedCategory by remember { mutableStateOf<Int?>(null) }
    var categoryApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var categoryLoading by remember { mutableStateOf(false) }
    // 分类软件缓存 (key: categoryId, 切换分类不重新请求)
    val categoryCache = remember { mutableMapOf<Int, List<AppItem>>() }
    // 页面级缓存: categories + 全部 apps (切 tab 回来不重新加载)
    var pageLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (pageLoaded) {
            loading = false
            return@LaunchedEffect
        }
        try {
            categories = ApiClient.getCategories()
            apps = ApiClient.getApps()
            pageLoaded = true
        } catch (_: Exception) { }
        loading = false
    }

    fun loadCategoryApps(catId: Int) {
        // 命中缓存直接返回, 不刷新
        categoryCache[catId]?.let {
            categoryApps = it
            categoryLoading = false
            return
        }
        scope.launch {
            categoryLoading = true
            try {
                val result = ApiClient.getApps(catId)
                categoryCache[catId] = result
                categoryApps = result
            } catch (_: Exception) { }
            categoryLoading = false
        }
    }

    Scaffold(
        topBar = {
            if (expandedCategory != null) {
                AppTopBar(
                    title = categories.firstOrNull { it.id == expandedCategory }?.name ?: "分类",
                    onBack = { expandedCategory = null },
                )
            } else {
                AppTopBar(title = "分类")
            }
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
            expandedCategory != null -> {
                val catId = expandedCategory!!
                if (categoryLoading && categoryApps.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("加载中...", color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                } else if (categoryApps.isEmpty()) {
                    EmptyMessage(text = "该分类暂无软件")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            SmallTitle(text = "共 ${categoryApps.size} 款软件")
                        }
                        items(categoryApps, key = { it.id }) { app ->
                            CategoryAppItem(app = app, onClick = { onAppClick(app.id) })
                        }
                    }
                }
                LaunchedEffect(catId) {
                    loadCategoryApps(catId)
                }
            }
            categories.isEmpty() -> {
                EmptyMessage(text = "暂无分类")
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        SmallTitle(text = "全部分类")
                    }
                    items(categories) { cat ->
                        CategoryCard(
                            name = cat.name,
                            appCount = apps.count { it.categoryId == cat.id },
                            icon = cat.icon,
                            color = cat.color,
                            onClick = {
                                expandedCategory = cat.id
                                categoryApps = emptyList()
                                loadCategoryApps(cat.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 分类卡片 (OShin 风格: 渐变图标圆 + 上传图标优先 + 计数徽章) */
@Composable
private fun CategoryCard(name: String, appCount: Int, icon: String, color: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 渐变彩色图标圆 (上传图标优先, 无图时首字符)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                parseColor(color).copy(alpha = 0.85f),
                                parseColor(color).copy(alpha = 0.45f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (icon.isNotEmpty()) {
                    AsyncImage(
                        model = icon,
                        contentDescription = name,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Text(
                        text = name.take(1),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$appCount 款软件",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            // 圆形计数徽章
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        parseColor(color).copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = appCount.coerceAtMost(99).toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = parseColor(color),
                )
            }
        }
    }
}

/** 解析 #RRGGBB 颜色 */
private fun parseColor(hex: String): androidx.compose.ui.graphics.Color {
    return try {
        androidx.compose.ui.graphics.Color(
            android.graphics.Color.parseColor(hex)
        )
    } catch (e: Exception) {
        androidx.compose.ui.graphics.Color(0xFF4C6FFF)
    }
}

/** 分类内软件项 */
@Composable
private fun CategoryAppItem(app: AppItem, onClick: () -> Unit) {
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
                    text = app.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (app.version.isNotEmpty()) append("v${app.version}")
                        if (app.downloadCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${app.downloadCount} 次下载")
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
