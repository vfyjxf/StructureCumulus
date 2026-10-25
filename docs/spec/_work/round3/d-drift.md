# Round 3 · D — spec-vs-code drift audit

Repo `Project/StructureCumulus`. Read-only; nothing under `src/`, `docs/spec/**` or the zh mirrors was modified. Record: `22-consolidated-design.md` (`22:3` = "revision 5, 2026-10-20"), decisions `23`, `24`.

> Length note: this file is ~16.7 KB, above the ~12 KB target. All six required sections and all verbatim signatures are kept; the overflow is the 20-row §3 table plus the 11 unregistered contradictions in §5, neither of which can be cut without dropping requested content.

## 1. Commands run and raw outcomes

| Command (repo root) | Raw outcome |
| --- | --- |
| `./gradlew compileKernelJava kernelPurity compileJava compileTestJava --console=plain` | `BUILD SUCCESSFUL in 2s`; `13 actionable tasks: 2 executed` (`kernelPurity`, `compileTestJava`), 11 up-to-date. `kernelPurity` **ran and passed**. |
| `./gradlew kernelTest --console=plain` | `BUILD SUCCESSFUL`, but `:kernelTest UP-TO-DATE` — did **not** execute; not evidence. |
| `./gradlew kernelTest --rerun-tasks --console=plain` | `BUILD SUCCESSFUL in 3s`; `7 executed`. From `build/test-results/kernelTest/*.xml`: **tests=162, skipped=1, failures=0, errors=0**. |

The one skip is `LivenessScenarioTest.java:37 @Disabled("engine not implemented")` — the only `@Disabled`/`@Ignore` in `src/`, i.e. GAP-13.

## 2. Code surface (verbatim signatures, file:line)

