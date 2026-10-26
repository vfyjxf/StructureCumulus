# 16 — Minecraft adapter (A1)

> A1 · `dev.vfyjxf.cumulus.mc` · `src/main/java` · serves R-01/02/04/05/11/12 · carries
> I-1/2/4/5/10. Baseline 1.21.1 NeoForge 21.1.x (`neoforge-21.1.219.jar`). Prior art:
> ArchitectCumulus `42`–`45`; this file's lexicon wins.

## 1. Scope

The adapter converts world values into kernel values and kernel values back into world writes; it
never decides what a value means. One package, no `client` subpackage: the two client classes sit
behind `FMLLoader.getDist().isClient()` (`FMLLoader.java:160`). `mc` imports kernel; the kernel
imports neither `mc` nor Minecraft (`02-architecture.md` §2, I-1).

| A1 implements | A1 must not |
| --- | --- |
| `WorldCapture`, `WorldView`, `NestedView`, `OccupantResolver` | teach the kernel what a block is; expose `BlockState`/`Level`/`Entity` in a kernel signature |
| `ClaimLedger` over `SavedData` | hide `DENIED`/`NOT_CAPTURED`; infer air from a failed read; mint one ref per cell |
| `EditExecutor` + `EditTransaction` | write outside `plan.touched()`; apply without a dry run; write from a matcher or deriver |
| `DataBridge<JsonElement>`, `DataFrontend` | let `com.mojang.serialization` into a kernel signature |
| overlay, `FindingSink`, `/cumulus`, GameTests | capture, match or write on the client; read a level off the server thread |

## 2. Vocabulary

Kernel names link to `01-glossary.md` once here.

| A1 name | Role |
| --- | --- |
| [`LevelCapture`](01-glossary.md) | `WorldCapture`: the only A1 member that captures a `Level` into kernel values; server thread; one world |
| [`LevelSnapshot`](01-glossary.md) | `WorldView`: immutable cells, no `Level`; `NestedLevelSnapshot` is a `NestedView` |
| [`WorldOccupants`](01-glossary.md) | `OccupantResolver<Raw>`: block/block-entity/entity/extent → `OccupantSnapshot` |
| [`OccupantDescriptors`](01-glossary.md) | helper: state/body/entity → `OccupantDescriptor`, and exact inverse |
| [`OccupantTraits`](01-glossary.md) | helper: immutable inventory, energy, fluid, entity-NBT values |
| [`OrientationMapping`](01-glossary.md) | helper: `Heading`/`Turn`/`Tilt`/`Flip` ↔ `Direction`/`Rotation`/`Mirror` |
| [`DatapackBlueprints`](01-glossary.md) | reload listener: compact/canonical JSON → `BlueprintDocument` → `Blueprint`, cached per epoch |
| [`SavedClaims`](01-glossary.md) | `ClaimLedger` + `SavedData`: one store per world (one `ServerLevel`), revisioned, fail-closed |
| [`WorldEdits`](01-glossary.md) | `EditExecutor`: transactional writes, first-touch rollback |
| [`ClientOverlay`](01-glossary.md) | client renderer: `OverlayModel` → line boxes, no kernel service |
| `FindingOutput` | `FindingSink`: log, chat or JSON; never decides, never reorders |
| [`CumulusCommand`](01-glossary.md) | command tree: `capture`, `import`, `lint`, `validate`, `preview`, `build`, `explain` |
| `ServerState`, `JsonFrontend`, `JsonBridge` | A1-internal: per-server catalog/facade/ledgers; the two Gson classes |

## 3. API design

