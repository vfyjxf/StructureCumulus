package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** The set host over one real level: a two-cell root, an attached wing, the real ledger and contexts. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class StructureSetHostGameTest {

    private static final NamespacedId rootId = id("host_set_root");
    private static final NamespacedId wingBlueprintId = id("host_set_wing");
    private static final NamespacedId wingId = id("host_left_wing");
    private static final NamespacedId setId = id("host_set");
    private static final NamespacedId bodyBayId = id("host_set_body");
    private static final NamespacedId portBayId = id("host_set_port");
    private static final NamespacedId bodyCheckId = id("host_set_body_check");
    private static final NamespacedId portCheckId = id("host_set_port_check");
    private static final NamespacedId wingBayId = id("host_set_wing_bay");
    private static final NamespacedId wingCheckId = id("host_set_wing_check");
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");

    private static final LatticePoint portCell = LatticePoint.of(1, 0, 0);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void checkAllWritesNothingThenSettleAllWritesClaims(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.STONE);
        StructureSet plan = plan(lattice(origin));
        StructureSet.Satellite wing = plan.satellite(wingId);
        if (wing == null) {
            helper.fail("the wing is not placed");
            return;
        }
        SavedClaims ledger = state.claims(level);
        Siting rootSiting = plan.rootSiting();
        Siting wingSiting = wing.siting();
        try {
            release(ledger, state, rootSiting, wingSiting);
            long revision = ledger.revision();
            SnapshotLease lease = capture(state, level, plan);
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            try (lease) {
                StructureSetHost host = new StructureSetHost(state.kernel(level));
                StructureSetResult checked = host
                        .check(plan, lease, state.owner(), Budget.unbounded(), () -> state.instance(level));
                if (!checked.setFindings().isEmpty()) {
                    helper.fail("a clean check reported " + checked.setFindings());
                    return;
                }
                helper.assertValueEqual(
                    List.of(rootId, wingId),
                    List.copyOf(checked.outcomes().keySet()),
                    "the outcomes"
                );
                helper.assertTrue(checked.settled().isEmpty(), "a check settles nothing");
                helper.assertTrue(checked.claims().isEmpty(), "a check writes no claims");
                helper.assertTrue(checked.rolledBack().isEmpty(), "a check rolls nothing back");
                helper.assertTrue(checked.rollbackFailures().isEmpty(), "a check has no rollback failure");
                helper.assertTrue(!checked.formed(), "a check-only result is not formed");
                helper.assertValueEqual(revision, ledger.revision(), "a check does not move the ledger revision");
                helper.assertValueEqual(0, ledger.read(SetCommand.bounds(plan)).rows().size(), "a check writes no row");

                StructureSetResult settled = host
                        .run(plan, lease, state.owner(), Budget.unbounded(), () -> state.instance(level));
                if (!settled.formed()) {
                    helper.fail("the settle did not form: " + settled.setFindings());
                    return;
                }
                helper.assertValueEqual(List.of(rootId, wingId), settled.settled(), "the settle order");
                helper.assertValueEqual(2, settled.claimsOf(rootId).size(), "the root rows");
                helper.assertValueEqual(1, settled.claimsOf(wingId).size(), "the wing rows");
                helper.assertValueEqual(3, ledger.read(SetCommand.bounds(plan)).rows().size(), "the ledger rows");
                helper.assertValueEqual(
                    revision + 2,
                    ledger.revision(),
                    "each settled structure bumps the revision once"
                );
            }
        } finally {
            release(ledger, state, rootSiting, wingSiting);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aFailedMemberStopsSettle(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.DIRT);
        StructureSet plan = plan(lattice(origin));
        StructureSet.Satellite wing = plan.satellite(wingId);
        if (wing == null) {
            helper.fail("the wing is not placed");
            return;
        }
        SavedClaims ledger = state.claims(level);
        Siting rootSiting = plan.rootSiting();
        Siting wingSiting = wing.siting();
        try {
            release(ledger, state, rootSiting, wingSiting);
            long revision = ledger.revision();
            SnapshotLease lease = capture(state, level, plan);
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            try (lease) {
                StructureSetHost host = new StructureSetHost(state.kernel(level));
                StructureSetResult result = host
                        .run(plan, lease, state.owner(), Budget.unbounded(), () -> state.instance(level));
                helper.assertTrue(!result.formed(), "a failed member means no formed set");
                helper.assertValueEqual(
                    List.of(rootId),
                    List.copyOf(result.outcomes().keySet()),
                    "the admitted outcomes"
                );
                helper.assertTrue(result.settled().isEmpty(), "a failed check settles nothing");
                helper.assertTrue(result.claims().isEmpty(), "a failed check writes no claim");
                helper.assertTrue(result.rolledBack().isEmpty(), "nothing was settled, so nothing rolls back");
                helper.assertTrue(result.firstFailure() != null, "the failure is reported");
                helper.assertValueEqual(revision, ledger.revision(), "the failed set does not move the revision");
                helper.assertValueEqual(
                    0,
                    ledger.read(SetCommand.bounds(plan)).rows().size(),
                    "the failed set writes no row"
                );
            }
        } finally {
            release(ledger, state, rootSiting, wingSiting);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.STONE);
        StructureSet plan = plan(lattice(origin));
        StructureSet.Satellite wing = plan.satellite(wingId);
        if (wing == null) {
            helper.fail("the wing is not placed");
            return;
        }
        Siting rootSiting = plan.rootSiting();
        Cuboid onRoot = Objects.requireNonNull(rootSiting.toWorld(wing.blueprint().bounds()), "satellite bounds");
        StructureSet handBuilt = new StructureSet(
            plan.compiled(),
            rootSiting,
            plan.rootBounds(),
            List.of(new StructureSet.Satellite(wingId, wing.compiled(), rootSiting, onRoot, onRoot.asSet()))
        );
        SavedClaims ledger = state.claims(level);
        long revision = ledger.revision();
        AtomicInteger instances = new AtomicInteger();
        SnapshotLease lease = capture(state, level, plan);
        if (lease == null) {
            helper.fail("the set capture did not succeed");
            return;
        }
        try (lease) {
            StructureSetResult result = new StructureSetHost(state.kernel(level))
                    .check(handBuilt, lease, state.owner(), Budget.unbounded(), () -> {
                        instances.incrementAndGet();
                        return state.instance(level);
                    });
            helper.assertTrue(
                result.setFindings().stream()
                        .anyMatch(finding -> finding.code().equals(Codes.rootPlacementCollision.code())),
                "the same-siting code: " + result.setFindings()
            );
            helper.assertTrue(result.outcomes().isEmpty(), "the set is rejected before any check");
            helper.assertValueEqual(0, instances.get(), "no instance is allocated");
            helper.assertTrue(result.settled().isEmpty(), "the rejected set settles nothing");
            helper.assertTrue(result.rolledBack().isEmpty(), "the rejected set rolls nothing back");
            helper.assertTrue(result.claims().isEmpty(), "the rejected set reports no claim");
            helper.assertValueEqual(revision, ledger.revision(), "the rejected set moves no revision");
            helper.assertValueEqual(
                0,
                ledger.read(SetCommand.bounds(plan)).rows().size(),
                "the rejected set writes no row"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aOneCellSetBudgetLeavesTheRootCheckUndecided(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.STONE);
        StructureSet plan = plan(lattice(origin));
        StructureSet.Satellite wing = plan.satellite(wingId);
        if (wing == null) {
            helper.fail("the wing is not placed");
            return;
        }
        SavedClaims ledger = state.claims(level);
        Siting rootSiting = plan.rootSiting();
        Siting wingSiting = wing.siting();
        try {
            release(ledger, state, rootSiting, wingSiting);
            long revision = ledger.revision();
            SnapshotLease lease = capture(state, level, plan);
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            try (lease) {
                StructureSetResult result = new StructureSetHost(state.kernel(level))
                        .run(plan, lease, state.owner(), Budget.of(1, Integer.MAX_VALUE), () -> state.instance(level));
                helper.assertTrue(
                    result.setFindings().stream()
                            .anyMatch(finding -> finding.code().equals(Codes.budgetExhausted.code())),
                    "the shared budget is exhausted: " + result.setFindings()
                );
                helper.assertTrue(result.outcomes().isEmpty(), "the root check is not admitted");
                helper.assertTrue(result.settled().isEmpty(), "an undecided run settles nothing");
                helper.assertTrue(result.rolledBack().isEmpty(), "an undecided run rolls nothing back");
                helper.assertTrue(result.claims().isEmpty(), "an undecided run writes no claim");
                helper.assertValueEqual(revision, ledger.revision(), "the undecided set moves no revision");
                helper.assertValueEqual(
                    0,
                    ledger.read(SetCommand.bounds(plan)).rows().size(),
                    "the undecided set writes no row"
                );
            }
        } finally {
            release(ledger, state, rootSiting, wingSiting);
        }
        helper.succeed();
    }

    private static void release(SavedClaims ledger, ServerState state, Siting root, Siting wing) {
        ledger.release(state.owner(), root);
        ledger.release(state.owner(), wing);
    }

    private static @Nullable SnapshotLease capture(ServerState state, ServerLevel level, StructureSet plan) {
        CaptureResult result = state.capture(level).capture(
            new CaptureRequest(SetCommand.bounds(plan), SetCommand.policy(plan)),
            plan.rootSiting().placement().orientation()
        );
        return result instanceof CaptureResult.Captured captured ? captured.lease() : null;
    }

    private static LatticePoint lattice(BlockPos origin) {
        return LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
    }

    private static StructureSet plan(LatticePoint origin) {
        SatelliteSpec spec = new SatelliteSpec(
            wingId,
            wingBlueprintId,
            LatticePoint.of(2, 0, 0),
            Orientation.identity,
            true,
            Value.object(),
            null,
            List.of(LinkSpec.face(portCell, LatticePoint.origin)),
            null
        );
        CompiledStructureSet compiled = new CompiledStructureSet(
            setId,
            rootBlueprint(),
            List.of(new CompiledSatellite(spec, wingBlueprint()))
        );
        StructureSet.Placement placement = StructureSet.place(compiled, Siting.of(origin, Orientation.identity));
        return Objects.requireNonNull(placement.set(), () -> "placement failed: " + placement.findings());
    }

    private static Blueprint rootBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay body = Bay.of(
            bodyBayId,
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay port = Bay.of(
            portBayId,
            CuboidSet.of(Cuboid.of(portCell)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(rootId).bounds(bounds)
                .checks(
                    List.of(
                        Check.of(bodyCheckId, body, Focuses.bay(), Expectations.eachCell(Expectations.block(stone))),
                        Check.of(portCheckId, port, Focuses.bay(), Expectations.eachCell(Expectations.block(stone)))
                    )
                ).build();
    }

    private static Blueprint wingBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay cell = Bay.of(wingBayId, CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(wingBlueprintId).bounds(bounds)
                .check(Check.of(wingCheckId, cell, Focuses.bay(), Expectations.eachCell(Expectations.block(stone))))
                .build();
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

}
