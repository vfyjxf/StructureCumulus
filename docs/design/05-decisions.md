# 05 — Decisions

> Every ruling that replaced an older design. The old set's `19`, `23`, `24` and `open-questions.md`
> are historical; this file is the record. A decision names its consequence so a reviewer can reject
> the consequence without re-litigating the premise.

## 1. Process decisions

### D-01 — The design is the source; the kernel is generated

The old code, its tests and the old `docs/spec` set are reference material. The new kernel is written
from this design set, compiled, and tested; the old kernel is deleted rather than migrated. A public
signature that is not in a module file is a regeneration defect. The baseline is archived outside the
project (`Data/StructureCumulus/baseline-2026-10-26.tar.gz`).

**Consequence:** no compatibility shims, no migration ledger, no "the design is ahead of the code"
section. The acceptance gate is the regenerated kernel.

**Amendment (v1.2).** The v1.1 pending block is implemented: Model C and body identity in
`cb06aa8`/`64d04ce`, the nested capability in `f3e0338`/`d6aefc2`. The banned "design is ahead of
the code" section remains banned; a future pending block needs the same decision ID and gate record.

### D-02 — Scope of v1

Core kernel + document compiler + thin remedy SPI. The v1 round scoped the NeoForge adapter to
`modules/adapter.md` and left it unimplemented at the time; no scheduler, no dirty index, no overlay
renderer, no schematic importer, no client code. This kept the first regeneration small enough to
prove, while the ports make the adapter a later, independent exercise. Current status (2026-10-28):
M1 (host + command + capture-check), M2a (loaded-entity capture), M2b (upright orientation), M2c
(Model C, body identity, total read, four-state traits and the five adapter trait keys), M2d (nested
extents, frames, cuts, child entities) and M3 (`d4b6d82`: `SavedClaims`, `SavedContexts`, CAS
settle/release and reload codecs) are committed. M4a/M4b and the command slice (`settle` at
`4899d86`, plus `compile`, `explain`, `claims`) are implemented, and capture-revision lease
invalidation landed at `0f0bf25`. The rotated-siting two-phase capture (P1) landed at `56c632c`, the
event-edged watched-region mark counter (M5a) at `d1fa42f`, the 40 non-upright orientations (M5d) at
`ac11d0c` and the capture report (M5e, D-28, `SC-MC-0003`/`0004`/`0005`) at `28d0334`. The sited-root
descriptor projection (S6, D-29) was implemented and is superseded by the host snapshot (D-33,
H1–H4): `OccupantDescriptor` carries no properties, so there is nothing to project, and the retained
orientation audit is `OrientationAuditGameTest`. The host-data round adds `HostType`,
`WorldView.get`, `Expectations.host`, one snapshot per frame and the adapter `property`/`state`/`tag`/
`fluid` factories. Pending only: the performance/cost pass (block-entity-lazy and entity sweep) and
fresh visual review screenshots. There is no regeneration milestone.

**Consequence:** the kernel tests use an in-memory `WorldView`, `ClaimLedger` and `ContextStore`; the
adapter is implemented through H4. The spec index is green at v1.2: the M5e amendment was applied
docs-first (`world` §4.10, D-28) and its carrier landed; D-29 added no public type, and D-33 keeps
the host bridge to `HostType`, `WorldView.get` and `Expectations.host`. No pending signature is ever
documented as implemented (`06-verification.md` §1).

### D-03 — English only

The design set is English. The 25-file Chinese mirror is deleted; a Chinese quick-start may cite the
English files but is not a second source. This removes a structural drift source and halves the
review surface.

## 2. Architecture decisions

### D-04 — Eleven packages, one direction

`core → space → {finding, world} → claim → structure → context → document → engine → remedy → spi`.
`structure` holds the blueprint, bay, check, focus, expectation and computations; `context` holds the
store and derivers. There is no `base`, `occupant`, `predicate`, `blueprint`, `document` (as a
separate unit), `remedy` (as an unbuilt unit) or `finding` split by producer.

**Why:** the old `base` imported `space`/`occupant`/`context`, and `blueprint ⇄ predicate` formed a
cycle. Moving the value vocabulary to `core`, the occupant model to `world`, and the
focus/expectation/check into one `structure` package removes both cycles by construction.

**Consequence:** `03-modules.md` is the only edge table; a new edge is a design change.

### D-05 — Three phases, no stages

`Phase` is `compile`, `check`, `settle`; it labels a finding and a result. No unit declares a stage;
no request chooses one; there is no `expand(stage)`, no `ContextTier`, no `Stage.STALE`, no
`Deferred`/`RetryToken`. The check runs the whole plan; the settle writes claims; compile validates
before the world is touched.

**Why:** the old set removed stage declaration in `23` but kept `expand(S)`, `CheckPlan.stage` and
context tiers, which made "who selects the stage" unanswerable. One pass plus two entry points is the
only model that matches the code that actually ran.

### D-06 — `Bay.cells` is a `CuboidSet`

A bay may be a shell, a two-leaf door, an L-shaped room or a pipe box; all are box sets. There is no
one-cuboid constraint, no synthetic-bay lowering for a shell, no `Segment`, no `SegmentKey`, no
`Requirement`, no `BayBuilder` two-step knot and no pairwise `Overlap` declaration.

**Why:** the old document compiler had to split a holed shell into synthetic bays, while the Java API
forced a user to write 25 cell checks for a 3×3×3 shell. A `CuboidSet` is still box arithmetic (I-03)
and makes the Java and JSON forms agree.

**Consequence:** `Check` carries the `Bay` value; `bay(...)` is not a separate builder call; a
blueprint's bays are the distinct bays of its checks; two intersecting bays are legal only when both
are `shared`.

### D-07 — One key, two retentions, one publication

`ContextKey<T>` is the only key. `Retention` is `match` (attempt only) or `instance` (published).
A successful settle publishes the `instance` values once; a derive publishes one batch once. There
is no `ContextTier`, `ContextFrame`, `ATTEMPT`/`OUTCOME`/`CONTEXT`, `Augment*`, `RuntimeState` or
`ContextDraft` in the public API.

**Why:** the old model had four tiers, three retentions, frames, augments and a persisted
`RuntimeState`; the kernel actually ran one stage at a time and the tier never mattered. One
publication point is enough for `check` (read-only), `settle` (claims + values) and `derive`
(post-formation values).

