package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;

/** A code with the severity and explanation the registry promises. */
public record CodeEntry(FindingCode code, Severity severity, Explanation explanation) {

    public CodeEntry {
        Checks.checkNotNull(code, "code");
        Checks.checkNotNull(severity, "severity");
        Checks.checkNotNull(explanation, "explanation");
        Checks.checkArgument(code.equals(explanation.code()), "the explanation must describe the same code");
    }
}
