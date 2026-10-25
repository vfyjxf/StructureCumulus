# Worker / async design brief — round 1 (internal)

> Status: internal brief, not a final spec. Four designers write one draft each; four critics
> attack them; the lead integrates the survivors into the numbered spec.

## 0. Approved core (do not relitigate)

1. **No tick slicing of checking.** `match` / `derive` / `solve` / `PlanValidator` run to
   completion on a worker. There is no `begin`/`advance`/`WorkSlice` public API.
2. **Server thread only:** capture, dirty refresh, claim settlement, world edits, chat/log sinks,
   entity/world events.
3. **Snapshot is section-based:** copy `PalettedContainer<BlockState>` per 16³
   `LevelChunkSection`, skip `hasOnlyAir()`, store only tracked BE/entity payload positions.
   No per-cell `OccupantSnapshot` array for block states.
4. **Capture is one-shot on the server thread**, using `ServerChunkCache.getChunkNow` (never
   `Level.getChunk`, which loads/tickets). Unloaded chunk ⇒ `NOT_CAPTURED`, never air. Too large
   ⇒ `CAPTURE_LIMIT`.
5. **Dirty is section + payload + entity:** mixin on `LevelChunk.setBlockState` marks the
   16³ section; mixin on `BlockEntity.setChanged` marks only NBT-sensitive positions; entity
   join/leave/move marks affected sections. `version` + `dirtyForCheck` + `checkInFlight`
   coalesce and discard stale results.
6. **Chunked checking uses world 16³ sections** (not a custom grid): local cellwise steps,
   global atomic/claim/seam/anchor steps; `SectionResult` cache; dirty section invalidation;
   default sequential, parallel only with `AGGREGATE`.
7. **Budget is work units** (`cellReads`/`occupantReads`/`sections`/`steps`); no `nanoTime`, no
   `TimeSource` in the kernel. A host watchdog may cancel via `CancelToken`.
8. **Kernel purity:** worker sees only `WorldView`/`StructureSnapshot`; never `Level`,
   `BlockEntity`, `Entity`, `ChunkAccess`, or `SavedData`.

## 1. Current spec anchors

Read first:

- `docs/spec/01-glossary.md` — names and owners.
- `docs/spec/02-architecture.md` — threading, source sets, package graph.
- `docs/spec/06-occupants.md` — `WorldView`, `ReadStatus`, `OccupantSnapshot`, capture SPI.
- `docs/spec/07-expectations.md` — `Expectation`, `ExpectationMeta.cost`, atomic bays.
- `docs/spec/08-claims.md` — `ClaimLedger`, reservation, `SETTLE`.
- `docs/spec/11-engine.md` — `MatchRequest`, `MatchResult`, `ReadSet`, `ChangeSet`,
  `InvalidationIndex`, `InstanceRef`, `PrescreenIndex`.
- `docs/spec/12-context.md` — `Deriver`, `StructureContext`, `RuntimeState`.
- `docs/spec/15-spi.md` — `StructureKernel`, `Host`, `Catalogs`.
- `docs/spec/16-adapter.md` — `LevelCapture`, `LevelSnapshot`, `WorldOccupants`, `SavedClaims`,
  `WorldEdits`, `ServerState`, `DatapackBlueprints`, `FindingOutput`, `ClientOverlay`.

Existing names you may reference, not rename: `MatchRequest`, `MatchPolicy`, `MatchResult`,
`MatchFacts`, `ReadSet`, `ChangeSet`, `InvalidationIndex`, `InstanceRef`, `Budget`, `Spend`,
`CancelToken`, `ClaimLedger`, `StructureSnapshot`, `SectionSnapshot`, `CellPayload`,
`DirtyIndex`, `SectionResult`, `SectionKey`, `SnapshotId`, `ReadStatus`, `LevelCapture`,
`LevelSnapshot`, `WorldOccupants`, `SavedClaims`, `WorldEdits`, `ServerState`.

## 2. Prior art to mine (not copy)

### Beecrasy `CachedLevel`

`https://github.com/khjxiaogu/Beecrasy/blob/master/src/main/java/com/khjxiaogu/beecrasy/utils/CachedLevel.java`

- `PalettedContainer<BlockState>[] sections`, one per 16³ section in an AABB.
- `getBlockState` = `x>>4`, `y>>4`, `z>>4`, section index, `container.get(localX,localY,localZ)`.
- `copy=true` → `section.getStates().copy()` for long-lived/async snapshots; `copy=false` → live
  reference for same-tick queries.
