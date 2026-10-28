package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The settle subcommand: capture once, check once, then write the claims of the admitted attempt. */
@NullMarked
final class SettleCommand {

    static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        NamespacedId id = CommandSupport.id(context, "id");
        BlockPos origin = BlockPosArgument.getBlockPos(context, "origin");
        LatticePoint point = CommandSupport.point(origin, source);
        if (point == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        Blueprint blueprint = CommandSupport.blueprint(state.kernel(level), source, id);
        if (blueprint == null) {
            return 0;
        }
        MatchResult result = settle(source, state, level, blueprint, point);
        return result instanceof MatchResult.Formed ? 1 : 0;
    }

    static @Nullable MatchResult settle(
        CommandSourceStack source,
        ServerState state,
        ServerLevel level,
        Blueprint blueprint,
        LatticePoint origin
    ) {
        StructureKernel kernel = state.kernel(level);
        CommandSupport.Attempt attempt = CommandSupport.attempt(state, source, blueprint.id(), blueprint, origin);
        if (attempt == null) {
            return null;
        }
        try (CommandSupport.Attempt prepared = attempt) {
            CommandSupport.printCaptureFindings(source, blueprint.id(), prepared.report(), kernel);
            ClaimView claims = state.claims(level).read(prepared.bounds());
            MatchRequest request = MatchRequest.of(
                blueprint,
                prepared.siting(),
                prepared.lease(),
                state.instance(level),
                state.owner(),
                Budget.unbounded(),
                claims
            );
            MatchResult checked = kernel.check(request);
            if (!(checked instanceof MatchResult.Admitted admitted)) {
                CommandSupport
                        .printFindings(source, summary("check", blueprint.id(), origin, checked), checked.findings());
                return checked;
            }
            MatchResult settled = kernel.settle(request, admitted);
            CommandSupport
                    .printFindings(source, summary("settle", blueprint.id(), origin, settled), settled.findings());
            return settled;
        }
    }

    private static String summary(String verb, NamespacedId id, LatticePoint origin, MatchResult result) {
        String detail = switch (result) {
            case MatchResult.Formed formed -> "formed (" + formed.claims().rows().size() + " rows, ledger revision "
                    + formed.ledgerRevision() + ", context revision " + formed.contextRevision() + ")";
            case MatchResult.Admitted ignored -> "admitted";
            case MatchResult.Undecided undecided -> "undecided (" + undecided.reason() + ")";
            case MatchResult.Failed ignored -> "failed";
            case MatchResult.Dropped ignored -> "dropped";
        };
        return verb + " " + id.text() + " at " + origin + ": " + detail;
    }

    private SettleCommand() {}
}
