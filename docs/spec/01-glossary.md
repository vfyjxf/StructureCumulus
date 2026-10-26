# 01 — Glossary

> The closed vocabulary. A term is defined here exactly once; a module links to this page
> instead of restating it. The **Type** column is the Java name; the **Prior** column names
> the nearest ArchitectCumulus term when one exists, for readers of the prior art, and is
> not a compatibility promise.

## A. Prose terms that must not be misread

These are the words a reader is most likely to over-read. Each has one allowed reading.

| Term | Allowed reading | Forbidden reading |
| --- | --- | --- |
| **Element** | Any occupant of one or more cells: a block, a block entity, an entity, a nested structure, or a host-defined kind. | "Block". A predicate that says "the block at …" is a bug (R-01). |
| **Controller** | A declared anchor role whose `AnchorSpec` is read at the definition origin. The *compute centre* is resolved from anchor candidates and the `OrientationPolicy`. | A required `Entity` or block entity type. No kernel type names a controller (R-04). |
| **Structure entity** | The *placed* blueprint: a `Siting` + claims + a derived `StructureContext`; represented by a `MatchResult.Formed` plus its `InstanceRef` and `StructureContext`. | `net.minecraft.world.entity.Entity`. The adapter may back an instance with a block entity, an attachment, or nothing. |
| **Stage** | A life-cycle checkpoint with its own `CheckPlan`: `DESIGN`, `PROBE`, `FORM`, `SETTLE`. The engine selects it; an author declares none, and a caller does not choose one. | A tick budget, a build "generation", or a loader phase. |
| **Generation** | Never used bare. Two qualified terms: **data generation** (R-09, external tools emitting documents) and **instance generation** (`InstanceRef.generation`, the re-check counter of a placed structure). | A generic synonym for stage or version. |
| **Match** | One pure evaluation of `(blueprint, snapshot, siting, policy)`. | A fuzzy search, a block-by-block loop owned by the caller, or a world mutation. |
| **Blueprint** | The immutable, validated template the engine matches. It is compiled from a `BlueprintDocument`. | A mutable builder, a world object, or a datapack file. |
| **Snapshot** | An immutable `WorldView` captured at one revision. | A live world handle or a mutable cache. |
| **Claim** | A cell ownership record with a sharing value. | A lock, a chunk claim, or a permission. |
| **Remedy** | A way to act on a finding: `Repair`, `Preview` or `ExplainRemedy`. | A guaranteed fix. Applicability is explicit. |
| **Finding** | One structured report with a stable code, severity, labels and notes. | A log line or an exception. |
| **Kernel** | Everything under `dev.vfyjxf.cumulus.kernel`, with no Minecraft on its classpath. | The mod, the adapter, or the whole project. |

## B. U0 — base (`kernel.base`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Namespaced id | `NamespacedId` | Immutable `(namespace, path)` pair; the only naming type in the kernel. Parsed from `ns:path`, validated, ordered by namespace then path. | `NamespacedKey` |
| Budget | `Budget` | Ceiling for one attempt: cells read and sections copied, nothing else. Immutable; `Budget.of(cells, sections)`. No time, no per-unit price. | `Budget` |
| Spend | `Spend` | What was actually consumed: `(int cells, int sections)`. | `Spend` |
| Cancellation | `CancelToken` | Injected cooperative cancellation handle; `isCancelled()`. Never a thread interrupt. | `CancelToken` |
| Stability | `Stability` | `STABLE`, `EXPERIMENTAL`, `DEPRECATED`; the promise attached to an SPI name. | `Stability` |
| Stage | `Stage` | `DESIGN`, `PROBE`, `FORM`, `SETTLE`; the life-cycle checkpoint the engine selects, with its own `CheckPlan`. | `Phase` |
| Arguments | `Arguments` | Immutable heterogeneous map of **actual argument values**, keyed by `ParameterKey<T>`; the values a segment or computation is called with. | — |
| Parameter key | `ParameterKey<T>` | Typed identity of one **formal parameter**: `(NamespacedId id, Class<T> type)`. | — |
| Source span | `SourceSpan` | `(NamespacedId document, int start, int end)`: half-open character range in one document. Lives in `base` so `space` never depends on `finding`. | — |
| Data view | `DataView` | Format-neutral read interface: `kind()`, `get(String)`, `list()`, `keys()` (`SequencedSet`), `asString/asInt/asBoolean(default)`, `@Nullable SourceSpan span()`. Lives in `base` so `expectation` never depends on `document`. | `DataView` |
| Data kind | `DataKind` | `OBJECT`, `ARRAY`, `STRING`, `NUMBER`, `BOOLEAN`, `NULL`. | — |

## C. U1 — space and orientation (`kernel.space`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Lattice point | `LatticePoint` | Integer `(x, y, z)` in the blueprint's local grid. The origin is the blueprint's declared origin. | `Cell` |
| Lattice key | `LatticeKey` | Packed `long` in Minecraft's bit layout (x:26, z:26, y:12) for map keys; `LatticeKey.of(point)` / `toPoint()`. | `CellKey` |
| Axis | `Axis` | `X`, `Y`, `Z`; the axis an `Heading` lies on. No signed-axis type exists, so an orientation acts on points, not axes. | `Axis` |
| Cuboid | `Cuboid` | Half-open axis-aligned box `[min, max)`; `min == max` on an axis is empty. The only primitive footprint. | `Box` |
| Cuboid set | `CuboidSet` | Canonical, disjoint, sorted set of `Cuboid`s; supports union, intersection, subtraction, translation, volume, containment. A normal form, not a unique decomposition: equal cell sets compare with `containsAll` both ways. Never a voxel enumeration. | `Region` |
| Heading | `Heading` | The six cardinal directions in Minecraft order: `NORTH`, `SOUTH`, `WEST`, `EAST`, `UP`, `DOWN`; `opposite()`, `axis()`, `step()`. | `Facing` |
| Turn | `Turn` | Quarter turn about the Y axis: `NONE`, `CLOCKWISE`, `HALF`, `COUNTERCLOCKWISE`. A generator, not a name for one of the 48 orientations. | `Rotation` |
| Tilt | `Tilt` | The six choices of which axis is "up": `LEVEL`, `PITCH_UP`, `PITCH_DOWN`, `ROLL_LEFT`, `ROLL_RIGHT`, `INVERTED`. A generator. | `Tilt` |
| Flip | `Flip` | Reflection: `NONE`, `LEFT_RIGHT`, `FRONT_BACK`. A generator. | `Mirror` |
| Orientation | `Orientation` | One element of the 48-element group, generated by `Turn × Tilt × Flip` (72 names onto 48 elements); `compose`, `inverse`, `apply(Heading)`, `apply(LatticePoint)`, `all()`; canonical order is turn-major, then tilt, then flip. | `Orientation` |
| Placement | `Placement` | `(Orientation orientation, LatticePoint offset)`: how a blueprint is turned and shifted. | `Transform` |
| Siting | `Siting` | `(LatticePoint origin, Placement placement)`: where a blueprint stands in the world. `toWorld(local)` / `toLocal(world)`. | `Pose` |
| Locus frame | `LocusFrame` | `DEFINITION`, `LOCAL`, `WORLD`, `KEY`: the frames a diagnostic can point into; `KEY` names a value, not a cell. | `DefSpan`/`LocalSpan`/`WorldSpan` |
| Locus | `Locus` | A frame + a non-null `CuboidSet` (empty when the frame carries no cells) + an optional `KeyLocus` + an optional `SourceSpan`; a `KEY` locus carries a `KeyLocus` and no cells, a `DEFINITION` locus requires its span. | `Span` |
| Key locus | `KeyLocus` | `(InstanceId instance, NamespacedId key)`: the one instance key a `KEY` locus names. | — |
| Locus map | `LocusMap` | Converts a `Locus` between the cell frames for one `Siting`; `toWorld`, `toLocal`, `toDefinition`; a `KEY` locus is not converted. | `SpanMapper` |
| Chunk key | `ChunkKey` | `(int cx, cy, cz)` index in a `ChunkGrid`, ordered `(cy, cz, cx)`. Not a Minecraft class. | — |
| Chunk grid | `ChunkGrid` | `(int cellSize, LatticePoint origin)`: a world-framed grid of cube chunks; `chunkOf`, `boundsOf`, `chunkCount`, `chunksOf`. `cellSize` is data; the adapter passes 16. | — |

