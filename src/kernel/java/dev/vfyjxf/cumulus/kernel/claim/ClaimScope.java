package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

import java.util.Objects;

public sealed interface ClaimScope
        permits ClaimScope.Owned, ClaimScope.Occupied, ClaimScope.Declared, ClaimScope.Audit {

    record Owned() implements ClaimScope {
    }

    record Occupied() implements ClaimScope {
    }

    record Declared(CuboidSet cells) implements ClaimScope {

        public Declared {
            Objects.requireNonNull(cells, "cells");
        }
    }

    record Audit() implements ClaimScope {
    }
}
