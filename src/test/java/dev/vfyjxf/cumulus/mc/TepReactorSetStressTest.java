package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.codes;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.compiledSolar;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillReactor;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillSolar;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.id;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.reactorId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.reactorPort;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.reactorSet;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.solarSpec;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TEP-style reactor: several solar generators fan in to one controller cell under three range metrics. */
class TepReactorSetStressTest {

    private static final LatticePoint parent = reactorPort;

    @Test
    void severalSolarGeneratorsFanInToOneParentCell() {
        NamespacedId first = id("solar_one");
        NamespacedId second = id("solar_two");
        NamespacedId third = id("solar_three");
        StructureSetTestSupport.Fixture fixture = fixture(
            reactorSet(
                compiledSolar(
                    solarSpec(first, LatticePoint.of(1, 1, 6), LinkSpec.range(parent, LatticePoint.origin, 4))
                ),
                compiledSolar(
                    solarSpec(second, LatticePoint.of(4, 1, 5), LinkSpec.range(parent, LatticePoint.origin, 4))
                ),
                compiledSolar(
                    solarSpec(third, LatticePoint.of(-1, 1, 6), LinkSpec.range(parent, LatticePoint.origin, 4))
                )
            )
        );
        fillReactor(fixture);
        fillSolar(fixture, first);
        fillSolar(fixture, second);
        fillSolar(fixture, third);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(List.of(reactorId, first, second, third), result.settled());
        assertEquals(26 + 3, fixture.ledger.rows().size());
        assertEquals(26, result.claimsOf(reactorId).size());

        LatticePoint parentWorld = world(fixture.plan.rootSiting(), parent);
        for (NamespacedId solarId : List.of(first, second, third)) {
            StructureSet.Satellite satellite = fixture.plan.satellite(solarId);
            assertNotNull(satellite);
            LatticePoint portWorld = world(satellite.siting(), LatticePoint.origin);
            assertTrue(
                chebyshev(parentWorld, portWorld) <= 4,
                "the generator " + solarId + " stays within the fan-in range"
            );
        }
    }

    @Test
    void chebyshevManhattanAndEuclideanDifferAtTheSameDistance() {
        // delta (2, 0, 2): chebyshev 2, manhattan 4, euclidean sqrt(8)
        LatticePoint at = LatticePoint.of(3, 1, 4);
        assertTrue(metricRun(at, 2, DistanceMetric.chebyshev).formed());
        assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(metricRun(at, 3, DistanceMetric.manhattan)));
        assertTrue(metricRun(at, 4, DistanceMetric.manhattan).formed());
        assertTrue(metricRun(at, 3, DistanceMetric.euclidean).formed());
        assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(metricRun(at, 2, DistanceMetric.euclidean)));
    }

    @Test
    void anOptionalSolarGeneratorMayBeAbsent() {
        NamespacedId first = id("solar_one");
        NamespacedId missing = id("solar_two");
        NamespacedId third = id("solar_three");
        StructureSetTestSupport.Fixture fixture = fixture(
            reactorSet(
                compiledSolar(
                    solarSpec(first, LatticePoint.of(1, 1, 6), LinkSpec.range(parent, LatticePoint.origin, 4))
                ),
                compiledSolar(
                    solarSpec(missing, LatticePoint.of(4, 1, 5), LinkSpec.range(parent, LatticePoint.origin, 4))
                ),
                compiledSolar(
                    solarSpec(third, LatticePoint.of(-1, 1, 6), LinkSpec.range(parent, LatticePoint.origin, 4))
                )
            )
        );
        fillReactor(fixture);
        fillSolar(fixture, first);
        fillSolar(fixture, third);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty());
        assertEquals(List.of(reactorId, first, third), result.settled());
        assertFalse(result.outcomes().containsKey(missing));
        assertEquals(26 + 2, fixture.ledger.rows().size());
    }

    @Test
    void anOutOfRangeLinkFailsBeforeAnyCheck() {
        NamespacedId far = id("solar_far");
        StructureSetTestSupport.Fixture fixture = fixture(
            reactorSet(
                compiledSolar(solarSpec(far, LatticePoint.of(1, 1, 7), LinkSpec.range(parent, LatticePoint.origin, 4)))
            )
        );
        fillReactor(fixture);
        fillSolar(fixture, far);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(result));
        assertTrue(fixture.instances.isEmpty(), "link validation runs before every structure check");
        assertTrue(result.settled().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void anAbsentOptionalSatelliteStillHasItsLinkValidated() {
        NamespacedId absent = id("solar_absent");
        StructureSetTestSupport.Fixture fixture = fixture(
            reactorSet(
                compiledSolar(
                    solarSpec(absent, LatticePoint.of(4, 1, 5), LinkSpec.range(parent, LatticePoint.origin, 2))
                )
            )
        );
        fillReactor(fixture);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(
            List.of(Codes.linkDistanceInvalid.code()),
            codes(result),
            "an absent optional link is still checked"
        );
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void euclideanUsesSquaredIntegersAtItsBoundary() {
        // delta (3, 0, 4): squared 25; chebyshev 4, manhattan 7, euclidean 5
        LatticePoint at = LatticePoint.of(4, 1, 6);
        assertTrue(metricRun(at, 4, DistanceMetric.chebyshev).formed());
        assertTrue(metricRun(at, 5, DistanceMetric.euclidean).formed());
        assertEquals(
            List.of(Codes.linkDistanceInvalid.code()),
            codes(metricRun(at, 4, DistanceMetric.euclidean)),
            "sqrt(25) is exactly five; a max of four is out of range"
        );
    }

    private static StructureSetResult metricRun(LatticePoint at, int maxDistance, DistanceMetric metric) {
        NamespacedId satelliteId = id("solar");
        StructureSetTestSupport.Fixture fixture = fixture(
            reactorSet(
                compiledSolar(
                    solarSpec(satelliteId, at, LinkSpec.range(parent, LatticePoint.origin, maxDistance, metric))
                )
            )
        );
        fillReactor(fixture);
        fillSolar(fixture, satelliteId);
        return fixture.run();
    }

    private static StructureSetTestSupport.Fixture fixture(CompiledStructureSet set) {
        return new StructureSetTestSupport.Fixture(set);
    }

    private static int chebyshev(LatticePoint first, LatticePoint second) {
        return Math.max(
            Math.abs(first.x() - second.x()),
            Math.max(Math.abs(first.y() - second.y()), Math.abs(first.z() - second.z()))
        );
    }
}