## D. U2 — occupants and world (`kernel.occupant`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Occupant kind | `OccupantKind` | A namespaced id record with the constants `BLOCK`, `BLOCK_ENTITY`, `ENTITY`, `NESTED`, `OTHER`; the family an `OccupantResolver` serves. `OTHER` carries a host id. | `ElementKind` |
| Occupant ref | `OccupantRef` | Snapshot-scoped handle `(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)`; equality is identity within one capture. | `ElementRef` |
| Occupant descriptor | `OccupantDescriptor` | Version-independent description: `(type, kind, Map<String,String> properties, List<NamespacedId> tags)`. | `ElementDescriptor` |
| Trait key | `TraitKey<T>` | Typed identity of a queryable trait: `(NamespacedId id, Class<T> type)`. | `ElementAspect` |
| Trait | `Trait<T>` | A `(TraitKey<T>, @Nullable T value)` pair carried by an occupant snapshot; a present trait with a null value is a fact, a missing trait is unreadable. | `ElementAspect` value |
| Occupant snapshot | `OccupantSnapshot` | Immutable read model: ref, descriptor, footprint (`Cuboid`), traits, optional `NestedView`. Air is a normal occupant; `null` means "not readable". | `ElementSnapshot` |
| Section slot | `SectionSlot` | A section's answer: `Air(revision)`, `Present(section)`, `Refused(status, code)`; `readable()` is `Air` or `Present`, and a `Refused` slot cannot carry a readable status. | — |
| Section snapshot | `SectionSnapshot` | One frozen section: `(SectionKey key, Map<LatticePoint, OccupantSnapshot> cells)`; `contentDigest()`, `occupantAt(cell)`, `occupants(box, filter)`. | — |
| Occupant filter | `OccupantFilter` | `(type, kind, trait)` predicates for a box query; a `null` component does not constrain. | — |
| Entity window | `EntityWindow` | `(Cuboid cells, NamespacedId type)`: the box one entity type may move in; `MAX_ENTITY_SPAN = 3`; carried in `ReadScope.entities`. | — |
| Entity key | `EntityKey` | `(UUID id, NamespacedId type, long dataDigest)`: one entity's identity in a window diff. | — |
| Nested view | `NestedView` | A child `WorldView` exposed by a nested occupant; `parent()`, `depth()`, `ancestry()`, `occupant(LatticePoint)`. | `NestedElementView` |
| World view | `WorldView` | The kernel's only world window: `SnapshotId snapshotId()`, `SectionSlot slot(SectionKey)`, `@Nullable OccupantSnapshot occupant(LatticePoint)`, `@Nullable OccupantSnapshot occupant(OccupantRef)`, `List<OccupantSnapshot> occupants(Cuboid, OccupantFilter)`, `@Nullable NestedView nested(LatticePoint)`, `long contentDigest(SectionKey)`. | `WorldView` |
| Read status | `ReadStatus` | `READABLE`, `OUT_OF_BOUNDS`, `NOT_CAPTURED`, `DENIED`, `STALE`. `READABLE` with an air occupant is a fact; every other value is `UNREADABLE`, never "air". | — |
| World | `World` | One Minecraft level as the kernel sees it: `record World(NamespacedId id)`, `id` being the level's key (`minecraft:overworld`). The Minecraft side calls this a level (`ServerLevel`, `LevelEvent.Unload`) and those names are quoted as they are, never used as kernel vocabulary. A `World` is not a place: coordinates live in `LatticeKey`, so the same coordinates in two worlds are two different cells. | `dimension` |
| Owner | `OwnerId` | `(NamespacedId owner)`: who holds a claim — a player or a team. The world is not part of it; a claim names its world separately. | — |
| Snapshot id | `SnapshotId` | `(World world, long revision)`; revision is monotonic per world. | `SnapshotId` |
| Snapshot policy | `SnapshotPolicy` | Capture mode, required occupant kinds, required traits, maximum elements, maximum nested depth, staleness tolerance. | `SnapshotPolicy` |
| World window | `WorldWindow` | `(Cuboid bounds, int maxElements)`: what to capture. | — |
| World capture | `WorldCapture` | Host SPI producing a `WorldView` from a `WorldWindow` and a `SnapshotPolicy`; `null` means "cannot serve". | `WorldCapture` |
| Occupant resolver | `OccupantResolver<R>` | Host SPI turning one raw host occupant into an `OccupantSnapshot`; `kinds()` declares the families it serves, `resolve` may decline. | `WorldOccupants` |
| Resolve context | `ResolveContext` | What a resolver may see: `snapshotId()`, `anchor()`, `remainingDepth()`, `policy()`. | — |
| Well-known traits | `Traits` | The kernel's reserved `TraitKey` constants (`ITEM_INVENTORY`, `ENERGY`, `FLUID`, `TIER`); addons may define their own. | — |