- `MatchRequest` `engine/MatchRequest.java:17-20`: `public record MatchRequest(SnapshotInfo snapshotInfo, Blueprint blueprint, Siting siting, Stage stage, SnapshotLease lease, MatchPolicy policy, InstanceBudget budget, PinBudget pins, InstanceRecord record, OwnerId owner)`.
- `Stage` `base/Stage.java:3-9`: `DESIGN, PROBE, FORM, SETTLE, STALE` (`STALE` `:8`; used at `MatchSession.java:520`, `Blueprint.java:169`).
- `StructureKernel.check` `spi/StructureKernel.java:75-83`; **no `settle(...)` method exists**:
```java
public MatchResult check(MatchRequest request, Supplier<SnapshotInfo> live) {
    ClaimLedger claims = ledger;
    if (request.stage() == Stage.SETTLE && claims == null) { return noLedger(request.stage()); }
    return new MatchSession(request, claims == null ? null : MatchSettler.inline(claims)).run(live);
}
```
- `MatchSession.admit` `engine/MatchSession.java:346-371` (SETTLE gates also `:137`, `:520`):
```java
if (request.stage() != Stage.SETTLE) {
    return new MatchResult.Admitted(request.stage(), request.siting(), outcome, child, notes);
}
ClaimPlan plan = ClaimPlanner.plan(request.blueprint(), request.siting(),
        bayBindings.values(), request.owner());
...
MatchResult settled = Objects.requireNonNull(settler, "settler")
        .settle(request, outcome, store.revision(), rows, instanceRecord, child);
```
- `MatchSettler` `engine/MatchSettler.java:30-31`: `static final FindingCode LEDGER_MOVED = FindingCode.of(FindingCode.Family.ENG, 3);` and `static final FindingCode SETTLE_UNAVAILABLE = FindingCode.of(FindingCode.Family.ENG, 4);`. `:49-50`: `public MatchResult settle(MatchRequest request, MatchOutcome outcome, long contextRevision, List<ClaimRow> rows, InstanceRecord record, SnapshotLease lease)`. `:79-82`: on `RevisionMoved` with `retriesLeft > 0` it calls `attempt(request, ..., moved.actual(), retriesLeft - 1)` resubmitting the *same* rows; `:84-89` otherwise builds an `ERROR` finding from `LEDGER_MOVED` with `"the ledger moved from revision " + moved.expected() + " to " + moved.actual() + " again after one retry"`. Kernel has no `ledger.read` caller (grep → 0).
- `MatchResult` `engine/MatchResult.java:20-22`: `permits MatchResult.Admitted, MatchResult.Dropped, MatchResult.Formed, MatchResult.Failed, MatchResult.Undecided, MatchResult.Deferred` (6 arms). Cases: `Admitted(Stage stage, Siting siting, MatchOutcome outcome, SnapshotLease lease, FindingBundle findings)` `:26`; `Formed(InstanceId id, Siting siting, MatchOutcome outcome, ClaimSet claims, long generation, long contextRevision, long ledgerRevision, SnapshotLease lease, FindingBundle findings)` `:48`; `Dropped(DropCause cause, List<Finding> findings)` `:84`; `Failed(List<Finding> findings)` `:100`; `Undecided(UndecidedReason reason, List<Finding> findings, @Nullable MatchOutcome partial, @Nullable RetryToken retry)` `:114`; `Deferred(RetryToken token, List<Finding> findings)` `:128`.
- `ClaimLedger` `claim/ClaimLedger.java:5-13` — `revision()`, `ClaimVerdict settle(SettleRequest)`, `ClaimView read(Cuboid)`; **no `release`**.
- `InMemoryClaimLedger` `claim/InMemoryClaimLedger.java:22`; `settle` `:35-58` re-scans `conflictFindings(...)` inside its CAS loop (`:48-56`); `read` `:66-70` returns `new ClaimView(current.rowsIn(box), current.revision(), true)`. No `release`.
- `Bay` `blueprint/Bay.java:19-20`: `public record Bay(NamespacedId id, Cuboid cells, Coverage coverage, ClaimMode mode, ClaimScope scope, Requirement requirement, @Nullable String description)`. Refusals throw `IllegalArgumentException`: `:34`, `:38`, `:67`, `:82` → `SC-CHK-0106`; `:49` → `SC-CHK-0107`; `:58`, `:74` → `SC-BAY-0107`.
- `BayBuilder` `blueprint/BayBuilder.java:19-21`: `private final Coverage coverage; private final ClaimMode mode; private final ClaimScope scope;`; factories `:41`, `:46`: `public static BayBuilder start(NamespacedId id, Cuboid cells, Coverage coverage, ClaimMode mode, ClaimScope scope)`; refusal `:35-38` → `SC-CHK-0106`.
- `Coverage` `blueprint/Coverage.java:6-18`: `public enum Coverage { CUBIC, SUBJECT, PRESENCE;` + `public boolean accepts(ClaimScope scope) { return switch (this) { case CUBIC -> true; case SUBJECT -> !(scope instanceof ClaimScope.Owned); case PRESENCE -> scope instanceof ClaimScope.Audit || scope instanceof ClaimScope.Declared; }; }`.
- `ClaimMode` `claim/ClaimMode.java:3-10`: `EXCLUSIVE, SHARED, RESERVED` + `public boolean excludes() { return this == EXCLUSIVE; }`. `ClaimScope` `claim/ClaimScope.java:7-24`: `public sealed interface ClaimScope permits ClaimScope.Owned, ClaimScope.Occupied, ClaimScope.Declared, ClaimScope.Audit`.
- `ClaimMatrix` `claim/ClaimMatrix.java:25-26`: `OVERLAP_CODE = FindingCode.of(FindingCode.Family.CLM, 1); CONFLICT_CODE = FindingCode.of(FindingCode.Family.CLM, 2);`; `:35-45`: `public static boolean conflicts(ClaimRow a, ClaimRow b) { ... return a.mode().excludes() || b.mode().excludes(); }`; `conflictFinding(...)` `:141-155` at `Stage.SETTLE`.
- `ClaimPlanner` `engine/ClaimPlanner.java:40`: `static final FindingCode BOUNDS_MISMATCH = FindingCode.of(FindingCode.Family.BAY, 107);`; `plan(...)` `:45-69` holds `if (bay.coverage() == Coverage.SUBJECT) { ... subjectMismatch(bay, binding, siting); }` and `rows.add(new ClaimRow(cell, bay.mode(), owner, siting, bay.id()));`; `claimedCells` `:97-119` branches on `ClaimScope.Audit` (∅) / `Owned` / `Declared` / else bound footprints.
- `ClaimRow` `claim/ClaimRow.java:12-13`: `public record ClaimRow(LatticeKey cell, ClaimMode mode, OwnerId owner, Siting siting, NamespacedId bay) implements Comparable<ClaimRow>`; order key `:30-37` = cell, owner, bay.

