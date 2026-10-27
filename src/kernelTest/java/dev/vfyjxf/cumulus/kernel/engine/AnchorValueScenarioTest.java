package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Anchors;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The anchor's declared value and a policy with two orientations that share one north image. */
class AnchorValueScenarioTest {

    private static final NamespacedId role = TechFixtures.id("anchor/primary");
    private static final LatticePoint origin = LatticePoint.origin;
    private static final Orientation frontBack = Orientation.of(Turn.none, Tilt.level, Flip.frontBack);

    @Test
    @DisplayName("known gap: AnchorSpec.value is ignored; only the property heading selects")
    void anchorValueMismatchIsIgnored() {
        OrientationPolicy policy = OrientationPolicy.builder(role)
                .allowTurns(Turn.none, Turn.clockwise, Turn.half, Turn.counterclockwise).build();
        Blueprint blueprint = anchorBlueprint("anchor/value_mismatch", new AnchorSpec(role, "facing", "north"), policy);
        TestWorld.View view = new TestWorld.View()
                .put(TestWorld.block(TechFixtures.anchorBlock, origin, Map.of("facing", "east"), List.of()));
        SitingResult resolved = Anchors.resolve(blueprint, origin, view);
        assertInstanceOf(
            Outcome.Found.class,
            resolved.outcome(),
            "the current resolver ignores AnchorSpec.value and accepts a heading-only match"
        );
        assertTrue(resolved.findings().isEmpty(), "the ignored declared value produces no finding");
        Siting found = resolved.outcome().fold(value -> value, () -> null);
        assertNotNull(found, "the current resolver finds a siting");
        assertEquals(
            Orientation.of(Turn.clockwise, Tilt.level, Flip.none),
            found.placement().orientation(),
            "the actual east heading selects the clockwise orientation despite the declared north value"
        );
    }

    @Test
    @DisplayName("known gap: two allowed orientations with one north image resolve to the canonical first")
    void twoAllowedOrientationsWithTheSameNorthImagePickTheFirst() {
        OrientationPolicy policy = OrientationPolicy.builder(role).allow(frontBack, Orientation.identity).build();
        assertEquals(2, policy.allowed().size(), "the policy allows both orientations");
        assertEquals(Heading.north, frontBack.apply(Heading.north), "the mirror keeps the north image");
        assertEquals(Orientation.identity, policy.select(Heading.north), "the canonical first orientation wins today");
    }

    @Test
    void matchingAnchorValueStillResolves() {
        OrientationPolicy policy = OrientationPolicy.builder(role)
                .allowTurns(Turn.none, Turn.clockwise, Turn.half, Turn.counterclockwise).build();
        Blueprint blueprint = anchorBlueprint("anchor/matching_value", new AnchorSpec(role, "facing", "east"), policy);
        TestWorld.View view = new TestWorld.View()
                .put(TestWorld.block(TechFixtures.anchorBlock, origin, Map.of("facing", "east"), List.of()));
        assertInstanceOf(Outcome.Found.class, Anchors.resolve(blueprint, origin, view).outcome());
        Siting found = Anchors.resolve(blueprint, origin, view).outcome().fold(value -> value, () -> null);
        assertNotNull(found, "the matching anchor finds a siting");
        assertEquals(
            Orientation.of(Turn.clockwise, Tilt.level, Flip.none),
            found.placement().orientation(),
            "the east anchor keeps working when the declared value also matches"
        );
    }

    private static Blueprint anchorBlueprint(String path, AnchorSpec spec, OrientationPolicy policy) {
        CuboidSet cells = TechFixtures.cells(TechFixtures.cell(origin));
        Bay bay = TechFixtures
                .bay(TechFixtures.id(path + "/bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(TechFixtures.id(path)).bounds(TechFixtures.box(0, 0, 0, 1, 1, 1))
                .check(
                    TechFixtures.occupant(
                        TechFixtures.id(path + "/anchor"),
                        bay,
                        origin,
                        Expectations.block(TechFixtures.anchorBlock)
                    )
                ).anchor(spec).orientation(policy).build();
    }
}
