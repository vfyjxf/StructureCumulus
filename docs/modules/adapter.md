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
> watched-region mark counter with lazy entity and block-entity fingerprints (`d1fa42f`; the
> block-entity half and the `setChanged` feed are removed by D-61); P1 fixes the
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
  watched chunks, lazy entity and block-entity fingerprints) at `d1fa42f` — D-61 later removes the
  block-entity fingerprint and the `setChanged` feed it served. P1: the two-phase anchored
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
  and the `HostDataScenarioTest` stress group (`06-verification.md` §3.4). X05 opens the shipped
  catalogs to plugin contributions: `ModPlugin.register(CatalogRegistrar)` contributes expectation,
  focus, computation and deriver factories plus code entries, assembled once in `ServerState` over
  `CatalogRegistrar.builtin()` and the adapter tables (D-72).
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
| `SnapshotLevel` | `BlockGetter` | package-private; one frame's immutable host snapshot: copied section states, fluids and captured entities, in local coordinates; no block entity copy (D-61) |
| `HostTypes` | — | public final class; the three typed host-read tokens (`blockState`, `fluidState`, `entity`) |
| `AdapterExpectations` | — | package-private; the four host expectation factories (`property`, `state`, `tag`, `fluid`) and their catalog |
| `AdapterCatalogs` | — | package-private; seeds the catalog registrar with the adapter's expectation and code tables before any plugin contributes |
| `LevelLease` | `SnapshotLease` | `isCurrent` while open, its capture is the level's latest, the world epoch matches and the watched region is unmarked; lazy entity fingerprints |
| `LevelWatch` / `WatchEvents` | package-private | the per-level mark registry (refcounted watched chunks, marks, epoch) and the NeoForge listeners that edge the marks |
| `OccupantDescriptors` | — | raw value → `(type, kind)` descriptor; entity body identity; nested body |
| `McAnchors` | — | anchor siting from the captured anchor `BlockState`; the allowed orientation that maps the declared heading to the observed one wins; a missing state is `SC-MC-0010`; no live read |
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
| `ModPlugin` / `PluginDependency` | interface / record (`plugin`) | plugin id, `before`/`after`/`none` order, `required`/`optional`/`optionalRequired` constraints, and catalog contributions through `register(CatalogRegistrar)` (default no-op) |
| `PluginLookup<T>` / `AnnotationPluginLookup<T>` / `SpiPluginLookup<T>` | interface / final classes (`plugin`) | plugin discovery; NeoForge annotation scan and Java SPI |
| `PluginLoader` / `PluginLoadingException` | final class / exception (`plugin`) | deterministic Kahn loading; fatal missing/duplicate/cycle and warning/skip failures; the catalog register pass folds its failures into the same policy |
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
| `ServerState` | — | catalogs (built-ins plus the adapter's tables plus plugin contributions, assembled once over `CatalogRegistrar`), server-level and per-level kernels, per-level ledger/structures, capture cache, merged set lookup (datapack over builtin, S5) and set compile |
| `StructureSet` | — | host plan: compiled set + host root siting + each satellite's composed siting, world bounds and world-space presence region; `place` reports mapping failures |
| `StructureSet.Satellite` | record | one placed satellite: id, compiled satellite, absolute siting, world bounds, presence cells |
| `StructureSet.Placement` | record | the placed set or the findings that prevented it |
| `StructureSetHost` | — | presence/link/check-all/settle-all/rollback sequencing over the kernel facade; `check` stops before the settle pass |
| `StructureSetResult` | record | per-structure outcomes and filtered claims, set findings, settled/rolled-back ids, rollback failures and, for a set build, one `ApplyReport` per member |
| `CumulusCommand` | — | the five top-level `/cumulus` subcommands (`check`, `compile`, `settle`, `explain`, `claims`), `build`/`build undo`, `preview`, `prefs` and `set check`/`set settle`/`set build` |
| `SetCommand` | final class, package-private | the structure-set driver: compile, root siting, placement, one union capture, host check, settle or build, the set summary and its exit code |
| `FindingOutput` | — | human/JSON to chat, log or a file (absent; the commands print inline today) |
| `MatchScheduler` | — | optional host execution: submit, coalesce, markDirty, tick (absent) |

## 3. Capture

M1 copies blocks; the block entity copy it carried is gone (D-61). M2a adds loaded entities, M2c
adds trait slots and per-read
refusal, M2d adds nested frames, and M5e adds the capture report and, with it, the orientation audit.
The rules below are implemented.

- One server-thread call. `ServerChunkCache.getChunkNow`; the capture never calls `Level.getChunk`.
  `LevelCapture.capture` entered from another thread is rejected with an `IllegalStateException`
  naming the server thread, before any chunk is touched. Snapshot reads are exempt: a lease's view
  is an immutable snapshot and may be read from any thread.
  This covers the capture's own reads: a capability provider is host code and is handed the live
  `Level`, so a provider that reads a neighbour may cause a load — the vanilla double chest does,
  through `ChestBlock.getContainer`. That read is the provider's, not the capture's.
  A nested child uses the same rule: an unloaded chunk is a cut, never a load.
- The section walk visits every captured cell and reads its state from a per-frame copied section
  container; an all-air section is `Air(revision)`; an unloaded root chunk is
  `CaptureResult.Unavailable(notCaptured, SC-MC-0001)`. A section is never lazily resolved.
- The block entity is read **once** from the live chunk (`LevelChunk#getBlockEntity`) and passed to
  the trait and nested capability queries; nothing is copied and nothing is fingerprinted. If
  `state.hasBlockEntity()` and the read block entity is `null`, the cell is a plain block and no
  block capability is queried; the adapter never re-fetches the block entity.
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
  otherwise no capability is queried. Every frame discovers — including the frame sitting at the
  depth ceiling, which queries once, keeps what it finds and reports each body as a cut (§8.4, D-65).
  Nested discovery queries loaded entities intersecting the
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
  is copied once, the fluids are filled from the copy and the captured entity candidates keep their
  local footprints. The per-cell occupant walk reads the state from the copy, so no cell is read twice.
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
  is `(type, kind)` and the actual state still feeds the block-entity lookup, the trait capabilities
  and the nested capability. Child/nested frames and the phase-1 anchor
  capture are not audited: a child extent is an absolute structure (§8).

### 3.1 Snapshot and typed reads

`SnapshotLevel` is the one host snapshot per frame. It implements `BlockGetter` in **local** frame
coordinates: `getBlockState`, `getFluidState`, `getBlockEntity` and the typed `get(pos, HostType)`.
A typed `get` answers null for a cell that was not captured — outside the capture, a refused section,
or a cell refused individually (D-80); air is answered only for a cell that was captured holding air,
and an all-air section was captured. The `BlockGetter` face keeps answering air / empty fluid / null,
because the game interface has no null; it serves internal helpers and never reaches a check. Only the adapter's own `HostTypes`
constants dispatch — a token that is not one of them answers null. The
snapshot retains no `Level` of its own: block states and fluids are copies, and an entity cell carries
the captured `EntityType`. A capability handle handed through the
capture is host code's object and may reach one; it is live for the lease (D-41).

- `LevelCapture` copies each world section's `PalettedContainer<BlockState>` once per frame
  (`PalettedContainer.copy()`), keyed by world section coordinates; the occupant walk reads its
  `BlockState` from the copy. A section's fluids are copied into a 16³ `FluidState` array from the
  copied states.
- No block entity is copied: `getBlockEntity` answers null and `HostTypes` has no block-entity token
  (D-61). Block entity data reaches a check only through a trait — the capture resolves the trait
  once against the live block entity, and the value is the live handle (D-34).
- The captured entity candidates are kept with their local footprints; `get(cell, entityType)`
  answers the `EntityType` of the narrowest captured entity covering the cell.
- `FrameData` carries the snapshot per frame; nested frames build and carry their own.
- `LevelSnapshot.get(pos, type)` serves only cells whose `read` is `Found` and then dispatches to the
  frame snapshot; `get(ref, type)` answers a bodyless ref through that cell read and a body ref
  through the captured body index (D-52). A `LevelSnapshot` built without a snapshot answers null. No
  typed read touches the level.
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

`ServerState` seeds `AdapterExpectations.catalog()` into its catalog registrar before any plugin
contributes (§7.5), so an ordinary `checks` entry whose `expect.kind` is `property`, `state`, `tag`
or `fluid` compiles through the same
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

The mark counter is **write-edged** (D-47): the adapter hooks `LevelChunk#setBlockState`
instead of subscribing to intent events, so every server-side write is
observed once and a write that changed nothing reports nothing. The `BlockEntity#setChanged` hook D-47
added is cancelled by D-61: contents are watched by nobody. `LevelWatch` keeps its chunk marks,
while `CumulusHooks` owns the instance side: a chunk-grained index finds the formed instances that
touch the written cell, and the first hit of a tick drops the instance and releases its claims — a
piston push is a batch of writes, so a pushed structure is gone rather than translated. The two
remaining main-bus listeners are `EntityJoinLevelEvent` and `EntityLeaveLevelEvent`; `LevelEvent.Load`
and `LevelEvent.Unload` still open and close the level's store, whose ledger is retired on unload.
`LevelWatch` refcounts watched chunks per level, keeps a monotonic epoch, and stores the capture's
lazy entity fingerprints: anchor/footprint/type/UUID, verified through `Level.getEntity` with no
chunk load. A missing, unreadable or different entity is `isCurrent == false`. Block entity contents
are deliberately outside the gate (D-61): a change or a removal without a block change keeps the
lease current, and the trait's live handle answers the data. Nested child chunks join the same watch
only when the child frame is built; a cut child contributes nothing.

`LevelLease` holds the `SnapshotId`, the root view and two facts: the world epoch it was captured
under, and the watched-region mark counter since capture. `isCurrent()` is
`worldEpoch == currentEpoch && markCounter == 0 && !closed` plus the lazy entity fingerprint check. A
block change, an entity join/leave/move in a watched window, or a chunk unload
increments the mark counter; a world unload increments the epoch. A lease is closed by the host that
opened it; the kernel never closes. `isCurrent()` runs on the server thread; only `view()` may be
read from any thread.

## 5. Claims

`ClaimLedger.inMemory` is one ledger per world, created with that world's structure state and dropped
with it (D-44); rows are a flat list ordered by
`(cell, instance, bay, siting origin, placement orientation, placement offset, sharing)`, and
`revision` is a long beside them. It is compatible with Model C: a refused read never creates a row,
a nested body is never a claim binding (`engine` §7.5), and a failed check still writes nothing.
`settle` runs on the server thread and is one atomic replacement of one instance's rows after
checking `expectedRevision` and the matrix; `release(instance)` removes that instance's rows and
bumps the revision only when something was removed;
`read` is a copy. The `/cumulus settle` command drives this path on the server thread.

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
that restores its own instances, but this adapter has no persisted instances to restore. It has no
persisted claim rows either (D-44): rows live and die with the world's structure state. A manager
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
- a provider that throws (`RuntimeException` or `LinkageError`), returns `null` or answers a
  different trait id is `Refused(failed)`.

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
dependencies (warning, plugin skipped) and cycles (fatal); a dependency on a skipped plugin skips
the dependent through a non-optional constraint, cascading to a fixed point with the same
fatal/warning split. The remainder is ordered with Kahn's algorithm using `NamespacedId` order. A
candidate class that cannot be loaded — a broken static
initializer, an unresolved reference — never reaches the loader: both lookups log the class name and
the cause at error level and skip it (D-81); the `ServiceLoader` path catches `ServiceConfigurationError`
too, which is how the JDK wraps an instantiation failure. Nothing enters the loading result, so a plugin
that declared a dependency on the skipped class reports a missing dependency — that message is accurate
about what happened to it and misleading about the cause.

`TraitPlugin` extends `ModPlugin` with the `builtin` id and the `afterBuiltin` dependency; the built-in
plugin overrides its dependencies to empty. `TraitLookup.builtin()` registers the built-in providers
directly; `TraitLookup.load(StructureCumulus.logger)` discovers `TraitPlugin`s with
`AnnotationPluginLookup.of(TraitPlugin.class)`, ensures the built-in plugin is present, runs
`PluginLoader.loadPlugin`, and calls each `registerTraits` once against the immutable builder; a
plugin whose `registerTraits` throws — the builder's duplicate `(trait id, holder id)` rejection,
or a `LinkageError` from a class it cannot resolve — is a fatal `PluginLoadingException` naming
the plugin.

`TraitCaptureContext(capabilityContext, hostContext)` is the approved A+B context: the nullable
`Direction` capability context is passed to the block and sided entity capability accessors, while
the typed `TraitContext` host context is a `get` parameter available to every provider. `TraitContext`
is built with typed `TraitContextKey<T>` entries; a duplicate id, null key/value are rejected at
build time and a wrong-type `get` throws `IllegalArgumentException`. `LevelCapture` defaults to
`TraitLookup.builtin()` with `TraitCaptureContext.empty()`; the public
`LevelCapture(ServerLevel, TraitLookup, TraitCaptureContext)` injects both, and the existing
constructors are preserved.

### 7.5 Plugin contributions (X05, D-72, P7)

`ModPlugin` carries three default methods, one per contribution pass: `register(CatalogRegistrar)`
(document kinds), `registerMaterializers(MaterializerRegistrar)` and
`registerItemSources(ItemSourceRegistrar)`. A plugin overrides the passes it contributes to, every
other plugin inherits the no-op, so trait plugins and the JEI plugin load unchanged.
`ServerState` assembles them once per session while it opens: `CatalogRegistrar.builtin()` (the
kernel's six tables), `AdapterCatalogs.seed` (the `property`/`state`/`tag`/`fluid` factories and the
`SC-MC-*`/`SC-BLD-*` code entries), then the catalog pass
`PluginLoader.register(logger, "catalog", AnnotationPluginLookup.of(ModPlugin.class), registrar)` and
the extension passes over the same loaded instances
`PluginLoader.registerExtensions(logger, "extension", plugins, List.of(new PluginPass<>(
"materializers", p -> p.registerMaterializers(materializers)), new PluginPass<>("item sources",
p -> p.registerItemSources(itemSources))))`. The catalog pass returns the loaded plugins so the
extension passes never re-discover the annotations. Each registrar freezes its snapshot
(`Catalogs`, `Materializers`, `ItemSources`) and the session reads those; there is no runtime
contribution and no second assembly.

Failures use the loader's existing policy, not a new one. A plugin that throws while registering —
a `RuntimeException` or a `LinkageError` from a class it cannot resolve, including the
`SC-SPI-0003` a registrar raises when a contribution would replace an id that is already
registered — is attributed to its plugin id. The catalog pass is fatal: warnings log, fatals throw
`PluginLoadingException` out of `ServerState.open`, and the registrar's `fail`/`failures` carry the
same attribution for a host that assembles by hand. An extension pass is not fatal: the plugin's
remaining passes are skipped, the failure is logged with the plugin id and the pass name, and a
plugin that depends on it through a non-optional constraint is skipped too, so a broken extension
never takes the host down. A contribution a plugin registered before its throw stays, exactly as
the catalog pass behaves.

### 7.6 Materializer and item-source tables (P7)

`mc.build` owns the two extension tables. `Materializers` is an ordered chain — plugin contributions
in registration order, then the built-in `structurecumulus:vanilla`, which is always last — and the
first entry that answers non-null lowers the edit; a materializer answers null for edits outside its
concern, and a throwing one fails the step with `SC-BLD-0004` naming its id (design 14 §6).
`ItemSources` holds `ItemSourceProvider`s, not instances: the source needs the requesting player and
level, so `ItemSources.create(id, player, level)` builds it per run for the command's
`with <kind-id>` form (design 14 §5/§12). Both registrars apply the catalog duplicate law: the same
value twice collapses, a different value under a taken id is `SC-SPI-0003`.

`BuildExecutor` and `SetBuildHost` take the session's `Materializers` (a no-arg form defaults to
`Materializers.builtin()`, the vanilla chain); the command path passes `ServerState.materializers()`,
the preview confirm path `LevelAccess.materializers()`, so a plugin materializer lowers the real
build and the materials HUD alike. `ServerState.installPluginTables` is a package-private gametest
seam (the gametest composition is in the same package) that swaps both tables for a test and restores
them; it is not part of the public API.

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
no-chunk reads, cycle detection, budget, refusal and the child view. Expansion is automatic,
level-order and capped (D-65): a frame only ever captures itself and discovers its own nested
declarations, the driver expands one level at a time (parent order, then anchor order within a
parent) and assembles the immutable frames bottom-up, and no author-declared depth exists anywhere.

