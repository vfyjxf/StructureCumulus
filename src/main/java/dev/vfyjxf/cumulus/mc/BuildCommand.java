package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildConfig;
import dev.vfyjxf.cumulus.mc.build.BuildExecutor;
import dev.vfyjxf.cumulus.mc.build.BuildHistory;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.BuildRequest;
import dev.vfyjxf.cumulus.mc.build.CreativeItemSource;
import dev.vfyjxf.cumulus.mc.build.ItemHandlerSource;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.build.PlayerInventorySource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The build subcommands: apply a plan to the world and undo the last apply. */
@NullMarked
final class BuildCommand {

    static int run(CommandContext<CommandSourceStack> context, Options options) {
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
        NamespacedId id = CommandSupport.id(context, "id");
        Blueprint blueprint = CommandSupport.blueprint(state.kernel(level), source, id);
        if (blueprint == null) {
            return 0;
        }
        LatticePoint origin = CommandSupport.point(BlockPosArgument.getBlockPos(context, "origin"), source);
        if (origin == null) {
            return 0;
        }
        ItemSource items = items(source, level, player, context, options);
        if (items == null) {
            return 0;
        }
        Siting siting = CommandSupport.rootSiting(state, source, id, blueprint, origin);
        if (siting == null) {
            return 0;
        }
        BuildPolicy configured = BuildConfig.policy();
        BuildPolicy policy = configured.withAllowRemoval(configured.allowRemoval() || options.allowRemoval());
        BuildExecutor executor = new BuildExecutor(state.capture(level), state.kernel(level), state.owner(), policy);
        ApplyReport report = executor
                .run(new BuildRequest(blueprint.id(), siting, items, policy, options.dry(), null, player));
        print(source, report, id, origin, options);
        return report.status() == ApplyReport.Status.committed ? 1 : 0;
    }

    static int undo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("an undo needs a player"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        ApplyReport report = BuildHistory.undo(level, player.getUUID());
        if (report == null) {
            source.sendFailure(Component.literal("nothing to undo in this world"));
            return 0;
        }
        CommandSupport.printFindings(source, "build undo: " + report.status(), report.findings());
        return report.status() == ApplyReport.Status.committed ? 1 : 0;
    }

    private static @Nullable ItemSource items(
        CommandSourceStack source,
        ServerLevel level,
        ServerPlayer player,
        CommandContext<CommandSourceStack> context,
        Options options
    ) {
        return switch (options.kind()) {
            case creative -> new CreativeItemSource();
            case player -> new PlayerInventorySource(player);
            case container -> {
                BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
                ItemHandlerSource container = ItemHandlerSource.at(level, pos, null);
                if (container == null) {
                    source.sendFailure(Component.literal("no item handler at " + pos.toShortString()));
                }
                yield container;
            }
            case defaultSource -> player.isCreative() ? new CreativeItemSource() : new PlayerInventorySource(player);
        };
    }

    private static void print(
        CommandSourceStack source,
        ApplyReport report,
        NamespacedId id,
        LatticePoint origin,
        Options options
    ) {
        int applied = 0;
        int skipped = 0;
        int failed = 0;
        for (ApplyReport.Step step : report.steps()) {
            switch (step.outcome()) {
                case applied -> applied++;
                case skipped -> skipped++;
                case failed -> failed++;
                case restored -> {}
            }
        }
        String detail = switch (report.status()) {
            case committed -> options.dry()
                    ? "dry validation clean"
                    : "committed (" + applied + " applied, " + skipped + " skipped" + items(report) + ")";
            case rolledBack -> "rolled back (" + failed + " failed steps" + items(report) + ")";
            case partiallyRolledBack -> "partially rolled back (" + failed + " failed steps" + items(report) + ")";
            case refused -> "refused";
        };
        CommandSupport.printFindings(source, "build " + id.text() + " at " + origin + ": " + detail, report.findings());
    }

    private static String items(ApplyReport report) {
        List<ItemStack> consumed = report.itemsConsumed();
        if (consumed.isEmpty()) {
            return "";
        }
        int total = 0;
        for (ItemStack stack : consumed) {
            total += stack.getCount();
        }
        return ", " + total + " items consumed";
    }

    /** Which source the command line asked for, before the player's game mode decides the default. */
    enum Kind {
        defaultSource, creative, player, container
    }

    /** The leaf's effect flags and its source choice. */
    record Options(boolean dry, boolean allowRemoval, Kind kind) {}

    private BuildCommand() {}
}
