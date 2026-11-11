package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.EditStep;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaMaterializerTest {

    private static final VanillaMaterializer materializer = new VanillaMaterializer();
    private static final LatticePoint cell = LatticePoint.of(3, 4, 5);

    @Test
    void placeResolvesTheBlockAndItsProperties() {
        MaterializedEdit edit = lower(place("minecraft:oak_log", Map.of("axis", "y")), Orientation.identity);
        assertEquals(1, edit.placements().size());
        assertEquals(
            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
            placementAt(edit, cell).state()
        );
        assertEquals(1, edit.items().size());
        assertEquals(1, edit.items().get(0).count(), "one item per placed cell");
        assertEquals(cell, edit.placements().keySet().iterator().next());
    }

    @Test
    void anUnknownBlockAnswersNull() {
        assertNull(materializer.lower(place("structurecumulus:not_a_block", Map.of()), Orientation.identity));
    }

    @Test
    void anUnknownPropertyAnswersNull() {
        assertNull(materializer.lower(place("minecraft:oak_log", Map.of("spin", "true")), Orientation.identity));
    }

    @Test
    void anUnknownValueAnswersNull() {
        assertNull(materializer.lower(place("minecraft:oak_log", Map.of("axis", "w")), Orientation.identity));
    }

    @Test
    void anUprightTurnIsAppliedForward() {
        MaterializedEdit edit = lower(
            place("minecraft:furnace", Map.of("facing", "north")),
            Orientation.of(Turn.clockwise, Tilt.level, Flip.none)
        );
        assertEquals(Direction.EAST, placementAt(edit, cell).state().getValue(HorizontalDirectionalBlock.FACING));
    }

    @Test
    void aPartialFacingIsUnrepresentableUnderANonUprightOrientation() {
        assertNull(
            materializer.lower(
                place("minecraft:furnace", Map.of("facing", "north")),
                Orientation.of(Turn.none, Tilt.pitchUp, Flip.none)
            )
        );
    }

    @Test
    void removeMaterializesAirAndCostsNoItems() {
        MaterializedEdit edit = lower(remove(cells()), Orientation.identity);
        assertTrue(edit.items().isEmpty());
        assertTrue(placementAt(edit, cell).isAir());
    }

    @Test
    void aBlockWithoutAnItemCostsNoItem() {
        MaterializedEdit edit = lower(place("minecraft:water", Map.of()), Orientation.identity);
        assertTrue(edit.items().isEmpty());
    }

    @Test
    void setTraitIsNotMaterializedInV1() {
        assertNull(
            materializer.lower(
                new Edit.SetTrait(
                    cells(),
                    Trait.absent(new TraitKey<>(NamespacedId.of("structurecumulus", "energy"), Integer.class))
                ),
                Orientation.identity
            )
        );
    }

    @Test
    void theIdIsTheBuiltInMaterializer() {
        assertEquals("structurecumulus:vanilla", materializer.id().text());
    }

    private static MaterializedEdit lower(Edit edit, Orientation orientation) {
        return Objects.requireNonNull(materializer.lower(edit, orientation), "materialized");
    }

    private static BlockPlacement placementAt(MaterializedEdit edit, LatticePoint cell) {
        return Objects.requireNonNull(edit.placements().get(cell), "the placement");
    }

    private static CuboidSet cells() {
        return CuboidSet.of(Cuboid.of(cell));
    }

    private static Edit.Place place(String type, Map<String, String> properties) {
        return new Edit.Place(cells(), Objects.requireNonNull(NamespacedId.parse(type), "id"), properties);
    }

    private static Edit.Remove remove(CuboidSet cells) {
        return new Edit.Remove(cells);
    }

    private static EditPlan plan(Edit edit) {
        return new EditPlan(
            List.of(new EditStep(edit, Locus.world(cells()), Applicability.machineApplicable)),
            "test plan"
        );
    }

    @Test
    void aPlanWrapsItsEditForTheGates() {
        EditPlan plan = plan(place("minecraft:stone", Map.of()));
        assertInstanceOf(Edit.Place.class, plan.steps().get(0).edit());
    }
}
