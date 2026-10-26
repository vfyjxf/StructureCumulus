# 04 — API doctrine

> The kernel is complex inside and small outside (R-14). This file is the contract that keeps
> it that way: what the three audiences see, how composition works, how types stay honest,
> and what a reference sketch must prove.

## 1. Three audiences, three surfaces

| Audience | Sees | Does not see |
| --- | --- | --- |
| **First-use caller** | `Blueprint` builder, `StructureKernel.check`/`settle`, sealed `MatchResult`, `StructureContext`, `FindingBundle` | prescreen indexes, sessions, step ordering, claim reservations, transaction internals |
| **Addon author** | `Expectation` + `Expectations`, `Deriver`, `RemedyProvider`, typed keys, `DataView` | engine internals, the matcher loop, the finding bundle's dedup algorithm |
| **Host / adapter author** | `WorldCapture`, `WorldView`, `ClaimLedger`, `EditExecutor`, `DataBridge`, catalogs | how a match walks a blueprint |

A public type that only the engine needs is package-private. A type that an addon needs but
a first-use caller never touches lives in its unit package and is documented as such.

## 2. Composition, not configuration

Every place where a user might want "one of several behaviours", the API offers a value that
composes with the same kind of value.

- **Expectations** compose: every combinator takes `Expectation` and returns `Expectation`.
  There is no `ExpectationBuilder` hierarchy and no `PredicateType` enum.
- **Segments** compose: a `Segment` is a value; a `Blueprint` is an assembly of segments.
- **Derivations** compose: a derivation set is an ordered list of `Deriver` nodes; one may read
  values produced earlier in the same stage.
- **Remedies** compose: `RemedyProvider` returns a list; a repair may contain a nested edit
  plan; a preview is an independent projection of the same finding.
- **Findings** compose: a bundle is a filtered/ordered set; a related finding is an id, not a
  nested object graph.

Configuration objects (`MatchPolicy`, `FindingOptions`, `OrientationPolicy`) exist only for
knobs that are *not* compositional: caps, budgets, allowed orientation sets.

## 3. Type safety

### 3.1 Typed keys

Any value that crosses a boundary as "a named thing of some type" uses a typed key:

```java
record ContextKey<T>(NamespacedId id, Class<T> type,
                     Retention retention, @Nullable Merge<T> merge, @Nullable T whenAbsent) {}
record TraitKey<T>(NamespacedId id, Class<T> type) {}
record BindingKey<T>(NamespacedId id, Class<T> type) {}
record ParameterKey<T>(NamespacedId id, Class<T> type) {}
```

The `Class<T>` token is not decoration. It lets the heterogeneous map fail at the boundary
with a precise finding instead of a `ClassCastException` three frames later:

```java
<T> @Nullable T get(ContextKey<T> key);          // no unchecked cast at the call site
<T> Outcome<T> require(ContextKey<T> key);       // absence with a finding
<T> ContextDraft put(ContextKey<T> key, T v);    // single writer; the compiler ties key and value
<T> ContextDraft contribute(ContextKey<T> key, T partial);   // section contribution
```

### 3.2 Sealed families

Closed sets are `sealed`, so a consumer switch is exhaustive and a new case is a deliberate
change:

```java
sealed interface MatchResult extends AutoCloseable
        permits MatchResult.Admitted, MatchResult.Dropped, MatchResult.Formed, MatchResult.Failed,
                MatchResult.Undecided, MatchResult.Deferred {}
sealed interface Edit permits Place, Remove, Replace, SetTrait, Configure, BindAnchor, NestedEdit {}
sealed interface Remedy permits Remedy.Repair, Remedy.Preview, Remedy.ExplainRemedy {}
```

A `default` branch in a switch over a sealed kernel type is a code smell: it silently
survives a new case. The reference sketches use exhaustive switches.

### 3.3 No stringly-typed channel

A `String` is allowed for: a human message, a descriptor property value, a namespace/path
inside `NamespacedId`, and a document field name. It is not allowed for a bay id, a claim
sharing value, a stage, a check kind, a remedy kind or a finding severity.

## 4. Nullness

- Every kernel package is `@NullMarked` (JSpecify).
- Absence is `@Nullable`, never `Optional` in a public signature.
- `null` means **absent or out of bounds**, never "unreadable". A cell the snapshot cannot
  answer for is a finding (`UNREADABLE`), not `null` (see `06-occupants.md` §5).
- A method that returns a collection returns an empty collection, never `null`.
- `Checks.checkNotNull` is the only assertion helper; it throws only for a violated
  programmer precondition, never for a structural outcome.

## 5. Results, not exceptions

