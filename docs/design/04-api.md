# 04 — API

> The surfaces a user touches, and the examples that define "good" for this library. A signature in
> this file must exist in the module files; an example that cannot be written from those signatures
> is a design defect.

## 1. The three surfaces

| Surface | Types | What it is for |
| --- | --- | --- |
| **Author** | `Blueprint`, `BlueprintBuilder`, `Bay`, `Check`, `Focuses`, `Expectations`, `ContextKeys`, `Computations` | declare a structure in Java |
| **Addon** | `Expectation`, `Focus`, `Computation`, `Deriver`, `RemedyProvider`, `TraitKey`, `ContextKey` | teach the kernel one domain |
| **Host** | `WorldCapture`, `WorldView`, `SnapshotLease`, `ClaimLedger`, `StructureManager`, `DocumentReader`, `DocumentWriter` | bind the kernel to a world, a store and a format |

The facade is one final class, `StructureKernel`, with four pipeline operations (`compile`, `check`,
`settle`, `derive`), three helpers (`solve`, `explain`, `explainOrFallback`) and four lookups
(`document`, `ledger`, `contexts`, `catalogs`). Anchor resolution is host work (`McAnchors` in the
adapter); the facade has no `siting` operation.

## 2. The author surface

### 2.1 The shape

```java
NamespacedId id = NamespacedId.of("mymod", "campfire");

ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class,
        Retention.instance, Merges.sum(), null);

CuboidSet shellCells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3))
        .subtract(CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2)))
        .subtract(CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2)));

Bay shell = Bay.of(id("shell"), shellCells, Coverage.everyCell,
        Footprint.box(), Sharing.exclusive);
Bay hollow = Bay.of(id("hollow"), CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2)),
        Coverage.everyCell, Footprint.box(), Sharing.exclusive);
Bay fire = Bay.of(id("fire"), CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2)),
        Coverage.everyCell, Footprint.box(), Sharing.exclusive);

Blueprint campfire = Blueprint.builder(id)
        .bounds(Cuboid.of(0, 0, 0, 3, 3, 3))
        .check(Check.of(id("shell_bricks"), shell, Focuses.bay(),
                Expectations.eachCell(Expectations.block(mc("stone_bricks")))))
        .check(Check.of(id("hollow_air"), hollow, Focuses.bay(),
                Expectations.eachCell(Expectations.air())))
        .check(Check.of(id("fire"), fire, Focuses.occupant(LatticePoint.of(1, 0, 1)),
                Expectations.block(mc("campfire"))))
        .computation(Computations.air(air, hollow.cells()))
        .build();
```

This is the 10–20-line target. It is explicit: every bay, every check, every key. There is no
builder knot, no `Requirement`, no stage, and no hidden default that changes a verdict.

### 2.2 Rules the author relies on

- `Bay.cells` is a `CuboidSet`. A shell is a set subtraction, not six synthetic bays and not a
  voxel list.
- `Check.of(id, bay, focus, expectation)` carries the `Bay` value. The builder adds the bay to the
  blueprint; there is no separate `bay(...)` call and no "check without a bay" state.
- A check whose focus binds a subject declares it through the focus; a check that only reads a
  context key uses `Focuses.context(key)` and must live in an `anySubject` bay.
- `Coverage` is enforced at `Blueprint.build()`: `everyCell` needs the checks' static cells to
  cover the bay; `anySubject` forbids static cells; `oneSubject` needs a binding focus and, at
  settle, a bound footprint equal to the bay.
- `Footprint` is independent of `Coverage`: `everyCell + Bound` asserts eight and owns four (pipe
  corner); `everyCell + Box` protects an empty volume (furnace cavity); `oneSubject + Bound`
  owns exactly the subject's cells (door); `None` owns nothing and is illegal with `exclusive`.
- Two bays of one blueprint may overlap only when both are `shared`; any other overlap is a build
  error. There is no pairwise `overlap` declaration.
- A nested focus (`Focuses.nested`) binds a child view but no claim subject: a nested structure is
  checkable, never claimable through the nested body (`engine` §7.5).
