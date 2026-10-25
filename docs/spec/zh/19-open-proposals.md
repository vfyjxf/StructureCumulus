# 19 — 开放设计提案（不纳入内核）

> 这些是第一轮刻意排除的特性。它们连同最小 API 形状和阻塞它们的决策一并记录在此，
> 以免后续轮次重新发现它们。本文件中的任何内容都未冻结；一项提案只有通过修改
> `01-glossary.md` 和所属单元，才能成为模块。

## 对账记录（2026-10-20）

十二份单元草稿是各自独立写成的。本轮统一了跨单元接口面（cross-unit surface）；模块
内部的草图可能仍具示例性质，但下列名字和形状现在是规范性的（canonical）。

| # | 裁决 | 涉及文件 |
| --- | --- | --- |
| R1 | `SourceSpan` 在 `kernel.base` 中是 `(NamespacedId document, int start, int end)`；`space` 只依赖 `base`，且 `Label` 携带一个 `Locus`（其 `source` 就是唯一的跨度），而不是第二个跨度。 | `01`, `05`, `09`, `13` |
| R2 | 门面的 `validate` 变为 `compile(BlueprintDocument) -> CompileResult(findings, @Nullable Blueprint)`。它属于 DESIGN 阶段，是唯一的文档→蓝图路径；操作数是九个，且计入 `settle`（`19` R31）。 | `00`, `02`, `04`, `10`, `15` |
| R3 | `DocumentValidator.of(library, expectations)` 与 `SegmentAssembler.of(library, expectations)`；`SegmentLibrary.Builder.put(SegmentDocument, Map<NamespacedId, ExpectationFactory>, issues)` 与 `SegmentAssembler.compile(SegmentDocument, issues)` 负责声明→`Segment` 这一步。用 `Map` 而不是 `Catalog`，使 U6 不依赖 U11，并保持包图无环。 | `10` |
| R4 | 逐元素的作者位置区间以 `Blueprint.sourceOf(NamespacedId): @Nullable SourceSpan` 传递；`BlueprintBuilder.source(...)` 负责盖印（stamp）。`DocumentProvenance` 保持 U6 局部，因此 `blueprint` 永不导入 `document`。 | `09`, `10` |
| R5 | `Finding` 携带 `FindingId id`；新增 `FindingCode.of(String)` 与 `Finding.Builder.label(Locus, LabelRole, String)`。 | `13`, `08` |
| R6 | `ExpectationContext` 与 `ProbeSession` 暴露 `stage()`；生产者在每条诊断项上盖印 `.stage(ctx.stage())`。 | `07`, `04`, `13` |
| R7 | `MatchResult` 的每个分支都携带 `Stage`。`SETTLE` 之前的 `Formed` 是阶段结果；I-10 适用于已结算的单元格，只有处于 `SETTLE` 的 `Formed` 才是结构。**已被 R27/R28/R31 取代。** | `00`, `11`, `04`, `18` |
| R8 | `OccupantRef` 包含其 `SnapshotId`；`OccupantSnapshot` 没有载荷；`SnapshotPolicy` 携带 `requiredTraits`；`Traits` 是保留的已知键容器。被已接纳占用体覆盖的单元格是 `READABLE`，即使它自身的读取被拒。 | `01`, `06`, `16` |
| R9 | `ClaimLedger` 是 `settle(SettleRequest)` 与 `read(Cuboid) -> ClaimView`；视图回答 `rows()`、`revision()` 与 `available()`，其中 `available() == false` 表示“无法回答”，绝不是空声明集。**已被 R27/R28/R31 取代；`23` §5 新增 `release`。** | `01`, `08`, `15`, `16` |
| R10 | `DerivationSpec.order` 唯一且非负，但不要求稠密；U8 按 `(order, cost, id)` 排序。增益由宿主提供；`Blueprint` 不把 `Augment` 烘焙进模板。 | `09`, `12` |
| R11 | `MatchRequest` 是 `(ticket, blueprint, siting, stage, lease, policy, budget)`；安放位在请求存在之前就已解析；`MatchResult` 是 `Admitted`/`Formed`/`Failed`/`Undecided`/`Deferred`。**已被 R27/R28/R31 取代。** | `01`, `11`, `04`, `15` |
| R12 | 为 `Axis`、`ReadStatus`、`ResolveContext`、`OccupantResolver<R>`、`Traits`、`RenderContext`、`SourceText`、`CompileResult`、`Place` 增加了术语表条目；修正了 `OccupantRef`、`Finding`、`Label`、`DataView`、`ClaimLedger`、`MatchResult`、`CheckPlan`。 | `01` |
| R13 | 黄金示例现在使用 `OrientationPolicy.builder(role).allowTurns(...)`、声明式 `derive(id, Arguments)`、`kernel.compile`，以及显式的派生预算。7×6×7 外壳的内部体积是 100 个单元格。 | `04`, `18` |
| R14 | `DataView`/`DataKind` 移到 `kernel.base`。于是 `ExpectationFactory.create(DataView, ...)` 不闭合任何环：U6 可以依赖 U3，而 U3 只依赖 base。 | `01`, `03`, `10` |
| R15 | 适配器类去掉机械的 `Mc` 前缀；`mc` 包本身就是命名空间：`LevelCapture`、`LevelSnapshot`、`WorldOccupants`、`OccupantDescriptors`、`OccupantTraits`、`OrientationMapping`、`SavedClaims`、`WorldEdits`、`ClientOverlay`、`OverlayPayload`、`DatapackBlueprints`、`FindingOutput`、`ServerState`、`JsonFrontend`、`JsonBridge`。 | `01`, `02`, `03`, `06`, `16` |
| R16 | 拒绝 tick 切片。`StructureKernel.match` 保持同步且纯净；宿主 `MatchScheduler` 拥有一个有界工作线程池。没有 `begin`/`advance`，没有 `TimeSource`，没有 `nanos`。 | `02`, `11`, `16`, `18`, `20` |
| R17 | 快照按区段组织：冻结的 `PalettedContainer` 副本、`SectionSlot` 的 `Air`/`Present`/`Refused`、只存载荷格；共享 `SectionStore` 是可选项，默认关闭。捕获是一次服务端线程调用；`getChunkNow` 从不加载。 | `06`, `16`, `20` |
| R18 | 脏状态按区段/载荷/实体划分；在 `LevelChunk.setBlockState` 与 `Level.blockEntityChanged` 上挂钩（mixin）；一次性的服务端线程刷新；`changeCount` 参与快照核对。 | `02`, `16`, `18`, `20` |
| R19 | 按区段分块的检查不是一种调度切分。区段 `Computation` 按区段运行，提交屏障按规范顺序折叠贡献，随后聚合单元与检查各运行一次。`ChunkGrid` 仍然是空间工具。 | `06`, `07`, `11`, `16`, `20`, `22` |
| R20 | 每个 `(world, owner)` 至多一个在飞任务；较新的请求按提交 ticket 合并；`AUTO` 被推迟，并由调度器重新探测；`INTERACTIVE` 获得保留配额。保留的 `InstanceRecord` 丢弃快照视图。 | `02`, `16`, `20` |
| R21 | 默认逐快照持有区段副本；共享 `SectionStore` 是 opt-in，只有在 profiling 显示重叠捕获或大区段重复复制是热点时才启用。 | `20` |
| R22 | 实体移动不由逐 tick 的 `setPos` 挂钩跟踪。join/leave 标记足迹；每个膨胀后的窗口都是一个受监视的区域，对其现存实体集做有界的逐 tick 差分就是移动的见证。 | `16`, `20`, `22` |
| R23 | `AUTO` 只跑 `PROBE`；通过后由调度器另投 `FORM`。`COMMAND`/`INTERACTIVE` 可以链式跑完整阶段。 | `16`, `20` |
| R24 | 并行区段计算是 `MatchPolicy.parallel`。折叠顺序是规范的，因此结果不变；不再有仅限 `AGGREGATE` 的限制。 | `18`, `20`, `22` |
| R25 | `ReadEnvelopeAware` 已删除。可缓存性是 `(unit, closureDigest)`，条目存储贡献或单元结果，绝不存储折叠后的值。 | `01`, `07`, `20`, `22` |
| R26 | 紧凑作者格式是普通 JSON profile，不是 DSL。它是数据包一等格式（`cumulus/structure/*.json`），以 `DocumentProfile.AUTO` 加载，并保留 `shell`/`cavity`/`fills`/`bays` 为独立顶层字段。capture/import 优先输出该 profile。 | `10`, `16`, `21` |

