package dev.vfyjxf.cumulus.mc.preview.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The base plate of design 17 §4.7: the footprint plus one tile a side, its floor and its checkerboard. */
class BasePlateTest {

    @Test
    void thePlateGrowsOneTilePastTheFootprintOnEverySide() {
        BasePlate plate = BasePlate.of(new Hull(2.0F, 3.0F, 4.0F, 5.0F, 7.0F, 6.0F));
        assertEquals(1, plate.minX());
        assertEquals(3, plate.minZ());
        assertEquals(6, plate.maxX(), "the footprint ends at x = 5, so the plate ends one tile past it");
        assertEquals(7, plate.maxZ());
        assertEquals(20, plate.tiles(), "a three-by-two footprint stands on a five-by-four plate");
    }

    @Test
    void thePlaneIsTheHullsOwnFloor() {
        BasePlate plate = BasePlate.of(new Hull(0.0F, 4.0F, 0.0F, 1.0F, 5.0F, 1.0F));
        assertEquals(4, plate.y(), "the plate lies in the drawn hull's bottom plane (§4.7)");
    }

    @Test
    void anEmptyHullDrawsNoPlate() {
        BasePlate plate = BasePlate.of(new Hull(0.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F));
        assertTrue(plate.isEmpty());
        assertEquals(0, plate.tiles());
        assertEquals(BasePlate.empty(), plate);
    }

    @Test
    void theTwoGreysAlternateInACheckerboard() {
        BasePlate plate = BasePlate.of(new Hull(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 2.0F));
        assertNotEquals(BasePlate.lightTile, BasePlate.darkTile, "the plate needs two greys to alternate at all");
        for (int x = plate.minX(); x < plate.maxX(); x++) {
            for (int z = plate.minZ(); z < plate.maxZ(); z++) {
                assertNotEquals(plate.color(x, z), plate.color(x + 1, z), "a neighbour along x flips the grey");
                assertNotEquals(plate.color(x, z), plate.color(x, z + 1), "a neighbour along z flips the grey");
                assertEquals(plate.color(x, z), plate.color(x + 2, z), "two tiles on is the same grey again");
            }
        }
    }

    @Test
    void thePlateIsNeverTheHull() {
        Hull hull = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 3.0F);
        BasePlate plate = BasePlate.of(hull);
        assertFalse(plate.isEmpty());
        assertEquals(0.0F, hull.minX(), "the hull's own box never grows to hold the plate");
        assertEquals(2.0F, hull.maxX());
        assertEquals(3.0F, hull.maxZ());
        assertTrue(plate.minX() < hull.minX() && plate.maxX() > hull.maxX(), "the plate is the wider box");
        assertTrue(plate.minZ() < hull.minZ() && plate.maxZ() > hull.maxZ());
        assertEquals(plate, BasePlate.of(hull), "the plate is a pure function of the drawn hull");
    }
}