- A trait read has four states. An addon uses `OccupantSnapshot.slot(id)` (or `find`) to separate
  `Absent` from `Refused`/unasked; a refused or unasked trait is `Unreadable`, never `Mismatch`.

## 3. The host surface

```java
// One capture, one immutable view, one lease.
WorldCapture capture = new MyCapture(level);              // host implementation
CaptureResult result = capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
if (result instanceof CaptureResult.Captured captured) {
    try (SnapshotLease lease = captured.lease()) {
        StructureAttempt attempt = structures.begin(level, spec);
        MatchRequest request = MatchRequest.of(campfire, siting, lease, attempt.id(), owner,
                Budget.of(4096, 64), claims);
        MatchResult checked = kernel.check(request);
        if (checked instanceof MatchResult.Admitted admitted) {
            MatchResult settled = kernel.settle(request, admitted, attempt);
            if (settled instanceof MatchResult.Formed formed) {
                attempt.commit(bounds);                 // the host's world-frame cells
                StructureContext values = kernel.derive(formed, campfire.derivations());
            } else {
                attempt.abort();
            }
        } else {
            attempt.abort();
        }
    }
}
```

A settle prepares: the claims are written and the values are staged in the attempt. `commit` forms
the instance; a failed settle is rolled back with `abort`, and a `Formed` that is not wanted is
rolled back with `ledger.release(instance)` before `abort`.

The host owns the lease. The kernel never closes it, never retains it and never hands a child
handle back. A result is a pure value; a lease is a resource the host scopes. An anchored blueprint
resolves its `Siting` in the host: `McAnchors.resolve(blueprint, origin, view)` reads the captured
anchor cell and answers the `SitingResult` the request needs; the facade has no `siting` call.

### 3.1 Host data

The kernel models no block state. A host that has one exposes it as a typed read:

```java
HostType<BlockState> blockState = HostType.of(NamespacedId.of("mymod", "block_state"));
BlockState state = lease.view().get(LatticePoint.of(1, 0, 1), blockState);
```

`HostType<H>` carries an id, never a `Class<?>`; `WorldView.get` defaults to `null` and a capture
answers from its one snapshot, never from the live level. The adapter owns the constants
(`HostTypes.blockState`, `fluidState`, `entity`, `blockEntity`).

A host-fact expectation is
`Expectations.host(type, test, mismatchCode, absentCode, description)`: one typed read at the
subject's ref; a missing value is `Unreadable` with the caller's absent code, and a false
predicate is `Mismatch`. Both codes come from the caller; the kernel never mints an MC code. The
adapter registers `property`, `state`, `tag` and `fluid` as ordinary check kinds in its document
catalog, and each lowers to one or two `Expectations.host` leaves. The `fluid` kind is two leaves
under `allOf`, so its `ExpectationMeta` is the kernel junction's `SC-EXP-0001` while its findings are
the leaves' `SC-MC-0009`.

## 4. The addon surface

```java
final class HatchExpectation implements Expectation<OccupantSnapshot> {
    static final NamespacedId id = id("hatch");
    static final TraitKey<IItemHandler> inventory = new TraitKey<>(id("inventory"), IItemHandler.class);

    @Override public Class<OccupantSnapshot> subject() { return OccupantSnapshot.class; }
    @Override public ExpectationMeta meta() {
        return ExpectationMeta.of(id, 80, Codes.expected.code());
    }
    @Override public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
        OccupantSnapshot occupant = ctx.subject();
        return switch (occupant.slot(inventory.id())) {
            case TraitSlot.Present present -> Reply.match();
            case TraitSlot.Absent absent -> Reply.mismatch(Finding.builder(Codes.expected)
                    .phase(ctx.phase())
                    .message("expected an inventory-bearing hatch")
                    .label(Label.primary(ctx.locus(), "expected an inventory"))
                    .build());
            case TraitSlot.Refused refused -> Reply.unreadable(Finding.builder(Codes.unreadableRead)
                    .phase(ctx.phase())
                    .message("the inventory trait is unreadable")
                    .label(Label.primary(ctx.locus(), "the inventory could not be read"))
                    .note("refusal: " + refused.refusal().status())
                    .build());
            case null -> Reply.unreadable(Finding.builder(Codes.unreadableRead)
                    .phase(ctx.phase())
                    .message("the inventory trait was not captured")
                    .label(Label.primary(ctx.locus(), "the inventory was not asked for"))
                    .build());
        };
    }
}
```

