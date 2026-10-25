package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.Label;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface ContextView {

    ContextTier tier();

    /** Null means no frame at or below {@link #tier()} publishes a value for this key; never "unreadable". */
    <T> @Nullable T get(ContextKey<T> key);

    <T> Outcome<T> require(ContextKey<T> key);

    boolean has(ContextKey<?> key);

    Set<ContextKey<?>> keys();

    ContextView at(ContextTier tier);
}

final class ContextFindings {

    static final FindingCode CONFLICTING_VALUE = FindingCode.of(FindingCode.Family.CTX, 107);
    static final FindingCode STALE_BASE = FindingCode.of(FindingCode.Family.CTX, 108);
    static final FindingCode KEY_ABSENT = FindingCode.of(FindingCode.Family.CTX, 202);
    static final FindingCode NON_FINITE_TOTAL = FindingCode.of(FindingCode.Family.CTX, 301);

    private ContextFindings() {
    }

    static Finding conflictingValue(InstanceId instance, ContextKey<?> key, ContextTier tier) {
        String message = "two different values for " + key.id() + " in one frame at " + tier;
        return Finding.builder(CONFLICTING_VALUE).severity(Severity.ERROR).message(message)
                .label(Locus.key(instance, key.id()), message).stage(stage(tier)).build();
    }

    static Finding staleBase(InstanceId instance, long base, long current) {
        return Finding.builder(STALE_BASE).severity(Severity.ERROR)
                .message("publish for " + instance + " based on revision " + base + " but the store is at " + current)
                .stage(Stage.SETTLE).build();
    }

    static Finding keyAbsent(InstanceId instance, ContextKey<?> key, ContextTier tier) {
        String message = "context key " + key.id() + " of " + instance + " has no value at " + tier;
        return Finding.builder(KEY_ABSENT).severity(Severity.ERROR).message(message)
                .label(Locus.key(instance, key.id()), message).stage(stage(tier)).build();
    }

    static Finding nonFiniteTotal(AugmentKey key) {
        return Finding.builder(NON_FINITE_TOTAL).severity(Severity.WARNING)
                .message("augment " + key.id() + " folded to a non-finite total").stage(Stage.SETTLE).build();
    }

    static Stage stage(ContextTier tier) {
        return switch (tier) {
            case BLUEPRINT -> Stage.DESIGN;
            case PROBE -> Stage.PROBE;
            case FORM -> Stage.FORM;
            case SETTLE -> Stage.SETTLE;
        };
    }

    static FindingBundle bundle(Finding finding) {
        return FindingBundle.of(List.of(finding));
    }

    static FindingBundle bundle(List<Finding> findings) {
        return FindingBundle.of(findings);
    }
}
