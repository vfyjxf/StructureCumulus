package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dev-only client model of the structure flow: draft → check → settle → formed → claims → context →
 * capture. It holds the deterministic phase the dev command selected, the completed phases, the last
 * transition and the observed per-member states; it never mutates kernel state.
 */
public record VisualFlowModel(
    Phase current,
    List<Phase> completed,
    @Nullable String lastTransition,
    List<String> members,
    Set<String> failedMembers
) {

    public enum Phase {
        draft("草稿"), check("检查"), settle("结算"), formed("形成"), claims("声明"), context("上下文"), capture("捕获");

        private final String displayName;

        Phase(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum MemberStatus {
        pending("待定"), formed("已形成"), claimed("已声明"), failed("失败");

        private final String displayName;

        MemberStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record MemberFlow(String name, MemberStatus status) {}

    public enum Ready {
        pending("待定"), ready("就绪");

        private final String displayName;

        Ready(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public VisualFlowModel {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(completed, "completed");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(failedMembers, "failedMembers");
        completed = List.copyOf(completed);
        members = List.copyOf(members);
        failedMembers = Set.copyOf(failedMembers);
    }

    public static VisualFlowModel start(List<String> members) {
        return new VisualFlowModel(Phase.draft, List.of(), null, members, Set.of());
    }

    public VisualFlowModel advanceTo(Phase target) {
        List<Phase> next = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            if (phase.ordinal() < target.ordinal()) {
                next.add(phase);
            }
        }
        String transition = current == target ? lastTransition : current + " -> " + target;
        return new VisualFlowModel(target, next, transition, members, failedMembers);
    }

    public VisualFlowModel observe(List<VisualInstance> instances) {
        List<String> names = new ArrayList<>();
        Set<String> failed = new HashSet<>();
        int index = 0;
        for (VisualInstance instance : instances) {
            names.add(index == 0 ? "根节点" : "卫星-" + index);
            if (!instance.findings().isEmpty()) {
                failed.add(index == 0 ? "根节点" : "卫星-" + index);
            }
            index++;
        }
        List<String> nextNames = names.isEmpty() ? members : names;
        return new VisualFlowModel(current, completed, lastTransition, nextNames, failed);
    }

    public List<MemberFlow> memberFlows() {
        List<MemberFlow> flows = new ArrayList<>(members.size());
        for (String member : members) {
            flows.add(new MemberFlow(member, memberStatus(member)));
        }
        return flows;
    }

    public MemberStatus memberStatus(String member) {
        if (failedMembers.contains(member)) {
            return MemberStatus.failed;
        }
        if (current.ordinal() < Phase.formed.ordinal()) {
            return MemberStatus.pending;
        }
        if (current.ordinal() < Phase.claims.ordinal()) {
            return MemberStatus.formed;
        }
        return MemberStatus.claimed;
    }

    public Ready claims() {
        return ready(Phase.claims);
    }

    public Ready context() {
        return ready(Phase.context);
    }

    public Ready capture() {
        return ready(Phase.capture);
    }

    private Ready ready(Phase phase) {
        return current.ordinal() >= phase.ordinal() ? Ready.ready : Ready.pending;
    }

    public int completedCount() {
        return completed.size();
    }

    public int phaseCount() {
        return Phase.values().length;
    }

    /** The strip line, then phase/status/member lines, all part of the HUD text model. */
    public List<String> hudLines() {
        List<String> lines = new ArrayList<>();
        lines.add(stripLine());
        lines.add(
            "流程阶段: " + current.displayName() + " (" + (completedCount() + 1) + "/" + phaseCount() + "), 已完成 "
                    + completedCount() + ", 最近 " + (lastTransition == null ? "无" : transitionText(lastTransition))
        );
        lines.add(
            "流程声明: " + claims().displayName() + ", 上下文: " + context().displayName() + ", 捕获: " + capture().displayName()
        );
        lines.add("流程颜色: 草稿=琥珀, 检查=白, 结算=蓝, 形成=青, 声明=青, 上下文=灰, 捕获=白");
        List<MemberFlow> flows = memberFlows();
        for (int index = 0; index < flows.size(); index += 4) {
            List<String> chunk = new ArrayList<>();
            for (int offset = index; offset < Math.min(index + 4, flows.size()); offset++) {
                MemberFlow flow = flows.get(offset);
                chunk.add(flow.name() + "=" + flow.status().displayName());
            }
            lines.add("流程成员: " + String.join(", ", chunk));
        }
        if (members.isEmpty()) {
            lines.add("流程成员: 无");
        }
        return List.copyOf(lines);
    }

    public String stripLine() {
        StringBuilder line = new StringBuilder("流程: ");
        Phase[] phases = Phase.values();
        for (int index = 0; index < phases.length; index++) {
            Phase phase = phases[index];
            if (index > 0) {
                line.append(" > ");
            }
            String marker = phase.equals(current) ? "[>]" : completed.contains(phase) ? "[x]" : "[ ]";
            line.append(marker).append(' ').append(phase.displayName());
        }
        return line.toString();
    }

    public String compactLine() {
        return "流程: " + current.displayName() + " (" + (completedCount() + 1) + "/" + phaseCount() + ")";
    }

    private static String transitionText(String transition) {
        String text = transition;
        for (Phase phase : Phase.values()) {
            text = text.replace(phase.name(), phase.displayName());
        }
        return text;
    }
}
