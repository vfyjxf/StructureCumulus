package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.BlockNestedExtentContext;
import dev.vfyjxf.cumulus.mc.capability.CapabilityNestedExtentProvider;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolder;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolders;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Adversarial capture/host GameTests: sitings, world edges, nested providers, traits, leases and managers. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class CaptureAdversarialGameTest {

    private static final Orientation pitchUp = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);
    private static final Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);

    // --- sitings and the audit -------------------------------------------------------------------

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void identityAndNoSitingBothStayWorldFrameWithoutAnAudit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(BlockPos.ZERO);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        level.setBlock(cell, stairs, 3);
        Cuboid bounds = oneCell(cell);
        LevelCapture capture = new LevelCapture(level);
        try {
            for (String name : List.of("no siting", "identity")) {
                CaptureResult result = name.equals("identity")
                        ? capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()), Orientation.identity)
                        : capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
                if (!(result instanceof CaptureResult.Captured captured)) {
                    helper.fail(name + " capture failed: " + result);
                    return;
                }
                try (SnapshotLease lease = captured.lease()) {
                    if (!captured.report().isEmpty()) {
                        helper.fail(name + " produced an audit: " + captured.report().entries());
                        return;
                    }
                    BlockState value = lease.view().get(lattice(cell), HostTypes.blockState);
                    if (value == null || !value.equals(stairs)) {
                        helper.fail(name + " changed the world-frame host value: " + value);
                        return;
                    }
                }
            }
        } finally {
            level.removeBlock(cell, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aNonUprightSitingAuditsGroupsAndKeepsWorldFrameValues(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        BlockPos first = aligned(base);
        BlockPos second = first.east();
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        level.setBlock(first, stairs, 3);
        level.setBlock(second, stairs, 3);
        Cuboid bounds = Cuboid
                .of(first.getX(), first.getY(), first.getZ(), second.getX() + 1, second.getY() + 1, second.getZ() + 1);
        try {
            CaptureResult result = new LevelCapture(level)
                    .capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()), pitchUp);
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("the pitched capture failed: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                helper.assertValueEqual(1, captured.report().size(), "one grouped audit entry");
                var entry = captured.report().entries().get(0);
                helper.assertValueEqual(AdapterCodes.orientation, entry.code(), "the orientation code");
                helper.assertValueEqual(
                    2L,
                    Objects.requireNonNull(entry.cells(), "cells").volume(),
                    "both cells are grouped"
                );
                helper.assertTrue(
                    entry.notes().stream().anyMatch(note -> note.contains("property: facing")),
                    "the group names the failing property: " + entry.notes()
                );
                helper.assertValueEqual(
                    stairs,
                    lease.view().get(lattice(first), HostTypes.blockState),
                    "the audited capture still publishes the observed world state"
                );
            }
        } finally {
            level.removeBlock(first, false);
            level.removeBlock(second, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void anUprightTurnIsRepresentableAndAuditsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = aligned(helper.absolutePos(BlockPos.ZERO));
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        level.setBlock(cell, stairs, 3);
        try {
            CaptureResult result = new LevelCapture(level)
                    .capture(new CaptureRequest(oneCell(cell), SnapshotPolicy.minimal()), clockwise);
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("the turned capture failed: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                helper.assertTrue(captured.report().isEmpty(), "a turn audits nothing: " + captured.report().entries());
                helper.assertValueEqual(
                    stairs,
                    lease.view().get(lattice(cell), HostTypes.blockState),
                    "an upright turn publishes the observed state, not a mapped one"
                );
            }
        } finally {
            level.removeBlock(cell, false);
        }
        helper.succeed();
    }

    // --- policy and report boundaries ------------------------------------------------------------

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theOverCapBoundaryIsTheVolumeNotTheVolumeMinusOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = aligned(helper.absolutePos(BlockPos.ZERO));
        Cuboid bounds = Cuboid
                .of(cell.getX(), cell.getY(), cell.getZ(), cell.getX() + 2, cell.getY() + 1, cell.getZ() + 1);
        long volume = bounds.volume();

        CaptureResult atCeiling = new LevelCapture(level)
                .capture(new CaptureRequest(bounds, policy((int) volume, 0, Set.of())));
        if (!(atCeiling instanceof CaptureResult.Captured)) {
            helper.fail("a volume equal to the ceiling is not captured: " + atCeiling);
            return;
        }
        CaptureResult over = new LevelCapture(level)
                .capture(new CaptureRequest(bounds, policy((int) volume - 1, 0, Set.of())));
        if (!(over instanceof CaptureResult.TooLarge tooLarge)) {
            helper.fail("a volume over the ceiling is not TooLarge: " + over);
            return;
        }
        helper.assertValueEqual((int) volume, tooLarge.elements(), "the elements are the volume");
        helper.assertValueEqual(1, tooLarge.report().size(), "one over-cap entry");
        helper.assertValueEqual(AdapterCodes.overCap, tooLarge.report().entries().get(0).code(), "the over-cap code");
        helper.assertValueEqual(
            volume,
            Objects.requireNonNull(tooLarge.report().entries().get(0).cells(), "cells").volume(),
            "the report region is the bounds"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aLeaseGoesStaleOnRecaptureAndOnCloseWhileCapturingAfterCloseThrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cell = aligned(helper.absolutePos(BlockPos.ZERO));
        Cuboid bounds = oneCell(cell);
        LevelCapture capture = new LevelCapture(level);
        CaptureResult first = capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (!(first instanceof CaptureResult.Captured firstCaptured)) {
            helper.fail("the first capture failed: " + first);
            return;
        }
        SnapshotLease firstLease = firstCaptured.lease();
        helper.assertTrue(firstLease.isCurrent(), "a fresh lease is current");
        CaptureResult second = capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (!(second instanceof CaptureResult.Captured secondCaptured)) {
            helper.fail("the second capture failed: " + second);
            return;
        }
        helper.assertTrue(!firstLease.isCurrent(), "the second capture invalidates the first lease");
        SnapshotLease secondLease = secondCaptured.lease();
        capture.close();
        helper.assertTrue(!secondLease.isCurrent(), "closing the capture invalidates the live lease");
        capture.close();
        try {
            capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
            helper.fail("a capture after close did not throw");
        } catch (IllegalStateException expected) {
            helper.assertValueEqual("capture is closed", expected.getMessage(), "the closed capture message");
        }
        helper.succeed();
    }

    // --- world edges and child frames ------------------------------------------------------------

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aRootBeyondBuildHeightIsAirButAChildSectionIsRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = aligned(helper.absolutePos(BlockPos.ZERO));
        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight();
        BlockPos floorCell = new BlockPos(base.getX(), minBuild, base.getZ());
        level.setBlock(floorCell, Blocks.STONE.defaultBlockState(), 3);
        LatticePoint below = LatticePoint.of(base.getX(), minBuild - 1, base.getZ());
        Cuboid rootBounds = Cuboid
                .of(base.getX(), minBuild - 1, base.getZ(), base.getX() + 1, minBuild + 1, base.getZ() + 1);
        CaptureResult root = new LevelCapture(level).capture(new CaptureRequest(rootBounds, SnapshotPolicy.minimal()));
        if (!(root instanceof CaptureResult.Captured captured)) {
            helper.fail("the root capture across the build floor failed: " + root);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            helper.assertValueEqual(
                ReadStatus.readable,
                lease.view().statusOf(below),
                "a root cell below the build floor stays readable air"
            );
            OccupantSnapshot air = lease.view().occupant(below);
            helper.assertTrue(air != null && air.isAir(), "the below-floor root cell is air, not unreadable");
        }

        LatticePoint farOrigin = LatticePoint.of(base.getX(), maxBuild + 5, base.getZ());
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "high_extent"),
            Cuboid.of(
                farOrigin.x(),
                farOrigin.y(),
                farOrigin.z(),
                farOrigin.x() + 1,
                farOrigin.y() + 1,
                farOrigin.z() + 1
            ),
            farOrigin
        );
        AtomicInteger queries = new AtomicInteger();
        NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction>() {
                    @Override
                    public NestedExtentHolder<BlockNestedExtentContext> holder() {
                        return NestedExtentHolders.block;
                    }

                    @Override
                    public @Nullable NestedExtent get(BlockNestedExtentContext actual, @Nullable Direction context) {
                        queries.incrementAndGet();
                        return extent;
                    }
                }).build();
        CaptureResult nested = new LevelCapture(level, lookup)
                .capture(new CaptureRequest(oneCell(floorCell), policy(1_000_000, 1, Set.of(OccupantKind.nested))));
        if (!(nested instanceof CaptureResult.Captured nestedCaptured)) {
            helper.fail("the nested capture failed: " + nested);
            return;
        }
        try (SnapshotLease lease = nestedCaptured.lease()) {
            helper.assertTrue(queries.get() > 0, "the nested provider was queried");
            WorldView child = lease.view().nested(lattice(floorCell));
            if (child == null) {
                helper.fail("the out-of-build-height child frame is missing");
                return;
            }
            SectionSlot slot = child.slot(child.sectionOf(LatticePoint.origin));
            helper.assertTrue(
                slot instanceof SectionSlot.Refused,
                "an out-of-build-height child section must be refused: " + slot
            );
            SectionSlot.Refused refused = (SectionSlot.Refused) slot;
            helper.assertValueEqual(ReadStatus.outOfBounds, refused.status(), "the refused child status");
            helper.assertValueEqual(AdapterCodes.notCaptured, refused.code(), "the refused child code");
            helper.assertTrue(
                child.read(LatticePoint.origin) instanceof CellRead.Unreadable,
                "the child cell is unreadable, not air"
            );
        }
        level.removeBlock(floorCell, false);
        helper.succeed();
    }

    // --- nested providers --------------------------------------------------------

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aNestedChildAtANonzeroOriginOwnsItsLocalFrameAndSnapshot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = aligned(helper.absolutePos(BlockPos.ZERO));
        BlockPos childOrigin = anchor.offset(8, 1, 8);
        BlockPos childBlock = childOrigin.offset(1, 0, 0);
        level.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(childBlock, Blocks.OAK_LOG.defaultBlockState(), 3);
        LatticePoint origin = lattice(childOrigin);
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "child"),
            Cuboid.of(origin.x(), origin.y(), origin.z(), origin.x() + 2, origin.y() + 2, origin.z() + 2),
            origin
        );
        NestedExtentLookup lookup = blockLookup(actual -> extent);
        try {
            CaptureResult result = new LevelCapture(level, lookup)
                    .capture(new CaptureRequest(oneCell(anchor), policy(1_000_000, 1, Set.of(OccupantKind.nested))));
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("the nested capture failed: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                WorldView child = lease.view().nested(lattice(anchor));
                if (child == null) {
                    helper.fail("the child frame is missing");
                    return;
                }
                helper.assertValueEqual(origin, child.origin(), "the child keeps the declared origin");
                helper.assertValueEqual(
                    Cuboid.of(0, 0, 0, 2, 2, 2),
                    child.bounds(),
                    "the child publishes local bounds"
                );
                helper.assertValueEqual(1, child.frame(), "the child frame");
                helper.assertValueEqual(1, child.depth(), "the child depth");
                helper.assertValueEqual(
                    Blocks.OAK_LOG.defaultBlockState(),
                    child.get(LatticePoint.of(1, 0, 0), HostTypes.blockState),
                    "the child snapshot reads its own world cells"
                );
                BlockState parentAtChild = lease.view().get(lattice(childBlock), HostTypes.blockState);
                helper.assertTrue(
                    parentAtChild == null || parentAtChild.isAir(),
                    "the parent snapshot does not carry the child host value"
                );
            }
        } finally {
            level.removeBlock(anchor, false);
            level.removeBlock(childBlock, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aThrowingNestedProviderIsContainedAndAMissingProviderAnswersNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = aligned(helper.absolutePos(BlockPos.ZERO));
        level.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3);
        NestedExtentLookup throwing = NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction>() {
                    @Override
                    public NestedExtentHolder<BlockNestedExtentContext> holder() {
                        return NestedExtentHolders.block;
                    }

                    @Override
                    public @Nullable NestedExtent get(BlockNestedExtentContext actual, @Nullable Direction context) {
                        throw new IllegalStateException("provider failure");
                    }
                }).build();
        try {
            CaptureResult result = new LevelCapture(level, throwing)
                    .capture(new CaptureRequest(oneCell(anchor), policy(1_000_000, 1, Set.of(OccupantKind.nested))));
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("a throwing provider must not fail the capture: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                helper.assertTrue(lease.view().nested(lattice(anchor)) == null, "a throwing provider yields no child");
                helper.assertTrue(
                    lease.view().occupants(oneCell(anchor), OccupantFilter.ofKind(OccupantKind.nested)).isEmpty(),
                    "a throwing provider yields no nested body"
                );
                helper.assertTrue(captured.report().isEmpty(), "a throwing provider reports no entry");
                OccupantSnapshot block = lease.view().occupant(lattice(anchor));
                helper.assertTrue(
                    block != null && block.descriptor().type().equals(NamespacedId.of("minecraft", "stone")),
                    "the block occupant is still captured"
                );
            }
            CaptureResult missing = new LevelCapture(level, NestedExtentLookup.builder().build())
                    .capture(new CaptureRequest(oneCell(anchor), policy(1_000_000, 1, Set.of(OccupantKind.nested))));
            if (!(missing instanceof CaptureResult.Captured capturedMissing)) {
                helper.fail("a missing provider must not fail the capture: " + missing);
                return;
            }
            try (SnapshotLease lease = capturedMissing.lease()) {
                helper.assertTrue(lease.view().nested(lattice(anchor)) == null, "a missing provider yields no child");
            }
        } finally {
            level.removeBlock(anchor, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theCapFrameKeepsTheBodyAndReportsTheDepthCut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = aligned(helper.absolutePos(BlockPos.ZERO));
        level.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3);
        LatticePoint anchorPoint = lattice(anchor);
        NestedExtent childExtent = new NestedExtent(
            NamespacedId.of("test", "depth_child"),
            Cuboid.of(
                anchorPoint.x(),
                anchorPoint.y(),
                anchorPoint.z(),
                anchorPoint.x() + 1,
                anchorPoint.y() + 1,
                anchorPoint.z() + 1
            ),
            anchorPoint
        );
        try {
            CaptureResult result = new LevelCapture(level, blockLookup(actual -> childExtent))
                    .capture(new CaptureRequest(oneCell(anchor), policy(1_000_000, 1, Set.of(OccupantKind.nested))));
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("the depth-capped capture failed: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                WorldView child = lease.view().nested(lattice(anchor));
                if (child == null) {
                    helper.fail("depth one must still capture the child");
                    return;
                }
                helper.assertTrue(
                    child.nested(LatticePoint.origin) == null,
                    "the depth ceiling builds no grandchild"
                );
                helper.assertValueEqual(
                    1,
                    child.occupants(child.bounds(), OccupantFilter.ofKind(OccupantKind.nested)).size(),
                    "the cap frame keeps the discovered body"
                );
                helper.assertValueEqual(1, captured.report().size(), "the cap frame reports its cut");
                var entry = captured.report().entries().get(0);
                helper.assertValueEqual(AdapterCodes.cut, entry.code(), "the cut code");
                helper.assertTrue(
                    entry.notes().stream().anyMatch(note -> note.contains("reason: depth")),
                    "the cut names the depth ceiling: " + entry.notes()
                );
                helper.assertTrue(entry.cells() == null, "a child-frame cut carries no region");
            }
        } finally {
            level.removeBlock(anchor, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aNestedCycleIsCutWithAReportEntry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = aligned(helper.absolutePos(BlockPos.ZERO));
        level.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3);
        LatticePoint anchorPoint = lattice(anchor);
        NestedExtent childExtent = new NestedExtent(
            NamespacedId.of("test", "cycle_child"),
            Cuboid.of(
                anchorPoint.x(),
                anchorPoint.y(),
                anchorPoint.z(),
                anchorPoint.x() + 1,
                anchorPoint.y() + 1,
                anchorPoint.z() + 1
            ),
            anchorPoint
        );
        try {
            CaptureResult result = new LevelCapture(level, blockLookup(actual -> childExtent))
                    .capture(new CaptureRequest(oneCell(anchor), policy(1_000_000, 2, Set.of(OccupantKind.nested))));
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("the cycle capture failed: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                helper.assertValueEqual(1, captured.report().size(), "one cycle cut entry");
                var entry = captured.report().entries().get(0);
                helper.assertValueEqual(AdapterCodes.cut, entry.code(), "the cut code");
                helper.assertTrue(
                    entry.notes().stream().anyMatch(note -> note.contains("reason: cycle")),
                    "the cut names the cycle: " + entry.notes()
                );
                helper.assertTrue(entry.cells() == null, "a non-root cut carries no region");
                helper.assertTrue(lease.view().nested(lattice(anchor)) != null, "the first child is still captured");
            }
        } finally {
            level.removeBlock(anchor, false);
        }
        helper.succeed();
    }

    // --- entities and windows --------------------------------------------------------------------

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void anEntitySweepCapturesEdgeStraddlingBodiesAndAWindowIsAdditiveNotAFilter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        Cuboid bounds = Cuboid
                .of(base.getX(), base.getY(), base.getZ(), base.getX() + 2, base.getY() + 2, base.getZ() + 2);
        Vec3 edge = new Vec3(1.9, 1.0, 1.9);
        Vec3 far = new Vec3(30.5, 1.0, 30.5);
        helper.spawnWithNoFreeWill(EntityType.CHICKEN, edge);
        helper.spawnWithNoFreeWill(EntityType.CHICKEN, far);
        EntityWindow cowWindow = Objects
                .requireNonNull(EntityWindow.of(bounds, NamespacedId.of("minecraft", "cow")), "the cow window");
        SnapshotPolicy policy = new SnapshotPolicy(
            1_000_000,
            0,
            Set.of(OccupantKind.entity),
            Set.of(),
            List.of(cowWindow)
        );
        CaptureResult result = new LevelCapture(level).capture(new CaptureRequest(bounds, policy));
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("the entity capture failed: " + result);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            List<OccupantSnapshot> entities = lease.view()
                    .occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity));
            helper.assertValueEqual(1, entities.size(), "only the straddling body is captured");
            OccupantSnapshot body = entities.get(0);
            helper.assertValueEqual(
                NamespacedId.of("minecraft", "chicken"),
                body.descriptor().type(),
                "the chicken is captured"
            );
            helper.assertTrue(
                body.footprint().max().x() > bounds.max().x(),
                "the captured body footprint is not clipped to the capture"
            );
            helper.assertTrue(body.footprint().intersects(bounds), "the captured body intersects the bounds");
            helper.assertTrue(
                lease.view().occupants(bounds, OccupantFilter.ofType(NamespacedId.of("minecraft", "cow"))).isEmpty(),
                "the cow window names no cow and the window never filters an overlapping chicken"
            );
        }
        helper.succeed();
    }

    // --- traits and capabilities ----------------------------------------------------------------

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aChestTraitIsTheLiveCapabilityWhileStoneAndUnknownTraitsAreAnswered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chestPos = aligned(helper.absolutePos(BlockPos.ZERO));
        BlockPos stonePos = chestPos.east();
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), 3);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity seeded) {
            seeded.setItem(0, new ItemStack(Items.DIAMOND, 3));
        }
        Cuboid bounds = Cuboid.of(
            chestPos.getX(),
            chestPos.getY(),
            chestPos.getZ(),
            stonePos.getX() + 1,
            stonePos.getY() + 1,
            stonePos.getZ() + 1
        );
        NamespacedId unknownTrait = NamespacedId.of("test", "unknown_trait");
        SnapshotPolicy policy = new SnapshotPolicy(
            1_000_000,
            0,
            Set.of(),
            Set.of(OccupantTraits.inventory.id(), OccupantTraits.energy.id(), OccupantTraits.fluid.id(), unknownTrait),
            List.of()
        );
        try {
            CaptureResult result = new LevelCapture(level).capture(new CaptureRequest(bounds, policy));
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("the trait capture failed: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                OccupantSnapshot chest = lease.view().occupant(lattice(chestPos));
                if (chest == null) {
                    helper.fail("the chest cell is unreadable");
                    return;
                }
                ChestBlockEntity chestEntity = level.getBlockEntity(chestPos) instanceof ChestBlockEntity entity
                        ? entity
                        : null;
                helper.assertTrue(chestEntity != null, "the chest block entity exists");
                IItemHandler handler = chest.trait(OccupantTraits.inventory);
                helper.assertTrue(
                    handler != null,
                    "the captured inventory trait is present: " + chest.slot(OccupantTraits.inventory.id())
                );
                helper.assertValueEqual(27, Objects.requireNonNull(handler).getSlots(), "the chest slot count");
                helper.assertTrue(handler.getStackInSlot(0).is(Items.DIAMOND), "the captured stack is the live stack");
                Objects.requireNonNull(chestEntity).setItem(0, new ItemStack(Items.EMERALD, 5));
                helper.assertTrue(
                    handler.getStackInSlot(0).is(Items.EMERALD),
                    "the trait value is the live capability"
                );
                helper.assertValueEqual(5, handler.getStackInSlot(0).getCount(), "the live stack size");
                helper.assertTrue(
                    chest.slot(OccupantTraits.energy.id()) instanceof TraitSlot.Absent,
                    "a chest has no energy, which is absent and not refused"
                );
                helper.assertTrue(
                    chest.slot(OccupantTraits.fluid.id()) instanceof TraitSlot.Absent,
                    "a chest has no fluid handler, which is absent and not refused"
                );
                helper.assertTrue(
                    chest.slot(unknownTrait) instanceof TraitSlot.Refused refused
                            && refused.refusal().status() == ReadStatus.denied,
                    "an unknown trait id is refused as denied"
                );
                OccupantSnapshot stone = lease.view().occupant(lattice(stonePos));
                helper.assertTrue(
                    stone != null && stone.slot(OccupantTraits.inventory.id()) instanceof TraitSlot.Absent,
                    "stone has the inventory trait absent, not denied"
                );
            }
        } finally {
            level.removeBlock(chestPos, false);
            level.removeBlock(stonePos, false);
        }
        helper.succeed();
    }

    // --- manager bounds integration --------------------------------------------------------------

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theHostManagerCommitsSitedAndNestedBoundsAndFindsThemByWorldCell(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        LatticePoint origin = lattice(base);
        Siting siting = Siting.of(origin, clockwise);
        CuboidSet formed = CuboidSet.of(
            Cuboid.of(origin.x(), origin.y(), origin.z(), origin.x() + 2, origin.y() + 2, origin.z() + 2),
            Cuboid.of(origin.x() + 8, origin.y() + 1, origin.z() + 8, origin.x() + 10, origin.y() + 3, origin.z() + 10)
        );
        StructureAttempt attempt = manager.begin(
            state.world(level),
            new StructureSpec(state.owner(), siting, NamespacedId.of("test", "adversarial_manager_structure"))
        );
        StructureInstance instance = attempt.commit(formed);
        try {
            helper.assertValueEqual(
                1,
                manager.findAt(origin).size(),
                "a world cell inside the root box finds the instance"
            );
            helper.assertValueEqual(
                1,
                manager.findAt(LatticePoint.of(origin.x() + 9, origin.y() + 2, origin.z() + 9)).size(),
                "a nested world cell inside the second box finds the same instance"
            );
            helper.assertValueEqual(
                0,
                manager.findAt(LatticePoint.of(origin.x() + 4, origin.y(), origin.z())).size(),
                "the gap between the boxes finds nothing"
            );
            helper.assertValueEqual(siting, instance.siting(), "the instance keeps the siting");
            helper.assertValueEqual(formed, instance.bounds(), "the instance keeps the committed bounds verbatim");
            helper.assertTrue(
                manager.find(instance.owner(), siting) == instance,
                "the owner/siting lookup answers the same instance"
            );
        } finally {
            manager.onEvent(new StructureEvent.Invalidated(instance.id(), "adversarial cleanup"));
        }
        helper.assertTrue(manager.find(instance.id()) == null, "the invalidated instance is removed");
        helper.succeed();
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static SnapshotPolicy policy(int maxElements, int maxDepth, Set<OccupantKind> kinds) {
        return new SnapshotPolicy(maxElements, maxDepth, kinds, Set.of(), List.of());
    }

    private static Cuboid oneCell(BlockPos pos) {
        return Cuboid.of(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private static LatticePoint lattice(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos aligned(BlockPos pos) {
        return new BlockPos((pos.getX() & ~15) + 8, pos.getY(), (pos.getZ() & ~15) + 8);
    }

    private static NestedExtentLookup blockLookup(Function<BlockNestedExtentContext, NestedExtent> extents) {
        return NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction>() {
                    @Override
                    public NestedExtentHolder<BlockNestedExtentContext> holder() {
                        return NestedExtentHolders.block;
                    }

                    @Override
                    public @Nullable NestedExtent get(BlockNestedExtentContext actual, @Nullable Direction context) {
                        return extents.apply(actual);
                    }
                }).build();
    }

}
