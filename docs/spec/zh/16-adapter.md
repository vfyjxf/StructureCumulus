# 16 — Minecraft 适配器（A1）

> A1 · `dev.vfyjxf.cumulus.mc` · `src/main/java` · 服务于 R-01/02/04/05/11/12 · 承载
> I-1/2/4/5/10。基线 1.21.1 NeoForge 21.1.x（`neoforge-21.1.219.jar`）。先前工作：
> ArchitectCumulus `42`–`45`；以本文件的术语表为准。

## 1. 范围（Scope）

适配器把世界值（world values）转换为内核值（kernel values），并把内核值转换回世界写入（world writes）；它从不判定某个值的含义。只有一个包，没有 `client` 子包：两个客户端类置于 `FMLLoader.getDist().isClient()` 之后（`FMLLoader.java:160`）。`mc` 导入 kernel；kernel 既不导入 `mc`，也不导入 Minecraft（`02-architecture.md` §2，I-1）。

| A1 实现 | A1 不得 |
| --- | --- |
| `WorldCapture`、`WorldView`、`NestedView`、`OccupantResolver` | 让内核知道方块是什么；在内核签名中暴露 `BlockState`/`Level`/`Entity` |
| `ClaimLedger`（基于 `SavedData`） | 隐藏 `DENIED`/`NOT_CAPTURED`；从失败的读取推断出空气；为每个格生成一个 ref |
| `EditExecutor` + `EditTransaction` | 写到 `plan.touched()` 之外；不做试运行（dry run）就应用；从匹配器或派生器写入 |
| `DataBridge<JsonElement>`、`DataFrontend` | 让 `com.mojang.serialization` 进入内核签名 |
| overlay、`FindingSink`、`/cumulus`、GameTests | 在客户端捕获、匹配或写入；在服务端线程之外读取 level |

## 2. 术语（Vocabulary）

内核名称在此处一次性链接到 `01-glossary.md`。

| A1 名称 | 角色 |
| --- | --- |
| [`LevelCapture`](01-glossary.md) | `WorldCapture`：A1 中唯一把 `Level` 捕获为内核值的成员；服务端线程；一个世界 |
| [`LevelSnapshot`](01-glossary.md) | `WorldView`：不可变的格，不含 `Level`；`NestedLevelSnapshot` 是一个 `NestedView` |
| [`WorldOccupants`](01-glossary.md) | `OccupantResolver<Raw>`：方块/方块实体/实体/extent → `OccupantSnapshot` |
| [`OccupantDescriptors`](01-glossary.md) | 辅助：state/body/entity → `OccupantDescriptor`，以及精确逆映射 |
| [`OccupantTraits`](01-glossary.md) | 辅助：不可变的物品栏、能量、流体、实体 NBT 值 |
| [`OrientationMapping`](01-glossary.md) | 辅助：`Heading`/`Turn`/`Tilt`/`Flip` ↔ `Direction`/`Rotation`/`Mirror` |
| [`DatapackBlueprints`](01-glossary.md) | 重载监听器：compact/canonical JSON → `BlueprintDocument` → `Blueprint`，按 epoch 缓存 |
| [`SavedClaims`](01-glossary.md) | `ClaimLedger` + `SavedData`：一个世界一个存储（一个 `ServerLevel`），带修订号，失败即关闭 |
| [`WorldEdits`](01-glossary.md) | `EditExecutor`：事务式写入，首次触碰回滚 |
| [`ClientOverlay`](01-glossary.md) | 客户端渲染器：`OverlayModel` → 线框盒，不依赖内核服务 |
| `FindingOutput` | `FindingSink`：日志、聊天或 JSON；从不判定，从不重排 |
| [`CumulusCommand`](01-glossary.md) | 命令树：`capture`、`import`、`lint`、`validate`、`preview`、`build`、`explain` |
| `ServerState`、`JsonFrontend`、`JsonBridge` | A1 内部：按服务器的目录/门面/账本；两个 Gson 类 |

## 3. API 设计（API design）

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

`capture` 对“无法服务”（cannot serve，如线程外或拒绝）返回 `Unavailable`，对超限返回 `TooLarge`；它绝不返回 `null`，也绝不返回不带租约的快照；每一次损失都会报告给 `IssueSink`。`occupant` 为 `null` 当且仅当 `readStatus != READABLE`；`stateOf` 是精确逆映射，否则为 `null`；`remap` 以值的形式返回 `Refused`；`settle` 在单次 CAS 中替换一个 `(world, owner, siting)` 的全部行，并返回内核的 `ClaimVerdict` —— `Settled`、带在位行的 `ClaimConflict`，或 `RevisionMoved`；`begin` 返回 `REFUSED` 时不写入任何内容。

