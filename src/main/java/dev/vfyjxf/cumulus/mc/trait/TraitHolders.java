package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

/** The adapter's declared trait holders; the only place naming the current subject kinds. */
@NullMarked
public final class TraitHolders {

    public static final TraitHolder<BlockTraitContext> block = TraitHolder.of(id("block"));

    public static final TraitHolder<EntityTraitContext> entity = TraitHolder.of(id("entity"));

    private TraitHolders() {}

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }
}
