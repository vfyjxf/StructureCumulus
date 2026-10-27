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

Current evidence (2026-10-28, HEAD `3e60ed4`): M1, M2a, M2b, M2c, M2d and M3 (`d4b6d82`) are
committed; M4a/M4b, the command slice (`settle` at `4899d86`, plus `compile`, `explain`, `claims`),
capture-revision lease invalidation (`0f0bf25`), the rotated-siting two-phase capture (P1, `56c632c`),
the event-edged watched-region mark counter (M5a, `d1fa42f`), the non-upright orientation remap (M5d,
`ac11d0c`), the capture report (M5e, `28d0334`) and the sited-root descriptor projection (S6, D-29)
are implemented, and the two stress batches add characterization scenarios: batch 1
(`kernel-stress-batch-1`, `110e797`) 44 kernel scenarios (§3.1) and batch 2
(`kernel-stress-batch-2`, `3e60ed4`) 17 kernel scenarios plus 11 adapter unit tests (§3.2).
`./gradlew check` is green: 186
kernel tests (33 test containers), 141 adapter unit tests, 0 failures, 0 skipped;
`runGameTestServer` passes 66/66.
`tools/spec-check.py` is clean. Model C, body identity, trait capture, the nested capability, the
mark counter, the capture-report channel and the definition-frame descriptor projection are
implemented, not pending. The determinism gate is
implemented (`DeterminismTest`): canonical bytes across member order, the parse/write fixed point,
equal compile runs and stable finding order. `Codes.all` has 57 entries and the decision record has 30
decisions (D-30).

The envelope batch (uncommitted, based on `1bf0cef`) adds `EnvelopeTest` (7 tests),
`EnvelopeScenarioTest` (12 tests) and three `DocumentCompilerTest` cases in the kernel, plus four
`JsonDocumentTest` cases in the adapter. Its focused runs are green: `compileKernelTestJava`,
`compileTestJava`, `kernelTest --tests '*Envelope*' --tests '*CodesTest' --tests
'*DocumentCompilerTest'` and `test --tests '*JsonDocumentTest'`; no full-suite run is claimed for
that batch.

**What remains.** Two items only: the performance/cost pass over the block-entity-lazy and entity
sweep, and fresh visual review screenshots. There is no regeneration milestone. The former
siting-aware `property` gap is closed by the host-side root descriptor projection (D-29): a sited root
publishes definition-frame descriptor properties, so `Expectations.property` needs no kernel change;
only an `Unrepresentable` state keeps the raw descriptor, and the same capture reports `SC-MC-0004`
for it. The projection adds no kernel type, so the spec index is unaffected.

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
16. **Envelope** — a 5×5×5 fusion torus declares only its 32-cell ring and `.envelope()`: clean air
    forms with 32 claims, a crystal in the void is `Failed(SC-EXP-0006)` at that cell and writes no
    row; an AE2 2×2×2 CPU has an empty `E`, so `.envelope()` equals the plain build and adds no
    check; a declared PNC port or GT EBF ring bay accepts a foreign block while an undeclared cell
    in `E` fails; an envelope-only blueprint is `Admitted` with zero rows and fails on one block;
    an unreadable cell is `Undecided(SC-OCC-0004)`, a tiny budget is `Undecided(SC-ENG-0001)`, and a
    block outside the bounds is invisible; the reserved id collides as `SC-BPT-0004`; `build()` is
    idempotent. The document member is root-only, is rejected on a definition, and compiles to the
    same `Blueprint`, `Plan` and `ReadScope` as `.envelope()`. Covered by `EnvelopeTest`,
    `EnvelopeScenarioTest`, `DocumentCompilerTest`, `JsonDocumentTest` and `CodesTest`.

### 3.1 Stress batch `kernel-stress-batch-1`

A second end-to-end suite over realistic multiblock shapes, tagged `kernel-stress-batch-1` and committed
at `110e797`: 12 scenario classes and 44 tests in `kernelTest/.../engine`, plus the `MagicFixtures` and
`TechFixtures` shape builders. It runs with the same in-memory `WorldView`/`ClaimLedger`/`ContextStore`
as §3 and adds no type, no decision and no kernel scenario obligation; it is a **characterization**
suite, and each gap below is asserted rather than hidden.

