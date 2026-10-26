# 08 — Claims

> Serves R-11, R-08 (`settle` is its own step), I-10 (a conflict is a `Finding`, never a
> silent overwrite). Depends on `base`, `space`; the plan half reads `blueprint`. Aligned with
> `22` revision 5 §10: two axes, one predicate, one CAS.

## 1. Scope

**Owns** claim identity, the two axes, the matrix, the scope footprints, the `ClaimLedger` SPI,
the plan and planner, the `SC-CLM` codes. **Does not own** the bay's `Coverage`, `Footprint` and
`Sharing` declaration (U5), coverage legality (U3/U7, `SC-CHK-0106`), findings (U9), persistence
(host, A1). **Is not** a lock: a claim is a value.

## 2. Vocabulary

| Name | Shape and meaning |
| --- | --- |
| `Sharing` | `EXCLUSIVE`, `SHARED`: whether this claim excludes another over the same cell |
| `Footprint` | `Box`, `Bound`, `Declared`, `None`: which cells a bay owns |
| `ClaimRow` | `(world, cell, sharing, owner, siting, bay)`: one cell of one world held by one bay of one owner at one siting |
| `ClaimSet` | `(rows, revision)`: one whole settlement, rows in canonical order (`ClaimRow.ordered`) |
| `ClaimConflict` | `(rows, findings)`: the held rows that block the settle, plus the request's own conflicting rows when the conflict is inside one request, plus the findings naming them |
| `ClaimMatrix` | the one conflict predicate, `conflicts(a, b)`: one world, one cell, different ownership, at least one `EXCLUSIVE` |
| `ClaimLedger` | host store, one handle per world: `world()`, `settle(SettleRequest)`, `release(OwnerId, Siting)` and `read(Cuboid)` |
| `ClaimView` | one read: the rows intersecting a box, the revision read, and whether the store answered |
| `SettleRequest` | `(world, owner, siting, rows, expectedRevision)`: the unit of the single CAS |
| `ClaimPlan` | what a `check` found — the bindings per bay and the ledger revision it read; **no rows**, because every row is derived by `ClaimPlanner`, so no caller can hand `settle` rows it built itself |
| `ClaimPlanner` | the only row factory: `derive(World, Blueprint, Siting, OwnerId, bindings)`, pure and deterministic |

A `ClaimRow` carries its world and its siting: `ClaimMatrix.conflicts` compares the world first and
then excludes two rows of one `(owner, siting)`, so both the world and the settlement key travel on
the row (§4.1).

## 3. API design

```java
public enum Sharing {
    EXCLUSIVE, SHARED;
    public boolean blocks() { return this == EXCLUSIVE; }
}

public sealed interface Footprint permits Box, Bound, Declared, None {
    record Box() implements Footprint {}                         // the bay cuboid, air included
    record Bound() implements Footprint {}                       // union of bound non-air footprints
    record Declared(CuboidSet cells) implements Footprint {}     // exactly these cells
    record None() implements Footprint {}                        // no cell
}

public record ClaimRow(World world, LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting,
                       NamespacedId bay) {}

public record ClaimSet(List<ClaimRow> rows, long revision) {
    public ClaimSet { rows = ClaimRow.ordered(rows); }           // canonical order, §4.1
}

public sealed interface ClaimVerdict permits Settled, ClaimConflict, RevisionMoved {
    record Settled(long revision, List<ClaimRow> rows) implements ClaimVerdict {
        public Settled { rows = ClaimRow.ordered(rows); }
    }
    record ClaimConflict(List<ClaimRow> rows, FindingBundle findings) implements ClaimVerdict {
        public ClaimConflict { rows = ClaimRow.ordered(rows); }
    }
    record RevisionMoved(long expected, long actual) implements ClaimVerdict {}
}

public record SettleRequest(World world, OwnerId owner, Siting siting, List<ClaimRow> rows,
                            long expectedRevision) {}

public final class ClaimMatrix {
    public static boolean conflicts(ClaimRow a, ClaimRow b);      // §4.2; another world or one (owner, siting) never conflicts
}

public interface ClaimLedger {                                    // @NullMarked; settle and release are the only writes
    World world();                                                // the one world this handle can answer for
    long revision();                                              // ledger-wide CAS base, independent of a box
    ClaimVerdict settle(SettleRequest request);                   // one CAS over every row of (world, owner, siting)
    ClaimVerdict release(OwnerId owner, Siting siting);            // delete every row of the key; bumps the revision
    ClaimView read(Cuboid box);                                   // never null; an empty answer is available
}

public record ClaimView(List<ClaimRow> rows, long revision, boolean available) {
    public ClaimView {                                            // an unavailable view holds no rows
        rows = ClaimRow.ordered(rows);
        if (!available && !rows.isEmpty()) throw new IllegalArgumentException("an unavailable view holds no rows");
    }
    public static ClaimView unavailable() { return new ClaimView(List.of(), 0L, false); }
    public List<ClaimRow> rowsIn(Cuboid box);                     // rows intersecting the box, canonical order
}

public sealed interface ClaimPlan permits ClaimPlan.Matched, ClaimPlan.Invalid {
    /** What a check found, and the ledger revision it read. No rows: they are derived, never handed over. */
    record Matched(long ledgerRevision, List<BayBinding> bindings) implements ClaimPlan {}
    record Invalid(FindingBundle findings) implements ClaimPlan {}  // nothing may be written
    record Overlap(NamespacedId low, NamespacedId high) implements Comparable<Overlap> {}
}

public final class ClaimPlanner {
    /** The only row factory: pure, deterministic, no view, no ledger, no I/O. */
    public static Derived derive(World world, Blueprint blueprint, Siting siting, OwnerId owner,
                                 Collection<BayBinding> bindings);
    public record Derived(List<ClaimRow> rows, List<ClaimPlan.Overlap> overlaps,
                          CuboidSet staticFootprint) {}
}
```

