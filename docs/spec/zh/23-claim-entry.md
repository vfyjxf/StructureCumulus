# 23. 声明入口：check 只读，settle 写入

> 决策记录，2026-10-23。状态：**已定**；它触及的每个单元文件都已更新，代码迁移清单在 §9。
> 它取代 `MatchRequest.stage`（`22` §9.1）与 `SettlementIntent` 词汇（`20` §5.3）；阶段不再由
> 作者声明，也不再由调用方选择（§7）。

## 1. 问题

一个结构检查同时做两件性质不同的事：

1. 读方块，回答“这堆方块是否构成这个结构”——**只读**；
2. 在账本里写“这块地归某台实例”——**有副作用**。

代码把两件事塞进一次调用，靠请求里的 `stage=SETTLE` 触发第二件
（`StructureKernel.check` → `MatchSession.admit` → `MatchSettler.settle`）。由此有三个后果：

| 后果 | 机制 | 玩家看到什么 |
| --- | --- | --- |
| 冲突发现得太晚 | 内核从不调用 `ClaimLedger.read`，只有 `settle` 读一次版本号 | 整台机器建完、材料花掉，最后一步才被告知这块地有主 |
| 声明无法撤销 | `ClaimLedger` 只有 `revision/settle/read` | 拆掉、卸载、搬家后旧行永久保留，挡住后来的人和自己 |
| 无法表达“只看不声明” | 唯一的判据是 `stage`，而它同时决定跑哪些检查 | 自动探测与真正声明共用一条路径，只能靠调用方自律 |

第二行是最严重的：**一个只能写、不能删的账本，会把错误永久固化。**

## 2. 决策

拆成两个入口，写入只发生在第二个：

```java
// read-only: reads blocks and the ledger; writes nothing
MatchResult check(MatchRequest request);

// writing: the only path that may change the ledger; closes the admitted result on every path
MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);
```

`check` 返回 `MatchResult.Admitted`——判定、**计划**与**它读到的账本修订号**
（`Admitted.planned()`、`Admitted.ledgerRevision()`）。计划是这次 check 发现的内容，而不是将要
写入的内容：它按舱位携带绑定与那个修订号，而 `settle` 用 `ClaimPlanner` 从请求派生出每一行
（`08` §4.5，I-16），因此调用方无法把它自己造的行交给它。这两样是 `settle` 的输入，也是
“建造前就能告诉玩家有没有冲突”的原因。计划类型是 `ClaimPlan`；它不是单条检查的应答，
后者是 `CheckResult`。

- `check` 允许被任意频繁调用：自动探测、预览、命令、编辑器叠加层都走它。
- `settle` 只由明确要求声明的调用方触发，且只在该结构确实成立之后。
- “能不能写”不再是请求里的一个字段，而是**选了哪个方法**。

## 3. 时序

### 3.1 正常路径

```
(1) check(request)                           caller thread
    read blocks -> compare cell by cell -> read the ledger over the box
    conflict -> Failed(SC-CLM-0002), nothing written
    matched  -> ClaimPlan.Matched(ledgerRevision = 41, bindings) attached to the result

(2) the caller decides: preview only (stop here), or register

(3) settle(request, admitted)                server thread
    3a is the capture still current?   CaptureGate.evaluate(request.lease()) -> else Dropped, no write
    3b do the bindings hold?           bay of this blueprint, subjects of this capture,
                                       kinds matching, cells inside the bay
                                       -> else Failed(SC-CLM-0005), no write
    3c derive the rows                 ClaimPlanner.derive(world, blueprint, siting, owner, bindings)
    3d is the revision still 41?       else re-read once (§3.2)
    3e ledger.settle(SettleRequest(world, owner, siting, rows, 41))
         conflict      -> Failed(SC-CLM-0002)
         revision moved-> retry once (§3.2)
         settled       -> Formed(rows, revision = 42)
```

### 3.2 版本移动

`settle` 带的是 `check` 时读到的版本。若账本在两者之间被别人写过：

1. 重读一次同一范围（`ledger.read(box)`），重新跑冲突矩阵；
2. 仍冲突 → `Failed(SC-CLM-0002)`，并且诊断项以**这次**读到的在册行为准；
3. 不冲突 → 以新版本重试一次；
4. 第二次仍移动 → `Undecided(LEDGER_MOVED)` + `RetryToken`，由调用方决定是否重来。

关键规则：**重试必须重读，不能只替换期望版本号。** 今天的 `MatchSettler` 只替换版本号
（`MatchSettler.java:80-82`），安全性只因为 `InMemoryClaimLedger` 在自己的 CAS 循环里重扫了一遍
冲突；换成信任请求行的存储型适配器，就会写下一组过期但冲突的行。

### 3.3 为什么要版本号而不是预留

`check` 读到版本 41、`settle` 带着 41 去写——两个人的 41 只有一个人能写成功，另一个拿到版本
移动并重新判断。**不需要往账本里塞临时行**就能解决“我看好了、别人先声明了”。

## 4. 结果类型的变化

| 类型 | 今天 | 23 之后 |
| --- | --- | --- |
| `MatchResult.Admitted` | 探针成功，无实例 | `check` 成功：判定 + `@Nullable ClaimPlan planned` + `ledgerRevision`；永不写账本 |
| `MatchResult.Formed` | 结算时产生 | **只由 `settle` 产生**；携带 `ClaimSet` 与写入后的版本 |
| `MatchResult.Dropped` | 快照变了 | 不变；`settle` 也会返回它（§3.1 3a） |
| `MatchResult.Failed` | 有 ERROR | 不变；冲突码仍是 `SC-CLM-0002` |

