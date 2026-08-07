package com.fengling.share.ui.main.explore

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.Category
import com.fengling.share.data.categoryWithSubsIds
import com.fengling.share.data.childrenOf
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.LoadingBox
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
    var selectedSubCategory by remember { mutableStateOf<Int?>(null) } // null=父分类全部
    var categoryApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var categoryLoading by remember { mutableStateOf(false) }
    // 分类软件缓存 (key: categoryId, 切换分类不重新请求)
    val categoryCache = remember { mutableMapOf<Int, List<AppItem>>() }
    // 页面级缓存: categories + 全部 apps (切 tab 回来不重新加载)
    var pageLoaded by remember { mutableStateOf(false) }

    // 顶级分类 (宫格只显示顶级; 子分类在二级页以 chips 呈现)
    val topCategories = remember(categories) { categories.filter { it.isTopLevel } }
    // 当前展开分类的子分类
    val subCategories = remember(categories, expandedCategory) {
        expandedCategory?.let { childrenOf(categories, it) } ?: emptyList()
    }

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

    // 返回键: 子分类筛选时先回「全部」, 二级页时回分类列表, 否则默认处理
    androidx.activity.compose.BackHandler(enabled = expandedCategory != null) {
        if (selectedSubCategory != null) {
            selectedSubCategory = null
            categoryApps = emptyList()
            loadCategoryApps(expandedCategory!!)
        } else {
            expandedCategory = null
        }
    }

    fun openCategory(catId: Int) {
        expandedCategory = catId
        selectedSubCategory = null
        categoryApps = emptyList()
        loadCategoryApps(catId)
    }

    fun selectSubCategory(subId: Int) {
        selectedSubCategory = subId
        categoryApps = emptyList()
        loadCategoryApps(subId)
    }

    Scaffold(
        topBar = {
            if (expandedCategory != null) {
                AppTopBar(
                    title = categories.firstOrNull { it.id == expandedCategory }?.name ?: "分类",
                    onBack = {
                        if (selectedSubCategory != null) {
                            selectedSubCategory = null
                            categoryApps = emptyList()
                            loadCategoryApps(expandedCategory!!)
                        } else {
                            expandedCategory = null
                        }
                    },
                )
            } else {
                AppTopBar(title = "分类")
            }
        },
    ) { innerPadding ->
        when {
            loading -> {
                LoadingBox(Modifier.fillMaxSize().padding(innerPadding))
            }
            expandedCategory != null -> {
                val catId = expandedCategory!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // 子分类筛选 chips (选中分类有子分类时显示)
                    if (subCategories.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            item {
                                CategoryChip(
                                    name = "全部",
                                    selected = selectedSubCategory == null,
                                    onClick = {
                                        selectedSubCategory = null
                                        categoryApps = emptyList()
                                        loadCategoryApps(catId)
                                    },
                                )
                            }
                            items(subCategories, key = { it.id }) { sub ->
                                CategoryChip(
                                    name = sub.name,
                                    selected = selectedSubCategory == sub.id,
                                    onClick = { selectSubCategory(sub.id) },
                                )
                            }
                        }
                    }
                    if (categoryLoading && categoryApps.isEmpty()) {
                        LoadingBox(Modifier.fillMaxSize())
                    } else if (categoryApps.isEmpty()) {
                        Box(Modifier.fillMaxSize()) {
                            EmptyMessage(text = "该分类暂无软件")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp, end = 12.dp, top = 4.dp,
                                bottom = 100.dp, // 留出悬浮胶囊空间, 内容可滚到胶囊下方被模糊
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
                }
                LaunchedEffect(catId, selectedSubCategory) {
                    if (categoryApps.isEmpty()) loadCategoryApps(selectedSubCategory ?: catId)
                }
            }
            categories.isEmpty() -> {
                EmptyMessage(text = "暂无分类")
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 14.dp, end = 14.dp, top = 8.dp,
                        bottom = 100.dp, // 留出悬浮胶囊空间, 内容可滚到胶囊下方被模糊
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 标题横跨整行 (2 列)
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        SmallTitle(text = "全部分类", modifier = Modifier.padding(vertical = 4.dp))
                    }
                    gridItems(topCategories, key = { it.id }) { cat ->
                        MarketCategoryCard(
                            name = cat.name,
                            appCount = apps.count { it.categoryId in categoryWithSubsIds(categories, cat.id) },
                            icon = cat.icon,
                            color = cat.color,
                            onClick = { openCategory(cat.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 子分类筛选 chip (胶囊形, 选中主色填充, 未选中玻璃浅底)
 */
@Composable
private fun CategoryChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "exploreChip",
    )
    Text(
        text = name,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MiuixTheme.colorScheme.onPrimary
        else MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * 应用市场风格分类大卡片 (2 列宫格, 小米应用商店分类页同款)
 * 整卡渐变色背景 + 大图标 + 名称 + 软件数量, 视觉冲击力强
 */
@Composable
private fun MarketCategoryCard(
    name: String,
    appCount: Int,
    icon: String,
    color: String,
    onClick: () -> Unit,
) {
    val catColor = parseColor(color)
    val isDark = isSystemInDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "catCard",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        catColor,
                        catColor.copy(alpha = 0.62f),
                    )
                )
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        // 大图标 (白底圆角方块内展示, 无图用首字符) — 左上
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon.isNotEmpty()) {
                AsyncImage(
                    model = icon,
                    contentDescription = name,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                Text(
                    text = name.take(1),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = catColor,
                )
            }
        }
        // 名称 + 数量 (左下, 与图标分开不遮挡)
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "$appCount 款软件",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        // 装饰圆 (右上角, 避开文字区域)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.14f)),
        )
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

/** 分类内软件项 (现代化: 图标 + 名称 + 元信息 + 查看按钮) */
@Composable
private fun CategoryAppItem(app: AppItem, onClick: () -> Unit) {
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
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 应用图标 (圆角方块)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp)),
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
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 新版本徽标 (渐变)
                    if (app.isNew) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "新",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFF8F1F), Color(0xFFFF4D6D)),
                                    )
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        // 发布日期: 只显示月-日
                        if (app.releaseDate.isNotEmpty() && app.releaseDate.length >= 10) {
                            append("更新于 ")
                            append(app.releaseDate.substring(5, 10))
                            append(" · ")
                        }
                        if (app.version.isNotEmpty()) append("v${app.version}")
                        if (app.downloadCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${formatCount(app.downloadCount)} 次下载")
                        }
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            // 查看按钮 (胶囊)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "查看",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 下载量格式化 */
private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> String.format("%.1fw", count / 10000.0)
        count >= 1000 -> String.format("%.1fk", count / 1000.0)
        else -> count.toString()
    }
}
