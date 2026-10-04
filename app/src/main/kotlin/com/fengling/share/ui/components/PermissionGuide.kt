package com.fengling.share.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.utils.PermissionHelper
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * PermissionGuideHost - 一进 App 就走的权限引导 (v1.0.36)
 *
 * 用户不会主动去「我的 → 消息通知」点那三个按钮, 所以启动时直接推到他面前:
 *
 * 第 1 步 (系统框, 用户点一下就完事): 通知权限未授予 -> RequestPermission(POST_NOTIFICATIONS);
 * 第 2 步 (我们自己的弹框): 系统框关掉后, 通知或电池优化仍不满足 -> 弹「让消息不再漏接」;
 * 第 3 步 (一键跳系统框): 点「一键允许」-> ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (系统一键允许框),
 *        随后再弹一个提示「厂商自启动」, 「去设置」跳厂商页面 (没有公开 API, 只能用户点一下)。
 *
 * 节流: 走完一轮就写 Settings.permGuideTs, 12 小时内不再弹, 一次启动最多一轮, 绝不循环弹。
 * 调用位置: MainActivity 的 setContent 里 (AppTheme 内), 与主界面同层。
 */
@Composable
fun PermissionGuideHost() {
    val context = LocalContext.current

    // 「让消息不再漏接」主弹框 / 厂商自启动提示框
    var showGuide by remember { mutableStateOf(false) }
    var showAutoStart by remember { mutableStateOf(false) }

    // 第 1 步: 系统通知权限框。无论允许还是拒绝都会回调到这里。
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val batteryFree = PermissionHelper.ignoringBattery(context)
        // 第 2 步: 任一仍不满足 -> 应用内一键引导; 都满足了这一轮就结束
        if (!granted || !batteryFree) {
            showGuide = true
        } else {
            PermissionHelper.markGuided(context)
        }
    }

    // 启动判定: 状态驱动 (登录 + 开关 + 权限缺 + 超 12 小时), 只在进入时跑一轮
    LaunchedEffect(Unit) {
        // 稍等一会儿再弹: onCreate 阶段 Activity 还没 RESUMED, 立刻申请权限框可能弹不出来
        delay(700L)
        if (!PermissionHelper.shouldGuide(context)) return@LaunchedEffect

        if (!PermissionHelper.notificationsEnabled(context)) {
            val launched = runCatching {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }.isSuccess
            // 少数 ROM 系统框弹不出来时兜底: 直接进应用内引导 (里面能跳系统设置页)
            if (!launched) showGuide = true
        } else if (!PermissionHelper.ignoringBattery(context)) {
            showGuide = true
        } else {
            PermissionHelper.markGuided(context)
        }
    }

    // ==================== 第 2 步: 一键引导弹框 ====================
    if (showGuide) {
        AlertDialog(
            onDismissRequest = {
                showGuide = false
                PermissionHelper.markGuided(context)
            },
            title = {
                Text(
                    text = "让消息不再漏接",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    Text(
                        text = "为了在后台也能秒收消息，请允许「风铃分享库」在后台运行：",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    GuidePoint(
                        mark = "①",
                        title = "忽略电池优化",
                        body = "防系统省电掐断这条长连接",
                    )
                    Spacer(Modifier.height(8.dp))
                    GuidePoint(
                        mark = "②",
                        title = "允许自启动 / 后台运行",
                        body = "国产系统必需，比如 OPPO 的「电池 → 应用耗电管理 → 允许后台运行」",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showGuide = false
                    // 第 3 步: 电池优化没忽略就发系统「一键允许」框
                    if (!PermissionHelper.ignoringBattery(context)) {
                        PermissionHelper.openIgnoreBatterySettings(context)
                    }
                    PermissionHelper.markGuided(context)
                    // 自启动没有公开 API, 只能再弹一个框把用户送过去
                    showAutoStart = true
                }) {
                    Text(
                        text = "一键允许",
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGuide = false
                    PermissionHelper.markGuided(context)
                }) {
                    Text(text = "稍后再说", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }

    // ==================== 第 3 步之二: 厂商自启动提示 ====================
    if (showAutoStart) {
        AlertDialog(
            onDismissRequest = { showAutoStart = false },
            title = {
                Text(
                    text = "最后一步：允许自启动",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    Text(
                        text = "小米 / 华为 / OPPO / vivo 等系统会在息屏后清理后台应用，" +
                            "允许「自启动」后连接被掐断也能自动重连。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "系统不允许应用自动开启这一项，点一下「去设置」在列表里打开就好。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAutoStart = false
                    PermissionHelper.openAutoStartSettings(context)
                }) {
                    Text(
                        text = "去设置",
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoStart = false }) {
                    Text(text = "我知道了", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }
}

/** 弹框里的一行要点: 序号 + 标题 + 说明 */
@Composable
private fun GuidePoint(mark: String, title: String, body: String) {
    Row {
        Text(
            text = mark,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}
