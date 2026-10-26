package dev.vfyjxf.cumulus.kernel.space;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.World;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
class SpaceTest {

    @Test
    void latticePackingRoundTrips() {
        for (LatticePoint point : List.of(LatticePoint.ORIGIN, LatticePoint.of(-1, -1, -1),
                LatticePoint.of(LatticePoint.MAX_XZ, LatticePoint.MAX_Y, LatticePoint.MAX_XZ),
                LatticePoint.of(LatticePoint.MIN_XZ, LatticePoint.MIN_Y, LatticePoint.MIN_XZ))) {
            assertEquals(point, point.key().point());
        }
        assertThrows(IllegalArgumentException.class,
                () -> LatticePoint.of(LatticePoint.MAX_XZ + 1, 0, 0));
    }

    @Test
    void cuboidIsHalfOpen() {
        Cuboid box = Cuboid.of(0, 0, 0, 2, 3, 4);
        assertEquals(24, box.volume());
        assertFalse(box.contains(LatticePoint.of(2, 0, 0)));
        assertTrue(box.contains(LatticePoint.of(1, 2, 3)));
        Cuboid empty = Cuboid.of(1, 1, 1, 1, 2, 2);
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.volume());
        assertNull(empty.intersect(Cuboid.of(0, 0, 0, 2, 2, 2)));
    }

    @Test
    void cuboidSetKeepsTouchingBoxesSeparate() {
        CuboidSet set = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(1, 0, 0, 2, 1, 1));
        assertEquals(2, set.boxCount());
        assertEquals(2, set.volume());
    }

    @Test
    void cuboidSetMergesOverlap() {
        CuboidSet set = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1), Cuboid.of(1, 0, 0, 3, 1, 1));
        assertEquals(3, set.boxCount());
        assertEquals(3, set.volume());
    }

    @Test
    void cuboidSetSubtractShell() {
        CuboidSet shell = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3))
                .subtract(Cuboid.of(1, 1, 1, 2, 2, 2));
        assertEquals(26, shell.volume());
        assertFalse(shell.contains(LatticePoint.of(1, 1, 1)));
        assertTrue(shell.contains(LatticePoint.of(0, 1, 1)));
        assertTrue(shell.containsAll(shell));
        assertEquals(27, shell.union(CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2))).volume());
    }

    @Test
    void orientationGroupHas48DistinctElements() {
        List<Orientation> all = Orientation.all();
        assertEquals(48, all.size());
        assertEquals(48, new HashSet<>(all).size());
        assertEquals(Orientation.IDENTITY, Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.NONE));
        for (Orientation orientation : all) {
            assertEquals(Orientation.IDENTITY, orientation.inverse().compose(orientation));
            assertEquals(orientation, orientation.compose(Orientation.IDENTITY));
        }
    }

    @Test
    void orientationMirrorKeepsVolumeAndMapsCorners() {
        Orientation mirror = Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.FRONT_BACK);
        Cuboid image = mirror.apply(Cuboid.of(-1, 0, -1, 1, 1, 1));
        assertNotNull(image);
        assertEquals(Cuboid.of(0, 0, -1, 2, 1, 1), image);
        assertEquals(4, image.volume());
        for (Orientation orientation : Orientation.all()) {
            Cuboid box = orientation.apply(Cuboid.of(0, 0, 0, 1, 2, 4));
            assertNotNull(box);
            assertEquals(8, box.volume());
        }
    }

    @Test
    void turnAndTiltCompose() {
        Orientation yaw = Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE);
        assertEquals(Heading.EAST, yaw.apply(Heading.NORTH));
        Orientation cap = Orientation.of(Turn.NONE, Tilt.INVERTED, Flip.NONE);
        assertEquals(Heading.DOWN, cap.apply(Heading.UP));
        Orientation tipped = yaw.compose(cap);
        assertEquals(Heading.WEST, tipped.apply(Heading.NORTH));
        assertEquals(Heading.DOWN, tipped.apply(Heading.UP));
        assertFalse(tipped.isMirror());
        assertTrue(Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.LEFT_RIGHT).isMirror());
    }

    @Test
    void placementLawsAndOffLattice() {
        Placement p = new Placement(Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE),
                LatticePoint.of(10, 64, -5));
        Placement inverse = p.inverse();
        assertNotNull(inverse);
        assertEquals(Placement.IDENTITY, inverse.compose(p));
        assertEquals(LatticePoint.of(10, 64, -5), p.apply(LatticePoint.ORIGIN));
        Placement edge = new Placement(Orientation.IDENTITY, LatticePoint.of(LatticePoint.MIN_XZ, 0, 0));
        assertNull(edge.inverse());
    }

    @Test
    void sitingRoundTrips() {
        Siting siting = Siting.of(LatticePoint.of(10, 64, -5),
                Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE));
        LatticePoint local = LatticePoint.of(3, 0, 0);
        LatticePoint world = siting.toWorld(local);
        assertNotNull(world);
        assertEquals(local, siting.toLocal(world));
        CuboidSet set = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1));
        CuboidSet worldSet = siting.toWorld(set);
        assertNotNull(worldSet);
        assertTrue(siting.toLocal(worldSet).contentEquals(set));
    }

    @Test
    void sectionKeyFloorsNegativeCoordinates() {
        World world = World.of(NamespacedId.of("minecraft", "overworld"));
        SectionKey key = SectionKey.of(world, 0, LatticePoint.of(-1, -1, -1), 16);
        assertEquals(-1, key.x());
        assertEquals(-1, key.y());
        assertEquals(-1, key.z());
        assertEquals(key, key.sectionOf(LatticePoint.of(-1, -1, -1), 16));
    }

    @Test
    void locusFramesValidate() {
        Locus local = Locus.local(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));
        assertEquals(LocusFrame.LOCAL, local.frame());
        assertThrows(IllegalArgumentException.class, () -> Locus.local(CuboidSet.empty()));
        Locus key = Locus.key(new InstanceId(
                World.of(NamespacedId.of("w")), 1), NamespacedId.of("k"));
        assertEquals(LocusFrame.KEY, key.frame());
        assertThrows(IllegalArgumentException.class, () -> new Locus(
                LocusFrame.DEFINITION, CuboidSet.empty(), null, null));
    }

    @Test
    void locusMapMapsLocalAndWorld() {
        Siting siting = Siting.of(LatticePoint.of(10, 64, -5),
                Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE));
        Locus local = Locus.local(CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1)));
        Locus world = LocusMap.of(siting).toWorld(local);
        assertNotNull(world);
        assertEquals(LocusFrame.WORLD, world.frame());
        assertTrue(LocusMap.of(siting).toLocal(world).cells().contentEquals(local.cells()));
        assertNull(LocusMap.of(siting).toWorld(Locus.key(
                new InstanceId(
                        World.of(NamespacedId.of("w")), 1), NamespacedId.of("k"))));
    }

    @Test
    void cuboidSetEqualityIsByCellsNotByDecomposition() {
        CuboidSet split = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1),
                Cuboid.of(1, 0, 0, 2, 1, 1));
        CuboidSet whole = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        assertEquals(whole, split);
        assertEquals(whole.hashCode(), split.hashCode());
        assertEquals(whole, CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)));
        assertNotEquals(whole, CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));
    }
}
