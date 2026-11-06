package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** context.md §7: associativity for each built-in merge; union is canonical. */
class MergesTest {

    @Test
    void sumMinMaxLengthAndBooleanMergesAreAssociative() {
        long[] longs = {-12L, -1L, 0L, 1L, 7L, Long.MAX_VALUE, Long.MIN_VALUE / 2};
        for (long a : longs) {
            for (long b : longs) {
                for (long c : longs) {
                    assertLongAssociative(Merges.sum(), a, b, c);
                    assertLongAssociative(Merges.min(), a, b, c);
                    assertLongAssociative(Merges.max(), a, b, c);
                }
            }
        }
        for (boolean a : new boolean[] {true, false}) {
            for (boolean b : new boolean[] {true, false}) {
                for (boolean c : new boolean[] {true, false}) {
                    assertBooleanAssociative(Merges.and(), a, b, c);
                    assertBooleanAssociative(Merges.or(), a, b, c);
                }
            }
        }
    }

    @Test
    void unionIsAssociative() {
        CuboidSet a = CuboidSet.of(Cuboid.of(0, 0, 0, 4, 4, 4));
        CuboidSet b = CuboidSet.of(Cuboid.of(2, 2, 2, 6, 6, 6));
        CuboidSet c = CuboidSet.of(Cuboid.of(5, 0, 5, 9, 4, 9));
        CuboidSet left = Merges.union().combine(Merges.union().combine(a, b), c);
        CuboidSet right = Merges.union().combine(a, Merges.union().combine(b, c));
        assertEquals(left, right);
        assertEquals(left.volume(), right.volume());
    }

    @Test
    void unionIsCanonical() {
        CuboidSet a = CuboidSet.of(Cuboid.of(0, 0, 0, 4, 4, 4));
        CuboidSet b = CuboidSet.of(Cuboid.of(2, 2, 2, 6, 6, 6));
        CuboidSet merged = Merges.union().combine(a, b);
        List<Cuboid> boxes = new ArrayList<>(a.decomposition());
        boxes.addAll(b.decomposition());
        CuboidSet canonical = CuboidSet.of(boxes);
        assertEquals(canonical, merged);
        assertEquals(canonical.decomposition(), merged.decomposition(), "union keeps the canonical decomposition");

        CuboidSet first = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 2));
        CuboidSet second = CuboidSet.of(Cuboid.of(2, 0, 0, 4, 2, 2));
        CuboidSet disjoint = Merges.union().combine(first, second);
        assertEquals(CuboidSet.of(Cuboid.of(0, 0, 0, 4, 2, 2)), disjoint);
        assertEquals(
            CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 2), Cuboid.of(2, 0, 0, 4, 2, 2)).decomposition(),
            disjoint.decomposition()
        );
    }

    private static void assertLongAssociative(dev.vfyjxf.cumulus.kernel.core.Merge<Long> merge, long a, long b, long c) {
        assertEquals(merge.combine(merge.combine(a, b), c), merge.combine(a, merge.combine(b, c)));
    }

    private static void assertBooleanAssociative(
        dev.vfyjxf.cumulus.kernel.core.Merge<Boolean> merge,
        boolean a,
        boolean b,
        boolean c
    ) {
        assertEquals(merge.combine(merge.combine(a, b), c), merge.combine(a, merge.combine(b, c)));
    }
}
