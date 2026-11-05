# 05 — Decisions

> Every ruling that replaced an older design. The old set's `19`, `23`, `24` and `open-questions.md`
> are historical; this file is the record. A decision names its consequence so a reviewer can reject
> the consequence without re-litigating the premise.

## 1. Process decisions

### D-01 — The design is the source; the kernel is generated (generation retired 2026-10-31)

The old code, its tests and the old `docs/spec` set are reference material. The new kernel is written
from this design set, compiled, and tested; the old kernel is deleted rather than migrated. A public
signature that is not in a module file is a regeneration defect. The baseline is archived outside the
project (`Data/StructureCumulus/baseline-2026-10-26.tar.gz`).

**Consequence:** no compatibility shims, no migration ledger, no "the design is ahead of the code"
section. The acceptance gate is the regenerated kernel.

**Amendment (v1.2).** The v1.1 pending block is implemented: Model C and body identity in
`cb06aa8`/`64d04ce`, the nested capability in `f3e0338`/`d6aefc2`. The banned "design is ahead of
the code" section remains banned; a future pending block needs the same decision ID and gate record.

**Amendment (2026-10-31).** The generation half is retired. The kernel is corrected to this set in
place; it is never deleted and rebuilt from it, and no skeleton is compiled as a substitute for the
real source (`docs/README.md`, "The one rule"). "Regeneration defect" reads **conformance defect**
throughout the set, and `07-regeneration.md` now specifies correction rather than rebuild. What
survives is the part that mattered: a public shape is a module-file change first, and code that
outruns the document is the defect this set exists to prevent. The acceptance gate is conformance to
the set, not a regenerated kernel.

### D-02 — Scope of v1

