package com.fengling.share.ui.main.my

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.Contributor
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.EmptyMessage
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.LoadingBox
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * ContributorsScreen - 投稿名单页
 * 完整列表: 每位投稿人头像 + 投稿了哪些应用 (QQ 号不对外显示)
 */
@Composable
fun ContributorsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            contributors = ApiClient.getContributors()
        } catch (_: Exception) { }
        loading = false
    }

    // 契约 B: 预测性返回(跟手) —— 跟手右移+缩小淡出, 松手过半分提交返回, 否则回弹;
    // 未开「预测性返回手势动画」的系统上系统不回传进度, 回调立刻正常结束 -> 直接 onBack(), 功能不变。
    Scaffold(
        modifier = Modifier,
        topBar = {
            AppTopBar(
                title = "投稿名单",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        when {
            loading -> {
                LoadingBox(Modifier.fillMaxSize().padding(innerPadding))
            }
            contributors.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    EmptyMessage(text = "暂无投稿, 快来分享好软件吧")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = GlassSpacing.page)
                        .appGradientBackground(),
                ) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        SmallTitle(text = "感谢每一位投稿人 · ${contributors.size} 位")
                    }
                    items(contributors) { c ->
                        ContributorRow(c)
                        Spacer(Modifier.height(6.dp))
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** 投稿人卡片: 头像 + 投稿应用列表 */
@Composable
private fun ContributorRow(c: Contributor) {
    Card(
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackground,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .glassCard(radius = GlassRadius.card),
        cornerRadius = GlassRadius.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像 (圆形, QQ 官方头像)
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
                Text(
                    text = if (c.name.isNotBlank()) c.name else if (c.appNames.isNotEmpty()) "热心投稿人" else "感谢 ta 的投稿",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                // 投稿说明
                if (c.bio.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = c.bio,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 2,
                    )
                }
                // 投稿应用
                if (c.appNames.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "投稿: ${c.appNames.joinToString("、")}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
