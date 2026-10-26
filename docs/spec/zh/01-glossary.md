# 01 — 术语表（Glossary）

> 封闭词汇表。一个术语只在这里定义一次；模块链接到本页，而不是复述它。**类型（Type）**
> 列是 Java 名称；**先前术语（Prior）** 列在有对应项时给出最接近的 ArchitectCumulus
> 术语，供先前技术的读者参考，不构成兼容承诺。

## A. 不得误读的叙述术语

这些是读者最可能过度解读的词。每个词只有一种允许的读法。

| 术语 | 允许的读法 | 禁止的读法 |
| --- | --- | --- |
| **元素（Element）** | 一个或多个单元格中的任意占用体：方块、方块实体、实体、嵌套结构，或宿主定义的种类。 | “方块”。一个说“在……处的方块”的谓词是缺陷（R-01）。 |
| **控制器（Controller）** | 一个已声明的锚点角色，其 `AnchorSpec` 在定义原点被读取。*计算中心*由锚点候选与 `OrientationPolicy` 解析得出。 | 必需的 `Entity` 或方块实体类型。没有任何内核类型命名控制器（R-04）。 |
| **结构实体（Structure entity）** | *已摆放*的蓝图：一个 `Siting` + 声明 + 派生的 `StructureContext`；由 `MatchResult.Formed` 及其 `InstanceRef`、`StructureContext` 表示。 | `net.minecraft.world.entity.Entity`。适配器可以用方块实体、附属数据（attachment）或什么都不用来承载一个实例。 |
| **阶段（Stage）** | 带自己的 `CheckPlan` 的生命周期检查点：`DESIGN`、`PROBE`、`FORM`、`SETTLE`。由引擎选择它；作者不声明，调用方也不选择。 | 一个 tick 预算、一个建造“代际”，或一个加载器阶段。 |
| **代际（Generation）** | 从不单独使用。只有两个限定术语：**数据生成（data generation）**（R-09，外部工具产出文档）与**实例代际（instance generation）**（`InstanceRef.generation`，已摆放结构的重新检查计数器）。 | 阶段或版本的泛化同义词。 |
| **匹配（Match）** | 对 `(blueprint, snapshot, siting, policy)` 的一次纯净求值。 | 模糊搜索、由调用方拥有的逐方块循环，或世界修改。 |
| **蓝图（Blueprint）** | 引擎所匹配的、不可变且已校验的模板。它由 `BlueprintDocument` 编译而来。 | 可变构建器、世界对象，或数据包文件。 |
| **快照（Snapshot）** | 在一个修订版本上捕获的不可变 `WorldView`。 | 实时世界句柄或可变缓存。 |
| **声明（Claim）** | 带共享值（sharing value）的单元格归属记录。 | 锁、区块声明，或权限。 |
| **补救方案（Remedy）** | 对一条诊断项采取行动的方式：`Repair`、`Preview` 或 `ExplainRemedy`。 | 保证能修好的修复。适用性是显式的。 |
| **诊断项（Finding）** | 一份结构化报告，带稳定诊断码、严重级别、标签与注释。 | 一行日志或一个异常。 |
| **内核（Kernel）** | `dev.vfyjxf.cumulus.kernel` 下的一切，其 classpath 上没有 Minecraft。 | mod、适配器，或整个项目。 |

## B. U0 — 基础（`kernel.base`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 命名空间 id（Namespaced id） | `NamespacedId` | 不可变的 `(namespace, path)` 对；内核中唯一的命名类型。从 `ns:path` 解析、校验，先按 namespace 再按 path 排序。 | `NamespacedKey` |
| 预算（Budget） | `Budget` | 一次尝试的上限：只数读取的单元格与复制的区段，别无其他。不可变；`Budget.of(cells, sections)`。不含时间，也没有逐单元单价。 | `Budget` |
| 消耗（Spend） | `Spend` | 实际消耗的量：`(int cells, int sections)`。 | `Spend` |
| 取消（Cancellation） | `CancelToken` | 注入的协作式取消句柄；`isCancelled()`。绝不是线程中断。 | `CancelToken` |
| 稳定性（Stability） | `Stability` | `STABLE`、`EXPERIMENTAL`、`DEPRECATED`；附加在某个 SPI 名称上的承诺。 | `Stability` |
| 阶段（Stage） | `Stage` | `DESIGN`、`PROBE`、`FORM`、`SETTLE`；由引擎选择、带自己的 `CheckPlan` 的生命周期检查点。 | `Phase` |
| 实参表（Arguments） | `Arguments` | 以 `ParameterKey<T>` 为键的不可变异构映射，保存片段或计算被调用时的**实际实参值**。 | — |
| 形参键（ParameterKey） | `ParameterKey<T>` | 单个**形参**的类型化标识：`(NamespacedId id, Class<T> type)`。 | — |
| 源区间（Source span） | `SourceSpan` | `(NamespacedId document, int start, int end)`：一个文档内的半开字符区间。位于 `base`，使 `space` 永不依赖 `finding`。 | — |
| 数据视图（Data view） | `DataView` | 格式中立的读取接口：`kind()`、`get(String)`、`list()`、`keys()`（`SequencedSet`）、`asString/asInt/asBoolean(default)`、`@Nullable SourceSpan span()`。位于 `base`，使 `expectation` 永不依赖 `document`。 | `DataView` |
| 数据种类（Data kind） | `DataKind` | `OBJECT`、`ARRAY`、`STRING`、`NUMBER`、`BOOLEAN`、`NULL`。 | — |
| 诊断码（Finding code） | `FindingCode` | 稳定标识符 `SC-<FAMILY>-<NNNN>`，例如 `SC-SPC-0001`，`record (Family family, int number)`；绝不重用为不同含义。它属于 `base` 而非 `finding`：`occupant` 与 `space` 都携带它（`06` §3.3 的 `Refused`、`R38`），而两者都不得引用 U9。 | `DiagnosticCode` |