### D-08 — Absence is three types, not one

`Outcome<T>` (`Found`/`Missing`) is a lookup. `FocusResult<T>` (`Taken`/`Unavailable`) is a focus.
`Reply` (`Match`/`Mismatch`/`Unreadable`/`Abstain`) is a test. `MatchResult` is an attempt. A
`Mismatch` is refutation, `Unreadable` is ignorance, `Abstain` is refusal; they never collapse.

**Why:** the old `Outcome` carried subjects and findings for every lookup, `Reply` had a `Verdict`
enum and a generic-free record, and `Bindings` added a fifth typed map. Splitting by question makes
each type honest and removes `Verdict`, `Bindings`, `BindingKey` and the `SC-EXP-0003` merge rule.

**Amendment (D-24).** A fourth absence is the refused read: `CellRead.Unreadable(ReadRefusal)`. It
is a read answer, not a fourth lookup family; it does not add an `Outcome` arm, and `Reply.Unreadable`
still carries the reported finding.

### D-09 — No `DecisionKind`, no `ReadSet`, no `ClaimPlan`

`Coverage` is the only three-value vocabulary; a focus declares `staticCells` and `bindsSubjects`;
the build compares them to the bay's coverage. `ReadScope` is the only read closure; there is no
`ReadSet`, `ChangeSet` or invalidation index. `Admitted` carries the bindings and the revision it
read; there is no `ClaimPlan` type and no row in a plan.

**Why:** each pair was a second name for one concept; keeping both forced every consumer to learn the
difference and every producer to choose. The engine derives rows, so a plan with rows is a forgery
surface.

### D-10 — The snapshot is recursive, with a frame

`WorldView` is the only view, including nested ones: same `SnapshotId`, distinct `frame`, own
`origin`/`cellSize`, `parent`/`depth`. `SectionKey` and `OccupantRef` carry the frame. There is no
`NestedView`, `NestedLevelSnapshot`, `ChunkGrid`, `ChunkKey`, `EntityKey`, `ChangeSet`, `DirtyIndex`
or `WatchedInstance` in the kernel.

**Why:** the old set had two nested types, two section types and a host dirty index inside `engine`.
A frame number makes a nested view answerable through the same interface and makes a ref from another
frame detectably foreign. Host dirty tracking is a host concern.

**Amendment (nested-capability round).** One `SnapshotId` covers every frame. The root frame is
`0`/depth `0`/parent null with `origin = (0,0,0)` and world bounds; a child has the extent origin as
its `origin`, local bounds = absolute bounds translated by `-origin`, local cells, and
`world = origin + local`. `policy()` is the same capture policy in every frame, and `maxDepth` is the
ceiling below the root. A nested body is a parent-frame occupant of kind `nested` with its footprint
translated into that parent; it is not in a `SectionSnapshot`. The adapter keeps one `LevelSnapshot`
and a package-private `FrameData`; the banned `NestedView`/`NestedLevelSnapshot` names stay banned.

### D-11 — The lease is one method

`SnapshotLease` is `id()`, `view()`, `isCurrent()`, `close()`. The kernel never closes it, never
retains it and never returns a child handle. `MatchResult` is a pure value, not `AutoCloseable`.

**Why:** the old lease had `changeCount`/`isValid`/`retain(PinBudget)` and result arms held child
handles; the kernel owned a memory-management policy it could not enforce. The host scopes the lease;
the kernel reads one boolean.

### D-12 — The engine has no cache, no ticket, no prescreen

`MatchRequest` is `(blueprint, siting, lease, instance, owner, budget, claims, cancel)`. There is no
`MatchPolicy`, `Ticket`, `InstanceRecord`, `InstanceBudget`, `PinBudget`, `PrescreenIndex`, `Census`,
`Fingerprint`, `CacheMode`, `AttemptCache`, `PlanId`, `UnitId`, `Deferred` or `RetryToken`. The check
runs the plan; the settle writes; a retry is a new call.

**Why:** these were host scheduling and performance features living in the kernel's type space,
unused by the actual pipeline. They can return as host code without changing a kernel signature.

### D-13 — Claims are per world and derived

A `ClaimLedger` handle answers for one `World`; a `ClaimRow` has no world. `ClaimMatrix.conflicts` is
the only predicate; `ClaimPlanner` is the only row factory; `Admitted` has no rows. `release` is the
third writer for lifecycle removal. `ClaimPlan`, `ClaimMode`, `ClaimScope` and `Overlap` are gone.

**Why:** a row's world was a second copy of the ledger's identity; the plan's rows were a
forgeable input; `Overlap` was a pairwise declaration the build can derive.

### D-14 — One document schema, one compiler

`Document` is one schema version (`1`), one root spec, reusable definitions, placements and
extensions. `DocumentCompiler` lowers it to `Blueprint`. There is no `COMPACT`/`CANONICAL`/`AUTO`
profile, no `DocumentUpgrade`, no `Canonicalizer`, no `SegmentLibrary`, no `SegmentEmitter`, no
`DataBridge`/`DataFrontend` pair and no `DataView`/`DataKind`; `Value` is the only tree. A
`DocumentReader`/`Writer` is the host's text port.

**Why:** the old document module had 25 types for a format that had never shipped, and two profiles
whose "lossless" claim was untested. One schema with defaults and one lowering function is smaller
and provable. A version upgrade can be added when a version has shipped.

### D-15 — The finding layer is values, not a framework

`Finding` is seven components; `FindingBundle` is a final class; `FindingRenderer` has `human` and
`json`; `Codes` is the code table; `CodeEntry` carries the canonical severity and explanation.
There is no `FindingId`, `related`, `FindingSink`, `ExplainBook`, `Stability`, SARIF renderer or
per-code emitter.

**Why:** `FindingId`/`related` and the sink were unused or duplicated the bundle; `ExplainBook` was a
second catalog; `Stability` was a documentation promise without a runtime effect. A severity
mismatch is caught by a registry test, not a `SC-DIA` code.

### D-16 — The remedy layer is values and a provider

`Remedy` is `Repair(EditPlan, Applicability, rationale)` or `Preview(OverlayModel, rationale)`;
`Edit` is `Place`/`Remove`/`Replace`/`SetTrait`; `RemedyProvider` returns candidates. There is no
`EditExecutor`, `EditTransaction`, `PlanValidator`, `ApplyReport`, `PlanDiff`, `SolveOptions` or
`RemedyKind` in the kernel.

