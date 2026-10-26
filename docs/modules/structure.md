# Module `kernel.structure`

> Rank 4. The author's model: a bay, a check, a focus, an expectation, and the immutable blueprint
> that ties them together. This is the largest module because it is where the library's vocabulary
> becomes type-safe; it still owns no world, no claim row and no context store.

## 1. Scope

**Owns:** `Bay`, `Coverage`, `Check`, `CheckResult`, `Focus`, `FocusResult`, `FocusContext`,
`Focuses`, `Expectation`, `ExpectationContext`, `ExpectationMeta`, `Expectations`, `Reply`,
`ReadScope`, `BayView`, `DoorView`, `Seam`, `SeamId`, `SeamKind`, `AnchorSpec`, `AnchorRoles`,
`OrientationPolicy`, `Anchors`, `DerivationSpec`, `Blueprint`, `BlueprintBuilder`, `Plan`,
`Computation`, `SectionComputation`, `AggregateComputation`, `SectionContext`, `AggregateContext`,
`Computations`, `FocusFactory`, `ExpectationFactory`, `ComputationFactory`, `FactoryContext`.

**Does not own:** `Sharing`/`Footprint` (`claim`), the occupant model (`world`), context values or
their store (`context`), the engine loop (`engine`), the document IR (`document`).

**Imports:** `core`, `space`, `finding`, `world`, `claim`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `Bay` | record | id + `CuboidSet` cells + coverage + footprint + sharing |
| `Coverage` | enum | `EVERY_CELL`, `ONE_SUBJECT`, `ANY_SUBJECT` |
| `Check<T>` | record | id + bay + focus + expectation + description |
| `CheckResult` | record | reply + subjects + non-fatal notes |
| `Focus<T>` | interface | binds a subject or explains why not |
| `FocusResult<T>` | sealed | `Taken(value, subjects, notes)`, `Unavailable(findings)` |
| `FocusContext` | interface | what a focus sees: bay, view, siting, cells, phase, locus, context, budget |
| `Expectation<T>` | interface | one test over one subject type |
| `ExpectationContext<T>` | interface | focus context + the subject and its re-typing |
| `ExpectationMeta` | record | id, specificity, required keys, default mismatch code |
| `Reply` | sealed | `Match`, `Mismatch`, `Unreadable`, `Abstain` |
| `ReadScope` | record | cells + keys + entity windows |
| `BayView` | record | a bay's own read face |
| `DoorView` | record | the two halves of a door + its bay |
| `Focuses` | final class | the shipped focus kinds |
| `Expectations` | final class | the shipped expectation combinators and leaves |
| `Seam` / `SeamId` / `SeamKind` | record / record / enum | a declared connectable edge |
| `AnchorSpec` / `AnchorRoles` | record / final class | one property at the definition origin |
| `OrientationPolicy` | record | allowed orientations + primary role + upright promise |
| `Anchors` | final class | the one anchor read |
| `DerivationSpec` | record | deriver id + args + output key id + order |
| `Blueprint` | final class | immutable compiled template with a cached plan |
| `BlueprintBuilder` | final class | fluent construction; validates at `build()` |
| `Plan` | record | section units, aggregate units, checks |
| `Computation<T>` | interface | id + output + inputs + region |
| `SectionComputation<T>` | interface | `compute(SectionContext)` |
| `AggregateComputation<T>` | interface | `compute(AggregateContext)` |
| `SectionContext` / `AggregateContext` | interfaces | what a computation sees |
| `Computations` | final class | `count`, `volume`, `air` |
| `FocusFactory` / `ExpectationFactory` / `ComputationFactory` | interfaces | data-driven constructors |
| `FactoryContext` | record | document id, source, finding reporter |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.structure;

public record Bay(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                  Sharing sharing, @Nullable String description) {
    public Bay { /* id non-null; cells non-empty; footprint non-null; sharing non-null */ }
    public static Bay of(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                         Sharing sharing);
    public static Bay of(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                         Sharing sharing);
    public static Bay of(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                         Sharing sharing, String description);
}

public enum Coverage { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }

public record Check<T>(NamespacedId id, Bay bay, Focus<T> focus, Expectation<T> expectation,
                       @Nullable String description) {
    public Check { /* id non-null; bay non-null; focus.subject() == expectation.subject() */ }
    public static <T> Check<T> of(NamespacedId id, Bay bay, Focus<T> focus,
                                  Expectation<T> expectation);
    public ReadScope reads();
    public CheckResult run(FocusContext ctx);
}

