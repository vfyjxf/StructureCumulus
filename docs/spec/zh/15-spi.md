# 15 — SPI 与门面（U11，`kernel.spi`）

> 设计草案 v0.1，2026-10-20。服务 R-10、R-14；强制 I-1、I-8、I-14、I-15。既有实现（prior
> art）：ArchitectCumulus `41`、`05`、`docs/design/spi.md`——仅供参考；以基础文档为准。

## 1. 范围（Scope）

| 拥有 | 不拥有 |
| --- | --- |
| `NamespacedId` 的语法与全序——唯一的命名类型；记录本身属于 U0（`kernel.base`、`01` §B） | 格、长方体、安放位（`05`） |
| `Catalog<T>`、`Catalogs`、`ContextKeys`——冻结的扩展快照与唯一的键工厂 | 被存储的类型：`ExpectationFactory`（`07`）、`FocusFactory`/`ComputationFactory`（`11`）、`RemedyProvider`（`14`）、`FindingCode`/`ExplainBook`（`13`） |
| `StructureKernel`——九个操作、它们的不变量、线程约定 | 匹配、计算或补救*意味着*什么（`11`、`12`、`14`） |
| `Stability`、随附的 `Codes`、I-8 这条线 | 语法（`10`）、诊断码格式（`13`）、每个宿主端口 |

这里的任何东西都不启动线程、不缓存一次尝试、不读时钟、不持有世界：一个内核就是
`(Catalogs, Host)` 加上九个操作——`document`、`compile`、`siting`、`check`、`settle`、
`derive`、`solve`、`explain`（`explainOrFallback` 与它合并计为一个）以及 `ledger`。`create`
是静态工厂，不是操作。

## 2. 术语（Vocabulary）

| 名称 | 形态 | 定义 |
| --- | --- | --- |
| `NamespacedId` | 记录 `(namespace, path)`，`Comparable` | `ns:path`；唯一的命名类型；先按命名空间、再按路径排序 |
| `Catalog<T>` / `.Builder<T>` | final 类 / 嵌套构建器 | 一个冻结的、带命名空间的、按插入顺序排列的家族，其自身的 id 由 `family()` 给出；`get` 是 `@Nullable`；构建器是唯一可变形态，并拒绝为同一个 id 提供第二个不同的值 |
| `Catalogs` | 接口 | 七个冻结家族加解释手册，对应一个内核 |
| `ContextKeys` | final 类 | `ContextKey` 的唯一构造者，也是它唯一的注册漏斗：`of(...)` 构建，`register(draft, key)` 绑定进 `Catalogs.Builder` 所实现的草稿，重复注册就在此时被拒绝（`SC-CTX-0102`） |
| `StructureKernel` | 接口，静态工厂 | 九个操作：`document`、`compile`、`siting`、`check`、`settle`、`derive`、`solve`、`explain`、`ledger`；`create` 是静态工厂 |
| `MatchResult` | 密封接口，`AutoCloseable` | `Admitted`、`Dropped`、`Formed`、`Failed`、`Undecided`、`Deferred`；只有 `Formed` 携带声明与修订，且只有 `settle` 产出它 |
| `Host` | 嵌套记录 | 创建时固定的只读端口：`DocumentSource`、`SegmentLibrary`、`@Nullable ClaimLedger` |
| `Stability` | 枚举 + `of(name)` | `STABLE`、`EXPERIMENTAL`、`DEPRECATED`；未知回答 `EXPERIMENTAL` |
| `Codes` | final 类，常量 | 内核随附的 `FindingCode` 实例，对应 `13` §4.3 的每一行 |

## 3. API 设计（API design）

```java
package dev.vfyjxf.cumulus.kernel.base;                             // @NullMarked

public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {
    public static final String DEFAULT_NAMESPACE = "structurecumulus";
    public static NamespacedId of(String namespace, String path);   // IAE on a violated grammar
    public static NamespacedId of(String path);                     // default namespace
    public static @Nullable NamespacedId parse(String text);        // null on malformed; no partial id
    public String text();                                           // "ns:path"
    @Override public int compareTo(NamespacedId other);             // namespace, then path, code point
}
```

`NamespacedId` 是 `kernel.base` 的，与其他 id 并列；本单元只是使用它。

