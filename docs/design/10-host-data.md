# 10. Host snapshot and typed reads

> Status: implemented (H1–H4). This document is the source of truth for the world snapshot, the
> typed host-read bridge, and MC-specific property/state/tag/fluid checks.

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
view.get(pos, HostTypes.entityType)
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
public static final HostType<EntityType<?>> entityType = HostType.of(id("entity_type"));
public static final HostType<BlockEntity> blockEntity = HostType.of(id("block_entity"));
```

The constants live in the adapter's `HostTypes`; the lowerCamel names follow the project's
static-field style.

## 3. WorldView bridge

`WorldView` has one generic read method:

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
- total: missing means "not in this snapshot", not an exception;
- the value is the captured one, in the snapshot's frame: the kernel never re-frames it and the host
  owns the frame.

The method is a field extension, not a wrapper object:

```java
BlockState state = view.get(cell, HostTypes.blockState);
FluidState fluid = view.get(cell, HostTypes.fluidState);
EntityType<?> entityType = view.get(cell, HostTypes.entityType);
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
- heightmaps are not captured: no kernel request path exists (§13).

`CapturedEntity` is the adapter's internal record: entity UUID, type id, local footprint and the
captured `EntityType`. The live `Entity` is used only while the capture runs.

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
    if (type.equals(HostTypes.entityType)) {
        return (H) entityTypeAt(pos);
    }
    if (type.equals(HostTypes.blockEntity)) {
        return (H) getBlockEntity(pos);
    }
    return null;
}
```

A new field extension adds a `HostType` and one branch; no existing type changes.

## 5. Capture

The capture is one section walk:

1. for each non-air world section the frame walk touches, copy its `PalettedContainer<BlockState>`
   once (`copy()`), keyed by the world section coordinates;
2. fill that section's 16³ `FluidState` array from the copied block states;
3. copy each block entity as a detached snapshot block entity (`saveWithId` / `loadStatic`) keyed by
   the local cell;
4. keep the captured entity candidates with their local footprints and their `EntityType`;
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
- false -> `Mismatch` with `mismatchCode` and `description`;
- the read is one snapshot value: the kernel does not decode, transform or re-frame it.

The predicate and both codes are supplied by the adapter. The kernel mints no MC code: it never
declares `SC-MC-0010` and never interprets host data. The adapter passes its `SC-MC-0010` as
`absentCode`.

## 7. Adapter expectations

The JSON document has no separate MC check type. A normal check whose expectation kind is one of the
adapter factories compiles into an `Expectations.host` predicate. The adapter registers `property`,
`state`, `tag` and `fluid` in its expectation catalog; the kernel document reader stays blueprint-only
and its built-in expectation catalog has none of the four.

A host expectation reads the value the snapshot carries. A sited capture's root snapshot is the world
frame, so a serialized property or an exact state is the observed world value: the adapter does not
project definition-frame states (D-29 is superseded by D-33). A host that needs definition-frame
values must capture them itself.

### 7.1 Property

```json
{"kind": "property", "key": "lit", "value": "true"}
```

```java
Expectations.host(
    blockState,
    (state, cell) -> propertyValue(state, key).equals(value),   // false when the block has no key
    SC_MC_0006,
    SC_MC_0010,
    "expected property " + key + "=" + value
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
    && block.getBlock() == block.getFluidState().createLegacyBlock().getBlock()
```

- real water matches;
- a waterlogged slab does not match;
- waterlogged is a block state: use `property("waterlogged", "true")` or `state`;
- lava and modded fluids use the MC fluid registry.

The adapter composes the two conditions as two `Expectations.host` leaves under one `all_of`: the
captured `FLUID_STATE`'s type is the expected fluid, and the captured `BLOCK_STATE`'s block is that
cell's own legacy fluid block (`block.getFluidState().createLegacyBlock()`). Both leaves carry
`SC-MC-0009` as the mismatch code and `SC-MC-0010` as the absent code.

The composed check's `ExpectationMeta` is the kernel `Junction`'s: id `all_of` and mismatch code
`SC-EXP-0001`, not `SC-MC-0009`; the attempt's mismatch findings are the leaves' `SC-MC-0009`. A host
that needs the fluid code reads the findings, not the meta. A waterlogged slab passes the fluid-state
leaf and the fluid tag, and fails only the block-state leaf.

## 8. Anchor

The kernel has no `Anchors` resolver and `StructureKernel` has no `siting` operation. The adapter
resolves anchors from the snapshot:

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
- `AnchorSpec.value` is a real constraint;
- the anchor cell is captured with the same world snapshot;
- no live world read.

The phase-1 command capture supplies the anchor cell's view, so `McAnchors` reads the captured
`BlockState`; a missing state is `AdapterCodes.valueNotCaptured` (`SC-MC-0010`), never a live read and
never `SC-MC-0001`. `AnchorSpec.value` is compared case-insensitively against the captured property
value at the definition origin, and a mismatch is `Codes.anchorInvalid` (`SC-BPT-0006`). `SC-MC-0001`
remains the capture refusal (an unloaded chunk or a refused section, §3); `SC-MC-0010` is both the
host expectation absent code and the anchor's missing-state answer.

## 9. Descriptor

`OccupantDescriptor` is:

```java
(type, kind)
```

It carries no `properties`, no `tags` and no `property(...)`; those were removed with the kernel
`Expectations.property` leaf. `OccupantRef` remains the occupant identity, and `OccupantFilter` still
uses type/kind. The adapter registers the `property` factory.

## 10. Digest

`SectionSnapshot.contentDigest` is adapter-computed from the snapshot. It includes:

- occupant refs;
- descriptor type/kind;
- the snapshot's block/fluid/entity content for the section.

The composition is implemented by `SnapshotLevel.contentDigest(region)`, which hashes each captured
block state, fluid state and entity that intersects the section; `LevelCapture` combines that value
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
| `SC-MC-0010` | host snapshot value was not captured (host expectation absent code and a missing anchor state) |

`SC-MC-0010` never answers a mismatch: a host expectation returns `Unreadable`, and `McAnchors` fails
the siting. It is the adapter's code, passed as the `absentCode` of `Expectations.host`; the kernel
never mints it. `SC-EXP-0001` remains for kernel leaves.

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
- no property map or tag list in `OccupantDescriptor`;
- no heightmap capture: no kernel request path exists.

## 14. Impact (implemented)

Kernel:

- `OccupantDescriptor` is `(type, kind)`;
- `Expectations.property` is gone;
- `Anchors` and `StructureKernel.siting` are gone;
- `HostType` is added;
- `WorldView.get(pos, type)` is added;
- `Expectations.host` is added.

Adapter:

- one `SnapshotLevel` per frame;
- `HostType` constants for block state, fluid state, entity type, block entity;
- `LevelCapture`/`FrameData`/`LevelSnapshot` snapshot wiring;
- `McAnchors`;
- adapter expectation factories;
- `SC-MC-0006..0010`;
- digest update.

Tests/docs:

- descriptor tests use `(type, kind)`;
- the anchor tests are `McAnchorsTest`;
- `OrientationAuditGameTest` covers the siting audit and `HostExpectationTest` the default read and
  the bridge;
- `AdapterExpectationsTest` compiles the host kinds through the adapter catalog and shows that the
  kernel catalog rejects them;
- snapshot read/digest tests: `HostExpectationTest`, `SnapshotLevelTest`, `LevelSnapshotTest`;
- the waterlogged distinction is `AdapterExpectationsTest` and `HostDataScenarioTest`;
- JSON/Java parity is `AdapterExpectationsTest`;
- the host-data stress group is `HostDataScenarioTest`: a rotated structure set, a nested child
  frame, a waterlogged slab and a snapshot with no captured host values.

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
`Expectations.host` over `HostTypes.blockState`/`fluidState`/`entityType`; the `state` factory resolves
its `BlockState` at compile time; the `fluid` factory composes the fluid-state and block-state leaves;
`McAnchors` enforces `AnchorSpec.value` against the captured origin property; `AdapterCodes` carries
the `SC-MC-0006`…`0010` catalog entries; and the adapter expectation catalog is merged into
`ServerState`'s catalogs so a normal JSON `checks` entry compiles.

H4 is implemented: this document's implemented status, the counts in `06-verification.md` and
`README.md`, the supersession of D-29 by D-33, and the adapter host-data stress group
(`HostDataScenarioTest`: a rotated structure set, a nested child frame, a waterlogged slab and a
snapshot with no captured host values). H4 adds no public type and no kernel change.

Visual review stays last.