```java
public final class LevelCapture implements WorldCapture {
    public LevelCapture(ServerLevel level, OccupantDescriptors d, WorldOccupants r, ServerState s);
    public CaptureResult capture(CaptureRequest req, IssueSink issues);   // lease + issues, never null
    public long latestRevision();
}
public class LevelSnapshot implements WorldView {
    public SnapshotId snapshotId();
    public SectionSlot slot(SectionKey k);
    public @Nullable OccupantSnapshot occupant(LatticePoint c);
    public @Nullable OccupantSnapshot occupant(OccupantRef r);
    public List<OccupantSnapshot> occupants(Cuboid b, OccupantFilter f);
    public @Nullable NestedLevelSnapshot nested(LatticePoint c);
    public long contentDigest(SectionKey k);
    public long estimatedBytes();
    public List<Finding> findings();                       // cap/refusal reports, status unchanged
}
public final class NestedLevelSnapshot implements NestedView { /* parent, depth, ancestry, same bytes */ }
public final class WorldOccupants implements OccupantResolver<WorldOccupants.Raw> {
    public sealed interface Raw permits Block, BlockEntity, Entity, Extent {}
    public record Block(BlockState state) implements Raw {}
    public record BlockEntity(BlockState state, net.minecraft.world.level.block.entity.BlockEntity body) implements Raw {}
    public record Entity(net.minecraft.world.entity.Entity body) implements Raw {}
    public record Extent(BlockEntity parent, Cuboid localBox) implements Raw {}
    public Set<OccupantKind> kinds();  public @Nullable OccupantSnapshot resolve(Raw r, ResolveContext c);
    public @Nullable Raw.Extent extent(Raw r);  public OccupantSnapshot resolveNested(Raw r, ResolveContext c, NestedView v);
}
public final class OccupantDescriptors {
    public OccupantDescriptor block(BlockState s);  public OccupantDescriptor blockEntity(BlockState s, BlockEntity be);
    public OccupantDescriptor entity(Entity e);     public @Nullable BlockState stateOf(OccupantDescriptor d);
    public static NamespacedId id(ResourceLocation loc);
}
public final class OccupantTraits {
    public static final TraitKey<ItemInventory> ITEM_INVENTORY;   // + ENERGY, FLUID, ENTITY_NBT
    public record StackCount(NamespacedId item, int count) {}  public record ItemInventory(List<StackCount> slots, int size) {}
    public record Energy(int stored, int capacity) {}          public record Tank(NamespacedId fluid, int amount, int cap) {}
    public record FluidContents(List<Tank> tanks) {}           public record EntityNbt(String canonicalSnbt) {}
    public static List<Trait<?>> of(ServerLevel l, BlockPos p, BlockState s, @Nullable BlockEntity be, Set<TraitKey<?>> req);
    public static Trait<EntityNbt> entityNbt(Entity e);
}
public final class OrientationMapping {
    public static Heading heading(Direction d);  public static Direction direction(Heading h);
    public static Rotation rotation(Turn t);     public static Turn turn(Rotation r);
    public static Mirror mirror(Flip f);         public static Flip flip(Mirror m);
    public @Nullable Heading anchorHeading(BlockState s);  public Remap remap(BlockState s, Orientation o);
    public sealed interface Remap permits Remap.Done, Remap.Refused {
        record Done(BlockState state) implements Remap {}
        record Refused(FindingCode code, String detail) implements Remap {}
    }
}
public final class DatapackBlueprints extends SimplePreparableReloadListener<DatapackBlueprints.Draft> {
    protected Draft prepare(ResourceManager r, ProfilerFiller p);        // off-thread
    protected void apply(Draft d, ResourceManager r, ProfilerFiller p);  // server thread
    public record Draft(Catalog catalog, List<ValidationIssue> issues) {}
    public record Compiled(NamespacedId id, String digest, @Nullable Blueprint bp, List<ValidationIssue> issues) {}
    public record Catalog(long epoch, Map<NamespacedId, Compiled> docs) { public @Nullable Compiled document(NamespacedId id); }
}
public final class SavedClaims extends SavedData implements ClaimLedger {
    public long revision();                                 // the CAS base, from the stored file
    public ClaimVerdict settle(SettleRequest request);      // rows of (world, owner, siting); one CAS
    public ClaimVerdict release(OwnerId owner, Siting siting);  // one transaction deleting every row of the key, revision +1; an absent key is a no-op with no bump
                                                                 // called only from 23 §5's three lifecycle points (20 §3.2)
    public ClaimView read(Cuboid box);                      // rows/revision/available; never null
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs);
}
public final class CaptureTool {                            // document capture (`/cumulus capture`)
    public static @Nullable DataView capture(CaptureRequest request, IssueSink issues);
}
public final class WorldEdits implements EditExecutor {
    public WorldEdits(ServerLevel l, Gate gate, Options o);
    public EditTransaction begin(WorldView view, Siting siting, ClaimView claims,
                                 long ledgerRevision, PlanDigest plan);   // REFUSED when the host declines
    public record Options(int maxCells, boolean loadChunks, int flags) { public static Options defaults(); }
    public interface Gate { boolean allows(CuboidSet cells); }
}
public final class ClientOverlay { public void accept(OverlayPayload p); public void render(RenderLevelStageEvent e); }
public final class CumulusCommand { public static void register(RegisterCommandsEvent e, ServerState s); }
```

`capture` returns `Unavailable` for "cannot serve" (off-thread, denied) and `TooLarge` for a cap;
it never returns `null` and never returns a snapshot without its lease; every loss is reported to the
`IssueSink`. `occupant` is `null` iff `readStatus != READABLE`; `stateOf` is the exact inverse or
`null`; `remap` returns `Refused` as a value; `settle` replaces every row of one `(world, owner, siting)` in
a single CAS and returns the kernel's `ClaimVerdict` — `Settled`, `ClaimConflict` with the incumbent
rows, or `RevisionMoved`; a `REFUSED` `begin` writes nothing.

## 4. Semantics

### 4.1 Capture: sections, payloads, thread

```
1 reject: !server.isSameThread() || volume > maxCells || empty box
2 rev = worldCounter.incrementAndGet()                    // before the first read
3 for each 16³ section in (y, z, x):
     chunk = serverChunkCache.getChunkNow(cx, cz)         // never Level.getChunk
     chunk == null                 -> Refused(NOT_CAPTURED, SC-MC-0306)
     section outside build height  -> Refused(OUT_OF_BOUNDS, SC-MC-0505)
     section.hasOnlyAir()          -> Air
     cap exceeded                  -> Refused(NOT_CAPTURED, SC-MC-0501)
     else                          -> Present(store.intern(key, states.copy(), rev))
4 for each Present section:
     for pos in chunk.getBlockEntitiesPos() ∩ section:
         if watched: be = chunk.getBlockEntity(pos, CHECK);
                     payload = be.saveWithoutMetadata(regs)  // type from be.getType()
5 one AABB entity query; admit by anchor; copy type/UUID/box/saveWithoutId
6 stamp SnapshotId(world, rev); freeze lists; wrap in a SnapshotLease; append CaptureIssues
```

That lease answers the two facts the gate reads: `changeCount()` is the watched region's mark
counter, zero at capture, and `isValid()` is false once the world's generation moved or the handle
was closed. `16` keeps the generation in the world's own bookkeeping; the kernel never reads it.

| Work, 7×6×7 | Cost |
| --- | --- |
| chunk gate | ≤4 columns; `ServerChunkCache.getChunkNow` (`ServerChunkCache.java:162`) is main-thread only and does not load |
| block state | one `PalettedContainer.copy()` per 16³ section (`PalettedContainer.java:265`), not 294 cell reads |
| block entity | only `getBlockEntitiesPos()` positions, not every cell |
| entities | one `getEntitiesOfClass` (`EntityGetter.java:29`), cost by intersecting sections |
| entity NBT | `e × Entity.saveWithoutId` (`Entity.java:1725`), only when required |

