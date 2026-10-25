# Round 3, review round 1 — front A (vocabulary, names and codes)

Read-only on the design set. Everything below was verified against the file text; line numbers are
the English originals unless a `zh/` path is named.

## 1. Method

1. Mechanical pass over the 26 English `docs/spec/*.md` with a script (no hand-grepping): every
   `SC-<FAMILY>-<NNNN>` token per file; every line matching a *registry-shaped* table row
   (`| \`SC-…\` | <Severity> | …`) per code; whole-word scans for the retired vocabulary
   (`ReadEnvelopeAware`, `SettlementIntent`, `SpanExpectation`, `ClaimMode`, `ClaimScope`, `mode`,
   `scope`, `Owned`, `Occupied`, `Audit`, `RESERVED`, `STALE`, `"stage"`, `status.md`, `GAP-`).
2. Declaration-site extraction for each name in the brief's list (`Footprint`, `Sharing`,
   `Coverage`, `Focus<T>`, `ClaimRow`, `ClaimPlan`, `CheckResult`, `MatchOutcome`/`MatchCompletion`)
   plus the claim-adjacent names the sweep touched (`ClaimLedger`, `ClaimView`, `SettleRequest`,
   `ClaimMatrix`, `ClaimSet`, `AnchorSpec`/`AnchorRole`, `CheckRequest`).
3. Read end-to-end: `01`, `08`, `09`, `10`, `21`, `24`, `23`, `19`'s third-pass table, `22` §6.2/§6.5/
   §10/§12.3/§16–§16.1; the claim-relevant sections of `07`, `11`, `13`, `16`, `17`, `20`; the four
   `_work/round3/` reports and the sweep contract.
4. Every finding was re-read in place before ranking. No Minecraft/NeoForge fact is load-bearing here,
   so `sourcescout` was not bound.

## 2. What I checked

- **One declaration per name.** `Footprint` (`Box|Bound|Declared|None`) is now uniform in `01` §F,
  `08` §2/§3, `09` §2/§3, `22` §6.5, `24` §4 Q2; `Sharing` (`EXCLUSIVE|SHARED` + `blocks()`) in
  `01` §F, `08` §3, `09` §2, `22` §6.5, `24` §4 Q3 — the twelve-row whitelist and the
  `permits Owned, Occupied, Declared, Audit` form are gone from every English original. `Coverage`
  belongs to the bay alone; `SnapshotPolicy`'s enum is `CaptureMode{EXACT, REQUIRED}` (`06` §… :109-113)
  and no `policy.coverage`/`coverage()` survives. `ClaimRow`'s component is `sharing` in `01` :116,
  `08` :72, `16` :274, `22` :523.
- **`13`'s table against every code raised or cited.** 123 distinct codes; 90 have a registry-shaped
  row somewhere; all of `SC-CLM-0001…0006`, `0104`, `SC-BAY-0107…0111`, `SC-BPT-0001/0002`,
  `SC-CHK-0101/0102/0106/0107/0109`, `SC-CTX-0202/0301`, `SC-ENG-0001…0005`, `SC-DOC-0101…0606`,
  `SC-DIA-*`, `SC-RMD-0001/0309`, `SC-SPI-*`, `SC-MC-0001/0404/0502` are registered, and the R27–R34
  allocations landed (`0003` keeps `08` §4.6's meaning at `13` :334, the old INFO row is `0006` at
  :337, `0107` is R3-only at :341, R1/R4 are `SC-CHK-0106` at :348, `0005`/`0006` are new at :336-337).
- **Retired terms.** `SettlementIntent` survives only in `23`'s "it replaces …" notes (`23` :4, :178);
  `ClaimScope`/`ClaimMode`/`SpanExpectation` only in `_work/**`; no `status.md` pointer and no
  `TODO(GAP-xx)` in any English original.
- **R1–R4** are stated identically in `24` §5, `09` §4.1 :185-188, `13` :341/:348 and used the same
  way in `17` §2 — with the two exceptions ranked 9 and 10 below.
