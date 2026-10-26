package dev.vfyjxf.cumulus.kernel.world;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Turns one raw host occupant into a snapshot, or declines. */
public interface OccupantResolver<R> {

    Set<OccupantKind> kinds();

    @Nullable
    OccupantSnapshot resolve(R raw, ResolveContext context);
}
