package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;

/** One trait id's capture state. */
public sealed interface TraitSlot {

    record Present(Trait<?> trait) implements TraitSlot {
        public Present {
            Objects.requireNonNull(trait, "trait");
        }
    }

    record Absent(NamespacedId id) implements TraitSlot {
        public Absent {
            Objects.requireNonNull(id, "id");
        }
    }

    record Refused(NamespacedId id, ReadRefusal refusal) implements TraitSlot {
        public Refused {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    default NamespacedId id() {
        return switch (this) {
            case Present present -> present.trait().key().id();
            case Absent absent -> absent.id();
            case Refused refused -> refused.id();
        };
    }
}
