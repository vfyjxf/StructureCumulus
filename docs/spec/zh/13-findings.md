# 13 —— 诊断项（Findings）

> 状态：设计草案 v0.1，2026-10-20。单元 **U9**（`dev.vfyjxf.cumulus.kernel.finding`）。
> 服务于 R-07、R-12、R-14；落实 I-7、I-14。先例：ArchitectCumulus `34`–`37`、
> `08-prior-art-diagnostics.md`、`docs/design/diagnostics.md` —— 仅作参考，以术语表与
> 基础文件为准。

## 1. 范围

拥有失败报告的*值*，以及把一组报告变成人、日志与 CI 任务各自都能读的东西的机制：`Finding`
及其组成部分、稳定的诊断码词汇、有序／去重／截断的 `FindingBundle`、`FindingSink` 契约、
三种渲染投影，以及按诊断码组织的长文本（`ExplainBook`）。

不拥有：

| 不在本单元 | 归属方 |
| --- | --- |
| 哪个检查在哪个阶段抛出哪个诊断码 | U3、U4、U5、U7、U8 |
| `Locus`、`LocusFrame`、`CuboidSet` 及其坐标系映射 | U1 |
| 诊断码→`Explanation` 注册表实例集与 `Codes` 常量 | U11 |
| `Remedy`、`EditPlan`、`OverlayModel`；建议的具体修复 | U10（前向引用：`Suggestion.remedy` 只给出提供者 id） |
| 本地化目录、终端检测、日志追加器 | 宿主 / A1 |
| 抑制（`#[allow]` 形式）、抑制注册表 | 不建模（§4.1） |

本单元不得破坏的一条结构规则：**诊断项是值，绝不是发射器的副作用。** 生产者把诊断项交给
接收器；接收器从不做决定；只有 `build()` 负责排序、去重与截断。

## 2. 术语

所有行均来自术语表 §K。本文件引入的两个名字是 U9 局部的、但被宿主引用；两者都已有行。

| 名字 | 形状 | 拥有 |
| --- | --- | --- |
| `Severity` | 枚举 `ERROR, WARNING, INFO` | 一条诊断项的分量；声明顺序即强度降序 |
| `FindingCode` | record `(Family family, int number)` | 稳定标识 `SC-<FAMILY>-<NNNN>`；`Family` 是嵌套的 15 值枚举 |
| `Finding` | record，9 个组件（`id`、`code`、`severity`、`message`、`labels`、`notes`、`suggestion`、`related`、`stage`） | 一条报告；自行校验并规范化 |
| `FindingId` | record `(int value)` | 在单个诊断包内的可寻址性，用于 `related` |
| `Label` | record `(Locus, String, LabelRole)` | 一条带位置的陈述；跨度位于其 `Locus` 中 |
| `LabelRole` | 枚举 `PRIMARY, SECONDARY, CONTEXT` | 一个标签断言了什么 |
| `Note` | record `(String, @Nullable Locus)` | 一条没有自己格子的事实（一个属性名） |
| `Suggestion` | record `(String, Applicability, @Nullable NamespacedId)` | 一个提议的改动，不含计划 |
| `Applicability` | 枚举，4 个梯级 | 该提议有多安全；只有最高梯级可自动应用 |
| `SourceSpan` | record `(NamespacedId document, int start, int end)` | 单个文档中的半开字符区间 |
| `FindingBundle` | 接口 + `Builder` | 一次尝试的有序、去重、截断的报告 |
| `FindingSink` | 接口 `accept`、`acceptAll` | 一个从不做决定的推送型消费者 |
| `FindingOptions` | record `(int maxTotal, int maxPerCode, boolean includeInfo, boolean includeNotes)` | 发射旋钮 |
| `FindingRenderer` | 接口 `render(Finding, RenderContext)` | 到文本的纯投影 |
| `ExplainBook` | 接口 `explain` | 诊断码→长文本的注册表视图 |
| `Explanation` | record `(code, summary, body, seeAlso)` | 一个诊断码的长文本 |
| `RenderContext` | record（U9 局部） | 来源、宽度、颜色、区域 —— 渲染所读取的一切 |
| `SourceText` | record（U9 局部） | 一个文档的文本加上偏移→行映射 |

`Locus`、`LocusFrame`、`CuboidSet`、`Stage`、`NamespacedId` 是被引用、而非被拥有。假定
`Locus` 为 `(LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan span)`
并具有这些访问器；`LocusFrame` 按该声明顺序为 `DEFINITION, LOCAL, WORLD, KEY`。
一个 `KEY` 定位域在其 `key` 中携带 `(InstanceId, NamespacedId)` —— 实例与该 `ContextKey` 的
id —— 并带**空**的 `cells`，没有跨度。

## 3. API 设计（API design）

```java
package dev.vfyjxf.cumulus.kernel.finding;

public enum Severity {
    ERROR, WARNING, INFO;
    public int rank();                              // ERROR 0, WARNING 1, INFO 2
    public boolean atLeast(Severity floor);
}

public record FindingCode(Family family, int number) implements Comparable<FindingCode> {
    public enum Family {
        SPC, OCC, EXP, FOC, CLM, BPT, BAY, CHK, DOC, ENG, CTX, DIA, RMD, SPI, MC;
        public String subject();                    // "space", "claims", "findings", …
    }
    public static FindingCode of(Family family, int number);   // 1..9999, else IAE
    public static FindingCode of(String text);                  // parse or IAE; UI/sketch convenience
    public static @Nullable FindingCode parse(String text);    // null when malformed
    public String text();                                      // "SC-EXP-0001"
    @Override public int compareTo(FindingCode other);         // family ordinal, then number
}

public record SourceSpan(NamespacedId document, int start, int end) {   // half-open, 0 <= start <= end
    public static SourceSpan of(NamespacedId document, int start, int end);
    public int length();
    public boolean contains(SourceSpan other);
    public boolean contains(int offset);
}

public enum LabelRole {
    PRIMARY, SECONDARY, CONTEXT;
    public int rank();                                         // 0, 1, 2
}

public record Label(Locus locus, String message, LabelRole role) {
    public static Label primary(Locus locus, String message);
    public static Label secondary(Locus locus, String message);
    public static Label context(Locus locus, String message);
}

public record Note(String message, @Nullable Locus locus) {
    public static Note of(String message);
}

public enum Applicability {
    MACHINE_APPLICABLE, MAYBE_INCORRECT, HAS_PLACEHOLDERS, UNSPECIFIED;
    public int rank();                                         // 0..3, ladder top first
}

public record Suggestion(String message, Applicability applicability,
                         @Nullable NamespacedId remedy) {}      // the id of a RemedyProvider

public record FindingId(int value) {
    public static final FindingId NONE = new FindingId(0);
    public static FindingId of(int value);                      // value > 0, else IAE
    public boolean assigned();
}

public record Finding(FindingId id, FindingCode code, Severity severity, String message, List<Label> labels,
                      List<Note> notes, @Nullable Suggestion suggestion,
                      List<FindingId> related, Stage stage) {
    public Finding { /* validates, copies, sorts labels — §4.2 */ }
    public static Builder builder(FindingCode code);
    public @Nullable Label primaryLabel();
    public List<Label> labelsOf(LabelRole role);
    public boolean hasPrimary();
    public Finding withSuggestion(Suggestion suggestion);
    public Finding withRelated(List<FindingId> related);
    public Finding withId(FindingId id);          // re-mint a cached finding

    public static final class Builder {
        public Builder id(FindingId id);
        public Builder severity(Severity severity);
        public Builder message(String message);
        public Builder label(Label label);
        public Builder label(Locus locus, String message);      // PRIMARY
        public Builder label(Locus locus, LabelRole role, String message);   // explicit role
        public Builder secondary(Locus locus, String message);
        public Builder note(String message);
        public Builder note(String message, Locus locus);
        public Builder suggest(String message, Applicability applicability, @Nullable NamespacedId remedy);
        public Builder related(FindingId id);
        public Builder stage(Stage stage);
        public Finding build();                                 // IAE on a defect — §4.2
    }
}

public interface FindingBundle {
    List<Finding> findings();                                   // canonical order
    int size();
    boolean isEmpty();
    @Nullable Finding primary();                                // findings().get(0), or null
    int elided();
    int filtered();
    FindingOptions options();
    List<Finding> ofCode(FindingCode code);
    List<Finding> ofSeverity(Severity severity);
    FindingBundle filter(FindingOptions narrower);

    static FindingBundle empty();
    static FindingBundle of(List<Finding> findings, FindingOptions options);   // list order = insertion order
    static Builder builder(FindingOptions options);

    interface Builder extends FindingSink {
        Builder add(Finding finding);
        FindingBundle build();                                  // idempotent
    }
}

public interface FindingSink {
    void accept(Finding finding);
    default void acceptAll(FindingBundle bundle);               // bundle order, no re-filtering
    static FindingSink noop();
    static FindingSink guarded(FindingSink delegate, java.util.function.Consumer<RuntimeException> onFailure);
}

public record FindingOptions(int maxTotal, int maxPerCode, boolean includeInfo, boolean includeNotes) {
    public static FindingOptions defaults();                    // MAX_VALUE, MAX_VALUE, true, true
    public FindingOptions narrowedBy(FindingOptions other);      // min of caps, both booleans by AND
    public FindingOptions withMaxTotal(int maxTotal);            // bounds WARNING/INFO, never ERROR
    public FindingOptions withMaxPerCode(int maxPerCode);        // bounds WARNING/INFO, never ERROR
}

public interface FindingRenderer {
    String render(Finding finding, RenderContext context);
    default String render(FindingBundle bundle, RenderContext context);   // newline join
    static FindingRenderer human();
    static FindingRenderer json();
    static FindingRenderer sarif();
}

public record RenderContext(List<SourceText> sources, @Nullable NamespacedId document,
                            int width, ColorMode color, Locale locale) {
    public enum ColorMode { PLAIN, ANSI }
    public static RenderContext plain();                        // no sources, width 0, PLAIN, Locale.ROOT
    public static RenderContext of(SourceText source);
    public @Nullable SourceText sourceFor(NamespacedId id);
    public RenderContext withWidth(int width);
}

public record SourceText(NamespacedId id, String text) {
    public static SourceText of(NamespacedId id, String text);
    public @Nullable String lineAt(int offset);                 // null outside [0, length]
    public int lineNumber(int offset);                          // 1-based
    public int column(int offset);                              // 1-based, in code points
}

public interface ExplainBook {
    @Nullable Explanation explain(FindingCode code);
    @Nullable Explanation explain(NamespacedId topic);
    List<FindingCode> codes();
    Explanation explainOrFallback(FindingCode code);             // never null — §4.8
}

public record Explanation(FindingCode code, String summary, String body,
                          List<NamespacedId> seeAlso) {}
```

