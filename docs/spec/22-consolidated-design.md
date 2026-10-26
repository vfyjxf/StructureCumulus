# 22 — Consolidated design

> Status: revision 5, 2026-10-20. This file is the consolidated design. Intermediate state is not a
> separate unit: it is the context API extended with contributions, a fold and a required read.
> Revision 4's predicate-layer rewrite and the `StateKey`/`PartialKey`/`Reducer` split of
> revisions 3–4 were withdrawn; §15.0 records why.
> `_work/review1` and `_work/review2` refer to this file as `23`; that numbering is historical.

## 0. The model

1. **One key: `ContextKey<T>`.** It carries `(id, type, Retention, @Nullable Merge<T>,
   @Nullable T whenAbsent)`. There is no second key type for intermediate state.
2. **One store: `ContextDraft` / `ContextView`.** A section computation calls
   `draft.contribute(key, partial)`; an aggregate calls `draft.put(key, value)`; a reader calls
   `view.require(key)`.
3. **`merge == null` means single-writer**; `merge != null` means section contributions are legal.
   Using the wrong verb for a key is a DESIGN error, not a runtime one.
4. **Fold is canonical.** Contributions are sorted by `SectionKey` and combined left-to-right with
   `Merge.combine`. A refused section makes the key absent; it is never a short sum and never an
   identity value.
5. **Predicates read through a focus.** `Focuses.context(key)` yields a `Focus<T>`; `Check<T>` pairs
   it with an `Expectation<T>`. A missing key is `Outcome.Missing`, which becomes `UNREADABLE`; the
   predicate is not called.
6. **Publication is the same store.** `Retention.CONTEXT` writes the value into the stage's tier
   frame; `OUTCOME` keeps it in `MatchOutcome`; `ATTEMPT` drops it. Only the `SETTLE` frame becomes
   `RuntimeState`.
7. **A `Deriver` is a context-only producer** (no world reads); a `Computation` is the world-reading
   producer. Both write `ContextKey`s and are ordered by `requires()`.
8. **The engine is worker-only**, as in revision 3: snapshot capture, section copies, dirty marking,
   the capture gate, claim settlement, diagnostics and remedies are unchanged.

## 1. One-paragraph model

A **Blueprint** is matched against an immutable **LevelSnapshot** at a **Siting**, in one
**Stage**. A bay owns a `Cuboid`, a `Coverage`, a claim policy and a **Requirement**: `Check<T>`
units (a `Focus<T>`, an `Expectation<T>`) and computations. Section computations contribute
contributions to `ContextKey`s; a commit barrier folds them in canonical section order; aggregate
computations and checks read the folded values through the same `ContextView`. `Retention.CONTEXT`
values are published as versioned **ContextTier** frames owned by an instance, and only the
`SETTLE` frame is persisted as `RuntimeState`. Checking runs on a host worker over the snapshot;
capture, dirty refresh, claim settlement and world edits stay on the server thread.

## 2. Requirements traceability

| Id | Requirement | Mechanism | Residual risk |
| --- | --- | --- | --- |
| R-01 | Elements are not only blocks; entities and nested structures are elements | `OccupantSnapshot` + `OccupantKind`; `Focus.occupant/entity/nested`; window index + per-tick diff (§5.4) | window cost |
| R-02 | Version-independent kernel, baseline 1.21.1 NeoForge | pure `src/kernel`; `compileKernelJava`/`kernelPurity` | purity is an import scan |
| R-03 | Structure → runtime context | `Computation`/`Deriver` → `ContextKey` → tier frame → `RuntimeState` (§7, §8) | host `RUNTIME` upgrades |
| R-04 | Compute centre without a controller entity | `AnchorSpec` at the siting origin; `AnchorResolver` (§4.4) | property names are adapter-side |
| R-05 | Orientation from controller facing | anchor heading → one mapping formula (§4.4) | tie-break policy |
| R-06 | Heteromorphic predicates, rectangles only | one `Cuboid` per bay; `Focus` per kind; `Coverage` asserted; door is one bay | compound shapes need several bays |
| R-07 | rustc-grade errors/logs | `Reply` four-state; `Outcome` absence channel; `SC-FOC/SC-CHK/SC-BAY/SC-CLM/SC-CTX` (§6.6, §11.1) | wording untested |
| R-08 | Generational/staged checks, predicate order | engine-selected `Stage`; phase split; barrier; `ChangeSet` re-check; counter table (§8.2, §9.2) | liveness argued; the fast-fail needs a carrier (§16.1 item 16) |
| R-09 | Data-driven segments/definitions | canonical + compact JSON; one `Spec` family; schema 2 (§12) | computations remain verbose |
| R-10 | One universal kernel; extensions | `Catalogs` = expectations, focuses, computations, contextKeys, derivers, remedies, codes (§6.7) | catalog list closed |
| R-11 | Shared and exclusive claims | `Sharing` × `Footprint`; one matrix; claims are written only by `settle`, never by `check` (§10) | empty-volume protection is declared |
| R-12 | Visualization and auto-build | `RemedyContext` with an outcome, context and claims (§11.3) | provider quality untested |
| R-13 | Compositional, type-safe API | `ContextKey<T>`, `Check<T>`, typed `Bindings`; `Focus`/`Expectation` same `T` (§6) | `List<Check<?>>` erases in the planner |
| R-14 | Complex inside, simple outside | facade `check`/`settle`/`derive`/`solve`; `Formed` only from `settle` (§10.4) | lease discipline |
| R-15 | Reference code and proofs | §14 program; `kernelTest` named, not written | **no compiled test** |

## 3. Kernel and adapter

```
src/kernel/java      dev.vfyjxf.cumulus.kernel.**     Java 21 + JSpecify only
src/kernelTest/java  kernel tests                     JUnit 5 only
src/main/java        dev.vfyjxf.cumulus.**            mod + mc adapter
src/main/resources   metadata + data/<ns>/cumulus/structure/*.json
```

- No `net.minecraft`, `net.neoforged`, `com.mojang` under `kernel.**`.
- Adapter names are role-based: `LevelCapture`, `LevelSnapshot`, `WorldOccupants`,
  `OccupantDescriptors`, `OccupantTraits`, `OrientationMapping`, `SavedClaims`, `WorldEdits`,
  `ClientOverlay`, `DatapackBlueprints`, `FindingOutput`, `ServerState`, `JsonFrontend`.
- The package is the namespace; no `Mc`/`Vanilla` prefix.
- Host policy (worker counts, tick budgets, lease caps) stays outside the kernel; the kernel sees
  work units only.

