package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;

import java.util.Objects;

/** The planner's refusal; only this package constructs it, and the code never lives in the message. */
final class PlanRejected extends IllegalArgumentException {

    private final CodeEntry code;

    PlanRejected(CodeEntry code, String detail) {
        super(code.code() + ": " + detail);
        this.code = Objects.requireNonNull(code, "code");
    }

    CodeEntry code() {
        return code;
    }
}
