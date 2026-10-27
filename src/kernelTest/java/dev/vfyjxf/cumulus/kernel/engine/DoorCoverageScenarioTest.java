package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DoorView;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Door coverage: the door focus reads two cells and must not decide a third. */
class DoorCoverageScenarioTest {

    private static final LatticePoint lower = LatticePoint.of(0, 0, 0);
    private static final LatticePoint upper = LatticePoint.of(0, 1, 0);
    private static final LatticePoint extra = LatticePoint.of(1, 0, 0);
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 3, 2, 2);
    private static final CuboidSet twoDoorCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 2, 1));
    private static final CuboidSet overstatedCells = twoDoorCells.union(CuboidSet.of(Cuboid.of(extra)));

    @Test
    void aTwoCellDoorOwnsExactlyTwoCells() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("door/two_bay"),
            twoDoorCells,
            Coverage.oneSubject,
            Footprint.bound(),
            Sharing.exclusive
        );
        Blueprint blueprint = doorBlueprint("door/two", bay);
        TestWorld.View view = doorView(siting);
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(blueprint, siting, view, MagicFixtures.ledger())
        );
        assertEquals(2, formed.claims().rows().size(), "the bound door halves own two cells");
        assertEquals(
            twoDoorCells,
            Focuses.door(LatticePoint.origin).staticCells(twoDoorCells),
            "the focus decides its two reads"
        );
    }

    @Test
    void aThreeCellEveryCellBayFailsBuildWithUndecidedCells() {
        assertEquals(
            twoDoorCells,
            Focuses.door(LatticePoint.origin).staticCells(overstatedCells),
            "the door focus still decides only the origin and the upper half"
        );
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("door/overstated_bay"),
            overstatedCells,
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> doorBlueprint("door/overstated", bay),
            "an every-cell bay with a third door cell has undecided cells"
        );
        String message = Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.contains("SC-BPT-0005"), "the understatement is SC-BPT-0005: " + message);
    }

    private static Blueprint doorBlueprint(String path, Bay bay) {
        Check<DoorView> check = Check.of(
            MagicFixtures.id(path + "_check"),
            bay,
            Focuses.door(LatticePoint.origin),
            Expectations.custom(
                DoorView.class,
                ExpectationMeta
                        .of(MagicFixtures.id(path + "_expectation"), 10, FindingCode.of(FindingCode.Family.exp, 1)),
                context -> Reply.match()
            )
        );
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds).check(check);
        return builder.build();
    }

    private static TestWorld.View doorView(Siting siting) {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.put(view, siting, lower, TestWorld.door);
        MagicFixtures.put(view, siting, upper, TestWorld.door);
        return view;
    }
}
