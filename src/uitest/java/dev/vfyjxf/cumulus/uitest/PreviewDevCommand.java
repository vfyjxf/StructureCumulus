package dev.vfyjxf.cumulus.uitest;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.PreviewService;
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
 * The dev-only twin of {@code /cumulus preview}: the harness drives the production service without
 * owning a node on the production command surface, whose owner attaches the real node.
 */
@NullMarked
final class PreviewDevCommand {

    /** The last dev-open outcome, readable by the scenarios because the command output is suppressed. */
    static volatile String lastResult = "not run";

    private PreviewDevCommand() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cumuluspreview").then(
                Commands.argument("id", ResourceLocationArgument.id()).executes(PreviewDevCommand::run)
                        .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(PreviewDevCommand::run))
            )
        );
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        var location = ResourceLocationArgument.getId(context, "id");
        NamespacedId id = NamespacedId.of(location.getNamespace(), location.getPath());
        LatticePoint origin = null;
        if (context.getNodes().stream().anyMatch(node -> "origin".equals(node.getNode().getName()))) {
            BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
            if (!LatticePoint.inLattice(pos.getX(), pos.getY(), pos.getZ())) {
                context.getSource().sendFailure(Component.literal("origin outside the lattice"));
                return 0;
            }
            origin = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        }
        CommandSourceStack source = context.getSource();
        List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
        if (players.size() != 1) {
            lastResult = "refused: expected exactly one player online, found " + players.size();
            source.sendFailure(Component.literal(lastResult));
            return 0;
        }
        int result = PreviewService.open(source.withEntity(players.get(0)), id, origin);
        lastResult = result == 1 ? "opened " + id.text() : "refused " + id.text();
        return result;
    }
}
