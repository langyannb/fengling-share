# WebSocket 消息流 + 全 App 动效实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 客户端改接现有 `/ws` WebSocket，并把全 App 动效统一到 FlClash 质感的 MotionTokens；服务端零改动。

**Architecture:** `ApiClient` 新增 `streamMessagesWs`，用 OkHttp WebSocket 对接生产已有端点；`MessageService` 保留唯一连接持有者和补拉/通知职责，只替换传输层；动效按 MotionTokens → 页面转场/共享元素 → P0/P1/P2 逐页迁移。

**Tech Stack:** Kotlin 2.4、Compose、miuix-nav 0.9.4、OkHttp 4.12、Go 1.27.4（本机已装，仅用于协议探针/未来服务端工具）、GitHub Actions。

**Spec:** `docs/superpowers/specs/2026-11-27-websocket-and-motion-design.md`

## Global Constraints

- minSdk 29 / target 34 / compile 37；不新增第三方依赖。
- 服务端 `/ws` 零改动；`api.php?action=stream` 保留至少一个版本。
- WS 地址只从 `ServerConfig` 推导，不新增明文 host/port。
- 不支持 WebView/视频/`AndroidView` 的共享元素或缩放转场；`enableCornerClip = false` 不动。
- 动效参数只来自 `MotionTokens`；关闭对应开关时直接给终态，不创建动画。
- 本机无 Android SDK/JDK 21，不能在本地 `assemble`；编译验证走 GitHub Actions。CI 需要 PAT，由用户通过 secret 卡提供。
- 真机测试机：红米 M2104K10AC，`adb`/`wadb` 无线调试。每轮真机结束先切回 Chrome。

## Review Focus

- **WebSocket 半开连接**：手机休眠/切网后 socket 可能不报错。`ping` 文本帧必须刷新 `lastStreamActivityAt`，否则 180 秒看门狗会误判并重连。
- **游标倒退**：`hello.cursor` 不得覆盖本地 `StreamCursor`（会跳过断线消息）；只在本地游标为 0 时用作基线。
- **401 循环**：token 失效后不能 1 秒一次重连；退避拉到 30 秒，且不清本地登录态。
- **WebView 白屏**：共享元素/缩放转场碰到 `AndroidView` 会白屏；WebView 页只允许 alpha + 位移。
- **低端机动效**：API 29 设备上共享元素和玻璃同时开启可能掉帧；动效关闭时必须真正不跑动画。

---

### Task 1: WS URL 与协议解析

**Files:**
- Modify: `app/src/main/kotlin/com/fengling/share/data/ServerConfig.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/data/ApiClient.kt:100-250`

**Interfaces:**
- Produces: `ServerConfig.WS_URL: String`
- Produces: `ApiClient.parsePmStreamEvent(JSONObject): StreamEvent.Pm?`
- Produces: `ApiClient.parseGroupStreamEvent(JSONObject): StreamEvent.Group?`
- Produces: `ApiClient.streamMessagesWs(pmId: Int, groupId: Int, onHello: (Int, Int) -> Unit, onActivity: () -> Unit, onEvent: (StreamEvent) -> Unit): Result<Unit>`

- [ ] **Step 1: 加 `WS_URL` 推导**

在 `ServerConfig` 内加：

```kotlin
/** ws://host:9845/ws；由 BASE_URL 推导，不新增明文地址 */
val WS_URL: String by lazy {
    BASE_URL.removeSuffix("api.php").replaceFirst("http", "ws") + "ws"
}
```

检查 `BASE_URL` 形如 `http://43.248.187.94:9845/api.php`；`WS_URL` 必须是 `ws://43.248.187.94:9845/ws`。

- [ ] **Step 2: 解析函数改成接受 `JSONObject`**

保留现有 `String` 入口的行为：先把 `parsePmStreamEvent(payload: String)` 改为先 `JSONObject(payload)` 再调用新重载；新增 `parsePmStreamEvent(j: JSONObject)`。`parseGroupStreamEvent` 同样处理。字段名不改。

- [ ] **Step 3: 实现 `streamMessagesWs`**

在 `ApiClient` 内实现 `suspendCancellableCoroutine` 版本：

