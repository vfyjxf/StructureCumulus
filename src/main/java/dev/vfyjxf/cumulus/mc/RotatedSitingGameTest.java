package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.AnchorRoles;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** The settle path through a non-identity siting: the anchor cell is read before the world bounds are known. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class RotatedSitingGameTest {

    private static final Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);
    private static final Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);
    private static final Orientation pitchUp = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);
    private static final NamespacedId anchorCheck = NamespacedId.of(Constants.modId, "rotated_anchor_check");
    private static final NamespacedId otherCheck = NamespacedId.of(Constants.modId, "rotated_other_check");
    private static final NamespacedId bayId = NamespacedId.of(Constants.modId, "rotated_bay");
    private static final NamespacedId blueprintId = NamespacedId.of(Constants.modId, "rotated_cell");
    private static final NamespacedId stairs = NamespacedId.of("minecraft", "oak_stairs");
    private static final NamespacedId endRod = NamespacedId.of("minecraft", "end_rod");
    private static final Cuboid flatBounds = Cuboid.of(0, 0, 0, 2, 1, 1);
    private static final Cuboid tallBounds = Cuboid.of(0, 0, 0, 2, 2, 1);
    private static final CuboidSet flatCells = CuboidSet.of(flatBounds);
    private static final CuboidSet tallCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(0, 1, 0, 1, 2, 1));

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settlesAnchoredBlueprintAtAClockwiseSiting(GameTestHelper helper) {
        settleAt(
            helper,
            clockwise,
            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
            "east",
            stairs,
            flatBounds,
            flatCells,
            LatticePoint.of(1, 0, 0)
        );
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settlesAnchoredBlueprintAtAHalfTurnSiting(GameTestHelper helper) {
        settleAt(
            helper,
            half,
            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH),
            "south",
            stairs,
            flatBounds,
            flatCells,
            LatticePoint.of(1, 0, 0)
        );
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settlesAnchoredBlueprintAtAPitchUpSiting(GameTestHelper helper) {
        settleAt(
            helper,
            pitchUp,
            Blocks.END_ROD.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP),
            "up",
            endRod,
            tallBounds,
            tallCells,
            LatticePoint.of(0, 1, 0)
        );
    }

    private static void settleAt(
        GameTestHelper helper,
        Orientation orientation,
        BlockState anchorState,
        String anchorValue,
        NamespacedId anchorType,
        Cuboid bounds,
        CuboidSet bayCells,
        LatticePoint secondLocal
    ) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos structure = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos((structure.getX() & ~15) + 8, structure.getY(), (structure.getZ() & ~15) + 8);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        Siting siting = Siting.of(point, orientation);
        LatticePoint second = siting.toWorld(secondLocal);
        if (second == null) {
            helper.fail("the siting leaves the lattice");
            return;
        }
        BlockPos secondPos = new BlockPos(second.x(), second.y(), second.z());
        level.setBlock(origin, anchorState, 3);
        level.setBlock(secondPos, Blocks.AIR.defaultBlockState(), 3);
        SavedClaims ledger = state.claims(level);
        ledger.release(state.owner(), siting);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        try {
            Blueprint blueprint = anchoredBlueprint(
                orientation,
                anchorValue,
                anchorType,
                bounds,
                bayCells,
                secondLocal
            );
            MatchResult result = SettleCommand.settle(source, state, level, blueprint, point);
            if (!(result instanceof MatchResult.Formed formed)) {
                helper.fail("the anchored settle did not form: " + result + " / " + recording.text());
                return;
            }
            helper.assertValueEqual(2, formed.claims().rows().size(), "the rotated claim rows");
            Cuboid worldBounds = siting.toWorld(bounds);
            if (worldBounds == null) {
                helper.fail("the rotated bounds leave the lattice");
                return;
            }
            List<ClaimRow> rows = ledger.read(worldBounds).rows();
            helper.assertValueEqual(2, rows.size(), "the rows inside the phase-2 bounds");
            List<LatticeKey> cells = new ArrayList<>();
            for (ClaimRow row : rows) {
                cells.add(row.cell());
            }
            helper.assertTrue(cells.contains(LatticeKey.of(point)), "the anchor cell row is claimed");
            helper.assertTrue(cells.contains(LatticeKey.of(second)), "the rotated cell row is claimed");
        } finally {
            ledger.release(state.owner(), siting);
            level.removeBlock(origin, false);
            level.removeBlock(secondPos, false);
        }
        helper.succeed();
    }

    private static Blueprint anchoredBlueprint(
        Orientation orientation,
        String anchorValue,
        NamespacedId anchorType,
        Cuboid bounds,
        CuboidSet bayCells,
        LatticePoint secondLocal
    ) {
        Bay bay = Bay.of(bayId, bayCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(bounds).check(
            Check.of(anchorCheck, bay, Focuses.occupant(LatticePoint.of(0, 0, 0)), Expectations.block(anchorType))
        ).check(Check.of(otherCheck, bay, Focuses.occupant(secondLocal), Expectations.air()))
                .anchor(new AnchorSpec(AnchorRoles.primary, "facing", anchorValue))
                .orientation(OrientationPolicy.builder(AnchorRoles.primary).allow(orientation).build()).build();
    }

    private static CommandSourceStack stack(GameTestHelper helper, BlockPos origin, CommandSource source) {
        return new CommandSourceStack(
            source,
            Vec3.atLowerCornerOf(origin),
            Vec2.ZERO,
            helper.getLevel(),
            4,
            "gametest",
            Component.literal("gametest"),
            helper.getLevel().getServer(),
            null
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
            return String.join("\n", messages);
        }
    }
}
