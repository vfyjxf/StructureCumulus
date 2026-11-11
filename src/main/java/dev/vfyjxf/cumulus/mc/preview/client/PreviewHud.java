package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * The corner hint bar and the marker billboards: guidance, never chat. The bar carries the counter,
 * the candidate, the slice, the markers and the key legend; a marker with cells is projected onto the
 * world instead of listed twice.
 */
final class PreviewHud {

    private static final ResourceLocation layerId = ResourceLocation
            .fromNamespaceAndPath(Constants.modId, "preview_hud");
    private static final int background = 0x90101418;
    private static final int textColor = 0xFFE8E8E8;
    private static final int titleColor = 0xFFFFE080;
    private static final int warnColor = 0xFFFFB040;
    private static final int errorColor = 0xFFFF6060;
    private static final int maxBillboards = 8;

    private PreviewHud() {}

    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(layerId, PreviewHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        PreviewSyncPayload session = PreviewClientState.session();
        if (session == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        List<String> lines = PreviewClientState.hudLines();
        if (lines.isEmpty()) {
            return;
        }
        var font = minecraft.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int top = 6;
        int left = 6;
        graphics.pose().pushPose();
        graphics.fill(left - 3, top - 3, left + width + 3, top + lines.size() * 10 + 3, background);
        for (int index = 0; index < lines.size(); index++) {
            int color = index == 0 ? titleColor : colorOf(lines.get(index));
            graphics.drawString(font, lines.get(index), left, top + index * 10, color, false);
        }
        graphics.pose().popPose();
        billboards(graphics, minecraft, session);
        PreviewClientState.markRendered();
    }

    /** A marker with cells is labelled at its world position, so the text follows the structure. */
    private static void billboards(GuiGraphics graphics, Minecraft minecraft, PreviewSyncPayload session) {
        Vec3 eye = PreviewClientState.cameraPosition();
        Matrix4f view = PreviewClientState.viewMatrix();
        Matrix4f projection = PreviewClientState.projectionMatrix();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int drawn = 0;
        for (Marker marker : session.markers()) {
            if (drawn >= maxBillboards) {
                return;
            }
            LatticePoint anchor = PreviewOverlayRenderer.center(marker);
            if (anchor == null || !PreviewClientState.inRange(anchor)) {
                continue;
            }
            Vector4f point = new Vector4f(
                (float) (anchor.x() + 0.5D - eye.x),
                (float) (anchor.y() + 0.5D - eye.y),
                (float) (anchor.z() + 0.5D - eye.z),
                1.0F
            );
            point.mul(view).mul(projection);
            if (point.w <= 0.01F) {
                continue;
            }
            int x = Math.round((point.x / point.w * 0.5F + 0.5F) * screenWidth);
            int y = Math.round((1.0F - (point.y / point.w * 0.5F + 0.5F)) * screenHeight);
            if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
                continue;
            }
            String label = shorten(marker.label());
            int labelWidth = minecraft.font.width(label);
            graphics.pose().pushPose();
            graphics.fill(x - 2, y - 10, x + labelWidth + 2, y, background);
            graphics.drawString(minecraft.font, label, x, y - 9, colorOf(marker.kind()), false);
            graphics.pose().popPose();
            drawn++;
        }
    }

    private static String shorten(String label) {
        return label.length() <= 48 ? label : label.substring(0, 45) + "...";
    }

    private static int colorOf(String line) {
        if (line.startsWith("error")) {
            return errorColor;
        }
        if (line.startsWith("warning") || line.startsWith("hover")) {
            return warnColor;
        }
        if (line.startsWith("keys")) {
            return 0xFF9A9A9A;
        }
        return textColor;
    }

    private static int colorOf(MarkerKind kind) {
        return switch (kind) {
            case error -> errorColor;
            case warning -> warnColor;
            case info, anchor, seam -> textColor;
        };
    }

    static Component describe(PreviewSyncPayload session) {
        return Component.literal("preview " + session.blueprintId().text());
    }
}
