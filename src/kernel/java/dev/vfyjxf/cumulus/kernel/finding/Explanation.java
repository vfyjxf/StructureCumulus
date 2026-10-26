package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.ArrayList;
import java.util.List;

/** One code's long form. */
public record Explanation(FindingCode code, String summary, String body, List<NamespacedId> seeAlso) {

    public Explanation {
        Checks.checkNotNull(code, "code");
        Checks.checkNotNull(summary, "summary");
        Checks.checkNotNull(body, "body");
        Checks.checkArgument(!summary.isBlank(), "an explanation summary must not be blank");
        Checks.checkArgument(!body.isBlank(), "an explanation body must not be blank");
        List<NamespacedId> topics = new ArrayList<>(Checks.checkNotNull(seeAlso, "seeAlso"));
        topics.sort(NamespacedId::compareTo);
        seeAlso = List.copyOf(topics);
    }
}
