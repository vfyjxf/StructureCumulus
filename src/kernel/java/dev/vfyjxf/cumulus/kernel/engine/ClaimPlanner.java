package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The only row factory: pure, with no view and no ledger. */
public final class ClaimPlanner {

    public static Derived derive(Blueprint blueprint, Siting siting, InstanceId instance, List<BayBinding> bindings) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(bindings, "bindings");
        Map<NamespacedId, List<OccupantSnapshot>> subjectsByBay = new LinkedHashMap<>();
        for (BayBinding binding : bindings) {
            for (OccupantSnapshot subject : binding.subjects()) {
                if (subject.ref().frame() != 0 || subject.ref().kind().equals(OccupantKind.nested)) {
                    throw new PlanRejected(Codes.bindingInvalid, "a nested or non-root subject cannot back a claim");
                }
            }
            subjectsByBay.put(binding.bay().id(), binding.subjects());
        }
        List<ClaimRow> rows = new ArrayList<>();
        CuboidSet allOwned = CuboidSet.empty();
        for (Bay bay : blueprint.bays()) {
            List<OccupantSnapshot> subjects = subjectsByBay.getOrDefault(bay.id(), List.of());
            CuboidSet owned = switch (bay.footprint()) {
                case Footprint.Box ignored -> {
                    CuboidSet world = siting.toWorld(bay.cells());
                    if (world == null) {
                        throw new PlanRejected(Codes.locusUnmapped, "bay leaves the lattice");
                    }
                    yield world;
                }
                case Footprint.Declared declared -> {
                    CuboidSet world = siting.toWorld(declared.cells());
                    if (world == null) {
                        throw new PlanRejected(Codes.locusUnmapped, "footprint leaves the lattice");
                    }
                    yield world;
                }
                case Footprint.Bound ignored -> {
                    CuboidSet union = CuboidSet.empty();
                    for (OccupantSnapshot subject : subjects) {
                        if (!subject.isAir()) {
                            union = union.union(subject.footprint().asSet());
                        }
                    }
                    yield union;
                }
                case Footprint.None ignored -> CuboidSet.empty();
            };
            if (owned.isEmpty() && bay.sharing() == Sharing.exclusive) {
                throw new PlanRejected(Codes.boundEmpty, "exclusive bay " + bay.id() + " owns no cell");
            }
            for (Cuboid box : owned.boxes()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            LatticePoint point = LatticePoint.of(x, y, z);
                            rows.add(new ClaimRow(LatticeKey.of(point), bay.sharing(), siting, bay.id(), instance));
                        }
                    }
                }
            }
            allOwned = allOwned.union(owned);
        }
        rows.sort(ClaimRow::compareTo);
        for (int i = 1; i < rows.size(); i++) {
            ClaimRow left = rows.get(i - 1);
            ClaimRow right = rows.get(i);
            if (!left.cell().equals(right.cell()) || left.bay().equals(right.bay())) {
                continue;
            }
            if (left.sharing() == Sharing.exclusive || right.sharing() == Sharing.exclusive) {
                throw new PlanRejected(
                    Codes.claimConflict,
                    "bays " + left.bay() + " and " + right.bay() + " both own " + left.cell()
                );
            }
        }
        return new Derived(rows, allOwned);
    }

    public record Derived(List<ClaimRow> rows, CuboidSet footprint) {
        public Derived {
            Objects.requireNonNull(rows, "rows");
            Objects.requireNonNull(footprint, "footprint");
            List<ClaimRow> sorted = new ArrayList<>(rows);
            sorted.sort(ClaimRow::compareTo);
            rows = List.copyOf(sorted);
        }
    }

    private ClaimPlanner() {}
}
