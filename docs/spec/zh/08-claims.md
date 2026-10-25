# 08 — 声明（Claims）

> 服务于 R-11、R-08（`SETTLE` 是独立阶段）、I-10（冲突是一个 `Finding`，绝不是静默覆盖）。
> 依赖 `base`、`space`；计划那一半读取 `blueprint`。与 `22` 修订 5 §10 对齐：两个轴、
> 一个谓词、一次 CAS。

## 1. 范围（Scope）

**拥有**声明身份、两个轴、矩阵、各范围的占地轮廓、`ClaimLedger` SPI、计划与规划器、`SC-CLM`
诊断码。**不拥有**舱位的 `Coverage`、`Footprint` 与 `Sharing` 声明（U5）、覆盖合法性（U3/U7，
`SC-CHK-0106`）、诊断项（U9）、持久化（宿主，A1）。**不是**锁：声明是一个值。

## 2. 术语（Vocabulary）

| 名称 | 形态与含义 |
| --- | --- |
| `Sharing` | `EXCLUSIVE`、`SHARED`：同一格上是否排斥另一份登记 |
| `Footprint` | `Box`、`Bound`、`Declared`、`None`：一个舱位拥有哪些单元格 |
| `ClaimRow` | `(world, cell, sharing, owner, siting, bay)`：一个世界的一个单元格，由一个所有者在一个安放位上的一个舱位持有 |
| `ClaimSet` | `(rows, revision)`：一次完整结算，行按规范顺序 |
| `ClaimConflict` | `(rows, findings)`：未通过矩阵的行，加上指名它们的诊断项 |
| `ClaimMatrix` | 唯一的冲突谓词 `conflicts(a, b)`：同一个世界、同一个单元格、不同的归属，至少一方为 `EXCLUSIVE` |
| `ClaimLedger` | 宿主存储，一个世界一个句柄：`world()`、`settle(SettleRequest)`、`release(OwnerId, Siting)` 与 `read(Cuboid)` |
| `ClaimView` | 一次读取：与某个长方体相交的行、读到的修订号，以及存储是否作答 |
| `SettleRequest` | `(world, owner, siting, rows, expectedRevision)`：单次 CAS 的单位 |
| `ClaimPlan` | `check` 发现的内容——按舱位的绑定，以及它读取的账本修订号；**不携带任何行**，因为每一行都由 `ClaimPlanner` 派生，因此没有调用方能把自建的行交给 `settle` |
| `ClaimPlanner` | 唯一的行工厂：`derive(World, Blueprint, Siting, OwnerId, bindings)`，纯净且确定性 |

`ClaimRow` 携带它的世界与它的安放位：`ClaimMatrix.conflicts` 先比较世界，然后排除同一
`(owner, siting)` 的两行，因此世界与结算键都随行一起传递（§4.1）。

## 3. API 设计（API design）

