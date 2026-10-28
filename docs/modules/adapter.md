# Module `dev.vfyjxf.cumulus.mc` (adapter, M1 + M2a/M2b/M2c/M2d + M3 + M4 + M5a/M5d/M5e + S1–S4 + H1–H4 + T1–T3)

> The only code allowed to name Minecraft. M1 implements the host, the command and the capture-check
> path: `LevelCapture`, `LevelSnapshot`, `LevelLease`, block/block-entity descriptors, the
> `ServerState` lifecycle, in-memory `BuiltinDocuments` (`structurecumulus:air_cell`) and
> `/cumulus check <id> <origin>` (identity siting for anchorless blueprints). M2a adds loaded-entity
> capture with policy windows, full footprint indexing and entity-vs-block precedence (`world.md`
> §4.3). M2b adds `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror` and an upright
> mirror-then-rotate `BlockState` transform.
> M2c (`cb06aa8`/`64d04ce`) implements Model C, body identity, the total read and its five trait
> keys (now three capability keys after T1–T3). M2d (`f3e0338`/`d6aefc2`) implements
> `NestedExtent`/`NestedExtents`, one `LevelSnapshot` with package-private `FrameData`, child frames
> and cuts. M3 is committed at `d4b6d82`; `SavedClaims`/
> `SavedContexts` are compatible with Model C. M4a JSON reader/writer (`f2659c7`), M4b datapack reload
> (`76a8e5c`) and the command slice (`settle` at `4899d86`, plus `compile`, `explain`, `claims`) are
> implemented; capture-revision lease invalidation landed at `0f0bf25`. M5a adds the event-edged
> watched-region mark counter with lazy entity and block-entity fingerprints (`d1fa42f`); P1 fixes the
> rotated-siting capture bounds with the two-phase anchored capture (`56c632c`); M5d adds the
> non-upright orientation remap (`ac11d0c`); M5e adds the capture report the adapter answers with
> (`SC-MC-0003`/`0004`/`0005`) plus the report site the commands print (`28d0334`). T1–T3 (D-34)
> replace the M2c provider shape with generic per-holder providers and make the three capability
> traits return the capability handle itself.

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
  `AdapterCodes` catalog and the `CaptureReports` relay) at `28d0334`. T1–T3 add the generic
  `TraitProvider<T, H>`/`TraitHolder<H>`/`TraitLookup` pair registry, the one
  `CapabilityTraitProvider<T, H, C>` with `CapabilityAccesses.block`/`entity`/`entityVoid`, the
  three direct capability trait keys (`IItemHandler`, `IEnergyStorage`, `IFluidHandler`) and remove
  the NBT traits (D-34). S1–S4 add the structure-set
  layer: `Siting.compose` and the seven `SC-SET` codes (`9cf2794`), the set document IR/reader/
  writer/compiler (`00cf6c4`), the host `StructureSet` plan, `StructureSetHost` and `StructureSetResult`
  (`93f5941`), and the SS4 stress suite (`06-verification.md` §3.3). H1 adds `HostType`,
  `WorldView.get` and `Expectations.host` and retires the descriptor properties; H2 adds the per-frame
  `SnapshotLevel`, the capture wiring, the typed `WorldView.get` dispatch, snapshot anchors and the
  digest content. H3 adds the `property`/`state`/`tag`/`fluid` expectation factories and catalog, the
  `SC-MC-0006`…`0010` entries and the anchor's declared-value constraint. H4 closes the docs, counts
  and the `HostDataScenarioTest` stress group (`06-verification.md` §3.4).
- **Implemented (S6, retired by host-data H1; D-29 superseded by D-33).** A capture carrying a siting orientation
  audits every root block state through the inverse orientation and reports an unrepresentable state as
  `SC-MC-0004` (§3, §10). The definition-frame descriptor projection is retired: the kernel
  `OccupantDescriptor` is `(type, kind)`, so a descriptor carries no properties and the audit is the
  only siting fact the capture publishes (`OrientationAuditGameTest` covers the audit). The adapter
  `property`/`state`/`tag`/`fluid` expectation factories and `SC-MC-0006..0010` are implemented in §3.2
  and §12.