Values are immutable records, structurally equal, never `null`. `Sharing` ordinals are never
compared: there is no strength order. Ledger cost is per row **returned**, not per cell queried.

## 4. Semantics

### 4.1 Identity and order

The claimant is `(world, owner, siting, bay)`: two placements are two claimants, two bays of one
blueprint are two claimants, a re-check at the same siting is the same claimant. The world and the
siting are components of the row, so the settlement key `(world, owner, siting)` is read off the rows
themselves.

| Thing | Total order |
| --- | --- |
| cell | U1's canonical order (`LatticeKey` natural order) |
| `ClaimRow` | `(world, cell, owner, bay)`, implemented once by `ClaimRow.ordered(List<ClaimRow>)`; the sharing value never breaks a tie |
| `ClaimSet.rows`, `Settled.rows`, `ClaimConflict.rows` | the `ClaimRow` order |

**Replacement rule.** `settle` replaces *every* row of `(world, owner, siting)` in one CAS: there is
no per-row merge, no per-bay skip, and no comparison of a request against its own incumbent rows. A
re-check at the same siting reproduces its set and bumps the revision. A *moved* structure is the same
owner at a different siting: the replacement cannot reach the old key, so its former rows survive and
an `EXCLUSIVE` placement conflicts with them until the host settles or clears that key. A structure
that moves to another world is a release in the old world and a settle in the new one.

### 4.2 The claim matrix

Two rows of one world conflict **iff at least one side is `EXCLUSIVE`.** Rows of two worlds never
conflict: the same coordinates in two worlds are two different cells, and one world's ledger is the
only place its cells exist.

| `a` \ `b` | `EXCLUSIVE` | `SHARED` |
| --- | --- | --- |
| `EXCLUSIVE` | **conflict** | **conflict** |
| `SHARED` | **conflict** | coexist |

| Pair | Why |
| --- | --- |
| `EXCLUSIVE` / `EXCLUSIVE` | both demand sole occupancy of one cell |
| `EXCLUSIVE` / `SHARED` | shared still *uses* the cell; raise the shared side, never downgrade it |
| SHARED pairs | neither excludes, so nothing is decided here; the cell is simply shared |

`ClaimMatrix.conflicts(a, b)` is the only conflict function: the kernel planner and `SavedClaims`
both call it, and the adapter has no predicate of its own. It compares the world and the cell first,
then the sharing values, and returns false for two rows of one `(owner, siting)`, so `settle` can
replace a claimant's rows without flagging them against themselves. The predicate is symmetric; the report is directed (`a` the
challenger, `b` the incumbent). A settlement conflict is `Failed` with `SC-CLM-0002`: no advisory
relaxation, and `check` reports the same rows as a prediction rather than as a conclusion.

