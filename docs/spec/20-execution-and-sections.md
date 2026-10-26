# 20 — Execution, snapshots, dirty state, and section cache

> Accepted design, replacing the earlier tick-slicing proposal. Serves R-02, R-03, R-08;
> touches I-1, I-4, I-15. The kernel stays synchronous and pure; everything asynchronous is
> host-side (`ServerState`). `11` describes the kernel call this layer invokes; `16` describes the
> Minecraft implementation.

## 1. Decisions in one page

1. **No tick slicing of checking.** `StructureKernel.check` runs to completion. There is no
   `begin`/`advance`, no `WorkSlice`, and no clock in a kernel signature.
2. **Server thread** owns capture, dirty refresh, ledger freeze, settlement, world edits, sinks and
   events. **Worker threads** run `check`, `derive`, `solve` and `PlanValidator` over an immutable
   snapshot. The kernel receives no executor, future or callback.
3. **Snapshot is section-based.** One `SectionSnapshot` per 16³ world section holds a frozen
   `PalettedContainer<BlockState>` copy; air-only sections are a singleton. No per-cell
   `OccupantSnapshot` array for block states. **Default is per-snapshot copies**; a shared
   `SectionStore` is opt-in and awaits profiling.
4. **Dirty is section/payload/entity.** `LevelChunk.setBlockState` marks a section;
   `Level.blockEntityChanged` marks a payload position only if watched; entity join/leave marks
   its footprint, and **entity movement is found by a per-tick window diff on the server thread**,
   not by a `setPos` hook; chunk unload makes the section `UNKNOWN`.
5. **One in-flight check per `(world, owner)`.** A newer request coalesces; it never races a
   running task. A result may be published or settled only while its lease answers `CURRENT`:
   `changeCount() == 0` and `isValid()` (§5.4). The slot's `Ticket` decides coalescing inside the
   scheduler and never enters a capture.
6. **No per-section check phase.** Section `Computation`s run per section and call
   `draft.contribute`, a barrier folds their contributions with `Merge.combine` in canonical
   `SectionKey` order, then aggregate computations and checks run once per stage. The only
   parallelism is `MatchPolicy.parallel`; `ChunkGrid` is a read-view shape, not a work partition.
7. **One-shot refresh.** Dirty sections are recopied on the server thread at dispatch, not on a
   per-tick checking pump. If the cap is exceeded, the request is deferred; the slot stays dirty.
   A capture and a no-op refresh charge the plan-closure section count either way.
8. **The instance owns its capture.** `InstanceRecord` is the only owner; `Admitted` and `Formed`
   hold child handles, `retain(PinBudget)` can refuse, and `retainedBytes` (never evicted) is
   separate from `cacheBytes` (LRU). A viewer can therefore never push a capture into `DEFERRED`.

## 2. Thread and ownership model

| Phase | Thread | Reads | Writes |
| --- | --- | --- | --- |
| capture / refresh | server | `Level`, `ChunkAccess`, `LevelChunk`, `BlockEntity`, `Entity` | `SectionStore`, current `LevelSnapshot` |
| check / derive / solve / validate | worker | `LevelSnapshot`, `Blueprint`, frozen `ClaimView`, `CancelToken` | result objects only |
| settle | server | `MatchRequest` + the admitted result, `SavedClaims` revision | `SavedData`, `RuntimeState` |
| apply | server | result/plan | world |
| render/log | server (side effect) or worker (pure data) | `FindingBundle` | host sink |

- `LevelSnapshot` (root, implements `WorldView`) and `NestedLevelSnapshot` (child, implements
  `NestedView`) live in `dev.vfyjxf.cumulus.mc.capture`, outside `kernel.**`.
- `MatchScheduler` lives in `ServerState`; one per server, not per world.
- The kernel never imports `ChunkAccess`, `PalettedContainer`, `CompoundTag` or `Level`.

## 3. Host scheduler

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

### 3.1 Admission and coalescing

- A slot is keyed by `(world, owner)`. `Place` is a host-side target: the scheduler resolves it
  through the facade's `siting(blueprint, origin)` — one anchor cell — and the kernel request then
  carries a plain `Siting` (`11` §3, `22` §4.4).
