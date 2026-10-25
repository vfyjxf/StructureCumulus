# E — U4 claims (code ↔ spec)

## 1. Scope (files read)

**Code, the slice** (`src/kernel/java/dev/vfyjxf/cumulus/kernel/claim/`): `ClaimLedger.java`,
`InMemoryClaimLedger.java`, `ClaimMatrix.java`, `ClaimRow.java`, `ClaimSet.java`, `ClaimVerdict.java`,
`ClaimView.java`, `SettleRequest.java`, `ClaimMode.java`, `ClaimScope.java`, `package-info.java`.

**Code, named cross-references** (load-bearing, and only for the question asked):

- `src/kernel/java/dev/vfyjxf/cumulus/kernel/engine/ClaimPlan.java`, `ClaimPlanner.java` — the plan /
  planner pair lives here, not in `kernel.claim` (D13, D6's `SC-BAY-0107` producer).
- `src/kernel/java/dev/vfyjxf/cumulus/kernel/blueprint/Bay.java`, `Coverage.java` — the legality
  whitelist D2 and the R1–R4 question are answered here.
- `src/kernel/java/dev/vfyjxf/cumulus/kernel/engine/MatchSettler.java`, `BayBinding.java`,
  `src/kernel/java/dev/vfyjxf/cumulus/kernel/base/OwnerId.java`, `InstanceId.java`,
  `src/kernel/java/dev/vfyjxf/cumulus/kernel/occupant/OccupantRef.java` — the R36/§4.5 settle path and
  the carriers SC-CLM-0005 needs.
- `src/kernelTest/java/.../claim/ClaimMatrixTest.java`, `InMemoryClaimLedgerTest.java`,
  `examples/HatchScenarioTest.java` — what the tests assert about the codes and `RESERVED`.

**Docs**: `08-claims.md` and `24-claim-vocabulary.md` end to end; `01-glossary.md` §F (U4) rows plus
the `Footprint`/`Sharing`/`Coverage`/`Bay`/`OccupantRef`/`BayBinding` rows; `13-findings.md`'s `CLM`,
`BPT`, `BAY`, `CHK` rows and the `P`-column note; `23-claim-entry.md`; `19-open-proposals.md`
(R8, R27–R37); `00-overview.md` §5; `03-naming.md` §3; `22-consolidated-design.md` §10/§6.5;
`09-blueprints.md` §4.1/§4.2; `15-spi.md` §3.

Minecraft/NeoForge: nothing in this slice should touch it (`ClaimRow`'s world is the only Minecraft-adjacent
name), so no SourceScout call was needed.

## 2. Divergences

| id | doc anchor | code anchor | what the doc claims | what the code does | verdict | one-line reason |
| --- | --- | --- | --- | --- | --- | --- |
| D1 | `08` §4.2 ("Two bays of one blueprint are two claimants, so the matrix applies unchanged", "an `EXCLUSIVE` pair of one blueprint is that same `SC-BPT-0002`, never a ledger conflict, because `ClaimMatrix.conflicts` is false for two rows of one `(owner, siting)`"); `08` §2 matrix row | `ClaimMatrix.java:60` `internalOverlaps`, `:85-92` `internalConflict` (`:89` `!a.owner().equals(b.owner()) \|\| !a.siting().equals(b.siting()) \|\| a.bay().equals(b.bay())` → false) | one predicate; an overlap of two bays of one blueprint is decided at `SETTLE` | a **second, unstated** predicate: same cell ∧ same `(owner, siting)` ∧ **different bay** ∧ one `EXCLUSIVE` — the exact inverse of `conflicts`' `(owner, siting)` exclusion | **BOTH** | the spec asserts "the matrix applies unchanged" while `conflicts` returns false for precisely the pair the overlap rule must catch, so the spec names no predicate a regenerated kernel could implement. Rule: two predicates — `conflicts(a, b)` = same world ∧ same cell ∧ not the same `(owner, siting)` ∧ one `EXCLUSIVE`; `overlapsWithin(rows)` = same cell ∧ different bay ∧ **same** `(owner, siting)` ∧ one `EXCLUSIVE` |
| D2 | `24` §5 R1–R4 ("Everything else is allowed"); `24` §6 (the mob pen `ANY_SUBJECT + Bound + SHARED`), §7 gain 3; `24` §9.1 | `Coverage.java:15-16` `case SUBJECT -> !(scope instanceof ClaimScope.Owned); case PRESENCE -> scope instanceof ClaimScope.Audit \|\| scope instanceof ClaimScope.Declared;` (`Bay.java:32-34` throws `SC-CHK-0106`) | `ANY_SUBJECT + Bound` and `ONE_SUBJECT + Box` are legal (only R1–R4 restrict) | the 9-of-12 whitelist **refuses** `ANY_SUBJECT + Occupied` and `ONE_SUBJECT + Owned` — 24's own motivating machine (the pen that owns the animal's footprint) cannot be declared | **DOC** | `24` §9.1 records the rename but not that today's whitelist *refuses* the machine `24` exists to enable; a regenerated kernel from R1–R4 accepts it, so the code must widen, not merely rename |
| D3 | `08` §4.1 order table (`ClaimRow` = `(world, cell, owner, bay)`, "the sharing value never breaks a tie"); `08` §4.1 replacement rule ("two placements are two claimants", a moved structure's former rows survive); P5 ("both compose total orders") | `ClaimRow.java:30-36` `compareTo` = `(cell, owner, bay)` — `siting` absent | the row order is a total order | two rows of one `(cell, owner, bay)` at two sitings (one owner, one blueprint, a `SHARED` frame re-settled at an offset siting: `InMemoryClaimLedger.replaced` keeps both keys) compare equal though they are not equal — and `conflicts` treats them as two claimants | **BOTH** | the key that admits two claimants must contain the claimant: `(world, cell, owner, siting, bay)`; as written, `ClaimSet.rows` order (and any `TreeSet<ClaimRow>`) depends on settle order, falsifying P5 |
| D4 | `13` `SC-CLM` rows; `23` §8 (0003, 0104), §9.4 (0005), §9.5 (0004); `19` R30, R35 (0007) | `ClaimMatrix.java:25-26` (`CLM,1` / `CLM,2`); `MatchSettler.java:37-38` `LEDGER_MOVED = (ENG,3)`, `SETTLE_UNAVAILABLE = (ENG,4)` | nine `SC-CLM` codes are the unit's vocabulary | the tree can emit exactly **`SC-CLM-0001` and `SC-CLM-0002`**; 0003/0004/0005/0006/0007/0104 have no producer, and the unavailable/moved-twice path emits `SC-ENG-0003`/`0004`, which `23` §9.5 forbids | **DOC** | each has a §9/R-record, but the tally is nowhere: 6 of 9 codes in the table are unimplemented and two spec-reassigned codes are still emitted under their old family (0001's own text is RULE-1) |
| D5 | `24` §5 R1, R2, §9.1, §9.4; `23` §8 row "`EXCLUSIVE` with a `None` footprint \| accepted by code; zero rows" | `Bay.java:36-39` (refuses `PRESENCE + EXCLUSIVE` unless `Declared`); `Coverage.java:14` `case CUBIC -> true`; `ClaimPlanner.java:103` (an `Occupied`/`Bound` scope with no binding yields no rows, silently) | R1 refuses `EXCLUSIVE + None` at build; R2 refuses `EXCLUSIVE + Bound` that resolves empty (`SC-BAY-0111`, new); both still to do | R1 holds only inside `Coverage.PRESENCE`; `EVERY_CELL`/`ONE_SUBJECT` + `Audit`/`None` + `EXCLUSIVE` is still accepted; `SC-BAY-0111` has no producer anywhere | **DOC** | `23` §8's "accepted by code" is **stale** (one of three coverages is refused) and would mis-scope the work as a rename; the remaining R1 cases and all of R2 are genuine gaps |
| D6 | `24` §5 R3/R4 rows (`R3 → SC-BAY-0107`, `R4 → SC-CHK-0106`); `13:342` (BAY-0107 = "`ONE_SUBJECT` without subject-kind checks, or a bound footprint…"), `13:349` (CHK-0106 = R1 or R4) | `Bay.java:82-83` (a `SUBJECT` bay holding a `CUBIC` focus → `SC-CHK-0106`), `:74` ("CUBIC bay … leaves … undecided" → `SC-BAY-0107`), `:58-59` ("decides cells outside bay" → `SC-BAY-0107`), `:67` (`PRESENCE` holding `CUBIC` → `SC-CHK-0106`) | R3's kind half and R4's completeness half both have one home | the code splits the *opposite* way: kind mismatch → `CHK-0106`, incomplete coverage → `BAY-0107`, plus a third case (`a check decides cells outside bay`) that neither table row covers | **RULE** (RULE-4) | `13`'s own family table says `BAY` = "bay coverage" *and* `CHK` = "coverage match", so the spec cannot say which family owns an R3-kind / R4-completeness violation |
| D7 | `08` §4.6 ("One finding per **(owner, bay) pair**"), P5 ("findings group by `(owner, bay)` and order by the first contested cell"), §4.1 (the claimant is `(world, owner, siting, bay)`) | `InMemoryClaimLedger.java:111` `new Pair(candidate.bay(), incumbent.owner(), incumbent.bay())`, `:127` `record Pair(NamespacedId challengerBay, OwnerId incumbentOwner, NamespacedId incumbentBay)`, `:122-125` pairs sorted by `Pair` order; `ClaimMatrix.java:186` `record BayPair(OwnerId incumbentOwner, Siting incumbentSiting, NamespacedId incumbentBay, …)` **does** carry both sitings | one finding per pair of claimants, ordered by the first contested cell | the grouping key drops the incumbent's `siting` (and the challenger's owner), so two incumbent claims of one owner+bay at two sitings collapse into one finding; findings are ordered by `(challengerBay, incumbentOwner, incumbentBay)`, not by the first contested cell | **BOTH** | group by the full claimant key on both sides — `(owner, siting, bay)` — and order findings by `(first contested cell, challenger key, incumbent key)`; today `ClaimMatrix.BayPair` and this `Pair` disagree with each other, let alone with P5 |
| D8 | `08` §2 ("`ClaimConflict` … the rows that failed the matrix"), §3, §4.5 ("`Failed(SC-CLM-0002)` with the incumbent rows"), P2 | `InMemoryClaimLedger.java:91-101` `blockingRows` (stored rows only), `:40` + `ClaimMatrix.java:96-107` `rowsOf` (challenger **and** incumbent) | the carrier's meaning is stated twice, differently | the store path returns only held rows; the request-internal path returns both sides of each overlap | **CODE** | `§4.5`/`instant` semantics win: `ClaimConflict.rows` = the **held** rows that block, plus the request's own conflicting rows when the conflict is inside one request — state that exception in §2/§3 instead of "the rows that failed the matrix" |
| D9 | `08` §2/§3 `record ClaimConflict(List<ClaimRow> rows, List<Finding> findings)`; `22:528` identical; `01:124` identical | `ClaimVerdict.java:21` `record ClaimConflict(List<ClaimRow> rows, FindingBundle findings) implements ClaimVerdict` | a `List<Finding>` | a `FindingBundle` | **CODE** | the same unit already carries `ClaimPlan.Invalid(FindingBundle)` (`01:125`, `08` §3), every facade result carries a bundle (`01:191-192`), and bundles own the ordering/dedup the renderers rely on (`13` F5) — a `List<Finding>` cannot express "no primary" ordering or dedup |
| D10 | `08` §3 `public interface ClaimView { … }`; §4.7 (`read` → `available() == false`); `22:626` `@Nullable ClaimView claims` | `ClaimView.java:9` `record ClaimView(List<ClaimRow> rows, long revision, boolean available)`, `:15-18` ("an unavailable view holds no rows" → `IllegalArgumentException`), `:21-23` `unavailable()` | an interface with three accessors | a value record with a canonical constructor, an invariant the spec never states, and the only factory that can produce `available() == false` | **CODE** | an interface with no producer and no factory leaves `Undecided(LEDGER_UNAVAILABLE)` unconstructable for a host, and `22:626`'s `@Nullable ClaimView` is a second, competing "no answer" channel the spec must pick between |
| D11 | `19` R35; `08` §2/§3/§4.1/§4.2; `01:117/120/121/122/123`; `13` `SC-CLM-0007`; `24` §9.6 | `ClaimRow.java:12` (no world), `ClaimMatrix.java:35-44` (world never compared), `SettleRequest.java:9` (no world), `ClaimLedger.java:12-16` (`revision`, `settle`, `read` — no `world()`), `OwnerId.java:3` (still `(NamespacedId world, NamespacedId owner)`) | the row names its world; the handle is bound to one world; `conflicts` compares the world first; `SC-CLM-0007` refuses disagreement | no world anywhere in the slice; the world is reachable only through the **owner's** world, which is the owner's identity, not the claimed world | **DOC** | `R35` covers it (and `24` §9.6, `23`–`24` §9); the extra note is that today's `OwnerId.world` is a different world from the one the row is in, so no migration path can read the row's world off the owner |
| D12 | `19` R32; `24` §4 Q3 ("it is why the type has two values"), §9.2; `01:114-116` | `ClaimMode.java:4-8` `EXCLUSIVE, SHARED, RESERVED` + `excludes()`; `ClaimScope.java` `Owned/Occupied/Declared/Audit`; `Bay.java:19` `(… Coverage coverage, ClaimMode mode, ClaimScope scope …)` | three questions, three types: `Coverage × Footprint × Sharing`; `Sharing` has **two** values | two enums (`ClaimMode`, `ClaimScope`) under the old names plus a third mode `RESERVED` that nothing produces (`grep` finds it only in `HatchScenarioTest`/`ClaimMatrixTest`, whose comments cite "`08` §4.6: a `RESERVED` row behaves like `SHARED`…" — text `08` §4.6 no longer contains) | **DOC** | `R32` + `24` §9.2 cover the rename; the new fact is `RESERVED`, a value the spec never had, produced by no kernel path, and asserted by tests against a doc line that no longer exists — it must be deleted with them, not documented |
| D13 | `19` R36; `08` §3 (`ClaimPlan.Matched(long ledgerRevision, List<BayBinding> bindings)`, `ClaimPlanner.Derived(rows, overlaps, staticFootprint)`), §4.5, §4.2 ("the kernel planner and `SavedClaims` both call it"); `01:125-126` | `engine/ClaimPlan.java:12` `record Rows(List<ClaimRow> rows) implements ClaimPlan`, `engine/ClaimPlanner.java:45-68` `plan(…)` → `new ClaimPlan.Rows(…)`; `MatchSettler.java:58` `settle(request, outcome, contextRevision, rows, record, lease)` takes `List<ClaimRow> rows`; `grep` shows `ClaimMatrix` is called **only** from `InMemoryClaimLedger` | the plan carries bindings + the revision it read, never rows; the planner returns `Derived(rows, overlaps, staticFootprint)` and is a caller of the matrix | `ClaimPlan.Rows` is the plan; the planner returns rows and never calls the matrix; there is no `Derived`, no `overlaps`, no `staticFootprint`, and the settler is handed rows | **DOC** | `R36` + `23` §9.2/§9.4 cover the shape; the overlooked half is that the spec's `Derived.overlaps`/`staticFootprint` have **no consumer anywhere** (`claim` §4.2 attributes the matrix call to the planner, which does not make it) — see RULE-3 |
| D14 | `23` §5 (the ledger gains `release`, "an absent key is a no-op (no error, no revision bump)"), §9.1; `19` R35; `01:120` | `ClaimLedger.java:12-16` — `revision()`, `settle`, `read` only; no `release`, no `world()`; `InMemoryClaimLedger` has no release path | `release(OwnerId, Siting)` deletes every row of the key and bumps the revision; the three lifecycle points call it | a ledger that can be written and never released | **DOC** | `23` §5/§9.1 covers it; it stays in the table because it is the difference between "a mistake is permanent" (`23` §1) and the design, and no test in the slice covers the released-key-is-empty round trip |
| D15 | `08` §3 `public record ClaimSet(…) { public ClaimSet { rows = List.copyOf(rows); } }  // canonical order, §4.1`; §2 vocabulary ("rows in canonical order"); §4.1 order table; P5 | `ClaimSet.java:14` `rows = ClaimRow.ordered(rows);` | the constructor establishes canonical order | the spec's own constructor does **not** sort (nor dedup) anything | **CODE** | `List.copyOf` copies insertion order, so a kernel regenerated from `08` §3 would produce a `ClaimSet` whose rows are not in §4.1's order — `P5`'s determinism would be false for the hardest-working value in the unit |

