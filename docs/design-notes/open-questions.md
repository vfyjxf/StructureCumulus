# 待定问题清单（移交版）

这份文档是给其他 agent 或人做裁决用的。它自包含：先讲清项目与术语，再给已由证据定下的结论，最后逐条列出待定问题、事实、选项与后果。裁决结果回填到每条问题的 `裁决:` 行即可。

依据：`docs/spec/_work/align/` 九份分片报告、三份复查（`recheck-coverage`、`recheck-k-consistency`、`recheck-L`）、`stage3-fact-sheets.md`（R3–R14 的详细事实表）、`stage3-audit.md`。合并报告在 `docs/design-notes/alignment-report.md`。基线提交 `0ee39b9`，修订提交 `d1bd920`。

## 0. 背景与术语

这个项目是一个 Minecraft 结构内核：判断一片方块是否构成蓝图描述的结构，若成立就把占用关系写进账本，防止结构互相重叠。设计文档在 `docs/spec/`（英文为准，`zh/` 是镜像），代码在 `src/kernel/java`。目标是**修正文档，再用文档重新生成整个内核**，所以文档必须自身正确且足以重生成。

| 术语 | 意思 |
| --- | --- |
| 蓝图 `Blueprint` | 结构模板：范围、舱位、每个舱位要求的方块、锚点与朝向 |
| 舱位 `Bay` | 蓝图内一块受检区域（开口、某一面、空腔） |
| 落点 `Siting` | 蓝图在世界中的具体位置与朝向 |
| 主体 `OccupantRef` | 判定时实际绑定到的东西（一扇门、一个方块实体） |
| 绑定 `BayBinding` | 某舱位绑定到哪些主体 |
| 声明 `ClaimRow` | 一条记录：某世界某格归某舱位的某占用者 |
| 账本 `ClaimLedger` | 声明的持久化存储，按世界分开 |
| 冻结副本 / 租约 `SnapshotLease` | 只读拍摄结果及其句柄，记录拍摄后该区域的改动次数 |
| 判定 `check` | 只读入口，可任意频繁调用 |
| 登记 `settle` | 唯一写入入口 |
| 计划 `ClaimPlan` | 判定留下的产物：每舱位的绑定与读到的账本修订号，不含格子 |
| 诊断码 `SC-*` | 错误/警告的稳定编号，分配后永不复用 |
| 单元（U0–U11） | 文档的十二个内核对分包，与代码包一一对应是设计意图 |

已知滞后（代码落后于文档，有 R 记录覆盖，不在本清单里）：R8（`OccupantRef` 带 `SnapshotId`）、R27/R37（两个入口点、`Stage` 移出请求、`SnapshotInfo` 删除）、R32（`Sharing`/`Footprint`/`Coverage`）、R35（`ClaimRow` 带 `World`）、R36（计划不携带行）。

## 1. 怎么回答

每条问题有稳定编号。回填格式：

```
Q<n>: <选项字母> [+ 一句理由，可选]
```

如果某个选项都不合适，写 `Q<n>: 新方案 — <一句话>`。答案会直接进入文档修订与 `19` 的裁决记录，所以请只选你能对后果负责的那一项。标了（推荐）的是我的建议，不是已定。

## 2. 已由证据定下、只需确认

这些不需要选，确认即可；若你不同意其中一条，请单独指出。

| 项 | 结论 | 依据 |
| --- | --- | --- |
| 分层搬迁 | `Finding*` 全体 → `kernel.finding`；`Locus`/`LocusFrame`/`KeyLocus` → `kernel.space`；`Outcome` 及两臂 → `kernel.finding`；`ReadScope` → `kernel.context`；`OccupantRef` 留 `occupant`、`SourceSpan` 留 `base` | 依赖图计算：`base` 向上的四条边只来自 `Finding.java:3-5`、`ReadScope.java:3-5`、`Outcome.java:3`、`Locus.java:3`；搬完 `base` 无出边 |
| `PrescreenIndex`/`Census`/`AnchorCandidate`/`Fingerprint` | 删除（`11` §8 自称被取代，代码里 0 命中） | `G12` |
| `anyOf()` 空数组 | 要求至少一个子项 | `RULE-D1` |
| 两个二参 `Locus` 工厂 | 删除（无调用者） | `L-R5` |
| `SC-CTX-0109` | 引擎是唯一生产者，draft 侧抛无码的 `IllegalStateException` | `H-R3`，与我的初版相反 |
| `NestedView` | 不是完整 `WorldView`，按代码的 `NestedLevelSnapshot`（名字与粒度随 Q3 定） | `C-14` |
| `Reply.combine` | 接口静态成员，隐式 `public`（不是包私有） | `Reply.java:73` |
| `SC-CTX-0202` | 有三个生产者（`ContextView`/`StructureKernel`/`MatchSession`），不是零 | 〔已核〕 |
| `UndecidedReason` 的个数 | 文档里 8 个、代码里 7 个，均缺 `LEDGER_UNAVAILABLE` | `G11`（取值集合仍需 Q9 定） |

