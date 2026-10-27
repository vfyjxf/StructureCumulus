# Module `dev.vfyjxf.cumulus.mc` (adapter, M1 + M2a/M2b/M2c/M2d + M3 + M4 + M5a/M5d/M5e)

> The only code allowed to name Minecraft. M1 implements the host, the command and the capture-check
> path: `LevelCapture`, `LevelSnapshot`, `LevelLease`, block/block-entity descriptors, the
> `ServerState` lifecycle, in-memory `BuiltinDocuments` (`structurecumulus:air_cell`) and
> `/cumulus check <id> <origin>` (identity siting for anchorless blueprints). M2a adds loaded-entity
> capture with policy windows, full footprint indexing and entity-vs-block precedence (`world.md`
> §4.3). M2b adds `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror` and an upright
> mirror-then-rotate `BlockState` transform.
> M2c (`cb06aa8`/`64d04ce`) implements Model C, body identity, the total read and the five trait
> keys. M2d (`f3e0338`/`d6aefc2`) implements `NestedExtent`/`NestedExtents`, one `LevelSnapshot` with
> package-private `FrameData`, child frames and cuts. M3 is committed at `d4b6d82`; `SavedClaims`/
> `SavedContexts` are compatible with Model C. M4a JSON reader/writer (`f2659c7`), M4b datapack reload
> (`76a8e5c`) and the command slice (`settle` at `4899d86`, plus `compile`, `explain`, `claims`) are
> implemented; capture-revision lease invalidation landed at `0f0bf25`. M5a adds the event-edged
> watched-region mark counter with lazy entity and block-entity fingerprints (`d1fa42f`); P1 fixes the
> rotated-siting capture bounds with the two-phase anchored capture (`56c632c`); M5d adds the
> non-upright orientation remap (`ac11d0c`); M5e adds the capture report the adapter answers with
> (`SC-MC-0003`/`0004`/`0005`) plus the report site the commands print (`28d0334`).

## 1. Position

- Source sets `main`; packages `dev.vfyjxf.cumulus.mc` and `dev.vfyjxf.cumulus.mc.capability`; the
  mod entry is `dev.vfyjxf.cumulus.StructureCumulus`.
- Minecraft 1.21.1 / NeoForge 21.1.x; one baseline, no loader abstraction and no version branches.
- **Implemented.** M1: `WorldCapture`, `WorldView`, `SnapshotLease`, block/block-entity descriptors,
  `ServerState`, in-memory `BuiltinDocuments`, `/cumulus check`. M2a: loaded-entity capture with
  `SnapshotPolicy.kinds`/`windows`, full footprint indexing, entity-vs-block precedence. M2b: upright
  orientation mapping. M2c: `OccupantRef.body` for entities, total `CellRead`/`ReadRefusal`,
  `SectionSlot.Partial`, four-state `TraitSlot`, `SnapshotPolicy.traits` by id, `OccupantTraits`,
  `SC-OCC-0004`, `SC-MC-0002` relay. M2d: `NestedExtent`/`NestedExtents`, one `LevelSnapshot` with
  `FrameData`, child frames, budgets/cuts, child entities and entity-hosted extents. M3: `SavedClaims`/
  `SavedContexts`, CAS settle/release, reload codecs and per-level stores. M4: the JSON reader/writer,
  the datapack source, the merged document source and the five `/cumulus` subcommands. M5: the
  determinism gate (`DeterminismTest`) covers canonical bytes across member order, the parse/write
  fixed point, equal compile runs and stable finding order; capture-revision lease invalidation is
  in place. M5a: the event-edged watched-region mark counter (`LevelWatch`/`WatchEvents`, refcounted
  watched chunks, lazy entity and block-entity fingerprints) at `d1fa42f`. P1: the two-phase anchored
  capture so the sited world bounds are captured under the resolved siting (`56c632c`). M5d: the
  non-upright orientation remap with `MappingResult.Unrepresentable` (`ac11d0c`). M5e: the capture
  report (over-cap `SC-MC-0005`, cut `SC-MC-0003`, the sited-capture `SC-MC-0004` audit, the
  `AdapterCodes` catalog and the `CaptureReports` relay) at `28d0334`.
