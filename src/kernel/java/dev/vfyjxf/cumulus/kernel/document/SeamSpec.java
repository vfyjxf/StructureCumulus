package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.structure.SeamId;
import dev.vfyjxf.cumulus.kernel.structure.SeamKind;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
/** The data form of a seam. */
public record SeamSpec(SeamId id, Cuboid cells, SeamKind kind, Map<String, String> properties,
                       @Nullable SourceSpan source) {

    public SeamSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(properties, "properties");
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }
}