**Why:** the old U10 was ~20 unbuilt types with no compiled test. The kernel can describe a repair
and hand it to a host; execution is a host transaction with its own report.

### D-17 — The facade is four operations, one final class

`StructureKernel` is a final class with `compile`, `check`, `settle`, `derive`; `solve`,
`explain`/`explainOrFallback` are helpers; `document`/`ledger`/`catalogs` are lookups. Anchor
resolution is host work (`McAnchors`, D-33); the facade has no `siting` operation. It is not an
interface and has no static-method-in-interface factories. `Host` is one record of ports. `Catalogs`
has six families; there is no `contextKeys` catalog, because a key is a blueprint/document value.

**Why:** the old facade had nine operations, four of which did not exist, an interface with static
factories, a `Host` with a segment library, and a key registry that forced a `context → spi` edge.
The new shape is one class a host can hold and an addon can ignore.

### D-18 — Trait and kind are open values

`OccupantKind` is a `NamespacedId` record with four constants, not a five-case enum; there is no
`OTHER`, no `compareTo` by ordinal. `OccupantDescriptor` is `(type, kind)`. **Amendment (H1):** the
property map and tag list were removed and the host payload moved to the typed snapshot (D-33,
`10-host-data.md`).

A trait has four capture states, not two. `Trait<T>` stays `(key, @Nullable value)`; `TraitSlot` is
`Present(Trait<?>)`, `Absent(id)`, `Refused(id, ReadRefusal)`, and "no slot" (unasked). Present-null
is a fact, `Absent` is a positive fact of no trait, and a missing slot is unreadable, never `Absent`
and never a `Mismatch`. A trait is identified by its id: `OccupantSnapshot` has one slot per id,
`SnapshotPolicy.traits` is a `Set<NamespacedId>` (not typed keys), and `OccupantSnapshot.find` finds by
id plus `key.type().isInstance(value)` so a JSON `TraitKey<Object>` reads a typed capture. A
present-null value satisfies presence and a value test without applying the predicate.

**Why:** an open family cannot be a closed enum; `OTHER` duplicated `type`; the old id-set model
could not express "present but valueless", which the furnace/hatch semantics need. The four states
keep a failed read distinct from a positive absence: collapsing `Refused` into `Absent` would turn a
failed capture into a mismatch, and `not(trait)` would then report a passing check over an unread
world.

### D-19 — One producer per code, one explanation

`Codes.all` is the table; every entry has a code, a canonical severity and an explanation; the
producer table in `finding.md` names one module/site per code. An unproduced code is deleted, not
marked reserved. `DESIGN` violations are findings from the compiler; a `Blueprint.build()` throw is a
programmer proof failure with the code in the message. An adapter-only code outside `Codes.all` may be
deferred (`SC-MC-0003`); it is not a kernel registry entry, so this rule does not license a dead
kernel code.

**Why:** the old set had codes with no producer, codes emitted by three modules, and a registry split
between `13` and `15`. A test asserts the table is bijective with the producers.

### D-20 — The old open questions, answered

| Old id | Ruling |
| --- | --- |
| Q1 `predicate ↔ blueprint` cycle | Removed: focus/expectation/check live in `structure`; `Bay` holds no checks. |
| Q2 stage carrier | Removed: phases are operations, not declarations (D-05). |
| Q3 view model | Section-based recursive `WorldView`, `Bay.cells` is a `CuboidSet` (D-06/D-10). |
| Q4 `ReadSet`/`ReadScope`/`ChangeSet` | One `ReadScope`; the other two are deleted. |
| Q5 `check` publication / re-emission | `check` writes nothing; `settle` publishes once; no re-emission. |
| Q6 internal overlap | Build error for any overlapping pair with an `exclusive` side; both `shared` is legal. |
| Q7 `DecisionKind`/`Coverage` | Only `Coverage`; `Focus.staticCells` replaces the decision kind. |
| Q8 `Reply`/`Bindings.merge` | Sealed four-state `Reply`; `Bindings` deleted. |
| Q9 `UndecidedReason` | Eight values: `budgetExhausted`, `cancelled`, `unreadable`, `abstained`, `noChecks`, `ledgerUnavailable`, `ledgerMoved`, `designDefect`. |
| Q10 `OccupantRef`/`SnapshotId` | `SnapshotId` in core; ref carries snapshot + frame + type + kind + anchor + opaque `body`; no footprint. |
| Q11 capture SPI | One `WorldCapture.capture(CaptureRequest)` returning `Captured(view, lease)`/`TooLarge`/`Unavailable`. |
| Q12 section phase read | `SectionComputation`/`SectionContext` has no `read(ContextKey)`; it does have a total cell `read(LatticePoint)` and `unreadable()` (D-24). `AggregateComputation` has no `contribute`. |
| Q13 step vocabulary | No step types; `Plan` has sections/aggregates/checks; a check cannot read a derivation output. |
| Q14 facade | Four operations, one final class, one `Host` (D-17; `siting` moved to the adapter, D-33). |
| Q15 code table | One table in `finding`, one producer, no dead codes (D-19). |
| Q16 `LocusMap`/`definition` | `LocusMap` maps local↔world only; a definition label is created where the source is in hand; no `toDefinition`. |
| Q18 U10 | Thin values + provider (D-16). |
| Q19 named-but-unimplemented | `DataView`/`DataKind` → `Value`; `Codes` implemented; `Checks` in core; `Stability`/`ExplainBook` deleted; no empty names. |
| Q20 trait model | Typed `Trait` with present-null (D-18). |
| Q21 descriptor | `(type, kind)`; properties/tags removed by H1 (D-33). |
| Q22 `OccupantKind.OTHER` | No `OTHER`; an open id record with four constants. |
| Q23 `ClaimMatrix`/`Overlap` | One public `conflicts(a,b)`; no `Overlap`; internal overlap is a build rule. |
| Q24 `MatchSession` | Package-private; no public members; not a resumable session. |
| Q25 nested view | Same `WorldView`, distinct frame; no separate nested type (D-10). |
| Q26 per-cell status | `WorldView.statusOf(cell)` default over `read`; `CellRead` is the read primitive, not a second status type. |

### D-21 — Documents: definitions are geometry, kinds are snake_case

