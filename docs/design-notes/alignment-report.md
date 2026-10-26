# 代码与设计的不对齐报告

目的：这份文档用于修正 `docs/spec/`，随后把规格作为唯一输入重生成内核。因此判定标准不是"代码有没有按文档写"，而是"哪一侧才是正确的表述"。

## 1. 方法与口径

九个分片并行审查，覆盖 `src/kernel/java` 全部 143 个文件、`src/main/java` 7 个文件、`src/kernelTest/java` 32 个文件（162 个测试），以及 `docs/spec/` 25 个设计文件。原始报告在 `docs/spec/_work/align/`。

| 分片 | 单元 | 报告 |
| --- | --- | --- |
| A | U0 `base` | `a-base.md` |
| B | U1 `space` | `b-space.md` |
| C | U2 `occupant` | `c-occupant.md` |
| D | U3 谓词 | `d-predicate.md` |
| E | U4 `claim` | `e-claim.md` |
| F | U5/U6 蓝图与文档 | `f-blueprint.md` |
| G | U7 `engine` | `g-engine.md` |
| H | U8/U9 上下文与诊断 | `h-context-findings.md` |
| I | U11 门面、适配器、测试 | `i-spi-adapter-tests.md` |

判定口径：

- `DOC`：文档对，代码滞后。若没有任何 R 记录或迁移项覆盖这条滞后，该滞后本身算一条发现。
- `CODE`：代码对，文档要改。
- `BOTH`：两侧都错，需要第三条规则替换。
- `RULE`：两侧都能自圆其说，或者都写不出可实现的形态，交你裁决。

本报告中标〔已核〕的条目由我直接读了那一行代码或文档确认；其余按分片报告的锚点转述。

## 2. 九个单元的一句话结论

- **U0 `base`**：它承担了文档分配给别的单元的类型（U9 全部模型、`Locus` 族、`NamespacedId`），并且反过来 import `space`/`occupant`/`context`，"不依赖任何包"不成立。
- **U1 `space`**：坐标与朝向基本对齐；`LocusMap` 完全不存在，`SectionKey`/`ChunkGrid`/`ChunkKey` 在代码里但文档没定义，`Placement.inverse()` 可空而文档把它当恒等。
- **U2 `occupant`**：文档描述的逐格 `WorldView` 已被 `20`/`22` 的区段模型取代；`06` 漏掉包内 7 个文件，包括被 U0/U7 使用的实体窗口模型。
- **U3 谓词**：包名与单元名不一致；`Reply` 的形态、`DecisionKind` 与 `Coverage` 的关系、`Expectation.requires` 的载体都要重定。
- **U4 `claim`**：`24` 的词汇只到文档不到代码；"同一蓝图两个舱位的重叠"没有规格化的谓词，代码暗中另有一个。
- **U5/U6 蓝图与文档**：整个 U6（`kernel.document`，约 25 个类型）不存在；`Bay` 仍是被判死的白名单，且会拒绝 `24` §6 的动机机器。
- **U7 `engine`**：两个入口点、计划不携带行、捕获门禁都只在文档里；`UndecidedReason` 的取值集合四处不同。
- **U8/U9 上下文与诊断**：`ComputationContext` 让区段相位也能 `read`，使 P6 成为假命题；诊断的发射半边完全未建。
- **U11/A1 门面与适配器**：九个操作只有四个存在；`Host` 端口形状、`derive` 签名、`ContextKeys.register` 的方向都与代码相反或缺失；适配器是空的。

## 3. 需要你裁决的十二项

> 本节是初版。K/L 复查后的裁定清单见 §8，以 §8 为准；本节的 D-1、D-4、D-5、D-7、D-8、D-11 都存在捆绑或不可写的选项。

### D-1 `base` 的边界与包图

三个分片独立命中（A1/A4/A5、B-6、H-R1/H3）。文档说 `base` 不依赖任何包（`03` §3、`02` §2），但代码里 `base` 持有 `ReadScope`（其成员是 `ContextKey`、`EntityWindow`、`OccupantRef`）、`Outcome`、整个诊断模型和 `Locus` 族，并 import `space`/`occupant`/`context`。同时文档给 U9 一个不存在的包 `kernel.finding`。

选项：

- (a) **`base` 就是值底座**：承认它持有诊断模型与 `Locus`，把 `finding` 从单元表删掉，把"不依赖任何包"改成"base 只依赖 JDK"并允许它反向被所有单元使用；`ReadScope` 留在 `base`，循环用"`base` 例外"豁免。
- (b) **`base` 只放纯值**：诊断的*值*（`Finding`/`FindingBundle`/`FindingCode`/`Severity`/`Label`/`SourceSpan`/`Locus`）留在 `base`，U9 的*发射半边*（`FindingSink`/`FindingOptions`/`FindingRenderer`/`ExplainBook`）独立成 `kernel.finding`；`Outcome`/`ReadScope` 移到 `finding`（因为 `Outcome.Missing` 携带诊断包），`ReadScope` 的窗口分量改为 `occupant` 侧可注入。
- (c) 把 `ReadScope` 拆成三份（格子归 `space`、键归 `context`、窗口归 `occupant`），由 `engine` 组合。

建议 (b)。它保住文档的单元表，也让"值"与"报告"分离；涟漪是 `Outcome` 与 `ReadScope` 的包移动，所有 import 跟着改，但没有语义变化。(a) 改动最小但要靠豁免条款维持依赖门禁，(c) 会把一个记录拆成三处。

### D-2 阶段由谁承载

三个来源命中（F2、H-R6、`22` §16.1 第 16 项；另见 A7/H11 的 `Stage.STALE` 残留）。文档说没有任何单元可以声明阶段（`I-6`、`23` §7、`10` 的文法无 `"stage"`），但 `expand(S)` 要选出"属于 `S` 的单元"，B-11 与 `09` §4.8 也按阶段分组；代码把 `Stage` 挂在 `Check`/`Computation`/`DerivationSpec` 上。

选项：

- (a) **任何地方都不要阶段**：`expand()` 不再带参数，计划按依赖顺序排列；结果里的 `Stage` 由引擎按产出它的步骤填写。`I-6`/`R-08` 的"检查点"由引擎的步序承担。
- (b) **单元声明阶段**：`Check` 等保留 `Stage`，撤回 `23` §7 的"作者不声明"。
- (c) **目录工厂声明**：`Check` 等仍然无阶段，由 `catalogs` 注册时给出它的阶段，`expand(S)` 按目录分组。