| Class | Shape it drives | Tests |
| --- | --- | --- |
| `Ae2CpuScenarioTest` | AE2 crafting CPU: 2×2×2 solid, missing storage cell, outside block, lattice edge, exclusive conflict | 5 |
| `AnchorValueScenarioTest` | declared anchor value; two allowed orientations sharing one north image | 3 |
| `AstralAltarScenarioTest` | Astral Altar tier II (54 cells) and tier III (72 cells) grown in place | 4 |
| `BloodAltarScenarioTest` | Blood Altar tier prefixes, any-rune slots, footprint variants, a NOTAIR probe | 5 |
| `ContextInstanceScenarioTest` | a context-focus check over already-published instance values | 1 |
| `DoorCoverageScenarioTest` | two-cell door coverage and a three-cell `everyCell` bay | 2 |
| `EntityWindowScenarioTest` | a read-scope entity window at a sited capture | 2 |
| `FusionVoidScenarioTest` | Mekanism fusion torus: hollow ring, air void, blocks hidden in the void | 4 |
| `GtEbfScenarioTest` | GT Modern EBF: 3×3×4 shell, nine coils, four yaw turns, host-side coil count | 4 |
| `PncRefineryScenarioTest` | PneumaticCraft refinery: two body layers, four outputs, controller on top, edge cases | 7 |
| `SeamScenarioTest` | a declared seam over air; a seam outside every bay | 2 |
| `TerraPlateScenarioTest` | Botania Terra Plate: 3×3 two-material base, one plate above, ten claimed cells | 5 |

**Characterized gaps** — documented limitations of the current kernel, each with its asserting test;
they are not owed work and not a pending list:

| Gap | Asserted by |
| --- | --- |
| `AnchorSpec.value` is ignored; only the anchor property's heading selects the orientation | `AnchorValueScenarioTest.anchorValueMismatchIsIgnored` |
| Two allowed orientations with one north image resolve to the canonical first match | `AnchorValueScenarioTest.twoAllowedOrientationsWithTheSameNorthImagePickTheFirst` |
| A check cannot read instance context already published in `ContextStore` (it stays `SC-CTX-0007`) | `ContextInstanceScenarioTest.checkCannotReadPersistedInstanceContext` |
| An entity read-scope window is published in the blueprint-local frame, not the world frame | `EntityWindowScenarioTest.sitedEntityWindowIsPublishedInTheLocalFrame` |
| `anyOccupant` has no fluid predicate, so water satisfies a NOTAIR slot | `BloodAltarScenarioTest.waterInANotAirSlotIsAdmittedAsAKnownFalsePositive` |
| A cell outside every bay is ignored unless the blueprint asks for `envelope`; with `"envelope": true` the bounds are closed (a non-air occupant inside is `SC-EXP-0006`) and only cells outside `bounds` stay invisible | Without the flag: `Ae2CpuScenarioTest.anExtraBlockJustOutsideTheBayIsAdmitted`, `GtEbfScenarioTest.anExtraHatchOutsideTheBayIsNotEvidence`, `PncRefineryScenarioTest.aFifthOutputOutsideTheBayIsAdmitted`, `TerraPlateScenarioTest.anExtraBlockAboveThePlateIsIgnored`. With the flag: `EnvelopeScenarioTest.fusionTorusEnvelopeIsCleanAtAHalfTurn`, `EnvelopeScenarioTest.aCrystalInTheVoidFailsAtItsCell`, `EnvelopeScenarioTest.envelopeOnlyBlueprintIsAdmittedWithNoRows` |
| Per-cell checks cannot express "exactly one controller" or a mixed coil count; a host `Computation`/aggregate must | `PncRefineryScenarioTest.twoControllersAreAdmittedByPerCellChecks`, `PncRefineryScenarioTest.aHostCountRejectsTwoControllers`, `GtEbfScenarioTest.mixedCoilCountNeedsAHostAggregate` |
| A seam is inert: it declares a connectable edge, never feeds the engine and never owns a cell | `SeamScenarioTest.aDeclaredSeamOverAirPassesAndDoesNotChangeTheResult` |

