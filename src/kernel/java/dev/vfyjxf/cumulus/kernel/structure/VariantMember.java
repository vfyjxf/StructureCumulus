package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One member of a compiled variant group: the blueprint document id and the label its declaration
 * carried. Both halves are opaque data — the kernel never reads, formats or localizes them
 * (design 15 §12, D-98).
 */
public record VariantMember(NamespacedId id, @Nullable String label) {

    public VariantMember {
        Objects.requireNonNull(id, "id");
    }
}
