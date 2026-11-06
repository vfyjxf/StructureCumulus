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
producer table in `finding.md` names the owning module and every emit site of the code's one
meaning (D-69). An unproduced code is deleted, not
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
| Q15 code table | One table in `finding`, one meaning per code with every emit site listed, no dead codes (D-19, D-69). |
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
explains the cut and the check finding judges it. **Superseded by D-65:** nested expansion is now
automatic, breadth-first and capped — the author no longer declares a depth, the frame at the
ceiling keeps and reports the bodies it discovers, and the cut rules this decision sketched are
owned by D-65's wording.

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
  `FluidState` arrays and captured entities; no live `Level` is retained and no block entity is
  copied (D-61);
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
kernel/adapter tests are green; `./gradlew check` and `runGameTestServer` are green on this tree (541 kernel / 65 containers, 440 adapter / 46 containers, 140/140 GameTests; check exit 0).

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
**Superseded by design 13 (N6):** the capability-only `NestedExtentAccess` is replaced by the
capability/attachment split — `CapabilityNestedExtentAccess<H, C>` passes the capability's own
context and `AttachmentNestedExtentProvider<H, T>` adds the attachment source — while the holder
tokens, the holder-keyed lookup and the standard block/entity declarations survive
(`13-nested-extent-access.md`).

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
`StructureLifecycleAdversarialGameTest`; `./gradlew check` and `runGameTestServer` are green on this tree (541 kernel / 65 containers, 440 adapter / 46 containers, 140/140 GameTests; check exit 0).

### D-41 — The snapshot carries values; a live handle is a contract, not a guard

The capture freezes the values the framework owns and states one contract where it does not.

**The contract.** `check` is pure: it writes nothing — no claim, no frame, no world edit — and that
includes writing through a host value. Determinism is promised for a check that reads only captured
values; a check that dereferences a live host value accepts that its verdict may differ between runs.

**The values.** Captured cells are frozen: `BlockState`, `FluidState` and the occupant walk are
copies. A block entity has no copy at all — its data reaches a check through a trait whose value is
the live handle (D-61). The three capability traits carry the live NeoForge capability
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
rollback (`StructureSetHost` settles every member, then commits; a failure releases, it never
restores, D-79). A single
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

### D-46 — Per-level state is created by the level lifecycle

The adapter's per-level state is created when a level loads and dropped when it unloads. No read path
creates it, and no map is keyed by a level object.

**A deletion must not be undoable.** `ServerState.store(level)` was a `computeIfAbsent`, so any later
read could recreate a store that `ServerState.unload` had just removed — which is what
`WatchEvents.levelUnloaded` did, dispatching `Unloaded` into the fresh empty manager. The fix removes
the ability instead of reordering: creation happens in `openLevel`, driven by `LevelEvent.Load`, and
every accessor is a read that answers "no state" rather than building one.

**One handler per transition.** `LevelEvent.Unload` is handled by `ServerState.unload` alone: it
dispatches `Unloaded` to the store that still exists, then closes the store, closes the capture and
forgets the watcher. `WatchEvents` keeps no unload handler. Handler order between mods is not a
contract, so no behaviour may depend on it.

**The key is the dimension.** Both per-level maps are keyed by `ResourceKey<Level>`
(`ServerLevel#dimension()`), never by the level object: an object key pins an unloaded level and,
after a reload, gives one dimension two entries. `LevelWatch` keeps its `LevelAccessor` entry points —
its events arrive as `LevelAccessor` — and derives the resource key internally rather than narrowing
its signatures to `Level`.

**Rejected:** moving the dispatch into `ServerState.unload` while leaving the lazy creation — it fixes
this occurrence, not the class, and any future listener can resurrect again. Keeping `LevelAccessor` as
a key and passing the resource key separately — the map still pins. An attachment on the level is the
better long-term shape, but it restructures every per-level accessor and belongs with the claim work,
not with a P2 lifecycle fix.