- One `SnapshotId` per capture. The root frame is `frame = 0`, `depth = 0`, `parent = null`, `origin =
  (0,0,0)`, with world bounds and world cells. A child gets a unique `frame`, `depth + 1`, the
  exposing view as `parent`, `origin = extent.origin()`, local bounds = `extent.bounds()` translated
  by `-origin`, and local cells; its world cells are `origin + local`. Frames are assigned in
  expansion order — every sibling of a level before any child of the next — so within one parent the
  anchor order decides (`NestedCaptureGameTest.siblingFramesFollowTheAnchorOrder`,
  `everySiblingIsCapturedBeforeAnyChild`); a cut child rolls its frame number back.
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
  is cut too, never dropped silently. A frame at `depth == policy.maxDepth()` still discovers and
  keeps its bodies — each of them is cut by the depth ceiling with one report entry, so "there is
  something here, I did not expand it" is always said, never silent (D-65). A cut rolls back the
  budget and the reserved frame, keeps the
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
never kills the reload. Scan-time diagnostics are rendered per finding with
`FindingRenderer.human` over the file's `SourceText`, so the reload log carries the document id,
line, column and the offending source line, not just the code and message. `ServerState` installs one merged `DocumentSource` in both the server-level
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
values must capture them (D-33). `FocusContext.siting()` is unchanged. The definition origin's
anchor property is read from the captured anchor `BlockState` (the phase-1 capture is not audited):
the captured value is the observed heading, `AnchorSpec.value` names the declared heading in the
blueprint's own coordinates (D-68), and `OrientationPolicy.select(declared, observed)` picks the
allowed orientation that maps one to the other. A value on either side that is not a heading, or
no admissible orientation, is `SC-BPT-0006` anchor-invalid, and `SC-MC-0004` never becomes a
`ReadRefusal`. A document written for the pre-D-68 rule changes meaning: its `value` named the
world orientation the drawing was made for, and it must be rewritten to the blueprint-frame
heading.

## 11. Commands and lifecycle

All commands run on the server thread. `check` returns 1 whenever it reaches a result (including a reported failed/undecided/dropped check) and 0 only on an argument/document/capture/siting failure; the mutating `settle` returns 1 only for `Formed`; `compile`, `explain` and `claims` return 1 on a produced result and 0 on a reported failure. The `set` pair runs the host in §13.5: `set check` returns 1 only when the set result carries no error finding and at least one admitted outcome, and `set settle` only when the result is `formed()`; a compile, placement, siting or capture failure is 0.

The command boundary is gated, guarded and self-cleaning (D-54). The root literal requires
`Commands.LEVEL_GAMEMASTERS` (2), so every verb inherits the gate and none opts out. Each verb body
runs inside one `CommandSupport.guarded` that catches `RuntimeException`, `StackOverflowError` and
`LinkageError`,
logs the failure with the verb, answers `cumulus <verb> failed: <type>: <message>` and returns 0 —
no other error is caught. A verb that opened a draft closes it in a `finally` through
`CommandSupport.abortQuietly`; a `settle` that formed rows and then failed to commit releases that
instance's key through `CommandSupport.releaseQuietly` instead of leaving an orphan until the level
unloads.

- `/cumulus check <id> <origin>` — capture, siting, read the per-level claims view for the captured
  bounds, then `check`; print the result and findings. check predicts with the same siting
  replacement the settle performs: the view drops the rows of the request's own siting (D-48).
  The claims read and the check write nothing.
  An anchored blueprint captures twice (P1, `56c632c`): the anchor cell is captured, `McAnchors`
  resolves the siting from that captured view (`view.get(origin, HostTypes.blockState)`), and the
  sited bounds are the capture the orientation audit runs on. The phase-1 view is closed after
  resolution. The capture policy maps the blueprint's entity windows through that siting, dropping a
  window that leaves the lattice (`CommandSupport.policy(Blueprint, Siting)`). The anchor property
  still selects the orientation: the captured value is the observed heading, `AnchorSpec.value` is
  the declared heading in the blueprint's own frame (D-68), and the allowed orientation that maps
  one to the other wins; a value on either side that is not a heading, or no admissible
  orientation, is `SC-BPT-0006` anchor-invalid. An
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
- `/cumulus set build <id> <origin> [with creative|player|container <pos>] [--dry] [--allow-removal]
  [--settle]` — the set build driver (§16.3): the same compile, root siting, placement and one union
  capture, then `SetBuildHost.run` over the placed set. It prints the set id/origin, the root and
  satellite build reports, the total consumed items, `settled`, `rolledBack`, `rollbackFailures`,
  `firstFailure` and the set findings. A plain set build exits 1 only on `built()`, a `--settle` one
  only on `formed()`.
- `ServerAboutToStartEvent` opens `ServerState`; `ServerStoppingEvent` closes it, and
  `ServerStoppedEvent` closes it again on the crash path, where the stopping event never fires.
  `LevelEvent.Load` creates that world's per-level store; `LevelEvent.Unload` — handled by
  `ServerState.unload` alone — dispatches `Unloaded` to the store that still exists, closes the
  store (its accessors answer "no state" until the level loads again), retires its claim ledger,
  closes the capture (invalidating its leases) and forgets the watcher; the closed store keeps its
  place so instance serials survive a reload. Per-level state is keyed by
  `ServerLevel#dimension()`, and no read path creates it: a lookup answers `null` or throws rather
  than rebuilding what an unload just closed. The level's claim ledger is created with the store
  and retired with it, so no row outlives the instance that owns it (D-44).

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
the adapter cut (depth — including a body discovered by the ceiling frame itself —, budget, cycle,
unloaded chunk, or a declaration that does not fit its parent
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
(`AdapterCodes` holds the ten `SC-MC` codes and their entries; `BuildCodes` holds the build family),
so each carries the canonical severity
above and an explanation, and `/cumulus explain` resolves it. The catalog is the host's, not
`Codes.all`: `Codes.all` has 69 entries, and a capture entry's code is never a kernel registry
entry. `CaptureReports` is the only report site: it reads the code's `CodeEntry` (or answers `error`
when the host registered none), relays the entry as a finding, and applies the kernel's trust guard —
a code that is already in `Codes.all` is replaced by `SC-OCC-0004` plus a note (`world` §4.1, §4.10).
The capture report never enters a `MatchResult` bundle; the commands print it as their own block
before the attempt's findings (§11).

`SC-BLD-0001`…`0008` are the adapter's build family (design 14 §14, landed with P2/P3/P7):
`0001` no `machineApplicable` remedy (WARNING), `0002` policy refusal (ERROR), `0003` item shortage
(ERROR), `0004` materialization failure — an unknown block/property/value, an unrepresentable pose,
or a throwing materializer (ERROR), `0005` transaction step failure (ERROR), `0006` incomplete
rollback (WARNING), `0007` post-build re-check still not formed (ERROR), `0008` preview session
invalidated (WARNING). They register through `AdapterCodes` under the same law as the
`SC-MC-*` codes: each entry carries its canonical severity and explanation, and no entry ever
enters `Codes.all`. `BuildCodes` mints the family and its entries, and `AdapterCatalogs` feeds them
into the code catalog; the preview reads `SC-BLD-0008` as `BuildCodes.previewStale` and registers
nothing (design 15 §10).

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
Before the settle pass the host takes over each member's siting — the formed instance at the same
owner and siting is released through the ledger — and a rollback never restores it (D-79): the
release covers only what this run settled, the previous instance's rows stay gone, and the host
re-forms the siting by settling again.

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
the adapter's element ceiling, the nested-ceiling constant (`SnapshotPolicy.defaultMaxDepth`, 16)
and the union of the root's and every satellite's `Blueprint.reads()`:
union of kinds and traits (`ReadScope.union`), and the root and every satellite window
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
releases, and a failed release or `Broken` dispatch is a `SC-SET-0007` rollback failure (D-35). Rollback never restores the previous occupant of a member's siting
(D-79): the takeover release already dropped its rows, member commits are deferred until every settle
succeeds, and a settle failure leaves the previous instance as a stale manager entry that the host
replaces only by settling again, while a commit-phase failure (only reachable via
a host-contract violation) rolls the committed prefix back the same release-only way. A set has one shared budget: presence charges `(1, 0)` per
read and each attempt receives the remainder, so the set's total reads stay within the run budget
(09 §7.3). Same-siting is rejected (`SC-SET-0008`) in the compiler, in `StructureSet.place` and by
the host's defensive check. The SS4 stress suite
(`ImprovedBlastFurnaceSetStressTest`, `TepReactorSetStressTest`,
`LogicalSubnetworkSetStressTest`, `PortEnvelopeSetStressTest`, `StructureSetRotationStressTest`,
`StructureSetRollbackStressTest`; 34 tests) exercises these rules with fake views, leases and
ledgers; S5 adds the two real-level GameTest classes above, and S6 extends them to six methods.

## 14. What is deliberately absent

No schematic importer and no scheduler. No built-in nested source provider and no adapter-owned
nested source registry. The document reader/writer is the only text format; capture/import tools
are host tools, not kernel modules. The single-structure build executor and the
`/cumulus build`/`/cumulus build undo` commands landed at P2 (§16.1); the server-side preview
session, the network payloads, the client overlay renderer and the JEI category landed at P3 and P4
(§17, design 15); the structure-set build is the set layer's own build (§16.3, design 14 §13). The
single structure's `--settle` flag is no longer absent: P7 chains the settle path after a committed,
re-check-clean run (§16.2).

The presentation pack (design 16, D-93…D-98) is specified and lands at E1–E6 (§19); E1's stage model,
staged playback, HUD tiers and banner and E2's placement feedback are in. What stays absent is stated
there — no presentation state on the server beyond the payload facts, no animation or particle work on
the server thread, and no second client config file.

