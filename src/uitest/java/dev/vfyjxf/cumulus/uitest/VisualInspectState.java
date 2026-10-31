package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** The dev inspect card's current target; set by the right-click handler or the inspect command. */
public final class VisualInspectState {

    private static volatile @Nullable VisualPart inspected;
    private static volatile @Nullable LatticePoint inspectedCell;

    private VisualInspectState() {}

    public static @Nullable VisualPart inspected() {
        return inspected;
    }

    public static @Nullable LatticePoint inspectedCell() {
        return inspectedCell;
    }

    static void inspect(VisualPart part, LatticePoint cell) {
        inspected = part;
        inspectedCell = cell;
    }

    static void inspectAt(LatticePoint cell) {
        VisualPart part = VisualPartState.partAt(cell);
        inspected = part;
        inspectedCell = part == null ? null : cell;
    }

    /** Drops the card when its target instance/part or target block disappears. */
    static void tick() {
        VisualPart current = inspected;
        if (current == null) {
            return;
        }
        VisualSnapshot snapshot = VisualClientState.snapshot();
        if (snapshot == null || !hasInstance(snapshot, current) || !targetBlockPresent()) {
            clear();
        }
    }

    private static boolean hasInstance(VisualSnapshot snapshot, VisualPart part) {
        for (VisualInstance instance : snapshot.instances()) {
            if (instance.id().equals(part.instance())) {
                return true;
            }
        }
        return false;
    }

    private static boolean targetBlockPresent() {
        Minecraft minecraft = Minecraft.getInstance();
        LatticePoint cell = inspectedCell;
        if (minecraft.level == null || cell == null) {
            return false;
        }
        return !minecraft.level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z())).isAir();
    }

    static void clear() {
        inspected = null;
        inspectedCell = null;
    }
}