- A submit runs `check` and nothing more: the result carries the planned claim and the revision it
  read, and no claim is written. Registration is a second, explicit call — `submitForm(admitted)`
  chains one `settle` on the server thread — so a preview, an overlay, a probe and an AUTO pass all
  stop at `Admitted`, and only the caller that wants the claim pays for the write.
- One task per key may be `QUEUED`/`RUNNING`/`POSTED`. A newer submit increments
  `submitTicket`; it does **not** start a second task. It either:
  - marks the slot dirty with the new request as `pending`, or
  - if the current task is still `QUEUED`, replaces its pending request.
  When the running task posts, the scheduler compares its own ticket; a stale ticket discards the
  attempt as `MatchCompletion.Discarded` and the pending request is dispatched next. A coalesced-away
  submit's lease is closed by the scheduler in a `finally`, never by the caller.
- This keeps W-3 true without relying on cooperative cancellation to retire a `RUNNING` task.
  `cancel()` is only for unload/stop/removal/user abort, not for "newer request wins".
- Every attempt captures. A retry is a new attempt over a fresh capture, and a snapshot never
  blends two capture revisions; a unit whose `cellStamp` held is reused **in the next attempt**
  through `AttemptCache` (§6.3), never by re-serving the old snapshot.
- The lease is a move, in exactly one of three places: the result that holds it, the request it was
  moved into, or the scheduler. `submitForm(admitted)` moves it into the `FORM` request. The
  scheduler's `finally` closes the lease only for a request that produced no result; once a request
  returns `Admitted`, its lease leaves queue management, belongs to whoever holds that result, and is
  closed by `settle` (or by a caller that only previewed).
- `INTERACTIVE` holds one reserved permit; `AUTO` gets `max(0, threads - 1)`. On a one-worker host
  `AUTO` is disabled, not starved, and `/cumulus workers` says so. `COMMAND` shares the remainder.
  `AUTO` without a permit is `DEFERRED` and re-probed by `MatchScheduler.tick()`, never by W3.
- `AUTO` runs `PROBE` only. If it passes and the instance is still a candidate, the scheduler
  submits `FORM` as a separate request; `COMMAND`/`INTERACTIVE` may chain the full stage list.
  This keeps AUTO tasks short, cancellable and coalescible.
- `INTERACTIVE` under `syncMaxCells` (default 4096) may run inline through the same `check` and
  `settle` bodies. It uses the same `FrozenLedger`, snapshot check and `CancelToken`; the sync path is
  not a separate semantic path.
- `FrozenLedger.readBox` is the union of every cell the plan, anchor resolution, seam pairing and
  settle scope can query, closed under seam dilation. A query outside it is a host assertion
  failure (`SC-MC-06xx`), not `Undecided(LEDGER_UNAVAILABLE)`; the async and inline paths share the
  same frozen ledger.

### 3.2 Lifecycle

- `ServerAboutToStartEvent` opens; `ServerStoppingEvent` closes.
- `LevelEvent.Unload` cancels that world's slots, bumps the level epoch, releases snapshots and
  every claim row of that world's instances (`ClaimLedger.release`, `23` §5); no settle after it.
- `dropInstance` is the single removal entry point: cancel → dirty unwatch → release the instance's
  claim rows (`ClaimLedger.release`) → close the instance's own lease reference → release the
  record; a `POSTED` result for it is `Dropped(CAPTURE_INVALID)` (the lease is closed).
- `onTerminal` is always delivered once for `SETTLED`, `DROPPED`, `FAILED` and `CANCELLED`,
  including unload/stop; a terminal path never leaves the scheduler holding an unclosed lease.
  `onSettled` is a success-only convenience, not the terminal signal.
- `close()` refuses new work, cancels, shuts down, waits briefly, then drains. `done()` always
  completes (`Discarded` on stop), so no caller awaits forever.

### 3.3 Failure

