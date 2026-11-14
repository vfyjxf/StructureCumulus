package dev.vfyjxf.cumulus.mc.preview.jei;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cumulus.mc.preview.client.screen.StageTimeline;
import dev.vfyjxf.cumulus.mc.preview.jei.BlueprintPreviewCategory.PageView;
import dev.vfyjxf.cumulus.mc.preview.scene.BlueprintPreviewRecipe;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewViewMode;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.Util;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * The category's one input surface: drag rotates the isometric camera, the wheel zooms it, a double
 * click frames the hull, a click on a timeline segment selects that stage, the slider reveals a Y layer,
 * the buttons cycle the view, toggle the set overlay and open the session or the screen. All
 * coordinates are recipe-local (design 19 §5).
 */
final class PreviewInputHandler implements IJeiInputHandler {

    private enum Mode {
        none, rotate, slice, button, view, variantPrev, variantNext, screen, timeline, constraints
    }

    private static final float degreesPerPixel = 1.6F;
    private static final float pitchLimit = 85.0F;

    private final BlueprintPreviewRecipe recipe;
    private final PageView view;
    private Mode mode = Mode.none;
    private boolean dragged;

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
            dragged = false;
            return hit != Mode.none;
        }
        Mode pressed = mode;
        mode = Mode.none;
        if (pressed == Mode.constraints && hit == Mode.constraints) {
            view.toggleConstraints();
            return true;
        }
        if (pressed == Mode.timeline && hit == Mode.timeline) {
            view.selectTimeline(
                view.timeline(recipe),
                (int) mouseX - BlueprintPreviewCategory.timelineX,
                (int) mouseY - BlueprintPreviewCategory.timelineY
            );
            return true;
        }
        if (pressed == Mode.rotate && hit == Mode.rotate && !dragged) {
            // two clicks inside the threshold, with no drag between, are the screen's frame-all
            view.clickScene(Util.getMillis());
            return true;
        }
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
                dragged = true;
                view.drag();
                // the three-view and the grid fix their planes, so only the isometric view turns
                if (view.mode() != PreviewViewMode.isometric) {
                    yield true;
                }
                view.yaw(view.yaw() + (float) dragX * degreesPerPixel);
                view.pitch(clamp(view.pitch() + (float) dragY * degreesPerPixel));
                yield true;
            }
            case slice -> {
                dragged = true;
                sliceAt(mouseY);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (scrollDeltaY == 0.0D) {
            return false;
        }
        if (insideTimeline(mouseX, mouseY)) {
            // the strip scrolls under the wheel exactly as the screen's does (design 19 §3)
            var timeline = view.timeline(recipe);
            if (timeline.scrollable()) {
                view.timelineScroll(
                    timeline.clampScroll(
                        view.timelineScroll() - (int) Math.round(scrollDeltaY * StageTimeline.scrollStepPx)
                    )
                );
                return true;
            }
            return false;
        }
        if (inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.sliderX,
            BlueprintPreviewCategory.sliderY,
            BlueprintPreviewCategory.sliderWidth,
            BlueprintPreviewCategory.sliderHeight
        )) {
            // the slice keeps the slider's own wheel; the viewport's wheel zooms (design 15 §6.2's amendment)
            int target = view.slice() + (scrollDeltaY > 0.0D ? 1 : -1);
            view.slice(Math.max(0, Math.min(recipe.scene().layers() - 1, target)));
            return true;
        }
        if (inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.sceneX,
            BlueprintPreviewCategory.sceneY,
            BlueprintPreviewCategory.sceneWidth,
            BlueprintPreviewCategory.sceneHeight
        )) {
            view.zoomBy((float) scrollDeltaY);
            return true;
        }
        return false;
    }

    private Mode modeAt(double mouseX, double mouseY) {
        if (recipe.variantCount() > 1
                && inside(
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
            return Mode.none;
        }
        if (inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.screenX,
            BlueprintPreviewCategory.screenY,
            BlueprintPreviewCategory.screenWidth,
            BlueprintPreviewCategory.screenHeight
        )) {
            return Mode.screen;
        }
        if (inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.modeX,
            BlueprintPreviewCategory.modeY,
            BlueprintPreviewCategory.modeWidth,
            BlueprintPreviewCategory.modeHeight
        )) {
            return Mode.view;
        }
        if (recipe.isSet()
                && inside(
                    mouseX,
                    mouseY,
                    BlueprintPreviewCategory.constraintsX,
                    BlueprintPreviewCategory.constraintsY,
                    BlueprintPreviewCategory.constraintsWidth,
                    BlueprintPreviewCategory.constraintsHeight
                )) {
            return Mode.constraints;
        }
        if (!recipe.isSet()
                && inside(
                    mouseX,
                    mouseY,
                    BlueprintPreviewCategory.buttonX,
                    BlueprintPreviewCategory.buttonY,
                    BlueprintPreviewCategory.buttonWidth,
                    BlueprintPreviewCategory.buttonHeight
                )) {
            return Mode.button;
        }
        if (inside(
            mouseX,
            mouseY,
            BlueprintPreviewCategory.sliderX,
            BlueprintPreviewCategory.sliderY,
            BlueprintPreviewCategory.sliderWidth,
            BlueprintPreviewCategory.sliderHeight
        )) {
            return Mode.slice;
        }
        if (insideTimeline(mouseX, mouseY)) {
            return Mode.timeline;
        }
        if (inside(
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

    private boolean insideTimeline(double mouseX, double mouseY) {
        var timeline = view.timeline(recipe);
        return timeline.height() > 0
                && inside(
                    mouseX,
                    mouseY,
                    BlueprintPreviewCategory.timelineX,
                    BlueprintPreviewCategory.timelineY,
                    timeline.width(),
                    timeline.height()
                );
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return BlueprintPreviewCategory.inside(mouseX, mouseY, x, y, width, height);
    }

    private static float clamp(float pitch) {
        return Math.max(-pitchLimit, Math.min(pitchLimit, pitch));
    }
}
