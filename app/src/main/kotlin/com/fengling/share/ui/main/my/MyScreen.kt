package com.fengling.share.ui.main.my

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor
import com.fengling.share.data.ThemeMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MyScreen - 设置页 (Miuix 风格)
 * 通用(预测返回) / 外观(主题: 点击下栏折叠展开, 含模式+色板+动态取色) / 关于(版本检测)
 */
@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    onThemeChanged: (ThemeMode) -> Unit = {},
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

    val currentVersion = "1.0"

    fun checkVersion() {
        scope.launch {
            checkingUpdate = true
            checkResult = ""
            try {
                val info = ApiClient.checkVersion()
                if (info.version.isNotEmpty() && info.version != currentVersion) {
                    checkResult = "发现新版本 v${info.version}"
                    updateUrl = info.url
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
            TopAppBar(title = "设置")
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

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

    // 发现新版本对话框
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本") },
            text = { Text(checkResult) },
            confirmButton = {
                M3TextButton(onClick = {
                    showUpdateDialog = false
                    if (updateUrl.isNotEmpty()) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                        )
                    } else {
                        Toast.makeText(context, "下载链接暂未配置", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("去更新", color = MiuixTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                M3TextButton(onClick = { showUpdateDialog = false }) {
                    Text("取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }
}
