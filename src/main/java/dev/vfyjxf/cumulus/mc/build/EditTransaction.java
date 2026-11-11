package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A transaction over live cells: {@code open} captures the pre-image, every step applies in plan
 * order, and a rollback restores what was written in reverse order.
 */
public final class EditTransaction implements AutoCloseable {

    private final ServerLevel level;
    private final Map<LatticePoint, BlockPlacement> preImage;
    private final Map<Integer, Map<LatticePoint, BlockPlacement>> applied = new TreeMap<>();
    private boolean closed;

    private EditTransaction(ServerLevel level, Map<LatticePoint, BlockPlacement> preImage) {
        this.level = level;
        this.preImage = Collections.unmodifiableMap(preImage);
    }

    public static EditTransaction open(ServerLevel level, CuboidSet touchedCells) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(touchedCells, "touchedCells");
        HolderLookup.Provider registries = level.registryAccess();
        Map<LatticePoint, BlockPlacement> preImage = new LinkedHashMap<>();
        for (LatticePoint cell : Cells.of(touchedCells)) {
            BlockPos pos = pos(cell);
            BlockEntity entity = level.getBlockEntity(pos);
            CompoundTag data = entity == null ? null : entity.saveWithFullMetadata(registries);
            preImage.put(cell, new BlockPlacement(level.getBlockState(pos), data));
        }
        return new EditTransaction(level, preImage);
    }

    /** Writes one step's cells in the map's order; a cell already at its target is skipped. */
    public ApplyReport.Step apply(int stepIndex, Map<LatticePoint, BlockPlacement> writes) {
        requireOpen();
        Objects.requireNonNull(writes, "writes");
        applied.put(stepIndex, Collections.unmodifiableMap(new LinkedHashMap<>(writes)));
        int written = 0;
        try {
            for (Map.Entry<LatticePoint, BlockPlacement> write : writes.entrySet()) {
                if (atTarget(write.getKey(), write.getValue())) {
                    continue;
                }
                write(write.getKey(), write.getValue());
                written++;
            }
        } catch (RuntimeException failure) {
            return new ApplyReport.Step(stepIndex, ApplyReport.Outcome.failed, reason(failure));
        }
        return new ApplyReport.Step(
            stepIndex,
            written == 0 ? ApplyReport.Outcome.skipped : ApplyReport.Outcome.applied,
            null
        );
    }

    /** Restores the given steps in reverse order; a step whose restore fails is named, never retried. */
    public List<ApplyReport.Step> rollback(List<ApplyReport.Step> steps) {
        requireOpen();
        Objects.requireNonNull(steps, "steps");
        List<ApplyReport.Step> ordered = new ArrayList<>(steps);
        Collections.reverse(ordered);
        List<ApplyReport.Step> restored = new ArrayList<>(ordered.size());
        for (ApplyReport.Step step : ordered) {
            Map<LatticePoint, BlockPlacement> writes = applied.get(step.index());
            if (writes == null) {
                continue;
            }
            String failure = restore(writes);
            restored.add(
                new ApplyReport.Step(
                    step.index(),
                    failure == null ? ApplyReport.Outcome.restored : ApplyReport.Outcome.failed,
                    failure
                )
            );
        }
        return List.copyOf(restored);
    }

    /** The live pre-image of every touched cell, in capture order. */
    public Map<LatticePoint, BlockPlacement> preImage() {
        return preImage;
    }

    @Override
    public void close() {
        closed = true;
    }

    private @Nullable String restore(Map<LatticePoint, BlockPlacement> writes) {
        try {
            for (LatticePoint cell : writes.keySet()) {
                BlockPlacement before = preImage.get(cell);
                if (before == null) {
                    return "no pre-image for " + cell;
                }
                if (!atTarget(cell, before)) {
                    write(cell, before);
                }
            }
            return null;
        } catch (RuntimeException failure) {
            return reason(failure);
        }
    }

    private void write(LatticePoint cell, BlockPlacement placement) {
        BlockPos pos = pos(cell);
        if (!level.setBlock(pos, placement.state(), Block.UPDATE_ALL)) {
            throw new IllegalStateException("the level refused the write at " + pos.toShortString());
        }
        CompoundTag data = placement.blockEntityData();
        if (data == null) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            throw new IllegalStateException("no block entity at " + pos.toShortString());
        }
        entity.loadWithComponents(data.copy(), level.registryAccess());
        entity.setChanged();
    }

    private boolean atTarget(LatticePoint cell, BlockPlacement target) {
        BlockPos pos = pos(cell);
        BlockState live = level.getBlockState(pos);
        if (!live.equals(target.state())) {
            return false;
        }
        CompoundTag data = target.blockEntityData();
        if (data == null) {
            return true;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && entity.saveWithFullMetadata(level.registryAccess()).equals(data);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("the transaction is closed");
        }
    }

    private static BlockPos pos(LatticePoint cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static String reason(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }
}
