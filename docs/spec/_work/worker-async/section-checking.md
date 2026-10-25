# W4 — Chunked checking and the section cache (draft)

## 1. Scope and invariants

Owns `ChunkGrid`, `ChunkKey`, `SectionKey`, `SectionStamp`, `SectionResult`, `StepResult`,
`BindingSite`, `ChunkContent`, `ChunkPlan`, `ChunkPlanner`, `SectionCache`. Not owned: capture (W2),
dirty marks (W3), executor and exceptions (W1), the verdict algebra (07), the fold (11), claims (08).

- **S-1** Steps are *partitioned* over the grid, never replicated: no double claim, no double charge.
- **S-2** The grid is world-framed (`siting.toWorld(cell)`); blueprint-local chunking is never used (X2).
- **S-3** An entry is a verdict, not a world: no content, `OccupantSnapshot`, `OccupantRef` or
  reservation, so a cache cannot pin a capture.
- **S-4** No kernel type names a Minecraft class and `16` is never a constant: cell size travels on
  `WorldView.chunkGrid()`; palette primitives are adapter obligations (§2).
- **S-5** No clock: `(cellReads, occupantReads, sections, steps)`, cancelled by a host watchdog.
  `FIRST` is sequential, parallel needs `AGGREGATE`, and a hit must equal a re-execution.

## 2. API

```java
public record ChunkKey(int cx, int cy, int cz) implements Comparable<ChunkKey> {}  // (cy,cz,cx) asc
public record ChunkGrid(int cellSize, LatticePoint origin) {     // one cube size, all three axes
    ChunkKey chunkOf(LatticePoint p);   // floorDiv(p.a - origin.a, cellSize), per axis
    Cuboid boundsOf(ChunkKey k);        // [origin + i*cellSize, …)
    int chunkCount(Cuboid box);         // distinct indexes per axis; 0 when empty
    List<ChunkKey> chunksOf(Cuboid box);// canonical order, box arithmetic only (I-3)
}
public record PlanId(long value) {}     // interned: Blueprint identity + Stage + plan digest
public record SectionKey(PlanId plan, Siting siting, ChunkGrid grid, ChunkKey chunk) {}
public record SectionStamp(long content, SnapshotId capturedAt) {}   // content = per-chunk epoch
public record StepResult(int stepIndex, Verdict verdict, List<Finding> findings,
                         List<BindingSite> sites, boolean rebindable) {}  // site = (key, cell)
public record SectionResult(SectionKey key, SectionStamp stamp, Map<Integer, StepResult> steps,
                            Census census, Spend spend) {}
public interface SectionCache {
    @Nullable SectionResult get(SectionKey k);  void put(SectionResult d);  // stamp-equal merges
    static SectionCache none();  static SectionCache bounded(int maxEntries);
}
```

