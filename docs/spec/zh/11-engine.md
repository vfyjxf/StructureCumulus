# 11 — 引擎（Engine，U7，`kernel.engine`）

> 一次匹配：请求固定问题，会话累积答案，密封的结果以六个分支报告它。服务于 R-01、R-04、R-05、
> R-08、R-11、R-13；涉及 I-4、I-5、I-6、I-7、I-10、I-15。

## 1. 范围（Scope）

负责尝试（`MatchRequest`、`MatchPolicy`、`MatchSession`、`MatchResult` 及六个分支、
`UndecidedReason`）、检测输出（`MatchOutcome`、`BayBinding`、`SeamBinding`、`Census`）、廉价预读
（`PrescreenIndex`、`AnchorCandidate`、`Fingerprint`）、增量重检（`ReadSet`、`ChangeSet`、
`InvalidationIndex`、`InstanceRef`）、单元顺序、折叠、门禁、花费账本、缓存键与下一次尝试。不负责
`Reply` 语义（`07`）、`Bay` 期望（`07`、`09`）、冲突（`08`）、派生（`12`）、诊断项（`13`）、
补救方案（`14`）、空间代数（`05`）、`CheckPlan`（`09`）或调度。它除通过 `settle` 外不写入任何
东西、不调度、不读取时钟，也不因任何结局抛异常。宿主执行模型、快照捕获与脏刷新属于 `20`；本单元
是该层调用的同步内核调用。`Budget` 不属于本单元：槽位拥有 `InstanceBudget` 并按引用传入，因此请求
无法开启、重置或放宽它。

## 2. 术语（Vocabulary）

每个名称只在 `01-glossary.md` §I 定义一次，本单元拥有全部这些名称，且不添加同义词：
`MatchRequest`（snapshotInfo、blueprint、siting、lease、policy、budget、pins、record、owner）、
`MatchPolicy`、`MatchSession`、`MatchResult` 及 `Admitted` / `Dropped` / `Formed` / `Failed` /
`Undecided` / `Deferred`、`UndecidedReason`、`MatchOutcome`、`BayBinding`、`SeamBinding`、
`Census`、`PrescreenIndex`、`AnchorCandidate`、`Fingerprint`、`ReadSet`、`ChangeSet`、
`InvalidationIndex`、`CacheKey`、`InstanceRef`。`Spend` 是工作单元取值（`20` §7），也是一次尝试
唯一报告的账本；`Budget` 属于槽位，永不属于本单元。不存在时钟类型。

术语表增量：请求携带 ticket、已解析的安放位、租约与一个预算引用；`Formed` 携带 `generation`、
`contextRevision`、`ledgerRevision` 与一个子租约句柄；`Admitted` 与 `Formed` 是
`AutoCloseable`——由 `04` §8 与 `22` §5.5 强制要求。单元驱动的贡献面是带其 `Merge` 的
`ContextKey`、带 `phase()` 的 `Computation`、`ContextDraft.contribute` 与 `ContextView.require`
（`22` §6.4、§8.3）。

## 3. API 设计（API design）

`build()` 仅在程序员错误时抛 `IllegalArgumentException`（`DESIGN` 阶段没有已声明的计划、更新器
为 null）；其他任何错误调用都产出一条诊断项。会话的修改器是包私有（package-private），因此扩展
无法绑定、预留、报告或放宽上限（doctrine §10）。请求是尝试的确定性元组，因此被冻结；会话累积花费
与绑定，因此不被冻结。只有 `Formed` 携带产物与声明，且 `MatchOutcome` 有序：舱位按 id，接缝按 id，
声明先按格点再按舱位。