## E. U3 — expectations (`kernel.expectation`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Expectation | `Expectation<T>` | A test over the subject one `Bay` binds: `Reply test(ExpectationContext<T>)`, `Class<T> subject()`, `default ReadScope reads()`, `default ExpectationMeta meta()`. The kernel's word for "predicate"; `Predicate` is banned because of `java.util.function.Predicate`. | `CellPredicate` |
| Expectation context | `ExpectationContext<T>` | What a test may see: `subject()` (`T`) and `subjects()` (the bound `OccupantRef`s, taken from `Found`), and `withSubject(value, subjects)`, which re-types the subject so a bay-level test can hand one cell to a cell test; `of(base, subject, subjects)` builds it from a `FocusContext`. It extends `FocusContext`, so `bay()` (`BayView`), `view()`, `siting()`, `cells()`, `stage()`, `locus()` and `AggregateContext`'s `read`/`occupants`/`context()` come with it. | `PredicateContext` |
| Probe session | `ProbeSession` | Read-only face of the running attempt: `occupant(LatticePoint)`, `claimsAt(LatticePoint)` (frozen ledger only; session reservations stay private), `binding(BindingKey<T>)`, `remaining()`, `stage()`. | `MatchSessionView` |
| Verdict | `Verdict` | `MATCH`, `MISMATCH`, `ABSTAIN`, `UNREADABLE`. `ABSTAIN` means "this expectation declines"; `UNREADABLE` means "the snapshot cannot answer". | `Verdict` |
| Reply | `Reply` | `(Verdict verdict, Bindings bindings, FindingBundle findings)`, never generic: one reply per check, whatever the subject type. Only `MATCH` may carry bindings; `MISMATCH` and `UNREADABLE` must carry at least one finding. | `Outcome` |
| Bindings | `Bindings` | Immutable heterogeneous map keyed by `BindingKey<T>`; produced by a successful expectation and merged by combinators. | — |
| Binding key | `BindingKey<T>` | Typed key `(NamespacedId id, Class<T> type)` for one bound value. | — |
| Expectation meta | `ExpectationMeta` | `(NamespacedId id, int specificity, Set<ContextKey<?>> requires, FindingCode mismatchCode)`; catalog identity, a report-order hint, the `ContextKey`s the test needs, and the code a plain mismatch carries. `anonymous()` and `of(id, specificity, mismatchCode)` are the factories. There is no `cost` or `admission`: the budget is cells/sections (`22` §9.5). | `PredicateMeta` |
| Expectation factory | `ExpectationFactory` | `NamespacedId id()`, `Expectation<?> create(DataView args, FactoryContext ctx)`; the data-driven resolution step. | `PredicateFactory` |
| Factory context | `FactoryContext` | What a factory may see while building an expectation: the catalog snapshot and the document provenance. | — |
| Combinators | `Expectations` | Static `allOf`, `anyOf`, `not`, `sequence`, `atLeast`, `atMost`, `exactly`, `block`, `air`, `trait`, `anyOccupant`, `nothing`, `custom`. Each returns an `Expectation<T>` of its children's subject type, so a `DoorView` child and a `BayView` child do not compose. | `Predicates` |
| Focus | `Focus<T>` | The reading half of a check: `Outcome<T> take(FocusContext)`, `Class<T> subject()`, `DecisionKind decisionKind()`, `CuboidSet staticDecisionCells(Cuboid)`, `ReadScope reads(Cuboid bayCells)`, `FindingBundle describeMissing()`. Kinds are data and each binds its own subject: `bay` → `BayView`, `allCells` → `List<OccupantSnapshot>`, `occupant` → `OccupantSnapshot`, `entity` → `EntitySnapshot`, `nested` → `NestedView`, `context` → `T`, `door` → `DoorView`. | — |
| Focus outcome | `Outcome<T>` | `Found(value, subjects, notes)` or `Missing(findings)`, with `fold(onFound, onMissing)`; `Found.subjects` is the only way a bay binding is built, and a missing subject never reaches `test`. | — |
| Decision kind | `DecisionKind` | `EVERY_CELL`, `ONE_SUBJECT`, `ANY_SUBJECT`: what a focus can assert at DESIGN, validated against the bay's `Coverage`. | — |
| Read scope | `ReadScope` | A unit's declared read closure: cells, keys and entity windows. `Check.reads(bayCells)` is the union of its focus's and its expectation's; a bay focus reads the whole bay. | — |

## F. U4 — claims (`kernel.claim`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Sharing | `Sharing` | `EXCLUSIVE`, `SHARED`: whether this claim excludes another over the same cell. | — |
| Footprint | `Footprint` | Which cells a bay owns: `Box` (the bay cuboid), `Bound` (the union of the non-air bound footprints), `Declared` (a declared `CuboidSet`), `None` (nothing). Claims protect what a structure occupies or declares, never what it merely reads. | — |
| Coverage | `Coverage` | `EVERY_CELL`, `ONE_SUBJECT`, `ANY_SUBJECT`: what a match on a bay must establish — every cell decided, the cells equal to the bound footprints, or no cell at all. Legality is rules R1–R4 (`24`). | — |
| Claim row | `ClaimRow` | `(World world, LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting, NamespacedId bay)`: one cell of one world owned by one bay of one placement; `(world, owner, siting)` is the replace scope and the self-conflict exclusion. | `Claim`, `ClaimKey` |
| Claim set | `ClaimSet` | `(List<ClaimRow> rows, long revision)`: what one `settle` writes, carried by `Formed`. | — |
| Claim verdict | `ClaimVerdict` | Sealed `Settled(revision, rows)` \| `ClaimConflict(rows, findings)` \| `RevisionMoved(expected, actual)`. | `Settlement` |
| Claim ledger | `ClaimLedger` | One handle is bound to one world: `World world()`, `long revision()`, `ClaimVerdict settle(SettleRequest)`, `ClaimVerdict release(OwnerId, Siting)` and `ClaimView read(Cuboid)`; the view answers `rows()`, `revision()` and `available()`, where `available() == false` means "cannot answer", never an empty claim set. `settle` and `release` are the only writers; the three lifecycle points that call `release` — an instance's removal, a move, a world unload — are `23` §5's. Handing the kernel a handle of another world is `SC-CLM-0007`. | `ClaimLedger` |
| Claim matrix | `ClaimMatrix` | The one conflict predicate: `static boolean conflicts(ClaimRow a, ClaimRow b)` — true exactly when both rows are of one world, name one cell, are not the same `(owner, siting)`, and at least one is `EXCLUSIVE`. | — |
| Settle request | `SettleRequest` | `(World world, OwnerId owner, Siting siting, List<ClaimRow> rows, long expectedRevision)`: the unit of the ledger's single CAS; every row's world must equal the request's (`SC-CLM-0007`). | — |
| Claim view | `ClaimView` | `record (List<ClaimRow> rows, long revision, boolean available)`: the rows intersecting a box, in canonical order, the revision they were read at, and `available`, where `false` means "cannot answer", never an empty claim set — an unavailable view holds no rows (`unavailable()` is its factory), and `rowsIn(Cuboid)` narrows it. | — |
| Claim conflict | `ClaimConflict` | `(List<ClaimRow> rows, FindingBundle findings)`: the held rows that block, plus the request's own conflicting rows when the conflict is inside one request; nothing is written. | `ClaimConflict` |
| Claim plan | `ClaimPlan` | Sealed `Matched(long ledgerRevision, List<BayBinding> bindings)` \| `Invalid(FindingBundle findings)`: what a `check` found and the revision it read. It carries **no rows** — every row is derived by `ClaimPlanner`, so a caller cannot hand `settle` rows it built itself. | — |
| Claim planner | `ClaimPlanner` | The only row factory: `Derived derive(World, Blueprint, Siting, OwnerId, Collection<BayBinding>)`, pure and deterministic (no view, no ledger, no I/O), returning `(rows, overlaps, staticFootprint)`. | — |

