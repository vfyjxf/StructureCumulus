package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The unit of the ledger's compare-and-set. */
public record SettleRequest(InstanceId instance, List<ClaimRow> rows, long expectedRevision) {

    public SettleRequest {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(rows, "rows");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision >= 0");
        }
        List<ClaimRow> sorted = new ArrayList<>(rows);
        for (ClaimRow row : sorted) {
            if (!row.instance().equals(instance)) {
                throw new IllegalArgumentException("a row does not belong to this instance");
            }
        }
        sorted.sort(ClaimRow::compareTo);
        rows = List.copyOf(sorted);
    }
}
