# Module `dev.vfyjxf.cumulus.mc` (adapter, M1 + M2a/M2b/M2c/M2d + M3 + M4 + M5a/M5d/M5e + S1–S6 + H1–H4 + T1–T3 + N1)

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
> `SavedClaims` is compatible with Model C; the M3 `SavedContexts` was removed by D-40. M4a JSON reader/writer (`f2659c7`), M4b datapack reload
> (`76a8e5c`) and the command slice (`settle` at `4899d86`, plus `compile`, `explain`, `claims`) are
> implemented; capture-revision lease invalidation landed at `0f0bf25`. M5a adds the event-edged
> watched-region mark counter with lazy entity and block-entity fingerprints (`d1fa42f`); P1 fixes the
> rotated-siting capture bounds with the two-phase anchored capture (`56c632c`); M5d adds the
> non-upright orientation remap (`ac11d0c`); M5e adds the capture report the adapter answers with
> (`SC-MC-0003`/`0004`/`0005`) plus the report site the commands print (`28d0334`). T1–T3 (D-34)
> replace the M2c provider shape with generic per-holder providers and make the three capability
> traits return the capability handle itself. N1 (D-36) gives the nested-extent lookup the same
> per-holder shape: one public generic `NestedExtentLookup`, the capability source types
> `CapabilityNestedExtentProvider`/`CapabilityNestedExtentAccess` and the standard block/entity
> declarations; design 13 keeps `C` as the capability's own context and passes it to the capability
> get. S5 adds the production set driver:
> `/cumulus set check`/`set settle`, one capture for the bounding box of the root and every satellite under
> the union of their `Blueprint.reads()`, the check-only host path and a built-in demo set. S6 closes
> the set layer: `SC-SET-0008` same-siting rejection, `StructureManager` rollback
> invalidation used by the set host, and one set budget shared by presence and every member
> attempt.

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
  `FrameData`, child frames, budgets/cuts, child entities and entity-hosted extents. M3: `SavedClaims`,
  CAS settle/release (whole `(owner, siting)` key, D-38), reload codecs and per-level stores; D-40
  replaces the context `SavedData` with the host-driven `StructureManager`. M4: the JSON reader/writer,
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
  the NBT traits (D-34). N1 replaces the nested-extent seam with `NestedExtentHolder`/
  `CapabilityNestedExtentProvider`/`NestedExtentLookup`, the standard capability declarations over
  `CapabilityNestedExtentAccesses.block()`/`entity()` and the `BlockNestedExtentContext` input
  (D-36); `C` is the capability's own context (`@Nullable Direction` for the standard pair) and the
  capture passes `TraitCaptureContext.capabilityContext()` (design 13). The previous package-private
  aggregate seam and the unified `NestedExtentAccess`/`NestedExtentProvider` pair are gone. S1–S4 add the structure-set
  layer: `Siting.compose` and the `SC-SET` codes (`9cf2794`), the set document IR/reader/
  writer/compiler (`00cf6c4`), the host `StructureSet` plan, `StructureSetHost` and `StructureSetResult`
  (`93f5941`), and the SS4 stress suite (`06-verification.md` §3.3). S5 adds `SetCommand` and the
  `set check`/`set settle` literals, `CommandSupport.rootSiting`/`policy(Blueprint, Siting)`,
  `StructureSetHost.check`, the built-in `set_root`/`set_wing`/`set_demo` documents with the
  `ServerState.findSet` builtin fallback, and the real-level `StructureSetHostGameTest`/`SetCommandGameTest`
  (`06-verification.md` §3.5). S6 adds `SC-SET-0008` at compile/placement/host, the shared set budget
  and rollback invalidation through `StructureEvent.Broken` on the bound `StructureManager` (D-35). H1 adds `HostType`,
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
| `NestedExtentHolder<H>` / `NestedExtentHolders` | record / final class (capability) | typed holder id token (never a `Class<?>`); the adapter's `block` and `entity` declarations |
| `CapabilityNestedExtentProvider<H, C>` | interface (capability) | one capability provider: `holder()` and `get(actual, context)`; `C` is the capability's own context |
| `NestedExtentLookup` | final class (capability) | immutable two-source registry keyed by holder id: explicit `getCapability`/`getAttachment`, capability-first `get`, `knows`, `builder()`, `capabilities()` |
| `AttachmentNestedExtentProvider<H, T>` | interface (capability) | one attachment provider: holder/type plus `hasData`, `getExistingDataOrNull`, `getData`, `setData`, `removeData` |
| `AttachmentNestedExtentProviders` | final class (capability) | `readOnly`/`readWrite` binding factories, plus the standard `blockEntity()`/`entity()` holder adapters |
| `AttachmentHolder<H>` / `AttachmentNestedExtentReader<T>` / `AttachmentNestedExtentWriter<T>` | functional interfaces (capability) | subject → nullable `IAttachmentHolder`; attachment data → extent; extent → attachment data |
| `BlockNestedExtentContext` | record (capability) | the block provider input: level, pos, state and the nullable already-read block entity |
| `CapabilityNestedExtentAccess<H, C>` / `CapabilityNestedExtentProviders` / `CapabilityNestedExtentAccesses` / `NestedExtentProviders` | functional interface / final class / final class / final class (capability) | one capability access over `BaseCapability`; the binding factory; `block()`/`entity()`/`entityVoid()` map a holder to its capability get signature; the standard block and entity declarations |
| `OrientationMapping` | — | `Heading`/`Direction`, `Turn`/`Rotation`, `Flip`/`Mirror`; upright mirror-then-rotate transform and the non-upright capability remap, with `MappingResult.Unrepresentable` as the `SC-MC-0004` payload |
| `AdapterCodes` | final class, package-private | the adapter's `SC-MC-*` codes, their catalog `CodeEntry`s and the severity each finding carries |
| `CaptureReports` | final class, package-private | the report site: one `CaptureReport` entry → one `Finding` (severity, world label, trust guard) |
| `ClaimLedger.inMemory` | `ClaimLedger` | per-world in-memory ledger, CAS on the server thread; dies with the level's structure state (D-44) |
| `StructureManager` | `ContextStore` | in-memory per-level registry; `begin`/`commit`/`abort` and host lifecycle events (D-40) |
| `JsonReader` / `JsonWriter` | `DocumentReader` / `DocumentWriter` | Gson tree ↔ `Document`; canonical output (M4a) |
| `JsonDocument` / `StructureSetReader` / `StructureSetWriter` | — | `kind` dispatcher; Gson tree ↔ `StructureSetDocument`; canonical set output (S2) |
| `DatapackBlueprints` | `DocumentSource` | reload listener; blueprint and structure-set ids under `data/<ns>/cumulus/structure/` (M4b + S3); wins over `BuiltinDocuments` on an id collision |
| `ServerState` | — | catalogs (including the adapter's `SC-MC-*` code entries), server-level and per-level kernels, per-level ledger/structures, capture cache, merged set lookup (datapack over builtin, S5) and set compile |
| `StructureSet` | — | host plan: compiled set + host root siting + each satellite's composed siting, world bounds and world-space presence region; `place` reports mapping failures |
| `StructureSet.Satellite` | record | one placed satellite: id, compiled satellite, absolute siting, world bounds, presence cells |
| `StructureSet.Placement` | record | the placed set or the findings that prevented it |
| `StructureSetHost` | — | presence/link/check-all/settle-all/rollback sequencing over the kernel facade; `check` stops before the settle pass |
| `StructureSetResult` | record | per-structure outcomes and filtered claims, set findings, settled/rolled-back ids and rollback failures |
| `CumulusCommand` | — | the five top-level `/cumulus` subcommands (`check`, `compile`, `settle`, `explain`, `claims`) plus `set check`/`set settle` |
| `SetCommand` | final class, package-private | the structure-set driver: compile, root siting, placement, one union capture, host check or settle, the set summary and its exit code |
| `FindingOutput` | — | human/JSON to chat, log or a file (absent; the commands print inline today) |
| `MatchScheduler` | — | optional host execution: submit, coalesce, markDirty, tick (absent) |

## 3. Capture

M1 copies blocks and block entities. M2a adds loaded entities, M2c adds trait slots and per-read
refusal, M2d adds nested frames, and M5e adds the capture report and, with it, the orientation audit.
The rules below are implemented.

- One server-thread call. `ServerChunkCache.getChunkNow`; the capture never calls `Level.getChunk`.
  This covers the capture's own reads: a capability provider is host code and is handed the live
  `Level`, so a provider that reads a neighbour may cause a load — the vanilla double chest does,
  through `ChestBlock.getContainer`. That read is the provider's, not the capture's.
  A nested child uses the same rule: an unloaded chunk is a cut, never a load.
- The section walk visits every captured cell and reads its state from a per-frame copied section
  container; an all-air section is `Air(revision)`; an unloaded root chunk is
  `CaptureResult.Unavailable(notCaptured, SC-MC-0001)`. A section is never lazily resolved.
- Block entities are read `saveWithoutMetadata`; the state and block entity are read **once** and
  passed to capability queries. If `state.hasBlockEntity()` and the already-read block entity is
  `null`, the cell is a plain block and no block capability is queried; the adapter never re-fetches
  the block entity.
- Entities intersecting `bounds.inflate(maxEntitySpan)` are queried; M2a admits a loaded entity when
  its footprint intersects the bounds, adds each world-frame policy window's typed cells, and
  indexes every covered cell. A body leaving the capture is not clipped; its covered cells outside
  are unreadable.
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
world read. The snapshot retains no `Level` of its own: block states, fluids and block entities are
copies, and an entity cell carries the captured `EntityType`. A capability handle handed through the
capture is host code's object and may reach one; it is live for the lease (D-41).

- `LevelCapture` copies each world section's `PalettedContainer<BlockState>` once per frame
  (`PalettedContainer.copy()`), keyed by world section coordinates; the occupant walk reads its
  `BlockState` from the copy. A section's fluids are copied into a 16³ `FluidState` array from the
  copied states.
- Block entities are copied at capture (`saveWithId` then `BlockEntity.loadStatic`) into detached
  snapshot instances keyed by the local cell; `getLevel()` is null on every one of them.
- The captured entity candidates are kept with their local footprints; `get(cell, entityType)`
  answers the `EntityType` of the narrowest captured entity covering the cell.
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
reads `HostTypes.fluidState`; the entity tag reads `HostTypes.entityType`; the fluid check reads both
block and fluid state. A missing value is `Unreadable` with `SC-MC-0010`, never a mismatch.

A registry reference — the `state` factory's `block`, the `fluid` factory's `id` and a tag's `id` — must
carry a namespace; a bare id is refused with a hint naming `minecraft:<id>` rather than read in the
mod's namespace. An unknown block or fluid is the compile finding `SC-EXP-0005`, because both
registries are defaulted and a lookup must not answer the default. A tag id is the one registry
reference left unresolved: a later datapack may declare it, so an unknown tag matches nothing at match
time.

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

The mark counter is **write-edged** (D-47): the adapter hooks `LevelChunk#setBlockState` and
`BlockEntity#setChanged` instead of subscribing to intent events, so every server-side write is
observed once and a write that changed nothing reports nothing. `LevelWatch` keeps its chunk marks,
while `CumulusHooks` owns the instance side: a chunk-grained index finds the formed instances that
touch the written cell, and the first hit of a tick drops the instance and releases its claims — a
piston push is a batch of writes, so a pushed structure is gone rather than translated. The two
remaining main-bus listeners are `EntityJoinLevelEvent` and `EntityLeaveLevelEvent`; `LevelEvent.Load`
and `LevelEvent.Unload` still open and close the level's store, whose ledger is retired on unload.
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

`ClaimLedger.inMemory` is one ledger per world, created with that world's structure state and dropped
with it (D-44); rows are a flat list ordered by
`(cell, instance, bay, siting origin, placement orientation, placement offset, sharing)`, and
`revision` is a long beside them. It is compatible with Model C: a refused read never creates a row,
a nested body is never a claim binding (`engine` §7.5), and a failed check still writes nothing.
`settle` runs on the server thread and is one atomic replacement of one instance's rows after
checking `expectedRevision` and the matrix; `release(instance)` removes that instance's rows and
bumps the revision only when something was removed;
`read` is a copy. A crash between the in-memory mutation and `setDirty` loses the revision; the host
re-reads the store on load. The `/cumulus settle` command drives this path on the server thread.

## 6. Context

`StructureManager` is the per-level in-memory registry (D-40). `ServerState` creates one manager per
level; `SettleCommand`, `SetCommand` and `StructureSetHost` reserve a `StructureAttempt`, settle into
its draft context, then `commit(bounds)`; a failed attempt calls `abort()`. The manager stores
`StructureInstance(id, spec, context, bounds)`; no context is written to NBT. `ContextStore` remains
the narrow publish/read interface implemented by the manager and the attempt.

`WatchEvents` dispatches the host lifecycle: a player break or explosion inside a structure's bounds
is `Broken` and releases the claim rows; a placement is `Invalidated` and releases the rows; a piston
that moves any claimed cell is `Invalidated` and releases the rows — a pushed structure is gone,
not translated; a partial move/destroy is `Invalidated` and releases the rows; a level unload is
`Unloaded`, dispatched by `ServerState.unload` rather than by `WatchEvents`. The adapter produces no move event. `Loaded` exists for a host
that restores its own instances, but this adapter has no persisted instances to restore. A manager
rejects an event that would create a duplicate `(owner, siting)`; `Broken`/`Invalidated` are
idempotent. The kernel manager reports `findAt`/`findIn`/`instances` registry-wide (no `World`); the
adapter's one-manager-per-level layout makes those results per-level. `find(World, owner, siting)` is
the world-qualified tuple lookup.

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

The framework does not police this value. A check may read it, and a check that does accepts that its
verdict depends on world state; a check must not write through it (D-41, `11` §7.1). Any other entity
state is a captured trait, not a host read.

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

`dev.vfyjxf.cumulus.mc.capability` is the public API a provider uses (D-36). It is a per-holder
provider seam with separate capability and attachment source types, not an aggregate block/entity
interface:

```java
package dev.vfyjxf.cumulus.mc.capability;

public record NestedExtent(NamespacedId type, Cuboid bounds, LatticePoint origin) {
    public NestedExtent { /* non-null; non-empty bounds; type is not the reserved air id */ }
}

public final class NestedExtents {
    public static final BlockCapability<NestedExtent, @Nullable Direction> BLOCK;
    public static final EntityCapability<NestedExtent, @Nullable Direction> ENTITY;
    private NestedExtents() {}
}

public record NestedExtentHolder<H>(NamespacedId id) implements Comparable<NestedExtentHolder<?>> {
    public static <H> NestedExtentHolder<H> of(NamespacedId id) { /* ... */ }
}

public final class NestedExtentHolders {
    public static final NestedExtentHolder<BlockNestedExtentContext> block;
    public static final NestedExtentHolder<Entity> entity;
    private NestedExtentHolders() {}
}

public interface CapabilityNestedExtentProvider<H, C extends @Nullable Object> {
    NestedExtentHolder<H> holder();
    @Nullable NestedExtent get(H actual, @Nullable C context);
}

public final class NestedExtentLookup {
    public static NestedExtentLookup capabilities() { /* ... */ }
    public static Builder builder() { /* ... */ }
    public <H, C extends @Nullable Object> @Nullable NestedExtent getCapability(
            NestedExtentHolder<H> holder, H actual, @Nullable C context) { /* ... */ }
    public <H> @Nullable NestedExtent getAttachment(
            NestedExtentHolder<H> holder, H actual) { /* ... */ }
    /** Capability first; falls back to the attachment source when the capability answers null. */
    public <H, C extends @Nullable Object> @Nullable NestedExtent get(
            NestedExtentHolder<H> holder, H actual, @Nullable C context) { /* ... */ }
    public boolean knows(NestedExtentHolder<?> holder) { /* ... */ }

    public static final class Builder {
        public <H, C extends @Nullable Object> Builder add(
                CapabilityNestedExtentProvider<H, C> provider) { /* ... */ }
        public <H, T> Builder add(AttachmentNestedExtentProvider<H, T> provider) { /* ... */ }
        public NestedExtentLookup build() { /* ... */ }
    }
}

public record BlockNestedExtentContext(
    ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity
) {}

public interface CapabilityNestedExtentAccess<H, C extends @Nullable Object> {
    @Nullable NestedExtent get(BaseCapability<NestedExtent, C> capability, H actual, @Nullable C context);
}

public final class CapabilityNestedExtentProviders {
    public static <H, C extends @Nullable Object> CapabilityNestedExtentProvider<H, C> of(
            NestedExtentHolder<H> holder, BaseCapability<NestedExtent, C> capability,
            CapabilityNestedExtentAccess<H, C> access) { /* ... */ }
}

public final class CapabilityNestedExtentAccesses {
    public static CapabilityNestedExtentAccess<BlockNestedExtentContext, @Nullable Direction> block();
    public static CapabilityNestedExtentAccess<Entity, @Nullable Direction> entity();
    public static CapabilityNestedExtentAccess<Entity, @Nullable Void> entityVoid();
}

public final class NestedExtentProviders {
    public static final CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction> block;
    public static final CapabilityNestedExtentProvider<Entity, @Nullable Direction> entity;
}

public interface AttachmentNestedExtentProvider<H, T> {
    NestedExtentHolder<H> holder();
    AttachmentType<T> type();
    boolean hasData(H actual);
    @Nullable NestedExtent getExistingDataOrNull(H actual);
    @Nullable NestedExtent getData(H actual);
    @Nullable NestedExtent setData(H actual, NestedExtent value);
    @Nullable NestedExtent removeData(H actual);
}

public final class AttachmentNestedExtentProviders {
    public static <H, T> AttachmentNestedExtentProvider<H, T> readOnly(
            NestedExtentHolder<H> holder, AttachmentType<T> type,
            AttachmentHolder<H> holderOf, AttachmentNestedExtentReader<T> reader) { /* ... */ }
    public static <H, T> AttachmentNestedExtentProvider<H, T> readWrite(
            NestedExtentHolder<H> holder, AttachmentType<T> type,
            AttachmentHolder<H> holderOf, AttachmentNestedExtentReader<T> reader,
            AttachmentNestedExtentWriter<T> writer) { /* ... */ }
    public static AttachmentHolder<BlockNestedExtentContext> blockEntity() { /* ... */ }
    public static AttachmentHolder<Entity> entity() { /* ... */ }
}

@FunctionalInterface
public interface AttachmentHolder<H> {
    @Nullable IAttachmentHolder holder(H actual);
}

@FunctionalInterface
public interface AttachmentNestedExtentReader<T> {
    @Nullable NestedExtent read(T data);
}

@FunctionalInterface
public interface AttachmentNestedExtentWriter<T> {
    @Nullable T write(NestedExtent extent);
}
```

`NestedExtentHolders` is the adapter's declared subject kinds: `block` (`structurecumulus:block`,
input `BlockNestedExtentContext`) and `entity` (`structurecumulus:entity`, input the loaded `Entity`).
`NestedExtentLookup` keeps the capability and attachment registries separate. `getCapability` and
`getAttachment` are explicit; `get` is capability-first and falls back to the attachment source when
the capability answers `null` (or no capability provider is registered for that holder). The two
sources may share one holder id; the builder rejects a duplicate only within one source. The
attachment provider mirrors `IAttachmentHolder`; capture only calls `getExistingDataOrNull`, so it
never creates a default attachment or dirties a holder. The host configures the lookup through
`ServerState.installNestedExtentLookup(NestedExtentLookup)`, defaulting to
`NestedExtentLookup.capabilities()`; `§8.5` owns the test seam and the in-repo registration boundary.
The holder is the registry key and the type parameter is a compile-time token only, never a
`Class<?>`. `NestedExtentLookup.capabilities()` is the production pair over
`NestedExtentProviders.block`/`entity`; a fake registers only the providers it queries and an
unregistered holder answers `null`. The builder rejects a second provider for one holder id when it
is added; `knows(holder)` keeps "registered but absent here" apart from "unknown holder". One
`CapabilityNestedExtentProvider` binds one capability and one access; `CapabilityNestedExtentAccesses
.block()`/`entity()` are the standard accesses and `NestedExtentProviders.block`/`entity` the
standard declarations. The context `C` is the capability's own context, not a new capture record:
the block and entity standard pairs use `@Nullable Direction` and `entityVoid()` covers a
`createVoid` capability. There is no aggregate `block(...)`/`entity(...)` interface. A new subject kind adds a holder, an accessor when it is a capability, and a provider; it
does not edit the lookup or every fake (D-36).

