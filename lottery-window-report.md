# 抽奖「自定义时段 + 一堆设置」服务端实现与线上实测报告

**结论先行**：契约 A 节 + B 节已全部实现并部署上线，线上 `api.php` 与本地**字节级一致**（229535 字节 / md5 `818e9fb1cde1ffa646c0d8f1efd0e8c0`）；线上实测 **143 条用例全 PASS / 0 FAIL**（含全部中文错误文案原文、加权分布数字、鉴权 401/403）；测试数据**零残留**，计数与配置 100% 回到基线；**46 个真实用户数据一个都没动，未调用 `admin_lottery_activity_reset`**。

## 1. 分支 / 提交 / CI

| 项 | 值 |
|---|---|
| 仓库 / 分支 | `fengling-server` / `feature/lottery-window`（自 `feature/group-members` head `444f397` 切出） |
| 功能提交 | `a110151` feat(lottery): 抽奖自定义开放时段/每日重置时间/每周与全站上限/冷却/有效期/奖项权重加权抽奖 + 展示与通知开关、自定义文案 |
| 加固提交 | `42ceeb8` fix(tools): 抽奖迁移脚本加匿名执行保护 |
| CI run（功能） | `37226556949`「服务端检查」**success**（headSha a110151） |
| CI run（加固） | `37226608265`「服务端检查」**success**（headSha 42ceeb8） |
| 推送 | 仅 `git push origin feature/lottery-window`，**未推 main** |

改动文件（严格按授权范围）：`api.php`（+509 / -37）、新增 `tools/install_lottery_window.php`；**未触碰 `admin/`，未切换他人分支**。

## 2. 迁移脚本幂等性（服务器实跑 4 次）

```
# 第 1 次
[列] lottery_prizes.weight OK
[数据] weight 为 NULL 的行改成 100: 0 行
[配置] settings.lottery_config 补齐新键: success_text, empty_text, week_limit, daily_reset_time,
       cooldown_seconds, daily_total_limit, windows_enabled, windows, start_date, end_date,
       show_prizes, show_stock, notify_winner
# 第 2 次
[列] lottery_prizes.weight 已存在
[数据] weight 为 NULL 的行改成 100: 0 行
[配置] settings.lottery_config 新键已齐全, 跳过
完成, 共执行 0 项        ← 幂等证明
# 第 3、4 次（加固版）输出与第 2 次完全相同: 完成, 共执行 0 项
```

## 3. 本地 / 线上文件核对

| 文件 | 本地 | 线上 | 一致 |
|---|---|---|---|
| `api.php` | 229535 字节 / md5 `818e9fb1cde1ffa646c0d8f1efd0e8c0` | 同左 | ✅ |
| `tools/install_lottery_window.php` | 3888 字节 / md5 `e7b89c87d64e62e0832ffa8d7e4ef123` | 同左 | ✅ |

- 部署流程：`php -l` → 备份 → 覆盖 → `chown www:www` + `chmod 644` → `/etc/init.d/php-fpm-85 reload` → 复核 md5。
- 线上备份：`/www/wwwroot/flfxk/api.php.bak-lotwin-20261005_025616`（206860 字节，md5 `a4241ffc085e9f840580d0ff54f82903`，= 改动前线上）、`api.php.bak-lotwin-20261005_025658`（229044 字节，加 `lottery_flag` 之前的版本）。
  回滚命令：`cp api.php.bak-lotwin-20261005_025616 api.php && chown www:www api.php && /etc/init.d/php-fpm-85 reload`

## 4. 线上实测用例（143 PASS / 0 FAIL）

| # | 用例 | 结果 |
|---|---|---|
| 1 | 自定义开放时段（未开放/时段内/跨天/指定星期/windows_enabled=0） | **19 PASS / 0 FAIL** |
| 2 | 每日重置 04:00 的「今日」边界 + SQL 边界对照 | **3 PASS**（SQL 边界命中 3 条 = 昨 04:00~今 04:00 的 10:00 / 23:30 / 00:30） |
| 3 | 每周次数 week_limit | **3 PASS** |
| 4 | 冷却 cooldown_seconds | **3 PASS** |
| 5 | 全站每日发放上限 daily_total_limit | **5 PASS** |
| 6 | 活动有效期 start_date / end_date | **7 PASS** |
| 7 | 加权抽奖分布 | **5 PASS** |
| 8 | 后台配置校验文案 + 非法调用不写库 | **64 PASS** |
| 9 | 展示开关 / 通知开关 / 自定义文案 | **13 PASS** |
| 10 | 鉴权 401/403 + preview 扁平结构 | **21 PASS** |

