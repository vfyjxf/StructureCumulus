# W2 — Snapshot: representation, freezing, versioning, handoff

> `dev.vfyjxf.cumulus.mc.capture`. Uses U2 (`WorldView`, `ReadStatus`, `SnapshotId`,
> `SnapshotPolicy`), U1 (`Cuboid`, `LatticeKey`), A1 (`LevelCapture`, `WorldOccupants`).

## 1. Scope and invariants

**`StructureSnapshot` implements `WorldView`** (frozen root capture); **`LevelSnapshot` implements
`NestedView`** (child frame over the same bytes). Both live outside `kernel.**`, in
`dev.vfyjxf.cumulus.mc.capture`; `LevelCapture.capture` hands one to U7 as `WorldView`, and no kernel
signature names `PalettedContainer`, `LevelChunk`, `getChunkNow` or `CompoundTag`.

| # | Invariant |
| --- | --- |
| S-1 | No `Level`, `LevelChunk`, `ChunkAccess`, `BlockEntity`, `Entity`, `SavedData` in the field graph. |
| S-2 | One revision; any status but `READABLE` is never air. |
| S-3 | The `PalettedContainer` is reachable only as `FrozenStates`: `copy()` once, never written, no `set`, `acquire`, `getAndSet`, `release`. |
| S-4 | `SnapshotId.revision` strictly increases per world over every capture and refresh. |
| S-5 | A section is read and copied in one server-thread slice; no cell comes from a slice that did not read it. |
| S-6 | Cells, sections, bytes, entities, payloads, store entries are checked before the allocation they gate. |
| S-7 | Every `WorldView` answer is pure over frozen bytes; caches change allocation, never value. |

## 2. API

Representation: a dense `SectionSlot[]` over the section box of `bounds()`, a `Map<LatticeKey,
CellPayload>` for BE NBT, an entity index in `OccupantRef` order, one id and version, immutable
findings. `SectionKey(sx, sy, sz)` is `x >> 4` per axis.

```java
final class FrozenStates {                    // the only state read path, no writer
    BlockState get(int lx, int ly, int lz);   // states.get, acquires inside the call
    boolean contentEquals(FrozenStates o);    // 4096 elementwise gets
    int nonAirCount();
}
final class SectionSnapshot {                 // immutable 16^3 copy
    SectionKey key();  FrozenStates states();  long captureRevision();  long[] payloadCells();
}
sealed interface SectionSlot permits Air, Present, Refused {          // hasOnlyAir -> Air
    record Air(SectionKey k) implements SectionSlot {}
    record Present(SectionSnapshot s) implements SectionSlot {}
    record Refused(SectionKey k, ReadStatus status, FindingCode code) implements SectionSlot {}
}
record CellPayload(LatticeKey cell, NamespacedId type, CompoundTag nbt) {}
record CaptureLimits(int maxCells, int maxSections, long maxBytes, int maxEntities, int maxPayloads) {}
record CaptureRequest(Cuboid bounds, SnapshotPolicy policy, Set<NamespacedId> trackedTypes,
                      Set<LatticeKey> trackedCells, CaptureLimits limits, @Nullable Heightmap.Types heightmap) {}
final class StructureSnapshot implements WorldView {    // + bounds, occupant, occupants, readStatus
    SnapshotId snapshotId();  long version();           // version == snapshotId().revision()
    SectionSlot slot(SectionKey k);  @Nullable CellPayload payload(LatticePoint c);
    List<CapturedEntity> entities(Cuboid box);          // OccupantRef order, no live Entity
    @Nullable FrozenStates heightmap(int wx, int wz);   // optional, cloned rows
    List<Finding> findings();  long bytes();
    StructureSnapshot refresh(Map<SectionKey, SectionSnapshot> replaced, long rev);
}
```

`child(frame, origin, parent, depth)` returns the `LevelSnapshot`; `SectionStore.intern` and `refresh`
are package-private.

## 3. Capture, sequence, state machine

