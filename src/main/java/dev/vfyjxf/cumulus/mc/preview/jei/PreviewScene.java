package dev.vfyjxf.cumulus.mc.preview.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One blueprint's expected states as an isometric scene inside a GUI: the cells resolved against the
 * client's block registry, the camera the drag rotates and the Y slice that reveals the layers up to
 * the chosen one. Depth-sorted cells plus the GUI pass's depth buffer order the cubes, and the batch
 * is flushed without {@code GuiGraphics.flush()} so depth writes survive it (design 15 §6).
 */
final class PreviewScene {

    /** One expected cell: the wire description it came from, plus the client state it resolves to. */
    record Cell(GhostCell ghost, @Nullable BlockState state) {}

    /** How a panel filters the entry's cells by Y: the whole entry, the layers up to one, or exactly one. */
    enum LayerRule {
        all, upTo, exact
    }

    /** One panel's rectangle and the camera its mode gives it; the fit is always the entry's bounds. */
    record View(int x, int y, int width, int height, float yaw, float pitch, LayerRule rule) {}

    /** What one panel drew: its cell count and the Y layer it isolates or highlights. */
    record Panel(int cells, int layer) {}

    private static final float margin = 0.86F;
    private static final int sceneDepth = 150;
    private static final int bandColor = 0x38FFFFFF;
    private static final int bandEdgeColor = 0x58FFFFFF;

