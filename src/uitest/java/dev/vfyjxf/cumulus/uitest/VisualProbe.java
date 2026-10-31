package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.visual.VisualClaim;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public read-only view of the visual snapshot, the highlight model and the HUD text used by scenarios. */
public final class VisualProbe {

    private static final VisualHighlightSummary emptySummary = new VisualHighlightSummary(
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        null
    );

    private VisualProbe() {}

    public static @Nullable VisualSnapshot snapshot() {
        return VisualClientState.snapshot();
    }

    public static boolean hasSnapshot() {
        return snapshot() != null;
    }

    public static boolean hudVisible() {
        return VisualClientState.hudEnabled() && VisualClientState.hudRendered();
    }

    public static int instanceCount() {
        VisualSnapshot snapshot = snapshot();
        return snapshot == null ? 0 : snapshot.instanceCount();
    }

    public static int claimCount() {
        VisualSnapshot snapshot = snapshot();
        return snapshot == null ? 0 : snapshot.claimCount();
    }

    public static @Nullable VisualSetSummary setSummary() {
        return VisualClientState.setSummary();
    }

    public static int setLinkCount() {
        VisualSetSummary summary = VisualClientState.setSummary();
        return summary == null ? 0 : summary.linkCount();
    }

    public static List<VisualGroup> groups() {
        return VisualGroupState.groups();
    }

    public static List<VisualHierarchyNode> hierarchy() {
        return VisualGroupState.hierarchy();
    }

    public static int hierarchyDepth() {
        return VisualGroupState.maxDepth();
    }

    public static int hierarchyLinkCount() {
        return VisualGroupState.hierarchyLinks().size();
    }

    public static int groupColorCount() {
        return VisualGroupState.distinctGroupColors();
    }

    public static List<Integer> highlightColors() {
        return VisualHighlights.colors();
    }

    public static @Nullable VisualInstance firstInstance() {
        VisualSnapshot snapshot = snapshot();
        return snapshot == null || snapshot.instances().isEmpty() ? null : snapshot.instances().get(0);
    }

    public static @Nullable CuboidSet firstBounds() {
        VisualInstance instance = firstInstance();
        return instance == null ? null : instance.bounds();
    }

    /** The current highlight counts the world layer submits for the latest snapshot. */
    public static VisualHighlightSummary highlightSummary() {
        VisualSnapshot snapshot = snapshot();
        return snapshot == null ? emptySummary : VisualHighlights.summary();
    }

    public static @Nullable InstanceId selectedInstanceId() {
        return VisualClientState.selectedInstanceId();
    }

    public static String selectionSource() {
        return VisualClientState.selectionSource();
    }

    public static @Nullable VisualInstance selectedInstance() {
        InstanceId selected = selectedInstanceId();
        VisualSnapshot snapshot = snapshot();
        if (selected == null || snapshot == null) {
            return null;
        }
        for (VisualInstance instance : snapshot.instances()) {
            if (instance.id().equals(selected)) {
                return instance;
            }
        }
        return null;
    }

    public static List<String> legend() {
        List<String> lines = new ArrayList<>(VisualHighlights.legend());
        lines.addAll(VisualPartState.legendLines());
        return List.copyOf(lines);
    }

    public static List<VisualPart> parts() {
        return VisualPartState.parts();
    }

    public static @Nullable VisualPart inspectedPart() {
        return VisualInspectState.inspected();
    }

    /** The HUD's exact text model, line by line, for scenario assertions and capture checks. */
    public static List<String> hudLines() {
        return hudModel().flattened();
    }

    public static @Nullable VisualFlowModel flow() {
        return VisualClientState.flow();
    }

    public static @Nullable String lastEvent() {
        return VisualClientState.lastEvent();
    }

    public static @Nullable String error() {
        return VisualClientState.error();
    }

    public static Set<LatticePoint> claimCells() {
        VisualSnapshot snapshot = snapshot();
        Set<LatticePoint> cells = new LinkedHashSet<>();
        if (snapshot != null) {
            for (VisualClaim claim : snapshot.claims()) {
                cells.add(claim.cell().point());
            }
        }
        return cells;
    }

    static boolean hudContextValue(String pair) {
        return hudLines().stream().anyMatch(line -> line.startsWith("上下文键值: ") && line.contains(pair));
    }