## C. U1 — 空间与朝向（`kernel.space`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 格点（Lattice point） | `LatticePoint` | 蓝图局部网格中的整数 `(x, y, z)`。原点是蓝图声明的原点。 | `Cell` |
| 格键（Lattice key） | `LatticeKey` | 采用 Minecraft 位布局（x:26、z:26、y:12）的打包 `long`，用作映射键；`LatticeKey.of(point)` / `toPoint()`。 | `CellKey` |
| 轴（Axis） | `Axis` | `X`、`Y`、`Z`；`Heading` 所在的轴。不存在带符号的轴类型，因此朝向变换作用于点，而不是轴。 | `Axis` |
| 长方体（Cuboid） | `Cuboid` | 半开轴对齐盒 `[min, max)`；在某个轴上 `min == max` 即为空。唯一的原始占地轮廓。 | `Box` |
| 长方体集合（Cuboid set） | `CuboidSet` | 规范、互不相交、有序的 `Cuboid` 集合；支持并、交、差、平移、体积、包含。它是一种规范形式而非唯一分解：相等的单元格集合用双向 `containsAll` 比较。绝不是体素枚举。 | `Region` |
| 朝向（Heading） | `Heading` | 按 Minecraft 顺序的六个基本方向：`NORTH`、`SOUTH`、`WEST`、`EAST`、`UP`、`DOWN`；`opposite()`、`axis()`、`step()`。 | `Facing` |
| 偏转（Turn） | `Turn` | 绕 Y 轴的四分之一转：`NONE`、`CLOCKWISE`、`HALF`、`COUNTERCLOCKWISE`。它是一个生成元，不是 48 个朝向之一的名称。 | `Rotation` |
| 倾斜（Tilt） | `Tilt` | 选择哪个轴为“上”的六种取值：`LEVEL`、`PITCH_UP`、`PITCH_DOWN`、`ROLL_LEFT`、`ROLL_RIGHT`、`INVERTED`。一个生成元。 | `Tilt` |
| 镜像（Flip） | `Flip` | 反射：`NONE`、`LEFT_RIGHT`、`FRONT_BACK`。一个生成元。 | `Mirror` |
| 朝向变换（Orientation） | `Orientation` | 48 元群中的一个元素，由 `Turn × Tilt × Flip` 生成（72 个名字映到 48 个元素）；`compose`、`inverse`、`apply(Heading)`、`apply(LatticePoint)`、`all()`；规范顺序为先偏转、再倾斜、再镜像。 | `Orientation` |
| 摆放（Placement） | `Placement` | `(Orientation orientation, LatticePoint offset)`：蓝图如何被转向与平移。 | `Transform` |
| 安放位（Siting） | `Siting` | `(LatticePoint origin, Placement placement)`：蓝图在世界中站立的位置。`toWorld(local)` / `toLocal(world)`。 | `Pose` |
| 定位域框架（Locus frame） | `LocusFrame` | `DEFINITION`、`LOCAL`、`WORLD`、`KEY`：诊断可以指向的坐标框架；`KEY` 命名一个值，而不是单元格。 | `DefSpan`/`LocalSpan`/`WorldSpan` |
| 定位域（Locus） | `Locus` | 一个框架 + 一个非空 `CuboidSet`（框架不携带单元格时为空集）+ 一个可选 `KeyLocus` + 一个可选 `SourceSpan`；`KEY` 定位域携带一个 `KeyLocus` 且不含单元格，`DEFINITION` 定位域要求其源区间。 | `Span` |
| 键定位域（Key locus） | `KeyLocus` | `(InstanceId instance, NamespacedId key)`：一个 `KEY` 定位域所命名的那一个实例键。 | — |
| 定位域映射（Locus map） | `LocusMap` | 针对一个 `Siting`，在单元格框架之间转换 `Locus`；`toWorld`、`toLocal`、`toDefinition`；`KEY` 定位域不参与转换。 | `SpanMapper` |
| 区块键（Chunk key） | `ChunkKey` | `ChunkGrid` 中的 `(int cx, cy, cz)` 下标，按 `(cy, cz, cx)` 排序。不是 Minecraft 类。 | — |
| 区块网格（Chunk grid） | `ChunkGrid` | `(int cellSize, LatticePoint origin)`：世界框架下的立方区块网格；`chunkOf`、`boundsOf`、`chunkCount`、`chunksOf`。`cellSize` 是数据；适配器传 16。 | — |
| 区段键（Section key） | `SectionKey` | `(World world, int x, int y, int z)`：一个世界的 16³ 区段下标，排序为世界、`x`、`z`、`y`；不是缓存键。 | — |

## D. U2 — 占用体与世界（`kernel.occupant`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 占用体种类（Occupant kind） | `OccupantKind` | 一个命名空间 id 记录，带常量 `BLOCK`、`BLOCK_ENTITY`、`ENTITY`、`NESTED`、`OTHER`；即一个 `OccupantResolver` 所服务的族。`OTHER` 携带宿主 id。 | `ElementKind` |
| 占用体引用（Occupant ref） | `OccupantRef` | 快照范围内的句柄 `(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)`；相等性即同一次捕获内的同一性。 | `ElementRef` |
| 占用体描述符（Occupant descriptor） | `OccupantDescriptor` | 与版本无关的描述：`(type, kind, Map<String,String> properties, List<NamespacedId> tags)`。 | `ElementDescriptor` |
| 特征键（Trait key） | `TraitKey<T>` | 可查询特征的类型化标识：`(NamespacedId id, Class<T> type)`。 | `ElementAspect` |
| 特征（Trait） | `Trait<T>` | 由占用体快照携带的 `(TraitKey<T>, @Nullable T value)` 对；存在的特征带 null 值是一个事实，缺失的特征则是不可读。 | `ElementAspect` 值 |
| 占用体快照（Occupant snapshot） | `OccupantSnapshot` | 不可变读模型：ref、descriptor、占地轮廓（`Cuboid`）、特征、可选 `NestedView`。空气是普通占用体；`null` 表示“不可读”。 | `ElementSnapshot` |
| 区段槽（Section slot） | `SectionSlot` | 一个区段的回答：`Air(revision)`、`Present(section)`、`Refused(status, code)`；`readable()` 为 `Air` 或 `Present`，而 `Refused` 槽不能携带可读状态。 | — |
| 区段快照（Section snapshot） | `SectionSnapshot` | 一个冻结的区段：`(SectionKey key, Map<LatticePoint, OccupantSnapshot> cells)`；`contentDigest()`、`occupantAt(cell)`、`occupants(box, filter)`。 | — |
| 占用体过滤器（Occupant filter） | `OccupantFilter` | 盒查询用的 `(type, kind, trait)` 谓词；为 `null` 的组件不施加约束。 | — |
| 实体窗口（Entity window） | `EntityWindow` | `(Cuboid cells, NamespacedId type)`：一种实体类型可以在其中移动的盒；`MAX_ENTITY_SPAN = 3`；由 `ReadScope.entities` 携带。 | — |
| 实体键（Entity key） | `EntityKey` | `(UUID id, NamespacedId type, long dataDigest)`：一个实体在窗口差异中的身份。 | — |
| 嵌套视图（Nested view） | `NestedView` | 由嵌套占用体暴露的子 `WorldView`；`parent()`、`depth()`、`ancestry()`、`occupant(LatticePoint)`。 | `NestedElementView` |
| 世界视图（World view） | `WorldView` | 内核唯一的世界窗口：`SnapshotId snapshotId()`、`SectionSlot slot(SectionKey)`、`@Nullable OccupantSnapshot occupant(LatticePoint)`、`@Nullable OccupantSnapshot occupant(OccupantRef)`、`List<OccupantSnapshot> occupants(Cuboid, OccupantFilter)`、`@Nullable NestedView nested(LatticePoint)`、`long contentDigest(SectionKey)`。 | `WorldView` |
| 读取状态（Read status） | `ReadStatus` | `READABLE`、`OUT_OF_BOUNDS`、`NOT_CAPTURED`、`DENIED`、`STALE`。`READABLE` 且占用体为空气是一个事实；其他每个值都是 `UNREADABLE`，绝不是“空气”。 | — |
| 世界（World） | `World` | 内核视角下的一个 Minecraft 层级：`record World(NamespacedId id)`，`id` 是该层级的键（`minecraft:overworld`）。Minecraft 侧把它叫作 level（`ServerLevel`、`LevelEvent.Unload`），这些名称按原样引用，绝不用作内核术语。`World` 不是一个地点：坐标存在于 `LatticeKey`，因此两个世界中的相同坐标是两个不同的单元格。 | `dimension` |
| 属主（Owner） | `OwnerId` | `(NamespacedId owner)`：谁持有声明——一名玩家或一个队伍。世界不是它的一部分；声明另行命名自己的世界。 | — |
| 快照 id（Snapshot id） | `SnapshotId` | `(World world, long revision)`；revision 按世界单调递增。 | `SnapshotId` |
| 快照策略（Snapshot policy） | `SnapshotPolicy` | 捕获模式（capture mode）、必需的占用体种类、必需的特征、最大元素数、最大嵌套深度、陈旧容忍度。 | `SnapshotPolicy` |
| 世界窗口（World window） | `WorldWindow` | `(Cuboid bounds, int maxElements)`：要捕获的内容。 | — |
| 世界捕获（World capture） | `WorldCapture` | 宿主 SPI，从 `WorldWindow` 与 `SnapshotPolicy` 产出 `WorldView`；`null` 表示“无法提供”。 | `WorldCapture` |
| 占用体解析器（Occupant resolver） | `OccupantResolver<R>` | 宿主 SPI，把一个宿主原始占用体转换为 `OccupantSnapshot`；`kinds()` 声明它服务的族，`resolve` 可以拒绝。 | `WorldOccupants` |
| 解析上下文（Resolve context） | `ResolveContext` | 解析器可见的内容：`snapshotId()`、`anchor()`、`remainingDepth()`、`policy()`。 | — |
| 众所周知的特征（Well-known traits） | `Traits` | 内核保留的 `TraitKey` 常量（`ITEM_INVENTORY`、`ENERGY`、`FLUID`、`TIER`）；附加组件可以定义自己的。 | — |
| 嵌套层级快照（Nested level snapshot） | `NestedLevelSnapshot` | 一个嵌套占用体冻结后的内部，在它自己的坐标系中读取：`static of(OccupantRef parent, int depth, Map<LatticePoint, OccupantSnapshot>)`、`parent()`、`depth()`、`contentDigest()`、`occupantAt(LatticePoint)`、`occupants(Cuboid, OccupantFilter)`。属于内核侧，适配器不拥有它。 | — |