## 3. 待定问题

### A 结构

#### Q1 `predicate ↔ blueprint` 的环怎么拆

为什么必须现在定：文档声称包级无环（`02` §2），而代码今天就有这个环；重生成会照抄它。

事实〔已核〕：`predicate/Focuses.java:11 import ...blueprint.Bay`；反向有五个文件 import `predicate`（`blueprint/Bay.java:6-7`、`CheckPlan.java:5`、`Requirement.java:4`、`BlueprintBuilder.java:7`）。成因是 `Bay` 持有 `Check`，而 `Focuses.bay()` 需要 `Bay`。

- (a) `Focuses.bay()` 改取 `Cuboid` 或 `BayView`，不再 import `Bay`。改一个 import、一个签名。
- (b) `Bay` 不再持有 `Check`，检查改为按舱位 id 的独立表。`blueprint→predicate` 消失，但"检查属于舱位"的模型要重写。
- (c) 接受这个环，删掉 `02` §2 的"无环"承诺。

推荐 (a)。
裁决:

#### Q2 阶段由谁承载

为什么必须现在定：`I-6`/`R-08`、`23` §7、`09` §4.8 的 `expand(S)`、B-11、`Check`/`Computation`/`DerivationSpec` 的分量都取决于它。

事实：文档说无人声明阶段（`I-6`、`23` §7、`10` 文法无 `"stage"`），但 `expand(S)` 按阶段选单元；代码把 `Stage` 挂在 `Check`/`Computation`/`DerivationSpec` 上（`blueprint/Bay.java:95,105,121`）。

- (a) 不要阶段：撤 `I-6`/`R-08` 的检查点说法，`expand()` 无参数，`Finding.stage` 仍由引擎填。
- (b) 单元声明：撤回 `23` §7。
- (c) 目录工厂声明：单元无阶段，注册时给出；`expand(S)` 按目录分组。
- (d) 由输出键的 tier 推导（`Deriver.tier()` 已有先例）。

推荐 (c)。若不需要"按阶段分组的计划"，选 (d)。
裁决:

#### Q3 视图模型：逐格还是逐区段

为什么必须现在定：`06` 的整篇、`22` §5.1、`20` §4.1、五档 `ReadStatus` 的可达性都由它决定。

事实：`06` 写逐格 `WorldView`（`bounds()`、`occupant(LatticePoint)`、`readStatus(LatticePoint)`）；代码与 `20`/`22` 是区段模型（`slot(SectionKey)`、`occupants(Cuboid, filter)`、`contentDigest`）。消费者无法把单元格映射成 `SectionKey`。

- (a) 区段模型为准，`06` 重写，补 `sectionOf(cell)` 与 `snapshotId()`。
- (b) 逐格模型为准，代码与 `20`/`22` 大改。
- (c) 两层并存，逐格作为区段的便捷外壳。

推荐 (a)。`(a)` 里还要定 `ReadStatus` 的"覆盖格"语义（`C-11`）。
裁决:

#### Q4 `ReadSet` / `ReadScope` / `ChangeSet` 是否各为一个类型

为什么必须现在定：三者在文档里并存，代码里只有 `ReadScope` 与 `ChangeSet`；`stepReads()` 没有对应类型，且没有尝试缓存。

事实：`07`/`22` §6.1 用 `Check.reads(...)` 的读闭包；`13` §4.5 用 `ReadSet`；代码 `base/ReadScope.java` 与 `engine/ChangeSet`（两种形态：变更提示与逐格标记）。`G12` 删掉 `PrescreenIndex` 后无人持 `stepReads()`。

- (a) 一个类型：`ReadScope` 即读闭包，删 `ReadSet`；`ChangeSet` 取一种形态。
- (b) 两个类型：`ReadScope` 是声明期闭包，`ReadSet` 是实际读取集合；`ChangeSet` 保留两形态并各自命名。
- (c) 三个都保留，写清各自生产者。

