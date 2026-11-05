package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

/** The claims subcommand: the ledger rows inside one box, in claim order. */
@NullMarked
final class ClaimsCommand {

    static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        Cuboid box = CommandSupport.box(from, to);
        if (box == null) {
            source.sendFailure(
                Component.literal("the box leaves the lattice: " + from.toShortString() + " .. " + to.toShortString())
            );
            return 0;
        }
        ClaimView view = state.claims(source.getLevel()).read(box);
        String header = "claims " + from.toShortString() + " .. " + to.toShortString() + ": " + view.rows().size()
                + " row(s), revision " + view.revision() + (view.available() ? "" : ", unavailable");
        source.sendSuccess(() -> Component.literal(header), false);
        ServerLevel level = source.getLevel();
        for (ClaimRow row : view.rows()) {
            source.sendSuccess(() -> Component.literal(describe(state, level, row)), false);
        }
        return 1;
    }

    private static String describe(ServerState state, ServerLevel level, ClaimRow row) {
        StructureInstance instance = state.structures(level).find(row.instance());
        String owner = instance == null ? "unknown" : instance.owner().id().text();
        return "  " + row.cell().point() + " " + row.sharing() + " " + owner + " " + row.bay().text() + " at "
                + row.siting().origin();
    }

    private ClaimsCommand() {}
}
