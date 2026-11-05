package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.Objects;

/** One cell of one world held by one bay of one instance at one siting. */
public record ClaimRow(LatticeKey cell, Sharing sharing, Siting siting, NamespacedId bay, InstanceId instance)
        implements
            Comparable<ClaimRow> {

    public ClaimRow {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(sharing, "sharing");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(instance, "instance");
    }

    @Override
    public int compareTo(ClaimRow other) {
        int result = cell.compareTo(other.cell);
        if (result != 0) {
            return result;
        }
        result = instance.compareTo(other.instance);
        if (result != 0) {
            return result;
        }
        result = bay.compareTo(other.bay);
        if (result != 0) {
            return result;
        }
        result = siting.origin().compareTo(other.siting.origin());
        if (result != 0) {
            return result;
        }
        Placement placement = siting.placement();
        Placement otherPlacement = other.siting.placement();
        result = placement.orientation().compareTo(otherPlacement.orientation());
        if (result != 0) {
            return result;
        }
        result = placement.offset().compareTo(otherPlacement.offset());
        if (result != 0) {
            return result;
        }
        return sharing.compareTo(other.sharing);
    }
}
