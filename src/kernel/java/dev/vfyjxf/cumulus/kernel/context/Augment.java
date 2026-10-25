package dev.vfyjxf.cumulus.kernel.context;

import java.util.Objects;

public record Augment(AugmentKey key, double amount) {

    public Augment {
        Objects.requireNonNull(key, "key");
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("amount");
        }
    }
}
