package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** One focus paired with one expectation over one bay. */
public record Check<T>(
    NamespacedId id,
    Bay bay,
    Focus<T> focus,
    Expectation<T> expectation,
    @Nullable String description
) {

    public Check {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(expectation, "expectation");
        if (!focus.subject().equals(expectation.subject())) {
            throw new IllegalArgumentException("SC-CHK-0001: focus and expectation subject differ");
        }
    }

    public static <T> Check<T> of(NamespacedId id, Bay bay, Focus<T> focus, Expectation<T> expectation) {
        return new Check<>(id, bay, focus, expectation, null);
    }

    public static <T> Check<T> of(
        NamespacedId id,
        Bay bay,
        Focus<T> focus,
        Expectation<T> expectation,
        @Nullable String description
    ) {
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
        return new CheckResult(Reply.unreadable(missing.findings()), List.of(), FindingBundle.empty());
    }

    static FindingBundle missing(FocusContext ctx, String message) {
        Finding finding = Finding.builder(FindingCode.of(FindingCode.Family.occ, 1), Severity.error).phase(ctx.phase())
                .message(message).label(Label.primary(ctx.locus(), message)).build();
        List<Finding> findings = new ArrayList<>();
        findings.add(finding);
        return FindingBundle.of(findings);
    }

    static Finding unreadable(Phase phase, Locus locus, String message, @Nullable String note) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(locus, "locus");
        Finding.Builder builder = Finding.builder(Codes.unreadableRead).phase(phase).message(message)
                .label(Label.primary(locus, message));
        if (note != null) {
            builder.note(note);
        }
        return builder.build();
    }

    static Finding relay(Phase phase, ReadRefusal refusal, Locus locus, String message) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(refusal, "refusal");
        Objects.requireNonNull(locus, "locus");
        FindingCode code = refusal.code();
        CodeEntry kernel = kernelEntry(code);
        Finding.Builder builder = kernel == null
                ? Finding.builder(code, Severity.error)
                : Finding.builder(Codes.unreadableRead);
        builder.phase(phase).message(message).label(Label.primary(locus, message)).note("refusal: " + refusal.status());
        if (kernel != null && !code.equals(Codes.unreadableRead.code())) {
            builder.note(
                "the host code " + code.text() + " is a kernel code; " + Codes.unreadableRead.code().text()
                        + " substituted"
            );
        }
        return builder.build();
    }

    static FindingBundle relayAll(
        Phase phase,
        Map<LatticePoint, ReadRefusal> unreadable,
        Function<CuboidSet, Locus> locus,
        String message
    ) {
        Objects.requireNonNull(unreadable, "unreadable");
        Map<ReadRefusal, CuboidSet> groups = new LinkedHashMap<>();
        for (Map.Entry<LatticePoint, ReadRefusal> entry : unreadable.entrySet()) {
            groups.merge(
                Objects.requireNonNull(entry.getValue(), "refusal"),
                CuboidSet.of(Cuboid.of(entry.getKey())),
                CuboidSet::union
            );
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<ReadRefusal, CuboidSet> group : groups.entrySet()) {
            String grouped = message + " (" + group.getValue().volume() + " cell(s))";
            findings.add(relay(phase, group.getKey(), locus.apply(group.getValue()), grouped));
        }
        return FindingBundle.of(findings);
    }

    private static @Nullable CodeEntry kernelEntry(FindingCode code) {
        for (CodeEntry entry : Codes.all) {
            if (entry.code().equals(code)) {
                return entry;
            }
        }
        return null;
    }
}
