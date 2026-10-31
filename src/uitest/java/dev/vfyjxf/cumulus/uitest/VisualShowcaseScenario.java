package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.HashSet;
import java.util.Set;

/** The large all-in-one free-roam showcase: overview, one capture per zone, and a walkable ending. */
@ClientScenario(
    name = "visual.showcase",
    group = "visual",
    tags = {"visual", "showcase", "roam"},
    timeoutMs = 45_000,
    scenarioTimeoutMs = 60_000
)
public final class VisualShowcaseScenario {

    public void define(ScenarioBuilder build) {
        VisualShowcaseFixtures.Showcase plan = VisualShowcaseFixtures.planned();
        String instructions = VisualShowcaseState.instructionLine();
        build = build.guiScale(2).hideGui(false).log(
            "visual.showcase: all complex sets, group systems, satellites and substructures in one free-roam area"
        ).runCommand("cumulusvisual clearall").runCommand("cumulusvisual showcase")
                .teleportPlayer(0.5D, VisualShowcaseFixtures.spawn.y(), 0.5D).yawPitch(180.0F, 10.0F)
                .awaitClientChunk(0, 0)
                .timelineRecord(
                    "showcase-debug",
                    () -> "active=" + VisualShowcaseState.active() + " zones=" + VisualShowcaseState.zoneCount()
                            + " instances=" + VisualProbe.instanceCount() + "/" + plan.instances() + " claims="
                            + VisualProbe.claimCount() + "/" + plan.claims() + " parts="
                            + VisualPartState.parts().size() + "/" + plan.parts() + " compact=" + VisualProbe.compact()
                ).waitUntil("the showcase snapshot holds every zone", () -> {
                    return VisualShowcaseState.active()
                            && VisualProbe.instanceCount() == plan.instances()
                            && VisualProbe.claimCount() == plan.claims()
                            && VisualPartState.parts().size() == plan.parts();
                }).waitUntil("the compact HUD shows the showcase instructions", () -> {
                    return VisualProbe.compact()
                            && VisualProbe.hudVisible()
                            && VisualProbe.hudLines().contains(instructions)
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("展示区: "));
                })
                .check(
                    "the showcase has every planned zone",
                    () -> VisualShowcaseState.zoneCount() == plan.zones().size()
                ).check("the showcase has every planned instance and claim", () -> {
                    return VisualProbe.instanceCount() == plan.instances() && VisualProbe.claimCount() == plan.claims();
                }).check("the showcase has every planned part", () -> VisualPartState.parts().size() == plan.parts())
                .check("the showcase has every planned group", () -> VisualProbe.groups().size() == plan.groups())
                .check("every showcase zone has instances and parts", () -> {
                    for (VisualShowcaseState.Zone zone : VisualShowcaseState.zones()) {
                        if (zone.instanceCount() <= 0 || zone.partCount() <= 0) {
                            return false;
                        }
                    }
                    return true;
                }).check("the zone origins are distinct", () -> {
                    Set<String> origins = new HashSet<>();
                    for (VisualShowcaseFixtures.Zone zone : plan.zones()) {
                        if (!origins.add(zone.origin().toString())) {
                            return false;
                        }
                    }
                    return true;
                }).check("the HUD reports the Chinese showcase totals", () -> {
                    return VisualProbe.hudLines().contains(
                        "展示区: " + plan.zones().size() + " 个区域, " + plan.instances() + " 实例, " + plan.parts() + " 部件"
                    ) && VisualProbe.hudLines().contains(instructions);
                }).screenshot("overview-hud").frames(3).checkCaptured("overview-hud").hideGui(true)
                .runCommand("cumulusvisual tutorial hide").runCommand("cumulusvisual hud hide")
                .runCommand("cumulusvisual highlights hide").runCommand("gamemode spectator @a").frames(3);

