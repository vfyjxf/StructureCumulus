package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.mc.preview.client.PlacementAnimator;
import dev.vfyjxf.cumulus.mc.preview.net.BuildCommitPayload;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Read-only view of the placement animator, for the animation scenario. */
final class PlacementProbe {

    private PlacementProbe() {}

    static boolean animating() {
        return PlacementAnimator.animating();
    }

    static int animatingCells() {
        return PlacementAnimator.animatingCells();
    }

    static BuildCommitPayload.@Nullable Status lastStatus() {
        return PlacementAnimator.lastStatus();
    }

    static int soundRequests() {
        return PlacementAnimator.soundRequests();
    }

    static int celebrations() {
        return PlacementAnimator.celebrations();
    }

    static int startedRuns() {
        return PlacementAnimator.startedRuns();
    }

    static int finishedRuns() {
        return PlacementAnimator.finishedRuns();
    }

    static List<String> noteLines() {
        return PlacementAnimator.noteLines();
    }
}
