# W1 — Execution model and lifecycle

> Round-1 draft. Host-side only: `ServerState` owns the executor; `StructureKernel.match` stays a
> synchronous pure call over a snapshot, so no thread, future or callback enters the kernel. Existing
> names are used, never renamed; additions are in §8.

## 1. Scope and invariants

W1 owns dispatch, lifecycle and failure handling for a pure `kernel.match`.

| # | Invariant |
| --- | --- |
| W-1 | The server thread is the only writer of scheduler state: capture, refresh, ledger freeze, settlement, findings, callbacks. |
| W-2 | A worker reads only `StructureSnapshot`, `Blueprint`, `Siting`, `MatchPolicy`, a frozen `ClaimLedger`, `CancelToken`; never `Level`, `ChunkAccess`, `BlockEntity`, `Entity`, `SavedData`. |
| W-3 | At most one task per `CheckKey` is `QUEUED`/`RUNNING`/`POSTED`. |
| W-4 | A result is applied only if `version`, `SnapshotId` and level epoch equal the slot's current values; else discarded. |
| W-5 | Every permit, pin and handle completes exactly once, including `Error`, rejection and shutdown. |
| W-6 | After level unload, instance removal or `ServerStopping`: no settle and no `onSettled`; `done()` still completes with `Discarded`. |
| W-7 | No tick slicing, no `nanoTime`/`TimeSource` in the kernel; budget is work units; a host deadline may only cancel. |

## 2. API

All names below are `dev.vfyjxf.cumulus.mc` (A1); the kernel signature set is unchanged.

```java
public record CheckKey(NamespacedId world, NamespacedId owner, Siting siting) implements Comparable<CheckKey> {}
public enum Priority { INTERACTIVE, COMMAND, AUTO }
public enum Settlement { NEVER, IF_FORMED }              // only COMMAND passes IF_FORMED
public record CheckRequest(CheckKey key, Blueprint bp, List<Stage> stages, MatchPolicy policy,
                           Settlement settlement, Priority priority, @Nullable ChangeSet change) {}

public final class MatchScheduler implements AutoCloseable {          // one per ServerState
    public static MatchScheduler open(ServerState state, SchedulerOptions options);
    public MatchHandle submit(CheckRequest request);                  // server thread only
    public void markDirty(CheckKey key, ChangeSet change);            // from DirtyIndex (W3)
    public void dropInstance(CheckKey key);
    public void levelUnloaded(NamespacedId world, long epoch);        // LevelEvent.Unload
    public SchedulerMetrics metrics();
    @Override public void close();                                    // ServerStopping
}

public final class MatchHandle {                                      // one submit, one handle
    public CheckKey key();  public long version();  public MatchState state();  public CancelToken token();
    public CompletableFuture<MatchOutcome> done();                    // completes on the server thread
    public void onSettled(Consumer<MatchOutcome> cb);                 // once, server thread
    public boolean cancel(CancelReason reason);                       // cooperative
}
public enum MatchState { QUEUED, RUNNING, POSTED, SETTLED, DROPPED, FAILED, DEFERRED, CANCELLED }
public sealed interface MatchOutcome {
    record Reported(MatchResult result, @Nullable SavedClaims.Settlement settlement) implements MatchOutcome {}
    record Discarded(DiscardReason reason, long resultVersion, long currentVersion) implements MatchOutcome {}
    record Failed(MatchResult.Undecided reported, Throwable cause) implements MatchOutcome {}
}
// DiscardReason: STALE_VERSION, SUPERSEDED, LEVEL_UNLOADED, INSTANCE_REMOVED, SERVER_STOPPING, DEFERRED_BACKPRESSURE, SLOT_BROKEN
// CancelReason: SUPERSEDED, LEVEL_UNLOADED, INSTANCE_REMOVED, SERVER_STOPPING, DEADLINE, USER
public record SchedulerOptions(int threads, int backlog, int syncMaxCells, long maxSnapshotBytes,
                               long deadlineMillis, int autoReprobeTicks) {}   // 2, 2, 4096, 64 MiB, 10 s, 10
```

`FrozenLedger(claims, readBox, revision)` implements `ClaimLedger`: `claimsIn` filters the frozen list
and answers `null` for any box not inside `readBox` (never `[]`), and `revision()` is the dispatch-time
value. `readBox` is `new ClaimPlanner().plan(blueprint, siting).footprint()`, world-free, so dispatch
reads exactly the box `SETTLE` will query.

## 3. Sequence and state machine

