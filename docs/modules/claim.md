# Module `kernel.claim`

> Rank 3. What a claim is, which cells a bay owns, how two claims meet, and the one-per-world ledger
> port. The module is values and a matrix; the row factory lives in `engine`, the bay declaration in
> `structure`.

## 1. Scope

**Owns:** `Sharing`, `Footprint`, `ClaimRow`, `ClaimSet`, `ClaimView`, `ClaimVerdict`,
`SettleRequest`, `ClaimLedger`, `ClaimMatrix`.

**Does not own:** which cells a check binds (`structure`), how rows are derived (`engine`), finding
text (`finding`), persistence (`mc`), the bay's `Coverage` (`structure`). A conflict carries a
`FindingBundle`; the engine builds its findings.

**Imports:** `core`, `space`, `finding` (the bundle only).

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `Sharing` | enum | `exclusive`, `shared`; `blocks()` is true only for exclusive |
| `Footprint` | sealed | `Box`, `Bound`, `Declared`, `None`: which cells a bay owns |
| `ClaimRow` | record | one cell of one world held by one bay of one owner at one siting |
| `ClaimSet` | record | the rows of one settlement + the revision written |
| `ClaimView` | record | a read answer: rows, revision, availability |
| `ClaimVerdict` | sealed | `Settled`, `Conflict`, `RevisionMoved` |
| `SettleRequest` | record | owner + siting + rows + expected revision |
| `ClaimLedger` | interface | one handle per world; `settle`/`release` the only writers |
| `ClaimMatrix` | final class | the only conflict predicate |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.claim;

public enum Sharing {
    exclusive, shared;
    public boolean blocks();
}

public sealed interface Footprint {
    record Box() implements Footprint { }                         // the bay cells, air included
    record Bound() implements Footprint { }                       // union of bound non-air footprints
    record Declared(CuboidSet cells) implements Footprint { }     // exactly these cells
    record None() implements Footprint { }                        // no cell
    static Footprint box();
    static Footprint bound();
    static Footprint declared(CuboidSet cells);
    static Footprint none();
}

public record ClaimRow(LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting,
                       NamespacedId bay) implements Comparable<ClaimRow> {
    @Override public int compareTo(ClaimRow other);               // cell, owner, bay
}

public record ClaimSet(java.util.List<ClaimRow> rows, long revision) {
    public ClaimSet { /* canonical order; revision >= 0 */ }
}

public record ClaimView(java.util.List<ClaimRow> rows, long revision, boolean available) {
    public ClaimView { /* canonical; unavailable ⇒ no rows */ }
    public static ClaimView unavailable();
    public java.util.List<ClaimRow> rowsIn(Cuboid box);
}

public sealed interface ClaimVerdict {
    record Settled(long revision, java.util.List<ClaimRow> rows) implements ClaimVerdict {
        public Settled { /* canonical rows; revision >= 1 */ }
    }
    record Conflict(java.util.List<ClaimRow> incumbents, FindingBundle findings)
            implements ClaimVerdict { }
    record RevisionMoved(long expected, long actual) implements ClaimVerdict { }
}

public record SettleRequest(OwnerId owner, Siting siting, java.util.List<ClaimRow> rows,
                            long expectedRevision) {
    public SettleRequest { /* rows owner/siting match; canonical */ }
}

public interface ClaimLedger {
    World world();
    long revision();
    ClaimVerdict settle(SettleRequest request);
    ClaimVerdict release(OwnerId owner, Siting siting);
    ClaimView read(Cuboid box);
}