## 3. Spec-vs-code table

| # | Touch point | Spec | Code | Matches? / Registered? | Ref |
| --- | --- | --- | --- | --- | --- |
| T1 | `MatchRequest` fields | 22 §9.1 `22:456-458` (has `Stage stage`); 23 §2/§7 removes it | `MatchRequest.java:17-20` | 22 yes, 23 no; GAP-15 text, no marker | U-9 |
| T2 | `Stage.STALE` | 23 §7 `23:145` "deleted" | `Stage.java:8` | no; GAP-15 omits it | partial |
| T3 | `check` reads the ledger | 23 §2 `23:33-36` | `StructureKernel.java:75-83`, no `read` call | no; GAP-15 | — |
| T4 | `settle(request, plan, live)` | 23 §2 `23:38-40` | absent | no; GAP-15 | — |
| T5 | `admit` builds the plan only | 23 §9.2 | `MatchSession.java:354-370` calls the settler inline | no; GAP-15 | — |
| T6 | `Admitted` + plan + revision | 23 §4 `23:88` | `MatchResult.java:26`, 5 components | no; GAP-15 | — |
| T7 | `MatchResult` arms | 22 §10.4 `22:552-561` permits 5, no `Dropped` | `MatchResult.java:20-22` permits 6 | no; **unregistered** | U-8 |
| T8 | `RevisionMoved` re-read | 23 §3.2 `23:76-82` | `MatchSettler.java:79-82` swaps revision only | no; GAP-15 (its wording is accurate) | — |
| T9 | `ClaimLedger.release` | 23 §5 `23:105` | `ClaimLedger.java:5-13` | no; GAP-15 | — |
| T10 | kernel uses `read` | 23 §5 `23:107` | 0 callers | no; GAP-15 | — |
| T11 | `read` return type | 23 §5 `23:109` `ClaimSnapshot`; 01:119 `ClaimView` | `ClaimLedger.java:12` `ClaimView` | 01 yes, 23 no; **unregistered** | U-10 |
| T12 | `Bay` components | 22 `22:311-312`; 09:48; 24 §9.2-3 | `Bay.java:19-20`, `BayBuilder.java:19-21` | no; GAP-16 | U-7 |
| T13 | `Coverage` values | 22 `22:316`; 24 §4 Q1 | `Coverage.java:7-9` `CUBIC/SUBJECT/PRESENCE` | no; GAP-16 | U-6 |
| T14 | mode+scope → `Sharing`+`Footprint` | 22 `22:317-318`; 24 §4 Q2/Q3 | `ClaimMode.java:3-5`, `ClaimScope.java:8` | no; GAP-16 (mangled text, S3) | — |
| T15 | R1–R4 replace the whitelist | 24 §5 `24:96-100`; 22 `22:327-332` | `Coverage.accepts` `:12-18` + `Bay.java:36-76` | no; GAP-16 | — |
| T16 | `ClaimRow` field | 24 §9.6; 22:520 | `ClaimRow.java:12` `ClaimMode mode` | no; GAP-16 | — |
| T17 | conflict predicate | 24 §9.5 `blocks()` | `ClaimMatrix.java:44,92` `mode().excludes()` | semantically yes; GAP-16 | — |
| T18 | planner applies R2 | 24 §9.4 | `ClaimPlanner.java:45-69` emits only `BAY:107` (R3) | no; GAP-16 silent | U-5 |
| T19 | conflict code stays `SC-CLM-0002` | 23 `23:56,97` | `ClaimMatrix.java:26`, produced `InMemoryClaimLedger.java:121` | yes; — | — |
| T20 | `MatchPolicy` readers | 22/20 | `MatchPolicy.java:5`; `policy()` has no caller anywhere | unchanged; GAP-01/02/03 + marker `:4` | accurate |

## 4. status.md staleness