### 第二遍（翻译审阅）

中文译者标出了英文原文中的内部矛盾；这些矛盾都经过核对并在两种语言中一并修正，而不是
作为翻译注记保留。

- **U9** —— §2 现在写 9 个 `Finding` 组件、3 个组件的 `Label`（跨度位于 `Locus`）和
  3 个组件的 `SourceSpan`；`Locus.withSource` 是唯一的跨度设置器；`Label.source` 与
  `withSource` 已删除。
- **U5** —— 姿态选择使用 `candidate.implied().apply(Heading.NORTH)`；`build()` 对
  B-1…B-10 以及 `allowed` 前置条件抛出；`SEAM` 属于 `FORM` 种类。
- **U14** —— `BindAnchor` 有后置条件；`Configure` 的占位符短语已补全；支撑检查使用
  自己的 `0206`，而不是 `0202`。
- **U10** —— `SC-DOC-0301` 只适用于内容*不同*的重复键。
- **U11** —— `step.cost` 是调度下界，从不实际扣费；预算按每次读取授权。
- **U16** —— `ClaimSettlement` 引用指向 U4 OQ-4。
- **U18** —— 已结算的 `Formed` 需要 `CLAIM` 步骤；I-5 说的是 `Siting` 由锚点与策略导出，
  而不是说二者就是 `Siting` 记录。
