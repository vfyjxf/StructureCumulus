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
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
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

import java.util.ArrayList;
import java.util.List;

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

    static SnapshotPolicy policy(Blueprint blueprint, Siting siting) {
        return policy(blueprint.reads(), siting);
    }

    static SnapshotPolicy policy(ReadScope reads, Siting siting) {
        return policy(reads, windows(reads.windows(), siting));
    }

    static SnapshotPolicy policy(ReadScope reads, List<EntityWindow> windows) {
        return new SnapshotPolicy(maxElements, SnapshotPolicy.defaultMaxDepth, reads.kinds(), reads.traits(), windows);
    }

    static List<EntityWindow> windows(List<EntityWindow> local, Siting siting) {
        List<EntityWindow> mapped = new ArrayList<>(local.size());
        for (EntityWindow window : local) {
            Cuboid world = siting.toWorld(window.cells());
            if (world != null) {
                mapped.add(new EntityWindow(world, window.type()));
            }
        }
        return mapped;
    }

    static @Nullable Captured capture(
        ServerState state,
        CommandSourceStack source,
        NamespacedId id,
        Cuboid bounds,
        SnapshotPolicy policy,
        @Nullable Orientation orientation
    ) {
        CaptureResult result = state.capture(source.getLevel())
                .capture(new CaptureRequest(bounds, policy), orientation);
        if (result instanceof CaptureResult.Captured captured) {
            return new Captured(captured.lease(), captured.report());
        }
        String reason = switch (result) {
            case CaptureResult.TooLarge tooLarge -> "too large (" + tooLarge.elements() + " elements)";
            case CaptureResult.Unavailable unavailable -> unavailable.status() + " (" + unavailable.code().text() + ")";
            case CaptureResult.Captured ignored -> "captured";
        };
        source.sendFailure(Component.literal("capture " + id.text() + ": " + reason));
        CaptureReport report = switch (result) {
            case CaptureResult.TooLarge tooLarge -> tooLarge.report();
            case CaptureResult.Unavailable unavailable -> unavailable.report();
            case CaptureResult.Captured captured -> captured.report();
        };
        printCaptureFindings(source, id, report, state.kernel());
        return null;
    }

    static void printCaptureFindings(
        CommandSourceStack source,
        NamespacedId id,
        CaptureReport report,
        StructureKernel kernel
    ) {
        if (report.isEmpty()) {
            return;
        }
        FindingBundle findings = CaptureReports.relay(report, kernel.catalogs().codes());
        printFindings(source, "capture " + id.text() + ": findings", findings);
    }

    static @Nullable Siting siting(
        CommandSourceStack source,
        NamespacedId id,
        Blueprint blueprint,
        LatticePoint origin,
        WorldView view
    ) {
        if (blueprint.anchors().isEmpty()) {
            return Siting.of(origin, Orientation.identity);
        }
        SitingResult resolved = McAnchors.resolve(blueprint, origin, view);
        if (resolved.outcome() instanceof Outcome.Found<Siting> found) {
            return found.value();
        }
        printFindings(source, "siting " + id.text() + ": failed", resolved.findings());
        return null;
    }

    static @Nullable Cuboid sitedBounds(Blueprint blueprint, Siting siting) {
        return siting.toWorld(blueprint.bounds());
    }

    static @Nullable Cuboid anchorCell(LatticePoint origin) {
        try {
            return Cuboid.of(origin);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    /**
     * Resolves the siting of an anchorless blueprint as identity at the origin and of an anchored one
     * from the minimal anchor capture, closing that capture before the caller captures the sited bounds.
     */
    static @Nullable Siting rootSiting(
        ServerState state,
        CommandSourceStack source,
        NamespacedId id,
        Blueprint blueprint,
        LatticePoint origin
    ) {
        if (blueprint.anchors().isEmpty()) {
            return Siting.of(origin, Orientation.identity);
        }
        Cuboid anchorCell = anchorCell(origin);
        if (anchorCell == null) {
            source.sendFailure(Component.literal("the anchor cell leaves the lattice at " + origin));
            return null;
        }
        Captured anchor = capture(state, source, id, anchorCell, SnapshotPolicy.minimal(), null);
        if (anchor == null) {
            return null;
        }
        try (SnapshotLease phase1 = anchor.lease()) {
            return siting(source, id, blueprint, origin, phase1.view());
        }
    }

    static @Nullable Attempt attempt(
        ServerState state,
        CommandSourceStack source,
        NamespacedId id,
        Blueprint blueprint,
        LatticePoint origin
    ) {
        if (blueprint.anchors().isEmpty()) {
            Siting siting = Siting.of(origin, Orientation.identity);
            Cuboid bounds = worldBounds(blueprint, origin, source);
            if (bounds == null) {
                return null;
            }
            Captured captured = capture(state, source, id, bounds, policy(blueprint, siting), null);
            return captured == null ? null : new Attempt(bounds, siting, captured.lease(), captured.report());
        }
        Siting siting = rootSiting(state, source, id, blueprint, origin);
        if (siting == null) {
            return null;
        }
        Cuboid bounds = sitedBounds(blueprint, siting);
        if (bounds == null) {
            source.sendFailure(Component.literal("the sited blueprint bounds leave the world at " + origin));
            return null;
        }
        Captured captured = capture(
            state,
            source,
            id,
            bounds,
            policy(blueprint, siting),
            siting.placement().orientation()
        );
        return captured == null ? null : new Attempt(bounds, siting, captured.lease(), captured.report());
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

    /** One prepared capture: the world bounds, the resolved siting, the lease and the host report. */
    record Attempt(Cuboid bounds, Siting siting, SnapshotLease lease, CaptureReport report) implements AutoCloseable {

        @Override
        public void close() {
            lease.close();
        }
    }

    record Captured(SnapshotLease lease, CaptureReport report) {}

    private CommandSupport() {}
}