A definition contributes bays, seams and placements only; checks, computations, keys, derivations,
anchors and orientation are root-only in v1. This avoids rewriting cell-bearing `Value` arguments
for a placed definition and keeps one check per bay in the root. Document kind ids are lowercase
snake_case (`all_of`, `each_cell`, `any_occupant`, `all_cells`); an unqualified kind resolves in
`structurecumulus`, a custom kind is `namespace:path`.

### D-22 — Local reads are mapped through the siting

A root `WorldView` is world-framed; a blueprint's bays, checks and computations are local. Every
`FocusContext`/`SectionContext`/`AggregateContext` cell argument is mapped by the request's `Siting`
before a view read, and every subject footprint is world-framed. `BayView`/`Focuses`/`Engine` do the
mapping once; a raw `view.occupant(local)` in a check would be a frame bug. Nested subjects
(`frame != 0`) are readable but cannot back a claim.

### D-23 — The document IR names `Footprint` and `Sharing`

A `BaySpec` carries `Footprint` and `Sharing` directly, so `document` imports `claim`; the edge is
recorded in the module table. The alternative — duplicating the two enums in `document` — would
create a second vocabulary and a lossy conversion. `document` still does not import `engine` or
`remedy`.

### D-24 — The narrowest failed read is a value (Model C)

The section-only refusal model answered too coarsely. `ReadStatus` had four values,
`SectionSlot.Refused` carried a status and a code, `WorldView.occupant(cell)` returned `null` for
every cell of a refused section, and a trait was either present or "not captured" (`Trait<T>` list,
`find == null`). A partially readable section therefore refused cells the host could answer; a
refused trait was indistinguishable from an absent one; and the consumer had to invent a code for a
read the host had already rejected.

The replacement is one total read and three narrower shapes: a per-cell `CellRead`, a per-section
`Partial`, and a per-trait `TraitSlot`.

- `ReadStatus` adds `failed` (the host tried and could not read) beside `outOfBounds`, `notCaptured`
  and `denied`; `readable` stays the only non-refusal value.
- `ReadRefusal(ReadStatus status, FindingCode code)` is non-readable by construction.
- `CellRead` is `Found(OccupantSnapshot)` or `Unreadable(ReadRefusal)`. `WorldView.read(cell)` is the
  only primitive; `occupant`, `refusalAt` and `statusOf` are derived. A cell outside `bounds()` is
  `outOfBounds`, never air; a readable empty cell is still a real air occupant.
- `SectionSlot.Partial(section, unreadable)` refuses individual cells of an otherwise readable
  section; `Refused` still refuses the whole section. `SectionSlot.readable()` is true for `Air`,
  `Present` and `Partial`.
- `TraitSlot` is `Present(trait)`, `Absent(id)` or `Refused(id, refusal)`, one slot per trait id.
  `Present(null)` is a positive fact; `Absent` is a positive fact of no trait; a missing slot is
  "unasked" and unreadable. A refused or unasked trait is `Unreadable`, never a `Mismatch`, and
  `not(trait)` never promotes it.
- `SC-OCC-0004` is the kernel's default required-read-unavailable code. A host code (for example
  `SC-MC-0002`) may travel in a `ReadRefusal` as opaque payload and is relayed at the report site;
  the kernel never branches on it, never mints it and never allocates it in `Codes.all`. A relayed
  code already in `Codes.all` is replaced by `SC-OCC-0004` plus a note.
- `SnapshotPolicy.traits` is a `Set<NamespacedId>` (trait identity is the id), not a set of typed
  keys, and its constructor copies the ids without silently emptying the set. `maxDepth` is the
  nested ceiling.
- `Blueprint.reads()` is the union of every check's `ReadScope` and every computation's region; the
  host derives `SnapshotPolicy` (`kinds`, `traits`, `depth`, `windows`) from it. `ReadScope` carries
  `(cells, keys, traits, kinds, depth, windows)`.

**The body component.** `OccupantRef` gains an opaque `@Nullable NamespacedId body` after `anchor`;
the five-argument constructor is the compatibility form and passes `null`. Equality and `compareTo`
include `body` (null first). Blocks, air and nested bodies may use `null`; the adapter gives an
entity `NamespacedId.of(entityType.namespace(), uuid.toString())`. This is a public kernel API change:
the old ref collapsed two entities of one type at one anchor into one occupant, while the new ref
keeps both.

**Why:** the read is the smallest fact the kernel can hold; anything wider forces a consumer to
reconstruct a refusal the host already knew. A trait absent is not a trait unread, and a refused cell
is not air. The body is part of identity for the same reason: an identity that omits it is not an
identity of the body.

**Consequence:** a computation that sums over a `Partial` slice must consult
`SectionContext.unreadable()` or return `Missing`; a returned `Found` over refused cells is a short
sum and a review defect. **Status (v1.2):** implemented in `cb06aa8`/`64d04ce`; the spec index is
green.

### D-25 — A nested body is checkable, never claimable

A nested extent becomes a parent-frame occupant of kind `nested` with a footprint translated into
the parent, but it is not a claim subject. `Focuses.nested(cell)` returns the child view with an
empty subject list; a check that binds a nested body through `Focuses.occupant` fails at claim
prediction with `SC-CLM-0004`, before an `Admitted` exists; `settle` revalidates the binding, and
`ClaimPlanner.derive` throws the same code if a nested or non-root subject reaches it (a proof
failure, because check and settle validate first). A nested child subject (frame `!= 0`) is invalid
for a claimable bay for the same reason. **Status (v1.2):** implemented in `cb06aa8`; the check-time
rejection is covered by `ReadModelTest.nestedSubjectIsRejectedAtCheckAndPlanner`.

**Why:** a claim row is a world cell of the root instance. A child frame's local cells are not the
parent's cells, and claiming the extent would let a child structure's internals — or a template that
moved since capture — forge the parent's ownership. What the parent owns is what it explicitly
declares or binds in its own frame.

**Consequence:** a nested structure is checkable but claimless; a host that wants the nested volume
protected claims the parent cells explicitly (a `Box`/`Declared` footprint) or makes the parent body
the binding. A nested check can still fail or pass and publish context values; it cannot write rows.
A bay whose only check is a nested focus therefore cannot be `oneSubject + Bound` (the bound union is
empty): use `everyCell + Box` to own the declared volume, or `anySubject + None` to own nothing.