```java
package dev.vfyjxf.cumulus.kernel.spi;                              // @NullMarked

public final class Catalog<T> {
    public NamespacedId family();                                   // the family's own id
    public @Nullable T get(NamespacedId id);                        // null = not registered
    public T require(NamespacedId id);                              // IAE naming the family and the id
    public boolean has(NamespacedId id);
    public List<NamespacedId> ids();                                // frozen, registration order
    public Map<NamespacedId, T> asMap();                            // unmodifiable, same order
    public int size();  public boolean isEmpty();
    public static <T> Catalog<T> empty(NamespacedId family);
    public static <T> Builder<T> builder(NamespacedId family);

    public static final class Builder<T> {
        public Builder<T> put(NamespacedId id, T value);            // a different value for one id is an IAE here
        public Builder<T> putAll(Catalog<T> catalog);
        public Builder<T> fill(Map<NamespacedId, T> values);
        public Catalog<T> build();                                  // frozen copy, ids in registration order
    }
}

public enum Stability {
    STABLE, EXPERIMENTAL, DEPRECATED;
    public static Stability of(NamespacedId name);                  // kernel-owned table
}

public interface Catalogs {
    Catalog<ExpectationFactory> expectations();   Catalog<FocusFactory> focuses();
    Catalog<ComputationFactory> computations();   Catalog<ContextKey<?>> contextKeys();
    Catalog<Deriver<?>> derivers();               Catalog<RemedyProvider> remedies();
    Catalog<FindingCode> codes();
    ExplainBook explanations();
    static Builder builder();                                       // starts from builtin()
    static Catalogs builtin();                                      // kernel entries only
    interface Builder {
        Builder expectations(Catalog<ExpectationFactory> catalog);  // merged over the kernel's family
        Builder focuses(Catalog<FocusFactory> catalog);
        Builder computations(Catalog<ComputationFactory> catalog);
        Builder contextKeys(Catalog<ContextKey<?>> catalog);
        Builder derivers(Catalog<Deriver<?>> catalog);
        Builder remedies(Catalog<RemedyProvider> catalog);
        Builder codes(Catalog<FindingCode> catalog);
        Builder explanations(List<Explanation> entries);            // merged; kernel entries win
        Catalogs build();                                           // IAE on a clash, naming both sides
    }
}

public final class ContextKeys {                                    // kernel.context
    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention,
                                       @Nullable Merge<T> merge, @Nullable T whenAbsent);
    public static <T> ContextKey<T> register(CatalogsDraft draft, ContextKey<T> declared);
    // A repeated id is refused when it registers, not at build(); the message names SC-CTX-0102 (§4.8).
}

public interface StructureKernel {
    static StructureKernel create(Catalogs catalogs);               // no documents, no ledger
    static StructureKernel create(Catalogs catalogs, Host host);
    @Nullable BlueprintDocument document(NamespacedId id);          // null = this host lacks it
    CompileResult compile(BlueprintDocument document);              // DESIGN: validate + assemble
    Outcome<Siting> siting(Blueprint blueprint, LatticePoint origin, WorldView view);   // anchor read
    MatchResult check(MatchRequest request);                        // read-only: no claim, no frame
    MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);  // the only writer; closes `admitted`
    StructureContext derive(MatchResult.Formed formed, List<DerivationSpec> derivations, Budget budget);
    List<Remedy> solve(FindingBundle findings, RemedyContext context);   // ordered, deduped, capped
    @Nullable Explanation explain(FindingCode code);                // null = not in this build
    Explanation explainOrFallback(FindingCode code);                // total; family fallback (13 §4.8)
    @Nullable ClaimLedger ledger();                                 // the handle for the world being matched; null when absent

    record CompileResult(FindingBundle findings, @Nullable Blueprint blueprint) {
        public boolean succeeded() { return blueprint != null; }    // findings may still warn
    }

    record Host(DocumentSource documents, SegmentLibrary segments, @Nullable ClaimLedger ledger) {
        public static Host empty();
    }
    @FunctionalInterface interface DocumentSource {
        @Nullable BlueprintDocument find(NamespacedId id);
        static DocumentSource of(Map<NamespacedId, BlueprintDocument> documents);   // immutable copy
        static DocumentSource empty();
    }
}

public sealed interface MatchResult extends AutoCloseable {
    record Admitted(Stage stage, Siting siting, MatchOutcome outcome,
                    @Nullable ClaimPlan planned, long ledgerRevision,
                    SnapshotLease lease, FindingBundle findings) implements MatchResult, AutoCloseable { }
    record Dropped(Stage stage, DropCause cause, List<Finding> findings) implements MatchResult { }
    record Formed(Stage stage, InstanceId id, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long generation, long contextRevision, long ledgerRevision,
                  SnapshotLease lease, FindingBundle findings) implements MatchResult, AutoCloseable { }
    record Failed(Stage stage, List<Finding> findings) implements MatchResult { }
    record Undecided(Stage stage, UndecidedReason reason, List<Finding> findings,
                     @Nullable MatchOutcome partial, @Nullable RetryToken retry) implements MatchResult { }
    record Deferred(Stage stage, RetryToken token, List<Finding> findings) implements MatchResult { }

    @Override default void close() { }        // Failed/Undecided/Deferred hold nothing
}
```

`create` 固定 `(Catalogs, Host)`；没有任何成员修改这两者，也不存储别的东西。`asMap()` 之所以存
在，是因为 `Catalog` 也被 `context` 的计划编译器消费，后者接受 `Map<NamespacedId, …>`，且不得
导入 `spi`（`02` §2）。`ContextKeys` 与键类型一起位于 `kernel.context`，原因相同：它是*注册*
——接受草稿并返回键——而草稿是 `kernel.context` 的 `CatalogsDraft`，一个由本单元的
`Catalogs.Builder` 实现的接口，因此没有任何 `context` 类型导入 `spi`。

