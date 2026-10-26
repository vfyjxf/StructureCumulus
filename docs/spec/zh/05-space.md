# 05 — 空间（Space）

> 一个格点在哪里、一个盒子是什么、一个结构如何转动、一条诊断指向哪个框架。
> 服务 R-01、R-04、R-05、R-07；强制 I-3、I-5。包 `kernel.space`。

## 1. 范围（Scope）

拥有格点（`LatticePoint`、`LatticeKey`）、占地代数（`Cuboid`、`CuboidSet`）、
48 元群（`Heading`、`Turn`、`Tilt`、`Flip`、`Orientation`）、摆放（`Placement`、`Siting`）、
诊断框架（`LocusFrame`、`Locus`、`LocusMap`），以及后续单元继承的规范单元格顺序与盒顺序。
不拥有：`SourceSpan`、`Finding`、标签排序（U9）；`OrientationPolicy`、锚点角色、蓝图边界（U5）；
`AnchorCandidate`、安放位选择、世界读取、预算（U7）；片段装配与来源（U6）。

- **纯算术**：不访问世界、时钟、随机数或 I/O（I-4、I-15）。
- **无浮点、无半格**：转动围绕局部原点 `(0,0,0)`，`Placement.offset` 是唯一的重新居中手段。
- **只有格点等距变换**：48 个朝向加一次整数平移。
- **离开格点即缺席**：`@Nullable`，绝不回绕、钳制或抛异常。

## 2. 术语（Vocabulary）

| 术语 | 类型 | 约定 |
| --- | --- | --- |
| 格点（Lattice point） | `LatticePoint` | 打包范围内的 `int (x,y,z)`；`(0,0,0)` 是声明的原点 |
| 格键（Lattice key） | `LatticeKey` | 一个 `long`，x:26 / z:26 / y:12；作标识，并按格序可比较 |
| 轴（Axis） | `Axis` | `X`、`Y`、`Z` |
| 长方体（Cuboid） | `Cuboid` | 半开区间 `[min,max)`；`min == max` 时为空 |
| 长方体集合（Cuboid set） | `CuboidSet` | 规范、互不相交、已排序、不含空盒的盒子列表 |
| 朝向（Heading） | `Heading` | `NORTH,SOUTH,WEST,EAST,UP,DOWN` |
| 偏转（Turn） | `Turn` | 绕 `Y` 的偏航四分之一转；循环，阶为 4 |
| 倾斜（Tilt） | `Tilt` | 局部上方指向何处；六种旋转 |
| 镜像（Flip） | `Flip` | `NONE` 加两个反射 |
| 朝向变换（Orientation） | `Orientation` | 恰好 48 个带符号轴置换之一 |
| 摆放（Placement） | `Placement` | `(Orientation, LatticePoint offset)` |
| 安放位（Siting） | `Siting` | `(LatticePoint origin, Placement)` |
| 定位框架（Locus frame） | `LocusFrame` | `DEFINITION`、`LOCAL`、`WORLD`、`KEY` |
| 定位域（Locus） | `Locus` | 框架 + `CuboidSet` + 可选的 `KeyLocus` + 可选的 `SourceSpan` |
| 定位域映射（Locus map） | `LocusMap` | 针对一个 `Siting` 的框架转换 |

## 3. API 设计（API design）

行为见 §4；构造函数只因畸形值抛异常（`04` §5）。