```java
public record MatchRequest(Blueprint blueprint, Siting siting,
                           SnapshotLease lease, MatchPolicy policy, InstanceBudget budget,
                           PinBudget pins, InstanceRecord record, OwnerId owner,
                           @Nullable Ticket ticket) {}   // ticket = slot state, not capture state

public MatchResult check(MatchRequest request);                       // read-only
public MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);  // the only writer

public record MatchPolicy(int maxSectionsPerUnit, CacheMode cache, boolean parallel) {
    public static MatchPolicy defaults();          // (8, OUTCOMES, false); a host may only narrow
}

public sealed interface MatchResult extends AutoCloseable
        permits MatchResult.Admitted, MatchResult.Dropped, MatchResult.Formed, MatchResult.Failed,
                MatchResult.Undecided, MatchResult.Deferred {
    Stage stage();  FindingBundle findings();

    @Override default void close() {}              // a result that holds no handle closes to nothing

    record Admitted(Stage stage, Siting siting, MatchOutcome outcome,
                    @Nullable ClaimPlan planned, long ledgerRevision,
                    SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }     // this child handle only
    }
    record Dropped(Stage stage, DropCause cause, List<Finding> findings) implements MatchResult {}
    record Formed(Stage stage, InstanceId id, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long generation, long contextRevision, long ledgerRevision,
                  SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }
        public ReadSet readSet();                  // union of outcome.stepReads()
    }
    record Failed(Stage stage, List<Finding> findings) implements MatchResult {}
    record Undecided(Stage stage, UndecidedReason reason, List<Finding> findings,
                     @Nullable MatchOutcome partial, @Nullable RetryToken retry) implements MatchResult {}
    record Deferred(Stage stage, RetryToken token, List<Finding> findings) implements MatchResult {}
}
// MatchSession: idempotent run(), spend(), remaining(), readSet(), probe(); writes are internal.
```

安放位在请求存在之前就已解析：请求里没有 `Place`，`run()` 内部也没有朝向搜索（`22` §4.4）。
`ticket` 是合并令牌，`lease` 是一次移交——从未被派发的请求得不到租约，而被派发的请求持有它，直到
结果或调度器把它收回。`blueprint` 是一个 `BlueprintId`，在计算单元顺序之前，对 `apply` 时冻结的
目录解析一次。预算是槽位的对象，按引用传入：`run()` 对它花费，且永远无法重置计数器。
`MatchPolicy.defaults()` 把 `maxSectionsPerUnit` 保持为 8，足以覆盖区域横跨区块角落的单元；该上限
约束的是 `closureDigest` 所哈希的 `(SectionKey, contentDigest)` 列表（§4.7），而不是正确性。


## 4. 语义（Semantics）

### 4.1 算法（The algorithm）

```java
MatchResult check(MatchRequest request) {          // read-only; worker over one snapshot
    Stage stage = stageOf(request);           // the engine's pipeline assigns it; no field names one
    CheckPlan plan;
    try { plan = request.blueprint().expand(stage); }                   // 1 expand the stage's plan, pure
    catch (IllegalArgumentException rejected) { return seal(Failed, SC_ENG_0102); }
    if (plan.checks().isEmpty()) return seal(Undecided, NO_JUDGING_UNIT);  // nothing judges
    PrescreenIndex index = PrescreenIndex.of(request.lease().view(), plan, request.siting());
    runSectionPass(plan);                                             // 2 sections, then the barrier
    runAggregatePass(plan);                                           // 3 aggregates, Kahn order
    for (Check<?> check : plan.checks()) {                            // 4 checks, declaration order
        ReadScope scope = check.reads(owner(check).cells());
        if (!slot.tryCharge((int) scope.cells().volume(), sectionCount(scope.cells())))
            return seal(Undecided, BUDGET_EXHAUSTED);
        tally.add(check, check.run(focusContext(check, index)));
    }
    switch (CaptureGate.evaluate(request.lease())) {                  // 5 the one check
        case CHANGED    -> return seal(Dropped(SNAPSHOT_CHANGED), SC_ENG_0003);
        case SUPERSEDED -> return seal(Dropped(CAPTURE_INVALID), SC_ENG_0006);
        case CURRENT    -> { }
    }
    Tally.Fold fold = tally.fold();                                   // 6 fold
    if (fold.failed())    return seal(Failed, fold.findings());
    if (fold.undecided()) return seal(Undecided, fold.reason(), fold.findings());
    ClaimPlan planned = fold.plan();                                  // 7 the plan: bindings + revision, no rows
    ClaimPlan.Matched matched = (ClaimPlan.Matched) planned;
    ClaimPlanner.Derived d = ClaimPlanner.derive(request.world(), request.blueprint(), request.siting(),
            request.owner(), matched.bindings());                      // 8 derive the rows, purely
    ClaimView held = ledger.read(d.staticFootprint());                 // 9 the ledger, read only
    if (!held.available()) return seal(Undecided, LEDGER_UNAVAILABLE);
    if (ClaimMatrix.conflicts(d.rows(), held.rows()))                  // a prediction, not a conclusion
        return seal(Failed, SC_CLM_0002, held.rows());
    return seal(Admitted, planned, held.revision());                   // nothing written
}

MatchResult settle(MatchRequest request, MatchResult.Admitted admitted) {  // server thread
    try (admitted) {                                                   // the admitted handle dies here, always
        switch (CaptureGate.evaluate(request.lease())) {               // 3a the capture still holds
            case CHANGED    -> return seal(Dropped(SNAPSHOT_CHANGED), SC_ENG_0003);
            case SUPERSEDED -> return seal(Dropped(CAPTURE_INVALID), SC_ENG_0006);
            case CURRENT    -> { }
        }
        if (!(admitted.planned() instanceof ClaimPlan.Matched matched))
            return seal(Undecided, LEDGER_UNAVAILABLE);                // 3b no ledger to write into
        if (!bindingsHold(request, matched)) return seal(Failed, SC_CLM_0005);   // 3b bays, capture, kinds, cells
        ClaimPlanner.Derived d = ClaimPlanner.derive(request.world(), request.blueprint(), request.siting(),
                request.owner(), matched.bindings());                  // 3c the only row factory, pure
        ClaimVerdict v = facade.settle(new SettleRequest(request.world(), request.owner(), request.siting(),
                d.rows(), admitted.ledgerRevision()));                 // 3d one CAS over (world, owner, siting)
        if (v instanceof RevisionMoved moved)                          // one re-read, inside the call
            v = retrySettle(d, moved);
        if (v instanceof RevisionMoved) return seal(Undecided, LEDGER_MOVED);
        if (v instanceof ClaimConflict c) return seal(Failed, SC_CLM_0002, c.rows());
        return seal(Formed, d, (Settled) v);                           // Formed only after Settled
    }
}
```

