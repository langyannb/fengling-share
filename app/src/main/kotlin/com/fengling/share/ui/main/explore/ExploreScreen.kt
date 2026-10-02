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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.Category
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
 * ExploreScreen - 分类页 (左侧分类栏 + 右侧软件列表, 双栏联动)
 *
 * 布局:
 *   ┌────────┬──────────────────────────┐
 *   │ 全部   │  子分类 chips (横滑)      │
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
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var selectedCategory by remember { mutableStateOf(0) }    // 左侧选中的一级分类 (0 = 全部)
    var selectedSubCategory by remember { mutableStateOf(0) } // 右侧选中的子分类 (0 = 该分类全部)
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var appsLoading by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf(0) }          // 最近一次请求的分类 id, 防乱序落地

    // 分类软件缓存 (key: categoryId, 切回来不重新请求)
    val cache = remember { mutableMapOf<Int, List<AppItem>>() }
    var pageLoaded by remember { mutableStateOf(false) }

    val topCategories = remember(categories) { categories.filter { it.isTopLevel } }
    val subCategories = remember(categories, selectedCategory) {
        if (selectedCategory > 0) childrenOf(categories, selectedCategory) else emptyList()
    }

    fun loadApps(catId: Int) {
        cache[catId]?.let {
            apps = it
            appsLoading = false
            return
        }
        scope.launch {
            appsLoading = true
            val result = runCatching { ApiClient.getApps(catId) }.getOrDefault(emptyList())
            cache[catId] = result
            if (pendingKey == catId) apps = result
            appsLoading = false
        }
    }

    fun openCategory(catId: Int) {
        selectedCategory = catId
        selectedSubCategory = 0
        pendingKey = catId
        apps = cache[catId] ?: emptyList()
        loadApps(catId)
    }

    fun openSubCategory(subId: Int) {
        selectedSubCategory = subId
        pendingKey = subId
        apps = cache[subId] ?: emptyList()
        loadApps(subId)
    }

    LaunchedEffect(Unit) {
        if (!pageLoaded) {
            try {
                categories = ApiClient.getCategories()
                allApps = ApiClient.getApps()
                pageLoaded = true
            } catch (_: Exception) { }
        }
        loading = false
        pendingKey = 0
        loadApps(0)
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
                    // ── 左侧: 一级分类竖排栏 ──
                    LazyColumn(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp),
                    ) {
                        item(key = "cat_all") {
                            SideCategoryItem(
                                name = "全部",
                                count = allApps.size,
                                selected = selectedCategory == 0,
                                accent = MiuixTheme.colorScheme.primary,
                                onClick = { openCategory(0) },
                            )
                        }
                        items(topCategories, key = { it.id }) { cat ->
                            SideCategoryItem(
                                name = cat.name,
                                count = 0,
                                selected = selectedCategory == cat.id,
                                accent = parseColor(cat.color),
                                onClick = { openCategory(cat.id) },
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
                                        onClick = {
                                            selectedSubCategory = 0
                                            pendingKey = selectedCategory
                                            apps = cache[selectedCategory] ?: emptyList()
                                            loadApps(selectedCategory)
                                        },
                                    )
                                }
                                items(subCategories, key = { it.id }) { sub ->
                                    CategoryChip(
                                        name = sub.name,
                                        selected = selectedSubCategory == sub.id,
                                        onClick = { openSubCategory(sub.id) },
                                    )
                                }
                            }
                        }

                        when {
                            appsLoading && apps.isEmpty() -> {
                                LoadingBox(Modifier.fillMaxSize())
                            }

                            apps.isEmpty() -> {
                                Box(Modifier.fillMaxSize()) {
                                    EmptyMessage(text = "该分类暂无软件")
                                }
                            }

                            else -> {
                                LazyColumn(
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
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "sideCat",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.surface else Color.Transparent
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 14.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) accent else MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (count > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$count",
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                )
            }
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