| 类型 | 签名面 |
| --- | --- |
| `LatticePoint` | `record (int x, int y, int z) implements Comparable<LatticePoint>`; `static of(int,int,int)`, `ORIGIN`, `static boolean inLattice(int,int,int)`, `MIN_XZ`, `MAX_XZ`, `MIN_Y`, `MAX_Y`（打包范围）, `@Nullable offset(int,int,int)`, `key()`, `compareTo` |
| `LatticeKey` | `record (long packed) implements Comparable<LatticeKey>`; `static of(LatticePoint)`, `x()`, `z()`, `y()`, `toPoint()`, `compareTo` 委托给 `toPoint()`（`y,z,x`） |
| `Cuboid` | `record (LatticePoint min, LatticePoint max) implements Comparable<Cuboid>`; `static of(int minX,int minY,int minZ,int maxX,int maxY,int maxZ)`, `of(LatticePoint,LatticePoint)`, `of(LatticePoint)`; `isEmpty`, `contains(LatticePoint)`, `contains(Cuboid)`, `intersects`, `@Nullable intersect`, `bounding`, `@Nullable translate(int,int,int)`, `long volume()`, `asSet()` |
| `CuboidSet` | `static empty()`, `of(Cuboid...)`, `of(Collection<Cuboid>)`; `List<Cuboid> boxes()`, `boxCount()`, `isEmpty()`, `long volume()`, `@Nullable Cuboid bounds()`, `contains(LatticePoint)`, `containsAll(CuboidSet)`, `intersects(CuboidSet)`, `union`, `intersect`, `subtract` (set and cuboid forms), `@Nullable translate` |
| `Heading` | `enum { NORTH, SOUTH, WEST, EAST, UP, DOWN }`; `opposite()`, `axis()`, `step()` |
| `Turn` | `enum { NONE, CLOCKWISE, HALF, COUNTERCLOCKWISE }`; `compose(Turn)`, `inverse()` |
| `Tilt` | `enum { LEVEL, PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, INVERTED }`; `Heading up()` |
| `Flip` | `enum { NONE, LEFT_RIGHT, FRONT_BACK }`; `boolean isMirror()` |
| `Orientation` | `record (int ordinal) implements Comparable<Orientation>`; `IDENTITY`, `static of(Turn,Tilt,Flip)`, `compose`, `then`（本元素在前）, `inverse`, `Heading apply(Heading)`, `@Nullable apply(LatticePoint)`, `@Nullable apply(Cuboid)`, `@Nullable apply(CuboidSet)`, `Turn turn()`, `Tilt tilt()`, `isMirror()`, `id()`, `static List<Orientation> all()` |
| `Placement` | `record (Orientation, LatticePoint offset)`; `IDENTITY`, `@Nullable compose(Placement)`, `@Nullable inverse()`, `@Nullable apply(...)`——偏移是格点，因此一次合成可能越出格阵并返回 `null` |
| `Siting` | `record (LatticePoint origin, Placement)`; `static of(origin, Orientation)`, `of(origin, Placement)`, `withOrigin`, `withPlacement`, `static @Nullable anchoring(anchorTarget, anchorLocal, Placement)`, `@Nullable toWorld(...)`, `@Nullable toLocal(...)` |
| `SectionKey` | `record (World world, int x, int y, int z) implements Comparable<SectionKey>`; `static of(World, LatticePoint cell, int cellSize)`, `SectionKey sectionOf(LatticePoint, int cellSize)`（实例方法：复用本键的世界）；先比世界、再比 `x`、`z`、`y`。它是一世界内的 16³ 区段索引，不是缓存键 |
| `Locus` | `record (LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan)`; `static definition(SourceSpan)`, `local(CuboidSet)`, `local(CuboidSet, SourceSpan)`, `world(CuboidSet)`, `key(InstanceId, NamespacedId)`, `withSource(SourceSpan)` |
| `LocusMap` | `record (Siting)`; `static of(Siting)`, `@Nullable toWorld(Locus)`, `@Nullable toLocal(Locus)`, `toDefinition(Locus)` |

## 4. 语义（Semantics）

### 4.1 格点：一个 `long`

采用 Minecraft 的布局，但不使用 Minecraft 的类型（I-1）：三个带符号的二进制补码字段，位于互不重叠的位片。

| 位 | 宽度 | 字段 | 范围 |
| --- | --- | --- | --- |
| 63–38 | 26 | `x` | `[-33_554_432, 33_554_431]` |
| 37–12 | 26 | `z` | 同上 |
| 11–0 | 12 | `y` | `[-2_048, 2_047]` |

