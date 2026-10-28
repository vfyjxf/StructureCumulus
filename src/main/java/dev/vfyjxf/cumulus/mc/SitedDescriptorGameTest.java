package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The sited capture's orientation audit: an unrepresentable root state reports SC-MC-0004. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class SitedDescriptorGameTest {

    private static final Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);
    private static final Orientation pitchUp = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);
    private static final NamespacedId stairs = NamespacedId.of("minecraft", "oak_stairs");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void representableStateReportsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        try {
            level.setBlock(origin, stairs(Direction.EAST), 3);
            CaptureResult.Captured captured = capture(level, origin, clockwise);
            try (SnapshotLease lease = captured.lease()) {
                OccupantSnapshot occupant = lease.view().occupant(pointOf(origin));
                if (occupant == null) {
                    helper.fail("the anchor cell is not readable");
                    return;
                }
            }
            helper.assertTrue(captured.report().isEmpty(), "an upright siting reports no SC-MC-0004");
        } finally {
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void unrepresentableStateReportsTheSitingOrientation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        try {
            level.setBlock(origin, stairs(Direction.EAST), 3);
            CaptureResult.Captured captured = capture(level, origin, pitchUp);
            helper.assertValueEqual(1, captured.report().size(), "one orientation group");
            CaptureReport.Entry entry = captured.report().entries().get(0);
            CuboidSet cells = Objects.requireNonNull(entry.cells(), "the group region");
            helper.assertValueEqual(AdapterCodes.orientation, entry.code(), "the orientation code");
            helper.assertValueEqual(1L, cells.volume(), "one cell in the group");
            helper.assertTrue(
                cells.contains(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())),
                "the audited cell is in the group"
            );
            helper.assertTrue(
                entry.notes().stream().anyMatch(note -> note.contains("property: facing")),
                "the entry names the property"
            );
            helper.assertTrue(
                entry.notes().stream().anyMatch(note -> note.contains("value: east")),
                "the entry names the observed value"
            );
            helper.assertTrue(
                entry.notes().stream().anyMatch(note -> note.contains("orientation: 2")),
                "the entry names the siting orientation"
            );
        } finally {
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void childFramesAreNotAudited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        RecordingLookup lookup = new RecordingLookup();
        lookup.extents.put(origin, new NestedExtent(NamespacedId.of("test", "nested"), Cuboid.of(point), point));
        SnapshotPolicy policy = new SnapshotPolicy(1_000_000, 1, Set.of(OccupantKind.nested), Set.of(), List.of());
        try {
            level.setBlock(origin, stairs(Direction.EAST), 3);
            CaptureResult result = new LevelCapture(level, lookup)
                    .capture(new CaptureRequest(Cuboid.of(point), policy), clockwise);
            if (!(result instanceof CaptureResult.Captured captured)) {
                helper.fail("the nested sited capture failed: " + result);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                WorldView root = lease.view();
                if (root.nested(point) == null) {
                    helper.fail("the child frame is missing");
                    return;
                }
            }
            helper.assertTrue(captured.report().isEmpty(), "an upright siting reports no SC-MC-0004");
            helper.assertTrue(
                lookup.states.stream().anyMatch(state -> state.getValue(StairBlock.FACING) == Direction.EAST),
                "the nested lookup receives the actual state"
            );
        } finally {
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    private static LatticePoint pointOf(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static CaptureResult.Captured capture(
        ServerLevel level,
        BlockPos origin,
        @Nullable Orientation orientation
    ) {
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        CaptureResult result = new LevelCapture(level)
                .capture(new CaptureRequest(Cuboid.of(point), SnapshotPolicy.minimal()), orientation);
        if (result instanceof CaptureResult.Captured captured) {
            return captured;
        }
        throw new GameTestAssertException("capture did not succeed: " + result);
    }

    private static BlockState stairs(Direction facing) {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private static BlockPos structureOrigin(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        return new BlockPos((base.getX() & ~15) + 8, base.getY(), (base.getZ() & ~15) + 8);
    }

    private static final class RecordingLookup implements NestedExtentLookup {

        private final Map<BlockPos, NestedExtent> extents = new HashMap<>();
        private final List<BlockState> states = new ArrayList<>();

        @Override
        public @Nullable NestedExtent block(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity
        ) {
            states.add(state);
            return extents.get(pos);
        }

        @Override
        public @Nullable NestedExtent entity(Entity entity) {
            return null;
        }
    }
}
