# 14 — 补救方案（Remedies，`kernel.remedy`）

> U10：`Finding` 如何变成可对它采取的行动。服务 R-12、R-14；强制 I-10、I-11、I-14、I-15；
> 通过从不写入世界来保持 I-1 与 I-4。

## 1. 范围（Scope）

| 拥有 | 拒绝 |
| --- | --- |
| `Remedy`、`RemedyKind`、`RemedyProvider`、`RemedyContext`、`RemedyOptions` | 诊断项模型、诊断码、`Applicability`（U9） |
| `EditPlan`、`EditStep`、`Edit` + 7 个分支 | 蓝图、舱位、锚点、期望（U5/U3） |
| `PlanValidator`、`ValidationReport`、`PlanDiff`、`PlanDigest` | 声明语义（U4）：U10 读取一个 `ClaimView` |
| `EditExecutor`、`EditTransaction`、`ApplyReport` | 写入机制（A1）；权限、宿主边界 |
| `OverlayModel`、`CellMark`、`Marker`、`MarkerKind` | 颜色、几何、传输、绘制顺序（A1/宿主） |

依赖边遵循 `02-architecture.md` §2：`remedy ← blueprint, occupant, expectation, finding`。U10
不导入任何 `engine` 类型；提供者永远看不到 `MatchSession`；`Suggestion` 永远不携带计划。

**坐标系规则（frame rule）。** 计划与叠加层都是**相对于安放位（siting-relative）**的：一个
`Edit` 寻址本地 `LatticeKey`，而 `Siting` 在校验、应用与渲染时到达，因此计划只是 `(finding,
blueprint)` 的函数，可以在宿主提交之前完成校验。以 `WORLD` 为坐标系的步骤定位域会被拒绝
（`0204`）。

## 2. 术语（Vocabulary）

`01-glossary.md` §L 为准；下表是本单元内的约定。

| 名称 | 约定 |
| --- | --- |
| `Remedy` / `RemedyKind` | 密封的 `Repair`\|`Preview`\|`ExplainRemedy`；是提案，永不是应用 |
| `Repair` / `Preview` / `ExplainRemedy` | 计划 + 阶梯 / 仅叠加层 / 仅解释 |
| `RemedyProvider` | `solve(Finding, RemedyContext)`；全函数，空列表 = “无法提供帮助” |
| `RemedyContext` | 提供者所读取的冻结前状态：租约的捕获、蓝图、安放位、某一修订下的声明、提升后的事实与上下文、选项 |
| `EditPlan` / `EditStep` | 有序步骤 / 一次编辑、它的阶梯、它的前状态要求 |
| `Edit` | 密封，7 个分支；新增一个分支将带来新的撤销与校验语义 |
| `PlanDigest` | 计划步骤多重集的内容摘要；执行器把它已校验的摘要与它实际应用的计划相比较 |
| `PlanValidator` / `EditTransaction` / `EditExecutor` | 试运行 / 写入范围 / 宿主写入 SPI |
| `OverlayModel` / `CellMark` / `Marker` | 格标记与标记作为数据；没有渲染器类型 |

## 3. API 设计（API design）

### 3.1 家族（The family）

```java
public sealed interface Remedy permits Remedy.Repair, Remedy.Preview, Remedy.ExplainRemedy {
    RemedyKind kind();
    String rationale();
    record Repair(EditPlan plan, Applicability applicability, String rationale) implements Remedy {
        @Override public RemedyKind kind() { return RemedyKind.REPAIR; }
        public Preview preview(FindingBundle bundle) { … }   // the one sanctioned Preview path
        public Locus locus() { return plan().locus(); }
    }
    record Preview(OverlayModel overlay, String rationale) implements Remedy {
        @Override public RemedyKind kind() { return RemedyKind.PREVIEW; }
    }
    record ExplainRemedy(Explanation explanation) implements Remedy {
        @Override public RemedyKind kind() { return RemedyKind.EXPLAIN; }
        @Override public String rationale() { return explanation().summary(); }
    }
}
```

`Repair.preview(bundle)` 是把 I-11 机械化的地方：图像是执行器将要运行的那份计划的投影，绝不是
第二次派生。

### 3.2 求解（Solving）

