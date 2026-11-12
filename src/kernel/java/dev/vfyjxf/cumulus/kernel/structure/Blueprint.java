package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The immutable compiled template. */
public final class Blueprint {

    private final NamespacedId id;
    private final Cuboid bounds;
    private final List<Bay> bays;
    private final List<Check<?>> checks;
    private final List<Computation<?>> computations;
    private final List<Seam> seams;
    private final List<AnchorSpec> anchors;
    private final @Nullable OrientationPolicy orientation;
    private final List<DerivationSpec> derivations;
    private final Value arguments;
    private final @Nullable String description;
    private final @Nullable SourceSpan source;
    private final Plan plan;
    private final CuboidSet bayCells;
    private final ReadScope reads;

    Blueprint(
        NamespacedId id,
        Cuboid bounds,
        List<Bay> bays,
        List<Check<?>> checks,
        List<Computation<?>> computations,
        List<Seam> seams,
        List<AnchorSpec> anchors,
        @Nullable OrientationPolicy orientation,
        List<DerivationSpec> derivations,
        Value arguments,
        @Nullable String description,
        @Nullable SourceSpan source,
        Plan plan,
        CuboidSet bayCells
    ) {
        this.id = id;
        this.bounds = bounds;
        this.bays = List.copyOf(bays);
        this.checks = List.copyOf(checks);
        this.computations = List.copyOf(computations);
        this.seams = List.copyOf(seams);
        this.anchors = List.copyOf(anchors);
        this.orientation = orientation;
        this.derivations = List.copyOf(derivations);
        this.arguments = arguments;
        this.description = description;
        this.source = source;
        this.plan = plan;
        this.bayCells = bayCells;
        this.reads = computeReads(this.checks, this.computations);
    }

    public static BlueprintBuilder builder(NamespacedId id) {
        return new BlueprintBuilder(id);
    }

    public NamespacedId id() {
        return id;
    }

    public Cuboid bounds() {
        return bounds;
    }

    public List<Bay> bays() {
        return bays;
    }

    public List<Check<?>> checks() {
        return checks;
    }

    public List<Computation<?>> computations() {
        return computations;
    }

    public List<Seam> seams() {
        return seams;
    }

    public List<AnchorSpec> anchors() {
        return anchors;
    }

    public @Nullable OrientationPolicy orientation() {
        return orientation;
    }

    public List<DerivationSpec> derivations() {
        return derivations;
    }

    public Value arguments() {
        return arguments;
    }

    /** The root's carried text, or null when the document declares none (design 16 §3). */
    public @Nullable String description() {
        return description;
    }

    public @Nullable SourceSpan source() {
        return source;
    }

    public Plan plan() {
        return plan;
    }

    public CuboidSet bayCells() {
        return bayCells;
    }

    public ReadScope reads() {
        return reads;
    }

    public @Nullable Bay bay(NamespacedId id) {
        for (Bay bay : bays) {
            if (bay.id().equals(id)) {
                return bay;
            }
        }
        return null;
    }

    public @Nullable Check<?> check(NamespacedId id) {
        for (Check<?> check : checks) {
            if (check.id().equals(id)) {
                return check;
            }
        }
        return null;
    }

    public @Nullable Seam seam(SeamId id) {
        for (Seam seam : seams) {
            if (seam.id().equals(id)) {
                return seam;
            }
        }
        return null;
    }

    public @Nullable AnchorSpec anchor(NamespacedId role) {
        for (AnchorSpec anchor : anchors) {
            if (anchor.role().equals(role)) {
                return anchor;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Blueprint blueprint)) {
            return false;
        }
        return id.equals(blueprint.id)
                && bounds.equals(blueprint.bounds)
                && bays.equals(blueprint.bays)
                && checks.equals(blueprint.checks)
                && computations.equals(blueprint.computations)
                && seams.equals(blueprint.seams)
                && anchors.equals(blueprint.anchors)
                && Objects.equals(orientation, blueprint.orientation)
                && derivations.equals(blueprint.derivations)
                && arguments.equals(blueprint.arguments)
                && Objects.equals(description, blueprint.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            bounds,
            bays,
            checks,
            computations,
            seams,
            anchors,
            orientation,
            derivations,
            arguments,
            description
        );
    }

    @Override
    public String toString() {
        return "Blueprint[" + id + ", bays=" + bays.size() + ", checks=" + checks.size() + ", computations="
                + computations.size() + "]";
    }

    static CuboidSet unionOfCells(List<Bay> bays) {
        List<Cuboid> boxes = new ArrayList<>();
        for (Bay bay : bays) {
            boxes.addAll(bay.cells().decomposition());
        }
        return CuboidSet.of(boxes);
    }

    private static ReadScope computeReads(List<Check<?>> checks, List<Computation<?>> computations) {
        ReadScope scope = ReadScope.none();
        for (Check<?> check : checks) {
            scope = scope.union(check.reads());
        }
        for (Computation<?> computation : computations) {
            scope = scope.union(ReadScope.cells(computation.region()));
        }
        return scope;
    }
}
