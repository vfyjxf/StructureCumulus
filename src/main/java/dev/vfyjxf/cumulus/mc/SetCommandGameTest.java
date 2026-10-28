package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** {@code /cumulus set check} and {@code /cumulus set settle} over the built-in demo set. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class SetCommandGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void setCheckWritesNothingAndSetSettleWritesTheClaims(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.STONE);
        SavedClaims ledger = state.claims(level);
        Siting rootSiting = Siting.of(point, Orientation.identity);
        Siting wingSiting = Siting.of(LatticePoint.of(point.x() + 1, point.y(), point.z()), Orientation.identity);
        Cuboid covered = Cuboid.of(point.x(), point.y(), point.z(), point.x() + 2, point.y() + 1, point.z() + 1);
        String set = BuiltinDocuments.setDemoId().text();
        String arguments = origin.getX() + " " + origin.getY() + " " + origin.getZ();
        try {
            ledger.release(state.owner(), rootSiting);
            ledger.release(state.owner(), wingSiting);
            long revision = ledger.revision();
            RecordingSource recording = new RecordingSource();
            CommandSourceStack source = stack(helper, origin, recording);

            helper.assertValueEqual(
                1,
                execute(helper, source, "cumulus set check " + set + " " + arguments),
                "the set check result"
            );
            String text = recording.text();
            helper.assertTrue(text.contains("set " + set + " at "), "the set id and origin: " + text);
            helper.assertTrue(
                text.contains("root " + BuiltinDocuments.setRootId().text() + ": admitted"),
                "the root outcome: " + text
            );
            helper.assertTrue(
                text.contains("satellite " + BuiltinDocuments.leftWingId().text() + ": admitted"),
                "the satellite outcome: " + text
            );
            helper.assertTrue(text.contains("settled: none"), "a check settles nothing: " + text);
            helper.assertTrue(text.contains("first failure: none"), "a clean check has no failure: " + text);
            helper.assertValueEqual(revision, ledger.revision(), "a set check moves no ledger revision");
            helper.assertValueEqual(0, ledger.read(covered).rows().size(), "a set check writes no row");

            recording.clear();
            helper.assertValueEqual(
                1,
                execute(helper, source, "cumulus set settle " + set + " " + arguments),
                "the set settle result"
            );
            text = recording.text();
            helper.assertTrue(text.contains("set " + set + " at "), "the set id and origin: " + text);
            helper.assertTrue(
                text.contains(
                    "settled: " + BuiltinDocuments.setRootId().text() + ", " + BuiltinDocuments.leftWingId().text()
                ),
                "the settle order: " + text
            );
            helper.assertTrue(
                text.contains(BuiltinDocuments.setRootId().text() + "=1 row(s)"),
                "the root row count: " + text
            );
            helper.assertTrue(
                text.contains(BuiltinDocuments.leftWingId().text() + "=1 row(s)"),
                "the satellite row count: " + text
            );
            helper.assertTrue(text.contains("first failure: none"), "a clean settle has no failure: " + text);
            helper.assertValueEqual(2, ledger.read(covered).rows().size(), "the settled rows");
            helper.assertValueEqual(revision + 2, ledger.revision(), "each settled structure bumps the revision once");
        } finally {
            ledger.release(state.owner(), rootSiting);
            ledger.release(state.owner(), wingSiting);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aMissingRequiredSatelliteFailsCheckAndSettle(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.AIR);
        SavedClaims ledger = state.claims(level);
        Siting rootSiting = Siting.of(point, Orientation.identity);
        Siting wingSiting = Siting.of(LatticePoint.of(point.x() + 1, point.y(), point.z()), Orientation.identity);
        Cuboid covered = Cuboid.of(point.x(), point.y(), point.z(), point.x() + 2, point.y() + 1, point.z() + 1);
        String set = BuiltinDocuments.setDemoId().text();
        String arguments = origin.getX() + " " + origin.getY() + " " + origin.getZ();
        try {
            ledger.release(state.owner(), rootSiting);
            ledger.release(state.owner(), wingSiting);
            long revision = ledger.revision();
            RecordingSource recording = new RecordingSource();
            CommandSourceStack source = stack(helper, origin, recording);

            helper.assertValueEqual(
                0,
                execute(helper, source, "cumulus set check " + set + " " + arguments),
                "an absent required satellite fails the set check"
            );
            String text = recording.text();
            helper.assertTrue(text.contains("SC-SET-0001"), "the absent satellite code: " + text);
            helper.assertTrue(text.contains("first failure: SC-SET-0001"), "the first failure: " + text);
            helper.assertTrue(
                text.contains("satellite " + BuiltinDocuments.leftWingId().text() + ": not admitted"),
                "the satellite outcome: " + text
            );
            helper.assertValueEqual(revision, ledger.revision(), "a failed set check moves no revision");
            helper.assertValueEqual(0, ledger.read(covered).rows().size(), "a failed set check writes no row");

            recording.clear();
            helper.assertValueEqual(
                0,
                execute(helper, source, "cumulus set settle " + set + " " + arguments),
                "an absent required satellite fails the set settle"
            );
            text = recording.text();
            helper.assertTrue(text.contains("SC-SET-0001"), "the absent satellite code: " + text);
            helper.assertTrue(text.contains("settled: none"), "nothing settled: " + text);
            helper.assertValueEqual(revision, ledger.revision(), "a failed set settle moves no revision");
            helper.assertValueEqual(0, ledger.read(covered).rows().size(), "a failed set settle writes no row");
        } finally {
            ledger.release(state.owner(), rootSiting);
            ledger.release(state.owner(), wingSiting);
        }
        helper.succeed();
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

}
