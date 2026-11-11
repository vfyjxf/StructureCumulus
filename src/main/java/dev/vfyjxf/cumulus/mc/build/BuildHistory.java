package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The undo history: one pre-image per player and level, replayed in reverse as its own transaction.
 * In memory only, and dropped with the level that owns it.
 */
public final class BuildHistory {

    private static final Map<ResourceKey<Level>, Map<UUID, Map<LatticePoint, BlockPlacement>>> history = new HashMap<>();

    public static void record(ServerLevel level, UUID player, Map<LatticePoint, BlockPlacement> preImage) {
        history.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(player, new LinkedHashMap<>(preImage));
    }

    public static boolean has(ServerLevel level, UUID player) {
        Map<UUID, Map<LatticePoint, BlockPlacement>> levelHistory = history.get(level.dimension());
        return levelHistory != null && levelHistory.containsKey(player);
    }

    /** Undoes the player's last build in this level, or answers null when there is nothing to undo. */
    public static @Nullable ApplyReport undo(ServerLevel level, UUID player) {
        Map<UUID, Map<LatticePoint, BlockPlacement>> levelHistory = history.get(level.dimension());
        if (levelHistory == null) {
            return null;
        }
        Map<LatticePoint, BlockPlacement> preImage = levelHistory.remove(player);
        if (preImage == null) {
            return null;
        }
        if (preImage.isEmpty()) {
            return ApplyReport.formed();
        }
        if (levelHistory.isEmpty()) {
            history.remove(level.dimension());
        }
        Cuboid bounds = boundsOf(preImage);
        if (bounds == null) {
            return ApplyReport.formed();
        }
        Map<LatticePoint, BlockPlacement> reversed = new LinkedHashMap<>();
        List<LatticePoint> cells = new ArrayList<>(preImage.keySet());
        for (int index = cells.size() - 1; index >= 0; index--) {
            LatticePoint cell = cells.get(index);
            reversed.put(cell, preImage.get(cell));
        }
        List<Finding> findings = new ArrayList<>();
        try (EditTransaction transaction = EditTransaction.open(level, CuboidSet.of(bounds))) {
            ApplyReport.Step step = transaction.apply(0, reversed);
            if (!step.failed()) {
                return new ApplyReport(ApplyReport.Status.committed, List.of(step), List.of(), FindingBundle.empty());
            }
            List<ApplyReport.Step> restored = transaction.rollback(List.of(step));
            boolean incomplete = restored.stream().anyMatch(ApplyReport.Step::failed);
            findings.add(
                BuildFindings.at(
                    BuildCodes.stepFailureEntry,
                    Phase.settle,
                    "the undo failed at step 0: " + step.reason(),
                    bounds
                )
            );
            if (incomplete) {
                findings.add(
                    BuildFindings.at(
                        BuildCodes.incompleteRollbackEntry,
                        Phase.settle,
                        "the undo could not be rolled back fully",
                        bounds
                    )
                );
            }
            return new ApplyReport(
                incomplete ? ApplyReport.Status.partiallyRolledBack : ApplyReport.Status.rolledBack,
                restored,
                List.of(),
                FindingBundle.of(findings)
            );
        }
    }

    /** The level is gone: its pre-images describe blocks nobody can write any more. */
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            history.remove(level.dimension());
        }
    }

    public static void forgetAll() {
        history.clear();
    }

    private static @Nullable Cuboid boundsOf(Map<LatticePoint, BlockPlacement> cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (LatticePoint cell : cells.keySet()) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        if (maxX < minX) {
            return null;
        }
        return Cuboid.of(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private BuildHistory() {}
}