建议 (c)。它同时满足"作者不声明阶段""结果里有阶段""计划可分组"三条，且与 `12` 的 `Deriver.tier()` 已有做法一致。涟漪：`Check`/`Computation`/`DerivationSpec` 去掉 `Stage` 分量，注册路径多一个阶段参数，`CheckPlan` 继续持有引擎填写的 `Stage`。

### D-3 区段相位的可读性

`ComputationContext extends AggregateContext`（〔已核〕类型关系），于是 section 相位的单元也能 `read(ContextKey)`，读到上一修订的值；`22` §14 的 P6 与 `12` §8 的"`SectionContext` 没有 `read`"因此是假命题。

选项：

- (a) **按相位拆接口**：`compute(AggregateContext)` 与 `compute(SectionContext, SectionDraft)`，`SectionContext` 故意没有 `read`，编译期保证。
- (b) `read` 在屏障前返回 `Missing`，运行期保证。
- (c) 撤回 P6 与 `12` §9 的编译失败测试，承认区段单元可以读。

建议 (a)。(b) 把编译期保证降级为运行期，(c) 会让投影在区段相位上依赖未折叠的值。

### D-4 同一蓝图内部的舱位重叠

文档说"同一蓝图的两个舱位是两个声明者，矩阵原样适用"，但 `ClaimMatrix.conflicts` 对同一 `(owner, siting)` 恒返回假，所以这条路径看不见；代码另造了第二个谓词（`ClaimMatrix.java:85-92`）。静态足迹重叠在 DESIGN 由 `SC-BPT-0002` 处理，`Bound` 足迹的重叠要绑定后才知道，落在两套机制之间。

选项：

- (a) **内部重叠合法并去重**：同一个归属者占同一格两次没有意义，结算时按 `(cell, owner, siting)` 去重，删掉账本里的第二个谓词。
- (b) **内部重叠是缺陷**：在结算阶段报一条新码（例如 `SC-CLM-0008`，`SETTLE` 级），并删掉第二个谓词，把它并回矩阵之外的显式检查。
- (c) 保留两个谓词，在 `08` 里点名第二个。

建议 (b)。声明了重叠舱位是作者错误，DESIGN 能查的用 `SC-BPT-0002`，只有绑定后才能查的用结算级新码；(a) 会把作者错误静默吞掉。

### D-5 视图模型：逐格还是逐区段

`06` 写的是逐格 `WorldView`（`bounds()`、`occupant(LatticePoint)`、`readStatus(LatticePoint)`），代码与 `20` §1.3、`22` §5.1 是区段模型（`slot(SectionKey)`、`occupants(Cuboid, filter)`、`contentDigest(SectionKey)`）。消费方无法把单元格映射成 `SectionKey`，于是五档 `ReadStatus` 在单元格上不可达。

选项：

- (a) **以区段模型为准**：`06` 重写为 `22` §5.1 的成员集，补上 `snapshotId()`；单元格级状态改为 `22`/`20` 的区段状态加一个 `cell→SectionKey` 的换算（`view.sectionOf(cell)`）。
- (b) 以逐格模型为准：代码必须大改，`20`/`22` 也要重写。
- (c) 两套并存，逐格视图作为区段视图的便捷外壳。

建议 (a)，并加 `sectionOf(cell)` 让五档状态可达；`06` 的其余旧内容（实体窗口、`NestedView` 粒度）一并并入。

### D-6 `OccupantRef` 与捕获身份

`19` R8、`01` §D、`06` §3.3、`22` §5.1 都说 `OccupantRef` 携带 `SnapshotId`（〔已核〕）；代码是 `OccupantRef(InstanceId, type, kind, anchor, footprint)`（〔已核〕）。身份用 `InstanceId` 时，"主体来自本次捕获"无法检查，R36 的绑定校验失去依据；而文档的 `SnapshotId` 目前在代码里没有生产者，因为没有视图暴露捕获 id。

这不是文档缺陷，是**代码滞后于 R8**，另加一个文档缺口：`WorldView` 必须提供 `snapshotId()`，`OccupantSnapshot` 而不是 `OccupantRef` 携带 `footprint`。

裁决点只有一个：确认"代码补 `SnapshotId`、删 `footprint`，视图加 `snapshotId()`"。

### D-7 捕获与解析 SPI 的唯一定义

`06` §3.4、`16` §47/§64、`22` §5.2 给了三份互不相同的捕获签名（`CaptureResult` 的三臂/两臂、`capture` 的返回值、`IssueSink` 的有无），`IssueSink` 在词汇表里没有行。代码里一份都没有。

选项：

- (a) 以 `06` §3.4 为准（`capture(CaptureRequest) -> CaptureResult`，三臂 `Captured/TooLarge/Unavailable`），`16` 实现它，`22` §5.2 改成引用。
- (b) 以 `16` 为准（捕获返回快照+租约+issues），`06` 改写。
- (c) 拆成两件事：`capture` 只产快照，租约由 `retain` 另取。

建议 (a) 并在 `01` 补 `IssueSink` 行；租约由 `Captured` 携带，避免调用方自己拼装。

### D-8 门面与宿主端口

四项同源（I-01/I-02/I-09/I-04）：

- `15` 说内核是 `(Catalogs, Host)`，`Host` 携带 `documents`/`segments`/`ledger`；代码是 `create(Catalogs)` 与 `create(Catalogs, ClaimLedger)`，没有任何 `Host` 类型。
- `15` 的 `derive(Formed, List<DerivationSpec>, Budget)` 与代码的 `derive(Formed, ContextStore, List<Deriver<?>>)` 相反；文档形态让调用方拿到 store，等于拿到发布目标。
- `15` 把 `ContextKeys.register` 放在 `Catalogs.Builder` 上，这会让 `kernel.context` import `kernel.spi`，违反 `02` 的依赖方向；代码是 `ContextKeys.register(draft, key)`。
- 九个操作里有五个（`document`、`compile`、`siting`、`solve`、`explain`）根本没有代码，且没有 R 记录或迁移项。

建议：`Host` 成为唯一端口，保留 `create(Catalogs, ClaimLedger)` 作为测试便利重载；`derive` 取文档形态（store 从 `Formed` 指向的实例取）；`ContextKeys.register` 保留在 `context`，`Catalogs.Builder` 只消费键（`spi → context` 这条边写进 `02`）；五个缺失操作进入迁移清单。

### D-9 `LocusMap` 是否存在

