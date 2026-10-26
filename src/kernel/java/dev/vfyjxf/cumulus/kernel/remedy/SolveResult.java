package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;

import java.util.List;
import java.util.Objects;
/** The remedies and the findings produced while solving. */
public record SolveResult(List<Remedy> remedies, FindingBundle findings) {

    public SolveResult {
        Objects.requireNonNull(remedies, "remedies");
        Objects.requireNonNull(findings, "findings");
        remedies = List.copyOf(remedies);
    }
}