    static boolean hudInspectContextValue(String pair) {
        return hudLines().stream().anyMatch(line -> line.startsWith("检视上下文值: ") && line.contains(pair));
    }

    public static boolean hudLineStartsWith(String prefix) {
        for (String line : hudLines()) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String codesLine(VisualSnapshot snapshot) {
        Set<String> codes = new LinkedHashSet<>();
        for (VisualInstance instance : snapshot.instances()) {
            codes.addAll(instance.findings());
        }
        return "错误码: " + (codes.isEmpty() ? "无" : String.join(", ", codes));
    }

    private static String selectionText(String source) {
        return switch (source) {
            case "crosshair" -> "准星";
            case "nearest" -> "最近";
            case "none" -> "无";
            default -> source;
        };
    }

    private static String eventText(String event) {
        return switch (event) {
            case "moved" -> "移动";
            case "invalidated" -> "失效";
            case "loaded" -> "加载";
            case "unloaded" -> "卸载";
            default -> event;
        };
    }

    private static String boundsText(CuboidSet bounds) {
        List<String> boxes = new ArrayList<>();
        for (Cuboid box : bounds.boxes()) {
            boxes.add(
                "[" + box.min().x() + "," + box.min().y() + "," + box.min().z() + ".." + box.max().x() + ","
                        + box.max().y() + "," + box.max().z() + "]"
            );
        }
        return String.join(" ", boxes);
    }

    public static boolean compact() {
        return VisualClientState.compact();
    }

    public static void setCompact(boolean value) {
        VisualClientState.setCompact(value);
    }

    public static void toggleCompact() {
        VisualClientState.toggleCompact();
    }

    public static @Nullable InstanceId hoveredInstanceId() {
        return VisualClientState.hoveredInstanceId();
    }

    public static @Nullable LatticePoint hoveredCell() {
        return VisualClientState.hoveredCell();
    }

    public static @Nullable VisualInstance hoveredInstance() {
        InstanceId hovered = hoveredInstanceId();
        VisualSnapshot snapshot = snapshot();
        if (hovered == null || snapshot == null) {
            return null;
        }
        for (VisualInstance instance : snapshot.instances()) {
            if (instance.id().equals(hovered)) {
                return instance;
            }
        }
        return null;
    }

    /** Ticks between the server read and now, clamped at zero. */
    public static long snapshotAgeTicks() {
        VisualSnapshot snapshot = snapshot();
        var level = Minecraft.getInstance().level;
        if (snapshot == null || level == null) {
            return 0L;
        }
        return Math.max(0L, level.getGameTime() - snapshot.capturedTick());
    }

    public static VisualHudModel hudModel() {
        VisualSnapshot snapshot = snapshot();
        if (snapshot == null) {
            return VisualHudModel.empty();
        }
        VisualHighlightSummary summary = highlightSummary();
        int shared = 0;
        int exclusive = 0;
        for (VisualClaim claim : snapshot.claims()) {
            if (claim.sharing() == Sharing.exclusive) {
                exclusive++;
            } else {
                shared++;
            }
        }
        VisualInstance selected = selectedInstance();
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("实例: " + snapshot.instanceCount());
        summaryLines.add("草稿: " + summary.draftCells());
        summaryLines.add(
            "声明: " + snapshot.claimCount() + " (修订 " + snapshot.claimRevision() + ", 共享 " + shared + ", 独占 " + exclusive
                    + ")"
        );
        summaryLines.add("冲突: " + summary.conflictCells() + " 格, " + summary.conflictInstances() + " 实例");
        summaryLines.add(
            "高亮: " + summary.boundsBoxes() + " 边界, " + summary.claimCells() + " 格, " + summary.movedInstances()
                    + " 移动, " + summary.invalidatedInstances() + " 失效, " + summary.selectedBounds() + " 选中盒"
        );
        if (summary.boundsBoxes() < snapshot.instanceCount() || summary.claimCells() < snapshot.claimCount()) {
            summaryLines.add(
                "高亮上限: 边界 " + summary.boundsBoxes() + "/" + snapshot.instanceCount() + ", 声明格 " + summary.claimCells()
                        + "/" + snapshot.claimCount()
            );
        }
        if (!VisualPartState.parts().isEmpty()) {
            summaryLines.add(
                "部件: " + VisualPartState.parts().size() + " 个部件, " + VisualPartState.distinctRoleCount() + " 种角色"
            );
        }
        if (compact()) {
            VisualPart compactPart = selected == null ? null : VisualPartState.part(selected.id());
            if (compactPart != null) {
                summaryLines.add("部件: " + compactPart.name() + "（" + compactPart.role().displayName() + "）");
            }
        }
        String hoveredText = "无";
        InstanceId hovered = hoveredInstanceId();
        if (hovered != null) {
            hoveredText = idText(hovered);
        }
        LatticePoint hoverCell = hoveredCell();
        summaryLines.add("悬停: " + hoveredText + " 于 " + (hoverCell == null ? "无" : pointText(hoverCell)));
        long ageBucket = snapshotAgeTicks() / 10L * 10L;
        summaryLines.add(
            "快照: 年龄 " + ageBucket + " tick, 时刻 " + snapshot.capturedTick() + ", 选中 " + selectionText(selectionSource())
        );
        String lastEvent = VisualClientState.lastEvent();
        summaryLines.add(
            "事件: 移动 " + summary.movedInstances() + ", 失效 " + summary.invalidatedInstances() + ", 最近 "
                    + (lastEvent == null ? "无" : eventText(lastEvent))
        );
        String error = VisualClientState.error();
        if (error != null) {
            summaryLines.add("错误: " + error);
        }
        VisualSetSummary set = VisualClientState.setSummary();
        if (set == null) {
            summaryLines.add("结构集: 无");
        } else {
            summaryLines.add(
                "结构集: " + set.id() + " 成员 " + set.members() + ", 已形成 " + set.formed() + ", 声明 " + set.claims() + ", 预算 "
                        + set.budgetUsed() + "/" + set.budgetLimit() + ", 链接 " + set.linkCount()
            );
        }
        summaryLines.add(codesLine(snapshot));
        if (VisualShowcaseState.active()) {
            summaryLines.add(
                "展示区: " + VisualShowcaseState.zoneCount() + " 个区域, " + VisualShowcaseState.totalInstances() + " 实例, "
                        + VisualShowcaseState.totalParts() + " 部件"
            );
            summaryLines.add(VisualShowcaseState.instructionLine());
        }
        List<String> detailLines = new ArrayList<>();
        if (selected != null) {
            detailLines.add("选中: " + selectionText(selectionSource()) + " " + idText(selected.id()));
            detailLines.add("所有者: " + selected.owner().id());
            VisualHierarchyNode selectedNode = VisualGroupState.node(selected.id());
            if (selectedNode != null) {
                detailLines.add(
                    "分组: " + selectedNode.groupId() + " 节点 " + selectedNode.label() + " 深度 " + selectedNode.depth()
                );
            }
            detailLines.add("坐标: " + sitingText(selected.siting()));
            detailLines.add("朝向: " + orientationText(selected.siting()));
            detailLines.add("边界: " + boundsText(selected.bounds()));
            detailLines.add(
                "上下文: 修订 " + selected.contextRevision() + ", 消耗 " + selected.spend().cells() + "/"
                        + selected.spend().sections() + ", 键 " + selected.contextKeys().size() + ", 值 "
                        + selected.contextValueCount() + "（显示 " + VisualContexts.semanticKeyCount(selected) + "）"
            );
            addContextValueLines(detailLines, selected, "上下文键值: ");
            VisualPart selectedPart = VisualPartState.part(selected.id());
            if (selectedPart != null) {
                detailLines.add(
                    "部件: " + selectedPart.name() + "（" + selectedPart.role().displayName() + " "
                            + colorText(selectedPart.color()) + "）"
                );
                detailLines.add("部件描述: " + selectedPart.description());
            }
            detailLines.add("发现: " + (selected.findings().isEmpty() ? "无" : String.join(", ", selected.findings())));
        } else {
            detailLines.add("选中: 无");
            VisualInstance first = firstInstance();
            if (first != null) {
                detailLines.add("所有者: " + first.owner().id());
                VisualHierarchyNode firstNode = VisualGroupState.node(first.id());
                if (firstNode != null) {
                    detailLines.add(
                        "分组: " + firstNode.groupId() + " 节点 " + firstNode.label() + " 深度 " + firstNode.depth()
                    );
                }
                detailLines.add("坐标: " + sitingText(first.siting()));
                detailLines.add("朝向: " + orientationText(first.siting()));
                detailLines.add("边界: " + boundsText(first.bounds()));
                detailLines.add(
                    "上下文: 修订 " + first.contextRevision() + ", 消耗 " + first.spend().cells() + "/"
                            + first.spend().sections() + ", 键 " + first.contextKeys().size() + ", 值 "
                            + first.contextValueCount() + "（显示 " + VisualContexts.semanticKeyCount(first) + "）"
                );
                addContextValueLines(detailLines, first, "上下文键值: ");
            }
        }
        VisualHierarchyNode selectedNode = selected == null ? null : VisualGroupState.node(selected.id());
        VisualGroup selectedGroup = null;
        if (selectedNode != null) {
            for (VisualGroup group : VisualGroupState.groups()) {
                if (group.id().equals(selectedNode.groupId())) {
                    selectedGroup = group;
                    break;
                }
            }
        }
        List<String> groupLines = new ArrayList<>();
        List<VisualGroup> groups = VisualGroupState.groups();
        if (groups.isEmpty()) {
            groupLines.add("分组: 无");
        } else {
            groupLines.add("分组: " + groups.size() + " 组, " + VisualGroupState.hierarchy().size() + " 节点");
            if (selectedGroup == null) {
                groupLines.add("选中分组: 无");
            } else {
                groupLines.add(
                    "选中分组: " + selectedGroup.displayName() + " [" + selectedGroup.id() + "]: 实例 "
                            + selectedGroup.instanceCount() + ", 声明 " + selectedGroup.claimCount() + ", 颜色 "
                            + colorText(selectedGroup.color())
                );
            }
            if (compact()) {
                String selectedText = selectedNode == null
                        ? "无"
                        : selectedGroup.displayName() + "/" + selectedNode.label() + "（深度 " + selectedNode.depth()
                                + "）";
                summaryLines.add(
                    "分组: " + groups.size() + " 组, " + snapshot.instanceCount() + " 实例, 声明 " + snapshot.claimCount()
                            + "；层级 " + VisualGroupState.hierarchy().size() + " 节点, 最大深度 " + VisualGroupState.maxDepth()
                            + "；选中 " + selectedText
                );
            }
        }
        List<String> hierarchyLines = new ArrayList<>();
        List<VisualHierarchyNode> hierarchy = VisualGroupState.hierarchy();
        if (hierarchy.isEmpty()) {
            hierarchyLines.add("层级: 无");
        } else {
            hierarchyLines.add(
                "层级: " + hierarchy.size() + " 节点, 最大深度 " + VisualGroupState.maxDepth() + ", 链接 "
                        + VisualGroupState.hierarchyLinks().size()
            );
            if (selectedNode == null) {
                hierarchyLines.add("选中节点: 无");
            } else {
                hierarchyLines.add(
                    "选中节点: " + selectedNode.label() + " 深度 " + selectedNode.depth() + ", 父节点 "
                            + parentLabel(selectedNode) + ", 分组 " + selectedNode.groupId()
                );
            }
        }
        List<String> inspectLines = new ArrayList<>();
        VisualPart inspected = VisualInspectState.inspected();
        if (inspected != null) {
            inspectLines.add(
                "检视: " + inspected.name() + "（" + inspected.role().displayName() + " " + colorText(inspected.color())
                        + "）"
            );
            inspectLines.add("检视实例: " + idText(inspected.instance()));
            LatticePoint inspectCell = VisualInspectState.inspectedCell();
            inspectLines.add("检视方块: " + (inspectCell == null ? "无" : pointText(inspectCell)));
            VisualHierarchyNode inspectedNode = VisualGroupState.node(inspected.instance());
            inspectLines.add(
                inspectedNode == null ? "检视分组: 无" : "检视分组: " + inspectedNode.groupId() + " 深度 " + inspectedNode.depth()
            );
            VisualInstance inspectedInstance = instanceById(snapshot, inspected.instance());
            if (inspectedInstance != null) {
                inspectLines.add(
                    "检视上下文: 修订 " + inspectedInstance.contextRevision() + ", 消耗 " + inspectedInstance.spend().cells()
                            + "/" + inspectedInstance.spend().sections() + ", 键 "
                            + inspectedInstance.contextKeys().size() + ", 值 " + inspectedInstance.contextValueCount()
                            + "（显示 " + VisualContexts.semanticKeyCount(inspectedInstance) + "）"
                );
                addContextValueLines(inspectLines, inspectedInstance, "检视上下文值: ");
                inspectLines.add(
                    "检视发现: " + (inspectedInstance.findings().isEmpty()
                            ? "无"
                            : String.join(", ", inspectedInstance.findings()))
                );
            }
            inspectLines.add("检视边界: " + boundsText(inspected.bounds()));
            inspectLines.add("检视声明: " + claimsForPart(snapshot, inspected) + " 格");
            inspectLines.add("检视描述: " + inspected.description());
        }
        VisualFlowModel flow = VisualClientState.flow();
        List<String> flowLines = flow == null ? List.of() : flow.hudLines();
        if (flow != null && compact()) {
            flowLines = List.of(flow.compactLine());
        }
        return new VisualHudModel(
            "StructureCumulus 可视化",
            summaryLines,
            detailLines,
            groupLines,
            hierarchyLines,
            inspectLines,
            flowLines,
            legend(),
            compact()
        );
    }

    private static void addContextValueLines(List<String> lines, VisualInstance instance, String prefix) {
        Map<String, String> values = VisualContextValueState.values(instance.id());
        List<String> pairs = new ArrayList<>();
        for (String key : instance.contextKeys()) {
            if (VisualContexts.isSemanticKey(key)) {
                String label = key.startsWith("structurecumulus:visual_")
                        ? key.substring("structurecumulus:visual_".length())
                        : key;
                pairs.add(label + "=" + values.getOrDefault(key, "（未采集）"));
            }
        }
        if (pairs.isEmpty()) {
            lines.add(prefix + "无（仅通用键已隐藏）");
            return;
        }
        for (int index = 0; index < pairs.size(); index += 2) {
            String line = prefix + pairs.get(index);
            if (index + 1 < pairs.size()) {
                line += " | " + pairs.get(index + 1);
            }
            lines.add(line);
        }
    }

    private static @Nullable VisualInstance instanceById(VisualSnapshot snapshot, InstanceId id) {
        for (VisualInstance instance : snapshot.instances()) {
            if (instance.id().equals(id)) {
                return instance;
            }
        }
        return null;
    }

    private static String parentLabel(VisualHierarchyNode node) {
        if (node.parent() == null) {
            return "无";
        }
        for (VisualHierarchyNode candidate : VisualGroupState.hierarchy()) {
            if (candidate.id().equals(node.parent())) {
                return candidate.label() + "（深度 " + candidate.depth() + "）";
            }
        }
        return "无";
    }

    private static int claimsForPart(VisualSnapshot snapshot, VisualPart part) {
        VisualInstance target = null;
        for (VisualInstance instance : snapshot.instances()) {
            if (instance.id().equals(part.instance())) {
                target = instance;
                break;
            }
        }
        if (target == null) {
            return 0;
        }
        int count = 0;
        for (VisualClaim claim : snapshot.claims()) {
            if (claim.owner().equals(target.owner()) && claim.siting().equals(target.siting())) {
                count++;
            }
        }
        return count;
    }

    private static String colorText(int color) {
        return String.format("#%08X", color);
    }

    private static String sitingText(Siting siting) {
        return "原点=" + pointText(siting.origin()) + ", 偏移=" + pointText(siting.placement().offset());
    }

    private static String orientationText(Siting siting) {
        Orientation orientation = siting.placement().orientation();
        return orientation.turn() + "/" + orientation.tilt() + " 序数=" + orientation.ordinal() + " 镜像="
                + orientation.isMirror();
    }

    private static String idText(InstanceId id) {
        return id.world().id() + "#" + id.serial();
    }

    private static String pointText(LatticePoint point) {
        return "(" + point.x() + "," + point.y() + "," + point.z() + ")";
    }
}