### 4.1 关键 PASS 原文（节选）

```
PASS  1.1 时段未开放 reason            outside_window
PASS  1.2 时段文案 text                每天 19:30-20:00
PASS  1.3 reason_text                  现在不在抽奖时间内
PASS  1.4 next_open_at                 2026-10-05 19:30:00
PASS  1.5 seconds_to_open              59571
PASS  1.7 抽奖报时段错误               现在不在抽奖时间内, 下次开放: 2026-10-05 19:30:00
PASS  1.8 当前时段内 open              1（原始 JSON 为布尔 true）
PASS  1.10 seconds_to_close            7191
PASS  1.13 跨天 22:00-01:00 凌晨        未开放, next_open_at = 2026-10-05 22:00:00
PASS  1.17 指定星期中文案              周一 00:10-23:50
PASS  1.18/1.19 windows_enabled=0      reason=open, text=""（全天开放）
PASS  2.1  my_today_drawn              3（按 daily_reset_time=04:00 切窗）
PASS  2.3  每日次数用完                今天的抽奖次数已用完, 明天再来
PASS  3.3  每周次数用完                本周的抽奖次数已用完, 下周再来
PASS  4.1  cooldown_left               60
PASS  4.3  冷却文案                    抽得太快啦, 请 60 秒后再试
PASS  5.1  daily_total_left            1 → 抽一次后 0
PASS  5.4  第 2 个用户被拦             今天的奖品已经发完了, 明天再来
PASS  6.2  已结束                       抽奖活动已经结束        (reason=after_end)
PASS  6.5  未开始                       抽奖活动还没开始        (reason=before_start)
PASS  6.7  start_date=end_date=今天     window.reason=open（含当天边界）
PASS  9.3  show_prizes=0               prizes=[]（[] 真数组）
PASS  9.7  notify_winner=0             抽中但不写 notifications（前后条数一致）
PASS  9.8  notify_winner=1             通知 +1，通知正文含卡密
PASS  9.11 池子空                      empty_text 原文回显
PASS  10.1 未登录 lottery_info         HTTP 401 / code 401
PASS  10.3 普通用户调 preview          code 403 / 没有权限, 仅管理员可操作
PASS  10.7 preview 管理员 200          返回体为扁平 data（无 window 包装）
```

### 4.2 加权抽奖分布（隔离真实奖项后实测）

- 高权重（weight=1000）× 低权重（weight=1），各 3 张假卡密，**抽 20 次**：高权重 **20 次**、低权重 **0 次**。
- 只启用低权重奖项（weight=1）再抽：抽中低权重奖项，卡密 `LWTEST-L-2` 正常发放 → 权重不阻断抽取。
- 等权重公平性抽检（两者都 1000，抽 12 次）：高 **5** / 低 **7** → 不是「永远取第一个奖项」。
- `weight=0` 的奖项不参与抽取。
- 测试后奖项 `weight`/`is_active` 已还原：`{id:4,is_active:1,weight:100} {id:5,1,100} {id:6,1,100}`。

### 4.3 后台校验文案（每条均触发，且 `settings.lottery_config` md5 前后一致 = 非法调用零写库）

```
每日重置时间格式不对, 应该是 HH:MM          (25:00 / 12:60 / abc 都命中)
抽奖时段格式不对, 应该是 HH:MM-HH:MM        (缺 end / 19-30 / 19:60)
抽奖时段最多 10 条                           (11 条)
抽奖时段开始和结束时间不能相同
星期只能是 1-7                               (0 / 8 / "a")
活动开始日期格式不对, 应该是 YYYY-MM-DD
活动结束日期格式不对, 应该是 YYYY-MM-DD
结束日期不能早于开始日期
冷却时间不能超过 86400 秒
每周抽奖次数不能超过 999
今天发放上限不能超过 999999
提示文案不能超过 200 个字                    (success_text / empty_text 各 201 字)
抽奖标题不能超过 50 个字
抽奖内容不能超过 2000 个字
每人抽奖次数不能超过 9999
每日抽奖次数不能超过 999
奖项权重不能超过 1000                        (admin_lottery_prize_save，权重未被写坏)
```

另验：`days` 去重排序（`[1,1]` → `[1]`）、缺参保留旧值（只传 `title` 时 `cooldown_seconds/windows_enabled/windows` 原样不动）、显式 `windows=[]` 按传的值存。

## 5. 父 agent 三条硬约定的线上实际返回（核对用）

**① JSON body 里 `windows` 是真数组**

