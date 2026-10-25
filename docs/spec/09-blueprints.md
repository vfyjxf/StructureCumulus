# 09 — Blueprints and plans (U5)

> When-to-read: declaring a blueprint or a reusable segment, a bay, its coverage and claim policy,
> a check, a seam, an anchor role, an orientation policy or a stage plan — or asking which invalid
> input throws and which becomes a finding. Package `kernel.blueprint`, `@NullMarked`.
> Serves R-04, R-06, R-08, R-09, R-11, R-13; touches I-3, I-4, I-5, I-6, I-10.

## 1. Scope

| Owns | Does not own |
| --- | --- |
| `Bay`, `Requirement`, `Check`, `Coverage`, `Footprint`, `Seam`, `SeamId`, `SeamKind`, `Segment`, `SegmentKey` | a `Focus`/`Expectation` verdict (U3); computation bodies (U8, U11) |
| `AnchorRoles`, `AnchorSpec`, `AnchorResolver`, `OrientationPolicy` | the orientation group and siting (U1); the pose search (U7) |
| `Blueprint`, `BlueprintBuilder`, `DerivationSpec` | `Sharing` conflicts, the ledger (U4) |
| `CheckPlan`, a stage's plan expansion | `ReadScope` (`base`), `Computation` and `StatePhase` (`context`); execution and the fold (U7); codes (U9) |

A blueprint declares no world cell, siting, controller entity or baked anchor offset (R-04, I-5), no
`Deriver` object, no `Finding` or code (R-07), no budget source, clock or token (I-15). A check is a
`Focus`/`Expectation` pair plus its bay; `run` is the kernel's own composition of the two, so a
blueprint still declares no check implementation.

## 2. Vocabulary

| Name | Contract |
| --- | --- |
| `Bay` record | named `Cuboid` + `Coverage` + `Sharing` + `Footprint` + `Requirement` + optional description |
| `Requirement` record | a bay's `Check`s and `Computation`s; the unit list the stage plan expands |
| `Check` record | planned unit: id, bay, `Focus<T>`, `Expectation<T>`, description |
| `Coverage` enum | `EVERY_CELL`, `ONE_SUBJECT`, `ANY_SUBJECT` — what a MATCH asserts about the bay |
| `Footprint` sealed interface | `Box`, `Bound`, `Declared`, `None` — what a MATCH owns |
| `Seam` record | connectable edge: cells + kind + properties; never tested, never claimed |
| `SeamId` record | `(NamespacedId)` local to its blueprint; nominal, never a bay id |
| `SeamKind` enum | `ITEM`, `FLUID`, `ENERGY`, `STRUCTURAL`, `CUSTOM` |
| `Segment` record | key, bounds, bays, seams, anchors; no policy, plans or claims |
| `SegmentKey` record | `(id, Arguments)`; identity includes the arguments |
| `AnchorRoles` final class | `PRIMARY`/`FRONT`/`CENTER` as `NamespacedId`s; a role is never a type of its own |
| `AnchorSpec` record | `(role, property, value)`; read at the definition origin, one cell |
| `AnchorResolver` interface | `resolve(blueprint, origin, view) → Outcome<Siting>`; one property read, one mapping |
| `OrientationPolicy` record | the allowed orientations, the role that supplies the heading, the upright promise |
| `Blueprint` final class | compiled template; one construction path (§4.6) |
| `BlueprintBuilder` final class | fluent, mutable, not thread-safe; validates once at `build()` |
| `DerivationSpec` record | `(deriver, Arguments, output, order)`; a reference, not an object |
| `CheckPlan` record | a stage's expansion: section computations, aggregate computations, derivations, checks (§4.8) |

## 3. API design