    private final List<Cell> cells;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private PreviewScene(List<Cell> cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.cells = List.copyOf(cells);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    static PreviewScene of(PreviewCatalogPayload.Entry entry) {
        List<Cell> resolved = new ArrayList<>(entry.cells().size());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (GhostCell ghost : entry.cells()) {
            LatticePoint at = ghost.cell();
            minX = Math.min(minX, at.x());
            minY = Math.min(minY, at.y());
            minZ = Math.min(minZ, at.z());
            maxX = Math.max(maxX, at.x());
            maxY = Math.max(maxY, at.y());
            maxZ = Math.max(maxZ, at.z());
            resolved.add(new Cell(ghost, resolve(ghost)));
        }
        return new PreviewScene(resolved, minX, minY, minZ, maxX, maxY, maxZ);
    }

    int cellCount() {
        return cells.size();
    }

    List<Cell> cells() {
        return cells;
    }

    /** The number of Y layers the blueprint spans; at least one, so the slider always has a range. */
    int layers() {
        return maxY < minY ? 1 : maxY - minY + 1;
    }

    @Nullable
    Cell hover(double mouseX, double mouseY, View view, int layer) {
        if (mouseX < view.x()
                || mouseX >= view.x() + view.width()
                || mouseY < view.y()
                || mouseY >= view.y() + view.height()) {
            return null;
        }
        Projection projection = projection(view);
        Vector3f origin = projection.origin(mouseX, mouseY);
        Vector3f direction = projection.direction();
        Cell nearest = null;
        float nearestExit = Float.NEGATIVE_INFINITY;
        for (Cell cell : cells) {
            if (!visible(cell, view.rule(), layer)) {
                continue;
            }
            float exit = exit(origin, direction, cell.ghost().cell(), projection);
            if (exit > 0.0F && exit > nearestExit) {
                nearestExit = exit;
                nearest = cell;
            }
        }
        return nearest;
    }

    /** Where one cell's centre lands inside a panel, or null when that panel filters it out. */
    @Nullable
    Vector2f point(View view, int layer, LatticePoint at) {
        for (Cell cell : cells) {
            if (cell.ghost().cell().equals(at) && visible(cell, view.rule(), layer)) {
                return projection(view).screenOf(at);
            }
        }
        return null;
    }

    /** Draws the panel's cells and returns what it covered. */
    Panel draw(GuiGraphics graphics, View view, int layer, boolean band) {
        List<Cell> drawn = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            if (visible(cell, view.rule(), layer) && cell.state() != null) {
                drawn.add(cell);
            }
        }
        if (drawn.isEmpty()) {
            return new Panel(0, layer);
        }
        Projection projection = projection(view);
        drawn.sort(Comparator.comparingDouble(cell -> projection.depth(cell.ghost().cell())));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(projection.screenX(), projection.screenY(), sceneDepth);
        pose.scale(projection.scale(), -projection.scale(), projection.scale());
        pose.mulPose(projection.rotation());
        pose.translate(-projection.shiftX(), -projection.shiftY(), -projection.shiftZ());
        Lighting.setupFor3DItems();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        for (Cell cell : drawn) {
            BlockState state = cell.state();
            if (state == null) {
                continue;
            }
            LatticePoint at = cell.ghost().cell();
            pose.pushPose();
            pose.translate(at.x(), at.y(), at.z());
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state,
                pose,
                graphics.bufferSource(),
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                null
            );
            pose.popPose();
        }
        // GuiGraphics.flush() disables the depth test around the batch, which would drop this scene's occlusion.
        graphics.bufferSource().endBatch();
        pose.popPose();
        if (band) {
            drawBand(graphics, view, projection, layer);
        }
        return new Panel(drawn.size(), layer);
    }

    private void drawBand(GuiGraphics graphics, View view, Projection projection, int layer) {
        float bottom = projection.screenYOf(minY + layer);
        float top = projection.screenYOf(minY + layer + 1);
        int topY = Math.round(Math.min(top, bottom));
        int bottomY = Math.round(Math.max(top, bottom));
        graphics.fill(view.x(), topY, view.x() + view.width(), bottomY, bandColor);
        graphics.fill(view.x(), topY, view.x() + view.width(), topY + 1, bandEdgeColor);
        graphics.fill(view.x(), bottomY - 1, view.x() + view.width(), bottomY, bandEdgeColor);
    }

    private boolean visible(Cell cell, LayerRule rule, int layer) {
        int offset = cell.ghost().cell().y() - minY;
        return switch (rule) {
            case all -> true;
            case upTo -> offset <= layer;
            case exact -> offset == layer;
        };
    }

    private static @Nullable BlockState resolve(GhostCell ghost) {
        NamespacedId id = ghost.block();
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(key);
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> property : ghost.properties().entrySet()) {
            state = apply(state, property.getKey(), property.getValue());
        }
        return state;
    }

    private static BlockState apply(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return withValue(state, property, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(
        BlockState state,
        Property<T> property,
        String value
    ) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(typed -> state.setValue(property, typed)).orElse(state);
    }

    /** The cube-to-screen map of one camera pose: orthographic, one unit of the scene per {@code scale} pixels. */
    private record Projection(
        Quaternionf rotation,
        Quaternionf inverse,
        float scale,
        float shiftX,
        float shiftY,
        float shiftZ,
        float screenX,
        float screenY
    ) {

        float depth(LatticePoint at) {
            return rotate(at.x() + 0.5F, at.y() + 0.5F, at.z() + 0.5F).z;
        }

        Vector3f rotate(float x, float y, float z) {
            return rotation.transform(new Vector3f(x - shiftX, y - shiftY, z - shiftZ));
        }

        /** The world point the cursor projects to on the scene's mid plane. */
        Vector3f origin(double mouseX, double mouseY) {
            float u = (float) ((mouseX - screenX) / scale);
            float v = (float) -((mouseY - screenY) / scale);
            Vector3f point = inverse.transform(new Vector3f(u, v, 0.0F));
            return point.add(shiftX, shiftY, shiftZ);
        }

        /** Toward the viewer: the ray parameter is the depth the pick compares. */
        Vector3f direction() {
            return inverse.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        }

        /** The panel point the centre of one cell projects to. */
        Vector2f screenOf(LatticePoint at) {
            Vector3f rotated = rotate(at.x() + 0.5F, at.y() + 0.5F, at.z() + 0.5F);
            return new Vector2f(screenX + rotated.x * scale, screenY - rotated.y * scale);
        }

        /** The panel row a horizontal Y plane sits on, used by the three-view's section band. */
        float screenYOf(float worldY) {
            return screenY - rotate(0.0F, worldY, 0.0F).y * scale;
        }
    }

    private Projection projection(View view) {
        int width = view.width();
        int height = view.height();
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(view.yaw()))
                .rotateX((float) Math.toRadians(view.pitch()));
        float shiftX = (minX + maxX + 1) * 0.5F;
        float shiftY = (minY + maxY + 1) * 0.5F;
        float shiftZ = (minZ + maxZ + 1) * 0.5F;
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            float cx = (corner & 1) == 0 ? minX : maxX + 1;
            float cy = (corner & 2) == 0 ? minY : maxY + 1;
            float cz = (corner & 4) == 0 ? minZ : maxZ + 1;
            Vector3f rotated = rotation.transform(new Vector3f(cx - shiftX, cy - shiftY, cz - shiftZ));
            minU = Math.min(minU, rotated.x);
            maxU = Math.max(maxU, rotated.x);
            minV = Math.min(minV, rotated.y);
            maxV = Math.max(maxV, rotated.y);
        }
        float scale = Math
                .min(width * margin / Math.max(0.001F, maxU - minU), height * margin / Math.max(0.001F, maxV - minV));
        return new Projection(
            rotation,
            new Quaternionf(rotation).invert(),
            scale,
            shiftX,
            shiftY,
            shiftZ,
            view.x() + width * 0.5F,
            view.y() + height * 0.5F
        );
    }

    private static float exit(Vector3f origin, Vector3f direction, LatticePoint at, Projection projection) {
        float minX = at.x() - projection.shiftX();
        float minY = at.y() - projection.shiftY();
        float minZ = at.z() - projection.shiftZ();
        float[] boxMin = {minX, minY, minZ};
        float[] boxMax = {minX + 1.0F, minY + 1.0F, minZ + 1.0F};
        float[] originParts = {origin.x - projection.shiftX(), origin.y - projection.shiftY(),
                origin.z - projection.shiftZ()};
        float[] directionParts = {direction.x, direction.y, direction.z};
        float enter = Float.NEGATIVE_INFINITY;
        float leave = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directionParts[axis]) < 1.0E-6F) {
                if (originParts[axis] < boxMin[axis] || originParts[axis] > boxMax[axis]) {
                    return Float.NEGATIVE_INFINITY;
                }
                continue;
            }
            float first = (boxMin[axis] - originParts[axis]) / directionParts[axis];
            float second = (boxMax[axis] - originParts[axis]) / directionParts[axis];
            if (first > second) {
                float swap = first;
                first = second;
                second = swap;
            }
            enter = Math.max(enter, first);
            leave = Math.min(leave, second);
        }
        return enter <= leave ? leave : Float.NEGATIVE_INFINITY;
    }
}
