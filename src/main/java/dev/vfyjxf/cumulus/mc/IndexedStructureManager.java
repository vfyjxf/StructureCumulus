package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The funnel's write side: every commit and removal the manager sees is mirrored into the level's
 * {@link InstanceIndex}, including the silent same-siting replacement inside {@code commit}.
 */
@NullMarked
final class IndexedStructureManager implements StructureManager {

    private final StructureManager delegate;
    private final InstanceIndex index;

    IndexedStructureManager(StructureManager delegate, InstanceIndex index) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public StructureAttempt begin(World world, StructureSpec spec) {
        return new IndexedAttempt(delegate.begin(world, spec));
    }

    @Override
    public @Nullable StructureInstance find(InstanceId id) {
        return delegate.find(id);
    }

    @Override
    public @Nullable StructureInstance find(World world, OwnerId owner, Siting siting) {
        return delegate.find(world, owner, siting);
    }

    @Override
    public List<StructureInstance> findAt(LatticePoint worldCell) {
        return delegate.findAt(worldCell);
    }

    @Override
    public List<StructureInstance> instances() {
        return delegate.instances();
    }

    @Override
    public StructureContext context(InstanceId instance) {
        return delegate.context(instance);
    }

    @Override
    public @Nullable StructureContext remove(InstanceId instance) {
        StructureContext removed = delegate.remove(instance);
        if (removed != null) {
            index.drop(instance);
        }
        return removed;
    }

    @Override
    public StructureContext publish(
        InstanceId instance,
        Map<ContextKey<?>, Object> values,
        FindingBundle findings,
        Spend spend
    ) {
        return delegate.publish(instance, values, findings, spend);
    }

    @Override
    public void onEvent(StructureEvent event) {
        switch (event) {
            case StructureEvent.Broken broken -> {
                delegate.onEvent(event);
                index.drop(broken.id());
            }
            case StructureEvent.Invalidated invalidated -> {
                delegate.onEvent(event);
                index.drop(invalidated.id());
            }
            // Loaded validates before it mutates, so the index is rebuilt only after it lands.
            case StructureEvent.Loaded loaded -> {
                delegate.onEvent(event);
                index.dropWorld(loaded.world());
                for (StructureInstance instance : loaded.instances()) {
                    index.add(instance);
                }
            }
            case StructureEvent.Unloaded unloaded -> {
                delegate.onEvent(event);
                index.dropWorld(unloaded.world());
            }
        }
    }

    private final class IndexedAttempt implements StructureAttempt {

        private final StructureAttempt attempt;

        private IndexedAttempt(StructureAttempt attempt) {
            this.attempt = attempt;
        }

        @Override
        public InstanceId id() {
            return attempt.id();
        }

        @Override
        public StructureSpec spec() {
            return attempt.spec();
        }

        @Override
        public boolean isOpen() {
            return attempt.isOpen();
        }

        @Override
        public StructureInstance commit(CuboidSet bounds) {
            StructureSpec spec = attempt.spec();
            StructureInstance previous = delegate.find(attempt.id().world(), spec.owner(), spec.siting());
            StructureInstance formed = attempt.commit(bounds);
            if (previous != null && !previous.id().equals(formed.id())) {
                index.drop(previous.id());
            }
            index.add(formed);
            return formed;
        }

        @Override
        public void abort() {
            attempt.abort();
        }

        @Override
        public StructureContext context(InstanceId instance) {
            return attempt.context(instance);
        }

        @Override
        public @Nullable StructureContext remove(InstanceId instance) {
            return attempt.remove(instance);
        }

        @Override
        public StructureContext publish(
            InstanceId instance,
            Map<ContextKey<?>, Object> values,
            FindingBundle findings,
            Spend spend
        ) {
            return attempt.publish(instance, values, findings, spend);
        }
    }
}