| 契约 | 规则 |
| --- | --- |
| `Finding` 是 record，不是由构建器构造的不透明类型 | 去重与不涉及 `related` 的测试读的是结构相等；9 个组件、多数为必需，因此 03 §4 仍将其定为 record |
| `Finding.Builder` 仅为易用而存在 | 它只产出一个 `Finding` 后即被丢弃；它不是第二种值类型，也不引入 `Finding` 能持有的新状态 |
| 除违反程序员前置条件的 `of(…)`/`build()` 外，没有成员抛异常 | I-14；本单元任何地方都没有抛异常的桩，也没有 `UnsupportedOperationException` |
| 每个返回列表的成员都返回不可变副本，绝不返回 `null` | 04 §4 |
| 除值本身外，`RenderContext` 是渲染器的*唯一*输入 | 没有任何环境依赖，没有时钟、没有环境、没有目录查找（§4.7） |

## 4. 语义

### 4.1 rustc 的映射，以及被拒绝的部分

| rustc | StructureCumulus | 裁定 |
| --- | --- | --- |
| 级别 `error`/`warning`/`help`/`note` | `Severity`（3 个值） | 借用形状（BORROW-SHAPE）：`help` 与 `note` 是字段（`Suggestion`、`Note`），不是级别 —— 注释不是更轻的错误 |
| 诊断码 `E0123`，lint 中可选 | `FindingCode`，必需 | 拒绝可选性（REJECT optionality）：任何消费者都不应需要消息文本（I-7），因此每条诊断项都给出自己的诊断码 |
| 扁平的全局诊断码 | `SC-<FAMILY>-<NNNN>` | 拒绝扁平（REJECT flat）：十五个族让读者看出哪个单元失败，并让一个族的编号由单一归属方分配 |
| 消息 | `Finding.message` | 一行散文，从不解析，除 §4.5 的注释情形外从不作为键的一部分 |
| 主跨度 + 标签 | `Label(PRIMARY)` | 至多一个（§4.2） |
| 次跨度 + 标签 | `Label(SECONDARY)` | 任意多个；补充主标签说不出的内容 |
| 跨度自带的子级别 | — | 拒绝（REJECT）：标签没有严重级别；严重级别属于诊断项 |
| `note:` 子项 | `Note` | 带可选 `Locus` —— 注释可以指向某处，也可以不指向任何位置 |
| `help:` | `Suggestion` + `Applicability` | 修复只是*提示*；计划属于 U10 |
| `span_suggestion`（跨度上的替换文本） | `Suggestion.remedy` | 拒绝该形状（REJECT the shape）：修复是对格子的计划，不是对字符的字符串（R-12） |
| 相关诊断（clippy/ESLint） | `Finding.related` | id，绝不是嵌套对象图（04 §2） |
| `--error-format=json` 对象 | §4.7 的 JSON 投影 | 采纳（ADOPT），但带版本号，不像 rustc 那样不作承诺的 schema |
| `--explain CODE` | `ExplainBook` | 采纳（ADOPT）：每个已发布诊断码一条条目 |
| 宏展开回溯 | — | 拒绝（REFUSED）：内核没有宏系统；诊断项给出格子与文档，绝不写 "expanded from" |
| 指向生成代码的跨度 | — | *作为跨度链*拒绝（REFUSED *as a span chain*）：关于生成片段的诊断项指向生成文本，`DocumentProvenance`（U6）是回到生成者的唯一桥梁 |
| 增量查询缓存（`-Zquery-dep-graph`） | — | 拒绝（REFUSED）：诊断项不缓存。定向的重查是对请求带 `ChangeSet` 的 `check`（U7）；缓存诊断项会成为第二个事实来源 |
| `#[allow]`／lint 级别 | — | 拒绝（REFUSED）：内核中不存在抑制。宿主通过 `FindingOptions` 过滤，被过滤的诊断项会被计数，绝不静默（§4.5） |
| 数据旁的 `rendered` 字符串 | — | 拒绝（REFUSED）：线上传散文会诱发字符串匹配；人类可读形式是值加上下文的函数 |

### 4.2 构造及其可落实的不变量

`Finding` 的紧凑构造器负责校验与规范化；`build()` 返回该 record，因此现存的每个 `Finding`
都满足局部不变量。规则分三类：

| 种类 | 规则 | 执行者 |
| --- | --- | --- |
| 局部、无条件 | `code`、`severity`、`stage`、`message` 非空；`message` 经 `strip()` 后非空白；至多一个 `PRIMARY` 标签；每个标签的 `message` 非空白；`related` 不含 `FindingId.NONE`；`suggestion.message` 非空白；`KEY` 定位域没有格子 | `Finding` 构造器 → `IllegalArgumentException` |
| 局部、跨度与坐标系一致 | `locus.frame() == DEFINITION` ⟺ `locus.source() != null`；`DEFINITION` 定位域总是写明其文档与区间；`locus.frame() == KEY` ⟺ `locus.key() != null`；`KEY` 定位域没有跨度 | `Finding` 构造器经由 `Locus` |
| 文本层面、无法局部 | `MISMATCH` 或 `UNREADABLE` 应答中的诊断项各自必须带一个 `PRIMARY` 标签，且 `SC-FOC` 诊断项的主标签位于 `KEY` 或某个格子定位域上，绝不是仅消息的报告 | U3 的 `Reply` 契约；引擎在收集时复查并抛出 `SC-SPI-0001`（§4.3） |

为什么跨度规则就是坐标系规则：`SourceSpan` 是文档内的偏移量。`WORLD` 定位域是一组格子，
`LOCAL` 定位域是一组网格格子；两者都没有偏移量，因此这类标签上的源跨度只能是 bug —— 典型
情形是生产者在把定位域切换到它映射到的格子时仍保留了定义跨度。该规则使这种情形不可表示，
而不只是不被鼓励，这也是 `FindingRenderer` 可以假定只有 `DEFINITION` 标签才能画出代码框的
原因。