## 4. Space, orientation, siting

- `LatticePoint`, `LatticeKey` (x:26 / z:26 / y:12), `SectionKey`.
- `Cuboid` half-open; `CuboidSet` normal form; `volume()` exact.
- `Heading` six directions; `Turn`/`Tilt`/`Flip`; `Orientation` 48 elements.
- `Placement` = orientation + offset; `Siting` = origin + placement; `Locus` frames
  `DEFINITION | LOCAL | WORLD | KEY`, where `KEY` carries `(InstanceId, NamespacedId)`.

### 4.4 Anchor and orientation

```java
record AnchorSpec(NamespacedId role, NamespacedId property, String value) {}

interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
}
```

- The anchor cell is the definition origin. The cell to read is `origin`, supplied by the caller,
  not derived from the orientation being chosen, so there is no circularity.
- `resolve` reads one cell, checks property/value, obtains a `Heading`, and returns the `Siting`
  whose placement maps the authored front onto that heading:
  `candidate.apply(authoredFront) == anchorHeading`.
- A blueprint with no anchor takes a `Siting` directly (commands, tools).

## 5. Occupants, snapshots, entities

### 5.1 Read model

```java
interface WorldView {
    SectionSlot slot(SectionKey key);
    OccupantSnapshot occupant(LatticeKey cell);
    @Nullable OccupantSnapshot occupant(OccupantRef ref);
    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
    @Nullable NestedLevelSnapshot nested(LatticeKey cell);
    long contentDigest(SectionKey key);
}
```

- `OccupantSnapshot(ref, kind, descriptor, footprint, traits, @Nullable nested)`.
- `OccupantRef(snapshot, type, kind, anchor)`: the `SnapshotId` it was minted under is part of the
  identity; the footprint belongs to the `OccupantSnapshot`, so `Footprint.Bound` resolves from the
  capture already in hand.
- `ReadStatus` = `READABLE | OUT_OF_BOUNDS | NOT_CAPTURED | DENIED | STALE`; air is readable,
  a refused cell is never air.
- `SectionSlot` = `Air(revision) | Present(SectionSnapshot) | Refused(status, code)`; a section
  snapshot holds a frozen container copy and a `contentDigest`.

### 5.2 Capture

- One server-thread call, `getChunkNow`, never `Level.getChunk`; no chunk loading.
- Sections requested by the plan's read closure plus entity windows.
- `CaptureResult(snapshot, CaptureIssues)`; a missing section is `Refused(NOT_CAPTURED)`.

### 5.3 Section store

Per-snapshot copies by default; `SectionStore` opt-in after profiling, keyed by
`(SectionKey, contentDigest)`, never aliasing a mutable container.

### 5.4 Entities

```java
record EntityKey(UUID id, NamespacedId type, long dataDigest) {}
record EntityWindow(Cuboid cells, NamespacedId type) {}
```

- `MAX_ENTITY_SPAN = 3`; a wider type is `SC-BAY-0110`. Windows are the declared box inflated by
  the span.
- `DirtyIndex` keeps `window → Set<InstanceRef>`. Each tick, per window, the host compares the live
  `cell → EntityKey` set with the captured one; a difference dirties the units that read the window.
  This is the outward-move witness.
- Join/leave marks the footprint. `ChangeSet(cells, sections, payloads, entities, keys)`.

### 5.5 Snapshot lease

```java
interface SnapshotLease extends AutoCloseable {
    SnapshotId id();
    WorldView view();
    long changeCount();                                 // marks since capture; zero means untouched
    boolean isValid();                                  // the world still exists and this handle is open
    Outcome<SnapshotLease> retain(PinBudget budget);   // Pinned | PinRefused
    boolean isOpen();
    @Override void close();
}
```

- `InstanceRecord` owns the capture; `Formed`/`Admitted` hold child handles. `retain` charges
  `retainedBytes` and can refuse (`SC-MC-0502`). `retainedBytes` and `cacheBytes` are separate.
- Both result arms are `AutoCloseable`; dropping one without closing is a tracked leak, not a lost
  view, because the instance keeps its own reference.

## 6. Predicates, focuses, checks

### 6.1 The predicate

```java
@FunctionalInterface
interface Expectation<T> {
    Reply test(ExpectationContext<T> ctx);
    Class<T> subject();                                             // the one subject type it accepts
    default ReadScope reads() { return ReadScope.none(); }          // declared reads beyond the bay
    default ExpectationMeta meta() { return ExpectationMeta.anonymous(); }
}

interface ExpectationContext<T> {
    T subject();
    BayView bay();
    WorldView view();
    Siting siting();
    Cuboid cells();
    ProbeSession session();
    ContextView context();
    Budget budget();
    Locus locus();
    Stage stage();
}
```

- `Reply` stays `MATCH | MISMATCH | UNREADABLE | ABSTAIN`, non-generic, with typed `Bindings`.
- `ExpectationMeta` = `id`, `specificity`, `requires` (`Set<ContextKey<?>>`), finding template.
  `specificity` is a report-order hint, not a budget number.
- The same interface serves element predicates (`T = OccupantSnapshot`), bay predicates
  (`T = BayView`) and state predicates (`T = S`).

### 6.2 Focus and Check

```java
interface Focus<T> {
    Outcome<T> take(FocusContext ctx);
    Class<T> subject();
    DecisionKind decisionKind();
    CuboidSet staticDecisionCells(Cuboid bayCells);
    ReadScope reads(Cuboid bayCells);
    FindingBundle describeMissing();
}

record Check<T>(NamespacedId id, NamespacedId bay,
                Focus<T> focus, Expectation<T> expectation,
                @Nullable String description) {

    public ReadScope reads(Cuboid bayCells) { return focus.reads(bayCells).withKeys(expectation.meta().requires()); }

    public CheckResult run(CheckContext ctx) {
        Outcome<T> taken = focus.take(ctx);
        if (taken instanceof Outcome.Found<T> found) {
            return new CheckResult(expectation.test(ctx.with(found.value(), found.subjects())),
                    found.subjects(), found.notes());
        }
        Outcome.Missing<T> missing = (Outcome.Missing<T>) taken;
        return new CheckResult(Reply.unreadable(focus.describeMissing()), List.of(), missing.findings());
    }
}

record Requirement(List<Check<?>> checks, List<Computation<?>> computations) {}
```

- `Check<T>` is the only place focus and expectation meet; the compiler enforces the same `T`.
- A focus failure is `Outcome.Missing` with `SC-FOC-*`; `test` is never called with a fabricated
  subject.
