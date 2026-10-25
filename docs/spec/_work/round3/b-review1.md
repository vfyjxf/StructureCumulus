# Front B — entry points, runtime, facade: review round 1

Files read end to end: `00`, `02`, `03`, `04`, `08` (for the claim-lifecycle side of the entry
points), `11`, `15`, `16`, `18`, `20`, `22`, `23`, plus the third-pass table and R1–R26 of `19`,
`01`'s glossary rows for the names my front hinges on (`MatchRequest`, `MatchResult`,
`ClaimLedger`, `CacheKey`, `MatchCompletion`), and `13` §4.3 for the family order. Read-only: no
file in the design set was edited.

## 1. Method

1. Read `23` and `24` as the oracle, then the contract, then `_work/round3/{a,b,c,d}` and
   `22` §16.1 so that recorded-open items are excluded up front.
2. Mechanical pass over my front's files: `grep -n` for `check(`/`settle(`, `MatchRequest`,
   `MatchResult`, `Admitted`, `MatchCompletion`, `MatchOutcome`, `stage`/`Stage`/`STALE`,
   `release`, `nine operations`, the facade verb names (`match(`, `repair(`), and the code
   families; then read every hit in context rather than trusting the hit list.
3. Read each of my front's files end to end and checked the four named areas: entry-point
   signatures; `MatchResult` arms and `Admitted.planned()`/`ledgerRevision()`; the nine facade
   operations; the stage purge and everything that used to read a stage (gate terms, counters,
   lifecycle prose, determinism tuples).
4. Every claim below was re-checked against the file at the cited line before it was written;
   nothing is reported from the round-3 reports alone.

## 2. What I checked

- `check(MatchRequest, Supplier<SnapshotInfo>)` / `settle(MatchRequest, ClaimPlan,
  Supplier<SnapshotInfo>)` at `23` §2, `22` §9.1, `11` §3, `15` §3, `04` §8, `20` §3.1/§6.4/§10,
  `16` §4.6/§4.10, `18` C-11/C-28.
- The six arms and the two new `Admitted` members at `22` §10.4, `11` §3, `15` §3, `04` §3.2/§5/§8,
  `01` :184–:188, `19` R7/R11.
- The nine operations and the absence of a tenth, and the `solve`/`check` verb rulings, at
  `00` R-14, `01` :284, `02` §3/§7, `03` §5, `04` §5/§8, `15` §1/§2/§4.4, `19` R2/R31.
- The stage purge: `MatchRequest` components, `Check` components, `CheckPlan`/`expand`,
  `Stage.STALE`, the JSON `"stage"` key, the five-term gate and its counters, the determinism
  tuples, and the host-side selector (`20` §3).
- `MatchPolicy` at `11` §3, `22` §9.1, `20` §10 (three components, no `FailureMode`/`StopPolicy`/
  `deriveOnForm`).
- `MatchCompletion` vs `MatchOutcome` at `01` :311, `20` §3, `19` R29, and `MatchOutcome`'s use as
  the attempt product in `11`, `12`, `14`, `22`.
- The settlement/lifecycle prose at `08` §4.4–§4.6, `16` §4.6/§4.10, `20` §3.1–§3.2/§5.1/§5.3/§6.4.
- `22` §10.3/§10.4 against `23` §2–§6 line by line.

## 3. Ranked defects

### D1 — `08` still settles inside the `check` call (High)

