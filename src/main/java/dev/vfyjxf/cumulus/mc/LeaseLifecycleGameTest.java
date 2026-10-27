package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;

/** Lease lifecycle: a new capture, a capture close or a level close revokes the leases that capture handed out. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class LeaseLifecycleGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void newCaptureInvalidatesTheOlderLease(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        Cuboid bounds = cell(helper);
        CaptureResult.Captured first = captured(capture, bounds);
        helper.assertTrue(first.lease().isCurrent(), "the first lease is current before a second capture");
        helper.assertValueEqual(1L, capture.currentRevision(), "the first capture stamps revision 1");
        helper.assertValueEqual(1L, first.lease().id().revision(), "the first snapshot revision");

        CaptureResult.Captured second = captured(capture, bounds);
        helper.assertFalse(first.lease().isCurrent(), "a new capture invalidates the older lease");
        helper.assertTrue(second.lease().isCurrent(), "the newest lease is current");
        helper.assertValueEqual(2L, capture.currentRevision(), "the second capture stamps revision 2");

        first.lease().close();
        helper.assertTrue(second.lease().isCurrent(), "closing the old handle leaves the newest lease current");
        second.lease().close();
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void failedCaptureKeepsThePreviousLeaseCurrent(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        Cuboid bounds = cell(helper);
        CaptureResult.Captured first = captured(capture, bounds);

        SnapshotPolicy tiny = new SnapshotPolicy(0, 0, Set.of(), Set.of(), List.of());
        CaptureResult tooLarge = capture.capture(new CaptureRequest(bounds, tiny));
        if (!(tooLarge instanceof CaptureResult.TooLarge)) {
            throw new GameTestAssertException("the tiny ceiling did not reject the capture: " + tooLarge);
        }
        helper.assertTrue(first.lease().isCurrent(), "a rejected capture leaves the previous lease current");
        helper.assertValueEqual(1L, capture.currentRevision(), "a rejected capture stamps no revision");

        LatticePoint far = LatticePoint.of(30_000_000, 64, 30_000_000);
        Cuboid unloaded = Cuboid.of(far.x(), far.y(), far.z(), far.x() + 1, far.y() + 1, far.z() + 1);
        CaptureResult unavailable = capture.capture(new CaptureRequest(unloaded, SnapshotPolicy.minimal()));
        if (!(unavailable instanceof CaptureResult.Unavailable result)) {
            throw new GameTestAssertException("the far capture did not report an unavailable root: " + unavailable);
        }
        helper.assertValueEqual(ReadStatus.notCaptured, result.status(), "the far root chunk is not loaded");
        helper.assertTrue(first.lease().isCurrent(), "an unavailable capture leaves the previous lease current");
        helper.assertValueEqual(1L, capture.currentRevision(), "an unavailable capture stamps no revision");

        first.lease().close();
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void closingCaptureInvalidatesEveryLease(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        Cuboid bounds = cell(helper);
        CaptureResult.Captured first = captured(capture, bounds);
        CaptureResult.Captured second = captured(capture, bounds);

        capture.close();
        helper.assertFalse(first.lease().isCurrent(), "close invalidates the older lease");
        helper.assertFalse(second.lease().isCurrent(), "close invalidates the newest lease");

        capture.close();
        boolean rejected = false;
        try {
            capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "a closed capture rejects a new capture");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void unloadReleasesOnlyTheManagedCapture(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        Cuboid bounds = cell(helper);
        LevelCapture managed = state.capture(level);
        CaptureResult.Captured managedLease = captured(managed, bounds);
        LevelCapture standalone = new LevelCapture(level);
        try {
            CaptureResult.Captured standaloneLease = captured(standalone, bounds);

            ServerState.unload(new LevelEvent.Unload(level));
            helper.assertFalse(managedLease.lease().isCurrent(), "unload closes the managed capture's leases");
            helper.assertTrue(standaloneLease.lease().isCurrent(), "unload does not touch another capture");
            helper.assertTrue(state.capture(level) != managed, "unload drops the cached capture");

            LevelCapture beforeClose = state.capture(level);
            CaptureResult.Captured closeLease = captured(beforeClose, bounds);
            state.closeCaptures();
            helper.assertFalse(closeLease.lease().isCurrent(), "the stop loop closes every managed lease");
            helper.assertTrue(state.capture(level) != beforeClose, "the stop loop drops every cached capture");

            CaptureResult.Captured afterClose = captured(state.capture(level), bounds);
            helper.assertTrue(afterClose.lease().isCurrent(), "a capture after the close is current");
            afterClose.lease().close();
            standaloneLease.lease().close();
        } finally {
            standalone.close();
        }
        helper.succeed();
    }

    private static Cuboid cell(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return Cuboid.of(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + 1,
            origin.getY() + 1,
            origin.getZ() + 1
        );
    }

    private static CaptureResult.Captured captured(LevelCapture capture, Cuboid bounds) {
        CaptureResult result = capture.capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (result instanceof CaptureResult.Captured captured) {
            return captured;
        }
        throw new GameTestAssertException("capture did not succeed: " + result);
    }
}