`Admitted.planned()` 可以为 `null`：没有账本、或蓝图没有可声明的舱位时，`check` 仍然可用，
但该结果不能交给 `settle`（`Undecided(LEDGER_UNAVAILABLE)`）。

## 5. 账本要补的能力

```java
public interface ClaimLedger {
    long revision();
    ClaimVerdict settle(SettleRequest request);          // exists
    ClaimView read(Cuboid box);                          // exists; the kernel starts using it
    ClaimVerdict release(OwnerId owner, Siting siting);  // new: delete every row of that key
}
```

`release` 的语义：删除 `(world, owner, siting)` 的全部行，并把版本推进一格；没有该键时是无操作
（不报错、不推进版本）。它只能由服务端线程调用。

三个生命周期点必须调用它：

| 时机 | 谁调 | 后果（若不调） |
| --- | --- | --- |
| 实例被移除 | `dropInstance` | 旧实例永久占位 |
| 世界卸载 | `LevelEvent.Unload`（Minecraft 侧的名字） | 该世界的所有声明永久残留 |
| 实例搬家 | 搬家处理函数，在新安放位 `settle` 之前 | 旧行挡住自己的新位置 |

## 6. 冲突判定的分层

| 层 | 何时 | 依据 | 失败 |
| --- | --- | --- | --- |
| 蓝图内（静态） | 构建蓝图 | 舱位几何与声明集 | `SC-BAY-*` / `SC-BPT-0002`（构建期抛出） |
| 蓝图外（咨询性） | `check` | 账本的在册行 | `Failed(SC-CLM-0002)`，**只是预测** |
| 蓝图外（权威） | `settle` | 在册行 + 版本 | `Failed(SC-CLM-0002)`，权威 |

`check` 的冲突是预测：它读到的行可能马上变。因此诊断项要说“当前有主”，不能写“结算被拒”。
`settle` 的冲突才是结论。

## 7. 前提：阶段不再由任何人声明

本决策与“阶段由引擎拥有”一起成立，二者互相依赖：

| 项 | 23 之后 |
| --- | --- |
| `Check` | `(id, bay, focus, expectation, description)`，没有 `stage` |
| 文档 JSON | 没有 `"stage"` 字段 |
| 执行顺序 | 依赖（读了谁产出的键）→ 读取闭包体积升序 → 声明顺序 |
| `Stage.STALE` | 删除；定向重检 = `check` + 请求里的 `ChangeSet` |
| `Stage` 在结果里 | 保留，由引擎填（蓝图校验 / 世界检查 / 结算），表示“这条来自哪一步” |
| 结算的触发 | `settle` 这个入口，不再是 `stage=SETTLE` |

因此 `settle` 不需要知道“阶段”，只需要知道“这次允许写”。

## 8. 未决

| 问题 | 现状 | 影响 |
| --- | --- | --- |
| `EXCLUSIVE` 配 `None` 足迹 | 代码接受，产生零行 | 已由 `24` 的 R1 回答：构建期拒绝 |
| `Bound` 舱位没有绑定时 | 静默产生零行 | 实例成形却没有任何占用 |
| 实体栏位重叠 | `ANY_SUBJECT`/`None` 零行，两栏永不冲突 | 两个生物陷阱可以叠在一起 |
| `SC-CLM-0003`（`SHARED` 方要得太多） | 定义存在，代码只发 0002 | 宿主无法区分“我越界”和“你越界” |
| `SC-CLM-0104`（边界残留） | 定义存在，无产生点 | 结构缩小后旧格子不再被检查 |
| 搬家 | 旧键不会被自动释放 | 依赖宿主按 §5 调 `release` |

## 9. 迁移清单

代码（按顺序）：

1. `ClaimLedger`/`InMemoryClaimLedger`：加 `release`；补一个“被释放的键读到空、版本已推进”的测试。
2. `MatchSession.admit`：只构建 `ClaimPlan.Matched`——绑定与修订号，绝不是行——且**不再调用 settler**；把结算路径整体移出。
3. `MatchResult.Admitted`：加 `@Nullable ClaimPlan planned` 与 `long ledgerRevision`。
4. `StructureKernel.check`：为它的预测用 `ClaimPlanner` 派生出行，并在这些行上读账本；`settle` 在检查绑定之后再次派生它们（`SC-CLM-0005`），然后做 CAS（`19` R36）。
5. `MatchSettler`：拆成“校验 + 写入”，`RevisionMoved` 分支改为重读后重判；账本相关情形使用
   `SC-CLM-0004`，绝不用 `SC-ENG-0003`/`SC-ENG-0004`，后两者的含义已冻结在 `13`。
6. `MatchRequest`/`Stage`：随 §7 一起改（阶段移除、`ChangeSet` 提示）。
7. `SnapshotInfo`：删除。`SnapshotLease` 获得 `changeCount()` 与 `isValid()`；`Ticket` 离开捕获，留在 `MatchRequest` 上供调度器使用；`check` 与 `settle` 失去它们的供给器；`settle` 接收 `Admitted` 并在每条路径上关闭它（`19` R37）。
8. 测试：现有 SETTLE 用例改走 `settle`；新增“check 不写账本”“版本移动后重读”“释放后可再声明”。

文档：`08` §4.5/§4.6（一次调用 → 两次）、`11` §4.1/§5、`15` §3（入口参考代码，以及 `00` 的
R-14 与 `01` 同样计数的九个操作）、`16` §4.9/§4.10、`18` 的反例、`20` §5.3（`SettlementIntent`
删除）、`22` §9.1/§10、`02`、`04`、`17` §5–§7、`19`（处置记录），以及本清单触及的每个文件的
`zh/**` 镜像。