- URL：`WS_URL + "?pm_id=$pmId&group_id=$groupId"`。
- `Request.Builder().url(...).header("Authorization", "Bearer " + UserStore.token).build()`。
- 用 `streamClient.newBuilder().pingInterval(15, TimeUnit.SECONDS).build()`。
- `WebSocketListener`：
  - `onMessage(text)`：先 `onActivity()`；解析 `type`：
    - `hello` → 读 `data.cursor.pm_id` / `data.cursor.group_id`，回调 `onHello`；
    - `pm`/`group` → 复用解析函数，`onEvent`；
    - `ping` → 只 `onActivity`；
    - `error` → 以 `ApiException(msg, code)` 结束。
  - `onClosed`/`onFailure` → 以失败结束。
  - `invokeOnCancellation` → `webSocket.cancel()`。
- 返回 `Result<Unit>`：正常关闭为 success，异常为 failure。

- [ ] **Step 4: 静态检查**

Run: `git diff --check`，Expected: 无输出。
Run: `grep -n "43.248" app/src/main/kotlin/com/fengling/share/data/ServerConfig.kt`，Expected: 只允许注释里的既有地址，不允许新增明文。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/fengling/share/data/ServerConfig.kt app/src/main/kotlin/com/fengling/share/data/ApiClient.kt
git commit -m "feat: 新增 WebSocket 消息流客户端"
```

---

### Task 2: MessageService 切换与退避

**Files:**
- Modify: `app/src/main/kotlin/com/fengling/share/service/MessageService.kt:245-310`
- Modify: `app/src/main/kotlin/com/fengling/share/data/MessageStream.kt:80-95`

**Interfaces:**
- Consumes: `ApiClient.streamMessagesWs(...)`（Task 1）
- Produces: WS 主循环使用指数退避；`isStreamStale(180s)` 在 WS 下仍有效。

- [ ] **Step 1: 替换主循环调用**

把 `ApiClient.streamMessages(...)` 换成 `streamMessagesWs(...)`：

```kotlin
val result = runCatching {
    ApiClient.streamMessagesWs(
        pmId = StreamCursor.lastPmId,
        groupId = StreamCursor.lastGroupId,
        onHello = { serverPm, serverGroup ->
            if (StreamCursor.lastPmId <= 0) StreamCursor.markPm(serverPm)
            if (StreamCursor.lastGroupId <= 0) StreamCursor.markGroup(serverGroup)
        },
        onActivity = { lastStreamActivityAt = SystemClock.elapsedRealtime() },
    ) { event -> onEvent(event) }
}
```

不要用 `hello.cursor` 覆盖非零本地游标。

- [ ] **Step 2: 指数退避**

在 `runStream` 内维护 `var attempt = 0`：

- 连接成功（`connected == true`）→ `attempt = 0`。
- 失败 → `attempt = min(attempt + 1, 5)`。
- 等待：`min(30_000L, 1_000L shl attempt) + Random.nextLong(0, 500)`。
- `error 401` 通过 `ApiException.code == 401` 判断，直接把 `attempt` 拉到 5。

保留 `firstConnect`/`lastConnectFailed` 的 `MessageCatchUp` 逻辑和 `StreamEvent.Reconnected` 广播。

- [ ] **Step 3: 心跳常量对齐**

`MessageStream.RECONNECT_DELAY_MS` 不再用于固定等待；保留常量给旧 SSE 路径。新增私有常量：

```kotlin
private const val MAX_BACKOFF_MS = 30_000L
private const val BASE_BACKOFF_MS = 1_000L
```

- [ ] **Step 4: 静态检查**

Run: `git diff --check`。
Run: `grep -n "streamMessages(" app/src/main/kotlin/com/fengling/share/service/MessageService.kt`，Expected: 无调用（旧方法保留在 ApiClient 即可）。

- [ ] **Step 5: 推送编译**

```bash
git add app/src/main/kotlin/com/fengling/share/service/MessageService.kt app/src/main/kotlin/com/fengling/share/data/MessageStream.kt
git commit -m "feat: 消息服务切换到 WebSocket 并加指数退避"
git push -u origin HEAD
```

Expected: Actions `conclusion == success`。没有 PAT 时停下，让用户提供。

---

### Task 3: MotionTokens 与按压基础设施

**Files:**
- Create: `app/src/main/kotlin/com/fengling/share/ui/motion/MotionTokens.kt`
- Create: `app/src/main/kotlin/com/fengling/share/ui/motion/PressableScale.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/MainScreen.kt:150-185`

**Interfaces:**
- Produces: `MotionTokens.PageEnter`, `PageExit`, `ListStepMs`, `SpringSnappy`, `SpringSheet` 等常量。
- Produces: `Modifier.pressableScale(enabled: Boolean = true): Modifier`。
- Consumes: 现有 `LocalAnimPageSwitch` / `LocalAnimSheetSpring` / `LocalAnimListEnter`。

- [ ] **Step 1: 建 MotionTokens**

```kotlin
object MotionTokens {
    const val PageEnterMs = 320
    const val PageExitMs = 220
    const val ListEnterMs = 300
    const val ListStepMs = 20
    const val ThemeMs = 300
    val SpringSnappy = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
    val SpringSheet = spring<Float>(dampingRatio = 0.82f, stiffness = 560f)
}
```

- [ ] **Step 2: 建 `pressableScale`**

用 `MutableInteractionSource` 的 `collectIsPressedAsState()` 驱动 `graphicsLayer { scaleX = 0.98f; scaleY = 0.98f }`，用 `MotionTokens.SpringSnappy`；`enabled=false` 时返回 `this`。不要在这一步接 `InteractiveHighlight`（避免和 clickable 抢手势）。

- [ ] **Step 3: 页面转场接开关**

`MainScreen` 的 `transition` 改为：

```kotlin
val animPage = LocalAnimPageSwitch.current
val transition = when {
    !animPage -> NavTransitions.None
    predictiveBack -> NavTransitions.MiuixDefault
    else -> NavTransitions.None
}
```

保持 `enableCornerClip = false`。

- [ ] **Step 4: 静态检查 + 提交**

Run: `git diff --check`。

```bash
git add app/src/main/kotlin/com/fengling/share/ui/motion app/src/main/kotlin/com/fengling/share/ui/main/MainScreen.kt
git commit -m "feat: 动效 token 与按压缩放基础设施"
```

---

### Task 4: 页面转场与共享元素 spike

**Files:**
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/MainScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/book/detail/DetailScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/social/SocialScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/social/GroupInfoScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/pm/PmScreen.kt`

