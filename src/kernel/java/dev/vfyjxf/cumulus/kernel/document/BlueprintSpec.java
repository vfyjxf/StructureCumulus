package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** The data form of one blueprint. */
public record BlueprintSpec(
    NamespacedId id,
    Cuboid bounds,
    Value arguments,
    List<BaySpec> bays,
    List<SeamSpec> seams,
    List<CheckSpec> checks,
    List<ComputationSpec> computations,
    List<KeySpec> keys,
    List<DerivationSpec> derivations,
    List<AnchorSpec> anchors,
    @Nullable OrientationPolicy orientation,
    List<PlacementSpec> placements,
    Map<String, Value> extensions,
    @Nullable SourceSpan source
) {

    public BlueprintSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(bays, "bays");
        Objects.requireNonNull(seams, "seams");
        Objects.requireNonNull(checks, "checks");
        Objects.requireNonNull(computations, "computations");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(derivations, "derivations");
        Objects.requireNonNull(anchors, "anchors");
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(extensions, "extensions");
        bays = List.copyOf(bays);
        seams = List.copyOf(seams);
        checks = List.copyOf(checks);
        computations = List.copyOf(computations);
        keys = List.copyOf(keys);
        derivations = List.copyOf(derivations);
        anchors = List.copyOf(anchors);
        placements = List.copyOf(placements);
        extensions = Collections.unmodifiableMap(new TreeMap<>(extensions));
    }
}
