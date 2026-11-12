package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;

/** One material the open plan still needs: how much it asks for and how much the source can give. */
public record MaterialStatus(NamespacedId item, int need, int have) {

    public MaterialStatus {
        Objects.requireNonNull(item, "item");
        if (need <= 0) {
            throw new IllegalArgumentException("need must be positive");
        }
        if (have < 0 || have >= need) {
            throw new IllegalArgumentException("a material line reports a shortage: " + have + "/" + need);
        }
    }
}
