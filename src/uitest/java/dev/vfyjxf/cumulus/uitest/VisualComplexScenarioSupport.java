package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Shared block placement, camera flyover and expected-text helpers for the complex fixtures. */
final class VisualComplexScenarioSupport {

    private static final Block[] palette = {Blocks.BLAST_FURNACE, Blocks.BRICKS, Blocks.IRON_BLOCK, Blocks.CHEST,
            Blocks.HOPPER, Blocks.FURNACE, Blocks.OBSERVER, Blocks.REDSTONE_WIRE, Blocks.POLISHED_ANDESITE,
            Blocks.GLASS};

    private VisualComplexScenarioSupport() {}

    static ScenarioBuilder define(
        ScenarioBuilder build,
        VisualComplexFixtures.Fixture fixture,
        String command,
        String log
    ) {
        LatticePoint root = fixture.rootOrigin();
        String setLine = setLine(fixture);
        build = build.guiScale(2).hideGui(false).log(log).runCommand("cumulusvisual clearall");
        build = place(build, fixture);
        build = build.runCommand("gamemode creative @a").runCommand(command)
                .teleportPlayer(root.x() + 0.5, root.y() + 1.0, root.z() + 12.5)
                .awaitClientChunk(root.x() >> 4, root.z() >> 4)
                .waitUntil(
                    "the snapshot holds every complex member and claim row",
                    () -> VisualProbe.instanceCount() == fixture.members().size()
                            && VisualProbe.claimCount() == fixture.claimRows()
                )
                .waitUntil(
                    "the HUD renders the complex set summary",
                    () -> VisualProbe.hudVisible() && VisualProbe.hudLines().contains(setLine)
                ).waitUntil("the highlight layer submits the complex bounds and links", () -> {
                    VisualHighlightSummary summary = VisualProbe.highlightSummary();
                    return summary.boundsBoxes() == fixture.members().size()
                            && summary.boundsCuboids() == fixture.boundsBoxes()
                            && summary.setLinks() == fixture.links().size();
                })
                .check(
                    "the set summary names every member and link",
                    () -> VisualProbe.setSummary() != null
                            && VisualProbe.setSummary().members() == fixture.members().size()
                            && VisualProbe.setSummary().formed() == fixture.members().size()
                            && VisualProbe.setSummary().linkCount() == fixture.links().size()
                ).check("the HUD reports the complex set summary", () -> VisualProbe.hudLines().contains(setLine))
                .check(
                    "the multi-cuboid bounds are highlighted",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == fixture.members().size()
                            && VisualProbe.highlightSummary().boundsCuboids() == fixture.boundsBoxes()
                )
                .check(
                    "the claim rows are highlighted",
                    () -> VisualProbe.claimCount() == fixture.claimRows() && !VisualProbe.claimCells().isEmpty()
                )
                .check(
                    "the links are submitted to the highlight layer",
                    () -> VisualProbe.highlightSummary().setLinks() == fixture.links().size()
                )
                .check(
                    "the HUD detail reports orientation and world bounds",
                    () -> VisualProbe.hudLineStartsWith("朝向: ") && VisualProbe.hudLineStartsWith("边界: ")
                ).timelineRecord(
                    "complex-state",
                    () -> "instances=" + VisualProbe.instanceCount() + ", claims=" + VisualProbe.claimCount() + ", "
                            + VisualScenarioSupport.describe(VisualProbe.highlightSummary())
                );
        build = flyover(build, fixture);
        build = build
                .check(
                    "the complex set survives both flyover viewpoints",
                    () -> VisualProbe.instanceCount() == fixture.members().size()
                            && VisualProbe.claimCount() == fixture.claimRows()
                )
                .check(
                    "the complex highlight boxes remain bounded",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == fixture.members().size()
                            && VisualProbe.highlightSummary().boundsCuboids() == fixture.boundsBoxes()
                ).screenshot("hud").frames(2).checkCaptured("hud");
        build = clear(build, fixture);
        return build.runCommand("cumulusvisual clearall");
    }

