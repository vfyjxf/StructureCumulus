# 12. Structure instances and the manager

> Status: implemented. Replaces the detached `SavedContexts` map with a host-driven structure
> manager.

## 1. Problem

The kernel has `StructureContext` and `ContextStore`, but no structure object:

- `StructureContext` is published into a `ContextStore` keyed by `InstanceId`;
- the adapter's `SavedContexts` persists that map in world `SavedData`;
- `nextSerial` is an allocator watermark, and the map has no automatic lifecycle;
- no host path reads a restored context: `check` cannot read instance context, no `derive` command
  exists, and the adapter never implements `CachedContext`;
- claims are per-cell rows, not a structure aggregate, so there is nothing that owns the context.

The result is a write-mostly side table: it grows with every formed instance, needs manual
`remove`, and adds serial/persistence machinery for data that is normally derived from the blueprint,
siting and world snapshot. A host cannot say "this structure was destroyed" and have the library
follow.

The library should own the identity/context state machine, not the world lifecycle. The host knows
when a structure is formed, broken, moved or unloaded; it dispatches that as lifecycle events. The
host also decides whether anything is persisted.

## 2. Shape

All types are kernel-side and Minecraft-free.

```java
package dev.vfyjxf.cumulus.kernel.context;

public record StructureSpec(
    OwnerId owner,
    Siting siting,
    NamespacedId structure
) {
    public StructureSpec { /* non-null */ }
}

public record StructureInstance(
    InstanceId id,
    StructureSpec spec,
    StructureContext context,
    CuboidSet bounds
) {
    public StructureInstance { /* context.instance().equals(id), bounds non-null */ }
    public OwnerId owner() { return spec.owner(); }
    public Siting siting() { return spec.siting(); }
}

public interface StructureAttempt extends ContextStore {
    InstanceId id();
    StructureSpec spec();
    StructureInstance commit(CuboidSet bounds);
    void abort();
}

public interface StructureManager extends ContextStore {

    StructureAttempt begin(World world, StructureSpec spec);

    @Nullable StructureInstance find(InstanceId id);

    @Nullable StructureInstance find(World world, OwnerId owner, Siting siting);

    /** Answers the tuple only when exactly one world holds it; ambiguous tuples throw. */
    default @Nullable StructureInstance find(OwnerId owner, Siting siting) { /* ... */ }

    List<StructureInstance> instances();

    List<StructureInstance> findAt(LatticePoint worldCell);

    /** Every instance whose bounds intersect the world cells. */
    default List<StructureInstance> findIn(CuboidSet worldCells) { /* ... */ }

    void onEvent(StructureEvent event);
}
```

`StructureEvent` is the host's lifecycle input:

```java
public sealed interface StructureEvent {

    record Broken(InstanceId id) implements StructureEvent {}

    record Loaded(World world, List<StructureInstance> instances) implements StructureEvent {}

    record Unloaded(World world) implements StructureEvent {}

    record Invalidated(InstanceId id, String reason) implements StructureEvent {}
}
```

`begin`/`commit` is the only creation path; `Loaded` is the only bulk restore path. There is no
separate `Formed` event to keep two creation paths in sync.

## 3. Lifecycle

The host owns detection; the manager owns the registry and context state.

| Step | Host action | Manager action |
| --- | --- | --- |
| attempt | `begin(world, spec)` | allocates `InstanceId`, reserves `(world, owner, siting)`, returns a draft `ContextStore` |
| settle | `MatchEngine.settle(..., attempt)` | publishes values into the draft context |
| success | `attempt.commit(bounds)` | moves the draft into `instances`, returns the immutable `StructureInstance` |
| failure | `attempt.abort()` | discards the draft and its context |
| destroyed | `onEvent(new Broken(id))` | removes the instance and its context |
| unloaded | `onEvent(new Unloaded(world))` | clears that world's in-memory instances and drafts; claim rows stay |
| loaded | `onEvent(new Loaded(world, restored))` | validates, then replaces only that world's instances with the restored set |
| corrupt | `onEvent(new Invalidated(id, reason))` | removes the instance and context |

Laws:

- one formed instance per `(world, owner, siting)`; a second draft for that tuple is rejected; a
  `commit` replaces an already formed instance at that tuple in the same world. The same
  `(owner, siting)` in another world is a different instance;
