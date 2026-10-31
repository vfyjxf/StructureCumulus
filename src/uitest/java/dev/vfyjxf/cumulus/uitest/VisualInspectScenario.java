package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V6 inspect-break scenario: inspect a complex part, remove its target block, and prove the client
 * tick validation clears the inspector card even though the fixture's part model still exists.
 */
@ClientScenario(
    name = "visual.inspect",
    group = "visual",
    tags = {"visual", "inspect", "break"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualInspectScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualComplexFixtures.Fixture fixture = VisualComplexFixtures.blastFurnace(VisualScenarioSupport.point(origin));
        LatticePoint root = fixture.members().get(0).siting().origin();
        BlockPos target = new BlockPos(root.x(), root.y(), root.z());
        build = build.guiScale(2).hideGui(false)
                .log("visual.inspect: inspect a part, remove its block, and clear the stale card")
                .runCommand("cumulusvisual clearall");
        build = VisualComplexScenarioSupport.place(build, fixture);
        build = build.runCommand("gamemode creative @a").runCommand("cumulusvisual complex blast_furnace 0 100 0")
                .teleportPlayer(root.x() + 0.5D, root.y() + 2.0D, root.z() + 12.5D)
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(target), 8, null)
                .awaitClientChunk(origin.getX() >> 4, origin.getZ() >> 4)
                .waitUntil(
                    "the complex fixture exposes its parts",
                    () -> VisualProbe.instanceCount() == fixture.members().size()
                            && VisualProbe.parts().size() == fixture.members().size()
                ).runCommand("cumulusvisual inspect " + target.getX() + " " + target.getY() + " " + target.getZ())
                .waitUntil(
                    "the inspect card shows the target part",
                    () -> VisualProbe.inspectedPart() != null
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视: controller（控制器 "))
                )
                .check(
                    "the inspector records the exact target block",
                    () -> VisualProbe.hudLines()
                            .contains("检视方块: (" + target.getX() + "," + target.getY() + "," + target.getZ() + ")")
                ).screenshot("inspect-before-hud").frames(2).checkCaptured("inspect-before-hud")
                .screenshot("inspect-before-world").frames(2).checkCaptured("inspect-before-world")
                .clearArea(target.getX(), target.getY(), target.getZ(), target.getX(), target.getY(), target.getZ())
                .waitUntil(
                    "removing the target block clears the inspector card",
                    () -> VisualProbe.inspectedPart() == null
                            && VisualProbe.hudLines().stream().noneMatch(line -> line.startsWith("检视: "))
                )
                .check(
                    "the part model still exists when the block path clears the card",
                    () -> VisualProbe.parts().size() == fixture.members().size()
                            && VisualProbe.instanceCount() == fixture.members().size()
                )
                .check(
                    "no stale inspect or description lines remain",
                    () -> VisualProbe.hudLines().stream().noneMatch(line -> line.startsWith("检视"))
                ).check("the target block is gone", () -> !blockPresent(target)).screenshot("inspect-after-hud")
                .frames(2).checkCaptured("inspect-after-hud").screenshot("inspect-after-world").frames(2)
                .checkCaptured("inspect-after-world").runCommand("cumulusvisual clearall");
        build = VisualComplexScenarioSupport.clear(build, fixture).fill(
            target.getX(),
            target.getY(),
            target.getZ(),
            target.getX(),
            target.getY(),
            target.getZ(),
            Blocks.AIR.defaultBlockState()
        );
        VisualHierarchyFixtures.Fixture groups = VisualHierarchyFixtures
                .groups(VisualScenarioSupport.point(VisualScenarioSupport.cell(128, 0, 0)));
        LatticePoint groupRoot = groups.members().get(0).siting().origin();
        BlockPos groupTarget = new BlockPos(groupRoot.x(), groupRoot.y(), groupRoot.z());
        build = VisualHierarchyScenarioSupport.place(build, groups).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual groups 128 100 0")
                .teleportPlayer(groupRoot.x() + 0.5D, groupRoot.y() + 2.0D, groupRoot.z() + 8.5D)
                .awaitClientChunk(groupRoot.x() >> 4, groupRoot.z() >> 4)
                .waitUntil(
                    "the grouped fixture exposes its parts",
                    () -> VisualProbe.instanceCount() == groups.members().size()
                            && VisualProbe.parts().size() == groups.members().size()
                )
                .runCommand(
                    "cumulusvisual inspect " + groupTarget.getX() + " " + groupTarget.getY() + " " + groupTarget.getZ()
                ).waitUntil("the grouped inspect card opens", () -> VisualProbe.inspectedPart() != null)
                .check(
                    "the grouped inspector reports a group part",
                    () -> VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视分组: "))
                )
                .clearArea(
                    groupTarget.getX(),
                    groupTarget.getY(),
                    groupTarget.getZ(),
                    groupTarget.getX(),
                    groupTarget.getY(),
                    groupTarget.getZ()
                )
                .waitUntil(
                    "removing the grouped target block clears the card",
                    () -> VisualProbe.inspectedPart() == null
                            && VisualProbe.hudLines().stream().noneMatch(line -> line.startsWith("检视: "))
                )
                .check(
                    "the grouped part model still exists after the block clears the card",
                    () -> VisualProbe.parts().size() == groups.members().size()
                ).screenshot("inspect-groups-after").frames(2).checkCaptured("inspect-groups-after");
        build = VisualHierarchyScenarioSupport.clear(build, groups).runCommand("cumulusvisual clearall");
    }

    private static boolean blockPresent(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level != null && !level.getBlockState(pos).isAir();
    }
}
