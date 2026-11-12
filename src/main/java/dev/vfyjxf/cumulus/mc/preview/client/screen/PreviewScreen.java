package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout.Rect;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload.Entry;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import dev.vfyjxf.cumulus.mc.preview.scene.AxisGizmo;
import dev.vfyjxf.cumulus.mc.preview.scene.Fit;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene.Cell;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The catalogue page as a room (design 17 §5): one main viewport the viewer owns and three
 * axis-locked panels, all four drawn from one scene and one shared camera. The page is read-only;
 * nothing here writes a block or opens a session.
 */
public final class PreviewScreen extends Screen {

    /** One of the four panels: the main viewport or one axis-locked one (design 17 §5.1). */
    enum Panel {
        main, front, side, top
    }

    private static final int panelColor = 0x30000000;
    private static final int chromeColor = 0xFFC0C0C0;
    private static final int titleColor = 0xFFE8E8E8;
    private static final int buttonColor = 0xFF2E4A1E;
    private static final int buttonHoverColor = 0xFF4A6B2A;
    private static final int buttonTextColor = 0xFFF0F0F0;
    private static final int tooltipColor = 0xE0101010;
    private static final int tooltipTextColor = 0xFFF0F0F0;
    private static final int outlineColor = 0xFFE8E8E8;
    private static final int buttonHeight = 14;
    private static final int buttonGap = 4;
    private static final float degreesPerPixel = 1.6F;
    private static final float scrollStep = 1.1F;

    private final NamespacedId blueprintId;
    private final Runnable onCatalog = this::reconcile;
    private PreviewCatalogPayload.Entry entry;
    private PreviewScene scene;
    private SceneCamera camera = SceneCamera.isometric();
    private ScreenLayout layout;
    private boolean listening;
    private int drawnCells;
    private boolean dragging;
    private boolean panning;
    private boolean draggedSinceClick;
    private long lastClickMillis = -1L;
    private double pointerX = -1.0D;
    private double pointerY = -1.0D;
    private ScreenClock clock = ScreenClock.start();
    private @Nullable Cell hovered;
    private @Nullable AxisGizmo gizmo;

    private PreviewScreen(Entry entry) {
        super(Component.literal(entry.blueprintId().text()));
        this.blueprintId = entry.blueprintId();
        this.entry = entry;
        this.scene = PreviewScene.of(entry);
        this.layout = ScreenLayout.of(320, 240);
    }

    /** Opens the page for {@code blueprintId}; an id the snapshot does not carry opens nothing. */
    public static void open(NamespacedId blueprintId) {
        Minecraft minecraft = Minecraft.getInstance();
        Entry entry = find(blueprintId);
        if (entry == null) {
            say(minecraft, "no preview page for " + blueprintId.text());
            return;
        }
        minecraft.setScreen(new PreviewScreen(entry));
    }

