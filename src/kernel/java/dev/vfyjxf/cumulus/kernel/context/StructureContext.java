package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable frame stack of one instance; a read never looks above its tier's newest published revision. */
public record StructureContext(InstanceId instance, long revision, List<ContextFrame> frames)
        implements ContextView {

    public StructureContext {
        Objects.requireNonNull(instance, "instance");
        if (revision < 0) {
            throw new IllegalArgumentException("revision");
        }
        Objects.requireNonNull(frames, "frames");
        List<ContextFrame> ordered = new ArrayList<>(frames);
        ordered.sort(Comparator.comparingLong(ContextFrame::revision));
        frames = List.copyOf(ordered);
    }

    public static StructureContext empty(InstanceId instance) {
        return new StructureContext(instance, 0L, List.of());
    }

    @Override
    public ContextTier tier() {
        return ContextTier.SETTLE;
    }

    @Override
    public <T> @Nullable T get(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        return getAt(tier(), key);
    }

    @Override
    public <T> Outcome<T> require(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        T value = getAt(tier(), key);
        return value != null
                ? new Outcome.Found<>(value, List.of(), FindingBundle.empty())
                : new Outcome.Missing<>(ContextFindings.bundle(
                        ContextFindings.keyAbsent(instance, key, tier())));
    }

    @Override
    public boolean has(ContextKey<?> key) {
        Objects.requireNonNull(key, "key");
        return getAt(tier(), key) != null;
    }

    @Override
    public Set<ContextKey<?>> keys() {
        return keysAt(tier());
    }

    @Override
    public ContextView at(ContextTier tier) {
        Objects.requireNonNull(tier, "tier");
        return tier == ContextTier.SETTLE ? this : new View(this, tier);
    }

    <T> @Nullable T getAt(ContextTier tier, ContextKey<T> key) {
        int limit = tier.ordinal();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ContextFrame frame = frames.get(index);
            if (frame.tier().ordinal() > limit || !frame.publishes(key)) {
                continue;
            }
            Object value = Objects.requireNonNull(frame.values().get(key), "value");
            return ContextFrame.isAbsent(value) ? null : key.type().cast(value);
        }
        return null;
    }

    Set<ContextKey<?>> keysAt(ContextTier tier) {
        Set<ContextKey<?>> keys = new TreeSet<>();
        int limit = tier.ordinal();
        for (ContextFrame frame : frames) {
            if (frame.tier().ordinal() > limit) {
                continue;
            }
            for (Map.Entry<ContextKey<?>, Object> entry : frame.values().entrySet()) {
                if (!ContextFrame.isAbsent(entry.getValue())) {
                    keys.add(entry.getKey());
                }
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    private record View(StructureContext context, ContextTier tier) implements ContextView {

        @Override
        public <T> @Nullable T get(ContextKey<T> key) {
            Objects.requireNonNull(key, "key");
            return context.getAt(tier, key);
        }

        @Override
        public <T> Outcome<T> require(ContextKey<T> key) {
            Objects.requireNonNull(key, "key");
            T value = context.getAt(tier, key);
            return value != null
                    ? new Outcome.Found<>(value, List.of(), FindingBundle.empty())
                    : new Outcome.Missing<>(ContextFindings.bundle(
                            ContextFindings.keyAbsent(context.instance(), key, tier)));
        }

        @Override
        public boolean has(ContextKey<?> key) {
            Objects.requireNonNull(key, "key");
            return context.getAt(tier, key) != null;
        }

        @Override
        public Set<ContextKey<?>> keys() {
            return context.keysAt(tier);
        }

        @Override
        public ContextView at(ContextTier narrower) {
            Objects.requireNonNull(narrower, "narrower");
            ContextTier clamped = narrower.ordinal() > tier.ordinal() ? tier : narrower;
            return clamped == tier ? this : new View(context, clamped);
        }
    }
}
