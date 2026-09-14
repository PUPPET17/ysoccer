# 实时比赛 AI 最小战术控制层（第一版 Demo 与阶段化扩展）

本文记录 `Player AI 与战术可控性审计` 之后实现的第一版运行时战术控制层，以及随后加入的轻量、确定性比赛阶段模型。完整的改造前审计、原始状态机清单和历史缺口见 [Player AI 与战术可控性审计](player-ai-tactical-control-audit.md)。

实现基线为 `d1124d09` 之后的 Java/libGDX 实时比赛代码。任务分类是“现有功能扩展 + 战术状态基础设施”。本阶段没有引入语音识别、LLM、网络服务、命令解析器或事件总线。

## 结论

现有 ySoccer AI 可以被持续的教练意图影响，但需要在静态阵型目标与硬编码决策之间补一层偏置。当前实现已经验证四类变化能通过原有 FSM、虚拟输入和普通跑动/踢球动作发生，并能按刚得球、有序控球、刚丢球和稳定防守分别解释同一偏置：

- 队形宽度和线距按时间渐变，球员仍自行跑向新目标；
- 中锋的回撤接应、身后跑、做球和持球推进倾向可以形成差异；
- 单独指定 7 号后，其回防目标、同侧支援、主防守人候选权重和防守反应频率会变化；
- 降低节奏会减少前插和直接动作，传球候选从窄角度“最近可达者”升级为带方向及线路安全评分的选择。

所有参数均为 `bias / preference`。代码没有在收到指令时写 `player.x` 或 `player.y`，也没有绕过 `AiFsm`、`PlayerFsm`、球员速度和足球物理。

## 改造前 AI 调用链审计

### 总循环与双 FSM

```text
MatchStateMain.doActions()
  → Match.updateAi()                         // 64 Hz
    → Team.updateLineupAi()
      → Player.updateAi()
        → InputDevice.update()
          → Ai.read()
            → AiFsm.think()
              → AiState*.doActions()/checkConditions()
                → 写 ai.x0/y0/fire10/fire20

MatchStateMain.updatePlayers()
  → Team.updatePlayers()/updateLineup()      // 512 Hz 子帧
    → Player.update()
      → PlayerFsm.think()
        → PlayerState*.doActions()/checkConditions()
          → 普通跑动、踢球、争抢与物理
```

AI 是虚拟 `InputDevice`，不是直接操纵坐标的控制器。这个边界是战术层可以保持薄且安全的基础。

### 无球站位和阵型目标

改造前的精确链路：

```text
MatchStateMain.updatePlayers()
  → Match.updateTactics()
    → Team.updateTactics(relativeToCenter)
      → 计算 35 区 ball_zone
      → Assets.tactics[team.tactics].target[lineupIndex][ball_zone]
      → 依据 team.side 转成世界坐标 player.tx/player.ty

AiStatePositioning.doActions()
  → Player.targetDistance()
  → Player.targetAngle()
  → 距离 > 20 时写八方向 ai.x0/ai.y0
  → PlayerStateStandRun 读取输入并使用 Player.speed()
```

阵型中心、宽度和纵向距离都不是显式参数，而是 10 个外场目标点隐含形成的几何结果。同一张 `.TAC` 表覆盖有球与无球状态。球的 `mx/my` 虽已存在，旧表达式两端读取同一个目标点，实际上没有区内插值。

### 持球决策

```text
AiStateAttacking.doActions()
  → getUrgentAngleCorrection()               // 防止出界、近门转向
  → getAngleCorrection()
    → getMateWeights()                       // 队友到球预测帧
    → getOpponentWeights()                   // 对手到球预测帧
    → getGoalWeights()/getOwnGoalWeights()   // 球门方向
  → Player.searchPassingMate()               // 每 8 AI 帧

AiStateAttacking.checkConditions()
  → 有接球队友且固定 0.3 随机命中 → AiStatePassing
  → 直接射门区 + 看见球门 + 几何概率命中 → AiStateKicking
  → 否则保持持球和局部三方向带球

AiStatePassing
  → 虚拟动作键短按/释放
  → PlayerStateStandRun.checkConditions()
  → PlayerStateKick
    → 传球初速 240 + 距离修正
```

