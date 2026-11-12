package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The overlay's world layer: one frame per mark, coloured by 4.1's layering, faded by Y-slice
 * distance and culled by reach. A refused confirm pulses its failed cells red (9.1).
 */
final class PreviewOverlayRenderer {

    private static final int missing = 0xCC40D040;
    private static final int present = 0x4050A0FF;
    private static final int wrong = 0xD0FFB040;
    private static final int extra = 0xD0FF4040;
    private static final int blocked = 0xD0B040FF;
    private static final int unknown = 0x90A0A0A0;
    private static final int pulse = 0xE0FF2020;
    private static final long pulseTicks = 40L;
    /** The done stage's frame keeps a quarter of the mark's alpha. */
    private static final float doneFrame = 0.25F;
    private static final int maxFrames = 1_024;

    private static volatile List<LatticePoint> pulsed = List.of();
    private static volatile long pulseUntil;

    private PreviewOverlayRenderer() {}

    /** Keeps the failure pulse alive for the cells an error marker names. */
    static void onPayload(PreviewSyncPayload payload) {
        List<LatticePoint> cells = new ArrayList<>();
        for (Marker marker : payload.markers()) {
            if (marker.kind() != MarkerKind.error || !marker.locus().hasCells()) {
                continue;
            }
            for (var box : marker.locus().cells().decomposition()) {
                for (int x = box.min().x(); x < box.max().x(); x++) {
                    for (int y = box.min().y(); y < box.max().y(); y++) {
                        for (int z = box.min().z(); z < box.max().z(); z++) {
                            cells.add(LatticePoint.of(x, y, z));
                        }
                    }
                }
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        pulsed = List.copyOf(cells);
        pulseUntil = now() + pulseTicks;
    }

    static void clear() {
        pulsed = List.of();
        pulseUntil = 0L;
    }

    static void render(RenderLevelStageEvent event) {
        PreviewSyncPayload session = PreviewClientState.session();
        if (session == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        int drawn = 0;
        OverlayModel overlay = session.overlay();
        boolean problemsOnly = PreviewClientState.problemsOnly();
        for (var entry : overlay.cells().entrySet()) {
            CellMark mark = entry.getValue();
            if (problemsOnly && (mark == CellMark.present || mark == CellMark.unknown)) {
                continue;
            }
            if (drawn >= maxFrames) {
                break;
            }
            LatticePoint cell = entry.getKey().point();
            if (!PreviewClientState.inRange(cell)) {
                continue;
            }
            int color = colorOf(mark);
            if (color == 0) {
                continue;
            }
            float alpha = PreviewClientState.layerAlpha(cell.y());
            Integer stage = PlaybackController.stageOf(cell);
            int selected = PlaybackController.selected();
            if (stage != null && selected >= 0) {
                if (stage > selected) {
                    continue;
                }
                if (stage < selected) {
                    emitThin(buffer, matrix, cell, camera, withAlpha(color, alpha * doneFrame));
                    drawn++;
                    continue;
                }
                if (!PlaybackController.revealedCell(cell)) {
                    continue;
                }
            }
            emit(buffer, matrix, cell, camera, withAlpha(color, alpha));
            drawn++;
        }
        if (now() < pulseUntil) {
            for (LatticePoint cell : pulsed) {
                if (PreviewClientState.inRange(cell)) {
                    emit(buffer, matrix, cell, camera, pulse);
                }
            }
        }
        draw(buffer);
    }

    private static int colorOf(CellMark mark) {
        return switch (mark) {
            case missing, expected -> missing;
            case present -> present;
            case wrong -> wrong;
            case extra -> extra;
            case blocked -> blocked;
            case unknown -> unknown;
        };
    }

    private static int withAlpha(int argb, float factor) {
        int alpha = Math.max(0x20, Math.min(0xFF, Math.round(((argb >>> 24) & 0xFF) * factor)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static void emit(BufferBuilder buffer, Matrix4f matrix, LatticePoint cell, Vec3 camera, int argb) {
        emitBox(buffer, matrix, cell, camera, argb, 0.002F);
    }

    /** The done stage's frame: a thinner, dimmer box than the current stage's (design 16 §6.2). */
    private static void emitThin(BufferBuilder buffer, Matrix4f matrix, LatticePoint cell, Vec3 camera, int argb) {
        emitBox(buffer, matrix, cell, camera, argb, 0.3F);
    }

    private static void emitBox(
        BufferBuilder buffer,
        Matrix4f matrix,
        LatticePoint cell,
        Vec3 camera,
        int argb,
        float inset
    ) {
        float x0 = (float) (cell.x() - camera.x) + inset;
        float y0 = (float) (cell.y() - camera.y) + inset;
        float z0 = (float) (cell.z() - camera.z) + inset;
        float x1 = (float) (cell.x() - camera.x) + 1.0F - inset;
        float y1 = (float) (cell.y() - camera.y) + 1.0F - inset;
        float z1 = (float) (cell.z() - camera.z) + 1.0F - inset;
        edge(buffer, matrix, x0, y0, z0, x1, y0, z0, argb);
        edge(buffer, matrix, x0, y0, z1, x1, y0, z1, argb);
        edge(buffer, matrix, x0, y1, z0, x1, y1, z0, argb);
        edge(buffer, matrix, x0, y1, z1, x1, y1, z1, argb);
        edge(buffer, matrix, x0, y0, z0, x0, y1, z0, argb);
        edge(buffer, matrix, x1, y0, z0, x1, y1, z0, argb);
        edge(buffer, matrix, x0, y0, z1, x0, y1, z1, argb);
        edge(buffer, matrix, x1, y0, z1, x1, y1, z1, argb);
        edge(buffer, matrix, x0, y0, z0, x0, y0, z1, argb);
        edge(buffer, matrix, x1, y0, z0, x1, y0, z1, argb);
        edge(buffer, matrix, x0, y1, z0, x0, y1, z1, argb);
        edge(buffer, matrix, x1, y1, z0, x1, y1, z1, argb);
    }

    private static void edge(
        BufferBuilder buffer,
        Matrix4f matrix,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        int argb
    ) {
        buffer.addVertex(matrix, x0, y0, z0).setColor(argb);
        buffer.addVertex(matrix, x1, y1, z1).setColor(argb);
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    static @Nullable LatticePoint center(Marker marker) {
        if (!marker.locus().hasCells()) {
            return null;
        }
        var bounds = marker.locus().cells().bounds();
        if (bounds == null) {
            return null;
        }
        return LatticePoint.of(
            (bounds.min().x() + bounds.max().x()) / 2,
            bounds.min().y(),
            (bounds.min().z() + bounds.max().z()) / 2
        );
    }
}
