package com.fengling.share.ui.main.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.LoadingBox
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.enterUp
import com.fengling.share.ui.theme.LocalAnimPageSwitch
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.pressScaleEffect
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 进程级缓存: ExploreScreen 在 NavHost 的 MAIN destination 内,
 * 进详情页时组合被销毁, 返回后 remember 全部归零。
 * 用 object 缓存让返回时列表数据立即可用(配合 rememberSaveable 的选中项,
 * 滚动位置才能被 LazyGridState 正确恢复)。
 */
private object ExploreCache {
    var categories: List<Category> = emptyList()
    var allApps: List<AppItem> = emptyList()
    val appsByKey = mutableMapOf<Int, List<AppItem>>()
}

/**
 * ExploreScreen - 分类页 (v1.1.23 重构)
 *
 *   ┌──────────────────────────────────┐
 *   │ 分类                              │
 *   │ [全部] [游戏] [工具] [Root] …      │  ← 横向滚动胶囊, 选中填充主色
 *   ├──────────────────────────────────┤
 *   │ 游戏                    48 款      │
 *   │ [全部] [王者荣耀] [和平精英] …      │  ← 子分类 chip (有才显示)
 *   │ ┌────────┐ ┌────────┐             │
 *   │ │ 图标    │ │ 图标    │             │  ← 两列卡片, 逐个淡入上浮
 *   │ │ 名称    │ │ 名称    │             │
 *   │ │ v1 · 10-21│ │ …     │             │
 *   │ └────────┘ └────────┘             │
 *   └──────────────────────────────────┘
 *
 * 为什么不再用左侧 88dp 竖栏: 中文分类名两三个字就要换行, 栏子窄得只能塞下
 * 「王者荣耀」四个字的截断版, 一屏能看的分类也少。改成顶部横滑胶囊 + 两列网格,
 * 分类名有整行宽度可用, 软件卡片也从「一行一条」变成「一屏四张」。
 */
