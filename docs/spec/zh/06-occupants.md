# 06 — 占用体与世界窗口（Occupants and the world window）

> U2 · v0.1 · 2026-10-20 · R-01、R-02、R-08 · I-2、I-3、I-4、I-13。

## 1. 范围（Scope）

**纳入：** `OccupantKind`、`OccupantRef`、`OccupantDescriptor`、`TraitKey`、`Trait`、
`OccupantSnapshot`、`NestedView`、`WorldView`、`ReadStatus`、`SnapshotId`、`SnapshotPolicy`、
`WorldWindow`、`WorldCapture`、`OccupantResolver<R>`、`ResolveContext`。

**排除：** 空间原语（U1）；`Verdict`/`Reply`（U3 —— `expectation` 依赖 `occupant`）；
`Census`/`ReadSet`（U7）；`Finding`（U9 —— U2 只保留诊断码）；捕获实现（A1）；声明（U4）。

**主张（claim）：** 一次匹配读取一个不可变、单一修订的快照；每个单元格都有应答或被显式标为
不可应答；四类占用体来源通过同一形状应答。

## 2. 术语（Vocabulary）

| 名称 | 约定 |
| --- | --- |
| `OccupantKind` | 解析器所服务的族：一个 id record，五个常量。 |
| `OccupantRef` | `(snapshot, type, kind, anchor)`；相等是同一捕获内的同一性；内核唯一的排序依据。 |
| `OccupantDescriptor` | `(type, kind, properties, tags)`；只有 id 与字符串。 |
| `TraitKey<T>`、`Trait<T>` | 类型化键 `(id, Class<T>)`；一个 `(key, @Nullable value)` 对。 |
| `OccupantSnapshot` | 不可变读取模型：ref、描述符、占地轮廓、特征、可选的嵌套视图。 |
| `NestedView` | 子结构的窗口：一个完整的 `WorldView`，同一快照，自有框架，深度有界。 |
| `WorldView` | 唯一的世界窗口；在其边界内是全函数；从不接触世界。 |
| `ReadStatus` | `READABLE`、`OUT_OF_BOUNDS`、`NOT_CAPTURED`、`DENIED`、`STALE`。 |
| `SnapshotId`、`SnapshotPolicy`、`WorldWindow` | 捕获标识、其保证、请求的盒。 |
| `WorldCapture`、`OccupantResolver<R>`、`ResolveContext` | 捕获 SPI、解析器 SPI、解析器上下文。 |

## 3. API 设计（API design）

### 3.1 标识、描述符与特征

```java
record OccupantKind(NamespacedId id) implements Comparable<OccupantKind>
    // BLOCK, BLOCK_ENTITY, ENTITY, NESTED, OTHER (structurecumulus ids); of(id) = host family
record OccupantRef(SnapshotId snapshot, NamespacedId type, OccupantKind kind, LatticePoint anchor)
    implements Comparable<OccupantRef>          // order: snapshot, y, z, x, kind.id, type
    boolean from(SnapshotId id)                 // snapshot.equals(id)
record OccupantDescriptor(NamespacedId type, OccupantKind kind,
                          Map<String, String> properties, List<NamespacedId> tags)
    @Nullable String property(String key)       // null = the host published nothing
record TraitKey<T>(NamespacedId id, Class<T> type)
record Trait<T>(TraitKey<T> key, @Nullable T value)
```

- `anchor` 是**铸造该 ref 的框架中**的所属单元格：子视图铸造子框架的 ref，所以跨框架只有
  `snapshot` 可比较。
- 描述符：`properties` 按键、`tags` 排序并去重，以不可修改方式复制；
  `property(k) == null` 是沉默，绝不是 `false`。两个适配器版本解析同一个逻辑值时，必须
  产出相等的描述符。
- `TraitKey` 的相等是 `(id, type)`：错误的令牌取不到值，同一 id 挂在两个类型下会在构造时
  被拒绝。特征值是 `String`、装箱基本类型、`NamespacedId`、不可变 record，或上述元素的
  不可修改集合；活对象或 `Supplier` 破坏 I-4。