推荐 (a)：一个闭包类型，一个变更类型，减少重名。
裁决:

### B 语义

#### Q5 `check` 是否发布上下文帧；`Formed` 是否可重发射

为什么必须现在定：`I-16` 说"`check` 不写任何东西"，而 `MatchSession.publish()`（`:324`，由 `:183` 在每个阶段到达）会写 per-instance 帧；`G10` 的"旧 `Formed` 重发射"（`:338`）也在这条路径上。

事实：`I-10` 称只读调用不写；`22` §16.1 第 8 项把这条登记为未决；代码 `check` 会 `store.publish(draft)` 并在发布失败时 `reemit(dirty)`。

- (a) `check` 不发布：`Retention.CONTEXT` 的写入只发生在 `settle` 或引擎收尾；需要重写 `MatchSession` 的发布路径，并删 `22` §16.1 第 8 项。
- (b) `check` 可以发布：把 `Retention.CONTEXT` 明确为"写"，相应修订 `I-10`/`I-16` 的措辞。
- 子问题：`Formed` 重发射是 (i) 独立的 `MatchResult` 臂（需从六臂改七臂，违反 R27），(ii) 调度动作（`MatchCompletion`），还是 (iii) 删掉重发射。

推荐 (a) + 子问题 (ii)。
裁决:

#### Q6 同一归属者的 `EXCLUSIVE` 舱位重叠

为什么必须现在定：矩阵对同一 `(owner, siting)` 恒返回假，所以这条路径看不见；代码另造了第二个谓词（`ClaimMatrix.java:85-92`），文档从未提。

事实：`09` §4.2 已允许"两个 `SHARED` 舱位声明同一格"（`SHARED+SHARED` 合法）；`F9` 显示 `Blueprint.overlaps()` 被 `09` §8 标为"已解决"，但文档要删它。`EXCLUSIVE` 的同归属者重叠只有在绑定后才能发现。

- (a) 合法并去重：同一归属者占同一格两次没有意义，按 `(world, cell, owner, siting)` 去重，删账本的第二个谓词。
- (b) 是缺陷：绑定后才知的用结算级新码（例如 `SC-CLM-0008`），静态的用 `SC-BPT-0002`，删第二个谓词。
- (c) 保留两个谓词，在 `08` 里点名第二个，并保留 `Blueprint.overlaps()`。

推荐 (b)，但必须与 `SHARED+SHARED` 的合法声明共存（只有 `EXCLUSIVE` 或不同共享值的组合才是缺陷）。
裁决:

#### Q7 `DecisionKind` 与 `Coverage`：一个还是两个

为什么必须现在定：`07` §3、`01` §E、`07` §4.6 的 `block` 行、B-9/B-10/R4 都引用它们。

事实：两者都是"每格/单主体/任意主体"三元组；`RULE-D2` 与 `L` 都建议 **保留两个**（`01` 的 triads 各自有用），而我的报告 §8.1 误写成"只留一个"，并与 R10 自相矛盾。`07` §4.6 的 `block` 行要求无参 `decisionKind()` 随格子变化，不可实现。

- (a) 两个类型：`Focus.decisionKind()` 与 `Bay.coverage()` 各一个，规定映射；删 `block` 行。
- (b) 一个类型：`DecisionKind` 删除，B-9/B-10/R4 改用 `Coverage`；`24` §9.1 第 1 条随之修订。
- (c) 一个类型但改名，保留两个访问器。

推荐 (a)（与 L/RULE-D2 一致）。
裁决:

#### Q8 `Reply` 的形态与 `Bindings.merge` 的阶段

事实：文档是记录 + `Verdict` 枚举 + 私有构造；代码是四臂密封族（`Match`/`Mismatch`/`Unreadable`/`Abstain`）+ 接口静态 `combine`（隐式 `public`），`MatchSession.java:291` 已调用。`Bindings.merge` 文档是静态三参形式，代码是 `merge(Bindings other)` 且硬编码 `.stage(Stage.SETTLE)`。

- (a) 密封族 + MATCH 备注通道 + `combine`；`merge` 增加阶段参数。
- (b) 密封族，但 `merge` 不携带阶段，由调用方在收尾时补。

推荐 (a)。
裁决:

#### Q9 `UndecidedReason` 的取值集与生产者

