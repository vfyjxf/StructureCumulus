package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The pre-images one build run committed, in commit order. The single build keeps one entry; a set
 * build passes one journal to every member, so it can tear the members down in reverse and leave one
 * undo entry behind.
 */
public final class BuildJournal {

    private final ServerLevel level;
    private final UUID player;
    private final Map<LatticePoint, BlockPlacement> merged = new LinkedHashMap<>();
    private final List<Map<LatticePoint, BlockPlacement>> committed = new ArrayList<>();

    public BuildJournal(ServerLevel level, UUID player) {
        this.level = Objects.requireNonNull(level, "level");
        this.player = Objects.requireNonNull(player, "player");
    }

    /** Appends one committed write's pre-image and republishes the merged map as the undo entry. */
    public void record(Map<LatticePoint, BlockPlacement> preImage) {
        Objects.requireNonNull(preImage, "preImage");
        committed.add(Map.copyOf(preImage));
        for (Map.Entry<LatticePoint, BlockPlacement> entry : preImage.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        BuildHistory.record(level, player, merged);
    }

    /** The recorded pre-images in commit order: one entry per committed write, in call order. */
    public List<Map<LatticePoint, BlockPlacement>> commitOrder() {
        return List.copyOf(committed);
    }

    /** Drops the undo entry this journal published; the world is back at the run's pre-image. */
    public void forget() {
        BuildHistory.forget(level, player);
    }
}