### D-26 — The nested source is a capability, not a structure block

The nested source is one third-party declaration: `NestedExtent(type, absolute bounds, origin)` in
the public `dev.vfyjxf.cumulus.mc.capability` package, queried through
`NestedExtents.BLOCK`/`NestedExtents.ENTITY` (both `createVoid`, id
`structurecumulus:nested_extent`). Providers register on the MOD bus through
`RegisterCapabilitiesEvent`. There is **no built-in provider** for the vanilla Structure Block, no
`StructureBlockEntity` special case, no template read and no adapter-owned source registry. For this
capability the adapter queries only the four-argument block overload with the already-read
state/block entity, skips a block-entity state whose block entity is null, and queries an entity with
`entity.getCapability(ENTITY, null)`; one result per cell, first provider wins, block source over
entity source, no merge or conflict detection. A provider value is a declaration; the adapter owns
validation, frames, depth, parent, no-chunk reads, `(type, origin)` cycle detection, budget, refusal
and the child view.

**Why:** the vanilla structure block is an editor, not a stable composition source — reading its
template would make capture depend on a file format and a block-entity special case. A capability is
the loader's extension point: a mod that owns a nested structure declares it, and the adapter never
privileges one mod or guesses a template. It also keeps the kernel pure: the declaration reaches the
adapter, not `kernel.world`.

**Consequence:** nested views appear only when a third-party mod provides a capability; a vanilla
structure block stays metadata. A template-backed extents provider (`TemplateNestedExtent`) is a
future, separate feature. The adapter's fake-provider seam is package-private
(`LevelCapture(ServerLevel, NestedExtentLookup)`), because a real registration cannot be made after
the MOD bus event. **Status (v1.2):** implemented in `f3e0338`/`d6aefc2`; the nested query remains
the four-argument block overload, while the separate trait capability queries use the five-argument
`Void`-context form.

### D-27 — A cut child is absent, not a refused child

A child frame that cannot be built — depth `> maxDepth`, element budget, a repeated
`(type, origin)` on the open frame stack (cycle), an unloaded chunk, or a declaration that cannot be
translated into its parent frame — is **cut**: the nested body stays in the parent, no child exists,
`nested(cell)` returns `null`, and the consumer reports `SC-OCC-0003`. The adapter does not fabricate
a refused child or an empty child view. A child whose bounds leave the build height is not cut: the
child exists, its out-of-range local sections are `Refused`/`Partial` (never air), and each refused
cell is visible through the read. There is no partial child: a child either exists with its sections
answered (readable or refused) or is cut entirely. **Status (v1.2):** implemented in
`f3e0338`/`d6aefc2`. **Amendment (M5e):** the cut reason now travels as one `SC-MC-0003` entry in
the capture report (D-28), a WARNING; `SC-OCC-0003` remains the check's finding, so the capture entry
explains the cut and the check finding judges it.

**Why:** a refused child is a second structure that checks could walk and pass; the honest fact is
that the child is not available. Keeping the body and returning `null` preserves the parent-frame
occupant while forcing every consumer onto `SC-OCC-0003`. The cut itself is a host fact, so its
reason belongs in the host's report channel, not in a refused child and not in the kernel's bundle.

**Consequence:** the cut reason is a finding value in the capture report (D-28); it never enters
`Codes.all` and never changes the child or the body. A consumer that treats `null` `nested(cell)` as
an empty structure is wrong, and a child build-height refusal is a read refusal, not a cut.
`SC-MC-0004` is outside this decision: it remains an orientation mismatch, not a `ReadRefusal`.

### D-28 — A capture reports its own findings, opaquely

The capture port answered with a verdict and nothing else. `TooLarge` carried a volume, `Captured`
carried a view, and neither could say why: a cut child had no channel (D-27), an over-cap root had no
code, and a block state that cannot express the siting orientation had no producer. `SC-MC-0003` was
reserved and `SC-MC-0004`/`0005` had no producer at all.

The replacement is one value: `world.CaptureReport`, an ordered immutable list of
`Entry(FindingCode code, String message, List<String> notes, @Nullable CuboidSet cells)`, carried by
every arm. An entry is a host code (never minted by the kernel, never in `Codes.all`), prose that is
never parsed, ordered facts and an optional world region. `Captured`, `TooLarge` and `Unavailable`
each gain the component behind a two-argument compatibility constructor that answers `empty()`; the
`WorldCapture` port itself is unchanged. The kernel carries the report and never reads it: the arm is
the verdict, the report is the finding. A report site relays each entry as a `Finding` with its own
phase, the code's severity (the host's own catalog entry) and `Locus.world(cells)`, and applies the
unchanged §4.1 trust guard — a code already in `Codes.all` becomes `SC-OCC-0004` plus a note.

The four rulings this record fixes:

- **The arms carry the report**, not a wrapper. A value on the answer keeps `world` the only owner,
  keeps the port source-compatible for every host, and lets each arm say what it can: `Captured` and
  `TooLarge` carry entries, `Unavailable` normally none because its own code already is its finding.
- **Adapter-only codes are catalog codes.** The adapter registers `SC-MC-0001`…`0005` in its own code
  catalog through `Catalogs.Builder.codes`, so each carries a canonical severity and an explanation
  and `/cumulus explain` resolves it. `Codes.all` stays kernel-only (64 entries after the
  structure-set family, D-32); the kernel registry is not a host registry, and a host code never
  enters it.
- **The severities are the host's.** `SC-MC-0005` (refused root) is an ERROR; `SC-MC-0003` (cut) and
  `SC-MC-0004` (orientation mismatch) are WARNINGs, because a check can still pass over both, and a
  warning printed before a `Formed` summary is honest while an error would not be.
- **A cut is one fact per channel.** `SC-MC-0003` in the capture report explains the cut with its
  reason; `SC-OCC-0003` in the check bundle judges that the check needed the child. No channel
  translates into the other, neither suppresses the other, and a declaration that cannot be
  translated into its parent frame is cut and reported instead of dropped silently.

**Why:** the finding layer cannot move down — `world` may import only `core`/`space` (`03-modules.md`)
and severity lives on the finding (Law 7), so a severity in the payload would be a second vocabulary
and a constitution violation. The report cannot move into the match bundle — a match's findings must
be a function of the match tuple, and host capture state is not part of it (Law 11). The kernel
already carries opaque host codes for refusals (§4.1 of `world`); this is the same rule applied to the
capture answer, with the same trust guard.

