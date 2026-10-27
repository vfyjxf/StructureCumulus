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
`Computations`, `FocusFactory`, `ExpectationFactory`, `ComputationFactory`, `FactoryContext`,
`SitingResult`.

**Does not own:** `Sharing`/`Footprint` (`claim`), the occupant model (`world`), context values or
their store (`context`), the engine loop (`engine`), the document IR (`document`).

**Imports:** `core`, `space`, `finding`, `world`, `claim`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `Bay` | record | id + `CuboidSet` cells + coverage + footprint + sharing |
| `Coverage` | enum | `everyCell`, `oneSubject`, `anySubject` |
| `Check<T>` | record | id + bay + focus + expectation + description |
| `CheckResult` | record | reply + subjects + non-fatal notes |
| `Focus<T>` | interface | binds a subject or explains why not |
| `FocusResult<T>` | sealed | `Taken(value, subjects, notes)`, `Unavailable(findings)` |
| `FocusContext` | interface | what a focus sees: bay, view, siting, cells, phase, locus, context, budget, a total `read` |
| `Expectation<T>` | interface | one test over one subject type |
| `ExpectationContext<T>` | interface | focus context + the subject and its re-typing |
| `ExpectationMeta` | record | id, specificity, required keys, default mismatch code |
| `Reply` | sealed | `Match`, `Mismatch`, `Unreadable`, `Abstain` |
| `ReadScope` | record | cells + keys + trait ids + occupant kinds + depth + entity windows |
| `BayView` | record | a bay's own read face; total `read` |
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
| `SectionContext` / `AggregateContext` | interfaces | what a computation sees; `SectionContext` has a total `read`, `unreadable()` and `phase()` |
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

public enum Coverage { everyCell, oneSubject, anySubject }

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
    CellRead read(LatticePoint cell);
    default @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    default @Nullable ReadRefusal refusalAt(LatticePoint cell);
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
                        java.util.Set<NamespacedId> traits, java.util.Set<OccupantKind> kinds,
                        int depth, java.util.List<EntityWindow> windows) {
    public ReadScope { /* depth >= 0; canonical sets; windows deduplicated in declaration order */ }
    public static ReadScope none();
    public static ReadScope cells(CuboidSet cells);
    public ReadScope union(ReadScope other);
    public ReadScope withKeys(java.util.Set<ContextKey<?>> keys);
    public ReadScope withTraits(java.util.Set<NamespacedId> traits);
    public ReadScope withKinds(java.util.Set<OccupantKind> kinds);
    public ReadScope withDepth(int depth);
    public ReadScope withWindows(java.util.List<EntityWindow> windows);
}