```java
long packed = ((long)(x & 0x3ff_ffff) << 38) | ((long)(z & 0x3ff_ffff) << 12) | (y & 0xfffL);
int x = (int)(packed >> 38);          // bit 63 is x's sign
int z = (int)(packed << 26 >> 38);    // move bit 37 up, then sign-extend
int y = (int)(packed << 52 >> 52);    // 12-bit signed
```

`LatticePoint` 只保存**此范围内**的坐标，正是这项检查让打包成为单射：掩码会丢弃位，所以
`(33_554_432,0,0)` 与 `(-33_554_432,0,0)` 共享同一个 26 位模式，而只有后者是格点。宿主的
坐标（边界 `±30_000_000`，高度 `-64..320`）都落在范围内；该范围是防护，不是限制。

`LatticeKey` 的相等就是 `packed` 相等，`key()` 是双射。它**是** `Comparable`：
`compareTo` 委托给 `LatticePoint.compareTo`，`(y,z,x)` 升序 —— 层、行、列；`Cuboid` 以同样的方式排序。
数值 `packed` 顺序**不是**这个顺序：`y` 在低位上做了符号扩展，
`of(0,0,0)=0`、`of(0,1,0)=1`、`of(0,-1,0)=4095`、`of(1,0,0)=2^38`，永远不要按 `packed` 排序。
（`08` 要求声明行使用该自然序；折叠用的是 `SectionKey`，不是它。）

### 4.2 长方体（Cuboid）

每个轴上都是 `[min,max)`；`min == max` 不贡献任何东西，所以这样的长方体是*空*的，但合法。
包含关系是集合包含：空长方体不含任何点，并且位于每个长方体之内。

| 操作 | 规则 |
| --- | --- |
| `contains(p)` / `contains(b)` | `min ≤ p < max`（为空时 `false`）/ `b` 为空，或 `min ≤ b.min`、`b.max ≤ max` |
| `intersects(b)` / `intersect(b)` | 所有轴上 `min < b.max` 且 `b.min < max`；一旦 `lo ≥ hi`，`intersect` 为 `null` |
| `bounding(b)` / `translate(d)` | min 取各 min 的最小值，max 取各 max 的最大值 / 两个角点 `+ d`，离开格点则为 `null` |
| `volume()` | `∏ (max − min)`，饱和运算 |

没有 `Cuboid.union`，也没有 `Cuboid.subtract`：两个长方体的并集不是长方体，差最多是六个
长方体，所以两者都放在 `CuboidSet` 上，长方体只保留外壳 `bounding` —— 让两种操作共用一个
名字，会用一层壳把占地轮廓撑大（I-10）。体积做饱和运算，因为格点自身的体积是
`2^26 · 2^12 · 2^26 = 2^64`，比 `Long.MAX_VALUE` 多一。两端都闭的宿主盒 `[a,b]` 在这里是
`[a,b+1)`：把 `b` 当作 `max` 抄过来会丢掉最后一格。

### 4.3 CuboidSet：规范形式

不做单元格枚举；每个成员都是盒子算术（I-3）。`of` 是唯一的入口，也是每个代数成员的主体：

1. **丢弃空盒。**
2. **依次在 x、z 上切割**每个相异边界，留下板柱（slab-columns）。
3. **在列内沿 y 合并**区间重叠的部分；仅仅相接的保持分开。
4. **排序**按规范盒顺序；绝不把两个盒子合并成它们的凸包。

**CS-1** 无空盒，**CS-2** 两两不相交，**CS-3** 规范顺序，**CS-4** 幂等，
**CS-5** 无冗余。是无冗余而非最小：最小覆盖不唯一，因此不可能规范。