旧 `searchPassingMate()` 只搜索当前朝向约 ±27.5°，候选还必须能较快到达当前足球预测轨迹，最终取最小 `frameDistance`。它没有向前/横向/回传分类，也没有线路安全或比赛节奏权重。

### 无球进攻选择

旧代码没有独立的“前插、回撤、拉边、靠近持球人”候选行为。除本队最近追球者外，无球队员在 `AiStatePositioning` 中只执行 `.TAC` 目标。阵型表随球区变化会产生间接跑位，但角色、技能和球员个体没有参与选择。

### 防守选择

```text
Team.updatePlayers()
  → Team.findBestDefender()
    → 必须位于足球的本方球门侧
    → 候选中取 ballDistance 最小者
    → 新人至少近 10% 才替换 bestDefender

AiStatePositioning.checkConditions()
  → player == team.bestDefender → AiStateDefending

AiStateDefending.doActions()
  → 每 15..30 AI 帧朝可到达的足球预测点更新方向
  → 接近后由 Player.getPossession() 的通用争抢处理球权
```

非主防守人仍回 `.TAC` 目标；没有盯人实体、补位角色或多人压迫分工。`AiStateDefending` 不会主动触发铲球。

### role、position 与 skill

- `Player.role` 在开放比赛中主要区分守门员；其他角色主要用于名单、替补匹配、显示和自动比分。第一版只在调试入口中用 `ATTACKER + 最接近阵型横向中心` 识别中锋。
- 阵型 position 实际是 `lineupIndex → .TAC target`，不是独立位置对象。
- `speed` 影响普通跑动，`shooting` 影响部分射门/踢球，`tackling` 影响争抢和铲球。
- `passing` 和 `finishing` 仍未进入实时比赛的对应动作质量计算；第一版不顺便重写技能系统。

### 比赛阶段与决策模型

改造前没有 `mentality`、`aggression`、`supportDistance`、`pressing`、`tempo` 或 `tacticalState`。也没有 build-up、final third 或攻防转换状态。第一版只使用足球当前所有者派生出 `本队控球 / 对方控球 / 无明确控球` 三种最小上下文；本轮阶段化扩展已用持续仿真帧和迟滞替换该瞬时判断，规则见下文。

AI 整体仍以 FSM、硬编码条件和固定随机概率为主。持球转向原本已有三个候选方向的权重累加；第一版复用这种风格，并只为战术传球补了确定性的候选评分，没有把整个 AI 改成 utility AI。

## 第一版架构

```text
开发按键 → Team.TacticalState（每场、每队、可恢复）
             ├─ current/target 团队参数（逐帧趋近）
             └─ IdentityHashMap<Player, PlayerTacticalInstruction>
比赛 ball.owner → TeamPhaseTracker（每队、仿真帧、去抖）
             │ coach intent                  │ phase context
             └─────────────────┬─────────────┘
       ┌───────────────────────┴──────────────────────────┐
       ↓                                                  ↓
TacticalPositioning                              TacticalDecisionPolicy
  → 修正 formation target                         → 传球候选评分/行动概率
  → Player.setTarget(tx, ty)                      → 主防守候选距离/反应间隔
       ↓                                                  ↓
AiStatePositioning → 普通跑动                    原 AiState → 虚拟输入 → PlayerFsm
```

### `TeamPhase` 与确定性状态转换

文件：[`TeamPhase.java`](../core/src/main/java/com/ygames/ysoccer/match/TeamPhase.java) 与 [`TeamPhaseTracker.java`](../core/src/main/java/com/ygames/ysoccer/match/TeamPhaseTracker.java)。

阶段是每支球队的视角，不是新的 AI FSM：

| 阶段 | 定义 | 战术解释 |
|---|---|---|
| `DISPUTED` | 尚无一方提供足够连续球权证据，或无主球超过宽限 | 保留基础阵型和中性决策，不把争抢误判为稳定战术阶段 |
| `ATTACKING_TRANSITION` | 本队球权连续确认，但持续时间未达稳定阈值 | 刚得球；保留多数旧 AI 推进倾向，只轻量应用慢节奏，并继续给予中锋支点偏置 |
| `ORGANIZED_POSSESSION` | 本队确认控球至少 48 个 AI 帧 | 有序控球；完整应用 `tempo`、`passingRisk`、`forwardRunRate`、`ballRetention` 和回传偏置 |
| `DEFENDING_TRANSITION` | 对手球权连续确认，但持续时间未达稳定阈值 | 刚丢球；个人回防、同侧支援和主压迫候选偏置最强 |
| `STABLE_DEFENSE` | 对手确认控球至少 48 个 AI 帧 | 稳定防守；保持更深站位，但降低持续追抢强度 |

