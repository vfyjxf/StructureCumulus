package dev.vfyjxf.cumulus.mc.preview;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The {@code preview} node. The command owner attaches {@link #literal()} to {@code /cumulus}; the
 * preview package registers nothing itself, so the command surface keeps one owner.
 */
public final class PreviewCommand {

    private PreviewCommand() {}

    private static boolean hasOrigin(CommandContext<CommandSourceStack> context) {
        return context.getNodes().stream().anyMatch(node -> "origin".equals(node.getNode().getName()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.literal("preview").then(
            Commands.argument("id", ResourceLocationArgument.id()).executes(PreviewCommand::run)
                    .then(Commands.argument("origin", BlockPosArgument.blockPos()).executes(PreviewCommand::run))
        );
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        try {
            var location = ResourceLocationArgument.getId(context, "id");
            NamespacedId id = NamespacedId.of(location.getNamespace(), location.getPath());
            LatticePoint origin = null;
            if (hasOrigin(context)) {
                BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
                if (!LatticePoint.inLattice(pos.getX(), pos.getY(), pos.getZ())) {
                    context.getSource().sendFailure(Component.literal("the origin is outside the lattice"));
                    return 0;
                }
                origin = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
            }
            return PreviewService.open(context.getSource(), id, origin);
        } catch (RuntimeException | StackOverflowError | LinkageError failure) {
            StructureCumulus.logger.error("/cumulus preview failed", failure);
            String detail = failure.getMessage() == null ? "" : ": " + failure.getMessage();
            context.getSource().sendFailure(
                Component.literal("cumulus preview failed: " + failure.getClass().getSimpleName() + detail)
            );
            return 0;
        }
    }
}
