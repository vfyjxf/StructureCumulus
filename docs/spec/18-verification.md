# 18 — Verification plan

> This is the falsification plan for the design, not a claim that tests exist. It names the
> gates, the counterexamples each unit must survive, and the evidence a reviewer accepts.
> The counterexample catalogue deliberately inherits the failures the ArchitectCumulus
> prototype exposed (`docs/spec/_work/PROTOTYPE-FINDINGS.md`, P-01…P-70); a new design that
> repeats one of them is rejected even if its prose is clean.

## 1. Static gates

| Gate | What it proves | Failure means |
| --- | --- | --- |
| `compileKernelJava` | no Minecraft/NeoForge type is reachable from `src/kernel/java` | a kernel type leaked a loader dependency (I-1) |
| `kernelPurity` | no `net.minecraft`, `net.neoforged`, `com.mojang` import or fully-qualified name in a kernel source file | a string-level leak the compiler did not see |
| `kernelTest` | the kernel runs on a plain JVM with JUnit only | the kernel needs a game bootstrap |
| NullAway (`OnlyNullMarked`) | every `@NullMarked` kernel package is null-safe | a public signature hides an absence |
| `spotlessCheck` | formatting and import order match the project profile | style drift |
| `specCheck` (this round) | every cross-unit name in a module is in `01-glossary.md`; no module imports a package below it in `02-architecture.md` §2 | vocabulary or dependency drift |

