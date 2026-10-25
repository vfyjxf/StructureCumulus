# 09 — 蓝图与计划（U5）

> 何时阅读：声明一个蓝图或一个可复用片段、一个舱位、它的覆盖与声明策略、一个检查、一条接缝、
> 一个锚点角色、一个朝向策略或一个阶段计划时——或者要确认哪些非法输入会抛异常、哪些会变成
> 诊断项。包 `kernel.blueprint`、`@NullMarked`。服务于 R-04、R-06、R-08、
> R-09、R-11、R-13；涉及 I-3、I-4、I-5、I-6、I-10。

## 1. 范围（Scope）

| 拥有 | 不拥有 |
| --- | --- |
| `Bay`、`Requirement`、`Check`、`Coverage`、`Footprint`、`Seam`、`SeamId`、`SeamKind`、`Segment`、`SegmentKey` | `Focus`/`Expectation` 的判定（U3）；计算体（U8、U11） |
| `AnchorRoles`、`AnchorSpec`、`AnchorResolver`、`OrientationPolicy` | 朝向变换组与安放位（U1）；位姿搜索（U7） |
| `Blueprint`、`BlueprintBuilder`、`DerivationSpec` | `Sharing` 冲突、账本（U4） |
| `CheckPlan`、一个阶段的计划展开 | `ReadScope`（`base`）、`Computation` 与 `StatePhase`（`context`）；执行与折叠（U7）；诊断码（U9） |

蓝图不声明任何世界格、安放位、控制器实体或烘焙后的锚点偏移（R-04、I-5），不声明 `Deriver` 对象，
不声明 `Finding` 或诊断码（R-07），不声明预算来源、时钟或令牌（I-15）。一个检查就是一个
`Focus`/`Expectation` 对加上它所属的舱位；`run` 是内核对两者的自有组合，因此蓝图仍然不声明任何
检查实现。

## 2. 术语（Vocabulary）

| 名称 | 契约 |
| --- | --- |
| `Bay` record | 具名 `Cuboid` + `Coverage` + `Sharing` + `Footprint` + `Requirement` + 可选描述 |
| `Requirement` record | 一个舱位的 `Check` 与 `Computation`；阶段计划所展开的单元列表 |
| `Check` record | 计划单元：id、舱位、`Focus<T>`、`Expectation<T>`、描述 |
| `Coverage` enum | `EVERY_CELL`、`ONE_SUBJECT`、`ANY_SUBJECT`——一次 MATCH 就舱位断言了什么 |
| `Footprint` sealed interface | `Box`、`Bound`、`Declared`、`None`——一次 MATCH 拥有什么 |
| `Seam` record | 可连接的边：格子 + 种类 + 属性；从不被测试，从不声明 |
| `SeamId` record | `(NamespacedId)`，限于其蓝图本地；名义类型，绝不是舱位 id |
| `SeamKind` enum | `ITEM`、`FLUID`、`ENERGY`、`STRUCTURAL`、`CUSTOM` |
| `Segment` record | 键、边界、舱位、接缝、锚点；不含策略、计划或声明 |
| `SegmentKey` record | `(id, Arguments)`；身份包含实参 |
| `AnchorRoles` final class | `PRIMARY`/`FRONT`/`CENTER` 作为 `NamespacedId`；角色绝不是自成一体的类型 |
| `AnchorSpec` record | `(role, property, value)`；在定义原点读取，一个格子 |
| `AnchorResolver` interface | `resolve(blueprint, origin, view) → Outcome<Siting>`；一次属性读取，一次映射 |
| `OrientationPolicy` record | 允许的朝向变换、提供朝向的角色、直立承诺 |
| `Blueprint` final class | 编译后的模板；唯一构造路径（§4.6） |
| `BlueprintBuilder` final class | 流式、可变、非线程安全；在 `build()` 处一次性校验 |
| `DerivationSpec` record | `(deriver, Arguments, output, order)`；是一个引用，不是对象 |
| `CheckPlan` record | 一个阶段的展开结果：区段计算、聚合计算、派生、检查（§4.8） |

## 3. API 设计（API design）

