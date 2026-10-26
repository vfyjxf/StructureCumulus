package dev.vfyjxf.cumulus.kernel.engine;

/** Why an attempt has no verdict. */
public enum UndecidedReason {
    budgetExhausted, cancelled, unreadable, abstained, noChecks, ledgerUnavailable, ledgerMoved, designDefect
}