状态转换规则固定为：新所有者必须连续出现 3 个 64 Hz AI 帧才替换已确认球权；无主球在 32 帧（0.5 秒）以内保留最近确认球权，超过后进入 `DISPUTED`；同一方确认球权累计至 48 帧后才进入稳定阶段。候选球权帧不会覆盖已确认方，因此一次短触球或常见短传飞行不会每帧切换阶段；长时间无人接应的球仍会进入无序状态。

`ownerLast` 没有时间年龄且可能在长时间无主球中一直保留，所以状态机只把当前 `ball.owner` 当作新证据，并把“最近球权及其年龄”维护在自身帧计数中。所有计数只在 `Team.updateLineupAi()` 的仿真 AI 帧推进，不读取系统时间、不消耗随机数。`Team.beforeMatch()` 和 `Team.beforeTraining()` 均清空阶段证据；相同球权帧序列必然得到相同阶段序列。

### `TacticalState`

文件：[`TacticalState.java`](../core/src/main/java/com/ygames/ysoccer/match/TacticalState.java)

团队级当前值和请求值：

| 参数 | 默认 | Demo 值 | 含义 |
|---|---:|---:|---|
| `width` | 1.00 | 0.72 | 围绕阵型中心缩放横向目标 |
| `compactness` | 1.00 | 1.35 | 作为纵向线距的逆缩放值 |
| `tempo` | 1.00 | 0.65 | 直接传/射决定的耐心偏置，不改物理速度 |
| `passingRisk` | 1.00 | 0.55 | 向前传球与向球门推进偏置 |
| `forwardRunRate` | 1.00 | 0.55 | 本队控球时高级目标的前插深度 |
| `ballRetention` | 1.00 | 1.45 | 队友支援、横传/回传和持球耐心偏置 |
| `backPassPreference` | 1.00 | 1.60 | 回传候选的额外评分 |

团队值以每秒最多 `0.30` 的速度接近请求值。`resetShape()` 与 `resetPossessionProfile()` 同样渐变；`resetAll()` 还清除个人指令。状态属于 `Team` 的比赛运行时对象，`beforeMatch()` 会立即重置，且不写入球队 JSON。阶段模型只决定偏置的生效强度，不改变这些请求值。

### `PlayerTacticalInstruction`

文件：[`PlayerTacticalInstruction.java`](../core/src/main/java/com/ygames/ysoccer/match/PlayerTacticalInstruction.java)

| 类别 | 字段 |
|---|---|
| 个人防守 | `defensiveWorkRate`、`trackingBack`、`defensiveDepth`、`pressSupport` |
| 前锋支点 | `comeShort`、`holdUpPlay`、`runBehind`、`layoffPreference` |

映射以 `Player` 身份为键，不以阵容索引或号码为键。这样换人或重排阵容不会把指令错误转移给另一名球员；号码只负责在按下调试键时解析目标。

## 四条指令的实际代码路径

### 收紧队形

```text
TacticalState.setShape(0.72, 1.35)
  → Team.updateLineupAi() 中渐变 current value
  → Team.updateTactics()
    → 计算当前 10 名外场目标的 formation centre
    → TacticalPositioning.targetX(): width 缩放
    → TacticalPositioning.targetY(): compactness 缩短纵向距离
    → Player.setTarget()
  → AiStatePositioning
  → PlayerStateStandRun 普通跑向目标
```

修改点在 [`Team.java`](../core/src/main/java/com/ygames/ysoccer/match/Team.java) 与 [`TacticalPositioning.java`](../core/src/main/java/com/ygames/ysoccer/match/TacticalPositioning.java)。它修改的是目标，不是球员坐标。

### 中锋多做支点

无球时：

```text
comeShort ↑ + runBehind ↓
  → TacticalPositioning.targetY()
  → 有序控球时完整回撤；刚得球时保留 55% 的支点偏置，避免强制放弃反击
```

