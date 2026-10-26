package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.space.Locus;

import java.util.Objects;
/** One ordered edit with its locus and safety. */
public record EditStep(Edit edit, Locus locus, Applicability applicability) {

    public EditStep {
        Objects.requireNonNull(edit, "edit");
        Objects.requireNonNull(locus, "locus");
        Objects.requireNonNull(applicability, "applicability");
    }
}
