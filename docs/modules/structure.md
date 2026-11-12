# Module `kernel.structure`

> Rank 4. The author's model: a bay, a check, a focus, an expectation, and the immutable blueprint
> that ties them together. This is the largest module because it is where the library's vocabulary
> becomes type-safe; it still owns no world, no claim row and no context store.

## 1. Scope

**Owns:** `Bay`, `Coverage`, `Check`, `CheckResult`, `Focus`, `FocusResult`, `FocusContext`,
`Focuses`, `Expectation`, `ExpectationContext`, `ExpectationMeta`, `Expectations`, `Reply`,
`ReadScope`, `BayView`, `DoorView`, `Seam`, `SeamId`, `SeamKind`, `AnchorSpec`, `AnchorRoles`,
`OrientationPolicy`, `DerivationSpec`, `Blueprint`, `BlueprintBuilder`, `Plan`,
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
| `ExpectationDeclaration` | sealed | a declaration as a value (D-91): leaf payloads, composite children, undescribable for custom |
| `Reply` | sealed | `Match`, `Mismatch`, `Unreadable`, `Abstain` |
| `ReadScope` | record | cells + keys + trait ids + occupant kinds + entity windows |
| `BayView` | record | a bay's own read face; total `read` |
| `DoorView` | record | the two halves of a door + its bay |
| `Focuses` | final class | the shipped focus kinds |
| `Expectations` | final class | the shipped combinators, leaves, the host bridge and the typed context test |
| `Seam` / `SeamId` / `SeamKind` | record / record / enum | a declared connectable edge |
| `AnchorSpec` / `AnchorRoles` | record / final class | the anchor's heading in the blueprint's own coordinates |
| `OrientationPolicy` | record | allowed orientations + primary role + upright promise |
| `DerivationSpec` | record | deriver id + args + output key id + order |
| `Blueprint` | final class | immutable compiled template with a cached plan |
| `BlueprintBuilder` | final class | fluent construction; validates at `build()`; `.envelope()` derives the closed-world air pair |
| `Plan` | record | section units, aggregate units, checks |
| `Computation<T>` | interface | id + output + inputs + region |
| `SectionComputation<T>` | interface | `compute(SectionContext)` |
| `AggregateComputation<T>` | interface | `compute(AggregateContext)` |
| `SectionContext` / `AggregateContext` | interfaces | what a computation sees; both have a total `read` and `unreadable()`, `SectionContext` also has `phase()` |
| `Computations` | final class | `count` (with an optional predicate), `volume`, `air` |
| `FocusFactory` / `ExpectationFactory` / `ComputationFactory` | interfaces | data-driven constructors |
| `FactoryContext` | record | document id, source, finding reporter |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.structure;

public record Bay(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                  Sharing sharing, @Nullable String description, @Nullable String hint) {
    public Bay { /* id non-null; cells non-empty; footprint non-null; sharing non-null */ }
    public static Bay of(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                         Sharing sharing);
    public static Bay of(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                         Sharing sharing);
    public static Bay of(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                         Sharing sharing, String description);
    public static Bay of(NamespacedId id, CuboidSet cells, Coverage coverage, Footprint footprint,
                         Sharing sharing, @Nullable String description, @Nullable String hint);
}

public enum Coverage { everyCell, oneSubject, anySubject }

public record Check<T>(NamespacedId id, Bay bay, Focus<T> focus, Expectation<T> expectation,
                       @Nullable String description) {
    public Check { /* id non-null; bay non-null; focus.subject() == expectation.subject() */ }
    public static <T> Check<T> of(NamespacedId id, Bay bay, Focus<T> focus,
                                  Expectation<T> expectation);
    public static <T> Check<T> of(NamespacedId id, Bay bay, Focus<T> focus,
                                  Expectation<T> expectation, @Nullable String description);
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
    default boolean bindsClaimableSubject();
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
    default @Nullable OccupantSnapshot cellOccupant(LatticePoint cell);   // maps local to world
    default @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    default @Nullable ReadRefusal refusalAt(LatticePoint cell);
    java.util.List<ClaimRow> claimsAt(LatticePoint cell);
}

