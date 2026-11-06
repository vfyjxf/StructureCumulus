package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every §4.8 build rule, the plan order and the reads union. */
class BuildRulesTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    @Test
    void emptyBoundsAndAChecklessBlueprintAreBpt0001() {
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        BlueprintRejected emptyBounds = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("empty_bounds")).bounds(Cuboid.of(0, 0, 0, 0, 1, 1)).check(check).build()
        );
        assertEquals(Codes.blueprintInvalid, emptyBounds.code());
        BlueprintRejected checkless = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("checkless")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).build()
        );
        assertEquals(Codes.blueprintInvalid, checkless.code());
    }

    @Test
    void aBayLeavingTheBoundsIsBpt0003() {
        Bay outside = Bay.of(
            id("outside"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("outside_bay")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), outside)).build()
        );
        assertEquals(Codes.bayOutside, rejected.code());
    }

    @Test
    void anEmptyBayIsRejectedAtConstruction() {
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> Bay.of(id("empty"), CuboidSet.empty(), Coverage.everyCell, Footprint.none(), Sharing.shared)
        );
        assertTrue(
            Objects.requireNonNull(rejected.getMessage(), "message").contains("at least one cell"),
            rejected.getMessage()
        );
    }

    @Test
    void duplicateCheckIdsAreBpt0004() {
        Bay bay = anyCellBay();
        Check<?> first = Check.of(id("twin"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        Check<?> second = Check.of(id("twin"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_checks")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(first).check(second)
                    .build()
        );
        assertEquals(Codes.duplicateId, rejected.code());
    }

    @Test
    void twoDifferentBaysWithOneIdAreBpt0004() {
        Bay first = Bay.of(
            id("twin"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        Bay second = Bay.of(
            id("twin"),
            CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_bays")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                    .check(anyCellCheck(id("first"), first)).check(anyCellCheck(id("second"), second)).build()
        );
        assertEquals(Codes.duplicateId, rejected.code());
        assertTrue(
            Objects.requireNonNull(rejected.getMessage(), "message").contains("two different bays share id"),
            rejected.getMessage()
        );
    }

    @Test
    void duplicateSeamIdsAreBpt0004() {
        Bay bay = anyCellBay();
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_seams")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), bay))
                    .seam(new Seam(new SeamId(id("port")), Cuboid.of(0, 0, 0, 1, 1, 1), SeamKind.item, Map.of()))
                    .seam(new Seam(new SeamId(id("port")), Cuboid.of(0, 0, 0, 1, 1, 1), SeamKind.fluid, Map.of()))
                    .build()
        );
        assertEquals(Codes.duplicateId, rejected.code());
    }

    @Test
    void coverageGapsAreBpt0005() {
        Bay undecided = Bay.of(
            id("undecided"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        BlueprintRejected everyCellGap = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("gap")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1)).check(
                Check.of(id("one"), undecided, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant())
            ).build()
        );
        assertEquals(Codes.coverageInvalid, everyCellGap.code());
        assertTrue(
            Objects.requireNonNull(everyCellGap.getMessage(), "message").contains("undecided cells"),
            everyCellGap.getMessage()
        );

        Bay perCell = Bay.of(
            id("per_cell"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.anySubject,
            Footprint.none(),
            Sharing.shared
        );
        BlueprintRejected anySubjectCells = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("per_cell")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("one"), perCell)).build()
        );
        assertEquals(Codes.coverageInvalid, anySubjectCells.code());
        assertTrue(
            Objects.requireNonNull(anySubjectCells.getMessage(), "message").contains("per-cell checks"),
            anySubjectCells.getMessage()
        );

        ContextKey<Long> key = ContextKeys.of(id("instance_key"), Long.class, Retention.instance);
        Bay subjectless = Bay.of(
            id("subjectless"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.oneSubject,
            Footprint.none(),
            Sharing.shared
        );
        BlueprintRejected bindsNothing = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("subjectless")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(
                        Check.of(id("ctx"), subjectless, Focuses.context(key), Expectations.context(key, value -> true))
                    ).build()
        );
        assertEquals(Codes.coverageInvalid, bindsNothing.code());
        assertTrue(
            Objects.requireNonNull(bindsNothing.getMessage(), "message").contains("binds no subject"),
            bindsNothing.getMessage()
        );
    }

    @Test
    void anExclusiveBayOwningNoKnownCellIsBpt0005() {
        Bay noOwned = Bay.of(
            id("no_owned"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.oneSubject,
            Footprint.none(),
            Sharing.exclusive
        );
        BlueprintRejected nothingOwned = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("no_owned")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(
                        Check.of(id("mob"), noOwned, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant())
                    ).build()
        );
        assertEquals(Codes.coverageInvalid, nothingOwned.code());
        assertTrue(
            Objects.requireNonNull(nothingOwned.getMessage(), "message").contains("owns nothing"),
            nothingOwned.getMessage()
        );

        ContextKey<Long> key = ContextKeys.of(id("instance_key"), Long.class, Retention.instance);
        Bay emptyDeclared = Bay.of(
            id("empty_declared"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.anySubject,
            Footprint.declared(CuboidSet.empty()),
            Sharing.exclusive
        );
        BlueprintRejected emptyOwned = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("empty_declared")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(
                Check.of(id("ctx"), emptyDeclared, Focuses.context(key), Expectations.context(key, value -> true))
            ).build()
        );
        assertEquals(Codes.coverageInvalid, emptyOwned.code());
        assertTrue(
            Objects.requireNonNull(emptyOwned.getMessage(), "message").contains("owns nothing"),
            emptyOwned.getMessage()
        );
    }

    @Test
    void aNonClaimableSubjectOnAClaimingBayIsBpt0005() {
        Bay claiming = Bay.of(
            id("claiming"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.anySubject,
            Footprint.box(),
            Sharing.shared
        );
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("non_claimable")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(Check.of(id("custom"), claiming, nonClaimableFocus(), Expectations.anyOccupant())).build()
        );
        assertEquals(Codes.coverageInvalid, rejected.code());
        assertTrue(
            Objects.requireNonNull(rejected.getMessage(), "message").contains("non-claimable subject"),
            rejected.getMessage()
        );
    }

    @Test
    void exclusiveOwnedSetOverlapsAreBpt0002() {
        CuboidSet leftCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        CuboidSet rightCells = CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1));

        Bay exclusiveBox = Bay.of(id("box"), leftCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay exclusiveDeclared = Bay
                .of(id("declared"), rightCells, Coverage.everyCell, Footprint.declared(leftCells), Sharing.exclusive);
        BlueprintRejected overExclusive = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("declared_over_exclusive")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                    .check(anyCellCheck(id("box_check"), exclusiveBox))
                    .check(anyCellCheck(id("declared_check"), exclusiveDeclared)).build()
        );
        assertEquals(Codes.baysOverlap, overExclusive.code());

        Bay sharedBox = Bay.of(id("shared_box"), leftCells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        BlueprintRejected overShared = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("declared_over_shared")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                    .check(anyCellCheck(id("box_check"), sharedBox))
                    .check(anyCellCheck(id("declared_check"), exclusiveDeclared)).build()
        );
        assertEquals(Codes.baysOverlap, overShared.code());

        Bay sharedDeclared = Bay.of(
            id("shared_declared"),
            rightCells,
            Coverage.everyCell,
            Footprint.declared(leftCells),
            Sharing.shared
        );
        assertDoesNotThrow(
            () -> Blueprint.builder(id("shared_overlap")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                    .check(anyCellCheck(id("box_check"), sharedBox))
                    .check(anyCellCheck(id("declared_check"), sharedDeclared)).build()
        );

        Bay secondSharedBox = Bay.of(id("second_box"), leftCells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        assertDoesNotThrow(
            () -> Blueprint.builder(id("shared_boxes")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("first_check"), sharedBox))
                    .check(anyCellCheck(id("second_check"), secondSharedBox)).build()
        );
    }

    @Test
    void anchorAndOrientationRulesAreBpt0006() {
        AnchorSpec front = new AnchorSpec(id("front"), "facing", "north");
        AnchorSpec duplicateRole = new AnchorSpec(id("front"), "facing", "south");
        BlueprintRejected duplicated = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_roles")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), anyCellBay())).anchor(front).anchor(duplicateRole)
                    .orientation(OrientationPolicy.builder(id("front")).allow(Orientation.identity).build()).build()
        );
        assertEquals(Codes.anchorInvalid, duplicated.code());
        assertTrue(
            Objects.requireNonNull(duplicated.getMessage(), "message").contains("duplicate anchor role"),
            duplicated.getMessage()
        );

        BlueprintRejected noAnchor = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("no_anchor")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), anyCellBay()))
                    .orientation(OrientationPolicy.builder(id("front")).allow(Orientation.identity).build()).build()
        );
        assertEquals(Codes.anchorInvalid, noAnchor.code());
        assertTrue(
            Objects.requireNonNull(noAnchor.getMessage(), "message").contains("needs an anchor"),
            noAnchor.getMessage()
        );

        BlueprintRejected noPolicy = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("no_policy")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), anyCellBay())).anchor(front).build()
        );
        assertEquals(Codes.anchorInvalid, noPolicy.code());
        assertTrue(
            Objects.requireNonNull(noPolicy.getMessage(), "message").contains("need an orientation policy"),
            noPolicy.getMessage()
        );

        BlueprintRejected noPrimary = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("no_primary")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), anyCellBay())).anchor(front)
                    .orientation(OrientationPolicy.builder(id("back")).allow(Orientation.identity).build()).build()
        );
        assertEquals(Codes.anchorInvalid, noPrimary.code());
        assertTrue(
            Objects.requireNonNull(noPrimary.getMessage(), "message").contains("primary role has no anchor"),
            noPrimary.getMessage()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> OrientationPolicy.builder(id("front")).build(),
            "allowed must not be empty"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new OrientationPolicy(id("front"), List.of(Orientation.identity, Orientation.identity), false),
            "allowed must not contain duplicates"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new OrientationPolicy(
                id("front"),
                List.of(Orientation.of(Turn.clockwise, Tilt.pitchUp, Flip.none)),
                true
            ),
            "uprightOnly promises every allowed maps UP to UP"
        );
    }

    @Test
    void computationCyclesAndUnknownInputsAreBpt0007() {
        ContextKey<Long> a = ContextKeys.of(id("a"), Long.class, Retention.instance);
        ContextKey<Long> b = ContextKeys.of(id("b"), Long.class, Retention.instance);
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        BlueprintRejected cycled = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("cycle")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(aggregate(a, Set.of(b))).computation(aggregate(b, Set.of(a))).build()
        );
        assertEquals(Codes.planInvalid, cycled.code());
        assertTrue(Objects.requireNonNull(cycled.getMessage(), "message").contains("cycle"), cycled.getMessage());

        ContextKey<Long> unknown = ContextKeys.of(id("unknown"), Long.class, Retention.instance);
        BlueprintRejected missingProducer = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("missing_producer")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(aggregate(a, Set.of(unknown))).build()
        );
        assertEquals(Codes.planInvalid, missingProducer.code());
        assertTrue(
            Objects.requireNonNull(missingProducer.getMessage(), "message").contains("reads unknown key"),
            missingProducer.getMessage()
        );
    }

    @Test
    void sameIdOtherTypeInput() {
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        ContextKey<Long> writes = ContextKeys.of(id("value"), Long.class, Retention.instance);
        ContextKey<String> reads = ContextKeys.of(id("value"), String.class, Retention.instance);
        AggregateComputation<String> reader = new AggregateComputation<>() {
            @Override
            public NamespacedId id() {
                return BuildRulesTest.id("reader");
            }

            @Override
            public ContextKey<String> output() {
                return ContextKeys.of(BuildRulesTest.id("total"), String.class, Retention.instance);
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of(reads);
            }

            @Override
            public CuboidSet region() {
                return CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
            }

            @Override
            public Outcome<String> compute(AggregateContext context) {
                return Outcome.missing();
            }
        };
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("typed_input")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(aggregate(id("writer"), writes, Set.of())).computation(reader).build()
        );
        assertEquals(Codes.planInvalid, rejected.code());
        assertTrue(
            Objects.requireNonNull(rejected.getMessage(), "message")
                    .contains("reads " + id("value") + " as java.lang.String, but its producer writes java.lang.Long"),
            rejected.getMessage()
        );

        Expectation<String> requiresText = Expectations.custom(
            String.class,
            new ExpectationMeta(
                id("requires_text"),
                0,
                Set.of(ContextKeys.of(id("value"), String.class, Retention.match)),
                Codes.expected.code()
            ),
            ctx -> Reply.match()
        );
        Bay anySubject = Bay.of(
            id("subject_bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.anySubject,
            Footprint.none(),
            Sharing.shared
        );
        Check<?> requiring = Check.of(id("requiring"), anySubject, stringFocus(), requiresText);
        BlueprintRejected unproduced = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("typed_requirement")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(requiring)
                    .computation(aggregate(id("writer"), writes, Set.of())).build()
        );
        assertEquals(Codes.checkReadsDerivation, unproduced.code());
        assertTrue(
            Objects.requireNonNull(unproduced.getMessage(), "message").contains("requires " + id("value")),
            unproduced.getMessage()
        );
    }

    @Test
    void sectionComputationRulesAreBpt0007() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        ContextKey<Long> produced = ContextKeys.of(id("produced"), Long.class, Retention.instance);
        ContextKey<Long> fed = ContextKeys.of(id("fed"), Long.class, Retention.instance, Merges.sum());

        BlueprintRejected withInputs = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("section_inputs")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(aggregate(produced, Set.of())).computation(section(fed, cells, Set.of(produced)))
                    .build()
        );
        assertEquals(Codes.planInvalid, withInputs.code());
        assertTrue(
            Objects.requireNonNull(withInputs.getMessage(), "message").contains("has inputs"),
            withInputs.getMessage()
        );

        ContextKey<Long> noMerge = ContextKeys.of(id("no_merge"), Long.class, Retention.instance);
        BlueprintRejected withoutMerge = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("section_no_merge")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(Computations.volume(noMerge, cells)).build()
        );
        assertEquals(Codes.planInvalid, withoutMerge.code());
        assertTrue(
            Objects.requireNonNull(withoutMerge.getMessage(), "message").contains("contributed output key"),
            withoutMerge.getMessage()
        );

        ContextKey<Long> merged = ContextKeys.of(id("merged"), Long.class, Retention.instance, Merges.sum());
        BlueprintRejected emptyRegion = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("empty_region")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(Computations.volume(merged, CuboidSet.empty())).build()
        );
        assertEquals(Codes.planInvalid, emptyRegion.code());
        assertTrue(
            Objects.requireNonNull(emptyRegion.getMessage(), "message").contains("empty region"),
            emptyRegion.getMessage()
        );
    }

    @Test
    void duplicateComputationIdsAndOutputsAreBpt0007() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        ContextKey<Long> first = ContextKeys.of(id("first"), Long.class, Retention.instance);
        ContextKey<Long> second = ContextKeys.of(id("second"), Long.class, Retention.instance);

        BlueprintRejected duplicateId = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_computations")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(aggregate(id("twin"), first, Set.of()))
                    .computation(aggregate(id("twin"), second, Set.of())).build()
        );
        assertEquals(Codes.planInvalid, duplicateId.code());
        assertTrue(
            Objects.requireNonNull(duplicateId.getMessage(), "message").contains("duplicate computation id"),
            duplicateId.getMessage()
        );

        ContextKey<Long> merged = ContextKeys.of(id("merged"), Long.class, Retention.instance);
        BlueprintRejected duplicateOutput = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_outputs")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(aggregate(id("first"), merged, Set.of()))
                    .computation(aggregate(id("second"), merged, Set.of())).build()
        );
        assertEquals(Codes.planInvalid, duplicateOutput.code());
        assertTrue(
            Objects.requireNonNull(duplicateOutput.getMessage(), "message").contains("two computations produce"),
            duplicateOutput.getMessage()
        );
    }

    @Test
    void anUnknownComputationKindIsBpt0007() {
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        ContextKey<Long> output = ContextKeys.of(id("out"), Long.class, Retention.instance);
        Computation<Long> foreign = new Computation<>() {
            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of();
            }

            @Override
            public CuboidSet region() {
                return CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
            }
        };
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("foreign")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check).computation(foreign)
                    .build()
        );
        assertEquals(Codes.planInvalid, rejected.code());
        assertTrue(
            Objects.requireNonNull(rejected.getMessage(), "message").contains("unknown computation kind"),
            rejected.getMessage()
        );
    }

    @Test
    void derivationRulesAreBpt0008() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DerivationSpec(id("deriver"), Value.object(), id("out"), -1)
        );
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        DerivationSpec first = new DerivationSpec(id("a"), Value.object(), id("out"), 0);
        DerivationSpec second = new DerivationSpec(id("b"), Value.object(), id("out"), 0);
        BlueprintRejected duplicateOrder = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_orders")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check).derive(first)
                    .derive(second).build()
        );
        assertEquals(Codes.derivationInvalid, duplicateOrder.code());
        assertTrue(
            Objects.requireNonNull(duplicateOrder.getMessage(), "message").contains("duplicate derivation order"),
            duplicateOrder.getMessage()
        );

        DerivationSpec otherOrder = new DerivationSpec(id("b"), Value.object(), id("out"), 1);
        BlueprintRejected duplicateOutput = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("twin_outputs")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check).derive(first)
                    .derive(otherOrder).build()
        );
        assertEquals(Codes.derivationInvalid, duplicateOutput.code());
        assertTrue(
            Objects.requireNonNull(duplicateOutput.getMessage(), "message").contains("two derivations produce"),
            duplicateOutput.getMessage()
        );

        ContextKey<Long> volume = ContextKeys.of(id("volume"), Long.class, Retention.instance, (left, right) -> left);
        BlueprintRejected derivationAndComputation = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("clash")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(Computations.volume(volume, CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))))
                    .derive(new DerivationSpec(id("a"), Value.object(), id("volume"), 0)).build()
        );
        assertEquals(Codes.derivationInvalid, derivationAndComputation.code());
        assertTrue(
            Objects.requireNonNull(derivationAndComputation.getMessage(), "message")
                    .contains("a derivation and a computation produce"),
            derivationAndComputation.getMessage()
        );
    }

    @Test
    void seamRulesAreBpt0009() {
        Bay bay = anyCellBay();
        BlueprintRejected empty = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("empty_seam")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), bay))
                    .seam(new Seam(new SeamId(id("void")), Cuboid.of(0, 0, 0, 0, 1, 1), SeamKind.item, Map.of()))
                    .build()
        );
        assertEquals(Codes.seamOutside, empty.code());
        assertTrue(Objects.requireNonNull(empty.getMessage(), "message").contains("is empty"), empty.getMessage());

        BlueprintRejected outside = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("outside_seam")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                    .check(anyCellCheck(id("cells"), bay))
                    .seam(new Seam(new SeamId(id("stray")), Cuboid.of(1, 0, 0, 2, 1, 1), SeamKind.item, Map.of()))
                    .build()
        );
        assertEquals(Codes.seamOutside, outside.code());
        assertTrue(
            Objects.requireNonNull(outside.getMessage(), "message").contains("leaves every bay"),
            outside.getMessage()
        );
    }

    @Test
    void aDeclaredFootprintOutsideBoundsIsBpt0010() {
        Bay declared = Bay.of(
            id("declared"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.declared(CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1))),
            Sharing.shared
        );
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("declared")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                    .check(anyCellCheck(id("cells"), declared)).build()
        );
        assertEquals(Codes.footprintOutside, rejected.code());
    }

    @Test
    void aSubjectMismatchIsChk0001AtConstruction() {
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            BuildRulesTest::buildMismatchedSubjectCheck
        );
        assertTrue(
            Objects.requireNonNull(rejected.getMessage(), "message").startsWith("SC-CHK-0001"),
            rejected.getMessage()
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void buildMismatchedSubjectCheck() {
        Check.of(id("mismatched"), anyCellBay(), (Focus) Focuses.bay(), (Expectation) Expectations.anyOccupant());
    }

    @Test
    void aFocusDecidingOutsideItsBayIsChk0003() {
        Bay bay = Bay.of(
            id("bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        Check<?> outside = Check
                .of(id("outside"), bay, Focuses.occupant(LatticePoint.of(1, 0, 0)), Expectations.anyOccupant());
        BlueprintRejected rejected = assertThrows(
            BlueprintRejected.class,
            () -> Blueprint.builder(id("outside_focus")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1)).check(outside).build()
        );
        assertEquals(Codes.focusOutside, rejected.code());
    }

    @Test
    void planOrdersSectionsAggregatesAndChecks() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> first = ContextKeys.of(id("first"), Long.class, Retention.instance, Merges.sum());
        ContextKey<Long> second = ContextKeys.of(id("second"), Long.class, Retention.instance, Merges.sum());
        ContextKey<Long> aOut = ContextKeys.of(id("a_out"), Long.class, Retention.instance);
        ContextKey<Long> bOut = ContextKeys.of(id("b_out"), Long.class, Retention.instance);
        ContextKey<Long> cOut = ContextKeys.of(id("c_out"), Long.class, Retention.instance);
        Bay bay = Bay.of(id("bay"), region, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> one = Check.of(id("one"), bay, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant());
        Check<?> two = Check.of(id("two"), bay, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant());
        Blueprint blueprint = Blueprint.builder(id("ordered")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(one).check(two)
                .computation(Computations.volume(second, region)).computation(Computations.volume(first, region))
                .computation(aggregate(aOut, Set.of(bOut))).computation(aggregate(cOut, Set.of()))
                .computation(aggregate(bOut, Set.of())).build();

        assertEquals(
            List.of(id("second"), id("first")),
            blueprint.plan().sections().stream().map(SectionComputation::id).toList(),
            "sections keep declaration order"
        );
        assertEquals(
            List.of(id("c_out"), id("b_out"), id("a_out")),
            blueprint.plan().aggregates().stream().map(AggregateComputation::id).toList(),
            "aggregates follow dependencies, ties broken by declaration order"
        );
        assertEquals(List.of(id("one"), id("two")), blueprint.plan().checks().stream().map(Check::id).toList());
    }

    @Test
    void aggregateOrderBreaksTiesByDeclarationOrder() {
        ContextKey<Long> aOut = ContextKeys.of(id("a_out"), Long.class, Retention.instance);
        ContextKey<Long> bOut = ContextKeys.of(id("b_out"), Long.class, Retention.instance);
        ContextKey<Long> cOut = ContextKeys.of(id("c_out"), Long.class, Retention.instance);
        Check<?> check = anyCellCheck(id("cells"), anyCellBay());
        Blueprint blueprint = Blueprint.builder(id("tie_order")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                .computation(aggregate(bOut, Set.of())).computation(aggregate(cOut, Set.of()))
                .computation(aggregate(aOut, Set.of(bOut))).build();
        assertEquals(
            List.of(id("b_out"), id("c_out"), id("a_out")),
            blueprint.plan().aggregates().stream().map(AggregateComputation::id).toList()
        );
    }

    @Test
    void blueprintReadsEqualTheUnionOfChecksAndComputationRegions() {
        Bay bay = Bay.of(
            id("bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        Check<?> probe = Check.of(id("probe"), bay, Focuses.occupant(LatticePoint.origin), Expectations.anyOccupant());
        ContextKey<Long> out = ContextKeys.of(id("out"), Long.class, Retention.instance, Merges.sum());
        Blueprint blueprint = Blueprint.builder(id("reads_union")).bounds(Cuboid.of(0, 0, 0, 4, 1, 1)).check(probe)
                .computation(Computations.volume(out, CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1)))).build();
        assertEquals(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(3, 0, 0, 4, 1, 1)), blueprint.reads().cells());
    }

    @Test
    void orientationPolicySelectsTheFirstInOrdinalOrder() {
        OrientationPolicy policy = OrientationPolicy.builder(id("front"))
                .allowTurns(Turn.counterclockwise, Turn.clockwise).build();
        assertEquals(
            Orientation.of(Turn.clockwise, Tilt.level, Flip.none),
            policy.select(Heading.north, Heading.east),
            "clockwise comes before counterclockwise in ordinal order"
        );
        assertEquals(
            Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none),
            policy.select(Heading.north, Heading.west)
        );
        assertNull(policy.select(Heading.north, Heading.up), "a level turn never maps north to up");
    }

    private static Bay anyCellBay() {
        return Bay.of(
            id("bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
    }

    private static Check<?> anyCellCheck(NamespacedId checkId, Bay bay) {
        return Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
    }

    private static Focus<OccupantSnapshot> nonClaimableFocus() {
        return new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                throw new AssertionError("build never runs a focus");
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

    private static Focus<String> stringFocus() {
        return new Focus<>() {
            @Override
            public FocusResult<String> take(FocusContext ctx) {
                throw new AssertionError("build never runs a focus");
            }

            @Override
            public Class<String> subject() {
                return String.class;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }
        };
    }

    private static AggregateComputation<Long> aggregate(ContextKey<Long> output, Set<ContextKey<?>> inputs) {
        return aggregate(output.id(), output, inputs);
    }

    private static AggregateComputation<Long> aggregate(
        NamespacedId computationId,
        ContextKey<Long> output,
        Set<ContextKey<?>> inputs
    ) {
        return new AggregateComputation<>() {
            @Override
            public NamespacedId id() {
                return computationId;
            }

            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return inputs;
            }

            @Override
            public CuboidSet region() {
                return CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
            }

            @Override
            public Outcome<Long> compute(AggregateContext context) {
                return Outcome.missing();
            }
        };
    }

    private static SectionComputation<Long> section(
        ContextKey<Long> output,
        CuboidSet region,
        Set<ContextKey<?>> inputs
    ) {
        return new SectionComputation<>() {
            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return inputs;
            }

            @Override
            public CuboidSet region() {
                return region;
            }

            @Override
            public Outcome<Long> compute(SectionContext context) {
                return Outcome.found(0L);
            }
        };
    }
}
