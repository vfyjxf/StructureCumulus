package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
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

/** The read-only subcommands end to end through the server dispatcher. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class CommandGameTest {

    private static final NamespacedId checkId = NamespacedId.of(Constants.modId, "command_check");
    private static final NamespacedId bayId = NamespacedId.of(Constants.modId, "command_bay");
    private static final NamespacedId blueprintId = NamespacedId.of(Constants.modId, "command_cell");
    private static final Cuboid cell = Cuboid.of(0, 0, 0, 1, 1, 1);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void compileReportsSuccessAndFailure(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        String document = BuiltinDocuments.airCellId().text();
        helper.assertValueEqual(1, execute(helper, source, "cumulus compile " + document), "a compiling document");
        helper.assertTrue(recording.text().contains(": ok ("), "the compile outcome");
        recording.clear();
        helper.assertValueEqual(
            0,
            execute(helper, source, "cumulus compile " + Constants.modId + ":missing"),
            "a missing document"
        );
        helper.assertTrue(
            recording.text().contains("no document " + Constants.modId + ":missing"),
            "the missing document failure"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void explainReportsKnownAndUnknownCodes(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        helper.assertValueEqual(1, execute(helper, source, "cumulus explain SC-ENG-0002"), "a registered code");
        helper.assertTrue(recording.text().contains("SC-ENG-0002:"), "the explanation summary");
        recording.clear();
        helper.assertValueEqual(0, execute(helper, source, "cumulus explain SC-ENG-9999"), "an unknown code");
        helper.assertTrue(recording.text().contains("no explanation for SC-ENG-9999"), "the unknown code failure");
        recording.clear();
        helper.assertValueEqual(0, execute(helper, source, "cumulus explain nope"), "a malformed code");
        helper.assertTrue(recording.text().contains("malformed code nope"), "the malformed code failure");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void claimsReportsEmptyThenSettledRows(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        Siting siting = Siting.of(point, Orientation.identity);
        ClaimLedger ledger = state.claims(level);
        release(ledger, state, level, siting);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        String claims = "cumulus claims " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + " "
                + origin.getX() + " " + origin.getY() + " " + origin.getZ();
        try {
            helper.assertValueEqual(1, execute(helper, source, claims), "an empty claims read");
            helper.assertTrue(recording.text().contains("0 row(s)"), "the empty claim count");
            recording.clear();

            MatchResult settled = SettleCommand.settle(source, state, level, claimableBlueprint(), point);
            if (!(settled instanceof MatchResult.Formed)) {
                helper.fail("the settle did not form: " + settled + " / " + recording.text());
                return;
            }
            long revision = ledger.revision();
            recording.clear();

            helper.assertValueEqual(1, execute(helper, source, claims), "a claims read after settle");
            helper.assertTrue(recording.text().contains("1 row(s)"), "the settled claim count");
            helper.assertTrue(recording.text().contains(bayId.text()), "the settled bay is named");
            helper.assertValueEqual(revision, ledger.revision(), "claims reads the ledger without writing");
        } finally {
            release(ledger, state, level, siting);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void checkStillWorks(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        String document = BuiltinDocuments.airCellId().text();
        helper.assertValueEqual(
            1,
            execute(
                helper,
                source,
                "cumulus check " + document + " " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
            ),
            "the check command"
        );
        helper.assertTrue(recording.text().contains("admitted"), "the check admission");
        helper.succeed();
    }

    private static Blueprint claimableBlueprint() {
        CuboidSet cells = CuboidSet.of(cell);
        Bay bay = Bay.of(bayId, cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(cell)
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

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException failure) {
            helper.fail("the command did not parse: " + failure.getMessage());
            return 0;
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

        private void clear() {
            messages.clear();
        }
    }

    private static void release(ClaimLedger ledger, ServerState state, ServerLevel level, Siting siting) {
        StructureInstance instance = state.structures(level).find(state.world(level), state.owner(), siting);
        if (instance != null) {
            ledger.release(instance.id());
        }
    }
}
