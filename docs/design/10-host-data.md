# 10. Host snapshot and typed reads

> Status: H1–H3 implemented; H4 proposed. This document is the source of truth for the world
> snapshot, the typed host-read bridge, and MC-specific property/state/tag/fluid checks.

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
view.get(pos, HostTypes.blockState)
view.get(pos, HostTypes.fluidState)
view.get(pos, HostTypes.entity)
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
public static final HostType<BlockState> blockState = HostType.of(id("block_state"));
public static final HostType<FluidState> fluidState = HostType.of(id("fluid_state"));
public static final HostType<Entity> entity = HostType.of(id("entity"));
public static final HostType<BlockEntity> blockEntity = HostType.of(id("block_entity"));
```

The constants live in the adapter's `HostTypes`; the lowerCamel names follow the project's
static-field style.

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
BlockState state = view.get(cell, HostTypes.blockState);
FluidState fluid = view.get(cell, HostTypes.fluidState);
Entity entity = view.get(cell, HostTypes.entity);
BlockEntity blockEntity = view.get(cell, HostTypes.blockEntity);
```

## 4. The world snapshot

The adapter captures one immutable snapshot per frame, `CachedLevel`-shaped:

```java
final class SnapshotLevel implements BlockGetter {

    private final LatticePoint origin;
    private final Cuboid bounds;
    private final Map<Long, PalettedContainer<BlockState>> sections;
    private final Map<Long, FluidState[]> fluids;
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final List<CapturedEntity> entities;

    BlockState getBlockState(BlockPos pos);
    FluidState getFluidState(BlockPos pos);
    @Nullable BlockEntity getBlockEntity(BlockPos pos);
    <H> @Nullable H get(LatticePoint pos, HostType<H> type);
}
```

- local coordinates, with `origin` translating a section to world space;
- copied sections, not per-cell maps; section keys are world section coordinates;
- fluids are one 16³ `FluidState` array per copied section, read from the copied block states;
- block entities are detached copies; entities keep their captured footprint;
- out-of-bounds reads return air / empty fluid;
- no live `Level` retained for reads;
- one snapshot, not one per data type;
- heightmaps are absent until a request path exists (H2 adds none).

