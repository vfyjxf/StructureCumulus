# 18 — 验证计划（Verification plan）

> 这是针对设计的证伪计划，不是“测试已经存在”的声明。它列出各道门禁（gate）、每个单元
> 必须存活下来的反例，以及评审者接受的证据。反例目录刻意继承了 ArchitectCumulus 原型
> 暴露过的失败（`docs/spec/_work/PROTOTYPE-FINDINGS.md`，P-01…P-70）；重复其中任何一条
> 的新设计一律驳回，即使它的文字表述很干净。

## 1. 静态门禁（Static gates）

| 门禁 | 它证明什么 | 失败意味着 |
| --- | --- | --- |
| `compileKernelJava` | 从 `src/kernel/java` 无法触达任何 Minecraft/NeoForge 类型 | 某个内核类型泄漏了加载器依赖（I-1） |
| `kernelPurity` | 内核源文件中没有 `net.minecraft`、`net.neoforged`、`com.mojang` 的导入或全限定名 | 编译器没有看到的字符串级泄漏 |
| `kernelTest` | 内核只靠 JUnit 就能在普通 JVM 上运行 | 内核需要游戏引导（bootstrap） |
| NullAway（`OnlyNullMarked`） | 每个 `@NullMarked` 内核包都是空安全的 | 某个公开签名藏起了一处缺失 |
| `spotlessCheck` | 格式与导入顺序符合项目配置 | 风格漂移 |
| `specCheck`（本轮） | 模块中每个跨单元名称都在 `01-glossary.md` 中；没有模块导入 `02-architecture.md` §2 中位于它下方的包 | 词汇或依赖漂移 |

最后一道门禁是作用于 `docs/spec` 的一个小型 Python 脚本：从每个模块的词汇表
（vocabulary table）中抽取反引号标识符，与术语表（glossary）比对，并在存在
`May depend on` 行时解析该行。允许它是近似的；它的职责是抓出一个同义词，而不是充当
编译器。

## 2. 测试层次（Test layers）

| 层次 | 它测试什么 | 位置 |
| --- | --- | --- |
| **值代数（Value algebra）** | `LatticePoint`/`LatticeKey` 往返、`Cuboid`/`CuboidSet` 定律、朝向群公理 | `src/kernelTest/java/.../space` |
| **组合器定律（Combinator laws）** | `Expectations` 折叠优先级、绑定合并、代价聚合、门的原子性 | `.../expectation` |
| **声明（Claims）** | 冲突矩阵、预留生命周期、结算幂等 | `.../claim` |
| **引擎场景（Engine scenarios）** | 探测失败、成型、未决、陈旧重检、预算耗尽、取消 | `.../engine` |
| **上下文（Context）** | 派生顺序、环路拒绝、内部体积、增益折叠 | `.../context` |
| **诊断项（Findings）** | 全序、去重、上限、渲染器金文本（golden text）、解释查找 | `.../finding` |
| **补救方案（Remedies）** | 试运行、计划确定性、回滚、预览/修复一致性 | `.../remedy` |
| **适配器（Adapter）** | 捕获、朝向往返、重载、声明持久化、编辑回滚、命令 | `src/test/java` + GameTest |
| **黄金文档（Golden documents）** | 每个模式特性一份有效文档；规范化往返 | `src/kernelTest/resources` |

代数部分优先用性质测试（property test）（对带符号坐标和 48 个朝向做一个小型生成器）。
引擎部分优先用场景测试：一个由字符串映射搭出的假 `WorldView`、一个假 `ClaimLedger`、
一个假 `DirtyIndex`/`SectionStore`，这样测试就能断言精确的 `Spend` 和精确的诊断项顺序。

## 3. 反例目录（Counterexample catalogue）

每一行都是一个必须存在的测试。“Prior”指它防止的 ArchitectCumulus 诊断项。

