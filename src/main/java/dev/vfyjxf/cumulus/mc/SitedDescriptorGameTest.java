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
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
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

/** Sited captures project root-frame descriptors into the definition frame while child frames stay world-frame. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class SitedDescriptorGameTest {

    private static final Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);
    private static final Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);
    private static final Orientation pitchUp = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);
    private static final NamespacedId stairs = NamespacedId.of("minecraft", "oak_stairs");
    private static final NamespacedId checkId = NamespacedId.of(Constants.modId, "sited_descriptor_check");
    private static final NamespacedId bayId = NamespacedId.of(Constants.modId, "sited_descriptor_bay");
    private static final NamespacedId blueprintId = NamespacedId.of(Constants.modId, "sited_descriptor_cell");
    private static final LatticePoint localOrigin = LatticePoint.of(0, 0, 0);
    private static final Cuboid localBounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void uprightSitedDescriptorsUseDefinitionFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        try {
            level.setBlock(origin, stairs(Direction.EAST), 3);
            CaptureResult.Captured clockwiseCapture = capture(level, origin, clockwise);
            try (SnapshotLease lease = clockwiseCapture.lease()) {
                assertFacing(helper, lease.view(), pointOf(origin), "north", "clockwise descriptor");
            }
            helper.assertTrue(clockwiseCapture.report().isEmpty(), "an upright siting reports no SC-MC-0004");

            level.setBlock(origin, stairs(Direction.SOUTH), 3);
            CaptureResult.Captured halfCapture = capture(level, origin, half);
            try (SnapshotLease lease = halfCapture.lease()) {
                assertFacing(helper, lease.view(), pointOf(origin), "north", "half-turn descriptor");
            }
            helper.assertTrue(halfCapture.report().isEmpty(), "a half turn reports no SC-MC-0004");
        } finally {
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void nonUprightSitedDescriptorsUseDefinitionFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        try {
            level.setBlock(
                origin,
                Blocks.END_ROD.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP),
                3
            );
            CaptureResult.Captured captured = capture(level, origin, pitchUp);
            try (SnapshotLease lease = captured.lease()) {
                assertFacing(helper, lease.view(), pointOf(origin), "north", "pitch-up descriptor");
            }
            helper.assertTrue(captured.report().isEmpty(), "a representable state reports no SC-MC-0004");
        } finally {
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void unorientedAndIdentityCapturesKeepRawDescriptors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        try {
            level.setBlock(origin, stairs(Direction.EAST), 3);
            CaptureResult.Captured unoriented = capture(level, origin, null);
            try (SnapshotLease lease = unoriented.lease()) {
                assertFacing(helper, lease.view(), pointOf(origin), "east", "unoriented descriptor");
            }
            helper.assertTrue(unoriented.report().isEmpty(), "an unoriented capture reports nothing");

            CaptureResult.Captured identity = capture(level, origin, Orientation.identity);
            try (SnapshotLease lease = identity.lease()) {
                assertFacing(helper, lease.view(), pointOf(origin), "east", "identity descriptor");
            }
            helper.assertTrue(identity.report().isEmpty(), "an identity capture reports nothing");
        } finally {
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void unrepresentableStateKeepsRawDescriptorAndReports(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        try {
            level.setBlock(origin, stairs(Direction.EAST), 3);
            CaptureResult.Captured captured = capture(level, origin, pitchUp);
            try (SnapshotLease lease = captured.lease()) {
                assertFacing(helper, lease.view(), pointOf(origin), "east", "unrepresentable descriptor stays raw");
            }
            helper.assertValueEqual(1, captured.report().size(), "one orientation group");
            CaptureReport.Entry entry = captured.report().entries().get(0);
            CuboidSet cells = Objects.requireNonNull(entry.cells(), "the group region");
            helper.assertValueEqual(AdapterCodes.orientation, entry.code(), "the orientation code");
            helper.assertValueEqual(1L, cells.volume(), "one cell in the group");
            helper.assertTrue(
                cells.contains(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())),
                "the raw cell is in the group"
            );
            helper.assertTrue(
                entry.notes().stream().anyMatch(note -> note.contains("property: facing")),
                "the entry names the property"
            );
            helper.assertTrue(
                entry.notes().stream().anyMatch(note -> note.contains("value: east")),
                "the entry names the raw value"
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
    public void propertyExpectationMatchesAtARotatedSiting(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = structureOrigin(helper);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        Siting siting = Siting.of(point, clockwise);
        SavedClaims ledger = state.claims(level);
        ledger.release(state.owner(), siting);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        try {
            level.setBlock(origin, stairs(Direction.EAST), 3);
            MatchResult result = SettleCommand.settle(source, state, level, propertyBlueprint(), point);
            if (!(result instanceof MatchResult.Formed formed)) {
                helper.fail("the property expectation did not form: " + result + " / " + recording.text());
                return;
            }
            helper.assertValueEqual(1, formed.claims().rows().size(), "one claim row");
            helper.assertTrue(recording.text().contains("formed"), "the settle summary is printed");
            List<ClaimRow> rows = ledger.read(Cuboid.of(point)).rows();
            helper.assertValueEqual(1, rows.size(), "the ledger row is inside the sited bounds");
        } finally {
            ledger.release(state.owner(), siting);
            level.removeBlock(origin, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void childFramesKeepWorldFrameDescriptors(GameTestHelper helper) {
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
                assertFacing(helper, root, point, "north", "the root descriptor is projected");
                WorldView child = root.nested(point);
                if (child == null) {
                    helper.fail("the child frame is missing");
                    return;
                }
                assertFacing(helper, child, LatticePoint.origin, "east", "the child descriptor stays world-frame");
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

    private static Blueprint propertyBlueprint() {
        Bay bay = Bay.of(bayId, CuboidSet.of(localBounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> check = Check.of(
            checkId,
            bay,
            Focuses.occupant(localOrigin),
            Expectations.allOf(Expectations.block(stairs), Expectations.property("facing", "north"))
        );
        return Blueprint.builder(blueprintId).bounds(localBounds).check(check)
                .anchor(new AnchorSpec(AnchorRoles.primary, "facing", "east"))
                .orientation(OrientationPolicy.builder(AnchorRoles.primary).allow(clockwise).build()).build();
    }

    private static void assertFacing(
        GameTestHelper helper,
        WorldView view,
        LatticePoint cell,
        String expected,
        String message
    ) {
        OccupantSnapshot occupant = view.occupant(cell);
        if (occupant == null) {
            helper.fail("no occupant at " + cell + " for " + message);
            return;
        }
        helper.assertValueEqual(expected, occupant.descriptor().property("facing"), message);
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
