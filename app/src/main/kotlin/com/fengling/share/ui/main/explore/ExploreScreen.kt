package com.fengling.share.ui.main.explore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * 进程级缓存: ExploreScreen 在 NavHost 的 MAIN destination 内,
 * 进详情页时组合被销毁, 返回后 remember 全部归零。
 * 用 object 缓存让返回时列表数据立即可用(配合 rememberSaveable 的选中项,
 * 滚动位置才能被 LazyListState 正确恢复)。
 */
private object ExploreCache {
    var categories: List<Category> = emptyList()
    var allApps: List<AppItem> = emptyList()
    val appsByKey = mutableMapOf<Int, List<AppItem>>()
}

/**
 * ExploreScreen - 分类页 (左侧一级分类栏 + 右侧软件列表, 双栏联动)
 *
 *   ┌────────┬──────────────────────────┐
 *   │ 全部   │  子分类 chips (横滑, 有才显示)│
 *   │ 分类1  │  ───────────────────────  │
 *   │ 分类2  │  软件列表 (图标+名称+元信息)│
 *   └────────┴──────────────────────────┘
 * 点左侧一级分类, 右侧立即切换, 不再进二级页面。
 */
@Composable
fun ExploreScreen(
    onAppClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var categories by remember { mutableStateOf(ExploreCache.categories) }
    var allApps by remember { mutableStateOf(ExploreCache.allApps) }
    var loading by remember { mutableStateOf(ExploreCache.categories.isEmpty()) }

    // ⚠️ rememberSaveable: 进详情页返回后要恢复选中项, 否则回到「全部」
    var selectedCategory by rememberSaveable { mutableStateOf(0) }
    var selectedSubCategory by rememberSaveable { mutableStateOf(0) }
    var lastKey by rememberSaveable { mutableStateOf(-1) }

    // 实际生效的分类 key: 选了子分类用子分类, 否则用一级分类
    val currentKey = if (selectedSubCategory > 0) selectedSubCategory else selectedCategory

    var apps by remember { mutableStateOf(ExploreCache.appsByKey[currentKey] ?: emptyList()) }
    var appsLoading by remember { mutableStateOf(apps.isEmpty()) }
    var appsError by remember { mutableStateOf("") }

    val topCategories = remember(categories) { categories.filter { it.isTopLevel } }
    val subCategories = remember(categories, selectedCategory) {
        if (selectedCategory > 0) childrenOf(categories, selectedCategory) else emptyList()
    }
    // 一级分类的软件数 (含子分类)
    val topCounts = remember(categories, allApps) {
        topCategories.associate { c ->
            c.id to allApps.count { it.categoryId in categoryWithSubsIds(categories, c.id) }
        }
    }

    // 首次加载: 分类列表 + 全部软件
    LaunchedEffect(Unit) {
        if (ExploreCache.categories.isEmpty()) {
            runCatching { ApiClient.getCategories() }
                .getOrNull()
                ?.let { list ->
                    ExploreCache.categories = list
                    categories = list
                }
            runCatching { ApiClient.getApps() }
                .getOrNull()
                ?.let { list ->
                    ExploreCache.allApps = list
                    allApps = list
                    ExploreCache.appsByKey[0] = list
                }
        }
        loading = false
    }

    // 分类切换 -> 加载对应软件 (命中缓存不重复请求)
    LaunchedEffect(currentKey, categories) {
        if (categories.isEmpty()) return@LaunchedEffect
        val cached = ExploreCache.appsByKey[currentKey]
        if (cached != null) {
            apps = cached
            appsLoading = false
            appsError = ""
        } else {
            appsLoading = true
            appsError = ""
            apps = emptyList()
            runCatching { ApiClient.getApps(currentKey) }
                .onSuccess { result ->
                    ExploreCache.appsByKey[currentKey] = result
                    apps = result
                }
                .onFailure { appsError = "加载失败, 请稍后重试" }
            appsLoading = false
        }
        // 主动切换分类才回到顶部; 从详情页返回(lastKey 已恢复)不滚动
        if (lastKey != currentKey) {
            if (lastKey != -1) listState.scrollToItem(0)
            lastKey = currentKey
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "分类") },
    ) { innerPadding ->
        when {
            loading -> {
                LoadingBox(Modifier.fillMaxSize().padding(innerPadding))
            }

            categories.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    EmptyMessage(text = "暂无分类")
                }
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // ── 左侧: 一级分类栏 ──
                    LazyColumn(
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp),
                    ) {
                        item(key = "cat_all") {
                            SideCategoryItem(
                                name = "全部",
                                count = allApps.size,
                                selected = selectedCategory == 0,
                                accent = MiuixTheme.colorScheme.primary,
                                onClick = {
                                    if (selectedCategory != 0 || selectedSubCategory != 0) {
                                        selectedCategory = 0
                                        selectedSubCategory = 0
                                    }
                                },
                            )
                        }
                        items(topCategories, key = { it.id }) { cat ->
                            SideCategoryItem(
                                name = cat.name,
                                count = topCounts[cat.id] ?: 0,
                                selected = selectedCategory == cat.id,
                                accent = parseColor(cat.color),
                                onClick = {
                                    if (selectedCategory != cat.id) {
                                        selectedCategory = cat.id
                                        selectedSubCategory = 0   // 切一级分类时重置子分类
                                    }
                                },
                            )
                        }
                    }

                    // ── 右侧: 子分类 chips + 软件列表 ──
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        if (subCategories.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                item(key = "sub_all") {
                                    CategoryChip(
                                        name = "全部",
                                        selected = selectedSubCategory == 0,
                                        onClick = { selectedSubCategory = 0 },
                                    )
                                }
                                items(subCategories, key = { it.id }) { sub ->
                                    CategoryChip(
                                        name = sub.name,
                                        selected = selectedSubCategory == sub.id,
                                        onClick = { selectedSubCategory = sub.id },
                                    )
                                }
                            }
                        }

                        when {
                            appsLoading && apps.isEmpty() -> {
                                LoadingBox(Modifier.fillMaxSize())
                            }

                            appsError.isNotEmpty() && apps.isEmpty() -> {
                                Box(Modifier.fillMaxSize()) {
                                    EmptyMessage(text = appsError)
                                }
                            }

                            apps.isEmpty() -> {
                                Box(Modifier.fillMaxSize()) {
                                    EmptyMessage(text = "该分类暂无软件")
                                }
                            }

                            else -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 4.dp,
                                        bottom = 100.dp, // 留出悬浮胶囊空间
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    item(key = "count_title") {
                                        SmallTitle(text = "共 ${apps.size} 款软件")
                                    }
                                    items(apps, key = { it.id }) { app ->
                                        CategoryAppItem(app = app, onClick = { onAppClick(app.id) })
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

/**
 * 左侧一级分类项: 选中时白底圆角卡片 + 左侧主色竖条 + 主色粗体文字
 */
@Composable
private fun SideCategoryItem(
    name: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "sideCat",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.surface else Color.Transparent
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 15.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(6.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) accent else MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (count > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.65f),
                    )
                }
            }
            if (selected) Spacer(Modifier.width(9.dp)) // 与左侧竖条对齐, 文字不偏移
        }
    }
}

/**
 * 子分类筛选 chip (胶囊形, 选中主色填充, 未选中浅底)
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

/** 解析 #RRGGBB 颜色 */
private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF4C6FFF)
    }
}

/** 分类内软件项 (图标 + 名称 + 元信息 + 查看按钮) */
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
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
