package dev.vfyjxf.cumulus.mc.trait;

import org.jspecify.annotations.NullMarked;

/** The registration face a {@link TraitPlugin} receives. */
@NullMarked
@FunctionalInterface
public interface TraitRegister {

    <T> void register(TraitProvider<T> provider);
}
