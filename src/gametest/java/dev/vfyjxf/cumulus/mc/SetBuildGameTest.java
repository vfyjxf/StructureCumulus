package dev.vfyjxf.cumulus.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
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
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.CreativeItemSource;
import dev.vfyjxf.cumulus.mc.build.ItemHandlerSource;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.build.SetBuildHost;
import dev.vfyjxf.cumulus.mc.build.SetBuildRequest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The set build over one real level: two one-cell members, the real ledger, sources and transaction. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class SetBuildGameTest {

    private static final NamespacedId rootBlueprintId = id("set_build_root");
    private static final NamespacedId wingBlueprintId = id("set_build_wing");
    private static final NamespacedId wingId = id("set_build_wing_sat");
    private static final NamespacedId setId = id("set_build");
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void everyAbsentMemberIsBuilt(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = plan(origin);
        try (SnapshotLease lease = capture(state, level, plan)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level)
                    .run(request(plan, new CreativeItemSource(), helper, false, false), lease, Budget.unbounded());
            if (!result.built()) {
                helper.fail("the set build did not complete: " + findings(result));
                return;
            }
            helper.assertValueEqual(
                List.of(rootBlueprintId, wingId),
                List.copyOf(result.builds().keySet()),
                "the build order"
            );
            assertEquals(helper, result, rootBlueprintId, ApplyReport.Status.committed, "the root member");
            assertEquals(helper, result, wingId, ApplyReport.Status.committed, "the wing member");
            helper.assertValueEqual(2, count(result.itemsConsumed()), "two cells paid for");
            helper.assertTrue(blockAt(helper, origin).is(Blocks.STONE), "the root cell holds stone");
            helper.assertTrue(blockAt(helper, wing(origin)).is(Blocks.STONE), "the wing cell holds stone");
            helper.assertValueEqual(
                0,
                state.claims(level).read(SetCommand.bounds(plan)).rows().size(),
                "a build takes no claim"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aPresentFormedMemberIsSkippedAndTheAbsentOneBuilt(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        level.setBlockAndUpdate(pos(origin), Blocks.STONE.defaultBlockState());
        StructureSet plan = plan(origin);
        try (SnapshotLease lease = capture(state, level, plan)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level)
                    .run(request(plan, new CreativeItemSource(), helper, false, false), lease, Budget.unbounded());
            helper.assertTrue(result.built(), "the set build completed: " + findings(result));
            ApplyReport rootReport = result.buildOf(rootBlueprintId);
            if (rootReport == null) {
                helper.fail("the root member has no report");
                return;
            }
            helper.assertTrue(rootReport.steps().isEmpty(), "a present and formed member is skipped");
            helper.assertValueEqual(0, rootReport.itemsConsumed().size(), "a skipped member pays nothing");
            ApplyReport wingReport = result.buildOf(wingId);
            if (wingReport == null) {
                helper.fail("the wing member has no report");
                return;
            }
            helper.assertValueEqual(1, wingReport.steps().size(), "the absent member's plan");
            helper.assertValueEqual(1, count(result.itemsConsumed()), "only the absent member paid");
            helper.assertTrue(blockAt(helper, origin).is(Blocks.STONE), "the present member was not rewritten");
            helper.assertTrue(blockAt(helper, wing(origin)).is(Blocks.STONE), "the absent member was built");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aMemberShortageTearsTheWrittenMembersDown(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = plan(origin);
        ItemHandlerSource source = chest(helper, 1);
        if (source == null) {
            return;
        }
        try (SnapshotLease lease = capture(state, level, plan)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level)
                    .run(request(plan, source, helper, false, false), lease, Budget.unbounded());
            helper.assertTrue(!result.built(), "a member shortage fails the set build");
            assertEquals(helper, result, rootBlueprintId, ApplyReport.Status.rolledBack, "the written root member");
            assertEquals(helper, result, wingId, ApplyReport.Status.refused, "the short wing member");
            helper.assertTrue(
                hasCode(result.buildOf(wingId), BuildCodes.itemShortage),
                "the shortage is reported against the wing"
            );
            helper.assertTrue(
                result.setFindings().stream().anyMatch(finding -> finding.code().equals(BuildCodes.itemShortage)),
                "the set carries the member's shortage"
            );
            helper.assertTrue(
                result.rolledBack().contains(rootBlueprintId),
                "the torn-down member is named: " + result.rolledBack()
            );
            helper.assertTrue(blockAt(helper, origin).isAir(), "the root's write was torn down");
            helper.assertValueEqual(
                1,
                source.available(new ItemStack(Items.STONE)),
                "the torn-down member's item went back to the shared source"
            );
            helper.assertValueEqual(
                0,
                state.claims(level).read(SetCommand.bounds(plan)).rows().size(),
                "a failed build leaves no claim row"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aDrySetBuildWritesNothing(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = plan(origin);
        try (SnapshotLease lease = capture(state, level, plan)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level)
                    .run(request(plan, new CreativeItemSource(), helper, true, false), lease, Budget.unbounded());
            helper.assertTrue(result.built(), "a dry validation is clean: " + findings(result));
            for (NamespacedId member : List.of(rootBlueprintId, wingId)) {
                ApplyReport report = result.buildOf(member);
                if (report == null) {
                    helper.fail("member " + member.text() + " has no report");
                    return;
                }
                helper.assertTrue(report.steps().isEmpty(), "a dry run applies nothing");
            }
            helper.assertTrue(result.itemsConsumed().isEmpty(), "a dry run consumes nothing");
            helper.assertTrue(blockAt(helper, origin).isAir(), "a dry run writes no root cell");
            helper.assertTrue(blockAt(helper, wing(origin)).isAir(), "a dry run writes no wing cell");
            helper.assertValueEqual(
                0,
                state.claims(level).read(SetCommand.bounds(plan)).rows().size(),
                "a dry run writes no claim row"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theSharedSourceBillsEveryMember(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = plan(origin);
        ItemHandlerSource source = chest(helper, 2);
        if (source == null) {
            return;
        }
        try (SnapshotLease lease = capture(state, level, plan)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level)
                    .run(request(plan, source, helper, false, false), lease, Budget.unbounded());
            helper.assertTrue(result.built(), "the shared source covers both members: " + findings(result));
            helper.assertValueEqual(2, count(result.itemsConsumed()), "both members were billed");
            helper.assertValueEqual(
                0,
                source.available(new ItemStack(Items.STONE)),
                "the shared source paid for both members"
            );
            helper.assertTrue(blockAt(helper, origin).is(Blocks.STONE), "the root cell holds stone");
            helper.assertTrue(blockAt(helper, wing(origin)).is(Blocks.STONE), "the wing cell holds stone");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settleChainsTheSetSettlePath(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = plan(origin);
        ClaimLedger ledger = state.claims(level);
        Siting rootSiting = plan.rootSiting();
        StructureSet.Satellite wing = plan.satellite(wingId);
        if (wing == null) {
            helper.fail("the wing is not placed");
            return;
        }
        try {
            release(ledger, state, level, rootSiting);
            release(ledger, state, level, wing.siting());
            try (SnapshotLease lease = capture(state, level, plan)) {
                if (lease == null) {
                    helper.fail("the set capture did not succeed");
                    return;
                }
                StructureSetResult result = host(state, level)
                        .run(request(plan, new CreativeItemSource(), helper, false, true), lease, Budget.unbounded());
                helper.assertTrue(result.formed(), "the settle chain formed the set: " + findings(result));
                helper.assertValueEqual(List.of(rootBlueprintId, wingId), result.settled(), "the settle order");
                helper.assertValueEqual(1, result.claimsOf(rootBlueprintId).size(), "the root rows");
                helper.assertValueEqual(1, result.claimsOf(wingId).size(), "the wing rows");
                helper.assertValueEqual(
                    2,
                    ledger.read(SetCommand.bounds(plan)).rows().size(),
                    "only the settle pass wrote rows"
                );
            }
        } finally {
            release(ledger, state, level, rootSiting);
            release(ledger, state, level, wing.siting());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aFailedSettleTearsTheWorldWritesDown(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = plan(origin);
        try (SnapshotLease lease = capture(state, level, plan)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level).run(
                request(plan, new CreativeItemSource(), helper, false, true),
                lease,
                Budget.of(1, Integer.MAX_VALUE)
            );
            helper.assertTrue(!result.formed(), "the settle pass could not run on one cell of budget");
            helper.assertTrue(
                result.setFindings().stream()
                        .anyMatch(finding -> finding.code().equals(Codes.presenceUnreadable.code())),
                "the settle pass's presence read is the failure: " + findings(result)
            );
            helper.assertTrue(blockAt(helper, origin).isAir(), "the build's root write was torn down");
            helper.assertTrue(blockAt(helper, wing(origin)).isAir(), "the build's wing write was torn down");
            helper.assertValueEqual(
                0,
                state.claims(level).read(SetCommand.bounds(plan)).rows().size(),
                "a failed settle wrote no row"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aMemberThatStillNeedsARepairKeepsItsWrite(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = twoPartPlan(origin);
        try (SnapshotLease lease = capture(state, level, plan)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level)
                    .run(request(plan, new CreativeItemSource(), helper, false, false), lease, Budget.unbounded());
            helper.assertTrue(!result.built(), "a member with a second faulty part is not built in one run");
            assertEquals(helper, result, rootBlueprintId, ApplyReport.Status.committed, "the repaired root member");
            ApplyReport rootReport = result.buildOf(rootBlueprintId);
            helper.assertTrue(
                hasCode(rootReport, BuildCodes.recheckNotFormed),
                "the member's re-check is reported: " + (rootReport == null ? "no report" : codes(rootReport))
            );
            helper.assertTrue(result.rolledBack().isEmpty(), "a re-check failure tears nothing down");
            assertEquals(helper, result, wingId, ApplyReport.Status.committed, "the wing after the member warning");
            helper.assertTrue(blockAt(helper, origin).is(Blocks.STONE), "the repair stayed in the world");
            helper.assertTrue(
                blockAt(helper, LatticePoint.of(origin.x() + 1, origin.y(), origin.z())).isAir(),
                "the second fault waits for the next run"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aHandBuiltSittingCollisionIsRejectedBeforeAnyWrite(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint origin = origin(helper);
        StructureSet plan = plan(origin);
        StructureSet.Satellite wing = plan.satellite(wingId);
        if (wing == null) {
            helper.fail("the wing is not placed");
            return;
        }
        Cuboid onRoot = Objects.requireNonNull(plan.rootSiting().toWorld(wing.blueprint().bounds()), "bounds");
        StructureSet handBuilt = new StructureSet(
            plan.compiled(),
            plan.rootSiting(),
            plan.rootBounds(),
            List.of(new StructureSet.Satellite(wingId, wing.compiled(), plan.rootSiting(), onRoot, onRoot.asSet()))
        );
        try (SnapshotLease lease = capture(state, level, handBuilt)) {
            if (lease == null) {
                helper.fail("the set capture did not succeed");
                return;
            }
            StructureSetResult result = host(state, level)
                    .run(request(handBuilt, new CreativeItemSource(), helper, false, false), lease, Budget.unbounded());
            helper.assertTrue(
                result.setFindings().stream()
                        .anyMatch(finding -> finding.code().equals(Codes.rootPlacementCollision.code())),
                "the same-siting refusal: " + findings(result)
            );
            helper.assertTrue(result.builds().isEmpty(), "no member is built");
            helper.assertTrue(blockAt(helper, origin).isAir(), "the rejected set writes nothing");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theCommandBuildsSettlesAndDryRunsTheDatapackSet(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        LatticePoint point = origin(helper);
        BlockPos origin = pos(point);
        StructureSet plan = buildSetPlan(state, point);
        if (plan == null) {
            helper.fail("the build_set document did not compile and place");
            return;
        }
        FakePlayer player = player(helper, playerId("set_command"));
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, recording, player, origin);
        String arguments = origin.getX() + " " + origin.getY() + " " + origin.getZ();
        String set = id("build_set").text();
        ClaimLedger ledger = state.claims(level);
        StructureSet.Satellite wing = plan.satellite(id("left_wing"));
        if (wing == null) {
            helper.fail("the wing is not placed");
            return;
        }
        try {
            release(ledger, state, level, plan.rootSiting());
            release(ledger, state, level, wing.siting());
            helper.assertValueEqual(
                1,
                execute(helper, source, "cumulus set build " + set + " " + arguments + " with creative --dry"),
                "the dry build validates " + recording.text()
            );
            helper.assertTrue(blockAt(helper, point).isAir(), "the dry command writes nothing");
            helper.assertValueEqual(0, ledger.read(SetCommand.bounds(plan)).rows().size(), "a dry build writes no row");

            helper.assertValueEqual(
                1,
                execute(helper, source, "cumulus set build " + set + " " + arguments + " with creative"),
                "the set build commits " + recording.text()
            );
            helper.assertTrue(blockAt(helper, point).is(Blocks.STONE), "the root cell holds stone");
            helper.assertTrue(
                blockAt(helper, LatticePoint.of(point.x() + 1, point.y(), point.z())).is(Blocks.STONE),
                "the wing cell holds stone"
            );
            helper.assertValueEqual(
                0,
                ledger.read(SetCommand.bounds(plan)).rows().size(),
                "a plain set build takes no claim"
            );

            helper.assertValueEqual(
                1,
                execute(helper, source, "cumulus set build " + set + " " + arguments + " with creative --settle"),
                "the set build settles " + recording.text()
            );
            helper.assertValueEqual(
                2,
                ledger.read(SetCommand.bounds(plan)).rows().size(),
                "the settle pass wrote the claim rows"
            );
        } finally {
            release(ledger, state, level, plan.rootSiting());
            release(ledger, state, level, wing.siting());
        }
        helper.succeed();
    }

    private static @Nullable StructureSet buildSetPlan(ServerState state, LatticePoint origin) {
        var document = state.structureSet(id("build_set"));
        if (document == null) {
            return null;
        }
        var compiled = state.compileSet(document).set();
        if (compiled == null) {
            return null;
        }
        return StructureSet.place(compiled, Siting.of(origin, Orientation.identity)).set();
    }

    private static SetBuildHost host(ServerState state, ServerLevel level) {
        return new SetBuildHost(state.capture(level), state.kernel(level), state.owner());
    }

    private static SetBuildRequest request(
        StructureSet plan,
        ItemSource source,
        GameTestHelper helper,
        boolean dry,
        boolean settle
    ) {
        return new SetBuildRequest(
            plan,
            source,
            BuildPolicy.defaults(),
            player(helper, playerId("set_build")),
            dry,
            settle
        );
    }

    private static @Nullable ServerState state(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
        }
        return state;
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
            List.of(),
            null
        );
        CompiledStructureSet compiled = new CompiledStructureSet(
            setId,
            memberBlueprint(rootBlueprintId),
            List.of(new CompiledSatellite(spec, memberBlueprint(wingBlueprintId)))
        );
        StructureSet.Placement placement = StructureSet.place(compiled, Siting.of(origin, Orientation.identity));
        return Objects.requireNonNull(placement.set(), () -> "placement failed: " + placement.findings());
    }

    /** A root of two one-cell checks and the usual one-cell wing three cells to the east. */
    private static StructureSet twoPartPlan(LatticePoint origin) {
        SatelliteSpec spec = new SatelliteSpec(
            wingId,
            wingBlueprintId,
            LatticePoint.of(3, 0, 0),
            Orientation.identity,
            true,
            Value.object(),
            null,
            List.of(),
            null
        );
        CompiledStructureSet compiled = new CompiledStructureSet(
            setId,
            twoPartBlueprint(),
            List.of(new CompiledSatellite(spec, memberBlueprint(wingBlueprintId)))
        );
        StructureSet.Placement placement = StructureSet.place(compiled, Siting.of(origin, Orientation.identity));
        return Objects.requireNonNull(placement.set(), () -> "placement failed: " + placement.findings());
    }

    private static Blueprint twoPartBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay first = Bay.of(
            id("first"),
            Cuboid.of(LatticePoint.origin).asSet(),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay second = Bay.of(
            id("second"),
            Cuboid.of(LatticePoint.of(1, 0, 0)).asSet(),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(rootBlueprintId).bounds(bounds).checks(
            List.of(
                Check.of(id("first_check"), first, Focuses.bay(), Expectations.eachCell(Expectations.block(stone))),
                Check.of(id("second_check"), second, Focuses.bay(), Expectations.eachCell(Expectations.block(stone)))
            )
        ).build();
    }

    private static Blueprint memberBlueprint(NamespacedId id) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay cell = Bay.of(id("cell"), bounds.asSet(), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(id).bounds(bounds)
                .check(
                    Check.of(id("cell_check"), cell, Focuses.bay(), Expectations.eachCell(Expectations.block(stone)))
                ).build();
    }

    private static @Nullable ItemHandlerSource chest(GameTestHelper helper, int stones) {
        ServerLevel level = helper.getLevel();
        BlockPos chestPos = helper.absolutePos(new BlockPos(5, 1, 5));
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            helper.fail("the chest has no block entity");
            return null;
        }
        chest.setItem(0, new ItemStack(Items.STONE, stones));
        ItemHandlerSource source = ItemHandlerSource.at(level, chestPos, null);
        if (source == null) {
            helper.fail("the chest exposes no item handler");
        }
        return source;
    }

    private static @Nullable SnapshotLease capture(ServerState state, ServerLevel level, StructureSet plan) {
        CaptureResult result = state.capture(level)
                .capture(new CaptureRequest(SetCommand.bounds(plan), SetCommand.policy(plan)), null);
        return result instanceof CaptureResult.Captured captured ? captured.lease() : null;
    }

    private static void release(ClaimLedger ledger, ServerState state, ServerLevel level, Siting siting) {
        StructureInstance instance = state.structures(level).find(state.world(level), state.owner(), siting);
        if (instance != null) {
            ledger.release(instance.id());
        }
    }

    private static void assertEquals(
        GameTestHelper helper,
        StructureSetResult result,
        NamespacedId member,
        ApplyReport.Status expected,
        String message
    ) {
        ApplyReport report = result.buildOf(member);
        if (report == null) {
            helper.fail(message + ": no report");
            return;
        }
        if (report.status() != expected) {
            helper.fail(message + ": expected " + expected + ", was " + report.status() + " " + codes(report));
        }
    }

    private static boolean hasCode(@Nullable ApplyReport report, FindingCode code) {
        if (report == null) {
            return false;
        }
        return report.findings().findings().stream().anyMatch(finding -> finding.code().equals(code));
    }

    private static int count(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) {
            total += stack.getCount();
        }
        return total;
    }

    private static String codes(ApplyReport report) {
        List<String> text = new ArrayList<>();
        for (Finding finding : report.findings().findings()) {
            text.add(finding.code().text() + " '" + finding.message() + "'");
        }
        return text.toString();
    }

    private static String findings(StructureSetResult result) {
        List<String> text = new ArrayList<>();
        for (Finding finding : result.setFindings()) {
            text.add(finding.code().text() + " '" + finding.message() + "'");
        }
        return text.toString();
    }

    /** The template's first cell: the structure block sits one block below the test origin. */
    private static LatticePoint origin(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        return LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
    }

    private static LatticePoint wing(LatticePoint origin) {
        return LatticePoint.of(origin.x() + 2, origin.y(), origin.z());
    }

    private static BlockPos pos(LatticePoint cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static BlockState blockAt(GameTestHelper helper, LatticePoint cell) {
        return helper.getLevel().getBlockState(pos(cell));
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

    private static UUID playerId(String tag) {
        return UUID.nameUUIDFromBytes(("cumulus-set-build-gametest/" + tag).getBytes(StandardCharsets.UTF_8));
    }

    private static FakePlayer player(GameTestHelper helper, UUID id) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "cumulus_gametest"));
    }

    private static CommandSourceStack stack(
        GameTestHelper helper,
        CommandSource source,
        FakePlayer player,
        BlockPos at
    ) {
        return new CommandSourceStack(
            source,
            Vec3.atLowerCornerOf(at),
            Vec2.ZERO,
            helper.getLevel(),
            4,
            "gametest",
            Component.literal("gametest"),
            helper.getLevel().getServer(),
            player
        );
    }

    private static final class RecordingSource implements CommandSource {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        private String text() {
            return String.join(" | ", messages);
        }
    }

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException failure) {
            helper.fail("the command did not parse: " + failure.getMessage());
            return 0;
        }
    }
}
