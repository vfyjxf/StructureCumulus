# 03 — 命名与形状约定（Naming and shape conventions）

> 命名冲突禁用清单见 `01-glossary.md` §O。本文给出生成名字的规则，使模块作者不必询问
> 就能命名一个新类型。

## 1. 一个概念，一个名字（One concept, one name）

一个概念在内核（kernel）中只有一个名词。同义词是缺陷，不是风格选择。术语表是权威；
一个模块若需要为既有概念造第二个词，它就是错的。不得共存的名字对举例：`Bay`/`Slot`、
`Occupant`/`Element`、`Expectation`/`Predicate`、`Finding`/`Diagnostic`、
`Remedy`/`Solution`、`Cuboid`/`Box`。

## 2. 冲突禁用（Collision ban）

任何内核类型都不得与 `net.minecraft.**` 类型或常被导入的 JDK 类型同名。可操作的规则：

- 若同一适配器文件中很可能 import 一个同名的 Minecraft 类，该名字即被禁用。
- 若该名字是 `java.util.function` 接口、`java.util` 集合、`java.time` 值类型，或
  `javax.tools`/`javax.swing`/`java.awt` UI 类型，该名字即被禁用。
- 被禁用的概念取一个领域词，而不是加前缀。`Turn` 优于 `CumulusRotation`。
- 适配器包本身就是命名空间。适配器类按 Minecraft 侧的角色命名（`LevelSnapshot`、
  `LevelCapture`、`WorldEdits`），绝不加机械的 `Mc`/`Vanilla` 前缀。与内核类型冲突时，
  加角色限定词（`OccupantDescriptors`、`OccupantTraits`），而不是前缀。内核永不导入 `mc`。

`01-glossary.md` §O 的禁用清单是规范性的。引入尚未列出的名字之前，先在那里加一行。

## 3. 包布局（Package layout）

| 包 | 内容 | 可依赖 |
| --- | --- | --- |
| `kernel.base` | id、预算、开销、取消、稳定性、阶段、参数、源码区间、数据视图 | 无 |
| `kernel.space` | 格点、长方体、朝向、朝向变换、安放位、定位域 | base |
| `kernel.occupant` | 占用体模型、世界视图、捕获 SPI | base、space |
| `kernel.finding` | 诊断项模型、诊断包、渲染器、解释 | base、space |
| `kernel.claim` | 声明模型、账本、规划器 | base、space |
| `kernel.expectation` | 期望 SPI、应答、绑定、组合器 | base、space、occupant、claim、finding |
| `kernel.blueprint` | 舱位、接缝、片段、锚点、蓝图、构建器 | base、space、expectation、claim |
| `kernel.document` | 文档模型、数据视图、桥、装配器、发射器 | base、space、blueprint、expectation、finding |
| `kernel.engine` | 请求、会话、结果、事实、预筛、失效 | 以上全部 |
| `kernel.context` | 上下文键、派生器、计划、增益、运行时状态 | engine 及以上 |
| `kernel.remedy` | 补救方案、编辑计划、叠加层、校验器、事务 | blueprint、occupant、expectation、finding |
| `kernel.spi` | 目录、门面、稳定性策略 | 所有内核包 |
| `mc` | 适配器（主源集，main source set） | 所有内核包 + Minecraft |

一个单元一个包。不存在 `internal` 子包：包私有类型就是内部标记。

## 4. 类型形状约定（Type-shape conventions）

| 后缀 / 形状 | 含义 | 示例 |
| --- | --- | --- |
| `record` | 具备结构化相等的不可变值 | `LatticePoint`、`Finding`、`Claim` |
| `sealed interface` + record | 调用方以 switch 覆盖的封闭家族 | `MatchResult`、`Edit`、`Remedy` |
| 普通 `interface` | 扩展点或只读视图 | `Expectation`、`WorldView`、`ClaimLedger` |
| `*Key<T>` | 携带 `Class<T>` 令牌的类型化标识 | `ContextKey<T>`、`TraitKey<T>` |
| `*Ref` | 快照作用域内的句柄，快照内按标识判等 | `OccupantRef`、`InstanceRef` |
| `*Id` | 稳定的逻辑标识，不是句柄 | `SeamId`、`FindingId`、`SnapshotId` |
| `*Snapshot` | 某一修订上的不可变读取模型 | `OccupantSnapshot` |
| `*View` | 快照之上的只读实时视图 | `WorldView`、`NestedView` |
| `*Spec` | 声明式编写形式，稍后解析 | `AnchorSpec`、`ClaimSpec`、`DerivationSpec` |
| `*Plan` | 由 spec 编译出的有序可执行形式 | `CheckPlan`、`DerivationPlan`、`EditPlan` |
| `*Policy` | 一小组成员可选项，改变行为 | `OrientationPolicy`、`MatchPolicy` |
| `*Mode` | 从几种语义中择一的枚举 | `CacheMode` |
| `*Options` | 面向宿主调用的旋钮，带默认值 | `FindingOptions`、`SolveOptions` |
| `*Context` | 一次调用中扩展可见的内容 | `ExpectationContext`、`DerivationContext`、`RemedyContext` |
| `*Factory` | 在目录中注册的数据驱动构造器 | `ExpectationFactory` |
| `*Provider` | 返回多个候选答案的扩展 | `RemedyProvider` |
| `*Sink` | 推送型消费者 | `FindingSink` |
| `*Renderer` | 到文本/数据的纯投影 | `FindingRenderer` |
| `*Catalog` / `Catalogs` | 冻结的注册表快照 | `Catalog<T>`、`Catalogs` |
| `*Builder` | 可变构造，`build()` 负责校验 | `BlueprintBuilder`、`MatchRequest.Builder` |

