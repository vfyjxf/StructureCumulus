# 12 — Context (U8)

> Status: design draft v0.3, 2026-10-20. Package `dev.vfyjxf.cumulus.kernel.context`. Serves R-03,
> R-13; enforces I-7, I-9, I-14. Aligned with `22` revision 5 §6.4 and §8: one `ContextKey`, one
> store per instance, tier-stamped frames, contributions folded at the barrier.

## 1. Scope

In: the key type, the contribution/fold model, the draft and view, tiers and retention, `RuntimeState`
and the augment fold, and the shipped keys. Out: plan units and their execution (U7), checks (U3),
occupants (U2), cuboids (U1), findings' text (U9), catalog registration (U11), persistence (A1).
Intermediate state is not a separate mechanism: a computation writes the same `ContextKey`s a
`StructureContext` reads, through the same draft and store.

## 2. Vocabulary

| Term | Contract |
| --- | --- |
| `ContextKey<T>` | `(id, Class<T>, Retention, @Nullable Merge<T>, @Nullable T whenAbsent)`; the one key type |
| `Merge<T>` | `combine(T left, T right)`; non-null marks a key as section-contributed |
| `Retention` | `ATTEMPT` discarded, `OUTCOME` in `MatchOutcome.context`, `CONTEXT` published into a frame |
| `ContextTier` | `BLUEPRINT`, `PROBE`, `FORM`, `SETTLE`: the stage projection of a frame |
| `ContextFrame` | `(tier, revision, Map<ContextKey<?>, Object>)`, one publication |
| `StructureContext` | `(instance, revision, frames)`; immutable, tier-stamped reads |
| `ContextDraft` | one stage's writes: `put` (single writer), `forSection` + `contribute` (section partial), `refuse`/`declined` (a decided absence) |
| `ContextView` | `get` (nullable) / `require` (`Outcome`) / `at(tier)` |
| `ContextStore` | one per instance: `context(instance)`, `draft(tier, baseRevision)`, `publish(draft)` |
| `RuntimeState` | `(context, augments, contextRevision)` plus fold findings; the only persisted form |
| `Augment` / `AugmentKey` / `AugmentFold` | `(key, finite amount)`; `(id, fold)`; `SUM, MAX, MIN, MULTIPLY, SET` |

`StateKey`, `PartialKey`, `Key`, `Reducer`, `ContextValue` and `ContextLifetime` are gone: they split
one context into three key types and a second fold. `Deriver` remains, as the context-only producer.

## 3. API design

```java
public record ContextKey<T>(NamespacedId id, Class<T> type,
                            Retention retention,
                            @Nullable Merge<T> merge,      // non-null: contributions allowed
                            @Nullable T whenAbsent) {}

public interface Merge<T> { T combine(T left, T right); }

public enum Retention { ATTEMPT, OUTCOME, CONTEXT }
public enum ContextTier { BLUEPRINT, PROBE, FORM, SETTLE }

public record ContextFrame(ContextTier tier, long revision, Map<ContextKey<?>, Object> values) {}

public interface ContextView {
    ContextTier tier();
    <T> @Nullable T get(ContextKey<T> key);        // stamped at or below tier(), never above
    <T> Outcome<T> require(ContextKey<T> key);     // Missing with a finding, never a bare null
    boolean has(ContextKey<?> key);
    Set<ContextKey<?>> keys();                     // ascending (id, type name)
    ContextView at(ContextTier tier);              // narrow to `tier`, same frames; a wider tier clamps
}

public record StructureContext(InstanceId instance, long revision, List<ContextFrame> frames)
        implements ContextView {
    public ContextTier tier() { return ContextTier.SETTLE; }
    public ContextView at(ContextTier tier);       // the same frames, read at `tier`
    public static StructureContext empty(InstanceId instance);
}

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

public interface ContextStore {
    StructureContext context(InstanceId instance);  // frames only, never a lease
    ContextDraft draft(ContextTier tier, long baseRevision);
    Outcome<Void> publish(ContextDraft draft);      // Missing(SC-CTX-0108) when base is stale
}

public interface CatalogsDraft {                     // the catalog's key family while it is being built
    <T> ContextKey<T> registerKey(ContextKey<T> key);
    <T> ContextKey<T> putKey(ContextKey<T> key);     // a second declaration of one id is SC-CTX-0102
    static CatalogsDraft create();
}

public enum AugmentFold { SUM, MAX, MIN, MULTIPLY, SET }
public record AugmentKey(NamespacedId id, AugmentFold fold) { static AugmentKey of(NamespacedId, AugmentFold); }
public record Augment(AugmentKey key, double amount) {
    public Augment { if (!Double.isFinite(amount)) throw new IllegalArgumentException("amount"); }
}

public final class RuntimeState {
    public static RuntimeState of(StructureContext context, List<Augment> augments, long contextRevision);
    public StructureContext context();  Map<AugmentKey, Double> augments();
    public long contextRevision();      List<Finding> findings();
    public <T> RuntimeState upgrade(ContextKey<T> key, T value);    // the host's only write; §4.5
}
```

