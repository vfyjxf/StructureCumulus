package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;

import java.util.Objects;

/** Why one read could not answer. */
public record ReadRefusal(ReadStatus status, FindingCode code) {

    public ReadRefusal {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(code, "code");
        if (status.readable()) {
            throw new IllegalArgumentException("a refusal cannot be readable");
        }
    }
}
