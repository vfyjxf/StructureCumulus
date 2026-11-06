package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.BaySpec;
import dev.vfyjxf.cumulus.kernel.document.BlueprintSpec;
import dev.vfyjxf.cumulus.kernel.document.CheckSpec;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.ComputationSpec;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.KeySpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DoorView;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldCapture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Each test transcribes one code block from docs/design/04-api.md and asserts the documented
 * outcome; deviations are reported, never patched here.
 */
class ApiExamplesTest {

    private static final NamespacedId campfireBlock = NamespacedId.of("minecraft", "campfire");
    private static final NamespacedId stoneBricks = NamespacedId.of("minecraft", "stone_bricks");

    private static NamespacedId id(String path) {
        return NamespacedId.of("mymod", path);
    }

    private static NamespacedId mc(String path) {
        return NamespacedId.of("minecraft", path);
    }

    private static NamespacedId sc(String path) {
        return NamespacedId.of("structurecumulus", path);
    }

    private static LatticePoint point(int x, int y, int z) {
        return LatticePoint.of(x, y, z);
    }

    /** docs/design/04-api.md §2.1. */
    private static Blueprint authorCampfire() {
        NamespacedId campfireId = NamespacedId.of("mymod", "campfire");

        ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class, Retention.instance, Merges.sum());

