# 23. Claim entry: a read-only check, a writing settle

> Decision record, 2026-10-23. Status: **decided**; every unit file it touches is updated, and the
> code migration is §9. It replaces `MatchRequest.stage` (`22` §9.1) and the `SettlementIntent`
> vocabulary (`20` §5.3); a stage is no longer declared by an author or chosen by a caller (§7).

## 1. Problem

One structure check does two things of different natures:

1. read blocks and answer "do these blocks form this structure" — **read-only**;
2. write "this region belongs to this instance" into the ledger — **a side effect**.

The code merges both into one call, with the second triggered by `stage=SETTLE` on the request
(`StructureKernel.check` → `MatchSession.admit` → `MatchSettler.settle`). Three consequences:

| Consequence | Mechanism | What the player sees |
| --- | --- | --- |
| Conflicts surface too late | the kernel never calls `ClaimLedger.read`; only `settle` reads a revision | the machine is fully built and the materials are spent before "this region is taken" |
| A claim cannot be released | `ClaimLedger` offers `revision/settle/read` only | after a break, an unload or a move, the rows stay forever and block newcomers and the owner |
| "Look but do not register" is inexpressible | the only switch is `stage`, which also selects which checks run | auto-probing and real registration share one path and rely on caller discipline |

The second row is the worst: **a ledger that can only be written, never released, hardens a
mistake permanently.**

## 2. Decision

Two entry points; writes happen only in the second:

```java
// read-only: reads blocks and the ledger; writes nothing
MatchResult check(MatchRequest request);

// writing: the only path that may change the ledger; closes the admitted result on every path
MatchResult settle(MatchRequest request, MatchResult.Admitted admitted);
```

`check` returns `MatchResult.Admitted` — the verdict, the **plan** and **the ledger revision it read**
(`Admitted.planned()`, `Admitted.ledgerRevision()`). The plan is what the check found, not what will be
written: it carries the bindings per bay and that revision, and `settle` derives every row from the
request with `ClaimPlanner` (`08` §4.5, I-16), so a caller cannot hand it rows of its own. Those two
are `settle`'s input, and the reason a conflict can be reported *before* the player builds. The plan
type is `ClaimPlan`; it is not the per-check reply, which is `CheckResult`.

- `check` may be called at any rate: auto-probing, previews, commands and editor overlays all use it.
- `settle` is invoked only by a caller that explicitly wants registration, and only after the
  structure actually holds.
- "May this call write" is no longer a request field; it is **which method was called**.

## 3. Timing

### 3.1 The happy path

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

### 3.2 Revision moved

`settle` carries the revision `check` read. If the ledger moved in between:

1. re-read the same box (`ledger.read(box)`) and re-run the conflict matrix;
2. still conflicting → `Failed(SC-CLM-0002)`, with findings naming the rows read *this* time;
3. no conflict → retry once at the new revision;
4. moved again → `Undecided(LEDGER_MOVED)` + `RetryToken`; the caller decides whether to start over.

The rule that matters: **a retry re-reads; it never just swaps the expected revision.** Today's
`MatchSettler` only swaps the number (`MatchSettler.java:80-82`), and safety survives only because
`InMemoryClaimLedger` re-scans conflicts inside its CAS loop; a store-backed adapter that trusts the
request rows would write a stale-but-conflicting set.

### 3.3 Why a revision, not a reservation

`check` reads revision 41 and `settle` writes against 41: of two callers holding 41, exactly one
wins; the other gets a revision move and re-judges. **No temporary row is needed** to solve
"looked, then someone took it".

## 4. Result-type changes

| Type | Today | After 23 |
| --- | --- | --- |
| `MatchResult.Admitted` | a probe matched; no instance | `check` matched: verdict + `@Nullable ClaimPlan planned` + `ledgerRevision`; never writes |
| `MatchResult.Formed` | produced at settle | **produced only by `settle`**; carries the `ClaimSet` and the revision written |
| `MatchResult.Dropped` | the snapshot moved | unchanged; `settle` returns it too (§3.1 3a) |
| `MatchResult.Failed` | at least one ERROR | unchanged; the conflict code stays `SC-CLM-0002` |

`Admitted.planned()` may be `null`: with no ledger, or with a blueprint that has no registerable
bay, `check` still works, but that result cannot be handed to `settle`
(`Undecided(LEDGER_UNAVAILABLE)`).

## 5. What the ledger must gain

```java
public interface ClaimLedger {
    long revision();
    ClaimVerdict settle(SettleRequest request);          // exists
    ClaimView read(Cuboid box);                          // exists; the kernel starts using it
    ClaimVerdict release(OwnerId owner, Siting siting);  // new: delete every row of that key
}
```