| Fault | Handling |
| --- | --- |
| kernel `RuntimeException` | `Undecided(WORKER_FAILED)` + `SC-MC-0601`; worker survives |
| `OutOfMemoryError` | release the pin, mark scheduler degraded, **rethrow**; no synthetic result |
| other `VirtualMachineError`/`LinkageError` | release in `finally`, rethrow; slot `SLOT_BROKEN` |
| `RejectedExecutionException` | return permit, leave dirty; `MatchCompletion.Discarded` or `DEFERRED` |
| deadline | host watchdog calls `CancelToken.cancel(DEADLINE)`; kernel polls at read intervals |
| wedged custom expectation | no `Thread.stop`; after a second interval mark scheduler degraded, replace worker |
| repeated failure | three consecutive failures pause `AUTO`; `INTERACTIVE` clears it |

## 4. Snapshot

### 4.1 Representation

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
    record Air(long revision) implements SectionSlot {}
    record Present(SectionSnapshot section) implements SectionSlot {}
    record Refused(ReadStatus status, FindingCode code) implements SectionSlot {}
}
record CellPayload(LatticeKey cell, OccupantDescriptor descriptor, List<Trait<?>> traits) {}
final class LevelSnapshot implements WorldView {
    SnapshotId snapshotId();          // capture identity; a new object = a new id
    long estimatedBytes();
    SectionSlot slot(SectionKey key);
    @Nullable CellPayload payload(LatticePoint cell);
    List<CapturedEntity> entities(Cuboid box);
    List<Finding> findings();
}
final class NestedLevelSnapshot implements NestedView { /* child frame over the same bytes */ }

interface SnapshotLease extends AutoCloseable {     // one owner: InstanceRecord
    SnapshotId id();
    WorldView view();
    long changeCount();                                 // marks since capture; zero means untouched
    boolean isValid();                                  // the world still exists and this handle is open
    Outcome<SnapshotLease> retain(PinBudget budget);    // Pinned | Missing(SC-MC-0502)
    boolean isOpen();
    @Override void close();                             // releases this handle only
}
```

- `SectionKey` is the world 16³ index (`x >> 4` etc.); it is not a cache key.
- `Air` is read and empty: `READABLE`, with a `revision` (the content epoch) for dirty/cache checks.
  It is not `NOT_CAPTURED`.
- `Refused` carries a `ReadStatus` and a finding code; `occupant` is `null`.
- `CellPayload` carries a resolved descriptor and trait list for a watched BE or entity;
  `occupant(cell)` stamps the final `SnapshotId` into the `OccupantRef` lazily. Block states are
  built lazily from `BlockState`. There is no raw payload channel.
- `LevelSnapshot` runs the resolver for tracked BEs/entities; a missing required trait is
  `NOT_CAPTURED`, never a zeroed value.
- Entity footprints are indexed cell→occupant before sealing; `occupant(cell)` returns the entity
  for every covered cell, even if the block section is `Air`. A shadowed cell is `NOT_CAPTURED`.
- `SnapshotId` is capture identity and is never reused; a content-equal refresh returns the same
  `LevelSnapshot` object (no new id). `SectionSnapshot.captureRevision` is the content epoch used by
  the section cache; it is not a substitute for `SnapshotId`.

### 4.2 Capture

One server-thread function, no tick pipeline:

```
capture(req, issues) -> Captured(LevelSnapshot, CaptureIssues) | TooLarge | Unavailable
for each section in y,z,x order:
    chunk = serverChunkCache.getChunkNow(sx, sz)   // never Level.getChunk
    if chunk == null || !chunk.isLoaded()          -> Refused(NOT_CAPTURED, SC-MC-0306)
    if index outside build height                  -> Refused(OUT_OF_BOUNDS, SC-MC-0505)
    if section.hasOnlyAir()                        -> Air(contentEpoch++)
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

`SC-MC-0501` is the capture-over-cap code and nothing else; `SC-MC-0502` belongs to `retain`
refusing a pin and is never used for a payload.

- `getChunkNow` is main-thread only and never loads/tickets; a chunk that is not fully loaded
  (including one whose post-load/block-entity registration is pending) is `NOT_CAPTURED`.
