# 21 — 紧凑 JSON 创作 profile（Compact JSON authoring profile）

> 状态：设计草案 v0.2，2026-10-20。这是一个 JSON profile，不是自定义语言。紧凑 profile 是
> 同一个 `BlueprintDocument` 的第二种*形态*；`DocumentSchema` 在校验之前把它规范化为规范
> 形式。schema 2 是唯一可写的版本；schema 1 经 `DocumentUpgrade.v1ToV2` 读取。

## 1. 缘由（Why）

规范的 `BlueprintDocument` 是 IR，不是创作格式。一个 7×6×7 的熔炉需要：

- 带 `bounds`、`parameters`、`bays[]`、`seams[]` 的 `segments[]`；
- 带 `as`、`segment`、`at`、`overlap` 的 `assembly[]`；
- `anchors[]`、`orientation`、`extensions`；
- 一套不透明的期望 `DataView` 文法。

多数结构并不需要这些。它们只需要一个外壳、一个空腔、几个舱位、一两个检查、一个锚点和一个
朝向。

紧凑 profile 只保留这些，其余部分由成文的默认值派生。它仍然是 JSON：没有注释、没有
自定义文法、没有第二个解析器。规范 profile 仍可用于高级特性；紧凑文档会被规范化成它，
因此 `StructureKernel.compile`、校验、诊断项与运行时语义都不变。

## 2. 两个 profile，一种文档类型（Profiles, one document type）

```java
public enum DocumentProfile { COMPACT, CANONICAL, AUTO }

public interface DocumentSchema {
    int MIN_VERSION = 1;
    int MAX_VERSION = 2;

    @Nullable BlueprintDocument read(NamespacedId source, DataView root,
                                     DocumentProfile profile, List<ValidationIssue> issues);
    DataView write(BlueprintDocument document, DocumentProfile profile);  // canonical or compact
}

public interface DocumentUpgrade {
    DataView v1ToV2(DataView root, List<ValidationIssue> issues);   // `size` → `bounds`, `claim` split
    static DocumentUpgrade defaults();
}
```

- `COMPACT` 接受省略 `segments`、`assembly`、`orientation`、`context`、`computations` 与
  `checks`。
- `CANONICAL` 是显式形式。
- `AUTO` 是数据包默认值：含 `segments`/`assembly` 的根为规范形；含
  `shell`/`cavity`/`fills`/`bays` 的根为紧凑形。混用两者的文档是校验错误。
- 两个 profile 产出*同一个* `BlueprintDocument`；区别只在于作者必须写出什么。
- `read` 接受 `1..MAX_VERSION`：版本 1 在校验之前由 `DocumentUpgrade.v1ToV2` 升级，其他任何
  版本是 `SC-DOC-0001`。文档绝不会在新形态下被静默重新解释。
- `write` 总是输出 `schemaVersion: 2`。`write(COMPACT)` 是工具/导出路径；`write(CANONICAL)`
  是稳定的规范形式。
- 紧凑文档从不存入运行时；它在加载/编译时被规范化。

### 2.1 数据包（Datapack）

紧凑 JSON 是一等数据包格式：

```
data/<namespace>/cumulus/structure/<path>.json
```

`DatapackBlueprints` 以 `DocumentProfile.AUTO` 读取每个文件，规范化为规范
`BlueprintDocument`，校验它，并缓存编译后的 `Blueprint`。重载会整体换掉缓存；校验失败的
文件就是缺席，绝不静默地用它的上一版替换。`/cumulus lint` 与 `/cumulus build` 可以强制
`COMPACT` 或 `CANONICAL`，让某个错误显式暴露。

## 3. 紧凑形态（Compact shape）