**Interfaces:**
- Consumes: `MotionTokens`、`AppNavigator`。
- Produces: 首页卡片→详情的共享元素；群/私聊头像→资料页的共享元素（若 spike 通过）。

- [ ] **Step 1: spike 共享元素**

在 `MainScreen` 的 `NavDisplay` 外层包 `SharedTransitionLayout`，给首页第一张卡片封面和详情页封面设置相同 `sharedBounds` key，运行真机：

- 进入详情页动画正常、返回手势正常、WebView 返回不白屏 → 继续；
- 白屏、崩溃、返回手势异常 → 移除共享元素，只保留 Task 3 的转场，记录结论到计划末尾。

- [ ] **Step 2: 落地首页/详情**

只在 spike 通过时保留；共享元素只绑封面和标题，不绑整张卡片。

- [ ] **Step 3: 落地群/私聊头像**

在群列表项和群资料头像、私聊列表项和私聊页头像上加共享 key；WebView/视频不加。

- [ ] **Step 4: 真机验证**

Run: `adb devices`，Expected: 设备在线。
手测：首页→详情→返回、群列表→群资料→返回、私聊列表→私聊→返回、WebView→返回。
Expected: 无白屏、无 crash、返回手势跟手。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/fengling/share/ui
git commit -m "feat: 页面共享元素与转场"
```

---

### Task 5: 消息与列表动效（P0）

**Files:**
- Modify: `app/src/main/kotlin/com/fengling/share/ui/social/SocialScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/pm/PmChatScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/pm/PmScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/my/MessagesScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/explore/ExploreScreen.kt`

**Interfaces:**
- Consumes: `Modifier.enterUp`、`MotionTokens`、`Modifier.pressableScale`。
- Produces: 列表增删/移动用 `animateItem()`；消息气泡 spring 出现。

- [ ] **Step 1: LazyColumn 加 key + animateItem**

每个主要 `LazyColumn` 的 `items(...)` 加稳定 `key`，item 根 `Modifier.animateItem()`；首屏项保留 `enterUp`，参数改用 `MotionTokens.ListStepMs`。

- [ ] **Step 2: 消息气泡**

`PmChatScreen`/`SocialScreen` 的新消息气泡外层用 `AnimatedVisibility` + `scaleIn(initialScale = 0.92f, animationSpec = MotionTokens.SpringSnappy)`；发送中状态用 `AnimatedContent`。

- [ ] **Step 3: 自动滚动**

新消息到达时 `animateScrollToItem(lastIndex)`；用户手动上滑超过一屏时不强制。

- [ ] **Step 4: 真机验证**

手测：群聊连续收 5 条、私聊切换会话、消息列表进入/返回；检查无闪烁、无重复动画。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/fengling/share/ui
git commit -m "feat: 消息与列表交互动效"
```