事实：`01` 列八个（`BUDGET_EXHAUSTED`/`DIRTY_UNSTABLE`/`LEDGER_MOVED`/`LEDGER_UNAVAILABLE`/`SUBJECT_ABSENT`/`LEASE_CLOSED`/`WORKER_FAILED`/`DESIGN_DEFECT`），代码七个且缺 `LEDGER_UNAVAILABLE`；`11` §4.4 又按 `CANCELLED`/`UNREADABLE`/`ABSTAINED`/`NO_STEPS` 排序，这四个值在任何枚举里都不存在。

- (a) 以 `01` 的八值为准，代码补 `LEDGER_UNAVAILABLE`，`11` §4.4 的排序改用这八个值。
- (b) 保留 `11` §4.4 的四个额外值，扩充为十二个。
- (c) 以代码七值为准，删 `LEDGER_UNAVAILABLE`（与 `23` §4 冲突）。

推荐 (a)。
裁决:

### C 契约

#### Q10 `OccupantRef` 的身份与 `SnapshotId` 的归属

事实：文档（R8/`01`/`06`/`22`）是 `(SnapshotId, type, kind, anchor)`；代码是 `(InstanceId, type, kind, anchor, footprint)`〔已核〕。`SnapshotId` 目前属于 `engine`，而 `occupant` 要用它，所以它必须下移；`engine` 作为归属会成环。

- (a) `SnapshotId` 下移 `space`，`OccupantRef` 补 `SnapshotId`、去 `footprint`，视图加 `snapshotId()`。
- (b) `SnapshotId` 下移 `base`，其余同 (a)。
- (c) 保留 `InstanceId`，撤回 R8，改用"实例身份 + 捕获校验"（`06` §4.7 的检测要重写）。

子问题：`OccupantRef` 是否需要 `Comparable`；"空气主体"由谁铸造。
推荐 (b)（`SnapshotId` 是值，只依赖 `World` 与 `long`）。
裁决:

#### Q11 捕获 SPI 的唯一签名

事实：三份不同定义：`06` §3.4（`capture(CaptureRequest) -> CaptureResult`，三臂 `Captured/TooLarge/Unavailable`）、`16` §47/§64（`capture(req, IssueSink)`，结果含快照+租约+issues）、`22` §5.2（`CaptureResult(snapshot, CaptureIssues)`）。`IssueSink` 没有词汇表行；`SnapshotPolicy.requiredTraits` 的需求载体在 `ExpectationMeta` 里已变成 `Set<ContextKey<?>>`；`OccupantResolver` 的成员数三处不同。

- (a) 以 `06` 为准，`16` 实现它，`22` 改为引用。
- (b) 以 `16` 为准，`06` 改写（租约与快照同时产出）。
- (c) 以 `22` 的 issues-as-value 为准，`IssueSink` 只作内部累加器。

推荐 (b)：租约必须随快照同时产出，否则调用方自己拼装。
裁决:

#### Q12 区段相位的可读性

事实：`ComputationContext extends AggregateContext`〔已核〕，所以 SECTION 相位的单元也能 `read(ContextKey)`，读到上一修订的值；`22` §14 的 P6 与 `12` §8 的"`SectionContext` 没有 `read`"因此为假。

- (a) 按相位拆接口：`compute(AggregateContext)` 与 `compute(SectionContext, SectionDraft)`，编译期保证。
- (b) `read` 在屏障前返回 `Missing`，运行期保证。
- (c) 撤回 P6 与 `12` §9 的编译失败测试。

推荐 (a)。
裁决:

#### Q13 步骤词汇与 `SC-CHK-0109`

事实：`11` §4.3、`18` C-12/C-13/C-17、`09` B-11 用 `BOUNDS`/`CENSUS`/`ANCHOR`/`BAY`/`SEAM`/`CLAIM` 这套步骤名，但代码里没有步骤类型；`G12` 删掉 `PrescreenIndex` 后它们没有载体。`SC-CHK-0109`（读后一阶段的键）在 Q2 选 (a)/(d) 之后可能失去意义。

- (a) 保留步骤词汇，新增 `Step` 类型与载体（`CheckPlan` 的步骤枚举）。
- (b) 删除步骤词汇，`18` 的三条反例与 `09` B-11 一并删。
- (c) 只保留 `Claim` 一步（对应 `settle`），其余删。

推荐 (a) 的轻量版：步骤名作为 `CheckPlan` 的 `StepKind` 枚举，不引入新的执行模型。
裁决:

