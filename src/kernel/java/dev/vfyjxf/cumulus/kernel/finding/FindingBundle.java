package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.space.KeyLocus;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The only aggregator of findings. */
public final class FindingBundle {

    private static final FindingBundle empty = new FindingBundle(List.of(), 0, 0, FindingOptions.defaults());

    private final List<Finding> findings;
    private final int elided;
    private final int filtered;
    private final FindingOptions options;

    private FindingBundle(List<Finding> findings, int elided, int filtered, FindingOptions options) {
        this.findings = List.copyOf(findings);
        this.elided = elided;
        this.filtered = filtered;
        this.options = options;
    }

    public static FindingBundle empty() {
        return empty;
    }

    public static FindingBundle of(List<Finding> findings) {
        Checks.checkNotNull(findings, "findings");
        Builder builder = builder(FindingOptions.defaults());
        for (Finding finding : findings) {
            builder.add(finding);
        }
        return builder.build();
    }

    public List<Finding> findings() {
        return findings;
    }

    public int size() {
        return findings.size();
    }

    public boolean isEmpty() {
        return findings.isEmpty();
    }

    public @Nullable Finding primary() {
        return findings.isEmpty() ? null : findings.get(0);
    }

    public int elided() {
        return elided;
    }

    public int filtered() {
        return filtered;
    }

    public FindingOptions options() {
        return options;
    }