| # | Claim (anchor) | Counter-evidence |
| --- | --- | --- |
| S1 | `status.md:15` "代码里对应的标记是 `TODO(GAP-xx)`，一条一行" | `grep -rn "TODO(GAP-" src/` → exactly 3 lines (`MatchPolicy.java:4`, `CacheMode.java:4`, `FindingBundle.java:12`) covering GAP-01/02/03/05/14; GAP-04, 06–13, **15, 16 have no marker**. |
| S2 | `status.md:38` GAP-15 内容 = "check/settle 拆分、`ClaimLedger.release`、`Admitted` 携带计划、`RevisionMoved` 重读" | 23 §7 also deletes `Check`'s `stage` (`Check.java:14` still `record Check<T>(NamespacedId id, Stage stage, …)`), the JSON `"stage"` field and `Stage.STALE`; §5 names three `release` call sites. None listed. |
| S3 | `status.md:39` GAP-16 内容 = "`Coverage` 值改名、`Footprint`→`Footprint`、`Sharing`→`Sharing`、R1–R4 …" | Both arrows are self-identical, so the row never names `ClaimScope`→`Footprint` / `ClaimMode`→`Sharing`; it omits 24 §9.3 (B-7 `EXCLUSIVE` only), §9.6 and the new `SC-BAY-0111`. |
| S4 | `status.md:51` "`SC-CLM-0003/0004/0005/0104` \| 4" | `SC-CLM-0005` is declared nowhere in the English spec (`grep -rl` → empty); the CLM numbers that exist are `0001, 0002, 0003, 0004, 0104`. |
| S5 | `status.md:43` "文档登记 103 个码，代码里出现 29 个" | `grep -rho "SC-[A-Z]\{2,4\}-[0-9]\{4\}" docs/spec/*.md \| sort -u` = 120; code has 26 distinct constants. Neither figure reproduces (§6). |
| S6 | `status.md:51` producer-less list = "`SC-BAY-0108/0109`" | Also producer-less, unlisted: `SC-BAY-0110` (13:341; only in a test comment) and `SC-BAY-0111` (13:342, 08:208, 22:329, 24:98; no `0111` string anywhere in `src/`). |
| S7 | `status.md:4` "一句话结论 … 上面那层 … 一层都没做" | Accurate: none of `PrescreenIndex, AttemptCache, CacheKey, MatchScheduler, WatchRefresh, LevelCapture, FrozenLedger, DocumentValidator, BlueprintDocument, SpecResolver, RemedyContext, RemedyOptions, IssueSink, ProbeSession, SectionStore, ExplainBook, PlanValidator, EditExecutor` occurs in `src/`. |
| S8 | 22 §15.5 `22:822-825`: "The `zh/**` mirrors still describe the older model … **`zh/22` does not exist yet**." | Falsified twice: `zh/22-consolidated-design.md`, `zh/23-claim-entry.md`, `zh/24-claim-vocabulary.md` all exist; `zh/23:1` = "登记入口：check 只读，settle 写入"; `zh/01:112-114` carries `Sharing`/`Footprint`/`Coverage`; `zh/08:17-18,34,39` the new vocabulary. |
| S9 | 22 §15.5 `22:822-823` re-synced list = 01, 07, 08…21 | `22`, `23`, `24` are missing although their zh mirrors exist and are translated (43822/8738/7679 B vs EN 42660/9205/7847 B). |
| S10 | 22 header `22:3` = "revision 5" | 22 absorbed 24 (`22:316-318`, `22:520`) but not 23 (`22:462` only `check`; `22:546` "The facade `check()` runs the worker match, then the server-thread `MatchSettler`"). No row says 22 needs a revision 6. See U-9. |

## 5. Unregistered contradictions

