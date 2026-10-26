# 12 — 上下文（Context，U8）

> 状态：设计草案 v0.3，2026-10-20。包 `dev.vfyjxf.cumulus.kernel.context`。服务于 R-03、
> R-13；强制 I-7、I-9、I-14。与 `22` 修订 5 §6.4 与 §8 对齐：一个 `ContextKey`，每实例一个
> 存储，按层打戳的帧，贡献在屏障处折叠。

## 1. 范围（Scope）

包含：键类型、贡献/折叠模型、草稿与视图、层与保留策略、`RuntimeState` 与增益折叠，以及随附的
键。不包含：计划单元及其执行（U7）、检查（U3）、占用体（U2）、长方体（U1）、诊断项的文本
（U9）、目录注册（U11）、持久化（A1）。中间状态不是独立机制：一次计算通过同一个草稿与存储，
写入 `StructureContext` 读取的同一批 `ContextKey`。

## 2. 术语（Vocabulary）

| 术语 | 契约 |
| --- | --- |
| `ContextKey<T>` | `(id, Class<T>, Retention, @Nullable Merge<T>, @Nullable T whenAbsent)`；唯一的键类型 |
| `Merge<T>` | `combine(T left, T right)`；非空表示该键由区段贡献（section-contributed） |
| `Retention` | `ATTEMPT` 丢弃，`OUTCOME` 进入 `MatchOutcome.context`，`CONTEXT` 发布进一个帧 |
| `ContextTier` | `BLUEPRINT`、`PROBE`、`FORM`、`SETTLE`：帧的阶段投影 |
| `ContextFrame` | `(tier, revision, Map<ContextKey<?>, Object>)`，一次发布 |
| `StructureContext` | `(instance, revision, frames)`；不可变，读取按层打戳 |
| `ContextDraft` | 一个阶段的写入：`put`（单写者）、`forSection` + `contribute`（区段部分值）、`refuse`/`declined`（已决定的缺席） |
| `ContextView` | `get`（可空）/ `require`（`Outcome`）/ `at(tier)` |
| `ContextStore` | 每实例一个：`context(instance)`、`draft(tier, baseRevision)`、`publish(draft)` |
| `RuntimeState` | `(context, augments, contextRevision)` 外加折叠诊断项；唯一被持久化的形态 |
| `Augment` / `AugmentKey` / `AugmentFold` | `(key, finite amount)`；`(id, fold)`；`SUM, MAX, MIN, MULTIPLY, SET` |

`StateKey`、`PartialKey`、`Key`、`Reducer`、`ContextValue` 与 `ContextLifetime` 已移除：它们把一个
上下文拆成三种键类型和第二次折叠。`Deriver` 保留，作为仅面向上下文的生产者。

## 3. API 设计（API design）

```java
public record ContextKey<T>(NamespacedId id, Class<T> type,
                            Retention retention,
                            @Nullable Merge<T> merge,      // non-null: contributions allowed
                            @Nullable T whenAbsent) {}

public interface Merge<T> { T combine(T left, T right); }

public enum Retention { ATTEMPT, OUTCOME, CONTEXT }
public enum ContextTier { BLUEPRINT, PROBE, FORM, SETTLE }

public record ContextFrame(ContextTier tier, long revision, Map<ContextKey<?>, Object> values) {}

public interface ContextView {
    ContextTier tier();
    <T> @Nullable T get(ContextKey<T> key);        // stamped at or below tier(), never above
    <T> Outcome<T> require(ContextKey<T> key);     // Missing with a finding, never a bare null
    boolean has(ContextKey<?> key);
    Set<ContextKey<?>> keys();                     // ascending (id, type name)
    ContextView at(ContextTier tier);              // narrow to `tier`, same frames; a wider tier clamps
}

public record StructureContext(InstanceId instance, long revision, List<ContextFrame> frames)
        implements ContextView {
    public ContextTier tier() { return ContextTier.SETTLE; }
    public ContextView at(ContextTier tier);       // the same frames, read at `tier`
    public static StructureContext empty(InstanceId instance);
}

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

public interface ContextStore {
    StructureContext context(InstanceId instance);  // frames only, never a lease
    ContextDraft draft(ContextTier tier, long baseRevision);
    Outcome<Void> publish(ContextDraft draft);      // Missing(SC-CTX-0108) when base is stale
}

public enum AugmentFold { SUM, MAX, MIN, MULTIPLY, SET }
public record AugmentKey(NamespacedId id, AugmentFold fold) { static AugmentKey of(NamespacedId, AugmentFold); }
public record Augment(AugmentKey key, double amount) {
    public Augment { if (!Double.isFinite(amount)) throw new IllegalArgumentException("amount"); }
}

public final class RuntimeState {
    public static RuntimeState of(StructureContext context, List<Augment> augments, long contextRevision);
    public StructureContext context();  Map<AugmentKey, Double> augments();
    public long contextRevision();      List<Finding> findings();
    public <T> RuntimeState upgrade(ContextKey<T> key, T value);    // the host's only write; §4.5
}
```

