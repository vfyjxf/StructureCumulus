# 10. Host data and MC-specific checks

> Status: proposed, not implemented. This document is the source of truth for host data capture,
> the generic host-data read face, and MC-specific property/state/tag/fluid checks.

## 1. Purpose

The kernel describes what an occupant is: its type and kind. It must not model block states,
properties, tags, fluids, entity data, or any other host-specific payload.

Those are host data:

- a block state;
- a fluid state;
- an entity;
- a block entity;
- any future host payload.

They are captured by the host, stored in the capture, and read by host-specific checks through one
generic typeclass. The kernel never imports a host type and never translates host data into a
kernel model.

## 2. Problem

The current model mixes two different things in `OccupantDescriptor`:

```java
(type, kind, properties, tags)
```

- `type` and `kind` are identity;
- `properties` and `tags` are host data.

That forces the adapter to translate MC block state into strings and tag lists, and it makes the
kernel carry data it cannot interpret. It also makes exact-state and fluid checks impossible without
adding more host data to the kernel.

The fix is:

```text
descriptor = (type, kind)
host data  = captured separately, read by HostType<H>
```

## 3. HostType

`HostType<H>` is the type token. It carries no host class object and no host data.

```java
package dev.vfyjxf.cumulus.kernel.world;

public record HostType<H>(
    NamespacedId id,
    Set<OccupantKind> kinds
) implements Comparable<HostType<?>> {

    public HostType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kinds, "kinds");
        kinds = Set.copyOf(new TreeSet<>(kinds));
    }

    public static <H> HostType<H> of(NamespacedId id, OccupantKind... kinds) {
        return new HostType<>(id, Set.of(kinds));
    }

    @Override
    public int compareTo(HostType<?> other) {
        return id.compareTo(other.id);
    }
}
```

- `H` is the host payload type; the kernel never names it.
- `kinds` declares which occupant kinds can carry the payload.
- equality and ordering are by `id`; ids are unique per host.
- no `Class<?>`.
- no reflection.

Adapter constants:

```java
public static final HostType<BlockState> BLOCK_STATE =
    HostType.of(id("block_state"), OccupantKind.block, OccupantKind.blockEntity);

public static final HostType<FluidState> FLUID_STATE =
    HostType.of(id("fluid_state"), OccupantKind.block, OccupantKind.blockEntity);

public static final HostType<Entity> ENTITY =
    HostType.of(id("entity"), OccupantKind.entity);
```

## 4. Read face

`WorldView` gains one generic method:

```java
public interface WorldView {

    <H> @Nullable H host(HostType<H> type, LatticePoint cell);

    default <H> @Nullable H host(HostType<H> type, OccupantRef ref) {
        return host(type, ref.anchor());
    }
}
```

- the default implementation returns `null`;
- the adapter's `LevelSnapshot` implements it from captured host tables;
- it never reads the live world;
- it is total: a missing value is `null`, not an exception;
- it does not use traits, ids, `ReadScope.traits()`, or any generic slot container.

`null` means one of:

1. the host type was not captured;
2. the cell has no occupant of a kind the type supports;
3. the host genuinely has no value for that cell.

Checks that require the data must treat `null` as a host-data refusal, not as a mismatch. The exact
code is `SC-MC-0010` (see §10).

## 5. Capture policy

Host data is captured only when the plan asks for it.

### 5.1 ReadScope

`ReadScope` gains:

```java
Set<HostType<?>> hostTypes
```

with:

```java
ReadScope withHostTypes(Set<HostType<?>> extra);
```

`union` merges host types by id. `Expectations.host` adds its own type and the type's kinds.

### 5.2 SnapshotPolicy

`SnapshotPolicy` gains:

```java
Set<HostType<?>> hostTypes
```

and:

```java
SnapshotPolicy withHostTypes(Set<HostType<?>> extra);
```

`CommandSupport.policy(blueprint)` passes `blueprint.reads().hostTypes()`.

### 5.3 Blueprint.reads

`Blueprint.reads()` is the union of:

- every check's `ReadScope`;
- every computation region;
- the anchor host type when the blueprint has anchors.

The anchor capture policy in `CommandSupport` includes `BLOCK_STATE` so the anchor cell can be read
from the captured view.

## 6. Capture

### 6.1 Storage

`FrameData` gains one field:

```java
Map<HostType<?>, HostTable<?>> hostData
```

`HostTable<H>` is an internal adapter record:

```java
record HostTable<H>(
    Map<OccupantRef, H> byRef,
    Map<LatticePoint, H> byCell
) { }
```

- one table per captured host type per frame;
- both maps are sorted and immutable;
- no public `HostData` interface;
- no host type in the kernel.

`LevelSnapshot` implements `WorldView.host` by looking up the frame's table and casting through the
type token:

```java
@Override
public <H> @Nullable H host(HostType<H> type, LatticePoint cell) {
    HostTable<?> table = data.hostData().get(type);
    if (table == null) {
        return null;
    }
    @SuppressWarnings("unchecked")
    HostTable<H> typed = (HostTable<H>) table;
    return typed.byCell().get(cell);
}
```

The same lookup exists by `OccupantRef`; it is the primary lookup for expectation subjects because
multi-cell bodies share one anchor cell.

### 6.2 What is captured

For each captured block or block-entity occupant:

- `BLOCK_STATE` → the **projected** `BlockState` used by the descriptor;
- `FLUID_STATE` → `projectedState.getFluidState()`;
- `ENTITY` → not present for blocks.

For each captured entity:

- `ENTITY` → the entity;
- block/fluid tables have no entry.

For nested bodies and air:

- no host data.

The root frame uses the projected state (`context.project(state, cell)`); child frames use the raw
state. This keeps host checks consistent with the descriptor the check sees.

### 6.3 Capture order

Host data is captured in the same loop as the occupant:

```text
state -> projected state -> occupant -> host tables
```

There is no second world read. The captured host value is the value the snapshot represents.

### 6.4 Partial and refused sections

- a refused section captures no host data;
- a partial section captures host data only for cells whose occupant was captured;
- an unreadable cell has no host data, and a host check reports `SC-MC-0010` rather than a false
  mismatch.

## 7. Expectations.host

The kernel provides one generic expectation:

```java
public static <H> Expectation<OccupantSnapshot> host(
    HostType<H> type,
    Predicate<H> test,
    FindingCode mismatchCode,
    String description
);
```

Semantics:

- read `ctx.view().host(type, ctx.subject().ref())`;
- `null` → `Unreadable` with `SC-MC-0010`;
- predicate true → `Match`;
- predicate false → `Mismatch` with `mismatchCode` and `description`.

`reads()` returns:

```java
ReadScope.none()
    .withHostTypes(Set.of(type))
    .withKinds(type.kinds());
```

The predicate is supplied by the adapter. The kernel never constructs a host value and never
interprets one.

## 8. Adapter checks

The JSON document has no separate MC check type. A normal check whose expectation kind is one of the
adapter factories is compiled by the adapter into an `Expectations.host` predicate.

### 8.1 Factories

Adapter `ExpectationFactory` entries:

| JSON kind | Host type | Predicate |
| --- | --- | --- |
| `property` | `BLOCK_STATE` | `state.getValue(property).getName().equals(value)` |
| `state` | `BLOCK_STATE` | `state.equals(expectedState)` |
| `tag` | `BLOCK_STATE` / `FLUID_STATE` / `ENTITY` | `state.is(tag)` / `fluid.is(tag)` / `entityType.is(tag)` |
| `fluid` | `FLUID_STATE` + `BLOCK_STATE` | actual fluid block check |

### 8.2 Property

```json
{"kind": "property", "key": "lit", "value": "true"}
```

- `property` is an MC property name;
- the adapter resolves it against the captured `BlockState`;
- missing property → mismatch;
- no string map in the kernel.

### 8.3 Exact state

```json
{
  "kind": "state",
  "block": "minecraft:furnace",
  "properties": {"facing": "north", "lit": "true"}
}
```

- the adapter resolves `block + properties` to a `BlockState` at compile time;
- the predicate compares the captured state directly;
- extra or missing properties fail;
- no property-map translation in the kernel.

### 8.4 Tag

```json
{"kind": "tag", "registry": "block", "id": "minecraft:logs"}
{"kind": "tag", "registry": "fluid", "id": "minecraft:water"}
{"kind": "tag", "registry": "entity", "id": "minecraft:undead"}
```

- `registry` selects the MC tag registry;
- the predicate uses `TagKey` directly;
- tags are not stored in the descriptor;
- tags are not expanded to `anyOf`;
- a datapack tag change is visible on the next capture.

### 8.5 Fluid

```json
{"kind": "fluid", "id": "minecraft:water"}
```

- read `FLUID_STATE` and `BLOCK_STATE`;
- actual fluid block:

```java
fluid.getType() == expected
    && block.getBlock() == fluid.createLegacyBlock().getBlock()
```

- real water matches;
- a waterlogged slab does **not** match;
- waterlogged is a block state: use `property("waterlogged", "true")` or `state`;
- lava and modded fluids use the MC fluid registry.

## 9. Anchor

`Anchors` leaves the kernel. The adapter owns anchor resolution:

```java
public final class McAnchors {

    public static SitingResult resolve(
        Blueprint blueprint,
        LatticePoint origin,
        WorldView view
    ) {
        BlockState state = view.host(BLOCK_STATE, origin);
        if (state == null) {
            return failure(..., SC_MC_0010, "the anchor block state was not captured");
        }
        String value = state.getValue(property);
        if (!value.equalsIgnoreCase(spec.value())) {
            return failure(..., anchorInvalid, "anchor property does not equal the declared value");
        }
        Heading heading = heading(value);
        ...
    }
}
```

- the anchor cell is captured with `BLOCK_STATE` in the policy;
- `AnchorSpec.value` is a real constraint;
- the kernel `AnchorSpec` and `OrientationPolicy` remain declarations;
- `StructureKernel.siting` is removed;
- `CommandSupport` calls `McAnchors.resolve`;
- no live world read at check time.

## 10. Codes

Adapter codes, not in `Codes.all`:

| Code | Meaning |
| --- | --- |
| `SC-MC-0006` | property mismatch |
| `SC-MC-0007` | exact state mismatch |
| `SC-MC-0008` | tag mismatch |
| `SC-MC-0009` | fluid mismatch |
| `SC-MC-0010` | host data was not captured |

`SC-MC-0010` is `Unreadable`, never a mismatch. The kernel `SC-EXP-0001` remains for kernel leaves.

## 11. Digest

`SectionSnapshot.contentDigest` is adapter-computed. When a section captures host data, the digest
includes:

- occupant refs;
- descriptor type/kind;
- every captured host value for the section's cells.

Test obligation:

```text
same occupant type, different BlockState
  -> different contentDigest
```

This is a capture-cache correctness requirement.

## 12. JSON

- no `mc_checks` member;
- no separate JSON type;
- normal `checks` use adapter expectation kinds;
- the adapter catalogs register `property`, `state`, `tag`, `fluid`;
- the kernel document reader stays blueprint-only;
- an adapter check is still a normal `Check` with `Expectations.host` and an adapter predicate.

## 13. Non-goals

- no `HostData` interface;
- no `HostView` wrapper;
- no `McOccupant` union;
- no `Class<?>` tokens;
- no trait reuse for block/fluid/entity data;
- no live world read inside `WorldView.host`;
- no MC type in the kernel;
- no property map or tag list in `OccupantDescriptor`.

## 14. Impact

Kernel:

- `OccupantDescriptor` becomes `(type, kind)`;
- `Expectations.property` is removed;
- `Anchors` and `StructureKernel.siting` are removed;
- `ReadScope`/`SnapshotPolicy` gain `hostTypes`;
- `Blueprint.reads()` includes anchor host type;
- `WorldView` gains `host`;
- `Expectations.host` is added;
- `Expectations.Property` is removed.

Adapter:

- `HostType` constants;
- `HostTable` and `FrameData` host data;
- `LevelCapture` capture;
- `LevelSnapshot.host`;
- `McAnchors`;
- adapter expectation factories;
- `SC-MC-0006..0010`;
- digest update.

Tests/docs:

- descriptor tests remove properties/tags;
- anchor tests move to adapter;
- `SitedDescriptorGameTest`/`WorldTest` migrate;
- `DocumentCompilerTest` property checks move to adapter catalogs;
- host data capture/read/digest tests;
- waterlogged distinction tests;
- JSON/Java parity for property/state/tag/fluid.

## 15. Implementation slices

| Slice | Scope | Gate |
| --- | --- | --- |
| H1 | `HostType`, `ReadScope`/`SnapshotPolicy` host types, `WorldView.host`, `Expectations.host`; descriptor `(type, kind)`; remove property/anchors | focused kernel tests |
| H2 | `HostTable`, `FrameData`/`LevelCapture`/`LevelSnapshot` capture/read, digest | focused adapter capture tests |
| H3 | adapter `property`/`state`/`tag`/`fluid` factories, `McAnchors`, `SC-MC-0006..0010`, JSON/Java parity | focused adapter tests |
| H4 | docs, counts, stress scenarios | focused regression |

Visual review stays last.