```java
public record Bay(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                  Sharing sharing, Requirement requirement,
                  @Nullable String description) {           // empty cells refused
    public static Bay of(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                         Sharing sharing, @Nullable String description,
                         Function<Bay, Requirement> units);  // the check/bay knot, one pass
}
public record Requirement(List<Check<?>> checks, List<Computation<?>> computations) {}

public enum Coverage { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }
public sealed interface Footprint permits Box, Bound, Declared, None {
    record Box() implements Footprint {}                     // the bay cuboid, air included
    record Bound() implements Footprint {}                   // union of the non-air bound footprints
    record Declared(CuboidSet cells) implements Footprint {} // exactly these cells
    record None() implements Footprint {}                    // nothing
}

public record Check<T>(NamespacedId id, NamespacedId bay, Focus<T> focus,
                       Expectation<T> expectation, @Nullable String description) {
    public Check {
        if (!focus.subject().equals(expectation.subject()))
            throw new IllegalArgumentException("SC-CHK-0101: subject mismatch");
        Objects.requireNonNull(bay, "SC-CHK-0107: a check belongs to exactly one bay");
    }

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
public record SeamId(NamespacedId id) implements Comparable<SeamId> {}
public enum SeamKind { ITEM, FLUID, ENERGY, STRUCTURAL, CUSTOM }
public record Seam(SeamId id, Cuboid cells, SeamKind kind, Map<String, String> properties) {
    public static final String KIND = "kind";               // reserved key, CUSTOM only
    public boolean compatibleWith(Seam o) { return kind == o.kind   // the kernel's only such rule
            && Objects.equals(properties.get(KIND), o.properties.get(KIND)); }
}
public record SegmentKey(NamespacedId id, Arguments arguments) {}
public record Segment(SegmentKey key, Cuboid bounds, List<Bay> bays, List<Seam> seams,
                      List<AnchorSpec> anchors) {}          // checks B-1..B-4 locally; qualify(alias, local)
public final class AnchorRoles {                        // role ids, not a wrapper type
    public static final NamespacedId PRIMARY = NamespacedId.of("cumulus", "primary");
    public static final NamespacedId FRONT = NamespacedId.of("cumulus", "front");
    public static final NamespacedId CENTER = NamespacedId.of("cumulus", "center");
}
public record AnchorSpec(NamespacedId role, NamespacedId property, String value) {}   // cell = origin
public interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);   // §4.5
}
public record OrientationPolicy(NamespacedId primaryRole, List<Orientation> allowed,
                                boolean uprightOnly) {
    public static Builder builder(NamespacedId primaryRole);   // allow | allowTurns | uprightOnly
    public @Nullable Orientation select(Heading anchorHeading);              // §4.5, one equation
}
public final class Blueprint {                  // private ctor; builder() is the only construction path
    public NamespacedId id();  public Cuboid bounds();
    public List<Bay> bays();  public List<Seam> seams();          // declaration order
    public List<AnchorSpec> anchors();  public List<Overlap> overlaps();
    public OrientationPolicy orientation();
    public List<DerivationSpec> derivations();
    public Arguments arguments();  public @Nullable SourceSpan provenance();
    public @Nullable Bay bay(NamespacedId id);  public @Nullable Seam seam(SeamId id);
    public @Nullable AnchorSpec anchor(NamespacedId role);
    public CheckPlan expand(Stage s);                           // §4.8, pure
    public @Nullable SourceSpan sourceOf(NamespacedId bayOrSeam);   // per-element authoring range
    public CuboidSet bayCells();                                // cached union, excluded from equals
    public record Overlap(NamespacedId low, NamespacedId high) implements Comparable<Overlap> {}
}
public final class BlueprintBuilder {   // every member returns the builder
    public BlueprintBuilder bounds(Cuboid b);
    public BlueprintBuilder bay(NamespacedId id, Cuboid c, Coverage cov, Footprint footprint, Sharing sharing);
    public BlueprintBuilder bay(NamespacedId id, Cuboid c, Coverage cov, Footprint footprint,
                                Sharing sharing, @Nullable String d);
    public BlueprintBuilder bay(String path, Cuboid c, Coverage cov, Footprint footprint, Sharing sharing);
    public <T> BlueprintBuilder check(NamespacedId bay, NamespacedId id, Focus<T> focus,
                                      Expectation<T> expectation,
                                      @Nullable String description);   // appends to that bay's Requirement
    public BlueprintBuilder compute(NamespacedId bay, Computation<?> computation);
    public BlueprintBuilder seam(SeamId id, Cuboid c, SeamKind k);          // + properties overload
    public BlueprintBuilder anchor(AnchorSpec s);
    public BlueprintBuilder orientation(OrientationPolicy p);  public BlueprintBuilder derive(DerivationSpec d);
    public BlueprintBuilder overlap(NamespacedId a, NamespacedId b);        // §4.2
    public BlueprintBuilder place(Segment s, LatticePoint offset, NamespacedId alias);  // §4.4
    public BlueprintBuilder arguments(Arguments a);  public BlueprintBuilder provenance(SourceSpan p);
    public BlueprintBuilder source(NamespacedId bayOrSeam, SourceSpan p);   // per-element range
    public Blueprint build();
}
public record DerivationSpec(NamespacedId deriver, Arguments args, NamespacedId output,
                             int order) {}                 // order >= 0, unique
public record CheckPlan(Stage stage, List<Computation<?>> computations,
                        List<DerivationSpec> derivations, List<Check<?>> checks) {}   // §4.8
```

`allowed` non-empty, duplicate-free, in `Orientation.all()` order; `uprightOnly` promises every allowed
`o` maps `Heading.UP` to `UP` (checked at build, not a filter); `select` ⇒ `null` means "no admissible
orientation", never identity, never `allowed.get(0)`. One spec per anchor role: a role names the one
property the definition origin must show (§4.5), so there is no alternative site to rank and no
priority to tie. The stage plan is a pure function of the blueprint: `expand(S)` is recomputed, never
stored, so equal blueprints cannot disagree about their plan. A bay's `cells` is non-empty,
its declaration obeys rules R1 and R4 of §4.1, and its `requirement` is non-empty — a bay
that asserts nothing is a seam (§4.3). `Bay` and `Check` reference each other; `Bay.of` closes the
knot in one pass — it hands the author a bay value to hang checks on, and the checks remember that
declaration value. `bay` is compared and hashed by `id`, `Requirement` by the checks' ids, so no
equality chain recurses (§7.1).

