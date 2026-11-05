# Module `kernel.context`

> Rank 5. Typed values outlive one check: the per-instance store, the publish rule, the derivation
> pass, and the merge combinators. One key type, one publication point, no tiers.

## 1. Scope

**Owns:** `StructureManager`, `StructureInstance`, `StructureSpec`, `StructureAttempt`,
`StructureEvent`, `ContextStore`, `StructureContext`, `CachedContext`, `Deriver<T>`, `DeriveContext`,
`Merges`.

**Does not own:** `ContextKey`/`ContextView`/`Outcome` (`core`), computations and their contexts
(`structure`), match results (`engine`), world lifecycle detection and persistence (`mc`).

**Imports:** `core`, `space`, `finding`, `structure`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `StructureContext` | record | `(instance, revision, values, findings, spend)`, a `ContextView` |
| `ContextStore` | interface | per-instance publish/read/remove; the narrow engine port |
| `StructureSpec` | record | `(owner, siting, structure)`, what to form |
| `StructureInstance` | record | `(id, spec, context, bounds)`, the formed structure |
| `StructureAttempt` | interface | a draft `ContextStore`; `id`, `spec`, `commit(bounds)`, `abort` |
| `StructureManager` | interface | in-memory registry; `begin`, world-scoped `find`, `findAt`/`findIn`, `instances`, `onEvent` |
| `InMemoryStructureManager` | final class, package-private | the server-thread registry behind `StructureManager.create()` |
| `StructureEvent` | sealed interface | `Broken`, `Loaded`, `Unloaded`, `Invalidated` |
| `CachedContext` | interface | host-owned read port: `<T> @Nullable T get(NamespacedId id, Class<T> type)` |
| `Deriver<T>` | interface | context-only producer of one key |
| `DeriveContext` | interface | what a deriver sees: base context, budget, findings |
| `Merges` | final class | `sum`, `union`, `min`, `max`, `and`, `or` |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.context;

public record StructureContext(InstanceId instance, long revision,
                               java.util.Map<ContextKey<?>, Object> values,
                               FindingBundle findings, Spend spend) implements ContextView {
    public StructureContext { /* immutable, keys sorted by (id, type name) */ }
    public static StructureContext empty(InstanceId instance);
    @Override public <T> @Nullable T get(ContextKey<T> key);
    @Override public <T> Outcome<T> require(ContextKey<T> key);
    @Override public boolean has(ContextKey<?> key);
    @Override public java.util.Set<ContextKey<?>> keys();
}

public interface CachedContext {
    <T> @Nullable T get(NamespacedId id, Class<T> type);
}

public interface ContextStore {
    StructureContext context(InstanceId instance);
    @Nullable StructureContext remove(InstanceId instance);
    StructureContext publish(InstanceId instance, java.util.Map<ContextKey<?>, Object> values,
                             FindingBundle findings, Spend spend);
    static ContextStore inMemory();
}

public record StructureSpec(OwnerId owner, Siting siting, NamespacedId structure) {}
public record StructureInstance(InstanceId id, StructureSpec spec, StructureContext context,
                                CuboidSet bounds) {}
public interface StructureAttempt extends ContextStore {
    InstanceId id();
    StructureSpec spec();
    StructureInstance commit(CuboidSet bounds);
    void abort();
}
final class InMemoryStructureManager implements StructureManager { /* server-thread registry */ }

public interface StructureManager extends ContextStore {
    static StructureManager create();
    StructureAttempt begin(World world, StructureSpec spec);
    @Nullable StructureInstance find(InstanceId id);
    @Nullable StructureInstance find(World world, OwnerId owner, Siting siting);
    default @Nullable StructureInstance find(OwnerId owner, Siting siting) { /* ambiguous throws */ }
    java.util.List<StructureInstance> findAt(LatticePoint worldCell);
    default java.util.List<StructureInstance> findIn(CuboidSet worldCells);
    java.util.List<StructureInstance> instances();
    void onEvent(StructureEvent event);
}
public sealed interface StructureEvent {
    record Broken(InstanceId id) implements StructureEvent {}
    record Loaded(World world, java.util.List<StructureInstance> instances) implements StructureEvent {}
    record Unloaded(World world) implements StructureEvent {}
    record Invalidated(InstanceId id, String reason) implements StructureEvent {}
}

