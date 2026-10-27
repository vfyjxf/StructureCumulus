# 06 — Verification

> What must be true before the design is accepted, and how each claim is falsified. The design is a
> falsifiable draft; a module without a counterexample is not reviewed.

## 1. Gates

| Gate | Command | Pass means |
| --- | --- | --- |
| **Compile** | `javac --release 21` over `src/kernel/java` with JSpecify only | every public signature in the module files exists and compiles |
| **Purity** | `kernelPurity` text scan + no Minecraft on the kernel classpath | no `net.minecraft`, `net.neoforged`, `com.mojang` under `kernel.**` |
| **Tests** | JUnit 5 over `src/kernelTest/java` | every test in §3 passes; no `@Disabled` |
| **Spec index** | `tools/spec-check.py` | every documented public type and `SC-` code has a producer; no extra public type |
| **No stubs** | grep | no `TODO`, `FIXME`, `UnsupportedOperationException`, empty public method body |
| **Determinism** | golden test | two runs over one tuple produce equal values, orders and spend |

Gradle is the shipping build (`./gradlew check`, including Spotless and the kernel gates). A
regeneration session without the wrapper cache must still run `javac` and the JUnit launcher directly
and record the commands.

Current evidence (2026-10-27, HEAD `28d0334`): M1, M2a, M2b, M2c, M2d and M3 (`d4b6d82`) are
committed; M4a/M4b, the command slice (`settle` at `4899d86`, plus `compile`, `explain`, `claims`),
capture-revision lease invalidation (`0f0bf25`), the rotated-siting two-phase capture (P1, `56c632c`),
the event-edged watched-region mark counter (M5a, `d1fa42f`), the non-upright orientation remap (M5d,
`ac11d0c`) and the capture report (M5e, `28d0334`) are implemented. `./gradlew check` is green: 125
kernel tests and 130 adapter unit tests, 0 failures, 0 skipped; `runGameTestServer` passes 60/60.
`tools/spec-check.py` is clean. Model C, body identity, trait capture, the nested capability, the
mark counter and the capture-report channel are implemented, not pending. The determinism gate is
implemented (`DeterminismTest`): canonical bytes across member order, the parse/write fixed point,
equal compile runs and stable finding order. `Codes.all` has 56 entries and the decision record has 28
decisions (D-28).

**What remains.** Three items only, none of them a kernel registry or API gap: the `property`
expectation is not siting-aware (a pre-existing kernel gap — it compares the raw descriptor value, so
a rotated siting can report `SC-EXP-0001` next to the capture's `SC-MC-0004` capability warning; the
`FocusContext.siting()` seam exists), the performance/cost pass over the block-entity-lazy and entity
sweep, and fresh visual review screenshots. There is no regeneration milestone.

**Spec index.** `tools/spec-check.py` reports every documented type present in code and every code
in code documented; it is green. There is no documented-but-not-in-code list and no suppression.

## 2. Requirement → module → test

| Requirement | Modules | Test |
| --- | --- | --- |
| F-01 occupants are not blocks | world, structure | entity pen; nested |
| F-02 purity | all | purity gate |
| F-03 typed context | context, structure | furnace capacity, iron count |
| F-04 anchor/no controller | structure, space | anchor read; orientation policy |
| F-05 atomic bay/door | structure, claim | door scenario |
| F-06 claims | claim, engine | hatch, door, pipe |
| F-07 check/settle split | engine, spi | "check writes nothing"; settle conflict |
| F-08 data + code one IR | document | campfire/furnace parity |
| F-09 findings | finding | golden human text; bundle order |
| F-10 catalogs | spi, structure | builtin + addon factory |
| F-11 remedy values | remedy | provider order/cap |
| F-12 determinism/budget/cancel | core, engine | budget, duplicate run, cancel |

## 3. Scenario tests

These are end-to-end kernel tests with an in-memory `WorldView`, `ClaimLedger` and `ContextStore`.
They are the acceptance suite; a module test that does not feed one of them is optional.

1. **Campfire** — 3×3×3 shell + hollow + lit fire; 3 checks; `air` section computation; check →
   admitted → settle → formed → derive. Asserts the shell subtraction, air occupant, property test,
   the published `Long`, and that `check` wrote no claim.
2. **Furnace** — shell/cavity/mouth, `Box` cavity, door expectation, `volume` + capacity deriver;
   asserts claim rows, context revision, and a second check at the same siting does not self-conflict.
3. **Door** — one bay, two halves, `oneSubject + Bound`; missing half → `Undecided`; wrong half →
   `Failed` with one finding and two labels; both correct → two rows.
4. **Hatch** — two `shared` owners coexist; an `exclusive` owner conflicts; `release` lets a third
   settle; a revision moved once retries, twice is `Undecided`.
