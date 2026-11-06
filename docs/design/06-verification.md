# 06 — Verification

> What must be true before the design is accepted, and how each claim is falsified. The design is a
> falsifiable draft; a module without a counterexample is not reviewed.

## 1. Gates

| Gate | Command | Pass means |
| --- | --- | --- |
| **Compile** | `javac --release 21` over `src/kernel/java` with JSpecify only | every public signature in the module files exists and compiles |
| **Purity** | `kernelPurity` text scan + no Minecraft on the kernel classpath | no `net.minecraft`, `net.neoforged`, `com.mojang` under `kernel.**` |
| **Tests** | JUnit 5 over `src/kernelTest/java` | every test in §3 passes; no `@Disabled` |
| **Spec index** | `tools/spec-check.py` (name level, kernel + adapter) and `SpecIndexTest` / `AdapterIndexTest` (member level) | every documented public type and `SC-` code has a producer; no extra public type; the producer table matches `Codes.all` row for row; every documented member matches the code and vice versa — the kernel wholesale, the adapter in batches whose uncovered list (`tools/spec-uncovered.txt`) only shrinks (D-74) |
| **No stubs** | grep | no `TODO`, `FIXME`, `UnsupportedOperationException`, empty public method body |
| **Determinism** | `determinismCheck` (three JVM processes vs `tools/golden/determinism.txt`) | one fixed scenario renders the same canonical text in three separate JVMs, equal to the golden file |

Gradle is the shipping build (`./gradlew check`, including Spotless and the kernel gates). A
regeneration session without the wrapper cache must still run `javac` and the JUnit launcher directly
and record the commands.

The **Determinism** gate is `determinismCheck` (D-75): `DeterminismDump` in `kernelTest` renders one
fixed bay + checks + computation + three derivations + anchor scenario through check, settle to an
in-memory ledger and derive, and prints canonical `blueprint`/`finding`/`claim`/`context`/`frame`
lines. The digest line folds the iteration order of `SnapshotPolicy.kinds`/`traits` and
`ComputationSpec.inputs`. The task runs the dump in three separate JVM processes under different
`-XX:hashCode` modes — the hash salt repeats inside one process, so a same-process comparison cannot
catch a hash-order leak — and every run must equal `tools/golden/determinism.txt`; `-PupdateGolden`
rewrites the golden from the first run, `-PskipDeterminism` skips the gate. Reintroducing a
`Set.copyOf` at either site fails the gate in at least one process.

### 1.1 Green build

Until the repository has a remote, the gates run on the development machine (D-82,
`05-decisions.md`). A green build is exactly this list:

| Command | Expected result |
| --- | --- |
| `./gradlew check` | exit 0 — every source set compiles (Error Prone and NullAway included), Spotless and the PMD gates pass, `kernelPurity` finds no host names, and the kernel and adapter unit suites are green |
| `./gradlew kernelTest test` | exit 0 — the kernel suite and the adapter unit suite pass, no failure and no skipped test |
| `./gradlew runGameTestServer` | the game-test server run completes with every registered `@GameTest` method green |
| `python3 tools/spec-check.py` | prints `spec-check: ok` — every documented public type and `SC-` code has a producer, no extra public type, and the producer table matches `Codes.all` row for row |
| `./gradlew jmh` | the JMH benchmarks run outside `check` and report throughput and latency; run it when a change touches the kernel's hot paths |

When a remote exists, a workflow runs that same list; this document stays the source of truth so the
workflow cannot drift from it.

Current evidence (2026-11-06, this tree): M1, M2a, M2b, M2c, M2d and
M3 (`d4b6d82`) are committed; M4a/M4b, the command slice (`settle` at `4899d86`, plus `compile`,
`explain`, `claims`), capture-revision lease invalidation (`0f0bf25`), the rotated-siting two-phase
capture (P1, `56c632c`), the event-edged watched-region mark counter (M5a, `d1fa42f`), the non-upright
orientation remap (M5d, `ac11d0c`), the capture report (M5e, `28d0334`) and the sited-root descriptor
projection (the old S6 label, D-29) were implemented. D-29 is superseded by the typed host snapshot (D-33,
`10-host-data.md`): the projection is retired, `OccupantDescriptor` is `(type, kind)`, and
`OrientationAuditGameTest` covers the retained orientation audit. The two stress batches add
characterization scenarios: batch 1 (`kernel-stress-batch-1`, `110e797`) 44 kernel scenarios (§3.1)
and batch 2 (`kernel-stress-batch-2`, `3e60ed4`) 17 kernel scenarios plus 11 adapter unit tests
(§3.2). The envelope (`a5f7084`), the cached context port (`681c505`, D-31), the structure-set
document/compiler (S2, `00cf6c4`) and the host set layer (S3, `93f5941`, D-32) are implemented; SS4
adds the six-class/33-test structure-set stress batch (§3.3). The host-data round closes with H4:
H1 adds `HostType`/`WorldView.get`/`Expectations.host` and `OccupantDescriptor(type, kind)`; H2 adds
one `SnapshotLevel` per frame and the capture wiring; H3 adds the adapter host expectation factories
and `SC-MC-0006`…`0010`; H4 adds the counts below and the host-data stress group (§3.4). The
T1–T3 trait-provider slice (D-34) then replaces the provider shape and the trait values. S5 adds the
production set command and its one capture, the check-only host path and the set GameTests (§3.5). S6
closes the set layer (D-35): `SC-SET-0008` same-siting rejection, `StructureEvent.Broken` rollback
invalidation, and one set budget shared by presence and every member attempt (§3.3). The manager round
(D-40 with D-44/D-45) then makes claims session-scoped and instance-keyed, and D-47 moves block
observation into the funnel. The current
suites total 541 kernel tests (65 containers), 440 adapter unit tests (46 containers) and 140
`@GameTest` methods. `./gradlew check` and `runGameTestServer` are green on this tree (541 kernel / 65 containers, 440 adapter / 46 containers, 140/140 GameTests; check exit 0). `tools/spec-check.py` is clean.
`Codes.all` has 66 entries and the decision record has 80 decisions (D-82; D-78 withdrawn). Model C, body identity,
trait capture, the generic trait providers, the nested capability, the mark counter, the
capture-report channel, the typed host snapshot, the cached-context port and the structure-set layer
are implemented, not pending. The
determinism gate is implemented (`DeterminismTest`): canonical bytes across member order, the
parse/write fixed point, equal compile runs and stable finding order.