    public List<Finding> ofCode(FindingCode code) {
        Checks.checkNotNull(code, "code");
        List<Finding> selected = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.code().equals(code)) {
                selected.add(finding);
            }
        }
        return List.copyOf(selected);
    }

    public List<Finding> ofSeverity(Severity severity) {
        Checks.checkNotNull(severity, "severity");
        List<Finding> selected = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.severity() == severity) {
                selected.add(finding);
            }
        }
        return List.copyOf(selected);
    }

    public FindingBundle filter(FindingOptions narrower) {
        Checks.checkNotNull(narrower, "narrower");
        FindingOptions merged = options.narrowedBy(narrower);
        List<Finding> base = new ArrayList<>(findings.size());
        for (Finding finding : findings) {
            if (!finding.code().equals(Codes.bundleTruncated.code())) {
                base.add(finding);
            }
        }
        return assemble(base, merged, elided, filtered, latestPhase(findings));
    }

    public static Builder builder(FindingOptions options) {
        return new BundleBuilder(Checks.checkNotNull(options, "options"));
    }

    private static FindingBundle assemble(
        List<Finding> input,
        FindingOptions options,
        int priorElided,
        int priorFiltered,
        @Nullable Phase fallbackPhase
    ) {
        List<Finding> retained = new ArrayList<>();
        Set<DedupKey> seen = new HashSet<>();
        Phase latest = fallbackPhase;
        int elided = priorElided;
        int filtered = priorFiltered;
        for (Finding finding : input) {
            Checks.checkNotNull(finding, "finding");
            latest = later(latest, finding.phase());
            if (!options.includeInfo() && finding.severity() == Severity.info) {
                filtered++;
                continue;
            }
            DedupKey key = new DedupKey(
                finding.severity(),
                finding.phase(),
                finding.code(),
                finding.labels(),
                finding.notes()
            );
            if (!seen.add(key)) {
                continue;
            }
            retained.add(finding);
        }
        retained.sort(FindingBundle::compareFindings);

        List<Finding> capped = new ArrayList<>(retained.size());
        Map<FindingCode, Integer> perCode = new HashMap<>();
        int total = 0;
        for (Finding finding : retained) {
            if (finding.severity() == Severity.error) {
                capped.add(finding);
                continue;
            }
            int seenForCode = perCode.getOrDefault(finding.code(), 0);
            if (total >= options.maxTotal() || seenForCode >= options.maxPerCode()) {
                elided++;
                continue;
            }
            capped.add(finding);
            perCode.put(finding.code(), seenForCode + 1);
            total++;
        }

        List<Finding> cleaned = new ArrayList<>(capped.size() + 1);
        for (Finding finding : capped) {
            if (options.includeNotes() || finding.notes().isEmpty()) {
                cleaned.add(finding);
            } else {
                cleaned.add(
                    new Finding(
                        finding.code(),
                        finding.severity(),
                        finding.message(),
                        finding.labels(),
                        List.of(),
                        finding.suggestion(),
                        finding.phase()
                    )
                );
            }
        }
        if (elided + filtered > 0) {
            cleaned.add(truncation(elided, filtered, latest == null ? Phase.settle : latest));
        }
        return new FindingBundle(cleaned, elided, filtered, options);
    }

    private static Finding truncation(int elided, int filtered, Phase phase) {
        String message = elided + " elided, " + filtered + " filtered findings were omitted.";
        return Finding.builder(Codes.bundleTruncated).phase(phase).message(message).build();
    }

    private static Phase later(@Nullable Phase current, Phase candidate) {
        if (current == null || candidate.rank() > current.rank()) {
            return candidate;
        }
        return current;
    }

    private static @Nullable Phase latestPhase(List<Finding> findings) {
        Phase latest = null;
        for (Finding finding : findings) {
            latest = later(latest, finding.phase());
        }
        return latest;
    }

    private static int compareFindings(Finding left, Finding right) {
        int bySeverity = Integer.compare(left.severity().rank(), right.severity().rank());
        if (bySeverity != 0) {
            return bySeverity;
        }
        int byPhase = Integer.compare(left.phase().rank(), right.phase().rank());
        if (byPhase != 0) {
            return byPhase;
        }
        int byCode = left.code().compareTo(right.code());
        if (byCode != 0) {
            return byCode;
        }
        return comparePrimary(left, right);
    }

    private static int comparePrimary(Finding left, Finding right) {
        Label leftLabel = left.primaryLabel();
        Label rightLabel = right.primaryLabel();
        if (leftLabel == null && rightLabel == null) {
            return 0;
        }
        if (leftLabel == null) {
            return 1;
        }
        if (rightLabel == null) {
            return -1;
        }
        return compareLoci(leftLabel.locus(), rightLabel.locus());
    }

    private static int compareLoci(Locus left, Locus right) {
        LocusFrame frame = left.frame();
        int byFrame = Integer.compare(frame.rank(), right.frame().rank());
        if (byFrame != 0) {
            return byFrame;
        }
        return switch (frame) {
            case definition -> compareDefinitions(left, right);
            case local, world -> left.cells().compareTo(right.cells());
            case key -> compareKeys(left.key(), right.key());
        };
    }

    private static int compareDefinitions(Locus left, Locus right) {
        if (left.source() == null || right.source() == null) {
            return 0;
        }
        int byDocument = left.source().document().compareTo(right.source().document());
        if (byDocument != 0) {
            return byDocument;
        }
        int byStart = Integer.compare(left.source().start(), right.source().start());
        return byStart != 0 ? byStart : Integer.compare(left.source().end(), right.source().end());
    }

    private static int compareKeys(@Nullable KeyLocus left, @Nullable KeyLocus right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int byInstance = left.instance().compareTo(right.instance());
        return byInstance != 0 ? byInstance : left.key().compareTo(right.key());
    }

    public interface Builder {
        Builder add(Finding finding);
        FindingBundle build();
    }

    private record DedupKey(Severity severity, Phase phase, FindingCode code, List<Label> labels, List<Note> notes) {}

    private static final class BundleBuilder implements Builder {
        private final FindingOptions options;
        private final List<Finding> added = new ArrayList<>();

        private BundleBuilder(FindingOptions options) {
            this.options = options;
        }

        @Override
        public Builder add(Finding finding) {
            added.add(Checks.checkNotNull(finding, "finding"));
            return this;
        }

        @Override
        public FindingBundle build() {
            return assemble(added, options, 0, 0, null);
        }
    }
}