```
POST ?action=admin_lottery_config_set  body:
{"windows":[{"start":"19:30","end":"20:00","days":[]},{"start":"12:00","end":"13:00","days":[1,3]}],"windows_enabled":0}

返回 data:
{"enabled":1,"title":"免费抽卡密","content":"关注风铃分享库…","success_text":"恭喜你抽中卡密","empty_text":"奖品抽完啦, 明天再来","per_user_limit":999,"daily_limit":0,"week_limit":3,"daily_reset_time":"04:00","cooldown_seconds":60,"daily_total_limit":10,"windows_enabled":0,"windows":[{"start":"19:30","end":"20:00","days":[]},{"start":"12:00","end":"13:00","days":[1,3]}],"start_date":"2026-10-05","end_date":"2026-11-04","show_prizes":1,"show_stock":1,"notify_winner":1}
```

**② `windows_enabled=0` 不清空 `windows`**：见上（0 + 两条原样保留）；`lottery_info` 同样原样回显，此时 `window.reason="open"`、`text=""`。

**③ `admin_lottery_window_preview` 扁平结构**（`data` 下直接是字段，无 `data.window`）：

```
{"code":0,"msg":"ok","data":{"open":false,"reason":"outside_window","reason_text":"现在不在抽奖时间内","text":"每天 19:30-20:00","next_open_at":"2026-10-05 19:30:00","next_close_at":"","seconds_to_open":59446,"seconds_to_close":0,"server_time":"2026-10-05 02:59:14","my_allowed":false,"daily_total_left":-1,"daily_total_limit":0}}
```

> `open` / `my_allowed` 是**布尔**（与契约 B 节一致）；布尔兼容已加固：`true/1/"1"/"true"/"on"` → 1，`false/0/"0"/"false"` → 0（后台 a-switch 传 JSON 布尔不会丢配置）。

## 6. 给后台 / 客户端 agent 的必读提醒

1. `action` 必须放 **URL 查询串**（`api.php?action=xxx`），参数放 **POST JSON body**；错误码在返回体 `code`（HTTP 400/401/403 同步）。
2. `lottery_info.data.window`：`{open:bool, reason, reason_text, text, next_open_at, next_close_at, seconds_to_open, seconds_to_close, server_time, my_allowed:bool}`；`reason ∈ disabled|before_start|after_end|outside_window|open`；`windows_enabled=0` → `reason="open"`、`text=""`。
3. **`-1` = 不限**：`daily_limit=0 → my_today_left=-1`；`week_limit=0 → my_week_left=-1`；`daily_total_limit=0 → daily_total_left=-1`；`0` 才是「用完/发完」。客户端按契约「daily_limit>0 才显示今日剩余」处理，不要用 `-1` 判禁用。
4. 抽中返回 `lottery_draw.data` 新增 `cooldown_seconds / cooldown_left / my_today_left / my_week_left / daily_total_left`（同样 `-1`=不限）。
5. `prizes[]` 新增 `weight`；`show_prizes=0 → prizes=[]`；`show_stock=0` 仅前端隐藏数量，`left` 仍返回。
6. 固定文案：`抽奖活动已关闭` / `抽奖活动还没开始` / `抽奖活动已经结束` / `现在不在抽奖时间内, 下次开放: YYYY-MM-DD HH:MM:SS` / `抽得太快啦, 请 N 秒后再试` / `今天的奖品已经发完了, 明天再来` / `今天的抽奖次数已用完, 明天再来` / `本周的抽奖次数已用完, 下周再来` / `你的抽奖次数已用完` / 池子空用 `empty_text`（默认 `奖品已抽完, 请稍后再来`）。
7. 服务端对 `HH:MM` **宽松解析 + 规范化**：`9:5`/`9:05` 都接受并存成 `09:05`；`25:00`/`12:60`/`abc` 报格式错误。`days` 去重排序，空数组 = 每天。
8. 后台：`windows_enabled=0` 时不传 `windows` 也保留旧值，传了照样存；要真删光时段必须显式传 `windows: []`。
9. `admin_lottery_window_preview` 未登录 401、非管理员 403；`admin_lottery_config_get/set`、`admin_lottery_prizes` 同样只认管理员。
10. 迁移脚本 `tools/install_lottery_window.php` 已加保护：CLI 可跑，HTTP 匿名 403，HTTP 带管理员 token（`?token=`）可跑。

## 7. 清理前后计数对照（零残留、真实数据未动）

