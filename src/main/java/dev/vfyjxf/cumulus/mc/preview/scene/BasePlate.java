package dev.vfyjxf.cumulus.mc.preview.scene;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * The base plate of design 17 §4.7: a checkerboard of one-cell tiles in two light greys, lying in the
 * plane of the drawn hull's floor and one tile past its footprint on every side. Scene geometry that
 * orbits with the cells and never becomes one: no hull, no stage, no pick ray and no explode sees it.
 */
public record BasePlate(int minX, int minZ, int maxX, int maxZ, int y) {

    /** The lighter of the plate's two chrome greys. */
    public static final int lightTile = 0xFFEDEDED;
    /** The darker of the plate's two chrome greys; unlit, so no document block can pass for it. */
    public static final int darkTile = 0xFFCFCFCF;

    private static final BasePlate empty = new BasePlate(0, 0, 0, 0, 0);

    /** The floor under one drawn hull: the footprint plus one tile a side, at the hull's own floor. */
    public static BasePlate of(Hull hull) {
        if (hull.isEmpty()) {
            return empty;
        }
        return new BasePlate(
            (int) Math.floor(hull.minX()) - 1,
            (int) Math.floor(hull.minZ()) - 1,
            (int) Math.ceil(hull.maxX()) + 1,
            (int) Math.ceil(hull.maxZ()) + 1,
            (int) Math.floor(hull.minY())
        );
    }

    /** The plate an empty page draws: no hull, no floor (§4.7). */
    public static BasePlate empty() {
        return empty;
    }

    public boolean isEmpty() {
        return maxX <= minX || maxZ <= minZ;
    }

    public int tiles() {
        return isEmpty() ? 0 : (maxX - minX) * (maxZ - minZ);
    }

    /** The tile's colour: the two greys alternate by the tile's own parity, the checkerboard of §4.7. */
    public int color(int x, int z) {
        return Math.floorMod(x + z, 2) == 0 ? lightTile : darkTile;
    }

    /**
     * Emits the tiles in the current pose as unlit colour quads and returns how many it drew: a model of
     * the scene pass, so the depth test lets a cell occlude the tile under it.
     */
    public int draw(GuiGraphics graphics) {
        if (isEmpty()) {
            return 0;
        }
        Matrix4f matrix = graphics.pose().last().pose();
        // The one vanilla type that is unlit colour quads with the depth test and the depth write on.
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.debugQuads());
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                int color = color(x, z);
                vertex(consumer, matrix, x, z, color);
                vertex(consumer, matrix, x, z + 1, color);
                vertex(consumer, matrix, x + 1, z + 1, color);
                vertex(consumer, matrix, x + 1, z, color);
            }
        }
        return tiles();
    }

    private void vertex(VertexConsumer consumer, Matrix4f matrix, int x, int z, int color) {
        consumer.addVertex(matrix, x, y, z).setColor(color);
    }
}