```
scheduler ─request(ticket,siting,lease)─► check(worker) ─fold─► Admitted(planned, ledgerRevision)
                                                       │
         MatchResult ◄── seal ◄── settle(request, admitted)(server thread, frozen revision)
```

计划单元是一个 `Computation` 或一个 `Check`；计算的 `phase()` 决定它是逐区段运行，还是在屏障之后
运行一次。引擎拥有流水线并挑选它运行的阶段（`stage`），因此请求与 `Check` 都不声明阶段，`Stage` 是
填入结果里的；在一个阶段内，单元按 `(phase, declaration index)` 排序：区段遍历完成，屏障折叠其贡献，然后聚合按
`inputs()` 上的 Kahn 顺序运行，检查则跟随它们所读取的单元（`22` §7.1）。彼此独立的单元保持声明
顺序；成本不是优先级。`check` 从不写入，也从不结算；`settle` 从不匹配。

计费：预算只有两条腿，`cells` 与 `sections`。检查按它声明的读取闭包与触及的区段计费；区段计算
按切片体积加一个区段计费；聚合不计费。每次计费都发生在派发时，无论是否缓存。
`Spend(cells, sections)` 就是整个账本——没有逐单元准入，没有 checks 腿，也没有 retries 腿。


### 4.2 锚点与朝向变换（Anchors and orientation）

锚点格永远是**定义原点**：所读取的世界格是调用方提供的 `siting.origin()`，因此朝向不会循环
（`22` §4.4）。不评分任何候选，也不搜索任何朝向。

```java
record AnchorSpec(NamespacedId role, NamespacedId property, String value) {}

interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
}
```

| 规则 | 契约 |
| --- | --- |
| 读取 | 一个格，即原点；记一个格与一个区段。`AnchorResolver` 只有这个读取范围，没有别的。 |
| 映射 | 该格的属性/值给出一个 `Heading`；安放位是满足 `apply(authoredFront) == anchorHeading` 的唯一摆放。一个方向，一个公式——不满足它的候选不是候选。 |
| 输入 | 请求携带已解析的 `Siting`；`facade.siting(bp, origin)` 是唯一解析它的路径，它在服务端线程上对一次捕获运行。 |
| 无 | 可读但没有满足的映射 → `Failed(SC-ENG-0104)`；格不可读 → `Undecided(UNREADABLE)`。 |

控制器位于别处的结构把原点放在那里；不存在控制器实体（I-5）。

### 4.3 预筛可靠性与 `Fingerprint`（Prescreen soundness and `Fingerprint`）

