# 07 — Expectations (`kernel.expectation`)

> What one bay expects, what a test may answer, how tests compose, how a document names one.
> Serves R-06, R-07, R-08, R-10, R-13; frozen names in `01-glossary.md` §E.

## 1. Scope

| Owns | Does not own |
| --- | --- |
| the `Expectation<T>` SPI, `ExpectationContext<T>`, `ProbeSession`, `Verdict`, `Reply`, `Bindings`, `BindingKey<T>` | which bays exist, their `Cuboid`, their `Coverage`, their `Footprint` and `Sharing` (U5) |
| `Focus<T>`, `Outcome<T>`, `DecisionKind`, the verdict algebra, `ExpectationMeta`, `Expectations`, `ExpectationFactory`, `FactoryContext` | claims and conflicts — an expectation emits none (U4) |
| the subject channel (`Found.subjects`), the `reads()` contribution and `specificity` | when a bay runs, the cells/sections `Spend`, `Stage` and the step order (U7); finding order and caps (U9); the document grammar (U6) |

It never mutates, throws, or reads outside the context.

## 2. Vocabulary

Names defined in `01-glossary.md` §E and shaped in §3: `Expectation<T>`, `ExpectationMeta`,
`ExpectationContext<T>`, `Focus<T>`, `FocusContext`, `Outcome<T>`, `DecisionKind`, `ReadScope`,
`ProbeSession`, `Verdict`, `Reply`, `Bindings`, `BindingKey<T>`, `Expectations`,
`ExpectationFactory`, `FactoryContext`. `Reply` is the one non-generic name here: a verdict is
about a whole check, not about its subject type.

## 3. API design

```java
public interface Expectation<T> {
    Reply test(ExpectationContext<T> ctx);                          // total, re-entrant, stateless
    Class<T> subject();                                             // the one subject type it accepts
    default ReadScope reads() { return ReadScope.none(); }          // declared reads beyond the bay
    default ExpectationMeta meta() { return ExpectationMeta.anonymous(); }   // no id, no demand
}

public interface Focus<T> {
    Outcome<T> take(FocusContext ctx);                              // binds the subject, or says why not
    Class<T> subject();
    DecisionKind decisionKind();                                    // EVERY_CELL | ONE_SUBJECT | ANY_SUBJECT
    CuboidSet staticDecisionCells(Cuboid bayCells);                          // DESIGN-time; no world needed
    ReadScope reads(Cuboid bayCells);
    FindingBundle describeMissing();
}

public sealed interface Outcome<T> permits Outcome.Found, Outcome.Missing {
    record Found<T>(T value, List<OccupantRef> subjects, FindingBundle notes) implements Outcome<T> {}
    record Missing<T>(FindingBundle findings) implements Outcome<T> {}

    <R> R fold(BiFunction<T, List<OccupantRef>, R> onFound, Function<FindingBundle, R> onMissing);
}

public enum DecisionKind { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }

public record ExpectationMeta(NamespacedId id, int specificity, Set<ContextKey<?>> requires,
                              FindingCode mismatchCode) {
    public static ExpectationMeta anonymous();   // structurecumulus:anonymous, 0, empty, SC-EXP-0001
    public static ExpectationMeta of(NamespacedId id, int specificity, FindingCode mismatchCode);
}

public enum Verdict { MATCH, MISMATCH, ABSTAIN, UNREADABLE }

/** Four states, never generic: one check answers one Reply, whatever its subject type is. */
public record Reply(Verdict verdict, Bindings bindings, FindingBundle findings) {  // ctor is private
    public static Reply match();  public static Reply match(Bindings b);
    public static Reply match(Bindings b, FindingBundle notes);    // no ERROR findings
    public static Reply mismatch(Finding first, Finding... rest);  // >= 1 finding by construction
    public static Reply unreadable(Finding first, Finding... rest);
    public static Reply unreadable(FindingBundle findings);        // the focus's Missing channel
    public static Reply abstain();
}

public interface FocusContext extends AggregateContext {          // read, occupants, context
    BayView bay();  WorldView view();  Siting siting();           // bay() carries the id and the cells
    Cuboid cells();  Stage stage();  Locus locus();               // locus() is this check's label frame
}

public interface ExpectationContext<T> extends FocusContext {     // what a test may see
    T subject();  List<OccupantRef> subjects();
    <V> ExpectationContext<V> withSubject(V subject, List<OccupantRef> subjects);   // one cell of a bay test
    static <T> ExpectationContext<T> of(FocusContext base, T subject, List<OccupantRef> subjects);
    Budget budget();  ProbeSession session();
    @Nullable OccupantSnapshot occupantAt(LatticePoint cell);
}
```

