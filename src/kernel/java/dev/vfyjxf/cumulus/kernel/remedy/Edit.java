package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.world.Trait;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One host-applicable edit. */
public sealed interface Edit {
    record Place(CuboidSet cells, NamespacedId type, Map<String, String> properties) implements Edit {
        public Place {
            Objects.requireNonNull(cells, "cells");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(properties, "properties");
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
    }

    record Remove(CuboidSet cells) implements Edit {
        public Remove {
            Objects.requireNonNull(cells, "cells");
        }
    }

    record Replace(CuboidSet cells, NamespacedId type, Map<String, String> properties) implements Edit {
        public Replace {
            Objects.requireNonNull(cells, "cells");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(properties, "properties");
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
    }

    record SetTrait(CuboidSet cells, Trait<?> trait) implements Edit {
        public SetTrait {
            Objects.requireNonNull(cells, "cells");
            Objects.requireNonNull(trait, "trait");
        }
    }
}
