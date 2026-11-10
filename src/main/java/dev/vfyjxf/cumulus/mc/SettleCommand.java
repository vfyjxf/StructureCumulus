package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
            ClaimView claims = remainingClaims(state.claims(level), prepared.bounds(), prepared.siting());
            StructureAttempt draft = state.structures(level)
                    .begin(state.world(level), new StructureSpec(state.owner(), prepared.siting(), blueprint.id()));
            boolean closed = false;
            boolean settleAttempted = false;
            MatchResult settled = null;
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
                MatchResult checked = kernel.check(request);
                if (!(checked instanceof MatchResult.Admitted admitted)) {
                    CommandSupport.printFindings(
                        source,
                        summary("check", blueprint.id(), origin, checked),
                        checked.findings()
                    );
                    return checked;
                }
                settleAttempted = true;
                settled = kernel.settle(request, admitted, draft);
                if (settled instanceof MatchResult.Formed) {
                    draft.commit(prepared.bounds().asSet());
                    closed = true;
                }
                CommandSupport
                        .printFindings(source, summary("settle", blueprint.id(), origin, settled), settled.findings());
                return settled;
            } finally {
                if (!closed) {
                    CommandSupport.abortQuietly(draft);
                    if (settleAttempted) {
                        CommandSupport.releaseQuietly(state.claims(level), draft.id());
                    }
                }
            }
        }
    }

    // A settle replaces its siting's incumbent rows, so the check view must not carry them as conflicts.
    private static ClaimView remainingClaims(ClaimLedger ledger, Cuboid bounds, Siting siting) {
        ClaimView view = ledger.read(bounds);
        List<ClaimRow> kept = new ArrayList<>(view.rows().size());
        for (ClaimRow row : view.rows()) {
            if (!row.siting().equals(siting)) {
                kept.add(row);
            }
        }
        return new ClaimView(kept, view.revision(), view.available());
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
