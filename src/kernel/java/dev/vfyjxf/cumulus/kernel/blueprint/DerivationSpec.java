package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A reference to a deriver, never the deriver itself: the id resolves through {@code Catalogs.derivers}
 * when the plan is compiled.
 *
 * <p>Deviation: {@code args} is a {@code Map<String, Object>} placeholder until the {@code Arguments}
 * value exists; values are assumed immutable by the caller.
 */
public record DerivationSpec(NamespacedId deriver, @Nullable Map<String, Object> args,
                             NamespacedId output, Stage stage, int order)
        implements Comparable<DerivationSpec> {

    public DerivationSpec {
        Objects.requireNonNull(deriver, "deriver");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(stage, "stage");
        if (order < 0) {
            throw new IllegalArgumentException("B-6: derivation " + deriver + " has negative order " + order);
        }
        args = args == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    /** Uniqueness is {@code (stage, order)} inside a blueprint, so order sorts derivation intent. */
    @Override
    public int compareTo(DerivationSpec other) {
        int byStage = Integer.compare(stage.ordinal(), other.stage.ordinal());
        if (byStage != 0) {
            return byStage;
        }
        int byOrder = Integer.compare(order, other.order);
        return byOrder != 0 ? byOrder : deriver.compareTo(other.deriver);
    }
}
