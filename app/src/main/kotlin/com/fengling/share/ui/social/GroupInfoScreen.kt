package com.fengling.share.ui.social

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.SocialGroup
import com.fengling.share.data.SocialGroupMember
import com.fengling.share.data.UserStore
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 危险操作红 (退出群聊 / 全员禁言开启态, 和主页那边的警示红同色) */
private val DangerRed = Color(0xFFE5484D)

/**
 * 群详情页 (接口契约 B6)。
 *
 * 从聊天页右上角「☰」进来, QQ 群设置那种结构:
 *  1. 群头像 + 群名 + 群号 + 成员数 (接口 social_group_public / social_groups 的 member_count)
 *  2. 群公告 (可展开全文; 全站管理员或群主能发布 / 编辑, 走 social_group_notice_set)
 *  3. 群相册 (social_group_images 分页; 3 列网格, 点开大图能左右翻页 / 保存到相册)
 *  4. 群成员 (前 10 个头像横滑 + 「查看全部 N 人」→ 成员列表页)
 *  5. 设置 (消息免打扰 / 全员禁言(仅管理员) / 退出群聊(非群主))
 *
 * 登录态是硬前提: 未登录时只给一句提示 + 返回 (群详情里的接口都要带 token)。
 *
 * @param onOpenMembers 点「查看全部 N 人」: 打开成员列表页 (传群 id)
 * @param onOpenUser    点群成员头像 / 系统消息里的人名: 打开用户主页 (带群 id, 只看本群禁言)
 * @param onLeft        退出群聊成功后回调: 一般用来退回群列表
 */