| 指标 | 基线 | 清理前 | 清理后 |
|---|---|---|---|
| lottery_prizes | 3 | 5（+2 临时） | **3** |
| lottery_codes | 54 | 84（+30 假卡密） | **54** |
| codes_used / free | 40 / 14 | 40 / 44 | **40 / 14** |
| lottery_draws | 40 | 40 | **40** |
| notifications / 其中 lottery | 490 / 40 | 490 / 40 | **490 / 40** |
| users | 46 | 48（+2 临时账号） | **46** |
| 临时奖项/卡密/抽奖记录/账号 | — | 2 / 30 / 0 / 2 | **0 / 0 / 0 / 0** |

- 临时数据：账号 id `990001`(`lotwin_probe_a`) / `990002`、奖项 id 9 `[时段测试]高权重` / 10 `[时段测试]低权重`、假卡密前缀 `LWTEST-`；`users AUTO_INCREMENT` 已回 **67**（真实 max(id)=66）。其余表自增号仅前进（`lottery_prizes:11 / lottery_codes:119 / lottery_draws:98 / notifications:816`），无数据影响。
- 真实奖项 `{4 Aevum天卡, 5 Aevum周卡, 6 Aevum月卡}` → `is_active=1`、`weight=100`，与测试前一致。
- `settings.lottery_config` **逐字节还原**为「原 5 键原值 + 13 新键默认值」（迁移脚本产物，521 字节，写入后复核 `raw === 库内值` = `true`）：

```json
{"enabled":1,"title":"免费抽卡密","content":"关注风铃分享库，天天有机会抽到天卡 \/ 周卡 \/ 月卡卡密！\n抽到的卡密会同时保存在「我的 → 消息中心」，可随时查看复制。","per_user_limit":1,"daily_limit":1,"success_text":"","empty_text":"奖品已抽完, 请稍后再来","week_limit":0,"daily_reset_time":"00:00","cooldown_seconds":0,"daily_total_limit":0,"windows_enabled":0,"windows":[],"start_date":"","end_date":"","show_prizes":1,"show_stock":1,"notify_winner":1}
```

- 纪律：**未调用 `admin_lottery_activity_reset`**；未抽真实奖项、未消耗真实卡密（`codes_used` 40→40）；未改任何真实用户记录。

### 需要如实交代的一次插曲

清理脚本第一次运行时，还原用的原文文件因路径写错未上传成功，`settings.lottery_config` 被短暂写成空字符串（约 2 分钟，表现为默认 `enabled=0`「抽奖活动已关闭」，期间无用户抽奖：`lottery_draws` 仍 40）。发现后立即用备份原文写入并复核「字节完全一致」，随后 HTTP 冒烟（`lottery_info`、`admin_lottery_prizes`）正常。**无真实用户数据受损**，属我的操作失误。

## 8. 未验证项与已知限制

1. 只做串行接口测试，**未压测并发**（多人同时抢最后一张卡密的 `FOR UPDATE` 行为未验证）。
2. 加权分布是小样本（20 / 12 次），只证明方向正确，未做统计显著性检验。
3. 跨天时段 + 指定星期几的**组合**只各测 1 例，未穷举 10 时段 × 7 天；`next_open_at` 每分钟扫描、最多扫 8 天（11520 次），极端配置未覆盖。
4. `daily_reset_time` 变更后的历史数据兼容：沿用原 `lottery_day_reset_at` 比较逻辑，仅用 3 条构造记录验证边界，未在真实历史数据上回放。
5. 客户端 App（1.1.4 / versionCode 141）与后台 UI 联调不在本 agent 范围，未验证。
6. 未测通知开关与「我的→消息中心」列表联动展示（只验证通知写入有无与正文含卡密）。
7. **系统性问题（非本次引入）**：`/www/wwwroot/flfxk/tools/*.php` 可被公网匿名访问（历史 `install_lottery.php` 等 HTTP 200 且含 DDL）。我只给自己新增的 `install_lottery_window.php` 加了保护，**其余 13 个历史脚本仍在裸奔**，建议 nginx 加 `location ^~ /tools/ { deny all; }` 或统一加固。

## 9. 测试脚本（服务器 /tmp，可复查）

`/tmp/lotwin_test.php`（时段/每日重置/每周/冷却）、`/tmp/lotwin_test2.php`（全站上限/日期范围/加权/校验/开关/鉴权/清理）、`/tmp/lotwin_funcs.php`（从 api.php 提取的 25 个抽奖函数）、`/tmp/lotwin_demo.php`（三约定取证）、`/tmp/final_state.php`（最终状态核对）、`/tmp/restore_cfg.php`（配置原文还原）。
本地副本：`~/lotwin/`（`baseline.txt` 基线快照、`orig_cfg.json` 还原原文、`p1..p7_*.py` 锚点补丁脚本）。