Also *not* in the table because they are the brief's known state and add nothing: `MatchRequest` still
carrying `Stage`, the facade's single `check(request, Supplier<SnapshotInfo>)`, `OwnerId`'s world as an
R35 item.

## 3. Rulings needed

**RULE-1 — which code (and stage) does a same-claimant overlap carry?** The code emits
`SC-CLM-0001` at `Stage.SETTLE` for two bays of one `(owner, siting)` (`ClaimMatrix.java:119-131`,
`OVERLAP_CODE`, `Stage.SETTLE`), but `13:332`/`08` §4.6 define `SC-CLM-0001` as "two `EXCLUSIVE` claims of
**different owners**", and `08` §4.2 assigns the static half (`Box`, `Declared`) to `SC-BPT-0002` at
`DESIGN`. Options: **(A)** broaden `0001` to "two rows of one settle request that cannot coexist,
whatever their owners", keep `SC-BPT-0002` as the `DESIGN` static-overlap code; **(B)** leave `0001`
alone and allocate a new `SC-CLM` code for a same-claimant pair of bays; **(C)** split by scope —
`Box`/`Declared` becomes `SC-BPT-0002` at `DESIGN` (the blueprint builder gains the pass, which the
code does not have at all today), `Bound` reaches `SETTLE` and takes the broadened `0001`.
*Recommendation:* (A) — it is the only option that needs no new code, matches what the code already
does, and keeps `DESIGN` free of a claim row; it costs one sentence in `13:332` and `08` §4.6.

