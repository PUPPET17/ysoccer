# Player AI 与战术可控性审计

本文梳理当前 ySoccer 实时比赛中的 Player AI、站位与跑位、传球、射门、防守以及阵型对传跑的影响，并据此判断“自然语言场边指令”可以复用哪些底层能力、还缺少哪些战术旋钮。

审计基线：仓库提交 `7adc7bf3`，2026-09-14。任务分类为“现有系统调研与文档化”，本文不改变比赛行为。

后续第一版实现与行为验收见 [实时比赛 AI 最小战术控制层](tactical-control-demo.md)。本文继续保留为“改造前”事实基线。

## 结论摘要

当前实时比赛 AI 是一套两层有限状态机：

1. `AiFsm` 把电脑球员的决定翻译成与手柄相同的方向键和动作键输入。
2. `PlayerFsm` 消费这些输入，执行跑动、控球、传球、射门、抢球、扑救和定位球动作。

球队层没有独立的 `Team AI`、比赛阶段模型或动态战术状态。现有唯一成熟的球队级战术入口是 `team.tactics`：每套阵型为 10 名外场球员预存了“足球位于 5×7 个区域时各自应该去哪里”的目标点。

因此，现有系统能表现“换一套完整阵型后，球员逐渐跑向新位置”，也能让阵型间接改变接球队员的分布；但它还不能表达宽度、紧凑度、节奏、压迫强度、球员职责和指令持续时间等独立参数。

| 教练意图 | 当前能力 | 判断 |
|---|---|---|
| 收敛/拉开队形 | 可换到另一套目标点表，或编辑整套 `.TAC` | 部分支持；没有可连续调节的宽度、紧凑度或线距 |
| 中后场在组织阶段分散 | 可以为不同球区预制不同站位 | 部分支持；没有“控球/防守/转换”阶段，也没有中后场组或职责 |
| 中锋多做支点 | 只有粗粒度 `ATTACKER` 角色，常规 AI 不读取角色来决定回撤、背身或前插 | 不支持 |
| 7 号多参与防守 | 能按号码找到球员，但没有单人指令状态；防守追球由全队统一选 `bestDefender` | 不支持 |
| 缓一下节奏 | 没有 tempo、持球耐心、向前传球风险或跑动频率参数 | 不支持 |

最适合第一版战术指令接入的位置不是语音层，而是 `Team.updateTactics()`、`AiStatePositioning`、`AiStateAttacking`、`Player.searchPassingMate()` 和 `Team.findBestDefender()` 之间。目前这些位置直接读取全局常量、固定概率或静态目标点，尚无球队/球员级运行时战术上下文。

## 代码范围与事实来源

仓库包含三套有历史关系的实现：

- `java/core`：当前 Java/libGDX 主实现，也是本文的事实来源。
- `android/ysoccer_demo`：较早的 Android 演示移植。
- `blitzmax`：原始 BlitzMax 实现。

后两套只适合追溯算法来源，不能用来断言当前游戏行为。

核心文件：

| 责任 | 当前实现 |
|---|---|
| AI 作为虚拟输入设备 | [`framework/Ai.java`](../core/src/main/java/com/ygames/ysoccer/framework/Ai.java) |
| AI 决策状态机 | [`AiFsm.java`](../core/src/main/java/com/ygames/ysoccer/match/AiFsm.java) 与 `AiState*.java` |
| 球员动作状态机 | [`PlayerFsm.java`](../core/src/main/java/com/ygames/ysoccer/match/PlayerFsm.java) 与 `PlayerState*.java` |
| 球队级选人、站位目标 | [`Team.java`](../core/src/main/java/com/ygames/ysoccer/match/Team.java) |
| 球员数据、接球人搜索 | [`Player.java`](../core/src/main/java/com/ygames/ysoccer/match/Player.java) |
| 阵型目标点数据结构 | [`Tactics.java`](../core/src/main/java/com/ygames/ysoccer/match/Tactics.java) |
| 持球进攻、传射选择 | [`AiStateAttacking.java`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java) |
| 传球和射门动作 | [`PlayerStateKick.java`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateKick.java) |
| 常规跑动和近距离控球 | [`PlayerStateStandRun.java`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateStandRun.java) |
| 比赛主循环 | [`MatchStateMain.java`](../core/src/main/java/com/ygames/ysoccer/match/MatchStateMain.java) |

