package dev.vfyjxf.cumulus.kernel.engine;

/** Why an attempt was dropped instead of producing a verdict. */
public enum DropCause {
    /** The watched region changed after capture; the units read a picture that no longer holds. */
    SNAPSHOT_CHANGED,
    /** The capture's world was replaced, or the handle was closed. */
    CAPTURE_INVALID
}
