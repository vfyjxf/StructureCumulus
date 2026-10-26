# 02 — Model

> One page of nouns and one page of flow. Every module file is an elaboration of this page.

## 1. The nouns, once

| Noun | Type | Home | Meaning |
| --- | --- | --- | --- |
| id | `NamespacedId` | core | `ns:path`, the only naming type |
| world | `World` | core | one level, named by its id |
| instance | `InstanceId` | core | `(world, serial)`, one placed structure |
| snapshot | `SnapshotId` | core | `(world, revision)`, one capture |
| owner | `OwnerId` | core | who holds a claim |
| phase | `Phase` | core | `compile`, `check`, `settle` |
| cell | `LatticePoint` | space | integer `(x,y,z)` in a blueprint's local frame |
| box | `Cuboid` | space | half-open axis-aligned box |
| region | `CuboidSet` | space | normalized disjoint box set; value equality is by cells |
| orientation | `Orientation` | space | one of 48 signed axis permutations |
| siting | `Siting` | space | origin + placement: where a blueprint stands |
| locus | `Locus` | space | where a finding points: definition, local, world or key |
| occupant | `OccupantSnapshot` | world | what is in a cell: block, block entity, entity, nested structure, air |
| view | `WorldView` | world | an immutable, section-based snapshot read face |
| lease | `SnapshotLease` | world | a view plus `isCurrent()` |
| expectation | `Expectation<T>` | structure | a test over one typed subject |
| focus | `Focus<T>` | structure | how a check binds its subject |
| check | `Check<T>` | structure | one focus + one expectation over one bay |
| bay | `Bay` | structure | a rectangular region + coverage + footprint + sharing |
| blueprint | `Blueprint` | structure | immutable compiled template |
| key | `ContextKey<T>` | core | typed identity of a value |
| computation | `Computation<T>` | context | produces a key from the snapshot |
| deriver | `Deriver<T>` | context | produces a key from a formed result |
| context | `StructureContext` | context | typed values of one instance |
| claim | `ClaimRow` | claim | one cell of one world owned by one bay of one siting |
| ledger | `ClaimLedger` | claim | per-world claim store |
| finding | `Finding` | finding | one report with a stable code |
| bundle | `FindingBundle` | finding | ordered, deduplicated, capped report |
| remedy | `Remedy` | remedy | a repair or a preview |
| document | `Document` | document | the data form of a blueprint |
| kernel | `StructureKernel` | spi | the facade: five operations and two lookups |

## 2. The flow

```
Document (JSON/host tree)
   │  DocumentCompiler.compile  (COMPILE phase, no world)
   ▼
Blueprint ──────────────────────────────────────────────┐
   │                                                    │
   │  host: WorldCapture.capture → WorldView + lease    │
   ▼                                                    │
MatchRequest(blueprint, siting, lease, instance, owner, budget, claims)
   │
   ├─ CHECK        engine.check
   │    section computations → fold → aggregate computations → checks
   │    → context values, bay bindings, findings, spend
   │    → capture gate
   │    → claim prediction against the frozen ClaimView
   │    → Admitted | Failed | Undecided | Dropped        (writes nothing)
   │
   ├─ SETTLE       engine.settle(request, admitted)
   │    gate → validate bindings → derive rows → one CAS → publish instance values
   │    → Formed | Failed | Undecided | Dropped          (the only writer)
   │
   └─ DERIVE       engine.derive(formed, derivationSpecs)
        derivers over the formed context → publish one batch
        → StructureContext
```

## 3. The three phases

| Phase | Input | Reads world | Writes | Output |
| --- | --- | --- | --- | --- |
| `compile` | `Document` | no | nothing | `Blueprint` + findings |
| `check` | `MatchRequest` + lease | the immutable view | nothing | `Admitted` / `Failed` / `Undecided` / `Dropped` |
| `settle` | request + `Admitted` | the lease's gate | claims + context | `Formed` / `Failed` / `Undecided` / `Dropped` |

A `Phase` appears only in a `Finding` and in a result, to say when the fact became knowable. It is
not a scheduling unit and not declared by a blueprint.

## 4. The check pipeline in detail

1. **Gate.** If the lease is not current, the result is `Dropped(check, findings)` with `SC-ENG-0002`
   in the bundle; nothing is read.