- A check belongs to exactly one bay (`SC-CHK-0107`).

### 6.3 Focuses

```java
Focus<OccupantSnapshot> Focuses.occupant(LatticeKey cell);
Focus<List<OccupantSnapshot>> Focuses.allCells();
Focus<BayView> Focuses.bay();
Focus<T> Focuses.context(ContextKey<T> key);
Focus<EntitySnapshot> Focuses.entity(UUID id);
Focus<NestedView> Focuses.nested(LatticeKey cell);
Focus<DoorView> Focuses.door(LatticeKey origin);
```

- `Focuses.context(key)` is exactly `ctx.context().require(key)`; absence is `Missing`, never a
  zero.
- A one-cell check uses `Focuses.occupant(cell)`; a door or multi-cell bay uses `Focuses.bay()` with
  an `Expectation<BayView>`; "every cell is a brick" is `Expectations.eachCell(block(...))`.
- `Focuses.door(origin)` yields a `DoorView` carrying both halves, so a door expectation never
  re-reads the world to find the second half.

### 6.4 Context keys and computations

```java
record ContextKey<T>(NamespacedId id, Class<T> type,
                     Retention retention,          // ATTEMPT | OUTCOME | CONTEXT
                     @Nullable Merge<T> merge,      // non-null: section contributions allowed
                     @Nullable T whenAbsent) {}

interface Merge<T> { T combine(T left, T right); }

interface Computation<T> {
    NamespacedId id();
    StatePhase phase();                 // SECTION | AGGREGATE; the engine assigns the stage
    ContextKey<T> output();
    Set<ContextKey<?>> inputs();
    CuboidSet region();
    Outcome<T> compute(ComputationContext ctx);
}

interface ComputationContext extends AggregateContext {
    @Nullable SectionContext section();   // non-null iff phase == SECTION
}

interface Deriver<T> {                 // no world reads; context-only producer
    ContextKey<T> output();
    Set<ContextKey<?>> requires();
    ContextTier tier();
    @Nullable T derive(DeriveContext ctx);
}

final class ContextKeys {
    public static <T> ContextKey<T> of(NamespacedId id, Class<T> type, Retention retention,
                                       @Nullable Merge<T> merge, @Nullable T whenAbsent) { ... }
    public static <T> ContextKey<T> register(CatalogsDraft draft, ContextKey<T> key) { ... }
}
```

- `StatePhase` is an enum: `SECTION`, `AGGREGATE`.

- **One key type.** `ContextKey` is the same type `StructureContext` is indexed by; there is no
  `StateKey` or `PartialKey`.
- `merge == null`: single writer, written with `put`. `merge != null`: section contributions,
  written with `contribute`, folded at the barrier.
- `phase` lives on the computation: `SECTION` runs per section, `AGGREGATE` runs once after the
  barrier. The key does not carry a phase.
- `Retention`: `ATTEMPT` dropped with the attempt, `OUTCOME` in `MatchOutcome.context`, `CONTEXT`
  published into the stage's frame.
- `whenAbsent` is honoured only when every section in the region was readable and no contribution
  was produced; it is visible in the document.
- Declarations are built with `ContextKeys.of(...)` and bound with `ContextKeys.register(draft, key)`; two registrations of one id are
  `SC-CTX-0102`; equality is `(id, type)`.

### 6.5 Coverage and claims on a bay

```java
record Bay(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
           Sharing sharing, Requirement requirement, @Nullable String description) {}

record BayBinding(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings) {}

enum Coverage { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }
enum Sharing { EXCLUSIVE, SHARED }
sealed interface Footprint permits Box, Bound, Declared, None { ... }
```

| Coverage | What a match must establish |
| --- | --- |
| `EVERY_CELL` | every cell of `bay.cells` is decided |
| `ONE_SUBJECT` | `bay.cells` equals the union of the bound subjects' footprints |
| `ANY_SUBJECT` | no cell asserted; a presence or state criterion |

The two axes are independent, so four rules cover every genuine contradiction and nothing
else needs listing: R1 (build) `EXCLUSIVE` needs a footprint other than `None` (`SC-CHK-0106`);
R2 (settle) `EXCLUSIVE` + `Bound` must resolve at least one cell (`SC-BAY-0111`);
R3 `ONE_SUBJECT` needs subject-kind checks and a bound footprint equal to `bay.cells`
(`SC-BAY-0107`); R4 `EVERY_CELL` needs per-cell checks covering the bay, and `ANY_SUBJECT`
forbids them (`SC-CHK-0106`). `24` is the owner of this vocabulary.

### 6.6 Outcome

```java
sealed interface Outcome<T> permits Found, Missing {
    record Found<T>(T value, List<OccupantRef> subjects, FindingBundle notes) implements Outcome<T> {}
    record Missing<T>(FindingBundle findings) implements Outcome<T> {}

    <R> R fold(BiFunction<T, List<OccupantRef>, R> onFound, Function<FindingBundle, R> onMissing);
}
```

- `Found.subjects` is the only way a binding is built; a door returns both halves, a golem one
  two-cell footprint.
- `Missing` carries findings with a `Locus` (`KEY` for a context absence, `WORLD`/`LOCAL` for a
  cell). A missing subject is never silence and never `test(null)`.
- Codes: `SC-FOC-0101` absent, `SC-FOC-0201` ambiguous, `SC-FOC-0301` refused section,
  `SC-FOC-0302` contribution missing.

### 6.7 Catalogs

`Catalogs` = `expectations`, `focuses`, `computations`, `contextKeys`, `derivers`, `remedies`,
`codes`. Each is a `Catalog<T>` keyed by `NamespacedId`, frozen at `apply`, resolved without
reflection. Kernel ships `block`, `air`, `trait`, `allOf`, `anyOf`, `not`, `eachCell`; focuses as
listed; computations `count`, `sum`, `volume`, `distinct`, `unionFind`; merges `sum`, `union`,
`min`, `max`, `and`, `or`.

## 7. Contributions, fold, order

### 7.1 Stage plan

For stage `S`: every unit of `expand(S)`. Order: section computations → barrier → aggregate
computations and derivers in Kahn order over `inputs()`/`requires()` (`SC-CHK-0102` on a cycle) →
checks, after the units they read. Independent units keep declaration order; cost is not a priority,
and there is no per-unit tie-break score.

### 7.2 Contribution and fold

- A section computation runs once per section in `region ∩ section` and calls
  `draft.contribute(key, partial)`.
