package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import dev.vfyjxf.cumulus.Constants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** The command boundary: the root permission gate, the verb guard and the draft recovery. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class CommandBoundaryGameTest {

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
    }
}
