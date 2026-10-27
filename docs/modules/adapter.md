# Module `dev.vfyjxf.cumulus.mc` (adapter, M1 + partial M2)

> The only code allowed to name Minecraft. M1 implements the host, the command and the capture-check
> path: `LevelCapture`, `LevelSnapshot`, `LevelLease`, block/block-entity descriptors, the
> `ServerState` lifecycle, in-memory `BuiltinDocuments` (`structurecumulus:air_cell`) and
> `/cumulus check <id> <origin>` (identity siting for anchorless blueprints), with two GameTests
> (capture-only; capture → compile → check). M2 is partial. M2a adds loaded-entity capture with
> policy windows, full footprint indexing and entity-vs-block precedence (`world.md` §4.3), with a
> third GameTest (capture entity over block). M2b adds `Heading`/`Direction`, `Turn`/`Rotation`,
> `Flip`/`Mirror` and an upright mirror-then-rotate `BlockState` transform; non-level tilts return
> `MappingResult.UnsupportedTilt`. Traits, nested views, non-upright pitch/roll state mapping,
> `SC-MC-0002`–`0005` emission, window/mark-counter lease invalidation, claims/persistence,
> JSON/datapack and the rest of the command surface remain the specification. The kernel is complete
> without it: a host that implements the four ports can run every scenario.

## 1. Position

- Source set `main`; package `dev.vfyjxf.cumulus.mc`; the mod entry is
  `dev.vfyjxf.cumulus.StructureCumulus`.
- Minecraft 1.21.1 / NeoForge 21.1.x; one baseline, no loader abstraction and no version branches.
- At M1/M2a/M2b the adapter implements `WorldCapture`, `WorldView`, `SnapshotLease`, block,
  block-entity and entity descriptors, loaded-entity capture with `SnapshotPolicy.kinds`/`windows`,
  full footprint indexing, entity-vs-block precedence, upright orientation mapping, the `ServerState`
  lifecycle (`ServerAboutToStartEvent`, `ServerStoppingEvent`, `LevelEvent.Unload`), an in-memory
  `DocumentSource`, and `/cumulus check <id> <origin>` as its only registered command. Still pending:
  `OccupantTraits`/entity NBT traits, `NestedLevelSnapshot`/nested views, non-upright pitch/roll state
  mapping, `SC-MC-0002`–`0005` emission, window/mark-counter lease invalidation,
  `ClaimLedger`/`SavedClaims`, `ContextStore`/`SavedContexts`, `DocumentReader`/`DocumentWriter`,
  datapack documents, and the remaining commands (`compile`, `settle`, `explain`, `claims`).
- No kernel type names a Minecraft type; the adapter may name every kernel type.

## 2. Types

| Type | Implements | Role |
| --- | --- | --- |
| `LevelCapture` | `WorldCapture` | server-thread capture into a `LevelSnapshot` |
| `LevelSnapshot` | `WorldView` | immutable root view over copied sections and entity bodies |
| `NestedLevelSnapshot` | `WorldView` | child frame over the same capture, unique `frame`, local origin |
| `LevelLease` | `SnapshotLease` | `isCurrent` from a world epoch + watched mark counter |
| `WorldOccupants` | `OccupantResolver<Object>` | block state, block entity, entity, nested extent |
| `OccupantDescriptors` | — | raw value ↔ `OccupantDescriptor` (exact inverse) |
| `OccupantTraits` | — | inventory, energy, fluid, entity NBT as immutable `Trait` values |
| `OrientationMapping` | — | `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror`; upright state transform, non-upright tilt rejected |
| `SavedClaims` | `ClaimLedger` | per-world `SavedData`, CAS on the server thread |
| `SavedContexts` | `ContextStore` | per-instance values in `SavedData` |
| `JsonReader` / `JsonWriter` | `DocumentReader` / `DocumentWriter` | Gson tree ↔ `Document`; canonical output |
| `DatapackBlueprints` | `DocumentSource` | reload listener; ids under `data/<ns>/cumulus/structure/` |
| `ServerState` | — | catalogs, kernel, ledger, contexts, scheduler |
| `CumulusCommand` | — | `/cumulus compile|check|settle|explain|claims` |
| `FindingOutput` | — | human/JSON to chat, log or a file |
| `MatchScheduler` | — | optional host execution: submit, coalesce, markDirty, tick |

## 3. Capture

M1 copies blocks and block entities into section snapshots. M2a adds loaded-entity capture: when
`SnapshotPolicy.kinds` contains `entity`, loaded entities whose footprint intersects the bounds are
indexed under every covered cell, with each policy window adding its typed cells. Traits, nested
structures, entity NBT, tracked-type-first admission and capture findings (`SC-MC-0002`, `0003`,
`0005`) remain pending; the normative rules below still apply.

- One server-thread call. `ServerChunkCache.getChunkNow`; never `Level.getChunk`, never a chunk load.
- Sections are copied with `PalettedContainer.copy()`; an all-air section is `Air(revision)`; an
  unloaded chunk is `Refused(notCaptured, SC-MC-0001)`. A section is never lazily resolved.