2. **Section pass.** For each `SectionComputation`, for each section of its `region` intersecting the
   captured sections, call `compute(SectionContext)`. A `Found(value)` becomes a contribution
   stamped with the section; a `Missing` makes the whole key absent. Charge cells and sections.
3. **Barrier.** For each contributed key, sort contributions by `SectionKey` and fold with
   `Merge.combine`. If any section was missing or refused, the key is absent. If all sections were
   readable and nothing was contributed, use `ContextKey.whenAbsent` (possibly null).
4. **Aggregate pass.** In a topological order of `inputs()`, call `compute(AggregateContext)`. A
   `Found(value)` writes the key; a `Missing` makes it absent and reports `SC-CTX-0201` unless the
   computation reported its own finding. A cycle is a compile error, so this cannot fail here.
5. **Checks.** In declaration order, call `Check.run(FocusContext)`. A check's focus binds a subject
   from the snapshot; a missing subject is `Unreadable`, never `Mismatch`. Charge the read scope.
6. **Gate again.** A mark or invalidation during the attempt drops it. This is the conservative
   gate: one counter for the whole watched region.
7. **Verdict fold.** Any `Mismatch` → `Failed`. Else any `Unreadable` or `Abstain` or zero checks →
   `Undecided`. Else `Admitted` with the values, bindings and spend.
8. **Claim prediction.** Derive rows from the bindings, compare with the frozen `ClaimView`. An
   unavailable view is `Undecided(ledgerUnavailable)`; a conflict is `Failed(SC-CLM-0002)`; else
   `Admitted` carries the view's revision.

## 5. The settle pipeline

1. Gate.
2. Validate every binding: the bay is this blueprint's; every subject was minted under this
   snapshot; the subjects' footprints lie inside the bay (equal for `oneSubject`); an `exclusive`
   `Bound` bay bound at least one non-air cell.
3. Derive rows with `ClaimPlanner`; the caller supplied none.
4. `ClaimLedger.settle(SettleRequest)` at the revision the check read.
5. `RevisionMoved` → re-read the same box, re-run the matrix, retry once at the new revision.
6. `Conflict` → `Failed` with the incumbent rows. `Moved` twice → `Undecided(ledgerMoved)`.
7. `Settled` → publish the `instance` values to the `ContextStore` at `revision + 1`, then return
   `Formed` carrying the `ClaimSet`, `contextRevision` and `ledgerRevision`.

## 6. The value algebra

- `Outcome<T>` = `Found(T)` | `Missing`: a lookup.
- `FocusResult<T>` = `Taken(T, subjects, notes)` | `Unavailable(findings)`: a focus.
- `Reply` = `Match(bindings)` | `Mismatch(findings)` | `Unreadable(findings)` | `Abstain`: a test.
- `MatchResult` = `Admitted` | `Formed` | `Failed` | `Undecided` | `Dropped`: an attempt.
- `ClaimVerdict` = `Settled` | `Conflict` | `RevisionMoved`: a ledger answer.

Each family is sealed and each arm is a record. A `switch` over one is exhaustive; a `default` over
one is a defect.

## 7. What is deliberately absent

- No `Stage`, `ContextTier`, `Retention.ATTEMPT`, `ContextFrame`, `RuntimeState`, `Augment`,
  `DecisionKind`, `ReadSet`, `ClaimPlan`, `ClaimMode`, `ClaimScope`, `Requirement`, `BayBuilder`,
  `Segment`, `SegmentKey`, `Overlap`, `PrescreenIndex`, `Census`, `Fingerprint`, `EntityKey`,
  `ChangeSet`, `DirtyIndex`, `WatchedInstance`, `Ticket`, `RetryToken`, `PinBudget`, `InstanceBudget`,
  `InstanceRecord`, `Deferred`, `CacheMode`, `AttemptCache`, `PlanId`, `UnitId`, `Projection`,
  `NestedLevelSnapshot`, `NestedView`, `ReadSet`, `ClaimPlan.Overlap`.
- No public `MatchSession`, no `ProbeSession`, no child lease handles, no `AutoCloseable` results, no
  `solve`/`edit` execution, no `Stability` enum, no `ExplainBook` interface.
- No document profile, schema upgrade, canonicaliser, emitter or compact writer. One schema, one
  writer function, one reader port.

The list is part of the design: a module that reintroduces one of these names is regressing to the
old model and must say why in a decision record.
