package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** The structure-set subcommands: compile, place, capture once and check or settle every member. */
@NullMarked
final class SetCommand {

    static int check(CommandContext<CommandSourceStack> context) {
        return run(context, false);
    }

    static int settle(CommandContext<CommandSourceStack> context) {
        return run(context, true);
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean settle) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return 0;
        }
        NamespacedId id = CommandSupport.id(context, "id");
        BlockPos position = BlockPosArgument.getBlockPos(context, "origin");
        LatticePoint origin = CommandSupport.point(position, source);
        if (origin == null) {
            return 0;
        }
        StructureSetDocument document = state.structureSet(id);
        if (document == null) {
            source.sendFailure(Component.literal("no structure set " + id.text()));
            return 0;
        }
        StructureSetCompileResult compiled = state.compileSet(document);
        CompiledStructureSet compiledSet = compiled.set();
        if (compiledSet == null) {
            CommandSupport.printFindings(source, "set " + id.text() + ": compile failed", compiled.findings());
            return 0;
        }
        if (!compiled.findings().isEmpty()) {
            CommandSupport.printFindings(source, "set " + id.text() + ": compile findings", compiled.findings());
        }
        Siting rootSiting = CommandSupport.rootSiting(state, source, id, compiledSet.root(), origin);
        if (rootSiting == null) {
            return 0;
        }
        StructureSet.Placement placement = StructureSet.place(compiledSet, rootSiting);
        if (!placement.succeeded()) {
            CommandSupport.printFindings(
                source,
                "set " + id.text() + " at " + origin + ": placement failed",
                FindingBundle.of(placement.findings())
            );
            return 0;
        }
        if (!placement.findings().isEmpty()) {
            CommandSupport.printFindings(
                source,
                "set " + id.text() + " at " + origin + ": placement findings",
                FindingBundle.of(placement.findings())
            );
        }
        StructureSet plan = Objects.requireNonNull(placement.set());
        ServerLevel level = source.getLevel();
        CommandSupport.Captured captured = CommandSupport
                .capture(state, source, id, bounds(plan), policy(plan), rootSiting.placement().orientation());
        if (captured == null) {
            return 0;
        }
        try (SnapshotLease lease = captured.lease()) {
            CommandSupport.printCaptureFindings(source, id, captured.report(), state.kernel(level));
            StructureSetHost host = new StructureSetHost(state.kernel(level));
            StructureSetResult result = settle
                    ? host.run(plan, lease, state.owner(), Budget.unbounded(), () -> state.instance(level))
                    : host.check(plan, lease, state.owner(), Budget.unbounded(), () -> state.instance(level));
            print(source, plan, origin, result, settle);
            if (settle) {
                return result.formed() ? 1 : 0;
            }
            return result.firstFailure() == null && !result.outcomes().isEmpty() ? 1 : 0;
        }
    }

    /** The one capture region of a set: the bounding box of the root and every satellite's world bounds. */
    static Cuboid bounds(StructureSet plan) {
        Cuboid bounds = plan.rootBounds();
        for (StructureSet.Satellite satellite : plan.satellites()) {
            bounds = bounds.bounding(satellite.worldBounds());
        }
        return bounds;
    }

    /** The capture policy of a set: the union of the root's and every satellite's declared reads. */
    static SnapshotPolicy policy(StructureSet plan) {
        ReadScope reads = plan.root().reads();
        for (StructureSet.Satellite satellite : plan.satellites()) {
            reads = reads.union(satellite.blueprint().reads());
        }
        return CommandSupport.policy(reads);
    }

    private static void print(
        CommandSourceStack source,
        StructureSet plan,
        LatticePoint origin,
        StructureSetResult result,
        boolean settle
    ) {
        String where = "set " + plan.id().text() + " at " + origin;
        source.sendSuccess(() -> Component.literal(where + ": " + (settle ? "settle" : "check")), false);
        printOutcome(source, "root", plan.root().id(), result);
        for (StructureSet.Satellite satellite : plan.satellites()) {
            printOutcome(source, "satellite", satellite.id(), result);
        }
        source.sendSuccess(() -> Component.literal("settled: " + ids(result.settled())), false);
        source.sendSuccess(() -> Component.literal("rolled back: " + ids(result.rolledBack())), false);
        source.sendSuccess(() -> Component.literal("rollback failures: " + result.rollbackFailures().size()), false);
        source.sendSuccess(() -> Component.literal("claims: " + claimCounts(plan, result)), false);
        source.sendSuccess(() -> Component.literal("first failure: " + firstFailure(result.firstFailure())), false);
        if (!result.setFindings().isEmpty()) {
            CommandSupport.printFindings(source, where + ": findings", FindingBundle.of(result.setFindings()));
        }
    }

    private static void printOutcome(
        CommandSourceStack source,
        String role,
        NamespacedId id,
        StructureSetResult result
    ) {
        String state = result.outcomes().containsKey(id) ? "admitted" : "not admitted";
        source.sendSuccess(() -> Component.literal(role + " " + id.text() + ": " + state), false);
    }

    private static String claimCounts(StructureSet plan, StructureSetResult result) {
        StringBuilder text = new StringBuilder();
        appendClaimCount(text, plan.root().id(), result);
        for (StructureSet.Satellite satellite : plan.satellites()) {
            appendClaimCount(text, satellite.id(), result);
        }
        return text.toString();
    }

    private static void appendClaimCount(StringBuilder text, NamespacedId id, StructureSetResult result) {
        if (!text.isEmpty()) {
            text.append(", ");
        }
        text.append(id.text()).append('=').append(result.claimsOf(id).size()).append(" row(s)");
    }

    private static String ids(List<NamespacedId> ids) {
        if (ids.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (NamespacedId id : ids) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(id.text());
        }
        return text.toString();
    }

    private static String firstFailure(@Nullable Finding finding) {
        return finding == null ? "none" : finding.code().text() + " " + finding.message();
    }

    private SetCommand() {}
}