## E. U3 — 期望（`kernel.expectation`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 期望（Expectation） | `Expectation<T>` | 对一个 `Bay` 所绑定主体的测试：`Reply test(ExpectationContext<T>)`、`Class<T> subject()`、`default ReadScope reads()`、`default ExpectationMeta meta()`。这是内核用来指称“谓词”的词；`Predicate` 因 `java.util.function.Predicate` 而被禁用。 | `CellPredicate` |
| 期望上下文（Expectation context） | `ExpectationContext<T>` | 测试可见的内容：`subject()`（`T`）与 `subjects()`（绑定的 `OccupantRef`，取自 `Found`），以及 `withSubject(value, subjects)`——它重新定型主体，使舱位级测试可以把一个单元格交给单元格级测试；`of(base, subject, subjects)` 从 `FocusContext` 构建它。它扩展 `FocusContext`，因此 `bay()`（`BayView`）、`view()`、`siting()`、`cells()`、`stage()`、`locus()` 与 `AggregateContext` 的 `read`/`occupants`/`context()` 一并继承。 | `PredicateContext` |
| 探测会话（Probe session） | `ProbeSession` | 运行中尝试的只读面：`occupant(LatticePoint)`、`claimsAt(LatticePoint)`（仅限冻结账本；会话预留保持私有）、`binding(BindingKey<T>)`、`remaining()`、`stage()`。 | `MatchSessionView` |
| 判定（Verdict） | `Verdict` | `MATCH`、`MISMATCH`、`ABSTAIN`、`UNREADABLE`。`ABSTAIN` 表示“该期望拒绝作答”；`UNREADABLE` 表示“快照无法回答”。 | `Verdict` |
| 应答（Reply） | `Reply` | `(Verdict verdict, Bindings bindings, FindingBundle findings)`，从不泛型化：每次检查一个应答，无论主体类型为何。只有 `MATCH` 可以携带绑定；`MISMATCH` 与 `UNREADABLE` 必须携带至少一条诊断项。 | `Outcome` |
| 绑定（Bindings） | `Bindings` | 以 `BindingKey<T>` 为键的不可变异构映射；由一次成功的期望产出，并由组合器合并。 | — |
| 绑定键（Binding key） | `BindingKey<T>` | 单个绑定值的类型化键 `(NamespacedId id, Class<T> type)`。 | — |
| 期望元数据（Expectation meta） | `ExpectationMeta` | `(NamespacedId id, int specificity, Set<ContextKey<?>> requires, FindingCode mismatchCode)`；目录身份、报告顺序提示、测试所需的 `ContextKey`，以及一次普通不匹配所携带的诊断码。工厂方法是 `anonymous()` 与 `of(id, specificity, mismatchCode)`。不存在 `cost` 或 `admission`：预算只数单元格与区段（`22` §9.5）。 | `PredicateMeta` |
| 期望工厂（Expectation factory） | `ExpectationFactory` | `NamespacedId id()`、`Expectation<?> create(DataView args, FactoryContext ctx)`；数据驱动的解析步骤。 | `PredicateFactory` |
| 工厂上下文（Factory context） | `FactoryContext` | 工厂在构建期望时可见的内容：目录快照与文档来源。 | — |
| 组合器（Combinators） | `Expectations` | 静态方法 `allOf`、`anyOf`、`not`、`sequence`、`atLeast`、`atMost`、`exactly`、`block`、`air`、`trait`、`anyOccupant`、`nothing`、`custom`。每个组合器都返回其子项主体类型的 `Expectation<T>`，因此一个 `DoorView` 子项与一个 `BayView` 子项不能组合。 | `Predicates` |
| 焦点（Focus） | `Focus<T>` | 检查的读取那一半：`Outcome<T> take(FocusContext)`、`Class<T> subject()`、`DecisionKind decisionKind()`、`CuboidSet staticDecisionCells(Cuboid)`、`ReadScope reads(Cuboid bayCells)`、`FindingBundle describeMissing()`。种类是数据，且各自绑定自己的主体：`bay` → `BayView`，`allCells` → `List<OccupantSnapshot>`，`occupant` → `OccupantSnapshot`，`entity` → `EntitySnapshot`，`nested` → `NestedView`，`context` → `T`，`door` → `DoorView`。 | — |
| 焦点结果（Focus outcome） | `Outcome<T>` | `Found(value, subjects, notes)` 或 `Missing(findings)`，带 `fold(onFound, onMissing)`；`Found.subjects` 是构建舱位绑定的唯一途径，缺失的主体永不进入 `test`。 | — |
| 决策种类（Decision kind） | `DecisionKind` | `EVERY_CELL`、`ONE_SUBJECT`、`ANY_SUBJECT`：焦点在 DESIGN 可以断言的内容，按该舱位的 `Coverage` 校验。 | — |
| 读取范围（Read scope） | `ReadScope` | 一个单元声明的读取闭包：单元格、键与实体窗口。`Check.reads(bayCells)` 是其焦点与期望的并集；舱位焦点读取整个舱位。 | — |

