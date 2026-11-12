package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.preview.net.BuildCommitPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place a run's own report becomes the placement animation: the changed cells in the order
 * the transaction wrote them, capped, to the player who caused the run only.
 */
public final class BuildFeedback {

    private BuildFeedback() {}

    /** Sends a run's writes, or its restored cells when it rolled back; a run that wrote nothing sends nothing. */
    public static void send(ServerPlayer player, ApplyReport report, boolean formed) {
        switch (report.status()) {
            case committed -> send(player, report, BuildCommitPayload.Status.committed, formed);
            case rolledBack, partiallyRolledBack -> send(player, report, BuildCommitPayload.Status.rolledBack, false);
            case refused -> {}
        }
    }

    /** An undo sounds like a rollback; the cells it changed back travel in restore order. */
    public static void undo(ServerPlayer player, ApplyReport report) {
        switch (report.status()) {
            case committed -> send(player, report, BuildCommitPayload.Status.undone, false);
            case rolledBack, partiallyRolledBack -> send(player, report, BuildCommitPayload.Status.rolledBack, false);
            case refused -> {}
        }
    }

    private static void send(
        ServerPlayer player,
        ApplyReport report,
        BuildCommitPayload.Status status,
        boolean formed
    ) {
        List<ApplyReport.Change> changes = report.changes();
        if (changes.isEmpty()) {
            return;
        }
        int shown = Math.min(changes.size(), BuildCommitPayload.maxAnimatedCells);
        List<BuildCommitPayload.CommitCell> cells = new ArrayList<>(shown);
        for (int index = 0; index < shown; index++) {
            ApplyReport.Change change = changes.get(index);
            BlockState state = change.placement().state();
            cells.add(
                new BuildCommitPayload.CommitCell(
                    change.cell(),
                    PreviewExpected.blockId(state.getBlock()),
                    PreviewExpected.properties(state)
                )
            );
        }
        PacketDistributor.sendToPlayer(player, new BuildCommitPayload(cells, status, formed, changes.size() - shown));
    }
}