```java
public record Bay(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                  Sharing sharing, Requirement requirement,
                  @Nullable String description) {           // empty cells refused
    public static Bay of(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                         Sharing sharing, @Nullable String description,
                         Function<Bay, Requirement> units);  // the check/bay knot, one pass
}
public record Requirement(List<Check<?>> checks, List<Computation<?>> computations) {}

public enum Coverage { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }
public sealed interface Footprint permits Box, Bound, Declared, None {
    record Box() implements Footprint {}                     // the bay cuboid, air included
    record Bound() implements Footprint {}                   // union of the non-air bound footprints
    record Declared(CuboidSet cells) implements Footprint {} // exactly these cells
    record None() implements Footprint {}                    // nothing
}

public record Check<T>(NamespacedId id, NamespacedId bay, Focus<T> focus,
                       Expectation<T> expectation, @Nullable String description) {
    public Check {
        if (!focus.subject().equals(expectation.subject()))
            throw new IllegalArgumentException("SC-CHK-0101: subject mismatch");
        Objects.requireNonNull(bay, "SC-CHK-0107: a check belongs to exactly one bay");
    }

    public ReadScope reads(Cuboid bayCells) { return focus.reads(bayCells).withKeys(expectation.meta().requires()); }

    public CheckResult run(CheckContext ctx) {
        Outcome<T> taken = focus.take(ctx);
        if (taken instanceof Outcome.Found<T> found) {
            return new CheckResult(expectation.test(ctx.with(found.value(), found.subjects())),
                    found.subjects(), found.notes());
        }
        Outcome.Missing<T> missing = (Outcome.Missing<T>) taken;
        return new CheckResult(Reply.unreadable(focus.describeMissing()), List.of(), missing.findings());
    }
}
public record SeamId(NamespacedId id) implements Comparable<SeamId> {}
public enum SeamKind { ITEM, FLUID, ENERGY, STRUCTURAL, CUSTOM }
public record Seam(SeamId id, Cuboid cells, SeamKind kind, Map<String, String> properties) {
    public static final String KIND = "kind";               // reserved key, CUSTOM only
    public boolean compatibleWith(Seam o) { return kind == o.kind   // the kernel's only such rule
            && Objects.equals(properties.get(KIND), o.properties.get(KIND)); }
}
public record SegmentKey(NamespacedId id, Arguments arguments) {}
public record Segment(SegmentKey key, Cuboid bounds, List<Bay> bays, List<Seam> seams,
                      List<AnchorSpec> anchors) {}          // checks B-1..B-4 locally; qualify(alias, local)
public final class AnchorRoles {                        // role ids, not a wrapper type
    public static final NamespacedId PRIMARY = NamespacedId.of("cumulus", "primary");
    public static final NamespacedId FRONT = NamespacedId.of("cumulus", "front");
    public static final NamespacedId CENTER = NamespacedId.of("cumulus", "center");
}
public record AnchorSpec(NamespacedId role, NamespacedId property, String value) {}   // cell = origin
public interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);   // §4.5
}
public record OrientationPolicy(NamespacedId primaryRole, List<Orientation> allowed,
                                boolean uprightOnly) {
    public static Builder builder(NamespacedId primaryRole);   // allow | allowTurns | uprightOnly
    public @Nullable Orientation select(Heading anchorHeading);              // §4.5, one equation
}
public final class Blueprint {                  // private ctor; builder() is the only construction path
    public NamespacedId id();  public Cuboid bounds();
    public List<Bay> bays();  public List<Seam> seams();          // declaration order
    public List<AnchorSpec> anchors();  public List<Overlap> overlaps();
    public OrientationPolicy orientation();
    public List<DerivationSpec> derivations();
    public Arguments arguments();  public @Nullable SourceSpan provenance();
    public @Nullable Bay bay(NamespacedId id);  public @Nullable Seam seam(SeamId id);
    public @Nullable AnchorSpec anchor(NamespacedId role);
    public CheckPlan expand(Stage s);                           // §4.8, pure
    public @Nullable SourceSpan sourceOf(NamespacedId bayOrSeam);   // per-element authoring range
    public CuboidSet bayCells();                                // cached union, excluded from equals
    public record Overlap(NamespacedId low, NamespacedId high) implements Comparable<Overlap> {}
}
public final class BlueprintBuilder {   // every member returns the builder
    public BlueprintBuilder bounds(Cuboid b);
    public BlueprintBuilder bay(NamespacedId id, Cuboid c, Coverage cov, Footprint footprint, Sharing sharing);
    public BlueprintBuilder bay(NamespacedId id, Cuboid c, Coverage cov, Footprint footprint,
                                Sharing sharing, @Nullable String d);
    public BlueprintBuilder bay(String path, Cuboid c, Coverage cov, Footprint footprint, Sharing sharing);
    public <T> BlueprintBuilder check(NamespacedId bay, NamespacedId id, Focus<T> focus,
                                      Expectation<T> expectation,
                                      @Nullable String description);   // appends to that bay's Requirement
    public BlueprintBuilder compute(NamespacedId bay, Computation<?> computation);
    public BlueprintBuilder seam(SeamId id, Cuboid c, SeamKind k);          // + properties overload
    public BlueprintBuilder anchor(AnchorSpec s);
    public BlueprintBuilder orientation(OrientationPolicy p);  public BlueprintBuilder derive(DerivationSpec d);
    public BlueprintBuilder overlap(NamespacedId a, NamespacedId b);        // §4.2
    public BlueprintBuilder place(Segment s, LatticePoint offset, NamespacedId alias);  // §4.4
    public BlueprintBuilder arguments(Arguments a);  public BlueprintBuilder provenance(SourceSpan p);
    public BlueprintBuilder source(NamespacedId bayOrSeam, SourceSpan p);   // per-element range
    public Blueprint build();
}
public record DerivationSpec(NamespacedId deriver, Arguments args, NamespacedId output,
                             int order) {}                 // order >= 0, unique
public record CheckPlan(Stage stage, List<Computation<?>> computations,
                        List<DerivationSpec> derivations, List<Check<?>> checks) {}   // §4.8
```

`allowed` 非空、无重复，且按 `Orientation.all()` 的顺序给出；`uprightOnly` 承诺每个被允许的 `o`
都把 `Heading.UP` 映射到 `UP`（在 build 时检查，不是过滤器）；`select` 返回 `null` 表示“没有可
接受的朝向变换”，绝不是恒等变换，也绝不是 `allowed.get(0)`。每个锚点角色只有一个规格：一个角色
命名定义原点必须呈现的那一个属性（§4.5），因此没有备选位点可排序，也没有优先级可打平。阶段计划
是蓝图的纯函数：`expand(S)` 每次重算、从不存储，因此相等的蓝图不可能对计划有分歧。
一个舱位的 `cells` 非空，它的声明遵守 §4.1 的 R1 与 R4，它的 `requirement` 非空
——一个不断言任何东西的舱位就是接缝（§4.3）。`Bay` 与 `Check` 互相引用；`Bay.of` 一趟就闭合
这个结——它把舱位值交给作者，供其挂上检查，而检查记住那个声明值。`bay` 按 `id` 比较与哈希，
`Requirement` 按各检查的 id 比较与哈希，因此没有相等链会递归（§7.1）。

