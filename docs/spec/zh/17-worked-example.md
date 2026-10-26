# 17 — 完整示例：巨型熔炉（Worked example: the huge furnace）

> 一个结构，每个阶段，每种输出。数字是设计的算术，不是截图：7×6×7 边界 = 294 格；
> 外壳 194、内腔 100、炉口 2，且外壳与内腔恰好覆盖整个边界。内腔是 5×4×5，因此
> `capacity = 4 × 100 = 400`。示例刻意小到可以手工核对，又大到足以覆盖多格占地
> （multi-cell footprint）、一次带聚合的区段计算、一个自有内腔、一次声明冲突和一次修复。

## 1. 结构（The structure）

```
      x0          x6
y 5   ▓ ▓ ▓ ▓ ▓ ▓ ▓    roof (shell)
  4   ▓ · · · · · ▓
  3   ▓ · · · · · ▓    cavity: x1..5, y1..4, z1..5 (100)
  2   ▓ · · ░ · · ▓    mouth:  x3, y1..2, z0 (2)
  1   ▓ · · ░ · · ▓
  0   ▓ ▓ ▓ ▓ ▓ ▓ ▓    floor (shell)
```

炉口是正面（`z = 0`）一个 1×2 的开口，内腔是外壳围起来的空气盒。外壳是 7×6×7 的盒体减去内腔：294 − 100 = 194 格。锚点是定义原点 `(0,0,0)` 处的那个外壳方块，它的 `facing` 属性指明正面，因此不存在控制器实体，也不需要搜索它。

## 2. 定义，代码优先（Definition, code-first）

```java
Catalogs.Builder draft = Catalogs.builder();

// VOLUME takes one contribution per section, folded at the barrier.
ContextKey<Long> VOLUME = ContextKeys.register(draft, new ContextKey<>(
        NamespacedId.of("mymod", "volume"), Long.class, Retention.CONTEXT, Merges.sum(), null));
// CAPACITY is written once, after the fold; neither key has a whenAbsent.
ContextKey<Long> CAPACITY = ContextKeys.register(draft, new ContextKey<>(
        NamespacedId.of("mymod", "capacity"), Long.class, Retention.CONTEXT, null, null));

// The cavity: EVERY_CELL over its 100 cells, EXCLUSIVE, and it owns them because the volume is empty.
Bay interior = new Bay(NamespacedId.of("mymod", "interior"), Cuboid.of(1, 1, 1, 5, 4, 5),
        Coverage.EVERY_CELL, Sharing.EXCLUSIVE, new Footprint.Box(),
        new Requirement(List.of(interiorAir), List.of(volume, capacity)), "the hollow");

// The mouth: two cells inside the shell, asserted and owned as positive footprints.
Bay mouth = new Bay(NamespacedId.of("mymod", "mouth"), Cuboid.of(3, 1, 0, 1, 2, 1),
        Coverage.EVERY_CELL, Sharing.EXCLUSIVE, new Footprint.Bound(),
        new Requirement(List.of(mouthBars, mouthFacing), List.of()), "the opening");

AnchorSpec front = new AnchorSpec(NamespacedId.of("mymod", "front"),
        NamespacedId.of("minecraft", "facing"), "east");
```

`interior` 舱位的 `Requirement` 同时持有两项计算。`volume` 是一个区段 `Computation`：`output()` 是 `VOLUME`，`region()` 是内腔，`inputs()` 为空，它每个区段运行一次，每次运行调用 `draft.contribute(VOLUME, partial)`。`capacity` 是一个聚合 `Computation`，其 `inputs()` 是 `Set.of(VOLUME)`；它用 `ctx.read(VOLUME)` 读取折叠后的值，并用 `draft.put` 写入 `CAPACITY`。需要该值的检查把 `Focuses.context(VOLUME)` 作为其焦点，因此它的 `Expectation<Long>` 看到的是折叠值（此处为 100），绝不会是零；被拒绝的区段以 `Outcome.Missing` 到达它，检查将其报告为 `UNREADABLE`。

外壳是一个带洞的盒体 —— 194 格，内腔被挖去 —— 因此它**不是**一个 `Bay`：降级（lowering）为每个面盒发出一个舱位，id 形如 `#box@x,y,z:sx,sy,sz`，各自 `EVERY_CELL`、`EXCLUSIVE` 且 `Box`（`21` §4）。六个舱位，六个盒，没有任何 id 跨越六个盒。