## 4. Semantics

### 4.1 Why `Bay`, not `Slot`, and what a bay asserts

`net.minecraft.world.inventory.Slot` is what the adapter is likeliest to import, and
`01-glossary.md` §O bans the name. Beyond the collision, "slot" is one place in a container while a
bay is a region tested **atomically** (the door case, R-06, spans cells), and "slot" implies something
is put in while a bay is *asserted about* — a check inspects and stores nothing (I-4). Ids are
`NamespacedId`s local to their blueprint: `mouth` in two blueprints is two bays, and
`(world, owner, siting, bay)` (U4) draws the world-level distinction.

A bay states two independent things: what a MATCH **asserts** (`Coverage`) and what it **owns**
(`Sharing` + `Footprint`). Neither implies the other.

| `Coverage` | What a match must establish |
| --- | --- |
| `EVERY_CELL` | every cell of `bay.cells` is decided by at least one check |
| `ONE_SUBJECT` | `bay.cells` equals the union of the bound subjects' footprints |
| `ANY_SUBJECT` | no cell is asserted; a presence or state criterion |

The two axes are independent, so only genuine contradictions need rules. Four of them, and
nothing else:

| Rule | When | Statement | Code |
| --- | --- | --- | --- |
| R1 | build | `EXCLUSIVE` needs a `Footprint` other than `None`: a claim that excludes others but owns nothing contradicts itself | `SC-CHK-0106` |
| R2 | settle | `EXCLUSIVE` + `Bound` must resolve to at least one cell; a door that never bound cannot settle as an empty exclusive claim | `SC-BAY-0111` |
| R3 | build + settle | `ONE_SUBJECT` needs subject-kind checks, and at SETTLE the bound footprint must equal `bay.cells` | `SC-BAY-0107` |
| R4 | build | `EVERY_CELL` needs per-cell checks covering `bay.cells`; `ANY_SUBJECT` forbids per-cell checks | `SC-CHK-0106` |

Three combinations worth keeping in view:

- `EVERY_CELL` + `Bound`: every cell is asserted, only the non-air ones are owned — the pipe corner.
- `EVERY_CELL` + `Box`: an empty interior is protected — the furnace cavity, 100 air cells.
- `ONE_SUBJECT` + `Bound`: the door case, where the bay *is* the two halves — R3 makes the resolved
  bound footprint equal `bay.cells`, so the bay owns the halves and never the air around them.

A non-`None` bay's `reads()` must stay inside `bay.cells` ∪ its declared footprint, else
`SC-BAY-0108` at SETTLE; a bound entity whose footprint leaves `bay.cells` is `SC-BAY-0109` at
SETTLE; an entity type wider than `MAX_ENTITY_SPAN` is `SC-BAY-0110` at DESIGN. Claims protect what a
structure occupies or declares, never what it merely reads (§10.2 of `22`).

### 4.2 Overlap

Two intersecting bays are a DESIGN error unless both are `SHARED` **and** the pair is declared with
`overlap(a, b)`.

| Pair | Result |
| --- | --- |
| both `SHARED`, intersect, declared | legal; those cells are checked and charged twice (R-08) |
| intersect, undeclared — or declared with one bay not `SHARED` | DESIGN error: a declaration cannot license an exclusive claim |
| disjoint but declared | DESIGN error: a dead declaration is a typo signal |

Three bays over one cell need all three pairs declared: a shared *cell* without a declaration is a
modelling error.

### 4.3 A seam is not a bay

A bay carries a `Requirement` and its claims, can fail a match (`MISMATCH` or `UNREADABLE`) and is
subject to §4.2. A seam carries neither, produces no claim — its cells lie inside bay cells (B-3), so
they are already claimed — can only be `UNREADABLE`, and is exempt from §4.2: seams may overlap bays
and each other freely.

Binding has three sites; only the first is in the compile path. **Assembly**: U6's assembler calls
`compatibleWith` on two named seams of one blueprint (equal kind, adjacent cells ⇒ a paired seam, else
a DESIGN finding). **Match**: a check of the bay that holds the seam records the occupants over it as
counterparts — a check always names one bay (`SC-CHK-0107`), so a seam is judged through the bay under
it, and an unreadable counterpart becomes that check's `UNREADABLE`. **Join**: two formed structures —
the host calls `compatibleWith` itself; no kernel state, no claim movement. The kernel adds no rule
beyond `compatibleWith` and never fails a match because a counterpart "looks wrong": judging an
occupant needs an `Expectation`, which a seam lacks by design.