## 4. 语义（Semantics）

### 4.1 为什么是 `Bay` 而不是 `Slot`，以及舱位断言了什么

`net.minecraft.world.inventory.Slot` 恰好是适配器最可能顺手导入的类型，而 `01-glossary.md` §O
禁用这个名字。除名字冲突之外，“slot” 是容器中的一个位置，而舱位是一个被**原子地**测试的区域
（门的情形，R-06，跨越多个格子）；“slot” 还暗示有东西被放进去，而舱位是被*断言*的对象——
一个检查只检视、什么都不存（I-4）。id 是限于其蓝图本地的 `NamespacedId`：两个蓝图里的 `mouth`
是两个舱位，而 `(world, owner, siting, bay)`（U4）在世界的层级上作出区分。

舱位陈述两件互相独立的事：一次 MATCH **断言**什么（`Coverage`）与**拥有**什么（`Sharing` +
`Footprint`）。两者互不蕴含。

| `Coverage` | 一次匹配必须建立什么 |
| --- | --- |
| `EVERY_CELL` | `bay.cells` 的每个格子都由至少一个检查判定 |
| `ONE_SUBJECT` | `bay.cells` 等于被绑定主体足迹的并集 |
| `ANY_SUBJECT` | 不断言任何格子；一条存在或状态判据 |

两个轴互相独立，因此只有真正的矛盾需要规则。四条，没有别的：

| 规则 | 时机 | 内容 | 码 |
| --- | --- | --- | --- |
| R1 | 构建 | `EXCLUSIVE` 的 `Footprint` 不能是 `None`：排斥别人却什么都不占，自相矛盾 | `SC-CHK-0106` |
| R2 | 结算 | `EXCLUSIVE` + `Bound` 必须解析出至少一格；没绑上的门不能以“空的独占声明”结算 | `SC-BAY-0111` |
| R3 | 构建 + 结算 | `ONE_SUBJECT` 要求检查都是主体类，且结算时绑定足迹等于 `bay.cells` | `SC-BAY-0107` |
| R4 | 构建 | `EVERY_CELL` 要求逐格检查覆盖 `bay.cells`；`ANY_SUBJECT` 不允许逐格检查 | `SC-CHK-0106` |

三种值得放在眼里的组合：

- `EVERY_CELL` + `Bound`：每个格子都被断言，只拥有非空气的那些——管道拐角。
- `EVERY_CELL` + `Box`：空的内部被保护——熔炉腔体，100 格空气。
- `ONE_SUBJECT` + `Bound`：门的情形，舱位*就是*那两半——R3 让解析出的绑定足迹等于 `bay.cells`，
  因此舱位拥有那两半，绝不拥有它们周围的空气。

非 `None` 舱位的 `reads()` 必须留在 `bay.cells` ∪ 它声明的足迹之内，否则在 SETTLE 时为
`SC-BAY-0108`；一个被绑定实体的足迹离开 `bay.cells`，在 SETTLE 时为 `SC-BAY-0109`；实体类型宽于
`MAX_ENTITY_SPAN`，在 DESIGN 时为 `SC-BAY-0110`。声明保护的是结构所占或所声明的格子，绝不是它
仅仅读取的格子（`22` §10.2）。

### 4.2 重叠（Overlap）

两个相交的舱位是 DESIGN 错误，除非两者都是 `SHARED` **并且**这一对已用 `overlap(a, b)` 声明。

| 一对舱位 | 结果 |
| --- | --- |
| 两者皆 `SHARED`、相交、已声明 | 合法；这些格子被检查并计费两次（R-08） |
| 相交、未声明——或已声明但其中一方不是 `SHARED` | DESIGN 错误：声明无法为一笔排他声明授权 |
| 不相交但已声明 | DESIGN 错误：无用的声明是笔误信号 |

三个舱位覆盖同一个格子时，三对都必须声明：一个未被声明的共享*格*是建模错误。

### 4.3 接缝不是舱位（A seam is not a bay）

舱位携带一个 `Requirement` 与它的各项声明，可以让一次匹配失败（`MISMATCH` 或 `UNREADABLE`），并
受 §4.2 约束。接缝两者都不携带，不产生声明——它的格子位于舱位格子之内（B-3），因此已经被
声明——只能取 `UNREADABLE`，并且豁免于 §4.2：接缝可以与舱位以及彼此自由重叠。

绑定有三个位点；只有第一个在编译路径上。**装配**（Assembly）：U6 的装配器对同一蓝图的两个具名
接缝调用 `compatibleWith`（种类相等、格子相邻 ⇒ 一条成对接缝，否则是一个 DESIGN 诊断项）。
**匹配**（Match）：持有该接缝的舱位的一个检查把接缝上的占用体记录为对应体——检查总是命名一个
舱位（`SC-CHK-0107`），因此接缝是通过它下方的舱位被判断的，不可读的对应体成为该检查的
`UNREADABLE`。**接合**（Join）：两个已成型结构——由宿主自己调用 `compatibleWith`；不涉及内核
状态，也不搬动声明。内核除 `compatibleWith` 之外不加任何规则，也绝不因为某个对应体“看起来
不对”而让匹配失败：判断一个占用体需要 `Expectation`，而接缝按设计就不带它。