- **U00/U02** —— 十二个单元、三道门禁、两个生产源集及其测试源集。
- **U17** —— `Integer tier`、铁栏杆炉口、`SC-REM-` 诊断码，以及 1×2×1 的两格门。
- **U05** —— `toLocal` 为 `@Nullable`；仿射复合为 `(π₁∘π₂, s₁ + π₁(s₂))`。

对账之后仍待决的问题（负责人见各单元文件）：附属代码命名空间（`OQ-6`，U9/U11）、
作为内核类型的 `ClaimSettlement`（`16` Q-3）、`FindingOptions` 的严重级别下限（`OQ-5`，U9）、
`ExpectationMeta` 中的普查需求（`11` Q-3，U3）、`SegmentFamily`（`09` Q5-3），以及
`Seam.required`/向外朝向（`09` Q5-3）。第三轮残留（`22` §16.1）按同样的条件保持开放。
声明行的世界身份曾以“（`16` OQ-4）”列在这份清单上，而那一行从未存在；它改为被裁决——见 R35。

### 第三遍（第三轮）

第三轮的这一遍连同 `23` 与 `24` 一起攻击修订版 5，从三条战线外加一次规范对代码的审计
入手。报告是 `_work/round3/a-semantics.md`、`b-types-data.md`、`c-runtime.md` 与
`d-drift.md`；牵头人裁决如下。这一遍*没有*决定的是 `22` §16.1。