## 15. Signatures

The adapter's public types join the spec gate in batches (D-74). A type is either documented here or
listed in `tools/spec-uncovered.txt` with a reason; the gate fails when a documented signature
drifts, when a public type is in neither place, and when a listed type no longer exists — so the list
only shrinks. Batch 1 covers the set host, the snapshot pair, the host tokens and the document
family; the preview types landed with P3/P4, and P7 lands the plugin (batch 2), build (batches 7
and 9) and preference (batch 8) types, so `tools/spec-uncovered.txt` keeps only the remaining
core/trait/capability batches and the non-API entries. The block compares against `src/main/java` through `AdapterIndexTest` (member-level)
and `tools/spec-check.py` (name-level).

Batch 10 is the presentation pack (§19, design 16): its types join this block in the milestone that
lands them (E1 the stage model and the payload facts, E2 the placement feedback, E4 the view modes,
E5 the guide), never before — a type documented here that the sources do not declare fails the gate,
so a reserved batch is a note here and a shape in design 16, not a signature in this block. A
landing milestone either extends this block or lists a non-API type in `tools/spec-uncovered.txt`
with a reason; it may not leave a public presentation type in neither place. The `cine` package is
the exception that proves the rule: the cinematography core is a pure-math foundation with no event
wiring, landed ahead of its E8 integration so the milestones above and E8 share one math; E8 adds
`CinematicPrograms` there and the two client controllers beside it (§19).

