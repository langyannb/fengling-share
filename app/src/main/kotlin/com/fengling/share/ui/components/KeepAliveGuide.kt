package com.fengling.share.ui.components

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
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore
import com.fengling.share.utils.PermissionHelper
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KeepAliveGuideHost - 「划掉后台也能收消息」的一次性提示 (v1.1.2)
 *
 * 为什么要有它: OPPO / 一加 / realme 的 ColorOS 上, 用户把 App 从最近任务里划掉之后,
 * ROM 会**拒绝**重启服务 (`OplusAppStartupManager: prevent restart service, scenePriority=-1`),
 * 只有在最近任务里把这张卡片**锁定** (锁图标) 才会放行。这个动作系统没给任何 API,
 * 但用户不知道就会一直「后台收不到消息」。所以第一次进 App 讲一次。
 *
 * 克制原则 (用户明确反感反复提示):
 * - 只在 `Settings.keepaliveGuideShown == false` 时弹**一次**, 关掉立刻写 true, **永不再弹**;
 * - 不做任何 12 小时节流/循环, 不做「是否已锁定」的反复检测 (系统不告诉我们);
 * - 和 PermissionGuideHost 错开: 权限引导那条链还在跑就先等着, 不同时弹两个框。
 *
 * 调用位置: MainActivity 的 setContent 里 (AppTheme 内), 紧跟 PermissionGuideHost()。
 */
@Composable
fun KeepAliveGuideHost() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 让位给权限引导 (它可能正在弹通知权限系统框 / 应用内授权框)
        var waited = 0L
        while (waited < 60_000L && PermissionHelper.shouldGuide(context)) {
            delay(2000L)
            waited += 2000L
        }
        if (Settings.keepaliveGuideShown) return@LaunchedEffect
        // 没登录时不弹: 引导文案与「收消息」相关, 登录后才讲得通 (也就不会白占掉这一次机会)
        if (!UserStore.isLoggedIn()) return@LaunchedEffect
        show = true
    }

    if (!show) return

    AlertDialog(
        onDismissRequest = {
            show = false
            Settings.keepaliveGuideShown = true
        },
        title = {
            Text(
                text = "划掉后台也能收消息",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column {
                Text(
                    text = "请把「风铃分享库」在最近任务里锁定，否则系统会阻止它后台接收消息（这是 OPPO / 一加 / realme / 小米等国产系统必须做的一步）：",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(10.dp))
                KeepAlivePoint(
                    mark = "①",
                    title = "打开最近任务",
                    body = "按底部多任务键（或从屏幕底部上滑并停顿）",
                )
                Spacer(Modifier.height(8.dp))
                KeepAlivePoint(
                    mark = "②",
                    title = "下拉这张卡片 / 点卡片右上角菜单",
                    body = "卡片角上出现小锁图标 = 已锁定，之后划掉也不会被系统掐掉",
                )
                Spacer(Modifier.height(8.dp))
                KeepAlivePoint(
                    mark = "③",
                    title = "顺手放行自启动与电池优化",
                    body = "在「我的 → 消息通知」里两个按钮点一下就行",
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "这一步只提示一次，以后不再打扰。",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                show = false
                Settings.keepaliveGuideShown = true
                // 「去设置」= 厂商自启动页 (跳不动会自己退化到应用详情页)
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
            TextButton(onClick = {
                show = false
                Settings.keepaliveGuideShown = true
            }) {
                Text(text = "知道了", color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        },
    )
}

/** 弹框里的一行要点: 序号 + 标题 + 说明 (与 PermissionGuide 的 GuidePoint 同款排版) */
@Composable
private fun KeepAlivePoint(mark: String, title: String, body: String) {
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
