package com.fengling.share.ui.main.my

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppVersion
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor
import com.fengling.share.data.ThemeMode
import com.fengling.share.data.isNewerVersion
import com.fengling.share.ui.components.AppTopBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MyScreen - 设置页 (Miuix 风格)
 * 通用(预测返回) / 外观(主题: 点击下栏折叠展开, 含模式+色板+动态取色) / 关于(版本检测)
 */
@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    onThemeChanged: (ThemeMode) -> Unit = {},
    onOpenWeb: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // 当前版本号 (独立版本文件 AppVersion, 非 build.gradle — 防改包绕过)
    val currentVersion = AppVersion.CURRENT
    // 弹窗内显示的最新版本号 (从检查结果提取, 如 "发现新版本 v1.0.1" → "1.0.1")
    val updateVersion = checkResult.substringAfter("v")

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

    Scaffold(
        topBar = {
            // OShin 式关于页大标题
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "关于",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ===== OShin 式关于头部 (App 图标 + 名称 + 版本 + 标语) =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // App 图标 (主题色圆角方块 + 风铃文字)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MiuixTheme.colorScheme.primary,
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.7f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "风",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "风铃分享库",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v$currentVersion",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "好软件，一起分享",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))

            // ===== 通用 =====
            SmallTitle(text = "通用")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "预测性返回",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (predictiveBack) "开启: 返回时页面滑动过渡" else "关闭: 直接返回无动画",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Switch(
                        checked = predictiveBack,
                        onCheckedChange = {
                            predictiveBack = it
                            Settings.predictiveBackEnabled = it
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // ===== 外观 (下栏折叠) =====
            SmallTitle(text = "外观")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
            ) {
                // 主题行 (点击展开/收起)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { themeExpanded = !themeExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "主题",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${themeMode.label} · ${themeColor.label}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    // 当前颜色圆点
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(Settings.currentSeedColor())),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (themeExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (themeExpanded) "收起" else "展开",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // 下栏折叠内容 (展开时显示)
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

                        // 预置色板 (横向可滚动, 不挤压)
                        Text(
                            text = "主题色",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(ThemeColor.entries.filter { it != ThemeColor.CUSTOM }) { tc ->
                                val selected = themeColor == tc
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(tc.seed))
                                        .clickable {
                                            themeColor = tc
                                            Settings.themeColor = tc.value
                                            onThemeChanged(themeMode)
                                        }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 动态取色 (调色盘直接可见)
                        Text(
                            text = "动态取色",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        // 自定义选中态: 显示调色盘 + 应用按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 当前自定义色圆
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(Settings.customColor))
                                    .clickable {
                                        themeColor = ThemeColor.CUSTOM
                                        Settings.themeColor = ThemeColor.CUSTOM.value
                                        onThemeChanged(themeMode)
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (themeColor == ThemeColor.CUSTOM) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
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
                        // 调色盘始终可见 (拖拽即生效)
                        ColorPalette(
                            color = Color(Settings.customColor),
                            onColorChanged = { newColor ->
                                Settings.customColor = newColor.value.toLong()
                                // 使用自定义色时实时更新
                                if (themeColor != ThemeColor.CUSTOM) {
                                    themeColor = ThemeColor.CUSTOM
                                    Settings.themeColor = ThemeColor.CUSTOM.value
                                }
                                onThemeChanged(themeMode)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ===== 关于 =====
            SmallTitle(text = "关于")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "当前版本",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "v$currentVersion",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!checkingUpdate) checkVersion() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "检查更新",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (checkingUpdate) "正在检查..." else if (checkResult.isNotEmpty()) checkResult else "点击检测最新版本",
                            fontSize = 12.sp,
                            color = if (checkResult.contains("发现新版本")) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    // Miuix 风格按钮
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary)
                            .clickable { if (!checkingUpdate) checkVersion() }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = if (checkingUpdate) "..." else "检查",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 发现新版本对话框 (美化: 图标+版本醒目+更新日志+内置/外置选择)
    if (showUpdateDialog) {
        // 强制更新: 拦截返回键 (用户必须更新, 弹窗不可关闭)
        if (forceUpdate) {
            BackHandler { /* 强制更新: 不允许返回 */ }
        }
        AlertDialog(
            onDismissRequest = {
                // 强制更新: 不可关闭; 非强制: 可关闭
                if (!forceUpdate) showUpdateDialog = false
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 更新图标
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
                // 按后端配置的更新方式: 内置=App内浏览器, 外置=系统浏览器
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
                // 强制更新: 无取消按钮; 非强制: 稍后再说
                if (!forceUpdate) {
                    M3TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后再说", color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                }
            },
        )
    }
}
