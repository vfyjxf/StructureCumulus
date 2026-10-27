package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import org.jspecify.annotations.NullMarked;

/** One typed trait provider: a key and its block/entity reads. */
@NullMarked
public interface TraitProvider<T> {

    TraitKey<T> key();

    default TraitSlot readBlock(BlockTraitContext context) {
        return new TraitSlot.Absent(key().id());
    }

    default TraitSlot readEntity(EntityTraitContext context) {
        return new TraitSlot.Absent(key().id());
    }
}
