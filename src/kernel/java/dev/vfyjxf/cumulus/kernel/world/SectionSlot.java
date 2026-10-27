package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One section's answer. */
public sealed interface SectionSlot {
    record Air(long revision) implements SectionSlot {
        public Air {
            if (revision < 0) {
                throw new IllegalArgumentException("revision >= 0");
            }
        }
    }

    record Present(SectionSnapshot section) implements SectionSlot {
        public Present {
            Objects.requireNonNull(section, "section");
        }
    }

    record Partial(SectionSnapshot section, Map<LatticePoint, ReadRefusal> unreadable) implements SectionSlot {
        public Partial {
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(unreadable, "unreadable");
            if (unreadable.isEmpty()) {
                throw new IllegalArgumentException("a partial section needs an unreadable cell");
            }
            TreeMap<LatticePoint, ReadRefusal> sorted = new TreeMap<>();
            for (Map.Entry<LatticePoint, ReadRefusal> entry : unreadable.entrySet()) {
                LatticePoint cell = Objects.requireNonNull(entry.getKey(), "cell");
                sorted.put(cell, Objects.requireNonNull(entry.getValue(), "refusal"));
                if (section.occupants().containsKey(cell)) {
                    throw new IllegalArgumentException("an unreadable cell cannot name an occupant: " + cell);
                }
            }
            unreadable = Collections.unmodifiableMap(sorted);
        }
    }

    record Refused(ReadStatus status, FindingCode code) implements SectionSlot {
        public Refused {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(code, "code");
            if (status.readable()) {
                throw new IllegalArgumentException("a refused slot cannot be readable");
            }
        }
    }

    default boolean readable() {
        return this instanceof Air || this instanceof Present || this instanceof Partial;
    }

    default ReadStatus status() {
        return this instanceof Refused refused ? refused.status() : ReadStatus.readable;
    }
}