### 3.2 占用体快照与读取状态

```java
record OccupantSnapshot(OccupantRef ref, OccupantDescriptor descriptor, Cuboid footprint,
                        List<Trait<?>> traits, @Nullable NestedView nested)
    static NamespacedId AIR                     // structurecumulus:air
    static OccupantSnapshot air(SnapshotId, LatticePoint)
    boolean isAir()                             // descriptor().type().equals(AIR)
    <T> @Nullable Trait<T> find(TraitKey<T>)    // null = not captured
    <T> @Nullable T trait(TraitKey<T>)          // null = not captured or valueless
    boolean has(TraitKey<?>)                    // present; value may be null
enum ReadStatus { READABLE, OUT_OF_BOUNDS, NOT_CAPTURED, DENIED, STALE; boolean readable() }
```

- `footprint()` 是一个半开 `Cuboid`（I-3：单个元素的并），即最大的轴对齐体；非盒状组合是
  多个占用体。
- `nested() != null` 当且仅当宿主解析出了子结构；`NESTED ⇒ nested() != null`，反之不成立。
  构造时拒绝 ref 与描述符不一致、空占地轮廓、没有子结构的 `NESTED`。
- `air(...)`：kind 为 `BLOCK`（该 id 是哨兵），一格，无特征；适配器把每个空世界值映射
  到它。
- `view.occupant(cell) != null ⇔ view.readStatus(cell) == READABLE`（§7.2）。

### 3.3 嵌套视图、世界视图与策略

```java
interface NestedView extends WorldView
    OccupantSnapshot parent()       // the occupant that exposed this view
    int depth()                     // root view = 0; a direct child = 1
    List<OccupantRef> ancestry()    // outermost first, ending with parent().ref()

interface WorldView
    SnapshotId snapshotId()
    Cuboid bounds()
    @Nullable OccupantSnapshot occupant(LatticePoint cell)
    List<OccupantSnapshot> occupants(Cuboid box)
    ReadStatus readStatus(LatticePoint cell)
    default @Nullable OccupancySummary summary(Cuboid box) { return null; }   // palette-derived
    default @Nullable ChunkGrid chunkGrid() { return null; }                  // no chunking
    default @Nullable ChunkContent chunk(ChunkKey key) { return null; }       // palette facade
record OccupancySummary(Map<NamespacedId, Integer> byType, int total) {}
interface ChunkContent {
    Cuboid bounds(); ReadStatus status(); boolean allAir();
    boolean maybeHasBlock(NamespacedId type);
    int countBlock(NamespacedId type);          // -1 = cannot answer
    @Nullable OccupantSnapshot occupant(LatticePoint cell);
    ReadStatus readStatus(LatticePoint cell);
}
record SnapshotId(World world, long revision) implements Comparable<SnapshotId>
record WorldWindow(Cuboid bounds, int maxElements)
record SnapshotPolicy(CaptureMode capture, Set<OccupantKind> requiredKinds,
                      Set<TraitKey<?>> requiredTraits, int maxElements,
                      int maxNestedDepth, long maxStalenessNanos)
    enum CaptureMode { EXACT, REQUIRED }
    static SnapshotPolicy minimal(int maxElements)   // REQUIRED, depth 0, staleness 0
```

嵌套视图**就是**一个完整的 `WorldView`：顶层能问的每个问题都能在下一层以相同的代价类别
与缺席规则询问（R-01）；改变的只有框架。`bounds()` 是本视图的可寻址盒，其外是
`OUT_OF_BOUNDS`，绝不是“空”。`revision >= 1`，按世界单调递增。上限是
`min(window.maxElements(), policy.maxElements)` —— 请求可以降低宿主的承诺，绝不能抬高。
`requiredTraits` 必须得到回答；无法计算某一项的宿主把它记为缺席（⇒ 不可读），绝不是
默认值。

### 3.4 捕获与解析 SPI