**相等是结构性的**：两个表示同一批单元格的输入可能规范化为不同结果，而从单元格集合反推
分解意味着枚举单元格。没有任何地方需要它 —— 每个集合都来自 `of`、`union`、`intersect`、
`subtract` 或 `translate`，所以相同的推导给出相同的值；语义相等是
`a.containsAll(b) && b.containsAll(a)`。代价为 `O(n log n)` 个边界、最坏 `O(n²)` 个片段，
逐单元格无代价。

### 4.4 朝向与三个生成元族

```java
// step: NORTH(0,0,-1) SOUTH(0,0,1) WEST(-1,0,0) EAST(1,0,0) UP(0,1,0) DOWN(0,-1,0)
Turn  NONE(x,y,z) CLOCKWISE(-z,y,x) HALF(-x,y,-z) COUNTERCLOCKWISE(z,y,-x)   // fixes UP
Tilt  LEVEL(x,y,z) PITCH_UP(x,-z,y) PITCH_DOWN(x,z,-y)                      // up: UP, SOUTH, NORTH
      ROLL_LEFT(-y,x,z) ROLL_RIGHT(y,-x,z) INVERTED(x,-y,-z)                // up: WEST, EAST, DOWN
Flip  NONE(x,y,z) LEFT_RIGHT(x,y,-z) FRONT_BACK(-x,y,z)                     // the two improper
```

`Turn` 是 4 阶循环（`HALF` 自逆）。`Tilt` 是一个**横截（transversal）**：六个真旋转，
`up()` 的像互不相同，偏航子群的每个陪集各取一个。`Tilt` 与 `Flip` 都不对合成封闭
（`ROLL_LEFT²` 是绕 `Z` 的半转；`FRONT_BACK ∘ LEFT_RIGHT` 是 `Turn.HALF`），这正是它们是
生成元而不是群元素的原因。

### 4.5 Orientation：48 个元素之一

一个朝向变换是**带符号轴置换** `world(a) = s_a · local(π(a))`：`{X,Y,Z}` 的一个置换，
加上每个世界轴一个符号。共有 `6 · 8 = 48` 个，这就是整个群（P3）。

```
ordinal = (turn.ordinal() * 6 + tilt.ordinal()) * 2 + (isMirror() ? 1 : 0)      // 0..47
```

以偏转为主序，其次倾斜，最后奇偶性；`IDENTITY = 0`，`all()` 升序，`compareTo` 按
ordinal。一个元素的规范名是 `(turn(), tilt(), isMirror() ? FRONT_BACK : NONE)`。奇偶性即
行列式：`isMirror()` 为真当且仅当 flip 不是 `NONE`，所以 `INVERTED` —— 绕 `X` 的半转 ——
是真旋转，而镜像摆放永远无法通过转动到达。

**生成元不是 48 的唯一命名。** 48 个元素上有 `4 · 6 · 3 = 72` 个三元组：每个真元素一个
名字（奇偶性 `NONE`），每个非真元素两个 —— `of(HALF, LEVEL, LEFT_RIGHT)` 等于
`of(NONE, LEVEL, FRONT_BACK)` —— 这是一个有 24 条双纤维的满射，规范三元组是使它成为双射
的截面。存储从不使用三元组：那里 `(π₁,s₁) ∘ (π₂,s₂) = (π₁∘π₂, s₁ + π₁(s₂))` 是一步，
而三元组无法逐分量合成。

**像规则。** 点按 `world(a) = s_a · local(π(a))`；长方体逐世界轴处理，源轴 `c = π(a)`：

```
s_a = +1:  lo = min[c]        hi = max[c]
s_a = -1:  lo = 1 - max[c]    hi = 1 - min[c]
```

`1 −` 正是正确与少一格之间的全部差别：取负把 `c` 送到 `−c`，所以 `[min,max)` 映到
`[-max+1,-min+1)`；直接映射存储的角点得到 `[-max,-min)`，它包含一个谁都不含的单元格，
却丢掉了 `min` 的像。长度不变，所以**朝向变换从不改变体积**。对集合：每个盒子取一个像，
然后 `of(...)` —— 双射保持不相交性，所以它只做排序。