## 4. 语义（Semantics）

### 4.1 捕获：区段、载荷、线程（Capture: sections, payloads, thread）

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

该租约回答门控所读取的两个事实：`changeCount()` 是受监视区域的标记计数器，捕获时为零；而一旦世界的代际移动或句柄被关闭，`isValid()` 为 false。`16` 把代际保存在世界自身的簿记里；内核从不读取它。

| 工作项，7×6×7 | 开销 |
| --- | --- |
| 区块门控 | ≤4 列；`ServerChunkCache.getChunkNow`（`ServerChunkCache.java:162`）仅在主线程可用且不加载 |
| 方块状态 | 每个 16³ 区段一次 `PalettedContainer.copy()`（`PalettedContainer.java:265`），而不是 294 次格读取 |
| 方块实体 | 只取 `getBlockEntitiesPos()` 的位置，而不是每个格 |
| 实体 | 一次 `getEntitiesOfClass`（`EntityGetter.java:29`），开销取决于相交的区段 |
| 实体 NBT | `e × Entity.saveWithoutId`（`Entity.java:1725`），仅在需要时 |

上限：`maxCells`、`maxSections`、`maxBytes`、`maxEntities`、`maxPayloads`；超出上限即
`CAPTURE_LIMIT`/`NOT_CAPTURED`，绝不把部分快照当作完整快照。`MAX_CELLS = 1 << 20` 用于拒绝病态
尺寸的盒体。

**线程。** `ServerChunkCache.getChunkNow` 与 `LevelChunk.getBlockEntity` 仅在主线程可用
（`ServerChunkCache.java:162`、`Level.java:805-811`）。`capture` 检查 `server.isSameThread()`，
否则返回 `Unavailable(OFF_THREAD)`。捕获是一次调用，绝不是 tick 流水线；区段复制开销很低，纯空气区段被跳过。巨大的
结构要么付出延迟，要么被拒绝；它绝不会看到混合（blend）的快照。

**问题与租约（Issues and lease）。** `capture(request, issues)` 绝不因世界状况抛出异常，也绝不
返回 `null`：不可恢复的读取是快照内的 `Refused(NOT_CAPTURED)` 加上 sink 中的一个问题，超出上限是
`TooLarge`。`CaptureResult` 携带快照及其租约；租约交给调度器，由调度器在每条路径上负责释放 ——
一个结果的子句柄、一次被合并掉的提交、一次延迟、一次取消以及一次停止（§4.10）。A1 绝不关闭它
仅仅返回过的租约。

**存储（可选）。** 默认由 `LevelSnapshot` 自己持有区段副本。只有在 profiling 之后才可启用每世界
一个 `SectionStore`；它按 `(SectionKey, revision)` 持有当前的 `SectionSnapshot`。当
`contentEquals` 成立时，`intern` 返回现存的条目，因此空操作刷新保持对象与修订号不变。该存储是
弱缓存；`LevelSnapshot` 持有强引用。已密封的快照绝不通过该存储惰性解析区段。

### 4.2 状态、不可变性、修订号（Statuses, immutability, revision）

| 情形 | `readStatus` | `occupant` |
| --- | --- | --- |
| 在 `bounds()` 之外 | `OUT_OF_BOUNDS` | `null` |
| 区块缺失、不是 `FULL`，或未加载 | `NOT_CAPTURED` | `null` |
| 区段在建筑高度之外 | `OUT_OF_BOUNDS` | `null` |
| 区段已读取、为空（`Air`） | `READABLE` | `OccupantSnapshot.air(id, cell)` |
| 区段已读取、该处有方块 | `READABLE` | 该方块的快照 |
| 载荷上限 / 嵌套深度 | `NOT_CAPTURED` / `DENIED` | `null` |
| 被某个已接纳的多格占用体覆盖 | `READABLE` | 该占用体 |

`LevelSnapshot` 实现 `WorldView`；`NestedLevelSnapshot` 在同一批字节上实现 `NestedView`。它持有
`SnapshotId`、`Cuboid`、`SectionSlot[]`、载荷格、实体索引与诊断项，全部不可修改；`Level`、
`ChunkAccess`、`BlockEntity` 或 `Entity` 都不可触达。`SectionSlot` 是 `Air` |
`Present(SectionSnapshot)` | `Refused(status, code)`。

`SnapshotId.revision` 是按 `ServerLevel` 的 `AtomicLong`，在首次读取之前预留，从不持久化。内容
相等的刷新保持修订号不变；真正的复制会递增它。`SnapshotId.world` 是内核的 `World`，由
`Level.dimension()`（`Level.java:1179`）的 `ResourceKey.location()`（`ResourceKey.java:82`）构建
—— 这是 Minecraft 侧的 level 变成内核的世界的唯一一处。

