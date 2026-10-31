package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The dev fixture's current group and hierarchy model, read by the HUD and highlight layer. */
public final class VisualGroupState {

    private static volatile List<VisualGroup> groups = List.of();
    private static volatile List<VisualHierarchyNode> hierarchy = List.of();

    private VisualGroupState() {}

    static void set(List<VisualGroup> nextGroups, List<VisualHierarchyNode> nextHierarchy) {
        groups = List.copyOf(nextGroups);
        hierarchy = List.copyOf(nextHierarchy);
    }

    static void clear() {
        groups = List.of();
        hierarchy = List.of();
    }

    public static List<VisualGroup> groups() {
        return groups;
    }

    public static List<VisualHierarchyNode> hierarchy() {
        return hierarchy;
    }

    public static int colorOf(InstanceId id) {
        VisualHierarchyNode node = node(id);
        if (node == null) {
            return -1;
        }
        for (VisualGroup group : groups) {
            if (group.id().equals(node.groupId())) {
                return group.color();
            }
        }
        return -1;
    }

    public static VisualHierarchyNode node(InstanceId id) {
        for (VisualHierarchyNode node : hierarchy) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    static List<VisualSetLink> hierarchyLinks() {
        Map<InstanceId, VisualHierarchyNode> byId = new HashMap<>();
        for (VisualHierarchyNode node : hierarchy) {
            byId.put(node.id(), node);
        }
        List<VisualSetLink> links = new ArrayList<>();
        for (VisualHierarchyNode node : hierarchy) {
            if (node.parent() != null) {
                VisualHierarchyNode parent = byId.get(node.parent());
                if (parent != null) {
                    links.add(new VisualSetLink(parent.origin(), node.origin()));
                }
            }
        }
        return List.copyOf(links);
    }

    static List<VisualGroupLink> coloredHierarchyLinks() {
        Map<InstanceId, VisualHierarchyNode> byId = new HashMap<>();
        for (VisualHierarchyNode node : hierarchy) {
            byId.put(node.id(), node);
        }
        List<VisualGroupLink> links = new ArrayList<>();
        for (VisualHierarchyNode node : hierarchy) {
            if (node.parent() == null) {
                continue;
            }
            VisualHierarchyNode parent = byId.get(node.parent());
            if (parent != null) {
                links.add(new VisualGroupLink(parent.origin(), node.origin(), colorOf(node.id())));
            }
        }
        return List.copyOf(links);
    }

    static int distinctGroupColors() {
        Set<Integer> colors = new LinkedHashSet<>();
        for (VisualGroup group : groups) {
            colors.add(group.color());
        }
        return colors.size();
    }

    static int maxDepth() {
        int depth = 0;
        for (VisualHierarchyNode node : hierarchy) {
            depth = Math.max(depth, node.depth());
        }
        return depth;
    }

    static Map<String, Integer> depthCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (VisualHierarchyNode node : hierarchy) {
            counts.merge("深度 " + node.depth(), 1, Integer::sum);
        }
        return counts;
    }
}
