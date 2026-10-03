package com.fengling.share.ui.main.my

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.AboutConfig
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppVersion
import com.fengling.share.data.Contributor
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor
import com.fengling.share.data.ThemeMode
import com.fengling.share.data.UserStore
import com.fengling.share.data.VersionInfo
import com.fengling.share.data.isNewerVersion
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.GitHubBrandIcon
import com.fengling.share.ui.components.QqBrandIcon
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MyScreen - 关于页 (Miuix 原生设置页风格)
 *
 * 结构: 顶部信息区 (App 名称 + 版本 + 简介) + 分组 SmallTitle/Card 设置行
 * 无滚动视差、无装饰渐变、无滚动淡入、无按压缩放动画。
 */
@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    onThemeChanged: (ThemeMode) -> Unit = {},
    onOpenWeb: (String, String) -> Unit = { _, _ -> },
    onOpenUpdate: (VersionInfo) -> Unit = {},
    onOpenContributors: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    var updateSize by remember { mutableStateOf(0f) }
    var updateDate by remember { mutableStateOf("") }

    val currentVersion = AppVersion.CURRENT

    // 消息中心未读数: 未登录清零, 登录后拉一次 (消息页内也会实时同步)
    LaunchedEffect(UserStore.hasToken) {
        if (UserStore.hasToken) {
            ApiClient.notifications(page = 1, pageSize = 1).onSuccess { page ->
                MessageBadge.update(page.unread)
            }
        } else {
            MessageBadge.update(0)
        }
    }
    val updateVersion = checkResult.substringAfter("v")

    // 登录态 (UserStore 内部是 mutableStateOf, 登录/退出/改资料后账号卡片自动重组刷新)
    val account = UserStore.current

    // 关于页配置 (官方频道/链接, 后端可配)
    var aboutConfig by remember { mutableStateOf(AboutConfig()) }
    LaunchedEffect(Unit) {
        try {
            aboutConfig = ApiClient.getAboutConfig()
        } catch (_: Exception) { }
    }

    // 投稿名单 (头像 + 投稿应用, 后端不暴露 QQ 号)
    var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            contributors = ApiClient.getContributors()
        } catch (_: Exception) { }
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
                    updateSize = info.sizeMb
                    updateDate = info.releaseDate
                    showUpdateDialog = true
                } else {
                    checkResult = "已是最新版本"
                    Toast.makeText(context, "已是最新版本 v$currentVersion", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // 不显示 e.message: 网络异常消息含服务器 IP, 不能泄露
                checkResult = e.userFriendlyMessage()
                Toast.makeText(context, "检查更新失败", Toast.LENGTH_SHORT).show()
            }
            checkingUpdate = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // ===== 顶部信息区 (普通字号/普通颜色, 无渐变无超大高度) =====
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "风",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "风铃分享库",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v$currentVersion",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "好软件，一起分享",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ===== 账号卡片 (页面最顶部) =====
        item {
            SmallTitle(text = "账号")
        }
        item {
            Card(
                onClick = { onOpenAccount() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 头像: 已登录显示真实头像 (无头像显示昵称首字), 未登录显示灰色占位
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (account != null && account.avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = account.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (account != null && account.displayName.isNotBlank()) {
                            Text(
                                text = account.initial,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (account != null) account.displayName else "点击登录 / 注册",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // 已登录但邮箱还没验证: 给个轻量提醒, 点进账号页可以自助验证
                            if (account != null && account.email.isNotBlank() && !account.emailVerified) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "邮箱未验证",
                                    fontSize = 10.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = account?.let { u -> u.bio.ifBlank { u.email } }?.takeIf { it.isNotBlank() }
                                ?: "登录后可同步收藏、评论与头像",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 已登录右侧显示「编辑」, 未登录只留箭头
                    if (account != null) {
                        Text(
                            text = "编辑",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ===== 社交 =====
        item {
            SmallTitle(text = "社交")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
            ) {
                SettingRow(
                    title = "消息中心",
                    summary = "系统通知 / 管理员公告 / 群聊提及",
                    leading = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            if (MessageBadge.unread > 0) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE5484D)),
                                )
                            }
                        }
                    },
                    value = if (MessageBadge.unread > 0) "${MessageBadge.unread} 条未读" else null,
                    onClick = onOpenMessages,
                )
            }
        }

        // ===== 外观 =====
        item {
            SmallTitle(text = "外观")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
            ) {
                SettingRow(
                    title = "主题",
                    summary = "${themeMode.label} · ${themeColor.label}",
                    icon = Icons.Filled.Palette,
                    value = if (themeExpanded) "收起" else "展开",
                    onClick = { themeExpanded = !themeExpanded },
                )
                // 展开区 (折叠动画保留, 与滚动无关)
                AnimatedVisibility(
                    visible = themeExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
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

                        // 预置色板 (精选 7 色)
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
                                            tint = MiuixTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===== 投稿名单 =====
        item {
            SmallTitle(text = "投稿名单")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
            ) {
                // 入口: 横向头像预览 (空时占位), 点击进入完整投稿名单页
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenContributors() },
                ) {
                    if (contributors.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(contributors) { c ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (c.avatar.isNotEmpty()) {
                                            AsyncImage(
                                                model = c.avatar,
                                                contentDescription = null,
                                                modifier = Modifier.size(52.dp),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Text(
                                                text = "?",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = c.name.ifBlank { "投稿人" },
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    } else {
                        // 空状态占位: 提示 + 查看入口
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "感谢每一位投稿人",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "点击查看投稿名单",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    // 底部提示条
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (contributors.isNotEmpty()) "${contributors.size} 位投稿人 · 点击查看全部" else "查看全部投稿人",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
        }

        // ===== 官方频道 =====
        item {
            SmallTitle(text = "官方频道")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
            ) {
                // 频道说明 (纯文字, 无渐变底色块)
                if (aboutConfig.bannerText.isNotEmpty() || aboutConfig.bannerSub.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                    ) {
                        if (aboutConfig.bannerText.isNotEmpty()) {
                            Text(
                                text = aboutConfig.bannerText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        if (aboutConfig.bannerSub.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = aboutConfig.bannerSub,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                    SettingDivider()
                }
                // QQ 群 (mqqapi 跳转, 无 QQ 回退网页; 彩色 QQ 群头像图标)
                SettingRow(
                    title = "加入官方QQ群",
                    summary = if (aboutConfig.qqGroup.isNotEmpty()) "群号: ${aboutConfig.qqGroup}" else "获取最新版本与专属福利",
                    leading = {
                        if (aboutConfig.qqGroup.isNotEmpty()) {
                            QqBrandIcon()
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    onClick = {
                        if (aboutConfig.qqGroup.isNotEmpty()) {
                            // 直接拉起 QQ 加群 (mqqapi), 失败再回退网页; 不依赖包可见性查询
                            // (Android 11+ 包可见性可能导致查询返回 null)
                            val qqIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=${aboutConfig.qqGroup}&card_type=group&source=qrcode"
                                )
                            )
                            try {
                                context.startActivity(qqIntent)
                            } catch (e: Exception) {
                                // QQ 未安装或 scheme 不可用 → 回退内置浏览器打开加群网页
                                if (aboutConfig.qqUrl.isNotEmpty()) {
                                    onOpenWeb(aboutConfig.qqUrl, "加入QQ群")
                                } else {
                                    Toast.makeText(context, "请安装QQ后加入群聊", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "官方群暂未配置", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                SettingDivider()
                // QQ 频道 (内置浏览器打开频道主页)
                SettingRow(
                    title = "加入QQ频道",
                    summary = if (aboutConfig.qqChannel.isNotEmpty()) "官方频道 · 最新动态" else "获取最新版本与专属福利",
                    leading = {
                        if (aboutConfig.qqChannel.isNotEmpty()) {
                            QqBrandIcon()
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    onClick = {
                        if (aboutConfig.qqChannel.isNotEmpty()) {
                            onOpenWeb(aboutConfig.qqChannel, "加入QQ频道")
                        } else {
                            Toast.makeText(context, "官方频道暂未配置", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                SettingDivider()
                SettingRow(
                    title = "意见反馈",
                    summary = "遇到问题告诉我们",
                    icon = Icons.Filled.Email,
                    onClick = {
                        if (aboutConfig.feedback.isNotEmpty()) {
                            onOpenWeb(aboutConfig.feedback, "意见反馈")
                        } else {
                            Toast.makeText(context, "反馈通道暂未配置", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        // ===== 其他 =====
        item {
            SmallTitle(text = "其他")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                cornerRadius = 16.dp,
            ) {
                SettingRow(
                    title = "检查更新",
                    summary = if (checkingUpdate) "正在检查..." else if (checkResult.isNotEmpty()) checkResult else "点击检测最新版本",
                    icon = Icons.Filled.Refresh,
                    onClick = { if (!checkingUpdate) checkVersion() },
                )
                SettingDivider()
                SettingRow(
                    title = "官方网站",
                    summary = if (aboutConfig.website.isNotEmpty()) aboutConfig.website else "访问官网了解详情",
                    icon = Icons.Filled.Language,
                    onClick = {
                        if (aboutConfig.website.isNotEmpty()) {
                            onOpenWeb(aboutConfig.website, "官方网站")
                        } else {
                            Toast.makeText(context, "官网暂未配置", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                SettingDivider()
                SettingRow(
                    title = "GitHub",
                    summary = if (aboutConfig.github.isNotEmpty()) "开源项目 · 欢迎 Star" else "开源项目",
                    leading = { GitHubBrandIcon() },
                    onClick = {
                        if (aboutConfig.github.isNotEmpty()) {
                            onOpenWeb(aboutConfig.github, "GitHub")
                        } else {
                            Toast.makeText(context, "GitHub 暂未配置", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                SettingDivider()
                SettingRow(
                    title = "捐赠支持",
                    summary = "喜欢就支持一下吧",
                    icon = Icons.Filled.Favorite,
                    onClick = {
                        if (aboutConfig.donate.isNotEmpty()) {
                            onOpenWeb(aboutConfig.donate, "捐赠支持")
                        } else {
                            Toast.makeText(context, "捐赠通道暂未配置", Toast.LENGTH_SHORT).show()
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
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
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
                    // 版本信息行 (MIUI 风格)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "当前版本",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "v$currentVersion",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "新版本",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "v$updateVersion",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    if (updateSize > 0f || updateDate.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (updateSize > 0f) {
                                Text(
                                    text = String.format("%.1f MB", updateSize),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                            if (updateDate.isNotEmpty()) {
                                if (updateSize > 0f) Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "发布于 $updateDate",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                    if (updateLog.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    text = "更新日志",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = updateLog,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (updateMode == "external") "更新将使用外部浏览器打开" else "更新将使用内置浏览器打开",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            },
            confirmButton = {
                // 普通实心按钮 (无渐变), 跳转更新页下载安装
                Card(
                    onClick = {
                        showUpdateDialog = false
                        if (updateUrl.isNotEmpty()) {
                            onOpenUpdate(
                                VersionInfo(
                                    version = updateVersion,
                                    url = updateUrl,
                                    updateLog = updateLog,
                                    updateMode = updateMode,
                                    forceUpdate = forceUpdate,
                                    sizeMb = updateSize,
                                    releaseDate = updateDate,
                                )
                            )
                        } else {
                            Toast.makeText(context, "下载链接暂未配置", Toast.LENGTH_SHORT).show()
                        }
                    },
                    cornerRadius = 10.dp,
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = "立即更新",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

/**
 * SettingRow - Miuix 原生设置行
 * 左: 可选图标 + 标题/副标题; 右: 可选 value 文本 + 箭头; 无按压缩放动画 (用系统 clickable 反馈)
 */
@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickAction: () -> Unit = onClick ?: {}
    val clickable = onClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable { clickAction() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 设置行分隔线 (卡片内, 左右留边) */
@Composable
private fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
    )
}
