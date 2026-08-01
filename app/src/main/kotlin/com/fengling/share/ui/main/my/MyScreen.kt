package com.fengling.share.ui.main.my

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.fengling.share.data.ApiClient
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.components.AppSubtitle
import com.fengling.share.ui.components.AppText
import com.fengling.share.ui.components.GlassCard
import com.fengling.share.ui.components.SectionTitle
import kotlinx.coroutines.launch

/**
 * MyScreen - 设置页
 * 预测返回开关 / 主题设置 / 版本检测
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    onThemeChanged: (ThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 设置状态
    var predictiveBack by remember { mutableStateOf(Settings.predictiveBackEnabled) }
    var themeMode by remember { mutableStateOf(Settings.getThemeMode()) }
    // 版本检测状态
    var checkingUpdate by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf("") }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf("") }

    // 当前版本 (build.gradle.kts versionName)
    val currentVersion = "1.0"

    fun checkVersion() {
        scope.launch {
            checkingUpdate = true
            checkResult = ""
            try {
                val info = ApiClient.checkVersion()
                if (info.version.isNotEmpty() && info.version != currentVersion) {
                    // 有新版
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
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ===== 通用设置 =====
            SectionTitle(text = "通用")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                // 预测返回开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Column(Modifier.weight(1f)) {
                        AppText("预测性返回", fontWeight = FontWeight.Medium)
                        AppSubtitle("返回手势时预览上一个页面")
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

            // ===== 外观设置 =====
            SectionTitle(text = "外观")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                // 主题模式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Brightness6,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Column(Modifier.weight(1f)) {
                        AppText("主题", fontWeight = FontWeight.Medium)
                        AppSubtitle("选择界面配色模式")
                    }
                    // 主题循环切换: 跟随系统 → 浅色 → 深色
                    TextButton(onClick = {
                        themeMode = when (themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        Settings.themeMode = themeMode.value
                        onThemeChanged(themeMode)
                    }) {
                        Text(themeMode.label, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ===== 关于 =====
            SectionTitle(text = "关于")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                // 当前版本
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Column(Modifier.weight(1f)) {
                        AppText("当前版本", fontWeight = FontWeight.Medium)
                        AppSubtitle("v$currentVersion")
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 版本检测
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Column(Modifier.weight(1f)) {
                        AppText("检查更新", fontWeight = FontWeight.Medium)
                        if (checkResult.isNotEmpty()) {
                            AppSubtitle(checkResult)
                        } else {
                            AppSubtitle("检测最新版本")
                        }
                    }
                    if (checkingUpdate) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        TextButton(onClick = { checkVersion() }) {
                            Text("检查", color = MaterialTheme.colorScheme.primary)
                        }
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
                TextButton(onClick = {
                    showUpdateDialog = false
                    if (updateUrl.isNotEmpty()) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                        )
                    } else {
                        Toast.makeText(context, "下载链接暂未配置", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("去更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