**Consequence:** the cut reason is a finding value and the D-27 deferred channel is closed. A host
code's severity comes from the host catalog, so a misleading host code is possible; the guard only
prevents impersonation of a kernel code, and that stays a host review issue. A capture report never
fails a check, never enters a `MatchResult` and never changes a command's exit code. The evidence is
the capture-report kernel test, the report-site unit test and the capture GameTests
(`06-verification.md` §3). **Status (v1.2):** implemented at `28d0334` (the carrier at `0419f06`,
blank-note validation at `1fddb29`), on the P1 two-phase capture at `56c632c`; its adapter-only
capture overload (`audit`) becomes the siting `orientation` when it also feeds the root descriptor
projection (D-29).

### D-29 — A sited root capture publishes definition-frame descriptors

**Superseded by D-33.** The kernel descriptor no longer carries properties (H1), so the projection
has no carrier; the orientation audit (`SC-MC-0004`) remains, and the adapter's value checks read the
raw captured snapshot. The body below is the historical ruling.

The kernel's `property` expectation compares a descriptor's property string to a literal declared in
the definition frame (`world` §4.7). A root view of a sited capture is not the world frame: the
definition is posed at `siting.orientation()`, so before this decision a correct structure
at a rotated siting reported `SC-EXP-0001` for every orientation-bearing property, next to the
capture's `SC-MC-0004` capability warning. The kernel could not close it: `world` may not name a block
state and has no property vocabulary, and a kernel transform would make the generic property leaf
depend on Minecraft state semantics.

The replacement is a host contract, not an API change: **the root frame's descriptors are published in
the definition frame.** A capture that carries a siting orientation projects every root-frame
`BlockState` through `OrientationMapping.transform(orientation.inverse(), state)` before
`OccupantDescriptors`; the inverse pose is what recovers the definition-frame state from the observed
world state, and a `MappingResult.Mapped` state supplies the descriptor's properties, so
`Expectations.property` compares definition-frame values with no kernel change. An `Unrepresentable`
state keeps its raw state (the descriptor stays world-frame, so the value finding remains honest), and
the same capture carries the `SC-MC-0004` warning for it (D-28, `adapter` §10).

The boundaries this record fixes:

- **Only the root frame, only a non-null orientation.** A child/nested frame is an absolute extent, not
  rotated by the parent siting, so it stays in its captured frame; the phase-1 anchor-cell capture and
  an anchorless identity capture pass no orientation and stay world-frame (an identity projection is a
  no-op anyway).
- **An upright siting projects too.** The projection is not limited to non-level tilts: a mirror or
  rotation siting also publishes definition-frame properties, and because an upright pose is always
  expressible it produces no `SC-MC-0004` entry.
- **The descriptor is the only projected fact.** The actual state still feeds the block-entity lookup,
  the trait capabilities, the nested capability, the audit and the watch fingerprints: this is a
  publication choice for `OccupantDescriptor.properties`, not a substitution of the captured content.
- **The kernel stays raw.** `Expectations.property` still compares the published string to the
  literal; `world` §4.7 states that a descriptor is published in the view's frame and that the host
  owns the frame. A host that publishes world-frame descriptors keeps the old behaviour; the kernel
  neither knows nor transforms.
- **The unrepresentable fallback is visible.** A state whose properties cannot express the pose keeps
  its world-frame descriptor and reports `SC-MC-0004`; a property expectation on such a cell can still
  mismatch. Capability (warning) and value (error) are two findings, not a contradiction, and the
  fallback is never silent.

**Why:** the frame of a descriptor is exactly the fact only the host can know — it owns the state model
and the siting — while the kernel owns the frame arithmetic (`Siting`) and the generic property leaf.
Projecting at publication closes the gap with zero public API change, keeps `Expectations.property` a
pure string comparison, and leaves a custom host free to publish whatever frame its capture is in.

**Consequence:** a check at a rotated siting matches property expectations for representable states;
the property leaf's meaning is "the declaration's frame", which for a kernel-built `MatchRequest` is
the siting's definition frame. `SC-EXP-0001` on a rotated siting is now possible only for a cell that
also carries `SC-MC-0004`, and the audit still reports the capability fact. The evidence is the
rotated-siting adapter GameTests: stairs at clockwise/half/pitchUp publish definition-frame values, a
`property` expectation settles at a rotated siting, identity/phase-1 captures are unchanged, and an
unrepresentable state keeps the raw descriptor with the warning. **Status (v1.2):** implemented in
`LevelCapture` (inverse-orientation projection before `OccupantDescriptors`) with the S6 GameTest; no
kernel type was added. **Status (H1/H4):** superseded by D-33; the projection was removed with the
descriptor's properties, and the retained orientation audit is `OrientationAuditGameTest`.

### D-30 — The envelope is derived from the bounds, not declared cell by cell

A blueprint can ask for a closed world with `BlueprintBuilder.envelope()` (or the root document
member `"envelope": true`). The build derives
`E = bounds.asSet − (union bay.cells ∪ union Footprint.Declared cells)` from its local declaration
lists and appends one reserved `structurecumulus:envelope` bay (`everyCell` / `Footprint.none` /
`shared`) plus its `Focuses.bay()` + `eachCell(envelopeAir())` check last. An empty `E` is a no-op.
The cell set is never an API argument, and the flag is root-only; a definition that carries it is
`SC-DOC-0002`.

The alternatives were rejected:

- **An explicit `envelope(CuboidSet)` or an allow-list of ignored cells** would let an author hide a
  cell from the closed world without a check, and would make two sources of truth (the set and the
  bays) disagree. Declaring a bay is the existing opt-out, and it carries a check and a footprint.
- **A new `Plan`/kernel `Envelope` type** would duplicate the bay/check pipeline and force budget,
  read-scope, refusal and siting rules to be written twice. The synthetic pair uses the ordinary
  path and owns no claim row.
- **A schema version bump** is unnecessary: an unknown root member is already an error, and a
  missing boolean is off. A reader that does not know `envelope` rejects it rather than silently
  closing the world.
- **Reusing `SC-EXP-0001` for a foreign occupant** would fold "this leaf did not match" and "the
  closed world is not closed" into one code. `SC-EXP-0006` names the envelope; a refused read stays
  `SC-OCC-0004` and never becomes a mismatch.

