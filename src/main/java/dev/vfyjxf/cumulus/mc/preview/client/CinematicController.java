package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.ClientConfig;
import dev.vfyjxf.cumulus.mc.preview.client.cine.CameraProgram;
import dev.vfyjxf.cumulus.mc.preview.client.cine.CameraShot;
import dev.vfyjxf.cumulus.mc.preview.client.cine.CinematicPrograms;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Ease;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The cinema mode: C detaches the frame from the player, the camera flies the program of
 * {@link CinematicPrograms}, and the first sign of the viewer's own input hands the frame back.
 * It is a pure client viewing mode — no world write, no server state, no camera the player cannot
 * reclaim (design 16 §6.7, D-100).
 */
public final class CinematicController {

    /** The hand-back's span: a quarter of a second, the design's single EASE_OUT_QUAD leg. */
    static final int handBackTicks = 5;
    static final int letterboxFadeTicks = 8;
    /** The framing law's fov is fixed, so the program is a function of the payload, not of a setting. */
    private static final double targetDistance = 16.0D;

    private static boolean active;
    private static @Nullable CameraProgram program;
    private static int programTicks;
    private static boolean opening;
    private static boolean closing;
    private static int focusedStage = Integer.MIN_VALUE;
    private static boolean lastPlaying;
    private static @Nullable CameraShot sampledShot;
    private static @Nullable CameraProgram handBack;
    private static int handBackTicksRun;
    private static int letterboxTicks;
    private static @Nullable Entity rig;
    private static @Nullable Entity previousCameraEntity;
    private static @Nullable CameraType previousCameraType;
    private static @Nullable Cuboid overallBounds;
    private static @Nullable Vec3 approach;
    private static List<Cuboid> stageBounds = List.of();
    private static float entryYaw;
    private static float entryPitch;

    private CinematicController() {}

    public static boolean active() {
        return active;
    }

    /** True while the camera is flying back to the player after the user took the keys. */
    public static boolean fastReturning() {
        return active && handBack != null;
    }

    /** True while the closing pull-back plays after the playback ran out of stages. */
    public static boolean closing() {
        return active && closing;
    }

    /** The black bars' current opacity; zero when the mode is off or the config turned them off. */
    public static float letterboxAlpha() {
        if (!active || !ClientConfig.cinematicsLetterbox()) {
            return 0.0F;
        }
        double fade = Math.min(1.0D, letterboxTicks / (double) letterboxFadeTicks);
        if (handBack != null) {
            fade *= Math.max(0.0D, 1.0D - handBackTicksRun / (double) handBackTicks);
        }
        return (float) fade;
    }

    /** The program's whole-tick cursor; the render samples it plus the frame's partial tick. */
    public static int programTicks() {
        return programTicks;
    }

    /** The stage the movie is framing, or {@link Integer#MIN_VALUE} before the opening orbit ends. */
    public static int focusedStage() {
        return focusedStage;
    }

    /** The shot the last camera frame applied; null before the first frame of a mode. */
    public static @Nullable CameraShot sampledShot() {
        return sampledShot;
    }

    /** C: in when off, a hand-back when on. No session open means no mode. */
    static void toggle() {
        if (active) {
            requestHandBack();
        } else {
            enter();
        }
    }

    static void enter() {
        if (active || !ClientConfig.cinematicsEnabled()) {
            return;
        }
        PreviewSyncPayload payload = PreviewClientState.session();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (payload == null || player == null || minecraft.level == null) {
            return;
        }
        Cuboid bounds = overallBounds(payload);
        if (bounds == null) {
            return;
        }
        GuideController.disarm();
        previousCameraEntity = minecraft.getCameraEntity();
        previousCameraType = minecraft.options.getCameraType();
        entryYaw = player.getYRot();
        entryPitch = player.getXRot();
        if (rig == null) {
            // a player-type anchor for its 1.62 eye height; it never joins a level and never ticks
            rig = new Anchor(EntityType.PLAYER, minecraft.level);
        }
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(rig);
        active = true;
        overallBounds = bounds;
        approach = entryApproach(player, bounds);
        stageBounds = stageBounds(payload, bounds);
        program = CinematicPrograms.opening(bounds, approach);
        programTicks = 0;
        opening = true;
        closing = false;
        focusedStage = PlaybackController.selected();
        lastPlaying = PlaybackController.playing();
        handBack = null;
        handBackTicksRun = 0;
        letterboxTicks = 0;
        sampledShot = null;
    }