```java
public enum Sharing {
    EXCLUSIVE, SHARED;
    public boolean blocks() { return this == EXCLUSIVE; }
}

public sealed interface Footprint permits Box, Bound, Declared, None {
    record Box() implements Footprint {}                         // the bay cuboid, air included
    record Bound() implements Footprint {}                       // union of bound non-air footprints
    record Declared(CuboidSet cells) implements Footprint {}     // exactly these cells
    record None() implements Footprint {}                        // no cell
}

public record ClaimRow(World world, LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting,
                       NamespacedId bay) {}

public record ClaimSet(List<ClaimRow> rows, long revision) {
    public ClaimSet { rows = List.copyOf(rows); }                 // canonical order, §4.1
}

public sealed interface ClaimVerdict permits Settled, ClaimConflict, RevisionMoved {
    record Settled(long revision, List<ClaimRow> rows) implements ClaimVerdict {}
    record ClaimConflict(List<ClaimRow> rows, List<Finding> findings) implements ClaimVerdict {}
    record RevisionMoved(long expected, long actual) implements ClaimVerdict {}
}

public record SettleRequest(World world, OwnerId owner, Siting siting, List<ClaimRow> rows,
                            long expectedRevision) {}

public final class ClaimMatrix {
    public static boolean conflicts(ClaimRow a, ClaimRow b);      // §4.2; another world or one (owner, siting) never conflicts
}

public interface ClaimLedger {                                    // @NullMarked; settle and release are the only writes
    World world();                                                // the one world this handle can answer for
    long revision();                                              // ledger-wide CAS base, independent of a box
    ClaimVerdict settle(SettleRequest request);                   // one CAS over every row of (world, owner, siting)
    ClaimVerdict release(OwnerId owner, Siting siting);            // delete every row of the key; bumps the revision
    ClaimView read(Cuboid box);                                   // never null; an empty answer is available
}

public interface ClaimView {
    List<ClaimRow> rows();                                        // rows intersecting the box, canonical order
    long revision();                                              // the revision this answer was read at
    boolean available();                                          // false = the store could not answer
}

public sealed interface ClaimPlan permits ClaimPlan.Matched, ClaimPlan.Invalid {
    /** What a check found, and the ledger revision it read. No rows: they are derived, never handed over. */
    record Matched(long ledgerRevision, List<BayBinding> bindings) implements ClaimPlan {}
    record Invalid(FindingBundle findings) implements ClaimPlan {}  // nothing may be written
    record Overlap(NamespacedId low, NamespacedId high) implements Comparable<Overlap> {}
}

public final class ClaimPlanner {
    /** The only row factory: pure, deterministic, no view, no ledger, no I/O. */
    public static Derived derive(World world, Blueprint blueprint, Siting siting, OwnerId owner,
                                 Collection<BayBinding> bindings);
    public record Derived(List<ClaimRow> rows, List<ClaimPlan.Overlap> overlaps,
                          CuboidSet staticFootprint) {}
}
```

值是不可变 record，按结构相等，绝不为 `null`。`Sharing` 的序号从不比较：不存在强度顺序。账本
代价按**返回**的行计，而不是按查询的单元格计。

## 4. 语义（Semantics）

### 4.1 身份与顺序（Identity and order）

声明者是 `(world, owner, siting, bay)`：两个摆放是两个声明者，同一蓝图的两个舱位是两个声明者，在同一
安放位上的重新检查是同一个声明者。世界与安放位都是行的组成部分，因此结算键 `(world, owner, siting)`
正是从行本身读出的。

| 对象 | 全序 |
| --- | --- |
| 单元格 | U1 的规范顺序（`LatticeKey` 的自然顺序） |
| `ClaimRow` | `(world, cell, owner, bay)`；共享值从不用于打破平局 |
| `ClaimSet.rows`、`Settled.rows`、`ClaimConflict.rows` | `ClaimRow` 顺序 |

**替换规则。** `settle` 在一次 CAS 中替换 `(world, owner, siting)` 的*每一*行：没有逐行合并，没有逐舱位
跳过，也不把请求与它自己的在位行相比较。在同一安放位上的重新检查会重现它的集合，并递增修订号。
*移动过的*结构是同一所有者处在不同的安放位：替换无法触及旧的键，因此它先前的行会留存，
`EXCLUSIVE` 的摆放会与它们冲突，直到宿主结算或清除该键。结构移动到另一个世界，就是在旧世界的
一次 release，以及在新世界的一次 settle。

### 4.2 声明矩阵（The claim matrix）

同一世界的两行冲突，**当且仅当至少一方是 `EXCLUSIVE`。** 两个世界的行永不冲突：两个世界中的相同
坐标是两个不同的单元格，而一个世界的账本是它的单元格唯一存在的地方。

| `a` \ `b` | `EXCLUSIVE` | `SHARED` |
| --- | --- | --- |
| `EXCLUSIVE` | **冲突** | **冲突** |
| `SHARED` | **冲突** | 共存 |

| 组合 | 理由 |
| --- | --- |
| `EXCLUSIVE` / `EXCLUSIVE` | 两者都要求独占一个单元格 |
| `EXCLUSIVE` / `SHARED` | 共享方仍然*使用*该单元格；应提升共享方，绝不降级它 |
| SHARED 的组合 | 两者都不排他，因此这里无需决定；这一格就是共享的 |