`05` §1/§3/§4.7/§5/§6、`01`、`06`、`13` 的 `SC-SPC-0001` 都依赖 `LocusMap`（帧转换），代码里 0 命中，没有 R 记录。同时 `05` §4.7 的 `toDefinition` 与 L-1 的"`DEFINITION` 携带作者格子"在 `Locus.definition(SourceSpan)` 只有空格子的情况下不能并存。

选项：

- (a) **实现 `LocusMap`**：`record(Siting)`、`of`、`toWorld`/`toLocal`、`toDefinition`，并规定 `DEFINITION` 的格子来源。
- (b) 删除帧转换要求：`Locus` 的帧由产出它的单元直接给出，`05`/`13` 去掉 `LocusMap` 与 `toDefinition`。

建议 (a)。诊断要跨"作者坐标/世界坐标"两套表述，删除会让渲染与 `--explain` 失去换算能力；但必须同时定 `DEFINITION` 到底携带空集还是作者格子（现在代码是 `Locus.definition` 只拿 `SourceSpan`，`Locus` 的 `cells` 非空校验与"可空"表述也冲突）。

### D-10 `Placement` 的定律与可空

`Placement.compose`/`inverse` 在代码里是 `@Nullable`（〔已核〕`Placement.java:15,27`），`05` §3 未标注，§4.6 把 `P.inverse().compose(P) == IDENTITY` 当恒等式。摆放把原点映射到格点表示范围之外时逆不存在。

选项：

- (a) 限制定义域：只有把原点留在可表示范围内的摆放才可构造，于是 `inverse` 恒存在，定律成立。
- (b) 承认可空，把 §4.6 的定律改成"当 `inverse()` 非空时成立"。

建议 (a)。(b) 会让每处调用都要处理空值，而这类摆放本来就没有意义。

### D-11 `DecisionKind` 与 `Coverage`

两者是同一个三元组（每格/单主体/任意主体）被声明两次（`07` §3、`01` §E），代码里 `DecisionKind` 是 `CUBIC/SUBJECT/PRESENCE`、`Coverage` 是另一套枚举。`07` §4.6 的 `block` 行还要求 `decisionKind()` 随格子是否覆盖舱位而变化，而无参方法做不到。

选项：

- (a) **只留一个枚举**：`Focus.decisionKind()` 与 `Bay.coverage()` 共用一个类型，值名取 `24` 的三问（`EVERY_CELL/ONE_SUBJECT/ANY_SUBJECT`）。
- (b) 保留两个类型，并规定映射关系与各自的用途。
- (c) 删掉 `DecisionKind`，B-9/B-10/R4 直接用 `Coverage` 表述。

建议 (a)。`07` §4.6 的 `block` 行必须删（静态焦点不可能有条件种类），改为"`block` 的决策格非空且不等于整舱位 → 需要 `EVERY_CELL`"这类可判定的表述。

### D-12 `Reply` 的形态

文档是 `record Reply(Verdict, Bindings, FindingBundle)` 加私有构造，代码是四臂密封族并自带 `combine`（`MatchSession` 已调用）。密封形态让"匹配/不匹配/缺席/拒绝"结构化，也让折叠有类型可依。

建议：采用密封族，补回 §4.1 的 MATCH 备注通道与 `combine`；`Verdict` 枚举删除（只有一个使用者）。

## 4. 文档必改清单

按文件分组。每行：锚点 → 现状 → 应改为。未标〔已核〕的按分片报告转述。

### `00-overview`

- 单元表 U9 `kernel.finding` / U6 `kernel.document` / U10 `kernel.remedy`：按 D-1、D-8 的结论改写（要么建包，要么删行）。
- `I-6`（阶段）：按 D-2 改写"谁选阶段"。
- `I-16`：保留（R36）。

### `01-glossary`

- §D `OwnerId`、§I `InstanceId` 的位置与使用者不相邻（A5）；按 D-1 移到同一节。
- §B `Budget`/`Spend` 归 `base`，实际在 `engine`（〔已核〕`Budget` 在 `engine`；`Budget.of(int,int)` 存在，A6 的"方法不存在"不成立，只需改归属）。
- §B `CancelToken`/`Stability`/`Arguments`/`ParameterKey`/`DataView`/`DataKind` 在 `src/` 里没有类型（A6）；要么实现，要么从词汇表删除。
- §A `Stage` 的取值：`STALE` 必须删（A7；`base/Stage.java` 仍有五值〔已核〕）。
- §C `Locus`：`cells` 是否可空（A12）；`Locus.definition` 的格子来源（D-9）。
- §E `DecisionKind`/`Coverage`：按 D-11 合一。
- §E `ExpectationMeta.requires` 的 `TraitKey`（D10）：代码是 `Set<ContextKey<?>>` 加第四个分量 `FindingCode mismatchCode`，文档必须写这个形态。
- §I `UndecidedReason` 取值集：四处不同（G11），以代码加 `LEDGER_UNAVAILABLE` 为准统一。
- §I `MatchRequest` 的 `Blueprint` 与 §I `BlueprintId`（G13）：文档一处说 `BlueprintId`、一处说 `Blueprint`，代码是 `Blueprint`；删掉 `BlueprintId` 或改名统一。
- §I `SnapshotLease.view()` 的返回类型 `LevelSnapshot` 与代码 `WorldView`（G14）：按 D-5 统一（建议 `WorldView`）。
- §K `Finding` 的九个分量与 `severity` 的必填性（A9）：补"标签兜底成消息"的规则或改成必填。

### `02-architecture`

- §2 包图与 §9.1 依赖规则：按 D-1 重写；补 `spi → context` 边（D-8）。
- §4 阶段表：`STALE` 行已删，但 `base/Stage` 仍有该值（A7），需要在代码侧同步。
- §5 账本行：`FrozenLedger` 在代码里不存在（I-12 相关），改为现有形态或标注未建。
- §3 流程图 `MatchRequest` 元组已更新；`Place`/`List<Stage>` 的宿主侧归属仍悬（`22` §16.1 第 16 项）。

### `05-space`

- §3 `Placement`：按 D-10 标注可空或限制定义域。
- §3/§4.7/§5/§6 `LocusMap`：按 D-9 决定实现或删除。
- §1/§3 `SectionKey`/`ChunkGrid`/`ChunkKey`：文档没定义却在代码里（B-4），补三行并统一 `cellSize`（代码可配置，`01`/`20` 说固定 16³）。
- §3 `Orientation`/`LatticeKey`/`CuboidSet` 的成员名（B-7）：按代码校正（`then`、`id()`、`x()/z()/y()`）。
- §6 与 Q-S4(A) 的"box 展开顺序"（B-5）：给出真正的全序定义（先按 box 序，再按格序，或明确是拼接序）。
- §8 Q-S2（`Flip` 两还是三）：关闭，取三常量加 `isMirror()`（〔已核〕代码三值）。
- §4.1 与 `Cuboid.of(LatticePoint)` 的"guard not limit"（B-8）：代码对无后继的格子抛异常，文档要写这个边界。

