package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** A declared connectable edge. It is never tested and never claimed. */
public record Seam(SeamId id, Cuboid cells, SeamKind kind, Map<String, String> properties) {

    public static final String CUSTOM_KIND = "kind";

    public Seam {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(properties, "properties");
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    public boolean compatibleWith(Seam other) {
        return kind == other.kind && Objects.equals(properties.get(CUSTOM_KIND), other.properties.get(CUSTOM_KIND));
    }
}
