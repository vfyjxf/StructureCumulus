# 07 — 期望（Expectations，`kernel.expectation`）

> 一个舱位期望什么、一次测试可以回答什么、测试如何组合、文档如何命名一个期望。
> 服务于 R-06、R-07、R-08、R-10、R-13；冻结名称见 `01-glossary.md` §E。

## 1. 范围（Scope）

| 拥有 | 不拥有 |
| --- | --- |
| `Expectation<T>` SPI、`ExpectationContext<T>`、`ProbeSession`、`Verdict`、`Reply`、`Bindings`、`BindingKey<T>` | 哪些舱位存在、它们的 `Cuboid`、它们的 `Coverage`、它们的 `Footprint` 与 `Sharing`（U5） |
| `Focus<T>`、`Outcome<T>`、`DecisionKind`、判定代数、`ExpectationMeta`、`Expectations`、`ExpectationFactory`、`FactoryContext` | 声明与冲突——期望不产生任何声明（U4） |
| 主体通道（`Found.subjects`）、`reads()` 的贡献与 `specificity` | 舱位何时运行、`Spend` 的单元格/区段、`Stage` 与步骤顺序（U7）；诊断项顺序与上限（U9）；文档文法（U6） |

它从不修改、不抛异常，也不读取上下文之外的内容。

## 2. 术语（Vocabulary）

名称在 `01-glossary.md` §E 中定义，在 §3 中成形：`Expectation<T>`、`ExpectationMeta`、
`ExpectationContext<T>`、`Focus<T>`、`FocusContext`、`Outcome<T>`、`DecisionKind`、`ReadScope`、
`ProbeSession`、`Verdict`、`Reply`、`Bindings`、`BindingKey<T>`、`Expectations`、
`ExpectationFactory`、`FactoryContext`。`Reply` 是这里唯一非泛型的名称：判定针对的是整个检查，
而不是它的主体类型。

## 3. API 设计（API design）

```java
public interface Expectation<T> {
    Reply test(ExpectationContext<T> ctx);                          // total, re-entrant, stateless
    Class<T> subject();                                             // the one subject type it accepts
    default ReadScope reads() { return ReadScope.none(); }          // declared reads beyond the bay
    default ExpectationMeta meta() { return ExpectationMeta.anonymous(); }   // no id, no demand
}

public interface Focus<T> {
    Outcome<T> take(FocusContext ctx);                              // binds the subject, or says why not
    Class<T> subject();
    DecisionKind decisionKind();                                    // EVERY_CELL | ONE_SUBJECT | ANY_SUBJECT
    CuboidSet staticDecisionCells(Cuboid bayCells);                          // DESIGN-time; no world needed
    ReadScope reads(Cuboid bayCells);
    FindingBundle describeMissing();
}

public sealed interface Outcome<T> permits Outcome.Found, Outcome.Missing {
    record Found<T>(T value, List<OccupantRef> subjects, FindingBundle notes) implements Outcome<T> {}
    record Missing<T>(FindingBundle findings) implements Outcome<T> {}

    <R> R fold(BiFunction<T, List<OccupantRef>, R> onFound, Function<FindingBundle, R> onMissing);
}

public enum DecisionKind { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }

public record ExpectationMeta(NamespacedId id, int specificity, Set<ContextKey<?>> requires,
                              FindingCode mismatchCode) {
    public static ExpectationMeta anonymous();   // structurecumulus:anonymous, 0, empty, SC-EXP-0001
    public static ExpectationMeta of(NamespacedId id, int specificity, FindingCode mismatchCode);
}

public enum Verdict { MATCH, MISMATCH, ABSTAIN, UNREADABLE }

/** Four states, never generic: one check answers one Reply, whatever its subject type is. */
public record Reply(Verdict verdict, Bindings bindings, FindingBundle findings) {  // ctor is private
    public static Reply match();  public static Reply match(Bindings b);
    public static Reply match(Bindings b, FindingBundle notes);    // no ERROR findings
    public static Reply mismatch(Finding first, Finding... rest);  // >= 1 finding by construction
    public static Reply unreadable(Finding first, Finding... rest);
    public static Reply unreadable(FindingBundle findings);        // the focus's Missing channel
    public static Reply abstain();
}

public interface FocusContext extends AggregateContext {          // read, occupants, context
    BayView bay();  WorldView view();  Siting siting();           // bay() carries the id and the cells
    Cuboid cells();  Stage stage();  Locus locus();               // locus() is this check's label frame
}

public interface ExpectationContext<T> extends FocusContext {     // what a test may see
    T subject();  List<OccupantRef> subjects();
    <V> ExpectationContext<V> withSubject(V subject, List<OccupantRef> subjects);   // one cell of a bay test
    static <T> ExpectationContext<T> of(FocusContext base, T subject, List<OccupantRef> subjects);
    Budget budget();  ProbeSession session();
    @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
}
```

