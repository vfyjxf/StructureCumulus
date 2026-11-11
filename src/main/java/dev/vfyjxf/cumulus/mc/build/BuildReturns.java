package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Returns a rollback's items to the source; a remainder spills at the cells as {@code SC-BLD-0006}. */
final class BuildReturns {

    static List<Finding> insertAll(
        ServerLevel level,
        ItemSource source,
        List<ItemStack> consumed,
        @Nullable Cuboid where,
        Phase phase
    ) {
        List<Finding> findings = new ArrayList<>();
        for (ItemStack stack : consumed) {
            ItemStack remainder = source.insert(stack.copy(), ExtractionMode.execute);
            if (remainder.isEmpty()) {
                continue;
            }
            spawn(level, remainder, where);
            findings.add(
                BuildFindings.at(
                    BuildCodes.incompleteRollbackEntry,
                    phase,
                    remainder.getCount() + " " + remainder.getItem() + " could not be returned",
                    where
                )
            );
        }
        return findings;
    }

    private static void spawn(ServerLevel level, ItemStack stack, @Nullable Cuboid where) {
        if (where == null) {
            return;
        }
        LatticePoint cell = where.min();
        ItemEntity entity = new ItemEntity(level, cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5, stack);
        level.addFreshEntity(entity);
    }

    private BuildReturns() {}
}