- **Pending.** Two items only: the performance/cost pass (block-entity-lazy and entity sweep) and fresh
  visual review screenshots. There is no regeneration milestone.
- No kernel type names a Minecraft type; the adapter may name every kernel type.

## 2. Types

| Type | Implements | Role |
| --- | --- | --- |
| `LevelCapture` | `WorldCapture` | server-thread capture into a `LevelSnapshot`; the sited orientation audit; nested extents as declarations only |
| `LevelSnapshot` | `WorldView` | one immutable view over every frame; `read`, `bounds`, `policy`, `nested`, typed `get` |
| `FrameData` | — | package-private per-frame sections, entities, nested bodies/extents, children and the frame snapshot |
| `SnapshotLevel` | `BlockGetter` | package-private; one frame's immutable host snapshot: copied section states, fluids, detached block entities and captured entities, in local coordinates |
| `HostTypes` | — | public final class; the four typed host-read tokens (`blockState`, `fluidState`, `entity`, `blockEntity`) |
| `AdapterExpectations` | — | package-private; the four host expectation factories (`property`, `state`, `tag`, `fluid`) and their catalog |
| `LevelLease` | `SnapshotLease` | `isCurrent` while open, its capture is the level's latest, the world epoch matches and the watched region is unmarked; lazy entity and block-entity fingerprints |
| `LevelWatch` / `WatchEvents` | package-private | the per-level mark registry (refcounted watched chunks, marks, epoch) and the NeoForge listeners that edge the marks |
| `OccupantDescriptors` | — | raw value → `(type, kind)` descriptor; entity body identity; nested body |
| `McAnchors` | — | anchor siting from the captured anchor `BlockState`; the declared `AnchorSpec.value` is compared at the definition origin; a missing state is `SC-MC-0010`; no live read |
| `OccupantTraits` | — | the three stable capability trait keys and generic provider dispatch into four-state slots |
| `TraitLookup` | final class (`trait`) | immutable registry keyed by `(trait id, holder id)`; `builtin()` and sequential plugin-loaded `load(Logger)`; `provider(id, holder)`, `knows(id)` |
| `TraitProvider<T, H>` | interface (`trait`) | one typed provider: `key()`, `holder()`, `get(actual, capture)` |
| `TraitHolder<H>` / `TraitHolders` | record / final class (`trait`) | typed holder id token (never a `Class<?>`); the adapter's `block` and `entity` declarations |
| `TraitPlugin` / `BuiltinTraitPlugin` | interface / final class (`trait`) | plugin contribution point; the built-in plugin registers the standard block and entity providers |
| `TraitRegister` | interface (`trait`) | the generic `<T, H>` registration face a plugin receives |
| `TraitContextKey<T>` / `TraitContext` | record / final class (`trait`) | typed immutable host context with duplicate-id and wrong-type rejection |
| `TraitCaptureContext` | record (`trait`) | capture-wide nullable `Direction` capability context plus the typed host context (`empty()`) |
| `BlockTraitContext` / `EntityTraitContext` | records (`trait`) | immutable provider inputs: level/pos/state/block entity or entity; the capture is a `get` parameter |
| `BlockTraitProviders` / `EntityTraitProviders` | final classes (`trait`) | the six standard providers as `CapabilityTraitProvider` declarations (inventory/energy/fluid per holder) |
| `CapabilityAccess<H, C>` / `CapabilityTraitProvider<T, H, C>` / `CapabilityAccesses` | functional interface / final class / final class (`trait`) | one generic capability provider; `block()`/`entity()`/`entityVoid()` map a holder to its normal capability get signature |
| `ModPlugin` / `PluginDependency` | interface / record (`plugin`) | plugin id, `before`/`after`/`none` order and `required`/`optional`/`optionalRequired` constraints |
| `PluginLookup<T>` / `AnnotationPluginLookup<T>` / `SpiPluginLookup<T>` | interface / final classes (`plugin`) | plugin discovery; NeoForge annotation scan and Java SPI |
| `PluginLoader` / `PluginLoadingException` | final class / exception (`plugin`) | deterministic Kahn loading; fatal missing/duplicate/cycle and warning/skip failures |
| `PluginMarker` | annotation (`plugin`) | marks a plugin class for annotation discovery |
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
| `JsonDocument` / `StructureSetReader` / `StructureSetWriter` | — | `kind` dispatcher; Gson tree ↔ `StructureSetDocument`; canonical set output (S2) |
| `DatapackBlueprints` | `DocumentSource` | reload listener; blueprint and structure-set ids under `data/<ns>/cumulus/structure/` (M4b + S3); wins over `BuiltinDocuments` on an id collision |
| `ServerState` | — | catalogs (including the adapter's `SC-MC-*` code entries), server-level and per-level kernels, per-level ledger/contexts, capture cache, merged set lookup and set compile |
| `StructureSet` | — | host plan: compiled set + host root siting + each satellite's composed siting, world bounds and world-space presence region; `place` reports mapping failures |
| `StructureSet.Satellite` | record | one placed satellite: id, compiled satellite, absolute siting, world bounds, presence cells |
| `StructureSet.Placement` | record | the placed set or the findings that prevented it |
| `StructureSetHost` | — | presence/link/check-all/settle-all/rollback sequencing over the kernel facade |
| `StructureSetResult` | record | per-structure outcomes and filtered claims, set findings, settled/rolled-back ids and rollback failures |
| `CumulusCommand` | — | the five `/cumulus` subcommands (`check`, `compile`, `settle`, `explain`, `claims`) |
| `FindingOutput` | — | human/JSON to chat, log or a file (absent; the commands print inline today) |
| `MatchScheduler` | — | optional host execution: submit, coalesce, markDirty, tick (absent) |

## 3. Capture

M1 copies blocks and block entities. M2a adds loaded entities, M2c adds trait slots and per-read
refusal, M2d adds nested frames, and M5e adds the capture report and, with it, the orientation audit.
The rules below are implemented.

- One server-thread call. `ServerChunkCache.getChunkNow`; never `Level.getChunk`, never a chunk load.
  A nested child uses the same rule: an unloaded chunk is a cut, never a load.
- The section walk visits every captured cell and reads its state from a per-frame copied section
  container; an all-air section is `Air(revision)`; an unloaded root chunk is
  `CaptureResult.Unavailable(notCaptured, SC-MC-0001)`. A section is never lazily resolved.
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
- The capture copies host data from the same walk (§3.1): each world section's `PalettedContainer<BlockState>`
  is copied once, the fluids are filled from the copy, block entities are detached copies and the
  captured entity candidates keep their local footprints. The per-cell occupant walk reads the state
  from the copy, so no cell is read twice.
- The orientation audit runs inside the sited capture, in the same loop that already read each
  `BlockState`, so it needs no chunk load and no second traversal. `LevelCapture` exposes an
  adapter-only overload `capture(CaptureRequest, @Nullable Orientation orientation)`; the orientation
  comes from the two-phase command path (`siting.placement().orientation()`, §11), the anchor-cell
  phase passes none and only the root frame is audited. A nested child is an absolute extent and is
  not rotated by the parent's siting.
- A sited root capture runs the **orientation audit** only: each root-frame `BlockState` is projected
  through the inverse siting orientation (`siting.placement().orientation().inverse()`, the pose that
  recovers the definition-frame state from the observed world state) and an unrepresentable state is
  grouped into an `SC-MC-0004` report entry. A `Mapped` state publishes nothing: `OccupantDescriptor`
  is `(type, kind)` and the actual state still feeds the block-entity lookup, the trait capabilities,
  the nested capability and the watch fingerprints. Child/nested frames and the phase-1 anchor
  capture are not audited: a child extent is an absolute structure (§8).

### 3.1 Snapshot and typed reads

`SnapshotLevel` is the one host snapshot per frame. It implements `BlockGetter` in **local** frame
coordinates: `getBlockState`, `getFluidState`, `getBlockEntity` and the typed `get(pos, HostType)`.
Out-of-bounds reads answer air / empty fluid / null; a missing section answers the same without a
world read. No `Level` is retained.

- `LevelCapture` copies each world section's `PalettedContainer<BlockState>` once per frame
  (`PalettedContainer.copy()`), keyed by world section coordinates; the occupant walk reads its
  `BlockState` from the copy. A section's fluids are copied into a 16³ `FluidState` array from the
  copied states.
- Block entities are copied at capture (`saveWithId` then `BlockEntity.loadStatic`) into detached
  snapshot instances keyed by the local cell; `getLevel()` is null on every one of them.
- The captured entity candidates are kept with their local footprints; `get(cell, entity)` answers the
  narrowest captured entity covering the cell.
- `FrameData` carries the snapshot per frame; nested frames build and carry their own.
- `LevelSnapshot.get(pos, type)` dispatches to the frame snapshot; `get(ref, type)` uses the ref's
  anchor. A `LevelSnapshot` built without a snapshot answers null. No typed read touches the level.
- The section digest composes the occupant digest with `SnapshotLevel.contentDigest(region)`, which
  hashes the captured block states, fluid states and entities of the section. A same-type occupant
  with a different `BlockState` therefore changes `contentDigest`.
- Heightmaps are not captured: no kernel request path exists (D-33, `10-host-data.md` §13).

### 3.2 Host expectations

`AdapterExpectations` registers four normal check kinds; each lowers to `Expectations.host` over a
typed read, so a factory only sees the captured snapshot and never the live level.

| Kind | Typed read | Mismatch | Absent | Rule |
| --- | --- | --- | --- | --- |
| `property` | `blockState` | `SC-MC-0006` | `SC-MC-0010` | the state carries the named property with the declared serialized value |
| `state` | `blockState` | `SC-MC-0007` | `SC-MC-0010` | the state equals the `BlockState` resolved from `block` + `properties` at compile time |
| `tag` (block) | `blockState` | `SC-MC-0008` | `SC-MC-0010` | `state.is(tag)` |
| `tag` (fluid) | `fluidState` | `SC-MC-0008` | `SC-MC-0010` | `fluid.is(tag)` |
| `tag` (entity) | `entity` | `SC-MC-0008` | `SC-MC-0010` | `entity.getType().is(tag)` |
| `fluid` | `fluidState` + `blockState` | `SC-MC-0009` | `SC-MC-0010` | the fluid's type is the declared fluid **and** the block is that cell's own legacy fluid block |

The `state` factory resolves its state when the document compiles; an unknown block, property or
property value is a compile finding. The `fluid` factory is two host leaves under one `all_of`: the
fluid-state leaf matches the fluid type and the block-state leaf matches `block.getBlock()` against
`block.getFluidState().createLegacyBlock().getBlock()`. A waterlogged slab therefore fails the block
leaf (its block is not the water block) even though its fluid state is water; a real water or lava
cell passes both. The composed check's `ExpectationMeta` is the kernel `Junction`'s (`all_of`,
`SC-EXP-0001`); its mismatch findings are the leaves' `SC-MC-0009`. `property`, `state` and the block tag read `HostTypes.blockState`; the fluid tag
reads `HostTypes.fluidState`; the entity tag reads `HostTypes.entity`; the fluid check reads both
block and fluid state. A missing value is `Unreadable` with `SC-MC-0010`, never a mismatch.

`ServerState` merges `AdapterExpectations.catalog()` into its expectation catalog, so an ordinary
`checks` entry whose `expect.kind` is `property`, `state`, `tag` or `fluid` compiles through the same
`DocumentCompiler` path as a kernel leaf. There is no `mc_checks` member, no separate MC JSON check
type and no kernel MC kind; the built-in kernel catalog alone reports `SC-DOC-0003` for these kinds.
A host expectation adds no `ReadScope` entry: the declared cells already pick the anchor the typed
read sees.

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

### 7.1 The three stable keys

`OccupantTraits` declares exactly three public `TraitKey`s; the ids use the mod id and the values are
the live capabilities:

| Field | Id | Java type |
| --- | --- | --- |
| `inventory` | `structurecumulus:inventory` | `IItemHandler` |
| `energy` | `structurecumulus:energy` | `IEnergyStorage` |
| `fluid` | `structurecumulus:fluid` | `IFluidHandler` |

The value is the provider's real value, not a copy the framework invents: a consumer calls
`getStackInSlot`, `getEnergyStored` or `getFluidInTank` on the capability itself. The capture lease
is the validity boundary; a consumer must not retain a handle after the lease stops being current.
No list, integer or NBT wrapper exists.

### 7.2 Slot rules

One slot per id in `policy.traits()`; a trait outside the policy has no slot (unasked). The cell
stays readable regardless of trait outcome.

| Trait | Block occupant | Entity occupant |
| --- | --- | --- |
| `inventory`/`energy`/`fluid` | capability present → `Present`; capability absent → `Absent`; `state.hasBlockEntity()` with a `null` captured block entity → `Refused(notCaptured)` | capability present → `Present`; absent → `Absent` |
| unknown requested id | `Refused(denied)` | `Refused(denied)` |

A provider that throws, returns `null` or answers a different trait id, and a capability lookup that
throws, is `Refused(failed)`. Every refusing slot carries `ReadRefusal(status, SC-MC-0002)`. A
positive absence is `TraitSlot.Absent`, never a default and never `Refused`. A trait failure never
makes the containing section `Partial` and never refuses the cell: `SectionSlot.Partial` is reserved
for cell reads (build-height/unloaded-cell cases), not for traits. The four-state expectation rules
are in `structure.md` §4.6; `not(trait(...))` leaves a refused slot `Unreadable`.

### 7.3 Providers and relay

The production path is the public `dev.vfyjxf.cumulus.mc.trait.TraitLookup` registry, keyed by
`(trait id, holder id)`. `OccupantTraits` keeps the three keys and dispatches every requested id
through `TraitLookup.provider(id, TraitHolders.block)` or `provider(id, TraitHolders.entity)` into a
generic `TraitProvider<T, H>`:

- an unknown trait id is `Refused(denied)`;
- a known trait without a provider for this holder is `Absent`, preserving the old default;
- a block state that has a block entity but whose captured block entity is `null` is
  `Refused(notCaptured)` before a provider reads it, so a capability lookup never falls back to a
  live block-entity lookup;
- a provider that throws, returns `null` or answers a different trait id is `Refused(failed)`.

The standard providers are declarations, not per-capability classes. `BlockTraitProviders` and
`EntityTraitProviders` build the six `CapabilityTraitProvider` values from
`CapabilityAccesses.block()`/`entity()`/`entityVoid()`: block providers pass
`capture.capabilityContext()` (the nullable `Direction` context) to the five-argument block get,
entity inventory uses the `Void` accessor with a `null` item-handler context, and entity energy and
fluid use the sided accessor. `BuiltinTraitPlugin` registers both groups through `TraitRegister`.
There is no NBT provider: a host that needs block-entity or entity data registers a typed provider
over the owning API (`11-trait-providers.md` §7.5).

The `SC-MC-0002` code is not in `Codes.all`; the kernel's `Check.relay` therefore keeps it and emits
an `Unreadable` finding with the host code plus a `refusal: <status>` note. If a host code were
already a kernel code, the kernel substitutes `SC-OCC-0004` and adds a note (`world.md` §4.1).

### 7.4 Plugin loading and capture context

The plugin core is a package-preserving adaption of CloudLib's `api/plugin` into
`dev.vfyjxf.cumulus.mc.plugin`: `ModPlugin`, `PluginDependency`, `PluginMarker`, `PluginLookup`,
`AnnotationPluginLookup`, `SpiPluginLookup`, `PluginLoader` and `PluginLoadingException`. There is no
dispatcher, progress tracker or dispatch queue; registration is sequential and deterministic.
`PluginLoader` validates duplicate ids (fatal), missing `required` dependencies (fatal, plugin
excluded), missing `optional` dependencies (warning, plugin loaded), missing `optionalRequired`
dependencies (warning, plugin skipped) and cycles (fatal), and orders the remainder with Kahn's
algorithm using `NamespacedId` order.

`TraitPlugin` extends `ModPlugin` with the `builtin` id and the `afterBuiltin` dependency; the built-in
plugin overrides its dependencies to empty. `TraitLookup.builtin()` registers the built-in providers
directly; `TraitLookup.load(StructureCumulus.logger)` discovers `TraitPlugin`s with
`AnnotationPluginLookup.of(TraitPlugin.class)`, ensures the built-in plugin is present, runs
`PluginLoader.loadPlugin`, and calls each `registerTraits` once against the immutable builder; a
duplicate `(trait id, holder id)` pair is fatal.

`TraitCaptureContext(capabilityContext, hostContext)` is the approved A+B context: the nullable
`Direction` capability context is passed to the block and sided entity capability accessors, while
the typed `TraitContext` host context is a `get` parameter available to every provider. `TraitContext`
is built with typed `TraitContextKey<T>` entries; a duplicate id, null key/value are rejected at
build time and a wrong-type `get` throws `IllegalArgumentException`. `LevelCapture` defaults to
`TraitLookup.builtin()` with `TraitCaptureContext.empty()`; the public
`LevelCapture(ServerLevel, TraitLookup, TraitCaptureContext)` injects both, and the existing
constructors are preserved.

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

`JsonReader` parses a JSON object into `Document`: `schemaVersion`, `id`, `bounds`, `envelope`,
`definitions`, `placements`, `bays` (`box`/`boxes`/`minus`), `checks` (focus/expectation trees),
`computations`, `keys`, `derivations`, `anchors`, `orientation`, `arguments`, `extensions`. Unknown
members are `SC-DOC-0002`; spans are attached when the parser records offsets. `envelope` is a root
boolean only; the same member inside a definition is an unknown member and `SC-DOC-0002`.
`JsonWriter` writes the schema in canonical order, emits `"envelope": true` only when set and
immediately after `bounds`, and refuses a definition that carries it. `bounds` is origin+size and
object members ascend. LowerCamel enum constants
never change a serialized tag: finding JSON takes `severity`/`phase`/`role`/`frame`/`applicability`
from `wireName()`, `SC-*` codes take their family tag from the explicit table, and both stay
uppercase, while the document shape keeps its separate lowercase vocabulary (`every_cell`,
`exclusive`, `identity`). A host payload check is an ordinary `checks` entry with `kind` `property`,
`state`, `tag` or `fluid`; the reader stores it as a normal `CheckSpec` and the adapter's factory
catalog lowers it (§3.2). There is no `mc_checks` member and no separate MC document type.

`DatapackBlueprints` scans `data/<ns>/cumulus/structure/*.json` on reload and dispatches on `kind`
through `JsonDocument`: a kindless/`blueprint` file becomes a `Document`, a `structure_set` file
becomes a `StructureSetDocument`. Both are served through `DocumentSource.find`/`findSet`; the two
id namespaces are independent, so a blueprint and a set may share one id. A duplicate id with
different bytes is a reload finding per namespace; the first file in path order wins and the
duplicate is reported. The reader/writer (M4a) and the datapack reload (M4b) are committed. S3
makes the scan accept set files instead of rejecting them; a malformed set is a located finding and
never kills the reload. `ServerState` installs one merged `DocumentSource` in both the server-level
and the per-level host: a datapack document wins over a `BuiltinDocuments` document on an id
collision, the built-in `structurecumulus:air_cell` remains the fallback, `findSet` delegates to the
datapack source, and `compileSet(StructureSetDocument)` compiles against the same merged source and
catalogs. A blueprint document takes exactly the path it took before S3.

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
non-air block of the root frame the adapter projects the live `BlockState` it already read through the
inverse siting orientation and, when the result is `Unrepresentable`, groups the cell by
`(block type, property, value)`. One `SC-MC-0004` entry (WARNING) per group carries the code, the
message, notes (`block`, `property`, `value`, `cells`, `orientation`) and the union of the group's
world cells as its region. The projection of an upright siting always succeeds, so it produces no
entry (an upright pose is always expressible), and the audit never reads the world again: no chunk
load, no second traversal, no `ReadRefusal`. The orientation comes from the two-phase command path
(§11); a capture taken without a siting orientation reports none.

`SC-MC-0004` is a capability fact about the observed blocks, not a value fact about the check. Since
`OccupantDescriptor` is `(type, kind)`, an unrepresentable state publishes no descriptor fallback and
the capture report is the only trace of the capability; the check's own value expectations are the
H3 host factories, which read the snapshot rather than a descriptor. The audit transform is the
inverse-orientation `OrientationMapping.transform` result described above, not a descriptor
publication: a value check compares the captured world state, so a host that needs definition-frame
values must capture them (D-33). `FocusContext.siting()` is unchanged. The definition origin's anchor property is read from the captured anchor `BlockState` (the
phase-1 capture is not audited) and its declared `AnchorSpec.value` is compared there, case-insensitively;
a mismatch is `SC-BPT-0006` anchor-invalid, and `SC-MC-0004` never becomes a `ReadRefusal`.