`Formed` 是唯一携带 `ClaimSet` 的分支，而且只有 `settle` 产出它：`Admitted` 既没有 id 也没有
声明（`derive` 会拒绝它），它改为携带计划中的声明与 `check` 读到的账本修订，因此调用方可以
预览或注册。三个修订是被携带的，而不是重算的：`generation`（实例的）、`contextRevision`（它
发布的那个帧）与 `ledgerRevision`（结算时所处的修订），因此消费者不必询问宿主就能区分陈旧的
`Formed` 与当前的 `Formed`。

## 4. 语义（Semantics）

### 4.1 `NamespacedId`

- **形态**——严格是 `namespace:path`；`parse` 也接受默认命名空间下的裸路径；出现两个冒号即为
  格式错误。字符集：命名空间 `[a-z0-9_.-]{1,64}`，路径 `[a-z0-9/._-]{1,256}`，`/` 不得位于开
  头、结尾或连续出现。
- **大小写**——大写被拒绝，从不折叠；折叠会把两个宿主的名字并成一个键。
- **默认值**——`parse("hatch")` 是 `structurecumulus:hatch`（`03` §7）；`of(ns, path)` 没有默
  认值。
- **失败**——`parse` 回答 `null`：单一通道，不抛异常，不产生部分值。坏 id 的*位置*是调用方已
  经持有的 `DataView.span()`（U6），因此解析器不携带偏移量。
- **顺序**——记录相等性，因此 `parse(text(id)).equals(id)`；`compareTo` 是对命名空间再对路径
  的 `String.compareTo`——按码点、与区域设置无关，绝不使用 `Collator` 或 `equalsIgnoreCase`。

不是 `ResourceLocation`：I-1 让 `net.minecraft.**` 不出现在内核类路径上；它的字符集与大写处理
在 Minecraft 各版本之间已经变动（而 R-02 仍需要这条轴线）；并且它会把每份文档、每个目录 id
与每个诊断码都绑到一个版本上，尽管它们没有一个指名方块。

### 4.2 `Catalog<T>`：冻结，以及为何不用静态注册表

`put` 按引用存储实例，从不克隆，并在同一个 id 出现第二个*不同的*值时立即以
`IllegalArgumentException` 拒绝，同时指名该家族与该 id；重新放入同一个实例是空操作。`build()`
一趟把 id 复制进冻结列表加一个索引。冻结后的类型没有修改器，因此 `get`/`ids`/`asMap` 是
`O(1)` 且不会失败：冻结是类型事实，不是运行时标志。

静态注册表会成为全局可变状态（I-15），依赖类加载顺序，每个 JVM 只允许一个快照（无法做黄金对
比，无法并行测试），而且没有冻结，等于给 I-4 的元组增加一个隐藏参数。目录是一个值。

### 4.3 `Catalogs`：恰好七个家族

| 家族 | 条目 | 解析者 | `builtin()` 中该家族为空意味着 |
| --- | --- | --- | --- |
| `:expectations` | `ExpectationFactory`（`07`） | `compile`、组装 | 每个 `factory` 形态的舱位主体都无法解析：`DESIGN` 阶段的 `SC-SPI-0002` |
| `:focuses` | `FocusFactory`（`11`） | `compile`、计划构造 | 每个舱位只回退到随附的种类；声明了这些种类之外的焦点种类即 `SC-SPI-0002` |
| `:computations` | `ComputationFactory`（`11`） | `compile`、`check` 的 `SECTION`/`AGGREGATE` 阶段 | 每条随附五种之外的 `computations[]` 条目都无法解析：`SC-CTX-0101` |
| `:contextKeys` | `ContextKey<?>`（`12`） | `compile`（声明）、`check`/`derive`（读取） | 任何 `context[]` 声明都无法解析：`DESIGN` 阶段的 `SC-CTX-0101` |
| `:derivers` | `Deriver<?>`（`12`） | `compile`、`derive` | 每条随附集合之外的 `derives[]` 条目都无法解析：`SC-CTX-0101` |
| `:remedies` | `RemedyProvider`（`14`） | `solve` | `solve` 回答空列表 |
| `:codes` | `FindingCode`（`13`） | `compile`、`explain`、`Codes` | 不可达：随附 `13` §4.3 的每一行 |

片段是**文档，不是目录**（`SegmentLibrary` 是带修订号的宿主数据，随 `Host` 传入，随数据包重
载）；捕获、占用体解析、声明与编辑执行器都是**宿主端口**，以对象形式传入。增加第八个家族是一
次词汇表与诊断码表级别的事件。