持球时：

```text
holdUpPlay + layoffPreference
  → AiStateAttacking.getAngleCorrection()
      队友方向权重 ↑、球门方向权重 ↓
  → Player.searchTacticalPassingMate()
      横向/回传做球候选加分
  → TacticalDecisionPolicy.passingProbability()
      做球概率 ↑
  → TacticalDecisionPolicy.shootingProbability()
      仓促直接射门概率 ↓
```

旧 AI 没有侧向/回传接球人机制，所以 [`Player.java`](../core/src/main/java/com/ygames/ysoccer/match/Player.java) 新增了最小的方向与传球线路评分；[`AiStatePassing.java`](../core/src/main/java/com/ygames/ysoccer/match/AiStatePassing.java) 只在战术传球启用时先通过普通八方向输入转向，再短按传球。默认状态仍走旧窄锥搜索和旧按键时序。

### 7 号多参与防守

```text
Team.findPlayerByNumber(7)
  → TacticalState.addPlayerInstruction(actualPlayer, defendMore)
  ├─ 对方控球：TacticalPositioning
  │    defensiveDepth/trackingBack → 目标更靠近本方球门
  │    pressSupport → 同侧时目标向球和球门侧支援点靠拢
  │    刚丢球使用 1.20 倍恢复/压迫影响；稳定防守降低持续追抢影响
  ├─ Team.findBestDefender()
  │    defensiveWorkRate/pressSupport → effectiveDefenderDistance 降低
  └─ AiStateDefending
       defensiveWorkRate/pressSupport → 追球方向更新间隔缩短
```

这不会强制 7 号上抢：原有“必须位于球门侧”和候选切换迟滞仍然有效。个人偏置不会影响其他队员。

### 缓一下节奏

```text
TacticalState.setPossessionProfile(0.65, 0.55, 0.55, 1.45, 1.60)
  ├─ TacticalPositioning.targetY()
  │    有序控球时高级无球目标的 forwardRunRate 完整下降
  │    刚得球时只应用 20%，保留转换推进机会
  ├─ Player.searchTacticalPassingMate()
  │    forward pass 分数 ↓
  │    side/back pass 分数 ↑
  │    传球线路安全权重 ↑
  ├─ AiStateAttacking
  │    直接传球概率/直接射门概率 ↓
  │    队友方向权重 ↑、球门方向权重 ↓
  └─ AiStatePassing
       允许通过普通转向完成横传和回传
```

没有修改 `Player.speed()`、跑动动画速度或球速；“慢”来自决策风险和跑位频率，而不是所有人机械降速。

## Demo 调试入口

原项目的 `F1`–`F6` 已分别用于帮助、音量、解说、观众声和全屏，所以 Demo 使用等价且不冲突的开发组合键。入口位于 [`MatchHotKeys.java`](../core/src/main/java/com/ygames/ysoccer/match/MatchHotKeys.java)，仅在现有 `Settings.development=true` 时启用。

| 按键 | 行为 |
|---|---|
| `Ctrl+1` | 收紧队形 |
| `Ctrl+2` | 恢复默认队形参数 |
| `Ctrl+3` | 当前最居中的 `ATTACKER` 加强支点行为 |
| `Ctrl+4` | 当前场上球衣号码 7 加强防守参与 |
| `Ctrl+5` | 降低比赛节奏 |
| `Ctrl+6` | 恢复全部默认战术并清除个人指令 |

优先作用于 `PLAYER` 或 `COACH` 控制的球队；纯电脑对战作用于主队。若阵容中没有 `ATTACKER` 或场上没有 7 号，HUD 消息会明确提示，不会把指令错误应用给其他人。

## 调试可观察性

触发任一 Demo 指令会自动打开现有 development HUD；也可以用原有 `F12` 开关。新增 HUD 位于 [`MatchRenderer.java`](../core/src/main/java/com/ygames/ysoccer/match/MatchRenderer.java)，显示：

```text
TacticalState [HOME/AWAY] | phase + phase age
width current -> target | compactness current -> target
tempo | passingRisk | forwardRun | retention | backPass
actualSpread | lineGap | forwardRun ratio
phase seconds O/T/D/U | switches/short
current-phase pass ratios F/S/B, baseline -> current
Player #<centre-forward> holdUp | comeShort | runBehind | layoff
Player #7 defensiveWork | tracking | depth | pressSupport
```