### 4.3 描述符与特征（Descriptor and traits）

| 来源 | `type` | `properties` / 标签 | 特征 |
| --- | --- | --- | --- |
| 方块 | `BLOCK.getKey`（`BuiltInRegistries.java:140`） | 每个状态属性，`Property.getName()`（`:52`）= `Property.getName(value)`（`:65`），已排序；方块标签（`Holder.java:30`） | 无 |
| 方块实体 | `BLOCK_ENTITY_TYPE.getKey`（`:147`，`BlockEntity.java:257`） | 该方块状态的属性；方块标签 | `ITEM_INVENTORY`、`ENERGY`、`FLUID` |
| 实体 | `ENTITY_TYPE.getKey`（`:142`，`Entity.java:308`） | 无；实体类型标签 | `ENTITY_NBT` |

立即读取：type、properties、标签、占地范围（footprint）。当 `policy.requiredTraits()` 点名该键时也立即读取：物品栏/能量/流体（`Capabilities.ItemHandler.BLOCK` `:37`、`EnergyStorage.BLOCK` `:21`、`FluidHandler.BLOCK` `:29`）、实体 NBT。从不读取：活体对象、UUID、依赖位置的 NBT、组件树、战利品表、未解析的能力。

`ENTITY_NBT` 是 `saveWithoutId` 的规范 SNBT，去掉 `Pos`/`Motion`/`Rotation`/`UUID`/`FallDistance`/`PortalCooldown`，因此同一头牛捕获两次会比较为相等 —— 这是 A1 对术语表“可选载荷”（optional payload）的应答（`06-occupants.md` §3.3 没有对应槽位）。抛异常的能力会让该特征缺席 → `UNREADABLE`，绝不会变成清零的物品栏；物品栏保留 `(item id, count)`。

### 4.4 朝向变换（Orientation）

两套顺序不同；不得比较序数（ordinal）（`Direction.java:27-32`）。`Heading`/`Direction`：
NORTH/SOUTH/WEST/EAST/UP/DOWN ↔ `NORTH`/`SOUTH`/`WEST`/`EAST`/`UP`/`DOWN`。
`Turn`/`Rotation`：NONE/CLOCKWISE/HALF/COUNTERCLOCKWISE ↔
`NONE`/`CLOCKWISE_90`/`CLOCKWISE_180`/`COUNTERCLOCKWISE_90`。`Flip`/`Mirror`：
NONE/LEFT_RIGHT/FRONT_BACK ↔ `NONE`/`LEFT_RIGHT`/`FRONT_BACK`。

`Rotation.rotate(Direction)` 固定 Y，并偏转其余分量（`Rotation.java:66-79`）；`Mirror.mirror(Direction)` 对 `LEFT_RIGHT` 取反 Z，对 `FRONT_BACK` 取反 X（`Mirror.java:59-64`），与 `Flip` 一致（`05-space.md` §4.4）。任何地方都不出现控制器实体。

`remap` 只读取 `turn()`、`tilt()` 与 `isMirror()`：`Flip.LEFT_RIGHT` 不是独立的群元素（它归一化为 `Turn.HALF ∘ Flip.FRONT_BACK`），所以每个非正常朝向变换（improper orientation）都实现为 `Mirror.FRONT_BACK` 后接规范偏转（`05-space.md` §3.4）。`mirror(Flip)` 与 `flip(Mirror)` 用于解析与诊断，不用于 `remap`。

1. **偏转 + 镜像**（`tilt() == LEVEL`）：`state.mirror(m).rotate(r)`，先镜像，这是 vanilla 的顺序（`StructureTemplate.java:254`）。方块覆写完成实际工作：门旋转 `FACING` 并循环 `HINGE`（`DoorBlock.java:268`、`:276`）；楼梯旋转 `FACING`、镜像 `SHAPE`（`StairBlock.java:201`、`:209`）。
2. **任何倾斜**：vanilla 没有俯仰 API。按 `o.apply(heading)` 重写 `DirectionProperty`/`Axis`；拒绝带有 `SHAPE`、`HINGE`、`WALL`、`RAIL_SHAPE` 或 `ORIENTATION` 的状态 → `Refused(SC-MC-0203)`。倾斜在位置上对全部 48 种朝向变换精确，在状态上仅当方向语义是有符号置换（signed permutation）时精确。