**Why:** `bounds` is the only closed world the author already declares, and the envelope is the set
difference between it and the declarations. It reuses `Bay`, `Check`, `Focuses`, `Expectations`,
siting, read scope, budget, refusal and the claim planner; `Footprint.none` keeps the envelope
unclaimable, and a second `build()` of one builder is equal.

**Consequence:** a block outside `bounds` stays invisible; a block inside `bounds` and outside every
bay or declared footprint fails at its cell; permissive cells (ports, ignored cells, any-occupant
cells) must be declared as bays; the reserved id collides as `SC-BPT-0004`; `envelope: true` on a
definition is `SC-DOC-0002`; an empty `E` changes nothing. **Status:** implemented in
`BlueprintBuilder`, `Expectations.envelopeAir`, `BlueprintSpec`/`DocumentCompiler`,
`JsonReader`/`JsonWriter`, `Codes.envelopeOpen` and the envelope tests.

### D-31 — Cached context is a host-owned `(id, type)` value cache

`MatchRequest.cachedContext` carries an optional `CachedContext`: a read-only `(id, type)` lookup for
values an earlier settle/derive produced. The current attempt is authoritative. A value computed or
published in this attempt wins; a cached value is visible only for an instance key the plan declares
(`ExpectationMeta.requires()` for a check, `Computation.inputs()` for an aggregate); a missing or
wrong-typed cached value stays absent. Producer-only key fields (`merge`, `whenAbsent`, `retention`)
are not part of the lookup key. The kernel never writes, serialises, stamps, evicts or invalidates the
cache; the host owns every freshness rule.

The alternatives were rejected:

- **Keying the cache by the full `ContextKey`** would force a host to reconstruct producer-only fields
  it never stored, and a cached value whose producer merged differently would not match its own key.
- **A kernel cache** would make a match result depend on hidden host state and break the determinism
  tuple (Law 11); the default path must stay a fresh computation.
- **Check-only visibility** would leave an aggregate unable to read the prior structure context even
  when it declares that key as an input.

**Why:** the cache is a host optimisation, not a match input. One read-only port with the existing
absence semantics keeps the kernel free of eviction and freshness policy while letting a host reuse a
large aggregate across attempts.

**Consequence:** a cache hit cannot change the attempt's tuple, bypass the read scope, or skip the
budget; the current attempt's value always wins; and the host owns eviction (the shipped in-memory
persistence grows one entry per instance, `06-verification.md` §3.2 D5). **Status:** implemented in
`CachedContext`, `MatchRequest.cachedContext`, `MatchEngine`, `Computations`/`Expectations` and
`08-context-cache.md`; the focused evidence is `CachedContextTest`, `AggregateContextTest` and
`ComputationsCountTest`.

### D-32 — A structure set is a derived host layer, not a kernel transaction

A `Blueprint` remains one structure: one bounds, one siting, one check, one settle, one claim set.
The `structure_set` document adds a root blueprint, satellites placed in the root local frame, and
optional face/range/logical links between local root cells and local satellite cells.
`StructureSetCompiler` resolves the members and compiles their blueprints; the adapter's
`StructureSet` composes the host root siting with each relative siting and maps bounds and presence
into world space; `StructureSetHost` sequences presence, link validation, check-all, settle-all and
release. The kernel gains no set type, no multi-structure ledger transaction, no claim transaction
and no context rollback.

The alternatives were rejected:

- **A kernel multi-structure engine** would duplicate the budget, read-scope, claim, context and
  determinism rules on a second path, and make one attempt's tuple depend on the whole set.
- **A merged mega-blueprint** cannot express optional satellites, per-satellite arguments, independent
  claims or per-structure outcomes; it would turn a skipped preheater into a required cell.
- **World coordinates in the set document** would make it a placement record, not a portable graph;
  a satellite is exactly `rootSiting.compose(relativeSiting)`, and a null composition is a finding.
- **A kernel context rollback** would hide the published value from the host that owns the instance;
  release is claim-only and the result names every rolled-back id.
- **Host-only JSON parsing without the document layer** would lose the shared schema, spans and the
  `(blueprint, effective arguments)` compile cache; the set document reuses the blueprint compiler.

**Why:** a set is placement and orchestration policy over the single-structure primitives. It reuses
`Siting.compose`, `Blueprint.reads`, the check/settle split, the ledger CAS and the per-structure
`MatchOutcome` without weakening a kernel law, and it keeps the host responsible for the lifecycle it
alone can observe.

**Consequence:** set failures live in the `set` code family (`SC-SET-0001`…`0007`); presence and links
are host work before any check; any failure before a settle means no settle; a settle failure releases
the formed structures in reverse declaration order and reports a failed release as `SC-SET-0007`;
`claims` is filtered by `(owner, siting, bay)` so a ledger-wide `ClaimSet` never leaks another
structure's rows; a rolled-back structure keeps the context its settle published (`ContextStore` has
no removal operation, so the host owns whatever it does with that `InstanceId`); nested sets,
satellite-of-satellite, automatic retry, world-coordinate placement in the document and a
command/capture executor for a set are out. **Status (SS4):** the document/compiler layer landed at
`9cf2794`/`00cf6c4`, the host plan and orchestration at `93f5941`, and SS4 adds the six-class/33-test
stress suite plus the counts and gap close-out (`06-verification.md` §3.3).

### D-33 — Host payload is one typed snapshot read, never a descriptor projection

H1 removed the descriptor's `properties`/`tags` and the kernel `Expectations.property`, H1 added
`HostType<H>`/`WorldView.get`, H2 added one immutable snapshot per frame, H3 added the adapter's
`property`/`state`/`tag`/`fluid` factories over `Expectations.host`, and H4 closes the docs, counts
and stress evidence. D-29's definition-frame descriptor projection therefore has no carrier:
`OccupantDescriptor` is `(type, kind)`, and the only siting fact the capture publishes is the
`SC-MC-0004` orientation audit.

The replacement contract:

- one world snapshot per frame: copied `PalettedContainer<BlockState>` sections, per-section
  `FluidState` arrays, detached block entities and captured entities; no live `Level` is retained;