## 11. Commands and lifecycle

All five subcommands run on the server thread. `check` returns 1 whenever it reaches a result (including a reported failed/undecided/dropped check) and 0 only on an argument/document/capture/siting failure; the mutating `settle` returns 1 only for `Formed`; `compile`, `explain` and `claims` return 1 on a produced result and 0 on a reported failure.

- `/cumulus check <id> <origin>` — capture, siting, read the per-level claims view for the captured
  bounds, then `check`; print the result and findings. The claims read and the check write nothing.
  An anchored blueprint captures twice (P1, `56c632c`): the anchor cell is captured, `McAnchors`
  resolves the siting from that captured view (`view.get(origin, HostTypes.blockState)`), and the
  sited bounds are the capture the orientation audit runs on. The phase-1 view is closed after
  resolution. The anchor property still selects the orientation, and `AnchorSpec.value` is now a real
  constraint: a captured value that differs from the declaration (case-insensitively) is
  `SC-BPT-0006` anchor-invalid. An uncaptured anchor state is `SC-MC-0010`, the same
  host-value-not-captured code as the expectation absent value; `SC-MC-0001` remains the capture
  refusal (§3.2, §12).
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
section/`ReadRefusal`. `SC-MC-0002` unreadable trait or
block-entity read: carried in a `TraitSlot.Refused` and relayed by the consumer, not a capture
finding. `SC-MC-0003` a nested extent
the adapter cut (depth, budget, cycle, unloaded chunk, or a declaration that does not fit its parent
frame): a WARNING entry in the `CaptureResult.Captured` report, one per cut, with the reason; the
check still reports `SC-OCC-0003` when it needs the child and the adapter never fabricates a refused
child. `SC-MC-0004` a captured block state cannot express the siting orientation: a WARNING entry,
one per `(block type, property, value)` group, never a `ReadRefusal` (§10). `SC-MC-0005` the root is
over the element ceiling: an ERROR entry on `CaptureResult.TooLarge`, which stays the refusal.
`SC-MC-0006` a captured block state does not carry the declared property value; `SC-MC-0007` a
captured block state differs from the state resolved at compile time; `SC-MC-0008` a captured block
state, fluid state or entity type is outside the declared registry tag; `SC-MC-0009` a captured fluid
cell is not the declared real fluid block; `SC-MC-0010` a host expectation's typed read answered no
value, or `McAnchors` found no captured anchor state (always `Unreadable`/a failed siting, never a
mismatch). `SC-MC-0001`…`0010` are adapter-only, and none enters `Codes.all`. The
kernel mints no `SC-MC` code: `Expectations.host` takes its absent-value code from the caller. H3
registers the `SC-MC-0006`…`0010` catalog entries and the `property`/`state`/`tag`/`fluid` factories,
passing `SC-MC-0010` as the absent code (§3.2).