**锚点朝向**按属性*实例*探测：`FACING`（`BlockStateProperties.java:50`）、`HORIZONTAL_FACING`（`:54`）、`ORIENTATION` 的 front（`:56`）。两种 facing 都拼作 `facing`，所以 `hasProperty` 是唯一安全的判定方式。`AXIS`（`:43`）不是朝向来源（柱子会声称一个虚假的北）；`null` 让该角色无法解析，并且没有回退朝向 —— `OrientationPolicy.select` 返回“没有可接受的朝向变换”（no admissible orientation），绝不是 identity（`09` §4.5）。走完任一分支后，每个 `Direction` 属性都与 `o.apply(headingOf(old))` 比较；不匹配即 `Refused`，绝不是 `BlockBehaviour.rotate`（`BlockBehaviour.java:282`）或 `mirror`（`:289`）那种静默空操作。

### 4.5 重载：JSON → 目录（Reload: JSON → catalog）

| 步骤 | 规则 |
| --- | --- |
| `prepare`（线程外，off-thread） | `listResources("cumulus/structure", p -> p.getPath().endsWith(".json"))`（`ResourceManager.java:17`） |
| 解析 | `JsonFrontend` → `DataView`；Gson 没有偏移量，所以值不带位置跨度（span-less）（`SC-DOC-0403` 只出现一次） |
| 语法 | `DocumentSchema.current().read(..., DocumentProfile.AUTO, ...)`；compact 或 canonical；`null` → `SC-MC-0102` 外加若干校验问题 |
| 校验/组装 | `DocumentValidator`、`SegmentAssembler`；两者都是纯函数；`Compiled(id, digest, bp, issues)` |
| `apply`（服务端线程） | 一次 `AtomicReference<Catalog>` 交换，`epoch + 1`；读者看到旧值或新值 |
| 报告 | 校验问题保留给 `/cumulus validate`；损坏的文档**缺席**，绝不静默地用它之前的版本替代 |

`SimplePreparableReloadListener` 在后台执行器上运行 `prepare`，在游戏执行器上运行 `apply`（`SimplePreparableReloadListener.java:9-20`）；`AddReloadListenerEvent.addListener`（`AddReloadListenerEvent.java:44`）提供注册表访问。目录按服务器（`ServerAboutToStartEvent.java:19` / `ServerStoppingEvent.java:15`），账本按 level，叠加层按客户端。

### 4.6 声明：持久化、带修订号、失败即关闭（Claims: persisted, revisioned, fail-closed）

每个世界一个 `SavedData` —— 也就是每个 `ServerLevel` —— 经 `ServerLevel.getDataStorage()`（`ServerLevel.java:1370`）与 `DimensionDataStorage.computeIfAbsent`（`DimensionDataStorage.java:43`），文件 `cumulus_claims.dat` 位于那个世界的 `data/` 下：`DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))`（`DimensionType.java:152`、`MinecraftServer.java:1954`、`LevelResource.java:15`）+ `data`。

| 关注点 | 规则 |
| --- | --- |
| 行（row） | `ClaimRow(world, cell, sharing, owner, siting, bay)`；`(world, owner, siting)` 既是替换作用域，也是自冲突排除条件 |
| 读取 | `read(Cuboid)` 返回 `ClaimView`；`available() == false` = 无法应答，可用的空视图 = 无任何声明（`08-claims.md` §4.7） |
| 修订号 | 文件中的一个 `long`，每次写入 `+1`，`setDirty()`（`SavedData.java:18`）；即 CAS 令牌 |
| 损坏与缺席 | `DimensionDataStorage.get` 对两者都返回 `null`（`:55`、`:73-77`），而 `computeIfAbsent` 会创建一个空存储。A1 检查文件是否存在：缺席 → 创建；存在但不可读 → `unavailable`，每次读取都不可用，不做结算 |
| 结算 | 在 `(world, owner, siting)` 上的一次 CAS：对传入行调用 `ClaimMatrix.conflicts`，与作用域之外的每一行比对，然后删除全部作用域行并用一次 `setDirty` 插入新行 |
| 冲突 | `ClaimMatrix` 报告至少一对冲突 → `ClaimConflict(rows, findings)`，**不写入任何内容**；修订号移动 → `RevisionMoved(expected, actual)`，绝不是冲突 |

`ClaimMatrix` 是唯一的冲突谓词：内核计划器与 `SavedClaims` 调用同一个函数，A1 不自己追加“外来 `EXCLUSIVE`”检查（`22` §10.1）。`RevisionMoved` 是账本在说世界已经移动，而不是调用方做错了，因此它绝不作为冲突上报。

结算是**由调度器串接的服务端线程入口**，不是匹配器做的事：`check` 返回计划中的声明与它读到的冻结修订，且不写入任何内容，而想要注册该声明的调用方用该计划调用一次 `settle`。只有 `Settled` 判定才产生 `Formed`。匹配器从不触碰 `SavedData`；`ClaimConflict` 变成带在位行的 `Failed(SC-CLM-0002)`，而 `RevisionMoved` 在调用内重试一次，之后是 `Undecided`。

