package com.fengling.share.ui.main.my

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.AboutConfig
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppVersion
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor
import com.fengling.share.data.ThemeMode
import com.fengling.share.data.isNewerVersion
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.CouiPosition
import com.fengling.share.ui.components.OShinCard
import com.fengling.share.ui.components.OShinCardTitle
import com.fengling.share.ui.components.OShinDivider
import com.fengling.share.ui.components.OShinSettingRow
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()

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

    val currentVersion = AppVersion.CURRENT
    val updateVersion = checkResult.substringAfter("v")

    // 关于页配置 (官方频道/链接, 后端可配)
    var aboutConfig by remember { mutableStateOf(AboutConfig()) }
    LaunchedEffect(Unit) {
        try {
            aboutConfig = ApiClient.getAboutConfig()
        } catch (_: Exception) { }
    }

    // OShin 滚动视差动画: 头部随滚动缩放/淡出
    var headerAlpha by remember { mutableStateOf(1f) }
    var headerScale by remember { mutableStateOf(1f) }
    var updateBtnAlpha by remember { mutableStateOf(1f) }
    LaunchedEffect(scroll) {
        snapshotFlow { Pair(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset) }
            .onEach { (index, offset) ->
                if (index == 0) {
                    val f = offset.toFloat()
                    headerAlpha = ((520f - f / 1.6f).coerceIn(0f, 520f) / 520f).coerceIn(0f, 1f)
                    headerScale = 1f - f / 2000f
                    updateBtnAlpha = (1f - f / 300f).coerceIn(0f, 1f)
                } else {
                    headerAlpha = 0f
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

    Box(Modifier.fillMaxSize()) {
        // 头部背景渐变 (滚动时淡出)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .alpha(headerAlpha)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MiuixTheme.colorScheme.background,
                        )
                    )
                ),
        )

        // 头部内容 (App 名 + 版本号, 滚动缩放淡出)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 60.dp),
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
                    )
                    .scale(headerScale)
                    .alpha(headerAlpha),
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
                modifier = Modifier
                    .scale(headerScale)
                    .alpha(headerAlpha),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "v$currentVersion",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier
                    .scale(headerScale)
                    .alpha(headerAlpha),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "好软件，一起分享",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .scale(headerScale)
                    .alpha(headerAlpha),
            )
        }

        // 主内容列表
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // 头部占位
            item { Spacer(Modifier.height(280.dp)) }

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

            // ===== 官方频道 =====
            item { OShinCardTitle(title = "官方频道") }
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
                                val qqIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=${aboutConfig.qqGroup}&card_type=group&source=qrcode"
                                    )
                                )
                                if (qqIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(qqIntent)
                                } else if (aboutConfig.qqUrl.isNotEmpty()) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aboutConfig.qqUrl)))
                                } else {
                                    Toast.makeText(context, "请安装QQ后加入群聊", Toast.LENGTH_SHORT).show()
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
            item { OShinCardTitle(title = "其他") }
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
                        leftIcon = Icons.Filled.Star,
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
                        leftIcon = Icons.Filled.Star,
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
                        leftIcon = Icons.Filled.ThumbUp,
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

        // OShin 检查更新悬浮按钮 (渐变描边, 滚动淡出)
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
                .padding(top = 380.dp)
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
                    Text(
                        text = "当前版本: v$currentVersion",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    if (updateLog.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "更新内容:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = updateLog,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            },
            confirmButton = {
                M3TextButton(onClick = {
                    showUpdateDialog = false
                    if (updateUrl.isNotEmpty()) {
                        if (updateMode == "external") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                        } else {
                            onOpenWeb(updateUrl, "更新下载")
                        }
                    } else {
                        Toast.makeText(context, "下载链接暂未配置", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(
                        text = if (updateMode == "external") "去更新" else "立即更新",
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
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
