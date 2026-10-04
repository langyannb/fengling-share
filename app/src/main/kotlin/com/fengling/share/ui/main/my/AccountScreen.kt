package com.fengling.share.ui.main.my

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.User
import com.fengling.share.data.UserStore
import com.fengling.share.service.MessageService
import com.fengling.share.ui.components.AppTopBar
import com.fengling.share.ui.components.CaptchaDialog
import com.fengling.share.ui.components.predictiveBackTransform
import com.fengling.share.ui.components.rememberPredictiveBackProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 危险色 (退出登录等) — Miuix 主题没有 error 色, 用固定红 */
private val DangerRed = Color(0xFFE5484D)

/** 账号页视图 (未登录时在登录/注册之间切换) */
private enum class AccountView { LOGIN, REGISTER }

/**
 * AccountScreen - 账号页 (登录 / 注册 / 个人资料)
 *
 * 三种状态:
 * - 未登录 → 登录视图 (可切到注册)
 * - 注册视图 (邮箱验证码 60 秒倒计时)
 * - 已登录 → 资料视图 (头像/昵称/简介编辑, 改密码, 退出登录)
 *
 * 登录态用 UserStore 的 Compose 状态驱动, 登录成功/退出后视图自动切换。
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loggedIn = UserStore.hasToken
    val me = UserStore.current
    var view by remember { mutableStateOf(AccountView.LOGIN) }
    // 切换账号时把选中的用户名带进登录框
    var switchPrefill by remember { mutableStateOf("") }

    // 契约 B: 预测性返回(跟手) —— 跟手右移+缩小淡出, 松手过半分提交返回, 否则回弹;
    // 未开「预测性返回手势动画」的系统上系统不回传进度, 回调立刻正常结束 -> 直接 onBack(), 功能不变。
    val backProgress = rememberPredictiveBackProgress(enabled = true) { onBack() }

    Scaffold(
        modifier = modifier.predictiveBackTransform(backProgress),
        topBar = {
            AppTopBar(
                title = when {
                    loggedIn -> "个人资料"
                    view == AccountView.REGISTER -> "注册账号"
                    else -> "登录"
                },
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (loggedIn) {
                ProfileView(
                    me = me ?: User(),
                    onSwitchAccount = { username ->
                        // 只清本地登录态, 不作废服务端会话, 方便随时切回
                        switchPrefill = username
                        UserStore.clear()
                        view = AccountView.LOGIN
                    },
                )
            } else if (view == AccountView.LOGIN) {
                LoginView(
                    prefill = switchPrefill,
                    onSwitchToRegister = { view = AccountView.REGISTER },
                )
            } else {
                RegisterView(onSwitchToLogin = { view = AccountView.LOGIN })
            }
        }
    }
}

// ===================== 账号免密切换 (契约 F5) =====================

/**
 * 免密切换账号到 [username]。
 *
 * 「账号保险箱」(UserStore.account_vault) 里按用户名存了 {token, nickname, avatar, userId},
 * 所以切换账号**不需要再输密码**:
 * 1. 保险箱里有 token → UserStore.switchTo() 直接把登录态切过去, 再调 user_me 校验:
 *    校验通过 = 切换完成 (群列表 / 未读 / 私聊都会按新账号重新拉取);
 *    校验失败 (token 过期) = 清掉这条记录 + [onNeedPassword] 回填用户名让用户重新输密码。
 * 2. 没有 token (老版本只存了用户名的记录) → 直接 [onNeedPassword] 回填用户名。
 *
 * ⚠️ 这里**绝对不能**调 ApiClient.logoutAccount() —— 服务端 logout 会执行
 * `DELETE FROM sessions WHERE token = ?`, 把这条 session 删掉, 下次就没法免密切回来了。
 * 只有用户主动点「退出登录」时才允许调 logout。
 */