Caps: `maxCells`, `maxSections`, `maxBytes`, `maxEntities`, `maxPayloads`; over cap is
`CAPTURE_LIMIT`/`NOT_CAPTURED`, never a partial snapshot treated as complete. `MAX_CELLS = 1 << 20`
refuses a pathological box.

**Thread.** `ServerChunkCache.getChunkNow` and `LevelChunk.getBlockEntity` are main-thread only
(`ServerChunkCache.java:162`, `Level.java:805-811`). `capture` checks `server.isSameThread()` and
returns `Unavailable(OFF_THREAD)` otherwise. Capture is one call, never a tick pipeline; section copies are cheap and
air-only sections are skipped. A huge structure pays latency or is refused; it never sees a blend.

**Issues and lease.** `capture(request, issues)` never throws for a world condition and never
returns `null`: an unrecoverable read is `Refused(NOT_CAPTURED)` inside the snapshot and an issue in
the sink, and a cap is `TooLarge`. The `CaptureResult` carries the snapshot and its lease; the lease
is handed to the scheduler, which owns its release on every path — a result's child handle, a
coalesced-away submit, a defer, a cancel and a stop (§4.10). A1 never closes a lease it merely
returned.

**Store (optional).** By default a `LevelSnapshot` owns its section copies. A per-world
`SectionStore` may be enabled after profiling; it holds the current `SectionSnapshot` per
`(SectionKey, revision)`. `intern` returns the live entry when `contentEquals` holds, so a no-op
refresh keeps object and revision. The store is a weak cache; a `LevelSnapshot` holds the strong
refs. A sealed
snapshot never lazily resolves a section through the store.

### 4.2 Statuses, immutability, revision

| Situation | `readStatus` | `occupant` |
| --- | --- | --- |
| outside `bounds()` | `OUT_OF_BOUNDS` | `null` |
| chunk missing / not `FULL` / unloaded | `NOT_CAPTURED` | `null` |
| section outside build height | `OUT_OF_BOUNDS` | `null` |
| section read, empty (`Air`) | `READABLE` | `OccupantSnapshot.air(id, cell)` |
| section read, block present | `READABLE` | that block snapshot |
| payload cap / nested depth | `NOT_CAPTURED` / `DENIED` | `null` |
| covered by an admitted multi-cell occupant | `READABLE` | that occupant |

`LevelSnapshot` implements `WorldView`; `NestedLevelSnapshot` implements `NestedView` over the same
bytes. It holds `SnapshotId`, `Cuboid`, `SectionSlot[]`, payload cells, entity index and findings,
all unmodifiable; no `Level`, `ChunkAccess`, `BlockEntity` or `Entity` is reachable. `SectionSlot`
is `Air` | `Present(SectionSnapshot)` | `Refused(status, code)`.

`SnapshotId.revision` is a per-`ServerLevel` `AtomicLong`, reserved before the first read, never
persisted. A content-equal refresh keeps the revision; a real copy bumps it. `SnapshotId.world` is the kernel's `World`, built from `ResourceKey.location()` (`ResourceKey.java:82`)
of `Level.dimension()` (`Level.java:1179`) — the one place where the Minecraft side's level becomes
the kernel's world.

### 4.3 Descriptor and traits

| Source | `type` | `properties` / tags | Traits |
| --- | --- | --- | --- |
| block | `BLOCK.getKey` (`BuiltInRegistries.java:140`) | every state property, `Property.getName()` (`:52`) = `Property.getName(value)` (`:65`), sorted; block tags (`Holder.java:30`) | none |
| block entity | `BLOCK_ENTITY_TYPE.getKey` (`:147`, `BlockEntity.java:257`) | the block state's properties; block tags | `ITEM_INVENTORY`, `ENERGY`, `FLUID` |
| entity | `ENTITY_TYPE.getKey` (`:142`, `Entity.java:308`) | none; entity-type tags | `ENTITY_NBT` |

Eager: type, properties, tags, footprint. Eager when `policy.requiredTraits()` names the key:
inventory/energy/fluid (`Capabilities.ItemHandler.BLOCK` `:37`, `EnergyStorage.BLOCK` `:21`,
`FluidHandler.BLOCK` `:29`), entity NBT. Never: a live body, a UUID, position-dependent NBT,
component trees, loot tables, unresolved capabilities.

`ENTITY_NBT` is canonical SNBT of `saveWithoutId` minus `Pos`/`Motion`/`Rotation`/`UUID`/
`FallDistance`/`PortalCooldown`, so one cow captured twice compares equal — A1's answer to the
glossary's "optional payload" (`06-occupants.md` §3.3 has no slot). A throwing capability leaves
the trait absent → `UNREADABLE`, never a zeroed inventory; inventory keeps `(item id, count)`.

### 4.4 Orientation

Orders differ; never compare ordinals (`Direction.java:27-32`). `Heading`/`Direction`:
NORTH/SOUTH/WEST/EAST/UP/DOWN ↔ `NORTH`/`SOUTH`/`WEST`/`EAST`/`UP`/`DOWN`.
`Turn`/`Rotation`: NONE/CLOCKWISE/HALF/COUNTERCLOCKWISE ↔
`NONE`/`CLOCKWISE_90`/`CLOCKWISE_180`/`COUNTERCLOCKWISE_90`. `Flip`/`Mirror`:
NONE/LEFT_RIGHT/FRONT_BACK ↔ `NONE`/`LEFT_RIGHT`/`FRONT_BACK`.

