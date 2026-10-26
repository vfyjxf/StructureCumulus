package dev.vfyjxf.cumulus.kernel.claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/** The rows of one settlement and the revision written. */
public record ClaimSet(List<ClaimRow> rows, long revision) {

    public ClaimSet {
        Objects.requireNonNull(rows, "rows");
        if (revision < 0) {
            throw new IllegalArgumentException("revision >= 0");
        }
        List<ClaimRow> sorted = new ArrayList<>(rows);
        sorted.sort(ClaimRow::compareTo);
        rows = List.copyOf(sorted);
    }
}
