package dev.vfyjxf.cumulus.kernel.engine;

/** Why an attempt reached no verdict; the reason names the stop, never the verdict. */
public enum UndecidedReason {
    BUDGET_EXHAUSTED,
    DIRTY_UNSTABLE,
    LEDGER_MOVED,
    SUBJECT_ABSENT,
    LEASE_CLOSED,
    WORKER_FAILED,
    DESIGN_DEFECT
}
