# 10 — 文档与数据（Documents and data，`kernel.document`）

> 单元 U6。R-09、I-12：蓝图如何变成数据，又如何变回蓝图。服务于 R-02、R-07、R-14；I-1、I-6、
> I-7、I-12、I-14、I-15。

## 1. 范围（Scope）

负责创作形态（authoring form）以及从该形态出发的编译：值模型与文法（§4.1–4.2）、规范化
（canonicalisation，§4.3）、DESIGN 校验（§4.4）、片段查找与放置（§4.5–4.6）、下沉
（lowering，§4.7）、生成契约，以及两个宿主接缝。不负责：字段一经编译后*意味着*什么——舱位的
检查（`Focus`/`Expectation`，U3）、`Segment` 构造与阶段计划（U5）、声明共存（U4）、计算体
（U8）。舱位的正文是一个 **`Spec`**：本单元存储它、校验它的形状，从不解释 `kind`——
`SpecResolver`（§4.6）在库安装时于服务器线程上针对 `Catalogs` 解析它。内核不附带任何解析器。
依赖：`base`、`space`、`blueprint`、`context`、`expectation`、`finding`。

## 2. 术语（Vocabulary）

| 名称 | 形态 | 契约 |
| --- | --- | --- |
| `DataView` / `DataKind` | interface / enum（位于 `kernel.base`） | 不可变值节点；`OBJECT`、`ARRAY`、`STRING`、`NUMBER`、`BOOLEAN`、`NULL`。定义在 `base` 中，使 `expectation` 无需导入 `document` 即可读取工厂实参。 |
| `DataFrontend` | SPI | 文本 ↔ `DataView`；JSON 与 SNBT 由适配器提供。 |
| `DataBridge<T>` | SPI | 宿主树 ↔ `DataView`；`T` 从不命名内核类型。 |
| `BlueprintDocument` | record | 类型化创作形态；构造即规范。 |
| `SegmentDocument` | record | 一条片段声明（嵌套 `Bay`、`Seam`、`Parameter`）。 |
| `AssemblyDocument` | record | 一次放置：`as`、`SegmentKey`、`at`、`overlap`。 |
| `Spec` | record | 一个已声明的单元或键：`kind` + `args` + 可选的 focus/expectation/subject/output/phase/retention/inputs。 |
| `SpecResolver` | interface（此处） | 通过 `Catalogs` 解析一个 `Spec`；`kind` 的唯一读取者。在 `spi` 中实现；未知的 kind 或实参是一个诊断项。 |
| `KeyDeclaration` | record | 一个已声明的 `ContextKey`：`{id, type, retention, merge?, whenAbsent?}`；`merge` 标记一个由段贡献的键。 |
| `DocumentUpgrade` | interface | 一个版本步；`v1ToV2` 是唯一随附的实例。 |
| `DocumentSchema` | interface | 某一版本的封闭文法、默认值、`read`/`write`。 |
| `Canonicalizer` | interface | 值规范形；纯净、全函数、幂等。 |
| `DocumentValidator` | interface | 针对文档与库的 DESIGN 检查；从不抛异常。 |
| `ValidationIssue` | record | `(code, severity, message, @Nullable locus)`。 |
| `DocumentProvenance` | record | `(source, @Nullable SourceSpan)`：编译后的值来自何处。 |
| `SegmentLibrary` | interface | 带宿主修订号的不可变带键片段集合，每次重载安装一次。 |
| `SegmentAssembler` | interface | `(document, library) → Blueprint` 或诊断项；纯净。 |
| `SegmentEmitter` | SPI | 把一条片段写回为 `DataView` 的外部工具。 |

引用：`NamespacedId`、`Arguments`、`ParameterKey`、`Retention`（U0）；`Cuboid`、
`CuboidSet`、`LatticePoint`、`Locus`、`Heading`、`Turn`（U1）；`Segment`、`SegmentKey`、`Bay`、
`Seam`、`Coverage`、`Sharing`、`Footprint`、`AnchorSpec`、`OrientationPolicy`、`CheckPlan`、
`Blueprint`（U5）；`Check`、`ContextKey`、`StatePhase`（U3、U8）；`Catalogs`（U11）；
`FindingCode`、`Severity`、`SourceSpan`、`Finding`（U9）。

## 3. API 设计（API design）

