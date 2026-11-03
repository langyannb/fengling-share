package com.fengling.share.ui.main

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fengling.share.data.ApiClient
import com.fengling.share.data.MessageStream
import com.fengling.share.data.PendingNav
import com.fengling.share.service.MessageService
import com.fengling.share.ui.components.appGradientBackground
import com.fengling.share.data.NoticeInfo
import com.fengling.share.data.StreamEvent
import com.fengling.share.data.Settings
import com.fengling.share.data.UserStore
import com.fengling.share.ui.theme.isAppInDarkTheme
import com.fengling.share.utils.NotifySound
import com.fengling.share.data.VersionInfo
import com.fengling.share.ui.book.detail.DetailScreen
import com.fengling.share.ui.browser.WebViewScreen
import com.fengling.share.ui.components.AppScaffold
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.fengling.share.ui.components.ExternalJumpDialog
import com.fengling.share.ui.components.ExternalJumpTarget
import com.fengling.share.ui.components.resolveExternalJump
import com.fengling.share.ui.components.navigation.MainBottomBar
import com.fengling.share.ui.components.rememberGlassBackdrop
import com.fengling.share.ui.components.rememberGlassBackdrop2
import com.fengling.share.ui.components.rememberBlurBackdrop
import com.fengling.share.ui.theme.LocalEnableBlur
import com.fengling.share.ui.theme.LocalFloatingBottomBar
import com.fengling.share.ui.theme.LocalGlassBottomBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import com.fengling.share.ui.main.explore.ExploreScreen
import com.fengling.share.ui.main.home.HomeScreen
import com.fengling.share.ui.main.my.AccountScreen
import com.fengling.share.ui.main.my.ContributorsScreen
import com.fengling.share.ui.main.my.MessagesScreen
import com.fengling.share.ui.main.my.NotifySettingsScreen
import com.fengling.share.ui.main.my.ThemeSettingsScreen
import com.fengling.share.ui.pm.PmChatScreen
import com.fengling.share.ui.user.UserProfileScreen
import com.fengling.share.ui.main.my.MyScreen
import com.fengling.share.ui.social.GroupInfoScreen
import com.fengling.share.ui.social.GroupMemberListScreen
import com.fengling.share.ui.social.SocialScreen
import com.fengling.share.ui.update.UpdateScreen
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 底部导航 tab */
private data class NavTab(val label: String, val icon: ImageVector)

/** 「群组」在底部 tab 里的下标 (未登录时会被拦到登录页) */
private const val SOCIAL_TAB_PAGE = 2

/** 导航路由 */
object Routes {
    const val MAIN = "main"
    const val DETAIL = "detail/{appId}"
    const val WEBVIEW = "webview?url={url}&title={title}&password={password}"
    const val UPDATE = "update?version={version}&url={url}&log={log}&mode={mode}&size={size}&date={date}"
    const val CONTRIBUTORS = "contributors"
    const val ACCOUNT = "account"
    // 社交群组: 独立底部 tab「群组」进列表, 点群组进入全屏聊天
    const val SOCIAL_GROUP = "social/group/{groupId}?messageId={messageId}&notice={notice}"
    const val MESSAGES = "messages"
    // 消息通知设置 (后台接收开关 + 通知/电池优化/自启动 权限引导)
    const val NOTIFY_SETTINGS = "settings/notify"
    // 主题设置 (明暗 / 主题色 / 色板风格 / 模糊 / 悬浮底栏 / 预测性返回)
    const val THEME_SETTINGS = "settings/theme"
    // 私聊会话页 (会话列表 / 用户主页「发消息」/ 私聊通知 进入)
    const val PM_CHAT = "pm/chat?convId={convId}&userId={userId}&messageId={messageId}"
    // 用户主页 (群聊 / 私聊里点任何人的头像进入, 含自己)
    // groupId: 从群聊进来带群 id (主页上的禁言只对本群生效), 其它入口 = 0 全站视角
    const val USER_PROFILE = "user/profile/{userId}?groupId={groupId}"
    // 群详情页 (群聊页右上角「☰」进入: 群公告 / 群相册 / 群成员 / 消息免打扰 / 退出群聊)
    const val GROUP_INFO = "social/group/{groupId}/info"
    // 群成员列表页 (群详情页「查看全部 N 人」进入: 搜索 + 分页)
    const val GROUP_MEMBERS = "social/group/{groupId}/members?name={name}"

    fun detail(appId: Int) = "detail/$appId"
    fun socialGroup(groupId: Int, messageId: Int = 0, notice: Boolean = false) =
        "social/group/$groupId?messageId=$messageId&notice=" + if (notice) 1 else 0
    fun pmChat(convId: Int = 0, userId: Int = 0, messageId: Int = 0) =
        "pm/chat?convId=$convId&userId=$userId&messageId=$messageId"
    fun userProfile(userId: Int, groupId: Int = 0) = "user/profile/$userId?groupId=$groupId"
    fun groupInfo(groupId: Int) = "social/group/$groupId/info"
    fun groupMembers(groupId: Int, name: String = "") =
        "social/group/$groupId/members?name=${android.net.Uri.encode(name)}"
    fun webview(url: String, title: String, password: String = "") =
        "webview?url=${android.net.Uri.encode(url)}&title=${android.net.Uri.encode(title)}&password=${android.net.Uri.encode(password)}"
    fun update(info: com.fengling.share.data.VersionInfo) =
        "update?version=${android.net.Uri.encode(info.version)}&url=${android.net.Uri.encode(info.url)}" +
            "&log=${android.net.Uri.encode(info.updateLog)}&mode=${android.net.Uri.encode(info.updateMode)}" +
            "&size=${info.sizeMb}&date=${android.net.Uri.encode(info.releaseDate)}"
}