One interface, one catalog registration, no engine access. `ctx.phase()` is always `check`.

The standard `structurecumulus:inventory` trait value is the live `IItemHandler` itself, so the check
above reads `((IItemHandler) present.trait().value()).getStackInSlot(0)` when it needs slot contents;
there is no copied slot list. The value is live: a check that reads it accepts dependence on world
state, and a check must never write through it (D-41). A blueprint that needs capture-time contents
registers a captured trait. The same rule holds for `energy` (`IEnergyStorage`) and `fluid`
(`IFluidHandler`): a trait carries the owning API object, never a framework wrapper.

## 5. The document surface

The same campfire as data. A document is the authoring form; the compiler lowers it to the same
`Blueprint`. A computation's `id` is optional: a computation is identified by the key it produces,
so the two frontends cannot disagree on it.

```json
{
  "schemaVersion": 1,
  "id": "mymod:campfire",
  "bounds": [0, 0, 0, 3, 3, 3],
  "bays": [
    {"id": "shell", "boxes": [[0,0,0,3,3,3]], "minus": [[1,1,1,2,2,2], [1,0,1,2,1,2]],
     "coverage": "every_cell", "footprint": "box", "sharing": "exclusive"},
    {"id": "hollow", "box": [1,1,1,2,2,2], "coverage": "every_cell",
     "footprint": "box", "sharing": "exclusive"},
    {"id": "fire", "box": [1,0,1,2,1,2], "coverage": "every_cell",
     "footprint": "box", "sharing": "exclusive"}
  ],
  "checks": [
    {"id": "shell_bricks", "bay": "shell", "focus": {"kind": "bay"},
     "expect": {"kind": "each_cell", "element": {"kind": "block", "id": "minecraft:stone_bricks"}}},
    {"id": "hollow_air", "bay": "hollow", "focus": {"kind": "bay"},
     "expect": {"kind": "each_cell", "element": {"kind": "air"}}},
    {"id": "fire", "bay": "fire", "focus": {"kind": "occupant", "cell": [1,0,1]},
     "expect": {"kind": "block", "id": "minecraft:campfire"}}
  ],
  "computations": [
    {"kind": "air", "region": [[1,1,1,2,2,2]], "output": "mymod:air_cells"}
  ],
  "keys": [
    {"id": "mymod:air_cells", "type": "long", "retention": "instance", "merge": "sum"}
  ],
  "anchors": [{"role": "front", "property": "facing", "value": "north"}],
  "orientation": {"primary": "front", "allowed": ["identity", "clockwise", "half", "counterclockwise"],
                  "upright": true}
}
```

The keys of the schema are fixed: `schemaVersion`, `id`, `bounds`, `envelope`, `definitions`,
`placements`, `bays`, `checks`, `computations`, `keys`, `derivations`, `anchors`, `orientation`,
`arguments`, `extensions`. A member outside that set is an error, never ignored. Boxes are
`[x, y, z, sizeX, sizeY, sizeZ]`; a `boxes` array is a `CuboidSet`; `minus` subtracts boxes. The
Java DSL and the JSON compiler produce the same `Blueprint` (I-13).

## 6. Four worked cases

### 6.1 Huge furnace (shell + cavity + context)

