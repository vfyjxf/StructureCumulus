# Module `dev.vfyjxf.cumulus.mc` (adapter, M1 + M2a/M2b + M3; Model C / nested specified)

> The only code allowed to name Minecraft. M1 implements the host, the command and the capture-check
> path: `LevelCapture`, `LevelSnapshot`, `LevelLease`, block/block-entity descriptors, the
> `ServerState` lifecycle, in-memory `BuiltinDocuments` (`structurecumulus:air_cell`) and
> `/cumulus check <id> <origin>` (identity siting for anchorless blueprints). M2a adds loaded-entity
> capture with policy windows, full footprint indexing and entity-vs-block precedence (`world.md`
> §4.3). M2b adds `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror` and an upright
> mirror-then-rotate `BlockState` transform; non-level tilts return `MappingResult.UnsupportedTilt`.
> M3 is committed at `d4b6d82`: `SavedClaims`, `SavedContexts`, their NBT codecs and the per-level
> `ServerState` stores, with CAS/release/reload tests; it still rebases onto Model C. **Model C and
> nested capability are specified here and not implemented yet**: body identity, `ReadRefusal`,
> `CellRead`, `SectionSlot.Partial`, `TraitSlot`, `SnapshotPolicy.traits` by id, trait/refusal
> handling in `OccupantTraits`, nested frames, `NestedExtent`/`NestedExtents`, cut rules and child
> entities. The kernel is complete without the adapter: a host that implements the ports can run
> every scenario.

## 1. Position

- Source sets `main`; packages `dev.vfyjxf.cumulus.mc` and `dev.vfyjxf.cumulus.mc.capability`; the
  mod entry is `dev.vfyjxf.cumulus.StructureCumulus`.
- Minecraft 1.21.1 / NeoForge 21.1.x; one baseline, no loader abstraction and no version branches.
- **Committed.** M1: `WorldCapture`, `WorldView`, `SnapshotLease`, block/block-entity descriptors,
  `ServerState`, in-memory `BuiltinDocuments`, `/cumulus check`, two GameTests. M2a: loaded-entity
  capture with `SnapshotPolicy.kinds`/`windows`, full footprint indexing, entity-vs-block
  precedence, a third GameTest. M2b: upright orientation mapping. M3: `SavedClaims`/
  `SavedContexts`, CAS settle/release, reload codecs, per-level stores and a claims GameTest.
- **Specified, pending implementation.** Model C: `OccupantRef.body` for entities, the total
  `CellRead`/`ReadRefusal` read, `SectionSlot.Partial`, four-state `TraitSlot`, `SnapshotPolicy.traits`
  as trait ids, `OccupantTraits`, refusal relay (`SC-MC-0002` through a `ReadRefusal`). Nested
  capability: `NestedExtent`/`NestedExtents`, one `LevelSnapshot` with package-private `FrameData`,
  child frames, budgets/cuts, child entities and entity-hosted extents. Lease invalidation by epoch
  and mark counter remains specified, not implemented; M3 persists but does not yet validate a
  watched region.
- **Pending after the rebase.** `SC-MC-0004`/`SC-MC-0005` emission, non-upright pitch/roll state
  mapping, the remaining commands (`compile`, `settle`, `explain`, `claims`) and M4 documents.
- No kernel type names a Minecraft type; the adapter may name every kernel type.

## 2. Types