**DoorFocus coverage fixed.** `DoorFocus.staticCells` now answers the two cells it actually reads
(`origin` and `origin + (0,1,0)`) instead of the whole bay, so a two-cell door satisfies
`Coverage.everyCell` and a three-cell `everyCell` bay fails the build with undecided cells instead of
being silently covered (`DoorCoverageScenarioTest`, `110e797`).

### 3.2 Stress batch `kernel-stress-batch-2`

The second characterization batch, tagged `kernel-stress-batch-2` and committed at `3e60ed4`: 4 kernel
scenario classes (17 tests) in `kernelTest/.../engine` and 3 adapter unit classes (11 tests) in
`src/test/.../mc`. The one code change is the `NestedFocus` fix below; the suite stays
characterization, not a new kernel promise.

| Suite | Class | Shape it drives | Tests |
| --- | --- | --- | --- |
| kernel | `NestedCoverageScenarioTest` | a nested focus binds no subject; `oneSubject + Bound` at build; `anySubject + Bound` at check; a cut child; a child-frame occupant | 4 |
| kernel | `ClaimStressScenarioTest` | shared/exclusive walls, per-owner release, whole-key settle replacement, duplicate rows | 5 |
| kernel | `ContextAggregateScenarioTest` | a custom aggregate, a missing aggregate result, `whenAbsent` on a check-only key, a derivation output | 4 |
| kernel | `GateBudgetScenarioTest` | stale lease at check, mid-attempt invalidation, invalidation before settle, a tiny budget | 4 |
| adapter | `EntityWindowPolicyTest` | the blueprint-local entity window copied into `CommandSupport.policy`, and a nonzero siting | 2 |
| adapter | `SavedClaimsLifecycleTest` | stale CAS, conflict bytes, whole-key release, duplicate-row canonical collapse | 4 |
| adapter | `SavedContextsLifecycleTest` | serial monotonicity, whole-context publish/reload, instance isolation, growth, unknown type | 5 |

**Characterized gaps** — documented limitations, each with its asserting test; they are not owed work
and not a pending list:

| Gap | Asserted by |
| --- | --- |
| `Coverage.anySubject + Footprint.bound` accepts a nested body at build, then check rejects it with `SC-CLM-0004` | `NestedCoverageScenarioTest.nestedAnySubjectBoundBodyBuildsThenFailsAtCheck` |
| Two `shared` bays over one cell write duplicate claim rows | `ClaimStressScenarioTest.duplicateRowsAreCharacterized` |
| `whenAbsent` is a dead path for a check-only key: it needs a section computation, so a check cannot reach it | `ContextAggregateScenarioTest.whenAbsentIsADeadPathForACheckOnlyKey` |
| D4: `CommandSupport.policy` copies the blueprint-local entity window and a nonzero siting never maps it to the world frame | `EntityWindowPolicyTest.policyCopiesTheLocalEntityWindow`, `EntityWindowPolicyTest.aNonzeroSitingStillLeavesThePolicyWindowLocal` |
| D5: `SavedContexts` grows one entry per instance without eviction | `SavedContextsLifecycleTest.growthWithoutEvictionKeepsEveryInstance` |
| Claim characterization: `release` removes the whole `(owner, siting)` key, not one row, and a reload collapses duplicate rows into one canonical order | `SavedClaimsLifecycleTest.releaseRemovesTheWholeOwnerSitingKey`, `SavedClaimsLifecycleTest.duplicateRowsCollapseAndReadIsCanonical` |

**NestedFocus fixed.** `NestedFocus.bindsSubjects()` is now false, so a nested focus never backs a
`Coverage.oneSubject` bay: `oneSubject + Bound + nested` fails the build with `SC-BPT-0005` instead of
building a blueprint that can never admit (`NestedCoverageScenarioTest`, `3e60ed4`; `structure` §4.4).
The `anySubject + Bound` acceptance above remains the open half and is characterized, not fixed.

## 4. Module tests