**Consequence:** `ServerState` gains `openLevel`, accessors become reads, `LevelEvent.Unload` has one
producer, `WatchEvents` loses its dispatch, and the PMD rule `NoLevelAsCollectionKey` goes from three
hits to zero.

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
formed structure costs one hash lookup. A block-entity `setChanged` fed only the capture watch, never
the instance side (D-41); D-61 removes that feed together with the block-entity fingerprint it
guarded.

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

### D-49 — Composing a siting applies the root offset once

`Siting#toWorld(p)` is `origin + orientation(p) + offset`. Composing a satellite onto a root must
place it exactly where `root.toWorld(satellite.toWorld(p))` puts it.

The old `compose` counted the root offset twice: `origin` already carried it through `toWorld`, and
`Placement#compose` carried it again into the composed placement, so the result was `+2·t_root`.

The fix keeps `origin = toWorld(relative.origin)` and composes the placement from a rotation-only
root placement, giving `(origin_P, (o_P∘o_R, o_P(t_R)))` — one copy of the root offset, the one
inside `origin`.

`Placement#compose` is unchanged: it composes two transforms, and a transform includes its own
offset. A non-zero placement offset stays legal for a host.

Nothing observable changes while every siting is built by `Siting.of(origin, orientation)`, whose
placement offset is zero; the defect is fixed in the kernel because a host can construct the other
form.

### D-50 — A region is its cells; the box list is a decomposition

`CuboidSet` holds a decomposition of a cell set. Which decomposition it holds is an implementation
detail: equality and hashing already compare cells, and no consumer may use the box list as an
identity or an ordering key.

**Ordering is content-based.** Two regions compare by bounds, then cell count, then the first cell
where they differ; the common case never enumerates cells. A decomposition that happens to differ
therefore cannot reorder a finding.

**The box list is renamed `decomposition()`** and documented as a decomposition, not a canonical
form. `firstCell` already answers the region's smallest cell and needs no change.

**Text is not specified.** Rendered finding text is debug output; the working error surface is the
visual layer, so the text keeps whatever the current renderer produces.

**Rejected: canonicalising the decomposition.** It forces a choice between a unique form that is not
minimal (staircase families blow up to one box per cell) and a minimal one that must enumerate cells,
and it makes every construction pay for a property only the ordering actually needs.

### D-51 — A context key is identified by (id, type), not by its whole record

`ContextKey` carries five components, but only two of them name the slot: `id` and `type`. `merge` and
`whenAbsent` describe how the slot is filled; `retention` says when it is readable. Two keys with the
same id and type are the same slot even when their merge functions differ, and the merge function is
compared by reference on purpose — a lambda cannot be compared by value.

**Equality stays the whole record.** `equals` compares every component so that a blueprint built twice
stays equal only when the derived content is identical (I-13), and so that a duplicate key is a real
duplicate rather than a silent merge.

**Hashing and lookup use `(id, type)`.** The record's generated `hashCode` mixes in a `Class`, an enum
and a lambda, so it varies across JVMs and across two equivalent lambda sites. That broke two things at
once: a `HashMap`-backed lookup missed a key the `TreeMap`-backed one found, and `HashSet` iteration
order leaked into results. `ContextKey#hashCode` therefore returns a hash of `(id, type.getName())`,
which keeps the `equals`/`hashCode` contract (equal objects still hash equally) and makes every
identity-hash dependency disappear.

**One canonical order.** `ContextKeys.order()` sorts by `(id, type.getName())` and is the only order a
`ContextKey` set may be iterated in; the comparators that re-implemented it are replaced by it.

### D-52 — A ref read answers the occupant, not its cell

`WorldView.get(ref, type)` answers for the occupant `ref` names. A ref without a body names a cell:
the default reads `ref.anchor()`. A ref with a body names one body: the default answers `null`, and a
host that stores values per body overrides `get(ref, type)` to answer by body. A ref from another
snapshot or frame answers `null`, exactly like `occupant(ref)`.

