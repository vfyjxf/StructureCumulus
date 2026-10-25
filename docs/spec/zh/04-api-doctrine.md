# 04 — API 设计准则（API doctrine）

> 内核（kernel）内部复杂、外部小（R-14）。本文是维持这一点的契约：三类受众各看到
> 什么、组合如何工作、类型如何保持诚实，以及参考代码必须证明什么。

## 1. 三类受众，三个表面（Three audiences, three surfaces）

| 受众 | 可见 | 不可见 |
| --- | --- | --- |
| **首次使用者（First-use caller）** | `Blueprint` 构建器、`StructureKernel.check`/`settle`、sealed `MatchResult`、`StructureContext`、`FindingBundle` | 预筛索引、会话、步骤顺序、声明预留、事务内部 |
| **附加包作者（Addon author）** | `Expectation` + `Expectations`、`Deriver`、`RemedyProvider`、类型化键、`DataView` | 引擎内部、匹配循环、诊断包的去重算法 |
| **宿主 / 适配器作者（Host / adapter author）** | `WorldCapture`、`WorldView`、`ClaimLedger`、`EditExecutor`、`DataBridge`、目录 | 一次匹配如何遍历蓝图 |

只被引擎需要的公开类型是包私有的。附加包需要、但首次使用者从不接触的类型，留在其所属
单元的包里，并在文档中如此说明。

## 2. 组合，而非配置（Composition, not configuration）

凡是使用者可能想要“几种行为之一”的地方，API 都提供一个与该类值可组合的值。

- **期望（expectation）**可组合：每个组合器接收 `Expectation` 并返回 `Expectation`。
  没有 `ExpectationBuilder` 层次，也没有 `PredicateType` 枚举。
- **片段（segment）**可组合：`Segment` 是值；`Blueprint` 是片段的装配。
- **派生（derivation）**可组合：派生集合是有序的 `Deriver` 节点列表；一个派生器可以读取
  同一阶段中更早产生的值。
- **补救方案（remedy）**可组合：`RemedyProvider` 返回列表；一次修复可以包含嵌套的编辑
  计划；预览是同一诊断项的独立投影。
- **诊断项（finding）**可组合：诊断包是经过过滤/排序的集合；相关诊断项是一个 id，不是
  嵌套对象图。

配置对象（`MatchPolicy`、`FindingOptions`、`OrientationPolicy`）只用于*非*组合性的
旋钮：上限、预算、允许的朝向变换集合。

## 3. 类型安全（Type safety）

### 3.1 类型化键（Typed keys）

任何以“某类型的具名事物”身份跨越边界的值，都使用类型化键：

```java
record ContextKey<T>(NamespacedId id, Class<T> type,
                     Retention retention, @Nullable Merge<T> merge, @Nullable T whenAbsent) {}
record TraitKey<T>(NamespacedId id, Class<T> type) {}
record BindingKey<T>(NamespacedId id, Class<T> type) {}
record ParameterKey<T>(NamespacedId id, Class<T> type) {}
```

`Class<T>` 令牌不是装饰。它让异构映射在边界处以一个精确的诊断项失败，而不是在三层调用
之后抛 `ClassCastException`：

```java
<T> @Nullable T get(ContextKey<T> key);          // no unchecked cast at the call site
<T> Outcome<T> require(ContextKey<T> key);       // absence with a finding
<T> ContextDraft put(ContextKey<T> key, T v);    // single writer; the compiler ties key and value
<T> ContextDraft contribute(ContextKey<T> key, T partial);   // section contribution
```

### 3.2 封闭家族（Sealed families）

封闭集合是 `sealed`，因此消费者的 switch 是穷尽的，新增分支属于有意的变更：

```java
sealed interface MatchResult extends AutoCloseable
        permits MatchResult.Admitted, MatchResult.Dropped, MatchResult.Formed, MatchResult.Failed,
                MatchResult.Undecided, MatchResult.Deferred {}
sealed interface Edit permits Place, Remove, Replace, SetTrait, Configure, BindAnchor, NestedEdit {}
sealed interface Remedy permits Remedy.Repair, Remedy.Preview, Remedy.ExplainRemedy {}
```

在内核 sealed 类型上的 switch 里写 `default` 分支是代码异味：它会在新增分支时静默通过。
参考代码使用穷尽 switch。

### 3.3 不使用字符串类型化的通道（No stringly-typed channel）

`String` 允许用于：人类可读消息、描述符属性值、`NamespacedId` 内部的命名空间/路径，
以及文档字段名。它不允许用于舱位 id、共享规则、阶段、检查种类、补救方案种类或诊断项
严重级别。

## 4. 空值（Nullness）

- 每个内核包都是 `@NullMarked`（JSpecify）。
- 缺失用 `@Nullable` 表示，公开签名中绝不用 `Optional`。
- `null` 表示**缺失或越界**，绝不表示“不可读”。快照无法回答的格是一个诊断项
  （`UNREADABLE`），不是 `null`（见 `06-occupants.md` §5）。
