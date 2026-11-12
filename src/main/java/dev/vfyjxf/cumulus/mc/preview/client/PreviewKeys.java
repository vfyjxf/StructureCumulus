package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/** The preview's key bindings: every control is a real {@link KeyMapping} in the controls screen. */
public final class PreviewKeys {

    /** One bindable preview action. */
    public enum Action {
        turnCw, turnCcw, flip, prevOrigin, nextOrigin, reset, recall, sliceUp, sliceDown, toggleProblems, confirm, cancel, prevStage, nextStage, playPause, stepCell, guide, nextVariant, savePreset, cinematic
    }

    private static final String category = "key.categories.structurecumulus.preview";
    private static final Map<Action, KeyMapping> bindings = new EnumMap<>(Action.class);

    private PreviewKeys() {}

    /** The binding of one action, or null before the client registered the keys. */
    public static @Nullable KeyMapping binding(Action action) {
        return bindings.get(action);
    }

    static void register(RegisterKeyMappingsEvent event) {
        bind(Action.turnCw, "key.structurecumulus.preview.turn_cw", GLFW.GLFW_KEY_RIGHT_BRACKET, event);
        bind(Action.turnCcw, "key.structurecumulus.preview.turn_ccw", GLFW.GLFW_KEY_LEFT_BRACKET, event);
        bind(Action.flip, "key.structurecumulus.preview.flip", GLFW.GLFW_KEY_SEMICOLON, event);
        bind(Action.prevOrigin, "key.structurecumulus.preview.prev_origin", GLFW.GLFW_KEY_COMMA, event);
        bind(Action.nextOrigin, "key.structurecumulus.preview.next_origin", GLFW.GLFW_KEY_PERIOD, event);
        bind(Action.reset, "key.structurecumulus.preview.reset", GLFW.GLFW_KEY_R, event);
        bind(Action.recall, "key.structurecumulus.preview.recall", GLFW.GLFW_KEY_P, event);
        bind(Action.sliceUp, "key.structurecumulus.preview.slice_up", GLFW.GLFW_KEY_PAGE_UP, event);
        bind(Action.sliceDown, "key.structurecumulus.preview.slice_down", GLFW.GLFW_KEY_PAGE_DOWN, event);
        bind(Action.toggleProblems, "key.structurecumulus.preview.problems", GLFW.GLFW_KEY_T, event);
        bind(Action.confirm, "key.structurecumulus.preview.confirm", GLFW.GLFW_KEY_ENTER, event);
        bind(Action.cancel, "key.structurecumulus.preview.cancel", GLFW.GLFW_KEY_ESCAPE, event);
        bind(Action.prevStage, "key.structurecumulus.preview.prev_stage", GLFW.GLFW_KEY_UP, event);
        bind(Action.nextStage, "key.structurecumulus.preview.next_stage", GLFW.GLFW_KEY_DOWN, event);
        bind(Action.playPause, "key.structurecumulus.preview.play_pause", GLFW.GLFW_KEY_V, event);
        bind(Action.stepCell, "key.structurecumulus.preview.step_cell", GLFW.GLFW_KEY_B, event);
        bind(Action.guide, "key.structurecumulus.preview.guide", GLFW.GLFW_KEY_G, event);
        bind(Action.nextVariant, "key.structurecumulus.preview.next_variant", GLFW.GLFW_KEY_N, event);
        bind(Action.savePreset, "key.structurecumulus.preview.save_preset", GLFW.GLFW_KEY_K, event);
        bind(Action.cinematic, "key.structurecumulus.preview.cinematic", GLFW.GLFW_KEY_C, event);
    }

    private static void bind(Action action, String description, int code, RegisterKeyMappingsEvent event) {
        KeyMapping mapping = new KeyMapping(description, InputConstants.Type.KEYSYM, code, category);
        bindings.put(action, mapping);
        event.register(mapping);
    }
}