**Why.** `OccupantRef` has carried `body` since the entity-collapse fix, and `occupant(ref)` already
rejects a foreign frame and a foreign body. `get` was the one read entry that ignored both: two
entities over one cell made `get(cow.ref(), entity)` answer the chicken. Widening the key is enough;
no new port is needed.

**Fail closed.** A default that falls back to the anchor cell would answer a different body's value
silently — the defect it removes. `null` is the honest "not captured", and `world.md` §4.11 already
says a missing value means "not in this snapshot", never a substitute.

**Rejected: `HostType` gains a scope (cell or body).** One dispatch flag on every host token would
select between two code paths, while the ref already carries the discriminator (`body`).

**Rejected: hosts answer bodies through `get(LatticePoint, type)`.** A cell does not name a body; the
occupant chosen for a cell is a heuristic, not the identity the ref carries.

**Consequence:** the kernel default guards snapshot, frame and body; `Expectations.host` reads the
subject's ref; `SnapshotLevel` indexes entity types by body and answers the ref form, `LevelSnapshot`
routes a no-body ref to its cell; `world` §4.4/§4.11 and `10-host-data` §3/§4 state the rule.

### D-53 — A conflict is data; the engine writes its finding

`ClaimVerdict.Conflict` carries the incumbent rows and nothing else. The ledger detects a conflict and
answers which rows contest the request; it does not choose a code, a phase, a locus or a sentence.

The engine already builds that finding for its pre-settle check (`MatchEngine.conflicts`), from the
requested rows and the held rows. The ledger's copy was a second implementation of the same text, and
a host implementing `ClaimLedger` had to reproduce it a third time.

**A ledger is a store, not a reporter.** With the finding removed, `InMemoryClaimLedger` loses its
dependency on `Finding`, `Codes`, `Phase`, `Label` and `Locus`, and the port can be implemented from
the claim types alone.

**Rejected: keeping the findings as an optimisation** (the ledger already has both row sets, so
building the finding there is "free"): it makes the wording part of every implementation instead of
one place, and the engine has to run the same builder anyway for its pre-check.

**Rejected: a middle `ConflictFacts` type**: it would be a second representation of the same two row
lists, with one consumer.

**Consequence:** the settle verdict renders through the same builder as the check prediction and the
`RevisionMoved` re-read, so both entries produce one wording; the D-76 host law keeps its data half
only — a conflicting settle answers the incumbent rows and writes nothing — and the finding half is
covered by the booted host's check path (`CheckClaimsGameTest`: a real `check` over the shipped
ledger answers `SC-CLM-0001`) and by the kernel-level equivalence of the two engine entries
(`theSettleConflictRendersLikeThePreCheck`); `claim` §1/§4.5/§4.6, `engine` §7.5, `finding` §6 and
`06-verification.md` §3.8 state the rule. **Status:** implemented
with the tests that pin it: `aConflictCarriesNoFinding`, `aConflictKeepsEveryIncumbent`,
`aConflictWithNoIncumbentIsRejected` (kernel claim) and `theSettleConflictRendersLikeThePreCheck`
(kernel engine).

### D-54 — The command boundary is gated, guarded and self-cleaning

`/cumulus` requires `Commands.LEVEL_GAMEMASTERS` (2) on the root literal. Every verb inherits it and
none opts out. Each verb body runs inside one guard that catches `RuntimeException` and
`StackOverflowError`, logs the failure at ERROR with the verb, answers
`cumulus <verb> failed: <type>: <message>`, and returns 0. A verb that opened a draft closes it in a
`finally`; a settle that wrote rows and then failed to commit releases that instance's key.

**Why the root and not the verbs.** Every verb captures a region on the server thread, and one
capture may read up to a million cells. `explain` is a debugging aid for the authors who write
blueprints, not a player feature; a per-verb gate would only mean answering the same question again
for each verb added later.

