package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;
import dev.vfyjxf.cumulus.mc.build.BuildConfig;
import dev.vfyjxf.cumulus.mc.build.BuildExecutor;
import dev.vfyjxf.cumulus.mc.build.BuildHistory;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.BuildRequest;
import dev.vfyjxf.cumulus.mc.build.CreativeItemSource;
import dev.vfyjxf.cumulus.mc.build.ItemHandlerSource;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.build.ItemSources;
import dev.vfyjxf.cumulus.mc.build.PlayerInventorySource;
import dev.vfyjxf.cumulus.mc.preview.BuildFeedback;
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
        BlockPos container = options.kind() == Kind.container ? BlockPosArgument.getBlockPos(context, "pos") : null;
        ItemSource items = items(
            source,
            state,
            level,
            player,
            options.kind(),
            container,
            sourceId(context, options.kind())
        );
        if (items == null) {
            return 0;
        }
        Siting siting = CommandSupport.rootSiting(state, source, id, blueprint, origin);
        if (siting == null) {
            return 0;
        }
        BuildPolicy configured = BuildConfig.policy();
        BuildPolicy policy = configured.withAllowRemoval(configured.allowRemoval() || options.allowRemoval());
        BuildExecutor executor = new BuildExecutor(
            state.capture(level),
            state.kernel(level),
            state.owner(),
            policy,
            state.materializers()
        );
        ApplyReport report = executor
                .run(new BuildRequest(blueprint.id(), siting, items, policy, options.dry(), null, player));
        print(source, report, id, origin, options);
        boolean formed = report.status() == ApplyReport.Status.committed
                && !hasCode(report, BuildCodes.recheckNotFormed);
        if (!options.settle() || options.dry()) {
            BuildFeedback.send(player, report, formed);
            return report.status() == ApplyReport.Status.committed ? 1 : 0;
        }
        formed = formed && SettleCommand.settle(source, state, level, blueprint, origin) instanceof MatchResult.Formed;
        BuildFeedback.send(player, report, formed);
        return formed ? 1 : 0;
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
        BuildFeedback.undo(player, report);
        CommandSupport.printFindings(source, "build undo: " + report.status(), report.findings());
        return report.status() == ApplyReport.Status.committed ? 1 : 0;
    }

    /** The command line's source choice, resolved once for the single build and the set build. */
    static @Nullable ItemSource items(
        CommandSourceStack source,
        ServerState state,
        ServerLevel level,
        ServerPlayer player,
        Kind kind,
        @Nullable BlockPos container,
        @Nullable NamespacedId registered
    ) {
        return switch (kind) {
            case creative -> new CreativeItemSource();
            case player -> new PlayerInventorySource(player);
            case container -> {
                if (container == null) {
                    source.sendFailure(Component.literal("a container source needs a position"));
                    yield null;
                }
                ItemHandlerSource handler = ItemHandlerSource.at(level, container, null);
                if (handler == null) {
                    source.sendFailure(Component.literal("no item handler at " + container.toShortString()));
                }
                yield handler;
            }
            case registered -> {
                ItemSource plugin = registered == null ? null : state.itemSources().create(registered, player, level);
                if (plugin == null) {
                    source.sendFailure(
                        Component.literal(
                            "no item source " + (registered == null ? "named" : registered.text()) + " (available: "
                                    + String.join(", ", available(state.itemSources())) + ")"
                        )
                    );
                }
                yield plugin;
            }
            case defaultSource -> player.isCreative() ? new CreativeItemSource() : new PlayerInventorySource(player);
        };
    }

    /** The plugin source id the command names, or null for every built-in form. */
    static @Nullable NamespacedId sourceId(CommandContext<CommandSourceStack> context, Kind kind) {
        return kind == Kind.registered ? CommandSupport.id(context, "source") : null;
    }

    /** The three built-in forms and every registered kind, for the refusal message and completion. */
    static List<String> available(ItemSources table) {
        List<String> ids = new ArrayList<>(List.of("creative", "player", "container"));
        for (NamespacedId id : table.ids()) {
            ids.add(id.text());
        }
        return ids;
    }

    private static boolean hasCode(ApplyReport report, FindingCode code) {
        return report.findings().findings().stream().anyMatch(finding -> finding.code().equals(code));
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
        defaultSource, creative, player, container, registered
    }

    /** The leaf's effect flags and its source choice. */
    record Options(boolean dry, boolean allowRemoval, boolean settle, Kind kind) {}

    private BuildCommand() {}
}