| 契约 | 裁定 |
| --- | --- |
| 一种键 | 到达帧、检查或 `RuntimeState` 的值都是 `ContextKey`；不存在第二种键类型 |
| 动词与键匹配 | 对 `merge != null` 的键调用 `put` 是 `SC-CTX-0109`；对 `merge == null` 的键调用 `contribute` 同样如此 |
| 声明归目录所有 | 声明用 `ContextKeys.of(...)` 构建，用 `ContextKeys.register(draft, key)` 绑定；同一 id 的两次注册是 `SC-CTX-0102`；相等性是 `(id, type)` |
| 不交出裸 `Map` | `ContextDraft` 与 `ContextView` 是唯一句柄；帧的映射从不交出 |
| 按层打戳 | 在层 `t` 的读者看见的是在 `t` 或以下打戳的值；在 `t+1` 的发布绝不重写 `t` 的帧 |
| 缺席会被发布 | 帧为其草稿决定的每个键携带一个条目；已决定但无值的键记录为缺席标记，因此该帧遮蔽更旧的值，而不是让读者回退到它 |

## 4. 语义（Semantics）

### 4.1 阶段、层与保留策略（Stages, tiers, retention）

```
Stage      DESIGN   PROBE   FORM   SETTLE
Tier       BLUEPRINT  PROBE  FORM  SETTLE
Projection -          -      -     RUNTIME
```

| `Retention` | 落入 | 读取者 |
| --- | --- | --- |
| `ATTEMPT` | 仅该次尝试的视图；随后丢弃 | 同一次尝试的单元 |
| `OUTCOME` | `MatchOutcome.context` | 持有该产物的聚合、检查与补救方案 |
| `CONTEXT` | 阶段层的帧，随后是持久化的 `SETTLE` 帧 | `StructureContext`，以及通过 `ContextView` 读取的补救方案 |

- `RUNTIME` 没有生产者：它是 `SETTLE` 加上宿主升级（§4.5）的持久化投影。
- 由区段贡献的键在一次尝试内有两种形态：各区段的部分值（在草稿中）与折叠后的值（屏障之后在
  尝试视图中）。两者是同一个 `ContextKey`；不存在 `PartialKey`。
- `whenAbsent` 是声明的字段，在文档中可见，并且只在该区域每个区段都可读时才生效（§4.3）。
- 规范键序是 `(id, type name)`；它是 `keys()` 的顺序、帧迭代的顺序，以及 `RuntimeState` 增益分组
  的顺序。

### 4.2 发布（Publication）

- **每实例一个存储。** `StructureContext` 携带 `InstanceId`；两台熔炉发布 `mymod:volume` 会发布到
  不同的存储，绝不冲突。因此 `SC-CTX-0107` 指的是*一个*实例的两个写者，而不是两个结构共享一个
  键。
- `ContextDraft` 按层打开，携带发布者读到的 `baseRevision`，由该阶段的单元写入，并在阶段边界
  发布一次。
- `publish` 返回 `Outcome<Void>`：过期的 `baseRevision` 是 `Missing(SC-CTX-0108)`，且不写入任何
  内容。同一帧内一个键出现两个不同值是 `SC-CTX-0107`，被拒绝而不是后写者胜。
- 草稿决定了一个键却不给值时，就发布那次缺席：帧记录该标记，因此在该层或以上的读者得到
  `null`/`Missing`，而不是更旧帧的值。
- **一个写者。** 匹配器在每个阶段边界发布一个草稿，存储按实例串行化发布；没有其他组件为某个层
  打开草稿。
- 读者按层打戳：聚合在其阶段的层读取 `context()`，检查通过 `FocusContext` 读取，
  `StructureContext.at(t)` 把实例上下文收窄到 `t`；高于视图自身层的层被钳制到它，因此 `at`
  绝不放宽。