**Internal overlaps.** Two bays of one blueprint are two claimants, so the matrix applies unchanged:
an overlap is legal iff the sharing values coexist. `SHARED` pairs are silent; the static half
(`Box`, `Declared`) is a blueprint-design error decided at `DESIGN`, where no claim row exists yet,
and it is `SC-BPT-0002`, locus the intersection cells; an `EXCLUSIVE` pair of one blueprint is that
same `SC-BPT-0002`, never a ledger conflict, because `ClaimMatrix.conflicts` is false for two rows
of one `(owner, siting)`. The `Bound` half needs the bound footprints and is decided at `SETTLE`
(§4.3).

### 4.3 Footprints and scopes

| Scope | Owned cells | Resolved |
| --- | --- | --- |
| `Bound` (default) | the union of **non-air** bound footprints | `SETTLE`, from `BayBinding.subjects()` |
| `Box` | `bay.cells` | `DESIGN` |
| `Declared` | the declared `CuboidSet` | `DESIGN` |
| `None` | ∅; reads anywhere | never |

- `Bound` is the union of the bound subjects' footprints, minus air. A `ONE_SUBJECT` bay is refused when that union differs from its translated `bay.cells` (`SC-BAY-0107`), so a half-bound door never becomes a plan. It is **not** the bay cuboid
  and **not** what the checks read: the footprint comes from the bound subjects'
  `OccupantSnapshot`s (`22` §5.1), not from a world read.
- Claims protect what a structure **occupies or declares**, never what it merely reads. An empty volume
  that must be protected declares `Box` (the furnace cavity); a mixed or irregular protection
  declares `Declared`; a criterion that must not claim anything is `None`.
- **The pipe corner.** A 2×2×2 bay with four pipe blocks and four must-be-air cells is `EVERY_CELL` +
  `Bound`: eight cells asserted, four owned. The neighbour's corner owns its own four cells at the
  other cells of the box, so the two settlements do not conflict. No `allOf` workaround, no forced
  `SHARED` sharing value.
- **The residue is named.** A `bounds` cell that no non-`None` bay could own — outside every bay's
  `cells` and outside every `Declared` set — gets `SC-CLM-0104` (WARNING) at `DESIGN`. Because
  `Bound` is not resolved there, the same code is re-evaluated at `SETTLE` against the bound
  footprints, and it stays a WARNING both times. This is a decision, not a hole: the warning is the
  visible record of the choice, and the only signal for an unprotected empty volume.
- `SC-BAY-0108` (ERROR) fires at `SETTLE` when a non-`None` bay's `reads().cells` leave
  `bay.cells ∪ resolved footprint`. The DESIGN-time version was vacuous for `Bound` and is
  withdrawn; an `None` bay is exempt because it owns nothing and may read anywhere.

### 4.4 Stages

`DESIGN` validates the two axes and the static footprint; the world pass (`PROBE`, `FORM`) never
touches the ledger. `check` reads the ledger once, writes nothing, and returns the planned claim with
the revision it read; `settle` performs the write on the server thread (§4.5), and only then may a
`Formed` exist. A targeted re-check is `check` with a `ChangeSet` on the request, and `release`
removes an instance's rows (23 §5).

Coverage and claims stay two axes: `Coverage` is what a MATCH asserts, `Sharing` ×
`Footprint` is what it owns, and the legality table (`22` §6.5) is enforced at `DESIGN`. A `EVERY_CELL`
bay asserts every cell of `bay.cells`; `EXCLUSIVE` needs a footprint other than `None`
(`SC-CHK-0106`, R1). Whether a presence-only `ANY_SUBJECT` claim may be `EXCLUSIVE` at all stays
open (`22` §16 Q6).

### 4.5 Settlement through the `settle` entry point

- The matcher emits a `ClaimPlan` — the bindings it found per bay plus the ledger revision it read, and
  **no rows** — and writes no row. `check` returns that plan on `Admitted`; `settle(request, admitted)`
  derives the rows and applies them. Settlement is a step of that call, not work for the matcher
  worker, so no claim is ever written off the server thread.
- **The rows are derived, never handed over.** `settle` runs `ClaimPlanner.derive(...)` over the
  request's `(world, blueprint, siting, owner)` and the plan's bindings, so what reaches the ledger is a
  function of the blueprint and the capture, not of the caller's data (I-16). A `ClaimPlan` that
  `check` did not produce is therefore harmless instead of dangerous: there is nothing in it to forge.
- **The bindings are checked structurally, before the matrix**: each names a bay this blueprint
  declares at this siting; each subject it carries was observed in **this** capture
  (`OccupantRef.snapshot`, `19` R8); its kind matches the bay's expectation; and its cells lie inside
  `bay.cells` — exactly equal for `ONE_SUBJECT` (R3). A failure is `Failed(SC-CLM-0005)` and nothing is
  written.