@Composable
fun GroupInfoScreen(
    groupId: Int,
    onBack: () -> Unit,
    onOpenMembers: (groupId: Int) -> Unit = {},
    onOpenUser: ((userId: Int, groupId: Int) -> Unit)? = null,
    onLeft: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var group by remember { mutableStateOf<SocialGroup?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<SocialGroupMember>>(emptyList()) }
    var memberTotal by remember { mutableStateOf(0) }
    var meRole by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<ApiClient.GroupImage>>(emptyList()) }
    var imagePage by remember { mutableStateOf(1) }
    var imageTotal by remember { mutableStateOf(0) }
    var imageHasMore by remember { mutableStateOf(false) }
    var imagesLoading by remember { mutableStateOf(false) }
    var noticeExpanded by remember { mutableStateOf(false) }
    var noticeDraft by remember { mutableStateOf("") }
    var showNoticeEditor by remember { mutableStateOf(false) }
    var noticeSaving by remember { mutableStateOf(false) }
    var muteSaving by remember { mutableStateOf(false) }
    var allMuteSaving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf(-1) }
    var savingImage by remember { mutableStateOf(false) }

    val isAdmin = UserStore.current?.role == "admin"
    val isOwner = meRole == "owner"

    /**
     * 群资料: 用 social_groups (已上线的接口) 全量列表里挑这个群。
     * 里面已经带了 member_count / is_member / notice / muted / all_muted, 不用另开接口。
     */
    fun loadGroup() {
        scope.launch {
            loading = true
            ApiClient.socialGroups()
                .onSuccess { list ->
                    val g = list.firstOrNull { it.id == groupId }
                    if (g == null) {
                        error = "群不存在, 或你已经不在这个群里了"
                    } else {
                        group = g
                        error = ""
                    }
                }
                .onFailure { e -> error = e.userFriendlyMessage() }
            loading = false
        }
    }

    /** 成员: 只取第一页 (前 10 个够画头像墙), 顺手拿 member_count / 我的角色 */
    fun loadMembers() {
        scope.launch {
            ApiClient.socialGroupMembersPage(groupId = groupId, page = 1, pageSize = 10)
                .onSuccess { p ->
                    members = p.list
                    memberTotal = if (p.memberCount > 0) p.memberCount else p.total
                    val meId = UserStore.current?.id ?: 0
                    meRole = p.list.firstOrNull { it.id == meId }?.role ?: ""
                }
        }
    }

    /** 群相册分页 (page 从 1 开始, 服务端 page_size 默认 30 最大 60) */
    fun loadImages(reset: Boolean) {
        if (imagesLoading) return
        val next = if (reset) 1 else imagePage + 1
        scope.launch {
            imagesLoading = true
            ApiClient.socialGroupImages(groupId = groupId, page = next, pageSize = 30)
                .onSuccess { p ->
                    images = if (reset) {
                        p.list
                    } else {
                        images + p.list.filter { n -> images.none { it.id == n.id } }
                    }
                    imagePage = p.page
                    imageTotal = p.total
                    imageHasMore = p.hasMore
                }
                .onFailure { e ->
                    Toast.makeText(context, e.userFriendlyMessage(), Toast.LENGTH_SHORT).show()
                }
            imagesLoading = false
        }
    }

    /** 消息免打扰: 直接复用聊天页用的 social_mute_set */
    fun setMuted(m: Boolean) {
        if (muteSaving) return
        scope.launch {
            muteSaving = true
            ApiClient.socialMuteSet(groupId, m)
                .onSuccess {
                    group = group?.copy(muted = m)
                    Toast.makeText(
                        context,
                        if (m) "已开启消息免打扰" else "已关闭消息免打扰",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure { e ->
                    Toast.makeText(context, e.userFriendlyMessage(), Toast.LENGTH_SHORT).show()
                }
            muteSaving = false
        }
    }

    /** 全员禁言: 只有管理员看得到这一行 (服务端也会再挡一次) */
    fun setAllMuted(m: Boolean) {
        if (allMuteSaving) return
        scope.launch {
            allMuteSaving = true
            ApiClient.socialGroupAllMuteSet(groupId, m)
                .onSuccess {
                    group = group?.copy(allMuted = m)
                    Toast.makeText(
                        context,
                        if (m) "已开启全员禁言" else "已关闭全员禁言",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure { e ->
                    Toast.makeText(context, e.userFriendlyMessage(), Toast.LENGTH_SHORT).show()
                }
            allMuteSaving = false
        }
    }

    /** 发布 / 编辑群公告 (留空 = 清空公告, 服务端限制 500 字) */
    fun saveNotice() {
        if (noticeSaving) return
        scope.launch {
            noticeSaving = true
            val text = noticeDraft.trim()
            ApiClient.socialSetNotice(groupId, text)
                .onSuccess {
                    group = group?.copy(notice = text)
                    showNoticeEditor = false
                    Toast.makeText(context, "公告已更新", Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    Toast.makeText(context, e.userFriendlyMessage(), Toast.LENGTH_SHORT).show()
                }
            noticeSaving = false
        }
    }

    /** 退出群聊: 群主退不了, 服务端会回中文原文, 这里原样 Toast */
    fun doLeave() {
        if (leaving) return
        scope.launch {
            leaving = true
            ApiClient.socialGroupLeave(groupId)
                .onSuccess {
                    showLeaveConfirm = false
                    Toast.makeText(context, "已退出群聊", Toast.LENGTH_SHORT).show()
                    onLeft()
                }
                .onFailure { e ->
                    Toast.makeText(context, e.message ?: "退出群聊失败", Toast.LENGTH_SHORT).show()
                }
            leaving = false
        }
    }

    LaunchedEffect(groupId) {
        loadGroup()
        loadMembers()
        loadImages(true)
    }

    if (!UserStore.isLoggedIn()) {
        Scaffold(
            topBar = { AppTopBar(title = "群详情", onBack = onBack) },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "登录后才能查看群详情",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
        return
    }

    Scaffold(
        topBar = { AppTopBar(title = "群详情", onBack = onBack) },
    ) { innerPadding ->
        val g = group
        when {
            loading && g == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "加载中…",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }

            g == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error.ifBlank { "群资料加载失败" },
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Card(onClick = { loadGroup() }, cornerRadius = 12.dp) {
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

            else -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    item { GroupHeaderCard(group = g) }
                    item {
                        NoticeCard(
                            notice = g.notice,
                            expanded = noticeExpanded,
                            canEdit = isAdmin || isOwner,
                            onToggle = { noticeExpanded = !noticeExpanded },
                            onEdit = {
                                noticeDraft = g.notice
                                showNoticeEditor = true
                            },
                        )
                    }
                    item {
                        AlbumCard(
                            images = images,
                            total = imageTotal,
                            loading = imagesLoading,
                            hasMore = imageHasMore,
                            onOpen = { index -> previewIndex = index },
                            onLoadMore = { loadImages(false) },
                        )
                    }
                    item {
                        MembersCard(
                            members = members,
                            total = memberTotal,
                            onOpenAll = { onOpenMembers(groupId) },
                            onOpenUser = { uid -> onOpenUser?.invoke(uid, groupId) },
                        )
                    }
                    item {
                        SettingsCard(
                            muted = g.muted,
                            allMuted = g.allMuted,
                            isAdmin = isAdmin,
                            isMember = g.isMember,
                            isOwner = isOwner,
                            muteSaving = muteSaving,
                            allMuteSaving = allMuteSaving,
                            onToggleMute = { m -> setMuted(m) },
                            onToggleAllMute = { m -> setAllMuted(m) },
                            onLeave = { showLeaveConfirm = true },
                        )
                    }
                }

                // ===== 群公告发布 / 编辑 =====
                if (showNoticeEditor) {
                    AlertDialog(
                        onDismissRequest = { if (!noticeSaving) showNoticeEditor = false },
                        title = { Text(text = if (g.notice.isBlank()) "发布群公告" else "编辑群公告") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = noticeDraft,
                                    onValueChange = { if (it.length <= 500) noticeDraft = it },
                                    label = { Text("公告内容（留空表示清空公告）") },
                                    minLines = 4,
                                    maxLines = 8,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = noticeDraft.length.toString() + "/500",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !noticeSaving,
                                onClick = { saveNotice() },
                            ) {
                                Text(text = if (noticeSaving) "保存中…" else "保存")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNoticeEditor = false }) {
                                Text(text = "取消")
                            }
                        },
                    )
                }

                // ===== 退出群聊二次确认 =====
                if (showLeaveConfirm) {
                    AlertDialog(
                        onDismissRequest = { if (!leaving) showLeaveConfirm = false },
                        title = { Text(text = "退出群聊") },
                        text = {
                            Text(
                                text = "退出「" + g.name + "」后就不再是群成员了, 需要重新加入才能发言。",
                                fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !leaving,
                                onClick = { doLeave() },
                            ) {
                                Text(
                                    text = if (leaving) "退出中…" else "退出",
                                    color = DangerRed,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLeaveConfirm = false }) {
                                Text(text = "再想想")
                            }
                        },
                    )
                }

                // ===== 群相册大图 (左右翻页 + 保存到相册) =====
                val preview = images.getOrNull(previewIndex)
                if (preview != null) {
                    Dialog(
                        onDismissRequest = { previewIndex = -1 },
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                        ) {
                            AsyncImage(
                                model = preview.image,
                                contentDescription = "群相册图片",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(vertical = 64.dp),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = (previewIndex + 1).toString() + " / " + images.size,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = if (savingImage) "保存中…" else "保存",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !savingImage) {
                                            savingImage = true
                                            scope.launch {
                                                val msg = saveImageToGallery(context, preview.image)
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                savingImage = false
                                            }
                                        }
                                        .padding(8.dp),
                                )
                                Text(
                                    text = "关闭",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { previewIndex = -1 }
                                        .padding(8.dp),
                                )
                            }
                            if (previewIndex > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(8.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                        .clickable { previewIndex -= 1 },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowLeft,
                                        contentDescription = "上一张",
                                        tint = Color.White,
                                    )
                                }
                            }
                            if (previewIndex < images.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(8.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                        .clickable { previewIndex += 1 },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowRight,
                                        contentDescription = "下一张",
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 群头像 + 群名 + 群号 + 成员数 */
@Composable
private fun GroupHeaderCard(group: SocialGroup) {
    Card(
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupAvatar(url = group.icon, name = group.name, size = 56)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "群号: " + group.id,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (group.memberCount > 0)
                        group.memberCount.toString() + " 名成员"
                    else "成员数未知",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                if (!group.isMember) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "你还不是这个群的成员",
                        fontSize = 12.sp,
                        color = DangerRed,
                    )
                }
            }
        }
    }
}

/** 群公告卡片 (整段可展开; 能编辑的人右上角有「发布 / 编辑」) */
@Composable
private fun NoticeCard(
    notice: String,
    expanded: Boolean,
    canEdit: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "群公告",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                if (canEdit) {
                    Text(
                        text = if (notice.isBlank()) "发布" else "编辑",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEdit() }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (notice.isBlank()) {
                Text(
                    text = "暂无群公告",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            } else {
                Text(
                    text = notice,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (expanded) "收起" else "展开全文",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
    }
}

/** 群相册: 3 列网格, 点开大图, 还能继续加载更多 */
@Composable
private fun AlbumCard(
    images: List<ApiClient.GroupImage>,
    total: Int,
    loading: Boolean,
    hasMore: Boolean,
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    Card(
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "群相册",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (total > 0) total.toString() + " 张" else "",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (images.isEmpty()) {
                Text(
                    text = if (loading) "加载中…" else "群里还没有人发过图片",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            } else {
                images.chunked(3).forEachIndexed { rowIndex, rowImages ->
                    if (rowIndex > 0) Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowImages.forEachIndexed { colIndex, img ->
                            val index = rowIndex * 3 + colIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onOpen(index) },
                            ) {
                                AsyncImage(
                                    model = img.image,
                                    contentDescription = "群相册图片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        // 最后一行不满 3 张时补空位, 保证每格宽度一致
                        repeat(3 - rowImages.size) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
                if (hasMore) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !loading) { onLoadMore() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (loading) "加载中…" else "加载更多",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 群成员: 前 10 个横滑 + 查看全部 */
@Composable
private fun MembersCard(
    members: List<SocialGroupMember>,
    total: Int,
    onOpenAll: () -> Unit,
    onOpenUser: (Int) -> Unit,
) {
    Card(
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "群成员",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (total > 0) total.toString() + " 人" else "",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (members.isEmpty()) {
                Text(
                    text = "暂时看不到成员",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(members.take(10), key = { it.id }) { m ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenUser(m.id) },
                        ) {
                            GroupAvatar(url = m.avatar, name = m.nickname, size = 44)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = m.nickname.ifBlank { m.username },
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (m.role == "owner" || m.role == "admin") {
                                Text(
                                    text = if (m.role == "owner") "群主" else "管理员",
                                    fontSize = 10.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenAll() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "查看全部 " + (if (total > 0) total else members.size).toString() + " 人",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "›",
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        }
    }
}

/** 设置区: 消息免打扰 / 全员禁言(仅管理员) / 退出群聊(非群主) */
@Composable
private fun SettingsCard(
    muted: Boolean,
    allMuted: Boolean,
    isAdmin: Boolean,
    isMember: Boolean,
    isOwner: Boolean,
    muteSaving: Boolean,
    allMuteSaving: Boolean,
    onToggleMute: (Boolean) -> Unit,
    onToggleAllMute: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    Card(
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            SettingRow(
                title = "消息免打扰",
                value = if (muted) "已开启" else "已关闭",
                enabled = !muteSaving,
                onClick = { onToggleMute(!muted) },
            )
            if (isAdmin) {
                SettingRow(
                    title = "全员禁言",
                    value = if (allMuted) "已开启" else "已关闭",
                    enabled = !allMuteSaving,
                    danger = allMuted,
                    onClick = { onToggleAllMute(!allMuted) },
                )
            }
            if (isMember && !isOwner) {
                SettingRow(
                    title = "退出群聊",
                    value = "",
                    danger = true,
                    onClick = onLeave,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = if (danger) DangerRed else MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(
                text = value,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/** 圆形群头像 (地址为空时显示群名首字) */
@Composable
private fun GroupAvatar(url: String, name: String, size: Int) {
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

/**
 * 保存图片到系统相册 (targetSdk 34 / minSdk 33 → MediaStore + RELATIVE_PATH, 不需要存储权限)。
 * 和聊天页的保存逻辑同一套: 下载走 ApiClient.downloadChatImage, 写盘在 IO 线程。
 * @return 给用户看的 Toast 文案 (成功 = 「已保存到相册」)
 */
private suspend fun saveImageToGallery(context: Context, url: String): String {
    if (url.isBlank()) return "图片地址为空"
    val bytes = ApiClient.downloadChatImage(url).getOrElse { e ->
        return e.message?.takeIf { it.isNotBlank() } ?: "图片下载失败"
    }
    val mime = when {
        url.endsWith(".png", true) -> "image/png"
        url.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }
    val ext = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "fl_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext,
                )
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                // 相册里的「风铃分享库」相簿
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/风铃分享库",
                )
            }
            val cr = context.contentResolver
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("相册写入失败")
            cr.openOutputStream(uri)?.use { out -> out.write(bytes) } ?: error("相册写入失败")
        }.fold(
            onSuccess = { "已保存到相册" },
            onFailure = { e -> "保存失败: " + (e.message ?: "未知错误") },
        )
    }
}