- Contributions are sorted by `SectionKey` `(x, z, y)` and combined left-to-right with
  `merge.combine`.
- The fold starts at the first present contribution; there is no identity element.
- Any refused section in the region makes the key absent; a short sum is unrepresentable.
- Parallel section passes are allowed; the fold order is fixed, so the value is a function of the
  contribution set.

### 7.3 Reads and the barrier

- `SectionContext` exposes the section, its cells and occupants; it has no `read(ContextKey)`.
- `AggregateContext` exposes `read(ContextKey)` and the folded values; it cannot `contribute`.
- A check never runs before the barrier of its stage.
- `ContextView.get` is nullable for callers that can live without a value; `require` is the
  predicate path and returns `Outcome`.

### 7.4 Reply combination

Checks are independent; the attempt folds replies `UNREADABLE > MISMATCH > ABSTAIN > MATCH`.
`Bindings.merge` refuses a same-key-different-value pair (`SC-EXP-0003`).

## 8. Context, tiers, generations

### 8.1 Stage and tier

```
Stage      DESIGN   PROBE   FORM   SETTLE
Tier       BLUEPRINT  PROBE  FORM  SETTLE
Projection -          -      -     RUNTIME
```

`Stage` is filled by the engine, which is the only party that knows which step produced a finding;
no author declares one and no caller chooses one (`23` §7). `RUNTIME` has no producer in the
checking path: it is the persisted projection of the `SETTLE` tier plus host upgrades. A targeted
re-check is `check` with a `ChangeSet` on the request, not a re-run stage.

### 8.2 Counters

| Counter | Owner | Changes when | Invalidates |
| --- | --- | --- | --- |
| `mapGeneration` | host | a world reload | everything, including leases |
| `contentDigest` | capture | a section's frozen content differs | cache entries for that section |
| `cellStamp` | `DirtyIndex` | a cell/payload/entity mark | units whose read cells intersect |
| `contextRevision` | `ContextStore` per instance | a frame is published | downstream frames; `RuntimeState` |
| `generation` | `InstanceRecord` | a `Formed` is emitted | host caches keyed by generation |

`generation` increments on every emitted `Formed`, including a re-emission after
`DIRTY_UNSTABLE`.

### 8.3 Frames, draft, view

```java
record ContextFrame(ContextTier tier, long revision, Map<ContextKey<?>, Object> values) {}

record StructureContext(InstanceId instance, long revision, List<ContextFrame> frames)
        implements ContextView {}

public final class ContextDraft {                  // one stage's writes, not yet visible
    public ContextTier tier();  public long baseRevision();
    public <T> ContextDraft put(ContextKey<T> key, T value);          // merge == null only
    public ContextDraft forSection(SectionKey section);              // the scope contribute requires
    public <T> ContextDraft contribute(ContextKey<T> key, T partial); // merge != null only; needs forSection
    public <T> ContextDraft refuse(ContextKey<T> key);                // a section of the region was refused
    public <T> ContextDraft declined(ContextKey<T> key);              // every section readable, nothing contributed
    public <T> @Nullable T read(ContextKey<T> key);                   // the draft's own folded value
    public boolean publishes(ContextKey<?> key);                      // decides the key, value or absence
    public Set<ContextKey<?>> keys();                                 // every key this draft decides
}

interface ContextView {
    <T> @Nullable T get(ContextKey<T> key);
    <T> Outcome<T> require(ContextKey<T> key);
    ContextView at(ContextTier tier);
}

interface ContextStore {
    ContextDraft draft(ContextTier tier, long baseRevision);
    Outcome<Void> publish(ContextDraft draft);     // Missing(SC-CTX-0108) when base is stale
    StructureContext context(InstanceId instance);
}
```

- A computation writes into the attempt's draft; the matcher publishes one draft per stage
  boundary with the `baseRevision` it read.
- `CONTEXT` values go into the tier frame; `OUTCOME` into `MatchOutcome.context`; `ATTEMPT` is dropped.
- Two different values for one key in one frame is `SC-CTX-0107`; a stale base is `SC-CTX-0108`.
- A reader at tier `t` sees the newest frame at or below `t`.

## 9. Engine and execution

### 9.1 Request

```java
record MatchRequest(Blueprint blueprint, Siting siting, SnapshotLease lease, MatchPolicy policy,
                    InstanceBudget budget, PinBudget pins, InstanceRecord record, OwnerId owner,
                    @Nullable Ticket ticket) {}   // no stage: the engine selects it
                    // world() and the capture id are derived: lease.id()

record MatchPolicy(int maxSectionsPerUnit, CacheMode cache, boolean parallel) {}

MatchResult check(MatchRequest request);                                     // read-only
MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);     // the only writer
```

### 9.2 Capture check

`CaptureGate.evaluate(request.lease())` reads two facts off the lease: `changeCount() == 0` (the
watched region was not marked since capture) and `isValid()` (its world still exists and the handle
was not closed). A mark answers `CHANGED` and drops the attempt as `Dropped(SNAPSHOT_CHANGED)`; an
invalid capture answers `SUPERSEDED` and drops it as `Dropped(CAPTURE_INVALID)`. The slot's `Ticket`
is the scheduler's state and never travels in a capture, so a preview cannot invalidate a plan that
waits for settlement (`19` R37). A re-run is a new attempt over a fresh capture; a capture never
blends revisions.

### 9.3 One in-flight per `(world, owner)`

Newer requests coalesce by ticket; `cancel()` is cooperative.

### 9.4 Liveness

- Discard counters and the instance budget live in the slot, keyed by unit id; coalescing inherits
  them.
- After `maxDiscards` (default 3), a unit settles `Undecided(DIRTY_UNSTABLE)` with `SC-MC-0404`;
  the previous `Formed` is re-emitted with `generation + 1`.
- `DEFERRED` is terminal with a retry token; only `AUTO` re-submits automatically.
- `INTERACTIVE` holds one reserved permit; `AUTO` gets `max(0, threads - 1)` and is disabled on a
  one-worker host; `COMMAND` shares the rest.
- `autoChecksPerTick` and `autoSectionsPerTick` bound AUTO work; a retry is charged as another
  dispatch, not as a separate unit.

### 9.5 Billing

- The budget has two legs only: `cells` and `sections`. A check charges its declared read closure
  (`reads(bayCells).cells`) and the sections that closure touches; a section computation charges the
  slice volume plus one section; an aggregate charges nothing.
- Charged on dispatch, cached or not. `Spend(cells, sections)` is the whole ledger. There is no
  per-unit `admission`, no `checks` leg and no `retries` leg.