- **U-1 `SC-ENG-0003` double-booked.** 13:351 = `WARNING` "watched region changed … `Dropped(SNAPSHOT_CHANGED)`" (matches `MatchSession.java:62`); `MatchSettler.java:30` also defines `LEDGER_MOVED = ENG:3`, emitted `ERROR` (`:84-89`). 11:214/11:360/22:527 give that case `Undecided(LEDGER_MOVED)` and **no number**.
- **U-2 `SC-ENG-0004` double-booked.** 13:352 = `INFO` "steps were never reached"; `MatchSettler.java:31` `SETTLE_UNAVAILABLE = ENG:4` emits it `ERROR` "the claim ledger could not answer on the calling thread", while 08:205 puts ledger-unavailability on `SC-CLM-0004` (`WARNING`).
- **U-3 codes the code emits that the English spec never declares** (`grep -rl` over `docs/spec/*.md` → nothing): `SC-ENG-0102` (`MatchSession.java:64`), `SC-ENG-0103` (`:65`, `StructureKernel.java:44`), `SC-ENG-0201` (`:66`), `SC-ENG-0401` (`:67`), `SC-ENG-0402` (`:68`), `SC-CTX-0202` (`ContextView.java:37`, `MatchSession.java:71`, `StructureKernel.java:193`). 11:363 names the ENG family only as the unchanged range "`0101`–`0104`, `0201`, `0401`" — excluding 0402, defining none. Spec CTX = `0101,0102,0107,0108,0109,0201,0301`: `0202` is code-only, `CTX-0201` (13:357) has no producer.
- **U-4 `SC-CLM-0003` means three things.** 13:334 `INFO` "claims were returned unused because the attempt did not form"; 08:204 `ERROR` "this `SHARED` bay meets another owner's `EXCLUSIVE`"; 23:158 "a `SHARED` claimant asking for too much". Code has no `CLM:3`.
- **U-5 `SC-BAY-0111` declared and implemented nowhere.** 13:342, 08:208, 22:329, 24:98 (`(new)`); no `Family.BAY, 111` constant, no thrown message, no R2 step in `ClaimPlanner.java:45-69`; `status.md:51` lists `0108/0109` only.
- **U-6 `SC-BAY-0107` serves two rules; the code sides with the older.** 13:338 = "coverage shortfall … for `EVERY_CELL`"; 24 §5 R3 = "`ONE_SUBJECT` … bound footprint must equal the bay". Code emits both (`Bay.java:74`, `ClaimPlanner.java:40`); 24 R4 moves coverage shortfall to `SC-CHK-0106`, so `Bay.java:74` contradicts 24, agreeing with 13:338.
- **U-7 `08-claims.md` still declares the old `Footprint` shape, and the code follows 08.** `08:41` `Footprint permits Owned, Occupied, Declared, Audit` vs `09:58`, `22:318` (`Box, Bound, Declared, None`), 24 §4 Q2. `08:10` still says "**Does not own** mode and scope declaration (`Bay.mode()`, `Bay.scope()`, U5)", `08:178` "non-`Declared` scope". `ClaimScope.java:8` is exactly `08:41`.
- **U-8 `22 §10.4` arm count.** `22:552-561` permits five arms without `Dropped`; code permits six (`MatchResult.java:20-22`) and returns `Dropped` for both causes (`MatchSession.java:381-385`), while 22 keeps `Dropped` as an outcome term (`22:469`).
- **U-9 22 rev5 straddles both decisions.** 22 carries 24's vocabulary but 23's predecessor model (`22:462`, `22:546`, `22:565`); the code matches 22 §9.1/§10.3 exactly, so the drift is 22 trailing 23. GAP-15's 设计出处 cites only `23`.
- **U-10 23 §5 cites a nonexistent type.** `23:109` `ClaimSnapshot read(Cuboid box)`; the real name (01:119, 08:70/73, `ClaimLedger.java:12`) is `ClaimView`.
- **U-11 20 §5.3's `SettlementIntent` still stands in 20.** `20:57-60` (`enum SettlementIntent { UNTIL_ADMITTED, THROUGH_SETTLE }`, `MatchRequest(…, SettlementIntent settlement, Priority priority, …)`) although `23:4` says 23 replaces it and `23:174` lists its removal. Code has neither; the nearest row (GAP-06) does not mention it.

## 6. Unverifiable

- **Code counts (S5).** Neither 103 nor 29 reproduces (120 spec / 26 code). 13:371 says "The rows above are representative, not the full allocation: each unit file (`05`–`16`) owns its `SC-<FAMILY>` block", and 13's printed table has 46 rows — the denominator is not derivable from one source. A discrepancy, not a proven error.
- **"No producer" (U-3, U-5)** rests on constants plus `grep -rl` of the literal string under `src/`; a code assembled at runtime would escape it. None found.
- **zh mirrors.** Verified: existence plus vocabulary greps for `zh/01, 08, 22, 23, 24`. I did not read all 24 zh files, so I claim only that 22 §15.5's two clauses are false, not that every zh file is current.
- **T7's five-arm reading** comes from a delegated extraction of `22:552-561`; I did not re-read that block myself.
- `LivenessScenarioTest` is `@Disabled` regardless of `--rerun-tasks`, so GAP-13's async path has no passing evidence here.
