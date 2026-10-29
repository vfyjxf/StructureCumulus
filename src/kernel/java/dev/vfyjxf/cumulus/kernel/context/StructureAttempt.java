package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.NullMarked;

/** A reserved structure attempt. It is the only context store the settle pass may publish into. */
@NullMarked
public interface StructureAttempt extends ContextStore {

    InstanceId id();

    StructureSpec spec();

    /** Commits the attempt once; the bounds are the formed structure's world-frame cells. */
    StructureInstance commit(CuboidSet bounds);

    /** Discards the attempt and its draft context. */
    void abort();
}