The envelope batch (`a5f7084`) adds `EnvelopeTest` (7 tests), `EnvelopeScenarioTest` (12 tests) and
three `DocumentCompilerTest` cases in the kernel, plus four `JsonDocumentTest` cases in the adapter.
The context-cache batch (`681c505`) adds 31 kernel tests across four classes and changes the aggregate
scenario; the structure-set slices add `StructureSetCompilerTest` (15 kernel tests),
`JsonStructureSetTest` (10), `StructureSetPlanTest` (8), `StructureSetHostTest` (26, two of them S5's
check-only path), `DatapackStructureSetTest` (5) and the SS4 stress suite (34 after S6's shared-budget
case). S6 also adds `ContextTest` (4); D-40 adds `StructureManagerTest` (9) and `StructureLifecycleGameTest` (3);
the adversarial round adds `AdversarialManagerTest` (41), `AdversarialClaimCoverageTest` (18),
`AdversarialExpectationTest` (13) and `AdversarialFuzzTest` (8), plus the three GameTest classes in
§3.6. S5 adds the two
real-level GameTest classes below (§3.5). The host-data round adds
`HostExpectationTest` (7 kernel tests), `McAnchorsTest` (7), `SnapshotLevelTest` (7),
`LevelSnapshotTest` (17), `AdapterExpectationsTest` (12) and the H4 `HostDataScenarioTest` (4, §3.4).
Each batch landed with focused runs (`compileKernelTestJava`, `compileTestJava`, `kernelTest` and
`test` selections); the full-suite result above covers them on the current tree.

**What remains.** Two items only: acting on the measured kernel-side cost debts and the live-level
review items (`07` §7), and fresh visual review screenshots. There is no regeneration milestone. The
former
siting-aware `property` gap is now the typed host snapshot (D-33): a value check reads the captured
snapshot, so the kernel needs no property descriptor and the adapter projects nothing. A state that
cannot express the pose is still `SC-MC-0004` in the capture report; a missing value is
`SC-MC-0010`, never a mismatch. The snapshot adds no kernel type beyond `HostType` and
`WorldView.get`, so the spec index is unaffected.

**Spec index.** Two layers. `tools/spec-check.py` (wired into `./gradlew check` as `specCheck`) is
the name-level gate: every documented type present in code, every code type documented, every code
`SC-` code produced, and — for the adapter — every public top-level type either documented in
`adapter.md` §15 or listed with a reason in `tools/spec-uncovered.txt`, where the gate fails on a
stale or doubly-registered line so the list only shrinks (D-74). The member-level gate runs in
`kernelTest`: `SpecIndexTest` compares every kernel module's `## 3. Signatures` block with the
compiled kernel through reflection (arity, parameter and return type names, `@Nullable` positions),
and `AdapterIndexTest` compares the documented adapter batch with the sources under
`src/main/java`. The kernel side has no list and no suppression; the adapter list is batch debt, not
suppression, and batch 1 (set host, snapshot pair, host tokens, document family) is documented.

## 2. Requirement → module → test

| Requirement | Modules | Test | Status |
| --- | --- | --- | --- |
| F-01 occupants are not blocks | world, structure | entity pen; nested | implemented |
| F-02 purity | all | purity gate | implemented |
| F-03 typed context | context, structure | furnace capacity, iron count | implemented |
| F-04 anchor/no controller | structure, space | anchor read; orientation policy | implemented |
| F-05 atomic bay/door | structure, claim | door scenario | implemented |
| F-06 claims | claim, engine | hatch, door, pipe | implemented |
| F-07 check/settle split | engine, spi | "check writes nothing"; settle conflict | implemented |
| F-08 data + code one IR | document | campfire/furnace parity | implemented |
| F-09 findings | finding | golden human text; bundle order | implemented |
| F-10 catalogs | spi, structure | builtin + addon factory | implemented |
| F-11 remedy values | remedy | provider order/cap | implemented |
| F-12 determinism/budget/cancel | core, engine | budget, duplicate run, cancel | implemented |

## 3. Scenario tests

These are end-to-end kernel tests with an in-memory `WorldView`, `ClaimLedger` and `ContextStore`.
They are the acceptance suite; a module test that does not feed one of them is optional.

1. **Campfire** — 3×3×3 shell + hollow + lit fire; 3 checks; `air` section computation; check →
   admitted → settle → formed → derive. Asserts the shell subtraction, air occupant, block test,
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
   `Unreadable`, never a short sum.
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
`TechFixtures` shape builders. H1 removed `AnchorValueScenarioTest` together with the kernel anchor
resolver, so the current tree has 11 classes and 41 tests; `McAnchorsTest` covers the captured anchor,
the declared value and the missing-state code. It runs with the same in-memory `WorldView`/`ClaimLedger`/`ContextStore`
as §3 and adds no type, no decision and no kernel scenario obligation; it is a **characterization**
suite, and each gap below is asserted rather than hidden.

