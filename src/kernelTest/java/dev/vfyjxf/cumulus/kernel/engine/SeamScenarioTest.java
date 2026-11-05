package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Seam;
import dev.vfyjxf.cumulus.kernel.structure.SeamId;
import dev.vfyjxf.cumulus.kernel.structure.SeamKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seams are declared connectable edges: they never feed the engine and never own a cell. */
class SeamScenarioTest {

    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
    private static final CuboidSet bayCells = CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2));
    private static final LatticePoint seamCell = LatticePoint.of(1, 0, 1);

    @Test
    void aDeclaredSeamOverAirPassesAndDoesNotChangeTheResult() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.air(view, siting, seamCell);

        Blueprint withSeam = blueprint("seam/with", true);
        Blueprint withoutSeam = blueprint("seam/without", false);
        assertEquals(1, withSeam.seams().size(), "the seam is declared");
        assertEquals(seamCell, withSeam.seams().get(0).cells().min(), "the seam covers the declared air cell");

        ClaimLedger seamLedger = MagicFixtures.ledger();
        MatchResult.Formed withFormed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(withSeam, siting, view, seamLedger)
        );
        TestWorld.View controlView = MagicFixtures.view();
        MagicFixtures.air(controlView, siting, seamCell);
        ClaimLedger plainLedger = MagicFixtures.ledger();
        MatchResult.Formed plainFormed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(withoutSeam, siting, controlView, plainLedger)
        );
        assertEquals(1, withFormed.claims().rows().size(), "the seam is never claimed");
        assertEquals(1, plainFormed.claims().rows().size(), "the no-seam control claims one cell");
        assertEquals(1, TestWorld.rows(seamLedger).size(), "the seam does not add a row");
        assertEquals(
            plainFormed.claims().rows().get(0).cell(),
            withFormed.claims().rows().get(0).cell(),
            "the seam does not move the claimed cell"
        );
        assertTrue(
            withSeam.reads().cells().containsAll(bayCells),
            "the read scope stays the check's cells, not the seam's cells"
        );
    }

    @Test
    void aSeamOutsideEveryBayFailsTheBuild() {
        Seam outside = new Seam(
            new SeamId(MagicFixtures.id("seam/outside")),
            Cuboid.of(2, 0, 2, 3, 1, 3),
            SeamKind.structural,
            Map.of()
        );
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> blueprint("seam/outside_template", outside),
            "a seam outside every bay is a build failure"
        );
        String message = Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.contains("SC-BPT-0009"), "the failure carries SC-BPT-0009: " + message);
    }

    private static Blueprint blueprint(String path, boolean withSeam) {
        return buildBlueprint(path, withSeam ? seam(path) : null);
    }

    private static Blueprint blueprint(String path, Seam seam) {
        return buildBlueprint(path, seam);
    }

    private static Seam seam(String path) {
        return new Seam(
            new SeamId(MagicFixtures.id(path + "_seam")),
            Cuboid.of(seamCell),
            SeamKind.structural,
            Map.of()
        );
    }

    private static Blueprint buildBlueprint(String path, @Nullable Seam seam) {
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id(path + "_bay"), bayCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> check = Check.of(MagicFixtures.id(path + "_air"), bay, Focuses.occupant(seamCell), Expectations.air());
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds).check(check);
        if (seam != null) {
            builder.seam(seam);
        }
        return builder.build();
    }
}
