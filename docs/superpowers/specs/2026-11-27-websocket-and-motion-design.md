# WebSocket 消息流 + 全 App 动效设计

- 日期：2026-11-27
- 分支：`feat/ws-and-motion`
- 状态：待批准

## 1. 意图

把消息流从「PHP SSE、25 秒强制重连、每条连接占一个 PHP worker」换成常驻 WebSocket。生产环境的 `/ws` 已经是一个完整实现，客户端直接对接即可；同时按 FlClash 的 Apple 交互质感重做全 App 动效。

## 2. 现状（已实测）

- `https://…` 不适用，站点当前是明文：`43.248.187.94:9845/ws` 由 nginx 反代到 `127.0.0.1:9100` 的 **goapi（Go 服务）**。
- 无 token 握手直接返回 `{"type":"error","code":401,"msg":"登录已失效"}`。
- 用临时探针账号实测到的协议：
  - 连接：`ws://43.248.187.94:9845/ws?pm_id=0&group_id=0`，`Authorization: Bearer <token>` 有效。
  - 首帧：`{"type":"hello","data":{"user_id":261,"server_time":1795709549,"heartbeat_sec":15,"cursor":{"pm_id":536,"group_id":808}}}`。
  - 私聊：`{"type":"pm","data":{"id":537,"conv_id":95,"from_user":262,"nickname":"probe","username":"…","content":"…","image":"","msg_type":"","video":"","video_w":0,"video_h":0,"video_duration":0,"video_size":0,"created_at":"…"}}`，字段与现有 SSE `pm` 完全一致。
  - 心跳：`{"type":"ping","data":{"t":…}}`，`heartbeat_sec` 为 15。
- 客户端 `ApiClient.streamMessages` 用 OkHttp 读 SSE；`MessageService` 是唯一连接持有者；`MessageStream` 是事件总线；`StreamCursor`、`MessageCatchUp`、`MessageNotifier` 已覆盖断线补拉和通知去重。
- 动效已有 `EnterUp`、`InteractiveHighlight`、`DampedDragAnimation`、多个 spring sheet，但参数分散、页面覆盖不全。
- 本机有 OkHttp 4.12、无 Android SDK/JDK 21；构建走 GitHub Actions。`ServerConfig` 解密后指向 `43.248.187.94:9845`。

## 3. 目标 / 非目标

**目标**

- 客户端与现有 `/ws` 建立常驻 WebSocket；心跳、断线指数退避、游标补发、后台通知行为与现在一致。
- 服务端 **零改动**（除非实测发现群消息帧或游标不兼容，再补最小改动）。
- 动效覆盖消息相关页面和全部 P0 页面；共享元素用于列表→详情；参数统一到一套 token。
- 可回滚：PHP SSE 保留至少一个版本；旧客户端不受影响。

**非目标**

- 不新建第二套 Go 服务、不加 systemd/nginx 单元（现有 `/ws` 已够用）。
- 不做已读回执、输入中、在线状态、多端同步/踢下线。
- 不改 PHP 的 action 协议或消息表结构。
- 不给 WebView、视频、SurfaceView 做共享元素或缩放转场。
- 不重写 miuix-nav、不引入新 UI 库。

**失败条件（anti-goals）**

- 为了动效牺牲 WebView 返回稳定性，或让低端机掉到 60fps 以下。
- WebSocket 上线后旧客户端收不到消息。
- 消息重复弹通知、游标倒退、后台服务被 ROM 杀死后无法恢复。

## 4. 方案与取舍（已选）

| 决策 | 选择 | 放弃的方案与原因 |
|---|---|---|
| 服务端端点 | **直接复用现有 `/ws`（goapi）**，服务端零改动 | 新建 Go 服务/`/msgws`：现有端点已经实现 hello/pm/ping/cursor，重复部署没有收益 |
| 推送方式 | 现有服务端的实现（已在线） | 自己写轮询/发布：没有必要 |
| 客户端 | OkHttp WebSocket，保留 `MessageService`/`MessageStream`/游标/补拉 | 引入新 WS 库没有必要；改页面订阅层风险大 |
| 动效 | **全 App 深改**，分阶段、可关闭 | 只改聊天页达不到「像 FlClash」的整体质感 |

## 5. 现有 `/ws` 协议（实测）

### 5.1 连接

- URL：`ws://<host>:9845/ws?pm_id=<id>&group_id=<id>`。
- 鉴权：`Authorization: Bearer <token>`；`pm_id`/`group_id` 为 0 时，服务端从当前 `MAX(id)` 开始，不回放历史。
- 首帧 `hello` 的 `cursor` 是服务端当前基线，可用于校正客户端游标；`heartbeat_sec=15`。
- 服务端发 `{"type":"ping","data":{"t":…}}`，客户端收到任意帧都算连接活动。

### 5.2 事件

