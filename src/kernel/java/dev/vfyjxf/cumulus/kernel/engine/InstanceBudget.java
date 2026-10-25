package dev.vfyjxf.cumulus.kernel.engine;

import java.util.Objects;

/**
 * The slot's mutable counters; a request holds the reference, so an attempt can charge but never
 * open, reset or widen a ceiling.
 */
public final class InstanceBudget {

    private final Budget limit;
    private Spend spent = Spend.ZERO;

    private InstanceBudget(Budget limit) {
        this.limit = Objects.requireNonNull(limit, "limit");
    }

    public static InstanceBudget of(Budget limit) {
        return new InstanceBudget(limit);
    }

    public static InstanceBudget unbounded() {
        return new InstanceBudget(Budget.unbounded());
    }

    public synchronized Budget limit() {
        return limit;
    }

    public synchronized Spend spend() {
        return spent;
    }

    /** Charges both legs at once, or nothing; a partial charge would make the ledger a lie. */
    public synchronized boolean tryCharge(int cells, int sections) {
        return tryCharge(new Spend(cells, sections));
    }

    public synchronized boolean tryCharge(Spend charge) {
        Objects.requireNonNull(charge, "charge");
        Spend candidate = spent.plus(charge);
        if (!limit.admits(candidate)) {
            return false;
        }
        spent = candidate;
        return true;
    }

    public synchronized Spend remaining() {
        return new Spend(
                limit.cells() - spent.cells(),
                limit.sections() - spent.sections());
    }
}
