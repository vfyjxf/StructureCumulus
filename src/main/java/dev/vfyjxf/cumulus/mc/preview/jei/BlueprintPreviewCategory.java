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

import java.util.Map;

/**
 * The "Multiblock Preview" category: the isometric scene, the Y-slice slider, the material summary and
 * the "Ghost to world" button. Everything it draws is the catalog the server pushed (design 15 §6).
 */
final class BlueprintPreviewCategory implements IRecipeCategory<BlueprintPreviewRecipe> {

    static final RecipeType<BlueprintPreviewRecipe> type = RecipeType
            .create(Constants.modId, "multiblock_preview", BlueprintPreviewRecipe.class);

    static final int width = 176;
    static final int height = 148;

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

    private static final int columns = 4;
    private static final int tile = 18;
    private static final int panelColor = 0x40000000;
    private static final int trackColor = 0x30FFFFFF;
    private static final int thumbColor = 0xFFE0E0E0;
    private static final int buttonColor = 0xFF2E4A1E;
    private static final int buttonHoverColor = 0xFF4A6B2A;
    private static final int borderColor = 0xFF101010;

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
        JeiPreview.recordFrame(
            recipe.scene().draw(
                graphics,
                sceneX,
                sceneY,
                sceneWidth,
                sceneHeight,
                recipe.yaw(),
                recipe.pitch(),
                recipe.slice()
            )
        );
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
        drawMaterials(graphics, recipe, mouseX, mouseY);
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        BlueprintPreviewRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        PreviewScene.Cell cell = recipe.scene().hover(
            mouseX,
            mouseY,
            sceneX,
            sceneY,
            sceneWidth,
            sceneHeight,
            recipe.yaw(),
            recipe.pitch(),
            recipe.slice()
        );
        if (cell != null) {
            tooltip.add(Component.literal(cell.ghost().block().text()));
            for (Map.Entry<String, String> property : cell.ghost().properties().entrySet()) {
                tooltip.add(Component.literal(property.getKey() + ": " + property.getValue()));
            }
            return;
        }
        int material = materialAt(recipe, mouseX, mouseY);
        if (material >= 0) {
            BlueprintPreviewRecipe.Material line = recipe.materials().get(material);
            tooltip.add(line.stack().getHoverName());
            tooltip.add(Component.literal("x " + line.count()));
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
        boolean hovered = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
        graphics.fill(
            buttonX,
            buttonY,
            buttonX + buttonWidth,
            buttonY + buttonHeight,
            hovered ? buttonHoverColor : buttonColor
        );
        graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 1, borderColor);
        graphics.fill(buttonX, buttonY + buttonHeight - 1, buttonX + buttonWidth, buttonY + buttonHeight, borderColor);
        graphics.fill(buttonX, buttonY, buttonX + 1, buttonY + buttonHeight, borderColor);
        graphics.fill(buttonX + buttonWidth - 1, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, borderColor);
        graphics.drawString(
            Minecraft.getInstance().font,
            "Ghost to world",
            buttonX + 4,
            buttonY + 4,
            0xFFF0F0F0,
            false
        );
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