5. **Pipe corner** — `everyCell + Bound`; eight asserted, four owned; a neighbour's corner does not
   conflict over the four air cells.
6. **Entity pen** — `anySubject + Bound`, entity focus, window read; a footprint leaving the bay is
   `Failed`; two entities is ambiguous; a body wider than `maxEntitySpan` is a build error.
7. **Absence** — a refused middle section makes a contributed key absent; a check reading it is
   `Unreadable`, never a short sum; `whenAbsent` is used only when all sections are readable.
8. **Budget** — a budget too small for the section pass is `Undecided(budgetExhausted)`, no claims.
9. **Gate** — a lease that is no longer current at the start or after the units is `Dropped`, no
   claims, no context publish.
10. **Bindings** — a subject from another snapshot, a nested frame, a cell outside the bay, and an
    empty exclusive `Bound` are each `Failed(SC-CLM-0004/0005)`; nothing is written.
11. **Derive** — derivers run in `(order, id)`, one publish, missing deriver is `SC-CTX-0004`, a
    `match` output is rejected, a stale context is not possible by contract.
12. **Document parity** — the campfire/furnace as a `Document` and as a builder produce equal
    blueprints; a bad schema field/kind/placement produces the documented code and no blueprint.
13. **Refusal** — a `Partial` section: a `count`/`air` computation returns `Missing` and reports the
    refusal, never a short sum; a check over the refused cell is `Unreadable`; a host code is relayed,
    and a relayed kernel code becomes `SC-OCC-0004` with a note; `not(trait(...))` stays `Unreadable`;
    missing-slot traits are never `Mismatch`.
14. **Nested** — one snapshot with a child frame: distinct frame/depth/parent, local bounds and
    `world = origin + local`; `nested(cell)` anchor-only; a cut child keeps the body and yields
    `SC-OCC-0003`; a nested body bound as a claim is `Failed(SC-CLM-0004)` at claim prediction; a
    child build-height section is refused, not air; two same-type/same-anchor entities are distinct;
    every cut also answers one `SC-MC-0003` (WARNING) capture entry with its reason, and the check
    still reports `SC-OCC-0003` when it needs the child (no suppression, no translation). Covered by
    `NestedCaptureGameTest`.
15. **Capture report** — a capture answers with a `CaptureReport` on every arm: a root over the
    element ceiling is `TooLarge` plus one `SC-MC-0005` (ERROR) entry in its report; a cut child is
    `Captured` plus one `SC-MC-0003` (WARNING) entry; a sited capture with a non-upright orientation
    answers one `SC-MC-0004` (WARNING) entry per `(block type, property, value)` group and none for
    an upright siting; each two-argument compatibility constructor equals its three-argument form
    with `empty()`. The entries are host codes: none enters `Codes.all`, none changes an arm's
    meaning, none enters a `MatchResult` bundle, and the adapter's own catalog supplies their
    severity and explanation. Covered by `CaptureReportTest` (payload), `CaptureReportsTest`
    (severity, trust guard, world label, notes), `NestedCaptureGameTest` (over-cap and cut entries),
    `CaptureAuditGameTest` (the orientation audit) and `CommandCaptureReportGameTest` (print order and
    command surfacing).

## 4. Module tests

| Module | Must test |
| --- | --- |
| `core` | id grammar, catalog freeze, Value content equality, budget, key equality |
| `space` | packing bijection, cuboid edges, CuboidSet normalization and semantic-equality laws, 48 orientations, mirror `1-`, placement laws, section frame, locus frames |
| `finding` | code parse, `Codes.all` bijection, builder validation, bundle dedup/order/cap, renderers, source text |
| `world` | total read vs bounds/air, partial/refused section, body identity and nullable order, trait four states and id lookup, `SnapshotPolicy.traits` ids, root/child origin and local bounds, `statusOf` derivation, window, capture-report payload and its compatibility constructors |
| `claim` | matrix four pairs, ledger CAS/release, canonical order, footprint resolution, nested/non-root subject rejection (`SC-CLM-0004`) |
| `structure` | bay/coverage rules, reply combinators, focus kinds, nested focus (anchor-only, no subject, cut), four-state trait expectations, build table, `Blueprint.reads` union, plan DAG, anchors, partial-slice computations |
| `context` | context view, publish merge/revision, derive order, merges |
| `document` | schema validation, placement/alias, key types, compile parity, determinism, trait/read-scope parity |
| `engine` | §3 scenarios, gate, budget, fold, ledger retry, partial-section `Missing`, nested-claim rejection |
| `remedy` | provider order/cap, edit/overlay values |
| `spi` | builtins, host empty, facade paths, two-kernel isolation |

## 5. Code producers

The authoritative table is `finding.md` §6. `Codes.all` has 56 entries in v1.2, matching the
registry. A test reads `Codes.all` and asserts:

