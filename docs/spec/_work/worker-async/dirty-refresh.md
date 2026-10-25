# W3 — Dirty marking, refresh scheduling, coalescing

> Host unit `dev.vfyjxf.cumulus.mc.watch`. Kernel sees `ChangeSet` in, `ReadSet` out; no MC type in a
> kernel signature, section keys are host-side. Reused: `DirtyIndex`, `SectionKey`, `SectionResult`,
> `SnapshotId`, `ChangeSet`, `InvalidationIndex`, `InstanceRef`.

## 1. Scope and invariants

**Owns** mutation observation (mixins + events), position→instance index, section dirtiness, the
tick-end refresh pump, `ChangeSet` production. **Not** capture content (W2), pool/lifecycle (W1),
section checking (W4), `STALE` (`11` §4.6).

| # | Invariant |
|---|---|
| D-1 | `DirtyIndex`, `SectionStore`, pump: **server-thread confined**, no lock; a worker sees only sealed `SectionSnapshot`s. A mark follows its mutation, on the same thread, before the tick ends. |
| D-2 | Granularity **section ∪ payload position ∪ entity footprint** — block states per 16³ section, NBT-sensitive BEs per `BlockPos`, entities per footprint. |
| D-3 | A watch with any stale section is **never checked**, refresh never starts a check, and a result publishes only if the world provably did not change under it (P-3). |
| D-4 | An unloaded, non-ticking or denied chunk is `UNKNOWN` — never `BROKEN`, never air (`06` §4.1). |
| D-5 | Dispatch never does unbounded work; refresh is budgeted per tick and never starts a check. |
| D-6 | `ChunkHolder.blockChanged` is not a dirty signal: network bookkeeping, inert for untracked chunks. |

## 2. API

```java
package dev.vfyjxf.cumulus.mc.watch;
record SectionKey(int x, int y, int z) { static SectionKey of(LatticePoint p); }   // host-side
enum WatchState { CLEAN, DIRTY, REFRESHING, CHECKING, BROKEN, UNKNOWN }

final class DirtyIndex {           // mark* are O(1); watch fan-in never enters a hook
    void watch(WatchedInstance w);  void unwatch(InstanceRef id);  Set<InstanceRef> instancesIn(SectionKey k);
    void markSection(SectionKey k, DirtyCause c);  void markPayload(long pos, DirtyCause c);  void markChunk(int cx, int cz, DirtyCause c);
    void markEntityFootprint(Cuboid was, Cuboid now);  void markCells(CuboidSet cells, NamespacedId type);
}
final class WatchedInstance {      // stale(i) = store.rev(k) != revOf(i) ← the authoritative predicate
    SnapshotId snapshot();  WatchState state();  long version();  boolean dirtyForCheck();  boolean checkInFlight();
    long[] sections();  long revOf(int i);  boolean stale(int i);
}
// SectionStore (W2): content(k) · rev(k) · invalidate(k) · install(k, s) · pin/unpin(k) · evictUnpinned(n)
// ChangeLog.intersects(sections, sinceEpoch) → FRESH | CHANGED | UNKNOWN; DirtyPump.tick(level, budget)
```

| Mutation | Hook | Dirty |
|---|---|---|
| block at `p` | `LevelChunk.setBlockState`, all overloads | section `of(p)`; payload `p` if `hasBlockEntity()` flips |
| BE body at `p` | `BlockEntity.setChanged` | payload `p` **iff** `p ∈ payloadIndex` — traits, no section copy |
| entity join/leave/move | `EntityJoinLevelEvent` / `EntityLeaveLevelEvent` / `Entity.setPos` mixin | footprint sections; a move marks old ∪ new |
| chunk load / unload | `ChunkEvent.Load` / `ChunkEvent.Unload` | its watched sections / sections → `UNKNOWN` |
| claim settle/release | `SavedClaims.settle` | `cells ∩ window`, `changedTypes += SC:claim` |
| datapack / config | `DatapackBlueprints.apply`, config event | whole instance, unlocalised |

Three maps, all `long → set`: `sectionIndex` (16³ key → watches), `payloadIndex` (`BlockPos` →
watches, populated only where capture asked for a BE trait), `entityIndex` (section key → watched
entity footprints). Each mark is one hash insert; `unwatch` walks the watch's own lists. A barrel's
`setChanged` therefore costs one lookup when no watch reads BE NBT.

## 3. State machine

