package dev.vfyjxf.cumulus.uitest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.mc.preview.client.CinematicController;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import dev.vfyjxf.cumulus.mc.preview.client.TransitionController;
import dev.vfyjxf.cumulus.mc.preview.client.cine.CameraShot;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only view of the cinema mode and its hand-off animations, for the cinematic scenario. */
final class PreviewCinematicProbe {

    /** The vanilla control categories; the cinematic key may not shadow a control in one of them. */
    private static final Set<String> vanillaCategories = Set.of(
        "key.categories.movement",
        "key.categories.misc",
        "key.categories.multiplayer",
        "key.categories.gameplay",
        "key.categories.ui",
        "key.categories.inventory",
        "key.categories.creative"
    );

    private PreviewCinematicProbe() {}

    static boolean active() {
        return CinematicController.active();
    }

    static boolean fastReturning() {
        return CinematicController.fastReturning();
    }

    static boolean closing() {
        return CinematicController.closing();
    }

    static float letterboxAlpha() {
        return CinematicController.letterboxAlpha();
    }

    static int programTicks() {
        return CinematicController.programTicks();
    }

    static int focusedStage() {
        return CinematicController.focusedStage();
    }

    static @Nullable CameraShot sampledShot() {
        return CinematicController.sampledShot();
    }

    static @Nullable Entity cameraEntity() {
        return Minecraft.getInstance().getCameraEntity();
    }

