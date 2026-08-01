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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.Category
import com.fengling.share.ui.components.AppSubtitle
import com.fengling.share.ui.components.AppText
import com.fengling.share.ui.components.AppTitle
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.GlassCard
import com.fengling.share.ui.components.SectionTitle
import com.fengling.share.ui.theme.adaptiveListPadding
import kotlinx.coroutines.launch

/**
 * HomeScreen - 首页 (参考 legado-with-MD3 ui/main/home)
 * MD3 LargeTopAppBar + SearchBar + 分类 + 软件列表
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var searchActive by remember { mutableStateOf(false) }
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

    androidx.compose.material3.Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("风铃分享库", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 搜索栏 (MD3 SearchBar)
                SearchBar(
                    query = query,
                    onQueryChange = {
                        query = it
                        loadApps(selectedCategory, it)
                    },
                    onSearch = { loadApps(selectedCategory, it) },
                    active = searchActive,
                    onActiveChange = { searchActive = it },
                    placeholder = { Text("搜索软件") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (apps.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText("未找到相关软件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        apps.forEach { app ->
                            AppListItem(app = app, onClick = { onAppClick(app.id) })
                        }
                    }
                }

                // 分类 (横向滚动)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
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
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
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
                            contentPadding = adaptiveListPadding(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                SectionTitle(text = "共 ${apps.size} 款软件")
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
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    GlassCard(
        onClick = onClick,
        cornerRadius = 20.dp,
        containerColor = bg,
        contentColor = fg,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 7.dp)) {
            AppText(
                text = name,
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** 软件列表项 */
@Composable
private fun AppListItem(app: AppItem, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标 (圆角方块)
            Box(
                modifier = Modifier
                    .width(52.dp)
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
                    AppText(
                        text = app.name.take(1),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // 信息
            Column(Modifier.weight(1f)) {
                AppTitle(text = app.name)
                Spacer(Modifier.height(3.dp))
                AppSubtitle(
                    text = buildString {
                        if (app.categoryName.isNotEmpty()) append(app.categoryName)
                        if (app.version.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("v${app.version}")
                        }
                    },
                )
                Spacer(Modifier.height(3.dp))
                AppSubtitle(text = "${formatCount(app.downloadCount)} 次下载")
            }
            // 下载按钮
            DownloadButton(app = app, onClick = onClick)
        }
    }
}

/** 下载按钮 */
@Composable
private fun DownloadButton(app: AppItem, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        cornerRadius = 8.dp,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            AppText(
                text = "下载",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelLarge,
            )
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
