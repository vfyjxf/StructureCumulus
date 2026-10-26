package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** The result of one compile. */
public record CompileResult(FindingBundle findings, @Nullable Blueprint blueprint) {

    public CompileResult {
        Objects.requireNonNull(findings, "findings");
    }

    public boolean succeeded() {
        return blueprint != null;
    }
}