**Why catch `StackOverflowError`.** It is the one error a command body could raise from document data
(K10-D09), and an uncaught error reaches the tick loop. The expansion budget removes the cause; this
is defence in depth. No other error is caught: an `OutOfMemoryError` leaves no safe state to report.

**Why the draft closes in `finally`.** `begin` reserves `(world, owner, siting)` until abort or
commit, so an exception between them locks that spot until the level unloads. A settle that formed
and then threw leaves rows with no instance; releasing that key on the way out is the only cleanup.

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

### D-56 — A publish is a new table, never a merge

`ContextStore.publish(instance, values, findings, spend)` stores exactly `values` and returns a
`StructureContext` built from scratch: a fresh immutable copy, `revision + 1`, this bundle's findings
and the cumulative spend. The previously published object is not touched; a host that still holds it
keeps the old table.

`derive` therefore builds its whole table before publishing: take the base values, drop every key the
blueprint declares as a derivation output, then add what this pass produced. A `Missing` output is
absent, not stale.

`DeriveContext.base()` is removed: it exposed the previous revision, the one thing a derivation must
not read, and nothing calls it.

**Why replacement.** `context.md` §4.3 says a missing derived value means the output is absent. A
merge cannot express absence, so a derivation that stops producing a value leaves a stale one that
the next reader takes for current.

**Why a new object each publish.** A published context is a snapshot: the instance holds the latest
one and a request may carry an older one as `CachedContext`. Publishing into a shared map would
change yesterday's snapshot under its holder.

**Why not a `removed` parameter.** Two ways to say "delete these keys" for one caller, and every host
implementation would have to support both. Replacement makes the argument the whole truth.

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

### D-58 — A registry reference names its namespace and must resolve

A factory argument that names a registry entry — `block`, `fluid`, and a tag id — must carry a
namespace, and an unknown entry is a compile finding (`SC-EXP-0005`). A bare `"stone"` is refused with
a hint naming `minecraft:stone`; it is not silently rewritten into the mod's namespace.

**Why no default namespace here.** `NamespacedId.parse` fills `structurecumulus:`, which is right for
document-local ids (a bay, a check, a key) and wrong for registry entries: every vanilla block would be
spelled `minecraft:x` anyway, and a bare id silently becomes a lookup in a namespace no registry
uses.

**Why an unknown id is an error.** Both registries are defaulted: `BLOCK.get` answers air and
`FLUID.get` answers the empty fluid, so a typo compiled into "expects air". For a fluid the mistake is
worse: the empty fluid's block form is air, so "expects water" matched air. The two `== null` guards
were dead code.

**Tags stay unresolved.** A tag may be declared by a datapack that loads later, so an unknown tag is
not a compile error; it matches nothing at match time. This is the one place a document names a
registry object without resolving it.

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

### D-61 — Block entities are read live through traits; the snapshot keeps neither a copy nor a fingerprint

A capture copies no `BlockEntity` and records no fingerprint of one. The block entity host read goes
with the copy: nothing in production read it, and a snapshot that hands out a detached world object
is what D-41 forbids. Block entity data reaches a check the way every other capability does — a trait
resolved at capture, whose value is the live handle (D-34).

Watching contents would contradict that contract. A live handle answers "as of the moment you read";
an NBT fingerprint answers "did this change since capture". Keeping both means an attempt can be
dropped for a change the live read already saw, and the fingerprint costs a full NBT serialization
per block entity on every capture and every freshness check.

What the freshness check still covers: the watched chunks (block changes and unloads), the entity
fingerprints and the level epoch. A block entity that changes or disappears without a block change no
longer invalidates the snapshot; the captured handle keeps answering its own state.

