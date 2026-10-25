package dev.vfyjxf.cumulus.kernel.base;

import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.occupant.EntityWindow;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ReadScope(CuboidSet cells, Set<ContextKey<?>> keys, List<EntityWindow> entities) {

    private static final ReadScope NONE = new ReadScope(CuboidSet.empty(), Set.of(), List.of());

    public ReadScope {
        keys = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        entities = List.copyOf(entities);
    }

    public static ReadScope none() {
        return NONE;
    }

    public ReadScope withKeys(Set<ContextKey<?>> added) {
        if (added.isEmpty()) {
            return this;
        }
        Set<ContextKey<?>> merged = new LinkedHashSet<>(keys);
        merged.addAll(added);
        return new ReadScope(cells, merged, entities);
    }

    public ReadScope union(ReadScope other) {
        Set<ContextKey<?>> mergedKeys = new LinkedHashSet<>(keys);
        mergedKeys.addAll(other.keys);
        Set<EntityWindow> mergedWindows = new LinkedHashSet<>(entities);
        mergedWindows.addAll(other.entities);
        return new ReadScope(cells.union(other.cells), mergedKeys, List.copyOf(mergedWindows));
    }

    public boolean isEmpty() {
        return cells.isEmpty() && keys.isEmpty() && entities.isEmpty();
    }
}