`TacticalDebugMetrics` 每个 AI 帧被动采样球员真实坐标、已生成的跑位目标和当前阶段，并在 `PlayerStateKick` 真正进入传球分支后记录一次不可变传球观测。每条传球观测包含阶段及阶段年龄、接球队员距离、实际出脚方向、0..1 线路安全估计、7 个团队战术当前值，以及传球队员的防守、支点和做球偏置；无显式接球人的手动传球用 `NaN` 表示无法可靠推导的距离和线路安全，不伪造数据。

当前窗口和上一比较窗口都按阶段保存 AI 帧数、前传/横传/回传计数及比例，同时记录阶段切换次数，以及已结束且少于 8 帧的异常短阶段数。每次按下 `Ctrl+1`–`Ctrl+6` 都会把当前窗口保存为基线，再开启新窗口。HUD 只显示当前阶段/阶段年龄、O/T/D/U 阶段时间摘要、切换/短状态计数、当前阶段的前横回比例、队形趋势和两名目标球员偏置；完整逐传数据保留在收集器中，不继续堆到屏幕上。遥测结果从不反馈给 AI 决策。

这能同时观察请求值、渐变中的当前值、阶段上下文、两类个人偏置和真实动作趋势。当前实现没有增加球员头顶标签，避免扩大渲染改动。

## 自动验收

无图形测试 [`TacticalStateRegressionTest.java`](../core/src/test/java/com/ygames/ysoccer/match/TacticalStateRegressionTest.java) 直接调用生产环境中的 `TacticalPositioning` 与 `TacticalDecisionPolicy`，验证的是输出趋势而非字段赋值。运行：

```powershell
.\gradlew.bat :core:tacticalStateRegressionTest
.\gradlew.bat :core:teamPhaseRegressionTest
```

本次结果：

```text
Shape metrics: horizontal 172.0 -> 123.8, line separation 450.0 -> 333.3
Hold-up target: -220.0 -> -114.5, pass probability 0.300 -> 0.600
#7 defense target Y: -50.0 -> 77.6, effective pressure distance 200.0 -> 165.7
Pass scores forward 2.31 -> 1.77, side 1.81 -> 2.89, back 1.81 -> 3.52
Tactical state regression checks passed: 37

Phase-aware trends: run target transition -200.2 vs organized -121.0,
forward score 2.20 vs 1.77, #7 recovery 111.6 vs stable 77.6
Team phase regression checks passed: 48
```

验证覆盖：

- 收紧后平均横向距离下降 28%，三线总纵向距离下降 26%；请求与恢复都逐帧趋近；
- 支点目标明显回撤，队友方向与做球倾向提高，向球门转身和仓促射门倾向降低；
- 7 号的个人回防目标、同侧支援、主压迫候选和防守反应间隔均改变，且不泄漏给队友；
- 慢节奏下前插目标回收，向前传球评分降低，横传/回传评分提高，直接传射概率降低；
- 已执行传球能够按实际球路分类为前传、横传和回传，并记录传球前连续控球时间；
- 全部个人指令可恢复默认。
- 固定球权序列准确覆盖刚得球、有序控球、刚丢球、稳定防守和长时间无主球边界；
- 2 帧短暂异方触球与 32 帧无主球不会触发切换，3 帧连续异方球权才确认转换；
- 新比赛和训练均清除旧阶段；相同种子与输入的阶段序列、逐阶段传球计数和逐传快照完全一致；
- 慢节奏下前插目标从刚得球的 `-200.2` 收到有序控球的 `-121.0`，前传评分从 `2.20` 降至 `1.77`，生产 `Player.searchTacticalPassingMate()` 也从刚得球选择前方接应者变为有序控球选择回传接应者；7 号的回防目标在刚丢球时为 `111.6`，稳定防守时为 `77.6`。这些是生产站位和接球人决策变化，不是仅检查阶段字段。

同时执行了既有 `playerControlRegressionTest`，4 项通过，确认战术防守接入没有重新混淆抢球动作与手动换人。`TacticalStateRegressionTest` 仍保持原 37 项验收，不用新增阶段测试掩盖第一版回归。

