package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.finding.Explanation;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/** The explain subcommand: the registered long form of one finding code. */
@NullMarked
final class ExplainCommand {

    static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String text = StringArgumentType.getString(context, "code");
        FindingCode code = FindingCode.parse(text);
        if (code == null) {
            source.sendFailure(Component.literal("malformed code " + text));
            return 0;
        }
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        StructureKernel kernel = state.kernel();
        Explanation explanation = kernel.explain(code);
        if (explanation == null) {
            source.sendFailure(Component.literal("no explanation for " + code.text()));
            print(source, kernel.explainOrFallback(code));
            return 0;
        }
        print(source, explanation);
        return 1;
    }

    private static void print(CommandSourceStack source, Explanation explanation) {
        source.sendSuccess(() -> Component.literal(explanation.code().text() + ": " + explanation.summary()), false);
        source.sendSuccess(() -> Component.literal(explanation.body()), false);
        if (!explanation.seeAlso().isEmpty()) {
            source.sendSuccess(() -> Component.literal("see also: " + join(explanation)), false);
        }
    }

    private static String join(Explanation explanation) {
        StringBuilder topics = new StringBuilder();
        for (var topic : explanation.seeAlso()) {
            if (!topics.isEmpty()) {
                topics.append(", ");
            }
            topics.append(topic.text());
        }
        return topics.toString();
    }

    private ExplainCommand() {}
}