public interface Deriver<T> {
    ContextKey<T> output();
    java.util.Set<ContextKey<?>> requires();
    Outcome<T> derive(DeriveContext ctx);
    default Outcome<T> derive(DeriveContext ctx, Value arguments) { return derive(ctx); }
}

public interface DeriveContext extends ContextView {
    StructureContext base();
    Budget remaining();
    CancelToken cancel();
    void report(Finding finding);
}

public final class Merges {
    public static Merge<Long> sum();
    public static Merge<CuboidSet> union();
    public static Merge<Long> min();
    public static Merge<Long> max();
    public static Merge<Boolean> and();
    public static Merge<Boolean> or();
    public static <T> Merge<T> of(java.util.function.BinaryOperator<T> operator);
    private Merges() {}
}
```

## 4. Laws

### 4.1 StructureContext

One `StructureContext` per `InstanceId`: a revision, a typed value map, the latest findings and the
cumulative spend. `revision` starts at 0 and grows by one per publish. The map is immutable and
ordered by `(key.id, key.type.getName())`; `keys()` returns that order. `get` returns `null` only
when the key is absent; a published `null` is not a value (a key's absence is the absence of the
entry). `require` returns `Found(value)` or `Missing`; it never throws. `has` is `get != null`.

`StructureContext` is the derived form. The manager does not persist it; the adapter is in-memory
(D-40). A host that persists its own non-derived values owns the codec; the kernel defines no wire
format (I-12 is about documents, not runtime values).

### 4.2 Publication

`publish(instance, values, findings, spend)`:

1. Read the current context; the new revision is `current.revision() + 1`.
2. Reject a key with `Retention.match` in `values` (`IllegalArgumentException`): only `instance`
   values are published.
3. Merge: every entry in `values` replaces the current entry; keys not mentioned keep their old
   value. A published `null` is not representable; absence is the absence of an entry.
4. Store `findings` as the latest bundle and `spend` as the cumulative spend
   (`current.spend.plus(spend)`).
5. Return the new context.

`publish` is total and single-writer: the engine publishes at most once per settle and once per
derive, both on the server thread, and the host serialises them per instance. A concurrent publish
is a host contract violation, not a kernel outcome. A successful `settle` publishes the `instance`
values once; a `derive` publishes its own batch once at `revision + 1`. There is no base-revision CAS
and no stale-publish finding.

A published `instance` value must be a value: it is merged into the instance context and survives
every later revision, so anything held there outlives the lease that produced it. A live host handle
— a capability, an entity — may only be published at `Retention.match`, where it dies with the
attempt. A `Retention.instance` handle is a contract violation (D-41); the store cannot detect it,
because the value is an arbitrary `Object`.

**Removal.** `remove(instance)` returns the stored `StructureContext`, or `null` when the instance is
absent; it never fabricates an empty context. After removal a later `context(instance)` reads the
empty context at revision 0. The manager removes the instance and its context together; a set
rollback dispatches `Broken` for every formed attempt, including one whose claim release failed
(09 §10.3); a prior instance already replaced by an earlier commit in the same set run is not
reconstructed, so commit-phase failure is a documented host-contract limitation rather than atomic
replace. On a draft, `remove` closes the attempt: it releases the `(world, owner, siting)`
reservation, returns the old context, and subsequent `publish`/`remove`/`commit`/`abort` throw
`IllegalStateException`; no instance is formed. `Broken`/`Invalidated` and a world unload close a
draft the same way.

### 4.2.1 StructureManager

`begin(world, spec)` allocates an `InstanceId` and a draft `StructureAttempt`; the reservation is
`(world, owner, siting)`. A second draft for that tuple is rejected; the same tuple in another world
is independent, and a formed instance at the tuple in the same world may be replaced by a later
`commit`. `find(World, owner, siting)` is the unambiguous lookup; the two-argument `find` answers the
tuple only when exactly one world holds it and throws on ambiguity. The settle pass publishes into
the attempt. Only an open attempt accepts it: a closed attempt is `Undecided(designDefect)` before
the ledger is touched. `commit(bounds)` forms the `StructureInstance`; `abort` discards the draft;
`remove` on the draft closes it as above. `release(instance)` plus `abort` is the rollback of a
settled attempt, and it is exact — the settle compare-and-set replaced that instance's rows, so
`release` removes exactly the rows that settle wrote. The manager stores the world-frame `bounds` only for lifecycle lookup
(`findAt`/`findIn`); it does not interpret them. `findAt`, `findIn` and `instances` are
registry-wide: they take no `World` and see every world held by the manager. `find(World, owner,
siting)` is the world-qualified tuple lookup; the adapter's one-manager-per-level layout makes the
registry-wide helpers per-level in production.

The manager is in-memory and single-writer on the server thread. `Broken`/`Invalidated` remove the
instance (or close a draft); a structure that a piston pushes is invalidated, not translated, so
there is no move event; `Loaded(World, List)` validates every restored
instance belongs to that world and has a unique id/siting before replacing only that world, closes
that world's drafts and raises the serial watermark; other worlds are untouched. A rejected
`Loaded` leaves the registry unchanged. `Unloaded(world)` removes that world's instances and drafts
only. Neither unload nor `Loaded` touches persisted claim rows: world unload keeps claims, while
`Broken`/`Invalidated`/move release the whole `(owner, siting)` key (D-38). The adapter dispatches
these events from `WatchEvents`; the manager never listens to Minecraft events and never persists.

### 4.3 Derivation

`derive` is a separate call over a `Formed`. The engine resolves each `DerivationSpec.deriver`
through `Catalogs.derivers()`; a missing id makes the output absent and reports `SC-CTX-0004`. The
derivers run in `(order, id)` order; each sees the context published by the settle plus the values
produced earlier in the same derive pass. A `Deriver.requires` key must be present or the deriver is
skipped with `SC-CTX-0007`; a `Missing` return makes the output absent and adds the deriver's own
findings, or `SC-CTX-0005` if it reported none. All `Found` values of one derive pass are published
in one `ContextStore.publish`; a `match` key produced by a deriver is rejected with `SC-CTX-0005`
(only `instance` values are published). A derive pass publishes one revision, never a partial batch.

A deriver has no world view and no lease: it reads `DeriveContext` (the base context) only. A
deriver that needs the world must be a computation, not a deriver. `DerivationSpec.arguments` is
passed to the `derive(ctx, arguments)` overload; the single-argument overload is the common path.

### 4.4 Merges

A `Merge` is associative and has no identity; the fold is over contributions sorted by
`SectionKey`. `sum` is `Long` addition, `union` is `CuboidSet.union`, `min`/`max` are numeric,
`and`/`or` are boolean. `of` accepts a caller's operator and is the only way to add a merge not in
this list. The engine owns the fold; the merge only combines two values.

### 4.5 Retention, in one table

| Retention | Where the value goes | Read by | Persisted |
| --- | --- | --- | --- |
| `match` | `MatchOutcome.values` | later checks/aggregates of the same attempt | no |
| `instance` | `StructureContext` after a successful settle/derive | checks, derivers, remedies, host | yes, by the host |

There is no `ATTEMPT`, no `OUTCOME`, no tier and no frame. A value that is only needed for a
diagnostic is a `match` value; a value the instance keeps is `instance`.

### 4.6 Cached instance context (host contract)

An `instance` value outlives an attempt only because the host persists it. For a later attempt the
host may offer those values through `CachedContext`, a get-only port keyed by value identity
`(id, type)`; `MatchRequest.cachedContext` carries it. It is a port, not a `ContextView`: a host
that decodes a stored `StructureContext` answers by id and type and never has to reconstruct
`merge`/`whenAbsent`, and the kernel never enumerates the cache.

`check` composes the cache read-only with the attempt's own values (`engine` §7.7): only
`Retention.instance` keys are eligible, and only keys the plan declares as instance dependencies — a
check's `ExpectationMeta.requires()` or a section/aggregate `Computation.inputs()` — participate. A
current attempt value shadows the cache, including when the producer's key object differs in
`merge`/`whenAbsent`; a cached lookup answers by `(id, type)`; a wrong-typed value or a host
exception is treated as absent. A required key still absent after composition is `SC-CTX-0007`.

The host owns cache validation, read, write, invalidation and persistence. The kernel defines no
cache format, no stamp, no definition digest and no stale-cache finding; whatever the host answers
is the instance's truth for that tuple. A cached value never becomes a `MatchOutcome` value and
`publish` never re-publishes it, so the cache cannot drift into the store through a settle or
derive.

## 5. Pipeline role

- `check`: the engine folds section contributions and runs aggregates into a local value map;
  `Retention.match` values stay in the outcome; `instance` values are carried but not published. A
  supplied cached instance context is composed read-only for the declared instance dependencies
  (check requires and computation inputs, `engine` §7.7).
- `settle`: after `Settled`, publish the `instance` values once; `Formed.contextRevision` is the new
  revision.
- `derive`: run derivers over the formed context; publish one batch; return the new
  `StructureContext`.

## 6. Codes

`SC-CTX-0001` (ERROR, primary): a computation produced no value. Producer: `engine.check`.
`SC-CTX-0004` (ERROR, primary): a derivation names
a missing deriver. Producer: `engine.derive`. `SC-CTX-0005` (ERROR, primary): a deriver failed.
Producer: `engine.derive`. `SC-CTX-0006` (ERROR, primary): a fold has a refused section, or a
section computation returned `Missing` for a `Partial` slice. Producer: `engine.check`.
`SC-CTX-0007` (ERROR, primary): a required key is absent. Producer: `Focuses.context`,
`engine.check` (a required key absent after cache composition) and `engine.derive`.

## 7. Tests

- `StructureContext`: sorted keys, typed get/require, empty, immutability.
- Publish: merge semantics, revision growth, `match` rejection, cumulative spend, latest findings,
  one writer per instance.
- Remove: the old context is returned, a second remove answers `null` (or throws on a closed
  attempt), the instance reads empty at revision 0 afterwards; `Broken` removes the instance and
  context together; draft removal closes the attempt and releases its reservation.
- Manager/attempt lifecycle: world-scoped keys (`(world, owner, siting)`), per-world `Loaded` with
  validate-before-mutate atomicity, one-shot commit/abort/remove, closed-attempt write rejection,
  per-world unload and draft invalidation; `StructureManagerTest` and `AdversarialManagerTest`.
- Derive: order, requires, missing deriver, stale base, one batch, no partial publish.
- Cached context: instance-only eligibility, the declared-dependency gate (check requires and
  computation inputs), lookup by `(id, type)` for a host-decoded key, the attempt value shadows the
  cached one, a missing key is `SC-CTX-0007`, a wrong type or host exception is absent, a `match` key
  is never read from the cache, and a cached value is never published.
- Merges: associativity for each built-in; `union` canonical.
- In-memory store: one server-thread writer; two instances at the same `(world, owner, siting)`
  never coexist, and the same tuple in two worlds does.

## 8. Dependencies

`core`, `space`, `finding`, `structure` (for `Computation`/`Plan` types used by the engine; this
module itself uses only `NamespacedId`/`ContextKey`/`ContextView`/`Outcome`/`Budget`/`CancelToken`). It does not
import `engine`, `document`, `remedy` or `spi`.