- Warm and cold runs of one plan spend the same, because both legs are charged before the cache is
  consulted.

### 9.6 Cache

```java
record CacheKey(PlanId plan, Siting siting, UnitId unit, long closureDigest) {}
```

`closureDigest` hashes the sorted `(SectionKey, contentDigest)` pairs the unit read. Entries hold
contributions and unit outcomes, never folded values. `cacheBytes` is LRU and separate from
`retainedBytes`.

## 10. Claims, settlement, results

### 10.1 One predicate

```java
sealed interface ClaimVerdict permits Settled, ClaimConflict, RevisionMoved {
    record Settled(long revision, List<ClaimRow> rows) implements ClaimVerdict {}
    record ClaimConflict(List<ClaimRow> rows, FindingBundle findings) implements ClaimVerdict {}
    record RevisionMoved(long expected, long actual) implements ClaimVerdict {}
}

record ClaimRow(World world, LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting,
                NamespacedId bay) {}
record ClaimSet(List<ClaimRow> rows, long revision) {}

sealed interface ClaimPlan permits ClaimPlan.Matched, ClaimPlan.Invalid {
    record Matched(long ledgerRevision, List<BayBinding> bindings) implements ClaimPlan {}  // no rows
    record Invalid(FindingBundle findings) implements ClaimPlan {}
}
```

- `ClaimMatrix.conflicts(a, b)` is the only conflict predicate; rows of another world, or of one
  `(owner, siting)`, are excluded there.
- `ClaimPlanner.derive(world, blueprint, siting, owner, bindings)` is the only row factory, and it is
  pure. `settle` runs it itself, after checking each binding against this blueprint and this capture
  (`SC-CLM-0005`), so a plan carries no rows for a caller to forge (I-16).
- `settle` replaces all rows of `(world, owner, siting)` in one CAS; `RevisionMoved` is retried once inside
  the transaction, then `Undecided(LEDGER_MOVED)` with a retry token.

### 10.2 Footprint

| Footprint | Owned cells |
| --- | --- |
| `Bound` (default) | union of non-air bound footprints |
| `Box` | `bay.cells` (a cavity or shell) |
| `Declared` | an explicit `CuboidSet` |
| `None` | none; reads anywhere |

- Claims protect what a structure occupies or declares, never what it reads. The pipe corner is
  `EVERY_CELL` + `Bound`: eight cells asserted, four owned.
- Uncovered blueprint bounds raise `SC-CLM-0104` (WARNING); the furnace cavity declares `Box`.
- `SC-BAY-0108` (ERROR) at SETTLE when a non-`None` bay's `reads().cells` leave
  `bay.cells ∪ footprint`.

### 10.3 Two entry points, one writer

`check` reads the world and the ledger and returns the verdict, the **plan** — the bindings it found
per bay and the ledger revision it read (`Admitted.planned()`, `Admitted.ledgerRevision()`) — and writes
nothing, so a probe, a preview, an overlay or a command may call it at any rate. `settle` is the only
path that writes: it takes the request and the admitted result, re-checks the capture and each
binding, **derives** the rows with the pure `ClaimPlanner`, and returns `Formed` — carrying
the `ClaimSet` and the revision written — or `Failed(SC-CLM-0002)` with the incumbent rows. A `check`
conflict is a prediction and says "currently held"; only a `settle` conflict is a conclusion.

### 10.4 Result

```java
sealed interface MatchResult extends AutoCloseable
        permits Admitted, Dropped, Formed, Failed, Undecided, Deferred {
    @Override default void close() {}                       // a result that holds no handle closes to nothing
    record Admitted(Stage stage, Siting siting, MatchOutcome outcome,
                    @Nullable ClaimPlan planned, long ledgerRevision,
                    SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }
    }
    record Dropped(Stage stage, DropCause cause, List<Finding> findings) implements MatchResult {}
    record Formed(Stage stage, InstanceId id, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long generation, long contextRevision, long ledgerRevision,
                  SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }
    }
    record Failed(Stage stage, List<Finding> findings) implements MatchResult {}
    record Undecided(Stage stage, UndecidedReason reason, List<Finding> findings,
                     @Nullable MatchOutcome partial, @Nullable RetryToken retry) implements MatchResult {}
    record Deferred(Stage stage, RetryToken token, List<Finding> findings) implements MatchResult {}
}
```

`Admitted` is not a structure; `derive` refuses it, and `planned` is `null` when there is nothing to
register. Only `settle` produces `Formed`.

## 11. Findings and remedies

### 11.1 Findings

- `Finding(id, code, severity, message, labels, notes, suggestion, related, stage)`; `Label(Locus,
  String, LabelRole)` on a `Locus` (`DEFINITION | LOCAL | WORLD | KEY`), the severity living on the
  `Finding` and never per label (`13` §4.1).
- Families: `SC-DOC`, `SC-BAY`, `SC-CHK`, `SC-EXP`, `SC-FOC`, `SC-CLM`, `SC-CTX`, `SC-MC`,
  `SC-RMD`.
- Focus codes: `SC-FOC-0101` absent, `SC-FOC-0201` ambiguous, `SC-FOC-0301` refused section,
  `SC-FOC-0302` contribution missing.
- Check codes: `SC-CHK-0102` cycle, `SC-CHK-0106` illegal bay policy, `SC-CHK-0107` check without a
  bay, `SC-CHK-0109` a key read before its stage.
- Bundle order: severity, stage, code, locus, insertion; dedup by `(code, primary label)`.

### 11.2 Explain

`/cumulus explain` renders the bundle plus the plan: stage, unit, cost in cells/sections, read
footprint, context keys read and published.

### 11.3 RemedyContext

```java
record RemedyContext(Blueprint blueprint, Siting siting,
                     @Nullable ClaimView claims, long ledgerRevision,
                     @Nullable MatchOutcome outcome, @Nullable StructureContext context,
                     RemedyOptions options) {}
```

- Providers read promoted values through `context`; a missing key or missing ledger means "cannot
  help". A provider never receives the lease; the executor holds it.
- `PlanValidator` folds the plan over the captured view to a post-state; a plan that changes a
  `ContextKey` any provider read is `SC-RMD-0309` and is refused.
- `EditExecutor.begin(WorldView, Siting, ClaimView, long ledgerRevision, PlanDigest)` re-checks
  claims and the digest at apply time; a settle between validate and apply refuses (`SC-RMD-0303`).