| Class | Shape it drives | Tests |
| --- | --- | --- |
| `Ae2CpuScenarioTest` | AE2 crafting CPU: 2×2×2 solid, missing storage cell, outside block, lattice edge, exclusive conflict | 5 |
| `AstralAltarScenarioTest` | Astral Altar tier II (54 cells) and tier III (72 cells) grown in place | 3 |
| `BloodAltarScenarioTest` | Blood Altar tier prefixes, any-rune slots, footprint variants, a NOTAIR probe and the empty/non-fluid classification | 6 |
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
| A check cannot read instance context already published in `ContextStore` (it stays `SC-CTX-0007`) | `ContextInstanceScenarioTest.checkCannotReadPersistedInstanceContext` |
| The kernel keeps an entity read-scope window in the blueprint-local frame; the host maps it into the world frame (D4, §3.2) | `EntityWindowScenarioTest.sitedEntityWindowIsPublishedInTheLocalFrame` |
| A cell outside every bay is ignored unless the blueprint asks for `envelope`; with `"envelope": true` the bounds are closed (a non-air occupant inside is `SC-EXP-0006`) and only cells outside `bounds` stay invisible | Without the flag: `Ae2CpuScenarioTest.anExtraBlockJustOutsideTheBayIsAdmitted`, `GtEbfScenarioTest.anExtraHatchOutsideTheBayIsNotEvidence`, `PncRefineryScenarioTest.aFifthOutputOutsideTheBayIsAdmitted`, `TerraPlateScenarioTest.anExtraBlockAboveThePlateIsIgnored`. With the flag: `EnvelopeScenarioTest.fusionTorusEnvelopeIsCleanAtAHalfTurn`, `EnvelopeScenarioTest.aCrystalInTheVoidFailsAtItsCell`, `EnvelopeScenarioTest.envelopeOnlyBlueprintIsAdmittedWithNoRows` |
| Per-cell checks cannot express "exactly one controller" or a mixed coil count; a host `Computation`/aggregate must | `PncRefineryScenarioTest.twoControllersAreAdmittedByPerCellChecks`, `PncRefineryScenarioTest.aHostCountRejectsTwoControllers`, `GtEbfScenarioTest.mixedCoilCountNeedsAHostAggregate` |
| A seam is inert: it declares a connectable edge, never feeds the engine and never owns a cell | `SeamScenarioTest.aDeclaredSeamOverAirPassesAndDoesNotChangeTheResult` |

**DoorFocus coverage fixed.** `DoorFocus.staticCells` now answers the two cells it actually reads
(`origin` and `origin + (0,1,0)`) instead of the whole bay, so a two-cell door satisfies
`Coverage.everyCell` and a three-cell `everyCell` bay fails the build with undecided cells instead of
being silently covered (`DoorCoverageScenarioTest`, `110e797`).

### 3.2 Stress batch `kernel-stress-batch-2`

The second characterization batch, tagged `kernel-stress-batch-2` and committed at `3e60ed4`: 4 kernel
scenario classes (19 tests) in `kernelTest/.../engine` and 3 adapter unit classes (11 tests) in
`src/test/.../mc`. The one code change is the `NestedFocus` fix below; the suite stays
characterization, not a new kernel promise.

| Suite | Class | Shape it drives | Tests |
| --- | --- | --- | --- |
| kernel | `NestedCoverageScenarioTest` | a nested focus binds no subject; `oneSubject + Bound` at build; `anySubject + Bound` with a non-claimable custom focus at build; a cut child; a child-frame occupant | 4 |
| kernel | `ClaimStressScenarioTest` | shared/exclusive walls, per-owner release, whole-key settle replacement, duplicate rows | 5 |
| kernel | `ContextAggregateScenarioTest` | a custom aggregate, a missing aggregate result, an instance requirement without a producer, a derivation output, an uncontributed key staying absent, a contributed zero | 6 |
| kernel | `GateBudgetScenarioTest` | stale lease at check, mid-attempt invalidation, invalidation before settle, a tiny budget | 4 |
| adapter | `EntityWindowPolicyTest` | the blueprint-local entity window mapped into `CommandSupport.policy` through the siting (identity, nonzero and a dropped unmappable window), and a set's root/satellite windows mapped by their own sitings | 2 |
| kernel | `StructureManagerTest` | begin/commit/abort, draft rejection, formed replacement, moved/broken/loaded/unloaded, bounds lookup and post-commit publish | 7 |
| kernel | `ClaimLedgerTest` | stale CAS, conflict, whole-instance replacement and release, revision bumps, duplicate-row collapse | 12 |

**Characterized gaps** — documented limitations, each with its asserting test; they are not owed work
and not a pending list:

| Gap | Asserted by |
| --- | --- |
| Two `shared` bays over one cell write duplicate claim rows | `ClaimStressScenarioTest.duplicateRowsAreCharacterized` |

**D4 fixed.** The host maps every blueprint-local entity window through its siting before it builds
`SnapshotPolicy`: `CommandSupport.policy(blueprint, siting)` maps one blueprint's windows,
`SetCommand.policy(plan)` maps the root window by the root siting and every satellite window by its
own composed siting, and a window whose cells leave the lattice under the siting is dropped without a
new finding. The kernel keeps `ReadScope.windows` blueprint-local, so
`EntityWindowScenarioTest.sitedEntityWindowIsPublishedInTheLocalFrame` remains a kernel
characterization. Asserted by `EntityWindowPolicyTest.policyMapsTheLocalEntityWindowThroughTheSiting`
and `EntityWindowPolicyTest.aSetPolicyMapsRootAndSatelliteWindowsThroughTheirOwnSitings`.

**NestedFocus and claimability fixed.** `NestedFocus.bindsSubjects()` is false, so a nested focus
never backs a `Coverage.oneSubject` bay: `oneSubject + Bound + nested` fails the build with
`SC-BPT-0005` instead of building a blueprint that can never admit (`NestedCoverageScenarioTest`,
`3e60ed4`; `structure` §4.4). D-37 adds `Focus.bindsClaimableSubject()` (default false) and makes a
claiming bay reject a subject-binding focus that is not claimable, so `anySubject + Bound` with the
custom nested focus now fails at build with `SC-BPT-0005`
(`NestedCoverageScenarioTest.nestedAnySubjectBoundBodyFailsBuild`). The runtime `SC-CLM-0004` path
stays covered by `ReadModelTest` and `NestedCoverageScenarioTest.aChildFrameOccupantIsRejectedAtCheck`.