- **The document schema in `10` against `10`'s IR and against `17`/`21`/`22`'s examples** — the
  sweep's D3 fix (keys `coverage`/`sharing`/`footprint`, `footprint` default `bound`, `sharing`
  default `exclusive`) is present in `10` :205-213 and `21` §3.3; the mismatches that remain are
  ranked 11–13.
- Contract §A–§D conformance item by item (see §3, §4); contract §G/§H items are front B/C's.

## 3. Ranked defects

### 1. `08` §4.5 still makes `check` the writing call

`08` :183-185 — "The facade `check()` is one call: worker match → server-thread settler → result …
Settlement is a step of the facade call"; :301 repeats that "the matcher hands the facade a
`SettleRequest`". Breaks `23` §2 (`check` "writes nothing"; `settle` is the only writer) and contract
§B.6/§B.7. It is the exact edit `23` §9 lists for this file ("`08` §4.5/§4.6 (one call → two)"), so
the sweep's claim to have applied the decided part of `_work/round3/a-semantics.md` D1 fails here.
Fix: rewrite §4.5 as two calls — `check(request, live)` returns `Admitted{planned, ledgerRevision}`
and writes nothing; `settle(request, plan, live)` does the CAS on the server thread — and change
:301's "hands the facade a `SettleRequest`" to "hands the facade a `ClaimPlan`".

### 2. `ClaimLedger.release` is declared in `23` only

`23` §5 :107-112 adds `ClaimVerdict release(OwnerId owner, Siting siting)` and its table (:119-124)
says three lifecycle points "must call it". The ledger is declared **without** it in the three places
that own the name: `01` :119 (`revision` / `settle` / `read`), `08` §2 :24 and §3 :67-71 (the unit that
"owns … the `ClaimLedger` SPI", `08` :9), and `19` R9 :23 (still presented as canonical). No spec site
calls it: `20` :130-132's `LevelEvent.Unload` / `dropInstance` prose releases snapshots and leases,
never claim rows, and `16` :97-101's `SavedClaims` shows only `read`. Fix: add `release` to `01`'s
row, `08` §2/§3 and `19` R9, and name the call in `20` §? (`LevelEvent.Unload`, `dropInstance`) and
`16` §4.9.

### 3. `08` §4.4 still settles by stage, and still re-settles `STALE`

`08` :172-174 — "`SETTLE` performs the request on the server thread (§4.5) and only then may a
`Formed` exist. `STALE` re-settles the instance's rows". Contradicts `23` §7 ("settlement trigger |
the `settle` entry point, no longer `stage=SETTLE`"; "`Stage.STALE` | deleted; a targeted re-check is
`check` plus a `ChangeSet`") and contract §B.10. Fix: "`settle` performs the request …"; the removed
sentence becomes "a targeted re-check is `check` with a `ChangeSet` on the request".

### 4. `08` declares `ClaimRow` both with and without its `siting`

`08` :20 (`| \`ClaimRow\` | (cell, sharing, owner, bay) |`), :30 ("A `ClaimRow` carries no siting")
and :98 ("The siting lives on the `SettleRequest`, not on the row") versus the same file's
declaration at :72 `record ClaimRow(LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting,
NamespacedId bay)` and its own use at :231-237, plus `01` :116, `16` :274, `22` :523. The prose is
also self-refuting: `08` :130-131 says `ClaimMatrix.conflicts` "returns false for two rows of one
`(owner, siting)`" from the two rows alone, which requires the siting on the row. Fix: put `siting`
back into the §2 shape and delete the §4.1 sentence, or (if the row really loses it) change all five
other sites and `ClaimMatrix`'s signature to take the settlement key.

### 5. `AnchorRole` / `AnchorSpec` are declared in two incompatible shapes