`CapturedEntity` is the adapter's internal record: entity UUID, type, local footprint and the captured
entity reference.

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
    if (type.equals(HostTypes.blockState)) {
        return (H) getBlockState(pos);
    }
    if (type.equals(HostTypes.fluidState)) {
        return (H) getFluidState(pos);
    }
    if (type.equals(HostTypes.entity)) {
        return (H) entityAt(pos);
    }
    if (type.equals(HostTypes.blockEntity)) {
        return (H) getBlockEntity(pos);
    }
    return null;
}
```

A new field extension adds a `HostType` and one branch; no existing type changes.

## 5. Capture

H2 implements the capture as one section walk:

1. for each non-air world section the frame walk touches, copy its `PalettedContainer<BlockState>`
   once (`copy()`), keyed by the world section coordinates;
2. fill that section's 16³ `FluidState` array from the copied block states;
3. copy each block entity as a detached snapshot block entity (`saveWithId` / `loadStatic`) keyed by
   the local cell;
4. keep the captured entity candidates with their local footprints;
5. wrap the frame's maps in one `SnapshotLevel`.

The occupant walk reads each cell's `BlockState` from the copied container, not the live chunk
section, so a cell is read from the world once. The existing `OccupantSnapshot` capture stays as the
kernel's type/kind/footprint view. It is not extended with host data.

`FrameData`/`LevelSnapshot` carry the per-frame snapshot. Nested frames get their own snapshot.

No second read: `WorldView.get` reads the snapshot, never the live level.

## 6. Expectation bridge

The kernel provides one generic host expectation:

```java
public static <H> Expectation<OccupantSnapshot> host(
    HostType<H> type,
    BiPredicate<H, LatticePoint> test,
    FindingCode mismatchCode,
    FindingCode absentCode,
    String description
);
```

Semantics:

- `H host = ctx.view().get(subject.ref().anchor(), type)`;
- `null` -> `Unreadable` with `absentCode`, the caller's code;
- `test.test(host, cell)` true -> `Match`;
- false -> `Mismatch` with `mismatchCode` and `description`.

The predicate and both codes are supplied by the adapter. The kernel mints no MC code: it never
declares `SC-MC-0010` and never interprets host data. The adapter passes its `SC-MC-0010` as
`absentCode`.

## 7. Adapter expectations

The JSON document has no separate MC check type. A normal check whose expectation kind is one of the
adapter factories compiles into an `Expectations.host` predicate. The adapter registers `property`,
`state`, `tag` and `fluid` in its expectation catalog; the kernel document reader stays blueprint-only
and its built-in expectation catalog has none of the four.

### 7.1 Property

```json
{"kind": "property", "key": "lit", "value": "true"}
```

```java
Expectations.host(
    blockState,
    (state, cell) -> state.getValue(property).getName().equals(value),
    SC_MC_0006,
    SC_MC_0010,
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

- block: `view.get(cell, HostTypes.blockState).is(tag)`;
- fluid: `view.get(cell, HostTypes.fluidState).is(tag)`;
- entity: the snapshotted entity type holder `is(tag)`.

No descriptor tags and no `anyOf` expansion.

### 7.4 Fluid

```json
{"kind": "fluid", "id": "minecraft:water"}
```

```java
BlockState block = view.get(cell, HostTypes.blockState);
FluidState fluid = view.get(cell, HostTypes.fluidState);

fluid.getType() == expected
    && block.getBlock() == fluid.createLegacyBlock().getBlock()
```

- real water matches;
- a waterlogged slab does not match;
- waterlogged is a block state: use `property("waterlogged", "true")` or `state`;
- lava and modded fluids use the MC fluid registry.

The adapter expresses the two conditions as two `Expectations.host` leaves under one `all_of`: the
captured `FLUID_STATE`'s type is the expected fluid, and the captured `BLOCK_STATE`'s block is that
cell's own legacy fluid block. Both leaves carry `SC-MC-0009` as the mismatch code and `SC-MC-0010` as
the absent code.

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
        BlockState state = view.get(origin, HostTypes.blockState);
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

H2 implements the snapshot read. The phase-1 command capture supplies the anchor cell's view, so
`McAnchors` reads the captured `BlockState`; a missing state is the adapter's `SC-MC-0001`, not a
live read. H3 implements the declared-value comparison: `AnchorSpec.value` is compared
case-insensitively against the captured property value at the definition origin, and a mismatch is
`Codes.anchorInvalid` (`SC-BPT-0006`). The `SC-MC-0010` catalog entry is the absent code of the host
expectations, not the anchor's missing-state code.

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

H2 implements the composition: `SnapshotLevel.contentDigest(region)` hashes each captured block
state, fluid state and entity that intersects the section, and `LevelCapture` combines that value
with the occupant digest. The same occupant type with a different `BlockState` therefore changes the
section digest.

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

`SC-MC-0010` is `Unreadable`, never a mismatch. It is the adapter's code, passed as the `absentCode`
of `Expectations.host`; the kernel never mints it. `SC-EXP-0001` remains for kernel leaves.

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
| H3 | adapter `property`/`state`/`tag`/`fluid` factories, `McAnchors`, `SC-MC-0006..0010` (the absent code passed to `Expectations.host`), JSON/Java parity | focused adapter tests |
| H4 | docs, counts, stress scenarios | focused regression |

H1 is implemented: `HostType`, `WorldView.get`, `Expectations.host`, `OccupantDescriptor(type, kind)`,
the removed `Expectations.property`/`Anchors`/`StructureKernel.siting` and the removed descriptor
properties/tags.

H2 is implemented: one `SnapshotLevel` per frame, built by the existing section walk from copied
section states, per-section fluids, detached block entities and captured entities; `LevelSnapshot.get`
dispatches typed reads to its frame snapshot; `McAnchors` resolves the anchor from the captured
block state; `LevelCapture` composes the snapshot content into the section digest. Heightmaps are
not captured because no request path exists.

H3 is implemented: the adapter's `property`/`state`/`tag`/`fluid` factories lower to
`Expectations.host` over `HostTypes.blockState`/`fluidState`/`entity`; the `state` factory resolves
its `BlockState` at compile time; the `fluid` factory composes the fluid-state and block-state leaves;
`McAnchors` enforces `AnchorSpec.value` against the captured origin property; `AdapterCodes` carries
the `SC-MC-0006`…`0010` catalog entries; and the adapter expectation catalog is merged into
`ServerState`'s catalogs so a normal JSON `checks` entry compiles. H4 closes docs, counts and stress
scenarios.

Visual review stays last.