## 运行时架构

### AI 并不直接移动球员

每名 `Player` 构造时都会创建一个 `Ai`，它继承 `InputDevice`。`Ai.read()` 只调用 `AiFsm.think()`；各个 AI 状态写入 `x0/y0/fire10/fire20`，再由通用输入缓冲转换为有效方向、角度和按键边沿。证据见 [`Ai.java#L6-L20`](../core/src/main/java/com/ygames/ysoccer/framework/Ai.java#L6-L20) 和 [`InputDevice.java#L42-L65`](../core/src/main/java/com/ygames/ysoccer/framework/InputDevice.java#L42-L65)。

这条链路是：

```text
AiFsm 决策
    ↓
虚拟方向/动作键
    ↓
PlayerFsm 动作状态
    ↓
速度、朝向、球速、球权
    ↓
比赛物理
```

好处是电脑和真人共用动作规则；限制是 AI 目前只能通过很小的输入接口表达决定，不能直接提交“跑到某个战术区域”“执行某种角色行为”之类的结构化意图。

### 两个状态机的分工

`AiFsm` 有 15 个状态，负责“想做什么”：空闲、站位、追逐、持球进攻、主防守、传球、射门以及各种定位球输入。完整注册见 [`AiFsm.java#L7-L62`](../core/src/main/java/com/ygames/ysoccer/match/AiFsm.java#L7-L62)。

`PlayerFsm` 有 40 多个状态，负责“动作如何发生”：站立/跑动、踢球、头球、铲球、倒地、到达目标、门将站位/扑救、定位球动作、庆祝和替补等。完整列表见 [`PlayerFsm.java#L5-L51`](../core/src/main/java/com/ygames/ysoccer/match/PlayerFsm.java#L5-L51)。

`AiFsm` 状态职责清单：

| 状态 | 当前职责 |
|---|---|
| `STATE_IDLE` | 清空方向与动作键，并根据 `PlayerFsm` 当前动作路由到其他 AI 状态 |
| `STATE_POSITIONING` | 跑向阵型目标点，并判断是否应追逐、持球进攻或主防守 |
| `STATE_SEEKING` | 追向自己可到达的足球预测点 |
| `STATE_DEFENDING` | 被选为 `bestDefender` 后，间歇更新追球方向 |
| `STATE_ATTACKING` | 持球转向、搜索队友，并在传球、射门和继续带球之间切换 |
| `STATE_PASSING` | 保持方向，短按动作键以触发通用踢球动作中的传球分支 |
| `STATE_KICKING` | 选择近/远门柱方向并用动作键时长控制射门 |
| `STATE_KICKING_OFF` | 开球时使用固定方向和固定按键时序 |
| `STATE_GOAL_KICKING` | 球门球使用固定等待和按键时序 |
| `STATE_THROWING_IN` | 界外球使用固定转向和按键时序 |
| `STATE_CORNER_KICKING` | 角球使用固定转向和按键时序 |
| `STATE_FREE_KICKING` | 在解围、直接射门、传给最近队友、向前开大脚之间按硬规则选择 |
| `STATE_PENALTY_KICKING` | 随机选择低/中/高和左右方向，再生成按键时长 |
| `STATE_KEEPER_KICKING` | 门将持球开球使用固定方向变化和按键时序 |
| `AI_STATE_BARRIER` | 人墙面对来球时判断是否跳起 |

定位球 AI 大多是固定时序。任意球是例外：它会根据位置、是否可直接射门、随机射门信心和最近队友选择四类处理方式；其中射门信心读取 `shooting`，传给最近队友仍不读取 `passing`，见 [`AiStateFreeKicking.java#L43-L103`](../core/src/main/java/com/ygames/ysoccer/match/AiStateFreeKicking.java#L43-L103)。

开放比赛中的主要 AI 转移如下：

```text
Idle
  └─ 球员进入 StandRun → Positioning

Positioning
  ├─ 本队最近且无人持球 → Seeking
  ├─ 自己持球           → Attacking
  └─ 被选为主防守人     → Defending

Seeking
  ├─ 自己拿球           → Attacking
  ├─ 队友拿球           → Positioning
  └─ 不再是本队最近     → Positioning

Defending
  ├─ 自己拿球           → Attacking
  ├─ 队友拿球           → Positioning
  └─ 不再是主防守人     → Positioning

Attacking
  ├─ 找到接球队友且概率命中 → Passing
  ├─ 进入射门区且概率命中   → Kicking
  └─ 丢失球权               → Seeking
```

对应条件集中在 [`AiStatePositioning.java#L27-L47`](../core/src/main/java/com/ygames/ysoccer/match/AiStatePositioning.java#L27-L47)、[`AiStateSeeking.java#L30-L56`](../core/src/main/java/com/ygames/ysoccer/match/AiStateSeeking.java#L30-L56)、[`AiStateDefending.java#L44-L70`](../core/src/main/java/com/ygames/ysoccer/match/AiStateDefending.java#L44-L70) 和 [`AiStateAttacking.java#L108-L153`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L108-L153)。

值得注意：持球人丢球后会先进入 `Seeking`。只要他仍是本队离球最近者，即使另一名球员已被选为 `bestDefender`，他仍可能继续追球，所以场上并非严格永远只有一人逼抢。

### 主循环与决策频率

模拟每秒运行 512 个物理子帧，每 8 个子帧更新一次 AI，即 AI 频率为 64 Hz，见 [`GLGame.java#L23-L27`](../core/src/main/java/com/ygames/ysoccer/framework/GLGame.java#L23-L27)。开放比赛每个虚拟帧先更新 AI 和到球时间，再在子帧中更新球、球员、最近球员、球区和阵型目标，见 [`MatchStateMain.java#L64-L94`](../core/src/main/java/com/ygames/ysoccer/match/MatchStateMain.java#L64-L94) 与 [`MatchStateMain.java#L255-L274`](../core/src/main/java/com/ygames/ysoccer/match/MatchStateMain.java#L255-L274)。

因此 AI 使用的是上一轮已经计算出的球轨迹预测和到球时间。这是一帧级滞后，不是基于未来整段比赛状态的规划。

## 站位与无球跑位

### 阵型是“球区 → 球员目标点”查表

球场按足球位置离散成横向 5 区、纵向 7 区，共 35 区。`Ball.updateZone()` 用 `BALL_ZONE_DX=206`、`BALL_ZONE_DY=184` 计算 `zoneX ∈ [-2,2]`、`zoneY ∈ [-3,3]`，见 [`Ball.java#L265-L276`](../core/src/main/java/com/ygames/ysoccer/match/Ball.java#L265-L276) 和 [`Const.java#L185-L195`](../core/src/main/java/com/ygames/ysoccer/match/Const.java#L185-L195)。

每个 `.TAC` 文件保存：

- 10 名外场球员 × 35 个球区 × 1 字节目标坐标；
- 编辑器用的球员配对信息；
- 一个 `basedOn` 基础阵型编号。

加载规则见 [`Tactics.java#L63-L120`](../core/src/main/java/com/ygames/ysoccer/match/Tactics.java#L63-L120)。守门员索引 0 不在这张目标点表中。

比赛中 `Team.updateTactics()` 根据球队进攻方向把当前球区归一化，读取 `Assets.tactics[tactics].target[i][ballZone]`，再旋转到本队实际半场坐标，见 [`Team.java#L291-L311`](../core/src/main/java/com/ygames/ysoccer/match/Team.java#L291-L311)。

### 目标点如何变成跑动

处于 `AiStatePositioning` 的球员只做一件事：

- 距目标大于 20：沿目标角度写入一个八方向移动输入；
- 距目标不大于 20：停止移动。

见 [`AiStatePositioning.java#L14-L24`](../core/src/main/java/com/ygames/ysoccer/match/AiStatePositioning.java#L14-L24)。真实速度由 `PlayerStateStandRun` 使用球员 `speed` 技能计算，持球者另受 5% 速度惩罚，见 [`Player.java#L712-L727`](../core/src/main/java/com/ygames/ysoccer/match/Player.java#L712-L727) 与 [`PlayerStateStandRun.java#L28-L49`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateStandRun.java#L28-L49)。

这意味着阵型切换不会瞬移球员；下一次目标表更新后，球员会通过普通跑动靠近新目标。当前没有跑动路线规划、队友避让、越位线、插上时机、套边、回撤接应或位置轮换决策。

### 站位更新是离散的，没有真正插值

`Ball` 已经计算球在当前球区内部的 `mx/my` 小数偏移，但 `Team.updateTactics()` 的表达式是：

```java
player.tx = (1 - abs(ball.mx)) * tx + abs(ball.mx) * tx;
player.ty = (1 - abs(ball.my)) * ty + abs(ball.my) * ty;
```

两项使用的是同一个 `tx/ty`，代数化简后仍然只是 `tx/ty`。所以球员目标会在足球跨过球区边界时直接跳到下一组目标点，区内没有平滑插值。参见 [`Team.java#L303-L307`](../core/src/main/java/com/ygames/ysoccer/match/Team.java#L303-L307)。

这是已有实现中的明确缺口：若以后让“宽度/紧凑度”连续变化，最好先把目标生成和插值从查表代码中分离出来。

### `pairs` 只服务阵型编辑器

`Tactics.pairs` 能把左右对称位置配成一组，但当前引用只出现在阵型编辑/复制逻辑中；实时比赛的 `Team.updateTactics()` 不读取它。因此它不是盯人、协防或双人联动关系。

## 持球推进与跑动选择

持球 AI 不再读取自己的阵型目标点，而由 `AiStateAttacking` 在“左转 45°、保持方向、右转 45°”三个候选方向中累加权重。

普通方向更新综合三类信息：

1. 队友：偏向让队友更快接近预测球路的方向。
2. 对手：偏向让对手更晚接近预测球路的方向。
3. 球门：通常向对方球门推进；在己方禁区持球时优先远离己方球门。

默认权重是队友 `1.0`、对手 `1.5`、己方球门脱险 `3.5`、对方球门 `2.5`，进入对方禁区后球门权重 `3.5`，见 [`AiStateAttacking.java#L24-L39`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L24-L39) 和 [`AiStateAttacking.java#L206-L250`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L206-L250)。靠近边线/球门线时另有紧急转向，防止跑出场外。

这些是全局静态参数，不属于某支球队、某名球员或某条临时指令，也没有被持久化或联网同步为战术状态。

队友和对手权重使用 `frameDistanceL/frameDistance/frameDistanceR`：它们分别表示某球员到足球按当前方向左偏 45°、直行、右偏 45°的预测轨迹需要多少帧，预测实现见 [`Ball.java#L225-L262`](../core/src/main/java/com/ygames/ysoccer/match/Ball.java#L225-L262) 和 [`Player.java#L318-L339`](../core/src/main/java/com/ygames/ysoccer/match/Player.java#L318-L339)。这是一种局部反应式转向，不是搜索多步带球路线。

## 传球选择与执行

### 何时传球

持球 AI 每 8 个 AI 帧（约 125 ms）搜索一次接球队友。只要找到候选人，就以固定 `0.3` 概率进入传球状态；传球判断先于当帧射门判断，见 [`AiStateAttacking.java#L73-L75`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L73-L75) 和 [`AiStateAttacking.java#L108-L139`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L108-L139)。

当前没有把以下信息计入“传还是不传”：

- 比分、剩余时间或比赛节奏；
- 接球队友位置是否更有推进价值；
- 传球线路上的对手和被拦截风险；
- 持球压力、人数优势或空间；
- 球员角色和个人倾向；
- `passing` 技能。

### 传给谁

`Player.searchPassingMate()` 遍历本队场上球员，候选人必须：

- 不是自己；
- 能在预测窗口内到达当前直行球路；
- 正处于 `STAND_RUN`、`REACH_TARGET` 或 `IDLE` 动作状态；
- 位于当前朝向约 ±27.5° 的搜索锥内。

符合条件者按最小 `frameDistance` 选出，传球修正角最多 ±22.5°。完整条件见 [`Player.java#L1134-L1180`](../core/src/main/java/com/ygames/ysoccer/match/Player.java#L1134-L1180)。

这里的“最佳”只表示最快到达预测球路，不代表线路最安全、最向前、最空当或最符合阵型职责。算法使用的是当前球轨迹预测，而不是按即将产生的传球速度为每个候选人单独模拟传球。

### 怎样完成传球

`AiStatePassing` 保持原移动方向并按住动作键 8 个 AI 帧，然后释放，见 [`AiStatePassing.java#L14-L34`](../core/src/main/java/com/ygames/ysoccer/match/AiStatePassing.java#L14-L34)。`PlayerStateStandRun` 检测按键边沿后进入 `STATE_KICK`；约 150 ms 后，如果动作键已释放，就把动作判为传球。

传球初速固定为 240，再按接球队友距离乘 `PASSING_SPEED_FACTOR` 加速；无额外方向输入时才应用自动角度修正，见 [`PlayerStateKick.java#L43-L73`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateKick.java#L43-L73)。

审计发现：`Player.skills.passing` 在当前实时比赛包中没有参与传球目标选择、角度误差、传球速度或成功率。它目前主要用于球员展示/估值排序，以及自动比分的攻防评分汇总。`Const.PASSING_THRESHOLD` 也只有控制台读写入口，没有实时比赛读取点。相反，传球蓄力和高度的若干计算仍读取 `skills.shooting`。

这意味着“传球能力”和“降低传球风险”在现有实时 AI 中都还不是可用旋钮。

## 射门选择

持球者只有同时满足以下条件才考虑射门：

- 足球位于距任一门柱不超过 310 场地像素的直接射门区；
- 当前朝向能够看到球门。

射门触发概率由球门视觉张角和距门柱距离共同计算：

```text
P_visual  = -width² / 180² + 2 × width / 180
P_distance = 1 - 0.9 × distance² / 310²
P_shoot   = (P_visual + P_distance²) / 2
```

随机命中后才进入 `AiStateKicking`，证据见 [`AiStateAttacking.java#L123-L138`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L123-L138) 和 [`AiStateAttacking.java#L389-L401`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L389-L401)。

这里的概率是“此刻是否起脚”的决策概率，不是进球概率（xG）。起脚后的进球仍由射门方向、蓄力、旋转、球员 `shooting` 技能、门将行为和足球物理共同决定。

`AiStateKicking` 在近角和远角中选择需要转向修正较小的一侧，再通过按键持续时间产生射门力度，见 [`AiStateKicking.java#L25-L76`](../core/src/main/java/com/ygames/ysoccer/match/AiStateKicking.java#L25-L76) 与 [`AiStateKicking.java#L79-L101`](../core/src/main/java/com/ygames/ysoccer/match/AiStateKicking.java#L79-L101)。`finishing` 技能没有进入实时射门选择或射门动作；它只参与评分类方法。

## 防守选择与抢球

### 谁上抢

`Team.findBestDefender()` 从索引 1–10 的外场球员中选择主防守人。只有对手是最后触球方时才寻找候选；候选球员必须比球更靠近本方球门（球员到本方球门距离小于球到本方球门距离的 95%），再从中选择离球最近者。新候选必须比当前主防守人至少近 10% 才替换，以减少频繁切换。见 [`Team.java#L267-L289`](../core/src/main/java/com/ygames/ysoccer/match/Team.java#L267-L289)。

该选择每个虚拟帧更新一次，入口见 [`Team.java#L394-L400`](../core/src/main/java/com/ygames/ysoccer/match/Team.java#L394-L400)。它没有考虑：

- 球员角色、号码或个人防守职责；
- 防线是否会被带走；
- 对位、区域、协防、补位和身后保护；
- 压迫强度、体能或犯规风险；
- 多人同时压迫的组织规则。

### 主防守人做什么

`AiStateDefending` 每隔随机 15–30 个 AI 帧，把方向对准自己预计可到达的足球预测点，见 [`AiStateDefending.java#L20-L41`](../core/src/main/java/com/ygames/ysoccer/match/AiStateDefending.java#L20-L41)。它不计算封堵传球线、把持球人赶向边路、保持身位或延缓进攻。

该状态也不会主动按下动作键，所以电脑 AI 的常规防守不是主动选择滑铲。接近足球后，通用 `Player.getPossession()` 会自动争夺球权：成功概率只比较抢球者和持球者的 `tackling`，见 [`Player.java#L342-L379`](../core/src/main/java/com/ygames/ysoccer/match/Player.java#L342-L379)。

真正的 `PlayerStateTackle` 需要动作键触发，铲球速度、碰撞宽度和出球速度受 `tackling` 影响，见 [`PlayerStateStandRun.java#L110-L139`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateStandRun.java#L110-L139) 与 [`PlayerStateTackle.java#L17-L84`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateTackle.java#L17-L84)。当前 `AiStateDefending` 不会走这条主动选择链。

### 其他队员怎样防守

未被选为追球队员的外场 AI 继续执行阵型目标点。因此防守整体形状完全来自同一套 `.TAC` 的当前球区目标，没有独立的盯人、回收、保护肋部或防线协同行为。

守门员是例外：`PlayerStateKeeperPositioning` 自己维护 `DEFAULT`、`RECOVER_BALL`、`COVER_SHOOTING_ANGLE` 三种模式，能根据禁区、最近到球者和球路决定回收球或封角，见 [`PlayerStateKeeperPositioning.java#L17-L29`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateKeeperPositioning.java#L17-L29) 与 [`PlayerStateKeeperPositioning.java#L85-L164`](../core/src/main/java/com/ygames/ysoccer/match/PlayerStateKeeperPositioning.java#L85-L164)。

## 阵型选择对传跑的实际影响

### 直接影响：无球队员跑向不同目标

项目内置 12 套实际预设：`4-4-2`、`5-4-1`、`4-5-1`、`5-3-2`、`3-5-2`、`4-3-3`、`4-2-4`、`3-4-3`、`SWEEP`、`5-2-3`、`ATTACK`、`DEFEND`；另有 6 个用户槽位，默认从 `4-4-2` 文件加载。代码表见 [`Tactics.java#L19-L55`](../core/src/main/java/com/ygames/ysoccer/match/Tactics.java#L19-L55)，二进制预设位于 [`assets/data/tactics/preset`](../assets/data/tactics/preset)。

阵型切换只修改 `team.tactics`，随后每轮查表产生新目标；教练的 `CALL` 动画只是视觉反馈，不参与 AI 决策，见 [`MatchStateBenchTactics.java#L63-L80`](../core/src/main/java/com/ygames/ysoccer/match/MatchStateBenchTactics.java#L63-L80) 和 [`Coach.java#L47-L83`](../core/src/main/java/com/ygames/ysoccer/match/Coach.java#L47-L83)。

以下是从预设文件解码出的“足球在中央球区”纵向目标示例。数值为 `.TAC` 网格单位，乘 `TACT_DY=40` 后才是场地距离；越大越靠近进攻方向。`P1..P10` 是外场阵容索引，不是球衣号码。

| 预设 | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 | P9 | P10 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `4-4-2` | -7 | -11 | -9 | -7 | 1 | -5 | -1 | 1 | 7 | 7 |
| `ATTACK` | -7 | -9 | -3 | -7 | 5 | -3 | 3 | 5 | 7 | 9 |
| `DEFEND` | -9 | -11 | -11 | -9 | -7 | -5 | -5 | -7 | 1 | 7 |

这说明 `ATTACK`/`DEFEND` 不是一个心态倍率，而是完整目标点表：它们会把特定阵容索引放到更高或更低的位置。

### 间接影响：阵型改变传球候选分布

阵型不会直接改变 `PASSING_PROBABILITY`、搜索角或线路评分，但会改变队友的真实位置；真实位置又影响：

1. 谁落在持球者前方约 ±27.5° 的搜索锥内；
2. 谁能最快到达当前预测球路；
3. 持球推进时三个方向的队友权重。

因此换阵确实会改变可观察到的传球和带球方向，但这是几何位置产生的二阶效果，不是“4-3-3 更爱边路”“5-4-1 更保守”这样的显式策略。

### 不影响的内容

换阵不会直接改变：

- 约每 125 ms 一次的传球搜索频率；
- 找到候选人后的固定 0.3 传球概率；
- 接球人评分方式；
- 射门概率公式；
- 主防守人选择规则；
- 球员 `role`、技能或个人倾向；
- 比赛节奏和动作冷却；
- 控球与非控球阶段的不同职责。

`Player.role` 在开放比赛 AI 中主要只用于识别首发守门员；其他角色更多服务名单、替补匹配、显示和自动比分评分。`Tactics.basedOn/order` 主要服务阵容位置与编辑映射，而实时目标更新直接按 `lineup.get(i)` 读取目标索引，见 [`Team.java#L299-L304`](../core/src/main/java/com/ygames/ysoccer/match/Team.java#L299-L304) 与 [`Team.java#L639-L679`](../core/src/main/java/com/ygames/ysoccer/match/Team.java#L639-L679)。

## 现有可调参数清单

### 已经影响实时比赛，但都是全局值

| 参数 | 当前作用 | 位置 |
|---|---|---|
| `AiStateAttacking.Parameters.PASSING_PROBABILITY` | 找到队友后是否传球 | [`AiStateAttacking.java#L24-L39`](../core/src/main/java/com/ygames/ysoccer/match/AiStateAttacking.java#L24-L39) |
| `MATE_FACTOR` / `OPPONENT_FACTOR` | 持球方向对队友/对手的权重 | 同上 |
| `GOAL_FACTOR*` | 持球方向对球门的权重 | 同上 |
| `PASSING_SPEED_FACTOR` | 按传球距离追加球速 | [`Const.java#L29-L32`](../core/src/main/java/com/ygames/ysoccer/match/Const.java#L29-L32) |
| `POSSESSION_SPEED_PENALTY` | 持球跑速惩罚 | [`Const.java#L55-L59`](../core/src/main/java/com/ygames/ysoccer/match/Const.java#L55-L59) |
| `DRIBBLE_CONTROL_DISTANCE` | 近距离控球范围 | [`Const.java#L39-L43`](../core/src/main/java/com/ygames/ysoccer/match/Const.java#L39-L43) |
| `BALL_OWNER_RELEASE_DISTANCE` | 解除球权的距离 | [`Const.java#L45-L53`](../core/src/main/java/com/ygames/ysoccer/match/Const.java#L45-L53) |

这些参数不能直接充当教练指令状态，因为修改后会影响双方所有球员，没有球队归属、目标球员、比赛阶段、强度、时限、衰减、冲突合并和网络同步语义。

### 已存在数据，但没有进入对应实时决策

| 数据 | 当前实时缺口 |
|---|---|
| `Player.role` | 不决定开放比赛中的站位职责、跑位或传防倾向 |
| `skills.passing` | 不决定实时传球选择、误差、力度或成功率 |
| `skills.finishing` | 不决定实时射门选择或终结动作 |
| `Tactics.pairs` | 只用于编辑器左右配对，不用于比赛协作 |
| `Ball.mx/my` | 已计算，但阵型目标插值实际为恒等式 |
| `Const.PASSING_THRESHOLD` | 有控制台入口，没有比赛读取点 |
| `Team.ControlMode.COACH` / `Coach` | 提供教练模式和动画，没有战术决策模型 |

## 对自然语言战术指令的缺口映射

### “收敛队形”

可复用：35 球区目标点、普通跑动到目标的执行链。

缺少：球队级 `width`、横纵紧凑度、各线间距、合法范围、平滑过渡和恢复基线。当前若直接修改 `.TAC` 目标，会改变整套静态资源，而不是产生某场比赛中的临时战术状态。

### “中后场拉开一点”

可复用：球区条件能粗略表示球在后场/中场/前场。

缺少：控球阶段识别、位置组、后场组织职责、传球出球点、安全边界和丢球后的独立防守形状。同一张目标表当前同时承担控球与非控球站位。

### “中锋多做支点”

可复用：`ATTACKER` 角色、接球队友搜索、球员目标和普通动作系统。

缺少：更精确的位置/职责绑定，以及 `comeShort`、`holdUpPlay`、`runBehind`、`layoffPreference` 等候选行为。当前无球队员只回阵型点，持球者只做局部方向、传球和射门判断。

### “7 号多参与防守”

可复用：`Player.number` 能解析到实体；追球和自动争抢动作已经存在。

缺少：每名球员的运行时指令、回防深度、跟防对象、协防优先级，以及与 `bestDefender` 的仲裁。直接强制 7 号成为 `bestDefender` 会破坏现有“球门侧且最近”的保护规则。

### “缓一下节奏”

可复用：传球概率、AI 更新间隔和若干运动参数说明底层有可调常量。

缺少：球队级 tempo 对多个决策的统一影响。合理效果至少应同时进入持球时间、向前传球风险、无球前插频率、回传偏好和重新组织倾向；现有代码没有这些候选动作与评分维度。

## 推荐的接入边界

自然语言或语音解析结果不应直接改 `Player.x/y`，也不应直接改全局静态常量。适合增加的中间层是每场、每队独立的运行时战术状态：

```text
TacticalCommand（结构化、已校验）
            ↓
TeamTacticalState（球队级、分阶段、可衰减）
            ├─ PlayerInstruction[playerId]
            ├─ positioning modifiers
            └─ decision modifiers
                    ↓
现有 Team / AiState / Player 动作与物理
```

第一阶段最小接入点建议如下：

1. 在 `Team` 上增加每场独立的战术状态，不放进 `Const` 或 `AiStateAttacking.Parameters`。
2. 把 `Team.updateTactics()` 拆成“基础目标点 + 球区插值 + 球队修正 + 个人修正 + 场地约束”，先支持宽度和紧凑度。
3. 让 `AiStatePositioning` 消费带职责的目标，而不只是在 20 像素阈值外直线跑动。
4. 把 `searchPassingMate()` 从“找到一个人”升级为返回候选列表与可解释评分；再由 `AiStateAttacking` 决定传、带、射。
5. 把 `bestDefender` 从单个最近者扩展为主压迫、保护和跟防职责分配，但保留清晰上限，防止多人无脑追球。
6. 为这些决策增加固定随机种子下的无头测试和可观测日志，再接语音/NLU。

这条边界让语言模型只有“解释教练意图”的权限，真正场上行为仍由可测试、可回放、可联网同步的比赛规则产生。

## 测试与可观测性现状

当前 `java/core/src/test` 没有覆盖 AI 状态转移、阵型目标、接球人选择或主防守人选择的测试。回放帧会保存 `playerAiState`、`isBestDefender` 和 `frameDistance`，见 [`Player.java#L561-L572`](../core/src/main/java/com/ygames/ysoccer/match/Player.java#L561-L572)，持球进攻也已有分类调试日志；这些可以作为后续战术回归测试和可视化审计的基础。

建议最低限度建立以下确定性测试：

- 给定球区和球队朝向，10 名外场球员得到预期目标点；
- 球跨区时目标连续插值，且宽度/紧凑度修正不越界；
- 给定候选人、对手和战术状态，传球评分及选择可重复；
- 给定球权与站位，压迫/保护职责分配稳定且不会抽空防线；
- 同一结构化教练命令在本地、回放和网络端产生一致状态；
- 指令到期或撤销后平滑回到基础阵型，而不是坐标跳变。

## 最终判断

现有代码不是从零开始：阵型目标点、球区感知、球轨迹预测、普通跑动、传射动作、争抢和守门员行为都可以复用。最大的缺口是决策层没有可组合的球队/球员战术状态，且多个看似相关的数据（角色、传球技能、阵型配对、区内偏移）尚未进入对应实时决策。

所以“自然语言场边教练”在当前项目中的正确实施顺序应是：先把静态阵型和硬编码 AI 变成可参数化、可评分、可测试的战术执行层，再接文本解析和语音识别。否则语音即使识别准确，也只能触发整套阵型切换，无法稳定表现“中锋支点、7 号回防、放慢节奏”这些细粒度意图。