- Copy sections, not cells. Skip `hasOnlyAir`. `PalettedContainer.copy()` is the only source of
  `FrozenStates`; a copy is never written.
- BE payload uses `saveWithoutMetadata` (type from `be.getType()`), so position/id churn does not
  invalidate content. The resolver fills `requiredTraits`; a missing trait is `NOT_CAPTURED`.
  After the resolver returns, re-read the block state at that cell; if it differs from the copied
  state, a mod write occurred during capture: refuse the section, never publish a torn snapshot.
- Entities are capped tracked-type-first; their footprints are indexed cell→occupant before
  sealing. A covered cell answers the entity, even if the block section is `Air`; a shadowed cell
  is `NOT_CAPTURED`, never air.
- Caps: `maxCells`, `maxSections`, `maxBytes`, `maxEntities`, `maxPayloads`; a cap is a sealed
  `TooLarge`/`Refused`, never a partial snapshot treated as complete.
- `SnapshotId` is stamped last, after the reads. `SectionSnapshot.captureRevision` is the content
  epoch used by the section cache; it is independent of the `SnapshotId`.

### 4.3 Section ownership and versions

- **Default:** `LevelSnapshot` owns its section copies directly. There is no shared store, no weak
  map, no GC-dependent revision. A snapshot is released when its last handle closes — the
  `InstanceRecord`'s reference, a result's child handle, or the scheduler's.
- **Optional `SectionStore`:** a per-world shared cache may be enabled after profiling shows
  overlapping captures or repeated large copies are hot. It holds the current `SectionSnapshot`
  per `SectionKey`; values are weak, snapshots hold the strong references. `intern` returns the
  live entry when `contentEquals` holds. A weak entry that is collected and re-interned may get a
  new content epoch; that is a conservative cache miss, never a false hit. The store is a cache,
  not a durability guarantee.
- The store is never a resolver: a sealed `LevelSnapshot` holds direct `SectionSnapshot` refs.
  Lazy `snapshot.section(k) -> store.get(k)` is forbidden, because a section could advance
  mid-check and blend revisions.
- `SnapshotId` is capture identity, allocated after the reads and never reused. Content identity
  is `SectionSnapshot.captureRevision`; a no-op refresh returns the same `LevelSnapshot` object, so
  no new `SnapshotId` and no new content epoch.
- The only content epoch is `SectionSnapshot.captureRevision`. `SnapshotId` is the identity the gate
  compares; neither is a cache key, and a cache hit compares digests, never epochs.
- **Ownership.** `InstanceRecord` is the only owner of a capture for the instance's lifetime;
  `Admitted` and `Formed` hold child handles and release only their own reference. `derive` reads
  `StructureContext.at(instance)` — frames, never the lease — and a remedy provider receives a
  `ContextView`, never a lease. There is no forced close after N ticks: the host may close the
  instance's own reference on `dropInstance` or a policy eviction, and closing it is exactly one
  release of that reference, not a sweep of outstanding handles.
- **Two ledgers.** `retain(PinBudget)` charges `retainedBytes`, which is never evicted, and can
  refuse with `Missing(SC-MC-0502)`; `cacheBytes` is LRU and lives with `AttemptCache` (§6.3).
  Because a viewer only ever touches `cacheBytes`, it can never push a capture into `DEFERRED`.

## 5. Dirty marking and refresh

### 5.1 Hooks

| Mutation | Hook | Dirty |
| --- | --- | --- |
| block state | `LevelChunk.setBlockState` (all overloads) | `SectionKey` of the position |
| BE body | `Level.blockEntityChanged(BlockPos)` **only** | payload position iff watched |
| entity join/leave | `EntityJoinLevelEvent`/`EntityLeaveLevelEvent` | footprint sections + every window containing the footprint |
| entity move | **per-tick window diff on the server thread**: each window's live `cell → EntityKey` set against the captured one | the units that read that window |
| chunk load/unload | `ChunkEvent.Load`/`Unload` | load: `DIRTY`; unload: `UNKNOWN` |
| claim settle/release | `SavedClaims.settle` on a write, `SavedClaims.release` on removal | claim cells ∩ windows |
| datapack/config | reload hooks | whole instance |