- `commit` and `abort` are one-shot; a second call is an error;
- `ContextStore.remove` on a draft closes it: the reservation is released, it returns the old
  context, and later `publish`/`remove`/`commit`/`abort` throw `IllegalStateException`; no instance
  is formed. `remove` on a formed instance removes the instance and returns its context;
- `Broken`/`Invalidated` are idempotent (an unknown id is a no-op); on a draft they close it;
- `Unloaded` and `Loaded` are per `World`; `Loaded` is the only bulk replace and touches only the
  named world. It validates every restored id/siting and world membership before mutating, so a
  rejected `Loaded` leaves the registry unchanged; existing drafts of that world are closed;
- a world unload drops in-memory instances and drafts only. Persisted claim rows are not released,
  moved or rewritten by the manager; removal and move release claims, unload does not (D-38);
- no automatic block-break detection, no chunk listening, no world save: the adapter does that and
  calls the manager.

`findAt`, `findIn` and `instances` are **registry-wide**: they take no `World` and see every formed
instance in every world held by that manager. `find(World, owner, siting)` is the only world-qualified
tuple lookup. The adapter keeps one manager per level, so its registry is one world and those
lookups are effectively per-level; a host that shares one manager across worlds must filter by
`instance.id().world()` or use `find(World, ...)`. This is the documented multi-world lookup
boundary, not a hidden world argument.

The manager's serial is session-scoped and shared across worlds. `Loaded` raises the watermark to
`max(restored serial)+1`; `Unloaded` drops only that world. If a host persists instances, it must
also persist its own ordering or use a host-side UUID; the library does not write `nextSerial` to
disk.

## 3.1 Lifecycle detection

The host supplies the world-frame `bounds`; the manager never interprets them. The adapter's
`WatchEvents` listener is the detector:

- a player break or explosion inside `bounds` dispatches `Broken`;
- a block place inside `bounds` dispatches `Invalidated` (the structure no longer matches its
  captured cells);
- a piston that moves any claimed cell, or destroys a bound cell, dispatches `Invalidated` and
  releases the claim rows: a pushed structure is gone, not translated;
- a level unload dispatches `Unloaded(world)` and drops that world's manager state together with
  its claim ledger (D-44);
- a host that restores its own instances dispatches `Loaded(world, instances)`.

No chunk listener is needed: a structure spans chunks and is not removed by a chunk unload. The
adapter does not persist instances; `Loaded` exists for a host that does.

## 4. Context ownership

- `StructureInstance.context` is the instance's latest `StructureContext`; the manager replaces the
  whole record on publish, so a reader never sees a half-updated context.
- `StructureAttempt` is a `ContextStore`: a draft can publish and read only its own instance.
  `commit` is the only transfer from draft to registry. `remove(draft.id())` closes the draft and
  releases its reservation; a removed attempt cannot publish, commit or abort afterwards.
- `StructureManager` is also a `ContextStore` for formed instances; `derive` and future check-time
  reads use it. Publishing to a formed instance updates its `StructureInstance.context` atomically.
- `StructureContext` stays immutable; no mutable context object, no second value model.
- The manager does not know how to encode values. If the host persists a context, it owns the
  codec and the NBT/attachment format.

## 5. Persistence

The library manager is **in-memory by default**:

```text
world load -> empty manager (Loaded may restore one world)
settle      -> draft -> commit
world unload-> empty manager for that world; claim rows stay
```

This is the default because `StructureContext` values are derived from the blueprint, siting and
snapshot and can be recomputed. The adapter's `SavedContexts`, `nextSerial`, `SavedContextsLifecycleTest`
and the context NBT format are removed.

A host that needs durable non-derived values may persist `manager.instances()` and restore each
world through `Loaded(world, restored)`; it must supply its own codecs. That is an optional host
concern, not a library `SavedData`.
If the host persists nothing, the serial is only unique within the session; that is acceptable because
no instance id survives the session.

## 6. API changes

- `Host.contexts()` becomes `Host.structures()` (a `StructureManager`); the manager implements
  `ContextStore` for engine calls.
- `StructureKernel.settle(request, admitted)` keeps using the host manager for formed instances;
  a new `settle(request, admitted, StructureAttempt)` overload publishes the draft.
- `StructureKernel.derive(formed, derivations)` reads the context store supplied by the host; a caller
  that formed the instance through a non-host manager must derive against that manager.
