package dev.vfyjxf.cumulus.mc.capability;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/** The adapter's declared nested-extent holders; the only place naming the current subject kinds. */
@NullMarked
public final class NestedExtentHolders {

    public static final NestedExtentHolder<BlockNestedExtentContext> block = NestedExtentHolder.of(id("block"));

    public static final NestedExtentHolder<Entity> entity = NestedExtentHolder.of(id("entity"));

    private NestedExtentHolders() {}

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }
}