| 类型 | 签名 |
| --- | --- |
| `Bindings` / `BindingKey<T>` | `EMPTY`、`of`、`<T> @Nullable T get(key)`、`merge(Bindings, Bindings, Locus): Reply`；键为 `(NamespacedId id, Class<T> type)` |
| `FocusContext` | `extends AggregateContext`（后者回答 `read(key)`、`occupants(box)` 与 `context()`），并增加 `bay(): BayView`、`view()`、`siting()`、`cells()`、`stage()`、`locus()`；因此一个检查只能通过 `context()` 读取 `ContextKey`，别无其他途径 |
| `ExpectationContext<T>` | `extends FocusContext`，并增加 `subject(): T`、`subjects()`、`withSubject(value, subjects): ExpectationContext<V>`——为舱位测试中的单个单元格重新指定主体类型的唯一途径——、静态的 `of(base, subject, subjects)`、`budget()`、`session()`、`occupantAt(p)`——null ⇔ 该单元格所在区段不可读 |
| `ProbeSession` | `occupant(LatticePoint)`、`claimsAt(LatticePoint): List<Claim>`（仅限冻结账本；会话预留保持私有）、`<T> @Nullable T binding(BindingKey<T>)`、`remaining(): Budget`、`stage(): Stage` |
| `DoorView` | `record (OccupantRef first, OccupantRef second, BayView bay)`：`door` 焦点绑定的两半，`first` 是下半（`06` §3.2），以及它们所在的舱位 |
| `Expectations` | `allOf`、`anyOf`、`not`、`sequence`、`atLeast(k)`、`atMost(k)`、`exactly(k)`、`block`、`air`、`eachCell(element)`、`trait`、`anyOccupant`、`nothing`、`custom(meta, body)`——每个都返回其子项主体类型的 `Expectation<T>`；`eachCell` 是唯一改变该类型的组合器（`Expectation<OccupantSnapshot>` → `Expectation<BayView>`） |
| `ExpectationFactory` / `FactoryContext` | `id()`、`create(DataView, FactoryContext): @Nullable Expectation<?>`；`factory(id)`、`source()`、`range()`、`report(Finding)` |

`subject()` 是一次测试接受的唯一类型：`Check<T>` 拒绝 `subject()` 不同的焦点与期望
（`SC-CHK-0101`），因此 `DoorView` 测试无法与 `BayView` 焦点配对。`meta().specificity` 用于打破
平局，绝不是预算输入；`requires` 是**覆盖**要求（§4.5）。`reads()` 默认为 `ReadScope.none()`，
而读取舱位决策单元格之外内容的期望——相邻的单元格、实体窗口、`ContextKey`——必须声明它，
因为 `Check.reads(bayCells)` 是 `focus.reads(bayCells).union(expectation.reads())`，而这个读取
闭包正是引擎按单元格与区段计费的对象（`22` §6.1、§9.5）。不存在 `cost` 字段，也完全不存在
逐单元的数字：预算是对单元格与区段的粗粒度资源上限。`Reply`**不是**泛型的：判定针对整个检查，
与它的主体类型无关，且一个检查在四种状态中的一种里产出一个 `Reply`。`custom(meta, body)` 测试
是匿名的——特异性为 `0`，没有声明的读取——因此它在平局中排在最后，且只按它的决策单元格计费；
`Expectation` 本身不是 lambda 目标，因为 `subject()` 是抽象的。`Predicate` 被禁用（术语表
§O），而且形态本身就不对：`boolean` 无法携带期望值/实际值这一对（R-07），也没有“未知”这一
回答，于是未加载的区块会被读成*错误*；没有承载元数据的字段（R-08）；没有不受约束的
`and`/`or`/`negate` 路径。