### `06-occupants`

- 整篇按 D-5 重写为区段模型，并补 `snapshotId()`、`sectionOf(cell)`。
- §3.3 `NestedView extends WorldView`（C-14）：与 `predicate.NestedView` 同名两义；二选一改名，并定粒度为区段。
- §3.4 捕获 SPI：按 D-7 取唯一定义，`IssueSink` 补词汇表行。
- §4.1 的 `SC-OCC-0101–0203` 与 `13` 的 `SC-OCC-0001/0002`（C-12）：合成一张表并指定生产者；当前两侧都没有生产者。
- §4.2/§4.3 的"一格两占用者"（C-7）：统一为"一格一个占用者；被实体足迹覆盖的格回答实体，其方块为 `NOT_CAPTURED`"（与 `20` §4.2 一致）。
- §3.1 `OccupantRef`（D-6）：文档已对（`SnapshotId`），无需改；但 `OccupantDescriptor`/`TraitKey`/`Trait` 的形态（C-4/C-5）与代码不符，按代码改写或实现。
- §1 漏掉的七个文件（C-3）：补 `SectionSlot`/`SectionSnapshot`/`NestedLevelSnapshot`/`OccupantFilter`/`EntityWindow`/`EntityKey`/`ChangeSet`，其中 `EntityWindow`/`EntityKey` 必须进词汇表（U0/U7 在用）。

### `07-expectations`

- §3 包名与 `00`/`03`/`01` 的 `kernel.expectation`（D1）：二选一，建议把代码包改名（文档只改三处，代码改 18 个文件的 import）。
- §3/§5 的 `BlockView`/`OccupantView`/`EntityView`（D2）：代码里不存在，`allCells` 是 `List<OccupantSnapshot>`；按代码改写。
- §3 `Reply`（D-12）：改为密封族。
- §4.3 `anyOf` 全零行（RULE-D1）：说明空子数组的行为，或要求 `n ≥ 1`。
- §4.6 `block` 行（D-11）：删，改成可判定的表述。
- §3 `Expectation.reads()`（RULE-D3）：补 `reads(Cuboid bayCells)` 或写明由引擎按舱位计费。
- §3 `ExpectationMeta.requires`（D10）。
- §3 `Bindings.merge`（D12）：文档的静态合并无法携带阶段，代码的 `merge(other)` 硬编码 `Stage.SETTLE`；需统一。
- `13` 的 `SC-EXP-0001–0003` 与本单元的关系（D13）：确认哪个单元产出。

### `08-claims`

- §4.2 内部重叠（D-4）：按裁决写第二套规则或删掉。
- §4.1 行序键（E D3）：补 `siting`，`(world, cell, owner, siting, bay)`。
- §4.6 冲突诊断的分组（E D7）：`(owner, bay)` 与代码的 `Pair(challengerBay, incumbentOwner, incumbentBay)` 不一致；按"声明者是 `(owner, siting, bay)`"统一。
- §2/§3 `ClaimConflict` 的 findings 类型（E D9）：文档 `List<Finding>`，代码 `FindingBundle`。
- §3 `ClaimView`（E D10）：改记录形态，补 `unavailable()` 与"不可用视图不持有行"。
- §3 `ClaimSet`（E D15）：写明"按 `ClaimRow.ordered` 排序"，不是 `List.copyOf`。
- §4.5 `ClaimLedger`（E D14）：补 `world()`、`release`；`23` §5 已有语义。
- §4.8/§7 的 `SC-CLM` 表（E D4/E D6）：与 `13` 合一，见 §8 的 R12。
- §5 参考草图（D15 相关）：`ClaimPlanner.derive(...).rows()` 与 `ClaimRow.ordered` 的调用顺序要能编译。

### `09-blueprints`

- §3 `Bay.of(..., Function<Bay, Requirement>)`（F6）：改写为构造器 + `attach(Requirement)`。
- §4.6 B-6/B-9/B-10 与 `SC-BPT-0002`/`SC-CHK-0106`/`SC-BAY-0107`（F8）：按 D-4 与码族裁决统一。
- §4.4/§5 的舱位 id 重挂（F11）：`alias.namespace()/alias.path()` 与 `as + ":" + localName` 二选一。
- §4.8 `expand(S)` 与阶段（D-2）；`DerivationSpec` 的 `Arguments` 与 `Map<String,Object>`（F3）、`order` 唯一性范围。
- §4.8 step 3 的 Kahn 序与 `inputs()`/`requires()`（F15）：两处措辞不一致，且与代码的排序不同。
- `Check` 的 `Stage` 分量（D4，D-2）。

### `10-documents`

- §3 `package dev.vfyjxf.cumulus.kernel.document` 与约 25 个类型（F1）：要么建单元，要么整段删除并把文档能力并入 `blueprint`。
- §4.4 的 `SC-DOC` 码表（F4）：与 `13`、`21` 合一。
- §4.1/§4.7 的 `bounds` 推断（F5）：与 `21` 的"必填、从不推断"统一。
- §3 `DocumentUpgrade` 两个签名（F12）：取一个，`21` 改为调用它。
- §9 R3 的 `SegmentLibrary.Builder`/`Assembler`（F13）：R3 要么标为被取代，要么与 `10` 统一。

### `11-engine`

- §3/§4.1 的两个入口点（G1）、`Admitted` 的 `planned`/`ledgerRevision`（G2）、`check` 读账本（G3）、`ClaimPlan` 无行（G4）、`ClaimPlanner.derive`（G5）、`MatchResult` 每臂带 `Stage`（G6）、`MatchRequest` 元组（G7）：这些是已知滞后，文档已对；迁移见 §6。
- §4.6 的 `Formed` 重发射（G10）：文档内部矛盾（`01`/`22` 说只由 `settle` 产生，`11` 说会重发射），需要定"重发射是独立动作"。
- §1/§4.3/§4.4 的 `PrescreenIndex`/`Census`/`AnchorCandidate`/`Fingerprint`（G12）：文档要求、代码没有、`11` §8 又说被取代；三者取一。
- §4.4 的 `SC-ENG-0104`（G12）：没有承载者，补或删。

### `12-context`