为什么 `KEY` 不携带格子：一个计算的缺席、一次被拒绝的 pin 与一次不稳定的尝试都有身份，但
没有被占用的格子 —— 其对象是某个实例的某个键。为它们写一个合成格子会渲染出一个看起来可
操作、实则不可操作的世界坐标，这正是预算诊断项所拒绝的同一失败（§4.4）。因此该坐标系只
携带其键，而该 iff 的两个方向使每个格子坐标系都保留一个格子。

为什么 `build()` 抛异常而不是返回结果：畸形的诊断项是生产者中的*程序员*错误，与
`Cuboid.of(min > max)`（03 §5）同类。它不是结构性结果 —— 引擎的结构性结果是
`Reply`/`MatchResult`。使其不至于掀翻一个 tick 的边界是扩展守卫：引擎在守卫下调用扩展，把
`IllegalArgumentException` 转成 `SC-SPI-0001`（抛出非 IAE 时为 `SC-SPI-0002`）并继续。U9
拥有诊断码；U7 拥有守卫。

消息默认：若从未设置 `message`，`build()` 使用 `PRIMARY` 标签的消息；没有主标签时生产者必须
自行设置，因为无标签的诊断项（预算、截断、账本）没有其他通道表达其对象。这正是
`04-api-doctrine.md` §9 的 `Finding.builder(code).label(…).build()` 形式能够编译的原因。

### 4.3 诊断码

格式：`SC-` `<FAMILY>` `-` `<NNNN>`，族为封闭的 15 值枚举，编号 `1..9999` 零填充。
`FindingCode.parse` 恰好接受该文法（外加数值范围），否则返回 `null`；在良构输入上，`text()`
与 `parse` 互为逆。两个诊断码先按声明顺序
`SPC OCC EXP FOC CLM BPT BAY CHK DOC ENG CTX DIA RMD SPI MC` 的族序号比较，再按编号比较。

| 族 | 主题 | 抛出方 |
| --- | --- | --- |
| `SPC` | 空间、朝向变换、坐标系映射 | U1 |
| `OCC` | 占用体与快照读取 | U2 |
| `EXP` | 期望与舱位 | U3 |
| `FOC` | 焦点：一次检查绑定了哪个主体，以及为何没有主体 | U3 |
| `CLM` | 声明、预留、账本 | U4 |
| `BPT` | 蓝图形状、锚点、派生 | U5 |
| `BAY` | 舱位覆盖、足迹、实体跨度 | U5 |
| `CHK` | 检查的声明：舱位归属、覆盖匹配、阶段读取 | U3 |
| `DOC` | 文档与装配 | U6 |
| `ENG` | 本次尝试：预算、步骤、取消 | U7 |
| `CTX` | 计算、键、坐标系 | U8 |
| `DIA` | 诊断层本身 | U9 |
| `RMD` | 补救方案的规划与应用 | U10 |
| `SPI` | 目录／扩展契约违规 | U11 |
| `MC` | 适配器 | A1 |

已发布的分配，连同生产者照抄的严重级别与主标签要求。`P` = 该诊断码要求一个 `PRIMARY` 标签；
`–` 标记无标签的诊断码，只有它们才可能触达 `FindingBundle` 排序中"无主标签"的排名。

| 诊断码 | 严重级别 | P | 含义 |
| --- | --- | --- | --- |
| `SC-SPC-0001` | ERROR | ✓ | 在该安放位下，`Locus` 无法映射到所请求的坐标系 |
| `SC-OCC-0001` | ERROR | ✓ | 该格子为 `UNREADABLE`；快照无法回答，且"缺失"不等于"空气" |
| `SC-OCC-0002` | WARNING | ✓ | 占用体的占地轮廓超出快照；只比较了交集 |
| `SC-EXP-0001` | ERROR | ✓ | 某个元素测试失败：主体不是所声明的类型，或在声明为空气处不是空气；这是每个随附测试的默认 `mismatchCode` |
| `SC-EXP-0002` | ERROR | ✓ | 某个取反测试匹配上了，因此 `not(child)` 构成反驳 |
| `SC-EXP-0003` | ERROR | ✓ | 同一个检查的两次匹配把同一个键绑定到两个不同的值：这是绑定冲突，绝不是异常 |
| `SC-EXP-0004` | ERROR | ✓ | 阈值无法满足：`atLeast(k)` 而 `k > n`，或能匹配到的子项少于 `k` |
| `SC-FOC-0101` | ERROR | ✓ | 焦点在舱位要求主体之处没有找到任何主体；主标签位于该舱位的格子或该键上 |
| `SC-FOC-0201` | ERROR | ✓ | 不止一个占用体可能是该主体，因此绑定有歧义 |
| `SC-FOC-0301` | ERROR | ✓ | 该单元区域内有一个区段被拒绝（`Refused(status, code)`）；其值为 `Missing`，绝不是缩短的求和 |
| `SC-FOC-0302` | ERROR | ✓ | 某个区段的局部贡献缺失，因此折叠没有值 |
| `SC-CLM-0001` | ERROR | ✓ | 两个不同归属方的 `EXCLUSIVE` 声明指向同一个世界的同一格子 |
| `SC-CLM-0002` | ERROR | ✓ | 一次结算与既有声明冲突；诊断项携带既有行 |
| `SC-CLM-0003` | ERROR | ✓ | 该 `SHARED` 舱位遇到另一个所有者的 `EXCLUSIVE` |
| `SC-CLM-0004` | WARNING | – | 账本无法回答，或修订移动了两次：没有结论 |
| `SC-CLM-0005` | ERROR | ✓ | 一次结算的绑定对该请求无效：舱位不属于本蓝图、主体来自另一次捕获、种类不匹配，或单元格落在舱位之外；不写入任何内容 |
| `SC-CLM-0006` | INFO | – | 某次结算产出的声明因本次尝试未成型而被退回未使用 |
| `SC-CLM-0007` | ERROR | – | 一次结算的各部分在它们属于哪个世界这一问题上不一致——某一行、请求，或账本句柄；在写入任何内容之前即被拒绝 |
| `SC-CLM-0104` | WARNING | ✓ | 蓝图的边界未被任何声明的占地轮廓覆盖（`None` 或部分的 `Bound`） |
| `SC-BPT-0001` | ERROR | ✓ | 舱位长方体为空或越出蓝图边界 |
| `SC-BPT-0002` | ERROR | ✓ | 两个舱位以不可共存的共享值重叠 |
| `SC-BAY-0107` | ERROR | ✓ | `ONE_SUBJECT` 没有主体类型检查，或绑定的足迹不等于该舱位（规则 R3） |
| `SC-BAY-0108` | ERROR | ✓ | 非 `None` 舱位的 `reads().cells` 越出 `bay.cells ∪ declared footprint`（SETTLE） |
| `SC-BAY-0109` | ERROR | ✓ | 在 SETTLE 时，某个已绑定实体的占地轮廓离开了 `Bay.cells` |
| `SC-BAY-0110` | ERROR | ✓ | 某实体类型宽于 `MAX_ENTITY_SPAN`；该舱位无法匹配 |
| `SC-BAY-0111` | ERROR | ✓ | `EXCLUSIVE` 舱位的 `Bound` 足迹什么都没绑上，声明会变成空的（R2） |
| `SC-CHK-0101` | ERROR | ✓ | 某个检查把焦点与 `subject()` 不一致的期望配成一对 |
| `SC-CHK-0102` | ERROR | ✓ | 计算／派生 DAG 存在环 |
| `SC-CHK-0106` | ERROR | ✓ | 自相矛盾的声明：`EXCLUSIVE` 配 `None` 足迹（R1），或检查与覆盖不符（R4） |
| `SC-CHK-0107` | ERROR | ✓ | 某个检查不属于任何舱位，或某个舱位什么都没断言：结构级检查与 `Requirement` 为空的舱位都属于接缝，不是声明 |
| `SC-CHK-0109` | ERROR | ✓ | 检查读取了更晚阶段才产出的上下文键 |
| `SC-DOC-0001` | ERROR | ✓ | `schemaVersion` 超出支持范围 |
| `SC-DOC-0002` | ERROR | ✓ | 必需字段缺失或数据类型错误 |
| `SC-DOC-0003` | ERROR | ✓ | 装配引用了库中不存在的片段 |
| `SC-ENG-0001` | ERROR | – | 计划完成前预算耗尽（指向步骤，而非格子 —— §4.4） |
| `SC-ENG-0002` | WARNING | – | 本次尝试在步骤边界处被取消 |
| `SC-ENG-0003` | WARNING | – | 关注区域在捕获之后发生变化，本次尝试 `Dropped(SNAPSHOT_CHANGED)` |
| `SC-ENG-0004` | INFO | – | 因计划中止而从未到达的步骤 |
| `SC-ENG-0005` | WARNING | – | 已停用：槽位取代是调度器的决定，并作为 `MatchCompletion.Discarded` 上报，因此内核没有产出方（`19` R37） |
| `SC-ENG-0006` | WARNING | – | 捕获不再有效：其世界被替换，或租约被关闭；`Dropped(CAPTURE_INVALID)` |
| `SC-ENG-0104` | ERROR | ✓ | 锚点原点格可读，但不给出满足 `candidate.apply(authoredFront) == anchorHeading` 的朝向；本次尝试为 `Failed` |
| `SC-CTX-0101` | ERROR | ✓ | 文档指名了目录中不存在的单元、键或派生器；在 DESIGN 阶段未解析 |
| `SC-CTX-0102` | ERROR | – | 同一个 id 的两次注册；第二次在注册时被拒绝 |
| `SC-CTX-0107` | ERROR | ✓ | 同一坐标系中一个键出现两个不同的值；该发布被拒绝 |
| `SC-CTX-0108` | ERROR | ✓ | 某次发布的 `baseRevision` 已过期；不发布任何内容，且缓慢的 `derive` 无法覆盖更新的代际 |
| `SC-CTX-0109` | ERROR | ✓ | 对某个键用错了动词：`put` 指向由区段贡献的键（`merge != null`），或 `contribute` 指向单写者键；该写入被拒绝 |
| `SC-CTX-0201` | WARNING | ✓ | 某个计算拒绝产出其键（`SC-CTX` 块归 U8 所有） |
| `SC-CTX-0202` | ERROR | ✓ | 某读取方要求一个该坐标系不持有的键；该值缺失，绝不是默认值 |
| `SC-CTX-0301` | WARNING | – | 某个折叠由有限输入产出了非有限总数；该键被省略 |
| `SC-DIA-0001` | WARNING | – | 诊断包被截断：诊断项因上限被省略，或因下限被过滤 |
| `SC-DIA-0002` | WARNING | ✓ | 诊断项的严重级别与其诊断码注册的严重级别不一致 |
| `SC-DIA-0003` | WARNING | – | 两条诊断项共用了同一个 `FindingId`，因此 `related` 有歧义 |
| `SC-DIA-0004` | INFO | ✓ | 本诊断包中的某个诊断码在此构建中没有 `ExplainBook` 条目 |
| `SC-RMD-0001` | ERROR | ✓ | 修复计划未通过试运行；具体行是 `SC-RMD-02xx`/`03xx` 之一 |
| `SC-RMD-0309` | ERROR | ✓ | 该计划的后续状态改变了某个提供者读取过的 `ContextKey`；该计划被拒绝 |
| `SC-SPI-0001` | ERROR | ✓ | 扩展返回的诊断项，其诊断码的形状要求主标签 |
| `SC-SPI-0002` | ERROR | ✓ | 文档指名了本目录快照中不存在的扩展 id |
| `SC-MC-0001` | WARNING | ✓ | 捕获跳过了未加载区域；其格子为 `NOT_CAPTURED` |
| `SC-MC-0404` | WARNING | ✓ | 一次尝试在超过 `maxDiscards` 后被丢弃，并结算为 `Undecided(DIRTY_UNSTABLE)`；主标签位于该实例的键上 |
| `SC-MC-0502` | ERROR | ✓ | `retain(PinBudget)` 拒绝了该 pin（`PIN_REFUSED`）；本次租约上的尝试无法继续 |
| `SC-MC-0601` | ERROR | ✓ | 工作线程抛出意外的 `RuntimeException`；本次尝试为 `Undecided(WORKER_FAILED)`，且工作线程存活 |