public interface Expectation<T> {
    Reply test(ExpectationContext<T> ctx);
    Class<T> subject();
    default ExpectationMeta meta();
    default ReadScope reads();
    default ExpectationDeclaration declaration();
}

public sealed interface ExpectationDeclaration {
    record BlockLeaf(NamespacedId type) implements ExpectationDeclaration { }
    record TraitLeaf(TraitKey<?> key, boolean present) implements ExpectationDeclaration { }
    record HostLeaf(NamespacedId type, FindingCode mismatchCode, FindingCode absentCode)
            implements ExpectationDeclaration { }
    enum MarkerLeaf implements ExpectationDeclaration { air, anyOccupant, empty, nonFluid, envelopeAir }
    record Composite(CompositeKind kind, java.util.List<ExpectationDeclaration> children)
            implements ExpectationDeclaration { }
    enum CompositeKind { allOf, anyOf, not, sequence, eachCell }
    record Threshold(ThresholdKind kind, int count, java.util.List<ExpectationDeclaration> children)
            implements ExpectationDeclaration { }
    enum ThresholdKind { atLeast, atMost, exactly }
    record Undescribable() implements ExpectationDeclaration { }
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
                        java.util.List<EntityWindow> windows) {
    public ReadScope { /* canonical sets; windows deduplicated in declaration order */ }
    public static ReadScope none();
    public static ReadScope cells(CuboidSet cells);
    public ReadScope union(ReadScope other);
    public ReadScope withKeys(java.util.Set<ContextKey<?>> keys);
    public ReadScope withTraits(java.util.Set<NamespacedId> traits);
    public ReadScope withKinds(java.util.Set<OccupantKind> kinds);
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
    public static Expectation<OccupantSnapshot> envelopeAir();
    public static <H> Expectation<OccupantSnapshot> host(
        HostType<H> type, java.util.function.BiPredicate<H, LatticePoint> test,
        FindingCode mismatchCode, FindingCode absentCode, String description);
    public static Expectation<OccupantSnapshot> trait(TraitKey<?> key);
    public static <T> Expectation<OccupantSnapshot> trait(TraitKey<T> key,
                                                          java.util.function.Predicate<T> test);
    public static Expectation<OccupantSnapshot> traitAbsent(TraitKey<?> key);
    public static Expectation<OccupantSnapshot> anyOccupant();
    public static Expectation<OccupantSnapshot> empty();
    public static Expectation<OccupantSnapshot> nonFluid();
    public static <T> Expectation<T> context(ContextKey<T> key,
                                             java.util.function.Predicate<T> test);
    public static <T> Expectation<T> allOf(Expectation<T>... children);
    public static <T> Expectation<T> anyOf(Expectation<T>... children);
    public static <T> Expectation<T> sequence(Expectation<T>... children);
    public static <T> Expectation<T> not(Expectation<T> child);
    public static <T> Expectation<T> atLeast(int count, Expectation<T>... children);
    public static <T> Expectation<T> atMost(int count, Expectation<T>... children);
    public static <T> Expectation<T> exactly(int count, Expectation<T>... children);
    public static Expectation<BayView> eachCell(Expectation<OccupantSnapshot> element);
    public static <T> Expectation<T> custom(Class<T> subject, ExpectationMeta meta, ReadScope reads,
                                            java.util.function.Function<ExpectationContext<T>, Reply> body);
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
    public @Nullable Orientation select(Heading declared, Heading observed);

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
    CuboidSet cells();
    CellRead read(LatticePoint cell);
    default @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
    default @Nullable ReadRefusal refusalAt(LatticePoint cell);
    java.util.Map<LatticePoint, ReadRefusal> unreadable();
    java.util.List<OccupantSnapshot> occupants(Cuboid box);
    Budget remaining();
    CancelToken cancel();
    void report(Finding finding);
}