`bounds` is absolute world space, half-open. `origin` is the world position of the child-local
`(0,0,0)`: the child frame's `origin()`, with local bounds = `bounds` translated by `-origin` and
`world = origin + local`. The adapter does not clamp, re-anchor or normalise an extent; the provider's
origin is authoritative. `origin` may lie outside `bounds`; the local frame still treats `(0,0,0)` as
`origin`. An empty extent and the reserved `OccupantSnapshot.air` type are rejected at construction;
a reserved id or empty box is a provider bug, not a cut. A local translation that would leave the
lattice is the same class of provider bug and is rejected before a frame is allocated.

`NestedExtents.BLOCK` and `NestedExtents.ENTITY` are created with the real capability context type
(`BlockCapability.createSided` / `EntityCapability.createSided`, context `@Nullable Direction`) with
id `structurecumulus:nested_extent`, so `C` is the side the capture carries and the access passes it
to `getCapability`. A Void-context capability is still expressible with `createVoid` and read
through `entityVoid()`. The constants are declared once, in `NestedExtents`; an addon must query
these constants, not create a same-named capability.

### 8.2 No built-in provider

There is **no built-in provider** for the vanilla Structure Block, and no adapter-owned source
registry. The adapter does not special-case `StructureBlockEntity`, does not read templates and does
not treat a structure block as a nested source. A structure block is metadata; a structure containing
one is not automatically nested. `NestedExtentProviders.block`/`entity` are the standard capability
declarations, not a built-in source: they read only what a mod registered. Third-party mods register
providers on the **MOD bus** through `RegisterCapabilitiesEvent`: `registerBlock`/`registerBlockEntity`
for `NestedExtents.BLOCK` and `registerEntity` for `NestedExtents.ENTITY`. `TemplateNestedExtent` is
a future, separate feature; it is not part of this round.

