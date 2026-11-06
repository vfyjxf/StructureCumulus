package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
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

/** The settle command: the built-in document end to end and a claimable Java-built blueprint. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class SettleCommandGameTest {

    private static final NamespacedId checkId = NamespacedId.of(Constants.modId, "gametest_check");
    private static final NamespacedId bayId = NamespacedId.of(Constants.modId, "gametest_bay");
    private static final NamespacedId blueprintId = NamespacedId.of(Constants.modId, "gametest_cell");
    private static final NamespacedId stoneCheckId = NamespacedId.of(Constants.modId, "gametest_stone_check");
    private static final NamespacedId stoneBayId = NamespacedId.of(Constants.modId, "gametest_stone_bay");
    private static final NamespacedId stoneBlueprintId = NamespacedId.of(Constants.modId, "gametest_stone");
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final Cuboid cell = Cuboid.of(0, 0, 0, 1, 1, 1);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settlesTheBuiltinDocumentFromTheServerDispatcher(GameTestHelper helper) {
        if (ServerState.current() == null) {
            helper.fail("server state is not open");
            return;
        }
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, recording);
        String command = "cumulus settle " + BuiltinDocuments.airCellId().text() + " " + origin.getX() + " "
                + origin.getY() + " " + origin.getZ();
        helper.assertValueEqual(1, execute(helper, source, command), "the settle command result");
        helper.assertTrue(recording.text().contains("formed"), "the settle command reported a formed result");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settlesAJavaBuiltBlueprintAndKeepsOneRowPerSiting(GameTestHelper helper) {
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
        try {
            MatchResult first = SettleCommand.settle(source, state, level, cellBlueprint(), point);
            if (!(first instanceof MatchResult.Formed formed)) {
                helper.fail("the first settle did not form: " + first + " / " + recording.text());
                return;
            }
            helper.assertValueEqual(1, formed.claims().rows().size(), "the settled rows");
            ClaimView held = ledger.read(Cuboid.of(point));
            helper.assertValueEqual(1, held.rows().size(), "the claim row is visible");
            ClaimRow row = held.rows().get(0);
            StructureInstance formedInstance = state.structures(level).find(state.world(level), state.owner(), siting);
            helper.assertTrue(
                formedInstance != null && formedInstance.id().equals(row.instance()),
                "the row belongs to the formed instance"
            );
            helper.assertValueEqual(point, row.siting().origin(), "the siting origin");
            helper.assertValueEqual(bayId, row.bay(), "the row bay");
            helper.assertValueEqual(Sharing.exclusive, row.sharing(), "the row sharing");
            long revision = held.revision();

            MatchResult second = SettleCommand.settle(source, state, level, cellBlueprint(), point);
            if (!(second instanceof MatchResult.Formed replaced)) {
                helper.fail("the second settle did not form: " + second + " / " + recording.text());
                return;
            }
            helper.assertValueEqual(1, replaced.claims().rows().size(), "a re-settle leaves one row");
            ClaimView after = ledger.read(Cuboid.of(point));
            helper.assertValueEqual(1, after.rows().size(), "the claim row is not duplicated");
            helper.assertValueEqual(revision + 1, after.revision(), "the re-settle bumps the revision once");

            MatchResult rejected = SettleCommand.settle(source, state, level, stoneBlueprint(), point);
            if (!(rejected instanceof MatchResult.Failed)) {
                helper.fail("a mismatching check must not settle: " + rejected + " / " + recording.text());
                return;
            }
            ClaimView untouched = ledger.read(Cuboid.of(point));
            helper.assertValueEqual(1, untouched.rows().size(), "a failed check writes no row");
            helper.assertValueEqual(revision + 1, untouched.revision(), "a failed check does not move the revision");
        } finally {
            release(ledger, state, level, siting);
        }
        helper.succeed();
    }

    private static Blueprint cellBlueprint() {
        CuboidSet cells = CuboidSet.of(cell);
        Bay bay = Bay.of(bayId, cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(blueprintId).bounds(cell)
                .check(Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant())))
                .build();
    }

    private static Blueprint stoneBlueprint() {
        CuboidSet cells = CuboidSet.of(cell);
        Bay bay = Bay.of(stoneBayId, cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(stoneBlueprintId).bounds(cell)
                .check(Check.of(stoneCheckId, bay, Focuses.bay(), Expectations.eachCell(Expectations.block(stone))))
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
    }

    private static void release(ClaimLedger ledger, ServerState state, ServerLevel level, Siting siting) {
        StructureInstance instance = state.structures(level).find(state.world(level), state.owner(), siting);
        if (instance != null) {
            ledger.release(instance.id());
        }
    }
}
