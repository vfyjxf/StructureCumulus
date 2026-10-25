# 02 — 架构（Architecture）

> 回答：哪个单元可以依赖哪个，一次操作如何流动，什么在哪个线程上运行，以及 Minecraft
> 边界在哪里。先读 `00-overview.md` §5 了解单元编号；本文件默认你已知道它们。

## 1. 两个层次，一个方向

```
┌────────────────────────────── src/main/java ──────────────────────────────┐
│  dev.vfyjxf.cumulus            mod entry, FML wiring, commands            │
│  dev.vfyjxf.cumulus.mc         MC 1.21.1 / NeoForge 21.1.x adapter (A1)   │
└───────────────────────────────────┬───────────────────────────────────────┘
                                    │ depends on
┌───────────────────────────────────▼───────────────────────────────────────┐
│  dev.vfyjxf.cumulus.kernel.**   pure Java 21, no Minecraft on classpath   │
│  base · space · occupant · expectation · claim · blueprint ·              │
│  document · engine · context · finding · remedy · spi                     │
└───────────────────────────────────────────────────────────────────────────┘
```

箭头是单向的。内核绝不引用适配器类型，哪怕是在一句点名适配器的注释里（I-1）。适配器
可以使用每一个内核包。

## 2. 包依赖图

边从消费者指向提供者。包级别没有环；当两个概念看起来互相需要时，其中一个是声明式 id，
另一个拥有编译后的形态（见下文）。

```
base ──────────────────────────────────────────────────────────────┐
 ├── space ──────────────────────────────────────────────────────┐  │
 │    ├── finding ────────────────────────────────┐              │  │
 │    ├── occupant ───────────────────────────┐   │              │  │
 │    └── claim ──────────────────────────┐   │   │              │  │
 │                                        │   │   │              │  │
 │  expectation ◄── occupant, claim, finding, space, base         │  │
 │  blueprint   ◄── expectation, claim, space, base               │  │
 │  document    ◄── blueprint, expectation, finding, space        │  │
 │  engine      ◄── all of the above                              │  │
 │  context     ◄── engine, occupant, finding, space              │  │
 │  remedy      ◄── blueprint, occupant, expectation, finding     │  │
 └── spi        ◄── every package above ──────────────────────────┘  │
```

三处刻意的破环：

| 诱人的环 | 破环方式 |
| --- | --- |
| `blueprint` ↔ `context`：蓝图拥有自己的阶段计划 | `CheckPlan` 与蓝图同住在 `blueprint`；`Computation` 与 `StatePhase` 在 `context`，`ReadScope` 在 `base`。 |
| `blueprint` ↔ `context`：蓝图声明派生 | 蓝图存储 `DerivationSpec(NamespacedId deriver, Arguments, int order)`；`context` 通过目录编译该计划。 |
| `finding` ↔ `remedy`：诊断项携带一个建议计划 | `Suggestion` 携带消息、一个 `Applicability` 与一个可选补救方案 id；具体的 `EditPlan` 由 `kernel.solve` 产生。 |

`MatchOutcome`、`BayBinding`、`SeamBinding` 与 `Census` 位于 `engine`，而不是 `context`：
它们是检测输出。`context` 消费它们并加上 `StructureContext`。

## 3. 一次匹配的流程

```
host code
  │  BlueprintDocument (JSON/SNBT/codec)
  ▼
[document] canonicalise → compile (DESIGN: validate + assemble segments)
  │  Blueprint
  ▼
[space]    resolve anchor candidate + OrientationPolicy → Siting
  │
  ▼
[engine]   MatchRequest(blueprint, siting, lease, policy, budget,
                        pins, record, owner, ticket)
  │
  ├─ DESIGN  no view; document and geometry checks
  ├─ PROBE   bounds, census, anchor fingerprint; cheap rejection
  ├─ FORM    bays in cost/specificity/declaration order; claims deferred
  ├─ SETTLE  ledger conflicts, seams, legality; the write is the entry point's, not the stage's
  │
  └──► two entry points, one writer
         check  ──► Admitted(planned, ledgerRevision)            never writes
         settle ──► Formed(outcome, claims, findings)             writes the claims
                        │
                        ▼
                   [context] Computation/Deriver → ContextKey → StructureContext + RuntimeState
         settle ──► Failed / Undecided / Dropped(findings)
           │
           ▼
        [finding] bundle → human / JSON / SARIF renderer
           │
           ▼
        [remedy] solve(finding) → Repair(EditPlan) | Preview(OverlayModel) | Explain
```

