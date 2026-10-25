package dev.vfyjxf.cumulus.kernel.engine;

/**
 * The slot's retained-bytes ceiling; {@code retainedBytes} is separate from {@code cacheBytes} and a
 * charge that would pass the ceiling is refused whole.
 */
public final class PinBudget {

    private final long limit;
    private long retained;

    private PinBudget(long limit) {
        if (limit < 0L) {
            throw new IllegalArgumentException("limit must not be negative: " + limit);
        }
        this.limit = limit;
    }

    public static PinBudget of(long limit) {
        return new PinBudget(limit);
    }

    public static PinBudget unbounded() {
        return new PinBudget(Long.MAX_VALUE);
    }

    public synchronized long limit() {
        return limit;
    }

    public synchronized long retained() {
        return retained;
    }

    public synchronized long remaining() {
        return limit - retained;
    }

    public synchronized boolean tryCharge(long bytes) {
        if (bytes < 0L) {
            throw new IllegalArgumentException("bytes must not be negative: " + bytes);
        }
        if (bytes > limit - retained) {
            return false;
        }
        retained += bytes;
        return true;
    }
}
