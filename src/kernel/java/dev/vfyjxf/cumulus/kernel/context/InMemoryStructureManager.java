package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One server-thread, in-memory registry; the host dispatches lifecycle events into it. */
@NullMarked
final class InMemoryStructureManager implements StructureManager {

    private static final Comparator<ContextKey<?>> keyOrder = Comparator.comparing((ContextKey<?> key) -> key.id())
            .thenComparing(key -> key.type().getName());

    private final TreeMap<InstanceId, StructureInstance> instances = new TreeMap<>();
    private final Map<SitingKey, InstanceId> bySiting = new HashMap<>();
    private final Map<InstanceId, Draft> drafts = new LinkedHashMap<>();
    private long nextSerial = 1;

    @Override
    public StructureAttempt begin(World world, StructureSpec spec) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spec, "spec");
        SitingKey key = new SitingKey(spec.owner(), spec.siting());
        if (drafts.values().stream().anyMatch(draft -> draft.key().equals(key))) {
            throw new IllegalArgumentException("an attempt already occupies " + key);
        }
        InstanceId id = new InstanceId(world, nextSerial++);
        Draft draft = new Draft(id, spec);
        drafts.put(id, draft);
        return draft;
    }

    @Override
    public @Nullable StructureInstance find(InstanceId id) {
        Objects.requireNonNull(id, "id");
        return instances.get(id);
    }

    @Override
    public @Nullable StructureInstance find(OwnerId owner, Siting siting) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        InstanceId id = bySiting.get(new SitingKey(owner, siting));
        return id == null ? null : instances.get(id);
    }

    @Override
    public List<StructureInstance> findAt(LatticePoint worldCell) {
        Objects.requireNonNull(worldCell, "worldCell");
        List<StructureInstance> found = new ArrayList<>();
        for (StructureInstance instance : instances.values()) {
            if (instance.bounds().contains(worldCell)) {
                found.add(instance);
            }
        }
        return List.copyOf(found);
    }

    @Override
    public List<StructureInstance> instances() {
        return List.copyOf(instances.values());
    }

    @Override
    public StructureContext context(InstanceId instance) {
        Objects.requireNonNull(instance, "instance");
        StructureInstance formed = instances.get(instance);
        if (formed != null) {
            return formed.context();
        }
        Draft draft = drafts.get(instance);
        return draft == null ? StructureContext.empty(instance) : draft.context();
    }

    @Override
    public @Nullable StructureContext remove(InstanceId instance) {
        Objects.requireNonNull(instance, "instance");
        StructureInstance formed = removeFormed(instance);
        if (formed != null) {
            return formed.context();
        }
        Draft draft = drafts.remove(instance);
        return draft == null ? null : draft.context();
    }

    @Override
    public StructureContext publish(
        InstanceId instance,
        Map<ContextKey<?>, Object> values,
        FindingBundle findings,
        Spend spend
    ) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(spend, "spend");
        Draft draft = drafts.get(instance);
        if (draft != null) {
            return draft.publish(instance, values, findings, spend);
        }
        StructureInstance formed = instances.get(instance);
        if (formed == null) {
            throw new IllegalArgumentException("no structure instance " + instance);
        }
        StructureContext merged = merge(formed.context(), values, findings, spend);
        instances.put(instance, new StructureInstance(formed.id(), formed.spec(), merged, formed.bounds()));
        return merged;
    }

    @Override
    public void onEvent(StructureEvent event) {
        Objects.requireNonNull(event, "event");
        switch (event) {
            case StructureEvent.Broken broken -> {
                removeFormed(broken.id());
                Draft draft = drafts.remove(broken.id());
                if (draft != null) {
                    draft.close();
                }
            }
            case StructureEvent.Moved moved -> move(moved);
            case StructureEvent.Loaded loaded -> load(loaded.instances());
            case StructureEvent.Unloaded unloaded -> unload(unloaded.world());
            case StructureEvent.Invalidated invalidated -> {
                removeFormed(invalidated.id());
                Draft draft = drafts.remove(invalidated.id());
                if (draft != null) {
                    draft.close();
                }
            }
        }
    }

    private void move(StructureEvent.Moved moved) {
        StructureInstance instance = instances.get(moved.id());
        if (instance == null) {
            throw new IllegalArgumentException("no structure instance " + moved.id());
        }
        if (!instance.siting().equals(moved.from())) {
            throw new IllegalArgumentException("the structure is not at " + moved.from());
        }
        SitingKey target = new SitingKey(instance.owner(), moved.to());
        InstanceId occupant = bySiting.get(target);
        if (occupant != null && !occupant.equals(instance.id())) {
            throw new IllegalArgumentException("a structure already occupies " + target);
        }
        StructureSpec spec = new StructureSpec(instance.owner(), moved.to(), instance.spec().structure());
        StructureInstance movedInstance = new StructureInstance(
            instance.id(),
            spec,
            instance.context(),
            moved.bounds()
        );
        bySiting.remove(new SitingKey(instance.owner(), instance.siting()));
        instances.put(instance.id(), movedInstance);
        bySiting.put(target, instance.id());
    }

    private void load(List<StructureInstance> restored) {
        instances.clear();
        bySiting.clear();
        drafts.clear();
        long maxSerial = 0;
        for (StructureInstance instance : restored) {
            if (instances.putIfAbsent(instance.id(), instance) != null) {
                throw new IllegalArgumentException("duplicate structure instance " + instance.id());
            }
            SitingKey key = new SitingKey(instance.owner(), instance.siting());
            if (bySiting.putIfAbsent(key, instance.id()) != null) {
                throw new IllegalArgumentException("duplicate structure siting " + key);
            }
            maxSerial = Math.max(maxSerial, instance.id().serial());
        }
        nextSerial = Math.max(nextSerial, maxSerial + 1);
    }

    private void unload(World world) {
        List<InstanceId> removed = new ArrayList<>();
        for (InstanceId id : instances.keySet()) {
            if (id.world().equals(world)) {
                removed.add(id);
            }
        }
        for (InstanceId id : removed) {
            removeFormed(id);
        }
        drafts.entrySet().removeIf(entry -> {
            if (entry.getKey().world().equals(world)) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    private @Nullable StructureInstance removeFormed(InstanceId id) {
        StructureInstance removed = instances.remove(id);
        if (removed != null) {
            bySiting.remove(new SitingKey(removed.owner(), removed.siting()));
        }
        return removed;
    }

    private void register(StructureInstance instance) {
        SitingKey key = new SitingKey(instance.owner(), instance.siting());
        if (instances.containsKey(instance.id()) || bySiting.containsKey(key)) {
            throw new IllegalArgumentException("a structure already occupies " + key);
        }
        instances.put(instance.id(), instance);
        bySiting.put(key, instance.id());
    }

    private static StructureContext merge(
        StructureContext base,
        Map<ContextKey<?>, Object> values,
        FindingBundle findings,
        Spend spend
    ) {
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            ContextKey<?> key = Objects.requireNonNull(entry.getKey(), "key");
            Objects.requireNonNull(entry.getValue(), "value");
            if (key.retention() != Retention.instance) {
                throw new IllegalArgumentException("only INSTANCE values are published: " + key.id());
            }
        }
        TreeMap<ContextKey<?>, Object> merged = new TreeMap<>(keyOrder);
        merged.putAll(base.values());
        merged.putAll(values);
        return new StructureContext(
            base.instance(),
            base.revision() + 1,
            Collections.unmodifiableMap(merged),
            findings,
            base.spend().plus(spend)
        );
    }

    private record SitingKey(OwnerId owner, Siting siting) {}

    private final class Draft implements StructureAttempt {

        private final InstanceId id;
        private final StructureSpec spec;
        private StructureContext context;
        private boolean closed;

        private Draft(InstanceId id, StructureSpec spec) {
            this.id = id;
            this.spec = spec;
            this.context = StructureContext.empty(id);
        }

        private SitingKey key() {
            return new SitingKey(spec.owner(), spec.siting());
        }

        private StructureContext context() {
            return context;
        }

        @Override
        public InstanceId id() {
            return id;
        }

        @Override
        public StructureSpec spec() {
            return spec;
        }

        @Override
        public StructureContext context(InstanceId instance) {
            if (!instance.equals(id)) {
                return StructureContext.empty(instance);
            }
            return context;
        }

        @Override
        public @Nullable StructureContext remove(InstanceId instance) {
            if (!instance.equals(id)) {
                return null;
            }
            StructureContext old = context;
            context = StructureContext.empty(id);
            return old;
        }

        @Override
        public StructureContext publish(
            InstanceId instance,
            Map<ContextKey<?>, Object> values,
            FindingBundle findings,
            Spend spend
        ) {
            if (!instance.equals(id)) {
                throw new IllegalArgumentException("the draft belongs to " + id);
            }
            context = merge(context, values, findings, spend);
            return context;
        }

        @Override
        public StructureInstance commit(CuboidSet bounds) {
            Objects.requireNonNull(bounds, "bounds");
            if (closed) {
                throw new IllegalStateException("the attempt is already closed");
            }
            closed = true;
            drafts.remove(id);
            InstanceId previous = bySiting.get(new SitingKey(spec.owner(), spec.siting()));
            if (previous != null && !previous.equals(id)) {
                removeFormed(previous);
            }
            StructureInstance instance = new StructureInstance(id, spec, context, bounds);
            register(instance);
            return instance;
        }

        @Override
        public void abort() {
            if (closed) {
                throw new IllegalStateException("the attempt is already closed");
            }
            closed = true;
            drafts.remove(id);
        }

        private void close() {
            closed = true;
        }
    }
}