        for (VisualShowcaseFixtures.Zone zone : plan.zones()) {
            Cuboid area = zone.area();
            int zx = (area.min().x() + area.max().x()) / 2;
            int zz = (area.min().z() + area.max().z()) / 2;
            int zy = (area.min().y() + area.max().y()) / 2;
            int span = Math.max(area.max().x() - area.min().x(), area.max().z() - area.min().z());
            boolean industrial = zone.id().equals("smeltery") || zone.id().equals("ebf");
            double distance = industrial ? Math.max(8.0D, span * 0.8D) : Math.max(12.0D, span * 0.95D);
            double cameraX = zx + 0.5D + (industrial ? -distance * 0.65D : distance * 0.55D);
            double cameraY = area.max().y() + Math.max(industrial ? 4.0D : 5.0D, span * 0.25D);
            double cameraZ = zz + 0.5D + (industrial ? -distance : distance);
            float[] cameraAngles = angles(cameraX, cameraY, cameraZ, zx, zy, zz);
            build = build.teleportPlayer(cameraX, cameraY, cameraZ)
                    .awaitClientChunk(
                        Math.floorDiv((int) Math.floor(cameraX), 16),
                        Math.floorDiv((int) Math.floor(cameraZ), 16)
                    ).awaitClientChunk(area.min().x() >> 4, area.min().z() >> 4)
                    .awaitClientChunk(area.max().x() >> 4, area.max().z() >> 4)
                    .yawPitch(cameraAngles[0], cameraAngles[1]).frames(6).screenshot("zone-" + zone.id()).frames(2)
                    .checkCaptured("zone-" + zone.id());
        }

        Cuboid overviewArea = overallRegion(plan);
        int centerX = (overviewArea.min().x() + overviewArea.max().x()) / 2;
        int centerZ = (overviewArea.min().z() + overviewArea.max().z()) / 2;
        int centerY = (overviewArea.min().y() + overviewArea.max().y()) / 2;
        build = build.teleportPlayer(0.5D, 126.0D, 20.5D).awaitClientChunk(0, 1)
                .awaitClientChunk(overviewArea.min().x() >> 4, overviewArea.min().z() >> 4)
                .awaitClientChunk(overviewArea.max().x() >> 4, overviewArea.max().z() >> 4)
                .yawPitch(
                    angles(0.5D, 126.0D, 20.5D, centerX, centerY, centerZ)[0],
                    angles(0.5D, 126.0D, 20.5D, centerX, centerY, centerZ)[1]
                ).frames(10).screenshot("overview").frames(2).checkCaptured("overview");

        build = build.hideGui(false).runCommand("cumulusvisual hud show").runCommand("cumulusvisual highlights show")
                .runCommand("gamemode creative @a").teleportPlayer(0.5D, VisualShowcaseFixtures.spawn.y(), 0.5D)
                .yawPitch(180.0F, 10.0F).frames(4)
                .waitUntil("the free-roam overlook returns with compact instructions", () -> {
                    return VisualProbe.hudVisible()
                            && VisualProbe.compact()
                            && VisualProbe.hudLines().contains(instructions);
                }).check("free roam starts at the overlook overview", () -> VisualProbe.compact())
                .check(
                    "inspect is available in free roam",
                    () -> VisualPartState.partAt(VisualShowcaseFixtures.spawn) != null
                            || !VisualPartState.parts().isEmpty()
                ).screenshot("free-roam").frames(3).checkCaptured("free-roam");
        if (!keepOpen()) {
            Cuboid area = overallRegion(plan);
            build = build.clearArea(
                area.min().x() - 8,
                99,
                area.min().z() - 8,
                area.max().x() + 8,
                area.max().y() + 8,
                area.max().z() + 8
            );
            build = build.runCommand("cumulusvisual clearall").runCommand("cumulusvisual hud detail")
                    .runCommand("cumulusvisual hud show").runCommand("cumulusvisual highlights show");
        }
    }

    private static boolean hasChinese(String text) {
        return text != null && text.codePoints().anyMatch(code -> code >= 0x4E00 && code <= 0x9FFF);
    }

    private static boolean keepOpen() {
        return Boolean.parseBoolean(System.getProperty("cloudlib.uitest.keepOpen", "false"));
    }

    private static Cuboid overallRegion(VisualShowcaseFixtures.Showcase plan) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (VisualShowcaseFixtures.Zone zone : plan.zones()) {
            minX = Math.min(minX, zone.area().min().x());
            minY = Math.min(minY, zone.area().min().y());
            minZ = Math.min(minZ, zone.area().min().z());
            maxX = Math.max(maxX, zone.area().max().x());
            maxY = Math.max(maxY, zone.area().max().y());
            maxZ = Math.max(maxZ, zone.area().max().z());
        }
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static float[] angles(double x, double y, double z, double targetX, double targetY, double targetZ) {
        double eyeY = y + 1.62D;
        double dx = targetX + 0.5D - x;
        double dy = targetY + 0.5D - eyeY;
        double dz = targetZ + 0.5D - z;
        double ground = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, ground));
        return new float[]{yaw, pitch};
    }
}
