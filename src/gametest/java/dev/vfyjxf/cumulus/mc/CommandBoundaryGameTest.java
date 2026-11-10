package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The command boundary: the root permission gate, the verb guard and the draft recovery. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class CommandBoundaryGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aThrowingOutputStillAbortsTheDraft(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        LatticePoint origin = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        CommandSourceStack source = stack(helper, pos, 4, new RecordingSource("check "));
        Blueprint blueprint = CommandSupport.blueprint(state.kernel(), source, BuiltinDocuments.airCellId());
        if (blueprint == null) {
            helper.fail("the air cell document did not compile");
            return;
        }
        try {
            CumulusCommand.check(source, state, level, blueprint, origin);
            helper.fail("the throwing output must fail the check");
        } catch (IllegalStateException expected) {
            helper.assertTrue(true, "the throwing output fails the check");
        }
        Siting siting = Siting.of(origin, Orientation.identity);
        StructureAttempt again = state.structures(level)
                .begin(state.world(level), new StructureSpec(state.owner(), siting, blueprint.id()));
        try {
            helper.assertTrue(again.isOpen(), "the aborted draft frees the siting for a new attempt");
        } finally {
            again.abort();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aSubcommandFailureIsReportedAndExitsZero(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = stack(helper, origin, 4, recording);
        helper.assertValueEqual(
            0,
            execute(helper, source, "cumulus compile " + Constants.modId + ":a//b"),
            "a guarded failure exits zero"
        );
        helper.assertTrue(
            recording.text().startsWith("cumulus compile failed: IllegalArgumentException"),
            "the guard reports the failure"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aLinkageErrorIsReportedAndExitsZero(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LinkageSource recording = new LinkageSource();
        CommandSourceStack source = stack(helper, origin, 4, recording);
        helper.assertValueEqual(
            0,
            execute(helper, source, "cumulus explain SC-ENG-0002"),
            "a guarded LinkageError exits zero"
        );
        helper.assertTrue(
            recording.text().startsWith("cumulus explain failed: NoClassDefFoundError"),
            "the guard reports the LinkageError"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void onlyAGamemasterSourceCanUseCumulus(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        CommandSourceStack player = stack(helper, origin, 0, new RecordingSource());
        CommandSourceStack gamemaster = stack(helper, origin, 2, new RecordingSource());
        CommandNode<CommandSourceStack> cumulus = dispatcher(helper).getRoot().getChild("cumulus");
        helper.assertValueEqual(false, cumulus.canUse(player), "permission 0 is denied");
        helper.assertValueEqual(true, cumulus.canUse(gamemaster), "permission 2 is allowed");
        try {
            dispatcher(helper).execute("cumulus explain SC-ENG-0002", player);
            helper.fail("permission 0 must be refused");
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(true, "the gate hides the verb from a low-permission source");
        }
        helper.succeed();
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return dispatcher(helper).execute(command, source);
        } catch (CommandSyntaxException failure) {
            helper.fail("the command did not parse: " + failure.getMessage());
            return 0;
        }
    }

    private static CommandSourceStack stack(
        GameTestHelper helper,
        BlockPos origin,
        int permission,
        CommandSource source
    ) {
        return new CommandSourceStack(
            source,
            Vec3.atLowerCornerOf(origin),
            Vec2.ZERO,
            helper.getLevel(),
            permission,
            "gametest",
            Component.literal("gametest"),
            helper.getLevel().getServer(),
            null
        );
    }

    private static final class RecordingSource implements CommandSource {

        private final List<String> messages = new ArrayList<>();
        private final @Nullable String throwingPrefix;

        private RecordingSource() {
            this(null);
        }

        private RecordingSource(@Nullable String throwingPrefix) {
            this.throwingPrefix = throwingPrefix;
        }

        @Override
        public void sendSystemMessage(Component message) {
            String text = message.getString();
            if (throwingPrefix != null && text.startsWith(throwingPrefix)) {
                throw new IllegalStateException("the output threw on " + text);
            }
            messages.add(text);
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

    /** Throws once from the first output, then records, so the guard's own report survives. */
    private static final class LinkageSource implements CommandSource {

        private final List<String> messages = new ArrayList<>();
        private boolean thrown = false;

        @Override
        public void sendSystemMessage(Component message) {
            if (!thrown) {
                thrown = true;
                throw new NoClassDefFoundError("the optional mod is absent");
            }
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