**NOTAIR and fluid leaves (D-39).** `Expectations.empty()` matches air or a vanilla fluid block and
`Expectations.nonFluid()` matches every non-fluid occupant. `NOTAIR` is `not(air())`, so water is a
non-air occupant and is admitted; `BloodAltarScenarioTest.waterInANotAirSlotIsAdmitted` asserts that.
`BloodAltarScenarioTest.emptyAndNonFluidClassifyAirWaterLavaAndStone` covers air/water/lava/stone for
both explicit leaves. The kernel classifies by the vanilla block type; a waterlogged block is not a
fluid occupant at this layer. `anyOccupant` is unchanged.

**Release granularity and unload (D-38, D-45).** `ClaimLedger.release(instance)` deletes every row of
the instance; there is no per-bay or per-row release. A shared cell's two rows are still one
lifecycle object. Removal (`Broken`/`Invalidated`), a piston move — a move is a removal (D-45); the
adapter invalidates the instance and releases its rows, and a rebuild is a fresh settle — and a
structure-set rollback all release the whole instance. A world unload drops the manager instances,
the drafts and the claim ledger together (D-44), so no row outlives its instance.
`StructureLifecycleAdversarialGameTest` asserts that split; `ClaimLedgerTest` characterizes the
whole-instance removal and the duplicate-row collapse.

### 3.3 Structure-set stress batch (SS4)

The structure-set characterization suite, added in SS4: six adapter classes and 34 tests in
`src/test/.../mc`, over the same in-memory `WorldView`, `ClaimLedger` and `ContextStore` as §3 and
with no GameTest. SS4 added no kernel type, no code, no kernel scenario obligation and no decision;
S6 then closes the two characterized gaps below with `SC-SET-0008`, `StructureEvent.Broken` rollback
invalidation and one shared set budget (D-35). The suite drives the adapter's `StructureSet.place` and `StructureSetHost`
against realistic member shapes and records every limitation it meets.

| Class | Shape it drives | Tests |
| --- | --- | --- |
| `ImprovedBlastFurnaceSetStressTest` | IE improved blast furnace: 3×3×3 casing shell, two optional preheaters, a wide hot-blast main with three face pairs, one/both optional absent, required absence, claims disjoint | 5 |
| `TepReactorSetStressTest` | TEP reactor: several solar generators range-fan-in to one controller cell, chebyshev/manhattan/euclidean, optional absence, out-of-range failure, an absent optional link still validated, euclidean squared boundary | 6 |
| `LogicalSubnetworkSetStressTest` | AE2 logical subnetwork: no links / `mode: none`, required and optional presence, partial presence, unreadable presence, entity is not presence, shared set budget (presence + root + satellite), a one-cell budget that cannot cover presence and an attempt | 9 |
| `PortEnvelopeSetStressTest` | controller/port root: the parent cell is a declared bay/check, a satellite outside the envelope, a satellite inside it (`SC-EXP-0006`), an envelope-read parent without a bay, a bay cell no check reads (`SC-SET-0003`) | 4 |
| `StructureSetRotationStressTest` | root identity/clockwise/half/counterclockwise, relative half alignment, composed orientation `root ∘ relative`, wide main under a half turn, exact world mapping | 4 |
| `StructureSetRollbackStressTest` | root and satellite check failure, settle-failure reverse release, failed release `SC-SET-0007`, ledger-wide claim filtering with foreign rows, required-last declaration order, every formed context removed after rollback | 6 |

**S6 rules** — the shared budget, context invalidation and same-siting rule, each with its asserting
tests:

| Rule | Asserted by |
| --- | --- |
| Presence charges one cell per read and each attempt sees the set budget minus presence and the earlier admitted spends; a budget-exhausted attempt stops the set | `LogicalSubnetworkSetStressTest.presenceAndEveryStructureAttemptShareOneBudgetCeiling`, `aOneCellBudgetCannotCoverPresenceAndAnAttempt` |
| A rollback dispatches `StructureEvent.Broken` for every formed attempt, including a failed-release root; a dispatch `RuntimeException` is `SC-SET-0007` and does not stop the other releases | `StructureSetRollbackStressTest.aSettleFailureReleasesInReverseOrder`, `aFailedReleaseReportsScSet0007AndKeepsTheRest`; `StructureSetHostTest.aFailedContextRemovalIsScSet0007AndKeepsTheOtherRemovals`, `rollbackRemovesTheKernelContextOfEveryFormedAttempt` |
| A satellite siting equal to the root siting is rejected in the compiler, in `StructureSet.place` and defensively by the host; an origin satellite with a non-identity orientation is allowed | `StructureSetCompilerTest.aSatellitePlacedExactlyOnTheRootIsRejected`, `aSatelliteAtTheRootOriginWithANonIdentityOrientationIsAllowed`; `StructureSetPlanTest.aHandBuiltSatelliteOnTheRootSitingIsRejected`, `aSatelliteAtTheRootOriginWithANonIdentityOrientationIsAllowed`; `StructureSetHostTest.aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence` |

**Boundaries, not gaps.** The host re-checks link distance, coverage and now same-siting, but not the
compiler's duplicate placement/parent-face rules (09 §6.4, §9); a hand-built `CompiledStructureSet` is
otherwise assumed to have passed the compiler. S5 adds the production command and its one capture
(adapter §13.5) and two real-level GameTest classes (§3.5), so `StructureSetHost` is no longer
exercised only as a library entry point by the adapter unit suite.

### 3.4 Host-data stress group (H4)

