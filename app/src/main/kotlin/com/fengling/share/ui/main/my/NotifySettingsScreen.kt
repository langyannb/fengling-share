package com.fengling.share.ui.main.my

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore
import com.fengling.share.service.MessageService
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import com.fengling.share.utils.PermissionHelper
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * NotifySettingsScreen - 消息通知设置 (v1.0.35)
 *
 * 三件事:
 * 1. 「后台接收消息」总开关 —— 决定后台常驻服务 (MessageService) 跑不跑, 默认开;
 * 2. 三条必要权限 (通知 / 忽略电池优化 / 自启动) 的申请与状态展示;
 * 3. 把「为什么会有状态栏那条常驻通知」「国产系统为什么要手动放行」跟用户讲清楚。
 *
 * 开关和权限都不影响前台使用: 关掉只是「退到后台不再实时收」, 前台照旧。
 */
@Composable
fun NotifySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var serviceOn by remember { mutableStateOf(Settings.msgServiceOn) }
    // v1.1.1: 群消息提醒范围 (默认全提醒)
    var notifyGroupAll by remember { mutableStateOf(Settings.notifyGroupAll) }
    var running by remember { mutableStateOf(MessageService.isRunning) }
    var notifGranted by remember { mutableStateOf(PermissionHelper.notificationsEnabled(context)) }
    var batteryFree by remember { mutableStateOf(PermissionHelper.ignoringBattery(context)) }

    // 通知权限: 系统弹框申请, 拒了就提示 (后面还有「去系统设置」兜底入口)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
        if (!granted) {
            Toast.makeText(context, "没给通知权限, 后台收到消息也不会提醒", Toast.LENGTH_LONG).show()
        }
    }

    // 服务状态 / 权限状态每秒刷一次 (很轻, 但用户从系统设置回来后能立刻看到变化)
    LaunchedEffect(Unit) {
        while (true) {
            running = MessageService.isRunning
            notifGranted = PermissionHelper.notificationsEnabled(context)
            batteryFree = PermissionHelper.ignoringBattery(context)
            delay(1000L)
        }
    }

    // 契约 B: 预测性返回(跟手) —— 跟手右移+缩小淡出, 松手过半分提交返回, 否则回弹;
    // 未开「预测性返回手势动画」的系统上系统不回传进度, 回调立刻正常结束 -> 直接 onBack(), 功能不变。
    Scaffold(
        modifier = Modifier,
        topBar = {
            AppTopBar(
                title = "消息通知",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            // ==================== 保证后台收消息 3 步 (v1.1.2) ====================
            item {
                Spacer(Modifier.height(6.dp))
                SmallTitle(text = "保证后台收消息（3 步）")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = "把应用从最近任务里划掉之后还想收到消息，下面 3 步缺一不可：",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        KeepAliveStepRow(
                            mark = "①",
                            title = "在最近任务里把「风铃分享库」锁定",
                            body = "按底部多任务键（或从屏幕底部上滑并停顿）→ 下拉这张卡片（或点卡片右上角菜单）" +
                                "→ 卡片角上出现小锁图标就锁好了。" +
                                "没锁定时 ColorOS 会直接拒绝重启后台服务，消息就收不到；" +
                                "这一步系统没给任何接口，只能手动点一下。",
                        )
                        Spacer(Modifier.height(10.dp))
                        KeepAliveStepRow(
                            mark = "②",
                            title = "允许自启动 / 后台运行",
                            body = "点下面的按钮跳到系统设置页，把「风铃分享库」的开关打开。",
                        )
                        Spacer(Modifier.height(10.dp))
                        KeepAliveStepRow(
                            mark = "③",
                            title = "忽略电池优化",
                            body = "点下面的按钮，在弹出的系统框里选「允许」，防止息屏/省电时连接被冻结。",
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { PermissionHelper.openAutoStartSettings(context) }) {
                                Text(
                                    text = "② 允许自启动",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { PermissionHelper.openIgnoreBatterySettings(context) }) {
                                Text(
                                    text = "③ 忽略电池优化",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 总开关 ====================
            item {
                Spacer(Modifier.height(6.dp))
                SmallTitle(text = "后台接收消息")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !serviceOn
                                serviceOn = next
                                Settings.msgServiceOn = next
                                if (next) {
                                    if (UserStore.isLoggedIn()) {
                                        MessageService.start(context)
                                        Toast.makeText(context, "已开启后台接收消息", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "请先登录后再开启", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    MessageService.stop(context)
                                    Toast.makeText(context, "已关闭, 后台将不再接收消息", Toast.LENGTH_LONG).show()
                                }
                                running = MessageService.isRunning
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "后台接收消息",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "打开后即使退到桌面, 也能像 QQ/微信一样秒收消息",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 2,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = serviceOn,
                            onCheckedChange = null,
                        )
                    }
                }
            }
            // v1.1.1: 群消息提醒范围 (所有人发的都提醒 / 只提醒 @ 我的)
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !notifyGroupAll
                                notifyGroupAll = next
                                Settings.notifyGroupAll = next
                                Toast.makeText(
                                    context,
                                    if (next) "群消息将全部提醒" else "只提醒 @ 我 / @所有人的群消息",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "群消息提醒",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "打开后群里任何人发言都提醒; 关闭后只提醒 @ 我 / @所有人的消息",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 2,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = notifyGroupAll,
                            onCheckedChange = null,
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "运行状态",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (running) {
                                    "常驻服务正在运行, 通知栏能看到「风铃分享库 · 正在接收消息」"
                                } else if (serviceOn) {
                                    "开关是开的, 但服务没跑起来 (未登录或启动被系统拦了)"
                                } else {
                                    "已关闭, 后台不会收到任何消息提醒"
                                },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 3,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (running) "运行中" else "未运行",
                            fontSize = 13.sp,
                            color = if (running) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            },
                        )
                    }
                }
            }

            // ==================== 必要权限 ====================
            item {
                Spacer(Modifier.height(10.dp))
                SmallTitle(text = "必要权限")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column {
                        SettingActionRow(
                            title = "通知权限",
                            summary = "没有它, 后台收到消息也只能知道, 没法提醒你 (Android 13+ 必须手动允许)",
                            value = if (notifGranted) "已开启" else "去开启",
                            valueHighlight = !notifGranted,
                            onClick = {
                                if (notifGranted) {
                                    PermissionHelper.openNotificationSettings(context)
                                } else {
                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                        SettingActionRow(
                            title = "忽略电池优化",
                            summary = "不让系统在息屏/省电时冻结这条连接, 否则消息会延迟",
                            value = if (batteryFree) "已允许" else "去设置",
                            valueHighlight = !batteryFree,
                            onClick = { PermissionHelper.openIgnoreBatterySettings(context) },
                        )
                        SettingActionRow(
                            title = "自启动 / 后台运行",
                            summary = "小米/华为/OPPO/vivo 等国产系统要手动允许, 否则服务被清理后不会自动重连",
                            value = "去设置",
                            valueHighlight = false,
                            onClick = { PermissionHelper.openAutoStartSettings(context) },
                            last = true,
                        )
                    }
                }
            }

            // ==================== 说明 ====================
            item {
                Spacer(Modifier.height(10.dp))
                SmallTitle(text = "说明")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        ExplainLine("1. 为什么通知栏一直有「正在接收消息」?")
                        ExplainBody(
                            "为了在后台也能立刻收到消息, 应用必须保持一条与服务器的长连接。" +
                                "Android 要求这种「常驻连接」必须以前台服务运行, 而前台服务必须在通知栏留一条常驻通知" +
                                "—— 这是系统的硬性规定, 不是我们在推送广告。"
                        )
                        Spacer(Modifier.height(10.dp))
                        ExplainLine("2. 为什么国产系统还要单独设置?")
                        ExplainBody(
                            "小米/华为/OPPO/vivo/荣耀等系统会自己「省电清理」后台应用, 把服务和连接一起杀掉。" +
                                "允许「自启动」和「忽略电池优化」后, 被清理的瞬间也能自动重连, 消息才不会断。"
                        )
                        Spacer(Modifier.height(10.dp))
                        ExplainLine("3. 关掉开关会怎样?")
                        ExplainBody(
                            "关掉后常驻服务会停止, 通知栏那条常驻通知消失, 应用退到后台就不再实时收消息;" +
                                "重新打开应用时依然能收, 只是慢了 (靠轮询兜底)。前台使用完全不受影响。"
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

/** 「保证后台收消息」卡片里的一行要点: 序号 + 标题 + 说明 (v1.1.2) */
@Composable
private fun KeepAliveStepRow(mark: String, title: String, body: String) {
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

/** 权限条目: 标题 + 说明 + 右侧「去开启」 */
@Composable
private fun SettingActionRow(
    title: String,
    summary: String,
    value: String,
    valueHighlight: Boolean,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                color = if (valueHighlight) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onBackgroundVariant
                },
            )
        }
        if (!last) {
            Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun ExplainLine(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onBackground,
    )
}

@Composable
private fun ExplainBody(text: String) {
    Spacer(Modifier.height(3.dp))
    Text(
        text = text,
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
}
