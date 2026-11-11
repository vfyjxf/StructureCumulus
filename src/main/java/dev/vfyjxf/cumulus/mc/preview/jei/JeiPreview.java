package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewOpenPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The JEI preview's public face: what the category lists, what its last frame covered, and the one
 * action its button performs. Another renderer integration copies this shape (roadmap §9).
 */
public final class JeiPreview {

    private static volatile int drawnFrames;
    private static volatile int drawnCells;

    private JeiPreview() {}

    /** The blueprint ids the category lists, in the catalog's order. */
    public static List<NamespacedId> blueprintIds() {
        return JeiPreviewPlugin.listedIds();
    }

    /** Frames the category has drawn since the last world join. */
    public static int drawnFrames() {
        return drawnFrames;
    }

    /** The expected cells the last drawn frame covered. */
    public static int drawnCells() {
        return drawnCells;
    }

    /** The "Ghost to world" action: the server opens the ordinary session at the crosshair. */
    public static void openWorld(NamespacedId blueprintId) {
        PacketDistributor.sendToServer(new PreviewOpenPayload(blueprintId));
    }

    /** Pages the category to one blueprint; false when JEI or the entry is not available. */
    public static boolean show(NamespacedId blueprintId) {
        return JeiPreviewPlugin.show(blueprintId);
    }

    static void recordFrame(int cells) {
        drawnCells = cells;
        drawnFrames++;
    }

    static void resetFrames() {
        drawnFrames = 0;
        drawnCells = 0;
    }
}