- **Pending.** Three items only: the pre-existing kernel gap that a `property` expectation is not
  siting-aware, the performance/cost pass (block-entity-lazy and entity sweep) and fresh visual review
  screenshots. There is no regeneration milestone.
- No kernel type names a Minecraft type; the adapter may name every kernel type.

## 2. Types

| Type | Implements | Role |
| --- | --- | --- |
| `LevelCapture` | `WorldCapture` | server-thread capture into a `LevelSnapshot`; nested extents as declarations only |
| `LevelSnapshot` | `WorldView` | one immutable view over every frame; `read`, `bounds`, `policy`, `nested` |
| `FrameData` | — | package-private per-frame sections, entities, nested bodies/extents and children |
| `LevelLease` | `SnapshotLease` | `isCurrent` while open, its capture is the level's latest, the world epoch matches and the watched region is unmarked; lazy entity and block-entity fingerprints |
| `LevelWatch` / `WatchEvents` | package-private | the per-level mark registry (refcounted watched chunks, marks, epoch) and the NeoForge listeners that edge the marks |
| `OccupantDescriptors` | — | raw value ↔ `OccupantDescriptor`; entity body identity; nested body |
| `OccupantTraits` | — | the five stable trait keys and the four-state slots one policy requests |
| `TraitLookup` | interface, package-private | the trait-capability seam: fake in tests, capabilities in production |
| `CapabilityTraits` | enum, package-private | production trait lookup over NeoForge capabilities |
| `NestedExtent` | record (capability) | one provider declaration: type, absolute bounds, world origin |
| `NestedExtents` | final class (capability) | the `BLOCK` and `ENTITY` capabilities |
| `NestedExtentLookup` | interface, package-private | test seam for fake nested providers |
| `CapabilityLookup` | enum, package-private | production nested lookup (four-argument block / context-null entity) |
| `OrientationMapping` | — | `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror`; upright mirror-then-rotate transform and the non-upright capability remap, with `MappingResult.Unrepresentable` as the `SC-MC-0004` payload |
| `AdapterCodes` | final class, package-private | the adapter's `SC-MC-*` codes, their catalog `CodeEntry`s and the severity each finding carries |
| `CaptureReports` | final class, package-private | the report site: one `CaptureReport` entry → one `Finding` (severity, world label, trust guard) |
| `SavedClaims` | `ClaimLedger` | per-world `SavedData`, CAS on the server thread (M3) |
| `SavedContexts` | `ContextStore` | per-instance values in `SavedData` (M3) |
| `JsonReader` / `JsonWriter` | `DocumentReader` / `DocumentWriter` | Gson tree ↔ `Document`; canonical output (M4a) |
| `DatapackBlueprints` | `DocumentSource` | reload listener; ids under `data/<ns>/cumulus/structure/` (M4b); wins over `BuiltinDocuments` on an id collision |
| `ServerState` | — | catalogs (including the adapter's `SC-MC-*` code entries), server-level and per-level kernels, per-level ledger/contexts and capture cache |
| `CumulusCommand` | — | the five `/cumulus` subcommands (`check`, `compile`, `settle`, `explain`, `claims`) |
| `FindingOutput` | — | human/JSON to chat, log or a file (absent; the commands print inline today) |
| `MatchScheduler` | — | optional host execution: submit, coalesce, markDirty, tick (absent) |

## 3. Capture

M1 copies blocks and block entities. M2a adds loaded entities, M2c adds trait slots and per-read
refusal, M2d adds nested frames, and M5e adds the capture report and, with it, the orientation audit.
The rules below are implemented.

- One server-thread call. `ServerChunkCache.getChunkNow`; never `Level.getChunk`, never a chunk load.
  A nested child uses the same rule: an unloaded chunk is a cut, never a load.
- Sections are copied per captured cell; an all-air section is `Air(revision)`; an unloaded root
  chunk is `CaptureResult.Unavailable(notCaptured, SC-MC-0001)`. A section is never lazily resolved.
- Block entities are read `saveWithoutMetadata`; the state and block entity are read **once** and
  passed to capability queries. If `state.hasBlockEntity()` and the already-read block entity is
  `null`, the cell is a plain block and no block capability is queried; the adapter never re-fetches
  the block entity.
- Entities intersecting `bounds.inflate(maxEntitySpan)` are queried; M2a admits a loaded entity when
  its footprint intersects the bounds, adds each policy window's typed cells, and indexes every
  covered cell. A body leaving the capture is not clipped; its covered cells outside are unreadable.
  Entity-vs-block narrowing follows `world.md` §4.3: non-air, then smaller footprint volume, then
  `OccupantRef` order including `body`.
- Entity identity: `OccupantDescriptors.entity(...)` mints `body =
  NamespacedId.of(entityType.namespace(), uuid.toString())`. Two entities of one type at one anchor
  are two refs and stay two occupants; §8.4 covers child frames.
- Nested structures are discovered only when `policy.kinds()` contains `OccupantKind.nested`;
  otherwise no capability is queried. Nested discovery queries loaded entities intersecting the
  bounds for `NestedExtents.ENTITY` even when `entity` occupants are not captured; indexing the
  entity as an occupant itself still requires the `entity` kind. §8 owns the capability rules, frame
  construction, budgets and cuts.
- `SnapshotId` is stamped last from the per-world counter; a content-equal refresh may return the
  same view object (no new id).
- Caps: `maxElements` bounds the root plus every child frame. A root volume over the ceiling returns
  `CaptureResult.TooLarge`, never a partial root, with one `SC-MC-0005` entry (ERROR) in its report;
  a child over budget is cut, not partial (§8).
- Every capture answers with a `CaptureReport` (`world` §4.10). The arm is the verdict and the report
  is the finding: a cut child stays `nested(cell) == null` and leaves the arm `Captured`; the report
  carries one `SC-MC-0003` entry per cut and one `SC-MC-0004` entry per unrepresentable state group
  (§10). An empty report is the normal case and `Unavailable` carries one only when the host has
  extra detail; its own `SC-MC-0001` code already is its finding.
- The orientation audit runs inside the sited capture, in the same loop that already read each
  `BlockState`, so it needs no chunk load and no second traversal. `LevelCapture` exposes an
  adapter-only overload `capture(CaptureRequest, @Nullable Orientation audit)`; the orientation comes
  from the two-phase command path (`siting.placement().orientation()`, §11), the anchor-cell phase
  passes none and only the root frame is audited. A nested child is an absolute extent and is not
  rotated by the parent's siting.

## 4. Lease and gate

`LevelLease` is host-owned: `isCurrent()` is `!closed && the capture that minted it is still the
level's latest`. `LevelCapture` bumps its epoch on every capture and exposes `close()`; a later
capture, `LevelCapture.close()`, `LevelEvent.Unload` and `ServerStoppingEvent` invalidate older
leases. The world epoch and the watched-region mark counter below are the full specification and are
implemented (M5a, `d1fa42f`); M3's per-level stores do not change them.

The mark counter is **event-edged**: `WatchEvents` listens for `BlockEvent.NeighborNotifyEvent`
(receive-canceled), `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`, `ExplosionEvent.Detonate`,
`PistonEvent.Pre`, `ChunkEvent.Unload`, `EntityJoinLevelEvent`, `EntityLeaveLevelEvent` and
`LevelEvent.Unload`; each handler is a server-side filter that only marks the chunks of an active
capture's watched region, so an unmarked world pays nothing and a 10k-move sweep dispatches no work.
`LevelWatch` refcounts watched chunks per level, keeps a monotonic epoch, and stores the capture's
lazy fingerprints: entity anchor/footprint/type/UUID (verified through `Level.getEntity`, no chunk
load) and block-entity type/NBT (`saveWithoutMetadata`, re-read through `getChunkNow` only, never
loaded). A missing, unreadable or different entity or block entity is `isCurrent == false`, which
closes the block-entity-NBT event gap without polling. Nested child chunks join the same watch only
when the child frame is built; a cut child contributes nothing.

`LevelLease` holds the `SnapshotId`, the root view and two facts: the world epoch it was captured
under, and the watched-region mark counter since capture. `isCurrent()` is
`worldEpoch == currentEpoch && markCounter == 0 && !closed` plus the lazy fingerprint check. A block
change, a block-entity change, an entity join/leave/move in a watched window, or a chunk unload
increments the mark counter; a world unload increments the epoch. A lease is closed by the host that
opened it; the kernel never closes.

## 5. Claims

`SavedClaims` is one `SavedData` per world (M3, committed at `d4b6d82`); rows are a flat list ordered
by `(cell, owner, bay, siting, sharing)`; `revision` is a long in the same data. It is compatible with
Model C: a refused read never creates a row, a nested body is never a claim binding (`engine` §7.5),
and a failed check still writes nothing. The persisted row shape is unchanged. `settle` runs on the
server thread and is one atomic replacement of `(owner, siting)` after checking `expectedRevision`
and the matrix; `release` removes the key and bumps the revision only when something was removed;
`read` is a copy. A crash between the in-memory mutation and `setDirty` loses the revision; the host
re-reads the store on load. The `/cumulus settle` command drives this path on the server thread.

## 6. Context

`SavedContexts` stores `(instance, revision, values[id, type-text], findings, spend)` per instance
(M3, committed). It is written only by `settle`/`derive`; the server thread is the only writer. A
value's type text is the `Class.getName()` and the host's codec table maps it; an unknown type is a
load-time finding, not a crash. A `RuntimeState` type does not exist: the `StructureContext` is the
persisted value. The finding codec writes the code text, not a registry ordinal, so a persisted
`SC-OCC-0004` or relayed host code survives a reload.

## 7. Traits

### 7.1 The five stable keys

`OccupantTraits` declares exactly five public `TraitKey`s; the ids use the mod id:

| Field | Id | Java type |
| --- | --- | --- |
| `inventory` | `structurecumulus:inventory` | `List<ItemStack>` |
| `energy` | `structurecumulus:energy` | `Integer` |
| `fluid` | `structurecumulus:fluid` | `List<FluidStack>` |
| `blockEntityNbt` | `structurecumulus:block_entity_nbt` | `CompoundTag` |
| `entityNbt` | `structurecumulus:entity_nbt` | `CompoundTag` |

The inventory list copies every `IItemHandler` slot in slot order; the fluid list copies every
`IFluidHandler` tank in tank order; energy is `IEnergyStorage.getEnergyStored()`. All copies are
immutable snapshots: mutating the live handler or stack after capture never reaches the view.

### 7.2 Slot rules

One slot per id in `policy.traits()`; a trait outside the policy has no slot (unasked). The cell
stays readable regardless of trait outcome.

| Trait | Block occupant | Entity occupant |
| --- | --- | --- |
| `inventory`/`energy`/`fluid` | capability present → `Present`; capability absent → `Absent`; `state.hasBlockEntity()` with a `null` block entity → `Refused(notCaptured)` | capability present → `Present`; absent → `Absent` |
| `blockEntityNbt` | block entity present → `Present(saved copy)`; block entity state with a `null` block entity → `Refused(notCaptured)`; no block entity → `Absent` | `Absent` |
| `entityNbt` | `Absent` | `Present(entity.saveWithoutId(copy))` |
| unknown requested id | `Refused(denied)` | `Refused(denied)` |

A capability read that throws, or a handler that reports a malformed slot/tank count, is
`Refused(failed)`. Every refusing slot carries `ReadRefusal(status, SC-MC-0002)`. A positive absence
is `TraitSlot.Absent`, never a default and never `Refused`. A trait failure never makes the
containing section `Partial` and never refuses the cell: `SectionSlot.Partial` is reserved for cell
reads (build-height/unloaded-cell cases), not for traits. The four-state expectation rules are in
`structure.md` §4.6; `not(trait(...))` leaves a refused slot `Unreadable`.

### 7.3 Seam and relay

The production path is the package-private `TraitLookup` interface, implemented by the package-private
`CapabilityTraits` enum; tests use a fake `TraitLookup`. Block traits query the five-argument
capability form with a `null` context
(`level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, null)`); entity traits
query `entity.getCapability(Capabilities.ItemHandler.ENTITY, null)` and its energy/fluid siblings.
The nested capability's four-argument `Void`-context query is separate (§8.3).

The `SC-MC-0002` code is not in `Codes.all`; the kernel's `Check.relay` therefore keeps it and emits
an `Unreadable` finding with the host code plus a `refusal: <status>` note. If a host code were
already a kernel code, the kernel substitutes `SC-OCC-0004` and adds a note (`world.md` §4.1).

## 8. Nested capability

### 8.1 The public declaration

`dev.vfyjxf.cumulus.mc.capability` is the only public API a provider uses:

```java
package dev.vfyjxf.cumulus.mc.capability;

public record NestedExtent(NamespacedId type, Cuboid bounds, LatticePoint origin) {
    public NestedExtent { /* non-null; non-empty bounds; type is not the reserved air id */ }
}

public final class NestedExtents {
    public static final BlockCapability<NestedExtent, @Nullable Void> BLOCK;
    public static final EntityCapability<NestedExtent, @Nullable Void> ENTITY;
    private NestedExtents() {}
}
```

`bounds` is absolute world space, half-open. `origin` is the world position of the child-local
`(0,0,0)`: the child frame's `origin()`, with local bounds = `bounds` translated by `-origin` and
`world = origin + local`. The adapter does not clamp, re-anchor or normalise an extent; the provider's
origin is authoritative. `origin` may lie outside `bounds`; the local frame still treats `(0,0,0)` as
`origin`. An empty extent and the reserved `OccupantSnapshot.air` type are rejected at construction;
a reserved id or empty box is a provider bug, not a cut. A local translation that would leave the
lattice is the same class of provider bug and is rejected before a frame is allocated.

`NestedExtents.BLOCK` and `NestedExtents.ENTITY` are `createVoid` capabilities with id
`structurecumulus:nested_extent`, so a provider needs no context object. The constants are declared
once, in `NestedExtents`; an addon must query these constants, not create a same-named capability.

### 8.2 No built-in provider

There is **no built-in provider** for the vanilla Structure Block, and no adapter-owned source
registry. The adapter does not special-case `StructureBlockEntity`, does not read templates and does
not treat a structure block as a nested source. A structure block is metadata; a structure containing
one is not automatically nested. Third-party mods register providers on the **MOD bus** through
`RegisterCapabilitiesEvent`: `registerBlock`/`registerBlockEntity` for `NestedExtents.BLOCK` and
`registerEntity` for `NestedExtents.ENTITY`. `TemplateNestedExtent` is a future, separate feature;
it is not part of this round.

### 8.3 Safe query

The adapter queries only declarations, on the server thread, in a loaded chunk:

- Block: `level.getCapability(NestedExtents.BLOCK, pos, state, blockEntity)` — the four-argument
  `Void`-context overload, with the already-read `state` and `blockEntity`. The two- and three-
  argument overloads are never used: they re-fetch the state/block entity and can hide a read. If
  `state.hasBlockEntity()` and the already-read block entity is `null`, the cell is skipped.
- Entity: `entity.getCapability(NestedExtents.ENTITY, null)` — chunk-free, on a loaded entity the
  capture already knows.
- One query per cell/source. The first non-null provider result wins; the adapter does not merge,
  take multiple results or report a conflict. If a block and an entity at one cell both declare an
  extent, the block wins: one nested body per cell, source precedence block > entity.

### 8.4 Adapter-owned capture

A provider value is a declaration only. The adapter owns validation, frame allocation, depth, parent,
no-chunk reads, cycle detection, budget, refusal and the child view:

- One `SnapshotId` per capture. The root frame is `frame = 0`, `depth = 0`, `parent = null`, `origin =
  (0,0,0)`, with world bounds and world cells. A child gets a unique `frame`, `depth + 1`, the
  exposing view as `parent`, `origin = extent.origin()`, local bounds = `extent.bounds()` translated
  by `-origin`, and local cells; its world cells are `origin + local`. Frames are assigned in capture
  order (cell order, depth-first); a cut child rolls its frame number back, so sibling frames follow
  the anchor order (`NestedCaptureGameTest.siblingFramesFollowTheAnchorOrder`).
- One public `LevelSnapshot` implements `WorldView` over every frame; `FrameData` is package-private
  and holds a frame's sections, entities, nested bodies/extents and children. There is no
  `NestedLevelSnapshot` and no second view type.
- A nested body lives in the **parent** frame: kind `nested`, `type = extent.type()`, `body = null`,
  anchor at the cell whose declaration was used (block position or the entity's anchor cell),
  footprint = the extent bounds translated into that parent frame (identity at the root,
  `bounds - parent.origin` in a child). It is indexed separately from `SectionSnapshot`: it does not
  enter a section, a section digest or a section occupant map. `occupant(cell)` folds it with
  block/air/entity by `world.md` §4.3; `nested(cell)` is anchor-only and independent of that
  narrowing.
- A child is built under the same `SnapshotPolicy` object, so the same `kinds`/`traits` and the same
  shared element ceiling and depth ceiling apply. A child whose extent bounds leave the level's build
  height keeps the child; its local sections outside the range are `Refused(outOfBounds, SC-MC-0001)`
  (a section only partly in range is `Partial` with the outside cells refused). Those cells are never
  air. There is no partial child: either the child exists with its sections answered (readable or
  refused), or the child is cut.
- A child is **cut** when `depth + 1 > policy.maxDepth()`, when its volume would exceed the shared
  `maxElements` budget, when its `(type, origin)` already appears on the open frame path (a cycle),
  or when a chunk it needs is unloaded; a declaration that cannot be translated into its parent frame
  is cut too, never dropped silently. A cut rolls back the budget and the reserved frame, keeps the
  nested body, creates no child, makes `nested(cell)` return `null`, and the consumer reports
  `SC-OCC-0003`. A cut is never a refused child and never air. The adapter also reports the cut in
  the capture report as one `SC-MC-0003` entry (WARNING) with the reason and, when the cut happens in
  the root frame, the extent's world footprint as the entry's cells; a deeper cut carries no cells
  and names its frame and anchor in a note (`world` §4.10). The capture entry explains the cut, the
  check finding judges it, and neither suppresses the other.
- Child entities: the same policy and the same footprint rule as the root; `body` is the
  UUID-derived id, so two same-type entities at one anchor no longer collapse. Entity-hosted nested
  extents are supported by `NestedExtents.ENTITY`; the nested scan queries a loaded entity even when
  it is not indexed as an `entity` occupant, and block source precedence still applies per cell.
  `SnapshotPolicy.windows` is root-framed and is not a child capture bound; a child captures entities
  whose footprint intersects its own bounds.
- Nested focus and claims: `Focuses.nested` returns the child view with an empty subject list. A
  check that binds a nested body through `Focuses.occupant` fails at claim prediction with
  `SC-CLM-0004`; `ClaimPlanner`/`settle` reject `OccupantKind.nested` and non-root frames again
  (`engine` §7.5). A nested structure is checkable, never claimable through the nested body.

### 8.5 Test seam

There is no test mod source set and a real `RegisterCapabilitiesEvent` registration cannot be made at
runtime. `LevelCapture` therefore has package-private constructors
`LevelCapture(ServerLevel level, NestedExtentLookup lookup)` and
`LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup, TraitLookup traitLookup)`;
`NestedExtentLookup` is a package-private interface with a block method (level, pos, state, block
entity) and an entity method, returning `@Nullable NestedExtent`. The fake lookup returns extents for
chosen cells/entities; the real path queries `NestedExtents.BLOCK`/`ENTITY`. The seam is not public
API and is not on the kernel classpath.

## 9. Documents

`JsonReader` parses a JSON object into `Document`: `schemaVersion`, `id`, `bounds`, `definitions`,
`placements`, `bays` (`box`/`boxes`/`minus`), `checks` (focus/expectation trees), `computations`,
`keys`, `derivations`, `anchors`, `orientation`, `arguments`, `extensions`. Unknown members are
`SC-DOC-0002`; spans are attached when the parser records offsets. `JsonWriter` writes the schema in
canonical order, with `bounds` as origin+size and object members ascending. LowerCamel enum constants
never change a serialized tag: finding JSON takes `severity`/`phase`/`role`/`frame`/`applicability`
from `wireName()`, `SC-*` codes take their family tag from the explicit table, and both stay
uppercase, while the document shape keeps its separate lowercase vocabulary (`every_cell`,
`exclusive`, `identity`).

`DatapackBlueprints` scans `data/<ns>/cumulus/structure/*.json` on reload, stores `Document`s and
serves them through `DocumentSource.find`. A duplicate id with different bytes is a reload finding;
the first file in path order wins and the duplicate is reported. The reader/writer (M4a) and the
datapack reload (M4b) are committed. `ServerState` installs one merged `DocumentSource` in both the
server-level and the per-level host: a datapack document wins over a `BuiltinDocuments` document on
an id collision, and the built-in `structurecumulus:air_cell` remains the fallback.

## 10. Orientation

M2b implements `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror` and an upright
`BlockState` transform that applies the mirror and then the rotation. `ac11d0c` adds the non-upright
remap: `OrientationMapping.transform(Orientation, BlockState)` maps six-value `Direction`, all-three
`Direction.Axis` and `FrontAndTop` properties through `Orientation.apply`, copies neutral properties,
and answers `MappingResult.Unrepresentable(orientation, property, value)` for the first property that
cannot express the pose — the payload `SC-MC-0004` reports. The normative rule remains:
`Tilt.pitchUp`/`Tilt.pitchDown` and `Tilt.rollLeft`/`Tilt.rollRight` are legal for blocks that
support six-way facing; a block whose state cannot express the chosen orientation is a capture-time
mismatch (`SC-MC-0004`), never a silent identity.

The **orientation audit** (M5e, `28d0334`) runs in the sited (phase-2) capture: for every captured
non-air block of the root frame the adapter applies the siting orientation to the live `BlockState`
it already read and, when the result is `Unrepresentable`, groups the cell by
`(block type, property, value)`. One
`SC-MC-0004` entry (WARNING) per group carries the code, the message, notes (`block`, `property`,
`value`, `cells`, `orientation`) and the union of the group's world cells as its region. An upright
siting is not audited (an upright pose is always expressible), and the audit never reads the world
again: no chunk load, no second traversal, no `ReadRefusal`. The orientation comes from the
two-phase command path (§11); a capture taken without a siting orientation reports none.

`SC-MC-0004` is a capability fact about the observed blocks, not a value fact about the check. The
kernel's built-in `property` expectation compares the raw descriptor value and is not siting-aware,
so a rotated siting with a property expectation can also produce a `SC-EXP-0001` mismatch:
capability and value, two findings, not a contradiction. A siting-aware property expectation is a
pre-existing kernel gap and the one non-visual item still owed (`07-regeneration.md` §7); the
`FocusContext.siting()` seam exists. The definition origin's anchor property is read from the
`BlockState` properties, and `SC-MC-0004` never becomes a `ReadRefusal`.

## 11. Commands and lifecycle

All five subcommands run on the server thread. `check` returns 1 whenever it reaches a result (including a reported failed/undecided/dropped check) and 0 only on an argument/document/capture/siting failure; the mutating `settle` returns 1 only for `Formed`; `compile`, `explain` and `claims` return 1 on a produced result and 0 on a reported failure.

- `/cumulus check <id> <origin>` — capture, siting, read the per-level claims view for the captured
  bounds, then `check`; print the result and findings. The claims read and the check write nothing.
  An anchored blueprint captures twice (P1, `56c632c`): the anchor cell resolves the siting, and the
  sited bounds are the capture the orientation audit runs on.
- `/cumulus compile <id>` — host document → blueprint (server kernel); print the compile outcome and
  findings.
- `/cumulus settle <id> <origin>` — capture once, `check`, then `settle` with the same request; print
  `Formed` (rows, ledger revision, context revision) or the check/settle failure. A failed check
  writes nothing.
- `/cumulus explain <code>` — `FindingCode.parse` + code lookup; print the registered explanation, or
  the fallback explanation and 0 for an unregistered code; a malformed code is 0. The adapter
  registers its `SC-MC-*` entries in its kernel's code catalog, so its codes are explainable and
  carry their canonical severity.
- `/cumulus claims <from> <to>` — the ledger's rows in the box between two inclusive corners. The box
  is normalized to the kernel's half-open `Cuboid`; a box that leaves the lattice is a reported
  failure. Rows print in claim order and the read never writes.
- `ServerAboutToStartEvent` opens `ServerState`; `ServerStoppingEvent` closes it; `LevelEvent.Unload`
  closes that world's capture (invalidating its leases) and drops its per-level store. Persisted claims
  rows are not released; the next access reloads them from the world's saved data.

A capture report is printed as its own block, `capture <id>: findings`, before the check/settle
findings (M5e, `28d0334`): a capture failure prints its failure line and then the report, and a
successful capture prints it only when it is non-empty. Capture findings are stamped `Phase.check`,
relayed by `CaptureReports` (`world` §4.10) and never merged into a `MatchResult` bundle — so a
`SC-MC-0003` cut warning can precede a `Formed` summary without contradiction. `/cumulus check` still
returns 1 on a reached result and 0 only on an argument/document/capture/siting failure; a report
never changes the exit code.

## 12. Codes

`SC-MC-0001` unloaded chunk/not captured: implemented as `CaptureResult.Unavailable` or a refused
section/`ReadRefusal`. `SC-MC-0002` unreadable trait or block-entity read: carried in a
`TraitSlot.Refused` and relayed by the consumer, not a capture finding. `SC-MC-0003` a nested extent
the adapter cut (depth, budget, cycle, unloaded chunk, or a declaration that does not fit its parent
frame): a WARNING entry in the `CaptureResult.Captured` report, one per cut, with the reason; the
check still reports `SC-OCC-0003` when it needs the child and the adapter never fabricates a refused
child. `SC-MC-0004` a captured block state cannot express the siting orientation: a WARNING entry,
one per `(block type, property, value)` group, never a `ReadRefusal` (§10). `SC-MC-0005` the root is
over the element ceiling: an ERROR entry on `CaptureResult.TooLarge`, which stays the refusal.
`SC-MC-0001`…`0005` are adapter-only: the kernel never mints `SC-MC`, and none enters `Codes.all`.

The adapter registers those codes in its own code catalog through `Catalogs.Builder.codes`
(`AdapterCodes` holds the five codes and their entries), so each carries the canonical severity
above and an explanation, and `/cumulus explain` resolves it. The catalog is the host's, not
`Codes.all`: `Codes.all` stays at 56 entries, and a capture entry's code is never a kernel registry
entry. `CaptureReports` is the only report site: it reads the code's `CodeEntry` (or answers `error`
when the host registered none), relays the entry as a finding, and applies the kernel's trust guard —
a code that is already in `Codes.all` is replaced by `SC-OCC-0004` plus a note (`world` §4.1, §4.10).
The capture report never enters a `MatchResult` bundle; the commands print it as their own block
before the attempt's findings (§11).

## 13. What is deliberately absent

No client overlay renderer, no network payload, no schematic importer, no GUI, no scheduler in this
round. No built-in nested provider and no adapter-owned nested source registry. The overlay model is
data; a later adapter may render it. The document reader/writer is the only text format;
capture/import tools are host tools, not kernel modules.