The adapter registers its codes in its own code catalog through `Catalogs.Builder.codes`
(`AdapterCodes` holds the ten codes and their entries), so each carries the canonical severity
above and an explanation, and `/cumulus explain` resolves it. The catalog is the host's, not
`Codes.all`: `Codes.all` stays at 64 entries, and a capture entry's code is never a kernel registry
entry. `CaptureReports` is the only report site: it reads the code's `CodeEntry` (or answers `error`
when the host registered none), relays the entry as a finding, and applies the kernel's trust guard —
a code that is already in `Codes.all` is replaced by `SC-OCC-0004` plus a note (`world` §4.1, §4.10).
The capture report never enters a `MatchResult` bundle; the commands print it as their own block
before the attempt's findings (§11).

## 13. Structure sets

### 13.1 Host plan

`StructureSet.place(CompiledStructureSet, Siting)` composes the host's root siting with every
satellite's relative siting (`rootSiting.compose(relativeSiting)`) and maps the root bounds, each
satellite's world bounds and its presence region into world space. The presence region is the
satellite bounds unless `PresenceSpec` overrides it. A null mapping is `SC-SPC-0001`; a presence
region outside the satellite bounds is `SC-DOC-0002`; a satellite id equal to the root blueprint id
would collide with the result key and is `SC-DOC-0004`. The returned `StructureSet.Placement` is
either the placed set or the findings, never a partially placed set.