- 返回集合的方法返回空集合，绝不返回 `null`。
- `Checks.checkNotNull` 是唯一的断言辅助；它只在程序员前置条件被违反时抛出，绝不为
  结构性结果抛出。

## 5. 结果，而非异常（Results, not exceptions）

结构性失败是数据：

```java
MatchResult result = kernel.check(request);
switch (result) {
    case MatchResult.Admitted admitted -> { /* the planned claim and the revision it read */ }
    case MatchResult.Dropped dropped -> { /* the snapshot moved; no verdict exists */ }
    case MatchResult.Formed formed -> { /* outcome + findings */ }
    case MatchResult.Failed failed -> { /* at least one mismatch */ }
    case MatchResult.Undecided undecided -> { /* reason + findings */ }
    case MatchResult.Deferred deferred -> { /* retry token */ }
}
```

规则：
- `compile`、`check`、`settle`、`derive`、`solve` 和 `explain` 对坏结构从不抛出。
- `IllegalArgumentException` 只用于构造时的畸形值（`min > max` 的 `Cuboid`），且消息中
  点明该参数。
- 任何公开成员都不得无条件抛出（项目的桩禁用规则）。
- 宿主侧失败（世界已关闭、账本不可用）是一个带稳定码的诊断项，不是让一个 tick 解栈的
  异常。

## 6. 构建器与不可变性（Builders and immutability）

- 组件多于四个的值**仅当**多数组件可选时才配一个构建器。`Blueprint` 与
  `MatchRequest` 符合；`LatticePoint`、`Cuboid`、`Claim` 和 `Finding` 不符合。
- 构建器是可变的、非线程安全的，`build()` 只校验一次。构建出的值不可变。
- 除非必须缓存派生字段，每个值类型都是 `record`；被缓存的字段是 `private final`，且不
  参与相等判定。
- `withX(...)` 返回新值；构建出的值上没有 setter。

## 7. 预算、取消与开销（Budget, cancellation and cost）

- `Budget` 显式传递。扩展从不读取全局预算。
- `ProbeSession.remaining()` 返回*此刻*剩余的预算，而不是步骤入口时的预算；谓词可以据此
  判断下一次读取是否可负担。
- `CancelToken` 在步骤边界以及每 N 格读取时检查；被取消的尝试返回
  `Undecided(CANCELLED)`，而不是异常。
- 每个结果都上报 `Spend`，包括失败或未决的结果。被触到的上限是一个诊断项
  （`SC-ENG-…`），绝不是静默截断。

## 8. 黄金示例（The golden example）

这是首次使用者应当能写出的 API。它是设计目标，不是已编译的代码；其中每个名字都出现在
术语表里。

```java
// 1. A blueprint: a shell, a mouth, an interior bay, an anchor. No stage is declared.
Blueprint furnace = Blueprint.builder(NamespacedId.of("mymod", "huge_furnace"))
        .bounds(Cuboid.of(0, 0, 0, 7, 6, 7))
        .bay(NamespacedId.of("mymod", "mouth"), Cuboid.of(3, 0, 0, 5, 2, 1),
                Expectations.allOf(
                        Expectations.block(NamespacedId.of("minecraft", "iron_bars")),
                        Expectations.trait(Traits.ITEM_INVENTORY)),
                Coverage.ONE_SUBJECT, Footprint.Bound, Sharing.EXCLUSIVE, "the opening")
        .bay(NamespacedId.of("mymod", "wall"), Cuboid.of(0, 0, 6, 7, 6, 7),
                Expectations.block(NamespacedId.of("minecraft", "bricks")),
                Coverage.EVERY_CELL, Footprint.Box, Sharing.EXCLUSIVE)
        .bay(NamespacedId.of("mymod", "interior"), Cuboid.of(1, 1, 1, 6, 5, 6),
                Expectations.air(),
                Coverage.EVERY_CELL, Footprint.Box, Sharing.EXCLUSIVE)
        .anchor(new AnchorSpec(AnchorRoles.FRONT, NamespacedId.of("minecraft", "facing"), "east"))
        .orientation(OrientationPolicy.builder(AnchorRoles.FRONT)
                .allowTurns(Turn.NONE, Turn.CLOCKWISE, Turn.HALF, Turn.COUNTERCLOCKWISE)
                .uprightOnly()
                .build())
        .claim(Sharing.EXCLUSIVE)
        .derive(NamespacedId.of("mymod", "interior_volume"), Arguments.empty())
        .build();

// 2. One request for this candidate: `lease` is the host's capture, and the same request is settled later.
MatchRequest request = MatchRequest.at(lease, furnace, LatticePoint.of(10, 64, 10))
        .budget(Budget.of(4_096, 64))
        .build();
MatchResult probe = kernel.check(request);          // read-only: no claim, no frame

// 3. Exhaustive handling; no exception, no Optional.
switch (probe) {
    case MatchResult.Failed failed -> renderer.render(failed.findings());
    case MatchResult.Dropped dropped -> log(dropped.cause(), dropped.findings());
    case MatchResult.Undecided undecided -> log(undecided.reason(), undecided.findings());
    case MatchResult.Admitted admitted -> {
        // `settle` derives the rows itself and takes `admitted` over, closing it on every path.
        MatchResult registered = kernel.settle(request, admitted);
        if (registered instanceof MatchResult.Formed formed) {
            StructureContext ctx = kernel.derive(formed, furnace.derivations(),
                    Budget.of(1_024, 16));
            Integer volume = ctx.get(ContextKeys.INTERIOR_VOLUME);   // null when unproduced
            RuntimeState state = RuntimeState.of(ctx, List.of(), formed.generation());
        }
    }
    case MatchResult.Formed formed -> { /* already registered */ }
    case MatchResult.Deferred deferred -> scheduler.retry(deferred.token());
}
```