### 4.7 编辑：事务、回滚、影响范围（Edits: transaction, rollback, blast radius）

| 阶段 | 规则 |
| --- | --- |
| 门控 | 以下情形为 `REFUSED`：不在服务端线程（`SC-MC-0301`、`BlockableEventLoop.java:35`）、视图不是该 level 的视图、某格在边界之外（`SC-MC-0305`、`WorldBorder.java:32`）、某格未加载（`Level.java:829`）、门控拒绝（`SC-MC-0401`），或 `plan.touched().volume() > maxCells`（`SC-MC-0307`） |
| 前像（pre-image） | 对每个被写入格的首次触碰：`getBlockState`（`Level.java:406`）+ `BlockEntity.saveWithId`（`BlockEntity.java:106`） |
| 写入 | `level.setBlock(pos, state, flags)`（`Level.java:228`），带 `UPDATE_ALL`（`Block.java:85`）+ `UPDATE_KNOWN_SHAPE`（`:81`）；`false` = 步骤失败 |
| 载荷 | 放置的方块实体以空状态开始（`OccupantDescriptor` 没有 NBT）；`SetTrait` 与实体编辑被拒绝（`SC-MC-0304`） |
| 恢复 | 先设置方块，再 `level.getBlockEntity(pos)`（IMMEDIATE，`Level.java:805-811`）并 `loadWithComponents(tag, regs)`（`BlockEntity.java:82`）—— vanilla 自己的顺序（`StructureTemplate.java:276`）；调用 `markPayload(pos)`，因为 `loadWithComponents` 绕过 `blockEntityChanged` |
| 回滚 | 逆序，先状态后载荷；恢复失败即 `SC-MC-0405`；`close()` 只回滚 `OPEN`/`FAILED` |
| 影响范围 | 恰为 `plan.touched()`；`SC-MC-0307` 拒绝，`0402` 步骤失败，`0403` 已回滚 |

`UPDATE_KNOWN_SHAPE` 抑制形状级联（shape cascade），但不抑制重力或流体流动；对沙子或水之上的计划可能改变未命名的格。

### 4.8 叠加层：数据到像素（Overlay: data to pixels）

| `CellMark` | 盒体 | RGBA | 附加 |
| --- | --- | --- | --- |
| `EXPECTED` | 完整，内缩 0.02 | 0.20, 0.80, 1.00, 0.35 | — |
| `PRESENT` | 完整 | 0.20, 1.00, 0.40, 0.30 | — |
| `MISSING` | 完整 | 1.00, 0.25, 0.20, 0.55 | — |
| `EXTRA` | 完整 | 1.00, 0.60, 0.10, 0.45 | — |
| `WRONG` | 完整 | 1.00, 0.20, 0.80, 0.50 | — |
| `BLOCKED` | 完整 | 0.60, 0.00, 0.00, 0.60 | 顶面画 X |
| `UNKNOWN` | 线框 | 0.60, 0.60, 0.60, 0.15 | 不填充 |

在 `RenderLevelStageEvent` 的 `AFTER_TRANSLUCENT_BLOCKS` 阶段绘制（`RenderLevelStageEvent.java:37`、`:64`）并使用其 `PoseStack`（`:78`）；一个 `RenderType.lines()` 缓冲，每个格一次 `LevelRenderer.renderLineBox`（`LevelRenderer.java:2231`），按标记分组。格是 LOCAL 的；把每个 `LatticeKey` 经 `siting.toWorld` 映射，并跳过未加载的客户端区块。`Marker` 变成一个标签，或一个限速的粒子（`Level.addParticle`，`Level.java:530`）。渲染器只导入内核值类型，从不捕获/匹配/求解/写入，且只在客户端 dist 检查下构造（`FMLLoader.java:160`）。载荷上限 8192 格。

### 4.9 命令与 GameTest（Commands and GameTest）

| 命令 | 侧 | 行为 |
| --- | --- | --- |
| `/cumulus capture <id> <from> <to>` | 服务端 | `CaptureTool.capture(request, issueSink)` → compact JSON `DataView` 或 `null`；写入数据包文件或复制到聊天 |
| `/cumulus import <file>` | 服务端 | `.schem`/`.nbt` → compact JSON；不支持的条目变成诊断项 |
| `/cumulus lint <file>` | 服务端，无世界 | JSON profile 解析 + `DocumentValidator`；行列问题 |
| `/cumulus validate <id>` | 服务端，无世界 | 重新运行 `DocumentValidator`，打印校验问题与人类可读的诊断项 |
| `/cumulus preview <id> [pos]` | 服务端计算，客户端绘制 | 捕获 footprint + 1，`PROBE` → `FORM`，`solve(PREVIEW)`，发送 `OverlayPayload` |
| `/cumulus build <id> [pos]` | 服务端 | 权限 2（`CommandSourceStack.java:390`），`check` → `settle`，`solve` → `Repair`，`PlanValidator`，`applyIfMachineApplicable` |
| `/cumulus explain <code>` | 任一侧 | `FindingCode.parse` + `ExplainBook`；不涉及世界 |