`Rotation.rotate(Direction)` fixes Y and yaws the rest (`Rotation.java:66-79`);
`Mirror.mirror(Direction)` negates Z for `LEFT_RIGHT`, X for `FRONT_BACK` (`Mirror.java:59-64`),
matching `Flip` (`05-space.md` §4.4). No controller entity appears anywhere.

`remap` reads only `turn()`, `tilt()` and `isMirror()`: `Flip.LEFT_RIGHT` is not a separate group
element (it normalises to `Turn.HALF ∘ Flip.FRONT_BACK`), so every improper orientation is
realised as `Mirror.FRONT_BACK` followed by the canonical turn (`05-space.md` §3.4). `mirror(Flip)`
and `flip(Mirror)` exist for parsing and diagnostics, not for `remap`.

1. **Yaw + mirror** (`tilt() == LEVEL`): `state.mirror(m).rotate(r)`, mirror first, vanilla's
   order (`StructureTemplate.java:254`). Block overrides do the work: doors rotate `FACING` and
   cycle `HINGE` (`DoorBlock.java:268`, `:276`); stairs rotate `FACING`, mirror `SHAPE`
   (`StairBlock.java:201`, `:209`).
2. **Any tilt**: vanilla has no pitch API. Rewrite `DirectionProperty`/`Axis` by
   `o.apply(heading)`; refuse a state carrying `SHAPE`, `HINGE`, `WALL`, `RAIL_SHAPE` or
   `ORIENTATION` → `Refused(SC-MC-0203)`. Tilt is exact on positions for all 48 orientations,
   exact on states only where the directional semantics are a signed permutation.

**Anchor heading** probes by property *instance*: `FACING` (`BlockStateProperties.java:50`),
`HORIZONTAL_FACING` (`:54`), `ORIENTATION` front (`:56`). Both facings are spelled `facing`, so
`hasProperty` is the only safe test. `AXIS` (`:43`) is not a heading source (a pillar would claim
a false north); `null` leaves the role unresolved, with no fallback heading — `OrientationPolicy.select`
returns "no admissible orientation", never identity (`09` §4.5). After either path every
`Direction` property is compared with `o.apply(headingOf(old))`; a mismatch is `Refused`, never
the silent no-op of `BlockBehaviour.rotate` (`BlockBehaviour.java:282`) or `mirror` (`:289`).

### 4.5 Reload: JSON → catalog

| Step | Rule |
| --- | --- |
| `prepare` (off-thread) | `listResources("cumulus/structure", p -> p.getPath().endsWith(".json"))` (`ResourceManager.java:17`) |
| parse | `JsonFrontend` → `DataView`; Gson has no offsets, so values are span-less (`SC-DOC-0403` once) |
| grammar | `DocumentSchema.current().read(..., DocumentProfile.AUTO, ...)`; compact or canonical; `null` → `SC-MC-0102` plus issues |
| validate/assemble | `DocumentValidator`, `SegmentAssembler`; both pure; `Compiled(id, digest, bp, issues)` |
| `apply` (server thread) | one `AtomicReference<Catalog>` swap, `epoch + 1`; readers see old or new |
| report | issues kept for `/cumulus validate`; a broken document is **absent**, never silently replaced by its previous version |

`SimplePreparableReloadListener` runs `prepare` on the background executor and `apply` on the
game executor (`SimplePreparableReloadListener.java:9-20`); `AddReloadListenerEvent.addListener`
(`AddReloadListenerEvent.java:44`) supplies registry access. Catalog per server
(`ServerAboutToStartEvent.java:19` / `ServerStoppingEvent.java:15`), ledger per level, overlay per
client.

### 4.6 Claims: persisted, revisioned, fail-closed

One `SavedData` per world — that is, per `ServerLevel` — via `ServerLevel.getDataStorage()`
(`ServerLevel.java:1370`) and `DimensionDataStorage.computeIfAbsent` (`DimensionDataStorage.java:43`),
file `cumulus_claims.dat` under that world's `data/`:
`DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))`
(`DimensionType.java:152`, `MinecraftServer.java:1954`, `LevelResource.java:15`) + `data`.

| Concern | Rule |
| --- | --- |
| row | `ClaimRow(world, cell, sharing, owner, siting, bay)`; `(world, owner, siting)` is both the scope of a replace and the self-conflict exclusion |
| read | `read(Cuboid)` returns a `ClaimView`; `available() == false` = cannot answer, an available empty view = nothing claimed (`08-claims.md` §4.7) |
| revision | one `long` in the file, `+1` per write, `setDirty()` (`SavedData.java:18`); the CAS token |
| corrupt vs absent | `DimensionDataStorage.get` returns `null` for both (`:55`, `:73-77`), and `computeIfAbsent` would create an empty store. A1 checks file existence: absent → create; present but unreadable → `unavailable`, every read unavailable, no settlement |
| settlement | one CAS on `(world, owner, siting)`: call `ClaimMatrix.conflicts` for the incoming rows against every row outside that scope, then delete all scope rows and insert the new ones with one `setDirty` |
| conflict | `ClaimMatrix` reporting at least one conflicting pair → `ClaimConflict(rows, findings)`, **nothing written**; a moved revision → `RevisionMoved(expected, actual)`, never a conflict |

`ClaimMatrix` is the only conflict predicate: the kernel planner and `SavedClaims` call the same
function, and A1 adds no "foreign `EXCLUSIVE`" test of its own (`22` §10.1). `RevisionMoved` is the
ledger saying the world moved, not that the caller was wrong, so it is never reported as a
conflict.