## F. U4 — 声明（`kernel.claim`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 共享规则（Sharing） | `Sharing` | `EXCLUSIVE`、`SHARED`：同一格上是否排斥另一份登记。 | — |
| 足迹（Footprint） | `Footprint` | 一个舱位拥有哪些单元格：`Box`（舱位盒子）、`Bound`（非空气绑定足迹的并集）、`Declared`（声明的 `CuboidSet`）、`None`（无）。声明保护结构所占用或所声明的内容，绝不保护它仅仅读取的内容。 | — |
| 覆盖（Coverage） | `Coverage` | `EVERY_CELL`、`ONE_SUBJECT`、`ANY_SUBJECT`：某个舱位上的匹配必须确立的内容——每个单元格都已判定、单元格等于所绑定的占地轮廓，或完全不涉及单元格。合法性是规则 R1–R4（`24`）。 | — |
| 声明行（Claim row） | `ClaimRow` | `(World world, LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting, NamespacedId bay)`：一个世界的一个单元格，由一次摆放的某个舱位拥有；`(world, owner, siting)` 既是替换范围，也是自冲突排除条件。 | `Claim`、`ClaimKey` |
| 声明集（Claim set） | `ClaimSet` | `(List<ClaimRow> rows, long revision)`：一次 `settle` 写入的内容，随 `Formed` 携带。 | — |
| 声明裁决（Claim verdict） | `ClaimVerdict` | 密封的 `Settled(revision, rows)` \| `ClaimConflict(rows, findings)` \| `RevisionMoved(expected, actual)`。 | `Settlement` |
| 声明账本（Claim ledger） | `ClaimLedger` | 一个句柄绑定到一个世界：`World world()`、`long revision()`、`ClaimVerdict settle(SettleRequest)`、`ClaimVerdict release(OwnerId, Siting)` 与 `ClaimView read(Cuboid)`；视图回答 `rows()`、`revision()` 与 `available()`，其中 `available() == false` 表示“无法回答”，绝不是空声明集。`settle` 与 `release` 是唯一的写入者；调用 `release` 的三个生命周期点——实例被移除、一次移动、一次世界卸载——见 `23` §5。把一个其他世界的句柄交给内核是 `SC-CLM-0007`。 | `ClaimLedger` |
| 声明矩阵（Claim matrix） | `ClaimMatrix` | 唯一的冲突谓词：`static boolean conflicts(ClaimRow a, ClaimRow b)`——当且仅当两行属于同一个世界、命名同一个单元格、不是同一个 `(owner, siting)`，且至少一个为 `EXCLUSIVE` 时为真。 | — |
| 结算请求（Settle request） | `SettleRequest` | `(World world, OwnerId owner, Siting siting, List<ClaimRow> rows, long expectedRevision)`：账本那一次 CAS 的单位；每一行的世界都必须等于请求的世界（`SC-CLM-0007`）。 | — |
| 声明视图（Claim view） | `ClaimView` | `record (List<ClaimRow> rows, long revision, boolean available)`：与某个盒相交的行，按规范顺序排列，读到它们时的修订号，以及 `available`，其中 `false` 表示“无法回答”，绝不是空声明集——不可用的视图不持有任何行（`unavailable()` 是它的工厂），`rowsIn(Cuboid)` 收窄它。 | — |
| 声明冲突（Claim conflict） | `ClaimConflict` | `(List<ClaimRow> rows, FindingBundle findings)`：阻塞的已持有行，加上冲突位于单个请求内部时该请求自身的冲突行；不写入任何内容。 | `ClaimConflict` |
| 声明计划（Claim plan） | `ClaimPlan` | 密封的 `Matched(long ledgerRevision, List<BayBinding> bindings)` \| `Invalid(FindingBundle findings)`：`check` 发现了什么，以及它读取的修订号。它**不携带任何行**——每一行都由 `ClaimPlanner` 派生，因此调用方无法把自己构造的行交给 `settle`。 | — |
| 声明规划器（Claim planner） | `ClaimPlanner` | 唯一的行工厂：`Derived derive(World, Blueprint, Siting, OwnerId, Collection<BayBinding>)`，纯净且确定性（无视图、无账本、无 I/O），返回 `(rows, overlaps, staticFootprint)`。 | — |

## G. U5 — 蓝图与阶段计划（`kernel.blueprint`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 舱位（Bay） | `Bay` | 绑定到一个 `Requirement`（检查 + 计算）的具名 `Cuboid`，带 `Coverage`、`Footprint`、`Sharing` 与一个可选描述。一个舱位可以是多个单元格（门的情形）。 | `Slot` |
| 接缝（Seam） | `Seam` | 片段上声明的可连接边：`(SeamId id, Cuboid cells, SeamKind kind, Map<String,String> properties)`。 | `FragmentInterface` |
| 接缝 id（Seam id） | `SeamId` | 蓝图局部的命名空间 id。 | — |
| 接缝种类（Seam kind） | `SeamKind` | `ITEM`、`FLUID`、`ENERGY`、`STRUCTURAL`、`CUSTOM`。 | — |
| 片段（Segment） | `Segment` | 蓝图中可复用、可参数化的一块：key、bounds、bays、seams、anchors。这是内核用来指称“fragment”的词。 | `Fragment` |
| 片段键（Segment key） | `SegmentKey` | `(NamespacedId id, Arguments arguments)`；身份包含实参。 | `FragmentKey` |
| 锚点角色（Anchor role） | `AnchorRoles` | `PRIMARY`、`FRONT`、`CENTER` 等作为 `NamespacedId` 常量；角色就是一个 `NamespacedId`，绝不是它自己的类型，自定义角色就是普通 id。绝不是必需的实体。 | `AnchorRole` |
| 锚点规格（Anchor spec） | `AnchorSpec` | `(NamespacedId role, NamespacedId property, String value)`：在定义原点读取的一个属性，一个单元格。 | `AnchorSpec` |
| 朝向策略（Orientation policy） | `OrientationPolicy` | 允许的 `Orientation`、主锚点角色，以及是否要求仅直立。 | `OrientationPolicy` |
| 蓝图（Blueprint） | `Blueprint` | 不可变的已校验模板：id、bounds、bays、seams、anchors、朝向策略、按阶段的计划、声明规格、派生规格、实参、来源、已声明的重叠。 | `StructureDefinition` |
| 蓝图构建器（Blueprint builder） | `BlueprintBuilder` | 流式构建；每次 `bay`、`seam`、`anchor`、`check`、`compute`、`derive` 调用都返回构建器；`build()` 执行校验。 | — |
| 声明规格（Claim spec） | `ClaimSpec` | `(Sharing defaultSharing, Map<NamespacedId, Sharing> overrides)`。 | `ClaimSpec` |
| 派生规格（Derivation spec） | `DerivationSpec` | `(NamespacedId deriver, Arguments args, int order)`；一个声明式引用，在派生时通过目录解析。 | `DerivationSpec` |
| 阶段计划（Stage plan） | `CheckPlan` | `(Stage stage, List<Computation<?>> computations, List<DerivationSpec> derivations, List<Check<?>> checks)`：经过区段/聚合/检查展开之后，某一阶段的有序单元。 | `CheckStep`、`CheckKind` |
| 检查（Check） | `Check<T>` | `(NamespacedId id, NamespacedId bay, Focus<T> focus, Expectation<T> expectation, @Nullable String description)`：一个焦点与一个期望钉在同一个 `T` 上，属于且只属于一个舱位。运行在哪个阶段由引擎而非作者决定。 | `CheckStep`、`CheckKind` |
| 检查结果（Check result） | `CheckResult` | `(Reply reply, List<OccupantRef> subjects, FindingBundle notes)`：判定、焦点绑定的主体（声明足迹的唯一来源）与非致命注记。 | — |
| 上下文键（Context key） | `ContextKey<T>` | `(NamespacedId id, Class<T> type, Retention, @Nullable Merge<T>, @Nullable T whenAbsent)`：唯一的键类型，也是框架、检查或 `RuntimeState` 唯一使用的键。相等性是 `(id, type)`；唯一性属于 `Catalogs.contextKeys`，不属于该记录。只有当区域的每个区段都可读时，`whenAbsent` 才生效。 | `ContextKey` |
| 合并（Merge） | `Merge<T>` | `combine(T left, T right)`：非空的 `merge` 把该键标记为由区段贡献，并禁止 `put`；`null` 则标记为单写者，并禁止 `contribute`。贡献按 `SectionKey` 排序并自左向右折叠；没有单位元，因此分区方式不能改变结果。 | `Reducer` |
| 保留（Retention） | `Retention` | `ATTEMPT` 丢弃，`OUTCOME` 进入 `MatchOutcome.context`，`CONTEXT` 发布到该阶段的框架；这是唯一的生命周期。 | — |
| 计算（Computation） | `Computation<T>` | 产出一个 `ContextKey<T>` 的计划单元：`output()`、`phase()`、`inputs()`、`region()`、`compute(ComputationContext)`。同一个类型服务两个阶段；区段运行执行贡献，聚合运行执行 put，阶段由引擎指派。 | — |
| 状态阶段（State phase） | `StatePhase` | `SECTION` 或 `AGGREGATE`，由 `Computation.phase()` 返回：`SECTION` 对 `region()` 的每个区段各运行一次，且不能读取其他区段；`AGGREGATE` 在屏障之后运行一次。键本身不携带它。 | — |
| 上下文草稿（Context draft） | `ContextDraft` | 一个阶段尚未可见的写入：在单写者键上用 `put(key, value)`，在由区段贡献的键上用 `contribute(key, partial)`；对键用错动词是 `SC-CTX-0109`。 | — |
| 上下文视图（Context view） | `ContextView` | 读取面：`get(key)` 可为 null，供不需要该值也能继续的调用方使用；`require(key)` 返回 `Outcome`（缺失即 `Missing`，绝不是裸 null）。 | — |
| 上下文键注册（Context keys） | `ContextKeys` | 键的注册路径：`register(draft, ...)`；同一个 id 的两次注册是 `SC-CTX-0102`。 | — |