Core kernel + document compiler + thin remedy SPI. The v1 round scoped the NeoForge adapter to
`modules/adapter.md` and left it unimplemented at the time; no scheduler, no dirty index, no overlay
renderer, no schematic importer, no client code. This kept the first regeneration small enough to
prove, while the ports make the adapter a later, independent exercise. Current status (2026-10-28):
M1 (host + command + capture-check), M2a (loaded-entity capture), M2b (upright orientation), M2c
(Model C, body identity, total read, four-state traits and its five adapter trait keys, three after
T1–T3), M2d (nested
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
`fluid` factories. T1–T3 (D-34) replace the `readBlock`/`readEntity` trait providers with the generic
`TraitProvider<T, H>`/`TraitHolder<H>` pair registry and direct capability values. Pending only: the
performance/cost pass (block-entity-lazy and entity sweep) and fresh visual review screenshots.
There is no regeneration milestone.

**Consequence:** the kernel tests use an in-memory `WorldView`, `ClaimLedger` and `ContextStore`; the
adapter is implemented through T1–T3. The spec index is green at v1.2: the M5e amendment was applied
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

`Coverage` is the only three-value vocabulary; a focus declares `staticCells`, `bindsSubjects` and
`bindsClaimableSubject`; the build compares them to the bay's coverage and footprint. `ReadScope` is the only read closure; there is no
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
| Q27 trait providers | Generic `TraitProvider<T, H>` keyed by `(trait id, holder id)`; the three capability values are the live handles (D-34). |

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
mapping once; a raw `view.occupant(local)` in a check would be a frame bug. The host applies the same
rule to the read-scope entity windows: it maps every `ReadScope.windows` entry through the siting
before it builds `SnapshotPolicy`, so the kernel keeps them blueprint-local. Nested subjects
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
  host derives `SnapshotPolicy` (`kinds`, `traits`, `depth`, and `windows` mapped through the siting)
  from it. `ReadScope` carries `(cells, keys, traits, kinds, depth, windows)`.

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
the MOD bus event. **Amendment (D-36):** the seam is now the public generic per-holder
`NestedExtentLookup`/`NestedExtentProvider` registry in `mc.capability`; `CapabilityLookup` and the
old aggregate `mc.NestedExtentLookup` are deleted. **Status (v1.2):** implemented in
`f3e0338`/`d6aefc2`; the nested query remains the four-argument block overload, while the separate
trait capability queries use the five-argument `Void`-context form.

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
  and `/cumulus explain` resolves it. `Codes.all` stays kernel-only (65 entries after the
  same-siting entry, D-35); the kernel registry is not a host registry, and a host code never
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
wrong-typed cached value stays absent. Producer-only key fields (`merge`, `retention`)
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
  release is claim-only and the host invalidates the context through `StructureEvent.Broken` on the
  bound `StructureManager` (D-35).
- **Host-only JSON parsing without the document layer** would lose the shared schema, spans and the
  `(blueprint, effective arguments)` compile cache; the set document reuses the blueprint compiler.

**Why:** a set is placement and orchestration policy over the single-structure primitives. It reuses
`Siting.compose`, `Blueprint.reads`, the check/settle split, the ledger CAS and the per-structure
`MatchOutcome` without weakening a kernel law, and it keeps the host responsible for the lifecycle it
alone can observe.

**Consequence:** set failures live in the `set` code family (`SC-SET-0001`…`0008`); presence and links
are host work before any check; presence and the member attempts share one budget (D-35); any failure
before a settle means no settle; a settle failure releases
the formed structures in reverse declaration order and reports a failed release or `Broken` dispatch
as `SC-SET-0007`; `claims` is filtered by `(owner, siting, bay)` so a ledger-wide `ClaimSet` never
leaks another structure's rows; a rolled-back structure's context is invalidated by
`StructureEvent.Broken` through the bound manager (D-35); a satellite may not share the root siting (D-35); nested sets,
satellite-of-satellite, automatic retry, world-coordinate placement in the document and a
command/capture executor for a set are out. **Status (SS4):** the document/compiler layer landed at
`9cf2794`/`00cf6c4`, the host plan and orchestration at `93f5941`, and SS4 adds the six-class/33-test
stress suite plus the counts and gap close-out (`06-verification.md` §3.3). S6 closes the set layer
(D-35).

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
covers the rotated set, nested child frame, waterlogged slab and missing snapshot. The record set was
33 decisions at H4; `Codes.all` was 64 entries then (65 after D-35) because every `SC-MC-*` code is the
adapter's own. **Status (H4):** implemented; H1 added `HostType`/`WorldView.get`/`Expectations.host`,
and H2/H3/H4 added no kernel type beyond those.

### D-34 — Trait providers are generic per holder, and capability traits carry the capability

T1–T3 replace the `readBlock`/`readEntity` provider interface with `TraitProvider<T, H>`, key the
registry by `(trait id, holder id)`, and make the three standard traits return the live NeoForge
capability (`IItemHandler`, `IEnergyStorage`, `IFluidHandler`) instead of list, integer or NBT
copies. The generic NBT traits are removed. `11-trait-providers.md` is the design.

The contract:

- `TraitHolder<H>` is an id token, never a `Class<?>`; `TraitHolders` declares `block` and `entity`
  and is the only adapter code naming a subject kind;
- the actual holder records (`BlockTraitContext`, `EntityTraitContext`) carry no capture; the
  capture is a `get(actual, capture)` parameter;
- `TraitLookup` keys by `(trait id, holder id)`: a duplicate pair is rejected, the same trait with a
  different holder is a second registration, and `knows(id)` distinguishes an unknown trait from a
  known trait without a provider for this holder;
- `TraitRegister` is one generic `<T, H> register(...)`;
- `CapabilityTraitProvider<T, H, C>` is the only capability provider class; `CapabilityAccess<H, C>`
  is the subject-specific get signature and `CapabilityAccesses.block()`/`entity()`/`entityVoid()`
  cover the current holders (entity inventory is `Void`, block and sided entity use the nullable
  `Direction`);
- a null capability is `Absent`; a throwing lookup is the dispatcher's `Refused(failed)`; a block
  state whose block entity was not captured is `Refused(notCaptured)` before the capability call,
  so `BlockCapability.getCapability` never falls back to a live `Level.getBlockEntity`;
- the standard providers are six declarations (`BlockTraitProviders`, `EntityTraitProviders`) over
  the generic provider; there is no per-capability class and no item holder.

The alternatives were rejected:

- **Keeping the block/entity defaults** would turn a missing provider into a hidden absence and
  re-open the provider interface for every new subject.
- **A per-capability provider class** (`BlockInventoryProvider`, `EntityEnergyProvider`, …) would
  duplicate the capability get and defeat the generic provider.
- **A `Class<?>` holder token** would smuggle a reflective type into the provider identity.
- **List/`Integer`/NBT values** would invent a second value model and lose the real handle.
- **An item holder or item accessor** would be dead API: the current capture has no item subject.

**Why:** a trait is a typed read of one real API value from one subject; the subject must be a type
token and the value must be the API object. The pair key keeps one trait available on several
holders without a fake default for the other.

**Consequence:** `StandardTraitProviders` is replaced by `BlockTraitProviders` and
`EntityTraitProviders`; the NBT keys disappear, so a host that needs serialized block-entity or
entity data registers a typed provider over the owning API. The trait value is live for the lease's
lifetime; a consumer must not retain it after the lease stops being current. The record set is now
34 decisions (D-34); the kernel types `TraitKey`/`TraitSlot`/`Trait` are unchanged. The value is
live but not policed: the contract is purity, not immutability (D-41). **Status
(T1–T3):** implemented; the focused trait tests and the full GameTest run are green.

### D-35 — A set's siting, budget and context lifecycle close S6

S6 closes the three structure-set gaps in one decision: a satellite may not share the root siting
(A+), the set carries one budget across presence and every member attempt, and `ContextStore` gains a
removal operation so a rollback can invalidate the contexts it formed.

The contract:

- **A+ same-siting.** A satellite whose relative placement is the identity
  (`Placement.identity`: offset `LatticePoint.origin`, orientation `Orientation.identity`) would sit
  exactly on the root. `StructureSetCompiler` rejects it with `SC-SET-0008`;
  `StructureSet.place` reports the same code for a hand-built set whose composed siting equals the
  root siting; `StructureSetHost.run`/`check` rejects a hand-built `StructureSet` whose satellite
  siting equals `rootSiting` before presence or any check. `SC-SET-0005` remains the
  satellite-satellite duplicate. A satellite at the root origin with a non-identity orientation is
  allowed, because its composed siting differs from the root siting.
- **One set budget.** `StructureSetHost` holds one mutable set budget seeded from `Budget`. Presence
  charges `(1, 0)` per read; each attempt (root, then each present satellite) receives the remaining
  `Budget`; an admitted outcome's `spend()` is subtracted. Presence exhaustion is `SC-SET-0004`; an
  exhausted attempt is the engine's `SC-ENG-0001` and stops the set, as any non-admitted check does.
- **Context invalidation.** The bound `StructureManager` removes the instance and its
  `StructureContext` when it receives `StructureEvent.Broken`; `ContextStore.remove` is not the
  rollback path. `StructureSetHost` dispatches `Broken` for every formed attempt regardless of the
  release verdict or a release throw; a dispatch `RuntimeException` is a `SC-SET-0007` rollback
  failure and does not stop the other releases or dispatches.

The alternatives were rejected:

- **Reusing `SC-SET-0005` for the root collision** would fold "a satellite repeats another
  satellite" into "a satellite repeats the root", so an author could not tell which duplicate to
  fix. `SC-SET-0008` names the root/satellite case.
- **A per-structure full budget** (the pre-S6 behavior) lets a set read `n × budget`; the host
  admitted one set, so the set is the unit that owns the ceiling.
- **A separate presence ceiling** leaves a second budget with no owner and no cross-member
  accounting, and cannot answer whether a later attempt is still affordable.
- **Leaving contexts in place and letting the host invalidate them** leaves the shipped host without
  an operation to do so; `remove` is the smallest store operation that expresses invalidation.
- **Aborting rollback on the first failed release or removal** would leave the remaining formed
  structures settled and their contexts published.

**Why:** the root and its satellites are one placement graph and one host run; a satellite at the
root siting cannot be placed or claimed independently, a set budget is the honest total, and a
rollback that only releases claims leaves a context describing a structure that no longer exists.

**Consequence:** `SC-SET-0008` joins the set family (`Codes.all` is 65); a hand-built `StructureSet`
is defensively re-checked; the set's total reads never exceed `budget.cells()`; a successful
rollback invalidates the `StructureContext` of every formed attempt via `StructureEvent.Broken`
(`rolledBack` still lists only the successful releases); a failed release does not skip the
invalidation. The record set is now 35 decisions (D-35). **Status (S6):** implemented; the focused
kernel/adapter tests are green; `./gradlew check` and `runGameTestServer` are green on the expanded wave (382 kernel / 48 containers, 411 adapter / 43 containers, 126/126 GameTests; check exit 0).

### D-36 — Nested-extent lookup is generic per holder

The nested capability had one package-private aggregate lookup with a hardcoded `block(...)` and
`entity(...)` method. Every fake and the production `CapabilityLookup` had to implement both, and a
future subject kind would have changed the interface. D-36 replaces that seam with the same
per-holder shape as the trait providers:

- `NestedExtentHolder<H>` is a typed `NamespacedId` token, never a `Class<?>`;
- `NestedExtentProvider<H>` declares `holder()` and `@Nullable NestedExtent get(H actual)`;
- `NestedExtentLookup` is an immutable registry keyed by holder id: `get(holder, actual)`,
  `knows(holder)`, a builder with duplicate-holder rejection, and `capabilities()` for the built-in
  block/entity pair;
- `NestedExtentHolders.block` carries `BlockNestedExtentContext(level, pos, state, blockEntity)`;
  `NestedExtentHolders.entity` carries the loaded `Entity`;
- `NestedExtentAccess<H, C>` and one `CapabilityNestedExtentProvider<H, C>` cover both
  `BaseCapability<NestedExtent, C>` signatures; `NestedExtentAccesses.block()`/`entity()` and
  `NestedExtentProviders.block`/`entity` are the standard declarations.

`LevelCapture` keeps its package-private constructors and calls
`get(NestedExtentHolders.block, new BlockNestedExtentContext(...))` or
`get(NestedExtentHolders.entity, entity)`. The old `mc.NestedExtentLookup` interface and
`CapabilityLookup` enum are deleted. There is no aggregate block/entity interface and no
per-capability provider class. A new subject kind adds a holder, an accessor when it is a capability,
and a provider; it does not edit the lookup interface or every fake.

**Why:** the previous seam encoded the current two subjects into the lookup type. The generic
registry keeps one lookup, one provider shape and one registration path, and mirrors
`TraitProvider`/`TraitHolder` without pretending a nested extent has a trait key.

**Consequence:** the provider/holder/lookup API is public in `mc.capability`; the fake tests register
only the providers they need; a provider `RuntimeException` still becomes the existing capture
warning at the `LevelCapture` call site. The record set is now 36 decisions (D-36). **Status (N1):**
implemented; the nested unit and GameTests, the full `check` and `runGameTestServer` are green.

### D-37 — A focus declares whether its subjects can back a claim

`Focus.bindsSubjects()` says a focus produces subjects; it does not say those subjects are
claimable. A custom focus could bind a nested body while answering `bindsSubjects() == true`, so
`Coverage.anySubject + Footprint.bound` built a blueprint that could only fail later with
`SC-CLM-0004`. D-37 adds `Focus.bindsClaimableSubject()`, default **false**: an unknown custom focus
is not assumed claimable. The shipped focuses that bind root block/entity subjects answer true;
`nested` and `context` stay false. When a bay's footprint is not `None`, `BlueprintBuilder` rejects
any check that binds subjects but does not claim them (`SC-BPT-0005`). A bay with `Footprint.None`
claims nothing and is not rejected. The runtime `SC-CLM-0004` check in `ClaimPlanner` remains the
authority if a focus declaration is wrong or a subject changes frame at runtime.

**Why:** claimability is a positive property. Defaulting unknown focuses to true was the hole that
let the nested custom focus pass the build. Defaulting to false makes a custom focus opt in, moves
the failure to the earliest place that has the bay's footprint, and keeps one runtime guard.

**Consequence:** the focus API has one more default method; custom focuses that bind claimable
subjects must override it. The record set is now 37 decisions (D-37). **Status:** implemented; the
nested build-rejection test, the runtime `SC-CLM-0004` tests, the full `check` and
`runGameTestServer` are green.

### D-38 — Claim release is whole `(owner, siting)`

`ClaimLedger.release(owner, siting)` deletes every row of that key and is the only release API. One
siting is one instance: a structure's own bays share the key, so a shared cell with two rows is still
one lifecycle object. Per-bay or per-row release would add lifecycle states the host does not have
and would let one structure's rollback keep or drop a sibling bay's rows independently. Removal
(`Broken`/`Invalidated`) and a move release the whole instance; a structure-set rollback releases
each member's own key. A world unload is different: it drops only the in-memory manager instances
(and drafts) for that world and keeps the persisted claim rows; the host owns whether those rows are
later released or reused. A failed release leaves the key for a later attempt and is reported as
`SC-SET-0007`; it is not split into partial releases. Duplicate rows in one key remain a ledger
representation detail and collapse on reload.

**Why:** the host owns structures, not claim rows. Whole-key release matches the instance boundary
and keeps the ledger's compare-and-set model simple.

**Consequence:** there is no per-bay or per-cell release. A caller that needs finer invalidation must
settle the new row set instead. **Superseded in part by D-45** — the key is the instance, not
`(owner, siting)` — **and by D-44** — a world unload drops the rows instead of keeping them. A piston full move translates the rows to the new siting and then
releases the old key; a partial/destroyed piston result is a removal and releases the key. A world
unload neither removes nor rewrites claim rows. The record set is now 38 decisions (D-38).
**Status:** decided; `SavedClaimsLifecycleTest` characterizes the whole-key removal and duplicate-row
collapse, and `StructureLifecycleAdversarialGameTest.levelUnloadDropsInstancesAndKeepsTheClaimRows`
plus the piston partial/destroy/move cases carry the lifecycle behavior.

### D-39 — Empty and non-fluid expectations

`anyOccupant` deliberately matches every occupant, including air; it is a "the read succeeded" leaf,
not a NOTAIR test. D-39 adds two kernel leaves:

- `Expectations.empty()` matches air or a vanilla fluid block (`minecraft:water`/`minecraft:lava`);
- `Expectations.nonFluid()` matches any occupant that is not one of those fluid blocks (air is
  non-fluid).

`NOTAIR` is exactly `not(air())`: water and lava are non-air and pass, while air fails. `empty()` and
`nonFluid()` are separate opt-in leaves for slots whose real rule is "no air and no fluid" or "no
fluid"; they do not redefine `NOTAIR`. The kernel `OccupantSnapshot` has no separate fluid state; the
classification is by the vanilla fluid block type. A waterlogged block remains a solid block occupant
at the kernel level and is not classified as a fluid; a host `fluid` expectation can read the captured
fluid state.

**Why:** `anyOccupant` cannot express "not air" by itself, and the altar test had conflated `NOTAIR`
with "no fluid". `not(air())` is the exact NOTAIR test; `empty`/`nonFluid` remain available for slots
that genuinely need a fluid rule. No new finding code is introduced (the leaves use `SC-EXP-0001`/the
`not` wrapper's `SC-EXP-0002`).

**Consequence:** `BloodAltarScenarioTest` admits water in its NOTAIR slot (water is not air);
`empty` and `nonFluid` have direct classification tests. `Codes.all` is unchanged. The record set is
now 39 decisions (D-39). **Status:** implemented; kernel tests, full `check` and 71/71 GameTests are
green.

### D-40 — Structure contexts belong to a host-driven structure manager

`StructureContext` had no owning structure object. It was published into a `ContextStore` map and
the adapter persisted that map in `SavedContexts`; the map grew without lifecycle, and `nextSerial`
existed only because the map could outlive the session. D-40 replaces the detached store with
`StructureManager`:

- `StructureSpec(owner, siting, structure)` describes what to form; `StructureInstance(id, spec,
  context, bounds)` is the formed object and owns its latest context;
- `StructureManager.begin(world, spec)` reserves a draft `StructureAttempt`; the settle pass publishes
  into the draft; `commit(bounds)` forms the instance; `abort()` discards it;
- the host dispatches `Broken`, `Loaded`, `Unloaded` and `Invalidated`; the manager owns the
  registry and the context lifecycle;
- the manager is in-memory. The adapter deletes `SavedContexts`, its codecs and tests; no context is
  persisted. A host that wants durable non-derived values persists them itself and restores through
  `Loaded`;
- the adapter's `WatchEvents` detects breaks, explosions, placements, piston moves and level unloads
  and dispatches the corresponding events.

**Why:** the library owns the identity/context state machine, not the world lifecycle. Binding the
context to the instance removes the map/serial/eviction side table; the host already knows when a
structure is formed, moved or gone.

**Consequence:** `ContextStore` remains the narrow publish/read interface implemented by the manager
and the attempt. `Host` carries a `StructureManager`; `StructureKernel.settle` takes a
`StructureAttempt`.
The record set is now 40 decisions (D-40). **Status:** implemented; `StructureManagerTest`,
`AdversarialManagerTest`, `StructureLifecycleGameTest` and
`StructureLifecycleAdversarialGameTest`; `./gradlew check` and `runGameTestServer` are green on the expanded wave (382 kernel / 48 containers, 411 adapter / 43 containers, 126/126 GameTests; check exit 0).

### D-41 — The snapshot carries values; a live handle is a contract, not a guard

The capture freezes the values the framework owns and states one contract where it does not.

**The contract.** `check` is pure: it writes nothing — no claim, no frame, no world edit — and that
includes writing through a host value. Determinism is promised for a check that reads only captured
values; a check that dereferences a live host value accepts that its verdict may differ between runs.

**The values.** Captured cells are frozen: `BlockState`, `FluidState`, the detached block-entity copy
and the occupant walk are copies. The three capability traits carry the live NeoForge capability
(D-34); the framework neither copies nor wraps them, and does not police how they are used. An entity
cell carries the captured `EntityType`, never the live `Entity`: the host read is
`HostTypes.entityType`, and any other entity state is a captured trait.

**The store.** A `Retention.instance` value is merged into the instance context and survives every
later revision, so it must be a value. A live handle may only be published at `Retention.match`,
where it dies with the attempt; a `Retention.instance` handle is a contract violation.

**Rejected:** freezing the three capabilities into read-only copies (a second value model and a copy
on every capture, for a value no shipped check dereferences); a kind marker on `TraitKey` (the value
stays reachable through `Trait.value()` and `OccupantSnapshot.trait`, so the marker would label the
gun without unloading it); an exception carved into I-02 (the snapshot would stop being an isolation
boundary at all).

**Why:** the framework cannot tell whether an arbitrary `Object` is mutable, and the only type-level
fix costs a public concept per capability. The shipped surface dereferences no trait value, so the
contract costs one paragraph and keeps the real API object for addons.

**Consequence:** `world` §4.7, `adapter` §3.1/§7.1, `11` §7.1 and `context` §4.2 state the contract;
`04-api` §4 keeps its `getStackInSlot` example and notes that the value is live; `10-host-data` §2-§4
and `adapter` §8 rename the entity host read to `HostTypes.entityType`.

### D-42 — Settle is a prepare step with exactly one fallible write

`settle` validates everything it can before it writes, performs exactly one ledger compare-and-set,
and leaves nothing that can fail after it.

**The target.** The only legitimate publish target is the `StructureAttempt` that reserved the
instance; `settle` takes it by type and a bare `ContextStore` is no longer an accepted argument. A
closed attempt, an attempt of another `InstanceId`, and an attempt whose `StructureSpec` (owner,
siting, structure) differs from the request are each `Undecided(designDefect)` with no write.

**The write.** Exactly one `ClaimLedger.settle` per settle, including the zero-row case, which now
replaces the `(owner, siting)` key with an empty row set instead of leaving a previous instance's
rows behind. Every check that can fail runs before it: attempt validity, lease currency, `Admitted`
belonging, bindings, the planner, and the publishable value set (already type-checked and non-null by
construction). After the compare-and-set the kernel has no failing step: publishing into an open
attempt cannot throw.

**The protocol.** `settle` prepares (rows written, context staged in the attempt), `commit` forms,
`release(instance)` plus `abort` rolls back. Because the compare-and-set replaces one instance's rows,
`release` after a `Formed` removes exactly the rows the settle wrote: it is exact compensation, not a
destructive one. A host that forms a set settles every member first, commits them together, and rolls
back with that compensation.

**Rejected.** Folding `commit` into `settle`: the set host needs the prepare/commit gap for its own
rollback (`StructureSetHost` settles every member, then commits, then restores claims). A single
`form(instanceId, rows, values)` port: it moves the same ordering decision into the host without
adding atomicity, and the two stores have different lifetimes — the ledger is per-world and
persisted, the context is per-instance and in memory. A `Prepared` handle that forces commit-or-
release: it would make a result a resource, which `engine` §7 forbids.

**Why:** two writes with no transaction cannot be made atomic by catching exceptions, because a
ledger write is not undoable without knowing what it replaced. Ordering plus a total tail gives the
same guarantee with no rollback path.

**Consequence:** the two-argument `settle` is deleted; `StructureKernel.settle` and
`MatchEngine.settle` take a `StructureAttempt`; `StructureAttempt` gains `isOpen()`; `04-api` §3
shows `begin` / `settle` / `commit`; `engine` §5, `spi` §4.3, `12` §6, `02-model` §2/§5 and
`context` §4.2.1 state the protocol; `claim` §4.5 states the compensation.

**Host contracts:** a `ClaimLedger.settle` that throws is a host contract violation and its row state
is undefined; so is a `StructureAttempt` whose `publish` or `commit` throws while `isOpen()` is true.

### D-43 — The document declares; the compiler confirms

A document describes a blueprint; it never asks the compiler to repair one. Every place where a
declaration can disagree with the code that produces it is a compile finding, and no declaration is
ever retargeted.

**Kind resolution is exact.** A catalog lookup never falls back: `mymod:air` is either registered
under that exact id or it is a finding. The default namespace is applied once, by the reader, when
the document omits one; a qualified id is never re-read in `structurecumulus`.

**Grammar belongs to the default namespace.** A structural form — `all_of`, `not`, `each_cell`,
`at_least`, `sum` — is grammar only when its namespace is `structurecumulus`; a qualified id is a
catalog id. `mymod:each_cell` therefore reaches the expectation catalog or fails, never the built-in
grammar.

**A computation is its output key.** `Computation.id()` is derived from `output()`, the document's
`id` defaults to `output` and must equal it when written. A computation's `inputs` must equal the ones
its factory declares. Any disagreement is `SC-DOC-0014`, and the compiler adds no wrapper: the object
a factory returns is the object in the blueprint.

**Rejected.** Keeping the fallback with a warning: Law 9 says never a fallback, and the verdict would
still be the wrong one. Keeping the wrapper and teaching the Java builder to reproduce it: a second
semantic path, and the wrapper carries the document's `inputs` over the factory's (K10-D04). Deleting
the `id` member outright: a schema version bump for a cosmetic gain, when a derived field gives the
same guarantee.

**Why:** I-13 promises that the two frontends produce one blueprint; a compiler that repairs
declarations makes the promise false in exactly the cases an author is most likely to hit. The
built-in computation factory already defines its identity as `output.id()`, so the document's second
name was never a second meaning.

**Consequence:** `Computation.id()` becomes a default method over `output()`; `Computations.Builtin`
drops its override; the JSON reader defaults `id` to `output` and the writer stops emitting it;
`document` §4.1/§6/§8, `structure` §4.10 and `04-api` §5 state the rule.

### D-44 — Claims are session-scoped

A claim ledger lives exactly as long as the structure instances that own its rows: the adapter persists
neither, so a restart starts with an empty manager and an empty ledger. `ClaimLedger.inMemory(World)` is
the shipped implementation and the mod registers no `SavedData`.

**The asymmetry it removes.** M3 shipped a persisted ledger while D-40 shipped an in-memory manager, so
a row could outlive its instance. Such a row can be neither checked, released nor invalidated; its only
remaining effect is blocking other structures on cells no operation can refer to.

**Durability is a host concern.** `Loaded` exists for a host that restores its own instances; a host
that also wants durable claims re-settles its restored instances and the planner re-derives the rows.

### D-45 — A claim belongs to an instance

`ClaimRow` carries `instance` instead of `owner`; `SettleRequest` is `(instance, rows, expectedRevision)`;
`ClaimLedger.release` takes an `InstanceId`; `ClaimMatrix` conflicts only between different instances.
`OwnerId` stays on `StructureSpec`, on `MatchRequest` and in the manager's reservation lookups, and
`/cumulus claims` resolves an owner through the manager, printing `unknown` when the instance is gone.

**A move is a removal.** With the instance as the key there is no second identity to reconcile when a
structure is pushed: the adapter invalidates it and releases its rows, and a rebuild is a fresh settle.

### D-47 — A block change is observed at the write, not at the intent

The adapter learns that a block changed by hooking the one method every server-side write goes
through — `LevelChunk#setBlockState` — instead of subscribing to a list of NeoForge events. The
callback carries the old and the new state, the position and the `isMoving` flag, and it fires only
when the write actually changed something: a `null` return is a no-op.

**Why the event list was wrong.** A pre-event describes an intent: it fires before the write, it may
be cancelled, it names a direction rather than a result, and it exists only for the paths someone
remembered to cover. Fire, fluids, `/fill`, `/clone`, another mod's `setBlock` and a wither had no
event at all, and a cancelled break still deleted the instance.

**A piston is not a special case.** A piston move is a batch of block writes with `isMoving` set, so
the funnel sees it like any other change and reports the affected instances as gone. The adapter does
not reconstruct a translation: a structure a piston pushes is no longer formed, its claim rows are
released, and the player settles it again at its new position.

**The instance side is indexed.** A per-level `Map<chunk, Set<InstanceId>>` is maintained by the
manager wrapper, so the hook's first three checks are constant-time and a write that touches no
formed structure costs one hash lookup. A block-entity `setChanged` feeds only the capture watch: a
content change never invalidates the instance itself (D-41).

**Costs.** Mixin becomes a load-bearing dependency of the adapter; the hook sits on a hot path and may
only do a hash lookup before returning; the injection points must be re-verified on every Minecraft
update. The kernel is unaffected — every hook lives in the adapter source set.

### D-48 — A siting holds one instance, so a re-settle replaces it

One siting names one structure position, so the ledger never treats the incumbent rows of the same
siting as a conflict: `settle` drops every row of the settling instance, and every row of another
instance whose siting equals one of the incoming rows, before it runs the matrix. The replacement,
the conflict check and the write stay inside the one compare-and-set settle already performs, so a
failed settle leaves the previous rows in place.

**Why the ledger and not the host.** The host can filter its check view (`StructureSetHost` already
does), but `settle` re-reads the ledger, so a replacement decision made in the host is invisible to
the matrix. Encoding it in the ledger makes the rule hold for every host, including a bare
`/cumulus settle` that passes no view at all.

**Why the siting and not the owner.** D-45 removed the owner from the claim model: `(owner, siting)`
was a proxy for "one instance", and the owner dimension only turned a re-settle by another owner into
a conflict instead of a takeover. The siting is the position; the owner is a host reservation fact.

**Rejected.** Teaching `SettleRequest` a `replaces` list: a public record change for a rule the
ledger can derive from the rows it already holds. Releasing the old rows in the host before settling:
two writes with no transaction, and a failed settle would have dropped them. Adding a conflict
exception flag to `ClaimMatrix`: the matrix compares instances and must stay a pure predicate.

**Consequence:** `InMemoryClaimLedger.settle` excludes the replacing rows before `conflicts`;
`claim` §4.5 states the rule; the three same-siting GameTests pass again.

### D-63 — A document declaration the compiler ignores is an error

Four shapes used to be accepted and dropped:

- a bay that no check names — `Blueprint.bays()` is derived from checks, so the bay disappeared, and
  with `envelope: true` its cells fell back into the envelope and had to be air;
- a definition's `arguments` and `extensions` — parsed, then never read;
- a definition that no placement reaches — its §4.2 obligations were never checked;
- an external document used as a placement source with an unsupported `schemaVersion`.

All four are errors now: the first two and the unplaced definition are `SC-DOC-0002`; the external
version is `SC-DOC-0001`. A definition's `bounds` stays required and gains a meaning: its own bays
and seams must lie inside it.

**Why errors and not warnings.** A dropped declaration is invisible in the result: the structure
matches with fewer cells than the author wrote, and the finding that eventually appears points at the
envelope or at a neighbouring bay. Warnings do not survive that path.

**Why not implement the dropped meanings.** A bay no check names has no obligation to state — its
coverage is decided by checks; a definition's `arguments` and `extensions` would need placement-time
semantics no document uses; an unreachable definition has no site.

### D-64 — A key has no default value

`ContextKey` loses `whenAbsent`, the document schema loses the field, and `fold` loses the branch that
wrote a default when no section contributed. A key that nothing contributed is absent; an author who
wants 0 on an empty region contributes 0.

**Why delete instead of completing the decoder.** The document decoder resolves long, int, boolean,
string and id — not `cuboid`, not `cuboid_set`, and not a custom type, which would need a value
catalog of its own. Completing the built-ins would leave the field permanently half-supported, and the
long case uses `Long.MIN_VALUE` as "absent", so `Long.MIN_VALUE` itself is rejected.

**Why the field is nearly dead already.** The shipped computations always answer `Found`: `count`
counts to zero rather than reporting missing. A default is only needed by a custom section computation
that deliberately answers `Missing` for readable sections — and that case is better written as
"answer zero".

### D-55 — The envelope judges cells; entities are opt-in

The derived envelope judges the occupant stored at each cell: block state, fluid and block entity. An
entity covering an envelope cell is transparent by default; a blueprint that wants entities rejected
sets `envelopeEntities`, which also adds the entity kind to its read scope so the host captures them.

**Why not "whatever the host captured".** Read kinds are the union over every check, so the same
envelope could start rejecting animals after an unrelated check declared the entity kind. A check's
verdict must not depend on its neighbours.

**Why entities are opt-in.** An envelope is a claim about blocks. Animals wander and players walk;
failing every large structure while a mob is inside makes the feature unusable and forces an entity
scan the author did not ask for.

**Why a second read.** `read(cell)` answers the body covering the cell, which would hide a foreign
block under it. `cellOccupant(cell)` answers what the cell stores; a host that captures bodies must
answer both, and one that cannot answers "undecided" rather than guessing.
### D-57 — A blueprint is refused only for conflicts it cannot outgrow

The build refuses two bays of one blueprint whose owned cell sets are known at build time and
intersect with an exclusive side. `Box` owns its cells and `Declared` owns the set written in the
document; both transform rigidly with the siting, so the overlap holds in every world, every origin
and every orientation. `Bound` is excluded: its owned set is the union of the subjects read at
settle and may be smaller than its bay, so a static answer would refuse blueprints that resolve
cleanly.

The remaining cases are decided where the cells exist:

- a `Bound` overlap is refused by the planner, which scans its sorted rows and rejects a cell held
  by two bays when either side is exclusive;
- two instances never share a key, so their conflicts stay the ledger's job.

Replacing the old rule changes three cases. The old rule compared bay cells, not owned cells, so it
refused a `Declared` bay whose checked cells overlapped another bay while its owned cells did not,
and it treated a `Bound` bay's cells as if they were owned. It also ignored `Declared` against
`Declared`, which the owned-set rule now refuses.

### D-59 — A contributed key is complete or absent

A section contribution whose value does not match the key's type marks the key absent. The engine
still reports the defect and never folds the remaining sections. A `merge.combine` that throws, or a
merged value of the wrong type, marks the key absent the same way. No partial sum is published.

Whether the attempt forms is a separate question: a check that requires an absent key answers
`Unreadable`, so the attempt is `Undecided`; an attempt that never reads the key may still form and
carries the error finding. That matches the engine's existing error semantics — `budgetExhausted` and
`foldRefused` are errors too, and they produce `Undecided`, not `Failed`.

**Why keep the finding.** The defect is in extension code, and the finding is the only channel that
tells the author. Bare absence would read as "the computation chose not to produce a value".

**Why not `Failed`.** A broken computation that no check reads does not make the structure wrong;
failing every attempt would turn one extension bug into a global outage.

### D-60 — Dropping a key is a release; a settle that changes nothing is not a write

When the planner derives no rows, the engine releases the instance's key instead of settling an empty
request. `SettleRequest` therefore rejects an empty row list in its constructor: "own nothing" is a
release, not a degenerate settle, and no host has to implement that edge case.

`settle` compares the resulting ledger content with the current content and writes only when they
differ. A settle that changes nothing answers `Settled(revision, rows)` with the revision unchanged.
`release` already behaved this way.

Revision therefore means "the content version", not "the number of writes": it changes exactly when
the rows a reader can see change. CAS stays first — a stale `expectedRevision` is refused even when
the content happens to match, because the caller's view is stale and it must re-read.

**Why release and not an empty settle.** An empty request makes correctness depend on every host
implementing "empty means clear" the same way; a host that reads it as "nothing to do" keeps stale
rows forever. `release` already means "drop this instance's rows" and is implemented once.

**Why idempotence is safe.** No production path branches on "the revision changed"; only tests count
bumps. A host that still bumps on a no-op stays correct — it only invalidates readers it need not.

**Supersedes:** the zero-row half of D-42's write rule — exactly one compare-and-set per settle still
holds for every settle that happens, but a settle whose plan owns nothing no longer happens; it is a
`release`.

### D-66 — A rejection carries its code

The builder and the planner refuse with a kernel-internal `IllegalArgumentException` subtype that
holds the `CodeEntry`; only its owning package constructs it. Callers read `code()` and never parse
the message. The message stays the English diagnostic the finding carries today, prefixed with the
code for compatibility with existing assertions.

**Why.** The code used to be the first token of the message. Rewording a message — a translation, a
shorter sentence, a reordered clause — silently changed which code the author saw.

**Why a subtype and not the message.** The rejecting site and the reporting site sit in different
packages, so the type is visible kernel-wide while its constructors stay package-private; the
documented API keeps throwing `IllegalArgumentException`.

**Localization.** `CodeEntry` already carries the localizable surface: `summary` and `body` in
`Explanation`. Per-instance details stay English, like every other finding message in the kernel; a
full localization layer would need templates and arguments and applies to all findings, not just these
two rejection paths.

**Consequence:** the compiler catches `BlueprintRejected`, the engine catches `PlanRejected`, and an
`IllegalArgumentException` that is neither stays a programmer error. `DocumentCompiler.codeFromMessage`
and `MatchEngine.plannerFailure`'s message dispatch are gone; the planner's refusal sites carry their
`CodeEntry` directly (`SC-SPC-0001`, `SC-CLM-0001`, `SC-CLM-0004`, `SC-CLM-0005`).

> Numbering note: D-46 is still a proposal carried by
> `Data/StructureCumulus/plan-2026-11-01/`; it enters this file when its code lands.

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
- A structure set is not atomic across structures (D-32): a later settle failure releases the
  earlier claim rows in reverse order and invalidates their contexts through
  `StructureEvent.Broken` (D-35). `StructureSetResult.rolledBack` names the releases that answered
  `Settled`; a failed release or dispatch is a `SC-SET-0007` rollback failure, and a failed release
  does not skip the invalidation.
- The set shares one `Budget` across presence and every member attempt (D-35): presence charges
  `(1, 0)` per read, an attempt sees what remains, and an exhausted attempt stops the set. A host
  that wants an independent ceiling per structure must run one set per budget.
- A satellite may not share the root siting (D-35): the compiler, `StructureSet.place` and the host
  each reject it with `SC-SET-0008`; a satellite at the root origin with a non-identity orientation
  is legal.
- `MatchRequest` carries an `InstanceId` before a match exists; a host that previews many candidates
  must allocate ids it never uses. This is a host counter, not a kernel resource.
- `ContextStore.publish` is total and single-writer; a host that publishes from two threads violates
  the contract. The in-memory store synchronises, but a `SavedData` store must be server-thread-only.
- The document schema's `Value` arguments are untyped at the boundary; a factory is the only place a
  type is enforced. A bad argument is `SC-DOC-0002`, not a compile-time error.
- A trait capability value is the live handle, not a snapshot (D-34): a consumer that keeps an
  `IItemHandler`/`IEnergyStorage`/`IFluidHandler` past the lease's validity boundary observes later
  world mutations. The lease is the only validity boundary the framework offers; a check that needs
  captured contents must copy them itself inside the lease.
- A block state whose block entity was not captured answers every registered block trait with
  `Refused(notCaptured)` (D-34), even a custom trait that reads only `BlockState`; that is the price
  of never letting `BlockCapability.getCapability` fall back to a live `Level.getBlockEntity`.
