package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.FocusResult;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nested coverage: a nested focus binds no subjects and a nested body never backs a claim. */
class NestedCoverageScenarioTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
    private static final CuboidSet oneCell = CuboidSet.of(Cuboid.of(origin));

    @Test
    void nestedOneSubjectBoundBayFailsBuildWithUndecidedCells() {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("nested/one_bay"),
            oneCell,
            Coverage.oneSubject,
            Footprint.bound(),
            Sharing.exclusive
        );
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id("nested/one_subject")).bounds(bounds).check(
            Check.of(MagicFixtures.id("nested/one_check"), bay, Focuses.nested(origin), nestedExpectation())
        );
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> builder.build());
        String message = Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.contains("SC-BPT-0005"), "the nested focus binds no subject: " + message);
    }

    @Test
    @DisplayName("known gap: ANY_SUBJECT+BOUND accepts a nested body at build, then check rejects it with SC-CLM-0004")
    void nestedAnySubjectBoundBodyBuildsThenFailsAtCheck() {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("nested/any_bay"),
            oneCell,
            Coverage.anySubject,
            Footprint.bound(),
            Sharing.exclusive
        );
        Blueprint blueprint = Blueprint.builder(MagicFixtures.id("nested/any_subject")).bounds(bounds)
                .check(
                    Check.of(MagicFixtures.id("nested/any_check"), bay, bindNested(origin), Expectations.anyOccupant())
                ).build();
        TestWorld.View view = MagicFixtures.view().put(nestedOccupant(origin, 0));
        MatchResult result = MagicFixtures
                .check(blueprint, MagicFixtures.identity(origin), view, new ClaimView(List.of(), 0, true));
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result);
        assertTrue(MagicFixtures.hasCode(failed, Codes.bindingInvalid), "the nested subject is SC-CLM-0004");
    }

    @Test
    void aCutNestedViewIsUndecidedWithOcc0003() {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("nested/cut_bay"),
            oneCell,
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Blueprint blueprint = Blueprint.builder(MagicFixtures.id("nested/cut")).bounds(bounds)
                .check(Check.of(MagicFixtures.id("nested/cut_check"), bay, Focuses.nested(origin), nestedExpectation()))
                .build();
        MatchResult result = MagicFixtures.check(
            blueprint,
            MagicFixtures.identity(origin),
            MagicFixtures.view(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertTrue(MagicFixtures.hasCode(undecided, Codes.nestedCut), "a cut child is SC-OCC-0003");
    }

    @Test
    void aChildFrameOccupantIsRejectedAtCheck() {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("nested/frame_bay"),
            oneCell,
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Blueprint blueprint = Blueprint.builder(MagicFixtures.id("nested/child_frame")).bounds(bounds).check(
            Check.of(MagicFixtures.id("nested/frame_check"), bay, Focuses.occupant(origin), Expectations.anyOccupant())
        ).build();
        TestWorld.View view = MagicFixtures.view().put(nestedOccupant(origin, 1));
        MatchResult result = MagicFixtures
                .check(blueprint, MagicFixtures.identity(origin), view, new ClaimView(List.of(), 0, true));
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result);
        assertTrue(MagicFixtures.hasCode(failed, Codes.bindingInvalid), "a child-frame subject is SC-CLM-0004");
    }

    private static Expectation<WorldView> nestedExpectation() {
        return Expectations.custom(
            WorldView.class,
            ExpectationMeta.of(MagicFixtures.id("nested/expectation"), 10, Codes.nestedCut.code()),
            context -> Reply.match()
        );
    }

    private static Focus<OccupantSnapshot> bindNested(LatticePoint cell) {
        return new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot occupant = ctx.occupantAt(cell);
                if (occupant == null || occupant.isAir()) {
                    return new FocusResult.Unavailable<>(FindingBundle.empty());
                }
                return new FocusResult.Taken<>(occupant, List.of(occupant), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }
        };
    }

    private static OccupantSnapshot nestedOccupant(LatticePoint cell, int frame) {
        NamespacedId type = MagicFixtures.id("nested/body");
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, frame, type, OccupantKind.nested, cell);
        return new OccupantSnapshot(ref, new OccupantDescriptor(type, OccupantKind.nested), Cuboid.of(cell), List.of());
    }
}
