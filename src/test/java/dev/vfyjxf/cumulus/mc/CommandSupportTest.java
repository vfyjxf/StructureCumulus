package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
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

    @Test
    void theAnchorCellIsTheOneCellCaptureRegion() {
        assertEquals(Cuboid.of(3, 4, 5, 4, 5, 6), CommandSupport.anchorCell(LatticePoint.of(3, 4, 5)));
    }

    @Test
    void theLastLatticeCornerHasNoAnchorCell() {
        assertNull(CommandSupport.anchorCell(LatticePoint.of(LatticePoint.maxXz, 0, 0)));
    }

    @Test
    void rotatesSitedBoundsThroughTheSiting() {
        Blueprint blueprint = boundsBlueprint();
        LatticePoint origin = LatticePoint.of(10, 0, 10);
        assertEquals(
            Cuboid.of(10, 0, 10, 11, 1, 12),
            CommandSupport
                    .sitedBounds(blueprint, Siting.of(origin, Orientation.of(Turn.clockwise, Tilt.level, Flip.none)))
        );
        assertEquals(
            Cuboid.of(9, 0, 10, 11, 1, 11),
            CommandSupport.sitedBounds(blueprint, Siting.of(origin, Orientation.of(Turn.half, Tilt.level, Flip.none)))
        );
    }

    @Test
    void keepsIdentitySitedBoundsAtTheTranslation() {
        assertEquals(
            Cuboid.of(10, 0, 10, 12, 1, 11),
            CommandSupport.sitedBounds(boundsBlueprint(), Siting.of(LatticePoint.of(10, 0, 10), Orientation.identity))
        );
    }

    private static Blueprint boundsBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay bay = Bay.of(
            NamespacedId.of("test", "bounds_bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(NamespacedId.of("test", "bounds")).bounds(bounds)
                .check(
                    Check.of(
                        NamespacedId.of("test", "bounds_anchor"),
                        bay,
                        Focuses.occupant(LatticePoint.of(0, 0, 0)),
                        Expectations.anyOccupant()
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("test", "bounds_other"),
                        bay,
                        Focuses.occupant(LatticePoint.of(1, 0, 0)),
                        Expectations.anyOccupant()
                    )
                ).build();
    }
}