/**
 * MainScreen - 主界面 (OShin 风格重构)
 * - HorizontalPager: tab 左右滑动切换 (平滑滑动动画)
 * - 液态玻璃: 页面级 backdrop 捕获 + 玻璃底栏 (OShin 同款 kyant/backdrop)
 * - Navigation 返回栈: main → detail → webview
 * - WebViewScreen 每次进入创建全新实例 (不复用, 避免历史栈残留)
 */


/**
 * 契约 B: NavHost 每个目的地统一补齐四段转场 (进入 / 退出 / 返回进入 / 返回退出)。
 *
 * 进入 = 右侧滑入 (与页面切换方向一致);
 * 返回 = 上一级左视差跟入 + 当前页整屏右移 —— 手势进行中系统会把进度 seek 进这段转场
 * (navigation-compose 的 SeekableTransitionState), 所以不能有 fadeIn/scaleIn。
 * 规格统一写在这里, 新增目的地直接 appScreen(route) 即可, 不会再出现「有的页面有动画有的没有」。
 */
// ───────────────────────── 预测性返回：跟手层 ─────────────────────────
// 参考项目（KernelSU-Style-UI-Kit）的「原生感」不是它自己画的，而是 miuix-nav 的
// NavDisplayEffects 给的：进入/退出时被遮住的那一页整体压一层黑色 scrim（dimAmount 随跟手
// 进度线性变化），同时当前页缩成带圆角的卡片。本仓库用不了 miuix-nav（只发 0.9.4，
// 依赖 compose 1.12 + kotlin 2.4.20，与本仓库 1.11.4 / 2.4.10 不同代），所以在这里等价实现。
//
// 进度来源必须是框架的 seek：navigation-compose 的 NavHost 在预测性返回进行中会把手势进度
// seek 进转场（SeekableTransitionState.seekTo），所以这里只读 AnimatedVisibilityScope.transition
// 上由 Transition.animateFloat 产出的值 —— 它会被 seek 驱动，与手指 1:1，不需要（也不允许）
// 再挂一个 PredictiveBackHandler（那就会变成用户报的「两层预测返回」）。
private const val PREDICTIVE_BACK_DURATION_MS = 400

/**
 * 退出时四周露出的「暗色阴影」最大不透明度。参考项目那层暗色来自 miuix
 * `NavDisplayEffects.dimAmount`（默认 0.5f），这里取同一个值。
 *
 * 注意它压暗的**不是当前页**：压暗层画在退出页之下，只有退出页缩小后露出的四周才会看到它，
 * 它把后一层页面压暗。当前页自己保持原亮度 —— 这才是原生观感（早期版本把压暗层画在页面
 * 之上，整页被刷灰、内容发闷，真机截图核对后推翻）。
 */
private const val PREDICTIVE_BACK_DIM = 0.5f

/**
 * 退出层最小缩放比例 —— Android 15 原生预测返回退回时当前页缩到约 0.92，四周露出下层。
 */
private const val PREDICTIVE_BACK_MIN_SCALE = 0.88f

/**
 * 退出页的右移量（占屏宽比例）。原生预测返回是「边缩边右移」，但位移必须很小：
 * 一旦接近整屏，观感就退回普通横移。0.10 屏宽 ≈ 124px，足够看出方向、又不抢缩放的戏。
 *
 * 为什么自己算而不用 popExitTransition 的 slide：转场位移作用在**整层**上，
 * 连暗色层一起被推走，左侧会漏出一条没被压暗的亮边（真机帧已证实）。
 * 位移只加在 page 上、暗色层保持铺满全屏，才对。
 */
private const val PREDICTIVE_BACK_SHIFT_FRACTION = 0.10f

/**
 * 退出层圆角，随进度 0 → 24dp 渐变（原生在 16~28dp 之间，取 24dp 与 App 卡片半径同族）。
 */
private const val PREDICTIVE_BACK_CORNER_DP = 24f

/**
 * 预测性返回的「暗色阴影」层：正在退出的那一页整体压暗，压暗程度跟手指 1:1。
 *
 * 为什么必须自己画：EnterTransition / ExitTransition 都是 sealed class（Compose 1.11.4
 * EnterExitTransition.kt:101 / :193，实现类 private），只能组合 fadeIn/fadeOut/scaleIn/
 * scaleOut、slideIn 系列、slideOut 系列，**没有任何办法在转场里叠一层颜色**。参考项目
 * KernelSU-Style-UI-Kit 的那层暗色来自 miuix-nav 的 NavDisplayEffects.dimAmount（默认 0.5f），
 * 而 miuix-nav 只在 0.9.4 发布（依赖 compose 1.12 + kotlin 2.4.20，与本仓库 1.11.4 / 2.4.10
 * 不同代，引不进来），所以在这里等价实现。
 *
 * 进度来源必须是框架的 seek：预测性返回进行中，navigation-compose 的 NavHost 会把手势进度
 * seek 进转场（SeekableTransitionState.seekTo → Transition.setPlaytimeAfterInitialAndTargetState
 * Established → 递归 _transitions + _animations.seekTo）。这里读的
 * AnimatedVisibilityScope.transition 是那条 Transition 的 child transition（
 * createChildTransitionInternal 在父 isSeeking 时把 lastSeekedTimeNanos 一并传下来），
 * 所以 animateFloat 出来的值就是手指进度 —— 1:1 跟手。
 * **不需要也不允许**再挂一个 PredictiveBackHandler：那一层会和 NavHost 自己抢
 * OnBackPressedDispatcher 的回调，位移叠两次 —— 就是用户此前报的「两层预测返回」。
 *
 * 只对退出层生效（targetState == PostExit）：进入层保持原样，否则 push 时新页一进来就是黑的。
 * 稳定态 targetState == Visible，压暗恒为 0，本层完全不参与绘制（alpha 0 的 Box 不产生视觉）。
 *
 * 性能：alpha 只在 graphicsLayer 的 block 里读（deferred read），逐帧只重绘、不触发重组。
 */