`builtin()` 在全部七个家族中都随附内核自己的条目：叶与组合器期望（`block`、`air`、`trait`、
`allOf`、`anyOf`、`not`），`22` §6.3 的焦点种类，计算 `count`、`sum`、`volume`、`distinct`、
`bind`，内核自己的声明所用的 `contextKeys`，它随附的 `derivers`，没有补救方案提供者，以及全部
诊断码。它无法随附的是插件形态的工厂：插件自己发明的 id 在该插件注册它之前始终无法解析。

`builder()` 从 `builtin()` 出发，因此未设置的家族取内核自己的——绝不是 `null`，也绝不会意外为
空——而传入的家族**合并到**它之上，内核条目在前，所以已随附的 id 无法被遮蔽（被拒绝，而不是
被采纳）。插件通过调用自己的 `Catalog.builder(...).put(...).build()` 方法，并在唯一一次
`create` 之前把它们交给 `Catalogs.builder()` 来加入；插件永远看不到内核。
`ContextKeys.register(draft, …)` 是键进入 `:contextKeys` 的途径：草稿是唯一能对照将要与之一
冻结的目录来校验声明的地方，而同一个 id 的第二次注册会在注册时被拒绝
（`SC-CTX-0102`），无论两条记录的其他组件如何。

### 4.4 九个操作（The nine operations）

| 操作 | 读取 | 不得触碰 | 确定性输入 |
| --- | --- | --- | --- |
| `document` | `Host.documents` | 文件系统、缓存 | id + 宿主快照 |
| `compile` | 文档、`Host.segments`、五个声明家族 | `WorldView`、声明 | 文档字节 + 快照 |
| `siting` | 恰好一个格——`origin`——经给定的视图 | 正在被选择的朝向 | 蓝图 + 原点 + 捕获 |
| `check` | `request.lease().view()`、宿主在派发时制作的冻结声明视图（`Host.ledger()`）、`catalogs.focuses()`/`computations()` | 写入声明、上下文帧或世界；任何其他世界句柄、任何修改器 | 请求（I-4） |
| `settle` | 请求、admitted 结果（它的计划与其子句柄）、`Host.ledger` | 世界；脱离服务端线程的结算；不是本请求的绑定 | 请求 + admitted + 账本修订 |
| `derive` | 已成型实例的帧、`Host.ledger()`（只读、可空）、`catalogs.computations()`/`contextKeys()`/`derivers()` | 重新遍历世界；修改已结算的匹配（I-9）；在更新的 `baseRevision` 之上发布 | formed + specs + budget |
| `solve` | `context`（租约、声明、产物、上下文、选项）、`catalogs.remedies()` | 应用任何东西、开启事务（I-11） | findings + context |
| `explain`、`explainOrFallback` | `catalogs.explanations()` | 文件系统、区域设置的默认值 | 诊断码 |
| `ledger` | `Host.ledger` | 世界、账本的写 API | 宿主快照 |

- **`check` 读取，`settle` 写入。** 匹配在宿主工作线程上对密封快照运行，并读取声明视图；
  `check` 返回判定、计划中的声明与它读到的修订（`Admitted.planned()`、
  `Admitted.ledgerRevision()`），不写入任何声明，也不写入任何上下文帧。
  `settle(request, admitted)` 是唯一的写入入口：它在服务端线程上运行，用
  `CaptureGate.evaluate(request.lease())` 检查捕获，检查绑定，派生出行，且只在 `Settled` 之后
  返回 `Formed`。冲突是带在位行的 `Failed(SC-CLM-0002)`，
  而 `RevisionMoved` 在事务内重试一次，然后才变成 `Undecided`。
- **`check` 与 `settle` 是唯一持有租约的成员。** `Admitted` 与 `Formed` 是 `AutoCloseable`。从不调用
  `settle` 的调用方自己关闭 `Admitted`；调用 `settle` 的调用方不再碰它，因为 `settle` 在每条路径
  上都关闭它，而 `Formed` 只在调用方用完 `derive` 之后才关闭。对每个分支
  `try (var r = kernel.check(request))` 都能编译，而未关闭的句柄是宿主会计数的泄漏。
- **`siting` 是锚点的那一次读取。** `AnchorResolver.resolve` 只读 `origin`，并返回满足
  `candidate.apply(authoredFront) == anchorHeading` 的唯一 `Siting`；已经持有安放位的调用方
  （一条命令、一个没有锚点的蓝图）把它放进 `MatchRequest`，永不调用本操作。
- **缺失就是答案。** 没有文档 → `null`；创建时没有账本 → `ledger()` 为 `null`；未随附的诊断
  码 → `explain` 为 `null`，`explainOrFallback` 合成家族文本。永不抛异常：只有
  `NamespacedId.of` 与两个 `build()` 抛异常，且只在启动期的程序员错误时抛。
- **缺失的定义是能最早知道它的那个阶段的一条诊断项。** `compile` 把无法解析的工厂报告为
  `SC-SPI-0002`（`DESIGN`），缺失的片段报告为 `SC-DOC-0003`，未注册的计算或键在文档的诊断项
  中报告为 `SC-CTX-0101`。`check` 永远不会遇到这类诊断项：`Blueprint` 已经持有它的
  `Expectation`/`Focus`/`Computation` 实例，因此 **`check` 完全不解析任何工厂**。