Settlement is a **server-thread entry point chained by the scheduler**, not something the matcher
does: `check` returns the planned claim with the frozen revision it read and writes nothing, and a
caller that wants the claim registered calls `settle` once with that plan. Only a `Settled` verdict
yields `Formed`. The matcher never touches `SavedData`; a `ClaimConflict` becomes
`Failed(SC-CLM-0002)` with the incumbent rows, and a `RevisionMoved` is retried once inside the call
before `Undecided`.

### 4.7 Edits: transaction, rollback, blast radius

| Stage | Rule |
| --- | --- |
| gate | `REFUSED` when off the server thread (`SC-MC-0301`, `BlockableEventLoop.java:35`), view is not this level's view, a cell is outside the border (`SC-MC-0305`, `WorldBorder.java:32`), a cell is unloaded (`Level.java:829`), the gate denies (`SC-MC-0401`), or `plan.touched().volume() > maxCells` (`SC-MC-0307`) |
| pre-image | first touch of every written cell: `getBlockState` (`Level.java:406`) + `BlockEntity.saveWithId` (`BlockEntity.java:106`) |
| write | `level.setBlock(pos, state, flags)` (`Level.java:228`) with `UPDATE_ALL` (`Block.java:85`) + `UPDATE_KNOWN_SHAPE` (`:81`); `false` = failed step |
| payload | a placed block entity starts empty (`OccupantDescriptor` has no NBT); `SetTrait` and entity edits are refused (`SC-MC-0304`) |
| restore | set the block first, then `level.getBlockEntity(pos)` (IMMEDIATE, `Level.java:805-811`) and `loadWithComponents(tag, regs)` (`BlockEntity.java:82`) — vanilla's own order (`StructureTemplate.java:276`); call `markPayload(pos)` because `loadWithComponents` bypasses `blockEntityChanged` |
| rollback | inverse order, state then payload; a restoration failure is `SC-MC-0405`; `close()` rolls back `OPEN`/`FAILED` only |
| blast radius | exactly `plan.touched()`; `SC-MC-0307` refused, `0402` step failed, `0403` rolled back |

`UPDATE_KNOWN_SHAPE` suppresses shape cascades, not gravity or fluid flow; a plan over sand or
water may change unnamed cells.

### 4.8 Overlay: data to pixels

| `CellMark` | Box | RGBA | Extra |
| --- | --- | --- | --- |
| `EXPECTED` | full, inset 0.02 | 0.20, 0.80, 1.00, 0.35 | — |
| `PRESENT` | full | 0.20, 1.00, 0.40, 0.30 | — |
| `MISSING` | full | 1.00, 0.25, 0.20, 0.55 | — |
| `EXTRA` | full | 1.00, 0.60, 0.10, 0.45 | — |
| `WRONG` | full | 1.00, 0.20, 0.80, 0.50 | — |
| `BLOCKED` | full | 0.60, 0.00, 0.00, 0.60 | X on top face |
| `UNKNOWN` | wireframe | 0.60, 0.60, 0.60, 0.15 | no fill |

Draw on `RenderLevelStageEvent` at `AFTER_TRANSLUCENT_BLOCKS` (`RenderLevelStageEvent.java:37`,
`:64`) with its `PoseStack` (`:78`); one `RenderType.lines()` buffer, one
`LevelRenderer.renderLineBox` per cell (`LevelRenderer.java:2231`), grouped by mark. Cells are
LOCAL; map each `LatticeKey` through `siting.toWorld` and skip unloaded client chunks.
`Marker`s become a label or one rate-limited particle (`Level.addParticle`, `Level.java:530`).
The renderer imports only kernel value types, never captures/matches/solves/writes, and is
constructed only under a client dist check (`FMLLoader.java:160`). Payload cap 8192 cells.

### 4.9 Commands and GameTest

| Command | Side | Behaviour |
| --- | --- | --- |
| `/cumulus capture <id> <from> <to>` | server | `CaptureTool.capture(request, issueSink)` → compact JSON `DataView` or `null`; writes a datapack file or copies to chat |
| `/cumulus import <file>` | server | `.schem`/`.nbt` → compact JSON; unsupported entries become findings |
| `/cumulus lint <file>` | server, no world | JSON profile parse + `DocumentValidator`; line/column issues |
| `/cumulus validate <id>` | server, no world | re-runs `DocumentValidator`, prints issues and human findings |
| `/cumulus preview <id> [pos]` | server compute, client draw | capture footprint + 1, `PROBE` → `FORM`, `solve(PREVIEW)`, send `OverlayPayload` |
| `/cumulus build <id> [pos]` | server | permission 2 (`CommandSourceStack.java:390`), `check` → `settle`, `solve` → `Repair`, `PlanValidator`, `applyIfMachineApplicable` |
| `/cumulus explain <code>` | either | `FindingCode.parse` + `ExplainBook`; no world |

Tree on `RegisterCommandsEvent.getDispatcher()` (`RegisterCommandsEvent.java:36`) via
`Commands.literal("cumulus")` (`Commands.java:419`); feedback via `sendSuccess`
(`CommandSourceStack.java:480`). GameTests (`@GameTest`, `GameTest.java:10`; `setBlock` `:345`,
`assertBlockState` `:415`, `succeed` `:777`) cover **placement** (one cell removed ⇒ the next
attempt is `Dropped(SNAPSHOT_CHANGED)`, never a `Formed` on the old capture), **rotation** (four yaws
on a door and a stair), **shared claim** (two `SHARED` settle; a third `EXCLUSIVE` is
`SC-CLM-0002`, ledger byte-identical), **rollback** (forced failure at step 3 restores every state
and block-entity NBT).

### 4.10 Scheduler, dirty state, snapshot check