@Composable
private fun AnimatedContentScope.PredictivePageLayer(page: @Composable () -> Unit) {
    // 与 pop 转场同长同曲线（400ms + LinearEasing），框架 seek 进来的 playTime 1:1 映射到这里。
    val visible = transition.animateFloat(
        transitionSpec = { tween(PREDICTIVE_BACK_DURATION_MS, easing = LinearEasing) },
        label = "predictiveBackVisible",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val exiting = transition.targetState == EnterExitState.PostExit
    // 圆角卡片形状：随进度 0 → 24dp。稳定态不裁剪（clip=false），不影响任何正常页面。
    val cardShape = RoundedCornerShape(PREDICTIVE_BACK_CORNER_DP.dp)
    // 两个 Box 是**兄弟**不是嵌套：卡片层单独缩放，暗色层铺满全屏、不跟着缩。
    //
    // 为什么这样画就对了（关键，别再改回去）：
    // NavHost 的 transitionSpec 在 pop/预测性返回时把目标页压到 `initialZIndex - 1`
    // （navc 的 NavHost.kt:671），也就是「退出中的当前页在上，上一页在下」。本层是退出页的
    // 内层，所以：暗色层铺满全屏 → 透过它看到被压暗的上一页；卡片层画在暗色层之上 →
    // 卡片自己保持原亮度。这正是原生那圈「四周暗、卡片亮」的观感。
    // push 时方向相反（旧页在下），暗色层被它自己的 page 完全遮住 → 零副作用，
    // 所以这里不需要判断 pop / push 方向。
    Box(modifier = Modifier.fillMaxSize()) {
        // ① 暗色层：铺满全屏、不缩放，压在 page() 之下（兄弟顺序：先声明者在下面）。
        // 退出页缩小后四周露出的部分被它压暗；卡片本身在它之上，不受影响。
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = if (exiting) PREDICTIVE_BACK_DIM * (1f - visible.value) else 0f
                }
                .background(Color.Black)
        )
        // ② 退出页本体：跟手缩小 + 圆角裁剪。位移交给 popExit 的小位移 slide，两者叠加即原生卡片感。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // d: 0 = 稳定可见, 1 = 完全退出。只在退出层非零。
                    val d = if (exiting) 1f - visible.value else 0f
                    // **必须无条件赋值**：预测性返回取消时 d 会从 1 回到 0，条件赋值的写法
                    // 会把 scale 停在上一次的 0.88，页面就卡在缩小状态（graphicsLayer 的
                    // block 不会重置未赋值的属性）。
                    scaleX = 1f - (1f - PREDICTIVE_BACK_MIN_SCALE) * d
                    scaleY = scaleX
                    translationX = size.width * PREDICTIVE_BACK_SHIFT_FRACTION * d
                    shape = cardShape
                    clip = d > 0f
                }
        ) {
            page()
        }
    }
}

private fun NavGraphBuilder.appScreen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    val slideSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    composable(
        route = route,
        arguments = arguments,
        // 进入: 新页从右滑入, 旧页向左让 1/4
        enterTransition = { slideInHorizontally(slideSpec) { it } },
        exitTransition = { slideOutHorizontally(slideSpec) { -it / 4 } },
        // 返回: 交给 PredictivePageLayer 自己做原生卡片式 —— 缩小 + 圆角 + 右移 + 全屏暗色层。
        // 转场在这里必须留空: slide 作用在整层上, 会把暗色层一起推走、左侧漏亮边；
        // 上一页也留空(静止露出), 它本来就该是不动的底。
        // 跟手进度由框架 seek 进 transition, 本层 animateFloat 1:1 读到, 所以留空不影响跟手。
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) { entry ->
        PredictivePageLayer {
            content(entry)
        }
    }
}

