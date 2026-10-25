# 20 — 执行、快照、脏状态与区段缓存（Execution, snapshots, dirty state, and section cache）

> 已接受的设计，取代先前的 tick 切片（tick-slicing）提案。服务于 R-02、R-03、R-08；
> 触及 I-1、I-4、I-15。内核保持同步且纯净；所有异步都在宿主侧（`ServerState`）。
> `11` 描述本层调用的内核调用；`16` 描述 Minecraft 侧的实现。

## 1. 一页决策（Decisions in one page）

1. **检查不做 tick 切片。** `StructureKernel.check` 一次运行到完成。没有 `begin`/`advance`，
   没有 `WorkSlice`，内核签名中也没有时钟。
2. **服务器线程**拥有捕获、脏刷新、账本冻结、结算、世界编辑、输出汇聚（sink）与事件。
   **工作线程**在不可变快照上运行 `check`、`derive`、`solve` 与 `PlanValidator`。内核不接收
   执行器（executor）、future 或回调。
3. **快照以区段为单位（section-based）。** 每个 16³ 世界区段一个 `SectionSnapshot`，内含
   冻结的 `PalettedContainer<BlockState>` 副本；纯空气区段是单例（singleton）。方块状态不使用
   逐单元格的 `OccupantSnapshot` 数组。**默认是每快照副本**；共享 `SectionStore` 是可选启用的，
   等待性能分析。
4. **脏分为区段/载荷/实体三类。** `LevelChunk.setBlockState` 标记一个区段；
   `Level.blockEntityChanged` 只在被监视时标记载荷位置；实体加入/离开标记其足迹，**实体移动
   由服务器线程上的逐 tick 窗口差分发现**，而不是 `setPos` 钩子；区块卸载使该区段变为
   `UNKNOWN`。
5. **每个 `(world, owner)` 至多一个在执行（in-flight）的检查。** 较新的请求会并入；它从不
   与正在运行的任务竞争。只有当租约回答 `CURRENT` 时，结果才可发布或结算：
   `changeCount() == 0` 且 `isValid()`（§5.4）。槽位的 `Ticket` 在调度器内部决定合并，且
   永不进入捕获。
6. **没有逐区段的检查阶段。** 区段 `Computation` 按区段运行并调用 `draft.contribute`；屏障按
   规范的 `SectionKey` 顺序用 `Merge.combine` 折叠它们的贡献，随后聚合计算与检查每阶段各运行
   一次。唯一的并行是 `MatchPolicy.parallel`；`ChunkGrid` 是读取视图的形状，不是工作划分。
7. **一次性刷新。** 脏区段在派发时于服务器线程上重新复制，而不是在逐 tick 的检查泵上。若超出
   上限，请求被推迟；该登记项（slot）保持脏。无论哪种情形，捕获与空操作刷新都按计划闭包的
   区段数计费。
8. **实例拥有它自己的捕获。** `InstanceRecord` 是唯一所有者；`Admitted` 与 `Formed` 持有子
   句柄，`retain(PinBudget)` 可以拒绝，且 `retainedBytes`（从不被逐出）与 `cacheBytes`（LRU）
   分开。因此查看者永远无法把一次捕获推入 `DEFERRED`。

## 2. 线程与所有权模型（Thread and ownership model）

| 阶段 | 线程 | 读取 | 写入 |
| --- | --- | --- | --- |
| 捕获 / 刷新 | 服务器 | `Level`、`ChunkAccess`、`LevelChunk`、`BlockEntity`、`Entity` | `SectionStore`、当前 `LevelSnapshot` |
| 检查 / 派生 / 求解 / 校验 | 工作线程 | `LevelSnapshot`、`Blueprint`、冻结的 `ClaimView`、`CancelToken` | 仅结果对象 |
| 结算 | 服务器 | `MatchRequest` + admitted 结果、`SavedClaims` 修订 | `SavedData`、`RuntimeState` |
| 应用 | 服务器 | 结果/计划 | 世界 |
| 渲染/日志 | 服务器（副作用）或工作线程（纯数据） | `FindingBundle` | 宿主输出汇聚 |

- `LevelSnapshot`（根，实现 `WorldView`）与 `NestedLevelSnapshot`（子，实现 `NestedView`）位于
  `dev.vfyjxf.cumulus.mc.capture`，在 `kernel.**` 之外。
- `MatchScheduler` 位于 `ServerState` 中；每个服务器一个，而非每个世界一个。
- 内核从不导入 `ChunkAccess`、`PalettedContainer`、`CompoundTag` 或 `Level`。

## 3. 宿主调度器（Host scheduler）

