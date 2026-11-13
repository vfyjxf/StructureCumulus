package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.ClientConfig;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewCatalogClient;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Ease;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Tween;
import dev.vfyjxf.cumulus.mc.preview.client.screen.ScreenLayout.Rect;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload.Entry;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewOpenPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import dev.vfyjxf.cumulus.mc.preview.scene.AxisGizmo;
import dev.vfyjxf.cumulus.mc.preview.scene.CellOutline;
import dev.vfyjxf.cumulus.mc.preview.scene.ExplodeModel;
import dev.vfyjxf.cumulus.mc.preview.scene.Fit;
import dev.vfyjxf.cumulus.mc.preview.scene.Hull;
import dev.vfyjxf.cumulus.mc.preview.scene.NextStepLine;
import dev.vfyjxf.cumulus.mc.preview.scene.PopIn;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene;
import dev.vfyjxf.cumulus.mc.preview.scene.PreviewScene.Cell;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneLighting;
import dev.vfyjxf.cumulus.mc.preview.scene.StagePlayback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final int barColor = 0x60484848;
    private static final int barHoverColor = 0x906A6A6A;
    private static final int barCursorColor = 0xFFE8E8E8;
    private static final int buttonHeight = 14;
    private static final int buttonGap = 4;
    private static final int barTop = 3;
    private static final int barHeight = 9;
    private static final int summaryTop = 13;
    private static final int buttonsTop = 25;
    private static final float degreesPerPixel = 1.6F;
    private static final float scrollStep = 1.1F;
    private static final int explodeTicks = 12;
    /** The declaration order is the wire's tie-breaker, not the compass's (north, south, west, east). */
    private static final Heading[] horizons = {Heading.north, Heading.south, Heading.west, Heading.east};

    private NamespacedId blueprintId;
    private final Runnable onCatalog = this::reconcile;
    private PreviewCatalogPayload.Entry entry;
    private PreviewScene scene;
    private StagePlayback playback;
    private Map<GhostCell, StagePlayback.Placed> placed;
    private Map<LatticePoint, GhostCell> ghostAt;
    private ExplodeModel explode;
    private SceneCamera camera = SceneCamera.isometric();
    private ScreenLayout layout;
    private boolean listening;
    private int drawnCells;
    private int drawnLabels;
    private int drawnFutureBoxes;
    private boolean dragging;
    private boolean panning;
    private boolean draggedSinceClick;
    private long lastClickMillis = -1L;
    private double pointerX = -1.0D;
    private double pointerY = -1.0D;
    private ScreenClock clock = ScreenClock.start();
    private @Nullable Cell hovered;
    private @Nullable AxisGizmo gizmo;
    private StageBar stageBar = new StageBar(List.of(), 0, 0, 0, 0);
    private @Nullable String summary;
    private @Nullable String barHover;
    private boolean outline;
    private float explodeT;
    private float explodeTarget;
    private @Nullable Tween explodeTween;
    private float explodeTweenStart;
    private @Nullable StageFraming framing;
    private boolean framingArmed = true;
    private int framedCursor = -1;
    private PopIn popIn = PopIn.none();
    private Tween popScaleLeg = PopIn.scaleLeg(0);
    private Tween popAlphaLeg = PopIn.alphaLeg(0);
    private int animatingCells;
    private final Map<Panel, Shading> shading = new EnumMap<>(Panel.class);
    private final Map<Panel, CellOutline> outlines = new EnumMap<>(Panel.class);

    private PreviewScreen(Entry entry) {
        super(Component.literal(entry.blueprintId().text()));
        this.blueprintId = entry.blueprintId();
        this.entry = entry;
        this.scene = PreviewScene.of(entry);
        this.playback = StagePlayback.whole(entry.presentation().stages(), entry.cells());
        this.placed = index(playback);
        this.ghostAt = ghosts(entry.cells());
        this.explode = explodeModel();
        this.layout = ScreenLayout.of(320, 240);
    }

    /**
     * Opens the page for {@code blueprintId}; an id the snapshot does not carry opens nothing and
     * answers with the one client message (design 17 §3.4). True when a page opened.
     */
    public static boolean open(NamespacedId blueprintId) {
        Minecraft minecraft = Minecraft.getInstance();
        Entry entry = find(blueprintId);
        if (entry == null) {
            say(minecraft, "no preview page for " + blueprintId.text());
            return false;
        }
        minecraft.setScreen(new PreviewScreen(entry));
        return true;
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
        float delta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        clock = clock.advance(delta);
        playback = playback.advance(delta, cadence());
        tickPopIn();
        tickExplode();
        tickFraming();
        double cursorX = pointerX < 0.0D ? mouseX : pointerX;
        double cursorY = pointerY < 0.0D ? mouseY : pointerY;
        hovered = hoveredCell(cursorX, cursorY);
        graphics.drawString(font, blueprintId.text(), 4, 6, titleColor, false);
        String variant = variantLine();
        if (variant != null) {
            graphics.drawString(font, variant, 8 + font.width(blueprintId.text()), 6, chromeColor, false);
        }
        drawnCells = 0;
        drawnLabels = 0;
        drawnFutureBoxes = 0;
        drawPanel(graphics, layout.main(), Panel.main, false);
        drawPanel(graphics, layout.front(), Panel.front, true);
        drawPanel(graphics, layout.side(), Panel.side, true);
        drawPanel(graphics, layout.top(), Panel.top, true);
        graphics.fill(0, layout.control().y(), width, layout.control().y() + layout.control().height(), panelColor);
        drawStageBar(graphics, (int) cursorX, (int) cursorY);
        drawSummary(graphics);
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
            case GLFW.GLFW_KEY_SPACE -> {
                togglePlay();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                playback = playback.previous();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                playback = playback.next();
                return true;
            }
            case GLFW.GLFW_KEY_B -> {
                playback = playback.step();
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
        int stage = stageBar.hit(mouseX, mouseY);
        if (stage >= 0) {
            playback = playback.enter(stage);
            return true;
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
        handOnCamera();
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
        handOnCamera();
        camera = camera.zoom((float) Math.pow(scrollStep, scrollY));
        return true;
    }

    /** `all`: drops the cursor, draws every stage and stops the play (design 17 §7.1). */
    private void wholeView() {
        playback = playback.all();
        framedCursor = -1;
        framing = null;
        summary = null;
    }

    private void togglePlay() {
        boolean starting = !playback.playing();
        playback = playback.togglePlay(cadence());
        if (starting) {
            framingArmed = true;
            framedCursor = -1;
        }
    }

    /** The explode toggle: a 12-tick leg, and a second press reverses the same leg (design 17 §8.1). */
    private void toggleExplode() {
        if (!explode.separable()) {
            explodeTarget = 0.0F;
            return;
        }
        explodeTarget = explodeTarget > 0.5F ? 0.0F : 1.0F;
        explodeTween = new Tween(explodeT, explodeTarget, explodeTicks(), Ease.inOutCubic);
        explodeTweenStart = clock.time();
    }

    private void tickExplode() {
        if (explodeTween == null) {
            return;
        }
        double tick = clock.time() - explodeTweenStart;
        explodeT = (float) explodeTween.at(tick);
        if (explodeTween.done(tick)) {
            explodeT = (float) explodeTween.to();
            explodeTween = null;
        }
    }

    /** A stage advance during a play flies the main viewport, until a hand takes the camera (design 17 §7.5). */
    private void tickFraming() {
        if (playback.cursor() != framedCursor) {
            framedCursor = playback.cursor();
            if (framingArmed && playback.playing() && framedCursor >= 0) {
                startFraming(framedCursor);
            }
        }
        if (framing != null && framing.done(clock.time())) {
            camera = framing.to();
            framing = null;
        }
    }

    private void startFraming(int stage) {
        SceneCamera from = currentCamera();
        camera = from;
        framing = StageFraming.of(from, from.frame(scene.hull(stage), mainAspect()), clock.time(), focusTicks());
    }

    /** The first manual camera gesture disarms framing for the rest of that play-through. */
    private void handOnCamera() {
        camera = currentCamera();
        framing = null;
        framingArmed = false;
    }

    private SceneCamera currentCamera() {
        return framing == null ? camera : framing.at(clock.time());
    }

    /** Frame all: target the current hull's centre and fit it, exploded parts included (design 17 §8.3). */
    private void frameAll() {
        handOnCamera();
        camera = fitted(currentCamera().frame(scene.hull(), mainAspect()));
    }

    /** The default 45° isometric, framed: the state a fresh open produces. */
    private void resetView() {
        handOnCamera();
        camera = fitted(SceneCamera.isometric().frame(scene.hull(), mainAspect()));
    }

    private void snapMain(SceneCamera.Snap snap) {
        handOnCamera();
        camera = camera.snap(snap);
    }

    private void toggleProjection() {
        handOnCamera();
        SceneCamera.Projection next = camera.projection() == SceneCamera.Projection.orthographic
                ? SceneCamera.Projection.perspective
                : SceneCamera.Projection.orthographic;
        camera = camera.withProjection(next, scene.hull());
    }

    /** The zoom that makes the current hull, displaced cells included, exactly fill the page's fit. */
    private SceneCamera fitted(SceneCamera base) {
        Hull page = scene.hull();
        Hull current = currentHull();
        Rect main = layout.main();
        if (main.isEmpty() || page.isEmpty() || current.equals(page)) {
            return base;
        }
        float pageScale = Fit.of(page, main.x(), main.y(), main.width(), main.height(), base).scale();
        float currentScale = Fit.of(current, main.x(), main.y(), main.width(), main.height(), base).scale();
        if (currentScale >= pageScale) {
            return base;
        }
        return base.zoom(
            base.projection() == SceneCamera.Projection.orthographic
                    ? currentScale / pageScale
                    : current.radius() / page.radius()
        );
    }

    /** The page hull union every drawn cell's displaced box: frame all's subject (design 17 §8.3). */
    private Hull currentHull() {
        Hull hull = scene.hull();
        for (Cell cell : scene.cells()) {
            PreviewScene.Sight sight = sightOf(cell);
            if (sight == null) {
                continue;
            }
            LatticePoint at = cell.ghost().cell();
            hull = hull.union(
                new Hull(
                    at.x() + sight.dx(),
                    at.y() + sight.dy(),
                    at.z() + sight.dz(),
                    at.x() + sight.dx() + 1.0F,
                    at.y() + sight.dy() + 1.0F,
                    at.z() + sight.dz() + 1.0F
                )
            );
        }
        return hull;
    }

    /** How many cells one pixel covers for pan, from the main viewport's own fit. */
    private float mainUnitsPerPixel() {
        Rect main = layout.main();
        if (main.isEmpty()) {
            return 1.0F;
        }
        return Fit.of(scene.hull(), main.x(), main.y(), main.width(), main.height(), currentCamera()).unitsPerPixel();
    }

    private static boolean inside(@Nullable Rect rect, double mouseX, double mouseY) {
        return rect != null
                && !rect.isEmpty()
                && mouseX >= rect.x()
                && mouseX < rect.x() + rect.width()
                && mouseY >= rect.y()
                && mouseY < rect.y() + rect.height();
    }

    private List<Stage> stages() {
        return entry.presentation().stages();
    }

    private StagePlayback.Cadence cadence() {
        return StagePlayback.Cadence.of(ClientConfig.animationSpeed(), ClientConfig.animationEnabled());
    }

    private int explodeTicks() {
        return ClientConfig.animationEnabled()
                ? Math.max(1, (int) Math.ceil(explodeTicks / ClientConfig.animationSpeed()))
                : 0;
    }

    private int focusTicks() {
        return ClientConfig.animationEnabled()
                ? Math.max(1, (int) Math.ceil(StageFraming.stageFocusTicks / ClientConfig.animationSpeed()))
                : 0;
    }

    private static Map<LatticePoint, GhostCell> ghosts(List<GhostCell> cells) {
        Map<LatticePoint, GhostCell> byPoint = new HashMap<>();
        for (GhostCell ghost : cells) {
            byPoint.put(ghost.cell(), ghost);
        }
        return Map.copyOf(byPoint);
    }

    private static Map<GhostCell, StagePlayback.Placed> index(StagePlayback playback) {
        Map<GhostCell, StagePlayback.Placed> placed = new HashMap<>();
        for (StagePlayback.Placed cell : playback.cells()) {
            placed.put(cell.ghost(), cell);
        }
        return Map.copyOf(placed);
    }

    private ExplodeModel explodeModel() {
        List<GhostCell> drawn = new ArrayList<>();
        for (Cell cell : scene.cells()) {
            if (PreviewScene.draws(cell)) {
                drawn.add(cell.ghost());
            }
        }
        return ExplodeModel.of(stages(), drawn, scene.hull());
    }

    /** One cell's treatment: the stage's alpha, the pop-in's scale and the explode's offset (§7.2, §7.6, §8.1). */
    private PreviewScene.@Nullable Sight sightOf(Cell cell) {
        StagePlayback.Placed placedCell = placed.get(cell.ghost());
        if (placedCell == null) {
            return PreviewScene.Sight.full;
        }
        if (!playback.drawn(placedCell)) {
            return null;
        }
        ExplodeModel.Offset offset = explode.offset(cell.ghost(), explodeT);
        double now = clock.time();
        GhostCell ghost = cell.ghost();
        float alpha = playback.alpha(placedCell) * popIn.alpha(ghost, now, popAlphaLeg);
        float scale = popIn.scale(ghost, now, popScaleLeg);
        return new PreviewScene.Sight(alpha, scale, offset.x(), offset.y(), offset.z());
    }

    /**
     * One frame of the pop-in (design 17 §7.6): a cell newly revealed since the last frame takes the
     * clock's current tick as its reveal stamp, and a whole view pops nothing.
     */
    private void tickPopIn() {
        int duration = popInTicks();
        popScaleLeg = PopIn.scaleLeg(duration);
        popAlphaLeg = PopIn.alphaLeg(duration);
        double now = clock.time();
        popIn = playback.wholeView() ? PopIn.none() : popIn.advance(revealedNow(), now);
        int animating = 0;
        for (StagePlayback.Placed cell : placed.values()) {
            if (playback.drawn(cell) && popIn.animating(cell.ghost(), now, duration)) {
                animating++;
            }
        }
        animatingCells = animating;
    }

    /** The page's drawn-and-revealed cells: the set the pop-in stamps. */
    private Set<GhostCell> revealedNow() {
        Set<GhostCell> revealed = new HashSet<>();
        for (StagePlayback.Placed cell : placed.values()) {
            if (playback.drawn(cell) && playback.revealed(cell)) {
                revealed.add(cell.ghost());
            }
        }
        return revealed;
    }

    /** The pop-in's span, speed divided, zero when the animation is off (design 17 §7.5). */
    private int popInTicks() {
        return ClientConfig.animationEnabled()
                ? Math.max(1, (int) Math.ceil(PopIn.popInTicks / ClientConfig.animationSpeed()))
                : 0;
    }

    private List<ButtonSpec> buttonSpecs() {
        List<ButtonSpec> specs = new ArrayList<>();
        specs.add(new ButtonSpec("close", "close (Esc)", this::onClose));
        specs.add(new ButtonSpec("ghost to world", "open the ordinary session on this page", this::ghostToWorld));
        if (variantCount() > 1) {
            specs.add(new ButtonSpec("\u2039 variant", "the group's previous member", () -> switchVariant(-1)));
            specs.add(new ButtonSpec("variant \u203A", "the group's next member", () -> switchVariant(1)));
        }
        specs.add(new ButtonSpec("iso", "reset: iso (Home)", this::resetView));
        specs.add(new ButtonSpec("frame", "frame all (F / double click)", this::frameAll));
        specs.add(new ButtonSpec("front", "front (1)", () -> snapMain(SceneCamera.Snap.front)));
        specs.add(new ButtonSpec("side", "side (3)", () -> snapMain(SceneCamera.Snap.right)));
        specs.add(new ButtonSpec("top", "top (7)", () -> snapMain(SceneCamera.Snap.top)));
        String projection = camera.projection() == SceneCamera.Projection.orthographic ? "ortho" : "perspective";
        specs.add(new ButtonSpec(projection, "projection: " + projection, this::toggleProjection));
        specs.add(new ButtonSpec("all", "the whole view", this::wholeView));
        specs.add(
            new ButtonSpec(
                explode.separable() ? "explode" : "one part",
                explode.separable() ? "the exploded view" : "one part: nothing to separate",
                this::toggleExplode
            )
        );
        specs.add(
            new ButtonSpec(
                playback.playing() ? "pause" : "play",
                playback.playing() ? "pause the play (Space)" : "play the stages (Space)",
                this::togglePlay
            )
        );
        specs.add(new ButtonSpec("step", "one cell step (B)", () -> playback = playback.step()));
        specs.add(new ButtonSpec("<", "previous stage (\u2191)", () -> playback = playback.previous()));
        specs.add(new ButtonSpec(">", "next stage (\u2193)", () -> playback = playback.next()));
        specs.add(
            new ButtonSpec(
                outline ? "outline" : "hidden",
                outline ? "future cells: outline" : "future cells: hidden",
                () -> outline = !outline
            )
        );
        return List.copyOf(specs);
    }

    private List<Button> buttons() {
        List<Button> built = new ArrayList<>();
        int x = buttonGap;
        int y = layout.control().y() + buttonsTop;
        int bottom = layout.control().y() + layout.control().height() - 1;
        for (ButtonSpec spec : buttonSpecs()) {
            int specWidth = font.width(spec.label()) + 10;
            if (x + specWidth > width - buttonGap) {
                x = buttonGap;
                y += buttonHeight + 2;
            }
            if (y + buttonHeight > bottom) {
                break;
            }
            built.add(new Button(new Rect(x, y, specWidth, buttonHeight), spec.label(), spec.tooltip(), spec.action()));
            x += specWidth + buttonGap;
        }
        return List.copyOf(built);
    }

    private void drawButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Button button : buttons()) {
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
                drawTooltip(graphics, button.tooltip(), mouseX, mouseY);
            }
        }
    }

    private void drawTooltip(GuiGraphics graphics, String text, int mouseX, int mouseY) {
        int textWidth = font.width(text);
        int textX = Math.max(2, (int) Math.min(mouseX + 8.0D, width - textWidth - 6));
        int textY = Math.max(2, mouseY - 12);
        graphics.fill(textX - 3, textY - 3, textX + textWidth + 3, textY + 10, tooltipColor);
        graphics.drawString(font, text, textX, textY, tooltipTextColor, false);
    }

    /** The stage bar as a table of contents: proportional segments, the cursor outlined (design 17 §7.3). */
    private void drawStageBar(GuiGraphics graphics, int mouseX, int mouseY) {
        stageBar = StageBar.of(stages(), buttonGap, layout.control().y() + barTop, width - 2 * buttonGap, barHeight);
        int hoveredIndex = stageBar.hit(mouseX, mouseY);
        String barHover = null;
        for (StageBar.Segment segment : stageBar.segments()) {
            Stage stage = stages().get(segment.index());
            graphics.fill(
                segment.x(),
                stageBar.y(),
                segment.x() + segment.width(),
                stageBar.y() + stageBar.height(),
                segment.index() == hoveredIndex ? barHoverColor : barColor
            );
            if (segment.index() == playback.cursor()) {
                outlineBox(graphics, segment.x(), stageBar.y(), segment.width(), stageBar.height(), barCursorColor);
            }
            if (stageBar.fits(segment.index(), font.width(stage.name()))) {
                graphics.drawString(font, stage.name(), segment.x() + 1, stageBar.y() + 1, chromeColor, false);
            }
            if (segment.index() == hoveredIndex) {
                barHover = stage.name() + " \u00B7 " + stage.expectedCells() + " cells";
            }
        }
        if (barHover != null) {
            drawTooltip(graphics, barHover, mouseX, stageBar.y() - 2);
        }
    }

    /** 16 §12.3's next-step line, and only while a cursor exists (design 17 §7.4). */
    private void drawSummary(GuiGraphics graphics) {
        summary = summary();
        if (summary != null) {
            graphics.drawString(font, summary, buttonGap, layout.control().y() + summaryTop, titleColor, false);
        }
    }

    private @Nullable String summary() {
        int cursor = playback.cursor();
        if (cursor < 0 || cursor >= stages().size()) {
            return null;
        }
        Stage stage = stages().get(cursor);
        return NextStepLine.render(
            playback.stageCells(cursor),
            stage.expectedCells(),
            stage,
            cursor,
            stages().size(),
            false,
            false
        );
    }

    private void drawPanel(GuiGraphics graphics, @Nullable Rect rect, Panel panel, boolean label) {
        if (rect == null || rect.isEmpty()) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), panelColor);
        Fit fit = fitFor(panel);
        graphics.enableScissor(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height());
        drawnCells += scene.draw(graphics, fit, this::sightOf).cells();
        shading.put(panel, shadingOf(fit.camera()));
        outlines.remove(panel);
        drawFutureBoxes(graphics, fit);
        if (panel == Panel.main) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, PreviewScene.chromeDepth);
            drawGizmo(graphics, fit.camera(), rect);
            graphics.pose().popPose();
        }
        if (hovered != null) {
            PreviewScene.Sight sight = sightOf(hovered);
            if (sight != null) {
                CellOutline outline = CellOutline
                        .of(fit, hovered.ghost().cell(), sight.dx(), sight.dy(), sight.dz(), sight.scale());
                outlines.put(panel, outline);
                drawOutline(graphics, outline, outlineColor);
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, PreviewScene.chromeDepth);
        drawLabels(graphics, fit);
        graphics.pose().popPose();
        graphics.disableScissor();
        if (label) {
            graphics.drawString(font, panel.name(), rect.x() + 2, rect.y() + 2, chromeColor, false);
        }
    }

    /** The shading one panel installed this frame: the six world faces, in {@code Direction}'s order. */
    private static Shading shadingOf(SceneCamera camera) {
        Matrix3f normals = SceneLighting.normals(camera);
        Vector3f[] lights = SceneLighting.forCamera(camera);
        return new Shading(
            face(normals, lights, 0.0F, -1.0F, 0.0F),
            face(normals, lights, 0.0F, 1.0F, 0.0F),
            face(normals, lights, 0.0F, 0.0F, -1.0F),
            face(normals, lights, 0.0F, 0.0F, 1.0F),
            face(normals, lights, -1.0F, 0.0F, 0.0F),
            face(normals, lights, 1.0F, 0.0F, 0.0F)
        );
    }

    private static float face(Matrix3f normals, Vector3f[] lights, float x, float y, float z) {
        return SceneLighting.brightness(normals.transform(new Vector3f(x, y, z)), lights[0], lights[1]);
    }

    /** Outline mode's 1-px chrome outline at a future cell (design 17 §7.2). */
    private void drawFutureBoxes(GuiGraphics graphics, Fit fit) {
        if (!outline) {
            return;
        }
        for (Cell cell : scene.cells()) {
            if (!PreviewScene.draws(cell)) {
                continue;
            }
            StagePlayback.Placed placedCell = placed.get(cell.ghost());
            if (placedCell == null || playback.drawn(placedCell)) {
                continue;
            }
            drawOutline(graphics, CellOutline.of(fit, cell.ghost().cell(), 0.0F, 0.0F, 0.0F, 1.0F), chromeColor);
            drawnFutureBoxes++;
        }
    }

    /** One cell's chrome outline: a 1-px polygon on the cell's own silhouette, at its nearest depth. */
    private static void drawOutline(GuiGraphics graphics, CellOutline outline, int color) {
        graphics.pose().pushPose();
        graphics.pose()
                .translate(0.0F, 0.0F, PreviewScene.sceneDepth + outline.depth() + PreviewScene.sceneSpan * 0.002F);
        List<Vector2f> points = outline.points();
        for (int index = 0; index < points.size(); index++) {
            Vector2f from = points.get(index);
            Vector2f to = points.get((index + 1) % points.size());
            line(graphics, Math.round(from.x), Math.round(from.y), Math.round(to.x), Math.round(to.y), color);
        }
        graphics.pose().popPose();
    }

    /** Every part's name on its moved centroid, faded with `t` (design 17 §8.2). */
    private void drawLabels(GuiGraphics graphics, Fit fit) {
        if (explodeT <= 0.0F) {
            return;
        }
        List<ExplodeModel.Group> groups = explode.groups();
        List<ExplodeModel.Label> labels = explode.labels(explodeT);
        int alpha = (int) (Math.min(1.0F, explodeT) * 255.0F) << 24;
        for (int index = 0; index < Math.min(groups.size(), labels.size()); index++) {
            if (!partDrawn(groups.get(index))) {
                continue;
            }
            ExplodeModel.Label label = labels.get(index);
            Vector2f point = fit.project(label.x(), label.y(), label.z());
            int textX = Math.round(point.x) - font.width(label.text()) / 2;
            int textY = Math.round(point.y) - 4;
            graphics.drawString(font, label.text(), textX, textY, alpha | 0x00C0C0C0, false);
            drawnLabels++;
        }
    }

    /** A part with no drawn cell has no anchor, so it has no label (design 17 §8.2). */
    private boolean partDrawn(ExplodeModel.Group group) {
        for (LatticePoint at : group.cells()) {
            GhostCell ghost = ghostAt.get(at);
            if (ghost == null) {
                continue;
            }
            StagePlayback.Placed placedCell = placed.get(ghost);
            if (placedCell == null || playback.drawn(placedCell)) {
                return true;
            }
        }
        return false;
    }

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

    /** The six world faces in {@code Direction}'s declaration order, shaded as the panel installed them. */
    public record Shading(float down, float up, float north, float south, float west, float east) {}

    private static void outlineBox(GuiGraphics graphics, int left, int top, int width, int height, int color) {
        int right = left + width;
        int bottom = top + height;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
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
            return scene.hover(mouseX, mouseY, fitFor(panel), this::sightOf);
        }
        return null;
    }

    private Fit fitFor(Panel panel) {
        Rect rect = rectFor(panel);
        if (rect == null) {
            return Fit.of(scene.hull(), 0, 0, 1, 1, cameraFor(currentCamera(), panel));
        }
        return Fit.of(scene.hull(), rect.x(), rect.y(), rect.width(), rect.height(), cameraFor(currentCamera(), panel));
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
            applyEntry(updated);
        }
    }

    /**
     * Replaces the page's entry in place: the scene and the explode model are rebuilt, while the
     * camera, the explode's `t` and the whole/staged presentation stay where the viewer left them,
     * the cursor clamped to the new table (design 17 §3.4, §9.5).
     */
    private void applyEntry(Entry updated) {
        camera = currentCamera();
        framing = null;
        framedCursor = -1;
        entry = updated;
        blueprintId = updated.blueprintId();
        scene = PreviewScene.of(updated);
        int cursor = playback.cursor();
        playback = StagePlayback.whole(updated.presentation().stages(), updated.cells());
        if (cursor >= 0 && cursor < updated.presentation().stages().size()) {
            playback = playback.enter(cursor);
        }
        placed = index(playback);
        ghostAt = ghosts(updated.cells());
        popIn = PopIn.none();
        explode = explodeModel();
        if (!explode.separable()) {
            explodeT = 0.0F;
            explodeTarget = 0.0F;
            explodeTween = null;
        }
    }

    /**
     * The in-screen variant switch (design 17 §9.5): the group's previous or next member in
     * declaration order, wrapping, client-side and payload-free. A member the catalogue left out is
     * not switchable and answers with the one client message.
     */
    private void switchVariant(int step) {
        VariantSummary group = entry.variants();
        if (group == null || group.members().size() < 2) {
            return;
        }
        int index = group.indexOf(blueprintId);
        if (index < 0) {
            return;
        }
        List<VariantSummary.Member> members = group.members();
        VariantSummary.Member next = members.get(Math.floorMod(index + step, members.size()));
        Entry updated = find(next.id());
        if (updated == null) {
            say(Minecraft.getInstance(), "no preview page for " + next.id().text());
            return;
        }
        applyEntry(updated);
    }

    /** 15 §12.2's line for the page's member, or null for a standalone page (design 17 §9.5). */
    public @Nullable String variantLine() {
        VariantSummary group = entry.variants();
        if (group == null || group.members().size() < 2) {
            return null;
        }
        int index = group.indexOf(blueprintId);
        if (index < 0) {
            return null;
        }
        VariantSummary.Member member = group.members().get(index);
        return "variant " + (index + 1) + "/" + group.members().size() + ": " + member.text();
    }

    /** The group's declared member count, one for a standalone page (design 17 §9.5). */
    public int variantCount() {
        VariantSummary group = entry.variants();
        return group == null ? 1 : group.members().size();
    }

    /** The page's member index in its group, or -1 for a standalone page. */
    public int variantIndex() {
        VariantSummary group = entry.variants();
        return group == null ? -1 : group.indexOf(blueprintId);
    }

    private static void say(Minecraft minecraft, String line) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(line), false);
        }
    }

    /**
     * The screen's one world action (design 17 §9.4): the existing open payload plus the camera's
     * heading, and the screen closes on the click because the ghosts are what the click promised.
     */
    private void ghostToWorld() {
        PacketDistributor.sendToServer(new PreviewOpenPayload(blueprintId, heading()));
        Minecraft.getInstance().setScreen(null);
    }

    /** The heading this page's camera looks along; the four horizons only (design 17 §3.2, §9.4). */
    public Heading heading() {
        return headingOf(currentCamera().yaw());
    }

    /**
     * The cardinal reading of one turntable yaw: the four horizons each own 45° either side, and an
     * exact 45° boundary takes the earlier value in {@code Heading}'s declaration order, so the same
     * camera always sends the same heading (design 17 §9.4).
     */
    static Heading headingOf(float yaw) {
        float wrapped = yaw % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        Heading best = Heading.north;
        float bestDistance = Float.MAX_VALUE;
        for (Heading candidate : horizons) {
            float distance = Math.abs(signedDistance(wrapped, bearing(candidate)));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static float bearing(Heading heading) {
        return switch (heading) {
            case north -> 0.0F;
            case west -> 90.0F;
            case south -> 180.0F;
            case east -> 270.0F;
            case up, down -> 0.0F;
        };
    }

    private static float signedDistance(float from, float to) {
        float delta = (to - from) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        } else if (delta <= -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    /** The shared camera value, as the frame samples it; the four panels derive their own from it. */
    public SceneCamera camera() {
        return currentCamera();
    }

    /** The current layout; the four viewport rectangles are recomputed from the screen size. */
    public ScreenLayout layout() {
        return layout;
    }

    /** The page's scene: one resolved cell list for all four panels. */
    public PreviewScene scene() {
        return scene;
    }

    /** The camera one panel draws through, indexed main, front, side, top (design 17 §5.2). */
    public @Nullable SceneCamera panelCamera(int panel) {
        Panel[] panels = Panel.values();
        return panel < 0 || panel >= panels.length ? null : cameraFor(currentCamera(), panels[panel]);
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

    /** The stage playback: whole view or staged, the cursor and the reveal counts (design 17 §7). */
    public StagePlayback playback() {
        return playback;
    }

    /** True when the staged view is entered, false in the whole view (design 17 §7.1). */
    public boolean staged() {
        return playback.cursor() >= 0;
    }

    /** The cell's alpha factor over the scene's base alpha (design 17 §7.2). */
    public float alphaOf(GhostCell ghost) {
        StagePlayback.Placed placedCell = placed.get(ghost);
        return placedCell == null ? 1.0F : playback.alpha(placedCell);
    }

    /** True when the stage treatment draws the cell at all (design 17 §7.2). */
    public boolean drawn(GhostCell ghost) {
        StagePlayback.Placed placedCell = placed.get(ghost);
        return placedCell == null || playback.drawn(placedCell);
    }

    /** The exploded view's model: its groups, its distance and its labels (design 17 §8). */
    public ExplodeModel explode() {
        return explode;
    }

    /** The explode animation's current `t` (design 17 §8.1). */
    public float explodeT() {
        return explodeT;
    }

    /** True when a second bay exists to separate; otherwise the button reports `one part` (§8.1). */
    public boolean canExplode() {
        return explode.separable();
    }

    /** The future cells' treatment: false is hidden, true is the 1-px outline (design 17 §7.2). */
    public boolean outlineMode() {
        return outline;
    }

    /** The next-step line under the stage bar, or null in the whole view (design 17 §7.4). */
    public @Nullable String summaryLine() {
        return summary;
    }

    /** The stage bar as last drawn (design 17 §7.3). */
    public StageBar stageBar() {
        return stageBar;
    }

    /** True while a stage-framing leg is in flight (design 17 §7.5). */
    public boolean framingActive() {
        return framing != null;
    }

    /** False once a manual camera gesture disarmed framing for this play-through (design 17 §7.5). */
    public boolean framingArmed() {
        return framingArmed;
    }

    /** The number of part labels drawn in the last frame, summed over the four panels (§8.2). */
    public int drawnLabels() {
        return drawnLabels;
    }

    /** The number of future-cell outline boxes drawn in the last frame (§7.2). */
    public int drawnFutureBoxes() {
        return drawnFutureBoxes;
    }

    /**
     * The shading one panel installed in the last frame, or null before its first draw: the six world
     * faces in {@code Direction}'s order. Panels are indexed main, front, side, top (design 17 §4.1).
     */
    public @Nullable Shading shading(int panel) {
        Panel[] panels = Panel.values();
        return panel < 0 || panel >= panels.length ? null : shading.get(panels[panel]);
    }

    /** The hovered cell's chrome outline as one panel drew it last frame, or null when it drew none. */
    public @Nullable CellOutline hoverOutline(int panel) {
        Panel[] panels = Panel.values();
        return panel < 0 || panel >= panels.length ? null : outlines.get(panels[panel]);
    }

    /** The pop-in scale of one cell as of the last frame; 1.0 when it is not popping (§7.6). */
    public float scaleOf(GhostCell ghost) {
        return popIn.scale(ghost, clock.time(), popScaleLeg);
    }

    /** How many drawn cells were inside their pop-in in the last frame (§7.6). */
    public int animatingCells() {
        return animatingCells;
    }

    /** The rectangle one control-strip button occupies, or null when the label is not on the strip. */
    public @Nullable Rect buttonRect(String label) {
        for (Button button : buttons()) {
            if (button.label().equals(label)) {
                return button.rect();
            }
        }
        return null;
    }

    /** The control strip's button labels in layout order, for the §4.5 one-list-read-twice check. */
    public List<String> buttonLabels() {
        List<String> labels = new ArrayList<>();
        for (Button button : buttons()) {
            labels.add(button.label());
        }
        return List.copyOf(labels);
    }

    /** One control-strip button: its rectangle, its face text and the tooltip that names its key. */
    private record Button(Rect rect, String label, String tooltip, Runnable action) {

        boolean inside(double mouseX, double mouseY) {
            return PreviewScreen.inside(rect, mouseX, mouseY);
        }
    }

    private record ButtonSpec(String label, String tooltip, Runnable action) {}
}
