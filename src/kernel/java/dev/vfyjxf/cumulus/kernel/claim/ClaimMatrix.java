package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class ClaimMatrix {

    public static final FindingCode OVERLAP_CODE = FindingCode.of(FindingCode.Family.CLM, 1);
    public static final FindingCode CONFLICT_CODE = FindingCode.of(FindingCode.Family.CLM, 2);

    private ClaimMatrix() {
    }

    /**
     * Two rows conflict only on one cell; one bay of one claimant never conflicts with itself, but two
     * bays of one settlement are two claimants.
     */
    public static boolean conflicts(ClaimRow a, ClaimRow b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (!a.cell().equals(b.cell())) {
            return false;
        }
        if (a.owner().equals(b.owner()) && a.siting().equals(b.siting())) {
            return false;
        }
        return a.mode().excludes() || b.mode().excludes();
    }

    public static List<ClaimRow> conflictsWith(ClaimRow candidate, Collection<ClaimRow> existing) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(existing, "existing");
        List<ClaimRow> conflicting = new ArrayList<>();
        for (ClaimRow row : existing) {
            if (conflicts(candidate, row)) {
                conflicting.add(row);
            }
        }
        return ClaimRow.ordered(conflicting);
    }

    /** Two bays of one settlement that fight over a cell: {@code SC-CLM-0001}, answerable without a ledger. */
    public static List<Overlap> internalOverlaps(List<ClaimRow> rows) {
        Objects.requireNonNull(rows, "rows");
        Map<LatticePoint, List<ClaimRow>> byCell = new TreeMap<>();
        for (ClaimRow row : ClaimRow.ordered(rows)) {
            byCell.computeIfAbsent(row.cell().toPoint(), ignored -> new ArrayList<>()).add(row);
        }
        Map<BayPair, Overlap> overlaps = new LinkedHashMap<>();
        for (Map.Entry<LatticePoint, List<ClaimRow>> entry : byCell.entrySet()) {
            List<ClaimRow> holders = entry.getValue();
            for (int first = 0; first < holders.size(); first++) {
                for (int second = first + 1; second < holders.size(); second++) {
                    ClaimRow incumbent = holders.get(first);
                    ClaimRow challenger = holders.get(second);
                    if (!internalConflict(challenger, incumbent)) {
                        continue;
                    }
                    overlaps.computeIfAbsent(BayPair.of(incumbent, challenger),
                                    ignored -> new Overlap(challenger, incumbent))
                            .contested(entry.getKey());
                }
            }
        }
        return List.copyOf(overlaps.values());
    }

    private static boolean internalConflict(ClaimRow a, ClaimRow b) {
        if (!a.cell().equals(b.cell())) {
            return false;
        }
        if (!a.owner().equals(b.owner()) || !a.siting().equals(b.siting()) || a.bay().equals(b.bay())) {
            return false;
        }
        return a.mode().excludes() || b.mode().excludes();
    }

    /** The rows an internal overlap involves, each once, in canonical order. */
    public static List<ClaimRow> rowsOf(List<Overlap> overlaps) {
        Objects.requireNonNull(overlaps, "overlaps");
        List<ClaimRow> rows = new ArrayList<>();
        for (Overlap overlap : overlaps) {
            if (!rows.contains(overlap.challenger())) {
                rows.add(overlap.challenger());
            }
            if (!rows.contains(overlap.incumbent())) {
                rows.add(overlap.incumbent());
            }
        }
        return ClaimRow.ordered(rows);
    }

    public static List<Finding> overlapFindings(List<Overlap> overlaps) {
        Objects.requireNonNull(overlaps, "overlaps");
        List<Finding> findings = new ArrayList<>(overlaps.size());
        for (Overlap overlap : overlaps) {
            findings.add(overlapFinding(overlap));
        }
        return List.copyOf(findings);
    }

    public static Finding overlapFinding(Overlap overlap) {
        Objects.requireNonNull(overlap, "overlap");
        ClaimRow first = overlap.incumbent();
        ClaimRow second = overlap.challenger();
        Locus contested = Locus.world(overlap.cells());
        String message = first.owner() + ": bays " + first.bay() + " (" + first.mode() + ") and "
                + second.bay() + " (" + second.mode() + ") both claim one settlement's cells";
        return Finding.builder(OVERLAP_CODE)
                .severity(Severity.ERROR)
                .message(message)
                .label(contested, first.bay() + " holds " + first.mode())
                .secondary(contested, second.bay() + " holds " + second.mode())
                .stage(Stage.SETTLE)
                .build();
    }

    public static Finding conflictFinding(ClaimRow challenger, ClaimRow incumbent) {
        Objects.requireNonNull(challenger, "challenger");
        return conflictFinding(challenger, incumbent,
                Locus.world(CuboidSet.of(Cuboid.of(challenger.cell().toPoint()))));
    }

    public static Finding conflictFinding(ClaimRow challenger, ClaimRow incumbent, Locus contested) {
        Objects.requireNonNull(challenger, "challenger");
        Objects.requireNonNull(incumbent, "incumbent");
        Objects.requireNonNull(contested, "contested");
        String message = challenger.owner() + ":" + challenger.bay() + " (" + challenger.mode() + ") and "
                + incumbent.owner() + ":" + incumbent.bay() + " (" + incumbent.mode()
                + ") both claim cell " + challenger.cell();
        return Finding.builder(CONFLICT_CODE)
                .severity(Severity.ERROR)
                .message(message)
                .label(contested, challenger.bay() + " requests " + challenger.mode())
                .secondary(contested, incumbent.bay() + " already holds it as " + incumbent.mode())
                .stage(Stage.SETTLE)
                .build();
    }

    /** Two bays of one settlement and the cells they fight over. */
    public static final class Overlap {

        private final ClaimRow challenger;
        private final ClaimRow incumbent;
        private final List<Cuboid> cells = new ArrayList<>();

        private Overlap(ClaimRow challenger, ClaimRow incumbent) {
            this.challenger = Objects.requireNonNull(challenger, "challenger");
            this.incumbent = Objects.requireNonNull(incumbent, "incumbent");
        }

        public ClaimRow challenger() {
            return challenger;
        }

        public ClaimRow incumbent() {
            return incumbent;
        }

        public CuboidSet cells() {
            return CuboidSet.of(cells);
        }

        private void contested(LatticePoint cell) {
            cells.add(Cuboid.of(cell));
        }
    }

    private record BayPair(OwnerId incumbentOwner, Siting incumbentSiting, NamespacedId incumbentBay,
                           OwnerId challengerOwner, Siting challengerSiting, NamespacedId challengerBay) {

        static BayPair of(ClaimRow incumbent, ClaimRow challenger) {
            return new BayPair(incumbent.owner(), incumbent.siting(), incumbent.bay(),
                    challenger.owner(), challenger.siting(), challenger.bay());
        }
    }
}