```java
package dev.vfyjxf.cumulus.kernel.document;      // @NullMarked

public enum DataKind { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

public interface DataView {                      // statics + nested Builder construct
    DataKind kind();
    @Nullable DataView get(String key);          // null iff the member is absent
    List<DataView> list();                       // empty unless ARRAY; never null
    SequencedSet<String> keys();                 // code-point order; empty unless OBJECT
    @Nullable SourceSpan span();                 // position, not content
    String asString(String fallback);            // fallback unless the kind matches
    int asInt(int fallback);
    boolean asBoolean(boolean fallback);
}

public record BlueprintDocument(int schemaVersion, NamespacedId id, Cuboid bounds,
        List<SegmentDocument> segments, List<AssemblyDocument> assembly,
        Map<String, DataView> parameters, List<AnchorSpec> anchors, OrientationPolicy orientation,
        List<Spec> checks, List<Spec> computations,
        List<Spec> derives, List<KeyDeclaration> context,
        Map<String, DataView> extensions) {}

public record SegmentDocument(NamespacedId id, Cuboid bounds, Map<String, Parameter> parameters,
        List<Bay> bays, List<Seam> seams) {
    public record Bay(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint,
                      Sharing sharing, @Nullable String description) {}   // JSON key "box"
    public record Seam(NamespacedId id, Cuboid cells, SeamKind kind, Map<String, String> props) {}
    public record Parameter(String name, @Nullable DataView defaultValue) {}
}

public record Spec(NamespacedId kind, DataView args,
                   @Nullable SpecRef focus, @Nullable SpecRef expectation,
                   @Nullable ClassRef subject, @Nullable KeyRef output,
                   @Nullable StatePhase phase, @Nullable Retention retention,
                   @Nullable List<KeyRef> inputs) {}
public record KeyDeclaration(NamespacedId id, ClassRef type, @Nullable Retention retention,
                             @Nullable NamespacedId merge, @Nullable DataView whenAbsent) {}
public record SpecRef(NamespacedId id) {}          // a catalog id, never an object
public record KeyRef(NamespacedId id) {}           // a key id, never a key instance
public record ClassRef(String name) {}             // "long", "int", "cumulus:block_view", …
public record AssemblyDocument(String as, SegmentKey key, LatticePoint at, boolean overlap) {}
public record ValidationIssue(FindingCode code, Severity severity, String message,
                              @Nullable Locus locus) {}
public record DocumentProvenance(NamespacedId source, @Nullable SourceSpan range) {}

public enum DocumentProfile { COMPACT, CANONICAL, AUTO }

public interface DocumentSchema {
    int MIN_VERSION = 1, MAX_VERSION = 2;
    int version(); SequencedSet<String> fields(); static DocumentSchema current();
    @Nullable BlueprintDocument read(NamespacedId source, DataView root,
                                     DocumentProfile profile, List<ValidationIssue> issues);
    DataView write(BlueprintDocument document, DocumentProfile profile);  // COMPACT or CANONICAL
}
public interface DocumentUpgrade {                     // one step, never a chain of dialects
    int from(); int to();
    @Nullable DataView upgrade(DataView root, List<ValidationIssue> issues);
    static DocumentUpgrade v1ToV2();                   // the only shipped step
}
public interface SpecResolver {            // implemented in spi; this side never names Catalogs
    @Nullable Check<?> check(Spec spec, List<ValidationIssue> issues);
    @Nullable Computation<?> computation(Spec spec, List<ValidationIssue> issues);
    @Nullable ContextKey<?> key(KeyDeclaration declaration, List<ValidationIssue> issues);
}
public interface CompactWriter {
    DataView write(BlueprintDocument document);    // schema.write(COMPACT), then span-free
}
public interface Canonicalizer {
    static Canonicalizer of(); DataView canonical(DataView value); boolean isCanonical(DataView value);
}
public interface DataFrontend {
    NamespacedId id();
    @Nullable DataView read(String text, List<ValidationIssue> issues);   // attaches spans
    @Nullable String write(DataView value, List<ValidationIssue> issues);
    default @Nullable String write(BlueprintDocument d, List<ValidationIssue> out);
}
public interface DataBridge<T> {
    NamespacedId id();
    @Nullable DataView fromHost(T hostValue, List<ValidationIssue> issues);
    @Nullable T toHost(DataView value, List<ValidationIssue> issues);
    default @Nullable T toHost(BlueprintDocument d, List<ValidationIssue> out);  // write, then toHost
}
public interface SegmentLibrary {
    long revision();                              // host-supplied, never a clock
    @Nullable Segment lookup(SegmentKey key);
    List<SegmentKey> keys();                      // id, then argument names
    static Builder builder(long revision);
    interface Builder {
        Builder put(Segment s, List<ValidationIssue> issues);
        Builder put(SegmentDocument document, List<ValidationIssue> issues);   // stores the specs
        SegmentLibrary apply(SpecResolver resolver, List<ValidationIssue> issues);  // §4.6
    }
}
public interface SegmentAssembler {
    static SegmentAssembler of(SegmentLibrary library);
    @Nullable Blueprint assemble(BlueprintDocument document, List<ValidationIssue> issues);
    @Nullable Segment compile(SegmentDocument document, List<ValidationIssue> issues);
}
public interface SegmentEmitter {
    NamespacedId id(); int schemaVersion(); void emit(Segment segment, DataView.Builder sink);
}
public interface DocumentValidator {
    static DocumentValidator of(SegmentLibrary library);
    List<ValidationIssue> validate(BlueprintDocument document);   // ordered; never throws
}
```

`DocumentSchema` 在同一个 `BlueprintDocument` 上有两个 profile：`CANONICAL` 是上面的显式
IR；`COMPACT` 省略默认值本可提供的东西——`segments`、`assembly`、`bounds`、
`parameters`——把几何写成 `shell`/`cavity`/`fills`/`bays`（§4.7），再物化成同一个显式值。
`AUTO` 是数据包默认：`segments`/`assembly` 选择 canonical，顶层 `shell`/`cavity`/`fills`/`bays`
选择 compact，混用两者是错误。两种 profile 都是无损的：`bounds`、`segments`、`assembly`
与 `extensions` 各有 compact 拼写，因此一份 compact 文档可以写回成 compact。`MIN_VERSION = 1`、
`MAX_VERSION = 2`：v1 文档会被接受，并在文法读取它之前经过 `DocumentUpgrade.v1ToV2`，因此旧
数据包继续可加载，而规范形继续前进。

`Spec` 是唯一的正文形态：`checks[]`、`computations[]` 与 `derives[]` 都是它的列表，而
`SpecResolver` 是 `kind` 的唯一读取者。解析只发生一次，位于
`SegmentLibrary.Builder.apply(resolver, issues)`（§4.6），在服务器线程上，针对冻结的 `Catalogs`；
未知的 `kind` 或错误的 `args` 是该 spec 自身节点上的一个诊断项，绝不是反射查找，也绝不是半成品
单元。

`read`、`assemble`、`fromHost`、`toHost` 或 `write` 返回 `null`，**当且仅当**已向调用方列表追加了
一个 ERROR，且该列表按 §4.4 的规则排序。该列表是本单元唯一的拒绝通道：成功类型（`Blueprint`）
属于 U5，不携带诊断项，在此另造一个结果族会与 `Finding` 重复（I-7、I-14）。本单元不因任何文档
抛异常。

## 4. 语义（Semantics）

### 4.1 文法（Grammar）

`schemaVersion` 是信封，最先读取，这样不支持的版本也有一个可指向的节点。`bounds` 与 `orientation`
不在任务书（brief）的字段清单中，但 `Blueprint` 要求它们，且其默认值很差（缺少策略会静默地表示
“仅单位变换”），因此二者在这份规范文法中都是必填；compact 拼写为它们提供默认值（见下方 §4.1 的
表）。

