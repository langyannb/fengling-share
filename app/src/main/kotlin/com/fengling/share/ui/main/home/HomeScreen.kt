package com.fengling.share.ui.main.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.Banner
import com.fengling.share.data.Category
import com.fengling.share.data.childrenOf
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.LoadingBox
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.pressScaleEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HomeScreen - 首页
 * 搜索 + 轮播 + 分类胶囊 + 软件列表
 * 设计取向: Miuix 原生(设置页式) —— 无渐变装饰、无弹跳缩放、色板只用 MiuixTheme,
 * 层级靠字号/字重/间距/分组表达, 而不是彩色块与阴影。
 */
@Composable
fun HomeScreen(
    onAppClick: (Int) -> Unit,
    onOpenUrl: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var banners by remember { mutableStateOf<List<Banner>>(emptyList()) }
    // 分类缓存: 每个分类的 apps 缓存, 切换不重新加载
    val appsCache = remember { mutableStateMapOf<Int, List<AppItem>>() }
    // ⚠️ rememberSaveable: 进入详情页时 HomeScreen 离开组合, 返回后要恢复
    // 所选分类/子分类/搜索词, 否则会重置回"全部" (用户反馈 bug)
    var selectedCategory by rememberSaveable { mutableStateOf(0) }
    var selectedSubCategory by rememberSaveable { mutableStateOf(0) } // 0=父分类全部
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    // 顶级分类 (宫格只显示顶级; 子分类以 chips 形式出现在选中分类下)
    val topCategories = remember(categories) { categories.filter { it.isTopLevel } }
    // 当前选中顶级分类的子分类
    val subCategories = remember(categories, selectedCategory) {
        childrenOf(categories, selectedCategory)
    }
    // 实际查询分类: 选中子分类用子分类 id, 否则父分类 (后端父分类自动包含子分类软件)
    val effectiveCategoryId = if (selectedSubCategory > 0) selectedSubCategory else selectedCategory

    fun loadApps(catId: Int, kw: String) {
        // 命中缓存直接返回 (不重新加载)
        val cacheKey = "$catId|$kw"
        if (kw.isEmpty()) {
            appsCache[catId]?.let {
                apps = it
                return
            }
        }
        scope.launch {
            loading = true
            error = ""
            try {
                val result = ApiClient.getApps(catId, kw)
                apps = result
                if (kw.isEmpty()) appsCache[catId] = result
            } catch (e: Exception) {
                error = e.userFriendlyMessage()
            }
            loading = false
        }
    }

    // 键盘收起时自动收起搜索 (用户反馈: 收起键盘后搜索栏还挂在界面上)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var imeWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, searchExpanded) {
        if (imeVisible) {
            imeWasShown = true
        } else if (imeWasShown) {
            imeWasShown = false
            if (searchExpanded) {
                searchExpanded = false
                query = ""
                loadApps(effectiveCategoryId, "")
            }
        }
    }


    fun refresh() {
        scope.launch {
            refreshing = true
            try {
                categories = ApiClient.getCategories()
                appsCache.clear() // 下拉刷新: 清缓存强制重新加载
                apps = ApiClient.getApps(effectiveCategoryId, query)
                if (query.isEmpty()) appsCache[effectiveCategoryId] = apps
            } catch (_: Exception) { }
            refreshing = false
        }
    }

    // 切换分类 (顶级宫格 / 子分类 chips 共用入口)
    fun selectCategory(catId: Int) {
        selectedSubCategory = 0
        selectedCategory = catId
        loadApps(catId, query)
    }

    fun selectSubCategory(subId: Int) {
        selectedSubCategory = subId
        loadApps(subId, query)
    }

    LaunchedEffect(Unit) {
        // 首次进入: 加载分类 + 轮播; 应用列表按当前选中分类加载
        // (rememberSaveable 恢复后 selectedCategory 可能是非 0, 不能硬编码 0)
        if (appsCache[effectiveCategoryId]?.isNotEmpty() == true || banners.isNotEmpty()) {
            return@LaunchedEffect
        }
        try {
            categories = ApiClient.getCategories()
            banners = ApiClient.getBanners()
        } catch (_: Exception) { }
        loadApps(effectiveCategoryId, query)
    }

    val isDark = isSystemInDarkTheme()
    Scaffold(
        topBar = {
            // 极简顶部占位: 只保留状态栏高度 + 6dp 间距
            // (原「风铃分享库 / 发现好软件 · 分享新乐趣」大标题区已移除, 释放垂直空间)
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .height(6.dp),
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .appGradientBackground()
                // 搜索展开时, 点空白处取消搜索 (子层 clickable 消费点击, 不触发这里)
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (searchExpanded) {
                            searchExpanded = false
                            query = ""
                            loadApps(effectiveCategoryId, "")
                        }
                    }
                },
        ) {
            // Miuix 搜索栏
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = {
                            query = it
                            loadApps(effectiveCategoryId, it)
                        },
                        onSearch = { loadApps(effectiveCategoryId, it) },
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

            // 顶部轮播图
            if (banners.isNotEmpty()) {
                BannerCarousel(
                    banners = banners,
                    onBannerClick = { banner ->
                        if (banner.url.isNotEmpty()) {
                            onOpenUrl(banner.url, banner.title.ifEmpty { "轮播" })
                        } else {
                            banner.appId?.let { onAppClick(it) }
                        }
                    },
                )
            }

            // 顶级分类 (胶囊)
            if (topCategories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    item {
                        CategoryChip(
                            name = "全部",
                            icon = "",
                            selected = selectedCategory == 0,
                            onClick = { selectCategory(0) },
                        )
                    }
                    items(topCategories, key = { it.id }) { cat ->
                        CategoryChip(
                            name = cat.name,
                            icon = cat.icon,
                            selected = selectedCategory == cat.id,
                            onClick = { selectCategory(cat.id) },
                        )
                    }
                }
            }

            // 子分类 chips: 选中顶级分类且有子分类时显示 (更具体地寻找应用)
            if (subCategories.isNotEmpty() && query.isEmpty()) {
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
                            icon = "",
                            selected = selectedSubCategory == 0,
                            onClick = { selectCategory(selectedCategory) },
                        )
                    }
                    items(subCategories, key = { it.id }) { sub ->
                        CategoryChip(
                            name = sub.name,
                            icon = sub.icon,
                            selected = selectedSubCategory == sub.id,
                            onClick = { selectSubCategory(sub.id) },
                        )
                    }
                }
            }

            // 列表区 (只做短淡入淡出, 不做左右滑动)
            AnimatedContent(
                targetState = HomeListState(effectiveCategoryId, apps, loading, error),
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                },
                label = "homeList",
            ) { state ->
                when {
                    state.loading && state.apps.isEmpty() -> {
                        LoadingBox(Modifier.fillMaxSize())
                    }
                    state.error.isNotEmpty() && state.apps.isEmpty() -> {
                        EmptyMessage(text = state.error)
                    }
                    state.apps.isEmpty() -> {
                        EmptyMessage(text = "暂无软件")
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = GlassSpacing.page, end = GlassSpacing.page, top = 4.dp,
                                bottom = 100.dp, // 留出悬浮胶囊空间, 内容可滚到胶囊下方被模糊
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            item {
                                SmallTitle(
                                    text = if (state.categoryId == 0) "全部软件" else "共 ${state.apps.size} 款软件",
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(state.apps, key = { it.id }) { app ->
                                AppListItem(app = app, onClick = { onAppClick(app.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分类入口 (Miuix 胶囊: 选中主色填充, 未选中浅底; 不再用渐变图标块与弹跳动画) */
@Composable
private fun CategoryChip(
    name: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isNotEmpty()) {
            AsyncImage(
                model = icon,
                contentDescription = name,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.onPrimary
            else MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
        )
    }
}

/** 列表角标 (统一形状与色板, 不再用橙色渐变) */
@Composable
private fun AppTag(text: String, highlight: Boolean = false) {
    Spacer(Modifier.width(6.dp))
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = if (highlight) MiuixTheme.colorScheme.onPrimary
        else MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlight) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceContainerHigh
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** 软件列表项 (Miuix 设置行风格: 图标 + 名称/元信息 + 右箭头, 整行可点, 无自绘按压缩放) */
@Composable
private fun AppListItem(app: AppItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackground,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .pressScaleEffect(label = "homeAppPress")
            .glassCard(radius = GlassRadius.card),
        cornerRadius = GlassRadius.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant),
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (app.isNew) AppTag(text = "新", highlight = true)
                    if (app.isTop) AppTag(text = "顶")
                    if (app.isFeatured) AppTag(text = "精")
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (app.categoryName.isNotEmpty()) append(app.categoryName)
                        if (app.version.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("v")
                            append(app.version)
                        }
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (app.releaseDate.isNotEmpty() && app.releaseDate.length >= 10) {
                            append("更新于 ")
                            append(app.releaseDate.substring(5, 10))
                            append(" · ")
                        }
                        append(formatCount(app.downloadCount))
                        append(" 次下载")
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp),
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

/** 顶部轮播图 (HorizontalPager + 自动轮播 + 指示器; 不做相邻页缩放/变淡) */
@Composable
private fun BannerCarousel(
    banners: List<Banner>,
    onBannerClick: (Banner) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { banners.size })

    // 自动轮播 (4s 切换, 无限循环)
    LaunchedEffect(banners.size) {
        if (banners.size > 1) {
            while (true) {
                delay(4000)
                val next = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            pageSpacing = 8.dp,
        ) { page ->
            val banner = banners[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onBannerClick(banner) },
            ) {
                AsyncImage(
                    model = banner.image,
                    contentDescription = banner.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // 渐变遮罩 + 标题 (仅用于图片上的文字可读性)
                if (banner.title.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                )
                            ),
                    )
                    Text(
                        text = banner.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (banners.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(banners.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(width = if (selected) 16.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.3f)
                            ),
                    )
                }
            }
        }
    }
}
/** 首页列表状态 (AnimatedContent targetState) */
private data class HomeListState(
    val categoryId: Int,
    val apps: List<AppItem>,
    val loading: Boolean,
    val error: String,
)
