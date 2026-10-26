package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

import java.util.Objects;
/** Which cells a bay owns. */
public sealed interface Footprint
        permits Footprint.Box, Footprint.Bound, Footprint.Declared, Footprint.None {

    record Box() implements Footprint {
    }

    record Bound() implements Footprint {
    }

    record Declared(CuboidSet cells) implements Footprint {
        public Declared {
            Objects.requireNonNull(cells, "cells");
        }
    }

    record None() implements Footprint {
    }

    static Footprint box() {
        return new Box();
    }

    static Footprint bound() {
        return new Bound();
    }

    static Footprint declared(CuboidSet cells) {
        return new Declared(cells);
    }

    static Footprint none() {
        return new None();
    }
}