### 4.6 摆放与安放位

```
Placement.apply(q) = o(q) + t                    // o = orientation, t = offset
Siting.toWorld(p)  = origin + o(p) + t           // componentwise, checked
Siting.toLocal(w)  = o⁻¹(w − origin − t)
```

| 定律 | 陈述 |
| --- | --- |
| 合成（Composition） | `P.compose(Q) = (o_P ∘ o_Q, o_P(t_Q) + t_P)` —— `Q` 先作用；和越出格阵时为 `null` |
| 逆（Inverse） | `P.inverse() = (o⁻¹, −o⁻¹(t))`；当两者都非空时，`P.inverse().compose(P) == IDENTITY` |
| 线性（Linearity） | `o(p.offset(d)) == o(p).offset(o(d))` |
| 仿射等变（Affine equivariance） | `s.toWorld(p.offset(d)) == s.toWorld(p).offset(o(d))` |
| 重新安放（Re-siting） | `s.withPlacement(P.compose(Q)).toWorld(q) == s.withPlacement(P).toWorld(Q.apply(q))` |

第四条定律是关键：**局部增量必须先经过朝向变换，再加到世界点上。** 世界 `(10,64,-5)` 处
的铰链，局部 `+X` 增量为 2，在 `Turn.CLOCKWISE` 下沿 `+Z` 移动；`toWorld` 是仿射的，
从不是线性的。

`anchoring(target, local, P)` 使用 `origin = target − (o(local) + t)`，使一个单元格落到
另一个上（I-5）；哪个单元格、哪个朝向属于 U5。一个 `7 × 6 × 7` 蓝图绕原点转
`CLOCKWISE` 后落在 `x ∈ [-6,1)`、`z ∈ [0,7)`。

### 4.7 框架、定位域与转换

| 框架 | 坐标含义 | 单元格 | 产生者 |
| --- | --- | --- | --- |
| `DEFINITION` | 作者文本中的字符偏移 | 作者声明的单元格 | U6 校验、设计检查 |
| `LOCAL` | 蓝图自身的网格 | 检查触及的单元格 | 舱位与接缝检查（U7） |
| `WORLD` | 某一安放位上的绝对格点 | 同一批单元格，摆放后 | 账本冲突、修复（U4、U10） |
| `KEY` | 某一实例的一个类型化上下文键 | 总为空 | 状态计算、上下文诊断 |

- **L-1** `DEFINITION` 蕴含 `source != null`，并携带作者声明的单元格；`KEY` 蕴含 `key != null`、`cells.isEmpty()` 且没有 span。
- **L-2** 否则每个单元格都落在该框架内（`LOCAL` 平凡成立；`WORLD` 由 `Siting.toWorld` 构造而来）；在蓝图
  边界内的包含性由 U5 检查。
- **L-3** 定位域不是声明，不授权任何东西；只有 `Claim` 携带义务。

`toWorld` 映射 `LOCAL → WORLD` 并固定 `WORLD`；`toLocal` 相反；两者都保留 `source`，
并在结果会离开格点时回答 `null`。
`toDefinition` 对任意框架回答所携带 source 的 `DEFINITION` 定位域；其他所有配对 ——
从 `DEFINITION` 求几何、从未记录过 source —— 回答 `null`。一个标签持有其检查运行时所在
框架中的一个定位域，渲染器负责投影：这是投影职责，不是三个字段。

**片段装配下的 `DEFINITION`。** 一个 `SourceSpan` 是*一个*文档中的区间。装配会组合但从不
重写文本，所以装配后的蓝图每个源文档各有一个定义空间：一个 `DEFINITION` 定位域指向某一篇
片段的文本，只有配合指明它的来源（U6 的 `DocumentProvenance`）才可解释。U1 从不重新编号
区间。