### 4.4 实例化片段（Instantiating a segment）

`place(segment, offset, alias)` 是纯函数，并立即生效：`bay.cells` 与 `seam.cells` 按 `offset`
平移；`bay.id` 与 `seam.id` 通过
`Segment.qualify(alias, local) = NamespacedId.of(alias.namespace(), alias.path() + "/" + local.path())`
重新归属。舱位的检查随之迁移：每个检查都被重新指向放置后的舱位——它的 `bay` 字段与它自己的 id
都来自这次放置，因为检查 id 必须在整个蓝图内保持唯一（B-2），而一个保留片段原舱位的检查会读到
未平移的格子。**锚点规格既不参与平移，也不参与限定**：规格命名的是定义原点的一个属性（§4.5），
而放置并不移动那个原点；并且角色在整个蓝图内唯一（B-4）——一个片段的两次放置若都声明 `CENTER`
会被拒绝，因为原点只有一个格子，而提供朝向的角色只有一个。`segment.bounds` 不会被加入蓝图的
边界：已声明的边界必须已经容纳每个放置后的格子（B-8），因为扩大边界会在作者脚下挪动足迹。
`Focus` 与 `Expectation` 是共享而非复制的（不可变、可重入，U3），因此同一个片段值可以被放置
多次；一次放置的溯源信息就是它的别名。

`qualify` 是单射，因为别名路径不得包含 `/`（`place` 会检查）：第一个 `/` 在唯一的位置上切分。
局部命名空间被有意丢弃——舱位 id 是*它所属*蓝图的局部量，`mymod:shell` 的两次放置不得都产出
`mymod:floor`。

### 4.5 锚点解析（Anchor resolution）

```java
public interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
}
```

1. 锚点格子就是 `origin`，即**定义原点**，由调用方给出——一条命令、一个 watch，或一次文档放置。
   蓝图的任何成员都不命名世界格（B-12、I-5），因此这次读取不可能依赖尚未选定的朝向：循环性是被
   消除的，不是被绕开的。
2. 恰好读取一个格子，`view.occupant(LatticeKey.of(origin))`。格子缺席是 `Missing` 并带
   `SC-FOC-0101`，段被拒绝是 `SC-FOC-0301`；两者都不是回退朝向。
3. 在该占用体上把 `spec.property()` 与 `spec.value()` 比对。不一致就是角色无法解析：`Missing`，
   由 U7 报告（不一致是 `Failed`）；绝不默认 `Heading.NORTH`。
4. `anchorHeading` 是该属性产出的 `Heading`。候选朝向变换 `o` 可接受，当且仅当
   `o.apply(authoredFront) == anchorHeading`；不满足它的候选就不是候选。作者声明的正面是局部
   `Heading.NORTH`——一个常量，因此不存在会漂移的 front 字段。
5. `OrientationPolicy.select(anchorHeading)` 按 `Orientation.all()` 的顺序扫描 `allowed`，返回
   第一个可接受的候选，一个都没有时返回 `null`。它是对一个等式的过滤，绝不是朝向变换之间的偏好：
   不满足该等式的朝向变换不可能靠声明顺序取胜（§7.5）。
6. `null` ⇒ 不安放。U7 会报告它（不一致时为 `Failed`，角色无法解析时按它的表为 `Failed` 或
   `Undecided`）；本单元只保证缺席仍是缺席（I-5）。
7. `uprightOnly` 在匹配时不剔除任何东西——它已在 build 时针对 `allowed` 检查过。

锚点读取计费一个格子与一个段。`AnchorResolver` 不持有世界状态：视图是实参，因此蓝图值不包含
`WorldView` 字段。

### 4.6 蓝图不变量（Blueprint invariants）

| Id | 不变量 | 由谁强制 |
| --- | --- | --- |
| B-1 | `bounds` 非空；每个舱位非空且位于其中 | `build()` |
| B-2 | 舱位 id、接缝 id 与检查 id 唯一 | `build()` |
| B-3 | 接缝格子位于 `⋃ bay.cells` 之内 | `build()` |
| B-4 | 锚点角色唯一；策略的 `primaryRole` 有规格 | `build()` |
| B-5 | 每个检查命名的舱位都在蓝图里声明了（`SC-CHK-0107`） | `expand` |
| B-6 | 派生的 `output` 键唯一；`order` 唯一且非负 | `build()` |
| B-7 | §4.2 双向成立（`SC-BPT-0002`） | `build()` |
| B-8 | 别名不含 `/`（`place`）；放置后的格子位于 `bounds` 内（`build`） | `place`、`build` |
| B-9 | 声明遵守 R1 与 R4（`SC-CHK-0106`），且 `ONE_SUBJECT` 舱位的检查都是主体类（R3 的构建那一半，`SC-BAY-0107`） | `build()` |
| B-10 | `EVERY_CELL` 舱位的静态判定格子覆盖 `bay.cells`（`SC-CHK-0106`） | `build()` |
| B-11 | 检查绝不读取更晚阶段才产出的 `ContextKey`（`SC-CHK-0109`） | `expand` |
| B-12 | 没有任何成员是世界格、安放位、实体、诊断项、时钟或令牌 | 类型本身 |

