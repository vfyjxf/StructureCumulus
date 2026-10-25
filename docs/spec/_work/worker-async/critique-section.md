# Critique — W4 section checking and the section cache

Ranked most severe first. Each fix names its owner; none edits W4.

## C1 — "Local" is not a property of the bay's Cuboid (Critical)

**Scenario.** A `custom` bay inside one chunk reads `ctx.view().occupantAt(p)` one cell below its floor,
or `ctx.session().binding(K)` for an earlier bay's binding — both legal under 07 §3. The support cell is
dirtied, the bay's chunk is clean, epochs agree, the cached MATCH is applied to a bay whose support is
gone.

**Breaks S-5, P2, C-1** because nothing records which cells a step read: locality comes from the bay's
`Cuboid` alone. The draft uses `Expectation` opacity to deny a cellwise/atomic discriminator, then
assumes the closure property opacity also denies. 11 §4.6 lacks the same artefact.

**Fix.** A declared read envelope on `ExpectationMeta` (07 delta, absent ⇒ unbounded); local only if it
sits in one chunk *and* inside the bay union; store it per `StepResult` and in the stamp.

## C2 — Cache warmth changes the verdict through the budget (Critical)

**Scenario.** Ceiling 100 reads, plan declares 120: cold ⇒ `Undecided(BUDGET_EXHAUSTED)`, warm ⇒ every
local step hits at zero reads, globals fit ⇒ `Formed`. In parallel, "chunk `k` gets Σ cost of its local
steps" pre-splits by `CheckStep.cost` — a **floor** per 11 §4.1/P-4, "never charged", while reads "bill
as served" and `cost = 0` buys nothing: a chunk overspends, so P-4 breaks or the task truncates mid-chunk
and the value changes.

**Breaks 11 P-1, P-4, §4.7's determinism tuple, F-3** because P4 assumes the executed step set is fixed
by the plan, while a hit drops a step's reads and keeps its verdict in the fold: affordability becomes a
function of cache state. The parallel test equates a floor with a ceiling.

**Fix.** Charge a hit its declared cost, or admit hits only if the plan fits uncached; pre-split by an
upper bound (07 `ExpectationMeta.cost`) and decline to sequential when a step is anonymous.

## C3 — `BindingSite (key, cell)` cannot rebuild a `Bindings`; the finding re-mint is half-specified (Critical)

**Scenario.** Run 1 binds `HATCH → OccupantRef(ssn1, …)`; run 2 captures `ssn2` and the cached step
re-mints "by cell". The ref differs or is absent when an entity now shares the cell or the occupant's
footprint starts outside the bay; a re-executed bay binds the same fact, merge (07 §4.3) answers
`MISMATCH` + `SC-EXP-0003` on an unchanged world. A `BindingKey<T>` on a `NamespacedId` or `Integer` has
no cell and is dropped — a `Formed` with a missing witness.

**Breaks 07 §4.1, §4.3, S-5; 13 §4.5** — `FindingId.NONE` plus `withId` leaves `related` at pre-mint
ids, the ambiguity `SC-DIA-0003` names (13 OQ-7: no allocator).

**Fix.** Cache a value only when it is snapshot-free by construction (allowlisted types or a per-key
re-mint function), else mark the step uncacheable; re-mint ids and `related` in one pass at merge.

## C4 — `PlanId` is not a behaviour identity (Critical)

**Scenario.** Two blueprints, or one rebuilt on datapack reload, both use
`Expectations.custom(PANEL_META, lambda)`: `meta().id()` and the step digest are equal, so the second
attempt hits another lambda's verdicts. Bare lambdas are worse — `ExpectationMeta.anonymous()` is one
constant id for every anonymous expectation. The reverse also fails: `Blueprint` defines value equality
(09 §4.6), so "identity" is ambiguous, and an interned identity-plus-digest table leaks on reload.

**Breaks S-5 and 09 §4.8** because `Expectation` is opaque: the kernel cannot see that two expectations
sharing one `meta().id()` differ, so no digest of ids is a behaviour key. `SC-EXP-0014` concedes that.

**Fix.** One interned `long` allocated at `Blueprint` build, used alone with the stage; rebuild ⇒ cold,
and a moved instance keeps its stamp so the siting stays the only mover.

## C5 — The stamp has no producer, the key name is taken, `valid()` reads live host state (High)

