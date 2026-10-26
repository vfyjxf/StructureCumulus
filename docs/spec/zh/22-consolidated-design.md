# 22 — 合并设计（Consolidated design）

> 状态：修订 5，2026-10-20。本文件是合并设计。中间状态不是独立单元：它是扩展了贡献、折叠与
> 必需读取的上下文 API。修订 4 的谓词层重写，以及修订 3–4 的 `StateKey`/`PartialKey`/`Reducer`
> 拆分均已撤回；§15.0 记录原因。
> `_work/review1` 与 `_work/review2` 把本文件称作 `23`；该编号是历史遗留。

## 0. 模型（The model）

1. **一种键：`ContextKey<T>`。** 它携带 `(id, type, Retention, @Nullable Merge<T>,
   @Nullable T whenAbsent)`。中间状态没有第二种键类型。
2. **一个存储：`ContextDraft` / `ContextView`。** 区段计算调用
   `draft.contribute(key, partial)`；聚合调用 `draft.put(key, value)`；读者调用
   `view.require(key)`。
3. **`merge == null` 表示单写者**；`merge != null` 表示允许区段贡献。对某个键使用错误的动词
   是 DESIGN 错误，不是运行期错误。
4. **折叠是规范的。** 贡献按 `SectionKey` 排序，并用 `Merge.combine` 从左到右合并。被拒绝的
   区段使该键缺席；绝不是不完整的和，也绝不是单位元值。
5. **谓词通过焦点读取。** `Focuses.context(key)` 产出 `Focus<T>`；`Check<T>` 把它与
   `Expectation<T>` 配对。缺席的键是 `Outcome.Missing`，成为 `UNREADABLE`；谓词不会被调用。
6. **发布走同一个存储。** `Retention.CONTEXT` 把值写入阶段层的帧；`OUTCOME` 保留在 `MatchOutcome`
   中；`ATTEMPT` 丢弃。只有 `SETTLE` 帧成为 `RuntimeState`。
7. **`Deriver` 是仅面向上下文的生产者**（不读世界）；`Computation` 是读世界的生产者。两者都写
   `ContextKey`，并由 `requires()` 排序。
8. **引擎只在 worker 上运行**，与修订 3 相同：快照捕获、区段副本、脏标记、五元门、声明结算、
   诊断与补救方案均未改变。

## 1. 一段话模型（One-paragraph model）

一个**蓝图**在某个**阶段**中，于某个**安放位**与不可变的**层级快照**（`LevelSnapshot`）匹配。
舱位拥有一个 `Cuboid`、一个 `Coverage`、一个声明策略和一个**需求**（`Requirement`）：`Check<T>`
单元（一个 `Focus<T>`、一个 `Expectation<T>`）与计算。区段计算向 `ContextKey` 贡献值；提交屏障
按规范区段顺序折叠它们；聚合计算与检查通过同一个 `ContextView` 读取折叠后的值。
`Retention.CONTEXT` 值发布为按版本化的**上下文层**（`ContextTier`）帧，由某个实例拥有，只有
`SETTLE` 帧被持久化为 `RuntimeState`。检查在宿主 worker 上针对快照运行；捕获、脏刷新、声明结算
与世界的编辑都留在服务器线程。

## 2. 需求追溯（Requirements traceability）

| 编号 | 需求 | 机制 | 残余风险 |
| --- | --- | --- | --- |
| R-01 | 元素不只是方块；实体与嵌套结构也是元素 | `OccupantSnapshot` + `OccupantKind`；`Focus.occupant/entity/nested`；窗口索引 + 每 tick diff（§5.4） | 窗口开销 |
| R-02 | 与版本无关的内核，基线 1.21.1 NeoForge | 纯净的 `src/kernel`；`compileKernelJava`/`kernelPurity` | 纯净性只是导入扫描 |
| R-03 | 结构 → 运行时上下文 | `Computation`/`Deriver` → `ContextKey` → 层帧 → `RuntimeState`（§7、§8） | 宿主 `RUNTIME` 升级 |
| R-04 | 无需控制器实体即可计算中心 | 安放位原点处的 `AnchorSpec`；`AnchorResolver`（§4.4） | 属性名在适配器侧 |
| R-05 | 由控制器朝向得到朝向变换 | 锚点朝向 → 一个映射公式（§4.4） | 平局策略 |
| R-06 | 异构谓词，仅矩形 | 每舱位一个 `Cuboid`；每种 kind 一个 `Focus`；断言 `Coverage`；门是一个舱位 | 复合形状需要多个舱位 |
| R-07 | rustc 级错误/日志 | `Reply` 四态；`Outcome` 缺席通道；`SC-FOC/SC-CHK/SC-BAY/SC-CLM/SC-CTX`（§6.6、§11.1） | 措辞未经测试 |
| R-08 | 分代/分阶段检查，谓词顺序 | 由引擎选择的 `Stage`；阶段拆分；屏障；`ChangeSet` 重检；计数器表（§8.2、§9.2） | 活性仅为论证；快失败需要一个载体（§16.1 第 16 条） |
| R-09 | 数据驱动的片段/定义 | 规范 + 紧凑 JSON；一族 `Spec`；schema 2（§12） | 计算仍然冗长 |
| R-10 | 一个通用内核；扩展 | `Catalogs` = expectations、focuses、computations、contextKeys、derivers、remedies、codes（§6.7） | 目录清单是封闭的 |
| R-11 | 共享与独占声明 | `Sharing` × `Footprint`；一个矩阵；声明只由 `settle` 写入，`check` 永不写（§10） | 空体积保护是声明式的 |
| R-12 | 可视化与自动建造 | 带事实、上下文与声明的 `RemedyContext`（§11.3） | 提供者质量未经测试 |
| R-13 | 组合式、类型安全的 API | `ContextKey<T>`、`Check<T>`、类型化 `Bindings`；`Focus`/`Expectation` 使用同一个 `T`（§6） | `List<Check<?>>` 在计划器中被擦除 |
| R-14 | 内部复杂，外部简单 | 门面 `check`/`settle`/`derive`/`solve`；`Formed` 只来自 `settle`（§10.4） | 租约纪律 |
| R-15 | 参考代码与证明 | §14 程序；`kernelTest` 已命名，未编写 | **无编译测试** |

## 3. 内核与适配器（Kernel and adapter）

```
src/kernel/java      dev.vfyjxf.cumulus.kernel.**     Java 21 + JSpecify only
src/kernelTest/java  kernel tests                     JUnit 5 only
src/main/java        dev.vfyjxf.cumulus.**            mod + mc adapter
src/main/resources   metadata + data/<ns>/cumulus/structure/*.json
```

- `kernel.**` 下不得出现 `net.minecraft`、`net.neoforged`、`com.mojang`。
- 适配器名称按角色命名：`LevelCapture`、`LevelSnapshot`、`WorldOccupants`、
  `OccupantDescriptors`、`OccupantTraits`、`OrientationMapping`、`SavedClaims`、`WorldEdits`、
  `ClientOverlay`、`DatapackBlueprints`、`FindingOutput`、`ServerState`、`JsonFrontend`。
- 包就是命名空间；没有 `Mc`/`Vanilla` 前缀。
- 宿主策略（worker 数、tick 预算、租约上限）留在内核之外；内核只看见工作单元。