`01` :134-135 declares a **type** `AnchorRole` and `AnchorSpec(AnchorRole role, Cuboid cells, int
priority, boolean required)`; `01` :199 repeats `AnchorCandidate(AnchorRole role, …)` and `04`
:152-153's golden example uses `AnchorSpec.of(AnchorRole.PRIMARY, Cuboid.of(3, 1, 0, 5, 2, 1))` and
`OrientationPolicy.builder(AnchorRole.PRIMARY)`. `09` :36-37/:100 states the opposite — "a role is
never a type of its own", roles are `NamespacedId` constants on `AnchorRoles`, `AnchorSpec(NamespacedId
role, NamespacedId property, String value)` — and that shape is what `22` :91, `11` :173, `10` :66/:197
and `17` :49 use. `14` :136's `BindAnchor(AnchorRole role, …)` follows the `01` reading. One of the two
shapes is wrong everywhere it appears. Fix: make `01` §G and `01` :199 match `09` (`AnchorRoles`
holder, `AnchorSpec(role, property, value)`, `AnchorCandidate` carrying a `NamespacedId` role) and
correct `04`'s example and `14`'s `BindAnchor`.

### 6. `Focus<T>` still has two member sets

`22` §6.2 :204-209 declares `take` / `subject` / `reads` / `desc()` — no `decisionKind()`, no
`staticDecisionCells(Cuboid)`, no `describeMissing()`, plus an `FocusDesc` that appears nowhere else —
while `01` :104, `07` §3 :34-41 and contract §A.4 fix the six-member set, and `09` :82 and `22` :224
both *call* `focus.describeMissing()`, which §6.2 does not declare. R32's "one `Focus<T>` member set"
and `_work/round3/b-types-data.md` B3 both name this; the sweep edited `01`/`07`'s wording but left
`22` §6.2. Fix: replace :204-209 with `01`'s member list.

### 7. `08`'s retry-token workaround rests on a false premise

`08` :189 — "(`22` §10.4's `Undecided` record has no token field, so the token rides a finding
note — §8.)" — and `08` :311-312 — "`22` §10.4's `Undecided` record has nowhere to put one". `22`
:569-570 declares `record Undecided(UndecidedReason reason, List<Finding> findings, @Nullable
MatchOutcome partial, @Nullable RetryToken retry)`, and `01` :188 and `23` §3.2 (`Undecided(LEDGER_MOVED)`
+ `RetryToken`) agree. Fix: delete the parenthetical and §8's paragraph, and let the token ride the
`retry` component.

### 8. The DESIGN-time internal overlap has two registered codes and no owning row

`13` :340 allocates `SC-BPT-0002` = "two bays overlap with sharing values that cannot coexist";
`08` :203 allocates `SC-CLM-0001` = "two bays of one blueprint overlap, at least one `EXCLUSIVE`"
(DESIGN **and** SETTLE), and `08` :136-137 says an "`EXCLUSIVE` pair is `SC-CLM-0001`", while `13`
:332 registers `0001` as the narrower "two `EXCLUSIVE` claims name the same cell". The row that
enforces the overlap, `09` :292 `B-7`, names no code at all, and `09` :553 (F-4) expects only "throws
B-7". This is `_work/round3/a-semantics.md` D8, whose proposed disposition ("B-7 → `SC-BPT-0002`;
`SC-CLM-0001` SETTLE-only") is in neither the sweep contract nor `22` §16.1, so the sweep both failed
to apply it and failed to record it. Fix: give B-7 `SC-BPT-0002` in `09` §4.6/§4.7, reword `13` :332
to the ledger-side reading, and narrow `08` :203 to SETTLE.

### 9. R3 and R4 both own "the checks match the coverage"

`09` :294 (B-9) = "the declaration obeys R1 and R4, **and every check's decision kind matches the
coverage** (`SC-CHK-0106`)", while `24` :98 (R3) = "`ONE_SUBJECT` requires subject-kind checks …
`SC-BAY-0107`", `13` :341 agrees with R3, and R4's own statement (`24` :99, `09` :187) covers only
`EVERY_CELL`/`ANY_SUBJECT`. So a `ONE_SUBJECT` bay whose check asserts per-cell is `SC-CHK-0106` by
B-9 and `SC-BAY-0107` by R3 — and R3's build half has no row in `09` §4.6 at all (B-9 names R1 and
R4). Fix: drop the "decision kind matches the coverage" clause from B-9, or move it into R4's
statement in `24`/`09`/`13` and cite `SC-BAY-0107` where R3 owns the case.

### 10. `ONE_SUBJECT` + `Box` is attributed to R3, which does not forbid it

`17` :66-67 — "`ONE_SUBJECT` + `Box` would be `SC-BAY-0107` (R3: a `ONE_SUBJECT` bay needs
subject-kind checks and a bound footprint equal to the bay)" — and `09` :553 F-1 expect a build
refusal for `ONE_SUBJECT` + `Footprint.Box`. R3 as recorded in `24` :98, `09` :187, `13` :341 and in
`17`'s own gloss refuses only a missing subject-kind check or `bound footprint ≠ bay.cells`;
`Footprint.Box` fixes the owned cells, not the bound footprint, and `24` §6 lists no such machine. Fix:
state the clause the falsifiers assume ("`ONE_SUBJECT` requires `Bound`, or `None`") in R3 in all four
files, or delete the `Box` half of `17` :66 and F-1.

### 11. `21` still treats `plans` (and `claims`) as document fields

`21` :14 lists the canonical document's fields as "… `orientation`, `plans` by `Stage`, `claims`,
`extensions`"; :45 says COMPACT may omit `plans`; :294 gives a per-stage default ("`plans` |
`PROBE`: the anchor and bounds units; `FORM`: one check per named bay …; `SETTLE`: the claim
settle"); :376 says the normalised document "contains … plans". All four contradict the same file at
:109 ("no segments, no assembly, no plans, no claims block"), :127 ("`plans` … not a document key")
and `10` :199 ("`plans` | — | … Refused key"), and the `SETTLE: the claim settle` default is the
`stage=SETTLE` machinery `23` §7 deleted. `claims` is a key no file declares (not in `10` §4.1's
table nor `21` §3.1). Fix: delete `plans`/`claims` from :14, `plans` from :45, the `plans` row from
:294, and "plans" from :376.

### 12. The `orientation` field has two shapes and two requiredness answers

`10` :198 types it "Role, allowed turns, upright-only" and :444-445's example spells it
`{"anchor": "mymod:primary", "uprightOnly": true, "turns": [...]}`; `21` :126 types it "`front`, the
authored front vector", :203-204 says "`orientation.front` … There is no turn list in schema 2", and
`17` :125 / `21` :105 / `22` :675 write `{"front": "north"}`. Requiredness is also split: `10`
:186-187 makes
`orientation` required precisely *because* "an absent policy silently means 'identity only'", while
`21` :45/:126/:293 accept its omission and supply exactly that identity default. `anchors` is
required in `10` :197, optional in `21` :125, and `21` §5's default table has no `anchors` row even
though `09` B-4 needs a spec for the policy's `primaryRole`. Fix: one shape and one requiredness for
`orientation` in `10`/`21`/`17`/`22`, and either make `anchors` required in `21` or add its default.

### 13. Bay references are compiled ids in `10`, bare names in `17`/`21`/`22`

`10` :446/:450 writes `"bay": "main:mouth"` and `"bay": "main:wall"`, and `10` §4.5 step 5 (:377-378)
plus Q-D3 (:605) make the compiled id (`as + ":" + localName`, `main` for the implicit placement,
`10` :425-426) the form a `Spec` addresses. `17` :108, `21` :91, `21` :97 (`"region": [...]` beside
`21` :227's `region`), `21` :184 and `22` :652/:664 write `mouth` / `interior`, which resolve under the
default namespace to `mymod:mouth` — a different id. Both spellings cannot be right, and `21` §10's
falsifier ("the furnace compact JSON normalises to the same `Blueprint` as the canonical JSON") is
what breaks. Fix: pick one spelling and correct the other three files.

### 14. `SC-DOC-0521` is raised by two files and registered by none

`21` :367 and :414 and `22` :702 emit `SC-DOC-0521` for an unreadable block entity, but `10` — the
owner of the `SC-DOC` block (`13` :381) — registers `0501…0505`, `0601…0606` only (`10` :329-339).
The same gap is systemic: 33 codes have no registry-shaped row anywhere, among them `SC-ENG-0104`
(raised at `11` :185/:327; `13` registers `0001…0005` only, `11` :381 names the range without a
meaning), the whole `SC-MC-*` set used in `16`/`20` (`0102`, `0201`, `0203`, `0302`, `0304`, `0306`,
`0401`-`0403`, `0405`, `0501`, `0505`, `0601`), `SC-OCC-0101`/`02xx` (`06` :160, :213, :385) and
`SC-EXP-0010`-`0014` (`07` :317-322). Fix: register each block (or one line saying the block is not
registered yet and why); `SC-DOC-0521` is the one inside my front's own files.

### 15. `08` §5's sketch contradicts `08` §3's own plan type

:253-255 call `plan.staticFootprint()` and `plan.resolve(List.of(doorBinding), before.revision())`,
returning a `ClaimSet`; §3 :79-87 declares `ClaimPlan` as `Rows(List<ClaimRow>)` | `Invalid(
FindingBundle)` with `ClaimPlanner.plan(blueprint, siting, bindings, owner)` and no such members. It
also mints a `ClaimSet` before any settle, while `01` :117 and `08` :18 define `ClaimSet` as one
settlement's rows carried by `Formed`, bypassing the two entry points the rest of the sweep
introduced. Fix: restate the sketch against §3's `ClaimPlan` and route it through
`check(request, live)` → `settle(request, plan, live)`.

### 16. `09` §4.8 says `Sharing` names three semantics

`09` :365 — "`Sharing` names three semantics, not three levels". `Sharing` has two values (`24` §4 Q3
:79-84, `01` :113, `08` :31-33, `22` :317); the third is the retired `RESERVED` of `ClaimMode`. Fix:
"`Sharing` names two semantics, not two levels", or rephrase to `Sharing` × `Footprint`.

### 17. `scope` survives as live claim vocabulary in `08`

`08` :9 ("the scope footprints"), :141 ("Footprints and scopes"), :143 ("| Scope | Owned cells |"),
:179 ("non-`Declared` scope"), :207 ("owned by no bay's scope"), :293 ("Each scope names its cells"),
:306 ("a per-bay scope choice"). Contract §A.3 bans `mode`/`scope` in an English original (historical
rows in `19`/`_work/**` excepted), and the equivalent tables elsewhere use `Footprint` (`22` :534).
Fix: `scope` → `footprint` in those seven places.

### 18. `Audit` survives as "audit bay"

`21` :171 ("an audit bay (`Footprint.None`, asserting nothing)") and :410 ("is an audit bay (legal,
claims nothing)"). Contract §A.3 bans `Audit` as live vocabulary and `24` §7 says `None` "no longer
smuggles in a read permission"; no glossary row defines "audit bay". Fix: "a bay with
`Footprint.None`".

### 19. `01` keeps a glossary row for `ReadEnvelopeAware`

`01` :93 — `| Read envelope aware | \`ReadEnvelopeAware\` | Deleted: … |`. Contract §A.5 is explicit
that the name "appears in no English original except `19`'s historical row and `_work/**`"; `19` R25
already records the deletion. Fix: delete the row (and its `zh/01` mirror line).

### 20. `01` §F lacks rows for three cross-unit claim names

`SettleRequest`, `ClaimView` and `ClaimMatrix` are referenced by `08`, `11`, `14`, `15`, `16`, `20`
and `22`, but appear in `01` only inside other rows (`:119`), and `01` §P requires a row for every
name another module must reference. `10` :89's `FootprintRef(Kind, cells)` is the mirror-image
problem: a second spelling of `Footprint` declared in `10` §3 and used by no file. Fix: add the three
rows; delete `FootprintRef` or make `SegmentDocument.Bay` use it.

### 21. `08` §4.6's `SC-CLM` block omits the two codes the sweep allocated to it

`08` :9 says the unit owns "the `SC-CLM` codes", and §4.6 :201-210 is its block, but contract §D.15/
§D.16's `SC-CLM-0005` (a plan that does not match its request) and `SC-CLM-0006` (rows returned
unused) exist only in `13` :336-337, in a family `08` enumerates. Fix: add both rows to `08` §4.6.

## 4. Checked and cleared

- `Footprint`/`Sharing`/`Coverage` values, `Sharing.blocks()`, `ClaimRow`'s `sharing` component,
  `CaptureMode`, and the absence of the twelve-row whitelist and of `ClaimScope`/`ClaimMode` in every
  English original — all as R32/`24` require (`_work/review2/*` findings are closed).
- `13`'s allocations against contract §D: `0003` keeps `08` §4.6's ERROR meaning, the old INFO row is
  `0006`, `0004` is the ledger, `0005` is the mismatched plan, `0107` is R3-only, `0106` carries R1
  and R4, `0111` is R2; `SC-CTX-0202`/`0301` are now in the table (`13` :365-366); no code is
  re-pointed.
- `check`/`settle` signatures (`23` §2, `11` :52, `15` :103, `22` :464-465) and `MatchResult`'s six arms
  with `Admitted.planned`/`ledgerRevision` agree in `01` :184-192, `11` :61-80, `15` :124-134, `22`
  :560-571; `MatchCompletion{Reported, Discarded, Failed}` matches in `01` :311 and `20` :82-86, and
  `MatchOutcome` keeps `22` §10.4's "what one attempt produced" meaning everywhere else.
- `CheckResult` is one declaration (`01` :143, `07` :136, `09` :75, `22` :217) with the order
  `(Reply, subjects, notes)`.
- `10` §4.1's field table matches `10` §3's `BlueprintDocument` component set, includes the refused
  `plans` row, and its compact keys (`coverage`/`sharing`/`footprint`, defaults `bound`/`exclusive`)
  match `21` §3.3 and `17`/`22`'s JSON.
- `SC-DOC-0603`/`0604` are the only retired DOC codes and both carry their retirement reason; the
  `Stage` vocabulary in `13` (`:518` `DESIGN, PROBE, FORM, SETTLE`) has no `STALE`.
- `"mode": "cells"` in `10` :451, `17` :115, `21` :98/:228, `22` :665 is a computation *argument*,
  not claim vocabulary — not a §A.3 violation.
- `22` §12.3's bare `"kind": "scale"` (`22` :665) contradicts `21` §3.5's namespace rule, but `17`
  :152-154 already documents the divergence in place; left as-is.

## 5. Could not confirm

- Whether `08`'s `ClaimPlan.Rows|Invalid` or `23`'s `Admitted.planned` is the intended member set for
  `ClaimPlan` — `23` §2/:41-44 and `24` §9.4 describe it only in prose, and item 7 of `22` §16.1
  (plan authentication) keeps its contents open. Rank 15 treats only the §3-versus-§5 mismatch, which
  needs no ruling.
- Whether the bare `role`/`property` strings in `21` :104/:188 (`"front"`, `"facing"`) are legal
  default-namespace ids. `15` :158 ("`parse` also takes a bare path under the default namespace")
  says yes; `10` :308's `SC-DOC-0103` ("an id is not `ns:path`") says no. I did not rank it because
  the two rules may be about different layers.
- Whether "audit bay" (`21` :171/:410) is meant as a live term or as prose shorthand — rank 18 is
  the conservative reading of contract §A.3.
- `SC-BAY-0101` in `13` :840's F2b is an unallocated code used as a synthetic ERROR example; I could
  not tell whether the falsifier intends a registered code or any code text.