`Level.blockEntityChanged` is the single payload choke point: `BlockEntity.setChanged` calls it, and
direct callers reach it too. A mod override that skips `super` is not covered; `loadWithComponents`
and the rollback restore path must call `markPayload` explicitly. `ChunkHolder.blockChanged` is
network bookkeeping, not a dirty API. A mutation that bypasses all hooks is covered by an aged
revalidation sweep; it is not claimed as exact.

Entity movement has no block hook. Every tick, for each watched window, the server thread compares
the live set of `EntityKey(uuid, type, digest)` in the window's box with the captured set, bounded by
the window's entity count rather than its cell count; any difference dirties the units that read
that window. This is the outward-move witness: an entity that leaves the window changes the live set,
so a cow walking out through a hole is seen without a `setPos` hook and without walking the volume.
Join/leave marks the footprint immediately. A type wider than `MAX_ENTITY_SPAN = 3` cells on any axis
is refused at DESIGN (`SC-BAY-0110`), so a window can never be blind to part of its occupant.

### 5.2 Index and state

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

- Four indexes: section, payload position, entity footprint, and `window → Set<InstanceRef>`. A
  window is a watched region, not only a query, so per-tick diffing (§5.1) knows who to dirty.
  Marks are O(1) and do not iterate all watches.
- `stale()` is the union of section, payload and entity revisions; a payload mark must not require
  a section copy, and an entity move must not dirty every block in its footprint by copying.
- `cellStamp(unit)` is the mark of the cells that unit read, taken since capture; it feeds the host's
  dirty index, while the gate's `changeCount` is the watch's single counter (§5.4). `dirtyEpoch`
  survives only as a watch-wide monotone counter for metrics and never appears in the gate. `UNKNOWN`
  is unloaded/denied, never `BROKEN` and never air;
  it re-enters on `ChunkEvent.Load` or the first ticking tick. `BROKEN` means the request cannot be
  built, not a cap hit.
- A no-op refresh (`contentEquals`) keeps the same `LevelSnapshot` and clears `DIRTY`; it does not
  create a new id and does not force a check.

### 5.3 Refresh

- One owner: `WatchRefresh.refresh(watch) -> Ready | Deferred`, called once by the scheduler at
  submit on the server thread. The dirty tick hook may pre-warm, but it never starts a check, and a
  submit runs one `check` against the refreshed view: whether anything is written is decided by the
  caller following it with `settle` (§3.1), never by a request field.
- Refresh is atomic over the watch's sections, payloads and entity window. It either makes all
  stale pieces fresh or returns `Deferred` with the watch still dirty; it never publishes a mix of
  fresh and stale pieces.
- A refresh is charged the section count and the cells it re-reads, out of the same `cells` /
  `sections` budget the attempt uses. If the cap is exceeded, `Deferred`
  and the scheduler re-probes later.
- A no-op refresh returns the same object, leaves `dirtyEpoch` unchanged (the mark was consumed)
  and yields zero checks — and still charges the plan-closure section count, so the `sections` leg
  is cache-independent (`22` §9.5).

### 5.4 Capture check

`CaptureGate.evaluate(request.lease())` reads two facts off the lease, **after** the units ran and
**before** the outcome is published or settled:

| Fact | Read from | Fails when |
| --- | --- | --- |
| the watch is clean | `lease.changeCount() == 0` | the watched region was marked since capture |
| the capture is valid | `lease.isValid()` | its world was replaced, or the handle was closed |

A mark answers `CHANGED` and drops the attempt as `Dropped(SNAPSHOT_CHANGED)`, `SC-ENG-0003`; an
invalid capture answers `SUPERSEDED` and drops it as `Dropped(CAPTURE_INVALID)`, `SC-ENG-0006`.
Nothing is written, so a dirty-but-not-refreshed mutation cannot produce a `Formed`. This is the
single check for the worker, the inline path and a separated `settle`. Host lifecycle stops land on
the same two causes: instance removal closes the lease, and a world unload or a server stop replaces
the world.