### 8.3 Safe query

The adapter queries only declarations, on the server thread, in a loaded chunk, through
`NestedExtentLookup.capabilities()`:

- Block: `get(NestedExtentHolders.block, new BlockNestedExtentContext(level, pos, state, blockEntity),
  side)`, where `side` is `TraitCaptureContext.capabilityContext()` (nullable). The standard access
  `CapabilityNestedExtentAccesses.block()` casts the `BaseCapability` to `BlockCapability` and calls
  the five-argument `level.getCapability(NestedExtents.BLOCK, pos, state, blockEntity, side)` with
  the already-read `state` and `blockEntity`. The two- and three-argument overloads are never used:
  they re-fetch the state/block entity and can hide a read. The capture skips the query when
  `state.hasBlockEntity()` and the already-read block entity is `null`.
- Entity: `get(NestedExtentHolders.entity, entity, side)`; `CapabilityNestedExtentAccesses.entity()`
  calls `entity.getCapability(NestedExtents.ENTITY, side)` — chunk-free, on a loaded entity the
  capture already knows. `entityVoid()` is the `Void`-context variant and passes `null` for a
  `createVoid` capability.
- One query per cell/source. The first non-null provider result wins; the adapter does not merge,
  take multiple results or report a conflict. If a block and an entity at one cell both declare an
  extent, the block wins: one nested body per cell, source precedence block > entity.