- `hasOnlyAir()` sections skipped; heightmap raw data copied; `CachedLevelReader` implements
  `LevelReader` but `getBlockEntity` returns null.
- Missing: BE/entity, dirty, version, `NOT_CAPTURED` (out-of-range returns air), thread safety
  contract for the copied container.

### Multiblocked2 `PatternSnapshot` / `MultiblockWorldSavedData` / mixins

`https://github.com/Low-Drag-MC/Multiblocked2` branch `1.21`:

- `api/pattern/snapshot/PatternSnapshot.java`: `trackedPositions`, `nbtSensitivePositions`,
  `Long2ObjectOpenHashMap<SnapshotEntry>`, `pendingPositions`, `fullyBuilt`, `version`,
  `dirtyForCheck`, `checkInFlight`; `tickCapture` drains 20 positions/tick;
  `markDirty`, `tryConsumeDirtyForCheck`, `tryClaimCheckSlot`.
- `api/pattern/snapshot/SnapshotEntry.java`: `(BlockState state, @Nullable CompoundTag beData)`.
- `api/pattern/MultiblockState.java`: snapshot-backed reads, `getTileEntity()` null in async,
  `getTileEntityData()` uses NBT, rotation normalization via `BlockState.rotate`.
- `api/pattern/MultiblockWorldSavedData.java`: `snapshotPosIndex` pos→snapshots; single daemon
  worker; `dispatchPendingChecks` coalesces one in-flight task per snapshot; worker result posts
  back to main thread; `releaseCheckSlot` last in `finally`; no whole-snapshot lock during check.
- `core/mixins/ChunkMixin.java`: injects at `LevelChunk.setBlockState` → `markPositionDirty`.
- `core/mixins/BlockEntityMixin.java`: injects `BlockEntity.setChanged` → only NBT-sensitive
  positions.

### Minecraft 1.21.1 facts (SourceScout, vanilla 1.21.1)

- `LevelChunkSection.SECTION_SIZE = 4096`, 16³; block states in `PalettedContainer<BlockState>`;
  `getStates()`, `hasOnlyAir()`, `maybeHas(Predicate<BlockState>)`.
- `PalettedContainer.copy()` exists in 1.21.1; `get(x,y,z)` O(1); `count(CountConsumer)` counts
  each palette value; `maybeHas` uses the palette.
- `ChunkAccess.getSections()` / `getSection(index)`; `LevelChunk.getSectionIndex(y)`;
  `LevelChunk.getBlockState` checks `hasOnlyAir` and reads the section.
- `ServerChunkCache.getChunkNow(cx,cz)` is main-thread-only and returns `null` if not loaded;
  `Level.getChunk` / `getChunk(x,z,status,true)` adds tickets and loads.
- `LevelChunk.getBlockEntity(pos, CHECK)`; `ChunkAccess.getBlockEntitiesPos()` enumerates real BEs.
- `LevelChunk.setBlockState` is the block mutation point; `BlockEntity.setChanged` is the BE
  mutation point; `ChunkHolder.blockChanged` is network-change tracking, not a general dirty API.
- `ServerChunkCache.isPositionTicking` / `ChunkHolder.getTickingChunk` expose ticking state;
  chunk load/unload events exist in NeoForge (`ChunkEvent.Load/Unload`).

## 3. Four design units

Each designer writes **one file** under `docs/spec/_work/worker-async/` and nothing else.

### W1 — `execution-model.md`

Questions:

1. Executor topology: per-server, per-world, or per-`ServerState`? Bounded pool size, daemon
   threads, naming, queue type, rejection/backpressure policy.
2. Task lifecycle: submit → run → post result to server thread → settle; how `MatchHandle` /
   `MatchTask` expose completion; sync fallback for small structures.
3. Interaction with `checkInFlight`: one in-flight task per snapshot, coalescing, dirty-while-
   running, latest-version-wins.
4. Shutdown: server stop, world unload, chunk unload, instance removal, executor shutdown,
   in-flight task cancellation, no leaks, no callbacks after unload.
5. Exceptions: worker throw, OOM, `RejectedExecutionException`, `Error`; how to turn into a
   finding/`Undecided` without killing the worker.
6. Observability: queue depth, active tasks, snapshot bytes, check latency, stale discards.

### W2 — `snapshot-handoff.md`

Questions:

1. `StructureSnapshot` representation: `SectionSnapshot[]`/map, `PalettedContainer` copies,
   payload map, entity index, `ReadStatus`, bounds, version.