```java
record CheckKey(World world, OwnerId owner) implements Comparable<CheckKey> {}
enum Priority { INTERACTIVE, COMMAND, AUTO }

record CheckRequest(CheckKey key, Blueprint blueprint, Place place, List<Stage> stages,
                    MatchPolicy policy, Priority priority,
                    @Nullable ChangeSet change) {}

final class MatchScheduler implements AutoCloseable {
    static MatchScheduler open(ServerState state, SchedulerOptions options);
    MatchHandle submit(CheckRequest request);               // server thread only
    MatchHandle submitForm(MatchResult.Admitted admitted);  // FORM from an admitted result; lease moves
    void markDirty(CheckKey key, ChangeSet change);         // W3, never dispatches
    void dropInstance(CheckKey key);                        // cancel -> unwatch -> release
    void worldUnloaded(World world, long epoch);
    void tick();                                            // window diff, re-probe AUTO, watchdog
    SchedulerMetrics metrics();
    void close();                                           // ServerStopping
}

final class MatchHandle {
    CheckKey key(); long ticket(); MatchState state(); CancelToken token();
    CompletableFuture<MatchCompletion> done();               // completes on the server thread
    void onTerminal(Consumer<MatchCompletion> cb);           // exactly once, all outcomes
    boolean cancel(CancelReason reason);
}

enum MatchState { QUEUED, RUNNING, POSTED, SETTLED, DROPPED, FAILED, DEFERRED, CANCELLED }
sealed interface MatchCompletion {
    record Reported(MatchResult result) ...
    record Discarded(DiscardReason reason, long resultVersion, long currentVersion) ...
    record Failed(MatchResult.Undecided reported, Throwable cause) ...
}
```

### 3.1 接纳与合并（Admission and coalescing）

- 一个登记项以 `(world, owner)` 为键。`Place` 是宿主侧的目标：调度器通过门面的
  `siting(blueprint, origin)`——一个锚点单元格——解析它，随后内核请求携带一个普通的 `Siting`
  （`11` §3，`22` §4.4）。
- 一次提交只运行 `check`，别的什么都不做：结果携带计划中的声明与它读到的修订，且不写入任何
  声明。注册是第二次显式调用——`submitForm(admitted)` 在服务器线程上串接一次 `settle`——
  因此预览、叠加层、探测与 AUTO 通过都停在 `Admitted`，只有想要该声明的调用方为写入付出代价。
- 每个键至多有一个任务处于 `QUEUED`/`RUNNING`/`POSTED`。较新的提交会递增 `submitTicket`；
  它**不**启动第二个任务。它要么：
  - 以新请求作为 `pending` 把该登记项标脏，要么
  - 若当前任务仍为 `QUEUED`，替换其待处理请求。
  正在运行的任务投递结果（post）时，调度器比对它自己的票据；陈旧票据把该尝试作为
  `MatchCompletion.Discarded` 丢弃，随后派发待处理请求。被合并掉的提交，其租约由调度器在
  `finally` 中关闭，绝不由调用者关闭。
- 这使 W-3 无需依赖协作式取消来退役 `RUNNING` 任务即可成立。`cancel()` 只用于卸载/停止/
  移除/用户中止，不用于“较新请求胜出”。
- 每次尝试都会捕获。重试是在一次全新捕获之上的新尝试，快照从不混合两次捕获的修订；`cellStamp`
  保持不变的单元**在下一次尝试**通过 `AttemptCache`（§6.3）复用，绝不靠重新提供旧快照。
- 租约是一次移动，只发生在三个地方之一：持有它的结果、它被移入的请求，或调度器。
  `submitForm(admitted)` 把它移入 `FORM` 请求。调度器的 `finally` 只为没有产出结果的请求关闭
  租约；一旦请求返回 `Admitted`，它的租约就离开队列管理，归持有那个结果的一方所有，并由
  `settle`（或只做过预览的调用方）关闭。
- `INTERACTIVE` 持有一个保留许可；`AUTO` 得到 `max(0, threads - 1)`。在单工作线程的宿主上，
  `AUTO` 被禁用而不是被饿死，`/cumulus workers` 会说明这一点。`COMMAND` 共享剩余部分。
  没有许可的 `AUTO` 是 `DEFERRED`，由 `MatchScheduler.tick()` 重新探测，绝不由 W3。
- `AUTO` 只运行 `PROBE`。若它通过且实例仍是候选项，调度器把 `FORM` 作为单独的请求提交；
  `COMMAND`/`INTERACTIVE` 可以串接完整阶段列表。这使 AUTO 任务保持短小、可取消且可合并。
- 低于 `syncMaxCells`（默认 4096）的 `INTERACTIVE` 可以内联穿过相同的 `check` 与 `settle`
  函数体。它使用相同的 `FrozenLedger`、快照核对与 `CancelToken`；同步路径不是一条单独的
  语义路径。
- `FrozenLedger.readBox` 是计划、锚点解析、接缝配对与结算范围可能查询的每个单元格的并集，
  并在接缝膨胀（seam dilation）下封闭。越界查询是宿主断言失败（`SC-MC-06xx`），而不是
  `Undecided(LEDGER_UNAVAILABLE)`；异步路径与内联路径共享同一冻结账本。

### 3.2 生命周期（Lifecycle）

- `ServerAboutToStartEvent` 打开；`ServerStoppingEvent` 关闭。
- `LevelEvent.Unload` 取消该世界的登记项、递增世界纪元、释放快照，以及该世界各实例的每一条
  声明行（`ClaimLedger.release`，`23` §5）；此后不再结算。
- `dropInstance` 是唯一的移除入口：取消 → 解除脏监视 → 释放该实例的声明行
  （`ClaimLedger.release`） → 关闭实例自己的租约引用 → 释放记录；其 `POSTED` 结果为
  `Dropped(CAPTURE_INVALID)`（租约已关闭）。