- `settle` does not re-run the expectations. That verdict is `check`'s, and the capture gate pins the
  world to it (`20` §5.4), so a binding that was real when `check` ran is still real, and one
  that was never real is caught by the capture check above rather than by a second match.
- `14`'s `EditPlan` carries a `PlanDigest` because it travels from a validation to a later application
  across a human decision; a `ClaimPlan` carries none, deliberately: with no rows in it there is
  nothing for a digest to protect.
- The ledger's `settle` is one CAS over all rows of `(world, owner, siting)`, conditional on `expectedRevision`.
- On `RevisionMoved` the settler re-reads and re-checks **once** inside the transaction; a second
  movement is `Undecided(LEDGER_MOVED)` with a retry token, and never an invented conflict.
- `Formed` exists only after `Settled`, and carries the `ClaimSet` plus the settled revision. A conflict
  is `Failed(SC-CLM-0002)` carrying the held rows that block, and the request's own rows as well when
  the conflict is inside one request; an unanswerable ledger is `Undecided`.
- `release(owner, siting)` — on a handle for one world — deletes every row of that key in one
  transaction and bumps the revision; a released key reads empty, and the three lifecycle points that
  call it — instance removal, a move, a world unload — are `23` §5's.
- The kernel does no I/O (I-15): `ClaimLedger` is the host's transaction. The adapter implements the
  CAS as one transaction that deletes every row of `(world, owner, siting)` and inserts the request's rows
  when the revision matches, so a crash mid-write is healed by the next settle.

### 4.6 Conflict findings

One finding per **(owner, bay) pair**, not per cell: the contested cells form one `CuboidSet` locus, the
primary label sits on the canonically first contested cell, a note gives the count.

| Code | Sev | Stage | Meaning |
| --- | --- | --- | --- |
| `SC-CLM-0001` | ERROR | SETTLE | two `EXCLUSIVE` claims of different owners name the same cell |
| `SC-CLM-0002` | ERROR | SETTLE | this bay asks `EXCLUSIVE` where another owner holds |
| `SC-CLM-0003` | ERROR | SETTLE | this `SHARED` bay meets another owner's `EXCLUSIVE` |
| `SC-CLM-0004` | WARNING | SETTLE | the ledger cannot answer, or the revision moved twice: no conclusion |
| `SC-CLM-0005` | ERROR | SETTLE | a settle's bindings are not valid for this request: a bay that is not this blueprint's, a subject of another capture, a kind that does not match, or cells outside the bay; nothing is written |
| `SC-CLM-0006` | INFO | SETTLE | claims produced by a settle were returned unused because the attempt did not form |
| `SC-CLM-0104` | WARNING | DESIGN / SETTLE | part of `bounds` is owned by no bay's scope |
| `SC-BPT-0002` | ERROR | DESIGN | two bays of one blueprint overlap, at least one `EXCLUSIVE` (U5 family, cited here) |
| `SC-BAY-0108` | ERROR | SETTLE | a non-`None` bay reads outside `bay.cells ∪ footprint` (U7 family, cited here) |
| `SC-BAY-0111` | ERROR | SETTLE | an `EXCLUSIVE` bay with a `Bound` footprint bound nothing (rule R2) |

`0002`/`0003` split on *who asks too much*; exactly one fires per pair. Message, both claimants literal:
`<ownerA>:<bayA> (<sharingA>) and <ownerB>:<bayB> (<sharingB>) both claim cell (x, y, z)`. Both labels sit on
the contested cell — `PRIMARY` the challenger's request, `SECONDARY` the incumbent's hold.

### 4.7 Ledger behaviour

| Situation | Reading | Outcome |
| --- | --- | --- |
| `read` → `available() == false` | cannot check ownership | `Undecided(LEDGER_UNAVAILABLE)`, `SC-CLM-0004` |
| `read` → available, no rows in the footprint | readable, nothing claimed here | normal settlement |
| `expectedRevision` ≠ store revision | the store changed under the attempt | `RevisionMoved`; one re-read and re-check, then `Undecided(LEDGER_MOVED)` with a retry token |

An unavailable ledger is **not** an empty ledger: reading it as empty grants exclusive rights
fail-open. `Undecided` says "no conclusion about ownership"; `Failed` would assert the structure is
wrong.

## 5. Reference sketch

