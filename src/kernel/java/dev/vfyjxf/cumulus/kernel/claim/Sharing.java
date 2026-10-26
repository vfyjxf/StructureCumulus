package dev.vfyjxf.cumulus.kernel.claim;

/** Whether a claim excludes another over the same cell. */
public enum Sharing {
    EXCLUSIVE, SHARED;

    public boolean blocks() {
        return this == EXCLUSIVE;
    }
}
