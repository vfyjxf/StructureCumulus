package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

/** The compile subcommand: lower the host document to a blueprint and report the outcome. */
@NullMarked
final class CompileCommand {

    static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        NamespacedId id = CommandSupport.id(context, "id");
        ServerLevel level = source.getLevel();
        StructureKernel kernel = state.kernel(level);
        Document document = CommandSupport.document(kernel, source, id);
        if (document == null) {
            return 0;
        }
        CompileResult compiled = kernel.compile(document);
        Blueprint blueprint = compiled.blueprint();
        String header = blueprint == null
                ? "compile " + id.text() + ": failed"
                : "compile " + id.text() + ": ok (" + blueprint.bounds() + ", " + blueprint.bays().size() + " bays, "
                        + blueprint.checks().size() + " checks)";
        CommandSupport.printFindings(source, header, compiled.findings());
        return blueprint == null ? 0 : 1;
    }

    private CompileCommand() {}
}
