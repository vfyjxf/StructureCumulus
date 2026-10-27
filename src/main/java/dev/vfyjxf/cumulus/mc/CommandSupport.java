package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.context.CommandContext;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.FindingRenderer;
import dev.vfyjxf.cumulus.kernel.finding.RenderContext;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The helpers shared by the {@code /cumulus} subcommands: arguments, documents, capture, siting and output. */
@NullMarked
final class CommandSupport {

    private static final int maxElements = 1_000_000;

    static NamespacedId id(CommandContext<CommandSourceStack> context, String name) {
        ResourceLocation location = ResourceLocationArgument.getId(context, name);
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    static @Nullable LatticePoint point(BlockPos position, CommandSourceStack source) {
        if (!LatticePoint.inLattice(position.getX(), position.getY(), position.getZ())) {
            source.sendFailure(Component.literal("the position is outside the lattice: " + position.toShortString()));
            return null;
        }
        return LatticePoint.of(position.getX(), position.getY(), position.getZ());
    }

    static @Nullable Document document(StructureKernel kernel, CommandSourceStack source, NamespacedId id) {
        Document document = kernel.document(id);
        if (document == null) {
            source.sendFailure(Component.literal("no document " + id.text()));
        }
        return document;
    }

    static @Nullable Blueprint blueprint(StructureKernel kernel, CommandSourceStack source, NamespacedId id) {
        Document document = document(kernel, source, id);
        if (document == null) {
            return null;
        }
        CompileResult compiled = kernel.compile(document);
        Blueprint blueprint = compiled.blueprint();
        if (blueprint == null) {
            printFindings(source, "compile " + id.text() + ": failed", compiled.findings());
        }
        return blueprint;
    }

    static @Nullable Cuboid worldBounds(Blueprint blueprint, LatticePoint origin, CommandSourceStack source) {
        Cuboid bounds = blueprint.bounds().translate(origin.x(), origin.y(), origin.z());
        if (bounds == null) {
            source.sendFailure(Component.literal("the blueprint bounds leave the world at " + origin));
        }
        return bounds;
    }

    static SnapshotPolicy policy(Blueprint blueprint) {
        ReadScope reads = blueprint.reads();
        return new SnapshotPolicy(maxElements, reads.depth(), reads.kinds(), reads.traits(), reads.windows());
    }

    static @Nullable SnapshotLease capture(
        ServerState state,
        CommandSourceStack source,
        NamespacedId id,
        Cuboid bounds,
        SnapshotPolicy policy
    ) {
        CaptureResult result = state.capture(source.getLevel()).capture(new CaptureRequest(bounds, policy));
        if (result instanceof CaptureResult.Captured captured) {
            return captured.lease();
        }
        String reason = switch (result) {
            case CaptureResult.TooLarge tooLarge -> "too large (" + tooLarge.elements() + " elements)";
            case CaptureResult.Unavailable unavailable -> unavailable.status() + " (" + unavailable.code().text() + ")";
            case CaptureResult.Captured ignored -> "captured";
        };
        source.sendFailure(Component.literal("capture " + id.text() + ": " + reason));
        return null;
    }

    static @Nullable Siting siting(
        StructureKernel kernel,
        CommandSourceStack source,
        NamespacedId id,
        Blueprint blueprint,
        LatticePoint origin,
        WorldView view
    ) {
        if (blueprint.anchors().isEmpty()) {
            return Siting.of(origin, Orientation.identity);
        }
        SitingResult resolved = kernel.siting(blueprint, origin, view);
        if (resolved.outcome() instanceof Outcome.Found<Siting> found) {
            return found.value();
        }
        printFindings(source, "siting " + id.text() + ": failed", resolved.findings());
        return null;
    }

    static @Nullable Cuboid box(BlockPos from, BlockPos to) {
        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        long maxX = (long) Math.max(from.getX(), to.getX()) + 1;
        long maxY = (long) Math.max(from.getY(), to.getY()) + 1;
        long maxZ = (long) Math.max(from.getZ(), to.getZ()) + 1;
        if (!LatticePoint.inLattice(minX, minY, minZ)
                || maxX > LatticePoint.maxXz
                || maxY > LatticePoint.maxY
                || maxZ > LatticePoint.maxXz) {
            return null;
        }
        return Cuboid.of(minX, minY, minZ, (int) maxX, (int) maxY, (int) maxZ);
    }

    static void printFindings(CommandSourceStack source, String header, FindingBundle findings) {
        source.sendSuccess(() -> Component.literal(header), false);
        if (findings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("no findings"), false);
            return;
        }
        source.sendSuccess(() -> Component.literal(FindingRenderer.human(findings, RenderContext.plain())), false);
    }

    private CommandSupport() {}
}
