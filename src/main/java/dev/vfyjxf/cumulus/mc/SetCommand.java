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
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildConfig;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.build.SetBuildHost;
import dev.vfyjxf.cumulus.mc.build.SetBuildRequest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The structure-set subcommands: compile, place, capture once and check, settle or build every member. */
@NullMarked
final class SetCommand {

    static int check(CommandContext<CommandSourceStack> context) {
        return run(context, false);
    }

    static int settle(CommandContext<CommandSourceStack> context) {
        return run(context, true);
    }

    static int build(CommandContext<CommandSourceStack> context, BuildOptions options) {
        CommandSourceStack source = context.getSource();
        Planned planned = plan(context);
        if (planned == null) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("a set build needs a player"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos container = options.kind() == BuildCommand.Kind.container
                ? BlockPosArgument.getBlockPos(context, "pos")
                : null;
        ItemSource items = BuildCommand.items(
            source,
            planned.state(),
            level,
            player,
            options.kind(),
            container,
            BuildCommand.sourceId(context, options.kind())
        );
        if (items == null) {
            return 0;
        }
        BuildPolicy configured = BuildConfig.policy();
        BuildPolicy policy = configured.withAllowRemoval(configured.allowRemoval() || options.allowRemoval());
        StructureSet plan = planned.set();
        CommandSupport.Captured captured = CommandSupport
                .capture(planned.state(), source, planned.id(), bounds(plan), policy(plan), null);
        if (captured == null) {
            return 0;
        }
        try (SnapshotLease lease = captured.lease()) {
            CommandSupport.printCaptureFindings(source, planned.id(), captured.report(), planned.state().kernel(level));
            SetBuildHost host = new SetBuildHost(
                planned.state().capture(level),
                planned.state().kernel(level),
                planned.state().owner(),
                planned.state().materializers()
            );
            StructureSetResult result = host.run(
                new SetBuildRequest(plan, items, policy, player, options.dry(), options.settle()),
                lease,
                Budget.unbounded()
            );
            printBuild(source, plan, planned.origin(), result, options);
            if (options.settle() && !options.dry()) {
                return result.formed() ? 1 : 0;
            }
            return result.built() ? 1 : 0;
        }
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean settle) {
        CommandSourceStack source = context.getSource();
        Planned planned = plan(context);
        if (planned == null) {
            return 0;
        }
        ServerState state = planned.state();
        NamespacedId id = planned.id();
        LatticePoint origin = planned.origin();
        StructureSet plan = planned.set();
        ServerLevel level = source.getLevel();
        CommandSupport.Captured captured = CommandSupport.capture(state, source, id, bounds(plan), policy(plan), null);
        if (captured == null) {
            return 0;
        }
        try (SnapshotLease lease = captured.lease()) {
            CommandSupport.printCaptureFindings(source, id, captured.report(), state.kernel(level));
            StructureSetHost host = new StructureSetHost(state.kernel(level));
            StructureSetResult result = settle
                    ? host.run(
                        plan,
                        lease,
                        state.owner(),
                        Budget.unbounded(),
                        spec -> state.structures(level).begin(state.world(level), spec)
                    )
                    : host.check(
                        plan,
                        lease,
                        state.owner(),
                        Budget.unbounded(),
                        spec -> state.structures(level).begin(state.world(level), spec)
                    );
            print(source, plan, origin, result, settle);
            if (settle) {
                return result.formed() ? 1 : 0;
            }
            return result.firstFailure() == null && !result.outcomes().isEmpty() ? 1 : 0;
        }
    }

    /** Everything a set verb resolves before its capture: the merged document, compiled and placed. */
    private static @Nullable Planned plan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerState state = ServerState.current();
        if (state == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            return null;
        }
        NamespacedId id = CommandSupport.id(context, "id");
        BlockPos position = BlockPosArgument.getBlockPos(context, "origin");
        LatticePoint origin = CommandSupport.point(position, source);
        if (origin == null) {
            return null;
        }
        StructureSetDocument document = state.structureSet(id);
        if (document == null) {
            source.sendFailure(Component.literal("no structure set " + id.text()));
            return null;
        }
        StructureSetCompileResult compiled = state.compileSet(document);
        CompiledStructureSet compiledSet = compiled.set();
        if (compiledSet == null) {
            CommandSupport.printFindings(source, "set " + id.text() + ": compile failed", compiled.findings());
            return null;
        }
        if (!compiled.findings().isEmpty()) {
            CommandSupport.printFindings(source, "set " + id.text() + ": compile findings", compiled.findings());
        }
        Siting rootSiting = CommandSupport.rootSiting(state, source, id, compiledSet.root(), origin);
        if (rootSiting == null) {
            return null;
        }
        StructureSet.Placement placement = StructureSet.place(compiledSet, rootSiting);
        if (!placement.succeeded()) {
            CommandSupport.printFindings(
                source,
                "set " + id.text() + " at " + origin + ": placement failed",
                FindingBundle.of(placement.findings())
            );
            return null;
        }
        if (!placement.findings().isEmpty()) {
            CommandSupport.printFindings(
                source,
                "set " + id.text() + " at " + origin + ": placement findings",
                FindingBundle.of(placement.findings())
            );
        }
        return new Planned(state, id, Objects.requireNonNull(placement.set()), origin);
    }

