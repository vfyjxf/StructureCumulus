# 10. Host snapshots and MC-specific checks

> Status: proposed, not implemented. This document is the source of truth for host snapshots, the
> generic host bridge, and MC-specific property/state/tag/fluid checks.

## 1. Purpose

The kernel describes what an occupant is: its type and kind. It must not model block states,
properties, tags, fluids, entity data, or any other host payload.

Those are host data. They are **snapshotted in their original MC form**, not translated into a
kernel model and not merged into one "host snapshot" object:

- `BlockSnapshot`: copied `PalettedContainer<BlockState>` sections;
- `FluidSnapshot`: copied fluid states;
- `EntitySnapshot`: copied entities;
- `BlockEntitySnapshot`: copied block entities, when requested.

Each is its own type, its own `HostType`, and its own capture request. A host can add a new snapshot
type without touching the existing ones.

The reference shape is
[CachedLevel.java](https://github.com/khjxiaogu/Beecrasy/blob/master/src/main/java/com/khjxiaogu/beecrasy/utils/CachedLevel.java):
copy the vanilla section data, then bridge a typed read API. MBD2's structure debugger follows the
same snapshot direction ([debugging docs](https://low-drag-mc.github.io/LowDragMC-Doc/en/multiblocked2/editor/debugging.html)).

There is no per-cell `HostTable`, no `HostData` wrapper, no `McOccupant` union, no `McSnapshot`
union and no live-world read from `WorldView.host`.

## 2. HostType

`HostType<H>` is the type token for one host snapshot API:

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

- `H` is one snapshot type; the kernel never names it;
- no `Class<?>`;
- no per-cell value type;
- one `HostType` per snapshot API.

Adapter constants:

```java
public static final HostType<BlockSnapshot> BLOCK_SNAPSHOT =
    HostType.of(id("block_snapshot"));

public static final HostType<FluidSnapshot> FLUID_SNAPSHOT =
    HostType.of(id("fluid_snapshot"));

public static final HostType<EntitySnapshot> ENTITY_SNAPSHOT =
    HostType.of(id("entity_snapshot"));

public static final HostType<BlockEntitySnapshot> BLOCK_ENTITY_SNAPSHOT =
    HostType.of(id("block_entity_snapshot"));
```

## 3. WorldView bridge

`WorldView` gains one generic method:

```java
public interface WorldView {

    default <H> @Nullable H host(HostType<H> type) {
        return null;
    }
}
```

- no cell parameter;
- returns one snapshot type, never a union;
- default `null`;
- `LevelSnapshot` overrides it for each captured snapshot type;
- it never reads the live world;
- it is total: missing means "not captured", not an exception.

## 4. Snapshots

### 4.1 BlockSnapshot

```java
public final class BlockSnapshot implements BlockGetter {

    private final Cuboid bounds;
    private final PalettedContainer<BlockState>[] sections;
    private final HeightProvider[] heightmaps;

    public BlockState getBlockState(BlockPos pos);
    public int getHeight(int x, int z);
}
```

- copies `PalettedContainer<BlockState>` sections;
- local coordinates;
- out-of-bounds reads return air, like `CachedLevel`;
- no live `Level` reference;
- used by property, exact state, block tag and anchor.

### 4.2 FluidSnapshot

```java
public final class FluidSnapshot {

    private final Cuboid bounds;
    private final PalettedContainer<FluidState>[] sections;

    public FluidState getFluidState(BlockPos pos);
}
```

- copies fluid states;
- local coordinates;
- out-of-bounds reads return `Fluids.EMPTY.defaultFluidState()`;
- used by fluid and fluid tag checks.

### 4.3 EntitySnapshot

```java
public final class EntitySnapshot {

    private final Cuboid bounds;
    private final List<Entity> entities;

    public List<Entity> entities();
}
```

- copies the captured entity references/records;
- used by entity tag checks.

### 4.4 BlockEntitySnapshot

```java
public final class BlockEntitySnapshot {

    private final Cuboid bounds;
    private final Map<BlockPos, CompoundTag> blockEntities;

    public @Nullable CompoundTag get(BlockPos pos);
}
```

- copies block entity data when requested;
- used by future block-entity-specific checks;
- no capability or live block entity is retained.

### 4.5 No union

There is no `McSnapshot`, no `HostData`, and no combined object. A check asks for exactly the
snapshot type it needs:

```java
BlockSnapshot blocks = view.host(BLOCK_SNAPSHOT);
FluidSnapshot fluids = view.host(FLUID_SNAPSHOT);
EntitySnapshot entities = view.host(ENTITY_SNAPSHOT);
```

A host can add `ItemSnapshot`, `EnergySnapshot`, or any other type without changing the existing
snapshots.

## 5. Capture

### 5.1 Policy

`ReadScope` and `SnapshotPolicy` gain:

```java
Set<HostType<?>> hostTypes
```

with `withHostTypes` and union by id. `Blueprint.reads()` includes:

- every check's host types;
- `BLOCK_SNAPSHOT` when the blueprint has anchors.

`CommandSupport.policy(blueprint)` passes them to the capture.

### 5.2 Capture path

`LevelCapture` builds only the requested snapshots from the same section walk:

1. if `BLOCK_SNAPSHOT` is requested: copy the relevant `PalettedContainer<BlockState>` sections and
   heightmaps;
2. if `FLUID_SNAPSHOT` is requested: copy the relevant fluid states;
3. if `ENTITY_SNAPSHOT` is requested: copy the captured entities;
4. if `BLOCK_ENTITY_SNAPSHOT` is requested: copy block entity data.

The existing `OccupantSnapshot` capture stays as the kernel's type/kind/footprint view. It is not
extended with host data.

`FrameData`/`LevelSnapshot` carry the per-frame snapshot objects. Nested frames get their own
snapshots.

### 5.3 No second read

Snapshots are built during capture, not by `WorldView.host`. A check never reads the live level.

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

- read `H host = ctx.view().host(type)`;
- `null` -> `Unreadable` with `SC-MC-0010`;
- apply `test.test(host, subject.ref().anchor())`;
- true -> `Match`;
- false -> `Mismatch` with `mismatchCode` and `description`.

`reads()` adds `type` to `ReadScope.hostTypes`. No per-cell host data and no trait request.

## 7. Adapter expectations

The JSON document has no separate MC check type. A normal check whose expectation kind is one of the
adapter factories is compiled into an `Expectations.host` predicate.

### 7.1 Property

```json
{"kind": "property", "key": "lit", "value": "true"}
```

```java
Expectations.host(
    BLOCK_SNAPSHOT,
    (blocks, cell) -> blocks.getBlockState(pos(cell)).getValue(property).getName().equals(value),
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

- block: `blocks.getBlockState(pos).is(tag)`;
- fluid: `fluids.getFluidState(pos).is(tag)`;
- entity: the entity snapshot's type holder `is(tag)`.

No descriptor tags and no `anyOf` expansion.

### 7.4 Fluid

```json
{"kind": "fluid", "id": "minecraft:water"}
```

```java
BlockState block = blocks.getBlockState(pos);
FluidState fluid = fluids.getFluidState(pos);

fluid.getType() == expected
    && block.getBlock() == fluid.createLegacyBlock().getBlock()
```

- real water matches;
- a waterlogged slab does not match;
- waterlogged is a block state: use `property("waterlogged", "true")` or `state`;
- lava and modded fluids use the MC fluid registry.

## 8. Anchor

The kernel `Anchors` and `StructureKernel.siting` are removed. The adapter resolves anchors from
the captured `BlockSnapshot`:

```java
public final class McAnchors {

    public static SitingResult resolve(
        Blueprint blueprint,
        LatticePoint origin,
        WorldView view
    ) {
        BlockSnapshot blocks = view.host(BLOCK_SNAPSHOT);
        if (blocks == null) {
            return failure(..., SC_MC_0010, "the anchor block snapshot was not captured");
        }
        BlockState state = blocks.getBlockState(pos(origin));
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
- `CommandSupport` captures the anchor cell with `BLOCK_SNAPSHOT` in the policy;
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

`SectionSnapshot.contentDigest` is adapter-computed. The digest includes:

- occupant refs;
- descriptor type/kind;
- the requested host snapshots for the section's cells.

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
| `SC-MC-0010` | host snapshot was not captured |

`SC-MC-0010` is `Unreadable`, never a mismatch. `SC-EXP-0001` remains for kernel leaves.

## 12. JSON

- no `mc_checks`;
- no separate JSON type;
- normal `checks` use adapter expectation kinds;
- adapter catalogs register `property`, `state`, `tag`, `fluid`;
- the kernel document reader stays blueprint-only.

## 13. Non-goals

- no `McSnapshot` union;
- no `HostData` interface;
- no `HostView` wrapper;
- no `McOccupant` union;
- no per-cell `HostTable`;
- no `Class<?>` tokens;
- no trait reuse for block/fluid/entity data;
- no live world read in `WorldView.host`;
- no MC type in the kernel;
- no property map or tag list in `OccupantDescriptor`.

## 14. Impact

Kernel:

- `OccupantDescriptor` becomes `(type, kind)`;
- `Expectations.property` is removed;
- `Anchors` and `StructureKernel.siting` are removed;
- `ReadScope`/`SnapshotPolicy` gain `hostTypes`;
- `Blueprint.reads()` includes the anchor host type;
- `WorldView` gains `host`;
- `Expectations.host` is added;
- `Expectations.Property` is removed.

Adapter:

- `BlockSnapshot`, `FluidSnapshot`, `EntitySnapshot`, `BlockEntitySnapshot`;
- four `HostType` constants;
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
- snapshot copy/read/digest tests per type;
- waterlogged distinction tests;
- JSON/Java parity for property/state/tag/fluid.

## 15. Implementation slices

| Slice | Scope | Gate |
| --- | --- | --- |
| H1 | `HostType`, `ReadScope`/`SnapshotPolicy` host types, `WorldView.host`, `Expectations.host`; descriptor `(type, kind)`; remove property/anchors | focused kernel tests |
| H2 | `BlockSnapshot`/`FluidSnapshot`/`EntitySnapshot`/`BlockEntitySnapshot`, capture wiring, digest | focused adapter capture tests |
| H3 | adapter `property`/`state`/`tag`/`fluid` factories, `McAnchors`, `SC-MC-0006..0010`, JSON/Java parity | focused adapter tests |
| H4 | docs, counts, stress scenarios | focused regression |

Visual review stays last.