稳定性：诊断码的含义在分配时冻结。诊断码从不复用、从不重新编号、从不改指；退役的诊断码保持
孤立，其 `Explanation` 完整，但没有抛出方。上表各行是代表性示例，不是完整分配：每个单元文件
（`05`–`16`）拥有自己的 `SC-<FAMILY>` 块并在其中枚举其诊断码，`SC-CTX` 属于 U8，因此此处的
样例不得为 U8 已使用的编号铸造第二个含义。较新的族按同样方式分配：`SC-FOC` 与 `SC-CHK` 归
U3，`SC-BAY` 归 U5，`SC-RMD` 归 U10。改变既有诊断码*含义*的行为变更即为新诊断码。编号在
每个族内只增不改，因此分配表的 diff 只显示新增。`Stability`（U0）附着在注册表条目上，而非
诊断项上：`DEPRECATED` 的诊断码渲染结果与稳定码完全相同 —— 弃用是对生产者的建议，绝不是
消费者停止读取的理由。

修订 3 的合并带来两项后果，记录于此，而非静默施加。`FOC`、`BAY`、`CHK` 与 `RMD` 被
**插入**到族顺序中的固定位置，这使得此前每个族的相对顺序保持不变，因此没有既有诊断包会跨族
重新排序。`RMD` 占据 `REM` 曾有的槽位：补救族在所有地方都名为 `SC-RMD`（`22` §11.1、
§11.3），`SC-REM` 从未承载过已发布的诊断码，`14` 中的各行即重新编号后的块；若某个诊断码曾以
`REM` 发布，它就只能改为保持孤立。

未知诊断码的行为有三种不同答案，其中没有一种是抛出异常：

| 情形 | 行为 |
| --- | --- |
| `parse` 畸形字符串（`SC-XXX-0001`、`SC-EXP-0`、`SC-EXP-00000`） | 返回 `null`；内核中没有任何代码在未做空检查的情况下解引用 `@Nullable` 的解析结果 |
| 良构但无注册表条目的诊断码 | 正常构造、排序与渲染 —— 严重级别与标签来自诊断项，因此报告是完整的；`JSON` 保留诊断码文本，当诊断包针对某本解释手册构建时，每个此类诊断码发出一条 `SC-DIA-0004` |
| 对该诊断码调用 `ExplainBook.explain` | 返回 `null`；`explainOrFallback` 合成族级解释（§4.8） |

注册表归属：U11 的 `Catalogs` 持有诊断码清单（分配表、规范严重级别、每个诊断码的
`Explanation`），并通过 `Codes` 为每个已发布诊断码暴露一个常量。U9 拥有*格式*、族列表、排序
与上述语义；U11 拥有*实例*，并在目录构建时拒绝重复项或没有解释的诊断码（`SC-SPI-…`）。
生产者从注册表复制严重级别；不一致即 `SC-DIA-0002`，由 `DESIGN` 中的目录自检抛出，绝不由
引擎抛出（`Finding` 必须在手头没有目录的情况下也能渲染）。

### 4.4 定位域与标签

| 坐标系 | 指向 | `CuboidSet` | `SourceSpan` |
| --- | --- | --- | --- |
| `DEFINITION` | 编写文档与所声明的格子 | 按声明给出的舱位／片段格子 | 由 L-1 要求：`Locus.source` 写明文档与区间 |
| `LOCAL` | 蓝图的网格 | 蓝图坐标下的格子 | 从不 |
| `WORLD` | 快照 | 某一 `Siting` 下世界坐标的格子 | 从不 |
| `KEY` | 某个实例的一个 `ContextKey` id | 按构造为空 | 从不 |

`Locus.source` 是*细化后*的高亮：定位域说明指的是哪些格子，源跨度说明声明中的哪些字符被划线。
代码构建的蓝图没有文本位置，因此没有 `DEFINITION` 定位域，而是使用 `LOCAL`。角色语义：

| 角色 | 断言 | 数量 | 示例 |
| --- | --- | --- | --- |
| `PRIMARY` | *故障在此* | 至多一个 | 持有错误方块的世界格子 |
| `SECONDARY` | *这是故事的另一半* | 任意 | 门的第二个部分；声明处 |
| `CONTEXT` | *这里是周边，不是故障* | 任意 | 锚点格子、舱位本应接合的接缝 |