```
  mark*(window)                                            result, P-3 holds
 ┌──────────────┐                                                 │
 ▼              │                                                 ▼
DIRTY ◄────── CLEAN ──ensureClean(Ready)──► CHECKING ───────► CLEAN  (dirtyForCheck=0)
  │  ▲          ▲                          │ result, P-3 fails
  │  │          └── refresh, equal content ─┼──► DIRTY (discard)
  │  │  mark while CHECKING (dirtyForCheck=1)│
  │  └───────────────────────────────────────┘
  ▼ budget
REFRESHING ── budget spent ──► REFRESHING (next tick)   ├─ all copied, differs ─► CLEAN, version+1
                                                        └─ chunk gone ────────► UNKNOWN ──Load──► DIRTY
any ── unwatch ──► gone     any ── no blueprint / digest clash ──► BROKEN ──rebind──► DIRTY
```

A no-op refresh neither clears `dirtyForCheck` nor bumps `version` (CE-1); `BROKEN` means "cannot
build a request" (no blueprint, `Siting`/snapshot disagreement), never a cap hit.

## 4. Refresh scheduling

One call per level at server-tick end — one shot, never a per-check tick walk, so there is no
check-tick state machine. Order `(checkInFlight ? 0 : 1, dirtyAge desc, instance)`; a granted watch
copies **only its stale sections**, unpins each after the swap, and stops at a section, payload or
`System.nanoTime` deadline (adapter clock; the kernel owns none); leftover budget funds `agedSweep`,
`store.evictUnpinned` and `log.age()`. Unvisited sections stay stale and the watch stays `REFRESHING`,
so a huge structure costs latency, never a spike. `ensureClean` is the only synchronous refresh,
capped at 4 sections / 32 KiB (larger ⇒ `Deferred`), and it reads **store revisions**, not `state()`:
a mark from earlier in the tick has not flipped `state()` yet (CE-3).

**Priority.** A watch whose `checkInFlight` waits on its own refresh goes first, then oldest dirty age
past 400 ticks; an unloaded-chunk watch becomes `UNKNOWN` and stops consuming. `churnScore` above 60
marks per 100 ticks throttles **dispatch** to one per 20 — delaying a `Formed`, never falsifying one.

### 4.1 Store-based invalidation — adopted, with one condition

A section is world state; nothing about it is per-instance, so marking invalidates the store entry.
Per-snapshot bits cost O(fan-in) — 12 watches copy one shared section 12× — while the store is O(1),
copies once per `(key, rev)`, and compares `rev(k) != snap.rev(k)`: computed, so it cannot drift, and
right even for a watch registered after the fact.

**Condition: the store is a construction cache, never a resolver.** A sealed snapshot holds direct
`SectionSnapshot` references plus pinned revisions (CE-7); lazy `snapshot.section(k)` →
`store.content(k)` would blend revisions once one section advances — a self-inconsistent `SnapshotId`
the kernel cannot detect (`06` §4.7). `invalidate` drops `content`, keeps `pins`, marks the key dirty;
`install` stamps `rev = ++captureEpoch` (global, monotonic across evict/recreate, so no rev reuse). A
pinned object survives invalidation, evictable on `unpin`; dirty metadata outlives content eviction
(`{key, rev, null, pins}`, 32 bytes), so a capacity squeeze loses a copy, never an update.

## 5. Failure and recovery

| Gap | Cover |
|---|---|
| pistons, explosions, fluid, `/setblock`, `placeInWorld`, `WorldEdits` — all reach `LevelChunk.setBlockState` | primary hook |
| worldgen before the chunk is observable (`getChunkNow` null) | `ChunkEvent.Load` |
| direct `section.getStates().set(...)`, `loadWithComponents`, `setChanged` without `super`, an already-loaded chunk at `watch()` | aged sweep; `watch()` captures |
| a chunk replaced (unload + load) | unload ⇒ `UNKNOWN`, load ⇒ `DIRTY` |

The sweep is **revalidation, not fingerprinting**: 1.21.1 has no cheap section hash and sampling
cannot prove absence, so it re-captures the least recently refreshed watched section, round-robin, at
600 ticks; a snapshot older than 1200 ticks is recaptured before dispatch.

**Failures.** `getChunkNow` null mid-refresh ⇒ `UNKNOWN`, snapshot dropped, store rev left stale. A
throwing copy leaves the tick in `REFRESHING` (3× ⇒ `BROKEN` + one `SC-MC` finding); log overflow and
an orphaned result both discard, releasing pins. On server stop the pump halts, in-flight cancels, and
index and store are cleared.

## 6. Proofs

**P-1 No lost update.** A mark is written at the mutation point on the mutating thread before the tick
ends (D-1) and survives content eviction. The refresh reads the world on that same thread and installs
the copy, with no mutation in between, so a mutation is either inside the installed `rev` or its key
is still dirty — and dirt is a revision inequality nothing overwrites.