数据优先的调用方用一份文档替换第 1 步，得到同一个 `Blueprint`：

```java
CompileResult compiled = kernel.compile(document);          // DESIGN: validate + assemble
if (compiled.blueprint() != null) {
    MatchResult result = kernel.check(MatchRequest.at(lease, compiled.blueprint(), origin)
            .build());
} else {
    renderer.render(compiled.findings());
}
```

该示例说明了什么：

- 使用者从未看到 `WorldView` 实现、会话、步骤开销或声明预留。
- 引擎拥有每个检查的计划与阶段；调用方两者都不指定，只读的 `check` 廉价到可以任意频率调用。
- 朝向变换策略是声明式的，且不出现任何控制器实体。
- 派生值是类型化的、可为缺失；它用一个键来请求，而不是一个字符串。
- sealed 结果迫使调用方把“无结论”与“错误”分开处理。

## 9. 附加包示例（The addon example）

附加包作者写一个期望、注册一个工厂，且从不触碰引擎。

```java
public final class HatchExpectation implements Expectation {
    public static final BindingKey<OccupantRef> HATCH = BindingKeys.of("hatch", OccupantRef.class);

    @Override
    public ExpectationMeta meta() {
        return new ExpectationMeta(NamespacedId.of("mymod", "hatch"), 4, Set.of(Traits.ITEM_INVENTORY));
    }

    @Override
    public Reply test(ExpectationContext ctx) {
        OccupantSnapshot occupant = ctx.occupantAt(ctx.cells().min());
        if (occupant == null) {
            return Reply.unreadable(Finding.builder(Codes.UNREADABLE_CELL)
                    .stage(ctx.stage())
                    .label(ctx.locus(), "this cell was not readable")
                    .build());
        }
        if (!occupant.has(Traits.ITEM_INVENTORY)) {
            return Reply.mismatch(Finding.builder(Codes.EXPECTED_INVENTORY)
                    .stage(ctx.stage())
                    .label(ctx.locus(), "expected an inventory-bearing hatch")
                    .note("found " + occupant.descriptor().type())
                    .build());
        }
        return Reply.match(Bindings.of(HATCH, occupant.ref()));
    }
}
```

三种应答分支是刻意区分的：`unreadable` 不是 `mismatch`，且只有 `match` 产生绑定。对
缺失的特征返回 `mismatch` 的谓词，是在宣称快照并不具备的知识。

## 10. 反模式（Anti-patterns）

| 反模式 | 拒绝原因 |
| --- | --- |
| `boolean isStructure(...)` 门面 | 丢失诊断项、阶段、声明与上下文；调用方要重新遍历世界。 |
| 用 `Map<String, Object>` 承载上下文 | 破坏 R-13；一个拼写错误会变成运行时 `null`。 |
| 控制器 `Entity` 参数 | 违反 R-04 与 I-5；锚点是一个角色，不是生物。 |
| `throw new StructureException("not formed")` | 结构性失败是 `Failed`/`Undecided`，不是异常。 |
| `Predicate<BlockState>` 捷径 | 使方块获得特权（R-01）并绕过快照（I-4）。 |
| 公开的 `MatchSession` setter | 扩展得到的是 `ProbeSession`；只有引擎写入。 |
| 上下文上的 `getOrThrow` | 仅当前置检查已证明存在时才用 `require`；否则用 `@Nullable` + 一个诊断项。 |
| 对 sealed 结果写 `default` 分支 | 隐藏新增分支；穷尽 switch 才是契约。 |

## 11. 参考代码标准（Reference-sketch standard）

模块文件中的参考代码在满足以下条件时被接受：

1. 它命名的每个类型都在术语表中，或在同一节中声明。
2. 填上函数体后，它能在冻结的名字下编译；省略的函数体用 `// …` 或一行注释标记，绝不用
   `throw new UnsupportedOperationException`。
3. 它展示错误路径，而不只是顺路径：至少一个 `Reply.mismatch`/`Finding` 和一个
   `Undecided`。
4. 对它处理的每个 sealed 类型都使用穷尽 switch。
5. 它的 `Use cases` 一节点名一个具体的附加包（一个舱口、一扇门、一座巨型熔炉）和一个具体的
   宿主（一条命令、一次数据包重载、一根建造杖）。

若一份参考代码在不改动冻结签名的前提下无法通过编译，那它就是一个**待决问题（Open
question）**，而不是悄悄改签名的理由。