命令树挂在 `RegisterCommandsEvent.getDispatcher()`（`RegisterCommandsEvent.java:36`）上，经由 `Commands.literal("cumulus")`（`Commands.java:419`）；反馈经由 `sendSuccess`（`CommandSourceStack.java:480`）。GameTests（`@GameTest`，`GameTest.java:10`；`setBlock` `:345`、`assertBlockState` `:415`、`succeed` `:777`）覆盖 **摆放**（移除一个格 ⇒ 下一次尝试是 `Dropped(SNAPSHOT_CHANGED)`，绝不是在旧捕获上的 `Formed`）、**旋转**（门与楼梯上的四种偏航）、**共享声明**（两个 `SHARED` 结算；第三个 `EXCLUSIVE` 为 `SC-CLM-0002`，账本逐字节不变）、**回滚**（在第 3 步强制失败，恢复每个状态与方块实体 NBT）。

### 4.10 调度器、脏状态、快照核对（Scheduler, dirty state, snapshot check）

`ServerState` 拥有一个 `MatchScheduler`。`submit` 在服务端线程运行；`CheckKey` 是
`(world, owner)`，而 `Place` 是宿主的目标，在派发前经门面的 `siting(blueprint, origin)` 解析，
因此内核请求携带的是普通 `Siting`。每个键至多一个任务在飞；较新的提交递增一个 ticket 并合并，
绝不启动第二个任务。`INTERACTIVE` 持有一个保留许可，`AUTO` 得到 `max(0, threads - 1)`；在只有
一个 worker 的宿主上 `AUTO` 被禁用，`/cumulus workers` 会说明这一点。无许可的 `AUTO` 是
`DEFERRED`，由调度器自己的 `tick()` 重新探测。`AUTO` 只跑
`PROBE`；通过后另投 `FORM`，因此 AUTO 任务保持短小、可合并。调度器还负责看门狗；它绝不从脏泵
启动一次检查。

`LevelChunk.setBlockState` 把其 16³ 区段的 `SectionKey` 标脏。`Level.blockEntityChanged` 仅在某个
监视读取该载荷时才标记载荷位置。实体的加入/离开标记其占地范围以及包含它的每个窗口；移动由
**服务端线程上的逐 tick 窗口差分**发现 —— 把每个窗口的实时 `cell → EntityKey(uuid, type, digest)`
集合与其捕获时的集合比对，开销受该窗口的实体数量约束 —— 而不是由方块挂钩发现，也不是由
`setPos` 挂钩发现。区块卸载把区段标为 `UNKNOWN`。`DirtyIndex` 维护四张映射，第四张是
`window → Set<InstanceRef>`；标记是 O(1)，且任何标记都不遍历体积。

派发时，服务端线程冻结声明视图（覆盖锚点格与结算格并集的不变行 + 修订号），并刷新过期的区段、
载荷与实体窗口（一次性；超出上限 = `DEFERRED`，槽位保持脏）。只有当其租约应答 `CURRENT` 时，
结果才可发布或结算：`lease.changeCount() == 0` 且 `lease.isValid()`（`11` §4.6）。计数移动是
`Dropped(SNAPSHOT_CHANGED)`；世界被替换或句柄被关闭是 `Dropped(CAPTURE_INVALID)`。发起注册的
调用方随后在服务端线程上调用 `settle`，而 `SavedClaims.settle` 以冻结的修订号运行：
`ClaimConflict` 是 `Failed(SC-CLM-0002)`，`RevisionMoved` 重试一次，随后
`Undecided(LEDGER_MOVED)`。`onTerminal` 对每种结局都触发；`onSettled` 是仅成功时的便捷回调。

`InstanceRecord` 拥有该捕获。`Formed` 与 `Admitted` 持有指向它的子句柄，`derive` 读取帧而不是
租约。从不结算的调用方自己关闭 `Admitted`；`settle` 在每条路径上关闭它，并交给 `Formed` 它自己
的子句柄，由调用方在 `derive` 之后关闭。`retain(PinBudget)` 可以以 `SC-MC-0502` 拒绝，此时调用
方保留实例自身的引用。