- 对 `SETTLED`、`DROPPED`、`FAILED` 与 `CANCELLED`（包括卸载/停止），`onTerminal` 总是恰好
  投递一次；任何终止路径都不会让调度器留下未关闭的租约。`onSettled` 只是仅限成功情形的
  便利方法，不是终止信号。
- `close()` 拒绝新工作、取消、关闭、短暂等待，然后排空。`done()` 总会完成（停止时为
  `Discarded`），因此没有任何调用者会永久等待。

### 3.3 失败（Failure）

| 故障 | 处理 |
| --- | --- |
| 内核 `RuntimeException` | `Undecided(WORKER_FAILED)` + `SC-MC-0601`；工作线程存活 |
| `OutOfMemoryError` | 释放预留，标记调度器降级，**重新抛出**；不产生合成结果 |
| 其他 `VirtualMachineError`/`LinkageError` | 在 `finally` 中释放，重新抛出；登记项 `SLOT_BROKEN` |
| `RejectedExecutionException` | 归还许可，保持脏；`MatchCompletion.Discarded` 或 `DEFERRED` |
| 截止时间 | 宿主看门狗调用 `CancelToken.cancel(DEADLINE)`；内核在读取间隔处轮询 |
| 卡死的自定义期望 | 不 `Thread.stop`；第二个间隔之后标记调度器降级，替换工作线程 |
| 反复失败 | 连续三次失败暂停 `AUTO`；`INTERACTIVE` 清除该状态 |

## 4. 快照（Snapshot）

### 4.1 表示（Representation）

```java
final class FrozenStates {                     // private, immutable-by-convention
    BlockState get(int lx, int ly, int lz);   // PalettedContainer.get, no acquire
    boolean contentEquals(FrozenStates other);
    int nonAirCount();
}
final class SectionSnapshot {
    SectionKey key(); FrozenStates states(); long captureRevision(); long[] payloadCells();
}
sealed interface SectionSlot permits Air, Present, Refused {
    record Air(SectionKey key, long captureRevision) implements SectionSlot {}
    record Present(SectionSnapshot section) implements SectionSlot {}
    record Refused(SectionKey key, ReadStatus status, FindingCode code) implements SectionSlot {}
}
record CellPayload(LatticeKey cell, OccupantDescriptor descriptor, List<Trait<?>> traits) {}
final class LevelSnapshot implements WorldView {
    SnapshotId snapshotId();          // capture identity; a new object = a new id
    long estimatedBytes();
    SectionSlot slot(SectionKey key);
    @Nullable CellPayload payload(LatticePoint cell);
    List<CapturedEntity> entities(Cuboid box);
    List<Finding> findings();
    @Override default @Nullable OccupancySummary summary(Cuboid box) { ... }
    @Override ChunkGrid chunkGrid();
    @Override @Nullable ChunkContent chunk(ChunkKey key);
}
final class NestedLevelSnapshot implements NestedView { /* child frame over the same bytes */ }

interface SnapshotLease extends AutoCloseable {     // one owner: InstanceRecord
    SnapshotId id();
    LevelSnapshot view();
    Outcome<SnapshotLease> retain(PinBudget budget);    // Pinned | Missing(SC-MC-0502)
    boolean isOpen();
    @Override void close();                             // releases this handle only
}
```

- `SectionKey` 是世界 16³ 索引（`x >> 4` 等）；它不是缓存键。
- `Air` 是可读且为空：`READABLE`，并带一个 `captureRevision` 供脏/缓存检查使用。它不是
  `NOT_CAPTURED`。
- `Refused` 携带一个 `ReadStatus` 与一个诊断码；`occupant` 为 `null`。
- `CellPayload` 为被监视的方块实体或实体携带已解析的描述符与特征列表；`occupant(cell)` 在读取时
  把最终的 `SnapshotId` 惰性盖进 `OccupantRef`。方块状态从 `BlockState` 惰性构造。不存在原始
  载荷通道。
- `LevelSnapshot` 为被跟踪的方块实体/实体运行解析器；缺失必需特征时为 `NOT_CAPTURED`，
  绝不是清零值。
- 实体足迹在封存前按单元格→占用体建立索引；`occupant(cell)` 对每个被覆盖的单元格返回该
  实体，即使方块区段为 `Air`。被遮蔽的单元格为 `NOT_CAPTURED`。
- `SnapshotId` 是捕获身份且从不重用；内容相等的刷新返回同一个 `LevelSnapshot` 对象
  （不产生新 id）。`SectionSnapshot.captureRevision` 是区段缓存使用的内容纪元；它不能替代
  `SnapshotId`。

### 4.2 捕获（Capture）

一个服务器线程函数，没有 tick 流水线：

```
capture(req, issues) -> Captured(LevelSnapshot, CaptureIssues) | TooLarge | Unavailable
for each section in y,z,x order:
    chunk = serverChunkCache.getChunkNow(sx, sz)   // never Level.getChunk
    if chunk == null || !chunk.isLoaded()          -> Refused(NOT_CAPTURED, SC-MC-0306)
    if index outside build height                  -> Refused(OUT_OF_BOUNDS, SC-MC-0505)
    if section.hasOnlyAir()                        -> Air(key, contentEpoch++)
    if section/cap/byte limit exceeded              -> TooLarge or Refused(NOT_CAPTURED, SC-MC-0501)
    else                                            -> Present(store.intern(key, states.copy(), contentEpoch++))
for each Present section:
    for pos in chunk.getBlockEntitiesPos() ∩ section:
        be = chunk.getBlockEntity(pos, CHECK);
        payload = be == null ? none : resolver.blockEntity(be, requiredTraits)
        if required trait missing -> Refused(NOT_CAPTURED, SC-MC-0302); issues.add(...)
for entities intersecting bounds.inflate(MAX_ENTITY_SPAN):
    admit tracked types first; resolve entity snapshot (type/UUID/box/NBT/traits)
    anchor inside -> cover its cells; anchor outside/body inside -> shadowed NOT_CAPTURED
stamp SnapshotId(world, captureCounter.incrementAndGet()) last; wrap it in a SnapshotLease
```