`ChunkContent` (W2's per-chunk facade): `bounds()`, `status()`, `allAir()`, `maybeHasBlock`,
`countBlock` (`-1` = cannot answer), `occupant`, `readStatus`. `WorldView.chunkGrid()` is `@Nullable`,
default `null` = no chunking. `chunkOf` uses `Math.floorDiv`/`floorMod` on `p − origin`, never `/`, `%`
or `>>`: a negative `y` and a non-zero `origin` (`minBuildHeight = -64`) are ordinary inputs (X1).
`PlanId` derives from `Blueprint` *identity*, the stage, a plan digest and each step's `Expectation`
identity token (`meta().id()`, else object identity — a guessed digest for a lambda gives a false hit).
The snapshot also answers `knownEmpty`: a chunk omitted for `hasOnlyAir()` is not one never captured.

Palette facts — `count` counts palette values over all 4096 cells (whole chunk, block states only),
`maybeHas` is one-sided, `hasOnlyAir` is exact:

| Fact | May conclude | May **not** conclude |
| --- | --- | --- |
| `hasOnlyAir`, `knownEmpty` | `air()` MATCH on any box inside, zero reads | non-air expectations |
| `!maybeHas(s -> s.is(b))` | a requirement of `b` on ≥1 cell here is MISMATCH | anything outside the chunk |
| `count` | census lower bound; `0` refutes a demand | occupant types, **sub-box** counts |
| per cell | everything else | — |

A per-cell loop is still required for a strict sub-box count, for traits/NBT, for an entity footprint,
for an `UNREADABLE` cell (the finding names it), and when `countBlock` answers `-1`.

**No `invalidate`.** Staleness is a failed match, not an operation: no listener, no unload callback, no
unregister race. A `SectionResult` carries no chunk verdict, so `FIRST`, `AGGREGATE` and 13 §4.5's
insertion key stay exact. Cached findings hold `FindingId.NONE` and are re-minted at merge; a cached
MATCH keeps bindings as `BindingSite`s, never refs (`OccupantRef` embeds its `SnapshotId`, 06 §4.6).

## 3. Classification and algorithm

| Kind | Class | Why |
| --- | --- | --- |
| `BOUNDS` | global, 0 reads | one footprint-vs-`view.bounds()` compare |
| `CENSUS` | **additive** | per-chunk counts, one global compare |
| `ANCHOR` | global | orientation is one decision over all candidates |
| `BAY(b)` | local iff `grid.chunkCount(siting.toWorld(b.cells())) == 1`, else global | `Reply` is atomic (07 §4.5) |
| `SEAM` | global, additive unreadable tally | cells cross chunks; counterparts pair |
| `CLAIM`, `CUSTOM` | global | ledger atomicity (08 §4.3); no declared locality |

- **C-1 — locality is geometric only.** `Expectation` is opaque: 07 exposes no cellwise/atomic
  discriminator, so CELLWISE vs ATOMIC *is not observable*. A bay in one chunk is local whatever its
  expectation; a spanning bay is global whatever its expectation. Hence no per-cell cache, and a
  spanning bay has no cached verdict — the remedy is authoring (split the wall).
- **C-2 — atomicity is inherited, never split.** A door is one step: one cache line and one `Reply`; a
  boundary demotes it to global, never halves it.
- **C-3 — coverage.** `ChunkPlanner.plan(blueprint, siting, stage, view)` yields
  `local: Map<ChunkKey,int[]>`, `additive: int[]`, `global: int[]`, union exactly `plan.ordered()`,
  intersections empty. A pin violating `BOUNDS ≤ ANCHOR ≤ BAY` is already a DESIGN finding (09 §4.8);
  such a plan, and any `FIRST` plan, takes `ChunkPlan.sequential()`.

Sequential mode is the existing loop plus a lookup (`for i in plan order: hit → apply; else execute,
charge, put`): step-major order survives, so truncation, `Spend` and finding order match a run without a
cache. Parallel runs one chunk per task in `ChunkKey` order, joins, merges the census, then tallies —
reordering local steps ahead of global ones, which is why it needs `AGGREGATE` (11 §4.4 keeps the value
mode-invariant; only `Spend` and truncation differ).

**Budget.** `sections` is charged once per chunk per attempt. Sequential keeps one pool and
pre-authorises each read (11 §4.1). Parallel pre-splits by declared cost (chunk `k` gets `Σ cost` of its
local steps, no borrowing), admissible only when `Σ_k cost(k) + Σ global cost ≤ remaining`; otherwise
it declines rather than becoming schedule-dependent, so no chunk starves and total spend agrees.
Cancellation is polled between steps and at chunk start; a cancelled chunk stores nothing, and
`Undecided(CANCELLED)` outranks every other reason (11 §4.4). A non-readable chunk makes its local step
`UNREADABLE`, uncached; a throwing expectation contributes `UNREADABLE` plus one finding; a rejected
task runs inline (S-1); a missing cache, grid or `noop` index means **caching off, never trusted** (X3).

## 4. Cache and invalidation

`view.chunk(k)` must be non-null and `READABLE` (an absent chunk is `UNREADABLE`, never served), then:

```java
valid(r, now) ⇔ r != null && r.stamp().content() == now.content()   // 1 no observed change since
             && viewBounds.contains(grid.boundsOf(k))              // 2 chunk wholly inside view
```

Geometry, siting, plan and chunk are in the key, so 1 and 2 are the whole runtime check.

| Event | Effect |
| --- | --- |
| block, block-entity NBT, or entity change (W3) | miss for that chunk or footprint pair |
| new capture, same content | **hit** — why the key carries content, not `SnapshotId` |
| chunk unload, or a capture that no longer covers the chunk | miss, then `UNREADABLE` |
| siting change (origin or orientation) | miss; old entries stay valid for the old siting |
| plan/stage/blueprint edit, grid change (16 → 8) | miss (`PlanId`, `grid`) |
| ledger revision moves, a global step's outcome | **no** local effect |

A global step never invalidates a section result: a local step reads its chunk's content, the siting and
the plan, and no global step writes any of them. The one ordering dependency — `ANCHOR` *choosing* the
siting — resolves before classification, so no entry is computed at a siting that later moved.
`bounded(n)` is an LRU over small records; writes are put-if-absent and race-safe (two threads computing
one `SectionKey` from one snapshot run a pure function), and `put` merges only when stamps agree,
replacing otherwise, so an entry's steps always share the stamp it advertises.

## 5. Merge and determinism

Merging is a **gather by step index**, not a fold over chunks: each index occurs in exactly one
contribution (S-1), so assembling in `plan.ordered()` order reproduces the sequential list. Three things
fold: **census** (integer sums per type, then one compare against the declared demand — 11 §4.3 has none
yet, so it observes); the **tally** (`m/x/a/u/t`; 11 §4.4's table is `max` over two total orders, hence
commutative, associative, idempotent); **`Spend`** (a sum over a budget-fixed chunk set). Order-sensitive
items are assembled serially, never by arrival: **findings** by `(stepIndex, in-step insertion)` — legal
because 13 §4.5's fifth key is a documented order only while the step order is; **bindings** merged in
producing step order through `Bindings.merge` (07 §4.3), so a conflicting key lands in a fixed place.

**Claims.** The chunk phase produces verdicts only. One serial pass, after the fold and in plan order,
turns each `MATCH`ed bay into one `Reservation` over its whole `Cuboid` and, at `SETTLE`, one `settle`.
A chunk never reserves: a bay cannot be reserved twice, a spanning bay cannot be reserved half. It also
drops an order dependency — `claimsAt` seeing *another bay's* reservation made a verdict depend on
execution order — and no legal blueprint needs it: an undeclared overlap is a DESIGN error and a declared
one must be `SHARED`/`SHARED`, which never conflicts (09 §4.2, 08 §4.3).

## 6. Proofs

**P1 — deterministic merge.** Contributions are indexed by step, so the gather is a re-index and verdicts
equal the sequential ones; census and tally are commutative monoids, findings and bindings are ordered
after all tasks finish, `Spend` sums over a budget-fixed set. Each bay runs once (C-3, S-1), so a leak
shows as `Spend.steps > ordered().size()`.

**P2 — no stale `SectionResult`.** A step's result is pure in (cells read, geometry, expectation, siting,
plan): the key fixes the last four, the stamp fixes content. Epochs are monotonic and advance only on an
observed change (W3's premise), so `entry.content == current` means no change was observed between the
two captures: identical content, same result. The `READABLE` guard excludes load/unload (which W3 cannot
see), guard 2 a clipped chunk; a failed conjunct is a miss, never a repair.

**P3 — no double claim.** Reservations exist only in the serial pass, one per `MATCH`ed bay under
`ClaimKey(owner, siting, bay)`; the chunk phase creates none and by S-1 each bay occurs once there.
08 §4.1's skip rule would *hide* a duplicate, so the reservation count, not the ledger's final state, is
the test (X5).

**P4 — no missed spanning bay.** `chunkCount` counts distinct `floorDiv` indexes per axis between the
box's first and last cell, so it is 1 exactly when every cell shares one chunk; every bay above 1 lands
in `global`, and C-3 asserts the partition covers every step.

## 7. Counterexamples

| # | Case | Required | Breaks when |
| --- | --- | --- | --- |
| X1 | grid `(16,(0,-64,0))`, structure at `y ∈ [64,80)`; kernel uses `y >> 4` | `cy = 8` | `cy = 4` (section index) or `0` (raw shift): air where bricks are, a wrong structure `Formed` |
| X2 | same blueprint at origin `(0,0,0)` and `(3,0,0)`, a "chunk 0" cell changed between | second attempt misses | blueprint-local chunking reuses chunk 0 while half its world cells moved |
| X3 | chest inventory changes with no `setChanged` (modded BE, or `DirtyIndex.noop`) | cache disabled, step re-executes | epoch unchanged ⇒ hit ⇒ MATCH for an inventory that changed |
| X4 | plan `[A1(c0), B1(c1), A2(c0)]`, all local; `A2` and `B1` mismatch | `FIRST` reports `B1` (plan order) | chunk-major execution reports `A2`: same `Failed`, different repair target |
| X5 | one `EXCLUSIVE` bay, one attempt; count `settle` calls and reservations per `ClaimKey` | 1 and 1 | the bay is in both the local and global sets: two reservations, hidden by the skip rule |
| X6 | 1×2×1 door at `y ∈ {79,80}`, grid `(16,(0,-64,0))` | global (2 chunks) | `chunkCount` from blueprint coords says 1: cached in chunk 9; only chunk 8's dirty mark misses |
| X7 | two chunks, one same-code finding each, scheduler completing chunk 1 first | bundle byte-identical to the reverse | arrival-order appending makes 13 §4.5 key 5 the scheduler's; the cap truncates differently |

## 8. Integration and API delta

| # | Unit | Need |
| --- | --- | --- |
| D1 | W2 | `ChunkGrid`, `ChunkContent`, `knownEmpty`, and a per-chunk epoch **read on the server thread at copy time, atomically with `PalettedContainer.copy()`** — read-before-copy lets a write slip in and yields a false hit |
| D2 | W3 | `DirtyIndex.contentStamp(ChunkKey)`, monotonic per chunk, bumped by block, payload and entity changes; a flag so an absent index disables caching |
| D3 | W1 | every chunk task executes or runs inline; cancellation delivery; the parallel flag |
| D4 | 01/11/15 | `Budget`/`Spend` gain `sections` and `steps`; `TimeSource` leaves `MatchRequest` |
| D5 | 06/16 | `WorldView.chunkGrid()` default `null`; `LevelSnapshot` returns its captured grid |
| D6 | 13 | `Finding.withId(FindingId)` for re-minting cached findings |
| D7 | 18 | C-08 narrowed: a bay test no longer sees another bay's same-attempt reservation |
| D8 | 11 | `Census.plus(Census)`; `MatchRequest` gains `@Nullable SectionCache` and `parallelism` |
| D9 | 09 | none — classification is derived, so no `chunkLocal` flag on `CheckStep` |

**Open questions.** (1) Is the content epoch the right witness? It assumes W3 covers every write path; a
fallback sweep must invalidate. (2) Auto-split a spanning bay? It makes `air()` interiors cacheable but
changes `Reply` composition, which 07 refuses. (3) `sections` in `Budget`, or `steps` with a multiplier?
(4) One cache per world or per `ServerState`? World-framed entries make sharing sound but pointless.
(5) Parallel at all? Default sequential, and the numbers that would justify it are W1's to produce.