- A provider that throws is logged and treated as no declaration at the `LevelCapture` call site,
  so a broken provider cannot fail the capture; a holder with no registered provider answers `null`.

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
  `SnapshotPolicy.windows` is world-framed and is not a child capture bound; a child captures entities
  whose footprint intersects its own bounds.
- Nested focus and claims: `Focuses.nested` returns the child view with an empty subject list. A
  check that binds a nested body through `Focuses.occupant` fails at claim prediction with
  `SC-CLM-0004`; `ClaimPlanner`/`settle` reject `OccupantKind.nested` and non-root frames again
  (`engine` §7.5). A nested structure is checkable, never claimable through the nested body.

### 8.5 Test seam

There is no test mod source set and a real `RegisterCapabilitiesEvent` registration cannot be made at
runtime. The seam is the public registry itself (D-36): a fake builds
`NestedExtentLookup.builder().add(provider)` and registers only the holders its scenario queries,
with a block provider receiving `BlockNestedExtentContext` (level, pos, state, block entity) and an
entity provider receiving the loaded `Entity`, both answering `@Nullable NestedExtent` and observing
the context the lookup passes to `get(actual, context)`.
`LevelCapture` therefore keeps its package-private constructors
`LevelCapture(ServerLevel level, NestedExtentLookup lookup)`,
`LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup, TraitLookup traitLookup)` and the
capture-context form; the public constructors use `NestedExtentLookup.capabilities()`. The fake
providers return extents for chosen cells/entities, count queries, record the state the audit saw,
record the capability context or throw on a chosen cell; the production pair is
`NestedExtentProviders.block`/`entity` over `NestedExtents.BLOCK`/`ENTITY` with
`CapabilityNestedExtentAccesses.block()`/`entity()`. `NestedExtentTest` covers the extent record, the
holder ids, the lookup, duplicate registration, a direction-sensitive custom capability receiving
the bound capability and its context, the Void-context null and the standard context types;
`NestedCaptureGameTest.capturePassesTheCapabilitySideToTheBlockAndEntityProviders` covers the
capture passing `TraitCaptureContext.capabilityContext()` to both providers, and
`theStandardCapabilityAccessesAreDirectionTypedAndNullSafe` exercises the standard accesses.

