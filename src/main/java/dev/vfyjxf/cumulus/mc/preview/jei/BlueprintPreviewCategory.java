package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.screen.StageTimeline;
import dev.vfyjxf.cumulus.mc.preview.scene.BlueprintPreviewRecipe;
import dev.vfyjxf.cumulus.mc.preview.scene.CellOutline;
import dev.vfyjxf.cumulus.mc.preview.scene.ChromeStroke;
import dev.vfyjxf.cumulus.mc.preview.scene.ConstraintOverlay;
import dev.vfyjxf.cumulus.mc.preview.scene.ExplodeModel;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewViewMode;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import dev.vfyjxf.cumulus.mc.preview.scene.SetPage;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The "Multiblock Preview" category: the isometric scene, the compact stage timeline, the Y-slice
 * slider, the material summary and the buttons. Everything it draws is the catalog the server pushed
 * (design 15 §6, 19 §5).
 */
final class BlueprintPreviewCategory implements IRecipeCategory<BlueprintPreviewRecipe> {

    static final RecipeType<BlueprintPreviewRecipe> type = RecipeType
            .create(Constants.modId, "multiblock_preview", BlueprintPreviewRecipe.class);

    static final int width = 176;
    static final int height = 234;

    static final int sceneX = 2;
    static final int sceneY = 2;
    static final int sceneWidth = 156;
    static final int sceneHeight = 104;

    static final int sliderX = 160;
    static final int sliderY = 2;
    static final int sliderWidth = 14;
    static final int sliderHeight = 104;

    /**
     * The compact timeline of design 19 §5: the screen's layout in 11-px lanes and 0.75-scale text, in
     * a fixed 38-px block (three member lanes and their gaps). A taller table scrolls with the wheel
     * over it; the face itself never grows with the page.
     */
    static final int timelineX = 2;
    static final int timelineY = 108;
    static final int timelineWidth = 172;
    static final int timelineHeight = 38;
    static final float timelineTextScale = 0.75F;
    static final int timelineLaneHeight = 11;
    static final int timelineLaneGap = 2;
    static final int timelineSegmentGap = 1;
    static final int timelineMinSegmentWidth = 8;
    static final int timelineHeadMaxWidth = 96;
    static final int timelineWrapIndent = 8;
    static final int timelineNameInset = 1;

    static final int buttonX = 2;
    static final int buttonY = 148;
    static final int buttonWidth = 88;
    static final int buttonHeight = 16;

    static final int materialsX = 94;
    static final int materialsY = 148;
    static final int materialsWidth = 80;
    static final int materialsHeight = 38;

    static final int modeX = 2;
    static final int modeY = 188;
    static final int modeWidth = 100;
    static final int modeHeight = 14;

    /** The set page's constraint toggle beside the mode button: the face has room, so it shows (§5). */
    static final int constraintsX = 104;
    static final int constraintsY = 188;
    static final int constraintsWidth = 70;
    static final int constraintsHeight = 14;

    /** The variant row: one page per group, the arrows switch the member in place (design 15 §12.3). */
    static final int variantX = 2;
    static final int variantY = 204;
    static final int variantWidth = 172;
    static final int variantHeight = 14;
    static final int variantArrowWidth = 18;

    /** The screen's entry: the page's selected member, opened as the client screen (design 17 §9.1). */
    static final int screenX = 2;
    static final int screenY = 220;
    static final int screenWidth = 172;
    static final int screenHeight = 14;

    /** One panel of the three-view: three of these and the two gaps fill the scene's width. */
    static final int panelWidth = 50;
    static final int panelGap = 3;

    private static final int columns = 4;
    private static final int tile = 18;
    private static final int panelColor = 0x40000000;
    private static final int panelBackColor = 0x28000000;
    private static final int trackColor = 0x30FFFFFF;
    private static final int thumbColor = 0xFFE0E0E0;
    private static final int buttonColor = 0xFF2E4A1E;
    private static final int buttonHoverColor = 0xFF4A6B2A;
    private static final int borderColor = 0xFF101010;
    private static final int labelColor = 0xFFC0C0C0;
    private static final int chromeColor = 0xFFC0C0C0;
    private static final int captionColor = ConstraintOverlay.linkColor;
    private static final StageTimeline.Chrome chrome = StageTimeline.Chrome.screen;
    /** The 9-px font's line height at the face's 0.75 scale: a small label fits inside a row. */
    private static final int smallTextHeight = (int) Math.ceil(StageTimeline.textHeight * timelineTextScale);
    /** Every link and probe is drawn still: the JEI face has no explode and no clock (design 19 §5). */
    private static final ExplodeModel still = new ExplodeModel(List.of(), List.of(), 0.0F, 0.0F, 0.0F, 0.0F);
    private static final String[] panelNames = {"front", "side", "top"};

