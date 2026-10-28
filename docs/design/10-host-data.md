# 10. Host snapshot and typed reads

> Status: proposed, not implemented. This document is the source of truth for the world snapshot,
> the typed host-read bridge, and MC-specific property/state/tag/fluid checks.

## 1. Purpose

The kernel describes what an occupant is: its type and kind. It must not model block states,
properties, tags, fluids, entity data, or any other host payload.

The capture is **one world snapshot** in the original MC shape, following
[CachedLevel.java](https://github.com/khjxiaogu/Beecrasy/blob/master/src/main/java/com/khjxiaogu/beecrasy/utils/CachedLevel.java):
copied `PalettedContainer<BlockState>` sections, fluids, block entities and entities. MBD2's
structure debugger works from a captured structure region in the same direction
([debugging docs](https://low-drag-mc.github.io/LowDragMC-Doc/en/multiblocked2/editor/debugging.html)).

Reads are a **typeclass bridge** over that snapshot:

```java
view.get(pos, BLOCK_STATE)
view.get(pos, FLUID_STATE)
view.get(pos, ENTITY)
```

There is no `BlockSnapshot`, `FluidSnapshot`, `EntitySnapshot`, `McSnapshot`, `HostData`, `HostTable`
or per-cell host map. One snapshot, many typed views.

## 2. HostType

`HostType<H>` is the type token:

```java
package dev.vfyjxf.cumulus.kernel.world;

public record HostType<H>(NamespacedId id) implements Comparable<HostType<?>> {

    public HostType {
        Objects.requireNonNull(id, "id");
    }

    public static <H> HostType<H> of(NamespacedId id) {
        return new HostType<>(id);
    }

    @Override
    public int compareTo(HostType<?> other) {
        return id.compareTo(other.id);
    }
}
```

- `H` is the read type; the kernel never names it;
- no `Class<?>`;
- one type token per field extension;
- the adapter owns the constants.

Adapter constants:

```java
public static final HostType<BlockState> BLOCK_STATE =
    HostType.of(id("block_state"));

public static final HostType<FluidState> FLUID_STATE =
    HostType.of(id("fluid_state"));

public static final HostType<Entity> ENTITY =
    HostType.of(id("entity"));

public static final HostType<BlockEntity> BLOCK_ENTITY =
    HostType.of(id("block_entity"));
```

## 3. WorldView bridge

`WorldView` gains one generic read method:

```java
public interface WorldView {

    default <H> @Nullable H get(LatticePoint pos, HostType<H> type) {
        return null;
    }

    default <H> @Nullable H get(OccupantRef ref, HostType<H> type) {
        return get(ref.anchor(), type);
    }
}
```

- one snapshot, typed reads;
- default `null`;
- `LevelSnapshot` overrides it and reads its world snapshot;
- never reads the live world;
- total: missing means "not in this snapshot", not an exception.

The method is a field extension, not a wrapper object:

```java
BlockState state = view.get(cell, BLOCK_STATE);
FluidState fluid = view.get(cell, FLUID_STATE);
Entity entity = view.get(cell, ENTITY);
BlockEntity blockEntity = view.get(cell, BLOCK_ENTITY);
```

## 4. The world snapshot

The adapter captures one immutable snapshot per frame, `CachedLevel`-shaped:

```java
final class SnapshotLevel implements BlockGetter {

    private final Cuboid bounds;
    private final PalettedContainer<BlockState>[] sections;
    private final PalettedContainer<FluidState>[] fluids;
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final List<Entity> entities;
    private final HeightProvider[] heightmaps;

    BlockState getBlockState(BlockPos pos);
    FluidState getFluidState(BlockPos pos);
    @Nullable BlockEntity getBlockEntity(BlockPos pos);
}
```

- local coordinates;
- copied sections, not per-cell maps;
- out-of-bounds reads return air / empty fluid;
- no live `Level` retained for reads;
- one snapshot, not one per data type.

`LevelSnapshot` holds the frame's `SnapshotLevel` and implements `WorldView.get`:

```java
@Override
public <H> @Nullable H get(LatticePoint pos, HostType<H> type) {
    return snapshot.get(pos, type);
}
```

The adapter's `SnapshotLevel.get` is the typeclass dispatcher:

```java
<H> @Nullable H get(LatticePoint pos, HostType<H> type) {
    if (type.equals(BLOCK_STATE)) {
        return (H) getBlockState(pos);
    }
    if (type.equals(FLUID_STATE)) {
        return (H) getFluidState(pos);
    }
    if (type.equals(ENTITY)) {
        return (H) entityAt(pos);
    }
    if (type.equals(BLOCK_ENTITY)) {
        return (H) getBlockEntity(pos);
    }
    return null;
}
```

A new field extension adds a `HostType` and one branch; no existing type changes.

## 5. Capture

The capture stays one section walk:

1. copy the relevant `PalettedContainer<BlockState>` sections;
2. copy fluid states;
3. copy block entities;
4. copy entities;
5. copy heightmaps when requested;
6. wrap in one `SnapshotLevel`.

The existing `OccupantSnapshot` capture stays as the kernel's type/kind/footprint view. It is not
extended with host data.

`FrameData`/`LevelSnapshot` carry the per-frame snapshot. Nested frames get their own snapshot.

No second read: `WorldView.get` reads the snapshot, never the live level.

## 6. Expectation bridge

The kernel provides one generic host expectation:

```java
public static <H> Expectation<OccupantSnapshot> host(
    HostType<H> type,
    BiPredicate<H, LatticePoint> test,
    FindingCode mismatchCode,
    String description
);
```

Semantics:

- `H host = ctx.view().get(subject.ref().anchor(), type)`;
- `null` -> `Unreadable` with `SC-MC-0010`;
- `test.test(host, cell)` true -> `Match`;
- false -> `Mismatch` with `mismatchCode` and `description`.

The predicate is supplied by the adapter. The kernel never constructs or interprets host data.

## 7. Adapter expectations

The JSON document has no separate MC check type. A normal check whose expectation kind is one of the
adapter factories compiles into an `Expectations.host` predicate.

### 7.1 Property

```json
{"kind": "property", "key": "lit", "value": "true"}
```

```java
Expectations.host(
    BLOCK_STATE,
    (state, cell) -> state.getValue(property).getName().equals(value),
    SC_MC_0006,
    "expected property ..."
)
```

### 7.2 Exact state

```json
{
  "kind": "state",
  "block": "minecraft:furnace",
  "properties": {"facing": "north", "lit": "true"}
}
```

The adapter resolves `block + properties` to a `BlockState` at compile time and compares the
snapshotted state directly.

### 7.3 Tag

```json
{"kind": "tag", "registry": "block", "id": "minecraft:logs"}
{"kind": "tag", "registry": "fluid", "id": "minecraft:water"}
{"kind": "tag", "registry": "entity", "id": "minecraft:undead"}
```

The predicate uses `TagKey` directly:

- block: `view.get(cell, BLOCK_STATE).is(tag)`;
- fluid: `view.get(cell, FLUID_STATE).is(tag)`;
- entity: the snapshotted entity type holder `is(tag)`.

No descriptor tags and no `anyOf` expansion.

### 7.4 Fluid

```json
{"kind": "fluid", "id": "minecraft:water"}
```

```java
BlockState block = view.get(cell, BLOCK_STATE);
FluidState fluid = view.get(cell, FLUID_STATE);

fluid.getType() == expected
    && block.getBlock() == fluid.createLegacyBlock().getBlock()
```

- real water matches;
- a waterlogged slab does not match;
- waterlogged is a block state: use `property("waterlogged", "true")` or `state`;
- lava and modded fluids use the MC fluid registry.

## 8. Anchor

The kernel `Anchors` and `StructureKernel.siting` are removed. The adapter resolves anchors from
the snapshot:

```java
public final class McAnchors {

    public static SitingResult resolve(
        Blueprint blueprint,
        LatticePoint origin,
        WorldView view
    ) {
        BlockState state = view.get(origin, BLOCK_STATE);
        if (state == null) {
            return failure(..., SC_MC_0010, "the anchor block state was not captured");
        }
        String value = state.getValue(property);
        if (!value.equalsIgnoreCase(spec.value())) {
            return failure(..., anchorInvalid, "anchor property does not equal the declared value");
        }
        ...
    }
}
```

- `AnchorSpec` and `OrientationPolicy` remain kernel declarations;
- `AnchorSpec.value` becomes a real constraint;
- the anchor cell is captured with the same world snapshot;
- no live world read.

## 9. Descriptor

`OccupantDescriptor` becomes:

```java
(type, kind)
```

Removed:

- `properties`;
- `tags`;
- `property(...)`.

`OccupantRef` remains the occupant identity. `OccupantFilter` still uses type/kind.

`Expectations.property` is removed from the kernel; the adapter registers the `property` factory.

## 10. Digest

`SectionSnapshot.contentDigest` is adapter-computed from the snapshot. It includes:

- occupant refs;
- descriptor type/kind;
- the snapshot's block/fluid/entity content for the section.

Test:

```text
same type, different BlockState
  -> different contentDigest
```

## 11. Codes

Adapter codes, not in `Codes.all`:

| Code | Meaning |
| --- | --- |
| `SC-MC-0006` | property mismatch |
| `SC-MC-0007` | exact state mismatch |
| `SC-MC-0008` | tag mismatch |
| `SC-MC-0009` | fluid mismatch |
| `SC-MC-0010` | host snapshot value was not captured |

`SC-MC-0010` is `Unreadable`, never a mismatch. `SC-EXP-0001` remains for kernel leaves.

## 12. JSON

- no `mc_checks`;
- no separate JSON type;
- normal `checks` use adapter expectation kinds;
- adapter catalogs register `property`, `state`, `tag`, `fluid`;
- the kernel document reader stays blueprint-only.

## 13. Non-goals

- no `McSnapshot` union;
- no `BlockSnapshot`/`FluidSnapshot`/`EntitySnapshot` split;
- no `HostData` interface;
- no `HostView` wrapper;
- no `McOccupant` union;
- no per-cell `HostTable`;
- no `Class<?>` tokens;
- no trait reuse for block/fluid/entity data;
- no live world read in `WorldView.get`;
- no MC type in the kernel;
- no property map or tag list in `OccupantDescriptor`.

## 14. Impact

Kernel:

- `OccupantDescriptor` becomes `(type, kind)`;
- `Expectations.property` is removed;
- `Anchors` and `StructureKernel.siting` are removed;
- `WorldView` gains `get(pos, type)`;
- `Expectations.host` is added;
- `Expectations.Property` is removed.

Adapter:

- one `SnapshotLevel` per frame;
- `HostType` constants for block state, fluid state, entity, block entity;
- `LevelCapture`/`FrameData`/`LevelSnapshot` snapshot wiring;
- `McAnchors`;
- adapter expectation factories;
- `SC-MC-0006..0010`;
- digest update.

Tests/docs:

- descriptor tests remove properties/tags;
- anchor tests move to adapter;
- `SitedDescriptorGameTest`/`WorldTest` migrate;
- `DocumentCompilerTest` property checks move to adapter catalogs;
- snapshot read/digest tests;
- waterlogged distinction tests;
- JSON/Java parity for property/state/tag/fluid.

## 15. Implementation slices

| Slice | Scope | Gate |
| --- | --- | --- |
| H1 | `HostType`, `WorldView.get`, `Expectations.host`; descriptor `(type, kind)`; remove property/anchors | focused kernel tests |
| H2 | `SnapshotLevel`, capture wiring, digest | focused adapter capture tests |
| H3 | adapter `property`/`state`/`tag`/`fluid` factories, `McAnchors`, `SC-MC-0006..0010`, JSON/Java parity | focused adapter tests |
| H4 | docs, counts, stress scenarios | focused regression |

Visual review stays last.
