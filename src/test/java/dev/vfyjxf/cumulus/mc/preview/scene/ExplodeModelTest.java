package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The explode offset law of design 17 §8.1: the groups, the direction, the distance and the labels. */
class ExplodeModelTest {

    private static final float eps = 1.0e-5F;
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId base = NamespacedId.of("structurecumulus", "base");
    private static final NamespacedId stack = NamespacedId.of("structurecumulus", "stack");

    private static Stage stage(String name, int cells, NamespacedId bay) {
        return new Stage(name, cells, 0, null, null, bay);
    }

    private static GhostCell ghost(int x, int y, int z, int stage) {
        return new GhostCell(LatticePoint.of(x, y, z), stone, Map.of(), stage);
    }

    /** Two bays, one layer each, stacked along Y so the directions are exactly up and down. */
    private static List<Stage> twoBays() {
        return List.of(stage("base", 2, base), stage("stack", 2, stack));
    }

    private static List<GhostCell> twoBayCells() {
        return List.of(ghost(0, 0, 0, 0), ghost(1, 0, 0, 0), ghost(0, 1, 0, 1), ghost(1, 1, 0, 1));
    }

    @Test
    void twoPartsSeparateAlongTheirOwnCentroidDifference() {
        Hull hull = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F);
        ExplodeModel model = ExplodeModel.of(twoBays(), twoBayCells(), hull);
        assertTrue(model.separable());
        assertEquals(2, model.groups().size());

        ExplodeModel.Group floor = model.groups().get(0);
        assertEquals(base, floor.bay());
        assertEquals(0.0F, floor.directionX(), eps);
        assertEquals(-1.0F, floor.directionY(), eps);
        assertEquals(0.0F, floor.directionZ(), eps);

        ExplodeModel.Group chimney = model.groups().get(1);
        assertEquals(stack, chimney.bay());
        assertEquals(1.0F, chimney.directionY(), eps);

