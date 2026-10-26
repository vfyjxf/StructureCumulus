# 从文档重生成代码：就绪度检查

目的：验证"只给 `docs/spec/`，能否写出完整且可编译的内核"。方法与结论分开写，便于复查。

## 1. 方法

两步。

**机械闭合检查**（脚本，可重跑）：抽取 `docs/spec/*.md` 全部 Java 代码块，统计三类名字：
1. 在代码块中声明过（`record`/`class`/`interface`/`enum`）；
2. 在代码块中以类型位置出现（`new`/`extends`/`implements` 之后，或名字后跟 `<`/`(`/`[]`/`.class`）；
3. 在 `01-glossary` 中有行（散文描述）。

然后用 1、3 判定 2 里的每个名字：有声明（可抄）、只有词表（需要补形状）、都没有（无处可查）。

**盲写骨架探针**：只给一个单元的文档，写出该单元声明或拥有的类型的骨架，然后才去读对应代码，逐条列出"文档沉默、我必须自己决定"的地方。探针测的是形状之外的东西：成员是否齐全、行为是否可推断。

## 2. 机械结论

| 指标 | 数值 |
| --- | --- |
| 代码块里的类型声明 | 260 |
| 词表里的类型名 | 470 |
| 代码块中以类型位置出现的名字 | 479 |
| **有词表行、但没有代码形状** | **40** |
| **既无词表行、也无声明** | **32** |

## 3. 有词表行、没有代码形状（40）

其中 32 个是内核类型，每一个都被多个单元引用。生成器遇到它们只能自创形状：

```
Cuboid  LatticePoint  Siting  OwnerId  CuboidSet  Locus  InstanceId  LatticeKey
MatchOutcome  PinBudget  World  CheckResult  DropCause  UndecidedReason  Bindings
ChangeSet  Heading  InstanceRef  OverlayModel  SectionKey  StatePhase  Ticket
UnitId  Arguments  Budget  Spend  DoorView  FactoryContext  Flip  Orientation
OverlayPayload  Turn
```

另外 8 个属于 JDK 或 Minecraft API，不需要文档给形状：`BlockPos`、`BlockState`、`Direction`、`Mirror`、`Rotation`、`ServerLevel`、`SequencedSet`、`Throwable`（后两个其实在 JDK 里，只是被脚本算错）。

这一组的性质：它们的语义在 `01` 有散文定义（"一个格子"、"位置与朝向"），但**分量、构造方式、相等性、比较顺序都没有代码形态**。`Cuboid`/`LatticePoint`/`CuboidSet`/`Siting` 尤其关键，`05` 里只有数学描述，没有一行可抄的声明。

## 4. 既无词表行、也无声明（32，去掉 JDK/示例类型后）

内核相关、必须补的：

```
RetryToken  CacheMode  InstanceRecord  IssueSink  OccupantFilter  OccupantView
SchedulerOptions  DiscardReason  DirtyCause  CancelReason  CaptureIssues
CatalogsDraft  DeriveContext  Computations
```

`RetryToken` 被 `08`/`11`/`15`/`22` 四个单元用在签名里，词表里没有它；`CacheMode` 是 `MatchPolicy` 的分量；`InstanceRecord` 出现在 `11`/`22` 的签名里；其余是宿主侧或上下文侧的成员类型。

不需要补的：`Class`/`Locale`/`Throwable`/`CompletableFuture`/`SequencedMap`（JDK），`BlockPos`/`BlockState`/`CompoundTag`/`EntityNbt`/`HolderLookup`/`ProfilerFiller`/`Provider`/`ResourceLocation`/`ResourceManager`/`SimplePreparableReloadListener`/`RegisterCommandsEvent`/`RenderLevelStageEvent`/`BlockableEventLoop`（Minecraft），`Tank`/`WoodPanel`/`Tooling`/`Capture`/`Computations`（示例中的示意名，需确认真伪）。

## 5. 结论

文档目前是**设计说明 + 分散的代码草图**，不是可生成源。缺口集中在两处：

1. **值底座没有形状**。`Cuboid`、`LatticePoint`、`CuboidSet`、`Siting`、`LatticeKey`、`Locus`、`World`、`OwnerId`、`InstanceId`、`SnapshotId` 这十个类型被所有单元引用，却只有散文。它们必须先写成可编译的声明，否则任何生成都会从第一行开始即兴发挥。
2. **成员表不完整**。有形状的类型里，多数只给了"关键成员"，没有给全部公开成员；`ContextDraft` 的动词集是已知例子（缺 `forSection`/`refuse`/`declined`/`read`/`publishes`/`keys`）。

