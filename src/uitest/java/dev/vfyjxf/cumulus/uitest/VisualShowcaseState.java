package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** The staged all-in-one showcase: zone metadata and the active free-roam state. */
public final class VisualShowcaseState {

    public record Zone(
        String id,
        String displayName,
        String kind,
        LatticePoint origin,
        Cuboid area,
        int instanceCount,
        int claimCount,
        int partCount
    ) {}

    private static volatile List<Zone> zones = List.of();
    private static volatile boolean active;

    private VisualShowcaseState() {}

    static void activate(List<Zone> nextZones) {
        zones = List.copyOf(nextZones);
        active = true;
    }

    static void clear(ServerLevel level) {
        zones = List.of();
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static List<Zone> zones() {
        return zones;
    }

    public static int zoneCount() {
        return zones.size();
    }

    public static int totalInstances() {
        int total = 0;
        for (Zone zone : zones) {
            total += zone.instanceCount();
        }
        return total;
    }

    public static int totalClaims() {
        int total = 0;
        for (Zone zone : zones) {
            total += zone.claimCount();
        }
        return total;
    }

    public static int totalParts() {
        int total = 0;
        for (Zone zone : zones) {
            total += zone.partCount();
        }
        return total;
    }

    public static String instructionLine() {
        return "展示区: 右键部件查看详情；输入 /cumulusvisual showcase 重置";
    }
}
