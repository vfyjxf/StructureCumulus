package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ClaimView(List<ClaimRow> rows, long revision, boolean available) {

    public ClaimView {
        rows = ClaimRow.ordered(rows);
        if (revision < 0L) {
            throw new IllegalArgumentException("negative revision: " + revision);
        }
        if (!available && !rows.isEmpty()) {
            throw new IllegalArgumentException("an unavailable view holds no rows");
        }
    }

    public static ClaimView unavailable() {
        return new ClaimView(List.of(), 0L, false);
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