这说明"修正文档再用文档重生成"这条路线需要多加一个中间产物：**一份与文档同步的骨架源**（类型、成员、签名、常量表，不含实现），先由文档生成它、再让它编译。否则文档的散文与代码块之间会一直缺一层。

## 6. 盲写探针结果

两个探针，协议相同：只读指定文档 → 写骨架 → 再读代码 → 列出"文档沉默处"。产物在
`docs/spec/_work/regen/`。

**P1（`01`/`05`/`06` → `base`/`space`/`occupant`）**：骨架 54 个单元，`javac` 通过。这证明骨架层这条路可行。八个阻塞点集中在占据物：

- `OccupantSnapshot`、`OccupantDescriptor`、`OccupantKind` 三处，文档的模型比代码更有表达力（`Trait<T>` 能表达"在场但无值"，`OTHER` 能带宿主 id），代码是简化版。**两侧都没定好，进待定。**
- `WorldView` 缺 `readStatus`、多出 `slot`/`contentDigest`；`SectionSnapshot`/`SectionSlot`/`SectionKey` 三份文档都没提。**代码对，写回文档（已在本轮 CODE 批次）。**
- `OwnerId`、`SnapshotId`/`World` 与代码相反，属 R35 滞后，**不回写。**
- 文档声明了约 25 个 U0/U2 类型代码里没有（`TraitKey`、`Traits`、`SnapshotPolicy`、`WorldCapture`、`CaptureResult`、`OccupantResolver`、`LocusMap`、`Budget`、`DataView` 等）。这是目标态，属"文档对、代码待补"。

**P2（`08`/`11`/`20`/`23` → `claim`/`engine`）**：八条阻塞中三条是已登记滞后（R32/R36、`UndecidedReason` 属待定），两条是文档缺口而代码有答案，三条两侧都没定好。新暴露的一类是：

- **引擎输出类型没有成员表**。`11` §1 只给了 `BayBinding.subjects()` 与 `PrescreenIndex.of(...)` 两个签名；代码里 `MatchOutcome` 六个分量、`BayBinding` 三个，文档一个都没列。`SeamBinding`/`Census`/`PrescreenIndex`/`AnchorCandidate`/`Fingerprint`/`ReadSet`/`InvalidationIndex`/`InstanceRef` 八个名字有、形状无。
- **`ClaimMatrix` 的公开面是文档的三倍**：八个未列成员，外加 `OVERLAP_CODE`/`CONFLICT_CODE` 两个常量；它对同蓝图重叠发 `SC-CLM-0001`，而 `08` §4.2 要求 `SC-BPT-0002`。
- **`ClaimPlan.Overlap(low, high)` 与代码的 `Overlap(challenger, incumbent, cells)` 是两个不同的东西**。
- **`MatchSession` 的五个成员只写在注释里**，可见性还与文档相反（文档说包私有，代码是公开类）。

## 7. 由此得到的三类清单

| 类 | 含义 | 处理 |
| --- | --- | --- |
| 文档对、代码待补 | R 记录覆盖的滞后，或文档声明的目标态类型 | 不写回；进迁移清单 |
| 代码对、写回文档 | 代码有更具体且不与 R 记录冲突的事实 | 本轮 CODE 批次（33 条） |
| 两侧都没定好 | 文档与代码各有更强处，或都没有 | 进 `open-questions.md` 的待定 |

新增的待定项（P1/P2 发现，原先未列）：

- 占据物的特质模型：`Trait<T>` 带值 vs `Set<NamespacedId>`。
- `OccupantDescriptor` 的形状：`properties`/`tags` 映射 vs `traits` 集合。
- `OccupantKind` 是否有第五个 `OTHER`（携带宿主 id）。
- `UndecidedReason` 的取值集（已在 Q9）。
- 同蓝图重叠的诊断码（已在 Q6）。
- `ClaimMatrix` 的公开成员表要不要写全。
- `MatchSession` 的成员与可见性。

## 8. 下一步

1. 三组 CODE 回填完成后，把 §2 的"值底座没有形状"与 §6 的"引擎输出类型没有形状"合成一份**骨架补写清单**，按类型逐个写形状。
2. 骨架补齐后重跑 P1/P2 的协议（只读文档 → 写骨架 → 编译），能编译即视为文档就绪。
3. 待定项按 `open-questions.md` 裁决后再落文档。
