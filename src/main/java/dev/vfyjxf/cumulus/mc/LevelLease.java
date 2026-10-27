package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Host-owned lease: current while its capture is the level's latest and the handle is open. */
@NullMarked
public final class LevelLease implements SnapshotLease {

    private final SnapshotId id;
    private final WorldView view;
    private final BooleanSupplier current;
    private boolean closed;

    public LevelLease(SnapshotId id, WorldView view, BooleanSupplier current) {
        this.id = Objects.requireNonNull(id, "id");
        this.view = Objects.requireNonNull(view, "view");
        this.current = Objects.requireNonNull(current, "current");
    }

    @Override
    public SnapshotId id() {
        return id;
    }

    @Override
    public WorldView view() {
        return view;
    }

    @Override
    public boolean isCurrent() {
        return !closed && current.getAsBoolean();
    }

    @Override
    public void close() {
        closed = true;
    }
}
