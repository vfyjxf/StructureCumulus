package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.bay;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.bayBlueprint;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.context;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.finding;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.id;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.identity;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.keyedFinding;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.localFinding;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.occupantBlueprint;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.oneCell;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.stone;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.withoutCapture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The overlay derivation of remedy §5.2: one mark per cell, loudest wins, a marker per locusless finding. */
class StandardPreviewProviderTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final LatticePoint east = LatticePoint.of(1, 0, 0);

    private final StandardPreviewProvider provider = new StandardPreviewProvider();

    @Test
    void theProviderIsTheBuiltinPreview() {
        assertEquals(NamespacedId.of("structurecumulus", "preview"), provider.id());
    }

    @Test
    void aJudgedExpectedCellWithoutAFindingIsPresent() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.block(stone)),
            new RemedyFixtures.View().block(stone, origin),
            keyedFinding(Codes.cancelled)
        );

        assertEquals(Map.of(LatticeKey.of(origin), CellMark.present), overlay.cells());
    }

    @Test
    void anAbsentExpectedOccupantIsMissing() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.block(stone)),
            new RemedyFixtures.View().air(origin),
            finding(Codes.expected, origin)
        );

        assertEquals(CellMark.missing, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void aDifferentOccupantIsWrong() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.block(stone)),
            new RemedyFixtures.View().block(RemedyFixtures.dirt, origin),
            finding(Codes.expected, origin)
        );

        assertEquals(CellMark.wrong, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void aForeignOccupantOnAnAirLeafIsExtra() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.air()),
            new RemedyFixtures.View().block(stone, origin),
            finding(Codes.expected, origin)
        );

        assertEquals(CellMark.extra, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void aMatchedNegationIsExtra() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.anyOccupant()),
            new RemedyFixtures.View().block(stone, origin),
            finding(Codes.negationMatched, origin)
        );

        assertEquals(CellMark.extra, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void anOpenEnvelopeIsExtra() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.anyOccupant()),
            new RemedyFixtures.View().block(stone, origin),
            finding(Codes.envelopeOpen, origin)
        );

        assertEquals(CellMark.extra, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void aClaimConflictIsBlocked() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.block(stone)),
            new RemedyFixtures.View().block(stone, origin),
            finding(Codes.claimConflict, origin)
        );

        assertEquals(CellMark.blocked, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void anUnreadableCellIsUnknownAndNeverMissing() {
        OverlayModel overlay = previewOf(
            occupantBlueprint(Expectations.block(stone)),
            new RemedyFixtures.View().unreadable(origin),
            finding(Codes.expected, origin)
        );

        assertEquals(CellMark.unknown, overlay.cells().get(LatticeKey.of(origin)));
        assertNotEquals(CellMark.missing, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void anUnreadableOrStaleFindingMarksItsCellsUnknown() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));
        RemedyFixtures.View view = new RemedyFixtures.View().block(stone, origin);

        for (CodeEntry code : List.of(
            Codes.unreadableCell,
            Codes.footprintClipped,
            Codes.nestedCut,
            Codes.unreadableRead,
            Codes.snapshotStale
        )) {
            OverlayModel overlay = onlyPreview(provider.solve(finding(code, origin), context(blueprint, view)))
                    .overlay();
            assertEquals(
                CellMark.unknown,
                overlay.cells().get(LatticeKey.of(origin)),
                () -> code + " overlay: " + overlay
            );
            assertNotEquals(CellMark.missing, overlay.cells().get(LatticeKey.of(origin)));
        }
    }

    @Test
    void withoutACaptureEveryExpectedCellIsUnknownAndNeverMissing() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        OverlayModel overlay = onlyPreview(provider.solve(finding(Codes.expected, origin), withoutCapture(blueprint)))
                .overlay();

        assertEquals(CellMark.unknown, overlay.cells().get(LatticeKey.of(origin)));
        assertNotEquals(CellMark.missing, overlay.cells().get(LatticeKey.of(origin)));
    }

    @Test
    void marksAreSortedByLatticeKey() {
        LatticePoint north = LatticePoint.of(0, 0, 1);
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1), Cuboid.of(0, 0, 1, 1, 1, 2));
        Blueprint blueprint = bayBlueprint(bay("bay", cells), Expectations.eachCell(Expectations.block(stone)));
        RemedyFixtures.View view = new RemedyFixtures.View().block(stone, origin).block(stone, east)
                .block(stone, north);

        OverlayModel overlay = previewOf(blueprint, view, keyedFinding(Codes.cancelled));

        assertEquals(
            List.of(LatticeKey.of(origin), LatticeKey.of(east), LatticeKey.of(north)),
            new ArrayList<>(overlay.cells().keySet())
        );
    }

    @Test
    void theLoudestMarkWinsAcrossTheWholeOrder() {
        List<CellMark> order = List.of(
            CellMark.present,
            CellMark.unknown,
            CellMark.extra,
            CellMark.missing,
            CellMark.wrong,
            CellMark.blocked
        );

        for (int left = 0; left < order.size(); left++) {
            for (int right = 0; right < order.size(); right++) {
                assertEquals(
                    order.get(Math.max(left, right)),
                    CellMark.louder(order.get(left), order.get(right)),
                    order.get(left) + " vs " + order.get(right)
                );
            }
        }
        assertEquals(CellMark.expected, CellMark.louder(CellMark.expected, CellMark.present));
    }

    @Test
    void aLocuslessFindingBecomesOneMarker() {
        record Expected(Finding finding, MarkerKind kind) {}
        List<Expected> cases = List.of(
            new Expected(keyedFinding(FindingCode.of(FindingCode.Family.mc, 4)), MarkerKind.warning),
            new Expected(keyedFinding(Codes.anchorInvalid), MarkerKind.error),
            new Expected(keyedFinding(Codes.budgetExhausted), MarkerKind.error),
            new Expected(keyedFinding(Codes.cancelled), MarkerKind.info)
        );
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));
        RemedyFixtures.View view = new RemedyFixtures.View().block(stone, origin);

        for (Expected expected : cases) {
            OverlayModel overlay = onlyPreview(provider.solve(expected.finding(), context(blueprint, view))).overlay();
            assertEquals(
                1,
                overlay.markers().size(),
                () -> expected.finding().code() + " markers: " + overlay.markers()
            );
            Marker marker = overlay.markers().get(0);
            assertEquals(expected.kind(), marker.kind());
            assertEquals(expected.finding().message(), marker.label());
            assertEquals(Objects.requireNonNull(expected.finding().primaryLabel()).locus(), marker.locus());
        }
    }

    @Test
    void aLocalMarkerLocusIsMappedIntoTheWorld() {
        Siting siting = Siting.of(LatticePoint.of(5, 0, 5), identity().placement());
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        OverlayModel overlay = onlyPreview(
            provider.solve(
                localFinding(Codes.cancelled, LatticePoint.of(1, 0, 0)),
                context(blueprint, siting, 8, new RemedyFixtures.View().block(stone, LatticePoint.of(6, 0, 5)), null)
            )
        ).overlay();

        assertEquals(1, overlay.markers().size(), () -> overlay.markers().toString());
        assertEquals(Locus.world(oneCell(LatticePoint.of(6, 0, 5))), overlay.markers().get(0).locus());
    }

    @Test
    void aPinOutsideItsPartIsAnnotatedAndIgnored() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));
        SolvePreferences preferences = new SolvePreferences(
            List.of(),
            Map.of(id("bay"), oneCell(LatticePoint.of(3, 0, 0)))
        );

        OverlayModel overlay = onlyPreview(
            provider.solve(
                finding(Codes.expected, origin),
                RemedyFixtures.preferred(blueprint, new RemedyFixtures.View().air(origin), preferences)
            )
        ).overlay();

        assertEquals(1, overlay.markers().size(), () -> overlay.markers().toString());
        assertEquals(MarkerKind.info, overlay.markers().get(0).kind());
        assertTrue(overlay.markers().get(0).label().contains("outside its legal set"));
    }

    @Test
    void aPinNamingNoPartIsAnnotated() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));
        SolvePreferences preferences = new SolvePreferences(List.of(), Map.of(id("nowhere"), oneCell(origin)));

        OverlayModel overlay = onlyPreview(
            provider.solve(
                finding(Codes.expected, origin),
                RemedyFixtures.preferred(blueprint, new RemedyFixtures.View().air(origin), preferences)
            )
        ).overlay();

        assertEquals(1, overlay.markers().size(), () -> overlay.markers().toString());
        assertEquals(MarkerKind.info, overlay.markers().get(0).kind());
        assertTrue(overlay.markers().get(0).label().contains("names no bay or check"));
    }

    @Test
    void theOverlayBoundsAreTheBlueprintBoundsAtTheSiting() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));
        Siting siting = Siting.of(LatticePoint.of(5, 0, 5), identity().placement());

        OverlayModel overlay = onlyPreview(
            provider.solve(
                keyedFinding(Codes.cancelled),
                context(blueprint, siting, 8, new RemedyFixtures.View().block(stone, LatticePoint.of(5, 0, 5)), null)
            )
        ).overlay();

        assertEquals(Cuboid.of(5, 0, 5, 7, 2, 7), overlay.bounds());
    }

    private OverlayModel previewOf(Blueprint blueprint, RemedyFixtures.View view, Finding finding) {
        return onlyPreview(provider.solve(finding, context(blueprint, view))).overlay();
    }

    private static Remedy.Preview onlyPreview(List<Remedy> remedies) {
        assertEquals(1, remedies.size(), () -> remedies.toString());
        return assertInstanceOf(Remedy.Preview.class, remedies.get(0));
    }
}
