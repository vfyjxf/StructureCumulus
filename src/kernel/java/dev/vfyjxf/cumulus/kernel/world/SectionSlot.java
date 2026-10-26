package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;

import java.util.Objects;
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
        return this instanceof Air || this instanceof Present;
    }

    default ReadStatus status() {
        return this instanceof Refused refused ? refused.status() : ReadStatus.READABLE;
    }
}