## 4. 空间、朝向与安放（Space, orientation, siting）

- `LatticePoint`、`LatticeKey`（x:26 / z:26 / y:12）、`SectionKey`。
- `Cuboid` 半开；`CuboidSet` 规范形式；`volume()` 精确。
- `Heading` 六个方向；`Turn`/`Tilt`/`Flip`；`Orientation` 48 个元素。
- `Placement` = 朝向变换 + 偏移；`Siting` = 原点 + 摆放；`Locus` 取
  `DEFINITION | LOCAL | WORLD | KEY`，其中 `KEY` 携带 `(InstanceId, NamespacedId)`。

### 4.4 锚点与朝向变换（Anchor and orientation）

```java
record AnchorSpec(NamespacedId role, NamespacedId property, String value) {}

interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
}
```

- 锚点格是定义原点。要读取的格是调用者提供的 `origin`，不由正在选择的朝向变换推导，因此不存在
  循环。
- `resolve` 读取一个格，检查属性/值，取得一个 `Heading`，并返回把作者标注的正面映射到该朝向的
  `Siting`：`candidate.apply(authoredFront) == anchorHeading`。
- 没有锚点的蓝图直接接受一个 `Siting`（命令、工具）。

## 5. 占用体、快照与实体（Occupants, snapshots, entities）

### 5.1 读模型（Read model）

```java
interface WorldView {
    SnapshotId snapshotId();
    SectionSlot slot(SectionKey key);
    @Nullable OccupantSnapshot occupant(LatticePoint cell);
    @Nullable OccupantSnapshot occupant(OccupantRef ref);
    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
    @Nullable NestedLevelSnapshot nested(LatticePoint cell);
    long contentDigest(SectionKey key);
}
```

- `OccupantSnapshot(ref, kind, descriptor, footprint, traits, @Nullable nested)`。
- `OccupantRef(snapshot, type, kind, anchor)`：它被铸造时所处的 `SnapshotId` 是身份的一部分；
  足迹属于 `OccupantSnapshot`，因此 `Footprint.Bound` 从已经在手的捕获即可解析。
- `ReadStatus` = `READABLE | OUT_OF_BOUNDS | NOT_CAPTURED | DENIED | STALE`；空气是可读的，
  被拒绝的格绝不是空气。
- `SectionSlot` = `Air(revision) | Present(SectionSnapshot) | Refused(status, code)`；区段快照
  持有冻结的容器副本与一个 `contentDigest`。

### 5.2 捕获（Capture）

- 一次服务器线程调用，`getChunkNow`，绝不用 `Level.getChunk`；不加载区块。
- 区段由计划的读取闭包加上实体窗口请求。
- `CaptureResult(snapshot, CaptureIssues)`；缺失的区段是 `Refused(NOT_CAPTURED)`。

### 5.3 区段存储（Section store）

默认按快照各自复制；`SectionStore` 在剖析之后按需启用，以 `(SectionKey, contentDigest)` 为键，
绝不复用可变容器的别名。

### 5.4 实体（Entities）

```java
record EntityKey(UUID id, NamespacedId type, long dataDigest) {}
record EntityWindow(Cuboid cells, NamespacedId type) {}
```

- `MAX_ENTITY_SPAN = 3`；更宽的类型是 `SC-BAY-0110`。窗口是声明的盒子按跨度外扩。
- `DirtyIndex` 维护 `window → Set<InstanceRef>`。每个 tick，宿主对每个窗口比较实时的
  `cell → EntityKey` 集合与捕获时的集合；差异使读取该窗口的单元变脏。这就是向外移动的见证。
- 加入/离开标记足迹。`ChangeSet(cells, sections, payloads, entities, keys)`。

### 5.5 快照租约（Snapshot lease）

```java
interface SnapshotLease extends AutoCloseable {
    SnapshotId id();
    WorldView view();
    long changeCount();                                 // marks since capture; zero means untouched
    boolean isValid();                                  // the world still exists and this handle is open
    Outcome<SnapshotLease> retain(PinBudget budget);   // Pinned | PinRefused
    boolean isOpen();
    @Override void close();
}
```

- `InstanceRecord` 拥有捕获；`Formed`/`Admitted` 持有子句柄。`retain` 计入 `retainedBytes`，并且
  可以拒绝（`SC-MC-0502`）。`retainedBytes` 与 `cacheBytes` 是分开的。
- 两个结果分支都是 `AutoCloseable`；不关闭就丢弃是可追踪的泄漏，而不是丢失视图，因为实例保持
  自己的引用。

## 6. 谓词、焦点与检查（Predicates, focuses, checks）

### 6.1 谓词（The predicate）

```java
@FunctionalInterface
interface Expectation<T> {
    Reply test(ExpectationContext<T> ctx);
    Class<T> subject();                                             // the one subject type it accepts
    default ReadScope reads() { return ReadScope.none(); }          // declared reads beyond the bay
    default ExpectationMeta meta() { return ExpectationMeta.anonymous(); }
}

interface ExpectationContext<T> {
    T subject();
    BayView bay();
    WorldView view();
    Siting siting();
    Cuboid cells();
    ProbeSession session();
    ContextView context();
    Budget budget();
    Locus locus();
    Stage stage();
}
```

- `Reply` 保持 `MATCH | MISMATCH | UNREADABLE | ABSTAIN`，非泛型，带类型化的 `Bindings`。
- `ExpectationMeta` = `id`、`specificity`、`requires`（`Set<ContextKey<?>>`）、诊断项模板。
  `specificity` 只是诊断项排序的提示，不是预算数字。
- 同一个接口服务于元素谓词（`T = OccupantSnapshot`）、舱位谓词（`T = BayView`）与状态谓词
  （`T = S`）。

### 6.2 焦点与检查（Focus and Check）

```java
interface Focus<T> {
    Outcome<T> take(FocusContext ctx);
    Class<T> subject();
    DecisionKind decisionKind();
    CuboidSet staticDecisionCells(Cuboid bayCells);
    ReadScope reads(Cuboid bayCells);
    FindingBundle describeMissing();
}

record Check<T>(NamespacedId id, NamespacedId bay,
                Focus<T> focus, Expectation<T> expectation,
                @Nullable String description) {

    public ReadScope reads(Cuboid bayCells) { return focus.reads(bayCells).withKeys(expectation.meta().requires()); }

    public CheckResult run(FocusContext ctx) {
        Outcome<T> taken = focus.take(ctx);
        if (taken instanceof Outcome.Found<T> found) {
            return new CheckResult(
                    expectation.test(ExpectationContext.of(ctx, found.value(), found.subjects())),
                    found.subjects(), found.notes());
        }
        Outcome.Missing<T> missing = (Outcome.Missing<T>) taken;
        return new CheckResult(Reply.unreadable(focus.describeMissing()), List.of(), missing.findings());
    }
}

record Requirement(List<Check<?>> checks, List<Computation<?>> computations) {}
```

- `Check<T>` 是焦点与期望唯一的交汇点；编译器强制同一个 `T`。
- 焦点失败是带 `SC-FOC-*` 的 `Outcome.Missing`；`test` 绝不会用伪造的主体调用。
- 一个检查恰好属于一个舱位（`SC-CHK-0107`）。

