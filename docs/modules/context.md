# Module `kernel.context`

> Rank 5. Typed values outlive one check: the per-instance store, the publish rule, the derivation
> pass, and the merge combinators. One key type, one publication point, no tiers.

## 1. Scope

**Owns:** `ContextStore`, `StructureContext`, `Deriver<T>`, `DeriveContext`, `Merges`.

**Does not own:** `ContextKey`/`ContextView`/`Outcome` (`core`), computations and their contexts
(`structure`), match results (`engine`), persistence (`mc`).

**Imports:** `core`, `space`, `finding`, `structure`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `StructureContext` | record | `(instance, revision, values, findings, spend)`, a `ContextView` |
| `ContextStore` | interface | per-instance publish/read; `inMemory()` for tests and hosts |
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

public interface ContextStore {
    StructureContext context(InstanceId instance);
    StructureContext publish(InstanceId instance, java.util.Map<ContextKey<?>, Object> values,
                             FindingBundle findings, Spend spend);
    static ContextStore inMemory();
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

`StructureContext` is the persisted form. The host serialises `(instance, revision, values)` with its
own codec keyed by `ContextKey.id`; the kernel does not define a wire format (I-12 is about documents,
not runtime values).

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

### 4.3 Derivation

`derive` is a separate call over a `Formed`. The engine resolves each `DerivationSpec.deriver`
through `Catalogs.derivers()`; a missing id makes the output absent and reports `SC-CTX-0004`. The
derivers run in `(order, id)` order; each sees the context published by the settle plus the values
produced earlier in the same derive pass. A `Deriver.requires` key must be present or the deriver is
skipped with `SC-CTX-0007`; a `Missing` return makes the output absent and adds the deriver's own
findings, or `SC-CTX-0005` if it reported none. All `Found` values of one derive pass are published
in one `ContextStore.publish`; a `match` key produced by a deriver is rejected with `SC-CTX-0005`
(only `instance` values are persisted). A derive pass publishes one revision, never a partial batch.

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

## 5. Pipeline role

- `check`: the engine folds section contributions and runs aggregates into a local value map;
  `Retention.match` values stay in the outcome; `instance` values are carried but not published.
- `settle`: after `Settled`, publish the `instance` values once; `Formed.contextRevision` is the new
  revision.
- `derive`: run derivers over the formed context; publish one batch; return the new
  `StructureContext`.

## 6. Codes

`SC-CTX-0001` (ERROR, primary): a computation produced no value. Producer: `engine.check`.
`SC-CTX-0004` (ERROR, primary): a derivation names
a missing deriver. Producer: `engine.derive`. `SC-CTX-0005` (ERROR, primary): a deriver failed.
Producer: `engine.derive`. `SC-CTX-0006` (ERROR, primary): a fold has a refused section. Producer:
`engine.check`. `SC-CTX-0007` (ERROR, primary): a required key is absent. Producer: `Focuses.context`
and `engine.derive`.

## 7. Tests

- `StructureContext`: sorted keys, typed get/require, empty, immutability.
- Publish: merge semantics, revision growth, `match` rejection, cumulative spend, latest findings,
  one writer per instance.
- Derive: order, requires, missing deriver, stale base, one batch, no partial publish.
- Merges: associativity for each built-in; `union` canonical.
- In-memory store: concurrent publish one winner; two instances never collide.

## 8. Dependencies

`core`, `space`, `finding`, `structure` (for `Computation`/`Plan` types used by the engine; this
module itself uses only `ContextKey`/`ContextView`/`Outcome`/`Budget`/`CancelToken`). It does not
import `engine`, `document`, `remedy` or `spi`.
