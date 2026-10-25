# Front B — entry points, runtime and facade: review round 2

Project: `/Users/vfyjxf/ModdingGround/Project/StructureCumulus`. Files read end to end this round:
`23`, `24`, the sweep contract, `19`'s third-pass table (R27–R34), then Set B's files (`00`, `02`,
`03`, `04`, `11`, `15`, `16`, `18`, `19`, `20`, `23`) plus the sides of `01`, `06`, `08`, `09`,
`13`, `22` the fifteen claims anchor in. Read-only: the only write is this report. Nothing below is
taken from the fix reports; every line was re-read at its anchor.

## 1. Method

1. Read `23` §2–§9 and the sweep contract §A–§G first, so a claim could be judged against the
   canonical surface rather than against round 1's wording.
2. For each of Set B's fifteen claims, opened every file the claim names and read the named
   construct in place (record/interface declarations, not prose about them), then searched the whole
   English set for the same name/shape in the other files — that second pass is what produced §3.
3. Mechanical sweeps used: `stage`/`Stage`/`STALE`/`stage()` per file; `MatchRequest` component
   lists; `MatchResult` arm lists; `release`; `OccupantRef`; `CacheKey`/`PlanId`/`SectionStamp`/
   `StepResult`/`BindingSite`/`CachedUnit`; `SC-*` FAMILY order in `03` vs `13`; the determinism
   tuples (`fully determines`, `Determinism`, `for a fixed`).
4. Took §I's residue by deciding each row's disposition against `20` §6.3/§4.3 and `11` §4.7 — no
   edit was made in `01` or `20` (round-2 protocol: the lead applies).

## 2. Verdict table

1. **VERIFIED** — `22`:563-574, `11`:62-81, `15`:124-135, `01`:186-194. All six arms declare
   `Stage` in every sketch (`Dropped` included), so `11`:62's abstract `Stage stage();` is total;
   `01`'s `Admitted`/`Formed`/`Failed`/`Undecided`/`Deferred`/`Dropped` rows list exactly the
   components the three sketches show (incl. `@Nullable ClaimPlan planned`, `long ledgerRevision`).
2. **VERIFIED** — `01`:179, `22`:461-463, `11`:24 / 48-50 / 324-325, `02`:70-71. Nine components in
   all five places: `snapshotInfo, blueprint, siting, lease, policy, budget, pins, record, owner`.