```java
// 1. One shared hatch cell, two machines: no conflict.
ClaimRow a = new ClaimRow(world, hatch, Sharing.SHARED, ownerA, atA, NamespacedId.of("mymod", "hatch"));
ClaimRow b = new ClaimRow(world, hatch, Sharing.SHARED, ownerB, atB, NamespacedId.of("mymod", "hatch"));
boolean coexists = !ClaimMatrix.conflicts(a, b);                       // true

// 2. An exclusive wall: the incumbent keeps the cell, the challenger is told.
ClaimRow incumbent  = new ClaimRow(world, cell, Sharing.EXCLUSIVE, ownerA, atA, NamespacedId.of("mymod", "wall_a"));
ClaimRow challenger = new ClaimRow(world, cell, Sharing.SHARED,    ownerB, atB, NamespacedId.of("mymod", "wall_b"));
if (ClaimMatrix.conflicts(challenger, incumbent)) {
    Finding finding = conflictFinding(challenger, incumbent, at);      // §4.6, a = challenger
}

static Finding conflictFinding(ClaimRow challenger, ClaimRow incumbent, Locus at) {
    return Finding.builder(FindingCode.of("SC-CLM-0002")).stage(Stage.SETTLE)
            .message(incumbent.owner() + ":" + incumbent.bay() + " (" + incumbent.sharing() + ") and "
                    + challenger.owner() + ":" + challenger.bay() + " (" + challenger.sharing()
                    + ") both claim cell " + challenger.cell())
            .label(at, LabelRole.PRIMARY, challenger.bay() + " requests " + challenger.sharing())
            .label(at, LabelRole.SECONDARY, incumbent.bay() + " already holds it as " + incumbent.sharing())
            .build();
}

// 3. The settle step derives the rows itself. The plan only says what the check found and at which
//    revision; nothing here can be handed in from outside.
ClaimPlan.Matched matched = (ClaimPlan.Matched) plan;
ClaimPlanner.Derived d = ClaimPlanner.derive(request.world(), request.blueprint(), request.siting(),
                                             request.owner(), matched.bindings());
ClaimView before = ledger.read(d.staticFootprint());
// The bindings are checked first (SC-CLM-0005): a bay of this blueprint, subjects of this capture,
// matching kinds, cells inside the bay — then the matrix, then one CAS.
ClaimVerdict verdict = ledger.settle(new SettleRequest(request.world(), owner, siting, d.rows(),
                                                       matched.ledgerRevision()));
if (verdict instanceof ClaimVerdict.RevisionMoved moved) {
    ClaimView fresh = ledger.read(d.staticFootprint());
    verdict = fresh.available()
            ? ledger.settle(new SettleRequest(request.world(), owner, siting, d.rows(), fresh.revision()))
            : new ClaimVerdict.RevisionMoved(moved.expected(), fresh.revision());
}
MatchResult result = switch (verdict) {
    case ClaimVerdict.Settled s        -> new Formed(Stage.SETTLE, instance, siting, outcome,
                                             new ClaimSet(s.rows(), s.revision()), …);
    case ClaimVerdict.ClaimConflict c  -> new Failed(Stage.SETTLE, c.findings());
    case ClaimVerdict.RevisionMoved m  -> new Undecided(Stage.SETTLE, UndecidedReason.LEDGER_MOVED,
                                             List.of(movedFinding(m.expected(), m.actual())),
                                             null, RetryToken.of(m.expected(), m.actual()));   // RetryToken.of(long expected, long actual)
};
```

## 6. Use cases

**Addon author, a hatch** declares `SHARED`: two furnaces on one hatch settle with two `SHARED` rows and
no finding; `EXCLUSIVE` makes the second furnace `SC-CLM-0002`. **Addon author, a two-cell door** writes
one bay with `Bound`: the two rows come from the door's two subjects, so both cells are owned or
neither. **Addon author, a pipe corner** writes `EVERY_CELL` + `Bound` and gets eight asserted cells and
four owned ones, with no forced `SHARED` sharing value. **Host, a build wand** runs the facade `settle` before
editing and blocks on any conflict; **a datapack reload** runs `DESIGN`, which reports `SC-BPT-0002` and
`SC-CLM-0104` with no world and no store; **`/cumulus claims`** prints `read(cellBox).rows()`.

## 7. Proofs

