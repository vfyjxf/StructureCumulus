package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
/** One focus paired with one expectation over one bay. */
public record Check<T>(NamespacedId id, Bay bay, Focus<T> focus, Expectation<T> expectation,
                       @Nullable String description) {

    public Check {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(expectation, "expectation");
        if (!focus.subject().equals(expectation.subject())) {
            throw new IllegalArgumentException("SC-CHK-0001: focus and expectation subject differ");
        }
    }

    public static <T> Check<T> of(NamespacedId id, Bay bay, Focus<T> focus,
                                  Expectation<T> expectation) {
        return new Check<>(id, bay, focus, expectation, null);
    }

    public static <T> Check<T> of(NamespacedId id, Bay bay, Focus<T> focus,
                                  Expectation<T> expectation, @Nullable String description) {
        return new Check<>(id, bay, focus, expectation, description);
    }

    public ReadScope reads() {
        Set<ContextKey<?>> required = expectation.meta().requires();
        return focus.reads(bay.cells()).union(expectation.reads()).withKeys(required);
    }

    public CheckResult run(FocusContext ctx) {
        FocusResult<T> taken = focus.take(ctx);
        if (taken instanceof FocusResult.Taken<T> found) {
            Reply reply = expectation.test(ExpectationContext.of(ctx, found.value(), found.subjects()));
            return new CheckResult(reply, found.subjects(), found.notes());
        }
        FocusResult.Unavailable<T> missing = (FocusResult.Unavailable<T>) taken;
        return new CheckResult(Reply.unreadable(missing.findings()), List.of(),
                FindingBundle.empty());
    }

    static FindingBundle missing(FocusContext ctx, String message) {
        Finding finding = Finding.builder(FindingCode.of(FindingCode.Family.OCC, 1), Severity.ERROR)
                .phase(ctx.phase())
                .message(message)
                .label(Label.primary(ctx.locus(), message))
                .build();
        List<Finding> findings = new ArrayList<>();
        findings.add(finding);
        return FindingBundle.of(findings);
    }
}