| Type | Implements | Role |
| --- | --- | --- |
| `LevelCapture` | `WorldCapture` | server-thread capture into a `LevelSnapshot`; nested extents as declarations only |
| `LevelSnapshot` | `WorldView` | one immutable view over every frame; `read`, `bounds`, `policy`, `nested` |
| `FrameData` | — | package-private per-frame sections, entities, nested bodies/extents and children |
| `LevelLease` | `SnapshotLease` | `isCurrent` from a host liveness flag; epoch/mark counter pending |
| `WorldOccupants` | `OccupantResolver<Object>` | block state, block entity, entity, nested extent |
| `OccupantDescriptors` | — | raw value ↔ `OccupantDescriptor` (exact inverse) |
| `OccupantTraits` | — | immutable `TraitSlot` values for `policy.traits` by id; pending Model C |
| `NestedExtent` | record (capability) | one provider declaration: type, absolute bounds, world origin |
| `NestedExtents` | final class (capability) | the `BLOCK` and `ENTITY` capabilities |
| `NestedExtentLookup` | interface, package-private | test seam for fake nested providers |
| `OrientationMapping` | — | `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror`; upright state transform, non-upright tilt rejected |
| `SavedClaims` | `ClaimLedger` | per-world `SavedData`, CAS on the server thread (M3) |
| `SavedContexts` | `ContextStore` | per-instance values in `SavedData` (M3) |
| `JsonReader` / `JsonWriter` | `DocumentReader` / `DocumentWriter` | Gson tree ↔ `Document`; canonical output (pending M4) |
| `DatapackBlueprints` | `DocumentSource` | reload listener; ids under `data/<ns>/cumulus/structure/` (pending M4) |
| `ServerState` | — | catalogs, per-level kernel/ledger/contexts, capture cache |
| `CumulusCommand` | — | `/cumulus check` only; the rest is specified |
| `FindingOutput` | — | human/JSON to chat, log or a file (pending) |
| `MatchScheduler` | — | optional host execution: submit, coalesce, markDirty, tick (absent) |

## 3. Capture

M1 copies blocks and block entities. M2a adds loaded entities. M3 adds persistence, not capture.
Model C adds trait slots and per-read refusal; nested capability adds frames. The normative rules
below apply once the pending slices land.

- One server-thread call. `ServerChunkCache.getChunkNow`; never `Level.getChunk`, never a chunk load.
  A nested child uses the same rule: an unloaded chunk is a cut, never a load.
- Sections are copied with `PalettedContainer.copy()`; an all-air section is `Air(revision)`; an
  unloaded root chunk is `CaptureResult.Unavailable(notCaptured, SC-MC-0001)`. A section is never
  lazily resolved.
- Block entities are read with `saveWithoutMetadata`; the state and block entity are read **once**
  and passed to capability queries. If `state.hasBlockEntity()` and the already-read block entity is
  `null`, the cell is a plain block and no capability is queried; the adapter never re-fetches the
  block entity.
- A requested trait whose read throws (including a block-entity read through
  `saveWithoutMetadata`) is a `TraitSlot.Refused(failed, SC-MC-0002)` (or the status that fits:
  `denied`/`notCaptured`); the occupant itself stays readable, and an unresolvable block-entity type
  degrades to the block occupant. A trait the host can prove absent is `TraitSlot.Absent(id)`, never
  a default. A trait outside `policy.traits` has no slot.
- Entities intersecting `bounds.inflate(maxEntitySpan)` are queried; M2a admits a loaded entity when
  its footprint intersects the bounds, adds each policy window's typed cells, and indexes every
  covered cell. A body leaving the capture is not clipped; its covered cells outside are unreadable.
  Entity-vs-block narrowing follows `world.md` §4.3: non-air, then smaller footprint volume, then
  `OccupantRef` order including `body`.
- Entity identity: `OccupantDescriptors.entity(...)` mints `body =
  NamespacedId.of(entityType.namespace(), uuid.toString())`. Two entities of one type at one anchor
  are two refs and stay two occupants. This is the approved body-identity fix; the committed M2a
  descriptor still passes the five-argument ref and loads one of the two.
- Nested structures are discovered only when `policy.kinds()` contains `OccupantKind.nested`;
  otherwise no capability is queried. Nested discovery queries loaded entities intersecting the
  bounds for `NestedExtents.ENTITY` even when `entity` occupants are not captured; indexing the
  entity as an occupant itself still requires the `entity` kind. §7 owns the capability rules, frame
  construction, budgets and cuts.
- `SnapshotId` is stamped last from the per-world counter; a content-equal refresh may return the
  same view object (no new id).
- Caps: `maxElements` bounds the root plus every child frame. A root volume over the ceiling returns
  `CaptureResult.TooLarge`, never a partial root. A child over budget is cut, not partial (§7).

## 4. Lease and gate

