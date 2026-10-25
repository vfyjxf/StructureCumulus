package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Bay;
import dev.vfyjxf.cumulus.kernel.blueprint.Blueprint;
import dev.vfyjxf.cumulus.kernel.blueprint.Coverage;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a bay's rows from its claim scope: {@code Owned}/{@code Declared} cells are authored local and
 * translated by the siting, while {@code Occupied} footprints are already world cells. A {@code SUBJECT}
 * bay is refused unless its bound footprints cover exactly its bay cells.
 */
public final class ClaimPlanner {

    static final FindingCode BOUNDS_MISMATCH = FindingCode.of(FindingCode.Family.BAY, 107);

    private ClaimPlanner() {
    }

    public static ClaimPlan plan(Blueprint blueprint, Siting siting, Collection<BayBinding> bindings,
                                 OwnerId owner) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(owner, "owner");
        Map<NamespacedId, BayBinding> byBay = new LinkedHashMap<>();
        for (BayBinding binding : bindings) {
            byBay.put(binding.bay(), binding);
        }
        List<ClaimRow> rows = new ArrayList<>();
        for (Bay bay : blueprint.bays()) {
            BayBinding binding = byBay.get(bay.id());
            if (bay.coverage() == Coverage.SUBJECT) {
                ClaimPlan.Invalid invalid = subjectMismatch(bay, binding, siting);
                if (invalid != null) {
                    return invalid;
                }
            }
            for (LatticeKey cell : claimedCells(bay, binding, siting)) {
                rows.add(new ClaimRow(cell, bay.mode(), owner, siting, bay.id()));
            }
        }
        return new ClaimPlan.Rows(ClaimRow.ordered(rows));
    }

    private static ClaimPlan.@Nullable Invalid subjectMismatch(Bay bay, @Nullable BayBinding binding,
                                                               Siting siting) {
        Set<LatticeKey> world = new LinkedHashSet<>();
        translate(bay.cells(), siting, world);
        Set<LatticeKey> bound = new LinkedHashSet<>();
        if (binding != null) {
            for (OccupantRef subject : binding.subjects()) {
                for (LatticePoint cell : cellsOf(subject.footprint())) {
                    bound.add(LatticeKey.of(cell));
                }
            }
        }
        if (bound.equals(world)) {
            return null;
        }
        String message = "SUBJECT bay " + bay.id() + " binds " + bound.size()
                + " cells but its bay is " + world.size();
        Finding finding = Finding.builder(BOUNDS_MISMATCH)
                .severity(Severity.ERROR)
                .message(message)
                .label(Locus.local(CuboidSet.of(bay.cells())), message)
                .stage(Stage.SETTLE)
                .build();
        return new ClaimPlan.Invalid(FindingBundle.of(finding));
    }

    private static Set<LatticeKey> claimedCells(Bay bay, @Nullable BayBinding binding, Siting siting) {
        Set<LatticeKey> cells = new LinkedHashSet<>();
        ClaimScope scope = bay.scope();
        if (scope instanceof ClaimScope.Audit) {
            return cells;
        }
        if (scope instanceof ClaimScope.Owned) {
            translate(bay.cells(), siting, cells);
            return cells;
        }
        if (scope instanceof ClaimScope.Declared declared) {
            translateAll(declared.cells(), siting, cells);
            return cells;
        }
        if (binding != null) {
            for (OccupantRef subject : binding.subjects()) {
                for (LatticePoint cell : cellsOf(subject.footprint())) {
                    cells.add(LatticeKey.of(cell));
                }
            }
        }
        return cells;
    }

    private static void translate(Cuboid box, Siting siting, Set<LatticeKey> into) {
        for (LatticePoint cell : cellsOf(box)) {
            LatticePoint world = siting.toWorld(cell);
            if (world == null) {
                throw new IllegalArgumentException("claim cell " + cell + " leaves the lattice at " + siting);
            }
            into.add(LatticeKey.of(world));
        }
    }

    private static void translateAll(CuboidSet boxes, Siting siting, Set<LatticeKey> into) {
        for (Cuboid box : boxes.boxes()) {
            translate(box, siting, into);
        }
    }

    static List<LatticePoint> cellsOf(Cuboid box) {
        if (box.isEmpty()) {
            return List.of();
        }
        List<LatticePoint> cells = new ArrayList<>((int) Math.min(Integer.MAX_VALUE, box.volume()));
        for (int x = box.min().x(); x < box.max().x(); x++) {
            for (int y = box.min().y(); y < box.max().y(); y++) {
                for (int z = box.min().z(); z < box.max().z(); z++) {
                    cells.add(LatticePoint.of(x, y, z));
                }
            }
        }
        return List.copyOf(cells);
    }
}