`build()` 以合法性表为准，而不是构建器的口头承诺：内腔上的 `EVERY_CELL` + `Box` 合法，因为每个格都被断言且全部被声明；没有舱位的检查是 `SC-CHK-0107`；而 interior 舱位的覆盖范围会在世界存在之前，对检查的静态判定格校验（`SC-CHK-0106`）。

**锚点与朝向变换。** 锚点格是定义原点，即格中的 `(0,0,0)`，解析器只读取那一个世界格。它从方块的 `facing` 属性得到一个 `Heading`，并返回满足下式的唯一 `Siting`

```
candidate.apply(authoredFront) == anchorHeading
```

本文件的 `authoredFront` = `north`。只有一个方向，没有打破平局：不满足该等式的候选不是候选，朝向变换是锚点的函数，而不是寻找锚点的输入。

引擎的步骤是对 R-08 的答复 —— 一个廉价的 `PROBE`（边界、锚点、普查）、一个断言各舱位并运行计算的 `FORM`，以及结算 —— 而且只能通过两个入口到达它们：只读的 `check` 与写入的 `settle`，后者独自写入声明并宣告结构成型。

## 3. 定义，数据优先（Definition, data-first）

同一结构写成 compact 文档，schema 2 —— 即 compact profile，因此它的舱位名与区域名是裸名，在唯一的隐式 `main` 摆放内解析（`mouth` 编译为 `main:mouth`，`10` §4.5 第 4 步，Q-D3）：

```json
{
  "schemaVersion": 2,
  "id": "mymod:huge_furnace",
  "bounds": [0, 0, 0, 7, 6, 7],
  "shell": [{"box": [0,0,0,7,6,7], "block": "minecraft:blast_furnace"}],
  "cavity": [[1,1,1,5,4,5]],
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
     "focus": {"kind": "block", "cell": [3,1,0]},
     "expect": {"kind": "state", "property": "facing", "value": "east"}}
  ],
  "computations": [
    {"id": "volume", "phase": "section",
     "output": "mymod:volume", "region": "interior",
     "kind": "count", "args": {"mode": "cells"}},
    {"id": "capacity", "phase": "aggregate",
     "output": "mymod:capacity", "inputs": ["mymod:volume"],
     "kind": "mymod:scale", "args": {"factor": 4}}
  ],
  "context": [
    {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
    {"id": "mymod:capacity", "type": "long", "retention": "context"}
  ],
  "anchors": [{"role": "front", "property": "facing", "value": "east"}],
  "orientation": {"front": "north"}
}
```

```java
CompileResult compiled = kernel.compile(document);       // DESIGN: validate + assemble
if (compiled.blueprint() == null) {
    renderer.render(compiled.findings());                 // SC-DOC / SC-SPI, with spans
    return;
}
Blueprint furnace = compiled.blueprint();
```

编译出的值与 §2 的形状相同；有一个测试断言两条路径相等。

- 盒体是 `[originX, originY, originZ, sizeX, sizeY, sizeZ]`，因此 `cavity` 是 `[1,1,1,5,4,5]` = 5×4×5 = **100** 格，`bounds` 是原点处的 7×6×7 盒。
- `volume` 在 `interior` 区域上按格计数（`"mode": "cells"`），因此是 100；`capacity` 把折叠后的贡献乘以 4，因此是 **400**。
- `phase: section` 要求 `output` 键携带 `merge`；`phase: aggregate` 禁止它。两个键都是 `Retention.CONTEXT`，因此二者都发布到同一个 `FORM` 帧中，折叠是对按 `SectionKey` 排序的贡献执行 `Merges.sum()`。
- 没有 `whenAbsent`：`interior` 中一个被拒绝的区段会让 `volume` 是 `Missing`，随后 `capacity` 也是 `Missing` —— 绝不会是一个更小的数字。
- 缺少 `count` 工厂是 `DESIGN` 阶段的 `SC-SPI-0002`；缺少某个片段是 `SC-DOC-0003`；两者都不抛出。
- `scale` 是一个附加组件计算，因此带命名空间书写（`mymod:scale`，注册在 `Catalogs.computations()` 中）；内核自身只提供 `count`、`sum`、`volume`、`distinct` 与 `bind`。`22` §12.3 把同一条目写作 `"kind": "scale"`，那样会在默认命名空间中解析，而在那里什么也没有注册。

