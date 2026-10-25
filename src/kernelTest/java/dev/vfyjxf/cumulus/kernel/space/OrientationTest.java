package dev.vfyjxf.cumulus.kernel.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrientationTest {

    @Test
    void allHasFortyEightDistinctElements() {
        List<Orientation> all = Orientation.all();
        assertEquals(48, all.size());
        assertEquals(48, Set.copyOf(all).size());
        assertEquals(48, new HashSet<>(all).size());
        for (int index = 0; index < 48; index++) {
            assertEquals(index, all.get(index).ordinal());
            assertEquals(index, all.get(index).id());
        }
    }

    @Test
    void compositionCloses() {
        for (Orientation left : Orientation.all()) {
            assertEquals(left, left.then(Orientation.IDENTITY));
            assertEquals(left, Orientation.IDENTITY.then(left));
            for (Orientation right : Orientation.all()) {
                Orientation product = left.then(right);
                assertTrue(product.ordinal() >= 0 && product.ordinal() < 48);
                assertEquals(right.compose(left), product);
            }
        }
    }

    @Test
    void compositionIsAssociative() {
        for (Orientation a : Orientation.all()) {
            for (Orientation b : Orientation.all()) {
                for (Orientation c : Orientation.all()) {
                    assertEquals(a.then(b).then(c), a.then(b.then(c)));
                }
            }
        }
    }

    @Test
    void inverseUndoesBothWays() {
        for (Orientation orientation : Orientation.all()) {
            assertEquals(Orientation.IDENTITY, orientation.then(orientation.inverse()));
            assertEquals(Orientation.IDENTITY, orientation.inverse().then(orientation));
            assertEquals(Orientation.IDENTITY, orientation.compose(orientation.inverse()));
            assertEquals(orientation, orientation.inverse().inverse());
        }
    }

    @Test
    void canonicalTripleReproducesEveryElement() {
        for (Orientation orientation : Orientation.all()) {
            Flip flip = orientation.isMirror() ? Flip.FRONT_BACK : Flip.NONE;
            assertEquals(orientation, Orientation.of(orientation.turn(), orientation.tilt(), flip));
        }
    }

    @Test
    void generatorTriplesCollapseOntoFortyEightElements() {
        assertEquals(
                Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.FRONT_BACK),
                Orientation.of(Turn.HALF, Tilt.LEVEL, Flip.LEFT_RIGHT));
    }

    @Test
    void volumeSurvivesEveryOrientation() {
        Cuboid box = Cuboid.of(0, 0, 0, 1, 2, 4);
        for (Orientation orientation : Orientation.all()) {
            Cuboid image = orientation.apply(box);
            assertNotNull(image);
            assertEquals(8L, image.volume());
        }
    }

    @Test
    void negationMapsCornersCellWise() {
        Orientation mirror = Orientation.of(Turn.NONE, Tilt.LEVEL, Flip.FRONT_BACK);
        Cuboid across = mirror.apply(Cuboid.of(-1, 0, -1, 1, 1, 1));
        assertEquals(Cuboid.of(0, 0, -1, 2, 1, 1), across);
    }

    @Test
    void yawTurnsNorthIntoEast() {
        Orientation yaw = Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE);
        assertEquals(Heading.EAST, yaw.apply(Heading.NORTH));
        assertEquals(Heading.UP, yaw.apply(Heading.UP));
        assertEquals(Heading.UP, yaw.tilt().up());
    }

    @Test
    void composeAppliesTheArgumentFirst() {
        Orientation yaw = Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE);
        Orientation cap = Orientation.of(Turn.NONE, Tilt.INVERTED, Flip.NONE);
        Orientation tipped = yaw.compose(cap);
        assertEquals(Heading.WEST, tipped.apply(Heading.NORTH));
        assertEquals(Heading.DOWN, tipped.apply(Heading.UP));
        assertFalse(tipped.isMirror());
        assertEquals(tipped, cap.then(yaw));
    }
}
