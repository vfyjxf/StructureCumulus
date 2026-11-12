package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code /cumulus preview screen <id>}: a client command, so the client tree executes it locally and
 * the server never hears of it. The argument completes from the last catalogue (design 17 §9.2).
 */
final class PreviewClientCommand {

    private PreviewClientCommand() {}

    /** The client's own {@code cumulus} root: no permission, no packet, in any world. */
    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("cumulus").then(
            Commands.literal("preview").then(
                Commands.literal("screen").then(
                    Commands.argument("id", ResourceLocationArgument.id()).suggests(suggestions())
                            .executes(PreviewClientCommand::run)
                )
            )
        );
    }

    /** Every catalogue id, both spellings, in catalogue order: the bare path is the common typing. */
    private static SuggestionProvider<CommandSourceStack> suggestions() {
        return (context, builder) -> {
            Set<String> ids = new LinkedHashSet<>();
            for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
                NamespacedId id = entry.blueprintId();
                ids.add(id.text());
                ids.add(id.path());
            }
            return SharedSuggestionProvider.suggest(ids, builder);
        };
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        ResourceLocation location = ResourceLocationArgument.getId(context, "id");
        NamespacedId id = NamespacedId.of(location.getNamespace(), location.getPath());
        // 1 opened, 0 otherwise: the adapter's command convention, though a client source ignores it
        return PreviewScreen.open(id) ? 1 : 0;
    }
}
