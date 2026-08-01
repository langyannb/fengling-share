package com.fengling.share.ui.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.fengling.share.data.Category
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.GlassCard
import com.fengling.share.ui.components.rememberGlassBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HomeScreen - 首页 (Miuix 现代化列表风格)
 * Miuix TopAppBar(紧凑大标题) + SearchBar + 分类 chips + 列表
 */
@Composable
fun HomeScreen(
    onAppClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    fun loadApps(catId: Int, kw: String) {
        scope.launch {
            loading = true
            error = ""
            try {
                apps = ApiClient.getApps(catId, kw)
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            }
            loading = false
        }
    }

    fun refresh() {
        scope.launch {
            refreshing = true
            try {
                categories = ApiClient.getCategories()
                apps = ApiClient.getApps(selectedCategory, query)
            } catch (_: Exception) { }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            categories = ApiClient.getCategories()
        } catch (_: Exception) { }
        loadApps(0, "")
    }

    Scaffold(
        topBar = {
            // Miuix 大标题栏 (自带紧凑折叠, 不占多余空间)
            TopAppBar(
                title = "风铃分享库",
                largeTitle = "风铃分享库",
            )
        },
    ) { innerPadding ->
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Miuix 搜索栏
                SearchBar(
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = {
                                query = it
                                loadApps(selectedCategory, it)
                            },
                            onSearch = { loadApps(selectedCategory, it) },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = "搜索软件",
                        )
                    },
                    onExpandedChange = { searchExpanded = it },
                    expanded = searchExpanded,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    if (apps.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("未找到相关软件", color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                    } else {
                        apps.forEach { app ->
                            AppListItem(app = app, onClick = { onAppClick(app.id) })
                        }
                    }
                }

                // 分类 chips (横向滚动)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    item {
                        CategoryChip(
                            name = "全部",
                            selected = selectedCategory == 0,
                            onClick = {
                                selectedCategory = 0
                                loadApps(0, query)
                            },
                        )
                    }
                    items(categories) { cat ->
                        CategoryChip(
                            name = cat.name,
                            selected = selectedCategory == cat.id,
                            onClick = {
                                selectedCategory = cat.id
                                loadApps(cat.id, query)
                            },
                        )
                    }
                }

                // 列表区
                when {
                    loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中...", color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                    }
                    error.isNotEmpty() && apps.isEmpty() -> {
                        EmptyMessage(text = error)
                    }
                    apps.isEmpty() -> {
                        EmptyMessage(text = "暂无软件")
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            item {
                                SmallTitle(text = "共 ${apps.size} 款软件")
                            }
                            items(apps, key = { it.id }) { app ->
                                AppListItem(app = app, onClick = { onAppClick(app.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分类胶囊 */
@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp),
        cornerRadius = 20.dp,
        colors = if (selected) {
            top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary,
            )
        } else {
            top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                contentColor = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
            Text(
                text = name,
                fontSize = 14.sp,
                color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** 软件列表项 (Miuix Card) */
@Composable
private fun AppListItem(app: AppItem, onClick: () -> Unit) {
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
            // 图标 (圆角方块)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (app.icon.isNotEmpty()) {
                    AsyncImage(
                        model = app.icon,
                        contentDescription = app.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = app.name.take(1),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // 信息
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (app.categoryName.isNotEmpty()) append(app.categoryName)
                        if (app.version.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("v${app.version}")
                        }
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${formatCount(app.downloadCount)} 次下载",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            // 下载按钮
            Card(
                onClick = onClick,
                cornerRadius = 10.dp,
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "下载",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 下载量格式化 */
fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> String.format("%.1fw", count / 10000.0)
        count >= 1000 -> String.format("%.1fk", count / 1000.0)
        else -> count.toString()
    }
}
