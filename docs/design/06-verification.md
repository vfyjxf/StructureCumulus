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

Current evidence (2026-10-28, HEAD `dcd5de4` plus the H4 working tree): M1, M2a, M2b, M2c, M2d and
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
closes the set layer (D-35): `SC-SET-0008` same-siting rejection, `ContextStore.remove` with rollback
invalidation, and one set budget shared by presence and every member attempt (§3.3). On the S6
working tree a full `./gradlew check` and `runGameTestServer` both pass: 269 kernel tests (40
containers), 280 adapter unit tests (35 containers), 74/74 `@GameTest` methods, 0 failures, 0 skipped.
`tools/spec-check.py` is clean.
`Codes.all` has 65 entries and the decision record has 40 decisions (D-40). Model C, body identity,
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
case). S6 also adds `ContextTest` (4); D-40 adds `StructureManagerTest` (7) and `StructureLifecycleGameTest` (2). S5 adds the two
real-level GameTest classes below (§3.5). The host-data round adds
`HostExpectationTest` (7 kernel tests), `McAnchorsTest` (7), `SnapshotLevelTest` (7),
`LevelSnapshotTest` (17), `AdapterExpectationsTest` (12) and the H4 `HostDataScenarioTest` (4, §3.4).
Their focused runs are green: `compileKernelTestJava`, `compileTestJava`, `kernelTest` selections and
`test` selections; no full-suite result is claimed for any of them.

**What remains.** Two items only: the performance/cost pass over the block-entity-lazy and entity
sweep, and fresh visual review screenshots. There is no regeneration milestone. The former
siting-aware `property` gap is now the typed host snapshot (D-33): a value check reads the captured
snapshot, so the kernel needs no property descriptor and the adapter projects nothing. A state that
cannot express the pose is still `SC-MC-0004` in the capture report; a missing value is
`SC-MC-0010`, never a mismatch. The snapshot adds no kernel type beyond `HostType` and
`WorldView.get`, so the spec index is unaffected.

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
`TechFixtures` shape builders. H1 removed `AnchorValueScenarioTest` together with the kernel anchor
resolver, so the current tree has 11 classes and 41 tests; `McAnchorsTest` covers the captured anchor,
the declared value and the missing-state code. It runs with the same in-memory `WorldView`/`ClaimLedger`/`ContextStore`
as §3 and adds no type, no decision and no kernel scenario obligation; it is a **characterization**
suite, and each gap below is asserted rather than hidden.

| Class | Shape it drives | Tests |
| --- | --- | --- |
| `Ae2CpuScenarioTest` | AE2 crafting CPU: 2×2×2 solid, missing storage cell, outside block, lattice edge, exclusive conflict | 5 |
| `AstralAltarScenarioTest` | Astral Altar tier II (54 cells) and tier III (72 cells) grown in place | 4 |
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
scenario classes (17 tests) in `kernelTest/.../engine` and 3 adapter unit classes (11 tests) in
`src/test/.../mc`. The one code change is the `NestedFocus` fix below; the suite stays
characterization, not a new kernel promise.

| Suite | Class | Shape it drives | Tests |
| --- | --- | --- | --- |
| kernel | `NestedCoverageScenarioTest` | a nested focus binds no subject; `oneSubject + Bound` at build; `anySubject + Bound` with a non-claimable custom focus at build; a cut child; a child-frame occupant | 4 |
| kernel | `ClaimStressScenarioTest` | shared/exclusive walls, per-owner release, whole-key settle replacement, duplicate rows | 5 |
| kernel | `ContextAggregateScenarioTest` | a custom aggregate, a missing aggregate result, `whenAbsent` on a check-only key, a derivation output | 4 |
| kernel | `GateBudgetScenarioTest` | stale lease at check, mid-attempt invalidation, invalidation before settle, a tiny budget | 4 |
| adapter | `EntityWindowPolicyTest` | the blueprint-local entity window mapped into `CommandSupport.policy` through the siting (identity, nonzero and a dropped unmappable window), and a set's root/satellite windows mapped by their own sitings | 2 |
| kernel | `StructureManagerTest` | begin/commit/abort, draft rejection, formed replacement, moved/broken/loaded/unloaded, bounds lookup and post-commit publish | 7 |
| adapter | `SavedClaimsLifecycleTest` | stale CAS, conflict bytes, whole-key release, duplicate-row canonical collapse | 4 |

**Characterized gaps** — documented limitations, each with its asserting test; they are not owed work
and not a pending list:

| Gap | Asserted by |
| --- | --- |
| Two `shared` bays over one cell write duplicate claim rows | `ClaimStressScenarioTest.duplicateRowsAreCharacterized` |
| `whenAbsent` is a dead path for a check-only key: it needs a section computation, so a check cannot reach it | `ContextAggregateScenarioTest.whenAbsentIsADeadPathForACheckOnlyKey` |

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

**Release granularity (D-38).** `ClaimLedger.release(owner, siting)` deletes every row of the
`(owner, siting)` key; there is no per-bay or per-row release. One siting is one instance, and a
shared cell's two rows are still one lifecycle object. `SavedClaimsLifecycleTest` characterizes the
whole-key removal and the duplicate-row collapse on reload; this is intentional, not a gap.

### 3.3 Structure-set stress batch (SS4)

The structure-set characterization suite, added in SS4: six adapter classes and 34 tests in
`src/test/.../mc`, over the same in-memory `WorldView`, `ClaimLedger` and `ContextStore` as §3 and
with no GameTest. SS4 added no kernel type, no code, no kernel scenario obligation and no decision;
S6 then closes the two characterized gaps below with `SC-SET-0008`, `ContextStore.remove` and one
shared set budget (D-35). The suite drives the adapter's `StructureSet.place` and `StructureSetHost`
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
| A rollback removes the `StructureContext` of every formed attempt, including a failed-release root; a removal `RuntimeException` is `SC-SET-0007` and does not stop the other releases | `StructureSetRollbackStressTest.aSettleFailureReleasesInReverseOrder`, `aFailedReleaseReportsScSet0007AndKeepsTheRest`; `StructureSetHostTest.aFailedContextRemovalIsScSet0007AndKeepsTheOtherRemovals`, `rollbackRemovesTheKernelContextOfEveryFormedAttempt` |
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
`LevelCapture`, the real `SavedClaims`/`StructureManager` and the server dispatcher. They add no kernel
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

## 4. Module tests

| Module | Must test |
| --- | --- |
| `core` | id grammar, catalog freeze, Value content equality, budget, key equality |
| `space` | packing bijection, cuboid edges, CuboidSet normalization and semantic-equality laws, 48 orientations, mirror `1-`, placement laws, section frame, locus frames |
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

The authoritative table is `finding.md` §6. `Codes.all` has 65 entries in this revision, matching the
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
16. Can a satellite share the root siting? (compiler, `place` and host: `SC-SET-0008`)
17. Can a rolled-back structure keep a context? (host rollback: `ContextStore.remove` for every
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
    rows? (host: release removes every `(owner, siting)` row for the released structure by contract
    (D-38), and `claimsOf` filters by `(owner, siting, bay)`; §3.3)
24. Can a satellite link be considered aligned under a root rotation without composing both sitings?
    (host: the plan is `rootSiting.compose(relativeSiting)`, and both endpoints are mapped through
    their own sitings; §3.3)

## 7. What is not verified in this round

- The rest of the NeoForge adapter and its GameTests. The tree registers 74 `@GameTest` methods; the
  full `runGameTestServer` is 74/74 on the working tree. The registered set covers M1 capture/host, M2a entity precedence,
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
  rejection, and the capability provider delegation, `SavedClaimsTest` for claim persistence, `StructureManagerTest` for the manager,
  `OrientationMappingTest` for the orientation mapping, `DeterminismTest` for canonical bytes, the
  parse/write fixed point and equal compile runs, `ServerStateDocumentSourceTest`/
  `CommandSupportTest`/`CommandRouterTest` for the M4 sources and command plumbing, `LevelWatchTest`
  for the mark counter's refcounts and lazy fingerprints, `CaptureReportsTest` for the report
  site, and the batch-2 persistence/policy units `EntityWindowPolicyTest` (D4 fixed host mapping),
  `SavedClaimsLifecycleTest` (CAS, conflict, whole-key release, duplicate-row collapse), and the
  structure-set units `StructureSetPlanTest`, `StructureSetHostTest` (including S5's
  two check-only cases), `DatapackStructureSetTest`, the six-class SS4 stress suite (§3.3),
  `HostDataScenarioTest` (§3.4) and the S5 GameTest group (§3.5).
  **What remains:** the
  performance/cost pass (block-entity-lazy and entity sweep) and fresh visual review screenshots.
  There is no regeneration milestone.
- The scheduler, dirty index and per-tick window diff; host-side, out of v1.
- Rendering of overlays; the kernel produces data only.
- A real JSON parser; the kernel ships `Value` and the ports; a test writer/reader is test-only.