```java
package dev.vfyjxf.cumulus.mc;

public final class ClientConfig {
    public static final ModConfigSpec spec;
    public static boolean animationEnabled();
    public static double animationSpeed();
    public static boolean animationSound();
    public static boolean animationCelebrate();
    public static boolean hudProgress();
    public static boolean hudStageBanner();
    public static boolean cinematicsEnabled();
    public static double cinematicsSpeed();
    public static boolean cinematicsLetterbox();
}

public final class BuildPresetCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> literal();
}

public final class LevelAccess {
    public static @Nullable StructureKernel kernel();
    public static @Nullable LevelCapture capture(ServerLevel level);
    public static Materializers materializers();
    public static @Nullable ClaimLedger claims(ServerLevel level);
    public static @Nullable StructureManager structures(ServerLevel level);
    public static @Nullable World world(ServerLevel level);
    public static @Nullable OwnerId owner();
    public static long blockRevision(ServerLevel level, Cuboid region);
    public static java.util.List<NamespacedId> documentIds();
}

public final class StructureSetHost {
    public StructureSetHost(StructureKernel kernel);
    public StructureSetHost(StructureKernel kernel, StructureManager structures);
    public StructureSetResult run(StructureSet set, SnapshotLease lease, OwnerId owner, Budget budget,
                                  java.util.function.Function<StructureSpec, StructureAttempt> drafts);
    public StructureSetResult run(StructureSet set, SnapshotLease lease, OwnerId owner, Budget budget,
                                  StructureManager structures,
                                  java.util.function.Function<StructureSpec, StructureAttempt> drafts);
    public StructureSetResult check(StructureSet set, SnapshotLease lease, OwnerId owner, Budget budget,
                                    java.util.function.Function<StructureSpec, StructureAttempt> drafts);
    public StructureSetResult check(StructureSet set, SnapshotLease lease, OwnerId owner, Budget budget,
                                    StructureManager structures,
                                    java.util.function.Function<StructureSpec, StructureAttempt> drafts);
}

public final class LevelSnapshot implements WorldView {
    public LevelSnapshot(SnapshotId snapshot, Cuboid bounds, java.util.Map<SectionKey, SectionSlot> sections);
    public LevelSnapshot(SnapshotId snapshot, Cuboid bounds, java.util.Map<SectionKey, SectionSlot> sections,
                         java.util.List<OccupantSnapshot> entities);
    public LevelSnapshot(SnapshotId snapshot, Cuboid bounds, java.util.Map<SectionKey, SectionSlot> sections,
                         java.util.List<OccupantSnapshot> entities, SnapshotPolicy policy);
    @Override public SnapshotId snapshotId();
    @Override public int frame();
    @Override public int depth();
    @Override public LatticePoint origin();
    @Override public @Nullable WorldView parent();
    @Override public int cellSize();
    @Override public Cuboid bounds();
    @Override public SnapshotPolicy policy();
    @Override public SectionKey sectionOf(LatticePoint cell);
    @Override public SectionSlot slot(SectionKey key);
    @Override public CellRead read(LatticePoint cell);
    @Override public @Nullable OccupantSnapshot cellOccupant(LatticePoint cell);
    @Override public @Nullable OccupantSnapshot occupant(OccupantRef ref);
    @Override public java.util.List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);
    @Override public @Nullable WorldView nested(LatticePoint cell);
    @Override public <H> @Nullable H get(LatticePoint pos, HostType<H> type);
    @Override public long contentDigest(SectionKey key);
}

final class SnapshotLevel implements BlockGetter {
    static final int sectionSize = 16;
    SnapshotLevel(LatticePoint origin, Cuboid bounds, int minBuildHeight, int height,
                  java.util.Map<Long, PalettedContainer<BlockState>> sections,
                  java.util.Map<Long, FluidState[]> fluids,
                  java.util.List<CapturedEntity> entities);
    LatticePoint origin();
    Cuboid bounds();
    @Override public int getHeight();
    @Override public int getMinBuildHeight();
    @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos);
    @Override public BlockState getBlockState(BlockPos pos);
    @Override public FluidState getFluidState(BlockPos pos);
    <H> @Nullable H get(LatticePoint pos, HostType<H> type);
    long contentDigest(Cuboid cells);
    static long worldSectionKey(int worldX, int worldY, int worldZ);
    static int fluidIndex(int worldX, int worldY, int worldZ);
    record CapturedEntity(java.util.UUID id, NamespacedId type, Cuboid footprint, EntityType<?> entityType) { }
}

public final class HostTypes {
    public static final HostType<BlockState> blockState;
    public static final HostType<FluidState> fluidState;
    public static final HostType<EntityType<?>> entityType;
    private HostTypes() {}
}

public final class LevelCapture implements WorldCapture {
    public LevelCapture(ServerLevel level);
    public LevelCapture(ServerLevel level, TraitLookup traitLookup, TraitCaptureContext traitCapture);
    public LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup);
    LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup, TraitLookup traitLookup);
    LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup, TraitLookup traitLookup,
                 TraitCaptureContext traitCapture);
    ServerLevel level();
    @Override public CaptureResult capture(CaptureRequest request);
    public CaptureResult capture(CaptureRequest request, @Nullable Orientation orientation);
    @Override public long currentRevision();
    void close();
    @Nullable WatchHandle watchHandle();
}

public sealed interface JsonDocument {
    record Blueprint(Document document) implements JsonDocument { }
    record StructureSet(StructureSetDocument document) implements JsonDocument { }
    static @Nullable JsonDocument read(NamespacedId source, String text, java.util.List<Finding> errors);
}

public final class JsonReader implements DocumentReader {
    @Override public @Nullable Document read(NamespacedId source, String text, java.util.List<Finding> errors);
    static Finding parseFailure(NamespacedId source, String text, RuntimeException failure);
}

public final class JsonWriter implements DocumentWriter {
    @Override public String write(Document document);
}

package dev.vfyjxf.cumulus.mc.preview;

public final class PreviewCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> literal();
}

public final class PreviewService {
    public static int open(CommandSourceStack source, NamespacedId id, @Nullable LatticePoint origin);
    public static void open(ServerPlayer player, NamespacedId id);
    public static int openAt(CommandSourceStack source, BuildPreset preset, ItemSource items);
    public static void control(ServerPlayer player, PreviewControlPayload.Control control, @Nullable LatticePoint target);
    public static @Nullable PreviewContext context(ServerPlayer player);
    public static void installConfirmHandler(PreviewConfirmHandler handler);
}

public record PreviewContext(NamespacedId blueprintId, Siting siting, BuildPreset.Source source,
                             RemovalPolicy removalPolicy) { }

public interface PreviewConfirmHandler {
    boolean confirm(ServerPlayer player, Request request);

    record Request(NamespacedId blueprintId, Siting siting, java.util.List<String> codes,
                   ItemSource source, SolvePreferences preferences) { }
}

public final class PreviewEvents { }

public final class StageModel {
    public static final int maxStages;
    public static Table of(Blueprint blueprint, java.util.Collection<LatticePoint> expected);

    public record Stage(String name, @Nullable String description, @Nullable String hint,
                        java.util.List<LatticePoint> cells, NamespacedId bay) { }

    public record Table(java.util.List<Stage> stages, int expectedStages) {
        public boolean truncated();
    }
}

public final class BuildFeedback {
    public static void send(ServerPlayer player, ApplyReport report, boolean formed);
    public static void undo(ServerPlayer player, ApplyReport report);
}

package dev.vfyjxf.cumulus.mc.preview.net;

public record PreviewSyncPayload(NamespacedId blueprintId, java.util.List<SitingCandidate> candidates, int current,
                                 OverlayModel overlay, java.util.List<GhostCell> ghostCells,
                                 java.util.List<Marker> markers,
                                 PreviewPresentation presentation,
                                 @Nullable VariantSummary variants) implements CustomPacketPayload {
    public static final Type<PreviewSyncPayload> type;
    @Override public Type<? extends CustomPacketPayload> type();
}

public record PreviewControlPayload(Control control, @Nullable LatticePoint target) implements CustomPacketPayload {
    public static final Type<PreviewControlPayload> type;

    public enum Control {
        turnCw, turnCcw, flip, nextOrigin, prevOrigin, reset, resetIdentity, cyclePart, recall, confirm, cancel,
        nextVariant, savePreset
    }

    public static PreviewControlPayload of(Control control);
    @Override public Type<? extends CustomPacketPayload> type();
}

public record SitingCandidate(Siting siting, int score) { }

public record GhostCell(LatticePoint cell, NamespacedId block, java.util.Map<String, String> properties,
                        int stageIndex) { }

public record Stage(String name, int expectedCells, int satisfiedCells,
                    @Nullable String hint, @Nullable String description, NamespacedId bay) { }

public record PreviewPresentation(@Nullable String description, java.util.List<Stage> stages, int currentStage,
                                  java.util.List<MaterialStatus> materials, int moreMaterials) { }

public record MaterialStatus(NamespacedId item, int need, int have) { }

public record PreviewCatalogPayload(java.util.List<Entry> entries) implements CustomPacketPayload {
    public static final Type<PreviewCatalogPayload> type;
    @Override public Type<? extends CustomPacketPayload> type();

    public record Entry(NamespacedId blueprintId, java.util.List<GhostCell> cells,
                        java.util.List<MaterialLine> materials, PreviewPresentation presentation,
                        @Nullable VariantSummary variants) { }

    public record MaterialLine(NamespacedId item, int count) { }
}

public record VariantSummary(NamespacedId group, java.util.List<Member> members) {
    public @Nullable Member member(NamespacedId id);
    public int indexOf(NamespacedId id);

    public record Member(NamespacedId id, @Nullable String label) {
        public String text();
    }
}

public record PreviewOpenPayload(NamespacedId blueprintId) implements CustomPacketPayload {
    public static final Type<PreviewOpenPayload> type;
    @Override public Type<? extends CustomPacketPayload> type();
}

public record BuildCommitPayload(java.util.List<CommitCell> cells, Status status, boolean formed, int truncated)
        implements CustomPacketPayload {
    public static final int maxAnimatedCells;
    public static final Type<BuildCommitPayload> type;
    @Override public Type<? extends CustomPacketPayload> type();

    public enum Status {
        committed, rolledBack, undone
    }

    public record CommitCell(LatticePoint cell, NamespacedId block, java.util.Map<String, String> properties) {
    }
}

public final class PreviewPayloads {
    public static void register(RegisterPayloadHandlersEvent event);
}

package dev.vfyjxf.cumulus.mc.preview.client;

public final class PreviewClientState {
    public static @Nullable PreviewSyncPayload session();
    public static boolean problemsOnly();
    public static int sliceY();
    public static @Nullable String hoverTooltip();
    public static boolean visible();
    public static void toggleProblemsOnly();
    public static java.util.List<String> hudLines();
}

public final class PlaybackController {
    public static java.util.List<String> bannerLines();
    public static float alphaOf(GhostCell ghost);
    public static boolean revealed(GhostCell ghost);
    public static boolean revealedCell(LatticePoint cell);
    public static @Nullable Integer stageOf(LatticePoint cell);
    public static @Nullable PreviewPresentation presentation();
    public static int stageCount();
    public static int selected();
    public static int revealed(int stage);
    public static int ghostCount(int stage);
    public static boolean playing();
    public static @Nullable Stage stage(int index);
    public static java.util.List<PartLabel> partLabels();

    public record PartLabel(int stage, LatticePoint anchor, String hint, float alpha) { }
}

public final class PlacementAnimator {
    public static java.util.List<String> noteLines();
    public static boolean animating();
    public static int animatingCells();
    public static BuildCommitPayload.@Nullable Status lastStatus();
    public static int soundRequests();
    public static int celebrations();
    public static int startedRuns();
    public static int finishedRuns();
}

public final class PreviewKeys {
    public enum Action {
        turnCw, turnCcw, flip, prevOrigin, nextOrigin, reset, recall, sliceUp, sliceDown, toggleProblems,
        confirm, cancel, prevStage, nextStage, playPause, stepCell, guide, nextVariant, savePreset, cinematic
    }

    public static @Nullable KeyMapping binding(Action action);
}

public final class CinematicController {
    public static boolean active();
    public static boolean fastReturning();
    public static boolean closing();
    public static float letterboxAlpha();
    public static int programTicks();
    public static int focusedStage();
    public static @Nullable CameraShot sampledShot();
}

public final class TransitionController {
    public static float ghostAlpha(GhostCell ghost, float partialTick);
    public static float ghostScale(GhostCell ghost, float partialTick);
    public static boolean turntableActive();
    public static int turntableTicks();
    public static int turntableDurationTicks();
    public static double turntableAngleDeg();
    public static @Nullable Turn turntableModelTurn();
    public static boolean stageAdvanceActive();
    public static int stageAdvanceTicks();
    public static int stageAdvanceDurationTicks();
    public static boolean sessionFadeActive();
    public static int sessionFadeTicks();
}

public final class GuideController {
    public static boolean guided();
    public static java.util.List<LatticePoint> nextGroup();
    public static @Nullable String nextStep();
    public static boolean complete();
    public static java.util.List<LatticePoint> pulsingCells();
    public static boolean pulsing(LatticePoint cell);
    public static float pulseFactor(LatticePoint cell);
}

public final class PreviewClientBridge {
    public static void accept(PreviewSyncPayload payload);
    public static void acceptCatalog(PreviewCatalogPayload payload);
    public static void acceptCommit(BuildCommitPayload payload);
}

public final class PreviewCatalogClient {
    public static void accept(PreviewCatalogPayload payload);
    public static java.util.List<PreviewCatalogPayload.Entry> entries();
    public static void listen(Runnable listener);
    public static void unlisten(Runnable listener);
    public static void clear();
}

public final class PreviewClientEvents { }

package dev.vfyjxf.cumulus.mc.preview.scene;

// One page's expected states, resolved against the client's block registry and drawn through a
// rectangle and a camera (design 15 §6, design 17 §10).
public final class PreviewScene {

    public record Cell(GhostCell ghost, @Nullable BlockState state) { }

    public enum LayerRule {
        all, upTo, exact
    }

    public record View(int x, int y, int width, int height, float yaw, float pitch, LayerRule rule) { }

    public record Panel(int cells, int layer) { }

    public static PreviewScene of(PreviewCatalogPayload.Entry entry);
    public Hull hull();
    public int cellCount();
    public java.util.List<Cell> cells();
    public int layers();
    public @Nullable Cell hover(double mouseX, double mouseY, View view, int layer);
    public @Nullable Vector2f point(View view, int layer, LatticePoint at);
    public Panel draw(GuiGraphics graphics, View view, int layer, boolean band);
    public @Nullable Cell hover(double mouseX, double mouseY, Fit fit);
    public @Nullable Vector2f point(Fit fit, LatticePoint at);
    public Panel draw(GuiGraphics graphics, Fit fit);
}

public final class BlueprintPreviewRecipe {

    public record Material(ItemStack stack, int count) { }

    public BlueprintPreviewRecipe(java.util.List<PreviewCatalogPayload.Entry> group);
    public void update(java.util.List<PreviewCatalogPayload.Entry> group);
    public NamespacedId blueprintId();
    public NamespacedId selectedBlueprintId();
    public java.util.List<NamespacedId> variantIds();
    public int variantIndex();
    public int variantCount();
    public boolean cycleVariant(int step);
    public boolean selectVariant(NamespacedId blueprintId);
    public @Nullable String variantLine();
    public java.util.List<String> lines();
    public PreviewScene scene();
    public PreviewPresentation presentation();
    public java.util.List<String> cellLines(PreviewScene.Cell cell);
    public java.util.List<Material> materials();
}

public enum PreviewViewMode {
    isometric, threeView, sliceGrid;
    public String label();
    public PreviewViewMode next();
}

public final class CellStates {
    public static @Nullable BlockState of(NamespacedId block, java.util.Map<String, String> properties);
}

public final class TintedBuffers implements MultiBufferSource {
    public static MultiBufferSource of(MultiBufferSource delegate, float alpha);
    public static MultiBufferSource of(MultiBufferSource delegate, float alpha, float red, float green, float blue);
    @Override public VertexConsumer getBuffer(RenderType type);
}

// The drawn hull of design 17 §3.3: a box in cell units over the cells that draw something. The fit
// box widens every extent to at least one cell, so no fit ever divides by zero.
public record Hull(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    public Hull union(Hull other);
    public boolean isEmpty();
}

// One viewport's orbit camera (design 17 §4.2): a pure value whose every operation returns a new one.
public record SceneCamera(float targetX, float targetY, float targetZ, float yaw, float pitch,
                          float distance, float zoom, Projection projection) {
    public enum Projection { orthographic, perspective }
    public enum Snap { front, back, right, left, top, bottom }
    public static final float defaultYaw;
    public static final float defaultPitch;
    public static final float minZoom;
    public static final float maxZoom;
    public static final float minDistance;
    public static final float maxDistance;
    public static final float fovDegrees;
    public static final float maxPitch;
    public static SceneCamera isometric();
    public SceneCamera orbit(float deltaYaw, float deltaPitch);
    public SceneCamera pan(float right, float up);
    public SceneCamera zoom(float factor);
    public SceneCamera frame(Hull hull, float aspect);
    public SceneCamera snap(Snap snap);
    public SceneCamera reset(Hull hull, float aspect);
    public SceneCamera withProjection(Projection next, Hull hull);
}

// The three axes of design 17 §6: the drawn hull's minimum corner as origin, one segment and one
// label anchor per axis, projected through one viewport's camera.
public record AxisGizmo(Segment x, Segment y, Segment z) {
    public record Segment(Vector2f start, Vector2f end, Vector2f label) { }
    public static final int xColor;
    public static final int yColor;
    public static final int zColor;
    public static AxisGizmo of(Hull hull, SceneCamera camera, int x, int y, int width, int height);
}

// The fit of design 17 §4.3: one hull into one rectangle through one camera, with the 2D projection
// the axes, the picking and the scene share.
public record Fit(Hull hull, SceneCamera camera, int x, int y, int width, int height,
                  float scale, float focal) {
    public static final float margin;
    public static Fit of(Hull hull, int x, int y, int width, int height, SceneCamera camera);
    public float unitsPerPixel();
    public Vector2f project(float worldX, float worldY, float worldZ);
    public float depth(float worldX, float worldY, float worldZ);
    public org.joml.Matrix4f matrix();
}

package dev.vfyjxf.cumulus.mc.preview.client.screen;

// The screen's rectangles of design 17 §5.1: the two strips and the four viewports, a pure function
// of the screen size. The three small panels collapse to null when their column has no room.
public record ScreenLayout(Rect title, Rect main, @Nullable Rect front, @Nullable Rect side,
                           @Nullable Rect top, Rect control) {

    public static final int titleHeight;
    public static final int controlHeight;
    public static final int panelGap;
    public static final float mainFraction;

    public record Rect(int x, int y, int width, int height) {
        public static final Rect empty;
        public boolean isEmpty();
    }

    public static ScreenLayout of(int width, int height);
}

// The one screen of design 17 §5: the page looked up in the last catalogue, one shared camera and the
// four derived viewports.
public final class PreviewScreen extends Screen {
    public static void open(NamespacedId blueprintId);
    @Override public void added();
    @Override public void removed();
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers);
    @Override public void mouseMoved(double mouseX, double mouseY);
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button);
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button);
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);
    public SceneCamera camera();
    public ScreenLayout layout();
    public PreviewScene scene();
    public int drawnCells();
    public @Nullable PreviewScene.Cell hovered();
    public ScreenClock clock();
    public @Nullable AxisGizmo gizmo();
}

// The screen's own clock of design 17 §4.6: a fractional tick accumulator, fed by the frame's
// real-time delta because a screen pauses the game tick.
public record ScreenClock(int ticks, float fraction) {
    public static ScreenClock start();
    public ScreenClock advance(float realtimeDeltaTicks);
    public float time();
}

package dev.vfyjxf.cumulus.mc.preview.jei;

public final class JeiPreview {
    public record Panel(int cells, int layer, int x, int y, int width, int height) { }
    public record CellPoint(double x, double y) { }

    public static java.util.List<NamespacedId> blueprintIds();
    public static int drawnFrames();
    public static int drawnCells();
    public static java.util.List<Panel> drawnPanels();
    public static java.util.List<String> lines(NamespacedId blueprintId);
    public static @Nullable PreviewViewMode viewMode(NamespacedId blueprintId);
    public static boolean cycleViewMode(NamespacedId blueprintId);
    public static int slice(NamespacedId blueprintId);
    public static boolean setSlice(NamespacedId blueprintId, int layer);
    public static java.util.Optional<CellPoint> cellPoint(NamespacedId blueprintId, int panel, int cellIndex);
    public static java.util.List<String> hoverText(NamespacedId blueprintId, double mouseX, double mouseY);
    public static void openWorld(NamespacedId blueprintId);
    public static boolean show(NamespacedId blueprintId);
    public static java.util.List<NamespacedId> variants(NamespacedId blueprintId);
    public static int variantIndex(NamespacedId blueprintId);
    public static @Nullable NamespacedId selectedBlueprintId(NamespacedId blueprintId);
    public static boolean cycleVariant(NamespacedId blueprintId, int step);
    public static void openSelected(NamespacedId blueprintId);
}

public final class JeiPreviewPlugin implements IModPlugin, ModPlugin {
    @Override public ResourceLocation getPluginUid();
    @Override public NamespacedId pluginId();
    @Override public void registerCategories(IRecipeCategoryRegistration registration);
    @Override public void registerRecipes(IRecipeRegistration registration);
    @Override public void onRuntimeAvailable(IJeiRuntime jeiRuntime);
    @Override public void onRuntimeUnavailable();
}

public final class JeiPreviewClientEvents { }

package dev.vfyjxf.cumulus.mc.plugin;

public interface ModPlugin {
    NamespacedId pluginId();
    default Set<PluginDependency> dependencies();
    default void register(CatalogRegistrar registrar);
    default void registerMaterializers(MaterializerRegistrar registrar);
    default void registerItemSources(ItemSourceRegistrar registrar);
}
public record PluginDependency(NamespacedId pluginId, Order order, Constraint constraint) {
    public enum Order {
        before, after, none
    }
    public enum Constraint {
        required, optional, optionalRequired
    }
    public boolean required();
    public boolean optional();
    public boolean optionalRequired();
}
public final class PluginLoader {
    public static <T extends ModPlugin> LoadingResult<T> load(PluginLookup<T> lookup);
    public static <T extends ModPlugin> List<T> loadPlugin(Logger logger, String pluginCategory, PluginLookup<T> lookup);
    public static <T extends ModPlugin> List<T> register(Logger logger, String pluginCategory, PluginLookup<T> lookup, CatalogRegistrar registrar);
    public static <T extends ModPlugin> List<LoadingFailure<T>> registerExtensions(Logger logger, String pluginCategory, List<T> plugins, List<PluginPass<T>> passes);
    public record LoadingResult<T extends ModPlugin>(List<T> plugins, List<LoadingFailure<T>> failures) {
    }
    public record LoadingFailure<T extends ModPlugin>(T instance, FailureType type, String reason) {
    }
    public enum FailureType {
        fatal, warning
    }
}
public class PluginLoadingException extends RuntimeException {
    public PluginLoadingException(List<Failure> failures);
    public List<Failure> failures();
    public record Failure(NamespacedId pluginId, Throwable cause) {
    }
}
public interface PluginLookup<T extends ModPlugin> {
    Collection<T> findPlugins();
    default PluginLookup<T> combine(PluginLookup<T> other);
}
public final class AnnotationPluginLookup<T extends ModPlugin> implements PluginLookup<T> {
    public static <T extends ModPlugin> AnnotationPluginLookup<T> of(Class<T> pluginClass);
    public static <T extends ModPlugin> AnnotationPluginLookup<T> of(Class<T> pluginClass, Class<? extends Annotation> annotation);
    @Override public Collection<T> findPlugins();
}
public final class SpiPluginLookup<T extends ModPlugin> implements PluginLookup<T> {
    public static <T extends ModPlugin> SpiPluginLookup<T> of(Class<T> pluginClass);
    public static <T extends ModPlugin> SpiPluginLookup<T> of(Class<T> pluginClass, ClassLoader classLoader);
    @Override public Collection<T> findPlugins();
}
public record PluginPass<T extends ModPlugin>(String name, Consumer<T> action) {
}

package dev.vfyjxf.cumulus.mc.build;

public record ApplyReport(Status status, List<Step> steps, List<ItemStack> itemsConsumed, List<Change> changes,
                          FindingBundle findings) {
    public static ApplyReport refused(FindingBundle findings);
    public static ApplyReport formed();
    public enum Status {
        committed, rolledBack, partiallyRolledBack, refused
    }
    public record Step(int index, Outcome outcome, @Nullable String reason) {
        public boolean failed();
    }
    public enum Outcome {
        applied, skipped, failed, restored
    }
    public record Change(LatticePoint cell, BlockPlacement placement) {
    }
}
public record BlockPlacement(BlockState state, @Nullable CompoundTag blockEntityData) {
    public boolean isAir();
}
public final class BuildCodes {
    public static final FindingCode noMachineRepair;
    public static final FindingCode policyRefusal;
    public static final FindingCode itemShortage;
    public static final FindingCode materializationFailure;
    public static final FindingCode stepFailure;
    public static final FindingCode incompleteRollback;
    public static final FindingCode recheckNotFormed;
    public static final FindingCode previewStale;
    public static final CodeEntry noMachineRepairEntry;
    public static final CodeEntry policyRefusalEntry;
    public static final CodeEntry itemShortageEntry;
    public static final CodeEntry materializationFailureEntry;
    public static final CodeEntry stepFailureEntry;
    public static final CodeEntry incompleteRollbackEntry;
    public static final CodeEntry recheckNotFormedEntry;
    public static final CodeEntry previewStaleEntry;
    public static List<CodeEntry> entries();
}
public final class BuildConfig {
    public static final ModConfigSpec.IntValue maxTouchedVolume;
    public static final ModConfigSpec.BooleanValue allowRemoval;
    public static final ModConfigSpec spec;
    public static BuildPolicy policy();
}
public final class BuildExecutor {
    public BuildExecutor(LevelCapture capture, StructureKernel kernel, OwnerId owner, BuildPolicy configured);
    public BuildExecutor(LevelCapture capture, StructureKernel kernel, OwnerId owner, BuildPolicy configured, Materializers materializers);
    public ApplyReport run(BuildRequest request);
    public ApplyReport run(Blueprint blueprint, BuildRequest request, BuildJournal journal);
}
public final class BuildHistory {
    public static void record(ServerLevel level, UUID player, Map<LatticePoint, BlockPlacement> preImage);
    public static boolean has(ServerLevel level, UUID player);
    public static void forget(ServerLevel level, UUID player);
    public static @Nullable ApplyReport undo(ServerLevel level, UUID player);
    public static void levelUnloaded(LevelEvent.Unload event);
    public static void forgetAll();
}
public final class BuildJournal {
    public BuildJournal(ServerLevel level, UUID player);
    public void record(Map<LatticePoint, BlockPlacement> preImage);
    public List<Map<LatticePoint, BlockPlacement>> commitOrder();
    public void forget();
}
public final class BuildMaterials {
    public record MaterialShortage(ItemStack template, int have, int need) { }

    public static List<String> shortages(EditPlan plan, Siting siting, ServerLevel level, ItemSource source);
    public static List<String> shortages(EditPlan plan, Siting siting, ServerLevel level, ItemSource source, Materializers materializers);
    public static List<String> shortages(List<MaterialShortage> missing);
    public static List<MaterialShortage> missing(EditPlan plan, Siting siting, ServerLevel level, ItemSource source, Materializers materializers);
}
public record BuildPolicy(int permissionLevel, long maxTouchedVolume, boolean allowRemoval) {
    public static final int defaultPermissionLevel;
    public static final long defaultMaxTouchedVolume;
    public static final boolean defaultAllowRemoval;
    public static BuildPolicy defaults();
    public BuildPolicy withAllowRemoval(boolean allowed);
    public BuildPolicy withMaxTouchedVolume(long volume);
}
public record BuildRequest( NamespacedId blueprint, Siting siting, ItemSource source, BuildPolicy policy, boolean dry, @Nullable SolvePreferences preferences, ServerPlayer player ) {
}
public final class CompositeItemSource implements ItemSource {
    public CompositeItemSource(List<ItemSource> sources);
    public static ItemSource of(List<ItemSource> sources);
    public List<ItemSource> sources();
    @Override public NamespacedId id();
    @Override public int available(ItemStack template);
    @Override public ItemStack extract(ItemStack template, int count, ExtractionMode mode);
    @Override public ItemStack insert(ItemStack stack, ExtractionMode mode);
}
public final class CreativeItemSource implements ItemSource {
    @Override public NamespacedId id();
    @Override public int available(ItemStack template);
    @Override public ItemStack extract(ItemStack template, int count, ExtractionMode mode);
    @Override public ItemStack insert(ItemStack stack, ExtractionMode mode);
}
public final class EditTransaction implements AutoCloseable {
    public static EditTransaction open(ServerLevel level, CuboidSet touchedCells);
    public ApplyReport.Step apply(int stepIndex, Map<LatticePoint, BlockPlacement> writes);
    public List<ApplyReport.Step> rollback(List<ApplyReport.Step> steps);
    public Map<LatticePoint, BlockPlacement> preImage();
    @Override public void close();
}
public enum ExtractionMode {
    simulate, execute;
    public boolean simulated();
}
public final class ItemHandlerSource implements ItemSource {
    public ItemHandlerSource(IItemHandler handler);
    public static @Nullable ItemHandlerSource at(ServerLevel level, BlockPos pos, @Nullable Direction side);
    public IItemHandler handler();
    @Override public NamespacedId id();
    @Override public int available(ItemStack template);
    @Override public ItemStack extract(ItemStack template, int count, ExtractionMode mode);
    @Override public ItemStack insert(ItemStack stack, ExtractionMode mode);
}
public record ItemRequirement(ItemStack template, int count) {
}
public interface ItemSource {
    NamespacedId id();
    int available(ItemStack template);
    ItemStack extract(ItemStack template, int count, ExtractionMode mode);
    ItemStack insert(ItemStack stack, ExtractionMode mode);
}
public interface ItemSourceProvider {
    NamespacedId id();
    @Nullable ItemSource create(ServerPlayer player, ServerLevel level);
}
public interface ItemSourceRegistrar {
    ItemSourceRegistrar itemSource(ItemSourceProvider provider);
    ItemSources itemSources();
    static ItemSourceRegistrar empty();
}
public final class ItemSources {
    public static ItemSources empty();
    public List<NamespacedId> ids();
    public boolean has(NamespacedId id);
    public @Nullable ItemSource create(NamespacedId id, ServerPlayer player, ServerLevel level);
}
public record MaterializeContext(ServerLevel level) {
}
public record MaterializedEdit( Map<LatticePoint, BlockPlacement> placements, List<ItemRequirement> items, List<Finding> findings ) {
}
public interface Materializer {
    NamespacedId id();
    @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context);
}
public interface MaterializerRegistrar {
    MaterializerRegistrar materializer(Materializer materializer);
    Materializers materializers();
    static MaterializerRegistrar builtin();
}
public final class Materializers {
    public static Materializers builtin();
    public List<NamespacedId> ids();
    public @Nullable Materializer get(NamespacedId id);
    public @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context);
}
public final class PlayerInventorySource implements ItemSource {
    public PlayerInventorySource(ServerPlayer player);
    public ServerPlayer player();
    @Override public NamespacedId id();
    @Override public int available(ItemStack template);
    @Override public ItemStack extract(ItemStack template, int count, ExtractionMode mode);
    @Override public ItemStack insert(ItemStack stack, ExtractionMode mode);
}
public final class SetBuildHost {
    public SetBuildHost(LevelCapture capture, StructureKernel kernel, OwnerId owner);
    public SetBuildHost(LevelCapture capture, StructureKernel kernel, OwnerId owner, Materializers materializers);
    public StructureSetResult run(SetBuildRequest request, SnapshotLease lease, Budget budget);
}
public record SetBuildRequest( StructureSet set, ItemSource source, BuildPolicy policy, ServerPlayer player, boolean dry, boolean settle ) {
}
public final class VanillaMaterializer implements Materializer {
    @Override public NamespacedId id();
    @Override public @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context);
}

package dev.vfyjxf.cumulus.mc.pref;

public record BuildPreset( String name, NamespacedId blueprintId, Siting siting, Source source,
                          RemovalPolicy removalPolicy ) {
    public static final int formatVersion;
    public static final int capacity;
    public static final int maxNameLength;
    public String text();
    public static String pose(Siting siting);
    public static String cellText(LatticePoint cell);

    public record Source( Kind kind, @Nullable LatticePoint container, @Nullable NamespacedId provider ) {
        public enum Kind {
            creative, player, container, registered
        }
        public static Source creative();
        public static Source player();
        public static Source container(LatticePoint position);
        public static Source registered(NamespacedId id);
        public String text();
    }
}
public record BuildPreferences( OrientationMode orientationMode, @Nullable Heading fixedHeading, RemovalPolicy removalPolicy, List<PlacementWeight> weights, Map<NamespacedId, ChoiceHistory> remembered ) {
    public static final int defaultRememberedCapacity;
    public static BuildPreferences of(OrientationMode mode, @Nullable Heading heading, RemovalPolicy removal, List<PlacementWeight> weights);
    public @Nullable ChoiceHistory history(NamespacedId blueprint);
    public @Nullable Siting preferred(NamespacedId blueprint);
    public BuildPreferences remember(NamespacedId blueprint, Siting choice, int capacity);
    public static Map<NamespacedId, ChoiceHistory> remember(Map<NamespacedId, ChoiceHistory> remembered, NamespacedId blueprint, Siting choice, int capacity);
    public BuildPreferences forget(@Nullable NamespacedId blueprint);
    public BuildPreferences withRemembered(Map<NamespacedId, ChoiceHistory> next);
    public BuildPreferences withOrientationMode(OrientationMode mode);
    public BuildPreferences withFixedHeading(@Nullable Heading heading);
    public BuildPreferences withRemovalPolicy(RemovalPolicy policy);
    public BuildPreferences withWeights(List<PlacementWeight> next);
    public Heading startHeading(Heading playerFacing);
    public List<String> weightKeys();
}
public record ChoiceHistory(List<Siting> recent) {
    public static final int capacity;
    public static ChoiceHistory empty();
    public ChoiceHistory append(Siting choice);
    public @Nullable Siting preferred();
    public boolean isEmpty();
}
public enum OrientationMode {
    autoAnchor, facePlayer, fixedHeading;
    public static @Nullable OrientationMode parse(String name);
    public String key();
}
public enum PlacementWeight {
    facingPlayer, fewestRemovals, mostProgress, nearestToPlayer, remembered, support, align;
    public int magnitude();
    public static @Nullable PlacementWeight parse(String name);
    public String key();
}
public final class PrefConfig {
    public static final List<String> defaultWeightKeys;
    public static final ModConfigSpec spec;
    public static BuildPreferences defaults();
    public static int rememberedCapacity();
}
public final class PreferencesCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> literal();
}
public final class PreferencesSavedData extends SavedData {
    public static final String fileId;
    public static final int formatVersion;
    public static PreferencesSavedData get(ServerLevel level);
    public BuildPreferences preferences(UUID player);
    public void set(UUID player, BuildPreferences settings);
    public void remember(UUID player, NamespacedId blueprint, Siting choice);
    public void remember(UUID player, NamespacedId blueprint, Siting choice, int capacity);
    public void rememberPart(UUID player, NamespacedId blueprint, NamespacedId part, LatticePoint cell);
    public @Nullable LatticePoint partPin(UUID player, NamespacedId blueprint, NamespacedId part);
    public Map<NamespacedId, Map<NamespacedId, LatticePoint>> partPins(UUID player);
    public Map<NamespacedId, LatticePoint> partPins(UUID player, NamespacedId blueprint);
    public void forget(UUID player, @Nullable NamespacedId blueprint);
    public int rememberedBlueprints(UUID player);
    public int partPinCount(UUID player);
    public List<BuildPreset> presets(UUID player);
    public @Nullable BuildPreset preset(UUID player, String name);
    public SaveResult savePreset(UUID player, BuildPreset preset);
    public boolean deletePreset(UUID player, String name);
    public int presetCount(UUID player);
    public String nextPresetName(UUID player);
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);

    public enum SaveResult {
        saved, replaced, full
    }
}
public enum RemovalPolicy {
    forbid, ask, allow;
    public static @Nullable RemovalPolicy parse(String name);
    public String key();
}
public final class SitingCandidateScorer {
    public static final int facingBonus;
    public static final int removalPenalty;
    public static final int progressBonus;
    public static final int distanceStep;
    public static final int maxDistance;
    public static final int rememberedBonus;
    public record Candidate(Siting siting, int order, int satisfied, int removals, int supportBelow, int supportAbove) {
    }
    public record View( LatticePoint player, Heading playerFacing, Heading front, @Nullable ChoiceHistory memory, boolean hanging ) {
    }
    public record Scored(Siting siting, int score, int order) {
    }
    public SitingCandidateScorer();
    public List<Scored> rank(List<Candidate> candidates, View view, @Nullable List<PlacementWeight> weights);
    public int score(Candidate candidate, View view, @Nullable List<PlacementWeight> weights);
    public static Heading frontOf(Blueprint blueprint);
}
public final class SolvePreferenceAssembler {
    public static final int maxPreferredCells;
    public static SolvePreferences assemble(@Nullable List<LatticePoint> pending, @Nullable Map<NamespacedId, LatticePoint> partPins, Siting siting);
}

package dev.vfyjxf.cumulus.mc;

public final class CaptureReports {
    public static FindingBundle relay(CaptureReport report, Catalog<CodeEntry> codes);
    public static Finding relay(CaptureReport.Entry entry, Catalog<CodeEntry> codes);
}
public final class SetPresence {
    public enum State {
        present, absent, unknown
    }
    public record Probe(State state, @Nullable LatticePoint cell, boolean budgetExhausted) {
    }
    public static final class Budget {
        public Budget(dev.vfyjxf.cumulus.kernel.core.Budget budget);
        public dev.vfyjxf.cumulus.kernel.core.Budget remaining();
    }
    public static List<StructureSet.Satellite> requiredFirst(List<StructureSet.Satellite> satellites);
    public static Probe probe(StructureSet.Satellite satellite, WorldView view, Budget budget);
    public static Finding unreadable(StructureSet.Satellite satellite, Probe probe);
}

package dev.vfyjxf.cumulus.mc.preview.client.cine;

public enum Ease {
    linear, smoothstep, inOutCubic, outQuad, outBack;
    public final double applyClamped(double t);
}
public record Tween(double from, double to, int durationTicks, Ease ease) {
    public double at(double tick);
    public boolean done(double tick);
}
public record VecTween(Vec3 from, Vec3 to, int durationTicks, Ease ease) {
    public Vec3 at(double tick);
    public boolean done(double tick);
}
public record CameraShot(Vec3 eye, Vec3 target) {
    public static CameraShot framing(Cuboid bounds, Vec3 approach, double fovDeg);
    public float yaw();
    public float pitch();
}
public record CameraProgram(java.util.List<Leg> legs) {
    public record Leg(CameraShot shot, int dwellTicks, int moveTicks, Ease moveEase) {
    }
    public int totalTicks();
    public CameraShot at(double tick);
}
public record TurntableTransition(Turn from, Turn to, int durationTicks, Ease ease) {
    public int shortestArc();
    public double angleDeg(double tick);
    public Vec3 rotate(Vec3 offset, double tick);
    public Turn modelTurn(double tick);
}
public final class CinematicPrograms {
    public static Vec3 centre(Cuboid bounds);
    public static CameraProgram opening(Cuboid bounds, Vec3 approach);
    public static CameraShot stageShot(Cuboid bounds, Vec3 approach);
    public static CameraProgram focus(CameraShot from, Cuboid bounds, Vec3 approach);
    public static CameraProgram closing(CameraShot from, Cuboid bounds, Vec3 approach);
    public static Cuboid hull(java.util.List<LatticePoint> cells, Cuboid fallback);
}
```

