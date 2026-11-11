package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.pref.PreferencesCommand;
import dev.vfyjxf.cumulus.mc.preview.PreviewCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * The command surface: {@code check}, {@code compile}, {@code settle}, {@code build}, {@code explain},
 * {@code claims}, {@code preview}, {@code prefs} and the structure-set triple {@code set check}/{@code
 * set settle}/{@code set build}.
 */
@NullMarked
public final class CumulusCommand {

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(
                    Commands.literal("cumulus").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .then(
                                Commands.literal("check")
                                        .then(
                                            Commands.argument("id", ResourceLocationArgument.id()).then(
                                                Commands.argument("origin", BlockPosArgument.blockPos())
                                                        .executes(
                                                            context -> CommandSupport
                                                                    .guarded(context, "check", CumulusCommand::check)
                                                        )
                                            )
                                        )
                            )
                            .then(
                                Commands.literal("settle")
                                        .then(
                                            Commands.argument("id", ResourceLocationArgument.id()).then(
                                                Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                                    context -> CommandSupport
                                                            .guarded(context, "settle", SettleCommand::run)
                                                )
                                            )
                                        )
                            )
                            .then(
                                Commands.literal("build")
                                        .then(
                                            Commands.literal("undo").executes(
                                                context -> CommandSupport
                                                        .guarded(context, "build undo", BuildCommand::undo)
                                            )
                                        ).then(
                                            Commands.argument("id", ResourceLocationArgument.id()).then(buildOrigin())
                                        )
                            ).then(PreviewCommand.literal()).then(PreferencesCommand.literal())
                            .then(
                                Commands.literal("compile")
                                        .then(
                                            Commands.argument("id", ResourceLocationArgument.id())
                                                    .executes(
                                                        context -> CommandSupport
                                                                .guarded(context, "compile", CompileCommand::run)
                                                    )
                                        )
                            )
                            .then(
                                Commands.literal("explain")
                                        .then(
                                            Commands.argument("code", StringArgumentType.word()).executes(
                                                context -> CommandSupport
                                                        .guarded(context, "explain", ExplainCommand::run)
                                            )
                                        )
                            )
                            .then(
                                Commands.literal("claims")
                                        .then(
                                            Commands.argument("from", BlockPosArgument.blockPos())
                                                    .then(
                                                        Commands.argument("to", BlockPosArgument.blockPos()).executes(
                                                            context -> CommandSupport
                                                                    .guarded(context, "claims", ClaimsCommand::run)
                                                        )
                                                    )
                                        )
                            )
                            .then(
                                Commands.literal("set")
                                        .then(
                                            Commands.literal("check").then(
                                                Commands.argument("id", ResourceLocationArgument.id()).then(
                                                    Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                                        context -> CommandSupport
                                                                .guarded(context, "set check", SetCommand::check)
                                                    )
                                                )
                                            )
                                        )
                                        .then(
                                            Commands.literal("settle").then(
                                                Commands.argument("id", ResourceLocationArgument.id()).then(
                                                    Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                                        context -> CommandSupport
                                                                .guarded(context, "set settle", SetCommand::settle)
                                                    )
                                                )
                                            )
                                        )
                                        .then(
                                            Commands.literal("build").then(
                                                Commands.argument("id", ResourceLocationArgument.id()).then(setOrigin())
                                            )
                                        )
                            )
                );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildOrigin() {
        return origin(EnumSet.allOf(BuildFlag.class), CumulusCommand::buildBody);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> setOrigin() {
        return origin(EnumSet.allOf(BuildFlag.class), CumulusCommand::setBuildBody);
    }

    /** The origin argument, every flag subset and the three source forms. */
    private static ArgumentBuilder<CommandSourceStack, ?> origin(
        Set<BuildFlag> flags,
        BiFunction<Set<BuildFlag>, BuildCommand.Kind, Command<CommandSourceStack>> body
    ) {
        ArgumentBuilder<CommandSourceStack, ?> node = flags(
            Commands.argument("origin", BlockPosArgument.blockPos()),
            BuildCommand.Kind.defaultSource,
            EnumSet.noneOf(BuildFlag.class),
            flags,
            body
        );
        node.then(
            Commands.literal("with")
                    .then(
                        flags(
                            Commands.literal("creative"),
                            BuildCommand.Kind.creative,
                            EnumSet.noneOf(BuildFlag.class),
                            flags,
                            body
                        )
                    )
                    .then(
                        flags(
                            Commands.literal("player"),
                            BuildCommand.Kind.player,
                            EnumSet.noneOf(BuildFlag.class),
                            flags,
                            body
                        )
                    )
                    .then(
                        flags(
                            Commands.literal("container").then(Commands.argument("pos", BlockPosArgument.blockPos())),
                            BuildCommand.Kind.container,
                            EnumSet.noneOf(BuildFlag.class),
                            flags,
                            body
                        )
                    )
                    // a registered kind is an argument, added after the literals so it never shadows them
                    .then(
                        flags(
                            Commands.argument("source", ResourceLocationArgument.id())
                                    .suggests(CumulusCommand::suggestSources),
                            BuildCommand.Kind.registered,
                            EnumSet.noneOf(BuildFlag.class),
                            flags,
                            body
                        )
                    )
        );
        return node;
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

    /** Every flag subset in every order, each node executable, so the flags nest either way. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T flags(
        T node,
        BuildCommand.Kind kind,
        Set<BuildFlag> chosen,
        Set<BuildFlag> remaining,
        BiFunction<Set<BuildFlag>, BuildCommand.Kind, Command<CommandSourceStack>> body
    ) {
        node.executes(body.apply(chosen, kind));
        for (BuildFlag flag : List.copyOf(remaining)) {
            Set<BuildFlag> with = EnumSet.copyOf(chosen);
            with.add(flag);
            Set<BuildFlag> rest = EnumSet.copyOf(remaining);
            rest.remove(flag);
            node.then(flags(Commands.literal(flag.literal), kind, with, rest, body));
        }
        return node;
    }

    private static Command<CommandSourceStack> buildBody(Set<BuildFlag> flags, BuildCommand.Kind kind) {
        return context -> CommandSupport.guarded(
            context,
            "build",
            command -> BuildCommand.run(
                command,
                new BuildCommand.Options(
                    flags.contains(BuildFlag.dry),
                    flags.contains(BuildFlag.allowRemoval),
                    flags.contains(BuildFlag.settle),
                    kind
                )
            )
        );
    }

    private static Command<CommandSourceStack> setBuildBody(Set<BuildFlag> flags, BuildCommand.Kind kind) {
        return context -> CommandSupport.guarded(
            context,
            "set build",
            command -> SetCommand.build(
                command,
                new SetCommand.BuildOptions(
                    flags.contains(BuildFlag.dry),
                    flags.contains(BuildFlag.allowRemoval),
                    flags.contains(BuildFlag.settle),
                    kind
                )
            )
        );
    }

    /** One leaf flag: its literal on the command line. */
    private enum BuildFlag {
        dry("--dry"), allowRemoval("--allow-removal"), settle("--settle");

        private final String literal;

        BuildFlag(String literal) {
            this.literal = literal;
        }
    }

    private static int check(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        NamespacedId id = CommandSupport.id(context, "id");
        Blueprint blueprint = CommandSupport.blueprint(state.kernel(), source, id);
        if (blueprint == null) {
            return 0;
        }
        BlockPos origin = BlockPosArgument.getBlockPos(context, "origin");
        LatticePoint point = CommandSupport.point(origin, source);
        if (point == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        MatchResult checked = check(source, state, level, blueprint, point);
        return checked == null ? 0 : 1;
    }

    static @Nullable MatchResult check(
        CommandSourceStack source,
        ServerState state,
        ServerLevel level,
        Blueprint blueprint,
        LatticePoint origin
    ) {
        CommandSupport.Attempt attempt = CommandSupport.attempt(state, source, blueprint.id(), blueprint, origin);
        if (attempt == null) {
            return null;
        }
        try (CommandSupport.Attempt prepared = attempt) {
            CommandSupport.printCaptureFindings(source, blueprint.id(), prepared.report(), state.kernel());
            ClaimView claims = CommandSupport
                    .remainingClaims(state.claims(level), prepared.bounds(), prepared.siting());
            StructureAttempt draft = state.structures(level)
                    .begin(state.world(level), new StructureSpec(state.owner(), prepared.siting(), blueprint.id()));
            try {
                MatchRequest request = MatchRequest.of(
                    blueprint,
                    prepared.siting(),
                    prepared.lease(),
                    draft.id(),
                    state.owner(),
                    Budget.unbounded(),
                    claims
                );
                MatchResult checked = state.kernel().check(request);
                CommandSupport.printFindings(source, summary(blueprint.id(), origin, checked), checked.findings());
                return checked;
            } finally {
                CommandSupport.abortQuietly(draft);
            }
        }
    }

    private static String summary(NamespacedId id, LatticePoint origin, MatchResult result) {
        String state;
        if (result instanceof MatchResult.Undecided undecided) {
            state = "undecided (" + undecided.reason() + ")";
        } else if (result instanceof MatchResult.Admitted) {
            state = "admitted";
        } else if (result instanceof MatchResult.Formed) {
            state = "formed";
        } else if (result instanceof MatchResult.Failed) {
            state = "failed";
        } else if (result instanceof MatchResult.Dropped) {
            state = "dropped";
        } else {
            throw new IllegalArgumentException("unknown match result " + result);
        }
        return "check " + id.text() + " at " + origin + ": " + state;
    }

    private CumulusCommand() {}
}
