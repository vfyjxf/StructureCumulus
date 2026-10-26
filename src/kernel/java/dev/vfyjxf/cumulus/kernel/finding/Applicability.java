package dev.vfyjxf.cumulus.kernel.finding;

/** Machine-applicable first. */
public enum Applicability {
    MACHINE_APPLICABLE, MAYBE_INCORRECT, HAS_PLACEHOLDERS, UNSPECIFIED;

    public int rank() {
        return ordinal();
    }
}