### 13.2 Presence and budget

Presence is probed for required satellites first and then optional ones, each group in declaration
order. Presence is read through the lease's `WorldView.read`; every read cell is charged to a ceiling
seeded from the run budget's `cells`. Each structure check is a separate kernel attempt and receives
the full `Budget`, so the set's total reads can exceed `budget.cells()` (`06-verification.md` §3.3).
A satellite is present when its presence region holds a non-air block-family
occupant (`block` or `blockEntity`); entities, fluids and nested bodies do not prove presence. The
first non-air block proves presence and stops the probe; an unreadable cell or budget exhaustion
makes presence unknown. A present satellite is checked; an absent required satellite is
`SC-SET-0001`; an absent optional satellite is skipped; unknown is always `SC-SET-0004`, even for an
optional satellite, and is never treated as absent. All presence findings use phase `check`.

### 13.3 Links

The host maps `parent_cell` through the root siting and `satellite_cell` through the placed
satellite's siting, then measures the pair. `face` requires Manhattan distance one; `range` compares
squared integers under `chebyshev`/`manhattan`/`euclidean` against `max_distance`; `none` carries no
cells and no distance. A wrong distance is `SC-SET-0002`. Coverage is re-checked defensively against
each blueprint's check read scopes even though the compiler already validated it; an endpoint
outside its blueprint or uncovered is `SC-SET-0003`, and a null world mapping is `SC-SPC-0001`.
Every link of every satellite is validated independently, so a wide satellite's several face links
and a range fan-in to one parent cell are legal.

