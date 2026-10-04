package com.fengling.share.ui.lottery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text as M3Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.ApiClient
import com.fengling.share.data.LotteryInfo
import com.fengling.share.data.LotteryPrize
import com.fengling.share.data.LotteryRecord
import com.fengling.share.data.LotteryResult
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.LoadingBox
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * LotteryScreen - 抽奖页 (群聊输入框「+」菜单里的「抽奖 🎁」进入)
 *
 * 数据全部来自 ApiClient.lotteryInfo / lotteryDraw / lotteryRecords (都要带 token)。
 * 所有失败提示都取服务端 msg (如「你的抽奖次数已用完」), 经 userFriendlyMessage 过滤,
 * 不会把原始网络异常(含服务器地址)显示出来。
 */
@Composable
fun LotteryScreen(
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var info by remember { mutableStateOf<LotteryInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var drawing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    /** 刚抽中的结果 (非空时弹「🎉 恭喜抽中」弹窗) */
    var result by remember { mutableStateOf<LotteryResult?>(null) }

    /** 我的中奖记录 (单独走 lottery_records, 支持翻页) */
    var records by remember { mutableStateOf<List<LotteryRecord>>(emptyList()) }
    var recordsTotal by remember { mutableStateOf(0) }
    var recordsPage by remember { mutableStateOf(1) }
    var recordsLoading by remember { mutableStateOf(false) }

    /** 复制卡密到系统剪贴板 (和群消息「复制」同一套写法) */
    fun copyCode(code: String) {
        if (code.isBlank()) {
            onToast("没有可复制的卡密")
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("卡密", code))
            onToast("卡密已复制")
        } else {
            onToast("复制失败")
        }
    }

    /** 拉抽奖信息 (刷新剩余次数 / 奖品 / 开关) */
    fun loadInfo() {
        scope.launch {
            loading = true
            ApiClient.lotteryInfo()
                .onSuccess { info = it; error = "" }
                .onFailure { e -> error = e.userFriendlyMessage() }
            loading = false
        }
    }

    /** 拉我的中奖记录; append=false 时从第 1 页重来 */
    fun loadRecords(targetPage: Int = 1, append: Boolean = false) {
        scope.launch {
            recordsLoading = true
            ApiClient.lotteryRecords(page = targetPage)
                .onSuccess { pageData ->
                    records = if (append) {
                        (records + pageData.list).distinctBy { it.id }
                    } else {
                        pageData.list
                    }
                    recordsTotal = pageData.total
                    recordsPage = targetPage
                }
                .onFailure { /* 记录拉不到不影响抽奖, 静默 */ }
            recordsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadInfo()
        loadRecords(1, false)
    }

    /** 抽一次奖: 本地先拦一遍开关与次数, 服务端再做最终判断 */
    fun doDraw() {
        if (drawing) return
        val cur = info ?: return
        when {
            !cur.enabled -> {
                onToast("抽奖活动已关闭")
                return
            }
            cur.myQuota <= 0 -> {
                onToast("抽奖次数已用完")
                return
            }
            // 每日次数限制 (daily_limit = 0 表示不限): 用完直接用服务端原文提示
            cur.dailyLimit > 0 && cur.myTodayLeft <= 0 -> {
                onToast("今天的抽奖次数已用完, 明天再来")
                return
            }
        }
        drawing = true
        scope.launch {
            ApiClient.lotteryDraw()
                .onSuccess { r -> result = r }
                .onFailure { e -> onToast(e.userFriendlyMessage()) }
            drawing = false
        }
    }

    /** 关闭结果弹窗: 顺手刷新剩余次数与记录列表 */
    fun closeResult() {
        result = null
        loadInfo()
        loadRecords(1, false)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = info?.title?.takeIf { it.isNotBlank() } ?: "抽奖",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        when {
            loading && info == null -> LoadingBox(Modifier.fillMaxSize().padding(innerPadding))
            info == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error.ifBlank { "抽奖信息加载失败" },
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Card(onClick = { loadInfo() }, cornerRadius = 12.dp) {
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
            else -> {
                val cur = info!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    Spacer(Modifier.height(6.dp))

                    // ===== 标题 + 剩余次数 =====
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cur.title.ifBlank { "抽卡密活动" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (cur.myQuota > 0) {
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHigh
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "我还剩 " + cur.myQuota + " 次",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (cur.myQuota > 0) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onBackgroundVariant
                                },
                            )
                        }
                    }
                    if (cur.myDrawn > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "已抽 " + cur.myDrawn + " 次",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    // 每日次数限制 (daily_limit = 0 表示不限, 这时不显示这一行)
                    if (cur.dailyLimit > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "今日已抽 " + cur.myTodayDrawn + " 次 · 今日还剩 " +
                                cur.myTodayLeft + " 次 (每天 " + cur.dailyLimit + " 次)",
                            fontSize = 11.sp,
                            color = if (cur.myTodayLeft > 0) {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            } else {
                                Color(0xFFE5484D)
                            },
                        )
                    }

                    // ===== 活动说明 (可长按选中复制) =====
                    if (cur.content.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                            SelectionContainer {
                                Text(
                                    text = cur.content,
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }

                    // ===== 活动关闭提示 =====
                    if (!cur.enabled) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = "抽奖活动已关闭",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    }

                    // ===== 立即抽奖大按钮 =====
                    Spacer(Modifier.height(14.dp))
                    val dailyOut = cur.dailyLimit > 0 && cur.myTodayLeft <= 0
                    val canDraw = cur.enabled && cur.myQuota > 0 && !dailyOut && !drawing
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (canDraw) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .clickable(enabled = canDraw) { doDraw() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                drawing -> "抽奖中…"
                                !cur.enabled -> "抽奖活动已关闭"
                                cur.myQuota <= 0 -> "抽奖次数已用完"
                                dailyOut -> "今天的抽奖次数已用完"
                                else -> "🎁 立即抽奖"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canDraw) {
                                MiuixTheme.colorScheme.onPrimary
                            } else {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            },
                        )
                    }
                    if (cur.enabled && cur.myQuota <= 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "抽奖次数已用完, 关注后续活动吧",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    } else if (cur.enabled && dailyOut) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            // 服务端原文: 今天的抽奖次数已用完, 明天再来
                            text = "今天的抽奖次数已用完, 明天再来",
                            fontSize = 11.sp,
                            color = Color(0xFFE5484D),
                        )
                    }

                    // ===== 奖品列表 =====
                    Spacer(Modifier.height(16.dp))
                    SmallTitle(text = "奖品 (" + cur.prizes.size + ")")
                    if (cur.prizes.isEmpty()) {
                        Text(
                            text = "暂无可抽奖品, 请稍后再来",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        cur.prizes.forEach { p ->
                            PrizeRow(p)
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // ===== 我的中奖记录 =====
                    Spacer(Modifier.height(8.dp))
                    SmallTitle(text = "我的中奖记录 (" + recordsTotal + ")")
                    if (records.isEmpty()) {
                        Text(
                            text = "还没有中奖记录, 试试运气吧",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        records.forEach { r ->
                            RecordRow(r, onCopy = { copyCode(it) })
                            Spacer(Modifier.height(6.dp))
                        }
                        if (records.size < recordsTotal) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Card(
                                    onClick = {
                                        if (!recordsLoading) loadRecords(recordsPage + 1, true)
                                    },
                                    cornerRadius = 12.dp,
                                ) {
                                    Text(
                                        text = if (recordsLoading) "加载中…" else "加载更多记录",
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "卡密会同时存入消息中心, 中奖后随时可以回去复制",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    // ===== 中奖结果弹窗 (醒目的卡密大字 + 一键复制) =====
    val win = result
    if (win != null) {
        AlertDialog(
            onDismissRequest = { closeResult() },
            title = {
                M3Text(
                    text = "🎉 恭喜抽中",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = win.prizeName.ifBlank { "奖品" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        if (win.cardType.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            CardTypeTag(win.cardType)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 卡密: 大号等宽 + 可长按选中 (长按选中和「复制卡密」按钮两条路都留着)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = win.code,
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Card(
                        onClick = { copyCode(win.code) },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "复制卡密",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "卡密已存入消息中心，可随时查看",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "抽完后我还剩 " + win.left + " 次",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            },
            confirmButton = {
                M3TextButton(onClick = { closeResult() }) {
                    M3Text(text = "知道了", color = MiuixTheme.colorScheme.primary)
                }
            },
        )
    }
}

/** 奖品行: 名称 + 类型标签 + 剩余数量 */
@Composable
private fun PrizeRow(p: LotteryPrize) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = p.name.ifBlank { "奖品" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (p.cardType.isNotBlank()) {
                CardTypeTag(p.cardType)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "剩 " + p.left,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/** 中奖记录行: 奖项名 + 类型 + 卡密 + 复制按钮 + 时间 */
@Composable
private fun RecordRow(r: LotteryRecord, onCopy: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = r.prizeName.ifBlank { "奖品" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (r.cardType.isNotBlank()) {
                    CardTypeTag(r.cardType)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // SelectionContainer 里拿不到 RowScope, 所以 weight 挂在外面这层 Box 上
                Box(Modifier.weight(1f)) {
                    SelectionContainer {
                        Text(
                            text = r.code,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "复制",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable { onCopy(r.code) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (r.createdAt.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = r.createdAt,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}

/** 卡密类型标签: 天卡 / 周卡 / 月卡 各一种颜色, 其它文本用主题色 */
@Composable
private fun CardTypeTag(type: String) {
    val color = when (type) {
        "天卡" -> Color(0xFF2F9E5F)
        "周卡" -> Color(0xFF3B82F6)
        "月卡" -> Color(0xFFE58A00)
        else -> MiuixTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = type,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}
