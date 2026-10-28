package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** A structure set after compilation: one root blueprint and the compiled satellites in declaration order. */
public record CompiledStructureSet(NamespacedId id, Blueprint root, List<CompiledSatellite> satellites) {

    public CompiledStructureSet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(satellites, "satellites");
        satellites = List.copyOf(satellites);
    }

    public @Nullable CompiledSatellite satellite(NamespacedId id) {
        Objects.requireNonNull(id, "id");
        for (CompiledSatellite satellite : satellites) {
            if (satellite.spec().id().equals(id)) {
                return satellite;
            }
        }
        return null;
    }
}