    static ScenarioBuilder place(ScenarioBuilder build, VisualComplexFixtures.Fixture fixture) {
        int memberIndex = 0;
        for (VisualComplexFixtures.Member member : fixture.members()) {
            Block block = palette[memberIndex++ % palette.length];
            for (Cuboid box : member.bounds().boxes()) {
                build = build.fill(
                    box.min().x(),
                    box.min().y(),
                    box.min().z(),
                    box.max().x() - 1,
                    box.max().y() - 1,
                    box.max().z() - 1,
                    block.defaultBlockState()
                );
            }
        }
        return build;
    }

    static ScenarioBuilder clear(ScenarioBuilder build, VisualComplexFixtures.Fixture fixture) {
        Cuboid region = region(fixture);
        return build.clearArea(
            region.min().x(),
            region.min().y(),
            region.min().z(),
            region.max().x() + 1,
            region.max().y() + 1,
            region.max().z() + 1
        );
    }

    static BlockPos center(VisualComplexFixtures.Fixture fixture) {
        LatticePoint point = fixture.center();
        return new BlockPos(point.x(), point.y(), point.z());
    }

    static ScenarioBuilder flyover(ScenarioBuilder build, VisualComplexFixtures.Fixture fixture) {
        BlockPos center = center(fixture);
        LatticePoint point = fixture.center();
        Cuboid region = region(fixture);
        int span = Math.max(
            Math.max(region.max().x() - region.min().x(), region.max().y() - region.min().y()),
            region.max().z() - region.min().z()
        );
        double distance = Math.max(10.0D, span * 0.9D);
        double cameraY = point.y() + 0.5D + Math.max(2.0D, span * 0.2D);
        double z = point.z() + 0.5D + distance;
        double westX = point.x() + 0.5D - distance * 0.55D;
        double eastX = point.x() + 0.5D + distance * 0.55D;
        float[] westAngles = cameraAngles(westX, cameraY, z, center);
        float[] eastAngles = cameraAngles(eastX, cameraY - 1.0D, z, center);
        build = build.runCommand("gamemode spectator @a").runCommand("cumulusvisual hud hide")
                .runCommand("cumulusvisual highlights hide").frames(3).teleportPlayer(westX, cameraY, z)
                .yawPitch(westAngles[0], westAngles[1]).frames(4).screenshot("flyover-west").frames(2)
                .teleportPlayer(eastX, cameraY - 1.0D, z).yawPitch(eastAngles[0], eastAngles[1]).frames(4)
                .screenshot("flyover-east").frames(2).screenshot("world").frames(3).checkCaptured("world")
                .runCommand("cumulusvisual highlights show").runCommand("cumulusvisual hud show").frames(3)
                .waitUntil("the HUD returns for the HUD capture", VisualProbe::hudVisible).frames(2);
        return build;
    }

    private static float[] cameraAngles(double x, double y, double z, BlockPos target) {
        double eyeY = y + 1.62D;
        double dx = target.getX() + 0.5D - x;
        double dy = target.getY() + 0.5D - eyeY;
        double dz = target.getZ() + 0.5D - z;
        double ground = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, ground));
        return new float[]{yaw, pitch};
    }

    static String setLine(VisualComplexFixtures.Fixture fixture) {
        return "结构集: " + fixture.id() + " 成员 " + fixture.members().size() + ", 已形成 " + fixture.members().size()
                + ", 声明 " + fixture.claimRows() + ", 预算 " + fixture.claimRows() + "/" + fixture.budgetLimit() + ", 链接 "
                + fixture.links().size();
    }

    private static Cuboid region(VisualComplexFixtures.Fixture fixture) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (VisualComplexFixtures.Member member : fixture.members()) {
            for (Cuboid box : member.bounds().boxes()) {
                minX = Math.min(minX, box.min().x());
                minY = Math.min(minY, box.min().y());
                minZ = Math.min(minZ, box.min().z());
                maxX = Math.max(maxX, box.max().x());
                maxY = Math.max(maxY, box.max().y());
                maxZ = Math.max(maxZ, box.max().z());
            }
        }
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
