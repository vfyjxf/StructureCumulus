package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ClaimRow(LatticeKey cell, ClaimMode mode, OwnerId owner, Siting siting, NamespacedId bay)
        implements Comparable<ClaimRow> {

    public ClaimRow {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(bay, "bay");
    }

    public static List<ClaimRow> ordered(List<ClaimRow> rows) {
        List<ClaimRow> sorted = new ArrayList<>(Objects.requireNonNull(rows, "rows"));
        sorted.sort(null);
        return List.copyOf(sorted);
    }

    @Override
    public int compareTo(ClaimRow other) {
        int byCell = cell.toPoint().compareTo(other.cell.toPoint());
        if (byCell != 0) {
            return byCell;
        }
        int byOwner = owner.compareTo(other.owner);
        return byOwner != 0 ? byOwner : bay.compareTo(other.bay);
    }
}