- `ApplyReport` returns a fresh `RemedyContext`.

## 12. Documents

### 12.1 Profile

- Canonical `BlueprintDocument`; compact profile; `DocumentProfile.AUTO` by shape.
- `MIN_VERSION = 1`, `MAX_VERSION = 2`; v1 is upgraded by `DocumentUpgrade.v1ToV2`.
- Both profiles are lossless: `bounds`, `segments`, `assembly`, `extensions` all have
  compact spellings; capture output is writable in the compact profile.
- Expansion is a function: a named bay's cells are carved out of earlier surfaces; a later write
  inside a named bay is `SC-DOC-0504`. `SC-DOC-0503` is a named-bay split; `SC-DOC-0505` is
  `EmptyCuboid`.
- Boxes are `[originX, originY, originZ, sizeX, sizeY, sizeZ]`.

### 12.2 Specs

```java
record Spec(NamespacedId kind, DataView args,
            @Nullable SpecRef focus, @Nullable SpecRef expectation,
            @Nullable ClassRef subject, @Nullable KeyRef output,
            @Nullable StatePhase phase, @Nullable Retention retention,
            @Nullable List<KeyRef> inputs) {}
```

`SpecResolver` runs during `compile` (DESIGN): it resolves the focus and the
expectation through `Catalogs`, checks `focus.subject() == expectation.subject()`, validates
the key declaration, and rejects a mismatch at DESIGN (`SC-CHK-0101`). Unknown ids are findings,
never reflection.

### 12.3 Compact form

```json
{
  "schemaVersion": 2,
  "id": "mymod:blast_furnace",
  "bounds": [0, 0, 0, 7, 6, 7],
  "shell": [{"box": [0,0,0,7,6,7], "block": "minecraft:blast_furnace"}],
  "bays": [
    {"id": "mouth", "box": [3,1,0,1,2,1], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "bound",
     "expect": {"block": "minecraft:iron_bars"}},
    {"id": "interior", "box": [1,1,1,5,4,5], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "box",
     "expect": {"air": true}}
  ],
  "checks": [
    {"id": "mouth_facing", "bay": "mouth",
     "focus": {"kind": "occupant", "cell": [3,1,0]},
     "expect": {"kind": "state", "property": "facing", "value": "east"}}
  ],
  "computations": [
    {"id": "volume", "phase": "section", "bay": "interior",
     "output": "mymod:volume", "kind": "count", "args": {"mode": "cells"}},
    {"id": "capacity", "phase": "aggregate",
     "output": "mymod:capacity", "inputs": ["mymod:volume"],
     "kind": "scale", "args": {"factor": 4}}
  ],
  "context": [
    {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
    {"id": "mymod:capacity", "type": "long", "retention": "context"}
  ],
  "anchors": [{"role": "front", "property": "facing", "value": "east"}],
  "orientation": {"front": "north"}
}
```

- `interior` is 5×4×5 = 100 cells; `volume` counts cells; `capacity = 400`.
- `merge` present means section contributions; `phase: section` requires it, `phase: aggregate`
  forbids it.
- The named `interior` bay carves its cells out of `shell`; a `fills` entry inside it would be
  `SC-DOC-0504`.
- A `context[]` entry may carry `whenAbsent` (decoded against `type`); this example omits it because
  a missing section must make `volume` unreadable.
- A check that reads a key uses `{"kind": "context", "key": "mymod:volume"}` as its focus.

### 12.4 Lowering

- A named bay is emitted whole; a named bay that cannot be one cuboid is `SC-DOC-0503`.
- A holed shell lowers to synthetic bays whose ids derive from their own boxes:
  `#box@x,y,z:sx,sy,sz`. Never counters.
- Named bays carve their cells out of earlier surfaces; a later write inside one is
  `SC-DOC-0504`, never a silent merge.

### 12.5 Capture and import

- `CaptureTool.capture(CaptureRequest, IssueSink) : @Nullable DataView`; any loss is an issue and a
  null return.
- Capture writes `extensions["cumulus:capture"]` with `stateLossy`, `issues`, `toolVersion`; the
  compiler ignores it and round-tripping preserves it.
- Contents: `{"kind": "container", "items": {...}}` when readable, else `SC-DOC-0521` and the
  import refuses the BE rather than placing an empty one.

## 13. Lifecycle walkthrough

**Huge furnace, AUTO.** A block changes; one section is marked; the read closure is captured;
`PROBE` runs two `Check<OccupantSnapshot>` units and returns `Admitted(PROBE)`. The lease moves to
the `FORM` submit. `FORM` runs the shell checks, the `volume` section computation over the 5×4×5
cavity, folds contributions with `Merges.sum()`, writes `capacity = 400`, and publishes both
`CONTEXT` keys at FORM revision 1. `SETTLE` settles the shell and cavity claims on the server thread
and returns `Formed` with `ledgerRevision`. `derive` reads the FORM frame, persists `RuntimeState`
at SETTLE, and the child handle closes.

**Door, FORMed.** `Focuses.bay()` + `Expectation<BayView>`: the expectation sees both halves through
`BayView.occupantAt`, tests hinge/facing, and the bay claims both cells. A neighbour's door hits the
same two cells: one settles `EXCLUSIVE`, the other gets `Failed(SC-CLM-0002)`.

**Shared hatch.** A and B both `SHARED`; both settle. A later `EXCLUSIVE` conflicts and fails.

**Pipe corner.** 2×2×2 bbox, four pipe blocks, four air cells: `EVERY_CELL` + `Bound` asserts eight
through `Focuses.allCells()`, owns four. The neighbour's corner claims its own four.

**Entity pen.** `Focuses.entity(cow)` in a `ANY_SUBJECT` bay. A cow walks out; the per-tick window diff
dirties the check and `SETTLE` re-runs. A type wider than the span is `SC-BAY-0110`.

**Iron count.** A section `Computation` contributes per section; `Merges.sum()`; `Focuses.context(IRON)`
+ `Expectation<Long>` (`n >= 64`). A refused middle section makes the key absent, `require` returns
`Missing`, and the check is `UNREADABLE`, never `MISMATCH`.

## 14. Proofs and the falsification program