`SC-MC-0501` 是且仅是捕获超限的诊断码；`SC-MC-0502` 属于 `retain` 拒绝钉住（pin），从不用于
载荷。

- `getChunkNow` 仅限主线程，且从不加载区块/取票据；未完全加载的区块（包括加载后/方块实体
  注册仍在等待的区块）为 `NOT_CAPTURED`。
- 复制区段，不复制单元格。跳过 `hasOnlyAir`。`PalettedContainer.copy()` 是 `FrozenStates`
  的唯一来源；副本从不被写入。
- 方块实体载荷使用 `saveWithoutMetadata`（类型取自 `be.getType()`），因此位置/id 的变动
  不会使内容失效。解析器填充 `requiredTraits`；缺失的特征为 `NOT_CAPTURED`。
  解析器返回之后，重新读取该单元格的方块状态；若它与复制的状态不同，说明捕获期间发生了
  mod 写入：拒绝该区段，绝不发布撕裂的快照。
- 实体按被跟踪类型优先设上限；其足迹在封存前按单元格→占用体建立索引。被覆盖的单元格
  回答该实体，即使方块区段为 `Air`；被遮蔽的单元格为 `NOT_CAPTURED`，绝不是空气。
- 上限：`maxCells`、`maxSections`、`maxBytes`、`maxEntities`、`maxPayloads`；达到上限是一个
  封存的 `TooLarge`/`Refused`，绝不把部分快照当作完整快照。
- `SnapshotId` 在读取之后最后盖印。`SectionSnapshot.captureRevision` 是区段缓存使用的内容
  纪元；它与 `SnapshotId` 相互独立。

### 4.3 区段所有权与版本（Section ownership and versions）

- **默认：** `LevelSnapshot` 直接拥有其区段副本。没有共享存储、没有弱映射、没有依赖 GC 的
  修订。快照在其最后一个句柄关闭时释放——`InstanceRecord` 的引用、某个结果的子句柄，或
  调度器的引用。
- **可选的 `SectionStore`：** 只有在性能分析显示重叠捕获或重复的大块复制成为热点之后，才可
  启用每世界的共享缓存。它为每个 `SectionKey` 保留当前的 `SectionSnapshot`；值是弱引用，
  快照持有强引用。当 `contentEquals` 成立时 `intern` 返回现存条目。被回收并重新驻留的弱
  条目可能获得新的内容纪元；那是保守的缓存未命中，绝不是误命中。存储是缓存，不是持久性
  保证。
- 存储绝不是解析器：封存的 `LevelSnapshot` 持有直接的 `SectionSnapshot` 引用。
  禁止惰性的 `snapshot.section(k) -> store.get(k)`，因为区段可能在检查中途推进并混合修订。
- `SnapshotId` 是捕获身份，在读取之后分配且从不重用。内容身份是
  `SectionSnapshot.captureRevision`；空操作刷新返回同一个 `LevelSnapshot` 对象，因此既无
  新 `SnapshotId`，也无新内容纪元。
- 唯一的内容纪元是 `SectionSnapshot.captureRevision`。`SnapshotId` 是门禁比较的身份；两者都
  不是缓存键，缓存命中比较摘要，绝不比较纪元。
- **所有权。** 在实例生命期内，`InstanceRecord` 是一次捕获的唯一所有者；`Admitted` 与
  `Formed` 持有子句柄，只释放自己的引用。`derive` 读取
  `StructureContext.at(instance)`——框架，绝不是租约——补救方案提供者接收 `ContextView`，
  绝不是租约。不存在 N 个 tick 后强制关闭：宿主可以在 `dropInstance` 或策略逐出时关闭实例
  自己的引用，关闭它恰好是该引用的一次释放，而不是对未结句柄的清扫。
- **两本账。** `retain(PinBudget)` 计入 `retainedBytes`，该项从不被逐出，且可以拒绝为
  `Missing(SC-MC-0502)`；`cacheBytes` 是 LRU，与 `AttemptCache` 一起存在（§6.3）。由于查看者
  只触及 `cacheBytes`，它永远无法把一次捕获推入 `DEFERRED`。

## 5. 脏标记与刷新（Dirty marking and refresh）

### 5.1 钩子（Hooks）

