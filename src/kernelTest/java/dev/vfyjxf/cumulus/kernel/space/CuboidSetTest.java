package dev.vfyjxf.cumulus.kernel.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CuboidSetTest {

    @Test
    void emptyCuboidsAreDropped() {
        Cuboid empty = Cuboid.of(2, 0, 2, 2, 5, 5);
        assertTrue(empty.isEmpty());
        assertFalse(empty.contains(LatticePoint.of(2, 1, 2)));
        assertTrue(CuboidSet.of(empty).isEmpty());
    }

    @Test
    void touchingBoxesStayApart() {
        CuboidSet across = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(1, 0, 0, 2, 1, 1));
        assertEquals(2, across.boxCount());
        assertEquals(2L, across.volume());

        CuboidSet stacked = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(0, 1, 0, 1, 2, 1));
        assertEquals(2, stacked.boxCount());
    }

    @Test
    void faceAbutmentStaysApartOnEveryAxis() {
        CuboidSet alongX = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(1, 0, 0, 2, 1, 1));
        CuboidSet alongY = CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1), Cuboid.of(3, 1, 0, 4, 2, 1));
        CuboidSet alongZ = CuboidSet.of(Cuboid.of(5, 0, 0, 6, 1, 1), Cuboid.of(5, 0, 1, 6, 1, 2));

        assertEquals(2, alongX.boxCount());
        assertEquals(2, alongY.boxCount());
        assertEquals(2, alongZ.boxCount());
        assertTrue(alongY.containsAll(CuboidSet.of(Cuboid.of(3, 0, 0, 4, 2, 1))));
        assertTrue(CuboidSet.of(Cuboid.of(3, 0, 0, 4, 2, 1)).containsAll(alongY));
    }

    @Test
    void overlappingBoxesMerge() {
        CuboidSet merged = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 2, 1), Cuboid.of(0, 1, 0, 1, 3, 1));
        assertEquals(1, merged.boxCount());
        assertEquals(3L, merged.volume());
        assertEquals(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 3, 1)), merged);
    }

    @Test
    void nestedBoxAddsNothing() {
        Cuboid outer = Cuboid.of(0, 0, 0, 4, 4, 4);
        Cuboid inner = Cuboid.of(1, 1, 1, 2, 2, 2);
        CuboidSet set = CuboidSet.of(outer, inner);
        assertEquals(outer.volume(), set.volume());
        assertTrue(set.containsAll(CuboidSet.of(outer)));
        assertTrue(CuboidSet.of(outer).containsAll(set));
    }

    @Test
    void boxesAreDisjointAndCanonical() {
        CuboidSet set = CuboidSet.of(
                Cuboid.of(2, 0, 0, 3, 1, 1),
                Cuboid.of(0, 0, 0, 2, 3, 2),
                Cuboid.of(1, 1, 1, 2, 2, 2),
                Cuboid.of(0, 2, 0, 3, 4, 3));
        for (int i = 0; i < set.boxCount(); i++) {
            for (int j = i + 1; j < set.boxCount(); j++) {
                assertFalse(set.boxes().get(i).intersects(set.boxes().get(j)));
            }
        }
        for (int i = 1; i < set.boxCount(); i++) {
            assertTrue(set.boxes().get(i - 1).compareTo(set.boxes().get(i)) < 0);
        }
        assertEquals(set, CuboidSet.of(set.boxes()));
    }

    @Test
    void doorOpeningIsBayMinusFrame() {
        CuboidSet door = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 2, 1));
        CuboidSet frame = CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1));
        CuboidSet opening = door.subtract(frame);
        assertEquals(1, opening.boxCount());
        assertEquals(2L, opening.volume());
        assertEquals(CuboidSet.of(Cuboid.of(3, 1, 0, 5, 2, 1)), opening);
        assertTrue(door.containsAll(opening));
        assertFalse(opening.containsAll(door));
    }

    @Test
    void unionAddsCellsAndIntersectKeepsThem() {
        CuboidSet left = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        CuboidSet right = CuboidSet.of(Cuboid.of(1, 0, 0, 3, 1, 1));
        assertEquals(3L, left.union(right).volume());
        assertEquals(3L, left.union(right).union(left).volume());
        assertEquals(1L, left.intersect(right).volume());
        assertTrue(left.intersects(right));
        assertTrue(left.intersect(CuboidSet.of(Cuboid.of(5, 5, 5, 6, 6, 6))).isEmpty());
    }

    @Test
    void boundsAndTranslation() {
        CuboidSet set = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(4, 5, 6, 7, 8, 9));
        assertEquals(Cuboid.of(0, 0, 0, 7, 8, 9), set.bounds());
        CuboidSet moved = set.translate(3, -1, 2);
        assertNotNull(moved);
        CuboidSet returned = moved.translate(-3, 1, -2);
        assertNotNull(returned);
        assertEquals(set, returned);
        assertEquals(28L, set.volume());
        assertTrue(set.contains(LatticePoint.of(4, 5, 6)));
        assertFalse(set.contains(LatticePoint.of(2, 2, 2)));
        assertNull(CuboidSet.empty().bounds());
        assertEquals(List.of(), CuboidSet.empty().boxes());
    }
}
