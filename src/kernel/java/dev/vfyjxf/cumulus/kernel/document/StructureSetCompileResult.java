package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The result of one structure-set compile. */
public record StructureSetCompileResult(FindingBundle findings, @Nullable CompiledStructureSet set) {

    public StructureSetCompileResult {
        Objects.requireNonNull(findings, "findings");
    }

    public boolean succeeded() {
        return set != null;
    }
}
