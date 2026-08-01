package com.fengling.share.ui.main.my

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MyScreen - 设置页 (Miuix 风格)
 * 通用(预测返回) / 外观(主题: 底部弹窗 + 预置色板 + 自定义取色) / 关于(版本检测)
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var showThemeSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
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

            // ===== 外观 =====
            SmallTitle(text = "外观")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
            ) {
                // 主题模式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeSheet = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "主题模式",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = themeMode.label,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                // 主题色
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeSheet = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "主题色",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = themeColor.label,
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
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
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
                            text = if (checkResult.isNotEmpty()) checkResult else "检测最新版本",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    if (checkingUpdate) {
                        Text("...", fontSize = 16.sp, color = MiuixTheme.colorScheme.primary)
                    } else {
                        TextButton(
                            text = "检查",
                            onClick = { checkVersion() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ===== 主题设置底部弹窗 =====
    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = sheetState,
            containerColor = MiuixTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                // 标题
                Text(
                    text = "主题设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(18.dp))

                // 主题模式
                SmallTitle(text = "模式")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            cornerRadius = 14.dp,
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
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mode.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))

                // 预置主题色
                SmallTitle(text = "主题色")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ThemeColor.entries.filter { it != ThemeColor.CUSTOM }.forEach { tc ->
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
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 自定义取色
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "自定义",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                    }
                }
                if (themeColor == ThemeColor.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    // Miuix 调色盘 (HSV + 透明度)
                    ColorPalette(
                        color = Color(Settings.customColor),
                        onColorChanged = { newColor ->
                            Settings.customColor = newColor.value.toLong()
                            onThemeChanged(themeMode)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
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
