package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.ClientConfig;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The corner hint bar, the three progress tiers, the stage banner and the marker billboards:
 * guidance, never chat. A marker with cells is projected onto the world instead of listed twice.
 */
final class PreviewHud {

    private static final ResourceLocation layerId = ResourceLocation
            .fromNamespaceAndPath(Constants.modId, "preview_hud");
    private static final int background = 0x90101418;
    private static final int textColor = 0xFFE8E8E8;
    private static final int titleColor = 0xFFFFE080;
    private static final int warnColor = 0xFFFFB040;
    private static final int errorColor = 0xFFFF6060;
    private static final int barTrack = 0xFF202830;
    private static final int barFill = 0xFF50C060;
    private static final int barSegment = 0xFF4090D0;
    private static final int barDone = 0xFF2A6A38;
    private static final int barCursor = 0xFFFFFFFF;
    private static final int barFrontier = 0xFFFFE080;
    private static final int maxBillboards = 8;
    private static final int lineHeight = 10;
    private static final int materialRowHeight = 16;
    private static final int materialTextOffset = 18;
    private static final int progressBarHeight = 3;
    private static final int segmentBarHeight = 2;
    private static final int barSpacing = 3;

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
            width = Math.max(width, font.width(line) + (line.startsWith("material ") ? materialTextOffset : 0));
        }
        boolean progress = ClientConfig.hudProgress();
        int top = 6;
        int left = 6;
        int height = 0;
        for (String line : lines) {
            height += line.startsWith("material ") ? materialRowHeight : lineHeight;
        }
        height += progress ? (progressBarHeight + barSpacing) : 0;
        height += progress && session.presentation().stages().size() > 1 ? (segmentBarHeight + barSpacing) : 0;
        graphics.pose().pushPose();
        graphics.fill(left - 3, top - 3, left + width + 3, top + height + 3, background);
        int y = top;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int color = index == 0 ? titleColor : colorOf(line);
            int textLeft = left;
            if (line.startsWith("material ")) {
                icon(graphics, line, left, y);
                textLeft = left + materialTextOffset;
            }
            graphics.drawString(font, line, textLeft, y + (line.startsWith("material ") ? 4 : 0), color, false);
            y += line.startsWith("material ") ? materialRowHeight : lineHeight;
            if (index == 0 && progress) {
                progressBar(graphics, left, y, width, session);
                y += progressBarHeight + barSpacing;
            } else if (index == 1 && progress && session.presentation().stages().size() > 1) {
                segmentBar(graphics, left, y, width, session.presentation());
                y += segmentBarHeight + barSpacing;
            }
        }
        graphics.pose().popPose();
        banner(graphics, minecraft);
        billboards(graphics, minecraft, session);
        PreviewClientState.markRendered();
    }

    /** Tier 1: satisfied over expected, filled only by whole cells (design 16 §7.1). */
    private static void progressBar(GuiGraphics graphics, int left, int top, int width, PreviewSyncPayload session) {
        int expected = 0;
        int satisfied = 0;
        for (CellMark mark : session.overlay().cells().values()) {
            expected++;
            if (mark == CellMark.present) {
                satisfied++;
            }
        }
        graphics.fill(left, top, left + width, top + progressBarHeight, barTrack);
        if (expected == 0) {
            return;
        }
        int filled = Math.round(width * (float) satisfied / expected);
        graphics.fill(left, top, left + filled, top + progressBarHeight, barFill);
    }

    /** Tier 2: one segment per stage, width by expected cells, filled by the stage's own fraction. */
    private static void segmentBar(
        GuiGraphics graphics,
        int left,
        int top,
        int width,
        PreviewPresentation presentation
    ) {
        List<Stage> stages = presentation.stages();
        int expected = 0;
        for (Stage stage : stages) {
            expected += stage.expectedCells();
        }
        if (expected == 0) {
            return;
        }
        int gaps = stages.size() - 1;
        int available = Math.max(stages.size(), width - gaps);
        int x = left;
        int cursor = PlaybackController.selected();
        for (int index = 0; index < stages.size() && x < left + width; index++) {
            Stage stage = stages.get(index);
            int segment = Math.max(1, Math.round(available * (float) stage.expectedCells() / expected));
            int filled = Math.round(segment * (float) stage.satisfiedCells() / stage.expectedCells());
            graphics.fill(x, top, x + segment, top + segmentBarHeight, barTrack);
            int color = stage.satisfiedCells() >= stage.expectedCells() ? barDone : barSegment;
            graphics.fill(x, top, x + filled, top + segmentBarHeight, color);
            if (index == cursor) {
                graphics.fill(x, top, x + segment, top + 1, barCursor);
            }
            if (index == presentation.currentStage()) {
                graphics.fill(x, top, x + 2, top + segmentBarHeight + 1, barFrontier);
            }
            x += segment + 1;
        }
    }

    /** Tier 3: the shortage lines keep their content and gain the item's icon (design 16 §7.3). */
    private static void icon(GuiGraphics graphics, String line, int left, int top) {
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            return;
        }
        NamespacedId id = NamespacedId.parse(parts[1]);
        if (id == null) {
            return;
        }
        ItemStack stack = stackOf(id);
        if (stack.isEmpty()) {
            return;
        }
        graphics.renderItem(stack, left, top);
    }

    private static ItemStack stackOf(NamespacedId id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()))
                .map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
    }

    /** The stage banner at a quarter of the screen height, wrapped at the panel's width (design 16 §8). */
    private static void banner(GuiGraphics graphics, Minecraft minecraft) {
        List<String> lines = PlaybackController.bannerLines();
        if (lines.isEmpty() || !ClientConfig.hudStageBanner()) {
            return;
        }
        float alpha = PlaybackController.bannerAlpha();
        if (alpha <= 0.0F) {
            return;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int maxWidth = Math.max(80, screenWidth / 2);
        Font font = minecraft.font;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        int widest = 0;
        for (String line : lines) {
            for (FormattedCharSequence part : font.split(Component.literal(line), maxWidth)) {
                wrapped.add(part);
                widest = Math.max(widest, font.width(part));
            }
        }
        if (wrapped.isEmpty()) {
            return;
        }
        int top = screenHeight / 4;
        int left = (screenWidth - widest) / 2;
        int text = withAlpha(textColor, alpha);
        int fill = withAlpha(background, alpha);
        graphics.fill(left - 4, top - 2, left + widest + 4, top + wrapped.size() * lineHeight + 1, fill);
        for (int index = 0; index < wrapped.size(); index++) {
            FormattedCharSequence part = wrapped.get(index);
            graphics.drawString(
                font,
                part,
                left + (widest - font.width(part)) / 2,
                top + index * lineHeight,
                text,
                false
            );
        }
    }

    private static int withAlpha(int argb, float factor) {
        int alpha = Math.max(0, Math.min(0xFF, Math.round(((argb >>> 24) & 0xFF) * factor)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** A marker with cells is labelled at its world position, so the text follows the structure. */
    private static void billboards(GuiGraphics graphics, Minecraft minecraft, PreviewSyncPayload session) {
        int drawn = 0;
        for (Marker marker : session.markers()) {
            if (drawn >= maxBillboards) {
                break;
            }
            LatticePoint anchor = PreviewOverlayRenderer.center(marker);
            if (anchor == null) {
                continue;
            }
            String label = shorten(marker.label());
            if (billboard(graphics, minecraft, anchor, label, 1.0F, colorOf(marker.kind()))) {
                drawn++;
            }
        }
        for (PlaybackController.PartLabel part : PlaybackController.partLabels()) {
            String label = shorten(part.hint());
            billboard(graphics, minecraft, part.anchor(), label, part.alpha(), textColor);
        }
    }

    /** Projects one label onto the anchor cell; false when the anchor is culled or off screen. */
    private static boolean billboard(
        GuiGraphics graphics,
        Minecraft minecraft,
        LatticePoint anchor,
        String label,
        float alpha,
        int color
    ) {
        if (!PreviewClientState.inRange(anchor)) {
            return false;
        }
        Vec3 eye = PreviewClientState.cameraPosition();
        Matrix4f view = PreviewClientState.viewMatrix();
        Matrix4f projection = PreviewClientState.projectionMatrix();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        Vector4f point = new Vector4f(
            (float) (anchor.x() + 0.5D - eye.x),
            (float) (anchor.y() + 0.5D - eye.y),
            (float) (anchor.z() + 0.5D - eye.z),
            1.0F
        );
        point.mul(view).mul(projection);
        if (point.w <= 0.01F) {
            return false;
        }
        int x = Math.round((point.x / point.w * 0.5F + 0.5F) * screenWidth);
        int y = Math.round((1.0F - (point.y / point.w * 0.5F + 0.5F)) * screenHeight);
        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            return false;
        }
        int labelWidth = minecraft.font.width(label);
        graphics.pose().pushPose();
        graphics.fill(x - 2, y - 10, x + labelWidth + 2, y, withAlpha(background, alpha));
        graphics.drawString(minecraft.font, label, x, y - 9, withAlpha(color, alpha), false);
        graphics.pose().popPose();
        return true;
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
        if (line.startsWith("stage ")) {
            return titleColor;
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
