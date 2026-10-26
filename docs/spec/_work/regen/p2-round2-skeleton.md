# P2 round 2 — regenerated skeleton (docs only)

Written from `08-claims.md`, `11-engine.md`, `20-execution-and-sections.md`, `23-claim-entry.md`
only, before any look at `src/kernel/java/dev/vfyjxf/cumulus/kernel/{claim,engine}`.

- Scratch tree: `/tmp/cumulus-probe-p2/src` (deleted after the probe).
- Packages: `dev.vfyjxf.cumulus.kernel.claim`, `dev.vfyjxf.cumulus.kernel.engine`, plus one
  `dev.vfyjxf.cumulus.stub` file and the `org.jspecify.annotations` stub.
- `// INVENTED:` marks a type or member the documents never name, or name without a shape.
- `// STUB:` marks a type the four chapters never describe at all. They all live in one file,
  `dev/vfyjxf/cumulus/stub/Stub.java`, and are pulled in with
  `import static dev.vfyjxf.cumulus.stub.Stub.*;` so the skeleton keeps the documents' simple
  names instead of forty files of scaffolding.
- Bodies are `throw new UnsupportedOperationException()` / `return null` only. The one exception is
  where the chapter prints a compact constructor body (`ClaimSet`, `ClaimView`, `Settled`,
  `ClaimConflict`); those lines are quoted from the document unchanged.

Probe plumbing, not design: `Stub` is a single `interface` holding every given type as a public
nested member (`Stub.World`, `Stub.Siting`, …). Real packages for those types are never stated by
the four chapters, so any placement is a guess; putting them in one file at least makes the guess
visible and cheap to move.

## `dev/vfyjxf/cumulus/kernel/claim/ClaimLedger.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;

public interface ClaimLedger {

    World world();

    long revision();

    ClaimVerdict settle(SettleRequest request);

    ClaimVerdict release(OwnerId owner, Siting siting);

    ClaimView read(Cuboid box);
}
```

## `dev/vfyjxf/cumulus/kernel/claim/ClaimMatrix.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import java.util.List;

public final class ClaimMatrix {

    private ClaimMatrix() {}

    public static boolean conflicts(ClaimRow a, ClaimRow b) { throw new UnsupportedOperationException(); }

    // INVENTED: 11 §4.1 calls `ClaimMatrix.conflicts(d.rows(), held.rows())`; 08 §3 declares the
    // pair form only, so the list form (and its meaning: any conflicting pair) exists in no document.
    public static boolean conflicts(List<ClaimRow> a, List<ClaimRow> b) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/claim/ClaimPlan.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import dev.vfyjxf.cumulus.kernel.engine.BayBinding;
import java.util.List;

public sealed interface ClaimPlan permits ClaimPlan.Matched, ClaimPlan.Invalid {

    record Matched(long ledgerRevision, List<BayBinding> bindings) implements ClaimPlan {}

    record Invalid(FindingBundle findings) implements ClaimPlan {}

    // INVENTED: 08 §3 nests `Overlap` in `ClaimPlan` but leaves it out of the `permits` list, so it
    // is a nested type that is not a `ClaimPlan`; `Comparable<Overlap>` has no documented order.
    record Overlap(NamespacedId low, NamespacedId high) implements Comparable<Overlap> {
        @Override public int compareTo(Overlap other) { throw new UnsupportedOperationException(); }
    }
}
```

## `dev/vfyjxf/cumulus/kernel/claim/ClaimPlanner.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import dev.vfyjxf.cumulus.kernel.engine.BayBinding;
import java.util.Collection;
import java.util.List;

public final class ClaimPlanner {

    private ClaimPlanner() {}

    public static Derived derive(World world, Blueprint blueprint, Siting siting, OwnerId owner,
                                 Collection<BayBinding> bindings) {
        throw new UnsupportedOperationException();
    }

