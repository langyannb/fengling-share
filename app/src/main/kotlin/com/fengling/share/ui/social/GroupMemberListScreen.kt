package com.fengling.share.ui.social

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.SocialGroupMember
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.TagChips
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 禁言红 (和群聊 / 主页的警示红同色) */
private val MuteRed = Color(0xFFE5484D)

/**
 * 群成员列表页 (接口契约 B6)。
 *
 * 从群详情页的「查看全部 N 人」进来:
 *  - 顶部搜索框: 按昵称 / 用户名搜索, 输入防抖 300ms 再打 social_group_members(keyword=...)
 *  - 列表: 头像 / 昵称 / @用户名 / 标签 chips / 加入时间 / 角色角标 (群主 / 管理员)
 *  - 点某一行 → 用户主页 (带群 id, 只看这个人在本群的禁言状态)
 *  - 滑到底部自动加载下一页 (page_size = 50)
 */
@Composable
fun GroupMemberListScreen(
    groupId: Int,
    onBack: () -> Unit,
    groupName: String = "",
    onOpenUser: ((userId: Int, groupId: Int) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var keyword by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<SocialGroupMember>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    /** 拉一页成员: reset = true 从第一页重来 (搜索 / 首次进入), false = 下一页 */
    fun load(reset: Boolean) {
        if (loadingMore) return
        if (!reset && !hasMore) return
        val next = if (reset) 1 else page + 1
        loadingMore = true
        scope.launch {
            if (reset) loading = true
            ApiClient.socialGroupMembersPage(
                groupId = groupId,
                keyword = applied,
                page = next,
                pageSize = 50,
            )
                .onSuccess { p ->
                    members = if (reset) {
                        p.list
                    } else {
                        members + p.list.filter { n -> members.none { it.id == n.id } }
                    }
                    page = p.page
                    total = if (p.total > 0) p.total else members.size
                    hasMore = p.hasMore
                    error = ""
                }
                .onFailure { e ->
                    error = e.userFriendlyMessage()
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            loading = false
            loadingMore = false
        }
    }

    // 搜索防抖 300ms: 输入停下来才真正请求, 避免一边打字一边打接口
    LaunchedEffect(keyword) {
        delay(300L)
        applied = keyword.trim()
    }
    LaunchedEffect(groupId, applied) { load(true) }

    // 滑到列表底部自动加载下一页
    LaunchedEffect(listState, hasMore, members.size, loadingMore) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (hasMore && !loadingMore && members.isNotEmpty() && last >= members.size - 2) {
                    load(false)
                }
            }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (groupName.isNotBlank()) groupName + " · 群成员" else "群成员",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("搜索昵称或用户名") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                text = if (total > 0) "共 " + total + " 人" else "",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            when {
                loading && members.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "加载中…",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }

                members.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error.ifBlank {
                            if (keyword.isBlank()) "还没有成员" else "没搜到叫这个名字的人"
                        },
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(members, key = { it.id }) { m ->
                        MemberRow(
                            member = m,
                            onClick = { onOpenUser?.invoke(m.id, groupId) },
                        )
                    }
                    if (loadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "加载中…",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    } else if (!hasMore) {
                        item(key = "end") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "没有更多了",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 成员一行: 头像 / 昵称 + 角色角标 / @用户名 + 加入时间 / 标签 / 禁言中 */
@Composable
private fun MemberRow(member: SocialGroupMember, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(url = member.avatar, name = member.nickname, size = 40)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.nickname.ifBlank { member.username },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.role == "owner" || member.role == "admin") {
                    Spacer(Modifier.width(6.dp))
                    RoleBadge(role = member.role)
                }
                if (member.isAdmin) {
                    // 全站管理员 (global_role == admin), 和群角色分开标
                    Spacer(Modifier.width(6.dp))
                    GlobalAdminBadge()
                }
                if (member.muted) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "禁言中" + (if (member.muteLeft.isNotBlank()) "·" + member.muteLeft else ""),
                        fontSize = 10.sp,
                        color = MuteRed,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@" + member.username,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.joinedAt.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "加入于 " + member.joinedAt,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 1,
                    )
                }
            }
            if (member.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    TagChips(tags = member.tags, max = 3, small = true)
                }
            }
        }
    }
}

/** 角色角标: 群主 / 管理员 */
@Composable
private fun RoleBadge(role: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = if (role == "owner") "群主" else "群管理员",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

/** 全站管理员角标 (global_role == "admin") */
@Composable
private fun GlobalAdminBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "管理员",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

/** 圆形头像 (地址为空时显示名字首字) */
@Composable
private fun MemberAvatar(url: String, name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Text(
                text = name.take(1).ifBlank { "群" },
                fontSize = (size / 2.5).sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