Slot supersession is not part of this check. The scheduler owns the ticket, compares it when the
worker posts, and reports the abandonment as `MatchCompletion.Discarded` (`19` R37).

The kernel carries one `changeCount` for the watch, so the check is deliberately conservative:
any mark after capture drops the whole attempt. The per-unit form (`cellStamp(unit)`, dropping only
the units that read a marked cell) belongs to the host's dirty index and only pays off together with
the attempt cache; `ReadSet`/`ChangeSet` target reuse in the next attempt and are not a permission
to make the check optimistic.

## 6. Section pass, barrier, cache

### 6.1 Grid and content

```java
record ChunkKey(int cx, int cy, int cz) implements Comparable<ChunkKey> {}
record ChunkGrid(int cellSize, LatticePoint origin) {
    ChunkKey chunkOf(LatticePoint p);   // floorDiv per axis
    Cuboid boundsOf(ChunkKey k);
    int chunkCount(Cuboid box);
    List<ChunkKey> chunksOf(Cuboid box);
}
```

`ChunkGrid`/`ChunkKey` size the lattice only; a read is answered per section, through
`WorldView.slot(SectionKey)` (`06` §3.3). There is no per-chunk facade on `WorldView`: `cellSize` is
data, not a constant; the adapter passes 16. `chunkOf` uses `floorDiv`, never `>>` or `/`, so negative
`y` and non-zero origins work.

### 6.2 Section pass and barrier

| Phase | What runs | Order |
| --- | --- | --- |
| section pass | each section `Computation` once per section of `region ∩ sectionSet`; each run calls `draft.contribute(key, partial)` | sections sorted `(x, z, y)` |
| barrier | each contributed `ContextKey` folded by `Merge.combine` over its contributions sorted by `SectionKey` | one key at a time |
| aggregate | aggregate `Computation`s in Kahn order over `inputs()`, then every `Check` of the stage | `(phase, declaration)` — declaration order inside each group |

- A check never runs before the barrier, and no check is ever split per section: there is no
  per-section check phase, so a chunk-aligned siting and a straddling one reach the same verdict.
- A refused or air section where a payload was required makes that key `Missing`
  (`SC-FOC-0301`); it is never a short fold, because a contribution is `Missing` for the whole key.
- Entity windows are read scope like cells: a check that reads a window reads it once, in the
  aggregate phase. The window query is charged as sections at plan closure, whether or not the
  window's box overlaps a section that was copied.
- `parallel` is `MatchPolicy.parallel` and lives inside the determinism tuple. It may reorder the
  section pass, never the barrier — the fold sees the sorted contribution list either way — and
  changes neither `Spend` nor finding order (§8).

### 6.3 Cache

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

- `PlanId` is one interned `long` allocated at `Blueprint` build; a datapack rebuild gets a new id
  and a cold cache. It never hashes an expectation id or a lambda identity.
- The cache stores contributions and unit outcomes, **never folded values**, so no `Merge` is
  consulted and a hit cannot change a result. Inside a `CacheKey`, a unit and its `closureDigest`
  are what make a hit sound; the plan and siting components scope it to one blueprint placement.
- `CacheMode` gates what may be stored: `OFF` nothing, `PARTIALS` the section pass's contributions,
  `OUTCOMES` contributions and unit outcomes. `closureDigest` covers every section the unit read,
  and `MatchPolicy.maxSectionsPerUnit` bounds that list.
- A hit changes no value and no `Spend`: cells and sections are charged on dispatch regardless, and capture
  and refresh are closure-sized regardless (`22` §9.5). Warm and cold runs of one plan therefore
  have the same `Spend` and the same budget fold.
- Validity is `closureDigest` equality. A differing digest is a miss, never a mutation; there is no
  `invalidate` operation and no epoch comparison. `NOT_CAPTURED` is never served.
- A cached `MATCH` keeps its `OccupantRef`s, and each ref carries the `SnapshotId` it was minted
  under (`19` R8, §4.1); on reuse the refs are re-stamped to the new capture's id, so a hit never
  forges a binding from an older capture.
