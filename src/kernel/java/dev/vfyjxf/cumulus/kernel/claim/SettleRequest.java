package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.List;
import java.util.Objects;

public record SettleRequest(OwnerId owner, Siting siting, List<ClaimRow> rows, long expectedRevision) {

    public SettleRequest {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("negative revision: " + expectedRevision);
        }
        for (ClaimRow row : rows) {
            if (!row.owner().equals(owner) || !row.siting().equals(siting)) {
                throw new IllegalArgumentException("row outside the request's claimant: " + row);
            }
        }
    }
}
