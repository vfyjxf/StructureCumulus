package dev.vfyjxf.cumulus.kernel.engine;

import java.util.Objects;

/** A slot's coalescing token; the host assigns it, so the kernel only orders tickets. */
public record Ticket(long serial) implements Comparable<Ticket> {

    public Ticket {
        if (serial < 0L) {
            throw new IllegalArgumentException("serial must not be negative: " + serial);
        }
    }

    public static Ticket of(long serial) {
        return new Ticket(serial);
    }

    public Ticket next() {
        if (serial == Long.MAX_VALUE) {
            throw new IllegalStateException("ticket overflow");
        }
        return new Ticket(serial + 1L);
    }

    public boolean isNewerThan(Ticket other) {
        Objects.requireNonNull(other, "other");
        return serial > other.serial;
    }

    @Override
    public int compareTo(Ticket other) {
        return Long.compare(serial, other.serial);
    }

    @Override
    public String toString() {
        return "ticket#" + serial;
    }
}
