# 17 — Worked example: the huge furnace

> One structure, every stage, every output. The numbers are the design's arithmetic, not a
> screenshot: 7×6×7 bounds = 294 cells; shell 194, cavity 100, mouth 2, and the shell and the
> cavity together cover the bounds exactly. The cavity is 5×4×5, so `capacity = 4 × 100 = 400`.
> The example is deliberately small enough to check by hand and large enough to exercise a
> multi-cell footprint, a section computation with an aggregate, an owned cavity, a claim
> conflict and a repair.

## 1. The structure

```
      x0          x6
y 5   ▓ ▓ ▓ ▓ ▓ ▓ ▓    roof (shell)
  4   ▓ · · · · · ▓
  3   ▓ · · · · · ▓    cavity: x1..5, y1..4, z1..5 (100)
  2   ▓ · · ░ · · ▓    mouth:  x3, y1..2, z0 (2)
  1   ▓ · · ░ · · ▓
  0   ▓ ▓ ▓ ▓ ▓ ▓ ▓    floor (shell)
```

The mouth is a 1×2 opening in the front face (`z = 0`), and the cavity is the air box the shell
encloses. The shell is the 7×6×7 box minus the cavity: 294 − 100 = 194 cells. The anchor cell is
the shell block at the definition origin, `(0,0,0)`, whose `facing` property names the front, so
there is no controller entity and no search for one.

## 2. Definition, code-first

```java
Catalogs.Builder draft = Catalogs.builder();

// VOLUME takes one contribution per section, folded at the barrier.
ContextKey<Long> VOLUME = ContextKeys.register(draft, new ContextKey<>(
        NamespacedId.of("mymod", "volume"), Long.class, Retention.CONTEXT, Merges.sum(), null));
// CAPACITY is written once, after the fold; neither key has a whenAbsent.
ContextKey<Long> CAPACITY = ContextKeys.register(draft, new ContextKey<>(
        NamespacedId.of("mymod", "capacity"), Long.class, Retention.CONTEXT, null, null));

// The cavity: EVERY_CELL over its 100 cells, EXCLUSIVE, and it owns them because the volume is empty.
Bay interior = new Bay(NamespacedId.of("mymod", "interior"), Cuboid.of(1, 1, 1, 5, 4, 5),
        Coverage.EVERY_CELL, Sharing.EXCLUSIVE, new Footprint.Box(),
        new Requirement(List.of(interiorAir), List.of(volume, capacity)), "the hollow");

// The mouth: two cells inside the shell, asserted and owned as positive footprints.
Bay mouth = new Bay(NamespacedId.of("mymod", "mouth"), Cuboid.of(3, 1, 0, 1, 2, 1),
        Coverage.EVERY_CELL, Sharing.EXCLUSIVE, new Footprint.Bound(),
        new Requirement(List.of(mouthBars, mouthFacing), List.of()), "the opening");

AnchorSpec front = new AnchorSpec(NamespacedId.of("mymod", "front"),
        NamespacedId.of("minecraft", "facing"), "east");
```

The `interior` bay's `Requirement` holds both computations. `volume` is a section `Computation`:
`output()` is `VOLUME`, `region()` is the cavity, `inputs()` is empty, and it runs once per section,
each run calling `draft.contribute(VOLUME, partial)`. `capacity` is an aggregate `Computation` whose
`inputs()` is `Set.of(VOLUME)`; it reads the folded value with `ctx.read(VOLUME)` and writes
`CAPACITY` with `draft.put`. A check that needs the value names `Focuses.context(VOLUME)` as its
focus, so its `Expectation<Long>` sees the fold (100 here) and never a zero; a refused section
reaches it as `Outcome.Missing`, which the check reports as `UNREADABLE`.

The shell is a holed box — 194 cells with the cavity cut out — so it is **not** one `Bay`:
lowering emits one bay per face box with the ids `#box@x,y,z:sx,sy,sz`, each `EVERY_CELL`,
`EXCLUSIVE` and `Box` (`21` §4). Six bays, six boxes, no id spanning six boxes.