`Blueprint` 是带**私有**构造器的 `final class`，不是 record：record 的规范构造器不能比该 record
更严格，因此手写的 `new Blueprint(...)` 会成为第二条未校验的构造路径（§7.1）。该值不可变——私有
final 字段、集合只复制一次、计划由 `expand(S)` 重算、没有 setter、没有 `with`；唯一可达
的可变对象是 `Focus` 与 `Expectation`，按 U3 契约共享且可重入。`bayCells()` 与 `provenance` 被
排除在 `equals` 之外（派生量；来源，而非内容）。

### 4.7 什么抛异常、什么变成诊断项（What throws, what becomes a finding）

`build()` 恰对 §4.6 中由 `build()` 执行的行（B-1…B-4、B-6…B-10）以及 `allowed` 策略前置
条件（非空、无重复）抛出 `IllegalArgumentException`，别无其他；B-5 与 B-11 由 `expand(S)` 检查，
那是检查第一次遇到自己的舱位的时刻，而 B-12 由类型本身保持。`build()` 的调用者是代码：没有文档
位置可以挂一个 `Label`，而一个被静默修复的蓝图会在后续每个诊断项脚下挪动足迹。因此 `build()` 是
**证明义务，不是校验器**——它要么产出一个满足 §4.6 的值，要么抛出，并带上行 id 与违规的 id（`"B-2: check
mymod:mouth_bars is declared twice"`）。

文档数据永远不会到达失败的 `build()`：U6 的 `DocumentValidator` 先跑同一张表，并报告带 `Locus`
与 `SourceSpan` 的 `Finding`（R-07，`10-documents.md`）。§4.6 是一份**共享契约**——一行、两种
呈现，而行 id 就是审阅者对两者做匹配的方式。

**允许规范化，不允许修复。** 复制集合、排序 `allowed`、规范化 `Overlap` 的顺序、致密化一个阶段
内的派生顺序都是规范化；钳制一个 `Cuboid`、丢弃空舱位、补上缺失的主锚点、或重排一个阶段计划，
都会被拒绝。**`String` id 只是语法糖**：`bay("mouth", …)` 即
`bay(NamespacedId.of(id.namespace(), "mouth"), …)`——位于蓝图命名空间内的一个路径，也是
`04-api-doctrine.md` §3.3 允许的唯一 `String` 通道。

### 4.8 计划与展开（Plans and expansion）

一个阶段的计划不是步骤列表。计划是对蓝图就某一个阶段声明的各单元的**展开**，而 `CheckPlan` 就是
这个展开——一个值，不是一份配置。阶段由引擎选定，顺序由引擎算出；作者既不声明阶段，也不声明
单元顺序。

```java
public record CheckPlan(Stage stage, List<Computation<?>> computations,
                        List<DerivationSpec> derivations, List<Check<?>> checks) {}
```

对阶段 `S`，`Blueprint.expand(S)`（纯函数）按以下顺序产出：

1. `S` 的每个 `phase()` 为 `StatePhase.SECTION` 的 `Computation`；
2. 屏障；
3. `S` 的每个 `phase()` 为 `StatePhase.AGGREGATE` 的 `Computation`，以及分配给 `S` 的每个
   `DerivationSpec`，按 `inputs()`/`requires()` 上的 Kahn 序——成环是 `SC-CHK-0102`；
4. `S` 的每个 `Check`，排在被它读取的计算之后；
5. 1、3、4 内部的打平：声明顺序——舱位按声明顺序，然后是各自的 `Requirement` 顺序；
   `specificity` 只是诊断项排序的提示，不是计划键。

一个 `SECTION` 单元通过 `ContextDraft.contribute` 贡献，落在 `merge` 非空的键上；一个 `AGGREGATE`
单元用 `put` 放入单个值，读取屏障折叠出的结果。检查绝不在屏障之前运行；不存在逐段的检查阶段，
因此一个区块边界无法改变判定。`pinned` 不是旋钮：结构性顺序（1 → 2 → 3 → 4）与 Kahn 约束已经
固定了计划，打平内的声明顺序是仅剩的自由度。预算只会收紧——每一跳都取 `min`——因此没有上限的
计划继承请求。

检查彼此独立，因此不存在 `FIRST`/`AGGREGATE` 旋钮：U7 折叠该次尝试的各条应答
`UNREADABLE > MISMATCH > ABSTAIN > MATCH`。计划中的任何东西都不会释放声明（I-10）。

`DerivationSpec` 是一个**引用**：派生器 id、`Arguments`、`output` 键 id 与它的 `order`——蓝图里
不存在 `Deriver` 对象，这个破环让 `blueprint` 不依赖 `context`
（`02-architecture.md` §2）。U8 编译计划时该 id 通过 `Catalogs.derivers` 解析：`Deriver` 的
`output()` 就是规格命名的那个 `ContextKey`，缺失的 id 在那里是一个诊断项。`order` 唯一且非负，
U8 在计划内按 `(order, id)` 排序，因此计划不是源码顺序的偶然产物。同一个 `output`
键的两个规格被拒绝（B-6）：一个键只有一个产出者。
`Sharing` 与 `Footprint` 是舱位上的值，不是阶梯：`Sharing` 命名的是两种语义，不是两个
等级，因此一个格子上同时有 `SHARED` 与 `EXCLUSIVE` 舱位是 U4 的冲突规则，最终是账本的规则。

## 5. 参考代码（Reference sketch）

