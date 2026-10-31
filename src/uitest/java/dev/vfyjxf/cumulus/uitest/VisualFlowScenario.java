package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * V3 flow scenario: reuse the complex blast-furnace fixture, then step the deterministic flow model
 * draft → check → settle → 已形成 → claims → context → capture through the dev command and assert the
 * HUD strip, per-member states and statuses at each captured phase.
 */
@ClientScenario(
    name = "visual.flow",
    group = "visual",
    tags = {"visual", "flow", "lifecycle"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 240_000
)
public final class VisualFlowScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(128, 0, 0);
    private static final LatticePoint root = VisualScenarioSupport.point(origin);

    public void define(ScenarioBuilder build) {
        VisualComplexFixtures.Fixture fixture = VisualComplexFixtures.blastFurnace(root);
        int memberCount = fixture.members().size();
        build = build.guiScale(2).hideGui(false)
                .log("visual.flow: deterministic draft → check → settle → 已形成 → claims → context → capture")
                .runCommand("cumulusvisual clearall");
        build = VisualComplexScenarioSupport.place(build, fixture);
        build = build.runCommand("gamemode creative @a").runCommand("cumulusvisual complex blast_furnace 128 100 0")
                .teleportPlayer(root.x() + 0.5, root.y() + 1.0, root.z() + 12.5)
                .awaitClientChunk(root.x() >> 4, root.z() >> 4)
                .waitUntil(
                    "the complex fixture is fully 已形成 before the flow starts",
                    () -> VisualProbe.instanceCount() == memberCount && VisualProbe.claimCount() == fixture.claimRows()
                ).waitUntil(
                    "the HUD renders the complex set summary",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.hudLines().contains(VisualComplexScenarioSupport.setLine(fixture))
                ).runCommand("cumulusvisual flow draft").waitUntil(
                    "the flow model starts in draft",
                    () -> VisualProbe.flow() != null
                            && VisualProbe.flow().current() == VisualFlowModel.Phase.draft
                            && VisualProbe.hudLineStartsWith("流程: ")
                ).check("the flow lists all seven phases", () -> VisualProbe.flow().phaseCount() == 7)
                .check("draft has no completed phases", () -> VisualProbe.flow().completedCount() == 0)
                .check("draft observes every fixture member", () -> VisualProbe.flow().members().size() == memberCount)
                .check(
                    "draft leaves every member pending",
                    () -> VisualProbe.flow().memberFlows().stream()
                            .allMatch(member -> member.status() == VisualFlowModel.MemberStatus.pending)
                ).check(
                    "draft reports pending claims, context and capture",
                    () -> VisualProbe.flow().claims() == VisualFlowModel.Ready.pending
                            && VisualProbe.flow().context() == VisualFlowModel.Ready.pending
                            && VisualProbe.flow().capture() == VisualFlowModel.Ready.pending
                ).check(
                    "the HUD strip marks draft as current",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("流程: ") && line.contains("[>] 草稿"))
                ).check(
                    "the HUD phase line reports draft",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("流程阶段: 草稿 "))
                ).check(
                    "the HUD maps phase colors to the highlight palette",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("流程颜色: 草稿=琥珀"))
                ).screenshot("draft-hud").frames(2).checkCaptured("draft-hud").screenshot("draft-world").frames(2)
                .checkCaptured("draft-world").runCommand("cumulusvisual flow settle")
                .waitUntil(
                    "the flow reaches settle",
                    () -> VisualProbe.flow().current() == VisualFlowModel.Phase.settle
                            && VisualProbe.flow().completedCount() == 2
                ).check(
                    "settle completes draft and check",
                    () -> VisualProbe.flow().completed()
                            .containsAll(List.of(VisualFlowModel.Phase.draft, VisualFlowModel.Phase.check))
                ).check(
                    "settle keeps 成员 pending",
                    () -> VisualProbe.flow().memberFlows().stream()
                            .allMatch(member -> member.status() == VisualFlowModel.MemberStatus.pending)
                ).check(
                    "the HUD strip marks settle as current",
                    () -> VisualProbe.hudLines().stream().anyMatch(
                        line -> line.startsWith("流程: ")
                                && line.contains("[>] 结算")
                                && line.contains("[x] 草稿")
                                && line.contains("[x] 检查")
                    )
                ).screenshot("settle-hud").frames(2).checkCaptured("settle-hud").screenshot("settle-world").frames(2)
                .checkCaptured("settle-world").runCommand("cumulusvisual flow formed")
                .waitUntil(
                    "the flow reaches formed",
                    () -> VisualProbe.flow().current() == VisualFlowModel.Phase.formed
                            && VisualProbe.flow().completedCount() == 3
                )
                .check(
                    "已形成 marks every member formed",
                    () -> VisualProbe.flow().memberFlows().stream()
                            .allMatch(member -> member.status() == VisualFlowModel.MemberStatus.formed)
                )
                .check(
                    "the HUD reports the 已形成 members",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("流程成员: ") && line.contains("根节点=已形成"))
                )
                .check(
                    "the HUD strip marks 已形成 as current",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("流程: ") && line.contains("[>] 形成"))
                ).screenshot("formed-hud").frames(2).checkCaptured("formed-hud").screenshot("formed-world").frames(2)
                .checkCaptured("formed-world").runCommand("cumulusvisual flow claims")
                .waitUntil(
                    "the flow reaches claims",
                    () -> VisualProbe.flow().current() == VisualFlowModel.Phase.claims
                            && VisualProbe.flow().claims() == VisualFlowModel.Ready.ready
                )
                .check(
                    "声明 marks every member claimed",
                    () -> VisualProbe.flow().memberFlows().stream()
                            .allMatch(member -> member.status() == VisualFlowModel.MemberStatus.claimed)
                )
                .check(
                    "the HUD reports ready claims",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("流程声明: 就绪, 上下文: 待定, 捕获: 待定"))
                ).runCommand("cumulusvisual flow context")
                .waitUntil(
                    "the flow reaches context",
                    () -> VisualProbe.flow().current() == VisualFlowModel.Phase.context
                            && VisualProbe.flow().context() == VisualFlowModel.Ready.ready
                )
                .check(
                    "context is ready and capture still pending",
                    () -> VisualProbe.flow().context() == VisualFlowModel.Ready.ready
                            && VisualProbe.flow().capture() == VisualFlowModel.Ready.pending
                ).runCommand("cumulusvisual flow capture")
                .waitUntil(
                    "the flow reaches capture",
                    () -> VisualProbe.flow().current() == VisualFlowModel.Phase.capture
                            && VisualProbe.flow().completedCount() == 6
                )
                .check(
                    "capture completes every phase",
                    () -> VisualProbe.flow().completed().size() == 6
                            && VisualProbe.flow().current() == VisualFlowModel.Phase.capture
                )
                .check(
                    "capture reports ready claims, context and capture",
                    () -> VisualProbe.flow().claims() == VisualFlowModel.Ready.ready
                            && VisualProbe.flow().context() == VisualFlowModel.Ready.ready
                            && VisualProbe.flow().capture() == VisualFlowModel.Ready.ready
                )
                .check(
                    "the HUD phase line reports capture",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("流程阶段: 捕获 "))
                ).screenshot("capture-hud").frames(2).checkCaptured("capture-hud").screenshot("capture-world").frames(2)
                .checkCaptured("capture-world").runCommand("cumulusvisual hud compact").frames(3)
                .waitUntil(
                    "the compact HUD shows the one-line flow phase",
                    () -> VisualProbe.compact() && VisualProbe.hudLines().contains("流程: 捕获 (7/7)")
                ).check("compact keeps the current flow phase", () -> VisualProbe.hudLines().contains("流程: 捕获 (7/7)"))
                .runCommand("cumulusvisual hud detail").frames(3).waitUntil(
                    "the detail HUD returns",
                    () -> !VisualProbe.compact() && VisualProbe.hudLineStartsWith("流程: ")
                );
        build = VisualComplexScenarioSupport.clear(build, fixture);
        build.runCommand("cumulusvisual clearall");
    }
}