### 6.3 焦点（Focuses）

```java
Focus<OccupantSnapshot> Focuses.occupant(LatticeKey cell);
Focus<List<OccupantSnapshot>> Focuses.allCells();
Focus<BayView> Focuses.bay();
Focus<T> Focuses.context(ContextKey<T> key);
Focus<EntitySnapshot> Focuses.entity(UUID id);
Focus<NestedView> Focuses.nested(LatticeKey cell);
Focus<DoorView> Focuses.door(LatticeKey origin);
```

- `Focuses.context(key)` 恰好是 `ctx.context().require(key)`；缺席是 `Missing`，绝不是零。
- 单格检查用 `Focuses.occupant(cell)`；门或多格舱位用 `Focuses.bay()` 配
  `Expectation<BayView>`；“每一格都是砖”是 `Expectations.eachCell(block(...))`。
- `Focuses.door(origin)` 产出携带两半的 `DoorView`，因此门期望永远不会为了找另一半而重读世界。

### 6.4 上下文键与计算（Context keys and computations）

```java
record ContextKey<T>(NamespacedId id, Class<T> type,
                     Retention retention,          // ATTEMPT | OUTCOME | CONTEXT
                     @Nullable Merge<T> merge,      // non-null: section contributions allowed
                     @Nullable T whenAbsent) {}

interface Merge<T> { T combine(T left, T right); }

interface Computation<T> {
    NamespacedId id();
    StatePhase phase();                 // SECTION | AGGREGATE; the engine assigns the stage
    ContextKey<T> output();
    Set<ContextKey<?>> inputs();
    CuboidSet region();
    Outcome<T> compute(ComputationContext ctx);
}

interface ComputationContext extends AggregateContext {
    @Nullable SectionContext section();   // non-null iff phase == SECTION
}

interface Deriver<T> {                 // no world reads; context-only producer
    ContextKey<T> output();
    Set<ContextKey<?>> requires();
    ContextTier tier();
    @Nullable T derive(DeriveContext ctx);
}

final class ContextKeys {
    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention,
                                       @Nullable Merge<T> merge, @Nullable T whenAbsent) { ... }
    public static <T> ContextKey<T> register(CatalogsDraft draft, ContextKey<T> key) { ... }
}
```

- `StatePhase` 是枚举：`SECTION`、`AGGREGATE`。

- **一种键类型。** `ContextKey` 就是 `StructureContext` 用来索引的同一个类型；不存在 `StateKey`
  或 `PartialKey`。
- `merge == null`：单写者，用 `put` 写入。`merge != null`：区段贡献，用 `contribute` 写入，在
  屏障处折叠。
- `phase` 位于计算上：`SECTION` 按区段运行，`AGGREGATE` 在屏障之后运行一次。键不携带阶段。
- `Retention`：`ATTEMPT` 随尝试丢弃，`OUTCOME` 进入 `MatchOutcome.context`，`CONTEXT` 发布进该阶段
  的帧。
- `whenAbsent` 只在区域内每个区段都可读、且没有产生任何贡献时生效；它在文档中可见。
- 声明用 `ContextKeys.of(...)` 构建，用 `ContextKeys.register(draft, key)` 绑定；同一 id 的两次
  注册是 `SC-CTX-0102`；相等性是 `(id, type)`。

### 6.5 舱位上的覆盖与声明（Coverage and claims on a bay）

```java
record Bay(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
           Sharing sharing, Requirement requirement, @Nullable String description) {}

record BayBinding(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings) {}

enum Coverage { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }
enum Sharing { EXCLUSIVE, SHARED }
sealed interface Footprint permits Box, Bound, Declared, None { ... }
```

| Coverage | 一次匹配必须建立什么 |
| --- | --- |
| `EVERY_CELL` | `bay.cells` 的每一格都被判定 |
| `ONE_SUBJECT` | `bay.cells` 等于被绑定主体足迹的并集 |
| `ANY_SUBJECT` | 不断言任何格；一个存在或状态判据 |

两个轴互相独立，因此四条规则覆盖了全部真正的矛盾，其余无需逐格列举：R1（构建）`EXCLUSIVE`
的足迹不能是 `None`（`SC-CHK-0106`）；R2（结算）`EXCLUSIVE` + `Bound` 必须解析出至少一格
（`SC-BAY-0111`）；R3 `ONE_SUBJECT` 要求检查都是主体类、且绑定足迹等于 `bay.cells`
（`SC-BAY-0107`）；R4 `EVERY_CELL` 要求逐格检查覆盖舱位，`ANY_SUBJECT` 不允许逐格检查
（`SC-CHK-0106`）。这套词汇由 `24` 拥有。

### 6.6 Outcome

```java
sealed interface Outcome<T> permits Found, Missing {
    record Found<T>(T value, List<OccupantRef> subjects, FindingBundle notes) implements Outcome<T> {}
    record Missing<T>(FindingBundle findings) implements Outcome<T> {}

    <R> R fold(BiFunction<T, List<OccupantRef>, R> onFound, Function<FindingBundle, R> onMissing);
}
```

- `Found.subjects` 是构建绑定的唯一途径；门返回两半，傀儡返回一个两格的足迹。
- `Missing` 携带带 `Locus` 的诊断项（上下文缺席用 `KEY`，格用 `WORLD`/`LOCAL`）。缺失的主体
  绝不是沉默，也绝不是 `test(null)`。
- 诊断码：`SC-FOC-0101` 缺席，`SC-FOC-0201` 歧义，`SC-FOC-0301` 区段被拒绝，`SC-FOC-0302`
  贡献缺失。

### 6.7 目录（Catalogs）

`Catalogs` = `expectations`、`focuses`、`computations`、`contextKeys`、`derivers`、`remedies`、
`codes`。每个都是以 `NamespacedId` 为键的 `Catalog<T>`，在 `apply` 时冻结，解析时不使用反射。
内核随附 `block`、`air`、`trait`、`allOf`、`anyOf`、`not`、`eachCell`；焦点如上所列；计算
`count`、`sum`、`volume`、`distinct`、`unionFind`；合并 `sum`、`union`、`min`、`max`、`and`、
`or`。

## 7. 贡献、折叠与顺序（Contributions, fold, order）

### 7.1 阶段计划（Stage plan）

对阶段 `S`：`expand(S)` 的每个单元。顺序：区段计算 → 屏障 → 按 `inputs()`/`requires()` 的
Kahn 顺序排列的聚合计算与派生器（成环时报 `SC-CHK-0102`）→ 在其读取的单元之后运行检查。独立的
单元保持声明顺序；成本不是优先级，也没有逐单元的平局分值。

### 7.2 贡献与折叠（Contribution and fold）

- 区段计算在 `region ∩ section` 中每个区段运行一次，并调用 `draft.contribute(key, partial)`。
- 贡献按 `SectionKey` `(x, z, y)` 排序，并用 `merge.combine` 从左到右合并。
- 折叠从第一个存在的贡献开始；没有单位元。
- 区域内任何一个区段被拒绝，该键就缺席；不完整的和无法表示。
- 允许并行区段遍历；折叠顺序固定，因此值是贡献集合的函数。

