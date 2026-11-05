package dev.vfyjxf.cumulus.kernel.space;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidSetOrderTest {

    @Test
    void differentDecompositionsCompareEqual() {
        CuboidSet split = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(1, 0, 0, 2, 1, 1));
        CuboidSet whole = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        assertEquals(whole, split);
        assertEquals(whole.hashCode(), split.hashCode());
        assertEquals(0, whole.compareTo(split));
        assertEquals(0, split.compareTo(whole));
    }

    @Test
    void theOrderDoesNotDependOnTheDecomposition() {
        CuboidSet lShapeColumns = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 2, 1), Cuboid.of(1, 0, 0, 2, 1, 1));
        CuboidSet lShapeRows = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1), Cuboid.of(0, 1, 0, 1, 2, 1));
        CuboidSet shellCut = CuboidSet.of(Cuboid.of(4, 0, 0, 7, 3, 3)).subtract(Cuboid.of(5, 1, 1, 6, 2, 2));
        CuboidSet shellBoxes = CuboidSet.of(
            Cuboid.of(4, 0, 0, 7, 1, 3),
            Cuboid.of(4, 2, 0, 7, 3, 3),
            Cuboid.of(4, 1, 0, 7, 2, 1),
            Cuboid.of(4, 1, 2, 7, 2, 3),
            Cuboid.of(4, 1, 1, 5, 2, 2),
            Cuboid.of(6, 1, 1, 7, 2, 2)
        );
        CuboidSet slabSplit = CuboidSet.of(Cuboid.of(0, 4, 4, 2, 5, 6), Cuboid.of(0, 5, 4, 2, 6, 6));
        CuboidSet slabWhole = CuboidSet.of(Cuboid.of(0, 4, 4, 2, 6, 6));
        List<CuboidSet> first = new ArrayList<>(List.of(lShapeColumns, shellCut, slabWhole));
        List<CuboidSet> second = new ArrayList<>(List.of(slabWhole, lShapeRows, shellBoxes));
        first.sort(null);
        second.sort(null);
        assertEquals(List.of(lShapeColumns, shellCut, slabWhole), first);
        assertEquals(first, second);
    }

    @Test
    void compareToIsATotalOrder() {
        Random random = new Random(20261106L);
        List<CuboidSet> regions = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            regions.add(randomRegion(random));
        }
        for (CuboidSet left : regions) {
            assertEquals(0, left.compareTo(left));
            for (CuboidSet right : regions) {
                assertEquals(Integer.signum(left.compareTo(right)), -Integer.signum(right.compareTo(left)));
                assertEquals(left.compareTo(right) == 0, left.equals(right));
            }
        }
        for (int sample = 0; sample < 500; sample++) {
            CuboidSet a = regions.get(random.nextInt(regions.size()));
            CuboidSet b = regions.get(random.nextInt(regions.size()));
            CuboidSet c = regions.get(random.nextInt(regions.size()));
            if (a.compareTo(b) <= 0 && b.compareTo(c) <= 0) {
                assertTrue(a.compareTo(c) <= 0, a + " <= " + b + " <= " + c);
            }
        }
    }

    @Test
    void theTiebreakUsesCells() {
        CuboidSet mainDiagonal = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(1, 0, 1, 2, 1, 2));
        CuboidSet antiDiagonal = CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1), Cuboid.of(0, 0, 1, 1, 1, 2));
        assertEquals(mainDiagonal.bounds(), antiDiagonal.bounds());
        assertEquals(mainDiagonal.volume(), antiDiagonal.volume());
        assertTrue(mainDiagonal.compareTo(antiDiagonal) < 0);
        assertTrue(antiDiagonal.compareTo(mainDiagonal) > 0);
    }

    @Test
    void anEmptyRegionSortsLast() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        assertTrue(CuboidSet.empty().compareTo(region) > 0);
        assertTrue(region.compareTo(CuboidSet.empty()) < 0);
        assertEquals(0, CuboidSet.empty().compareTo(CuboidSet.empty()));
    }

    @Test
    void firstCellIsTheSmallestCell() {
        CuboidSet columns = CuboidSet.of(Cuboid.of(2, 0, 2, 3, 1, 3), Cuboid.of(0, 1, 0, 1, 2, 2));
        CuboidSet rows = CuboidSet.of(
            Cuboid.of(0, 1, 0, 1, 2, 1),
            Cuboid.of(0, 1, 1, 1, 2, 2),
            Cuboid.of(2, 0, 2, 3, 1, 3)
        );
        LatticePoint smallest = LatticePoint.of(2, 0, 2);
        for (CuboidSet set : List.of(columns, rows)) {
            assertEquals(smallest, set.decomposition().get(0).min());
            assertEquals(smallest, new CellIterator(set).next());
        }
    }

    private static CuboidSet randomRegion(Random random) {
        List<Cuboid> boxes = new ArrayList<>();
        int boxCount = 1 + random.nextInt(4);
        for (int i = 0; i < boxCount; i++) {
            int x = random.nextInt(8);
            int y = random.nextInt(8);
            int z = random.nextInt(8);
            boxes.add(Cuboid.of(
                x,
                y,
                z,
                x + 1 + random.nextInt(3),
                y + 1 + random.nextInt(3),
                z + 1 + random.nextInt(3)
            ));
        }
        return CuboidSet.of(boxes);
    }
}