An attachment source uses the same seam without a capability:
`AttachmentNestedExtentProviders.readOnly(...)`/`.readWrite(...)` bind an `AttachmentType`, an
`AttachmentHolder<H>` and an `AttachmentNestedExtentReader`/`AttachmentNestedExtentWriter`. The
standard adapters are `AttachmentNestedExtentProviders.blockEntity()` over the context's already-read
block entity (null-safe) and `.entity()` over the loaded entity. `NeoForgeRegistries.ATTACHMENT_TYPES`
is frozen before a GameTest runs, so a test cannot register its own `AttachmentType`; the attachment
tests drive the provider with a fake `IAttachmentHolder` behind the same adapter shape and check the
adapters separately. `LevelCapture` calls `NestedExtentLookup.get(...)`, which is capability-first and
falls back to `getAttachment(...)`; the attachment read uses `getExistingDataOrNull` only, so a
capture never creates a default attachment or dirties the holder. `readOnly` rejects `setData` with
`UnsupportedOperationException`; `readWrite` maps `setData`/`removeData` through the writer/reader; a
null holder is absent for every operation. The lookup rejects a duplicate holder id within each
source; a capability and an attachment may share a holder id, and `getCapability`/`getAttachment`
stay explicit. `AttachmentNestedExtentTest`
covers the reader, present/absent data, no default creation, the null holder, readOnly/readWrite,
writer-null rejection and the lookup fallback/duplicate rules; `NestedCaptureGameTest` adds the
block-entity present/absent/null-block-entity, entity, capture-no-dirty and readOnly/readWrite
GameTests, plus `installingAHostLookupInvalidatesAnOpenCaptureLease` for installing a host lookup
invalidating an open capture lease. The lookup is public adapter API but is not on the kernel classpath and no kernel type
names it.

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
datapack source and then to the built-in sets (`structurecumulus:set_demo` since S5), and
`compileSet(StructureSetDocument)` compiles against the same merged source and catalogs. A blueprint
document takes exactly the path it took before S3.

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