| # | Prior（先前项） | 单元 | 测试 | 预期 |
| --- | --- | --- | --- | --- |
| C-01 | P-01 | U1 | 同一批单元格的两种不同盒分解产生相等的 `CuboidSet`；`volume()` 是单元格数量；`boxes()` 具有全局顺序。 | 相等、体积精确、单一规范顺序 |
| C-02 | P-02 | U1 | `Orientation.compose(a, b)` 先应用 `b`；对全部 48 个朝向都有 `compose(a, inverse(a)) == identity`；`all()` 有 48 个不同元素。 | 群定律成立；无重复 |
| C-03 | P-04 | U1 | 把同一个朝向变换施加到带符号的 `Heading` 上、以及施加到 `Cuboid` 上，都保持体积并精确映射角点。 | 无符号丢失；体积不变 |
| C-04 | P-05 | U2 | `SnapshotId` 不跨世界 `Comparable`；相等性包含世界 id 与修订号。 | 无跨世界排序 |
| C-05 | P-06 | U2 | `SnapshotPolicy` 以及每个带集合的 record 都做防御性复制，并暴露不可变、有序的视图。 | 变更无法改变值 |
| C-06 | P-39 | U2 | 一次 `DENIED` 读取返回 `ReadStatus.DENIED` 而不是空气，对它的期望产生 `UNREADABLE`；真正为空的单元格是 `READABLE`，其占用体为空气，并产生事实性的 `MISMATCH`。 | 拒绝 ≠ 空 |
| C-07 | P-32 | U2, U7 | 在已确立 `MISMATCH` 之后发生一次被拒读取，会追加一个 `UNREADABLE`，但绝不抹掉该不匹配；结果仍是 `Failed`。 | 不匹配占主导 |
| C-08 | P-45 | U3 | 谓词通过 `ProbeSession.claimsAt` 看到冻结的账本；另一个舱位在同一次尝试中的预留不可见，因此判定顺序无法改变。 | 账本可见、预留私有 |
| C-09 | P-69 | U3 | `ProbeSession.remaining()` 在一个步骤进行期间递减；谓词可以拒绝下一次读取而不消耗它。 | 预算是实时的，而不是步骤入口快照 |
| C-10 | P-43 | U3, U7 | 多单元格舱位由一条期望测试一次，并原子地声明整个长方体；半扇门的失配产生一条带两个标签的 `MISMATCH`。 | 一次调用、原子声明 |
| C-11 | P-37 | U7 | `Formed` 只来自 `settle`，绝不来自 `check`；它绑定每个已匹配舱位并结算每项声明，因此当计划检查过舱位时，它绝不带着空产物或零声明返回。 | 成型 ⇒ `settle` 已写入，产物 + 声明齐备 |
| C-12 | P-38 | U5, U7 | 依据公开模型写出的 `CheckPlan` 可以端到端执行：每项计算、派生与检查都收到各自的载体，读取无法作答的单元是 `UNREADABLE`，而不是被静默跳过。 | 没有死掉的计划条目 |
| C-13 | P-67 | U5, U7 | 一项计算、一次派生和一个检查都显式地获得各自的上下文 —— `ComputationContext` 用于区段或聚合，`DeriveContext` 用于派生，`FocusContext`（舱位、安放位、视图、折叠后的上下文、阶段、定位域）用于检查 —— 绝不通过反射。 | 不使用反射 |
| C-14 | P-70 | U7 | `Fingerprint` 有命名的算法和 `of(WorldView, Blueprint)` 工厂；相同内容跨 JVM 产生相同指纹。 | 稳定哈希 |
| C-15 | P-31 | U7 | 某一资源的预算为零、而其余资源仍有剩余时，不报告耗尽；只有读取实际被拒时才报告耗尽。 | 无假耗尽 |
| C-16 | P-49 | U7 | `ReadScope` 的累积是摊销线性的；170 格的遍历不是二次的。 | 足够线性 |
| C-17 | P-50 | U7 | 边界/普查步骤按盒比较计费，而不是按每格一次单元格读取计费。 | 代价与种类匹配 |
| C-18 | P-62 | U7, U8 | 派生器的读取被加入结果的 `ReadScope`，因此对内部的一次改动会使已成型实例失效。 | 派生读取计入 |
| C-19 | P-61 | U8 | 两个增益写入同一个 `AugmentKey` 时，按声明的 `AugmentFold` 折叠；只有单个增益时仍产生该键。 | 折叠有载体 |
| C-20 | P-52 | U8 | 派生步骤声明的实参通过 `DeriveContext` 到达派生器，其中包括已绑定舱位的长方体。 | 实参送达 |
| C-21 | P-53 | U8 | 派生读取计入预算并出现在 `Spend` 中；被丢弃的增益是一条诊断项，而不是静默。 | 计入并报告 |
| C-22 | P-12 | U6, U8 | 从 `DataView` 解码出的 `ContextValue` 携带其 `DataKind`；类型不匹配是一条诊断项，而不是强制转换。 | 种类判别符 |
| C-23 | P-19 | U6 | 至少有一份完整文档能编译：校验、装配，并在规范化之后逐字节往返。 | 存在一份可被接受的文档 |
| C-24 | P-14 | U9 | 每个随附的 `FindingCode` 都有 `Explanation`；`render` 从不依赖映射顺序；上限保留主诊断项。 | 可解释、有序 |
| C-25 | P-07 | U9 | `FindingBundle.empty()`、`of(List)` 与 `of(Finding...)` 可以共存而不产生有歧义的重载；空工厂保留一个不同的名字。 | 合法的 Java |
| C-26 | P-11 | U9, U11 | 注册表拒绝（重复 id、未知族、迟注册）有稳定的诊断码和一条诊断项；任何拒绝都不是裸异常。 | 拒绝即数据 |
| C-27 | P-68 | U2, U3 | 嵌套占用体暴露 `NestedView`；谓词可以下潜，深度计入预算，子级内部的改动会使父级失效。 | 下潜有门 |
| C-28 | P-44 | U11 | 内核携带一个两个入口都能到达的 `ClaimLedger`：`check` 读取一个修订号且不写入任何内容，它的 `Admitted` 携带计划中的声明与该修订号，缺失账本时计划保持 `@Nullable`、结果为 `Undecided(LEDGER_UNAVAILABLE)`，而只有 `settle` 写入行。 | 账本可达；`check` 中只读 |
| C-29 | — | U10 | 计划能校验、应用，并在计划中途失败后回滚，包括方块实体数据与多单元格编辑。 | 回滚完整 |
| C-30 | — | U10 | 为某条诊断项生成的预览与同一诊断项的修复计划一致：应用该计划会产出预览中的 `PRESENT` 单元格，并移除其中的 `MISSING` 单元格。 | 预览 ≡ 修复 |
| C-31 | — | U11 | 由 `Catalogs` 快照创建的内核不受后续附属（addon）注册的影响；两个内核相互隔离。 | 冻结 |
| C-32 | — | A1 | `Heading`/`Turn`/`Tilt`/`Flip` 经 MC 的 `Direction`/`Rotation`/`Mirror` 往返；被旋转的门的方块状态被精确重映射。 | 朝向一致性 |
| C-33 | — | A1, 20 | 一个方块在 FORM 派发之后、刷新之前发生变化；结果必须未通过 `changeCount` 新鲜度项，且绝不结算。 | 脏标记胜过过期版本 |
| C-34 | — | A1, 20 | 一个箱子在运行中途被清空；缓存的区段副本仍说 `MATCH`；载荷标记使结果为 `DROPPED`，而不是 `Formed`。 | 载荷脏与方块状态脏是分开的 |
| C-35 | — | 20 | 两个相隔 200 ms 的 COMMAND 提交：只有一个 RUNNING 任务，第二个被合并；较旧的 ticket 被丢弃，较新的运行。 | 单任务在飞、ticket 胜出 |
| C-36 | — | 20 | FORM 期间 level 卸载：不结算、不触发 `onSettled`；`done()` 仍以 `Discarded` 完成。 | 无世界效果也能到达终态 |
| C-37 | — | 20 | 运行中途区块卸载、level 仍存活：快照结果对其修订号保持自洽；下一次捕获为 `NOT_CAPTURED`，而不是空气。 | 区块卸载 ≠ 失败 |
| C-38 | — | 20 | 1.21.1 中 `y=64`、原点 `-64`：`ChunkGrid.floorDiv` 得出 `cy=8`；裸 `>>4` 会给出错误的区段。 | 负原点网格 |
| C-39 | — | 20 | 一扇门跨越两个 16³ 区块：它是全局的，绝不会被缓存为本地 MATCH；只脏了一半的区块无法成型。 | 跨区块/原子步骤 |
| C-40 | — | 20 | 缓存的步骤结果重跑时带不同的 `FindingId`；缓存中的诊断项携带 `NONE` 并被重新铸造；输出逐字节相同。 | 无过期 id |
| C-41 | — | 20 | 启用可选的 `SectionStore` 时，两个区段共享一个条目；移除其中一个被监视的结构；另一个保留其副本，且没有更新丢失。 | 共享存储的生命期 |
| C-42 | — | 20 | 一个附加包期望在循环中不读取任何格；看门狗取消，内核的读取间隔轮询无法中断它；调度器降级而不杀死线程池。 | 卡死的自定义步骤 |
| C-43 | — | 20 | `AUTO` 脏事件：只跑 `PROBE`；通过后另投 `FORM`；AUTO 任务里不隐藏完整 `FORM`。 | AUTO 保持轻量 |
| C-44 | — | 20 | 默认设置下，两个重叠快照各自持有独立区段副本；启用 `SectionStore` 才是唯一的共享路径。 | 默认逐快照 |
| C-45 | — | 20 | 一头牛在两次检查之间走出实体舱位；派发时重查 `ReadScope.entities`，发现足迹变化，不复用旧 `MATCH`。 | 实体移动在派发时发现 |
| C-46 | — | 20 | 并行区段检查是 `MatchPolicy.parallel`，默认关闭；启用它既不改变结果，也不改变 `Spend`。 | 并行是 opt-in |
| C-47 | — | 20 | 普通 `Expectation` 本身永不被缓存；一条条目是某个单元的贡献或产物，且只在 `CacheKey(plan, siting, unit, closureDigest)` 相等时命中。 | 可缓存性是 `(unit, closureDigest)` |
| C-48 | — | 21 | 紧凑熔炉 JSON 规范化后与 canonical JSON 得到同一个 `Blueprint`。 | 单一运行时模型 |
| C-49 | — | 21 | 在 `DocumentProfile.AUTO` 下，根同时含 `segments` 与 `shell` 会被拒绝；检测不是后写覆盖。 | profile 检测互斥 |
| C-50 | — | 21 | 没有 `size` 的 `shell`/`cavity` 被拒绝；不推断零尺寸。 | 几何需要 bounds |
| C-51 | — | 21 | capture/import 遇到不支持的方块实体时产生诊断项，不静默丢载荷。 | 工具绝不静默丢数据 |

