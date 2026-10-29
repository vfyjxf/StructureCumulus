package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

/** How a check binds its subject. */
public interface Focus<T> {

    FocusResult<T> take(FocusContext ctx);

    Class<T> subject();

    CuboidSet staticCells(CuboidSet bayCells);

    default ReadScope reads(CuboidSet bayCells) {
        return ReadScope.cells(staticCells(bayCells));
    }

    default boolean bindsSubjects() {
        return true;
    }

    /**
     * Whether every subject this focus binds can back a claim. Unknown custom focuses answer false so a
     * claiming footprint is rejected at build instead of failing at settle with SC-CLM-0004.
     */
    default boolean bindsClaimableSubject() {
        return false;
    }

    default FindingBundle describeMissing() {
        return FindingBundle.empty();
    }
}