All commands run on the server thread. `check` returns 1 whenever it reaches a result (including a reported failed/undecided/dropped check) and 0 only on an argument/document/capture/siting failure; the mutating `settle` returns 1 only for `Formed`; `compile`, `explain` and `claims` return 1 on a produced result and 0 on a reported failure. The `set` pair runs the host in §13.5: `set check` returns 1 only when the set result carries no error finding and at least one admitted outcome, and `set settle` only when the result is `formed()`; a compile, placement, siting or capture failure is 0.

The command boundary is gated, guarded and self-cleaning (D-54). The root literal requires
`Commands.LEVEL_GAMEMASTERS` (2), so every verb inherits the gate and none opts out. Each verb body
runs inside one `CommandSupport.guarded` that catches `RuntimeException` and `StackOverflowError`,
logs the failure with the verb, answers `cumulus <verb> failed: <type>: <message>` and returns 0 —
no other error is caught. A verb that opened a draft closes it in a `finally` through
`CommandSupport.abortQuietly`; a `settle` that formed rows and then failed to commit releases that
instance's key through `CommandSupport.releaseQuietly` instead of leaving an orphan until the level
unloads.

- `/cumulus check <id> <origin>` — capture, siting, read the per-level claims view for the captured
  bounds, then `check`; print the result and findings. The claims read and the check write nothing.
  An anchored blueprint captures twice (P1, `56c632c`): the anchor cell is captured, `McAnchors`
  resolves the siting from that captured view (`view.get(origin, HostTypes.blockState)`), and the
  sited bounds are the capture the orientation audit runs on. The phase-1 view is closed after
  resolution. The capture policy maps the blueprint's entity windows through that siting, dropping a
  window that leaves the lattice (`CommandSupport.policy(Blueprint, Siting)`). The anchor property
  still selects the orientation, and `AnchorSpec.value` is now a real constraint: a captured value
  that differs from the declaration (case-insensitively) is `SC-BPT-0006` anchor-invalid. An
  uncaptured anchor state is `SC-MC-0010`, the same host-value-not-captured code as the expectation
  absent value; `SC-MC-0001` remains the capture refusal (§3.2, §12).
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
- `/cumulus set check <id> <origin>` / `/cumulus set settle <id> <origin>` — the production structure-set
  driver (§13.5): the document is resolved from the merged source (the built-in demo set is the
  fallback), compiled and placed, then captured once for the union bounds under the union policy with
  no orientation audit; the host checks every member and, for `settle`, settles them in
  declaration order. The command prints the set id/origin, the root and satellite outcomes, `settled`,
  `rolledBack`, `rollbackFailures`, the per-structure claim row counts and `firstFailure`, then the set
  findings. `set check` writes no row.