    public record Derived(List<ClaimRow> rows, List<ClaimPlan.Overlap> overlaps,
                          CuboidSet staticFootprint) {}
}
```

## `dev/vfyjxf/cumulus/kernel/claim/ClaimRow.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.List;

public record ClaimRow(World world, LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting,
                       NamespacedId bay) {

    public static List<ClaimRow> ordered(List<ClaimRow> rows) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/claim/ClaimSet.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import java.util.List;

public record ClaimSet(List<ClaimRow> rows, long revision) {

    public ClaimSet {
        rows = ClaimRow.ordered(rows);
    }
}
```

## `dev/vfyjxf/cumulus/kernel/claim/ClaimVerdict.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.List;

public sealed interface ClaimVerdict
        permits ClaimVerdict.Settled, ClaimVerdict.ClaimConflict, ClaimVerdict.RevisionMoved {

    record Settled(long revision, List<ClaimRow> rows) implements ClaimVerdict {
        public Settled {
            rows = ClaimRow.ordered(rows);
        }
    }

    record ClaimConflict(List<ClaimRow> rows, FindingBundle findings) implements ClaimVerdict {
        public ClaimConflict {
            rows = ClaimRow.ordered(rows);
        }
    }

    record RevisionMoved(long expected, long actual) implements ClaimVerdict {}
}
```

## `dev/vfyjxf/cumulus/kernel/claim/ClaimView.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.List;

public record ClaimView(List<ClaimRow> rows, long revision, boolean available) {

    public ClaimView {
        rows = ClaimRow.ordered(rows);
        if (!available && !rows.isEmpty()) throw new IllegalArgumentException("an unavailable view holds no rows");
    }

    public static ClaimView unavailable() { return new ClaimView(List.of(), 0L, false); }

    public List<ClaimRow> rowsIn(Cuboid box) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/claim/Footprint.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;

public sealed interface Footprint
        permits Footprint.Box, Footprint.Bound, Footprint.Declared, Footprint.None {

    record Box() implements Footprint {}

    record Bound() implements Footprint {}

    record Declared(CuboidSet cells) implements Footprint {}

    record None() implements Footprint {}
}
```

## `dev/vfyjxf/cumulus/kernel/claim/SettleRequest.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.List;

public record SettleRequest(World world, OwnerId owner, Siting siting, List<ClaimRow> rows,
                            long expectedRevision) {}
```

## `dev/vfyjxf/cumulus/kernel/claim/Sharing.java`

```java
package dev.vfyjxf.cumulus.kernel.claim;

public enum Sharing {
    EXCLUSIVE, SHARED;

    public boolean blocks() { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/AnchorCandidate.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

// INVENTED: 11 §1/§2 own the name, §4.2 then says "no candidate is scored and no orientation is
// searched" — the type is named and emptied.
public interface AnchorCandidate {}
```

## `dev/vfyjxf/cumulus/kernel/engine/AnchorResolver.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

public interface AnchorResolver {

    Outcome<Siting> resolve(Blueprint blueprint, LatticePoint origin, WorldView view);
}
```

## `dev/vfyjxf/cumulus/kernel/engine/AnchorSpec.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

public record AnchorSpec(NamespacedId role, NamespacedId property, String value) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/BayBinding.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.List;

// INVENTED: 08 §4.3 names `subjects()`, 08 §4.5 names "its kind matches the bay's expectation",
// 11 §4.5 says one binding per bay that bound a subject. Nothing else is described.
public record BayBinding(NamespacedId bay, NamespacedId kind, List<OccupantRef> subjects) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/Budget.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public record Budget(int cells, int sections) {

    public static final Budget ZERO = null;

    public static Budget of(int cells, int sections) { throw new UnsupportedOperationException(); }

    public static Budget unbounded() { throw new UnsupportedOperationException(); }

    public boolean admits(Spend spend) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/CacheKey.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

public record CacheKey(PlanId plan, Siting siting, UnitId unit, long closureDigest) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/CacheMode.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public enum CacheMode { OFF, PARTIALS, OUTCOMES }
```

## `dev/vfyjxf/cumulus/kernel/engine/CaptureGate.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

// INVENTED: 11 §4.1/§4.6 and 20 §5.4 call `CaptureGate.evaluate(lease)` and switch on the bare
// constants CURRENT / CHANGED / SUPERSEDED, but no chapter declares the type, the verdict's name,
// or whether it is an enum.
public final class CaptureGate {

    private CaptureGate() {}

    public enum Verdict { CURRENT, CHANGED, SUPERSEDED }

    public static Verdict evaluate(SnapshotLease lease) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/ChangeSet.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.Set;

// INVENTED: 11 §5 constructs `new ChangeSet(Set.of(cell), Set.of(), 41L)`; §4.6 describes only
// changed cells and changedTypes. What the third `long` is (a capture revision? a ledger revision?)
// is stated nowhere.
public record ChangeSet(Set<LatticeKey> cells, Set<?> changedTypes, long revision) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/DropCause.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public enum DropCause { SNAPSHOT_CHANGED, CAPTURE_INVALID }
```

## `dev/vfyjxf/cumulus/kernel/engine/Fingerprint.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

// INVENTED: 11 §4.3 lists what it covers (id, bounds, bay cuboids with Coverage/Footprint/Sharing,
// anchor specs, orientation policy, demand, each plan's unit ids) but gives no member or factory.
public record Fingerprint(String digest) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/InstanceBudget.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

public final class InstanceBudget {

    private InstanceBudget() {}

    public static InstanceBudget of(Budget limit) { throw new UnsupportedOperationException(); }

    public static InstanceBudget unbounded() { throw new UnsupportedOperationException(); }

    public Budget limit() { throw new UnsupportedOperationException(); }

    public Spend spend() { throw new UnsupportedOperationException(); }

    // INVENTED: 11 §4.6 gives four more members (`discards(unit)`, `attempts(unit)`, `spent()`,
    // `tryCharge(cells, sections)`) that contradict §3's `limit()/spend()` pair; both are declared
    // here because §4.1 calls `tryCharge` and §3 calls `spend`.
    public Spend spent() { throw new UnsupportedOperationException(); }

    public boolean tryCharge(int cells, int sections) { throw new UnsupportedOperationException(); }

    public long discards(NamespacedId unit) { throw new UnsupportedOperationException(); }

    public long attempts(NamespacedId unit) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/InstanceRecord.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import org.jspecify.annotations.Nullable;

public final class InstanceRecord {

    private InstanceRecord() {}

    public static InstanceRecord of(InstanceId id) { throw new UnsupportedOperationException(); }

    public InstanceId id() { throw new UnsupportedOperationException(); }

    public long generation() { throw new UnsupportedOperationException(); }

    public MatchResult.@Nullable Formed previous() { throw new UnsupportedOperationException(); }

    public long nextGeneration() { throw new UnsupportedOperationException(); }

    public void remember(MatchResult.Formed formed) { throw new UnsupportedOperationException(); }

    public MatchResult.@Nullable Formed reemit(FindingBundle findings) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/InstanceRef.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

// INVENTED: 11 §4.6 says `InstanceRef.generation` is instance generation; no other member is named.
public record InstanceRef(InstanceId id, long generation) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/InvalidationIndex.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.List;
import org.jspecify.annotations.Nullable;

// INVENTED: 11 §1 names the type; §5 uses `record(id, readSet)`, `instances()`, `affectedBy(change)`
// and treats a null answer as "re-check everything". No declaration exists anywhere.
public interface InvalidationIndex {

    void record(InstanceId id, ReadSet readSet);

    List<InstanceRef> instances();

    @Nullable List<InstanceRef> affectedBy(ChangeSet change);
}
```

## `dev/vfyjxf/cumulus/kernel/engine/MatchOutcome.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import java.util.List;

// INVENTED: 11 §3 says it is "ordered: bays by id, seams by id, claims by cell then bay" and §4.1
// reads `outcome.stepReads()`, but no document declares a member. `claims()` is the contradiction:
// 08 §1/§4.5 and 20 §6.4 say a row exists only after `Settled`, so a match outcome cannot hold one.
public record MatchOutcome(List<BayBinding> bays, List<SeamBinding> seams, ClaimSet claims) {

    public ReadSet stepReads() { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/MatchPolicy.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public record MatchPolicy(int maxSectionsPerUnit, CacheMode cache, boolean parallel) {

    public static MatchPolicy defaults() { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/MatchRequest.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import org.jspecify.annotations.Nullable;

public record MatchRequest(Blueprint blueprint, Siting siting,
                           SnapshotLease lease, MatchPolicy policy, InstanceBudget budget,
                           PinBudget pins, InstanceRecord record, OwnerId owner,
                           @Nullable Ticket ticket) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/MatchResult.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import dev.vfyjxf.cumulus.kernel.claim.ClaimPlan;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface MatchResult extends AutoCloseable
        permits MatchResult.Admitted, MatchResult.Dropped, MatchResult.Formed, MatchResult.Failed,
                MatchResult.Undecided, MatchResult.Deferred {

    Stage stage();

    FindingBundle findings();

    @Override default void close() {}

    record Admitted(Stage stage, Siting siting, MatchOutcome outcome,
                    @Nullable ClaimPlan planned, long ledgerRevision,
                    SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }
    }

    // INVENTED: the document's component is `List<Finding> findings`, which cannot implement
    // `FindingBundle findings()`; the component is renamed `errors` and the accessor throws.
    record Dropped(Stage stage, DropCause cause, List<Finding> errors) implements MatchResult {
        @Override public FindingBundle findings() { throw new UnsupportedOperationException(); }
    }

    record Formed(Stage stage, InstanceId id, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long generation, long contextRevision, long ledgerRevision,
                  SnapshotLease lease, FindingBundle findings) implements MatchResult {
        @Override public void close() { lease.close(); }

        public ReadSet readSet() { throw new UnsupportedOperationException(); }
    }

    record Failed(Stage stage, List<Finding> errors) implements MatchResult {
        @Override public FindingBundle findings() { throw new UnsupportedOperationException(); }
    }

    record Undecided(Stage stage, UndecidedReason reason, List<Finding> errors,
                     @Nullable MatchOutcome partial, @Nullable RetryToken retry) implements MatchResult {
        @Override public FindingBundle findings() { throw new UnsupportedOperationException(); }
    }

    record Deferred(Stage stage, RetryToken token, List<Finding> errors) implements MatchResult {
        @Override public FindingBundle findings() { throw new UnsupportedOperationException(); }
    }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/MatchSession.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

// INVENTED: 11 §3 gives these members only as a trailing comment ("idempotent run(), spend(),
// remaining(), readSet(), probe(); writes are internal") and §4.8 says it is internal and one-shot.
// Every return type below is a guess; `remaining()` in particular could be a `Budget` or a `Spend`.
public final class MatchSession {

    public MatchResult run() { throw new UnsupportedOperationException(); }

    public Spend spend() { throw new UnsupportedOperationException(); }

    public Spend remaining() { throw new UnsupportedOperationException(); }

    public ReadSet readSet() { throw new UnsupportedOperationException(); }

    public ProbeSession probe() { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/PinBudget.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public final class PinBudget {

    private PinBudget() {}

    public static PinBudget of(long limit) { throw new UnsupportedOperationException(); }

    public static PinBudget unbounded() { throw new UnsupportedOperationException(); }

    public long limit() { throw new UnsupportedOperationException(); }

    public long retained() { throw new UnsupportedOperationException(); }

    public long remaining() { throw new UnsupportedOperationException(); }

    public boolean tryCharge(long bytes) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/PlanId.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public record PlanId(long value) {}
```

## `dev/vfyjxf/cumulus/kernel/engine/ReadSet.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import java.util.Set;

// INVENTED: 11 §4.6 says "`ReadSet.cells` is durable, `occupants` snapshot-scoped"; nothing declares
// the container type, the union rule behind `Formed.readSet()`, or a factory.
public record ReadSet(Set<LatticeKey> cells, Set<OccupantRef> occupants) {

    public ReadSet union(ReadSet other) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/RetryToken.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

public record RetryToken(Ticket ticket, Stage stage) {

    public static RetryToken of(Ticket ticket, Stage stage) { throw new UnsupportedOperationException(); }

    // INVENTED: 08 §5 calls `RetryToken.of(m.expected(), m.actual())`, a two-long form that no
    // declaration in 11 §3 admits.
    public static RetryToken of(long expected, long actual) { throw new UnsupportedOperationException(); }

    public RetryToken next() { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/SeamBinding.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

// INVENTED: 11 §1/§2/§4.1 name `SeamBinding` and order seams by id; no member is ever given.
public interface SeamBinding {}
```

## `dev/vfyjxf/cumulus/kernel/engine/Spend.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public record Spend(int cells, int sections) {

    public static final Spend ZERO = null;
}
```

## `dev/vfyjxf/cumulus/kernel/engine/StructureKernel.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;

// INVENTED: 23 §2 and 11 §3 declare `check` and `settle` as bare functions belonging to no type;
// only 20 §3.1 and 23 §9.4 name `StructureKernel` in passing. The class, its injected ledger
// (11 §4.1 calls both `ledger.read` and `facade.settle`), and its construction are undescribed.
public final class StructureKernel {

    public MatchResult check(MatchRequest request) { throw new UnsupportedOperationException(); }

    public MatchResult settle(MatchRequest request, MatchResult.Admitted admitted) {
        throw new UnsupportedOperationException();
    }

    public Siting siting(Blueprint blueprint, LatticePoint origin) { throw new UnsupportedOperationException(); }

    // INVENTED: 11 §5 calls `kernel.siting(furnace, point, lease)` with three arguments while
    // 11 §4.2 and 20 §3.1 call `facade.siting(blueprint, origin)` with two.
    public Siting siting(Blueprint blueprint, LatticePoint origin, SnapshotLease lease) {
        throw new UnsupportedOperationException();
    }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/Tally.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

import static dev.vfyjxf.cumulus.stub.Stub.*;
import dev.vfyjxf.cumulus.kernel.claim.ClaimPlan;

// INVENTED: 11 §4.1 is the only mention — `tally.add(check, check.run(...))`, `tally.fold()`, and a
// `Fold` with `failed()/undecided()/reason()/findings()/plan()`. Neither the type nor any member is
// declared in §3.
public final class Tally {

    private Tally() {}

    public void add(Check<?> check, Reply reply) { throw new UnsupportedOperationException(); }

    public Fold fold() { throw new UnsupportedOperationException(); }

    public record Fold(boolean failed, boolean undecided, UndecidedReason reason,
                       FindingBundle findings, ClaimPlan plan) {}
}
```

## `dev/vfyjxf/cumulus/kernel/engine/Ticket.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

public record Ticket(long serial) implements Comparable<Ticket> {

    public Ticket next() { throw new UnsupportedOperationException(); }

    // INVENTED: `implements Comparable` is declared with no documented order.
    @Override public int compareTo(Ticket other) { throw new UnsupportedOperationException(); }
}
```

## `dev/vfyjxf/cumulus/kernel/engine/UndecidedReason.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

// INVENTED order beyond the documented precedence line: 11 §4.4 fixes the first eight; NO_JUDGING_UNIT
// (§4.1) and WORKER_FAILED (20 §3.3) are named there but never placed in the precedence.
public enum UndecidedReason {
    CANCELLED, DIRTY_UNSTABLE, BUDGET_EXHAUSTED, LEDGER_MOVED, LEDGER_UNAVAILABLE,
    UNREADABLE, ABSTAINED, NO_STEPS, NO_JUDGING_UNIT, WORKER_FAILED
}
```

## `dev/vfyjxf/cumulus/kernel/engine/UnitId.java`

```java
package dev.vfyjxf.cumulus.kernel.engine;

// INVENTED: 11 §4.7 puts `UnitId` inside `CacheKey` and §4.6 keys slot counters by unit id, but no
// document declares it; 20 §6.3 only says a plan unit is one `Computation` or one `Check`.
public record UnitId(String value) {}
```

## `dev/vfyjxf/cumulus/stub/Stub.java`

```java
// STUB: one file holding every type the four probe chapters name but never describe, or describe
// only in passing. Imported with `import static ...Stub.*;` so the skeleton keeps the documents'
// simple names. Each nested type is a stub, not a design.
package dev.vfyjxf.cumulus.stub;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import java.util.List;

public interface Stub {

    // STUB: "one world"; no member is ever named.
    final class World {}

    // STUB: 08 §8 names `Siting.origin()` and `Siting.placement()` and nothing else.
    record Siting(LatticePoint origin, Placement placement) {}

    // STUB: mentioned only through `Siting.placement()`.
    final class Placement {}

    // STUB: 11 §4.2 names `Heading` and `authoredFront`; no member described.
    enum Heading { NORTH, EAST, SOUTH, WEST }

    // STUB: opaque identity.
    record OwnerId(String value) {}

    // STUB: opaque id; 08 §5 rebuilds one with `NamespacedId.of(ns, path)`.
    record NamespacedId(String namespace, String path) {
        public static NamespacedId of(String namespace, String path) { return null; }
    }

    // STUB: 08 §8 says `LatticeKey` is comparable and has `toPoint()`.
    record LatticePoint(int x, int y, int z) {}

    record LatticeKey(int x, int y, int z) implements Comparable<LatticeKey> {
        public LatticePoint toPoint() { return null; }
        @Override public int compareTo(LatticeKey other) { return 0; }
    }

    // STUB: 08 §8 names `CuboidSet.of/intersection/union`; 20/11 pass it where a `Cuboid` is declared.
    final class CuboidSet {
        public static CuboidSet of(Cuboid... boxes) { return null; }
        public CuboidSet union(CuboidSet other) { return null; }
        public CuboidSet intersection(CuboidSet other) { return null; }
        public boolean isEmpty() { return false; }
        public java.util.Set<LatticeKey> cells() { return null; }
    }

    // STUB: the abort box; 11 §4.3 gives it `bounds()/volume()`, 11 §4.1 `volume()`.
    record Cuboid(LatticePoint min, LatticePoint max) {
        public Cuboid inflate(int amount) { return null; }
        public long volume() { return 0L; }
        public boolean contains(LatticeKey cell) { return false; }
    }

    // STUB: 20 §4.1 says it answers `slot(SectionKey)`; no member on this probe's path.
    interface WorldView {}

    // STUB: 08 §4.4, 23 §7 name DESIGN / PROBE / FORM / SETTLE and no other constant.
    enum Stage { DESIGN, PROBE, FORM, SETTLE }

    // STUB: 08 §4.6 uses `FindingCode.of`, `Finding.builder`, `LabelRole`, `Locus`; only the type matters here.
    record Finding(String code) {}

    // STUB: 08 §2 says the (owner, bay) pairing lives in the bundle; no member named.
    final class FindingBundle {}

    // STUB: 08 §8 names `bays()`; 11 §4.1 names `expand(stage)`.
    final class Blueprint {
        public List<Bay> bays() { return null; }
        public CheckPlan expand(Stage stage) { return null; }
    }

    // STUB: 08 §8 names `Bay.id()/cells()/coverage()/footprint()/sharing()`.
    record Bay(NamespacedId id, Cuboid cells, Coverage coverage, Footprint footprint, Sharing sharing) {}

    // STUB: 08 §4.3 names EVERY_CELL / ONE_SUBJECT / ANY_SUBJECT.
    enum Coverage { EVERY_CELL, ONE_SUBJECT, ANY_SUBJECT }

    // STUB: 09's type; 11 §4.1 names only `checks()`.
    interface CheckPlan {
        List<Check<?>> checks();
    }

    // STUB: 11 §4.1 names `check.reads(...)`, `check.run(...)`.
    interface Check<T> {
        NamespacedId id();
        ReadScope reads(Cuboid cells);
        Reply run(ContextView focus);
    }

    // STUB: 11 §4.1 names `scope.cells()`.
    interface ReadScope {
        Cuboid cells();
    }

    // STUB: 11 §4.6 / 08 §4.5 name `OccupantRef.snapshot`.
    interface OccupantRef {
        SnapshotId snapshot();
    }

    // STUB: opaque instance identity.
    record InstanceId(String value) {}

    // STUB: 20 §4.1 gives `id/view/changeCount/isValid/isOpen/retain/close`.
    interface SnapshotLease extends AutoCloseable {
        SnapshotId id();
        WorldView view();
        long changeCount();
        boolean isValid();
        boolean isOpen();
        Outcome<SnapshotLease> retain(Object budget);
        @Override void close();
    }

    // STUB: 20 §4.1 calls it capture identity and never names a member.
    record SnapshotId(World world, long counter) {}

    // STUB: 07's type; 11 §4.8 says a unit sees only a ProbeSession.
    interface ProbeSession {}

    // STUB: 11 §4.2 / 20 §4.1 return `Outcome<T>`; no arm is ever named on this path.
    interface Outcome<T> {}

    // STUB: 11 §2 names `ContextKey` with its `Merge`, `ContextDraft.contribute`, `ContextView.require`.
    interface ContextKey<T> {}

    interface ContextView {
        <T> T require(ContextKey<T> key);
    }

    // STUB: 11 §4.6 / 20 §7 : "the slot owns the InstanceBudget".
    final class Slot {}

    // STUB: 07's per-check reply; 11 §4.1 folds it, never names a member.
    interface Reply {}

    // STUB: 20 §4.1 names READABLE / NOT_CAPTURED / OUT_OF_BOUNDS.
    enum ReadStatus { READABLE, NOT_CAPTURED, OUT_OF_BOUNDS }
}
```

## `org/jspecify/annotations/Nullable.java`

```java
package org.jspecify.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Nullable {}
```