- **每次使用只有一个声明来源。** `ledger()` 是宿主为正在匹配的那个世界持有的句柄；`check` 读取宿主为那次派发制作的冻结
  视图（`02` §5、`20` §2），绝不读取活动账本。门面从不把其中一个来源注入另一个——那是隐藏状
  态——而 `11` §4.6 已经拥有 `Undecided(LEDGER_UNAVAILABLE)`。
- **线程：** 目录、`Host` 与内核都是不可变的，因此在端口线程安全的前提下
  `check`/`derive`/`compile`/`solve`/`explain` 可并发安全；构建器是单线程的。`settle` 属于服务
  端线程。`derive` 只接受一个 `Formed`（I-9）；`solve` 只做聚合——提供者按 `id()` 顺序，U10
  的去重、排序与上限。

### 4.5 平凡实现（R-10、I-8）

允许随附：`Expectations` 组合器（`allOf`、`anyOf`、`not`、`sequence`、`atLeast`、`atMost`、
`exactly`、`custom`），即 `Expectation` 值的纯组合；描述符测试 `air()`、`anyOccupant()`、
`nothing()`、`block(NamespacedId)`、`trait(TraitKey)`，它们比较 `OccupantSnapshot` 事实——类型、
空足迹、某特征是否存在——绝不比较宿主载荷；`22` §6.3 的焦点种类与计算 `count`、`sum`、
`volume`、`distinct`、`bind`；以及文档语法、规范化器、校验器、组装器、诊断码表与 `ExplainBook`。

不得随附：读取 `payload()` 的谓词、方块状态、能力、流体或能量处理器（适配器/插件知识，
R-01/R-02）；`WorldView`、`WorldCapture`、`ClaimLedger`、`EditExecutor` 或渲染器（宿主端口）；
**普查键**——普查是*检测*的输出（`MatchOutcome.census()`），重新发布它的 `ContextKey` 会声称自
己是一个零能力的计算，而插件用随附的 `count` 十行就能写出它。因此 `builtin()` 随附通用叶期
望、焦点与计算、内核自己的键、没有补救方案提供者以及全部诊断码：编码了*某一个*结构含义的名
字，永远是插件的。

### 4.6 `Stability`

- `STABLE`——签名与语义冻结；移除需要走一轮 `DEPRECATED` 并升主版本。记录在内核自有表中，也
  记录在 `Codes` 中。
- `EXPERIMENTAL`——可用，但可能在小版本中改变或消失；任何东西都不得跨端口依赖它。它是**默认
  值**：表中缺席的每个名称都回答这一项。
- `DEPRECATED`——仍在服务，行为不变，已排定移除，已指名替代项。通过
  `@Deprecated(since, forRemoval = true)` **以及**表行**以及**一个 `seeAlso` 指名替代项的
  `Explanation` 来记录。

U11 的五个名称是 `STABLE`；条目类型沿用其所属单元的状态（`expectation_factory`、
`focus_factory`、`computation_factory`、`state_key`、`remedy_provider`、`finding_code`、
`explain_book` 在这些单元冻结之前保持 `EXPERIMENTAL`）；插件里的名称不加标记，因此是
`EXPERIMENTAL`。弃用是建议，不是运行时状态：被弃用的条目照旧解析，也不会因为被使用而产生诊
断项。

### 4.7 生命周期（Lifecycle）

注册发生在 `create` 之前、单线程、经构建器完成。`create` 保留构建出的值，因此被留存的构建器
只能产出另一个目录。两个内核不共享任何可变对象：同样的字节在两个快照下编译与匹配的结果不同，
这正是黄金测试的固件。迟到的注册在类型上不可能（没有修改器），在值上也不可能（新内核即新快
照）；一次运行中的尝试保留自己那份。重载：文档与片段是*数据*——新的 `Host`、新的内核，在飞行
中的尝试结束后再退役旧的；目录是*代码*——一次新的构建。没有热插拔；条目在并发尝试之间共享、
可重入、从不克隆。

### 4.8 诊断码注册表（Code registry）

`Catalog<FindingCode>` 以 `NamespacedId` 为键，其路径是诊断码文本的小写形式
（`structurecumulus:sc-exp-0002` → `SC-EXP-0002`），命名空间由注册它的 mod 给出，因此键↔值可
推导、可检查。

- **一个诊断码一个键**——`build()` 拒绝不等于 `code.text().toLowerCase(Locale.ROOT)` 的路径。
- **一个编号一个诊断码**——`build()` 拒绝两个条目共用一组 `(family, number)`，并指名两个键。
- **每个随附的诊断码**——`builtin()` 携带 `13` §4.3 每一行对应的一个条目，与 `Codes` 双射；
  一个 `kernelTest` 断言这一点，且 `build()` 拒绝它的手册无法回答的诊断码。这里的每次拒绝都是
  `IllegalArgumentException`，而不是诊断项：快照在任何尝试存在之前就已构建，因此没有阶段或定
  位域可供挂载诊断项（快速失败胜过静默胜出）。