`build()` enforces the legality table rather than a builder's word: `EVERY_CELL` + `Box` on the
cavity is legal because every cell is asserted and all of them are claimed; a check with no bay is
`SC-CHK-0107`; and the coverage of the interior bay is checked against the checks' static decision
cells (`SC-CHK-0106`) before a world exists.

**Anchor and orientation.** The anchor cell is the definition origin, `(0,0,0)` in the grid, and
the resolver reads exactly that one world cell. It obtains a `Heading` from the block's `facing`
property and returns the unique `Siting` satisfying

```
candidate.apply(authoredFront) == anchorHeading
```

with `authoredFront` = `north` for this document. There is one direction and no tie-break: a
candidate that does not satisfy the equation is not a candidate, and the orientation is a
function of the anchor rather than an input to finding it.

The engine's steps are the answer to R-08 — a cheap `PROBE` (bounds, anchor, census), a `FORM` that
asserts the bays and runs the computations, and the settlement — and they are reached through two
entry points only: a read-only `check` and a writing `settle`, which alone writes claims and calls
the structure formed.

## 3. Definition, data-first

The same structure as a compact document, schema 2 — the compact profile, so its bay and region
names are bare and resolve inside the one implicit `main` placement (`mouth` compiles to
`main:mouth`, `10` §4.5 step 4, Q-D3):

```json
{
  "schemaVersion": 2,
  "id": "mymod:huge_furnace",
  "bounds": [0, 0, 0, 7, 6, 7],
  "shell": [{"box": [0,0,0,7,6,7], "block": "minecraft:blast_furnace"}],
  "cavity": [[1,1,1,5,4,5]],
  "bays": [
    {"id": "mouth", "box": [3,1,0,1,2,1], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "bound",
     "expect": {"block": "minecraft:iron_bars"}},
    {"id": "interior", "box": [1,1,1,5,4,5], "coverage": "every_cell",
     "sharing": "exclusive", "footprint": "box",
     "expect": {"air": true}}
  ],
  "checks": [
    {"id": "mouth_facing", "bay": "mouth",
     "focus": {"kind": "block", "cell": [3,1,0]},
     "expect": {"kind": "state", "property": "facing", "value": "east"}}
  ],
  "computations": [
    {"id": "volume", "phase": "section",
     "output": "mymod:volume", "region": "interior",
     "kind": "count", "args": {"mode": "cells"}},
    {"id": "capacity", "phase": "aggregate",
     "output": "mymod:capacity", "inputs": ["mymod:volume"],
     "kind": "mymod:scale", "args": {"factor": 4}}
  ],
  "context": [
    {"id": "mymod:volume", "type": "long", "retention": "context", "merge": "sum"},
    {"id": "mymod:capacity", "type": "long", "retention": "context"}
  ],
  "anchors": [{"role": "front", "property": "facing", "value": "east"}],
  "orientation": {"front": "north"}
}
```

```java
CompileResult compiled = kernel.compile(document);       // DESIGN: validate + assemble
if (compiled.blueprint() == null) {
    renderer.render(compiled.findings());                 // SC-DOC / SC-SPI, with spans
    return;
}
Blueprint furnace = compiled.blueprint();
```

The compiled value is the same shape as §2; a test asserts equality of the two paths.

- Boxes are `[originX, originY, originZ, sizeX, sizeY, sizeZ]`, so `cavity` is
  `[1,1,1,5,4,5]` = 5×4×5 = **100** cells and `bounds` is the 7×6×7 box at the origin.
- `volume` counts cells (`"mode": "cells"`) over the `interior` region, so it is 100; `capacity`
  scales the folded contribution by 4, so it is **400**.
- `phase: section` requires the `output` key to carry `merge`; `phase: aggregate` forbids it. Both
  keys are `Retention.CONTEXT`, so both are published into the same `FORM` frame, and the fold is
  `Merges.sum()` over contributions sorted by `SectionKey`.
- There is no `whenAbsent`: a refused section in `interior` makes `volume` `Missing`, and then
  `capacity` is `Missing` too — never a smaller number.