### 4.4 Instantiating a segment

`place(segment, offset, alias)` is pure and applies immediately: `bay.cells` and `seam.cells` are
translated by `offset`; `bay.id` and `seam.id` are re-homed through
`Segment.qualify(alias, local) = NamespacedId.of(alias.namespace(), alias.path() + "/" + local.path())`.
A bay's checks travel with it: each is re-pointed at the placed bay — its `bay` field and its own id
both come from the placement, because a check id must stay unique across the blueprint (B-2) and a
check that kept the segment's bay would read untranslated cells. An **anchor spec is neither
translated nor qualified**: a spec names a property of the definition origin (§4.5), which the
placement does not move, and roles are unique across the blueprint (B-4) — two placements of one
segment both declaring `CENTER` are refused, because one cell is the origin and only one role
supplies the heading. `segment.bounds` is not added to the blueprint's bounds: the declared bounds
must already hold every placed cell (B-8), since growing them would move the footprint under the
author. `Focus`es and `Expectation`s are shared, not copied (immutable, re-entrant, U3), so one
segment value is placed many times; a placement's provenance is its alias.

`qualify` is injective because an alias path may not contain `/` (`place` checks it): the first `/`
splits at a unique point. The local namespace is discarded on purpose — a bay id is local to *its*
blueprint, and two placements of `mymod:shell` must not both yield `mymod:floor`.

### 4.5 Anchor resolution

```java
public interface AnchorResolver {
    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
}
```

1. The anchor cell is `origin`, the **definition origin**, supplied by the caller — a command, a
   watch, or a document placement. No blueprint member names a world cell (B-12, I-5), so the read
   cannot depend on the orientation being chosen: the circularity is gone, not avoided.
2. Read exactly one cell, `view.occupant(LatticeKey.of(origin))`. An absent cell is `Missing` with
   `SC-FOC-0101`, a refused section `SC-FOC-0301`; neither is a fallback heading.
3. Check `spec.property()` against `spec.value()` on that occupant. A mismatch is an unresolved
   role: `Missing`, reported by U7 (a disagreement is `Failed`); never `Heading.NORTH` by default.
4. `anchorHeading` is the `Heading` that property yields. A candidate orientation `o` is admissible
   iff `o.apply(authoredFront) == anchorHeading`; a candidate that does not satisfy it is not a
   candidate. The authored front is the local `Heading.NORTH` — one constant, so no front field can
   drift.
5. `OrientationPolicy.select(anchorHeading)` scans `allowed` in `Orientation.all()` order and returns
   the first admissible candidate, `null` when none is. It is a filter on one equation, never a
   preference among orientations: an orientation that does not satisfy the equation cannot win on
   declaration order (§7.5).
6. `null` ⇒ no siting. U7 reports it (`Failed` on disagreement, `Failed` or `Undecided` on an
   unresolved role, per its table); this unit guarantees only that absence stays absence (I-5).
7. `uprightOnly` removes nothing at match time — it was checked against `allowed` at build.

The anchor read is charged one cell and one section. `AnchorResolver` holds no world state: the view
is an argument, so the blueprint value stays free of a `WorldView` field.

### 4.6 Blueprint invariants

| Id | Invariant | Enforced by |
| --- | --- | --- |
| B-1 | `bounds` non-empty; every bay non-empty and inside it | `build()` |
| B-2 | bay ids, seam ids and check ids unique | `build()` |
| B-3 | seam cells inside `⋃ bay.cells` | `build()` |
| B-4 | anchor roles unique; the policy's `primaryRole` has a spec | `build()` |
| B-5 | every check names a bay declared in the blueprint (`SC-CHK-0107`) | `expand` |
| B-6 | derivation `output` keys unique; `order` unique and non-negative | `build()` |
| B-7 | §4.2 holds in both directions (`SC-BPT-0002`) | `build()` |
| B-8 | an alias has no `/` (`place`); placed cells inside `bounds` (`build`) | `place`, `build` |
| B-9 | the declaration obeys R1 and R4 (`SC-CHK-0106`), and a `ONE_SUBJECT` bay's checks are subject-kind (R3's build half, `SC-BAY-0107`) | `build()` |
| B-10 | a `EVERY_CELL` bay's static decision cells cover `bay.cells` (`SC-CHK-0106`) | `build()` |
| B-11 | a check never reads a `ContextKey` produced at a later stage (`SC-CHK-0109`) | `expand` |
| B-12 | no member is a world cell, siting, entity, finding, clock or token | the type |

`Blueprint` is a `final class` with a **private** constructor, not a record: a record's canonical
constructor cannot be more restrictive than the record, so a hand-written `new Blueprint(...)` would
be a second, unvalidated path (§7.1). The value is immutable — private final fields, collections copied
once, the plan recomputed by `expand(S)`, no setter, no `with`; the only mutable objects reachable are
`Focus`es and `Expectation`s, shared and re-entrant by U3 contract. `bayCells()` and `provenance` are
excluded from `equals` (derived; source, not content).