| # | Claim | Mechanism | Status |
| --- | --- | --- | --- |
| P1 | Warm cannot form where cold was budget-exhausted | cells + sections charged before the cache (§9.5) | argument |
| P2 | Claims are total and conflict-checked | one matrix; one CAS; `RevisionMoved` (§10.1) | argument |
| P3 | A `Formed` is committable and current | settle before `Formed`; revisions carried (§10.4) | argument |
| P4 | A capture is released exactly once | one owner, child handles, failable retain (§5.5) | argument |
| P5 | Absence is never a number | no identity; a hole defeats the fold; `require` returns `Missing` (§7.2) | argument |
| P6 | A section unit cannot read aggregate state | `SectionContext` has no `read(ContextKey)` (§7.3) | argument |
| P7 | Orientation is a function of the anchor | anchor at origin; one mapping direction (§4.4) | argument |
| P8 | The gate is live and sound | two lease facts (`changeCount`, `isValid`); slot counters; fresh capture per attempt (§9.2, §9.4) | argument |
| P9 | Parallel does not change result or spend | fixed fold order; cells/sections billing; sorted findings (§7.2, §9.6) | argument |
| P10 | Publication has one writer and one order | one store per instance; `baseRevision` (§8.3) | argument |
| P11 | A short check cannot claim a whole bay | the four rules of §6.5; `Found.subjects` | argument |
| P12 | The kernel is host-free | import scan | **incomplete** |

Falsification program (`kernelTest`, R-15):

1. `FurnaceScenario` — shell + cavity `Box` + contribution + fold + context.
2. `DoorScenario` — one bay, two subjects, `ONE_SUBJECT` coverage, atomic claim.
3. `HatchScenario` — shared/exclusive, `(world, owner, siting)` replace, `RevisionMoved`.
4. `PipeCornerScenario` — `EVERY_CELL` + `Bound`, air asserted but not owned.
5. `EntityPenScenario` — window diff without a block change.
6. `AbsenceScenario` — one refused middle section → `UNREADABLE`, never a short sum.
7. `GateScenario` — churn past `maxDiscards` → `DIRTY_UNSTABLE`, `generation + 1`.
8. `PhaseScenario` — a `SectionContext` cannot call `read` (compile failure is the test).
9. `PublishScenario` — a stale `baseRevision` is refused.

## 15. Dispositions

### 15.0 Correction: one key, not three

Revisions 3 and 4 split intermediate state into `Key`/`PartialKey`/`StateKey` with a `Reducer`.
That duplicated the existing context API and made intermediate state look like a second mechanism.
Revision 5 keeps **one** `ContextKey`, adds three increments to the context API, and deletes the
extra key types:

| Added to the context API | Purpose |
| --- | --- |
| `ContextKey.merge` | marks a key as section-contributed |
| `ContextDraft.contribute` | section-phase write |
| `ContextView.require` | absence as `Outcome`, not a nullable unbox |

`StateKey`, `PartialKey`, `Reducer`, `SectionComputation` and `AggregateComputation` no longer
exist; a computation declares `phase`.

### 15.1 Round 1 — requirements

| # | Defect | Ruling |
| --- | --- | --- |
| D1 | Two computation models | **Fixed**: one `Computation`/`ContextKey`; `Deriver` is context-only (§6.4) |
| D2 | `MATCH` claims the whole bay | **Fixed**: footprint; `Found.subjects` (§6.5, §6.6) |
| D3 | Checks have no id or stage | **Fixed**: `Check` carries `id` (§6.2); its stage was removed by `23` §7 |
| D4 | Anchor facing never reaches the kernel | **Fixed**: anchor at origin, `AnchorResolver` (§4.4) |
| D5 | Entities read but not owned | **Partly**: windows, diff, `ChangeSet.entities`; ownership cell-based (§5.4) |
| D6 | Focus failure has no channel | **Fixed**: `Outcome.Missing`, `SC-FOC`, `Locus.KEY` (§6.6) |
| D7 | Four promotion knobs; remedies blind | **Fixed**: `Retention`; `RemedyContext.context` (§11.3) |
| D8 | Three counters | **Fixed**: counter table (§8.2) |
| D9 | No data form for checks/computations | **Fixed**: `Spec` + schema 2 (§12.2) |
| D10 | Settlement outside the match | **Fixed**: facade-level settle; `Formed` after `Settled` (§10.3) |
| D11 | PROBE `Formed` type-identical to settled | **Fixed**: `Admitted` vs `Formed` (§10.4) |
| D12 | No sketch compiles; nanos | **Partly**: nanos gone; test absent (§14) |

### 15.2 Round 1 — type, state, context

| # | Defect | Ruling |
| --- | --- | --- |
| D1 | Chunk-alignment-dependent verdict | **Fixed**: no per-section check phase (§7.1) |
| D2 | Absence folds to identity | **Fixed**: no identity; a hole defeats the fold (§7.2) |
| D3 | Key equality includes a lambda | **Fixed**: `(id, type)`; catalog owns uniqueness (§6.4) |
| D4 | `T` erased on the data path | **Partly**: `Check<T>` compile-time; `SpecResolver` at DESIGN (§12.2) |
| D5 | Two promotion authorities | **Fixed**: one store, `baseRevision` (§8.3) |
| D6 | Tier lineage erased; `STALE` has no tier | **Fixed**: frames `(tier, revision)`; per-instance store (§8.3) |
| D7 | Focus failure has no `Reply` channel | **Fixed**: `Missing` → `unreadable` (§6.6) |
| D8 | Step cache vs state cache | **Fixed**: one cache holding contributions/outcomes (§9.6) |

### 15.3 Round 1 — runtime, authoring, claims, remedies

| # | Defect | Ruling |
| --- | --- | --- |
| D1 | Adapter's weaker conflict predicate | **Fixed**: one matrix (§10.1) |
| D2 | Lease ownership | **Fixed**: one owner, child handles, failable retain (§5.5) |
| D3 | Entity movement has no key or witness | **Fixed**: windows, window index, per-tick diff (§5.4) |
| D4 | Claim footprint excludes judged cells | **Partly**: `Bound`/`Box`/`Declared`; uncovered bounds WARNING (§10.2) |
| D5 | Gate not live | **Fixed**: `CaptureGate.evaluate(lease)` runs in the kernel before anything is published or settled; slot coalescing stays host-side (§9.2, §9.4) |
| D6 | Cache/cost/identity contradiction | **Fixed**: cells/sections billing + closure sections, contributions-only cache (§9.5, §9.6) |
| D7 | `Spec` has no data form | **Fixed**: `Spec` (§12.2) |
| D8 | Compact lowering destroys bay identity | **Fixed**: named bays whole, carve-outs, box-derived ids (§12.4) |
| D9 | Capture cannot report loss | **Fixed**: `IssueSink`, capture extension, `container` (§12.5) |
| D10 | `RemedyContext` blind | **Fixed**: outcome, context, claims, revision (§11.3) |