- §3 `ContextDraft` 的动词集（H6）：补 `forSection`/`refuse`/`declined`/`read`/`publishes`/`keys`，否则无法从文档写出折叠。
- §3 `keys()`（H4）：定义其集合（合并旧帧还是仅当前层），并与 `get()`/`has()` 的遮蔽一致。
- §3 的 tier 遮蔽（H5）：`ABSENT` 语义要写明（"缺省"与"缺席"的区别）。
- §4.2 的 `SC-CTX-0107` 双写（H12）：与代码的抛异常行为对齐。
- §5 `Computation.stage()`（H10/D-2）。

### `13-findings`

- §3 的发射半边（H2）：`FindingSink`/`FindingOptions`/`FindingRenderer`/`ExplainBook` 等未建，且无迁移项；要么列入迁移，要么删除。
- §4.3 的 `SC-CTX-0109` 双通道（H7/RULE-H3）：定一个。
- `SC-CTX-0201`/`0301` 的无标签问题（H8/H9/RULE-H4）。
- §4.2 的 span 规则（H13/RULE-H5）：`DEFINITION ⟺ source != null` 与 `Locus.local(cells, source)` 冲突。
- §4.3 `SC-DOC` 与 `SC-OCC` 表的合并（F4、C-12）。
- `13` §4.5 的排序键（H11）：阶段已不再是"声明顺序"，`STALE` 已删。

### `15-spi`

- §3/§4.4 的九个操作：与代码的四项对齐，五个缺失进迁移（D-8）。
- §3 `Host` 与 `create`（I-02）。
- §3 `derive` 签名（I-09）。
- §3 `ContextKeys.register` 的方向（I-04）。
- §4.4 `Never throw`（I-10）：代码 `publishTier` 会重试并抛，需改代码或改规则。
- `SC-SPI-0001`/`0002` 的生产者（I-11）。

### `16-adapter`

- 整篇的捕获/写入门面（D-7、I-13）：`begin` 的参数必须与 `14` 一致（携带 `claims`、`ledgerRevision`、`planDigest`）。
- §8 的 `SC-MC` 码表（I-14）：与 `13`/`06` 的捕获码统一。

### `17-worked-example` / `18-verification`

- `17` 的层列表与 `18` C-12/C-13/C-16/C-18/C-20/C-25（I-15）：引用的包（`.../expectation`、`.../finding`、`.../remedy`）不存在，按 D-1/D-8 的结论改。
- `18` 的门禁若依赖未建的操作，标注"未建"或删行。

### `19-open-proposals`

- R3 未标被取代（F13）。
- R30 的 `SC-CLM-0005` 已被 R36 改义（上一轮已记）。
- 新增本轮裁决记录（见 §7 的写法）。

### `21-authoring` / `22-consolidated-design` / `23` / `24`

- `21` 的 `bounds` 必填（F5）与 `10` 统一。
- `22` §5.1 的 `WorldView` 补 `snapshotId()`（D-5）。
- `22` §7.2/§8.3 与 `12` 的 `ContextDraft` 动词集统一（H6）。
- `23` §9 的迁移清单补：`SnapshotInfo` 删除（已写）、`Ticket` 迁移（已写）、`World` 类型（R35）、`OccupantRef` 的 `SnapshotId`（R8）、`Host` 端口（D-8）。
- `24` §9 的迁移清单补：`Coverage.PRESENCE` 拒绝 `Bound` 这一语义缺失（E D2）。

## 5. 会阻断重生成的文档空白

这些不是"代码没跟上"，而是"照着文档写不出代码"：

1. `kernel.document`（U6）：约 25 个类型只有名字，没有实现路径（F1）。
2. 诊断的发射半边（U9）：`FindingSink`/`FindingOptions`/`FindingRenderer`/`ExplainBook`/`SourceText`/`RenderContext` 全部缺失（H2）。
3. 修葺单元（U10）：`Remedy`/`EditPlan`/`OverlayModel`/`PlanValidator` 在文档里存在、代码里没有、也没有迁移项（I 的单元表）。
4. `ContextDraft` 的真实动词集（`forSection`/`refuse`/`declined`/`read`/`publishes`/`keys`）（H6）。
5. `LocusMap` 与帧转换（B-1）。
6. `SectionKey`/`ChunkGrid`/`ChunkKey` 的定义（B-4）。
7. 捕获 SPI 的唯一定义与 `IssueSink`（D-7）。
8. 实体窗口模型（`EntityWindow`/`EntityKey`）没有词汇表行，却被 U0/U7 使用（C-3）。
9. `NamespacedId.parse` 与 `of(path)` 的默认命名空间规则（I-06）。
10. `Budget`/`Spend` 的归属与 `04` 示例的可用性（A6，部分不成立，见下）。
11. `UndecidedReason` 的最终取值集（G11）。
12. `PrescreenIndex`/`Census`/`AnchorCandidate`/`Fingerprint` 是否还有效（G12）。

## 6. 已登记的代码滞后（不计入缺陷）

以下由 R 记录或 `23`/`24` 的迁移清单覆盖，重生成时按文档写即可：

- R27/R37：两个入口点、`Stage` 从请求中移除、`SnapshotInfo` 删除、`CaptureGate`。
- R35：`ClaimRow` 带 `World`、`OwnerId` 去世界、`ClaimMatrix` 先比世界、`ClaimLedger.world()`。
- R36：`ClaimPlan` 不携带行、`ClaimPlanner.derive` 唯一造行、`settle` 收起绑定校验。
- R32：`Sharing`/`Footprint`/`Coverage` 取代 `ClaimMode`/`ClaimScope`。
- R8：`OccupantRef` 带 `SnapshotId`（D-6）。
- R2：`compile` 取代 `validate`。

## 7. 建议的处理顺序

1. 先裁 D-1、D-2、D-5：它们决定包图、阶段承载与视图模型，后面所有条目都依赖这三项。
2. 再裁 D-4、D-6、D-7、D-8、D-9、D-10、D-11、D-12：都是"二选一"的形态问题，裁决后可以成批改。
3. 按 §4 的清单改文档；每改一个文件，把该文件的改动记进 `19` 的一条新裁决（沿用 R35–R37 的格式）。
4. §5 的十二项空白必须在重生成前补完，否则生成的代码会缺类型或缺实现路径。
5. §6 的滞后留给代码迁移，不进文档。

## 附：我抽验后修正的三处

