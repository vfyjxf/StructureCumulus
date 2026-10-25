# StructureCumulus — 版本无关的多方块结构内核

> 状态：设计草案 v0.1，2026-10-20。本文集是一份*设计*，不是实现承诺。每个模块文件都
> 允许携带待决问题（open question）；一个没有证伪（falsification）路径的论断就是缺陷。

## 1. 我们在构建什么（What we are building）

StructureCumulus 是一个**结构内核（structure kernel）**：一个纯 Java 引擎，把声明式
蓝图（blueprint）与由**占用体（occupant）**构成的格（lattice）相匹配（方块、方块实体、
实体与嵌套结构同等对待），报告 rustc 级别的诊断项（finding），并从一次成功的匹配中
派生（derive）出类型化的运行时上下文（context）。它从不提及 Minecraft。一个薄适配器
（adapter）把内核绑定到 Minecraft 1.21.1 / NeoForge 21.1.x；该适配器是唯一允许 import
Minecraft 类型的代码。

内核刻意设计为**一个大型的封闭核心**，只带少量扩展点。谓词（predicate）、派生器、
补救方案、占用体解析器与世界捕获都是接口；内核只交付它们的平凡实现（trivial
implementation）。这是“一个内核，多种扩展”规则（R-10），而不是每个加载器一个内核的
插件框架。

## 2. 一句话模型

> 一个**蓝图（blueprint）**在一个**安放位（siting）**上、于一个**阶段（stage）**内，与
> 不可变的**世界快照（world snapshot）**相匹配，产出一个密封的**匹配结果
> （match result）**与一个**诊断包（finding bundle）**；已成型（formed）的结果可以
> **派生**为类型化的**结构上下文（structure context）**；诊断项可以在同一套计划基底
> （plan substrate）之上转换为**补救方案（remedy）**（修复计划、叠加层、解释）。

其余每份文档都展开这句话中的一个名词。一个提案若无法放进这句话，就属于内核之外。

## 3. 需求（Requirements）

委托声明是固定的。R-01..R-15 是内核的验收标准；每个模块文件列出它所服务的需求，
不服务任何需求的决策一律拒绝。

| 编号 | 需求 | 设计后果 |
| --- | --- | --- |
| R-01 | 不得假定谓词的基础元素是方块。实体与嵌套子结构同样是元素；坐标系与网格保持 Minecraft 的形状。 | `OccupantKind` 是开放族；`WorldView` 通过同一个 `OccupantSnapshot` 解析每一种类型；格使用 Minecraft 的 x:26 / z:26 / y:12 打包布局。 |
| R-02 | 不得假定 Minecraft，但要交付一个基线（baseline）：1.21.1 NeoForge。 | `src/kernel/java` 不含任何 Minecraft/NeoForge/Mojang import；`src/main/java` 是唯一的适配器；违反时纯净性门禁（purity gate）使构建失败。 |
| R-03 | 把结构信息转换为运行时上下文，而不只是检测。错误分析与结构特征（例如巨型熔炉的内部容积）都是上下文；升级同样是上下文。 | `Computation`/`Deriver` → `ContextKey<T>` → tier frame；计算读取快照，派生读取 `MatchOutcome` 与上下文，二者都不重新遍历结构；每个值都是类型化的，且允许缺失。 |
| R-04 | 存在一个计算中心，但不假定任何控制器（controller）实体。 | `AnchorSpec` 在单元格上声明角色；`OrientationPolicy` 从锚点候选中选择一个摆放；任何地方都不需要 `Entity` 类型。 |
| R-05 | 支持朝向变化：控制器的朝向决定整个结构的朝向；此处的“entity”不是 `net.minecraft.world.entity.Entity`。 | 48 元朝向群（`Turn` × `Tilt` × `Flip`）、`Placement`、`Siting`；被摆放的蓝图是一个带 `Siting` 的 `MatchResult.Formed`，而不是 MC 实体。 |
| R-06 | 支持异形谓词（门是典型例子）；矩形足够。 | 一个 `Bay` 拥有一个 `Cuboid`，而不是一个单元格；一个 `Expectation` 原子地测试整个舱位，并可按单元格返回标记；没有体素集合谓词。 |
| R-07 | 细致、正确的错误分析与日志；rustc 是质量标杆。 | `Finding` 携带稳定的 `FindingCode`、`Severity`、落在 `Locus` 上的主/次 `Label`、注释、帮助、相关诊断项、确定性排序、上限，以及一个 `ExplainBook`。 |
| R-08 | 分代计算：不同阶段需要不同的检查（成型前快速失败，成型后合法性检查）；谓词顺序有意义。 | `Stage`（`DESIGN`、`PROBE`、`FORM`、`SETTLE`，由引擎选择）、按阶段的 `CheckPlan`、按"产生的键 → 读取闭包体积 → 声明下标"排序的单元、`ReadSet` + `ChangeSet` + `InvalidationIndex`。 |
| R-09 | 结构片段与定义的数据驱动生成。 | `Segment` + `BlueprintDocument` + `DataView` + `SegmentLibrary` + `SegmentAssembler` + `SegmentEmitter`；文档语法由内核拥有并带版本。 |
| R-10 | 一个大型通用内核；具体谓词与上下文是扩展，或接口的平凡实现。 | `Catalogs` 冻结扩展实例；内核只交付 `Expectations` 组合器，不含任何 Minecraft 专属谓词。 |
| R-11 | 共享与独占声明检测。 | `Sharing`（`EXCLUSIVE`、`SHARED`）、`ClaimLedger`、逐单元格冲突检测；冲突是一条诊断项，绝不是静默覆盖。 |
| R-12 | 可视化与自动建造，作为不同的补救方案种类，与错误分析和修复服务于同一目的。 | `Remedy` = `Repair(EditPlan)` \| `Preview(OverlayModel)` \| `ExplainRemedy`；两种投影共享 `Locus` 与 `Applicability`。 |
| R-13 | 组合式 API，类型安全。 | `Expectation` 组合器返回 `Expectation`；类型化的 `BindingKey<T>`、`ContextKey<T>`、`TraitKey<T>`、`ParameterKey<T>`；密封的 `MatchResult` 与密封的 `Edit` 以支持穷尽 switch。 |
| R-14 | 领域复杂处复杂（诊断项、自动建造），对 API 使用者简单。 | 门面有九个操作，`01` 中计数、`15` §3 中绘出；构建器是流式的；内部阶段、预筛索引与事务机制绝不泄漏到首次使用路径。 |
| R-15 | 在设计期间编写参考代码，以检验 API 是否合理。 | 每个模块都带一段可编译形态的草图、一个用例与一节证伪；合并后的草图在获批后落到 `src/kernel/java` 下。 |