### D 门面与码表

#### Q14 门面的四个子问题

事实：`15` 说内核是 `(Catalogs, Host)` 九个操作；代码是 `create(Catalogs)` 与 `create(Catalogs, ClaimLedger)`，没有 `Host` 类型，九个操作实际存在三个（`check`、`derive`、`ledger`；`settle` 是 `check` 内联路径）。`derive` 文档取 `(Formed, List<DerivationSpec>, Budget)`，代码取 `(Formed, ContextStore, List<Deriver<?>>)`。`15` 把 `ContextKeys.register` 放在 `Catalogs.Builder` 上，会让 `context` 依赖 `spi`（方向反了）。

- Q14.1 端口：`Host` 唯一 + 账本重载 / 只有账本形态 / 两者都保留但 `Host` 为规范。
- Q14.2 `derive`：文档形态 / 代码形态 / 两者重载。
- Q14.3 `ContextKeys.register`：留在 `context`（允许 `spi→context`）/ 移到 `spi` / 移到 `base`。
- Q14.4 五个缺失操作（`document`/`compile`/`siting`/`solve`/`explain`）：列入迁移 / 从门面删除 / 先留签名后实现。

推荐 14.1 `Host` 唯一加账本重载；14.2 文档形态；14.3 留 `context`；14.4 列入迁移。
裁决:

#### Q15 码表、生产者与 R3/R4 的族归属

事实：`13` 是注册表，但 `10` §4.4 另提一套 `SC-DOC-0101–0606`，`14` §8 另提 `SC-RMD-0201–0601`，`06` §4.1 另提 `SC-OCC-0101–0203`；`13` 只登记了 `SC-DOC-0001–0003`、`SC-RMD-0001`+`0309`、`SC-OCC-0001/0002`。多处码无生产者，`SC-CTX-0202` 反而有三个。R3 类（`ONE_SUBJECT` 缺主体类检查）与 R4 类（覆盖不匹配）在 `13` 的族语义、`24` §5、代码里切成三种方向。另有一条"检查判定了舱位外的格子"尚未归族。

- Q15.1 注册表：`13` 唯一 / 各族自有 / 混合（`13` 登记跨单元，单元登记自有）。
- Q15.2 DESIGN 期违规：一律异常 / 一律诊断 / 按严重度分。
- Q15.3 死码：删除 / 补生产者 / 标为预留。
- Q15.4 R3/R4 归属：`SC-BAY-0107`=R3、`SC-CHK-0106`=R1+R4（`24` §5）/ 按 `13` 的族语义反向 / 拆成四个码。

推荐 15.1 `13` 唯一；15.2 DESIGN 期一律异常、SETTLE 期一律诊断；15.3 补生产者（每码一个）；15.4 取 `24` §5，并把 `13` 的族描述与代码同步。
裁决:

### E 文档完整性

#### Q16 `LocusMap` 与帧转换

事实：`05` §1/§3/§4.7/§5/§6、`01`、`06`、`13` 的 `SC-SPC-0001` 都依赖 `LocusMap`，代码里 0 命中；`Locus.definition(SourceSpan)` 只产空格子，而 `05` §4.7 的 `toDefinition` 与 L-1 的"`DEFINITION` 携带作者格子"不能并存。

- (a) 实现 `LocusMap`（`record(Siting)`、`of`、`toWorld`/`toLocal`、`toDefinition`），`DEFINITION` 携带作者格子。
- (b) 实现 `LocusMap`，`DEFINITION` 保持空格子，`toDefinition` 只做帧标注。
- (c) 删除帧转换，`05`/`13` 去掉相关要求。

推荐 (a)。
裁决:

#### Q17 `Placement.inverse` 的可空与定律

事实：`compose`/`inverse` 是 `@Nullable`〔已核〕；`05` §4.6 把 `P.inverse().compose(P) == IDENTITY` 当恒等式。`MIN_XZ` 的取反越界，所以"限制可逆"这条不可行。

- (a) 把定律改成"当 `inverse()` 非空时成立"。
- (b) `inverse` 返回 `Outcome`/`Optional`，强制调用方处理。
- (c) 用更宽的坐标表示（如 `long`）让所有合法摆放都可逆。

推荐 (a)。
裁决:

#### Q18 U10 修葺的归属

事实：`14-remedies.md` 约 20 个类型、十条试运行规则、七种编辑，没有任何分片对照过代码；`22` §11.2/§11.3/§12/§13/§14/§15 同样无人读；`RMD` 码族与 `14` 自相矛盾。