Structural failure is data:

```java
MatchResult result = kernel.check(request);
switch (result) {
    case MatchResult.Admitted admitted -> { /* the planned claim and the revision it read */ }
    case MatchResult.Dropped dropped -> { /* the snapshot moved; no verdict exists */ }
    case MatchResult.Formed formed -> { /* outcome + findings */ }
    case MatchResult.Failed failed -> { /* at least one mismatch */ }
    case MatchResult.Undecided undecided -> { /* reason + findings */ }
    case MatchResult.Deferred deferred -> { /* retry token */ }
}
```

Rules:
- `compile`, `check`, `settle`, `derive`, `solve` and `explain` never throw for a bad structure.
- `IllegalArgumentException` is reserved for a malformed value at construction (`Cuboid`
  with `min > max`), and the message names the argument.
- No public member may unconditionally throw (the project's stub ban).
- A host failure (world closed, ledger unavailable) is a finding with a stable code, not an
  exception that unwinds a tick.

## 6. Builders and immutability

- A value with more than four components gets a builder **only if** most components are
  optional. `Blueprint`, `MatchRequest` and `Finding` qualify; `LatticePoint`, `Cuboid` and
  `Claim` do not.
- A builder is mutable, not thread-safe, and `build()` validates once. The built value is
  immutable.
- Every value type is a `record` unless it must cache a derived field; a cached field is
  `private final` and excluded from equality.
- `withX(...)` returns a new value; there is no setter on a built value.

## 7. Budget, cancellation and cost

- `Budget` is passed explicitly. An extension never reads a global budget.
- `ProbeSession.remaining()` returns the budget left *now*, not at step entry; a predicate
  can decide whether the next read is affordable.
- `CancelToken` is checked at step boundaries and at every N-cell read; a cancelled attempt
  returns `Undecided(CANCELLED)`, not an exception.
- `Spend` is reported on every result, including a failed or undecided one. A cap that was
  hit is a finding (`SC-ENG-…`), never silent truncation.

## 8. The golden example

This is the API a first-use caller should be able to write. It is a design target, not
compiled code; every name in it appears in the glossary.

```java
// 1. A blueprint: a shell, a mouth, an interior bay, an anchor. No stage is declared.
Blueprint furnace = Blueprint.builder(NamespacedId.of("mymod", "huge_furnace"))
        .bounds(Cuboid.of(0, 0, 0, 7, 6, 7))
        .bay(NamespacedId.of("mymod", "mouth"), Cuboid.of(3, 0, 0, 5, 2, 1),
                Expectations.allOf(
                        Expectations.block(NamespacedId.of("minecraft", "iron_bars")),
                        Expectations.trait(Traits.ITEM_INVENTORY)),
                Coverage.ONE_SUBJECT, Footprint.Bound, Sharing.EXCLUSIVE, "the opening")
        .bay(NamespacedId.of("mymod", "wall"), Cuboid.of(0, 0, 6, 7, 6, 7),
                Expectations.block(NamespacedId.of("minecraft", "bricks")),
                Coverage.EVERY_CELL, Footprint.Box, Sharing.EXCLUSIVE)
        .bay(NamespacedId.of("mymod", "interior"), Cuboid.of(1, 1, 1, 6, 5, 6),
                Expectations.air(),
                Coverage.EVERY_CELL, Footprint.Box, Sharing.EXCLUSIVE)
        .anchor(new AnchorSpec(AnchorRoles.FRONT, NamespacedId.of("minecraft", "facing"), "east"))
        .orientation(OrientationPolicy.builder(AnchorRoles.FRONT)
                .allowTurns(Turn.NONE, Turn.CLOCKWISE, Turn.HALF, Turn.COUNTERCLOCKWISE)
                .uprightOnly()
                .build())
        .claim(Sharing.EXCLUSIVE)
        .derive(NamespacedId.of("mymod", "interior_volume"), Arguments.empty())
        .build();

// 2. One request for this candidate: `lease` is the host's capture, and the same request is settled later.
MatchRequest request = MatchRequest.at(lease, furnace, LatticePoint.of(10, 64, 10))
        .budget(Budget.of(4_096, 64))
        .build();
MatchResult probe = kernel.check(request);          // read-only: no claim, no frame

// 3. Exhaustive handling; no exception, no Optional.
switch (probe) {
    case MatchResult.Failed failed -> renderer.render(failed.findings());
    case MatchResult.Dropped dropped -> log(dropped.cause(), dropped.findings());
    case MatchResult.Undecided undecided -> log(undecided.reason(), undecided.findings());
    case MatchResult.Admitted admitted -> {
        // `settle` derives the rows itself and takes `admitted` over, closing it on every path.
        MatchResult registered = kernel.settle(request, admitted);
        if (registered instanceof MatchResult.Formed formed) {
            StructureContext ctx = kernel.derive(formed, furnace.derivations(),
                    Budget.of(1_024, 16));
            Integer volume = ctx.get(ContextKeys.INTERIOR_VOLUME);   // null when unproduced
            RuntimeState state = RuntimeState.of(ctx, List.of(), formed.generation());
        }
    }
    case MatchResult.Formed formed -> { /* already registered */ }
    case MatchResult.Deferred deferred -> scheduler.retry(deferred.token());
}
```

A data-first caller replaces step 1 with a document and gets the same `Blueprint`:

```java
CompileResult compiled = kernel.compile(document);          // DESIGN: validate + assemble
if (compiled.blueprint() != null) {
    MatchResult result = kernel.check(MatchRequest.at(lease, compiled.blueprint(), origin)
            .build());
} else {
    renderer.render(compiled.findings());
}
```

What the example demonstrates:

- The user never sees a `WorldView` implementation, a session, a step cost or a claim
  reservation.
- The engine owns the plan and the stage of every check; the caller names neither, and the
  read-only `check` is cheap enough to call at any rate.
- The orientation policy is declarative, and no controller entity appears.
- A derived value is typed and absent-able; it is requested with a key, not a string.
- The sealed result forces the caller to handle "no conclusion" separately from "wrong".

## 9. The addon example

An addon author writes one expectation, registers a factory, and never touches the engine.

```java
public final class HatchExpectation implements Expectation {
    public static final BindingKey<OccupantRef> HATCH = BindingKeys.of("hatch", OccupantRef.class);

    @Override
    public ExpectationMeta meta() {
        return new ExpectationMeta(NamespacedId.of("mymod", "hatch"), 4, Set.of(Traits.ITEM_INVENTORY));
    }

    @Override
    public Reply test(ExpectationContext ctx) {
        OccupantSnapshot occupant = ctx.occupantAt(ctx.cells().min());
        if (occupant == null) {
            return Reply.unreadable(Finding.builder(FindingCode.of(Family.FOC, 301))
                    .stage(ctx.stage())
                    .label(ctx.locus(), "this cell was not readable")
                    .build());
        }
        if (!occupant.has(Traits.ITEM_INVENTORY)) {
            return Reply.mismatch(Finding.builder(FindingCode.of(Family.EXP, 2))
                    .stage(ctx.stage())
                    .label(ctx.locus(), "expected an inventory-bearing hatch")
                    .note("found " + occupant.descriptor().type())
                    .build());
        }
        return Reply.match(Bindings.of(HATCH, occupant.ref()));
    }
}
```

The three reply cases are distinct on purpose: `unreadable` is not `mismatch`, and only
`match` binds. A predicate that returns `mismatch` for a missing trait is claiming
knowledge the snapshot does not have.

## 10. Anti-patterns

| Anti-pattern | Why it is refused |
| --- | --- |
| A `boolean isStructure(...)` facade | Loses findings, stage, claim and context; the caller re-walks the world. |
| A `Map<String, Object>` for context | Defeats R-13; a typo becomes a runtime `null`. |
| A controller `Entity` parameter | Violates R-04 and I-5; the anchor is a role, not a mob. |
| `throw new StructureException("not formed")` | Structural failure is `Failed`/`Undecided`, not an exception. |
| A `Predicate<BlockState>` shortcut | Privileges blocks (R-01) and bypasses the snapshot (I-4). |
| A public `MatchSession` setter | Extensions get `ProbeSession`; only the engine writes. |
| A `getOrThrow` on a context | Use `require` only when a prior check proved presence; otherwise `@Nullable` + a finding. |
| A `default` branch over a sealed result | Hides a new case; exhaustive switch is the contract. |

## 11. Reference-sketch standard

A sketch in a module file is accepted when:

1. Every type it names is in the glossary or is declared in the same section.
2. It compiles against the frozen names when the bodies are filled; omitted bodies are
   marked with `// …` or a one-line comment, never with `throw new UnsupportedOperationException`.
3. It shows the error path, not only the happy path: at least one `Reply.mismatch`/
   `Finding` and one `Undecided`.
4. It uses an exhaustive switch over every sealed type it handles.
5. Its `Use cases` section names a concrete addon (a hatch, a door, a huge furnace) and a
   concrete host (a command, a datapack reload, a build wand).

A sketch that cannot be made to compile without changing a frozen signature is an **Open
question**, not a reason to change the signature silently.