| 检查 | 拒绝条件 | 结果以及有效结构为何能存活 |
| --- | --- | --- |
| 包围盒 | `footprint ⊄ view.bounds()` | `Undecided(UNREADABLE)`——捕获之外一无所知，甚至不知道它是错的。 |
| 锚点 | 原点格可读，但不给出满足唯一映射方向的朝向 | `Failed`——锚点是蓝图自己对原点处所放之物的声明。 |
| 普查 | 对某声明的类型 `observed < demand` | `Failed`——`demand` 是声明的下界；任何满足各舱位的摆放都会提供这么多。 |

只有当拒绝否定了**蓝图所声明**的东西时才是 `Failed`；当它否定的是*认知*的条件时是 `Undecided`；
启发式（“比上次的砖少”）两者都不是：普查从不与另一次观测比较。没有任何已冻结的类型携带按类型的
需求量（`ExpectationMeta.requires` 是特征，不是计数），因此今天的普查检查只做观测。

`Fingerprint` 覆盖模板——id、bounds、带其 `Coverage`、`Footprint` 与 `Sharing` 的舱位长方体、锚点规格、
朝向策略、需求量，以及每个计划的单元 id——摘要的是规范内容，因此跨宿主稳定。它是缓存键，不是
门禁：命中仍需比较 id 与 bounds，因此碰撞只是多一次未命中。

### 4.4 判定折叠（Verdict folding）

```
Failed     ⇔  mismatch ≥ 1 ∨ claimConflict ≥ 1
Undecided  ⇔  mismatch = 0 ∧ claimConflict = 0 ∧ ( unreadable > 0 ∨ abstain > 0 ∨ untested > 0
                             ∨ no judging unit ∨ cancelled ∨ budget refused )
Formed     ⇔  mismatch = 0 ∧ claimConflict = 0 ∧ unreadable = 0 ∧ abstain = 0 ∧ untested = 0
              ∧ ≥1 judging unit
```

| 规则 | 契约 |
| --- | --- |
| 优先级 | 证伪优先于无知：一个 `MISMATCH` 即给出 `Failed`，即使旁边有不可读格点。 |
| 原因 | `CANCELLED > DIRTY_UNSTABLE > BUDGET_EXHAUSTED > LEDGER_MOVED > LEDGER_UNAVAILABLE > UNREADABLE > ABSTAINED > NO_STEPS`——前五个是*我们停止了*，后三个是*我们什么也没学到*；与单元顺序无关。 |
| 模式 | 不存在失败模式：`MISMATCH` 从不截断折叠，因此 `MatchPolicy` 没有任何旋钮的取值能改变判定、诊断包或 `Spend`（§4.7）。 |
| 不是软失败 | `Failed` 许可会改变世界的修复；`Undecided` 许可重读，把二者合并会为可能正确的结构构造修复。 |

### 4.5 声明与结算（Claims and settlement）

| 时机 | 动作 | 失败 |
| --- | --- | --- |
| 工作线程折叠 | 匹配器为每个绑定了主体的舱位产出一个 `BayBinding`，并产出一个携带这些绑定的 `ClaimPlan.Matched`，连同它读取的修订号；它不派生任何行，不写任何东西，也看不到账本。 | — |
| 折叠不是 `MATCH` | 没有 `ClaimPlan` 离开该次尝试；不铸造任何声明。 | — |
| `check` 读取 | 覆盖派生出的 `staticFootprint` 的冻结声明视图回答在位行，以及计划所依据的那个修订。 | `ClaimConflict` → `Failed(SC-CLM-0002)` 并附现在读到的行——这是预测，不是结论 |
| 服务端步骤 | `settle(request, admitted)` 是唯一的写入入口点：它在服务端线程上运行，对请求的租约求值 `CaptureGate`，检查绑定，用 `ClaimPlanner` 派生出行，然后以 admitted 结果所携带的修订，对 `(world, owner, siting)` 做一次 CAS。它在每条路径上都关闭 `admitted`。 | `ClaimConflict` → `Failed(SC-CLM-0002)` 并附在位行；`RevisionMoved` → 调用内重试一次，随后 `Undecided(LEDGER_MOVED)` 并附令牌 |

`ClaimMatrix.conflicts(a, b)` 是唯一的冲突谓词，位于 `08`；内核与适配器都调用它，谁都不添加自己
的测试。`SHARED`/`SHARED` 永不冲突，而 `Formed.claims()` 携带它结算时所处的修订，因此后来的替换
可被发现。

### 4.6 快照核对、丢弃与下一次尝试（Snapshot check, discards and the next attempt）