    static @Nullable Entity mainCameraEntity() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getEntity();
    }

    static Vec3 cameraPosition() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    static float cameraYaw() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
    }

    static float cameraPitch() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getXRot();
    }

    static CameraType cameraType() {
        return Minecraft.getInstance().options.getCameraType();
    }

    /** The last camera frame sits on the program's sample of it: the event really moved the frame. */
    static boolean cameraOnShot() {
        CameraShot shot = sampledShot();
        return shot != null && cameraPosition().distanceTo(shot.eye()) < 0.05D;
    }

    static boolean cameraOnShotAngles() {
        CameraShot shot = sampledShot();
        if (shot == null) {
            return false;
        }
        return Math.abs(wrapDegrees(cameraYaw() - shot.yaw())) < 1.0E-3D
                && Math.abs(cameraPitch() - shot.pitch()) < 1.0E-3D;
    }

    /** The shot's target is the payload's whole-subject centre; the orbit and the pull-back look there. */
    static boolean shotTargetsOverallCentre() {
        CameraShot shot = sampledShot();
        Vec3 centre = overallCentre();
        return shot != null && centre != null && shot.target().distanceTo(centre) < 1.0E-6D;
    }

    static @Nullable Vec3 overallCentre() {
        PreviewSyncPayload session = PreviewProbe.session();
        if (session == null) {
            return null;
        }
        return centre(session.overlay().bounds());
    }

    /** The centre of one stage's ghost hull, computed test-side from the payload's cells. */
    static @Nullable Vec3 stageCentre(int stage) {
        List<GhostCell> ghosts = PreviewStageProbe.ghosts(stage);
        if (ghosts.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (GhostCell ghost : ghosts) {
            minX = Math.min(minX, ghost.cell().x());
            minY = Math.min(minY, ghost.cell().y());
            minZ = Math.min(minZ, ghost.cell().z());
            maxX = Math.max(maxX, ghost.cell().x() + 1);
            maxY = Math.max(maxY, ghost.cell().y() + 1);
            maxZ = Math.max(maxZ, ghost.cell().z() + 1);
        }
        return new Vec3((minX + maxX) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ) / 2.0D);
    }

    /** True when the shot's eye stands at the fit distance on the entry side of the subject. */
    static boolean shotTargetsStage(int stage) {
        CameraShot shot = sampledShot();
        Vec3 centre = stageCentre(stage);
        return shot != null && centre != null && shot.target().distanceTo(centre) < 1.0E-6D;
    }

    static double eyeDistanceToOverallCentre() {
        Vec3 centre = overallCentre();
        return centre == null ? -1.0D : cameraPosition().distanceTo(centre);
    }

    static float playerYaw() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0.0F : player.getYRot();
    }

    static float playerPitch() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0.0F : player.getXRot();
    }

    static Vec3 playerEye() {
        var player = Minecraft.getInstance().player;
        return player == null ? Vec3.ZERO : player.getEyePosition(1.0F);
    }

    /** After the hand-back the camera is the player again: same entity, eye and view direction. */
    static boolean cameraReturnedToPlayer() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        Entity camera = cameraEntity();
        return camera == player
                && mainCameraEntity() == player
                && cameraPosition().distanceTo(playerEye()) < 0.05D
                && Math.abs(wrapDegrees(cameraYaw() - player.getYRot())) < 1.0E-3D
                && Math.abs(cameraPitch() - player.getXRot()) < 1.0E-3D;
    }

    static boolean turntableActive() {
        return TransitionController.turntableActive();
    }

    static int turntableTicks() {
        return TransitionController.turntableTicks();
    }

    static int turntableDurationTicks() {
        return TransitionController.turntableDurationTicks();
    }

    static double turntableAngleDeg() {
        return TransitionController.turntableAngleDeg();
    }

    static @Nullable Turn turntableModelTurn() {
        return TransitionController.turntableModelTurn();
    }

    static boolean stageAdvanceActive() {
        return TransitionController.stageAdvanceActive();
    }

    static int stageAdvanceTicks() {
        return TransitionController.stageAdvanceTicks();
    }

    static int stageAdvanceDurationTicks() {
        return TransitionController.stageAdvanceDurationTicks();
    }

    static float ghostAlpha(GhostCell ghost) {
        return TransitionController.ghostAlpha(ghost, 0.0F);
    }

    static float ghostScale(GhostCell ghost) {
        return TransitionController.ghostScale(ghost, 0.0F);
    }

    static boolean sessionFadeActive() {
        return TransitionController.sessionFadeActive();
    }

    static int sessionFadeTicks() {
        return TransitionController.sessionFadeTicks();
    }

    static int cinematicKey() {
        KeyMapping mapping = PreviewKeys.binding(PreviewKeys.Action.cinematic);
        return mapping == null ? InputConstants.UNKNOWN.getValue() : mapping.getDefaultKey().getValue();
    }

    /**
     * Every action owns one default key, and the cinema key shadows no vanilla control except the
     * creative hotbar save — the one collision vanilla has on `C`, which the mode claims on its press.
     */
    static @Nullable String keyConflict() {
        Map<PreviewKeys.Action, String> bound = new EnumMap<>(PreviewKeys.Action.class);
        Set<String> seen = new HashSet<>();
        for (PreviewKeys.Action action : PreviewKeys.Action.values()) {
            KeyMapping mapping = PreviewKeys.binding(action);
            if (mapping == null) {
                return "unbound action: " + action;
            }
            String key = mapping.getDefaultKey().getName();
            bound.put(action, key);
            if (!seen.add(key)) {
                return "two actions share " + key + ": " + bound.values();
            }
        }
        KeyMapping cinematic = PreviewKeys.binding(PreviewKeys.Action.cinematic);
        if (cinematic == null) {
            return "the cinema mode has no binding";
        }
        String key = cinematic.getDefaultKey().getName();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping == cinematic
                    || mapping == Minecraft.getInstance().options.keySaveHotbarActivator
                    || !vanillaCategories.contains(mapping.getCategory())) {
                continue;
            }
            if (mapping.getDefaultKey().getName().equals(key)) {
                return "the cinema mode's " + key + " shadows " + mapping.getName();
            }
        }
        return null;
    }

    /** True when a press the mode claimed still left the shadowed creative hotbar save queued. */
    static boolean toolbarSavePending() {
        return Minecraft.getInstance().options.keySaveHotbarActivator.consumeClick();
    }

    private static Vec3 centre(Cuboid bounds) {
        return new Vec3(
            (bounds.min().x() + bounds.max().x()) / 2.0D,
            (bounds.min().y() + bounds.max().y()) / 2.0D,
            (bounds.min().z() + bounds.max().z()) / 2.0D
        );
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }
}
