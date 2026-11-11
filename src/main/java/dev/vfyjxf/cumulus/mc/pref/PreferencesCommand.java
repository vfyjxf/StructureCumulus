package dev.vfyjxf.cumulus.mc.pref;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /cumulus prefs}: show, set and forget. The command owner attaches {@link #literal()}, and no
 * command writes a choice: the memory fills from confirmations and part pins (7.2).
 */
public final class PreferencesCommand {

    private static final List<String> keys = List.of("orientation", "heading", "removal", "weights", "all");
    private static final int maxShownBlueprints = 8;

    private PreferencesCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.literal("prefs").executes(context -> guarded(context, PreferencesCommand::show))
                .then(Commands.literal("show").executes(context -> guarded(context, PreferencesCommand::show)))
                .then(
                    Commands.literal("set").then(
                        Commands.argument("key", StringArgumentType.word()).then(
                            Commands.argument("value", StringArgumentType.greedyString())
                                    .executes(context -> guarded(context, PreferencesCommand::set))
                        )
                    )
                )
                .then(
                    Commands.literal("forget").executes(context -> guarded(context, PreferencesCommand::forget)).then(
                        Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> guarded(context, PreferencesCommand::forget))
                    )
                );
    }

    private static int guarded(CommandContext<CommandSourceStack> context, Body body) {
        try {
            return body.run(context);
        } catch (RuntimeException | StackOverflowError | LinkageError failure) {
            StructureCumulus.logger.error("/cumulus prefs failed", failure);
            String detail = failure.getMessage() == null ? "" : ": " + failure.getMessage();
            context.getSource().sendFailure(
                Component.literal("cumulus prefs failed: " + failure.getClass().getSimpleName() + detail)
            );
            return 0;
        }
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("preferences belong to a player"));
            return 0;
        }
        PreferencesSavedData store = PreferencesSavedData.get(source.getLevel());
        BuildPreferences preferences = store.preferences(player.getUUID());
        source.sendSuccess(
            () -> Component.literal(
                "prefs " + player.getGameProfile().getName() + ": orientation=" + preferences.orientationMode().key()
                        + " heading="
                        + (preferences.fixedHeading() == null
                                ? "none"
                                : preferences.fixedHeading().name().toLowerCase(Locale.ROOT))
                        + " removal=" + preferences.removalPolicy().key()
            ),
            false
        );
        source.sendSuccess(() -> Component.literal("weights: " + String.join(", ", preferences.weightKeys())), false);
        Map<NamespacedId, ChoiceHistory> remembered = preferences.remembered();
        source.sendSuccess(() -> Component.literal("remembered: " + remembered.size() + " blueprint(s)"), false);
        int shown = 0;
        for (Map.Entry<NamespacedId, ChoiceHistory> history : remembered.entrySet()) {
            if (shown >= maxShownBlueprints) {
                int rest = remembered.size() - shown;
                source.sendSuccess(() -> Component.literal("… " + rest + " more blueprint(s)"), false);
                break;
            }
            shown++;
            Siting preferred = history.getValue().preferred();
            source.sendSuccess(
                () -> Component.literal(
                    "  " + history.getKey().text() + ": " + history.getValue().recent().size() + " choice(s)"
                            + (preferred == null ? "" : " · preferred at " + describe(preferred))
                ),
                false
            );
        }
        Map<NamespacedId, Map<NamespacedId, LatticePoint>> partPins = store.partPins(player.getUUID());
        int pinCount = 0;
        for (Map<NamespacedId, LatticePoint> blueprint : partPins.values()) {
            pinCount += blueprint.size();
        }
        int pins = pinCount;
        source.sendSuccess(() -> Component.literal("part pins: " + pins), false);
        for (Map.Entry<NamespacedId, Map<NamespacedId, LatticePoint>> blueprint : partPins.entrySet()) {
            for (Map.Entry<NamespacedId, LatticePoint> pin : blueprint.getValue().entrySet()) {
                source.sendSuccess(
                    () -> Component.literal(
                        "  " + blueprint.getKey().text() + "/" + pin.getKey().text() + " -> " + describe(pin.getValue())
                    ),
                    false
                );
            }
        }
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("preferences belong to a player"));
            return 0;
        }
        String key = StringArgumentType.getString(context, "key").toLowerCase(Locale.ROOT);
        String value = StringArgumentType.getString(context, "value").trim();
        PreferencesSavedData store = PreferencesSavedData.get(source.getLevel());
        BuildPreferences current = store.preferences(player.getUUID());
        BuildPreferences next = switch (key) {
            case "orientation" -> orientation(current, value);
            case "heading" -> heading(current, value);
            case "removal" -> removal(current, value);
            case "weights" -> weights(current, value);
            case "all", "default" -> PrefConfig.defaults();
            default -> null;
        };
        if (next == null) {
            source.sendFailure(
                Component.literal("prefs set " + key + ": '" + value + "' is not a known value; keys are " + keys)
            );
            return 0;
        }
        store.set(player.getUUID(), next);
        source.sendSuccess(() -> Component.literal("prefs " + key + " = " + value), false);
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("preferences belong to a player"));
            return 0;
        }
        PreferencesSavedData store = PreferencesSavedData.get(source.getLevel());
        NamespacedId blueprint = null;
        if (context.getNodes().stream().anyMatch(node -> "id".equals(node.getNode().getName()))) {
            var location = ResourceLocationArgument.getId(context, "id");
            blueprint = NamespacedId.of(location.getNamespace(), location.getPath());
        }
        int choices = 0;
        int pins;
        if (blueprint == null) {
            for (ChoiceHistory history : store.preferences(player.getUUID()).remembered().values()) {
                choices += history.recent().size();
            }
            pins = store.partPinCount(player.getUUID());
        } else {
            ChoiceHistory history = store.preferences(player.getUUID()).history(blueprint);
            choices = history == null ? 0 : history.recent().size();
            pins = store.partPins(player.getUUID(), blueprint).size();
        }
        store.forget(player.getUUID(), blueprint);
        NamespacedId target = blueprint;
        int forgottenChoices = choices;
        int forgottenPins = pins;
        source.sendSuccess(
            () -> Component.literal(
                "prefs forgot " + (target == null ? "every blueprint" : target.text()) + ": " + forgottenChoices
                        + " choice(s), " + forgottenPins + " part pin(s)"
            ),
            false
        );
        return 1;
    }

    private static @Nullable BuildPreferences orientation(BuildPreferences current, String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
        OrientationMode mode = switch (normalized) {
            case "auto" -> OrientationMode.autoAnchor;
            case "face" -> OrientationMode.facePlayer;
            case "fixed" -> OrientationMode.fixedHeading;
            default -> OrientationMode.parse(normalized);
        };
        return mode == null ? null : current.withOrientationMode(mode);
    }

    private static @Nullable BuildPreferences heading(BuildPreferences current, String value) {
        if (value.equalsIgnoreCase("none")) {
            return current.withFixedHeading(null);
        }
        try {
            return current.withFixedHeading(Heading.valueOf(value.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static @Nullable BuildPreferences removal(BuildPreferences current, String value) {
        RemovalPolicy policy = RemovalPolicy.parse(value);
        return policy == null ? null : current.withRemovalPolicy(policy);
    }

    private static @Nullable BuildPreferences weights(BuildPreferences current, String value) {
        if (value.equalsIgnoreCase("default")) {
            return current.withWeights(PrefConfig.defaults().weights());
        }
        List<PlacementWeight> parsed = new ArrayList<>();
        for (String key : value.split(",")) {
            if (key.isBlank()) {
                continue;
            }
            PlacementWeight weight = PlacementWeight.parse(key);
            if (weight == null) {
                return null;
            }
            if (!parsed.contains(weight)) {
                parsed.add(weight);
            }
        }
        return parsed.isEmpty() ? null : current.withWeights(parsed);
    }

    private static String describe(Siting siting) {
        return "(" + siting.origin().x() + "," + siting.origin().y() + "," + siting.origin().z() + ") orientation "
                + siting.placement().orientation().ordinal();
    }

    private static String describe(LatticePoint cell) {
        return "(" + cell.x() + "," + cell.y() + "," + cell.z() + ")";
    }

    @FunctionalInterface
    private interface Body {
        int run(CommandContext<CommandSourceStack> context);
    }
}
