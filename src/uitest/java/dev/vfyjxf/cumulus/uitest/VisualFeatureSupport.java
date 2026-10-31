package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Shared setup/teardown for the ten Chinese feature scenarios. */
final class VisualFeatureSupport {

    private VisualFeatureSupport() {}

    static ScenarioBuilder start(ScenarioBuilder build, BlockPos origin, String log) {
        return build.guiScale(2).hideGui(false).log(log).runCommand("cumulusvisual clearall")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState())
                .fill(-1, 100, 5, 1, 100, 6, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a");
    }

    static ScenarioBuilder view(ScenarioBuilder build, BlockPos target) {
        return build.teleportPlayer(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 5.5D)
                .yawPitch(180.0F, 22.976944F).awaitClientChunk(target.getX() >> 4, target.getZ() >> 4);
    }

    static ScenarioBuilder finish(ScenarioBuilder build, BlockPos origin) {
        return build.runCommand("cumulusvisual clearall").fill(-1, 100, 5, 1, 100, 6, Blocks.AIR.defaultBlockState())
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.AIR.defaultBlockState());
    }

    static boolean hasLine(String prefix) {
        return VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith(prefix));
    }
}