- The adapter's `ServerState` owns one `StructureManager` per level; `SetCommand` and
  `StructureSetHost` call `begin`, `commit` and `Broken` instead of `allocateSerial` and
  `contexts().remove`. Rollback in `StructureSetHost` is `StructureEvent.Broken` through that bound
  manager, not `ContextStore.remove`. The manager has `find(World, owner, siting)`; the two-argument
  `find` throws when the tuple is present in more than one world.
- Set-host rollback restores snapshotted old claim rows but cannot recreate a prior
  `StructureInstance`/context already replaced by an earlier commit in the same run. Member commits
  run only after every settle succeeds, so a settle failure keeps the old set; a commit-phase failure
  is reachable only through a host-contract violation (draft committed into another manager, or a
  manager rejecting a valid commit). That is a documented limitation, not atomic replace.
- `StructureEvent.Loaded(World, List<StructureInstance>)` replaces one world's instances; the old
  world-less `Loaded(List)` shape is not supported.
- `SavedContexts`, `SavedContextsLifecycleTest`, `allocateSerial` and `ContextStore.inMemory()`'s
  global map are removed or demoted to test fixtures. `ContextStore` remains the narrow
  publish/read/remove interface used by `StructureAttempt` and the manager.

## 7. Tests

- `StructureManagerTest` (9): begin/commit/abort; duplicate world-scoped draft tuple; commit
  replacement; moved id/context and collision; broken/invalidated; unload one world; per-world
  `Loaded` validation and identity; world-qualified `find` and ambiguous tuple; publish after commit.
- `AdversarialManagerTest` (41): one-shot close/remove, attempt-remove close, per-world reservations
  and `Loaded`, rejected-restore atomicity, draft invalidation by events, bounds lookup, context
  merge/validation and closed-attempt write rejection.
- `StructureSetHostTest`/`StructureSetHostGameTest`: rollback dispatches `Broken`; a formed set has
  two instances; a failed member has no instance/context.
- `ContextStore` tests remain for the narrow interface; `SavedContextsLifecycleTest` is deleted.
- Counts are not pinned here; `06-verification.md` carries the current totals.

## 8. Non-goals

- No block-break/chunk event listeners in the kernel; the adapter host detects and dispatches.
- No persistence format in the library; no `SavedData` replacement.
- No check-time persisted-context read in this round; `CachedContext` stays a host port.
- No auto-build and no preview.
- No changes to claim identity, same-siting rejection, or `InstanceId`'s `(World, long)` shape.

## 9. Accepted scope

- `SavedContexts`, `ContextCodecs`, `FindingCodec` and their tests are deleted; the adapter manager is
  in-memory.
- The adapter wires `begin`/`commit`/`abort`, `Broken`, `Invalidated`, `Unloaded` and `Loaded`
  through `WatchEvents`; `Loaded` is host-driven because the adapter has no persisted
  instances to restore.

## 10. Nested extent access (design 13, approved split)

The approved nested-extent seam is no longer one capability-only accessor. It is split by source in
`docs/design/13-nested-extent-access.md`:

- **Capability source:** `CapabilityNestedExtentAccess<H, C>` receives the capability's own `C`
  (for example `@Nullable Direction`) and forwards it to `BaseCapability.getCapability`; there is no
  new read/context record and no wrapper around `C`. `CapabilityNestedExtentProvider<H, C>` binds one
  holder id, one capability and one access, and `CapabilityNestedExtentProviders.of(...)` is the
  registration factory.
- **Attachment source:** `AttachmentNestedExtentProvider<H, T>` mirrors `IAttachmentHolder`:
  `hasData`, `getExistingDataOrNull`, `getData`, `setData`, `removeData`. Capture calls only
  `getExistingDataOrNull`; it never creates a default attachment, writes data or dirties a block
  entity. `readOnly`/`readWrite` factories supply the reader and optional writer; the host owns the
  attachment type and holder id.
- **Lookup:** `NestedExtentLookup` keeps two registries keyed by holder id and exposes explicit
  `getCapability`/`getAttachment`, plus a capability-first `get` used by capture. Duplicate holder ids
  are rejected within each source; a capability and an attachment may share a holder id, and `knows`
  answers either source.
- **Capture is read-only:** `LevelCapture` passes the trait capture's capability side to the
  capability source and uses the capability-first lookup; the attachment fallback reads existing data
  only. No write path is added to capture.
- The kernel remains Minecraft-free; all of this lives in the adapter's `mc.capability` package and
  the nested extent itself is still only a declaration validated by `LevelCapture`.