- A6 说 `04` 的 `Budget.of(4_096, 64)` 不存在：`engine/Budget.java:14` 有 `static Budget of(int, int)`（〔已核〕）。成立的是归属问题，不是方法缺失。
- A11 说 `UNREADABLE_CELL` 没有类型：它存在，位于 `predicate/Focuses.java:35`（〔已核〕），只是不叫 `Codes.UNREADABLE_CELL`。文档需要的是一个统一的码持有者，或改用 `FindingCode.of(...)`。
- C-1 说文档的陈旧引用检测失去载体：方向相反，文档（R8）是对的，缺载体的是代码（`OccupantRef` 用 `InstanceId`，〔已核〕）。

（这三条的措辞在 §8.4 里被推翻了两条，见下。）

## 8. 复查后的裁定清单

三份复查报告：`recheck-coverage.md`（J）、`recheck-k-consistency.md`（K）、`recheck-L.md`（L）。**本节取代 §3。**

### 8.1 证据已经定下的，不需要你选

> stage-3 复核对本表有四条否决：`check` 不发布（Q5）、内部重叠是缺陷（Q6）、只留一个枚举（Q7）、`SC-CTX-0109` 的生产者方向。以 `open-questions.md` 为准。

| 项 | 结论 | 依据 |
| --- | --- | --- |
| `check` 是否发布上下文帧 | 待定，见 Q5（本行原写"不发布"，复核不成立） | `19` R27、`I-10`、`G9` |
| 内部舱位重叠 | 待定，见 Q6（`09` §4.2 已允许 `SHARED+SHARED`） | `I-10`、`D-4` |
| `DecisionKind` 与 `Coverage` | 待定，见 Q7（`RULE-D2` 与 L 建议保留两个） | `RULE-D2`、`F7` |
| `PrescreenIndex`/`Census`/`AnchorCandidate`/`Fingerprint` | 删除（`11` §8 自己说被取代，代码没有） | `G12` |
| `UndecidedReason` | 以 `01` 的八值为准，代码补 `LEDGER_UNAVAILABLE` | `G11`、L（该值已在 `01`） |
| 两个二参 `Locus` 工厂 | 删除（无调用者） | `H-R5(A)`、L |
| `SC-CTX-0109` | 引擎是唯一生产者，draft 侧抛无码的 `IllegalStateException` | `H-R3(A)`、L |
| `anyOf()` 空子数组 | 要求 `n ≥ 1` | `RULE-D1(c)`、L（`atLeast` 不以异常拒绝） |
| U6 `kernel.document` | 建包（不并入 `blueprint`） | `F1`、L |
| `OccupantRef` | 带 `SnapshotId`，去掉 `footprint` | `19` R8 〔已核〕 |
| `NestedView` 的粒度 | 不是完整 `WorldView`，按代码的 `NestedLevelSnapshot` | `C-14(b)` |

L 另外指出四句引用需要改口径（方向不变）：`Bay.java` 里的 CHK/BAY 码是异常消息文本而非诊断；九个操作里实际存在的是三个（`check`、`derive`、`ledger`，`settle` 是 `check` 内的内联路径）；`20` §4.2 没有 `CaptureLimits`。`Reply.combine` 那条 L 说错了：它是接口静态成员，隐式 `public`（`Reply.java:73`），所以 `MatchSession` 能跨包调用。

### 8.2 需要你逐项定

**R1 `Outcome` 与分层底座**（这是 D-1 的真正根，也是 K/L 都判错的那一处）

〔已核〕`Outcome.Found(value, subjects: List<OccupantRef>, notes: FindingBundle)`、`Outcome.Missing(FindingBundle)`，位于 `base`；被 `predicate`(5 个文件)、`context`(6)、`spi`(1)、`engine`(4) 使用。于是 `Outcome` 必须位于 `OccupantRef` 与 `FindingBundle` 之上，而 `context`、`predicate` 又都在它之上。

- (a) `Outcome` 移入 `kernel.finding`；`OccupantRef` 移入 `kernel.space`（它是 id + 坐标 + 种类，纯值）；`base` 回到 ids/`SourceSpan`/`Stage`/`Budget`/`CancelToken`。
- (b) `Outcome` 移入 `kernel.occupant`；诊断值随之下沉到 `occupant`。
- (c) 拆开：`base` 只有 `Maybe<T>`（无载荷），携带 subjects+notes 的 `FocusOutcome` 放在 `predicate`。
- (d) 承认 `base` 是"值底座"：`OccupantRef`、`FindingBundle`、`Locus` 都进 `base`，删掉"不依赖任何包"，改为"只依赖 JDK"。

建议 (a)。(b) 让诊断挂在占据物下，语义错位；(c) 会把一个结果类型拆成两个，所有调用点都要改；(d) 最小改动，但 `base` 会继续膨胀，且 `base→space` 的边仍在（`OccupantRef` 持有 `LatticePoint`/`Cuboid`）。

**R2 `ReadScope` 的归属**（依赖 R1 的答案）

〔已核〕`ReadScope(cells, keys: Set<ContextKey<?>>, entities: List<EntityWindow>)`，在 `base`，因此 `base→context`、`base→occupant`。R1(a) 之后它仍无处安放。

- (a) 留在 `base` 并允许三分量（等于 R1(d)）。
- (b) 移入 `kernel.context`，`context` 依赖 `space`/`occupant`，`predicate` 依赖 `context`。
- (c) 拆成 `CellScope`/`KeyScope`/`WindowScope`，由 `predicate` 的 `Check.reads` 组合。

建议 (b)：`ReadScope` 是读闭包，键是主分量，格子与窗口只是计量。

**R3 阶段承载**（F2、H-R6、K 的合并项）

- (a) 不要阶段：撤 `I-6`/`R-08` 的检查点说法，`expand()` 不带参数。
- (b) 单元声明：撤回 `23` §7。
- (c) 目录工厂声明：`Check`/`Computation`/`DerivationSpec` 无阶段，注册时给出，`expand(S)` 按目录分组。
- (d) 由输出键的 tier 推导（`Deriver.tier()` 已有）。

建议 (c)。若你不想保留"按阶段分组的计划"，选 (d) 并删 `expand(S)` 的参数。

**R4 视图模型**（C-2/C-13）

- (a) 区段模型为准，`06` 重写，补 `sectionOf(cell)` 与 `snapshotId()`。
- (b) 逐格模型为准，代码与 `20`/`22` 大改。
- (c) 两层并存，逐格作为区段的便捷外壳。

建议 (a)。

**R5 `OccupantRef` 的连锁**：确认 `SnapshotId` 补进代码（R8），并处理 L 指出的涟漪：`SnapshotId` 目前属于 `engine`，而 `occupant` 要用它，需下移到 `base` 或 `space`。

