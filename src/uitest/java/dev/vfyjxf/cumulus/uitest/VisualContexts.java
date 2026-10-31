package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The dev fixtures' real staged contexts; the values are also mirrored into the client HUD store. */
final class VisualContexts {

    static final ContextKey<String> groupKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_group"), String.class, Retention.instance);
    static final ContextKey<String> systemKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_system"), String.class, Retention.instance);
    static final ContextKey<String> roleKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_role"), String.class, Retention.instance);
    static final ContextKey<String> setRoleKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_set_role"), String.class, Retention.instance);
    static final ContextKey<Integer> budgetKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_budget"), Integer.class, Retention.instance);
    static final ContextKey<Integer> spendKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_spend"), Integer.class, Retention.instance);
    static final ContextKey<String> captureKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_capture"), String.class, Retention.instance);
    static final ContextKey<Integer> nestedDepthKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_nested_depth"), Integer.class, Retention.instance);
    static final ContextKey<String> noteKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_note"), String.class, Retention.instance);

    static final ContextKey<String> dimensionsKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_dimensions"), String.class, Retention.instance);
    static final ContextKey<Integer> interiorVolumeKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_interior_volume"), Integer.class, Retention.instance);
    static final ContextKey<Integer> wallCountKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_wall_count"), Integer.class, Retention.instance);
    static final ContextKey<Integer> drainCountKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_drain_count"), Integer.class, Retention.instance);
    static final ContextKey<Integer> tankCapacityKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_tank_capacity"), Integer.class, Retention.instance);
    static final ContextKey<Integer> tankFillKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_tank_fill"), Integer.class, Retention.instance);
    static final ContextKey<Integer> maxTemperatureKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_max_temperature"), Integer.class, Retention.instance);
    static final ContextKey<Boolean> smeltableKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_smeltable"), Boolean.class, Retention.instance);
    static final ContextKey<Boolean> alloyKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_alloy"), Boolean.class, Retention.instance);
    static final ContextKey<Integer> coilTierKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_coil_tier"), Integer.class, Retention.instance);
    static final ContextKey<String> energyTierKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_energy_tier"), String.class, Retention.instance);
    static final ContextKey<Integer> parallelsKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_parallels"), Integer.class, Retention.instance);
    static final ContextKey<Integer> mufflerCountKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_muffler_count"), Integer.class, Retention.instance);
    static final ContextKey<Boolean> maintenanceRequiredKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_maintenance_required"), Boolean.class, Retention.instance);
    static final ContextKey<String> controllerPosKey = ContextKeys
            .of(NamespacedId.of("structurecumulus", "visual_controller_pos"), String.class, Retention.instance);

    private static final Set<String> semanticKeys = Set.of(
        "structurecumulus:visual_dimensions",
        "structurecumulus:visual_interior_volume",
        "structurecumulus:visual_wall_count",
        "structurecumulus:visual_drain_count",
        "structurecumulus:visual_tank_capacity",
        "structurecumulus:visual_tank_fill",
        "structurecumulus:visual_max_temperature",
        "structurecumulus:visual_smeltable",
        "structurecumulus:visual_alloy",
        "structurecumulus:visual_coil_tier",
        "structurecumulus:visual_energy_tier",
        "structurecumulus:visual_parallels",
        "structurecumulus:visual_muffler_count",
        "structurecumulus:visual_maintenance_required",
        "structurecumulus:visual_controller_pos"
    );

    static boolean isSemanticKey(String key) {
        return semanticKeys.contains(key);
    }

    static int semanticKeyCount(VisualInstance instance) {
        int count = 0;
        for (String key : instance.contextKeys()) {
            if (isSemanticKey(key)) {
                count++;
            }
        }
        return count;
    }

    private VisualContexts() {}

    static Map<ContextKey<?>, Object> forMember(
        VisualPartRole role,
        String groupId,
        String system,
        int depth,
        int budget,
        String capture
    ) {
        Map<ContextKey<?>, Object> values = new LinkedHashMap<>();
        values.put(groupKey, groupId);
        values.put(systemKey, system);
        values.put(roleKey, role.displayName());
        values.put(setRoleKey, setRole(role));
        values.put(budgetKey, budget);
        values.put(spendKey, 1);
        values.put(captureKey, capture);
        values.put(nestedDepthKey, depth);
        values.put(noteKey, role.description());
        return Map.copyOf(values);
    }

    private static String setRole(VisualPartRole role) {
        return switch (role) {
            case controller, root -> "根节点";
            case satellite, subSatellite -> "卫星";
            case substructure -> "子结构";
            default -> "成员";
        };
    }

    static void record(StructureInstance instance, Map<ContextKey<?>, Object> values) {
        Map<String, String> text = new LinkedHashMap<>();
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            text.put(
                entry.getKey().id().text(),
                value instanceof Boolean bool ? (bool ? "是" : "否") : String.valueOf(value)
            );
        }
        VisualContextValueState.put(instance.id(), text);
    }
}