- `pm`：字段与 SSE `pm` 一致（已实测）。
- `group`：按 SSE `group` 字段实现（`id`/`group_id`/`group_name`/`user_id`/`nickname`/`content`/`image`/`video*`/`at_me`/`at_all`/`muted`/`msg_type`/`created_at`）；真机联调时确认，解析保持宽松（缺字段用默认值）。
- `error`：`{"type":"error","code":401,"msg":"登录已失效"}`（已实测）。
- `ping`：客户端忽略内容，只刷新活动时间；不投递给 `MessageStream`。

### 5.3 兼容与回滚

- `api.php?action=stream` 原样保留，旧 APK 继续可用。
- 新客户端只走 WS，不做运行时双栈；回滚发旧版本 APK。
- `/ws` 在 goapi 内，服务端升级/重启时要回归一次；客户端重连逻辑覆盖短暂断连。

## 6. 客户端

### 6.1 URL 与配置

`ServerConfig` 新增 `WS_URL`，由 `BASE_URL` 推导：`http://host:9845/api.php` → `ws://host:9845/ws`。不新增明文地址；将来上 HTTPS 时 `http` → `ws` 的替换自然变成 `wss`。

### 6.2 `ApiClient.streamMessagesWs`

```kotlin
suspend fun streamMessagesWs(
    pmId: Int,
    groupId: Int,
    onHello: (serverPmId: Int, serverGroupId: Int) -> Unit = { _, _ -> },
    onActivity: () -> Unit = {},
    onEvent: (StreamEvent) -> Unit,
): Result<Unit>
```

- `Request` 带 `Authorization: Bearer <token>`，query 只带 `pm_id`/`group_id`。
- 专用 `streamClient` 加 `pingInterval(15, SECONDS)`（与服务端 `heartbeat_sec` 对齐）。
- `suspendCancellableCoroutine` 包住 `WebSocketListener`。
- `onMessage`：
  - 任意帧先调用 `onActivity()`；
  - `hello` → 解析 `cursor.pm_id`/`cursor.group_id`，交给 `onHello` 校正基线；
  - `pm`/`group` → 复用现有解析逻辑，投递给 `MessageStream`；
  - `ping` → 仅活动信号；
  - `error` → 以失败结束，附带 code/msg。
- `onClosed`/`onFailure` 以失败结束；协程取消时 `webSocket.cancel()`，不留悬挂连接。
- 解析函数从 `String` 改成接受 `JSONObject`，SSE 与 WS 共用。

### 6.3 重连策略

`MessageService.runStream` 保留，替换调用：

- 首次连接失败：1 秒后重试。
- 连续失败：`min(30s, 1s * 2^n)` + 0–500ms jitter。
- 连接成功（收到 `hello` 或活过 `CONNECTED_MIN_MS`）重置退避。
- 收到 `error 401` 按失败处理并把退避拉到 30 秒；不自动清本地登录态。用户重新登录后 token 更新，下一次连接自然恢复。
- 保留 `firstConnect`/`lastConnectFailed` 的补拉逻辑、`StreamEvent.Reconnected` 广播、`MessageCatchUp` 兜底。
- `onActivity` 接回 `lastStreamActivityAt`，让现有的 `isStreamStale(180s)` 看门狗在 WS 下照常工作。
- 不做网络状态监听；ROM 的网络切换由重连覆盖。

### 6.4 保留的东西

`MessageService` 的服务/通知/看门狗、`MessageStream` 事件总线、`StreamCursor`、`MessageCatchUp`、`MessageNotifier` 全部不动。旧 `streamMessages` 保留但不再调用，方便对照和回滚。

## 7. 动效（全 App）

### 7.1 原则

- 只动 `graphicsLayer` / alpha / 裁切，不在 composition 里做 IO。
- 跟随系统「移除动画」：`LocalMotionDurationScale` 为 0 时直接给终态。
- 设置里保留「列表进场动画」开关，并新增「动效强度：完整 / 精简 / 关闭」。
- 页面转场不阻塞数据加载：先进入页面显示 spinner，数据就绪后内容淡入。

### 7.2 MotionTokens

新增 `ui/motion/MotionTokens.kt`，统一参数（参考 FlClash 的 drag-back 与 toolbar 弹簧）：

| 场景 | 参数 |
|---|---|
| 页面进入 | 320ms, `FastOutSlowInEasing`, alpha 0→1, scale 0.98→1, x +24dp→0 |
| 页面退出 | 220ms, `FastOutLinearInEasing`, alpha 1→0, x 0→-8dp |
| 列表首屏 | `enterUp` 300ms, 20ms 交错, 最多 12 项 |
| 列表增删 | `animateItem`, 300ms |
| 消息气泡 | `spring(dampingRatio=0.72, stiffness=420)`, scale 0.92→1 |
| 按压 | `spring(0.55, 600)`, scale 1→0.98；光斑 300ms |
| 底部栏图标 | `spring(0.65, 500)`, 选中 scale 1→1.06→1 |
| Sheet 进入 | `spring(0.82, 560)`；退出 180ms `tween` |
| 主题切换 | 300ms `tween` 色板过渡 |
| 共享元素 | 320ms `FastOutSlowInEasing` |

### 7.3 页面转场与共享元素