```java
interface WorldCapture
    CaptureResult capture(CaptureRequest request)   // Captured | TooLarge | Unavailable
    long latestRevision()                           // one WorldCapture serves exactly one world
record CaptureRequest(Cuboid bounds, ChunkGrid grid, SnapshotPolicy policy,
                      Set<NamespacedId> trackedTypes, Set<LatticeKey> trackedCells,
                      CaptureLimits limits)
record CaptureLimits(int maxCells, int maxSections, long maxBytes, int maxEntities, int maxPayloads)
sealed interface CaptureResult
    record Captured(WorldView view) implements CaptureResult
    record TooLarge(Cuboid bounds, int elements) implements CaptureResult
    record Unavailable(ReadStatus status, FindingCode code) implements CaptureResult
interface OccupantResolver<R>
    Set<OccupantKind> kinds()                   // dispatch: families this resolver claims
    @Nullable OccupantSnapshot resolve(R raw, ResolveContext context)      // null = decline
interface ResolveContext
    SnapshotId snapshotId()
    LatticePoint anchor()                       // in the frame being captured
    int remainingDepth()
    SnapshotPolicy policy()
```

## 4. 语义（Semantics）

### 4.1 五种状态；空不等于不可读

| 状态 | `occupant(cell)` | 原因 |
| --- | --- | --- |
| `READABLE` | 非空：覆盖该格的占用体，否则为空气 | 在 `snapshotId()` 处已捕获 |
| `OUT_OF_BOUNDS` | `null` | 在 `bounds()` 之外，或位于另一框架的点 |
| `NOT_CAPTURED` | `null` | 在界内但无数据：元素上限、深度截断、不支持的区域 |
| `DENIED` | `null` | 可读但被拒绝：保护、权限、不加载策略 |
| `STALE` | `null` | 只存在另一修订的值 |

优先级 `OUT_OF_BOUNDS > DENIED > STALE > NOT_CAPTURED > READABLE`。保留诊断码（U3/U7 构建
诊断项）：`SC-OCC-0101` 越界、`0102` 未捕获、`0103` 被拒绝、`0104` 陈旧、`0201` 占地轮廓
跨越窗口、`0202` 深度截断、`0203` 环。

- **空是事实：** 可读的空单元格是一个空气占用体，有真实的 ref 与占地轮廓；“没有占用体”
  绝不从 `null` 推断，也不从不带状态检查的空列表推断。
- **不可读不是否定回答：** `null` 加上不可读状态就是 `UNREADABLE`（U3），进而 `Undecided`
  （U7），而不是 `Failed`。在陈旧区块上返回不匹配的谓词，是在从未查看的情况下断言一堵墙
  不存在；把 `DENIED` 洗成空气，正是让正确内核说谎的宿主 bug。
- 消费者使用两次调用（先 `occupant`，再 `readStatus`），因为 `WorldView` 是冻结的；U3 的
  `ExpectationContext.occupantAt` 是安全路径必须成为短路径的地方。

### 4.2 一种形状，四类来源

| 来源 | `kind` | `type` | `properties` | 特征 | `footprint` |
| --- | --- | --- | --- | --- | --- |
| 方块状态 | `BLOCK` | 方块 id | 状态写作 `name=value` | 很少 | 1 格 |
| 方块实体 | `BLOCK_ENTITY` | 方块实体 id | 状态 + 本体字段 | 物品栏、能量 | 1 格 |
| 实体 | `ENTITY` | 实体 id | 身份、变种 | 物品栏、拥有者 | 包围盒 |
| 子结构 | `NESTED` | 结构 id | 声明的参数 | 内部总量 | 声明的范围 |

内核算法只能询问 `type()`、`kind()`、`property(..)`、`has(TraitKey)`、`footprint()` 与
`nested()`；上述任何一行都不享有特权。

### 4.3 多单元格占用体