        CuboidSet shellCells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3))
                .subtract(CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2)))
                .subtract(CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2)));

        Bay shell = Bay.of(id("shell"), shellCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay hollow = Bay.of(
            id("hollow"),
            CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay fire = Bay.of(
            id("fire"),
            CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );

        return Blueprint.builder(campfireId).bounds(Cuboid.of(0, 0, 0, 3, 3, 3)).check(
            Check.of(
                id("shell_bricks"),
                shell,
                Focuses.bay(),
                Expectations.eachCell(Expectations.block(mc("stone_bricks")))
            )
        ).check(Check.of(id("hollow_air"), hollow, Focuses.bay(), Expectations.eachCell(Expectations.air()))).check(
            Check.of(id("fire"), fire, Focuses.occupant(LatticePoint.of(1, 0, 1)), Expectations.block(mc("campfire")))
        ).computation(Computations.air(air, hollow.cells())).build();
    }

    @Test
    void theAuthorCampfireBuilds() {
        Blueprint campfire = authorCampfire();
        assertEquals(NamespacedId.of("mymod", "campfire"), campfire.id());
        assertEquals(Cuboid.of(0, 0, 0, 3, 3, 3), campfire.bounds());
        assertEquals(3, campfire.bays().size());
        assertEquals(3, campfire.checks().size());
        assertEquals(1, campfire.computations().size());
    }

    @Test
    void theHostFlowSettlesCommitsAndDerives() {
        Blueprint campfire = authorCampfire();
        TestWorld.View view = new TestWorld.View();
        for (var cell : TestWorld.stoneShell(campfire.bays().get(0).cells()).entrySet()) {
            view.put(cell.getValue());
        }
        view.air(LatticePoint.of(1, 1, 1));
        view.put(TestWorld.block(campfireBlock, LatticePoint.of(1, 0, 1)));

        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        StructureManager structures = StructureManager.create();
        StructureKernel kernel = StructureKernel
                .create(Catalogs.builtin(), new Host(DocumentSource.empty(), ledger, structures, Map.of()));
        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        Siting turned = MagicFixtures.turn(LatticePoint.origin, Turn.clockwise);
        OwnerId owner = OwnerId.of(id("owner"));
        StructureSpec spec = new StructureSpec(owner, siting, campfire.id());

        // One capture, one immutable view, one lease.
        WorldCapture capture = new MyCapture(view);
        CaptureResult result = capture.capture(new CaptureRequest(campfire.bounds(), SnapshotPolicy.minimal()));
        if (result instanceof CaptureResult.Captured captured) {
            try (SnapshotLease lease = captured.lease()) {
                StructureAttempt attempt = structures.begin(TestWorld.world, spec);
                MatchRequest request = MatchRequest.of(
                    campfire,
                    siting,
                    lease,
                    attempt.id(),
                    owner,
                    Budget.of(4096, 64),
                    new ClaimView(List.of(), 0, true)
                );
                MatchResult checked = kernel.check(request);
                if (checked instanceof MatchResult.Admitted admitted) {
                    MatchResult settled = kernel.settle(request, admitted, attempt);
                    if (settled instanceof MatchResult.Formed formed) {
                        assertEquals(27, formed.claims().rows().size(), "the ledger carries every planned row");
                        assertEquals(1, ledger.revision());
                        assertEquals(27, TestWorld.rows(ledger).size());
                        attempt.commit(CuboidSet.of(campfire.bounds()));
                        assertNotNull(structures.find(attempt.id()));
                        StructureContext values = kernel.derive(formed, campfire.derivations());
                        assertEquals(2, values.revision());
                        ContextKey<Long> air = ContextKeys
                                .of(id("air_cells"), Long.class, Retention.instance, Merges.sum());
                        assertEquals(1L, values.get(air));
                    } else {
                        attempt.abort();
                        fail("the §3 walk settles the campfire, got " + settled);
                    }
                } else {
                    attempt.abort();
                    fail("the §3 walk admits the campfire, got " + checked);
                }
            }
        } else {
            fail("the §3 walk captures the campfire bounds, got " + result);
        }
    }

    @Test
    void theHugeFurnaceBuilds() {
        CuboidSet shell = CuboidSet.of(Cuboid.of(0, 0, 0, 7, 6, 7)).subtract(CuboidSet.of(Cuboid.of(1, 1, 1, 6, 5, 6)))
                .subtract(CuboidSet.of(Cuboid.of(3, 1, 0, 4, 3, 1)));
        Bay wall = Bay.of(id("wall"), shell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Bay cavity = Bay.of(
            id("cavity"),
            CuboidSet.of(Cuboid.of(1, 1, 1, 6, 5, 6)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay mouth = Bay.of(
            id("mouth"),
            CuboidSet.of(Cuboid.of(3, 1, 0, 4, 3, 1)),
            Coverage.oneSubject,
            Footprint.bound(),
            Sharing.exclusive
        );

        ContextKey<Long> volume = ContextKeys.of(id("volume"), Long.class, Retention.instance, Merges.sum());
        ContextKey<Long> capacity = ContextKeys.of(id("capacity"), Long.class, Retention.instance);

        Blueprint furnace = Blueprint.builder(id("huge_furnace")).bounds(Cuboid.of(0, 0, 0, 7, 6, 7)).check(
            Check.of(id("wall_bricks"), wall, Focuses.bay(), Expectations.eachCell(Expectations.block(mc("bricks"))))
        ).check(Check.of(id("cavity_air"), cavity, Focuses.bay(), Expectations.eachCell(Expectations.air())))
                .check(
                    Check.of(id("mouth_door"), mouth, Focuses.door(LatticePoint.of(3, 1, 0)), new OakDoorExpectation())
                ).computation(Computations.volume(volume, cavity.cells())).build();

        assertEquals(Cuboid.of(0, 0, 0, 7, 6, 7), furnace.bounds());
        assertEquals(3, furnace.bays().size());
        assertEquals(3, furnace.checks().size());
        assertEquals(1, furnace.computations().size());
    }

    @Test
    void theDoorBuilds() {
        Bay doorBay = Bay.of(
            id("door"),
            CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1), Cuboid.of(3, 1, 0, 4, 2, 1)),
            Coverage.oneSubject,
            Footprint.bound(),
            Sharing.exclusive
        );
        Blueprint door = Blueprint.builder(id("door")).bounds(Cuboid.of(0, 0, 0, 8, 4, 8))
                .check(
                    Check.of(id("oak_door"), doorBay, Focuses.door(LatticePoint.of(3, 0, 0)), new OakDoorExpectation())
                ).build();

        assertEquals(Cuboid.of(0, 0, 0, 8, 4, 8), door.bounds());
        assertEquals(1, door.bays().size());
        assertEquals(1, door.checks().size());
        assertEquals(2, door.bays().get(0).cells().volume());
    }

    @Test
    void thePipeCornerBuilds() {
        CuboidSet cornerCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 2));
        Bay corner = Bay.of(id("corner"), cornerCells, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        List<Check<?>> checks = new ArrayList<>();
        for (LatticePoint p : List.of(point(0, 0, 0), point(1, 0, 0), point(0, 0, 1), point(1, 0, 1)))
            checks.add(
                Check.of(id("pipe_" + p.x() + p.z()), corner, Focuses.occupant(p), Expectations.block(mc("pipe")))
            );
        for (LatticePoint p : List.of(point(0, 1, 0), point(1, 1, 0), point(0, 1, 1), point(1, 1, 1)))
            checks.add(Check.of(id("air_" + p.x() + p.z()), corner, Focuses.occupant(p), Expectations.air()));
        Cuboid cornerBounds = Objects.requireNonNull(cornerCells.bounds());
        Blueprint pipe = Blueprint.builder(id("pipe_corner")).bounds(cornerBounds).check(checks.get(0))
                .check(checks.get(1)).check(checks.get(2)).check(checks.get(3)).check(checks.get(4))
                .check(checks.get(5)).check(checks.get(6)).check(checks.get(7)).build();

        assertEquals(8, pipe.checks().size());
        Blueprint oneLine = Blueprint.builder(id("pipe_corner")).bounds(cornerBounds).check(checks).build();
        assertEquals(pipe, oneLine, "the check(List) overload is the one-line form of the same example");
    }

    @Test
    void theSharedHatchSettlesTwiceAndRefusesAnExclusiveOwner() {
        CuboidSet hatchCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay hatch = Bay.of(id("hatch"), hatchCells, Coverage.oneSubject, Footprint.bound(), Sharing.shared);
        Check<?> hatchCheck = Check
                .of(id("hatch"), hatch, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant());
        Blueprint ownerA = Blueprint.builder(id("hatch_a")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(hatchCheck)
                .build();
        Blueprint ownerB = Blueprint.builder(id("hatch_b")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(hatchCheck)
                .build();

        TestWorld.View view = new TestWorld.View().put(TestWorld.block(mc("barrel"), LatticePoint.origin));
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        StructureManager structures = StructureManager.create();
        StructureKernel kernel = StructureKernel
                .create(Catalogs.builtin(), new Host(DocumentSource.empty(), ledger, structures, Map.of()));
        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        Siting turned = MagicFixtures.turn(LatticePoint.origin, Turn.clockwise);

        OwnerId ownerOfA = OwnerId.of(id("a"));
        StructureAttempt attemptA = structures.begin(TestWorld.world, new StructureSpec(ownerOfA, siting, ownerA.id()));
        MatchRequest requestA = MatchRequest.of(
            ownerA,
            siting,
            new TestWorld.Lease(view),
            attemptA.id(),
            ownerOfA,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admittedA = assertInstanceOf(MatchResult.Admitted.class, kernel.check(requestA));
        MatchResult.Formed formedA = assertInstanceOf(
            MatchResult.Formed.class,
            kernel.settle(requestA, admittedA, attemptA)
        );
        assertEquals(1, formedA.claims().rows().size(), "owner A settles one hatch row");

        OwnerId ownerOfB = OwnerId.of(id("b"));
        StructureAttempt attemptB = structures.begin(TestWorld.world, new StructureSpec(ownerOfB, turned, ownerB.id()));
        MatchRequest requestB = MatchRequest.of(
            ownerB,
            turned,
            new TestWorld.Lease(view),
            attemptB.id(),
            ownerOfB,
            Budget.unbounded(),
            ledger.read(Cuboid.of(0, 0, 0, 1, 1, 1))
        );
        MatchResult.Admitted admittedB = assertInstanceOf(
            MatchResult.Admitted.class,
            kernel.check(requestB),
            "a shared hatch does not conflict with an incumbent shared row"
        );
        assertInstanceOf(MatchResult.Formed.class, kernel.settle(requestB, admittedB, attemptB));
        assertEquals(2, TestWorld.rows(ledger).size(), "both hatch rows coexist");

        Bay exclusive = Bay.of(id("hatch"), hatchCells, Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);
        Blueprint ownerC = Blueprint.builder(id("hatch_c")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(
                    Check.of(id("hatch"), exclusive, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant())
                ).build();
        MatchRequest requestC = MatchRequest.of(
            ownerC,
            turned,
            new TestWorld.Lease(view),
            new InstanceId(TestWorld.world, 3),
            OwnerId.of(id("c")),
            Budget.unbounded(),
            ledger.read(Cuboid.of(0, 0, 0, 1, 1, 1))
        );
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, kernel.check(requestC));
        assertTrue(
            failed.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.claimConflict.code())),
            "an exclusive owner fails SC-CLM-0001"
        );
        assertTrue(
            failed.findings().findings().stream().anyMatch(finding -> finding.message().contains("already holds")),
            "the failure reports the incumbent rows"
        );
    }

    @Test
    void theClosedWorldEnvelopeFormsWithOnlyTheRingClaimed() {
        CuboidSet torus = CuboidSet.of(Cuboid.of(0, 0, 0, 5, 2, 5)).subtract(CuboidSet.of(Cuboid.of(1, 0, 1, 4, 2, 4)));
        Bay ring = Bay.of(id("ring"), torus, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);

        Blueprint reactor = Blueprint.builder(id("fusion")).bounds(Cuboid.of(0, 0, 0, 5, 5, 5)).check(
            Check.of(id("ring"), ring, Focuses.bay(), Expectations.eachCell(Expectations.block(id("fusion_torus"))))
        ).envelope().build();

        TestWorld.View view = new TestWorld.View();
        for (Cuboid box : torus.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        view.put(TestWorld.block(id("fusion_torus"), LatticePoint.of(x, y, z)));
                    }
                }
            }
        }
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        StructureManager structures = StructureManager.create();
        StructureKernel kernel = StructureKernel
                .create(Catalogs.builtin(), new Host(DocumentSource.empty(), ledger, structures, Map.of()));
        Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
        Siting turned = MagicFixtures.turn(LatticePoint.origin, Turn.clockwise);
        OwnerId owner = OwnerId.of(id("owner"));
        StructureAttempt attempt = structures.begin(TestWorld.world, new StructureSpec(owner, siting, reactor.id()));
        MatchRequest request = MatchRequest.of(
            reactor,
            siting,
            new TestWorld.Lease(view),
            attempt.id(),
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );

        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, kernel.check(request));
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            kernel.settle(request, admitted, attempt)
        );
        assertEquals(torus.volume(), formed.claims().rows().size(), "only the ring cells are claimed");

        view.put(TestWorld.block(id("crystal"), LatticePoint.of(0, 4, 0)));
        TestWorld.Lease refused = new TestWorld.Lease(view);
        MatchRequest crystal = MatchRequest.of(
            reactor,
            siting,
            refused,
            attempt.id(),
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult failed = kernel.check(crystal);
        MatchResult.Failed failure = assertInstanceOf(MatchResult.Failed.class, failed);
        assertTrue(
            failure.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.envelopeOpen.code())),
            "a crystal in the envelope fails SC-EXP-0006"
        );
    }

    @Test
    void theDocumentCampfireLowersToTheAuthorsBlueprint() {
        BlueprintSpec root = new BlueprintSpec(
            NamespacedId.of("mymod", "campfire"),
            Cuboid.of(0, 0, 0, 3, 3, 3),
            false,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    sc("shell"),
                    CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3)).subtract(CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2)))
                            .subtract(CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2))),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null
                ),
                new BaySpec(
                    sc("hollow"),
                    CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2)),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null
                ),
                new BaySpec(
                    sc("fire"),
                    CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2)),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null
                )
            ),
            List.of(),
            List.of(
                new CheckSpec(
                    sc("shell_bricks"),
                    sc("shell"),
                    sc("bay"),
                    Value.object(),
                    sc("each_cell"),
                    Value.object(
                        Map.of(
                            "element",
                            Value.object(
                                Map.of("kind", Value.string("block"), "id", Value.string("minecraft:stone_bricks"))
                            )
                        )
                    ),
                    null,
                    null
                ),
                new CheckSpec(
                    sc("hollow_air"),
                    sc("hollow"),
                    sc("bay"),
                    Value.object(),
                    sc("each_cell"),
                    Value.object(Map.of("element", Value.object(Map.of("kind", Value.string("air"))))),
                    null,
                    null
                ),
                new CheckSpec(
                    sc("fire"),
                    sc("fire"),
                    sc("occupant"),
                    Value.object(
                        Map.of("cell", Value.array(List.of(Value.number(1), Value.number(0), Value.number(1))))
                    ),
                    sc("block"),
                    Value.object(Map.of("id", Value.string("minecraft:campfire"))),
                    null,
                    null
                )
            ),
            List.of(
                new ComputationSpec(
                    NamespacedId.of("mymod", "air_cells"),
                    sc("air"),
                    Value.object(),
                    CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2)),
                    null,
                    NamespacedId.of("mymod", "air_cells"),
                    Set.of(),
                    null
                )
            ),
            List.of(new KeySpec(NamespacedId.of("mymod", "air_cells"), "long", Retention.instance, sc("sum"), null)),
            List.of(),
            List.of(new AnchorSpec(sc("front"), "facing", "north")),
            new OrientationPolicy(
                sc("front"),
                List.of(
                    Orientation.identity,
                    Orientation.of(Turn.clockwise, Tilt.level, Flip.none),
                    Orientation.of(Turn.half, Tilt.level, Flip.none),
                    Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none)
                ),
                true
            ),
            List.of(),
            Map.of(),
            null
        );

        CompileResult result = DocumentCompiler.compile(new Document(1, root, Map.of(), Map.of()), documentContext());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        assertNotNull(result.blueprint(), "the §5 document lowers to a blueprint");

        CuboidSet hollowCells = CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2));
        CuboidSet fireCells = CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2));
        Bay shell = Bay.of(
            sc("shell"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3)).subtract(hollowCells).subtract(fireCells),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay hollow = Bay.of(sc("hollow"), hollowCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay fire = Bay.of(sc("fire"), fireCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        ContextKey<Long> airCells = ContextKeys
                .of(NamespacedId.of("mymod", "air_cells"), Long.class, Retention.instance, Merges.sum());
        Blueprint javaCampfire = Blueprint.builder(NamespacedId.of("mymod", "campfire"))
                .bounds(Cuboid.of(0, 0, 0, 3, 3, 3))
                .check(
                    Check.of(
                        sc("shell_bricks"),
                        shell,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.block(mc("stone_bricks")))
                    )
                ).check(Check.of(sc("hollow_air"), hollow, Focuses.bay(), Expectations.eachCell(Expectations.air())))
                .check(
                    Check.of(
                        sc("fire"),
                        fire,
                        Focuses.occupant(LatticePoint.of(1, 0, 1)),
                        Expectations.block(mc("campfire"))
                    )
                ).computation(Computations.air(airCells, hollowCells))
                .anchor(new AnchorSpec(sc("front"), "facing", "north"))
                .orientation(
                    OrientationPolicy.builder(sc("front"))
                            .allow(
                                Orientation.identity,
                                Orientation.of(Turn.clockwise, Tilt.level, Flip.none),
                                Orientation.of(Turn.half, Tilt.level, Flip.none),
                                Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none)
                            ).uprightOnly().build()
                ).build();
        assertEquals(javaCampfire, result.blueprint(), "the §5 document lowers to the author's blueprint");
    }

    @Test
    void theEnvelopeDocumentAsWrittenLowers() {
        CuboidSet ringCells = CuboidSet.of(Cuboid.of(0, 0, 0, 5, 2, 5))
                .subtract(CuboidSet.of(Cuboid.of(1, 0, 1, 5, 2, 5)));
        BlueprintSpec root = new BlueprintSpec(
            NamespacedId.of("mymod", "fusion"),
            Cuboid.of(0, 0, 0, 5, 5, 5),
            true,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    sc("ring"),
                    ringCells,
                    Coverage.everyCell,
                    Footprint.bound(),
                    Sharing.exclusive,
                    null,
                    null
                ),
                new BaySpec(
                    sc("port"),
                    CuboidSet.of(Cuboid.of(2, 2, 2, 5, 5, 5)),
                    Coverage.everyCell,
                    Footprint.none(),
                    Sharing.shared,
                    null,
                    null
                )
            ),
            List.of(),
            List.of(
                new CheckSpec(
                    sc("ring"),
                    sc("ring"),
                    sc("bay"),
                    Value.object(),
                    sc("each_cell"),
                    Value.object(
                        Map.of(
                            "element",
                            Value.object(
                                Map.of("kind", Value.string("block"), "id", Value.string("mymod:fusion_torus"))
                            )
                        )
                    ),
                    null,
                    null
                ),
                new CheckSpec(
                    sc("port"),
                    sc("port"),
                    sc("bay"),
                    Value.object(),
                    sc("each_cell"),
                    Value.object(Map.of("element", Value.object(Map.of("kind", Value.string("any_occupant"))))),
                    null,
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null
        );

        CompileResult result = DocumentCompiler.compile(new Document(1, root, Map.of(), Map.of()), documentContext());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint reactor = result.blueprint();
        assertNotNull(reactor, "the §6.5 document as written lowers to a blueprint");
        assertEquals(3, reactor.bays().size(), "ring, port and the reserved envelope bay");
        assertEquals(3, reactor.checks().size(), "ring, port and the reserved envelope check");
    }

    private static CompilerContext documentContext() {
        Catalogs catalogs = Catalogs.builtin();
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
    }

    private record OakDoorExpectation() implements Expectation<DoorView> {

        @Override
        public Reply test(ExpectationContext<DoorView> ctx) {
            return Reply.match();
        }

        @Override
        public Class<DoorView> subject() {
            return DoorView.class;
        }
    }

    private static final class MyCapture implements WorldCapture {

        private final TestWorld.View view;

        private MyCapture(TestWorld.View view) {
            this.view = view;
        }

        @Override
        public CaptureResult capture(CaptureRequest request) {
            return new CaptureResult.Captured(view, new TestWorld.Lease(view));
        }

        @Override
        public long currentRevision() {
            return 0;
        }
    }
}