```json
{
  "schemaVersion": 2,
  "id": "mymod:huge_furnace",
  "bounds": [0, 0, 0, 7, 6, 7],

  "shell":  [{"box": [0,0,0,7,6,7], "block": "minecraft:blast_furnace"}],
  "cavity": [[1,1,1,5,4,5]],
  "fills":  [{"box": [1,1,1,5,1,5], "block": "minecraft:smooth_stone"}],

  "bays": [
    {"id": "mouth", "box": [3,1,0,1,2,1], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "bound",
     "expect": {"block": "minecraft:iron_bars"}}
  ],

  "checks": [
    {"id": "mouth_facing", "bay": "mouth",
     "focus": {"kind": "block", "cell": [3,1,0]},
     "expect": {"kind": "state", "property": "facing", "value": "east"}}
  ],
  "computations": [
    {"id": "volume", "phase": "section",
     "output": "mymod:volume", "region": [1,1,1,5,4,5],
     "kind": "count", "args": {"mode": "cells"}}
  ],
  "context": [
    {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"}
  ],

  "anchors": [{"role": "front", "property": "facing", "value": "east"}],
  "orientation": {"front": "north"}
}
```

这就是整个熔炉：没有 segments，没有 assembly，没有 plans，也没有 claims 块、没有偏转列表。

### 3.1 根字段（Root fields）

| 字段 | 类型 | 必需 | 含义 |
| --- | --- | --- | --- |
| `schemaVersion` | int | 是 | `1` 或 `2`；写出的是 `2`，`1` 被升级，其他任何值为 `SC-DOC-0001` |
| `id` | namespaced id | 是 | 蓝图 id |
| `bounds` | `[x, y, z, sx, sy, sz]` | 是 | 每个声明所处的空间；原点加尺寸，半开 |
| `shell` | `{box, block, thickness?}` 的数组 | 否 | 每个盒的外层一格（或 `thickness` 格）外壳 |
| `cavity` | 盒或 `{box, block}` 的数组 | 否 | 内缩盒；裸盒表示空气，这是常见情形 |
| `fills` | `{box, block}` 或 `{layer, block}` 的数组 | 否 | 有序的显式填充 |
| `bays` | array | 否 | 具名原子叠加层；每个是一个带覆盖、共享与占地的 `Cuboid` |
| `checks` | array | 否 | 一个 `Check`：所属舱位、焦点、期望 |
| `computations` | array | 否 | 一个计算：相位、输出键、区域（区段）或输入（聚合） |
| `context` | `{id, type, retention, merge?, whenAbsent?}` 的数组 | 否 | 计算写入的键；有 `merge` 表示区段贡献，缺席表示单写者 |
| `anchors` | `{role, property, value}` 的数组 | 否 | 在定义原点读取的锚点 |
| `orientation` | object | 否 | `front`，作者书写的正面向量，以及规范对象的可选 `turns` 与 `uprightOnly`；省略时，每个 `Turn` 都可接受，且不作任何 upright 承诺（`10` §4.1） |
| `plans` | object | 否 | 不是文档键：引擎展开声明的单元（`09` §4.8） |
| `segments` | array | 否 | 高级：显式片段；禁用隐式 `main` |
| `assembly` | array | 否 | 高级：显式摆放；与 `segments` 一起必需 |
| `extensions` | object | 否 | 宿主扩展，原样保留；`cumulus:capture` 被逐字保留 |

盒在任何地方都是 `[originX, originY, originZ, sizeX, sizeY, sizeZ]`——在 `bounds`、`shell`、
`cavity`、`fills` 与每个舱位中。最小/最大角点对不是盒，带 `min`/`max` 成员的 JSON 对象是
`SC-DOC-0002`。

锚点是**角色加在定义原点读取的属性与值**，而不是一个盒：原点正是让朝向变换非循环的东西
（`22` §4.4）。带单元格的锚点是 `SC-DOC-0002`。

### 3.2 期望（Expectations）

紧凑期望是一个带标签的 JSON 对象。它被规范化为规范形的不透明 `expectation` 节点。

