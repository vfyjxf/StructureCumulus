package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.Constants;
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
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Multiblock Preview" category: the isometric scene, the Y-slice slider, the material summary and
 * the "Ghost to world" button. Everything it draws is the catalog the server pushed (design 15 §6).
 */
final class BlueprintPreviewCategory implements IRecipeCategory<BlueprintPreviewRecipe> {

    static final RecipeType<BlueprintPreviewRecipe> type = RecipeType
            .create(Constants.modId, "multiblock_preview", BlueprintPreviewRecipe.class);

    static final int width = 176;
    static final int height = 178;

    static final int sceneX = 2;
    static final int sceneY = 2;
    static final int sceneWidth = 156;
    static final int sceneHeight = 104;

    static final int sliderX = 160;
    static final int sliderY = 2;
    static final int sliderWidth = 14;
    static final int sliderHeight = 104;

    static final int buttonX = 2;
    static final int buttonY = 108;
    static final int buttonWidth = 88;
    static final int buttonHeight = 16;

    static final int materialsX = 94;
    static final int materialsY = 108;
    static final int materialsWidth = 80;
    static final int materialsHeight = 38;

    static final int modeX = 2;
    static final int modeY = 148;
    static final int modeWidth = 172;
    static final int modeHeight = 14;

    /** The variant row: one page per group, the arrows switch the member in place (design 15 §12.3). */
    static final int variantX = 2;
    static final int variantY = 164;
    static final int variantWidth = 172;
    static final int variantHeight = 14;
    static final int variantArrowWidth = 18;

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
    private static final String[] panelNames = {"front", "side", "top"};

    private static final BlueprintPreviewCategory instance = new BlueprintPreviewCategory();

    private @Nullable IDrawable icon;

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
        builder.addInputHandler(new PreviewInputHandler(recipe));
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
        PreviewScene.View[] views = views(recipe.viewMode(), recipe.yaw(), recipe.pitch());
        for (PreviewScene.View view : views) {
            if (view.width() < sceneWidth) {
                graphics.fill(view.x(), view.y(), view.x() + view.width(), view.y() + view.height(), panelBackColor);
            }
        }
        List<JeiPreview.Panel> panels = new ArrayList<>(views.length);
        for (int index = 0; index < views.length; index++) {
            PreviewScene.View view = views[index];
            PreviewScene.Panel drawn = recipe.scene()
                    .draw(graphics, view, recipe.slice(), band(recipe.viewMode(), index));
            panels.add(
                new JeiPreview.Panel(drawn.cells(), drawn.layer(), view.x(), view.y(), view.width(), view.height())
            );
            graphics.drawString(
                Minecraft.getInstance().font,
                panelName(recipe.viewMode(), index),
                view.x() + 2,
                view.y() + 2,
                labelColor,
                false
            );
        }
        JeiPreview.recordFrame(panels);
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
        drawSlider(graphics, recipe);
        drawButton(graphics, mouseX, mouseY);
        drawModeButton(graphics, recipe, mouseX, mouseY);
        drawVariantRow(graphics, recipe, mouseX, mouseY);
        drawMaterials(graphics, recipe, mouseX, mouseY);
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

    /** The panels the current mode draws, in the order the hover asks them; the fit is the entry's bounds. */
    static PreviewScene.View[] views(PreviewViewMode mode, float yaw, float pitch) {
        return switch (mode) {
            case isometric -> new PreviewScene.View[]{new PreviewScene.View(
                sceneX,
                sceneY,
                sceneWidth,
                sceneHeight,
                yaw,
                pitch,
                PreviewScene.LayerRule.upTo
            )};
            case threeView -> new PreviewScene.View[]{
                    new PreviewScene.View(
                        sceneX,
                        sceneY,
                        panelWidth,
                        sceneHeight,
                        180.0F,
                        0.0F,
                        PreviewScene.LayerRule.all
                    ),
                    new PreviewScene.View(
                        sceneX + panelWidth + panelGap,
                        sceneY,
                        panelWidth,
                        sceneHeight,
                        90.0F,
                        0.0F,
                        PreviewScene.LayerRule.all
                    ),
                    new PreviewScene.View(
                        sceneX + 2 * (panelWidth + panelGap),
                        sceneY,
                        panelWidth,
                        sceneHeight,
                        0.0F,
                        90.0F,
                        PreviewScene.LayerRule.exact
                    )};
            case sliceGrid -> new PreviewScene.View[]{new PreviewScene.View(
                sceneX,
                sceneY,
                sceneWidth,
                sceneHeight,
                0.0F,
                90.0F,
                PreviewScene.LayerRule.exact
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
        List<String> cellLines = recipe.hoverLines(mouseX, mouseY);
        if (!cellLines.isEmpty()) {
            for (String line : cellLines) {
                tooltip.add(Component.literal(line));
            }
            return;
        }
        int material = materialAt(recipe, mouseX, mouseY);
        if (material >= 0) {
            BlueprintPreviewRecipe.Material line = recipe.materials().get(material);
            tooltip.add(line.stack().getHoverName());
            tooltip.add(Component.literal("x " + line.count()));
            return;
        }
        for (String line : recipe.lines()) {
            tooltip.add(Component.literal(line));
        }
    }

    private void drawSlider(GuiGraphics graphics, BlueprintPreviewRecipe recipe) {
        int layers = recipe.scene().layers();
        graphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, trackColor);
        int travel = sliderHeight - 6;
        float fraction = layers <= 1 ? 1.0F : recipe.slice() / (float) (layers - 1);
        int thumbY = sliderY + Math.round((1.0F - fraction) * travel);
        graphics.fill(sliderX + 2, thumbY, sliderX + sliderWidth - 2, thumbY + 6, thumbColor);
        graphics.drawString(
            Minecraft.getInstance().font,
            Integer.toString(recipe.slice() + 1),
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

    private void drawModeButton(GuiGraphics graphics, BlueprintPreviewRecipe recipe, double mouseX, double mouseY) {
        drawFace(graphics, modeX, modeY, modeWidth, modeHeight, mouseX, mouseY);
        graphics.drawString(
            Minecraft.getInstance().font,
            "view: " + recipe.viewMode().label(),
            modeX + 4,
            modeY + 3,
            0xFFF0F0F0,
            false
        );
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
}