@Composable
fun MainScreen(
    onThemeChanged: () -> Unit = {},
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 所有内部导航统一走这里。
    // v1.1.10: 自研快照层整个撤掉 —— navigation-compose 的 NavHost 自带预测性返回:
    // 手势进度会 seek 它内部的 SeekableTransitionState (pop 时上一级被放在下层 zIndex),
    // 上一级是**活的**页面, 不需要截图, 也就没有空白/卡顿。
    fun go(route: String) {
        navController.navigate(route)
    }

    // 液态玻璃 backdrop (kyant/backdrop, 内容捕获 + 底栏模糊) — OShin 同款方案
    // 只给 SocialScreen 的「群组/私聊」分段控件用。
    val (backdrop, captureModifier) = rememberGlassBackdrop2()

    // 底栏改用参考项目的 FloatingBottomBar, 它只认 miuix-blur 的 Backdrop ——
    // 两套 backdrop 类型不兼容, 所以这里平行建第二份 (同一块内容被两个层各录一次, 代价可忽略)。
    val enableBlur = LocalEnableBlur.current
    val floatingBar = LocalFloatingBottomBar.current
    val glassBar = LocalGlassBottomBar.current
    // 关掉模糊时返回 null: BlurredBar 收到 null 就不挂 textureBlur, 走不透明样式 (参考项目同款判断)
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    // 悬浮胶囊的透镜采样源: 只有「悬浮底栏 + 玻璃质感」都开才需要捕获层
    val glassCaptureNeeded = floatingBar && glassBar && enableBlur
    val (barBackdrop, barCaptureModifier) = rememberGlassBackdrop(enabled = glassCaptureNeeded)

    val tabs = remember {
        listOf(
            NavTab("首页", Icons.Filled.Home),
            NavTab("分类", Icons.Filled.Category),
            NavTab("群组", Icons.Filled.Forum),
            NavTab("关于", Icons.Filled.Info),
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // 底栏数据 (miuix 的 NavigationItem), tab 定义在 tabs 里, 这里只做类型转换
    val navItems = remember(tabs) { tabs.map { NavigationItem(label = it.label, icon = it.icon) } }

    // 契约 A(强化): 「群组 / 私聊」分段的选中值提升到这里 (MainScreen 常驻组合) 持有。
    // 原来放在 SocialScreen 内部 —— 导航返回能靠 rememberSaveable 保住, 但切底部 Tab 时
    // pager 页面会被回收重建, 页面内状态照样丢。放这里就不随任何页面销毁:
    // 1 私聊进会话返回仍停「私聊」 2 群聊返回仍停「群组」 3 切底部 Tab 来回切仍保持 4 冷启动默认「群组」
    var socialTab by rememberSaveable { mutableIntStateOf(0) }

    // 私聊新消息提示音 (不管停在哪个 tab 都能听到; 见文件末尾 PmNotifyWatcher)
    PmNotifyWatcher()

    // ---------- 点通知直达会话 (v1.0.35) ----------
    // 链路: 通知 PendingIntent 带 open_pm_conv / open_group -> MainActivity 解析写 PendingNav
    //      -> 这里收集后真正导航 -> 立刻 consume 清空 (免得重复跳)。
    // 复用消息中心那条老路径 (MessagesScreen 里 pm:<cid>:<mid> 的处理方式): 先切底部 tab, 再压栈到会话页。
    LaunchedEffect(Unit) {
        PendingNav.target.collect { target ->
            val t = target ?: return@collect
            PendingNav.consume()
            if (!UserStore.isLoggedIn()) return@collect
            when {
                t.groupId > 0 -> {
                    // 群聊在「群组」tab 里
                    pagerState.scrollToPage(SOCIAL_TAB_PAGE)
                    go(Routes.socialGroup(t.groupId))
                }
                t.pmConvId > 0 -> {
                    // 私聊会话列表也在「群组」tab 里, 先切过去再压栈, 返回时落点才自然
                    pagerState.scrollToPage(SOCIAL_TAB_PAGE)
                    go(Routes.pmChat(convId = t.pmConvId))
                }
            }
        }
    }

    // 未登录时左右滑动也不许停在「群组」页 (3 = 0 首页 / 1 分类 / 2 群组 / 3 关于)
    var lastAllowedPage by remember { mutableStateOf(0) }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            if (pagerState.currentPage == SOCIAL_TAB_PAGE && !UserStore.isLoggedIn()) {
                go(Routes.ACCOUNT)
                pagerState.animateScrollToPage(lastAllowedPage)
            } else {
                lastAllowedPage = pagerState.currentPage
            }
        }
    }

    // ===== 外部应用跳转确认 (2026-10-02: 用户要求跳转前先问一句) =====
    // 以前 mailto/tel/mqqwpa/uclink 等协议是直接 startActivity 抢跳, 现在一律先弹确认框
    var pendingExternal by remember { mutableStateOf<ExternalJumpTarget?>(null) }

    /**
     * 统一链接分流: http(s) → 内置浏览器;
     * 其它 scheme → 记下来弹确认框, 用户点「打开」才真跳, 点「取消」什么都不做
     */
    fun openLink(url: String, title: String, password: String = "") {
        val target = resolveExternalJump(context, url)
        if (target != null) {
            pendingExternal = target
            return
        }
        // 腾讯频道 / QQ群 一律保持在内置浏览器打开 (2026-10-03 用户明确要求)。
        // 之前「交 QQ 客户端打开」的方案已撤销; 页面完整渲染改由 WebView 侧保障
        // (CookieManager 放开第三方 cookie + 反爬挑战页等待 + 渲染能力补齐), 见 WebViewScreen.kt。
        go(Routes.webview(url, title, password))
    }

    // ===== 公告 (首页弹窗: 每日一次 / 每次打开 + 今日不再提示) =====
    // 语义: 「今日不再提示」只对当时那条公告当天生效; 公告内容一变 (hidden/shown content != 当前), 当天也重新弹
    var noticeInfo by remember { mutableStateOf<NoticeInfo?>(null) }
    var showNotice by remember { mutableStateOf(false) }
    var noMoreToday by remember { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    LaunchedEffect(Unit) {
        try {
            val info = ApiClient.getNotice()
            if (info.enabled && info.content.isNotBlank()) {
                val hiddenDate = Settings.noticeHiddenDate
                val hiddenContent = Settings.noticeHiddenContent
                val shownDate = Settings.noticeShownDate
                val shownContent = Settings.noticeShownContent
                val content = info.content
                val canShow = when (info.mode) {
                    // 每日一次: 今天没弹过, 或 (今天弹过但公告改了 → 重新弹)
                    "daily" -> shownDate != today || shownContent != content
                    // 每次打开: 没勾今日不再, 或 (勾过但公告改了 → 重新弹)
                    else -> hiddenDate != today || hiddenContent != content
                }
                if (canShow) {
                    noticeInfo = info
                    showNotice = true
                    // 记录本次弹窗 (daily 模式: 今天已弹过 + 弹的是哪条内容)
                    if (info.mode == "daily") {
                        Settings.noticeShownDate = today
                        Settings.noticeShownContent = content
                    }
                }
            }
        } catch (_: Exception) { }
    }
    if (showNotice) {
        NoticeDialog(
            info = noticeInfo,
            noMoreToday = noMoreToday,
            onNoMoreTodayChange = { noMoreToday = it },
            onLinkClick = { url ->
                // 公告里的链接统一分流: http(s) 内置浏览器, 自定义协议 (mqqwpa:// 等) 交系统应用
                showNotice = false
                openLink(url, "公告详情")
            },
            onDismiss = {
                if (noMoreToday) {
                    // 今日不再提示: 记录日期 + 当前公告内容 (公告改了当天也会重新弹)
                    Settings.noticeHiddenDate = today
                    Settings.noticeHiddenContent = noticeInfo?.content ?: ""
                }
                showNotice = false
            },
        )
    }

    // 外部应用跳转确认框 (公告/关于页/详情页里点了自定义协议链接时弹出)
    pendingExternal?.let { target ->
        ExternalJumpDialog(
            target = target,
            onConfirm = {
                val opened = runCatching { context.startActivity(target.intent) }.isSuccess
                if (!opened) {
                    // 没有应用能接管 → http(s) 回退内置浏览器, 不让用户卡住 (2026-10-03)
                    if (target.url.startsWith("http")) {
                        go(Routes.webview(target.url, target.appLabel))
                    } else {
                        Toast.makeText(
                            context,
                            "没有找到可以打开该链接的应用",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                pendingExternal = null
            },
            onDismiss = { pendingExternal = null },
        )
    }

    // 进入 = 滑动切换 (点击应用右滑进入, 旧页左移 1/4 视差)
    // 返回 = 预测性返回跟手 (同 appScreen, 见上面的说明)
    val slideSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    // v1.1.15 保底: NavHost 外层铺一层不透明的 App 渐变底。
    // 预测性返回跟手时, 当前页整体右移、在左边让出一条缝; 只要这一层是活着且静止的背景,
    // 那条缝就永远是 App 底色, 不可能再露出窗口底色 (用户报的「返回过程一片空白」)。
    // 这里只是一个静态绘制的背景层, 不截图、不写状态, 不影响任何页面已验证正常的预测返回。
    Box(modifier = Modifier.fillMaxSize().appGradientBackground()) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { slideInHorizontally(slideSpec) { it } },
        exitTransition = { slideOutHorizontally(slideSpec) { -it / 4 } },
        // 与 appScreen 内保持一致: 返回走原生卡片式, 转场留空(位移/缩放/暗色都在 PredictivePageLayer)。
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        appScreen(Routes.MAIN) {
            // 非首个 tab 按返回 → 收回「首页」(参考项目用 NavigationBackHandler 做同一件事)。
            // 这里故意用普通 BackHandler 而不是预测性返回: 参考项目本身也没有自绘跟手动画,
            // 它把跟手整个交给系统/框架。本页在 NavHost 栈深 1 时 NavHost 的
            // PredictiveBackHandler 是 disabled, 再挂一层自绘位移就变成「两层预测返回」。
            // 另外 enabled 里判当前路由: 一旦栈里压上详情页, 就整个交回 NavHost。
            val currentEntry by navController.currentBackStackEntryAsState()
            val onMainRoute = currentEntry?.destination?.route == Routes.MAIN
            BackHandler(enabled = onMainRoute && pagerState.currentPage != 0) {
                scope.launch { pagerState.animateScrollToPage(0) }
            }
            // 底栏摆放照抄参考项目: 用 Scaffold 的 bottomBar 槽位, 不再把底栏叠在内容 Box 上。
            // containerColor 透明 —— 底下那层 appGradientBackground 要能透出来。
            // contentWindowInsets 归零 —— 内容区本来就是 edge-to-edge 全屏, 各页面自己处理状态栏,
            // 交给 Scaffold 补 systemBars 只会多出一份顶部内边距。
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    Box(Modifier.fillMaxWidth()) {
                        // 底栏: 照抄参考项目的 BottomBarMiuix ——
                        // 关悬浮 → BlurredBar + NavigationBar (原生);
                        // 开悬浮 → FloatingBottomBar (液体玻璃胶囊, 可拖动切 tab)
                        MainBottomBar(
                            items = navItems,
                            selectedIndex = { pagerState.currentPage },
                            onSelect = { index ->
                                if (index == SOCIAL_TAB_PAGE && !UserStore.isLoggedIn()) {
                                    // 未登录点「群组」: 直接去登录页, 不切到这个 tab (用户 2026-10-04 要求)
                                    go(Routes.ACCOUNT)
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            },
                            blurBackdrop = blurBackdrop,
                            backdrop = barBackdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                },
            ) { _ ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background),
            ) {
                // 内容区: 全屏 (无底部 padding) —— 列表必须能滚动到悬浮胶囊下方,
                // 捕获层才能拿到真实内容做模糊; 底部留白由各页面 LazyColumn
                // contentPadding bottom 自行处理 (OShin 同款布局)。
                // 注意: 这里故意不消费 Scaffold 的 innerPadding, 否则会和页面自带的
                // 100/110.dp 底部留白叠成双份。
                // 这里不再挂任何 predictiveBackTransform: 主页面在 NavHost 栈深 1,
                // 页面本身没有「上一层」可揭示, 自绘跟手只会和框架的预测返回叠成两层。
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(captureModifier)
                        .then(barCaptureModifier),
                ) {
                    // HorizontalPager: tab 左右滑动切换
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 2,
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                onAppClick = { go(Routes.detail(it)) },
                                onOpenUrl = { url, title ->
                                    // 内置浏览器打开
                                    go(Routes.webview(url, title))
                                },
                            )
                            1 -> ExploreScreen(onAppClick = { go(Routes.detail(it)) })
                            2 -> SocialScreen(
                                // 「群组」tab: 常驻列表页, 无返回栏; 点群组进全屏聊天
                                onBack = null,
                                // 契约 A: 分段选中值由本层持有并回传 (受控), 页面重建也不丢
                                initialTab = socialTab,
                                onTabChange = { socialTab = it },
                                onOpenGroup = { g -> go(Routes.socialGroup(g.id)) },
                                // 群聊消息里的链接: 内置浏览器打开
                                onOpenWeb = { url, title -> openLink(url, title) },
                                onNeedLogin = { go(Routes.ACCOUNT) },
                                // 点群聊里任何人的头像: 打开用户主页 (带上群 id, 主页里可直接禁言)
                                onOpenUser = { uid, gid -> go(Routes.userProfile(uid, gid)) },

                                // 私聊会话列表点一条: 进私聊会话页
                                onOpenPm = { uid, cid -> go(Routes.pmChat(cid, uid)) },
                                // 群列表点「有人@你 / 有人@所有人」: 进群并定位到那条消息
                                onOpenGroupAt = { gid, mid -> go(Routes.socialGroup(gid, mid)) },
                                // 页面级捕获层: 「群组 / 私聊」液体玻璃分段控件用它做真实 backdrop 模糊
                                glassBackdrop = backdrop,
                            )
                            else -> MyScreen(
                                onOpenThemeSettings = {
                                    go(Routes.THEME_SETTINGS)
                                },
                                onOpenWeb = { url, title ->
                                    // 自定义协议先弹确认框, http(s) 内置浏览器
                                    openLink(url, title)
                                },
                                onOpenUpdate = { info ->
                                    go(Routes.update(info))
                                },
                                onOpenContributors = {
                                    go(Routes.CONTRIBUTORS)
                                },
                                onOpenAccount = {
                                    go(Routes.ACCOUNT)
                                },
                                onOpenMessages = {
                                    go(Routes.MESSAGES)
                                },
                                onOpenNotifySettings = {
                                    go(Routes.NOTIFY_SETTINGS)
                                },
                            )
                        }
                    }
                }
            }
            }
        }

        // 详情页
        appScreen(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("appId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getInt("appId") ?: 0
            DetailScreen(
                appId = appId,
                onBack = { navController.popBackStack() },
                onOpenWeb = { url, title, password ->
                    // 自定义协议先弹确认框, http(s) 内置浏览器
                    openLink(url, title, password)
                },
                onOpenSubApp = { subId ->
                    go(Routes.detail(subId))
                },
            )
        }

        // 内置浏览器
        appScreen(
            route = Routes.WEBVIEW,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("password") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val password = backStackEntry.arguments?.getString("password") ?: ""
            WebViewScreen(
                url = url,
                title = title,
                password = password,
                onBack = { navController.popBackStack() },
            )
        }

        // 投稿名单页
        appScreen(Routes.CONTRIBUTORS) {
            ContributorsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 账号页 (登录 / 注册 / 个人资料)
        appScreen(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 群组聊天页 (从「群组」tab 点进来, 直接进入指定群)
        appScreen(
            route = Routes.SOCIAL_GROUP,
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType },
                // 从「消息」里的 @我 通知点进来时, 直接定位到那条消息
                navArgument("messageId") { type = NavType.IntType; defaultValue = 0 },
                // 群公告通知点进来时, 直接弹公告
                navArgument("notice") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { backStackEntry ->
            SocialScreen(
                onBack = {
                    // 返回时直接回到主界面(群组 tab 的列表页)。
                    // 原来用 popBackStack() 会落到「我的-社交群组」那一页(现已移除), 用户反馈返回位置不对
                    navController.popBackStack(Routes.MAIN, false)
                },
                initialGroupId = backStackEntry.arguments?.getInt("groupId") ?: 0,
                onNeedLogin = { go(Routes.ACCOUNT) },
                initialMessageId = backStackEntry.arguments?.getInt("messageId") ?: 0,
                openNotice = (backStackEntry.arguments?.getInt("notice") ?: 0) == 1,
                // 群聊消息里的链接: 内置浏览器打开
                onOpenWeb = { url, title -> openLink(url, title) },
                // 群聊里点头像: 带上当前群 id → 主页里就能直接禁言 (只对本群生效)
                onOpenUser = { uid, gid -> go(Routes.userProfile(uid, gid)) },
                onOpenPm = { uid, cid -> go(Routes.pmChat(cid, uid)) },
                onOpenGroupAt = { gid, mid -> go(Routes.socialGroup(gid, mid)) },
                // 群聊右上角「☰」: 进群详情页 (群公告 / 群相册 / 群成员 / 退出群聊)
                onOpenGroupInfo = { gid -> go(Routes.groupInfo(gid)) },
                glassBackdrop = backdrop,
            )
        }

        // 群详情页 (契约 B6): 群公告 / 群相册 / 群成员 / 消息免打扰 / 全员禁言 / 退出群聊
        appScreen(
            route = Routes.GROUP_INFO,
            arguments = listOf(navArgument("groupId") { type = NavType.IntType }),
        ) { backStackEntry ->
            GroupInfoScreen(
                groupId = backStackEntry.arguments?.getInt("groupId") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenMembers = { id -> go(Routes.groupMembers(id)) },
                onOpenUser = { uid, id -> go(Routes.userProfile(uid, id)) },
                // 群公告里的链接: 走内置浏览器
                onOpenWeb = { url, title -> openLink(url, title) },
                // 退出群聊成功: 直接回群列表 (群详情页 + 群聊页一起弹掉)
                onLeft = { navController.popBackStack(Routes.MAIN, false) },
            )
        }

        // 群成员列表页 (契约 B6): 搜索昵称 / 用户名 + 分页 + 点成员进主页
        appScreen(
            route = Routes.GROUP_MEMBERS,
            arguments = listOf(
                navArgument("groupId") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            GroupMemberListScreen(
                groupId = backStackEntry.arguments?.getInt("groupId") ?: 0,
                onBack = { navController.popBackStack() },
                groupName = backStackEntry.arguments?.getString("name") ?: "",
                onOpenUser = { uid, gid -> go(Routes.userProfile(uid, gid)) },
            )
        }

        // 消息中心 (通知列表; link 走统一链接分流, http(s) 内置浏览器)
        appScreen(Routes.MESSAGES) {
            MessagesScreen(
                onBack = { navController.popBackStack() },
                onOpenWeb = { url, title ->
                    openLink(url, title)
                },
                // @我 / 群消息通知: 跳进对应群聊并定位到那条消息; 群公告通知: 进群并弹出公告
                onOpenGroup = { groupId, messageId, showNotice ->
                    go(Routes.socialGroup(groupId, messageId, showNotice))
                },
                // 私聊通知 (link = pm:<conv_id>:<msg_id>): 进私聊会话并定位到那条消息
                onOpenPm = { convId, messageId ->
                    go(Routes.pmChat(convId = convId, messageId = messageId))
                },
            )
        }

        // 消息通知设置 (我的 -> 消息通知): 后台接收开关 + 权限引导
        appScreen(Routes.NOTIFY_SETTINGS) {
            NotifySettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 主题设置 (我的 -> 主题): 明暗 / 主题色 / 色板风格 / 模糊 / 悬浮底栏 / 预测性返回
        appScreen(Routes.THEME_SETTINGS) {
            ThemeSettingsScreen(
                onBack = { navController.popBackStack() },
                onChanged = onThemeChanged,
            )
        }

        // 用户主页 (群聊 / 私聊里点任何人的头像进入, 含自己; 自己看时没有「发消息」按钮)
        appScreen(
            route = Routes.USER_PROFILE,
            arguments = listOf(
                navArgument("userId") { type = NavType.IntType },
                // 群聊进来的群 id (禁言只对本群生效); 私聊/消息中心进来 = 0 (全站视角)
                navArgument("groupId") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { backStackEntry ->
            UserProfileScreen(
                userId = backStackEntry.arguments?.getInt("userId") ?: 0,
                groupId = backStackEntry.arguments?.getInt("groupId") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenPm = { uid, cid -> go(Routes.pmChat(convId = cid, userId = uid)) },
                // 用户简介里的链接: 走内置浏览器
                onOpenWeb = { url, title -> openLink(url, title) },
            )
        }

        // 私聊会话页 (会话列表 / 用户主页「发消息」/ 私聊通知进入)
        appScreen(
            route = Routes.PM_CHAT,
            arguments = listOf(
                navArgument("convId") { type = NavType.IntType; defaultValue = 0 },
                navArgument("userId") { type = NavType.IntType; defaultValue = 0 },
                // 从私聊通知点进来时定位到那条消息
                navArgument("messageId") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { backStackEntry ->
            PmChatScreen(
                initialConvId = backStackEntry.arguments?.getInt("convId") ?: 0,
                initialUserId = backStackEntry.arguments?.getInt("userId") ?: 0,
                initialMessageId = backStackEntry.arguments?.getInt("messageId") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenWeb = { url, title -> openLink(url, title) },
                // 私聊里点头像: 没有群上下文 → groupId = 0 (全站视角)
                onOpenUser = { uid -> go(Routes.userProfile(uid)) },
            )
        }

        // 软件更新页 (OShin 同款: 下载并安装)
        appScreen(
            route = Routes.UPDATE,
            arguments = listOf(
                navArgument("version") { type = NavType.StringType; defaultValue = "" },
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("log") { type = NavType.StringType; defaultValue = "" },
                navArgument("mode") { type = NavType.StringType; defaultValue = "internal" },
                navArgument("size") { type = NavType.FloatType; defaultValue = 0f },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            UpdateScreen(
                info = VersionInfo(
                    version = args?.getString("version") ?: "",
                    url = args?.getString("url") ?: "",
                    updateLog = args?.getString("log") ?: "",
                    updateMode = args?.getString("mode") ?: "internal",
                    sizeMb = args?.getFloat("size") ?: 0f,
                    releaseDate = args?.getString("date") ?: "",
                ),
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}

/**
 * 公告弹窗 (首页弹出, 富文本 HTML 渲染)
 * 2026-10-02 重新设计: 去掉高饱和渐变横幅 (塑料感来源) → 浅色图标 + 通透留白;
 * WebView 底色改透明、融进圆角容器, 消除「白块拼贴」; 进出场缩放淡入。
 */
@Composable
private fun NoticeDialog(
    info: NoticeInfo?,
    noMoreToday: Boolean,
    onNoMoreTodayChange: (Boolean) -> Unit,
    onLinkClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val content = info?.content ?: ""
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    val scheme = MiuixTheme.colorScheme

    // 不做任何入场动画: Dialog 遮罩一出现, 卡片就是最终状态。
    // 历史坑: ①欠阻尼 spring 缩放 + alpha 淡入 → 回弹 + 遮罩空窗;
    //         ②animateContentSize 跟随 WebView 加载时的高度多帧变化,
    //           默认 spring(StiffnessMediumLow) 会让卡片缓慢弹性伸缩 —— 用户反馈的「很抖很抖慢慢的」。

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Miuix 官方 Card (自带正确的圆角与配色, 不再手搓 clip+background)
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                // ── 头部: 左对齐标题 + 淡色关闭按钮 (去掉彩色圆底图标, 不再有模板感) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "公告",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = scheme.onBackgroundVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.surfaceVariant),
                )

                // ── 内容: WebView 直接铺在卡片上 (透明底, 不再套一层灰色容器) ──
                val webView = remember { WebView(context) }
                LaunchedEffect(content) {
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val u = url ?: return false
                            // 链接统一交上层分流 (http(s) 内置浏览器; 自定义协议弹确认框)
                            onLinkClick(u)
                            return true
                        }
                    }
                    val textColor = if (isDark) "#E6E1E5" else "#1A1A2E"
                    val css = "<style>" +
                        "html,body{background:transparent;color:$textColor;font-size:14.5px;line-height:1.75;word-break:break-word;}" +
                        "body{padding:0;margin:0;}" +
                        "p{margin:0 0 8px 0;} p:last-child{margin-bottom:0;}" +
                        "a{color:#4C6FFF;text-decoration:none;font-weight:500;}" +
                        "img{max-width:100%;border-radius:10px;display:block;margin:8px 0;}" +
                        "h1,h2,h3{font-size:16px;font-weight:600;color:$textColor;margin:10px 0 6px;}" +
                        "ul,ol{padding-left:20px;margin:6px 0;}" +
                        "blockquote{margin:8px 0;padding:8px 12px;border-left:3px solid #4C6FFF;background:rgba(127,127,127,0.08);border-radius:8px;}" +
                        "</style>"
                    val fullHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">$css</head><body>$content</body></html>"
                    webView.loadDataWithBaseURL(null, fullHtml, "text/html", "utf-8", null)
                }
                AndroidView(
                    factory = { webView },
                    update = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 80.dp, max = 300.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.surfaceVariant),
                )

                // ── 底部: 方形勾选 (原生质感) + 主按钮 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onNoMoreTodayChange(!noMoreToday) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (noMoreToday) scheme.primary else Color.Transparent)
                                .then(
                                    if (noMoreToday) Modifier
                                    else Modifier.border(
                                        BorderStroke(1.5.dp, scheme.onBackgroundVariant.copy(alpha = 0.45f)),
                                        RoundedCornerShape(5.dp),
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (noMoreToday) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = scheme.onPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "今日不再提示",
                            fontSize = 13.sp,
                            color = scheme.onBackgroundVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Miuix 官方 Button (自带按压反馈与涟漪, 不手搓 Box + clickable)
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        cornerRadius = 23.dp,
                    ) {
                        Text(
                            text = "我知道了",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 私聊新消息提示音 (仅 App 前台生效)
 *
 * 每 8 秒静默拉一次私聊会话列表, 发现「别人发来的」新消息就响一声系统通知音。
 * 放在 MainScreen 里做, 这样用户停在哪个页面都能听到, 不用每个页面各写一份。
 *
 * 注意: 只在 App 处于前台时轮询 (ON_RESUME ~ ON_PAUSE)。想做到「退到后台也响」
 * 必须有常驻前台服务, 属于另一个量级的改动, 暂未做。
 */
@Composable
private fun PmNotifyWatcher() {
    val context = LocalContext.current
    val foreground = rememberAppForeground()
    // 已经见过的最新私聊消息 id; -1 = 还没建立基线 (首次只记录, 不响, 免得一进 App 就响)
    var seenMaxId by remember { mutableStateOf(-1) }

    // ---------- 兜底轮询 (SSE 挂了 / 断线窗口里也能响) ----------
    // 每 8 秒静默拉一次私聊会话列表, 发现「别人发来的」新消息就响一声。
    // ⚠️ 接上 SSE 后这条轮询**保留不动** (契约: 现有轮询一律保留作兜底)。
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) {
            if (UserStore.isLoggedIn()) {
                ApiClient.pmConversations().getOrNull()?.let { page ->
                    // 只看「别人发给我的」最后一条: 自己发的不算, 撤回后字段也会是空
                    val newestIncoming = page.list
                        .mapNotNull { it.last }
                        .filter { !it.mine }
                        .maxOfOrNull { it.id } ?: 0
                    when {
                        seenMaxId < 0 -> seenMaxId = newestIncoming
                        newestIncoming > seenMaxId -> {
                            seenMaxId = newestIncoming
                            NotifySound.play(context)
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(4000L)
        }
    }

    // ---------- 后台常驻服务: 按「已登录 + 开关」启停 (v1.0.35) ----------
    // 那条唯一的 SSE 连接从 MainScreen 搬到了 MessageService (前台服务): 退到后台/桌面也不断,
    // 才能像 QQ/微信一样秒收。这里只负责把「该不该跑」这个状态摆正, 不再按 foreground 断连。
    // - 已登录 + 开关打开 -> 拉起服务 (幂等, 已在跑就什么都不做)
    // - 未登录 -> 停掉服务 (没 token 挂着也是白发一条常驻通知)
    // 用户在设置页关开关时那边会自己 stopService, 这里不会把它又拉起来。
    // key 带上 foreground: 从设置页返回/回到前台时重新对一次状态。
    LaunchedEffect(foreground, UserStore.hasToken) {
        when {
            UserStore.isLoggedIn() && Settings.msgServiceOn -> MessageService.start(context)
            !UserStore.isLoggedIn() -> MessageService.stop(context)
        }
    }

    // 订阅流事件:
    // - 私聊 -> 响
    // - 群消息 -> 也响 (v1.1.1: 群里任何人发消息都有提示音, 和 QQ/微信一致);
    //   自己发的消息不响; 该群被我设了「消息免打扰」(event.muted) 时不响
    // - Reconnected -> 这里什么都不用做 (各页面自己补一次全量刷新)
    LaunchedEffect(Unit) {
        MessageStream.events.collect { event ->
            when (event) {
                is StreamEvent.Pm -> {
                    // 同步兜底轮询的基线, 免得 8 秒后同一条消息又响一次
                    if (event.msgId > seenMaxId) seenMaxId = event.msgId
                    NotifySound.play(context)
                }
                is StreamEvent.Group -> {
                    val mine = event.userId != 0 && event.userId == UserStore.current?.id
                    if (!mine && !event.muted) NotifySound.play(context)
                }
                StreamEvent.Reconnected -> Unit
            }
        }
    }
}

/**
 * 本页面是否处于前台 (ON_RESUME ~ ON_PAUSE)
 *
 * 从 LocalContext 拿 ComponentActivity 注册 LifecycleEventObserver,
 * 不引额外依赖 (Lifecycle/ LifecycleEventObserver 来自项目已有的 lifecycle-common)。
 */
@Composable
private fun rememberAppForeground(): Boolean {
    val context = LocalContext.current
    val owner = context as? LifecycleOwner
    var foreground by remember { mutableStateOf(true) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE -> foreground = false
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return foreground
}