| Module | Must test |
| --- | --- |
| `core` | id grammar, catalog freeze, Value content equality, budget, key equality |
| `space` | packing bijection, cuboid edges, CuboidSet normalization and semantic-equality laws, 48 orientations, mirror `1-`, placement laws, section frame, locus frames |
| `finding` | code parse, `Codes.all` bijection, builder validation, bundle dedup/order/cap, renderers, source text |
| `world` | total read vs bounds/air, partial/refused section, body identity and nullable order, trait four states and id lookup, `SnapshotPolicy.traits` ids, root/child origin and local bounds, `statusOf` derivation, window, capture-report payload and its compatibility constructors, descriptor frame (the property leaf compares the published string and never transforms it) |
| `claim` | matrix four pairs, ledger CAS/release, canonical order, footprint resolution, nested/non-root subject rejection (`SC-CLM-0004`) |
| `structure` | bay/coverage rules, reply combinators, focus kinds, nested focus (anchor-only, no subject, cut), four-state trait expectations, build table, `Blueprint.reads` union, plan DAG, anchors, partial-slice computations, derived envelope (empty no-op, reserved id, idempotent build) |
| `context` | context view, publish merge/revision, derive order, merges |
| `document` | schema validation, placement/alias, key types, compile parity, determinism, trait/read-scope parity, root-only envelope and definition rejection |
| `engine` | §3 scenarios and the §3.1/§3.2 stress batches, gate, budget, fold, ledger retry, partial-section `Missing`, nested-claim rejection, envelope strict air, unreadable and budget paths |
| `remedy` | provider order/cap, edit/overlay values |
| `spi` | builtins, host empty, facade paths, two-kernel isolation |

## 5. Code producers

The authoritative table is `finding.md` §6. `Codes.all` has 57 entries in this revision, matching the
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
21. Can a cell inside `bounds` escape the envelope, or a cell outside it become evidence? (structure:
    `E` is the bounds minus every bay and declared footprint; engine: only `E` is read, and a siting
    maps it)

## 7. What is not verified in this round

- The rest of the NeoForge adapter and its GameTests. All 66 GameTests are executed by
  `runGameTestServer`: M1 capture/host, M2a entity precedence, M2b orientation, M3 claims, M2c traits,
  M2d nested frames/cuts, capture-revision lease invalidation (`LeaseLifecycleGameTest`), the
  watched-region mark counter (`LeaseMarkCounterGameTest`), the rotated-siting two-phase capture
  (`RotatedSitingGameTest`), the M4 command surface (`SettleCommandGameTest`, `CommandGameTest`), the
  M5e capture report (`CaptureAuditGameTest`, `CommandCaptureReportGameTest`, `NestedCaptureGameTest`),
  the claims read in check (`CheckClaimsGameTest`) and the S6 projection
  (`SitedDescriptorGameTest`: upright and non-upright definition-frame descriptors,
  unoriented/identity raw descriptors, the unrepresentable raw fallback with `SC-MC-0004`, a
  `property` expectation matching at a rotated siting, and child frames staying world-frame). Unit
  evidence: `LevelSnapshotTest` for
  body/precedence/partial reads, `OccupantTraitsTest` for the five trait keys, `NestedExtentTest` for
  the capability declaration, `SavedClaimsTest`/`SavedContextsTest` for persistence,
  `OrientationMappingTest` for the orientation mapping, `DeterminismTest` for canonical bytes, the
  parse/write fixed point and equal compile runs, `ServerStateDocumentSourceTest`/
  `CommandSupportTest`/`CommandRouterTest` for the M4 sources and command plumbing, `LevelWatchTest`
  for the mark counter's refcounts and lazy fingerprints, `CaptureReportsTest` for the report
  site, and the batch-2 persistence/policy units `EntityWindowPolicyTest` (D4 local-window
  characterization), `SavedClaimsLifecycleTest` (CAS, conflict, whole-key release, duplicate-row
  collapse) and `SavedContextsLifecycleTest` (merge/reload, instance isolation, D5 growth, unknown
  type). **What remains:** the performance/cost pass (block-entity-lazy and entity sweep) and fresh
  visual review screenshots. There is no regeneration milestone.
- The scheduler, dirty index and per-tick window diff; host-side, out of v1.
- Rendering of overlays; the kernel produces data only.
- A real JSON parser; the kernel ships `Value` and the ports; a test writer/reader is test-only.