Anchors: `08:169-176` (§4.4: "the match produces a `SettleRequest` on the matcher worker and
settles nothing itself. `SETTLE` performs the request on the server thread (§4.5) and only then may
a `Formed` exist"), `08:181-190` (§4.5 title "Settlement inside the facade call"; "The facade
`check()` is one call: worker match → server-thread settler → result. … Settlement is a step of the
facade call").

Breaks: `23` §2 ("`settle` is invoked only by a caller that explicitly wants registration"), `23`
§3.1 (check → plan → *caller decides* → settle), `23` §6 ("outside the blueprint (advisory) | `check`
… **a prediction**"), R27, I-10, `22` §10.3, `15` §4.4, `20` §3.1, `04` §8's comment "The probe
matched and wrote nothing".

Why it fails: the sweep updated `16`/`20`/`22`/`11`/`15` to two entry points but left U4 — the unit
that owns the ledger and the settlement story — on the deleted single-call model, so the one file a
`ClaimLedger` implementor reads still says a read-only `check` settles. It also keeps the plan type
stale: the matcher's output is `ClaimPlan` (R28, `23` §4, `11` §4.5 "no `ClaimPlan` leaves the
attempt … the planned claim"), not `SettleRequest`.

Minimal fix: rewrite §4.4/§4.5 to the two entry points — `check` returns
`Admitted(planned, ledgerRevision)` and writes nothing; the match produces a `ClaimPlan`; `settle`
is a separate server-thread call that re-checks snapshot + revision and is the only source of
`Formed`; §4.6's table keeps its rows, whose codes already match `13`.

### D2 — `23` §5's `release` has no carrier in any unit file (High)

Anchors: `23:105-125` (`ClaimLedger.release(OwnerId, Siting)`, "Three lifecycle points must call
it": `dropInstance`, `LevelEvent.Unload`, before `settle` on a move) versus `01:119`, `08:24`,
`08:67-72` (the `ClaimLedger` interface: `revision`, `settle`, `read` only), `15:105-108`,
`16:97-102` (`SavedClaims implements ClaimLedger` with `revision`/`settle`/`read`/`save`), `20:130-135`
(§3.2 lifecycle: unload cancels/epochs/releases *snapshots*; `dropInstance` = "cancel → dirty unwatch
→ close the instance's own lease reference → release the record" — no ledger row), `20:292` (§5.1's
hook row is labelled "claim settle/release" but only `SavedClaims.settle` fires it), `19` R9
(`:23`, still "`settle(SettleRequest)` and `read(Cuboid)`"). `_index.md:36` already advertises
"the release path" as part of `23`.

Breaks: `23` §5/§9.1 (the decision's own API and call sites), I-10's ledger story, `23` §1's
premise ("a ledger that can only be written, never released, hardens a mistake permanently").

Why it fails: the sweep carried `check`/`settle`, `Admitted.planned`/`ledgerRevision` and the code
allocations into the unit files but not the third part of the same decision, so no unit file
declares or calls `release`. The only other `release` in the set is the snapshot lease's
(`11` §3, `20` §4.1) — a different concept, which makes a reader's grep actively misleading.

Minimal fix: add `ClaimVerdict release(OwnerId owner, Siting siting)` to the `ClaimLedger` blocks in
`01`/`08`/`15`, to `16` §3's `SavedClaims`, and the three call sites to `20` §3.2 (unload, and
`dropInstance`) and `16` §4.6 (before the move's `settle`); update `19` R9. (`22` §16.1 item 10
defers the move's `release`+`settle` *atomicity*, not the method's existence.)

### D3 — the determinism tuple still names `stage` in `00`, `02` and `20` (High)

Anchors: `00:62` (I-4 "`(blueprint, snapshot, siting, stage, policy)` fully determines the
result"), `02:132` ("for fixed `(blueprint, snapshot, place, stage, policy, catalog snapshot,
frozen ledger, cancellations)`"), `20:520` (§8 proof 9 "a function of the snapshot, blueprint,
siting, stage, policy, frozen claim view and cancellation") versus `11:314-315`'s tuple, which the
sweep changed to `(blueprint, snapshotId, siting, policy, catalog, frozen ledger revision,
cancellations)` (sweep contract §H: "the determinism tuple no longer names `stage`").

Breaks: I-4 as the set now states it, `23` §7 ("a stage is no longer declared by an author or chosen
by a caller"), I-6's "no caller chooses one", and `22` §8.1's own sentence at `:404` ("`Stage` is
filled by the engine … no author declares one and no caller chooses one").

Why it fails: once `Stage` is an engine-filled output, listing it as an input makes the tuple
circular in `00`/`02`/`20` and disagrees with the file the sweep actually fixed; the exclusion list
does not cover this (`22` §16.1 item 16 defers only *what replaces* `Stage` in I-6/R-08, i.e. the
carrier, not whether it is an input).

Minimal fix: drop `stage` from the three tuples (and `place`→`siting` in `02:132`), or state there
that `Stage` is a derived, not an input, component.

### D4 — `04` §3.2/§5 still sketch a three-arm `MatchResult` (High)

Anchors: `04:66` ("`sealed interface MatchResult permits MatchResult.Formed, MatchResult.Failed,
MatchResult.Undecided {}`") and `04:96-100` (the golden-path switch with those three cases) versus
`22` §10.4 `:560-572`, `11:58-81`, `15:124-135`, `01:184`, `19` R27 (six arms).

Breaks: R27 (`MatchResult` has six arms including `Dropped`), `22` §10.4 `:560-572`, `16` §4.10 and
`18` C-11 (an attempt can be `Dropped`), and `04`'s own rules — §3.2's "a new case is a deliberate
change", §10's "A `default` branch over a sealed result" anti-pattern and `04:273` (§11 item 4,
"an exhaustive switch over every sealed type it handles").

Why it fails: the sweep replaced `04` §8's golden example with the two entry points but never
touched the type sketch the same file teaches from, so `04` §5's switch no longer compiles against
the six-arm sealed type and a caller following the doctrine would drop `Dropped`/`Admitted`/
`Deferred` handling.

Minimal fix: extend `04:66`'s `permits` list to the six arms and add the three missing cases to
§5's switch (or replace it with §8's switch).

### D5 — `Stage` on the arms: `01` :184 versus every sketch (Medium-high)

Anchors: `01:184` ("Sealed: `Admitted`, `Dropped`, `Formed`, `Failed`, `Undecided`, `Deferred`;
every case but `Dropped` carries the `Stage` it was run in") and `11:61` (the sealed interface's
abstract `Stage stage(); FindingBundle findings();`) versus `11:63-81`, `22:559-574` and
`15:124-136`, where only `Admitted` has a `Stage` component and `Dropped`/`Formed`/`Failed`
(`Undecided`/`Deferred` too) provide none.

Breaks: `11` §3's own contract (a record implementing the interface must supply `stage()`), R7
("`MatchResult` carries `Stage` on every case"), `01` :184, and `03` §8's rule that a sketch
signature is normative.

Why it fails: the sweep added `planned`/`ledgerRevision` to `Admitted` in all three sketches but
never reconciled the stage component across the arms, so the interface cannot be implemented and
`01`'s row contradicts the sketches it points at.

Minimal fix: give every non-`Dropped` arm a `Stage stage` component in `11` §3, `22` §10.4 and
`15` §3 — or drop `01:184`'s "every case but `Dropped`" and the interface method if the engine
sinks the stage into `FindingBundle` instead.

### D6 — three incompatible `MatchRequest` shapes (Medium-high)

Anchors: the canonical 9-component record at `01:177`, `22:458-459`, `11:47-49` versus
`11:24` (vocabulary: "`MatchRequest` (ticket, blueprint, siting, lease, policy, budget)" — six
names, `ticket` named instead of `SnapshotInfo`, no `pins`/`record`/`owner`), `11:323-325`
(`new MatchRequest(info(ticket, lease), FURNACE, siting, lease, MatchPolicy.defaults(),
slot.budget())` — six positional arguments), and `02:71` (`MatchRequest(snapshotInfo, blueprint,
siting, lease, policy)` — five).

Breaks: `03` §8 ("a signature shown in a sketch is normative; prose must not contradict it") and
`04:273` (§11 item 4); `19` R31 ("`MatchRequest` carries neither `Place` nor a stage") read together with `01:177`'s
"built with a fluent builder"; `20` §10's summary of the same type.

Why it fails: the sweep removed `Stage` from the record but left the three other descriptions of it,
so the acceptance criterion I was given ("every signature agrees") holds for `check`/`settle` but not
for the type they take: `11` §5's construction cannot compile against `11` §3's own record, and
`11` §2 contradicts §3 two sections earlier.

Minimal fix: make `11:24` name the nine components (or say "the components of §3"), give `11:323`'s
call the missing `PinBudget`/`InstanceRecord`/`OwnerId` (or use the fluent builder of `04` §8 and
`15` §3/`17`), and expand or delete `02:71`'s abbreviation.

### D7 — `check`'s claim view has no declared source (Medium)

Anchors: `15:221` (`check` "Reads: `request.lease().view()`, **the claim view the request
carries**, …") and `15:249-251` ("a request carries the claims that attempt reads (possibly
narrowed)"), and `15` §4.6 P2 ("`check` reads only the claim view its request carries") versus
`01:177`, `22:458-459`, `11:47-49` and `20:545` (no claim component on `MatchRequest`), `15:105-108`
(the kernel gets the ledger from `Host`), `11:100-127` (the body reads a bare `ledger.read(...)`),
and the files that require a *frozen* view: `02:123` ("Ledger | Frozen at dispatch (`FrozenLedger`)
and read-only during a match"), `20:42` (`check`'s read set: "frozen `ClaimView`"), `20:94-96`
("freezes the claim view (immutable rows + revision …)") and `16:369` (the same sentence).

Breaks: `20` §2 (`check`'s read set contains "frozen `ClaimView`" on a worker, while the live
`SavedClaims` is server-thread-only per `16` §4.6/`20` §2), `15` §4.4's own "Must not touch … any
other world handle", and `23` §5's "the kernel starts using `ClaimLedger.read`".

Why it fails: the sweep made `check` the only reader of the ledger revision but never said where
that view comes from. `15` §4.4/§4.6 point at a request field no record declares, so either a reader
puts a live `SavedClaims` in `Host` and reads it from a worker (breaking the thread rule) or invents
the field.

Minimal fix: either add the frozen claim view (or the `FrozenLedger`) to `MatchRequest` in `01:177`,
`22` §9.1 and `11` §3 — matching `02` §5's "frozen at dispatch" and `20` §3.1 — or restate `15` §4.4
and P2 as "`check` reads `Host.ledger()`'s frozen view for this dispatch" and give `11` §4.1's
`ledger` a declared origin.

### D8 — `02` §3–§4 still show settlement as the engine's last stage (Medium)

Anchors: `02:59-90` (§3's flow: `DESIGN`/`PROBE`/`FORM`/`SETTLE` steps, ending in
`MatchResult.Formed(outcome, findings, spend)` with no `check`→`Admitted` step and no `settle`
entry point) and `02:104` (§4's stage table, `SETTLE` … "conflicts are findings; **a formed match
may still fail here**").

Breaks: `23` §4 ("`Formed` … **produced only by `settle`**"), `23` §7 ("settlement trigger: the
`settle` entry point, no longer `stage=SETTLE`"), I-10, and `02` §5's own sentence at `:121`
("`StructureKernel.check` is synchronous and writes nothing; `settle` is the only writer").

Why it fails: the sweep edited `02` §5 and §4's re-check sentence but not the flow diagram or the
SETTLE row, so the architecture file describes a formed-before-settle pipeline that its own
threading section denies. (The stage *labels* themselves are the open part of `22` §16.1 item 16;
the settlement step is not.)

Minimal fix: redraw §3's flow as `check → Admitted(planned, revision)` with `settle` as a separate
server-thread entry point below the pipeline, and reword §4's SETTLE row's fast-fail column to
"conflicts are findings; only `settle` produces `Formed`".

### D9 — `18` §3's C-46/C-47 still assert the withdrawn cache and parallel model (Medium-low)

Anchors: `18:96` (C-47: "only an expectation whose declared `reads()` stay inside one chunk can hit
`SectionCache` … the read envelope is opt-in") and `18:95` (C-46: "enabling it requires
`AGGREGATE`").

Breaks: R25 (`ReadEnvelopeAware` deleted; "Cacheability is `(unit, closureDigest)`"), R24 ("no
`AGGREGATE`-only restriction remains"), `20:427` ("`parallel` is `MatchPolicy.parallel` and lives
inside the determinism tuple"), `20:456-459` (`closureDigest` equality, an entry is contributions or
unit outcomes, no epoch), `22` §9.6.

Why it fails: the sweep carried R24/R25 into the unit files but not into the counterexample
catalogue, so two rows of `18` assert a `SectionCache` type that exists nowhere in the set and a
parallel precondition that R24 removed; a test written from C-47 cannot pass.

Minimal fix: restate C-47 over `AttemptCache`/`CacheKey(plan, siting, unit, closureDigest)` and
C-46 as "parallel is `MatchPolicy.parallel`, off by default, and changes neither value nor `Spend`".

### D10 — `18` C-33 requires the gate to fail on `dirtyEpoch` (Medium-low)

Anchors: `18:82` (C-33: "the result must fail the `dirtyEpoch` freshness term") versus
`20:325` (`cellStamp(unit)` is "the gate's per-unit term"), `20:338` ("`dirtyEpoch` survives only
as a watch-wide monotone counter for metrics and **never appears in the gate**"), `20:371` (term 4
is `changeCount`), `20:360-378`.

Breaks: `20` §5.2/§5.4 and `11` §4.6 (the five-term check), so `18` §5's I-4/I-15 rows inherit a
gate term that does not exist.

Why it fails: the sweep left the round-2 name in the one row that tells a test author which term to
assert; the test is unsatisfiable by the specified gate.

Minimal fix: replace `dirtyEpoch` with `changeCount` (or `cellStamp`) in `18:82`.

### D11 — `03` §7's code-family list is stale (Medium-low)

Anchors: `03:122-125` (the mnemonic list `SPC, OCC, EXP, CLM, BPT, DOC, ENG, CTX, DIA, REM, SPI,
MC`) versus `13:296` (the declaration order `SPC OCC EXP FOC CLM BPT BAY CHK DOC ENG CTX DIA RMD
SPI MC`) and `13:391-393` ("`FOC`, `BAY`, `CHK` and `RMD` are **inserted** into the family order …
`RMD` takes the slot `REM` held").

Breaks: `13` §4.3's closed 15-case enum (the owner of the format), `15:312` ("families are U9's
closed 15-case enum"), and `03:124`'s own "A code is never reused".

Why it fails: the list is the only place in my front's files that enumerates families, and it names
a retired family (`REM`) while omitting three live ones whose codes the set already raises
(`SC-BAY-*` in `13`/`22` §10.2, `SC-CHK-0106`/`SC-CHK-0109`, `SC-FOC-*`, `SC-RMD-0309`) — a
mnemonic a module author would copy from here is unregistered.

Minimal fix: replace the parenthetical with `13` :296's ordered list (or with "the 15 families of
`13` §4.3").

### D12 — `20` §6.3 denies the `OccupantRef` snapshot id that `20` §4.1 stamps (Low)

Anchors: `20:458-459` ("they carry `(instance, type, kind, anchor, footprint)` and **no snapshot
id**, so a reuse across attempts does not forge a binding from an older capture") versus `20:199`
("`occupant(cell)` stamps the final `SnapshotId` into the `OccupantRef` lazily"), `19` R8
("`OccupantRef` includes its `SnapshotId`"), and `16` §3/§5 (`new OccupantRef(id, …)` with the
snapshot id first, `16:400-410`).

Breaks: R8 and `22` §5.1's identity rule, and the cache-safety argument itself — if the ref carries
no id, §6.3's claim that a reused `MATCH` cannot forge an older binding has no mechanism.

Why it fails: within one file the same record is described with and without the id, so the
`AttemptCache` argument cannot be read either way.

Minimal fix: state the direction in §6.3 — either "the ref carries its `SnapshotId`, and a cached
outcome is re-stamped/re-minted on reuse" (matching §4.1 and R8) or drop the id from
`OccupantRef` everywhere (§4.1, `16` §3/§5, `19` R8).

### D13 — `19`'s first-pass rows R7/R9/R11 still state the pre-round-3 model (Low)

Anchors: `19:21` (R7: "A `Formed` before `SETTLE` is a stage result"), `19:23` (R9: `ClaimLedger` is
`settle` + `read`), `19:25` (R11: `MatchRequest` is `(ticket, blueprint, siting, **stage**, lease,
policy, budget)`; `MatchResult` is `Admitted`/`Formed`/`Failed`/`Undecided`/`Deferred`) versus
`19` R27/R31, `23` §2/§4/§7, `01:177`.

Breaks: R27 (six arms; no request carries a stage; `Formed` only from `settle`), R31, and `23` §5
(`release`).

Why it fails: the sweep edited `19` (R27–R34) without marking the first-pass rows it superseded, and
`19` is the disposition record the unit files are checked against — front A's own brief treats R1–R4
as live, so R7/R9/R11 read as live too.

Minimal fix: add "superseded by R27/R31/`23`" to R7, R9 and R11 (or strike the three rows'
contradicting fields).

## 4. Checked and cleared

- `check`/`settle` signatures agree at `23` §2, `22` §9.1, `11` §3, `15` §3, `04` §8, `20`
  §3.1/§6.4/§10, `16` §4.6/§4.10, `18` C-28, and `08`'s prose is the only exception (D1).
- Six arms and `Admitted`'s `@Nullable ClaimPlan planned` / `long ledgerRevision` agree at `22`
  §10.4, `11` §3, `15` §3, `01` :185, `18` C-28 (`04` §3.2/§5 is the exception, D4).
- Nine operations, counted the same way, at `00` R-14, `01` :284, `15` §1/§2/§4.4, `19` R2/R31;
  no tenth; `create` is a factory (`15` §2, `03` §5); `solve` has replaced `repair(` and `check`
  the attempt verb, with no live `match(`/`validate(` facade call left in the English set.
- `MatchPolicy` is the three-component record at `11:54`, `22:462`, `20` §10; no `FailureMode`,
  `StopPolicy` or `deriveOnForm` anywhere in my front's files.
- Stage purge, everything but D1/D2/D8 and the open carrier: `MatchRequest` has no stage at
  `01:177`, `22` §9.1, `11` §3, `20` §10, `19` R31; `Check` is `(id, bay, focus, expectation,
  description)` at `23:143`/`22` §6.2/`09:65`; no JSON example in `10`/`17`/`21`/`22` carries a
  `"stage"` key; `Stage.STALE` survives only in `08:173` (D1's file) and in historical rows
  (`22` §15.2, `01` :79's `ReadStatus`); `expand(Stage)` remains the plan carrier (`02` §4,
  `11` §8) and `11` §4.1's `expand(stage)` is that engine-selected stage, not a request field, so I
  did not report it.
- `MatchCompletion` (Reported/Discarded/Failed) is in `01` :311 and `20` §3, with `MatchOutcome`
  kept as the attempt product in `11`, `12`, `14`, `22` — R29 carried.
- `22` §10.3/§10.4 match `23` §2/§4/§5 on: `Formed` only from `settle`; the plan and revision on
  `Admitted`; `planned` nullable with `Undecided(LEDGER_UNAVAILABLE)`; the check conflict a
  prediction ("currently held") vs settle's conclusion; `SC-CLM-0002` in both layers.
- `16` §4.6/§4.9/§4.10 and `20` §3.1/§5.3/§6.4/§8.5/§10 are on the two entry points
  (`submitForm(admitted)` chains one `settle`; a submit runs `check` and nothing more).
- Left open on purpose, not reported: `20:58`'s `CheckRequest(… List<Stage> stages …)` and `16`
  §4.10's `PROBE`/`FORM` levels are the host-side selector that `22` §16.1 item 16 records as open
  ("the host's `PROBE`/`FORM` levels in `16`"); `22` §13's stage-labelled walkthrough is the same
  carrier question, so I did not score it.

## 5. Could not confirm

- **Where `check`'s frozen claim view comes from** (D7): the inconsistency between `15` §4.4/§4.6
  and the `MatchRequest` records is confirmed; which side the lead intended is not recorded anywhere
  in `23`, `22` §16.1 or `19`, so D7 is stated as an unresolved contradiction with two candidate
  fixes rather than a named wrong file.
- **`MatchResult`'s abstract `stage()`** (D5): the kernel's real shape is only visible in
  `_work/round3/d-drift.md` (which lists the arms with `Stage` on `Admitted` alone), and `_work` is
  historical, so I cannot tell whether `01` :184 or the three sketches is the intended side; the
  cross-file contradiction is confirmed either way.
- **`release`'s intended owner-side shape** (D2): `23` §5 fixes the signature and the three call
  sites but says nothing about `FrozenLedger`, and `19` R9 was not rewritten, so I cannot say
  whether a read-only view must also answer `release`.
