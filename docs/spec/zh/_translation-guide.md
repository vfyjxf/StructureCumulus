# 中文翻译规范（审阅版）

> 本目录是 `docs/spec/` 英文规范的中文对照版，供中文审阅使用。英文原文仍是规范
> 的权威版本；中文版不得改变任何技术结论、签名、数值、错误码或代码语义。

## 1. 文件映射

| 英文原文 | 中文版 |
| --- | --- |
| `docs/spec/00-overview.md` | `docs/spec/zh/00-overview.md` |
| `docs/spec/01-glossary.md` | `docs/spec/zh/01-glossary.md` |
| `docs/spec/02-architecture.md` | `docs/spec/zh/02-architecture.md` |
| `docs/spec/03-naming.md` | `docs/spec/zh/03-naming.md` |
| `docs/spec/04-api-doctrine.md` | `docs/spec/zh/04-api-doctrine.md` |
| `docs/spec/05-space.md` … `16-adapter.md` | 同名文件 |
| `docs/spec/17-worked-example.md` | `docs/spec/zh/17-worked-example.md` |
| `docs/spec/18-verification.md` | `docs/spec/zh/18-verification.md` |
| `docs/spec/19-open-proposals.md` | `docs/spec/zh/19-open-proposals.md` |
| `docs/spec/20-execution-and-sections.md` … `21-authoring.md` | 同名文件 |
| `docs/spec/22-consolidated-design.md` | `docs/spec/zh/22-consolidated-design.md` |
| `docs/spec/23-claim-entry.md` | `docs/spec/zh/23-claim-entry.md` |
| `docs/spec/24-claim-vocabulary.md` | `docs/spec/zh/24-claim-vocabulary.md` |
| `docs/spec/_index.md` | `docs/spec/zh/_index.md` |
| `docs/README.md` | `docs/README.zh.md`（链接改指 `spec/zh/`） |

## 2. 硬性规则

1. **代码块逐字保留**：Java、JSON、伪代码、命令行、错误码、类型名、方法名、字段名、
   文件路径、`SC-XXX-NNNN` 一律不翻译。代码块内的英文注释也保留原样，避免改变可编译
   语义；需要解释时写在代码块外的中文正文里。
2. **标识符不翻译**：`OccupantSnapshot`、`MatchResult.Formed`、`ReadStatus.DENIED`、
   `CuboidSet` 等保持英文与反引号。中文术语只用于正文叙述，首次出现写成
   `中文（English）`，之后可只用中文或英文类型名。
3. **不增删技术内容**：不补充新论点、不省略反例、不改数值（如 294、146、100）、
   不改错误码、不改 API 签名、不改“必须/不得”的规范强度。
4. **标题结构不变**：保留原章节编号与层级，标题译为中文，可在括号中保留英文，例如
   `## 3. API 设计（API design）`。九段式模板的段名保持固定译名。
5. **表格结构不变**：表头翻译，单元格中的标识符、代码、路径、错误码保持英文；
   表格行数与列数不变。
6. **链接保持可用**：中文文件与英文文件同名，因此同目录相对链接（如
   `[01-glossary.md](01-glossary.md)`）保持不变即可；指向 `docs/README.md` 的链接改指
   `docs/README.zh.md`。
7. **不确定时保留英文**：一句原文有歧义时，中文后加括号保留原短语，不要自行推断。
8. **简体中文**，技术写作风格：短句、少修辞、术语一致；不使用“我们认为”“显然”等
   主观措辞。

## 3. 固定术语表