| 字段 | 类型 | 必填 | 默认值 | 语义 | 是否保留 |
| --- | --- | --- | --- | --- | --- |
| `schemaVersion` | int | 是 | — | 信封；位于 `[MIN, MAX]` 内，v1 升级到 v2。 | 否 |
| `id` | id | 是 | — | 蓝图标识。 | `id` |
| `bounds` | box | 是 | — | 局部坐标系；所有编译出的长方体都在其中。 | `bounds` |
| `parameters` | obj name→scalar | 否 | `{}` | 形参默认值；`{"$param":"n"}` 在此解析。 | `parameters` |
| `segments` | declaration[] | 否 | `[]` | 片段来源；在构建库时读取。 | 合并 |
| `assembly` | placement[] | 是 | — | 哪个片段、放在哪里、以什么名字。 | 否 |
| `anchors` | `AnchorSpec[]` | 是 | — | `{role, property, value}`；格子就是定义原点。 | `anchors` |
| `orientation` | object | 是 | — | `{"front": …}`：作者声明的正面，可选 `turns` 与 `uprightOnly`。 | 是 |
| `plans` | — | 否 | — | 拒绝的键：阶段顺序来自声明，不来自文档。 | 否 |
| `checks` | `Spec[]` | 否 | `[]` | 每个条目一个单元；`bay`、`focus`、`expectation`、`subject` 必填。 | 是 |
| `computations` | `Spec[]` | 否 | `[]` | `phase`（`section`/`aggregate`）、`output`、`inputs`，以及 `bay`（一个舱位的格子）或 `region`（一个盒子）**二选一**；没有 `merge`——它在键上。 | 是 |
| `derives` | `Spec[]` | 否 | `[]` | 一个 `aggregate` 计算加它那个 `context` 键的简写。 | 是 |
| `context` | key[] | 否 | `[]` | `{id, type, retention, merge?, whenAbsent?}`；`merge` 标记一个由段贡献的键。 | 是 |
| `extensions` | obj id→value | 否 | `{}` | 厂商数据：被规范化，但从不解释。 | 否（Q-D5） |

嵌套结构：`segments[].id`/`.bounds` 必填；`.parameters` 为 `name → {default?: scalar}`；`.bays[]`
为 `{id, box, coverage, footprint?, sharing?, description?}`（`coverage` 为 `every_cell`、
`one_subject` 或 `any_subject`；`footprint` 为 `box`、`bound`、`declared` 或 `none`，默认
`bound`；`sharing` 为 `exclusive` 或 `shared`，默认 `exclusive`）；`.seams[]` 为
`{id, box, kind, properties?}`。
`assembly[].as` 匹配 `[a-z0-9_.-]+`，`.segment` 为 `{id, arguments?}`，`.at` 默认 `[0,0,0]`，
`.overlap` 默认 `false`。

box 在两种 profile 中都是 `[originX, originY, originZ, sizeX, sizeY, sizeZ]`——原点加尺寸，绝不是
min/max 角点。`bounds`、`bays[].box`、`seams[].box`、`checks[].focus.cell`、`fills[].box` 与
`cavity[]` 都用它；锚点不声明 box，因为它的格子就是定义原点（`09` §4.5）。

`segments`、`assembly`、`extensions` 以及 `$param` 形式在编译时消失；`Blueprint` 保留匹配会读取的
内容，外加每个值一条 `DocumentProvenance`。编译后的舱位 id 是全局的（`as + ":" + localName`），
因为一条声明以 `NamespacedId` 命名其舱位，而同一片段可能被放置两次；接缝 id 同理。

**v2 文档的 compact 拼写。**

| 规范字段 | compact 拼写 |
| --- | --- |
| `bounds` | `bounds: [ox, oy, oz, sx, sy, sz]`，或从显式 box 推断（§4.7） |
| `segments` + `assembly` | 一个隐式 `main` 放置内的内联 `shell` / `cavity` / `fills` / `bays` |
| `plans` | 不是文档键：阶段的计划由 `bays` 声明的东西展开而来（§4.8） |
| `anchors` | 默认为无：compact 形态不在原点读取任何角色，因此命名了角色的策略会被拒绝（`09` B-4） |
| `orientation` | 两种 profile 中都是同一个对象（§4.1 的规范行）：可完整写出，此处默认为 `{"front": "north"}`——`front` 是恒等朝向，省略 `turns` 表示允许每个 `Turn`（由锚点的朝向选定摆放），省略 `uprightOnly` 不作任何承诺 |
| `checks[]` | `{id, bay, focus: {kind, cell?}, expect: {kind, property?, value?}}`；`expect` 物化进 `args` |
| `computations[]` | `{id, phase, output, inputs?, bay?, region?}` |
| `derives[]` | `derive: [{kind, output, order}]`——没有 `phase`，没有 `inputs`；由解析器填入 |
| `context[]` | 不变：`{id, type, retention, merge?}` 本身就是它的 compact 形态 |
| `extensions` | 不变；每个键都带命名空间 |

键上的 `merge` 表示由段贡献；`phase: section` 要求它，`phase: aggregate` 禁止它。

舱位的 `expect` 是一个检查，其 id 由舱位与下标派生：
`{"expect": {"block": "minecraft:iron_bars"}}`。`bays[].expect` 是一个 `Spec` 正文，因此其中任何
东西都不在此解释。`derives[]` 的条目是同一类 `Spec`；装配把它下沉为
`DerivationSpec(deriver, args, output, order)`（`09` §4.8），因此文档携带统一的正文，而
蓝图携带引用。

### 4.2 DataView：值模型，而非 JSON（DataView: a value model, not JSON）

六种类型，没有浮点：`NUMBER` 是 64 位整数，因此小数以字符串形式书写（`"0.75"`），并可精确往返。
`get(k)` 为 `null`，**当且仅当** `k` 不存在；存在的 `null` 是一个 `NULL` 类型的节点（`keys()`
包含它，`asInt(f)` 返回 `f`），文法从不写出这种节点，在需要值的位置出现它即为 `SC-DOC-0105`。
类型不匹配时 `list()`/`keys()` 为空（`04` §4）。

