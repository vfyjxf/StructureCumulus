package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import org.jspecify.annotations.Nullable;

/** Lowers one kernel edit into world payloads, or answers null when it cannot. */
public interface Materializer {

    NamespacedId id();

    @Nullable
    MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context);
}
