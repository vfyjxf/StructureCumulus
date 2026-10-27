package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One frame's immutable sections, bodies and child frames. */
@NullMarked
record FrameData(
    SnapshotId snapshot,
    int frame,
    int depth,
    LatticePoint origin,
    Cuboid bounds,
    Map<SectionKey, SectionSlot> sections,
    List<OccupantSnapshot> entities,
    List<OccupantSnapshot> nestedBodies,
    Map<LatticePoint, FrameData> children
) {

    FrameData {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(nestedBodies, "nestedBodies");
        Objects.requireNonNull(children, "children");
        if (frame < 0 || depth < 0) {
            throw new IllegalArgumentException("frame and depth >= 0");
        }
        TreeMap<SectionKey, SectionSlot> sortedSections = new TreeMap<>();
        for (Map.Entry<SectionKey, SectionSlot> entry : sections.entrySet()) {
            SectionKey key = Objects.requireNonNull(entry.getKey(), "section key");
            if (!key.world().equals(snapshot.world()) || key.frame() != frame) {
                throw new IllegalArgumentException("section key belongs to another snapshot or frame: " + key);
            }
            sortedSections.put(key, Objects.requireNonNull(entry.getValue(), "section"));
        }
        sections = Collections.unmodifiableMap(sortedSections);
        entities = bodies(snapshot, frame, entities, OccupantKind.entity);
        nestedBodies = bodies(snapshot, frame, nestedBodies, OccupantKind.nested);
        TreeMap<LatticePoint, FrameData> sortedChildren = new TreeMap<>();
        for (Map.Entry<LatticePoint, FrameData> entry : children.entrySet()) {
            LatticePoint anchor = Objects.requireNonNull(entry.getKey(), "anchor");
            FrameData child = Objects.requireNonNull(entry.getValue(), "child");
            if (child.depth() != depth + 1) {
                throw new IllegalArgumentException("a child frame must be one level deeper");
            }
            sortedChildren.put(anchor, child);
        }
        children = Collections.unmodifiableMap(sortedChildren);
    }

    private static List<OccupantSnapshot> bodies(
        SnapshotId snapshot,
        int frame,
        List<OccupantSnapshot> bodies,
        OccupantKind kind
    ) {
        ArrayList<OccupantSnapshot> sorted = new ArrayList<>(bodies.size());
        for (OccupantSnapshot body : bodies) {
            Objects.requireNonNull(body, "body");
            if (!body.ref().from(snapshot) || body.ref().frame() != frame) {
                throw new IllegalArgumentException("body from another snapshot or frame");
            }
            if (!body.descriptor().kind().equals(kind)) {
                throw new IllegalArgumentException("not a " + kind.id() + " occupant: " + body.descriptor().type());
            }
            sorted.add(body);
        }
        sorted.sort(Comparator.comparing(OccupantSnapshot::ref));
        return List.copyOf(sorted);
    }
}
