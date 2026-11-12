package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One transaction's changed cells, in the order the transaction performed them, and what the run
 * did: a commit, a rollback or an undo. Plain data, so a dedicated server never loads a client class.
 */
public record BuildCommitPayload(List<CommitCell> cells, Status status, boolean formed, int truncated)
        implements
            CustomPacketPayload {

    /** The animated-cell cap of one payload; an overflow travels as {@code truncated}. */
    public static final int maxAnimatedCells = 512;

    public static final Type<BuildCommitPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.modId, "build_commit")
    );

    public BuildCommitPayload {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(status, "status");
        cells = List.copyOf(cells);
        if (cells.size() > maxAnimatedCells) {
            throw new IllegalArgumentException("a payload animates at most " + maxAnimatedCells + " cells");
        }
        if (truncated < 0) {
            throw new IllegalArgumentException("truncated must not be negative: " + truncated);
        }
        if (formed && status != Status.committed) {
            throw new IllegalArgumentException("only a committed run can be formed: " + status);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }

    public enum Status {
        committed, rolledBack, undone
    }

    /** The state one cell holds after the run: the new block for a commit, the pre-image for a rollback. */
    public record CommitCell(LatticePoint cell, NamespacedId block, Map<String, String> properties) {

        public CommitCell {
            Objects.requireNonNull(cell, "cell");
            Objects.requireNonNull(block, "block");
            properties = Map.copyOf(properties);
        }
    }
}
