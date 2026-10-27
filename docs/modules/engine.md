# Module `kernel.engine`

> Rank 7. One check, one settle, one derive. The engine is the only package that knows the whole
> pipeline; it owns the request, the outcome, the result arms, the gate, the row planner and the
> two entry points. It schedules nothing, stores nothing and renders nothing.
>
> `Blueprint.reads()` is the host's policy source; partial sections, host-code relay and nested
> claim rejection are implemented.

## 1. Scope

**Owns:** `MatchRequest`, `MatchOutcome`, `BayBinding`, `MatchResult` + five arms,
`UndecidedReason`, `MatchEngine`, `ClaimPlanner`.

**Does not own:** the blueprint's validity (`structure`), the ledger (`claim`), the context store
(`context`), the document (`document`), scheduling, capture, dirty state (`host`).

**Imports:** `core`, `space`, `finding`, `world`, `claim`, `structure`, `context`, `document`.

## 2. Types

| Type | Kind | Contract |
| --- | --- | --- |
| `MatchRequest` | record | blueprint + siting + lease + instance + owner + budget + frozen claims + cancellation |
| `MatchOutcome` | record | view, values, bindings, reads, spend |
| `BayBinding` | record | a bay and the subjects its checks bound |
| `MatchResult` | sealed | `Admitted`, `Formed`, `Failed`, `Undecided`, `Dropped` |
| `UndecidedReason` | enum | why no verdict exists |
| `MatchEngine` | final class | `check`, `settle`, `derive` |
| `ClaimPlanner` | final class | the only row factory |

## 3. Signatures

```java
package dev.vfyjxf.cumulus.kernel.engine;

public record MatchRequest(Blueprint blueprint, Siting siting, SnapshotLease lease,
                           InstanceId instance, OwnerId owner, Budget budget,
                           @Nullable ClaimView claims, CancelToken cancel) {
    public static MatchRequest of(Blueprint blueprint, Siting siting, SnapshotLease lease,
                                  InstanceId instance, OwnerId owner, Budget budget,
                                  @Nullable ClaimView claims);
}

public record MatchOutcome(WorldView view, java.util.Map<ContextKey<?>, Object> values,
                           java.util.List<BayBinding> bindings, ReadScope reads, Spend spend) {
    public MatchOutcome { /* immutable, keys/values sorted */ }
    public <T> Outcome<T> value(ContextKey<T> key);
}

public record BayBinding(Bay bay, java.util.List<OccupantSnapshot> subjects) { }

public enum UndecidedReason {
    budgetExhausted, cancelled, unreadable, abstained, noChecks,
    ledgerUnavailable, ledgerMoved, designDefect
}

public sealed interface MatchResult {
    Phase phase();
    FindingBundle findings();

    record Admitted(Phase phase, InstanceId instance, Siting siting, MatchOutcome outcome,
                    long ledgerRevision, boolean ledgerAvailable,
                    FindingBundle findings) implements MatchResult { }

    record Formed(Phase phase, InstanceId instance, Siting siting, MatchOutcome outcome,
                  ClaimSet claims, long contextRevision, long ledgerRevision,
                  FindingBundle findings) implements MatchResult { }

    record Failed(Phase phase, FindingBundle findings) implements MatchResult { }

    record Undecided(Phase phase, UndecidedReason reason, FindingBundle findings,
                     @Nullable MatchOutcome partial) implements MatchResult { }

    record Dropped(Phase phase, FindingBundle findings) implements MatchResult { }
}

public final class MatchEngine {
    public static MatchResult check(MatchRequest request);
    public static MatchResult settle(MatchRequest request, MatchResult.Admitted admitted,
                                     @Nullable ClaimLedger ledger, ContextStore contexts);
    public static StructureContext derive(MatchResult.Formed formed,
                                          java.util.List<DerivationSpec> derivations,
                                          Catalog<Deriver<?>> derivers, ContextStore contexts);
    private MatchEngine() {}
}

public final class ClaimPlanner {
    public static Derived derive(Blueprint blueprint, Siting siting, OwnerId owner,
                                 java.util.List<BayBinding> bindings);
    public record Derived(java.util.List<ClaimRow> rows, CuboidSet footprint) { }
    private ClaimPlanner() {}
}
```

## 4. The check algorithm