### 13.4 Check all, settle all and rollback

`StructureSetHost.run(set, lease, owner, budget, instances)` refuses a stale lease with
`SC-ENG-0002`, then runs the fixed order: presence, links, the root check, every present satellite
check, then the root and the satellites in declaration order. Each check is one `MatchRequest` with
a fresh `ClaimView` read for that structure's world bounds; the instance supplier allocates one
`InstanceId` per checked structure, root first. Any failure before a settle returns with no settle.

A settle failure stops the settle pass and releases the already-settled structures in reverse order;
a release that throws or answers anything except `Settled` is reported as `SC-SET-0007` in
`rollbackFailures`, the remaining releases still run, and nothing is retried. `settled` is the
successful settle order and `rolledBack` the successful release order. `setFindings` carries the
presence and link findings, a rejected check or settle's findings, and every `Formed` structure's
own findings (including warnings); `rollbackFailures` stays separate. `StructureSetResult.outcomes`
holds every admitted check, including a structure later rolled back; `claims` filters
`Formed.claims()` by `(owner, siting, bay)`, so a ledger-wide `ClaimSet` never leaks another
structure's rows into a per-structure view. Kernel context is not rolled back: a rolled-back
structure keeps the `StructureContext` its `Formed` settle published, and the host must invalidate
the context of every id in `rolledBack`.

### 13.5 Boundaries

There is no kernel multi-structure engine, no command that runs a set and no capture executor for a
set: the host plan and `StructureSetHost` are a library entry point the host may drive. No claim
transaction spans structures; each settle is independent and a later satellite's CAS retry sees the
earlier rows through the ledger. Rollback releases claims only; a rolled-back structure keeps its
kernel context, and `ContextStore` has no removal operation, so `StructureSetResult.rolledBack` is
the host's own invalidation list. The SS4 stress suite (`ImprovedBlastFurnaceSetStressTest`,
`TepReactorSetStressTest`, `LogicalSubnetworkSetStressTest`, `PortEnvelopeSetStressTest`,
`StructureSetRotationStressTest`, `StructureSetRollbackStressTest`; 33 tests) exercises these rules
with fake views, leases and ledgers; no GameTest drives a set.

## 14. What is deliberately absent

No client overlay renderer, no network payload, no schematic importer, no GUI, no scheduler in this
round. No built-in nested provider and no adapter-owned nested source registry. The overlay model is
data; a later adapter may render it. The document reader/writer is the only text format;
capture/import tools are host tools, not kernel modules.