| 情形 | 规则 |
| --- | --- |
| 占地轮廓内的单元格 | `occupant(cell)` 整体返回该占用体：一个 2×1 实体的两格给出同一个 ref、同一个描述符。**被覆盖的单元格是 `READABLE`，即使它自己的读取被拒绝或从未尝试**，因为已准入占用体的占地轮廓就是证据；`DENIED` 只适用于没有已准入占用体覆盖的单元格。 |
| `occupants(box)` | 每个占用体一条，无论它有多少格落在盒内，按 `OccupantRef` 排序；按 `ref` 去重，每个占用体只铸造一次。空气被排除，所以盒查询以元素为界，而不是以体积为界。 |
| 占地轮廓越过 `bounds()` | 允许，**不裁剪**（I-3）；准入以锚点为准。锚点在外而身体在内的占用体是缺席的，其在内的单元格是 `NOT_CAPTURED`，绝不是空气。 |
| 跨越舱位 | 对 U2 不可见：`occupants(bayCuboid)` 整体返回跨界者，所以 `footprint().volume() > bay.volume()` 可见，由期望决定。 |
| 一格两个占用体 | 两者都存在；`occupant(cell)` 依次按非空气、再按更小体积、再按 ref 顺序收窄。 |

### 4.4 特征只在捕获期产生

特征由解析器计算一次、存储、永不重算：U2 没有任何成员触及世界，所以“匹配期间没有实时
读取”是接口的性质（§7.1）。**缺失与 null：** `find == null` ⇒ `UNREADABLE`；存在但
`value() == null` ⇒ 一个确定的事实，谓词可以检验；无法区分二者的宿主不得发布该特征。
**必需特征**来自策略（U7 由 `ExpectationMeta.requires` 派生）；跳过一项即为 `UNREADABLE`。
**不可变性**是宿主的契约（§3.1）：特征值在快照生命期内固定，评审与适配器的捕获测试承担
这一点，因为 `Class<T>` 令牌无法证明深度不可变。

### 4.5 嵌套视图

| 问题 | 回答 |
| --- | --- |
| 边界 | `bounds()` 是子框架中的子网格；`parent().footprint()` 是父框架中的范围；`Siting`/`LocusMap`（U1）把二者复合。 |
| 快照 | 整条链上 `snapshotId()` 与父相同：一次捕获，一个 id。 |
| `readStatus` | 同样五种；`OUT_OF_BOUNDS` 相对于子 `bounds()`；`NOT_CAPTURED` 还涵盖深度截断与元素上限。 |
| 递归 | `depth() = parent + 1`；捕获拒绝 `depth() > maxNestedDepth` 的子结构，留下 `nested() == null` 且内部为 `NOT_CAPTURED`。 |
| 环 | 当 `(parent().descriptor().type(), parent().ref().anchor())` 沿 `ancestry()` 重复时视为成环；捕获拒绝（`nested() == null`、`NOT_CAPTURED`、`SC-OCC-0203`）。失败即关闭且为启发式 —— 框架不共享 —— 所以深度才是真正的保证。 |
| 暴露面 | 仅通过 `occupant`/`occupants`/`readStatus`；没有“此结构的子结构”成员。 |

### 4.6 捕获协议与宿主职责

```
host thread: capture(window, policy)
  1. intersect the window with the readable extent; split into capture units
  2. classify each unit readable / denied / unsupported     (no reads yet)
  3. admit elements in canonical order until the element cap
  4. per raw value: first resolver whose kinds() claims it → resolve(raw, ctx)
  5. NESTED → recurse with remainingDepth - 1, same policy
  6. mark cells under a rejected occupant NOT_CAPTURED
  7. mint one ref per occupant, stamp one SnapshotId
```

**必须：** 不可变（值在 `capture` 返回前固定；无世界、可变缓存或时钟）；覆盖真实性（每格一个
状态，`READABLE` 仅在确已读取处，空气仅在已验证未被覆盖处）；修订单调性（每次都严格更大，
任何被捕获的值变化时即改变）；确定性（规范顺序，相同输入相同输出）。
**不得：** 从视图惰性读取；修改世界（只有 `EditExecutor` 写，U10）；把 `DENIED`、读取失败
或未加载的区块变成空气；交出可变集合或活实例；在一个视图内混合修订。

