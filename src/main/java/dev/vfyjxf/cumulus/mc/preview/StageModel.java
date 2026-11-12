package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The stage table of one blueprint (design 16 §4): a pure function of the compiled declaration and
 * the definition-frame expected cells. No world, no siting, no clock and no hash-ordered collection.
 */
public final class StageModel {

    /** The stage cap; an overflow keeps the first stages in order (design 16 §4.1.8). */
    public static final int maxStages = 256;

    /** One stage: its display name, the description its bay carries, and its cells in canonical order. */
    public record Stage(String name, @Nullable String description, List<LatticePoint> cells) {

        public Stage {
            Objects.requireNonNull(name, "name");
            cells = List.copyOf(cells);
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("a stage needs at least one cell");
            }
        }
    }

    /** The capped table plus the stage count it was capped from. */
    public record Table(List<Stage> stages, int expectedStages) {

        public Table {
            stages = List.copyOf(stages);
            if (expectedStages < stages.size()) {
                throw new IllegalArgumentException("expectedStages cannot be below the shown count");
            }
        }

        public boolean truncated() {
            return stages.size() < expectedStages;
        }
    }

    /** The table of {@code blueprint} over {@code expected}, the cells the preview draws as ghosts. */
    public static Table of(Blueprint blueprint, Collection<LatticePoint> expected) {
        List<Bay> bays = blueprint.bays();
        TreeSet<LatticePoint> wanted = new TreeSet<>(expected);
        Map<LatticePoint, Integer> owned = new TreeMap<>();
        for (Check<?> check : blueprint.checks()) {
            int bay = bayIndex(bays, check.bay());
            if (bay < 0) {
                continue;
            }
            for (LatticePoint cell : PreviewExpected.declared(check)) {
                if (wanted.contains(cell)) {
                    owned.merge(cell, bay, Math::min);
                }
            }
        }
        List<Stage> stages = bays.size() == 1 ? layerStages(bays.get(0), owned) : bayStages(bays, owned);
        if (stages.size() == 1 && bays.size() == 1) {
            // one stage is the partition collapsed: it takes the bay's name, not a layer rank (design 16 §4.1.5)
            stages = bayStages(bays, owned);
        }
        if (stages.size() <= maxStages) {
            return new Table(stages, stages.size());
        }
        return new Table(stages.subList(0, maxStages), stages.size());
    }

    private static List<Stage> bayStages(List<Bay> bays, Map<LatticePoint, Integer> owned) {
        List<Stage> stages = new ArrayList<>(bays.size());
        for (int index = 0; index < bays.size(); index++) {
            List<LatticePoint> cells = new ArrayList<>();
            for (Map.Entry<LatticePoint, Integer> entry : owned.entrySet()) {
                if (entry.getValue() == index) {
                    cells.add(entry.getKey());
                }
            }
            if (cells.isEmpty()) {
                continue;
            }
            Bay bay = bays.get(index);
            stages.add(new Stage(bay.id().text(), bay.description(), cells));
        }
        return List.copyOf(stages);
    }

    private static List<Stage> layerStages(Bay bay, Map<LatticePoint, Integer> owned) {
        Map<Integer, List<LatticePoint>> layers = new TreeMap<>();
        for (LatticePoint cell : owned.keySet()) {
            layers.computeIfAbsent(cell.y(), layer -> new ArrayList<>()).add(cell);
        }
        List<Stage> stages = new ArrayList<>(layers.size());
        int rank = 1;
        for (Map.Entry<Integer, List<LatticePoint>> layer : layers.entrySet()) {
            stages.add(new Stage("layer " + rank, bay.description(), layer.getValue()));
            rank++;
        }
        return List.copyOf(stages);
    }

    private static int bayIndex(List<Bay> bays, Bay bay) {
        for (int index = 0; index < bays.size(); index++) {
            if (bays.get(index).id().equals(bay.id())) {
                return index;
            }
        }
        return -1;
    }

    private StageModel() {}
}