| Proof | Argument |
| --- | --- |
| P1 conflict symmetry | `ClaimMatrix.conflicts` is false for two rows of different worlds, and over one world it is true exactly when either sharing value excludes; disjunction is symmetric, so `conflicts(a, b) == conflicts(b, a)`, and the matrix is total over the `Sharing` pairs |
| P2 no silent overwrite | The ledger's two writers, `settle` and `release`, each CAS over the whole `(world, owner, siting)` key; the matrix is total over sharing pairs, so an incompatible pair always yields `ClaimConflict` with its rows and findings and no row of the request is written. Another owner's rows are unreachable from that key |
| P3 replacement idempotence | `ClaimPlanner.derive` and `overlaps` are pure, and `settle` replaces all rows of the key: re-settling at one siting reproduces the row set and moves only the revision, and no row of a request is ever compared against its own incumbent |
| P4 no provisional row | No row exists before `Settled`; `Failed` and `Undecided` leave the store at its prior revision, so a reader never sees a claim the writer abandoned |
| P5 determinism | Rows sort by `(world, cell, owner, bay)`, findings group by `(owner, bay)` and order by the first contested cell; both compose total orders, independent of iteration and hash order. Internal overlaps are the same matrix applied to bays that differ in `bay` |
| P6 four ownerships, one resolution | Each scope names its cells before any comparison: `Box` = `bay.cells`, `Declared` = the declared set, `Bound` = bound non-air footprints, `None` = ∅. A read outside `bay.cells ∪ footprint` is `SC-BAY-0108` at `SETTLE`, so no bay reads where it does not own without a finding |
| P7 derived, never handed over | Every row a settle writes comes from `ClaimPlanner.derive` over the request's `(world, blueprint, siting, owner)` and the plan's bindings, and the plan has no row field; each binding is checked against this blueprint and this capture (§4.5), so a caller can neither invent a cell nor name another capture's subject (I-16) |

## 8. Dependencies and open questions

| Dependency | Assumption |
| --- | --- |
| U1 `space` | `LatticeKey` comparable with `toPoint()`; `CuboidSet.of/intersection/union`; `Siting.origin()/placement()` |
| U5 `blueprint` | `Blueprint.bays()` in declaration order; `Bay.id()/cells()/coverage()/footprint()/sharing()` |
| U3/U7 `expectations`, `engine` | `BayBinding.subjects()` from `Outcome.Found`; the matcher hands the facade a `ClaimPlan`, never a write |
| U9 `finding` | `SC-CLM` strings and the `(owner, bay)` pairing live in the bundle |

**Open questions**, restricted to what `22` §16 leaves open for this unit. (1) **§16 Q1, empty-volume
protection**: is a per-blueprint `sealed: true` flag better than the `SC-CLM-0104` warning plus a per-bay
scope choice? (2) **§16 Q4, `RevisionMoved` retry**: §10.1 already fixes one in-transaction retry; what
stays open is whether a second movement should carry a retry token or be a hard `Undecided`. (3) **§16
Q6, `Coverage.ANY_SUBJECT`**: a presence-only bay asserts no cells, so can it ever be `EXCLUSIVE` without a
hole?

Not a §16 question: `Undecided(LEDGER_MOVED)` carries its retry token on
`Undecided.retry` (`22` §10.4), and `Deferred` is the arm a caller takes when it wants to resume
later rather than retry now.

## 9. Falsification

| Falsifier | What it breaks |
| --- | --- |
| A conflict only one side sees | Settle one contested cell from both placements in both orders: a differing `ClaimConflict` row set, or a ledger answering one box differently per asker, breaks P1/P5 |
| A row written by a failed attempt | Make settlement conflict, drop the ledger, and move the revision twice: the store's row count and revision must be unchanged after `Failed`/`Undecided`, or P4 falls |
| A `Bound` bay owning its whole box | A pipe-corner bay (`EVERY_CELL` + `Bound`) conflicts with the neighbour's corner, or owns eight cells instead of four, means `Bound` was resolved from the bay cuboid |
| A self-conflict on re-check | Re-settle at one siting: any conflict, or a row set that differs from the first settles, breaks §4.1's replacement rule |
| An unavailable ledger read as empty | With `available()` false, the outcome must be `Undecided(LEDGER_UNAVAILABLE)`, never `Formed`, never `Failed` |
| A shared claim blocking an exclusive one | A `SHARED`/`SHARED` conflict, or a two-`SHARED` blueprint failing `DESIGN`, means the matrix was read as a strength order; an `EXCLUSIVE` challenger against a `SHARED` incumbent must be `SC-CLM-0002`, not `0003` |