- (a) 补一个分片审 `14` 与 `22` §11–§15，再决定码表与类型。
- (b) 明确标注"U10 未建、不参与本轮重生成"，从重生成输入里排除。
- (c) 从设计集删除 U10。

推荐 (a)。若时间紧，选 (b) 并在文档里写明。
裁决:

#### Q19 有名无实现的类型

事实：`Codes`、`Stability`、`ExplainBook`、`Checks`（内核类路径外）、`DataView`、`DataKind` 在文档里被引用，代码里不存在或位置相反（`15` 说 `NamespacedId` 属于 `spi`，代码在 `base`；`15` 说 `Codes` 在 `spi`，`13` 说在 `base`）。`guide.zh`、`docs/README.md`、`19` 的 12 条未审 R 记录另有多处失真（`R16` 仍写 `StructureKernel.match`，`R29` 的 `MatchCompletion` 无代码）。

- (a) 逐类补齐实现，并把这些类型写进词汇表与正确单元。
- (b) 删除文档里的引用（`Codes` 改 `FindingCode.of(...)`，`Stability` 并入 `Stage`，`ExplainBook` 移到"未建"）。
- (c) 混合：值类型补实现，纯展示类型标注未建。

推荐 (c)，并单独修 `guide.zh`/`README`/R 记录（这些只需改文档，不需要裁决）。
裁决:

## 4. 不需要裁决、只需改文档的

以下已在 `alignment-report.md` §4 按文件列出，这里只列类别，避免重复：

- `01` 的归属与分量（`Budget`/`Spend` 在 `engine`、`CancelToken`/`Stability`/`Arguments`/`ParameterKey` 无类型、`Stage.STALE` 残留、`Locus` 可空性）。
- `05` 的 `SectionKey`/`ChunkGrid`/`ChunkKey` 缺定义、`Orientation` 成员名、box 展开顺序（给出反例：拼接序不等于格序）。
- `06` 的七个漏列文件、`SC-OCC` 表、`NestedView` 名字、`ReadStatus` 的覆盖格语义。
- `07` 的包名、视图类型、`anyOf` 全零行、`expectation.requires` 的载体。
- `08` 的行序键缺 `siting`、`ClaimConflict` 的 findings 类型、`ClaimSet` 的排序、`ClaimView` 的形状。
- `09` 的 `Bay.of` 结、`DerivationSpec` 的 `Arguments`、舱位 id 重挂、Kahn 序两处措辞。
- `10` 的 `bounds` 推断、`DocumentUpgrade` 两签名、`SC-DOC` 表。
- `12` 的 `ContextDraft` 动词集（`forSection`/`refuse`/`declined`/`read`/`publishes`/`keys`）、`keys()` 的集合、tier 的 `ABSENT`。
- `13` 的发射半边缺失、`SC-CTX-0109` 双通道、`SC-CTX-0201/0301` 的标签、`13` §4.5 排序键。
- `15`/`16`/`17`/`18` 的操作数、`Host`、`derive`、`register`、`begin` 的参数、`SC-SPI`/`SC-MC` 生产者。
- `19` 的 `R16`/`R29`、`R3` 未标取代。
- `21` 的 `bounds` 必填、`guide.zh`（约十处）、`README.md:21`、`_translation-guide` §3 的不变量计数。

## 5. 覆盖缺口

这些是"还没人看过"的部分，回答 Q18 决定它们的去向：

- `14-remedies.md` 全篇、`22` §11.2/§11.3/§12/§13/§14/§15。
- `00` 的 R/I 表未按表审（`I-4`、`I-10` 与代码相反；`I-5`/`R-04` 提名无载体的类型）。
- `19` 的 12 条 R 记录（`R16`、`R29` 与代码树矛盾）。
- `docs/guide.zh.md`、`docs/README.md`、`zh/_translation-guide.md`。
- `visual/`（前端）不在本轮范围。

## 6. 回答之后

1. 我按答案修订 `docs/spec/*.md`，每条改动记进 `19` 的裁决表（沿用 R27–R37 的格式）。
2. 同步 25 个中文镜像。
3. 再跑一次"文档能否独立重生成"的检查：按修订后的文档写出类型骨架，看是否有缺类型、缺成员、缺生产者。
4. 代码迁移单独一批，按已登记的 R 记录执行。