### 7.3 读取与屏障（Reads and the barrier）

- `SectionContext` 暴露该区段、它的格与占用体；它没有 `read(ContextKey)`。
- `AggregateContext` 暴露 `read(ContextKey)` 与折叠后的值；它不能 `contribute`。
- 检查绝不会在其阶段的屏障之前运行。
- `ContextView.get` 对可以不依赖值存活的调用者是可空的；`require` 是谓词路径，返回 `Outcome`。

### 7.4 应答合并（Reply combination）

检查彼此独立；尝试按 `UNREADABLE > MISMATCH > ABSTAIN > MATCH` 折叠应答。`Bindings.merge` 拒绝
同键不同值的配对（`SC-EXP-0003`）。

## 8. 上下文、层与代际（Context, tiers, generations）

### 8.1 阶段与层（Stage and tier）

```
Stage      DESIGN   PROBE   FORM   SETTLE
Tier       BLUEPRINT  PROBE  FORM  SETTLE
Projection -          -      -     RUNTIME
```

`Stage` 由引擎填，因为只有引擎知道哪一步产出了诊断项；没有作者声明它，也没有调用方选择它
（`23` §7）。`RUNTIME` 在检查路径上没有生产者：它是 `SETTLE` 层加上宿主升级的持久化投影。
定向重检是 `check` 加请求上的 `ChangeSet`，不是重跑某个阶段。

### 8.2 计数器（Counters）

| 计数器 | 归属 | 何时改变 | 使什么失效 |
| --- | --- | --- | --- |
| `mapGeneration` | 宿主 | 一次世界重载 | 一切，包括租约 |
| `contentDigest` | 捕获 | 某区段的冻结内容不同 | 该区段的缓存条目 |
| `cellStamp` | `DirtyIndex` | 格/载荷/实体标记 | 读取格与之相交的单元 |
| `contextRevision` | 每实例的 `ContextStore` | 某帧被发布 | 下游帧；`RuntimeState` |
| `generation` | `InstanceRecord` | 发出一个 `Formed` | 以代际为键的宿主缓存 |

`generation` 在每次发出 `Formed` 时递增，包括 `DIRTY_UNSTABLE` 之后的重新发出。

### 8.3 帧、草稿与视图（Frames, draft, view）

```java
record ContextFrame(ContextTier tier, long revision, Map<ContextKey<?>, Object> values) {}

record StructureContext(InstanceId instance, long revision, List<ContextFrame> frames)
        implements ContextView {}

public final class ContextDraft {                  // one stage's writes, not yet visible
    public ContextTier tier();  public long baseRevision();
    public <T> ContextDraft put(ContextKey<T> key, T value);          // merge == null only
    public ContextDraft forSection(SectionKey section);              // the scope contribute requires
    public <T> ContextDraft contribute(ContextKey<T> key, T partial); // merge != null only; needs forSection
    public <T> ContextDraft refuse(ContextKey<T> key);                // a section of the region was refused
    public <T> ContextDraft declined(ContextKey<T> key);              // every section readable, nothing contributed
    public <T> @Nullable T read(ContextKey<T> key);                   // the draft's own folded value
    public boolean publishes(ContextKey<?> key);                      // decides the key, value or absence
    public Set<ContextKey<?>> keys();                                 // every key this draft decides
}

interface ContextView {
    <T> @Nullable T get(ContextKey<T> key);
    <T> Outcome<T> require(ContextKey<T> key);
    ContextView at(ContextTier tier);
}

interface ContextStore {
    ContextDraft draft(ContextTier tier, long baseRevision);
    Outcome<Void> publish(ContextDraft draft);     // Missing(SC-CTX-0108) when base is stale
    StructureContext context(InstanceId instance);
}
```

- 计算写入尝试的草稿；匹配器在每个阶段边界用其读到的 `baseRevision` 发布一个草稿。
- `CONTEXT` 值进入层帧；`OUTCOME` 进入 `MatchOutcome.context`；`ATTEMPT` 被丢弃。
- 同一帧内一个键出现两个不同值是 `SC-CTX-0107`；过期的基线是 `SC-CTX-0108`。
- 在层 `t` 的读者看见 `t` 或以下最新的帧。

## 9. 引擎与执行（Engine and execution）

### 9.1 请求（Request）

```java
record MatchRequest(Blueprint blueprint, Siting siting, SnapshotLease lease, MatchPolicy policy,
                    InstanceBudget budget, PinBudget pins, InstanceRecord record, OwnerId owner,
                    @Nullable Ticket ticket) {}   // no stage: the engine selects it
                    // world() and the capture id are derived: lease.id()

record MatchPolicy(int maxSectionsPerUnit, CacheMode cache, boolean parallel) {}

MatchResult check(MatchRequest request);                                     // read-only
MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);     // the only writer
```

### 9.2 快照核对（Snapshot check）

`CaptureGate.evaluate(request.lease())` 从租约上读取两个事实：`changeCount() == 0`（自捕获以来
关注区域未被标记）与 `isValid()`（其世界仍存在且句柄未被关闭）。标记回答 `CHANGED`，并把该尝试
作为 `Dropped(SNAPSHOT_CHANGED)` 丢弃；无效的捕获回答 `SUPERSEDED`，并把它作为
`Dropped(CAPTURE_INVALID)` 丢弃。槽位的 `Ticket` 是调度器的状态，永不随捕获移动，因此一次预览
无法使一个正等待结算的计划失效（`19` R37）。重跑是在新捕获之上的新尝试；一次捕获绝不混合
修订号。

### 9.3 每个 `(world, owner)` 一个在途请求（One in-flight per `(world, owner)`）

较新的请求按 ticket 合并；`cancel()` 是协作式的。

### 9.4 活性（Liveness）

- 丢弃计数器与实例预算位于槽中，以单元 id 为键；合并会继承它们。
- 超过 `maxDiscards`（默认 3）后，单元以 `SC-MC-0404` 落定为 `Undecided(DIRTY_UNSTABLE)`；
  先前的 `Formed` 以 `generation + 1` 重新发出。
- `DEFERRED` 是终态，带一个重试令牌；只有 `AUTO` 会自动重新提交。
- `INTERACTIVE` 持有一个保留许可；`AUTO` 得到 `max(0, threads - 1)`，在单 worker 宿主上被禁用；
  `COMMAND` 共享其余部分。
- `autoChecksPerTick` 与 `autoSectionsPerTick` 限定 AUTO 的工作量；一次重试按另一次派发计费，
  不算作独立的单元。

### 9.5 计费（Billing）

- 预算只有两条腿：`cells` 与 `sections`。检查按它声明的读取闭包（`reads(bayCells).cells`）以及该
  闭包触及的区段计费；区段计算按切片体积加一个区段计费；聚合不计费。
- 派发时计费，无论是否命中缓存。`Spend(cells, sections)` 就是整个账本——没有逐单元 `admission`，
  没有 `checks` 腿，也没有 `retries` 腿。
- 同一计划的热运行与冷运行花费相同，因为两条腿都在查询缓存之前计入。

### 9.6 缓存（Cache）

```java
record CacheKey(PlanId plan, Siting siting, UnitId unit, long closureDigest) {}
```

