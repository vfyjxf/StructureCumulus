package dev.vfyjxf.cumulus.mc.preview.scene;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * One blueprint's expected states as an isometric scene inside a GUI: the cells resolved against the
 * client's block registry, the camera the drag rotates and the Y slice that reveals the layers up to
 * the chosen one. Depth-sorted cells plus the GUI pass's depth buffer order the cubes, and the batch
 * is flushed without {@code GuiGraphics.flush()} so depth writes survive it (design 15 §6).
 */
public final class PreviewScene {

    /** One expected cell: the wire description it came from, plus the client state it resolves to. */
    public record Cell(GhostCell ghost, @Nullable BlockState state) {}

    /** How a panel filters the entry's cells by Y: the whole entry, the layers up to one, or exactly one. */
    public enum LayerRule {
        all, upTo, exact
    }

    /** One panel's rectangle and the camera its mode gives it; the fit is always the drawn hull. */
    public record View(int x, int y, int width, int height, float yaw, float pitch, LayerRule rule) {}

    /** What one panel drew: its cell count and the Y layer it isolates or highlights. */
    public record Panel(int cells, int layer) {}

    /** How one cell is drawn this frame: its alpha factor, its pop scale and its displacement (§7.2, §8.1). */
    public record Sight(float alpha, float scale, float dx, float dy, float dz) {

        public static final Sight full = new Sight(1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
    }

    /** One frame's per-cell treatment; null means the stage hides the cell (design 17 §7.2). */
    public interface Treatment {

        @Nullable
        Sight of(Cell cell);
    }

    private record Drawable(Cell cell, Sight sight) {}

    /** The GUI depth at which the whole scene sits, in front of every screen background (design 17 §5.1). */
    public static final int sceneDepth = 150;
    /** The model pass's depth band above {@link #sceneDepth}: every drawn cell lands inside it. */
    public static final int sceneSpan = 64;
    /** The chrome depth above every model in both projections (design 17 §4.1). */
    public static final int chromeDepth = sceneDepth + sceneSpan + 10;
    private static final int bandColor = 0x38FFFFFF;
    private static final int bandEdgeColor = 0x58FFFFFF;

    private final List<Cell> cells;
    private final Hull hull;
    private final int minY;
    private final int maxY;

    private PreviewScene(List<Cell> cells) {
        this.cells = List.copyOf(cells);
        this.hull = hullOf(cells, PreviewScene::draws);
        if (this.hull.isEmpty()) {
            this.minY = 0;
            this.maxY = -1;
        } else {
            this.minY = (int) this.hull.minY();
            this.maxY = (int) this.hull.maxY() - 1;
        }
    }

    public static PreviewScene of(PreviewCatalogPayload.Entry entry) {
        List<Cell> resolved = new ArrayList<>(entry.cells().size());
        for (GhostCell ghost : entry.cells()) {
            resolved.add(new Cell(ghost, CellStates.of(ghost.block(), ghost.properties())));
        }
        return new PreviewScene(resolved);
    }

    /** The box of the cells that draw something; an air or unresolvable cell contributes no corner. */
    static Hull hullOf(List<Cell> cells, Predicate<Cell> draws) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean any = false;
        for (Cell cell : cells) {
            if (!draws.test(cell)) {
                continue;
            }
            LatticePoint at = cell.ghost().cell();
            minX = Math.min(minX, at.x());
            minY = Math.min(minY, at.y());
            minZ = Math.min(minZ, at.z());
            maxX = Math.max(maxX, at.x() + 1.0F);
            maxY = Math.max(maxY, at.y() + 1.0F);
            maxZ = Math.max(maxZ, at.z() + 1.0F);
            any = true;
        }
        return any ? new Hull(minX, minY, minZ, maxX, maxY, maxZ) : new Hull(0.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F);
    }

    /** The drawn hull of §3.3: the fit input of every panel, never the declared bounds. */
    public Hull hull() {
        return hull;
    }