`CaptureGate.evaluate(lease)` 就是整个核对，在单元跑完之后、产物被发布或结算之前执行。它从请求
自己的租约上读取两个事实：

| 事实 | 读取自 | 失败时的 `Verdict` |
| --- | --- | --- |
| 关注区域是干净的 | `lease.changeCount() == 0` | `CHANGED`：自捕获以来关注区域被标记过 |
| 捕获是有效的 | `lease.isValid()` | `SUPERSEDED`：其世界被替换，或句柄被关闭 |

内核每个关注区域只保留一个计数器，因此其中任何一处标记都会整次作废：一次捕获从不混合两个捕获
修订，也不存在在单元被捕获的那个捕获内重跑它的路径。

```
Dropped(SNAPSHOT_CHANGED):   // changeCount -> SC-ENG-0003
    nothing is published; the slot stays dirty
Dropped(CAPTURE_INVALID):    // isValid -> SC-ENG-0006
    nothing is published; the next attempt captures again
```

槽位取代不是内核事实。调度器拥有 `Ticket`，在工作线程投递结果时比对它，并把这次放弃报告为
`MatchCompletion.Discarded`；捕获不携带 ticket，因此一次预览永远无法使一个正等待结算的计划失效
（`19` R37）。

逐单元形态（`cellStamp(unit)`，只丢读了被标记格子的单元、其余复用）需要宿主的脏索引与尝试
缓存；在两者都存在之前，内核范围的计数器是保守形态，而它绝不可能在过期快照上发布 `Formed`。

计数器位于槽位中，以单元 id 为键——`discards`、`attempts` 与 `InstanceBudget`——因此合并的提交
继承它们，只有成功的结算或 `dropInstance` 才会重置它们。超过 `maxDiscards`（槽位策略，默认 3）
之后，尝试以 `SC-MC-0404` 结算为 `Undecided(DIRTY_UNSTABLE)`，并重新发出先前的 `Formed`，其
`generation + 1` 且事实相同，因此消费者能区分重发与新的匹配。

```java
final class InstanceBudget {                 // owned by the slot; the request holds the reference
    long discards(NamespacedId unit); long attempts(NamespacedId unit);
    Spend spent();                           // Spend(cells, sections), cumulative
    boolean tryCharge(int cells, int sections);   // false ⇒ the slot cannot afford this unit
}
```

许可是宿主的算术，写在这里是因为内核会读取槽位：`INTERACTIVE` 持有一个预留许可，`AUTO` 得到
`max(0, threads - 1)`，在单工作线程的宿主上 `AUTO` 被禁用而不是被饿死——`/cumulus workers` 会
说明这一点。`COMMAND` 分享剩余部分。`Deferred` 是带重试令牌的终结结局，只有 `AUTO` 会重新提交
它，并使用新令牌。

`InstanceRef.generation` 是*实例*代际，由 `contextRevision` 加上已结算的匹配派生，从不手工递增，
也绝不是*数据*代际（R-09 发射），后者引擎从不见到。`ReadSet.cells` 是持久的，`occupants` 以快照
为界，而没有变更格但 `changedTypes` 非空的 `ChangeSet` 是*未定位*的：下一次尝试不复用任何东西。

### 4.7 缓存与复用（Cache and reuse）

```java
record CacheKey(PlanId plan, Siting siting, UnitId unit, long closureDigest) {}
```

- 条目是一次贡献（区段 `Computation` 的逐区段输出）或一个单元结局（某个检查的应答）——绝不是
  一个折叠后的值，因此不查询任何 `Merge`，复用也无法改变结果。
- `closureDigest` 哈希单元所读的已排序 `(SectionKey, contentDigest)` 对；摘要不同即未命中，且
  不存在代际比较，也不混合某个单元的一半区段。
- `CacheMode` 为 `OFF | PARTIALS | OUTCOMES`：`OFF` 不存储任何东西，`PARTIALS` 存储区段贡献，
  `OUTCOMES` 存储贡献与单元结局。
- 命中既不改变值，也不改变 `Spend`：cells 与 sections 在派发时计费，无论是否缓存；捕获与刷新两段都按
  闭包大小计费，无论是否命中。`parallel` 是 `MatchPolicy` 的一部分，因此在确定性元组之内，且
  既不改变 `Spend` 也不改变诊断项顺序。
- 存储是 `20` §6.3 的 `AttemptCache`，一个位于 `cacheBytes` 之下的 LRU，绝不位于
  `retainedBytes` 之下。