| Contract | Ruling |
| --- | --- |
| One key | a value that reaches a frame, a check or `RuntimeState` is a `ContextKey`; there is no second key type |
| Verb matches key | `put` on a key with `merge != null` is `SC-CTX-0109`; `contribute` on a key with `merge == null` is the same |
| Declaration is catalog-owned | declarations are built with `ContextKeys.of(...)` and bound with `ContextKeys.register(draft, key)`; two registrations of one id are `SC-CTX-0102`; equality is `(id, type)` |
| No raw `Map` | `ContextDraft` and `ContextView` are the only handles; a frame's map is never handed out |
| Tier stamping | a reader at tier `t` sees values stamped at or below `t`; a publish at `t+1` never rewrites a frame at `t` |
| Absence is published | a frame carries an entry for every key its draft decided; a decided key with no value is recorded as an absence marker, so the frame shadows an older value instead of letting a reader fall back to it |

## 4. Semantics

### 4.1 Stages, tiers, retention

```
Stage      DESIGN   PROBE   FORM   SETTLE
Tier       BLUEPRINT  PROBE  FORM  SETTLE
Projection -          -      -     RUNTIME
```

| `Retention` | Lands in | Read by |
| --- | --- | --- |
| `ATTEMPT` | the attempt's view only; discarded | units of the same attempt |
| `OUTCOME` | `MatchOutcome.context` | aggregates, checks, remedies that hold the outcome |
| `CONTEXT` | the stage tier's frame, then the persisted `SETTLE` frame | `StructureContext`, remedies through `ContextView` |

- `RUNTIME` has no producer: it is the persisted projection of `SETTLE` plus host upgrades (§4.5).
- A contributed key exists in two forms inside one attempt: the per-section partials (in the draft)
  and the folded value (in the attempt view after the barrier). Both are the same `ContextKey`; there
  is no `PartialKey`.
- `whenAbsent` is a declared field, visible in the document, and honoured only when every section in
  the region was readable (§4.3).
- Canonical key order is `(id, type name)`; it is the order of `keys()`, of a frame's iteration, and
  of `RuntimeState`'s augment groups.

### 4.2 Publication

- **One store per instance.** `StructureContext` carries `InstanceId`; two furnaces publishing
  `mymod:volume` publish into different stores and never collide. `SC-CTX-0107` therefore means two
  writers for *one* instance, not two structures sharing a key.
- A `ContextDraft` is opened for a tier with the `baseRevision` the publisher read, written by the
  units of that stage, and published once at the stage boundary.
- `publish` returns `Outcome<Void>`: a stale `baseRevision` is `Missing(SC-CTX-0108)` and nothing is
  written. Two different values for one key in one frame is `SC-CTX-0107`, refused rather than
  last-write-wins.
- A draft that decides a key without a value publishes that absence: the frame records the marker,
  so a reader at that tier or above answers `null`/`Missing` instead of an older frame's value.
- **One writer.** The matcher publishes one draft per stage boundary and the store serializes
  publications per instance; no other component opens a draft for a tier.