## 4. 演练场景（Worked scenarios）

1. **小型**：一个 3×3×3 的外壳，一个开口。在错误的墙面上探测失败，成型成功，产物绑定
   三个舱位，声明完成结算，诊断项为空。
2. **中型**：两台机器在同一格共享一个舱门。一个 `SHARED` 声明可以共存；同一格上的
   `EXCLUSIVE` 声明在 `SETTLE` 时产生 `ClaimConflict`。
3. **大型**：巨型熔炉——7×6×7 外壳、开口、内部舱位；探测、成型、派生内部体积（100）、
   渲染一条缺失墙面的诊断项、建造、回滚一次失败的建造，然后改动一个内部单元格，只重检
   受影响的步骤。
4. **陈旧**：一个已成型实例、一个触及被读取单元格的 `ChangeSet`、一次针对性复查，
   复用未变化的判定并重跑被触及的步骤。
5. **被拒**：舱位中有一个区块被拒的单元格；结果是 `Undecided` 而不是 `Failed`，
   诊断项指向读取状态，而不是某个方块。
6. **门**：一个两格的门；一半存在、一半缺失；一条带两个标签的 `MISMATCH` 和一项原子声明。

## 5. 按不变量列出的证明义务（Proof obligations by invariant）

| 不变量 | 证明义务 | 证据 |
| --- | --- | --- |
| I-1 | 内核在无 MC 的情况下编译；纯净性扫描为空 | 构建日志 |
| I-2 | 方块、方块实体、实体与嵌套结构都通过同一个 `OccupantSnapshot` 接口匹配 | C-06, C-27 |
| I-3 | 每项占地轮廓（footprint）操作都是 `Cuboid`/`CuboidSet`；公开的占地轮廓中没有 `Set<LatticeKey>` | API 扫描 |
| I-4 | 相同输入 ⇒ 相同结果；无时钟/随机/全局状态 | C-02, C-14, 确定性测试 |
| I-5 | 内核签名中没有 `Entity`；`Siting` 由锚点候选与策略共同导出 | API 扫描, C-13 |
| I-6 | 每次检查都在引擎选定的 `Stage` 中运行，并带有各自的 `CheckPlan`；`DESIGN` 不读取世界 | C-12 |
| I-7 | 诊断项是唯一的结果通道；诊断码稳定 | C-24..C-26 |
| I-8 | 内核只随附 `Expectations` 与值类型；没有 MC 谓词 | API 扫描 |
| I-9 | `Computation`/`Deriver` 读取快照与上下文，绝不重新遍历蓝图 | C-18..C-21 |
| I-10 | 每个已结算的单元格都携带一个 `sharing` 值；冲突是一条诊断项 | C-11, C-28 |
| I-11 | 修复/预览/解释共享 `Locus` 与 `Applicability` | C-29, C-30 |
| I-12 | 一份文档可校验、规范化并往返 | C-23 |
| I-13 | 没有 stringly-typed（以字符串承载类型）的公开值通道；族是密封的 | API 扫描 |
| I-14 | 没有公开成员无条件抛出；缺失表示为 `@Nullable`/诊断项 | API 扫描 |
| I-15 | 内核中没有 `System.nanoTime`、`Random`、`Instant.now` | grep + 评审 |