- `ServerAboutToStartEvent` opens `ServerState`; `ServerStoppingEvent` closes it, and
  `ServerStoppedEvent` closes it again on the crash path, where the stopping event never fires.
  `LevelEvent.Load` creates that world's per-level store; `LevelEvent.Unload` — handled by
  `ServerState.unload` alone — dispatches `Unloaded`, removes the store, closes the capture
  (invalidating its leases) and forgets the watcher. Per-level state is keyed by
  `ServerLevel#dimension()`, and no read path creates it: a lookup answers `null` or throws rather
  than rebuilding what an unload just removed. The level's claim ledger is created with the store
  and dropped with it, so no row outlives the instance that owns it (D-44).

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
`Codes.all`: `Codes.all` has 65 entries, and a capture entry's code is never a kernel registry
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
would collide with the result key and is `SC-DOC-0004`. A satellite whose composed siting equals the
root siting is `SC-SET-0008` (the compiler rejects the relative identity first; this arm covers a
hand-built set). The returned `StructureSet.Placement` is
either the placed set or the findings, never a partially placed set.

### 13.2 Presence and budget

Presence is probed for required satellites first and then optional ones, each group in declaration
order. Presence is read through the lease's `WorldView.read`; the set carries **one** budget, and
every presence cell is charged `(1, 0)` before it is read. Each structure attempt then receives the
set budget minus what presence and the earlier admitted attempts spent, and an admitted attempt's
`outcome.spend()` is subtracted, so the set's total reads never exceed `budget.cells()`
(`06-verification.md` §3.3, D-35). A budget-exhausted attempt is the engine's `SC-ENG-0001` and stops
the set. A satellite is present when its presence region holds a non-air block-family
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

The host's public shape (D-70):

```java
public final class StructureSetHost {
    public StructureSetHost(StructureKernel kernel);
    public StructureSetHost(StructureKernel kernel, StructureManager structures);
    public StructureSetResult run(StructureSet set, SnapshotLease lease, OwnerId owner,
                                  Budget budget, Function<StructureSpec, StructureAttempt> drafts);
}
```

`StructureSetHost.run(set, lease, owner, budget, drafts)` refuses a stale lease with
`SC-ENG-0002`, rejects a hand-built set whose satellite siting equals the root siting with
`SC-SET-0008`, then runs the fixed order: presence, links, the root check, every present satellite
check, then the root and the satellites in declaration order. Each check is one `MatchRequest` with
a fresh `ClaimView` read for that structure's world bounds; the manager begins one draft
`StructureAttempt` per checked structure, root first. Any failure before a settle returns with no settle.

A settle failure stops the settle pass, releases the already-settled structures in reverse order and
dispatches `Broken` for every formed attempt through the bound `StructureManager`, regardless of the
release verdict or a release throw. This is the rollback mechanism; it does not call
`ContextStore.remove`. A release that throws or answers anything except `Settled` is reported as
`SC-SET-0007` in `rollbackFailures`, as is a `Broken`-dispatch `RuntimeException`; the remaining
releases and dispatches still run, and nothing is retried.
`settled` is the successful settle order and `rolledBack` the successful release order. `setFindings`
carries the presence and link findings, a rejected check or settle's findings, and every `Formed`
structure's own findings (including warnings); `rollbackFailures` stays separate.
`StructureSetResult.outcomes` holds every admitted check, including a structure later rolled back;
`claims` filters `Formed.claims()` by `(owner, siting, bay)`, so a ledger-wide `ClaimSet` never leaks
another structure's rows into a per-structure view. Rollback removes the context of every formed
attempt, including the failed-release root; `rolledBack` still names only the successful releases.