- Readers are tier-stamped: an aggregate reads `context()` at its stage's tier, a check reads through
  `FocusContext`, and `StructureContext.at(t)` narrows the instance context to `t`; a tier above the
  view's own is clamped to it, so `at` never widens.

### 4.3 Contributions and the fold

- A section computation contributes through a section-scoped draft: `draft.forSection(section)` stamps
  every `contribute(key, partial)` of that handle with the section, and `contribute` on a draft with no
  section scope is refused.
- At the barrier, contributions are sorted by `SectionKey` `(x, z, y)` and combined left-to-right
  with `merge.combine`. The fold starts at the first present contribution; there is no identity.
- Any refused section in the region makes the key absent. A short sum is unrepresentable. A section
  computation that could not produce its partial records `draft.refuse(key)`; one that read every
  section and contributed nothing records `draft.declined(key)`, the only path to `whenAbsent`.
- An aggregate computation or a check reads the folded value through `view.require(key)`; a
  `PartialKey`-style direct read does not exist.
- A single-writer key (`merge == null`) is written once with `put`, by an aggregate computation or a
  `Deriver`; a second write of a different value in one attempt is `SC-CTX-0107`, and writing the same
  value twice is accepted.
- Parallel section passes are allowed: contributions are independent, and the fold order is fixed.

### 4.4 Augments

The fold lives on `AugmentKey`, so every contributor to one effect agrees on how it stacks.

| Fold | Identity | Running total | Use |
| --- | --- | --- | --- |
| `SUM` | `0.0` | `+=` | independent bonuses |
| `MAX` / `MIN` | `-∞` / `+∞` | `max` / `min` | best installed tier, never stacking; a constraint |
| `MULTIPLY` | `1.0` | `*=` | one subsystem's scale factor |
| `SET` | — | last contribution in order | an override |

`RuntimeState.of` stable-sorts by `AugmentKey` (`id`, fold ordinal), then folds each key left to right:
key order canonical, within-key order the declaration order. Strict floating point makes that
bit-reproducible. An unrepresented key is **absent**, never zero or one; a non-finite `amount` is
refused at construction; a non-finite *total* from finite inputs is omitted with `SC-CTX-0301`, a
label-less `WARNING` whose message names the augment key.

### 4.5 Runtime state

| Question | Answer |
| --- | --- |
| Persisted where? | The host: the kernel holds no map, cache or static field (I-15) and does no I/O |
| What is persisted? | The `SETTLE` frame, the augment map and `contextRevision`; `BLUEPRINT`/`PROBE`/`FORM` frames are recomputed |
| Recomputed on reload? | Everything by default — a value is a function of the capture, the plan and the merge |
| Stale? | `contextRevision` plus the settled match decide; `generation` is derived by `InstanceRecord`, never bumped by hand |
| Why not a format? | No `schemaVersion`: JVM `Class` tokens change with the mod, and I-12 makes *documents* the versioned format |

**Host upgrades** are the one write from outside the match, and they follow the same single-writer
rule: `RuntimeState.upgrade(key, value)` publishes at `contextRevision + 1`, takes a `ContextKey` with
`Retention.CONTEXT`, and is refused when the base it was computed from has moved (`SC-CTX-0108`). A
host upgrade never edits a frame in place.

### 4.6 Hierarchy

Two structures are two instances and two stores, so a child's `mymod:volume` and a parent's never
collide, and no promotion step exists inside a plan. The cross-instance read has two shapes: the
parent re-computes the value in its own plan, or the host reads the child's settled `StructureContext`
and writes it into the parent's frame as an upgrade (§4.5). The second avoids a second walk and is a
host obligation, not a kernel one. Recorded as a gap, not resolved here.

## 5. Reference sketch

