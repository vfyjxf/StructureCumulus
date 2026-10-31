package dev.vfyjxf.cumulus.uitest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.NullMarked;

import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Dev-only server command that stages the multi-instance visual fixtures; the harness server steps
 * invoke it, and it never prints to chat.
 */
@NullMarked
final class VisualFixtureCommand {

    private VisualFixtureCommand() {}

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher
                .register(
                    Commands.literal("cumulusvisual")
                            .then(
                                Commands.literal("overlap").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stageOverlap))
                                )
                            )
                            .then(
                                Commands.literal("conflict").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stageConflict))
                                )
                            )
                            .then(
                                Commands.literal("context").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stageContext))
                                )
                            )
                            .then(
                                Commands.literal("scale").then(
                                    Commands.argument("count", IntegerArgumentType.integer(1))
                                            .executes(
                                                context -> scaleStage(
                                                    context,
                                                    IntegerArgumentType.getInteger(context, "count"),
                                                    LatticePoint.of(256, 100, 0)
                                                )
                                            )
                                            .then(
                                                Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                                    context -> scaleStage(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        LatticePoint.of(
                                                            BlockPosArgument.getBlockPos(context, "origin").getX(),
                                                            BlockPosArgument.getBlockPos(context, "origin").getY(),
                                                            BlockPosArgument.getBlockPos(context, "origin").getZ()
                                                        )
                                                    )
                                                )
                                            )
                                )
                            )
                            .then(
                                Commands.literal("groups").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                        context -> hierarchyStage(context, VisualHierarchyFixtures::groups)
                                    )
                                )
                            )
                            .then(
                                Commands.literal("satellites").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                        context -> hierarchyStage(context, VisualHierarchyFixtures::satellites)
                                    )
                                )
                            )
                            .then(
                                Commands.literal("substructures").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                        context -> hierarchyStage(context, VisualHierarchyFixtures::substructures)
                                    )
                                )
                            )
                            .then(
                                Commands.literal("set").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stageSet))
                                )
                            )
                            .then(
                                Commands.literal("orientation").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stageOrientation))
                                )
                            )
                            .then(
                                Commands.literal("large").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stageLarge))
                                )
                            )
                            .then(
                                Commands.literal("pair").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stagePair))
                                )
                            )
                            .then(
                                Commands.literal("move").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(VisualFixtureCommand::move)
                                )
                            ).then(Commands.literal("unload").executes(context -> {
                                return VisualFixtureSupport.unloadFixture(context.getSource().getLevel()) ? 1 : 0;
                            })).then(Commands.literal("load").executes(context -> {
                                return VisualFixtureSupport.loadFixture(context.getSource().getLevel()) ? 1 : 0;
                            }))
                            .then(
                                Commands.literal("error").then(
                                    Commands.argument("origin", BlockPosArgument.blockPos())
                                            .executes(context -> stage(context, VisualFixtureSupport::stageError))
                                )
                            )
                            .then(
                                Commands.literal("complex").then(
                                    Commands.literal("blast_furnace").then(
                                        Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                            context -> complexStage(context, VisualComplexFixtures::blastFurnace)
                                        )
                                    )
                                ).then(
                                    Commands.literal("port").then(
                                        Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                            context -> complexStage(context, VisualComplexFixtures::portEnvelope)
                                        )
                                    )
                                ).then(
                                    Commands.literal("logical").then(
                                        Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                            context -> complexStage(context, VisualComplexFixtures::logicalSubnetwork)
                                        )
                                    )
                                ).then(
                                    Commands.literal("nested").then(
                                        Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                            context -> complexStage(context, VisualComplexFixtures::nestedFrame)
                                        )
                                    )
                                ).then(
                                    Commands.literal("smeltery").then(
                                        Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                            context -> industrialStage(context, VisualIndustrialFixtures::smeltery)
                                        )
                                    )
                                ).then(
                                    Commands.literal("ebf").then(
                                        Commands.argument("origin", BlockPosArgument.blockPos()).executes(
                                            context -> industrialStage(context, VisualIndustrialFixtures::ebf)
                                        )
                                    )
                                )
                            )
                            .then(
                                Commands.literal("hud")
                                        .then(Commands.literal("compact").executes(context -> setCompact(true)))
                                        .then(Commands.literal("detail").executes(context -> setCompact(false)))
                                        .then(Commands.literal("hide").executes(context -> setHudVisible(false)))
                                        .then(Commands.literal("show").executes(context -> setHudVisible(true)))
                            ).then(flowCommand())
                            .then(
                                Commands.literal("inspect")
                                        .then(Commands.literal("clear").executes(context -> clearInspect())).then(
                                            Commands.argument("origin", BlockPosArgument.blockPos())
                                                    .executes(VisualFixtureCommand::inspectAt)
                                        )
                            )
                            .then(
                                Commands.literal("tutorial")
                                        .then(Commands.literal("hide").executes(context -> suppressTutorial()))
                            )
                            .then(
                                Commands.literal("highlights")
                                        .then(Commands.literal("hide").executes(context -> setHighlightsVisible(false)))
                                        .then(Commands.literal("show").executes(context -> setHighlightsVisible(true)))
                            ).then(Commands.literal("showcase").executes(VisualFixtureCommand::showcase))
                            .then(Commands.literal("clear").executes(VisualFixtureCommand::clear))
                            .then(Commands.literal("clearall").executes(context -> {
                                return VisualFixtureSupport.clearAll(context.getSource().getLevel()) ? 1 : 0;
                            }))
                );
    }

    private static int showcase(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        if (!VisualShowcaseFixtures.stage(level)) {
            return 0;
        }
        VisualClientState.setCompact(true);
        VisualClientState.setHudEnabled(true);
        VisualClientState.setHighlightsEnabled(true);
        VisualInspectState.clear();
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level, 0.5D, VisualShowcaseFixtures.spawn.y(), 0.5D, 180.0F, 10.0F);
        }
        return 1;
    }

    private static int stage(CommandContext<CommandSourceStack> context, BiPredicate<ServerLevel, LatticePoint> stage) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
        return stage.test(level, LatticePoint.of(pos.getX(), pos.getY(), pos.getZ())) ? 1 : 0;
    }

    private static int move(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
        return VisualFixtureSupport.moveFixture(level, LatticePoint.of(pos.getX(), pos.getY(), pos.getZ()), 1, 0, 0)
                ? 1
                : 0;
    }

    private static int hierarchyStage(
        CommandContext<CommandSourceStack> context,
        Function<LatticePoint, VisualHierarchyFixtures.Fixture> factory
    ) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
        LatticePoint origin = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        return VisualHierarchyFixtures.stage(level, factory.apply(origin)) ? 1 : 0;
    }

    private static int industrialStage(
        CommandContext<CommandSourceStack> context,
        Function<LatticePoint, VisualIndustrialFixtures.Fixture> factory
    ) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
        return VisualIndustrialFixtures.stage(level, factory.apply(LatticePoint.of(pos.getX(), pos.getY(), pos.getZ())))
                ? 1
                : 0;
    }

    private static int scaleStage(CommandContext<CommandSourceStack> context, int count, LatticePoint origin) {
        ServerLevel level = context.getSource().getLevel();
        return VisualHierarchyFixtures.stage(level, VisualHierarchyFixtures.scale(origin, count)) ? 1 : 0;
    }

    private static int complexStage(
        CommandContext<CommandSourceStack> context,
        Function<LatticePoint, VisualComplexFixtures.Fixture> factory
    ) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
        LatticePoint origin = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        return VisualComplexFixtures.stage(level, factory.apply(origin)) ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flowCommand() {
        LiteralArgumentBuilder<CommandSourceStack> flow = Commands.literal("flow");
        for (VisualFlowModel.Phase phase : VisualFlowModel.Phase.values()) {
            flow = flow.then(Commands.literal(phase.name()).executes(context -> setFlow(phase)));
        }
        return flow;
    }

    private static int setFlow(VisualFlowModel.Phase phase) {
        VisualClientState.advanceFlow(phase);
        return 1;
    }

    private static int suppressTutorial() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            minecraft.options.tutorialStep = TutorialSteps.NONE;
            minecraft.getToasts().clear();
        });
        return 1;
    }

    private static int inspectAt(CommandContext<CommandSourceStack> context) {
        BlockPos pos = BlockPosArgument.getBlockPos(context, "origin");
        VisualInspectState.inspectAt(LatticePoint.of(pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }

    private static int clearInspect() {
        VisualInspectState.clear();
        return 1;
    }

    private static int setHighlightsVisible(boolean visible) {
        VisualClientState.setHighlightsEnabled(visible);
        return 1;
    }

    private static int setHudVisible(boolean visible) {
        VisualClientState.setHudEnabled(visible);
        return 1;
    }

    private static int setCompact(boolean compact) {
        VisualClientState.setCompact(compact);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        return VisualFixtureSupport.clear(context.getSource().getLevel()) ? 1 : 0;
    }
}
