package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** A capture ceiling and the content it must resolve. */
public record SnapshotPolicy(
    int maxElements,
    int maxDepth,
    Set<OccupantKind> kinds,
    Set<NamespacedId> traits,
    List<EntityWindow> windows
) {

    public SnapshotPolicy {
        if (maxElements < 0 || maxDepth < 0) {
            throw new IllegalArgumentException("non-negative policy");
        }
        Objects.requireNonNull(kinds, "kinds");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(windows, "windows");
        kinds = Collections.unmodifiableSortedSet(new TreeSet<>(kinds));
        TreeSet<NamespacedId> traitIds = new TreeSet<>();
        for (NamespacedId trait : traits) {
            traitIds.add(Objects.requireNonNull(trait, "trait"));
        }
        traits = Collections.unmodifiableSortedSet(traitIds);
        windows = List.copyOf(windows);
    }

    public static SnapshotPolicy minimal() {
        return new SnapshotPolicy(1_000_000, 0, Set.of(), Set.of(), List.of());
    }
}