- Cache is per `ServerLevel`, owned with the scheduler, cleared on unload, and bounded by
  `cacheBytes`. `retainedBytes` is untouched by it, so a viewer filling the cache never evicts a
  lease.

### 6.4 Order and claims

- Findings are ordered by `(unit id, in-unit insertion)`; bindings merge in producing unit order.
  `Spend` is a sum of four counters, so order cannot change it.
- A contributed key is a function of the sorted contribution list; a partition of that list cannot
  change the result, which is why `parallel` is safe (P9).
- Claims are **serial after** the match: the worker produces one `ClaimPlan` holding every matched
  bay's bindings and the revision it read — never rows — and the scheduler chains exactly one `settle`
  on the server thread, which derives the rows itself (`08` §4.5). A section
  never reserves; a spanning bay is not half-reserved.
- `ProbeSession.claimsAt` returns the frozen `ClaimView` only; the attempt's own rows are not
  visible to another unit, which removes the order dependency without a mutable ledger member.

## 7. Budget, cancellation, memory

- `Spend(cells, sections)` is the whole ledger. A check is charged the cells and sections of its
  read closure on dispatch; a section computation is charged its slice plus one section; an
  aggregate is charged nothing. `InstanceBudget` belongs to the slot and is passed to the request
  by reference (`11` §4.6).
- `CancelToken` is polled at every unit boundary and every N reads inside built-in loops. A custom
  `Expectation` must not loop without a read; the contract is documented, and a wedged custom unit
  degrades the scheduler without `Thread.stop`.
- Scheduler-wide `retainedBytes` is reserved before capture; a refusal is `DEFERRED`, never a
  silent shrink. `LevelSnapshot.estimatedBytes()` is an estimate; section copies, payload NBT and
  entity records are counted.
- `OOM` is not converted into a normal `Failed`; it releases the pin, marks degraded and is
  rethrown. No allocation of a synthetic result on an exhausted heap.
- Snapshot/`SectionStore` memory: current store entries are weak; snapshots hold strong refs; old
  revisions die with the last handle. `InstanceRecord` holds the lease, so an instance keeps one
  capture alive and the cache never does.

## 8. Proofs

1. **No live access.** A worker's field graph contains no `Level`, `ChunkAccess`, `BlockEntity`,
   `Entity` or `SavedData`; only frozen states, payloads, ids and findings. Test: unload the level
   after capture and call every `WorldView` member.
2. **No torn section.** Each section is one `PalettedContainer.copy()` on the server thread; the
   copy is never written. A later change is a new revision, not a mutation of the copy.
3. **No stale `Formed`.** `CaptureGate.evaluate(lease)` runs before the outcome or the settlement: a
   mark in the watched region after capture answers `CHANGED` (`Dropped(SNAPSHOT_CHANGED)`), and a
   closed handle or a replaced world answers `SUPERSEDED` (`Dropped(CAPTURE_INVALID)`). Slot
   supersession is the scheduler's own decision, reported as `MatchCompletion.Discarded`.
4. **Single in-flight.** `submitTicket` and `inFlight` are server-thread-confined; a newer request
   coalesces, it does not dispatch a second task. The check compares the ticket.
5. **No double claim.** The match produces verdicts only; one attempt produces one `ClaimPlan` for
   one `(world, owner, siting)` and the caller chains one `settle`, and a duplicated execution shows as a
   doubled `Spend.cells`.
6. **No cache false hit.** A hit requires the same `plan`, `siting`, `unit` and `closureDigest`; the
   digest hashes the sorted `(SectionKey, contentDigest)` pairs the unit read, so a section whose
   frozen content differs is a different digest, taken from the frozen copy on the server thread.
   There is no epoch to compare and no blend of contributions, because the digest names the
   content the unit saw.
7. **No lease leak.** A lease has exactly one holder at a time — the instance, a result's child
   handle, or the scheduler — and every non-dispatch path closes the scheduler's copy in a
   `finally`. `metrics().openHandles` is what a dropped result leaks, never the view.
8. **No double release.** Closing a child handle decrements only its own reference; the
   `InstanceRecord` reference keeps the view alive, so a result closed twice is a host assertion
   failure rather than a use-after-free.