```
check(request):
  if !request.lease().isCurrent()                      -> Dropped(check) + SC-ENG-0002
  plan = request.blueprint().plan()
  if plan.checks().isEmpty()                           -> Undecided(noChecks)
  session = new Session(request)
  for each section computation in plan.sections():      // declaration order
      for each section of region ∩ view:
          if budget exhausted                          -> Undecided(budgetExhausted)
          if cancelled                                 -> Undecided(cancelled)
          if slot Refused                              -> session.refuse(key, SC-CTX-0006)
          else if slot Partial                         -> compute with unreadable(); Found -> contribute
          else                                         -> compute; Found -> contribute
  fold contributions per key                           // SectionKey order
  for each aggregate computation in plan.aggregates(): // Kahn order
      if budget exhausted / cancelled                  -> ...
      result = compute(aggregate context)
      Found -> values.put; Missing -> absent + SC-CTX-0001 if none reported
  for each check in plan.checks():                     // declaration order
      if budget exhausted / cancelled                  -> ...
      charge check.reads()
      result = check.run(focus context)
      collect reply, subjects per bay, notes
  if !lease.isCurrent()                                -> Dropped(check) + SC-ENG-0002
  fold replies: any Mismatch -> Failed; any Unreadable -> Undecided(unreadable);
                any Abstain -> Undecided(abstained); else Admitted
  admitted:
      if a binding carries a nested subject or a non-root frame -> Failed(SC-CLM-0004)
      if no claimable bay                              -> ledgerAvailable=false, revision=0
      else if request.claims() == null/!available      -> Undecided(ledgerUnavailable)
      else derive rows; matrix against claims.rows()
           conflict -> Failed(SC-CLM-0001)
           else     -> Admitted(revision = claims.revision, ledgerAvailable = true)
```

`check` never writes. It reads only the lease's view, the request's frozen claims and the blueprint.
A `Partial` section is readable: its refused cells are exposed through `SectionContext.unreadable()`
and are never silently counted as air. A wholly `Refused` section refuses the key before `compute`.

## 5. The settle algorithm

```
settle(request, admitted, ledger, contexts):
  if !request.lease().isCurrent()                      -> Dropped(settle) + SC-ENG-0002
  if admitted.phase() != check                          -> Undecided(designDefect)
  bindings = admitted.outcome().bindings()
  validate(bindings)                                    // blueprint, snapshot, root frame, cells, no nested, oneSubject
      failure -> Failed(SC-CLM-0004)
  rows = ClaimPlanner.derive(blueprint, siting, owner, bindings)
  if rows.isEmpty():
      context = contexts.publish(instance, instanceValues, findings, spend)
      return Formed(settle, instance, siting, outcome, new ClaimSet([], ledger == null ? 0 : ledger.revision()),
                    context.revision(), ledger == null ? 0 : ledger.revision(), findings)
  if ledger == null                                     -> Undecided(ledgerUnavailable)
  verdict = ledger.settle(new SettleRequest(owner, siting, rows, admitted.ledgerRevision()))
  if RevisionMoved:
      fresh = ledger.read(derived.footprint())
      if !fresh.available()                             -> Undecided(ledgerUnavailable)
      if matrix(rows, fresh.rows()) conflict            -> Failed(SC-CLM-0001)
      verdict = ledger.settle(new SettleRequest(owner, siting, rows, fresh.revision()))
      if RevisionMoved                                  -> Undecided(ledgerMoved)
  if Conflict                                           -> Failed(SC-CLM-0001) with incumbents
  Settled:
      context = contexts.publish(instance, instanceValues, findings, spend)
      return Formed(settle, instance, siting, outcome, new ClaimSet(rows, settled.revision()),
                    context.revision(), settled.revision(), findings)
```

`settle` is the only writer. It closes nothing; a lease is host-owned.

## 6. The derive algorithm

```
derive(formed, specs, derivers, contexts):
  base = contexts.context(formed.instance())
  values = new LinkedHashMap<>(base.values())
  findings = formed.findings()
  for spec in specs sorted by (order, deriver id):
      deriver = derivers.get(spec.deriver())
      if missing -> findings += SC-CTX-0004; continue
      if any requires absent -> findings += SC-CTX-0007; continue
      result = deriver.derive(context over values + base)
      Found -> values.put(spec.output(), value)
      Missing -> findings += reported or SC-CTX-0005
  return contexts.publish(formed.instance(), values, findings, formed.outcome().spend())
```

A derive pass publishes one revision. A `match` key produced by a deriver is rejected with
`SC-CTX-0005` (only `instance` values are persisted). A missing deriver id is a finding, not a throw.

## 7. Laws

### 7.1 Request

`MatchRequest` is the determinism tuple: `(blueprint, siting, lease.view(), lease.id(), instance,
owner, budget, claims, cancellation)`. It carries no stage, no policy, no ticket, no record, no pin
budget. `instance` is allocated by the host before the check; `check` does not mint it. `claims` is
the frozen ledger view the host read; `check` never reads a live ledger. `cancel` is checked at unit
boundaries; a long extension checks its own token. `CancelToken.never()` is the default factory.

### 7.2 Outcome

`MatchOutcome.values` contains the folded section values, the aggregate values, and the
`Retention.match`/`instance` values; keys are sorted by `(id, type name)`. `bindings` has one
`BayBinding` per bay that has checks, in bay declaration order; a bay with no non-air subject carries
an empty list, which is how an empty `Bound` claim is detected. `reads` is `Blueprint.reads()`: the
engine charges and reports exactly the declared closure, and a focus that reads outside it is a
review defect, never a silent widening. `spend` is the sum of every charge.

### 7.3 Result

