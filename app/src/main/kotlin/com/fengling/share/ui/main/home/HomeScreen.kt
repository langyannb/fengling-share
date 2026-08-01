package com.fengling.share.ui.main.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.Category
import com.fengling.share.ui.components.AppText
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.GlassCard
import kotlinx.coroutines.launch

/**
 * HomeScreen - 首页 (高级网格风格, 轮廓图标)
 * 渐变头部 + 搜索胶囊 + 分类 chips + 3列精致网格
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 渐变头部
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.background,
                                )
                            )
                        )
                        .padding(top = 8.dp, bottom = 12.dp),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "风铃分享库",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "精选软件 · 网盘直链 · 免费下载",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(14.dp))
                        // 搜索胶囊
                        TextField(
                            value = query,
                            onValueChange = {
                                query = it
                                loadApps(selectedCategory, it)
                            },
                            placeholder = {
                                Text("搜索软件", fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = "搜索",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 分类 chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                    rowItems(categories) { cat ->
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

                // 网格区
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            gridItems(apps, key = { it.id }) { app ->
                                AppGridItem(app = app, onClick = { onAppClick(app.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分类胶囊 (选中主色, 未选中轮廓) */
@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
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

/** 应用网格项: 图标 + 名称 + 下载按钮 */
@Composable
private fun AppGridItem(app: AppItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 图标 (大圆角方块)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp)),
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = app.name.take(1),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 名称
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(3.dp))
        // 下载量
        Text(
            text = "${formatCount(app.downloadCount)} 次下载",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        GlassCard(
            onClick = onClick,
            cornerRadius = 10.dp,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = "下载",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge,
                )
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