| 变更 | 钩子 | 脏 |
| --- | --- | --- |
| 方块状态 | `LevelChunk.setBlockState`（全部重载） | 该位置的 `SectionKey` |
| 方块实体本体 | `Level.blockEntityChanged(BlockPos)` **仅此** | 载荷位置，且仅当被监视 |
| 实体加入/离开 | `EntityJoinLevelEvent`/`EntityLeaveLevelEvent` | 足迹所在区段 + 每个包含该足迹的窗口 |
| 实体移动 | **服务器线程上的逐 tick 窗口差分**：每个窗口的现存 `cell → EntityKey` 集合与捕获集合比较 | 读取该窗口的单元 |
| 区块加载/卸载 | `ChunkEvent.Load`/`Unload` | 加载：`DIRTY`；卸载：`UNKNOWN` |
| 声明结算/释放 | 写入时 `SavedClaims.settle`，移除时 `SavedClaims.release` | 声明单元格 ∩ 窗口 |
| 数据包/配置 | reload 钩子 | 整个实例 |

`Level.blockEntityChanged` 是载荷的唯一咽喉点：`BlockEntity.setChanged` 调用它，直接调用者
也会到达它。跳过 `super` 的 mod 覆写不被覆盖；`loadWithComponents` 与回滚恢复路径必须显式
调用 `markPayload`。`ChunkHolder.blockChanged` 是网络记账，不是脏 API。绕过所有钩子的变更
由按龄重验扫描覆盖；不宣称它是精确的。

实体移动没有方块钩子。每个 tick，服务器线程对每个受监视窗口，把该窗口长方体内
`EntityKey(uuid, type, digest)` 的现存集合与捕获集合比较，开销以窗口的实体数而非其单元格数
为界；任何差异都会把读取该窗口的单元标脏。这就是向外移动的见证：离开窗口的实体会改变现存
集合，因此一头从洞里走出的牛无需 `setPos` 钩子、也无需遍历体积就能被发现。加入/离开立即
标记足迹。任何轴上宽于 `MAX_ENTITY_SPAN = 3` 个单元格的类型在 DESIGN 被拒绝
（`SC-BAY-0110`），因此窗口永远不会对其占用体的一部分失明。

### 5.2 索引与状态（Index and state）

```java
final class DirtyIndex {
    void watch(WatchedInstance w); void unwatch(InstanceRef id);
    Set<InstanceRef> instancesIn(SectionKey k);
    Set<InstanceRef> instancesFor(EntityWindow window);     // window -> instances, the fourth index
    void markSection(SectionKey k, DirtyCause c);
    void markPayload(LatticePoint p, DirtyCause c);
    void markEntityFootprint(Cuboid was, Cuboid now);
    long contentStamp(SectionKey k);
}
enum WatchState { CLEAN, DIRTY, CHECKING, BROKEN, UNKNOWN }
final class WatchedInstance {
    SnapshotId snapshot(); WatchState state(); long dirtyEpoch();     // metrics only
    boolean dirtyForCheck(); boolean checkInFlight();                 // any read cell marked since capture
    long cellStamp(NamespacedId unit);                                // the gate's per-unit term
    long[] sections(); long revOf(int i); long payloadRev(LatticePoint p); long entityRev(UUID id);
    boolean stale(int i); boolean stalePayload(LatticePoint p); boolean staleEntity(UUID id);
}
```

- 四张索引：区段、载荷位置、实体足迹，以及 `window → Set<InstanceRef>`。窗口是一个受监视的
  区域，而不只是一次查询，因此逐 tick 差分（§5.1）知道该把谁标脏。标记是 O(1)，且不遍历
  所有监视项。
- `stale()` 是区段、载荷与实体修订的并集；载荷标记不得要求区段复制，实体移动也不得靠
  复制把其足迹内每个方块标脏。
- `cellStamp(unit)` 是该单元读取过的单元格自捕获以来的标记；它喂给宿主的脏索引，而门禁的
  第四项是关注区域单一的 `changeCount`（§5.4）。
  `dirtyEpoch` 仅作为监视项范围的单调计数器保留，用于度量，永不出现在门禁中。`UNKNOWN` 是
  已卸载/被拒，绝不是 `BROKEN`，也绝不是空气；它会因 `ChunkEvent.Load` 或首个 ticking tick
  重新进入。`BROKEN` 表示请求无法构造，而不是达到上限。
- 空操作刷新（`contentEquals`）保持同一 `LevelSnapshot` 并清除 `DIRTY`；它不创建新 id，
  也不强制检查。

### 5.3 刷新（Refresh）

- 唯一所有者：`WatchRefresh.refresh(watch) -> Ready | Deferred`，由调度器在提交时于服务器
  线程调用一次。脏 tick 钩子可以预热，但绝不启动检查，而一次提交对刷新后的视图运行一次
  `check`：是否写入任何内容由随后跟着调用 `settle` 的调用方决定（§3.1），绝不由请求字段决定。
- 刷新在监视项的区段、载荷与实体窗口上是原子的。它要么把所有陈旧部分变新，要么返回
  `Deferred` 并让监视项保持脏；绝不发布新与旧的混合。
- 刷新按区段数与重读的单元格数计费，记在本次尝试同一份 `cells` / `sections` 预算里。若超出
  上限，则 `Deferred`，调度器稍后重新探测。
- 空操作刷新返回同一对象，`dirtyEpoch` 保持不变（该标记已被消费），并产生零次检查——且仍按
  计划闭包的区段数计费，因此 `sections` 这一项与缓存无关（`22` §9.5）。

### 5.4 快照核对（Snapshot check）

`CaptureGate.evaluate(request.lease())` 从租约上读取两个事实，**在**单元跑完**之后**、产物被发布
或结算**之前**：