2. Capture algorithm: `getChunkNow`, section index, skip air, BE positions, entity AABB,
   heightmap optional, cap, `CAPTURE_LIMIT`.
3. Freezing: `PalettedContainer.copy()` then no writes; who owns it; how to prevent accidental
   `set/acquire`; worker visibility.
4. Versioning: `SnapshotId`/`version` increments, stale result detection, relation to
   `dirtyForCheck`.
5. Memory: section copies, payload NBT, entity NBT, caches (`OccupantSnapshot` per section),
   retention across in-flight tasks, release after result.
6. Sharing: multiple structures at the same siting/sections; cache or one snapshot per instance;
   copy-on-write for dirty sections.

### W3 — `dirty-refresh.md`

Questions:

1. Dirty granularity: section vs payload position vs entity; how to map a block/BE/entity change.
2. Mixins vs NeoForge events; exact injection points; coverage gaps (pistons, explosions, AI,
   direct NBT writes); fallback sweep.
3. `DirtyIndex` structure: section→snapshots, payload→snapshots, entity→snapshots; O(1)
   marking; memory reclamation on unregister.
4. Refresh scheduling: when to refresh dirty sections (on dispatch? on tick?), one-shot vs
   bounded; how to avoid server-thread spikes; no check-tick state machine.
5. State machine: `CLEAN`, `DIRTY`, `REFRESHING`, `CHECKING`, `BROKEN`, `UNKNOWN`; transitions,
   coalescing, `dirtyForCheck`, `checkInFlight`, version mismatch.
6. Claims/config/blueprint reload/entity movement as dirty sources; priority and starvation.

### W4 — `section-checking.md`

Questions:

1. Section grid: world 16³ sections vs blueprint-local; orientation/siting mapping; section
   alignment; key type.
2. Plan split: `BOUNDS`, `CENSUS`, `ANCHOR`, `BAY`, `SEAM`, `CLAIM`, `CUSTOM`; local vs
   global; `CELLWISE` vs `ATOMIC` expectations; spanning bays.
3. Palette use: `count`, `maybeHas`, `hasOnlyAir`; census merge; block/air bulk checks.
4. `SectionResult` cache: key, version, dirty invalidation, clean reuse, global-step
   invalidation.
5. Merge: verdict fold, finding order, `FIRST` vs `AGGREGATE`, parallel determinism, spend.
6. Parallelism: whether to allow it at all; bounded pool; per-section budget; cancellation;
   claims must stay global.

## 4. Output format (designer)

Markdown, 8–12 KB, English, Java 21 signatures, these sections:

1. Scope and invariants.
2. API (names, signatures, ownership).
3. Sequence / state machine (text diagram).
4. Concurrency and lifecycle.
5. Failure and recovery.
6. Proofs (why no lost update / no stale form / no deadlock / no leak).
7. Counterexamples (at least 5).
8. Integration and open questions.

Rules:

- Do not edit any existing spec file.
- Do not rename existing public names; put proposed additions/changes in an **API delta** list.
- No history, no migration notes, no restating the brief.
- Every claim must survive a concrete scenario; "the executor handles it" is not a proof.

## 5. Critic brief (round 2)

Each critic reads **all four drafts** plus its assigned draft and writes
`docs/spec/_work/worker-async/critique-<topic>.md`:

- 6–10 strongest defects, ordered by severity.
- For each: scenario, violated invariant, why the draft's proof fails, minimal fix.
- Look for: races, lost wakeups, stale results forming after changes, claim leaks, callbacks
  after unload, executor starvation, server-thread capture spikes, unbounded memory,
  `PalettedContainer` mutation, section/orientation misalignment, `FIRST` vs parallel
  nondeterminism, stale `SectionResult` reuse.
- Cross-check MC facts and the Beecrasy/Multiblocked2 code paths; call out where the draft
  contradicts them.
- No praise, no implementation, no edits to the draft.

## 6. Integration (lead)

After the critique round, the lead:

1. Picks the surviving design per unit and resolves cross-unit conflicts.
2. Rewrites `11-engine.md` (execution model, budget, result/staleness).
3. Rewrites `16-adapter.md` (LevelCapture/StructureSnapshot, dirty mixins, executor, settle CAS).
4. Updates `02-architecture.md` (threading, source sets, lifecycle).
5. Updates `18-verification.md` (worker/dirty/section counterexamples).
6. Adds the decisions to `19-open-proposals.md` (or a new 20).
7. Re-checks Chinese `zh/` mirrors and code-block parity.
