package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** What the host wants to form: an owner, a siting and the blueprint/document identity. */
@NullMarked
public record StructureSpec(OwnerId owner, Siting siting, NamespacedId structure) {

    public StructureSpec {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(structure, "structure");
    }
}
