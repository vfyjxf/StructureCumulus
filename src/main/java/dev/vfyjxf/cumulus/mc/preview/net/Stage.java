package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** One stage of a blueprint's stage table: its bay, its name, its expected cells, the satisfied share and its bay's text. */
public record Stage(
    String name,
    int expectedCells,
    int satisfiedCells,
    @Nullable String hint,
    @Nullable String description,
    NamespacedId bay
) {

    public Stage {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bay, "bay");
        if (expectedCells <= 0) {
            throw new IllegalArgumentException("a stage has at least one expected cell");
        }
        if (satisfiedCells < 0 || satisfiedCells > expectedCells) {
            throw new IllegalArgumentException("satisfiedCells must lie within the expected count: " + satisfiedCells);
        }
    }
}