`ClaimMatrix.conflicts(a, b)` 是唯一的冲突函数：内核规划器与 `SavedClaims` 都调用它，适配器没有
自己的谓词。它先比较世界与单元格，再比较共享值，并对同一 `(owner, siting)` 的两行返回 false，因此
`settle` 替换一个声明者的全部行时，不会把它们判成与自身冲突。谓词是对称的；报告是有方向的（`a` 为
挑战者，`b` 为在位者）。一次结算冲突是 `Failed` 并带 `SC-CLM-0002`：没有建议性放宽，且 `check` 把这些
相同的行报告为一次预测，而不是一个结论。

**内部重叠。** 同一蓝图的两个舱位是两个声明者，因此矩阵原样适用：重叠合法当且仅当两种共享值共存。
`SHARED` 组合是静默的；静态那一半（`Box`、`Declared`）是蓝图设计错误，在 `DESIGN` 判定，那时还不
存在任何声明行，它就是 `SC-BPT-0002`，定位域为相交的单元格；同一蓝图内的 `EXCLUSIVE` 组合同样是
那个 `SC-BPT-0002`，绝不是账本冲突，因为对同一 `(owner, siting)` 的两行，
`ClaimMatrix.conflicts` 返回 false。`Bound` 那一半需要绑定后的占地轮廓，在 `SETTLE` 判定（§4.3）。

### 4.3 占地轮廓与范围（Footprints and scopes）

| 范围 | 拥有的单元格 | 解析时机 |
| --- | --- | --- |
| `Bound`（默认） | **非空气**绑定占地轮廓的并集 | `SETTLE`，来自 `BayBinding.subjects()` |
| `Box` | `bay.cells` | `DESIGN` |
| `Declared` | 声明的 `CuboidSet` | `DESIGN` |
| `None` | ∅；可在任意处读取 | 从不 |

- `Bound` 是绑定主体占地轮廓的并集减去空气。若该并集与平移后的 `bay.cells` 不相等，`ONE_SUBJECT` 舱位会被拒绝（`SC-BAY-0107`），半边门永远不会变成计划。它**不是**舱位长方体，也**不是**检查所读取的
  内容：占地轮廓来自被绑定主体的 `OccupantSnapshot`（`22` §5.1），而不是来自一次世界读取。
- 声明保护结构**占用或声明**的内容，绝不保护它仅仅读取的内容。必须受保护的空体积声明 `Box`
  （熔炉内腔）；混合或不规则的保护区声明 `Declared`；不得声明任何内容的判据是 `None`。
- **管道拐角。** 一个 2×2×2 的舱位带四个管道方块与四个必须为空气的单元格，是 `EVERY_CELL` +
  `Bound`：断言八个单元格，拥有四个。邻居的拐角在该长方体的另外几个单元格上拥有它自己的
  四个，因此两次结算不冲突。不用 `allOf` 绕路，也不强制 `SHARED` 共享值。
- **残余会被指名。** 一个 `bounds` 单元格若没有任何非 `None` 舱位可能拥有它——不在任何舱位的
  `cells` 内，也不在任何 `Declared` 集合内——在 `DESIGN` 得到 `SC-CLM-0104`（WARNING）。因为
  `Bound` 在那里尚未解析，同一诊断码会在 `SETTLE` 时针对绑定后的占地轮廓重新求值，两次都
  保持 WARNING。这是一个决策，不是漏洞：该警告是该选择的可见记录，也是未受保护的空体积唯一
  的信号。
- 当一个非 `None` 舱位的 `reads().cells` 越出 `bay.cells ∪ resolved footprint` 时，
  `SC-BAY-0108`（ERROR）在 `SETTLE` 触发。DESIGN 时的版本对 `Bound` 是空洞的，已撤回；
  `None` 舱位豁免，因为它什么都不拥有，可以在任意处读取。

### 4.4 阶段（Stages）

`DESIGN` 校验两个轴与静态占地轮廓；世界遍历（`PROBE`、`FORM`）从不接触账本。`check` 读取账本
一次、什么都不写，并返回计划中的声明连同它读到的修订号；`settle` 在服务器线程上执行写入（§4.5），
此后才可能存在 `Formed`。定向重新检查是带上 `ChangeSet` 的 `check`，而 `release` 删除一个实例的行
（23 §5）。

