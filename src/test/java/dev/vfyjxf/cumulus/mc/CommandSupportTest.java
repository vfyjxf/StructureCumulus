package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandSupportTest {

    @Test
    void normalizesTwoInclusiveCornersToAHalfOpenBox() {
        assertEquals(Cuboid.of(1, 2, 3, 4, 5, 6), CommandSupport.box(new BlockPos(3, 4, 5), new BlockPos(1, 2, 3)));
    }

    @Test
    void treatsEqualCornersAsOneCell() {
        assertEquals(Cuboid.of(7, 8, 9, 8, 9, 10), CommandSupport.box(new BlockPos(7, 8, 9), new BlockPos(7, 8, 9)));
    }

    @Test
    void keepsTheLastInLatticeInclusiveCorner() {
        assertEquals(
            Cuboid.of(0, LatticePoint.maxY - 1, 0, 1, LatticePoint.maxY, 1),
            CommandSupport.box(new BlockPos(0, LatticePoint.maxY - 1, 0), new BlockPos(0, LatticePoint.maxY - 1, 0))
        );
        assertEquals(
            Cuboid.of(LatticePoint.maxXz - 1, 0, 0, LatticePoint.maxXz, 1, 1),
            CommandSupport.box(new BlockPos(LatticePoint.maxXz - 1, 0, 0), new BlockPos(LatticePoint.maxXz - 1, 0, 0))
        );
    }

    @Test
    void rejectsBoxesThatLeaveTheLattice() {
        assertNull(CommandSupport.box(new BlockPos(0, LatticePoint.maxY, 0), new BlockPos(0, LatticePoint.maxY, 0)));
        assertNull(CommandSupport.box(new BlockPos(LatticePoint.maxXz, 0, 0), new BlockPos(LatticePoint.maxXz, 0, 0)));
        assertNull(
            CommandSupport.box(new BlockPos(LatticePoint.minXz - 1, 0, 0), new BlockPos(LatticePoint.minXz - 1, 0, 0))
        );
        assertNull(
            CommandSupport.box(new BlockPos(0, LatticePoint.minY - 1, 0), new BlockPos(0, LatticePoint.minY - 1, 0))
        );
    }
}