9. **Determinism.** The result is a function of the snapshot, blueprint, siting, policy,
   frozen claim view and cancellation; thread choice, queue order and `parallel` are not inputs.

## 9. Counterexamples

| # | Scenario | Required |
| --- | --- | --- |
| X1 | chest empties while FORM runs, refresh not yet | `changeCount` fails for the whole attempt: `Dropped(SNAPSHOT_CHANGED)`, no settle |
| X2 | `/cumulus build` edits 40 cells, re-check same tick | refresh, or `Deferred`; the next attempt captures |
| X3 | redstone clock toggles one block 20×/s | one refresh, zero/one check; no queue growth |
| X4 | level unloads mid-run | `Dropped(CAPTURE_INVALID)` (the world was replaced), no settle |
| X5 | chunk unloads mid-run, level alive | result remains coherent for its revision; next capture `NOT_CAPTURED` |
| X6 | shared section advances while a snapshot is out | the snapshot holds the old refs; the next capture sees the new revision; never blends |
| X7 | a check whose bay straddles two sections | one check after the barrier; a dirty half never yields a cached MATCH |
| X8 | 1.21.1 `y=64` with origin `-64` | `floorDiv`, `cy=8`; a raw `>>` is a false hit |
| X9 | cached outcome reused in two runs | findings re-minted; a cached outcome never carries an old id |
| X10 | two COMMAND submits 200 ms apart | one running task; second coalesces; ticket discards older |
| X11 | OOM in worker | pin released, scheduler degraded, OOM rethrown; no fake result |
| X12 | instance removed after POSTED | no settle; terminal `Discarded(INSTANCE_REMOVED)` |
| X13 | submit coalesced away before dispatch | the scheduler closes the lease in its `finally`; `openHandles` back to baseline |
| X14 | cow walks out of a pen through a hole, no block changes | the window diff changes the live set, the window's units are dirty, the next `check` re-runs them |
| X15 | warm run of a plan that was `BUDGET_EXHAUSTED` cold | the same `Spend` on all four legs; still `Undecided` |

## 10. Integration deltas

- `11`: `MatchRequest` carries ticket, siting, lease and a budget reference, and no `Place` and no
  stage; `MatchPolicy` gains `maxSectionsPerUnit`, `CacheMode` and `parallel`; `MatchResult` has six
  arms with child lease handles, `Admitted` carrying the planned claim and the revision it read, and
  only `settle` producing `Formed`; `MatchSession` remains internal and one-shot; the gate and
  billing are `11` §4.6.
- `16`: replace per-cell capture with `LevelSnapshot`/`SectionSnapshot` (optional `SectionStore`);
  add `MatchScheduler`, `DirtyIndex` with the window index, mixins, frozen claim view,
  `AttemptCache`; keep `SavedClaims` settle server-only.
- `02`: server/worker table; one scheduler per server; snapshot/store/dirty ownership; no tick.
- `06`: `WorldView` is the section model only — `slot(SectionKey)` answers a read, and the
  `chunkGrid()`/`chunk()`/`summary()` facades with `ChunkContent`/`OccupancySummary` are withdrawn;
  `NestedLevelSnapshot` naming.
- `07`: `ProbeSession.claimsAt` reads the frozen claim view only; `ExpectationMeta.cost` work
  units; custom expectation cancellation contract.
- `08`: claims serial after the match; no per-section reservation; one `ClaimMatrix` predicate.
- `13`: `Finding.withId` for cached findings; `SC-MC-06xx` codes.
- `18`: X1–X15 and gate tests.
- `19`: decision record R16–R20.

## 11. Open questions

| Id | Question | Maps to |
| --- | --- | --- |
| Q-1 | The per-tick window diff costs the entities present per window; is 200 windows per tick acceptable, or does the host need a shared spatial index? | `22` §16 Q2 |

Settled by `22`: a block-change re-check settles claims like any other attempt (§10.3), and
parallel section checking is `MatchPolicy.parallel`, which changes neither value nor `Spend`
(§9.6). The `SectionStore` and parallel-enable thresholds are host tuning, not design questions.
