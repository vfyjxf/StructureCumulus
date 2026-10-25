package dev.vfyjxf.cumulus.kernel.base;

public enum Applicability {
    MACHINE_APPLICABLE,
    MAYBE_INCORRECT,
    HAS_PLACEHOLDERS,
    UNSPECIFIED;

    public int rank() {
        return ordinal();
    }
}