- **插件诊断码**——家族是 U9 的封闭 15 分支枚举，因此插件增加的是*诊断码*，不是家族：
  `structurecumulus` 拥有 `0001–0999`，插件拥有 `1000–9999`，从不重新指向（`13` §4.3）。没有
  自动分配：分配顺序会让诊断码的含义取决于加载顺序。
- **未知诊断码**——仍能从诊断项构造、排序与渲染（`13` §4.3）；`explain` → `null`，
  `explainOrFallback` → 家族文本，当诊断包针对某本手册构建时给出 `SC-DIA-0004`。

U11 铸造一个诊断码：`SC-SPI-0002`，*文档指名了本快照不持有的扩展 id*。

## 5. 参考代码（Reference sketch）

```java
// ---- addon code (dev.vfyjxf.cumulus.example), against kernel types only ----
final class HatchFactory implements ExpectationFactory {
    static final NamespacedId ID = NamespacedId.of("mymod", "hatch");
    @Override public NamespacedId id() { return ID; }
    @Override public Expectation create(DataView args, FactoryContext ctx) {
        return Expectations.allOf(Expectations.block(NamespacedId.of("minecraft", "iron_bars")),
                                  Expectations.trait(Traits.ITEM_INVENTORY));
    }
}
final class Scale implements ComputationFactory {   // §12.3's aggregate, an addon computation
    static final NamespacedId ID = NamespacedId.of("mymod", "scale");
    @Override public NamespacedId id() { return ID; }
    @Override public Computation<Long> create(DataView args, FactoryContext ctx) {
        return /* factor = args.asInt("factor"); phase = AGGREGATE; output/inputs from the Spec */;
    }
}
final class ReplaceBlock implements RemedyProvider {
    static final NamespacedId ID = NamespacedId.of("mymod", "replace_block");
    @Override public NamespacedId id() { return ID; }
    @Override public List<Remedy> solve(Finding finding, RemedyContext ctx) {
        if (finding.primaryLabel() == null) { return List.of(); }    // cannot locate it, cannot help
        EditPlan plan = /* one Replace per primary cell, U10 */;
        return List.of(new Remedy.Repair(plan, Applicability.MACHINE_APPLICABLE, "swap the wall"));
    }
}

// ---- host: compose a snapshot, then create; no kernel internals touched ----
Catalogs catalogs = Catalogs.builder()
        .expectations(Catalog.<ExpectationFactory>builder(NamespacedId.of("expectations"))
                .put(HatchFactory.ID, new HatchFactory()).build())
        .computations(Catalog.<ComputationFactory>builder(NamespacedId.of("computations"))
                .put(Scale.ID, new Scale()).build())
        .remedies(Catalog.<RemedyProvider>builder(NamespacedId.of("remedies"))
                .put(ReplaceBlock.ID, new ReplaceBlock()).build())
        .codes(Catalog.<FindingCode>builder(NamespacedId.of("codes"))
                .put(NamespacedId.of("mymod", "sc-exp-1234"),
                     FindingCode.of(FindingCode.Family.EXP, 1234)).build())
        .explanations(addonExplanations)                                // one per addon code
        .build();

StructureKernel kernel = StructureKernel.create(catalogs,
        new StructureKernel.Host(StructureKernel.DocumentSource.of(documents), library, ledger));

BlueprintDocument doc = kernel.document(NamespacedId.of("mymod", "huge_furnace"));  // (a) absent?
if (doc == null) { command.reply("no such blueprint"); }                           // null, not a throw

CompileResult compiled = kernel.compile(doc);                                      // (b) DESIGN
if (!compiled.findings().isEmpty()) { command.reply(FindingRenderer.human().render(compiled.findings(), text)); }
if (compiled.blueprint() == null) { return; }                                      // errors, no artifact
Blueprint furnace = compiled.blueprint();

Outcome<Siting> siting = kernel.siting(furnace, LatticePoint.of(10, 64, 10), view); // one cell read
if (siting instanceof Outcome.Missing<Siting> missing) {                            // anchor absent
    missing.findings().forEach(sink::accept);
    return;
}
Siting at = ((Outcome.Found<Siting>) siting).value();
SnapshotLease lease = capture.lease(furnace, at);                                  // the record owns it

MatchRequest request = MatchRequest.at(lease, furnace, at)                           // (c) one call
        .budget(Budget.of(4_096, 64)).build();

// (d) `check` reads: verdict, plan and the revision it read — nothing is written
MatchResult probe = kernel.check(request);
if (!(probe instanceof MatchResult.Admitted admitted) || admitted.planned() == null) {
    command.reply("no conclusion: " + probe);   // no ledger, or no registerable bay: preview only
    probe.close();                              // preview only: the caller releases the handle
    return;
}
// (e) `settle` is the only writer and the only source of `Formed`; it consumes `admitted`
try (MatchResult result = kernel.settle(request, admitted)) {
        switch (result) {
            case MatchResult.Failed f    -> command.reply(FindingRenderer.human().render(f.findings(), text));
            case MatchResult.Undecided u -> command.reply("no conclusion: " + u.reason());
            case MatchResult.Dropped d   -> scheduler.resubmit();                    // the snapshot moved
            case MatchResult.Deferred d  -> scheduler.retry(d.token());             // AUTO only
            case MatchResult.Admitted a  -> { }       // `settle` never returns it: exhaustiveness only
            case MatchResult.Formed formed -> {
                StructureContext values = kernel.derive(formed, furnace.derivations(),  // (f) derive
                        Budget.of(1_024, 16));
                Outcome<Long> capacity = values.require(ExampleKeys.CAPACITY);              // Missing if unproduced
                ClaimSet settled = formed.claims();                                     // claims come from Formed
                long generation = formed.generation();
                long ledgerRevision = formed.ledgerRevision();                          // what the settle settled at
                ClaimView claims = ledger.read(furnace.bounds().translate(at));         // the same revision
                List<Remedy> offered = kernel.solve(formed.findings(),                  // (g) solve
                        new RemedyContext(furnace, formed.siting(), claims,
                                ledgerRevision, formed.outcome(), values, RemedyOptions.defaults()));
                Explanation known = kernel.explain(Codes.CLAIM_CONFLICT);                // (h) explain
                Explanation any = kernel.explainOrFallback(FindingCode.of(FindingCode.Family.SPI, 9999));
            }
        }
    }
}
// ledger(): kernel.ledger() is the view the requests carried; null when create took none.
```

