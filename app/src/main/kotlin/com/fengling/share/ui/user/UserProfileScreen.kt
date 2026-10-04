package com.fengling.share.ui.user

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 用户主页 (接口契约 A 节 user_profile)。
 *
 * 从群聊、私聊里点任何人的头像/昵称都能进到这里 (含自己, 自己看不显示「发消息」)。
 * can_chat == 0 (例如被封禁) 或 is_me == 1 时隐藏底部「发消息」按钮。
 */
@Composable
fun UserProfileScreen(
    userId: Int,
    onBack: () -> Unit,
    onOpenPm: ((userId: Int, convId: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            ApiClient.userProfile(userId)
                .onSuccess { profile = it; error = "" }
                .onFailure { e -> error = e.userFriendlyMessage() }
            loading = false
        }
    }

    LaunchedEffect(userId) { load() }

    Scaffold(
        modifier = modifier,
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
                    .padding(horizontal = 12.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // ===== 大头像 + 昵称 + @用户名 + 角色标签 =====
                Card(cornerRadius = 16.dp) {
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

                Spacer(Modifier.height(10.dp))

                // ===== 简介 =====
                Card(cornerRadius = 16.dp) {
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
                Card(cornerRadius = 16.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        InfoRow(label = "注册时间", value = p.createdAt.ifBlank { "未知" })
                        InfoRow(label = "发消息数", value = p.messageCount.toString() + " 条")
                        InfoRow(label = "共同群数", value = p.sameGroups.toString() + " 个")
                    }
                }

                // ===== 底部主按钮: 发消息 (自己看 / 不能聊时隐藏) =====
                if (p.canStartChat && onOpenPm != null) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        onClick = { onOpenPm(p.id, p.convId) },
                        cornerRadius = 14.dp,
                        modifier = Modifier.fillMaxWidth(),
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
    val bg = if (isAdmin) Color(0xFFE5484D).copy(alpha = 0.14f) else MiuixTheme.colorScheme.surfaceContainerHigh
    val fg = if (isAdmin) Color(0xFFE5484D) else MiuixTheme.colorScheme.onBackgroundVariant
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