### 4.3 贡献与折叠（Contributions and the fold）

- 区段计算通过一个以区段为界的草稿贡献：`draft.forSection(section)` 把该句柄的每次
  `contribute(key, partial)` 打上该区段，而在没有区段范围的草稿上调用 `contribute` 会被拒绝。
- 在屏障处，贡献按 `SectionKey` `(x, z, y)` 排序，并用 `merge.combine` 从左到右合并。折叠从第一个
  存在的贡献开始；没有单位元。
- 区域内任何一个区段被拒绝，该键就缺席。不完整的和无法表示。无法产出自己那份部分值的区段计算
  记录 `draft.refuse(key)`；读遍了每个区段却什么也没贡献的区段计算记录 `draft.declined(key)`，
  这是通往 `whenAbsent` 的唯一路径。
- 聚合计算或检查通过 `view.require(key)` 读取折叠后的值；不存在 `PartialKey` 式的直接读取。
- 单写者键（`merge == null`）由聚合计算或 `Deriver` 用 `put` 写入一次；同一次尝试中第二次写入
  不同的值是 `SC-CTX-0107`，而写入相同的值两次会被接受。
- 允许并行区段遍历：贡献彼此独立，且折叠顺序固定。

### 4.4 增益（Augments）

折叠规则位于 `AugmentKey` 上，因此同一效果的每个贡献者对叠加方式达成一致。

| 折叠 | 单位元 | 累计方式 | 用途 |
| --- | --- | --- | --- |
| `SUM` | `0.0` | `+=` | 相互独立的加成 |
| `MAX` / `MIN` | `-∞` / `+∞` | `max` / `min` | 已安装的最佳等级，从不叠加；是一种约束 |
| `MULTIPLY` | `1.0` | `*=` | 某子系统的缩放因子 |
| `SET` | — | 按顺序的最后一次贡献 | 覆盖 |

`RuntimeState.of` 按 `AugmentKey`（`id`、折叠序数）稳定排序，然后对每个键从左到右折叠：键序规范，
键内顺序为声明顺序。严格浮点使其可按位复现。未被表示的键是**缺席**，绝不是零或一；非有限的
`amount` 在构造时被拒绝；由有限输入得到的非有限*总和*会被省略并报 `SC-CTX-0301`，这是一条无
标签的 `WARNING`，其消息具名该增益键。

### 4.5 运行时状态（Runtime state）

| 问题 | 回答 |
| --- | --- |
| 持久化在哪里？ | 宿主：内核不持有任何映射、缓存或静态字段（I-15），也不做 I/O |
| 持久化什么？ | `SETTLE` 帧、增益映射与 `contextRevision`；`BLUEPRINT`/`PROBE`/`FORM` 帧重算 |
| 重载时会重算吗？ | 默认全部重算——值由捕获、计划与合并决定 |
| 会过期吗？ | 由 `contextRevision` 加上已结算的匹配决定；`generation` 由 `InstanceRecord` 派生，绝不手工递增 |
| 为什么不是一种格式？ | 没有 `schemaVersion`：JVM 的 `Class` token 随模组变化，而 I-12 让*文档*成为带版本的格式 |

**宿主升级**是来自匹配之外的唯一写入，且遵循同样的单写者规则：`RuntimeState.upgrade(key, value)`
在 `contextRevision + 1` 处发布，只接受 `Retention.CONTEXT` 的 `ContextKey`，当它所依据的基线已经
移动时被拒绝（`SC-CTX-0108`）。宿主升级绝不原地编辑帧。

### 4.6 层级（Hierarchy）

两个结构就是两个实例与两个存储，因此子结构的 `mymod:volume` 与父结构的绝不冲突，计划内部也不
存在提升步骤。跨实例读取有两种形态：父结构在自己的计划中重算该值，或者宿主读取子结构已结算的
`StructureContext`，并作为升级写入父结构的帧（§4.5）。第二种避免了第二次遍历，是宿主的义务，
而不是内核的。记录为缺口，此处不解决。

## 5. 参考代码（Reference sketch）

