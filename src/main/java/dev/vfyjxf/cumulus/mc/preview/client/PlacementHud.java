package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.Constants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.List;

/**
 * The animation's own note: the one line a capped payload says about its played and expected
 * counts. It draws without a preview session, because a build run has none.
 */
final class PlacementHud {

    private static final ResourceLocation layerId = ResourceLocation
            .fromNamespaceAndPath(Constants.modId, "placement_note");
    private static final int background = 0x90101418;
    private static final int noteColor = 0xFFFFB040;
    private static final int lineHeight = 10;
    private static final int padding = 3;

    private PlacementHud() {}

    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(layerId, PlacementHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        List<String> lines = PlacementAnimator.noteLines();
        if (lines.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        var font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int left = (screenWidth - width) / 2;
        int top = screenHeight / 4;
        graphics.pose().pushPose();
        graphics.fill(
            left - padding,
            top - padding,
            left + width + padding,
            top + lines.size() * lineHeight,
            background
        );
        int y = top;
        for (String line : lines) {
            graphics.drawString(font, line, left, y, noteColor, false);
            y += lineHeight;
        }
        graphics.pose().popPose();
    }
}