### 4.7 What throws, what becomes a finding

`build()` throws `IllegalArgumentException` for exactly the `build()` rows of §4.6 (B-1…B-4, B-6…B-10)
and the `allowed` policy precondition (non-empty, duplicate-free), nothing else; B-5 and B-11 are
checked by `expand(S)`, the first place a check meets its bay, and B-12 is held by the types. A `build()` caller is
code: there is no document position to hang a `Label` on, and a silently repaired blueprint would move
the footprint under every later finding. So `build()` is a **proof obligation, not a validator** — it
yields a value satisfying §4.6 or throws with the row id and the offending id (`"B-2: check
mymod:mouth_bars is declared twice"`).

Document data never reaches a failing `build()`: U6's `DocumentValidator` runs the same table first and
reports `Finding`s with `Locus` and `SourceSpan` (R-07, `10-documents.md`). §4.6 is a **shared
contract** — one row, two presentations, and the row id is how a reviewer matches them.

**Normalisation is allowed, repair is not.** Copying collections, sorting `allowed`, canonicalising
`Overlap` order and densifying derivation order are normalisations; clamping a
`Cuboid`, dropping an empty bay, adding a missing primary anchor or reordering a stage plan are
refused. **`String` ids are sugar only**: `bay("mouth", …)` is
`bay(NamespacedId.of(id.namespace(), "mouth"), …)` — a path inside the blueprint's namespace, the one
`String` channel `04-api-doctrine.md` §3.3 permits.

### 4.8 Plans and expansion

A stage's plan is not a list of steps. The plan is the **expansion** of the blueprint's declared units
for one stage, and `CheckPlan` is that expansion — a value, not a configuration. The engine selects
the stage and computes the order; an author declares neither a stage nor a unit order.

```java
public record CheckPlan(Stage stage, List<Computation<?>> computations,
                        List<DerivationSpec> derivations, List<Check<?>> checks) {}
```

For stage `S`, `Blueprint.expand(S)` (pure) yields, in this order:

1. every `Computation` of `S` whose `phase()` is `StatePhase.SECTION`;
2. the barrier;
3. every `Computation` of `S` whose `phase()` is `StatePhase.AGGREGATE`, and every `DerivationSpec`
   assigned to `S`, in Kahn order over `inputs()`/`requires()` — a cycle is `SC-CHK-0102`;
4. every `Check` of `S`, after the computations it reads;
5. ties within 1, 3 and 4: declaration order — bays in declaration order, then their `Requirement`
   order; `specificity` remains a finding-order hint, never a plan key.

A `SECTION` unit contributes through `ContextDraft.contribute`, on a key whose `merge` is non-null;
an `AGGREGATE` unit puts a single value with `put`, reading what the barrier folded. A check never
runs before the barrier; there is no per-section check phase, so a chunk boundary
cannot change a verdict. `pinned` is not a knob: the structural order (1 → 2 → 3 → 4) and the Kahn
constraints already fix the plan, and the declaration order inside a tie is the only freedom left.
Budgets only tighten — `min` at every hop — so a plan without a cap inherits the request.

Checks are independent, so no `FIRST`/`AGGREGATE` knob exists: U7 folds the attempt's replies
`UNREADABLE > MISMATCH > ABSTAIN > MATCH`. Nothing in a plan releases a claim (I-10).

`DerivationSpec` is a **reference**: deriver id, `Arguments`, the `output` key id and its `order` — no
`Deriver` object lives in a blueprint, the cycle break keeping `blueprint` off
`context` (`02-architecture.md` §2). The id resolves through `Catalogs.derivers` when U8 compiles the
plan: the `Deriver`'s `output()` is the `ContextKey` the spec names, and a missing id is a finding
there. `order` is unique and non-negative, and U8 sorts `(order, id)` inside the
plan, so the plan is not a source-order accident. Two specs for one `output` key are refused (B-6):
a key has one producer.
`Sharing` and `Footprint` are values on the bay, not a ladder: `Sharing` names two semantics,
not two levels, so a `SHARED` and an `EXCLUSIVE` bay on one cell is U4's conflict rule and, in the
end, the ledger's.

## 5. Reference sketch