**R6 捕获 SPI**（L 指出我的 D-7(a) 与 C-15(a) 相反）

- (a) 以 `06` §3.4 三臂 `CaptureResult` 为准。
- (b) 以 `16` §47/§64"返回快照+租约+issues"为准，改写 `06`。

建议 (b)（L 推荐 C-15→`16`）：租约必须与快照同时产出，否则调用方要自己拼装。

**R7 门面与端口**（原 D-8 的四问，分开答）

- 端口：`Host` 唯一 + `create(Catalogs, ClaimLedger)` 重载，还是只有账本形态。
- `derive`：文档形态（`Formed, List<DerivationSpec>, Budget`）还是代码形态（外加 `ContextStore`）。
- `ContextKeys.register`：留在 `context` 并允许 `spi→context`，还是按 `15` 放到 `Catalogs.Builder`。
- 缺失的五个操作（`document`、`compile`、`siting`、`solve`、`explain`）：列入迁移清单还是从门面删除。

建议：`Host` 唯一加账本重载；`derive` 取文档形态；`register` 留 `context`；五个操作列入迁移。

**R8 `LocusMap`**（B-1/B-2）：建（并定 `DEFINITION` 是否携带作者格子）或删（连同 `05`/`13` 的帧转换要求）。

建议建，否则诊断无法在作者坐标与世界坐标之间换算。

**R9 `Placement.inverse`**（B-3，L 修正了我的选项）

(a) 限制定义域不可行：`MIN_XZ` 取反越界，合法摆放也可以没有逆。

- (b) 把 §4.6 的定律改成"当 `inverse()` 非空时成立"。
- (c) 让 `inverse` 返回 `Outcome`/`Optional`，强制调用方处理。

建议 (b)：`@Nullable` 已在代码里，改文档最小；`(c)` 更安全但改动面大。

**R10 `DecisionKind` 与 `Coverage` 合一**（L 注：会作废 `24` §9.1 第 1 条）

(a) 只留 `Coverage`，值名为 `EVERY_CELL`/`ONE_SUBJECT`/`ANY_SUBJECT`；`Focus.decisionKind()` 删除或返回 `Coverage`。
(b) 保留两个类型并规定映射。

建议 (a)，同时修订 `24` §9.1 第 1 条。

**R11 `Reply`**：密封族 + MATCH 备注通道 + 包私有的 `combine`；删 `Verdict`。

**R12 码表与生产者**（初版遗漏的一项）

- 谁拥有注册表：`13` 唯一，还是各族在自己的文件里。
- DESIGN 期违规是异常还是诊断：`SC-DOC`/`SC-OCC`/`SC-RMD` 三族的现状互相矛盾。
- 每个已分配码必须有且只有一个生产者；当前 `SC-OCC-01xx/02xx`、`SC-RMD-02xx–0601`、`SC-ENG-0104`、`SC-CTX-0202` 都没有。

建议 `13` 唯一注册表，各族文件只引用；DESIGN 期一律异常（`IAE`/`ISE`），SETTLE 期一律诊断。

**R13 U10 修葺的归属**（J 发现）：补一个分片审 `14` 与 `22` §11–§15，或在文档里明确标注"未建、不参与重生成"。

**R14 设计面文档的接收方**（J 发现，初版缺失）：`00` 的 I-4/I-10 与代码相反；`22` §11.1 九族 vs 十五族；`19` 有 12 条 R 记录无人审、其中 R16/R29 与代码树矛盾；`guide.zh`、`README.md`、`_translation-guide` 各有多处失真。这些要一并修，否则重生成后文档仍会误导。

### 8.3 覆盖缺口（均需补审或明确排除）

- `14-remedies.md`（U10）无主：约 20 个类型、十条试运行规则、七种编辑未与代码对照；`22` §11.2/§11.3/§12/§13/§14/§15 无人读。
- `RMD` 码表：`14` 提议 `0201–0206`/`0301–0309`/`0401–0404`/`0601`，`13` 只登记 `0001` 与 `0309`；`0206` 被用却不在自己的区间。
- `00` 的表未按表审：`I-4`、`I-10` 与代码相反；`I-5`/`R-04` 提到无载体的 `OrientationPolicy`/`AnchorCandidate`；`_index.md` 的门禁 2 与 4 不可满足。
- `19` 的 12 条未审 R 记录；`R16`（仍写 `StructureKernel.match`）、`R29`（`MatchCompletion` 无代码）与代码树矛盾。
- `docs/guide.zh.md`（新人入口，零分片读过）约十处失真，包括"31 个编译错误 / 测试应保持红"与实测 162 测试 0 失败相反。
- `docs/README.md:21` 指向不存在的适配器目录；`:26-27` 的"没有差距台账"与源码里的三处缺口标记矛盾。
- `zh/_translation-guide.md` §3 说不变量止于 I-15，而 `00` 已有 I-16。

### 8.4 我上一版附录的三处，两处纠错纠反

- A6 的原话是"`04` §7 要求 `Budget` 由 U0 提供，而唯一的 `Budget` 是 `engine` 的请求级类型"，不是"`Budget.of` 不存在"。我的"纠正"打错了靶。
- A11 的原话是"`Codes` 只在 `15-spi.md:32` 声明、词汇表无行，所以 `04` §11.1 的自检失败"，不是"`UNREADABLE_CELL` 没有实现"。我答的常量存在与否，不是它问的问题。
- C-1 那条方向正确：文档（R8）对，代码缺载体。

### 8.5 被我在上一版丢掉、仍然有效的条目

`A3`（`claim` 缺 `finding` 依赖）、`A8`（`Checks` 不在内核类路径）、`A10`（`Finding` 例外于"值类型"规则）、`C-6`/`C-8`/`C-9`/`C-10`/`C-11`（占据物过滤、`Refused` 记录、枚举常量数、`contentDigest`、覆盖格的读状态）、`E RULE-2`/`RULE-3`（自洽检查与修订校验的先后、重叠诊断的产出者）、`F9`/`F10`（`Overlap` 的位置、重复键的生产者）、`G15`（`InstanceBudget` 的位置）、`H14`/`H15`（`at(tier)` 的语义、`ContextKeys` 与 `SC-CTX-0102`）、`I` 的 U6/U10 单元表行。逐条见九个分片报告。

## 9. `CODE` 批次的执行结果与连带修改

按你最新的一条规则执行：**代码里的设计更好就写进文档；代码有问题、或两边都没定好，就留待定**。因此这一批只动"代码对、文档改"的行。

### 9.1 已回填