| 事实 | 读取自 | 何时不成立 |
| --- | --- | --- |
| 关注区域是干净的 | `lease.changeCount() == 0` | 自捕获以来关注区域被标记过 |
| 捕获是有效的 | `lease.isValid()` | 其世界被替换，或句柄被关闭 |

标记回答 `CHANGED`，并把该尝试作为 `Dropped(SNAPSHOT_CHANGED)`、`SC-ENG-0003` 丢弃；无效的捕获
回答 `SUPERSEDED`，并把它作为 `Dropped(CAPTURE_INVALID)`、`SC-ENG-0006` 丢弃。什么都不写，因此
一次已脏但未刷新的变更不可能产生 `Formed`。这是工作线程、内联路径与分离出去的 `settle` 唯一的
核对点。宿主的生命周期停止也落在同样两个原因上：移除实例会关闭租约，世界卸载或服务器停止会替换
世界。

槽位取代不属于这次核对。调度器拥有票据，在工作线程投递结果时比对它，并把这次放弃报告为
`MatchCompletion.Discarded`（`19` R37）。

内核每个关注区域只带一个 `changeCount`，所以核对刻意保守：捕获之后任何标记都会整次作废。
逐单元形态（`cellStamp(unit)`，只丢读了被标记格子的单元）属于宿主的脏索引，并且只有和尝试
缓存一起才划算；`ReadSet`/`ChangeSet` 针对的是下次尝试的复用，不是把核对照得乐观的许可。

## 6. 区段遍历、屏障与缓存（Section pass, barrier, cache）

### 6.1 网格与内容（Grid and content）

```java
record ChunkKey(int cx, int cy, int cz) implements Comparable<ChunkKey> {}
record ChunkGrid(int cellSize, LatticePoint origin) {
    ChunkKey chunkOf(LatticePoint p);   // floorDiv per axis
    Cuboid boundsOf(ChunkKey k);
    int chunkCount(Cuboid box);
    List<ChunkKey> chunksOf(Cuboid box);
}
interface ChunkContent {
    Cuboid bounds(); ReadStatus status(); boolean allAir();
    boolean maybeHasBlock(NamespacedId type);
    int countBlock(NamespacedId type);  // -1 = cannot answer
    @Nullable OccupantSnapshot occupant(LatticePoint p);
    ReadStatus readStatus(LatticePoint p);
}
```

`WorldView` 新增 `@Nullable ChunkGrid chunkGrid()`、`@Nullable ChunkContent chunk(ChunkKey)` 与
`@Nullable OccupancySummary summary(Cuboid)`。`cellSize` 是数据，不是常量；适配器传 16。
`chunkOf` 使用 `floorDiv`，绝不用 `>>` 或 `/`，因此负 `y` 与非零原点都能工作。

### 6.2 区段遍历与屏障（Section pass and barrier）

| 阶段 | 运行什么 | 顺序 |
| --- | --- | --- |
| 区段遍历 | 每个区段 `Computation` 对 `region ∩ sectionSet` 的每个区段运行一次；每次运行调用 `draft.contribute(key, partial)` | 区段按 `(x, z, y)` 排序 |
| 屏障 | 每个被贡献的 `ContextKey` 由 `Merge.combine` 在其按 `SectionKey` 排序的贡献上折叠 | 一次一个键 |
| 聚合 | 聚合 `Computation` 按 `inputs()` 上的 Kahn 序，随后是该阶段的每个 `Check` | `(phase, declaration)`——各组内按声明顺序 |

- 检查绝不在屏障之前运行，也绝不被按区段拆分：不存在逐区段的检查阶段，因此对齐区块的
  安放位与跨区块的安放位得到同一判定。
- 要求载荷的位置若为被拒绝或纯空气的区段，会使该键为 `Missing`（`SC-FOC-0301`）；它绝不是
  一次短折叠，因为贡献对整个键都是 `Missing`。
- 实体窗口与单元格一样属于读取范围：读取某个窗口的检查在聚合阶段读取它一次。该窗口查询在
  计划闭包时按区段计费，无论该窗口的长方体是否与被复制的区段重叠。
- `parallel` 是 `MatchPolicy.parallel`，位于确定性元组内部。它可以重排区段遍历，绝不重排
  屏障——无论哪种情形，折叠看到的都是排好序的贡献列表——并且既不改变 `Spend`，也不改变诊断项
  顺序（§8）。

### 6.3 缓存（Cache）

```java
record PlanId(long value) {}                    // interned at Blueprint build
sealed interface CachedUnit permits CachedPartial, CachedOutcome {}
record CachedPartial(ContextKey<?> key, Object value) implements CachedUnit {}
record CachedOutcome(Reply reply, List<Finding> findings, List<OccupantRef> subjects)
        implements CachedUnit {}
interface AttemptCache {                        // CacheKey is 11 §4.7
    @Nullable CachedUnit get(CacheKey key);
    void put(CacheKey key, CachedUnit value);   // evicts by LRU under cacheBytes
    static AttemptCache none(long cacheBytes);
}
```

- `PlanId` 是在 `Blueprint` 构建时分配的一个驻留（interned）`long`；数据包重建得到新 id 与
  冷缓存。它从不对期望 id 或 lambda 身份做哈希。