```java
public interface RemedyProvider {
    NamespacedId id();
    List<Remedy> solve(Finding finding, RemedyContext ctx);      // never null, never throws
    static List<Remedy> solveAll(List<RemedyProvider> providers, Finding finding, RemedyContext ctx);
}
public record RemedyContext(Blueprint blueprint, Siting siting,
                            @Nullable ClaimView claims, long ledgerRevision,
                            @Nullable MatchOutcome outcome, @Nullable StructureContext context,
                            RemedyOptions options) {
    public Set<RemedyKind> kinds();        // options().kinds()
    public int maxResults();               // options().maxResults()
}
public record RemedyOptions(Set<RemedyKind> kinds, int maxResults, int planDepth) {
    public static RemedyOptions defaults();     // all kinds, 8, 1
}
```

`blueprint`、`siting` 与 `ledgerRevision` 始终存在；`claims`、`outcome` 与 `context` 恰在尝试到达
产出它们的阶段时存在，这正是它们为 `@Nullable`、并且提供者在读取前要先测试它们的原因。
`claims` 背后的那些行由 `settle` 写入 —— 唯一的写入入口 —— 因此提供者在 `ledgerRevision`
处读取它们，并且永不写入。宿主无法提供的 `ClaimView` 就是 `null`，那是第 8 行的“未检查声明”
情形，而不是空账本。

**提供者读取提升后的值，绝不重算它们。** 唯一获准的来源是 `ctx.outcome().context()`（一次
`OUTCOME` 保留）与 `ctx.context()`（一次 `CONTEXT` 保留，用 `context.require(key)` 读取）。
提供者永不接收快照租约：执行器为应用阶段持有它（§3.5）。某个键的 `require` 回答 `Missing`，
而事实也不持有它，意味着“本提供者无法提供帮助”：它返回 `List.of()`，并且**不得**遍历捕获
去私下派生同一个数——第二次派生就是第二个真相来源，它对后状态检查不可见（§4.4 第 9 行），
而这正是修复方案如何开始按它自己的计划所改变的值来定尺寸。`kinds()` 是过滤器，空集表示
“不提供任何方案”，绝不是“提供全部方案”；提供者返回 `null` 属于程序员错误，在边界处失败。

### 3.3 计划基座（Plan substrate）

```java
public record EditPlan(List<EditStep> steps, String rationale) {
    public static EditPlan empty();
    public CuboidSet touched();            // union of step cells: blast radius, from the plan alone
    public Locus locus();                  // LOCAL, touched()
    public Applicability applicability();  // join over steps (§4.3)
    public PlanDigest digest();            // canonical step multiset, no object identity
    public EditPlan merge(EditPlan other); // concat, dedup, canonical order
    public PlanDiff diff(EditPlan other);
}
public record EditStep(Edit edit, Locus locus, Applicability applicability,
                       @Nullable Expectation precondition) {
    public Cuboid cells();                 // edit.cells()
}
public record PlanDigest(String hex) { }   // lowercase hex of the canonical step multiset, never rendered
```

`merge` 满足交换律、结合律与幂等律：计划是其步骤*多重集*（multiset）的函数。

### 3.4 七个编辑（The seven edits）

```java
public sealed interface Edit permits Place, Remove, Replace, SetTrait, Configure, BindAnchor, NestedEdit {
    Cuboid cells();                                             // LOCAL frame
}
record Place(Cuboid cells, OccupantDescriptor expected, @Nullable NamespacedId bay) implements Edit
record Remove(Cuboid cells, @Nullable NamespacedId bay) implements Edit
record Replace(LatticeKey cell, OccupantDescriptor expected, OccupantDescriptor observed) implements Edit
record Configure(LatticeKey cell, Map<String, String> properties) implements Edit
record SetTrait<T>(LatticeKey cell, TraitKey<T> trait, @Nullable T value) implements Edit
record BindAnchor(NamespacedId role, LatticeKey cell) implements Edit
record NestedEdit(LatticeKey cell, EditPlan plan) implements Edit
```

五个格编辑把 `cells()` 实现为 `cell` 的单位长方体。