```java
CuboidSet shell = CuboidSet.of(Cuboid.of(0, 0, 0, 7, 6, 7))
        .subtract(CuboidSet.of(Cuboid.of(1, 1, 1, 6, 5, 6)))
        .subtract(CuboidSet.of(Cuboid.of(3, 1, 0, 4, 3, 1)));
Bay wall = Bay.of(id("wall"), shell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
Bay cavity = Bay.of(id("cavity"), CuboidSet.of(Cuboid.of(1, 1, 1, 6, 5, 6)),
        Coverage.everyCell, Footprint.box(), Sharing.exclusive);
Bay mouth = Bay.of(id("mouth"), CuboidSet.of(Cuboid.of(3, 1, 0, 4, 3, 1)),
        Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);

ContextKey<Long> volume = ContextKeys.of(id("volume"), Long.class, Retention.instance, Merges.sum(), null);
ContextKey<Long> capacity = ContextKeys.of(id("capacity"), Long.class, Retention.instance);

Blueprint furnace = Blueprint.builder(id("huge_furnace"))
        .bounds(Cuboid.of(0, 0, 0, 7, 6, 7))
        .check(Check.of(id("wall_bricks"), wall, Focuses.bay(),
                Expectations.eachCell(Expectations.block(mc("bricks")))))
        .check(Check.of(id("cavity_air"), cavity, Focuses.bay(),
                Expectations.eachCell(Expectations.air())))
        .check(Check.of(id("mouth_door"), mouth, Focuses.door(LatticePoint.of(3, 1, 0)),
                new OakDoorExpectation()))
        .computation(Computations.volume(volume, cavity.cells()))
        .build();
```

`capacity` is produced by a `Deriver` registered as `mymod:capacity`; the blueprint's
`DerivationSpec` names it. A deriver is a value from a catalog, never an object inside a blueprint.

### 6.2 Door (one bay, two subjects, one claim)

```java
Bay doorBay = Bay.of(id("door"),
        CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1), Cuboid.of(3, 1, 0, 4, 2, 1)),
        Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);
Blueprint door = Blueprint.builder(id("door"))
        .bounds(Cuboid.of(0, 0, 0, 8, 4, 8))
        .check(Check.of(id("oak_door"), doorBay, Focuses.door(LatticePoint.of(3, 0, 0)),
                new OakDoorExpectation()))
        .build();
```

A door that binds one half is `Unavailable`, never a one-cell claim. `oneSubject` refuses a bound
footprint that is not the bay at settle.

### 6.3 Pipe corner (eight asserted, four owned)

```java
CuboidSet cornerCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 2));
Bay corner = Bay.of(id("corner"), cornerCells, Coverage.everyCell,
        Footprint.bound(), Sharing.exclusive);
List<Check<?>> checks = new ArrayList<>();
for (LatticePoint p : List.of(point(0,0,0), point(1,0,0), point(0,0,1), point(1,0,1)))
    checks.add(Check.of(id("pipe_" + p.x() + p.z()), corner, Focuses.occupant(p),
            Expectations.block(mc("pipe"))));
for (LatticePoint p : List.of(point(0,1,0), point(1,1,0), point(0,1,1), point(1,1,1)))
    checks.add(Check.of(id("air_" + p.x() + p.z()), corner, Focuses.occupant(p),
            Expectations.air()));
Blueprint pipe = Blueprint.builder(id("pipe_corner"))
        .bounds(cornerCells.bounds())
        .check(checks.get(0)).check(checks.get(1)).check(checks.get(2)).check(checks.get(3))
        .check(checks.get(4)).check(checks.get(5)).check(checks.get(6)).check(checks.get(7))
        .build();
```

A `BlueprintBuilder.check(List<? extends Check<?>>)` overload exists for exactly this case, so the
example is one line. `Bound` owns the four pipe cells; the four air cells are asserted, not owned.

### 6.4 Shared hatch (two owners, no conflict)

Two blueprints, one hatch bay with `Footprint.bound()` and `Sharing.shared`. Owner A settles; owner
B checks and settles; both rows coexist. An `exclusive` owner C then checks: `Failed(SC-CLM-0001)`
with the incumbent rows. `claim` owns the matrix; the kernel never invents a second predicate.

### 6.5 Closed-world envelope (fusion torus)

`BlueprintBuilder.envelope()` closes the declared bounds: every cell that no bay or declared
footprint covers must be strict air. It is one call on the same builder, not a second blueprint.