`ServerState` owns one `MatchScheduler`. `submit` runs on the server thread; `CheckKey` is
`(world, owner)`, while `Place` is the host's target and is resolved through the facade's
`siting(blueprint, origin)` before dispatch, so the kernel request carries a plain `Siting`. One
task per key may be in flight; a newer submit increments a ticket and coalesces, it never starts a
second task. `INTERACTIVE` holds one reserved permit and `AUTO` gets `max(0, threads - 1)`; on a
one-worker host `AUTO` is disabled, and `/cumulus workers` says so. `AUTO` without a permit is
`DEFERRED`, and the scheduler's own `tick()` re-probes it. `AUTO` runs `PROBE` only;
a passing probe causes a separate `FORM` submit, so AUTO tasks stay short and coalescible.
The scheduler also runs the watchdog; it never starts a check from the dirty pump.

`LevelChunk.setBlockState` marks its 16³ `SectionKey` dirty. `Level.blockEntityChanged` marks a
payload position only if a watch reads that payload. Entity join/leave marks its footprint and every
window containing it; movement is found by a **per-tick window diff on the server thread** — each
window's live `cell → EntityKey(uuid, type, digest)` set against the captured one, cost bounded by the
window's entity count — not by a block hook and not by a `setPos` hook. Chunk unload marks the
sections `UNKNOWN`. `DirtyIndex` keeps four maps, the fourth being `window → Set<InstanceRef>`;
marks are O(1), and no mark walks a volume.

On dispatch, the server thread freezes the claim view (immutable rows + revision over the union of
anchor and settle cells) and refreshes stale sections, payloads and entity windows (one shot; cap
exceeded = `DEFERRED`, slot stays dirty). A result may be published or settled only while its lease
answers `CURRENT`: `lease.changeCount() == 0` and `lease.isValid()` (`11` §4.6). A mark is
`Dropped(SNAPSHOT_CHANGED)`; a replaced world or a closed handle is `Dropped(CAPTURE_INVALID)`. The
caller that registers then calls `settle` on the server thread, and `SavedClaims.settle` runs with the
frozen revision: `ClaimConflict` is `Failed(SC-CLM-0002)`, and `RevisionMoved` is retried once, then
`Undecided(LEDGER_MOVED)`.
`onTerminal` fires for every outcome; `onSettled` is the success-only convenience.

`InstanceRecord` owns the capture. `Formed` and `Admitted` hold child handles over it, and `derive`
reads frames rather than the lease. A caller that never settles closes `Admitted` itself; `settle`
closes it on every path and hands `Formed` its own child handle, which the caller closes after
`derive`. `retain(PinBudget)` can refuse with `SC-MC-0502` and the caller keeps the instance's own
reference.

## 5. Reference sketch