**Scenarios.** (a) `valid(r, now)` reads `now.content()`, never defined: from the live `DirtyIndex` it
puts a worker on W3's confined index (D-1) and violates W-2; from the snapshot it is degenerate, so hits
occur only within one capture. (b) `capturedAt` is a `SnapshotId`, minted per capture, so "merge when
stamps agree" is unreachable. (c) `SectionKey` is frozen (brief §1) and W3 defines it as the world
section; W4 redefines it. Four keys exist: W2 `(SectionKey, captureRevision)`, W3
`(SectionKey, SnapshotId)`, W1 `(SectionKey, snapshotVersion)`, W4 a per-chunk epoch.

**Breaks S-4/W-2 purity, the no-rename rule, the single-key requirement.**

**Fix.** Name it `CacheKey`; the stamp is the snapshot's per-section epoch (W2 keeps a section's
revision while `contentEquals` holds — that *is* a content epoch); compare against
`view.chunk(k).epoch()`; delete `capturedAt`; the lead freezes one key, deletes the other three.

## C6 — Census refutation on a partially captured world licences a repair (High)

**Scenario.** A `CAPTURE_LIMIT` or `NOT_CAPTURED` tail leaves 3 of 40 chunks readable: demand 100,
observed 9 ⇒ `Failed` ⇒ 11 §4.3 licenses filling a correct interior; a missing chunk is not the
`countBlock == -1` case. Also `hasOnlyAir`/`knownEmpty` are stated per *chunk* while W2 omits *sections*,
`knownEmpty` has no producer in W2's API, and `chunkCount(siting.toWorld(cells))` has no `null` branch
although `toWorld` is `@Nullable` (05 §4.6) — an edge bay loses its step, so C-3's "union exactly
`plan.ordered()`" is false. X6's door is right; demoting it costs both chunks their entries.

**Breaks 11 §4.3, F-2, W2 S-2** — a `Failed` that negates no declaration.

**Fix.** Census refutation needs every chunk of the envelope `READABLE`, else `untested` plus a finding;
`null` `toWorld` becomes `UNREADABLE`; state the half-open/empty cases of `chunksOf`/`chunkCount`, and
that `cellSize ≠ 16` leaves `hasOnlyAir`/`count` superset-exact only.

## C7 — The claims section deletes a frozen 07 API to make its own proof hold (High)

**Scenario.** 11 §4.5 makes reservations session-local, so `ProbeSession.claimsAt` reads the
`ClaimLedger` (08: one-cell `claimsIn`) — settled claims, never "another bay's reservation". The hazard
cited for removing it does not exist in 11/08 as written. The removal buys a cache without a ledger
input and costs every expectation the ability to express a ledger read, while 08 §4.3 defers
`SHARED`/`RESERVED` pairs to `SETTLE`; addons built against 07 §3 stop compiling.

**Breaks W4's scope** (`ProbeSession` is 07's), the no-delete rule, and C-2's non-sequitur: the ledger
still enters through `CLAIM` and `SETTLE`.

**Fix.** Keep `claimsAt`. If ledger dependence must be excluded, make it a 07 restriction with a DESIGN
finding and keep the serial pass — sound either way, since reservations are session-local.

## C8 — Nothing owns the cache, nothing bounds it, `put` can replace fresh with stale (High)

**Scenarios.** Per-`ServerState` cache, no world in the key: two levels hold the same blueprint at the
same siting, W3's `captureEpoch` is per level, both start at 0, the second hits the first's entries. No
unit clears it on unload (W1 clears slots, W3 the store). A machine moved one block per attempt mints a
`Siting` per attempt, so families accumulate until LRU eviction — and `bounded(n)` counts entries while
an entry carries findings, labels and sites: not a byte ceiling. "Last writer wins when stamps differ"
can install an older stamp over a newer one.

**Breaks W2 S-6 / W3 D-1's memory premise**; S-3 bounds the entry, not its lifetime.

**Fix.** One cache per `ServerLevel`, owned with the store, `world` in the key, cleared on unload; bound
by bytes; `put` keeps the greater stamp and treats a step map missing any index as a miss.

## Rulings for the lead

1. **Scope.** Cache only behind a declared read envelope; `CUSTOM`, anonymous and binding-reading steps
   are uncacheable. If 07 will not add `ExpectationMeta.reads`, withdraw W4 to "chunked execution, no
   cache" — its remaining value is parallelism, which W1 has not priced.
2. **One stamp, one key.** The content epoch lives on the snapshot; the key is `(world, blueprint stamp,
   stage, Siting, ChunkGrid, ChunkKey, epoch)`; W1/W2/W3 delete their competing keys; `SectionKey` stays
   W3's. No worker reads `DirtyIndex`.
3. **Spend invariance, claims API.** A hit is charged its declared cost; no cached step may observe
   `remaining()` or a later binding; keep `ProbeSession.claimsAt`. If the integration cannot promise
   "same `Spend` warm or cold", the cache goes, not 11's determinism claim.