| 编辑 | 后置条件 | 前置条件（前状态） | 阶梯上限 |
| --- | --- | --- | --- |
| `Place` | 每个格持有 `expected` | 格可读 | `MACHINE_APPLICABLE` |
| `Remove` | 每个格为空 | 格可读 | `MACHINE_APPLICABLE` |
| `Replace` | 持有 `expected`，原先持有 `observed` | 持有 `observed` | `MACHINE_APPLICABLE` |
| `Configure` | 类型保持，获得 `properties` | 捕获中观测到相关键 | 若有某个键未被观测，则为 `HAS_PLACEHOLDERS` |
| `SetTrait<T>` | 携带 `trait = value` | `cell` 可读 | `MAYBE_INCORRECT`：可写性是宿主事实 |
| `BindAnchor` | `role` 解析到 `cell` | `cell` 位于蓝图边界内 | `MACHINE_APPLICABLE` |
| `NestedEdit` | 子步骤在占用体内部成立 | `cell` 持有嵌套占用体 | `min(own, child)` |

区域编辑与内容无关，格编辑针对单个占用体：修复一面墙是一步，而多格回滚（§4.5）因此成为实
实在在的义务。

### 3.5 试运行、差异与应用（Dry run, diff, apply）

```java
public final class PlanValidator {
    public static ValidationReport validate(EditPlan plan, SnapshotLease lease, Siting siting,
                                            ClaimView claims, RemedyContext ctx);
    public static ValidationReport validate(EditPlan plan, RemedyContext ctx);
}
public record ValidationReport(List<Finding> findings, boolean applicable) { }
public record PlanDiff(List<EditStep> added, List<EditStep> removed, List<EditStep> changed) { }
public interface EditTransaction extends AutoCloseable {
    enum State { OPEN, FAILED, COMMITTED, ROLLED_BACK, REFUSED }
    State state(); void apply(EditStep step); void commit(); void rollback();
    ApplyReport report(); @Override void close();      // rollback when OPEN or FAILED
}
public interface EditExecutor {
    EditTransaction begin(WorldView view, Siting siting, ClaimView claims,
                          long ledgerRevision, PlanDigest plan);   // REFUSED when the host declines
    default ApplyReport applyIfMachineApplicable(Remedy.Repair repair, RemedyContext ctx);
    default ApplyReport applyConfirmed(Remedy.Repair repair, RemedyContext ctx);
}
public record ApplyReport(boolean success, List<Finding> findings, RemedyContext context) {
    public RemedyContext postState();      // == context(), the fresh context for the next round
}
```

协议存在于默认方法里，因此每个适配器都跑同一条序列：校验 → 按阶梯设门禁 → `begin` → 折叠
步骤 → 提交或回滚 → 报告。顺序归内核，写入归宿主。`begin` 不是对世界的一把锁：它重新读取试运
行所依赖的三项事实——`ledgerRevision` 处的声明、计划摘要与格点——并在其中任何一项移动时以
`REFUSED` 拒绝，因此校验与应用之间的窗口由拒绝来收口，绝不由覆盖来收口。`ApplyReport.context`
是从应用后捕获构建的**全新** `RemedyContext`：第二轮提供者与调用方自己的后续，要么从后状态
出发，要么不出发。

## 4. 语义（Semantics）

### 4.1 一个家族，三种投影（Q1）

同一个求解结果——`EditPlan`——有三个消费者（执行器、渲染器、人），它们在输出上不同、在输入
上相同，并且都使用 `Finding` 已经在用的 `Locus` 词汇。

| 种类 | 承诺 | 不承诺 |
| --- | --- | --- |
| `REPAIR` | 一个计划，其后状态能回答该诊断项，并带有阶梯和能够拒绝它的试运行 | 宿主能够实现它；世界仍然匹配 |
| `PREVIEW` | 该后状态的逐格图像，外加为没有格的对象准备的标记 | 某个格已被占用；它来自一次读取 |
| `EXPLAIN` | 该诊断码的含义，以及该诊断项为何只能手工修复 | 任何形式的计划 |

**投影规则。** `Preview` 由 `Repair.preview(bundle)` 构造，因此两者携带同一份计划与同一个诊断
包。`ExplainRemedy` 恰好只在不可能有修复方案时提供：声明冲突、不可读的格、仅 `SetTrait` 的差
异，或阶梯低于调用方的下限。

### 4.2 `solve`：排序、去重、上限与纯净性（Q2）

提供者按 `id()` 升序枚举，然后在每个提供者内部按它自己文档化的顺序枚举；重复项按
`(kind, payload)` 折叠——即步骤多重集、标记映射或诊断码——保留提供者最小、其次列表下标最小的
一项；结果按 `(kind order, applicability ordinal, provider id, provider index)` 排序，并截断到
`maxResults()`，其中 `size == maxResults` 是唯一的截断信号。`solve` 不自行读取世界、不修改诊
断项、不写账本、不开启事务、从不调用 `PlanValidator`；提供者只读取 `ctx.context()` 与
`ctx.outcome()` 中的提升值。在两个包中都找不到自己那个键的提供者返回 `List.of()`。

