package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.GuideController;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewClientState;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only view of the guided build's client state, for the guided scenario. */
final class PreviewGuideProbe {

    /** The vanilla control categories; a preview key may not shadow a control in one of them. */
    private static final Set<String> vanillaCategories = Set.of(
        "key.categories.movement",
        "key.categories.misc",
        "key.categories.multiplayer",
        "key.categories.gameplay",
        "key.categories.ui",
        "key.categories.inventory",
        "key.categories.creative"
    );

    private PreviewGuideProbe() {}

    static boolean guided() {
        return GuideController.guided();
    }

    static @Nullable String nextStep() {
        return GuideController.nextStep();
    }

    static List<LatticePoint> nextGroup() {
        return GuideController.nextGroup();
    }

    static List<LatticePoint> pulsing() {
        return GuideController.pulsingCells();
    }

    static float pulseFactor(LatticePoint cell) {
        return GuideController.pulseFactor(cell);
    }

    static boolean complete() {
        return GuideController.complete();
    }

    static @Nullable String hover() {
        return PreviewClientState.hoverTooltip();
    }

    /** True when the guide's one line is drawn by the HUD, i.e. the line exists and the mode is on. */
    static boolean hudHasGuide() {
        return PreviewProbe.hudHas("guide: ");
    }

    /**
     * Every preview action owns one default key, and `G` shadows no vanilla control: the combination
     * the design claims, asserted instead of trusted (design 16 §6.3, roadmap §5).
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
        KeyMapping guide = PreviewKeys.binding(PreviewKeys.Action.guide);
        if (guide == null) {
            return "the guide has no binding";
        }
        String key = guide.getDefaultKey().getName();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping == guide || !vanillaCategories.contains(mapping.getCategory())) {
                continue;
            }
            if (mapping.getDefaultKey().getName().equals(key)) {
                return "the guide's " + key + " shadows " + mapping.getName();
            }
        }
        return null;
    }
}