3. **NEIGHBOUR** — `00`:62, `02`:133-136, `11`:315-316, `20`:523-524, `13`:533 are all stage-free,
   but `01`:19's **Match** row still reads "One pure evaluation of `(blueprint, snapshot, siting,
   stage, policy)`" — the same determinism shape the three fixed files dropped, and a violation of
   §B.10 ("`Stage` appears in results only").
4. **VERIFIED** — `04`:66-68 (`permits … Admitted, Dropped, Formed, Failed, Undecided, Deferred`)
   and `04`:97-104 (all six cases, no `default`).
5. **VERIFIED** — `18`:95 (C-46 "parallel is opt-in"; no `AGGREGATE` precondition) and `18`:96
   (C-47 `CacheKey(plan, siting, unit, closureDigest)`; no `SectionCache`, no read envelope).
6. **VERIFIED** — `18`:82 names `changeCount`; `20`:24-25 ("the fourth is `changeCount`"), `20`:337
   ("the gate's fourth term is the watch's single `changeCount`") and `20`:372 (term 4) agree.
7. **VERIFIED** — `03`:122-124 lists `SPC, OCC, EXP, FOC, CLM, BPT, BAY, CHK, DOC, ENG, CTX, DIA,
   RMD, SPI, MC`, identical in name, order and count to `13`:296's fifteen.
8. **NEIGHBOUR** — `20`:199 and `20`:460-462 now carry the `SnapshotId` on the ref, but `22`:121
   still declares `OccupantRef(instance, type, kind, anchor, footprint)`: no `SnapshotId`, plus a
   `footprint` component no other site has (`01`:70, `06`:23/40, `16`:409, `19` R8), and `08`:159
   cites that row for its own identity argument.
9. **VERIFIED** — `15`:221 (`check` reads "the frozen claim view the host made at dispatch
   (`Host.ledger()`)"), `15`:249-251 ("never a live ledger … `02` §5, `20` §2") and `15`:432 (P2).
   No file in the English set still says the request carries the view.
10. **VERIFIED** — `02`:70-90's flow branch (`check ──► Admitted(planned, ledgerRevision) | settle
    ──► Formed`, "two entry points, one writer") and `02`:105's `SETTLE` row ("only `settle` writes
    the claims and produces `Formed`").
11. **VERIFIED** — `19`:21/23/25 carry "**Superseded by R27/R28/R31.**"; R27 (`19`:78) says "no
    `MatchRequest` carries a stage"; `23`:120-124 names a caller in all three lifecycle rows
    (`dropInstance`, `LevelEvent.Unload`, the move handler); `23`:130's static layer cites
    `SC-BPT-0002`.
12. **VERIFIED** — `04`:157 (`AnchorRoles.FRONT`), `04`:158-161 (`OrientationPolicy.builder(...)`,
    no fallback heading; no `fallbackHeading` anywhere in the English set) and `04`:170-193's
    six-arm switch in the golden example itself (`04`:97-104 is the same family in §5).
13. **FAILED** — `08`:280-287: the three arms are built with `Stage.SETTLE` and the prose (`08`:196-200
    "a second movement is `Undecided(LEDGER_MOVED)` with a retry token", `08`:327-328 "carries its
    retry token on `Undecided.retry`") landed, but the sketch's token argument is `m.retry()` while
    `m` is `ClaimVerdict.RevisionMoved(expected, actual)` (`08`:58), a record with no `retry()`
    member — the sketch cannot compile and the token has no declared source.
14. **VERIFIED** — `02`:17-27 (twelve packages: no `plan`) and `02`:33-53 (graph: no `plan` node);
    `03`:41's `kernel.blueprint` row depends on `base, space, expectation, claim` only.
15. **VERIFIED** — `22`:269-275's `Computation` (no `stage()`), `22`:176-181's `Expectation<T>`
    (`subject()`, `reads()`), `22`:585's nine-component `Finding`, `22`:123's `ReadStatus`, and
    `22`:787's D3 ("its stage was removed by `23` §7") against `22`:215's stage-free `Check`.

## 3. Neighbour damage (ranked)

**N1 — the `release` carrier never reached `16`/`20` (High).** `01`:118 and `08`:67-70 now declare
`ClaimVerdict release(OwnerId, Siting)` as part of `ClaimLedger`, and `23` §5 (`23`:120-124) names
three call sites — but `16` §3's `SavedClaims implements ClaimLedger` (`16`:97-102) still shows only
`revision/settle/read/save`, and `release` occurs nowhere else in `16` outside the lease sentence at
`16`:164; `20` §3.2's `LevelEvent.Unload` (`20`:130) and `dropInstance` (`20`:132-134) release
snapshots and the record and never claim rows; `20` §5.1's hook row (`20`:292) is labelled "claim
settle/release" but only `SavedClaims.settle` fires it. Round 1 (front B D2, front A rank 2) asked
for exactly these sites and the `01`/`08` half is what landed, so the adapter's implementation of
the interface does not implement it (whether the read-only `FrozenLedger`, `01`:314, should refuse
`release` is the separate question round 1 left open).

**N2 — the two `OccupantRef` shapes (High).** See claim 8: fixing `20` (`20`:199, :460-462) left
`22`:121's `(instance, type, kind, anchor, footprint)` — and `08`:159's citation of it — disagreeing
with `01`, `06`, `16`, `20` and `19` R8. R8 is the authority ("`OccupantRef` includes its
`SnapshotId`"), and the `footprint` belongs to `OccupantSnapshot` (`06`:62), not to the ref.

**N3 — `01`:19's `Match` row (Medium).** See claim 3. The three files round 1 purged
(`00`/`02`/`20`) now disagree with the glossary's own definition of a match.

**N4 — `Label` has a second shape in `22` §11.1 (Medium).** `22`:585-586 writes
"`Label(code, message, severity)` on a `Locus`", while `01`:239 and `13`:97 declare
`Label(Locus locus, String message, LabelRole role)`, and `13` §4.1 explicitly refuses a per-label
severity ("a label has no severity; severity is the finding's"). The claim-15 row it sits in
(`Finding`'s nine components) is correct; the clause beside it is not.

**N5 — `02`:76 still ties the write to the stage (Low).** The flow line reads "`SETTLE` ledger
conflicts, seams, legality — the stage `settle` writes in", which is the `stage=SETTLE` trigger
`23` §7 (`23`:150) replaced with the entry point; the same diagram's branch at `02`:78-80 states the
correct rule, so the file contradicts itself in four lines.

**N6 — `16` gives `OrientationPolicy` a fallback nothing declares (Low).** `16`:243: "`null` lets
`OrientationPolicy.select` use its fallback" (`zh/16`:212 mirrors it), while the policy is
`(NamespacedId primaryRole, List<Orientation> allowed, boolean uprightOnly)` (`09`:104, `01`:138)
and `09`:271-273 has `select` return `null` when no candidate is admissible. Either `16` is prose
shorthand for that `null`, or the policy needs the field — the round-1 removal of the golden
example's fallback heading (`04`:158-161) left this behind.

## 4. Residue decision (§I — the `01` §E cache cluster)

No edit made; the dispositions below are the decision the lead applies. Oracle: `20` §6.3
(`20`:432-462), `20` §4.1/§4.3, `11`:294-301, `22`:508-511.

1. **`SectionStamp` (`01`:211) — delete the row.** The current cache has no stamp: validity is
   `closureDigest` equality with "no `invalidate` operation and no epoch comparison"
   (`20`:458-459, `11`:300-301), and the only surviving content epoch is
   `SectionSnapshot.captureRevision`, a field of the snapshot (`20`:164-167, `20`:206; the optional
   store is keyed `(SectionKey, contentDigest)`, `22`:137). Nothing in `20` §6.3 or `11` §4.7 reads
   a stamp. **One coupling:** `20`:269's sentence "`SectionStamp(content)` is the cache stamp;
   `content` is the section content epoch" reintroduces the deleted name and must go with the row
   (or be restated as `SectionSnapshot.captureRevision`), otherwise `01` is clean and `20` is not.
2. **`StepResult` (`01`:212) — delete the row.** Its replacement is already rowed: `01`:214
   `CachedUnit` (`CachedPartial`/`CachedOutcome`) with `01`:215 `AttemptCache`, keyed by `CacheKey`.
   Every component of the old row is from the retired per-step cache: `stepIndex` (units are ordered
   `(phase, declaration)`, `22`:367-369), `Verdict` (a check yields a `Reply`, `01`:96),
   `BindingSite` (item 3), `cacheable` (cacheability is `(unit, closureDigest)`, `18`:96, `19` R25),
   `chargedCost` (billing is `Spend(cells, sections)` charged on dispatch cached or not,
   `20`:456-458, `22` §9.5).
3. **`BindingSite` (`01`:213) — delete the row.** Its job is done inside the ref: a cached `MATCH`
   keeps its `OccupantRef`s, "each ref carries the `SnapshotId` it was minted under …; on reuse the
   refs are re-stamped to the new capture's id" (`20`:460-462; `19` R8). No other English file names
   `BindingSite`.
4. **`CacheKey` (`01`:210) — re-shape, same cluster, not named in §I.** The row is `(world, PlanId
   plan, Stage stage, Siting, ChunkGrid, ChunkKey, long contentEpoch)`; the type is
   `CacheKey(PlanId plan, Siting siting, UnitId unit, long closureDigest)` (`11`:294, `22`:508,
   `20`:440). As it stands `01` is the only place that gives the key a `Stage` (an output, §B.10)
   and a per-section epoch, and it is the row a reader reaches from `AttemptCache` one line below.
5. **`PlanId` (`01`:208) — re-shape.** `20`:435 makes it "one interned `long` allocated at
   `Blueprint` build; a datapack rebuild gets a new id and a cold cache"; `01` still says "blueprint
   identity, stage and plan digest", a stage-bearing identity no file declares any more.
   The re-shape in 4 also pulls in `UnitId`, which `11`:294 and `22`:508 use and no `01` row
   declares — the same pass should row it (or reuse an existing id type).

Summary: 3 rows deleted (`SectionStamp`, `StepResult`, `BindingSite`), 2 re-shaped (`CacheKey`,
`PlanId`), plus one sentence in `20` §4.3. `AttemptCache`/`CachedUnit` (`01`:214-215) were left
untouched as instructed; they match `20` §6.3.

## 5. Out of scope notes (recorded, not scored)

- `01`:149's `Computation` row still lists `stage()` and `22`:367 selects units with
  "`stage() == S`", while `22`:269's `Computation` and `22`:215's `Check` declare none. `22` §16.1
  item 16 (`22`:890) records "how a computation declares where it runs" as open, so this is the
  recorded question, not a defect.
- `18`:62 (C-13) still requires `ANCHOR`/`CENSUS`/`CLAIM` "carriers through `MatchRequest`/
  `MatchPolicy`", but R31 (`19`:82) removed `Place`/stage from the request and R34 (`19`:85) fixed
  `MatchPolicy` at three components; the test as written has no channel to use. Predates round 1
  (no front flagged it), so it is a new finding rather than neighbour damage.
- `11`:137's `settle` body calls `facade.settle(new SettleRequest(...))`, which is the ledger's
  method, not the facade's `settle(request, plan, live)` (`11`:52, `15`:105); `11`:124 uses a
  `ledger`/`ClaimView` that `11`:374's dependency list does not name. Sketch-level, unnamed by any
  claim.
- `§H`'s mirror deltas and `§K`'s audit are front C's; nothing in this report bears on `zh/**`.
- `22` §16.1 items 7–17 stay open (plan authentication, what `check` may publish, the gate for a
  separated `settle`, `release`+`settle` atomicity, claim-row dimension identity, the `compute`
  phase split, the `Footprint` frame, `DecisionKind` vs `Coverage`, the `P5` hole, the `Stage`
  carrier, `ONE_SUBJECT` + `Box`); none of the verdicts above decides one.