### 4.3 计划构造：顺序、确定性、自包含（Q3）

主键 = 类别秩；次键 = 规范格顺序（`y`、`z`、`x`）；第三键 = `Edit` 的声明顺序；最后 = 来源
诊断项下标。秩：`BindAnchor`（0，角色使格有意义）< `Remove`（1，先移除障碍）< `Replace`、
`Place`（2，后续编辑写入的占用体）< `Configure`、`SetTrait`（3，需要一个占用体）<
`NestedEdit`（4，内部格位于其内）。

| 规则 | 陈述 | 违反 |
| --- | --- | --- |
| P1 | `step.locus.cells()` 等于 `step.edit.cells()` | `0205` |
| P2 | 坐标系为 `LOCAL` 或 `DEFINITION`，绝不是 `WORLD` | `0204` |
| P3 | **最后写入者**决定计划对每个格的后置条件 | `0201` |
| P4 | 秩顺序在每个共享格上两两成立 | `0202` |
| P5 | 深度 ≤ `planDepth`；没有 `NestedEdit` 包含自身 | `0203` |

**自包含。** 一个步骤只指名格、一个描述符、一个特征、一个角色、一个属性映射、一个子计划，以
及一个关于*前状态*的前置条件；没有快照、没有 `Siting`、没有世界句柄、没有其他步骤的结果。
应用该列表就是一次普通的折叠。

**阶梯合并。** `EditPlan.applicability()` 是各步骤阶梯在
`MACHINE_APPLICABLE < MAYBE_INCORRECT < HAS_PLACEHOLDERS < UNSPECIFIED` 下的**最大序数**：最不
确定的步骤说了算，因此计划的适用性永远不会高于它最弱的那次编辑。

### 4.4 试运行（Q4）

| # | 检查 | 读取 | 诊断码 |
| --- | --- | --- | --- |
| 1 | 形状：非空、深度、无环、P1/P2 | 仅计划 | `0307` `0203` `0205` `0204` |
| 2 | P3 最后写入者；P4 共享格上的秩顺序 | 仅计划 | `0201` `0202` |
| 3 | 格为 `READABLE`，不是 `OUT_OF_BOUNDS`/`DENIED` | `readStatus` | `0301` |
| 4 | 每个步骤的前置条件，针对前状态 | `occupant` | `0302`（不可读时为 `0301`） |
| 5 | 支撑：没有步骤需要另一个步骤清空的格 | 计划 + 第 4 行 | `0206` |
| 6 | 声明：被触碰的格被另一个声明者以 `EXCLUSIVE` 持有 | `claims` | `0303`；共享 ⇒ `0304` W |
| 7 | 空操作：前状态已满足该步骤 | `occupant` | `0305` I |
| 8 | 权限、载荷：内核不指名任何主体，也不检查任何载荷 | 无 | `0306` I，留给 `begin` |
| 9 | **后状态**：把计划折叠到捕获视图之上，重跑提供者从 `ctx.context()`/`ctx.outcome()` 读过的每个 `ContextKey`，然后比较 | `lease` + `context` + `outcome` | `0309`，拒绝该计划 |

`applicable` ⇔ 无 `ERROR`。警告与信息从不阻塞——除了一点：宿主无法提供的声明（`read` 回答
`null` 的 `ClaimView`）会发出 `0308`（“未检查声明”），并且**绝不授权写入**；只有
`ctx.claims().read(plan.touched())` 已回答的 `validate(plan, ctx)` 才为应用放行。带外格会被
拒绝，而不会被当作空气读取；宿主边界只以 `OUT_OF_BOUNDS` 的形式出现。任何东西都不被修改：
不可变视图、只读声明、无事务、无执行器 —— 账本唯一的写入者是 `settle`，而本表没有一行调用
它。代价 `O(m log m + v + p + f)`——宿主先读取
`plan.touched().volume()`，而第 9 行的代价是提供者实际读过的那些键，不是整个上下文。

