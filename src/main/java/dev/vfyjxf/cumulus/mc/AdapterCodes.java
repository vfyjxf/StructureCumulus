package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Explanation;
import dev.vfyjxf.cumulus.kernel.finding.Severity;

import java.util.List;
import java.util.Locale;

/** The adapter's own codes: they carry the host severity and explanation, and never enter {@code Codes.all}. */
final class AdapterCodes {

    static final FindingCode notCaptured = code(1);
    static final FindingCode unreadableTrait = code(2);
    static final FindingCode cut = code(3);
    static final FindingCode orientation = code(4);
    static final FindingCode overCap = code(5);

    static final CodeEntry notCapturedEntry = entry(
        notCaptured,
        Severity.error,
        "The root could not be captured.",
        "The root chunk or section was not captured. The capture arm stays Unavailable; the host code is its finding."
    );

    static final CodeEntry unreadableTraitEntry = entry(
        unreadableTrait,
        Severity.error,
        "A trait or block-entity read is unreadable.",
        "The block state, block entity or trait slot could not be read. The refusal carries this host code."
    );

    static final CodeEntry cutEntry = entry(
        cut,
        Severity.warning,
        "A nested extent was cut.",
        "A nested child was cut by depth, budget, cycle, an unloaded chunk or a declaration that does not fit "
                + "its parent frame. The nested body is kept and nested(cell) answers null."
    );

    static final CodeEntry orientationEntry = entry(
        orientation,
        Severity.warning,
        "A captured block state cannot express the siting orientation.",
        "The siting orientation cannot be represented by a captured block's state properties. The state is "
                + "reported, never silently replaced; the check still judges the value."
    );

    static final CodeEntry overCapEntry = entry(
        overCap,
        Severity.error,
        "The root capture is over the element ceiling.",
        "The root volume is over the policy's element ceiling. No partial root is built; the arm stays TooLarge."
    );

    static Catalog<CodeEntry> catalog() {
        Catalog.Builder<CodeEntry> builder = Catalog.builder(NamespacedId.of("structurecumulus", "codes"));
        for (CodeEntry entry : entries()) {
            builder.put(NamespacedId.of("structurecumulus", entry.code().text().toLowerCase(Locale.ROOT)), entry);
        }
        return builder.build();
    }

    static List<CodeEntry> entries() {
        return List.of(notCapturedEntry, unreadableTraitEntry, cutEntry, orientationEntry, overCapEntry);
    }

    private static FindingCode code(int number) {
        return FindingCode.of(FindingCode.Family.mc, number);
    }

    private static CodeEntry entry(FindingCode code, Severity severity, String summary, String body) {
        return new CodeEntry(code, severity, new Explanation(code, summary, body, List.of()));
    }

    private AdapterCodes() {}
}
