package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.engine.MatchRequest;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.FindingRenderer;
import dev.vfyjxf.cumulus.kernel.finding.RenderContext;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.NullMarked;

/** The M1 command surface: {@code /cumulus check <id> <origin>}. */
@NullMarked
public final class CumulusCommand {

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(
                    Commands.literal("cumulus")
                            .then(
                                Commands.literal("check").then(
                                    Commands.argument("id", ResourceLocationArgument.id()).then(
                                        Commands.argument("origin", BlockPosArgument.blockPos())
                                                .executes(CumulusCommand::check)
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
        NamespacedId id = id(ResourceLocationArgument.getId(context, "id"));
        Document document = state.kernel().document(id);
        if (document == null) {
            source.sendFailure(Component.literal("no document " + id.text()));
            return 0;
        }
        CompileResult compiled = state.kernel().compile(document);
        Blueprint blueprint = compiled.blueprint();
        if (blueprint == null) {
            print(source, "compile " + id.text() + ": failed", compiled.findings());
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPosArgument.getBlockPos(context, "origin");
        LatticePoint point = LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
        Cuboid bounds = blueprint.bounds().translate(origin.getX(), origin.getY(), origin.getZ());
        if (bounds == null) {
            source.sendFailure(Component.literal("the blueprint bounds leave the world at " + point));
            return 0;
        }
        CaptureResult captured = state.capture(level).capture(new CaptureRequest(bounds, SnapshotPolicy.minimal()));
        if (!(captured instanceof CaptureResult.Captured success)) {
            return captureFailure(source, id, captured);
        }
        try (SnapshotLease lease = success.lease()) {
            Siting siting;
            if (blueprint.anchors().isEmpty()) {
                siting = Siting.of(point, Orientation.identity);
            } else {
                SitingResult resolved = state.kernel().siting(blueprint, point, lease.view());
                if (!(resolved.outcome() instanceof Outcome.Found<Siting> anchored)) {
                    print(source, "siting " + id.text() + ": failed", resolved.findings());
                    return 0;
                }
                siting = anchored.value();
            }
            MatchRequest request = MatchRequest
                    .of(blueprint, siting, lease, state.instance(level), state.owner(), Budget.unbounded(), null);
            MatchResult checked = state.kernel().check(request);
            print(source, summary(id, point, checked), checked.findings());
            return 1;
        }
    }

    private static int captureFailure(CommandSourceStack source, NamespacedId id, CaptureResult result) {
        String reason = switch (result) {
            case CaptureResult.TooLarge tooLarge -> "too large (" + tooLarge.elements() + " elements)";
            case CaptureResult.Unavailable unavailable -> unavailable.status() + " (" + unavailable.code().text() + ")";
            case CaptureResult.Captured ignored -> "captured";
        };
        source.sendFailure(Component.literal("capture " + id.text() + ": " + reason));
        return 0;
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

    private static NamespacedId id(ResourceLocation location) {
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    private CumulusCommand() {}
}