```java
CuboidSet torus = CuboidSet.of(Cuboid.of(0, 0, 0, 5, 2, 5))
        .subtract(CuboidSet.of(Cuboid.of(1, 0, 1, 4, 2, 4)));   // the 18-cell hollow
Bay ring = Bay.of(id("ring"), torus, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);

Blueprint reactor = Blueprint.builder(id("fusion"))
        .bounds(Cuboid.of(0, 0, 0, 5, 5, 5))
        .check(Check.of(id("ring"), ring, Focuses.bay(),
                Expectations.eachCell(Expectations.block(mc("fusion_torus")))))
        .envelope()
        .build();
```

The builder derives `E = bounds − (bays ∪ declared footprints)`, so `E` is the 93 cells of the
torus void and the empty rest of the 5×5×5 frame. A crystal in the void is
`Failed(SC-EXP-0006)` at its cell; clean air is `Formed` with only the 32 ring cells claimed. A port
is not a hole in `E`; it is a declared bay, which is also how an "any occupant" or ignored cell is
expressed:

```json
{
  "schemaVersion": 1,
  "id": "mymod:fusion",
  "bounds": [0, 0, 0, 5, 5, 5],
  "envelope": true,
  "bays": [
    {"id": "ring", "boxes": [[0,0,0,5,2,5]], "minus": [[1,0,1,4,2,4]],
     "coverage": "every_cell", "footprint": "bound", "sharing": "exclusive"},
    {"id": "port", "box": [2,2,2,3,3,3], "coverage": "every_cell",
     "footprint": "none", "sharing": "shared"}
  ],
  "checks": [
    {"id": "ring", "bay": "ring", "focus": {"kind": "bay"},
     "expect": {"kind": "each_cell", "element": {"kind": "block", "id": "mymod:fusion_torus"}}},
    {"id": "port", "bay": "port", "focus": {"kind": "bay"},
     "expect": {"kind": "each_cell", "element": {"kind": "any_occupant"}}}
  ]
}
```

`envelope` is root-only and absent means off; a definition that carries it is `SC-DOC-0002`. A block
outside `bounds` stays invisible, and an unreadable envelope cell is `Undecided(SC-OCC-0004)`, never
a mismatch. The flag moves no schema version.

## 7. What the user never writes

- a `MatchSession`, a `MatchPolicy`, a `Stage`, a `ContextTier`, a `ReadSet`, a `ClaimPlan`;
- a lease close on a result (the host scopes the lease);
- a `Requirement`, a `BayBuilder`, a `Segment`, an `Overlap` declaration;
- a JSON profile flag, a schema upgrade, a canonicaliser;
- a per-cell loop to express a shell (use `CuboidSet.subtract`).

## 8. The facade, in full

```java
public final class StructureKernel {
    public static StructureKernel create(Catalogs catalogs);
    public static StructureKernel create(Catalogs catalogs, Host host);

    public Catalogs catalogs();
    public @Nullable Document document(NamespacedId id);
    public @Nullable ClaimLedger ledger();
    public ContextStore contexts();
    public StructureManager structures();

    public CompileResult compile(Document document);
    public MatchResult check(MatchRequest request);
    public MatchResult settle(MatchRequest request, MatchResult.Admitted admitted, StructureAttempt attempt);
    public StructureContext derive(MatchResult.Formed formed, List<DerivationSpec> derivations);
    public SolveResult solve(FindingBundle findings, RemedyContext context);
    public @Nullable Explanation explain(FindingCode code);
    public Explanation explainOrFallback(FindingCode code);
}
```

Four pipeline operations, three helpers, four lookups. `settle` takes the request, the admitted
result and the attempt that reserved the instance; it finds its ledger in the kernel's `Host`. `structures()` exposes the same host's manager; `contexts()` is its narrow engine port;
per-instance store so a host can remove a rolled-back set member's context (D-35). `create(catalogs)`
is the no-host form for compile-only use; `check` with a lease works, `settle` reports
`Undecided(ledgerUnavailable)`.
`McAnchors.resolve(...)` is the adapter's anchor operation, not a facade method.