第 9 行是修复方案自身的一致性检查。计划被允许改变世界；它不被允许让为自己定尺寸的那个数失
效。它所防止的具体振荡：提供者读到 `mymod:cavity_volume = 100`，并计算出一个按 100 格定尺寸、
位于 `y = 1` 的地板；计划的 `Place` 覆盖该地板，后状态折叠把 `cavity_volume` 重跑为 75（放
入的填充不再是空气），于是下一轮按 75 定出一个更小的地板，再下一轮又回到 100，世界就在两个
形状之间振荡而没有不动点。因为折叠发生在试运行*内部*，第一个这样的计划会以 `0309` 被拒绝，
提供者必须产出一个后状态让它读过的每个键都保持不变的计划——或者根本不读这样的键。

### 4.5 应用（Q5）

| 方面 | 约定 |
| --- | --- |
| 开启 | 每次应用在通过试运行之后调用一次 `begin(view, siting, claims, ledgerRevision, digest)`；`REFUSED` 不写任何东西 |
| 开启时的拒绝 | 在校验与应用之间落地的一次结算会移动账本修订，因此 `begin` 以 `0303` 拒绝并指名在位行；它绝不覆盖另一个所有者的格 |
| 应用 | 应用一个步骤；捕获所写每个格的**首次触碰**前像，包括状态与载荷；一次拒绝会把状态置为 `FAILED` 并给出 `0402` |
| 影响半径 | 恰好是 `plan.touched()`；宿主写到其外会静默破坏约定 |
| 提交 / 关闭 | 从 `OPEN` 提交即持久；关闭会回滚 `OPEN`/`FAILED`，并保留 `COMMITTED` |
| 回滚 | 按步骤逆序恢复每个前像，载荷在其方块之后；失败为 `0404`，`success == false` |
| 一致性 | 宿主在应用后重新捕获并重新匹配；返回的诊断项属于该期望，内核不重试任何东西 |
| 后状态 | `ApplyReport.context` 是对应用后捕获的全新 `RemedyContext`；前状态上下文永不交回 |
| 审计痕迹 | `findings`：开启时拒绝给出 `0303`，每个失败步骤给出 `0402`，完整回滚给出 `0403`，`0404` 指名仍被改动的格，宿主拒绝给出 `0401` |

### 4.6 图像（Q6）

| # | `CellMark` | 何时 |
| --- | --- | --- |
| 1 | `BLOCKED` | 一个声明诊断项（`0303`）覆盖该格：计划不得写它 |
| 2 | `WRONG` | 最后写入者是 `Replace`/`Configure`/`SetTrait`，且诊断包指名该格 |
| 3 | `MISSING` | 最后写入者是 `Place`，且诊断包指名该格 |
| 4 | `EXTRA` | 最后写入者是 `Remove`，且诊断包指名该格 |
| 5 | `UNKNOWN` | 诊断包指名该格不可读（`0301`），且不打算写入 |
| 6 | `EXPECTED` | `of(blueprint, siting)`：一个舱位格，不查询任何报告 |
| 7 | `PRESENT` | 计划指名该格，且诊断包没有反对它的说法 |

`PRESENT` 陈述的是计划的后状态，不是普查。不在 `cells()` 中的格是**未判定**的：`markAt` 返回
`null`，绝不是 `UNKNOWN`。`of(EditPlan, FindingBundle)` 预览一个损坏的结构；
`of(Blueprint, Siting)` 预览一个尚不存在的结构——舱位格为 `EXPECTED`，按角色给出 `ANCHOR` 标
记，按接缝给出 `SEAM` 标记。`Marker` 承载一切没有格的东西；`MarkerKind` 是严重级别
（`ERROR`/`WARNING`/`INFO`）或 `ANCHOR`/`SEAM`。

### 4.7 自动建造安全（Q7）

| 门禁 | 规则 |
| --- | --- |
| 阶梯 | `applyIfMachineApplicable` 只为 `MACHINE_APPLICABLE` 写入；否则 `0601`，不写入任何东西；由于合并，一个 `SetTrait` 就会让整个计划降级 |
| 确认 | `applyConfirmed` 是自动下限之下唯一的路径，且是一次单独的调用 |
| 先试运行 | 两个驱动器都在 `begin` 之前校验；不可用的报告不会开启任何东西 |
| 后状态优先 | 第 9 行在同一次试运行中运行：修复方案永不按它随后会使之失效的值来定尺寸（`0309`），因此自动建造不会在两个形状之间振荡 |
| 全新上下文 | 第二轮从 `ApplyReport.context` 出发，绝不从产出该计划的上下文出发 |
| 回滚 | 任何步骤失败都会回滚整个事务；没有部分成功 |
| 外来声明 | 被另一个声明者以 `EXCLUSIVE` 持有 ⇒ `0303` ⇒ 不可应用 ⇒ 永不应用；`SHARED` 的在位者（`0304`）先被指名，而校验与应用之间的一次结算由 `begin` 的修订检查捕获，不靠运气 |

