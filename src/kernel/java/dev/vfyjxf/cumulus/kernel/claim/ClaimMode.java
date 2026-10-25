package dev.vfyjxf.cumulus.kernel.claim;

public enum ClaimMode {
    EXCLUSIVE,
    SHARED,
    RESERVED;

    public boolean excludes() {
        return this == EXCLUSIVE;
    }
}