## 4. 语义（Semantics）

### 4.1 应答不变量（Reply invariants）

| 判定 | 绑定 | 诊断项 | 理由 |
| --- | --- | --- | --- |
| `MATCH` | 0..n | 0..n，且无 `ERROR` | 一个见证；INFO/WARNING 可以为其附加注释 |
| `MISMATCH` | **空** | **≥ 1** | 没有证据的反驳是缺陷 |
| `UNREADABLE` | **空** | **≥ 1** | 不可读的格子就是修复目标 |
| `ABSTAIN` | 空 | **空** | 拒绝必须是静默的，否则会被读成证据 |

包级私有的 `Reply` 构造器是唯一的关口：只有 `match*` 会存入绑定。**`UNREADABLE` 不是
`MISMATCH`**——不匹配陈述的是世界事实（“是石头，不是铁栏杆”），不可读陈述的是快照事实
（“我没有读”），因此把二者合并会让未加载的区块看起来是错的。**空气是可读的**：空气是非 null
的 `OccupantSnapshot`，所以对空气执行 `block(...)` 得到的是指名 `minecraft:air` 的 `MISMATCH`；
只有当状态不是 `READABLE` 时，`occupantAt` 才是 `null`。四种状态是穷尽的，且 `Reply` 不是
泛型的：一个检查恰好产出一个 `Reply`，而缺席没有自己的判定——它通过焦点进入（§4.2）。

### 4.2 缺席（Absence）

焦点回答 `Outcome<T>`，期望回答 `Reply`，而 `Check` 是这两条通道相遇的唯一地方：

```java
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
```

| 情形 | 产出 | 诊断码 |
| --- | --- | --- |
| 主体不在那里 | `Outcome.Missing` → `Reply.unreadable` | `SC-FOC-0101` |
| 有多个候选主体，且没有一个是决定性的 | `Outcome.Missing`，有歧义 | `SC-FOC-0201` |
| 焦点所需的某个区段被拒绝 | `Outcome.Missing` | `SC-FOC-0301` |
| 部分贡献在屏障处缺失 | 该键是 `Outcome.Missing`，每个读取方都是 `unreadable` | `SC-FOC-0302` |

- `Missing` 携带标注在 `Locus` 上的诊断项（上下文键缺席为 `LOCAL`，单元格为 `WORLD`/`LOCAL`），
  因此标签指向的是缺席处，而不是舱位的其他单元格。
- 读取键的焦点是 `ContextKey<T>` 上的 `Focuses.context(key)`：它恰好是
  `ctx.context().require(key)`，因此键缺席是携带 `SC-FOC-0302` 的 `Outcome.Missing`——消息指名
  该键，标签落在一个既没有单元格、也没有自身键的 `LOCAL` 定位域上——绝不是持有 null 的 `Found`，
  也绝不是零值。该键只在一处声明，即在 `reads()` 中；同一个缺席还会作为 `ContextView.require`
  自己的诊断项出现在 `CheckResult.notes` 里（§4.2 的参考代码），因此它被报告两次，处于两个框架
  之中。
- 主体缺席绝不是 `test(null)`，也绝不是静默：`fold` 的第二个分支根本不会调用期望，应答是携带
  该焦点诊断项的 `UNREADABLE`。`describeMissing()` 提供它们的措辞。
- 每个不可读诊断项都是 `SC-FOC-*` 的检查会结算为 `Undecided(ONE_SUBJECT_ABSENT)`，绝不是
  `Failed`：没有任何东西被反驳，主体只是没有被找到。

### 4.3 折叠：四种判定上的两个全序（The fold: two total orders on four verdicts）

计数器：`m` 为 MATCH、`x` 为 MISMATCH、`a` 为 ABSTAIN、`u` 为 UNREADABLE、`t` 为未测试（untested）、
`n = m+x+a+u+t`。枚举自身的顺序不表示任何含义；`allOf` 是 `MATCH ≺ ABSTAIN ≺ UNREADABLE ≺ MISMATCH`
下的 `max`，`anyOf` 是恰好相反顺序下的 `max`。每个单元格读作 `allOf / anyOf`：