覆盖与声明保持两个轴：`Coverage` 是一次 MATCH 断言了什么，`Sharing` × `Footprint` 是它拥有
什么，合法性表（`22` §6.5）在 `DESIGN` 强制执行。`EVERY_CELL` 舱位断言 `bay.cells` 的每个单元格；
`EXCLUSIVE` 需要 `None` 之外的占地轮廓（`SC-CHK-0106`，R1）。仅存在性的 `ANY_SUBJECT` 声明究竟
能否是 `EXCLUSIVE`，仍待决（`22` §16 Q6）。

### 4.5 通过 `settle` 入口点的结算（Settlement through the `settle` entry point）

- 匹配器产出一个 `ClaimPlan`——它按舱位找到的绑定，加上它读取的账本修订号，且**没有行**——并且不写任何行。`check` 在
  `Admitted` 上返回该计划；`settle(request, admitted)` 派生出行并应用它们。结算是该调用的一步，而不是匹配
  工作线程的工作，因此任何声明都不会在服务器线程之外被写入。
- **行是派生出来的，绝不直接交出。** `settle` 对请求的 `(world, blueprint, siting, owner)` 与计划的绑定运行
  `ClaimPlanner.derive(...)`，因此到达账本的内容是蓝图与这次捕获的函数，而不是调用方数据的函数（I-16）。
  一个并非由 `check` 产出的 `ClaimPlan` 因此是无害的，而不是危险的：它里面没有什么可供伪造。
- **绑定在矩阵之前被结构性地检查**：每个绑定都指名本蓝图在此安放位上声明的一个舱位；它携带的每个主体都在
  **本次**捕获中被观测到（`OccupantRef.snapshot`，`19` R8）；其种类与舱位的期望相符；且其单元格落在
  `bay.cells` 内——对 `ONE_SUBJECT` 要求完全相等（R3）。失败即 `Failed(SC-CLM-0005)`，什么都不写入。
- `settle` 不会重新运行期望。那个判定是 `check` 的，而捕获门禁把世界钉在它上面（`20` §5.4），因此一个在
  `check` 运行时为真的绑定现在仍然为真，而一个从来不为真的绑定会被上面的捕获检查捕获，而不是被第二次匹配捕获。
- `14` 的 `EditPlan` 携带一个 `PlanDigest`，因为它跨越一次人工决策、从一次校验旅行到稍后的应用；`ClaimPlan`
  刻意不携带：它里面没有行，也就没有什么需要摘要来保护。
- `settle` 是对 `(world, owner, siting)` 全部行的一次 CAS，以 `expectedRevision` 为条件。
- 出现 `RevisionMoved` 时，结算器在事务内部**一次**重读并重新检查；第二次变动是
  `Undecided(LEDGER_MOVED)`，携带一个重试令牌，绝不是凭空构造的冲突。
- `Formed` 只在 `Settled` 之后存在，携带 `ClaimSet` 与结算时的修订号。冲突是
  `Failed(SC-CLM-0002)` 并带有在位行；无法作答的账本是 `Undecided`。
- `release(owner, siting)`——在一个只服务一个世界的句柄上——在一个事务中删除该键的每一行并递增
  修订号；被释放的键读出来是空的，而调用它的三个生命周期点是 `23` §5 的——实例移除、一次移动、
  一次世界卸载。
- 内核不做 I/O（I-15）：`ClaimLedger` 是宿主的事务。适配器把该 CAS 实现为一个事务：修订号匹配
  时删除 `(world, owner, siting)` 的每一行并插入请求的行，因此写入中途崩溃会由下一次结算修复。

### 4.6 冲突诊断项（Conflict findings）

每个 **(owner, bay) 对**一个诊断项，而不是每个单元格一个：争议单元格构成一个 `CuboidSet` 定位域，
主标签落在规范顺序上第一个争议单元格，一条注释给出数量。