## 4. PROBE：廉价拒绝（PROBE: cheap rejection）

```java
// The host captured the plan's closure once, for this siting; the record owns it from here.
SnapshotLease lease = capture.lease(furnace, at);

try (MatchResult probe = kernel.check(MatchRequest.at(lease, furnace, at)
        .budget(Budget.of(256, 16))
        .build())) {
    if (probe instanceof MatchResult.Admitted admitted) {
        // bounds, anchor and census passed; a child handle, the plan and the revision it
        // read — no instance and no rows written
    }
}
```

世界在该安放位原点处有外壳。`BOUNDS` 通过（捕获覆盖全部 294 格），`CENSUS` 观测到声明的各项计数，`ANCHOR` 读取原点格并解析出一个 `Siting`。`Admitted` 刻意不是结构：它有阶段、安放位、那些廉价检查的产物、一个租约、`settle` 将会写入的计划以及它读到的账本修订，而 `derive` 会拒绝它。宿主只用它来决定是否继续走向 `settle`。

若原点深入墙内两格，`BOUNDS` 仍会通过，但锚点读取找不到任何 `facing` 值；结果是带 `SC-FOC-0101` 的 `Failed`，其主标签是被读取的那个 `WORLD` 格。若炉口所在区段是 `DENIED`，同一单元是 `Undecided`，带 `SC-FOC-0301` 指明该区段及其状态 —— 被拒绝的区段绝不是空气。

## 5. 结构：先 `check` 后 `settle`（The structure: `check` then `settle`）

`check` 在一个 worker 上对已密封的快照运行，且不写入任何内容：它返回 `Admitted`，携带该计划与它读到的账本修订。`settle` 在服务端线程上运行，是唯一写入声明的调用。

```java
MatchRequest request = MatchRequest.at(lease, furnace, at)
        .budget(Budget.of(4_096, 64))
        .build();

MatchResult matched = kernel.check(request);            // read-only: no claim and no frame

if (!(matched instanceof MatchResult.Admitted admitted)) {
    retire(matched);                     // host: render the arm's findings, close its handle
    return;
}

// `settle` consumes the admitted result: it closes that handle on every path, and a null `planned()`
// (no ledger to write into) is `Undecided(LEDGER_UNAVAILABLE)`.
MatchResult result = kernel.settle(request, admitted);  // the only writer

if (!(result instanceof MatchResult.Formed formed)) {
    retire(result);
    return;
}

try (formed) {
    assert formed.generation() == 1;
    assert formed.contextRevision() == 1;              // the FORM frame this attempt published
    assert formed.ledgerRevision() == ledger.revision();
    assert formed.claims().rows().size() == 294;       // 194 shell + 100 cavity
    assert formed.findings().isEmpty();
}
```

世界遍历保持声明顺序：炉口的两个格在先，然后是外壳的 194 格，然后是内腔的 100 格。炉口匹配一个铁栏杆方块及其 `facing`；外壳匹配声明的方块；内腔是空气。同一次遍历还对 `interior` 区域的每个区段运行一次 `volume`：每次运行调用 `draft.contribute(VOLUME, partial)`，屏障按 `SectionKey` 排序这些贡献并用 `Merges.sum()` 折叠为 100，聚合 `capacity` 用 `draft.put` 写入 `CAPACITY = 400`，两个键都发布到修订号 1 的 `FORM` 帧中。针对折叠值的检查用 `Focuses.context(VOLUME)` 读取它；一个被拒绝的中间区段让该键缺席、检查为 `UNREADABLE`，绝不是一次缩短的求和。

`settle` 在服务端线程上、在它读到的那个账本修订号上，把六个外壳面盒舱位（194 格）与内腔（100）写为 `Box` 声明，外加炉口的两条 `Bound` 行 —— 全部 `EXCLUSIVE`。炉口的两条 `Bound` 行落在外壳的 `Box` 行内部：一个所有者在一个安放位上，因此矩阵把它们配对而不产生冲突，集合是 294 个不同格。只有结算返回 `Settled` 之后 `Formed` 才存在，它携带 `generation`、`contextRevision` 与 `ledgerRevision`，使消费者能把它与更晚的一个区分开。

若第二座熔炉已对某一外壳格持有互斥声明，`settle` 就是带在位行的 `Failed(SC-CLM-0002)`；该诊断项的主标签是 `WORLD` 中的那个格，次标签是两个所有者的声明。第一座熔炉的格不受影响，也没有写入任何内容。