`release` semantics: delete every row of `(world, owner, siting)` and advance the revision by one; an
absent key is a no-op (no error, no revision bump). Server thread only.

Three lifecycle points must call it:

| Moment | Caller | Consequence if missed |
| --- | --- | --- |
| an instance is removed | `dropInstance` | the dead instance holds the region forever |
| a world unloads | `LevelEvent.Unload` (the Minecraft side's name) | every claim in that world leaks |
| an instance moves | the move handler, before `settle` at the new siting | the old rows block the owner's own new position |

## 6. Three layers of conflict decision

| Layer | When | Evidence | Failure |
| --- | --- | --- | --- |
| inside the blueprint (static) | blueprint build | bay geometry and declared cells | `SC-BAY-*` / `SC-BPT-0002` (thrown at build) |
| outside the blueprint (advisory) | `check` | the rows read from the ledger | `Failed(SC-CLM-0002)`, **a prediction** |
| outside the blueprint (authoritative) | `settle` | the rows plus the revision | `Failed(SC-CLM-0002)`, authoritative |

A `check` conflict is a prediction: the rows it read may already be gone. Its finding must therefore
say "currently held", never "settlement refused". Only `settle`'s conflict is a conclusion.

## 7. Premise: stages are no longer declared by anyone

This decision and "the engine owns the pipeline" hold together:

| Item | After 23 |
| --- | --- |
| `Check` | `(id, bay, focus, expectation, description)`, no `stage` |
| document JSON | no `"stage"` field |
| execution order | dependency (keys produced) → read-closure volume ascending → declaration index |
| `Stage.STALE` | deleted; a targeted re-check is `check` plus a `ChangeSet` on the request |
| `Stage` in results | kept, filled by the engine (blueprint validation / world pass / settlement) to say which step produced the finding |
| settlement trigger | the `settle` entry point, no longer `stage=SETTLE` |

`settle` therefore needs no notion of a stage; it only needs to know that writing is allowed.

## 8. Open items

| Question | Today | Impact |
| --- | --- | --- |
| `EXCLUSIVE` with a `None` footprint | accepted by code; zero rows | answered by `24` R1: refused at build |
| a `Bound` bay with no binding | silently claims nothing | an instance forms while holding no cells |
| overlapping entity windows | `ANY_SUBJECT`/`None` emit zero rows; two pens never conflict | two mob traps can occupy one space |
| `SC-CLM-0003` (a `SHARED` claimant asking for too much) | specified; the code only emits 0002 | a host cannot tell "I overreached" from "you overreached" |
| `SC-CLM-0104` (boundary residue) | specified; no producer | shrinking a structure stops re-checking the cells it gave up |
| moving an instance | the old key is never released automatically | depends on the host calling `release` per §5 |

## 9. Migration

Code, in order:

1. `ClaimLedger`/`InMemoryClaimLedger`: add `release`, plus a test that a released key reads empty and the revision advanced.
2. `MatchSession.admit`: build the `ClaimPlan.Matched` only — the bindings and the revision, never rows — and never call the settler; move the settle path out.
3. `MatchResult.Admitted`: add `@Nullable ClaimPlan planned` and `long ledgerRevision`.
4. `StructureKernel.check`: derive the rows for its prediction with `ClaimPlanner` and read the ledger over them; `settle` derives them again after checking the bindings (`SC-CLM-0005`), then CASes (`19` R36).
5. `MatchSettler`: split into validate and write; the `RevisionMoved` branch re-reads and re-judges; the
   ledger cases use `SC-CLM-0004`, never `SC-ENG-0003`/`SC-ENG-0004`, whose meanings are frozen in `13`.
6. `MatchRequest`/`Stage`: change with §7 (stage removed, `ChangeSet` hint added).
7. `SnapshotInfo`: delete. `SnapshotLease` gains `changeCount()` and `isValid()`, `Ticket` leaves the capture and stays on `MatchRequest` for the scheduler, `check` and `settle` lose their suppliers, and `settle` takes the `Admitted` and closes it on every path (`19` R37).
8. Tests: existing SETTLE cases move to `settle`; add "check writes nothing", "revision moved re-reads", "a released key can be claimed again".

Docs: `08` §4.5/§4.6 (one call → two), `11` §4.1/§5, `15` §3 (the entry-point sketch and the nine
operations that `00` R-14 and `01` also count), `16` §4.9/§4.10, `18`'s counterexamples,
`20` §5.3 (`SettlementIntent` removed), `22` §9.1/§10, `02`, `04`, `17` §5–§7, `19` (the disposition
record), and the `zh/**` mirror of every file this list touches.
