package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** What a check declares it reads: cells, keys, traits, kinds, depth and entity windows. */
public record ReadScope(
    CuboidSet cells,
    Set<ContextKey<?>> keys,
    Set<NamespacedId> traits,
    Set<OccupantKind> kinds,
    int depth,
    List<EntityWindow> windows
) {

    private static final Comparator<ContextKey<?>> keyOrder = Comparator.comparing((ContextKey<?> key) -> key.id())
            .thenComparing(key -> key.type().getName());

    public ReadScope {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(kinds, "kinds");
        Objects.requireNonNull(windows, "windows");
        if (depth < 0) {
            throw new IllegalArgumentException("depth >= 0");
        }
        TreeSet<ContextKey<?>> sortedKeys = new TreeSet<>(keyOrder);
        sortedKeys.addAll(keys);
        keys = Collections.unmodifiableSet(sortedKeys);
        TreeSet<NamespacedId> sortedTraits = new TreeSet<>();
        for (NamespacedId trait : traits) {
            sortedTraits.add(Objects.requireNonNull(trait, "trait"));
        }
        traits = Collections.unmodifiableSet(sortedTraits);
        TreeSet<OccupantKind> sortedKinds = new TreeSet<>();
        for (OccupantKind kind : kinds) {
            sortedKinds.add(Objects.requireNonNull(kind, "kind"));
        }
        kinds = Collections.unmodifiableSet(sortedKinds);
        windows = List.copyOf(new LinkedHashSet<>(windows));
    }

    public static ReadScope none() {
        return new ReadScope(CuboidSet.empty(), Set.of(), Set.of(), Set.of(), 0, List.of());
    }

    public static ReadScope cells(CuboidSet cells) {
        return new ReadScope(cells, Set.of(), Set.of(), Set.of(), 0, List.of());
    }

    public ReadScope union(ReadScope other) {
        Objects.requireNonNull(other, "other");
        List<EntityWindow> joined = new ArrayList<>(windows);
        joined.addAll(other.windows);
        return new ReadScope(
            cells.union(other.cells),
            unionKeys(keys, other.keys),
            unionTraits(traits, other.traits),
            unionKinds(kinds, other.kinds),
            Math.max(depth, other.depth),
            joined
        );
    }

    public ReadScope withKeys(Set<ContextKey<?>> extra) {
        Objects.requireNonNull(extra, "extra");
        return new ReadScope(cells, unionKeys(keys, extra), traits, kinds, depth, windows);
    }

    public ReadScope withTraits(Set<NamespacedId> extra) {
        Objects.requireNonNull(extra, "extra");
        return new ReadScope(cells, keys, unionTraits(traits, extra), kinds, depth, windows);
    }

    public ReadScope withKinds(Set<OccupantKind> extra) {
        Objects.requireNonNull(extra, "extra");
        return new ReadScope(cells, keys, traits, unionKinds(kinds, extra), depth, windows);
    }

    public ReadScope withDepth(int extra) {
        return new ReadScope(cells, keys, traits, kinds, Math.max(depth, extra), windows);
    }

    public ReadScope withWindows(List<EntityWindow> extra) {
        Objects.requireNonNull(extra, "extra");
        List<EntityWindow> joined = new ArrayList<>(windows);
        joined.addAll(extra);
        return new ReadScope(cells, keys, traits, kinds, depth, joined);
    }

    private static Set<ContextKey<?>> unionKeys(Set<ContextKey<?>> left, Set<ContextKey<?>> right) {
        TreeSet<ContextKey<?>> merged = new TreeSet<>(keyOrder);
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }

    private static Set<NamespacedId> unionTraits(Set<NamespacedId> left, Set<NamespacedId> right) {
        TreeSet<NamespacedId> merged = new TreeSet<>();
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }

    private static Set<OccupantKind> unionKinds(Set<OccupantKind> left, Set<OccupantKind> right) {
        TreeSet<OccupantKind> merged = new TreeSet<>();
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }
}
