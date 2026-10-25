package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 22 §4.4, P7; 09 §4.5: orientation is a function of the anchor, not a search preference. */
class OrientationScenarioTest {

    private static final Heading AUTHORED_FRONT = Heading.NORTH;

    @Test
    void fortyEightOrientationsAreDistinctAndClosedUnderComposition() {
        List<Orientation> all = Orientation.all();
        assertEquals(48, all.size(), "22 §4: Orientation has 48 elements");
        Set<Orientation> distinct = new HashSet<>(all);
        assertEquals(48, distinct.size(), "the 48 ordinals are 48 distinct isometries");

        for (Orientation left : all) {
            assertEquals(left, Orientation.IDENTITY.compose(left), "the identity is neutral on the left");
            assertEquals(left, left.compose(Orientation.IDENTITY), "and on the right");
            assertEquals(Orientation.IDENTITY, left.compose(left.inverse()),
                    "every element has its inverse in the group");
            for (Orientation right : all) {
                assertTrue(distinct.contains(left.compose(right)),
                        "22 §4: the 48 are closed under composition: " + left + " then " + right);
                assertTrue(distinct.contains(left.then(right)),
                        "then(other) is this, then other, and never leaves the group");
            }
        }
    }

    @Test
    void theAnchorEquationSelectsExactlyOneSitingPerHeading() {
        Map<Heading, Orientation> selected = new EnumMap<>(Heading.class);
        for (Heading heading : Heading.values()) {
            List<Orientation> candidates = Orientation.all().stream()
                    .filter(candidate -> candidate.apply(AUTHORED_FRONT) == heading)
                    .toList();
            assertEquals(8, candidates.size(),
                    "22 §4.4: the equation alone admits 8 orientations per heading; uniqueness comes from "
                            + "OrientationPolicy.select scanning allowed in Orientation.all() order (09 §4.5)");

            Orientation chosen = candidates.get(0);
            assertEquals(heading, chosen.apply(AUTHORED_FRONT),
                    "the selected candidate satisfies candidate.apply(authoredFront) == anchorHeading");

            Siting siting = Siting.of(LatticePoint.ORIGIN, chosen);
            assertEquals(chosen, siting.placement().orientation(), "the siting carries the mapped orientation");
            assertEquals(heading, siting.placement().orientation().apply(AUTHORED_FRONT),
                    "P7: the orientation is a function of the anchor heading, one siting per heading");
            selected.put(heading, chosen);
        }

        assertEquals(6, selected.size(), "six headings");
        assertEquals(6, new HashSet<>(selected.values()).size(),
                "one siting per heading: no orientation serves two anchor headings");
    }

    @Test
    void anOrientationThatFailsTheEquationIsNotACandidate() {
        for (Heading heading : Heading.values()) {
            if (heading == AUTHORED_FRONT) {
                continue;
            }
            assertNotEquals(heading, Orientation.IDENTITY.apply(AUTHORED_FRONT),
                    "identity maps the authored front onto itself, so it is no candidate for " + heading);
        }
    }
}
