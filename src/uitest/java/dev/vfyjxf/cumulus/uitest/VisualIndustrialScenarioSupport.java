package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Block placement, clear and camera helpers for the normal industrial multiblock fixtures. */
final class VisualIndustrialScenarioSupport {

    private VisualIndustrialScenarioSupport() {}

    static ScenarioBuilder place(ScenarioBuilder build, VisualIndustrialFixtures.Fixture fixture) {
        for (VisualIndustrialFixtures.Member member : fixture.members()) {
            Block block = VisualIndustrialFixtures.blockFor(member.role());
            if (member.role() == VisualPartRole.coil && member.label().contains("上")) {
                block = Blocks.COPPER_BLOCK;
            }
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

    static ScenarioBuilder clear(ScenarioBuilder build, VisualIndustrialFixtures.Fixture fixture) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (VisualIndustrialFixtures.Member member : fixture.members()) {
            for (Cuboid box : member.bounds().boxes()) {
                minX = Math.min(minX, box.min().x());
                minY = Math.min(minY, box.min().y());
                minZ = Math.min(minZ, box.min().z());
                maxX = Math.max(maxX, box.max().x());
                maxY = Math.max(maxY, box.max().y());
                maxZ = Math.max(maxZ, box.max().z());
            }
        }
        return build.clearArea(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    static ScenarioBuilder view(ScenarioBuilder build, VisualIndustrialFixtures.Fixture fixture) {
        LatticePoint root = fixture.members().get(0).siting().origin();
        boolean smeltery = fixture.id().endsWith(":smeltery");
        BlockPos target = new BlockPos(root.x(), root.y() + 1, root.z() + (smeltery ? 2 : 1));
        double cameraX = root.x() - (smeltery ? 4.5D : 3.2D);
        double cameraY = root.y() + (smeltery ? 4.5D : 3.8D);
        double cameraZ = root.z() - (smeltery ? 6.5D : 5.0D);
        return build.teleportPlayer(cameraX, cameraY, cameraZ)
                .lookSweepFrames(() -> VisualScenarioSupport.lookAt(target), 10, null)
                .awaitClientChunk((int) Math.floor(cameraX) >> 4, (int) Math.floor(cameraZ) >> 4);
    }
}