```
submit ── slot.inFlight? AUTO coalesce : cancel(SUPERSEDED) ──► acquire permit ─► refresh(key)
       ─capture─► pin ─► executor.execute ─► worker: match(stage 1..n) ─► post ─► settle

QUEUED ──start──► RUNNING ──ok/throw──► POSTED ──apply──► SETTLED       (settle once)
  │                  │                     │
  ▼                  ▼                     ▼
CANCELLED ◄──────────┴─────────────── DROPPED (STALE_VERSION | LEVEL_UNLOADED | …)
```

`DEFERRED` is terminal for that `submit`: no queue entry, the slot stays dirty, a later event
re-submits. The worker returns an immutable `WorkerOutcome`; the post is `server.execute(...)`, so
settle and callbacks run on the server thread. Reservations are session-local (`08`), so a cancel or
discard leaks none.

## 4. Concurrency and lifecycle

**Topology and pool.** One scheduler per server in `ServerState`, opened on `ServerAboutToStartEvent`,
closed on `ServerStoppingEvent` — not per level: pure work, one bound, one shutdown. Fixed
`ThreadPoolExecutor`, daemon platform threads `cumulus-worker-<serverId>-<n>`, `NORM - 1`, size
`clamp(2, 4, cpus / 2)`.

**Queue and admission.** `ArrayBlockingQueue(backlog)`, `AbortPolicy`, defaults 2 + 2; a
`Semaphore(threads + backlog)` acquired *before* `execute` is the gate, so the queue is a hand-off
buffer. `RejectedExecutionException` happens only on a race with `close()`: caught, permit returned,
slot stays dirty, handle `DROPPED(SERVER_STOPPING)`. A post rejected at shutdown finishes the same way.

**Backpressure.** `AUTO` without a permit is `DEFERRED`, never queued; it retries after
`autoReprobeTicks` (10), so a redstone clock cannot spin the pool. `COMMAND`/`INTERACTIVE` skip the
retry gate, never the permit, and on a busy slot cancel the in-flight handle (`SUPERSEDED`), so the
newest request wins; over `maxSnapshotBytes`, all priorities answer `DEFERRED`.

**Sync fallback.** `INTERACTIVE` under `syncMaxCells` (4096) runs inline through the same `run`/`settle`
bodies; `AUTO` never does, because a spike per block change is the failure this unit prevents.

**Who settles.** Only `settle(...)` on the server thread calls
`SavedClaims.settle(scope, claims, expectedRevision)`: `IF_FORMED` request, last stage `SETTLE`,
`Formed`, W-4 holds. `expectedRevision` is the dispatch-time `FrozenLedger.revision()`. `RevisionMoved`
→ `Undecided(LEDGER_UNAVAILABLE)` + `SC-CLM-0004`, no retry. `AUTO` is always `NEVER`.

**Snapshot lifetime, shared store.** The scheduler owns no section data. A snapshot is either (a)
per-snapshot `PalettedContainer` copies or (b) `(SectionKey, revision)` handles into a shared
`SectionStore`; either way the values are immutable for the task's life and the task holds a pin
released once in `finish`. Under (b) a replace leaves the old revision readable, so a result stays
*coherent* and is discarded only by W-4, never by "a section moved". Unpinned, (b) can mix rev 3 and
rev 4 mid-match — a silent stale form — so (b) needs retain/release plus an epoch-tagged stamp, else
W2 copies.

**Observability.** `SchedulerMetrics` from executor counters and `LongAdder`s: queue depth, active,
pinned bytes, submitted/deferred, stale discards, failures, settle outcomes, p50/p99 from adapter
`nanoTime` at dequeue/post only.

**Unload and stop.** `LevelEvent.Unload` cancels that dimension's slots, releases pins, bumps the
epoch; a queued post becomes `DROPPED(LEVEL_UNLOADED)`. `ServerStopping`: `accepting = false`, cancel
all, `shutdown`, `awaitTermination(2 s)`, `shutdownNow`, drain returned tasks, clear slots and
callbacks. A chunk unload cancels nothing — the snapshot stays true for its revision; the next capture
sees `NOT_CAPTURED`. `dropInstance` cancels and forbids settle.

## 5. Failure and recovery

| Fault | Handling | Outcome |
| --- | --- | --- |
| `RuntimeException` from kernel | log once, worker survives | `Undecided(WORKER_FAILED)` + `SC-MC-0601` |
| `OutOfMemoryError` / `StackOverflowError` | drop pin, release permit, pre-built bundle | `Failed(SC-MC-0602)`; key paused, `BROKEN` after 3 |
| other `VirtualMachineError`/`LinkageError` | release in `finally`, rethrow | thread dies, handler logs, slot `SLOT_BROKEN` |
| `RejectedExecutionException` | release permit, leave dirty | `DROPPED(SERVER_STOPPING)` or `DEFERRED` |
| over `deadlineMillis` | watchdog (server tick, adapter `nanoTime`) → `token.cancel(DEADLINE)` | `CANCELLED` at the next step boundary; a second interval marks `SLOT_BROKEN` |
| cancelled (`token`) | kernel folds `CANCELLED` at a step boundary | `DROPPED(<CancelReason>)`, no settle |