    /** The last catalogue's entry for {@code blueprintId}, or null when it carries none. */
    static @Nullable Entry find(NamespacedId blueprintId) {
        for (Entry candidate : PreviewCatalogClient.entries()) {
            if (candidate.blueprintId().equals(blueprintId)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public void added() {
        super.added();
        if (!listening) {
            PreviewCatalogClient.listen(onCatalog);
            listening = true;
        }
    }

    @Override
    public void removed() {
        if (listening) {
            PreviewCatalogClient.unlisten(onCatalog);
            listening = false;
        }
        super.removed();
    }

    @Override
    protected void init() {
        super.init();
        layout = ScreenLayout.of(width, height);
        camera = camera.frame(scene.hull(), mainAspect());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        clock = clock.advance(Minecraft.getInstance().getTimer().getRealtimeDeltaTicks());
        double cursorX = pointerX < 0.0D ? mouseX : pointerX;
        double cursorY = pointerY < 0.0D ? mouseY : pointerY;
        hovered = hoveredCell(cursorX, cursorY);
        graphics.drawString(font, blueprintId.text(), 4, 6, titleColor, false);
        drawnCells = 0;
        drawPanel(graphics, layout.main(), Panel.main, false);
        drawPanel(graphics, layout.front(), Panel.front, true);
        drawPanel(graphics, layout.side(), Panel.side, true);
        drawPanel(graphics, layout.top(), Panel.top, true);
        graphics.fill(0, layout.control().y(), width, layout.control().y() + layout.control().height(), panelColor);
        drawButtons(graphics, (int) cursorX, (int) cursorY);
        if (hovered != null) {
            drawCellTooltip(graphics, hovered, (int) cursorX, (int) cursorY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_F -> {
                frameAll();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                resetView();
                return true;
            }
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> {
                snapMain(control ? SceneCamera.Snap.back : SceneCamera.Snap.front);
                return true;
            }
            case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> {
                snapMain(control ? SceneCamera.Snap.left : SceneCamera.Snap.right);
                return true;
            }
            case GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_KP_7 -> {
                snapMain(control ? SceneCamera.Snap.bottom : SceneCamera.Snap.top);
                return true;
            }
            default -> {
                // every other key stays the screen's own or closes it through Screen's Esc
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (Button candidate : buttons()) {
            if (candidate.inside(mouseX, mouseY)) {
                candidate.action().run();
                return true;
            }
        }
        if (button == 0 || button == 2) {
            if (!inside(layout.main(), mouseX, mouseY)) {
                return false;
            }
            if (button == 0) {
                long now = Util.getMillis();
                if (lastClickMillis >= 0 && now - lastClickMillis <= DOUBLE_CLICK_THRESHOLD_MS && !draggedSinceClick) {
                    frameAll();
                    lastClickMillis = -1L;
                    return true;
                }
                lastClickMillis = now;
            }
            dragging = true;
            panning = button == 2 || hasShiftDown();
            draggedSinceClick = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        draggedSinceClick = true;
        pointerX = mouseX;
        pointerY = mouseY;
        if (panning) {
            float units = mainUnitsPerPixel();
            camera = camera.pan((float) (dragX * units), (float) (-dragY * units));
        } else {
            camera = camera.orbit((float) (dragX * degreesPerPixel), (float) (dragY * degreesPerPixel));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!inside(layout.main(), mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }
        camera = camera.zoom((float) Math.pow(scrollStep, scrollY));
        return true;
    }

    /** Frame all: target the drawn hull's centre and reset the orthographic zoom. */
    private void frameAll() {
        camera = camera.frame(scene.hull(), mainAspect());
    }

    /** The default 45° isometric, framed: the state a fresh open produces. */
    private void resetView() {
        camera = camera.reset(scene.hull(), mainAspect());
    }

    private void snapMain(SceneCamera.Snap snap) {
        camera = camera.snap(snap);
    }

    private void toggleProjection() {
        SceneCamera.Projection next = camera.projection() == SceneCamera.Projection.orthographic
                ? SceneCamera.Projection.perspective
                : SceneCamera.Projection.orthographic;
        camera = camera.withProjection(next, scene.hull());
    }

    /** How many cells one pixel covers for pan, from the main viewport's own fit. */
    private float mainUnitsPerPixel() {
        Rect main = layout.main();
        if (main.isEmpty()) {
            return 1.0F;
        }
        return Fit.of(scene.hull(), main.x(), main.y(), main.width(), main.height(), camera).unitsPerPixel();
    }

    private static boolean inside(@Nullable Rect rect, double mouseX, double mouseY) {
        return rect != null
                && !rect.isEmpty()
                && mouseX >= rect.x()
                && mouseX < rect.x() + rect.width()
                && mouseY >= rect.y()
                && mouseY < rect.y() + rect.height();
    }

    private List<Button> buttons() {
        List<Button> buttons = new ArrayList<>();
        int y = layout.control().y() + buttonGap;
        int x = buttonGap;
        x = add(buttons, x, y, "close", "close (Esc)", this::onClose);
        x = add(buttons, x, y, "iso", "reset: iso (Home)", this::resetView);
        x = add(buttons, x, y, "frame", "frame all (F / double click)", this::frameAll);
        x = add(buttons, x, y, "front", "front (1)", () -> snapMain(SceneCamera.Snap.front));
        x = add(buttons, x, y, "side", "side (3)", () -> snapMain(SceneCamera.Snap.right));
        x = add(buttons, x, y, "top", "top (7)", () -> snapMain(SceneCamera.Snap.top));
        String projection = camera.projection() == SceneCamera.Projection.orthographic ? "ortho" : "perspective";
        add(buttons, x, y, projection, "projection: " + projection, this::toggleProjection);
        return List.copyOf(buttons);
    }

    private int add(List<Button> buttons, int x, int y, String label, String tooltip, Runnable action) {
        int width = font.width(label) + 10;
        buttons.add(new Button(new Rect(x, y, width, buttonHeight), label, tooltip, action));
        return x + width + buttonGap;
    }

    private void drawButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Button button : buttons()) {
            if (button.rect().x() + button.rect().width() > width - buttonGap) {
                continue;
            }
            boolean hovered = button.inside(mouseX, mouseY);
            graphics.fill(
                button.rect().x(),
                button.rect().y(),
                button.rect().x() + button.rect().width(),
                button.rect().y() + button.rect().height(),
                hovered ? buttonHoverColor : buttonColor
            );
            graphics.drawString(
                font,
                button.label(),
                button.rect().x() + 5,
                button.rect().y() + 3,
                buttonTextColor,
                false
            );
            if (hovered) {
                int textWidth = font.width(button.tooltip());
                int textX = Math.max(2, (int) Math.min(mouseX + 8.0D, width - textWidth - 6));
                int textY = Math.max(2, (int) mouseY - 12);
                graphics.fill(textX - 3, textY - 3, textX + textWidth + 3, textY + 10, tooltipColor);
                graphics.drawString(font, button.tooltip(), textX, textY, tooltipTextColor, false);
            }
        }
    }

    /** One control-strip button: its rectangle, its face text and the tooltip that names its key. */
    private record Button(Rect rect, String label, String tooltip, Runnable action) {

        boolean inside(double mouseX, double mouseY) {
            return PreviewScreen.inside(rect, mouseX, mouseY);
        }
    }

    private void drawPanel(GuiGraphics graphics, @Nullable Rect rect, Panel panel, boolean label) {
        if (rect == null || rect.isEmpty()) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), panelColor);
        SceneCamera panelCamera = cameraFor(camera, panel);
        Fit fit = Fit.of(scene.hull(), rect.x(), rect.y(), rect.width(), rect.height(), panelCamera);
        graphics.enableScissor(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height());
        drawnCells += scene.draw(graphics, fit).cells();
        if (panel == Panel.main) {
            drawGizmo(graphics, panelCamera, rect);
        }
        if (hovered != null) {
            drawOutline(graphics, fit, hovered);
        }
        graphics.disableScissor();
        if (label) {
            graphics.drawString(font, panel.name(), rect.x() + 2, rect.y() + 2, chromeColor, false);
        }
    }

    /** The axes of design 17 §6: scene geometry through the main camera, named at their ends. */
    private void drawGizmo(GuiGraphics graphics, SceneCamera panelCamera, Rect rect) {
        AxisGizmo axes = AxisGizmo.of(scene.hull(), panelCamera, rect.x(), rect.y(), rect.width(), rect.height());
        gizmo = axes;
        drawAxis(graphics, axes.x(), "X", AxisGizmo.xColor);
        drawAxis(graphics, axes.y(), "Y", AxisGizmo.yColor);
        drawAxis(graphics, axes.z(), "Z", AxisGizmo.zColor);
    }

    private void drawAxis(GuiGraphics graphics, AxisGizmo.Segment axis, String name, int color) {
        line(
            graphics,
            Math.round(axis.start().x),
            Math.round(axis.start().y),
            Math.round(axis.end().x),
            Math.round(axis.end().y),
            color
        );
        graphics.drawString(font, name, Math.round(axis.label().x) + 1, Math.round(axis.label().y) - 4, color, false);
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = x0 + Math.round((x1 - x0) * (step / (float) steps));
            int y = y0 + Math.round((y1 - y0) * (step / (float) steps));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** The hovered cell's 1-px chrome box, drawn in every viewport it appears in (design 17 §5.3). */
    private void drawOutline(GuiGraphics graphics, Fit fit, Cell cell) {
        LatticePoint at = cell.ghost().cell();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vector2f point = fit.project(
                at.x() + ((corner & 1) == 0 ? 0.0F : 1.0F),
                at.y() + ((corner & 2) == 0 ? 0.0F : 1.0F),
                at.z() + ((corner & 4) == 0 ? 0.0F : 1.0F)
            );
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        int left = Math.round(minX);
        int right = Math.round(maxX);
        int top = Math.round(minY);
        int bottom = Math.round(maxY);
        graphics.fill(left, top, right, top + 1, outlineColor);
        graphics.fill(left, bottom - 1, right, bottom, outlineColor);
        graphics.fill(left, top, left + 1, bottom, outlineColor);
        graphics.fill(right - 1, top, right, bottom, outlineColor);
    }

    /** 15 §6.2's tooltip plus the owning stage's name (design 17 §5.3). */
    private void drawCellTooltip(GuiGraphics graphics, Cell cell, int mouseX, int mouseY) {
        List<String> lines = cellLines(cell);
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int textX = Math.max(2, Math.min(mouseX + 10, width - textWidth - 6));
        int textY = Math.max(2, Math.min(mouseY + 10, height - lines.size() * 10 - 6));
        graphics.fill(textX - 3, textY - 3, textX + textWidth + 3, textY + lines.size() * 10, tooltipColor);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), textX, textY + index * 10, tooltipTextColor, false);
        }
    }

    /** The cell's block id, its properties in key order and the stage that owns it. */
    private List<String> cellLines(Cell cell) {
        List<String> lines = new ArrayList<>(cell.ghost().properties().size() + 2);
        lines.add(cell.ghost().block().text());
        cell.ghost().properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(property -> lines.add(property.getKey() + ": " + property.getValue()));
        List<Stage> stages = entry.presentation().stages();
        int index = cell.ghost().stageIndex();
        if (index >= 0 && index < stages.size()) {
            lines.add("part: " + stages.get(index).name());
        }
        return List.copyOf(lines);
    }

    /** The one hover of the frame: the panel under the cursor answers for itself (design 17 §5.3). */
    private @Nullable Cell hoveredCell(double mouseX, double mouseY) {
        for (Panel panel : Panel.values()) {
            Rect rect = rectFor(panel);
            if (!inside(rect, mouseX, mouseY)) {
                continue;
            }
            return scene.hover(mouseX, mouseY, fitFor(panel));
        }
        return null;
    }

    private Fit fitFor(Panel panel) {
        Rect rect = rectFor(panel);
        if (rect == null) {
            return Fit.of(scene.hull(), 0, 0, 1, 1, cameraFor(camera, panel));
        }
        return Fit.of(scene.hull(), rect.x(), rect.y(), rect.width(), rect.height(), cameraFor(camera, panel));
    }

    private @Nullable Rect rectFor(Panel panel) {
        return switch (panel) {
            case main -> layout.main();
            case front -> layout.front();
            case side -> layout.side();
            case top -> layout.top();
        };
    }

    /**
     * One panel's camera: the main one is the viewer's, the three small ones are axis-locked and always
     * orthographic, and all four share the target, the distance and the zoom (design 17 §5.2).
     */
    static SceneCamera cameraFor(SceneCamera shared, Panel panel) {
        return switch (panel) {
            case main -> shared;
            case front -> axisLocked(shared, 180.0F, 0.0F);
            case side -> axisLocked(shared, 270.0F, 0.0F);
            case top -> axisLocked(shared, 0.0F, SceneCamera.maxPitch);
        };
    }

    private static SceneCamera axisLocked(SceneCamera shared, float yaw, float pitch) {
        return new SceneCamera(
            shared.targetX(),
            shared.targetY(),
            shared.targetZ(),
            yaw,
            pitch,
            shared.distance(),
            shared.zoom(),
            SceneCamera.Projection.orthographic
        );
    }

    private float mainAspect() {
        Rect main = layout.main();
        return main.isEmpty() ? 1.0F : main.width() / (float) main.height();
    }

    /** Rebuilds the page in place when the catalogue pushes; a vanished id closes the screen. */
    private void reconcile() {
        Entry updated = find(blueprintId);
        if (updated == null) {
            Minecraft minecraft = Minecraft.getInstance();
            say(minecraft, "the preview page for " + blueprintId.text() + " is gone");
            minecraft.setScreen(null);
            return;
        }
        if (updated != entry) {
            this.entry = updated;
            this.scene = PreviewScene.of(updated);
        }
    }

    private static void say(Minecraft minecraft, String line) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(line), false);
        }
    }

    /** The shared camera value; the four panels derive their own from it. */
    public SceneCamera camera() {
        return camera;
    }

    /** The current layout; the four viewport rectangles are recomputed from the screen size. */
    public ScreenLayout layout() {
        return layout;
    }

    /** The page's scene: one resolved cell list for all four panels. */
    public PreviewScene scene() {
        return scene;
    }

    /** The drawn cell count of the last frame, summed over the panels. */
    public int drawnCells() {
        return drawnCells;
    }

    /** The cell under the cursor in the viewport it is over, or null when none is (design 17 §5.3). */
    public @Nullable Cell hovered() {
        return hovered;
    }

    /** The screen's own clock: the tick cursor and the frame's fraction (design 17 §4.6). */
    public ScreenClock clock() {
        return clock;
    }

    /** The main viewport's gizmo as last drawn (design 17 §6). */
    public @Nullable AxisGizmo gizmo() {
        return gizmo;
    }
}
