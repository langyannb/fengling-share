package com.fengling.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppVersion
import com.fengling.share.data.CrashReporter
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.data.VersionInfo
import com.fengling.share.data.isNewerVersion
import com.fengling.share.ui.components.ProvideNavigationEventDispatcher
import com.fengling.share.ui.main.MainScreen
import com.fengling.share.ui.theme.AppTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 崩溃日志: 最先注册处理器, 再上传上次崩溃的日志
        CrashReporter.init(applicationContext)
        lifecycleScope.launch { CrashReporter.uploadPending(applicationContext) }
        super.onCreate(savedInstanceState)
        Settings.init(applicationContext)
        // OShin 同款: 状态栏 + 导航栏全透明, 关闭导航栏对比度强制 (浅色主题下默认白色 scrim 会盖住玻璃底栏下方!)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { true },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { true },
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            // 观察 themeMode + themeColor, 任一变化都触发重组换肤
            var themeMode by remember { mutableStateOf(Settings.getThemeMode()) }
            var themeColor by remember { mutableStateOf(Settings.getThemeColor()) }

            // 启动自动检查更新 (仅强制更新才自动弹窗)
            var forceUpdateInfo by remember { mutableStateOf<VersionInfo?>(null) }
            var checked by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!checked) {
                    checked = true
                    try {
                        val info = ApiClient.checkVersion()
                        if (info.forceUpdate && isNewerVersion(info.version, AppVersion.CURRENT)) {
                            forceUpdateInfo = info
                        }
                    } catch (_: Exception) { /* 网络失败静默 */ }
                }
            }

            AppTheme(
                themeMode = themeMode,
                seedColor = themeColor.seed,
            ) {
                ProvideNavigationEventDispatcher {
                    MainScreen(
                        onThemeChanged = { newMode ->
                            themeMode = newMode
                            themeColor = Settings.getThemeColor()
                        },
                    )
                }
                // 全局强制更新弹窗 (覆盖所有页面, 不可关闭)
                forceUpdateInfo?.let { info ->
                    ForceUpdateDialog(info = info)
                }
            }
        }
    }
}

/** 强制更新弹窗 - 全屏拦截, 返回键无效, 必须更新 */
@Composable
private fun ForceUpdateDialog(info: VersionInfo) {
    val context = LocalContext.current
    // 强制更新: 拦截返回键
    BackHandler { /* 强制更新: 不允许返回 */ }

    AlertDialog(
        onDismissRequest = { /* 强制更新: 不可关闭 */ },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "发现新版本",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v${info.version}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "本次为强制更新，请立即更新",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "当前版本: v${AppVersion.CURRENT}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                if (info.sizeMb > 0f || info.releaseDate.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Row {
                        if (info.sizeMb > 0f) {
                            Text(
                                text = String.format("%.1f MB", info.sizeMb),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                        if (info.releaseDate.isNotEmpty()) {
                            if (info.sizeMb > 0f) Spacer(Modifier.width(12.dp))
                            Text(
                                text = "发布于 ${info.releaseDate}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                }
                if (info.updateLog.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "更新内容:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info.updateLog,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (info.url.isNotEmpty()) {
                    // OShin 同款: 跳转更新页直接下载安装
                    context.startActivity(
                        Intent(context, UpdateActivity::class.java)
                            .putExtra("version", info.version)
                            .putExtra("url", info.url)
                            .putExtra("log", info.updateLog)
                            .putExtra("mode", info.updateMode)
                            .putExtra("size", info.sizeMb)
                            .putExtra("date", info.releaseDate)
                    )
                }
            }) {
                Text(
                    text = "立即更新",
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