### 4.8 线程与确定性（Threading and determinism）

捕获、脏刷新与声明结算在服务端线程上运行；`check` 在工作线程上对租约的不可变 `WorldView` 运行
（`20` §2–§5）。内核中不进入活动世界、账本写入、`ThreadLocal`、I/O、时钟或随机性；`Expectation`
在并发尝试之间共享，因此它们必须可重入，且只能看到 `ProbeSession`。`MatchSession` 是一次同步
`run()` 的内部累加器；它不是公开的可恢复会话，也没有 `advance`。确定性：固定
`(blueprint, snapshotId, siting, policy, catalog, frozen ledger revision, cancellations)` ⇒
相同的值、诊断包顺序与 `Spend`；宿主输入只会削弱确定性。结果只有在通过 §4.6 的捕获门禁之后
才到达世界；内核本身对哪个任务应当胜出没有意见。

## 5. 参考代码（Reference sketch）

```java
Siting siting = kernel.siting(furnace, LatticePoint.of(10, 64, 10), lease);   // one anchor cell
MatchResult probe = kernel.check(new MatchRequest(FURNACE, siting, lease,
        MatchPolicy.defaults(), slot.budget(), pins, record, owner, ticket));   // (a) slot-owned
switch (probe) {                                                    // exhaustive, no default
    case MatchResult.Dropped d   -> scheduler.resubmit();           // the snapshot moved
    case MatchResult.Failed f    -> renderer.render(f.findings());  // SC-ENG-0104: anchor absent
    case MatchResult.Undecided u -> log(u.reason());                // (c) UNREADABLE
    case MatchResult.Deferred d  -> scheduler.reprobe(d.token());   // AUTO only, fresh token
    case MatchResult.Formed p    -> index.record(p.id(), p.readSet());  // settle produced it
    case MatchResult.Admitted a  -> scheduler.submitForm(a);        // (b) the lease moves
}
// The scheduler closes a lease it took back (supersede, defer, cancel, stop) in its finally only when
// the request produced no result; a lease behind an `Admitted` belongs to whoever holds that result,
// and `settle` closes it.

// FORM (b) registers the admitted result: `settle(request, a)` runs on the server thread and answers
// the same six arms; on `Formed`:
//     index.record(full.id(), full.readSet());                         // settlement already ran
//     StructureContext ctx = kernel.derive(full, furnace.derivations());   // (e) U8, after Settled
ChangeSet change = new ChangeSet(Set.of(LatticeKey.of(LatticePoint.of(12, 65, 10))), Set.of(), 41L);
@Nullable List<InstanceRef> stale = index.affectedBy(change);       // (d) null: re-check everything
for (InstanceRef ref : stale == null ? index.instances() : stale) {
    scheduler.markDirty(keyOf(ref), change);                        // W3; the next attempt captures
}
```

共享舱门冲突（e）正是 settle 入口点存在的理由：A 的 `EXCLUSIVE` 声明与本次尝试的 `SHARED` 舱门舱位
在 `ClaimMatrix` 下互不兼容，因此结果是 `Failed(SC-CLM-0002)`，一条诊断项携带双方格点——不是静默
覆盖，也不是 `Undecided`，因为账本已经作答。

## 6. 用例（Use cases）

| 受众 | 用例 |
| --- | --- |
| 附加模组作者 | 熔炉在其原点声明一个 `PRIMARY` 锚点、一个 `SHARED` 舱门舱位，以及一个由 bounds 加 anchor 单元组成的计划；作者从不见到会话、索引或单元顺序。 |
| 宿主 | 建筑魔杖：点击时 `PROBE`，确认时 `FORM`，结算后 `Formed`，`Undecided` 渲染为 `UNKNOWN` 而非 `MISSING`。 |

## 7. 证明（Proofs）