两个驱动器都只写世界、不写别的：它们所读的声明行由 `settle` 写入，而 `settle` 是唯一可能写入
或重写声明行的调用，因此一次应用从不注册、释放或修复任何声明。

### 4.8 `RemedyOptions`（Q8）

| 旋钮 | 默认值 | 含义 |
| --- | --- | --- |
| `kinds` | 全部三种 | 唯一的种类过滤器；“包含预览”就是 `kinds ∋ PREVIEW`，而不是第二个布尔值 |
| `maxResults` | `8` | 排序与去重之后的上限；`0` = 不设上限 |
| `planDepth` | `1` | 一层嵌套框架；`0` 禁止 `NestedEdit` |

不存在 `allowWorldReads` 旋钮：提供者只看提升后的 `outcome` 与 `context`；持有捕获、并在校验时
折叠后状态的是执行器，不是提供者（§4.4 第 9 行、§3.5）。那个旋钮过去所表达的收窄就是
`RemedyOptions.kinds`，它决定是否要询问某个提供者。

## 5. 参考代码（Reference sketch）

```java
// Host-supplied: lease, claims, context, outcome, bundle, executor, sink, renderer.
// (1) A missing-wall repair, its preview, and a claim conflict that explains instead of repairing.
public final class CasingRemedyProvider implements RemedyProvider {
    @Override public NamespacedId id() { return NamespacedId.of("structurecumulus", "casing"); }
    @Override public List<Remedy> solve(Finding finding, RemedyContext ctx) {
        if (finding.code().equals(Codes.CLAIM_CONFLICT)) {
            return List.of(new Remedy.ExplainRemedy(new Explanation(finding.code(),
                    "a claim conflict cannot be built away", "release the foreign claim", List.of())));
        }
        if (!finding.code().equals(Codes.MISSING)) return List.of();
        Long volume = ctx.context() == null ? null
                : ctx.context().require(CapacityKeys.CAVITY_VOLUME)
                        .fold((value, subjects) -> value, missing -> null);
        if (volume == null) return List.of();       // cannot help; never recompute it here
        Cuboid wall = finding.labels().getFirst().locus().cells().bounds();
        EditStep step = new EditStep(
                new Place(wall, OccupantDescriptor.of(NamespacedId.of("minecraft", "bricks")),
                        NamespacedId.of("structurecumulus", "wall")),
                new Locus(LocusFrame.LOCAL, CuboidSet.of(wall), null, null),
                Applicability.MACHINE_APPLICABLE, Expectations.anyOccupant());
        Remedy.Repair repair = new Remedy.Repair(new EditPlan(List.of(step), "place the casing wall"),
                Applicability.MACHINE_APPLICABLE, "the wall bay is empty");
        return List.of(repair, repair.preview(FindingBundle.of(finding)));   // one plan, two projections
    }
}

// (2) Exhaustive over Remedy — no default branch (04 §3.2).
static String describe(Remedy remedy) {
    return switch (remedy) {
        case Remedy.Repair r -> "repair (" + r.applicability() + ", " + r.plan().steps().size() + ")";
        case Remedy.Preview p -> "preview of " + p.overlay().cells().size() + " cells";
        case Remedy.ExplainRemedy e -> "explain " + e.explanation().code();
    };
}

// (3) Exhaustive over Edit: may an executor write it unattended?
static boolean machineWritable(Edit edit) {
    return switch (edit) {
        case Place p -> true;
        case Remove r -> true;
        case Replace r -> true;
        case Configure c -> false;          // true only when every key was observed
        case SetTrait<?> s -> false;        // writability is a host fact
        case BindAnchor b -> true;
        case NestedEdit n -> n.plan().applicability() == Applicability.MACHINE_APPLICABLE;
    };
}

// (4) A blocked dry run: the wall is free, one cell has another owner.       // 0303 / 0309, nothing opened
ValidationReport dry = PlanValidator.validate(repair.plan(), ctx);
if (!dry.applicable()) { dry.findings().forEach(sink::accept); return; }

// (5) A rotated-furnace preview: the same plan, drawn instead of applied.     // no extra read
renderer.draw(OverlayModel.of(repair.plan(), bundle), siting);

// (6) Mid-plan failure: 0402 at step 2, 0403 for step 1, world byte-identical.
ApplyReport applied = executor.applyIfMachineApplicable(repair, ctx);
if (!applied.success()) sink.acceptAll(applied.findings());
RemedyContext postState = applied.postState();          // a fresh context, never ctx
```