| Type | Signature |
| --- | --- |
| `Bindings` / `BindingKey<T>` | `EMPTY`, `of`, `<T> @Nullable T get(key)`, `merge(Bindings, Bindings, Locus): Reply`; key is `(NamespacedId id, Class<T> type)` |
| `FocusContext` | `extends AggregateContext` (which answers `read(key)`, `occupants(box)` and `context()`) and adds `bay(): BayView`, `view()`, `siting()`, `cells()`, `stage()`, `locus()`; a check therefore reads `ContextKey`s through `context()` and nothing else |
| `ExpectationContext<T>` | `extends FocusContext` and adds `subject(): T`, `subjects()`, `withSubject(value, subjects): ExpectationContext<V>` — the one way to re-type the subject for a single cell of a bay test —, the static `of(base, subject, subjects)`, `budget()`, `session()`, `occupantAt(p)` — null ⇔ the cell's section is not readable |
| `ProbeSession` | `occupant(LatticePoint)`, `claimsAt(LatticePoint): List<Claim>` (frozen ledger only; session reservations stay private), `<T> @Nullable T binding(BindingKey<T>)`, `remaining(): Budget`, `stage(): Stage` |
| `Expectations` | `allOf`, `anyOf`, `not`, `sequence`, `atLeast(k)`, `atMost(k)`, `exactly(k)`, `block`, `air`, `eachCell(element)`, `trait`, `anyOccupant`, `nothing`, `custom(meta, body)` — each returns an `Expectation<T>` of its children's subject type; `eachCell` is the only one that changes it (`Expectation<OccupantSnapshot>` → `Expectation<BayView>`) |
| `ExpectationFactory` / `FactoryContext` | `id()`, `create(DataView, FactoryContext): @Nullable Expectation<?>`; `factory(id)`, `source()`, `range()`, `report(Finding)` |

`subject()` is the one type a test accepts: `Check<T>` refuses a focus and an expectation whose
`subject()` differ (`SC-CHK-0101`), so a `DoorView` test cannot be paired with a `BayView` focus.
`meta().specificity` is a tie-break, never a budget input; `requires` is a **coverage** demand
(§4.5). `reads()` defaults to `ReadScope.none()`, and an expectation that reads outside the bay's
decision cells — a neighbour cell, an entity window, a `ContextKey` — must declare it, because
`Check.reads(bayCells)` is `focus.reads(bayCells).union(expectation.reads())` and that read closure is
what the engine charges in cells and sections (`22` §6.1, §9.5). There is no `cost` field and no
per-unit number at all: the budget is a coarse resource cap on cells and sections. `Reply` is **not** generic:
a verdict is about a whole check, whatever its subject type, and one check yields one `Reply` in one
of the four states. A `custom(meta, body)` test is anonymous — specificity `0`, no declared reads —
so it sorts last among ties and is charged only its decision cells; `Expectation` itself is not a
lambda target, because `subject()` is abstract. `Predicate` is banned (glossary §O) and the
shape is wrong anyway: `boolean` cannot carry the expected/found pair (R-07) and has no "unknown"
answer, so an unloaded chunk reads as *wrong*; no metadata slot (R-08); no lawless
`and`/`or`/`negate` path.

## 4. Semantics

### 4.1 Reply invariants

| Verdict | Bindings | Findings | Why |
| --- | --- | --- | --- |
| `MATCH` | 0..n | 0..n, none `ERROR` | a witness; INFO/WARNING may annotate it |
| `MISMATCH` | **empty** | **≥ 1** | a refutation without evidence is a defect |
| `UNREADABLE` | **empty** | **≥ 1** | the unreadable cell is the repair target |
| `ABSTAIN` | empty | **empty** | a refusal must be silent, or it reads as evidence |

The package-private `Reply` constructor is the one gate: only `match*` stores bindings. **`UNREADABLE`
is not `MISMATCH`** — a
mismatch states a world fact ("stone, not iron bars"), unreadable a snapshot fact ("I did not read"),
so merging them makes an unloaded chunk look wrong. **Air is readable**: air is a non-null
`OccupantSnapshot`, so `block(...)` on air is `MISMATCH` naming `minecraft:air`; `occupantAt` is `null`
only when the status is not `READABLE`. The four states are exhaustive and `Reply` is not generic: a
check produces exactly one `Reply`, and absence has no verdict of its own — it enters through the
focus (§4.2).