public record BayView(NamespacedId id, CuboidSet cells, WorldView view, Siting siting) {
    public CellRead read(LatticePoint cell);
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
    public static Expectation<OccupantSnapshot> traitAbsent(TraitKey<?> key);
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

public enum SeamKind { item, fluid, energy, structural, custom }

public record Seam(SeamId id, Cuboid cells, SeamKind kind,
                   java.util.Map<String, String> properties) {
    public static final String customKind = "kind";
    public boolean compatibleWith(Seam other);
}

public record AnchorSpec(NamespacedId role, String property, String value) { }

public final class AnchorRoles {
    public static final NamespacedId primary = NamespacedId.of("primary");
    public static final NamespacedId front = NamespacedId.of("front");
    public static final NamespacedId center = NamespacedId.of("center");
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

public record SitingResult(Outcome<Siting> outcome, FindingBundle findings) {
    public boolean succeeded();
}

public final class Anchors {
    public static SitingResult resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
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
    Phase phase();
    CellRead read(LatticePoint cell);
    default @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    default @Nullable ReadRefusal refusalAt(LatticePoint cell);
    java.util.Map<LatticePoint, ReadRefusal> unreadable();
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
    public ReadScope reads();
    public @Nullable Bay bay(NamespacedId id);
    public @Nullable Check<?> check(NamespacedId id);
    public @Nullable Seam seam(SeamId id);
    public @Nullable AnchorSpec anchor(NamespacedId role);
    public CuboidSet bayCells();
    public static BlueprintBuilder builder(NamespacedId id);
    @Override public boolean equals(Object other);
    @Override public int hashCode();
    Blueprint() {}
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
| `everyCell` | union of the bay's checks' `focus.staticCells(bay.cells())` contains `bay.cells()` | — |
| `oneSubject` | at least one check binds subjects; static cells stay inside the bay | union of bound subject footprints equals `bay.cells()` |
| `anySubject` | every check's static cells is empty | — |

`exclusive` requires a footprint other than `None` (R1). `oneSubject + Box` is legal (the bay owns
its air); `anySubject + Bound` is legal (a pen owns the animal's cells). Only `None + exclusive` is a
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
`allCells`, `door`, `nested`). `bindsSubjects` is false for `context` and `nested`: a nested take
yields the child view as its subject but never a claim subject. `reads` defaults to the
static cells; an entity focus adds a window and the `entity` kind; a nested focus adds the `nested`
kind and one level of depth; a trait expectation adds its trait id. An expectation that reads outside
its cells declares it through `Expectation.reads()`.

`Focuses.occupant(cell)` is air-aware: an air cell is `Taken(air, List.of(), notes)`, never
`Unavailable`. An unreadable cell is `Unavailable`: the finding relays `refusalAt(cell).code()`
through the trust guard (`world` §4.1) and uses the focus's own locus. `Focuses.bay`/`allCells`
include air; subjects exclude air. `allCells` stops at the first `Unreadable` and reports it; a
`bay` query returns only readable occupants, so a caller that needs totality reads `BayView.read`
per cell. Neither treats a refused cell as empty. `Focuses.door(origin)` reads `origin` and
`origin + (0,1,0)` and requires both to be readable and non-air; otherwise `Unavailable`. Its
`staticCells` are exactly those two cells, so a coverage rule is decided over them: a third cell in
an `everyCell` bay leaves the bay undecided (`SC-BPT-0005`).
`Focuses.entity(type)` requires exactly one entity of that type in the bay; zero or many is
`Unavailable` (`SC-OCC-0001` for zero, an ambiguity finding for many); it reads the bay cells, the
entity window and the `entity` kind. `Focuses.context(key)` is `context().require(key)`; absent is
`Unavailable` with `SC-CTX-0007` on a `key` locus.

`Focuses.nested(cell)` is anchor-only: it asks `ctx.view().nested(world)` directly and does not
require `occupant(cell)` to narrow to the nested body, so an entity or block over the anchor does not
hide the child. A `null` child is a cut: `Unavailable(SC-OCC-0003)` with a local/world label. A
successful take is `Taken(child, List.of(), notes)`: the child view is the subject, but the focus
produces **no claim subject** and `bindsSubjects` is false, so `Coverage.oneSubject` with a nested
focus is a build error (`SC-BPT-0005`). `Coverage.anySubject + Footprint.bound` still builds a nested
body and rejects it at check (`SC-CLM-0004`) — a known gap characterized in `06-verification.md`
§3.2. A nested body is still an ordinary occupant of the parent frame; a
check that binds it through `Focuses.occupant` fails at claim prediction with `SC-CLM-0004` before an
`Admitted` exists, and `settle` revalidates the same rule (`engine` §7.5).

### 4.5 Read scope

`ReadScope` is the one read closure: `(cells, keys, traits, kinds, depth, windows)`. `cells` is the
declared cell set, `keys` the context keys a check or computation reads, `traits` the trait ids, and
`kinds` the occupant kinds the host must resolve. `depth` is the nested-frame ceiling the reads need
(`0` for a blueprint that never nests). `union` combines cells/keys/traits/kinds by set union, takes
the larger depth and appends the windows in declaration order with equal windows deduplicated;
`withKeys`/`withTraits`/`withKinds`/`withWindows` union their argument and `withDepth` keeps the
larger value, so a narrowed copy can never lower a requirement. `none()` is empty with depth `0`.

`Check.reads()` is `focus.reads(bay.cells()) ∪ expectation.reads()` plus
`expectation.meta().requires()`. `Blueprint.reads()` is the union of every check's `reads()` and every
computation's `ReadScope.cells(region)`: computations contribute cells only, because a `Computation`
declares no trait or kind. A computation that reads a trait must be accompanied by a check that
declares that trait id, or the host will not capture it. The engine charges `cells.volume()` and the
sections the cells touch; a window charges its inflated volume and sections. Declared reads are a
promise: a check that reads a cell or key outside its scope is a bug (review), not a runtime error. A
non-`None` bay's cells must stay inside `bay.cells ∪ footprint`; a `Bound` footprint is checked at
settle, a `Box`/`Declared` at build.

The host derives `SnapshotPolicy` from `Blueprint.reads()`: `kinds`, `traits` and `depth` map
directly, and `windows` is `reads.windows()`. `maxElements` and the capture bounds remain the host's
choice. No hidden read is legal; a policy without the declared trait ids produces `TraitSlot.Refused`
(or no slot), never a default value. A generic `occupant`/`bay` read declares no kind: it sees
whatever the host captured. A blueprint that requires entity-over-block or a nested body therefore
uses an `entity`/`nested` focus (or a custom focus that declares the kind), because `reads.kinds` is
the only capture signal; an `occupant` focus over an entity the host never captured sees the block
under it.

### 4.6 Expectation context

`FocusContext` is what a focus sees. `ExpectationContext` adds the subject and `withSubject`, which
re-types the subject for `each_cell`. Both expose the same bay/view/siting/phase/locus/context/budget
and the same `read`/`occupantAt`/`refusalAt`/`claimsAt`; there is no second session object. `read` maps
the local cell through the siting before asking the view; an unmapped local cell is
`Unreadable(outOfBounds, SC-OCC-0004)`, never air and never a silent skip. `BayView.read` is the same
local-to-world total read; it does not clip to the bay, and a siting miss is the same refusal.

The trait four states are normative (`world` §4.7): `Present` and `Present(null)` are positive facts,
`Absent` is a positive fact of no trait, and `Refused` or an unasked slot is unreadable.
`trait(key)` matches a present trait including present-null; `trait(key, test)` matches a present
non-null value whose test passes, and a present-null value matches without applying the test;
`traitAbsent(key)` matches an `Absent` slot and mismatches a present one. A `Refused` slot or no slot
is `Unreadable`, never `Mismatch`, and `not(trait(...))` preserves that unreadable answer.
`trait(key, test)` on a value outside the key's type is unreadable, not a mismatch: the typed lookup
could not answer.

`ExpectationMeta.requires` is a coverage demand: the engine checks the keys before calling `test`; if
any is missing, the check is `Unreadable(SC-CTX-0007)` and `test` is not called. An expectation that
uses `context().get(key)` for a key it did not declare is a review defect.

### 4.7 Blueprint

`Blueprint` is a final class with a package-private constructor. The builder is the only public
construction path.
It is immutable: private final fields, copied collections, a cached `Plan`, cached `bayCells()` and
cached `reads()` (all excluded from equality). `equals` is structural over the declarations (id,
bounds, bays, checks, computations, seams, anchors, orientation, derivations, arguments); it excludes
`source`, `plan`, `bayCells()` and `reads()`.

`plan()` returns the cached `Plan`: sections in declaration order, aggregates in a topological order
of `inputs()`, checks in declaration order. An **aggregate** input must be produced by another
computation in the same blueprint; a section computation has no inputs (its `SectionContext` exposes
the snapshot, not context keys); a derivation output is not available at check time and a check that
reads one is a build error. A cycle, a missing producer, a section input, or a duplicate output key
is `SC-BPT-0007`. `reads()` is computed once from the same immutable declarations; it is a
consequence of the blueprint, not a second declaration.

### 4.8 Build validation

`build()` throws `IllegalArgumentException` carrying the code and the offending id for:

| Rule | Code |
| --- | --- |
| id/bounds non-null, bounds non-empty, at least one check | `SC-BPT-0001` |
| two bays intersect and at least one is `exclusive` | `SC-BPT-0002` |
| a bay is empty or leaves `bounds` | `SC-BPT-0003` |
| duplicate bay/check/seam id, or two different bays with one id | `SC-BPT-0004` |
| coverage/footprint R1–R4, `None + exclusive`, `Declared` outside bounds | `SC-BPT-0005` |
| anchor roles duplicated, primary role absent, allowed empty/duplicate/not upright, anchors without a policy | `SC-BPT-0006` |
| computation DAG cycle/missing input/duplicate output/empty region/section input | `SC-BPT-0007` |
| derivation order negative/duplicate, output duplicate/also a computation output | `SC-BPT-0008` |
| seam outside every bay | `SC-BPT-0009` |
| a focus's static cells leave its bay, a check reads a derivation output, or a required key is produced by no computation | `SC-CHK-0002/0003` |

The document compiler runs the same table and reports findings with loci before calling `build()`;
a `build()` throw after a clean compile is a kernel defect.

### 4.9 Anchors and orientation

The anchor cell is the definition origin, supplied by the caller. `OrientationPolicy.select` scans
`allowed` in `Orientation.all()` order and returns the first `o` with
`o.apply(Heading.north) == anchorHeading`; `null` means no admissible pose. `allowed` is non-empty,
duplicate-free and in canonical order; `uprightOnly` is a build-time promise (`o.apply(up) == up`),
not a match-time filter. A blueprint with no anchor has a null orientation policy and takes a
`Siting` directly; `Anchors.resolve` is only for anchored blueprints.

`Anchors.resolve` reads exactly one cell (`origin`): an unreadable cell is reported with the
refusal's code (`SC-OCC-0004` as the kernel default, or a relayed host code); an air cell, a
missing property, a value that is not a heading, no admissible orientation, or a null policy is
`SC-BPT-0006`. There is no fallback heading and no search.

### 4.10 Computations

`Computation` has no phase field. The interface split is the phase: a `SectionComputation` receives a
`SectionContext`, which has no `read(ContextKey)`; an `AggregateComputation` receives an
`AggregateContext`, which has no `contribute`. This is a compile-time guarantee, not a convention.

A section computation runs once per section of `region ∩ captured sections` and returns one partial
value; it reads only `SectionContext` (the snapshot slice) and has no `inputs()`. An aggregate
computation runs once after the fold and returns one value; it reads the folded values through
`inputs()`. A `Missing` makes the key absent for that attempt. A computation may call
`report` for its own findings; the engine adds `SC-CTX-0001` when an aggregate computation returns `Missing`
without reporting; a missing section contribution makes the key absent and records `SC-CTX-0006`.

`SectionContext.read` is total, maps through the siting like `FocusContext.read`, and `unreadable()`
lists the slice's refused local cells with their refusals, in cell order. A section that is wholly
`Refused` is never handed to `compute`; its key is absent and the engine records `SC-CTX-0006`. A
`Partial` section is handed over with its refused cells visible. `SectionContext.occupants()` is
readable-only: a computation that uses its size for a total must consult `unreadable()` first.
`Computations.count` counts non-air occupants whose anchor is in the slice; `volume` counts cells;
`air` counts air cells. `count`/`air` require a total slice: when `unreadable()` is non-empty they
report each refusal (relaying the refusal's code through the trust guard) and return `Missing`, never
a short sum. `volume` is geometry and stays total. A custom computation that sums over its slice and
ignores `unreadable()` is a review defect, not a different verdict. An occupant spanning a section
boundary belongs to the section of its anchor, so a fold never counts it twice. All three are section
computations; folding is the key's `Merge`.

## 5. Pipeline role

- `compile`: the builder validates; the document compiler builds the same value.
- `check`: the engine walks `plan()`; `Check.run` is the only focus/expectation meeting point.
- `settle`: bound footprints from `CheckResult.subjects` become claims.
- `derive`: `DerivationSpec` is only a reference; no structure type is resolved there.

## 6. Codes

`SC-BPT-0001`…`0010`, `SC-CHK-0001`…`0003`, `SC-EXP-0001`…`0005`, `SC-SPC-0001`/`0002` (via
`LocusMap`/`Siting`), `SC-CTX-0007` (focus), `SC-OCC-0001`/`0002`/`0003`/`0004` (focus and relay).
The producer table in `finding.md` is authoritative. A focus relay of a host `ReadRefusal` code is
not a kernel producer row: the kernel only guarantees the `SC-OCC-0004` substitute for a code already
in `Codes.all`.

## 7. Tests

- Bay: `CuboidSet` shell, door pair, pipe box; empty cells rejected; `None + exclusive` rejected.
- Coverage: `everyCell` gap, `anySubject` with a static focus, `oneSubject` with a context focus.
- Focus: air-aware occupant; unreadable occupant relays its refusal; door missing half; entity
  zero/many; nested anchor-only, no claim subject and a cut; context absent.
- Reply combinators: all four states; thresholds; `not`; `each_cell`; findings merged; `not` leaves a
  trait refusal unreadable.
- Traits: present/present-null/absent/refused; `traitAbsent`; a missing slot is unreadable, never a
  mismatch; a JSON `TraitKey<Object>` reads a typed capture.
- Build: every row of §4.8; duplicate ids; overlapping exclusive bays; shared overlap allowed;
  derivation/check read conflict; `Plan` order and cycle; `Blueprint.reads()` equals the union of
  checks and computation regions.
- Anchors: one-cell read; missing/air/bad heading; upright policy; identity for no anchor.
- Computations: section/aggregate split; count/volume/air; `Missing` → absent key; a partial section
  makes `count`/`air` Missing, never a short sum; `volume` stays total.

## 8. Dependencies

`core`, `space`, `finding`, `world`, `claim`. It does not import `context` (computations receive
contexts declared here), `engine`, `document` or `spi`.
