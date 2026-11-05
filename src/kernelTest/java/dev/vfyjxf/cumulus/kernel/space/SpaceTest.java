package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.World;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceTest {

    @Test
    void latticePackingRoundTrips() {
        for (LatticePoint point : List.of(
            LatticePoint.origin,
            LatticePoint.of(-1, -1, -1),
            LatticePoint.of(LatticePoint.maxXz, LatticePoint.maxY, LatticePoint.maxXz),
            LatticePoint.of(LatticePoint.minXz, LatticePoint.minY, LatticePoint.minXz)
        )) {
            assertEquals(point, point.key().point());
        }
        assertThrows(IllegalArgumentException.class, () -> LatticePoint.of(LatticePoint.maxXz + 1, 0, 0));
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
        CuboidSet shell = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3)).subtract(Cuboid.of(1, 1, 1, 2, 2, 2));
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
        assertEquals(Orientation.identity, Orientation.of(Turn.none, Tilt.level, Flip.none));
        for (Orientation orientation : all) {
            assertEquals(Orientation.identity, orientation.inverse().compose(orientation));
            assertEquals(orientation, orientation.compose(Orientation.identity));
        }
    }

    @Test
    void orientationMirrorKeepsVolumeAndMapsCorners() {
        Orientation mirror = Orientation.of(Turn.none, Tilt.level, Flip.frontBack);
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
        Orientation yaw = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);
        assertEquals(Heading.east, yaw.apply(Heading.north));
        Orientation cap = Orientation.of(Turn.none, Tilt.inverted, Flip.none);
        assertEquals(Heading.down, cap.apply(Heading.up));
        Orientation tipped = yaw.compose(cap);
        assertEquals(Heading.west, tipped.apply(Heading.north));
        assertEquals(Heading.down, tipped.apply(Heading.up));
        assertFalse(tipped.isMirror());
        assertTrue(Orientation.of(Turn.none, Tilt.level, Flip.leftRight).isMirror());
    }

    @Test
    void placementLawsAndOffLattice() {
        Placement p = new Placement(Orientation.of(Turn.clockwise, Tilt.level, Flip.none), LatticePoint.of(10, 64, -5));
        Placement inverse = p.inverse();
        assertNotNull(inverse);
        assertEquals(Placement.identity, inverse.compose(p));
        assertEquals(LatticePoint.of(10, 64, -5), p.apply(LatticePoint.origin));
        Placement edge = new Placement(Orientation.identity, LatticePoint.of(LatticePoint.minXz, 0, 0));
        assertNull(edge.inverse());
    }

    @Test
    void sitingRoundTrips() {
        Siting siting = Siting.of(LatticePoint.of(10, 64, -5), Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
        LatticePoint local = LatticePoint.of(3, 0, 0);
        LatticePoint world = siting.toWorld(local);
        assertNotNull(world);
        assertEquals(local, siting.toLocal(world));
        CuboidSet set = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1));
        CuboidSet worldSet = siting.toWorld(set);
        assertNotNull(worldSet);
        assertTrue(Objects.requireNonNull(siting.toLocal(worldSet), "local").contentEquals(set));
    }

    @Test
    void sitingComposeWithIdentityRootAddsTheRelativeOrigin() {
        Siting root = Siting.of(LatticePoint.of(120, 70, -40), Orientation.identity);
        Siting negative = root.compose(Siting.of(LatticePoint.of(-3, 2, -7), Orientation.identity));
        assertNotNull(negative);
        assertEquals(LatticePoint.of(117, 72, -47), negative.origin());
        assertEquals(Placement.identity, negative.placement());
        Siting positive = root.compose(Siting.of(LatticePoint.of(4, -1, 6), Orientation.identity));
        assertNotNull(positive);
        assertEquals(LatticePoint.of(124, 69, -34), positive.origin());
        assertEquals(Placement.identity, positive.placement());
    }

    @Test
    void sitingComposeWithHalfTurnRootRotatesTheRelativeOrigin() {
        Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);
        Siting root = Siting.of(LatticePoint.of(100, 64, -20), half);
        Siting composed = root.compose(Siting.of(LatticePoint.of(-4, 1, 7), Orientation.identity));
        assertNotNull(composed);
        assertEquals(LatticePoint.of(104, 65, -27), composed.origin());
        assertEquals(half, composed.placement().orientation());
        assertEquals(LatticePoint.origin, composed.placement().offset());
        assertEquals(LatticePoint.of(102, 68, -22), composed.toWorld(LatticePoint.of(2, 3, -5)));
    }

    @Test
    void sitingComposeWithClockwiseRootComposesTheRelativeOrientation() {
        Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);
        Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);
        Siting root = Siting.of(LatticePoint.of(100, 64, -20), clockwise);
        Siting relative = Siting.of(LatticePoint.of(-2, 3, -4), half);
        Siting composed = root.compose(relative);
        assertNotNull(composed);
        assertEquals(LatticePoint.of(104, 67, -22), composed.origin());
        assertEquals(Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none), composed.placement().orientation());
        LatticePoint local = LatticePoint.of(1, 2, 3);
        LatticePoint throughRoot = root.toWorld(Objects.requireNonNull(relative.toWorld(local), "relative image"));
        assertEquals(throughRoot, composed.toWorld(local));
    }

    @Test
    void sitingComposeReturnsNullWhenAnImageLeavesTheLattice() {
        Siting high = Siting.of(LatticePoint.of(LatticePoint.maxXz - 1, 0, 0), Orientation.identity);
        assertNull(high.compose(Siting.of(LatticePoint.of(2, 0, 0), Orientation.identity)));
        Siting low = Siting.of(LatticePoint.of(LatticePoint.minXz + 1, 0, 0), Orientation.identity);
        assertNull(low.compose(Siting.of(LatticePoint.of(-2, 0, 0), Orientation.identity)));
        Siting mirror = Siting.of(LatticePoint.origin, Orientation.of(Turn.none, Tilt.level, Flip.frontBack));
        Siting extreme = Siting.of(
            LatticePoint.origin,
            new Placement(Orientation.identity, LatticePoint.of(LatticePoint.minXz, 0, 0))
        );
        assertNull(mirror.compose(extreme));
    }

    @Test
    void composeCountsANonZeroRootOffsetOnce() {
        Siting root = Siting
                .of(LatticePoint.of(10, 0, 0), new Placement(Orientation.identity, LatticePoint.of(0, 1, 0)));
        Siting relative = Siting.of(LatticePoint.origin, Orientation.identity);
        Siting composed = root.compose(relative);
        assertNotNull(composed);
        assertEquals(LatticePoint.of(10, 1, 0), composed.origin());
        assertEquals(LatticePoint.of(10, 1, 0), composed.toWorld(LatticePoint.origin));
    }

    @Test
    void composeMatchesTheSequentialTransform() {
        Orientation quarter = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);
        Siting root = Siting.of(LatticePoint.of(100, 64, -20), new Placement(quarter, LatticePoint.of(2, -1, 3)));
        Siting relative = Siting
                .of(LatticePoint.of(-4, 1, 7), new Placement(Orientation.identity, LatticePoint.of(1, 0, -2)));
        Siting composed = root.compose(relative);
        assertNotNull(composed);
        for (LatticePoint local : List.of(
            LatticePoint.origin,
            LatticePoint.of(1, 2, 3),
            LatticePoint.of(-4, 7, -2),
            LatticePoint.of(11, -5, 0)
        )) {
            LatticePoint sequential = root.toWorld(Objects.requireNonNull(relative.toWorld(local), "relative image"));
            assertEquals(sequential, composed.toWorld(local));
        }
    }

    @Test
    void composeIsAssociativeOnSampledPoints() {
        Random random = new Random(20261101L);
        for (int trial = 0; trial < 16; trial++) {
            Siting a = randomSiting(random);
            Siting b = randomSiting(random);
            Siting c = randomSiting(random);
            Siting left = Objects.requireNonNull(Objects.requireNonNull(a.compose(b), "a∘b").compose(c), "(a∘b)∘c");
            Siting right = Objects.requireNonNull(a.compose(Objects.requireNonNull(b.compose(c), "b∘c")), "a∘(b∘c)");
            assertEquals(left, right);
            for (int sample = 0; sample < 4; sample++) {
                LatticePoint local = LatticePoint
                        .of(random.nextInt(17) - 8, random.nextInt(17) - 8, random.nextInt(17) - 8);
                assertEquals(left.toWorld(local), right.toWorld(local));
            }
        }
    }

    private static Siting randomSiting(Random random) {
        LatticePoint origin = LatticePoint
                .of(random.nextInt(129) - 64, random.nextInt(65) - 32, random.nextInt(129) - 64);
        Orientation orientation = Orientation.all().get(random.nextInt(48));
        LatticePoint offset = LatticePoint.of(random.nextInt(17) - 8, random.nextInt(17) - 8, random.nextInt(17) - 8);
        return Siting.of(origin, new Placement(orientation, offset));
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
        assertEquals(LocusFrame.local, local.frame());
        assertThrows(IllegalArgumentException.class, () -> Locus.local(CuboidSet.empty()));
        Locus key = Locus.key(new InstanceId(World.of(NamespacedId.of("w")), 1), NamespacedId.of("k"));
        assertEquals(LocusFrame.key, key.frame());
        assertThrows(
            IllegalArgumentException.class,
            () -> new Locus(LocusFrame.definition, CuboidSet.empty(), null, null)
        );
    }

    @Test
    void locusMapMapsLocalAndWorld() {
        Siting siting = Siting.of(LatticePoint.of(10, 64, -5), Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
        Locus local = Locus.local(CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1)));
        Locus world = LocusMap.of(siting).toWorld(local);
        assertNotNull(world);
        assertEquals(LocusFrame.world, world.frame());
        assertTrue(
            Objects.requireNonNull(LocusMap.of(siting).toLocal(world), "local").cells().contentEquals(local.cells())
        );
        assertNull(
            LocusMap.of(siting)
                    .toWorld(Locus.key(new InstanceId(World.of(NamespacedId.of("w")), 1), NamespacedId.of("k")))
        );
    }

    @Test
    void cuboidSetEqualityIsByCellsNotByDecomposition() {
        CuboidSet split = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(1, 0, 0, 2, 1, 1));
        CuboidSet whole = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        assertEquals(whole, split);
        assertEquals(whole.hashCode(), split.hashCode());
        assertEquals(whole, CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)));
        assertNotEquals(whole, CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));
    }
}
