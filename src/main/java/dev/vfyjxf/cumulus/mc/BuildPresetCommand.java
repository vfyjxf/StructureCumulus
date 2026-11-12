package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;
import dev.vfyjxf.cumulus.mc.build.BuildConfig;
import dev.vfyjxf.cumulus.mc.build.BuildExecutor;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.BuildRequest;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.pref.BuildPreset;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.preview.BuildFeedback;
import dev.vfyjxf.cumulus.mc.preview.PreviewContext;
import dev.vfyjxf.cumulus.mc.preview.PreviewService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /cumulus build preset}: save, apply, run, preview, list and delete the build contexts a
 * preview session holds. The command owner attaches {@link #literal()} under the {@code build} node
 * before its {@code <id>} argument, so the literal wins the parse. Applying a preset is the plain build
 * command with every argument typed out (D-99): the stored pose is the request and the executor
 * validates the live world before it writes anything.
 */
@NullMarked
public final class BuildPresetCommand {

    private BuildPresetCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.literal("preset").then(Commands.literal("save").then(saveName()))
                .then(
                    Commands.literal("apply").then(
                        Commands.argument("name", StringArgumentType.string())
                                .suggests(BuildPresetCommand::suggestPresets).executes(BuildPresetCommand::apply)
                    )
                )
                .then(
                    Commands.literal("run").then(
                        Commands.argument("name", StringArgumentType.string())
                                .suggests(BuildPresetCommand::suggestPresets).executes(BuildPresetCommand::apply)
                    )
                )
                .then(
                    Commands.literal("preview").then(
                        Commands.argument("name", StringArgumentType.string())
                                .suggests(BuildPresetCommand::suggestPresets).executes(BuildPresetCommand::preview)
                    )
                ).then(Commands.literal("list").executes(BuildPresetCommand::list))
                .then(
                    Commands.literal("delete").then(
                        Commands.argument("name", StringArgumentType.string())
                                .suggests(BuildPresetCommand::suggestPresets).executes(BuildPresetCommand::delete)
                    )
                );
    }

    /**
     * The save verb's name node: the session's own source form, or one of the build command's four
     * forms spelled after {@code with}, which is the form the preset stores.
     */
    private static ArgumentBuilder<CommandSourceStack, ?> saveName() {
        ArgumentBuilder<CommandSourceStack, ?> name = Commands.argument("name", StringArgumentType.string())
                .executes(save(SourceForm.defaultSource));
        name.then(
            Commands.literal("with").then(Commands.literal("creative").executes(save(SourceForm.creative)))
                    .then(Commands.literal("player").executes(save(SourceForm.player)))
                    .then(
                        Commands.literal("container").then(
                            Commands.argument("pos", BlockPosArgument.blockPos()).executes(save(SourceForm.container))
                        )
                    )
                    .then(
                        Commands.argument("source", ResourceLocationArgument.id())
                                .suggests(BuildPresetCommand::suggestSources).executes(save(SourceForm.registered))
                    )
        );
        return name;
    }

    private static Command<CommandSourceStack> save(SourceForm form) {
        return context -> CommandSupport.guarded(context, "build preset save", command -> save(command, form));
    }

    /** Which source form a save leaf records; {@code defaultSource} copies the session's own form. */
    private enum SourceForm {
        defaultSource, creative, player, container, registered
    }

    private static int save(CommandContext<CommandSourceStack> context, SourceForm form) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("a preset belongs to a player"));
            return 0;
        }
        PreviewContext preview = PreviewService.context(player);
        if (preview == null) {
            source.sendFailure(
                Component.literal("no active preview session; open one with /cumulus preview <id>, then save it")
            );
            return 0;
        }
        BuildPreset.Source chosen = form == SourceForm.defaultSource ? preview.source() : source(context, form);
        if (chosen == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        String name = StringArgumentType.getString(context, "name");
        BuildPreset preset;
        try {
            preset = new BuildPreset(name, preview.blueprintId(), preview.siting(), chosen, preview.removalPolicy());
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        PreferencesSavedData.SaveResult result = PreferencesSavedData.get(level).savePreset(player.getUUID(), preset);
        if (result == PreferencesSavedData.SaveResult.full) {
            source.sendFailure(
                Component.literal(
                    "the preset store is full (" + BuildPreset.capacity
                            + " presets); delete one with /cumulus build preset delete <name>"
                )
            );
            return 0;
        }
        String verb = result == PreferencesSavedData.SaveResult.saved ? "saved" : "replaced";
        source.sendSuccess(
            () -> Component.literal(
                "preset " + preset.name() + " " + verb + ": " + preset.blueprintId().text() + " at "
                        + BuildPreset.pose(preset.siting()) + " [" + preset.source().text() + "]"
            ),
            false
        );
        return 1;
    }

    /** The override form, validated through the build command's own source path so a refusal matches. */
    private static BuildPreset.@Nullable Source source(CommandContext<CommandSourceStack> context, SourceForm form) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        ServerPlayer player = source.getPlayer();
        if (state == null || player == null) {
            return null;
        }
        ServerLevel level = source.getLevel();
        return switch (form) {
            case creative ->
                BuildCommand.items(source, state, level, player, BuildCommand.Kind.creative, null, null) == null
                        ? null
                        : BuildPreset.Source.creative();
            case player ->
                BuildCommand.items(source, state, level, player, BuildCommand.Kind.player, null, null) == null
                        ? null
                        : BuildPreset.Source.player();
            case container -> {
                BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
                if (BuildCommand.items(source, state, level, player, BuildCommand.Kind.container, pos, null) == null) {
                    yield null;
                }
                LatticePoint cell = CommandSupport.point(pos, source);
                yield cell == null ? null : BuildPreset.Source.container(cell);
            }
            case registered -> {
                NamespacedId id = CommandSupport.id(context, "source");
                if (BuildCommand.items(source, state, level, player, BuildCommand.Kind.registered, null, id) == null) {
                    yield null;
                }
                yield BuildPreset.Source.registered(id);
            }
            case defaultSource -> throw new IllegalStateException("the default form copies the session's own");
        };
    }

    private static int apply(CommandContext<CommandSourceStack> context) {
        return CommandSupport.guarded(context, "build preset apply", BuildPresetCommand::run);
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("a build needs a player"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BuildPreset preset = preset(context, level, player);
        if (preset == null) {
            return 0;
        }
        Blueprint blueprint = CommandSupport.blueprint(state.kernel(level), source, preset.blueprintId());
        if (blueprint == null) {
            return 0;
        }
        ItemSource items = items(source, state, level, player, preset.source());
        if (items == null) {
            return 0;
        }
        BuildPolicy configured = BuildConfig.policy();
        BuildPolicy policy = switch (preset.removalPolicy()) {
            case forbid -> configured.withAllowRemoval(false);
            case ask -> configured;
            case allow -> configured.withAllowRemoval(true);
        };
        BuildExecutor executor = new BuildExecutor(
            state.capture(level),
            state.kernel(level),
            state.owner(),
            policy,
            state.materializers()
        );
        source.sendSuccess(
            () -> Component.literal(
                "preset " + preset.name() + ": " + preset.blueprintId().text() + " at "
                        + BuildPreset.pose(preset.siting())
            ),
            false
        );
        ApplyReport report = executor
                .run(new BuildRequest(preset.blueprintId(), preset.siting(), items, policy, false, null, player));
        BuildCommand.print(source, report, preset.blueprintId(), preset.siting().origin(), false);
        boolean formed = report.status() == ApplyReport.Status.committed
                && !hasCode(report, BuildCodes.recheckNotFormed);
        BuildFeedback.send(player, report, formed);
        return report.status() == ApplyReport.Status.committed ? 1 : 0;
    }

    private static int preview(CommandContext<CommandSourceStack> context) {
        return CommandSupport.guarded(context, "build preset preview", command -> {
            CommandSourceStack source = command.getSource();
            ServerState state = ServerState.current();
            if (state == null) {
                source.sendFailure(Component.literal("StructureCumulus is not running"));
                return 0;
            }
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("a preview needs a player"));
                return 0;
            }
            ServerLevel level = source.getLevel();
            BuildPreset preset = preset(command, level, player);
            if (preset == null) {
                return 0;
            }
            ItemSource items = items(source, state, level, player, preset.source());
            if (items == null) {
                return 0;
            }
            return PreviewService.openAt(source, preset, items);
        });
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        return CommandSupport.guarded(context, "build preset list", command -> {
            CommandSourceStack source = command.getSource();
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("presets belong to a player"));
                return 0;
            }
            List<BuildPreset> presets = PreferencesSavedData.get(source.getLevel()).presets(player.getUUID());
            source.sendSuccess(
                () -> Component.literal(
                    "presets of " + player.getGameProfile().getName() + ": " + presets.size() + "/"
                            + BuildPreset.capacity
                ),
                false
            );
            if (presets.isEmpty()) {
                source.sendSuccess(
                    () -> Component.literal(
                        "no presets; press K in a preview session or run /cumulus build preset save <name>"
                    ),
                    false
                );
                return 1;
            }
            for (BuildPreset preset : presets) {
                source.sendSuccess(() -> Component.literal("  " + preset.text()), false);
            }
            return 1;
        });
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        return CommandSupport.guarded(context, "build preset delete", command -> {
            CommandSourceStack source = command.getSource();
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("presets belong to a player"));
                return 0;
            }
            String name = StringArgumentType.getString(command, "name");
            if (!PreferencesSavedData.get(source.getLevel()).deletePreset(player.getUUID(), name)) {
                source.sendFailure(Component.literal("no preset " + name + " here"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("preset " + name + " deleted"), false);
            return 1;
        });
    }

    /** The named preset of the invoking player, or the refusal message. */
    private static @Nullable BuildPreset preset(
        CommandContext<CommandSourceStack> context,
        ServerLevel level,
        ServerPlayer player
    ) {
        String name = StringArgumentType.getString(context, "name");
        BuildPreset preset = PreferencesSavedData.get(level).preset(player.getUUID(), name);
        if (preset == null) {
            context.getSource().sendFailure(
                Component.literal("no preset " + name + "; /cumulus build preset list shows the stored ones")
            );
        }
        return preset;
    }

    /** A stored source form resolved for the invoking player and level, through the build command's path. */
    private static @Nullable ItemSource items(
        CommandSourceStack source,
        ServerState state,
        ServerLevel level,
        ServerPlayer player,
        BuildPreset.Source form
    ) {
        return switch (form.kind()) {
            case creative -> BuildCommand.items(source, state, level, player, BuildCommand.Kind.creative, null, null);
            case player -> BuildCommand.items(source, state, level, player, BuildCommand.Kind.player, null, null);
            case container -> BuildCommand.items(
                source,
                state,
                level,
                player,
                BuildCommand.Kind.container,
                blockPos(Objects.requireNonNull(form.container(), "container")),
                null
            );
            case registered ->
                BuildCommand.items(source, state, level, player, BuildCommand.Kind.registered, null, form.provider());
        };
    }

    private static BlockPos blockPos(LatticePoint cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static CompletableFuture<Suggestions> suggestPresets(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            for (BuildPreset preset : PreferencesSavedData.get(player.serverLevel()).presets(player.getUUID())) {
                builder.suggest(preset.name());
            }
        }
        return builder.buildFuture();
    }

    /** The registered item-source kinds, after the three built-in forms the parser already knows. */
    private static CompletableFuture<Suggestions> suggestSources(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        ServerState state = ServerState.current();
        if (state != null) {
            for (NamespacedId id : state.itemSources().ids()) {
                builder.suggest(id.text());
            }
        }
        return builder.buildFuture();
    }

    private static boolean hasCode(ApplyReport report, FindingCode code) {
        return report.findings().findings().stream().anyMatch(finding -> finding.code().equals(code));
    }
}
