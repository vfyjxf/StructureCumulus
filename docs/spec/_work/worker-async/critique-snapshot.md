# Critique — W2 `snapshot-handoff.md`

Read with W1/W3/W4 and `06`, `11`, `16`; MC facts: NeoForge 21.1.219 via SourceScout.

## D-1 (Critical) The snapshot is not a `WorldView`

**Scenario.** A blueprint requiring `ITEM_INVENTORY` on a chest.
**Violated.** 06 §3.3 (`requiredTraits` answered), §4.2, §4.4, amendment A2 (payload removed).
**Failure.** §6.1 lists no `OccupantRef`, `OccupantDescriptor` or `Trait`, and no resolver runs
(16 §4.1 step 5): the passes copy states, NBT and `CapturedEntity` only. `payload(cell)` is not a
`WorldView` member, so the NBT is unreachable; the only host-body channel is a trait, and none is
captured. `CellPayload` re-adds the payload A2 deleted.
**Fix.** Run resolvers and fill `requiredTraits` (absent ⇒ `NOT_CAPTURED`), or drop `implements
WorldView` and declare every trait `UNREADABLE`; delete `CellPayload`.

## D-2 (Critical) Entity footprints never cover cells

**Scenario.** A cow in a bay declared empty; a cap filled by item entities.
**Violated.** 06 §3.2 (`occupant != null ⇔ READABLE`), §4.3 (a covered cell is `READABLE` even if
its own read was denied); 16 §9.1.
**Failure.** `entities(Cuboid)` is a list, so `occupant(cell)` inside the cow resolves from the air
section and returns the air sentinel while `occupants(box)` returns the cow, and `air()` over that
bay certifies an occupied cell. Admission is anchor-only and cap order is `OccupantRef`
(y,z,x,kind,type), not priority, so item entities at lower y evict the tracked golem and its cells
still read air.
**Fix.** Cell→occupant coverage before sealing (footprint beats `Refused`/`Air`), tracked type
first in cap order, a dropped entity marks its cells `NOT_CAPTURED`.

## D-3 (Critical) `Air`/`Refused` cannot express 06's status model

**Scenario.** Bounds 7×6×7 inside a `hasOnlyAir()` section; a cap that stops mid pass 1.
**Violated.** S-2, S-6; 06 §4.1 per-cell precedence and `DENIED`/`STALE`, §4.6; W3
`stale(i) = store.rev(k) != revOf(i)`.
**Failure.** `Air(SectionKey)` has no `captureRevision`, costs 0 B, is never interned: no `rev` for
W3's predicate, no bytes for `contentEquals`, so a section whose count is stale yields
unfalsifiable air. `Refused` collapses 4096 cells to one status: `DENIED` has no producer (a missing
chunk becomes `NOT_CAPTURED`, where 16 §4.2 says `DENIED`), and `OUT_OF_BOUNDS` is used for a
container index although 06 defines it against `bounds()`.
**Fix.** A revision and store entry per slot; `readStatus(cell)` = bounds→height→slot; produce
`DENIED`; separate cap from vanished-chunk codes.

## D-4 (Critical) Two revision witnesses; S-4 contradicts Q2

**Scenario.** A piston fires and retracts (W3 CE-1: no bump), then an inventory changes; a `Formed`
retained across a no-op refresh.
**Violated.** S-4; 06 §3.3, §4.6 ("strictly greater each time"), §4.7 (foreign ref detectable); W1
W-4; W4 P2.
**Failure.** S-4 says revision strictly increases over every capture *and refresh*; Q2 keeps it on a
no-op, and the draft does not choose. If reused, two snapshots with different payloads share a
`SnapshotId`: `OccupantRef.from` is true for a pre-refresh ref, 06 §4.7's only stale-ref detector is
gone, and `at(formed)` reuses a view by id. If always bumped, W3 CE-1 is false. W2 also carries two
stamps and W3/W4 a third (content epoch); W2 says W4 keys on `captureRevision`, W4 §4 on the epoch.
**Fix.** `SnapshotId` is capture identity, always strictly increasing; content identity is a separate
stamp carried by W4 and `OccupantRef`.

## D-5 (Critical) "One slice, one stamp" is circular; `saveWithId` does not compile