单条诊断项内的规范标签顺序 —— 列表由构造器排序，因此生产者的调用顺序绝不会泄漏进值中：

```
( role.rank(), frame.rank(), position(), source.start(), insertion )
```

`frame.rank()` 为 `DEFINITION 0, LOCAL 1, WORLD 2, KEY 3`；对带跨度的 `DEFINITION` 标签，
`position()` 是 `span.start()`，对 `KEY` 标签是 `0`，否则是定位域的规范首格（先最小 `y`，
再 `z`，再 `x`）；`insertion` 是生产者的声明序号，是稳定的尾部键，绝不是主键。坐标系排名
先于位置，因为比较文档偏移与世界坐标 —— 或把键与这两者相比 —— 没有意义（诊断包排序键在
§4.5 中给出的同一论证）。因此一条诊断项的两个 `KEY` 标签在除插入序之外的每个组件上都相等，
这正是该尾部键必须存在的原因。

设计必须展示的五种情形：

**方块不匹配（一个主标签，一个次标签）。** `PRIMARY` 位于 `WORLD` 格子，消息为
`expected minecraft:bricks, found minecraft:stone_bricks`；`SECONDARY` 位于 `DEFINITION`
舱位，`source` 落在期望字面量上，消息为 `declared here`。主标签指出故障；次标签指出承诺。

**门不匹配（一个主标签，一个次标签，均在世界坐标系）。** 一个期望以原子方式检验一个两部分
的舱位，其中一项属性不一致，因此一条诊断项在第一个不一致的部分上带 `PRIMARY`、在第二个
部分上带 `SECONDARY`，并为每个不一致的属性带一条 `Note`。它有意*不是*两条诊断项：两条诊断项
会共用同一诊断码和同一对标签，并会被之后任何一次注释改写所拆分，而一条带两个标签的诊断项
陈述了这层关系。当生产者有定义标签时，它是第三个标签（`SECONDARY`）。

**声明冲突（一格两个归属方）。** `PRIMARY` 位于有争议的 `WORLD` 格子 —— 冲突的对象是格子，
而不是任一归属方，这正是仍然恰好只有一个主标签的原因 —— 两个 `SECONDARY` 标签分别位于两个
归属方的 `DEFINITION` 定位域，每个归属方一条 `Note`，携带归属方 id 与其共享值。因此，一格两个
归属方无需第二个主标签、也无需合成的"归属方"类型即可表达：归属方在注释中命名，位置是标签，
需要机器可读归属方的消费者读取声明账本（U4），而不是诊断项。

**预算耗尽（无格子）。** `SC-ENG-0001` 带**无标签**：其对象是一个 `CheckPlan` 单元，而单元没有
格子。它的消息为人命名该单元，机器可读的步骤记账属于 `Spend`（U7）—— 诊断项的机器通道是
诊断码、严重级别与阶段。像先例那样改为指向"最后读取的格子"，正是这里拒绝的失败模式：它渲染出
一个看起来可操作、实则不可操作的世界坐标。

**计算缺失（一个位于键上的主标签）。** 焦点未找到主体时的 `SC-FOC-0101` 是格子情形 ——
主标签位于舱位的首格。同一诊断码作用于 `Focuses.context(key)` 时是 `KEY` 情形：`PRIMARY`
位于 `(instance, mymod:capacity)`，消息写出该键，另有一个 `SECONDARY` 位于该键的 `DEFINITION`
声明。`SC-MC-0404` 与 `SC-MC-0502` 对实例级事实是同一种形状：一个 `KEY` 主标签，绝不编造
格子，消息携带被拒绝的计数或预算。

### 4.5 诊断包流水线

`Builder.build()` 按以下顺序执行一趟处理，且是幂等的：

| # | 步骤 | 效果 | 计数器 |
| --- | --- | --- | --- |
| 1 | 盖章 | 每条诊断项的 `stage` 必须非空（`Finding` 总是有一个；这是复查） | — |
| 2 | 下限 | 当 `!includeInfo` 时丢弃 `INFO`（下限为 `WARNING`） | `filtered` |
| 3 | 去重 | 合并相等的值，保留**最先插入**的；合并 `suggestion` 与 `related` | — |
| 4 | 排序 | 下述全序，作用于存储的插入序号 | — |
| 5 | 按码上限 | 在 `WARNING` 与 `INFO` 中，保留每个诊断码的前 `maxPerCode` 条；`ERROR` 永不封顶 | `elided` |
| 6 | 总上限 | 保留幸存 `WARNING`/`INFO` 子序列的前 `maxTotal` 条；每一条 `ERROR` 都留下 | `elided` |
| 7 | 剥离注释 | 当 `!includeNotes` 时，重建每条保留的诊断项并去掉注释 | — |
| 8 | 截断 | 若 `elided + filtered > 0`，在超过上限之外追加 `SC-DIA-0001`，写明两个计数 | — |

去重位于排序之前，使去重的幸存者不依赖比较器；上限位于排序之后，使上限截断的是文档化的顺序，
而不是哈希顺序。由此得到两个性质，并在 §7 中证明：较小的上限得到较大上限的前缀；被截断的
诊断包绝不会出现 `isEmpty()` 为真而计数为零的情况。

**上限只约束噪音，绝不约束错误。** `ERROR` 的含义是“这个结构不可能成形，改这一格”，丢掉一条
就等于藏起玩家必须做的工作。因此上限只作用于 `WARNING` 与 `INFO`，一个诊断包可以因其携带的
`ERROR` 数量而超出 `maxTotal`。宿主若坚持丢弃错误，那是在它自己的过滤层做的，`SC-DIA-0001`
仍然记下数量。

**内核永不封顶。** `MatchResult.bundle()` 是 `FindingOptions.defaults()` 下的完整诊断包；封顶只在
调用方用 `filter(options)`（或以 options 构建）主动要求时发生，因此聊天、日志与导出可以各自从
同一个结果里选自己的尺寸。内核的承诺是“每条错误都在，按文档顺序”；报告多大是宿主的决定。

全序 —— 五个键，一次性应用：

| # | 键 | 比较 |
| --- | --- | --- |
| 1 | 严重级别排名 | `ERROR 0 < WARNING 1 < INFO 2` |
| 2 | 阶段 | `Stage` 声明顺序 `DESIGN, PROBE, FORM, SETTLE` |
| 3 | 诊断码 | `FindingCode.compareTo`：先族序号，再编号 |
| 4 | 主定位域 | `(frameRank, a, b, c)`；无标签的诊断项以 `(4,0,0,0)` 排最后；`DEFINITION` 主标签为 `(0, span.start, span.end, 0)`，`LOCAL`/`WORLD` 为规范首格的 `(1\|2, y, z, x)`，`KEY` 为 `(3, 0, 0, 0)` |
| 5 | 插入序 | 本次尝试的追加序号，在诊断包内唯一 |

为什么严重级别最先：读者首先按诊断项的分量分诊，而错误散落在世界坐标中的报告无法阅读。为什么
阶段先于诊断码：阶段是失败变得可知的顺序（DESIGN 先于 PROBE，PROBE 先于 FORM），因此同一
蓝图上的两个诊断包可以逐行对齐。为什么主定位域先于插入序：在同一阶段与诊断码内，读者按格子
顺序走查结构，而无标签的诊断项排在其诊断码下所有有位置诊断项之后。

插入顺序之所以是*合法*的键，只因它不是走查的偶然产物：步骤顺序本身是文档化的全序
（`02-architecture.md` §4），因此对固定的
`(blueprint, snapshotId, siting, policy, catalog, frozen ledger revision, cancellations)`，
追加序列是固定的。键 1–4 是内容，因此对以不同顺序发现同一批诊断项的两次尝试也稳定；键 5 是
最后的决胜键，正是它使比较器成为全序而不只是自洽（门的情形，以及仅在注释上不同的两条诊断项，
在键 1–4 上都相等）。

去重键 —— 恰好按以下内容的值相等：

```
( severity, stage, code, labels as [(role, locus, source)] in canonical order, notes as [message] )
```