**P-2 No duplicate concurrent check.** `checkInFlight` is set at submit and cleared last in `finally`;
submit while set only raises `dirtyForCheck`, and neither `ensureClean` nor the pump starts checks
(D-3). **No starvation:** each pump call copies a stale section or moves the watch to
`UNKNOWN`/`BROKEN`, removing it from the candidate set, so every call makes progress.

**P-3 No stale `Formed`.** Four terms: (1) the result's `SnapshotId` equals the watch's current one;
(2) `version == baseVersion`; (3) `log.intersects(sections, baseEpoch) == FRESH` — **no mark at all**
touched the window since submit, which versions cannot supply, because an unrefreshed mark leaves
`version` untouched while the snapshot is already stale; (4) digest and `Siting` unchanged. Failure
discards and sets `DIRTY`; the log over-approximates (overflow ⇒ `UNKNOWN` ⇒ discard), so no race
resolves toward publishing.

**P-4 No leak.** Each pin is taken at seal by the owning watch and released exactly once, on the server
thread, when that snapshot is superseded, discarded or unwatched; `unwatch` removes exactly the index
entries it created, and a later completion hits the null guard.

## 7. Counterexamples

| # | Scenario | Wrong design | Required |
|---|---|---|---|
| CE-1 | piston fires and retracts in one tick | version bump ⇒ a check on an identical snapshot ⇒ a new generation for nothing | one refresh, `equalContent`, version kept, **zero** checks |
| CE-2 | chest changes while `CHECKING`, pump not yet run | `version == baseVersion` passes ⇒ `MATCH` on an absent inventory | log marks the payload ⇒ `CHANGED` ⇒ discard |
| CE-3 | `/cumulus build` edits 40 cells, re-checks same tick | `state()` still `CLEAN` ⇒ the check reads the pre-edit snapshot; `SETTLE` commits claims for an absent structure | read store revisions; staleness defers or refreshes inline |
| CE-4 | two watches share a section; one is unwatched | a single-owner map drops the other, or clears a shared bit | `section→set`; pins released per watch |
| CE-5 | chunk unloads during `REFRESHING` | "skip missing chunks" keeps the old copy, blending a replaced world | `UNKNOWN`, snapshot dropped, rev stale, load ⇒ `DIRTY` |
| CE-6 | watch over an unloaded chunk | null chunk as empty ⇒ all `MISSING` ⇒ `Failed` + a repair that would bulldoze terrain | `UNKNOWN`, no request; `Undecided` at worst |
| CE-7 | store advances one section while a snapshot is out (lazy resolve) | the match reads rev 3 and 4 together — an undetectable, self-inconsistent `SnapshotId` | snapshot pins references at seal; the store never resolves |

## 8. Integration, API delta, open questions

Kernel boundary unchanged: `ChangeSet(changed, changedTypes, revision)` in, `ReadSet` out,
`InvalidationIndex.affectedBy` the host SPI. A dirty section becomes `changed = sections ∩
readSet().cells()`; above 4096 cells, or for `CATALOG` / an overflowed log, it emits **unlocalised**
— `changed` empty, `changedTypes` set — which `11` §4.6 maps to "re-check everything".

**API delta.** (1) `01` §I: `DirtyIndex`, `SectionKey`, `SectionStore`, `WatchState`, `ChangeLog`,
`DirtyCause` as **host-side** rows. (2) `03-naming.md`: `changedTypes` ids
`SC:block|payload|entity|claim|blueprint|config`, leaving `ChangeSet`'s shape unchanged. (3)
`InvalidationIndex`: `record`, `unrecord`, `affectedBy → null` when unlocalised. (4) `16` §3: the five
classes, one store and index per `ServerLevel`. (5) `18`: CE-1..CE-7 as GameTests.

**Cross-unit needs.** W2: `SectionSnapshot` keyed by `SectionKey`; `equalContent`; pin-at-seal and
unpin; a refresh must not touch a container a worker holds. W1: submit carries `SnapshotId`,
`version`, `ChangeLog.epoch()` and completion returns them, releasing pins on the server thread;
discard is `StaleDiscarded`; shutdown order pump → cancel → `clear()`. W4: `SectionResult` keyed
`(SectionKey, SnapshotId)`.

**Open questions.** (1) `BlockEntity.setChanged` vs `Level.blockEntityChanged` — which do all BE paths
reach in 1.21.1 (verify via SourceScout)? (2) `setPos` mixin, or is the sweep enough for entities? (3)
Must a `BROKEN` watch stay in `InvalidationIndex`? (4) Does P-2's neighbour-edge graph join `markCells`?
