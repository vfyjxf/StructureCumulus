package dev.vfyjxf.cumulus.kernel.base;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Ordered and deduplicated; capping is a caller's `filter(options)` and never elides an `ERROR`. */
// FindingOptions, Builder, filter and the SC-DIA-0001 truncation finding are not built yet.
public final class FindingBundle {

    private static final FindingBundle EMPTY = new FindingBundle(List.of());

    private static final Comparator<Entry> ORDER = FindingBundle::compareEntries;

    private final List<Finding> findings;

    private FindingBundle(List<Finding> findings) {
        this.findings = List.copyOf(findings);
    }

    public static FindingBundle empty() {
        return EMPTY;
    }

    public static FindingBundle of(Finding... findings) {
        return of(List.of(findings));
    }

    public static FindingBundle of(List<Finding> findings) {
        if (findings.isEmpty()) {
            return EMPTY;
        }
        List<Entry> entries = new ArrayList<>(findings.size());
        for (int i = 0; i < findings.size(); i++) {
            entries.add(new Entry(findings.get(i), i));
        }
        return new FindingBundle(canonical(entries));
    }

    public FindingBundle plus(Finding finding) {
        List<Entry> entries = new ArrayList<>(findings.size() + 1);
        for (int i = 0; i < findings.size(); i++) {
            entries.add(new Entry(findings.get(i), i));
        }
        entries.add(new Entry(Objects.requireNonNull(finding, "finding"), findings.size()));
        return new FindingBundle(canonical(entries));
    }

    public FindingBundle plus(FindingBundle other) {
        FindingBundle result = this;
        for (Finding finding : other.findings) {
            result = result.plus(finding);
        }
        return result;
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

    public List<Finding> ofCode(FindingCode code) {
        List<Finding> matching = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.code().equals(code)) {
                matching.add(finding);
            }
        }
        return List.copyOf(matching);
    }

    public List<Finding> ofSeverity(Severity severity) {
        List<Finding> matching = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.severity() == severity) {
                matching.add(finding);
            }
        }
        return List.copyOf(matching);
    }

    private static List<Finding> canonical(List<Entry> entries) {
        List<Entry> kept = new ArrayList<>(entries.size());
        Map<Key, Integer> positions = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Key key = Key.of(entry.finding());
            Integer position = positions.get(key);
            if (position == null) {
                positions.put(key, kept.size());
                kept.add(entry);
            } else {
                Entry survivor = kept.get(position);
                kept.set(position, new Entry(survivor.finding().mergeDuplicate(entry.finding()), survivor.insertion()));
            }
        }
        kept.sort(ORDER);
        List<Finding> ordered = new ArrayList<>(kept.size());
        for (Entry entry : kept) {
            ordered.add(entry.finding());
        }
        return ordered;
    }

    private static int compareEntries(Entry left, Entry right) {
        Finding a = left.finding();
        Finding b = right.finding();
        int result = Integer.compare(a.severity().rank(), b.severity().rank());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(a.stage().ordinal(), b.stage().ordinal());
        if (result != 0) {
            return result;
        }
        result = a.code().compareTo(b.code());
        if (result != 0) {
            return result;
        }
        result = compareLocusKeys(left.locusOrder(), right.locusOrder());
        if (result != 0) {
            return result;
        }
        return Integer.compare(left.insertion(), right.insertion());
    }

    private static int compareLocusKeys(int[] left, int[] right) {
        for (int i = 0; i < left.length; i++) {
            int result = Integer.compare(left[i], right[i]);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FindingBundle bundle && findings.equals(bundle.findings);
    }

    @Override
    public int hashCode() {
        return findings.hashCode();
    }

    @Override
    public String toString() {
        return "FindingBundle" + findings;
    }

    private record Entry(Finding finding, int insertion, int[] locusOrder) {

        Entry(Finding finding, int insertion) {
            this(finding, insertion, Finding.locusOrder(finding.primaryLocus()));
        }
    }

    private record LabelKey(LabelRole role, Locus locus) {
    }

    private record Key(Severity severity, Stage stage, FindingCode code,
                       List<LabelKey> labels, List<String> notes) {

        static Key of(Finding finding) {
            List<LabelKey> labels = new ArrayList<>(finding.labels().size());
            for (Label label : finding.labels()) {
                labels.add(new LabelKey(label.role(), label.locus()));
            }
            List<String> notes = new ArrayList<>(finding.notes().size());
            for (Note note : finding.notes()) {
                notes.add(note.message());
            }
            return new Key(finding.severity(), finding.stage(), finding.code(),
                    List.copyOf(labels), List.copyOf(notes));
        }
    }
}