没有 `Budget` 到达捕获：它运行在尝试之前，只受 `maxElements` 与 `maxNestedDepth` 约束，
在宿主选择的任何线程上（适配器使用服务端线程）。解析器在同一线程上运行，子捕获得到
`remainingDepth() - 1`。

### 4.7 标识、陈旧与可复现性

| 问题 | 回答 |
| --- | --- |
| 视图比世界旧？ | `revision() < capture.latestRevision()`；在 `maxStalenessNanos` 内允许，仍是单一连贯修订。 |
| 单元格比视图旧？ | 不得如此：只能从另一修订服务的单元格回答 `STALE`，绝不是混合。一扇门一半在修订 5、一半在 7 就是这种失败。 |
| 来自另一次捕获的 ref？ | `ref.from(view.snapshotId())` 为假，且 `occupant(ref.anchor())` 返回不相等的 ref：被检测到，不做修复。 |

`MatchRequest` 携带一个既有视图；内核从不捕获。一旦捕获返回，之后的世界变化不可达，所以
`(blueprint, snapshot, siting, stage, policy)` 固定结果（I-4）。匹配期间的变化只影响更晚的
一次尝试，它看到不同的 `SnapshotId` —— 不同的输入，而不是不同的回答。`STALE` 复查会捕获
新视图；它从不刷新正在运行的视图。id 从不持久化，所以持久状态保存值（U8），绝不保存 ref。

## 5. 参考代码（Reference sketch）

```java
// id("x") = NamespacedId.of("structurecumulus", "x"); addon trait value: immutable, captured, no host handle
record StackCount(NamespacedId item, int count) {}
record ItemInventory(List<StackCount> stacks) { ItemInventory { stacks = List.copyOf(stacks); } }
static final TraitKey<ItemInventory> ITEM_INVENTORY =
        new TraitKey<>(id("item_inventory"), ItemInventory.class);

final class CapturedView implements WorldView {      // built by capture; indexes and order precomputed
    private final SnapshotId id;
    private final Cuboid bounds;
    private final CuboidSet readable;
    private final Map<LatticePoint, ReadStatus> refused;       // DENIED / STALE / NOT_CAPTURED, in bounds
    private final Map<LatticePoint, OccupantSnapshot> covered;  // non-air, by covered cell
    private final List<OccupantSnapshot> elements;              // canonical order

    @Override public SnapshotId snapshotId() { return id; }
    @Override public Cuboid bounds() { return bounds; }

    @Override public ReadStatus readStatus(LatticePoint cell) {
        if (!bounds.contains(cell)) return ReadStatus.OUT_OF_BOUNDS;
        ReadStatus here = refused.get(cell);
        if (here != null) return here;
        return readable.contains(cell) ? ReadStatus.READABLE : ReadStatus.NOT_CAPTURED;
    }

    @Override public @Nullable OccupantSnapshot occupant(LatticePoint cell) {
        if (readStatus(cell) != ReadStatus.READABLE) return null;           // the biconditional
        OccupantSnapshot o = covered.get(cell);
        return o != null ? o : OccupantSnapshot.air(id, cell);
    }

    @Override public List<OccupantSnapshot> occupants(Cuboid box) {
        List<OccupantSnapshot> out = new ArrayList<>();
        for (OccupantSnapshot e : elements)      // sorted by ref once, in the capture constructor
            if (e.footprint().intersects(box) && readStatus(e.ref().anchor()) == ReadStatus.READABLE) out.add(e);
        return List.copyOf(out);
    }
}

static OccupantSnapshot chest(SnapshotId id, LatticePoint cell, List<StackCount> stacks) {
    OccupantRef ref = new OccupantRef(id, NamespacedId.of("example", "reinforced_chest"),
            OccupantKind.BLOCK_ENTITY, cell);
    OccupantDescriptor d = new OccupantDescriptor(ref.type(), ref.kind(),
            Map.of("facing", "north", "waterlogged", "false"), List.of(NamespacedId.of("example", "reinforced")));
    return new OccupantSnapshot(ref, d,
            Cuboid.of(cell.x(), cell.y(), cell.z(), cell.x() + 1, cell.y() + 1, cell.z() + 1),
            List.of(Trait.of(ITEM_INVENTORY, new ItemInventory(stacks))), null);
}

static OccupantSnapshot golem(SnapshotId id, LatticePoint min) {   // 2x1 entity
    OccupantRef ref = new OccupantRef(id, NamespacedId.of("example", "hauling_golem"), OccupantKind.ENTITY, min);
    Cuboid body = Cuboid.of(min.x(), min.y(), min.z(), min.x() + 2, min.y() + 1, min.z() + 1);
    return new OccupantSnapshot(ref, new OccupantDescriptor(ref.type(), ref.kind(),
            Map.of("variant", "iron"), List.of()), body, List.of(), null);
}

static void inspect(WorldView view, Cuboid hallBox) {
    view.occupant(LatticePoint.of(3, 64, 4));                                    // one ref for both cells
    view.occupants(Cuboid.of(3, 64, 4, 5, 65, 5));                               // the golem, listed once

    ReadStatus denied = view.readStatus(LatticePoint.of(4, 64, 9));              // DENIED
    OccupantSnapshot none = view.occupant(LatticePoint.of(4, 64, 9));            // null, never air
    ReadStatus outside = view.readStatus(LatticePoint.of(-1, 64, 0));            // OUT_OF_BOUNDS

    for (OccupantSnapshot o : view.occupants(hallBox)) {
        NestedView inner = o.nested();
        if (inner == null || inner.depth() > 2) continue;
        boolean sameCapture = inner.snapshotId().equals(view.snapshotId());      // true
        OccupantSnapshot core = inner.occupant(LatticePoint.of(0, 1, 0));        // child-frame point
    }
}
```