### 13.5 The set command and its one capture (S5)

`/cumulus set check <id> <origin>` and `/cumulus set settle <id> <origin>` are the production driver.
`SetCommand` resolves `ServerState.structureSet(id)` (the datapack source first, then the built-in
demo set on an id match), compiles it with `ServerState.compileSet` and prints the compile findings.
The root siting is identity at the origin for an anchorless root; an anchored root takes the minimal
anchor capture (`SnapshotPolicy.minimal()`, no orientation) and `CommandSupport.siting`, and that
lease is closed before the set capture. `StructureSet.place` then maps the plan; its findings print
and a placement failure exits 0.

The set capture is **one** `LevelCapture.capture` call for the bounding box of `rootBounds()` and
every satellite's `worldBounds()`, with a null orientation. Its policy is one `SnapshotPolicy` with
the adapter's element ceiling and the union of the root's and every satellite's `Blueprint.reads()`:
maximum depth, union of kinds and traits (`ReadScope.union`), and the root and every satellite window
mapped into world space by that member's own siting (`CommandSupport.windows`); a window that leaves
the lattice under its siting is dropped. The capture report prints through `CaptureReports` before the
set summary. The lease is closed after the host run.

The host then runs `StructureSetHost.check` or `StructureSetHost.run` with `Budget.unbounded()`, the
server owner and one drafts function that reserves each draft through the level's manager.
`SetCommand` builds the call site:

```java
StructureSetHost host = new StructureSetHost(state.kernel(level));
host.run(
        plan, lease, state.owner(), Budget.unbounded(),
        spec -> state.structures(level).begin(state.world(level), spec));
```

`check` is the same presence/link/check sequence and
stops before the settle pass: no claim, no release, no rollback, empty `settled`/`claims`. The
summary prints the set id and origin, the root and satellite outcomes (`admitted`/`not admitted` in
plan order), `settled`, `rolledBack`, `rollbackFailures` count, per-structure claim row counts and
`firstFailure` (`code message`, or `none`), followed by the result's findings when non-empty. `check`
exits 1 only with no error finding and at least one admitted outcome; `settle` exits 1 only on
`formed()`.

One capture is an explicit host choice. The capture is a superset region, so its element ceiling
applies to the union box. It carries no orientation and therefore runs no orientation audit: one
world-frame capture cannot audit the root's and each satellite's frames separately, so a satellite
whose own frame would answer `SC-MC-0004` produces no entry here. The set checks still read the
captured world-frame states.

`StructureSetHostGameTest` drives the real `LevelCapture`, `SavedClaims` and `StructureManager` over a
two-cell root and an attached wing: check-all writes no row or revision, settle-all writes the three
claimed cells, a wrong member stops the settle with the root's admitted outcome and no row, a
hand-built satellite on the root siting is `SC-SET-0008` before presence with no outcome or instance,
and a one-cell shared budget leaves the root check `Undecided(SC-ENG-0001)` without a row or revision.
`SetCommandGameTest` drives the dispatcher over the built-in demo set: check and settle exit 1 and
write the two rows only on settle, while an absent required wing is `SC-SET-0001` and exits 0 on both
paths.

### 13.6 Boundaries

There is no kernel multi-structure engine and no claim transaction across structures: the host plan
and `StructureSetHost` remain a library entry point a host may drive without the command. Each settle
is independent and a later satellite's CAS retry sees the earlier rows through the ledger. Rollback
releases claims and removes the kernel context of every formed attempt through
`StructureEvent.Broken` on the bound `StructureManager`; `rolledBack` still names only the successful
releases, and a failed release or `Broken` dispatch is a `SC-SET-0007` rollback failure (D-35). Rollback cannot recreate a previous `StructureInstance` that an
earlier commit in the same run already replaced: member commits are deferred until every settle
succeeds, so a settle failure keeps the previous set, while a commit-phase failure (only reachable via
a host-contract violation) removes the committed prefix and restores old claim rows but not the old
manager instance. A set has one shared budget: presence charges `(1, 0)` per
read and each attempt receives the remainder, so the set's total reads stay within the run budget
(09 §7.3). Same-siting is rejected (`SC-SET-0008`) in the compiler, in `StructureSet.place` and by
the host's defensive check. The SS4 stress suite
(`ImprovedBlastFurnaceSetStressTest`, `TepReactorSetStressTest`,
`LogicalSubnetworkSetStressTest`, `PortEnvelopeSetStressTest`, `StructureSetRotationStressTest`,
`StructureSetRollbackStressTest`; 34 tests) exercises these rules with fake views, leases and
ledgers; S5 adds the two real-level GameTest classes above, and S6 extends them to six methods.

## 14. What is deliberately absent

No client overlay renderer, no network payload, no schematic importer, no GUI, no scheduler in this
round. No built-in nested source provider and no adapter-owned nested source registry. The overlay
model is data; a later adapter may render it. The document reader/writer is the only text format;
capture/import tools are host tools, not kernel modules.