## H. U6 — 文档与数据（`kernel.document`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 蓝图文档（Blueprint document） | `BlueprintDocument` | `(int schemaVersion, NamespacedId id, List<SegmentDocument>, List<AssemblyDocument>, Map<String,DataView> extensions)`；创作形态，允许是部分且参数化的。 | `StructureDocument` |
| 片段文档（Segment document） | `SegmentDocument` | 一个片段声明：id、bounds、参数默认值、bays、seams；其 `expectation` 体是不透明的 `DataView`，在装配时由 `ExpectationFactory` 解析。 | — |
| 装配文档（Assembly document） | `AssemblyDocument` | 一次摆放：`as`、`SegmentKey`、`at`、`overlap`；即蓝图文档的摆放那一半。 | — |
| 文档模式（Document schema） | `DocumentSchema` | 字段语法与受支持的 `schemaVersion` 范围；规范字段顺序与默认值；`COMPACT`/`CANONICAL`/`AUTO` profile。 | `DocumentSchema` |
| 文档 profile（Document profile） | `DocumentProfile` | `COMPACT` 接受省略 `segments`、`assembly`、`orientation`、`context`、`computations` 与 `checks`；`CANONICAL` 是显式形式；`AUTO` 由数据包按顶层形状检测。 | — |
| 紧凑 writer（Compact writer） | `CompactWriter` | 把 `BlueprintDocument` 投影成 compact JSON profile，供工具/导出使用。 | — |
| 数据桥（Data bridge） | `DataBridge<T>` | 宿主编解码转换：`fromHost(T)` / `toHost(BlueprintDocument)`；内核永不依赖 `T`。 | `DataBridge` |
| 数据前端（Data frontend） | `DataFrontend` | 文本前端：`read(String)` / `write(BlueprintDocument)`；JSON 与 SNBT 是随附的前端。 | `DataFrontend` |
| 规范化器（Canonicalizer） | `Canonicalizer` | 往返归一器：相同内容 ⇒ 相同字节。 | `Canonicalizer` |
| 片段库（Segment library） | `SegmentLibrary` | 查询 `@Nullable Segment lookup(SegmentKey)`；缺失片段是校验问题，不是异常。 | `FragmentLibrary` |
| 片段装配器（Segment assembler） | `SegmentAssembler` | 使用 `SegmentLibrary` 的纯组合 `BlueprintDocument -> Blueprint`。 | `FragmentAssembler` |
| 片段发射器（Segment emitter） | `SegmentEmitter` | 外部生成契约：`void emit(Segment, DataView sink)`。 | `Generator` |
| 文档校验器（Document validator） | `DocumentValidator` | 设计期静态检查；返回 `List<ValidationIssue>`，绝不抛出。 | `DefinitionValidator` |
| 校验问题（Validation issue） | `ValidationIssue` | `(FindingCode code, Severity severity, String message, @Nullable Locus locus)`。 | `ValidationIssue` |
| 来源（Provenance） | `DocumentProvenance` | `(NamespacedId source, @Nullable SourceSpan range)`，附加在已编译的蓝图上。 | — |