## 16. Build (design 14, P2 landed the single structure; P6 adds the set build)

### 16.1 The landed build layer

`dev.vfyjxf.cumulus.mc.build` owns execution: the `ItemSource` SPI and its built-ins
(`CreativeItemSource`, `PlayerInventorySource`, `ItemHandlerSource`, `CompositeItemSource`), the
`Materializer` SPI and `VanillaMaterializer`, `EditTransaction`, `ApplyReport`, `BuildPolicy`,
`BuildRequest`, `BuildHistory`, `BuildGates`, `BuildConfig` and `BuildExecutor`. The command bodies
sit in `dev.vfyjxf.cumulus.mc.BuildCommand`, registered by `CumulusCommand`, because the command
helpers (`CommandSupport`, the anchor siting path) are package-scoped to `mc`; the build package owns
the flow and the command is its thin shell.

Deviations from design 14 §4, all deliberate: `BuildExecutor` takes the `OwnerId` explicitly
(`BuildExecutor(LevelCapture, StructureKernel, OwnerId, BuildPolicy)`) because the executor begins a
draft for the check and cannot reach `ServerState.owner()` from a subpackage; claims come from
`StructureKernel.ledger()` rather than a constructor argument; `run(Blueprint, BuildRequest,
BuildJournal)` lets a caller that already holds the compiled blueprint (the set build does) skip the
document lookup, which a hand-built set cannot satisfy; and `--dry` runs steps 1–4 and prints the
summary without a preview payload (the payload belongs to §17). P7 adds the materializer argument:
`BuildExecutor(..., Materializers)` takes the session's chain and the four-argument form keeps the
built-in vanilla chain, so a hand-built executor and every existing call site stay source-compatible.
The single build and the set build both carry `--settle` now (§16.2/§16.3).