## 修改/新增文件

| 文件 | 责任 |
|---|---|
| `TacticalState.java` | 每场每队的当前/目标战术状态与个人指令表 |
| `PlayerTacticalInstruction.java` | 不可变的球员级偏置 |
| `TacticalPositioning.java` | 基础阵型目标到战术目标的纯计算 |
| `TacticalDecisionPolicy.java` | 传球、传射时机、防守候选与反应间隔评分 |
| `TacticalDebugMetrics.java` | 开发模式下被动记录真实队形、跑位、传球和个人防守趋势 |
| `TeamPhase.java` / `TeamPhaseTracker.java` | 阶段词汇与基于球权仿真帧的去抖、迟滞状态机 |
| `Team.java` | 持有状态、生成战术站位、解析目标球员、偏置主防守人 |
| `Player.java` | 方向与线路安全感知的战术接球人搜索 |
| `AiStateAttacking.java` | 读取节奏/支点偏置，改变传、带、射倾向 |
| `AiStatePassing.java` | 战术横传/回传的普通控制转向时序 |
| `AiStateDefending.java` | 球员级防守反应频率 |
| `PlayerStateKick.java` | 保留 AI 已选择的战术接球人 |
| `MatchHotKeys.java` / `Match.java` | 六个开发指令及目标球队选择 |
| `MatchRenderer.java` | 战术 HUD |
| `TacticalStateRegressionTest.java` / `core/build.gradle` | 行为趋势验收及 Gradle 任务 |
| `TeamPhaseRegressionTest.java` | 固定球权帧场景、生命周期、确定性、阶段遥测与阶段化行为趋势验收 |

## 第一版仍不能自然表达的行为

1. **真正盯人**：当前没有“7 号跟随某个对方球员”的分配模型。Demo 做的是同侧球权支援、回防和更高的主压迫候选权，不伪装成实体盯人。
2. **严格背身护球动画/身体模型**：`holdUpPlay` 目前通过回撤目标、队友/球门方向权重和快速做球来近似；没有专门的背身护球状态或身体对抗动作。
3. **空间进攻阶段**：当前已有球权时间阶段，但仍没有按球场区域细分 build-up、middle third 或 final third，也没有把阶段扩成完整 utility AI。
4. **完整 utility AI**：只有战术传球新增候选评分；带球、射门和防守仍大部分是既有硬条件与局部权重。
5. **传球技能质量**：`skills.passing` 仍不影响实时传球误差/力度；本 Demo 只验证战术选择，不改变球员能力模型。
6. **真实比赛长样本统计**：HUD 已记录单个观测窗口的真实逐阶段 forward/side/back pass 和跑位比例，但当前自动验收使用可重复的仿真帧场景 runner，不是完整无头实时比赛运行器，因而不能给出具有统计显著性的多场差异。自动比分 `MatchScoreSimulator` 没有 Player AI/FSM/球物理，明确不作为替代验证。
7. **网络与回放同步**：状态未序列化到网络快照或回放。第一版是本地开发验证层，不宣称联机确定性。

## 下一阶段建议

在接文字或语音之前，建议按以下顺序继续：

1. 建立可批量运行的固定种子完整实时比赛 harness，用真实 FSM/球物理对逐阶段比例做长样本统计；不要复用自动比分模拟器。
2. 把无球支持从单一目标修正扩成少量可评分候选：保持阵型、靠近、回撤、前插、拉边。
3. 在不强制球员的前提下增加主压迫、协防、保护三种防守职责；之后才能实现真正的指定盯人。
4. 明确中锋身份或球员职责，避免长期依赖 `ATTACKER + 横向最居中` 的启发式。
5. 如真实比赛数据证明有必要，再按球区加入 build-up/final-third 子上下文，避免仅凭设计预先扩张状态数。
6. 完成这些确定性底层能力后，再定义结构化 `TacticalCommand` 与文本解析；语音仍应是最后一层输入适配。

当前验收结论：不依赖语音或 LLM，仅修改 `TacticalState` 已能让真实比赛 AI 的目标站位、接球人选择、持球方向、传射时机和个人防守参与产生可测试、可观察且可恢复的变化；同一偏置现在还会依据确定性的球权阶段产生不同强度，而中性状态继续走旧 AI 分支。
