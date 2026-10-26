package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.FindingRenderer;
import dev.vfyjxf.cumulus.kernel.finding.RenderContext;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.jspecify.annotations.NullMarked;

/** M1 GameTests: capture, then the first check, through the real server level. */
@NullMarked
@GameTestHolder(Constants.modId)
public final class CaptureGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void capturesTheTemplateCell(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        CaptureResult result;
        try {
            LevelCapture capture = new LevelCapture(helper.getLevel());
            result = capture.capture(
                new CaptureRequest(
                    Cuboid.of(
                        origin.getX(),
                        origin.getY(),
                        origin.getZ(),
                        origin.getX() + 1,
                        origin.getY() + 1,
                        origin.getZ() + 1
                    ),
                    SnapshotPolicy.minimal()
                )
            );
        } catch (Throwable failure) {
            helper.fail("capture threw: " + failure + " / " + failure.getCause());
            return;
        }
        if (!(result instanceof CaptureResult.Captured captured)) {
            helper.fail("capture did not succeed: " + result);
            return;
        }
        OccupantSnapshot cell = captured.view().occupant(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ()));
        if (cell == null) {
            helper.fail("captured cell is unreadable");
            return;
        }
        helper.assertValueEqual(
            ReadStatus.readable,
            captured.view().statusOf(LatticePoint.of(origin.getX(), origin.getY(), origin.getZ())),
            "cell status"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void checksTheBuiltinDocument(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        Document document = state.kernel().document(BuiltinDocuments.airCellId());
        if (document == null) {
            helper.fail("built-in document is missing");
            return;
        }
        CompileResult compiled = state.kernel().compile(document);
        Blueprint blueprint = compiled.blueprint();
        if (blueprint == null) {
            helper.fail(
                "built-in document does not compile: "
                        + FindingRenderer.human(compiled.findings(), RenderContext.plain())
            );
            return;
        }
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        Cuboid bounds = blueprint.bounds().translate(origin.getX(), origin.getY(), origin.getZ());
        if (bounds == null) {
            helper.fail("the blueprint bounds leave the world");
            return;
        }
        CaptureResult capture = state.capture(helper.getLevel())
                .capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (!(capture instanceof CaptureResult.Captured captured)) {
            helper.fail("capture did not succeed: " + capture);
            return;
        }
        try (SnapshotLease lease = captured.lease()) {
            MatchResult result = state.kernel().check(
                MatchRequest.of(
                    blueprint,
                    Siting.of(point, Orientation.identity),
                    lease,
                    state.instance(helper.getLevel()),
                    state.owner(),
                    Budget.unbounded(),
                    null
                )
            );
            if (result instanceof MatchResult.Failed failed) {
                helper.fail("check failed: " + FindingRenderer.human(failed.findings(), RenderContext.plain()));
                return;
            }
            if (!(result instanceof MatchResult.Admitted)) {
                helper.fail("check did not admit: " + result);
                return;
            }
        }
        helper.succeed();
    }
}