| 左 ⊕ 右 | MATCH | ABSTAIN | UNREADABLE | MISMATCH |
| --- | --- | --- | --- | --- |
| **MATCH** | M / M | A / M | U / M | X / M |
| **ABSTAIN** | A / M | A / A | U / U | X / A |
| **UNREADABLE** | U / M | U / U | U / U | X / U |
| **MISMATCH** | X / M | X / A | X / U | X / X |

各行读作：已知的违反是决定性的（`allOf(MISMATCH, UNREADABLE) = MISMATCH`），见证不能被撤销
（`anyOf(MATCH, ABSTAIN) = anyOf(MATCH, UNREADABLE) = MATCH`），而可能是见证的未知子项会让析取
保持未知（`anyOf(MISMATCH, UNREADABLE) = UNREADABLE`）。两个折叠都是结合的、交换的、幂等的，且
`not` 是对合。有三条定律不成立：阈值上的 De Morgan 律（对 MATCH+ABSTAIN 取 `not(atLeast(1))` 得
MISMATCH，而对取反后的子项取 `atMost(1)` 得 MATCH）；`anyOf` 在*绑定*上的交换性（第一个匹配者
绑定）；`allOf` 在*诊断项顺序*上的交换性。

| 组合器 | `MATCH` 条件 | `MISMATCH` 条件 | 其他情况 |
| --- | --- | --- | --- |
| `allOf` | `m = n` | `x > 0`（在第一个 MISMATCH 处停止） | `u > 0` → UNREADABLE，否则 ABSTAIN |
| `anyOf` | `m ≥ 1`（在第一个 MATCH 处停止） | `m = a = u = t = 0` | `u > 0` → UNREADABLE，否则 ABSTAIN |
| `sequence` | 同 `allOf`，按声明顺序 | 同 `allOf`，按声明顺序的第一个 MISMATCH | 同 `allOf` |
| `atLeast(k)` | `m ≥ k` | `m+u+a+t < k` | `u > 0` → UNREADABLE，否则 ABSTAIN |
| `atMost(k)` | `m+u+a+t ≤ k` | `m > k` | 同上 |
| `exactly(k)` | `m = k`，`u+a+t = 0` | `m > k` 或 `m+u+a+t < k` | 同上 |
| `not(c)` | `c` 是 MISMATCH | `c` 是 MATCH | `c` 的判定（A 仍为 A，U 仍为 U） |

阈值使用这种计数，而不是两两折叠。组合器只在**决定性**判定处停止，因此 `allOf` 会越过
`UNREADABLE` 与 `ABSTAIN` 继续寻找 `MISMATCH`，否则在本可证明 `Failed` 的地方会读成 `Undecided`；
`k > n` 的 `atLeast(k)` 被组合结果反驳（`SC-EXP-0004`）。

### 4.4 绑定与合并（Bindings and merge）

`merge` 返回 `Reply`，因此没有判定就看不到合并后的映射。

- 键不相交，或值相等：`MATCH`，每个键一个条目。
- 同一个键、值不相等：`MISMATCH`，携带 `Bindings.EMPTY` 和一个 `SC-EXP-0003` 诊断项，指名两个
  定位域和两个值——是诊断项，绝不是异常。
- 同一个 `id` 出现在两个 `Class` 标记下：`MISMATCH` 与 `SC-EXP-0003`，因为 id 空间是扁平的。

### 4.5 特异性与 requires（Specificity and requires）

特异性对 `allOf`/`sequence` 取 `max`，对 `anyOf` 与 `k < n` 的阈值取 `min`（一个可以在跳过某
子项时仍被满足的测试，不能声称拥有该子项的窄度），对 `not` 取 `0`。`requires` 取并集，`anyOf`
也包含在内，因为它是对*覆盖*的要求：未被满足的 `ContextKey` 会在读取任何单元格之前就让舱位
`UNREADABLE`，而把它当作未命中则会把“我没有看”变成 `MISMATCH`。`reads()` 是各子项读取范围的
并集，`subject()` 是它们共同的 `T`；两者都不是价格——读取范围就是引擎在运行时按单元格与区段
计费的内容（§4.7）。

### 4.6 主体舱位与门规则（Subject bays and the door rule）