    /** The one capture region of a set: the bounding box of the root and every satellite's world bounds. */
    static Cuboid bounds(StructureSet plan) {
        Cuboid bounds = plan.rootBounds();
        for (StructureSet.Satellite satellite : plan.satellites()) {
            bounds = bounds.bounding(satellite.worldBounds());
        }
        return bounds;
    }

    /** The capture policy of a set: the union of the declared reads with each member's windows mapped. */
    static SnapshotPolicy policy(StructureSet plan) {
        ReadScope reads = plan.root().reads();
        List<EntityWindow> windows = new ArrayList<>(CommandSupport.windows(reads.windows(), plan.rootSiting()));
        for (StructureSet.Satellite satellite : plan.satellites()) {
            ReadScope satelliteReads = satellite.blueprint().reads();
            windows.addAll(CommandSupport.windows(satelliteReads.windows(), satellite.siting()));
            reads = reads.union(satelliteReads);
        }
        return CommandSupport.policy(reads, windows);
    }

    private static void printBuild(
        CommandSourceStack source,
        StructureSet plan,
        LatticePoint origin,
        StructureSetResult result,
        BuildOptions options
    ) {
        String where = "set " + plan.id().text() + " at " + origin;
        String verb = options.dry() ? "dry build" : "build";
        source.sendSuccess(() -> Component.literal(where + ": " + verb), false);
        printBuilt(source, "root", plan.root().id(), result);
        for (StructureSet.Satellite satellite : plan.satellites()) {
            printBuilt(source, "satellite", satellite.id(), result);
        }
        source.sendSuccess(() -> Component.literal("items consumed: " + consumed(result)), false);
        source.sendSuccess(() -> Component.literal("settled: " + ids(result.settled())), false);
        source.sendSuccess(() -> Component.literal("rolled back: " + ids(result.rolledBack())), false);
        source.sendSuccess(() -> Component.literal("rollback failures: " + result.rollbackFailures().size()), false);
        source.sendSuccess(() -> Component.literal("first failure: " + firstFailure(result.firstFailure())), false);
        if (!result.setFindings().isEmpty()) {
            CommandSupport.printFindings(source, where + ": findings", FindingBundle.of(result.setFindings()));
        }
    }

    private static void printBuilt(CommandSourceStack source, String role, NamespacedId id, StructureSetResult result) {
        ApplyReport report = result.buildOf(id);
        String state = report == null ? "not built" : describe(report);
        source.sendSuccess(() -> Component.literal(role + " " + id.text() + ": " + state), false);
    }

    private static String describe(ApplyReport report) {
        int applied = 0;
        int skipped = 0;
        for (ApplyReport.Step step : report.steps()) {
            switch (step.outcome()) {
                case applied -> applied++;
                case skipped -> skipped++;
                case failed, restored -> {}
            }
        }
        String items = report.itemsConsumed().isEmpty() ? "" : ", " + count(report.itemsConsumed()) + " item(s)";
        return switch (report.status()) {
            case committed -> report.steps().isEmpty()
                    ? "committed (nothing to apply)"
                    : "committed (" + applied + " applied, " + skipped + " skipped" + items + ")";
            case rolledBack -> "rolled back (" + applied + " applied, torn down" + items + ")";
            case partiallyRolledBack -> "partially rolled back (" + applied + " applied" + items + ")";
            case refused -> "refused";
        };
    }

    private static String consumed(StructureSetResult result) {
        return result.itemsConsumed().isEmpty() ? "none" : count(result.itemsConsumed()) + " item(s)";
    }

    private static int count(List<ItemStack> stacks) {
        int total = 0;
        for (ItemStack stack : stacks) {
            total += stack.getCount();
        }
        return total;
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

    /** The leaf's effect flags and its source choice. */
    record BuildOptions(boolean dry, boolean allowRemoval, boolean settle, BuildCommand.Kind kind) {}

    private record Planned(ServerState state, NamespacedId id, StructureSet set, LatticePoint origin) {}

    private SetCommand() {}
}