## 5. 参考代码（Reference sketch）

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

## 6. 用例（Use cases）

| 角色 | 用例 | A1 提供什么 |
| --- | --- | --- |
| 附加组件作者（Addon author） | 一个含着漏斗的巨大熔炉口 | 带槽位顺序的 `ITEM_INVENTORY`；附加组件测试数量，A1 从不解释它们 |
| 附加组件作者 | 一个两格的门舱位 | 两个 `BLOCK` 占用体，各自是完整快照；舱位的 `Cuboid` 由内核持有 |
| 宿主运维者（Host operator） | 对损坏的围护（casing）执行 `/cumulus build` | 捕获 → `check` → `settle` → `solve` → 试运行 → 事务；一份带码的报告 |
| 客户端 / 测试者 | 预览、镜像门 | 每个 `CellMark` 的线框盒；对 `FACING`/`HINGE` 的 `stateOf`/`remap` 断言 |

## 7. 证明（Proofs）

1. **内核纯净性。** `mc` 导入 kernel；kernel 既不导入 `mc` 也不导入 Minecraft（`compileKernelJava`，外加作为文本兜底的 `kernelPurity`，`02-architecture.md` §6）。A1 不新增任何内核名称。*反例：* `Catalogs` 中的 `mc` 谓词（predicate）合法，但内核从不引用它，因此它无法把 Minecraft 类型拉进内核签名。
2. **朝向变换往返。** `heading`/`direction` 是互为逆的六元素表（`Direction.java:27-32`）；对于偏转/镜像，`Rotation.rotate` 与 `Mirror.mirror` 通过六种情形的枚举与 `Turn`/`Flip` 一致（Y 固定，`Rotation.java:67-68`；`LEFT_RIGHT` 取反 Z、`FRONT_BACK` 取反 X，`Mirror.java:59-64`）。因此对带符号置换的状态，`remap(remap(s, o), o.inverse())` 是精确的；带形状的状态逐属性验证，所以残余是 `Refused`，而不是错误。*反例：* 以相同顺序调用逆操作会得到 `R⁻¹∘M∘R∘M ≠ id`；逆性是在朝向映射上证明的。
3. **捕获确定性。** 格按 `LatticePoint` 顺序遍历（`05-space.md` §3.1），元素按 `OccupantRef` 排序，属性按键、标签按 id、槽位按索引、实体候选按 `(type, anchor, UUID)` 排序。没有任何 `HashMap` 迭代会影响取值。*反例：* `getEntitiesOfClass` 的顺序会让同一锚点上的两个实体在两次运行之间互换。
4. **回滚完备性。** 每次 `apply` 都捕获每个被写入格的首次触碰前像，且不写入 `touched()` 之外的任何内容；回滚按逆序恢复，载荷在状态之后。*反例：* 放置 → 移除 → 失败；按步撤销会恢复到中间状态，首次触碰恢复则回到原始状态。
5. **无主线程违规。** 世界读取是 `capture` 及其解析器，由 `server.isSameThread()` 守护；写入在 `apply` 中，由 `begin` 守护。该守护是承重的（load-bearing），因为 `Level.getBlockEntity` 在线程外应答 `null`（`Level.java:805-811`）。*反例：* 从 future 调用 `capture` 得到 `Unavailable(OFF_THREAD)`，而不是缺失箱子的快照。
6. **不可读绝不变成空气。** `occupant` 仅当 `readStatus == READABLE` 时非空，且只赋给已读取的格，或被已接纳占用体覆盖的格。`DENIED`、`NOT_CAPTURED`、`OUT_OF_BOUNDS` 返回 `null` ⇒ `Undecided(UNREADABLE)`，绝不是 `Failed`。*反例：* 区块在门控与读取之间卸载，则保持 `DENIED`。
7. **单一冲突谓词（`22` P2）。** `SavedClaims.settle` 询问 `ClaimMatrix.conflicts`，没有自己的规则：每一对格都由内核计划器所用的同一函数判定，删除与插入在 `(world, owner, siting)` 上的一次 CAS 中完成，修订号移动时返回 `RevisionMoved`，而不是编造一个冲突。*反例：* 一个格上的第二个 `EXCLUSIVE` 所有者被矩阵拒绝；`SHARED`/`SHARED` 对不被任何东西拒绝。
8. **单一租约持有者。** `capture` 把租约交给调度器且绝不关闭它；结果持有子句柄；`Formed` 与 `Admitted` 都是 `AutoCloseable`。*反例：* 一次在派发前被取代的提交 —— 调度器的 `finally` 关闭租约，`metrics().openHandles` 回到基线。