One adapter unit group, `HostDataScenarioTest` (4 tests in `src/test/.../mc`), drives the typed
snapshot through a realistic layout: a structure set whose root siting is clockwise and whose
satellite is placed at `half`, a nested child frame with its own snapshot, a waterlogged slab and a
snapshot with no captured host values. It adds no kernel type and no GameTest.

| Test | What it drives |
| --- | --- |
| `aRotatedStructureSetFormsFromTheCapturedHostSnapshot` | presence, check and settle of a root and a half-turned satellite; `property`, exact `state`, block `tag`, fluid `tag` and `fluid` checks read the captured world cells; the rotated stair state is the world-frame value |
| `aWaterloggedSlabIsABlockStateAndNotTheRealFluid` | `property("waterlogged","true")`, the exact state and the fluid tag match; the composed `fluid` mismatches `SC-MC-0009`; the composed `all_of` meta is `SC-EXP-0001` |
| `aNestedChildFrameServesItsOwnHostSnapshot` | frame/depth/parent/origin; the child's property/state/tag/fluid reads come from the child snapshot, not the root frame |
| `aSetWithoutACapturedHostSnapshotIsUnreadable` | the same set over a host-less `LevelSnapshot`: no settle, `SC-MC-0010` findings and no mismatch code |

**Characterized behaviour** (documented, each with its asserting test):

| Behaviour | Asserted by |
| --- | --- |
| A rotated siting compares the captured world-frame value; the adapter projects no definition-frame state (D-29 superseded by D-33) | `aRotatedStructureSetFormsFromTheCapturedHostSnapshot` |
| The composed `fluid` expectation's meta is the kernel junction's `SC-EXP-0001`, while its findings are the leaves' `SC-MC-0009` | `aWaterloggedSlabIsABlockStateAndNotTheRealFluid` |
| A nested child frame answers its own snapshot; a missing snapshot makes every host expectation `SC-MC-0010` (unreadable), never a mismatch | `aNestedChildFrameServesItsOwnHostSnapshot`, `aSetWithoutACapturedHostSnapshotIsUnreadable` |

### 3.5 Structure-set command and host GameTests (S5, extended by S6)

Three adapter GameTest classes (nine methods) in `src/main/java/.../mc` drive the set layer over a real
`LevelCapture`, the real in-memory ledger and `StructureManager`, and the server dispatcher. They add no kernel
type, no code and no decision.

| Test | What it drives |
| --- | --- |
| `StructureSetHostGameTest.checkAllWritesNothingThenSettleAllWritesClaims` | a two-cell stone root and an attached stone wing captured once for the union bounds; `check` admits both members, writes no row and moves no revision; `run` settles both, writes the three claimed cells and bumps the revision once per structure |
| `StructureSetHostGameTest.aFailedMemberStopsSettle` | the wing cell is dirt, so its check fails: only the root outcome is admitted, `settled`/`claims` stay empty, the revision does not move and no row is written |
| `StructureSetHostGameTest.aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence` | a hand-built `StructureSet.Satellite` whose siting equals the root siting: `check` reports `SC-SET-0008`, admits no outcome, allocates no instance and writes no claim, row or revision |
| `StructureSetHostGameTest.aOneCellSetBudgetLeavesTheRootCheckUndecided` | a real capture with `Budget.of(1, Integer.MAX_VALUE)`: presence consumes the one cell, the root check is `Undecided` with `SC-ENG-0001`, and no settle, row or revision follows |
| `SetCommandGameTest.setCheckWritesNothingAndSetSettleWritesTheClaims` | the dispatcher `set check`/`set settle` over the built-in `structurecumulus:set_demo`: both exit 1, the summary names the set, origin, root and wing, `settled: none` on check, the two rows only on settle |
| `SetCommandGameTest.aMissingRequiredSatelliteFailsCheckAndSettle` | an absent required wing is `SC-SET-0001`: both paths exit 0, report `first failure: SC-SET-0001`, write no row and move no revision |

**Characterized behaviour** (documented, each with its asserting test):

| Behaviour | Asserted by |
| --- | --- |
| One capture serves the whole set, so the capture region is the union bounding box and its element ceiling applies to the union; it is unsited and runs no orientation audit | `checkAllWritesNothingThenSettleAllWritesClaims` (the three cells are captured under one lease) |
| A failed satellite check stops the settle pass; the already-admitted root is not settled | `aFailedMemberStopsSettle` |
| A hand-built set whose satellite siting equals the root siting is rejected before presence and before any check | `aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence` |
| The set budget is shared: presence may consume it all, leaving the root check `Undecided` with `SC-ENG-0001` | `aOneCellSetBudgetLeavesTheRootCheckUndecided` |
| A set command reads the real per-structure claims ledger: check writes nothing, settle writes one row per footprint cell | `setCheckWritesNothingAndSetSettleWritesTheClaims` |
| The set command's exit code follows the set result, not a member result: an error finding is 0 even when a member was admitted | `aMissingRequiredSatelliteFailsCheckAndSettle` |

### 3.6 Adversarial close-out

The 2026-10-29 adversarial round adds four kernel classes (80 tests) and three real-level GameTest
classes (30 methods), and extends the manager lifecycle and claim/coverage edges. It adds no kernel
type, no new code and no decision.