public final class Computations {
    public static SectionComputation<Long> count(ContextKey<Long> output, CuboidSet region);
    public static SectionComputation<Long> count(ContextKey<Long> output, CuboidSet region,
                                                 java.util.function.Predicate<OccupantSnapshot> predicate);
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
    public @Nullable String description();
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
    public BlueprintBuilder envelope();
    public BlueprintBuilder envelopeEntities();
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
    public BlueprintBuilder description(@Nullable String description);
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
`description` and `hint` are carried text: the compiler copies them, no rule reads them, and they
participate in the bay's equality (`design/16` §3, D-94).

| Coverage | Build obligation | Settle obligation |
| --- | --- | --- |
| `everyCell` | union of the bay's checks' `focus.staticCells(bay.cells())` contains `bay.cells()` | — |
| `oneSubject` | at least one check binds subjects; static cells stay inside the bay | union of bound subject footprints equals `bay.cells()` |
| `anySubject` | every check's static cells is empty; a claiming footprint also requires every subject-binding focus to be claimable | — |

`exclusive` must own cells at build when the owned set is already known: `None` and a
`Declared` with no cells are contradictions (`SC-BPT-0005`). A `Bound` bay is not judged here —
its owned set exists only at settle. `oneSubject + Box` is legal (the bay owns its air);
`anySubject + Bound` is legal (a pen owns the animal's cells) only when every focus that binds a
subject answers `bindsClaimableSubject() == true`. A claiming bay with an unknown custom focus
fails the build (`SC-BPT-0005`).

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
looked at", an `Abstain` is a refusal. An `Unreadable` result carries only the findings of its
`Unreadable` children; a `Mismatch` result carries the findings of all its `Mismatch` and
`Unreadable` children.

Combinators evaluate every child (diagnostics matter more than a short-circuit) and fold:

- `all_of`: any `Mismatch` → `Mismatch` (merged findings); else any `Unreadable` → `Unreadable`;
  else any `Abstain` → `Abstain`; else `Match`.
- `any_of`: any `Match` → `Match`; else any `Unreadable` → `Unreadable`; else any `Mismatch` →
  `Mismatch`; else `Abstain`.
- `not`: `Match` ↔ `Mismatch(SC-EXP-0002)`; `Unreadable` and `Abstain` unchanged.
- `sequence`: same verdict and findings as `all_of`; the bundle orders them.
- Thresholds count `MATCH`/`MISMATCH`/`UNREADABLE`/`ABSTAIN`:
  `atLeast(k)` matches iff `m ≥ k`, mismatches iff `m+u+a < k`, else unreadable if `u > 0`, else
  abstains; `atMost(k)` matches iff `m+u+a ≤ k`, mismatches iff `m > k`, else unreadable if `u > 0`,
  else abstains; `exactly(k)` matches iff `m == k && u+a == 0`, mismatches iff `m > k || m+u+a < k`,
  else unreadable if `u > 0`, else abstains. A failing threshold adds `SC-EXP-0003`.
- `eachCell(element)` is `all_of` over every cell of the bay, each element seeing a conjoined context
  with that cell's occupant via `withSubject`.
- `custom` is a typed wrapper in two signatures: `custom(subject, meta, body)` carries the supplied
  meta and reads nothing, `custom(subject, meta, reads, body)` also carries the supplied `ReadScope`.
  A trait read without a declaration never enters the host's capture policy, so it is never captured.
- Every combinator requires at least one child; an empty `all_of`/`any_of`/`sequence`/threshold is an
  `IllegalArgumentException` at construction (the document twin is `SC-DOC-0002`).

### 4.4 Focus

A focus binds a subject or says why not. `staticCells` is the build-time decision set; it is empty
for dynamic focuses (`context`, `entity`) and non-empty for static ones (`occupant`, `bay`,
`allCells`, `door`, `nested`). `bindsSubjects` is false for `context` and `nested`: a nested take
yields the child view as its subject but never a claim subject. `bindsClaimableSubject` defaults to
false for an unknown custom focus; the shipped root block/entity focuses answer true. When the bay's
footprint claims cells, the builder rejects a subject-binding focus that is not claimable with
`SC-BPT-0005`. `reads` defaults to the
static cells; an entity focus adds a window and the `entity` kind; a nested focus adds the `nested`
kind and declares no depth — how deep the capture expands is the framework's, not the author's
(D-65); a trait expectation adds its trait id. An expectation that reads outside
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
focus is a build error (`SC-BPT-0005`). A custom focus that binds a nested body and leaves
`bindsClaimableSubject` false is also rejected at build when the bay's footprint claims cells; a
custom focus that lies still fails at check with `SC-CLM-0004`. A nested body is still an ordinary
occupant of the parent frame; a
check that binds it through `Focuses.occupant` fails at claim prediction with `SC-CLM-0004` before an
`Admitted` exists, and `settle` revalidates the same rule (`engine` §7.5).

`Expectations.empty()` matches air or a vanilla fluid block (`minecraft:water`/`minecraft:lava`);
`Expectations.nonFluid()` matches every occupant that is not one of those fluid blocks (air is
non-fluid). `NOTAIR` is exactly `not(air())`: water and lava are non-air and pass, while air fails.
`empty` and `nonFluid` are opt-in leaves for slots whose real rule is "no air and no fluid" or "no
fluid"; they do not redefine `NOTAIR`. `anyOccupant` remains the "any readable occupant, including
air" leaf. The kernel classifies fluids by the vanilla block type; a waterlogged block is a solid
block occupant, and a host `fluid` expectation reads its captured fluid state instead.

### 4.5 Read scope

`ReadScope` is the one read closure: `(cells, keys, traits, kinds, windows)`. `cells` is the
declared cell set, `keys` the context keys a check or computation reads, `traits` the trait ids, and
`kinds` the occupant kinds the host must resolve. There is no depth: whether a blueprint reads
nested bodies is the `nested` kind, never how deep (D-65). `union` combines cells/keys/traits/kinds
by set union and appends the windows in declaration order with equal windows deduplicated;
`withKeys`/`withTraits`/`withKinds`/`withWindows` union their argument, so a narrowed copy can never
lower a requirement. `none()` is empty.

A host expectation (§4.6) adds no scope entry: one snapshot carries every host payload, so the
declared cells already bound which anchor the read sees.

`Check.reads()` is `focus.reads(bay.cells()) ∪ expectation.reads()` plus
`expectation.meta().requires()`. `Blueprint.reads()` is the union of every check's `reads()` and every
computation's `ReadScope.cells(region)`: computations contribute cells only, because a `Computation`
declares no trait or kind. A computation that reads a trait must be accompanied by a check that
declares that trait id, or the host will not capture it. The engine charges `cells.volume()` and the
sections the cells touch; a window charges its inflated volume and sections. Declared reads are a
promise: a check that reads a cell or key outside its scope is a bug (review), not a runtime error. A
non-`None` bay's cells must stay inside `bay.cells ∪ footprint`; a `Bound` footprint is checked at
settle, a `Box`/`Declared` at build.

The host derives `SnapshotPolicy` from `Blueprint.reads()`: `kinds` and `traits` map
directly, and `windows` maps each blueprint-local window through the siting into the world frame.
The nested ceiling is not derived: the framework passes its own constant
(`SnapshotPolicy.defaultMaxDepth`, 16) and only a host that wants a lower ceiling sets `maxDepth`
itself (D-65).
`maxElements` and the capture bounds remain the host's choice. No hidden read is legal; a policy
without the declared trait ids produces `TraitSlot.Refused` (or no slot), never a default value. A
generic `occupant`/`bay` read declares no kind: it sees whatever the host captured. A blueprint that requires entity-over-block or a nested body therefore
uses an `entity`/`nested` focus (or a custom focus that declares the kind), because `reads.kinds` is
the only capture signal; an `occupant` focus over an entity the host never captured sees the block
under it. The envelope reads the stored cell, not whatever its siblings captured: its verdict follows
`cellOccupant` and never drifts with another check's kind declaration — only `envelopeEntities`
adds a kind, to the envelope's own check (§4.11, D-55).

### 4.6 Expectation context

`FocusContext` is what a focus sees. `ExpectationContext` adds the subject and `withSubject`, which
re-types the subject for `each_cell`. Both expose the same bay/view/siting/phase/locus/context/budget
and the same `read`/`cellOccupant`/`occupantAt`/`refusalAt`/`claimsAt`; there is no second session
object. `read` maps the local cell through the siting before asking the view; an unmapped local cell is
`Unreadable(outOfBounds, SC-OCC-0004)`, never air and never a silent skip. `cellOccupant` maps the
same way and adds no second verdict: it is the view's stored-cell answer (`world` §4.3). `BayView.read`
is the same
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

`Expectations.context(key, test)` is the shipped pairing for `Focuses.context(key)`: its subject is
`key.type()`, `meta.requires()` is exactly `Set.of(key)`, and a value
for which `test` returns false is `Mismatch(SC-EXP-0001)` on a `key` locus. It never fabricates a
value: an absent key is the engine's `SC-CTX-0007` gate (or `Focuses.context`'s `Unavailable`), so
`test` runs only on a found value. The expectation is the only way to declare a whole-structure
context read; a range test is a caller's `Predicate`, not a second API.
The context view behind it is the attempt composite of `engine` §7.7: the attempt's own values shadow
the cached instance values, and a cached key is composed only when the plan declares it as an
instance dependency — in a check's `ExpectationMeta.requires()` or a computation's `inputs()`. The
cache itself is the host `CachedContext` port, looked up by `(id, type)`.

`Expectations.host(type, test, mismatchCode, absentCode, description)` is the one bridge for host
payload. It reads `ctx.view().get(subject.ref(), type)` once: an absent value is
`Unreadable` with the caller's `absentCode` (never a mismatch), a passing `test(host, cell)` is
`Match`, and a failing test is `Mismatch(mismatchCode, description)`. The kernel never constructs or
interprets the host value, never declares a host type in `ReadScope`, never mints `SC-MC-0010`, and
`meta().mismatchCode()` is the supplied code. The adapter passes its own absent code (`world` §4.11,
`adapter` §12); its `property`, `state`, `tag` and `fluid` factories are exactly one or two such
leaves, and the `fluid` factory composes its two typed reads under `allOf` (`adapter` §3.2).

### 4.7 Blueprint

`Blueprint` is a final class with a package-private constructor. The builder is the only public
construction path.
It is immutable: private final fields, copied collections, a cached `Plan`, cached `bayCells()` and
cached `reads()` (all excluded from equality). `equals` is structural over the declarations (id,
bounds, bays, checks, computations, seams, anchors, orientation, derivations, arguments, description);
it excludes `source`, `plan`, `bayCells()` and `reads()`.

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
| two bays' owned sets (`Box` cells, `Declared` cells) intersect and at least one is `exclusive`; a `Bound` owned set is unknown at build and is not judged here | `SC-BPT-0002` |
| a bay leaves `bounds` | `SC-BPT-0003` |
| duplicate bay/check/seam id, or two different bays with one id | `SC-BPT-0004` |
| a bay breaks its coverage obligation (§4.1), a claiming bay binds a non-claimable focus, an `exclusive` bay owning no known cell (`None`, an empty `Declared`) | `SC-BPT-0005` |
| anchor roles duplicated, primary role absent, anchors without a policy | `SC-BPT-0006` |
| computation DAG cycle/missing input/duplicate output/empty region/section input | `SC-BPT-0007` |
| derivation order duplicate, output duplicate/also a computation output | `SC-BPT-0008` |
| seam outside every bay | `SC-BPT-0009` |
| a declared footprint leaves `bounds` | `SC-BPT-0010` |
| a focus's static cells leave its bay | `SC-CHK-0003` |
| a required `match` key is produced by no computation | `SC-CHK-0002` |

An empty `Bay`, an empty, duplicated or `uprightOnly`-breaking `allowed` list (`OrientationPolicy`)
and a negative `DerivationSpec.order` are rejected at construction, before `build()`, by a bare
`IllegalArgumentException` that carries no code; the document face catches each and reports it as a
positioned `SC-DOC-0002`.

A required `instance` key needs no computation producer: the host may supply it from the instance's
cached context, and a runtime absence is the engine's `SC-CTX-0007`, not a build error. A required
`match` key is attempt-local and has no cache, so it must still be produced by a computation in the
same blueprint. A derivation output is an `instance` key, not a computation producer; a check may
require it and the value arrives only through the cached context (or it is absent).

The document compiler runs the same table and reports findings with loci before calling `build()`;
a `build()` throw after a clean compile is a kernel defect.

### 4.9 Anchors and orientation

The anchor cell is the definition origin, supplied by the caller. `AnchorSpec.value` names the
anchor's heading in the blueprint's own coordinates (D-68); at check time the host reads the
heading actually present at the anchor cell and `OrientationPolicy.select(declared, observed)`
scans `allowed` in `Orientation.all()` order for the first `o` with
`o.apply(declared) == observed`; `null` means no admissible pose. One blueprint therefore matches
every allowed turn. `allowed` is non-empty, duplicate-free and in canonical order; `uprightOnly`
is a build-time promise (`o.apply(up) == up`), not a match-time filter. A blueprint with no anchor
has a null orientation policy and takes a `Siting` directly.

`AnchorSpec` and `OrientationPolicy` are declarations, not a kernel read. The host resolves the
anchor cell against its snapshot and answers a `SitingResult`. The host's anchor-invalid
`SC-BPT-0006` covers an air cell, a missing property, an observed or declared value that is not a
heading, no admissible orientation and a null policy. An uncaptured anchor cell is the host's
unreadable answer. The kernel has no `Anchors` resolver and no live anchor read. There is no
fallback heading and no search (`adapter` §3, §11).

### 4.10 Computations

`Computation` has no phase field. The interface split is the phase: a `SectionComputation` receives a
`SectionContext`, which has no `read(ContextKey)`; an `AggregateComputation` receives an
`AggregateContext`, which has no `contribute`. This is a compile-time guarantee, not a convention.

A computation's identity is its output key: `id()` returns `output().id()`. `Blueprint.equals`
compares members by value, so a computation, focus or expectation that is not a value type (a
record, or an equal-by-value class) breaks I-13 for both frontends; the kernel requires value types
and does not check them.

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
`Computations.count` counts non-air occupants whose anchor is in the slice; the three-argument
overload also requires `predicate.test(occupant)`, so a body is counted once, by the slice that
holds its anchor, and never once per covered cell. Its output key must have a merge
(`Merges.sum` is the whole-structure total) and its region must be non-empty; both are constructor
errors. `volume` counts cells; `air` counts air cells. `count` (both forms) and `air` require a total
slice: when `unreadable()` is non-empty they report each refusal (relaying the refusal's code through
the trust guard) and return `Missing`, never a short sum and never zero. `volume` is geometry and
stays total. A custom computation that sums over its slice and ignores `unreadable()` is a review
defect, not a different verdict. An occupant spanning a section boundary belongs to the section of
its anchor, so a fold never counts it twice. All three are section computations; folding is the
key's `Merge`.

I-13 covers what a document can express: a computation carrying a lambda (the three-argument
`count(predicate)`) has identity equality and therefore no document counterpart.

`AggregateContext` has the same total read for the computation's whole local region, not one
section: `cells()` is `computation.region()`, `read(cell)` maps the local cell through the siting and
returns `CellRead.Unreadable` for a refused, out-of-bounds or unmapped cell, and `unreadable()` lists
every refused local cell of the region in cell order (a refusal is never an absent occupant). An
aggregate runs once after the fold. The engine charges the region's volume and the sections it
touches once, before `compute`; an exhausted budget is `Undecided(budgetExhausted)` and the
aggregate never runs. An aggregate that treats `unreadable()` as empty is a review defect, like its
section twin; `volume`-style geometry is the caller's own choice, not a kernel guarantee. The
aggregate's `context()` is the attempt composite, so an instance key the aggregate declares in
`inputs()` but the current attempt did not produce may come from the host cache (`engine` §7.7).

### 4.11 Envelope

`BlueprintBuilder.envelope()` is the only way to ask for a closed world. The build computes
`E = bounds.asSet − (union of bay.cells ∪ union of Footprint.Declared cells)` from the local
declaration lists before the "at least one check" rule. An empty `E` is a silent no-op: no bay, no
check, no extra read, no charge, and the blueprint is structurally equal to the same build without
`.envelope()`. A non-empty `E` appends one reserved bay and one reserved check, both id
`structurecumulus:envelope`, **last** in the declared order: the bay is `E` with
`Coverage.everyCell`, `Footprint.none`, `Sharing.shared`; the check is `Focuses.bay()` with the
envelope's own cell rule (`Expectations.envelopeCells()`, or the body-narrowing rule when the
blueprint also sets `envelopeEntities`). A declared bay or check already using the
reserved id is `SC-BPT-0004`. There is no new plan or kernel type: the synthetic pair is an ordinary
bay and check, so budget, read-scope, siting, refusal and finding paths are unchanged, and
`Footprint.none` keeps the envelope out of the claim ledger.