private fun switchAccountNoPassword(
    username: String,
    context: Context,
    scope: CoroutineScope,
    onBusy: (Boolean) -> Unit = {},
    onSwitched: (User) -> Unit = {},
    onNeedPassword: (String) -> Unit = {},
) {
    val name = username.trim()
    if (name.isEmpty()) return
    if (!UserStore.switchTo(name)) {
        // 保险箱里没有这个账号的 token (老版本数据) → 只能回填用户名让用户输密码
        onNeedPassword(name)
        Toast.makeText(context, "该账号需要重新输入密码登录", Toast.LENGTH_SHORT).show()
        return
    }
    onBusy(true)
    scope.launch {
        ApiClient.getMe()
            .onSuccess { user ->
                // 用服务端最新资料刷新本地缓存与会话 (昵称/头像可能在别处改过)
                UserStore.updateUser(user)
                onSwitched(user)
                Toast.makeText(context, "已切换到 " + user.displayName, Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                // token 已失效: 清掉这条记录, 回填用户名让用户重新登录
                UserStore.forgetAccount(name)
                UserStore.clear()
                onNeedPassword(name)
                Toast.makeText(context, "登录已过期，请重新输入密码", Toast.LENGTH_SHORT).show()
            }
        onBusy(false)
    }
}

// ===================== 登录视图 =====================

@Composable
private fun LoginView(
    prefill: String = "",
    onSwitchToRegister: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember(prefill) { mutableStateOf(prefill) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // 最近登录过的账号 (保险箱里有 token 的可免密切换, 老记录只能回填用户名)
    val recents = remember(prefill) { UserStore.recentAccounts() }
    // 正在免密切换的账号名 (空 = 没有在切换)
    var switching by remember { mutableStateOf("") }

    fun quickSwitch(name: String) {
        if (switching.isNotEmpty()) return
        switchAccountNoPassword(
            username = name,
            context = context,
            scope = scope,
            onBusy = { busy -> switching = if (busy) name else "" },
            onNeedPassword = { n ->
                switching = ""
                account = n
                password = ""
            },
        )
    }

    fun doLogin() {
        if (account.isBlank() || password.isBlank()) {
            Toast.makeText(context, "请填写用户名/邮箱和密码", Toast.LENGTH_SHORT).show()
        } else {
            loading = true
            scope.launch {
                ApiClient.loginAccount(account.trim(), password)
                    .onSuccess { (token, user) ->
                        UserStore.save(token, user)
                        Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { e ->
                        Toast.makeText(context, e.message ?: "登录失败", Toast.LENGTH_SHORT).show()
                    }
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        AccountHeader(
            icon = Icons.Filled.Person,
            title = "欢迎回来",
            subtitle = "登录后可同步头像、收藏与评论",
        )
        Spacer(Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                AppTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = "用户名或邮箱",
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    isPassword = true,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = if (loading) "登录中…" else "登录", enabled = !loading) { doLogin() }
            }
        }
        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SmallTitle(text = "切换账号 (最近登录)")
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    recents.forEachIndexed { index, name ->
                        if (index > 0) ProfileDivider()
                        val acc = UserStore.vaultAccount(name)
                        val canQuick = !acc?.token.isNullOrBlank()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (canQuick) {
                                        quickSwitch(name)
                                    } else {
                                        // 老记录没有 token: 只能回填用户名
                                        account = name
                                        password = ""
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarCircle(
                                url = acc?.avatar ?: "",
                                name = acc?.displayName ?: name,
                                size = 34.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = acc?.displayName?.takeIf { it.isNotBlank() } ?: name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "@$name",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    switching == name -> "切换中…"
                                    canQuick -> "免密切换"
                                    else -> "点击填入"
                                },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "还没有账号？",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Text(
                text = "立即注册",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSwitchToRegister() },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ===================== 注册视图 =====================

@Composable
private fun RegisterView(onSwitchToLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    // 图形验证码 (契约 v1: send_code 必带 captcha_token / captcha_code)
    var showCaptcha by remember { mutableStateOf(false) }
    var captchaError by remember { mutableStateOf("") }
    var captchaRefresh by remember { mutableStateOf(0) }

    // 验证码倒计时: 每 60 秒只能发一次
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000L)
            countdown -= 1
        }
    }

    /** 真正发验证码: 需要图形验证码 token + code (由 CaptchaDialog 回调提供) */
    fun doSendCode(captchaToken: String, captchaCode: String) {
        sending = true
        captchaError = ""
        scope.launch {
            ApiClient.sendCode(
                email = email.trim(),
                purpose = "register",
                captchaToken = captchaToken,
                captchaCode = captchaCode,
            )
                .onSuccess {
                    countdown = 60
                    showCaptcha = false
                    Toast.makeText(context, "验证码已发送，请查收邮件", Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    // 图形验证码是一次性的, 失败后换一张让用户重填
                    captchaError = e.message ?: "发送失败"
                    captchaRefresh += 1
                }
            sending = false
        }
    }

    fun doRegister() {
        val tip = when {
            username.isBlank() -> "请填写用户名"
            password.length < 6 -> "密码至少 6 位"
            email.isBlank() -> "请填写邮箱"
            code.isBlank() -> "请填写邮箱验证码"
            else -> null
        }
        if (tip != null) {
            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
        } else {
            registering = true
            scope.launch {
                ApiClient.register(
                    username = username.trim(),
                    password = password,
                    nickname = nickname.trim(),
                    email = email.trim(),
                    code = code.trim(),
                )
                    .onSuccess { (token, user) ->
                        UserStore.save(token, user)
                        Toast.makeText(context, "注册成功", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { e ->
                        Toast.makeText(context, e.message ?: "注册失败", Toast.LENGTH_SHORT).show()
                    }
                registering = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        AccountHeader(
            icon = Icons.Filled.Person,
            title = "创建账号",
            subtitle = "用户名 3-20 位字母/数字/下划线",
        )
        Spacer(Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                AppTextField(value = username, onValueChange = { username = it }, label = "用户名")
                Spacer(Modifier.height(12.dp))
                AppTextField(value = nickname, onValueChange = { nickname = it }, label = "昵称 (选填)")
                Spacer(Modifier.height(12.dp))
                AppTextField(value = email, onValueChange = { email = it }, label = "邮箱")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        AppTextField(value = code, onValueChange = { code = it }, label = "邮箱验证码")
                    }
                    Spacer(Modifier.width(10.dp))
                    SmallButton(
                        text = when {
                            sending -> "发送中"
                            countdown > 0 -> "${countdown}s"
                            else -> "获取验证码"
                        },
                        enabled = !sending && countdown == 0 && email.isNotBlank(),
                    ) {
                        if (email.isBlank()) {
                            Toast.makeText(context, "请先填写邮箱", Toast.LENGTH_SHORT).show()
                        } else {
                            captchaError = ""
                            showCaptcha = true
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码 (至少 6 位)",
                    isPassword = true,
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = if (registering) "注册中…" else "注册", enabled = !registering) { doRegister() }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "已有账号？",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Text(
                text = "去登录",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSwitchToLogin() },
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    // 图形验证码弹框 (确认后才真正调用 send_code)
    if (showCaptcha) {
        CaptchaDialog(
            title = "图形验证码",
            subtitle = "发送邮箱验证码前需要完成安全验证",
            errorMessage = captchaError,
            confirming = sending,
            refreshKey = captchaRefresh,
            onConfirm = { token, captchaCode -> doSendCode(token, captchaCode) },
            onDismiss = { if (!sending) showCaptcha = false },
        )
    }
}

// ===================== 资料视图 =====================

@Composable
private fun ProfileView(
    me: User,
    onSwitchAccount: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf("") } // "nickname" / "bio" / ""
    var editText by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEmailVerify by remember { mutableStateOf(false) }
    // 切换账号: 弹框 + 正在免密切换的账号名
    var showSwitchDialog by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf("") }

    // 点某个最近账号: 保险箱里有 token 就直接免密切换, 否则回填登录框
    fun doSwitch(name: String) {
        if (switching.isNotEmpty()) return
        switchAccountNoPassword(
            username = name,
            context = context,
            scope = scope,
            onBusy = { busy -> switching = if (busy) name else "" },
            onNeedPassword = { n ->
                switching = ""
                showSwitchDialog = false
                // 交给 AccountScreen: 清本地登录态 + 回填用户名进登录框 (不作废服务端会话)
                onSwitchAccount(n)
            },
        )
    }

    // 进页面拉一次最新资料 (昵称/头像可能在别处改过)
    LaunchedEffect(Unit) {
        ApiClient.getMe().onSuccess { UserStore.updateUser(it) }
    }

    // 相册选图 → 读字节 → multipart 上传头像
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    uploading = false
                    Toast.makeText(context, "读取图片失败，请换一张", Toast.LENGTH_SHORT).show()
                } else {
                    val mime = context.contentResolver.getType(uri) ?: "image/*"
                    // 相册 URI 的 lastPathSegment 常常是一串数字 (没有扩展名), 这里按 MIME 给出规范文件名,
                    // 避免后端因扩展名不合法而拒绝 (相册选图不带 .jpg/.png)
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("webp") -> "webp"
                        mime.contains("gif") -> "gif"
                        else -> "jpg"
                    }
                    val filename = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() && it.contains('.') }
                        ?: "avatar.$ext"
                    ApiClient.uploadAvatar(bytes, filename, mime)
                        .onSuccess {
                            // 后端已同步 users.avatar, 重新拉一次资料刷新界面
                            ApiClient.getMe().onSuccess { fresh -> UserStore.updateUser(fresh) }
                            Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { e ->
                            Toast.makeText(context, e.message ?: "头像上传失败", Toast.LENGTH_SHORT).show()
                        }
                    uploading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        // 大头像 + 昵称 + 用户名
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                AvatarCircle(url = me.avatarUrl, name = me.displayName, size = 88.dp)
                if (uploading) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "上传中", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = me.displayName.ifBlank { "风铃用户" },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (me.bio.isBlank()) "还没有简介" else me.bio,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        SmallTitle(text = "账号资料")
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            ProfileRow(title = "用户名", value = me.username.ifBlank { "-" })
            ProfileDivider()
            ProfileRow(
                title = "昵称",
                value = me.nickname.ifBlank { "未设置" },
                editable = true,
            ) {
                editTarget = "nickname"
                editText = me.nickname
            }
            ProfileDivider()
            ProfileRow(
                title = "邮箱",
                value = me.email.ifBlank { "未绑定" },
                badge = if (me.email.isBlank()) null else if (me.emailVerified) "已验证" else "未验证",
                badgeHighlight = me.emailVerified,
            )
            ProfileDivider()
            ProfileRow(
                title = "简介",
                value = me.bio.ifBlank { "点击填写一句话简介 (最多 100 字)" },
                editable = true,
            ) {
                editTarget = "bio"
                editText = me.bio
            }
        }

        // 邮箱未验证 / 未绑定: 醒目提示 + 验证入口 (契约 v1 第四节)
        if (me.email.isBlank() || !me.emailVerified) {
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(
                    color = DangerRed.copy(alpha = 0.10f),
                    contentColor = MiuixTheme.colorScheme.onBackground,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "邮箱未验证",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = DangerRed,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (me.email.isBlank()) {
                                "还没有绑定邮箱, 验证后可用于找回密码"
                            } else {
                                "验证 " + me.email + " 后可用于找回密码与接收通知"
                            },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    SmallButton(text = "验证邮箱", enabled = true) {
                        showEmailVerify = true
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SmallTitle(text = "安全与操作")
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            ProfileRow(
                title = "更换头像",
                value = if (uploading) "上传中…" else "从相册选择图片",
                onClick = { if (!uploading) pickAvatar.launch("image/*") },
            )
            ProfileDivider()
            ProfileRow(
                title = "修改密码",
                value = "旧密码 + 新密码",
                onClick = {
                    oldPassword = ""
                    newPassword = ""
                    showPasswordDialog = true
                },
            )
            ProfileDivider()
            ProfileRow(
                title = "切换账号",
                value = "当前: ${me.username}",
                onClick = { showSwitchDialog = true },
            )
            ProfileDivider()
            ProfileRow(
                title = "退出登录",
                danger = true,
                onClick = { showLogoutDialog = true },
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    // ===== 邮箱验证弹框 =====
    if (showEmailVerify) {
        EmailVerifyDialog(
            initialEmail = me.email,
            onDismiss = { showEmailVerify = false },
            onVerified = { user ->
                showEmailVerify = false
                // 先用返回的 user 立刻刷新界面, 再拉一次资料保证与后端一致
                UserStore.updateUser(user)
                scope.launch {
                    ApiClient.getMe().onSuccess { fresh -> UserStore.updateUser(fresh) }
                }
            },
        )
    }

    // ===== 昵称 / 简介 编辑弹框 =====
    if (editTarget.isNotEmpty()) {
        val isBio = editTarget == "bio"
        val maxLen = if (isBio) 100 else 20
        AlertDialog(
            onDismissRequest = { editTarget = "" },
            title = {
                Text(
                    text = if (isBio) "编辑简介" else "编辑昵称",
                    color = MiuixTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    AppTextField(
                        value = editText,
                        onValueChange = { if (it.length <= maxLen) editText = it },
                        label = if (isBio) "简介 (最多 100 字)" else "昵称 (最多 20 字)",
                        singleLine = !isBio,
                        minHeight = if (isBio) 110.dp else null,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${editText.length}/$maxLen",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            },
            confirmButton = {
                M3TextButton(onClick = {
                    val target = editTarget
                    val text = editText
                    editTarget = ""
                    scope.launch {
                        val result = if (target == "bio") {
                            ApiClient.updateProfile(bio = text)
                        } else {
                            ApiClient.updateProfile(nickname = text)
                        }
                        result
                            .onSuccess { fresh ->
                                UserStore.updateUser(fresh)
                                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { e ->
                                Toast.makeText(context, e.message ?: "保存失败", Toast.LENGTH_SHORT).show()
                            }
                    }
                }) {
                    Text(text = "保存", color = MiuixTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                M3TextButton(onClick = { editTarget = "" }) {
                    Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }

    // ===== 修改密码弹框 =====
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = {
                Text(text = "修改密码", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Column {
                    AppTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = "旧密码",
                        isPassword = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    AppTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = "新密码 (至少 6 位)",
                        isPassword = true,
                    )
                }
            },
            confirmButton = {
                M3TextButton(onClick = {
                    if (oldPassword.isBlank() || newPassword.length < 6) {
                        Toast.makeText(context, "新密码至少 6 位", Toast.LENGTH_SHORT).show()
                    } else {
                        val old = oldPassword
                        val fresh = newPassword
                        showPasswordDialog = false
                        scope.launch {
                            ApiClient.changePassword(old, fresh)
                                .onSuccess {
                                    Toast.makeText(context, "密码已修改", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { e ->
                                    Toast.makeText(context, e.message ?: "修改失败", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }) {
                    Text(text = "确定", color = MiuixTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                M3TextButton(onClick = { showPasswordDialog = false }) {
                    Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }

    // ===== 切换账号 (免密: 保险箱里有 token 的点一下就切, 老记录才回填让输密码) =====
    if (showSwitchDialog) {
        val others = UserStore.recentAccounts().filter { it != me.username }
        AlertDialog(
            onDismissRequest = { if (switching.isEmpty()) showSwitchDialog = false },
            title = {
                Text(text = "切换账号", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Column {
                    Text(
                        text = "点一下要切换的账号即可直接登录（已记住登录状态，不用再输密码）。" +
                            "当前账号不会丢失，随时可以切回来。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (others.isEmpty()) {
                        Text(
                            text = "还没有其它登录过的账号，点「去登录」输入另一个账号。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    } else {
                        others.forEach { name ->
                            val acc = UserStore.vaultAccount(name)
                            val canQuick = !acc?.token.isNullOrBlank()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = switching.isEmpty()) { doSwitch(name) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AvatarCircle(
                                    url = acc?.avatar ?: "",
                                    name = acc?.displayName ?: name,
                                    size = 34.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = acc?.displayName?.takeIf { it.isNotBlank() } ?: name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "@$name",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        switching == name -> "切换中…"
                                        canQuick -> "免密切换"
                                        else -> "需输密码"
                                    },
                                    fontSize = 12.sp,
                                    color = if (canQuick) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onBackgroundVariant
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                M3TextButton(
                    onClick = {
                        showSwitchDialog = false
                        if (others.isEmpty()) {
                            // 没有历史账号: 清本地登录态, 回到登录框手动输
                            onSwitchAccount(me.username)
                        }
                    },
                ) {
                    Text(
                        text = if (others.isEmpty()) "去登录" else "关闭",
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            },
        )
    }

    // ===== 退出登录二次确认 =====
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = "退出登录", color = MiuixTheme.colorScheme.onBackground)
            },
            text = {
                Text(
                    text = "退出后需要重新登录才能同步头像与评论，确定退出吗？",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            },
            confirmButton = {
                M3TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        // ⚠️ 这里是**唯一**允许调用 logout 的地方: 服务端 logout 会
                        // `DELETE FROM sessions WHERE token = ?`, 把该账号的 session 作废。
                        // 切换账号绝不能走这里, 否则下次就没法免密切回来了。
                        // 后端作废失败也不阻塞本地退出
                        ApiClient.logoutAccount()
                        UserStore.clear()
                        // 主动退出就把后台常驻服务停掉: 没 token 的连接留着只会白挂一条常驻通知
                        MessageService.stop(context)
                        Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(text = "退出", color = DangerRed)
                }
            },
            dismissButton = {
                M3TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            },
        )
    }
}

/**
 * 邮箱验证弹框 (契约 v1 第四节 email_verify_send / email_verify)
 *
 * - 邮箱 + 图形验证码 + 邮箱验证码
 * - 图形验证码用单独的弹框展示 (showCaptcha 为真时只渲染 CaptchaDialog, 避免两层弹框叠加)
 * - 提交成功后通过 onVerified 把最新的 user 交回调用方刷新资料
 */
@Composable
private fun EmailVerifyDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onVerified: (User) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var code by remember { mutableStateOf("") }
    var showCaptcha by remember { mutableStateOf(false) }
    var captchaError by remember { mutableStateOf("") }
    var captchaRefresh by remember { mutableStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }

    // 60 秒内只能发一次
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000L)
            countdown -= 1
        }
    }

    fun doSend(captchaToken: String, captchaCode: String) {
        sending = true
        captchaError = ""
        scope.launch {
            ApiClient.emailVerifySend(
                email = email.trim(),
                captchaToken = captchaToken,
                captchaCode = captchaCode,
            )
                .onSuccess { sentTo ->
                    if (sentTo.isNotBlank()) email = sentTo
                    countdown = 60
                    showCaptcha = false
                    Toast.makeText(context, "验证码已发送，请查收邮件", Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    captchaError = e.message ?: "发送失败"
                    captchaRefresh += 1
                }
            sending = false
        }
    }

    fun doVerify() {
        if (email.isBlank() || code.isBlank()) {
            Toast.makeText(context, "请填写邮箱和邮箱验证码", Toast.LENGTH_SHORT).show()
            return
        }
        verifying = true
        scope.launch {
            ApiClient.emailVerify(email.trim(), code.trim())
                .onSuccess { user ->
                    onVerified(user)
                    Toast.makeText(context, "邮箱验证成功", Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    Toast.makeText(context, e.message ?: "验证失败", Toast.LENGTH_SHORT).show()
                }
            verifying = false
        }
    }

    if (showCaptcha) {
        CaptchaDialog(
            title = "图形验证码",
            subtitle = "发送邮箱验证码前需要完成安全验证",
            errorMessage = captchaError,
            confirming = sending,
            refreshKey = captchaRefresh,
            onConfirm = { token, captchaCode -> doSend(token, captchaCode) },
            onDismiss = { if (!sending) showCaptcha = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!verifying) onDismiss() },
        title = {
            Text(text = "验证邮箱", color = MiuixTheme.colorScheme.onBackground)
        },
        text = {
            Column {
                Text(
                    text = "验证后可用于找回密码与接收通知",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "邮箱",
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        AppTextField(value = code, onValueChange = { code = it }, label = "邮箱验证码")
                    }
                    Spacer(Modifier.width(10.dp))
                    SmallButton(
                        text = when {
                            sending -> "发送中"
                            countdown > 0 -> "${countdown}s"
                            else -> "获取验证码"
                        },
                        enabled = !sending && countdown == 0 && email.isNotBlank(),
                    ) {
                        captchaError = ""
                        showCaptcha = true
                    }
                }
            }
        },
        confirmButton = {
            M3TextButton(
                onClick = { doVerify() },
                enabled = !verifying && email.isNotBlank() && code.isNotBlank(),
            ) {
                Text(
                    text = if (verifying) "提交中…" else "提交验证",
                    color = if (verifying) {
                        MiuixTheme.colorScheme.onBackgroundVariant
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            M3TextButton(onClick = onDismiss) {
                Text(text = "取消", color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        },
    )
}

// ===================== 复用小组件 =====================

/** 页面头部: 方形图标 + 标题 + 副标题 (无渐变, 与关于页风格一致) */
@Composable
private fun AccountHeader(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 输入框
 * 用 material3 OutlinedTextField 保证 API 稳定, 但文字/标签色显式取 Miuix 主题色 —
 * 否则 MaterialTheme 默认浅色配色在深色主题下文字不可读。
 */
@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        },
        textStyle = TextStyle(
            fontSize = 15.sp,
            color = if (enabled) {
                MiuixTheme.colorScheme.onBackground
            } else {
                MiuixTheme.colorScheme.onBackgroundVariant
            },
        ),
        singleLine = singleLine,
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (minHeight != null) Modifier.height(minHeight) else Modifier),
    )
}

/** 主按钮 (Miuix 卡片风格, 和设置页卡片视觉一致) */
@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        colors = CardDefaults.defaultColors(
            color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabled) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/** 小按钮 (获取验证码 / 倒计时) */
@Composable
private fun SmallButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = { if (enabled) onClick() },
        cornerRadius = 12.dp,
        colors = CardDefaults.defaultColors(
            color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabled) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant,
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

/** 圆形头像 (coil 加载, 空/未登录显示首字占位) */
@Composable
private fun AvatarCircle(url: String, name: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.take(1).ifBlank { "铃" },
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** 资料行 (左: 标题 + 副值/小标签; 右: 可选「编辑」+ 箭头) */
@Composable
private fun ProfileRow(
    title: String,
    value: String? = null,
    badge: String? = null,
    badgeHighlight: Boolean = false,
    editable: Boolean = false,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickAction: () -> Unit = onClick ?: {}
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { clickAction() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (danger) DangerRed else MiuixTheme.colorScheme.onBackground,
            )
            if (value != null) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (badgeHighlight) {
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHigh
                                    },
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                color = if (badgeHighlight) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onBackgroundVariant
                                },
                            )
                        }
                    }
                }
            }
        }
        if (editable) {
            Text(
                text = "编辑",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(2.dp))
        }
        if (onClick != null || editable) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 资料行分隔线 (卡片内, 左右留边) */
@Composable
private fun ProfileDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
    )
}
