package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.FindingRenderer;
import dev.vfyjxf.cumulus.kernel.finding.RenderContext;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
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
 * The command surface: {@code check}, {@code compile}, {@code settle}, {@code explain}, {@code claims}
 * and the structure-set pair {@code set check}/{@code set settle}.
 */
@NullMarked
public final class CumulusCommand {

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(
                    Commands.literal("cumulus")
                            .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .then(
                                Commands.literal("check").then(
                                    Commands.argument("id", ResourceLocationArgument.id()).then(
                                        Commands.argument("origin", BlockPosArgument.blockPos())
                                                .executes(context -> CommandSupport.guarded(context, "check", CumulusCommand::check))
                                    )
                                )
                            )
                            .then(
                                Commands.literal("settle")
                                        .then(
                                            Commands.argument("id", ResourceLocationArgument.id()).then(
                                                Commands.argument("origin", BlockPosArgument.blockPos())
                                                        .executes(context -> CommandSupport.guarded(context, "settle", SettleCommand::run))
                                            )
                                        )
                            )
                            .then(
                                Commands.literal("compile").then(
                                    Commands.argument("id", ResourceLocationArgument.id())
                                            .executes(context -> CommandSupport.guarded(context, "compile", CompileCommand::run))
                                )
                            )
                            .then(
                                Commands.literal("explain").then(
                                    Commands.argument("code", StringArgumentType.word())
                                            .executes(context -> CommandSupport.guarded(context, "explain", ExplainCommand::run))
                                )
                            )
                            .then(
                                Commands.literal("claims")
                                        .then(
                                            Commands.argument("from", BlockPosArgument.blockPos()).then(
                                                Commands.argument("to", BlockPosArgument.blockPos())
                                                        .executes(context -> CommandSupport.guarded(context, "claims", ClaimsCommand::run))
                                            )
                                        )
                            )
                            .then(
                                Commands.literal("set")
                                        .then(
                                            Commands.literal("check").then(
                                                Commands.argument("id", ResourceLocationArgument.id()).then(
                                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                                            .executes(context -> CommandSupport.guarded(context, "set check", SetCommand::check))
                                                )
                                            )
                                        )
                                        .then(
                                            Commands.literal("settle").then(
                                                Commands.argument("id", ResourceLocationArgument.id()).then(
                                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                                            .executes(context -> CommandSupport.guarded(context, "set settle", SetCommand::settle))
                                                )
                                            )
                                        )
                            )
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
            ClaimView claims = state.claims(level).read(prepared.bounds());
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
                print(source, summary(blueprint.id(), origin, checked), checked.findings());
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

    private static void print(CommandSourceStack source, String header, FindingBundle findings) {
        source.sendSuccess(() -> Component.literal(header), false);
        if (findings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("no findings"), false);
            return;
        }
        source.sendSuccess(() -> Component.literal(FindingRenderer.human(findings, RenderContext.plain())), false);
    }

    private CumulusCommand() {}
}
