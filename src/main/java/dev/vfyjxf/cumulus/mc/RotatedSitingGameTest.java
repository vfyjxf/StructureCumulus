package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The settle path through a non-identity siting: the anchor cell is read before the world bounds are known. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class RotatedSitingGameTest {

    private static final NamespacedId anchorCheck = NamespacedId.of(Constants.modId, "rotated_anchor_check");
    private static final NamespacedId otherCheck = NamespacedId.of(Constants.modId, "rotated_other_check");
    private static final NamespacedId bayId = NamespacedId.of(Constants.modId, "rotated_bay");
    private static final NamespacedId blueprintId = NamespacedId.of(Constants.modId, "rotated_cell");
    private static final NamespacedId stairs = NamespacedId.of("minecraft", "oak_stairs");
    private static final NamespacedId endRod = NamespacedId.of("minecraft", "end_rod");
    private static final Cuboid flatBounds = Cuboid.of(0, 0, 0, 2, 1, 1);
    private static final CuboidSet flatCells = CuboidSet.of(flatBounds);
    private static final LatticePoint secondLocal = LatticePoint.of(1, 0, 0);
    private static final List<ObservedHeading> observedHeadings = List.of(
        new ObservedHeading(Direction.NORTH, Orientation.identity),
        new ObservedHeading(Direction.EAST, Orientation.of(Turn.clockwise, Tilt.level, Flip.none)),
        new ObservedHeading(Direction.SOUTH, Orientation.of(Turn.half, Tilt.level, Flip.none)),
        new ObservedHeading(Direction.WEST, Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none))
    );

    private record ObservedHeading(Direction facing, Orientation orientation) {}

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void oneBlueprintMatchesEveryAllowedTurn(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos structure = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos((structure.getX() & ~15) + 8, structure.getY(), (structure.getZ() & ~15) + 8);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        ClaimLedger ledger = state.claims(level);
        Blueprint blueprint = anchoredBlueprint(stairs);
        List<Orientation> orientations = new ArrayList<>();
        for (ObservedHeading observed : observedHeadings) {
            Siting siting = settleOnce(helper, state, level, ledger, blueprint, point, origin, observed);
            if (siting == null) {
                return;
            }
            orientations.add(siting.placement().orientation());
        }
        helper.assertValueEqual(4, Set.copyOf(orientations).size(), "the siting orientations are distinct");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aTiltIsStillRefusedWhenNotAllowed(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos structure = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos((structure.getX() & ~15) + 8, structure.getY(), (structure.getZ() & ~15) + 8);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        BlockState rod = Blocks.END_ROD.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP);
        level.setBlock(origin, rod, 3);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        try {
            MatchResult result = SettleCommand.settle(source, state, level, anchoredBlueprint(endRod), point);
            helper.assertTrue(result == null, "the tilted anchor must not form: " + result);
            String text = recording.text();
            helper.assertTrue(text.contains("SC-BPT-0006"), "the refusal carries the anchor-invalid code: " + text);
            helper.assertTrue(
                text.contains("no allowed orientation turns north into up"),
                "the refusal names the headings: " + text
            );
        } finally {
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    private static @Nullable Siting settleOnce(
        GameTestHelper helper,
        ServerState state,
        ServerLevel level,
        ClaimLedger ledger,
        Blueprint blueprint,
        LatticePoint point,
        BlockPos origin,
        ObservedHeading observed
    ) {
        BlockState anchor = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, observed.facing());
        LatticePoint expectedSecond = Siting.of(point, observed.orientation()).toWorld(secondLocal);
        if (expectedSecond == null) {
            helper.fail("the expected siting leaves the lattice");
            return null;
        }
        BlockPos expectedSecondPos = new BlockPos(expectedSecond.x(), expectedSecond.y(), expectedSecond.z());
        level.setBlock(expectedSecondPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(origin, anchor, 3);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        Siting[] formed = new Siting[1];
        try {
            MatchResult result = SettleCommand.settle(source, state, level, blueprint, point);
            if (!(result instanceof MatchResult.Formed settled)) {
                helper.fail(
                    "the settle at " + observed.facing() + " did not form: " + result + " / " + recording.text()
                );
                return null;
            }
            Siting siting = settled.siting();
            formed[0] = siting;
            helper.assertValueEqual(
                observed.orientation(),
                siting.placement().orientation(),
                "the orientation selected at " + observed.facing()
            );
            LatticePoint second = siting.toWorld(secondLocal);
            Cuboid worldBounds = siting.toWorld(blueprint.bounds());
            if (second == null || worldBounds == null) {
                helper.fail("the siting leaves the lattice");
                return null;
            }
            helper.assertValueEqual(2, settled.claims().rows().size(), "the rotated claim rows");
            List<LatticeKey> cells = new ArrayList<>();
            for (ClaimRow row : ledger.read(worldBounds).rows()) {
                cells.add(row.cell());
            }
            helper.assertTrue(cells.contains(LatticeKey.of(point)), "the anchor cell row is claimed");
            helper.assertTrue(cells.contains(LatticeKey.of(second)), "the rotated cell row is claimed");
            return siting;
        } finally {
            if (formed[0] != null) {
                release(ledger, state, level, formed[0]);
            }
            level.removeBlock(origin, false);
            level.removeBlock(expectedSecondPos, false);
        }
    }

    private static Blueprint anchoredBlueprint(NamespacedId anchorType) {
        Bay bay = Bay.of(bayId, flatCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(flatBounds).check(
            Check.of(anchorCheck, bay, Focuses.occupant(LatticePoint.of(0, 0, 0)), Expectations.block(anchorType))
        ).check(Check.of(otherCheck, bay, Focuses.occupant(secondLocal), Expectations.air()))
                .anchor(new AnchorSpec(AnchorRoles.primary, "facing", "north"))
                .orientation(
                    OrientationPolicy.builder(AnchorRoles.primary)
                            .allowTurns(Turn.none, Turn.clockwise, Turn.half, Turn.counterclockwise).build()
                ).build();
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

    private static void release(ClaimLedger ledger, ServerState state, ServerLevel level, Siting siting) {
        StructureInstance instance = state.structures(level).find(state.world(level), state.owner(), siting);
        if (instance != null) {
            ledger.release(instance.id());
        }
    }
}