## 6. 评审流程（Review procedure）

1. 运行词汇/依赖脚本；在阅读正文之前先修掉同义词。
2. 对照术语表条目，阅读该模块的 **API 设计** 与 **语义**；与术语表矛盾的签名是模块的
   缺陷，而不是术语表的缺陷。
3. 应用该单元对应的反例目录行：模块的契约能让该测试通过吗？如果不能，模块就不完整。
4. 检查 **参考代码** 中的非匹配路径与 `Undecided` 路径。
5. 检查 **待决问题** 是否都是带有拟定裁决的真实问题，而不是被推迟的工作。
6. 在 `_index.md` 中记录判定：drafted → reviewed，并附上该模块覆盖的反例和推迟的反例。

## 7. 本轮设计的退出标准（Exit criteria for this design round）

- 十二个模块文件全部存在，并通过静态的词汇/依赖门禁。
- 每一行反例要么被某个模块契约覆盖，要么被明确推迟，并带有负责人和原因。
- `04-api-doctrine.md` §8 中的黄金示例与 `17` 中的演练场景只使用冻结的名字。
- 内核源集在“每个包都有 `package-info.java`、且没有任何 MC 导入”的情况下编译通过。
- 没有模块声称“可实施（implementation-ready）”；交付物是一份可证伪的设计。