The envelope is the bounds, not the declared bays. A block outside `bounds` is invisible, while a
block inside `bounds` and outside every bay or declared footprint is foreign. A port, an ignored cell
or an "any occupant" cell therefore has to be declared as a bay (for example an `everyCell` bay with
`anyOccupant`); there is no allow-list API and no `envelope(CuboidSet)` overload. The envelope judges
what each cell stores (`cellOccupant`, `world` §4.3): a block, fluid or block entity that is not air
is `SC-EXP-0006` located at that cell. An entity covering an envelope cell is transparent (D-55):
`BlueprintBuilder.envelopeEntities()` switches the rule to the body `read` narrows to and adds the
`entity` kind to the envelope check's own reads so the host captures what it will judge; without the
switch the envelope declares no kind, so its verdict never drifts with a sibling check's kind
declarations. A nested body is outside this promise: the envelope captures no nested depth, and the
narrowed body matters only under `envelopeEntities`. A host that cannot answer the stored cell makes
the envelope `Unreadable`, never a silent pass; an unreadable envelope cell is the ordinary
`SC-OCC-0004` relay and leaves the check `Unreadable`, never a mismatch. `BlueprintBuilder.build()`
does not mutate the builder's lists, so it can be called twice with equal results.

### 4.12 Declaration description port (D-91)