Three consecutive failures mark the key `BROKEN`: `AUTO` stops, `INTERACTIVE` clears it; no
`Thread.stop`, no pool growth.

## 6. Proofs

**P-1 single writer and bounded queue (W-1, W-3, W-5).** Slot mutations assert
`server().isSameThread()`; the only cross-thread objects are the immutable task, an `AtomicBoolean`
permit guard and the `post` runnable, whose queue insertion gives happens-before. One acquire precedes
the single `execute`, one `releaseOnce` runs in `finally` and on every non-running path, so
`queued + active ≤ threads + backlog`, pins included, and `inFlight` cannot clear twice.

**P-2 no stale form (W-4).** `settle` compares `slot.currentVersion`, `slot.snapshotId` and
`task.levelEpoch` with the result's, on the same thread, before `facts().claims()` is read.

**P-3 no post-unload effect (W-6).** `close()`/`levelUnloaded()` set `accepting`/`epoch` on the server
thread before returning; `settle` checks them before `acceptAll` and `onSettled`, and instead finishes
`done()` with `Discarded` so no caller awaits forever.

**P-4 no leak, no deadlock.** `finish` clears snapshot, pin, permit, handle, future and callback;
workers never call `server.execute(...).get()`, and `close()` refuses work before `awaitTermination`.

**P-5 determinism.** The result is a function of `(snapshot, blueprint, place, stage, policy, frozen
ledger, cancellations)`, fixed before dispatch; thread choice is not an input.

## 7. Counterexamples

| # | Scenario | Must happen | Naive design that fails |
| --- | --- | --- | --- |
| CE-1 | Redstone clock toggles one bay block 20×/s. | One run, ≤1 discard per window, `queued ≤ backlog`. | Submit-per-dirty: 1200 tasks, snapshots pinned, OOM. |
| CE-2 | Level unloads during a FORM task. | `DROPPED(LEVEL_UNLOADED)`; no settle, no `onSettled`, pin and permit released. | Settle into a dead `SavedData`. |
| CE-3 | Chunk unloads mid-run, level alive. | Result applied; next capture `NOT_CAPTURED`. | Tying validity to chunk liveness loses a correct result and re-runs per cycle. |
| CE-4 | Shared `SectionStore` replaces a pinned section. | Epoch and `currentVersion` move ⇒ `DROPPED`; else the pinned revision is applied, never a blend. | Live reads mix revisions and file a `Formed` for a snapshot that never existed. |
| CE-5 | NPE, then OOM, on consecutive attempts. | `WORKER_FAILED`, then `SC-MC-0602`; both threads active; key paused. | `catch (Exception)`: OOM kills the thread and the pool shrinks. |
| CE-6 | Instance removed after `/cumulus build` dispatch. | Cancelled; no claims; late `Formed` is `DROPPED(INSTANCE_REMOVED)`. | Claims written for a deleted structure. |
| CE-7 | `submit` races with `ServerStopping`. | REE caught, permit returned, handle `DROPPED`. | REE into the tick loop, or a leaked permit. |

## 8. Integration, API delta, open questions

**Delta.** (1) `02` §5 and `11` carry `TimeSource`/`Budget.nanos`; W-7 removes both. (2)
`UndecidedReason` needs `WORKER_FAILED`; `13` needs `SC-MC-0601` (worker threw), `0602` (resource
exhaustion), `0603` (deadline), `0604` (malformed outcome). (3) `15` §4.4 allows a narrowed ledger;
`FrozenLedger` is that concretely. (4) Every new A1 name in §2 needs a `01` row (`EXPERIMENTAL`).

| Unit | Needs |
| --- | --- |
| W2 | `estimatedBytes()`, `release()`, epoch/revision stamp; a shared `SectionStore` only with retain/release. |
| W3 | `refresh(key)` bounded and server-thread; `versionOf(key)`; `markDirty` never dispatches; unload eviction hook. |
| W4 | `SectionResult` keyed by `(SectionKey, snapshotVersion)`. |
| A1 | `ServerState` owns the scheduler; unload/stop wiring; `settle` only from `settle(...)`; `/cumulus workers`. |

**Open questions.** (1) 2 threads or `cpus/2`? (2) `AUTO`: chain `PROBE → FORM` or `FORM` only, and
does `derive` share the worker? (3) Deadline cancel: finding or metric? (4) Can W2 price a snapshot
before building it? (5) Should a block-change `STALE` settle claims? Current: no.