## 6. 用例（Use cases）

| 受众 | 场景 |
| --- | --- |
| 插件作者 | 随附 `HatchFactory`、`Scale`、`ReplaceBlock`、一个键、一个诊断码及其 `Explanation`：它触及的每个家族一次 `Catalog.builder(...).put(...).build()`，然后一次 `ContextKeys.register(draft, …)`。永远看不到会话、步骤顺序、锁或内核子类。 |
| 宿主 | `/reload` 重建 `SegmentLibrary` + `DocumentSource` 并再次调用 `create`，在飞行中的尝试在旧内核上跑完；`/cumulus explain` 对已注册诊断码使用 `explain`（退出码 0），否则用 `explainOrFallback`（退出码 1）；一个测试在同一个方法里基于两个快照构建两个内核，缺少 `mymod:hatch` 的那个对同样的字节报告 `SC-SPI-0002`。 |

## 7. 证明（Proofs）

| # | 主张 | 论证（以及什么会破坏它） |
| --- | --- | --- |
| P1 | 冻结 | `Catalog` 没有修改器，因此构建出的值无法经任何路径改变；`build()` 把 id 复制进冻结列表加索引；被留存的构建器只写自身；`Catalogs.Builder.build()` 从不修改它的输入。*破坏条件*：某个成员暴露活的构建器状态，或 `asMap()` 可变。 |
| P2 | 门面纯净性 | `Host` 持有文档、片段、账本——没有 `WorldView`、`WorldCapture`、`EditExecutor`——因此任何操作都只能经调用方提供的值接触世界（`check` 用 `request.lease().view()`，`solve` 用 `context.lease().view()`，`siting` 用它的视图实参）；`check` 只读取宿主为那次派发制作的冻结声明视图，不写入任何东西，而 `settle` 是账本唯一的写入者。*破坏条件*：`Host` 的某个字段是 `WorldView`，或一次 `check` 调用写入了声明、上下文帧或世界。 |
| P3 | 没有静态可变状态 | `kernel.spi` 中的每个静态成员都是 `static final` 或纯工厂；稳定性表是一次性构建的不可变映射；内核是值，不是单例；一个反射测试断言非 final 静态成员为零。*破坏条件*：出现备忘或构建器缓存。 |
| P4 | 查询全函数性 | `get`、`document`、`explain`、`ledger` 是以 `@Nullable` 为值域的全函数；`explainOrFallback`、`compile`、`solve`、`ids`、`asMap` 是以值为值域的全函数；只有 `of` 与 `build()` 抛异常，且只在前提条件上抛。*破坏条件*：任何门面成员因文档、账本或诊断码缺失而抛异常。 |
| P5 | 内核间隔离 | 两个内核只共享调用方传入的不可变值，不存储别的东西；在端口线程安全的前提下两者都可安全并发调用，因此不同快照给出不同结果。*破坏条件*：内核把已解析的扩展备忘在字段里。 |
| P6 | 稳定的 id 顺序 | `ids()` 是冻结列表，绝不派生自哈希容器；门面为 `solve` 对提供者 id 排序（`14` §4.2），`compile` 按 `(order, cost, id)` 排序（`12` §4.1），因此结果与注册顺序无关；`ids()` 只在呈现上有所不同。*破坏条件*：某个消费者迭代 `asMap().values()` 来挑胜出者。 |
| P7 | 一个写入者，一个线程 | `settle` 是唯一产出实例的成员，且只在账本回答 `Settled` 之后才这么做；`check` 不写入声明也不写入帧，因此没有任何门面成员接受一个计划或 `ClaimSet` 并把它写下去。*破坏条件*：插件能不匹配就写入声明，或 `Formed` 在 `settle` 返回之前就存在。 |