Remedy derivation (D-85) needs the blueprint's declarations as values. `structure` has a
**total** declaration description port over `Expectation` trees — `Expectation.declaration()`
returns an `ExpectationDeclaration` (§3), landed with P1:

- Every built-in leaf describes as a sealed declaration value carrying its declaration: `block` its
  type (the leaf declares no properties — orientation-derived properties are the materializer's,
  design 14 §6), `trait`/`traitAbsent` their key, `host` its host-type token and codes;
  `air`, `anyOccupant`, `empty`, `nonFluid` and `envelopeAir` are marker arms.
- Every composite (`allOf`/`anyOf`/`not`/`sequence`/`atLeast`/`atMost`/`exactly`/`eachCell`)
  describes as its kind — with its count for the thresholds — plus access to its children.
- A `custom` expectation describes as **undescribable**: the kernel cannot know what a custom body
  expects, and a remedy over an undescribable expectation is advisory, never `machineApplicable`.

The port is total — every expectation answers exactly one arm — and it reads declarations only: it
never evaluates a `Reply` and never touches a world. If P1 finds that resolving a finding's cell to
the expectation that produced it also lacks a public port on `Blueprint`, that resolution port is
added in D-91's scope and reported, not under a new decision number.

## 5. Pipeline role

- `compile`: the builder validates; the document compiler builds the same value.
- `check`: the engine walks `plan()`; `Check.run` is the only focus/expectation meeting point.
- `settle`: bound footprints from `CheckResult.subjects` become claims.
- `derive`: `DerivationSpec` is only a reference; no structure type is resolved there.

