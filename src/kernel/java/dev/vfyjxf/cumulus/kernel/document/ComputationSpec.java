package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
/** The data form of a computation. */
public record ComputationSpec(NamespacedId id, NamespacedId kind, Value args,
                              @Nullable CuboidSet region, @Nullable NamespacedId bay,
                              NamespacedId output, Set<NamespacedId> inputs,
                              @Nullable SourceSpan source) {

    public ComputationSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(inputs, "inputs");
        inputs = Set.copyOf(new TreeSet<>(inputs));
    }
}
