package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
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

/**
 * The command surface: {@code check}, {@code compile}, {@code settle}, {@code build}, {@code explain},
 * {@code claims} and the structure-set pair {@code set check}/{@code set settle}.
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
                            ).then(PreviewCommand.literal())
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
                            )
                );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildOrigin() {
        var origin = buildFlags(
            Commands.argument("origin", BlockPosArgument.blockPos()),
            BuildCommand.Kind.defaultSource
        );
        origin.then(
            Commands.literal("with").then(buildFlags(Commands.literal("creative"), BuildCommand.Kind.creative))
                    .then(buildFlags(Commands.literal("player"), BuildCommand.Kind.player)).then(
                        buildFlags(
                            Commands.literal("container").then(Commands.argument("pos", BlockPosArgument.blockPos())),
                            BuildCommand.Kind.container
                        )
                    )
        );
        return origin;
    }

    /** The flags nest in either order, so {@code --dry --allow-removal} and its reverse both parse. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T buildFlags(T node, BuildCommand.Kind kind) {
        node.executes(buildBody(kind, false, false));
        node.then(
            Commands.literal("--dry").executes(buildBody(kind, true, false))
                    .then(Commands.literal("--allow-removal").executes(buildBody(kind, true, true)))
        );
        node.then(
            Commands.literal("--allow-removal").executes(buildBody(kind, false, true))
                    .then(Commands.literal("--dry").executes(buildBody(kind, true, true)))
        );
        return node;
    }

    private static Command<CommandSourceStack> buildBody(BuildCommand.Kind kind, boolean dry, boolean allowRemoval) {
        return context -> CommandSupport.guarded(
            context,
            "build",
            command -> BuildCommand.run(command, new BuildCommand.Options(dry, allowRemoval, kind))
        );
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