| 诊断码 | 严重级别 | 阶段 | 含义 |
| --- | --- | --- | --- |
| `SC-CLM-0001` | ERROR | SETTLE | 不同所有者的两笔 `EXCLUSIVE` 声明命名同一个单元格 |
| `SC-CLM-0002` | ERROR | SETTLE | 本舱位要求 `EXCLUSIVE`，而另一所有者已持有 |
| `SC-CLM-0003` | ERROR | SETTLE | 本 `SHARED` 舱位遇到另一所有者的 `EXCLUSIVE` |
| `SC-CLM-0004` | WARNING | SETTLE | 账本无法作答，或修订号变动两次：无结论 |
| `SC-CLM-0005` | ERROR | SETTLE | 一次结算的绑定对该请求无效：舱位不属于本蓝图、主体来自另一次捕获、种类不匹配，或单元格落在舱位之外；不会写入任何内容 |
| `SC-CLM-0006` | INFO | SETTLE | 某次结算产出的声明因该次尝试未成型而未被使用、被退回 |
| `SC-CLM-0104` | WARNING | DESIGN / SETTLE | `bounds` 的一部分不属于任何舱位的范围 |
| `SC-BPT-0002` | ERROR | DESIGN | 同一蓝图的两个舱位重叠，至少一方为 `EXCLUSIVE`（U5 家族，此处引用） |
| `SC-BAY-0108` | ERROR | SETTLE | 非 `None` 舱位读取了 `bay.cells ∪ footprint` 之外的内容（U7 家族，此处引用） |
| `SC-BAY-0111` | ERROR | SETTLE | 带 `Bound` 占地轮廓的 `EXCLUSIVE` 舱位什么也没有绑定（规则 R2） |

`0002`/`0003` 按*谁要求过多*划分；每对恰好触发一个。消息中两个声明者都是字面形式：
`<ownerA>:<bayA> (<sharingA>) and <ownerB>:<bayB> (<sharingB>) both claim cell (x, y, z)`。两个标签都在
争议单元格上——`PRIMARY` 是挑战者的请求，`SECONDARY` 是在位者的持有。

### 4.7 账本行为（Ledger behaviour）

| 情形 | 读取结果 | 结果 |
| --- | --- | --- |
| `read` → `available() == false` | 无法检查归属 | `Undecided(LEDGER_UNAVAILABLE)`，`SC-CLM-0004` |
| `read` → 可作答，占地轮廓内没有任何行 | 可读，此处无任何声明 | 正常结算 |
| `expectedRevision` ≠ 存储修订号 | 存储在本次尝试期间发生变化 | `RevisionMoved`；一次重读与重新检查，随后 `Undecided(LEDGER_MOVED)` 并携带重试令牌 |

不可用的账本**不是**空账本：把它读成空会以 fail-open 的方式授予排他权。`Undecided` 表示“对归属
没有结论”；`Failed` 则会断言结构是错的。

## 5. 参考代码（Reference sketch）