## 6. 用例（Use cases）

| 谁 | 场景 | U10 提供什么 |
| --- | --- | --- |
| 插件作者 | 一个熔炉差三个外壳格；另一个 mod 独占其炉口 | 一个 `Place` 及其预览；对 `0303` 给出 `ExplainRemedy` 且没有计划 |
| 宿主（命令） | `/cumulus build <id> <anchor>` | `check` → `settle` → `solve` → `applyIfMachineApplicable` → 以聊天形式报告 |
| 宿主（GameTest） | 第 2 步被强制失败 | 回滚恢复状态与载荷，逐字节一致 |

## 7. 证明（Proofs）

**P1 — `solve` 与 `validate` 不修改任何东西。** 唯一的写入动词是 `EditTransaction.apply`，只能
从 `EditExecutor` 到达；`solveAll` 与 `validate` 都不指名它，且二者都接受不可变参数。残留项：
代价，而不是状态。

**P2 — 计划是确定性的、自包含的。** 顺序在 `(rank, y, z, x, kind, finding index)` 上是全序，
去重保留最小下标，且没有任何映射迭代、时钟或随机性参与（I-15）；`merge` 满足交换律、结合律与
幂等律。自包含性即 P1–P5 加上前状态规则，因此应用就是一次折叠；两个提供者在同一个格上不一致
就是 `0201`。

**P3 — 试运行相对于执行器前置条件的可靠性。** §4.4 的第 1–7 行恰好是执行器写前检查在同一份
不可变 `(view, claims, siting)` 上求值的谓词，因此第 *i* 步报告 `ERROR` 就蕴含第 *i* 步被拒
绝。按设计这是单向蕴含：执行器还会拒绝内核无法表达的东西（权限、无法实现的描述符、物理）。
校验与应用之间的窗口由 `begin` 比较账本修订与它校验时所依据的计划摘要来收口：中间落地的一次
结算就是 `0303`，事务永不开启，因此执行器拒绝而不是覆盖。

**P4 — 回滚完整性。** 每次 `apply` 捕获它写入的每个格的*首次触碰*前像——区域编辑的全部 *n* 个
格——并且 `touched()` 之外不写入任何东西；回滚按步骤逆序写回每个前像，载荷在其方块之后。
`Remove(3)` → `Place(2 of them)` → 失败：朴素的逐步撤销会恢复出中间的空气，首次触碰捕获会恢复
全部三个。

**P5 — 预览与修复方案一致。** `Repair.preview` 把同一份计划交给 `OverlayModel.of(plan,
bundle)`，因此标记表只是 `(plan, bundle)` 的函数。在一次成功应用和一次新捕获之后，重新匹配
对已修复的格不产生诊断项（I-4），因此 `of(samePlan, emptyBundle)` 恰好把这些格标为 `PRESENT`
——这是预览状态，不是对占用的主张。

**P6 — 顶层阶梯之下不自动应用。** `solve` 只返回值，够不到执行器；`applyIfMachineApplicable`
拒绝 `MACHINE_APPLICABLE` 之外的一切（`0601`）；`applyConfirmed` 需要修复方案的持有者发起一次
单独的调用；一个 `SetTrait` 就会让整个计划降级。宿主直接调用 `begin` 在约定之外，这正是写入
路径是 SPI 的原因。

**P7 — 修复方案无法使自己输入的失效。** 第 9 行把计划折叠到提供者所读的同一份捕获视图之上，
并重跑提供者从 `ctx.context()`/`ctx.outcome()` 取过的每个键。这些键是捕获内容的纯函数，而计划
是一个值，因此折叠是确定性的，且不需要任何世界写入；某个键改变就是 `0309` 且 `applicable` 为
false，因此计划永远到不了 `begin`。不读取任何提升键的提供者不会在这一行失败，而私下重算的提供
者则在约定之外（§3.2）——这就是残留项，也是为什么规则在两侧都写明。