三组并行，逐行的 applied/held/stale 清单在 `_work/regen/apply-g1.md`、`apply-g2.md`、`apply-g3.md`：

| 组 | 覆盖文件 | 行数 |
| --- | --- | --- |
| G1 | `01`、`03`、`04`、`05`、`06` | 9（`A10`–`A14`、`B-7`、`C-2`、`C-3`、`C-8`） |
| G2 | `07`、`08`、`09`、`13` | 12（`d-D2`、`d-D6`、`d-D10`、`d-D11`、`d-D13`、`d-D15`、`e-D8`–`e-D10`、`e-D15`、`f-F6`、`f-F14`；`f-F14` 的 `Stage` 一半被 F2 待定挡住） |
| G3 | `11`、`12`、`15`、`16`、`18` | 12 |

没有一行被 R 记录挡下，也没有一行因锚点失效而作废。

### 9.2 因回填而必须一起改的文档

- **`C-8` 方向反转。** 该行标的是 `CODE`，但它自己的理由与 `stage3-audit.md` §4 都说该改代码（`FindingCode` 在 `base`，每个码携带者都是 `FindingCode`；`OccupantReadModelTest.java:64` 已经在传一个 `13` 不发布的 id）。已按"文档对、代码滞后"回退：`06` §3.3 的 `Refused` 回到 `FindingCode`，`06` §5 的示例同步为 `FindingCode.of(Family.OCC, 101)`，并新登记 **R38**。
- **逐块门面撤销。** `C-2` 把 `06` 改成区段模型后，`WorldView.summary()/chunkGrid()/chunk()` 已无载体，`OccupancySummary`/`ChunkContent` 两个类型在任何单元里都没有使用者。已从 `01` §D 撤两行、从 `16` §3 的 `LevelSnapshot` 删三个 `@Override`、从 `20` §6.1 删 `ChunkContent` 与"`WorldView` gains …"那段，并把 `ChunkGrid`/`ChunkKey` 的作用限定为"给格子定尺寸"。同一处把 `SectionSlot.Air` 的三分量对齐成 `06` 的 `Air(long revision)`（`20` §4.2 的伪码随之改）。全部并入 R38。
- **`01` §L `EditExecutor.begin`** 改成五参 `(WorldView, Siting, ClaimView, long ledgerRevision, PlanDigest)`，与 `14` §5、`17` §3 一致（`I-13`）。
- **`03` §4 的 `*Context` 行** `DerivationContext` → `DeriveContext`，与代码、`22`、`18` C-13/C-20 一致。
- **`13` §4.3 的 `SC-CTX-0301`**：`P` 由 `✓` 改 `–`（该码的 finding 没有 label，subject 是 `AugmentKey`），`H8`。
- **`SC-EXP` 的重编号跟着走下去**：`d-D13` 把 `0001` 定为"元素测试失败（默认 `mismatchCode`）"后，`17:277` 的例子还写着 `SC-EXP-0002`，已改 `0001`。
- **`01` §E/§F 的六行**按 G2 写进 `07`/`08` 的事实对齐：`ExpectationMeta` 的四分量（`Set<ContextKey<?>> requires` + `FindingCode mismatchCode`，`anonymous()`/`of(...)`）、`ExpectationContext` 的继承与 `withSubject`/`of`、`Focus` 的类型清单（删掉 `block`，补每个 kind 绑定的 subject）、`ClaimView` 的记录形态（`unavailable()`/`rowsIn`）、`ClaimConflict` 的 `FindingBundle`。
- **`22` 的两处**：§6.3 的 `Focuses.door` 返回 `Focus<DoorView>`，§10.1 的 `ClaimConflict` 用 `FindingBundle`。

### 9.3 明确推迟的条目（不是漏掉）

| 条目 | 为什么推迟 |
| --- | --- |
| `Codes` 在 `12:246`、`13:658-726`、`14:341-345`、`15:32` 的其余引用 | `Codes` 这个类型存不存在是 `Q19`，待定 |
| `01` §I 的 `ReadSet` 行（与 `ReadScope` 重复） | 一个类型还是两个是 `Q4`，待定 |
| `ChangeSet` 的两套分量 | `01` §I 已改成代码的六分量（与 `06` §4.8 一致）；`MQ-11` 仍待定的是"要不要两种形态（变更提示 vs 逐格标记）"，不是分量 |
| `NestedView` 还是 `NestedLevelSnapshot` | `C-14` 是 `RULE` 行，待定。补一条事实：`16`/`22` 写的是实现类、`06` 写的是接口，而 `NestedLevelSnapshot implements NestedView`，所以两者**可以并存**（协变返回），真正待定的只是"子视图是不是完整 `WorldView`"，不是名字 |
| `06` §3.4 的捕获 SPI（`CaptureResult`/`SnapshotPolicy`/`OccupantResolver`） | `C-15` 是 `RULE` 行，待定；且代码里这些都还不存在 |
| `18` §2 的测试层名（`.../expectation`、`.../finding`、`.../remedy`） | 打包归属是 `DOC` 判定，属代码迁移 |
| `kernel.expectation` 还是 `kernel.predicate`（`07` 标题、`00` §5、`03` §3、`01` §E 头、`06` §1） | `d-D1` 是 `DOC` 判定：文档的 `expectation` 为准，代码的 `predicate` 在迁移批里改 |
| `01:83`/`01:84` 之外的 `OccupancySummary` 提及 | 已随 9.2 清完，无残留 |

### 9.4 这一批新发现、原先没人报的几处

都不改，因为要么落在 U10（`Q18` 未审），要么是"文档声明的目标态、代码还没实现"：

- `01` §L 的 `ApplyReport` 行写 `(boolean success, List<Finding> findings, Spend spend)`，而 `14` §5 的记录是 `(boolean success, List<Finding> findings, RemedyContext context)`。
- `14:74` 与 `22` §11.3 的 `RemedyContext` 都写 `@Nullable ClaimView claims`，而 `ClaimView.unavailable()` 已经表达"不能回答"；两种风格并存，属 U10 的形态问题。
- `ExpectationContext` 的 `budget()`、`session()`、`occupantAt(p)` 三个成员、`ProbeSession` 整个类型，以及 `Expectations` 的 `sequence`/`atMost`/`exactly`/`trait`/`anyOccupant`/`nothing`/`custom` 七个组合子，文档都声明了、内核里都没有。这是 U3 的目标态，不是文档错误。
- `07` 缺 `eachCell` 一行，已补（代码里有、`09` §5 的示例也在用），并注明它是唯一改变 subject 类型的组合子。