- every entry has a non-blank summary and body;
- every code appears exactly once in the producer table (a hand-maintained `Map<FindingCode,String>`
  in the test, checked against `Codes.all`; the `SC-MC-*` wildcard row is excluded from the map);
- no producer row names a code absent from `Codes.all`;
- `SC-MC-*` are absent from `Codes.all` (adapter-only), including the capture-report codes
  `SC-MC-0003`/`0004`/`0005` that M5e ships (D-28); the adapter registers them in its own code
  catalog, which is not the kernel registry;
- a relayed host code is not allocated by the kernel, and a relayed code already in `Codes.all` is
  replaced by `SC-OCC-0004` with a note — the same guard covers a `CaptureReport` entry.

A code with no producer is deleted, not reserved. An unknown addon code is legal; it is not in
`Codes.all`.

## 6. Falsification questions

The reviewer's job is to find a concrete structure or snapshot that breaks a claim:

1. Can a `Formed` exist without a `Settled`? (engine: no path)
2. Can a `check` write a claim or a context value? (engine: no writer)
3. Can a `Mismatch` and an `Unreadable` collapse in the fold? (structure/engine)
4. Can a `Bound` footprint own a cell no check asserted? (claim/structure)
5. Can a nested subject become a parent claim? (engine: frame check)
6. Can a `Bay` with a `CuboidSet` shell be split differently by the Java and JSON paths? (same `Bay`)
7. Can two `shared` bays of one blueprint conflict? (matrix: same owner/siting excluded)
8. Can a finding be ordered differently by two JVMs? (all orders are explicit)
9. Can a document with reordered members produce a different blueprint? (canonical compile)
10. Can a stale lease produce a `Formed`? (gate before and after)
11. Can a budget-exhausted attempt be turned into a `Formed` by a warm cache? (no cache)
12. Can a missing extension be silently replaced by a default? (no reflection, no fallback)
13. Can a refused cell become air, a zero, or a short sum? (world/`Computations`: `Unreadable`,
    then `Missing` + report)
14. Can `not(trait(...))` turn a refused or unasked trait into a pass? (structure: `Unreadable`
    stays)
15. Can a nested body or a non-root subject become a claim row? (engine: `SC-CLM-0004`)
16. Can a host code enter `Codes.all` or impersonate a kernel code? (world/finding: opaque payload,
    trust guard)
17. Can a cut child appear as a refused or empty child? (adapter: `null` + `SC-OCC-0003`)
18. Can a non-empty `SnapshotPolicy.traits` become empty in the constructor? (world: copy by id, no
    silent drop)
19. Can a capture report change an arm's meaning, a `MatchResult` bundle, a command exit code or a
    code's registry membership? (world §4.10: the arm is the verdict, the report is the finding, host
    codes never enter `Codes.all`)
20. Can a cut be reported twice in one bundle, or by two codes for one fact? (adapter: the capture
    entry and `SC-OCC-0003` live in two channels; neither is translated into the other)

## 7. What is not verified in this round

- The rest of the NeoForge adapter and its GameTests. All 60 GameTests are executed by
  `runGameTestServer`: M1 capture/host, M2a entity precedence, M2b orientation, M3 claims, M2c traits,
  M2d nested frames/cuts, capture-revision lease invalidation (`LeaseLifecycleGameTest`), the
  watched-region mark counter (`LeaseMarkCounterGameTest`), the rotated-siting two-phase capture
  (`RotatedSitingGameTest`), the M4 command surface (`SettleCommandGameTest`, `CommandGameTest`), the
  M5e capture report (`CaptureAuditGameTest`, `CommandCaptureReportGameTest`, `NestedCaptureGameTest`)
  and the claims read in check (`CheckClaimsGameTest`). Unit evidence: `LevelSnapshotTest` for
  body/precedence/partial reads, `OccupantTraitsTest` for the five trait keys, `NestedExtentTest` for
  the capability declaration, `SavedClaimsTest`/`SavedContextsTest` for persistence,
  `OrientationMappingTest` for the orientation mapping, `DeterminismTest` for canonical bytes, the
  parse/write fixed point and equal compile runs, `ServerStateDocumentSourceTest`/
  `CommandSupportTest`/`CommandRouterTest` for the M4 sources and command plumbing, `LevelWatchTest`
  for the mark counter's refcounts and lazy fingerprints, and `CaptureReportsTest` for the report
  site. **What remains:** the pre-existing kernel gap that a `property` expectation is not
  siting-aware, the performance/cost pass (block-entity-lazy and entity sweep) and fresh visual review
  screenshots. There is no regeneration milestone.
- The scheduler, dirty index and per-tick window diff; host-side, out of v1.
- Rendering of overlays; the kernel produces data only.
- A real JSON parser; the kernel ships `Value` and the ports; a test writer/reader is test-only.