## G. U5 — blueprints and stage plans (`kernel.blueprint`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Bay | `Bay` | A named `Cuboid` bound to a `Requirement` (checks + computations), with `Coverage`, `Footprint`, `Sharing` and an optional description. One bay may be many cells (door case). | `Slot` |
| Seam | `Seam` | A declared connectable edge on a segment: `(SeamId id, Cuboid cells, SeamKind kind, Map<String,String> properties)`. | `FragmentInterface` |
| Seam id | `SeamId` | Namespaced id local to a blueprint. | — |
| Seam kind | `SeamKind` | `ITEM`, `FLUID`, `ENERGY`, `STRUCTURAL`, `CUSTOM`. | — |
| Segment | `Segment` | A reusable, parameterised piece of a blueprint: key, bounds, bays, seams, anchors. The kernel's word for "fragment". | `Fragment` |
| Segment key | `SegmentKey` | `(NamespacedId id, Arguments arguments)`; identity includes arguments. | `FragmentKey` |
| Anchor role | `AnchorRoles` | `PRIMARY`, `FRONT`, `CENTER` as `NamespacedId` constants; a role is a `NamespacedId`, never a type of its own, and custom roles are plain ids. Never a required entity. | `AnchorRole` |
| Anchor spec | `AnchorSpec` | `(NamespacedId role, NamespacedId property, String value)`: one property read at the definition origin, one cell. | `AnchorSpec` |
| Orientation policy | `OrientationPolicy` | Allowed `Orientation`s, the primary anchor role, and whether upright-only is required. | `OrientationPolicy` |
| Blueprint | `Blueprint` | Immutable validated template: id, bounds, bays, seams, anchors, orientation policy, per-stage plans, claim spec, derivation specs, arguments, provenance, declared overlaps. | `StructureDefinition` |
| Blueprint builder | `BlueprintBuilder` | Fluent construction; every `bay`, `seam`, `anchor`, `check`, `compute`, `derive` call returns the builder; `build()` validates. | — |
| Claim spec | `ClaimSpec` | `(Sharing defaultSharing, Map<NamespacedId, Sharing> overrides)`. | `ClaimSpec` |
| Derivation spec | `DerivationSpec` | `(NamespacedId deriver, Arguments args, int order)`; a declarative reference resolved through the catalog at derive time. | `DerivationSpec` |
| Stage plan | `CheckPlan` | `(Stage stage, List<Computation<?>> computations, List<DerivationSpec> derivations, List<Check<?>> checks)`: the ordered units of one stage, after the section/aggregate/check expansion. | `CheckStep`, `CheckKind` |
| Check | `Check<T>` | `(NamespacedId id, NamespacedId bay, Focus<T> focus, Expectation<T> expectation, @Nullable String description)`: one focus and one expectation pinned to the same `T`, belonging to exactly one bay. The engine, not the author, decides the stage it runs in. | `CheckStep`, `CheckKind` |
| Check result | `CheckResult` | `(Reply reply, List<OccupantRef> subjects, FindingBundle notes)`: the verdict, the subjects the focus bound (the claim footprint's only source), and non-fatal notes. | — |
| Context key | `ContextKey<T>` | `(NamespacedId id, Class<T> type, Retention, @Nullable Merge<T>, @Nullable T whenAbsent)`: the one key type, and the only key a frame, a check or `RuntimeState` ever uses. Equality is `(id, type)`; uniqueness belongs to `Catalogs.contextKeys`, not to the record. `whenAbsent` is honoured only when every section of the region was readable. | `ContextKey` |
| Merge | `Merge<T>` | `combine(T left, T right)`: a non-null `merge` marks the key as section-contributed and forbids `put`; `null` marks it single-writer and forbids `contribute`. Contributions sort by `SectionKey` and fold left-to-right; no identity, so a partition cannot change the result. | `Reducer` |
| Retention | `Retention` | `ATTEMPT` discarded, `OUTCOME` in `MatchOutcome.context`, `CONTEXT` published into the stage's frame; the only lifetime. | — |
| Computation | `Computation<T>` | A plan unit producing one `ContextKey<T>`: `output()`, `phase()`, `inputs()`, `region()`, `compute(ComputationContext)`. One type serves both phases; a section run contributes, an aggregate run puts, and the engine assigns the stage. | — |
| State phase | `StatePhase` | `SECTION` or `AGGREGATE`, returned by `Computation.phase()`: `SECTION` runs once per section of `region()` and cannot read another section, `AGGREGATE` runs once after the barrier. The key does not carry it. | — |
| Context draft | `ContextDraft` | One stage's writes, not yet visible: `put(key, value)` on a single-writer key, `contribute(key, partial)` on a section-contributed one; the wrong verb for the key is `SC-CTX-0109`. | — |
| Context view | `ContextView` | The read face: `get(key)` nullable for a caller that can live without a value, `require(key)` returning `Outcome` (absence is `Missing`, never a bare null). | — |
| Context keys | `ContextKeys` | The registration path for keys: `register(draft, ...)`; two registrations of one id are `SC-CTX-0102`. | — |

## H. U6 — documents and data (`kernel.document`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Blueprint document | `BlueprintDocument` | `(int schemaVersion, NamespacedId id, List<SegmentDocument>, List<AssemblyDocument>, Map<String,DataView> extensions)`; the authoring form, allowed to be partial and parameterised. | `StructureDocument` |
| Segment document | `SegmentDocument` | One segment declaration: id, bounds, parameter defaults, bays, seams; its `expectation` body is an opaque `DataView` resolved by an `ExpectationFactory` at assembly. | — |
| Assembly document | `AssemblyDocument` | One placement: `as`, `SegmentKey`, `at`, `overlap`; the placement half of a blueprint document. | — |
| Document schema | `DocumentSchema` | The field grammar and the supported `schemaVersion` range; canonical field order and defaults; `COMPACT`/`CANONICAL`/`AUTO` profiles. | `DocumentSchema` |
| Document profile | `DocumentProfile` | `COMPACT` accepts omitted `segments`, `assembly`, `orientation`, `context`, `computations` and `checks`; `CANONICAL` is the explicit form; `AUTO` detects by top-level shape for datapacks. | — |
| Compact writer | `CompactWriter` | Projects a `BlueprintDocument` to the compact JSON profile for tools/export. | — |
| Data bridge | `DataBridge<T>` | Host codec translation: `fromHost(T)` / `toHost(BlueprintDocument)`; the kernel never depends on `T`. | `DataBridge` |
| Data frontend | `DataFrontend` | Text frontend: `read(String)` / `write(BlueprintDocument)`; JSON and SNBT are the shipped frontends. | `DataFrontend` |
| Canonicalizer | `Canonicalizer` | Round-trip normaliser: same content ⇒ same bytes. | `Canonicalizer` |
| Segment library | `SegmentLibrary` | Lookup `@Nullable Segment lookup(SegmentKey)`; a missing segment is a validation issue, not an exception. | `FragmentLibrary` |
| Segment assembler | `SegmentAssembler` | Pure composition `BlueprintDocument -> Blueprint` using a `SegmentLibrary`. | `FragmentAssembler` |
| Segment emitter | `SegmentEmitter` | External generation contract: `void emit(Segment, DataView sink)`. | `Generator` |
| Document validator | `DocumentValidator` | Design-phase static checks; returns `List<ValidationIssue>`, never throws. | `DefinitionValidator` |
| Validation issue | `ValidationIssue` | `(FindingCode code, Severity severity, String message, @Nullable Locus locus)`. | `ValidationIssue` |
| Provenance | `DocumentProvenance` | `(NamespacedId source, @Nullable SourceSpan range)` attached to a compiled blueprint. | — |

## I. U7 — engine (`kernel.engine`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Place | `Place` | Sealed `Unplaced` \| `Pinned(Siting)` \| `Open(LatticePoint)`: the host's as-yet-unresolved placement, carried only by the scheduler's `CheckRequest` (`20` §3); a `MatchRequest` always carries a resolved `Siting`. | — |
| Match request | `MatchRequest` | `(Blueprint blueprint, Siting siting, SnapshotLease lease, MatchPolicy policy, InstanceBudget budget, PinBudget pins, InstanceRecord record, OwnerId owner, @Nullable Ticket ticket)`; built with a fluent builder. `world()` and the capture id are derived from `lease`, and `ticket()` is the host's slot token for this dispatch. The siting is resolved before the request exists, so there is no `Place` and no declared `Stage`. | `MatchRequest` |
| Snapshot lease | `SnapshotLease` | `AutoCloseable` capture handle: `id()`, `view()`, `retain(PinBudget)`, `changeCount()`, `isValid()`, `isOpen()`, `close()`. `changeCount()` is the marks in the watched region since this capture; `isValid()` is false once its world was replaced or the handle was closed. One owner per instance; `Formed` and `Admitted` hold child handles. | — |
| Ticket | `Ticket` | `(long serial)`: the host's slot counter, incremented per submit so a slot can coalesce newer requests. Queue state; it is not part of a capture and no capture comparison reads it. | — |
| Match policy | `MatchPolicy` | `(int maxSectionsPerUnit, CacheMode cache, boolean parallel)`; `defaults()` is `(8, OUTCOMES, false)` and a host may only narrow it. No component can change a verdict, a bundle or a `Spend` (`11` §4.7). | `MatchPolicy` |
| Pin budget | `PinBudget` | The ceiling `retain` charges to `retainedBytes`; exhaustion is `Missing` with `SC-MC-0502` (`PIN_REFUSED`), never a forced close. | — |
| Instance budget | `InstanceBudget` | Per-instance budget owned by the scheduler slot and passed by reference; a request cannot reset it, and discards and attempts are counted in the same slot. | — |
| Match session | `MatchSession` | Mutable per-attempt state: bindings, reservations, findings, spend, budget left. Exposed to extensions only through `ProbeSession`, plus a narrow write face for the engine. | `MatchSession` |
| Match settler | `MatchSettler` | The server-thread step `settle` runs: one revision read, one CAS, and at most one in-call re-read when the ledger moved. | — |
| Match result | `MatchResult` | Sealed: `Admitted`, `Dropped`, `Formed`, `Failed`, `Undecided`, `Deferred`; every case carries the `Stage` it was run in, and only `settle` produces `Formed`. The interface is `AutoCloseable`, with a no-op `close()` on the arms that hold no handle, so `try (var r = kernel.check(request))` compiles for every arm. | `AttemptResult` |
| Admitted | `MatchResult.Admitted` | `(Stage, Siting, MatchOutcome, @Nullable ClaimPlan planned, long ledgerRevision, SnapshotLease, FindingBundle)`: what a read-only `check` returns when the structure holds; `planned` is what a `settle` would claim. Carries a child lease handle, never an instance. | — |
| Formed | `MatchResult.Formed` | `(Stage, InstanceId, Siting, MatchOutcome, ClaimSet, long generation, long contextRevision, long ledgerRevision, SnapshotLease, FindingBundle)`: produced only by `settle`; derivation is a separate call. | `FormedResult` |
| Failed | `MatchResult.Failed` | `(Stage, List<Finding> findings)`; at least one `ERROR` finding (a mismatch or a claim conflict). | `FailedResult` |
| Undecided | `MatchResult.Undecided` | `(Stage, UndecidedReason reason, List<Finding> findings, @Nullable MatchOutcome partial, @Nullable RetryToken retry)`. | `UndecidedResult` |
| Deferred | `MatchResult.Deferred` | `(Stage, RetryToken token, List<Finding> findings)`. | — |
| Undecided reason | `UndecidedReason` | `BUDGET_EXHAUSTED`, `DIRTY_UNSTABLE`, `LEDGER_MOVED`, `LEDGER_UNAVAILABLE`, `SUBJECT_ABSENT`, `LEASE_CLOSED`, `WORKER_FAILED`, `DESIGN_DEFECT`. | — |
| Dropped | `MatchResult.Dropped` | `(Stage, DropCause cause, List<Finding> findings)`: no verdict exists because the capture moved or became invalid after it was taken; `cause` is `SNAPSHOT_CHANGED` (the watched region was marked) or `CAPTURE_INVALID` (its world was replaced, or the lease was closed). Nothing is published; the next attempt captures again. | — |
| Drop cause | `DropCause` | `SNAPSHOT_CHANGED` (`SC-ENG-0003`), `CAPTURE_INVALID` (`SC-ENG-0006`). | — |
| Match outcome | `MatchOutcome` | `(Stage stage, Siting siting, WorldView view, Map<ContextKey<?>,Object> context, Bindings bindings, ReadScope reads)`: what one attempt produced — the view it read, the values it published, the subjects it bound and everything it touched. | — |
| Bay binding | `BayBinding` | `(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings)`: the subjects a check bound, which may be two for a door, never unpacked from a value. | `SlotBinding` |
| Seam binding | `SeamBinding` | `(SeamId seam, List<OccupantRef> counterparts)`. | `PortBinding` |
| Census | `Census` | `(Map<NamespacedId,Integer> byType, Map<OccupantKind,Integer> byKind, int total)`. | `ElementCensus` |
| Prescreen | `PrescreenIndex` | Precomputed bounds, census and anchor candidates for a view; `@Nullable AnchorCandidate find(...)`. | `PrescreenIndex` |
| Anchor candidate | `AnchorCandidate` | `(NamespacedId role, LatticePoint cell, Orientation implied, int score)`. | `AnchorCandidate` |
| Fingerprint | `Fingerprint` | `(long high, long low)` digest of prescreen content; stable across runs. | `Fingerprint` |
| Read set | `ReadSet` | `(CuboidSet cells, Set<ChunkKey> chunks, Set<OccupantRef> occupants)` actually read. | `ReadSet` |
| Change set | `ChangeSet` | `(Set<LatticeKey> cells, Set<SectionKey> sections, Set<LatticeKey> payloads, Set<EntityKey> entities, Set<NamespacedId> keys, long revision)`. | `ChangeSet` |
| Invalidation index | `InvalidationIndex` | `List<InstanceRef> affectedBy(ChangeSet)`. | `InvalidationIndex` |
| Instance ref | `InstanceRef` | `(NamespacedId blueprint, Siting siting, long generation)`. | — |
| Instance id | `InstanceId` | `(World world, long serial)`: the identity of one placed structure. It keys its own `StructureContext` store, so two instances publishing one key never collide, and it is what a `KEY` locus names. | — |
| Plan id | `PlanId` | One interned `long`, allocated at `Blueprint` build; a datapack rebuild gets a new id and a cold cache. | — |
| Unit id | `UnitId` | The identity of one plan unit inside a plan: a `Check` id or a `Computation` id (`11` §4.7). | — |
| Section key | `SectionKey` | `(World world, int x, int y, int z)`: the 16³ section index of one world; not a cache key. | — |
| Cache key | `CacheKey` | `(PlanId plan, Siting siting, UnitId unit, long closureDigest)`: the attempt-cache identity of one unit at one placement (`11` §4.7). | — |
| Cached unit | `CachedUnit` | Sealed `CachedPartial(ContextKey<?> key, Object value)` \| `CachedOutcome(Reply reply, List<Finding> findings, List<OccupantRef> subjects)`: one unit's contributions or its outcome, never a folded value and never world content (`20` §6.3). | `SectionResult` |
| Attempt cache | `AttemptCache` | Optional LRU over `CachedUnit`: `@Nullable get(CacheKey)`, `put(CacheKey, CachedUnit)`, `none(long cacheBytes)`; evicts under `cacheBytes`, never under `retainedBytes` (`20` §6.3). | `SectionCache` |

## J. U8 — context (`kernel.context`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Structure context | `StructureContext` | Immutable, per-instance view of published frames: `(InstanceId instance, long revision, List<ContextFrame> frames)`, a `ContextView` (`get`, `require`, `keys()`) plus `findings()`, `spend()`. Two instances publishing one key never collide. | `StructureContext` |
| Context frame | `ContextFrame` | `(ContextTier tier, long revision, Map<ContextKey<?>, Object> values)`: one published stage boundary; a reader at tier `t` sees the newest frame at or below `t`. | `ContextValue` |
| Section context | `SectionContext` | What one section run of a `Computation` sees: `section()`, `occupants(...)`. It has no `read(ContextKey)`, so a section cannot read aggregate state, and its only write is `ContextDraft.contribute`. | — |
| Aggregate context | `AggregateContext` | What an aggregate run of a `Computation` sees: `read(ContextKey<T>)`, `occupants(...)`; it cannot `contribute`. | — |
| Focus context | `FocusContext` | `AggregateContext` for one check's subject: a check reads `ContextKey`s and nothing else. | — |
| Augment | `Augment` | An aggregatable effect `(AugmentKey key, double amount)`, e.g. "+3 capacity". | `Modifier` |
| Augment key | `AugmentKey` | `(NamespacedId id, AugmentFold fold)`. | — |
| Augment fold | `AugmentFold` | `SUM`, `MAX`, `MIN`, `MULTIPLY`, `SET`; the deterministic fold order is declaration order. | `ModifierAggregator` |
| Runtime state | `RuntimeState` | `(StructureContext context, Map<AugmentKey,Double> augments, long generation)`; the host-persisted form of a formed structure. | `RuntimeContext` |

## K. U9 — findings (`kernel.finding`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Severity | `Severity` | `ERROR`, `WARNING`, `INFO`. | `Severity` |
| Finding code | `FindingCode` | Stable identifier `SC-<FAMILY>-<NNNN>`, e.g. `SC-SPC-0001`; never reused for a different meaning. | `DiagnosticCode` |
| Finding | `Finding` | `(FindingId id, FindingCode code, Severity severity, String message, List<Label> labels, List<Note> notes, @Nullable Suggestion suggestion, List<FindingId> related, Stage stage)`. | `Diagnostic` |
| Finding id | `FindingId` | Stable within one bundle; used by `related`; `NONE` means "not addressable". | — |
| Label | `Label` | `(Locus locus, String message, LabelRole role)`; the source span travels inside the `Locus`, never twice. | `Label` |
| Label role | `LabelRole` | `PRIMARY`, `SECONDARY`, `CONTEXT`. | — |
| Note | `Note` | `(String message, @Nullable Locus locus)`. | `Note` |
| Suggestion | `Suggestion` | `(String message, Applicability applicability, @Nullable NamespacedId remedy)`; the plan itself is computed by `solve`, never embedded in a finding (keeps U9 independent of U10). | `Suggestion` |
| Applicability | `Applicability` | `MACHINE_APPLICABLE`, `MAYBE_INCORRECT`, `HAS_PLACEHOLDERS`, `UNSPECIFIED`. | `Applicability` |
| Source span | `SourceSpan` | `(NamespacedId document, int start, int end)`; defined in `base`, consumed here. | — |
| Finding bundle | `FindingBundle` | Immutable, ordered, deduplicated, capped set; `primary()`, `isEmpty()`, `filter(...)`. | `DiagnosticBundle` |
| Finding sink | `FindingSink` | Consumer: `accept(Finding)`, `acceptAll(FindingBundle)`. | `DiagnosticSink` |
| Finding options | `FindingOptions` | `(maxTotal, maxPerCode, includeInfo, includeNotes)`: the size of a report, spent on `WARNING`/`INFO` only — no cap ever elides an `ERROR`. | `DiagnosticOptions` |
| Finding renderer | `FindingRenderer` | `String render(Finding, RenderContext)`; human, JSON and SARIF-shaped renderers are hosts of this interface. | `DiagnosticRenderer` |
| Explain book | `ExplainBook` | `@Nullable Explanation explain(FindingCode)`. | `ExplainBook` |
| Explanation | `Explanation` | `(FindingCode code, String summary, String body, List<NamespacedId> seeAlso)`. | — |
| Render context | `RenderContext` | `(List<SourceText> sources, @Nullable NamespacedId document, int width, ColorMode color, Locale locale)`; the only ambient input a renderer may read. | — |
| Source text | `SourceText` | `(NamespacedId id, String text)` with offset→line/column mapping; U9-local, supplied by the host. | — |

## L. U10 — remedies (`kernel.remedy`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Remedy | `Remedy` | Sealed: `Repair`, `Preview`, `ExplainRemedy`. | `Solution` |
| Remedy kind | `RemedyKind` | `REPAIR`, `PREVIEW`, `EXPLAIN`. | `SolutionKind` |
| Remedy provider | `RemedyProvider` | `List<Remedy> solve(Finding, RemedyContext)`; returns an ordered, deduplicated list. | `SolutionProvider` |
| Remedy context | `RemedyContext` | `view()`, `blueprint()`, `siting()`, `ledger()`, `kinds()`, `maxResults()`. | `SolveOptions` |
| Solve options | `SolveOptions` | `(Set<RemedyKind> kinds, int maxResults, boolean allowWorldReads, int planDepth)`; defaults are all kinds, 8, false, 1. | — |
| Repair | `Remedy.Repair` | `(EditPlan plan, Applicability applicability, String rationale)`. | `SolutionPlan` |
| Preview | `Remedy.Preview` | `(OverlayModel overlay, String rationale)`. | `OverlayModel` |
| Explain remedy | `Remedy.ExplainRemedy` | `(Explanation explanation)`. | — |
| Edit plan | `EditPlan` | Ordered `EditStep`s plus a rationale. | `SolutionPlan` |
| Edit step | `EditStep` | `(Edit edit, Locus locus, Applicability applicability, @Nullable Expectation<?> precondition)`. | `Operation` |
| Edit | `Edit` | Sealed: `Place`, `Remove`, `Replace`, `SetTrait`, `Configure`, `BindAnchor`, `NestedEdit`. | `Operation` record set |
| Plan validator | `PlanValidator` | Dry run `(EditPlan, WorldView, Siting) -> ValidationReport`. | `PlanValidator` |
| Validation report | `ValidationReport` | `(List<Finding> findings, boolean applicable)`. | — |
| Plan diff | `PlanDiff` | `(List<EditStep> added, removed, changed)`. | `PlanDiff` |
| Apply report | `ApplyReport` | `(boolean success, List<Finding> findings, Spend spend)`. | `ApplyReport` |
| Edit transaction | `EditTransaction` | Reversible apply scope: `apply(EditStep)`, `commit()`, `rollback()`. | `Transaction` |
| Edit executor | `EditExecutor` | Host SPI: `EditTransaction begin(WorldView, Siting, ClaimView, long ledgerRevision, PlanDigest)`. | — |
| Overlay model | `OverlayModel` | `(Cuboid bounds, Map<LatticeKey,CellMark> cells, List<Marker> markers)`. | `OverlayModel` |
| Cell mark | `CellMark` | `EXPECTED`, `PRESENT`, `MISSING`, `EXTRA`, `WRONG`, `BLOCKED`, `UNKNOWN`. | `CellStatus` |
| Marker | `Marker` | `(Locus locus, String label, MarkerKind kind)`. | `Annotation` |
| Marker kind | `MarkerKind` | `ERROR`, `WARNING`, `INFO`, `ANCHOR`, `SEAM`. | — |

## M. U11 — SPI and facade (`kernel.spi`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Catalog | `Catalog<T>` | Namespaced, ordered, immutable extension map: `@Nullable T get(NamespacedId)`, `List<NamespacedId> ids()`. | `Catalog` |
| Catalogs | `Catalogs` | Frozen snapshot of every extension catalog: `expectations`, `focuses`, `computations`, `contextKeys`, `derivers`, `remedies`, `codes`; `Catalogs.builder()` then `build()`, frozen at `apply` and resolved without reflection. | `Catalogs` |
| Structure kernel | `StructureKernel` | The facade; nine operations: `document`, `compile`, `siting`, `check`, `settle`, `derive`, `solve`, `explain` (`explainOrFallback` counted with it), `ledger`. | `StructureKernel` |
| Compile result | `CompileResult` | `(FindingBundle findings, @Nullable Blueprint blueprint)`: the DESIGN report and, when clean, the assembled blueprint. `compile` replaces the earlier `validate` sketch. | — |
| Trivial implementation | — | A kernel-shipped implementation with no domain knowledge, e.g. `Expectations.air()`; the only implementations the kernel may ship (R-10). | same |

## N. A1 — adapter (`dev.vfyjxf.cumulus.mc`)

| Term | Type | Definition | Prior |
| --- | --- | --- | --- |
| Level snapshot | `LevelSnapshot` | `WorldView` over a captured `Level` region; immutable, no live `Level`. | — |
| Level capture | `LevelCapture` | `WorldCapture` that walks blocks, block entities, entities and nested structures. | — |
| World occupants | `WorldOccupants` | `OccupantResolver` for `BlockState`, block entity, `Entity` and extent raw values. | — |
| Occupant descriptors | `OccupantDescriptors` | Raw block/entity value → `OccupantDescriptor`, and its exact inverse. | — |
| Occupant traits | `OccupantTraits` | Immutable inventory, energy, fluid and entity-NBT values. | — |
| Orientation mapping | `OrientationMapping` | Maps `Heading`/`Turn`/`Tilt`/`Flip` to/from MC `Direction`/`Rotation`/`Mirror` and remaps block states. | `OrientationAdapter` |
| Saved claims | `SavedClaims` | `ClaimLedger` persisted through a `SavedData`. | `SavedClaims` |
| World edits | `WorldEdits` | `EditExecutor` applying plans with rollback. | `WorldEdits` |
| Client overlay | `ClientOverlay` | Client-side `OverlayModel` renderer; never part of the kernel. | `OverlayRenderer` |
| Overlay payload | `OverlayPayload` | S2C payload carrying an `OverlayModel` to the client. | — |
| Datapack blueprints | `DatapackBlueprints` | Datapack reload into cached documents. | `DefinitionReloadListener` |
| Finding output | `FindingOutput` | `FindingSink` to log, chat or JSON. | — |
| Server state | `ServerState` | Per-server catalogs, facade and claim ledger. | — |
| JSON frontend / bridge | `JsonFrontend`, `JsonBridge` | Gson text/tree ↔ `DataView`. | — |
| Cumulus command | `CumulusCommand` | `/cumulus capture`, `import`, `lint`, `validate`, `preview`, `build`, `explain`. | `CumulusCommand` |
| Match scheduler | `MatchScheduler` | Host-side per-server executor: `submit`, `markDirty`, `dropInstance`, `worldUnloaded`, `tick`, `close`. | — |
| Check key | `CheckKey` | `(World world, OwnerId owner)`: scheduler slot identity; the `Siting` stays in the request. | — |
| Match handle | `MatchHandle` | One submit: state, ticket, `done()`, `onTerminal`, cooperative `cancel`. | — |
| Match state | `MatchState` | `QUEUED`, `RUNNING`, `POSTED`, `SETTLED`, `DROPPED`, `FAILED`, `DEFERRED`, `CANCELLED`. | — |
| Match completion | `MatchCompletion` | Sealed `Reported`, `Discarded`, `Failed`; the worker's terminal completion value, one per submit. Not the attempt product: that is `MatchOutcome`. | `MatchOutcome` |
| Frozen ledger | `FrozenLedger` | Immutable `ClaimLedger` snapshot + revision, with a precomputed read box; dispatch-time view. | — |
| Dirty index | `DirtyIndex` | Host-side section/payload/entity → watched-instance index; O(1) marks. | — |
| Watched instance | `WatchedInstance` | One tracked structure: snapshot, state, `dirtyEpoch`, section/payload/entity revisions, `dirtyForCheck`, `checkInFlight`. | — |
| Watch state | `WatchState` | `CLEAN`, `DIRTY`, `CHECKING`, `BROKEN`, `UNKNOWN`. | — |
| Section store | `SectionStore` | Optional per-world shared cache of immutable `SectionSnapshot`s; default is per-snapshot copies. | — |
| Section snapshot | `SectionSnapshot` | Frozen `PalettedContainer<BlockState>` copy + capture revision + payload cells. | — |
| Cell payload | `CellPayload` | `(LatticeKey, OccupantDescriptor, List<Trait<?>>)` for a watched BE/entity cell; the `OccupantRef` is stamped with the final `SnapshotId` lazily. | — |
| Capture request / limits | `CaptureRequest`, `CaptureLimits` | Bounds, policy, grid, tracked types/cells, caps (cells, sections, bytes, entities, payloads). | — |
| Capture result | `CaptureResult` | Sealed `Captured(WorldView)`, `TooLarge`, `Unavailable(status, code)`; never a partial snapshot treated as complete. | — |
| Nested level snapshot | `NestedLevelSnapshot` | Child `NestedView` over the same frozen bytes as its parent. | — |
| Capture tool | `CaptureTool` | `/cumulus capture` host path: selection → compact JSON `DataView`. | — |
| Schematic import | `SchematicImport` | `.schem`/`.nbt` reader that emits compact JSON; unsupported entries become findings. | — |

## O. Banned names

These names are refused even when they read naturally, because a Minecraft or JDK type
already owns them. The right-hand column is the kernel term to use.

| Banned | Collides with | Use |
| --- | --- | --- |
| `Rotation`, `Mirror` | `net.minecraft.world.level.block.Rotation`, `.Mirror` | `Turn`, `Flip` |
| `Direction` | `net.minecraft.core.Direction` | `Heading` |
| `Pose` | `net.minecraft.world.entity.Pose` | `Siting` |
| `Slot` | `net.minecraft.world.inventory.Slot` | `Bay` |
| `BlockPos`, `AABB` | Minecraft | `LatticePoint`, `Cuboid` |
| `Predicate` | `java.util.function.Predicate` | `Expectation` |
| `Diagnostic` | `javax.tools.Diagnostic` | `Finding` |
| `Solution` | too generic; JDK-adjacent | `Remedy` |
| `Box` | `javax.swing.Box` | `Cuboid` |
| `Region` | common; collides with host regions | `CuboidSet` |
| `Element` | `javax.swing.text.Element`, `org.w3c.dom.Element` | `Occupant` |
| `Transform` | `java.awt.geom.AffineTransform` | `Placement` |
| `Registry` | `net.minecraft.core.Registry` | `Catalog` |
| `Holder` | `net.minecraft.core.Holder` | `OccupantRef` |
| `Codec` | `com.mojang.serialization.Codec` | `DataBridge` |
| `Component` | `net.minecraft.network.chat.Component` | `Trait` / host payload |
| `Ingredient` | `net.minecraft.world.item.crafting.Ingredient` | `Expectation` |
| `Modifier` | `java.lang.reflect.Modifier` | `Augment` |
| `Entity` | Minecraft | `MatchResult.Formed` / `OccupantRef` |
| `Optional` in public API | project nullness convention | `@Nullable` |

## P. Ownership of new names

A module may add a name inside its own package without a glossary change, but any name that
another module must reference is a **cross-unit name** and is added here, with an owner,
before the module file is accepted. The reviewer rejects a module that silently introduces a
second word for a term already in this table.
