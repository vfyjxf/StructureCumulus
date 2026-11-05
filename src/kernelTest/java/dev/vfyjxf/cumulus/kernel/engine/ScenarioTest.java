package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.DoorView;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.FocusResult;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.structure.SectionComputation;
import dev.vfyjxf.cumulus.kernel.structure.SectionContext;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {

    private static final Siting atOrigin = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final Siting turned = MagicFixtures.turn(LatticePoint.origin, Turn.clockwise);
    private static final OwnerId owner = OwnerId.of(id("owner"));
    private static final InstanceId instance = new InstanceId(TestWorld.world, 1);

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId mc(String path) {
        return NamespacedId.of("minecraft", path);
    }

    @Test
    void campfireChecksSettlesAndDerives() {
        CuboidSet shellCells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3)).subtract(Cuboid.of(1, 1, 1, 2, 2, 2))
                .subtract(Cuboid.of(1, 0, 1, 2, 1, 2));
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

        ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class, Retention.instance, Merges.sum());
        ContextKey<Long> capacity = ContextKeys.of(id("capacity"), Long.class, Retention.instance);
        DerivationSpec capacitySpec = new DerivationSpec(id("capacity_deriver"), Value.object(), capacity.id(), 0);

        Blueprint campfire = Blueprint.builder(id("campfire")).bounds(Cuboid.of(0, 0, 0, 3, 3, 3))
                .check(
                    Check.of(
                        id("shell_bricks"),
                        shell,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.block(TestWorld.stone))
                    )
                ).check(Check.of(id("hollow_air"), hollow, Focuses.bay(), Expectations.eachCell(Expectations.air())))
                .check(
                    Check.of(
                        id("lit"),
                        fire,
                        Focuses.occupant(LatticePoint.of(1, 0, 1)),
                        Expectations.block(TestWorld.campfire)
                    )
                ).computation(Computations.air(air, hollow.cells())).derive(capacitySpec).build();

        TestWorld.View view = new TestWorld.View();
        for (Map.Entry<LatticePoint, OccupantSnapshot> entry : TestWorld.stoneShell(shellCells).entrySet()) {
            view.put(entry.getValue());
        }
        view.air(LatticePoint.of(1, 1, 1));
        view.put(TestWorld.block(TestWorld.campfire, LatticePoint.of(1, 0, 1)));
        TestWorld.Lease lease = new TestWorld.Lease(view);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest
                .of(campfire, atOrigin, lease, instance, owner, Budget.unbounded(), new ClaimView(List.of(), 0, true));

        MatchResult checked = MatchEngine.check(request);
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, checked);
        assertEquals(1L, admitted.outcome().value(air).fold(v -> v, () -> -1L));
        assertEquals(0, ledger.revision());
        assertEquals(0, TestWorld.rows(ledger).size());

        TestAttempt attempt = TestAttempt.of(request);
        MatchResult settled = MatchEngine.settle(request, admitted, ledger, attempt);
        MatchResult.Formed formed = assertInstanceOf(MatchResult.Formed.class, settled);
        assertEquals(27, formed.claims().rows().size());
        assertEquals(new Spend(28, 4), formed.outcome().spend());
        assertEquals(1, formed.contextRevision());
        assertEquals(1, ledger.revision());

        Deriver<Long> capacityDeriver = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return capacity;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of(air);
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return ctx.require(air).fold(value -> Outcome.found(value * 10L), Outcome::missing);
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers"))
                .put(id("capacity_deriver"), capacityDeriver).build();
        StructureContext context = MatchEngine.derive(formed, campfire.derivations(), derivers, attempt.store());
        assertEquals(1L, context.get(air));
        assertEquals(10L, context.get(capacity));
        assertEquals(2, context.revision());
        assertEquals(formed.outcome().spend(), context.spend());
        StructureContext again = MatchEngine.derive(formed, campfire.derivations(), derivers, attempt.store());
        assertEquals(formed.outcome().spend(), again.spend());
        assertEquals(3, again.revision());
    }

    @Test
    void doorOwnsExactlyItsBoundCells() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1), Cuboid.of(3, 1, 0, 4, 2, 1));
        Bay bay = Bay.of(id("door"), cells, Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);
        Check<?> check = Check.of(
            id("oak_door"),
            bay,
            Focuses.door(LatticePoint.of(3, 0, 0)),
            Expectations.custom(
                DoorView.class,
                ExpectationMeta.of(id("door_test"), 10, FindingCode.of(FindingCode.Family.exp, 1)),
                ctx -> Reply.match()
            )
        );
        Blueprint blueprint = Blueprint.builder(id("door")).bounds(Cuboid.of(0, 0, 0, 8, 4, 8)).check(check).build();
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(TestWorld.door, LatticePoint.of(3, 0, 0)))
                .put(TestWorld.block(TestWorld.door, LatticePoint.of(3, 1, 0)));
        TestWorld.Lease lease = new TestWorld.Lease(view);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest
                .of(blueprint, atOrigin, lease, instance, owner, Budget.unbounded(), new ClaimView(List.of(), 0, true));
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(request, admitted, ledger, TestAttempt.of(request))
        );
        assertEquals(2, formed.claims().rows().size());
    }

    @Test
    void sharedHatchCoexistsAndExclusiveConflicts() {
        CuboidSet cell = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Focus<OccupantSnapshot> focus = new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot occupant = ctx.occupantAt(LatticePoint.origin);
                if (occupant == null || occupant.isAir()) {
                    Finding finding = Finding.builder(Codes.unreadableCell).phase(Phase.check).message("no hatch")
                            .label(Label.primary(ctx.locus(), "no hatch")).build();
                    return new FocusResult.Unavailable<>(FindingBundle.of(List.of(finding)));
                }
                return new FocusResult.Taken<>(occupant, List.of(occupant), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public boolean bindsClaimableSubject() {
                return true;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }
        };
        Bay shared = Bay.of(id("hatch"), cell, Coverage.anySubject, Footprint.bound(), Sharing.shared);
        Blueprint sharedBlueprint = Blueprint.builder(id("shared_hatch"))
                .bounds(
                    Objects.requireNonNull(cell.bounds(), "bounds")
                            .bounding(Objects.requireNonNull(cell.bounds(), "bounds"))
                ).check(Check.of(id("hatch"), shared, focus, Expectations.anyOccupant())).build();
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(mc("barrel"), LatticePoint.origin));
        TestWorld.Lease lease = new TestWorld.Lease(view);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest first = MatchRequest.of(
            sharedBlueprint,
            atOrigin,
            lease,
            instance,
            OwnerId.of(id("a")),
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Formed firstFormed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                first,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(first)),
                ledger,
                TestAttempt.of(first)
            )
        );
        assertEquals(1, firstFormed.claims().rows().size());

        MatchRequest second = MatchRequest.of(
            sharedBlueprint,
            turned,
            lease,
            new InstanceId(TestWorld.world, 2),
            OwnerId.of(id("b")),
            Budget.unbounded(),
            ledger.read(cell.bounds())
        );
        MatchResult.Formed secondFormed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                second,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(second)),
                ledger,
                TestAttempt.of(second)
            )
        );
        assertEquals(2, TestWorld.rows(ledger).size());

        Bay exclusive = Bay.of(id("hatch"), cell, Coverage.anySubject, Footprint.bound(), Sharing.exclusive);
        Blueprint exclusiveBlueprint = Blueprint.builder(id("exclusive_hatch"))
                .bounds(
                    Objects.requireNonNull(cell.bounds(), "bounds")
                            .bounding(Objects.requireNonNull(cell.bounds(), "bounds"))
                ).check(Check.of(id("hatch"), exclusive, focus, Expectations.anyOccupant())).build();
        MatchRequest third = MatchRequest.of(
            exclusiveBlueprint,
            turned,
            lease,
            new InstanceId(TestWorld.world, 3),
            OwnerId.of(id("c")),
            Budget.unbounded(),
            ledger.read(cell.bounds())
        );
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, MatchEngine.check(third));
        assertTrue(
            failed.findings().findings().stream().anyMatch(finding -> finding.code().equals(Codes.claimConflict.code()))
        );
    }

    @Test
    void staleLeaseDropsAndTinyBudgetUndecides() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("one")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air())).build();
        TestWorld.View view = new TestWorld.View().air(LatticePoint.origin);
        TestWorld.Lease lease = new TestWorld.Lease(view);
        MatchRequest request = MatchRequest
                .of(blueprint, atOrigin, lease, instance, owner, Budget.of(0, 0), new ClaimView(List.of(), 0, true));
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.budgetExhausted, undecided.reason());
        assertTrue(
            undecided.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.budgetExhausted.code()))
        );
        lease.invalidate();
        assertInstanceOf(MatchResult.Dropped.class, MatchEngine.check(request));
    }

    @Test
    void doorWithOneHalfIsUndecided() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1), Cuboid.of(3, 1, 0, 4, 2, 1));
        Bay bay = Bay.of(id("door"), cells, Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);
        Check<?> check = Check.of(
            id("oak_door"),
            bay,
            Focuses.door(LatticePoint.of(3, 0, 0)),
            Expectations.custom(
                DoorView.class,
                ExpectationMeta.of(id("door_test"), 10, FindingCode.of(FindingCode.Family.exp, 1)),
                ctx -> Reply.match()
            )
        );
        Blueprint blueprint = Blueprint.builder(id("door")).bounds(Cuboid.of(0, 0, 0, 8, 4, 8)).check(check).build();
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(TestWorld.door, LatticePoint.of(3, 0, 0)));
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.unreadable, undecided.reason());
    }

    @Test
    void pipeCornerAssertsEightAndOwnsFour() {
        CuboidSet corner = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 2));
        Bay bay = Bay.of(id("corner"), corner, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        TestWorld.View view = new TestWorld.View();
        List<Check<?>> checks = new ArrayList<>();
        for (LatticePoint cell : List.of(
            LatticePoint.of(0, 0, 0),
            LatticePoint.of(1, 0, 0),
            LatticePoint.of(0, 0, 1),
            LatticePoint.of(1, 0, 1)
        )) {
            checks.add(
                Check.of(id("pipe_" + cell.x() + cell.z()), bay, Focuses.occupant(cell), Expectations.block(mc("pipe")))
            );
            view.put(TestWorld.block(mc("pipe"), cell));
        }
        for (LatticePoint cell : List.of(
            LatticePoint.of(0, 1, 0),
            LatticePoint.of(1, 1, 0),
            LatticePoint.of(0, 1, 1),
            LatticePoint.of(1, 1, 1)
        )) {
            checks.add(Check.of(id("air_" + cell.x() + cell.z()), bay, Focuses.occupant(cell), Expectations.air()));
            view.air(cell);
        }
        Blueprint blueprint = Blueprint.builder(id("pipe")).bounds(Cuboid.of(0, 0, 0, 2, 2, 2)).checks(checks).build();
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                request,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request)),
                ledger,
                TestAttempt.of(request)
            )
        );
        assertEquals(4, formed.claims().rows().size());
    }

    @Test
    void entityPenOwnsItsBoundFootprint() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay bay = Bay.of(id("pen"), cells, Coverage.anySubject, Footprint.bound(), Sharing.shared);
        Focus<OccupantSnapshot> entityFocus = new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot occupant = ctx.occupantAt(LatticePoint.origin);
                if (occupant == null || occupant.isAir()) {
                    Finding finding = Finding.builder(Codes.unreadableCell).phase(Phase.check).message("no entity")
                            .label(Label.primary(ctx.locus(), "no entity")).build();
                    return new FocusResult.Unavailable<>(FindingBundle.of(List.of(finding)));
                }
                return new FocusResult.Taken<>(occupant, List.of(occupant), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public boolean bindsClaimableSubject() {
                return true;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }
        };
        Blueprint blueprint = Blueprint.builder(id("pen")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(Check.of(id("cow"), bay, entityFocus, Expectations.anyOccupant())).build();
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, 0, mc("cow"), OccupantKind.entity, LatticePoint.origin);
        OccupantSnapshot cow = new OccupantSnapshot(
            ref,
            new OccupantDescriptor(mc("cow"), OccupantKind.entity),
            Cuboid.of(0, 0, 0, 2, 1, 1),
            List.of()
        );
        TestWorld.View view = new TestWorld.View().put(cow);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                request,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request)),
                ledger,
                TestAttempt.of(request)
            )
        );
        assertEquals(2, formed.claims().rows().size());
    }

    @Test
    void refusedCellMakesTheAttemptUndecided() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class, Retention.instance, Merges.sum());
        Blueprint blueprint = Blueprint.builder(id("refused")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air()))
                .computation(Computations.air(air, cells)).build();
        TestWorld.View view = new TestWorld.View().refuse(LatticePoint.origin);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(
            undecided.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.unreadableRead.code()))
        );
    }

    @Test
    void revisionMovedOnceRetriesAndSharedRowsCoexist() {
        CuboidSet cell = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Focus<OccupantSnapshot> focus = new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot occupant = ctx.occupantAt(LatticePoint.origin);
                if (occupant == null || occupant.isAir()) {
                    Finding finding = Finding.builder(Codes.unreadableCell).phase(Phase.check).message("no hatch")
                            .label(Label.primary(ctx.locus(), "no hatch")).build();
                    return new FocusResult.Unavailable<>(FindingBundle.of(List.of(finding)));
                }
                return new FocusResult.Taken<>(occupant, List.of(occupant), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public boolean bindsClaimableSubject() {
                return true;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }
        };
        Bay hatch = Bay.of(id("hatch"), cell, Coverage.anySubject, Footprint.bound(), Sharing.shared);
        Blueprint blueprint = Blueprint.builder(id("hatch")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("hatch"), hatch, focus, Expectations.anyOccupant())).build();
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(mc("barrel"), LatticePoint.origin));
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest a = MatchRequest.of(
            blueprint,
            turned,
            new TestWorld.Lease(view),
            instance,
            OwnerId.of(id("a")),
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(a));
        MatchRequest b = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            new InstanceId(TestWorld.world, 2),
            OwnerId.of(id("b")),
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                b,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(b)),
                ledger,
                TestAttempt.of(b)
            )
        );
        assertEquals(1, ledger.revision());
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(a, admitted, ledger, TestAttempt.of(a))
        );
        assertEquals(1, formed.claims().rows().size(), "a settlement returns the rows it wrote");
        assertEquals(2, TestWorld.rows(ledger).size(), "both shared rows coexist in the ledger");
        assertEquals(2, ledger.revision());
    }

    @Test
    void furnaceFormsWithDoorAndCavityVolume() {
        CuboidSet bounds = CuboidSet.of(Cuboid.of(0, 0, 0, 7, 6, 7));
        CuboidSet cavityCells = CuboidSet.of(Cuboid.of(1, 1, 1, 6, 5, 6));
        CuboidSet mouthCells = CuboidSet.of(Cuboid.of(3, 1, 0, 4, 3, 1));
        CuboidSet wallCells = bounds.subtract(cavityCells).subtract(mouthCells);
        Bay wall = Bay.of(id("wall"), wallCells, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Bay cavity = Bay.of(id("cavity"), cavityCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay mouth = Bay.of(id("mouth"), mouthCells, Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);
        ContextKey<Long> volume = ContextKeys
                .of(id("cavity_volume"), Long.class, Retention.instance, Merges.sum());
        Blueprint blueprint = Blueprint.builder(id("furnace")).bounds(Cuboid.of(0, 0, 0, 7, 6, 7)).check(
            Check.of(id("wall_bricks"), wall, Focuses.bay(), Expectations.eachCell(Expectations.block(TestWorld.stone)))
        ).check(Check.of(id("cavity_air"), cavity, Focuses.bay(), Expectations.eachCell(Expectations.air())))
                .check(
                    Check.of(
                        id("mouth_door"),
                        mouth,
                        Focuses.door(LatticePoint.of(3, 1, 0)),
                        Expectations.custom(
                            DoorView.class,
                            ExpectationMeta.of(id("door_test"), 10, FindingCode.of(FindingCode.Family.exp, 1)),
                            ctx -> Reply.match()
                        )
                    )
                ).computation(Computations.volume(volume, cavityCells)).build();
        TestWorld.View view = new TestWorld.View();
        for (Map.Entry<LatticePoint, OccupantSnapshot> entry : TestWorld.stoneShell(wallCells).entrySet()) {
            view.put(entry.getValue());
        }
        for (LatticePoint cell : List.of(LatticePoint.of(3, 1, 0), LatticePoint.of(3, 2, 0))) {
            view.put(TestWorld.block(TestWorld.door, cell));
        }
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        assertEquals(100L, admitted.outcome().value(volume).fold(v -> v, () -> -1L));
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(request, admitted, ledger, TestAttempt.of(request))
        );
        assertEquals(294, formed.claims().rows().size());
    }

    @Test
    void localCellsMapThroughTheSiting() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("offset")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air())).build();
        LatticePoint world = LatticePoint.of(10, 64, 10);
        TestWorld.View view = new TestWorld.View().air(world);
        Siting siting = Siting.of(world, Orientation.identity);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                request,
                assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request)),
                ledger,
                TestAttempt.of(request)
            )
        );
        assertEquals(world.key(), formed.claims().rows().get(0).cell());
    }

    @Test
    void bindingFromAnotherSnapshotIsRefused() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("binding")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant()))
                .build();
        TestWorld.View view = new TestWorld.View().put(TestWorld.block(mc("barrel"), LatticePoint.origin));
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        OccupantSnapshot foreign = TestWorld.block(mc("barrel"), LatticePoint.origin);
        OccupantRef foreignRef = new OccupantRef(
            new SnapshotId(TestWorld.world, 2),
            0,
            mc("barrel"),
            OccupantKind.block,
            LatticePoint.origin
        );
        OccupantSnapshot wrong = new OccupantSnapshot(
            foreignRef,
            new OccupantDescriptor(mc("barrel"), OccupantKind.block),
            Cuboid.of(LatticePoint.origin),
            List.of()
        );
        MatchOutcome outcome = new MatchOutcome(
            view,
            Map.of(),
            List.of(new BayBinding(bay, List.of(wrong))),
            ReadScope.none(),
            Spend.zero
        );
        MatchResult.Admitted forged = new MatchResult.Admitted(
            Phase.check,
            instance,
            atOrigin,
            outcome,
            0,
            true,
            FindingBundle.empty()
        );
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MatchEngine.settle(request, forged, ledger, TestAttempt.of(request))
        );
        assertTrue(
            failed.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.bindingInvalid.code()))
        );
    }

    @Test
    void sectionComputationMapsThroughSiting() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class, Retention.instance, Merges.sum());
        Blueprint blueprint = Blueprint.builder(id("section_offset")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air()))
                .computation(Computations.air(air, cells)).build();
        LatticePoint world = LatticePoint.of(10, 64, 10);
        TestWorld.View view = new TestWorld.View().air(world)
                .refuseSection(SectionKey.of(TestWorld.world, 0, LatticePoint.origin, 16));
        Siting siting = Siting.of(world, Orientation.identity);
        ClaimLedger ledger = ClaimLedger.inMemory(TestWorld.world);
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        assertEquals(1L, admitted.outcome().value(air).fold(v -> v, () -> -1L));
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(request, admitted, ledger, TestAttempt.of(request))
        );
        assertEquals(world.key(), formed.claims().rows().get(0).cell());
    }

    @Test
    void aMissingSectionContributionIsAComputationFailure() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> key = ContextKeys.of(id("missing"), Long.class, Retention.instance, Merges.sum());
        SectionComputation<Long> missing = new SectionComputation<>() {
            @Override
            public Outcome<Long> compute(SectionContext ctx) {
                return Outcome.missing();
            }

            @Override
            public NamespacedId id() {
                return ScenarioTest.id("missing");
            }

            @Override
            public ContextKey<Long> output() {
                return key;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of();
            }

            @Override
            public CuboidSet region() {
                return cells;
            }
        };
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Blueprint blueprint = Blueprint.builder(id("missing_section")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(Check.of(id("air"), bay, Focuses.occupant(LatticePoint.origin), Expectations.air()))
                .computation(missing).build();
        TestWorld.View view = new TestWorld.View().air(LatticePoint.origin);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        assertTrue(
            admitted.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.computationFailed.code()))
        );
        assertFalse(
            admitted.findings().findings().stream().anyMatch(finding -> finding.code().equals(Codes.foldRefused.code()))
        );
    }

    @Test
    void aClippedEntityFootprintIsUnreadable() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay bay = Bay.of(id("pen"), cells, Coverage.anySubject, Footprint.bound(), Sharing.shared);
        Blueprint blueprint = Blueprint.builder(id("pen")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(Check.of(id("cow"), bay, Focuses.entity(mc("cow")), Expectations.anyOccupant())).build();
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, 0, mc("cow"), OccupantKind.entity, LatticePoint.origin);
        OccupantSnapshot cow = new OccupantSnapshot(
            ref,
            new OccupantDescriptor(mc("cow"), OccupantKind.entity),
            Cuboid.of(0, 0, 0, 2, 1, 1),
            List.of()
        );
        TestWorld.View view = new TestWorld.View().put(cow).sectionSize(1)
                .refuseSection(SectionKey.of(TestWorld.world, 0, LatticePoint.of(1, 0, 0), 1));
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            instance,
            owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(
            undecided.findings().findings().stream()
                    .anyMatch(finding -> finding.code().equals(Codes.footprintClipped.code()))
        );
    }
}
