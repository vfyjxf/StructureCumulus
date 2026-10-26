package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.jspecify.annotations.NullMarked;

/** M1 GameTest: capture the empty template through the real server level. */
@NullMarked
@GameTestHolder(Constants.modId)
public final class CaptureGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void capturesTheTemplateCell(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        CaptureResult result;
        try {
            LevelCapture capture = new LevelCapture(helper.getLevel());
            result = capture.capture(new CaptureRequest(
                    Cuboid.of(origin.getX(), origin.getY(), origin.getZ(),
                            origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1),
                    SnapshotPolicy.minimal()));
        } catch (Throwable failure) {
            helper.fail("capture threw: " + failure + " / " + failure.getCause());
            return;
        }
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("capture did not succeed: " + result);
            return;
        }
        OccupantSnapshot cell = captured.view().occupant(
                LatticePoint.of(origin.getX(), origin.getY(), origin.getZ()));
        if (cell == null) {
            helper.fail("captured cell is unreadable");
            return;
        }
        helper.assertValueEqual(ReadStatus.READABLE,
                captured.view().statusOf(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())),
                "cell status");
        helper.succeed();
    }
}
