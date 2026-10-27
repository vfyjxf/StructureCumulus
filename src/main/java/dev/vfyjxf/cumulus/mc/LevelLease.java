package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Host-owned lease: current while its capture is the level's latest and the handle is open. */
@NullMarked
public final class LevelLease implements SnapshotLease {

    private final SnapshotId id;
    private final WorldView view;
    private final BooleanSupplier current;
    private final @Nullable Runnable onClose;
    private boolean closed;

    public LevelLease(SnapshotId id, WorldView view, BooleanSupplier current) {
        this(id, view, current, null);
    }

    LevelLease(SnapshotId id, WorldView view, BooleanSupplier current, @Nullable Runnable onClose) {
        this.id = Objects.requireNonNull(id, "id");
        this.view = Objects.requireNonNull(view, "view");
        this.current = Objects.requireNonNull(current, "current");
        this.onClose = onClose;
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
        if (closed) {
            return;
        }
        closed = true;
        if (onClose != null) {
            onClose.run();
        }
    }
}