```json
{ "block": "minecraft:iron_bars" }
{ "air": true }
{ "any": true }
{ "entity": "minecraft:cow" }
{ "trait": "structurecumulus:item_inventory" }
{ "allOf": [{ "block": "minecraft:iron_bars" }, { "trait": "structurecumulus:item_inventory" }] }
{ "anyOf": [{ "block": "minecraft:stone_bricks" }, { "block": "minecraft:deepslate_bricks" }] }
{ "not": { "air": true } }
{ "kind": "factory", "id": "mymod:door", "args": { "facing": "north", "half": "left" } }
```

- **一个对象只有一个标签。** 键就是标签，值就是它的实参：`{"block": "x"}` 精确等价于
  `Spec(kind = block, args = "x")`。一个对象里出现两个标签是 `SC-DOC-0605`，绝不是隐式 AND。
- `allOf`/`anyOf` 接受非空的 `expect` 对象数组；`not` 恰好接受一个对象。空数组是 `SC-DOC-0606`。
- 同一个 `allOf`/`anyOf` 的所有子项必须共享主体类型。`block` 与 `trait` 都测
  `OccupantSnapshot`，可以混用；`state` 测的是 `T`，不能与它们同组——写成两条 `checks[]`。
- 按格子的 OR 是 `{"eachCell": {"anyOf": [...]}}`；按整个舱位的 OR 是 `BayView` 期望的
  `anyOf`。
- `block`/`air`/`trait`/`allOf`/`anyOf`/`not` 是已随附的期望（`22` §6.7）。
- `factory` 通过 `Catalog<ExpectationFactory>` 解析；未知 id 在 `DESIGN` 阶段是
  `SC-SPI-0002`，绝不使用反射。
- 舱位可以省略 `expect`。这是合法的，而且不是“any”：没有期望的舱位要么是审计舱位
  （`Footprint.None`，不做任何断言），要么其断言来自指名它的 `checks[]` 条目。期望从不被
  推断，缺失的期望也从不匹配一切。

### 3.3 舱位、检查、锚点、朝向变换（Bays, checks, anchors, orientation）

```json
"bays": [
  {"id": "door", "box": [3,0,0,1,2,1], "coverage": "one_subject",
   "sharing": "exclusive", "footprint": "bound",
   "expect": {"kind": "factory", "id": "mymod:door", "args": {"facing": "north"}}}
],
"checks": [
  {"id": "door_hinge", "bay": "door",
   "focus": {"kind": "door", "cell": [3,0,0]},
   "expect": {"kind": "state", "property": "hinge", "value": "left"}}
],
"anchors": [{"role": "front", "property": "facing", "value": "east"}],
"orientation": {"front": "north"}
```

- 一个舱位就是一个矩形 `Cuboid`；像门这样的多格谓词是一个舱位（R-06）。
- `coverage` 是必需的：`every_cell`、`one_subject` 或 `any_subject`；`sharing` 为 `exclusive`
  （默认）或 `shared`；`footprint` 为 `box`、`bound`（默认）、`declared` 或 `none`。
  三者由 R1 与 R4 在 DESIGN 一起校验（`24`）：`exclusive` 配 `none` 是 `SC-CHK-0106`，与覆盖
  不匹配的检查同样如此 —— 少于一个 `every_cell` 舱位的逐格检查集，或 `any_subject` 之下的
  逐格检查。
- 检查**指名它的舱位**；没有 `bay` 的检查是 `SC-CHK-0107`，因为结构级检查自相矛盾。
- 检查的 `focus` 可以指名一个单元格（`{"kind": "door", "cell": [...]}`），或指名计算写入的
  某个键（`{"kind": "context", "key": "mymod:volume"}`）。后者是 `Focuses.context(key)`，
  因此键缺席即 `Outcome.Missing`，该检查为 `UNREADABLE`，绝不是零值。
- `orientation.front` 是作者书写的正面向量；安放位是使 `apply(front)` 等于锚点所读朝向的
  唯一候选。`turns` 与 `uprightOnly` 在这里和规范形态中一样是可选的；未写出偏转列表的
  文档接受每一个 `Turn`，省略 `uprightOnly` 则不作任何承诺（`10` §4.1）。