M1's `LevelLease` is a host-owned liveness flag: `isCurrent()` is `!closed && current.getAsBoolean()`.
The world epoch and watched-region mark counter below are the specification; window/mark-counter
lease invalidation remains pending, and M3's per-level stores do not change it.

`LevelLease` holds the `SnapshotId`, the root view and two facts: the world epoch it was captured
under, and the watched-region mark counter since capture. `isCurrent()` is
`worldEpoch == currentEpoch && markCounter == 0 && !closed`. A block change, a block-entity change,
an entity join/leave/move in a watched window, or a chunk unload increments the mark counter; a world
unload increments the epoch. A lease is closed by the host that opened it; the kernel never closes.

## 5. Claims

`SavedClaims` is one `SavedData` per world (M3, committed at `d4b6d82`); rows are a flat list ordered
by `(cell, owner, bay, siting, sharing)`; `revision` is a long in the same data. `settle` runs on the
server thread and is one atomic replacement of `(owner, siting)` after checking `expectedRevision`
and the matrix; `release` removes the key and bumps the revision only when something was removed;
`read` is a copy. A crash between the in-memory mutation and `setDirty` loses the revision; the host
re-reads the store on load.

The rebase onto Model C is a semantic check, not a new store: a refused read never creates a row, a
nested body is never a claim binding (`engine` §7.5), and a failed check still writes nothing. The
persisted row shape is unchanged.

## 6. Context

`SavedContexts` stores `(instance, revision, values[id, type-text], findings, spend)` per instance
(M3, committed). It is written only by `settle`/`derive`; the server thread is the only writer. A
value's type text is the `Class.getName()` and the host's codec table maps it; an unknown type is a
load-time finding, not a crash. A `RuntimeState` type does not exist: the `StructureContext` is the
persisted value.

The rebase must round-trip the new `SC-OCC-0004` findings and any relayed host code: the finding
codec writes the code text, not a registry ordinal, so a code with no `CodeEntry` is preserved.

## 7. Nested capability (Model C, specified)

### 7.1 The public declaration

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

### 7.2 No built-in provider

There is **no built-in provider** for the vanilla Structure Block, and no adapter-owned source
registry. The adapter does not special-case `StructureBlockEntity`, does not read templates and does
not treat a structure block as a nested source. A structure block is metadata; a structure containing
one is not automatically nested. Third-party mods register providers on the **MOD bus** through
`RegisterCapabilitiesEvent`: `registerBlock`/`registerBlockEntity` for `NestedExtents.BLOCK` and
`registerEntity` for `NestedExtents.ENTITY`. `TemplateNestedExtent` is a future, separate feature;
it is not part of this round.

### 7.3 Safe query

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

### 7.4 Adapter-owned capture

A provider value is a declaration only. The adapter owns validation, frame allocation, depth, parent,
no-chunk reads, cycle detection, budget, refusal and the child view:

- One `SnapshotId` per capture. The root frame is `frame = 0`, `depth = 0`, `parent = null`, `origin =
  (0,0,0)`, with world bounds and world cells. A child gets a unique `frame`, `depth + 1`, the
  exposing view as `parent`, `origin = extent.origin()`, local bounds = `extent.bounds()` translated
  by `-origin`, and local cells; its world cells are `origin + local`. Frames are assigned in capture
  order (cell order, depth-first) and the nested-body index is sorted by ref, so equal captures
  produce equal frames and equal order.
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
- A child is **cut** when `depth + 1 > policy.maxDepth()`, when the added volume exceeds
  `maxElements`, when the source (block position or entity UUID) is already on the open frame stack
  (a cycle), or when a chunk it needs is unloaded. A cut keeps the nested body, creates no child,
  makes `nested(cell)` return `null`, and the consumer reports `SC-OCC-0003`. A cut is never a
  refused child and never air.
- Child entities: the same policy and the same footprint rule as the root; `body` is the
  UUID-derived id, so two same-type entities at one anchor no longer collapse. Entity-hosted nested
  extents are supported by `NestedExtents.ENTITY`; the nested scan queries a loaded entity even when
  it is not indexed as an `entity` occupant, and block source precedence still applies per cell.
  `SnapshotPolicy.windows` is root-framed and is not a child capture bound; a child captures entities
  whose footprint intersects its own bounds.
