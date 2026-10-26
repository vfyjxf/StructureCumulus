package dev.vfyjxf.cumulus.kernel.world;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
/** A capture ceiling and the content it must resolve. */
public record SnapshotPolicy(int maxElements, int maxDepth, Set<OccupantKind> kinds,
                             Set<TraitKey<?>> traits, List<EntityWindow> windows) {

    public SnapshotPolicy {
        if (maxElements < 0 || maxDepth < 0) {
            throw new IllegalArgumentException("non-negative policy");
        }
        Objects.requireNonNull(kinds, "kinds");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(windows, "windows");
        kinds = Set.copyOf(new TreeSet<>(kinds));
        traits = Set.copyOf(new TreeSet<>(Comparator
                .comparing((TraitKey<?> key) -> key.id())));
        windows = List.copyOf(windows);
    }

    public static SnapshotPolicy minimal() {
        return new SnapshotPolicy(1_000_000, 0, Set.of(), Set.of(), List.of());
    }
}