- `HostType<H>` is an id token, never a `Class<?>`; the adapter owns the constants (`HostTypes`);
- `WorldView.get(pos, type)` is the only host read: default `null`, total, no exception;
  `get(ref, type)` reads the ref's anchor; a capture overrides it and answers from its snapshot;
- `Expectations.host(type, test, mismatchCode, absentCode, description)` is the only bridge: a
  missing value is `Unreadable` with the caller's absent code, a false predicate is `Mismatch`; the
  kernel mints no MC code and never decodes, transforms or re-frames the value;
- the value is the captured one in the snapshot's frame. A sited root capture is in the world frame,
  so a property/state check compares the observed world value; the adapter does not project
  definition-frame states, and a host that needs them must capture them;
- the composite `fluid` factory is two host leaves under `all_of`: the fluid state's type and the
  block's own legacy fluid block. Its `ExpectationMeta` is the kernel junction's `SC-EXP-0001`,
  while the findings are the leaves' `SC-MC-0009`; a waterlogged slab stays a block state, not the
  real fluid;
- a missing anchor `BlockState` is `SC-MC-0010`, the shared "host value was not captured" code, never
  `SC-MC-0001` and never a live read; `SC-MC-0001` remains the capture refusal;
- heightmaps are not captured: no kernel request path exists.

The alternatives were rejected:

- **Keeping D-29's projection on the descriptor** would resurrect a property map the descriptor no
  longer has and put a Minecraft state model back into a publication the kernel owns.
- **A per-cell `HostData`/`HostTable` or a `HostView` wrapper** would duplicate the snapshot and turn
  "one snapshot" into a claim instead of a shape.
- **A `Class<?>` token or an `McSnapshot` union** would put a host type or a host discriminator in
  the kernel.
- **A default value for a missing read** would turn a missing capture into a false fact instead of
  `Unreadable`; the absent code has to be the caller's, so the kernel never mints `SC-MC-*`.

**Why:** one snapshot is the only thing a host can capture once and read many ways; typing it at the
read boundary keeps the kernel `(type, kind)`-only while letting an adapter compare real values. The
absent code must be the caller's for the same reason the mismatch code is: the kernel has no MC
registry. The frame is the host's because only the host knows which pose it captured.

**Consequence:** a rotated siting compares world-frame values unless the host projects its own
snapshot; `SC-MC-0004` remains the capability warning for a pose the observed state cannot express;
`SC-MC-0010` covers both a missing expectation value and a missing anchor state. D-29's projection
and its test are retired; `OrientationAuditGameTest` covers the audit and `HostDataScenarioTest`
covers the rotated set, nested child frame, waterlogged slab and missing snapshot. The record set is
now 33 decisions; `Codes.all` is unchanged at 64 entries because every `SC-MC-*` code is the
adapter's own. **Status (H4):** implemented; H1 added `HostType`/`WorldView.get`/`Expectations.host`,
and H2/H3/H4 added no kernel type beyond those.

## 3. Consequences a reviewer should attack

- The `CuboidSet` bay and per-cell claim rows make the claim planner enumerate cells. The bound is
  the structure volume; a 1M-cell structure emits 1M rows. If that is unacceptable, the footprint
  must become a box-list row form, which changes `ClaimRow` and the ledger. **Open, not decided.**
- `StatusOf(cell)` on a huge box is a per-cell call; it now costs one `read` per cell, so the shipped
  focuses use box queries and a reviewer should check that `Focuses.bay`/`allCells` do not degrade to
  per-cell status calls on a clean snapshot.
- A `Partial` section gives the computation a per-cell refusal map; the engine cannot prove that a
  custom computation consulted it. A `Found` returned over refused cells is a short sum and a review
  defect (D-24), not an engine catch.
- A relayed host code has no kernel `CodeEntry`, so its severity is the finding's, not the kernel
  registry's; the trust guard only prevents impersonation of a kernel code, not a misleading host
  code. A report site should keep the refusal's status in the note, and the shipped adapter registers
  its own `CodeEntry`s so its codes have a canonical severity (D-28).
- A capture report is host state, not match state (D-28): the same `(blueprint, snapshotId, siting,
  owner, budget, claims revision, catalog snapshot)` tuple can be printed with a different capture
  warning by a different host. That is the price of the host owning the reason; the match result
  stays deterministic (Law 11).
- The orientation audit reads only observed blocks: a `SC-MC-0004` says the pose is not realisable
  for those blocks, not that a declaration is wrong. The typed snapshot (D-33, superseding D-29)
  publishes no definition-frame properties: a check at a rotated siting compares the captured world
  value, so a definition-frame property declaration must be captured in that frame by the host.
  Capability (warning) and value (finding) stay two findings, not a contradiction.
- The nested source is a third-party capability; the adapter's block-over-entity precedence and
  first-provider-wins rule are policy, not a merge. A mod that wants two nested bodies at one cell
  cannot express it (D-26).
- A cell outside every bay is ignored unless the blueprint opts into `envelope` (D-30). Then every
  cell inside `bounds` is judged: a port or an "any occupant" cell has to be a declared bay, and only
  cells outside `bounds` stay invisible.
- Model C, the nested capability, the capture report, the typed host snapshot (D-33, superseding
  D-29), the derived envelope (D-30), the cached-context port (D-31) and the structure-set host layer
  (D-32) are implemented, and the spec index is green; what remains is the performance/cost pass and
  fresh visual review screenshots (D-02, `07-regeneration.md` §7).
- A structure set is not atomic across structures or context (D-32): a later settle failure releases
  the earlier claim rows in reverse order, but the earlier kernel contexts stay published.
  `StructureSetResult.rolledBack` is the host's list, and `ContextStore` has no removal operation; the
  host must use a fresh instance or its own store if it needs invalidation.
- Presence is charged to a ceiling seeded from the run budget's `cells`, while each structure attempt
  still receives the full `Budget`; a set's total reads can therefore exceed `budget.cells()`
  (`06-verification.md` §3.3).
- `MatchRequest` carries an `InstanceId` before a match exists; a host that previews many candidates
  must allocate ids it never uses. This is a host counter, not a kernel resource.
- `ContextStore.publish` is total and single-writer; a host that publishes from two threads violates
  the contract. The in-memory store synchronises, but a `SavedData` store must be server-thread-only.
- The document schema's `Value` arguments are untyped at the boundary; a factory is the only place a
  type is enforced. A bad argument is `SC-DOC-0002`, not a compile-time error.
