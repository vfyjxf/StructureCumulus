package dev.vfyjxf.cumulus.kernel.finding;

/** Machine-applicable first. */
public enum Applicability {
    machineApplicable, maybeIncorrect, hasPlaceholders, unspecified;

    public int rank() {
        return ordinal();
    }

    public String wireName() {
        return switch (this) {
            case machineApplicable -> "MACHINE_APPLICABLE";
            case maybeIncorrect -> "MAYBE_INCORRECT";
            case hasPlaceholders -> "HAS_PLACEHOLDERS";
            case unspecified -> "UNSPECIFIED";
        };
    }
}