@Composable
fun ExploreScreen(
    onAppClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

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
            if (lastKey != -1) gridState.scrollToItem(0)
            lastKey = currentKey
        }
    }

    // 当前分类名 (右侧大标题用)
    val currentCategoryName = remember(categories, selectedCategory, selectedSubCategory) {
        when {
            selectedSubCategory > 0 ->
                categories.firstOrNull { it.id == selectedSubCategory }?.name ?: "全部"
            selectedCategory > 0 ->
                categories.firstOrNull { it.id == selectedCategory }?.name ?: "全部"
            else -> "全部"
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = "分类") },
    ) { innerPadding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding).appGradientBackground()) {
                    LoadingBox(Modifier.fillMaxSize())
                }
            }

            categories.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding).appGradientBackground()) {
                    EmptyMessage(text = "暂无分类")
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .appGradientBackground(),
                ) {
                    // ── 一级分类: 横向滚动胶囊 ──
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = GlassSpacing.page, end = GlassSpacing.page, top = 4.dp, bottom = 2.dp),
                    ) {
                        item(key = "top_all") {
                            TopCategoryPill(
                                name = "全部",
                                count = allApps.size,
                                selected = selectedCategory == 0 && selectedSubCategory == 0,
                                onClick = {
                                    if (selectedCategory != 0 || selectedSubCategory != 0) {
                                        selectedCategory = 0
                                        selectedSubCategory = 0
                                    }
                                },
                            )
                        }
                        items(topCategories, key = { it.id }) { cat ->
                            TopCategoryPill(
                                name = cat.name,
                                count = topCounts[cat.id] ?: 0,
                                selected = selectedCategory == cat.id,
                                onClick = {
                                    if (selectedCategory != cat.id) {
                                        selectedCategory = cat.id
                                        selectedSubCategory = 0   // 切一级分类时重置子分类
                                    }
                                },
                            )
                        }
                    }

                    // ── 标题行: 当前分类 + 软件数 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = GlassSpacing.page, end = GlassSpacing.page, top = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = currentCategoryName,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${apps.size} 款",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    if (subCategories.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                start = GlassSpacing.page,
                                end = GlassSpacing.page,
                                top = 10.dp,
                                bottom = 4.dp,
                            ),
                        ) {
                            item(key = "sub_all") {
                                SubCategoryChip(
                                    name = "全部",
                                    selected = selectedSubCategory == 0,
                                    onClick = { selectedSubCategory = 0 },
                                )
                            }
                            items(subCategories, key = { it.id }) { sub ->
                                SubCategoryChip(
                                    name = sub.name,
                                    selected = selectedSubCategory == sub.id,
                                    onClick = { selectedSubCategory = sub.id },
                                )
                            }
                        }
                    }

                    // ── 软件网格: 切分类时上浮淡入 (与首页同一套节奏) ──
                    AnimatedContent(
                        targetState = currentKey,
                        // 占满标题与 chip 之外的剩余高度: 不给 weight 的话子项按"整屏高"测量,
                        // 列表底部会被挤出屏幕外 (底部 100dp 的留白就白留了)
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        transitionSpec = {
                            // 设置里关掉「页面转场」时: 保留 AnimatedContent 的结构 (子组合不销毁,
                            // 滚动位置与缓存都在), 只把时长压成 0 —— 视觉上就是直接换
                            if (LocalAnimPageSwitch.current) {
                                fadeIn(tween(200, delayMillis = 40, easing = FastOutSlowInEasing)) +
                                    slideInVertically(
                                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                                        initialOffsetY = { h -> h / 14 },
                                    ) togetherWith
                                    fadeOut(tween(130, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(
                                        animationSpec = tween(130, easing = FastOutSlowInEasing),
                                        targetOffsetY = { h -> -h / 20 },
                                    )
                            } else {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            }
                        },
                        label = "exploreGrid",
                    ) { key ->
                        // 转场期间上一层要显示它自己的数据 (而不是新的), 否则会「先看到新列表再淡出」
                        val shown = ExploreCache.appsByKey[key]
                            ?: if (key == currentKey) apps else emptyList()
                        when {
                            appsLoading && shown.isEmpty() -> {
                                LoadingBox(Modifier.fillMaxSize())
                            }

                            appsError.isNotEmpty() && shown.isEmpty() -> {
                                Box(Modifier.fillMaxSize()) {
                                    EmptyMessage(text = appsError)
                                }
                            }

                            shown.isEmpty() -> {
                                Box(Modifier.fillMaxSize()) {
                                    EmptyMessage(text = "该分类暂无软件")
                                }
                            }

                            else -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    state = gridState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = GlassSpacing.page,
                                        end = GlassSpacing.page,
                                        top = 6.dp,
                                        bottom = 110.dp, // 留出悬浮胶囊空间
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    itemsIndexed(shown, key = { _, app -> app.id }) { index, app ->
                                        AppGridCard(
                                            app = app,
                                            modifier = Modifier.enterUp(index),
                                            onClick = { onAppClick(app.id) },
                                        )
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
 * 一级分类胶囊。
 *
 * 选中态: 主色实底 + 反色文字 + 轻微抬起的投影; 未选中: 很淡的玻璃底。
 * ⚠️ 不读服务端下发的自定义颜色 —— 一级分类颜色统一走主题色, 换主题时整栏跟着变
 * (用户 2026-11-04 要求)。
 */
@Composable
private fun TopCategoryPill(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "topCatPress",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "topCatBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.onPrimary
        else MiuixTheme.colorScheme.onBackground,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "topCatFg",
    )
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = count.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = fg.copy(alpha = if (selected) 0.75f else 0.45f),
            )
        }
    }
}

/** 子分类筛选 chip (胶囊形, 选中主色描边+淡底, 未选中透明) */
@Composable
private fun SubCategoryChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "subChipPress",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
        else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "subChipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onBackgroundVariant,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "subChipFg",
    )
    Text(
        text = name,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

/**
 * 网格里的软件卡片: 图标 + 名称 + 版本/更新日期 + 下载量。
 * 两列布局让一屏能看到四张卡, 比原来一行一条的列表紧凑也更好看。
 */
@Composable
private fun AppGridCard(
    app: AppItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pressScaleEffect(label = "exploreCardPress")
            .glassCard(radius = GlassRadius.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
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
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (app.isNew) {
                    Text(
                        text = "新",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF8F1F), Color(0xFFFF4D6D)),
                                ),
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            Text(
                text = app.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = gridMetaOf(app),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (app.downloadCount > 0) {
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${formatCount(app.downloadCount)} 次下载",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 卡片第二行: "v1.2 · 更新于 10-21" (缺哪项就少哪项) */
private fun gridMetaOf(app: AppItem): String = buildString {
    if (app.version.isNotEmpty()) append("v${app.version}")
    if (app.releaseDate.length >= 10) {
        if (isNotEmpty()) append(" · ")
        append(app.releaseDate.substring(5, 10))
    }
    if (isEmpty()) append("暂无版本信息")
}

/** 下载量格式化 */
private fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> String.format("%.1fw", count / 10000.0)
        count >= 1000 -> String.format("%.1fk", count / 1000.0)
        else -> count.toString()
    }
}
