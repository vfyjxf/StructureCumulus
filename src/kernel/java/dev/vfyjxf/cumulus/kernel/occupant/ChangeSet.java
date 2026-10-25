package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What changed between two revisions; {@link #plus(ChangeSet)} keeps the later revision.
 */
public record ChangeSet(Set<LatticeKey> cells, Set<SectionKey> sections, Set<LatticeKey> payloads,
                        Set<EntityKey> entities, Set<NamespacedId> keys, long revision) {

    private static final Comparator<NamespacedId> ID_ORDER =
            Comparator.comparing(NamespacedId::namespace).thenComparing(NamespacedId::path);

    public ChangeSet {
        cells = Set.copyOf(Objects.requireNonNull(cells, "cells"));
        sections = Set.copyOf(Objects.requireNonNull(sections, "sections"));
        payloads = Set.copyOf(Objects.requireNonNull(payloads, "payloads"));
        entities = Set.copyOf(Objects.requireNonNull(entities, "entities"));
        keys = immutableKeys(keys);
        if (revision < 0L) {
            throw new IllegalArgumentException("negative revision: " + revision);
        }
    }

    public static ChangeSet empty() {
        return new ChangeSet(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0L);
    }

    public ChangeSet plus(ChangeSet other) {
        Objects.requireNonNull(other, "other");
        return new ChangeSet(union(cells, other.cells), union(sections, other.sections),
                union(payloads, other.payloads), union(entities, other.entities),
                union(keys, other.keys), Math.max(revision, other.revision));
    }

    public boolean isEmpty() {
        return cells.isEmpty() && sections.isEmpty() && payloads.isEmpty()
                && entities.isEmpty() && keys.isEmpty();
    }

    private static Set<NamespacedId> immutableKeys(Set<NamespacedId> ids) {
        Objects.requireNonNull(ids, "keys");
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<NamespacedId> ordered = new ArrayList<>(ids.size());
        for (NamespacedId id : ids) {
            ordered.add(Objects.requireNonNull(id, "key"));
        }
        ordered.sort(ID_ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    private static <T> Set<T> union(Set<T> left, Set<T> right) {
        if (right.isEmpty()) {
            return left;
        }
        Set<T> merged = new LinkedHashSet<>(left);
        merged.addAll(right);
        return merged;
    }
}
