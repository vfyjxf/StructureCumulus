package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureValidationTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    @Test
    void sectionComputationHasNoInputs() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> a = ContextKeys.of(id("a"), Long.class, Retention.instance, (l, r) -> l);
        ContextKey<Long> b = ContextKeys.of(id("b"), Long.class, Retention.instance, (l, r) -> l);
        SectionComputation<Long> first = Computations.volume(a, cells);
        SectionComputation<Long> second = new SectionComputation<>() {
            @Override
            public Outcome<Long> compute(SectionContext ctx) {
                return Outcome.found(0L);
            }

            @Override
            public NamespacedId id() {
                return StructureValidationTest.id("second");
            }

            @Override
            public ContextKey<Long> output() {
                return b;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of(a);
            }

            @Override
            public CuboidSet region() {
                return cells;
            }
        };
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> check = Check.of(id("cells"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("bad")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check).computation(first)
                    .computation(second).build()
        );
    }

    @Test
    void duplicateOutputAndEmptyRegionAreBuildErrors() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> a = ContextKeys.of(id("a"), Long.class, Retention.instance, (l, r) -> l);
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> check = Check.of(id("cells"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        IllegalArgumentException duplicate = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("dup")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(Computations.volume(a, cells)).computation(Computations.volume(a, cells)).build()
        );
        assertTrue(
            Objects.requireNonNull(duplicate.getMessage(), "message").startsWith("SC-BPT-0007"),
            duplicate.getMessage()
        );
        IllegalArgumentException emptyRegion = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("empty")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(Computations.volume(a, CuboidSet.empty())).build()
        );
        assertTrue(
            Objects.requireNonNull(emptyRegion.getMessage(), "message").startsWith("SC-BPT-0007"),
            emptyRegion.getMessage()
        );
    }

    @Test
    void aConflictExplainsItself() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.Conflict(List.of(), FindingBundle.empty()));
    }

    @Test
    void blueprintReadsCarryTraitsCellsAndDepth() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        TraitKey<Long> energy = new TraitKey<>(id("energy"), Long.class);
        Bay bay = Bay.of(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Check<?> check = Check.of(
            id("trait"),
            bay,
            Focuses.occupant(LatticePoint.origin),
            Expectations.allOf(Expectations.anyOccupant(), Expectations.trait(energy))
        );
        ContextKey<Long> out = ContextKeys.of(id("out"), Long.class, Retention.instance, (left, right) -> left);
        Blueprint blueprint = Blueprint.builder(id("reads")).bounds(Cuboid.of(0, 0, 0, 4, 1, 1)).check(check)
                .computation(Computations.volume(out, CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1)))).build();

        ReadScope reads = blueprint.reads();
        assertEquals(Set.of(energy.id()), reads.traits());
        assertTrue(reads.cells().contains(LatticePoint.of(3, 0, 0)));
        assertTrue(reads.keys().isEmpty());
        assertTrue(reads.kinds().isEmpty());
        assertEquals(0, reads.depth());
    }

    @Test
    void entityAndNestedReadsDeclareKindsAndWindows() {
        CuboidSet penCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay pen = Bay.of(id("pen"), penCells, Coverage.anySubject, Footprint.box(), Sharing.exclusive);
        Check<?> entity = Check.of(id("mob"), pen, Focuses.entity(id("mob")), Expectations.anyOccupant());
        Blueprint entityBlueprint = Blueprint.builder(id("entity")).bounds(Cuboid.of(0, 0, 0, 4, 4, 4)).check(entity)
                .build();
        ReadScope entityReads = entityBlueprint.reads();
        assertTrue(entityReads.kinds().contains(OccupantKind.entity));
        assertEquals(1, entityReads.windows().size());
        assertEquals(0, entityReads.depth());

        CuboidSet nestedCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Bay nestedBay = Bay.of(id("nested_bay"), nestedCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Expectation<WorldView> expectation = Expectations
                .custom(WorldView.class, ExpectationMeta.anonymous(), ctx -> Reply.match());
        Check<?> nested = Check.of(id("nested"), nestedBay, Focuses.nested(LatticePoint.origin), expectation);
        Blueprint nestedBlueprint = Blueprint.builder(id("nested")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(nested)
                .build();
        ReadScope nestedReads = nestedBlueprint.reads();
        assertTrue(nestedReads.kinds().contains(OccupantKind.nested));
        assertEquals(1, nestedReads.depth());
    }

    @Test
    void readScopeNarrowsOnlyUpward() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        NamespacedId energy = id("energy");
        NamespacedId lit = id("lit");
        ReadScope narrow = ReadScope.cells(cells).withDepth(1).withTraits(Set.of(energy));
        assertEquals(2, narrow.withDepth(2).depth());
        assertEquals(1, narrow.withDepth(0).depth());
        assertEquals(0, narrow.kinds().size());
        assertEquals(1, narrow.withKinds(Set.of(OccupantKind.entity)).kinds().size());

        ReadScope union = narrow
                .union(ReadScope.none().withDepth(3).withTraits(Set.of(lit)).withKinds(Set.of(OccupantKind.entity)));
        assertEquals(3, union.depth());
        assertEquals(Set.of(energy, lit), union.traits());
        assertTrue(union.kinds().contains(OccupantKind.entity));

        EntityWindow window = EntityWindow.of(Objects.requireNonNull(cells.bounds(), "bounds"), id("mob"));
        ReadScope windows = ReadScope.none().withWindows(List.of(window)).withWindows(List.of(window));
        assertEquals(1, windows.windows().size());
    }
}