一个舱位拥有一个 `Cuboid`；焦点决定其中哪些单元格被断言，而期望永远只看到主体。

| 焦点种类 | 主体类型 | `DecisionKind` |
| --- | --- | --- |
| `bay` | `BayView` | `EVERY_CELL` |
| `allCells` | `List<OccupantSnapshot>` | `EVERY_CELL` |
| `occupant` | `OccupantSnapshot` | `ONE_SUBJECT` |
| `entity` | `EntitySnapshot` | `ONE_SUBJECT` |
| `nested` | `NestedView` | `ONE_SUBJECT` |
| `context` | `T` | `ANY_SUBJECT` |
| `door` | `DoorView`（两个 `OccupantRef`，一个合并状态） | `ONE_SUBJECT` |

| 规则 | 陈述 |
| --- | --- |
| 元素 | 元素测试是 `Expectations.block(type)` 与 `Expectations.air()`，二者都作用于 `OccupantSnapshot`；绑定该主体的焦点是 `occupant`，而 `door` 是一种焦点（`Focuses.door`），绝不是组合器 |
| 舱位测试 | 针对整个舱位的测试是作用于 `BayView` 的 `Expectations.eachCell(element)`，与 `bay` 配对；元素通过 `withSubject` 一次看到一个单元格 |
| 几何 | 焦点的决策单元格是 `bay.cells` 内的 `CuboidSet`，在 DESIGN 由 `staticDecisionCells(bayCells)` 计算；动态种类（`occupant`、`entity`、`nested`、`context`）没有决策单元格 |
| 主体 | `focus: door` 产出 `DoorView`；门期望消费 `DoorView`，绝不消费 `BayView`，因此把它与 `BayView` 焦点配对是 `SC-CHK-0101`，而不是一次强制转换 |
| 原子性 | 一个 `Check`、一个 `Reply`：焦点取一次主体，期望回答一次，被绑定的主体随 `Found.subjects` 传递 |
| 证据 | 逐格事实是挂在**一个**诊断项上的 `Label`——第一个标签为主，其余为次 |
| 声明 | 期望不产生任何声明；U4 从 `Found.subjects` 的占地轮廓构建 `Bound`，从 `bay.cells` 构建 `Box` |
| 半扇门 | 错的那一半 ⇒ `MISMATCH`，主标签落在它上面，次标签落在匹配的那一半 |

**为什么矩形足够**：声明包含（U4）、预筛边界（U7）、内部体积（U8）与朝向等距变换（U1）都是
长方体算术，而体素集合式舱位会一次性让这四项退化为逐格枚举。非矩形的*单个*舱位需要在 `Cuboid`
之外再加一种足迹类型，因此被拒绝（I-3）；它应拆成多个 `allOf` 舱位，或一个包围盒，由测试拒绝
那些必须为空的格子。

### 4.7 排序：声明顺序，而非代价（Ordering: declaration, not cost）

彼此独立的单元保持声明顺序。`specificity` 是报告顺序的提示，不是预算数字；`requires` 仍然把
单元排到它所读取的键之后。不存在 `admission` 字段，也不存在逐单元代价：引擎按声明的读取闭包
以 `cells` 与 `sections` 计费（`22` §9.5）。

## 5. 参考代码（Reference sketch）

