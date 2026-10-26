# 骨架补写清单

`regeneration-readiness.md` 第 8 节第 1 步：把"值底座没有形状"与"引擎输出类型没有形状"合成一张按类型逐条的清单。

先说一句方法上的更正：`regeneration-readiness.md` §1 的机械检查只统计**代码块里声明过**的类型，所以"没有形状"其实有两种——一种是有一张成员表（`05` §3 那样），只是不是可抄的声明块；另一种是全篇没有。下表分开处理，否则会把已经写好的东西再写一遍。

## 1. 已经有成员表、只差声明块的（10 个）

`05-space.md` §3 的两张表已经把分量和成员写全了（`B-7` 之后）：

| 类型 | 行 |
| --- | --- |
| `LatticePoint`、`LatticeKey`、`Cuboid`、`CuboidSet` | `05` §3 第 47–50 行 |
| `Heading`、`Turn`、`Tilt`、`Flip` | `05` §3 第 51–54 行 |
| `Orientation`、`Placement`、`Siting` | `05` §3 第 55–57 行 |
| `Locus`、`LocusFrame`、`LocusMap` | `05` §3 第 37–39 行（词表）与第 58–59 行（成员） |
| `Bindings`、`BindingKey` | `07` §3 第 86 行 |
| `ChangeSet` | `06` §4.8（六个分量，MQ-11 待定，见 §5） |

这些**不需要再补形状**。如果重生成要"能直接抄的声明块"，做法是把 `05` §3 的表按行展开成 `record`/`class` 声明，不引入新内容。真要做，放在 `05` 的 §3 之后，不要挪进别的章。

## 2. 完全没有形状、代码里有（17 个）

以下类型在 `docs/spec/*.md` 里没有成员表、也没有声明块，代码里却有完整定义。形状照抄即可，写进"由谁拥有"那一章。

```java
// 05 — 值（World 是 R35 的裁决，代码还没有这个类型）
public record World(NamespacedId id) {}
public record OwnerId(NamespacedId owner) implements Comparable<OwnerId> {   // R35: no world
    public static OwnerId of(NamespacedId owner);
    @Override public int compareTo(OwnerId other);
}
public record InstanceId(NamespacedId world, long serial) implements Comparable<InstanceId> {
    public static InstanceId of(NamespacedId world, long serial);
    @Override public int compareTo(InstanceId other);
}
public record SectionKey(NamespacedId dimension, int x, int y, int z)
        implements Comparable<SectionKey> {
    public static SectionKey of(NamespacedId dimension, LatticePoint cell, int cellSize);
    public SectionKey sectionOf(LatticePoint cell, int cellSize);
    @Override public int compareTo(SectionKey other);      // dimension, then x, y, z
}
public record Ticket(long serial) implements Comparable<Ticket> {}

// 11 — 引擎
public record Budget(int cells, int sections) {}
public record Spend(int cells, int sections) {}
public final class PinBudget {                             // retained bytes, never cache bytes
    public static PinBudget of(long limit);  public static PinBudget unbounded();
    public long limit();  public long retained();  public long remaining();
    public boolean tryCharge(long bytes);                  // all or nothing
}
public record MatchOutcome(Stage stage, Siting siting, WorldView view,
                           Map<ContextKey<?>, Object> context, Bindings bindings, ReadScope reads) {
    public static MatchOutcome empty(Stage stage, Siting siting, WorldView view);
    public <T> @Nullable T value(ContextKey<T> key);
}
public record BayBinding(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings) {}
public final class InstanceRecord {                        // one per placed instance
    public static InstanceRecord of(InstanceId id);
    public InstanceId id();  public long generation();
    public MatchResult.@Nullable Formed previous();
    public long nextGeneration();
    public void remember(MatchResult.Formed formed);
    public MatchResult.@Nullable Formed reemit(FindingBundle findings);
}
public enum DropCause { SNAPSHOT_CHANGED, CAPTURE_INVALID }

// 07 / 12 — 判定与上下文
public record CheckResult(Reply reply, List<OccupantRef> subjects, FindingBundle notes) {}
public record DoorView(OccupantRef first, OccupantRef second, BayView bay) {}
public interface CatalogsDraft { /* the registration funnel: put/register a ContextKey */ }
public enum StatePhase { SECTION, AGGREGATE }
```

写的时候注意两处顺序：`DoorView` 的两半是"前半 → 后半"（`06` §3.2）；`SectionKey.compareTo` 先比 `dimension` 再比坐标，因为不同世界同坐标是两个区段。

## 3. 只有名字、没有载体的（14 个）

文档用了、代码里 `find src -name` 为空。它们不是"代码滞后"：要么补设计、要么删引用，判决在 `Q18`/`Q19`。

`InstanceRef`、`CaptureIssues`、`IssueSink`、`DiscardReason`、`DirtyCause`、`CancelReason`、`OverlayModel`、`OverlayPayload`、`FactoryContext`、`Arguments`、`UnitId`、`SeamBinding`、`MatchCompletion`、`InvalidationIndex`。

三个被别的待定连带：`Arguments` 跟 `Q2`（`DerivationSpec` 的分量）、`MatchCompletion` 跟 `Q5`（`Formed` 重发射）、`IssueSink` 跟 `Q11`（捕获 SPI 的唯一签名）。

## 4. 仍被待定直接挡住的形状

| 类型 | 待定 |
| --- | --- |
| `UndecidedReason` | `Q9`（八个还是七个值） |
| `ChangeSet` 的分量集 | `MQ-11`（变更提示 vs 逐格标记）；`06` §4.8 已按代码写，等裁决定一种 |
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

1. §2 的 17 个按注释里的章号写进去；`World` 同时进 `05` 与 `01` 的行。
2. §1 的十个只在重生成需要"可抄的声明块"时展开，不重复成员。
3. §4 的等裁决；§3 的等 `Q18`/`Q19`。
4. 然后重跑 `P1`/`P2`（只读文档 → 写骨架 → 编译）。能编译才算文档就绪；在那之前，"从文档重生成"仍会在第一行即兴发挥。
