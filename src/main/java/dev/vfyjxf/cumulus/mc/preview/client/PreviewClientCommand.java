package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.client.screen.PreviewScreen;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code /cumulus preview screen <id>}: a client command, so the client tree executes it locally and
 * the server never hears of it. The argument completes from the last catalogue (design 17 §9.2); a
 * set id opens its composite page, and {@code <id>#set} forces the set when a blueprint shares the id
 * (design 18 §7). The qualifier is the parser's, never the wire.
 */
final class PreviewClientCommand {

    /** The parser's set qualifier, exactly as design 18 §7 spells it. */
    static final String setQualifier = "#set";

    private PreviewClientCommand() {}

    /** The client's own {@code cumulus} root: no permission, no packet, in any world. */
    static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("cumulus").then(
            Commands.literal("preview").then(
                Commands.literal("screen").then(
                    Commands.argument("id", StringArgumentType.greedyString()).suggests(suggestions())
                            .executes(PreviewClientCommand::run)
                )
            )
        );
    }

    /** Every catalogue id, both spellings, and each set's qualified form in catalogue order. */
    private static SuggestionProvider<CommandSourceStack> suggestions() {
        return (context, builder) -> {
            Set<String> ids = new LinkedHashSet<>();
            for (PreviewCatalogPayload.Entry entry : PreviewCatalogClient.entries()) {
                add(ids, entry.blueprintId());
            }
            for (PreviewCatalogPayload.SetEntry set : PreviewCatalogClient.sets()) {
                add(ids, set.setId());
                NamespacedId id = set.setId();
                ids.add(id.text() + setQualifier);
                ids.add(id.path() + setQualifier);
            }
            return SharedSuggestionProvider.suggest(ids, builder);
        };
    }

    private static void add(Set<String> ids, NamespacedId id) {
        ids.add(id.text());
        ids.add(id.path());
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "id");
        boolean qualified = raw.endsWith(setQualifier);
        String idText = qualified ? raw.substring(0, raw.length() - setQualifier.length()) : raw;
        NamespacedId id = NamespacedId.parse(idText);
        if (id == null) {
            return 0;
        }
        // 1 opened, 0 otherwise: the adapter's command convention, though a client source ignores it
        boolean opened = qualified ? PreviewScreen.openSet(id) : PreviewScreen.open(id);
        return opened ? 1 : 0;
    }
}