**Consequence:** `HostTypes.blockEntity`, `copyBlockEntity` and the `BlockEntityFingerprint` watch
are gone; `isCurrent()` marks, epoch and entity fingerprints only. D-47's `BlockEntity#setChanged`
hook is cancelled by this decision — the feed existed only to invalidate on contents, so the mixin,
`CumulusHooks.blockEntityChanged` and the eager-mark test go with it, while D-47's
`LevelChunk#setBlockState` funnel stands. `world` §4.7 and `adapter` §3/§4 state the rule;
`10-host-data` drops the copy step, the host token and the fingerprint from its contract.
**Status:** implemented with the tests that pin it: no `BlockEntity` or `CompoundTag` field on
`SnapshotLevel`, a content change or removal without a block change keeps the lease current, and the
`inventory` trait still reads the changed contents.

### D-62 — An expectation declares its read scope when it is built

`Expectations.custom` gains a four-argument form carrying the expectation's `ReadScope`; the
three-argument form keeps working for expectations that read nothing. An expectation written as a
class overrides `Expectation.reads()`.

The kernel cannot infer the scope: the body is an opaque function, and the `H` of a host read is
never inspected. The declaration is the only signal, and it has to exist before capture, because the
host builds its `SnapshotPolicy` from `blueprint.reads()`.

**Why not `ExpectationMeta`.** Meta answers "what is this unit": its id orders the plan and its
`requires` gates context reads. A read scope is a different question, and folding it in would change
the identity and ordering of every meta.

**Rejected: recording what an expectation actually reads.** Capture happens before any expectation
runs. A runtime record answers a question that no longer matters, and it would need a hook on every
read call.

**Rejected: dropping the three-argument form.** A read-free custom check would have to pass
`ReadScope.none()` for no gain; the two-argument distinction is the useful one, and the overload
makes "I read nothing" the default rather than an omission.

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

### D-65 — Nested capture is automatic, breadth-first and capped

A blueprint says whether it reads nested bodies, never how deep. The capture expands them by level:
every body discovered in the frames of level N is captured before any frame of level N+1. Expansion
stops at a fixed ceiling of 16 levels, at the capture's element budget, at a cycle, or when a body
does not fit its parent frame.

A body that is discovered but not captured stays in its parent's occupant list with its footprint, and
the capture reports `SC-MC-0003` at that anchor. "There is something here, I did not expand it" is a
fact the author can act on; silence is not.

**Why no declared depth.** The number is unguessable for the author and unverifiable for the
framework: an opaque expectation can go arbitrarily deep. A ceiling plus a budget bounds the same cost
without asking a human to estimate it.

**Why breadth-first.** With a budget, depth-first spends everything on the first chain and leaves its
siblings uncaptured. Level order gives every sibling the same chance, and the cut set stops depending
on which body happened to be visited first.

**Why 16.** A structure nested sixteen deep is already pathological; the ceiling exists to make
recursion terminate, not to shape normal content.

**Consequence:** `ReadScope` loses `depth`/`withDepth`; a nested focus declares only the `nested`
kind. `SnapshotPolicy.maxDepth` stays as the host-allowed ceiling, `minimal()` and the command policy
pass `SnapshotPolicy.defaultMaxDepth` (16). `LevelCapture` captures each frame alone (`collect`) and
a level-order driver expands and assembles the frames bottom-up; the frame at the ceiling still
discovers its bodies and each is reported as a depth cut. `world` §4.6/§4.9, `structure` §4.4/§4.5
and `adapter` §3/§8.4 state the rule. **Status:** implemented with the tests that pin it: a
seventeen-level nest captures sixteen frames and reports one depth cut at the deepest anchor, both
siblings are captured before any grandchild under a shared budget, a policy without the `nested`
kind queries no provider, the ceiling frame queries its anchor exactly once and builds no child, and
two captures of one world produce the same frame order and the same cut report.

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

### D-67 — A compile finding always points somewhere, and a different message is a different finding

`DocumentCompiler` attaches a primary label to every finding it reports. When a declaration has no
source span and no cell locus, the label covers the blueprint's own bounds — "somewhere in this
blueprint" is still a place, and it is honest about what the compiler knows.

`FindingBundle` adds the message to its dedup key. Two findings that differ only in the sentence they
carry are two findings; before, both were unlabelled and collapsed into one, so an author with two
missing definitions was told about one.