```java
static NamespacedId id(String path) { return NamespacedId.of("mymod", path); }
static NamespacedId mc(String path) { return NamespacedId.of("minecraft", path); }

// Huge furnace: shell, door bay, owned cavity; a seam; an anchor; a section computation; a derivation.
Blueprint furnace = Blueprint.builder(id("huge_furnace"))
        .bounds(Cuboid.of(0, 0, 0, 7, 6, 7))
        .bay(id("wall"), Cuboid.of(0, 0, 6, 7, 6, 7), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Bound())
        .bay(id("mouth"), Cuboid.of(3, 1, 0, 4, 3, 1), Coverage.ONE_SUBJECT,
                Sharing.EXCLUSIVE, new Footprint.Bound(), "the opening the player sees")
        .bay(id("interior"), Cuboid.of(1, 1, 1, 6, 5, 6), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Box())          // the empty volume is protected
        .check(id("wall"), id("wall_bricks"), Foci.block(Cuboid.of(0, 0, 6, 7, 6, 7)),
                Expectations.block(mc("bricks")), null)
        .check(id("mouth"), id("mouth_door"), Foci.door(LatticePoint.of(3, 1, 0)),
                Expectations.door(mc("iron_door")), null)             // ONE_SUBJECT: two cells, one claim
        .check(id("interior"), id("interior_air"), Foci.allCells(), Expectations.air(),
                null)
        .seam(new SeamId(id("mouth_item")), Cuboid.of(3, 1, 0, 4, 2, 1), SeamKind.ITEM)
        .anchor(new AnchorSpec(AnchorRoles.FRONT, mc("facing"), "east"))  // read at the definition origin
        .orientation(OrientationPolicy.builder(AnchorRoles.FRONT)
                .allowTurns(Turn.NONE, Turn.CLOCKWISE, Turn.HALF, Turn.COUNTERCLOCKWISE)
                .uprightOnly().build())
        .compute(id("interior"), Computations.volume(id("interior_volume")))
        .derive(new DerivationSpec(id("capacity"), Arguments.empty(), id("capacity"), 0))
        .build();   // bayCells() 42 + 2 + 100 = 144; expand(FORM) = interior_volume, barrier,
                    // then wall (42), mouth (2), interior (100) — declaration order
```

管道拐角是一个舱位，而不是用 `allOf` 绕开：八个格子被断言，四个被拥有。

```java
var corner = Blueprint.builder(id("pipe_corner")).bounds(Cuboid.of(0, 0, 0, 2, 2, 2))
        .bay(id("corner"), Cuboid.of(0, 0, 0, 2, 2, 2), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Bound());
for (LatticePoint p : List.of(LatticePoint.of(0, 0, 0), LatticePoint.of(1, 0, 0),
                              LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 1)))
    corner.check(id("corner"), id("pipe_" + p.x() + p.y() + p.z()),
            Foci.block(Cuboid.of(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1)),
            Expectations.block(mc("pipe")), 1, null);
for (LatticePoint p : List.of(LatticePoint.of(0, 1, 0), LatticePoint.of(1, 1, 0),
                              LatticePoint.of(0, 1, 1), LatticePoint.of(1, 1, 1)))
    corner.check(id("corner"), id("clear_" + p.x() + p.y() + p.z()),
            Foci.block(Cuboid.of(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1)),
            Expectations.air(), 1, null);
Blueprint pipeCorner = corner.build();
// The corner claims its four pipe cells; a neighbour's corner claims its own four, and the four
// must-be-air cells are asserted by both and owned by neither.
```

一个参数化外壳片段（`HEIGHT` 是一个 `ParameterKey<Integer>`），放置两次：

```java
Segment shell = new Segment(
        new SegmentKey(id("shell"), Arguments.of(Keys.HEIGHT, 3)),
        Cuboid.of(0, 0, 0, 5, 3, 5),
        List.of(Bay.of(id("floor"), Cuboid.of(0, 0, 0, 5, 1, 5), Coverage.EVERY_CELL,
                        Sharing.SHARED, new Footprint.Bound(), null,
                        b -> new Requirement(List.of(new Check<>(id("floor_bricks"), b,
                                Foci.block(Cuboid.of(0, 0, 0, 5, 1, 5)),
                                Expectations.block(mc("bricks")), 25, null)), List.of())),
                Bay.of(id("hollow"), Cuboid.of(1, 1, 1, 4, 3, 4), Coverage.EVERY_CELL,
                        Sharing.SHARED, new Footprint.Bound(), null,
                        b -> new Requirement(List.of(new Check<>(id("hollow_air"), b,
                                Foci.allCells(), Expectations.air(), 36, null)), List.of()))),
        List.of(new Seam(new SeamId(id("hatch")), Cuboid.of(2, 1, 1, 3, 2, 2), SeamKind.ITEM,
                Map.of())),
        List.of(new AnchorSpec(AnchorRoles.CENTER, mc("facing"), "north")));

Blueprint kiln = Blueprint.builder(id("kiln"))
        .bounds(Cuboid.of(0, 0, 0, 11, 3, 5))                       // holds both placements
        .place(shell, LatticePoint.of(0, 0, 0), id("south"))
        .place(shell, LatticePoint.of(6, 0, 0), id("north"))
        .orientation(OrientationPolicy.builder(AnchorRoles.CENTER).allowTurns(Turn.NONE).build())
        .build();     // bays mymod:south/floor, mymod:south/hollow, mymod:north/floor, …
```

两个 `EXCLUSIVE` 舱位覆盖同一个格子会被 `build()` 拒绝：