    /** The session's facts changed under a running mode: keep framing the current stage's new geometry. */
    static void onPayload(PreviewSyncPayload payload) {
        if (!active) {
            return;
        }
        Cuboid bounds = overallBounds(payload);
        if (bounds == null) {
            leave();
            return;
        }
        overallBounds = bounds;
        stageBounds = stageBounds(payload, bounds);
    }

    /** The session closed under the mode: the frame belongs to the player again. */
    static void onSessionClosed() {
        if (active) {
            leave();
        }
    }

    /** G arms the guide: the mode leaves at once, because guiding is a hand and watching is an eye. */
    static void leaveForGuide() {
        if (active) {
            leave();
        }
    }

    /** One client tick: the program's clock, the exits and the playback coupling. */
    static void tick() {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!ClientConfig.cinematicsEnabled()
                || PreviewClientState.session() == null
                || player == null
                || minecraft.level == null) {
            leave();
            return;
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            leave();
            return;
        }
        if (player.getYRot() != entryYaw || player.getXRot() != entryPitch) {
            // the mouse moved; the frame is already the viewer's again
            leave();
            return;
        }
        if (handBack != null) {
            handBackTicksRun++;
            if (handBackTicksRun >= handBackTicks) {
                leave();
            }
            return;
        }
        if (movementHeld()) {
            requestHandBack();
            return;
        }
        int selected = PlaybackController.selected();
        boolean playing = PlaybackController.playing();
        if (opening) {
            if (selected != focusedStage || program != null && programTicks >= program.totalTicks()) {
                focus(selected);
            }
        } else if (selected != focusedStage) {
            focus(selected);
        } else if (lastPlaying && !playing && atEnd()) {
            closeMovie();
        }
        lastPlaying = playing;
        programTicks++;
        if (letterboxTicks < letterboxFadeTicks) {
            letterboxTicks++;
        }
    }

    /** The camera event: move the anchor and turn the frame to the program's sample of this frame. */
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active || program == null) {
            return;
        }
        CameraShot shot = handBack != null
                ? handBack.at(handBackTicksRun + event.getPartialTick())
                : program.at((programTicks + event.getPartialTick()) * speed());
        placeAnchor(shot);
        event.setYaw(shot.yaw());
        event.setPitch(shot.pitch());
        event.setRoll(0.0F);
        sampledShot = shot;
    }

    /** The movement is the player's; the camera mode zeroes it rather than moving the body. */
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active || event.getEntity() != Minecraft.getInstance().player) {
            return;
        }
        event.getInput().leftImpulse = 0.0F;
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().up = false;
        event.getInput().down = false;
        event.getInput().left = false;
        event.getInput().right = false;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    /** A click or a use would reach the world from the shot's crosshair; neither may fire here. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
    }

    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!active || Minecraft.getInstance().screen != null) {
            return;
        }
        event.setCanceled(true);
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            requestHandBack();
        }
    }

    static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (isCinematicKey(event.getKey())) {
            if (claimsKey()) {
                claimVanillaKey(event.getKey());
            }
            return;
        }
        if (active && isMovementKey(event.getKey())) {
            requestHandBack();
        }
    }

    /**
     * Esc is the game's pause key, so it is not claimed: the pause screen opens and the frame is the
     * player's at once. A cut, not the quarter-second hand-back — a paused game stops the tick clock
     * the hand-back flies on (KB 03). Any other screen is the same kind of taking over.
     */
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (active) {
            leave();
        }
    }

    /** The player's own body has no place in a shot the camera does not sit in. */
    static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (active && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    private static void requestHandBack() {
        if (!active || handBack != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        CameraShot from = currentShot();
        if (player == null || from == null) {
            leave();
            return;
        }
        CameraShot to = playerShot(player);
        handBack = new CameraProgram(
            List.of(
                new CameraProgram.Leg(from, 0, 0, Ease.linear),
                new CameraProgram.Leg(to, 0, handBackTicks, Ease.outQuad)
            )
        );
        handBackTicksRun = 0;
    }

    private static void focus(int stage) {
        CameraShot from = currentShot();
        if (from == null) {
            leave();
            return;
        }
        Cuboid bounds = stage >= 0 && stage < stageBounds.size() ? stageBounds.get(stage) : overallBounds;
        if (bounds == null || approach == null) {
            leave();
            return;
        }
        program = CinematicPrograms.focus(from, bounds, approach);
        programTicks = 0;
        opening = false;
        closing = false;
        focusedStage = stage;
        lastPlaying = PlaybackController.playing();
    }

    private static void closeMovie() {
        CameraShot from = currentShot();
        if (from == null || overallBounds == null || approach == null) {
            return;
        }
        program = CinematicPrograms.closing(from, overallBounds, approach);
        programTicks = 0;
        closing = true;
        focusedStage = PlaybackController.selected();
    }

    /** The shot the flight starts from: the frame just drawn, so a program swap never cuts. */
    private static @Nullable CameraShot currentShot() {
        if (sampledShot != null) {
            return sampledShot;
        }
        return program == null ? null : program.at(programTicks * speed());
    }

    /** The hand-back's target: the player's own eye and the direction the player is looking. */
    private static CameraShot playerShot(LocalPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());
        double cos = Math.cos(pitch);
        Vec3 direction = new Vec3(-Math.sin(yaw) * cos, -Math.sin(pitch), Math.cos(yaw) * cos);
        return new CameraShot(eye, eye.add(direction.scale(targetDistance)));
    }

    private static void placeAnchor(CameraShot shot) {
        Entity anchor = rig;
        if (anchor == null) {
            return;
        }
        anchor.setPos(shot.eye().x, shot.eye().y - anchor.getEyeHeight(), shot.eye().z);
        anchor.setYRot(shot.yaw());
        anchor.setXRot(shot.pitch());
        // the camera lerps the anchor's own old/current fields; equal values make the sample exact
        anchor.setOldPosAndRot();
    }

    private static void leave() {
        if (active) {
            Minecraft minecraft = Minecraft.getInstance();
            // a player who pressed F5 during the mode keeps the view they picked; the mode only undoes
            // the first-person it forced
            if (previousCameraType != null && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
                minecraft.options.setCameraType(previousCameraType);
            }
            Entity restore = previousCameraEntity;
            minecraft.setCameraEntity(restore == null ? minecraft.player : restore);
        }
        active = false;
        program = null;
        handBack = null;
        programTicks = 0;
        handBackTicksRun = 0;
        opening = false;
        closing = false;
        focusedStage = Integer.MIN_VALUE;
        lastPlaying = false;
        sampledShot = null;
        letterboxTicks = 0;
        previousCameraEntity = null;
        previousCameraType = null;
        overallBounds = null;
        approach = null;
        stageBounds = List.of();
        entryYaw = 0.0F;
        entryPitch = 0.0F;
        rig = null;
    }

    private static boolean atEnd() {
        int last = PlaybackController.stageCount() - 1;
        if (last < 0) {
            return true;
        }
        return PlaybackController.selected() == last
                && PlaybackController.revealed(last) >= PlaybackController.ghostCount(last);
    }

    /** The mode is about to take the press: an open session is enough, a running mode always. */
    private static boolean claimsKey() {
        return active || ClientConfig.cinematicsEnabled() && PreviewClientState.session() != null;
    }

    /**
     * C is the mode's key and vanilla's creative hotbar-save: a press the mode claims must not also
     * save a toolbar, so every other binding on that key has its queued click dropped. The mode wins
     * the press; the vanilla control keeps its own key for every press the mode does not claim.
     */
    private static void claimVanillaKey(int key) {
        String previewCategory = "key.categories.structurecumulus.preview";
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (previewCategory.equals(mapping.getCategory())
                    || mapping.getKey().getType() != InputConstants.Type.KEYSYM
                    || mapping.getKey().getValue() != key) {
                continue;
            }
            mapping.consumeClick();
        }
    }

    private static boolean isCinematicKey(int key) {
        KeyMapping mapping = PreviewKeys.binding(PreviewKeys.Action.cinematic);
        return mapping != null && sameKey(mapping, key);
    }

    private static boolean movementHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.keyUp.isDown()
                || minecraft.options.keyDown.isDown()
                || minecraft.options.keyLeft.isDown()
                || minecraft.options.keyRight.isDown()
                || minecraft.options.keyJump.isDown()
                || minecraft.options.keyShift.isDown();
    }

    private static boolean isMovementKey(int key) {
        Minecraft minecraft = Minecraft.getInstance();
        return sameKey(minecraft.options.keyUp, key)
                || sameKey(minecraft.options.keyDown, key)
                || sameKey(minecraft.options.keyLeft, key)
                || sameKey(minecraft.options.keyRight, key)
                || sameKey(minecraft.options.keyJump, key)
                || sameKey(minecraft.options.keyShift, key);
    }

    private static boolean sameKey(KeyMapping mapping, int key) {
        return mapping.getKey().getType() == InputConstants.Type.KEYSYM && mapping.getKey().getValue() == key;
    }

    private static double speed() {
        double speed = ClientConfig.cinematicsSpeed();
        return speed <= 0.0D ? 1.0D : speed;
    }

    private static Vec3 entryApproach(LocalPlayer player, Cuboid bounds) {
        Vec3 centre = CinematicPrograms.centre(bounds);
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 flat = new Vec3(eye.x - centre.x, 0.0D, eye.z - centre.z);
        return flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
    }

    private static @Nullable Cuboid overallBounds(PreviewSyncPayload payload) {
        Cuboid bounds = payload.overlay().bounds();
        if (!bounds.isEmpty()) {
            return bounds;
        }
        List<LatticePoint> cells = new ArrayList<>(payload.ghostCells().size());
        for (GhostCell ghost : payload.ghostCells()) {
            cells.add(ghost.cell());
        }
        if (cells.isEmpty()) {
            return null;
        }
        return CinematicPrograms.hull(cells, Cuboid.of(cells.get(0)));
    }

    /** The camera's anchor: an entity that only ever answers where the eye is, never ticked or drawn. */
    private static final class Anchor extends Entity {

        private Anchor(EntityType<?> type, Level level) {
            super(type, level);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {}

        @Override
        protected void readAdditionalSaveData(CompoundTag compound) {}

        @Override
        protected void addAdditionalSaveData(CompoundTag compound) {}
    }

    private static List<Cuboid> stageBounds(PreviewSyncPayload payload, Cuboid fallback) {
        int count = payload.presentation().stages().size();
        if (count == 0) {
            return List.of();
        }
        List<List<LatticePoint>> cells = new ArrayList<>(count);
        for (int stage = 0; stage < count; stage++) {
            cells.add(new ArrayList<>());
        }
        for (GhostCell ghost : payload.ghostCells()) {
            if (ghost.stageIndex() >= 0 && ghost.stageIndex() < count) {
                cells.get(ghost.stageIndex()).add(ghost.cell());
            }
        }
        List<Cuboid> bounds = new ArrayList<>(count);
        for (List<LatticePoint> stage : cells) {
            bounds.add(CinematicPrograms.hull(stage, fallback));
        }
        return List.copyOf(bounds);
    }
}
