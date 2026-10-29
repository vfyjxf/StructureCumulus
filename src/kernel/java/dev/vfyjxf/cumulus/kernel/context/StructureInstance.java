package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** One formed structure: its runtime identity, host spec, latest context and world-frame bounds. */
@NullMarked
public record StructureInstance(InstanceId id, StructureSpec spec, StructureContext context, CuboidSet bounds) {

    public StructureInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(bounds, "bounds");
        if (!context.instance().equals(id)) {
            throw new IllegalArgumentException("the context belongs to another instance");
        }
    }

    public OwnerId owner() {
        return spec.owner();
    }

    public Siting siting() {
        return spec.siting();
    }
}