| # | 裁决 | 涉及文件 |
| --- | --- | --- |
| R27 | 两个入口：`check` 只读、不写入任何内容，`settle` 是唯一的写入者。`MatchResult.Admitted` 携带 `@Nullable ClaimPlan planned` 与 `long ledgerRevision`；`MatchResult` 有六个分支，含 `Dropped`，且每个分支携带自己的 `Stage`；没有 `MatchRequest` 携带阶段，且 `Stage.STALE` 已删除。宿主的 `CheckRequest` 阶段列表是 `22` §16.1 第 16 项的问题。 | `22`, `23`, `00`, `01`, `03`, `04`, `08`, `09`, `10`, `11`, `15`, `16`, `17`, `18`, `20` |
| R28 | 计划类型是 `ClaimPlan`。`23` §2 的 `CheckResult.Plan` 与逐检查的 `CheckResult` 冲突。 | `23`, `01` |
| R29 | worker 的终值是 `MatchCompletion`；`MatchOutcome` 只是这次尝试的产物。 | `01`, `20` |
| R30 | 诊断码分配，且没有任何码被重新指向：`SC-CLM-0003` 保留 `08` §4.6 的含义，`SC-CLM-0004` 是无法应答或移动了两次的账本，`SC-CLM-0005` 是一次对其请求绑定无效的结算（`19` R36）——它在这里被分配为“不匹配其请求的计划”，而 R36 表明那没有载体；其含义转移到可检查的东西上，且该码不因任何其他用途被复用——`SC-CLM-0006` 是原先借用了 0003 的那条 INFO 行，`SC-BAY-0107` 只对应 R3，而 `SC-CTX-0202`/`SC-CTX-0301` 在 `13` 中注册。账本相关的情形绝不使用 `SC-ENG-0003`/`SC-ENG-0004`。 | `13`, `08`, `12` |
| R31 | 门面有九个操作。补救操作是 `solve`，因此 `15` 的 `repair` 被撤回；`MatchPolicy` 是 R34 固定的三分量记录；`MatchRequest` 既不携带 `Place` 也不携带阶段。 | `00`, `01`, `02`, `03`, `04`, `15`, `20`, `22` |
| R32 | 一个名字一个定义：一个 `Footprint`（`Box`/`Bound`/`Declared`/`None`），一个暴露 `blocks()` 的 `Sharing`，`ClaimRow` 的组件名为 `sharing`，一个 `Focus<T>` 成员集，没有 `ReadEnvelopeAware`，而 `Coverage` 只属于舱位 —— `SnapshotPolicy` 同名的旧枚举改为 `CaptureMode`（`EXACT`、`REQUIRED`）。 | `01`, `06`, `08`, `09`, `10`, `16`, `21` |
| R33 | 缺口账本 `docs/status.md` 已删除，且无论是文档还是源码注释，都不得指向它或 `TODO(GAP-xx)` 标记。 | `docs/`, `src/` |
| R34 | `MatchPolicy` 是 `(int maxSectionsPerUnit, CacheMode cache, boolean parallel)`，别无其他：`FailureMode`、`StopPolicy` 与 `deriveOnForm` 只被它的术语表行声明过，现予撤回，因为没有任何组件可以改变判定、诊断包或 `Spend`（`11` §4.7）。 | `01`, `03`, `07`, `22` |

### 第四遍 —— 设计裁决（the design rulings）

在处理残留项、逐项推进的过程中作出的裁决。每一条都是决策，而不是一次清扫。