## 6. 实例：租约、上下文、代际（The instance: lease, context, generations）

```java
// §5's try closed the child handle; the instance's own reference keeps the capture alive.
InstanceId instance = formed.id();
MatchOutcome outcome = formed.outcome();

InstanceRecord record = registry.instance(instance);       // owns the capture for the instance
StructureContext values = StructureContext.at(instance);   // frames only, never the lease
Outcome<Long> capacity = values.require(CAPACITY);         // Found(400), never a short sum
```

`InstanceRecord` 在实例的整个生命周期内拥有该捕获；`Formed` 与 `Admitted` 持有子句柄，实例自身的引用在句柄关闭后仍让视图存活。需要自己保留的查看者自行申请 —— `retain(PinBudget)` 计入 `retainedBytes`，并可以以 `SC-MC-0502` 拒绝。`StructureContext.at(instance)` 只读取已发布的帧；它从不读取租约，因此上下文不可能通过持有一个租约而比捕获活得更久。提供者通过 `RemedyContext`（§8）得到租约，那个租约属于注册表，而不属于某个结果。

宿主把 `SETTLE` 帧作为 `RuntimeState` 持久化在 `instance` 之下；内核不持久化它。重载时比较 `generation` 与实例的复查计数器，而不是时间戳，而升级是在同一单写者规则下的 `RuntimeState.upgrade(key, value)`。

## 7. 针对性复查：一个墙格发生变化（A targeted re-check: one wall cell changes）

```java
ChangeSet change = new ChangeSet(
        Set.of(LatticeKey.of(LatticePoint.of(12, 67, 16))),   // local (2,3,6) at this siting
        Set.of(), Set.of(), Set.of(), Set.of(), 41L);
MatchResult again = kernel.check(MatchRequest.at(record.lease(), furnace, at)
        .change(change).build());
```

外壳单元的读取范围包含被改动的格，因此它的 `cellStamp` 移动，该单元重新运行；炉口与内腔单元被复用，因为它们的读取闭包与本次变更不相交。复用是被带入下一次尝试的结果，绝不是本次快照内部的混合：重新运行会重新捕获。结果是带 `SC-EXP-0001`（期望声明的外壳方块，实为空气）的 `Failed`，并在该格上带一个 `WORLD` 标签。

`generation` 不变，因为实例没有重新成型：一次失败的复查让先前的 `Formed` 保持原样。只有一次新的结算（来自新匹配的新 `generation`）或一次由丢弃驱动的重新发出（`generation + 1`，产物相同，`22` §9.4）才会移动它。宿主把该实例标记为不健康，并提供一次修复。

## 8. 修复与预览：同一方案，两种投影（Repair and preview: the same solution, two projections）

```java
StructureContext current = StructureContext.at(instance);
if (current.get(CAPACITY) == null) { return List.of(); }          // in a provider: cannot help

ClaimView claims = ledger.read(furnace.bounds().translate(at));   // frozen at its revision
RemedyContext ctx = new RemedyContext(furnace, at, claims, ledger.revision(),
        outcome, current, RemedyOptions.defaults());

Remedy.Repair repair = (Remedy.Repair) kernel.solve(again.findings(), ctx)
        .stream().filter(r -> r.kind() == RemedyKind.REPAIR).findFirst().orElseThrow();

ValidationReport dry = PlanValidator.validate(repair.plan(), ctx);   // rows 1..9
if (!dry.applicable()) { dry.findings().forEach(sink::accept); return; }

try (EditTransaction tx = executor.begin(record.lease().view(), at, claims, ledger.revision(),
        repair.plan().digest())) {
    repair.plan().steps().forEach(tx::apply);
    tx.commit();
}
ApplyReport report = tx.report();                                  // report.context() is post-state
```

该计划在缺失的格上有一个 `Place`，带期望的外壳描述符、档位 `MACHINE_APPLICABLE`，以及“该格是空气”的前置条件。试运行读取该格，检查没有其他所有者持有互斥声明，然后把计划折叠到捕获的视图上，重新运行提供者读取过的每个键：会改变 `capacity` 的计划在 `begin` 之前以 `SC-RMD-0309` 被拒绝。`begin` 重新检查计划摘要与账本修订号，因此在试运行与施加之间落地的结算会得到 `SC-RMD-0303`，事务从不开启。随后 `report.context()` 是在施加后捕获之上的新 `RemedyContext`，第二轮提供者从那里开始。

