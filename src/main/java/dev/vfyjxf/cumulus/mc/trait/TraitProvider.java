package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import org.jspecify.annotations.NullMarked;

/** One typed trait provider for exactly one trait id and one holder id. */
@NullMarked
public interface TraitProvider<T, H> {

    TraitKey<T> key();

    TraitHolder<H> holder();

    TraitSlot get(H actual, TraitCaptureContext capture);
}
