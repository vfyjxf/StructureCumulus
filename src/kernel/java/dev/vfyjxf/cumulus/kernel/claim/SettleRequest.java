package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/** The unit of the ledger's compare-and-set. */
public record SettleRequest(OwnerId owner, Siting siting, List<ClaimRow> rows,
                            long expectedRevision) {

    public SettleRequest {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(rows, "rows");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision >= 0");
        }
        List<ClaimRow> sorted = new ArrayList<>(rows);
        for (ClaimRow row : sorted) {
            if (!row.owner().equals(owner) || !row.siting().equals(siting)) {
                throw new IllegalArgumentException("a row does not belong to this request");
            }
        }
        sorted.sort(ClaimRow::compareTo);
        rows = List.copyOf(sorted);
    }
}