## I. U7 — 引擎（`kernel.engine`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 待定摆放（Place） | `Place` | 密封 `Unplaced` \| `Pinned(Siting)` \| `Open(LatticePoint)`：宿主尚未解析的摆放，只由调度器的 `CheckRequest` 携带（`20` §3）；`MatchRequest` 总是携带已解析的 `Siting`。 | — |
| 匹配请求（Match request） | `MatchRequest` | `(Blueprint blueprint, Siting siting, SnapshotLease lease, MatchPolicy policy, InstanceBudget budget, PinBudget pins, InstanceRecord record, OwnerId owner, @Nullable Ticket ticket)`；用流式构建器构建。`world()` 与捕获 id 由 `lease` 派生，`ticket()` 是宿主为本次派发准备的槽位令牌。安放位在请求存在之前就已解析，因此既没有 `Place`，也没有声明的 `Stage`。 | `MatchRequest` |
| 快照租约（Snapshot lease） | `SnapshotLease` | `AutoCloseable` 捕获句柄：`id()`、`view()`、`retain(PinBudget)`、`changeCount()`、`isValid()`、`isOpen()`、`close()`。`changeCount()` 是本次捕获以来关注区域中的标记数；`isValid()` 在其世界被替换或句柄被关闭后为 false。每个实例一个属主；`Formed` 与 `Admitted` 持有子句柄。 | — |
| 令牌（Ticket） | `Ticket` | `(long serial)`：宿主的槽位计数器，每次提交递增，使槽位可以合并更新的请求。属于队列状态；它不是捕获的一部分，任何捕获比较都不读它。 | — |
| 匹配策略（Match policy） | `MatchPolicy` | `(int maxSectionsPerUnit, CacheMode cache, boolean parallel)`；`defaults()` 是 `(8, OUTCOMES, false)`，宿主只能把它收窄。任何组件都不能改变判定、诊断包或 `Spend`（`11` §4.7）。 | `MatchPolicy` |
| 驻留预算（Pin budget） | `PinBudget` | `retain` 计入 `retainedBytes` 的上限；耗尽时是带 `SC-MC-0502`（`PIN_REFUSED`）的 `Missing`，绝不是强制关闭。 | — |
| 实例预算（Instance budget） | `InstanceBudget` | 由调度器槽位拥有、按引用传递的逐实例预算；请求不能重置它，丢弃与尝试在同一槽位中计数。 | — |
| 匹配会话（Match session） | `MatchSession` | 每次尝试的可变状态：绑定、预留、诊断项、消耗、剩余预算。只通过 `ProbeSession` 暴露给扩展，另外给引擎一个窄写面。 | `MatchSession` |
| 匹配结算器（Match settler） | `MatchSettler` | `settle` 在服务器线程上运行的那一步：一次修订号读取、一次 CAS，以及账本移动时最多一次调用内重读。 | — |
| 匹配结果（Match result） | `MatchResult` | 密封：`Admitted`、`Dropped`、`Formed`、`Failed`、`Undecided`、`Deferred`；每个分支都携带它运行时所处的 `Stage`，且只有 `settle` 产出 `Formed`。该接口是 `AutoCloseable`，在不持有句柄的分支上 `close()` 是空操作，因此 `try (var r = kernel.check(request))` 对每个分支都能编译。 | `AttemptResult` |
| 准入（Admitted） | `MatchResult.Admitted` | `(Stage, Siting, MatchOutcome, @Nullable ClaimPlan planned, long ledgerRevision, SnapshotLease, FindingBundle)`：结构成立时只读的 `check` 返回的内容；`planned` 就是 `settle` 将会登记的内容。携带子租约句柄，绝不是实例。 | — |
| 已成型（Formed） | `MatchResult.Formed` | `(Stage, InstanceId, Siting, MatchOutcome, ClaimSet, long generation, long contextRevision, long ledgerRevision, SnapshotLease, FindingBundle)`：只由 `settle` 产出；派生是另一次调用。 | `FormedResult` |
| 失败（Failed） | `MatchResult.Failed` | `(Stage, List<Finding> findings)`；至少一条 `ERROR` 诊断项（一次不匹配或一次声明冲突）。 | `FailedResult` |
| 未决（Undecided） | `MatchResult.Undecided` | `(Stage, UndecidedReason reason, List<Finding> findings, @Nullable MatchOutcome partial, @Nullable RetryToken retry)`。 | `UndecidedResult` |
| 延后（Deferred） | `MatchResult.Deferred` | `(Stage, RetryToken token, List<Finding> findings)`。 | — |
| 未决原因（Undecided reason） | `UndecidedReason` | `BUDGET_EXHAUSTED`、`DIRTY_UNSTABLE`、`LEDGER_MOVED`、`LEDGER_UNAVAILABLE`、`SUBJECT_ABSENT`、`LEASE_CLOSED`、`WORKER_FAILED`、`DESIGN_DEFECT`。 | — |
| 作废（Dropped） | `MatchResult.Dropped` | `(Stage, DropCause cause, List<Finding> findings)`：因为捕获在取出之后移动了或失效了，本次没有任何判定；`cause` 是 `SNAPSHOT_CHANGED`（关注区域被标记）或 `CAPTURE_INVALID`（其世界被替换，或租约被关闭）。什么也不发布，下次尝试重新捕获。 | — |
| 作废原因（Drop cause） | `DropCause` | `SNAPSHOT_CHANGED`（`SC-ENG-0003`）、`CAPTURE_INVALID`（`SC-ENG-0006`）。 | — |
| 匹配产物（Match outcome） | `MatchOutcome` | `(Stage stage, Siting siting, WorldView view, Map<ContextKey<?>,Object> context, Bindings bindings, ReadScope reads)`：一次尝试产出了什么——它读了哪个视图、发布了哪些值、绑定了哪些主体，以及它触及的一切。 | — |
| 舱位绑定（Bay binding） | `BayBinding` | `(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings)`：检查所绑定的主体，门的情形可能是两个，绝不从某个值中解包。 | `SlotBinding` |
| 接缝绑定（Seam binding） | `SeamBinding` | `(SeamId seam, List<OccupantRef> counterparts)`。 | `PortBinding` |
| 普查（Census） | `Census` | `(Map<NamespacedId,Integer> byType, Map<OccupantKind,Integer> byKind, int total)`。 | `ElementCensus` |
| 预筛（Prescreen） | `PrescreenIndex` | 为某个视图预先计算的 bounds、普查与锚点候选；`@Nullable AnchorCandidate find(...)`。 | `PrescreenIndex` |
| 锚点候选（Anchor candidate） | `AnchorCandidate` | `(NamespacedId role, LatticePoint cell, Orientation implied, int score)`。 | `AnchorCandidate` |
| 指纹（Fingerprint） | `Fingerprint` | 预筛内容的摘要 `(long high, long low)`；跨运行稳定。 | `Fingerprint` |
| 读取集（Read set） | `ReadSet` | 实际读取的 `(CuboidSet cells, Set<ChunkKey> chunks, Set<OccupantRef> occupants)`。 | `ReadSet` |
| 变更集（Change set） | `ChangeSet` | `(Set<LatticeKey> cells, Set<SectionKey> sections, Set<LatticeKey> payloads, Set<EntityKey> entities, Set<NamespacedId> keys, long revision)`。 | `ChangeSet` |
| 失效索引（Invalidation index） | `InvalidationIndex` | `List<InstanceRef> affectedBy(ChangeSet)`。 | `InvalidationIndex` |
| 实例引用（Instance ref） | `InstanceRef` | `(NamespacedId blueprint, Siting siting, long generation)`。 | — |
| 实例 id（Instance id） | `InstanceId` | `(World world, long serial)`：一个已摆放结构的身份。它以自身为键拥有 `StructureContext` 存储，因此两个实例发布同一个键永不冲突；它也是 `KEY` 定位域所命名的对象。 | — |
| 计划 id（Plan id） | `PlanId` | 一个驻留的 `long`，在 `Blueprint` 构建时分配；数据包重建会得到新 id 与冷缓存。 | — |
| 单元 id（Unit id） | `UnitId` | 计划中一个计划单元的身份：一个 `Check` id 或一个 `Computation` id（`11` §4.7）。 | — |
| 缓存键（Cache key） | `CacheKey` | `(PlanId plan, Siting siting, UnitId unit, long closureDigest)`：某个安放位上单个单元的尝试缓存身份（`11` §4.7）。 | — |
| 缓存单元（Cached unit） | `CachedUnit` | 密封的 `CachedPartial(ContextKey<?> key, Object value)` \| `CachedOutcome(Reply reply, List<Finding> findings, List<OccupantRef> subjects)`：一个单元的贡献或它的产物，绝不是折叠后的值，也绝不是世界内容（`20` §6.3）。 | `SectionResult` |
| 尝试缓存（Attempt cache） | `AttemptCache` | 覆盖 `CachedUnit` 的可选 LRU：`@Nullable get(CacheKey)`、`put(CacheKey, CachedUnit)`、`none(long cacheBytes)`；在 `cacheBytes` 之下逐出，绝不在 `retainedBytes` 之下逐出（`20` §6.3）。 | `SectionCache` |

## J. U8 — 上下文（`kernel.context`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 结构上下文（Structure context） | `StructureContext` | 不可变的、逐实例的已发布框架视图：`(InstanceId instance, long revision, List<ContextFrame> frames)`，一个 `ContextView`（`get`、`require`、`keys()`）外加 `findings()`、`spend()`。两个实例发布同一个键永不冲突。 | `StructureContext` |
| 上下文框架（Context frame） | `ContextFrame` | `(ContextTier tier, long revision, Map<ContextKey<?>, Object> values)`：一个已发布的阶段边界；位于层级 `t` 的读者看到 `t` 及以下最新的框架。 | `ContextValue` |
| 区段上下文（Section context） | `SectionContext` | `Computation` 的一次区段运行所看到的内容：`section()`、`occupants(...)`。它没有 `read(ContextKey)`，因此区段无法读取聚合状态，其唯一的写入途径是 `ContextDraft.contribute`。 | — |
| 聚合上下文（Aggregate context） | `AggregateContext` | `Computation` 的一次聚合运行所看到的内容：`read(ContextKey<T>)`、`occupants(...)`；它不能 `contribute`。 | — |
| 焦点上下文（Focus context） | `FocusContext` | 面向一次检查主体的 `AggregateContext`：检查只读取 `ContextKey`，别的什么都不读。 | — |
| 增益（Augment） | `Augment` | 可聚合的效果 `(AugmentKey key, double amount)`，例如“+3 容量”。 | `Modifier` |
| 增益键（Augment key） | `AugmentKey` | `(NamespacedId id, AugmentFold fold)`。 | — |
| 增益折叠（Augment fold） | `AugmentFold` | `SUM`、`MAX`、`MIN`、`MULTIPLY`、`SET`；确定性的折叠顺序是声明顺序。 | `ModifierAggregator` |
| 运行时状态（Runtime state） | `RuntimeState` | `(StructureContext context, Map<AugmentKey,Double> augments, long generation)`；已成型结构由宿主持久化的形态。 | `RuntimeContext` |