前端（frontend）是内核不实现的 SPI（I-8、`02` §7）：适配器基于宿主的 JSON 库提供 JSON，基于其
标签解析器提供 SNBT；`kernelTest` 提供一个一次性实现。`read` 在可行处附加 `SourceSpan`；无法附加
的来源（宿主树）令 `span()` 为 null——这是降级 `SC-DOC-0403`，绝不改变判定。`equals` 忽略
`span()`，`write` 输出不含 span 的节点。

### 4.3 规范化（Canonicalisation）

`Canonicalizer.canonical` 与文法无关，因此它也会规范化内核从未听说过的 `extensions` 内容：

1. 对象成员按 Unicode 码点升序，递归进行（使用码点比较器，而非 `String.compareTo`——后者按
   UTF-16 单元排序，在 BMP 之外会不一致）；
2. `NUMBER` 采用最短十进制：不带 `+`、无前导零、`-0` 为 `0`；每个值对应一个 `long`，因此 `1` 与
   `01` 是同一个节点，而 `1.0` 不是 `NUMBER`；
3. `STRING` 字节保持不变——不做 Unicode 规范化，不做大小写或转义折叠；内部空白是内容，因此只有
   前端才能决定空白；
4. `ARRAY` 顺序不变；`NULL`/`BOOLEAN` 本已规范；`span()` 保留。

`DocumentSchema.write` 加上文法层：键序相同，**等于默认值的字段一律省略**，只有对顺序不敏感的
数组才按排序输出。排序发生在 *`BlueprintDocument` 工厂内部*，因此 `equals` 就是内容相等——
`segments` 按 `SegmentKey`，片段内的 `bays` 按 id，`assembly` 按 `as`，`anchors` 按角色，
`context` 按键 id，`derives` 按 `order`，每个映射按 key。逐字保留：`checks[]` 与
`computations[]`——声明顺序是计划的一个打平键（`09` §4.8），排序它们会改变计划——以及每个 box
的 origin/size 配对。

未命名的字段是 `SC-DOC-0104`，绝不丢弃：静默忽略字段正是让拼写错误变成“能用的文档”的方式，
而且更新版本数据包中的字段可能改变我们已读取字段的含义，因此尽力解析（best-effort parsing）
是不可靠的。唯一的开放映射是 `extensions`——带命名空间的键（`SC-DOC-0108`），值不透明。版本
超出范围是 `SC-DOC-0101`，且信封之后的内容一概不读；每个受支持的版本各自保留一个
`DocumentSchema`。

空白只属于前端契约：`write` 仅是规范值的函数，`write(canonical(v))` 与 `write(v)` 字节相同，
`read(write(v))` 与 `v` 内容相等。JSON 前端固定一种风格（两空格缩进、每行一个成员、`": "`、
键升序、标量数组内联、`\n`、末尾一个换行），使重新生成的文档呈现为逐行 diff。

### 4.4 校验（Validation）

`ValidationIssue` 是编译阶段的报告，`Finding` 是匹配阶段的报告：转换是单向的（U9 把 issue 包装为
`DESIGN` 诊断项，其 `Locus` 作为主标签），绝不反向。排序依据依次为 span 起点、诊断码、消息；
严重级别取自诊断码的注册表条目（该表由 U9 拥有），下表中的严重级别列是 U9 采纳的提案。

流水线（`StructureKernel.compile` 组合 1–3）：1. 前端 `read`（语法 → `SC-DOC-0402`，停止）；位于
`(MIN, MAX]` 的信封在此升级。2. 文法 `read`（任何 ERROR 即停止；不合文法的文档没有可检查的
值）。3. `validate` 检查引用、几何、锚点、检查、计算、键、存在性（到上限为止）。
4. `assemble`（ERROR 则停止，返回 `null`）。5. `write` + `canonical`，用于基准文件与重载。