```java
assertThrows(IllegalArgumentException.class, () -> Blueprint.builder(id("gate"))
        .bounds(Cuboid.of(0, 0, 0, 4, 4, 4))
        .bay(id("door"),   Cuboid.of(0, 0, 0, 4, 2, 1), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Box())
        .bay(id("lintel"), Cuboid.of(0, 1, 0, 4, 3, 1), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Box())
        .check(id("door"), id("door_planks"), Foci.block(Cuboid.of(0, 0, 0, 4, 2, 1)),
                Expectations.block(mc("oak_planks")), 8, null)
        .check(id("lintel"), id("lintel_planks"),
                Foci.block(Cuboid.of(0, 1, 0, 4, 3, 1)), Expectations.block(mc("oak_planks")), 8, null)
        .build());    // B-7: bays lintel and door overlap at x[0,4) y[1,2) z[0,1)
```

## 6. 用例（Use cases）

**插件作者。** 一扇门：一个舱位覆盖那个两格的开口，一个检查把 `door` 焦点与门期望配对（R-06），
`Coverage.ONE_SUBJECT` 配 `Footprint.Bound`，使这两个格子既被断言又被拥有，并且是 `EXCLUSIVE`，
使第二扇门无法声明它们。一段管道：两个面上的 `FLUID` 接缝，没有舱位，没有声明——隔壁的机器
声明一条匹配的接缝，U6 在装配时把它们配对。插件写的是 `SeamKind`，或在需要判别符时写一个
`CUSTOM` 种类，绝不是兼容性谓词。

**宿主。** 数据包重载时通过 `BlueprintBuilder` 编译文档；`/cumulus validate` 在发生任何匹配之前
报告 §4.7 的 DESIGN 诊断项。构建魔杖读取 `bayCells()` 取足迹；它从不读 `expand(...)`，那是引擎
输入。

## 7. 证明（Proofs）

**7.1 不可变性。** 没有公开构造器、没有 setter、没有 `with`；字段为 `private final`；集合只复制
一次；`expand(S)` 每次调用重算计划而不缓存（`Map.copyOf` 会证伪确定性主张，因此根本不存任何
以 `Stage` 为键的映射）。唯一可达的可变状态是一个 `Focus` 或一个 `Expectation`，按契约共享
且可重入，因此没有一次匹配会观察到半更新的模板（I-4）。*搜寻过的反例：* `bayCells()` 是缓存
——从 `equals` 中排除、从不返回可变对象，且 `CuboidSet` 按 U1 不可变；而一个 `Bay` 若包含一个
命名该 `Bay` 的 `Check`，朴素的 record `equals` 就会递归，因此 `Check` 比较并哈希 `bay.id()`，
`Requirement` 比较各检查的 id。

**7.2 构建器校验的完备性。** 主张：`build()` 抛出，当且仅当 §4.6 中由 `build()` 执行的行或
`allowed` 前置条件失败；`expand(S)` 抛出，当且仅当 B-5 或 B-11 失败，或计算 DAG 成环
（`SC-CHK-0102`）。（⇒）每行恰有一个检查，针对完成后的状态运行、只读取已构建的值，因此调用顺序
无法掩盖违规，也没有检查会遮蔽另一个。
（⇐）`build()` 中的每个 `throw` 都携带它的行，而每行都在
§4.6 中；不带行的抛出是审阅缺陷。幂等：`build()` 复制而不消耗构建器，因此两次构建相等。

**7.3 无包环。** 排名 `base` 0、`space` 1、`claim` 3、`expectation` 4、`blueprint`
5。`blueprint` 消费其余四个。不存在 `plan` 包：`CheckPlan` 是 `blueprint` 的值，`ReadScope` 在
`base`，`Computation`/`StatePhase` 在 `context`。要紧的那条边成立：**`base` 不命名 `Bay`、不命名
`Blueprint`、也不命名 `Check`。** 检查必须命名它的舱位（`SC-CHK-0107`），因此 `Check`、
`Requirement` 与 `Bay` 位于 `blueprint`，绝不会沉到它下面。锚点边同样不闭合：`AnchorResolver` 在
`blueprint` 中命名 `Blueprint`，并从 `space` 取 `WorldView`，绝不反向。因此 `blueprint ⇄ base` 与
`blueprint ⇄ context` 都无法形成（`DerivationSpec` 持有一个 id、`Arguments`、一个键 id 与一个
`int`，绝不持有 `Deriver`）。*溯源边是闭合的：* `Blueprint` 与 `BlueprintBuilder` 使用
`base` 的 `SourceSpan` 值，而不是 U6 的 `DocumentProvenance`，因此 `blueprint` 从不导入
`document`。

**7.4 展开全序。** 在一个阶段内，§4.8 的第 1 步与第 2 步是固定的；第 3 步是 `inputs()` 上的
Kahn 序，按 `SC-CHK-0102` 是一个 DAG；第 4 步排在它所读取的每个计算之后。每组内的打平键是
声明下标——按位置唯一——因此每组都是严格全序，稳定性让实现与该键一致。该键只读一个位置，
无哈希、无标识，因此相等的蓝图在每个 JVM 中展开一致。

**7.5 朝向变换的确定性。** `select` 在一个无重复列表中按全序规范顺序遍历，返回第一个满足
`o.apply(authoredFront) == anchorHeading` 的朝向变换：每次调用至多一个结果，与哈希种子或线程
无关。无候选的情形返回 `null`，没有恒等分支，因此“无可用位姿”不会退化为“原点处的位姿”（I-5）。

## 8. 依赖与待决问题（Dependencies and open questions）