## 8. 依赖与待决问题（Dependencies and open questions）

U10 建立在 U9（每个输入与产出的 `Finding`）、U4（`ClaimView`、声明行：`0303`/`0304` 门
禁）、U5（`Blueprint`、`Bay`、锚点角色 id）、U3（`Expectation`，用于 `EditStep.precondition`）、
U1（`Locus`、`LatticeKey`、`Cuboid`、`CuboidSet`）、`11`/`12`（`MatchOutcome`、`StructureContext`，
即提供者所读取的那些键）与 A1（执行器，它也构建应用后上下文）之上。尖锐之处：一个无法区分
“没有”与“无法回答”的 `ClaimView`，会让不含 `0304` 的沉默变得不安全；跨重载不稳定的 `Bay` id
会破坏可复现性；一个需要 `MatchSession` 的前置条件无法被校验；`CuboidSet.of(..).bounds()`、
单位 `Cuboid` 工厂，以及跨度可为空的 `Locus` 都属假定，`05` 中未写明。

建议的 `SC-RMD` 诊断码，交由 U9 接受或重新编号：计划 `0201`–`0205`（写入者、顺序、深度、坐标
系、定位域不匹配）；试运行 `0301` 不可读、`0302` 前置条件、`0303` 外来声明、`0304` 共享在位
者、`0305` 已满足、`0306` 权限、`0307` 为空、`0308` 未检查声明、`0309` 后状态改变了提供者读过
的一个 `ContextKey`；应用 `0401` 被拒绝、`0402` 步骤失败、`0403` 已回滚、`0404` 回滚不完整；
门禁 `0601` 在阶梯之下自动应用。“试运行拒绝了”的伞形诊断码是 `SC-RMD-0001`（`13` §4.3）。

`22` §16 的问题中有一个属于本单元，而它恰好落在 `begin` 上：

| 编号 | 问题 |
| --- | --- |
| OQ-1（`22` §16 Q4） | `RevisionMoved`：在结算事务内重试一次。第二次移动要么是重试令牌（`Deferred`），要么是硬性 `Undecided`——而在应用时，同一事件是 `0303`，完全不重试，因此两条路径必须在这个词上取得一致。 |

## 9. 证伪（Falsification）

| 证伪项 | 破坏什么 | 如何运行 |
| --- | --- | --- |
| 一个通过校验、却在应用中途失败的计划 | 若原因可见，则破坏 P3 的单向主张 | 在校验与应用之间把某个格声明为 `EXCLUSIVE`；`begin` 必须以 `0303` 拒绝并指名该格，且不得写入任何东西 |
| 一个按自己会改变的数值定尺寸的计划 | P7 / §4.4 第 9 行 | 提供一个读取 `mymod:cavity_volume` 并在空腔内放置填充的提供者：试运行必须返回 `0309`、`applicable == false`，且 `begin` 必须从未被到达 |
| 一个复用前状态的第二轮 | §3.5 `ApplyReport` | 应用一个计划，然后用 `applied.postState()` 调用提供者：提升值必须是应用后的那些，而不是计划赖以构建的那些 |
| 一个与修复方案不一致的预览 | I-11 / P5 | 用另一份计划的格手工构造一个 `Preview`；`PlanDiff` 非空，且 `plan().digest()` 与修复方案的不同 |
| 一个漏掉方块实体的回滚 | P4 | 带载荷的占用体，用 `Remove` 移除其周围 3 格区域，让第 2 步失败，断言载荷逐字节一致 |
| 一次覆盖共享声明的自动建造 | §4.7 | 两个蓝图共享一个格；第二次 `applyIfMachineApplicable` 必须以 `0303`/`0304` 失败 |
| 一个仅含 `SetTrait` 的计划被自动应用 | P6 | 单步计划；以 `0601` 拒绝 |
| 一个重算缺失键的提供者 | §3.2 | 一个缺少该键的上下文，加上一个本可回答它的视图：`solve` 必须返回空列表，且该补救方案的计划必须不存在 |
| 一个通过读取世界构造的叠加层 | §4.6 | 一个所有读取都抛异常的 `WorldView`；`of(plan, bundle)` 仍须返回 |
| 一个通过校验的 `WORLD` 坐标系步骤 | P2 | 一个带世界 `Locus` 的步骤；预期 `0204`，绝不发生写入 |