| 诊断码 | 检查 | 严重级别 | 触发条件 |
| --- | --- | --- | --- |
| `SC-DOC-0101` | UnsupportedVersion | ERROR | `schemaVersion` 超出范围。 |
| `SC-DOC-0102` | MissingEnvelope | ERROR | 根节点不是对象，或没有整数版本。 |
| `SC-DOC-0103` | MalformedId | ERROR | 某个 id 不是 `ns:path`。 |
| `SC-DOC-0104` | UnknownField | ERROR | 成员未被 schema 命名。 |
| `SC-DOC-0105` | WrongKind | ERROR | 类型不匹配，或在需要值的位置出现 `NULL`。 |
| `SC-DOC-0106` | MissingField | ERROR | 缺少必填成员。 |
| `SC-DOC-0107` | Truncated | WARNING | 达到上限（512）；尾部未报告。 |
| `SC-DOC-0108` | BadExtensionKey | ERROR | `extensions` 的键不是 `NamespacedId`。 |
| `SC-DOC-0201` | UnresolvedParameter | ERROR | `{"$param":"x"}` 未指向任何参数。 |
| `SC-DOC-0202` | UndeclaredArgument | ERROR | 片段未声明该参数。 |
| `SC-DOC-0203` | MissingArgument | ERROR | 无默认值的参数未获得值。 |
| `SC-DOC-0204` | ArgumentKindMismatch | ERROR | 实参类型 ≠ 默认值类型。 |
| `SC-DOC-0205` | UnknownBayReference | ERROR | 某个 `checks[].bay` 未指向蓝图的任何舱位。 |
| `SC-DOC-0206` | DeriveOrderDuplicate | ERROR | 两个 `derives` 共用同一个 `order`。 |
| `SC-DOC-0207` | DuplicateDerive | ERROR | 两个 `derives` 声明同一个 `output` 键。 |
| `SC-DOC-0208` | OutputKeyUndeclared | ERROR | 某个 spec 的 `output` 在其阶段没有对应的 `context` 条目。 |
| `SC-DOC-0209` | KeyTypeMismatch | ERROR | 某个 spec 产出的类型与其键声明中的 `type` 不同。 |
| `SC-DOC-0301` | DuplicateSegmentKey | ERROR | 同一个键有两份内容不同的声明或条目。 |
| `SC-DOC-0302` | SegmentMissing | ERROR | 某装配键在库中解析不到片段。 |
| `SC-DOC-0303` | DuplicatePlacement | ERROR | 两个装配条目共用 `as`。 |
| `SC-DOC-0401` | HostConversionFailed | ERROR | `DataBridge` 无法转换某个节点。 |
| `SC-DOC-0402` | SyntaxError | ERROR | `DataFrontend` 无法解析文本。 |
| `SC-DOC-0403` | NoPositions | INFO | 来源未产生任何 span。 |
| `SC-DOC-0501` | OutsideBounds | ERROR | 某次放置或某个长方体越出 `bounds`。 |
| `SC-DOC-0502` | SegmentOverlap | ERROR | 两次放置相交，且并非双方都选择允许。 |
| `SC-DOC-0503` | NamedBaySplit | ERROR | 一个具名舱位不是单个长方体；由作者拆分它。 |
| `SC-DOC-0504` | DuplicateDeclaration | ERROR | 某个格子被展开顺序（§4.7）声明两次。 |
| `SC-DOC-0505` | EmptyCuboid | ERROR | 某一个尺寸分量为零或负。 |
| `SC-DOC-0521` | UnreadableBlockEntity | ERROR | 捕获到的方块实体的内容无法读取；导入拒绝该方块实体（`21` §7）。 |
| `SC-DOC-0601` | DuplicateAnchorRole | ERROR | 两个锚点声明同一个角色。 |
| `SC-DOC-0602` | AnchorUnresolved | ERROR | 策略的 `primaryRole` 没有 `anchors[]` 条目。 |
| `SC-DOC-0603` | MissingStagePlan | retired | `PROBE` 或 `FORM` 没有计划。已废止：作者不声明阶段，文档也不命名任何计划，因此此处没有任何东西能触发它（`23` §7）。 |
| `SC-DOC-0604` | SpecStageMismatch | retired | 某个 spec 的 `stage` 无法承载它的种类（`DESIGN` 不派发任何东西）。已废止：文档不再携带 `stage` 字段（`23` §7）。 |
| `SC-DOC-0605` | AmbiguousExpectTag | ERROR | 一个 `expect` 对象带了两个标签；紧凑写法只允许一个。 |
| `SC-DOC-0606` | EmptyCombinator | ERROR | `allOf`/`anyOf` 没有子项；组合器必须断言些什么。 |

计算的*环*由 `inputs` 拒绝：`09` §4.8 的 Kahn 序不允许环，而 `SC-CHK-0102` 的 DESIGN 孪生指向
闭合它的那个 spec。此处仍然不可见的是目录条目自身声明的输入与类型，因为本单元从不导入注册表
（`02` §2）；`SpecResolver` 在能拿到目录的地方检查这些（§4.6）。

### 4.5 装配（Assembly）

`SegmentAssembler.of(library).assemble(document, issues)` 是纯净的：无世界视图、无文件、无时钟、
无缓存。

1. 用 `parameters` 替换 `$param` 引用；构造 `SegmentKey`（文档标量的 token 是 `String`、`Long` 或
   `Boolean`，Q-D4）；`lookup`。未命中是 `SC-DOC-0302`，附带一条列出库中该 id 各实参化
   （argumentisation，已排序、最多 8 条）的注释，并返回 `null`——绝不抛异常，绝不留空洞。
2. 检查 `as` 的唯一性（`SC-DOC-0303`）与 `bounds` 的包含关系（`SC-DOC-0501`）。
3. 以 x 区间扫描检查足迹（footprint）不相交。相交的一对是 `SC-DOC-0502`，除非**两个**条目都设置
   `overlap: true`；此时这些格子留待匹配时由共享声明处理（I-10）。相同的 `as` 一律
   拒绝，不做择优。
4. 按 `at` 平移每个舱位与接缝的长方体，把 id 改写为 `as + ":" + localName`，并以同样的方式派生
   的 id 把每个舱位的检查重新指向放置后的舱位；平移后越出 `bounds` 是 `SC-DOC-0501`。
5. 按（放置顺序、局部顺序）合并：舱位、接缝、锚点、检查、计算、派生、键声明。`Spec` 的
   `bay`、`output` 与 `inputs` 以编译后的 id 书写，因此只做校验，不做改写。
6. 写入来源：每个舱位与接缝、以及每个 `Spec` 都携带它声明节点的来源——
   `DocumentProvenance(document.id(), span)`——而蓝图携带根节点的。在*本文档*
   内声明的片段，其舱位取该声明的 span；来自别处的舱位取放置条目的 span——指向另一个文件的区间
   会定位到错误的字节。
7. `Blueprint.builder(id)` … `build()`：`build()` 所检查的每个前置条件都已通过，因此那里发生拒绝
   属于内核缺陷，应在测试中断言，而不是文档层面的结果。

唯一的顺序来自文档工厂所固定的顺序，没有任何步骤迭代哈希序结构，扫描在已排序的区间上进行——
因此蓝图只依赖 `(document, library)`，不依赖来源映射的键序（P6）。

### 4.6 库、发射器、桥与来源（Library, emitter, bridge, provenance）

**`SegmentLibrary`** 在 `apply(...)` 之后不可变、可共享，并由宿主提供的 `revision()` 为人标记
（绝不是时钟，也绝不参与相等性）。`SegmentKey` 的身份*包含*其 `Arguments`，因此两种实参化是两个
条目，而不是一个版本。内容相等的重复键会合并；内容不同的重复键是 `SC-DOC-0301`，且该键在应用
后的库中**缺席**（两者都不胜出）。重载会整体替换引用。

**`SpecResolver`** 是注册表安装的阶段。`put(SegmentDocument, issues)` 只存储它解析出的 `Spec`；
`apply(resolver, issues)` 每次重载运行一次，在服务器线程上，使用 `spi` 实现所持有的冻结
`Catalogs`，并且是 `kind` 被读取的唯一位置。它校验 kind、针对该 kind 的 schema 校验实参、针对
解析出的 focus/expectation 对校验 `subject`、校验键声明以及 Kahn 序所需的 `inputs`，并对无法解析
的 spec 返回 `null`——ERROR 已经在 `issues` 中。本单元没有任何成员反射地查找类型，因此数据包无法
到达宿主未注册的类。

