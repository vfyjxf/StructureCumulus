package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expanded N-row claim matrix, planner, ordering and ledger stress. */
class ExpandedClaimMatrixTest {

    private static final InstanceId instance = MagicFixtures.instance;
    private static final InstanceId otherInstance = MagicFixtures.rivalInstance;
    private static final Siting siting = MagicFixtures.turn(LatticePoint.origin, Turn.none);
    private static final Siting otherSiting = MagicFixtures.turn(LatticePoint.of(10, 0, 0), Turn.none);

    @Test
    void claimMatrixIsExactForEightyMixedRows() {
        List<ClaimRow> rows = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            rows.add(
                new ClaimRow(
                    LatticeKey.of(LatticePoint.of(i % 5, (i / 5) % 3, 0)),
                    i % 2 == 0 ? Sharing.exclusive : Sharing.shared,
                    i % 4 == 0 ? otherSiting : siting,
                    MagicFixtures.id("bay_" + (i % 7)),
                    i % 3 == 0 ? instance : otherInstance
                )
            );
        }

        for (ClaimRow a : rows) {
            assertFalse(ClaimMatrix.conflicts(a, a), "a row never conflicts with itself");
            for (ClaimRow b : rows) {
                boolean expected = a.cell().equals(b.cell())
                        && !a.instance().equals(b.instance())
                        && (a.sharing().blocks() || b.sharing().blocks());
                assertEquals(expected, ClaimMatrix.conflicts(a, b), "matrix is exact for " + a + " vs " + b);
                assertEquals(ClaimMatrix.conflicts(a, b), ClaimMatrix.conflicts(b, a), "matrix is symmetric");
            }
        }
    }

    @Test
    void sharedAndExclusiveOverlapStayExactAtOneHundredRows() {
        LatticePoint cell = LatticePoint.of(3, 4, 5);
        for (int i = 0; i < 50; i++) {
            ClaimRow shared = row(cell, Sharing.shared, instance, siting, "shared_" + i);
            ClaimRow exclusive = row(cell, Sharing.exclusive, otherInstance, otherSiting, "exclusive_" + i);
            assertFalse(ClaimMatrix.conflicts(shared, shared));
            assertTrue(ClaimMatrix.conflicts(shared, exclusive));
            assertTrue(ClaimMatrix.conflicts(exclusive, shared));
            ClaimRow otherExclusive = row(cell, Sharing.exclusive, instance, otherSiting, "other_exclusive_" + i);
            assertTrue(ClaimMatrix.conflicts(exclusive, otherExclusive), "two distinct exclusive rows conflict");
        }
        for (int i = 0; i < 10; i++) {
            ClaimRow a = row(cell, Sharing.shared, instance, siting, "same_key_" + i);
            ClaimRow b = row(cell, Sharing.exclusive, instance, siting, "same_key_" + i);
            assertFalse(ClaimMatrix.conflicts(a, b), "one instance is one lifecycle object even across sharing");
        }
    }

    @Test
    void distinctRowsWithTheSameCellBayAndSharingAreCanonicalAcrossInputOrders() {
        LatticePoint cell = LatticePoint.of(7, 0, 0);
        ClaimRow first = row(cell, Sharing.shared, instance, siting, "tied_bay");
        ClaimRow second = row(cell, Sharing.shared, otherInstance, otherSiting, "tied_bay");

        ClaimSet forward = new ClaimSet(List.of(first, second), 1);
        ClaimSet backward = new ClaimSet(List.of(second, first), 1);
        ClaimView forwardView = new ClaimView(List.of(first, second), 1, true);
        ClaimView backwardView = new ClaimView(List.of(second, first), 1, true);
        ClaimVerdict.Settled forwardSettled = new ClaimVerdict.Settled(1, List.of(first, second));
        ClaimVerdict.Settled backwardSettled = new ClaimVerdict.Settled(1, List.of(second, first));

        ClaimLedger ledger = MagicFixtures.ledger();
        assertTrue(
            ledger.settle(new SettleRequest(instance, List.of(first), 0)) instanceof ClaimVerdict.Settled
        );
        assertTrue(
            ledger.settle(new SettleRequest(otherInstance, List.of(second), 1)) instanceof ClaimVerdict.Settled
        );
        ClaimSet ledgerSet = new ClaimSet(TestWorld.rows(ledger), ledger.revision());
        assertEquals(2, ledgerSet.rows().size(), "the ledger can hold both tied rows");
        ClaimSet ledgerReversed = new ClaimSet(
            List.of(ledgerSet.rows().get(1), ledgerSet.rows().get(0)),
            ledgerSet.revision()
        );

        assertAll(
            () -> assertEquals(forward, backward, "ClaimSet rows must be canonical for distinct tied rows"),
            () -> assertEquals(forwardView, backwardView, "ClaimView rows must be canonical for distinct tied rows"),
            () -> assertEquals(
                forwardSettled,
                backwardSettled,
                "Settled rows must be canonical for distinct tied rows"
            ),
            () -> assertEquals(ledgerSet, ledgerReversed, "ledger rows must be canonical for distinct tied rows")
        );
    }

    @Test
    void claimSetAndViewSortOneHundredRowsAndKeepDuplicatesAndEdges() {
        List<ClaimRow> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(
                row(
                    LatticePoint.of(i % 10, (i / 10) % 10, 0),
                    i % 2 == 0 ? Sharing.shared : Sharing.exclusive,
                    i % 2 == 0 ? instance : otherInstance,
                    i % 3 == 0 ? otherSiting : siting,
                    "bay_" + i
                )
            );
        }
        rows.add(rows.get(0));
        ClaimSet set = new ClaimSet(rows, 9);
        assertEquals(101, set.rows().size(), "duplicate rows are retained");
        assertSorted(set.rows());
        assertThrows(UnsupportedOperationException.class, () -> set.rows().add(set.rows().get(0)));

        ClaimView view = new ClaimView(rows, 9, true);
        assertSorted(view.rows());
        assertEquals(11, view.rowsIn(Cuboid.of(0, 0, 0, 1, 10, 1)).size());
        assertTrue(view.rowsIn(Cuboid.of(100, 100, 100, 101, 101, 101)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ClaimView(rows, 9, false));
        assertThrows(IllegalArgumentException.class, () -> new ClaimSet(rows, -1));
        assertEquals(List.of(), ClaimView.unavailable().rows());
    }

    @Test
    void claimPlannerBoxesAllCellsAtScale() {
        List<Bay> bays = new ArrayList<>();
        List<Check<?>> checks = new ArrayList<>();
        CuboidSet expectedOwned = CuboidSet.empty();
        int expectedRows = 0;
        for (int i = 0; i < 12; i++) {
            CuboidSet cells = box(i * 3, 0, 0, i * 3 + 2, 1, 1);
            Bay bay = MagicFixtures.bay(
                MagicFixtures.id("box_bay_" + i),
                cells,
                Coverage.everyCell,
                Footprint.box(),
                i % 2 == 0 ? Sharing.shared : Sharing.exclusive
            );
            bays.add(bay);
            checks.add(
                Check.of(
                    MagicFixtures.id("box_check_" + i),
                    bay,
                    Focuses.bay(),
                    Expectations.eachCell(Expectations.anyOccupant())
                )
            );
            expectedOwned = expectedOwned.union(cells);
            expectedRows += count(cells);
        }
        Blueprint blueprint = blueprint("boxes", Cuboid.of(0, 0, 0, 40, 1, 1), checks);

        ClaimPlanner.Derived derived = ClaimPlanner.derive(blueprint, siting, instance, List.of());

        assertEquals(expectedRows, derived.rows().size());
        assertEquals(expectedOwned, derived.footprint());
        assertSorted(derived.rows());
        assertEquals(expectedRows, derived.rows().stream().map(ClaimRow::cell).distinct().count());
        for (ClaimRow row : derived.rows()) {
            assertEquals(instance, row.instance());
            assertEquals(siting, row.siting());
        }
    }

    @Test
    void claimPlannerBoundUnionsManySubjectsAndIgnoresAir() {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("bound_bay"),
            box(0, 0, 0, 10, 1, 1),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.shared
        );
        Blueprint blueprint = blueprint(
            "bound",
            Cuboid.of(0, 0, 0, 10, 1, 1),
            Check.of(
                MagicFixtures.id("bound_check"),
                bay,
                Focuses.bay(),
                Expectations.eachCell(Expectations.anyOccupant())
            )
        );

        List<OccupantSnapshot> subjects = new ArrayList<>();
        Set<LatticePoint> nonAir = new LinkedHashSet<>();
        for (int i = 0; i < 60; i++) {
            LatticePoint point = LatticePoint.of(i % 10, 0, 0);
            OccupantSnapshot subject = i % 5 == 0
                    ? TestWorld.airOccupant(point)
                    : TestWorld.block(MagicFixtures.id("type"), point);
            subjects.add(subject);
            if (!subject.isAir()) {
                nonAir.add(point);
            }
            if (i % 7 == 0) {
                subjects.add(subject);
            }
        }

        ClaimPlanner.Derived derived = ClaimPlanner
                .derive(blueprint, siting, instance, List.of(new BayBinding(bay, subjects)));

        assertEquals(nonAir.size(), derived.rows().size(), "air is ignored and duplicate subjects are idempotent");
        assertEquals(nonAir, cells(derived.rows()));
        assertSorted(derived.rows());

        Bay emptyBay = MagicFixtures.bay(
            MagicFixtures.id("empty_bound_bay"),
            box(0, 0, 0, 1, 1, 1),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Blueprint emptyBlueprint = blueprint(
            "empty_bound",
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Check.of(
                MagicFixtures.id("empty_bound_check"),
                emptyBay,
                Focuses.bay(),
                Expectations.eachCell(Expectations.anyOccupant())
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ClaimPlanner.derive(
                emptyBlueprint,
                siting,
                instance,
                List.of(new BayBinding(emptyBay, List.of(TestWorld.airOccupant(LatticePoint.origin))))
            ),
            "an exclusive Bound with no non-air subject is SC-CLM-0005"
        );
    }

    @Test
    void sharedOverlappingBaysProduceOneRowPerBayAtScale() {
        LatticePoint cell = LatticePoint.of(2, 0, 0);
        List<Bay> bays = new ArrayList<>();
        List<Check<?>> checks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Bay bay = MagicFixtures.bay(
                MagicFixtures.id("overlap_bay_" + i),
                CuboidSet.of(Cuboid.of(cell)),
                Coverage.everyCell,
                Footprint.box(),
                Sharing.shared
            );
            bays.add(bay);
            checks.add(
                Check.of(
                    MagicFixtures.id("overlap_check_" + i),
                    bay,
                    Focuses.bay(),
                    Expectations.eachCell(Expectations.anyOccupant())
                )
            );
        }
        Blueprint blueprint = blueprint("overlap", Cuboid.of(0, 0, 0, 4, 1, 1), checks);

        ClaimPlanner.Derived derived = ClaimPlanner.derive(blueprint, siting, instance, List.of());

        assertEquals(40, derived.rows().size());
        assertEquals(CuboidSet.of(Cuboid.of(cell)), derived.footprint());
        assertSorted(derived.rows());
        for (ClaimRow row : derived.rows()) {
            assertEquals(Sharing.shared, row.sharing());
            assertEquals(instance, row.instance());
            assertEquals(siting, row.siting());
        }
        for (int i = 0; i < derived.rows().size(); i++) {
            for (int j = i + 1; j < derived.rows().size(); j++) {
                assertFalse(ClaimMatrix.conflicts(derived.rows().get(i), derived.rows().get(j)));
            }
        }
    }

    @Test
    void claimPlannerDeclaredAndNoneAtScale() {
        List<Check<?>> declaredChecks = new ArrayList<>();
        CuboidSet expected = CuboidSet.empty();
        for (int i = 0; i < 20; i++) {
            CuboidSet declared = box(i * 2, 0, 0, i * 2 + 1, 1, 1);
            Bay bay = MagicFixtures.bay(
                MagicFixtures.id("declared_bay_" + i),
                box(i * 2, 0, 0, i * 2 + 1, 1, 1),
                Coverage.everyCell,
                Footprint.declared(declared),
                Sharing.shared
            );
            declaredChecks.add(
                Check.of(
                    MagicFixtures.id("declared_check_" + i),
                    bay,
                    Focuses.bay(),
                    Expectations.eachCell(Expectations.anyOccupant())
                )
            );
            expected = expected.union(declared);
        }
        Blueprint declaredBlueprint = blueprint("declared", Cuboid.of(0, 0, 0, 41, 1, 1), declaredChecks);
        ClaimPlanner.Derived declared = ClaimPlanner.derive(declaredBlueprint, siting, instance, List.of());
        assertEquals(expected, declared.footprint());
        assertEquals(cells(declared.rows()), cells(expected));
        assertSorted(declared.rows());

        Bay noneBay = MagicFixtures.bay(
            MagicFixtures.id("none_bay"),
            box(0, 0, 0, 1, 1, 1),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        Blueprint noneBlueprint = blueprint(
            "none",
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Check.of(
                MagicFixtures.id("none_check"),
                noneBay,
                Focuses.bay(),
                Expectations.eachCell(Expectations.anyOccupant())
            )
        );
        ClaimPlanner.Derived none = ClaimPlanner.derive(noneBlueprint, siting, instance, List.of());
        assertTrue(none.rows().isEmpty());
        assertEquals(CuboidSet.empty(), none.footprint());
    }

    @Test
    void claimPlannerSitingTranslationAtScale() {
        List<Check<?>> checks = new ArrayList<>();
        CuboidSet local = CuboidSet.empty();
        for (int i = 0; i < 10; i++) {
            CuboidSet cells = box(i, 0, 0, i + 1, 1, 1);
            Bay bay = MagicFixtures.bay(
                MagicFixtures.id("siting_bay_" + i),
                cells,
                Coverage.everyCell,
                Footprint.box(),
                Sharing.shared
            );
            checks.add(
                Check.of(
                    MagicFixtures.id("siting_check_" + i),
                    bay,
                    Focuses.bay(),
                    Expectations.eachCell(Expectations.anyOccupant())
                )
            );
            local = local.union(cells);
        }
        Blueprint blueprint = blueprint("siting", Cuboid.of(0, 0, 0, 11, 1, 1), checks);
        Siting turned = MagicFixtures.turn(LatticePoint.of(5, 6, 7), Turn.clockwise);

        ClaimPlanner.Derived derived = ClaimPlanner.derive(blueprint, turned, instance, List.of());

        CuboidSet expected = Objects.requireNonNull(turned.toWorld(local), "world cells");
        assertEquals(expected, derived.footprint());
        assertEquals(cells(derived.rows()), cells(expected));
        for (ClaimRow row : derived.rows()) {
            assertEquals(turned, row.siting());
        }
    }

    @Test
    void ledgerRevisionsAndWholeInstanceReplacementAtScale() {
        ClaimLedger ledger = MagicFixtures.ledger();
        List<InstanceId> instances = new ArrayList<>();
        List<Siting> sitings = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            InstanceId eachInstance = new InstanceId(TestWorld.world, 100 + i);
            instances.add(eachInstance);
            Siting each = MagicFixtures.turn(LatticePoint.of(i * 10, 0, 0), Turn.none);
            sitings.add(each);
            ClaimRow row = row(LatticePoint.of(i, 0, 0), Sharing.shared, eachInstance, each, "ledger_bay_" + i);
            ClaimVerdict verdict = ledger
                    .settle(new SettleRequest(eachInstance, List.of(row), ledger.revision()));
            assertTrue(verdict instanceof ClaimVerdict.Settled);
            assertEquals(i + 1, ((ClaimVerdict.Settled) verdict).revision());
        }
        assertEquals(30, ledger.read(Cuboid.of(0, 0, 0, 30, 1, 1)).rows().size());
        assertEquals(30, ledger.read(Cuboid.of(0, 0, 0, 30, 1, 1)).revision());

        List<ClaimRow> replacement = List.of(
            row(LatticePoint.of(0, 0, 0), Sharing.shared, instances.get(0), sitings.get(0), "ledger_bay_0"),
            row(LatticePoint.of(1, 0, 0), Sharing.shared, instances.get(0), sitings.get(0), "ledger_bay_1")
        );
        ClaimVerdict replaced = ledger
                .settle(new SettleRequest(instances.get(0), replacement, ledger.revision()));
        assertTrue(replaced instanceof ClaimVerdict.Settled);
        assertEquals(31, ledger.read(Cuboid.of(0, 0, 0, 30, 1, 1)).rows().size(), "the whole instance is replaced");
        assertEquals(31, ledger.revision());

        ClaimVerdict stale = ledger.settle(new SettleRequest(instances.get(0), replacement, 0));
        assertTrue(stale instanceof ClaimVerdict.RevisionMoved);

        for (int i = 0; i < 10; i++) {
            ClaimVerdict released = ledger.release(instances.get(0));
            assertTrue(released instanceof ClaimVerdict.Settled);
        }
        assertEquals(29, ledger.read(Cuboid.of(0, 0, 0, 30, 1, 1)).rows().size());
        assertEquals(32, ledger.revision(), "release of an absent instance does not bump the revision");
    }

    @Test
    void sharedInstancesAndExclusiveConflictAtScale() {
        ClaimLedger ledger = MagicFixtures.ledger();
        int cells = 10;
        for (int c = 0; c < cells; c++) {
            for (int o = 0; o < 5; o++) {
                InstanceId current = new InstanceId(TestWorld.world, 1_000 + c * 5L + o);
                Siting each = MagicFixtures.turn(LatticePoint.of(c * 100 + o * 10, 0, 0), Turn.none);
                ClaimRow row = row(LatticePoint.of(c, 0, 0), Sharing.shared, current, each, "shared_" + o);
                ClaimVerdict verdict = ledger.settle(new SettleRequest(current, List.of(row), ledger.revision()));
                assertTrue(verdict instanceof ClaimVerdict.Settled, "shared rows coexist at " + c + "/" + o);
            }
        }
        assertEquals(cells * 5, ledger.read(Cuboid.of(0, 0, 0, cells, 1, 1)).rows().size());
        long before = ledger.revision();

        InstanceId exclusiveInstance = new InstanceId(TestWorld.world, 9_999);
        ClaimRow exclusive = row(
            LatticePoint.of(0, 0, 0),
            Sharing.exclusive,
            exclusiveInstance,
            otherSiting,
            "exclusive_bay"
        );
        ClaimVerdict conflict = ledger
                .settle(new SettleRequest(exclusiveInstance, List.of(exclusive), ledger.revision()));
        assertTrue(conflict instanceof ClaimVerdict.Conflict);
        assertEquals(before, ledger.revision(), "a conflict leaves the ledger untouched");
        assertEquals(cells * 5, ledger.read(Cuboid.of(0, 0, 0, cells, 1, 1)).rows().size());
    }

    @Test
    void duplicateRowsAndRevisionGuardsAtScale() {
        ClaimRow row = row(LatticePoint.of(1, 1, 1), Sharing.exclusive, instance, siting, "duplicate_bay");
        ClaimSet set = new ClaimSet(List.of(row, row, row, row), 1);
        assertEquals(4, set.rows().size(), "duplicate rows are a representation detail and stay");
        assertEquals(1, set.revision());
        assertThrows(IllegalArgumentException.class, () -> new ClaimSet(List.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.Settled(0, List.of(row)));
        assertEquals(0, new ClaimSet(List.of(), 0).revision());
    }

    private static ClaimRow row(LatticePoint cell, Sharing sharing, InstanceId instance, Siting siting, String bay) {
        return new ClaimRow(LatticeKey.of(cell), sharing, siting, MagicFixtures.id(bay), instance);
    }

    private static Blueprint blueprint(String path, Cuboid bounds, Check<?>... checks) {
        return blueprint(path, bounds, List.of(checks));
    }

    private static Blueprint blueprint(String path, Cuboid bounds, List<? extends Check<?>> checks) {
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds);
        for (Check<?> check : checks) {
            builder.check(check);
        }
        return builder.build();
    }

    private static CuboidSet box(int x, int y, int z, int maxX, int maxY, int maxZ) {
        return CuboidSet.of(Cuboid.of(x, y, z, maxX, maxY, maxZ));
    }

    private static int count(CuboidSet cells) {
        int count = 0;
        for (Cuboid box : cells.boxes()) {
            count += (box.max().x() - box.min().x())
                    * (box.max().y() - box.min().y())
                    * (box.max().z() - box.min().z());
        }
        return count;
    }

    private static Set<LatticePoint> cells(List<ClaimRow> rows) {
        Set<LatticePoint> cells = new LinkedHashSet<>();
        for (ClaimRow row : rows) {
            cells.add(row.cell().point());
        }
        return cells;
    }

    private static Set<LatticePoint> cells(CuboidSet set) {
        Set<LatticePoint> cells = new LinkedHashSet<>();
        for (Cuboid box : set.boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        cells.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return cells;
    }

    private static void assertSorted(List<ClaimRow> rows) {
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).compareTo(rows.get(i)) <= 0, "rows are canonical");
        }
    }
}
