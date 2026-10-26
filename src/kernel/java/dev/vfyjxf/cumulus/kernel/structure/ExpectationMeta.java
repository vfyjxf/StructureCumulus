package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Catalog identity, report hint and the keys a test needs. */
public record ExpectationMeta(NamespacedId id, int specificity, Set<ContextKey<?>> requires, FindingCode mismatchCode) {

    public ExpectationMeta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requires, "requires");
        Objects.requireNonNull(mismatchCode, "mismatchCode");
        if (specificity < 0) {
            throw new IllegalArgumentException("specificity >= 0");
        }
        TreeSet<ContextKey<?>> sorted = new TreeSet<>(
            Comparator.comparing((ContextKey<?> key) -> key.id()).thenComparing(key -> key.type().getName())
        );
        sorted.addAll(requires);
        requires = Collections.unmodifiableSet(sorted);
    }

    public static ExpectationMeta anonymous() {
        return new ExpectationMeta(
            NamespacedId.of("anonymous"),
            0,
            Set.of(),
            FindingCode.of(FindingCode.Family.exp, 1)
        );
    }

    public static ExpectationMeta of(NamespacedId id, int specificity, FindingCode mismatchCode) {
        return new ExpectationMeta(id, specificity, Set.of(), mismatchCode);
    }
}
