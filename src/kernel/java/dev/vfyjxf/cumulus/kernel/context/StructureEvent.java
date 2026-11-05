package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/** The host's lifecycle input to a {@link StructureManager}. */
@NullMarked
public sealed interface StructureEvent {

    record Broken(InstanceId id) implements StructureEvent {
        public Broken {
            Objects.requireNonNull(id, "id");
        }
    }

    /** Replaces one world's restored instances; other worlds are untouched. */
    record Loaded(World world, List<StructureInstance> instances) implements StructureEvent {
        public Loaded {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(instances, "instances");
            instances = List.copyOf(instances);
        }
    }

    record Unloaded(World world) implements StructureEvent {
        public Unloaded {
            Objects.requireNonNull(world, "world");
        }
    }

    record Invalidated(InstanceId id, String reason) implements StructureEvent {
        public Invalidated {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