排除：`message`、每个标签消息、`suggestion`、`related`。消息是散文（I-7），因此改写或翻译
绝不能把一条诊断项拆成两条。**注释**被包含在内，这是散文承载事实的唯一之处：`Note` 是没有
格子的那些事实（属性名及其两个值）的唯一通道，因此在所有标签上一致、仅在注释上不同的两条
诊断项是不同的事实，必须都保留。内核只对注释文本做相等比较 —— 从不解析它，从不据其分支，
任何消费者也不得把它当作数据读取。

幸存者保留自己的散文，并获得 `related` 的并集（按值排序），当自己没有 `suggestion` 时获得
被丢弃重复项的 `suggestion`；两者都有时，`Applicability` 梯级更高者胜出，相同则保留幸存者
的。这就是"等价保持"的全部：每个键组件按构造相等，唯一可能不同的是散文与可加提示，它们被
合并而非消失。

`FindingOptions` 收窄：`narrowedBy` 取较小的上限并对布尔值做与运算，因此接收器可以收窄诊断包
的选项，但绝不能放宽。`includeInfo=false` 被读作 `WARNING` 下限；术语表固定了这个布尔值，
因此三值下限会是一项新增式变更（§8 OQ-5）。`includeNotes=false` 最后应用，因此它绝不会影响
哪些诊断项幸存；它在已构建的诊断包上不可逆，`filter` 对此有说明。

`filter(options)`：移除任何已有的 `SC-DIA-0001`，对存储的诊断项及其存储的插入序号重新应用
步骤 2、5–8，并返回新的诊断包。结果的计数器统计的是*本次*收窄丢弃的内容，而不是来源的历史；
需要总计的调用方保留两个诊断包。

### 4.6 接收器与线程

| 规则 | 陈述 |
| --- | --- |
| 线程契约 | 接收器只从单一线程调用 —— 本次尝试的线程。`FindingBundle.Builder` 不是线程安全的，也不被共享；已构建的诊断包不可变，可被任意线程读取 |
| 不做决定 | `accept` 只追加。接收器不得排序、去重、过滤、截断或重排。只有 `build()` 做决定，因此流式接收器与诊断包绝不会不一致 |
| 不抛异常 | `accept` 不得抛出异常。内核在每个宿主接收器外面套上 `FindingSink.guarded(delegate, onFailure)`；抛出会经 `onFailure` 计数，本次尝试继续。掀翻一个 tick 的接收器是缺陷，不是失败模式 |
| 不可重入 | 接收器不得回调引擎（不 `match`、不 `solve`、不做由 `explain` 驱动的重新匹配）。重入会使报告依赖于报告者 |
| 不解析消息 | 接收器读取 `code()`、`severity()`、`stage()`、`labels()`、`related()`。基于 `message()` 分支的接收器在构造上就是坏的 |
| 不阻塞 | `accept` 不阻塞：不做 I/O，不取生产者可能持有的锁，不 join future。需要队列的宿主在 `build()` 之后排空它 |
| 发射不计入预算 | `Budget` 计费读取，不计费报告；上限是报告的预算（`maxTotal`），只花在 `WARNING`/`INFO` 上 |

### 4.7 渲染

三个已发布渲染器，一个接口，一条规则：**渲染器是 `(value, RenderContext)` 的纯函数，绝不
改变交给它的诊断包。** `render` 不排序、不过滤、不修改、不缓存、不做记忆化；以相等参数调用
两次返回相等的字符串。

| 渲染器 | 每条诊断项 | 每个诊断包 | 确定性说明 |
| --- | --- | --- | --- |
| `human()` | rustc 形状的块：头部 `severity[code]: message`，一行 `-->` 指出主坐标系，对有 `SourceText` 的 `DEFINITION` 标签给出带插入符的源码框，每个 `LOCAL`/`WORLD` 标签一行 `= frame …`，随后是 `= note:` 与 `= help:` 行 | 块之间以空行连接，计数非零时追加 `= N elided, M filtered` 尾注 | 只读取标签列表与上下文；`width > 0` 时按 `width` 折行 |
| `json()` | 每条诊断项一个对象，一行 | `{"schema":1,"findings":[…],"elided":n,"filtered":m}` | 忽略 `width`、`color` 与 `locale`：机器输出既不折行、不着色，也不翻译 |
| `sarif()` | 一个 SARIF `result`，`ruleId` = 诊断码文本 | 一个 `run`，其 `rules[]` 按诊断包上的首次出现顺序构建 | `rules[]` 是以诊断码为键的 `LinkedHashMap`，由一次有序扫描填充 —— 绝不是哈希迭代 |

`RenderContext` 就是全部环境：`sources`（本报告可能引用的文档文本）、`document`（当恰好存在
一个来源时，`DEFINITION` 标签被假定所属的 id）、`width`（0 = 不折行）、`color`（`PLAIN` 或
`ANSI`；没有 `AUTO`，因为内核无法检测终端 —— I-15 —— 由宿主决定）、`locale`（只影响装饰
文本，绝不影响诊断码、标签或结构）。墙不匹配的人类可读块：

```text
error[SC-EXP-0001]: expected minecraft:bricks, found minecraft:stone_bricks
  --> structurecumulus:huge_furnace 14:21
   |
 14 |         "wall": { "expect": "minecraft:bricks" }
    |                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ declared here
   |
   = world (112, 64, 208) .. (113, 65, 209)  the wall bay, 1 occupant of another type
   = note: the bay declared one type and matched another
   = help: replace it (machine applicable, remedy structurecumulus:replace_block)
```

JSON schema 带版本且为新增式：字段只增不删、不改类型，只有发生删除或改类型时才提升 `schema`。
字段：`code`、`severity`（`error`/`warning`/`info`）、`stage`（声明名）、`message`、`labels[]`
（`role`、`frame`、`cells` 为 `"x,y,z"` 字符串或长方体三元组、`source` 为 `{start,end}` 或
缺省、`message`）、`notes[]`（`message`、可选 `locus`）、`suggestion`（`message`、
`applicability`、可选 `remedy`）、`related[]`（整数）。SARIF 投影把 `ERROR→error`、
`WARNING→warning`、`INFO→note`；当来源可解析时，`DEFINITION` 标签成为 `physicalLocation`，
`LOCAL`/`WORLD` 标签成为 `logicalLocation` 加 `properties.cells` 数组，因为 SARIF 没有世界
坐标概念，而发明一个假 URI 会是错误锚点。SARIF 的 `rules[].id` 是诊断码文本，其
`shortDescription` 在解释手册有条目时是 `Explanation.summary`。

渲染器绝不合成源码行：`DEFINITION` 标签没有 `SourceText` 时，它打印偏移量及其拿到的格子。
编造的摘录是错误的锚点，而错误的锚点看起来像可操作的。

### 4.8 `ExplainBook` 与 `--explain`

每个已发布诊断码一条条目，强制：`Explanation(code, summary, body, seeAlso)`，其中 `summary`
是一句话（即 `shortDescription`），`body` 是长文本（什么触发它、读者应检查什么、一个完整
案例），`seeAlso` 是 `structurecumulus` 命名空间中的文档主题 id
（`structurecumulus:doc/claims`），绝不是诊断码文本 —— 报告内的跨诊断码链接是 `related` 的
职责。

| 成员 | 契约 |
| --- | --- |
| `explain(FindingCode)` | `@Nullable`；对本构建未发布的诊断码返回 `null` |
| `explain(NamespacedId)` | 解析一个 `seeAlso` 主题；`@Nullable` |
| `codes()` | 已发布清单，按 `FindingCode.compareTo` 排序 |
| `explainOrFallback(FindingCode)` | 绝不为 null：已注册条目，或合成的 `Explanation`，其 summary 为 `no explanation is registered for SC-…`，body 为该族的 `subject()` 句子，`seeAlso` 为空 |

`--explain`（A1 的命令，即 U11 门面（facade）上的 `StructureKernel.explain`）打印一个
`Explanation`，绝不是 `Finding`：summary、空行、body，随后是 `see also:` 行。已注册的诊断码
退出码为 0；对未知诊断码，命令打印的是 `explainOrFallback` 的结果，退出码为 1。两者的全部
关系就是：解释手册是数据，诊断项引用诊断码，命令读取手册而不渲染任何东西。

## 5. 参考代码