## 6. Codes

`SC-BPT-0001`…`0010`, `SC-CHK-0001`…`0003`, `SC-EXP-0001`…`0006`, `SC-SPC-0001`/`0002` (via
`LocusMap`/`Siting`), `SC-CTX-0007` (focus), `SC-OCC-0001`/`0002`/`0003`/`0004` (focus and relay).
The producer table in `finding.md` is authoritative. A focus relay of a host `ReadRefusal` code is
not a kernel producer row: the kernel only guarantees the `SC-OCC-0004` substitute for a code already
in `Codes.all`.

The builder refuses by throwing `BlueprintRejected`, an `IllegalArgumentException` subtype only this
package constructs; the compiler reads the `CodeEntry` from the exception and never parses the
message (D-66). The message keeps its `SC-…` prefix so existing message assertions hold.

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
- Anchors: `OrientationPolicy.select` order and upright promise; a no-anchor blueprint takes a
  `Siting` directly; the host owns the anchor-cell read.
- Host expectation: captured/false/absent; supplied mismatch code and description; the supplied
  absent code for an absent value; no `ReadScope` entry.
- Computations: section/aggregate split; count/volume/air; `Missing` → absent key; a partial section
  makes `count`/`air` Missing, never a short sum; `volume` stays total.
- Envelope: empty `E` no-op and equality with the plain build; uncovered cells and declared
  footprints; envelope-only blueprint; reserved id collision; two builds; `SC-EXP-0006` metadata;
  fusion void, AE2 empty envelope, PNC port, GT widened ring, unreadable, budget and outside-bounds
  scenarios; an entity over an air cell is transparent, the block under it still fails, and
  `envelopeEntities` rejects the entity while adding the `entity` kind to `reads()`.
- Content count: predicate match, non-air, multi-section sum, one multi-cell body counted once by its
  anchor, a refused slice Missing with a relayed refusal and never a zero, a readable no-match slice
  zero, merge/region constructor errors.
- Aggregate context: `cells()` is the computation region; the total mapped `read`; a refused or
  out-of-bounds region cell is `Unreadable`; the region is charged once by volume and sections; an
  exhausted budget skips the aggregate and writes nothing.
- Expectation context: `match`/`mismatch` (`SC-EXP-0001`)/missing (`SC-CTX-0007`), and
  `meta.requires == Set.of(key)`.
- Build: an `instance` requirement needs no producer; a `match` requirement without a producer is
  `SC-CHK-0002`; a derivation output is a legal `instance` requirement. Cached composition: instance
  keys only; eligible via check requires or computation inputs; `(id, type)` lookup answers a
  host-decoded key with lost `merge`; current values shadow; a `match` key is never read;
  a wrong type or host exception is absent; an aggregate sees the composite.

## 8. Dependencies

`core`, `space`, `finding`, `world`, `claim`. It does not import `context` (computations receive
contexts declared here), `engine`, `document` or `spi`.