**Scenario.** A modded BE whose `saveAdditional` writes a block; capture from a `ChunkEvent.Load`
handler; pass 2 compiling.
**Violated.** S-5, S-6, §6.5 ("world writes are server-thread, so nothing mutates that section").
**Failure.** The argument is about threads, not time: pass 2 and pass 3 run arbitrary mod code on
that thread (`saveAdditional` is overridable), so a write lands between the pass-1 copy and the
pass-3 read with no interleaving; S-5 holds while the snapshot mixes two worlds. §4's "unload is
tick work" guards the wrong risk: `getChunkNow` hands out `currentlyLoading`
(`ServerChunkCache:191`), a chunk returned before `runPostLoad`, BE registration and
`ChunkEvent.Load` (`ChunkStatusTasks:205-217`). Stamping first
makes it worse: the revision a result is accepted under is allocated before any byte is read — the
reverse of W4 D1's epoch-with-copy. And `saveWithId(regs, new CompoundTag())` does not exist:
`saveWithId` is `final`, one `HolderLookup.Provider` argument (`BlockEntity.java:106`); the two-arg
form is pre-1.20.5.
**Fix.** One pass per section (payload, then states), stamp last, verify a per-chunk epoch across the
slice or refuse; `saveCustomOnly`, no redundant `id`.

## D-6 (Major) Memory: reuse is GC-dependent and the caps bound nothing

**Scenario.** A GC between refreshes; `evictUnpinned` under pressure; a 1M-cell box; a chest with
1 MB of `NeoForgeData`; 200 retained `Formed`.
**Violated.** S-3, S-6, S-7; W3 P-1/P-4; W1 §8 (pins, `estimatedBytes`); brief §0.4
(`CAPTURE_LIMIT`).
**Failure.** `FrozenStates.get` claims to "acquire inside the call";
`PalettedContainer.get(x,y,z)` does not (`:157-166`). The copy is sound (`:290/346`), so the claim
hides the exposure: weak values make `intern`'s reuse, hence `captureRevision` stability, depend on
whether a GC ran, and W3 derives staleness from `store.rev(k)`, so a collected entry marks every
watched section stale and buys a re-copy and re-check with no world change. `intern` copies before
comparing, so S-6's "checked before the allocation" is false; `{key, rev, null, pins}` never evicts;
no `pin`/`unpin`/`release` exists here, yet W1 accounts pinned bytes and W3 calls `evictUnpinned`.
Caps bound none of it: over-volume returns `null` with no status: `CAPTURE_LIMIT` has no
producer; `maxBytes` cannot be checked before the tag exists and the payload/entity ceilings are
counts; `bytes()` is post-hoc, and capture on the server thread means §5's OOM row covers the wrong
thread; retention is counted, while Q1 leaves a retained `Formed` holding a `WorldView` (11 §3) and
only proposes a delta (11 §5).
**Fix.** W2 owns the pin protocol; a strong bounded store (LRU or content-hash keys); compare
live-vs-frozen and copy only on difference; `CAPTURE_LIMIT` as a named outcome; measure tags and
palette bits before copying; a server-thread OOM policy; byte-bounded retention.

## D-7 (Major) The API delta cannot be integrated

**Scenario.** A1's `capture` returns `LevelSnapshot` (16 §3); W4 asks `view.chunk(k)`/`knownEmpty`;
W1 gates on `estimatedBytes()`; three units define `SectionKey`.
**Violated.** Brief §4 (no renaming frozen names), 16 §3/§4.2, 06 §4.5, W4 D1, W1 §8.
**Failure.** 16 §3 makes `LevelSnapshot` the root `WorldView`; W2 makes it the *child* (`implements
NestedView`) and moves `spend()`/`findings()` to a new root `StructureSnapshot` — re-parenting a
frozen name. `SectionKey` then has three identities: W2 `(sx,sy,sz)`, W3 host `(x,y,z)`, W4
`(PlanId, Siting, ChunkGrid, ChunkKey)`; `slot(SectionKey)` and W4's cache key can never share a
map. W4's façade (`ChunkGrid`, `chunk(k)`, `knownEmpty`, epoch) has no W2 member; W1 needs a
pre-capture `estimatedBytes()` and `release()`.
**Fix.** `LevelSnapshot` stays 16's root, child `LevelSnapshot.Nested`; add the chunk-grid façade;
one `SectionKey`.

## Rulings for the lead

1. **Identity.** `SnapshotId` = capture identity, strictly increasing, never reused (retire Q2);
   content identity becomes a separate section/chunk stamp carried by W4 and `OccupantRef`.
2. **One root, one key.** `LevelSnapshot` stays 16 §3's root, child `LevelSnapshot.Nested`, one
   world `SectionKey`, W4's plan-scoped key renamed; W2 supplies `chunkGrid`/`chunk`/`knownEmpty` and
   pin/release, or W4 is not built on W2.
3. **Occupants and caps.** W2 runs resolvers and fills `requiredTraits` (or is declared states-only,
   every trait `UNREADABLE`); `CellPayload` goes; entities get cell coverage and tracked-first cap
   order; `CAPTURE_LIMIT` and byte ceilings become measured outcomes.
