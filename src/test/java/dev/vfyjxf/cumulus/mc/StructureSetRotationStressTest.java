package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.codes;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.counterclockwise;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.elbowId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.elbowSpec;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillElbow;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillRotationRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillWideMain;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.half;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.hotBlastElbow;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.hotBlastMain;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.rotationPort;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.rotationRootId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.rotationSet;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.wideId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.wideLinks;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.wideMain;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.clockwise;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootOrigin;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Root rotation, relative orientation and link alignment for a controller with a port. */
class StructureSetRotationStressTest {

    @Test
    void relativeHalfAlignsThePortUnderEveryRootTurn() {
        for (Orientation rootOrientation : List.of(Orientation.identity, clockwise, half, counterclockwise)) {
            StructureSetTestSupport.Fixture fixture = fixture(
                rotationSet(
                    hotBlastElbow(),
                    elbowSpec(
                        elbowId,
                        LatticePoint.of(2, 0, 3),
                        half,
                        LinkSpec.face(rotationPort, LatticePoint.of(1, 0, 0))
                    )
                ),
                rootOrientation
            );
            fillRotationRoot(fixture);
            fillElbow(fixture, elbowId);
            StructureSetResult result = fixture.run();

            assertTrue(result.formed(), () -> rootOrientation + ": " + result.setFindings());
            assertTrue(result.setFindings().isEmpty(), () -> rootOrientation + ": " + result.setFindings());
            assertEquals(List.of(rotationRootId, elbowId), result.settled());

            StructureSet.Satellite elbow = fixture.plan.satellite(elbowId);
            assertNotNull(elbow);
            assertEquals(
                rootOrientation.compose(half),
                elbow.siting().placement().orientation(),
                "the absolute orientation is root times relative at " + rootOrientation
            );
            assertEquals(
                1,
                manhattan(
                    world(fixture.plan.rootSiting(), rotationPort),
                    world(elbow.siting(), LatticePoint.of(1, 0, 0))
                ),
                "the face pair stays adjacent at " + rootOrientation
            );
            assertEquals(9 + 2, fixture.ledger.rows().size());
        }
    }

    @Test
    void relativeIdentityAtTheSameOffsetIsScSet0002() {
        for (Orientation rootOrientation : List.of(Orientation.identity, half)) {
            StructureSetTestSupport.Fixture fixture = fixture(
                rotationSet(
                    hotBlastElbow(),
                    elbowSpec(
                        elbowId,
                        LatticePoint.of(2, 0, 3),
                        Orientation.identity,
                        LinkSpec.face(rotationPort, LatticePoint.of(1, 0, 0))
                    )
                ),
                rootOrientation
            );
            fillRotationRoot(fixture);
            fillElbow(fixture, elbowId);
            StructureSetResult result = fixture.run();

            assertFalse(result.formed(), "the elbow port is two cells away under " + rootOrientation);
            assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(result));
            assertTrue(fixture.instances.isEmpty());
            assertEquals(0, fixture.ledger.rows().size());
        }
    }

    @Test
    void aHalfTurnedRootKeepsAWideMainAdjacent() {
        StructureSetTestSupport.Fixture fixture = fixture(
            rotationSet(hotBlastMain(), wideMain(false, wideLinks().toArray(LinkSpec[]::new))),
            half
        );
        fillRotationRoot(fixture);
        fillWideMain(fixture, wideId);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(List.of(rotationRootId, wideId), result.settled());
        assertEquals(9 + 3, fixture.ledger.rows().size());

        StructureSet.Satellite main = fixture.plan.satellite(wideId);
        assertNotNull(main);
        assertEquals(half, main.siting().placement().orientation());
        for (int x = 0; x < 3; x++) {
            LatticePoint parent = world(fixture.plan.rootSiting(), LatticePoint.of(x, 0, 2));
            LatticePoint portWorld = world(main.siting(), LatticePoint.of(x, 0, 0));
            assertEquals(1, manhattan(parent, portWorld), "pair " + x + " stays adjacent under a half turn");
        }
    }

    @Test
    void aClockwiseRootMapsTheParentCellToTheExpectedWorldCell() {
        StructureSetTestSupport.Fixture fixture = fixture(
            rotationSet(
                hotBlastElbow(),
                elbowSpec(
                    elbowId,
                    LatticePoint.of(2, 0, 3),
                    half,
                    LinkSpec.face(rotationPort, LatticePoint.of(1, 0, 0))
                )
            ),
            clockwise
        );
        fillRotationRoot(fixture);
        fillElbow(fixture, elbowId);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(LatticePoint.of(98, 10, 101), fixture.plan.rootSiting().toWorld(rotationPort));
        StructureSet.Satellite elbow = fixture.plan.satellite(elbowId);
        assertNotNull(elbow);
        assertEquals(LatticePoint.of(97, 10, 102), elbow.siting().origin());
        assertEquals(
            fixture.plan.rootSiting().compose(elbow.compiled().relativeSiting()),
            elbow.siting(),
            "the placed satellite is exactly root.compose(relative)"
        );
    }

    private static StructureSetTestSupport.Fixture fixture(CompiledStructureSet set, Orientation orientation) {
        return new StructureSetTestSupport.Fixture(set, Siting.of(rootOrigin, orientation));
    }

    private static int manhattan(LatticePoint first, LatticePoint second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.y() - second.y()) + Math.abs(first.z() - second.z());
    }
}
