package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One world's claim ledger in memory: rows live and die with the world's structure state. */
final class InMemoryClaimLedger implements ClaimLedger {

    private final World world;
    private final List<ClaimRow> rows = new ArrayList<>();
    private long revision;

    InMemoryClaimLedger(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public ClaimVerdict settle(SettleRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.expectedRevision() != revision) {
            return new ClaimVerdict.RevisionMoved(request.expectedRevision(), revision);
        }
        List<ClaimRow> incumbents = incumbents(request);
        ClaimVerdict conflict = conflicts(request, incumbents);
        if (conflict != null) {
            return conflict;
        }
        List<ClaimRow> replacement = new ArrayList<>(new LinkedHashSet<>(request.rows()));
        replacement.sort(ClaimRow::compareTo);
        List<ClaimRow> next = new ArrayList<>(incumbents.size() + replacement.size());
        next.addAll(incumbents);
        next.addAll(replacement);
        next.sort(ClaimRow::compareTo);
        if (next.equals(rows)) {
            return new ClaimVerdict.Settled(revision, replacement);
        }
        rows.clear();
        rows.addAll(next);
        revision++;
        return new ClaimVerdict.Settled(revision, replacement);
    }

    /** The rows this settle leaves in place: not its own, not at a siting it takes over. */
    private List<ClaimRow> incumbents(SettleRequest request) {
        Set<Siting> replaced = new HashSet<>();
        for (ClaimRow row : request.rows()) {
            replaced.add(row.siting());
        }
        List<ClaimRow> incumbents = new ArrayList<>();
        for (ClaimRow row : rows) {
            if (row.instance().equals(request.instance()) || replaced.contains(row.siting())) {
                continue;
            }
            incumbents.add(row);
        }
        return incumbents;
    }

    @Override
    public ClaimVerdict release(InstanceId instance) {
        Objects.requireNonNull(instance, "instance");
        boolean removed = rows.removeIf(row -> row.instance().equals(instance));
        if (!removed) {
            return new ClaimVerdict.Settled(revision, List.of());
        }
        revision++;
        return new ClaimVerdict.Settled(revision, List.of());
    }

    @Override
    public ClaimView read(Cuboid box) {
        Objects.requireNonNull(box, "box");
        List<ClaimRow> found = new ArrayList<>();
        for (ClaimRow row : rows) {
            if (box.contains(row.cell().point())) {
                found.add(row);
            }
        }
        return new ClaimView(found, revision, true);
    }

    @Override
    public void retire() {
        rows.clear();
        revision = 0;
    }

    private @Nullable ClaimVerdict conflicts(SettleRequest request, List<ClaimRow> incumbents) {
        Map<String, Conflict> conflicts = new LinkedHashMap<>();
        for (ClaimRow requested : request.rows()) {
            for (ClaimRow incumbent : incumbents) {
                if (!ClaimMatrix.conflicts(requested, incumbent)) {
                    continue;
                }
                String key = requested.bay().text() + "|" + incumbent.instance() + "|" + incumbent.bay().text();
                Conflict conflict = conflicts
                        .computeIfAbsent(key, ignored -> new Conflict(requested, incumbent, CuboidSet.empty()));
                conflict.incumbents.add(incumbent);
                conflict.cells = conflict.cells.union(Cuboid.of(requested.cell().point()));
            }
        }
        if (conflicts.isEmpty()) {
            return null;
        }
        Set<ClaimRow> allIncumbents = new LinkedHashSet<>();
        List<Finding> findings = new ArrayList<>();
        for (Conflict conflict : conflicts.values()) {
            allIncumbents.addAll(conflict.incumbents);
            findings.add(finding(conflict));
        }
        return new ClaimVerdict.Conflict(new ArrayList<>(allIncumbents), FindingBundle.of(findings));
    }

    private static Finding finding(Conflict conflict) {
        Locus locus = Locus.world(conflict.cells);
        String message = conflict.incumbent.instance() + ":" + conflict.incumbent.bay() + " already holds "
                + conflict.cells.volume() + " cell(s) of " + conflict.requested.bay();
        return Finding.builder(Codes.claimConflict).phase(Phase.settle).message(message)
                .label(Label.primary(locus, "contested cells"))
                .note("this bay asks " + conflict.requested.sharing(), locus)
                .note("the incumbent holds " + conflict.incumbent.sharing(), null).build();
    }

    private static final class Conflict {
        private final ClaimRow requested;
        private final ClaimRow incumbent;
        private final Set<ClaimRow> incumbents = new LinkedHashSet<>();
        private CuboidSet cells;

        private Conflict(ClaimRow requested, ClaimRow incumbent, CuboidSet cells) {
            this.requested = requested;
            this.incumbent = incumbent;
            this.cells = cells;
        }
    }
}