- 先验证 miuix-nav 0.9.4 的 `NavTransition` 是否允许组合 alpha/scale；允许就用自定义 fade-through + 视差，不允许就保留手势深度，只在内容层做进入动画。
- `SharedTransitionLayout` 包住 `NavDisplay`；只对以下组合使用 `sharedBounds`：
  - 首页/分类卡片封面 + 标题 → 详情页；
  - 群列表项头像 → 群资料；
  - 私聊列表项头像 → 私聊。
- WebView、视频、`AndroidView` 一律不参与共享元素；WebView 页只做 alpha + 位移，且保持 `enableCornerClip = false`。
- 共享元素做 spike：如果和 NavDisplay 的转场/预测返回冲突，降级为同页面内共享或不做，不为它牺牲返回手势。

### 7.4 列表与消息

- 所有主要 `LazyColumn` 加 `Modifier.animateItem()`，保证增删/移动有位置过渡。
- 保留 `enterUp` 首屏交错，参数收进 `MotionTokens`。
- 聊天列表：新消息 `animateItem` + 气泡 `scaleIn`；发送状态用 `AnimatedContent`；新消息到达时 `animateScrollToItem`（用户已手动上滑时不强制）。
- 群/私聊列表的未读徽标用 `animateContentSize` + 数字切换 `AnimatedContent`。

### 7.5 按压、Sheet、底栏、主题

- 统一 `Modifier.pressable`：`clickable` + `InteractiveHighlight` + scale 0.98；替换 P0 页面的裸 `clickable`。
- Sheet/Dialog：进场 spring、退出 180ms、scrim 200ms 淡入；**不模糊背景**（FlClash 的 modal 只 dim）。
- 底栏：保留 `DampedDragAnimation`，补选中图标 fill/scale spring；玻璃背景不动。
- 主题：`AppTheme` 的色板用 `animateColorAsState` 300ms；切换主题不重建整个 Activity。
- 页面加载：`LoadingView` → 内容 `EnterUp` 淡入；不引入 skeleton。

### 7.6 逐页清单

| 优先级 | 页面 | 动效 |
|---|---|---|
| P0 | 首页、分类、详情、群组、私聊、我的、消息、主题设置 | 转场、列表 animateItem、按压、共享元素、加载淡入 |
| P1 | 账号、用户主页、群资料、下载、抽奖、通知设置 | 转场、列表、按压、空态 |
| P2 | 浏览器、更新页、贡献者、成员列表 | 仅转场 alpha + 按压；WebView 不做共享元素 |

### 7.7 性能门槛

- 目标 60fps；在华为 SEA-AL00（API 29）这类低端机上不出现持续掉帧。
- 不新增每帧整页捕获；玻璃/模糊仍走现有局部 backdrop。
- 列表项用稳定 key；动画期间不重建整页；共享元素只绑图片/标题，不绑整卡。
- 动效关闭时，所有新增动画走终态，不创建不必要的 `Animatable`。

## 8. 分阶段实施

1. **客户端 WS**：`streamMessagesWs` + `MessageService` 切换；保留 SSE；真机验证。
2. **动效基础设施**：MotionTokens、pressable、页面转场 spike、共享元素 spike。
3. **P0 页面**：消息相关 + 首页/分类/详情/群组/我的/主题。
4. **P1/P2 页面**：其余页面迁移。
5. **性能与回归**：低端机、后台消息、断网重连、WebView、动效开关。

每阶段单独提交、单独验证；阶段 1 可以独立发版。

## 9. 验收

- 客户端：Actions `assembleRelease` 成功；真机冷启动无 crash。
- 消息：后台 30 分钟、断网 2 分钟恢复、连续多条消息、锁屏/划掉；通知不重复、游标不倒退。
- WS：`hello` 基线正确；`pm` 到达即时；`ping` 不让看门狗 180s 误判；401 后退避。
- 动效：P0 页面 60fps；返回手势正常；WebView 返回不白屏；动效关闭可用。
- 回滚：旧 APK 仍可连 PHP SSE；服务端 `/ws` 不受客户端发布影响。

## 10. 风险

| 风险 | 处理 |
|---|---|
| `/ws` 无源码，group 帧未实测 | pm 已实测；group 用宽松解析，真机联调确认；不满足再补最小解析 |
| 服务端升级/重启导致 `/ws` 短暂不可用 | 客户端指数退避 + `MessageCatchUp` 兜底 |
| 明文 `ws://` | 与站点现状一致；上 HTTPS 后由 `BASE_URL` 推导自动切 `wss://` |
| 共享元素与 NavDisplay 冲突 | spike 先验证；冲突就降级，不牺牲返回手势 |
| 全 App 动效回归范围大 | 分阶段 + 动效强度开关 + 逐页验收 |
| 旧客户端兼容 | 保留 PHP SSE 至少一个版本；回滚发旧 APK |

## 11. 留给实现者的决定

- P0/P1/P2 内页面的先后顺序。
- 共享元素 spike 的结论（做/降级）。
- miuix-nav 是否支持自定义 fade-through；不支持时的替代参数。
- group 帧的最终字段核对方式（真机抓包/服务端日志）。