`closureDigest` 对单元读取过的排序后的 `(SectionKey, contentDigest)` 配对取哈希。条目持有贡献与
单元结果，绝不持有折叠后的值。`cacheBytes` 是 LRU，与 `retainedBytes` 分开。

## 10. 声明、结算与结果（Claims, settlement, results）

### 10.1 一个谓词（One predicate）

```java
sealed interface ClaimVerdict permits Settled, ClaimConflict, RevisionMoved {
    record Settled(long revision, List<ClaimRow> rows) implements ClaimVerdict {}
    record ClaimConflict(List<ClaimRow> rows, FindingBundle findings) implements ClaimVerdict {}
    record RevisionMoved(long expected, long actual) implements ClaimVerdict {}
}

record ClaimRow(World world, LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting,
                NamespacedId bay) {}
record ClaimSet(List<ClaimRow> rows, long revision) {}

sealed interface ClaimPlan permits ClaimPlan.Matched, ClaimPlan.Invalid {
    record Matched(long ledgerRevision, List<BayBinding> bindings) implements ClaimPlan {}  // no rows
    record Invalid(FindingBundle findings) implements ClaimPlan {}
}
```

- `ClaimMatrix.conflicts(a, b)` 是唯一的冲突谓词；另一个世界的行，或同一个 `(owner, siting)` 的行在那里被排除。
- `ClaimPlanner.derive(world, blueprint, siting, owner, bindings)` 是唯一的行工厂，且它是纯净的。
  `settle` 自己运行它，在此之前对每个绑定针对本蓝图与本次捕获做检查（`SC-CLM-0005`），因此计划
  不携带任何可供调用方伪造的行（I-16）。
- `settle` 在一次 CAS 中替换 `(world, owner, siting)` 的所有行；`RevisionMoved` 在事务内重试一次，然后
  以 `Undecided(LEDGER_MOVED)` 落定并带一个重试令牌。

### 10.2 足迹（Footprint）

| 足迹 | 拥有的格 |
| --- | --- |
| `Bound`（默认） | 非空气的已绑定足迹的并集 |
| `Box` | `bay.cells`（一个空腔或外壳） |
| `Declared` | 显式的 `CuboidSet` |
| `None` | 无；可在任意处读取 |

- 声明保护结构所占据或声明的东西，绝不保护它所读取的东西。管道拐角是 `EVERY_CELL` + `Bound`：
  断言八格，拥有四格。
- 未被覆盖的蓝图边界报 `SC-CLM-0104`（WARNING）；熔炉空腔声明 `Box`。
- 在 SETTLE 时，若非 `None` 舱位的 `reads().cells` 越出 `bay.cells ∪ footprint`，报
  `SC-BAY-0108`（ERROR）。

### 10.3 两个入口，一个写者（Two entry points, one writer）

`check` 读世界与账本，返回判定与 **plan**——它按舱位找到的绑定，以及它读取的账本修订号
（`Admitted.planned()`、`Admitted.ledgerRevision()`）——且什么都不写，因此探测、预览、叠加层或
命令都可以随意调用它。`settle` 是唯一的写入路径：它接收请求、计划与实时快照提供者，检查快照与
每个绑定，用纯净的 `ClaimPlanner` **派生**出行，返回 `Formed`——携带 `ClaimSet` 与写入后的
修订号——或者带在位行的 `Failed(SC-CLM-0002)`。`check` 的冲突是预测，说“当前有主”；只有
`settle` 的冲突才是结论。

### 10.4 结果（Result）

```java
sealed interface MatchResult extends AutoCloseable
        permits Admitted, Dropped, Formed, Failed, Undecided, Deferred {
    @Override default void close() {}                       // a result that holds no handle closes to nothing
    record Admitted(Stage stage, Siting siting, MatchOutcome outcome,
                    @Nullable ClaimPlan planned, long ledgerRevision,
                    SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }
    }
    record Dropped(Stage stage, DropCause cause, List<Finding> findings) implements MatchResult {}
    record Formed(Stage stage, InstanceId id, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long generation, long contextRevision, long ledgerRevision,
                  SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }
    }
    record Failed(Stage stage, List<Finding> findings) implements MatchResult {}
    record Undecided(Stage stage, UndecidedReason reason, List<Finding> findings,
                     @Nullable MatchOutcome partial, @Nullable RetryToken retry) implements MatchResult {}
    record Deferred(Stage stage, RetryToken token, List<Finding> findings) implements MatchResult {}
}
```

`Admitted` 不是结构；`derive` 拒绝它，没有可声明的东西时 `planned` 是 `null`。只有 `settle`
产出 `Formed`。

## 11. 诊断项与补救方案（Findings and remedies）

### 11.1 诊断项（Findings）

- `Finding(id, code, severity, message, labels, notes, suggestion, related, stage)`；`Label(Locus,
  String, LabelRole)` 落在 `Locus`（`DEFINITION | LOCAL | WORLD | KEY`）上，严重级别住在
  `Finding` 上，绝不逐标签（`13` §4.1）。
- 族：`SC-DOC`、`SC-BAY`、`SC-CHK`、`SC-EXP`、`SC-FOC`、`SC-CLM`、`SC-CTX`、`SC-MC`、
  `SC-RMD`。
- 焦点诊断码：`SC-FOC-0101` 缺席，`SC-FOC-0201` 歧义，`SC-FOC-0301` 区段被拒绝，
  `SC-FOC-0302` 贡献缺失。
- 检查诊断码：`SC-CHK-0102` 成环，`SC-CHK-0106` 非法舱位策略，`SC-CHK-0107` 检查没有舱位，
  `SC-CHK-0109` 在产出阶段之前读取键。
- 诊断包顺序：严重级别、阶段、诊断码、定位域、插入顺序；按 `(code, primary label)` 去重。

### 11.2 解释（Explain）

`/cumulus explain` 渲染诊断包以及计划：阶段、单元、按 cells/sections 计的成本、读取足迹、读取与
发布的上下文键。

### 11.3 RemedyContext

```java
record RemedyContext(Blueprint blueprint, Siting siting,
                     @Nullable ClaimView claims, long ledgerRevision,
                     @Nullable MatchOutcome outcome, @Nullable StructureContext context,
                     RemedyOptions options) {}
```

- 提供者通过 `context` 读取提升后的值；缺失的键或缺失的账本意味着“无法提供帮助”。提供者永远
  拿不到租约；执行器持有它。
- `PlanValidator` 在捕获的视图上把计划折叠到后置状态；改变任何提供者读取过的 `ContextKey` 的
  计划是 `SC-RMD-0309`，被拒绝。
- `EditExecutor.begin(WorldView, Siting, ClaimView, long ledgerRevision, PlanDigest)` 在应用时
  复查声明与摘要；校验与应用之间的结算会被拒绝（`SC-RMD-0303`）。
- `ApplyReport` 返回一个新的 `RemedyContext`。

## 12. 文档（Documents）

### 12.1 Profile

- 规范的 `BlueprintDocument`；紧凑 profile；按形态决定 `DocumentProfile.AUTO`。
- `MIN_VERSION = 1`、`MAX_VERSION = 2`；v1 由 `DocumentUpgrade.v1ToV2` 升级。
- 两个 profile 都是无损的：`bounds`、`segments`、`assembly`、`extensions` 都有紧凑
  写法；捕获输出可在紧凑 profile 中写出。