```java
// 1. One shared hatch cell, two machines: no conflict.
ClaimRow a = new ClaimRow(world, hatch, Sharing.SHARED, ownerA, atA, NamespacedId.of("mymod", "hatch"));
ClaimRow b = new ClaimRow(world, hatch, Sharing.SHARED, ownerB, atB, NamespacedId.of("mymod", "hatch"));
boolean coexists = !ClaimMatrix.conflicts(a, b);                       // true

// 2. An exclusive wall: the incumbent keeps the cell, the challenger is told.
ClaimRow incumbent  = new ClaimRow(world, cell, Sharing.EXCLUSIVE, ownerA, atA, NamespacedId.of("mymod", "wall_a"));
ClaimRow challenger = new ClaimRow(world, cell, Sharing.SHARED,    ownerB, atB, NamespacedId.of("mymod", "wall_b"));
if (ClaimMatrix.conflicts(challenger, incumbent)) {
    Finding finding = conflictFinding(challenger, incumbent, at);      // §4.6, a = challenger
}

static Finding conflictFinding(ClaimRow challenger, ClaimRow incumbent, Locus at) {
    return Finding.builder(FindingCode.of("SC-CLM-0002")).stage(Stage.SETTLE)
            .message(incumbent.owner() + ":" + incumbent.bay() + " (" + incumbent.sharing() + ") and "
                    + challenger.owner() + ":" + challenger.bay() + " (" + challenger.sharing()
                    + ") both claim cell " + challenger.cell())
            .label(at, LabelRole.PRIMARY, challenger.bay() + " requests " + challenger.sharing())
            .label(at, LabelRole.SECONDARY, incumbent.bay() + " already holds it as " + incumbent.sharing())
            .build();
}

// 3. The settle step derives the rows itself. The plan only says what the check found and at which
//    revision; nothing here can be handed in from outside.
ClaimPlan.Matched matched = (ClaimPlan.Matched) plan;
ClaimPlanner.Derived d = ClaimPlanner.derive(request.world(), request.blueprint(), request.siting(),
                                             request.owner(), matched.bindings());
ClaimView before = ledger.read(d.staticFootprint());
// The bindings are checked first (SC-CLM-0005): a bay of this blueprint, subjects of this capture,
// matching kinds, cells inside the bay — then the matrix, then one CAS.
ClaimVerdict verdict = ledger.settle(new SettleRequest(request.world(), owner, siting, d.rows(),
                                                       matched.ledgerRevision()));
if (verdict instanceof ClaimVerdict.RevisionMoved moved) {
    ClaimView fresh = ledger.read(d.staticFootprint());
    verdict = fresh.available()
            ? ledger.settle(new SettleRequest(request.world(), owner, siting, d.rows(), fresh.revision()))
            : new ClaimVerdict.RevisionMoved(moved.expected(), fresh.revision());
}
MatchResult result = switch (verdict) {
    case ClaimVerdict.Settled s        -> new Formed(Stage.SETTLE, instance, siting, outcome,
                                             new ClaimSet(s.rows(), s.revision()), …);
    case ClaimVerdict.ClaimConflict c  -> new Failed(Stage.SETTLE, c.findings());
    case ClaimVerdict.RevisionMoved m  -> new Undecided(Stage.SETTLE, UndecidedReason.LEDGER_MOVED,
                                             List.of(movedFinding(m.expected(), m.actual())),
                                             null, RetryToken.of(m.expected(), m.actual()));   // RetryToken.of(long expected, long actual)
};
```

## 6. 用例（Use cases）

**附加组件作者，一个舱口** 声明 `SHARED`：一个舱口上的两台熔炉以两条 `SHARED` 行结算，且没有
诊断项；`EXCLUSIVE` 会让第二台熔炉得到 `SC-CLM-0002`。**附加组件作者，一扇两格门** 写一个
`Bound` 舱位：两条行来自门的两个主体，因此两个单元格要么都拥有，要么都不拥有。
**附加组件作者，一个管道拐角** 写 `EVERY_CELL` + `Bound`，得到八个被断言的单元格与四个被拥有的
单元格，且不强制 `SHARED` 共享值。**宿主，一个建造棒** 在编辑前运行门面 `settle`，并在任何冲突上
阻断；**一次数据包重载** 运行 `DESIGN`，在没有世界、没有存储的情况下报告 `SC-BPT-0002` 与
`SC-CLM-0104`；**`/cumulus claims`** 打印 `read(cellBox).rows()`。

## 7. 证明（Proofs）

| 证明 | 论证 |
| --- | --- |
| P1 冲突对称性 | `ClaimMatrix.conflicts` 对不同世界的两行返回 false，而在一个世界内，它恰在任一共享值排他时为真；析取是对称的，因此 `conflicts(a, b) == conflicts(b, a)`，且矩阵在 `Sharing` 对上是完备的 |
| P2 无静默覆盖 | 账本的两个写入方法 `settle` 与 `release` 各自对完整的 `(world, owner, siting)` 键做一次 CAS；矩阵在共享值对上是完备的，因此不兼容的一对总会产出带行与诊断项的 `ClaimConflict`，请求中没有任何行被写入。另一所有者的行无法从该键触及 |
| P3 替换幂等性 | `ClaimPlanner.derive` 与 `overlaps` 是纯的，且 `settle` 替换该键的全部行：在同一安放位上重新结算会重现行集合并只移动修订号，请求中的任何行从不与它自己的在位行比较 |
| P4 无临时行 | `Settled` 之前不存在任何行；`Failed` 与 `Undecided` 让存储保持先前的修订号，因此读取方永远不会看到写入方已放弃的登记 |
| P5 确定性 | 行按 `(world, cell, owner, bay)` 排序，诊断项按 `(owner, bay)` 分组并按第一个争议单元格排序；两者都构成全序，与遍历顺序和哈希顺序无关。内部重叠是同一矩阵应用于 `bay` 不同的舱位 |
| P6 四种归属，一种解析 | 每个范围在任何比较之前就指名它的单元格：`Box` = `bay.cells`，`Declared` = 声明的集合，`Bound` = 绑定的非空气占地轮廓，`None` = ∅。在 `bay.cells ∪ footprint` 之外的读取在 `SETTLE` 是 `SC-BAY-0108`，因此没有舱位会在不拥有的地方读取而不产生诊断项 |
| P7 派生，绝不直接交出 | 一次结算写入的每一行都出自 `ClaimPlanner.derive`，作用于请求的 `(world, blueprint, siting, owner)` 与计划的绑定，且计划没有行字段；每个绑定都针对本蓝图与本次捕获检查（§4.5），因此调用方既不能凭空造出单元格，也不能指名另一次捕获的主体（I-16） |