## 5. 参考代码（Reference sketch）

```java
// 1. Compose and invert; INVERTED is a rotation, not a reflection.
Orientation yaw    = Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE);   // NORTH ↦ EAST
Orientation cap    = Orientation.of(Turn.NONE, Tilt.INVERTED, Flip.NONE);     // UP ↦ DOWN
Orientation tipped = yaw.compose(cap);        // cap first, then the yaw: NORTH ↦ WEST
assert tipped.apply(Heading.NORTH) == Heading.WEST && tipped.apply(Heading.UP) == Heading.DOWN;
assert !tipped.isMirror();                    // half turn about X, determinant +1
assert tipped.inverse().compose(tipped).equals(Orientation.IDENTITY);
assert Orientation.all().size() == 48;

// 2. A door: the opening is the bay minus its frame.
CuboidSet door    = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 2, 1));   // [3,5)x[0,2)x[0,1): 4 cells
CuboidSet frame   = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1));   // lower half: 2 cells
CuboidSet opening = door.subtract(frame);                       // [3,5)x[1,2)x[0,1)
assert opening.boxCount() == 1 && opening.volume() == 2;

// 3. One bay, three frames; the siting turns the south face east.
Siting siting = Siting.of(LatticePoint.of(10, 64, -5), yaw);
Locus authored = Locus.local(CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1)), sourceSpan);
LocusMap map = LocusMap.of(siting);
Locus inWorld = map.toWorld(authored);          // @Nullable: null only off-lattice
assert inWorld != null
        && inWorld.cells().equals(CuboidSet.of(Cuboid.of(10, 64, -2, 11, 65, 0)));
assert map.toLocal(inWorld).equals(authored);   // exact inverse, source span intact
Locus definition = map.toDefinition(inWorld);   // @Nullable
assert definition != null && definition.frame() == LocusFrame.DEFINITION;

// 4. Edges: empty cuboid, a cuboid across the origin, the lattice boundary.
Cuboid empty = Cuboid.of(2, 0, 2, 2, 5, 5);                     // min.x == max.x
assert empty.isEmpty() && !empty.contains(LatticePoint.of(2, 1, 2)) && CuboidSet.of(empty).isEmpty();

Cuboid across = Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.FRONT_BACK)
        .apply(Cuboid.of(-1, 0, -1, 1, 1, 1));                  // @Nullable
assert across != null && across.equals(Cuboid.of(0, 0, -1, 2, 1, 1));  // x is [0,2), not [-1,1)

Siting atEdge = Siting.of(LatticePoint.of(33_554_431, 0, 0), yaw);
assert atEdge.toWorld(LatticePoint.of(0, 0, -1)) == null;       // image is 2^25: absent, not wrapped
```

## 6. 用例（Use cases）

**附加包作者：一扇双扇门。** 舱位是 `Cuboid.of(3,0,0,5,2,1)`；两扇门是上面 `subtract` 的
结果及它在舱位内的镜像。当只有上扇出错时，期望以 `Reply.mismatch(...)` 应答，参数为
`Locus.local(openingCells, ctx.locus().source())` —— 一个定位域，旁边是作者区间，渲染器
从本次尝试的安放位推导世界单元格；附加包从不自行计算世界坐标。

**宿主：`/cumulus preview`。** 一次 `Repair` 之后，每个舱位通过
`LocusMap.of(formed.siting()).toWorld(Locus.local(bay.cells()))` 变成世界单元格；回答
`null` 意味着该安放位不适合格点 —— 这是一条诊断项，不是崩溃。

**U7：有界遍历。** 必须读取每个单元格的步骤按规范单元格顺序展开 `boxes()`，每格计一次
读取，总计 `volume()`；声明检查则询问 `a.subtract(b).isEmpty()`。

## 7. 证明（Proofs）