| Suite | Class | What it drives | Tests |
| --- | --- | --- | --- |
| kernel | `AdversarialManagerTest` | one-shot close/remove, attempt-remove close, per-world `Loaded` validation and identity, world-scoped draft keys, event rejection, bounds lookup, context merge/validation and closed-attempt writes | 41 |
| kernel | `AdversarialClaimCoverageTest` | coverage/claimability/footprint matrix, anySubject/Bound, NOTAIR/empty/non-fluid and nested-subject rejection | 18 |
| kernel | `AdversarialExpectationTest` | expectation combinators, absent/refused/trait states and typed host values | 13 |
| kernel | `AdversarialFuzzTest` | deterministic small-model manager/claim/coverage sequences | 8 |
| adapter | `CaptureAdversarialGameTest` | world-frame capture, over-cap boundary, lease invalidation, build-height child refusal, nested origin/provider/cut and manager bounds | 13 |
| adapter | `StructureLifecycleAdversarialGameTest` | overlapping break, explosion, placement, same-siting settle replacement, piston full move/partial/destroy/conflict, level unload keeps claims, rejected move | 12 |
| adapter | `StructureSetAdversarialGameTest` | check-only leaves no state, re-settle replacement, shared budget stop, duplicate placement, six-satellite set | 5 |

**Corrected lifecycle (D-38/D-40, D-44/D-45).** The lifecycle wording is now:

- `Broken`/`Invalidated` release the whole instance's rows (D-45: the key is the instance); a piston
  move is a removal, not a translation — the funnel observes the moving write as ordinary block
  writes, the instance is invalidated and its rows released, and a rebuild is a fresh settle. A claim
  row whose instance the manager does not know survives a moving write untouched.
  `StructureLifecycleAdversarialGameTest.movingWriteBreaksTheStructureAndReleasesEveryClaimRow`,
  `movingWriteKeepsAnUnformedIncumbentClaimRow`,
  `pushIntoAnOccupiedCellInvalidatesBothInstances`,
  `breakOverlappingStructuresReleasesEveryClaimOnce`,
  `breakReleasesClaimRowsOutsideTheBoundsToo`.
- A level unload dispatches `Unloaded(world)`, drops that world's in-memory manager instances and
  drafts, and retires the level's ledger, so its claim rows go with them (D-44).
  `StructureLifecycleAdversarialGameTest.levelUnloadDropsInstancesAndTheirClaimRows`.
- `StructureManager` keys are `(world, owner, siting)`: the same tuple in two worlds coexists;
  `find(World, owner, siting)` is the unambiguous lookup and the two-argument `find` throws on an
  ambiguous tuple. `Loaded(World, List)` replaces only that world, validates world membership and
  duplicates before mutating, closes that world's drafts, and raises the serial watermark;
  `AdversarialManagerTest.theSameOwnerAndSitingInTwoWorldsMustCoexist`,
  `aDraftSitingReservationIsPerWorld`, `loadedKeepsInstancesOfWorldsItDoesNotRestore`,
  `loadedRejectsAnotherWorldsRestoredInstanceWithoutMutating`,
  `aRejectedLoadedDuplicateIdMustNotDestroyTheCurrentRegistry`.
- `ContextStore.remove` on an attempt closes it: reservation released, old context returned, later
  writes/commit/abort throw, and no instance forms.
  `AdversarialManagerTest.attemptRemoveClosesTheDraftReleasesTheSitingAndFormsNothing`,
  `removingADraftIdInvalidatesTheAttempt`, `closedAttemptsCannotWriteThroughTheDraftIntoTheRegistry`.

**Nested source split (design 13).** The approved nested-extent seam no longer models only
capabilities. `CapabilityNestedExtentAccess<H, C>` passes the capability's own `C` (no new
read-context record), `AttachmentNestedExtentProvider<H, T>` adds the attachment source with
`readOnly`/`readWrite` factories, and `NestedExtentLookup` exposes explicit
`getCapability`/`getAttachment` plus the capability-first `get` used by capture. Capture is
read-only: it calls `getExistingDataOrNull` and never creates/writes an attachment. Covered by
`NestedExtentTest` and the nested/capture GameTests (`NestedCaptureGameTest`,
`CaptureAdversarialGameTest`); the full design is `docs/design/13-nested-extent-access.md`.

### 3.7 Expanded-instance wave

The expanded wave adds six suites (56 tests) over the N=10..100 shapes: kernel
`ExpandedManagerTest` (12), `ExpandedManagerFuzzTest` (5), `ExpandedClaimMatrixTest` (12); adapter
`ExpandedLifecycleTest` (14), `ExpandedSetTest` (9), `ExpandedInstanceFuzzTest` (4). It drives
N=100 overlapping instances, 80/100-row claim matrices, 64-member sets, 32-member rollback and
repeated re-settle. The wave's one production fix (P1b) removed the stale-row leak on a piston move: the moving write is
observed by the funnel, the instance is broken or invalidated and every row of its key is released
(D-45), instead of leaving the stale instance and claims behind; the coverage now lives in
`movingWriteBreaksTheStructureAndReleasesEveryClaimRow` and
`movingWriteKeepsAnUnformedIncumbentClaimRow` (§3.6). Total GameTests are 140/140.

| Suite | Class | What it drives | Tests |
| --- | --- | --- | --- |
| kernel | `ExpandedManagerTest` | 100 overlapping instances, half-open `findAt`/`findIn`, replacement/move/broken/invalidated at scale, per-world `Loaded`/`Unloaded`, serial, contexts and draft reservations | 12 |
| kernel | `ExpandedManagerFuzzTest` | world-scoped registry scripts, many overlapping instances, context isolation, load/unload draft closing, random event sequences | 5 |
| kernel | `ExpandedClaimMatrixTest` | 80/100-row claim ordering, duplicate canonicalization, planner Box/Bound/Declared/None, siting translation, revisions and conflicts | 12 |
| adapter | `ExpandedLifecycleTest` | overlapping break, one break/explosion fan-out, piston move/conflict, rejected moves, multi-world load/unload, duplicate events, claim release | 14 |
| adapter | `ExpandedSetTest` | 64-member set, exact shared budget, 32-member rollback, release failure, failed/successful re-settle, competing owners and repeated sitings | 9 |
| adapter | `ExpandedInstanceFuzzTest` | random claim/context/lifecycle scripts across worlds, exclusive conflicts, moves, unload/load and claim invariants | 4 |