`BuildPolicy` is the host gate. `config/structurecumulus-server.toml` carries `maxTouchedVolume`
(default 4096) and `allowRemoval` (default false); `BuildConfig.policy()` reads them, the permission
level stays the root's `LEVEL_GAMEMASTERS` gate, and `--allow-removal` lifts the removal gate for one
run only (the config OR the flag). `BuildGates` is the pure dry run: volume ceiling, removal,
foreign exclusive claims (a shared claim allows the run) and the item simulation, each refusal an
`ApplyReport` with zero writes.

A matched check is the "already built" signal. `Formed` belongs to settle (D-16: the build takes no
claims), so the pre-build check and the post-build re-check of an unserettled siting answer
`Admitted`; `BuildExecutor` accepts both and reports `SC-BLD-0007` when the re-check still mismatches.

`EditTransaction.open` captures the live pre-image (`BlockState` + block-entity NBT) of the plan's
cells; `apply` compares each cell with its target first (`skipped`, never rewritten) and records the
step's writes; `rollback` restores them in reverse order, skips a cell already at its pre-image, and
names the remaining cells when a restore fails. A commit records one entry per player and dimension
in `BuildHistory`; `/cumulus build undo` replays it in reverse as its own transaction, and the entry
is dropped on `LevelEvent.Unload` and on server stop (in memory only, never persisted).

The signatures are the ones in design 14 §4 with the owner and materializer arguments added; the
build types are documented in §15 (the P7 batch, D-74).

### 16.2 Build is not settle

Build takes no claims; `--settle` chains the existing settle path after the re-check, and only then
does rollback cover claims (design 14 §10). Both builds have the flag. The single build chains the
settle command's own flow on the same origin and siting (`SettleCommand.settle` — a fresh capture,
`check`, then the kernel `settle` with a `StructureAttempt`), only after a `committed` run whose
re-check did not report `SC-BLD-0007`, and never on a dry run; its exit code is then `formed`, not
`committed` (design 14 §12). The set build chains `StructureSetHost.run` (§16.3). A failed settle
writes no claim rows and leaves the committed world write in place. Design 14 owns the remaining
contracts: two-phase item handling, pre-image transactions with reverse restore, refusal as a
finding-carrying report with zero writes, idempotence as a verifier property and the `SC-BLD` code
family (§12). D-16 stands: no executor, transaction, validator or report enters the kernel.

### 16.3 The set build (P6, design 14 §13)

`dev.vfyjxf.cumulus.mc.build.SetBuildHost` sequences a structure set's build. Its one set-level read is
the caller's capture: `StructureSet.validate` (the `SC-SET-0008` same-siting refusal included), the
presence probe through the shared `SetPresence` law — each cell charged `(1, 0)` against the set's one
`Budget` — and `StructureSet.linkFindings` per satellite. An unprovable presence (`SC-SET-0004`) or a
stale lease stops the build before any write; an absent member is the build target, not `SC-SET-0001`.

The root and then the satellites run the §10 executor flow in declaration order, one at a time, with
**one** `ItemSource` and **one** `BuildJournal`. The journal appends each committed member's pre-image
in commit order and republishes the merged map as the run's `BuildHistory` entry, so the set build is
one undo step. A member whose check already matched is the executor's idempotent empty report (present
and formed, skipped). A member whose report is refused or rolled back is a failure: the members
already written down are torn down in reverse commit order, their consumed items return to the shared
source (a remainder spills as `SC-BLD-0006`) and the undo entry is dropped; each torn-down member's
report answers `rolledBack`. `SC-BLD-0007` is not a member failure: the committed write stays (§16.1)
and the set is only not `built()`, so a member with several faulty checks takes one repair per run. `--dry` runs every
member's steps 1–4 and never writes; a dry run consumes no item, so its per-member validation is not a
combined bill.

`--settle` re-captures the set capture's region and policy after the writes and runs the existing
`StructureSetHost.run` — only when the run is `built()`, so an incomplete member waits for the next
run instead of being torn down; the settle's presence, links, check-all and settle-all are the set
layer's own; the claims rollback of a failed settle is the set host's release + `StructureEvent.Broken` path,
and the build then tears its world writes down in reverse. The report is the `StructureSetResult` of
§13.5 with `builds` filled: per-member `ApplyReport` in build order, `itemsConsumed()` summing them,
`rolledBack` naming the torn-down members before the settle releases, and `built()` as the plain
build's success criterion. `SetBuildHost` is constructed as
`new SetBuildHost(LevelCapture, StructureKernel, OwnerId, Materializers)` (the three-argument form
keeps the vanilla chain) and run as `run(SetBuildRequest, SnapshotLease lease, Budget budget)`;
`SetBuildRequest(StructureSet, ItemSource, BuildPolicy, ServerPlayer, boolean dry, boolean settle)`.
The command's source forms are the single build's (§16.1), `with <kind-id>` included, and the shared
`ItemSource` is resolved once before the members run.

## 17. Preview (design 15, approved, landing with P3/P4)

`dev.vfyjxf.cumulus.mc.preview` (server and payloads), `...preview.client` (`Dist.CLIENT`) and
`...preview.jei` (a conditional built-in plugin, D-90) own the preview; the scene core they share lives
in `...preview.scene` since the screen pack (§20, design 17 §10). A session is keyed by
`(player, level)`, keeps its candidates, overlay and ghosts server-side, invalidates on the
`LevelWatch` mark counter (`SC-BLD-0008`), retracts its ghosts on timeout and recalls (design 15
§4), and never writes the world; confirm is the build flow (§16). Design 15 owns the payload
shapes, the client rendering, the JEI category and the orientation ergonomics: the expressibility
filter (3.4), turn snap (3.1), support scoring (3.2), flip memory (3.6), reset (3.7) and the
part-pin interaction (2.1, 2.2, 2.4). A lossy orientation fallback is explicitly rejected (3.5,
D-88).

The landed half (P3), with the wire facts a second implementation would need:

- **Command.** `PreviewCommand.literal()` is the one node the command owner attaches inside
  `CumulusCommand.register` (`.then(PreviewCommand.literal())`); the preview package owns no
  dispatcher and no other `cumulus` child. `LevelAccess` is the sibling-package facade over the
  level-scoped `ServerState` state, so the preview never uses reflection or package-private reach.
  The dev composition registers `/cumuluspreview` (`PreviewDevCommand`) against the same
  `PreviewService.open`, so the harness scenarios exercise the production service without depending
  on the production command wiring; its probe blueprint ships under `src/uitest/resources` as a
  datapack document, never in the production builtin source.
- **Session.** `PreviewService` holds one `PreviewSession` per `(player, level)`; `tick` compares
  `LevelAccess.blockRevision` (the `LevelWatch` per-chunk change counter) against the revision the
  overlay was computed at and re-syncs or closes. A session re-syncs on every control and on a
  20-tick heartbeat; the client drops it after 60 ticks without a sync. `SC-BLD-0008` is
  `BuildCodes.previewStale`, read-only here — the build family owns it. A timeout retracts the
  ghosts and keeps the plan (4.6); only `cancel` and a crashed level close a session.
- **Cycle.** Origins are the command argument, or the crosshair hit plus its face neighbour (1.1).
  The cycle is origins × the four upright turns snapped to the player's facing × both upright flips,
  then the `OrientationPolicy.allowed` poses whose tilt is not level (7.3). A pose whose declared
  states cannot express its orientation leaves the cycle and its `(block, property, value)` becomes
  one WARNING `Marker` (3.4); the detail is the marker list, not a click target. A blueprint that
  declares anchors resolves its siting from the formed anchor block at each origin instead
  (`McAnchors`, 1.2): one pose per origin, no free turn or flip, and an origin that is not a formed
  anchor yields no candidate rather than an invented pose (1.5).
- **Caps.** `ghostCells` is capped at 512 and `markers` at 48, both counted per payload and both
  code constants in v1 (config-izing them is P8 backlog); an overflow adds a WARNING marker naming
  the shown and expected counts, so nothing is truncated silently. Markers travel as world cell sets:
  a marker whose locus is not world-frame is re-anchored at the overlay bounds on the client, and no
  source span crosses the wire.
- **Controls.** `PreviewControlPayload.Control` carries the two resets and the part cycle the design's
  key table needs but its enum omitted: `reset` (tap R, 3.7), `resetIdentity` (hold R, 3.7) and
  `cyclePart`, whose optional `target` names the clicked ghost cell so the server resolves the bay
  from its own blueprint (2.1); a control still never carries a siting.