`Admitted` is not a structure: no claims, no `ClaimSet`, no `Formed`. `Formed` exists only after a
`Settled` (or an empty-row registration that needed no ledger) and carries the claims, the context
revision and the ledger revision. `Failed` has at least one `error` finding. `Undecided` carries a
reason and, when available, a partial outcome. `Dropped` means the snapshot was no longer current;
the attempt read nothing that can be trusted. A `Partial` section does not make an attempt
`Undecided` by itself: the computation's `Missing` propagates through the normal fold, and the
finding carries the refusal's code.

`MatchResult` is a pure value: no `AutoCloseable`, no lease, no `close()`. The host scopes the lease.

### 7.4 Gate

`SnapshotLease.isCurrent()` is the single gate. It is evaluated before the first read and after the
last unit. A mark during the attempt drops the whole attempt; there is no per-unit reuse in this
version. A stale snapshot never publishes a claim or a context value.

### 7.5 Claims

`ClaimPlanner` is the only row factory; it is pure and takes no view and no ledger. `Box` and
`Declared` are local sets translated by the siting; `Bound` is the union of the subjects' world
footprints; a subject from a nested frame (`frame != 0`) or of kind `OccupantKind.nested` is invalid
for a claimable bay. A nested structure is checkable but never claimable: a nested focus returns no
subject, and a check that binds a nested body through `Focuses.occupant` fails at claim prediction
with `SC-CLM-0004` before an `Admitted` exists. Rows are one per cell per bay; a cell may carry two
rows only when both bays are `shared` (the blueprint refuses an exclusive overlap). `rows` is sorted
by `(cell, owner, bay)`.

`check` rejects a nested or non-root binding while folding the verdict; it never returns an
`Admitted` that carries one. `settle` revalidates before deriving: each bay is this blueprint's; each
subject's ref belongs to this snapshot and the root frame; no subject has kind `nested`; every subject
footprint lies inside the bay's world cells; for `oneSubject` the union equals the bay; for
`exclusive + Bound` at least one non-air subject exists. A failure is
`Failed(SC-CLM-0004)`/`SC-CLM-0005` and nothing is written. `ClaimPlanner.derive` throws
`IllegalArgumentException` carrying `SC-CLM-0004` if a nested or non-root subject reaches it; that is
a proof failure, because check and settle already validated the bindings.

### 7.6 Budget and determinism

Charges: a section computation charges the slice volume plus one section per section run; a check
charges its declared cells' volume plus the sections they touch, and a window charges its inflated
volume plus its sections; an aggregate charges nothing. Charges happen before the unit runs; a
refusal returns `Undecided(budgetExhausted)`. The budget is the request's; the engine does not reset
it. A `Partial` section is charged as one section; its refused cells are not a second read. For a
fixed tuple the result value, finding order and spend are identical; `Plan` order is fixed at build;
the fold sorts by `SectionKey`; the finding bundle sorts.

## 8. Codes

`SC-ENG-0001` budget, `SC-ENG-0002` stale snapshot, `SC-ENG-0004` internal plan defect,
`SC-ENG-0005` cancelled; `SC-CLM-0001` conflict, `SC-CLM-0002` ledger unavailable, `SC-CLM-0003`
revision moved twice, `SC-CLM-0004` invalid binding (including a nested or non-root subject),
`SC-CLM-0005` empty exclusive bound; `SC-CTX-0001/0004/0005/0006/0007`; `SC-OCC-0004` as the relayed
default for a refused section read and `SC-SPI-0001` extension guard. A host code carried by a
`ReadRefusal` is relayed unchanged unless it is already in `Codes.all`.

## 9. Tests

- Campfire: compile → check → admitted → settle → formed → derive; context value.
- Furnace: shell/cavity/door; claims; context.
- Door: two subjects, one bay, one claim; missing half is `Undecided`, wrong half is `Failed`.
- Hatch: shared/shared coexists; exclusive conflicts; release re-claims.
- Pipe corner: eight asserted, four owned; neighbour does not conflict.
- Entity pen: window read, subject footprint, `anySubject + Bound`.
- Absence: a refused section makes the key absent; a read is `Unreadable`, never a short sum; a
  partial section makes `count`/`air` `Missing` and reports the refusal, while `volume` stays total.
- Nested: a nested focus yields no subject; a nested body bound through an occupant focus is
  `Failed(SC-CLM-0004)` before rows; a child-frame subject is rejected the same way.
- Budget: a too-small budget is `Undecided`; no claims.
- Gate: an invalidated lease is `Dropped`; nothing written.
- Ledger: revision moved once retries; twice is `Undecided`; conflict is `Failed`; no row on failure.
- Bindings: another snapshot, a nested frame, a cell outside the bay, an empty exclusive bound.
- Derive: order, missing deriver, missing key, one publish, stale match key.

## 10. Dependencies

All kernel packages below rank 8. It does not import `remedy` or `spi`; the facade passes the
catalogs, ledger and store in.