```java
// Wall bay: expected the declared type, found another. Primary = world, secondary = definition.
static Finding wallMismatch(Locus worldCell, Locus bayDeclaration, SourceSpan expectLiteral,
                            OccupantDescriptor found) {
    return Finding.builder(Codes.EXPECTED_TYPE)
            .stage(Stage.FORM)
            .severity(Severity.ERROR)
            .label(Label.primary(worldCell, "expected " + Codes.EXPECTED_TYPE + ", found " + found.type()))
            .label(Label.secondary(bayDeclaration.withSource(expectLiteral), "declared here"))
            .note("the bay declared " + Codes.EXPECTED_TYPE + " and matched another occupant type")
            .suggest("replace it", Applicability.MACHINE_APPLICABLE,
                     NamespacedId.of("structurecumulus", "replace_block"))   // a RemedyProvider id (U10)
            .build();
}

// Door: one expectation, two parts, one property. One finding, two labels.
static Finding doorDisagree(Locus lower, Locus upper, Locus declaration, String property,
                            String found, String expected) {
    return Finding.builder(Codes.PARTS_DISAGREE)
            .stage(Stage.FORM)
            .label(Label.primary(lower, property + " is " + found + ", expected " + expected))
            .label(Label.secondary(upper, "the other half of the same bay"))
            .label(Label.secondary(declaration, "declared here"))
            .note(property + " differs between the two parts")
            .build();
}

// Claim conflict: one primary cell, two owners' declarations, one note each.
static Finding claimConflict(Locus contestedCell, Locus declarationA, Locus declarationB,
                             NamespacedId ownerA, NamespacedId ownerB) {
    return Finding.builder(Codes.CLAIM_CONFLICT)
            .stage(Stage.SETTLE)
            .label(Label.primary(contestedCell, "claimed by " + ownerA + " and " + ownerB))
            .label(Label.secondary(declarationA, "first claim"))
            .label(Label.secondary(declarationB, "second claim"))
            .note(ownerA + " holds EXCLUSIVE", declarationA)
            .note(ownerB + " holds EXCLUSIVE", declarationB)
            .build();
}

// Budget: no cell. The step is named for a human; Spend carries it for a machine (U7).
static Finding budgetExhausted(NamespacedId step, long reads) {
    return Finding.builder(Codes.BUDGET_EXHAUSTED)
            .stage(Stage.FORM)
            .message("the budget ran out after " + reads + " reads in step " + step)
            .build();
}

// A focus that found no subject: the primary is a KEY over (instance, ContextKey id), never a cell.
static Finding keyAbsent(InstanceId instance, NamespacedId key, Locus declaration) {
    return Finding.builder(Codes.FOCUS_ABSENT)
            .stage(Stage.FORM)
            .label(Label.primary(Locus.key(instance, key), "no value for " + key))
            .label(Label.secondary(declaration, "declared here"))
            .build();
}

// One bundle, capped per code and in total, then read.
FindingBundle.Builder collecting = FindingBundle.builder(
        FindingOptions.defaults().withMaxPerCode(8).withMaxTotal(64));
FindingSink.guarded(collecting, failure -> { /* counted by the caller's adapter */ })
        .accept(wallMismatch(worldCell, bayDecl, expectLiteral, found));
collecting.accept(doorDisagree(lower, upper, bayDecl, "hinge", "right", "left"));
collecting.accept(claimConflict(cell, declA, declB, ownerA, ownerB));
collecting.accept(budgetExhausted(stepId, 4096L));

FindingBundle bundle = collecting.build();
Finding first = bundle.primary();               // non-null here: severity then stage then code
String human = FindingRenderer.human().render(bundle, RenderContext.of(documentText).withWidth(100));
String json = FindingRenderer.json().render(bundle, RenderContext.plain());

// ExplainBook lookup: registered, then an unregistered but well-formed code.
Explanation known = book.explainOrFallback(Codes.CLAIM_CONFLICT);       // registry entry
Explanation unknown = book.explainOrFallback(FindingCode.of(FindingCode.Family.EXP, 999)); // family fallback
```

```text
error[SC-CLM-0001]: claimed by structurecumulus:door_a and structurecumulus:door_b
  --> structurecumulus:huge_furnace 22:9
   |
 22 |         "mouth": { "claim": "exclusive" }
    |         ^^^^^^^^ first claim
   |
   = note: structurecumulus:door_a holds EXCLUSIVE
   = note: structurecumulus:door_b holds EXCLUSIVE
   = world (112, 64, 208)  the contested cell
```

## 6. 用例

**附属模组作者 —— 门的期望。** 对一个两部分舱位调用一次 `test` 返回
`Reply.mismatch(doorDisagree(…))`。作者为每个不一致的属性写两个标签和一条注释，从不接触
诊断包、诊断码表或渲染器；诊断码来自 `Codes`，阶段来自上下文，排序稍后发生。如果作者忘记
主标签，`build()` 在扩展内部抛出异常，引擎把它转成 `SC-SPI-0001`，而不是丢掉报告。

**宿主 —— `/cumulus why` 命令。** 该命令用蓝图文档的文本构建 `RenderContext`，`width = 60`，
颜色 `PLAIN`，并调用 `FindingRenderer.human().render(bundle, ctx)`，然后把每一行发送到聊天。
它从不排序，从不读取消息，其输出与 CI 日志对同一诊断包的输出在宽度与颜色之外完全一致。

**宿主 —— 数据包重载。** `DocumentValidator`（U6）在 `Stage.DESIGN` 中通过一个把 JSON 行
写入日志文件的 `FindingSink` 发出 `SC-DOC-*` 诊断项。因为 DESIGN 诊断项带有一个有源跨度的
`DEFINITION` 主标签，日志中的插入符指向确切字段；因为 JSON schema 带版本，从未见过此文档的
仪表盘也能渲染它。

**宿主 —— 建造法杖。** `RemedyProvider` 收集 `Suggestion.remedy` 的 id 以决定提供哪些预览；
它读取 `applicability()`，只自动应用 `MACHINE_APPLICABLE` 梯级。U9 提供提示；U10 提供计划。

## 7. 证明

**P1 —— 该顺序是全序。** 每个键都是存储状态的函数：`severity.rank()`、`stage.ordinal()`、
`code.compareTo`、主标签的坐标系／位置（标签列表顺序是规范的，因此"主标签"无歧义），以及
插入序号。前四个各自是值上的全预序；第五个是序号上的全序，因为 `build()` 为每条幸存的诊断项
分配互不相同的序号且从不重新编号。由唯一末位组件上的全序细化的全预序之字典序积是全序：对
键 1–4 完全相同的相异 `x, y`，`x.index() != y.index()` 决定次序。反对称性与传递性由各分量
的对应性质得出。反例检查：严重级别、阶段、诊断码与主定位域都相等*且*插入序号也相等的两条
诊断项不可能存在 —— `add` 是唯一的追加路径，且会递增序号。

**P2 —— 去重保持等价。** 去重键是不可变组件上的元组相等，因此自反、对称、传递；折叠是按该
关系的商，且每个幸存者的键仍等于每个成员的键，因此没有键组件丢失。一个重复组可能隐藏的差异
都在键之外 —— `message`、标签消息、`suggestion`、`related` —— 它们被合并（suggestion 与
related），或本就是幸存者已经陈述的散文（消息）。因此键所编码的*事实集*不变，消费者对
`(code, severity, stage, labels, notes)` 的读取无法区分"一条诊断项被收集两次"与"一条诊断项
被收集一次" —— 这正是去重必须保证的。

**P3 —— 上限保留每一条错误与至少一条提示，或明说。** `ERROR` 在两个上限之外，因此任何配置
下都不会被省略。在 `WARNING`/`INFO` 中，按码上限保留每个诊断码的前 `maxPerCode` 条，全局
上限保留该剩余子序列的前 `maxTotal` 条；当 `maxPerCode >= 1` 且 `maxTotal >= 1` 时，子序列
的首个元素在两个上限下都幸存。唯一不留下提示的配置是 `maxTotal = 0`，以及
`includeInfo = false` 下的全 `INFO` 诊断包；两种情况都有 `elided + filtered > 0`，因此步骤 8
追加 `SC-DIA-0001`，而 `isEmpty()` 为 `true` 当且仅当截断诊断项本身被过滤 —— 这不可能，
因为 `SC-DIA-0001` 是 `WARNING`。被截断的诊断包绝不会被读成完整而干净的报告，而一个全是
错误的诊断包绝不会因上限而减少。

