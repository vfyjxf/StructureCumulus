package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.Objects;

/** One anchor read: a siting or the findings that prevented it. */
public record SitingResult(Outcome<Siting> outcome, FindingBundle findings) {

    public SitingResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(findings, "findings");
    }

    public boolean succeeded() {
        return outcome.isPresent();
    }
}