`repair.preview(again.findings())` 产生客户端绘制的 `OverlayModel`：该格为 `MISSING`；外壳的其余部分为 `PRESENT`；炉口与内腔不带标记。施加该计划会把 `MISSING` 标记变为 `PRESENT` —— 预览与修复不可能不一致，因为两者来自同一个计划。

若该格被另一条互斥声明持有，`PlanValidator` 返回 `SC-RMD-0303`，执行器拒绝执行；若该格是 `DENIED`，它返回 `SC-RMD-0301`，且不提供该修复。单独的预览仍然有用：它从不写入，也从不需要声明。

## 9. 门：一个异形舱位（The door: a heteromorphic bay）

一个两格高的门是一个舱位，不是两个：

```java
Bay door = new Bay(NamespacedId.of("mymod", "door"), Cuboid.of(3, 0, 0, 1, 2, 1),
        Coverage.ONE_SUBJECT, Sharing.EXCLUSIVE, new Footprint.Bound(),
        new Requirement(List.of(DoorExpectations.door(Heading.NORTH, DoorHalf.LEFT)),
                        List.of()),
        null);
```

焦点是 `door`，因此其主体（subject）是一个 `DoorView` —— 两个 `OccupantRef` 与一个合并状态 —— 而期望是对同一类型书写的，绝不是对 `BayView`。焦点在 `Found.subjects` 中返回两半，这是构建该绑定的唯一方式。

该舱位是 `ONE_SUBJECT`：`bay.cells` 必须等于两半占地的并集，声明就是那两个正占地。铰链不一致是一条带两个标签的诊断项 —— `PRIMARY` 在第一个不一致的半上，`SECONDARY` 在另一半上（`13` §4.4）—— 不是两条诊断项，也不是两个主标签。舱位原子地声明这两格；半扇门不可能被半声明。引擎从不看到体素形状；矩形是唯一的跨格类型（R-06）。

## 10. 示例证明了什么（What the example proves）

| 需求 | 证据 |
| --- | --- |
| R-01 元素中立性 | 炉口是一个方块加其状态；箱子、牛和嵌套结构都会使用同一个 `OccupantSnapshot` |
| R-03 结构 → 上下文 | `volume` 是一个带 `Merges.sum()` 的 `ContextKey<Long>`，按区段贡献；`capacity` 是一个在屏障之后写入一次的 `ContextKey<Long>`；两者都在 `FORM` 发布，并经 `StructureContext.at(instance)` 读取 |
| R-04 无控制器实体 | 锚点是在定义原点读取的一个角色；内核路径中不出现任何 `Entity` |
| R-05 朝向变换 | 一条等式，`candidate.apply(authoredFront) == anchorHeading`，以及一次格读取 |
| R-06 异形 | 门的舱位是一个双格焦点，覆盖为 `ONE_SUBJECT` |
| R-07 诊断 | 每次失败都是一个 `Finding`，带一个码、一个主标签，以及一个 `KEY` 或格定位域 |
| R-08 代际 | 廉价的 `PROBE` 返回 `Admitted`；`FORM` 遍历在修订号 1 发布；`settle` 写入声明；针对性复查只重新运行被触及的单元 |
| R-09 数据驱动 | §3 从 schema 2 编译出同一个蓝图，包括各项计算及其阶段 |
| R-10 单一内核 | 示例中的每个谓词、计算和补救方案都是一条目录条目；内核只提供通用的那些 |
| R-11 声明 | 294 个 `EXCLUSIVE` 格在 `settle` 结算，外壳与内腔都是 `Box`；第二座熔炉以 `SC-CLM-0002` 冲突 |
| R-12 补救方案 | 修复与叠加层是同一个计划的两种投影，若计划会改变它读取过的键则被拒绝 |
| R-13 类型安全 | sealed 的 `MatchResult`/`Remedy`、类型化的 `ContextKey`/`TraitKey`，没有 `Map<String,Object>` |
| R-14 外部简单 | 首次使用路径是 `siting`、只读的 `check`、写入的 `settle`、一个 switch 和一次 `StructureContext.at` |
| R-15 可证伪 | 各项计数、阶段转换、`Missing` 传播与回滚都可以在带假视图和假声明的 `kernelTest` 中校验 |
