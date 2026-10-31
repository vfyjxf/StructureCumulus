package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The dev fixture's current part model, read by the HUD, highlight layer and inspect tool. */
public final class VisualPartState {

    private static volatile List<VisualPart> parts = List.of();

    private VisualPartState() {}

    static void set(List<VisualPart> next) {
        parts = List.copyOf(next);
    }

    static void clear() {
        parts = List.of();
    }

    public static List<VisualPart> parts() {
        return parts;
    }

    public static @Nullable VisualPart part(InstanceId id) {
        for (VisualPart part : parts) {
            if (part.instance().equals(id)) {
                return part;
            }
        }
        return null;
    }

    public static @Nullable VisualPart partAt(LatticePoint cell) {
        for (VisualPart part : parts) {
            if (part.bounds().contains(cell)) {
                return part;
            }
        }
        return null;
    }

    static int distinctRoleCount() {
        Set<VisualPartRole> roles = new LinkedHashSet<>();
        for (VisualPart part : parts) {
            roles.add(part.role());
        }
        return roles.size();
    }

    static List<String> legendLines() {
        List<String> lines = new ArrayList<>();
        Set<VisualPartRole> roles = new LinkedHashSet<>();
        for (VisualPart part : parts) {
            roles.add(part.role());
        }
        for (VisualPartRole role : VisualPartRole.values()) {
            if (roles.contains(role)) {
                lines.add("部件 " + role.displayName() + " " + colorText(role.color()) + ": " + role.description());
            }
        }
        return List.copyOf(lines);
    }

    static String colorText(int color) {
        return String.format("#%08X", color);
    }
}