```java
// (1) Capture 7×6×7 as section copies on the server thread; unloaded is not air.
CaptureResult capture(CaptureRequest req, IssueSink issues) {
    if (!server.isSameThread()) return CaptureResult.unavailable(OFF_THREAD);  // BlockableEventLoop.java:35
    Cuboid box = req.bounds(); if (box.volume() > req.limits().maxCells()) return CaptureResult.tooLarge();
    long rev = store.nextRevision(level);                          // before the first read
    var slots = new ArrayList<SectionSlot>();
    for (ChunkKey k : req.grid().chunksOf(box)) {
        LevelChunk chunk = serverChunkCache.getChunkNow(k.cx(), k.cz());   // ServerChunkCache.java:162
        if (chunk == null) { slots.add(new Refused(k, NOT_CAPTURED, SC_MC_0306)); continue; }
        LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(k.minY()));
        if (sec == null) { slots.add(new Refused(k, OUT_OF_BOUNDS, SC_MC_0505)); continue; }
        if (sec.hasOnlyAir()) { slots.add(new Air(k)); continue; }
        slots.add(new Present(store.intern(k, sec.getStates().copy(), rev)));   // PalettedContainer.java:265
    }
    for (SectionSlot s : slots) if (s instanceof Present p) capturePayloads(p, req, issues);
    captureEntities(box, req);                                    // one AABB query; anchor admission
    var snapshot = new LevelSnapshot(new SnapshotId(idOf(level.dimension()), rev), box, slots,
            payloads, entities, findings);                         // frozen lists
    return new CaptureResult(snapshot, leaseOf(snapshot, options.pinBudget()), issues.list());
}   // the lease goes to the scheduler; A1 never closes it here

// (2) One shape, two sources: a chest and a cow.
OccupantSnapshot chest(SnapshotId id, LatticePoint p, BlockState s, BlockEntity be, List<StackCount> slots) {
    return new OccupantSnapshot(new OccupantRef(id, NamespacedId.of("minecraft", "chest"),
            OccupantKind.BLOCK_ENTITY, p), DESCRIPTORS.blockEntity(s, be), Cuboid.of(p),
            List.of(Trait.of(OccupantTraits.ITEM_INVENTORY, new ItemInventory(slots, slots.size()))), null);
}
OccupantSnapshot cow(SnapshotId id, LatticePoint anchor, Entity e, String nbt) {
    return new OccupantSnapshot(new OccupantRef(id, NamespacedId.of("minecraft", "cow"),
            OccupantKind.ENTITY, anchor), DESCRIPTORS.entity(e), floorCeil(e.getBoundingBox()), // :3026
            List.of(Trait.of(OccupantTraits.ENTITY_NBT, new EntityNbt(nbt))), null);                   // :1737
}

// (3) INVERTED is a rotation; stairs refuse a tilt; a mirrored door cycles its hinge.
Orientation inverted = Orientation.of(Turn.NONE, Tilt.INVERTED, Flip.NONE);   // UP ↦ DOWN
Orientation turned  = Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE);  // NORTH ↦ EAST
BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState()
        .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT);
ADAPTER.remap(stairs, turned);    // Done: mirror(NONE).rotate(CLOCKWISE_90); SHAPE stays relative
ADAPTER.remap(stairs, inverted);  // Refused(SC-MC-0203): SHAPE is shape-bearing, no pitch path
BlockState door = Blocks.OAK_DOOR.defaultBlockState()
        .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
ADAPTER.remap(door, Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.LEFT_RIGHT));
// Done: FACING SOUTH, HINGE RIGHT — DoorBlock.java:276 cycles; a FACING-only rewrite would not

// (4) A wall, then a forced failure: first-touch rollback.
EditPlan wall = new EditPlan(List.of(place(Cuboid.of(0, 0, 0, 5, 3, 1), "minecraft:stone_bricks"),
        place(Cuboid.of(0, 3, 0, 5, 4, 1), "minecraft:stone_brick_slab")), "raise the casing");
try (EditTransaction tx = EXECUTOR.begin(view, siting, claims, ledger.revision(), wall.digest())) {
    tx.apply(wall.steps().get(0));                 // pre-image: states + block entities
    tx.apply(wall.steps().get(1));                 // forced failure (cell outside border)
    if (tx.state() == EditTransaction.State.FAILED) tx.rollback(); else tx.commit();
}   // success=false, SC-MC-0402 at step 1, SC-MC-0403; every first-touch cell restored

// (5) Reload and command registration.
@Override protected Draft prepare(ResourceManager r, ProfilerFiller p) {
    var docs = new LinkedHashMap<NamespacedId, Compiled>(); var issues = new ArrayList<ValidationIssue>();
    r.listResources("cumulus/structure", f -> f.getPath().endsWith(".json"))       // :17
     .forEach((file, res) -> compile(file, res, docs, issues));                    // pure
    return new Draft(new Catalog(0, Map.copyOf(docs)), List.copyOf(issues));
}
@Override protected void apply(Draft d, ResourceManager r, ProfilerFiller p) {
    state.swapCatalog(new Catalog(state.catalog().epoch() + 1, d.catalog().documents()));
}
static void register(RegisterCommandsEvent e, ServerState state) {
    e.getDispatcher().register(Commands.literal("cumulus")                        // :419
        .then(Commands.literal("validate").then(Commands.argument("id", ResourceLocationArgument.id()) // :26
            .executes(ctx -> validate(ctx.getSource(), state,
                    OccupantDescriptors.id(ResourceLocationArgument.getId(ctx, "id"))))))            // :46
        .then(Commands.literal("build").requires(src -> src.hasPermission(2))      // :390
            .then(Commands.argument("id", ResourceLocationArgument.id())
                .executes(ctx -> build(ctx.getSource(), state,
                        OccupantDescriptors.id(ResourceLocationArgument.getId(ctx, "id"))))))
        .then(Commands.literal("explain").then(Commands.argument("code", StringArgumentType.word())
            .executes(ctx -> explain(ctx.getSource(), ctx.getArgument("code", String.class))))));
}
```

## 6. Use cases

| Who | Case | What A1 provides |
| --- | --- | --- |
| Addon author | a huge-furnace mouth holding a hopper | `ITEM_INVENTORY` with slot order; the addon tests counts, A1 never interprets them |
| Addon author | a two-cell door bay | two `BLOCK` occupants, each a full snapshot; the bay's `Cuboid` is the kernel's |
| Host operator | `/cumulus build` on a broken casing | capture → `check` → `settle` → `solve` → dry run → transaction; one coded report |
| Client / tester | preview, mirrored door | line boxes per `CellMark`; `stateOf`/`remap` assertions on `FACING`/`HINGE` |

## 7. Proofs

1. **Kernel purity.** `mc` imports kernel; the kernel imports neither `mc` nor Minecraft
   (`compileKernelJava`, plus `kernelPurity` as textual backstop, `02-architecture.md` §6). A1
   adds no kernel name. *Counterexample:* an `mc` predicate in `Catalogs` is legal but never
   referenced by the kernel, so it cannot pull a Minecraft type into a kernel signature.
2. **Orientation round-trip.** `heading`/`direction` are mutually inverse six-element tables
   (`Direction.java:27-32`); for yaw/mirror, `Rotation.rotate` and `Mirror.mirror` agree with
   `Turn`/`Flip` by six-case enumeration (Y fixed, `Rotation.java:67-68`; Z negated by
   `LEFT_RIGHT`, X by `FRONT_BACK`, `Mirror.java:59-64`). So `remap(remap(s, o), o.inverse())`
   is exact for signed-permutation states; shape-bearing states are verified per property, so
   the residual is `Refused`, not wrong. *Counterexample:* same-order inverse calls give
   `R⁻¹∘M∘R∘M ≠ id`; the inverse is proved on the heading map.
3. **Capture determinism.** Cells walk in `LatticePoint` order (`05-space.md` §3.1), elements
   sort by `OccupantRef`, properties by key, tags by id, slots by index, entity candidates by
   `(type, anchor, UUID)`. No `HashMap` iteration reaches a value. *Counterexample:*
   `getEntitiesOfClass` order lets two entities at one anchor swap between runs.
4. **Rollback completeness.** Each `apply` captures the first-touch pre-image of every written
   cell and writes nothing outside `touched()`; rollback restores in inverse order, payload after
   state. *Counterexample:* place → remove → fail; per-step undo restores the intermediate state,
   first-touch the original.