| 命题 | 论证 |
| --- | --- |
| P-1 确定性 | 只读取冻结的请求元组、租约的不可变视图、已冻结的蓝图与目录以及账本修订；一个固定元组加上打开的租约与稳定的账本，产出相同的值、诊断包顺序与 `Spend`。不读取任何时钟。 |
| P-2 可靠性 | `Failed` 需要一个 `MISMATCH` 或一个 `ClaimConflict`；`Formed` 要求每个单元 `MATCH` 且有一个 `Settled` 判定；`Undecided` 具名一次停止或一个缺口；`Deferred` 具名一个令牌。 |
| P-3 无假阴性 | 包围盒成立，否则尝试为 `Undecided`；锚点完成映射，否则该格不可读；并且普查支配所声明的需求量。 |
| P-4 计费与缓存无关（`22` P1） | `cells` 与 `sections` 在派发时计费，无论是否缓存；检查按它的读取闭包计费，区段计算按切片加一个区段计费，聚合不计费；`Spend(cells, sections)` 就是整个账本，因此同一计划的冷热两次运行花费相同，缓存无法把 `BUDGET_EXHAUSTED` 变成 `Formed`。 |
| P-5 子句柄释放（`22` P4） | `InstanceRecord` 拥有捕获；`Admitted` 与 `Formed` 持有只释放自己那份引用的子句柄。当调用方从不调用 `settle` 时，`Admitted` 由调用方关闭；一旦调用，`settle` 在任何情况下都会关闭它；`Formed` 在 `derive` 之后由调用方关闭。未关闭的句柄会表现为 `metrics().openHandles`，绝不表现为已关闭的视图，也绝不把实例的引用关闭两次。 |
| P-6 失效完备性 | 关注区域内的一次标记会提升 `lease.changeCount()`，于是门禁回答 `CHANGED`、该尝试为 `Dropped(SNAPSHOT_CHANGED)`；世界被替换或句柄被关闭则回答 `SUPERSEDED`，该尝试为 `Dropped(CAPTURE_INVALID)`。槽位取代属于调度器（`19` R37）。任何单元都不会对它已读取的那个捕获重跑，不一致时什么也不发布。 |

## 8. 依赖与待决问题（Dependencies and open questions）

需要 U0–U5 与 U9 提供其签名中出现的名称（`Ticket`、`Siting`、`SnapshotLease`、`InstanceBudget`、
`ClaimPlan`、`ClaimVerdict`、`SettleRequest`、`CacheMode`、`Reply`），以及尚未冻结的
`Blueprint.expand(Stage)`。

| 编号 | 问题 | 提案 |
| --- | --- | --- |
| Q-1 | 事务内那一次重试之后的 `RevisionMoved`（`22` §16 Q4）：重试令牌还是硬性 `Undecided`？ | 当前：`Undecided(LEDGER_MOVED)` 携带一个令牌，依据 `22` §10.1——第二次移动从不静默 |

`22` §16 对本单元没有留下别的问题。它使用的两个名称不在 `22` 的列表中，且原样保留：`SC-ENG-*`
家族（`0101`–`0104`、`0201`、`0401`），以及 `PrescreenIndex` / `Census`——`22` §7.1 用 `Check`
单元取代了它们，但没有重命名。

## 9. 证伪（Falsification）

| # | 被否定的命题 | 使其失败的测试 |
| --- | --- | --- |
| F-1 | 预筛可靠性 | 一个有效的 8×7×8 熔炉，外加一块装饰方块，位于所有舱位之外但在足迹之内：普查启发式会说 `Failed`，而答案必须是 `Formed` 或 `Undecided`。 |
| F-2 | 不可读 ≠ 不匹配 | 卸载内部区块，运行 `FORM`：任何把 `ReadStatus != READABLE` 映射为 `MISMATCH` 的路径都会报 `Failed`。 |
| F-3 | 计费相等 | 先冷跑一个计划，再用 `CacheMode.OUTCOMES` 热跑：两份 `Spend` 记录在两条腿上相等，且都不会把 `Undecided(BUDGET_EXHAUSTED)` 变成 `Formed`。 |
| F-4 | 下一次尝试的复用 | 捕获之后标记一个墙格点：该尝试为 `Dropped(SNAPSHOT_CHANGED)`，没有任何 `Formed` 携带旧判定，下一次尝试重新捕获。 |
| F-5 | 结算顺序 | 第二个所有者在 `check` 与 `settle` 之间认领同一批 `EXCLUSIVE` 格点：结果是 `Failed(SC-CLM-0002)` 或 `Undecided(LEDGER_MOVED)`，绝不是带有第一份判定声明的 `Formed`。 |

审阅者最尖锐的问题：**是否存在某条路径，在没有每个计划单元都对可读输入返回 `MATCH` 并给出
`Settled` 判定的情况下到达 `Formed`，或因一个不可读格点到达 `Failed`？** 若存在，则 §4.4、§4.5
与 P-2 有误。
