package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** What a check declares it reads: cells, keys and entity windows. */
public record ReadScope(CuboidSet cells, Set<ContextKey<?>> keys, List<EntityWindow> windows) {

    private static final Comparator<ContextKey<?>> keyOrder = Comparator.comparing((ContextKey<?> key) -> key.id())
            .thenComparing(key -> key.type().getName());

    public ReadScope {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(windows, "windows");
        TreeSet<ContextKey<?>> sorted = new TreeSet<>(keyOrder);
        sorted.addAll(keys);
        keys = Collections.unmodifiableSet(sorted);
        windows = List.copyOf(new LinkedHashSet<>(windows));
    }

    public static ReadScope none() {
        return new ReadScope(CuboidSet.empty(), Set.of(), List.of());
    }

    public static ReadScope cells(CuboidSet cells) {
        return new ReadScope(cells, Set.of(), List.of());
    }

    public ReadScope union(ReadScope other) {
        Objects.requireNonNull(other, "other");
        List<EntityWindow> joined = new ArrayList<>(windows);
        joined.addAll(other.windows);
        return new ReadScope(cells.union(other.cells), unionKeys(keys, other.keys), joined);
    }

    public ReadScope withKeys(Set<ContextKey<?>> extra) {
        return new ReadScope(cells, unionKeys(keys, extra), windows);
    }

    public ReadScope withWindows(List<EntityWindow> extra) {
        List<EntityWindow> joined = new ArrayList<>(windows);
        joined.addAll(extra);
        return new ReadScope(cells, keys, joined);
    }

    private static Set<ContextKey<?>> unionKeys(Set<ContextKey<?>> left, Set<ContextKey<?>> right) {
        TreeSet<ContextKey<?>> merged = new TreeSet<>(keyOrder);
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }
}