public final class ClaimMatrix {
    public static boolean conflicts(ClaimRow a, ClaimRow b);
    private ClaimMatrix() {}
}
```

## 4. Laws

### 4.1 One world per ledger

A `ClaimLedger` handle answers for exactly one `World` (`world()`); its rows carry no world. Two
worlds are two ledgers. This removes the whole "row from another world" class of errors and makes
`ClaimRow` smaller. A host that wants a combined view composes two ledgers itself.

### 4.2 Conflict

`ClaimMatrix.conflicts(a, b)` is symmetric and true exactly when:

1. `a.cell().equals(b.cell())`;
2. not `(a.owner().equals(b.owner()) && a.siting().equals(b.siting()))` — a claimant never conflicts
   with itself, so a re-settle replaces its own rows;
3. at least one of `a.sharing().blocks()` / `b.sharing().blocks()`.

`shared`/`shared` coexists. `exclusive`/`shared` conflicts in both directions. There is no strength
order on `Sharing`; the matrix is the only rule and the adapter has no second predicate.

### 4.3 Order and identity

`ClaimRow` order is `(cell, owner, bay)`, implemented once in `compareTo`; `ClaimSet` and
`ClaimVerdict` sort by it. A row is identified by the full record; two bays of one owner at one siting
may hold the same cell only when both are `shared` (the blueprint refuses an exclusive overlap), and
the two rows stay distinct by `bay`. A settlement replaces every row of `(owner, siting)` in one
compare-and-set; it never merges per row or per bay.

### 4.4 Footprint

| Footprint | Owned cells | Resolved when |
| --- | --- | --- |
| `Box` | `bay.cells` | build (a local set, translated at settle) |
| `Bound` | union of the bound subjects' readable non-air root-frame footprints | settle |
| `Declared` | the declared `CuboidSet` | build |
| `None` | ∅ | always |

Claims protect what a structure occupies or declares, never what it merely reads. `Box` is how an
empty cavity is protected; `Declared` is ground the author writes down; `None` is an observation
that owns nothing. `Footprint` does not decide whether a `Match` is complete; that is `Coverage` in
`structure`. A nested body or a non-root subject is never part of a `Bound` footprint; the engine
rejects it with `SC-CLM-0004` before rows are derived (`engine` §7.5).

### 4.5 Ledger behaviour

- `read(box)` is total: an unavailable store answers `ClaimView.unavailable()` (no rows, revision 0,
  `available == false`), never an empty available view. An empty result is a fact.
- `settle(request)` is one compare-and-set over all rows of `(owner, siting)`. If
  `request.expectedRevision() != revision()`, it answers `RevisionMoved` and writes nothing. If a
  request row conflicts with an incumbent row (matrix, excluding the request's own key), it answers
  `Conflict` with the incumbents and writes nothing. Otherwise it replaces the key and answers
  `Settled(newRevision, rows)`, bumping the revision by one.
- `release(owner, siting)` deletes every row of the key and bumps the revision by one. An absent key
  is a no-op and does **not** bump. It is safe to call on every instance removal and move; it is
  idempotent after the first. A world unload keeps the persisted claim rows and does not release them
  (D-38). The whole-key granularity is the contract (D-38): one
  siting is one instance, so there is no per-bay or per-row release; duplicate rows in the key are a
  representation detail and are not independently releasable.
- All rows of a `SettleRequest` must name the request's `owner` and `siting`; a mismatch is a
  programmer error (`IllegalArgumentException`) because the planner is the only source. A ledger may
  additionally refuse a row whose `sharing`/`bay` is inconsistent; that is `Conflict`/`SC-CLM-0004`,
  not an exception.
- `revision()` is the ledger-wide CAS base, independent of a box. Revisions are positive and
  monotonic; a replacement at one siting always changes the revision, even when the row set is
  identical.

### 4.6 Conflict findings

The engine builds one finding per `(owner, bay)` pair, not per cell: the contested cells form one
`CuboidSet` locus, the primary label sits on the first contested cell in row order, a note gives the
count, and the two sharing values are named in the message. `Conflict.findings` is the report; the
ledger never renders. The incumbent rows are carried so a caller can name both owners without
re-reading.

## 5. Pipeline role

- `compile`: `Sharing`/`Footprint` are bay declaration values; no ledger.
- `check`: the request carries a frozen `ClaimView`; the engine derives a prediction and compares.
- `settle`: the engine derives rows, then the ledger CAS; `Conflict`/`RevisionMoved` are results.
- `release`: host lifecycle only (removal and move; a world unload keeps claim rows).

## 6. Codes

`SC-CLM-0001` (ERROR, primary): two claims conflict. Producer: `engine.check` (prediction) and
`engine.settle` (authoritative). `SC-CLM-0002` (WARNING, no primary): the ledger cannot answer.
Producer: `engine.check`. `SC-CLM-0003` (ERROR): the revision moved twice. Producer:
`engine.settle`. `SC-CLM-0004` (ERROR, primary): a settle binding is invalid. Producer:
`engine.settle`. `SC-CLM-0005` (ERROR, primary): an exclusive `Bound` footprint is empty. Producer:
`ClaimPlanner`/`engine.settle`.

## 7. Tests

- Matrix: all four sharing pairs; same owner/siting excluded; different cells; symmetry.
- Ledger: CAS success/failure; replacement of the whole key; release absent/present; revision bumps;
  unavailable read; conflict leaves bytes untouched; canonical order.
- Footprint resolution: pipe corner (`Bound` owns four of eight), door (`Bound` equals bay),
  cavity (`Box` owns air), `Declared` outside the bay, `None`.
- `ClaimSet`/`ClaimView` ordering and immutability.

## 8. Dependencies

`core` (`NamespacedId`, `World`, `OwnerId`, `LatticeKey`), `space` (`Cuboid`, `CuboidSet`,
`Siting`) and `finding` (`FindingBundle` in a conflict). It does not import `structure` or `engine`;
the engine builds the findings.