**P1 —— 打包可逆。** 各字段是互不重叠的位片，宽度之和为 64，所以编码器拼接的是定宽二进制
补码字段：范围内的不同三元组必在某个字段上相差 `(-2^26,2^26)`（`x`、`z`）或
`(-2^12,2^12)`（`y`）内的非零值，从而在该字段的余数上不同，从而在某一位上不同。范围内集合
有 `2^64` 个元素，恰好是 `long` 的个数，所以该映射是对全部 64 位模式的双射，
`key()`/`toPoint()` 处处互逆。没有范围检查时，`x` 与 `x ± 2^26` 会掩码到同一个字段。

**P2 —— 规范集合。** CS-1：第 1 步恰好移除不含单元格的盒子。CS-2：第 2 步之后每个片段
位于一个板柱内，区间只在重叠处合并，所以幸存者互不相交，各列在 `x`–`z` 上互不相交。
CS-3：排序键是全序，因为共享 `min.y, min.z, min.x` 的两个盒子不可能不相交。CS-4：对自身
输出，没有盒子跨越边界，也没有可合并的区间，所以流水线是恒等。CS-5：一列的区间都是极大的，
每个单元格只属于一列。`union` 与 `subtract` 返回显式盒列表的 `of(...)`，因此按构造即规范；
它们的单元格集合确为并集与差集，因为 `subtract` 一次移除一个长方体，`a \ b` 的六个片段
（两个 `x` 板，然后在交集内两个 `y` 与两个 `z` 板）互不相交，且恰好覆盖 `a` 减去它的部分。

**P3 —— 群。** *封闭性*：带符号置换与带符号置换合成为一个。*结合性*：矩阵合成。
*单位元*：`π = id`，所有符号为 `+`。*逆元*：`π⁻¹[π[i]] = i`，`s⁻¹[i] = s[π⁻¹[i]]`；
把两者代入乘积，正反两个方向都在每个坐标上得到单位元。*大小*：真旋转是 `6 × 4`
（`+Y` 的去向 × 残余偏航），`Turn × Tilt` 双射地实现它们，因为六个倾斜的 `up()` 像互不
相同，从而是偏航子群的不同陪集；再乘固定的反射 `FRONT_BACK` 得到 24 个非真元素，两半不
相交，所以群恰有 `24 + 24 = 48` 个元素。

**P4 —— 体积不变。** 每个轴上，`+1` 的像长度为 `max[c] − min[c]`，`−1` 的为
`(1 − min[c]) − (1 − max[c]) = max[c] − min[c]`；长度只是被置换，所以乘积不变，且因为
`min ≤ max`，不会出现负长度。

**P5 —— 确定性。** `ordinal` 是有限范围上的 `12·turn + 2·tilt + parity`，由 P3 它是到
`0..47` 的全函数；`all()` 就是该范围按序；`of`、`compose` 与 `inverse` 在静态初始化器中
由十二个生成元矩阵按公式得到。没有集合迭代、哈希顺序、身份哈希或时钟参与，所以同一份源码
在每次运行、每个 JVM 上都得到同样的 48 个值。

**P6 —— 往返。** 对格内点，`toLocal(toWorld(p)) = o⁻¹(origin + o(p) + t − origin − t) = p`。
对规范集合，带符号置换把边界映到边界、把盒内部映到内部，所以没有像盒跨越边界；不相交性与
区间重叠都保留，因此重新规范化既不切割也不合并，只做排序 —— 映射回去即还原列表。规范集合
同样对平移封闭。

**P7 —— 仿射等变。** `toWorld(p.offset(d)) = origin + o(p) + o(d) + t =
toWorld(p).offset(o(d))`，因为朝向变换是线性的；摆放与重新安放定律由同样的展开得到。

## 8. 依赖与待决问题（Dependencies and open questions）

仅依赖 `base`：`SourceSpan` 现在位于 `base`（Q-S1 已解决），所以 `space` 从不导入
`finding`。

