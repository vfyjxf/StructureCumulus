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

    default FindingBundle describeMissing() {
        return FindingBundle.empty();
    }
}