### 3.8 Host laws on a real server (D-76)

The behaviours a host must satisfy are asserted in `src/test` against a real server level, never
against the `TestWorld` double. `HostLawsTest` boots one dedicated server per class run — a flat,
offline, monster-free overworld in a throwaway directory, driven through the same chain as
`net.minecraft.server.Main` by the `HostServer` support class — and every case runs on the server
thread over its own loaded region, which it clears before it ends. Nested-extent sources use the
same fake-provider seam the nested GameTests use (`NestedExtentLookup.builder()`); everything else —
block writes, entity joins, the per-level claim ledger, lease retirement through the observation
funnel — is the shipped host. The suite needs no new source set and no dependency from `test` on
`kernelTest` output.

| Law | Asserted by |
| --- | --- |
| Capture honours the read policy: without the `entity` kind no entity body is captured, with it the body carries the live entity type, and blocks answer to no kind gate | `aPolicyWithoutTheEntityKindLeavesEntitiesOutOfTheSnapshot` |
| A refused cell reads `Unreadable` and answers no host value — never air (D-80) | `aRefusedCellReadsUnreadableAndAnswersNullNeverAir` |
| A captured nested body opens its child frame and stays in the parent's occupants; a cut body opens no child, stays in the parent and carries one `SC-MC-0003` entry naming the reason (D-65) | `aCapturedNestedBodyHasAChildAndACutBodyCarriesAFinding` |
| `Settled.rows` equals the requested rows of that key, and another key's rows never leak into an answer (`claim` §4.5) | `settleAnswersWithThisKeysRows` |
| A conflicting settle answers `Conflict` with the incumbent rows only — the finding is the engine's (D-53) — and writes nothing (`claim` §4.6) | `aConflictCarriesItsIncumbentsAndWritesNothing` |
| One real write inside the capture retires the lease (`isCurrent()` false) | `aWriteInsideTheCaptureRetiresTheLease` |

The read-policy law is pinned by mutation: commenting out the entity-kind gates in `LevelCapture`
turns it red. The conflict law asserts data only (D-53); the finding half is covered by the booted
host's check path (`CheckClaimsGameTest`: a real `check` over the shipped ledger answers
`SC-CLM-0001`) and by the kernel-level equivalence of the two engine entries
(`theSettleConflictRendersLikeThePreCheck`). The doubles answer like the host — `kernelTest`'s `TestWorld.View` filters occupants by
the capture policy's kinds (blocks unconditionally, bodies by their kind), and the src/test support
ledger answers settle with the requested rows — but they stay scenario doubles; no law test runs
against a double.

## 4. Module tests

| Module | Must test |
| --- | --- |
| `core` | id grammar, catalog freeze, Value content equality, budget, key equality |
| `space` | packing bijection, cuboid edges, CuboidSet semantic-equality laws, content order independent of the decomposition (`firstCell` is the smallest cell), 48 orientations, mirror `1-`, placement laws, section frame, locus frames |
| `finding` | code parse, `Codes.all` bijection, builder validation, bundle dedup/order/cap, renderers, source text |
| `world` | total read vs bounds/air, partial/refused section, body identity and nullable order, trait four states and id lookup, `SnapshotPolicy.traits` ids, root/child origin and local bounds, `statusOf` derivation, window, capture-report payload and its compatibility constructors, the typed `get` default and the expectation absent code (`HostExpectationTest`) |
| `claim` | matrix four pairs, ledger CAS/release, canonical order, footprint resolution, nested/non-root subject rejection (`SC-CLM-0004`) |
| `structure` | bay/coverage rules, reply combinators, focus kinds, nested focus (anchor-only, no subject, cut), four-state trait expectations, build table, `Blueprint.reads` union, plan DAG, anchors, partial-slice computations, derived envelope (empty no-op, reserved id, idempotent build) |
| `context` | context view, publish merge/revision, remove and empty-after-remove, derive order, merges |
| `document` | schema validation, placement/alias, key types, compile parity, determinism, trait/read-scope parity, root-only envelope and definition rejection, structure-set schema/compile/link/presence/duplicate rules |
| `engine` | §3 scenarios and the §3.1/§3.2 stress batches, gate, budget, fold, ledger retry, partial-section `Missing`, nested-claim rejection, envelope strict air, unreadable and budget paths |
| `remedy` | provider order/cap, edit/overlay values |
| `spi` | builtins, host empty, facade paths, two-kernel isolation |

The §3.3 structure-set suite and the §3.4 host-data group are adapter-level (`src/test`), not kernel
modules; they are listed in their sections and in `adapter.md` §13/§3.2, not in this table.

## 5. Code producers

The authoritative table is `finding.md` §6: one row per code with its severity, label policy
(`P`/`–`/`T`), meaning, owning module and every emit site (D-69). `Codes.all` has 66 entries in this
revision, matching the registry. A test reads `Codes.all` and asserts:

- every entry has a non-blank summary and body;
- every code appears exactly once in the producer table (a hand-maintained mirror in `CodesTest`
  carrying code, severity, label policy and owning module, checked against `Codes.all`; the
  `SC-MC-*` rows are excluded from the mirror);
- the label policy is one of `P`, `–`, `T`, the owning module is a kernel module name or `adapter`,
  and `T` appears only on `SC-CHK-0001` and `SC-SPI-0003`;
- no producer row names a code absent from `Codes.all`;
- `SC-MC-*` are absent from `Codes.all` (adapter-only), including the capture-report codes
  `SC-MC-0003`/`0004`/`0005` that M5e ships (D-28); the adapter registers them in its own code
  catalog, which is not the kernel registry;
- a relayed host code is not allocated by the kernel, and a relayed code already in `Codes.all` is
  replaced by `SC-OCC-0004` with a note — the same guard covers a `CaptureReport` entry.

