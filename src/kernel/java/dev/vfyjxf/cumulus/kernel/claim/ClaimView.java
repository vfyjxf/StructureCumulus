package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/** One read answer of the ledger. */
public record ClaimView(List<ClaimRow> rows, long revision, boolean available) {

    public ClaimView {
        Objects.requireNonNull(rows, "rows");
        if (revision < 0) {
            throw new IllegalArgumentException("revision >= 0");
        }
        if (!available && !rows.isEmpty()) {
            throw new IllegalArgumentException("an unavailable view holds no rows");
        }
        List<ClaimRow> sorted = new ArrayList<>(rows);
        sorted.sort(ClaimRow::compareTo);
        rows = List.copyOf(sorted);
    }

    public static ClaimView unavailable() {
        return new ClaimView(List.of(), 0, false);
    }

    public List<ClaimRow> rowsIn(Cuboid box) {
        List<ClaimRow> result = new ArrayList<>();
        for (ClaimRow row : rows) {
            if (box.contains(row.cell().point())) {
                result.add(row);
            }
        }
        return List.copyOf(result);
    }
}
