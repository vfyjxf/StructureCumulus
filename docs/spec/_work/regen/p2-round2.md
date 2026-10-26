# P2 round 2 — blind regeneration vs `kernel.{claim,engine}`

Probe: write `kernel.claim` + `kernel.engine` from `08-claims.md`, `11-engine.md`,
`20-execution-and-sections.md`, `23-claim-entry.md` alone, compile it, then compare with
`src/kernel/java/dev/vfyjxf/cumulus/kernel/{claim,engine}`. Skeleton in
[p2-round2-skeleton.md](p2-round2-skeleton.md).

Nothing outside those four chapters was opened before the comparison: no other spec file, no
`src/`, no `19`/`22`. `19-open-proposals.md` and the code were opened only in phase 3.

## 0. Counts and compile result

| | Count |
| --- | --- |
| Top-level types, two packages | 42 (11 `claim`, 31 `engine`) |
| Nested types (sealed arms, `Derived`, `Verdict`, `Fold`) | 19 |
| **Type declarations in the two packages** | **61** |
| Of those, named by a chapter with **no member list at all** (I invented every member) | 13 — `UndecidedReason`, `MatchSession`, `Tally`, `CaptureGate`, `SeamBinding`, `AnchorCandidate`, `Fingerprint`, `ReadSet`, `ChangeSet`, `InstanceRef`, `InvalidationIndex`, `UnitId`, `StructureKernel` |
| Stub types (never described by any of the four) | 31, in one file |
| `// INVENTED:` markers | 21 |

Compile, from `/tmp/cumulus-probe-p2`:

```
javac --release 21 -Xlint:all -d out21 $(find src -name '*.java')
exit=0          # no errors, no warnings; 61 class files in the two packages
```

Two compile errors forced a decision before that line was reached, and both are findings:

1. `claim/ClaimPlan.java:8` and `claim/ClaimPlanner.java:12`: `找不到符号: 类 BayBinding`. `08` §3
   prints `ClaimPlan` and `ClaimPlanner` inside the claims API and hands `derive` a
   `Collection<BayBinding>`, while `11` §1 assigns `BayBinding` to the engine. I imported
   `kernel.engine.BayBinding` into `kernel.claim` — which makes `claim → engine` while `11` §3's
   `MatchResult.Admitted` makes `engine → claim`. javac accepts the package cycle; nothing in the
   four chapters says which direction is intended (finding S2).
2. `11` §3's literal arm `record Failed(Stage stage, List<Finding> findings) implements
   MatchResult` does not compile against its own interface. Verified in isolation:

```
T.java:9: 错误: Failed不是抽象的, 并且未覆盖MatchResult中的抽象方法findings()
    record Failed(Stage stage, List<Finding> findings) implements MatchResult {}