```java
static NamespacedId id(String path) { return NamespacedId.of("mymod", path); }
static NamespacedId mc(String path) { return NamespacedId.of("minecraft", path); }

// Huge furnace: shell, door bay, owned cavity; a seam; an anchor; a section computation; a derivation.
Blueprint furnace = Blueprint.builder(id("huge_furnace"))
        .bounds(Cuboid.of(0, 0, 0, 7, 6, 7))
        .bay(id("wall"), Cuboid.of(0, 0, 6, 7, 6, 7), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Bound())
        .bay(id("mouth"), Cuboid.of(3, 1, 0, 4, 3, 1), Coverage.ONE_SUBJECT,
                Sharing.EXCLUSIVE, new Footprint.Bound(), "the opening the player sees")
        .bay(id("interior"), Cuboid.of(1, 1, 1, 6, 5, 6), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Box())          // the empty volume is protected
        .check(id("wall"), id("wall_bricks"), Foci.block(Cuboid.of(0, 0, 6, 7, 6, 7)),
                Expectations.block(mc("bricks")), null)
        .check(id("mouth"), id("mouth_door"), Foci.door(LatticePoint.of(3, 1, 0)),
                Expectations.door(mc("iron_door")), null)             // ONE_SUBJECT: two cells, one claim
        .check(id("interior"), id("interior_air"), Foci.allCells(), Expectations.air(),
                null)
        .seam(new SeamId(id("mouth_item")), Cuboid.of(3, 1, 0, 4, 2, 1), SeamKind.ITEM)
        .anchor(new AnchorSpec(AnchorRoles.FRONT, mc("facing"), "east"))  // read at the definition origin
        .orientation(OrientationPolicy.builder(AnchorRoles.FRONT)
                .allowTurns(Turn.NONE, Turn.CLOCKWISE, Turn.HALF, Turn.COUNTERCLOCKWISE)
                .uprightOnly().build())
        .compute(id("interior"), Computations.volume(id("interior_volume")))
        .derive(new DerivationSpec(id("capacity"), Arguments.empty(), id("capacity"), 0))
        .build();   // bayCells() 42 + 2 + 100 = 144; expand(FORM) = interior_volume, barrier,
                    // then wall (42), mouth (2), interior (100) — declaration order
```

A pipe corner is one bay, not an `allOf` workaround: eight cells asserted, four owned.

```java
var corner = Blueprint.builder(id("pipe_corner")).bounds(Cuboid.of(0, 0, 0, 2, 2, 2))
        .bay(id("corner"), Cuboid.of(0, 0, 0, 2, 2, 2), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Bound());
for (LatticePoint p : List.of(LatticePoint.of(0, 0, 0), LatticePoint.of(1, 0, 0),
                              LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 1)))
    corner.check(id("corner"), id("pipe_" + p.x() + p.y() + p.z()),
            Foci.block(Cuboid.of(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1)),
            Expectations.block(mc("pipe")), 1, null);
for (LatticePoint p : List.of(LatticePoint.of(0, 1, 0), LatticePoint.of(1, 1, 0),
                              LatticePoint.of(0, 1, 1), LatticePoint.of(1, 1, 1)))
    corner.check(id("corner"), id("clear_" + p.x() + p.y() + p.z()),
            Foci.block(Cuboid.of(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1)),
            Expectations.air(), 1, null);
Blueprint pipeCorner = corner.build();
// The corner claims its four pipe cells; a neighbour's corner claims its own four, and the four
// must-be-air cells are asserted by both and owned by neither.
```

A parameterised shell segment (`HEIGHT` a `ParameterKey<Integer>`), placed twice:

```java
Segment shell = new Segment(
        new SegmentKey(id("shell"), Arguments.of(Keys.HEIGHT, 3)),
        Cuboid.of(0, 0, 0, 5, 3, 5),
        List.of(Bay.of(id("floor"), Cuboid.of(0, 0, 0, 5, 1, 5), Coverage.EVERY_CELL,
                        Sharing.SHARED, new Footprint.Bound(), null,
                        b -> new Requirement(List.of(new Check<>(id("floor_bricks"), b,
                                Foci.block(Cuboid.of(0, 0, 0, 5, 1, 5)),
                                Expectations.block(mc("bricks")), 25, null)), List.of())),
                Bay.of(id("hollow"), Cuboid.of(1, 1, 1, 4, 3, 4), Coverage.EVERY_CELL,
                        Sharing.SHARED, new Footprint.Bound(), null,
                        b -> new Requirement(List.of(new Check<>(id("hollow_air"), b,
                                Foci.allCells(), Expectations.air(), 36, null)), List.of()))),
        List.of(new Seam(new SeamId(id("hatch")), Cuboid.of(2, 1, 1, 3, 2, 2), SeamKind.ITEM,
                Map.of())),
        List.of(new AnchorSpec(AnchorRoles.CENTER, mc("facing"), "north")));

Blueprint kiln = Blueprint.builder(id("kiln"))
        .bounds(Cuboid.of(0, 0, 0, 11, 3, 5))                       // holds both placements
        .place(shell, LatticePoint.of(0, 0, 0), id("south"))
        .place(shell, LatticePoint.of(6, 0, 0), id("north"))
        .orientation(OrientationPolicy.builder(AnchorRoles.CENTER).allowTurns(Turn.NONE).build())
        .build();     // bays mymod:south/floor, mymod:south/hollow, mymod:north/floor, …
```

Two `EXCLUSIVE` bays over one cell are refused by `build()`:

```java
assertThrows(IllegalArgumentException.class, () -> Blueprint.builder(id("gate"))
        .bounds(Cuboid.of(0, 0, 0, 4, 4, 4))
        .bay(id("door"),   Cuboid.of(0, 0, 0, 4, 2, 1), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Box())
        .bay(id("lintel"), Cuboid.of(0, 1, 0, 4, 3, 1), Coverage.EVERY_CELL,
                Sharing.EXCLUSIVE, new Footprint.Box())
        .check(id("door"), id("door_planks"), Foci.block(Cuboid.of(0, 0, 0, 4, 2, 1)),
                Expectations.block(mc("oak_planks")), 8, null)
        .check(id("lintel"), id("lintel_planks"),
                Foci.block(Cuboid.of(0, 1, 0, 4, 3, 1)), Expectations.block(mc("oak_planks")), 8, null)
        .build());    // B-7: bays lintel and door overlap at x[0,4) y[1,2) z[0,1)
```

## 6. Use cases

**Addon author.** A door: one bay over the two-cell opening, one check pairing a `door` focus with a
door expectation (R-06), `Coverage.ONE_SUBJECT` with `Footprint.Bound` so the two cells are asserted
and owned, and `EXCLUSIVE` so a second door cannot claim them. A pipe segment: `FLUID` seams on two
faces, no bay, no claim — the machine next door declares a matching seam and U6 pairs them at
assembly. The addon writes a `SeamKind`, or a `CUSTOM` kind when it needs a discriminator, never a
compatibility predicate.

**Host.** A datapack reload compiles documents through `BlueprintBuilder`; `/cumulus validate` reports
§4.7's DESIGN findings before anything is matched. A build wand reads `bayCells()` for the footprint;
it never reads `expand(...)`, which is engine input.

## 7. Proofs

**7.1 Immutability.** No public constructor, no setter, no `with`; fields `private final`; collections
copied once; `expand(S)` recomputes its plan on every call instead of caching one (`Map.copyOf` would
disprove the determinism claim, so no `Stage`-keyed map is stored at all). The only reachable mutable state is a
`Focus` or an `Expectation`, shared and re-entrant by contract, so no match observes a half-updated
template (I-4). *Hunted counterexample:* `bayCells()` is a cache — excluded from `equals`, never
returned mutable, and `CuboidSet` is immutable by U1; and a `Bay` that contains a `Check` that names
that `Bay` would make a naive record `equals` recurse, so `Check` compares and hashes `bay.id()` and
`Requirement` compares the checks' ids.

**7.2 Builder validation completeness.** Claim: `build()` throws iff one of §4.6's `build()` rows or the
`allowed` precondition fails, and `expand(S)` throws iff B-5 or B-11 fails or the computation DAG has a
cycle (`SC-CHK-0102`). (⇒) each row
has one check, run against the finished state and reading only built values, so call order cannot hide
a violation and no check masks another. (⇐) every `throw` in `build()` carries its row and every row is
in §4.6; a throw without a row is a review defect. Idempotent: `build()` copies without consuming the
builder, so two builds are equal.

**7.3 No package cycle.** Rank `base` 0, `space` 1, `claim` 3, `expectation` 4, `blueprint`
5. `blueprint` consumes the other four. There is no `plan` package: `CheckPlan` is a `blueprint` value,
`ReadScope` is `base`, and `Computation`/`StatePhase` are `context`. The edge that matters holds:
**`base` names no `Bay`, no `Blueprint` and no `Check`.** A check must name its bay (`SC-CHK-0107`), so
`Check`, `Requirement` and `Bay` live in `blueprint` and never below it. Nor does
the anchor edge close: `AnchorResolver` names `Blueprint` in `blueprint` and `WorldView` from `space`,
never the reverse. Hence `blueprint ⇄ base` and `blueprint ⇄ context` cannot form (`DerivationSpec`
holds an id, `Arguments`, a key id and an `int`, never a `Deriver`). *The provenance edge is
closed:* `Blueprint` and `BlueprintBuilder` use the `SourceSpan` value from `base`, not U6's
`DocumentProvenance`, so `blueprint` never imports `document`.

**7.4 Expansion total order.** Within a stage, steps 1 and 2 of §4.8 are fixed; step 3 is Kahn order
over `inputs()`, a DAG by `SC-CHK-0102`; step 4 follows every computation it reads. Each group's tie key
is the declaration index — unique by position — so each group is strictly totally ordered and
stability makes the implementation agree with the key. The key reads one position and no hash and no
identity, so equal blueprints expand identically in every JVM.

**7.5 Orientation determinism.** `select` walks a duplicate-free list in a total canonical order and
returns the first orientation that satisfies `o.apply(authoredFront) == anchorHeading`: at most one
result per call, independent of hash seed or thread. The no-candidate case returns `null` with no
identity branch,
so "no admissible pose" cannot decay into "pose at the origin" (I-5).

## 8. Dependencies and open questions