**Why both.** The label alone fixes the law but not the collapse for findings that share a code and a
dissimilar message; the key alone stops the collapse but leaves findings pointing nowhere.

### D-68 — An anchor's value is a heading in the definition frame

`AnchorSpec.value` names the anchor's heading in the blueprint's own coordinates. At check time the
host reads the heading actually present at the anchor cell and asks the orientation policy for the
allowed orientation that maps one to the other. One blueprint therefore matches every allowed turn.

**What was wrong.** The same value was also the expected property value, so the literal comparison
ran before any orientation was considered: placing the blueprint turned by 90° compared the
observed `east` against the declared `north` and failed, and a policy that allowed every turn could
only ever select the identity.

**What it costs.** A document written for the old rule changes meaning: its `value` used to
describe the world orientation it was drawn for. No shipped datapack declares an anchor (`grep`
over `src/main/resources/data`), so the breakage is confined to third-party documents, which the
module docs call out.

### D-69 — A code has one meaning and one owning module; the table lists every emit site

The producer column lists the classes that emit the code, not a single site and not a line number. A
code keeps one meaning; a site that needs a different meaning gets a different code. A code that only
appears inside a programmer-failure exception is marked `T` — thrown, never a finding value — and
still has to be listed with its throw site.

**Why not one emit site.** The same meaning is emitted from two phases (`check` predicts, `settle`
concludes) and from the adapter that relays a host code; forcing one site would split one meaning into
several codes or bend the call graph.

**Why no line numbers.** They drift on every edit; the gate (X03-D04) checks class names, and a class
is a stable anchor for a reader.

### D-70 — The set host documents the shape the code has

`StructureSetHost` takes the manager it works against and one function that reserves a draft per
spec. The module design shows both constructors, the `run(set, lease, owner, budget, drafts)`
signature and a real call site; the instance-supplier sketch and the per-level instance expression
are removed with it.

**Why the function stays.** After D-40 the host owns instance creation, and rollback and commit
verification target the manager the drafts came from. A test that needs to inject a failing draft
wraps the same function; an instance supplier would force every caller through one fixed path.

**What the second constructor buys.** Passing the manager explicitly turns a silent mismatch — a
draft committed into another manager — into a verification step. It stays.

### D-71 — A code may be thrown and never reported

`SC-SPI-0003` is thrown by `Catalog.Builder.put` when one id is registered twice with different
values. It is never a finding: the failure happens while catalogs are built, before any match exists.
The message keeps the code prefix for a human reader, and nothing parses it. The producer table marks
it `T` — thrown, never a finding value — and records `Catalog.Builder.put`.

**Why keep it.** A startup failure still needs a citable number; without one the author reads a
sentence and has nothing to look up.

**Why not delete it (C) or strip the prefix (B).** Deleting loses the citation; stripping leaves a code
that no line mentions, which the "unproduced codes are deleted" rule forbids.

### D-72 — A mod contributes catalog entries through the plugin entry point it already has

`ModPlugin` gains one default method, `register(CatalogRegistrar)`. A plugin that contributes document
kinds overrides it; every plugin that does not inherits a no-op, so trait providers and other existing
plugins are untouched. Discovery, ordering, duplicate detection and failure attribution stay with
`PluginLookup` and `PluginLoader`.

The host calls `register` once per loaded plugin, in the loader's resolved order, while it assembles
`Catalogs` and before any document is compiled. The catalogs are then a frozen snapshot.

**Why a default method and not a new interface.** One entry point means one discovery path, one order,
one failure model. A second plugin interface would re-implement all three and behave differently from
trait plugins in the same host.

**What a plugin may add.** Expectation, focus, computation and deriver factories, plus code entries.
Built-in ids cannot be replaced; a duplicate id with a different value is `SC-SPI-0003`, thrown while the
catalog is built.

