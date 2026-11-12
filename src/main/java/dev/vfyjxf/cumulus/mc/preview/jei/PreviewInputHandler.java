package dev.vfyjxf.cumulus.mc.preview.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * The category's one input surface: drag rotates the isometric camera, the slider reveals a Y layer,
 * a click on the mode button cycles the view, and a click on the other button opens the session.
 * All coordinates are recipe-local.
 */
final class PreviewInputHandler implements IJeiInputHandler {

    private enum Mode {
        none, rotate, slice, button, view
    }

    private static final float degreesPerPixel = 1.6F;
    private static final float pitchLimit = 85.0F;

    private final BlueprintPreviewRecipe recipe;
    private Mode mode = Mode.none;

    PreviewInputHandler(BlueprintPreviewRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public ScreenRectangle getArea() {
        return new ScreenRectangle(0, 0, BlueprintPreviewCategory.width, BlueprintPreviewCategory.height);
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE || input.getKey().getValue() != 0) {
            return false;
        }
        Mode hit = modeAt(mouseX, mouseY);
        if (input.isSimulate()) {
            mode = hit;
            return hit != Mode.none;
        }
        Mode pressed = mode;
        mode = Mode.none;
        if (pressed == Mode.button && hit == Mode.button) {
            JeiPreview.openWorld(recipe.blueprintId());
            return true;
        }
        if (pressed == Mode.view && hit == Mode.view) {
            recipe.cycleViewMode();
            return true;
        }
        if (pressed == Mode.slice || hit == Mode.slice) {
            sliceAt(mouseY);
            return true;
        }
        return pressed != Mode.none;
    }

    @Override
    public boolean handleMouseDragged(
        double mouseX,
        double mouseY,
        InputConstants.Key mouseKey,
        double dragX,
        double dragY
    ) {
        if (mouseKey.getType() != InputConstants.Type.MOUSE || mouseKey.getValue() != 0) {
            return false;
        }
        return switch (mode) {
            case rotate -> {
                // the three-view and the grid fix their planes, so only the isometric view turns
                if (recipe.viewMode() != PreviewViewMode.isometric) {
                    yield true;
                }
                recipe.yaw(recipe.yaw() + (float) dragX * degreesPerPixel);
                recipe.pitch(clamp(recipe.pitch() + (float) dragY * degreesPerPixel));
                yield true;
            }
            case slice -> {
                sliceAt(mouseY);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (!BlueprintPreviewCategory.inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.sceneX,
            BlueprintPreviewCategory.sceneY,
            BlueprintPreviewCategory.sceneWidth,
            BlueprintPreviewCategory.sceneHeight
        )) {
            return false;
        }
        int layers = recipe.scene().layers();
        int target = recipe.slice() + (scrollDeltaY > 0.0D ? 1 : -1);
        recipe.slice(Math.max(0, Math.min(layers - 1, target)));
        return true;
    }

    private Mode modeAt(double mouseX, double mouseY) {
        if (BlueprintPreviewCategory.inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.modeX,
            BlueprintPreviewCategory.modeY,
            BlueprintPreviewCategory.modeWidth,
            BlueprintPreviewCategory.modeHeight
        )) {
            return Mode.view;
        }
        if (BlueprintPreviewCategory.inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.buttonX,
            BlueprintPreviewCategory.buttonY,
            BlueprintPreviewCategory.buttonWidth,
            BlueprintPreviewCategory.buttonHeight
        )) {
            return Mode.button;
        }
        if (BlueprintPreviewCategory.inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.sliderX,
            BlueprintPreviewCategory.sliderY,
            BlueprintPreviewCategory.sliderWidth,
            BlueprintPreviewCategory.sliderHeight
        )) {
            return Mode.slice;
        }
        if (BlueprintPreviewCategory.inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.sceneX,
            BlueprintPreviewCategory.sceneY,
            BlueprintPreviewCategory.sceneWidth,
            BlueprintPreviewCategory.sceneHeight
        )) {
            return Mode.rotate;
        }
        return Mode.none;
    }

    private void sliceAt(double mouseY) {
        int layers = recipe.scene().layers();
        if (layers <= 1) {
            recipe.slice(0);
            return;
        }
        float fraction = 1.0F
                - (float) ((mouseY - BlueprintPreviewCategory.sliderY) / BlueprintPreviewCategory.sliderHeight);
        int slice = Math.round(fraction * (layers - 1));
        recipe.slice(Math.max(0, Math.min(layers - 1, slice)));
    }

    private static float clamp(float pitch) {
        return Math.max(-pitchLimit, Math.min(pitchLimit, pitch));
    }
}