```java
// addon side, dev.vfyjxf.cumulus.example — the worked example, not a kernel type.
// ContextKeys.of builds each declaration; register binds it to the Catalogs draft (§6.4 of 22).
static final ContextKey<Long> IRON = ContextKeys.of(
        NamespacedId.of("structurecumulus", "iron_count"), Long.class,
        Retention.OUTCOME, Merges.sum(), null);

static final ContextKey<Integer> VOLUME = ContextKeys.of(
        NamespacedId.of("structurecumulus", "interior_volume"), Integer.class,
        Retention.CONTEXT, Merges.sum(), null);

final class IronSection implements Computation<Long> {                 // one contribution per section
    @Override public ContextKey<Long> output() { return IRON; }
    @Override public StatePhase phase() { return StatePhase.SECTION; }
    @Override public CuboidSet region() { return INTERIOR; }
    @Override public Set<ContextKey<?>> inputs() { return Set.of(); }
    @Override public Outcome<Long> compute(ComputationContext ctx) {
        return new Outcome.Found<>(countIron(ctx.section(), ctx.cells()), List.of(), FindingBundle.empty());
    }
}

final class Capacity implements Computation<Integer> {                 // one value, after the barrier
    @Override public ContextKey<Integer> output() { return CAPACITY; }
    @Override public StatePhase phase() { return StatePhase.AGGREGATE; }
    @Override public Set<ContextKey<?>> inputs() { return Set.of(VOLUME); }
    @Override public Outcome<Integer> compute(ComputationContext ctx) {
        return ctx.read(VOLUME).fold(
                (volume, subjects) -> new Outcome.Found<>(volume * 4, subjects, FindingBundle.empty()),
                bundle -> new Outcome.Missing<>(bundle));
    }
}

// a predicate consumes the folded value; absence is a Missing, never a zero
Check<Integer> enoughFree = new Check<>(
        NamespacedId.of("structurecumulus", "enough_free"), interiorBay,
        Focuses.context(VOLUME),
        volume -> volume >= 64
                ? Reply.match()
                : Reply.mismatch(Codes.CAVITY_TOO_SMALL.finding(...)),
        100);

// host side: `store` is the instance's store (§4.2), one draft per stage boundary
StructureContext before = store.context(instance);                     // revision 7, FORM frame
ContextDraft draft = store.draft(ContextTier.FORM, before.revision());
// the section pass calls draft.forSection(section).contribute(VOLUME, partial) once per section;
// the barrier folds; the aggregate calls draft.put(CAPACITY, 400).
Outcome<Void> published = store.publish(draft);                        // Missing(SC-CTX-0108) if 7 moved
if (published instanceof Outcome.Missing<Void> stale) {
    // another publish won; the attempt re-reads at revision 8 rather than overwriting it
}

// tier-stamped read: a PROBE reader cannot see a FORM value
StructureContext ctx = store.context(instance);
Integer atProbe  = ctx.at(ContextTier.PROBE).get(VOLUME);               // null before the FORM frame
Integer atForm   = ctx.get(VOLUME);                                     // 100

// augments and the host upgrade
AugmentKey capacityBonus =
        AugmentKey.of(NamespacedId.of("structurecumulus", "capacity_bonus"), AugmentFold.SUM);
RuntimeState state = RuntimeState.of(ctx,
        List.of(new Augment(capacityBonus, 3.0), new Augment(capacityBonus, 1.5)),
        ctx.revision());
double bonus = state.augments().get(capacityBonus);                     // 4.5
RuntimeState upgraded = state.upgrade(VOLUME, 144);                     // revision + 1, or SC-CTX-0108
```

## 6. 用例（Use cases）

**附加模组作者。** 为每个逐区段量写一个区段 `Computation`，为每个折叠后的值写一个聚合
`Computation`（或 `Deriver`）；声明一个 `ContextKey`，带其 `Retention` 与可选的 `Merge`；检查用
`Focuses.context(key)` 读取它。没有第二种键类型要学，也没有超出“结合律”的 `merge` 定律，因为折叠
顺序是固定的。

**宿主。** 在每个阶段边界发布一个草稿，只持久化 `SETTLE` 帧，应用宿主提供的增益，把 `RuntimeState`
存到其 `InstanceRef` 下，并在加载时比较 `contextRevision` 与已结算的匹配。`/cumulus explain` 打印
每个发布的键，以及发布它的层与修订号。

## 7. 证明（Proofs）

**P1 确定性。** 贡献在一个按 `SectionKey`（x, z, y）排序的列表上折叠；帧按修订号排序，键序是
`(id, type name)`；诊断项按遍历顺序追加；没有步骤迭代哈希容器或时钟。*失效条件*：某次合并读取
哈希有序集合、某计算调用 `System.nanoTime()`，或某发布者为同一个层打开两个草稿。

