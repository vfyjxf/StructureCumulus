package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** What a test declares about itself: its id, its tie-break weight, its coverage demand, its code. */
public record ExpectationMeta(NamespacedId id, int specificity, Set<ContextKey<?>> requires,
                              FindingCode mismatchCode) {

    private static final NamespacedId ANONYMOUS_ID =
            NamespacedId.of(NamespacedId.DEFAULT_NAMESPACE, "anonymous");
    private static final ExpectationMeta ANONYMOUS = new ExpectationMeta(ANONYMOUS_ID, 0, Set.of(),
            FindingCode.of(FindingCode.Family.EXP, 1));

    public ExpectationMeta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requires, "requires");
        Objects.requireNonNull(mismatchCode, "mismatchCode");
        if (specificity < 0) {
            throw new IllegalArgumentException("specificity must not be negative: " + specificity);
        }
        requires = Collections.unmodifiableSet(new TreeSet<>(requires));
    }

    public static ExpectationMeta anonymous() {
        return ANONYMOUS;
    }

    public static ExpectationMeta of(NamespacedId id, int specificity, FindingCode mismatchCode) {
        return new ExpectationMeta(id, specificity, Set.of(), mismatchCode);
    }
}
