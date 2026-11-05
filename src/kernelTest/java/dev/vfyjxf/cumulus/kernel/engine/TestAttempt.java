package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/** A standalone settle target: id, owner, siting and structure all come from the request. */
final class TestAttempt implements StructureAttempt {

    private final InstanceId id;
    private final StructureSpec spec;
    private final ContextStore store = ContextStore.inMemory();
    private boolean open = true;

    private TestAttempt(InstanceId id, StructureSpec spec) {
        this.id = id;
        this.spec = spec;
    }

    static TestAttempt of(MatchRequest request) {
        return new TestAttempt(
            request.instance(),
            new StructureSpec(request.owner(), request.siting(), request.blueprint().id())
        );
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
    public boolean isOpen() {
        return open;
    }

    @Override
    public StructureContext context(InstanceId instance) {
        return store.context(instance);
    }

    @Override
    public @Nullable StructureContext remove(InstanceId instance) {
        return store.remove(instance);
    }

    @Override
    public StructureContext publish(
        InstanceId instance,
        Map<ContextKey<?>, Object> values,
        FindingBundle findings,
        Spend spend
    ) {
        if (!open) {
            throw new IllegalStateException("closed");
        }
        return store.publish(instance, values, findings, spend);
    }

    @Override
    public StructureInstance commit(CuboidSet bounds) {
        open = false;
        return new StructureInstance(id, spec, store.context(id), bounds);
    }

    @Override
    public void abort() {
        open = false;
    }

    ContextStore store() {
        return store;
    }
}