public record CheckResult(Reply reply, java.util.List<OccupantSnapshot> subjects,
                          FindingBundle notes) { }

public sealed interface FocusResult<T> {
    record Taken<T>(T value, java.util.List<OccupantSnapshot> subjects,
                    FindingBundle notes) implements FocusResult<T> { }
    record Unavailable<T>(FindingBundle findings) implements FocusResult<T> {
        public Unavailable { /* findings non-empty */ }
    }
    <R> R fold(java.util.function.BiFunction<T, java.util.List<OccupantSnapshot>, R> onTaken,
               java.util.function.Function<FindingBundle, R> onUnavailable);
}

public interface Focus<T> {
    FocusResult<T> take(FocusContext ctx);
    Class<T> subject();
    CuboidSet staticCells(CuboidSet bayCells);
    default ReadScope reads(CuboidSet bayCells);
    default boolean bindsSubjects();
    default FindingBundle describeMissing();
}

public interface FocusContext {
    BayView bay();
    WorldView view();
    Siting siting();
    InstanceId instance();
    CuboidSet cells();
    Phase phase();
    Locus locus();
    ContextView context();
    Budget remaining();
    CancelToken cancel();
    @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    java.util.List<ClaimRow> claimsAt(LatticePoint cell);
}

public interface Expectation<T> {
    Reply test(ExpectationContext<T> ctx);
    Class<T> subject();
    default ExpectationMeta meta();
    default ReadScope reads();
}

public interface ExpectationContext<T> extends FocusContext {
    T subject();
    java.util.List<OccupantSnapshot> subjects();
    <V> ExpectationContext<V> withSubject(V subject, java.util.List<OccupantSnapshot> subjects);
    static <T> ExpectationContext<T> of(FocusContext base, T subject,
                                        java.util.List<OccupantSnapshot> subjects);
}

public record ExpectationMeta(NamespacedId id, int specificity,
                              java.util.Set<ContextKey<?>> requires,
                              FindingCode mismatchCode) {
    public static ExpectationMeta anonymous();
    public static ExpectationMeta of(NamespacedId id, int specificity, FindingCode mismatchCode);
}

public sealed interface Reply {
    record Match() implements Reply { }
    record Mismatch(FindingBundle findings) implements Reply {
        public Mismatch { /* findings non-empty */ }
    }
    record Unreadable(FindingBundle findings) implements Reply {
        public Unreadable { /* findings non-empty */ }
    }
    record Abstain() implements Reply { }

    static Reply match();
    static Reply mismatch(Finding first, Finding... rest);
    static Reply mismatch(FindingBundle findings);
    static Reply unreadable(Finding first, Finding... rest);
    static Reply unreadable(FindingBundle findings);
    static Reply abstain();
}

public record ReadScope(CuboidSet cells, java.util.Set<ContextKey<?>> keys,
                        java.util.List<EntityWindow> windows) {
    public static ReadScope none();
    public static ReadScope cells(CuboidSet cells);
    public ReadScope union(ReadScope other);
    public ReadScope withKeys(java.util.Set<ContextKey<?>> keys);
    public ReadScope withWindows(java.util.List<EntityWindow> windows);
}

public record BayView(NamespacedId id, CuboidSet cells, WorldView view, Siting siting) {
    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    public java.util.List<OccupantSnapshot> occupants();
    public java.util.List<OccupantSnapshot> occupants(OccupantFilter filter);
}

public record DoorView(OccupantSnapshot first, OccupantSnapshot second, BayView bay) { }

public final class Focuses {
    public static Focus<OccupantSnapshot> occupant(LatticePoint cell);
    public static Focus<BayView> bay();
    public static Focus<java.util.List<OccupantSnapshot>> allCells();
    public static Focus<OccupantSnapshot> entity(NamespacedId type);
    public static Focus<WorldView> nested(LatticePoint cell);
    public static Focus<DoorView> door(LatticePoint origin);
    public static <T> Focus<T> context(ContextKey<T> key);
    private Focuses() {}
}

