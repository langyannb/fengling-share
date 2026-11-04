package com.fengling.share.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import java.util.concurrent.atomic.AtomicLong

/**
 * 目的地。全部是 value-derived 的 data class / data object：
 * NavDisplay 用 route 实例本身（或 contentKey）做返回栈身份，也是 rememberSaveable 的命名空间，
 * 所以必须是值语义 + @Serializable（进程被杀后返回栈靠 JSON 还原）。
 */
@Serializable
sealed interface Route {
    @Serializable
    data object Main : Route

    @Serializable
    data class Detail(val appId: Int) : Route

    @Serializable
    data class WebView(
        val url: String,
        val title: String,
        val password: String = "",
    ) : Route

    @Serializable
    data class Update(
        val version: String,
        val url: String,
        val log: String,
        val mode: String,
        val sizeMb: Float,
        val date: String,
    ) : Route

    @Serializable
    data object Contributors : Route

    @Serializable
    data object Account : Route

    @Serializable
    data class SocialGroup(
        val groupId: Int,
        val messageId: Int = 0,
        val notice: Boolean = false,
    ) : Route

    @Serializable
    data object Messages : Route

    @Serializable
    data object NotifySettings : Route

    @Serializable
    data object ThemeSettings : Route

    @Serializable
    data class PmChat(
        val convId: Int = 0,
        val userId: Int = 0,
        val messageId: Int = 0,
    ) : Route

    @Serializable
    data class UserProfile(val userId: Int, val groupId: Int = 0) : Route

    @Serializable
    data class GroupInfo(val groupId: Int) : Route

    @Serializable
    data class GroupMembers(val groupId: Int, val name: String = "") : Route
}

/**
 * 返回栈里的一层：包住 [Route] 并带一个单调递增的 [seq]。
 *
 * 为什么不直接把 Route 压栈：NavDisplay 要求每层的 contentKey **在整条栈上唯一**
 * （重复直接抛 IllegalArgumentException），而 data class 的相等性是按值算的。
 * 「私聊会话 → 用户主页 → 发消息」会再次压入完全相同的 PmChat 值，等于同一层压两次 → 崩溃。
 * seq 让每次入栈都是新实例，值相同也不会撞。
 */
@Serializable
data class Page(val seq: Long, val route: Route) : NavKey

/** 导航入口（薄封装，语义与原来 NavHost 上的 navigate/popBackStack 对齐） */
class AppNavigator(val controller: NavController) {

    val backStack: NavBackStack get() = controller.backStack

    /** 栈顶目的地 */
    val top: Route? get() = (backStack.lastOrNull() as? Page)?.route

    fun push(route: Route) {
        controller.push(Page(SEQ.incrementAndGet(), route))
    }

    /** 弹一层；栈里只剩根时返回 false（交给系统去结束 Activity） */
    fun pop(): Boolean = controller.pop()

    /** 一路回到根（原 popBackStack(Routes.MAIN, false) 的语义） */
    fun popToRoot() {
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    private companion object {
        // 进程内唯一即可；用纳秒起头，避免进程重建后与已还原的 seq 撞车
        val SEQ = AtomicLong(System.nanoTime())
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator {
    val controller = rememberNavController<Page>(Page(0L, Route.Main))
    return remember(controller) { AppNavigator(controller) }
}