## K. U9 — 诊断项（`kernel.finding`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 严重级别（Severity） | `Severity` | `ERROR`、`WARNING`、`INFO`。 | `Severity` |
| 诊断项（Finding） | `Finding` | `(FindingId id, FindingCode code, Severity severity, String message, List<Label> labels, List<Note> notes, @Nullable Suggestion suggestion, List<FindingId> related, Stage stage)`。 | `Diagnostic` |
| 诊断项 id（Finding id） | `FindingId` | 在一个包内稳定；被 `related` 引用；`NONE` 表示“不可寻址”。 | — |
| 标签（Label） | `Label` | `(Locus locus, String message, LabelRole role)`；源区间随 `Locus` 传递，绝不出现两次。 | `Label` |
| 标签角色（Label role） | `LabelRole` | `PRIMARY`、`SECONDARY`、`CONTEXT`。 | — |
| 注释（Note） | `Note` | `(String message, @Nullable Locus locus)`。 | `Note` |
| 建议（Suggestion） | `Suggestion` | `(String message, Applicability applicability, @Nullable NamespacedId remedy)`；计划本身由 `solve` 计算，绝不嵌入诊断项（以保持 U9 独立于 U10）。 | `Suggestion` |
| 适用性（Applicability） | `Applicability` | `MACHINE_APPLICABLE`、`MAYBE_INCORRECT`、`HAS_PLACEHOLDERS`、`UNSPECIFIED`。 | `Applicability` |
| 源区间（Source span） | `SourceSpan` | `(NamespacedId document, int start, int end)`；定义在 `base`，在此处消费。 | — |
| 诊断包（Finding bundle） | `FindingBundle` | 不可变、有序、去重、带上限的集合；`primary()`、`isEmpty()`、`filter(...)`。 | `DiagnosticBundle` |
| 诊断项汇（Finding sink） | `FindingSink` | 消费者：`accept(Finding)`、`acceptAll(FindingBundle)`。 | `DiagnosticSink` |
| 诊断项选项（Finding options） | `FindingOptions` | `(maxTotal, maxPerCode, includeInfo, includeNotes)`：报告的尺寸，只花在 `WARNING`/`INFO` 上——任何上限都不会省略 `ERROR`。 | `DiagnosticOptions` |
| 诊断项渲染器（Finding renderer） | `FindingRenderer` | `String render(Finding, RenderContext)`；人类可读、JSON 与 SARIF 形态的渲染器都是该接口的宿主实现。 | `DiagnosticRenderer` |
| 解释手册（Explain book） | `ExplainBook` | `@Nullable Explanation explain(FindingCode)`。 | `ExplainBook` |
| 解释（Explanation） | `Explanation` | `(FindingCode code, String summary, String body, List<NamespacedId> seeAlso)`。 | — |
| 渲染上下文（Render context） | `RenderContext` | `(List<SourceText> sources, @Nullable NamespacedId document, int width, ColorMode color, Locale locale)`；渲染器唯一可以读取的环境输入。 | — |
| 源文本（Source text） | `SourceText` | 带偏移→行/列映射的 `(NamespacedId id, String text)`；U9 局部，由宿主提供。 | — |

## L. U10 — 补救方案（`kernel.remedy`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 补救方案（Remedy） | `Remedy` | 密封：`Repair`、`Preview`、`ExplainRemedy`。 | `Solution` |
| 补救方案种类（Remedy kind） | `RemedyKind` | `REPAIR`、`PREVIEW`、`EXPLAIN`。 | `SolutionKind` |
| 补救方案提供者（Remedy provider） | `RemedyProvider` | `List<Remedy> solve(Finding, RemedyContext)`；返回有序、去重的列表。 | `SolutionProvider` |
| 补救方案上下文（Remedy context） | `RemedyContext` | `view()`、`blueprint()`、`siting()`、`ledger()`、`kinds()`、`maxResults()`。 | `SolveOptions` |
| 求解选项（Solve options） | `SolveOptions` | `(Set<RemedyKind> kinds, int maxResults, boolean allowWorldReads, int planDepth)`；默认值分别为全部种类、8、false、1。 | — |
| 修复（Repair） | `Remedy.Repair` | `(EditPlan plan, Applicability applicability, String rationale)`。 | `SolutionPlan` |
| 预览（Preview） | `Remedy.Preview` | `(OverlayModel overlay, String rationale)`。 | `OverlayModel` |
| 解释补救方案（Explain remedy） | `Remedy.ExplainRemedy` | `(Explanation explanation)`。 | — |
| 编辑计划（Edit plan） | `EditPlan` | 有序的 `EditStep` 加上一段理由。 | `SolutionPlan` |
| 编辑步骤（Edit step） | `EditStep` | `(Edit edit, Locus locus, Applicability applicability, @Nullable Expectation<?> precondition)`。 | `Operation` |
| 编辑（Edit） | `Edit` | 密封：`Place`、`Remove`、`Replace`、`SetTrait`、`Configure`、`BindAnchor`、`NestedEdit`。 | `Operation` 记录集 |
| 计划校验器（Plan validator） | `PlanValidator` | 试运行 `(EditPlan, WorldView, Siting) -> ValidationReport`。 | `PlanValidator` |
| 校验报告（Validation report） | `ValidationReport` | `(List<Finding> findings, boolean applicable)`。 | — |
| 计划差异（Plan diff） | `PlanDiff` | `(List<EditStep> added, removed, changed)`。 | `PlanDiff` |
| 应用报告（Apply report） | `ApplyReport` | `(boolean success, List<Finding> findings, Spend spend)`。 | `ApplyReport` |
| 编辑事务（Edit transaction） | `EditTransaction` | 可回退的应用范围：`apply(EditStep)`、`commit()`、`rollback()`。 | `Transaction` |
| 编辑执行器（Edit executor） | `EditExecutor` | 宿主 SPI：`EditTransaction begin(WorldView, Siting, ClaimView, long ledgerRevision, PlanDigest)`。 | — |
| 叠加层模型（Overlay model） | `OverlayModel` | `(Cuboid bounds, Map<LatticeKey,CellMark> cells, List<Marker> markers)`。 | `OverlayModel` |
| 格标记（Cell mark） | `CellMark` | `EXPECTED`、`PRESENT`、`MISSING`、`EXTRA`、`WRONG`、`BLOCKED`、`UNKNOWN`。 | `CellStatus` |
| 标记（Marker） | `Marker` | `(Locus locus, String label, MarkerKind kind)`。 | `Annotation` |
| 标记种类（Marker kind） | `MarkerKind` | `ERROR`、`WARNING`、`INFO`、`ANCHOR`、`SEAM`。 | — |

## M. U11 — SPI 与门面（`kernel.spi`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 目录（Catalog） | `Catalog<T>` | 带命名空间、有序、不可变的扩展映射：`@Nullable T get(NamespacedId)`、`List<NamespacedId> ids()`。 | `Catalog` |
| 目录集（Catalogs） | `Catalogs` | 所有扩展目录的冻结快照：`expectations`、`focuses`、`computations`、`contextKeys`、`derivers`、`remedies`、`codes`；先 `Catalogs.builder()` 再 `build()`，在 `apply` 时冻结，解析时不使用反射。 | `Catalogs` |
| 结构内核（Structure kernel） | `StructureKernel` | 门面；九个操作：`document`、`compile`、`siting`、`check`、`settle`、`derive`、`solve`、`explain`（`explainOrFallback` 与之合并计数）、`ledger`。 | `StructureKernel` |
| 编译结果（Compile result） | `CompileResult` | `(FindingBundle findings, @Nullable Blueprint blueprint)`：DESIGN 报告，以及干净时装配出的蓝图。`compile` 取代了更早的 `validate` 草图。 | — |
| 平凡实现（Trivial implementation） | — | 内核随附的、不含领域知识的实现，例如 `Expectations.air()`；这是内核唯一可以随附的实现（R-10）。 | 同上 |

## N. A1 — 适配器（`dev.vfyjxf.cumulus.mc`）

