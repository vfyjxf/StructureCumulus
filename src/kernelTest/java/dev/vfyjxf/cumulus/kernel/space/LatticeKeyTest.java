package dev.vfyjxf.cumulus.kernel.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatticeKeyTest {

    private static final List<LatticePoint> SAMPLES = List.of(
            LatticePoint.ORIGIN,
            LatticePoint.of(1, 0, 0),
            LatticePoint.of(0, 1, 0),
            LatticePoint.of(0, 0, 1),
            LatticePoint.of(0, -1, 0),
            LatticePoint.of(LatticePoint.MAX_XZ, LatticePoint.MAX_Y, LatticePoint.MIN_XZ),
            LatticePoint.of(LatticePoint.MIN_XZ, LatticePoint.MIN_Y, LatticePoint.MAX_XZ));

    @Test
    void packingRoundTrips() {
        for (LatticePoint point : SAMPLES) {
            assertEquals(point, point.key().toPoint());
            assertEquals(point, LatticeKey.of(point).toPoint());
        }
    }

    @Test
    void signBitsDoNotCollide() {
        assertNotEquals(
                LatticePoint.of(33_554_431, 0, 0).key(),
                LatticePoint.of(-33_554_431, 0, 0).key());
        assertNotEquals(
                LatticePoint.of(0, 2_047, 0).key(),
                LatticePoint.of(0, -2_047, 0).key());
        assertEquals(LatticePoint.of(0, 1, 0).key().packed(), 1L);
        assertEquals(LatticePoint.of(0, -1, 0).key().packed(), 4095L);
        assertEquals(LatticePoint.ORIGIN.key().packed(), 0L);
    }

    @Test
    void naturalOrderMatchesLatticePointOrder() {
        List<LatticePoint> points = new ArrayList<>(SAMPLES);
        points.add(LatticePoint.of(0, 0, 1));
        points.add(LatticePoint.of(-1, 0, 0));
        Collections.reverse(points);

        List<LatticePoint> byPoint = new ArrayList<>(points);
        byPoint.sort(null);
        List<LatticeKey> byKey = new ArrayList<>();
        for (LatticePoint point : points) {
            byKey.add(point.key());
        }
        byKey.sort(null);

        List<LatticeKey> expected = new ArrayList<>();
        for (LatticePoint point : byPoint) {
            expected.add(point.key());
        }
        assertEquals(expected, byKey);

        List<LatticePoint> layer = List.of(
                LatticePoint.of(2, 0, -1),
                LatticePoint.of(-1, 0, -1),
                LatticePoint.of(0, 0, 0));
        List<LatticeKey> ordered = new ArrayList<>();
        for (LatticePoint point : layer) {
            ordered.add(point.key());
        }
        ordered.sort(null);
        assertEquals(List.of(
                LatticePoint.of(-1, 0, -1).key(),
                LatticePoint.of(2, 0, -1).key(),
                LatticePoint.of(0, 0, 0).key()), ordered);
    }

    @Test
    void naturalOrderIsNotPackedOrder() {
        LatticeKey above = LatticePoint.of(0, 1, 0).key();
        LatticeKey below = LatticePoint.of(0, -1, 0).key();
        assertTrue(below.packed() > above.packed());
        assertTrue(below.compareTo(above) < 0);
        assertEquals(0, LatticePoint.of(4, -7, 9).key().compareTo(LatticePoint.of(4, -7, 9).key()));
    }

    @Test
    void sectionKeysUseFloorDivision() {
        NamespacedId dimension = NamespacedId.of("minecraft", "overworld");
        assertEquals(4, SectionKey.of(dimension, LatticePoint.of(0, 64, 0), 16).y());
        assertEquals(-1, SectionKey.of(dimension, LatticePoint.of(0, -1, 0), 16).y());
        assertEquals(-1, SectionKey.of(dimension, LatticePoint.of(-1, 0, 0), 16).x());
        assertTrue(SectionKey.of(dimension, LatticePoint.of(0, 0, 0), 16)
                .compareTo(SectionKey.of(dimension, LatticePoint.of(16, 0, 0), 16)) < 0);
    }

    @Test
    void chunkGridOfNegativeOrigin() {
        ChunkGrid grid = new ChunkGrid(16, LatticePoint.of(0, -64, 0));
        assertEquals(8, grid.chunkOf(LatticePoint.of(0, 64, 0)).cy());
        assertEquals(-1, grid.chunkOf(LatticePoint.of(0, -65, 0)).cy());
        Cuboid bounds = grid.boundsOf(grid.chunkOf(LatticePoint.of(3, 64, 3)));
        assertEquals(Cuboid.of(0, 64, 0, 16, 80, 16), bounds);
        assertTrue(bounds.contains(LatticePoint.of(3, 64, 3)));
        assertNotNull(grid.boundsOf(new ChunkKey(0, 8, 0)));
        assertEquals(1, grid.chunkCount(Cuboid.of(0, 64, 0, 4, 70, 4)));
        assertEquals(4, grid.chunksOf(Cuboid.of(0, 64, 0, 17, 65, 17)).size());
    }
}