### 15.4 Round 2 — semantics, types, runtime

| Area | Defects | Ruling |
| --- | --- | --- |
| Semantics | `Sharing` missing; door two subjects; furnace JSON; pipe corner; context identity; entity focus; orientation; derive order; lease transfer | `Bay` carries footprint/sharing; door is one `BayView`; per-instance context; anchor at origin (§6, §8, §10) |
| Types/data | fold with a hole; `ONE_SUBJECT`+`Box`; phase by convention; key identity; lowering; schema losslessness; merge laws | hole defeats the fold; footprint rule; `SectionContext` has no `read`; `Merge` associative by contract; carve-outs; schema 2 (§6, §7, §12) |
| Runtime | gate; `RevisionMoved`; `Bound`; lease owners; coalesced lease; discard counters; entity witness; remedy pre-state; billing; publish order | capture gate; `RevisionMoved`; footprint rule; one owner per world; the scheduler closes only unconcluded leases; slot counters; window diff; `SC-RMD-0309`; cells/sections billing; `baseRevision` (§5, §9, §10, §11) |

### 15.5 Mirror discipline

Every English original is mirrored under `zh/`, and the English set is the design of record wherever
the two differ. A change to an English file that leaves its mirror behind is a defect to fix in the
same change, not a deferred item. The two decisions of `23` and `24` reach every unit file by the
same rule: the claim entry points, the claim vocabulary, the document schema that carries it, and the
code table that names it.

## 16. Open questions

1. **Empty-volume protection.** `Box` is the declaration that closes the pen and furnace holes;
   is a per-blueprint `sealed: true` flag better than a warning plus per-bay choice?
2. **Window cost.** `MAX_ENTITY_SPAN = 3` and a per-tick diff; is 200 windows acceptable, or does
   the host need a shared spatial index?
3. **`whenAbsent`.** Honoured only when every section was readable; is that too strict for an
   optional decorative region?
4. **`RevisionMoved` retry.** One retry inside the transaction: is a second movement a retry token
   or a hard `Undecided`?
5. **Compact computations.** `context[]` + `computations[]` is correct but verbose; does the common
   `count`/`volume` case need a shorthand?
6. **`Coverage.ANY_SUBJECT`.** A presence-only bay asserts no cells; can it ever be `EXCLUSIVE`
   without a hole?

### 16.1 Round-3 residue

These were found by the round-3 pass and are open: the sweep of `23`/`24` does not decide them, and
each one changes a mechanism rather than a sentence. An item whose heading says "decided" names the
ruling that closed it and is no longer open.

7. **Plan authentication — decided (`19` R36).** The plan carries no rows at all: it holds the bindings
   a check found and the revision it read, and `settle` derives every row with the pure `ClaimPlanner`
   after checking each binding against this blueprint and this capture (`SC-CLM-0005`). There is
   nothing to forge, so no digest is needed (I-16).
8. **What `check` may publish.** A read-only call currently reaches the per-instance context store and
   can re-emit a previous result. Either `check` publishes nothing, or `Retention.CONTEXT` is defined as
   a write and `check` loses the tier frame (`SC-CTX-0108`'s `baseRevision` is the existing guard).
9. **The gate for a separated `settle` — decided (`19` R37).** `settle` runs the same
   `CaptureGate.evaluate(request.lease())` the attempt ran, so it re-reads the two lease facts and
   nothing else: no ticket, no second capture, no supplier. The scheduler owns supersession.
10. **`release` then `settle` on a move.** The two halves are not atomic, so a failed second half leaves
    an `EXCLUSIVE` bay owning nothing — the state R2 refuses at settle time. One transaction, a
    tombstone, or a compensating re-claim?
11. **Claim-row world identity — decided (`19` R35).** A `ClaimRow` carries its `World`, the ledger
    handle is bound to one world, and `ClaimMatrix.conflicts` compares the world first; a settle whose
    parts disagree is `SC-CLM-0007`. The earlier note that the predicate "compares cells only" no
    longer holds.
12. **The phase split of `compute`.** `ComputationContext extends AggregateContext`, so a section unit
    can still call `read(ContextKey)`, which is what P6's "compile failure" proof assumed impossible.
    Split into `compute(AggregateContext)` and `compute(SectionContext, SectionDraft)`, with the draft
    exposing `contribute` only?
13. **`Footprint` and the frame.** `Box`/`Declared` are blueprint-local, `Bound` is world cells; one
    `resolve` returning an untagged `CuboidSet` lets a caller translate uniformly and write rows in the
    wrong frame. Does the case carry the frame?
14. **`DecisionKind` against `Coverage`.** The three kind names and the three `Coverage` names describe
    the same distinction; B-9/B-10 and R4 are stated over the decision set, which only `Focus`
    carries. One vocabulary, or two?
15. **The `P5` hole for a denied unit.** Only a `Refused` section makes a key absent; a `DENIED` cell
    arrives as `Outcome.Missing` and the fold becomes a short sum. Does a unit's `Missing` make the key
    absent for every consumer?
16. **What replaces `Stage` in `I-6` and `R-08`.** With stages no longer declared, the checkpoint that
    `I-6` requires and the fast-fail that `R-08` names need another carrier — the engine's own phase
    order, or the plan's step structure? The same answer settles `09`'s B-11/F-8 (`SC-CHK-0109`, a
    check reading a key produced "at a later stage"), the host's `PROBE`/`FORM` levels in `16`, and
    how a computation declares where it runs.
17. **`ONE_SUBJECT` + `Box`.** R3 refuses only a *bound* footprint that differs from `bay.cells`, and
    `Box` equals the bay by construction, so a `ONE_SUBJECT` bay with `Footprint.Box` is legal today —
    while it would own the air around its subject rather than the subject's own cells. Should that
    combination be refused, or is owning the bay's air a deliberate choice?

## 17. Adversarial protocol

Round 1 attacked coverage, the typed model, runtime and authoring. Round 2 attacked revision 2 on
semantics, types/data and runtime. Round 3 attacked revision 5 on the same three fronts plus a
spec-against-code audit; its findings are in `_work/round3/`, its residue is §16.1, and the sweep
that carries the decided part into the unit files is recorded in `19`.

A critic writes a ranked defect list: scenario, violated requirement or invariant, why the stated
proof fails, minimal fix. The lead disposes, revises, and records the surviving decisions in `19`.
The round still owes one **compiled** `kernelTest` sketch, so that a signature which cannot express a
case fails the build.
