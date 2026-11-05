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
| `ClaimRow` | record | one cell of one world held by one bay of one instance at one siting |
| `ClaimSet` | record | the rows of one settlement + the revision written |
| `ClaimView` | record | a read answer: rows, revision, availability |
| `ClaimVerdict` | sealed | `Settled`, `Conflict`, `RevisionMoved` |
| `SettleRequest` | record | instance + rows + expected revision |
| `ClaimLedger` | interface | one handle per world; `settle`/`release` the only writers |
| `ClaimMatrix` | final class | the only conflict predicate |
| `InMemoryClaimLedger` | final class, package-private | the shipped ledger behind `ClaimLedger.inMemory`: rows, revision and CAS in memory (D-44) |

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

public record ClaimRow(LatticeKey cell, Sharing sharing, Siting siting,
                       NamespacedId bay, InstanceId instance) implements Comparable<ClaimRow> {
    @Override public int compareTo(ClaimRow other);               // cell, instance, bay,
                                                                  // siting origin, placement orientation,
                                                                  // placement offset, sharing
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
        public Settled { /* canonical rows; revision >= 1 when rows are present */ }
    }
    record Conflict(java.util.List<ClaimRow> incumbents, FindingBundle findings)
            implements ClaimVerdict { }
    record RevisionMoved(long expected, long actual) implements ClaimVerdict { }
}

public record SettleRequest(InstanceId instance, java.util.List<ClaimRow> rows,
                            long expectedRevision) {
    public SettleRequest { /* at least one row, every row of this instance; canonical;
                              expected >= 0 — "own nothing" is a release, not a settle (D-60) */ }
}

public interface ClaimLedger {
    World world();
    long revision();
    ClaimVerdict settle(SettleRequest request);
    ClaimVerdict release(InstanceId instance);
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
2. `!a.instance().equals(b.instance())` — a claimant never conflicts with itself, so a re-settle
   replaces its own rows;
3. at least one of `a.sharing().blocks()` / `b.sharing().blocks()`.

`shared`/`shared` coexists. `exclusive`/`shared` conflicts in both directions. There is no strength
order on `Sharing`; the matrix is the only rule and the adapter has no second predicate.

### 4.3 Order and identity

`ClaimRow` order is `(cell, instance, bay, siting origin, placement orientation, placement offset, sharing)`, implemented once in `compareTo`; `ClaimSet` and
`ClaimVerdict` sort by it. A row is identified by the full record; two bays of one instance may hold
the same cell only when both are `shared`. An exclusive overlap is refused twice: at build for the
owned sets the blueprint already knows (`Box`, `Declared` — D-57), and by the planner where the rows
are derived for the ones only the world answers (`Bound`, `SC-CLM-0001`). The two rows stay distinct
by `bay`. A settlement replaces every row of one instance in one compare-and-set;
it never merges per row or per bay.

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
rejects it with `SC-CLM-0004` before rows are derived (`engine` §7.5). Two `Bound` bays of one
blueprint that own the same cell with an exclusive side are not a build fact: the planner refuses
them when it derives the settled rows (`SC-CLM-0001`).

### 4.5 Ledger behaviour

- `read(box)` is total: an unavailable store answers `ClaimView.unavailable()` (no rows, revision 0,
  `available == false`), never an empty available view. An empty result is a fact.
- `settle(request)` is one compare-and-set over all rows of the request's instance, and the request
  carries at least one row (D-60). If `request.expectedRevision() != revision()`, it answers
  `RevisionMoved` and writes nothing — even when the content would have matched, because the caller's
  view is stale and it must re-read. It then drops the rows this settle replaces (next bullet) and,
  if a request row still conflicts with a remaining incumbent, answers `Conflict` with those
  incumbents and writes nothing. Otherwise it compares the resulting content with the current rows:
  identical content answers `Settled(revision, rows)` and writes nothing; different content writes
  the replacement and answers `Settled(newRevision, rows)`, bumping the revision by one.
- A siting holds one instance (D-48): the rows `settle` replaces are the request instance's own rows
  and every row of another instance whose siting equals one of the request's sitings. They are
  dropped before `ClaimMatrix.conflicts` runs and are never reported as conflicts. Exclusion,
  conflict check and write stay inside the one compare-and-set, so a failed settle leaves those
  incumbents in place. The ledger derives the replacement from the request's rows; no host passes a
  replacement list.
- `release(instance)` deletes every row of that instance and bumps the revision by one; dropping a
  key is this operation, including an instance whose next plan owns nothing (D-60). An absent
  instance is a no-op and does **not** bump. It is safe to call on every instance removal and move; it
  is idempotent after the first. A world unload drops the ledger with the world's structure state, so
  its rows never outlive their instances (D-44). The whole-instance granularity is the contract: there
  is no per-bay or per-row release; duplicate rows are a representation detail and are not
  independently releasable.
- Because a settle compare-and-set replaces the whole of one instance's rows, `release` after a
  `Formed` removes exactly the rows that settle wrote; it is the rollback companion of `settle`, not
  a destructive clean-up.
- All rows of a `SettleRequest` must name the request's `instance`; a mismatch is a programmer error
  (`IllegalArgumentException`) because the planner is the only source. A ledger may additionally
  refuse a row whose `sharing`/`bay` is inconsistent; that is `Conflict`/`SC-CLM-0004`, not an
  exception.
- `revision()` is the ledger-wide CAS base, independent of a box. Revisions are non-negative and
  monotonic; the revision is the content version, not a write counter: it changes exactly when the
  rows a reader can see change (D-60). A settle that changes nothing and a release of an absent key
  both keep it, so a `Formed` may carry the same ledger revision as the attempt before it.

### 4.6 Conflict findings

The engine builds one finding per `(instance, bay)` pair, not per cell: the contested cells form one
`CuboidSet` locus, the primary label sits on the first contested cell in row order, a note gives the
count, and the two sharing values are named in the message. `Conflict.findings` is the report; the
ledger never renders. The incumbent rows are carried so a caller can name both instances without
re-reading; an owner is resolved through the structure manager, never stored on a row.

## 5. Pipeline role

- `compile`: `Sharing`/`Footprint` are bay declaration values; no ledger.
- `check`: the request carries a frozen `ClaimView`; the engine derives a prediction and compares.
- `settle`: the engine derives rows, then the ledger CAS; an empty derivation is a `release`, not a
  CAS (D-60). `Conflict`/`RevisionMoved` are results.
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
- Ledger: CAS success/failure; replacement of the whole key; release absent/present; the revision
  bumps on a content change only — a no-op settle keeps it and a stale settle is refused even when
  the content matches; unavailable read; conflict leaves bytes untouched; canonical order.
- Footprint resolution: pipe corner (`Bound` owns four of eight), door (`Bound` equals bay),
  cavity (`Box` owns air), `Declared` outside the bay, `None`.
- `ClaimSet`/`ClaimView` ordering and immutability.

## 8. Dependencies

`core` (`NamespacedId`, `World`, `OwnerId`, `LatticeKey`), `space` (`Cuboid`, `CuboidSet`,
`Siting`) and `finding` (`FindingBundle` in a conflict). It does not import `structure` or `engine`;
the engine builds the findings.