**`SegmentEmitter`** 是生成契约：一次调用、一条片段、一个子树写入 `DataView.Builder`，
`schemaVersion` 等于当前版本，排序稳定（由无序集合驱动输出即为缺陷），正文满足 `isCanonical`。
往返是一条定律：读取发射出的文档并装配它，唯一放置的舱位等于发射片段自身的舱位（去掉前缀）
（P6）。

**`DataBridge<T>`** 进出搬运宿主树，而内核无需命名它：`fromHost` 产生不含 span 的值，`toHost`
接收规范视图，失败是 `SC-DOC-0401`，内核只调用这两个方法——不使用反射。宿主桥内部可以使用反射；
随附的适配器做直接的类型化遍历。

**`DocumentProvenance`** = `(source, @Nullable range)`，`range` 是值在 `source` 中占据的半开字符
区间：它让 `FORM` 诊断可以说“这就是你在 [368, 455) 处写的那个舱位”。不含 span 的来源保留
`source`、`range` 为 null，U9 的标签降级为不带文本的 `Locus`。

### 4.7 下沉（Lowering）

下沉是一个函数：一份文档产出一个值，且展开顺序固定——`shell` → `cavity` → `fills` → `bays`。
一个格子由恰好一个表面声明；同一格子的第二次声明是 `SC-DOC-0504`，绝不是静默覆盖，也绝不是
后者胜出的合并。`shell`/`cavity`/`fills` 在 `bays` 之前应用，因此伸进具名舱位的填充是 ERROR，
而不是降级为别的东西。

- 一个**具名舱位**整体发射。一个具名舱位若在文档形态中是多个 box，即为 `SC-DOC-0503`，由作者
  拆分它；编译器绝不拆分具名舱位。
- 一个未命名的**带孔外壳**下沉为若干个合成舱位，每个 id 由它自己的 box 派生：
  `#box@x,y,z:sx,sy,sz`。绝不用计数器——第二个 box 不得改变第一个 box 的 id——也绝不把六个 box
  压成一个 id。
- `SC-DOC-0505` 即 `EmptyCuboid`：某个 box 的尺寸分量为零或负。一个会被推断为空的 `bounds` 使用
  同一个诊断码，绝不是“一个空结构”。
- `SC-DOC-0503` 保留给具名舱位的拆分；`SC-DOC-0504` 保留给第二次声明。

compact profile 唯一的隐式放置名为 `main`，因此下沉后的舱位是 `main:<localName>`。下游的一切看到的
都是规范文档所产出的那个 `Blueprint`：下沉发生在文法的 `read` 内部，后续阶段不再重新应用任何
compact 默认值。

## 5. 参考代码（Reference sketch）

```java
static final String FURNACE = """
{ "schemaVersion": 2, "id": "mymod:huge_furnace",
  "parameters": {"mouthWidth": 3},
  "bounds": [0, 0, 0, 9, 7, 9],
  "shell": [{"box": [0, 0, 0, 9, 7, 9], "block": "minecraft:bricks"}],
  "cavity": [[1, 1, 1, 7, 5, 7]],
  "bays": [{"id": "wall", "box": [0, 0, 0, 9, 1, 9], "coverage": "every_cell",
            "sharing": "shared", "footprint": "bound",
            "expect": {"block": "minecraft:bricks"}},
           {"id": "mouth", "box": [3, 1, 0, 1, 2, 1], "coverage": "one_subject",
            "sharing": "exclusive", "footprint": "bound"}],
  "anchors": [{"role": "mymod:primary", "property": "minecraft:facing", "value": "east"}],
  "orientation": {"front": "north", "uprightOnly": true,
                  "turns": ["NONE","CLOCKWISE","HALF","COUNTERCLOCKWISE"]},
  "checks": [{"id": "mymod:door", "bay": "main:mouth",
              "focus": {"kind": "door", "cell": [3, 1, 0]},
              "expect": {"kind": "state", "property": "facing", "value": "east"}}],
  "computations": [{"id": "mymod:volume", "phase": "section",
                    "output": "mymod:volume", "bay": "main:wall",
                    "kind": "count", "args": {"mode": "cells"}},
                   {"id": "mymod:capacity", "phase": "aggregate",
                    "output": "mymod:capacity", "inputs": ["mymod:volume"],
                    "kind": "scale", "args": {"factor": 4}}],
  "context": [{"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
              {"id": "mymod:capacity", "type": "long", "retention": "context"}],
  "extensions": {"mymod:display": {"name": "Huge Furnace"}} }
""";

void compile(DataFrontend json, SegmentLibrary library) {
    List<ValidationIssue> issues = new ArrayList<>();
    DataView root = json.read(FURNACE, issues);                     // 1. text → value
    BlueprintDocument doc = root == null ? null : DocumentSchema.current()
            .read(NamespacedId.of("mymod", "huge_furnace"), root,
                    DocumentProfile.AUTO, issues);
    if (doc == null) { report(issues); return; }                    // grammar refused

    DataView canonical = Canonicalizer.of().canonical(
            DocumentSchema.current().write(doc, DocumentProfile.CANONICAL));
    assert Canonicalizer.of().isCanonical(canonical);               // P2, P3
    assert DocumentSchema.current().read(doc.id(), canonical, DocumentProfile.CANONICAL,
            new ArrayList<>()).equals(doc);                         // P1

    issues.addAll(DocumentValidator.of(library).validate(doc));     // 2. design checks
    Blueprint bp = SegmentAssembler.of(library).assemble(doc, issues);
    if (bp == null) {                                               // 3. the failure path
        // e.g. SC-DOC-0208 "mymod:capacity names no context key", locus [412, 470)
        report(issues);
        return;
    }
    Bay wall = bp.bay(NamespacedId.of("main", "wall"));              // 4. the implicit placement
    SourceSpan at = bp.sourceOf(wall.id());                         // Q-D2: the declaration's span
}

// The library is installed once, with the registry in hand, on the server thread.
SegmentLibrary reload(SegmentDocument shellDoc, SpecResolver resolver) {
    List<ValidationIssue> issues = new ArrayList<>();
    SegmentLibrary library = SegmentLibrary.builder(41L)             // host revision, not a clock
            .put(shellDoc, issues)                                   // stores specs; resolves nothing
            .apply(resolver, issues);                                // §4.6: kind, args, outputs
    if (!issues.isEmpty()) report(issues);                           // e.g. SC-SPI-0002
    return library;
}

// A host bridge. HostNode is the host's tree; no kernel type names it.
sealed interface HostNode permits HostObject, HostArray, HostScalar {}
record HostObject(Map<String, HostNode> m) implements HostNode {}
record HostArray(List<HostNode> e) implements HostNode {}
record HostScalar(DataKind kind, String text, long number, boolean flag) implements HostNode {}

record HostBridge() implements DataBridge<HostNode> {
    @Override public NamespacedId id() { return NamespacedId.of("mymod", "host_tree"); }

    @Override public @Nullable DataView fromHost(HostNode n, List<ValidationIssue> issues) {
        return switch (n) {
            case HostObject o -> {
                var b = DataView.builder();
                o.m().forEach((k, v) -> b.put(k, fromHost(v, issues)));   // null member → issue
                yield b.build();
            }
            case HostArray a -> {
                var b = DataView.builder();
                a.e().forEach(e -> b.add(fromHost(e, issues)));
                yield b.build();
            }
            case HostScalar s -> switch (s.kind()) {
                case STRING -> DataView.string(s.text());
                case NUMBER -> DataView.number(s.number());      // no float in the model (Q-D4)
                case BOOLEAN -> DataView.bool(s.flag());
                case NULL -> DataView.nil();
                case OBJECT, ARRAY -> null;                      // a host float or byte array
            };
        };
    }

    @Override public @Nullable HostNode toHost(DataView v, List<ValidationIssue> issues) {
        return switch (v.kind()) {
            case OBJECT -> new HostObject(v.keys().stream().collect(LinkedHashMap::new,
                    (m, k) -> m.put(k, toHost(v.get(k), issues)), Map::putAll));
            case ARRAY -> new HostArray(v.list().stream().map(e -> toHost(e, issues)).toList());
            case STRING -> new HostScalar(DataKind.STRING, v.asString(""), 0, false);
            case NUMBER -> new HostScalar(DataKind.NUMBER, "", v.asInt(0), false);
            case BOOLEAN -> new HostScalar(DataKind.BOOLEAN, "", 0, v.asBoolean(false));
            case NULL -> new HostScalar(DataKind.NULL, "", 0, false);
        };
    }
}
```

