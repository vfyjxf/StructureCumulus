package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ClaimSet(List<ClaimRow> rows, long revision) {

    public static final ClaimSet EMPTY = new ClaimSet(List.of(), 0L);

    public ClaimSet {
        rows = ClaimRow.ordered(rows);
        if (revision < 0L) {
            throw new IllegalArgumentException("negative revision: " + revision);
        }
    }

    public List<ClaimRow> rowsIn(Cuboid box) {
        Objects.requireNonNull(box, "box");
        List<ClaimRow> inside = new ArrayList<>();
        for (ClaimRow row : rows) {
            if (box.contains(row.cell().toPoint())) {
                inside.add(row);
            }
        }
        return List.copyOf(inside);
    }
}