- A missing `count` factory is `SC-SPI-0002` at `DESIGN`; a missing segment is `SC-DOC-0003`;
  neither throws.
- `scale` is an addon computation, so it is written with its namespace (`mymod:scale`, registered
  in `Catalogs.computations()`); the kernel itself ships `count`, `sum`, `volume`, `distinct` and
  `bind` only. `22` §12.3 spells the same entry `"kind": "scale"`, which would resolve in the
  default namespace and find nothing registered there.

## 4. PROBE: cheap rejection

```java
// The host captured the plan's closure once, for this siting; the record owns it from here.
SnapshotLease lease = capture.lease(furnace, at);

try (MatchResult probe = kernel.check(MatchRequest.at(lease, furnace, at)
        .budget(Budget.of(256, 16))
        .build())) {
    if (probe instanceof MatchResult.Admitted admitted) {
        // bounds, anchor and census passed; a child handle, the plan and the revision it
        // read — no instance and no rows written
    }
}
```

The world has the shell at the siting origin. `BOUNDS` passes (the capture covers all 294 cells),
`CENSUS` observes the declared counts, and `ANCHOR` reads the origin cell and resolves one
`Siting`. `Admitted` is deliberately not a structure: it has a stage, a siting, the outcome of the
cheap checks, a lease, the plan `settle` would write and the ledger revision it read, and `derive`
refuses it. The host uses it only to decide whether to go on to `settle`.

If the origin were two blocks into a wall, `BOUNDS` would still pass but the anchor read would
find no `facing` value; the result is `Failed` with a `SC-FOC-0101` whose primary label is the
`WORLD` cell that was read. If the mouth's section were `DENIED`, the same unit is `Undecided`
with `SC-FOC-0301` naming the section and its status — a refused section is never air.

## 5. The structure: `check` then `settle`

`check` runs on a worker over the sealed snapshot and writes nothing: it returns `Admitted` with
the plan and the ledger revision it read. `settle` runs on the server thread and is the only call
that writes a claim.

```java
MatchRequest request = MatchRequest.at(lease, furnace, at)
        .budget(Budget.of(4_096, 64))
        .build();

MatchResult matched = kernel.check(request);            // read-only: no claim and no frame

if (!(matched instanceof MatchResult.Admitted admitted)) {
    retire(matched);                     // host: render the arm's findings, close its handle
    return;
}

// `settle` consumes the admitted result: it closes that handle on every path, and a null `planned()`
// (no ledger to write into) is `Undecided(LEDGER_UNAVAILABLE)`.
MatchResult result = kernel.settle(request, admitted);  // the only writer

if (!(result instanceof MatchResult.Formed formed)) {
    retire(result);
    return;
}

try (formed) {
    assert formed.generation() == 1;
    assert formed.contextRevision() == 1;              // the FORM frame this attempt published
    assert formed.ledgerRevision() == ledger.revision();
    assert formed.claims().rows().size() == 294;       // 194 shell + 100 cavity
    assert formed.findings().isEmpty();
}
```

The world pass keeps declaration order: the mouth's two cells first, then the shell's 194, then the
cavity's 100. The mouth matches an iron-bars block and its
`facing`; the shell matches the declared block; the cavity is air. The same pass runs `volume`
once per section of the `interior` region: each run calls `draft.contribute(VOLUME, partial)`, the
barrier sorts the contributions by `SectionKey` and folds them with `Merges.sum()` to 100, and the
aggregate `capacity` writes `CAPACITY = 400` with `draft.put`, both keys published into the `FORM`
frame at revision 1. A check on the folded value reads it with `Focuses.context(VOLUME)`; a refused
middle section makes the key absent and the check `UNREADABLE`, never a short sum.

`settle` writes six shell face bays (194 cells) and the cavity (100) as `Box` claims, plus the
mouth's `Bound` rows — all `EXCLUSIVE`, on the server thread, at the ledger revision it read. The
mouth's two `Bound` rows fall inside the front face's `Box` rows: one owner at one siting, so the
matrix pairs them without a conflict and the set is 294 distinct cells. `Formed` exists only after the
ledger write returned `Settled`, and it carries `generation`, `contextRevision` and `ledgerRevision`
so a consumer can tell it from a later one.