- **Confirm.** `PreviewConfirmHandler` is the one write path out of a session. P5 installs
  `PreviewBuilds`, the real executor flow of §18: the session hands over its siting, its item source
  and the assembled `SolvePreferences`, and a refusal keeps the plan in the session for another try
  (5.1).
- **Variant switch (E6).** When the session's blueprint carries a variant group, its summary travels
  on `PreviewSyncPayload.variants` (the group id and the members in declaration order, each with its
  carried label) and the HUD names the current member. `N` is `PreviewKeys.Action.nextVariant`, which
  sends `PreviewControlPayload.Control.nextVariant`; the session answers with the next member's id and
  `PreviewService.control` closes it and re-opens the ordinary session for that id at the same origin,
  through `PreviewService.openFor` — the new id's own preference memory and part pins apply, and the
  build path is untouched (design 15 §12.2, §12.4). The summary is compiled-bounded, so the codec has
  no variant cap: an over-cap declaration never compiles (`SC-DOC-0002`) instead of arriving
  truncated.
- **Presets (E7).** `PreviewService.context` publishes the live session's build context
  (`PreviewContext`: blueprint id, the current pose, the source form, the player's removal policy) for
  the preset save verb; `PreviewService.openAt` is the pinned open, which builds the cycle from the
  stored `Siting` alone (`PreviewCycle.ofSittings` of one pose) and takes the command's resolved
  `ItemSource`, so a `preview <name>` shows exactly what `apply <name>` would build (design 15 §13.3).
  A pinned session marks itself `preset <name>` in the markers and answers the cycling controls with a
  note; `K` is `PreviewKeys.Action.savePreset`, which sends `PreviewControlPayload.Control.savePreset`
  and stores `preset-N` in the same store (design 15 §13.2). The preview never resolves a source form
  itself: the command does, through `BuildCommand.items`, so the refusal a missing container or an
  unknown kind produces is the build command's own.
- **Client.** `PreviewClientBridge` is the only common-side reference into `...preview.client`; the
  handler body resolves it inside `enqueueWork`, so no client class loads on a dedicated server.
  `PreviewClientState` is the read-only model the HUD, the renderers and the scenarios share;
  `PreviewClientEvents` (a `Dist.CLIENT` `EventBusSubscriber`) owns the keys, the two render stages
  and the HUD layer. Ghost models resolve through the block renderer with a forced-alpha translucent
  pass; no block entity, capability or world read backs them. The guided build is one more reader of
  the same model: `GuideController` (E5, §19) holds the mode and the hand pulse, `G` is
  `PreviewKeys.Action.guide`, and a sync after a confirm that leaves the plan arms the mode — the
  server is told nothing about it.

The JEI half (P4), with the same kind of wire and lifecycle facts:

- **Conditional plugin.** `JeiPreviewPlugin` carries both annotations (`@JeiPlugin` for JEI's own
  scan, `@PluginMarker` for the adapter's) and implements both interfaces (`IModPlugin`, `ModPlugin`),
  so the adapter's loader pass sees the same id, dependencies and failure attribution every other
  plugin gets. `JeiPreviewPlugins.load` is the pass: it returns before resolving a JEI type when
  `ModList` does not list JEI, otherwise it runs `PluginLoader.load(AnnotationPluginLookup.of(
  ModPlugin.class, JeiPlugin.class))` and logs each failure (fatal ones logged, never thrown, D-81);
  the category itself is a singleton both instances share. JEI's own instances do the registration —
  the adapter's instance is the validation copy. The `GuiPlugin` evaluation (P7, design 15 §6.5)
  concluded **not to introduce the subtype**: there is one GUI integration, its plugin API is JEI's
  own `IModPlugin` rather than a renderer-neutral contract, and `ModPlugin` already gives it the
  loader's id, dependency and failure handling — so a `GuiPlugin` with one implementer and no second
  consumer would add a public type and no behaviour. The item-source and materializer tables are the
  P7 extension points; a second renderer re-opens the question when it lands.
- **Catalog.** `PreviewCatalog` builds one payload from `LevelAccess.documentIds()` (the merged
  built-in plus datapack ids) and `kernel.compile`; it keeps the identity-orientation projection only.
  `PreviewCatalogSync` pushes it from `PreviewEvents`' `OnDatapackSyncEvent` handler, which fires on
  join and on `/reload` before recipes are sent. Caps are code constants — 512 cells and 64 material
  lines per entry, 4096 cells for the whole catalog — and an over-cap or non-compiling blueprint is
  left out of the payload and named in a WARNING log line, never truncated. `PreviewCatalogClient` is the client snapshot plus a
  listener list; `PreviewCatalogPayload`/`PreviewOpenPayload` are plain data, so the payload
  registration path loads no client class.
- **Recipe table.** `registerRecipes` seeds from the snapshot, `onRuntimeAvailable` and every later
  catalog push reconcile it: a new id is `addRecipes`, a known id updates the existing instance in
  place, a vanished id is `hideRecipes`, a returning one is `unhideRecipes`. Identity sets (`sent`,
  `hidden`) make the two paths order-independent, and JEI's set-backed recipe list never sees the
  same instance twice.
- **View.** `BlueprintPreviewCategory.draw` owns the whole face: `PreviewScene` renders the cells as
  block models under its own orthographic camera, far-to-near by depth, and flushes with
  `BufferSource.endBatch()` rather than `GuiGraphics.flush()` (whose contract disables the depth test
  around the batch). `PreviewInputHandler` is the one input surface — drag rotates, the slider and
  the wheel move the Y slice, the button calls `JeiPreview.openSelected`, the mode button and the
  variant row switch their own view state. `JeiPreview` is the public face: listed ids, the frame
  counters the harness asserts on, `show(id)`, `openWorld(id)`, the variant selection
  (`variants(id)`, `variantIndex(id)`, `selectedBlueprintId(id)`, `cycleVariant(id, step)`) and
  `openSelected(id)`.
- **Ghost to world.** `PreviewOpenPayload` carries only the blueprint id; `PreviewService.open(
  ServerPlayer, NamespacedId)` takes the crosshair origin and runs the same session path as
  `/cumulus preview`, so a JEI-opened preview is the §4 session with the same confirm exit.
- **Variant page (E6).** The catalog still carries one entry per blueprint id (each member is an
  ordinary document with its own cells and materials), and every entry's `variants` summary lets the
  client build one page per group: `JeiPreviewPlugin.pages()` keys a group by the first member the
  catalog ships in declaration order and attaches the other shipped members to it, so
  `JeiPreview.blueprintIds()` lists pages, not variants. `BlueprintPreviewRecipe` holds the shipped
  members and the selected index; the variant row switches it in place as per-recipe view state (no
  payload field), and the button's `openSelected` sends the selected member's id. A member left out
  by a catalog cap is simply not switchable, and the catalog's WARNING line names it as for every
  left-out entry (design 15 §12.3).

## 18. Preferences (design 15 §9, landed with P5; presets at E7)

`dev.vfyjxf.cumulus.mc.pref` owns the siting preferences and their memory. The public values are
`BuildPreferences` (the mode, the fixed heading, the removal policy, the ordered weights and the
per-blueprint `ChoiceHistory`), `OrientationMode`, `RemovalPolicy` and `PlacementWeight`;
`ChoiceHistory` is design 15 §9's frequency window (most recent last, eight choices, the mode wins,
a tie goes to the choice whose latest occurrence is the most recent). Every write returns a new
`BuildPreferences`, and `remember` is the bounded LRU rule with the capacity the caller passes.
`BuildPreset` is the explicit-data value beside them (design 15 §13, D-99).

- **Scorer.** `SitingCandidateScorer` is pure and deterministic: it ranks the measured candidates by
  descending score and breaks a tie by the candidate's canonical enumeration index, so a scrambled
  input list ranks identically. The weights and their magnitudes are design 15 §9's table.
  `frontOf(Blueprint)` derives the declaration frame's front: the side the blueprint's door cells
  sit on when a check's focus subject is `DoorView` (the kernel publishes the subject type, not a
  focus id, so this is the reachable test), otherwise the identity `-Z` face.
- **Assembly.** `SolvePreferenceAssembler` is the one translation into the kernel (D-86): the
  current pose's still-needed declaration cells become ordered world-frame cell preferences, and the
  live plus remembered part pins become world-frame box pins; a pin or cell the pose cannot place is
  dropped, so the adapter never invents a cell.
- **Store.** `PreferencesSavedData` is a per-level `SavedData` at file id
  `structurecumulus_preferences`, keyed by player UUID. `ServerState.openLevel` — the
  `LevelEvent.Load` handler — warms it, and every access re-reads `level.getDataStorage()`, so no
  reference survives a level unload and a reloaded dimension reads its own file: the KB's level
  identity rule with the persistence a preference needs. A row keeps the explicit settings apart
  from the memory, so a changed config default still reaches a player who never ran `set`. The file
  carries `Version`, and a version this build does not know is dropped with a WARNING; a malformed
  player row is dropped and a malformed choice inside a row is skipped.
- **Config.** `config/structurecumulus-preferences.toml` is a second registered server file:
  `defaults.orientationMode`, `defaults.fixedHeading`, `defaults.removalPolicy`, `defaults.weights`
  and `rememberedCapacity` (default 64, range 1..4096). One capacity bounds both the blueprint LRU
  and the part-pin memory. A config that has not loaded yet answers the shipped defaults.
- **Command.** `PreferencesCommand.literal()` is the one node the command owner attaches inside
  `CumulusCommand.register` (`.then(PreferencesCommand.literal())`); `prefs` and `prefs show` print
  the effective preferences, the memory and the part pins, `prefs set <key> <value>` writes one
  setting (keys `orientation | heading | removal | weights | all`) and `prefs forget [id]` clears one
  blueprint's memory or all of it. Exit code 1 on success and 0 on an unknown key or value, a bad
  heading or no player. No command writes a choice: a committed confirm and a part cycle are the
  implicit writes (7.2).
- **Wiring.** `PreviewSession.open` ranks the candidate cycle with the player's effective
  preferences, seeds the part pins from the store and locks to a pose when exactly one satisfies
  strictly more built cells than every other (1.5). `PreviewBuilds` is the installed
  `PreviewConfirmHandler`: it narrows or widens the server gate by the player's `RemovalPolicy`, runs
  `BuildExecutor` on the session's siting and item source with the assembled `SolvePreferences`, and
  on a commit writes the siting memory and sends one closing sync. The session's source is the
  creative source for a creative player and the player inventory otherwise; the materials marker
  (4.4) reads `BuildMaterials.shortages` from the same dry run's item simulation, so a shortage list
  is never invented.
- **Presets (E7).** `BuildPreset` is one stored build request: the blueprint id, the full `Siting`, the
  `BuildPreset.Source` form (`creative`, `player`, `container` + position, or a registered provider id)
  and the recorded `RemovalPolicy`. `PreferencesSavedData` keeps them in the player row's `"Presets"`
  list, sorted by name, each tag carrying its own `Version` (`BuildPreset.formatVersion`, gate in
  `BuildPreset.read`); a malformed or unknown-shape tag is skipped and the other presets survive. The
  bound is `BuildPreset.capacity = 32`: `savePreset` answers `SaveResult.saved|replaced|full`, a full
  store refuses the next *new* name and never evicts one, and `deletePreset` is the only way down. The
  explicit/implicit split is the law here — the choice history and the pins are the LRU, the presets
  are the user's artifact, and `/cumulus prefs forget` never deletes a preset. `nextPresetName` is the
  deterministic first free `preset-N`.