**P2 不重走。** `WorldView` 是匹配自身的不可变快照（I-4），每次读取都计入 `spend()`，随附算法是
每个区段一次复数查询加长方体算术。

**P3 一种键，一份声明。** `ContextKeys.register` 拒绝同一 id 的第二次注册（`SC-CTX-0102`），且
相等性是 `(id, type)`，因此没有两个组件能对 `mymod:volume` 赋予不同含义。

**P4 类型安全。** `get`/`require`/`put`/`contribute` 在 `T` 上泛型，因此编译器把键与值绑定；帧存储
`id → (type, value)`，视图按 `key.type()` 转型。残余漏洞：值类型必须可具体化（reifiable，`04`
§3.1）。

**P5 花费单调性。** 单元格与区段在派发时计费，无论是否命中缓存（`22` §9.5）；
耗尽只产生一次停止，绝不产生部分值。

**P6 增益折叠确定性。** 稳定排序固定键序，并在键内保留声明顺序；折叠从左到右，单位元有明确
文档；严格浮点可按位复现。

**P7 每实例一个写者。** 每次发布都经过一个存储，携带 `baseRevision`，过期时被拒绝
（`SC-CTX-0108`）；宿主升级遵循同样的规则（§4.5）。

**P8 层单调性。** 帧一经发布即不可变，读者绝不看自己所在层之上，因此在 `t+1` 的发布不能改变已在
`t` 达成的判定。

## 8. 依赖与待决问题（Dependencies and open questions）

| 依赖 | 归属 | 此处的假设 |
| --- | --- | --- |
| `ContextKey`、`Merge`、`Computation`、`SectionContext`、`AggregateContext`、`ContextDraft`、`ContextView` | U7 / `22` §6.4, §7.3 | 区段上下文没有 `read(ContextKey)`；区段计算只能 `contribute` |
| `MatchOutcome.context`、`Formed.generation()` | U7 | `OUTCOME` 值从产物中读取；`generation` 由派生得到 |
| `WorldView.occupants(Cuboid)`、`OccupantSnapshot.footprint()` | U2 | 足迹就是查询的答案 |
| `Cuboid.volume()/intersection`、`CuboidSet.of/empty/union/subtract/volume` | U1 | §4.3 与 §5 中的算术 |
| `ContextKeys.register`、`Catalogs.contextKeys`、`Spec.args` | U11, U5 | 一个注册漏斗 |
| `Outcome`、`Finding`、`FindingCode`；`InstanceId`、`InstanceRecord` | U0, U9, U7 | 诊断码 `SC-CTX-0102, 0107, 0108, 0109, 0301` |

**待决问题**，限于 `22` §16 为本单元留下的内容。（1）**§16 Q3，`whenAbsent`**：只在该区域每个区段
都可读时才生效；对一个可选的装饰性区域，这是否过严？无论答案如何，该字段都留在 `ContextKey` 上。

## 9. 证伪（Falsification）

| 测试 | 否定 | 期望 |
| --- | --- | --- |
| 发布一个 `baseRevision` 落后一次的草稿 | P7 | `Missing(SC-CTX-0108)`，较新的帧完好 |
| 两个单元在一个草稿里为同一个键 put 不同的值 | §4.2 | `SC-CTX-0107`，不写入帧 |
| 对一个 `merge` 非空的键调用 `put` | §3 动词规则 | DESIGN 时报 `SC-CTX-0109` |
| 一个 `FORM` 读者请求首次发布于 `SETTLE` 的键 | P8 | 在 `ContextTier.FORM` 处为 `null`，值只在 `SETTLE` 可见 |
| 三个被贡献的区段中有一个是 `Refused` | P1 / §4.3 | 该键缺席，`require` 返回 `Missing`，绝不是不完整的和 |
| 在一个 `SUM` 键上放两个 `Augment(capacityBonus, 1e308)` | `RuntimeState.of` | 无限总和被拒绝，键被省略，报 `SC-CTX-0301` |
| `new Augment(capacityBonus, Double.NaN)` | 有限性检查 | 构造时抛 `IllegalArgumentException` |
| 同一计划运行两次，其中一次打乱区段顺序 | P1 | 上下文相同：值相同、帧序相同、花费相同 |
| 针对已移动的 `contextRevision` 的宿主升级 | §4.5 | 被拒绝并报 `SC-CTX-0108`；较旧的 `StructureContext` 不变 |
| 一个 `SectionContext` 调用 `read(key)` | §4.3 | 无法编译：没有该成员 |