## 4. 不变量（Invariants）

与某条不变量矛盾的模块文件，即使自身内部自洽也是错的。

| 编号 | 不变量 | 由谁强制 |
| --- | --- | --- |
| I-1 | `kernel.**` 下的任何内容都不得引用 `net.minecraft`、`net.neoforged`、`com.mojang` 或任何加载器类型。 | `kernelPurity` 扫描 + `compileKernelJava`（内核 classpath 上没有 MC） |
| I-2 | 没有任何占用体种类享有特权。方块、方块实体、实体与嵌套结构都通过同一个 `OccupantSnapshot` 契约解析。 | U2 |
| I-3 | 每个占地轮廓（footprint）都是轴对齐 `Cuboid` 的并集。内核中不存在体素集合运算。 | U1 |
| I-4 | 一次匹配只读取不可变的 `WorldView` 快照；`(blueprint, snapshot, siting, policy)` 完全决定结果。 | U2、U7 |
| I-5 | `Siting` 由 `AnchorCandidate` 加上蓝图的 `OrientationPolicy` 派生；任何时候都不需要控制器实体。 | U1、U9 |
| I-6 | 每个检查都运行在由引擎选定的一个 `Stage` 中，带有自己的 `CheckPlan`、预算与失败策略，设计期校验也不例外；作者不声明阶段，调用方也不选择阶段。 | U7、U9 |
| I-7 | 诊断项是唯一的结果通道。机器消费者读取稳定的诊断码与定位域，绝不读取消息文本。 | U9 |
| I-8 | 内核只交付平凡实现；真正的谓词、派生器、补救方案与解析器都是扩展。 | U11 |
| I-9 | 检测与上下文生产相互分离：`Computation`/`Deriver` 读取快照与上下文（派生时还读 `MatchOutcome`），绝不修改匹配。 | U8 |
| I-10 | 每个**已结算（settled）**的单元格都携带一个共享值（sharing value），且只有 `settle` 才会结算：`Formed` 是一个已经被写入的结构。冲突是一条 `Finding`，绝不是静默覆盖。 | U4、U7 |
| I-11 | 修复、预览与解释共享同一套 `Remedy` 基底与同一套 `Locus`/`Applicability` 词汇。 | U10 |
| I-12 | 蓝图是一个带 `schemaVersion` 的 `BlueprintDocument`，可规范化、可由外部工具生成，并可翻译为宿主编解码（codec）对象，而内核不依赖这些对象。 | U6 |
| I-13 | 内核公开 API 是组合式且类型安全的：凡能存在 `*Key<T>` 之处，不得使用以字符串承载取值的通道；封闭集合使用密封族。 | U0、全部 |
| I-14 | 没有公开成员无条件抛出异常；缺失用 `@Nullable`、一条诊断项或一个显式密封分支表达。 | 全部 |
| I-15 | 内核不读取挂钟时间、不使用随机数、不做 I/O、不读全局状态。时间与取消都通过注入提供。 | U7、U11 |
| I-16 | 账本只接收派生出来的行：`settle` 写入的每一行都出自这个纯净规划器（planner），它作用于请求的 `(world, blueprint, siting, owner)` 与计划的绑定；计划本身不携带任何可供伪造的行。`settle` 对绑定做结构检查（每个绑定都指出本蓝图的一个舱位，其主体已在此次捕获中被观测到，其种类相符，且其单元格落在该舱位内——对 `ONE_SUBJECT` 要求完全相等）；它不会重新运行期望，因为那个判定是 `check` 的，且捕获门禁把世界钉在该判定上。 | U4、U7 |