- 不存在控制器实体；锚点是原点处读取的一个角色（R-04）。

### 3.4 填充与层（Fills and layers）

```json
"fills": [
  { "box": [0, 0, 6, 7, 1, 1], "block": "minecraft:bricks" },
  { "layer": { "y": 0, "legend": { "#": "minecraft:bricks", ".": "minecraft:air" },
               "rows": ["#######", "##...##", "#######"] } }
]
```

- `fills` 有序；每一项是一个 `box` 或一个 `layer`。
- 层是一个 `y` 上的二维切片；各行等长；图例把一个字符映射到一个方块/空气。多格期望/锚点
  是单独的 `bays`/`checks`。
- 层图精确但冗长；它们用于小件/不规则件以及捕获输出。

### 3.5 计算、键与形参（Computations, keys and parameters）

```json
"computations": [
  {"id": "volume", "phase": "section",
   "output": "mymod:volume", "region": [1,1,1,5,4,5],
   "kind": "count", "args": {"mode": "cells"}},
  {"id": "capacity", "phase": "aggregate",
   "output": "mymod:capacity", "inputs": ["mymod:volume"],
   "kind": "mymod:scale", "args": {"factor": 4}}
],
"context": [
  {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
  {"id": "mymod:capacity", "type": "long", "retention": "context"}
]
```

- 计算指名它的 `phase`：`section` 要求 `output` 键携带 `merge`，`aggregate` 禁止它。
  `phase`/`merge` 不匹配是 `SC-DOC-0002`，不是静默强制转换。
- 区段计算指名 `region`（一个舱位 id、一个盒，或一个片段）；聚合计算指名 `inputs`（它读取的
  键）——该列表正是 Kahn 序的构建基础，因此没有 `inputs` 而函数体非空的聚合是
  `SC-DOC-0002`。
- 聚合计算上的 `region` 是可选的，只收窄它的读取范围；没有 `region` 的区段计算覆盖
  `bounds`。
- `kind` 与其他任何目录 id 一样，裸词表示默认命名空间：内核随附 `count`、`sum`、`volume`、
  `distinct` 与 `bind`，因此附加组件的计算必须带命名空间书写（`mymod:scale`）。未注册的
  `kind` 在 `DESIGN` 是 `SC-CTX-0101`。
- `context[]` 只是声明：运行时通过 `ContextKeys.register(draft, …)` 构造键（`15` §3），编译器
  检查文档的声明与目录一致（`SC-CTX-0101`）。
- `whenAbsent` 是可选启用且显式的。省略它意味着“缺失的区段使该键为 `Missing`”，这是安全的
  默认（`22` §7.3）。
- `args` 是普通 JSON 对象；在紧凑 profile 中数字是整数（与 `DataView` 一致）。
- 规范 schema 的 `params`/`$param` 仍然可用；紧凑作者通常省略它们。

## 4. 几何降级（Geometry lowering）

紧凑几何为每个单元格分配恰好一个**声明面**。各阶段按此顺序应用，后一阶段的盒从更早的表面
取得它指名的单元格：

1. `shell`（每个盒的外层，`thickness` 深）；
2. `cavity`（内缩盒，默认空气）；
3. 按数组顺序的 `fills`（每个是一个 `box` 或一个 `layer`）；
4. 按数组顺序的 `bays`，每个是一个命名的原子叠加层。

结果是一张局部的 **单元格 → 声明** 映射，没有任何单元格被写入两次，这正是让展开成为函数的
原因：按 `y,z,x` 扫描，先沿 x 增长，再 z，再 y，就把该映射划分为互不相交的 `Cuboid`，每个
长方体成为一个 `Bay`。

规则：

- **具名舱位整体发出。** 一个不是单个长方体的具名舱位是 `SC-DOC-0503`，由作者拆分。编译器
  从不拆分具名舱位。
- **合成 id 来自盒**：`#box@x,y,z:sx,sy,sz`，绝不用计数器，也绝不让一个 id 覆盖多个盒。两个
  盒共用一个 id 是无法表示的。
