package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.Objects;

/**
 * What one section of the view answered; a refuse is never air.
 */
public sealed interface SectionSlot
        permits SectionSlot.Air, SectionSlot.Present, SectionSlot.Refused {

    default boolean readable() {
        return this instanceof Air || this instanceof Present;
    }

    record Air(long revision) implements SectionSlot {
    }

    record Present(SectionSnapshot section) implements SectionSlot {

        public Present {
            Objects.requireNonNull(section, "section");
        }
    }

    record Refused(ReadStatus status, NamespacedId code) implements SectionSlot {

        public Refused {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(code, "code");
            if (status.readable()) {
                throw new IllegalArgumentException("a refused slot cannot be readable: " + status);
            }
        }
    }
}
