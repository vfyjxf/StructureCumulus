package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryClaimLedger implements ClaimLedger {

    private final AtomicReference<ClaimSet> state;

    public InMemoryClaimLedger() {
        this(ClaimSet.EMPTY);
    }

    public InMemoryClaimLedger(ClaimSet initial) {
        this.state = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    @Override
    public ClaimVerdict settle(SettleRequest request) {
        Objects.requireNonNull(request, "request");
        List<ClaimRow> requested = ClaimRow.ordered(request.rows());
        List<ClaimMatrix.Overlap> overlaps = ClaimMatrix.internalOverlaps(requested);
        if (!overlaps.isEmpty()) {
            return new ClaimVerdict.ClaimConflict(ClaimMatrix.rowsOf(overlaps),
                    FindingBundle.of(ClaimMatrix.overlapFindings(overlaps)));
        }
        while (true) {
            ClaimSet current = snapshot();
            if (request.expectedRevision() != current.revision()) {
                return new ClaimVerdict.RevisionMoved(request.expectedRevision(), current.revision());
            }
            List<Finding> findings = conflictFindings(requested, current.rows());
            if (!findings.isEmpty()) {
                return new ClaimVerdict.ClaimConflict(blockingRows(requested, current.rows()),
                        FindingBundle.of(findings));
            }
            ClaimSet next = new ClaimSet(replaced(current.rows(), request), current.revision() + 1L);
            if (state.compareAndSet(current, next)) {
                return new ClaimVerdict.Settled(next.revision(), next.rows());
            }
        }
    }

    @Override
    public long revision() {
        return snapshot().revision();
    }

    @Override
    public ClaimView read(Cuboid box) {
        Objects.requireNonNull(box, "box");
        ClaimSet current = snapshot();
        return new ClaimView(current.rowsIn(box), current.revision(), true);
    }

    private ClaimSet snapshot() {
        return Objects.requireNonNull(state.get(), "state");
    }

    private static List<ClaimRow> replaced(List<ClaimRow> stored, SettleRequest request) {
        List<ClaimRow> next = new ArrayList<>(stored.size() + request.rows().size());
        for (ClaimRow row : stored) {
            if (!heldBy(row, request.owner(), request.siting())) {
                next.add(row);
            }
        }
        next.addAll(request.rows());
        return next;
    }

    private static boolean heldBy(ClaimRow row, OwnerId owner, Siting siting) {
        return row.owner().equals(owner) && row.siting().equals(siting);
    }

    private static List<ClaimRow> blockingRows(List<ClaimRow> requested, List<ClaimRow> stored) {
        Set<ClaimRow> blocking = new LinkedHashSet<>();
        for (ClaimRow row : stored) {
            for (ClaimRow candidate : requested) {
                if (ClaimMatrix.conflicts(candidate, row)) {
                    blocking.add(row);
                    break;
                }
            }
        }
        return ClaimRow.ordered(List.copyOf(blocking));
    }

    private static List<Finding> conflictFindings(List<ClaimRow> requested, List<ClaimRow> stored) {
        Map<Pair, Contest> contests = new LinkedHashMap<>();
        for (ClaimRow candidate : ClaimRow.ordered(requested)) {
            for (ClaimRow incumbent : stored) {
                if (!ClaimMatrix.conflicts(candidate, incumbent)) {
                    continue;
                }
                contests.computeIfAbsent(new Pair(candidate.bay(), incumbent.owner(), incumbent.bay()),
                                ignored -> new Contest(candidate, incumbent))
                        .contested(candidate.cell());
            }
        }
        List<Pair> pairs = new ArrayList<>(contests.keySet());
        pairs.sort(null);
        List<Finding> findings = new ArrayList<>(pairs.size());
        for (Pair pair : pairs) {
            Contest contest = Objects.requireNonNull(contests.get(pair), "contest");
            findings.add(ClaimMatrix.conflictFinding(contest.challenger(), contest.incumbent(),
                    Locus.world(contest.cells())));
        }
        return List.copyOf(findings);
    }

    private record Pair(NamespacedId challengerBay, OwnerId incumbentOwner, NamespacedId incumbentBay)
            implements Comparable<Pair> {

        @Override
        public int compareTo(Pair other) {
            int byBay = challengerBay.compareTo(other.challengerBay);
            if (byBay != 0) {
                return byBay;
            }
            int byOwner = incumbentOwner.compareTo(other.incumbentOwner);
            return byOwner != 0 ? byOwner : incumbentBay.compareTo(other.incumbentBay);
        }
    }

    private static final class Contest {

        private final ClaimRow challenger;
        private final ClaimRow incumbent;
        private final List<Cuboid> cells = new ArrayList<>();

        private Contest(ClaimRow challenger, ClaimRow incumbent) {
            this.challenger = challenger;
            this.incumbent = incumbent;
        }

        private void contested(LatticeKey cell) {
            cells.add(Cuboid.of(cell.toPoint()));
        }

        private ClaimRow challenger() {
            return challenger;
        }

        private ClaimRow incumbent() {
            return incumbent;
        }

        private CuboidSet cells() {
            return CuboidSet.of(cells);
        }
    }
}