使用 `base`（`NamespacedId`、`Arguments`、`ParameterKey`、`Stage`、`Budget`）、`space`（`Cuboid`、
`CuboidSet`、`LatticePoint`、`Orientation`、`Heading`、`Siting`、`WorldView`；`Turn`/`Tilt`/`Flip`
仅用于 `allowTurns`）、`base`（`ReadScope`）、`context`（`Computation`、`StatePhase`）、`occupant`（`EntityWindow`）、
`expectation`（`Focus`、`Expectation`、`Outcome`、`Reply`、`CheckContext`）、`claim`（`Sharing`）。
`LatticePoint` 与各偏转类型超出任务书的参考清单，因为一个偏移量与偏转语法糖没有别的类型可用。
`AnchorResolver` 是唯一读取 `WorldView` 的成员；视图是实参，因此该值保持无宿主（I-5）。

**假定，待确认：** U0——`Arguments` 具有与顺序无关的值相等（否则 `SegmentKey` 与 `DerivationSpec`
的身份就是错的）、`Arguments.of(ParameterKey<T>, T)`、`Budget.min`。U1——`Cuboid.isEmpty`、
`Cuboid.intersects`、`Cuboid.translate(int,int,int)`、`LatticePoint.x()/y()/z()`、
`Orientation.of(Turn,Tilt,Flip)`、`Orientation.all()` 的顺序。U3——`Foci` 与 `Expectations` 持有者、
`Focus.reads()`/`reads().union`、`ExpectationMeta.specificity()`。U8——`Computations` 持有者、
带 `StatePhase` 的一个 `Computation` 及其 `compute(...)` 上下文、`ContextKey`。U9 拥有每个诊断码；
本文件只命名诊断*位点*（`SC-BPT-…`）。

- **Q5-1（已解决）** `Blueprint` 存储一个 `@Nullable SourceSpan`（来自 `base`）并暴露
  `sourceOf(...)`；`DocumentProvenance` 留在 U6 本地。没有 `blueprint → document` 边。
- **Q5-2（已解决）** `Blueprint.overlaps()` 有术语表行；声明放在
  蓝图上，而不是放在某个声明规格上。
- **Q5-3** 空体积保护（`22` §16 Q1）：熔炉腔体声明 `Box`，其残留是一条 `SC-CLM-0104` WARNING
  ——这足够，还是蓝图需要一个 `sealed: true` 标志？
- **Q5-4** 一个 `ANY_SUBJECT` 舱位不断言任何格子（`22` §16 Q6）：它能否在不拥有任何东西的情况下
  是 `EXCLUSIVE`？R1–R4 不回答这一点：R1 只禁止 `None` 占地轮廓。
- **Q5-5** 实体窗口（`22` §16 Q2）：舱位的 `EntityWindow` 是声明盒按 `MAX_ENTITY_SPAN` 膨胀后的
  结果；对许多窗口做逐 tick 差分是宿主的成本，而本文件不限定一个蓝图可以声明多少个窗口。

## 9. 证伪（Falsification）

| # | 情形 | 预期 | 若不然则破坏 |
| --- | --- | --- | --- |
| F-1 | `EXCLUSIVE` 配 `None` 占地轮廓（R1） | `build()` 抛出 B-9，指名 `SC-CHK-0106`；文档产出一个 DESIGN `ERROR` | 排斥别人却什么都不占的声明自相矛盾（R1） |
| F-2 | `new Check<>(…, null /* bay */, …)`，或一个没有任何单元的舱位 | `IllegalArgumentException` 指名 `SC-CHK-0107`；文档产出对应的 DESIGN 诊断项 | 结构级检查没有舱位来拥有它的声明 |
| F-3 | `OrientationPolicy.builder(FRONT).build()` 且没有 `FRONT` 规格，或重复的 `allowed` | `build()` 抛出 B-4；空绝不表示“任意朝向变换” | 朝向没有声明的来源，R-05 落空 |
| F-4 | 两个 `EXCLUSIVE` 舱位相交（§5 的 `gate`） | `build()` 抛出 B-7，指名双方与交集 | 设计期本可报告的冲突流入 U4（I-10） |
| F-5 | 一个位于所有舱位之外的接缝；`place` 的别名含 `/` | `build()` 抛出 B-3；`place` 抛出 B-8 | 接缝描述未检查的格子；`qualify` 不再是单射 |
| F-6 | 一个蓝图构建两次；同一规范文档构建两个 | 值相等，`expand(...)` 顺序也相等 | §7.4 为假；`MatchResult` 不可复现 |
| F-7 | 一个 `EVERY_CELL` 舱位，其检查留下一个格子未判定；`EVERY_CELL` 舱位中的一个实体焦点 | `build()` 抛出 B-10 与 B-9，指名 `SC-CHK-0106` | “每个格子都被判定”会成为一句未检查的承诺（R-06） |
| F-8 | 一个 `FORM` 检查读取 `SETTLE` 才产出的键 | `expand(FORM)` 抛出 B-11 并指名 `SC-CHK-0109` | 检查会读取更晚阶段的值，永远看不到它 |
| F-9 | 同一阶段内两个计算产出同一个键 | `build()` 拒绝它（B-6），绝不静默保留一个 | 计划顺序会取决于声明上的偶然 |
| F-10 | 一个片段放置两次，然后 `expand(FORM)` | 每个检查读取自己那次放置的格子；id 各不相同 | 检查会判断未平移的格子（§4.4） |

审阅问题：§3 中是否有成员能让调用者到达 §4.6 未描述的状态，或 §4.6 的某一行既缺抛出位点又缺
U6 诊断项？任一为“是”则本模块失败。