- 缓存存储贡献与单元结果，**绝不存储折叠后的值**，因此不咨询任何 `Merge`，命中也不能改变
  结果。在一个 `CacheKey` 内部，单元及其 `closureDigest` 才使命中成立；计划与安放位分量把
  它限定在一次蓝图摆放内。
- `CacheMode` 门控什么可以被存储：`OFF` 什么都不存，`PARTIALS` 存区段遍历的贡献，
  `OUTCOMES` 存贡献与单元结果。`closureDigest` 覆盖该单元读取过的每个区段，并由
  `MatchPolicy.maxSectionsPerUnit` 限定该列表。
- 命中既不改变值也不改变 `Spend`：准入在派发时照常计费，捕获与刷新照常按闭包大小计费
  （`22` §9.5）。因此同一计划的热运行与冷运行具有相同的 `Spend` 与相同的预算折叠。
- 有效性是 `closureDigest` 相等。不同的摘要是未命中，绝不是一次变更；没有 `invalidate`
  操作，也没有纪元比较。绝不提供 `NOT_CAPTURED`。
- 缓存的 `MATCH` 保留其 `OccupantRef`，且每个引用携带它被铸造时所处的 `SnapshotId`
  （`19` R8，§4.1）；复用时这些引用会被重新盖印为新捕获的 id，因此命中绝不会用旧捕获伪造
  绑定。
- 缓存按 `ServerLevel` 划分，与调度器一起拥有，卸载时清除，并按 `cacheBytes` 设界。
  `retainedBytes` 不受它影响，因此查看者填满缓存永远不会逐出一个租约。

### 6.4 顺序与声明（Order and claims）

- 诊断项按 `(unit id, in-unit insertion)` 排序；绑定按产生单元的顺序合并。`Spend` 是四个
  计数器的和，因此顺序无法改变它。
- 被贡献的键是排好序的贡献列表的函数；该列表的任何分区都不能改变结果，这正是 `parallel`
  安全的原因（P9）。
- 声明在匹配**之后串行**：工作线程产出一个 `ClaimPlan`，持有每个已匹配舱位的绑定与它读取的
  修订号——绝不是行——调度器在服务器线程上串接恰好一次 `settle`，由它自己派生出行（`08` §4.5）。
  区段从不预留；跨区段舱位不会被半预留。
- `ProbeSession.claimsAt` 只返回冻结的 `ClaimView`；本次尝试自己的行对另一个单元不可见，这
  在不引入可变账本成员的前提下去掉了顺序依赖。

## 7. 预算、取消、内存（Budget, cancellation, memory）

- `Spend(cells, sections)` 就是整本账。检查在派发时按它读取闭包的单元格与区段计费；区段计算按
  切片加一个区段计费；聚合不计费。`InstanceBudget` 属于该登记项，并按引用传给请求（`11` §4.6）。
- `CancelToken` 在每个单元边界以及内建循环中每 N 次读取时被轮询。自定义 `Expectation` 不得
  在没有读取的情况下循环；该契约有文档记录，卡死的自定义单元使调度器降级，而不使用
  `Thread.stop`。
- 调度器范围的 `retainedBytes` 在捕获之前预留；拒绝即 `DEFERRED`，绝不是静默缩减。
  `LevelSnapshot.estimatedBytes()` 是一个估算；区段副本、载荷 NBT 与实体记录都计入。
- `OOM` 不被转换为普通的 `Failed`；它释放预留、标记降级并被重新抛出。堆耗尽时不分配合成
  结果。
- 快照/`SectionStore` 内存：存储的当前条目是弱引用；快照持有强引用；旧修订随最后一个句柄
  消亡。`InstanceRecord` 持有租约，因此一个实例让一次捕获存活，而缓存从不如此。

## 8. 证明（Proofs）

1. **无活动访问。** 工作线程的字段图不含 `Level`、`ChunkAccess`、`BlockEntity`、`Entity`
   或 `SavedData`；只有冻结状态、载荷、id 与诊断项。测试：捕获后卸载世界，并调用每个
   `WorldView` 成员。
2. **无撕裂区段。** 每个区段是服务器线程上的一次 `PalettedContainer.copy()`；副本从不被
   写入。之后的变更是新修订，而不是对副本的改动。
3. **无陈旧 `Formed`。** `CaptureGate.evaluate(lease)` 在产物发布或结算之前运行：捕获之后关注
   区域内的标记回答 `CHANGED`（`Dropped(SNAPSHOT_CHANGED)`），而已关闭的句柄或被替换的世界回答
   `SUPERSEDED`（`Dropped(CAPTURE_INVALID)`）。槽位取代是调度器自己的决定，报告为
   `MatchCompletion.Discarded`。
4. **单一在飞任务。** `submitTicket` 与 `inFlight` 限于服务器线程；较新的请求并入，不派发
   第二个任务。核对比对票据。
5. **无双重声明。** 匹配只产生判定；一次尝试为一个 `(world, owner, siting)` 产出一个 `ClaimPlan`，
   而调用方串接一次 `settle`，重复执行会表现为翻倍的 `Spend.cells`。
6. **无缓存误命中。** 命中要求相同的 `plan`、`siting`、`unit` 与 `closureDigest`；摘要哈希
   该单元读取过的排序后的 `(SectionKey, contentDigest)` 对，因此冻结内容不同的区段是不同
   摘要，取自服务器线程上的冻结副本。没有纪元可比，也没有贡献的混合，因为摘要指名了该单元
   所看到的内容。
