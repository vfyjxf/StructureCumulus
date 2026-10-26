package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
/** Caps and floors; errors are never capped. */
public record FindingOptions(int maxTotal, int maxPerCode, boolean includeInfo,
                             boolean includeNotes) {

    public FindingOptions {
        Checks.checkArgument(maxTotal >= 0, "maxTotal >= 0");
        Checks.checkArgument(maxPerCode >= 0, "maxPerCode >= 0");
    }

    public static FindingOptions defaults() {
        return new FindingOptions(Integer.MAX_VALUE, Integer.MAX_VALUE, true, true);
    }

    public FindingOptions narrowedBy(FindingOptions other) {
        Checks.checkNotNull(other, "other");
        return new FindingOptions(
                Math.min(maxTotal, other.maxTotal),
                Math.min(maxPerCode, other.maxPerCode),
                includeInfo && other.includeInfo,
                includeNotes && other.includeNotes);
    }

    public FindingOptions withMaxTotal(int maxTotal) {
        return new FindingOptions(maxTotal, maxPerCode, includeInfo, includeNotes);
    }

    public FindingOptions withMaxPerCode(int maxPerCode) {
        return new FindingOptions(maxTotal, maxPerCode, includeInfo, includeNotes);
    }
}
