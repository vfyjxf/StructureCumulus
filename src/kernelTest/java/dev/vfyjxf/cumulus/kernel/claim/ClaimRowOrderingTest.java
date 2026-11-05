package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRowOrderingTest {

    private static final LatticeKey cell = LatticeKey.of(LatticePoint.origin);
    private static final InstanceId instance = new InstanceId(World.of(NamespacedId.of("ordering")), 1);
    private static final InstanceId otherInstance = new InstanceId(World.of(NamespacedId.of("ordering")), 2);
    private static final NamespacedId bay = NamespacedId.of("ordering_bay");
    private static final Siting baseSiting = Siting.of(LatticePoint.origin, Orientation.identity);

    @Test
    void compareToUsesEveryRecordFieldIncludingSitingAndSharing() {
        ClaimRow base = row(Sharing.exclusive, instance, baseSiting, bay);
        ClaimRow cellVariant = row(Sharing.exclusive, instance, baseSiting, bay, LatticeKey.of(LatticePoint.of(1, 0, 0)));
        ClaimRow instanceVariant = row(Sharing.exclusive, otherInstance, baseSiting, bay);
        ClaimRow bayVariant = row(Sharing.exclusive, instance, baseSiting, NamespacedId.of("z_bay"));
        ClaimRow originVariant = row(
            Sharing.exclusive,
            instance,
            Siting.of(LatticePoint.of(1, 0, 0), Orientation.identity),
            bay
        );
        ClaimRow orientationVariant = row(
            Sharing.exclusive,
            instance,
            Siting.of(
                LatticePoint.origin,
                new Placement(Orientation.of(Turn.clockwise, Tilt.level, Flip.none), LatticePoint.origin)
            ),
            bay
        );
        ClaimRow offsetVariant = row(
            Sharing.exclusive,
            instance,
            Siting.of(LatticePoint.origin, new Placement(Orientation.identity, LatticePoint.of(1, 0, 0))),
            bay
        );
        ClaimRow sharingVariant = row(Sharing.shared, instance, baseSiting, bay);

        assertLess(base, cellVariant);
        assertLess(base, instanceVariant);
        assertLess(base, bayVariant);
        assertLess(base, originVariant);
        assertLess(base, orientationVariant);
        assertLess(base, offsetVariant);
        assertLess(base, sharingVariant, "sharing is compared after the existing keys");

        ClaimRow duplicate = row(Sharing.exclusive, instance, baseSiting, bay);
        assertEquals(base, duplicate);
        assertEquals(0, base.compareTo(duplicate), "equal records compare equal");
        assertNotEquals(base, cellVariant);
        assertNotEquals(base, instanceVariant);
        assertNotEquals(base, bayVariant);
        assertNotEquals(base, originVariant);
        assertNotEquals(base, orientationVariant);
        assertNotEquals(base, offsetVariant);
        assertNotEquals(base, sharingVariant);

        List<ClaimRow> rows = List.of(
            base,
            cellVariant,
            instanceVariant,
            bayVariant,
            originVariant,
            orientationVariant,
            offsetVariant,
            sharingVariant
        );
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < rows.size(); j++) {
                assertEquals(
                    Integer.signum(rows.get(i).compareTo(rows.get(j))),
                    -Integer.signum(rows.get(j).compareTo(rows.get(i))),
                    "compareTo is antisymmetric"
                );
                assertEquals(
                    rows.get(i).equals(rows.get(j)),
                    rows.get(i).compareTo(rows.get(j)) == 0,
                    "compareTo is zero exactly for equal records"
                );
            }
        }
    }

    @Test
    void forwardAndReverseInputsProduceTheSameCanonicalRows() {
        ClaimRow firstSiting = row(Sharing.shared, instance, baseSiting, bay);
        ClaimRow secondSiting = row(
            Sharing.shared,
            instance,
            Siting.of(LatticePoint.of(10, 0, 0), Orientation.identity),
            bay
        );
        ClaimRow exclusiveAtFirstSiting = row(Sharing.exclusive, instance, baseSiting, bay);
        List<ClaimRow> forward = List.of(firstSiting, secondSiting, exclusiveAtFirstSiting);
        List<ClaimRow> reverse = List.of(exclusiveAtFirstSiting, secondSiting, firstSiting);

        ClaimSet forwardSet = new ClaimSet(forward, 4);
        ClaimSet reverseSet = new ClaimSet(reverse, 4);
        assertEquals(forwardSet, reverseSet, "ClaimSet canonical order ignores input order");
        assertEquals(forwardSet.rows(), List.of(exclusiveAtFirstSiting, firstSiting, secondSiting));

        assertEquals(
            new ClaimView(forward, 4, true),
            new ClaimView(reverse, 4, true),
            "ClaimView canonical order ignores input order"
        );
        assertEquals(
            new ClaimVerdict.Settled(4, forward),
            new ClaimVerdict.Settled(4, reverse),
            "Settled canonical order ignores input order"
        );
        assertLess(exclusiveAtFirstSiting, firstSiting);
        assertLess(firstSiting, secondSiting);
    }

    private static ClaimRow row(Sharing sharing, InstanceId instance, Siting siting, NamespacedId bay) {
        return row(sharing, instance, siting, bay, cell);
    }

    private static ClaimRow row(Sharing sharing, InstanceId instance, Siting siting, NamespacedId bay, LatticeKey cell) {
        return new ClaimRow(cell, sharing, siting, bay, instance);
    }

    private static void assertLess(ClaimRow first, ClaimRow second) {
        assertLess(first, second, "the first row sorts first");
    }

    private static void assertLess(ClaimRow first, ClaimRow second, String message) {
        assertTrue(first.compareTo(second) < 0, message);
        assertTrue(second.compareTo(first) > 0, message + " (reverse)");
    }
}
