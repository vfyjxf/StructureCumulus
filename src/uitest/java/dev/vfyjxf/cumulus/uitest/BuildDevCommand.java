package dev.vfyjxf.cumulus.uitest;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The dev-only driver of the production build commands: {@code /cumulus build} and {@code
 * /cumulus build undo} need a player, which a console command source is not, so the harness dispatches
 * them on behalf of the one online player.
 */
@NullMarked
final class BuildDevCommand {

    /** The last dispatch's command line, readable by the scenarios because the output is suppressed. */
    static volatile String lastResult = "not run";

    private BuildDevCommand() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cumulusbuild")
                    .then(
                        Commands.literal("run").then(
                            Commands.argument("id", ResourceLocationArgument.id()).then(
                                Commands.argument("origin", BlockPosArgument.blockPos())
                                        .executes(context -> run(context, false))
                            )
                        )
                    ).then(Commands.literal("undo").executes(context -> run(context, true)))
        );
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean undo) {
        CommandSourceStack source = context.getSource();
        List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
        if (players.size() != 1) {
            lastResult = "refused: expected exactly one player online, found " + players.size();
            source.sendFailure(Component.literal(lastResult));
            return 0;
        }
        String command = "cumulus build undo";
        if (!undo) {
            BlockPos origin = BlockPosArgument.getBlockPos(context, "origin");
            var id = ResourceLocationArgument.getId(context, "id");
            command = "cumulus build " + id.getNamespace() + ":" + id.getPath() + " " + origin.getX() + " "
                    + origin.getY() + " " + origin.getZ();
        }
        lastResult = command;
        source.getServer().getCommands()
                .performPrefixedCommand(source.withEntity(players.get(0)).withSuppressedOutput(), command);
        return 1;
    }
}