- **具名舱位的单元格是最终的。** 后续阶段不得从具名舱位取走单元格，两个具名舱位也不得指名
  同一个单元格：那是 `SC-DOC-0504`，既不是降级，也不是静默合并。
- **`SC-DOC-0505` 是 `EmptyCuboid`**：任意轴上尺寸为零或负的 `box`。它是该字段处的声明错误，
  绝不是编译器丢弃的空舱位。

`shell`/`cavity`/`fills` 在 `bays` 之前应用，这就是 `shell → cavity → mouth` 从不产生重叠
独占舱位的原因；作者从不书写拆分。

## 5. 默认值（Defaults）

| 字段 | 紧凑默认值 |
| --- | --- |
| `schemaVersion` | 必需；写出 `2`，`1` 被升级 |
| `bounds` | 必需；绝不从盒推断，也绝不为零尺寸 |
| segments/assembly | 一个位于 `bounds.origin` 的隐式 `main` 片段 |
| 舱位 `coverage` / `sharing` / `footprint` | `coverage` 必需；`sharing` 为 `exclusive`，`footprint` 为 `bound` |
| `anchors` | 空：未指名任何锚点的紧凑文档不声明任何在原点读取的角色 |
| `orientation` | `front` = `north`（恒等）；由锚点的朝向决定摆放 |
| `computations`、`context` | 空 |
| `extensions` | 空 |

默认值被实体化进规范 `BlueprintDocument`；运行时从不应用紧凑 profile 的默认值。

## 6. 工具（Tooling，第一优先）

第一个实现面是捕获/导入，而不是手写文件。

| 工具 | 输入 | 输出 |
| --- | --- | --- |
| `/cumulus capture <id> <from> <to>` | 世界选区 | 紧凑 JSON |
| `/cumulus import <schem/nbt>` | Sponge `.schem` / 原版 `.nbt` | 紧凑 JSON |
| `/cumulus lint <file>` | 紧凑或规范 JSON | 带行/列的解析 + 设计诊断项 |
| `/cumulus build <file>` | 紧凑 JSON | 规范 JSON / 预览 |
| `/cumulus export <id> compact` | 已注册蓝图 | 紧凑 JSON，保留 `extensions` |

捕获启发式，按顺序：

1. 选区成为 `bounds`；`orientation.front` 是作者书写的 `north`，玩家的朝向成为锚点的
   `value`；
2. 连续的同方块段成为 `fills.box` 或 `shell`/`cavity`；
3. 不规则切片成为 `fills.layer`；
4. 方块实体与实体仅在谓词明显时成为一个 `bay`；它们的内容写入捕获扩展（§7），绝不丢弃，也
   绝不被空方块实体替换。

## 7. API

```java
public enum DocumentProfile { COMPACT, CANONICAL, AUTO }

public interface DocumentSchema {
    int MIN_VERSION = 1;
    int MAX_VERSION = 2;
    @Nullable BlueprintDocument read(NamespacedId source, DataView root,
                                     DocumentProfile profile, List<ValidationIssue> issues);
    DataView write(BlueprintDocument document, DocumentProfile profile);
}

public interface DocumentUpgrade {
    DataView v1ToV2(DataView root, List<ValidationIssue> issues);
    static DocumentUpgrade defaults();
}

public interface CompactWriter {
    DataView write(BlueprintDocument document);          // shorthand for schema.write(COMPACT)
}

public interface CaptureTool {
    @Nullable DataView capture(CaptureRequest request, IssueSink issues);   // null = any loss
}

public interface SchematicImport {
    @Nullable DataView read(NamespacedId source, byte[] bytes, IssueSink issues);
}
```