| # | 裁决 | 涉及文件 |
| --- | --- | --- |
| R35 | **声明行命名它的世界。** `ClaimRow` 携带它的 `World`；账本句柄绑定到一个世界（`World world()`，因此 `revision()` 按世界计）；`SettleRequest` 携带世界，且每一行都必须与它一致（`SC-CLM-0007`）；`ClaimMatrix.conflicts` 先比较世界，因此两个世界中的相同坐标是两个不同的单元格；`OwnerId` 失去它的世界。内核词汇是 `world`，Minecraft 侧的是 `level`（`ServerLevel`、`LevelEvent.Unload` 按原样引用），而声明空间旧的“两个维度”就是那两个 `axes`。 | `01`, `08`, `09`, `11`, `13`, `15`, `16`, `19`, `20`, `22`, `23`, `24` |
| R36 | **计划不携带任何行。** `ClaimPlan.Matched` 是 `(long ledgerRevision, List<BayBinding> bindings)`；`ClaimPlanner.derive(...)` 是唯一的行工厂且是纯净的；`settle` 自己运行它，在此之前对每个绑定做结构检查——本蓝图的一个舱位、**本次**捕获的主体（`OccupantRef.snapshot`）、相符的种类、落在舱位内的单元格（对 `ONE_SUBJECT` 要求完全相等）——失败即 `SC-CLM-0005`。`settle` 不会重新运行期望；那是 `check` 的判定，且捕获门禁把世界钉在它上面。本层没有 `PlanDigest`（既然没有行，就没有什么需要认证），不同于 `14` 的编辑计划，后者跨越一次人工决策。新不变量 `00` I-16。 | `00`, `01`, `08`, `11`, `13`, `19`, `20`, `22`, `23` |
| R37 | **捕获核对读取租约，而槽位的 ticket 不是它的事实之一。** `SnapshotInfo` 已删除：`MatchRequest` 携带租约，并为调度器携带一个可选的 `Ticket`。`CaptureGate.evaluate(lease)` 在 `changeCount() != 0` 时给出 `CHANGED`，在 `!isValid()` 时给出 `SUPERSEDED`；`check` / `settle` 不接收供给器（supplier），因此宿主无法用一个新捕获来回答。`MatchResult` 是 `AutoCloseable`，在不持有句柄的分支上 `close()` 是空操作。槽位合并绝不关闭一个产生过 `Admitted` 的租约；`settle(MatchRequest, Admitted)` 在每条路径上关闭该句柄，而从不结算的调用方自己关闭它。`SC-ENG-0005` 已退役（取代被报告为 `MatchCompletion.Discarded`），`SC-ENG-0006` 覆盖 `CAPTURE_INVALID`。 | `01`, `04`, `11`, `13`, `15`, `16`, `17`, `19`, `20`, `22`, `23` |

## P-1 — 单次尝试内共享的计算状态（Computed state shared inside one attempt，已接受）

**问题。** `FORM` 或 `SETTLE` 谓词可能需要一个派生而非读取的值：熔炉的内部占用情况、某个
普查比例、声明的内部区域是否真的为空。没有共享，同一个 100 格内部区域会按每个消费者扫描
一次，而合法性谓词根本看不到该值。

**解决。** 已接受，并在 `22` §6.4、§7 与 §8 中规定，不作为独立单元。只有一种键类型：
`ContextKey<T>` 携带 `merge`，非空的 `merge` 把该键标记为由区段贡献。区段 `Computation` 每个
区段调用一次 `ContextDraft.contribute(key, partial)`；提交屏障按 `SectionKey` 排序贡献，并用
`Merge.combine` 自左向右折叠；聚合与检查用 `ContextView.require(key)` 读取折叠后的值，检查则
通过 `Focuses.context(key)` 到达它。`Retention.CONTEXT` 把该键发布进该阶段的层级框架。被拒绝
的区段使该键缺席，它以 `Outcome.Missing` 到达焦点并变成 `UNREADABLE`。

先前的草图（`ContextKey` + `Deriver` + `ProbeSession` 中的 `computed(key)`）已退役：它把读取
放在会话便利方法之后，并给提升安排了第二个写者。`Deriver` 保留为仅上下文的生产者（`22`
§6.4）。

## P-2 — 跨实例结构图（Cross-instance structure graph）

**问题。** 两个独立摆放的结构可能相邻、共享接缝，或彼此依赖对方的声明（“这面墙是
我的；邻居不得在此建造”）。内核只建模一个蓝图在一个安放位上的情形；它没有两个已成型
实例之间的边。邻居添加或移除独占声明时，除非被改动的单元格位于本实例的 `ReadSet`
中，否则不会使本实例失效。

**形状。**

```java
record InstanceEdge(InstanceRef from, InstanceRef to, SeamKind kind, Locus contact) {}
interface InstanceGraph {
    List<InstanceRef> neighbours(InstanceRef instance);
    List<InstanceEdge> edgesOf(InstanceRef instance);
}
```