```
capture(req)                                            // server thread, one slice
  reject : !server.isSameThread() | volume > maxCells | empty box       -> null
  rev    : counter.incrementAndGet()                    // reserved before the first read
  pass 1 : for k in grid.keysInOrder()                  // y, then z, then x
             chunk = cache.getChunkNow(k.sx(), k.sz())  // never Level.getChunk
             chunk == null       -> Refused(k, NOT_CAPTURED, SC-MC-0306)
             si = chunk.getSectionIndex(k.minY()); si outside -> Refused(k, OUT_OF_BOUNDS)
             sec.hasOnlyAir()    -> Air(k)
             !budget.admits(sec) -> Refused(k, NOT_CAPTURED, SC-MC-0501)
             else                -> Present(store.intern(k, sec.getStates().copy(), rev))
  pass 2 : for Present k: sort(chunk.getBlockEntitiesPos() ∩ k)
             hasBlockEntity() && tracked -> be.saveWithId(regs, new CompoundTag())
  pass 3 : getEntitiesOfClass(Entity.class, aabb(bounds()).inflate(MAX_ENTITY_SPAN), NOT_PLAYER)
             anchor inside -> admit (cap -> NOT_CAPTURED, SC-MC-0501)
             anchor outside, body inside -> shadowed cells NOT_CAPTURED, SC-MC-0504
  build  : clone heightmap rows; sort slots, payloads, entities; stamp SnapshotId(world, rev)
```

Capture is a function, not a tick-sliced pipeline; only `refresh` touches sections afterwards, whole
sections only.

```
 ABSENT ──capture──► FROZEN(id) ──refresh(S)──► FROZEN(id')    new object, old untouched
    └──null / OOM──► ABSENT, findings on CaptureFailure, never on a snapshot
server: publish(v) ─► dispatch: v = version, dirtyForCheck=false; checkInFlight false→true ─► submit
worker: reads view@v, posts (result, v);  server: accept iff version == v ─► retain, else discard
        both paths clear checkInFlight and redispatch when dirty
```

## 4. Concurrency and lifecycle

World reads (`getChunkNow`, `getSection`, `getBlockEntitiesPos`, `saveWithId`, the AABB query) and
`states.copy()`/`store.intern` run on the server thread only; unload is tick work, so no chunk vanishes
inside a slice. `occupant`, `readStatus`, `payload`, `entities` and the caches are read-only anywhere;
publish, refresh and accept have one writer per instance.

**Ownership.** The registry owns the current snapshot, an in-flight task a second reference, a retained
`Formed` its `WorldView` (U7). Nothing owns a section: no refcount, no release order, so a
`SectionSnapshot` dies with the last snapshot referencing it, and COW pins exactly the bytes a task reads.

**Sharing.** (a) `SnapshotRegistry` coalesces by `CaptureKey(bounds, policy, trackedDigest)` within one
tick: two blueprints on one box get one capture, two views. (b) A per-world `SectionStore` keyed
`SectionKey` whose `intern` returns the live entry when `contentEquals` holds, so a re-read that changed
nothing keeps object and `captureRevision`; values are weak, snapshots hold the strong references. It is
a cache, never an authority (§7.7), and one unchanged section keeps one revision — so W4's
`SectionResult` key `(SectionKey, captureRevision)` survives a no-op sweep and misses on a real change.

**Memory.** Section copy 2 KB typical (≤16 states), 32 KB at ≥257 states, 0 B for `Air`; payload one
detached `CompoundTag`; entity record plus optional SNBT; descriptor cache per distinct `BlockState`;
occupant cache lazy, ≤64 KB. Ceilings `maxSections × 32 KB`, `maxPayloads × 4 KB`,
`maxEntities × 2 KB`, retention `maxInFlight + maxInstances` snapshots.

## 5. Failure and recovery

| Cause | Effect | Recovery |
| --- | --- | --- |
| Off-thread capture | `null` | W1 resubmits on the server thread. |
| Chunk missing, not `FULL`, unloaded before pass 1 | `Refused(NOT_CAPTURED, SC-MC-0306)` | `UNREADABLE` ⇒ `Undecided`; a refresh may fill it. |
| Cap reached (`CAPTURE_LIMIT`), or a refresh meets a vanished chunk | `Refused(NOT_CAPTURED, SC-MC-0501)` | fixed order, so the same tail recurs; the version bumps. |
| BE removed between passes; entity anchored outside, body inside | state-only traits; `NOT_CAPTURED, SC-MC-0504` | absent trait ⇒ `UNREADABLE`; never air. |
| Worker throw, cancel, OOM; server stop | untouched; bytes pre-checked | W1 seals `Undecided`; a late result loses the version compare. |

## 6. Proofs

1. **No live access.** Every field is a `SnapshotId`, `Cuboid`, `LatticePoint`, `NamespacedId`,
   `Finding`, `SectionSnapshot`, `FrozenStates`, `CompoundTag` or primitive; only `copy()` builds a
   `FrozenStates`. Test: capture, unload the level, call every `WorldView` member; a live reference answers
   differently (`Level.getBlockEntity` off-thread gives `null`).