public final class Expectations {
    public static Expectation<OccupantSnapshot> block(NamespacedId type);
    public static Expectation<OccupantSnapshot> air();
    public static Expectation<OccupantSnapshot> property(String key, String value);
    public static Expectation<OccupantSnapshot> trait(TraitKey<?> key);
    public static <T> Expectation<OccupantSnapshot> trait(TraitKey<T> key,
                                                          java.util.function.Predicate<T> test);
    public static Expectation<OccupantSnapshot> anyOccupant();
    public static <T> Expectation<T> allOf(Expectation<T>... children);
    public static <T> Expectation<T> anyOf(Expectation<T>... children);
    public static <T> Expectation<T> sequence(Expectation<T>... children);
    public static <T> Expectation<T> not(Expectation<T> child);
    public static <T> Expectation<T> atLeast(int count, Expectation<T>... children);
    public static <T> Expectation<T> atMost(int count, Expectation<T>... children);
    public static <T> Expectation<T> exactly(int count, Expectation<T>... children);
    public static Expectation<BayView> eachCell(Expectation<OccupantSnapshot> element);
    public static <T> Expectation<T> custom(Class<T> subject, ExpectationMeta meta,
                                            java.util.function.Function<ExpectationContext<T>, Reply> body);
    private Expectations() {}
}

public record SeamId(NamespacedId id) implements Comparable<SeamId> {
    @Override public int compareTo(SeamId other);
}

public enum SeamKind { ITEM, FLUID, ENERGY, STRUCTURAL, CUSTOM }

public record Seam(SeamId id, Cuboid cells, SeamKind kind,
                   java.util.Map<String, String> properties) {
    public static final String CUSTOM_KIND = "kind";
    public boolean compatibleWith(Seam other);
}

public record AnchorSpec(NamespacedId role, String property, String value) { }

public final class AnchorRoles {
    public static final NamespacedId PRIMARY;
    public static final NamespacedId FRONT;
    public static final NamespacedId CENTER;
    private AnchorRoles() {}
}

public record OrientationPolicy(NamespacedId primaryRole, java.util.List<Orientation> allowed,
                                boolean uprightOnly) {
    public static Builder builder(NamespacedId primaryRole);
    public @Nullable Orientation select(Heading anchorHeading);

    public interface Builder {
        Builder allow(Orientation... orientations);
        Builder allowTurns(Turn... turns);
        Builder uprightOnly();
        OrientationPolicy build();
    }
}

public final class Anchors {
    public static Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
    private Anchors() {}
}

public record DerivationSpec(NamespacedId deriver, Value arguments, NamespacedId output, int order) {
    public DerivationSpec { /* order >= 0; arguments non-null */ }
}

public interface FocusFactory {
    NamespacedId id();
    @Nullable Focus<?> create(Value args, FactoryContext context);
}

public interface ExpectationFactory {
    NamespacedId id();
    @Nullable Expectation<?> create(Value args, FactoryContext context);
}

public interface ComputationFactory {
    NamespacedId id();
    @Nullable Computation<?> create(Value args, ContextKey<?> output, CuboidSet region,
                                   FactoryContext context);
}

public record FactoryContext(NamespacedId document, @Nullable SourceSpan source,
                             java.util.function.Function<NamespacedId, @Nullable ContextKey<?>> keys,
                             java.util.function.Consumer<Finding> report) { }

public interface Computation<T> {
    NamespacedId id();
    ContextKey<T> output();
    java.util.Set<ContextKey<?>> inputs();
    CuboidSet region();
}

public interface SectionComputation<T> extends Computation<T> {
    Outcome<T> compute(SectionContext ctx);
}

public interface AggregateComputation<T> extends Computation<T> {
    Outcome<T> compute(AggregateContext ctx);
}

public interface SectionContext {
    SectionKey section();
    CuboidSet cells();
    WorldView view();
    Siting siting();
    @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    java.util.List<OccupantSnapshot> occupants();
    java.util.List<OccupantSnapshot> occupants(Cuboid box);
    Budget remaining();
    CancelToken cancel();
    void report(Finding finding);
}

public interface AggregateContext {
    ContextView context();
    WorldView view();
    Siting siting();
    java.util.List<OccupantSnapshot> occupants(Cuboid box);
    Budget remaining();
    CancelToken cancel();
    void report(Finding finding);
}

public final class Computations {
    public static SectionComputation<Long> count(ContextKey<Long> output, CuboidSet region);
    public static SectionComputation<Long> volume(ContextKey<Long> output, CuboidSet region);
    public static SectionComputation<Long> air(ContextKey<Long> output, CuboidSet region);
    private Computations() {}
}

public record Plan(java.util.List<SectionComputation<?>> sections,
                   java.util.List<AggregateComputation<?>> aggregates,
                   java.util.List<Check<?>> checks) { }