5. **No main-thread violation.** World reads are `capture` and its resolver, guarded by
   `server.isSameThread()`; writes are in `apply`, guarded in `begin`. The guard is load-bearing
   because `Level.getBlockEntity` answers `null` off-thread (`Level.java:805-811`).
   *Counterexample:* `capture` from a future gets `Unavailable(OFF_THREAD)`, not a snapshot with missing chests.
6. **Unreadable never becomes air.** `occupant` is non-null only when `readStatus == READABLE`,
   assigned only to a read cell or one covered by an admitted occupant. `DENIED`,
   `NOT_CAPTURED`, `OUT_OF_BOUNDS` return `null` ⇒ `Undecided(UNREADABLE)`, never `Failed`.
   *Counterexample:* a chunk unloading between gate and read leaves `DENIED`.
7. **One conflict predicate (`22` P2).** `SavedClaims.settle` asks `ClaimMatrix.conflicts` and has
   no rule of its own: every cell pair is judged by the same function the kernel planner uses, the
   delete-and-insert happens in one CAS on `(world, owner, siting)`, and a moved revision returns
   `RevisionMoved` instead of inventing a conflict. *Counterexample:* a second `EXCLUSIVE` owner on
   one cell is refused by the matrix; a `SHARED`/`SHARED` pair is not refused by anything.
8. **One lease holder.** `capture` returns the lease to the scheduler and never closes it; a result
   holds a child handle; `Formed` and `Admitted` are both `AutoCloseable`. *Counterexample:* a
   submit superseded before dispatch — the scheduler's `finally` closes the lease and
   `metrics().openHandles` returns to its baseline.

## 8. Dependencies and open questions

| Depends on | For | Risk |
| --- | --- | --- |
| U2 `WorldView`/`WorldCapture`/`OccupantResolver` | every read | no payload slot; A1 uses a trait |
| U2 `ReadStatus` precedence | status table | coverage vs `DENIED` |
| U4 `ClaimLedger`/`ClaimRow`/`ClaimVerdict`/`SettleRequest` | persistence | one predicate, one CAS |
| U6 `DataFrontend`/`DataBridge`/`DocumentSchema` | reload | JSON has no spans |
| U9 `FindingCode`/`Finding` | A1 codes | `SC-MC-*` must be registered |
| U10 `EditExecutor`/`EditTransaction`/`Edit` | writes | `SetTrait`/entity edits unrealisable |
| U11 `StructureKernel`/`Catalogs` | facade | one kernel per server |

| Id | Question | Maps to |
| --- | --- | --- |
| Q-1 | The per-tick window diff costs the entities present per window; is that acceptable at a few hundred windows, or does the host need a shared spatial index? | `22` §16 Q2 |
| Q-2 | A second `RevisionMoved` after the one in-call retry: a retry token or a hard `Undecided`? | `22` §16 Q4 |

`SC-MC-*` registry as this file uses it: `0101` missing blueprint, `0102` reload failed, `0201`
unorientable, `0203` tilt unsupported, `0301` wrong thread, `0302` payload precondition failed
(missing required trait), `0304` unsupported edit, `0305` outside border, `0306` chunk unloaded,
`0307` blast radius, `0401` permission denied, `0402` step failed, `0403` rolled back, `0404`
discard limit reached (`Undecided(DIRTY_UNSTABLE)`, `22` §9.4), `0405` rollback restore failed,
`0501` capture over cap, `0502` pin refused, `0505` section outside build height
(`Refused(OUT_OF_BOUNDS)`). `0501` is used for nothing but the cap, and no lease
event uses it.

## 9. Falsification

1. **An entity straddling chunks.** A cow at `x = 15.5` with the far chunk unloaded: one `ENTITY`
   occupant, 2×1 footprint, anchor in the loaded chunk, both covered cells `READABLE`, other
   far-chunk cells `DENIED`. Fails on two occupants, air for the far cell, or a clipped footprint.
2. **Block-entity NBT changed between capture and apply.** Capture a chest with 27 diamonds,
   remove them before `begin`: rollback restores the pre-apply contents (empty), not the captured
   27. Fails if the executor restores from the snapshot and duplicates items.
3. **A rotated door.** `FACING=NORTH`, `HINGE=LEFT`, both halves, `CLOCKWISE` ⇒ `FACING=EAST`,
   `HINGE=LEFT` (`DoorBlock.java:268`); `LEFT_RIGHT` mirror ⇒ `FACING=SOUTH`, `HINGE=RIGHT`
   (`:276`); two mirrors restore the original. Fails if a generic `FACING` rewrite leaves `HINGE`
   uncycled or the halves diverge.
4. **A denied chunk read.** A box overlapping an unloaded chunk: `DENIED`, `occupant == null`,
   `Spend.cells` excludes those cells, the match is `Undecided(UNREADABLE)`. Fails on air, a
   charged read, or `Failed`.
5. **A silent no-op rotation.** A mod block inheriting the default `rotate`
   (`BlockBehaviour.java:282`) with a `Direction` property: `Refused(SC-MC-0201)`. Fails if the
   unchanged state is accepted.
6. **Two owners, one cell, one predicate.** A settles `EXCLUSIVE`; B's `settle` over the same cell
   returns `ClaimConflict` from `ClaimMatrix`, the ledger file is byte-identical, and the result is
   `Failed(SC-CLM-0002)` carrying A's rows. Fails if the adapter refuses on its own rule, if B's
   rows are written, or if the conflict is reported as `RevisionMoved`.
7. **A cow leaves the pen.** No block changes and no `setPos` hook: the window's live
   `cell → EntityKey` set loses one entry, the window's units go dirty, and the next `check` re-runs
   them. Fails if a `Formed` still comes from the old snapshot, or if the diff walks the window's
   cells instead of its entities.