    private static final BlueprintPreviewCategory instance = new BlueprintPreviewCategory();

    private @Nullable IDrawable icon;

    /** One overlay label before it is drawn: its anchor, its colour, and whether it is centred or small. */
    private record OverlayText(String text, int x, int y, int color, boolean centred, boolean small) {}

    private BlueprintPreviewCategory() {}

    static BlueprintPreviewCategory instance() {
        return instance;
    }

    void icon(IDrawable drawable) {
        this.icon = drawable;
    }

    @Override
    public RecipeType<BlueprintPreviewRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.literal("Multiblock Preview");
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BlueprintPreviewRecipe recipe, IFocusGroup focuses) {
        // a preview has no ingredients to lay out; its whole face is the scene
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, BlueprintPreviewRecipe recipe, IFocusGroup focuses) {
        builder.addInputHandler(new PreviewInputHandler(recipe, JeiPreviewPlugin.viewFor(recipe)));
    }

    @Override
    public void draw(
        BlueprintPreviewRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphics graphics,
        double mouseX,
        double mouseY
    ) {
        graphics.fill(sceneX, sceneY, sceneX + sceneWidth, sceneY + sceneHeight, panelColor);
        PageView page = JeiPreviewPlugin.viewFor(recipe);
        PreviewScene.View[] views = page.views();
        for (PreviewScene.View view : views) {
            if (view.width() < sceneWidth) {
                graphics.fill(view.x(), view.y(), view.x() + view.width(), view.y() + view.height(), panelBackColor);
            }
        }
        List<JeiPreview.Panel> panels = new ArrayList<>(views.length);
        List<PreviewScene.Shading> shadings = new ArrayList<>(views.length);
        List<ChromeStroke.Stroke> strokes = new ArrayList<>();
        List<OverlayText> overlayTexts = new ArrayList<>();
        List<ExplodeModel.Label> memberLabels = memberLabels(recipe, page);
        int links = 0;
        int probes = 0;
        int labels = 0;
        for (int index = 0; index < views.length; index++) {
            PreviewScene.View view = views[index];
            PreviewScene.Panel drawn = recipe.scene().draw(graphics, view, page.slice(), band(page.mode(), index));
            panels.add(
                new JeiPreview.Panel(
                    drawn.cells(),
                    drawn.layer(),
                    view.x(),
                    view.y(),
                    view.width(),
                    view.height(),
                    recipe.scene().fitScale(view)
                )
            );
            shadings.add(PreviewScene.shading(view));
            graphics.drawString(
                Minecraft.getInstance().font,
                panelName(page.mode(), index),
                view.x() + 2,
                view.y() + 2,
                labelColor,
                false
            );
            if (recipe.isSet() && page.constraints()) {
                int[] counted = constraintChrome(recipe, page, view, strokes, overlayTexts, memberLabels);
                links += counted[0];
                probes += counted[1];
                labels += counted[2];
            }
        }
        JeiPreview.recordFrame(panels, shadings, links, probes, labels);
        ChromeStroke.draw(graphics, strokes);
        drawOverlayTexts(graphics, overlayTexts);
        if (recipe.scene().cellCount() == 0) {
            graphics.drawString(
                Minecraft.getInstance().font,
                "no declared blocks",
                sceneX + 4,
                sceneY + 4,
                0xFFA0A0A0,
                false
            );
        }
        drawTimeline(graphics, recipe, page, mouseX, mouseY);
        drawSlider(graphics, recipe, page);
        if (!recipe.isSet()) {
            // a set page has no session to open; its one world question is `set check` (design 18 §5)
            drawButton(graphics, mouseX, mouseY);
        }
        drawModeRow(graphics, recipe, page, mouseX, mouseY);
        drawVariantRow(graphics, recipe, mouseX, mouseY);
        drawScreenButton(graphics, mouseX, mouseY);
        drawMaterials(graphics, recipe, mouseX, mouseY);
    }

    /** The compact stage timeline of design 19 §5: segments select, the wheel scrolls, nothing plays. */
    private void drawTimeline(
        GuiGraphics graphics,
        BlueprintPreviewRecipe recipe,
        PageView page,
        double mouseX,
        double mouseY
    ) {
        StageTimeline timeline = page.timeline(recipe);
        if (timeline.height() <= 0 || timeline.width() <= 0) {
            return;
        }
        int stripX = timelineX;
        int stripY = timelineY;
        int scroll = page.timelineScroll();
        int localX = (int) mouseX - stripX;
        int localY = (int) mouseY - stripY + scroll;
        boolean over = inside(mouseX, mouseY, stripX, stripY, timeline.width(), timeline.height());
        int hovered = over ? timeline.hit(localX, localY) : -1;
        int top = stripY;
        int bottom = stripY + timeline.height();
        graphics.fill(stripX, top, stripX + timeline.width(), bottom, chrome.ground());
        for (StageTimeline.Lane lane : timeline.lanes()) {
            StageTimeline.Lane.Head head = lane.head();
            int laneY = stripY + lane.y() - scroll;
            if (head == null || laneY + lane.rowHeight() < top || laneY > bottom) {
                continue;
            }
            int headY = stripY + head.labelY() - scroll;
            if (headY < top || headY + smallTextHeight > bottom) {
                continue;
            }
            drawSmallText(graphics, head.label(), stripX + StageTimeline.namePad, headY, chrome.text());
        }
        for (StageTimeline.Segment segment : timeline.segments()) {
            int segmentY = stripY + segment.y() - scroll;
            if (segmentY + segment.height() < top || segmentY > bottom) {
                continue;
            }
            int y0 = Math.max(segmentY, top);
            int y1 = Math.min(segmentY + segment.height(), bottom);
            graphics.fill(
                stripX + segment.x(),
                y0,
                stripX + segment.x() + segment.width(),
                y1,
                segment.index() == hovered ? chrome.hover() : chrome.fill()
            );
            if (segment.index() == page.stage()) {
                outline(graphics, stripX + segment.x(), y0, segment.width(), y1 - y0, chrome.cursor());
            }
            if (segment.named() && segmentY >= top && segmentY + segment.height() <= bottom) {
                drawSmallText(
                    graphics,
                    segment.label(),
                    stripX + segment.x() + 1,
                    stripY + segment.nameY() - scroll,
                    chrome.text()
                );
            }
        }
        if (timeline.scrollable()) {
            int barX = stripX + timeline.width() - 2;
            int thumb = Math.max(
                6,
                (int) Math.round((double) timeline.height() * timeline.height() / timeline.contentHeight())
            );
            int travel = timeline.height() - thumb;
            int thumbY = travel <= 0
                    ? 0
                    : (int) Math.round((double) travel * scroll / Math.max(1, timeline.maxScroll()));
            graphics.fill(
                barX,
                stripY + thumbY,
                barX + 2,
                stripY + thumbY + thumb,
                (chrome.text() & 0x00FFFFFF) | 0xC0000000
            );
        }
    }

    /** One name at the face's 0.75 scale; the layout measured it at the same scale. */
    private static void drawSmallText(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(timelineTextScale, timelineTextScale, 1.0F);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** A one-pixel outline inside a rectangle: the selected segment's cursor chrome. */
    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** A page's member labels, still, on each member's centroid: JEI has no explode (design 18 §5). */
    private static List<ExplodeModel.Label> memberLabels(BlueprintPreviewRecipe recipe, PageView page) {
        SetPage set = recipe.setPage();
        if (set == null || !page.constraints()) {
            return List.of();
        }
        return ExplodeModel.of(set.stages(), set.cells(), recipe.scene().hull(), set.bayLabels()).labels(0.0F);
    }

    /**
     * The set page's overlay in one view (design 18 §5): optional members as dashed silhouettes, every
     * link as a face-meeting stroke with its {@code actual ≤ max} caption, the presence probes as
     * crosses and the member labels where a member's centroid falls. All chrome, no world read.
     */
    private static int[] constraintChrome(
        BlueprintPreviewRecipe recipe,
        PageView page,
        PreviewScene.View view,
        List<ChromeStroke.Stroke> strokes,
        List<OverlayText> texts,
        List<ExplodeModel.Label> memberLabels
    ) {
        SetPage set = recipe.setPage();
        if (set == null) {
            return new int[]{0, 0, 0};
        }
        int links = 0;
        int probes = 0;
        int labels = 0;
        for (int member : ConstraintOverlay.optionalMembers(set)) {
            for (PreviewScene.Cell cell : recipe.scene().cells()) {
                if (!recipe.scene().drawn(cell, view, page.slice()) || set.memberOf(cell.ghost()) != member) {
                    continue;
                }
                LatticePoint at = cell.ghost().cell();
                List<Vector2f> corners = new ArrayList<>(8);
                for (int corner = 0; corner < 8; corner++) {
                    corners.add(
                        recipe.scene().project(
                            view,
                            at.x() + 0.5F + ((corner & 1) == 0 ? 0.0F : 1.0F) - 0.5F,
                            at.y() + 0.5F + ((corner & 2) == 0 ? 0.0F : 1.0F) - 0.5F,
                            at.z() + 0.5F + ((corner & 4) == 0 ? 0.0F : 1.0F) - 0.5F
                        )
                    );
                }
                strokes.addAll(ChromeStroke.dashed(CellOutline.ofCorners(corners).points(), chromeColor));
            }
        }
        for (ConstraintOverlay.LinkLine link : ConstraintOverlay.links(set, 0.0F, still)) {
            Vector2f from = recipe.scene().project(view, link.segment().ax(), link.segment().ay(), link.segment().az());
            Vector2f to = recipe.scene().project(view, link.segment().bx(), link.segment().by(), link.segment().bz());
            if (link.segment().degenerate()) {
                // the two faces are the same plane: a seam marker, not a zero-length line
                strokes.add(
                    new ChromeStroke.Stroke(
                        List.of(new Vector2f(from.x - 3.0F, from.y), new Vector2f(from.x + 3.0F, from.y)),
                        ConstraintOverlay.linkColor
                    )
                );
                strokes.add(
                    new ChromeStroke.Stroke(
                        List.of(new Vector2f(from.x, from.y - 3.0F), new Vector2f(from.x, from.y + 3.0F)),
                        ConstraintOverlay.linkColor
                    )
                );
            } else {
                strokes.add(new ChromeStroke.Stroke(List.of(from, to), ConstraintOverlay.linkColor));
            }
            texts.add(
                new OverlayText(
                    link.caption(),
                    Math.round((from.x + to.x) * 0.5F),
                    Math.round((from.y + to.y) * 0.5F) - 9,
                    captionColor,
                    true,
                    false
                )
            );
            links++;
        }
        for (ConstraintOverlay.Probe probe : ConstraintOverlay.probes(set, 0.0F, still)) {
            Vector2f point = recipe.scene().project(view, probe.x(), probe.y(), probe.z());
            strokes.add(
                new ChromeStroke.Stroke(
                    List.of(new Vector2f(point.x - 2.0F, point.y), new Vector2f(point.x + 2.0F, point.y)),
                    ConstraintOverlay.probeColor
                )
            );
            strokes.add(
                new ChromeStroke.Stroke(
                    List.of(new Vector2f(point.x, point.y - 2.0F), new Vector2f(point.x, point.y + 2.0F)),
                    ConstraintOverlay.probeColor
                )
            );
            probes++;
        }
        StageTimeline.Labels display = StageTimeline.Labels.of(recipe.blueprintId());
        for (ExplodeModel.Label label : memberLabels) {
            Vector2f point = recipe.scene().project(view, label.x(), label.y(), label.z());
            String text = display.strip(label.text());
            int width = smallWidth(text);
            if (width > view.width() - 4) {
                continue;
            }
            int x = clamp(Math.round(point.x) - width / 2, view.x() + 2, view.x() + view.width() - 2 - width);
            int y = clamp(Math.round(point.y) - 4, view.y() + 2, view.y() + view.height() - 9);
            texts.add(new OverlayText(text, x, y, chromeColor, false, true));
            labels++;
        }
        return new int[]{links, probes, labels};
    }

    /** Every overlay label above the scene, at the chrome depth the screen uses (design 19 §4). */
    private static void drawOverlayTexts(GuiGraphics graphics, List<OverlayText> texts) {
        if (texts.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, PreviewScene.chromeDepth);
        for (OverlayText text : texts) {
            if (text.small()) {
                drawSmallText(graphics, text.text(), text.x(), text.y(), text.color());
                continue;
            }
            int x = text.centred() ? text.x() - Minecraft.getInstance().font.width(text.text()) / 2 : text.x();
            graphics.drawString(Minecraft.getInstance().font, text.text(), x, text.y(), text.color(), false);
        }
        graphics.pose().popPose();
    }

    /** A label's width at the face's 0.75 scale; the layout measures the same way. */
    private static int smallWidth(String text) {
        return Math.round(Minecraft.getInstance().font.width(text) * timelineTextScale);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** The variant row: the selected member's label between two arrows; a standalone page draws none. */
    private void drawVariantRow(GuiGraphics graphics, BlueprintPreviewRecipe recipe, double mouseX, double mouseY) {
        if (recipe.variantCount() < 2) {
            return;
        }
        drawFace(graphics, variantX, variantY, variantWidth, variantHeight, mouseX, mouseY);
        graphics.drawString(Minecraft.getInstance().font, "<", variantX + 6, variantY + 3, 0xFFF0F0F0, false);
        graphics.drawString(
            Minecraft.getInstance().font,
            ">",
            variantX + variantWidth - 11,
            variantY + 3,
            0xFFF0F0F0,
            false
        );
        String label = recipe.variantLine();
        if (label != null) {
            int textWidth = Minecraft.getInstance().font.width(label);
            graphics.drawString(
                Minecraft.getInstance().font,
                label,
                variantX + (variantWidth - textWidth) / 2,
                variantY + 3,
                0xFFC0E8C0,
                false
            );
        }
    }

    /** The "open in screen" button: no packet and no session, the page is the screen's own data. */
    private void drawScreenButton(GuiGraphics graphics, double mouseX, double mouseY) {
        drawFace(graphics, screenX, screenY, screenWidth, screenHeight, mouseX, mouseY);
        graphics.drawString(
            Minecraft.getInstance().font,
            "open in screen",
            screenX + 4,
            screenY + 3,
            0xFFF0F0F0,
            false
        );
    }

    /** The panels with the face's two reading controls: the wheel's zoom and the selected stage (19 §5). */
    static PreviewScene.View[] views(PreviewViewMode mode, float yaw, float pitch, float zoom, int stage) {
        return switch (mode) {
            case isometric -> new PreviewScene.View[]{new PreviewScene.View(
                sceneX,
                sceneY,
                sceneWidth,
                sceneHeight,
                yaw,
                pitch,
                PreviewScene.LayerRule.upTo,
                stage,
                zoom
            )};
            case threeView -> new PreviewScene.View[]{
                    new PreviewScene.View(
                        sceneX,
                        sceneY,
                        panelWidth,
                        sceneHeight,
                        180.0F,
                        0.0F,
                        PreviewScene.LayerRule.all,
                        stage,
                        zoom
                    ),
                    new PreviewScene.View(
                        sceneX + panelWidth + panelGap,
                        sceneY,
                        panelWidth,
                        sceneHeight,
                        90.0F,
                        0.0F,
                        PreviewScene.LayerRule.all,
                        stage,
                        zoom
                    ),
                    new PreviewScene.View(
                        sceneX + 2 * (panelWidth + panelGap),
                        sceneY,
                        panelWidth,
                        sceneHeight,
                        0.0F,
                        90.0F,
                        PreviewScene.LayerRule.exact,
                        stage,
                        zoom
                    )};
            case sliceGrid -> new PreviewScene.View[]{new PreviewScene.View(
                sceneX,
                sceneY,
                sceneWidth,
                sceneHeight,
                0.0F,
                90.0F,
                PreviewScene.LayerRule.exact,
                stage,
                zoom
            )};
        };
    }

    private static String panelName(PreviewViewMode mode, int index) {
        return panelNames[mode == PreviewViewMode.threeView ? index : 2];
    }

    private static boolean band(PreviewViewMode mode, int index) {
        return mode == PreviewViewMode.threeView && index < 2;
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        BlueprintPreviewRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        PageView page = JeiPreviewPlugin.viewFor(recipe);
        List<String> cellLines = page.hoverLines(recipe, mouseX, mouseY);
        if (!cellLines.isEmpty()) {
            for (String line : cellLines) {
                tooltip.add(Component.literal(line));
            }
            return;
        }
        String timelineLine = page.timelineHover(recipe, mouseX - timelineX, mouseY - timelineY);
        if (timelineLine != null) {
            tooltip.add(Component.literal(timelineLine));
            return;
        }
        int material = materialAt(recipe, mouseX, mouseY);
        if (material >= 0) {
            BlueprintPreviewRecipe.Material line = recipe.materials().get(material);
            tooltip.add(line.stack().getHoverName());
            tooltip.add(Component.literal("x " + line.count()));
            return;
        }
        if (recipe.isSet()) {
            String link = page.linkHover(recipe, mouseX, mouseY);
            if (link != null) {
                tooltip.add(Component.literal(link));
                return;
            }
            if (inside(mouseX, mouseY, constraintsX, constraintsY, constraintsWidth, constraintsHeight)) {
                tooltip.add(Component.literal(constraintsTooltip(page)));
                return;
            }
        }
        for (String line : recipe.lines()) {
            tooltip.add(Component.literal(line));
        }
    }

    private void drawSlider(GuiGraphics graphics, BlueprintPreviewRecipe recipe, PageView page) {
        int layers = recipe.scene().layers();
        graphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, trackColor);
        int travel = sliderHeight - 6;
        float fraction = layers <= 1 ? 1.0F : page.slice() / (float) (layers - 1);
        int thumbY = sliderY + Math.round((1.0F - fraction) * travel);
        graphics.fill(sliderX + 2, thumbY, sliderX + sliderWidth - 2, thumbY + 6, thumbColor);
        graphics.drawString(
            Minecraft.getInstance().font,
            Integer.toString(page.slice() + 1),
            sliderX + 3,
            sliderY + sliderHeight + 3,
            0xFFC0C0C0,
            false
        );
    }

    private void drawButton(GuiGraphics graphics, double mouseX, double mouseY) {
        drawFace(graphics, buttonX, buttonY, buttonWidth, buttonHeight, mouseX, mouseY);
        graphics.drawString(
            Minecraft.getInstance().font,
            "Ghost to world",
            buttonX + 4,
            buttonY + 4,
            0xFFF0F0F0,
            false
        );
    }

    /** The mode row, and on a set page the constraint overlay's toggle beside it (design 18 §5). */
    private void drawModeRow(
        GuiGraphics graphics,
        BlueprintPreviewRecipe recipe,
        PageView page,
        double mouseX,
        double mouseY
    ) {
        drawFace(graphics, modeX, modeY, modeWidth, modeHeight, mouseX, mouseY);
        graphics.drawString(
            Minecraft.getInstance().font,
            "view: " + page.mode().label(),
            modeX + 4,
            modeY + 3,
            0xFFF0F0F0,
            false
        );
        if (!recipe.isSet()) {
            return;
        }
        drawFace(graphics, constraintsX, constraintsY, constraintsWidth, constraintsHeight, mouseX, mouseY);
        graphics.drawString(
            Minecraft.getInstance().font,
            "constraints",
            constraintsX + 2,
            constraintsY + 3,
            page.constraints() ? 0xFFC0E8C0 : 0xFFC0C0C0,
            false
        );
    }

    private static String constraintsTooltip(PageView page) {
        return page.constraints() ? "the set's links and probes: on" : "the set's links and probes: off";
    }

    private static void drawFace(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        double mouseX,
        double mouseY
    ) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, hovered ? buttonHoverColor : buttonColor);
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    private void drawMaterials(GuiGraphics graphics, BlueprintPreviewRecipe recipe, double mouseX, double mouseY) {
        int shown = Math.min(recipe.materials().size(), columns * (materialsHeight / tile));
        for (int index = 0; index < shown; index++) {
            BlueprintPreviewRecipe.Material line = recipe.materials().get(index);
            int x = materialsX + (index % columns) * tile;
            int y = materialsY + (index / columns) * tile;
            graphics.renderItem(line.stack(), x, y);
            graphics.drawString(
                Minecraft.getInstance().font,
                Integer.toString(line.count()),
                x + 10,
                y + 9,
                0xFFFFFFFF,
                true
            );
        }
        int overflow = recipe.materials().size() - shown;
        if (overflow > 0) {
            graphics.drawString(
                Minecraft.getInstance().font,
                "+" + overflow,
                materialsX,
                materialsY + (shown / columns) * tile - 8,
                0xFFFF8080,
                false
            );
        }
    }

    private int materialAt(BlueprintPreviewRecipe recipe, double mouseX, double mouseY) {
        int shown = Math.min(recipe.materials().size(), columns * (materialsHeight / tile));
        for (int index = 0; index < shown; index++) {
            int x = materialsX + (index % columns) * tile;
            int y = materialsY + (index / columns) * tile;
            if (inside(mouseX, mouseY, x, y, 16, 16)) {
                return index;
            }
        }
        return -1;
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** The one page's view state the category mutates: the yaw, the layer, the mode and SC14's verbs. */
    static final class PageView {

        private float yaw = 135.0F;
        private float pitch = 30.0F;
        private int slice = -1;
        private PreviewViewMode mode = PreviewViewMode.isometric;
        /** The selected stage, or -1: the JEI face shows one stage at a time (design 19 §5). */
        private int stage = -1;
        /** The wheel's zoom over the fitted scale, clamped like the screen's camera (design 17 §4.4). */
        private float zoom = 1.0F;
        private boolean constraints = true;
        private int timelineScroll;
        private boolean draggedSinceClick;
        private long lastClickMillis = -1L;

        float yaw() {
            return yaw;
        }

        void yaw(float value) {
            yaw = value;
        }

        float pitch() {
            return pitch;
        }

        void pitch(float value) {
            pitch = value;
        }

        int slice() {
            return slice;
        }

        /** The slider's layer; moving it leaves the stage selection, the sibling view (design 19 §5). */
        void slice(int value) {
            slice = value;
            stage = -1;
        }

        PreviewViewMode mode() {
            return mode;
        }

        void cycleMode() {
            mode = mode.next();
        }

        int stage() {
            return stage;
        }

        void selectStage(int value) {
            stage = value < 0 ? -1 : value;
        }

        float zoom() {
            return zoom;
        }

        /** One wheel notch's zoom, clamped to the camera's own range (design 17 §4.4). */
        void zoomBy(float notches) {
            zoom = Math.max(
                SceneCamera.minZoom,
                Math.min(SceneCamera.maxZoom, (float) (zoom * Math.pow(SceneCamera.zoomStep, notches)))
            );
        }

        /** The double click's frame-all: the fitted hull, the zoom back to one (design 17 §4.2). */
        void frame() {
            zoom = 1.0F;
            lastClickMillis = -1L;
        }

        void drag() {
            draggedSinceClick = true;
        }

        /** A click without a drag; two inside the threshold are the double click that frames the hull. */
        boolean clickScene(long now) {
            if (lastClickMillis >= 0
                    && now - lastClickMillis <= GuiEventListener.DOUBLE_CLICK_THRESHOLD_MS
                    && !draggedSinceClick) {
                frame();
                return true;
            }
            lastClickMillis = now;
            draggedSinceClick = false;
            return false;
        }

        boolean constraints() {
            return constraints;
        }

        void toggleConstraints() {
            constraints = !constraints;
        }

        int timelineScroll() {
            return timelineScroll;
        }

        void timelineScroll(int value) {
            timelineScroll = value;
        }

        /** The compact timeline of one page: the screen's layout at this face's smaller metrics. */
        StageTimeline timeline(BlueprintPreviewRecipe recipe) {
            StageTimeline timeline = StageTimeline.of(
                recipe.presentation().stages(),
                timelineWidth,
                timelineMetrics(),
                StageTimeline.Labels.of(recipe.blueprintId())
            );
            timelineScroll = timeline.clampScroll(timelineScroll);
            return timeline;
        }

        /** A segment click selects its stage, and a second click on it returns to the whole page. */
        boolean selectTimeline(StageTimeline timeline, int localX, int localY) {
            int hit = timeline.hit(localX, localY + timelineScroll);
            if (hit < 0) {
                return false;
            }
            selectStage(hit == stage ? -1 : hit);
            return true;
        }

        /** The face's compact text metrics: one measured string at the drawn 0.75 scale (19 §5). */
        static StageTimeline.Metrics timelineMetrics() {
            Minecraft minecraft = Minecraft.getInstance();
            return new StageTimeline.Metrics(
                timelineLaneHeight,
                timelineLaneGap,
                timelineSegmentGap,
                timelineMinSegmentWidth,
                timelineHeadMaxWidth,
                timelineWrapIndent,
                timelineNameInset,
                timelineHeight,
                text -> Math.round(minecraft.font.width(text) * timelineTextScale)
            );
        }

        /** The panels the current camera and reading controls draw, in every mode. */
        PreviewScene.View[] views() {
            return BlueprintPreviewCategory.views(mode, yaw, pitch, zoom, stage);
        }

        /** Keeps the layer inside a page whose cell list just changed under the cursor. */
        void clamp(int layers) {
            slice = slice < 0 ? layers - 1 : Math.min(slice, layers - 1);
        }

        /** Keeps the stage selection inside a page whose table just changed under the cursor. */
        void clampStage(int stages) {
            if (stage >= stages) {
                stage = stages - 1;
            }
        }

        /** The cell tooltip under a recipe-local point in the current mode; a panel answers only for itself. */
        List<String> hoverLines(BlueprintPreviewRecipe recipe, double mouseX, double mouseY) {
            for (PreviewScene.View view : views()) {
                PreviewScene.Cell cell = recipe.scene().hover(mouseX, mouseY, view, slice);
                if (cell != null) {
                    return recipe.cellLines(cell);
                }
            }
            return List.of();
        }

        /** Where one expected cell's centre lands in one panel of the current mode. */
        Optional<JeiPreview.CellPoint> point(BlueprintPreviewRecipe recipe, int panel, int cellIndex) {
            PreviewScene.View[] views = views();
            if (panel < 0 || panel >= views.length || cellIndex < 0 || cellIndex >= recipe.scene().cells().size()) {
                return Optional.empty();
            }
            Vector2f point = recipe.scene()
                    .point(views[panel], slice, recipe.scene().cells().get(cellIndex).ghost().cell());
            return point == null ? Optional.empty() : Optional.of(new JeiPreview.CellPoint(point.x, point.y));
        }

        /** The timeline's hover: a segment names its whole id, its group and its cells; a head its group. */
        @Nullable
        String timelineHover(BlueprintPreviewRecipe recipe, double mouseX, double mouseY) {
            StageTimeline timeline = timeline(recipe);
            if (timeline.height() <= 0
                    || mouseX < 0.0D
                    || mouseY < 0.0D
                    || mouseX >= timeline.width()
                    || mouseY >= timeline.height()) {
                return null;
            }
            int localX = (int) mouseX;
            int localY = (int) mouseY + timelineScroll;
            int index = timeline.hit(localX, localY);
            if (index >= 0) {
                return StageTimeline.hoverText(recipe.presentation().stages().get(index));
            }
            StageTimeline.Lane lane = timeline.headAt(localX, localY);
            return lane == null || lane.head() == null ? null : lane.head().group();
        }

        /** One link's caption under the cursor names its metric; null when the cursor is elsewhere. */
        @Nullable
        String linkHover(BlueprintPreviewRecipe recipe, double mouseX, double mouseY) {
            SetPage set = recipe.setPage();
            if (set == null) {
                return null;
            }
            for (PreviewScene.View view : views()) {
                for (ConstraintOverlay.LinkLine link : ConstraintOverlay.links(set, 0.0F, still)) {
                    Vector2f from = recipe.scene()
                            .project(view, link.segment().ax(), link.segment().ay(), link.segment().az());
                    Vector2f to = recipe.scene()
                            .project(view, link.segment().bx(), link.segment().by(), link.segment().bz());
                    int textX = Math.round((from.x + to.x) * 0.5F)
                            - Minecraft.getInstance().font.width(link.caption()) / 2;
                    int textY = Math.round((from.y + to.y) * 0.5F) - 9;
                    if (inside(
                        mouseX,
                        mouseY,
                        textX - 1,
                        textY - 1,
                        Minecraft.getInstance().font.width(link.caption()) + 2,
                        10
                    )) {
                        return link.tooltip();
                    }
                }
            }
            return null;
        }
    }
}