- JSON 前端是现有的 `DataFrontend`（适配器中为 `JsonFrontend`）；没有自定义解析器，没有 DSL。
- `read(COMPACT)` 规范化为 `BlueprintDocument`；`read(CANONICAL)` 是现有 schema。
- 源码区间来自 JSON 前端；紧凑字段报告自己的行/列。
- 有任何内容丢失时 `capture` 返回 `null`；每次丢失都是 sink 中的一条问题，绝不是静默省略。
  非空结果携带 `extensions["cumulus:capture"]`：

  ```json
  { "stateLossy": false, "issues": [], "toolVersion": "0.2" }
  ```

  编译器忽略该扩展；经 `write(COMPACT)` 的往返会把它逐字保留。
- 内容无法读取的方块实体是一条带 `SC-DOC-0521` 的问题，导入会**拒绝该方块实体**，而不是放置
  一个空的。可读内容写为覆盖该单元格的舱位或填充上的 `{"kind": "container", "items": {...}}`。
- `CaptureTool` 与 `SchematicImport` 发出 `DataView`；由宿主写 JSON。

## 8. 证明（Proofs）

1. **一种类型。** 两个 profile 都产出 `BlueprintDocument`；`check` 从不看到 profile。
2. **默认值是显式的。** 规范化后的文档包含 `bounds`、隐式 `main`、共享与占地；没有任何
   下游组件重新应用紧凑默认值。
3. **往返。** 对紧凑子集，`read(write(doc, COMPACT), COMPACT)` 在规范化后等于 `doc`；子集之外
   的字段强制走 `write(CANONICAL)`，而不是被丢弃。
4. **诊断。** 每个紧凑字段保留其 JSON 区间；未知 factory/计算/键指向该字段，而不是指向生成
   文档中的偏移。
5. **不执行代码。** `expect`、`computations` 与 `anchors` 是带标签的数据；`factory` 与每个
   `kind` 都是目录 id；没有反射，没有脚本。
6. **确定性。** 阶段顺序、fill/bay 数组顺序与长方体划分都是固定的；JSON 对象成员顺序从不影响
   结果。
7. **每格一个声明。** 展开为每个单元格分配一个声明面，因此规范文档的舱位互不相交，且
   `CuboidSet` 的范式在其上是恒等。

## 9. 待决问题（Open questions）

此处先前的每个问题都已由 `22` §12（版本、`bounds`、盒、具名舱位、扩展）定论，或已移至 `22`
§15。§16 的问题中有一个仍属于本文件：

| # | 问题 |
| --- | --- |
| OQ-1（`22` §16 Q5） | 对常见的 `count`/`volume` 情形，`context[]` + `computations[]` 是正确的但冗长。它需要一种简写——由计算内联声明其键并让 `phase` 蕴含 `merge`——还是这种简写会掩盖修订 5 显式化的折叠？ |

## 10. 证伪（Falsification）

- 熔炉的紧凑 JSON 规范化为与规范 JSON 相同的 `Blueprint`；出现差异即为缺陷。
- 带 `shell` 但没有 `bounds` 的紧凑文档被拒绝；它绝不推断零尺寸盒，零尺寸的 `box` 是
  `SC-DOC-0505`。
- 未知 `factory` id 在 `expect` 字段处产生 `SC-SPI-0002`，而不是类加载错误。
- 任何 `box` 位置出现 `min`/`max` 角点对是 `SC-DOC-0002`：盒是原点加尺寸。
- schema-1 文档被升级而不是被拒绝，且 `write` 输出 `schemaVersion: 2`；schema-3 文档是
  `SC-DOC-0001`。
- 一个具名舱位的单元格被后续 `fills` 条目取走是 `SC-DOC-0504`；非矩形的具名舱位是
  `SC-DOC-0503`，且从不被拆分。
- 既没有 `expect`、也没有 `checks[]` 条目指名它的舱位是审计舱位（合法，不声明任何内容）；
  没有 `expect` 的舱位从不匹配一切。
- 对紧凑子集内的文档，`read(write(doc, COMPACT), COMPACT)` 在规范化后字节相同。
- 捕获含不可读方块实体的区域会发出 `SC-DOC-0521` 并拒绝该方块实体，且 `capture` 返回 `null`
  而不是带静默空洞的文档。