引擎中没有任何东西写世界。`EditExecutor` 是唯一的写路径，它是一个宿主 SPI，在
`PlanValidator` 批准计划之后被调用。

## 4. 阶段与排序

`Stage` 是“现在需要检查什么”的单位，不是时间单位。

| 阶段 | 是否读取世界 | 典型计划 | 快速失败契约 |
| --- | --- | --- | --- |
| `DESIGN` | 否 | 模式、引用、几何、锚点角色、声明重叠、派生环 | 每个错误都是 `ERROR`；不可能出现 `UNREADABLE` |
| `PROBE` | 是，粗粒度 | `BOUNDS`、`CENSUS`、`ANCHOR` | 一次廉价的未命中直接返回 `Failed`，不打开任何舱位 |
| `FORM` | 是，细粒度 | `BAY`、`SEAM` | 每个检查都跑；一个舱位的应答是一个 `Reply`，绝无部分通过 |
| `SETTLE` | 是，账本 | `CLAIM`、合法性、派生前置条件 | 冲突是诊断项；只有 `settle` 会写入声明并产出 `Formed`，而此处的冲突是 `Failed(SC-CLM-0002)`，什么也不写入 |

定向重检不是一个阶段：在请求上带 `ChangeSet` 的 `check` 会重跑 `ReadSet` 与之相交的那些
步骤，其余的复用先前的判定。

阶段内的顺序是全序：区段计算 → 屏障 → 按 `inputs()` 的 Kahn 序的聚合计算 → 检查，每组内部按
声明顺序。全序是确定性契约的一部分（I-4、I-7）。

`Stage` 在 `base` 中，因为诊断项要记录产生它的阶段；`CheckPlan` 在 `blueprint` 中，因为
`expand(S)` 从蓝图声明的东西产出它。

## 5. 线程与确定性

| 关注点 | 规则 |
| --- | --- |
| 捕获 | `LevelCapture.capture` 只在服务器线程上运行；它把 16³ 区段（`PalettedContainer.copy`）复制进不可变的 `LevelSnapshot`；未加载的区块是 `NOT_CAPTURED`，绝不是空气。 |
| 匹配 | 对快照纯净；运行在宿主工作线程上。无世界访问、无账本写入、无 `ThreadLocal`、无时钟。`StructureKernel.check` 是同步的且不写入任何东西；`settle` 是唯一的写入者，执行器归宿主调度器所有（`20`）。 |
| 调度 | 每个服务器在 `ServerState` 中有一个 `MatchScheduler`；每个 `(world, owner)` 至多一个在飞任务；更新的请求合并；结算与回调在服务器线程上运行。 |
| 账本 | 在派发时冻结（`FrozenLedger`），匹配期间只读；结算是在捕获门禁（`20` §5.4）之后于服务器线程上的一次 CAS。 |
| 快照存储 | 每世界一个 `SectionStore`，保存不可变 `SectionSnapshot`；快照持有强引用，存储是弱缓存，检查中途绝不惰性解析区段。 |
| 脏状态 | `LevelChunk.setBlockState` / `Level.blockEntityChanged` / 实体与区块事件标记区段或载荷；派发前在服务器线程上运行一次有界的一次性刷新。 |
| 派生 | 对 `MatchOutcome` + 快照 + 冻结账本纯净；可以运行在同一工作线程上。保留的 `InstanceRecord` 会丢弃视图。 |
| 诊断项 | 不可变；一个汇只能从一个线程调用。 |
| 预算 | 粗粒度资源上限，只有两条腿：`cells` 与 `sections`。没有逐单元单价，没有 `Budget.nanos`，没有 `TimeSource`；宿主看门狗可以通过 `CancelToken` 取消。 |
| 随机性 | 无。 |
| 迭代 | 任何带定义顺序的公开 `List`/`Set` 都按该顺序构建；`HashMap` 的迭代顺序绝不影响结果。 |

确定性声明：对于固定的 `(blueprint, snapshot, siting, policy, catalog snapshot,
frozen ledger, cancellations)`，`MatchResult`、`FindingBundle` 的顺序与 `Spend` 在不同运行与
不同 JVM 上完全一致。线程选择、队列顺序与任何刷新的大小都不进入该元组。

## 6. 源集布局与构建门禁

本轮设计中，项目是一个 Gradle 模块，含两个生产 Java 源集（`kernel`、`main`）及其测试
源集（`kernelTest`、`test`）。内核尚不是独立发布的产物；它是
一个**编译 classpath 上没有 Minecraft** 的源集，这比包约定更强。

