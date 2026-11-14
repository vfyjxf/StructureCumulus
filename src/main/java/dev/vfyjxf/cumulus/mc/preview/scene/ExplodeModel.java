package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The exploded view's offset law (design 17 §8.1): one group per bay, each part pushed away from the
 * structure's centre along its own direction. A pure function of the stage table, the drawn cells and
 * the drawn hull.
 */
public record ExplodeModel(
    List<Group> groups,
    List<NamespacedId> bayOfStage,
    float distance,
    float centroidX,
    float centroidY,
    float centroidZ
) {

    /** One bay's part: its cells, its centroid and its unit direction away from the structure's. */
    public record Group(
        NamespacedId bay,
        String text,
        List<LatticePoint> cells,
        float x,
        float y,
        float z,
        float directionX,
        float directionY,
        float directionZ
    ) {}

    /** One label and its anchor, already displaced by {@code t}. */
    public record Label(String text, float x, float y, float z, float alpha) {}

    /** A displacement in cell units. */
    public record Offset(float x, float y, float z) {

        public static final Offset zero = new Offset(0.0F, 0.0F, 0.0F);
    }

    public ExplodeModel {
        groups = List.copyOf(groups);
        bayOfStage = List.copyOf(bayOfStage);
    }

    /**
     * The model of one page: {@code drawnCells} is every cell the stage treatment draws, in wire order,
     * and {@code hull} is the box those cells occupy.
     */
    public static ExplodeModel of(List<Stage> stages, List<GhostCell> drawnCells, Hull hull) {
        return of(stages, drawnCells, hull, Map.of());
    }

    /**
     * The model with a display text per bay: a set page's parts are its members, and a label names the
     * member rather than the page's synthetic grouping key (design 18 §4).
     */
    public static ExplodeModel of(
        List<Stage> stages,
        List<GhostCell> drawnCells,
        Hull hull,
        Map<NamespacedId, String> texts
    ) {
        Map<NamespacedId, List<LatticePoint>> byBay = new LinkedHashMap<>();
        List<NamespacedId> bayOfStage = new ArrayList<>(stages.size());
        for (Stage stage : stages) {
            bayOfStage.add(stage.bay());
        }
        for (GhostCell ghost : drawnCells) {
            int stage = ghost.stageIndex();
            if (stage < 0 || stage >= stages.size()) {
                continue;
            }
            byBay.computeIfAbsent(stages.get(stage).bay(), ignored -> new ArrayList<>()).add(ghost.cell());
        }
        List<LatticePoint> all = new ArrayList<>(drawnCells.size());
        for (List<LatticePoint> cells : byBay.values()) {
            all.addAll(cells);
        }
        all.sort(canonical);
        float[] structure = centroid(all);
        float distance = hull.isEmpty() ? 0.0F : 0.5F * hull.diagonal();
        List<Group> groups = new ArrayList<>(byBay.size());
        for (Map.Entry<NamespacedId, List<LatticePoint>> part : byBay.entrySet()) {
            List<LatticePoint> cells = new ArrayList<>(part.getValue());
            cells.sort(canonical);
            float[] centre = centroid(cells);
            float dx = centre[0] - structure[0];
            float dy = centre[1] - structure[1];
            float dz = centre[2] - structure[2];
            if (dx == 0.0F && dy == 0.0F && dz == 0.0F) {
                dx = 0.0F;
                dy = 1.0F;
                dz = 0.0F;
            } else {
                float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= length;
                dy /= length;
                dz /= length;
            }
            String text = texts.getOrDefault(part.getKey(), part.getKey().text());
            groups.add(new Group(part.getKey(), text, cells, centre[0], centre[1], centre[2], dx, dy, dz));
        }
        groups.sort(Comparator.comparing(Group::bay));
        return new ExplodeModel(groups, bayOfStage, distance, structure[0], structure[1], structure[2]);
    }

    /** True when there is a second part to separate: one bay is `one part` (design 17 §8.1). */
    public boolean separable() {
        return groups.size() > 1;
    }

    /** The cell's displacement at {@code t}; a cell no group holds stays where it is. */
    public Offset offset(GhostCell ghost, float t) {
        return offset(ghost.stageIndex(), t);
    }

    /** The displacement of one stage's part at {@code t}; the overlay follows exploded members. */
    public Offset offset(int stage, float t) {
        if (stage < 0 || stage >= bayOfStage.size()) {
            return Offset.zero;
        }
        return offset(bayOfStage.get(stage), t);
    }

    /**
     * The displacement of one grouping key's part at {@code t}: a set page's key is its member, which
     * several stages share once the server flattened the member's own table (design 19 §2).
     */
    public Offset offset(NamespacedId bay, float t) {
        if (bay == null || !(t > 0.0F)) {
            return Offset.zero;
        }
        for (Group group : groups) {
            if (!group.bay().equals(bay)) {
                continue;
            }
            return new Offset(
                group.directionX() * distance * t,
                group.directionY() * distance * t,
                group.directionZ() * distance * t
            );
        }
        return Offset.zero;
    }

    /** Every part's label at {@code t}: the bay's name on the moved centroid (design 17 §8.2). */
    public List<Label> labels(float t) {
        List<Label> labels = new ArrayList<>(groups.size());
        for (Group group : groups) {
            labels.add(
                new Label(
                    group.text(),
                    group.x() + group.directionX() * distance * t,
                    group.y() + group.directionY() * distance * t,
                    group.z() + group.directionZ() * distance * t,
                    t
                )
            );
        }
        return List.copyOf(labels);
    }

    /** The structure's own centroid: the mean of every drawn cell centre. */
    public float[] structureCentroid() {
        return new float[]{centroidX, centroidY, centroidZ};
    }

    private static final Comparator<LatticePoint> canonical = LatticePoint::compareTo;

    private static float[] centroid(List<LatticePoint> cells) {
        if (cells.isEmpty()) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        for (LatticePoint cell : cells) {
            x += cell.x() + 0.5F;
            y += cell.y() + 0.5F;
            z += cell.z() + 0.5F;
        }
        float count = cells.size();
        return new float[]{x / count, y / count, z / count};
    }

}