## 5. 单元（Units）

内核有十二个单元，外加一个适配器。本表中的名称是模块唯一可以增添名称的集合；模块若
需要一个新的跨单元名称，应在自己的“待决问题”一节中提出，而不是另造一个私有同义词。

| 单元 | 包 | 它回答的问题 | 文件 |
| --- | --- | --- | --- |
| U0 | `kernel.base` | 什么是 id、预算、取消与稳定性？ | `03-naming.md`、`04-api-doctrine.md` |
| U1 | `kernel.space` | 单元格在哪里，盒是什么，结构如何转向？ | `05-space.md` |
| U2 | `kernel.occupant` | 单元格里有什么，内核可以读取什么？ | `06-occupants.md` |
| U3 | `kernel.expectation` | 一个舱位期望什么，期望如何组合？ | `07-expectations.md` |
| U4 | `kernel.claim` | 谁拥有哪些单元格，哪些声明相互冲突？ | `08-claims.md` |
| U5 | `kernel.blueprint` | 什么是可复用的结构，它如何被检查？ | `09-blueprints.md` |
| U6 | `kernel.document` | 蓝图如何变成数据，又如何变回？ | `10-documents.md` |
| U7 | `kernel.engine` | 一次匹配如何逐阶段运行？ | `11-engine.md` |
| U8 | `kernel.context` | 一次匹配如何变成类型化的运行时状态？ | `12-context.md` |
| U9 | `kernel.finding` | 失败如何像编译器那样被解释？ | `13-findings.md` |
| U10 | `kernel.remedy` | 我们如何修复、预览与解释一条诊断项？ | `14-remedies.md` |
| U11 | `kernel.spi` | 什么被冻结，什么可扩展，门面是什么？ | `15-spi.md` |
| A1 | `mc` | 1.21.1 NeoForge 如何绑定到内核？ | `16-adapter.md` |

## 6. 源集与纯净性边界

```
src/kernel/java      dev.vfyjxf.cumulus.kernel.**   pure Java 21, JSpecify only
src/kernelTest/java  kernel tests                   JUnit 5, no Minecraft
src/main/java        dev.vfyjxf.cumulus.**          mod entry + dev.vfyjxf.cumulus.mc.**
src/main/resources   mod metadata, datapack assets
src/test/java        adapter tests
```

`kernel` 是一个独立的 Gradle 源集，其编译 classpath 上没有 Minecraft。`main` 依赖
`kernel` 的输出。内核没有 mod 元数据，也没有加载器入口点。适配器不为每个加载器或每个
Minecraft 版本设子包：基线是 1.21.1 NeoForge，一次移植只改 `main`。精确的 Gradle 接线与
三道门禁（`compileKernelJava`、`kernelPurity`、`kernelTest`）见 `02-architecture.md` §6。

## 7. 非目标（Non-goals）

- 加载器抽象层（Fabric/Forge/NeoForge）。一个基线，一个适配器。
- 体素集合式的世界模型。占地轮廓是 `Cuboid` 的并集；需要泛洪填充的谓词通过带预算的
  派生器完成，而不是通过内核的体素类型。
- GUI 框架。`OverlayModel` 是纯数据；渲染属于宿主。
- 配方/能量/能力系统。这些是特征与派生器，不是内核名词。
- 网络同步。客户端需要什么由宿主决定；内核只输出数据。
- 从 ArchitectCumulus 迁移。ArchitectCumulus 是先前技术（prior art）；它的名称与代码
  都不被引入，也不承诺兼容垫片。

## 8. 阅读顺序

1. `01-glossary.md` —— 封闭词汇表。从头到尾读一遍。
2. `02-architecture.md` —— 单元、依赖方向、数据流、线程。
3. `03-naming.md` —— 冲突禁用名清单与后缀约定。
4. `04-api-doctrine.md` —— 组合、密封结果、可空性、黄金示例。
5. 你需要的那个单元文件，然后读 `17-worked-example.md` 了解端到端走查。

## 9. 本文集如何被证伪

每个模块文件都以**待决问题**与**证伪**结尾。审阅者的任务是找出一个具体的蓝图或世界
状态，使该模块的论断与某条不变量或某个同级模块相矛盾。一个模块被接受的条件是：其
草图能针对冻结的名称编译通过，其演算用例能在纸面上跑通，其待决问题要么已裁定，要么
被明确推迟。本轮不作任何“可实施（implementation-ready）”声明。
