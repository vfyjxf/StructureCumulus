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
    static final FindingCode propertyMismatch = code(6);
    static final FindingCode stateMismatch = code(7);
    static final FindingCode tagMismatch = code(8);
    static final FindingCode fluidMismatch = code(9);
    static final FindingCode valueNotCaptured = code(10);

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

    static final CodeEntry propertyMismatchEntry = entry(
        propertyMismatch,
        Severity.error,
        "A block property does not match the declaration.",
        "The captured block state has another value for the declared property, or the block has no such property. "
                + "Check the property name and value against the block."
    );

    static final CodeEntry stateMismatchEntry = entry(
        stateMismatch,
        Severity.error,
        "A block state does not match the declaration.",
        "The captured block state differs from the state resolved at compile time. Check the block id and every "
                + "declared property."
    );

    static final CodeEntry tagMismatchEntry = entry(
        tagMismatch,
        Severity.error,
        "A registry tag does not match the declaration.",
        "The captured block state, fluid state or entity type is not in the declared tag. Check the registry, the "
                + "tag id and the loaded tag data."
    );

    static final CodeEntry fluidMismatchEntry = entry(
        fluidMismatch,
        Severity.error,
        "A fluid does not match the declaration.",
        "The captured fluid is not the declared fluid, or the block at the cell is not that fluid's legacy block. A "
                + "waterlogged block is a block state, not a fluid."
    );

    static final CodeEntry valueNotCapturedEntry = entry(
        valueNotCaptured,
        Severity.error,
        "A host snapshot value was not captured.",
        "The typed read answered no value for the checked cell. The expectation is unreadable, never a mismatch. "
                + "Check the capture bounds and the snapshot the check runs against."
    );

    static Catalog<CodeEntry> catalog() {
        Catalog.Builder<CodeEntry> builder = Catalog.builder(NamespacedId.of("structurecumulus", "codes"));
        for (CodeEntry entry : entries()) {
            builder.put(NamespacedId.of("structurecumulus", entry.code().text().toLowerCase(Locale.ROOT)), entry);
        }
        return builder.build();
    }

    static List<CodeEntry> entries() {
        return List.of(
            notCapturedEntry,
            unreadableTraitEntry,
            cutEntry,
            orientationEntry,
            overCapEntry,
            propertyMismatchEntry,
            stateMismatchEntry,
            tagMismatchEntry,
            fluidMismatchEntry,
            valueNotCapturedEntry
        );
    }

    private static FindingCode code(int number) {
        return FindingCode.of(FindingCode.Family.mc, number);
    }

    private static CodeEntry entry(FindingCode code, Severity severity, String summary, String body) {
        return new CodeEntry(code, severity, new Explanation(code, summary, body, List.of()));
    }

    private AdapterCodes() {}
}
