package com.fengling.share.ui.user

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.UserProfile
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import com.fengling.share.ui.components.MuteOptionPicker
import com.fengling.share.ui.components.TagChips
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 管理员警示红 (和 AccountScreen 的 DangerRed / 群聊的禁言红同一个色) */
private val DangerRed = Color(0xFFE5484D)

/**
 * 用户主页 (接口契约 A / F2 节 user_profile)。
 *
 * 从群聊、私聊里点任何人的头像/昵称都能进到这里 (含自己, 自己看不显示「发消息」)。
 * can_chat == 0 (例如被封禁) 或 is_me == 1 时隐藏底部「发消息」按钮。
 *
 * @param groupId   从群聊进来时带上的群 id —— 禁言是「群内禁言」, 只对本群生效;
 *                  从私聊 / 消息中心 / 其它地方进来传 0 = 全站视角 (契约 F2)。
 * @param onMuteChanged 禁言状态变化后的回调 (群聊页可以据此刷新成员状态), 可选。
 */
@Composable
fun UserProfileScreen(
    userId: Int,
    onBack: () -> Unit,
    onOpenPm: ((userId: Int, convId: Int) -> Unit)? = null,
    groupId: Int = 0,
    onMuteChanged: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    // 禁言操作区状态
    var muteMinutes by remember { mutableStateOf(60) }
    var muteReason by remember { mutableStateOf("") }
    var muteSaving by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            ApiClient.userProfile(userId, groupId)
                .onSuccess { profile = it; error = "" }
                .onFailure { e -> error = e.userFriendlyMessage() }
            loading = false
        }
    }

    LaunchedEffect(userId, groupId) { load() }

    /** 禁言 / 重新禁言: 群内禁言只在本群生效, groupId = 0 时为全站禁言 */
    fun doMute() {
        val p = profile ?: return
        if (muteSaving) return
        scope.launch {
            muteSaving = true
            ApiClient.adminUserMute(
                userId = p.id,
                minutes = muteMinutes,
                groupId = groupId,
                reason = muteReason.trim(),
            )
                .onSuccess { left ->
                    // 服务端返回剩余时长文案 (left_text), msg 异常时优先用服务端原文
                    val tip = if (left.isBlank()) "已禁言" else "已禁言：" + left
                    Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                    muteReason = ""
                    load()
                    onMuteChanged?.invoke()
                }
                .onFailure { e ->
                    Toast.makeText(context, e.message ?: "禁言失败", Toast.LENGTH_SHORT).show()
                }
            muteSaving = false
        }
    }

    /** 解除禁言 */
    fun doUnmute() {
        val p = profile ?: return
        if (muteSaving) return
        scope.launch {
            muteSaving = true
            ApiClient.adminUserUnmute(userId = p.id, groupId = groupId)
                .onSuccess { removed ->
                    val tip = if (removed > 0) "已解除禁言" else "该用户当前未被禁言"
                    Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                    load()
                    onMuteChanged?.invoke()
                }
                .onFailure { e ->
                    Toast.makeText(context, e.message ?: "解除禁言失败", Toast.LENGTH_SHORT).show()
                }
            muteSaving = false
        }
    }

    // 契约 B: 预测性返回 (跟手) —— 手势进度 0→1 跟手右移 + 缩小淡出, 松手 <50% 回弹, >=50% 提交返回。
    // 未开「预测性返回手势动画」或低版本系统上系统不回传进度, 回调立刻正常结束 -> 直接 onBack(),
    // 功能与原来完全一致, 不会崩。
    val backProgress = rememberPredictiveBackProgress(enabled = true) { onBack() }

    Scaffold(
        modifier = modifier.predictiveBackTransform(backProgress),
        topBar = {
            AppTopBar(title = "个人主页", onBack = onBack)
        },
    ) { innerPadding ->
        val p = profile
        when {
            loading && p == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "加载中…",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }

            p == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error.ifBlank { "用户信息加载失败" },
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Card(onClick = { load() }, cornerRadius = 12.dp) {
                        Text(
                            text = "重新加载",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GlassSpacing.page)
                    // 契约 C: 页面级柔和渐变底 (静态, 零模糊)
                    .appGradientBackground(),
            ) {
                Spacer(Modifier.height(10.dp))

                // ===== 大头像 + 昵称 + 管理员标签 + @用户名 + 角色标签 =====
                Card(
                    cornerRadius = GlassRadius.card,
                    // 契约 C: 玻璃卡片 —— 卡片底色透明, 由 glassCard 画半透明填充 + 细描边 (零模糊)
                    colors = CardDefaults.defaultColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(radius = GlassRadius.card),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProfileAvatar(url = p.avatar, initial = p.displayName.take(1).ifBlank { "用" }, size = 96)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = p.displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 管理员打在这个用户身上的标签 (防骗警示), 昵称正下方
                        if (p.tags.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            TagChips(tags = p.tags)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "@" + p.username,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        RoleTag(isAdmin = p.isAdmin)
                    }
                }

                // ===== 当前禁言状态 (本群 / 全站) =====
                if (p.muted || p.globalMuted || p.canMute) {
                    Spacer(Modifier.height(10.dp))
                    Card(
                    cornerRadius = GlassRadius.card,
                    // 契约 C: 玻璃卡片 —— 卡片底色透明, 由 glassCard 画半透明填充 + 细描边 (零模糊)
                    colors = CardDefaults.defaultColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(radius = GlassRadius.card),
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "当前状态",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = muteStateText(p, groupId),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (p.muted || p.globalMuted) DangerRed
                                else MiuixTheme.colorScheme.onBackground,
                            )
                            // 剩余时间 / 原因: 优先显示本群这条, 没有再显示全站那条
                            val left = if (p.muted) p.muteLeft else p.globalMuteLeft
                            val reason = if (p.muted) p.muteReason else p.globalMuteReason
                            if (left.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "剩余时间：" + left,
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                            if (reason.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "禁言原因：" + reason,
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                }

                // ===== 管理员禁言操作区 =====
                // can_mute == 0 (自己 / 目标也是管理员 / 我不是管理员) 时整块不显示;
                // 管理员不能被禁言, UI 这里再挡一次 (契约 F2/F3)
                if (p.canMute && !p.isAdmin && !p.isMe) {
                    Spacer(Modifier.height(10.dp))
                    Card(
                    cornerRadius = GlassRadius.card,
                    // 契约 C: 玻璃卡片 —— 卡片底色透明, 由 glassCard 画半透明填充 + 细描边 (零模糊)
                    colors = CardDefaults.defaultColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(radius = GlassRadius.card),
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = if (p.muted) "重新禁言" else "禁言",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (groupId > 0) "禁言时长（只在本群生效）" else "禁言时长（全站生效）",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            MuteOptionPicker(
                                selected = muteMinutes,
                                onSelect = { muteMinutes = it },
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = muteReason,
                                onValueChange = { if (it.length <= 60) muteReason = it },
                                label = { Text("禁言原因（可选，会告知对方）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Card(
                                    onClick = { doMute() },
                                    cornerRadius = 12.dp,
                                ) {
                                    Text(
                                        text = when {
                                            muteSaving -> "处理中…"
                                            p.muted -> "重新禁言"
                                            else -> "禁言"
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DangerRed,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                    )
                                }
                                if (p.muted) {
                                    Spacer(Modifier.width(10.dp))
                                    M3TextButton(
                                        enabled = !muteSaving,
                                        onClick = { doUnmute() },
                                    ) {
                                        Text(text = "解除禁言", color = MiuixTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                if (muteReason.isNotBlank()) {
                                    Text(
                                        text = muteReason.length.toString() + "/60",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ===== 简介 =====
                Card(
                    cornerRadius = GlassRadius.card,
                    // 契约 C: 玻璃卡片 —— 卡片底色透明, 由 glassCard 画半透明填充 + 细描边 (零模糊)
                    colors = CardDefaults.defaultColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(radius = GlassRadius.card),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "简介",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = p.bio.ifBlank { "这个人很懒，什么也没留下" },
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ===== 注册时间 / 发消息数 / 共同群数 =====
                Card(
                    cornerRadius = GlassRadius.card,
                    // 契约 C: 玻璃卡片 —— 卡片底色透明, 由 glassCard 画半透明填充 + 细描边 (零模糊)
                    colors = CardDefaults.defaultColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(radius = GlassRadius.card),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        InfoRow(label = "注册时间", value = p.createdAt.ifBlank { "未知" })
                        InfoRow(label = "发消息数", value = p.messageCount.toString() + " 条")
                        InfoRow(label = "共同群数", value = p.sameGroups.toString() + " 个")
                        if (groupId > 0) InfoRow(label = "浏览视角", value = "本群（群内禁言）")
                    }
                }

                // ===== 底部主按钮: 发消息 (自己看 / 不能聊时隐藏) =====
                if (p.canStartChat && onOpenPm != null) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        onClick = { onOpenPm(p.id, p.convId) },
                        cornerRadius = GlassRadius.card,
                        colors = CardDefaults.defaultColors(
                            color = Color.Transparent,
                            contentColor = MiuixTheme.colorScheme.primary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(radius = GlassRadius.card),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "发消息",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** 「当前状态」文案: 本群已禁言 / 全站已禁言 / 正常 */
private fun muteStateText(p: UserProfile, groupId: Int): String = when {
    p.muted && groupId > 0 -> "本群已禁言"
    p.muted -> "全站已禁言"
    p.globalMuted -> "全站已禁言"
    else -> "正常"
}

/** 大头像: 有图用 Coil 圆形裁剪, 没图用昵称首字 */
@Composable
private fun ProfileAvatar(url: String, initial: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else if (initial.isNotBlank()) {
            Text(
                text = initial,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "头像",
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

/** 角色标签: 管理员红色, 普通用户灰色 */
@Composable
private fun RoleTag(isAdmin: Boolean) {
    val bg = if (isAdmin) DangerRed.copy(alpha = 0.14f) else MiuixTheme.colorScheme.surfaceContainerHigh
    val fg = if (isAdmin) DangerRed else MiuixTheme.colorScheme.onBackgroundVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (isAdmin) "管理员" else "普通用户",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

/** 一行「标签 + 值」 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
