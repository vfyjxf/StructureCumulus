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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureValidationTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    @Test
    void sectionComputationHasNoInputs() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> a = ContextKeys.of(id("a"), Long.class, Retention.INSTANCE, (l, r) -> l);
        ContextKey<Long> b = ContextKeys.of(id("b"), Long.class, Retention.INSTANCE, (l, r) -> l);
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
        Bay bay = Bay.of(id("bay"), cells, Coverage.EVERY_CELL, Footprint.box(), Sharing.EXCLUSIVE);
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
        ContextKey<Long> a = ContextKeys.of(id("a"), Long.class, Retention.INSTANCE, (l, r) -> l);
        Bay bay = Bay.of(id("bay"), cells, Coverage.EVERY_CELL, Footprint.box(), Sharing.EXCLUSIVE);
        Check<?> check = Check.of(id("cells"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("dup")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(Computations.volume(a, cells)).computation(Computations.volume(a, cells)).build()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("empty")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).check(check)
                    .computation(Computations.volume(a, CuboidSet.empty())).build()
        );
    }

    @Test
    void whenAbsentMustMatchTheKeyType() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ContextKey(id("bad"), Long.class, Retention.INSTANCE, null, "x")
        );
    }

    @Test
    void aConflictExplainsItself() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.Conflict(List.of(), FindingBundle.empty()));
    }
}