### 4.2 Absence

A focus answers `Outcome<T>`, an expectation answers `Reply`, and a `Check` is the one place the two
channels meet:

```java
public CheckResult run(FocusContext ctx) {
    Outcome<T> taken = focus.take(ctx);
    if (taken instanceof Outcome.Found<T> found) {
        return new CheckResult(
                expectation.test(ExpectationContext.of(ctx, found.value(), found.subjects())),
                found.subjects(), found.notes());
    }
    Outcome.Missing<T> missing = (Outcome.Missing<T>) taken;
    return new CheckResult(Reply.unreadable(focus.describeMissing()), List.of(), missing.findings());
}
```

| Situation | Emitted | Code |
| --- | --- | --- |
| the subject is not there | `Outcome.Missing` → `Reply.unreadable` | `SC-FOC-0101` |
| more than one candidate subject, none decisive | `Outcome.Missing`, ambiguous | `SC-FOC-0201` |
| a section the focus needed was refused | `Outcome.Missing` | `SC-FOC-0301` |
| a partial contribution is missing at the barrier | the key is `Outcome.Missing`, every reader is `unreadable` | `SC-FOC-0302` |

- `Missing` carries findings labelled on a `Locus` (`LOCAL` for a context-key absence, `WORLD`/`LOCAL`
  for a cell), so the label points at the absence, not at the bay's other cells.
- A key-reading focus is `Focuses.context(key)` on a `ContextKey<T>`: it is exactly
  `ctx.context().require(key)`, so an absent key is `Outcome.Missing` carrying `SC-FOC-0302` — message
  naming the key, label on a `LOCAL` locus that holds no cell and no key of its own — never a `Found`
  holding null and never a zero. The key is declared once, in `reads()`; and the same absence also
  travels as `ContextView.require`'s own finding in `CheckResult.notes` (§4.2's sketch), so it is
  reported twice, in two frames.
- A missing subject is never `test(null)` and never silence: `fold`'s second arm does not call the
  expectation at all, and the reply is `UNREADABLE` carrying the focus's findings.
  `describeMissing()` supplies their wording.
- A check whose every unreadable finding is `SC-FOC-*` settles `Undecided(ONE_SUBJECT_ABSENT)`, never
  `Failed`: nothing was refuted, the subject was not found.

### 4.3 The fold: two total orders on four verdicts

Counters: `m` MATCH, `x` MISMATCH, `a` ABSTAIN, `u` UNREADABLE, `t` untested, `n = m+x+a+u+t`. The
enum's own order means nothing; `allOf` is `max` under `MATCH ≺ ABSTAIN ≺ UNREADABLE ≺ MISMATCH`,
`anyOf` is `max` under the exact reverse. Each cell reads `allOf / anyOf`:

| left ⊕ right | MATCH | ABSTAIN | UNREADABLE | MISMATCH |
| --- | --- | --- | --- | --- |
| **MATCH** | M / M | A / M | U / M | X / M |
| **ABSTAIN** | A / M | A / A | U / U | X / A |
| **UNREADABLE** | U / M | U / U | U / U | X / U |
| **MISMATCH** | X / M | X / A | X / U | X / X |

The rows read: a known violation is decisive (`allOf(MISMATCH, UNREADABLE) = MISMATCH`), a witness
cannot be withdrawn (`anyOf(MATCH, ABSTAIN) = anyOf(MATCH, UNREADABLE) = MATCH`), and an unknown child
that might be the witness keeps the disjunction unknown (`anyOf(MISMATCH, UNREADABLE) = UNREADABLE`).
Both folds are associative, commutative and idempotent, and `not` is an involution. Three laws fail:
De Morgan for thresholds (`not(atLeast(1))` over MATCH+ABSTAIN is MISMATCH while `atMost(1)` over the
negated children is MATCH); `anyOf` commutativity *in bindings* (the first match binds); `allOf`
commutativity *in findings order*.

| Combinator | `MATCH` when | `MISMATCH` when | else |
| --- | --- | --- | --- |
| `allOf` | `m = n` | `x > 0` (stop at the first MISMATCH) | `u > 0` → UNREADABLE, else ABSTAIN |
| `anyOf` | `m ≥ 1` (stop at the first MATCH) | `m = a = u = t = 0` | `u > 0` → UNREADABLE, else ABSTAIN |
| `sequence` | as `allOf`, declaration order | as `allOf`, first MISMATCH in declaration order | as `allOf` |
| `atLeast(k)` | `m ≥ k` | `m+u+a+t < k` | `u > 0` → UNREADABLE, else ABSTAIN |
| `atMost(k)` | `m+u+a+t ≤ k` | `m > k` | as above |
| `exactly(k)` | `m = k`, `u+a+t = 0` | `m > k` or `m+u+a+t < k` | as above |
| `not(c)` | `c` is MISMATCH | `c` is MATCH | `c`'s verdict (A stays A, U stays U) |