- Block entities are read with `saveWithoutMetadata`; a required trait that cannot be read makes the
  cell `Refused(notCaptured)`; a block entity whose body cannot be read is `SC-MC-0002`. M1 maps the
  block-entity type and block properties; NBT/trait reads and `SC-MC-0002` remain pending.
- Entities intersecting `bounds.inflate(maxEntitySpan)` are queried; M2a admits a loaded entity when
  its footprint intersects the bounds, adds each policy window's typed cells, and indexes every
  covered cell. A body leaving the capture is not clipped; its covered cells outside are unreadable.
  Tracked-type-first admission is still pending. Entity-vs-block narrowing follows `world.md` §4.3:
  non-air, then smaller footprint volume, then `OccupantRef` order.
- Nested structures recurse with `maxDepth - 1`; a cycle is cut with `Refused(notCaptured,
  SC-MC-0003)` and a finding.
- `SnapshotId` is stamped last from the per-world counter; a content-equal refresh may return the
  same view object (no new id).
- Caps: the host's own capture limits (`maxElements`, `maxSections`); exceeding one returns
  `CaptureResult.TooLarge`, never a partial capture.

## 4. Lease and gate

M1's `LevelLease` is a host-owned liveness flag: `isCurrent()` is `!closed && current.getAsBoolean()`.
The world epoch and watched-region mark counter below are the specification; window/mark-counter
lease invalidation remains pending.

`LevelLease` holds the `SnapshotId`, the root view and two facts: the world epoch it was captured
under, and the watched-region mark counter since capture. `isCurrent()` is
`worldEpoch == currentEpoch && markCounter == 0 && !closed`. A block change, a block-entity change,
an entity join/leave/move in a watched window, or a chunk unload increments the mark counter; a world
unload increments the epoch. A lease is closed by the host that opened it; the kernel never closes.

## 5. Claims

`SavedClaims` is one `SavedData` per world; rows are a flat list ordered by `(cell, owner, bay)`;
`revision` is a long in the same data. `settle` runs on the server thread and is one atomic
replacement of `(owner, siting)` after checking `expectedRevision` and the matrix; `release` removes
the key and bumps the revision only when something was removed; `read` is a copy. A crash between
the in-memory mutation and `setDirty` loses the revision; the host re-reads the store on load.

## 6. Context

`SavedContexts` stores `(instance, revision, values[id, type-text] , findings, spend)` per instance.
It is written only by `settle`/`derive`; the server thread is the only writer. A value's type text is
the `Class.getName()` and the host's codec table maps it; an unknown type is a load-time finding, not
a crash. A `RuntimeState` type does not exist: the `StructureContext` is the persisted value.

## 7. Documents

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
the first file in path order wins and the duplicate is reported.

## 8. Orientation

M2b implements `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror` and an upright
`BlockState` transform that applies the mirror and then the rotation; a non-level tilt returns
`MappingResult.UnsupportedTilt`. The normative rule remains: `Tilt.pitchUp`/`Tilt.pitchDown` and
`Tilt.rollLeft`/`Tilt.rollRight` are legal for blocks that support six-way facing; a block whose
state cannot express the chosen orientation is a capture-time mismatch (`SC-MC-0004`), never a silent
identity. `SC-MC-0004` emission and non-upright pitch/roll state mapping remain pending. The
definition origin's anchor property is read from the `BlockState` properties, not from NBT.

## 9. Commands and lifecycle

- `/cumulus compile <id>` — host document → blueprint, print findings.
- `/cumulus check <id> <origin>` — capture, siting, `check`; print the result and findings.
- `/cumulus settle <id> <origin>` — `check`, then `settle` on the server thread; print `Formed` or the
  conflict.
- `/cumulus explain <code>` — code lookup; exit 1 when the code is unregistered.
- `/cumulus claims <box>` — the ledger's rows in a box.
- `ServerAboutToStartEvent` opens `ServerState`; `ServerStoppingEvent` closes it; `LevelEvent.Unload`
  releases that world's claims and closes its leases.

## 10. Codes

`SC-MC-0001` unloaded chunk/not captured; `SC-MC-0002` unreadable block entity; `SC-MC-0003` nested
cycle/depth cut; `SC-MC-0004` block state cannot express the chosen orientation; `SC-MC-0005` capture
over cap. These are adapter-only; the kernel never emits `SC-MC`.

M1/M2a/M2b emit only `SC-MC-0001` (unloaded chunk/refused section). `SC-MC-0002` through
`SC-MC-0005` remain specification; `MappingResult.UnsupportedTilt` is not yet turned into
`SC-MC-0004`.

## 11. What is deliberately absent

No client overlay renderer, no network payload, no schematic importer, no GUI, no scheduler in this
round. The overlay model is data; a later adapter may render it. The document reader/writer is the
only text format; capture/import tools are host tools, not kernel modules.
