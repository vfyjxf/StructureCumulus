package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewClientState;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Read-only view of the production preview client state, for the preview scenarios. */
final class PreviewProbe {

    private PreviewProbe() {}

    static @Nullable PreviewSyncPayload session() {
        return PreviewClientState.session();
    }

    static boolean hasSession() {
        return PreviewClientState.session() != null;
    }

    static boolean hudVisible() {
        return PreviewClientState.visible();
    }

    static List<String> hudLines() {
        return PreviewClientState.hudLines();
    }

    static boolean hudHas(String prefix) {
        for (String line : hudLines()) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static @Nullable NamespacedId blueprintId() {
        PreviewSyncPayload session = session();
        return session == null ? null : session.blueprintId();
    }

    static @Nullable VariantSummary variants() {
        PreviewSyncPayload session = session();
        return session == null ? null : session.variants();
    }

    static int candidateCount() {
        PreviewSyncPayload session = session();
        return session == null ? 0 : session.candidates().size();
    }

    static int current() {
        PreviewSyncPayload session = session();
        return session == null ? -1 : session.current();
    }

    static @Nullable Siting siting() {
        PreviewSyncPayload session = session();
        return session == null ? null : session.candidates().get(session.current()).siting();
    }

    static int ghostCount() {
        PreviewSyncPayload session = session();
        return session == null ? 0 : session.ghostCells().size();
    }

    static int cells(CellMark mark) {
        PreviewSyncPayload session = session();
        if (session == null) {
            return 0;
        }
        int count = 0;
        for (CellMark found : session.overlay().cells().values()) {
            if (found == mark) {
                count++;
            }
        }
        return count;
    }

    static int cellCount() {
        PreviewSyncPayload session = session();
        return session == null ? 0 : session.overlay().cells().size();
    }

    static int markerCount() {
        PreviewSyncPayload session = session();
        return session == null ? 0 : session.markers().size();
    }

    static boolean hasMarker(String fragment) {
        PreviewSyncPayload session = session();
        if (session == null) {
            return false;
        }
        for (var marker : session.markers()) {
            if (marker.label().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    static boolean problemsOnly() {
        return PreviewClientState.problemsOnly();
    }

    static int sliceY() {
        return PreviewClientState.sliceY();
    }
}