| English | 中文 | 备注 |
| --- | --- | --- |
| kernel | 内核 | 不译作“核心” |
| facade | 门面 | `StructureKernel` 的门面层 |
| blueprint | 蓝图 | 类型名 `Blueprint` 保留 |
| argument / arguments | 实参 / 实参表 | 实际传入的值；`Arguments`，不要与“形参”混用 |
| parameter | 形参 | 声明侧的身份；`ParameterKey` 译“形参键” |
| segment | 片段 | `Segment` |
| bay | 舱位 | 禁用“槽位”，避免与 MC `Slot` 混淆 |
| seam | 接缝 | `Seam` |
| anchor / anchor role | 锚点 / 锚点角色 | `AnchorSpec` 锚点规格 |
| siting | 安放位 | `Siting`，不要译作“姿势” |
| placement | 摆放 | `Placement` |
| orientation | 朝向变换 | `Orientation`；`Heading` 译“朝向” |
| turn / tilt / flip | 偏转 / 倾斜 / 镜像 | 三个生成元，类型名保留 |
| lattice point / key | 格点 / 格键 | `LatticePoint` / `LatticeKey` |
| cuboid / cuboid set | 长方体 / 长方体集合 | `Cuboid` / `CuboidSet` |
| locus | 定位域 | `Locus`，不要译作“跨度” |
| occupant | 占用体 | 方块、实体、嵌套结构统一称占用体 |
| occupant snapshot | 占用体快照 | `OccupantSnapshot` |
| trait | 特征 | `Trait` / `TraitKey` |
| world view | 世界视图 | `WorldView` |
| read status | 读取状态 | `ReadStatus` |
| snapshot | 快照 | `SnapshotId` / `SnapshotPolicy` |
| capture | 捕获 | `WorldCapture` |
| resolver | 解析器 | `OccupantResolver` |
| expectation | 期望 | `Expectation`；不译作“谓词” |
| predicate | 谓词 | 仅用于说明被禁用的概念；类型名不叫 Predicate |
| verdict / reply | 判定 / 应答 | `Verdict` / `Reply` |
| binding | 绑定 | `Bindings` / `BindingKey` |
| combinator | 组合器 | `Expectations` |
| claim | 声明 | `Claim`；`Sharing` 共享规则 |
| ledger | 账本 | `ClaimLedger` |
| reservation | 预留 | `Reservation` |
| conflict | 冲突 | `ClaimConflict` |
| plan | 计划 | `CheckPlan` 检查计划 |
| stage | 阶段 | `Stage` 的枚举值保留英文 |
| check step | 检查步骤 | `CheckStep` |
| match | 匹配 | 名词/动词同形 |
| formed / failed / undecided | 已成型 / 失败 / 未决 | `MatchResult` 的分支 |
| outcome | 产物 | `MatchOutcome` |
| census | 普查 | `Census` |
| prescreen | 预筛 | `PrescreenIndex` |
| fingerprint | 指纹 | `Fingerprint` |
| read set / change set | 读取集 / 变更集 | `ReadSet` / `ChangeSet` |
| invalidation | 失效 | `InvalidationIndex` |
| instance generation | 实例代际 | 与“数据生成”区分 |
| context | 上下文 | `StructureContext` 结构上下文 |
| derive / deriver | 派生 / 派生器 | `Deriver` |
| derivation plan | 派生计划 | `DerivationPlan` |
| augment | 增益 | `Augment` / `AugmentFold` 增益折叠 |
| runtime state | 运行时状态 | `RuntimeState` |
| finding | 诊断项 | `Finding`；不译作“发现” |
| finding code | 诊断码 | `SC-XXX-NNNN` 保留 |
| severity | 严重级别 | `ERROR` / `WARNING` / `INFO` 保留 |
| label | 标签 | `Label` / `LabelRole` |
| note | 注释 | `Note` |
| suggestion | 建议 | `Suggestion` |
| applicability | 适用性 | `MACHINE_APPLICABLE` 等保留 |
| finding bundle | 诊断包 | `FindingBundle` |
| renderer | 渲染器 | `FindingRenderer` |
| explain book | 解释手册 | `ExplainBook` |
| remedy | 补救方案 | `Remedy` |
| repair / preview / explain | 修复 / 预览 / 解释 | `Remedy` 的三个分支 |
| edit plan | 编辑计划 | `EditPlan` |
| edit transaction | 编辑事务 | `EditTransaction` |
| edit executor | 编辑执行器 | `EditExecutor` |
| overlay | 叠加层 | `OverlayModel` |
| cell mark | 格标记 | `CellMark` |
| catalog | 目录 | `Catalog` / `Catalogs` |
| compile | 编译 | `CompileResult` 编译结果 |
| adapter | 适配器 | 适配器类名保留（`LevelCapture`、`LevelSnapshot` 等） |
| baseline | 基线 | 1.21.1 NeoForge |
| pure / purity | 纯净 / 纯净性 | kernel purity 内核纯净性 |
| invariant | 不变量 | I-1…I-15 保留编号 |
| requirement | 需求 | R-01…R-15 保留编号 |
| counterexample | 反例 | |
| falsification | 证伪 | 模块第九段 |
| open question | 待决问题 | |

## 4. 九段式模板译名

| 英文 | 中文 |
| --- | --- |
| Scope | 范围 |
| Vocabulary | 术语 |
| API design | API 设计 |
| Semantics | 语义 |
| Reference sketch | 参考代码 |
| Use cases | 用例 |
| Proofs | 证明 |
| Dependencies and open questions | 依赖与待决问题 |
| Falsification | 证伪 |

## 5. 审阅标记

- 翻译者认为原文本身有矛盾时，不擅自修正；在句末加
  `<!-- trans: 原文矛盾：… -->`，并在完成汇报中列出文件与行号。
- 只做翻译，不做“顺带改进”。任何技术性改动都必须回到英文原文的独立评审。