**Failures.** A plugin that throws while registering — a `RuntimeException` or a `LinkageError`
from a class it cannot resolve — is a fatal load failure named by its id, and every
plugin that depends on it is skipped — the loader's existing classes, not a new one.

### D-73 — Performance is a JMH number, produced on CI and on the development machine

The kernel's cost is measured by JMH benchmarks in a `src/jmh` source set, run by a `jmh` task. There
is no counting layer, no complexity bookkeeping and no threshold: a benchmark reports throughput and
latency for one operation, and the numbers are compared by a person looking at two runs.

**Why JMH alone.** JIT warmup, dead-code elimination and constant folding make a hand-timed loop
meaningless, and a counter of "how many sorts happened" answers a question nobody asks once the wall
time is known.

**Where it runs.** `./gradlew jmh` on a developer machine and in CI. The build compiles the source set
so it cannot rot; it never runs benchmarks inside `check`, because a shared runner's numbers are for
comparison, not for gating.

**What it cannot measure.** Capture against a live level and anything that needs a server tick. Those
are not benchmarked at all; a change that touches them is reviewed by reading the code.

**Status:** implemented — the `jmh` source set and task, and the six benchmarks of the cost pass
(`06-verification.md` §7); the CI half is deferred until the repository has a remote, so no workflow
file is kept yet.

### D-74 — The adapter joins the signature gate in batches, with a shrinking list

`spec-check` reads `src/main/java` as a second source root and compares the adapter's public types
with the `## 15. Signatures` section in `adapter.md`. Coverage arrives in batches: a type is either
documented in that section or listed in `tools/spec-uncovered.txt` with a reason. The gate fails
when a documented type drifts, when a public type is in neither place, and when a listed type no
longer exists — so the list can only shrink.

**Why not all at once.** The adapter has roughly ninety public types; writing their signatures takes
longer than the gate itself, and a half-written section would produce failures nobody asked for.

**Why a list and not a comment.** A comment rots silently. A file the gate reads forces every new
public adapter type to be a decision, and every batch to remove entries.

### D-75 — Determinism is proven across processes, and the order dependence is removed at the source

Two `Set.copyOf` sites decide the iteration order of a public collection: `SnapshotPolicy.kinds`,
`SnapshotPolicy.traits` and `ComputationSpec.inputs`. They become ordered immutable sets, so the order
is defined rather than inherited from identity hashes.

On top of that, a gate renders one fixed scenario to a canonical text, runs it in three separate JVM
processes, and compares every run with a golden file. The same scenario in one process proves nothing:
identity hashes repeat within a process, so any hash-order dependence passes.

**Why both.** The source fix makes the output correct; the gate makes it stay correct. The gate alone
would pin today's accidental order in a file; the fix alone would regress the first time someone adds a
`Set.copyOf`.

### D-76 — Host laws run on a real server, in the test source set that already has one

The behaviours a host must satisfy — capture honours the read policy, an unreadable cell stays
unreadable rather than reading as air, a nested body is present when it was captured, a settlement
answers with this key's rows and a conflict answers with the incumbent rows and its finding — are
asserted in `src/test` against a server level booted by the existing JUnit-with-Minecraft runner. No
new source set, no dependency from `test` on `kernelTest` output.

**Why not share the suite with `TestWorld`.** Sharing would need a test-to-test dependency and would
still compare a double with a host. The double exists to make kernel scenarios fast; the laws exist to
prove the shipped host, and the shipped host is the one that can boot.

**What happens to `TestWorld`.** Its `View` gains the read-policy filter and the src/test support
ledger returns the requested rows (the shipped ledger already answers so). Its remaining differences
are documented in place; the kernel tests stay scenario tests, not law tests.

**Status:** implemented — `HostLawsTest` (6 laws, `06-verification.md` §3.8) boots one real dedicated
server per class run through `HostServer`; commenting out the entity-kind gates in `LevelCapture`
turns the read-policy law red, and the kernel and adapter suites stay green over the aligned doubles.

