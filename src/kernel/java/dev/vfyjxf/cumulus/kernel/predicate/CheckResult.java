package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;

import java.util.List;
import java.util.Objects;

/** One check's full result: the verdict, the subjects it bound, and any non-fatal notes. */
public record CheckResult(Reply reply, List<OccupantRef> subjects, FindingBundle notes) {

    public CheckResult(Reply reply, List<OccupantRef> subjects, FindingBundle notes) {
        this.reply = Objects.requireNonNull(reply, "reply");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.notes = Objects.requireNonNull(notes, "notes");
    }
}