- Nested focus and claims: `Focuses.nested` returns the child view with an empty subject list, and
  `ClaimPlanner`/`settle` reject `OccupantKind.nested` and non-root frames as claim bindings
  (`engine` §7.5). A nested structure is checkable, never claimable through the nested body.

### 7.5 Test seam

There is no test mod source set and a real `RegisterCapabilitiesEvent` registration cannot be made at
runtime. `LevelCapture` therefore has a package-private constructor
`LevelCapture(ServerLevel level, NestedExtentLookup lookup)`; `NestedExtentLookup` is a
package-private interface with a block method (level, pos, state, block entity) and an entity method,
returning `@Nullable NestedExtent`. The fake lookup returns extents for chosen cells/entities; the
real path queries `NestedExtents.BLOCK`/`ENTITY`. The seam is not public API and is not on the kernel
classpath.

## 8. Documents

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
the first file in path order wins and the duplicate is reported. The reader/writer and datapack
reload are M4, not committed.

## 9. Orientation

M2b implements `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror` and an upright
`BlockState` transform that applies the mirror and then the rotation; a non-level tilt returns
`MappingResult.UnsupportedTilt`. The normative rule remains: `Tilt.pitchUp`/`Tilt.pitchDown` and
`Tilt.rollLeft`/`Tilt.rollRight` are legal for blocks that support six-way facing; a block whose
state cannot express the chosen orientation is a capture-time mismatch (`SC-MC-0004`), never a silent
identity. Non-level tilt (pitch/roll/inverted, the 40 Minecraft orientations, every 90° axis
permutation), capability-aware state remapping and `SC-MC-0004` emission remain pending. The
definition origin's anchor property is read from the `BlockState` properties, not from NBT.
`SC-MC-0004` is an orientation mismatch, not a read refusal; it never becomes a `ReadRefusal`.

## 10. Commands and lifecycle

- `/cumulus check <id> <origin>` — the only command implemented (M1): capture, siting, `check`; print
  the result and findings.
- `/cumulus compile <id>` — host document → blueprint, print findings (M4).
- `/cumulus settle <id> <origin>` — `check`, then `settle` on the server thread; print `Formed` or the
  conflict (M4).
- `/cumulus explain <code>` — code lookup; exit 1 when the code is unregistered (M4).
- `/cumulus claims <box>` — the ledger's rows in a box (M4).
- `ServerAboutToStartEvent` opens `ServerState`; `ServerStoppingEvent` closes it; `LevelEvent.Unload`
  releases that world's claims and closes its leases. M3 attaches the per-level `SavedClaims` and
  `SavedContexts` here.

## 11. Codes

`SC-MC-0001` unloaded chunk/not captured: implemented today as `CaptureResult.Unavailable` or a
refused section, and after M2c also as a `ReadRefusal`; `SC-MC-0002` unreadable block entity or
requested trait: carried in a `TraitSlot.Refused` and relayed by the consumer, not a capture finding;
`SC-MC-0003` nested cycle/depth/cap cut: **reserved/deferred** — this round has no capture-finding
channel, so the adapter does not produce it and does not fabricate a refused child; the consumer
reports `SC-OCC-0003`; `SC-MC-0004` block state cannot express the chosen orientation: pending, and
never a refusal; `SC-MC-0005` capture over cap: pending, the root returns `CaptureResult.TooLarge`
rather than a finding. These are adapter-only; the kernel never mints `SC-MC` and none is in
`Codes.all`, though it may relay one carried by a `ReadRefusal`.

Committed M1/M2a/M2b emit only `SC-MC-0001` (unloaded chunk/refused section). A host code carried by
a `ReadRefusal` is relayed unless it is already in `Codes.all`, in which case the kernel substitutes
`SC-OCC-0004` plus a note (`world.md` §4.1).

## 12. What is deliberately absent

No client overlay renderer, no network payload, no schematic importer, no GUI, no scheduler in this
round. No built-in nested provider and no adapter-owned nested source registry. The overlay model is
data; a later adapter may render it. The document reader/writer is the only text format;
capture/import tools are host tools, not kernel modules.