        assertEquals(0.0F, model.offset(twoBayCells().get(0), 0.0F).x(), eps);
        assertEquals(0.0F, model.offset(twoBayCells().get(0), 0.0F).y(), eps);
        ExplodeModel.Offset away = model.offset(twoBayCells().get(0), 1.0F);
        assertEquals(0.0F, away.x(), eps);
        assertEquals(-1.5F, away.y(), eps);
        assertEquals(0.0F, away.z(), eps);
        ExplodeModel.Offset towards = model.offset(twoBayCells().get(2), 1.0F);
        assertEquals(1.5F, towards.y(), eps);
        ExplodeModel.Offset halfway = model.offset(twoBayCells().get(2), 0.5F);
        assertEquals(0.75F, halfway.y(), eps);
    }

    @Test
    void theDistanceIsHalfTheDrawnHullsDiagonal() {
        Hull hull = new Hull(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 2.0F);
        ExplodeModel model = ExplodeModel.of(twoBays(), twoBayCells(), hull);
        assertEquals(0.5F * (float) Math.sqrt(9.0F + 1.0F + 4.0F), model.distance(), eps);
        ExplodeModel.Offset offset = model.offset(twoBayCells().get(2), 1.0F);
        assertEquals(model.distance(), offset.y(), eps);
    }

    @Test
    void oneBayIsOnePartWithTheDegenerateDirectionExactlyPlusY() {
        List<Stage> stages = List.of(stage("cells", 4, base));
        ExplodeModel model = ExplodeModel.of(stages, twoBayCells(), new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F));
        assertFalse(model.separable());
        assertEquals(1, model.groups().size());
        ExplodeModel.Group group = model.groups().get(0);
        assertEquals(0.0F, group.directionX());
        assertEquals(1.0F, group.directionY(), "the degenerate direction is exact, not near-degenerate");
        assertEquals(0.0F, group.directionZ());
        ExplodeModel.Offset offset = model.offset(twoBayCells().get(0), 1.0F);
        assertEquals(0.0F, offset.x());
        assertEquals(model.distance(), offset.y(), eps);
        assertEquals(0.0F, offset.z());
    }

    @Test
    void aBayWithSeveralStagesIsStillOneGroup() {
        List<Stage> stages = List.of(stage("layer 1", 2, base), stage("layer 2", 2, stack));
        List<GhostCell> cells = List.of(ghost(0, 0, 0, 0), ghost(1, 0, 0, 0), ghost(0, 1, 0, 1), ghost(1, 1, 0, 1));
        ExplodeModel model = ExplodeModel.of(stages, cells, new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F));
        assertTrue(model.separable(), "the two stages name two bays");

        List<Stage> sameBay = List.of(stage("layer 1", 2, base), stage("layer 2", 2, base));
        ExplodeModel one = ExplodeModel.of(sameBay, cells, new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F));
        assertFalse(one.separable());
        assertEquals(1, one.groups().size());
        assertEquals(4, one.groups().get(0).cells().size());
    }

    @Test
    void theSummationOrderKeepsShuffledWireOrdersEqual() {
        Hull hull = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F);
        List<GhostCell> forward = twoBayCells();
        List<GhostCell> backward = new ArrayList<>(forward);
        Collections.reverse(backward);
        ExplodeModel first = ExplodeModel.of(twoBays(), forward, hull);
        ExplodeModel second = ExplodeModel.of(twoBays(), backward, hull);
        assertEquals(first.distance(), second.distance());
        assertEquals(first.structureCentroid()[1], second.structureCentroid()[1]);
        for (GhostCell cell : forward) {
            assertEquals(first.offset(cell, 1.0F).y(), second.offset(cell, 1.0F).y());
        }
        assertEquals(
            first.groups().get(0).cells(),
            second.groups().get(0).cells(),
            "the cells are picked in canonical order"
        );
        assertEquals(first.groups().get(1).cells(), second.groups().get(1).cells());
    }

    @Test
    void aBayWithoutDrawnCellsHasNoGroupAndNoLabel() {
        List<Stage> stages = List.of(stage("base", 2, base), stage("empty", 1, stack));
        List<GhostCell> cells = List.of(ghost(0, 0, 0, 0), ghost(1, 0, 0, 0));
        ExplodeModel model = ExplodeModel.of(stages, cells, new Hull(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F));
        assertEquals(1, model.groups().size());
        assertFalse(model.separable());
        assertEquals(1, model.labels(1.0F).size());
    }

    @Test
    void theLabelsNameTheBaysOnTheMovedCentroids() {
        Hull hull = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F);
        ExplodeModel model = ExplodeModel.of(twoBays(), twoBayCells(), hull);
        List<ExplodeModel.Label> still = model.labels(0.0F);
        assertEquals("structurecumulus:base", still.get(0).text());
        assertEquals(1.0F, still.get(0).x(), eps);
        assertEquals(0.5F, still.get(0).y(), eps);
        assertEquals(0.5F, still.get(0).z(), eps);
        assertEquals(0.0F, still.get(0).alpha());

        List<ExplodeModel.Label> moving = model.labels(1.0F);
        assertEquals(0.5F - 1.5F, moving.get(0).y(), eps, "the label follows the part it names");
        assertEquals(1.5F + 1.5F, moving.get(1).y(), eps);
        assertEquals(1.0F, moving.get(0).alpha());
        assertEquals("structurecumulus:stack", moving.get(1).text());
    }

    @Test
    void aCellWithoutAGroupStaysWhereItIs() {
        ExplodeModel model = ExplodeModel.of(twoBays(), twoBayCells(), new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 1.0F));
        GhostCell orphan = ghost(4, 4, 4, 7);
        assertEquals(ExplodeModel.Offset.zero, model.offset(orphan, 1.0F));
        assertEquals(ExplodeModel.Offset.zero, model.offset(twoBayCells().get(0), -0.5F));
        assertEquals(ExplodeModel.Offset.zero, model.offset(twoBayCells().get(0), Float.NaN));
    }
}