## 6. 用例（Use cases）

**插件作者。** 写一次带 `width` 参数的 `mymod:shell`，用不同的 `$param` 值放置三次，运行
`/cumulus validate`，在 `anchors[0]` 得到 `SC-DOC-0602`——策略的 `mymod:primary` 角色没有
`anchors[]` 条目——并附带该条目的字符区间。

**宿主 / 适配器。** 数据包重载通过 `put` + `apply(resolver)` 构建 `SegmentLibrary`，注册 JSON
前端与宿主桥，并按 `(id, library.revision())` 缓存蓝图。客户端编辑器通过 `toHost`/`fromHost`
往返文档；两者以 `equals` 比较，编辑器的字段顺序绝不会传到蓝图。

**外部工具。** 结构导出器对每个发现的区域通过 `SegmentEmitter` 发射一条片段，输出规范 JSON；
加载器原样读取、校验并装配它。

## 7. 证明（Proofs）

**P1 — 往返恒等。** `read(source, write(d), ∅)` 等于 `d`：`write` 以唯一精确拼写输出每个非默认
字段，`read` 应用相同的字段集合、默认值表（§4.1）与规范化工厂，因此两者互逆。`write` 只省略
不可变默认值（一个共享的 `CheckPlan` 实例、一个空键列表），所以被省略的值在重读后不可能不同。v1
文档不是 `d`：它是 `v1ToV2` 的输出，P1 适用于升级后的值。

**P2 — 规范唯一性。** 若 `d1 ≅ d2`，则 `write(d1).equals(write(d2))`：`≅` *就是* record 相等，
而 `write` 是它的全函数——不依赖映射迭代顺序（每个映射都是排序副本），不依据输入拼写分支，
`checks[]`/`computations[]` 按声明顺序。`canonical(write(d))` 等于 `write(d)`，因为 `write` 已
输出有序键与规范数值：即把 P3 应用于其输出。

**P3 — 幂等性。** 按 `DataKind` 分类讨论。`OBJECT`：对已排序的键序列再排序即为恒等，递归由归纳法
幂等。`ARRAY`：顺序不变，递归由归纳法。`NUMBER`：最短十进制形式的最短十进制形式仍是自身。
`STRING`/`BOOLEAN`/`NULL`：不变。`canonical` 不增删任何成员。反例需要某条规则读取输入的*拼写*；
没有规则如此。

**P4 — 校验的可靠性与完备性。** *可靠*：每个检查都是关于 `(document, library)` 的纯谓词，诊断码
只在其成立的分支中发出，因此没有虚假诊断。*完备*（按所列检查）：文法（0101–0108）、引用
（0201–0209、0302、0601–0602）、几何（0501–0505）与存在性（0301、0303）都在文档与库完全暴露的
数据上判定，因此违规必到达其分支。三处被明确标注的边界，而不是检查缺口：`Spec` 的 `kind` 只在
`SpecResolver` 中解析（§4.6），目录条目自身声明的输入与类型需要 `spi`（`02` §2），而展开是一个
函数（§4.7），其重复声明规则是 `SC-DOC-0504`，不是一个校验检查。

**P5 — 无宿主依赖。** 内核只命名 `DataView`、`DataBridge<T>`（`T` 被擦除）与 `DataFrontend`；
它不导入任何 `com.google.gson`、`net.minecraft.nbt` 或 `com.mojang.serialization` 类型，不触碰
任何反射成员，`T` 仅作为类型变量存在。`SpecResolver` 的实现与 `Catalogs` 一起位于 `spi`，因此
`document` 不导入任何注册表类型。`compileKernelJava` 证明这一点，`kernelPurity` 兜底。

