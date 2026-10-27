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
prove, while the ports make the adapter a later, independent exercise. Current status (2026-10-27):
M1 (host + command + capture-check), M2a (loaded-entity capture), M2b (upright orientation), M2c
(Model C, body identity, total read, four-state traits and the five adapter trait keys) and M2d
(nested extents, frames, cuts, child entities) are committed. M3 is committed at `d4b6d82` —
`SavedClaims`, `SavedContexts`, CAS settle/release and reload codecs — and is compatible with Model C;
its settle command and lease invalidation are still missing. M4 (JSON/datapack, the remaining
commands), `SC-MC-0004`/`0005` emission, non-upright pitch/roll state mapping, window/mark-counter
lease invalidation and the deferred `SC-MC-0003` channel remain pending.

**Consequence:** the kernel tests use an in-memory `WorldView`, `ClaimLedger` and `ContextStore`; the
adapter is implemented through M2d, with M4 still missing. The spec index is green; no pending
signature is documented as implemented (`06-verification.md` §1).

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

### D-17 — The facade is five operations, one final class

`StructureKernel` is a final class with `compile`, `siting`, `check`, `settle`, `derive`; `solve`,
`explain`/`explainOrFallback` are helpers; `document`/`ledger`/`catalogs` are lookups. It is not an
interface and has no static-method-in-interface factories. `Host` is one record of ports. `Catalogs`
has six families; there is no `contextKeys` catalog, because a key is a blueprint/document value.

**Why:** the old facade had nine operations, four of which did not exist, an interface with static
factories, a `Host` with a segment library, and a key registry that forced a `context → spi` edge.
The new shape is one class a host can hold and an addon can ignore.

### D-18 — Trait and kind are open values

`OccupantKind` is a `NamespacedId` record with four constants, not a five-case enum; there is no
`OTHER`, no `compareTo` by ordinal. `OccupantDescriptor` is `(type, kind, properties, tags)`.

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
| Q14 facade | Five operations, one final class, one `Host` (D-17). |
| Q15 code table | One table in `finding`, one producer, no dead codes (D-19). |
| Q16 `LocusMap`/`definition` | `LocusMap` maps local↔world only; a definition label is created where the source is in hand; no `toDefinition`. |
| Q18 U10 | Thin values + provider (D-16). |
| Q19 named-but-unimplemented | `DataView`/`DataKind` → `Value`; `Codes` implemented; `Checks` in core; `Stability`/`ExplainBook` deleted; no empty names. |
| Q20 trait model | Typed `Trait` with present-null (D-18). |
| Q21 descriptor | `(type, kind, properties, tags)`. |
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
rejection is covered by `ReadModelTest`/`StructureValidationTest`.

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
`StructureBlockEntity` special case, no template read and no adapter-owned source registry. The
adapter queries only the four-argument block overload with the already-read state/block entity,
skips a block-entity state whose block entity is null, and queries an entity with
`entity.getCapability(ENTITY, null)`; one result per cell, first provider wins, block source over
entity source, no merge or conflict detection. A provider value is a declaration; the adapter owns
validation, frames, depth, parent, no-chunk reads, cycles, budget, refusal and the child view.

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
`(type, origin)` on the open frame stack (cycle), or an unloaded chunk — is **cut**: the nested body
stays in the parent, no child exists, `nested(cell)` returns `null`, and the consumer reports
`SC-OCC-0003`. The adapter does not fabricate a refused child or an empty child view. `SC-MC-0003` is
reserved/deferred: this round has no capture-finding channel, so the adapter does not produce it and
it never enters `Codes.all`. A child whose bounds leave the build height is not cut: the child
exists, its out-of-range local sections are `Refused`/`Partial` (never air), and each refused cell is
visible through the read. There is no partial child: a child either exists with its sections answered
(readable or refused) or is cut entirely. **Status (v1.2):** implemented in
`f3e0338`/`d6aefc2`; `SC-MC-0003` remains deferred.

**Why:** a refused child is a second structure that checks could walk and pass; the honest fact is
that the child is not available. Keeping the body and returning `null` preserves the parent-frame
occupant while forcing every consumer onto `SC-OCC-0003`. Reporting the cut through a code the
adapter cannot actually emit would be a fabricated finding.

**Consequence:** the cut reason is host-side state, not a finding payload; a future capture-finding
channel can promote `SC-MC-0003` without changing the kernel. A consumer that treats `null`
`nested(cell)` as an empty structure is wrong, and a child build-height refusal is a read refusal,
not a cut. `SC-MC-0004` is outside this decision: it remains an orientation mismatch, not a
`ReadRefusal`.

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
- A relayed host code has no `CodeEntry`, so its severity is the finding's, not the registry's; the
  trust guard only prevents impersonation of a kernel code, not a misleading host code. A report
  site should keep the refusal's status in the note.
- The nested source is a third-party capability; the adapter's block-over-entity precedence and
  first-provider-wins rule are policy, not a merge. A mod that wants two nested bodies at one cell
  cannot express it (D-26).
- Model C and the nested capability are implemented and the spec index is green; the remaining gaps
  are M4, non-upright tilt, `SC-MC-0004`/`0005`, lease invalidation and the deferred `SC-MC-0003`
  channel (D-02).
- `MatchRequest` carries an `InstanceId` before a match exists; a host that previews many candidates
  must allocate ids it never uses. This is a host counter, not a kernel resource.
- `ContextStore.publish` is total and single-writer; a host that publishes from two threads violates
  the contract. The in-memory store synchronises, but a `SavedData` store must be server-thread-only.
- The document schema's `Value` arguments are untyped at the boundary; a factory is the only place a
  type is enforced. A bad argument is `SC-DOC-0002`, not a compile-time error.