- 展开是一个函数：具名舱位的格从更早的表面中切出；在其内部稍后写入是 `SC-DOC-0504`。
  `SC-DOC-0503` 是具名舱位被拆分；`SC-DOC-0505` 是 `EmptyCuboid`。
- 盒子写作 `[originX, originY, originZ, sizeX, sizeY, sizeZ]`。

### 12.2 规格（Specs）

```java
record Spec(NamespacedId kind, DataView args,
            @Nullable SpecRef focus, @Nullable SpecRef expectation,
            @Nullable ClassRef subject, @Nullable KeyRef output,
            @Nullable StatePhase phase, @Nullable Retention retention,
            @Nullable List<KeyRef> inputs) {}
```

`SpecResolver` 在 `compile`（DESIGN）时运行：它通过 `Catalogs` 解析焦点与期望，
检查 `focus.subject() == expectation.subject()`，校验键声明，并在 DESIGN 时拒绝不匹配
（`SC-CHK-0101`）。未知 id 是诊断项，绝不是反射。

### 12.3 紧凑形态（Compact form）

```json
{
  "schemaVersion": 2,
  "id": "mymod:blast_furnace",
  "bounds": [0, 0, 0, 7, 6, 7],
  "shell": [{"box": [0,0,0,7,6,7], "block": "minecraft:blast_furnace"}],
  "bays": [
    {"id": "mouth", "box": [3,1,0,1,2,1], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "bound",
     "expect": {"block": "minecraft:iron_bars"}},
    {"id": "interior", "box": [1,1,1,5,4,5], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "box",
     "expect": {"air": true}}
  ],
  "checks": [
    {"id": "mouth_facing", "bay": "mouth",
     "focus": {"kind": "occupant", "cell": [3,1,0]},
     "expect": {"kind": "state", "property": "facing", "value": "east"}}
  ],
  "computations": [
    {"id": "volume", "phase": "section", "bay": "interior",
     "output": "mymod:volume", "kind": "count", "args": {"mode": "cells"}},
    {"id": "capacity", "phase": "aggregate",
     "output": "mymod:capacity", "inputs": ["mymod:volume"],
     "kind": "scale", "args": {"factor": 4}}
  ],
  "context": [
    {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
    {"id": "mymod:capacity", "type": "long", "retention": "context"}
  ],
  "anchors": [{"role": "front", "property": "facing", "value": "east"}],
  "orientation": {"front": "north"}
}
```

- `interior` 是 5×4×5 = 100 格；`volume` 计数格；`capacity = 400`。
- 出现 `merge` 表示区段贡献；`phase: section` 要求它，`phase: aggregate` 禁止它。
- 具名的 `interior` 舱位把它的格从 `shell` 中切出；在其内部写 `fills` 条目会是 `SC-DOC-0504`。
- `context[]` 条目可以携带 `whenAbsent`（按 `type` 解码）；本例省略它，因为缺失的区段必须让
  `volume` 不可读。
- 读取某个键的检查以其焦点写作 `{"kind": "context", "key": "mymod:volume"}`。

### 12.4 降级（Lowering）

- 具名舱位整体发出；无法成为一个长方体的具名舱位是 `SC-DOC-0503`。
- 带洞的外壳降级为合成舱位，其 id 由自己的盒子派生：`#box@x,y,z:sx,sy,sz`。绝不用计数器。
- 具名舱位把它们的格从更早的表面中切出；在其内部稍后写入是 `SC-DOC-0504`，绝不是静默合并。

### 12.5 捕获与导入（Capture and import）

- `CaptureTool.capture(CaptureRequest, IssueSink) : @Nullable DataView`；任何丢失都是一条问题
  记录并返回 null。
- 捕获把 `stateLossy`、`issues`、`toolVersion` 写入 `extensions["cumulus:capture"]`；编译器忽略
  它，往返会保留它。
- 内容：可读时为 `{"kind": "container", "items": {...}}`，否则报 `SC-DOC-0521`，导入拒绝该
  方块实体，而不是放置一个空的。

## 13. 生命周期走查（Lifecycle walkthrough）

**巨型熔炉，AUTO。** 一个方块改变；一个区段被标记；读取闭包被捕获；`PROBE` 运行两个
`Check<OccupantSnapshot>` 单元并返回 `Admitted(PROBE)`。租约移交到 `FORM` 提交。`FORM` 运行外壳
检查、覆盖 5×4×5 空腔的 `volume` 区段计算，用 `Merges.sum()` 折叠贡献，写入 `capacity = 400`，
并在 FORM 修订 1 发布两个 `CONTEXT` 键。`SETTLE` 在服务器线程结算外壳与空腔声明，并返回带
`ledgerRevision` 的 `Formed`。`derive` 读取 FORM 帧，在 SETTLE 持久化 `RuntimeState`，子句柄
关闭。

**门，已成型。** `Focuses.bay()` + `Expectation<BayView>`：期望通过 `BayView.occupantAt` 看见
两半，测试铰链/朝向，舱位声明两格。邻居的门命中同样两格：一个结算为 `EXCLUSIVE`，另一个得到
`Failed(SC-CLM-0002)`。

**共享舱口。** A 与 B 都是 `SHARED`；两者都结算。稍后的 `EXCLUSIVE` 冲突并失败。

**管道拐角。** 2×2×2 包围盒，四个管道方块，四个空气格：`EVERY_CELL` + `Bound` 通过
`Focuses.allCells()` 断言八格，拥有四格。邻居的拐角声明它自己的四格。

**实体围栏。** `ANY_SUBJECT` 舱位中的 `Focuses.entity(cow)`。一头牛走出；每 tick 的窗口 diff 使
检查变脏，`SETTLE` 重跑。比跨度更宽的类型是 `SC-BAY-0110`。

**铁计数。** 一个区段 `Computation` 按区段贡献；`Merges.sum()`；`Focuses.context(IRON)` +
`Expectation<Long>`（`n >= 64`）。被拒绝的中间区段使该键缺席，`require` 返回 `Missing`，检查是
`UNREADABLE`，绝不是 `MISMATCH`。

## 14. 证明与证伪程序（Proofs and the falsification program）

| # | 主张 | 机制 | 状态 |
| --- | --- | --- | --- |
| P1 | 冷运行因预算耗尽而无法成型的地方，热运行也无法成型 | cells + sections 在查询缓存之前计入（§9.5） | 论证 |
| P2 | 声明是完备的且经过冲突检查 | 一个矩阵；一次 CAS；`RevisionMoved`（§10.1） | 论证 |
| P3 | `Formed` 可提交且是当前的 | 在 `Formed` 之前结算；携带修订号（§10.4） | 论证 |
| P4 | 捕获恰好释放一次 | 一个所有者、子句柄、可失败的 retain（§5.5） | 论证 |
| P5 | 缺席绝不是数字 | 无单位元；一个洞使折叠失败；`require` 返回 `Missing`（§7.2） | 论证 |
| P6 | 区段单元无法读取聚合状态 | `SectionContext` 没有 `read(ContextKey)`（§7.3） | 论证 |
| P7 | 朝向变换是锚点的函数 | 锚点在原点；一个映射方向（§4.4） | 论证 |
| P8 | 门是活的且可靠的 | 两个租约事实（`changeCount`、`isValid`）；槽计数器；每次尝试新捕获（§9.2、§9.4） | 论证 |
| P9 | 并行不改变结果或花费 | 折叠顺序固定；cells/sections 计费；诊断项排序（§7.2、§9.6） | 论证 |
| P10 | 发布只有一个写者与一种顺序 | 每实例一个存储；`baseRevision`（§8.3） | 论证 |
| P11 | 不完整的检查无法声明整个舱位 | §6.5 的四条规则；`Found.subjects` | 论证 |
| P12 | 内核无宿主依赖 | 导入扫描 | **不完整** |

