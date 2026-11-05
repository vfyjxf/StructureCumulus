package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;

import java.util.Objects;

/** The builder's refusal; only this package constructs it, and the code never lives in the message. */
public final class BlueprintRejected extends IllegalArgumentException {

    private final CodeEntry code;

    BlueprintRejected(CodeEntry code, String detail) {
        super(code.code() + ": " + detail);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CodeEntry code() {
        return code;
    }
}
