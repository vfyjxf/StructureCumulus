package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

/** The reading half of a check: it binds the subject, or says why it cannot. */
public interface Focus<T> {

    Outcome<T> take(FocusContext ctx);

    Class<T> subject();

    DecisionKind decisionKind();

    /** The cells this focus decides inside the bay, known at DESIGN; empty for a dynamic kind. */
    CuboidSet staticDecisionCells(Cuboid bayCells);

    ReadScope reads(Cuboid bayCells);

    /** The declared wording of an absence; its findings carry the DESIGN stage, the stage of a focus. */
    FindingBundle describeMissing();
}