- **Preset command (E7).** `BuildPresetCommand.literal()` is attached by the command owner as a child of
  `build` inside `CumulusCommand.register`, *before* the `<id>` argument, so the literal wins the parse:
  `save <name> [with …]`, `apply`/`run <name>`, `preview <name>`, `list`, `delete <name>`. `save` copies
  `PreviewService.context` (the session's `PreviewContext`: id, pose, source form, removal policy) and
  optionally overrides the form through `BuildCommand.items`, so a source refusal is the build
  command's own; `apply`/`run` resolve the stored form through the same helper and run `BuildExecutor`
  with `BuildConfig.policy()` narrowed or widened by the preset's policy, printing through
  `BuildCommand.print` and sending `BuildFeedback`. Name completion lists the player's presets; the
  kind-id completion lists the registered sources.
- **Preset preview (E7).** `PreviewService.openAt` is the pinned open: `PreviewSession.openPreset` builds
  a one-pose cycle from the stored `Siting` and takes the command's resolved `ItemSource`, so the
  session's ghosts, materials and confirm are exactly the stored request. The session refuses the
  cycling controls with a note and marks itself `preset <name>`; `N` still switches to the next variant
  through the ordinary re-open. `PreviewKeys.Action.savePreset` is `K`, which sends
  `PreviewControlPayload.Control.savePreset`; the session stores `preset-N` and answers in the HUD
  (the hint line is one info marker, the capacity refusal one warning marker).
- **Not persisted.** The build undo history stays in-memory (D-87); the preferences, their memory and
  the presets are durable.

## 19. Presentation (design 16, approved at E0, landing E1–E8)

`dev.vfyjxf.cumulus.mc.preview` owns the stage model and the payload facts, `...preview.client` owns
playback, the layered ghosts, the HUD tiers, the stage banner, the placement animator, the guide, the
hand-off transitions and the cinema mode, and `...preview.jei` owns the view modes. No third package appears, and the presentation pack adds no
module edge. The contracts are design 16's; this section states the adapter-side shape of them, and
its signatures join §15 per milestone (the batch 10 note there).

**Landed.** E1: the stage model, the presentation block on both payloads, staged playback and stage
switching, the layered ghosts, the three HUD tiers, the stage banner and the client config file. E2:
the commit-feedback payload and its four send sites, the pop-in, the rollback and undo fade, the
rate-limited cues and the formed celebration. E3: the document's `description` and bay `hint` in the
compiled value and on both payloads, the HUD title and stage lines, the JEI entry lines and the
part-hint billboard in the world (design 16 §3, §10). E4: the category's three view modes — the
three-view with its section band, the slice grid and the per-cell tooltip that names the owning part
(design 16 §11). E5: the guided build — `G`, the next-step line, the next group's emphasis, the hand
pulse, the frontier's automatic advance through the existing invalidation channel, and the hovered
ghost's tooltip; `Enter` stays §18's confirm, so a hand-placed stage and a machine-built one share one
executor (design 16 §12). E6: the variant groups — the document's `variants` block in the compiled
value, the summary on both payloads, `N`'s re-open and the JEI page's in-place switch
(design 15 §12, D-98). E8: the cinema mode — `C`, the camera program from the stage table, the
12/6/4/8-tick hand-off transitions and the `cinematics.*` keys (design 16 §6.7, D-100). The
milestone's state lives in
[`docs/plan/presentation-plan.md`](../plan/presentation-plan.md).
The sections that already describe this ground keep their meaning — §17's session, §17's payload caps,
§18's confirm path and §16's build, undo and codes.

**Server side (E1, E2; E3 fills the text values).**

| Piece | What it is | Owner |
| --- | --- | --- |
| the stage model | one pure function over a compiled blueprint and its definition-frame expected cells, producing the stage table (names, expected and satisfied counts, the bay's description and hint); no world, no siting, no clock (D-93) | `mc.preview` |
| the payload facts | the presentation block (the blueprint's description, the stage table, the frontier stage the marks imply, the plan's shortage lines) on the session payload and on the catalog entry; one stage index per ghost cell; ghosts ordered by stage then definition-frame cell order; the variant summary beside the presentation block on both payloads, compiled-bounded (E6) | `mc.preview.net` |
| the commit feedback | one S2C payload per committed transaction and per undo, to the invoking player only, listing the cells the transaction changed in the order it changed them, with the status and the formed flag (D-95); `ApplyReport.changes` is where the cells come from, and `BuildFeedback` is the one sender | `mc.preview.net`, sent by the confirm path and the build commands |
| the client config | `config/structurecumulus-client.toml`, one `CLIENT` file: `hud.*` with `animation.enabled`/`animation.speed` at E1, `animation.sound`/`animation.celebrate` at E2, `cinematics.enabled`/`cinematics.speed`/`cinematics.letterbox` at E8 | `mc.ClientConfig`, registered by the mod entry |

The server adds no presentation state: no per-player playback record, no animation queue, no tick
handler, no map beside the session. The payload is plain data, and its handler resolves client classes
only inside the enqueued work (§17's law, design 15 §6.4), so a dedicated server never loads one.

**Client side (E1, E2, E3, E4, E5, E8).** The playback controller keeps three facts per session — the
presentation block it was sent (table and frontier), the view cursor and the reveal counts — and
derives nothing from the marks. The layered renderer multiplies the stage factor into the ghost alpha it
already forces (§17's translucent pass) and leaves the Y slice and the distance cull in charge of
visibility. The placement animator consumes the commit payload in its list order and draws an extra
translucent copy of the model; it never reads the world to correct itself and nothing waits for it. A
capped payload's one note line is drawn by the animator's own HUD layer, which needs no session. The
part-hint billboards reuse the marker billboard's projection: each label is anchored at the bounding
centre of the cells its stage was sent, the cursor's stage draws at full alpha, the other parts faint,
and the same distance cull applies (design 16 §10).
The guide is the same session viewed differently: its automatic advance is the session's existing
invalidation channel (§17's `LevelWatch` revision check), its next-step line is composed by one
formatter with a fixed vocabulary, and its `Enter` is the §18 confirm unchanged — the world keeps one
writer (D-97).
The cinema mode is the third view: `CinematicController` owns the one client-side camera — a rig
entity that never joins a level, the recorded camera entity and type, the program cursor and the
hand-back — and reads the payload and the playback cursor; `TransitionController` owns the hand-off
animations (the stage advance, the turntable, the flip and the session fade) and is queried by the
ghost pass for its per-cell alpha and scale and for the one old-pose layer a frame may still draw.
Both are `Dist.CLIENT`-only state reached from `PreviewClientEvents`, `PreviewHud`, `PreviewClientState`
and `GhostRenderer`; `CinematicPrograms` is the pure geometry the mode samples. Nothing in the mode
sends a payload, and the only common-side change is `C`'s binding and the three `cinematics.*` config
keys.

**JEI side (E4, E6).** The category gains a mode button (isometric, three-view, slice grid) beside the
existing slider and button. All three modes draw the catalog entry's cells; the mode is per-recipe
view state, no payload carries it, and no cap changes — the entry is already at most 512 cells or
absent (§17's catalog law, design 15 §6.1). E6 adds the variant row: a group is one page keyed by the
first shipped member, the row switches the member in place (per-recipe view state, no payload field),
and the button opens the selected member's id through the same `openSelected` entry point
(design 15 §12.3).

**Variant groups (E6).** The document block is read and written by the host's `JsonReader`/`JsonWriter`
(a document-level `variants` member, canonical order after `description`), validated by
`DocumentCompiler` (`SC-DOC-0016`/`SC-DOC-0017`, every member compiled once) and carried as
`Blueprint.variants()`. `PreviewVariants` is the one conversion from the compiled carry to the wire
`VariantSummary` and the one switch arithmetic (declaration order, wrapping); both are package-private,
so the adapter's public surface grows by the payload record and `JeiPreview`'s five methods only.

**Still absent.** A server-side stage, playback or camera state; animation or particle work on the
server thread; a camera fact on the wire; broadcasting another player's placement animation; a
stage-number billboard in the world; a second client config file; localisation of the HUD and
formatter vocabulary; exporting a view as an image. The pack allocates no finding code (design 16 §14): what it shows is a mark, a stage name or a
document string, and an invalidation stays `SC-BLD-0008`.

## 20. Preview screen (design 17, approved at SC0, landing SC1–SC4)

Two packages, both on the physical client: `dev.vfyjxf.cumulus.mc.preview.scene` — the JEI-free scene
core, moved out of `...preview.jei` and extended with the camera, the hull, the exploded view and the
axis gizmo — and `...preview.client.screen` (a subpackage of the client one), which holds the screen
itself. `...preview.jei` keeps only the mezz.jei half: the category, its input handler, the
conditional plugin and its loader pass, `JeiPreview` and its client events. The contracts are design
17's; this section states the adapter-side shape of them, and its members join §15 per milestone
(the same batch discipline §19 used).

**Landed, and landing.** SC0 landed this section, [design/17](../design/17-preview-screen.md) and
D-101…D-105. SC1 has landed the migration, the stage's `bay` on both payloads, `PreviewScreen`, the
camera and projection, the four viewports, the axes, the screen's own clock, the hover and the
drawn-hull fit (the JEI view takes the same hull change and keeps its own camera). SC2 lands the staged
playback, the delta treatments and the exploded view. SC3 lands the links: the JEI button, the client
command, the `openScreen` key, the open payload's heading and the in-screen variant switch. SC4 lands
the demo documents. The milestone state lives in
[`docs/plan/screen-plan.md`](../plan/screen-plan.md).

**Wire additions (SC1 and SC3).** The cell shape does not move: `GhostCell` stays the one expected-cell
record every surface shares.

| Shape | Component | Direction | Cap |
| --- | --- | --- | --- |
| `net.Stage` | `NamespacedId bay`, the bay the stage derives from | S2C on both the session payload and every catalog entry | none of its own: a bay with a drawn cell owns at least one stage, and the table is capped at `maxStages` = 256, so the distinct bays cannot exceed the stages |
| `net.PreviewOpenPayload` | `@Nullable Heading facing`, the direction the viewer faces | C2S, the existing open channel | an enum, never a list; the four horizons are accepted and `up`/`down` read as absent |

`PreviewService.open` keeps its two entry points: the player-facing one passes the player's own facing
as today, and the ghost-to-world one passes the payload's heading where that facing goes, so the
session's cycle start and its facing weight follow the screen's side and nothing else moves. No third
payload, no S2C open and no second open path appear.

**Public shape promises.** Member-level signatures are not listed here yet; they join §15 at the
milestone that lands the code (SC1 for the scene and the screen's skeleton, SC2 for the player and
the explode, SC3 for the action and the command), and a name that exists in neither §15 nor
`tools/spec-uncovered.txt` is a gate failure until then. The types are:

| Package | Type | What it is |
| --- | --- | --- |
| `preview.scene` | `PreviewScene` (with its `View` and `LayerRule`) | one page's cells resolved against the client registry, drawn through a rectangle and a camera; moved from `preview.jei` |
| `preview.scene` | `BlueprintPreviewRecipe` | the page model: the shipped members, the selected index, the scene, the materials, the presentation; the per-surface view state stays with JEI |
| `preview.scene` | `PreviewViewMode` | the scene's projection vocabulary, for the JEI face to select |
| `preview.scene` | `SceneCamera` (with `Projection` and `Snap`) | the orbit camera value: target, yaw, pitch, distance, zoom, projection; pure operations only |
| `preview.scene` | `Hull` | the drawn hull of design 17 §3.3, the input of every fit |
| `preview.scene` | `Fit` | the fit of design 17 §4.3: one hull into one viewport through one camera, the scale and the 2D projection the scene, the gizmo and the picking share |

The screen's `SceneCamera` is a turntable — yaw about the world's vertical, pitch above the horizon —
so its default 45°/35.264° is the true isometric view. The JEI face keeps its own `View` yaw/pitch
tilt (design 16 §11), so the migration changes no JEI picture: the two surfaces have two cameras.
| `preview.scene` | `ExplodeModel` | the per-bay offset function of design 17 §8.1 |
| `preview.scene` | `AxisGizmo` | the three axes and their label anchors, projected through a camera |
| `preview.scene` | `CellStates`, `TintedBuffers` | the payload-to-`BlockState` resolution and the forced-alpha pass, moved from `preview.client`; `PreviewScene`'s private duplicate of the first is deleted |
| `preview.client.screen` | `ScreenLayout` (with its `Rect`) | the four viewport rectangles and the two strips of design 17 §5.1, a pure function of the screen size |
| `preview.client.screen` | `ScreenClock` | the screen's own two-number clock of design 17 §4.6, advanced by the frame's real-time tick delta |
| `preview.client.screen` | `PreviewScreen` | the one `Screen`: the layout, the four viewports, the widgets, the input and the one world action |
| `preview.client.screen` | `ViewportWidget` | one viewport's rectangle, its camera derivation, its draw and its picking |
| `preview.client.screen` | `CameraController` | the input table of design 17 §4.4 translated into `SceneCamera` operations, plus the stage framing and its hand-back |
| `preview.client.screen` | `StagePlayerWidget` | the stage bar, the cursor and reveal counts, the summary line and the clock |
| `preview.client.screen` | `ScreenButtons` | the button row of design 17 §4.5, one button per action |
| `preview.client.screen` | `GhostToWorldAction` | the one C2S send, with the camera's heading |

**What the screen never does.** It reads no world state (no `Level`, no `ClientLevel`, no
`getBlockState`, no camera entity) and no session: its only input is `PreviewCatalogClient`'s
snapshot, plus its own view state. It sends one packet — the open of design 17 §9.4 — and adds no
server-side state, no tick handler, no map, no config file and no finding code (design 17 §12). It
names no `mezz.jei` type, so it loads with JEI absent; neither package is nameable from the common
side, and the dedicated-server runs load neither. The client tick is not the screen's clock: a screen
pauses the game, so the screen accumulates the frame's real-time tick delta and samples it, and no
screen animation reads the game tick.

**Config and keys.** The screen obeys `animation.enabled` and `animation.speed` (the client file of
§19) and adds no key of its own to that file. The preview keys gain exactly one `KeyMapping` action —
`openScreen`, default `O`, the world's key for opening the screen on the open session's blueprint —
and every key the screen itself uses (design 17 §4.4) is a `Screen` key, not a binding: a screen owns
the keyboard while it is open, and no world binding fires through it.

**Still absent.** A session behind the screen; a server-side screen, camera or explode state; a second
cell, stage or open payload; a slice, a material list or a camera memory in the screen; an S2C screen
request or a server-side `screen` verb; a second scene renderer or a second face for another mod's
browser; a screen-specific config key; and any world-space work (a `RenderLevelStageEvent` handler, a
stage-number billboard, a screen overlay in the world) for a screen that is not in the world.

