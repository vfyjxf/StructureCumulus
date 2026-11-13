package dev.vfyjxf.cumulus.mc.preview.scene;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * The base plate of design 17 §4.7: a platform of slab blocks, two cells on a side and half a cell
 * thick, whose tops lie in the drawn hull's floor and whose grid extends one tile past the footprint
 * on every side. Scene geometry that orbits with the cells and never becomes one: no hull, no stage,
 * no pick ray and no explode sees it.
 */
public record BasePlate(int minX, int minZ, int maxX, int maxZ, int y) {

    /** One slab tile's side, in cells. */
    public static final int tileSize = 2;
    /** One slab tile's thickness, in cells: the platform's visible rim (§4.7). */
    public static final float thickness = 0.5F;
    /** The quads one tile emits: top, bottom and the four sides. */
    public static final int facesPerTile = 6;

    /** The lighter of the plate's two chrome greys. */
    public static final int lightTile = 0xFFEDEDED;
    /** The darker of the plate's two chrome greys; unlit, so no document block can pass for it. */
    public static final int darkTile = 0xFFCFCFCF;

    private static final BasePlate empty = new BasePlate(0, 0, 0, 0, 0);

    /**
     * The platform under one drawn hull: its footprint snapped out to the two-cell grid, one tile past
     * it on every side, the tops at the hull's own floor.
     */
    public static BasePlate of(Hull hull) {
        if (hull.isEmpty()) {
            return empty;
        }
        int minTileX = Math.floorDiv((int) Math.floor(hull.minX()), tileSize) - 1;
        int maxTileX = Math.ceilDiv((int) Math.ceil(hull.maxX()), tileSize) + 1;
        int minTileZ = Math.floorDiv((int) Math.floor(hull.minZ()), tileSize) - 1;
        int maxTileZ = Math.ceilDiv((int) Math.ceil(hull.maxZ()), tileSize) + 1;
        return new BasePlate(
            minTileX * tileSize,
            minTileZ * tileSize,
            maxTileX * tileSize,
            maxTileZ * tileSize,
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
        return isEmpty() ? 0 : ((maxX - minX) / tileSize) * ((maxZ - minZ) / tileSize);
    }

    /** The quads the whole platform emits: every tile is a closed box (§4.7). */
    public int quads() {
        return tiles() * facesPerTile;
    }

    /** The tile's colour: the two greys alternate by the tile's own parity, the paving of §4.7. */
    public int color(int x, int z) {
        return Math.floorMod(Math.floorDiv(x, tileSize) + Math.floorDiv(z, tileSize), 2) == 0 ? lightTile : darkTile;
    }

    /** One tile's box: its top at {@link #y}, its bottom {@link #thickness} of a cell under it. */
    public Hull tile(int x, int z) {
        return new Hull(x, y - thickness, z, x + tileSize, y, z + tileSize);
    }

    /**
     * Emits the tiles in the current pose as unlit colour boxes and returns how many it drew: a model
     * of the scene pass, so the depth test lets a cell occlude the slab under it.
     */
    public int draw(GuiGraphics graphics) {
        if (isEmpty()) {
            return 0;
        }
        Matrix4f matrix = graphics.pose().last().pose();
        // The one vanilla type that is unlit colour quads with the depth test and the depth write on.
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.debugQuads());
        for (int x = minX; x < maxX; x += tileSize) {
            for (int z = minZ; z < maxZ; z += tileSize) {
                box(consumer, matrix, x, z, color(x, z));
            }
        }
        return tiles();
    }

    private void box(VertexConsumer consumer, Matrix4f matrix, int x, int z, int color) {
        int farX = x + tileSize;
        int farZ = z + tileSize;
        float bottom = y - thickness;
        quad(consumer, matrix, color, x, y, z, x, y, farZ, farX, y, farZ, farX, y, z);
        quad(consumer, matrix, color, x, bottom, z, farX, bottom, z, farX, bottom, farZ, x, bottom, farZ);
        quad(consumer, matrix, color, x, bottom, z, farX, bottom, z, farX, y, z, x, y, z);
        quad(consumer, matrix, color, x, bottom, farZ, x, y, farZ, farX, y, farZ, farX, bottom, farZ);
        quad(consumer, matrix, color, x, bottom, z, x, y, z, x, y, farZ, x, bottom, farZ);
        quad(consumer, matrix, color, farX, bottom, z, farX, bottom, farZ, farX, y, farZ, farX, y, z);
    }

    private static void quad(
        VertexConsumer consumer,
        Matrix4f matrix,
        int color,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3
    ) {
        consumer.addVertex(matrix, x0, y0, z0).setColor(color);
        consumer.addVertex(matrix, x1, y1, z1).setColor(color);
        consumer.addVertex(matrix, x2, y2, z2).setColor(color);
        consumer.addVertex(matrix, x3, y3, z3).setColor(color);
    }
}