| 编号 | 问题 | 选项 |
| --- | --- | --- |
| **Q-S1（已解决）** | `SourceSpan` 移到 `base`；`space` 只依赖 `base`，`Locus`/`Label` 都使用 base 的值。 | — |
| **Q-S2** | `Flip` 有三个常量；任务书（brief）算作两个。这正是 48 个元素有 72 个名字的原因。 | (A) 保留三个常量加规范三元组；(B) 二值 `Flip`，用缺席表示“无反射”。 |
| **Q-S3（已解决）** | `Label` 不再携带第二个 `SourceSpan`；`Locus.source()` 是唯一来源。 | — |
| **Q-S4** | 这里没有任何东西遍历单元格（I-3），但 U7 的遍历与 U10 的叠加层需要顺序。 | (A) 调用方按 `LatticePoint` 顺序展开 `boxes()`；(B) 增加 `Stream<LatticePoint> cells()` —— 需要术语表加一行，并按单元格计费。 |
| **Q-S5** | `LatticePoint` 受格点约束并带可空算术；替代方案是自由 `int`，只在宿主数据进入处检查范围。 | (A) 维持原样，全内核一条不变量；(B) 自由 `int` 加一个带检查的 `toWorld`，把 `null` 推给 U2/U6。 |
| **Q-S6（已解决）** | `Axis { X, Y, Z }` 现在是 `01-glossary.md` §C 中的 U1 行。 | — |

## 9. 证伪（Falsification）

| 论断 | 使其不成立的用例 |
| --- | --- |
| P1 | 从原始 int 打包而非 `LatticePoint`：`(33_554_432,0,0)` 与 `(-33_554_432,0,0)` 都掩码成 `0x2000000`，给出同一个 `long`。测试 `key(2^25) != key(-2^25)`，并在 `z` 与 `y ± 2^11` 上做同样的成对测试。 |
| §4.5，`all()` 是 48 个互异元素 | `all().size() == 48 && Set.copyOf(all()).size() == 48`。一旦 ordinal 写成 `(turn·6 + tilt)·3 + flip.ordinal()` 就会破：72 个下标，`of(HALF, LEVEL, LEFT_RIGHT)` 与 `of(NONE, LEVEL, FRONT_BACK)` 碰撞，`all()` 出现重复。 |
| P4，体积从不改变 | 对全部 48 个朝向 `o.apply(Cuboid.of(0,0,0,1,2,4)).volume() == 8`，再加上像盒本身（`1 × 2 × 4` 在 `CLOCKWISE` 下是 `4 × 2 × 1`）：立方体会掩盖错误的轴接线，而在 `+1` 轴上多做一个 `1 −` 修正会让长度变成 `max − min + 1`。 |
| CS-2 / CS-3 | `of(Cuboid.of(0,0,0,1,1,1), Cuboid.of(1,0,0,2,1,1)).boxCount() == 2`；一次合并会回答 1，一格声明就悄悄变成两格声明。对称地，`of(A,B)` 且 `B ⊂ A` 时不得存在重叠对。 |
| §4.5，像按逐格方式计算 | `Cuboid.of(-1,0,-1,1,1,1)` 在 `FRONT_BACK` 下是 `[0,2)×[0,1)×[-1,1)`。按角点映射会回答 `[-1,1)`，其中含 `-1` —— 没有任何元素的像含它 —— 并丢掉 `-1` 的像。 |
| P6 | 在格点边缘的安放位，配负偏移，作用于跨越负 `x` 的集合：`toLocal(toWorld(s))` 必须等于 `s`。若 `toWorld` 钳制或回绕而不是回答 `null`，或 `toLocal` 在求逆前先加偏移，则失败。 |
| §4.6，`toWorld` 是仿射的 | 世界 `(10,64,-5)` 处局部 `+X` 增量 2 在 `Turn.CLOCKWISE` 下必须落到 `+Z`。把增量直接加到世界点上，在 `IDENTITY` 下通过，在其余每个偏转下失败。 |