The last gate is a small Python script over `docs/spec`: extract backticked identifiers
from each module's vocabulary table, compare with the glossary, and parse the `May depend
on` line if present. It is allowed to be approximate; its job is to catch a synonym, not to
be a compiler.

## 2. Test layers

| Layer | What it tests | Where |
| --- | --- | --- |
| **Value algebra** | `LatticePoint`/`LatticeKey` round-trip, `Cuboid`/`CuboidSet` laws, orientation group axioms | `src/kernelTest/java/.../space` |
| **Combinator laws** | `Expectations` fold precedence, binding merge, cost aggregation, door atomicity | `.../expectation` |
| **Claims** | conflict matrix, reservation lifecycle, settlement idempotence | `.../claim` |
| **Engine scenarios** | probe-fail, form, undecided, stale re-check, budget exhaustion, cancellation | `.../engine` |
| **Context** | derivation order, cycle rejection, interior volume, augment folds | `.../context` |
| **Findings** | total order, dedup, caps, renderer golden text, explain lookup | `.../finding` |
| **Remedies** | dry-run, plan determinism, rollback, preview/repair consistency | `.../remedy` |
| **Adapter** | capture, orientation round-trip, reload, claim persistence, edit rollback, commands | `src/test/java` + GameTest |
| **Golden documents** | one valid document per schema feature; canonical round-trip | `src/kernelTest/resources` |

Property tests are preferred for the algebra (a small generator over signed coordinates and
the 48 orientations). Scenario tests are preferred for the engine: a fake `WorldView` built
from a string map, a fake `ClaimLedger`, a fake `DirtyIndex`/`SectionStore`, so a test can assert the exact
`Spend` and the exact finding order.

## 3. Counterexample catalogue

Each row is a test that must exist. "Prior" is the ArchitectCumulus finding it prevents.

| # | Prior | Unit | Test | Expected |
| --- | --- | --- | --- | --- |
| C-01 | P-01 | U1 | Two different box decompositions of the same cells produce equal `CuboidSet`s; `volume()` is the cell count; `boxes()` is globally ordered. | equal, exact volume, one canonical order |
| C-02 | P-02 | U1 | `Orientation.compose(a, b)` applies `b` first; `compose(a, inverse(a)) == identity` for all 48; `all()` has 48 distinct elements. | group laws hold; no duplicate |
| C-03 | P-04 | U1 | Applying an orientation to a signed `Heading` and to a `Cuboid` preserves volume and maps corners exactly. | no sign loss; volume invariant |
| C-04 | P-05 | U2 | `SnapshotId` is not `Comparable` across worlds; equality includes the world id and revision. | no cross-world ordering |
| C-05 | P-06 | U2 | `SnapshotPolicy` and every record with a collection defensively copy and expose an immutable, ordered view. | mutation cannot change a value |
| C-06 | P-39 | U2 | A `DENIED` read returns `ReadStatus.DENIED`, not air, and an expectation on it yields `UNREADABLE`; a genuinely empty cell is `READABLE` with an air occupant and yields a factual `MISMATCH`. | denied ≠ empty |
| C-07 | P-32 | U2, U7 | A refused read after an established `MISMATCH` adds an `UNREADABLE` but never erases the mismatch; the result stays `Failed`. | mismatch dominates |
| C-08 | P-45 | U3 | A predicate sees the frozen ledger through `ProbeSession.claimsAt`; another bay's same-attempt reservation is not visible, so verdict order cannot change. | ledger visible, reservations private |
| C-09 | P-69 | U3 | `ProbeSession.remaining()` decreases during a step; a predicate can refuse the next read without spending it. | live budget, not a step-entry snapshot |
| C-10 | P-43 | U3, U7 | A multi-cell bay is tested once, by one expectation, and claims the whole cuboid atomically; a half-door mismatch yields one `MISMATCH` with two labels. | one call, atomic claim |
| C-11 | P-37 | U7 | A `Formed` comes from `settle` alone and never from `check`; it binds every matched bay and settles every claim, so it never returns with an empty outcome or zero claims when the plan checked bays. | formed ⇒ `settle` wrote, outcome + claims present |
| C-12 | P-38 | U5, U7 | A stage plan written from the public model executes end to end: every step kind has a carrier, and a missing carrier is `UNREADABLE`, not silently skipped. | no dead step kind |
| C-13 | P-67 | U5, U7 | `ANCHOR`, `CENSUS` and `CLAIM` steps receive their carriers explicitly: `ANCHOR` the lease's view and the siting, `CENSUS` the view, `CLAIM` the host's frozen claim view — never through reflection. | no reflection |
| C-14 | P-70 | U7 | `Fingerprint` has a named algorithm and a `of(WorldView, Blueprint)` factory; the same content yields the same fingerprint across JVMs. | stable hash |
| C-15 | P-31 | U7 | A zero budget for one resource does not report exhaustion if the others remain; exhaustion is reported only when a read is actually refused. | no false exhaustion |
| C-16 | P-49 | U7 | `ReadSet` accumulation is amortised linear; a 170-cell walk is not quadratic. | linear enough |
| C-17 | P-50 | U7 | A bounds/census step is charged as a box comparison, not as one cell read per cell. | cost matches kind |
| C-18 | P-62 | U7, U8 | A deriver's reads are added to the result's `ReadSet`, so a change to the interior invalidates the formed instance. | derivation reads counted |
| C-19 | P-61 | U8 | Two augments writing one `AugmentKey` fold by the declared `AugmentFold`; a single augment still produces the key. | fold has a carrier |
| C-20 | P-52 | U8 | A derivation step's `Arguments` reach the deriver through `DerivationContext`, including a bound bay's cuboid. | parameters delivered |
| C-21 | P-53 | U8 | Derivation reads are charged to the budget and appear in `Spend`; a dropped augment is a finding, not silence. | charged and reported |
| C-22 | P-12 | U6, U8 | A `ContextValue` decoded from `DataView` carries its `DataKind`; a type mismatch is a finding, not a cast. | kind discriminator |
| C-23 | P-19 | U6 | At least one complete document compiles: validates, assembles and round-trips byte-for-byte after canonicalisation. | an admissible document exists |
| C-24 | P-14 | U9 | Every shipped `FindingCode` has an `Explanation`; `render` never depends on map order; a cap keeps the primary finding. | explainable, ordered |
| C-25 | P-07 | U9 | `FindingBundle.empty()` and `FindingBundle.none()` do not collide; the static factory has a distinct name. | legal Java |
| C-26 | P-11 | U9, U11 | Registry refusal (duplicate id, unknown family, late registration) has a stable code and a finding; no refusal is a bare exception. | refusal is data |
| C-27 | P-68 | U2, U3 | A nested occupant exposes a `NestedView`; a predicate can descend, the depth is budgeted, and a change inside the child invalidates the parent. | descent has a door |
| C-28 | P-44 | U11 | The kernel carries a `ClaimLedger` both entry points reach: `check` reads one revision and writes nothing, its `Admitted` carries the planned claim and that revision, a missing ledger leaves the plan `@Nullable` and the result `Undecided(LEDGER_UNAVAILABLE)`, and only `settle` writes rows. | ledger reachable; read-only in `check` |
| C-29 | — | U10 | A plan validates, applies, and rolls back after a mid-plan failure, including block-entity data and multi-cell edits. | rollback complete |
| C-30 | — | U10 | A preview generated for a finding and the repair plan for the same finding agree: applying the plan produces the preview's `PRESENT` cells and removes its `MISSING` cells. | preview ≡ repair |
| C-31 | — | U11 | A kernel created from a `Catalogs` snapshot is unaffected by a later addon registration; two kernels are isolated. | freeze |
| C-32 | — | A1 | `Heading`/`Turn`/`Tilt`/`Flip` round-trip through MC `Direction`/`Rotation`/`Mirror`; a rotated door's state is remapped exactly. | orientation parity |
| C-33 | — | A1, 20 | A block changes after FORM dispatch but before refresh; the result must fail the `changeCount` freshness term and never settle. | dirty mark beats a stale version |
| C-34 | — | A1, 20 | A chest empties mid-run; cached section copy still says `MATCH`; payload mark makes the result `DROPPED`, not `Formed`. | payload dirty is separate from block state |
| C-35 | — | 20 | Two COMMAND submits 200 ms apart: one RUNNING task, second coalesces; older ticket is discarded, newer runs. | one in-flight, ticket wins |
| C-36 | — | 20 | Level unload during FORM: no settle, no `onSettled`; `done()` still completes `Discarded`. | terminal completion without world effect |
| C-37 | — | 20 | Chunk unload mid-run, level alive: snapshot result stays coherent for its revision; next capture is `NOT_CAPTURED`, not air. | chunk unload ≠ failure |
| C-38 | — | 20 | 1.21.1 `y=64`, origin `-64`: `ChunkGrid.floorDiv` yields `cy=8`; a raw `>>4` gives the wrong section. | negative-origin grid |
| C-39 | — | 20 | A door spans two 16³ chunks: it is global, never cached as a local MATCH; a half-dirty chunk cannot form. | spanning/atomic step |
| C-40 | — | 20 | Cached step results re-run with different `FindingId`s; cached findings carry `NONE` and are re-minted; output is byte-identical. | no stale id |
| C-41 | — | 20 | With the optional `SectionStore` enabled, two sections share one entry; one watched structure is removed; the other keeps its copy and no update is lost. | shared store lifetime |
| C-42 | — | 20 | An addon expectation loops without a read; watchdog cancels, kernel's read-interval poll cannot interrupt it; scheduler degrades without killing the pool. | wedged custom step |
| C-43 | — | 20 | `AUTO` dirty event: only `PROBE` runs; a pass submits `FORM` separately; no full `FORM` is hidden in the AUTO task. | AUTO stays cheap |
| C-44 | — | 20 | Two overlapping snapshots with default settings own independent section copies; enabling `SectionStore` is the only path that shares them. | default per-snapshot |
| C-45 | — | 20 | A cow moves out of an entity bay between checks; dispatch re-queries `ReadSet.entities`, detects the footprint change and does not reuse the old `MATCH`. | entity movement found at dispatch |
| C-46 | — | 20 | Parallel section checking is `MatchPolicy.parallel`, off by default; enabling it changes neither the result nor `Spend`. | parallel is opt-in |
| C-47 | — | 20 | A plain `Expectation` is never cached as such; an entry is one unit's contributions or outcome, hit only on an equal `CacheKey(plan, siting, unit, closureDigest)`. | cacheability is `(unit, closureDigest)` |
| C-48 | — | 21 | The compact furnace JSON normalises to the same `Blueprint` as the canonical JSON. | one runtime model |
| C-49 | — | 21 | A root with both `segments` and `shell` under `DocumentProfile.AUTO` is rejected; detection is not last-wins. | profile detection is exclusive |
| C-50 | — | 21 | `shell`/`cavity` without `size` is rejected; no zero-size inference. | geometry requires bounds |
| C-51 | — | 21 | Capture/import of an unsupported block entity emits a finding and no silent payload loss. | tools never drop data silently |

## 4. Worked scenarios

1. **Small**: a 3×3×3 casing with one mouth. Probe fails on a wrong wall, form succeeds,
   the outcome binds three bays, claims settle, findings are empty.
2. **Medium**: a shared hatch between two machines at the same cell. One `SHARED` claim
   coexists; an `EXCLUSIVE` claim on the same cell produces a `ClaimConflict` at `SETTLE`.
3. **Large**: the huge furnace — 7×6×7 shell, mouth, interior bay; probe, form, derive
   interior volume (100), render a missing-wall finding, build it, roll back a failed build,
   then change one interior cell and re-check only the affected steps.
4. **Stale**: a formed instance, a `ChangeSet` on the request touching a read cell, a targeted
   re-check that reuses unchanged verdicts and re-runs the touched steps.
5. **Denied**: a chunk-denied cell in a bay; the result is `Undecided`, not `Failed`, and
   the finding points at the read status, not at a block.
6. **Door**: a two-cell door; one half present, one half missing; one `MISMATCH` with two
   labels and one atomic claim.

## 5. Proof obligations by invariant

| Invariant | Proof obligation | Evidence |
| --- | --- | --- |
| I-1 | kernel compiles with no MC; purity scan empty | build log |
| I-2 | a block, a block entity, an entity and a nested structure all match through one `OccupantSnapshot` interface | C-06, C-27 |
| I-3 | every footprint operation is `Cuboid`/`CuboidSet`; no `Set<LatticeKey>` in a public footprint | API scan |
| I-4 | same inputs ⇒ same result; no clock/random/global | C-02, C-14, determinism test |
| I-5 | no `Entity` in a kernel signature; a `Siting` is derived from an anchor candidate plus the policy | API scan, C-13 |
| I-6 | every check runs in a `Stage` the engine selects, with its own `CheckPlan`; `DESIGN` reads no world | C-12 |
| I-7 | findings are the only result channel; codes stable | C-24..C-26 |
| I-8 | kernel ships only `Expectations` and value types; no MC predicate | API scan |
| I-9 | `Computation`/`Deriver` reads the snapshot and context, never re-walks the blueprint | C-18..C-21 |
| I-10 | every settled cell carries a `sharing` value; a conflict is a finding | C-11, C-28 |
| I-11 | repair/preview/explain share `Locus` and `Applicability` | C-29, C-30 |
| I-12 | one document validates, canonicalises and round-trips | C-23 |
| I-13 | no stringly-typed public value channel; sealed families | API scan |
| I-14 | no public member throws unconditionally; absence is `@Nullable`/finding | API scan |
| I-15 | no `System.nanoTime`, `Random`, `Instant.now` in kernel | grep + review |

## 6. Review procedure

1. Run the vocabulary/dependency script; fix a synonym before reading prose.
2. Read the module's **API design** and **Semantics** against its glossary rows; a signature
   that contradicts the glossary is a defect in the module, not the glossary.
3. Apply the counterexample catalogue rows for that unit: does the module's contract make
   the test passable? If not, the module is incomplete.
4. Check the **Reference sketch** for a non-match path and an `Undecided` path.
5. Check **Open questions** are real questions with a proposed ruling, not deferred work.
6. Record the verdict in `_index.md`: drafted → reviewed, with the counterexamples the
   module covers and the ones deferred.

## 7. Exit criteria for this design round

- All twelve module files exist and pass the static vocabulary/dependency gate.
- Every counterexample row is either covered by a module contract or explicitly deferred
  with an owner and a reason.
- The golden example in `04-api-doctrine.md` §8 and the worked example in `17` use only
  frozen names.
- The kernel source set compiles with a `package-info.java` per package and no MC import.
- No module claims "implementation-ready"; the deliverable is a falsifiable design.