If a second furnace already holds an exclusive claim on one shell cell, `settle` is
`Failed(SC-CLM-0002)` with the incumbent rows; the finding's primary label is that cell in
`WORLD`, its secondary labels are the two owners' declarations. The first furnace's cells are
untouched, and nothing was written.

## 6. The instance: lease, context, generations

```java
// §5's try closed the child handle; the instance's own reference keeps the capture alive.
InstanceId instance = formed.id();
MatchOutcome outcome = formed.outcome();

InstanceRecord record = registry.instance(instance);       // owns the capture for the instance
StructureContext values = StructureContext.at(instance);   // frames only, never the lease
Outcome<Long> capacity = values.require(CAPACITY);         // Found(400), never a short sum
```

`InstanceRecord` owns the capture for the instance's lifetime; `Formed` and `Admitted` hold child
handles, and the instance's own reference keeps the view alive after the handle closes. A viewer
that needs its own retention asks for it — `retain(PinBudget)` charges `retainedBytes` and can
refuse with `SC-MC-0502`. `StructureContext.at(instance)` reads published frames only; it never
reads the lease, so a context cannot outlive a capture by holding one. A provider is handed the
lease through `RemedyContext` (§8), which is the registry's, not a result's.

The host persists the `SETTLE` frame as `RuntimeState` under `instance`; the kernel does not
persist it. A reload compares `generation` with the instance's re-check counter, not a timestamp,
and an upgrade is `RuntimeState.upgrade(key, value)` under the same single-writer rule.

## 7. A targeted re-check: one wall cell changes

```java
ChangeSet change = new ChangeSet(
        Set.of(LatticeKey.of(LatticePoint.of(12, 67, 16))),   // local (2,3,6) at this siting
        Set.of(), Set.of(), Set.of(), Set.of(), 41L);
MatchResult again = kernel.check(MatchRequest.at(record.lease(), furnace, at)
        .change(change).build());
```

The shell unit's read scope contains the changed cell, so its `cellStamp` moved and the unit
re-runs; the mouth and the cavity units are reused because their read closures are disjoint from
the change. A reuse is a result carried into the next attempt, never a blend inside this
snapshot: the re-run captures again. The result is `Failed` with `SC-EXP-0001` (expected the
declared shell block, found air) and a `WORLD` label on the cell.

`generation` is unchanged, because the instance did not re-form: a failed re-check leaves the
previous `Formed` exactly as it was. Only a new settle (a fresh `generation` from the new match)
or a discard-driven re-emission (`generation + 1` with the same outcome, `22` §9.4) moves it. The
host marks the instance unhealthy and offers a repair.

## 8. Repair and preview: the same solution, two projections

```java
StructureContext current = StructureContext.at(instance);
if (current.get(CAPACITY) == null) { return List.of(); }          // in a provider: cannot help

ClaimView claims = ledger.read(furnace.bounds().translate(at));   // frozen at its revision
RemedyContext ctx = new RemedyContext(furnace, at, claims, ledger.revision(),
        outcome, current, RemedyOptions.defaults());

Remedy.Repair repair = (Remedy.Repair) kernel.solve(again.findings(), ctx)
        .stream().filter(r -> r.kind() == RemedyKind.REPAIR).findFirst().orElseThrow();

ValidationReport dry = PlanValidator.validate(repair.plan(), ctx);   // rows 1..9
if (!dry.applicable()) { dry.findings().forEach(sink::accept); return; }

try (EditTransaction tx = executor.begin(record.lease().view(), at, claims, ledger.revision(),
        repair.plan().digest())) {
    repair.plan().steps().forEach(tx::apply);
    tx.commit();
}
ApplyReport report = tx.report();                                  // report.context() is post-state
```