```

   I renamed the component to `errors` and added a throwing `findings()` accessor (finding W1).

## 1. SILENT — the chapter never says, I invented it

Ranked by how much the invention would mislead a regenerator.

**S1. Two of the four vocabulary types are named differently in code, and one has a third value.**
Anchor `08` §2 table rows 1–2, `08` §3 (`enum Sharing`, `sealed interface Footprint`); code
`claim/ClaimMode.java:3-6`, `claim/ClaimScope.java:7-8`, `blueprint/Bay.java:19`.
Docs say `Sharing{EXCLUSIVE, SHARED; blocks()}` and
`Footprint{Box, Bound, Declared, None}`. The kernel contains **no `Sharing` and no `Footprint`
at all** (`grep` returns nothing): it has `ClaimMode{EXCLUSIVE, SHARED, RESERVED; excludes()}` and
`ClaimScope{Owned, Occupied, Declared, Audit}`. Consequences a regenerator cannot avoid: every
`Bay` constructor call is wrong, `08` §4.3's four-row resolution table (`Box`=DESIGN, `Bound`=
SETTLE, `Declared`=DESIGN, `None`=never) maps onto different names, and a third mode voids `P1`
and `P2`, which both argue "the matrix is total over the `Sharing` pairs". The doc's `Coverage`
values (`EVERY_CELL`/`ONE_SUBJECT`/`ANY_SUBJECT`, `08` §4.3) are likewise `CUBIC`/`SUBJECT`/
`PRESENCE` in code, and `08` §4.3's `ONE_SUBJECT` refusal is keyed on a constant that does not
exist. **Decision: I wrote the document's names, verbatim.**

**S2. `ClaimPlan` and `ClaimPlanner` have no package, and putting them in `claim` creates a cycle.**
Anchor `08` §2, `08` §3 (both printed in the claims API), `11` §1 (`BayBinding` is engine's);
code `engine/ClaimPlan.java:10`, `engine/ClaimPlanner.java:45`, `engine/BayBinding.java:10`.
`ClaimPlanner.derive(..., Collection<BayBinding>)` needs `engine` from `claim`; `MatchResult.Admitted`
and `Formed` need `claim` from `engine`. Code breaks the tie by moving both claim types into
`engine`. **Decision: I kept both in `claim` (as `08` prints them) and imported
`engine.BayBinding`.** A regenerator who instead moves them, as the code does, silently changes the
`08` §2 ownership table — `ClaimPlanner` is listed as claim's row factory.

**S3. `MatchSettler` is a public engine collaborator that appears in no chapter.** Anchor `11`
§4.1 (the whole settle body is inline in `settle(request, admitted)`), `23` §3.1; code
`engine/MatchSettler.java:38-59`. The only mention anywhere is `23` §9.5, a migration bullet
("`MatchSettler`: split into validate and write"). The real class has `inline(ClaimLedger)`,
`on(ClaimLedger, Executor)`, and
`settle(MatchRequest, MatchOutcome, long contextRevision, List<ClaimRow>, InstanceRecord,
SnapshotLease)` — six arguments, an executor, and a `FutureTask` per ledger call. **Decision: I put
`settle(request, admitted)` on the kernel and invented no settler at all.** This is the single
biggest structural miss available in the probe: I cannot tell from the four chapters that settling
is a separate object, that it takes a context revision, or that `Executor` (a thread boundary the
kernel is supposed to have none of) is a constructor argument.

**S4. `StructureKernel`'s owner, package, construction and ledger injection.** Anchor `23` §2 and
`11` §3 (both print `check`/`settle` as free functions), `20` §3.1 and `23` §9.4 (name
`StructureKernel` once, in passing); code `kernel/spi/StructureKernel.java` — package `spi`, not
`engine`; `create(Catalogs)`, `create(Catalogs, @Nullable ClaimLedger)`, `catalogs()`, `ledger()`,
`check`, `derive(Formed, ContextStore, List<Deriver<?>>)`. **Decision: I invented a no-arg
`StructureKernel` in `engine` with `check`, `settle` and two `siting` overloads.** A regenerator
looking for the settle entry point will not find a class that owns it, and will not know a kernel
exists per catalog set.

**S5. Eight types are named as this unit's without a member, and none of them exist in code.**
Anchor `11` §1, §2, §4.6, §4.7, §5; code: no file for `ReadSet`, `ChangeSet`, `InvalidationIndex`,
`InstanceRef`, `Fingerprint`, `AnchorCandidate`, `SeamBinding`, `UnitId`. **Decision: I declared
all eight** (guessed records; `SeamBinding`/`AnchorCandidate` as empty interfaces) plus `Formed.
readSet()` and `11` §5's `index.record/instances/affectedBy`. A regenerator who follows §1's
ownership list writes eight types and a `readSet()` the kernel never grew; the concrete cost is that
`Formed` has no way to answer "what did this instance read", which is the whole basis of `R-09`
re-checking in `11` §5.

**S6. `MatchOutcome`'s shape.** Anchor `11` §3 ("ordered: bays by id, seams by id, claims by cell
then bay"), `11` §4.1 (`outcome.stepReads()`); code `engine/MatchOutcome.java:18` is
`(Stage stage, Siting siting, WorldView view, Map<ContextKey<?>, Object> context, Bindings
bindings, ReadScope reads)` plus `empty(...)` and `value(ContextKey<T>)`. No bays, no seams, no
claims, and the read accessor is `reads()`. **Decision: I wrote `(bays, seams, claims)` and a
`stepReads()`.** The context map and `Bindings` are invisible in the four chapters, and `Bindings`
belongs to a package none of them mentions.

**S7. `InMemoryClaimLedger` — a concrete ledger lives in the kernel package.** Anchor `08` §4.5
("`ClaimLedger` is the host's transaction. The adapter implements the CAS"), `08` §1 ("**Does not
own** … persistence (host, A1)"); code `claim/InMemoryClaimLedger.java:22-58`, two constructors,
an `AtomicReference<ClaimSet>`, a CAS loop and a conflict pre-scan, plus `ClaimSet.EMPTY`.
**Decision: I wrote no implementation.** `23` §9.1 ("`ClaimLedger`/`InMemoryClaimLedger`: add
`release`") shows the class is known to the docs team, but no chapter describes it, so a
regenerator writes an interface and stops — and every settle-semantics claim in `08` §4.5/P2 is
actually enforced by this undocumented class.

**S8. `Tally` and the fold.** Anchor `11` §4.1 (`tally.add(check, check.run(...))`, `tally.fold()`,
`Fold` with `failed/undecided/reason/findings/plan`), `11` §4.4 (verdict equations over counters no
type owns); code: no `Tally` — the fold is `Reply.combine(...)`, three `FindingBundle` fields and
three `int` counters private to `MatchSession`. **Decision: I declared `Tally` + `Tally.Fold` with
five guessed components.** A regenerator invents a type the code does not have; worse, it hides that
the fold's real input is a `Reply` algebra (`Match`/`Mismatch`/`Unreadable`/`Abstain`) that no
chapter names.

**S9. `BayBinding`'s third component.** Anchor `08` §4.3 (`subjects()`), `08` §4.5 ("its kind
matches the bay's expectation"); code `engine/BayBinding.java:10` is
`(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings)` — there is no kind, and there is
a `Bindings` that the four chapters never mention. **Decision: I wrote `(bay, kind, subjects)`.**
The `SC-CLM-0005` "kind does not match" check in `08` §4.5 therefore has no carrier in the code, and
a regenerator can neither implement nor omit it knowingly.

**S10. `ClaimRow implements Comparable`, `ClaimSet.EMPTY`, `ClaimSet.rowsIn`.**
Anchor `08` §4.1 names only `ClaimRow.ordered(List)`; `08` §3 gives `ClaimSet(rows, revision)`.
Code adds `compareTo` on the row (cell/owner/bay), `ClaimSet.EMPTY`, `ClaimSet.rowsIn(Cuboid)`.
**Decision: none of the three.** Low harm, but note that `ClaimSet.rowsIn` duplicates
`ClaimView.rowsIn`, which is the one the doc declares.

**S11. `DirtyIndex` sits in `kernel.engine` while every chapter puts it host-side.** Anchor `11` §1
(the engine "writes nothing … schedules nothing, reads no clock"; the not-owned list is silent on
dirty state), `20` §5.2 (`DirtyIndex`/`WatchedInstance` declared as the host's tables); code
`engine/DirtyIndex.java` (127 lines, in the engine package). **Decision: I did not write it** — it
is neither owned nor excluded by `11` §1.

**S12. `CaptureGate` is used before it is introduced, and its verdict type has no name.** Anchor
`11` §4.1, §4.6, `20` §5.4; code `engine/CaptureGate.java` with a nested `public enum Verdict`.
**Decision: I invented `CaptureGate.Verdict{CURRENT, CHANGED, SUPERSEDED}` and a static
`evaluate(SnapshotLease)`.** This is the probe's control case: `switch (CaptureGate.evaluate(...))`
with bare constants forces the three names, and the invention matched the code exactly. Worth
recording because it shows the other gaps are not "I guessed badly" — they are genuinely absent.

## 2. CONTRADICTION — two statements that cannot both hold

**C1. `MatchOutcome` carries claims, and a row cannot exist before `Settled`.**
Anchor `11` §3 ("`MatchOutcome` is ordered: … claims by cell then bay") vs `08` §1/§4.5 ("no row
exists before `Settled`", P4), `20` §6.4 ("never rows"), `08` §2 (`ClaimPlan` "**no rows**, because
every row is derived"). Code agrees with `08`; I declared `(bays, seams, claims)` from `11` and then
had to decide whether `Formed.claims()` and `outcome.claims()` are the same object. **Decision:
duplicated the field in both.** A regenerator reading `11` alone builds a match outcome that mints
rows, which is exactly invariant I-16.

**C2. There is a second conflict predicate, at a different stage, with the other code number.**
Anchor `08` §4.2 ("`ClaimMatrix.conflicts(a, b)` is the only conflict function … the adapter has no
predicate of its own"; "an `EXCLUSIVE` pair of one blueprint is that same `SC-BPT-0002`, never a
ledger conflict, because `ClaimMatrix.conflicts` is false for two rows of one `(owner, siting)`")
vs `08` §4.6 (`SC-CLM-0001` = "two `EXCLUSIVE` claims of **different owners**", SETTLE) vs code
`claim/ClaimMatrix.java:85-93` (`internalConflict`, which *requires* one owner+siting and different
bays) and `claim/InMemoryClaimLedger.java:38-42`, which refuses a `settle` with
`OVERLAP_CODE` = `SC-CLM-0001` before reading the revision. **Decision: I wrote one 2-arg
`conflicts` and no internal-overlap path at all** (§4.2's argument is explicit that a self-pair is
false, so a settle can never see one). The divergence is not cosmetic: it moves a DESIGN-time
blueprint error into SETTLE and gives it the ledger-conflict code, so `P2` ("no silent overwrite")
and `P5` (one predicate) no longer describe the code.

**C3. `release` either bumps the revision or does not.** Anchor `08` §4.5 ("deletes every row of
that key in one transaction **and bumps the revision**") vs `23` §5 ("an absent key is a no-op (no
error, **no revision bump**)"). `23` is the later decision record and does not cite `08`, and `08`
was not updated. **Decision: declared the signature only; no way to choose.** This one matters
because `RevisionMoved` is defined as "the store moved", so a release that bumps wakes every
in-flight settle, and one that does not lets a settle target a revision whose rows are gone.

**C4. The engine assigns the stage, but the request has no stage and nothing else can yield one.**
Anchor `11` §3 (the 9-component `MatchRequest`; §4.1's comment "the engine's pipeline assigns it;
**no field names one**") vs `11` §4.1's first line, `Stage stage = stageOf(request)`, feeding
`blueprint.expand(stage)`. Neither `policy`, `budget`, `pins`, `record` nor `ticket` is a function of
the stage. Code resolves it by putting `stage` in the record (3rd component) and keeping
`Stage.STALE` (`base/Stage.java:8`), which `23` §7 deletes. **Decision: I could not implement
`stageOf`; the class body throws, and `Stage` never becomes an input.** A regenerator either ignores
`expand`'s parameter (silently wrong) or adds the field the doc forbids.

**C5. The algorithms call `request.world()`, and the request has no world.** Anchor `11` §4.1
(twice: `ClaimPlanner.derive(request.world(), …)` and the `SettleRequest` construction), `08` §4.5's
sketch (`request.world()`), vs `11` §3's component list. `08` §3 does give `SettleRequest(World
world, …)`, so the world exists at that boundary but not at the request's. Code adds a derived
accessor `MatchRequest.world()` returning `lease.id().world()` — of type **`NamespacedId`**, not a
`World`. **Decision: I invented a `World` stub and took it from `request.world()`**, i.e. I invented
both the member and the source of the world. (`08` §4.1's "the settlement key is read off the rows"
requires the world to travel on the row; see E1.)

**C6. Three incompatible homes for the spend counter.** Anchor `11` §3 (`InstanceBudget`:
`of`, `unbounded`, `limit()`, `spend()`; `MatchSession`: `spend()`, `remaining()`), `11` §4.6
(`InstanceBudget`: `discards(unit)`, `attempts(unit)`, `spent()`, `tryCharge(int,int)`), `11` §4.1
(`slot.tryCharge(...)` — the *slot*, a type the unit does not own), `20` §7 ("`InstanceBudget`
belongs to the slot"). Code: `limit()`, `spend()`, `tryCharge(int,int)`, `tryCharge(Spend)`,
`remaining()`, all `synchronized`; **none** of §4.6's four members exists. **Decision: I declared
both sets on `InstanceBudget` and guessed `Spend remaining()` on the session.** The consequence is
that §4.6's "after `maxDiscards` (default 3) the attempt settles `Undecided(DIRTY_UNSTABLE)`" has no
counter to fire on — `discards(unit)` exists in neither the docs' declared API nor the code.

**C7. `siting` has two arities.** Anchor `11` §5 `kernel.siting(furnace, LatticePoint.of(...),
lease)` (three arguments) vs `11` §4.2 `facade.siting(bp, origin)` and `20` §3.1 "the facade's
`siting(blueprint, origin)`" (two). **Decision: I declared both overloads.** No code implements
either (E5/S4), so this is unresolved in all three places.

**C8. `MatchSession`'s public surface.** Anchor `11` §3 ("`MatchSession`: idempotent `run()`,
`spend()`, `remaining()`, `readSet()`, `probe()`; writes are internal"), `11` §4.8 (internal,
one-shot, no `advance`) vs code `engine/MatchSession.java:120-146`: one public constructor
`(MatchRequest, @Nullable MatchSettler)`, one package-private three-arg constructor taking a
`ContextStore`, and exactly one public method `run()`. `remaining()` is on `InstanceBudget`,
`readSet()` is on `Formed` (which the code does not have), `probe()` exists nowhere. **Decision: I
declared all five with guessed return types** (`MatchResult run()`, `Spend spend()`, `Spend
remaining()`, `ReadSet readSet()`, `ProbeSession probe()`).

**C9. `UndecidedReason`'s constants do not match the ones the chapters require.** Anchor `11` §4.4's
precedence line (CANCELLED, DIRTY_UNSTABLE, BUDGET_EXHAUSTED, LEDGER_MOVED, LEDGER_UNAVAILABLE,
UNREADABLE, ABSTAINED, NO_STEPS) plus `11` §4.1 (`NO_JUDGING_UNIT`) and `20` §3.3 (`WORKER_FAILED`);
`08` §4.7 *mandates* `Undecided(LEDGER_UNAVAILABLE)` and `11` §4.2 `Undecided(UNREADABLE)`. Code
`engine/UndecidedReason.java:4-12` has seven: `BUDGET_EXHAUSTED, DIRTY_UNSTABLE, LEDGER_MOVED,
SUBJECT_ABSENT, LEASE_CLOSED, WORKER_FAILED, DESIGN_DEFECT`. Six doc-mandated reasons are absent, so
the two normative outcomes above **cannot be constructed**; four code-only reasons appear in no
chapter, so a regenerator cannot know when `SUBJECT_ABSENT`/`LEASE_CLOSED`/`DESIGN_DEFECT` are
expected. **Decision: I declared the ten doc-named constants and ordered them as §4.4's precedence
line states** (`NO_JUDGING_UNIT`/`WORKER_FAILED` placement is my own).

## 3. WRONG-DOC — would not compile as written

**W1. `MatchResult`'s accessor and four of its arms cannot coexist.** Anchor `11` §3, interface line
`FindingBundle findings();` vs `record Dropped(…, List<Finding> findings)`, `Failed(…, List<Finding>
findings)`, `Undecided(…, List<Finding> findings)`, `Deferred(…, List<Finding> findings)`. A record
component generates `findings()`; `List<Finding>` cannot override `FindingBundle`. **Compile error
reproduced** (section 0). No rename inside the doc can fix it, because `Admitted` and `Formed` *do*
carry `FindingBundle findings` — so the interface's accessor and the four arms' component are two
different things that were written as one word. Code resolves it by renaming the accessor to
`bundle()` and dropping `stage()` from the interface entirely (only `Formed` has `stage()`, derived
from `outcome.stage()`; `Dropped`/`Failed`/`Undecided`/`Deferred` carry no stage at all, which
`23` §7 requires them to carry — "`Stage` in results: kept, filled by the engine"). **Decision: I
renamed the four components to `errors` and gave each a throwing `findings()`** — which means my
skeleton *cannot* be handed to `FindingBundle`-consuming code, and the doc gives no way to build one
from the four lists.

**W2. `read` takes a `Cuboid` and every call site passes a `CuboidSet`.** Anchor `08` §3
`ClaimView read(Cuboid box);` and `10`-line `Derived(... CuboidSet staticFootprint)` vs `08` §5
`ClaimView before = ledger.read(d.staticFootprint());` (same chapter, 40 lines later). Won't
compile. Code has `read(Cuboid)` only (`claim/ClaimLedger.java:12`) and no `Derived`. **Decision: I
kept `read(Cuboid)` and could not write §5's settle sketch** — the one path the whole chapter exists
to describe.

**W3. `RetryToken.of(long, long)` contradicts `RetryToken(Ticket, Stage)`.** Anchor `08` §5
`RetryToken.of(m.expected(), m.actual())` vs `11` §3 `record RetryToken(Ticket ticket, Stage stage)`
with `of(Ticket, Stage)`. Code has the `11` form only (`engine/RetryToken.java:14`), so `08` §5's
line is simply wrong. **Decision: I declared both static overloads** — i.e. my skeleton has an API
member the code does not, and a regenerator who follows `08` will write a factory the ledger cannot
satisfy (a token has to name a stage and a slot ticket, not two revisions).

**W4. The list form of the conflict predicate exists only at its call site.** Anchor `11` §4.1
`ClaimMatrix.conflicts(d.rows(), held.rows())` vs `08` §3 `conflicts(ClaimRow a, ClaimRow b)`. Won't
compile. Code has neither the list form nor a list name: it has
`conflictsWith(ClaimRow, Collection<ClaimRow>)` (`ClaimMatrix.java:47`). **Decision: I added a
`conflicts(List, List)` overload marked INVENTED.** This one has a silent semantic rider: the doc
never says whether the list form means "any pair conflicts" or "every row of `a` is blocked".

## 4. Excluded — documented lag and open questions I hit

Not counted as findings, per the brief; listed so the reader knows what was set aside.

- **R35** (`19` §4th pass): row carries a `World`, `ClaimLedger.world()`, `SettleRequest` carries
  the world, `OwnerId` loses its world. Code: `ClaimRow` has no world, no `ClaimLedger.world()`, no
  world in `SettleRequest`, and `base/OwnerId.java:3` still carries `world`. Hit on every row
  literal and on `MatchMatrix`'s world comparison (`08` §4.2).
- **R36** (`19` §4th pass): plan carries no rows; `ClaimPlan.Matched(long, List<BayBinding>)`;
  `ClaimPlanner.derive` is the only factory; `SC-CLM-0005`. Code: `ClaimPlan.Rows(List<ClaimRow>)`
  and `ClaimPlanner.plan(Blueprint, Siting, Collection<BayBinding>, OwnerId)` returning the plan
  (`engine/ClaimPlan.java:12`, `engine/ClaimPlanner.java:45`). This is where my `Matched`/`derive`/
  `Derived` came from, and it is the reason half of my claim package has no code counterpart.
- **R38**: `SectionSlot` / per-chunk facades — hit through `20` §4.1 and §6.1 (`slot(SectionKey)`
  answers every read). No effect on these two packages.
- **`PrescreenIndex`/`Census` withdrawal**: `11` §1, §2, §4.1 (`PrescreenIndex.of(view, plan,
  siting)`), §4.3 and §8 all still name them while §8 says they are "carried unchanged". I declared
  neither, so my count is two types short of `11` §1's ownership list.
- **`23` §9's migration items the code has not taken**, which I would otherwise have filed as
  contradictions: no `settle(MatchRequest, Admitted)`; `Admitted` carries neither `planned` nor
  `ledgerRevision` (`MatchResult.java:30`); the settle still runs inline keyed on `stage == SETTLE`
  (`MatchSession.java:354-378`, inside `admit()`); `ClaimLedger.release` absent (`claim/ClaimLedger.java:5-13`);
  `MatchRequest.stage` and `Stage.STALE` not removed; and **`23` §3.2's named bug is still in the
  code** — the doc says "a retry re-reads; it never just swaps the expected revision. Today's
  `MatchSettler` only swaps the number (`MatchSettler.java:80-82`)", and
  `engine/MatchSettler.java:80-81` still calls `attempt(..., moved.actual(), retriesLeft - 1)` (the doc's own citation, `:80-82`, still lands on it)
  without re-reading or re-running the matrix. That last one deserves a ruling sooner rather than
  later: `08` §4.5's "re-reads and re-checks **once** inside the transaction" is the only thing
  standing between a moved revision and a stale-but-conflicting write.
- **Open questions I hit and left open**: `08` §8 (1) empty-volume protection, (2) retry token vs
  hard `Undecided` (this one decides `RetryToken`'s shape, so it blocked W3), (3) `ANY_SUBJECT`
  + `EXCLUSIVE`; `11` §8 Q-1 (same as (2)); `20` §11 Q-1 (window-diff cost); `23` §8's five items —
  of those, `SC-CLM-0003` and `SC-CLM-0104` have **no producer** in my skeleton (and none in the
  code: `grep` finds no `CLM, 3` and no `CLM, 104`), and the doc says so, so it is not a finding —
  but it is why `08` §4.6's code table has two rows a regenerator cannot wire to anything.

## 5. What these four chapters cannot tell you about engine state

Concrete omissions, i.e. state that exists in `kernel.engine` and is derivable from no sentence in
`08`/`11`/`20`/`23`.

1. **Who holds the attempt's accumulators.** `11` §3 says "the session accumulates spend and
   bindings" and `11` §4.1 shows a `Tally`, but neither type's fields are given. The code's
   `MatchSession` carries ten mutable fields (a `combined Reply`, three `FindingBundle`s, one
   `ReadScope`, `mismatched`/`unreadable`/`abstained` counters, a cached `result`, a `ContextDraft`,
   an `AttemptView`, a `Set<ContextKey<?>> absentKeys`, a `Map<NamespacedId, BayBinding>`), plus a
   `ContextStore` and a `ContextTier` derived from `Stage`. None of it is derivable; in particular
   nothing says an attempt owns a *draft* that can refuse a key, or that `Stage`→`ContextTier` is a
   five-value mapping (`MatchSession.java:513-527`) that must survive `Stage.STALE` for now.
2. **Per-instance counters.** `11` places the spend counter in three places (`11` §3
   `InstanceBudget.spend()`, `11` §4.1 `slot.tryCharge`, `11` §4.6 `discards(unit)/attempts(unit)`),
   and `20` §7 says the slot owns it. The code has exactly one mutable counter object
   (`InstanceBudget`, `synchronized`, with `remaining()` that no chapter names) and **no per-unit
   counters at all**. So `11` §4.6's `maxDiscards = 3` → `Undecided(DIRTY_UNSTABLE)` →
   `reemit(generation + 1)` policy has no home: `InstanceRecord` has only `generation`,
   `remember`, `reemit`, and no discard count. A regenerator cannot tell whether to build that
   counter, because §4.6 presents it as existing.
3. **Lease ownership across the settle boundary.** Four rules in three chapters (`11` §3: `Admitted`
   and `Formed` close only their own child handle; `11` §5 / `20` §8.7: the lease is a move with one
   holder at a time and the scheduler closes its copy in `finally`; `23` §2: `settle` closes the
   admitted result on every path; `20` §4.3: `InstanceRecord` is the only owner). The code has no
   `settle(request, admitted)` for the third rule to attach to, and instead: `admit()` calls
   `lease.retain(pins)` (`MatchSession.java:346-348`), a `PinBudget` refusal becomes
   `Undecided(BUDGET_EXHAUSTED)` *from the admit path* (nowhere in the docs), the child is passed
   into `MatchSettler.settle(...)`, stored inside the `Formed`, and `InstanceRecord.remember` keeps
   that `Formed` alive forever. Consequence that no chapter states: `reemit(...)` reuses
   `last.lease()` (`InstanceRecord.java:65`), so the re-emitted `Formed` and the previous one share a
   handle — closing one closes the view for both.
4. **The verdict algebra.** `11` §4.4 gives the verdict as equations over mismatch/unreadable/
   abstain/untested, but no type owns those counters and nothing names `Reply` or its four arms. The
   code's fold is `Reply.combine` over the per-check replies, republished as
   `MatchOutcome.context` (`Reply.combine` at `MatchSession.java:291`, `outcome()` at `:387`). A skeleton built from
   the doc produces three counters on a `Tally`; the code produces a merged `Reply` plus a published
   context map.
5. **Ledger state at rest.** The only shape the chapters give is "rows plus a revision" and "one CAS
   over `(world, owner, siting)`". The code's store is one immutable `ClaimSet` value behind an
   `AtomicReference`, replaced whole, seeded from `ClaimSet.EMPTY` at revision 0, with the conflict
   scan done twice (internal overlaps, then against stored rows) *before* the revision comparison.
   Nothing in the four chapters says the ledger is a single value, that revision 0 means empty, or in
   which order the three refusals are evaluated — and the order is observable, since a
   self-overlapping request gets `ClaimConflict` rather than `RevisionMoved`.
6. **Retry state.** `08` §4.5 and `11` §4.6 describe a retry token without its contents; the code's
   token is `(Ticket, Stage)` and `MatchRequest.retryToken()` fabricates one as `ticket.next()`
   (`MatchRequest.java:47-50`), so "a fresh token" is literally a new slot ticket, it is `null` for a
   direct facade call, and `Ticket.next()` throws on overflow. None of that is in any chapter.
7. **Session lifetime.** `11` §3/§4.8 say the session is internal and one-shot, but the code's
   `run()` is idempotent by caching its result, and a session is constructed per attempt *with a
   `ContextStore`* whose instance/attempt frames are then published into the store. A regenerator
   cannot tell whether the session owns the store, whether a second `run()` must re-check the
   capture (the doc says the gate runs once — the code caches to enforce that), or what happens to a
   draft that was never published.
