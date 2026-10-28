# 08. Context cache extension

> Status: implemented (D-31). This document is the source of truth for the cached-context seam; the module
> files record the same contract.

## 1. Purpose

Structural checks normally compute all context in the current attempt:

```text
section computations -> aggregate computations -> checks -> discard
```

That is the default and remains the default. Some hosts have a reason to reuse a value that was
computed by an earlier settle/derive: a large aggregate, a structure tier, a capacity, or a value
that cannot be derived from the current capture alone.

This document defines one optional extension point:

```text
MatchRequest.cachedContext
```

The kernel does not cache, serialize, stamp, or invalidate anything. The host owns the cache and
all of its freshness rules.

## 2. Problem

`ContextKey` is:

```text
id + type + retention + merge + whenAbsent
```

`merge` and `whenAbsent` describe how a value is produced; they are not part of the value's
identity. A host cache normally stores only:

```text
(id, type) -> value
```

If the cached context were a `ContextView` keyed by the full `ContextKey`, a host would have to
reconstruct producer-only fields such as `merge` to answer a lookup. That is both unnatural and
fragile: the existing persistence path already decodes instance keys without those fields, so a
blueprint key with `Merges.sum` would not match its own cached value.

A second gap: cached values must be visible to aggregate computations, not only to checks. An
aggregate may declare an instance key in `Computation.inputs()`; if only check
`ExpectationMeta.requires()` is considered, the aggregate can never read the prior structure
context.

## 3. Decision

Add a value-cache port:

```java
package dev.vfyjxf.cumulus.kernel.context;

public interface CachedContext {

    <T> @Nullable T get(NamespacedId id, Class<T> type);
}
```

Add one optional field to the match request:

```java
public record MatchRequest(
    Blueprint blueprint,
    Siting siting,
    SnapshotLease lease,
    InstanceId instance,
    OwnerId owner,
    Budget budget,
    @Nullable ClaimView claims,
    CancelToken cancel,
    @Nullable CachedContext cachedContext
) { }
```

The existing constructors and `of` factories keep their signatures and default the field to
`null`.

### 3.1 Eligible keys

A cached value may be read only when all of the following hold:

1. The key is `Retention.instance`.
2. The key id is explicitly declared as an instance dependency of the current plan:
   - a check `ExpectationMeta.requires()` entry, or
   - a section/aggregate `Computation.inputs()` entry.
3. The cached value's type matches the declared `ContextKey.type()`.

`Retention.match` keys are never read from the cache.

The eligibility set is the attempt-level union of those two dependency sources. It is computed
once from the blueprint plan before the section pass; it does not grow or shrink during the
attempt.

### 3.2 Lookup order

For a `ContextView.get(key)` on the attempt context:

1. Current attempt value with the exact `ContextKey` wins.
2. Otherwise, if a current attempt value exists with the same `(id, type, Retention.instance)`,
   that value wins. This makes the current attempt shadow the cache even when the producer's
   key object differs in `merge` or `whenAbsent`.
3. Otherwise, if the key is eligible, ask `cachedContext.get(key.id(), key.type())`.
4. Otherwise the key is absent.

`ContextView.require`, `has`, and `keys` follow the same view. `keys()` enumerates current-attempt
keys; cached ids are not enumerable because `CachedContext` is a get-only port.

### 3.3 Visibility

The same composite view is used by:

- every `FocusContext.context()` in the attempt;
- `AggregateContext.context()`.

It is not used by:

- `SectionContext` (section computations have no context read by design);
- `MatchOutcome.values` (current attempt values only);
- `settle` publication (current attempt instance values only);
- `derive` (it reads the host's `ContextStore`, not `MatchRequest.cachedContext`).

Cached values are never published by `settle` or `derive`.

### 3.4 Missing and invalid values

- No cached value and no current value: the key is absent; a check that requires it reports
  `SC-CTX-0007`.
- A host value of the wrong type is a host contract violation. The kernel treats the lookup as
  absent rather than guessing a conversion.
- A host exception from `get` is a host defect; the kernel treats the lookup as absent and does not
  fail the attempt with a hidden host exception.

### 3.5 Build rule

A check may require an instance key with no computation producer; the runtime absence is
`SC-CTX-0007`. A check that requires a `Retention.match` key still needs a computation producer
(`SC-CHK-0002`).

## 4. Host contract

The host owns:

- what is cached;
- when it is written;
- when it is invalidated (block changes, blueprint definition changes, structure moves, reload);
- whether it is persisted and with which codec;
- how it is keyed to a structure (owner, blueprint, siting, instance, etc.);
- thread safety and the server-thread rule.

The kernel only defines the read seam and the eligibility/precedence rules above.

A host that decodes a previously published `StructureContext` may fill `CachedContext` by
`(id, type)`; it does not need `merge` or `whenAbsent`.

## 5. Examples

### GT EBF

The blueprint declares:

```text
casingCount = ContextKey<Long>(id=casing_count, type=Long,
                              retention=instance, merge=sum, whenAbsent=null)
coilTier    = ContextKey<Integer>(id=coil_tier, type=Integer, retention=instance)
```

The host caches:

```text
(casing_count, Long)    -> 16L
(coil_tier, Integer)    -> 2
```

A later check requires `casingCount`; the aggregate declares `coilTier` in `inputs()`. Both are
eligible. The current attempt may recompute `casingCount`; that current value shadows the cached
16. If the aggregate does not recompute `coilTier`, it reads the cached 2.

### PNC refinery

The host caches `outputCount` from the last settle. A later check requires `outputCount`; it reads
the cached value until the host invalidates it after an output is added.

### No cache

`MatchRequest.of(...)` with the existing arguments leaves `cachedContext` null. The attempt behaves
exactly as before: only current section/aggregate values are visible.

## 6. Non-goals

- No automatic read from `ContextStore`.
- No kernel persistence or serialization of context values.
- No structure stamp, definition digest, or content digest.
- No `Retention` split beyond the existing `match` / `instance`.
- No per-key TTL, eviction, or cache ownership inside the kernel.
- No cache of `Retention.match` values.
- No change to `ContextStore`, `SavedContexts`, or the context wire format.

## 7. Affected modules

| Module | Change |
| --- | --- |
| `kernel.context` | new `CachedContext` port |
| `kernel.engine` | `MatchRequest` field, attempt composite view, eligibility set, lookup order, missing semantics |
| `kernel.structure` | build rule for instance requirements without a producer |
| docs | this document, `engine.md`, `context.md`, `structure.md` |

## 8. Test obligations

- Host-decoded cached key with `merge`/`whenAbsent` answers a blueprint key by `(id, type)`.
- Aggregate input-only instance key is cache-eligible and visible through `AggregateContext`.
- Current attempt value shadows cached, including when the current key object differs in
  `merge`/`whenAbsent`.
- `Retention.match` key is never read from cached.
- Missing cached key reports `SC-CTX-0007`.
- Wrong-type cached value is treated as absent.
- No cache means behavior is unchanged.
- Cached values are absent from `MatchOutcome.values` and are not published by settle/derive.
- Determinism: the same request and cache produce the same result and finding order.
- Build rule: instance requirement without producer builds; match requirement without producer is
  `SC-CHK-0002`.

## 9. Open implementation notes

- `CachedContext` lives in `kernel.context`; it is a host port, not a `ContextView`.
- The composite view is package-private in `kernel.engine`; no public context-view type is added.
- `keys()` returns current-attempt keys only; cached ids are not enumerable.
- `MatchRequest` keeps the existing constructor/`of` overloads and adds cache-aware overloads; no
  existing call site changes.
- The eligibility set is derived from `Blueprint.plan()` and is stable for the attempt.