Uses `base` (`NamespacedId`, `Arguments`, `ParameterKey`, `Stage`, `Budget`), `space` (`Cuboid`,
`CuboidSet`, `LatticePoint`, `Orientation`, `Heading`, `Siting`, `WorldView`; `Turn`/`Tilt`/`Flip` in
`allowTurns` only), `base` (`ReadScope`), `context` (`Computation`, `StatePhase`), `occupant` (`EntityWindow`),
`expectation` (`Focus`, `Expectation`, `Outcome`, `Reply`, `CheckContext`), `claim` (`Sharing`).
`LatticePoint` and the turn types go beyond the brief's reference list because an offset and the turn
sugar have no other type. `AnchorResolver` is the one member that reads a `WorldView`; the view is an
argument, so the value stays host-free (I-5).

**Assumed, to be confirmed:** U0 — `Arguments` has order-free value equality (else `SegmentKey` and
`DerivationSpec` identity are wrong), `Arguments.of(ParameterKey<T>, T)`, `Budget.min`. U1 —
`Cuboid.isEmpty`, `Cuboid.intersects`, `Cuboid.translate(int,int,int)`, `LatticePoint.x()/y()/z()`,
`Orientation.of(Turn,Tilt,Flip)`, the `Orientation.all()` order. U3 — `Foci` and `Expectations`
holders, `Focus.reads()`/`reads().union`, `ExpectationMeta.specificity()`. U8 — `Computations`
holder, one `Computation` with `StatePhase` and its `compute(...)` context, `ContextKey`. U9 owns
every code; this file names finding *sites* only (`SC-BPT-…`).

- **Q5-1 (resolved)** `Blueprint` stores a `@Nullable SourceSpan` (from `base`) and exposes
  `sourceOf(...)`; `DocumentProvenance` stays U6-local. No `blueprint → document` edge.
- **Q5-2 (resolved)** `Blueprint.overlaps()` has a glossary row; the declaration lives on the
  blueprint, not on a claim spec.
- **Q5-3** Empty-volume protection (`22` §16 Q1): the furnace cavity declares `Box` and the residue
  is a `SC-CLM-0104` WARNING — is that enough, or does a blueprint need one `sealed: true` flag?
- **Q5-4** A `ANY_SUBJECT` bay asserts no cells (`22` §16 Q6): can it ever be `EXCLUSIVE` without the
  claim owning nothing? R1–R4 do not answer it: R1 forbids only a `None` footprint.
- **Q5-5** Entity windows (`22` §16 Q2): a bay's `EntityWindow` is the declared box inflated by
  `MAX_ENTITY_SPAN`; a per-tick diff over many windows is the host's cost, and this file does not
  bound how many windows one blueprint may declare.

## 9. Falsification

| # | Case | Expected | Breaks if not |
| --- | --- | --- | --- |
| F-1 | `EXCLUSIVE` with a `None` footprint (R1) | `build()` throws B-9 naming `SC-CHK-0106`; a document yields a DESIGN `ERROR` | a claim that excludes others but owns nothing contradicts itself (R1) |
| F-2 | `new Check<>(…, null /* bay */, …)`, or a bay with no unit | `IllegalArgumentException` naming `SC-CHK-0107`; a document yields the DESIGN twin | a structure-level check has no bay to own its claim |
| F-3 | `OrientationPolicy.builder(FRONT).build()` with no `FRONT` spec, or a duplicated `allowed` | `build()` throws B-4; empty is never "any orientation" | the heading has no declared source, defeating R-05 |
| F-4 | two `EXCLUSIVE` bays intersecting (§5's `gate`) | `build()` throws B-7 naming both and the intersection | conflicts reach U4 that design time could have reported (I-10) |
| F-5 | a seam outside every bay; `place` with `/` in the alias | `build()` throws B-3; `place` throws B-8 | seams describe unchecked cells; `qualify` stops being injective |
| F-6 | one blueprint built twice; two from a canonical document | equal values, equal `expand(...)` order | §7.4 false; `MatchResult` not reproducible |
| F-7 | a `EVERY_CELL` bay whose checks leave one cell undecided; an entity focus in a `EVERY_CELL` bay | `build()` throws B-10 and B-9, naming `SC-CHK-0106` | "every cell is decided" would be an unchecked promise (R-06) |
| F-8 | a `FORM` check reading a key produced at `SETTLE` | `expand(FORM)` throws B-11 naming `SC-CHK-0109` | the check would read a later stage's value and never see it |
| F-9 | two computations in one stage producing the same key | `build()` refuses it (B-6), never silently keeps one | the plan order would depend on declaration accident |
| F-10 | a segment placed twice, then `expand(FORM)` | each check reads its own placement's cells; ids distinct | a check would judge untranslated cells (§4.4) |

Review question: does any §3 member let a caller reach state §4.6 does not describe, or does a §4.6 row
lack both a throw site and a U6 finding? Either "yes" fails this module.