Thresholds use this tally, not a pairwise fold. A
combinator stops only on a **decisive** verdict, so `allOf` continues past `UNREADABLE` and `ABSTAIN`
looking for a `MISMATCH`, which would otherwise read `Undecided` where `Failed` was provable;
`atLeast(k)` with `k > n` is refuted by the composite (`SC-EXP-0004`).

### 4.4 Bindings and merge

`merge` returns `Reply`, so no merged map is visible without a verdict.

- Disjoint keys, or equal values: `MATCH`, one entry per key.
- One key, unequal values: `MISMATCH` with `Bindings.EMPTY` and one `SC-EXP-0003` finding naming both
  loci and both values — a finding, never an exception.
- One `id` under two `Class` tokens: `MISMATCH` and `SC-EXP-0003`, since the id space is flat.

### 4.5 Specificity and requires

Specificity is `max` for `allOf`/`sequence`, `min` for `anyOf` and thresholds with `k < n` (a test
satisfiable while skipping a child cannot claim its narrowness), and `0` for `not`. `requires` is
the union, `anyOf` included, because it is a demand on *coverage*: an unserved `ContextKey` makes the
bay `UNREADABLE` before any cell is read, whereas treating it as a miss turns "I did not look" into
`MISMATCH`. `reads()` is the union of the children's scopes and `subject()` their common `T`;
neither is a price — the read scope is what the engine bills in cells and sections at run time (§4.7).

### 4.6 Subject bays and the door rule

A bay owns one `Cuboid`; the focus decides which cells inside it are asserted, and the expectation
only ever sees the subject.

| Focus kind | Subject type | `DecisionKind` |
| --- | --- | --- |
| `bay` | `BayView` | `EVERY_CELL` |
| `allCells` | `List<OccupantSnapshot>` | `EVERY_CELL` |
| `occupant` | `OccupantSnapshot` | `ONE_SUBJECT` |
| `entity` | `EntitySnapshot` | `ONE_SUBJECT` |
| `nested` | `NestedView` | `ONE_SUBJECT` |
| `context` | `T` | `ANY_SUBJECT` |
| `door` | `DoorView` (two `OccupantRef`s, one combined state) | `ONE_SUBJECT` |

| Rule | Statement |
| --- | --- |
| element | the element tests are `Expectations.block(type)` and `Expectations.air()`, both on an `OccupantSnapshot`; the focus that binds that subject is `occupant`, and `door` is a focus (`Focuses.door`), never a combinator |
| bay test | a test over a whole bay is `Expectations.eachCell(element)` on a `BayView`, paired with `bay`; the element sees one cell at a time through `withSubject` |
| geometry | the focus's decision cells are `CuboidSet`s inside `bay.cells`, computed at DESIGN by `staticDecisionCells(bayCells)`; a dynamic kind (`occupant`, `entity`, `nested`, `context`) has none |
| subject | `focus: door` yields a `DoorView`; a door expectation consumes `DoorView` and never `BayView`, so pairing it with a `BayView` focus is `SC-CHK-0101`, not a cast |
| atomicity | one `Check`, one `Reply`: the focus takes its subject once, the expectation answers once, and the subjects that were bound travel in `Found.subjects` |
| evidence | per-cell facts are `Label`s on **one** finding — first label primary, rest secondary |
| claims | the expectation emits none; U4 builds `Bound` from the `Found.subjects` footprints and `Box` from `bay.cells` |
| half door | wrong half ⇒ `MISMATCH`, primary label on it, secondary on the matching half |

**Why rectangles suffice**: claim containment (U4), prescreen bounds (U7), interior volume (U8) and the
orientation isometries (U1) are all box arithmetic, and a voxel-set bay degrades all four to cell
enumeration at once. A non-rectangular *single* bay needs a second footprint type beside `Cuboid`, so
it is refused (I-3); it becomes several `allOf` bays or one bounding box whose test rejects the
must-be-empty cells.

### 4.7 Ordering: declaration, not cost

