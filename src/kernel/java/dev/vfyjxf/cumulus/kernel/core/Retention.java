package dev.vfyjxf.cumulus.kernel.core;

/** How long a context value lives. */
public enum Retention {
    /** Readable by later units of one attempt only. */
    MATCH,
    /** Published into the instance's context. */
    INSTANCE
}