---

### Task 6: P0 其余页面与全局按压

**Files:**
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/my/MyScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/my/ThemeSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/user/UserProfileScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/social/GroupInfoScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/components/chat/ChatActionSheet.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/components/chat/NoticeEditorSheet.kt`

**Interfaces:**
- Consumes: `Modifier.pressableScale`、`MotionTokens.SpringSheet`、`LocalAnimSheetSpring`。

- [ ] **Step 1: 替换关键裸 clickable**

P0 页面的卡片/设置行/头像入口加 `pressableScale()`；不替换带自定义手势的组件。

- [ ] **Step 2: Sheet 统一参数**

`ChatActionSheet`/`NoticeEditorSheet` 的进场 spring 改用 `MotionTokens.SpringSheet`，退出 180ms；`LocalAnimSheetSpring=false` 时直接终态。

- [ ] **Step 3: 主题切换过渡**

`AppTheme` 的色板切换加 300ms `animateColorAsState`（`MotionTokens.ThemeMs`）；只在主题值变化时触发。

- [ ] **Step 4: 真机验证 + 提交**

手测：我的页、主题页、用户主页、群资料、长按菜单、公告编辑；开关关闭后无动画。

```bash
git add app/src/main/kotlin/com/fengling/share/ui
git commit -m "feat: P0 页面按压与弹层动效"
```

---

### Task 7: P1/P2 页面迁移

**Files:**
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/my/AccountScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/download/DownloadScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/lottery/LotteryScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/my/NotifySettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/browser/WebViewScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/update/UpdateScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/main/my/ContributorsScreen.kt`
- Modify: `app/src/main/kotlin/com/fengling/share/ui/social/GroupMemberListScreen.kt`

**Interfaces:**
- Consumes: 同 Task 5/6；WebView 只允许 alpha + 位移。

- [ ] **Step 1: 列表/按压迁移**

P1/P2 页面按 Task 5/6 的方式加 `animateItem()`/`enterUp`/`pressableScale`；WebView 页不加共享元素、不加缩放，只保留转场 alpha。

- [ ] **Step 2: 空态与加载**

空态/加载切换用 `AnimatedContent` 或 `EnterUp`，不新建 skeleton。

- [ ] **Step 3: 真机验证 + 提交**

手测：下载、抽奖、通知设置、更新页、贡献者、成员列表、WebView 返回。

```bash
git add app/src/main/kotlin/com/fengling/share/ui
git commit -m "feat: P1/P2 页面动效迁移"
```

---

### Task 8: 性能、回归与文档

**Files:**
- Modify: `docs/superpowers/specs/2026-11-27-websocket-and-motion-design.md`（记录 spike 结论）
- Modify: `docs/roadmap.md`（若已合并规则分支）

**Interfaces:**
- 无新接口。

- [ ] **Step 1: 低端机帧率**

Run: `adb shell dumpsys gfxinfo com.fengling.share framestats`，Expected: 关键页面无持续超过 16ms 的帧；记录最差页面。

- [ ] **Step 2: 动效关闭**

在设置里关闭列表/页面/弹层动画，手测 P0 页面：无动画、无残留 `Animatable`、无 crash。

- [ ] **Step 3: WebSocket 回归**

手测：后台 30 分钟、断网 2 分钟恢复、锁屏/划掉、连续 10 条消息；检查通知不重复、游标不倒退、看门狗不误判。

- [ ] **Step 4: 记录结论并提交**

把共享元素 spike 结论、group 帧核对结果、最差页面写回设计文档末尾；提交。

```bash
git add docs
git commit -m "docs: 记录 WebSocket 与动效验证结论"
```

---

## 计划自检

- 服务端零改动：Task 1/2 只改客户端；设计中的 `/ws` 已实测。
- 规格覆盖：WS 连接、hello、pm/group、ping、401、退避、看门狗、动效 token、转场、共享元素、P0/P1/P2、性能、回滚均有任务。
- 类型一致：`streamMessagesWs` 的 `onHello`/`onActivity`/`onEvent` 在 Task 1/2 一致；`MotionTokens` 在 Task 3-7 一致。
- 本机限制：不能本地编译；Task 2 起每次 push 走 CI，真机验证在 Task 4-8。
