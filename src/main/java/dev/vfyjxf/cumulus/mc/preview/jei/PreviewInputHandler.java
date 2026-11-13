package dev.vfyjxf.cumulus.mc.preview.jei;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cumulus.mc.preview.jei.BlueprintPreviewCategory.PageView;
import dev.vfyjxf.cumulus.mc.preview.scene.BlueprintPreviewRecipe;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewViewMode;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * The category's one input surface: drag rotates the isometric camera, the slider reveals a Y layer,
 * a click on the mode button cycles the view, a click on the lower button opens the session and a
 * click on the bottom one opens the screen. All coordinates are recipe-local.
 */
final class PreviewInputHandler implements IJeiInputHandler {

    private enum Mode {
        none, rotate, slice, button, view, variantPrev, variantNext, screen
    }

    private static final float degreesPerPixel = 1.6F;
    private static final float pitchLimit = 85.0F;

    private final BlueprintPreviewRecipe recipe;
    private final PageView view;
    private Mode mode = Mode.none;

    PreviewInputHandler(BlueprintPreviewRecipe recipe, PageView view) {
        this.recipe = recipe;
        this.view = view;
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
            // the selected variant is the blueprint the session opens (design 15 §12.4)
            JeiPreview.openSelected(recipe.blueprintId());
            return true;
        }
        if (pressed == Mode.view && hit == Mode.view) {
            view.cycleMode();
            return true;
        }
        if (pressed == Mode.screen && hit == Mode.screen) {
            // the page's own data and no packet: the screen reads the catalog this page is drawn from
            JeiPreview.openScreen(recipe.blueprintId());
            return true;
        }
        if (pressed == Mode.variantPrev && hit == Mode.variantPrev) {
            recipe.cycleVariant(-1);
            return true;
        }
        if (pressed == Mode.variantNext && hit == Mode.variantNext) {
            recipe.cycleVariant(1);
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
                if (view.mode() != PreviewViewMode.isometric) {
                    yield true;
                }
                view.yaw(view.yaw() + (float) dragX * degreesPerPixel);
                view.pitch(clamp(view.pitch() + (float) dragY * degreesPerPixel));
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
        int target = view.slice() + (scrollDeltaY > 0.0D ? 1 : -1);
        view.slice(Math.max(0, Math.min(layers - 1, target)));
        return true;
    }

    private Mode modeAt(double mouseX, double mouseY) {
        if (recipe.variantCount() > 1
                && BlueprintPreviewCategory.inside(
                    mouseX,
                    mouseY,
                    BlueprintPreviewCategory.variantX,
                    BlueprintPreviewCategory.variantY,
                    BlueprintPreviewCategory.variantWidth,
                    BlueprintPreviewCategory.variantHeight
                )) {
            boolean left = mouseX < BlueprintPreviewCategory.variantX + BlueprintPreviewCategory.variantArrowWidth;
            boolean right = mouseX
                    >= BlueprintPreviewCategory.variantX
                            + BlueprintPreviewCategory.variantWidth
                            - BlueprintPreviewCategory.variantArrowWidth;
            if (left) {
                return Mode.variantPrev;
            }
            if (right) {
                return Mode.variantNext;
            }
            return left ? Mode.variantPrev : Mode.none;
        }
        if (BlueprintPreviewCategory.inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.screenX,
            BlueprintPreviewCategory.screenY,
            BlueprintPreviewCategory.screenWidth,
            BlueprintPreviewCategory.screenHeight
        )) {
            return Mode.screen;
        }
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
        if (!recipe.isSet()
                && BlueprintPreviewCategory.inside(
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
            view.slice(0);
            return;
        }
        float fraction = 1.0F
                - (float) ((mouseY - BlueprintPreviewCategory.sliderY) / BlueprintPreviewCategory.sliderHeight);
        int slice = Math.round(fraction * (layers - 1));
        view.slice(Math.max(0, Math.min(layers - 1, slice)));
    }

    private static float clamp(float pitch) {
        return Math.max(-pitchLimit, Math.min(pitchLimit, pitch));
    }
}
