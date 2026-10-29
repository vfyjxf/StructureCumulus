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

    @Nullable StructureInstance find(OwnerId owner, Siting siting);

    List<StructureInstance> instances();

    List<StructureInstance> findAt(LatticePoint worldCell);

    void onEvent(StructureEvent event);
}
```

`StructureEvent` is the host's lifecycle input:

```java
public sealed interface StructureEvent {

    record Broken(InstanceId id) implements StructureEvent {}

    record Moved(InstanceId id, Siting from, Siting to, CuboidSet bounds) implements StructureEvent {}

    record Loaded(List<StructureInstance> instances) implements StructureEvent {}

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
| attempt | `begin(world, spec)` | allocates `InstanceId`, reserves `(owner, siting)`, returns a draft `ContextStore` |
| settle | `MatchEngine.settle(..., attempt)` | publishes values into the draft context |
| success | `attempt.commit(bounds)` | moves the draft into `instances`, returns the immutable `StructureInstance` |
| failure | `attempt.abort()` | discards the draft and its context |
| destroyed | `onEvent(new Broken(id))` | removes the instance and its context |
| moved | `onEvent(new Moved(id, from, to, bounds))` | updates the spec siting and world bounds; rejects a collision |
| unloaded | `onEvent(new Unloaded(world))` | clears all in-memory instances for that world |
| loaded | `onEvent(new Loaded(restored))` | replaces the world's instances with the host's restored set |
| corrupt | `onEvent(new Invalidated(id, reason))` | removes the instance and context |

Laws:

- one formed instance per `(owner, siting)`; a second draft at the same siting is rejected; a
  `commit` replaces an already formed instance at that siting; a `Moved` collision is rejected;
- `commit` and `abort` are one-shot; a second call is an error;
- `Moved` preserves the `InstanceId` and the context; only the siting changes;
- `Broken`/`Invalidated` are idempotent (an unknown id is a no-op);
- `Unloaded` and `Loaded` are per `World`; `Loaded` is the only bulk replace;
- no automatic block-break detection, no chunk listening, no world save: the adapter does that and
  calls the manager.

The manager's serial is session-scoped. `Loaded` raises the watermark to `max(restored serial)+1`;
`Unloaded` drops the registry. If a host persists instances, it must also persist its own ordering or
use a host-side UUID; the library does not write `nextSerial` to disk.

## 3.1 Lifecycle detection

The host supplies the world-frame `bounds`; the manager never interprets them. The adapter's
`WatchEvents` listener is the detector:

- a player break or explosion inside `bounds` dispatches `Broken`;
- a block place inside `bounds` dispatches `Invalidated` (the structure no longer matches its
  captured cells);
- a piston that moves only part of the structure, or destroys a bound cell, dispatches `Invalidated`;
- a piston that moves every claimed non-air cell by one direction dispatches `Moved` with the
  translated bounds and siting;
- a level unload dispatches `Unloaded(world)`;
- a host that restores its own instances dispatches `Loaded(instances)`.

No chunk listener is needed: a structure spans chunks and is not removed by a chunk unload. The
adapter does not persist instances; `Loaded` exists for a host that does.

## 4. Context ownership

- `StructureInstance.context` is the instance's latest `StructureContext`; the manager replaces the
  whole record on publish, so a reader never sees a half-updated context.
- `StructureAttempt` is a `ContextStore`: a draft can publish and read only its own instance.
  `commit` is the only transfer from draft to registry.
- `StructureManager` is also a `ContextStore` for formed instances; `derive` and future check-time
  reads use it. Publishing to a formed instance updates its `StructureInstance.context` atomically.
- `StructureContext` stays immutable; no mutable context object, no second value model.
- The manager does not know how to encode values. If the host persists a context, it owns the
  codec and the NBT/attachment format.

## 5. Persistence

The library manager is **in-memory by default**:

```text
world load -> empty manager
settle      -> draft -> commit
world unload-> empty manager
```

This is the default because `StructureContext` values are derived from the blueprint, siting and
snapshot and can be recomputed. The adapter's `SavedContexts`, `nextSerial`, `SavedContextsLifecycleTest`
and the context NBT format are removed.

A host that needs durable non-derived values may persist `manager.instances()` and restore through
`Loaded`; it must supply its own codecs. That is an optional host concern, not a library `SavedData`.
If the host persists nothing, the serial is only unique within the session; that is acceptable because
no instance id survives the session.

## 6. API changes

- `Host.contexts()` becomes `Host.structures()` (a `StructureManager`); the manager implements
  `ContextStore` for engine calls.
- `StructureKernel.settle(request, admitted)` keeps using the host manager for formed instances;
  a new `settle(request, admitted, StructureAttempt)` overload publishes the draft.
- `StructureKernel.derive(formed, derivations)` uses `manager.context(formed.instance())`.
- The adapter's `ServerState` owns one `StructureManager` per level; `SetCommand` and
  `StructureSetHost` call `begin`, `commit` and `Broken` instead of `allocateSerial` and
  `contexts().remove`.
- `SavedContexts`, `SavedContextsLifecycleTest`, `allocateSerial` and `ContextStore.inMemory()`'s
  global map are removed or demoted to test fixtures. `ContextStore` remains the narrow
  publish/read/remove interface used by `StructureAttempt` and the manager.

## 7. Tests

- `StructureManagerTest`: begin/commit/abort; duplicate `(owner, siting)`; duplicate commit/abort;
  broken removes; moved preserves id/context and rejects collision; unloaded clears; loaded replaces
  and raises the serial; unknown broken/invalidated is a no-op; publish after commit updates the
  instance.
- `StructureAttemptTest`: draft isolation, publish/read, abort drops, commit transfers, no leak after
  abort.
- `StructureSetHostTest`/`StructureSetHostGameTest`: rollback dispatches `Broken`; a formed set has
  two instances; a failed member has no instance/context.
- `ContextStore` tests remain for the narrow interface; `SavedContextsLifecycleTest` is deleted.
- Counts are expected to change: adapter tests down, kernel tests up; this document does not pin the
  final numbers.

## 8. Non-goals

- No block-break/chunk event listeners in the kernel; the adapter host detects and dispatches.
- No persistence format in the library; no `SavedData` replacement.
- No check-time persisted-context read in this round; `CachedContext` stays a host port.
- No auto-build and no preview.
- No changes to claim identity, same-siting rejection, or `InstanceId`'s `(World, long)` shape.

## 9. Accepted scope

- `SavedContexts`, `ContextCodecs`, `FindingCodec` and their tests are deleted; the adapter manager is
  in-memory.
- The adapter wires `begin`/`commit`/`abort`, `Broken`, `Invalidated`, `Moved`, `Unloaded` and
  `Loaded` through `WatchEvents`; `Loaded` is host-driven because the adapter has no persisted
  instances to restore.
