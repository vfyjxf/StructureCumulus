# 10. Host snapshots and MC-specific checks

> Status: proposed, not implemented. This document is the source of truth for host snapshots, the
> generic host bridge, and MC-specific property/state/tag/fluid checks.

## 1. Purpose

The kernel describes what an occupant is: its type and kind. It must not model block states,
properties, tags, fluids, entity data, or any other host payload.

Those are host data. They are **snapshotted in their original MC form**, not translated into a
kernel model:

- copied `PalettedContainer<BlockState>` sections;
- block entities;
- entities;
- heightmaps when a check needs them.

The reference implementation is
[CachedLevel.java](https://github.com/khjxiaogu/Beecrasy/blob/master/src/main/java/com/khjxiaogu/beecrasy/utils/CachedLevel.java):
it copies the section containers (or references them) and exposes the result through
`BlockGetter`/`LevelReader`. This design follows the same shape: copy the vanilla data, then bridge
one API.

There is no per-cell `HostTable`, no `HostData` wrapper, no `McOccupant` union and no host-data
mirror map.

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

- `H` is the host snapshot type; the kernel never names it;
- no `Class<?>`;
- no per-cell value type;
- one `HostType` per host snapshot API.

Adapter constant:

```java
public static final HostType<McSnapshot> MC_SNAPSHOT =
    HostType.of(id("mc_snapshot"));
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
- returns the whole host snapshot, not a per-cell value;
- default `null`;
- `LevelSnapshot` overrides it for `MC_SNAPSHOT`;
- it never reads the live world;
- it is total: missing means "not captured", not an exception.

## 4. McSnapshot

`McSnapshot` is the adapter's immutable snapshot of the vanilla data:

```java
public final class McSnapshot implements BlockGetter {

    private final Cuboid bounds;
    private final PalettedContainer<BlockState>[] sections;
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final List<Entity> entities;
    private final HeightProvider[] heightmaps;

    public BlockState getBlockState(BlockPos pos);
    public FluidState getFluidState(BlockPos pos);
    public @Nullable BlockEntity getBlockEntity(BlockPos pos);
    public List<Entity> entities();
}
```

- coordinates are **local** to the snapshot (the `CachedLevel` shape with a local origin);
- block states are copied `PalettedContainer`s, not per-cell maps;
- block entities are copied where needed;
- entities are copied references/records for the snapshot;
- out-of-bounds reads return air, like `CachedLevel`;
- no live `Level` reference is retained for reads.

`LevelSnapshot` holds one `McSnapshot` per frame and implements:

```java
@Override
public <H> @Nullable H host(HostType<H> type) {
    if (type.equals(McHostTypes.MC_SNAPSHOT)) {
        @SuppressWarnings("unchecked")
        H snapshot = (H) mcSnapshot;
        return snapshot;
    }
    return null;
}
```

## 5. Capture

### 5.1 Policy

`ReadScope` and `SnapshotPolicy` gain:

```java
Set<HostType<?>> hostTypes
```

with `withHostTypes` and union by id. `Blueprint.reads()` includes:

- every check's host types;
- the anchor host type when the blueprint has anchors.

`CommandSupport.policy(blueprint)` passes them to the capture.

### 5.2 Capture path

`LevelCapture` builds `McSnapshot` from the same section walk it already performs:

1. copy the relevant `PalettedContainer<BlockState>` sections;
2. copy the block entities that fall inside the capture bounds;
3. copy the entity candidates;
4. copy the heightmaps if requested;
5. wrap them in `McSnapshot`.

The existing `OccupantSnapshot` capture stays as the kernel's type/kind/footprint view. It is not
extended with host data.

`FrameData`/`LevelSnapshot` carry the `McSnapshot` for the frame. Nested frames get their own
snapshot.

### 5.3 No second read

The snapshot is built during capture, not by `WorldView.host`. A check never reads the live level.
The host snapshot is the frozen value for the attempt.

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

The predicate is supplied by the adapter. The kernel never constructs or interprets host data.

## 7. Adapter expectations

The JSON document has no separate MC check type. A normal check whose expectation kind is one of the
adapter factories is compiled into an `Expectations.host` predicate over `McSnapshot`.

### 7.1 Property

```json
{"kind": "property", "key": "lit", "value": "true"}
```

```java
Expectations.host(
    MC_SNAPSHOT,
    (snapshot, cell) -> snapshot.getBlockState(pos(cell)).getValue(property).getName().equals(value),
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

- block: `snapshot.getBlockState(pos).is(tag)`;
- fluid: `snapshot.getFluidState(pos).is(tag)`;
- entity: the snapshot's entity type holder `is(tag)`.

No descriptor tags and no `anyOf` expansion.

### 7.4 Fluid

```json
{"kind": "fluid", "id": "minecraft:water"}
```

```java
FluidState fluid = snapshot.getFluidState(pos);
BlockState block = snapshot.getBlockState(pos);
fluid.getType() == expected
    && block.getBlock() == fluid.createLegacyBlock().getBlock()
```

- real water matches;
- a waterlogged slab does not match;
- waterlogged is a block state: use `property("waterlogged", "true")` or `state`;
- lava and modded fluids use the MC fluid registry.

## 8. Anchor

The kernel `Anchors` and `StructureKernel.siting` are removed. The adapter resolves anchors from
the captured snapshot:

```java
public final class McAnchors {

    public static SitingResult resolve(
        Blueprint blueprint,
        LatticePoint origin,
        WorldView view
    ) {
        McSnapshot snapshot = view.host(McHostTypes.MC_SNAPSHOT);
        if (snapshot == null) {
            return failure(..., SC_MC_0010, "the anchor snapshot was not captured");
        }
        BlockState state = snapshot.getBlockState(pos(origin));
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
- `CommandSupport` captures the anchor cell with `MC_SNAPSHOT` in the policy;
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
- the `McSnapshot` content for the section's cells.

Test:

```text
same type, different BlockState
  -> different contentDigest
```

The snapshot is the source of truth; there is no per-cell host table to keep in sync.

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

- `McSnapshot` (copied sections, block entities, entities, heightmaps);
- `HostType<McSnapshot>` constant;
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
- snapshot copy/read/digest tests;
- waterlogged distinction tests;
- JSON/Java parity for property/state/tag/fluid.

## 15. Implementation slices

| Slice | Scope | Gate |
| --- | --- | --- |
| H1 | `HostType`, `ReadScope`/`SnapshotPolicy` host types, `WorldView.host`, `Expectations.host`; descriptor `(type, kind)`; remove property/anchors | focused kernel tests |
| H2 | `McSnapshot`, `LevelCapture`/`FrameData`/`LevelSnapshot` snapshot copy/read, digest | focused adapter capture tests |
| H3 | adapter `property`/`state`/`tag`/`fluid` factories, `McAnchors`, `SC-MC-0006..0010`, JSON/Java parity | focused adapter tests |
| H4 | docs, counts, stress scenarios | focused regression |

Visual review stays last.