```java
// addon side, dev.vfyjxf.cumulus.example — the worked example, not a kernel type.
// ContextKeys.of builds each declaration; register binds it to the Catalogs draft (§6.4 of 22).
static final ContextKey<Long> IRON = ContextKeys.of(
        NamespacedId.of("structurecumulus", "iron_count"), Long.class,
        Retention.OUTCOME, Merges.sum(), null);

static final ContextKey<Integer> VOLUME = ContextKeys.of(
        NamespacedId.of("structurecumulus", "interior_volume"), Integer.class,
        Retention.CONTEXT, Merges.sum(), null);

final class IronSection implements Computation<Long> {                 // one contribution per section
    @Override public ContextKey<Long> output() { return IRON; }
    @Override public StatePhase phase() { return StatePhase.SECTION; }
    @Override public CuboidSet region() { return INTERIOR; }
    @Override public Set<ContextKey<?>> inputs() { return Set.of(); }
    @Override public Outcome<Long> compute(ComputationContext ctx) {
        return new Outcome.Found<>(countIron(ctx.section(), ctx.cells()), List.of(), FindingBundle.empty());
    }
}

final class Capacity implements Computation<Integer> {                 // one value, after the barrier
    @Override public ContextKey<Integer> output() { return CAPACITY; }
    @Override public StatePhase phase() { return StatePhase.AGGREGATE; }
    @Override public Set<ContextKey<?>> inputs() { return Set.of(VOLUME); }
    @Override public Outcome<Integer> compute(ComputationContext ctx) {
        return ctx.read(VOLUME).fold(
                (volume, subjects) -> new Outcome.Found<>(volume * 4, subjects, FindingBundle.empty()),
                bundle -> new Outcome.Missing<>(bundle));
    }
}

// a predicate consumes the folded value; absence is a Missing, never a zero
Check<Integer> enoughFree = new Check<>(
        NamespacedId.of("structurecumulus", "enough_free"), interiorBay,
        Focuses.context(VOLUME),
        volume -> volume >= 64
                ? Reply.match()
                : Reply.mismatch(Codes.CAVITY_TOO_SMALL.finding(...)),
        100);

// host side: `store` is the instance's store (§4.2), one draft per stage boundary
StructureContext before = store.context(instance);                     // revision 7, FORM frame
ContextDraft draft = store.draft(ContextTier.FORM, before.revision());
// the section pass calls draft.forSection(section).contribute(VOLUME, partial) once per section;
// the barrier folds; the aggregate calls draft.put(CAPACITY, 400).
Outcome<Void> published = store.publish(draft);                        // Missing(SC-CTX-0108) if 7 moved
if (published instanceof Outcome.Missing<Void> stale) {
    // another publish won; the attempt re-reads at revision 8 rather than overwriting it
}

// tier-stamped read: a PROBE reader cannot see a FORM value
StructureContext ctx = store.context(instance);
Integer atProbe  = ctx.at(ContextTier.PROBE).get(VOLUME);               // null before the FORM frame
Integer atForm   = ctx.get(VOLUME);                                     // 100

// augments and the host upgrade
AugmentKey capacityBonus =
        AugmentKey.of(NamespacedId.of("structurecumulus", "capacity_bonus"), AugmentFold.SUM);
RuntimeState state = RuntimeState.of(ctx,
        List.of(new Augment(capacityBonus, 3.0), new Augment(capacityBonus, 1.5)),
        ctx.revision());
double bonus = state.augments().get(capacityBonus);                     // 4.5
RuntimeState upgraded = state.upgrade(VOLUME, 144);                     // revision + 1, or SC-CTX-0108
```

## 6. Use cases

**Addon author.** Writes one section `Computation` for each per-section quantity and one aggregate
`Computation` (or `Deriver`) for each folded value; declares one `ContextKey` with its `Retention` and
optional `Merge`; a check reads it with `Focuses.context(key)`. There is no second key type to learn
and no `merge` law beyond "associative", because the fold order is fixed.

**Host.** Publishes one draft per stage boundary, persists only the `SETTLE` frame, applies augments the
host supplies, stores the `RuntimeState` under its `InstanceRef`, and compares `contextRevision` plus
the settled match on load. `/cumulus explain` prints each published key with the tier and revision that
published it.

## 7. Proofs

**P1 Determinism.** Contributions are folded over one list sorted by `SectionKey` (x, z, y); frames are
ordered by revision and key order is `(id, type name)`; findings append in pass order; no step iterates
a hash container or a clock. *Breaks if* a merge reads a hash-ordered collection, a computation calls
`System.nanoTime()`, or a publisher opens two drafts for one tier.

