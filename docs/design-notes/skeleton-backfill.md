# 骨架补写清单

`regeneration-readiness.md` 第 8 节第 1 步：按类型逐条给出"形状够不够重生成用"。

## 0. 先更正一处统计口径

`regeneration-readiness.md` §2 的机械检查只统计**代码块里声明过**的类型，于是把一批"在 `01` 有一行、行里写着分量"的类型也算成"没有形状"。`01` 的行是"术语在前、类型在后"（`| World | `World` | `record World(NamespacedId id)` … |`），一个只按行首类型名匹配的检查会全部漏掉。

按行内容重查之后，真正的情况是：

| 情况 | 数量 | 例子 |
| --- | --- | --- |
| 有成员表或在 `01` 行里给了分量 | 多数 | `Cuboid`、`Siting`、`Locus`、`MatchOutcome`、`BayBinding`、`CheckResult`、`DropCause`、`StatePhase`、`Ticket`、`UndecidedReason` |
| 有一行、但行里只给散文（没有成员表） | 4 | `Budget`、`PinBudget`、`DoorView`、`SectionKey`（`05` 的表里没有它） |
| 连一行都没有 | 2 | `InstanceRecord`、`CatalogsDraft` |
| 只有名字、任何地方都没有载体 | 14 | 见 §3 |

所以"值底座没有形状"这个结论要缩小：真正缺的只有下面这几处，其余都能直接照 `01`/`05` 的行抄出来。

## 1. 需要补成员表的（4 个）

**已落地**：`SectionKey` → `05` §3 的行；`DoorView` → `07` §3 的行；`Budget`/`Spend`/`PinBudget` → `11` §3 的 supporting-values 块。

```java
// 05 — SectionKey 在 01 有一行，但 05 §3 的表里没有；代码在 space
public record SectionKey(NamespacedId dimension, int x, int y, int z)
        implements Comparable<SectionKey> {
    public static SectionKey of(NamespacedId dimension, LatticePoint cell, int cellSize);
    public SectionKey sectionOf(LatticePoint cell, int cellSize);
    @Override public int compareTo(SectionKey other);      // dimension, then x, y, z
}

// 11 — 01 的行是散文，这两处要成员表
public final class PinBudget {                             // 保留字节，与缓存字节分开计
    public static PinBudget of(long limit);  public static PinBudget unbounded();
    public long limit();  public long retained();  public long remaining();
    public boolean tryCharge(long bytes);                  // 全有或全无
}
public record Budget(int cells, int sections) {}           // 01 只给了 of(cells, sections)

// 07 — 01 没有 DoorView 的行，07 §4.6 只说"两个 OccupantRef、一个合并状态"
public record DoorView(OccupantRef first, OccupantRef second, BayView bay) {}
```

`SectionKey` 的分量写法与 `01` 的行不一致：行里写的是 `(World world, int x, int y, int z)`，代码是 `NamespacedId dimension`。这是 `R35`（内核词汇是 `world`）的滞后，写进 `05` 时保留 `World`，并在迁移清单里记一笔。

## 2. 连行都没有的（2 个）

**已落地**：`InstanceRecord` → `11` §3；`CatalogsDraft` → `12` §3（归属 `context`，与 `15` §3 的 `ContextKeys.register(CatalogsDraft, …)` 配套）。

```java
// 11 — 每个已放置实例一份
public final class InstanceRecord {
    public static InstanceRecord of(InstanceId id);
    public InstanceId id();  public long generation();
    public MatchResult.@Nullable Formed previous();
    public long nextGeneration();
    public void remember(MatchResult.Formed formed);
    public MatchResult.@Nullable Formed reemit(FindingBundle findings);
}

// 11 / 15 — 注册漏斗（Catalogs.Builder 实现它）
public interface CatalogsDraft { /* register/put 一个 ContextKey */ }
```

`InstanceRecord` 的六个成员与"谁关闭 `Admitted`/`Formed`"是同一段语义（`16` §4.10），写的时候两处一起看。

## 3. 只有名字、没有载体的（14 个）

文档用了、`find src -name` 为空。判决在 `Q18`/`Q19`：

`InstanceRef`、`CaptureIssues`、`IssueSink`、`DiscardReason`、`DirtyCause`、`CancelReason`、`OverlayModel`、`OverlayPayload`、`FactoryContext`、`Arguments`、`UnitId`、`SeamBinding`、`MatchCompletion`、`InvalidationIndex`。

三个被别的待定连带：`Arguments` 跟 `Q2`（`DerivationSpec` 的分量）、`MatchCompletion` 跟 `Q5`（`Formed` 重发射）、`IssueSink` 跟 `Q11`（捕获 SPI 的唯一签名）。

## 4. 仍被待定直接挡住的形状

| 类型 | 待定 |
| --- | --- |
| `UndecidedReason` | `Q9`（行里列了八个值，代码七个） |
| `ChangeSet` 的形态 | `MQ-11`（一种还是两种） |
| `MatchSession` 的成员与可见性 | `Q24` |
| `Reply` 的形态、`Bindings.merge` 的阶段 | `Q8` |
| `ClaimMatrix` 的公开面、`Overlap` 的分量、`Overlap` 的码 | `Q23`、`Q6` |
| `OccupantDescriptor`、`Trait`/`TraitKey` | `Q20`、`Q21` |
| `OccupantKind.OTHER` 与比较序 | `Q22` |
| `ReadScope`/`ReadSet` 是否一个类型 | `Q4` |
| `CaptureResult`/`SnapshotPolicy`/`OccupantResolver` | `Q11`、`C-15` |
| `SnapshotId` 落在哪个包 | `Q10` |
| `InstanceId` 落在哪个包 | `MQ-4` |
| `ClaimView` 的 `@Nullable` 与 `unavailable()` | 属 U10，`Q18` |

## 5. 落地顺序

1. §1 的四处写进 `05`/`11`/`07`；§2 的两处写进 `11`。
2. §4 的等裁决；§3 的等 `Q18`/`Q19`。
3. 然后重跑 `P1`/`P2`（只读文档 → 写骨架 → 编译）。这一次的判据比上一轮严：上一轮 P1 的八个阻塞点已经有一半被这一批文档改动消掉，重跑应当只剩 §1/§2 与 §4 的待定项。