## 8. 依赖与待决问题（Dependencies and open questions）

| 依赖 | 此处的假设 |
| --- | --- |
| U3 `ExpectationFactory`、`FactoryContext`、`Expectations` | `id()` + `create(DataView, FactoryContext)`；组合器是静态方法，不是条目 |
| U11/U8 `FocusFactory`、`ComputationFactory`、`Computation` | 焦点与计算种类都是目录条目；`open` 是把 `Spec` 解析成类型化单元的地方 |
| U8 `ContextKey`、`Merge`、`Retention`、`StructureContext` | `ContextKeys.of(...)` 构建一个 `ContextKey`；`ContextKeys.register(draft, key)` 是唯一的目录绑定；重复 id 是 `SC-CTX-0102` |
| U10 `RemedyProvider`、`RemedyContext`、`RemedyOptions`、`Remedy` | 提供者由 `id()` 声明；`solve` 是门面背后的 `solveAll`（排序、去重、上限） |
| U6 `SegmentLibrary`、`BlueprintDocument`、`SegmentAssembler`；U9 诊断码 | 校验结果是 `List<ValidationIssue>`，被包装进 `FindingBundle`；`Codes` 位于此处 |

1. **`Host` / `DocumentSource` 是新名字。** 因为嵌套，所以不欠词汇表行；把其中任何一个提升为顶
   层（A1 会实现它）需要一行，并需要一个 `Stability` 值。
2. **`derive` 需要一个 `Budget`。** 已解决：`04` §8 现在传入 `Budget.of(...)`；
   `StructureKernel.derive` 显式要求它，没有隐藏默认值（I-15）。
3. **编译路径。** 由 `compile` 操作解决：它以声明家族运行 U6 的 `DocumentValidator` 与
   `SegmentAssembler`，返回 `CompileResult(findings, @Nullable Blueprint)`，并且不增加成员。
   U6 必须在两个 API 中都接受工厂目录（`10` Q-D6）。
4. **`SC-SPI-0002`** 需要在 `13` §4.3 里有一行和一个 `Explanation`；现在各处的路由都是
   `15-spi.md`（`00` §5、`_index.md`）。

`22` §16 的六个问题没有一个属于本单元。有一项被携带的缺陷不是 §16 的问题，记录在这里以免丢
失：**规范严重级别无处安放。** `Catalog<FindingCode>` 携带身份，`Explanation` 携带文字，因此
`SC-DIA-0002` 仍然没有东西可用来比较诊断项的严重级别——要么 `Explanation` 增加 `Severity`
（U9），要么该条目变成 `CodeEntry(code, severity, explanation)`；`22` 对此不作裁定。

## 9. 证伪（Falsification）

| # | 主张 | 能破坏它的测试 |
| --- | --- | --- |
| F1 | 冲突被拒绝，永不被消解 | 两个目录都注册 `mymod:hatch`，两次 `ContextKeys.register` 调用取同一个 id，或两个命名空间都注册 `SC-EXP-1234`：第二次 `put`／注册抛异常并指名该家族与该 id，且没有任何内核持有其中任何一对，而重新放入同一个实例是空操作。静默保留第一个才是 bug。 |
| F2 | 目录变更之后内核永不改变 | 留存两个构建器，构建内核，再 `put` 更多条目并再次构建：`check`、`settle`、`derive`、`solve`、`explain` 与全新内核完全一致。 |
| F3 | 没有门面操作读取世界 | 统计 `WorldView` 上的读取次数；调用 `document`、`compile`、`explain`、`explainOrFallback`、`ledger`：计数保持为零。然后用一个*不同的*桩作为请求的视图调用 `check`：只有那一个被读取，而 `siting` 恰好读取它自己实参的一个格。 |
| F4 | 没有插件需要内核子类 | 写一个改变引擎步骤顺序、增加第十个操作或遮蔽随附诊断码的插件。若其中任何一项不编辑 `kernel.spi` 就能表达，“一个内核，多种扩展”即为假；今天这三项都被冻结的类型拒绝。 |
| F5 | `builtin()` 只匹配内核随附的东西 | 基于 `Catalogs.builtin()` 创建：一份舱位主体为 `allOf`/`block` 的文档能编译并匹配；同一份文档把主体换成 `mymod:hatch` 就是 `Stage.DESIGN` 处的 `SC-SPI-0002`，不涉及任何 `WorldView`。能解析 `mymod:hatch` 的 `builtin()` 已经随附了一个插件。 |