不要给公开类型加 `Impl`、`Default`、`Abstract`、`Base` 或 `Simple`。内核的平凡实现是
静态工厂或带领域名的嵌套 record（`Expectations.air()`），而不是 `DefaultExpectation`。

## 5. 方法命名（Method naming）

| 动词 | 契约 |
| --- | --- |
| `of(...)` | 静态工厂；校验参数，仅在程序员错误时抛 `IllegalArgumentException` |
| `from(...)` | 可能丢失信息的转换；丢失之处需写入文档 |
| `toX()` | 到另一种表示的投影 |
| `get(...)` | 返回 `@Nullable` 的查找；从不创建 |
| `require(...)` | 由前置检查保证的查找；返回非 null，或抛编程错误异常 |
| `at(...)`、`on(...)` | 空间选择（`occupantAt`、`claimsAt`） |
| `with(...)` | 改一个字段的副本；返回新值 |
| `test(...)` | 谓词式布尔值或一个 `Reply`；`Expectation` 使用 `test` |
| `check(...)` | 一次只读的引擎尝试；绝不是布尔值 |
| `settle(...)` | 写入一份已检查计划所点名的声明；唯一的声明写入动词 |
| `derive(...)` | 把产物变为上下文；从不修改 |
| `solve(...)` | 收集补救方案；从不施加 |
| `compile(...)` | 把文档变为蓝图并收集诊断项；对非法输入从不抛出 |
| `apply(...)` | 通过事务修改世界；世界编辑绝不隐式发生 |
| `explain(...)` | 针对一个码的长文本 |

可能产生结构性失败的方法返回一个值（`Reply`、`MatchResult`、`FindingBundle`），绝不
抛出。会抛出的方法意味着调用方违反了前置条件；`Checks.checkNotNull` 是唯一被认可的
断言辅助。

## 6. 泛型与变型规则（Generic and variance rules）

- 类型化键是 `record XKey<T>(NamespacedId id, Class<T> type)`。`Class<T>` 令牌是强制的：
  正是它使异构映射在调用点无需未检查转换即可安全。
- 查找返回 `@Nullable T`；绝不返回 `Optional<T>`（项目空值规则）。
- 调用方必须 switch 覆盖的家族是 `sealed`；新增一个分支属于有意的源不兼容变更，需记入
  模块的*待决问题（Open questions）*。
- 通配符：`Catalog<? extends T>` 只出现在读取边界；`Catalog<T>` 是不变的。
- 公开签名中不得出现原始类型。凡 `DataView`、`Arguments` 或某个 sealed 类型已表达意图
  之处，不得使用 `Object` 参数。

## 7. 常量、码与 id（Constants, codes and ids）

- 常量是 `static final`，名字为 `UPPER_SNAKE`，与术语表一致（`Expectations.AIR`、
  `TraitKeys.ITEM_INVENTORY`）；工厂方法保持 `lowerCamelCase`（`Expectations.air()`）。
- `FindingCode` 形如 `SC-<FAMILY>-<NNNN>`：`SC` 是项目，`FAMILY` 是三到六个字母的单元
  助记符（`SPC`、`OCC`、`EXP`、`FOC`、`CLM`、`BPT`、`BAY`、`CHK`、`DOC`、
  `ENG`、`CTX`、`DIA`、`RMD`、`SPI`、`MC`），`NNNN` 是补零的数字。码永不复用。
- `NamespacedId` 形如 `namespace:path`；默认命名空间是模组 id
  （`structurecumulus`），内置的码使用 `structurecumulus` 或所属附加包的 id。
- `UnitId`、`SeamId` 和 `FindingId` 是局部标识；局部 id 足够时不得复用
  `NamespacedId`。

## 8. 文档约定（Documentation conventions）

每个模块文件使用相同的九个小节，顺序如下：

1. **范围（Scope）**——本单元拥有什么、不拥有什么。
2. **术语（Vocabulary）**——它拥有的名字，链接到术语表。
3. **API 设计（API design）**——类型与签名，附契约表。
4. **语义（Semantics）**——算法、顺序、不变量、失败行为。
5. **参考代码（Reference sketch）**——形态上可编译的 Java；不得出现抛异常的桩。
6. **用例（Use cases）**——至少一个附加包作者用例和一个宿主用例。
7. **证明（Proofs）**——不变量为何成立；每个有风险的论断配一个反例。
8. **依赖与待决问题（Dependencies and open questions）。**
9. **证伪（Falsification）**——会击破该模块的具体测试或评审问题。

代码块为 Java 21。参考代码可以省略 import 与函数体，但参考代码中给出的签名是规范性的；
正文不得与之矛盾。