**RULE-2 — does a request's own self-conflict precede the revision check?** `InMemoryClaimLedger.settle`
scans `internalOverlaps` at `:38` *before* reading the revision at `:45`, so a stale-revision request
that overlaps itself gets `ClaimConflict`, not `RevisionMoved`; `23` §3.1's order is 3b bindings → 3c
derive → 3d revision → 3e ledger/matrix. Options: **(A)** request self-consistency first (the code: a
request contradicting itself is a caller bug and a retry cannot fix it); **(B)** revision staleness
first (the spec's step order: never report a conclusion about rows the store has already replaced);
**(C)** both, but `RevisionMoved` for a *self*-conflicting request carries no retry token.
*Recommendation:* (A), stated in `08` §4.5 and `23` §3.1 — otherwise `RevisionMoved` invites a retry
loop that can never succeed.

**RULE-3 — who produces an overlap finding, the planner or the ledger?** `08` §3/§4.2 give the planner
`Derived(rows, overlaps, staticFootprint)` and make it a caller of `ClaimMatrix`; nothing in the spec
says who turns `overlaps` into findings, and the code produces them inside the ledger's `settle`
(`InMemoryClaimLedger.java:38-42`) with the planner never calling the matrix. Options: **(A)** the
planner returns `overlaps` *and* the ledger keeps its own scan, as a defence for direct `ClaimLedger`
callers (the ledger is a host SPI: `15` §3 exposes it on `Host`, so a host can call `settle` with rows
it built); **(B)** the planner is the only producer and the ledger trusts its request; **(C)** the
planner drops `overlaps`/`staticFootprint` and the ledger keeps the scan only.
*Recommendation:* (A), and say in `08` §4.5 that the ledger's scan is a re-check, not a second
predicate; (B) leaves a host-supplied row set unchecked, (C) costs `check` its conflict prediction
(`23` §3.1 step 1).

**RULE-4 — which family owns an R3-kind or R4-completeness violation?** `24` §5 assigns R3 →
`SC-BAY-0107` and R4 → `SC-CHK-0106`; `13:342` gives `BAY-0107` R3's kind half, `13:349` gives
`CHK-0106` "checks that do not match the coverage (R4)", and `13`'s family table gives `BAY` the subject
"bay coverage" while giving `CHK` "coverage match". The code emits `CHK-0106` for a kind mismatch
(`Bay.java:82`) and `BAY-0107` for incomplete coverage (`:74`) and for "a check decides cells outside
bay" (`:58`), a case neither table row names. Options: **(A)** by symptom — anything about *checks not
matching the bay's declaration* is `CHK-0106` (kind, coverage completeness), anything about the *bay's
geometry* is `BAY-*` (the bound-footprint equality stays `BAY-0107`, and the new "decides cells outside
bay" becomes `SC-BAY-0108`'s sibling); **(B)** follow `24` §5 literally and make the code emit
`BAY-0107` for R3-kind and `CHK-0106` for R4-completeness; **(C)** one code for all four.
*Recommendation:* (A) with `13:342`/`13:349` rewritten to split R3 into its two halves — it is the only
option that also gives the code's unlisted `:58` case a home, and it matches the code except for the
undecided-cells throw.

## 4. Documentation gaps that block regeneration

### 4.1 Undocumented behaviour (the spec must gain it or the regeneration loses it)

Public surface in the slice that no spec line mentions:

- `ClaimMatrix`: `conflictsWith(ClaimRow, Collection<ClaimRow>)`, `rowsOf(List<Overlap>)`,
  `overlapFindings(List<Overlap>)`, `overlapFinding(Overlap)`, the two-argument
  `conflictFinding(challenger, incumbent)` overload (locus = the challenger's single cell),
  the public nested `Overlap` class with `challenger()`/`incumbent()`/`cells()`, and the constants
  `OVERLAP_CODE`/`CONFLICT_CODE`. `08` §3 shows only `conflicts`.
- `ClaimRow.ordered(List<ClaimRow>)` — the canonical-order helper every constructor calls; `08` §4.1
  states the order but never names the only implementation of it.
- `ClaimSet.rowsIn(Cuboid)`, `ClaimSet.EMPTY`, and the negative-revision `IllegalArgumentException`.
- `ClaimView.rowsIn(Cuboid)`, `ClaimView.unavailable()`, and the "an unavailable view holds no rows"
  invariant (also D10).
- `SettleRequest`'s check that every row's `(owner, siting)` equals the request's
  (`SettleRequest.java:17-21`, `IllegalArgumentException("row outside the request's claimant: ")`).
  The spec states no such rule and has no code for it; R35's `SC-CLM-0007` covers *world* disagreement
  as a `Finding`, not a throw. A regenerated kernel would silently accept a foreign row — or, worse,
  drop it during replacement.
- `ClaimMode.excludes()` / `ClaimScope`'s four names (D12), and the fact that `ClaimVerdict.Settled`
  and `ClaimConflict` re-sort their rows.
- The **order of operations inside `settle`** (`InMemoryClaimLedger.java:35-56`): self-overlap scan →
  revision compare → conflict scan → CAS, with the CAS retried in a loop while a concurrent writer
  moves the revision. `08` §4.5 says "one CAS"; the loop and the scan order are policy (RULE-2).

### 4.2 Unimplementable spec (blocks regeneration even though the code is absent)

- **`ClaimView` has no carrier** (D10): `08` §3 declares an interface; nothing in the spec constructs an
  instance, and only a constructor can produce `available() == false`. `22:626`'s `@Nullable ClaimView`
  is a competing channel; one of the two must go.
- **`SC-CLM-0005`'s capture check has no carrier today.** `08` §4.5/R36 require "each subject it carries
  was observed in **this** capture (`OccupantRef.snapshot`, `19` R8)"; the code's
  `OccupantRef(InstanceId instance, NamespacedId type, OccupantKind kind, LatticePoint anchor, Cuboid footprint)`
  has no snapshot (`06:40`/`01:70`/R8 say it must), and `BayBinding(NamespacedId bay, List<OccupantRef>,
  Bindings)` carries no capture identity either. The check is writable only after R8; until then
  `SC-CLM-0005` is unimplementable, and a regenerated kernel would need a statement of *which* capture
  identity it compares (the request's lease? the subject's snapshot?).
- **`Derived.overlaps` / `Derived.staticFootprint` have no producer or consumer** (RULE-3): `08` §3
  returns them from `ClaimPlanner.derive`, and no spec sentence says who turns an `Overlap` into a
  finding or who reads a static footprint (`08` §5's sketch reads `d.staticFootprint()` into
  `ledger.read`, so at least that half has a use). `ClaimPlan.Overlap(NamespacedId low, NamespacedId
  high)` is a nested record inside the sealed `ClaimPlan` that is not a permitted subtype — legal but a
  name/identity mismatch with the code's `Overlap` (rows, not ids).
- **`Footprint.resolve`** is required by `24` §9.2 ("One sealed `Footprint` (with `resolve` and the
  known-when information)") and declared nowhere in `08` §3 or `09:58-62`: no signature, no owner, no
  return type. The only resolution logic that exists is `ClaimPlanner.claimedCells` (private static).
- **The `(owner, siting, bay)` claimant key vs `(owner, bay)`** (D7): the spec's grouping key is not
  unique, so "one finding per pair" is undefined for two placements of one owner.

### 4.3 Package / unit mismatch

- `03-naming.md:39` reads "| `kernel.claim` | claim model, ledger, **planner** | base, space |" and
  `00` §5 gives U4 = `kernel.claim`. The code puts `ClaimPlan` and `ClaimPlanner` in
  `kernel.engine` (`ClaimPlanner.java:1` `package dev.vfyjxf.cumulus.kernel.engine;`).
  **The code is right and `03:39` must change**: under the migrated vocabulary `Bay` carries
  `Sharing`/`Footprint` (`09:48-49`), so `kernel.blueprint` depends on `kernel.claim`; a planner that
  reads `Blueprint` and `BayBinding` cannot live in `kernel.claim` without making that a cycle, and
  `03:39`'s dependency list ("base, space") would have to gain blueprint either way. A regenerated tree
  from `03:39`/`00` §5 would place the planner in `kernel.claim` and hit the cycle.
- Otherwise U4 is the one unit whose spec package exists in the code. The cross-cutting fact for the
  lead: the code has `kernel.base|blueprint|claim|context|engine|occupant|predicate|space|spi` — no
  `kernel.expectation` (`predicate`), no `kernel.document`, no `kernel.finding` (base +
  `Finding*`), no `kernel.remedy`. `SavedClaims` and `FrozenLedger` (`01:301/315`, `16` §4.6) exist
  nowhere in the tree: `grep -rl "ClaimMatrix\|ClaimLedger\|SavedClaims" src/main/java` returns nothing,
  so the adapter has no claim code at all and `08` §4.2's "the kernel planner and `SavedClaims` both call
  it" has no second caller.

## 5. Checked and aligned

- **`conflicts`' `(owner, siting)` exclusion is correct as far as it goes**: `ClaimMatrix.java:41-44`
  returns false for one `(owner, siting)` and true when either side `excludes()`, which matches `08`
  §4.1's replacement rule (two placements are two claimants; a re-check is one claimant) and
  `01:121`. Only the world term (D11) and the order key (D3) are missing.
- **Rows of two worlds never conflict** cannot be checked in the code (no world), but nothing in the
  slice compares coordinates across worlds, so the *absence* is a single R35 item, not a second defect.
- **The replacement rule is implemented exactly**: `InMemoryClaimLedger.replaced` drops every stored row
  of the request's key and adds the request's rows, with no per-row merge (`§4.1`, P3) — and the CAS
  loop re-reads the store each iteration, which is what makes P4 ("no provisional row") hold.
- **One conflict predicate in the whole tree**: `grep` finds `ClaimMatrix` referenced only from
  `InMemoryClaimLedger`; the adapter has no predicate of its own (`08` §4.2's invariant holds, modulo
  the planner attribution in D13/RULE-3).
- **`SC-CLM-0001`'s and `0002`'s finding shape** matches `13`'s `P` column and `08` §4.6: both carry a
  `PRIMARY` label plus a `SECONDARY` (`ClaimMatrix.java:119-132`, `:141-155`), one finding per pair, one
  `CuboidSet` locus of the contested cells, message naming both claimants.
- **`Coverage` × `ClaimScope` legality for the rows the whitelist *does* accept** (`CUBIC` × anything,
  `SUBJECT` × `Bound`/`Declared`/`None`, `PRESENCE` × `Declared`/`None`, plus `PRESENCE + EXCLUSIVE`
  requiring `Declared`) is a consistent reading of the twelve-row whitelist; the whitelist itself is
  D2, not a second defect.
- **R3's footprint half is implemented and carries the right code**: `ClaimPlanner.subjectMismatch`
  refuses a `SUBJECT` bay whose bound cells ≠ its translated `bay.cells` with `SC-BAY-0107`
  (`ClaimPlanner.java:40`, `:88-94`), exactly `24` R3 + `13:342`'s second clause.
- **`Bound` is resolved from the binding's subject footprints, not from the bay cuboid**
  (`ClaimPlanner.java:105-112`), so `08` §4.3's pipe corner and `24` §4 Q2 hold in the code.
- **`read`'s `available()` and the revision-independent `revision()`** match `08` §3/§4.7: the in-memory
  ledger always answers (`available() == true`), and `revision()` reads the store, not a box.
- **`23` §3.2's diagnosis of the retry is accurate**: `MatchSettler.java:80-82` swaps
  `moved.actual()` for the expected revision with no re-read, and safety survives only because
  `InMemoryClaimLedger.conflictFindings` runs inside the CAS loop.