证伪程序（`kernelTest`，R-15）：

1. `FurnaceScenario` — 外壳 + 空腔 `Box` + 贡献 + 折叠 + 上下文。
2. `DoorScenario` — 一个舱位、两个主体、`ONE_SUBJECT` 覆盖、原子声明。
3. `HatchScenario` — 共享/独占、`(world, owner, siting)` 替换、`RevisionMoved`。
4. `PipeCornerScenario` — `EVERY_CELL` + `Bound`，断言空气但不拥有。
5. `EntityPenScenario` — 没有方块变更的窗口 diff。
6. `AbsenceScenario` — 一个被拒绝的中间区段 → `UNREADABLE`，绝不是不完整的和。
7. `GateScenario` — 变动超过 `maxDiscards` → `DIRTY_UNSTABLE`、`generation + 1`。
8. `PhaseScenario` — `SectionContext` 无法调用 `read`（编译失败即为测试）。
9. `PublishScenario` — 过期的 `baseRevision` 被拒绝。

## 15. 处置（Dispositions）

### 15.0 更正：一种键，而不是三种（Correction: one key, not three）

修订 3 与 4 用 `Reducer` 把中间状态拆成 `Key`/`PartialKey`/`StateKey`。这重复了既有的上下文
API，并让中间状态看起来像第二种机制。修订 5 保留**一个** `ContextKey`，给上下文 API 增加三个
增量，并删除多余的键类型：

| 加入上下文 API 的内容 | 用途 |
| --- | --- |
| `ContextKey.merge` | 把键标记为由区段贡献 |
| `ContextDraft.contribute` | 区段阶段的写入 |
| `ContextView.require` | 把缺席作为 `Outcome`，而不是可空拆箱 |

`StateKey`、`PartialKey`、`Reducer`、`SectionComputation` 与 `AggregateComputation` 不再存在；
计算自己声明 `phase`。

### 15.1 第 1 轮 — 需求（Round 1 — requirements）

| # | 缺陷 | 裁定 |
| --- | --- | --- |
| D1 | 两套计算模型 | **已修复**：一个 `Computation`/`ContextKey`；`Deriver` 仅面向上下文（§6.4） |
| D2 | `MATCH` 声明整个舱位 | **已修复**：足迹；`Found.subjects`（§6.5、§6.6） |
| D3 | 检查没有 id 或阶段 | **已修复**：`Check` 携带 `id`（§6.2）；它的阶段已被 `23` §7 移除 |
| D4 | 锚点朝向从未到达内核 | **已修复**：锚点在原点、`AnchorResolver`（§4.4） |
| D5 | 实体被读取但不被拥有 | **部分修复**：窗口、diff、`ChangeSet.entities`；所有权按格（§5.4） |
| D6 | 焦点失败没有通道 | **已修复**：`Outcome.Missing`、`SC-FOC`、`Locus.KEY`（§6.6） |
| D7 | 四个提升开关；补救方案失明 | **已修复**：`Retention`；`RemedyContext.context`（§11.3） |
| D8 | 三个计数器 | **已修复**：计数器表（§8.2） |
| D9 | 检查/计算没有数据形态 | **已修复**：`Spec` + schema 2（§12.2） |
| D10 | 结算在匹配之外 | **已修复**：门面级结算；`Formed` 在 `Settled` 之后（§10.3） |
| D11 | PROBE 的 `Formed` 与已结算的类型相同 | **已修复**：`Admitted` 与 `Formed`（§10.4） |
| D12 | 没有一份参考代码能编译；纳秒 | **部分修复**：纳秒已移除；测试仍缺席（§14） |

### 15.2 第 1 轮 — 类型、状态、上下文（Round 1 — type, state, context）

| # | 缺陷 | 裁定 |
| --- | --- | --- |
| D1 | 判定依赖区块对齐 | **已修复**：没有逐区段检查阶段（§7.1） |
| D2 | 缺席折叠成单位元 | **已修复**：无单位元；一个洞使折叠失败（§7.2） |
| D3 | 键相等性包含 lambda | **已修复**：`(id, type)`；唯一性归目录所有（§6.4） |
| D4 | `T` 在数据路径上被擦除 | **部分修复**：`Check<T>` 编译期；`SpecResolver` 在 DESIGN（§12.2） |
| D5 | 两个提升权威 | **已修复**：一个存储、`baseRevision`（§8.3） |
| D6 | 层谱系被擦除；`STALE` 没有层 | **已修复**：帧 `(tier, revision)`；每实例存储（§8.3） |
| D7 | 焦点失败没有 `Reply` 通道 | **已修复**：`Missing` → `unreadable`（§6.6） |
| D8 | 步骤缓存与状态缓存 | **已修复**：一个缓存持有贡献/结果（§9.6） |

### 15.3 第 1 轮 — 运行时、创作、声明、补救方案（Round 1 — runtime, authoring, claims, remedies）

| # | 缺陷 | 裁定 |
| --- | --- | --- |
| D1 | 适配器更弱的冲突谓词 | **已修复**：一个矩阵（§10.1） |
| D2 | 租约所有权 | **已修复**：一个所有者、子句柄、可失败的 retain（§5.5） |
| D3 | 实体移动没有键或见证 | **已修复**：窗口、窗口索引、每 tick diff（§5.4） |
| D4 | 声明足迹排除被判定格 | **部分修复**：`Bound`/`Box`/`Declared`；未覆盖边界为 WARNING（§10.2） |
| D5 | 门禁不活 | **已修复**：`CaptureGate.evaluate(lease)` 在内核中、发布或结算任何东西之前运行；槽位合并仍在宿主侧（§9.2、§9.4） |
| D6 | 缓存/成本/身份矛盾 | **已修复**：cells/sections 计费 + 闭包区段、仅贡献的缓存（§9.5、§9.6） |
| D7 | `Spec` 没有数据形态 | **已修复**：`Spec`（§12.2） |
| D8 | 紧凑降级破坏舱位身份 | **已修复**：具名舱位整体、切出、盒派生 id（§12.4） |
| D9 | 捕获无法报告丢失 | **已修复**：`IssueSink`、捕获扩展、`container`（§12.5） |
| D10 | `RemedyContext` 失明 | **已修复**：事实、上下文、声明、修订号（§11.3） |

### 15.4 第 2 轮 — 语义、类型、运行时（Round 2 — semantics, types, runtime）

