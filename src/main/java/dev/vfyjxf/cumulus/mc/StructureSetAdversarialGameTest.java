package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
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
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Real-level adversarial checks for the structure-set manager: drafts, instances, re-settle and budget. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class StructureSetAdversarialGameTest {

    private static final NamespacedId rootBlueprintId = id("adversarial_root");
    private static final NamespacedId satelliteBlueprintId = id("adversarial_satellite");
    private static final NamespacedId setId = id("adversarial_set");
    private static final NamespacedId rootBayId = id("adversarial_root_bay");
    private static final NamespacedId rootCheckId = id("adversarial_root_check");
    private static final NamespacedId satelliteBayId = id("adversarial_satellite_bay");
    private static final NamespacedId satelliteCheckId = id("adversarial_satellite_check");
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void checkOnlyLeavesNoManagerInstanceOrDraft(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StructureSet plan = plan(origin, 2);
        placeBlocks(helper, plan);
        StructureManager structures = state.structures(level);
        SavedClaims ledger = state.claims(level);
        Cuboid bounds = SetCommand.bounds(plan);
        List<InstanceId> drafts = new ArrayList<>();
        cleanup(structures, ledger, state, plan);
        try (SnapshotLease lease = capture(state, level, plan, bounds)) {
            StructureSetResult result = new StructureSetHost(state.kernel(level))
                    .check(plan, lease, state.owner(), Budget.unbounded(), spec -> {
                        StructureAttempt attempt = structures.begin(state.world(level), spec);
                        drafts.add(attempt.id());
                        return attempt;
                    });
            helper.assertTrue(result.setFindings().isEmpty(), "check findings: " + result.setFindings());
            helper.assertTrue(result.settled().isEmpty(), "check-only settles nothing");
            helper.assertTrue(result.claims().isEmpty(), "check-only claims nothing");
            helper.assertValueEqual(0, ledger.read(bounds).rows().size(), "check-only row count");
            for (StructureSet.Satellite satellite : plan.satellites()) {
                helper.assertTrue(structures.find(state.owner(), satellite.siting()) == null, "no satellite instance");
            }
            helper.assertTrue(structures.find(state.owner(), plan.rootSiting()) == null, "no root instance");
            for (InstanceId draft : drafts) {
                helper.assertTrue(structures.remove(draft) == null, "draft " + draft + " was not aborted");
            }
        } finally {
            cleanup(structures, state.claims(level), state, plan);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settleThenReSettleReplacesInstancesAndClaims(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StructureSet plan = plan(origin, 2);
        placeBlocks(helper, plan);
        StructureManager structures = state.structures(level);
        SavedClaims ledger = state.claims(level);
        Cuboid bounds = SetCommand.bounds(plan);
        cleanup(structures, ledger, state, plan);
        try (SnapshotLease lease = capture(state, level, plan, bounds)) {
            StructureSetHost host = new StructureSetHost(state.kernel(level));
            List<InstanceId> firstIds = new ArrayList<>();
            StructureSetResult first = host.run(plan, lease, state.owner(), Budget.unbounded(), spec -> {
                StructureAttempt attempt = structures.begin(state.world(level), spec);
                firstIds.add(attempt.id());
                return attempt;
            });
            helper.assertTrue(first.formed(), "first set: " + first.setFindings());
            helper.assertValueEqual(3, ledger.read(bounds).rows().size(), "first row count");
            long revision = ledger.revision();

            List<InstanceId> secondIds = new ArrayList<>();
            StructureSetResult second = host.run(plan, lease, state.owner(), Budget.unbounded(), spec -> {
                StructureAttempt attempt = structures.begin(state.world(level), spec);
                secondIds.add(attempt.id());
                return attempt;
            });
            helper.assertTrue(second.formed(), "second set: " + second.setFindings());
            helper.assertValueEqual(3, ledger.read(bounds).rows().size(), "re-settle replaces rows");
            helper.assertTrue(ledger.revision() > revision, "the revision grows");
            for (InstanceId old : firstIds) {
                helper.assertTrue(structures.find(old) == null, "the old instance " + old + " was replaced");
            }
            helper.assertValueEqual(3, secondIds.size(), "one new instance per member");
            for (InstanceId id : secondIds) {
                helper.assertTrue(structures.find(id) != null, "the replacement instance " + id + " is formed");
            }
        } finally {
            cleanup(structures, state.claims(level), state, plan);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void oneCellSharedBudgetStopsBeforeAnyInstance(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StructureSet plan = plan(origin, 1);
        placeBlocks(helper, plan);
        StructureManager structures = state.structures(level);
        SavedClaims ledger = state.claims(level);
        Cuboid bounds = SetCommand.bounds(plan);
        List<InstanceId> drafts = new ArrayList<>();
        cleanup(structures, ledger, state, plan);
        try (SnapshotLease lease = capture(state, level, plan, bounds)) {
            StructureSetResult result = new StructureSetHost(state.kernel(level))
                    .run(plan, lease, state.owner(), Budget.of(1, 0), spec -> {
                        StructureAttempt attempt = structures.begin(state.world(level), spec);
                        drafts.add(attempt.id());
                        return attempt;
                    });
            helper.assertFalse(result.formed(), "one cell cannot cover presence and the root");
            helper.assertValueEqual(0, ledger.read(bounds).rows().size(), "no claim row");
            helper.assertTrue(structures.find(state.owner(), plan.rootSiting()) == null, "no root instance");
            helper.assertTrue(
                structures.find(state.owner(), plan.satellites().get(0).siting()) == null,
                "no satellite instance"
            );
            for (InstanceId draft : drafts) {
                helper.assertTrue(structures.remove(draft) == null, "the failed draft stays aborted");
            }
        } finally {
            cleanup(structures, state.claims(level), state, plan);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void handBuiltDuplicatePlacementIsRejectedBeforeAnyDraft(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StructureSet plan = duplicatePlan(origin);
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 0), Blocks.STONE);
        StructureManager structures = state.structures(level);
        SavedClaims ledger = state.claims(level);
        Cuboid bounds = SetCommand.bounds(plan);
        cleanup(structures, ledger, state, plan);
        List<InstanceId> drafts = new ArrayList<>();
        try (SnapshotLease lease = capture(state, level, plan, bounds)) {
            StructureSetResult result = new StructureSetHost(state.kernel(level), structures)
                    .check(plan, lease, state.owner(), Budget.unbounded(), structures, spec -> {
                        StructureAttempt attempt = structures.begin(state.world(level), spec);
                        drafts.add(attempt.id());
                        return attempt;
                    });
            helper.assertFalse(result.formed(), "the duplicate placement is rejected");
            helper.assertTrue(
                result.setFindings().stream()
                        .anyMatch(finding -> finding.code().equals(Codes.duplicatePlacement.code())),
                "the duplicate placement code: " + result.setFindings()
            );
            helper.assertValueEqual(0, drafts.size(), "no draft is begun");
            helper.assertValueEqual(0, ledger.read(bounds).rows().size(), "no claim row");
            helper.assertTrue(structures.find(state.owner(), plan.rootSiting()) == null, "no root instance");
        } finally {
            cleanup(structures, state.claims(level), state, plan);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void sixSatellitesFormWithOneInstanceAndClaimPerMember(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StructureSet plan = plan(origin, 6);
        placeBlocks(helper, plan);
        StructureManager structures = state.structures(level);
        SavedClaims ledger = state.claims(level);
        Cuboid bounds = SetCommand.bounds(plan);
        cleanup(structures, ledger, state, plan);
        try (SnapshotLease lease = capture(state, level, plan, bounds)) {
            StructureSetResult result = new StructureSetHost(state.kernel(level)).run(
                plan,
                lease,
                state.owner(),
                Budget.unbounded(),
                spec -> structures.begin(state.world(level), spec)
            );
            helper.assertTrue(result.formed(), "large set: " + result.setFindings());
            helper.assertValueEqual(7, result.settled().size(), "root plus six satellites");
            helper.assertValueEqual(7, ledger.read(bounds).rows().size(), "one claim row per member");
            helper.assertValueEqual(plan.root().id(), result.settled().get(0), "the root settles first");
            helper.assertTrue(structures.find(state.owner(), plan.rootSiting()) != null, "root instance");
            for (int index = 0; index < 6; index++) {
                StructureSet.Satellite satellite = plan.satellites().get(index);
                helper.assertTrue(
                    structures.find(state.owner(), satellite.siting()) != null,
                    "instance for satellite " + satellite.id()
                );
            }
        } finally {
            cleanup(structures, state.claims(level), state, plan);
        }
        helper.succeed();
    }

    private static StructureSet duplicatePlan(BlockPos origin) {
        SatelliteSpec first = new SatelliteSpec(
            id("adversarial_duplicate_a"),
            satelliteBlueprintId,
            LatticePoint.of(2, 0, 0),
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(LinkSpec.none()),
            null
        );
        SatelliteSpec second = new SatelliteSpec(
            id("adversarial_duplicate_b"),
            satelliteBlueprintId,
            LatticePoint.of(2, 0, 0),
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(LinkSpec.none()),
            null
        );
        CompiledStructureSet compiled = new CompiledStructureSet(
            setId,
            rootBlueprint(),
            List.of(
                new CompiledSatellite(first, satelliteBlueprint()),
                new CompiledSatellite(second, satelliteBlueprint())
            )
        );
        StructureSet.Placement placement = StructureSet
                .place(compiled, Siting.of(lattice(origin), Orientation.identity));
        if (!placement.succeeded()) {
            throw new GameTestAssertException("the duplicate plan does not place: " + placement.findings());
        }
        return Objects.requireNonNull(placement.set(), "duplicate plan");
    }

    private static StructureSet plan(BlockPos origin, int satelliteCount) {
        List<CompiledSatellite> satellites = new ArrayList<>();
        for (int index = 0; index < satelliteCount; index++) {
            SatelliteSpec spec = new SatelliteSpec(
                id("adversarial_satellite_" + index),
                satelliteBlueprintId,
                LatticePoint.of(2 + index * 2, 0, 0),
                Orientation.identity,
                index % 2 == 0,
                Value.object(),
                null,
                List.of(LinkSpec.none()),
                null
            );
            satellites.add(new CompiledSatellite(spec, satelliteBlueprint()));
        }
        CompiledStructureSet compiled = new CompiledStructureSet(setId, rootBlueprint(), List.copyOf(satellites));
        StructureSet.Placement placement = StructureSet
                .place(compiled, Siting.of(lattice(origin), Orientation.identity));
        if (!placement.succeeded()) {
            throw new GameTestAssertException("the adversarial plan does not place: " + placement.findings());
        }
        return Objects.requireNonNull(placement.set(), "placed set");
    }

    private static void placeBlocks(GameTestHelper helper, StructureSet plan) {
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        for (StructureSet.Satellite satellite : plan.satellites()) {
            LatticePoint world = satellite.siting().origin();
            LatticePoint root = plan.rootSiting().origin();
            helper.setBlock(
                new BlockPos(world.x() - root.x(), world.y() - root.y(), world.z() - root.z()),
                Blocks.STONE
            );
        }
    }

    private static SnapshotLease capture(ServerState state, ServerLevel level, StructureSet plan, Cuboid bounds) {
        CaptureResult result = state.capture(level).capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()), null);
        if (!(result instanceof CaptureResult.Captured captured)) {
            throw new GameTestAssertException("the set capture failed: " + result);
        }
        return captured.lease();
    }

    private static void cleanup(StructureManager structures, SavedClaims ledger, ServerState state, StructureSet plan) {
        for (StructureSet.Satellite satellite : plan.satellites()) {
            release(structures, ledger, state, satellite.siting());
        }
        release(structures, ledger, state, plan.rootSiting());
    }

    private static void release(StructureManager structures, SavedClaims ledger, ServerState state, Siting siting) {
        StructureInstance instance = structures.find(state.owner(), siting);
        if (instance != null) {
            structures.onEvent(new StructureEvent.Broken(instance.id()));
        }
        ledger.release(state.owner(), siting);
    }

    private static Blueprint rootBlueprint() {
        return blueprint(rootBlueprintId, rootBayId, rootCheckId);
    }

    private static Blueprint satelliteBlueprint() {
        return blueprint(satelliteBlueprintId, satelliteBayId, satelliteCheckId);
    }

    private static Blueprint blueprint(NamespacedId blueprintId, NamespacedId bayId, NamespacedId checkId) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(bayId, CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(bounds)
                .check(Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.block(stone)))).build();
    }

    private static LatticePoint lattice(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }
}