Independent units keep declaration order. `specificity` is a report-order hint, not a budget
number; `requires` still puts a unit after the keys it reads. There is no `admission` field and no
per-unit cost: the engine charges the declared read closure in `cells` and `sections` (`22` §9.5).

## 5. Reference sketch

```java
final class HatchExpectation implements Expectation<OccupantSnapshot> {   // three outcomes, one binding
    static final NamespacedId ITEM_INVENTORY = NamespacedId.of("minecraft", "item_inventory");
    static final BindingKey<OccupantRef> HATCH = BindingKey.of(ID("hatch"), OccupantRef.class);
    public Class<OccupantSnapshot> subject() { return OccupantSnapshot.class; }
    public ExpectationMeta meta() { return ExpectationMeta.of(ID("hatch"), 80, SC_EXP_0001); }

    public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
        OccupantSnapshot o = ctx.subject();                              // never null: absence is Missing
        if (!o.hasTrait(ITEM_INVENTORY)) return Reply.mismatch(Finding.builder(SC_EXP_0001)
                .message("expected an inventory-bearing hatch")
                .label(ctx.locus(), "expected an inventory-bearing hatch")
                .note("found " + o.descriptor().type()).build());
        return Reply.match(Bindings.of(HATCH, o.ref()));
    }
}

final class OakDoorExpectation implements Expectation<DoorView> {     // one bay, two subjects, one Reply
    private static final NamespacedId OAK = NamespacedId.of("minecraft", "oak_door");

    public Class<DoorView> subject() { return DoorView.class; }
    public ExpectationMeta meta() { return ExpectationMeta.of(ID("oak_door"), 90, SC_EXP_0001); }

    public Reply test(ExpectationContext<DoorView> ctx) {
        DoorView door = ctx.subject();                                   // both halves, already bound
        if (!OAK.equals(door.first().type())) return wrongHalf(ctx, door.first(), "lower");
        if (!OAK.equals(door.second().type())) return wrongHalf(ctx, door.second(), "upper");
        return Reply.match();                                            // subjects travel in Found
    }

    private static Reply wrongHalf(ExpectationContext<DoorView> ctx, OccupantRef found, String half) {
        return Reply.mismatch(Finding.builder(SC_EXP_0001)               // one finding, two labels
                .label(cellLocus(ctx, found.anchor()), "expected the " + half
                        + " half of an oak door, found " + found.type())
                .label(ctx.locus(), "the other half matches; the bay is claimed only when both do")
                .build());
    }

    private static Locus cellLocus(ExpectationContext<DoorView> ctx, LatticePoint cell) { /* … */ }
}

// block+trait on one cell, an anyOf fallback, and a pair whose members may ABSTAIN.
var core  = Expectations.allOf(Expectations.block(NamespacedId.of("minecraft", "barrel")),
                               Expectations.trait(NamespacedId.of("minecraft", "item_inventory")));
var panel = Expectations.anyOf(Expectations.custom(PANEL_META, ctx -> {
    OccupantSnapshot s = ctx.occupantAt(ctx.cells().min());
    return s == null ? Reply.unreadable(/* as above */)
            : s.descriptor().tags().contains(Tags.STONE_FAMILY) ? Reply.match() : Reply.abstain();
}), new WoodPanel());
// `anyOf(HatchExpectation, Expectations.block(mc("bars")))` is one `Expectation<OccupantSnapshot>`:
// a hatch-or-bars cell is one occupant focus and two element tests, not two bays.
```

A panel of neither stone nor wood makes both children abstain, so `anyOf` abstains and the engine
reports `Undecided(ABSTAINED)`; a door bay whose second half is not there never reaches `test` — the
focus returns `Missing(SC-FOC-0101)` and the attempt is `Undecided(ONE_SUBJECT_ABSENT)` — while both
halves present with one of them the wrong block gives `Failed` with one two-label finding.
`OakDoorExpectation` binds nothing: the two `OccupantRef`s arrive in `Found.subjects`, which is what
the claim footprint is built from.

### 5.1 The factory step

- `id()` absent, or `create` returning null: `ctx.report(SC-EXP-0010)`, naming the id and the known
  ids; DESIGN ERROR. A non-null result is used as is.
- Bad args: `ctx.report(SC-EXP-0011)` with the offending key as a note and `ctx.range()` as the label;
  null. A factory that reports an error and still returns an instance is dropped.
- Recursion: `ctx.factory(id)` is `@Nullable`; the assembler caps depth (`SC-EXP-0013`).
- Identity: `meta().id` must equal `id()` (`SC-EXP-0012`); anonymous is `SC-EXP-0014` INFO.