**P6 — 装配确定性、发射器往返。** 对 §4.5 与 §4.7 归纳：编译只读取其参数；迭代到的每个集合都已
由文档工厂排序，或处于声明顺序；展开顺序固定，扫描与合并都有明确的决胜规则；库不可变且冲突键
缺席。因此两次运行——以及仅在来源映射顺序上不同的两份文档——产生 `equals` 的蓝图。对于发射器：
其子树按契约是规范的，因此对它 `read` 得到一个单放置文档，其 key 与 bays 由 P1 等于 `s` 的；装配
该文档得到的蓝图，其放置的舱位在前缀下等于 `s.bays()`。

## 8. 依赖与待决问题（Dependencies and open questions）

| # | 需求 | 归属 |
| --- | --- | --- |
| Q-D1 | `DataView` 增加 `span()`，`keys()` 返回 `SequencedSet`；术语表相应行必须跟进。 | U0 |
| Q-D2 | **已解决**：`Blueprint.sourceOf(NamespacedId)` 返回每个舱位/接缝的 `@Nullable SourceSpan`，`BlueprintBuilder.source(...)` 写入它。`Bay`/`Seam` 保持不依赖文档类型，因此没有 `blueprint → document` 循环。 | U5 |
| Q-D3 | **已解决**：编译后的舱位 id 是 `<placement>:<localName>`——compact profile 的隐式放置为 `main`——而 `Spec` 的 `bay` 指向该 id。 | U5 |
| Q-D4 | 文档标量映射为 `String`/`Long`/`Boolean`；声明 `Integer` 型 `ParameterKey` 的片段永远无法从文档放置。采用规范数值 token，还是 `asLong`？ | U0, U5 |
| Q-D5 | **已解决**：`extensions` 不会在编译后保留；`write` 会往返它，其他任何东西都不读取它。 | U5 |
| Q-D6 | **已解决**：`SegmentLibrary.Builder.put(SegmentDocument, issues)` 存储 spec，`apply(SpecResolver, issues)` 解析它们；`SpecResolver` 接口在此声明、在 `spi` 中实现，因此 `document` 不命名任何目录类型。 | U3, U5, U11 |
| Q-D7 | **已解决**：`SpecResolver` 校验 `kind`、`args`、`subject`、键声明与 `inputs`；未知的 kind 是 `SC-SPI-0002`，类型错误的实参是 `SC-DOC-0105`/`0106`，未声明的输出是 `SC-DOC-0208`。 | U11 |
| Q-D8 | §4.3 的 JSON 空白约定与 SNBT 数值/转义约定在此是契约文本，在 `16` 中是实现文本；`kernelTest` 前端仅用于测试。 | A1 |

待决，来自 `22` §16：

- **Q-D9** 空体积保护（`§16` Q1）：一个蓝图级的 `sealed: true` 是否优于逐舱位的 `footprint: box` 加上
  `SC-CLM-0104` WARNING？一个标志会成为第一个重写舱位已声明 `footprint` 的文档字段。
- **Q-D10** `whenAbsent`（`§16` Q3）：它是 `ContextKey` 的字段（`22` §6.4），写在 `context[]` 条目上
  并针对 `type` 解码；只有该区域的每个段都可读时才被采纳。对一个可选的装饰性区域来说，这是否
  过严？
- **Q-D11** compact 计算（`§16` Q5）：`context[]` + `computations[]` 为一个数字要两条条目。`derive`
  是目前唯一的简写——`count`/`volume` 是否也该有一个？

## 9. 证伪（Falsification）

| # | 测试 | 失败则否定 |
| --- | --- | --- |
| F1 | 两份内容相同的文档，每个对象的成员均逆序：断言两次 `read` 结果 `equals`，两次 `write` 结果相等。 | P2，规范文件的前提。 |
| F2 | `assembly` 指向 `mymod:missing`：断言 `assemble` 返回 `null`，`issues` 含 `SC-DOC-0302`，无异常逸出。 | I-14，§4.5 步骤 1。 |
| F3 | 某舱位携带 `"bayColour": "red"`：断言报 `SC-DOC-0104`，`read` 返回 `null`，任何编译后的值都不含它。 | 禁止静默丢弃规则。 |
| F4 | `"schemaVersion": 3`：断言报 `SC-DOC-0101`，`read` 返回 `null`，信封之后的内容一概不读。 | 信封优先读取、封闭版本范围。 |
| F5 | 一份 v1 文档，使用 min/max box 与 `expectation` 正文：断言 `v1ToV2` 运行、`read` 成功，且值等于同一结构的 v2 拼写。 | 可接受的 v1 文档，§4.1 的版本行。 |
| F6 | 一个 `fill` 的 box 与具名舱位重叠，或两个 `shell` box 覆盖同一个格子：断言报 `SC-DOC-0504`，`read` 返回 `null`。 | 展开是一个函数（§4.7）。 |
| F7 | 一个具名舱位被写成两个 box：断言报 `SC-DOC-0503`，绝不是两个舱位，也绝不是一个合并后的舱位。 | 具名舱位的身份。 |
| F8 | `checks[0].kind` 指向没有任何目录注册的类：断言 `apply` 报 `SC-SPI-0002`，没有反射查找，也没有库条目。 | §4.6，`SpecResolver` 是 `kind` 的唯一读取者。 |
| F9 | 向发射器输入由 `HashMap` 支撑的参数：断言两次运行经规范写入后字节相同。 | §4.6 的稳定排序义务。 |
| F10 | 用按不同插入顺序应用的库各装配同一文档一次：断言蓝图 `equals` 且舱位顺序相同。 | P6，库不可变。 |

最省力的审阅攻击：构造一份语义依赖 `segments` 数组顺序的文档。若存在这样的文档，则 §4.3 的
顺序不敏感结论有误，`segments` 必须加入“保留顺序”的列表。