public final class Blueprint {
    public NamespacedId id();
    public Cuboid bounds();
    public java.util.List<Bay> bays();
    public java.util.List<Check<?>> checks();
    public java.util.List<Computation<?>> computations();
    public java.util.List<Seam> seams();
    public java.util.List<AnchorSpec> anchors();
    public @Nullable OrientationPolicy orientation();
    public java.util.List<DerivationSpec> derivations();
    public Value arguments();
    public @Nullable SourceSpan source();
    public Plan plan();
    public @Nullable Bay bay(NamespacedId id);
    public @Nullable Check<?> check(NamespacedId id);
    public @Nullable Seam seam(SeamId id);
    public @Nullable AnchorSpec anchor(NamespacedId role);
    public CuboidSet bayCells();
    public static BlueprintBuilder builder(NamespacedId id);
    @Override public boolean equals(Object other);
    @Override public int hashCode();
    private Blueprint() {}
}

public final class BlueprintBuilder {
    public BlueprintBuilder bounds(Cuboid bounds);
    public BlueprintBuilder check(Check<?> check);
    public BlueprintBuilder check(java.util.List<? extends Check<?>> checks);
    public BlueprintBuilder checks(java.util.Collection<? extends Check<?>> checks);
    public BlueprintBuilder computation(Computation<?> computation);
    public BlueprintBuilder computations(java.util.Collection<? extends Computation<?>> computations);
    public BlueprintBuilder seam(Seam seam);
    public BlueprintBuilder seams(java.util.Collection<Seam> seams);
    public BlueprintBuilder anchor(AnchorSpec anchor);
    public BlueprintBuilder anchors(java.util.Collection<AnchorSpec> anchors);
    public BlueprintBuilder orientation(OrientationPolicy orientation);   // required iff anchors exist
    public BlueprintBuilder derive(DerivationSpec derivation);
    public BlueprintBuilder derivations(java.util.Collection<DerivationSpec> derivations);
    public BlueprintBuilder arguments(Value arguments);
    public BlueprintBuilder source(SourceSpan source);
    public Blueprint build();
}
```

## 4. Laws

### 4.1 Bay

`Bay.cells` is a `CuboidSet`, not one cuboid. This is the one shape change from the old design: a
shell, a two-leaf door, an L-shaped room and a pipe corner are all sets of boxes. It preserves I-03
(no voxel set) and removes the synthetic-bay lowering the document used to need.

A bay is a declaration, not a check: `Coverage` says what a match must establish, `Footprint` says
what the instance owns, `Sharing` says how claims meet. They are three independent axes.

| Coverage | Build obligation | Settle obligation |
| --- | --- | --- |
| `EVERY_CELL` | union of the bay's checks' `focus.staticCells(bay.cells())` contains `bay.cells()` | — |
| `ONE_SUBJECT` | at least one check binds subjects; static cells stay inside the bay | union of bound subject footprints equals `bay.cells()` |
| `ANY_SUBJECT` | every check's static cells is empty | — |

`EXCLUSIVE` requires a footprint other than `None` (R1). `ONE_SUBJECT + Box` is legal (the bay owns
its air); `ANY_SUBJECT + Bound` is legal (a pen owns the animal's cells). Only `None + EXCLUSIVE` is a
contradiction.

### 4.2 Check

A `Check<T>` carries its `Bay` value. There is no separate bay id to miss, no `BayBuilder` knot and
no `Requirement` list. `Check.of` rejects `focus.subject() != expectation.subject()` with
`SC-CHK-0001`. `Check.run` is the only place a focus result meets an expectation:

```
FocusResult.Taken     → expectation.test(withSubject)
FocusResult.Unavailable → Reply.Unreadable(findings)
```

A missing subject never reaches `test`, and a focus that returns `Unavailable` with no finding is a
producer bug (the `Unavailable` constructor rejects it).

### 4.3 Reply fold

The four states are total and distinct. `Mismatch` has at least one finding; `Unreadable` has at
least one; `Match` and `Abstain` carry none. A `Mismatch` is a refutation, an `Unreadable` is "not
looked at", an `Abstain` is a refusal.

Combinators evaluate every child (diagnostics matter more than a short-circuit) and fold:

- `all_of`: any `Mismatch` → `Mismatch` (merged findings); else any `Unreadable` → `Unreadable`;
  else any `Abstain` → `Abstain`; else `Match`.
- `any_of`: any `Match` → `Match`; else any `Unreadable` → `Unreadable`; else any `Mismatch` →
  `Mismatch`; else `Abstain`.
- `not`: `Match` ↔ `Mismatch(SC-EXP-0002)`; `Unreadable` and `Abstain` unchanged.
- `sequence`: same verdict as `all_of`; findings in child order.
- Thresholds count `MATCH`/`MISMATCH`/`UNREADABLE`/`ABSTAIN`:
  `atLeast(k)` matches iff `m ≥ k`, mismatches iff `m+u+a < k`, else unreadable if `u > 0`, else
  abstains; `atMost(k)` matches iff `m+u+a ≤ k`, mismatches iff `m > k`, else unreadable if `u > 0`,
  else abstains; `exactly(k)` matches iff `m == k && u+a == 0`, mismatches iff `m > k || m+u+a < k`,
  else unreadable if `u > 0`, else abstains. A failing threshold adds `SC-EXP-0003`.
- `eachCell(element)` is `all_of` over every cell of the bay, each element seeing a conjoined context
  with that cell's occupant via `withSubject`.
- `custom` is a typed wrapper; it carries the supplied meta and reads.
- Every combinator requires at least one child; an empty `all_of`/`any_of`/`sequence`/threshold is an
  `IllegalArgumentException` at construction (the document twin is `SC-DOC-0002`).

### 4.4 Focus

A focus binds a subject or says why not. `staticCells` is the build-time decision set; it is empty
for dynamic focuses (`context`, `entity`) and non-empty for static ones (`occupant`, `bay`,
`allCells`, `door`, `nested`). `bindsSubjects` is false only for `context`. `reads` defaults to the
static cells; an entity focus adds a window; an expectation that reads outside its cells declares it
through `Expectation.reads()`.

`Focuses.occupant(cell)` is air-aware: an air cell is `Taken(air, List.of(), notes)`, never
`Unavailable`. `Focuses.bay`/`allCells` include air; subjects exclude air. `Focuses.door(origin)`
reads `origin` and `origin + (0,1,0)` and requires both to be non-air; otherwise `Unavailable`.
`Focuses.entity(type)` requires exactly one entity of that type in the bay; zero or many is
`Unavailable` (`SC-OCC-0001` for zero, an ambiguity finding for many). `Focuses.nested(cell)` requires
a nested occupant and returns its child view. `Focuses.context(key)` is `context().require(key)`;
absent is `Unavailable` with `SC-CTX-0007` on a `KEY` locus.

### 4.5 Read scope

`Check.reads()` is `focus.reads(bay.cells()) ∪ expectation.reads()` plus
`expectation.meta().requires()`. The engine charges `cells.volume()` and the sections the cells
touch; a window charges its inflated volume and sections. Declared reads are a promise: a check that
reads a cell or key outside its scope is a bug (review), not a runtime error. A non-`None` bay's
cells must stay inside `bay.cells ∪ footprint`; a `Bound` footprint is checked at settle, a
`Box`/`Declared` at build.

### 4.6 Expectation context

`FocusContext` is what a focus sees. `ExpectationContext` adds the subject and `withSubject`, which
re-types the subject for `each_cell`. Both expose the same bay/view/siting/phase/locus/context/budget
and the same `occupantAt`/`claimsAt`; there is no second session object.

`ExpectationMeta.requires` is a coverage demand: the engine checks the keys before calling `test`; if
any is missing, the check is `Unreadable(SC-CTX-0007)` and `test` is not called. An expectation that
uses `context().get(key)` for a key it did not declare is a review defect.

### 4.7 Blueprint

`Blueprint` is a final class with a private constructor. The builder is the only construction path.
It is immutable: private final fields, copied collections, a cached `Plan` and cached `bayCells()`
(excluded from equality). `equals` is structural over the declarations (id, bounds, bays, checks,
computations, seams, anchors, orientation, derivations, arguments); it excludes `source`, `plan` and
`bayCells()`.

`plan()` returns the cached `Plan`: sections in declaration order, aggregates in a topological order
of `inputs()`, checks in declaration order. A computation input must be produced by another
computation in the same blueprint; a derivation output is not available at check time and a check
that reads one is a build error. A cycle or a missing producer is `SC-BPT-0007`.

### 4.8 Build validation

`build()` throws `IllegalArgumentException` carrying the code and the offending id for:

| Rule | Code |
| --- | --- |
| id/bounds non-null, bounds non-empty, at least one check | `SC-BPT-0001` |
| two bays intersect and at least one is `EXCLUSIVE` | `SC-BPT-0002` |
| a bay is empty or leaves `bounds` | `SC-BPT-0003` |
| duplicate bay/check/seam id, or two different bays with one id | `SC-BPT-0004` |
| coverage/footprint R1–R4, `None + EXCLUSIVE`, `Declared` outside bounds | `SC-BPT-0005` |
| anchor roles duplicated, primary role absent, allowed empty/duplicate/not upright, anchors without a policy | `SC-BPT-0006` |
| computation DAG cycle/missing input/duplicate output | `SC-BPT-0007` |
| derivation order negative/duplicate, output duplicate/also a computation output | `SC-BPT-0008` |
| seam outside every bay | `SC-BPT-0009` |
| a focus's static cells leave its bay, a check reads a derivation output, or a required key is produced by no computation | `SC-CHK-0002/0003` |

The document compiler runs the same table and reports findings with loci before calling `build()`;
a `build()` throw after a clean compile is a kernel defect.

### 4.9 Anchors and orientation

The anchor cell is the definition origin, supplied by the caller. `OrientationPolicy.select` scans
`allowed` in `Orientation.all()` order and returns the first `o` with
`o.apply(Heading.NORTH) == anchorHeading`; `null` means no admissible pose. `allowed` is non-empty,
duplicate-free and in canonical order; `uprightOnly` is a build-time promise (`o.apply(UP) == UP`),
not a match-time filter. A blueprint with no anchor has a null orientation policy and takes a
`Siting` directly; `Anchors.resolve` is only for anchored blueprints.

`Anchors.resolve` reads exactly one cell (`origin`): a missing cell is `SC-OCC-0001`; an air cell, a
missing property, a value that is not a heading, no admissible orientation, or a null policy is
`SC-BPT-0006`. There is no fallback heading and no search.

### 4.10 Computations

`Computation` has no phase field. The interface split is the phase: a `SectionComputation` receives a
`SectionContext`, which has no `read(ContextKey)`; an `AggregateComputation` receives an
`AggregateContext`, which has no `contribute`. This is a compile-time guarantee, not a convention.

A section computation runs once per section of `region ∩ captured sections` and returns one partial
value; a `Missing` makes the whole key absent for that attempt. An aggregate computation runs once
after the fold and returns one value; a `Missing` makes the key absent. A computation may call
`report` for its own findings; the engine adds `SC-CTX-0001` when a computation returns `Missing`
without reporting.

`Computations.count` counts non-air occupants in the slice; `volume` counts cells; `air` counts air
cells. All three are section computations; folding is the key's `Merge`.

## 5. Pipeline role

- `compile`: the builder validates; the document compiler builds the same value.
- `check`: the engine walks `plan()`; `Check.run` is the only focus/expectation meeting point.
- `settle`: bound footprints from `CheckResult.subjects` become claims.
- `derive`: `DerivationSpec` is only a reference; no structure type is resolved there.

## 6. Codes

`SC-BPT-0001`…`0010`, `SC-CHK-0001`…`0003`, `SC-EXP-0001`…`0005`, `SC-SPC-0001`/`0002` (via
`LocusMap`/`Siting`), `SC-CTX-0007` (focus), `SC-OCC-0001`/`0002`/`0003` (focus). The producer
table in `finding.md` is authoritative.

## 7. Tests

- Bay: `CuboidSet` shell, door pair, pipe box; empty cells rejected; `None + EXCLUSIVE` rejected.
- Coverage: `EVERY_CELL` gap, `ANY_SUBJECT` with a static focus, `ONE_SUBJECT` with a context focus.
- Focus: air-aware occupant; door missing half; entity zero/many; nested; context absent.
- Reply combinators: all four states; thresholds; `not`; `each_cell`; findings merged.
- Build: every row of §4.8; duplicate ids; overlapping exclusive bays; shared overlap allowed;
  derivation/check read conflict; `Plan` order and cycle.
- Anchors: one-cell read; missing/air/bad heading; upright policy; identity for no anchor.
- Computations: section/aggregate split; count/volume/air; `Missing` → absent key.

## 8. Dependencies

`core`, `space`, `finding`, `world`, `claim`. It does not import `context` (computations receive
contexts declared here), `engine`, `document` or `spi`.
