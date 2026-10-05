package com.fengling.share.ui.lottery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.fengling.share.ui.components.GlassRadius
import com.fengling.share.ui.components.GlassSpacing
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.ui.components.glassCard
import com.fengling.share.ui.components.glassStroke
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
import com.fengling.share.data.LotteryWindow
import com.fengling.share.data.userFriendlyMessage
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import com.fengling.share.ui.components.LoadingBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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

    /**
     * 服务器时间对齐: 本地时钟 - 服务器时钟 (拿到数据时校准一次),
     * 之后倒计时全部用本地时钟逐秒递减, 不再打接口。
     */
    var serverOffsetMs by remember { mutableStateOf(0L) }
    /** 校准那一刻的本地时间 (倒计时基准) */
    var baseAtMs by remember { mutableStateOf(System.currentTimeMillis()) }
    /** 每秒刷新的本地时间 (驱动状态卡 / 冷却倒计时) */
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    /** 距校准时刻过了几秒 (把服务端下发的剩余秒数换算成「现在还剩多少」) */
    fun elapsedSec(): Int {
        val d = (nowMs - baseAtMs) / 1000L
        return when {
            d < 0L -> 0
            d > Int.MAX_VALUE -> Int.MAX_VALUE
            else -> d.toInt()
        }
    }

    /** 当前服务器时间 (本地推算, 用于判断「今天 / 明天」) */
    fun serverNowMs(): Long = nowMs - serverOffsetMs

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
                .onSuccess { fresh ->
                    info = fresh
                    error = ""
                    // 用服务端 server_time (数据库 NOW()) 校准一次本地偏移, 之后每秒本地递减
                    val serverAt = parseServerTime(
                        fresh.serverTime.ifBlank { fresh.window?.serverTime ?: "" },
                    )
                    val localAt = System.currentTimeMillis()
                    serverOffsetMs = if (serverAt != null) localAt - serverAt else 0L
                    baseAtMs = localAt
                    nowMs = localAt
                }
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

    /** 只有需要倒计时(状态卡 / 冷却)时才每秒刷新一次, 其余情况不空转 */
    val tickNeeded = info?.let {
        val w = it.window
        // 未开放(要数到下次开放) / 开放中但有明确关闭时间(要数到关闭) / 有冷却 才需要每秒刷新
        (w != null && (!w.open || w.secondsToClose > 0)) || it.cooldownLeft > 0
    } == true
    LaunchedEffect(tickNeeded) {
        var lastAutoReloadAt = 0L
        while (tickNeeded) {
            nowMs = System.currentTimeMillis()
            // 倒计时归零后自动拉一次最新状态 (30 秒内最多一次, 防止空转打服务端)
            val w = info?.window
            val closedNow = w != null && w.open && w.secondsToClose > 0 &&
                w.secondsToClose - elapsedSec() <= 0
            val openNow = w != null && !w.open && w.nextOpenAt.isNotBlank() &&
                w.secondsToOpen - elapsedSec() <= 0
            if ((closedNow || openNow) && nowMs - lastAutoReloadAt > 30_000L) {
                lastAutoReloadAt = nowMs
                loadInfo()
            }
            delay(1000L)
        }
    }

    /** 抽一次奖: 本地先拦一遍开关与次数, 服务端再做最终判断 */
    fun doDraw() {
        if (drawing) return
        val cur = info ?: return
        // ===== 契约 D: 开放时段 / 冷却 本地预拦 (服务端仍会再判一次) =====
        val w = cur.window
        val cdNow = (cur.cooldownLeft - elapsedSec()).coerceAtLeast(0)
        when {
            !cur.enabled -> {
                onToast("抽奖活动已关闭")
                return
            }
            w != null && !w.open -> {
                // 服务端中文原文优先; reason_text 缺失时按 reason 兜底
                onToast(w.reasonText.ifBlank { windowTitle(w, elapsedSec()) })
                return
            }
            w != null && !w.myAllowed -> {
                onToast("现在还抽不了, 请稍后再来")
                return
            }
            cdNow > 0 -> {
                onToast("抽得太快啦, 请 " + cdNow + " 秒后再试")
                return
            }
            cur.myQuota <= 0 -> {
                onToast("抽奖次数已用完")
                return
            }
            // 每周次数限制 (week_limit = 0 表示不限)
            cur.weekLimit > 0 && cur.myWeekLeft <= 0 -> {
                onToast("本周的抽奖次数已用完, 下周再来")
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

    // 契约 B: 预测性返回 (跟手) —— 手势进度 0→1 跟手右移 + 缩小淡出, 松手 <50% 回弹, >=50% 提交返回。
    // 未开「预测性返回手势动画」或低版本系统上系统不回传进度, 回调立刻正常结束 -> 直接 onBack(),
    // 功能与原来完全一致, 不会崩。
    // 抽奖结果弹窗开着时: 返回键先关弹窗; 其余情况交给框架的预测性返回退页
    BackHandler(enabled = result != null) { result = null }

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
                        .padding(horizontal = GlassSpacing.page)
                        // 契约 C: 页面级柔和渐变底 (静态, 零模糊)
                        .appGradientBackground(),
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
                    // 每周次数限制 (week_limit = 0 表示不限, 这时不显示这一行)
                    if (cur.weekLimit > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "本周已抽 " + cur.myWeekDrawn + " 次 · 本周还剩 " +
                                cur.myWeekLeft + " 次 (每周 " + cur.weekLimit + " 次)",
                            fontSize = 11.sp,
                            color = if (cur.myWeekLeft > 0) {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            } else {
                                Color(0xFFE5484D)
                            },
                        )
                    }
                    // 每日重置时刻 (服务端下发的配置, 非 00:00 才提一句)
                    if (cur.dailyResetTime.isNotBlank() && cur.dailyResetTime != "00:00") {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "次数每天 " + cur.dailyResetTime + " 重置",
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }

                    // ===== 顶部状态卡 (契约 D: 服务端没返回 window 时整块隐藏) =====
                    val winState = cur.window
                    // 活动总开关关了的话, 下面已经有「抽奖活动已关闭」提示块, 状态卡不再重复一句
                    if (winState != null && cur.enabled) {
                        val winOk = winState.open && winState.myAllowed
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (winOk) {
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MiuixTheme.colorScheme.error.copy(alpha = 0.10f)
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = windowTitle(winState, elapsedSec()),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (winOk) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.error
                                },
                            )
                            val sub2 = windowSubtitle(
                                winState,
                                elapsedSec(),
                                serverNowMs(),
                                cur.startDate,
                                cur.endDate,
                            )
                            if (sub2.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = sub2,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                            if (winOk && winState.text.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "开放时段: " + winState.text,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                    }

                    // ===== 活动说明 (可长按选中复制) =====
                    if (cur.content.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(radius = GlassRadius.card),
                            cornerRadius = GlassRadius.card,
                            colors = CardDefaults.defaultColors(
                                color = Color.Transparent,
                                contentColor = MiuixTheme.colorScheme.onBackground,
                            ),
                        ) {
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
                    val weekOut = cur.weekLimit > 0 && cur.myWeekLeft <= 0
                    // 冷却剩余秒数 (本地每秒递减, 服务端仍会再判一次)
                    val cdLeft = (cur.cooldownLeft - elapsedSec()).coerceAtLeast(0)
                    val ws = winState
                    // 服务端说现在不在开放时段 -> 按状态卡同一句话禁用按钮
                    val winClosedText = if (ws != null && !ws.open) windowTitle(ws, elapsedSec()) else null
                    val winNoPerm = ws != null && !ws.myAllowed
                    val winBlocked = winClosedText != null || winNoPerm
                    val canDraw = cur.enabled && !winBlocked && cur.myQuota > 0 && !dailyOut &&
                        !weekOut && cdLeft <= 0 && !drawing
                    // 契约 C: 抽奖大按钮 —— 按压缩放回弹; 条件不满足时补一道玻璃描边
                    val drawInteraction = remember { MutableInteractionSource() }
                    val drawPressed by drawInteraction.collectIsPressedAsState()
                    val drawScale by animateFloatAsState(
                        targetValue = if (drawPressed && canDraw) 0.97f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
                        label = "lotteryDrawPress",
                    )
                    val drawShape = RoundedCornerShape(GlassRadius.icon)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .graphicsLayer {
                                scaleX = drawScale
                                scaleY = drawScale
                            }
                            .clip(drawShape)
                            .background(
                                if (canDraw) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainerHigh
                                },
                            )
                            .then(if (canDraw) Modifier else Modifier.border(1.dp, glassStroke(), drawShape))
                            .clickable(
                                interactionSource = drawInteraction,
                                indication = null,
                                enabled = canDraw,
                            ) { doDraw() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                drawing -> "抽奖中…"
                                !cur.enabled -> "抽奖活动已关闭"
                                winClosedText != null -> winClosedText
                                winNoPerm -> "暂时不能参与抽奖"
                                cdLeft > 0 -> cdLeft.toString() + " 秒后可再抽"
                                weekOut -> "本周的抽奖次数已用完"
                                dailyOut -> "今天的抽奖次数已用完"
                                cur.myQuota <= 0 -> "抽奖次数已用完"
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
                    val btnHint = when {
                        !cur.enabled -> ""
                        winClosedText != null -> ""
                        winNoPerm -> "暂时不能参与, 请稍后再来"
                        cdLeft > 0 -> "冷却中, " + cdLeft + " 秒后可再抽一次"
                        weekOut -> "本周的抽奖次数已用完, 下周再来"
                        dailyOut -> "今天的抽奖次数已用完, 明天再来"
                        cur.myQuota <= 0 -> "抽奖次数已用完, 关注后续活动吧"
                        else -> ""
                    }
                    if (btnHint.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            // 服务端原文文案, 客户端只做同义复述
                            text = btnHint,
                            fontSize = 11.sp,
                            color = if (cdLeft > 0) {
                                MiuixTheme.colorScheme.onBackgroundVariant
                            } else {
                                Color(0xFFE5484D)
                            },
                        )
                    }

                    // ===== 奖品列表 (show_prizes = 0 时整块隐藏, 此时服务端 prizes 返回空数组) =====
                    if (cur.showPrizes) {
                        Spacer(Modifier.height(16.dp))
                        SmallTitle(text = "奖品 (" + cur.prizes.size + ")")
                    }
                    if (!cur.showPrizes) {
                        // 后台关了奖项展示: 不显示列表, 也不显示「暂无可抽奖品」
                    } else if (cur.prizes.isEmpty()) {
                        Text(
                            text = cur.emptyText.ifBlank { "暂无可抽奖品, 请稍后再来" },
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        cur.prizes.forEach { p ->
                            PrizeRow(p, showStock = cur.showStock)
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
                    // success_text 非空时用它替换默认标题
                    text = info?.successText?.takeIf { it.isNotBlank() } ?: "🎉 恭喜抽中",
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
private fun PrizeRow(p: LotteryPrize, showStock: Boolean = true) {    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(radius = GlassRadius.card),
        cornerRadius = GlassRadius.card,
        // 契约 C: 玻璃卡片 —— 卡片底色透明, 由 glassCard 画半透明填充 + 细描边 (零模糊)
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackground,
        ),
    ) {
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
            // show_stock = 0 时不显示剩余数量
            if (showStock) {
                Text(
                    text = "剩 " + p.left,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}

/** 中奖记录行: 奖项名 + 类型 + 卡密 + 复制按钮 + 时间 */
@Composable
private fun RecordRow(r: LotteryRecord, onCopy: (String) -> Unit) {    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(radius = GlassRadius.card),
        cornerRadius = GlassRadius.card,
        // 契约 C: 玻璃卡片 —— 卡片底色透明, 由 glassCard 画半透明填充 + 细描边 (零模糊)
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackground,
        ),
    ) {
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

// ===================== 契约 D 节: 开放时段 / 倒计时 辅助函数 =====================

/** 秒数 -> 倒计时文案 (不足 1 小时用 mm:ss, 超过用 HH:mm:ss) */
private fun fmtCountdown(sec: Int): String {
    val s = if (sec < 0) 0 else sec
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) two(h) + ":" + two(m) + ":" + two(ss) else two(m) + ":" + two(ss)
}

/** 两位补零 (不用 String.format, 免得踩 Locale 的坑) */
private fun two(n: Int): String = if (n < 10) "0" + n else n.toString()

/** 解析服务端 "YYYY-MM-DD HH:mm:ss" (数据库 NOW() 文本); 解析不了返回 null */
private fun parseServerTime(raw: String): Long? {
    val s = raw.trim()
    if (s.isBlank()) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(s)?.time
    } catch (e: Exception) {
        null
    }
}

/**
 * 状态卡主文案 / 按钮文案 (按服务端 window.reason 分支)
 * elapsed = 距拿到这份数据的秒数 (本地时钟已按 server_time 校准)
 */
private fun windowTitle(w: LotteryWindow, elapsed: Int): String {
    if (w.open) {
        // seconds_to_close = 0 表示这个时段没有关闭时间 (后台没启用自定义时段), 这时不显示倒计时
        if (w.secondsToClose <= 0) return "进行中"
        val left = w.secondsToClose - elapsed
        return if (left > 0) "进行中, 还剩 " + fmtCountdown(left) else "进行中, 即将结束"
    }
    return when (w.reason) {
        "disabled" -> "抽奖活动已关闭"
        "before_start" -> "抽奖活动还没开始"
        "after_end" -> "抽奖活动已经结束"
        // 服务端原文就是「现在不在抽奖时间内, 下次开放: ...」, 这里只取前半句,
        // 后半句的日期时间交给副标题做「今天 19:30 · 00:42 后开放」的友好渲染
        "outside_window" -> "现在不在抽奖时间内"
        else -> w.reasonText.substringBefore(", 下次开放").ifBlank { "未在抽奖时间" }
    }
}

/**
 * 状态卡副标题: 未开放时显示「下次开放: 今天 19:30 · 00:42 后开放」;
 * 活动未开始 / 已结束显示日期; 服务端没给 next_open_at 时退化成 reason_text。
 */
private fun windowSubtitle(
    w: LotteryWindow,
    elapsed: Int,
    serverNowMs: Long,
    startDate: String,
    endDate: String,
): String {
    if (w.open) return w.text
    return when (w.reason) {
        "disabled" -> ""
        "before_start" -> if (startDate.isNotBlank()) "活动开始: " + startDate else ""
        "after_end" -> if (endDate.isNotBlank()) "活动结束: " + endDate else ""
        else -> {
            val parts = mutableListOf<String>()
            val next = friendlyTime(w.nextOpenAt, serverNowMs)
            val cd = (w.secondsToOpen - elapsed).coerceAtLeast(0)
            if (next.isNotBlank()) {
                parts.add("下次开放: " + next)
                if (cd > 0) parts.add(fmtCountdown(cd) + " 后开放")
            }
            parts.joinToString(" · ").ifBlank { w.reasonText }
        }
    }
}

/** 把服务端 "YYYY-MM-DD HH:mm:ss" 变成「今天 19:30 / 明天 19:30 / 10月6日 19:30」 */
private fun friendlyTime(raw: String, serverNowMs: Long): String {
    val s = raw.trim()
    if (s.isBlank()) return ""
    val at = parseServerTime(s) ?: return s
    val hm = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(at))
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val target = dayFmt.format(Date(at))
    val today = dayFmt.format(Date(serverNowMs))
    val tomorrow = dayFmt.format(Date(serverNowMs + 24L * 60L * 60L * 1000L))
    return when (target) {
        today -> "今天 " + hm
        tomorrow -> "明天 " + hm
        else -> SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(at))
    }
}