## 8. 依赖与待决问题（Dependencies and open questions）

| 依赖 | 假设 |
| --- | --- |
| U1 `space` | `LatticeKey` 可与 `toPoint()` 比较；`CuboidSet.of/intersection/union`；`Siting.origin()/placement()` |
| U5 `blueprint` | `Blueprint.bays()` 按声明顺序；`Bay.id()/cells()/coverage()/footprint()/sharing()` |
| U3/U7 `expectations`、`engine` | `BayBinding.subjects()` 来自 `Outcome.Found`；匹配器交给门面一个 `ClaimPlan`，绝不是一次写入 |
| U9 `finding` | `SC-CLM` 字符串与 `(owner, bay)` 配对位于诊断包内 |

**待决问题**，限于 `22` §16 为本单元留下的内容。（1）**§16 Q1，空体积保护**：按蓝图的
`sealed: true` 标志是否优于 `SC-CLM-0104` 警告加逐舱位的范围选择？（2）**§16 Q4，`RevisionMoved`
重试**：§10.1 已经固定一次事务内重试；仍待决的是第二次变动应携带重试令牌，还是直接硬
`Undecided`。（3）**§16 Q6，`Coverage.ANY_SUBJECT`**：仅存在性舱位不断言任何单元格，那么它能在没有
漏洞的前提下成为 `EXCLUSIVE` 吗？

这不是 §16 的问题：`Undecided(LEDGER_MOVED)` 把它的重试令牌放在 `Undecided.retry` 上（`22`
§10.4），而 `Deferred` 是调用方想在稍后恢复、而不是现在重试时所取的分支。

## 9. 证伪（Falsification）

| 证伪项 | 被破坏的内容 |
| --- | --- |
| 只有一方看到的冲突 | 以两种顺序从两个摆放结算同一个争议单元格：`ClaimConflict` 行集合不同，或账本对不同的询问者就同一个长方体给出不同回答，都会破坏 P1/P5 |
| 失败尝试写入了行 | 让结算冲突、丢弃账本、并让修订号变动两次：`Failed`/`Undecided` 之后存储的行数与修订号必须不变，否则 P4 不成立 |
| `Bound` 舱位拥有它的整个长方体 | 管道拐角舱位（`EVERY_CELL` + `Bound`）与邻居的拐角冲突，或拥有八个而非四个单元格，说明 `Bound` 是从舱位长方体解析的 |
| 重新检查时与自身冲突 | 在同一安放位重新结算：任何冲突，或行集合不同于第一次结算，都会破坏 §4.1 的替换规则 |
| 不可用的账本被读成空 | 当 `available()` 为 false 时，结果必须是 `Undecided(LEDGER_UNAVAILABLE)`，绝不是 `Formed`，绝不是 `Failed` |
| 共享声明阻断排他声明 | 出现 `SHARED`/`SHARED` 冲突，或一个双 `SHARED` 蓝图在 `DESIGN` 失败，说明矩阵被读成了强度顺序；针对 `SHARED` 在位者的 `EXCLUSIVE` 挑战者必须是 `SC-CLM-0002`，而不是 `0003` |