## 8. 依赖与待决问题（Dependencies and open questions）

| 依赖 | 用于 | 风险 |
| --- | --- | --- |
| U2 `WorldView`/`WorldCapture`/`OccupantResolver` | 每次读取 | 没有载荷槽位；A1 改用特征 |
| U2 `ReadStatus` 优先级 | 状态表 | 覆盖与 `DENIED` 的关系 |
| U4 `ClaimLedger`/`ClaimRow`/`ClaimVerdict`/`SettleRequest` | 持久化 | 一个谓词，一次 CAS |
| U6 `DataFrontend`/`DataBridge`/`DocumentSchema` | 重载 | JSON 没有位置跨度 |
| U9 `FindingCode`/`Finding` | A1 诊断码 | `SC-MC-*` 必须被注册 |
| U10 `EditExecutor`/`EditTransaction`/`Edit` | 写入 | `SetTrait`/实体编辑无法实现 |
| U11 `StructureKernel`/`Catalogs` | 门面 | 每个服务器一个内核 |

| Id | 问题 | 对应 |
| --- | --- | --- |
| Q-1 | 逐 tick 窗口差分的开销等于每个窗口当前的实体数；在数百个窗口的规模下是否可以接受，还是宿主需要共享空间索引？ | `22` §16 Q2 |
| Q-2 | 调用内那一次重试之后的第二次 `RevisionMoved`：给一个重试令牌，还是直接 `Undecided`？ | `22` §16 Q4 |

本文件使用的 `SC-MC-*` 注册表：`0101` 蓝图缺失，`0102` 重载失败，`0201` 无法定向，`0203` 不支持倾斜，`0301` 线程错误，`0302` 载荷前置条件失败（缺少必需特征），`0304` 不支持的编辑，`0305` 在边界之外，`0306` 区块未加载，`0307` 影响范围，`0401` 权限被拒，`0402` 步骤失败，`0403` 已回滚，`0404` 达到丢弃上限（`Undecided(DIRTY_UNSTABLE)`，`22` §9.4），`0405` 回滚恢复失败，`0501` 捕获超限，`0502` 钉住被拒，`0505` 区段在建筑高度之外（`Refused(OUT_OF_BOUNDS)`）。`0501` 不用于上限之外的任何情形，也没有任何租约事件使用它。

## 9. 证伪（Falsification）

1. **跨区块的实体。** 一头牛位于 `x = 15.5`，远端区块未加载：一个 `ENTITY` 占用体，2×1 占地范围，锚点在已加载的区块内，两个被覆盖的格都是 `READABLE`，远端区块的其他格为 `DENIED`。若出现两个占用体、远端格为空气，或占地范围被裁切，则失败。
2. **捕获与应用之间方块实体 NBT 改变。** 捕获一个装着 27 颗钻石的箱子，在 `begin` 之前移除它们：回滚恢复的是应用前的内容（空），而不是捕获时的 27 颗。若执行器从快照恢复并复制物品，则失败。
3. **旋转过的门。** `FACING=NORTH`、`HINGE=LEFT`，上下两半都是如此，`CLOCKWISE` ⇒ `FACING=EAST`、`HINGE=LEFT`（`DoorBlock.java:268`）；`LEFT_RIGHT` 镜像 ⇒ `FACING=SOUTH`、`HINGE=RIGHT`（`:276`）；两次镜像恢复原状。若通用的 `FACING` 重写使 `HINGE` 未循环，或两半不一致，则失败。
4. **被拒绝的区块读取。** 一个与未加载区块重叠的盒体：`DENIED`，`occupant == null`，`Spend.cells` 不含这些格，匹配为 `Undecided(UNREADABLE)`。若变成空气、计入一次读取，或为 `Failed`，则失败。
5. **静默的空操作旋转。** 一个继承默认 `rotate`（`BlockBehaviour.java:282`）且带 `Direction` 属性的模组方块：`Refused(SC-MC-0201)`。若接受未改变的状态，则失败。
6. **两个所有者、一个格、一个谓词。** A 以 `EXCLUSIVE` 结算；B 对同一格的 `settle` 从 `ClaimMatrix` 返回 `ClaimConflict`，账本文件逐字节不变，结果是携带 A 的行的 `Failed(SC-CLM-0002)`。若适配器按自己的规则拒绝、B 的行被写入，或冲突被上报为 `RevisionMoved`，则失败。
7. **一头牛离开围栏。** 没有方块变化，也没有 `setPos` 挂钩：该窗口的实时 `cell → EntityKey` 集合少了一项，该窗口的单元变脏，下一次 `check` 重新运行它们。若 `Formed` 仍来自旧快照，或差分遍历的是窗口的格而不是其实体，则失败。