```java
final class HatchExpectation implements Expectation<OccupantSnapshot> {   // three outcomes, one binding
    static final BindingKey<OccupantRef> HATCH = BindingKey.of(ID("hatch"), OccupantRef.class);
    public Class<OccupantSnapshot> subject() { return OccupantSnapshot.class; }
    public ExpectationMeta meta() { return ExpectationMeta.of(ID("hatch"), 80, SC_EXP_0001); }

    public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
        OccupantSnapshot o = ctx.subject();                              // never null: absence is Missing
        if (!o.has(Traits.ITEM_INVENTORY)) return Reply.mismatch(Finding.builder(SC_EXP_0001)
                .message("expected an inventory-bearing hatch")
                .label(ctx.locus(), "expected an inventory-bearing hatch")
                .note("found " + o.descriptor().type()).build());
        return Reply.match(Bindings.of(HATCH, o.ref()));
    }
}

final class OakDoorExpectation implements Expectation<DoorView> {     // one bay, two subjects, one Reply
    private static final NamespacedId OAK = NamespacedId.of("minecraft", "oak_door");

    public Class<DoorView> subject() { return DoorView.class; }
    public ExpectationMeta meta() { return ExpectationMeta.of(ID("oak_door"), 90, SC_EXP_0001); }

    public Reply test(ExpectationContext<DoorView> ctx) {
        DoorView door = ctx.subject();                                   // both halves, already bound
        if (!OAK.equals(door.first().type())) return wrongHalf(ctx, door.first(), "lower");
        if (!OAK.equals(door.second().type())) return wrongHalf(ctx, door.second(), "upper");
        return Reply.match();                                            // subjects travel in Found
    }

    private static Reply wrongHalf(ExpectationContext<DoorView> ctx, OccupantRef found, String half) {
        return Reply.mismatch(Finding.builder(SC_EXP_0001)               // one finding, two labels
                .label(cellLocus(ctx, found.anchor()), "expected the " + half
                        + " half of an oak door, found " + found.type())
                .label(ctx.locus(), "the other half matches; the bay is claimed only when both do")
                .build());
    }

    private static Locus cellLocus(ExpectationContext<DoorView> ctx, LatticePoint cell) { /* … */ }
}

// block+trait on one cell, an anyOf fallback, and a pair whose members may ABSTAIN.
var core  = Expectations.allOf(Expectations.block(NamespacedId.of("minecraft", "barrel")),
                               Expectations.trait(NamespacedId.of("minecraft", "item_inventory")));
var panel = Expectations.anyOf(Expectations.custom(PANEL_META, ctx -> {
    OccupantSnapshot s = ctx.occupantAt(ctx.cells().min());
    return s == null ? Reply.unreadable(/* as above */)
            : s.descriptor().tags().contains(Tags.STONE_FAMILY) ? Reply.match() : Reply.abstain();
}), new WoodPanel());
// `anyOf(HatchExpectation, Expectations.block(mc("bars")))` is one `Expectation<OccupantSnapshot>`:
// a hatch-or-bars cell is one occupant focus and two element tests, not two bays.
```

一个既不是石头也不是木材的面板会让两个子项都弃权，于是 `anyOf` 弃权，引擎报告
`Undecided(ABSTAINED)`；一半不在那里的门舱位永远不会到达 `test`——焦点返回
`Missing(SC-FOC-0101)`，该次尝试是 `Undecided(ONE_SUBJECT_ABSENT)`——而两半都在、其中一半是
错误的方块时给出 `Failed`，并带一个含两个标签的诊断项。`OakDoorExpectation` 不绑定任何东西：
两个 `OccupantRef` 随 `Found.subjects` 到达，声明占地轮廓正是从它构建的。

### 5.1 工厂环节（The factory step）

- `id()` 缺失，或 `create` 返回 null：`ctx.report(SC-EXP-0010)`，指名该 id 与已知 id；DESIGN
  ERROR。非 null 的结果按原样使用。
- 参数不合法：`ctx.report(SC-EXP-0011)`，以出错的键作为注释、以 `ctx.range()` 作为标签；返回
  null。报告了错误却仍返回实例的工厂会被丢弃。
- 递归：`ctx.factory(id)` 是 `@Nullable`；装配器限制深度（`SC-EXP-0013`）。
- 身份：`meta().id` 必须等于 `id()`（`SC-EXP-0012`）；匿名是 `SC-EXP-0014` INFO。

不使用反射：冻结的目录是唯一的名称到实例路径，因为反射得到的 id 在 DESIGN 阶段无法检查，并且
会把宿主类名带进内核决策（I-1、I-8）。

## 6. 用例（Use cases）

**附加组件作者。** 注册 `id() = mymod:door` 的 `DoorFactory`，随附
`{"expectation": "mymod:door", "wood": "oak"}`；拼写错误（`"oack"`）会在 `compile`（DESIGN）
阶段以 `SC-EXP-0011` 暴露，早于任何世界读取。

**宿主。** `/cumulus validate` 对命名了不存在附加组件工厂的舱位报告 `SC-EXP-0010`；
`/cumulus build` 中若门舱位跨越未加载的区块，则返回 `Undecided(UNREADABLE)`。

## 7. 证明（Proofs）