`InvalidationIndex.affectedBy(ChangeSet)` 获得第二个来源：某条边的接触单元格上的声明
变更。图由宿主拥有；内核只消费它。

**为何推迟。** 它需要持久化方案（宿主已拥有 `RuntimeState`），并且有把内核变成世界级
注册表的风险。`Seam` 与声明行模型已携带足够的身份，可以在内核之外构建它。

## P-3 — 读取需求通道与预取（A read-demand channel and prefetch）

**问题。** `ReadStatus` 告诉谓词某个单元格*是* `NOT_CAPTURED` 还是 `DENIED`，但不告诉它
回答该问题*还需要什么*。能重新捕获缺失区域的宿主没有机器可读的请求。

**形状。** 由 `MatchRequest` 携带、或在 `Undecided` 中返回的 `CaptureHint`：

```java
record CaptureHint(CuboidSet missing, Set<TraitKey<?>> traits, int priority) {}
```

宿主可以用它在下次尝试时捕获更大的窗口。内核从不捕获，也从不因一个提示而阻塞。

**为何推迟。** 它是宿主优化而非语义；`ReadStatus` 已经保持了正确性。第一轮让
`Undecided` 保持诚实，而不是可操作。

## P-4 — 参考调度器（A reference scheduler）

**问题。** 宿主必须决定何时捕获、何时匹配、如何把工作分散到多个 tick，以及如何排队
陈旧重检。每个宿主都会重新实现这些。

**形状。** 宿主侧的 `StructureScheduler`，带每 tick 预算、`MatchRequest` 队列和挂起/
恢复协议。它**不是**内核代码：它持有 `StructureKernel`、`WorldCapture` 和一个时钟；
内核保持纯净。

**为何推迟。** 用户要求的是内核，而不是服务端模组。适配器章节（`16-adapter.md`）给出
了参考循环；独立调度器是后续的宿主库。

## P-5 — 附属代码与族分配（Addon code and family allocation）

**问题。** `SC-<FAMILY>-<NNNN>` 稳定且便于人工检索，但两个附属可以铸出同一个码，而新增
族会改变一个封闭枚举（U9）。

**选项。**

1. 保留封闭族枚举；每个族把 `0001–0999` 分配给内核、`1000–9999` 分配给附属，并维护
   一张经评审的注册表。简单，但两个未经评审的附属可能冲突。
2. 为族加命名空间：`SC-<family>-<ns>-<nnnn>`。无冲突，但更长，且不具备 rustc 的风格。
3. 保留枚举，并为 `FindingCode` 增加 `NamespacedId` 所有者；内核渲染 `SC-EXP-0001`，
   附属渲染 `SC-EXP-mymod-0001`。这属于格式变更。

第一轮采用选项 1，并把冲突风险记录在 `13-findings.md`；负责人应在附属生态出现之前
作出裁决。

## P-6 — 面向玩家的文本本地化（Localisation of player-facing text）

**问题。** 第一轮中 `Finding.message` 是英文散文；玩家看到的是很长的编译器风格消息。
宿主要求简短、已本地化的字符串。

**形状。** `FindingCode` 已携带身份；一张以 `(code, args)` 为键的 `FindingText` 本地化
表可以渲染简短形式，而 `FindingRenderer.human()` 为日志和 `/cumulus explain` 保留长
形式。内核不随附语言目录（locale catalogue）。

**为何推迟。** 它是宿主关注点，不改变诊断项的值。

## 提案如何毕业（How a proposal graduates）

1. 它在 `01-glossary.md` 中获得一行，带负责人和稳定性取值。
2. 其负责人撰写一份九节模块文件，并引用它所扩展的不变量。
3. `18-verification.md` 中的反例目录至少为它增加一行。
4. 评审者检查没有既有不变量（尤其是 I-4、I-9、I-15）被削弱。
