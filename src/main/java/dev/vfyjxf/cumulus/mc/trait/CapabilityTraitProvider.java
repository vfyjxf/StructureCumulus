package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import net.neoforged.neoforge.capabilities.BaseCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** One capability lookup as a trait provider; a null capability value is an absent trait. */
@NullMarked
public final class CapabilityTraitProvider<T, H, C extends @Nullable Object> implements TraitProvider<T, H> {

    private final TraitKey<T> key;
    private final TraitHolder<H> holder;
    private final BaseCapability<T, C> capability;
    private final CapabilityAccess<H, C> access;

    private CapabilityTraitProvider(
        TraitKey<T> key,
        TraitHolder<H> holder,
        BaseCapability<T, C> capability,
        CapabilityAccess<H, C> access
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.holder = Objects.requireNonNull(holder, "holder");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.access = Objects.requireNonNull(access, "access");
    }

    public static <T, H, C extends @Nullable Object> CapabilityTraitProvider<T, H, C> of(
        TraitKey<T> key,
        TraitHolder<H> holder,
        BaseCapability<T, C> capability,
        CapabilityAccess<H, C> access
    ) {
        return new CapabilityTraitProvider<>(key, holder, capability, access);
    }

    @Override
    public TraitKey<T> key() {
        return key;
    }

    @Override
    public TraitHolder<H> holder() {
        return holder;
    }

    @Override
    public TraitSlot get(H actual, TraitCaptureContext capture) {
        T value = access.get(capability, actual, capture);
        return value == null ? new TraitSlot.Absent(key.id()) : new TraitSlot.Present(Trait.of(key, value));
    }
}