No reflection: the frozen catalog is the only name-to-instance path, because a reflected id is
uncheckable at DESIGN and would put a host class name into a kernel decision (I-1, I-8).

## 6. Use cases

**Addon author.** Registers `DoorFactory` with `id() = mymod:door`, ships
`{"expectation": "mymod:door", "wood": "oak"}`; a typo (`"oack"`) surfaces as `SC-EXP-0011` at
`compile` (DESIGN), before any world is read.

**Host.** `/cumulus validate` reports `SC-EXP-0010` for a bay naming an absent addon's factory;
`/cumulus build` where a door bay straddles an unloaded chunk returns `Undecided(UNREADABLE)`.

## 7. Proofs

- **Determinism**: `take` and `test` are pure in `(snapshotId, siting, bay, bindings, budget)` and
  combinators iterate `vararg` arrays by index; a `HashSet` of children breaks it.
- **No binding on non-match**: only `Reply`'s package-private constructor stores bindings.
- **Fold precedence**: both folds are `max` over total orders, so the decisive rows follow from the
  order; thresholds agree because `m+u+a+t < k` leaves no reading that reaches `k`.
- **Read-closure monotonicity**: a check's read scope is a union of cell sets, and adding a child
  cannot shrink it, so the cells and sections the engine charges never decrease when a tree is
  flattened — a flattening cannot make a plan cheaper than the tree it replaces.
- **Door atomicity**: one `Check`, one `Reply`; the subjects a claim is built from are exactly
  `Found.subjects`, so a door cannot claim one half.
- **Absence is not a value**: `Outcome.Missing` never reaches `test`, so `test(null)` is
  unrepresentable and a missing half cannot be read as a mismatch.

## 8. Dependencies and open questions

Depends on U0 `base` (`NamespacedId`, `Budget`, `Stage`), U1 `space` (`Cuboid`, `CuboidSet`, `Locus`,
`Siting`), U2 `occupant` (`WorldView`, `ReadStatus`, `OccupantSnapshot`, `OccupantRef`), U8
`context` (`ContextKey`, `ContextView`, `AggregateContext`), U4 `claim` (`Claim`) and U9 `finding`
(`Finding`, `FindingBundle`, `FindingCode`, `SourceSpan`). U5
owns `Bay` and supplies it to `staticDecisionCells`; U7 charges the check's read closure and then
calls the focus and the expectation.

**OQ-1 — Entity window cost (`22` §16 Q2).** `focus: entity` and `focus: occupant` read an
inflated entity window; if the host adopts a shared spatial index, `Focus.reads(bayCells)` must name an index
instead of a window list, and every shipped focus changes shape.

**OQ-2 — `whenAbsent` strictness (`22` §16 Q3).** A `ContextKey` is `Missing` unless every section of
its region was readable, so a check reading it cannot distinguish "declared absent" from "not read"
in a partially refused region. Whether an optional decorative region may take the declared value is
open, and it decides whether such a check can ever `MATCH`.

Q1, Q4, Q5 and Q6 are not questions for this file: Q1 (empty-volume protection) and Q6
(`Coverage.ANY_SUBJECT` with `EXCLUSIVE` and no `Declared` footprint) belong to `08`/`09`, Q4 (`RevisionMoved` retry) to `16`, Q5
(compact computations) to `10`/`12`.

## 9. Falsification

- **F1**: `allOf(mismatch("bricks"), match())` returns `MATCH`, drops the child's finding, or lets
  `UNREADABLE` outrank the `MISMATCH`.
- **F2**: `anyOf(mismatch(), unreadable())` returns `MISMATCH`; `not(unreadable())` returns anything
  but `UNREADABLE`; `allOf` stops at the first `UNREADABLE` where a later `MISMATCH` was provable.
- **F3**: two `MATCH` children bind one `BindingKey` to unequal values and merge keeps one silently,
  throws, or returns a `MISMATCH` reply that still carries bindings.
- **F4**: a door bay that answers `Missing` still owns one cell, or a `Failed` door bay owns any
  claim at all.
- **F5**: `occupantAt` returns `null` for an air cell; a leaf turns a non-`READABLE` status into
  `MISMATCH`; a missing subject reaches `test(null)` or is reported as silence; a check's run is
  billed without its expectation's declared reads.
- **F6**: a door expectation written against `BayView` compiles, or a `Check` pairs a `BayView`
  expectation with a `door` focus and reaches a world read instead of failing `SC-CHK-0101`.
