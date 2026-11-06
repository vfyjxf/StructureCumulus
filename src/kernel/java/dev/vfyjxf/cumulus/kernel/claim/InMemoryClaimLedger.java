package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        List<ClaimRow> replacement = canonical(request.rows());
        List<ClaimRow> next = mergeSorted(incumbents, replacement);
        if (next.equals(rows)) {
            return new ClaimVerdict.Settled(revision, replacement);
        }
        rows.clear();
        rows.addAll(next);
        revision++;
        return new ClaimVerdict.Settled(revision, replacement);
    }

    /** The sorted unique request rows, deduped after sorting instead of hashing every record. */
    private static List<ClaimRow> canonical(List<ClaimRow> request) {
        List<ClaimRow> sorted = new ArrayList<>(request);
        sorted.sort(ClaimRow::compareTo);
        int write = 0;
        for (int read = 1; read < sorted.size(); read++) {
            if (!sorted.get(write).equals(sorted.get(read))) {
                write++;
                sorted.set(write, sorted.get(read));
            }
        }
        return write + 1 == sorted.size() ? sorted : new ArrayList<>(sorted.subList(0, write + 1));
    }

    /** Two sorted lists merged; ties keep the incumbent first, like a stable sort of both. */
    private static List<ClaimRow> mergeSorted(List<ClaimRow> incumbents, List<ClaimRow> replacement) {
        List<ClaimRow> merged = new ArrayList<>(incumbents.size() + replacement.size());
        int left = 0;
        int right = 0;
        while (left < incumbents.size() && right < replacement.size()) {
            if (incumbents.get(left).compareTo(replacement.get(right)) <= 0) {
                merged.add(incumbents.get(left++));
            } else {
                merged.add(replacement.get(right++));
            }
        }
        while (left < incumbents.size()) {
            merged.add(incumbents.get(left++));
        }
        while (right < replacement.size()) {
            merged.add(replacement.get(right++));
        }
        return merged;
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
        Map<Long, List<ClaimRow>> byCell = new HashMap<>(incumbents.size() * 2);
        for (ClaimRow incumbent : incumbents) {
            byCell.computeIfAbsent(incumbent.cell().packed(), ignored -> new ArrayList<>()).add(incumbent);
        }
        Set<ClaimRow> contested = new LinkedHashSet<>();
        for (ClaimRow requested : request.rows()) {
            List<ClaimRow> atCell = byCell.get(requested.cell().packed());
            if (atCell == null) {
                continue;
            }
            for (ClaimRow incumbent : atCell) {
                if (ClaimMatrix.conflicts(requested, incumbent)) {
                    contested.add(incumbent);
                }
            }
        }
        if (contested.isEmpty()) {
            return null;
        }
        return new ClaimVerdict.Conflict(new ArrayList<>(contested));
    }

}