7. **无租约泄漏。** 租约在同一时刻恰好有一个持有者——实例、某个结果的子句柄，或调度器——
   且每条非派发路径都在 `finally` 中关闭调度器的那一份。被丢弃的结果泄漏的是
   `metrics().openHandles`，绝不是视图。
8. **无双重释放。** 关闭子句柄只递减它自己的引用；`InstanceRecord` 的引用让视图保持存活，
   因此一个被关闭两次的结果是宿主断言失败，而不是 use-after-free。
9. **确定性。** 结果是快照、蓝图、安放位、策略、冻结声明视图与取消的函数；线程选择、
   队列顺序与 `parallel` 都不是输入。

## 9. 反例（Counterexamples）

| # | 场景 | 要求 |
| --- | --- | --- |
| X1 | 箱子在 FORM 运行期间清空，尚未刷新 | 第 4 项对整次尝试不成立：`Dropped(SNAPSHOT_CHANGED)`，不结算 |
| X2 | `/cumulus build` 编辑 40 个单元格，同一 tick 内重检 | 刷新，或 `Deferred`；下一次尝试捕获 |
| X3 | 红石时钟每秒切换一个方块 20 次 | 一次刷新、零或一次检查；队列不增长 |
| X4 | 运行中途世界卸载 | `Dropped(CAPTURE_INVALID)`（世界已被替换），不结算 |
| X5 | 运行中途区块卸载，世界仍存活 | 结果对其修订保持自洽；下一次捕获为 `NOT_CAPTURED` |
| X6 | 快照外发期间共享区段推进 | 快照持有旧引用；由快照/票据/图章丢弃；绝不混合 |
| X7 | 某个检查的舱位横跨两个区段 | 屏障之后一次检查；脏的一半绝不产生缓存的 MATCH |
| X8 | 1.21.1 中 `y=64`、原点 `-64` | `floorDiv`，`cy=8`；裸 `>>` 会造成误命中 |
| X9 | 缓存的单元结果在两次运行中复用 | 诊断项重新铸造；缓存的单元结果绝不携带旧 id |
| X10 | 两次 COMMAND 提交相隔 200 ms | 一个运行中任务；第二个并入；票据丢弃较旧者 |
| X11 | 工作线程 OOM | 预留已释放，调度器降级，OOM 重新抛出；无伪造结果 |
| X12 | 实例在 POSTED 之后被移除 | 不结算；终态 `Discarded(INSTANCE_REMOVED)` |
| X13 | 提交在派发前被合并掉 | 调度器在其 `finally` 中关闭租约；`openHandles` 回到基线 |
| X14 | 牛从围栏的洞里走出，没有方块变更 | 窗口差分改变现存集合，该窗口的单元变脏，下一次 `check` 重跑它们 |
| X15 | 一个冷运行时 `BUDGET_EXHAUSTED` 的计划热运行 | 四项上的 `Spend` 相同；仍是 `Undecided` |

## 10. 集成差异（Integration deltas）

- `11`：`MatchRequest` 携带 ticket、安放位、租约与一个预算引用，不再有 `Place`，也没有阶段；
  `MatchPolicy` 新增 `maxSectionsPerUnit`、`CacheMode` 与 `parallel`；`MatchResult` 有六个
  分支并带子租约句柄，`Admitted` 携带计划中的声明与它读到的修订，且只有 `settle` 产出
  `Formed`；`MatchSession` 保持内部且一次性；门禁与计费见 `11` §4.6。
- `16`：以 `LevelSnapshot`/`SectionSnapshot`（可选 `SectionStore`）取代逐单元格捕获；新增
  `MatchScheduler`、带窗口索引的 `DirtyIndex`、mixin、冻结声明视图、`AttemptCache`；
  `SavedClaims` 结算保持仅服务器。
- `02`：服务器/工作线程表；每服务器一个调度器；快照/存储/脏的所有权；无 tick。
- `06`：`WorldView` 默认 `chunkGrid()`/`chunk()`/`summary()`；`ChunkContent`、
  `OccupancySummary`；`NestedLevelSnapshot` 命名。
- `07`：`ProbeSession.claimsAt` 只读取冻结声明视图；`ExpectationMeta.cost` 为工作单元；
  自定义期望取消契约。
- `08`：声明在匹配之后串行；无逐区段预留；一个 `ClaimMatrix` 谓词。
- `13`：为缓存诊断项提供 `Finding.withId`；`SC-MC-06xx` 诊断码。
- `18`：X1–X15 与门禁测试。
- `19`：决策记录 R16–R20。

## 11. 待决问题（Open questions）

| Id | 问题 | 对应 |
| --- | --- | --- |
| Q-1 | 逐 tick 窗口差分的代价是每个窗口现存的实体；每 tick 200 个窗口可以接受，还是宿主需要一个共享空间索引？ | `22` §16 Q2 |

由 `22` 定论：方块变更引发的重检与其他任何尝试一样结算声明（§10.3），并行区段检查是
`MatchPolicy.parallel`，它既不改变值也不改变 `Spend`（§9.6）。`SectionStore` 与并行的启用
阈值属于宿主调优，不是设计问题。
