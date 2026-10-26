package dev.vfyjxf.cumulus.kernel.core;

/** How long a context value lives. */
public enum Retention {
    /** Readable by later units of one attempt only. */
    match,
    /** Published into the instance's context. */
    instance
}
