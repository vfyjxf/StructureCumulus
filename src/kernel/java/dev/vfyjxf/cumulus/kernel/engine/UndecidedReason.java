package dev.vfyjxf.cumulus.kernel.engine;

/** Why an attempt has no verdict. */
public enum UndecidedReason {
    BUDGET_EXHAUSTED,
    CANCELLED,
    UNREADABLE,
    ABSTAINED,
    NO_CHECKS,
    LEDGER_UNAVAILABLE,
    LEDGER_MOVED,
    DESIGN_DEFECT
}