```
StructureCumulus/
├── src/kernel/java      dev.vfyjxf.cumulus.kernel.**      pure Java 21
├── src/kernelTest/java  kernel unit tests                 JUnit 5 only
├── src/main/java        dev.vfyjxf.cumulus.**             mod + mc adapter
├── src/main/resources   META-INF/neoforge.mods.toml, assets, data
└── src/test/java        adapter tests
```

Gradle 接线（目标形态；首个参考代码落地时更新构建文件）：

```groovy
sourceSets {
    kernel {
        java.srcDir 'src/kernel/java'
    }
    kernelTest {
        java.srcDir 'src/kernelTest/java'
        compileClasspath += sourceSets.kernel.output
        runtimeClasspath += sourceSets.kernel.output
    }
}

dependencies {
    kernelCompileOnly 'org.jspecify:jspecify:1.0.1'
    kernelTestImplementation 'org.junit.jupiter:junit-jupiter:5.7.1'
    implementation sourceSets.kernel.output
}

tasks.register('kernelPurity') {
    // scan src/kernel/java; fail on net.minecraft / net.neoforged / com.mojang imports
}

tasks.register('kernelTest', Test) {
    testClassesDirs = sourceSets.kernelTest.output.classesDirs
    classpath = sourceSets.kernelTest.runtimeClasspath
}
```

两道门禁，都挂在 `check` 上：

1. **`compileKernelJava`** —— 编译器本身证明没有任何 Minecraft 类型可达。
2. **`kernelPurity`** —— 文本扫描捕获未在本机编译的文件中的全限定名或 import。扫描是
   兜底，不是主门禁。

第三道门禁 `kernelTest` 在不启动 Minecraft 的情况下运行纯净性测试（空间代数、朝向群、
组合器定律、诊断项排序）。`main` 的 `test` 覆盖适配器与 GameTest 测试框架。

内核没有 `@Mod`、没有 `neoforge.mods.toml`、没有点名任何加载器的 service 文件。门面由
适配器的 mod 入口点构造。

## 7. 扩展点

完整清单。每一项都是一个接口或一个接口记录（record of interfaces）；内核只交付平凡实现
（I-8）。

| 扩展点 | 接口 | 归属 | 典型实现 |
| --- | --- | --- | --- |
| 世界捕获 | `WorldCapture` | 宿主 | `LevelCapture` |
| 世界视图 | `WorldView` | 宿主 | `LevelSnapshot` |
| 占用体解析 | `OccupantResolver` | 宿主 | `WorldOccupants` |
| 期望 | `Expectation` / `ExpectationFactory` | 附加组件 | 方块、特征、门、舱口谓词 |
| 声明账本 | `ClaimLedger` | 宿主 | `SavedClaims` |
| 派生器 | `Deriver` | 附加组件 | 内部容积、等级、普查 |
| 补救方案提供者 | `RemedyProvider` | 附加组件 | 自动建造、叠加层、解释 |
| 计划执行 | `EditExecutor` | 宿主 | `WorldEdits` |
| 诊断项汇 | `FindingSink` | 宿主 | 日志、聊天、JSON |
| 数据翻译 | `DataBridge<T>` / `DataFrontend` | 宿主/附加组件 | 编解码、JSON、SNBT |
| 片段生成 | `SegmentEmitter` | 外部工具 | 数据包生成器 |

Catalogs 在 `StructureKernel.create(Catalogs)` 时冻结实例。在该点之后添加扩展需要
一个新的内核；内核不持有可变注册表。

## 8. 适配器不得做的事

- 它不得让内核了解方块。`OccupantDescriptor` 是内核唯一见到的形状；方块状态是适配器
  侧的原始值。
- 它不得把策略搬进适配器。朝向、声明策略、阶段计划与诊断项排序都是内核数据；适配器
  只负责实现它们。
- 它不得隐藏不可读的单元格。区块未加载、读取被拒绝与快照越界（snapshot bound）都是
  `UNREADABLE`，绝不是“空气”（见 `06-occupants.md` §5）。
- 它不得从匹配器或派生器中修改世界。只有 `EditExecutor` 写入。

## 9. 跨单元审阅规则

1. 模块只能 import §2 中位于它上方的包，外加 `base`。
2. 跨单元名称定义在 `01-glossary.md` 中；新名称要提出，而不是杜撰。
3. 模块的草图只使用冻结的名称；它需要但不拥有的名称是一条依赖说明，不是私有副本。
4. 发现缺失概念的模块在**待决问题**中报告并停下；它不会悄悄扩大自己的范围。