    /** The drawn hull of one stage's cells: the stage framing's fit box (design 17 §7.5). */
    public Hull hull(int stage) {
        List<Cell> owned = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.ghost().stageIndex() == stage) {
                owned.add(cell);
            }
        }
        return hullOf(owned, PreviewScene::draws);
    }

    /** True when the cell's expected state resolves to a block that draws something (design 17 §3.3). */
    public static boolean draws(Cell cell) {
        BlockState state = cell.state();
        return state != null && !state.isAir();
    }

    public int cellCount() {
        return cells.size();
    }

    public List<Cell> cells() {
        return cells;
    }

    /** The number of Y layers the drawn cells span; at least one, so the slider always has a range. */
    public int layers() {
        return maxY < minY ? 1 : maxY - minY + 1;
    }

    @Nullable
    public Cell hover(double mouseX, double mouseY, View view, int layer) {
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
            if (!draws(cell) || !visible(cell, view.rule(), layer)) {
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
    public Vector2f point(View view, int layer, LatticePoint at) {
        for (Cell cell : cells) {
            if (draws(cell) && cell.ghost().cell().equals(at) && visible(cell, view.rule(), layer)) {
                return projection(view).screenOf(at);
            }
        }
        return null;
    }

    /** Draws the panel's cells and returns what it covered. */
    public Panel draw(GuiGraphics graphics, View view, int layer, boolean band) {
        List<Cell> drawn = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            if (draws(cell) && visible(cell, view.rule(), layer)) {
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

    /**
     * The cell under the cursor in one screen viewport: the nearest drawn cell whose box the ray
     * crosses, using the cells' current positions (design 17 §5.3).
     */
    @Nullable
    public Cell hover(double mouseX, double mouseY, Fit fit) {
        return hover(mouseX, mouseY, fit, cell -> Sight.full);
    }

    /** The hover of the treated page: a cell the stage hides is not pickable (design 17 §5.3). */
    @Nullable
    public Cell hover(double mouseX, double mouseY, Fit fit, Treatment treatment) {
        if (mouseX < fit.x()
                || mouseX >= fit.x() + fit.width()
                || mouseY < fit.y()
                || mouseY >= fit.y() + fit.height()) {
            return null;
        }
        float centreX = fit.x() + fit.width() * 0.5F;
        float centreY = fit.y() + fit.height() * 0.5F;
        float[] originView;
        float[] directionView;
        if (fit.camera().projection() == SceneCamera.Projection.orthographic) {
            originView = new float[]{(float) ((mouseX - centreX) * fit.unitsPerPixel()),
                    (float) (-(mouseY - centreY) * fit.unitsPerPixel()), 10_000.0F};
            directionView = new float[]{0.0F, 0.0F, -1.0F};
        } else {
            originView = new float[]{0.0F, 0.0F, fit.camera().distance()};
            directionView = new float[]{(float) ((mouseX - centreX) / fit.focal()),
                    (float) (-(mouseY - centreY) / fit.focal()), -1.0F};
        }
        float[] origin = fit.camera().worldPoint(originView[0], originView[1], originView[2]);
        float[] direction = fit.camera().world(directionView[0], directionView[1], directionView[2]);
        Vector3f rayOrigin = new Vector3f(origin[0], origin[1], origin[2]);
        Vector3f rayDirection = new Vector3f(direction[0], direction[1], direction[2]);
        Cell nearest = null;
        float nearestExit = Float.POSITIVE_INFINITY;
        for (Cell cell : cells) {
            if (!draws(cell)) {
                continue;
            }
            Sight sight = treatment.of(cell);
            if (sight == null || sight.alpha() <= 0.0F) {
                continue;
            }
            LatticePoint at = cell.ghost().cell();
            float exit = exit(rayOrigin, rayDirection, at.x() + sight.dx(), at.y() + sight.dy(), at.z() + sight.dz());
            if (exit > 0.0F && exit < nearestExit) {
                nearestExit = exit;
                nearest = cell;
            }
        }
        return nearest;
    }

    /** Where one drawn cell's centre lands in one screen viewport, or null when it does not draw. */
    @Nullable
    public Vector2f point(Fit fit, LatticePoint at) {
        for (Cell cell : cells) {
            if (draws(cell) && cell.ghost().cell().equals(at)) {
                return fit.project(at.x() + 0.5F, at.y() + 0.5F, at.z() + 0.5F);
            }
        }
        return null;
    }

    /** Draws the page through one screen viewport's camera and fit, every cell at full alpha. */
    public Panel draw(GuiGraphics graphics, Fit fit) {
        return draw(graphics, fit, cell -> Sight.full);
    }

    /** Draws the treated cells through one screen viewport's camera and fit (design 17 §7.2, §8.1). */
    public Panel draw(GuiGraphics graphics, Fit fit, Treatment treatment) {
        List<Drawable> drawn = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            if (!draws(cell)) {
                continue;
            }
            Sight sight = treatment.of(cell);
            if (sight == null || sight.alpha() <= 0.0F) {
                continue;
            }
            drawn.add(new Drawable(cell, sight));
        }
        if (drawn.isEmpty()) {
            return new Panel(0, 0);
        }
        drawn.sort(Comparator.comparingDouble(drawable -> {
            LatticePoint at = drawable.cell().ghost().cell();
            Sight sight = drawable.sight();
            return fit.depth(at.x() + 0.5F + sight.dx(), at.y() + 0.5F + sight.dy(), at.z() + 0.5F + sight.dz());
        }));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, sceneDepth);
        pose.mulPose(fit.matrix());
        pose.last().normal().set(SceneLighting.normals(fit.camera()));
        Vector3f[] lights = SceneLighting.forCamera(fit.camera());
        try {
            RenderSystem.setShaderLights(lights[0], lights[1]);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            for (Drawable drawable : drawn) {
                Cell cell = drawable.cell();
                Sight sight = drawable.sight();
                BlockState state = cell.state();
                if (state == null) {
                    continue;
                }
                LatticePoint at = cell.ghost().cell();
                pose.pushPose();
                pose.translate(at.x() + sight.dx(), at.y() + sight.dy(), at.z() + sight.dz());
                if (sight.scale() != 1.0F) {
                    pose.translate(0.5F, 0.5F, 0.5F);
                    pose.scale(sight.scale(), sight.scale(), sight.scale());
                    pose.translate(-0.5F, -0.5F, -0.5F);
                }
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    state,
                    pose,
                    sight.alpha() >= 1.0F
                            ? graphics.bufferSource()
                            : TintedBuffers.of(graphics.bufferSource(), sight.alpha()),
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    null
                );
                pose.popPose();
            }
            graphics.bufferSource().endBatch();
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
        return new Panel(drawn.size(), 0);
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
        Hull box = hull.fitBox();
        float shiftX = box.centreX();
        float shiftY = box.centreY();
        float shiftZ = box.centreZ();
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            float cx = (corner & 1) == 0 ? box.minX() : box.maxX();
            float cy = (corner & 2) == 0 ? box.minY() : box.maxY();
            float cz = (corner & 4) == 0 ? box.minZ() : box.maxZ();
            Vector3f rotated = rotation.transform(new Vector3f(cx - shiftX, cy - shiftY, cz - shiftZ));
            minU = Math.min(minU, rotated.x);
            maxU = Math.max(maxU, rotated.x);
            minV = Math.min(minV, rotated.y);
            maxV = Math.max(maxV, rotated.y);
        }
        float scale = Math.min(
            width * Fit.margin / Math.max(0.001F, maxU - minU),
            height * Fit.margin / Math.max(0.001F, maxV - minV)
        );
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
        return exit(
            origin.x - projection.shiftX(),
            origin.y - projection.shiftY(),
            origin.z - projection.shiftZ(),
            direction.x,
            direction.y,
            direction.z,
            at.x() - projection.shiftX(),
            at.y() - projection.shiftY(),
            at.z() - projection.shiftZ()
        );
    }

    private static float exit(Vector3f origin, Vector3f direction, float minX, float minY, float minZ) {
        return exit(origin.x, origin.y, origin.z, direction.x, direction.y, direction.z, minX, minY, minZ);
    }

    private static float exit(
        float originX,
        float originY,
        float originZ,
        float directionX,
        float directionY,
        float directionZ,
        float minX,
        float minY,
        float minZ
    ) {
        float[] boxMin = {minX, minY, minZ};
        float[] boxMax = {minX + 1.0F, minY + 1.0F, minZ + 1.0F};
        float[] originParts = {originX, originY, originZ};
        float[] directionParts = {directionX, directionY, directionZ};
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
