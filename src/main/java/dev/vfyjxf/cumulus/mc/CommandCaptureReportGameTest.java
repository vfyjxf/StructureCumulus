package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** The command capture block: failure first, then findings; an empty report prints nothing. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class CommandCaptureReportGameTest {

    private static final NamespacedId blueprintId = NamespacedId.of(Constants.modId, "report_over_cap");
    private static final NamespacedId bayId = NamespacedId.of(Constants.modId, "report_over_cap_bay");
    private static final NamespacedId checkId = NamespacedId.of(Constants.modId, "report_over_cap_check");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void anOverCapCheckPrintsTheFailureThenTheReport(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        MatchResult result = CumulusCommand.check(source, state, level, overCapBlueprint(), point);
        if (result != null) {
            helper.fail("the over-cap attempt reached a result: " + result);
            return;
        }
        helper.assertTrue(
            state.kernel().catalogs().codes().get(NamespacedId.of("structurecumulus", "sc-mc-0005")) != null,
            "the host catalog registers the over-cap code"
        );
        helper.assertTrue(state.kernel().explain(AdapterCodes.overCap) != null, "the host code is explainable");
        String text = recording.text();
        int failure = text.indexOf("capture " + blueprintId.text() + ": too large");
        int block = text.indexOf("capture " + blueprintId.text() + ": findings");
        int code = text.indexOf("SC-MC-0005");
        helper.assertTrue(failure >= 0, "the failure line is printed: " + text);
        helper.assertTrue(block > failure, "the report block follows the failure line: " + text);
        helper.assertTrue(code > block, "the 0005 finding follows the header: " + text);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aSyntheticCutPrintsAndAnEmptyReportStaysSilent(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        CuboidSet cells = CuboidSet.of(
            Cuboid.of(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                origin.getX() + 1,
                origin.getY() + 1,
                origin.getZ() + 1
            )
        );
        CaptureReport report = CaptureReport.of(
            List.of(
                new CaptureReport.Entry(
                    AdapterCodes.cut,
                    "a nested extent was cut: depth",
                    List.of("reason: depth", "frame: 0"),
                    cells
                )
            )
        );
        CommandSupport.printCaptureFindings(source, blueprintId, report, state.kernel());
        String text = recording.text();
        helper.assertTrue(text.contains("capture " + blueprintId.text() + ": findings"), "the report header");
        helper.assertTrue(text.contains("SC-MC-0003"), "the cut code is relayed: " + text);
        helper.assertTrue(text.contains("reason: depth"), "the note is copied: " + text);

        recording.clear();
        CommandSupport.printCaptureFindings(source, blueprintId, CaptureReport.empty(), state.kernel());
        helper.assertTrue(recording.text().isEmpty(), "an empty report prints nothing");
        helper.succeed();
    }

    private static Blueprint overCapBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 200, 200, 200);
        Cuboid cell = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(bayId, CuboidSet.of(cell), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(bounds)
                .check(Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant())))
                .build();
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

        private void clear() {
            messages.clear();
        }
    }
}