**P2 No re-walk.** `WorldView` is the match's own immutable snapshot (I-4), every read is charged into
`spend()`, and the shipped algorithm is one plural query per section plus cuboid arithmetic.

**P3 One key, one declaration.** `ContextKeys.register` refuses a second registration of an id
(`SC-CTX-0102`), and equality is `(id, type)`, so no two components can mean different things by
`mymod:volume`.

**P4 Type safety.** `get`/`require`/`put`/`contribute` are generic in `T`, so the compiler ties key and
value; the frame stores `id → (type, value)` and the view casts against `key.type()`. Residual hole: a
value type must be reifiable (`04` §3.1).

**P5 Spend monotonicity.** Cells and sections are charged on dispatch, cached or not
(`22` §9.5); exhaustion yields one stop and never a partial value.

**P6 Augment fold determinism.** The stable sort fixes key order and preserves declaration order within
a key; the fold is left to right with documented identities; strict floating point is bit-reproducible.

**P7 One writer per instance.** Every publication goes through one store, takes a `baseRevision`, and is
refused when stale (`SC-CTX-0108`); host upgrades obey the same rule (§4.5).

**P8 Tier monotonicity.** A frame is immutable once published and a reader never looks above its tier,
so a publish at `t+1` cannot change a verdict already reached at `t`.

## 8. Dependencies and open questions

| Dependency | Owner | Assumption here |
| --- | --- | --- |
| `ContextKey`, `Merge`, `Computation`, `SectionContext`, `AggregateContext`, `ContextDraft`, `ContextView` | U7 / `22` §6.4, §7.3 | a section context has no `read(ContextKey)`; a section computation can only `contribute` |
| `MatchOutcome.context`, `Formed.generation()` | U7 | `OUTCOME` values are read from the outcome; `generation` is derived |
| `WorldView.occupants(Cuboid)`, `OccupantSnapshot.footprint()` | U2 | footprints are the query's answer |
| `Cuboid.volume()/intersection`, `CuboidSet.of/empty/union/subtract/volume` | U1 | the arithmetic in §4.3 and §5 |
| `ContextKeys.register`, `Catalogs.contextKeys`, `Spec.args` | U11, U5 | one registration funnel |
| `Outcome`, `Finding`, `FindingCode`; `InstanceId`, `InstanceRecord` | U0, U9, U7 | codes `SC-CTX-0102, 0107, 0108, 0109, 0301` |

**Open questions**, restricted to what `22` §16 leaves open for this unit. (1) **§16 Q3, `whenAbsent`**:
honoured only when every section in the region was readable; is that too strict for an optional
decorative region? The field stays on `ContextKey` either way.

## 9. Falsification

| Test | Breaks | Expected |
| --- | --- | --- |
| Publish a draft whose `baseRevision` is one behind | P7 | `Missing(SC-CTX-0108)`, the newer frame intact |
| Two units put different values for one key in one draft | §4.2 | `SC-CTX-0107`, no frame written |
| `put` on a key whose `merge` is non-null | §3 verb rule | `SC-CTX-0109` at DESIGN |
| A `FORM` reader asks for a key first published at `SETTLE` | P8 | `null` at `ContextTier.FORM`, the value only at `SETTLE` |
| One of three contributed sections is `Refused` | P1 / §4.3 | the key is absent, `require` returns `Missing`, never a short sum |
| Two `Augment(capacityBonus, 1e308)` on a `SUM` key | `RuntimeState.of` | infinite total refused, key omitted, `SC-CTX-0301` |
| `new Augment(capacityBonus, Double.NaN)` | the finiteness check | `IllegalArgumentException` at construction |
| The same plan run twice, once after shuffling section order | P1 | identical context: same value, same frame order, same spend |
| A host upgrade against a moved `contextRevision` | §4.5 | refused with `SC-CTX-0108`; the older `StructureContext` unchanged |
| A `SectionContext` calling `read(key)` | §4.3 | does not compile: no such member |
