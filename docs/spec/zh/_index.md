# StructureCumulus 规范——索引（index）

> 设计草案 v0.2，2026-10-20，合并设计处于修订 5，并含 `23`/`24` 两份 2026-10-23 的声明决策
> 记录。五个基础文件、十二个单元文件、合并设计（`22`）与两份决策记录都已草稿；一次对账把
> 跨单元签名定下来（见 `19`）。其余待决问题按单元记录，也记录在 `22` §16。这是一份可证伪的
> 设计，不是实现。

## 路由表（Routing table）

| 如果你需要…… | 阅读 |
| --- | --- |
| 愿景、十五条需求、不变量、非目标 | [00-overview.md](00-overview.md) |
| 任何内核名字的含义，以及禁用名单 | [01-glossary.md](01-glossary.md) |
| 包图、数据流、阶段、线程与源集 | [02-architecture.md](02-architecture.md) |
| 命名规则与九节模块模板 | [03-naming.md](03-naming.md) |
| 组合、类型化键、密封结果、黄金示例 | [04-api-doctrine.md](04-api-doctrine.md) |
| 单元格、长方体、48 个朝向、安放位、定位域 | [05-space.md](05-space.md) |
| 占用体、快照、世界视图 SPI、不可读单元格 | [06-occupants.md](06-occupants.md) |
| 期望、判定、组合器定律、门用例 | [07-expectations.md](07-expectations.md) |
| 声明、共享、独占、预留、账本 | [08-claims.md](08-claims.md) |
| 舱位、接缝、片段、锚点、蓝图、阶段计划 | [09-blueprints.md](09-blueprints.md) |
| 文档文法、规范化、装配、数据生成 | [10-documents.md](10-documents.md) |
| 匹配器、预筛、预算、失效、代际 | [11-engine.md](11-engine.md) |
| 派生器、类型化上下文、增益、运行时状态、升级 | [12-context.md](12-context.md) |
| 诊断项、诊断码、标签、排序、渲染器、`--explain` | [13-findings.md](13-findings.md) |
| 修复计划（`solve` 操作）、叠加层、试运行、事务、自动建造 | [14-remedies.md](14-remedies.md) |
| 目录、门面、扩展注册、稳定性 | [15-spi.md](15-spi.md) |
| 1.21.1 NeoForge 适配器：捕获、朝向、重载、命令 | [16-adapter.md](16-adapter.md) |
| 一个巨型熔炉走完整个流水线 | [17-worked-example.md](17-worked-example.md) |
| 验证计划、门禁与反例 | [18-verification.md](18-verification.md) |
| 刻意排除的特性，以及对账记录 | [19-open-proposals.md](19-open-proposals.md) |
| 工作线程执行、区段快照、脏状态、区段缓存 | [20-execution-and-sections.md](20-execution-and-sections.md) |
| 紧凑 JSON 作者 profile 与 capture/import 工具 | [21-authoring.md](21-authoring.md) |
| 合并设计（含共享中间状态与跨区段判据） | [22-consolidated-design.md](22-consolidated-design.md) |
| 声明入口：只读的 `check` 与写入的 `settle`、释放路径，以及阶段为何不再被声明 | [23-claim-entry.md](23-claim-entry.md) |
| 声明词汇：三个问题（要证明什么、要占什么、声明如何相遇）、R1–R4 | [24-claim-vocabulary.md](24-claim-vocabulary.md) |

## 单元（Units）

| 单元 | 文件 | 状态 | 负责人 |
| --- | --- | --- | --- |
| U0 基础 | `00`–`04` | 已评审 | 主代理 |
| U1 空间 | `05-space.md` | 已草稿 | 子代理 |
| U2 占用体 | `06-occupants.md` | 已草稿 | 子代理 |
| U3 期望 | `07-expectations.md` | 已草稿 | 子代理 |
| U4 声明 | `08-claims.md` | 已草稿 | 子代理 |
| U5 蓝图 + 计划 | `09-blueprints.md` | 已草稿 | 子代理 |
| U6 文档 | `10-documents.md` | 已草稿 | 子代理 |
| U7 引擎 | `11-engine.md` | 已草稿 | 子代理 |
| U8 上下文 | `12-context.md` | 已草稿 | 子代理 |
| U9 诊断项 | `13-findings.md` | 已草稿 | 子代理 |
| U10 补救方案 | `14-remedies.md` | 已草稿 | 子代理 |
| U11 SPI / 门面 | `15-spi.md` | 已草稿 | 子代理 |
| A1 适配器 | `16-adapter.md` | 已草稿 | 子代理 |
| X 执行 | `20-execution-and-sections.md` | 已评审 | 主代理 |
| A2 作者格式 | `21-authoring.md` | 已草稿 | 主代理 |
| R 合并设计 | `22-consolidated-design.md` | 评审中 | 主代理 |
| 跨单元 | `17`, `18`, `19`, `20` | 已评审 | 主代理 |

## 评审门禁（Review gates）

1. **词汇门禁**——模块中每个跨单元名称都以同一个负责人出现在 `01-glossary.md` 中；
   不引入任何同义词。
2. **依赖门禁**——模块只导入 `02-architecture.md` §2 中位于它上方的包。
3. **形状门禁**——`03-naming.md` §8 的九节齐备，且参考代码遵守 `04-api-doctrine.md` §11。
4. **不变量门禁**——`00-overview.md` §4 中该单元涉及的每条不变量，要么被证明，要么被
   明确推迟。
5. **证伪门禁**——模块指出一个会打破它的具体蓝图、世界状态或测试。

## 文档状态词汇（Document status vocabulary）

- **已草稿（drafted）**——已写出，未评审。
- **已评审（reviewed）**——通过五道门禁；待决问题已裁决或已推迟。
- **已冻结（frozen）**——未经术语表修订，不再改动名字。
