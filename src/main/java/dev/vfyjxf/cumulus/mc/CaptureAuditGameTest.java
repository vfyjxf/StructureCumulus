package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** The sited capture audits root-frame block states that cannot express the siting orientation. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class CaptureAuditGameTest {

    private static final Orientation pitchUp = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pitchUpAuditGroupsUnrepresentableStates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos((base.getX() & ~15) + 8, base.getY(), (base.getZ() & ~15) + 8);
        BlockPos second = origin.east();
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        level.setBlock(origin, stairs, 3);
        level.setBlock(second, stairs, 3);
        Cuboid bounds = Cuboid.of(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + 2,
            origin.getY() + 1,
            origin.getZ() + 1
        );
        try {
            CaptureResult audited = new LevelCapture(level)
                    .capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()), pitchUp);
            if (!(audited instanceof CaptureResult.Captured captured)) {
                helper.fail("the audited capture failed: " + audited);
                return;
            }
            try (SnapshotLease lease = captured.lease()) {
                helper.assertTrue(lease.isCurrent(), "the audited capture is leased");
            }
            helper.assertValueEqual(1, captured.report().size(), "one group for the same type/property/value");
            var entry = captured.report().entries().get(0);
            var cells = Objects.requireNonNull(entry.cells(), "the group region");
            helper.assertValueEqual(AdapterCodes.orientation, entry.code(), "the orientation code");
            helper.assertValueEqual(2L, cells.volume(), "the group unions both cells");
            helper.assertTrue(
                cells.contains(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())),
                "the first cell is in the group"
            );
            helper.assertTrue(
                cells.contains(LatticePoint.of(second.getX(), second.getY(), second.getZ())),
                "the second cell is in the group"
            );
            helper.assertTrue(
                entry.notes().stream().anyMatch(note -> note.contains("property: facing")),
                "the entry names the property"
            );
            helper.assertTrue(
                entry.notes().stream().anyMatch(note -> note.contains("orientation:")),
                "the entry names the orientation"
            );

            CaptureResult upright = new LevelCapture(level)
                    .capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()), Orientation.identity);
            if (!(upright instanceof CaptureResult.Captured plain)) {
                helper.fail("the upright capture failed: " + upright);
                return;
            }
            try (SnapshotLease lease = plain.lease()) {
                helper.assertTrue(lease.isCurrent(), "the upright capture is leased");
            }
            helper.assertTrue(plain.report().isEmpty(), "an upright siting is not audited");
        } finally {
            level.removeBlock(origin, false);
            level.removeBlock(second, false);
        }
        helper.succeed();
    }
}