| 术语 | 类型（Type） | 定义 | 先前术语（Prior） |
| --- | --- | --- | --- |
| 层级快照（Level snapshot） | `LevelSnapshot` | 覆盖一个已捕获 `Level` 区域的 `WorldView`；不可变，不含实时 `Level`。 | — |
| 层级捕获（Level capture） | `LevelCapture` | 走查方块、方块实体、实体与嵌套结构的 `WorldCapture`。 | — |
| 世界占用体（World occupants） | `WorldOccupants` | 面向 `BlockState`、方块实体、`Entity` 与 extent 原始值的 `OccupantResolver`。 | — |
| 占用体描述符（Occupant descriptors） | `OccupantDescriptors` | 原始方块/实体值 → `OccupantDescriptor`，以及其精确逆映射。 | — |
| 占用体特征（Occupant traits） | `OccupantTraits` | 不可变的物品栏、能量、流体与实体 NBT 值。 | — |
| 朝向映射（Orientation mapping） | `OrientationMapping` | 在 `Heading`/`Turn`/`Tilt`/`Flip` 与 MC 的 `Direction`/`Rotation`/`Mirror` 之间映射，并重映射方块状态。 | `OrientationAdapter` |
| 已保存声明（Saved claims） | `SavedClaims` | 通过 `SavedData` 持久化的 `ClaimLedger`。 | `SavedClaims` |
| 世界编辑（World edits） | `WorldEdits` | 带回退地应用计划的 `EditExecutor`。 | `WorldEdits` |
| 客户端叠加层（Client overlay） | `ClientOverlay` | 客户端侧的 `OverlayModel` 渲染器；绝不属于内核。 | `OverlayRenderer` |
| 叠加层载荷（Overlay payload） | `OverlayPayload` | 把 `OverlayModel` 送到客户端的 S2C 载荷。 | — |
| 数据包蓝图（Datapack blueprints） | `DatapackBlueprints` | 数据包重载到缓存的文档。 | `DefinitionReloadListener` |
| 诊断输出（Finding output） | `FindingOutput` | 输出到日志、聊天或 JSON 的 `FindingSink`。 | — |
| 服务器状态（Server state） | `ServerState` | 每服务器的目录、门面与声明账本。 | — |
| JSON 前端 / 桥（JSON frontend / bridge） | `JsonFrontend`, `JsonBridge` | Gson 文本/树 ↔ `DataView`。 | — |
| Cumulus 命令（Cumulus command） | `CumulusCommand` | `/cumulus capture`、`import`、`lint`、`validate`、`preview`、`build`、`explain`。 | `CumulusCommand` |
| 匹配调度器（Match scheduler） | `MatchScheduler` | 宿主侧每服务器执行器：`submit`、`markDirty`、`dropInstance`、`worldUnloaded`、`tick`、`close`。 | — |
| 检查键（Check key） | `CheckKey` | `(World world, OwnerId owner)`：调度器槽位身份；`Siting` 仍留在请求里。 | — |
| 匹配句柄（Match handle） | `MatchHandle` | 一次提交：状态、票据、`done()`、`onTerminal`、协作式 `cancel`。 | — |
| 匹配状态（Match state） | `MatchState` | `QUEUED`、`RUNNING`、`POSTED`、`SETTLED`、`DROPPED`、`FAILED`、`DEFERRED`、`CANCELLED`。 | — |
| 匹配完成（Match completion） | `MatchCompletion` | 密封的 `Reported`、`Discarded`、`Failed`；worker 的终态完成值，每次提交一个。它不是尝试产物：尝试产物是 `MatchOutcome`。 | `MatchOutcome` |
| 冻结账本（Frozen ledger） | `FrozenLedger` | 不可变 `ClaimLedger` 快照 + 修订号，附带预计算的读取盒；派发时的视图。 | — |
| 脏索引（Dirty index） | `DirtyIndex` | 宿主侧的区段/载荷/实体 → 受监视实例索引；O(1) 标记。 | — |
| 受监视实例（Watched instance） | `WatchedInstance` | 一个被跟踪的结构：快照、状态、`dirtyEpoch`、区段/载荷/实体修订号、`dirtyForCheck`、`checkInFlight`。 | — |
| 监视状态（Watch state） | `WatchState` | `CLEAN`、`DIRTY`、`CHECKING`、`BROKEN`、`UNKNOWN`。 | — |
| 区段存储（Section store） | `SectionStore` | 可选的每世界共享缓存，保存不可变 `SectionSnapshot`；默认是逐快照副本。 | — |
| 区段快照（Section snapshot） | `SectionSnapshot` | 冻结的 `PalettedContainer<BlockState>` 副本 + 捕获修订号 + 载荷单元格。 | — |
| 单元格载荷（Cell payload） | `CellPayload` | 受监视的 BE/实体单元格的 `(LatticeKey, OccupantDescriptor, List<Trait<?>>)`；`OccupantRef` 在读取时用最终 `SnapshotId` 惰性盖上。 | — |
| 捕获请求 / 上限（Capture request / limits） | `CaptureRequest`, `CaptureLimits` | 边界、策略、网格、受跟踪的类型/单元格，以及上限（单元格、区段、字节、实体、载荷）。 | — |
| 捕获结果（Capture result） | `CaptureResult` | 密封的 `Captured(WorldView)`、`TooLarge`、`Unavailable(status, code)`；绝不把部分快照当作完整。 | — |
| 捕获工具（Capture tool） | `CaptureTool` | `/cumulus capture` 宿主路径：选区 → compact JSON `DataView`。 | — |
| 结构导入（Schematic import） | `SchematicImport` | 读取 `.schem`/`.nbt` 并输出 compact JSON；不支持的条目变成诊断项。 | — |

## O. 禁用名

这些名称即使读起来自然也会被拒绝，因为 Minecraft 或 JDK 的某个类型已经占用了它们。
右列是应改用的内核术语。

| 禁用名 | 与之冲突 | 改用 |
| --- | --- | --- |
| `Rotation`、`Mirror` | `net.minecraft.world.level.block.Rotation`、`.Mirror` | `Turn`、`Flip` |
| `Direction` | `net.minecraft.core.Direction` | `Heading` |
| `Pose` | `net.minecraft.world.entity.Pose` | `Siting` |
| `Slot` | `net.minecraft.world.inventory.Slot` | `Bay` |
| `BlockPos`、`AABB` | Minecraft | `LatticePoint`、`Cuboid` |
| `Predicate` | `java.util.function.Predicate` | `Expectation` |
| `Diagnostic` | `javax.tools.Diagnostic` | `Finding` |
| `Solution` | 过于泛化；与 JDK 相邻 | `Remedy` |
| `Box` | `javax.swing.Box` | `Cuboid` |
| `Region` | 过于常见；与宿主区域冲突 | `CuboidSet` |
| `Element` | `javax.swing.text.Element`、`org.w3c.dom.Element` | `Occupant` |
| `Transform` | `java.awt.geom.AffineTransform` | `Placement` |
| `Registry` | `net.minecraft.core.Registry` | `Catalog` |
| `Holder` | `net.minecraft.core.Holder` | `OccupantRef` |
| `Codec` | `com.mojang.serialization.Codec` | `DataBridge` |
| `Component` | `net.minecraft.network.chat.Component` | `Trait` / 宿主载荷 |
| `Ingredient` | `net.minecraft.world.item.crafting.Ingredient` | `Expectation` |
| `Modifier` | `java.lang.reflect.Modifier` | `Augment` |
| `Entity` | Minecraft | `MatchResult.Formed` / `OccupantRef` |
| 公开 API 中的 `Optional` | 项目可空性约定 | `@Nullable` |

## P. 新名称的归属

模块可以在自己的包内添加名称而无需改动术语表，但任何其他模块必须引用的名称都是
**跨单元名称（cross-unit name）**，必须在模块文件被接受之前连同归属一起添加到这里。
审阅者会拒绝一个为本表中已有术语悄悄引入第二种叫法的模块。
