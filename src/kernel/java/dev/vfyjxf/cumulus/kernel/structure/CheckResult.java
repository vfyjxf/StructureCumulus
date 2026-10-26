package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.List;
import java.util.Objects;
/** The answer of one check and the subjects it bound. */
public record CheckResult(Reply reply, List<OccupantSnapshot> subjects, FindingBundle notes) {

    public CheckResult {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(subjects, "subjects");
        Objects.requireNonNull(notes, "notes");
        subjects = List.copyOf(subjects);
    }
}