2. **Deep immutability.** `copy()` duplicates palette and storage, entries are interned `BlockState`s,
   `saveWithId` writes a fresh tag, lists are `copyOf`, `payloadCells` and the heightmap return clones.
3. **Version monotonicity.** One `AtomicLong` per `ServerLevel`, `incrementAndGet` before the first read,
   never decremented or persisted; `version` is final and equals the id revision, and capture and refresh
   are its only allocators.
4. **No stale `Formed`.** Dirty mark, refresh publication and capture each strictly increase
   `TrackedInstance.version` on the server thread; `accept` reads it once there before exposing anything.
5. **No torn section, no leak.** The 4096 cells come from one `copy()` inside a slice and world writes are
   server-thread, so nothing mutates that section during the call; a later change is a different revision
   and §6.4 discards the result. Weak store values cannot extend a lifetime, and no `acquire` handle,
   `ThreadLocal` or callback exists.

## 7. Counterexamples

1. **`Level.getChunk` in capture.** An unloaded interior column loads and creates a ticket, and capture
   reads a chunk the world was about to drop.
2. **`copy=false` kept for "same-tick" reads.** A piston fires during a worker check; the worker reads a
   section that existed at no revision and may return `Formed`.
3. **Version compared after expose.** The accept path stores `facts` first and checks `version` after; a
   change at t3 plus a result at t4 leave a formed structure on a wall that is gone.
4. **A missing section read as not captured.** Dropping `hasOnlyAir` sections while treating absence
   as `NOT_CAPTURED` makes every interior cell `UNREADABLE`; `Air` keeps "no data, `READABLE`, air".
5. **Local section indexing.** A blueprint at `x=100..107` spans world sections 6 and 7; a refresh keyed
   by blueprint-local index dirties local 0 twice and leaves half a door stale.
6. **Entity query clipped to the box.** A cow anchored one cell outside with a body covering two cells
   inside is dropped by `aabb(box)`; those cells answer air and a bay forms on empty floor.
7. **The store used as an authority.** `intern` returning by key without `contentEquals` hands a stale
   entry to the next capture: the version bumps, the bytes stay old, and the check certifies a moved wall.

## 8. Integration, API delta, open questions

Cross-unit: W1 takes the snapshot as task payload and `version()` as accept token, and owes
`maxInFlight`, the dispatch CAS and accept-before-expose. W3 calls `refresh(...)`, relies on
`contentEquals` reuse and the `Refused` slot, owns refresh timing and the instance machine. W4 keys
`SectionResult` by `(SectionKey, captureRevision)`; A1 supplies `captureSnapshot`; U7 supplies
`trackedTypes`.

**API delta.**

1. New A1 names, outside `kernel.**`: `StructureSnapshot` (`WorldView`), `LevelSnapshot` (`NestedView`;
   16 §3's class keeps its name, now the child frame), `SectionSnapshot`, `SectionSlot`, `SectionKey`,
   `FrozenStates`, `CellPayload`, `CapturedEntity`, `CaptureRequest`, `CaptureLimits`, `SectionStore`,
   `SnapshotRegistry`; glossary §N gains the rows.
2. `LevelCapture` gains `@Nullable StructureSnapshot captureSnapshot(CaptureRequest)`;
   `capture(WorldWindow, SnapshotPolicy)` stays and returns it as `WorldView`. `LevelSnapshot`'s per-cell
   `covered`/`readable` maps become the slot grid; `findings()`/`spend()` move to the root.
3. New codes `0502` byte budget, `0503` payload cap, `0504` entity shadow, `0505` section outside build
   height; `0306` and `0501` reused.
4. Proposed U7 delta: a retained-result record dropping `WorldView`
   (`InstanceRecord(InstanceRef, MatchFacts, ReadSet, SnapshotId, long version)`), so the registry does
   not pin one capture per formed structure.

| # | Question | Position |
| --- | --- | --- |
| Q1 | Does a retained `Formed` keep `WorldView`, hence the snapshot? | It does today (U7 §3); prefer delta 4. |
| Q2 | Does a no-op refresh bump the version? | No — `contentEquals` reuse keeps object and revision. |
| Q3 | Shadowed entity cell, and the store: adopted? | `NOT_CAPTURED`; the store is a weak, content-compared cache. |