### D-79 — A rollback releases what the set wrote; it never restores the previous occupant

When a structure set fails partway it releases the members it already settled and invalidates their
contexts. The previous instance at the same owner and siting — taken over before the members settled —
is not restored. Its rows, its context and its identity are stale from the moment the replacement was
attempted: the blueprint may have changed under it, and after a reload there is no instance at all.

The host re-forms it the same way any structure comes back: by settling again.

**Why not restore.** Restoring means snapshotting every row of the previous instance before the
takeover — including rows outside the new member's bounds — and replaying them through a write path
that exists for that one purpose. That is a second source of truth for occupancy, and it is exactly
where the lost-rows defect came from: the snapshot was taken over the member's bounds while the release
removed the whole instance.

**Law 6 stays absolute.** With the replay gone, `engine.settle` remains the only writer and every row
it writes comes from the planner. D-78 is withdrawn.

### D-80 — A cell that was not captured has no value

`WorldView.get(cell, type)` answers null when the cell lies outside the capture, belongs to a refused
section, or was refused individually. Air is answered only for a cell that was captured and holds air.
`read(cell)` already distinguishes the two: `Found` versus `Unreadable`.

The Minecraft-facing `BlockGetter` accessor keeps answering air, because the game interface it
implements has no null; it serves internal helpers and never reaches a check.

**Why.** The two states are different questions. A check that asks "is this stone" and receives air for
a cell nobody looked at reports a mismatch; it should report that it cannot tell, and the engine turns
that into `Undecided`.

**Consequence:** `LevelSnapshot.get(cell, type)` serves only cells whose `read` is `Found`;
`SnapshotLevel.get` guards its own frame bounds, and its air fallbacks stay for the `BlockGetter` face
and the digest. An all-air section was captured and still answers air; an out-of-bounds cell, a refused
section and a refused cell answer null, so `Expectations.host` returns `Unreadable` with the absent
code (`SC-MC-0010`) instead of a mismatch against air. `world` §4.11, `10-host-data.md` §4 and
`adapter` §3.1 state the rule. **Status:** implemented with the tests that pin it: an out-of-bounds
cell, a refused section and an individually refused cell answer null for every host type, a captured
air cell and an all-air section still answer air, the `BlockGetter` face keeps answering air, and a
check expecting stone over an uncaptured cell is `Unreadable`, never an air mismatch.

### D-81 — A plugin class that cannot be loaded is logged and skipped

Discovery catches `LinkageError` around each candidate class, logs the class name and the cause at
error level, and continues. Nothing is added to the loading result: a class that never loaded has no
plugin id and no dependency edges, so the failure record — which names a plugin — cannot represent it.

A plugin that declared a dependency on the skipped plugin still reports a missing dependency. That
message is accurate about what happened to it and misleading about the cause; the module docs say so.

**Why not abort (C).** One broken third-party plugin would take the whole game down; the host already
degrades by skipping, and a skipped plugin surfaces as unknown kinds at document compile time.

**Why not extend the failure record (A).** A record that can hold "an instance or a class name" changes
a public shape for one message that the log already carries.

### D-82 — Gates run on the development machine until the repository has a remote

No CI workflow is added. `06-verification.md` lists the exact commands that constitute a green build:
`./gradlew check`, `./gradlew kernelTest test`, `./gradlew runGameTestServer`, `python3
tools/spec-check.py` and, when a change touches the kernel's hot paths, `./gradlew jmh`.

When a remote exists, a workflow runs that same list; the document is the source of truth so the
workflow cannot drift from it.

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

> Numbering note: D-46's code landed with the baseline before its decision was written down; the entry
> above records it, and the proposal that carried it is
> `Data/StructureCumulus/plan-2026-11-01/05-a06-d03-creation-discipline.md`.
> Numbering note: D-78 was a proposal (`37-law-6-host-may-replay.md`) whose plan was withdrawn before
> landing; it never entered this file, so the withdrawal above closes a proposal, not a recorded
> decision.