- **确定性**：`take` 与 `test` 在 `(snapshotId, siting, bay, bindings, budget)` 上是纯的，且组合
  器按下标遍历 `vararg` 数组；用 `HashSet` 存放子项会破坏这一点。
- **非匹配不产生绑定**：只有 `Reply` 的包级私有构造器会存入绑定。
- **折叠优先级**：两个折叠都是全序上的 `max`，因此决定性行由该顺序推出；阈值与之相符，因为
  `m+u+a+t < k` 不存在能达到 `k` 的读法。
- **读取闭包单调性**：检查的读取范围是单元格集合的并集，增加子项不可能缩小它，因此当一棵树被
  扁平化时，引擎计费的单元格与区段永不减少——扁平化不可能让计划比它替换的树更便宜。
- **门原子性**：一个 `Check`、一个 `Reply`；声明所依据的主体恰好是 `Found.subjects`，因此门不
  可能只声明一半。
- **缺席不是一个值**：`Outcome.Missing` 永远不会到达 `test`，因此 `test(null)` 不可表示，缺失
  的一半也不可能被读成不匹配。

## 8. 依赖与待决问题（Dependencies and open questions）

依赖 U0 `base`（`NamespacedId`、`Budget`、`Stage`）、U1 `space`（`Cuboid`、`CuboidSet`、`Locus`、
`Siting`）、U2 `occupant`（`WorldView`、`ReadStatus`、`OccupantSnapshot`、`OccupantRef`）、U8
`context`（`ContextKey`、`ContextView`、`AggregateContext`）、U4 `claim`（`Claim`）与 U9
`finding`（`Finding`、`FindingBundle`、`FindingCode`、`SourceSpan`）。U5 拥有 `Bay`，并把
`staticDecisionCells` 的输入提供给它；U7 对检查的读取闭包计费，然后调用焦点与期望。

**OQ-1——实体窗口代价（`22` §16 Q2）。** `focus: entity` 与 `focus: occupant` 读取一个膨胀的
实体窗口；如果宿主采用共享空间索引，`Focus.reads(bayCells)` 必须指名一个索引而不是一个窗口
列表，且每个随附的焦点都要改变形态。

**OQ-2——`whenAbsent` 的严格性（`22` §16 Q3）。** 除非其区域的每个区段都可读，否则
`ContextKey` 就是 `Missing`，因此读取它的检查在部分被拒绝的区域里无法区分“声明缺席”与“未
读取”。一个可选的装饰性区域是否可以取声明的值，尚未决定，而它决定了这样的检查能否曾经
`MATCH`。

Q1、Q4、Q5 与 Q6 不是本文件的问题：Q1（空体积保护）与 Q6（无 `Declared` 占地轮廓时的
`Coverage.ANY_SUBJECT` 加 `EXCLUSIVE`）属于 `08`/`09`，Q4（`RevisionMoved` 重试）属于 `16`，
Q5（紧凑计算）属于 `10`/`12`。

## 9. 证伪（Falsification）

- **F1**：`allOf(mismatch("bricks"), match())` 返回 `MATCH`、丢弃子项的诊断项，或让 `UNREADABLE`
  压过该 `MISMATCH`。
- **F2**：`anyOf(mismatch(), unreadable())` 返回 `MISMATCH`；`not(unreadable())` 返回除
  `UNREADABLE` 之外的任何值；`allOf` 在第一个 `UNREADABLE` 处停止，而后面本可证明 `MISMATCH`。
- **F3**：两个 `MATCH` 子项把同一个 `BindingKey` 绑定到不相等的值，而合并静默保留其一、抛异常，
  或返回仍携带绑定的 `MISMATCH` 应答。
- **F4**：回答 `Missing` 的门舱位仍然拥有一个单元格，或 `Failed` 的门舱位拥有任何声明。
- **F5**：`occupantAt` 对空气格返回 `null`；某个叶子把非 `READABLE` 状态转成 `MISMATCH`；缺失
  的主体到达 `test(null)` 或被报告为静默；检查的运行在没有计入其期望声明的读取的情况下被计费。
- **F6**：针对 `BayView` 写的门期望能编译，或一个 `Check` 把 `BayView` 期望与 `door` 焦点配对
  并到达一次世界读取，而不是以 `SC-CHK-0101` 失败。