| 领域 | 缺陷 | 裁定 |
| --- | --- | --- |
| 语义 | 缺失 `Sharing`；门有两个主体；熔炉 JSON；管道拐角；上下文身份；实体焦点；朝向变换；派生顺序；租约转移 | `Bay` 携带 footprint/sharing；门是一个 `BayView`；每实例上下文；锚点在原点（§6、§8、§10） |
| 类型/数据 | 折叠带洞；`ONE_SUBJECT`+`Box`；按约定分阶段；键身份；降级；schema 无损性；合并定律 | 洞使折叠失败；足迹规则；`SectionContext` 没有 `read`；`Merge` 按契约满足结合律；切出；schema 2（§6、§7、§12） |
| 运行时 | 门；`RevisionMoved`；`Bound`；租约所有者；合并后的租约；丢弃计数器；实体见证；补救前置状态；计费；发布顺序 | 五元门；`RevisionMoved`；足迹规则；一个所有者；调度器关闭；槽计数器；窗口 diff；`SC-RMD-0309`；cells/sections 计费；`baseRevision`（§5、§9、§10、§11） |

### 15.5 镜像纪律（Mirror discipline）

每个英文原文都在 `zh/` 下有镜像，两者不一致时英文集是设计上的权威。改动一个英文文件却把它的
镜像留在旧处，是必须在同一次改动里修掉的缺陷，而不是可以推迟的条目。`23` 与 `24` 两个决策按
同一条规则抵达每个单元文件：声明入口、声明词汇、承载它的文档 schema，以及为它命名的码表。

## 16. 待决问题（Open questions）

1. **空体积保护。** `Box` 是封闭围栏与熔炉空洞的声明；按蓝图一个 `sealed: true` 标志是否
   优于一个警告加每舱位选择？
2. **窗口开销。** `MAX_ENTITY_SPAN = 3` 加每 tick diff；200 个窗口可接受吗，还是宿主需要共享
   空间索引？
3. **`whenAbsent`。** 只在每个区段都可读时生效；对一个可选的装饰性区域，这是否过严？
4. **`RevisionMoved` 重试。** 事务内重试一次：第二次移动是重试令牌还是硬 `Undecided`？
5. **紧凑计算。** `context[]` + `computations[]` 正确但冗长；常见的 `count`/`volume` 情形是否
   需要简写？
6. **`Coverage.ANY_SUBJECT`。** 仅存在的舱位不断言任何格；它能在没有洞的情况下成为
   `EXCLUSIVE` 吗？

### 16.1 第 3 轮残留（Round-3 residue）

这些是第 3 轮扫出的、仍然未决的问题：`23`/`24` 的清扫不裁决它们，而且每一条改的都是机制而不是
一句话。标题写作“已裁决”（decided）的条目会指名关闭它的那条裁决，并不再是待决问题。

7. **计划认证——已裁决（`19` R36）。** 计划根本不携带任何行：它持有一次 `check` 找到的绑定与它
   读取的修订号，而 `settle` 在把每个绑定针对本蓝图与本次捕获检查过之后（`SC-CLM-0005`），用纯净的
   `ClaimPlanner` 派生每一行。没有什么可伪造，因此不需要摘要（I-16）。
8. **`check` 可以发布什么。** 一个只读调用现在会触达每实例的上下文存储，并可能重新发出上一次的
   结果。要么 `check` 什么都不发布，要么把 `Retention.CONTEXT` 定义为一次写入、`check` 就此失去
   层帧（`SC-CTX-0108` 的 `baseRevision` 是既有的防线）。
9. **分离出去的 `settle` 的门——已裁决（`19` R37）。** `settle` 运行与那次尝试相同的
   `CaptureGate.evaluate(request.lease())`，因此它重读那两个租约事实，别的什么都不读：没有票据，
   没有第二次捕获，也没有提供者。取代归调度器所有。
10. **搬家时先 `release` 再 `settle`。** 这两半不是原子的，因此第二半失败会留下一个什么都不占的
    `EXCLUSIVE` 舱位——正是 R2 在结算期拒绝的状态。一个事务、一块墓碑，还是一次补偿性重新声明？
11. **声明行的世界身份——已裁决（`19` R35）。** `ClaimRow` 携带它的 `World`，账本句柄绑定到一个
    世界，且 `ClaimMatrix.conflicts` 先比较世界；各部分不一致的一次结算是 `SC-CLM-0007`。先前那条
    谓词“只比较单元格”的说法不再成立。
12. **`compute` 的阶段拆分。** `ComputationContext extends AggregateContext`，所以区段单元仍然可以
    调用 `read(ContextKey)`，而 P6 的“编译失败”证明假设这不可能。拆成
    `compute(AggregateContext)` 与 `compute(SectionContext, SectionDraft)`，让 draft 只暴露
    `contribute`？
13. **`Footprint` 与坐标系。** `Box`/`Declared` 是蓝图局部的，`Bound` 是世界单元格；一个
    `resolve` 返回不带标签的 `CuboidSet`，会让调用方一致地做平移并把行写进错误的坐标系。case
    要不要携带坐标系？
14. **`DecisionKind` 与 `Coverage`。** 三个 kind 名与三个 `Coverage` 名描述的是同一个区分；
    B-9/B-10 与 R4 是在决策集上表述的，而决策集只有 `Focus` 携带。合成一套词汇，还是两套？
15. **被拒绝单元的 `P5` 漏洞。** 只有 `Refused` 的区段会让键缺席；`DENIED` 的单元格以
    `Outcome.Missing` 到来，折叠就变成不完整的和。一个单元的 `Missing` 是否让该键对每个消费者都
    缺席？
16. **`I-6` 与 `R-08` 里的 `Stage` 由什么取代。** 阶段不再被声明之后，`I-6` 要求的检查点与 `R-08`
    点名的快失败都需要另一个载体——引擎自己的阶段顺序，还是计划的步骤结构？同一个答案也定下
    `09` 的 B-11/F-8（`SC-CHK-0109`，一个检查读取“在更晚阶段”产出的键）、`16` 里宿主的
    `PROBE`/`FORM` 层级，以及一个计算如何声明它在哪运行。
17. **`ONE_SUBJECT` + `Box`。** R3 只拒绝与 `bay.cells` 不同的**绑定**足迹，而 `Box` 按构造就
    等于舱位，所以今天一个带 `Footprint.Box` 的 `ONE_SUBJECT` 舱位是合法的——但它占的会是主体
    周围的空气，而不是主体自己的格。这个组合应该被拒绝，还是“占住舱位的空气”是个有意为之的
    选择？

## 17. 对抗协议（Adversarial protocol）

第 1 轮攻击覆盖、类型化模型、运行时与创作。第 2 轮攻击修订 2 的语义、类型/数据与运行时。
第 3 轮以同样三个面攻击修订 5，外加一次规范对代码的审计；它的发现放在 `_work/round3/`，残留是
§16.1，把已决部分带进各单元文件的这次清扫记录在 `19`。

批评者写出排序的缺陷清单：场景、被违反的需求或不变量、所述证明为何失败、最小修复。负责人
处置、修订，并把存留的决策记录在 `19`。这一轮仍欠一份**可编译**的 `kernelTest` 参考代码，使
无法表达某情形的签名让构建失败。
