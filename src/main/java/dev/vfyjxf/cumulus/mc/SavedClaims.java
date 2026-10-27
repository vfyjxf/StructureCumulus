package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One world's claim ledger, persisted as that level's SavedData; writes run on the server thread. */
@NullMarked
public final class SavedClaims extends SavedData implements ClaimLedger {

    /** The saved-data name under the level's data folder. */
    public static final String dataName = Constants.modId + "_claims";

    /** The NBT schema version written by {@link #save}. */
    public static final int dataVersion = 1;

    private static final String versionKey = "version";
    private static final String revisionKey = "revision";
    private static final String rowsKey = "rows";

    private static final Comparator<ClaimRow> rowOrder = Comparator.comparing(ClaimRow::cell)
            .thenComparing(row -> row.owner().id()).thenComparing(ClaimRow::bay)
            .thenComparing(row -> row.siting().origin()).thenComparing(row -> row.siting().placement().orientation())
            .thenComparing(row -> row.siting().placement().offset()).thenComparing(ClaimRow::sharing);

    private final World world;
    private final List<ClaimRow> rows = new ArrayList<>();
    private long revision;

    public SavedClaims(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    private SavedClaims(World world, List<ClaimRow> decoded, long revision) {
        this.world = Objects.requireNonNull(world, "world");
        if (revision < 0) {
            throw new IllegalArgumentException("revision >= 0");
        }
        this.rows.addAll(decoded);
        this.rows.sort(rowOrder);
        this.revision = revision;
    }

    /** Builds the SavedData factory for a world. */
    public static SavedData.Factory<SavedClaims> factory(World world) {
        Objects.requireNonNull(world, "world");
        return new SavedData.Factory<>(() -> new SavedClaims(world), (tag, registries) -> load(world, tag));
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
        ClaimVerdict conflict = conflicts(request);
        if (conflict != null) {
            return conflict;
        }
        List<ClaimRow> replacement = new ArrayList<>(new LinkedHashSet<>(request.rows()));
        replacement.sort(rowOrder);
        List<ClaimRow> next = new ArrayList<>(rows.size() + replacement.size());
        for (ClaimRow row : rows) {
            if (!row.owner().equals(request.owner()) || !row.siting().equals(request.siting())) {
                next.add(row);
            }
        }
        next.addAll(replacement);
        next.sort(rowOrder);
        rows.clear();
        rows.addAll(next);
        revision++;
        setDirty();
        return new ClaimVerdict.Settled(revision, replacement);
    }

    @Override
    public ClaimVerdict release(OwnerId owner, Siting siting) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        boolean removed = rows.removeIf(row -> row.owner().equals(owner) && row.siting().equals(siting));
        if (!removed) {
            return new ClaimVerdict.Settled(revision, List.of());
        }
        revision++;
        setDirty();
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        tag.putInt(versionKey, dataVersion);
        tag.putLong(revisionKey, revision);
        ListTag encoded = new ListTag();
        for (ClaimRow row : rows) {
            encoded.add(ClaimCodec.encode(row));
        }
        tag.put(rowsKey, encoded);
        return tag;
    }

    /** Reads one ledger from its NBT form. */
    public static SavedClaims load(World world, CompoundTag tag) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(tag, "tag");
        int version = tag.getInt(versionKey);
        if (version != dataVersion) {
            throw new IllegalArgumentException("unsupported claim data version " + version);
        }
        long revision = tag.getLong(revisionKey);
        ListTag encoded = tag.getList(rowsKey, Tag.TAG_COMPOUND);
        List<ClaimRow> rows = new ArrayList<>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            rows.add(ClaimCodec.decode(encoded.getCompound(index)));
        }
        return new SavedClaims(world, rows, revision);
    }

    private @Nullable ClaimVerdict conflicts(SettleRequest request) {
        Map<String, Conflict> conflicts = new LinkedHashMap<>();
        for (ClaimRow requested : request.rows()) {
            for (ClaimRow incumbent : rows) {
                if (!ClaimMatrix.conflicts(requested, incumbent)) {
                    continue;
                }
                String key = requested.bay().text() + "|" + incumbent.owner().id().text() + "|"
                        + incumbent.bay().text();
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
        String message = conflict.incumbent.owner().id() + ":" + conflict.incumbent.bay() + " already holds "
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