## 6. 用例（Use cases）

| 角色 | 情形 | U2 为其提供 |
| --- | --- | --- |
| 附加包 | 需要物品栏的舱口；想要子结构内部的熔炉舱位 | `has(...)` 作免费测试，`trait(...)` 作计费测试，`nested()` 取内部 |
| 附加包 | 一扇门：两格、两个占用体、一个覆盖舱位的期望 | `occupants(bayCuboid)` 整体返回两者；没有体素形状（R-06） |
| 宿主 | 用于 `/cumulus validate` 的 `LevelCapture`；kernelTest 假视图 | 两个 SPI 加一个基于 map 的 `CapturedView`：服务端与测试共用一条代码路径 |
| 宿主 | 建造魔杖预览 | 确定性的 `occupants` 顺序：同一快照的两次预览完全一致 |

## 7. 证明（Proofs）

1. **没有实时读取。** 每个视图与快照成员都只接收和返回值；唯一触及世界的签名是 `capture` 与
   `resolve`，且 `kernel.occupant` 只导入 `base` 与 `space`。*反例：* 委托给活关卡的宿主
   满足接口却破坏 I-4 —— 这是宿主承诺，由适配器“关卡关闭后视图仍能应答”的测试抓住。
2. **不可变性与状态不变量。** 构造函数做规范化，没有 setter；`occupant` 是
   `readStatus != READABLE ? null : getOrDefault(cell, air)`。*反例：* 把调用方的 map 直接
   交出去的描述符会改变正在运行的匹配。
3. **多单元格一致性。** 每个占用体一个 ref，映射到它覆盖的每一格；`occupants` 过滤元素
   列表，所以去重靠同一性。*反例：* 每格一个 ref ⇒ 两个元素、翻倍的普查、被重复记账的
   声明。
4. **嵌套递归有界。** `depth = parent + 1`，超过 `maxNestedDepth` 即拒绝，所以一条链最多
   `maxNestedDepth + 1` 个视图。*反例：* 自我解析的结构方块；在深度 3 时第四层是
   `NOT_CAPTURED`，绝不是栈溢出。
5. **描述符与版本无关。** 四个 id/字符串分量，没有 ordinal、状态 id 或类名。*反例：*
   发布 `state=1234` 而不是 `facing=north`，会让所有蓝图在移植后失效。
6. **确定性迭代。** 特征按键 id、属性按键、标签按 id 去重、占用体按 `OccupantRef`。
   *反例：* 返回 `HashMap.values()` 会让诊断项抖动。