**P4 —— 诊断码格式稳定且可往返。** 文法为
`SC-(SPC|OCC|EXP|FOC|CLM|BPT|BAY|CHK|DOC|ENG|CTX|DIA|RMD|SPI|MC)-[0-9]{4}`，且
`1 <= number <= 9999`。
`text()` 恰好产出该形状（固定前缀、枚举名、`%04d`）；`parse` 只接受该形状与该范围，因此对
每个可构造的 `c` 有 `parse(text(c)) == c`，对文法接受的每个 `s` 有 `text(parse(s)) == s`。
族是枚举，因此拼写错误（`SC-EXPX-0001`）不可表示，而不只是未注册；四位填充加范围检查意味着
没有诊断码有两种拼法。新增族是源码不兼容的枚举变更，因此是一次评审事件，而不是静默放宽。

**P5 —— 渲染器是确定性的。** `render` 按顺序读取诊断包的 `findings()`、每条诊断项的规范
标签列表与上下文。除 `sarif()` 外，输出中不会出现映射迭代；在 `sarif()` 中，`rules[]` 由
一次有序扫描填充进 `LinkedHashMap`，且只为下标以键查找。不读取时钟、哈希码、身份、区域
默认值（`locale` 在上下文中）或环境。因此相等的 `(value, context)` 产出相等的字符串，两个
构建出相等诊断包的宿主渲染出相等的字节。

**P6 —— 内核没有任何分支读取散文。** 唯一的文本操作是：注释消息上的相等比较（去重键），
以及追加字符串。没有任何比较会选出诊断码、严重级别、角色或分支；`FindingCode` 只按其自身
文法解析，绝不从消息解析。证伪测试是机械的：改写夹具中的每条 `message` 与每个标签消息，断言
诊断包的 `size()`、`elided()`、`filtered()`、有序 `code` 列表与每个 `(role, locus)` 列表都
不变。唯一诚实的例外在 §4.5 中陈述：注释文本是去重组件，因此改写*注释*可能改变去重 ——
这正是任何消费者都不得把注释文本当作数据的原因。

## 8. 依赖与待决问题

依赖：**U0**（`NamespacedId`、`Stage`、`Stability`、`Checks`）；**U1**（`Locus`、带四个
坐标系的 `LocusFrame`、`CuboidSet` 及其规范顺序）；**U3**（`Reply`，要求
`MISMATCH`/`UNREADABLE` 上带主标签）；**U4**（`Claim`，用于冲突的注释）；**U7**（`Spend`，
无标签预算诊断项所越过的步骤记账）；**U11**（`Catalogs`、`Codes`、`ExplainBook` 实例与诊断码
清单）；**U10**（`Remedy` —— 仅作为 `NamespacedId` 引用）。`KeyLocus` 属于 U1；U9 只要求
`KEY` 坐标系携带 `(InstanceId, NamespacedId)` 且没有格子。

本表中每个更早的问题都已由 `22` §15 解决（已解决的行随之消失）。§16 中有一个问题落在本单元
的诊断码上：

| # | 问题 | 选项 | 后果 |
| --- | --- | --- | --- |
| OQ-1（`22` §16 Q1） | 空体积保护：按蓝图一个 `sealed: true` 标志是否优于一个警告加上按舱位的 `Box`？ | (A) 保留警告 `SC-CLM-0104` 加上按舱位的 `Footprint.Box` 选择；(B) 给蓝图新增 `sealed` 并使该警告不可达 | (A) 在本单元发布，并让该选择按舱位可见；(B) 需要一条术语表行，并把 WARNING 变成编译期保证，因此无论哪种方式，该诊断码都要为仅 `Declared` 的蓝图保留分配 |

## 9. 证伪

| # | 受检验的论断 | 能打破它的具体测试 |
| --- | --- | --- |
| F1 | 该顺序是全序 | 在一个诊断包中追加严重级别、阶段、诊断码与主定位域都相等但注释不同的两条诊断项：若 `compare` 返回 0，则 `findings()` 各位置互异的断言失败；由该诊断包构建的 `TreeSet` 必须保留两者 |
| F2 | 上限绝不移除提示的主标签 | 40 条 `SC-MC-0001`（`WARNING`），`maxTotal = 1`：`primary()` 必须等于截断前的 `primary()`，且 `size()` 必须为 1、`elided() == 39` |
| F2b | 任何上限都不丢错误 | 40 条 `SC-BAY-0101`（`ERROR`）加 5 条 `SC-MC-0001`，`maxTotal = 1`、`maxPerCode = 1`：`size()` 必须为 40 + 1 + 一条 `SC-DIA-0001`，`ofSeverity(ERROR)` 必须为 40 且与未封顶时逐字节相等，`elided() == 4` |
| F3 | 上限是前缀 | 同一次生产者运行产生 40 条 `SC-MC-0001`，分别以 `maxTotal = 16` 与 `64` 构建：64 条诊断包的前 16 条必须等于 16 条诊断包的 `findings()`，且两者必须共享 `primary()` |
| F4 | 被截断的诊断包绝不会静默地显得干净 | `maxTotal = 0` 且有 5 条诊断项：`isEmpty()` 为 false（`SC-DIA-0001` 诊断项存在），`elided() == 5` |
| F5 | 没有渲染器依赖哈希顺序 | 对诊断码顺序为 `EXP, CLM, EXP, ENG` 的诊断包做 SARIF 渲染：`rules[].id` 必须是 `[SC-EXP-…, SC-CLM-…, SC-ENG-…]`；在两个 JVM `-XX:hashCode` 种子下运行并对比 |
| F6 | 未知诊断码不会使渲染器崩溃 | `FindingCode.of(Family.EXP, 999)` 配合空 `ExplainBook`：human、JSON、SARIF 与 `explainOrFallback` 都必须产出指明该诊断码的输出，无 `NPE`，也无 `Optional` 解包 |
| F7 | 畸形诊断码无法进入 | `FindingCode.parse("SC-XXX-0001")`、`"SC-EXP-0000"`、`"SC-EXP-10000"`、`"sc-exp-0001"` 都返回 `null`；`FindingCode.of(Family.EXP, 0)` 抛出 `IAE` |
| F8 | 散文不是契约 | 改写夹具中的每条消息；`size`、计数器、有序诊断码与有序 `(role, locus)` 列表必须逐字节相同 |
| F9 | 跨度属于其坐标系 | 构造一个带 `WORLD` 定位域且 `source` 非空的 `Label`：`Finding.build()` 必须抛出 `IAE`；`source` 超出 `locus.span()` 的 `DEFINITION` 标签同理 |
| F10 | 无标签诊断项仅在表中允许处合法 | `budgetExhausted(…)` 无标签即可构建；§4.3 中每个 `P ✓` 诊断码在生产者的主标签缺失时必须产出 `SC-SPI-0001`，任何 `P –` 诊断码都不得如此 |
| F11 | 去重不会吞掉不同的阶段 | 除 `stage`（PROBE 与 FORM）外完全相同的两条诊断项必须都幸存：去重键包含 stage |
| F12 | 接收器无法掀翻本次尝试 | 一个在每次 `accept` 都抛 `RuntimeException` 的接收器，用 `FindingSink.guarded` 包裹：诊断包仍能构建，计数器递增 |
| F13 | 键诊断项没有可指向的格子 | 在一个诊断包中放入 `keyAbsent(instance, key, declaration)`：`primary().frame() == KEY`、`primary().cells().isEmpty()`、`primary().key()` 等于该对，且用格子或 `SourceSpan` 构建的 `KEY` 标签抛出 `IAE` |
| F14 | 键诊断项仍然参与排序 | 一条位于格子的 `SC-FOC-0101`，一条位于 `KEY` 的同一诊断码，两者严重级别、阶段与诊断码相同：格子诊断项排在前，而同一诊断码的无标签诊断项排在两者之后 |

能推翻本模块的评审问题：*举出一条内核发布的、需要两个 `PRIMARY` 标签的诊断项，或一条其
机器可读内容只存在于消息中的诊断项。* 若其中任一存在，则 §4.2 的"至多一个主标签"或 I-7 的
"诊断码与定位域就是通道"是错误的。
