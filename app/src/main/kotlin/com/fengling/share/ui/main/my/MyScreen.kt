package com.fengling.share.ui.main.my

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.fengling.share.data.AboutConfig
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppVersion
import com.fengling.share.data.Contributor
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor
import com.fengling.share.data.ThemeMode
import com.fengling.share.data.VersionInfo
import com.fengling.share.data.isNewerVersion
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.CouiPosition
import com.fengling.share.ui.components.OShinCard
import com.fengling.share.ui.components.OShinCardTitle
import com.fengling.share.ui.components.OShinDivider
import com.fengling.share.ui.components.OShinSettingRow
import com.fengling.share.ui.theme.BgEffectView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/**
 * MyScreen - OShin 风格关于页
 * 滚动视差头部 + 卡片滚动淡入 + COUI 设置行 + 官方频道 + 检查更新悬浮按钮
 */
@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    onThemeChanged: (ThemeMode) -> Unit = {},
    onOpenWeb: (String, String) -> Unit = { _, _ -> },
    onOpenUpdate: (VersionInfo) -> Unit = {},
    onOpenContributors: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    val density = LocalDensity.current

    var predictiveBack by remember { mutableStateOf(Settings.predictiveBackEnabled) }
    var themeMode by remember { mutableStateOf(Settings.getThemeMode()) }
    var themeColor by remember { mutableStateOf(Settings.getThemeColor()) }
    var themeExpanded by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf("") }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf("") }
    var updateMode by remember { mutableStateOf("internal") }
    var updateLog by remember { mutableStateOf("") }
    var forceUpdate by remember { mutableStateOf(false) }
    var updateSize by remember { mutableStateOf(0f) }
    var updateDate by remember { mutableStateOf("") }

    val currentVersion = AppVersion.CURRENT
    val updateVersion = checkResult.substringAfter("v")

    // 关于页配置 (官方频道/链接, 后端可配)
    var aboutConfig by remember { mutableStateOf(AboutConfig()) }
    LaunchedEffect(Unit) {
        try {
            aboutConfig = ApiClient.getAboutConfig()
        } catch (_: Exception) { }
    }

    // 投稿名单 (头像 + 投稿应用, 后端不暴露 QQ 号)
    var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            contributors = ApiClient.getContributors()
        } catch (_: Exception) { }
    }

    // OShin 滚动视差动画 (精确公式: 背景/标题/版本各自独立衰减)
    var bgAlpha by remember { mutableStateOf(1f) }
    var mainAlpha by remember { mutableStateOf(1f) }
    var mainScale by remember { mutableStateOf(1f) }
    var secAlpha by remember { mutableStateOf(1f) }
    var secScale by remember { mutableStateOf(1f) }
    var updateBtnAlpha by remember { mutableStateOf(1f) }
    LaunchedEffect(scroll) {
        val bgHeight = with(density) { 332.dp.toPx() }
        val sec = with(density) { 100.dp.toPx() }
        val main = with(density) { 160.dp.toPx() }
        val mainHeight = main - sec
        snapshotFlow { Pair(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset) }
            .onEach { (index, offset) ->
                if (index == 0) {
                    val f = offset.toFloat()
                    // OShin 公式
                    bgAlpha = ((bgHeight - f / 1.6f).coerceIn(0f, bgHeight) / bgHeight).coerceIn(0f, 1f)
                    val secValue = ((sec - f / 1.8f).coerceIn(0f, sec) / sec).coerceIn(0f, 1f)
                    secAlpha = secValue
                    secScale = lerp(0.9f, 1f, secValue)
                    val mainValue = ((main - (f / 1.3f).coerceIn(sec, main)) / mainHeight).coerceIn(0f, 1f)
                    mainAlpha = (mainValue * 1.5f).coerceIn(0f, 1f)
                    mainScale = lerp(0.9f, 1f, mainValue)
                    updateBtnAlpha = (1f - f / 300f).coerceIn(0f, 1f)
                } else {
                    bgAlpha = 0f
                    mainAlpha = 0f
                    secAlpha = 0f
                    updateBtnAlpha = 0f
                }
            }
            .collect()
    }

    fun checkVersion() {
        scope.launch {
            checkingUpdate = true
            checkResult = ""
            try {
                val info = ApiClient.checkVersion()
                if (isNewerVersion(info.version, currentVersion)) {
                    checkResult = "发现新版本 v${info.version}"
                    updateUrl = info.url
                    updateMode = info.updateMode
                    updateLog = info.updateLog
                    forceUpdate = info.forceUpdate
                    updateSize = info.sizeMb
                    updateDate = info.releaseDate
                    showUpdateDialog = true
                } else {
                    checkResult = "已是最新版本"
                }
            } catch (e: Exception) {
                checkResult = "检查失败: ${e.message}"
            }
            checkingUpdate = false
        }
    }

    val isDark = isSystemInDarkTheme()
    // OShin 渐变标题色
    val titleGradient = if (isDark) {
        listOf(Color(0xFFD0A279ED.toInt()), Color(0xFFD0E3BCB1.toInt()))
    } else {
        listOf(Color(0xFFD03A18AD.toInt()), Color(0xFFD0A56138.toInt()))
    }
    // BgEffectView 模式: 1=浅色, 2=深色
    val bgEffectMode = if (isDark) 2 else 1

    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .clipToBounds(),
    ) {
        // OShin 同款动态彩色背景 (RuntimeShader 动画, 滚动淡出 bgAlpha)
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .alpha(bgAlpha),
            factory = { ctx -> BgEffectView(ctx, bgEffectMode) },
            update = { view ->
                view.updateMode(bgEffectMode)
                view.alpha = bgAlpha
            },
        )

        // 头部内容 (OShin 同款: 520dp 高垂直居中, 标题 mainAlpha + 版本 secAlpha)
        Column(
            modifier = Modifier
                .padding(top = 55.dp)
                .fillMaxWidth()
                .height(520.dp)
                .graphicsLayer {
                    // 滚动隐藏时整体消失 (图标+文字+背景框一起, 无边框残留)
                    alpha = mainAlpha
                    scaleX = mainScale
                    scaleY = mainScale
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App 图标
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MiuixTheme.colorScheme.primary,
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.65f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "风",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "风铃分享库",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    brush = Brush.linearGradient(titleGradient),
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "v$currentVersion",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "好软件，一起分享",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(30.dp))
            // 向上滑动提示 (浮动动画, 滚动后淡出)
            Box(
                modifier = Modifier.alpha(mainAlpha),
                contentAlignment = Alignment.Center,
            ) {
                val infinite = rememberInfiniteTransition()
                val floatY by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = -10f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900),
                        repeatMode = RepeatMode.Reverse,
                    ),
                )
                Column(
                    modifier = Modifier.offset(y = floatY.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "向上滑动查看更多",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // 主内容列表
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // 头部占位 (OShin 同款 520dp)
            item { Spacer(Modifier.height(520.dp)) }

            // 卡片滚动淡入
            item {
                val cardAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                        .alpha(cardAlpha),
                ) {
                    // ===== 通用 =====
                    OShinCardTitle(title = "通用")
                    OShinSettingRow(
                        title = "预测性返回",
                        summary = if (predictiveBack) "开启: 返回时页面滑动过渡" else "关闭: 直接返回无动画",
                        leftIcon = Icons.Filled.Settings,
                        position = CouiPosition.Top,
                        onClick = {
                            predictiveBack = !predictiveBack
                            Settings.predictiveBackEnabled = predictiveBack
                        },
                    )
                }
            }

            // 外观 (主题折叠)
            item {
                val cardAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                        .alpha(cardAlpha),
                ) {
                    OShinCardTitle(title = "外观")
                    OShinSettingRow(
                        title = "主题",
                        summary = "${themeMode.label} · ${themeColor.label}",
                        leftIcon = Icons.Filled.Palette,
                        rightText = if (themeExpanded) "收起" else "展开",
                        position = CouiPosition.Top,
                        onClick = { themeExpanded = !themeExpanded },
                    )
                    // 下栏折叠内容
                    AnimatedVisibility(
                        visible = themeExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        ) {
                            // 模式三选
                            Text(
                                text = "模式",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    val selected = themeMode == mode
                                    Card(
                                        onClick = {
                                            themeMode = mode
                                            Settings.themeMode = mode.value
                                            onThemeChanged(mode)
                                        },
                                        modifier = Modifier.weight(1f),
                                        cornerRadius = 10.dp,
                                        colors = if (selected) {
                                            CardDefaults.defaultColors(
                                                color = MiuixTheme.colorScheme.primary,
                                                contentColor = MiuixTheme.colorScheme.onPrimary,
                                            )
                                        } else {
                                            CardDefaults.defaultColors(
                                                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                                            )
                                        },
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = mode.label,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // 预置色板
                            Text(
                                text = "主题色",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ThemeColor.entries.filter { it != ThemeColor.CUSTOM }.forEach { tc ->
                                    val selected = themeColor == tc
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(tc.seed))
                                            .clickable {
                                                themeColor = tc
                                                Settings.themeColor = tc.value
                                                onThemeChanged(themeMode)
                                            }
                                            .padding(3.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // 动态取色
                            Text(
                                text = "动态取色",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(Settings.customColor))
                                        .clickable {
                                            themeColor = ThemeColor.CUSTOM
                                            Settings.themeColor = ThemeColor.CUSTOM.value
                                            onThemeChanged(themeMode)
                                        }
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (themeColor == ThemeColor.CUSTOM) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "自定义",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            ColorPalette(
                                color = Color(Settings.customColor),
                                onColorChanged = { newColor ->
                                    Settings.customColor = newColor.value.toLong()
                                    if (themeColor != ThemeColor.CUSTOM) {
                                        themeColor = ThemeColor.CUSTOM
                                        Settings.themeColor = ThemeColor.CUSTOM.value
                                    }
                                    onThemeChanged(themeMode)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                            )
                        }
                    }
                }
            }

            // ===== 投稿名单 =====
            item {
                val titleAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCardTitle(
                    title = "投稿名单",
                    modifier = Modifier.alpha(titleAlpha),
                )
            }
            item {
                val cardAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                        .alpha(cardAlpha),
                ) {
                    // 入口: 横向头像预览 (空时占位), 点击进入完整投稿名单页
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenContributors() },
                    ) {
                        if (contributors.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp),
                            ) {
                                items(contributors.size) { i ->
                                    val c = contributors[i]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (c.avatar.isNotEmpty()) {
                                                AsyncImage(
                                                    model = c.avatar,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(52.dp),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Text(
                                                    text = "?",
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = c.name.ifBlank { "投稿人" },
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        } else {
                            // 空状态占位: 提示 + 查看入口
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "感谢每一位投稿人",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onBackground,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "点击查看投稿名单",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        // 底部提示条
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (contributors.isNotEmpty()) "${contributors.size} 位投稿人 · 点击查看全部" else "查看全部投稿人",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // ===== 官方频道 =====
            item {
                val titleAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCardTitle(
                    title = "官方频道",
                    modifier = Modifier.alpha(titleAlpha),
                )
            }
            item {
                val cardAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                        .alpha(cardAlpha),
                ) {
                    // 频道横幅
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    )
                                )
                            )
                            .padding(16.dp),
                    ) {
                        Column {
                            Text(
                                text = aboutConfig.bannerText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = aboutConfig.bannerSub,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                    // QQ 群 (mqqapi 跳转, 无 QQ 回退网页)
                    OShinSettingRow(
                        title = "加入官方QQ群",
                        summary = if (aboutConfig.qqGroup.isNotEmpty()) "群号: ${aboutConfig.qqGroup}" else "获取最新版本与专属福利",
                        leftIcon = Icons.Filled.Person,
                        position = CouiPosition.Middle,
                        onClick = {
                            if (aboutConfig.qqGroup.isNotEmpty()) {
                                // 直接拉起 QQ 加群 (mqqapi), 失败再回退网页; 不依赖包可见性查询
                                // (Android 11+ 包可见性可能导致查询返回 null)
                                val qqIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=${aboutConfig.qqGroup}&card_type=group&source=qrcode"
                                    )
                                )
                                try {
                                    context.startActivity(qqIntent)
                                } catch (e: Exception) {
                                    // QQ 未安装或 scheme 不可用 → 回退网页加群
                                    if (aboutConfig.qqUrl.isNotEmpty()) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aboutConfig.qqUrl)))
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "请安装QQ后加入群聊", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "请安装QQ后加入群聊", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "官方群暂未配置", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    OShinDivider()
                    OShinSettingRow(
                        title = "意见反馈",
                        summary = "遇到问题告诉我们",
                        leftIcon = Icons.Filled.Email,
                        position = CouiPosition.Bottom,
                        onClick = {
                            if (aboutConfig.feedback.isNotEmpty()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aboutConfig.feedback)))
                            } else {
                                Toast.makeText(context, "反馈通道暂未配置", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            // ===== 其他 =====
            item {
                val titleAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCardTitle(
                    title = "其他",
                    modifier = Modifier.alpha(titleAlpha),
                )
            }
            item {
                val cardAlpha by derivedStateOf {
                    if (scroll.firstVisibleItemIndex > 0) 1f
                    else (scroll.firstVisibleItemScrollOffset.toFloat() / 600f).coerceIn(0f, 1f)
                }
                OShinCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                        .alpha(cardAlpha),
                ) {
                    OShinSettingRow(
                        title = "检查更新",
                        summary = if (checkingUpdate) "正在检查..." else if (checkResult.isNotEmpty()) checkResult else "点击检测最新版本",
                        leftIcon = Icons.Filled.Refresh,
                        position = CouiPosition.Top,
                        onClick = { if (!checkingUpdate) checkVersion() },
                    )
                    OShinDivider()
                    OShinSettingRow(
                        title = "官方网站",
                        summary = if (aboutConfig.website.isNotEmpty()) aboutConfig.website else "访问官网了解详情",
                        leftIcon = Icons.Filled.Language,
                        position = CouiPosition.Middle,
                        onClick = {
                            if (aboutConfig.website.isNotEmpty()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aboutConfig.website)))
                            } else {
                                Toast.makeText(context, "官网暂未配置", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    OShinDivider()
                    OShinSettingRow(
                        title = "GitHub",
                        summary = if (aboutConfig.github.isNotEmpty()) "开源项目 · 欢迎 Star" else "开源项目",
                        leftIcon = Icons.Filled.Code,
                        position = CouiPosition.Middle,
                        onClick = {
                            if (aboutConfig.github.isNotEmpty()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aboutConfig.github)))
                            } else {
                                Toast.makeText(context, "GitHub 暂未配置", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    OShinDivider()
                    OShinSettingRow(
                        title = "捐赠支持",
                        summary = "喜欢就支持一下吧",
                        leftIcon = Icons.Filled.Favorite,
                        position = CouiPosition.Middle,
                        onClick = {
                            if (aboutConfig.donate.isNotEmpty()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aboutConfig.donate)))
                            } else {
                                Toast.makeText(context, "捐赠通道暂未配置", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    OShinDivider()
                    OShinSettingRow(
                        title = "给个好评",
                        summary = "在应用商店支持我们",
                        leftIcon = Icons.Filled.ThumbUp,
                        position = CouiPosition.Bottom,
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=${context.packageName}")
                                    )
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "未找到应用商店", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Powered By 风铃分享库",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                )
            }
        }

        // OShin 检查更新悬浮按钮 (右上角, 渐变描边, 滚动淡出)
        val interaction = remember { MutableInteractionSource() }
        val isPressed by interaction.collectIsPressedAsState()
        val btnScale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "updateBtn",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 430.dp)
                .navigationBarsPadding()
                .alpha(updateBtnAlpha)
                .scale(btnScale),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                    ) { if (!checkingUpdate) checkVersion() }
                    .padding(horizontal = 32.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (checkingUpdate) "检查中..." else "检查更新",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    // 发现新版本对话框
    if (showUpdateDialog) {
        if (forceUpdate) {
            BackHandler { /* 强制更新: 不允许返回 */ }
        }
        AlertDialog(
            onDismissRequest = {
                if (!forceUpdate) showUpdateDialog = false
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "发现新版本",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "v${updateVersion}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    if (forceUpdate) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "本次为强制更新，请更新后使用",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            },
            text = {
                Column {
                    // 版本信息行 (MIUI 风格)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "当前版本",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "v$currentVersion",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "新版本",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "v$updateVersion",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    if (updateSize > 0f || updateDate.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (updateSize > 0f) {
                                Text(
                                    text = String.format("%.1f MB", updateSize),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                            if (updateDate.isNotEmpty()) {
                                if (updateSize > 0f) Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "发布于 $updateDate",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                    if (updateLog.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    text = "更新日志",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = updateLog,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (updateMode == "external") "更新将使用外部浏览器打开" else "更新将使用内置浏览器打开",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            },
            confirmButton = {
                // MIUI 风格主按钮 (渐变填充, OShin 同款: 跳转更新页下载安装)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MiuixTheme.colorScheme.primary,
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.75f),
                                )
                            )
                        )
                        .clickable {
                            showUpdateDialog = false
                            if (updateUrl.isNotEmpty()) {
                                onOpenUpdate(
                                    VersionInfo(
                                        version = updateVersion,
                                        url = updateUrl,
                                        updateLog = updateLog,
                                        updateMode = updateMode,
                                        forceUpdate = forceUpdate,
                                        sizeMb = updateSize,
                                        releaseDate = updateDate,
                                    )
                                )
                            } else {
                                Toast.makeText(context, "下载链接暂未配置", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = "立即更新",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            },
            dismissButton = {
                if (!forceUpdate) {
                    M3TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后再说", color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                }
            },
        )
    }
}