## 8. 依赖与待决问题（Dependencies and open questions）

**依赖。** U0：`NamespacedId`、`Checks`。U1：`LatticePoint`、`LatticeKey.of/toPoint`、
`Cuboid.of/contains/intersects/volume`、`CuboidSet.contains`。**使用方。** U3、U4、U7、U8、
U10、U11、A1。

| # | 问题 | 立场 |
| --- | --- | --- |
| Q1 | 术语表中的 ref 元组省略了快照戳。 | **修正 A1：** `OccupantRef(SnapshotId, NamespacedId, OccupantKind, LatticePoint)`；没有它，`from(...)` 与陈旧检测都不可能。 |
| Q2 | 术语表给 `OccupantSnapshot` 一个“可选载荷（optional payload）”，但 `kernel.occupant` 不得依赖 `kernel.document.DataView`（03 §3），且无类型载荷破坏 I-13。 | **修正 A2：** 去掉 `payload`；宿主身体是一个特征（`TraitKey<HostBody>`，由 A1 声明）。 |
| Q3 | 没有捕获期的需求清单，读取特征就是掷硬币。 | **修正 A3：** 在 `SnapshotPolicy` 中增加 `Set<TraitKey<?>> requiredTraits`，由 U7 填充。 |
| Q4 | `footprint()` 是一个 `Cuboid`；L 形宿主组合无法表达。 | 推迟：拆分该组合。若出现真实用例，改为 `CuboidSet` 并重证 §7.3。 |
| Q5 | 众所周知的特征容器（`Traits.ITEM_INVENTORY`，04 §8–§9）归谁？ | U3 或 U11；`structurecumulus:item_inventory` 在此保留。 |
| Q6 | 03 §7 说常量是 lowerCamelCase，但其示例是 `Expectations.AIR`，术语表写的是 `BLOCK`。 | U2 遵循术语表；03 §7 应予更正。 |
| Q7 | 逐格覆盖检查使大蓝图的 BOUNDS 检查产生 O(volume) 次状态调用。 | 不加成员；若 U7 实测为热点，提议把 `readStatus(Cuboid)` 作为 default 方法。 |

## 9. 证伪（Falsification）

| 场景 | 必须发生什么 | 什么能证明 U2 错了 |
| --- | --- | --- |
| 承载一个舱位的区块在匹配中途卸载。 | 匹配不受影响：它读取之前捕获的视图。之后的捕获回答 `NOT_CAPTURED`，所以复查是 `Undecided`，绝不是“墙消失了”。 | 任何触及世界的成员：匹配会抛异常、阻塞或返回混合修订的回答。 |
| 一个实体跨越舱位边缘。 | `occupants(bay)` 整体返回它；期望看到 `footprint().volume() > bay.volume()` 并作出判断；另一个舱位也看到它。 | 被裁剪的占地轮廓：跨界看起来会像恰好吻合，声明会悄悄重叠。 |
| 宿主修改箱子的物品栏而不提升修订号。 | 两次捕获在相同 `SnapshotId` 下内容不同 —— 这是宿主违反 §4.6，内核无法检测；适配器的捕获测试断言修订必须提升。 | 若内核按 id 缓存内容（它没有 —— ref 是值），这会悄悄破坏匹配。 |
| 嵌套结构包含自身。 | 捕获在 `maxNestedDepth` 处停止（以及发现重复时）；最深一层是 `NOT_CAPTURED`，带 `SC-OCC-0202`/`0203`；最坏是 `Undecided`。 | 栈溢出，或一个“空”的最深层 —— 后者会把环变成匹配。 |
| 受保护的单元格（`DENIED`）、元素上限，或锚点在窗口之外。 | `null` 加状态与诊断码；被丢弃的单元与位于被拒占用体之下的单元格是 `NOT_CAPTURED`；最坏是 `Undecided`，绝不是被截断的“确定”回答。 | 空气或更软的状态：缺失的门、被丢弃的占用体，或占用体的尾部被读成空地板。 |