The plan has one `Place` over the missing cell with the expected shell descriptor, rung
`MACHINE_APPLICABLE`, and a precondition that the cell is air. The dry run reads the cell, checks
that no exclusive claim is held by another owner, and then folds the plan over the captured view
to re-run every key the provider read: a plan that would change `capacity` is refused with
`SC-RMD-0309` before `begin`. `begin` re-checks the plan digest and the ledger revision, so a
settle that landed between the dry run and the apply is `SC-RMD-0303` and the transaction never
opens. `report.context()` is then a fresh `RemedyContext` over the post-apply capture, and a
second provider round starts there.

`repair.preview(again.findings())` produces the `OverlayModel` the client draws: that cell is
`MISSING`; the rest of the shell is `PRESENT`; the mouth and the cavity are unmarked. Applying the
plan turns the `MISSING` mark into `PRESENT` — preview and repair cannot disagree because both
come from the same plan.

If the cell is owned by another exclusive claim, `PlanValidator` returns `SC-RMD-0303` and the
executor refuses; if the cell is `DENIED`, it returns `SC-RMD-0301` and the repair is not offered.
A preview alone is still useful: it never writes and never needs claims.

## 9. The door: a heteromorphic bay

A two-cell door is one bay, not two:

```java
Bay door = new Bay(NamespacedId.of("mymod", "door"), Cuboid.of(3, 0, 0, 1, 2, 1),
        Coverage.ONE_SUBJECT, Sharing.EXCLUSIVE, new Footprint.Bound(),
        new Requirement(List.of(DoorExpectations.door(Heading.NORTH, DoorHalf.LEFT)),
                        List.of()),
        null);
```

The focus is `door`, so its subject is a `DoorView` — two `OccupantRef`s and one combined state
— and the expectation is written against that same type, never against a `BayView`. The focus
returns both halves in `Found.subjects`, which is the only way the binding is built.

The bay is `ONE_SUBJECT`: `bay.cells` must equal the union of the two halves' footprints, and the
claim is those two positive footprints. A hinge disagreement is one finding with two labels —
`PRIMARY` on the first disagreeing half, `SECONDARY` on the other (`13` §4.4) — not two findings
and not two primaries. The bay claims both cells atomically; a half-door cannot be half-claimed.
The engine never sees a voxel shape; the rectangle is the only span type (R-06).

## 10. What the example proves

| Requirement | Evidence |
| --- | --- |
| R-01 element neutrality | the mouth is a block plus its state; a chest, a cow and a nested structure would use the same `OccupantSnapshot` |
| R-03 structure → context | `volume` is a `ContextKey<Long>` with `Merges.sum()`, contributed per section; `capacity` is a `ContextKey<Long>` written once after the barrier; both are published at `FORM` and read through `StructureContext.at(instance)` |
| R-04 no controller entity | the anchor is a role read at the definition origin; no `Entity` appears in the kernel path |
| R-05 orientation | one equation, `candidate.apply(authoredFront) == anchorHeading`, and one cell read |
| R-06 heteromorphic | the door bay is one two-cell focus with `ONE_SUBJECT` coverage |
| R-07 diagnostics | every failure is a `Finding` with a code, one primary label and a `KEY` or cell locus |
| R-08 generations | the cheap `PROBE` returns `Admitted`; the `FORM` pass publishes at revision 1; `settle` writes the claims; a targeted re-check re-runs only the touched unit |
| R-09 data-driven | §3 compiles the same blueprint from schema 2, including the computations and their phases |
| R-10 one kernel | every predicate, computation and remedy in the example is a catalog entry; the kernel ships only the generic ones |
| R-11 claims | 294 `EXCLUSIVE` cells settle at `settle`, the shell and the cavity both `Box`; a second furnace conflicts with `SC-CLM-0002` |
| R-12 remedies | the repair and the overlay are two projections of one plan, and the plan is refused if it changes a key it read |
| R-13 type safety | sealed `MatchResult`/`Remedy`, typed `ContextKey`/`TraitKey`, no `Map<String,Object>` |
| R-14 simple outside | the first-use path is `siting`, a read-only `check`, a writing `settle`, one switch and one `StructureContext.at` |
| R-15 falsifiable | the counts, the stage transitions, the `Missing` propagation and the rollback are all checkable in a `kernelTest` with a fake view and claims |