`tools/spec-check.py` parses the same table and the `Codes.java` declarations, so a missing row, an
extra row, a severity disagreement or an unreadable label policy fails the spec index without a
build; it also reports a registered code that no kernel or adapter source references (an unproduced
code).

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
16. Can a satellite share the root siting? (compiler, `place` and host: `SC-SET-0008`)
17. Can a rolled-back structure keep a context? (host rollback: dispatch `StructureEvent.Broken` for every
    formed attempt, even a failed release)
18. Can a set read more cells than its budget? (host: one set budget; presence `(1, 0)` and admitted
    spend are subtracted)
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
22. Can a set settle a structure after presence, link or check has failed? (host: presence and links
    run before any check, any error returns before the first settle, and a failed check stops the
    remaining checks)
23. Can a rolled-back structure keep claim rows, or can one structure's view leak a foreign owner's
    rows? (host: release removes every row of the released instance by contract
    (D-45), and `claimsOf` filters by instance and the blueprint's own bays; §3.3)
24. Can a satellite link be considered aligned under a root rotation without composing both sitings?
    (host: the plan is `rootSiting.compose(relativeSiting)`, and both endpoints are mapped through
    their own sitings; §3.3)

## 7. Performance

The kernel's cost is a JMH number, produced on a development machine and in CI (D-73). There is no
counting layer, no complexity bookkeeping and no threshold: a benchmark reports throughput and
latency for one operation, and the numbers are compared by a person looking at two runs.

- Run: `./gradlew jmh`. The `src/jmh` source set runs with `@Fork(2)`, `@Warmup(3)` and
  `@Measurement(5)` in both `Throughput` and `AverageTime` modes; `-PjmhArgs` forwards options to
  `org.openjdk.jmh.Main`, for example `-PjmhArgs="ClaimPlanning -rf json -rff bench.json -foe true"`
  (JMH creates only the result file, never its directory).
- CI is deferred until the repository has a remote; when one exists it runs the same `./gradlew jmh`
  and publishes the JSON result as a build artifact, and no workflow file is kept until then.
  `check` only compiles the source set: a shared runner's numbers are for comparison, not for
  gating, so no benchmark ever runs inside `check`.
- A number worth keeping is copied to `Data/StructureCumulus/bench/<date>-<benchmark>.json`;
  `07-regeneration.md` §7 records only the most recent conclusion.
- Capture against a live level and anything that needs a server tick is not benchmarked at all; a
  change that touches them is reviewed by reading the code.

## 8. What is not verified in this round

- The rest of the NeoForge adapter and its GameTests. The tree registers 140 `@GameTest` methods; `./gradlew check` and `runGameTestServer` are green on this tree (541 kernel / 65 containers, 440 adapter / 46 containers, 140/140 GameTests; check exit 0). The registered set covers M1 capture/host, M2a entity precedence,
  M2b orientation, M3 claims, the M2c trait slots with the T1–T3 direct capability handles
  (`TraitCaptureGameTest`), M2d nested frames/cuts, capture-revision lease invalidation
  (`LeaseLifecycleGameTest`), the watched-region mark counter (`LeaseMarkCounterGameTest`), the
  rotated-siting two-phase capture
  (`RotatedSitingGameTest`), the M4 command surface (`SettleCommandGameTest`, `CommandGameTest`), the
  M5e capture report (`CaptureAuditGameTest`, `CommandCaptureReportGameTest`, `NestedCaptureGameTest`),
  the claims read in check (`CheckClaimsGameTest`), the retained orientation audit
  (`OrientationAuditGameTest`: an upright siting reports no `SC-MC-0004`, an unrepresentable state
  reports one grouped entry, and a child frame is not audited), and the set layer
  (`StructureSetHostGameTest`, `SetCommandGameTest`: §3.5). Unit evidence: `HostExpectationTest`
  for the typed bridge and absent codes, `SnapshotLevelTest` for the copied states/fluids/entities,
  `LevelSnapshotTest` for
  body/precedence/partial reads, `TraitLookupTest` for the `(trait, holder)` registry, the duplicate
  pair, the same trait on two holders, known-without-provider absence, unknown denial and the
  throw/null/wrong-id refusals, `CapabilityTraitProviderTest` for the one generic capability provider
  and the direct value identity, `OccupantTraitsTest` for the three direct capability keys and the
  absent NBT traits, `NestedExtentTest` for
  the capability declaration, the typed holder ids, the per-holder lookup and its duplicate
  rejection, and the capability provider delegation, `StructureManagerTest` for the manager,
  `OrientationMappingTest` for the orientation mapping, `DeterminismTest` for canonical bytes, the
  parse/write fixed point and equal compile runs, `ServerStateDocumentSourceTest`/
  `CommandSupportTest`/`CommandRouterTest` for the M4 sources and command plumbing, `LevelWatchTest`
  for the mark counter's refcounts and lazy fingerprints, `CaptureReportsTest` for the report
  site, the batch-2 policy unit `EntityWindowPolicyTest` (D4 fixed host mapping), the kernel's
  `ClaimLedgerTest` (CAS, conflict, whole-instance release, duplicate-row collapse; claims are
  session-scoped and in-memory since D-44), and the
  structure-set units `StructureSetPlanTest`, `StructureSetHostTest` (including S5's
  two check-only cases), `DatapackStructureSetTest`, the six-class SS4 stress suite (§3.3),
  `HostDataScenarioTest` (§3.4) and the S5 GameTest group (§3.5).
  **What remains:** acting on the measured kernel-side cost debts and the live-level review items
  (`07` §7), and fresh visual review screenshots.
  There is no regeneration milestone.
- The scheduler, dirty index and per-tick window diff; host-side, out of v1.
- Rendering of overlays; the kernel produces data only.
- A real JSON parser; the kernel ships `Value` and the ports; a test writer/reader is test-only.
