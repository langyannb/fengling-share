package com.fengling.share.ui.main.my

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.components.EmptyMessage
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton as M3TextButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MyScreen - 设置页 (Miuix 风格)
 * 通用(预测返回) / 外观(主题) / 关于(版本检测)
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
    var showThemeDialog by remember { mutableStateOf(false) }
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
                // 预测返回开关
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeDialog = true }
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

    // 主题选择对话框
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeMode = mode
                                    Settings.themeMode = mode.value
                                    onThemeChanged(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (themeMode == mode) "● " else "○ ",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = mode.label,
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                M3TextButton(onClick = { showThemeDialog = false }) {
                    Text("取消", color = MiuixTheme.colorScheme.primary)
                }
            },
        )
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
